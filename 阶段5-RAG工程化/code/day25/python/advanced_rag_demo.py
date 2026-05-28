#!/usr/bin/env python3
"""
Day 25 — Python 对照版：高级 RAG 技术（查询重写 + HyDE + 父文档检索）

对比 Java 版的 QueryRewriteService + HydeSearchService + ParentDocService。

核心概念：
1. 查询重写（Query Rewriting）：LLM 将口语化 Query 转结构化关键词
2. HyDE (Hypothetical Document Embeddings)：假设回答做检索
3. 父文档检索（Parent Document Retrieval）：检索小块返回大块
4. 对比：直接检索 vs 重写 vs HyDE vs 父文档
"""

import json
import math
import os
from typing import List, Dict, Tuple, Optional


# ============================================================
# 第1层：模拟文档库（含块级和文档级结构）
# ============================================================

# 父文档（完整段落）
PARENT_DOCUMENTS = [
    {"id": "p1", "title": "向量数据库概述",
     "content": """向量数据库是一种专门为 AI 应用设计的数据库系统。它存储的是向量嵌入（Embedding），
通过向量相似度实现语义搜索。与传统数据库不同，向量数据库不依赖精确匹配，
而是在高维空间中寻找最相似的向量。常见的向量数据库包括 Pinecone、Milvus、Weaviate 和 PgVector。"""},

    {"id": "p2", "title": "PgVector 安装与配置",
     "content": """PgVector 是 PostgreSQL 的向量扩展。安装方式：在 PostgreSQL 中运行 CREATE EXTENSION vector。
支持 ivfflat（倒排文件）和 hnsw（分层可导航小世界）两种索引算法。
配置时需指定向量维度（如 1536 或 1024）。索引构建参数：
ivfflat 的 lists 参数控制聚类数量，hnsw 的 m 参数控制连接数。"""},

    {"id": "p3", "title": "Spring AI 集成指南",
     "content": """Spring AI 提供 ChatClient、PromptTemplate、ToolCallback 等核心组件。
集成 PgVector 时需添加 spring-ai-pgvector-store 依赖。
配置 EmbeddingModel Bean 实现类，通过 VectorStore 进行增删改查。
支持多种 Embedding 提供商：OpenAI、Ollama、Qianfan 等。"""},

    {"id": "p4", "title": "RAG 优化技巧",
     "content": """RAG 系统的性能可以从多个方面优化。查询重写可以让用户的口语化输入变成精确关键词。
HyDE 技术先生成假设回答再用它做检索，适用于短查询场景。
父文档检索在小块中搜索但在大块中返回，确保上下文完整性。
混合检索结合向量和关键词，重排序提高 TOP-K 准确率。缓存可以减少重复查询的成本。"""},
]

# 子文档（Chunk）— 每个父文档拆成 2 个块
CHUNK_DOCUMENTS = [
    {"id": "c1", "parent": "p1", "content": "向量数据库用于存储和检索向量嵌入，支持语义搜索。"},
    {"id": "c2", "parent": "p1", "content": "常见向量数据库：Pinecone、Milvus、Weaviate、PgVector。"},
    {"id": "c3", "parent": "p2", "content": "PgVector 是 PostgreSQL 扩展，安装后需建立索引。"},
    {"id": "c4", "parent": "p2", "content": "支持 ivfflat 和 hnsw 两种索引算法，各有优劣。"},
    {"id": "c5", "parent": "p3", "content": "Spring AI 核心组件：ChatClient、PromptTemplate。"},
    {"id": "c6", "parent": "p3", "content": "集成 PgVector 需配置 EmbeddingModel 和 VectorStore。"},
    {"id": "c7", "parent": "p4", "content": "查询重写将口语化输入转为精确关键词。"},
    {"id": "c8", "parent": "p4", "content": "HyDE 先生成假设回答，再用其检索，适用于短查询。"},
    {"id": "c9", "parent": "p4", "content": "父文档检索：搜索小块，返回完整上下文的大块。"},
]

# 模拟块向量（4 维）
CHUNK_VECTORS = {
    "c1": [0.85, 0.12, 0.30, 0.18],
    "c2": [0.80, 0.10, 0.25, 0.15],
    "c3": [0.20, 0.82, 0.72, 0.12],
    "c4": [0.18, 0.78, 0.68, 0.10],
    "c5": [0.12, 0.28, 0.92, 0.38],
    "c6": [0.15, 0.25, 0.88, 0.35],
    "c7": [0.75, 0.55, 0.35, 0.65],
    "c8": [0.70, 0.50, 0.30, 0.60],
    "c9": [0.72, 0.52, 0.32, 0.62],
}


# ============================================================
# 第2层：基础工具函数
# ============================================================

def cosine_similarity(a: List[float], b: List[float]) -> float:
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(y * y for y in b))
    return dot / (na * nb) if na > 0 and nb > 0 else 0


def keyword_score(query: str, text: str) -> float:
    """关键词匹配得分"""
    q_words = set(query.lower().split())
    t_words = set(text.lower().split())
    if not q_words:
        return 0
    intersection = q_words & t_words
    return len(intersection) / len(q_words)


