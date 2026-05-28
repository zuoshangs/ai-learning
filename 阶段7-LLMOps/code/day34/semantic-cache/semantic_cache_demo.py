"""
Day 34 — 语义缓存 (Python 对照版)

演示：
1. TF-IDF 向量化 (中文 + 英文)
2. 余弦相似度计算
3. 语义缓存引擎 (精确匹配 → 语义匹配 → LLM)
4. 相似度阈值对比
5. LRU 淘汰 + TTL 过期
"""

import math
import time
import json
from collections import OrderedDict, defaultdict
from concurrent.futures import ThreadPoolExecutor


# ============================================================
# 1. TF-IDF 向量化
# ============================================================
class TfIdfVectorizer:
    """TF-IDF 向量化器 (纯 Python, 不依赖外部库)"""

    def __init__(self):
        self.df = defaultdict(int)  # 文档频率
        self.total_docs = 0
        self.frozen = False

    def tokenize(self, text: str) -> list[str]:
        """分词: 中文字符逐字 + 英文单词拆分"""
        tokens = []
        eng = []
        for ch in text:
            if '\u4e00' <= ch <= '\u9fff' or '\u3000' <= ch <= '\u303f':
                if eng:
                    tokens.append(''.join(eng).lower())
                    eng = []
                tokens.append(ch)
            elif ch.isalpha() or ch in "-'":
                eng.append(ch)
            elif ch.isdigit():
                eng.append(ch)
            else:
                if eng:
                    tokens.append(''.join(eng).lower())
                    eng = []
        if eng:
            tokens.append(''.join(eng).lower())
        return tokens

    def add_document(self, text: str):
        if self.frozen:
            raise RuntimeError("Vectorizer is frozen")
        unique = set(self.tokenize(text))
        for term in unique:
            self.df[term] += 1
        self.total_docs += 1

    def freeze(self):
        self.frozen = True

    def compute_vector(self, text: str) -> dict[str, float]:
        """计算 TF-IDF 向量"""
        tokens = self.tokenize(text)
        tf = defaultdict(int)
        for t in tokens:
            tf[t] += 1

        vector = {}
        for term, count in tf.items():
            tf_val = 1.0 + math.log(count)
            idf = math.log((self.total_docs + 1) / (self.df.get(term, 1) + 1)) + 1.0
            vector[term] = tf_val * idf
        return vector

    @staticmethod
    def cosine_similarity(v1: dict[str, float], v2: dict[str, float]) -> float:
        """余弦相似度"""
        dot = n1 = n2 = 0.0
        all_terms = set(v1) | set(v2)
        for term in all_terms:
            a = v1.get(term, 0.0)
            b = v2.get(term, 0.0)
            dot += a * b
            n1 += a * a
            n2 += b * b
        if n1 == 0 or n2 == 0:
            return 0.0
        return dot / (math.sqrt(n1) * math.sqrt(n2))


# ============================================================
# 2. 缓存条目
# ============================================================
class CacheEntry:
    def __init__(self, query: str, vector: dict, response: str, ttl_seconds: int = 3600):
        self.query = query
        self.vector = vector
        self.response = response
        self.created_at = time.time()
        self.expires_at = self.created_at + ttl_seconds if ttl_seconds > 0 else None
        self.hit_count = 0

    def is_expired(self) -> bool:
        return self.expires_at is not None and time.time() > self.expires_at

    def hit(self):
        self.hit_count += 1


