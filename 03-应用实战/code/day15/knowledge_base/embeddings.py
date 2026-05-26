"""
文本嵌入生成模块
================
负责将文本转换为向量表示，支持:
1. Sentence-Transformer 模型（默认，高质量）
2. 简单哈希备用方案（当 sentence-transformers 不可用时）
"""

import os
import re
import hashlib
import logging
from typing import List, Optional

import numpy as np

from . import config

logger = logging.getLogger(__name__)


class EmbeddingGenerator:
    """
    文本嵌入生成器。

    使用 Sentence-Transformer 将文本转换为稠密向量。
    当 sentence-transformers 未安装时，自动降级为基于哈希的简单嵌入。

    用法:
        >>> generator = EmbeddingGenerator()
        >>> vectors = generator.generate(["你好", "世界"])
        >>> print(vectors.shape)  # (2, 384)
    """

    def __init__(self, model_name: Optional[str] = None):
        """
        初始化嵌入生成器。

        Args:
            model_name: Sentence-Transformer 模型名称。
                       默认为 config.EMBEDDING_MODEL。

        Raises:
            ValueError: 如果模型加载失败且没有备用方案。
        """
        self.model_name = model_name or config.EMBEDDING_MODEL
        self.model = None
        self._use_fallback = False

        # 尝试加载 Sentence-Transformer 模型
        self._load_model()

    def _load_model(self):
        """加载 Sentence-Transformer 模型，失败时降级到备用方案。"""
        try:
            from sentence_transformers import SentenceTransformer

            logger.info(f"正在加载嵌入模型: {self.model_name} ...")
            logger.info(f"  镜像源: {os.environ.get('HF_ENDPOINT', '默认')}")

            self.model = SentenceTransformer(self.model_name)

            # 获取向量维度
            test_emb = self.model.encode(["test"])
            self.dimension = test_emb.shape[1]

            logger.info(f"  ✓ 模型加载成功! 向量维度: {self.dimension}")

        except ImportError:
            logger.warning(
                "⚠️  sentence-transformers 未安装，使用备用嵌入方案。\n"
                "   建议安装: pip install sentence-transformers"
            )
            self._init_fallback()

        except Exception as e:
            logger.warning(
                f"⚠️  模型加载失败: {e}\n"
                f"   降级使用备用嵌入方案。"
            )
            self._init_fallback()

    def _init_fallback(self):
        """初始化备用嵌入方案。"""
        self._use_fallback = True
        self.dimension = 128  # 固定维度
        logger.info(f"  备用嵌入模式，向量维度: {self.dimension}")

    def generate(self, texts: List[str]) -> np.ndarray:
        """
        将文本列表转换为向量矩阵。

        Args:
            texts: 文本列表。

        Returns:
            np.ndarray: 形状为 (len(texts), dimension) 的向量矩阵。

        Raises:
            ValueError: 如果 texts 为空。
        """
        if not texts:
            raise ValueError("输入文本列表不能为空")

        # 清理文本
        cleaned = [self._clean_text(t) for t in texts]

        if self._use_fallback:
            return self._fallback_embed(cleaned)
        else:
            return self._model_embed(cleaned)

    def _model_embed(self, texts: List[str]) -> np.ndarray:
        """
        使用 Sentence-Transformer 生成嵌入。

        Args:
            texts: 已清洗的文本列表。

        Returns:
            np.ndarray: 向量矩阵。
        """
        try:
            embeddings = self.model.encode(
                texts,
                convert_to_numpy=True,
                show_progress_bar=False,
                normalize_embeddings=True,  # L2 归一化，方便余弦相似度
            )
            return embeddings

        except Exception as e:
            logger.error(f"模型编码失败: {e}，降级到备用方案。")
            self._use_fallback = True
            return self._fallback_embed(texts)

    def _clean_text(self, text: str) -> str:
        """
        清洗文本：去除非必要空白、控制字符。

        Args:
            text: 原始文本。

        Returns:
            str: 清洗后的文本。
        """
        # 替换多个空白为一个空格
        text = re.sub(r"\s+", " ", text)
        # 移除控制字符（保留换行等可见字符）
        text = re.sub(r"[\x00-\x08\x0b\x0c\x0e-\x1f]", "", text)
        return text.strip()

    # ================================================================
    # 备用嵌入方案
    # ================================================================

    def _fallback_embed(self, texts: List[str]) -> np.ndarray:
        """
        基于哈希的备用嵌入方案。

        将文本的哈希值映射到固定维度的向量空间。
        虽然不是真正的语义嵌入，但可以保证:
        1. 相同文本 → 相同向量（确定性）
        2. 不同文本 → 不同向量（高概率）

        Args:
            texts: 文本列表。

        Returns:
            np.ndarray: 形状为 (len(texts), 128) 的向量矩阵。
        """
        embeddings = []

        for text in texts:
            vec = self._hash_to_vector(text)
            embeddings.append(vec)

        result = np.array(embeddings)

        # L2 归一化
        norms = np.linalg.norm(result, axis=1, keepdims=True)
        norms = np.where(norms == 0, 1.0, norms)
        result = result / norms

        return result

    def _hash_to_vector(self, text: str, dim: int = 128) -> np.ndarray:
        """
        将文本哈希到固定维度的向量。

        策略: 使用文本的不同 n-gram 特征生成多个哈希值，
        映射到向量空间的不同位置。

        Args:
            text: 输入文本。
            dim: 向量维度。

        Returns:
            np.ndarray: 形状为 (dim,) 的向量。
        """
        vec = np.zeros(dim)

        if not text:
            return vec

        # 1. 整体哈希
        h = hashlib.md5(text.encode("utf-8")).hexdigest()
        seed = int(h[:8], 16)
        rng = np.random.RandomState(seed)
        vec += rng.randn(dim) * 0.5

        # 2. 单词级特征
        words = re.findall(r"\w+", text.lower())
        for word in words:
            h = hashlib.md5(word.encode("utf-8")).hexdigest()
            idx = int(h[:4], 16) % dim
            val = (int(h[4:8], 16) / 65535) * 2 - 1
            vec[idx] += val

        # 3. 字符二元组特征
        bigrams = [text[i:i+2] for i in range(len(text) - 1)]
        for bg in set(bigrams):
            h = hashlib.md5(bg.encode("utf-8")).hexdigest()
            idx = int(h[:6], 16) % dim
            vec[idx] += 0.3

        return vec

    def generate_single(self, text: str) -> np.ndarray:
        """
        为单段文本生成嵌入向量。

        Args:
            text: 输入文本。

        Returns:
            np.ndarray: 形状为 (dimension,) 的向量。
        """
        return self.generate([text])[0]

    def get_dimension(self) -> int:
        """返回嵌入向量的维度。"""
        return self.dimension

    def is_using_fallback(self) -> bool:
        """检查是否正在使用备用嵌入方案。"""
        return self._use_fallback

    def __repr__(self) -> str:
        mode = "备用模式" if self._use_fallback else f"模型: {self.model_name}"
        return f"<EmbeddingGenerator dim={self.dimension} mode={mode}>"