def search_chunks(query: str, top_k: int = 3) -> List[Tuple[str, float, str]]:
    """基础检索：向量 + 关键词混合"""
    query_vec = [0.5, 0.5, 0.5, 0.5]  # 默认
    scores = []
    for chunk in CHUNK_DOCUMENTS:
        cid = chunk["id"]
        vec = CHUNK_VECTORS[cid]
        sim = cosine_similarity(query_vec, vec)
        kw = keyword_score(query, chunk["content"])
        score = sim * 0.6 + kw * 0.4
        scores.append((cid, score, chunk["content"]))

    scores.sort(key=lambda x: x[1], reverse=True)
    return scores[:top_k]


def get_parent_text(chunk_id: str) -> Optional[str]:
    """根据 chunk ID 找父文档内容"""
    chunk = next((c for c in CHUNK_DOCUMENTS if c["id"] == chunk_id), None)
    if not chunk:
        return None
    parent = next((p for p in PARENT_DOCUMENTS if p["id"] == chunk["parent"]), None)
    return parent["content"] if parent else chunk["content"]


# ============================================================
# 第3层：查询重写（Query Rewriting）
# ============================================================

def query_rewrite(original_query: str, use_real_api: bool = False) -> str:
    """
    将口语化 Query 重写为结构化搜索关键词。

    用模拟或 DeepSeek API 实现。
    """
    if use_real_api:
        return _real_rewrite(original_query)
    else:
        return _simulated_rewrite(original_query)


def _simulated_rewrite(query: str) -> str:
    """模拟查询重写"""
    # 简单规则：去掉口语化词，提取核心名词
    stop_words = {"那个", "这个", "怎么", "哪个", "帮我", "请问", "如何",
                   "a", "an", "the", "how", "what", "which", "help"}
    words = query.split()
    core_words = [w for w in words if w.lower() not in stop_words]
    rewritten = " ".join(core_words) if core_words else query

    # 补充隐含关键词
    expansions = {
        "pgvector": ["PostgreSQL 向量 扩展"],
        "spring ai": ["Spring AI 框架 ChatClient"],
        "安装": ["安装 配置 部署"],
        "配置": ["配置 设置 参数"],
        "快": ["快速 性能 优化"],
    }

    for kw, exps in expansions.items():
        if kw in query.lower():
            rewritten += " " + exps[0]
            break

    return rewritten


def _real_rewrite(query: str) -> str:
    """真实查询重写 — 调用 DeepSeek API"""
    import requests

    api_key = os.environ.get("DEEPSEEK_API_KEY", "")
    if not api_key:
        print("  ⚠️ DEEPSEEK_API_KEY 未设置，使用模拟重写")
        return _simulated_rewrite(query)

    prompt = f"""你是一个搜索查询优化专家。将用户的口语化查询重写为更精确的搜索关键词。

规则：
1. 提取核心实体和概念
2. 去掉口语化词汇（那个、这个、怎么、帮我）
3. 补充隐含的关键词
4. 保持技术术语不变

输入: {query}
重写后:"""

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
                "temperature": 0.3,
                "max_tokens": 100,
            },
            timeout=10,
        )
        return resp.json()["choices"][0]["message"]["content"].strip()
    except Exception as e:
        print(f"    ⚠️ API 重写失败: {e}")
        return _simulated_rewrite(query)


# ============================================================
# 第4层：HyDE（假设文档嵌入）
# ============================================================

def hyde_generate(query: str, use_real_api: bool = False) -> str:
    """
    HyDE：先生成假设回答（Hypothetical Answer），
    再用假设回答做检索。

    直觉：假设回答包含更多的"语义锚点"，
    比短查询更容易在向量空间中找到正确位置。
    """
    if use_real_api:
        return _real_hyde(query)
    else:
        return _simulated_hyde(query)


def _simulated_hyde(query: str) -> str:
    """模拟 HyDE 生成（用预设模板）"""
    templates = {
        "向量": "向量数据库专门用于存储和检索向量嵌入。支持语义搜索、高维空间索引。常见产品包括 PgVector、Pinecone 等。",
        "PgVector": "PgVector 是 PostgreSQL 的扩展。安装步骤：CREATE EXTENSION vector。支持 ivfflat 和 hnsw 索引。配置参数：dimensions、lists、m。",
        "Spring": "Spring AI 框架提供 ChatClient、PromptTemplate 等组件。集成 EmbeddingModel 和 VectorStore 实现 RAG。",
        "配置": "配置 PgVector 需要先安装扩展，然后创建表，添加向量列，建立索引。维度和索引类型是关键参数。",
        "优化": "RAG 优化包括：查询重写、HyDE、父文档检索、重排序。缓存可以降低延迟。评估指标：命中率、MRR、NDCG。",
    }

    hypothetical = ""
    for keyword, answer in templates.items():
        if keyword.lower() in query.lower():
            hypothetical += answer + " "

    if not hypothetical:
        hypothetical = f"关于「{query}」的说明：{query} 是 AI 工程中的重要概念。相关技术包括实现方法、配置步骤和最佳实践。"

    return hypothetical.strip()