# ============================================================
# 3. 语义缓存引擎
# ============================================================
class SemanticCache:
    """语义缓存引擎"""

    def __init__(self, vectorizer: TfIdfVectorizer,
                 similarity_threshold: float = 0.85,
                 max_size: int = 1000,
                 ttl_seconds: int = 3600):
        self.vectorizer = vectorizer
        self.threshold = similarity_threshold
        self.max_size = max_size
        self.ttl_seconds = ttl_seconds

        # 精确匹配: query -> entry
        self._exact: dict[str, CacheEntry] = {}

        # 语义搜索: OrderedDict 作为 LRU 队列 (最近使用在前)
        self._entries: OrderedDict[str, CacheEntry] = OrderedDict()

        # 统计
        self.total_lookups = 0
        self.cache_hits = 0
        self.cache_misses = 0

    def _normalize(self, q: str) -> str:
        return ' '.join(q.strip().lower().split())

    def get_or_generate(self, query: str, llm_func) -> tuple[str, dict]:
        """获取或生成响应"""
        self.total_lookups += 1

        # 1. 精确匹配
        key = self._normalize(query)
        if key in self._exact:
            entry = self._exact[key]
            if not entry.is_expired():
                entry.hit()
                self.cache_hits += 1
                self._touch(entry)
                return entry.response, {
                    "source": "exact",
                    "similarity": 1.0,
                    "cached_query": entry.query,
                    "hit_count": entry.hit_count,
                }

        # 2. 语义匹配
        query_vec = self.vectorizer.compute_vector(query)
        best = None
        best_sim = 0.0

        to_evict = []
        for q, entry in self._entries.items():
            if entry.is_expired():
                to_evict.append(q)
                continue
            sim = self.vectorizer.cosine_similarity(query_vec, entry.vector)
            if sim > best_sim:
                best_sim = sim
                best = entry

        # 淘汰过期
        for q in to_evict:
            del self._entries[q]
            self._exact.pop(self._normalize(q), None)

        if best and best_sim >= self.threshold:
            best.hit()
            self.cache_hits += 1
            self._touch(best)
            return best.response, {
                "source": "semantic",
                "similarity": round(best_sim, 4),
                "cached_query": best.query,
                "hit_count": best.hit_count,
            }

        # 3. 未命中 → 调用 LLM
        self.cache_misses += 1
        start = time.time()
        response = llm_func(query)
        latency = int((time.time() - start) * 1000)

        self._put(query, query_vec, response)
        return response, {
            "source": "llm",
            "latency_ms": latency,
        }

    def _put(self, query: str, vector: dict, response: str):
        """添加缓存条目"""
        while len(self._entries) >= self.max_size:
            oldest_q, oldest_entry = self._entries.popitem(last=False)
            self._exact.pop(self._normalize(oldest_entry.query), None)

        entry = CacheEntry(query, vector, response, self.ttl_seconds)
        self._entries[query] = entry
        self._exact[self._normalize(query)] = entry

    def _touch(self, entry: CacheEntry):
        """移到队列前端 (最近使用)"""
        self._entries.move_to_end(entry.query)

    def clear(self):
        self._entries.clear()
        self._exact.clear()
        self.total_lookups = 0
        self.cache_hits = 0
        self.cache_misses = 0

    @property
    def hit_rate(self) -> float:
        total = self.total_lookups
        return self.cache_hits / total if total > 0 else 0.0

    @property
    def stats(self) -> dict:
        return {
            "total_lookups": self.total_lookups,
            "cache_hits": self.cache_hits,
            "cache_misses": self.cache_misses,
            "hit_rate": f"{self.hit_rate * 100:.1f}%",
            "size": len(self._entries),
            "max_size": self.max_size,
            "threshold": self.threshold,
        }

    def list_entries(self, limit: int = 10) -> list[dict]:
        return [
            {
                "query": q,
                "response_preview": e.response[:60] + "..." if len(e.response) > 60 else e.response,
                "hit_count": e.hit_count,
                "age_seconds": int(time.time() - e.created_at),
                "expired": e.is_expired(),
            }
            for q, e in list(self._entries.items())[-limit:]
        ]


# ============================================================
# 4. 演示
# ============================================================

