"""
向量存储与检索模块
==================
使用 NumPy 实现基于余弦相似度的向量存储和检索。
支持向量的增删和持久化（保存/加载到磁盘）。
"""

import os
import json
import pickle
import logging
from typing import List, Dict, Optional, Tuple

import numpy as np

from . import config
from .embeddings import EmbeddingGenerator

logger = logging.getLogger(__name__)


class VectorStore:
    """
    向量存储与检索器。

    使用余弦相似度（通过归一化向量的点积）进行语义搜索。
    所有向量和元数据存储在内存中，支持序列化到磁盘。

    用法:
        >>> store = VectorStore(embedding_generator)
        >>> store.add(embeddings, texts, metadata)
        >>> results = store.search(query_embedding, top_k=3)
    """

    def __init__(self, embedding_generator: Optional[EmbeddingGenerator] = None):
        """
        初始化向量存储。

        Args:
            embedding_generator: EmbeddingGenerator 实例。
                                如果为 None，则在需要时延迟创建。
        """
        self.embedding_generator = embedding_generator

        # 内部存储
        self._vectors: List[np.ndarray] = []      # 向量列表
        self._texts: List[str] = []                # 原始文本
        self._metadata: List[Dict] = []            # 元数据
        self._dimension: Optional[int] = None      # 向量维度

        logger.info("向量存储已初始化 (空)")

    # ============================================================
    # 添加数据
    # ============================================================

    def add(
        self,
        vectors: np.ndarray,
        texts: List[str],
        metadata: Optional[List[Dict]] = None,
    ) -> int:
        """
        添加向量及其对应的文本和元数据。

        Args:
            vectors: 形状为 (n, dim) 的向量矩阵。
            texts: 对应的文本列表，长度必须为 n。
            metadata: 可选的元数据列表，长度必须为 n。

        Returns:
            int: 当前存储的总向量数。

        Raises:
            ValueError: 如果参数长度不匹配或向量维度不一致。
        """
        n = vectors.shape[0]

        if len(texts) != n:
            raise ValueError(
                f"文本数量 ({len(texts)}) 与向量数量 ({n}) 不匹配"
            )

        if metadata is not None and len(metadata) != n:
            raise ValueError(
                f"元数据数量 ({len(metadata)}) 与向量数量 ({n}) 不匹配"
            )

        # 检查向量维度一致性
        if self._dimension is None:
            self._dimension = vectors.shape[1]
        elif vectors.shape[1] != self._dimension:
            raise ValueError(
                f"向量维度不匹配: 期望 {self._dimension}，实际 {vectors.shape[1]}"
            )

        # 确保向量已 L2 归一化（余弦相似度要求）
        norms = np.linalg.norm(vectors, axis=1, keepdims=True)
        norms = np.where(norms == 0, 1.0, norms)
        vectors = vectors / norms

        # 存储
        for i in range(n):
            self._vectors.append(vectors[i])
            self._texts.append(texts[i])
            self._metadata.append(
                metadata[i] if metadata and i < len(metadata) else {}
            )

        total = len(self._vectors)
        logger.debug(f"已添加 {n} 个向量，总计 {total}")
        return total

    def add_texts(
        self,
        texts: List[str],
        metadata: Optional[List[Dict]] = None,
        batch_size: int = 32,
    ) -> int:
        """
        直接添加文本（自动生成嵌入向量）。

        Args:
            texts: 文本列表。
            metadata: 可选的元数据列表。
            batch_size: 批处理大小（用于嵌入生成）。

        Returns:
            int: 当前存储的总向量数。
        """
        if self.embedding_generator is None:
            self.embedding_generator = EmbeddingGenerator()

        # 分批生成嵌入，避免内存占用过高
        all_vectors = []

        for i in range(0, len(texts), batch_size):
            batch = texts[i:i + batch_size]
            vectors = self.embedding_generator.generate(batch)
            all_vectors.append(vectors)

        vectors = np.vstack(all_vectors) if len(all_vectors) > 1 else all_vectors[0]

        return self.add(vectors, texts, metadata)

    # ============================================================
    # 检索
    # ============================================================

    def search(
        self,
        query_embedding: np.ndarray,
        top_k: Optional[int] = None,
        threshold: Optional[float] = None,
    ) -> List[Dict]:
        """
        搜索最相似的向量。

        Args:
            query_embedding: 查询向量，形状为 (dim,) 或 (1, dim)。
            top_k: 返回结果数（默认: config.TOP_K）。
            threshold: 相似度阈值（低于此值的结果被过滤）。

        Returns:
            List[Dict]: 按相似度降序排列的结果列表，每个元素:
                - "text": 文本内容
                - "score": 余弦相似度 (0~1)
                - "metadata": 元数据字典
                - "rank": 排名
        """
        if not self._vectors:
            logger.warning("向量存储为空，无法搜索")
            return []

        top_k = top_k or config.TOP_K
        threshold = threshold if threshold is not None else config.SIMILARITY_THRESHOLD

        # 确保查询向量是 1D 且已归一化
        query_vec = query_embedding.flatten()
        query_norm = np.linalg.norm(query_vec)
        if query_norm > 0:
            query_vec = query_vec / query_norm

        # 计算余弦相似度（所有向量已经归一化，点积 = 余弦相似度）
        all_vectors = np.array(self._vectors)  # (n, dim)
        similarities = np.dot(all_vectors, query_vec)  # (n,)

        # 获取 Top-K 索引
        top_indices = np.argsort(similarities)[::-1][:top_k]

        # 构建结果
        results = []
        for rank, idx in enumerate(top_indices):
            score = float(similarities[idx])

            if score < threshold:
                continue

            results.append({
                "text": self._texts[idx],
                "score": round(score, 4),
                "metadata": self._metadata[idx],
                "rank": rank + 1,
            })

        # 如果没有结果满足阈值，返回分数最高的那个（带提示）
        if not results and similarities.size > 0:
            best_idx = int(np.argmax(similarities))
            best_score = float(similarities[best_idx])
            results.append({
                "text": self._texts[best_idx],
                "score": round(best_score, 4),
                "metadata": self._metadata[best_idx],
                "rank": 1,
                "below_threshold": True,
            })
            logger.info(f"所有结果低于阈值 {threshold}，返回最佳匹配 (score={best_score:.4f})")

        return results

    def search_with_text(
        self,
        query: str,
        top_k: Optional[int] = None,
        threshold: Optional[float] = None,
    ) -> List[Dict]:
        """
        使用文本直接搜索（自动生成查询向量）。

        Args:
            query: 查询文本。
            top_k: 返回结果数。
            threshold: 相似度阈值。

        Returns:
            List[Dict]: 搜索结果列表。
        """
        if self.embedding_generator is None:
            self.embedding_generator = EmbeddingGenerator()

        query_vec = self.embedding_generator.generate_single(query)
        return self.search(query_vec, top_k, threshold)

    # ============================================================
    # 持久化
    # ============================================================

    def save(self, filepath: Optional[str] = None) -> str:
        """
        将向量存储保存到磁盘。

        Args:
            filepath: 保存路径。默认为 config.INDEX_SAVE_DIR 下的索引文件。

        Returns:
            str: 实际保存的文件路径。
        """
        if not self._vectors:
            logger.warning("向量存储为空，跳过保存")
            return ""

        filepath = filepath or os.path.join(
            config.INDEX_SAVE_DIR, "vector_store.pkl"
        )

        os.makedirs(os.path.dirname(filepath), exist_ok=True)

        data = {
            "vectors": [v.tolist() for v in self._vectors],
            "texts": self._texts,
            "metadata": self._metadata,
            "dimension": self._dimension,
        }

        with open(filepath, "wb") as f:
            pickle.dump(data, f)

        # 同时保存一份人类可读的索引摘要
        summary_path = filepath + ".meta.json"
        self._save_summary(summary_path)

        logger.info(f"向量存储已保存: {filepath} ({len(self._vectors)} 个向量)")
        return filepath

    def load(self, filepath: Optional[str] = None) -> bool:
        """
        从磁盘加载向量存储。

        Args:
            filepath: 加载路径。默认为 config.INDEX_SAVE_DIR 下的索引文件。

        Returns:
            bool: 加载是否成功。
        """
        filepath = filepath or os.path.join(
            config.INDEX_SAVE_DIR, "vector_store.pkl"
        )

        if not os.path.exists(filepath):
            logger.warning(f"索引文件不存在: {filepath}")
            return False

        try:
            with open(filepath, "rb") as f:
                data = pickle.load(f)

            self._vectors = [np.array(v) for v in data["vectors"]]
            self._texts = data["texts"]
            self._metadata = data["metadata"]
            self._dimension = data["dimension"]

            logger.info(f"向量存储已加载: {filepath} ({len(self._vectors)} 个向量)")
            return True

        except Exception as e:
            logger.error(f"加载向量存储失败: {e}")
            return False

    def _save_summary(self, filepath: str):
        """
        保存索引摘要（JSON 格式，人类可读）。

        Args:
            filepath: 摘要文件路径。
        """
        summary = {
            "total_vectors": len(self._vectors),
            "dimension": self._dimension,
            "sources": list(set(
                m.get("source", m.get("title", "unknown"))
                for m in self._metadata
            )),
            "text_previews": [
                {"index": i, "preview": t[:80] + "..." if len(t) > 80 else t}
                for i, t in enumerate(self._texts[:10])  # 只存前 10 个预览
            ],
        }

        with open(filepath, "w", encoding="utf-8") as f:
            json.dump(summary, f, ensure_ascii=False, indent=2)

    # ============================================================
    # 状态查询
    # ============================================================

    def count(self) -> int:
        """返回存储的向量总数。"""
        return len(self._vectors)

    def clear(self):
        """清空所有数据。"""
        self._vectors.clear()
        self._texts.clear()
        self._metadata.clear()
        self._dimension = None
        logger.info("向量存储已清空")

    def get_info(self) -> Dict:
        """
        获取存储的详细信息。

        Returns:
            Dict: 包含总数、维度、来源分布等信息。
        """
        sources = {}
        for m in self._metadata:
            src = m.get("source", m.get("title", "unknown"))
            sources[src] = sources.get(src, 0) + 1

        return {
            "total_vectors": self.count(),
            "dimension": self._dimension,
            "source_distribution": sources,
            "using_fallback": (
                self.embedding_generator.is_using_fallback()
                if self.embedding_generator else None
            ),
        }

    def __len__(self) -> int:
        return self.count()

    def __repr__(self) -> str:
        return f"<VectorStore vectors={self.count()} dim={self._dimension}>"
