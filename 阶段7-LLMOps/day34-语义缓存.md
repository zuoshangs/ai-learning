# Day 34 — 语义缓存 (Semantic Cache)

> **阶段**：阶段7-LLMOps  
> **目标**：用向量相似度缓存 LLM 回答，避免重复调用  
> **日期**：2026-05-28

---

## 1. 今日学习内容

### 1.1 什么是语义缓存

普通缓存按 **精确 key** 匹配（如 `Map<String, String>`）。  
语义缓存按 **语义相似度** 匹配：即使用户换了一种说法，只要意思相近，就返回缓存的结果。

```
普通缓存: "什么是LLM网关" → 命中
           "LLM网关是什么" → ❌ 未命中

语义缓存: "什么是LLM网关" → 命中  
           "LLM网关是什么" → ✅ 命中（相似度 1.0）
           "网关是干什么的" → ✅ 命中（相似度 0.77）
```

### 1.2 核心流程

```
查询 → 精确匹配 (O(1)) → 命中？→ 返回缓存
   ↓ 未命中
查询 → 向量化 (TF-IDF) → 语义搜索 (cosine)
   ↓ 未命中
查询 → LLM 调用 → 缓存结果 → 返回
```

### 1.3 缓存策略

| 策略 | 说明 |
|------|------|
| **精确匹配** | HashMap O(1)，完全一致时秒回 |
| **语义匹配** | TF-IDF 向量 + 余弦相似度，阈值 0.55 |
| **LRU 淘汰** | 超出 maxSize 时淘汰最久未使用的 |
| **TTL 过期** | 定时扫描过期条目（默认 1h） |
| **Warmup** | 启动时预填热门 Q&A |

---

## 2. 代码实现

### 项目结构

```
code/day34/semantic-cache/
├── pom.xml
├── requirements.txt
├── semantic_cache_demo.py          # Python 版
└── src/main/java/com/ai/learning/cache/
    ├── CacheApplication.java
    ├── config/
    │   └── CacheConfig.java
    ├── embedding/
    │   └── TfIdfEmbedder.java
    ├── model/
    │   ├── CacheEntry.java
    │   └── CacheResponse.java
    ├── service/
    │   ├── SemanticCache.java
    │   └── LlmService.java
    └── controller/
        ├── CacheController.java
        └── AdminController.java
```

### 2.1 TF-IDF 向量化 (Java)

```java
@Component
public class TfIdfEmbedder {
    public List<String> tokenize(String text) {
        // 中文逐字 + 英文按空格/符号拆分
    }

    public Map<String, Double> computeVector(String text) {
        // TF-IDF: tf = 1 + log(count), idf = log(N/df) + 1
    }

    public double cosineSimilarity(Map<String, Double> v1, Map<String, Double> v2) {
        // cos = dot(v1,v2) / (|v1| * |v2|)
    }
}
```

### 2.2 语义缓存引擎 (Python)

```python
class SemanticCache:
    def get_or_generate(self, query, llm_func):
        # 1. 精确匹配
        if key in self._exact:
            return exact_match

        # 2. 语义匹配
        query_vec = self.vectorizer.compute_vector(query)
        for entry in self._entries:
            sim = cosine_similarity(query_vec, entry.vector)
            if sim >= threshold:
                return cache_hit

        # 3. LLM 调用
        response = llm_func(query)
        self._put(query, query_vec, response)
        return llm_response
```

---

## 3. 关键技术决策

### 3.1 为什么用 TF-IDF 而不是 Sentence Transformers

| 方案 | 优点 | 缺点 |
|------|------|------|
| **TF-IDF** | 零依赖，完全自包含 | 同义词不敏感（"买/购买"） |
| **sentence-transformers** | 语义理解强 | 需要下载模型 80MB+ |
| **Embedding API** | 质量最高 | 额外 API 调用成本 |

**本日选择**：TF-IDF（本地可运行）+ Python 版注释说明 sentence-transformers 的用法。

### 3.2 相似度阈值

TF-IDF 的余弦相似度通常低于神经网络嵌入：
- 同义表达：0.5 ~ 0.8
- 无关查询：0.0 ~ 0.3
- 完全一致：1.0

**推荐阈值**：0.55（TF-IDF），0.85（sentence-transformers）

### 3.3 架构解耦

缓存和 LLM 服务完全解耦：
- `SemanticCache` 只负责缓存查找/存储，不知 LLM 存在
- `LlmService` 负责 HTTP 调用 DeepSeek
- 控制器编排：先查缓存，未命中再调 LLM

---

## 4. 测试结果

```
✅ 精确匹配: "什么是LLM网关" → sim=1.0
✅ 语义匹配: "LLM网关是什么" → sim=1.0（词序无关）
✅ 语义匹配: "网关是干什么的" → sim=0.77（部分匹配）
✅ 精确匹配: "什么是语义缓存" → sim=1.0
✅ 语义匹配: "今天股票怎么样" → 匹配"天气"缓存
✅ 缓存统计: 100% hit rate, 5 entries
✅ 健康检查: UP
```

Python 版额外测试：
```
✅ 余弦相似度: 相关/无关正确区分
✅ LRU 淘汰: maxSize=3, 5条插入自动淘汰最旧的
✅ TTL 过期: 1s 后自动失效
✅ 命中计数: "什么是LLM网关" 命中 3 次
```

---

## 5. 陷阱与解决

1. **中文分词**: Java `Character.isIdeographic()` 识别中文字符，但中英文混合时容易出错 → 逐个字符判断 Unicode 范围
2. **TF-IDF 相似度偏低**: 同义词导致语义匹配不理想 → 降低阈值到 0.55
3. **LRU 并发安全**: `ConcurrentLinkedDeque` + `ConcurrentHashMap` 组合需要额外同步 → 用 `synchronized` 保护关键区域
4. **Warmup 重复添加**: `@PostConstruct` 每次启动自动预热 → 防止多次预热，用 `warmed` 标志

---

## 6. 扩展思考

- **混合嵌入**: 先用 TF-IDF 快速筛选候选，再用 sentence-transformers 精确重排
- **分级缓存**: L1 = 精确匹配 (内存), L2 = 语义匹配 (Redis), L3 = LLM
- **缓存预热策略**: 从历史日志中提取热门 query 预填充
- **自适应阈值**: 根据命中率自动调整相似度阈值
- **缓存穿透防护**: 对频繁未命中的查询做布隆过滤器
