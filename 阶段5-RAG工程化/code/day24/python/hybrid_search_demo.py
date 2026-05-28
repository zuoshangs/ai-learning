#!/usr/bin/env python3
"""
Day 24 — Python 对照版：混合检索 + 重排 (Hybrid Search + Reranker)

对比 Java 版的 HybridSearchService + KeywordSearchService + VectorSearchService。

核心概念：
1. 语义检索（向量相似度计算）
2. 关键词检索（TF-IDF / BM25 模拟）
3. RRF 融合排序
4. LLM Reranker（模拟/真实）

注意：本 demo 为纯概念演示，使用本地模拟数据而非真实数据库。
"""

import math
import json
from typing import List, Dict, Tuple


# ============================================================
# 第1层：模拟文档库
# ============================================================

DOCUMENTS = [
    {"id": 1,  "title": "什么是向量数据库",  "content": "向量数据库是一种专门存储和检索向量嵌入的数据库系统。常用于 AI 应用中的语义搜索。"},
    {"id": 2,  "title": "PgVector 快速配置",  "content": "PgVector 是 PostgreSQL 的向量扩展，支持 ivfflat 和 hnsw 索引。配置步骤：安装扩展→创建表→添加向量列→建立索引。"},
    {"id": 3,  "title": "Spring AI 入门",  "content": "Spring AI 是 Spring 生态系统中的 AI 框架，提供 ChatClient、PromptTemplate、ToolCallback 等核心组件。"},
    {"id": 4,  "title": "PostgreSQL FTS 全文检索",  "content": "PostgreSQL 内置全文检索功能，使用 tsvector 和 tsquery 实现高效的文本匹配。"},
    {"id": 5,  "title": "语义搜索 vs 关键词搜索",  "content": "语义搜索理解查询含义，关键词搜索精确匹配字面。两者互补，混合检索是最佳实践。"},
    {"id": 6,  "title": "RRF 排序算法详解",  "content": "RRF 通过倒数排名融合多个检索结果，公式为 score = 1/(k+rank)。k 通常取 60。"},
    {"id": 7,  "title": "Docker 部署 PgVector",  "content": "使用 Docker 部署 PgVector：docker run -e POSTGRES_PASSWORD=password -d pgvector/pgvector:0.7.0-pg16"},
    {"id": 8,  "title": "Embedding 模型选择指南",  "content": "常见 Embedding 模型：text-embedding-3-small(1536维)、bge-large-zh(1024维)、m3e-base(768维)。选择依据：语言、维度、性能。"},
    {"id": 9,  "title": "Java 向量相似度计算",  "content": "余弦相似度 = A·B/(|A|×|B|) 是最常用向量相似度度量。Java 中使用 Apache Commons Math 或手动计算。"},
    {"id": 10, "title": "RAG 系统性能优化",  "content": "RAG 优化方向：查询重写、HyDE、父文档检索、混合检索、重排序、缓存。每一步都可能显著提升准确率。"},
]

# 模拟的向量嵌入（每个文档对应一个 4 维向量，仅供演示）
DOC_VECTORS: Dict[int, List[float]] = {
    1:  [0.9, 0.1, 0.3, 0.2],
    2:  [0.2, 0.8, 0.7, 0.1],
    3:  [0.1, 0.3, 0.9, 0.4],
    4:  [0.3, 0.5, 0.2, 0.9],
    5:  [0.8, 0.6, 0.4, 0.7],
    6:  [0.4, 0.3, 0.1, 0.3],
    7:  [0.1, 0.7, 0.6, 0.2],
    8:  [0.7, 0.2, 0.5, 0.8],
    9:  [0.3, 0.1, 0.8, 0.5],
    10: [0.6, 0.5, 0.3, 0.6],
}


# ============================================================
# 第2层：语义检索（向量相似度）
# ============================================================

def cosine_similarity(a: List[float], b: List[float]) -> float:
    """余弦相似度"""
    dot = sum(x * y for x, y in zip(a, b))
    norm_a = math.sqrt(sum(x * x for x in a))
    norm_b = math.sqrt(sum(y * y for y in b))
    return dot / (norm_a * norm_b) if norm_a > 0 and norm_b > 0 else 0


def simple_embed(text: str) -> List[float]:
    """
    简易 Embedding 模拟：根据关键词生成向量。
    真实场景使用 Embedding API，这里用关键词做近似。
    """
    keywords = {
        "向量": [1.0, 0.0, 0.0, 0.0],
        "配置": [0.0, 0.8, 0.2, 0.0],
        "搜索": [0.0, 0.0, 0.5, 0.5],
        "数据库": [0.7, 0.2, 0.0, 0.0],
        "Spring": [0.0, 0.2, 0.9, 0.1],
        "排序": [0.0, 0.0, 0.0, 0.3],
        "部署": [0.0, 0.7, 0.1, 0.1],
        "优化": [0.3, 0.2, 0.1, 0.6],
        "嵌入": [0.8, 0.0, 0.1, 0.0],
        "Java": [0.1, 0.1, 0.8, 0.2],
    }

    vec = [0.0, 0.0, 0.0, 0.0]
    count = 0
    for word, kv in keywords.items():
        if word in text:
            for i in range(4):
                vec[i] += kv[i]
            count += 1
    if count > 0:
        vec = [v / count for v in vec]
    return vec


