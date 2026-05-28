# Day 25：高级 RAG 技术

> **日期：** 2026-05-27
> **目标：** 查询重写 + HyDE + 父文档检索 — 让 RAG 更智能
> **技术栈：** Spring AI 1.0.0-M6, PgVector, DeepSeek, Ollama

---

## 一、三种高级 RAG 技术概览

| 技术 | 核心思想 | 解决的问题 | 场景 |
|:----|:--------|:----------|:----|
| **查询重写** | LLM 将口语化查询转结构化关键词 | "那个东西怎么用？" → 检索无关 | 用户输入随意、模糊 |
| **HyDE** | 先生成假设回答，再用它检索 | 查询短 → 向量空间远 | 查询信息量不足 |
| **父文档检索** | 检索小块，返回完整大块 | 小块独立缺上下文 | 需要完整段落/章节 |

---

## 二、查询重写（Query Rewriting）

### 原理

```
用户 Query: "那个向量数据库怎么用来着？我在Spring Boot里想配一下"
                      ↓ LLM 重写
重写后:    "向量数据库 使用 Spring Boot 配置"
                      ↓ 用重写后的 query 检索
PgVector:  命中"PgVector 快速配置"段落，得分 0.786
```

### Prompt

```java
private static final String REWRITE_PROMPT = """
    你是一个搜索查询优化专家。将用户的口语化查询重写为更精确的搜索关键词。

    规则：
    1. 提取核心实体和概念
    2. 去掉口语化词汇（那个、这个、怎么、来着、帮我）
    3. 补充隐含的关键词
    4. 用空格分隔关键词
    5. 只回复重写后的查询文本

    示例：
    用户：那个向量数据库怎么用？ → 向量数据库 使用 配置
    用户：Spring AI 的 RAG pipeline 是啥 → Spring AI RAG pipeline 流程

    用户：%s
    """;
```

### 实测效果

| 原始查询 | 重写后 | 得分提升 |
|:---------|:-------|:--------:|
| "那个向量数据库怎么用来着？" | "向量数据库 使用 Spring Boot 配置" | **0.786** ✅ |
| "PgVector 向量数据库 特点" | "PgVector 向量数据库 特点 功能" | **0.819** (同分，已清晰) |

---

## 三、HyDE（Hypothetical Document Embedding）

### 原理

> **HyDE 的核心洞察：** 假设你有一篇完美的参考文档，它的向量嵌入空间位置一定非常接近真实相关文档。而你直接用短查询去检索，向量空间距离可能很远。

```
用户 Query: "Spring AI 的 RAG 流程是什么样的"
                      ↓ LLM 生成假设回答
Hypothesis: "Spring AI 的 RAG（检索增强生成）流程遵循
             '检索-增强-生成'三阶段架构。首先，系统
             将用户查询通过嵌入模型转换为向量表示..."
                      ↓ 用假设回答检索 (287 字 vs 15 字)
PgVector:  得分 0.796（比直接检索更精准）
```

### 为什么 HyDE 有效？

- **短查询**（"RAG 流程"）的嵌入向量离相关文档较远
- **长假设回答**的嵌入向量覆盖了更多语义维度
- 假设回答中的专业术语（"嵌入模型"、"向量数据库"、"相似性搜索"）帮助精准定位

### 与直接检索的对比

```
查询: "Spring AI 的 RAG 流程是什么样的"
直接检索得分: 0.750
HyDE 检索得分: 0.796（+6%）
```

---

## 四、父文档检索（Parent Document Retrieval）

### 原理

```
     小块 (chunk)                   大块 (parent)
  ┌──────────────┐
  │  PgVector 介绍  │──┐            ┌──────────────────────────┐
  └──────────────┘  │            │  # Spring AI 向量数据库    │
  ┌──────────────┐  ├──拼接──→  │  集成指南                 │
  │  支持的数据库  │──┤            │                          │
  └──────────────┘  │            │  PgVector 介绍...         │
  ┌──────────────┐  │            │  支持的数据库...          │
  │  PgVector配置 │──┘            │  PgVector配置...          │
  └──────────────┘               │  相似度检索流程...        │
                                 │  向量 vs 传统数据库...     │
                                 └──────────────────────────┘
```

