# Day 24：混合检索 + 重排

> **日期：** 2026-05-27
> **目标：** 混合语义检索 + 关键词检索 + RRF 融合 + LLM Reranker
> **技术栈：** PostgreSQL FTS, PgVector, Spring AI, DeepSeek

---

## 一、为什么需要混合检索？

纯语义检索（向量）和纯关键词检索各有短板：

| 场景 | 语义检索 (PgVector) | 关键词检索 (FTS) |
|:----|:-----------------:|:---------------:|
| "PgVector 的优点" | ✅ 理解"优点"含义 | ❌ 无精确匹配 |
| "向量数据库 支持 哪些" | ⚠️ 语义相似但可能跑偏 | ✅ 精准命中关键词 |
| 拼写错误/同义词 | ✅ 鲁棒 | ❌ 完全失效 |
| 精确术语匹配 | ❌ 可能漏掉 | ✅ 精确命中 |
| 新领域/冷门词 | ❌ 向量未覆盖 | ✅ 只要有字面 |

**混合检索 = 语义 + 关键词 + 融合排序**，取两者之长。

---

## 二、系统架构

```
用户查询
  │
  ├──→ 语义检索 (PgVector) ──→ 排名 S1, S2, S3...
  │        向量相似度
  │
  ├──→ 关键词检索 (PostgreSQL FTS) ──→ 排名 K1, K2, K3...
  │        tsvector @@ tsquery
  │
  └──→ RRF 融合
           score = 1/(60+rank_s) + 1/(60+rank_k)
           │
           ├──→ [可选] LLM Reranker
           │       DeepSeek 逐条评分 1-5
           │       最终分 = RRF × 0.3 + Rerank × 0.7
           │
           └──→ 最终 Top-K 结果
```

---

## 三、核心算法详解

### 3.1 RRF（Reciprocal Rank Fusion）

RRF 是混合检索最常用的融合算法。核心公式：

```
RRF_score(d) = 1/(k + rank₁(d)) + 1/(k + rank₂(d))

其中:
- rank₁(d) = 文档 d 在语义检索中的排名
- rank₂(d) = 文档 d 在关键词检索中的排名
- k = 常数（通常 60，防止除零）
```

**为什么 RRF 好？**
- 不需要归一化分数（语义 0.8 vs 关键词 0.05 无法直接比较）
- 只依赖排名，不同检索系统的分数尺度不影响
- 天然处理"只有一侧命中"的情况

### 3.2 PostgreSQL FTS 关键词检索

PostgreSQL 内置全文搜索基于 **tsvector**（文本向量）和 **tsquery**（查询向量）：

```sql
-- 建表时自动生成 tsvector
INSERT INTO keyword_index (content, source, tsvector_col)
VALUES (?, ?, to_tsvector('simple', ?));

-- 检索时使用 @@ 操作符
SELECT content, ts_rank(tsvector_col, to_tsquery('simple', ?)) AS score
FROM keyword_index
WHERE tsvector_col @@ to_tsquery('simple', ?)
ORDER BY score DESC;
```

### 3.3 LLM Reranker

用 DeepSeek 对候选结果逐条评分：

```
Prompt:
你是搜索结果相关性评分系统。
用户查询: "Spring AI 支持哪些向量数据库"
请对以下 6 个结果分别评分(1-5分)：
5=完全匹配, 4=高度相关, 3=部分相关, 2=弱相关, 1=不相关

---结果1---
Spring AI 内置支持以下向量数据库：1. PgVector...
---结果2---
Spring AI provides a unified abstraction...

Response:
{"scores": [5, 3, ...]}
```

最终分数 = RRF × 0.3 + (LLM评分/5) × 0.7

---

## 四、API 端点

| 方法 | 路径 | 模式 | 说明 |
|:----|:----|:----|:------|
| `GET` | `/search` | `semantic` | 纯语义检索（PgVector） |
| `GET` | `/search` | `keyword` | 纯关键词检索（FTS） |
| `GET` | `/search` | `hybrid` | RRF 混合融合（默认） |
| `GET` | `/search` | `rerank` | RRF + LLM Reranker 重排 |
| `GET` | `/search` | `compare` | 三方法并列对比 |
| `GET` | `/search/modes` | — | 查看可用的检索模式 |

参数：
- `q`: 查询文本（必填）
- `mode`: 检索模式（默认 `hybrid`）
- `topK`: 结果数（默认 5）

---

## 五、实测对比

### 查询："Spring AI 支持哪些向量数据库"