def vector_search(query: str, top_k: int = 5) -> List[Tuple[int, float, str]]:
    """语义检索：向量相似度"""
    query_vec = simple_embed(query)
    # 用更真实的向量（含关键词匹配）
    if query_vec == [0.0, 0.0, 0.0, 0.0]:
        # fallback: 使用简单关键词匹配
        query_vec = [0.5, 0.5, 0.5, 0.5]

    scores = []
    for doc in DOCUMENTS:
        vec = DOC_VECTORS[doc["id"]]
        sim = cosine_similarity(query_vec, vec)
        # 再加一点关键词加分
        query_words = set(query.lower().split())
        doc_words = set(doc["content"].lower().split())
        overlap = len(query_words & doc_words)
        keyword_bonus = overlap * 0.05
        scores.append((doc["id"], sim + keyword_bonus, doc["content"][:50]))

    scores.sort(key=lambda x: x[1], reverse=True)
    return scores[:top_k]


# ============================================================
# 第3层：关键词检索（FTS 模拟）
# ============================================================

def keyword_search(query: str, top_k: int = 5) -> List[Tuple[int, float, str]]:
    """关键词检索：TF-IDF 模拟"""
    query_words = set(query.lower().split())

    results = []
    for doc in DOCUMENTS:
        doc_words = set(doc["content"].lower().split())
        # 计算 Jaccard 相似度作为关键词匹配得分
        intersection = query_words & doc_words
        union = query_words | doc_words
        if len(union) == 0:
            score = 0
        else:
            score = len(intersection) / len(union)
            # 如果标题包含关键词，额外加分
            title_words = set(doc["title"].lower().split())
            title_match = len(query_words & title_words)
            score += title_match * 0.2

        if score > 0:
            results.append((doc["id"], score, doc["content"][:50]))

    # 去重（同一个 query word 在多个文档中出现）
    seen_ids = set()
    deduped = []
    for doc_id, score, snippet in sorted(results, key=lambda x: x[1], reverse=True):
        if doc_id not in seen_ids:
            deduped.append((doc_id, score, snippet))
            seen_ids.add(doc_id)

    return deduped[:top_k]


# ============================================================
# 第4层：RRF 融合
# ============================================================

def rrf_fusion(
    vector_results: List[Tuple[int, float, str]],
    keyword_results: List[Tuple[int, float, str]],
    k: int = 60,
) -> List[Tuple[int, float, str, str]]:
    """
    RRF 融合：Reciprocal Rank Fusion

    score(d) = 1/(k + rank_1(d)) + 1/(k + rank_2(d))
    """
    # 构建排名映射
    vec_ranks = {doc_id: idx + 1 for idx, (doc_id, _, _) in enumerate(vector_results)}
    kw_ranks = {doc_id: idx + 1 for idx, (doc_id, _, _) in enumerate(keyword_results)}

    all_ids = set(vec_ranks.keys()) | set(kw_ranks.keys())
    fused = []
    for doc_id in all_ids:
        rank_v = vec_ranks.get(doc_id, k * 2)  # 如果没出现，给大排名
        rank_k = kw_ranks.get(doc_id, k * 2)
        score = 1 / (k + rank_v) + 1 / (k + rank_k)

        # 找文档信息
        doc = next((d for d in DOCUMENTS if d["id"] == doc_id), None)
        source = []
        if doc_id in vec_ranks:
            source.append("semantic")
        if doc_id in kw_ranks:
            source.append("keyword")

        fused.append((doc_id, score, doc["content"][:50] if doc else "", "+".join(source)))

    fused.sort(key=lambda x: x[1], reverse=True)
    return fused


# ============================================================
# 第5层：LLM Reranker（模拟）
# ============================================================

def llm_rerank(
    query: str,
    candidates: List[Tuple[int, float, str, str]],
    use_real_api: bool = False,
) -> List[Dict]:
    """
    LLM Reranker：对候选结果重新打分

    真实场景（use_real_api=True）调用 DeepSeek API
    模拟场景（默认）用规则评分
    """
    if use_real_api:
        return _real_llm_rerank(query, candidates)
    else:
        return _simulated_rerank(query, candidates)