def demo_similarity():
    """演示 TF-IDF 和相似度"""
    print("=" * 60)
    print("📐 TF-IDF 向量化 + 余弦相似度演示")
    print("=" * 60)

    vec = TfIdfVectorizer()

    # 训练语料
    corpus = [
        "什么是LLM网关 网关 限流 路由",
        "什么是语义缓存 缓存 向量 相似度",
        "什么是熔断器 熔断 保护 分布式",
        "今天天气怎么样 天气 预报",
        "API限流算法有哪些 限流 令牌桶 滑动窗口",
        "什么是数据库索引 索引 查询 性能",
    ]
    for doc in corpus:
        vec.add_document(doc)
    vec.freeze()

    test_pairs = [
        ("什么是LLM网关", "LLM网关的作用是什么"),           # 语义相近
        ("什么是语义缓存", "语义缓存的工作原理"),           # 语义相近
        ("什么是LLM网关", "今天天气怎么样"),                # 语义无关
        ("什么是熔断器", "熔断器如何保护系统"),             # 语义相近
        ("API限流算法有哪些", "有哪些常见的限流算法"),       # 语义相近
        ("什么是熔断器", "数据库索引如何工作"),              # 语义无关
        ("什么是语义缓存", "语义缓存和普通缓存有什么区别"),  # 语义相近
    ]

    print(f"\n相似度阈值: 0.85")
    print(f"{'查询A':<20} {'查询B':<25} {'相似度':<10} {'匹配?':<6}")
    print("-" * 65)
    for a, b in test_pairs:
        va = vec.compute_vector(a)
        vb = vec.compute_vector(b)
        sim = vec.cosine_similarity(va, vb)
        match = "✅" if sim >= 0.85 else "❌"
        # Truncate for display
        a_disp = a[:18]
        b_disp = b[:23]
        print(f"{a_disp:<20} {b_disp:<25} {sim:<10.4f} {match:<6}")


def demo_cache():
    """演示完整缓存流程"""
    print("\n" + "=" * 60)
    print("🔁 语义缓存引擎演示")
    print("=" * 60)

    # 初始化
    vec = TfIdfVectorizer()
    corpus = [
        "什么是LLM网关 LLM 大模型 网关 路由 限流",
        "什么是语义缓存 语义 缓存 向量 相似度 嵌入",
        "什么是熔断器 熔断 保护 分布式 系统",
        "今天天气怎么样 天气 查询 预报",
        "API限流算法 API 限流 Rate Limit 令牌桶",
    ]
    for doc in corpus:
        vec.add_document(doc)
    vec.freeze()

    # 预填充缓存 (模拟缓存了热门问题的回答)
    warmup_answers = {
        "什么是LLM网关": "LLM网关是介于应用和大模型之间的中间层，统一管理路由、鉴权、限流和监控。",
        "什么是语义缓存": "语义缓存是基于查询含义而非字面匹配的缓存技术，通过向量相似度实现。",
        "什么是熔断器": "熔断器是一种容错模式，当上游连续失败超阈值时快速拒绝请求。",
        "API限流算法有哪些": "常见限流算法：令牌桶、滑动窗口、漏桶、固定窗口计数器。",
    }
    for q, a in warmup_answers.items():
        vec_q = vec.compute_vector(q)
        cache = SemanticCache(vec, similarity_threshold=0.85, max_size=100)
        cache._entries  # trigger init

    cache = SemanticCache(vec, similarity_threshold=0.85, max_size=100)
    for q, a in warmup_answers.items():
        cache._put(q, vec.compute_vector(q), a)

    # LLM 模拟函数
    def mock_llm(query):
        time.sleep(0.3)  # 模拟 LLM 延迟
        return f"（模拟LLM回答）关于「{query}」的解释：这是AI生成的回答。"

    test_queries = [
        "什么是LLM网关",              # 精确命中
        "LLM网关是什么",              # 语义命中 (同义)
        "什么是语义缓存",              # 精确命中
        "语义缓存的原理是什么",        # 语义命中 (同义)
        "语义缓存和普通缓存有什么不同",  # 语义命中 (部分匹配)
        "什么是熔断器",                # 精确命中
        "熔断器是用来干嘛的",          # 语义命中 (同义)
        "今天天气怎么样",              # 未命中 → 模拟LLM
        "高可用架构有哪些模式",        # 未命中 → 模拟LLM
        "什么是LLM网关",              # 再次精确命中 (验证命中数)
    ]

    print(f"\n测试 {len(test_queries)} 条查询:")
    print(f"{'#':<3} {'查询':<26} {'来源':<12} {'相似度':<8} {'延迟':<8} {'命中数':<6}")
    print("-" * 65)

    for i, q in enumerate(test_queries, 1):
        resp, meta = cache.get_or_generate(q, mock_llm)
        src = meta.get("source", "?")
        sim = meta.get("similarity", "-")
        lat = f"{meta.get('latency_ms', 0)}ms"
        hits = meta.get("hit_count", 0)
        q_disp = q[:24]
        print(f"{i:<3} {q_disp:<26} {src:<12} {str(sim):<8} {lat:<8} {hits:<6}")

    print(f"\n📊 缓存统计:")
    for k, v in cache.stats.items():
        print(f"   {k}: {v}")
    print(f"\n📋 缓存条目:")
    for entry in cache.list_entries(5):
        print(f"   {entry['query']:<20} hits={entry['hit_count']} "
              f"age={entry['age_seconds']}s "
              f"{'⏰ EXPIRED' if entry['expired'] else '✅'}")