def _real_hyde(query: str) -> str:
    """真实 HyDE — 调用 DeepSeek API 生成假设回答"""
    import requests

    api_key = os.environ.get("DEEPSEEK_API_KEY", "")
    if not api_key:
        print("  ⚠️ DEEPSEEK_API_KEY 未设置，使用模拟 HyDE")
        return _simulated_hyde(query)

    prompt = f"""请用简洁的技术文档风格，回答以下问题（假设你是 AI 专家）：

问题: {query}

回答："""

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
                "temperature": 0.3,
                "max_tokens": 200,
            },
            timeout=10,
        )
        return resp.json()["choices"][0]["message"]["content"].strip()
    except Exception as e:
        print(f"    ⚠️ HyDE API 失败: {e}")
        return _simulated_hyde(query)


# ============================================================
# 第5层：父文档检索
# ============================================================

def parent_doc_retrieval(query: str, top_k: int = 3) -> List[Dict]:
    """
    父文档检索 Pipeline：
    1. 检索小块（chunk）→ 得到最相关的小块
    2. 映射到父文档 → 找到完整段落
    3. 返回父文档内容（含上下文）
    """
    # 步骤 1：检索小块
    chunks = search_chunks(query, top_k=top_k * 2)  # 多检索一些

    # 步骤 2：去重 → 映射到父文档
    seen_parents = set()
    results = []

    for cid, score, chunk_content in chunks:
        parent_text = get_parent_text(cid)
        if parent_text and parent_text not in seen_parents:
            seen_parents.add(parent_text)
            chunk = next(c for c in CHUNK_DOCUMENTS if c["id"] == cid)
            parent = next(p for p in PARENT_DOCUMENTS if p["id"] == chunk["parent"])

            results.append({
                "chunkId": cid,
                "chunkContent": chunk_content,
                "parentTitle": parent["title"],
                "parentContent": parent_text,
                "parentLength": len(parent_text),
                "chunkScore": round(score, 4),
            })

            if len(results) >= top_k:
                break

    return results


# ============================================================
# 第6层：对比演示
# ============================================================

def test_query(query: str, label: str):
    """对同一个查询运行所有 4 种方法并对比"""
    print(f"\n{'='*60}")
    print(f"  📝 查询: \"{query}\"  ({label})")
    print(f"{'='*60}")

    # 1. 直接检索（Baseline）
    direct = search_chunks(query)
    print(f"\n  ① 直接检索:")
    for i, (cid, score, content) in enumerate(direct, 1):
        print(f"     #{i}  [{cid}] ({score:.4f}) {content[:40]}...")

    # 2. 查询重写 → 检索
    rewritten = query_rewrite(query)
    print(f"\n  ② 查询重写:")
    print(f"     原文: {query}")
    print(f"     重写: {rewritten}")
    rewritten_results = search_chunks(rewritten)
    for i, (cid, score, content) in enumerate(rewritten_results, 1):
        print(f"     #{i}  [{cid}] ({score:.4f}) {content[:40]}...")

    # 3. HyDE → 检索
    hyde_doc = hyde_generate(query)
    print(f"\n  ③ HyDE 假设回答:")
    print(f"     {hyde_doc[:80]}...")
    hyde_results = search_chunks(hyde_doc)
    for i, (cid, score, content) in enumerate(hyde_results, 1):
        print(f"     #{i}  [{cid}] ({score:.4f}) {content[:40]}...")

    # 4. 父文档检索
    parent_results = parent_doc_retrieval(query)
    print(f"\n  ④ 父文档检索:")
    for i, r in enumerate(parent_results, 1):
        print(f"     #{i}  [{r['chunkId']}] 父文档: {r['parentTitle']}")
        print(f"     块内容: {r['chunkContent'][:40]}...")
        print(f"     上下文: {r['parentContent'][:60]}...")


def demo():
    print("🐍 Day 25：高级 RAG 技术 — Python 演示")
    print("=" * 60)

    print("\n📚 文档库:")
    for p in PARENT_DOCUMENTS:
        print(f"  父文档 [{p['id']}] {p['title']}（{len(p['content'])} 字）")
    print(f"  子块: {len(CHUNK_DOCUMENTS)} 个块")

    # 测试查询
    test_query("向量数据库怎么安装配置", "口语化查询")
    test_query("Spring AI 集成向量搜索", "技术查询")
    test_query("RAG 性能优化方法", "概念查询")

    # 对比总结
    print(f"\n{'='*60}")
    print(f"  📊 四种方法对比")
    print(f"{'='*60}")
    print("""
  方法         适用场景               优势                劣势
  ───────────────────────────────────────────────────────────
  直接检索    查询精确时              简单快速             口语化查询效果差
  查询重写    用户输入随意时          大幅提升检索命中      依赖 LLM 质量
  HyDE        查询信息量不足时        短查询效果好          多一步生成延迟
  父文档检索  需要完整上下文时        保留完整语义          检索粒度粗
    """)

    print("✅ 演示完成")


if __name__ == "__main__":
    demo()