def _simulated_rerank(query: str, candidates: List[Tuple]) -> List[Dict]:
    """模拟 LLM Reranker"""
    query_words = set(query.lower().split())

    results = []
    for doc_id, rrf_score, snippet, source in candidates:
        doc = next((d for d in DOCUMENTS if d["id"] == doc_id), None)
        content = doc["content"] if doc else snippet

        # 模拟 LLM 打分逻辑
        relevance = 0
        # 1. 关键词覆盖
        content_words = set(content.lower().split())
        overlap = len(query_words & content_words)
        relevance += overlap * 1.5

        # 2. 内容长度适中加分
        if 30 <= len(content) <= 150:
            relevance += 1

        # 3. 标题命中加分
        if doc and any(w in doc["title"].lower() for w in query_words):
            relevance += 2

        # 归一化到 1-5 分
        llm_score = min(5, max(1, relevance))

        final_score = rrf_score * 0.3 + (llm_score / 5) * 0.7

        results.append({
            "id": doc_id,
            "title": doc["title"] if doc else "",
            "snippet": content[:60],
            "rrfScore": round(rrf_score, 4),
            "llmScore": llm_score,
            "finalScore": round(final_score, 4),
            "source": source,
        })

    results.sort(key=lambda x: x["finalScore"], reverse=True)
    return results


def _real_llm_rerank(query: str, candidates: List[Tuple]) -> List[Dict]:
    """真实 LLM Reranker — 调用 DeepSeek API"""
    import requests
    import os

    api_key = os.environ.get("DEEPSEEK_API_KEY", "")
    if not api_key:
        print("  ⚠️ DEEPSEEK_API_KEY 未设置，使用模拟评分")
        return _simulated_rerank(query, candidates)

    results = []
    for doc_id, rrf_score, snippet, source in candidates:
        doc = next((d for d in DOCUMENTS if d["id"] == doc_id), None)
        content = doc["content"] if doc else snippet

        prompt = f"""请评估以下文档与查询的相关性，输出 1-5 分。

查询: {query}
文档: {content}

只输出一个数字 1-5，无需其他文字："""

        try:
            resp = requests.post(
                "https://api.deepseek.com/v1/chat/completions",
                headers={
                    "Content-Type": "application/json",
                    "Authorization": f"Bearer {api_key}",
                },
                json={
                    "model": "deepseek-chat",
                    "messages": [{"role": "user", "content": prompt}],
                    "temperature": 0.1,
                    "max_tokens": 5,
                },
                timeout=5,
            )
            response_text = resp.json()["choices"][0]["message"]["content"].strip()
            llm_score = min(5, max(1, int(response_text) if response_text.isdigit() else 3))
        except Exception as e:
            print(f"    ⚠️ API 请求失败: {e}，使用模拟分")
            llm_score = 3

        final_score = rrf_score * 0.3 + (llm_score / 5) * 0.7
        results.append({
            "id": doc_id,
            "title": doc["title"] if doc else "",
            "snippet": content[:60],
            "rrfScore": round(rrf_score, 4),
            "llmScore": llm_score,
            "finalScore": round(final_score, 4),
            "source": source,
        })

    results.sort(key=lambda x: x["finalScore"], reverse=True)
    return results


# ============================================================
# 第6层：主流程演示
# ============================================================

def print_results(stage: str, results: List):
    """格式化打印结果"""
    print(f"\n{'='*60}")
    print(f"  {stage}")
    print(f"{'='*60}")
    for i, r in enumerate(results, 1):
        if isinstance(r, tuple):
            doc_id, score, snippet = r[:3]
            print(f"  #{i}  doc[{doc_id}]  score={score:.4f}  {snippet}")
        elif isinstance(r, dict):
            src = r.get("source", "")
            print(f"  #{i}  doc[{r['id']}]  {r.get('title','')}")
            print(f"       RRF={r.get('rrfScore',0):.4f}  LLM={r.get('llmScore',0)}"
                  f"  Final={r.get('finalScore',0):.4f}  [{src}]")
            if r.get('snippet'):
                print(f"       {r['snippet']}")


def demo():
    print("🐍 Day 24：混合检索 + 重排 — Python 演示")
    print("=" * 60)

    queries = [
        "向量数据库配置",
        "Spring AI PgVector",
        "语义搜索排序",
    ]

    for q_idx, query in enumerate(queries, 1):
        print(f"\n{'#'*60}")
        print(f"  查询 {q_idx}: \"{query}\"")
        print(f"{'#'*60}")

        # 1. 语义检索
        vec_results = vector_search(query, top_k=5)
        print_results("步骤 1 — 语义检索（向量相似度）", vec_results)

        # 2. 关键词检索
        kw_results = keyword_search(query, top_k=5)
        print_results("步骤 2 — 关键词检索（FTS 模拟）", kw_results)

        # 3. RRF 融合
        fused = rrf_fusion(vec_results, kw_results)
        print_results("步骤 3 — RRF 融合排序", fused)

        # 4. LLM Reranker
        reranked = llm_rerank(query, fused, use_real_api=False)
        print_results("步骤 4 — LLM Reranker 重排", reranked)

    print("\n" + "=" * 60)
    print("✅ 演示完成")
    print("\n📊 对比总结")
    print("  语义检索: 理解含义，适合语义匹配")
    print("  关键词检索: 精确命中，适合专业术语")
    print("  RRF 融合: 1/(60+rank_v) + 1/(60+rank_k)")
    print("  LLM Rerank: 综合评分 = RRF×0.3 + LLM×0.7")


if __name__ == "__main__":
    demo()