def demo_lru_eviction():
    """演示 LRU 淘汰"""
    print("\n" + "=" * 60)
    print("🗑️ LRU 淘汰演示 (max_size=3)")
    print("=" * 60)

    vec = TfIdfVectorizer()
    for doc in ["猫 狗 鸟 鱼 兔", "苹果 香蕉 橘子", "红色 蓝色 绿色", "跑步 游泳 骑车", "Python Java Go"]:
        vec.add_document(doc)
    vec.freeze()

    cache = SemanticCache(vec, similarity_threshold=0.9, max_size=3, ttl_seconds=0)

    def fake_llm(q):
        return f"回答: {q}"

    queries = ["猫", "苹果", "红色", "跑步", "Python"]
    for q in queries:
        cache.get_or_generate(q, fake_llm)
    print(f"\n插入 5 条后 (max=3):")
    for e in cache.list_entries(10):
        print(f"   {e['query']:<10} {'✅ 缓存中' if not e['expired'] else '❌ 已淘汰'}")

    # 访问 "猫" → 使它成为最近使用
    cache.get_or_generate("猫", fake_llm)
    # 再插入一条 → 应淘汰最久未用的 "苹果" 或 "红色"
    cache.get_or_generate("新查询", fake_llm)
    print(f"\n访问「猫」后插入新查询:")
    for e in cache.list_entries(10):
        print(f"   {e['query']:<10} {'✅ 缓存中' if not e['expired'] else '❌ 已淘汰'}")


def demo_ttl_expiry():
    """演示 TTL 过期"""
    print("\n" + "=" * 60)
    print("⏰ TTL 过期演示 (ttl=1s)")
    print("=" * 60)

    vec = TfIdfVectorizer()
    vec.add_document("测试 缓存 TTL")
    vec.freeze()

    cache = SemanticCache(vec, similarity_threshold=0.5, max_size=10, ttl_seconds=1)

    def fake_llm(q):
        return f"回答: {q}"

    # 插入
    resp, meta = cache.get_or_generate("测试查询", fake_llm)
    print(f"\n刚插入: \n  来源={meta['source']}")

    # 立即查询 → 命中
    resp, meta = cache.get_or_generate("测试查询", fake_llm)
    print(f"立即查询: \n  来源={meta['source']} ✅")

    # 等待过期
    print(f"等待 1.5 秒...")
    time.sleep(1.5)

    # 过期后查询 → 未命中
    resp, meta = cache.get_or_generate("测试查询", fake_llm)
    print(f"过期后查询: \n  来源={meta['source']} ⏰ TTL 过期")


if __name__ == "__main__":
    print("📦 语义缓存系统演示")
    print("=" * 60)

    demo_similarity()
    demo_cache()
    demo_lru_eviction()
    demo_ttl_expiry()