### 实现

```java
// 1. 检索小块（更多数量保证覆盖）
var childDocs = vectorStore.similaritySearch(
    SearchRequest.builder().query(query).topK(childTopK).build());

// 2. 按 source 分组
Map<String, List<Document>> grouped = childDocs.stream()
    .collect(Collectors.groupingBy(
        doc -> (String) doc.getMetadata().getOrDefault("source", "unknown")));

// 3. 每个 source 拼接为父文档
String parentContent = docs.stream()
    .map(Document::getText)
    .distinct()
    .collect(Collectors.joining("\n\n---\n\n"));
```

### 实测

```
查询: "向量数据库 PgVector 配置"
→ 6 个小块 → 2 个父文档
  ─ vector-db-guide.md (1864 字符) 得分 0.779
  ─ vector-store-overview.txt (639 字符) 得分 0.616
```

---

## 五、四种技术并列对比

| 技术 | 最佳得分 | 特点 |
|:----|:-------:|:-----|
| **直接检索** | **0.819** | 查询已清晰时表现稳定 |
| **查询重写** | **0.819** | 口语化查询时优势明显 |
| **HyDE** | **0.793** | 假设回答 267 字，覆盖更多术语 |
| **父文档** | **0.806** | 6 小块 → 2 大块，上下文完整 |

**结论：**
- 查询清晰 → 直接检索足够好
- 用户输入随意 → **查询重写**最有效
- 查询短、信息少 → **HyDE** 提升显著
- 需要完整上下文给 LLM → **父文档检索**

---

## 六、API 端点

| 方法 | 路径 | 参数 | 说明 |
|:----|:----|:----|:------|
| `GET` | `/rag/rewrite` | `q`, `topK` | 查询重写 + 检索 |
| `GET` | `/rag/hyde` | `q`, `topK` | HyDE 假设回答检索 |
| `GET` | `/rag/parent-doc` | `q`, `topK` | 父文档检索 |
| `GET` | `/rag/compare` | `q`, `topK` | 四种技术并列对比 |

---

## 七、遇到的坑

### 1. ChunkHit 缺少 Setters
```java
ChunkHit hit = new ChunkHit(...);
hit.setRank(1); // ❌ 忘写 setter 了
```
用 `@Data` 或手动补全，内嵌类的 setter 很容易漏掉。

### 2. HyDE 的回答质量直接影响检索
HyDE 生成的假设回答如果太泛，检索效果比直接检索还差。Prompt 必须明确要求：
- "用第三人称、客观陈述"
- "要像真实的参考文档"
- "包含具体的技术名词和术语"

### 3. 父文档拼接去重
同一个 source 的不同 chunk 可能有重复内容，要用 `.distinct()` 去重。

---

## 八、从 Day 22 → 25 的 RAG 演进路线

```
Day 22: 文档切分              Day 24: 混合检索
  ├── 3 种切分策略              ├── 语义 + 关键词
  └── 策略对比                  └── RRF 融合 + Reranker

Day 23: 向量检索                    ↓
  ├── PgVector 入库          Day 25: 高级 RAG
  └── 5 种检索模式              ├── 查询重写
                                ├── HyDE
                                └── 父文档检索
                                    ↓
                          Day 26: 🔜 企业知识库 V2
                            集成全部 + 异步摄取 + 评估
```

**Day 26 预告：** 企业知识库 V2 — 集成所有 RAG 技术（查询重写 + HyDE + 混合检索 + 重排 + 父文档），异步文档摄取 + 评估对比。

---

## 代码清单

| 文件 | 说明 |
|:----|:----|
| `QueryRewriteService.java` | 查询重写 + 检索 |
| `HydeSearchService.java` | HyDE 假设回答生成 + 检索 |
| `ParentDocService.java` | 小块检索 + source 分组 + 父文档拼接 |
| `RagCompareService.java` | 四种技术并列对比 |
| `AdvancedRagController.java` | 4 个端点 |
| `RagConfig.java` | ChatClient 配置 |
| `RagResult.java` | 响应模型 |
| `application.yml` | PgVector + Ollama + DeepSeek |
