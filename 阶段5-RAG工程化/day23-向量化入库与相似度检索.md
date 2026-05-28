# Day 23：向量化入库 + 相似度检索

> **日期：** 2026-05-27
> **目标：** 从文档切分 → 向量化入库 → 多模式语义检索 → 完整闭环
> **技术栈：** Spring AI 1.0.0-M6, PgVector, Ollama (Embedding), DeepSeek (Chat)

---

## 一、今天构建的整体管线

```
文档文件 (txt/md/json/pdf/docx)
         ↓ 多格式读取 (TextReader / TikaDocumentReader)
原始文本内容
         ↓ 策略化切分 (3 种策略并行)
文档块 + 元数据 (source, strategy, chunkIndex)
         ↓ 自动向量化 (Ollama qwen2.5:0.5b → 896维向量)
PgVector 向量库
         ↑ 检索 (5 种模式)
用户查询
```

**与 Day 20 的区别：**
- Day 20：一个策略切分 → 直接入库 → QuestionAnswerAdvisor 一站式
- **Day 23：多策略并行摄入 → 带策略标签 → 5 种检索模式可切换**

---

## 二、五种检索模式

### 1. `top-k` — 标准语义检索
返回与查询最相似的 K 个文档块。

```
Request:  /vector/search?q=Spring+AI&mode=top-k&topK=3
Response: 3 个文档块，按相似度降序排列
Score:    0.812 (最高分)
```

### 2. `threshold` — 带阈值过滤
只返回相似度超过阈值的块，去掉低质量匹配。

```
Request:  /vector/search?q=xxx&mode=threshold&topK=10&threshold=0.5
```

### 3. `window` — 上下文窗口
返回命中块及其前后相邻块，解决"碎块无上下文"问题。

### 4. `compare` — 策略对比
同一查询用不同切分策略的结果并列展示，方便评估各策略效果。

```
TokenSplitter:    得分 0.552 — 块较小，关键词分散时可能漏命中
ParagraphSplitter:得分 0.500 — 大段落，含完整上下文
RecursiveSplitter:得分 0.500 — 近似段落，结构清晰
```

### 5. `rag` — 检索增强生成
检索 + 大模型生成回答。将检索结果作为上下文传给 DeepSeek。

```
Q: 向量数据库和传统SQL有什么区别？
A: 根据参考文档... [详细列出了 5 个维度的对比表格]
✓ 答案完整、有引用、有条理
```

---

## 三、核心代码分析

### 3.1 摄入管线 — VectorIngestionService

```java
public IngestionResult ingestFile(String filePath, String strategyName) {
    // 1. 读取文档（自动识别格式）
    String text = readDocument(path);

    // 2. 选择策略（默认全部）
    List<ChunkStrategy> activeStrategies = strategies;

    // 3. 各策略并行切分 + 入库
    for (ChunkStrategy strategy : activeStrategies) {
        List<String> chunks = strategy.chunk(text);

        // 每个块带元数据：source、chunkIndex、chunkStrategy
        List<Document> docs = chunks.stream()
            .map(c -> new Document(c, Map.of(
                "source", filename,
                "chunkIndex", i,
                "chunkStrategy", strategy.getName()
            )))
            .toList();

        vectorStore.add(docs);  // 自动 Embedding + PgVector 存储
    }
}
```

**关键设计：元数据标签。** 每个文档块都记录了由哪个策略切分，后续检索时可以用来分组对比。

### 3.2 检索服务 — VectorSearchService

```java
// 核心：一条语句完成检索
List<Document> docs = vectorStore.similaritySearch(
    SearchRequest.builder()
        .query(query)
        .topK(topK)
        .similarityThreshold(threshold)
        .build());
```

**Spring AI 自动做三件事：**
1. 用 `EmbeddingModel` 将 `query` 转为向量
2. 在 PgVector 中执行余弦距离搜索
3. 返回匹配的 `Document` 列表（含原始文本和元数据）

---

## 四、配置要点

```yaml
spring:
  ai:
    vectorstore:
      pgvector:
        index-type: HNSW        # HNSW = 分层可导航小世界图（高性能近似最近邻）
        distance-type: COSINE_DISTANCE  # 余弦距离
        dimensions: 896          # 必须匹配 Embedding 模型输出维度
        initialize-schema: true  # 自动建表
```

**Ollama Embedding 配置：**
```yaml
spring:
  ai:
    ollama:
      embedding:
        options:
          model: qwen2.5:0.5b   # 轻量级中文 Embedding 模型
```

Ollama 的 `qwen2.5:0.5b` 输出 **896 维向量**，与 PgVector 的 `dimensions: 896` 必须匹配。

---

## 五、API 参考

| 方法 | 路径 | 参数 | 说明 |
|:----|:----|:----|:----|
| `POST` | `/vector/ingest` | `filePath`, `strategy`(可选) | 摄入单个文档 |
| `POST` | `/vector/ingest/dir` | `dirPath`, `strategy`(可选) | 批量摄入 |
| `GET` | `/vector/search` | `q`, `mode`, `topK`, `threshold` | 检索 |
| `POST` | `/vector/clear` | — | 清空向量库 |
| `GET` | `/vector/strategies` | — | 查看可用模式 |

