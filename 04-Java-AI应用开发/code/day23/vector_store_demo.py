"""
Day 23 — 向量化入库 + 相似度检索（Python 对照版）

演示：切分 → 模拟向量化 → 相似度检索 → 上下文组装
"""

import json
import math
import re


# ============================================================
# 测试文档
# ============================================================

DOCS = {
    "Spring AI 基础": """
Spring AI 是一个面向人工智能应用的 Spring 生态框架。
它的核心理念是将大语言模型（LLM）的能力以 Spring 的风格集成到 Java 应用中。
""",
    "向量数据库": """
Spring AI 支持多种向量数据库，包括 PgVector、Redis、Chroma 和 Pinecone。
PgVector 是最常用的方案，作为 PostgreSQL 扩展部署。
""",
    "RAG 流程": """
RAG（检索增强生成）是当前最流行的 AI 应用架构。
核心流程为：文档加载 → 切分 → 向量化 → 存储 → 检索 → 生成。
""",
}


# ============================================================
# 简单向量化（模拟）
# ============================================================

def simple_embed(text):
    """模拟 Embedding：用词频做简单的向量（仅用于演示）"""
    words = set(re.findall(r'[\w\u4e00-\u9fff]+', text))
    # 返回一个 10 维伪向量：字符数 + 中文字数 + 数字数 + 7个常用词标记
    chinese = sum(1 for c in text if '\u4e00' <= c <= '\u9fff')
    english = sum(1 for c in text if c.isascii() and c.isalpha())
    digits = sum(1 for c in text if c.isdigit())
    vec = [len(text) / 100, chinese / 50, english / 50, digits]
    # 加上文档中关键词的标记
    keywords = ['spring', 'ai', '向量', '数据库', 'rag', '检索', 'pgvector']
    for kw in keywords:
        vec.append(1.0 if kw.lower() in text.lower() else 0.0)
    return vec


def cosine_sim(a, b):
    """余弦相似度"""
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(x * x for x in b))
    return dot / (na * nb) if na * nb > 0 else 0


# ============================================================
# 三种切分策略（简化版）
# ============================================================

def token_chunk(text, size=200):
    """模拟 Token 切分"""
    chars = list(text)
    return [''.join(chars[i:i+size]) for i in range(0, len(chars), size)]


def paragraph_chunk(text):
    """段落切分"""
    paras = [p.strip() for p in re.split(r'\n\s*\n', text) if p.strip()]
    return paras if paras else [text]


def recursive_chunk(text, size=300):
    """递归字符切分"""
    if len(text) <= size:
        return [text]
    # 找最后的分隔符
    sep = max(text.rfind(s, 0, size) for s in ['\n\n', '\n', '。', '.'])
    if sep <= 0:
        sep = size
    return [text[:sep+1]] + recursive_chunk(text[sep+1:], size)


# ============================================================
# 向量库
# ============================================================

class VectorDB:
    def __init__(self):
        self.documents = []  # [{text, vec, source, strategy}]

    def add(self, text, source, strategy):
        vec = simple_embed(text)
        self.documents.append({
            'text': text,
            'vec': vec,
            'source': source,
            'strategy': strategy,
        })

    def search(self, query, top_k=3, threshold=0.0):
        qvec = simple_embed(query)
        scored = []
        for doc in self.documents:
            score = cosine_sim(qvec, doc['vec'])
            if score >= threshold:
                scored.append((score, doc))
        scored.sort(key=lambda x: -x[0])
        return scored[:top_k]


# ============================================================
# 主测试
# ============================================================

def main():
    print("=" * 60)
    print("📦 文档摄入")
    print("=" * 60)

    db = VectorDB()
    for name, text in DOCS.items():
        for strategy, chunk_fn in [
            ("TokenSplitter", lambda t: token_chunk(t, 100)),
            ("ParagraphSplitter", paragraph_chunk),
            ("RecursiveSplitter", lambda t: recursive_chunk(t, 150)),
        ]:
            chunks = chunk_fn(text)
            for chunk in chunks:
                db.add(chunk, name, strategy)
        print(f"  ✅ {name}: {len(text)} chars → {len(paragraph_chunk(text))} chunks")

    print(f"\n📊 向量库总计: {len(db.documents)} 个文档块")

    # 测试检索
    queries = [
        "Spring AI 是什么框架？",
        "PgVector 支持哪些功能？",
        "RAG 的核心流程是什么？",
    ]

    for query in queries:
        print()
        print("-" * 60)
        print(f"🔍 查询: {query}")
        print("-" * 60)

        # 标准 Top-K
        results = db.search(query, top_k=3)
        print(f"\n📊 Top-K 模式:")
        for i, (score, doc) in enumerate(results):
            strategy = doc['strategy'][:15]
            preview = doc['text'][:60].replace('\n', ' ')
            print(f"  #{i+1} [{strategy}] (score={score:.3f}) {preview}...")

        # 策略对比
        print(f"\n📊 策略对比模式:")
        by_strategy = {}
        for score, doc in results:
            s = doc['strategy']
            if s not in by_strategy:
                by_strategy[s] = []
            by_strategy[s].append((score, doc))

        for strategy, items in by_strategy.items():
            best = items[0]
            print(f"  [{strategy[:15]}] 最佳命中: score={best[0]:.3f}")

    print()
    print("=" * 60)
    print("✅ 检索完成")


if __name__ == '__main__':
    main()
