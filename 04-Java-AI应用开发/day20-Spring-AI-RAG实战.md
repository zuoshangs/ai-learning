# 第20天：Spring AI RAG 实战 🧠📚

> **学习目标：** 掌握 RAG（Retrieval-Augmented Generation）完整流程，用 Spring AI + PgVector + Ollama 构建 Java 版 RAG 问答服务
> **代码语言：** Java（主）+ Python（对照）
> **前置知识：** Day 19（Spring AI @Tool）/ Day 8（Python 版 RAG）

---

## 📋 目录

1. [RAG 架构全景](#1-rag-架构全景)
2. [环境准备：PgVector + Ollama](#2-环境准备pgvector--ollama)
3. [文档加载与切分](#3-文档加载与切分)
4. [Embedding 向量化](#4-embedding-向量化)
5. [PgVector 向量存储](#5-pgvector-向量存储)
6. [QuestionAnswerAdvisor RAG 流程](#6-questionansweradvisor-rag-流程)
7. [REST API 与测试](#7-rest-api-与测试)
8. [遇到的坑与解决](#8-遇到的坑与解决)
9. [课堂练习](#9-课堂练习)
10. [今日小结](#10-今日小结)

---

## 1. RAG 架构全景

### 为什么需要 RAG？

大模型的三大局限：
- **知识过时**：训练数据有截止日期
- **幻觉问题**：不知道的问题会编造答案
- **无法引用来源**：你不知道它说的哪部分是事实

### RAG 解决思路

```
用户提问："Spring AI 支持哪些模型？"
          │
          ▼
  ┌─────────────────────┐
  │  1. Query 向量化     │
  │  Ollama Embedding    │
  └────────┬────────────┘
           │
           ▼
  ┌─────────────────────┐
  │  2. 向量检索 Top-K   │  ─── PgVector 存储的文档向量
  │  找到最相关的 3 块    │
  └────────┬────────────┘
           │
           ▼
  ┌─────────────────────┐
  │  3. 增强上下文        │
  │  文档块 + 用户问题    │
  └────────┬────────────┘
           │
           ▼
  ┌─────────────────────┐
  │  4. LLM 生成回答      │
  │  DeepSeek 基于文档    │
  │  回答并引用来源       │
  └─────────────────────┘
```

### 全流程 7 步

1. **文档加载** — 读取 TXT/PDF/Word 等
2. **文本切分** — 切成小块（固定大小 + 重叠区）
3. **向量化** — Embedding API 将文本转为向量
4. **存储** — 向量 + 原文存入 PgVector
5. **检索** — 用户问题向量化 → 相似度检索
6. **增强** — 检索结果 + 问题拼接
7. **生成** — LLM 基于上下文生成回答

---

## 2. 环境准备：PgVector + Ollama

### 安装 PgVector

```bash
# 安装 PostgreSQL 16 + pgvector 扩展
sudo apt-get install -y postgresql-16 postgresql-16-pgvector

# 启动服务
sudo pg_ctlcluster 16 main start

# 创建数据库和用户
sudo -u postgres psql -c "CREATE USER spring WITH PASSWORD 'spring123';"
sudo -u postgres psql -c "CREATE DATABASE airag OWNER spring;"
sudo -u postgres psql -d airag -c "CREATE EXTENSION IF NOT EXISTS vector;"
```

### 安装 Ollama + Embedding 模型

Ollama 已预装，使用已有的 `qwen2.5:0.5b` 作为 embedding 模型：

```bash
# 验证 Ollama 运行中
ollama list

# 测试 Embedding API
curl -s http://localhost:11434/api/embed -d '{
  "model": "qwen2.5:0.5b",
  "input": "Hello world"
}' | python3 -c "import sys,json; d=json.load(sys.stdin); print(f'维度: {len(d[\"embeddings\"][0])}')"
# 输出: 维度: 896
```

> **为什么不用 DeepSeek Embedding？** DeepSeek API 没有 embedding 端点，返回 404。Ollama 本地 embedding 更快（无网络延迟）、免费、不限流。

### 双引擎架构

| 组件 | 技术选型 | 原因 |
|------|---------|------|
| **聊天模型** | DeepSeek (API) | 对话质量高，中文友好 |
| **Embedding** | Ollama qwen2.5:0.5b (本地) | 免费、快、不限流 |
| **向量库** | PgVector (本地) | PostgreSQL 原生支持，无需额外服务 |

---

## 3. 文档加载与切分

### 文档内容

`src/main/resources/documents/spring-ai-intro.txt`:

```text
Spring AI 是 Spring 生态中的官方 AI 框架...
核心特性：ChatClient、提示词模板、模型支持...
RAG 流程：1. 文档加载 → 2. 文本切分 → ...
PgVector 支持：精确/近似检索，L2/IP/COSINE 距离...
```

### Java 代码：文档加载

```java
@Service
public class RagService {

    @Value("classpath:documents/*")
    private Resource[] documentResources;

    public String loadDocuments() {
        for (Resource resource : documentResources) {
            String filename = resource.getFilename();
            
            if (filename.endsWith(".txt")) {
                TextReader reader = new TextReader(resource);
                reader.setCharset(StandardCharsets.UTF_8);
                List<Document> docs = reader.get();
                docs.forEach(doc -> 
                    doc.getMetadata().put("source", filename));
                allDocs.addAll(docs);
            }
        }
        
        // 文本切分
        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> chunks = splitter.apply(allDocs);
        
        // 向量化 + 入库（自动调用 EmbeddingModel）
        vectorStore.add(chunks);
        
        return "✅ 加载 %d 个文件，切分为 %d 块".formatted(
            fileCount, chunks.size());
    }
}
```

### TokenTextSplitter

Spring AI 内置的文本切分器，自动计算 Token 数量并切分：
- **默认块大小**：基于 Token 数（约 500 Token/块）
- **重叠区**：防止语义截断（约 50 Token 重叠）
- **边界感知**：不会在单词中间切断

---

## 4. Embedding 向量化

### Spring AI 自动配置

```yaml
spring:
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        enabled: false       # 禁用 Ollama 聊天（只用 embedding）
      embedding:
        options:
          model: qwen2.5:0.5b
```

Spring AI 自动创建 `OllamaEmbeddingModel` bean，`PgVectorStore` 自动注入。

### 向量维度：896

`qwen2.5:0.5b` 产生的向量是 896 维（可通过 API 确认），对应配置：

```yaml
spring.ai.vectorstore.pgvector.dimensions: 896
```

> **注意：** 向量库表的维度必须与 Embedding 模型的输出维度一致，否则插入报错。

---

## 5. PgVector 向量存储

### 配置

```yaml
spring:
  ai:
    vectorstore:
      pgvector:
        index-type: HNSW              # 索引类型
        distance-type: COSINE_DISTANCE # 距离算法
        dimensions: 896               # 向量维度
        remove-existing-vector-store-table: false
        initialize-schema: true       # 自动建表
```

### 数据库表结构

```sql
-- Spring AI 自动创建的表
CREATE TABLE IF NOT EXISTS vector_store (
    id UUID PRIMARY KEY,
    content TEXT,                     -- 文档原文
    metadata JSON,                    -- 元数据（来源等）
    embedding VECTOR(896)             -- 向量（维度匹配）
);

-- HNSW 索引
CREATE INDEX ON vector_store 
USING hnsw (embedding vector_cosine_ops);
```

### 距离算法

| 算法 | 适用场景 | 说明 |
|------|---------|------|
| **COSINE_DISTANCE** | 语义搜索 | 关注方向，忽略长度 |
| **EUCLIDEAN_DISTANCE** | 向量聚类 | 关注绝对距离 |
| **NEGATIVE_INNER_PRODUCT** | 推荐系统 | 关注相似性强度 |

> **坑：** 在 `application.yml` 中填 `COSINE` 不行！必须填 `COSINE_DISTANCE`。

### 索引类型

| 索引 | 速度 | 精度 | 适用 |
|------|------|------|------|
| **HNSW** | 快 | 高 | 一般推荐（我们用的） |
| **IVFFlat** | 更快 | 中 | 大规模数据 |

---

## 6. QuestionAnswerAdvisor RAG 流程

### 核心代码

```java
public String ask(String question) {
    return chatClient.prompt()
        .user(question)
        .advisors(new QuestionAnswerAdvisor(
            vectorStore,
            SearchRequest.builder()
                .topK(3)
                .similarityThreshold(0.5)
                .build()
        ))
        .call()
        .content();
}
```

### QuestionAnswerAdvisor 做了什么？

1. **拦截用户问题** → 自动调用 `EmbeddingModel` 向量化
2. **向量检索** → 在 PgVector 中找 Top-K 最相关文档
3. **拼接上下文** → 将检索结果注入 `{question_answer_context}`
4. **调用 LLM** → 带上下文的增强提示词发给 DeepSeek
5. **返回回答** → 基于文档内容的精确回答

### 自动注入的上下文模板

```text
系统提示中预留了 {question_answer_context} 占位符，
QuestionAnswerAdvisor 自动将检索结果填入。

参考文档：
[文档块1] Spring AI 支持 OpenAI、Anthropic、Google Gemini、DeepSeek...
[文档块2] RAG 流程包含7个步骤...
```

---

## 7. REST API 与测试

### API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/rag/load` | 加载文档 → 切分 → 向量化 → 入库 |
| GET | `/rag/ask?q=问题` | RAG 问答（检索+增强+生成） |
| GET | `/rag/search?q=查询` | 纯检索（直接查看原文） |
| POST | `/rag/clear` | 清空向量库 |

### 启动服务

```bash
# 确保 PostgreSQL 和 Ollama 正在运行
sudo pg_ctlcluster 16 main start

# 启动 Spring Boot
cd code/day20
export DEEPSEEK_API_KEY=sk-xxx
mvn spring-boot:run
```

### 测试 1：加载文档

```bash
curl -X POST http://localhost:8080/rag/load
# ✅ 文档加载完成
# 加载文件数：1 个  原始文档：1 篇  切分块数：1 块
```

### 测试 2：RAG 问答

```bash
curl "http://localhost:8080/rag/ask?q=Spring+AI支持哪些大模型？"
# 回答：OpenAI、Anthropic、Google Gemini、DeepSeek 等

curl "http://localhost:8080/rag/ask?q=RAG架构的核心流程是什么？"
# 回答：加载→切分→向量化→存储→检索→增强→生成（7个步骤）
```

**实际输出验证：**
```
❓ Spring AI 支持哪些大模型？
💡 根据文档，Spring AI 支持：
   - OpenAI
   - Anthropic
   - Google Gemini  
   - DeepSeek

❓ RAG 架构的核心流程有哪些步骤？
💡 共 7 步：
   1. 文档加载 → 2. 文本切分 → 3. 向量化 → 4. 存储
   5. 检索 → 6. 增强 → 7. 生成
```

---

## 8. 遇到的坑与解决 🕳️

### 坑 1：DeepSeek 没有 Embedding API

**现象：** `404 -` 错误

**解决：** 改用 Ollama 本地 embedding 模型

**教训：** DeepSeek 只提供 Chat API，不支持 `v1/embeddings` 端点。RAG 场景中 embedding 用本地模型更合适。

### 坑 2：YAML 重复键

**现象：** `DuplicateKeyException: found duplicate key openai`

**原因：** 在 `spring.ai` 下写了两次 `openai` 块

**解决：** 把 embedding 配置合并到同一个 `openai` 块里

### 坑 3：PgVector 维度不匹配

**现象：** 向量插入报错

**原因：** 默认配置了 1024 维，但 qwen2.5:0.5b 输出 896 维

**解决：** 先用 curl 测试 embedding API 确定维度，再设置 `dimensions: 896`

### 坑 4：多个 ChatModel Bean 冲突

**现象：** `required a single bean, but 2 were found` — `openAiChatModel` vs `ollamaChatModel`

**原因：** `spring-ai-ollama-spring-boot-starter` 和 `spring-ai-openai-spring-boot-starter` 都创建了 ChatModel bean

**解决：** `spring.ai.ollama.chat.enabled: false` 禁用 Ollama 的 ChatModel

### 坑 5：YAML 中距离类型名

**现象：** `No enum constant COSINE`

**原因：** 填了 `COSINE` 但需要全名 `COSINE_DISTANCE`

**解决：** 使用完整枚举名 `COSINE_DISTANCE` / `EUCLIDEAN_DISTANCE` / `NEGATIVE_INNER_PRODUCT`

---

## 9. 课堂练习

### 练习 1：添加更多文档 ✏️

在 `src/main/resources/documents/` 下新建文件，比如 `pgvector-guide.txt`：

```text
PgVector 使用指南

PgVector 是 PostgreSQL 的向量扩展，目前版本 0.6.0。
支持的索引类型：
- IVFFlat：倒排文件索引，建索引快，查询略慢
- HNSW：分层可导航小世界，建索引慢，查询快
```

1. 重新 POST `/rag/load` 加载
2. 问 "PgVector 支持哪些索引类型？" 看能否正确检索到

### 练习 2：调整 Top-K ✏️

修改 `SearchRequest.builder().topK(5)` 为不同值：
- `topK(1)` → 只检索最相关 1 块，回答简洁但可能不完整
- `topK(5)` → 检索 5 块，回答全面但可能引入噪声

### 练习 3：Python 版 RAG 对照 🤝

`code/day20/rag_demo.py` 实现了完整 Python 版 RAG 流程：

```bash
cd code/day20 && python3 rag_demo.py
```

对比 Java 版和 Python 版的 API 设计差异。

---

## 10. 今日小结

### 核心概念

| 概念 | 说明 |
|------|------|
| **RAG** | 检索增强生成 — 解决大模型知识过时和幻觉问题 |
| **Embedding** | 将文本转为向量，语义相近的文本向量也相近 |
| **PgVector** | PostgreSQL 向量存储，支持余弦/欧几里得/内积 |
| **QuestionAnswerAdvisor** | Spring AI 自动 RAG 组件 |
| **TokenTextSplitter** | 文档切分器，自动计算 Token + 重叠区 |

### 代码位置

| 文件 | 路径 |
|------|------|
| **教程** | `04-Java-AI应用开发/day20-Spring-AI-RAG实战.md` |
| **DemoApplication.java** | `code/day20/src/main/java/.../DemoApplication.java` |
| **RagConfig.java** | `code/day20/.../config/RagConfig.java` |
| **RagService.java** | `code/day20/.../service/RagService.java` |
| **RagController.java** | `code/day20/.../controller/RagController.java` |
| **application.yml** | `code/day20/src/main/resources/application.yml` |
| **示例文档** | `code/day20/src/main/resources/documents/spring-ai-intro.txt` |
| **Python 对照** | `code/day20/rag_demo.py` |

### 架构图

```
┌─────────┐    ┌──────────┐    ┌───────┐
│ 用户提问  │───▶│ Embedding │───▶│ 检索   │
└─────────┘    │ Ollama   │    │ PgVec │
               └──────────┘    └───┬───┘
                                   │
                               ┌───▼───┐
                               │ 增强   │
                               │ 拼接   │
                               └───┬───┘
                                   │
                          ┌────────▼────────┐
                          │ LLM 生成（DeepSeek）│
                          └─────────────────┘
```

---

**⏭️ 明日预告：Day 21 — 实战：智能客服系统（Java 版）**
- 意图识别 + 路由到不同提示词模板
- 统一 AI 服务层
- 异常重试 + 单元测试
- 综合 Day 16-20 所有知识构建完整项目