**search 参数详解：**

| 参数 | 默认值 | 可选值 |
|:----|:-----:|:------|
| `mode` | `top-k` | `top-k`, `threshold`, `window`, `compare`, `rag` |
| `topK` | `5` | 任意正整数 |
| `threshold` | `0.0` | 0.0 ~ 1.0（仅 threshold 模式生效） |

**返回结构（RAG 模式）：**
```json
{
  "query": "PgVector 有什么优点？",
  "mode": "rag",
  "totalResults": 3,
  "results": [ ... 检索到的文档块 ... ],
  "ragAnswer": "根据参考文档...PgVector 的优点包括..."
}
```

---

## 六、实测结果

### 检索质量

| 查询 | 最佳匹配策略 | 相似度 | 命中内容 |
|:----|:-----------:|:------:|:--------|
| "Spring AI 支持哪些向量数据库？" | RecursiveSplitter | **0.812** | 完整列出 8 种数据库 |
| "PgVector vs SQL" | TokenSplitter | **0.552** | 对比表格 |
| "向量数据库和传统SQL有什么区别" | RAG | — | AI 生成了完整对比答案 |

### 策略对比（同查询）

| 策略 | 得分 | 说明 |
|:----|:---:|:----|
| TokenSplitter (Spring AI) | **0.552** | 块最小，关键词密度高 |
| ParagraphSplitter (语义) | 0.500 | 大段落含完整上下文但噪声更多 |
| RecursiveSplitter (LangChain式) | 0.500 | 近似段落 |

**观察：** Token 切分在某些查询中得分更高，因为小块的关键词密度更大。

---

## 七、遇到的坑

### 1. EmbeddingModel 缺失
```
APPLICATION FAILED TO START
Parameter 1 of method vectorStore ... required a bean of type
'EmbeddingModel' that could not be found.
```
**原因：** 同时禁用了 OpenAI embedding (`enabled: false`) 又没引入 Ollama starter。
**解决：** 添加 `spring-ai-ollama-spring-boot-starter` 依赖，让 Ollama 自动提供 EmbeddingModel bean。

### 2. 维度不匹配
**症状：** 向量入库报 schema 错误。
**分析：** PgVector 建表时指定的 `dimensions` 必须 = Embedding 模型输出维度。
**qwen2.5:0.5b → 896 维**，配置中必须一致。

### 3. `remove-existing-vector-store-table: true`
开发环境启用，每次启动重建表。生产环境应去掉。
```yaml
vectorstore:
  pgvector:
    remove-existing-vector-store-table: true  # 开发用
    initialize-schema: true                    # 自动建表
```

### 4. PgVector 相似度分数解读
Spring AI 返回 `distance`（余弦距离），范围 0~2：
- **0** = 完全相同
- **1** = 无关
- **2** = 完全相反

我们在 `SearchService` 中转换为相似度：`1 - distance / 2`

---

## 八、从 Day 20 → 22 → 23 的演进

```
Day 20: Demo RAG
├── TextReader (仅 txt)
├── TokenTextSplitter (默认)
├── QuestionAnswerAdvisor (一站式)
└── 一个端点

Day 22: 文档处理管线
├── 多格式读取 (txt/md/pdf/docx/json)
├── 3 种切分策略 (Token/Paragraph/Recursive)
├── 策略对比 API
└── 元数据提取

Day 23: 向量检索管线 👈 今天
├── 多策略并行摄入 ← Day 22 的切分
├── 元数据标签 (source/strategy/index)
├── 5 种检索模式 (top-k/threshold/window/compare/rag)
├── PgVector 持久化 ← Day 20 的数据库
├── 策略对比检索
└── RAG 增强生成
```

**Day 24 预告：** 混合检索 + 重排 — Elasticsearch 关键词检索 + PgVector 语义检索 + RRF 融合排序 + Cohere/本地 Reranker 二次排序。

---

## 代码清单

| 文件 | 说明 |
|:----|:----|
| `VectorApplication.java` | 启动类 |
| `VectorConfig.java` | ChatClient 配置 |
| `VectorIngestionService.java` | 文档摄入（读取 → 切分 → 入库） |
| `VectorSearchService.java` | 5 种检索模式实现 |
| `VectorController.java` | REST API |
| `ChunkStrategy.java` | 切分策略接口 |
| `TokenChunkStrategy.java` | Token 切分 (Spring AI) |
| `ParagraphChunkStrategy.java` | 段落语义切分 |
| `RecursiveChunkStrategy.java` | 递归字符切分 (LangChain 式) |
| `SearchResult.java` / `SearchResponse.java` / `IngestionResult.java` | 响应模型 |
| `application.yml` | PgVector + Ollama + DeepSeek 配置 |
| `vector-db-guide.md` / `vector-store-overview.txt` | 示例文档 |
| `ChunkStrategyTest.java` | 8 个单元测试 |
| `vector_store_demo.py` | Python 对照版 |