| 模式 | 结果数 | 最佳分数 | 最佳命中 |
|:----|:-----:|:-------:|:---------|
| **语义** | 6 | **0.773** | ✅ 完整数据库列表 |
| **关键词** | 0 | — | ❌ 分词不识别"向量数据库" |
| **RRF** | 3 | **0.016** | ✅ 融合语义结果 |
| **Rerank** | 3 | **0.705** | ✅ Rerank 给 5/5 满分 |

### 查询："向量数据库"

| 模式 | 结果数 | 最佳分数 | 最佳命中 |
|:----|:-----:|:-------:|:---------|
| **语义** | 6 | **0.750** | ✅ 语义相关 |
| **关键词** | 3 | **0.076** | ✅ 精确匹配标题文字 |
| **RRF** | 2 | **0.032** | ✅ 融合两种结果 |

### Reranker 效果

DeepSeek LLM Reranker 评分：

| 结果 | RRF 分 | Rerank 分 | 综合分 | 分析 |
|:----|:-----:|:--------:|:-----:|:----|
| #1 (PgVector 列表) | 0.016 | **5/5** | **0.705** | 完全匹配查询意图 |
| #2 (同名文档变体) | 0.016 | **5/5** | **0.705** | 高度相关 |
| #3 (通用概述) | 0.015 | **3/5** | **0.425** | 部分相关 |

---

## 六、代码核心片段

### RRF 融合算法

```java
private List<SearchResult> rrfFusion(String query, int topK) {
    // 1. 分别检索
    List<SearchResult> semanticResults = vectorSearch.search(query, topK * 2);
    List<SearchResult> keywordResults = keywordSearch.search(query, topK * 2);

    // 2. RRF 计算
    for (String content : allContents) {
        int rankS = semanticRank.getOrDefault(content, Integer.MAX_VALUE);
        int rankK = keywordRank.getOrDefault(content, Integer.MAX_VALUE);

        double rrfScore = 0;
        if (rankS != Integer.MAX_VALUE) rrfScore += 1.0 / (RRF_K + rankS);
        if (rankK != Integer.MAX_VALUE) rrfScore += 1.0 / (RRF_K + rankK);
        // ... 排序输出
    }
}
```

### 关键词索引初始化

```java
@Bean
public CommandLineRunner initKeywordIndex(KeywordSearchService keywordSearch) {
    return args -> {
        keywordSearch.initKeywordIndex();
        keywordSearch.syncFromVectorStore(); // 从 PgVector 同步数据
    };
}
```

---

## 七、遇到的坑

### 1. Immutable List 无法排序

```java
List<SearchResult> candidates = rrfFusion(...); // .toList() 返回不可变列表
candidates.sort(...); // ❌ UnsupportedOperationException
```

**修复：** 包装为 `new ArrayList<>(rrfFusion(...))`

### 2. PostgreSQL simple FTS 不支持中文分词

`to_tsvector('simple', '向量数据库')` 不会自动切分中文。中文需要：
- `zhparser` 扩展（PostgreSQL 中文分词插件）
- 或用 Elasticsearch + IK 分词器
- 或用 `jieba` 预处理后自定义 tsvector

我们使用的是 `simple` 配置，对单中文词有效，但对"向量数据库"作为一个整体被 tsvector 正确索引了，但 `to_tsquery('simple', '支持')` 可能匹配不到。

### 3. DeepSeek Reranker 的 JSON 解析

LLM 返回可能带 markdown 包裹的 JSON，需要多层 fallback 解析：
```java
if (resp.contains("```json")) {
    json = resp.split("```json")[1].split("```")[0];
} else if (resp.contains("```")) {
    json = resp.split("```")[1].split("```")[0];
}
```

---

## 八、Day 23 → Day 24 升级路径

```
Day 23: 语义检索
├── PgVector 相似度搜索
└── 一种检索方式

Day 24: 混合检索 + 重排
├── PgVector 语义搜索
├── PostgreSQL FTS 关键词搜索
├── RRF 融合（语义+关键词）
├── LLM Reranker 二次排序
└── 5 种检索模式可切换
```

**Day 25 预告：** 高级 RAG 技术 — 查询重写、HyDE（假设回答检索）、父文档检索

---

## 代码清单

| 文件 | 说明 |
|:----|:----|
| `HybridApplication.java` | 启动类 |
| `HybridConfig.java` | 配置 + 关键词索引初始化 |
| `VectorSearchService.java` | PgVector 语义检索 |
| `KeywordSearchService.java` | PostgreSQL FTS 关键词检索 |
| `HybridSearchService.java` | RRF 融合 + LLM Reranker + 对比 |
| `HybridController.java` | 6 种模式 API |
| `SearchResult.java` / `HybridSearchResponse.java` | 响应模型 |
| `application.yml` | PgVector + FTS 配置 |
