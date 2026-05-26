"""
simple_vector_store.py — 从零搭建的向量检索器

理解原理后，再用 ChromaDB/FAISS 等专业工具。
核心：余弦相似度 + 内存存储 + JSON 持久化
"""

import math
import json


class SimpleVectorStore:
    """一个最简单的内存向量数据库"""

    def __init__(self):
        self.vectors = []       # [[vec1], [vec2], ...]
        self.texts = []         # ["文本1", "文本2", ...]
        self.metadatas = []     # [{"source": "...", ...}, ...]

    def add(self, text: str, vector: list, metadata: dict = None):
        """添加一条记录"""
        self.vectors.append(vector)
        self.texts.append(text)
        self.metadatas.append(metadata or {})

    def cosine_similarity(self, a: list, b: list) -> float:
        """计算两个向量的余弦相似度"""
        dot = sum(x * y for x, y in zip(a, b))
        norm_a = math.sqrt(sum(x * x for x in a))
        norm_b = math.sqrt(sum(x * x for x in b))
        if norm_a == 0 or norm_b == 0:
            return 0.0
        return dot / (norm_a * norm_b)

    def search(self, query_vector: list, top_k: int = 3) -> list:
        """搜索最相似的 top_k 条记录"""
        scores = []
        for i, vec in enumerate(self.vectors):
            score = self.cosine_similarity(query_vector, vec)
            scores.append((score, i))

        # 按相似度降序排列
        scores.sort(reverse=True)

        results = []
        for score, idx in scores[:top_k]:
            results.append({
                "text": self.texts[idx],
                "score": round(score, 4),
                "metadata": self.metadatas[idx]
            })
        return results

    def save(self, path: str):
        """保存到磁盘"""
        data = {
            "vectors": self.vectors,
            "texts": self.texts,
            "metadatas": self.metadatas
        }
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False)

    def load(self, path: str):
        """从磁盘加载"""
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
        self.vectors = data["vectors"]
        self.texts = data["texts"]
        self.metadatas = data["metadatas"]

    def __len__(self):
        return len(self.texts)


# ─── 测试 ────────────────────────────────────
if __name__ == "__main__":
    store = SimpleVectorStore()

    # 插入测试数据
    store.add("猫是哺乳动物，喜欢吃鱼", [0.9, 0.1, 0.0], {"topic": "动物"})
    store.add("狗是哺乳动物，喜欢出去玩", [0.8, 0.2, 0.1], {"topic": "动物"})
    store.add("Python 是一种编程语言", [0.1, 0.3, 0.9], {"topic": "编程"})
    store.add("Java 也是一种编程语言", [0.0, 0.2, 0.8], {"topic": "编程"})

    # 搜索"猫"
    query = [0.85, 0.15, 0.05]
    results = store.search(query, top_k=2)

    print("🔍 搜索 '猫' 的结果：")
    for r in results:
        print(f"  [{r['score']:.4f}] {r['text']} ({r['metadata']['topic']})")

    # 测试持久化
    store.save("/tmp/test_vector_store.json")
    store2 = SimpleVectorStore()
    store2.load("/tmp/test_vector_store.json")
    print(f"\n💾 持久化测试：加载后共 {len(store2)} 条记录 ✅")
