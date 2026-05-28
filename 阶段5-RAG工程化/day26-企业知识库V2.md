# Day 26：企业知识库 V2 — 集成全部 RAG 技术

> **日期：** 2026-05-27
> **目标：** 将 Day 22-25 全部 RAG 技术集成到一套生产级知识库系统中
> **技术栈：** Spring AI 1.0.0-M6, PgVector, DeepSeek, 本地哈希嵌入

---

## 一、Day 26 在学什么？

至此，我们已经分别掌握了：

| 天 | 技术 | 产出 |
|:--:|:----|:----|
| Day 22 | 文档加载与切分 | ChunkingService |
| Day 23 | 向量嵌入与存储 | EmbeddingService + PgVector |
| Day 24 | 混合检索 + 重排 | HybridSearch + Reranker |
| Day 25 | 高级 RAG 技术 | Query Rewrite + HyDE + ParentDoc |

**Day 26 的目标：把这些技术**全部集成**到一个系统中，加上异步摄取和评估对比，构建一个生产级的企业知识库。**

### 知识点地图

```
┌─────────────────────────────────────────────────────────┐
│              企业知识库 V2 架构                            │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  用户查询                                                 │
│     │                                                    │
│     ▼                                                    │
│  ┌──────────────────┐                                    │
│  │ 查询重写 (D25)   │  ← LLM 口语化→结构化                │
│  └──────┬───────────┘                                    │
│         ▼                                               │
│  ┌──────────────────┐                                    │
│  │ 混合检索 (D24)   │  ← RRF: 语义+关键词融合              │
│  │ ┌────┐  ┌────┐  │                                    │
│  │ │语义│  │FTS │  │                                    │
│  │ └────┘  └────┘  │                                    │
│  └──────┬───────────┘                                    │
│         ▼                                               │
│  ┌──────────────────┐                                    │
│  │ 父文档检索 (D25) │  ← 小块→完整文档                    │
│  └──────┬───────────┘                                    │
│         ▼                                               │
│  ┌──────────────────┐                                    │
│  │ LLM Reranker(D24)│  ← DeepSeek 打分重排               │
│  └──────┬───────────┘                                    │
│         ▼                                               │
│  ┌──────────────────┐                                    │
│  │ 回答生成          │  ← 检索结果+LLM 生成最终答案        │
│  └──────────────────┘                                    │
│                                                         │
│  异步摄入管线:                                            │
│  文档 → [队列] → 切分 → 嵌入 → PgVector                    │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

---

## 二、项目结构

```
code/day26/
├── pom.xml                           # Maven 依赖
├── src/main/resources/
│   └── application.yml               # 服务器配置
├── src/main/java/com/ai/learning/knowledge/
│   ├── KnowledgeBaseApplication.java # 启动类
│   ├── config/
│   │   ├── KnowledgeConfig.java      # 配置占位
│   │   ├── SimpleEmbeddingConfig.java # 本地哈希嵌入模型
│   │   └── PgVectorManualConfig.java  # 手动 PgVector 管理
│   ├── model/
│   │   ├── KnowledgeDocument.java    # 文档实体
│   │   ├── DocumentChunk.java        # 分块实体
│   │   ├── SearchRequest.java        # 搜索请求参数
│   │   ├── SearchResponse.java       # 搜索响应
│   │   ├── SearchResultItem.java     # 搜索结果项
│   │   ├── EvaluationResult.java     # 评估报告
│   │   └── IngestRequest.java        # 摄入请求
│   ├── ingestion/
│   │   ├── DocumentIngestionService.java  # 异步摄入管线
│   │   ├── ChunkingService.java           # 文档切分
│   │   └── EmbeddingService.java          # 向量入库
│   ├── search/
│   │   ├── KnowledgeBaseService.java      # ⭐ 集成搜索核心
│   │   ├── QueryRewriteService.java       # 查询重写
│   │   ├── VectorSearchService.java       # 语义检索
│   │   ├── KeywordSearchService.java      # 关键词检索 (FTS)
│   │   └── RerankerService.java           # LLM 重排
│   ├── evaluation/
│   │   └── RagEvaluator.java              # V1 vs V2 评估
│   └── controller/
│       └── KnowledgeBaseController.java   # REST API
└── python/
    └── knowledge_base_demo.py             # Python 对照版
```

---

## 三、核心组件详解

### 3.1 异步摄入管线 `DocumentIngestionService`

使用内存队列 `BlockingQueue` + 单线程处理器实现异步文档摄入：

```java
// 工作流：收到文档 → 入队 → 异步切分 → 异步向量化入库
public String ingestAsync(KnowledgeDocument doc) {
    String taskId = "task-" + taskIdGen.incrementAndGet();
    queue.offer(new IngestionTask(taskId, doc));
    return taskId;  // 立即返回，处理在后台
}
```

```python
# Python 对照
from queue import Queue
import threading

class DocumentIngestionService:
    def __init__(self):
        self.queue = Queue(maxsize=100)
        self._start_processor()
    
    def ingest_async(self, doc):
        task_id = f"task-{next(self._id_gen)}"
        self.queue.put((task_id, doc))
        return task_id  # 立即返回
```

### 3.2 核心集成管线 `KnowledgeBaseService`

```java
// V2 集成搜索 — 全管线
public SearchResponse searchV2(SearchRequest req) {
    // 1. 查询重写
    if (req.isUseRewrite()) 
        searchQuery = rewriteService.rewrite(query);
    
    // 2. 混合检索 (RRF 融合)
    if (req.isUseHybridSearch()) 
        results = hybridSearch(searchQuery, topK);
    
    // 3. 父文档检索
    if (req.isUseParentDoc()) 
        results = expandParentDocs(results);
    
    // 4. LLM Reranker
    if (req.isUseReranker()) 
        results = rerankerService.rerank(query, results, topK);
    
    // 5. 回答生成
    String answer = generateAnswer(query, results);
    
    return response;
}
```

### 3.3 RRF 混合检索

```
RRF_score(d) = 1/(60 + rank_semantic(d)) + 1/(60 + rank_keyword(d))
```

RRF (Reciprocal Rank Fusion) 的优点是：
- **不需要归一化分数** — 直接用排名计算
- **鲁棒性好** — 即使某个检索器效果差也不影响整体
- **简单有效** — 实践表明比加权平均好 10-30%

### 3.4 V1 vs V2 评估对比

| 对比项 | V1 基础版 | V2 增强版 |
|:------|:----------|:----------|
| 检索方式 | 仅语义检索 | 语义 + 关键词混合 (RRF) |
| 查询优化 | 无 | 查询重写 |
| 结果排序 | 余弦距离 | RRF + LLM Reranker |
| 上下文 | 单块 | 父文档检索 |

---

## 四、实际碰到的坑

### 🕳️ 坑 1：DeepSeek 不支持 Embedding API

**现象：** `OpenAiApi.embeddings()` → HTTP 404
**原因：** DeepSeek API 只支持 Chat Completions，不支持 OpenAI 兼容的 Embedding
**解决：** 自研本地哈希嵌入模型

```java
// SimpleEmbeddingConfig — 本地 1024 维哈希嵌入
// 使用 TF-IDF + n-gram + MurmurHash 投影
private float[] computeHashEmbedding(String text) {
    // 1. 分词 → TF 加权
    // 2. 多哈希投影到 1024 维空间
    // 3. 字符 n-gram 投影（捕获子词信息）
    // 4. L2 归一化
    return vector;
}
```

### 🕳️ 坑 2：PgVector 向量格式要求

**现象：** `ERROR: malformed vector literal: "0.000000,0.000000,..."`  
**错误信息：** `Vector contents must start with "["`
**原因：** PostgreSQL pgvector 扩展要求向量格式为 `[0.1,0.2,0.3]` 而非 `0.1,0.2,0.3`
**解决：** 向量字符串前后加 `[...]`

```java
String vectorStr = "[" + String.join(",", values) + "]";
```

### 🕳️ 坑 3：uuid-ossp 扩展与 UUID 格式

**现象：** `UUID string too large`  
**原因：** Chunk ID 格式为 `docId-chunk-N`，不是合法 UUID
**解决：** 对非法 UUID 字符串使用 `UUID.nameUUIDFromBytes()` 生成版本 3 UUID

---

## 五、生产环境建议

| 当前方案 | 生产建议 |
|:--------|:---------|
| 内存队列 | RabbitMQ / Kafka |
| 哈希嵌入 | Ollama bge-m3 / OpenAI text-embedding-ada-002 |
| 单线程处理器 | 线程池 + 分片 |
| JSON 元数据硬编码 | Jackson ObjectMapper |

---

## 六、接口文档

| 方法 | 路径 | 说明 |
|:----|:-----|:-----|
| GET | `/api/knowledge/health` | 健康检查 |
| POST | `/api/knowledge/ingest` | 异步摄入文档 |
| GET | `/api/knowledge/ingest/{taskId}` | 查询任务状态 |
| GET | `/api/knowledge/ingest/stats` | 摄入统计 |
| GET | `/api/knowledge/search?query=...&mode=v2` | 搜索 (GET) |
| POST | `/api/knowledge/search/v2` | V2 集成搜索 |
| GET | `/api/knowledge/evaluate/quick` | 快速评估 |

---

## 七、第四阶段总结

至此，我们从 Spring Boot 零配置到生产级知识库，走完了 **RAG 工程化** 的完整路径：

```
Day 16: 环境搭建      → 🟢 Hello World
Day 17: 提示词+结构化  → 🟢 模板/输出
Day 18: 多轮对话+SSE  → 🟢 流式/记忆
Day 19: 工具调用      → 🟢 Function Calling
Day 20: 基础 RAG      → 🟢 Embedding+检索
Day 21: 智能客服      → 🟢 完整应用
────────────────────────────────────
Day 22: 文档切分      → 🟢 加载/切分
Day 23: 向量化入库    → 🟢 PgVector
Day 24: 混合检索+重排 → 🟢 RRF+Reranker
Day 25: 高级 RAG     → 🟢 重写/HyDE/父文档
Day 26: 企业知识库 V2 → 🟢 集成全部
```

明天进入 **第五阶段：Agent 与工作流**，从 RAG 进阶到自主 Agent！
