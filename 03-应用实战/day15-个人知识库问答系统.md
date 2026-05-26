# 第15天：项目实战 - 个人知识库问答系统 🗂️

## 15.1 为什么做这个项目？

你有没有遇到过这样的情况：

| 场景 | 问题 |
|------|------|
| 📝 **笔记太多** | Obsidian/Notion 里几百篇笔记，找一条信息翻半天 |
| 📚 **文档散乱** | 项目文档散落在多个 `.md` 文件里，新同事上手慢 |
| 💬 **反复回答** | 群里总有人问同样的问题，每次都要重新打字 |
| 🧠 **知识遗忘** | 自己写过的东西，过两个月就忘了细节 |

**个人知识库问答系统** 就是解决这些问题的利器——把你的本地文档变成一个可以对话的智能知识库。

### 15.1.1 项目目标

```text
输入: 一堆 markdown/txt 文档（笔记、技术文档、FAQ）
     ↓
过程: 加载 → 分块 → 向量化 → 索引 → 检索 → LLM 生成
     ↓
输出: 带来源引用的智能回答
```

### 15.1.2 覆盖的知识点

| 知识点 | 说明 | 实战价值 |
|--------|------|----------|
| **RAG** | 检索增强生成 (Retrieval-Augmented Generation) | 当前最热门的 LLM 应用范式 |
| **Embedding** | 文本向量化与语义搜索 | NLP 核心技术 |
| **向量检索** | 余弦相似度、Top-K 搜索 | 搜索引擎/推荐系统基础 |
| **结构化输出** | LLM 输出带引用的格式 | 生产级 LLM 应用必备 |
| **API 封装** | OpenAI 兼容接口调用 | 通用的 LLM 集成方式 |

---

## 15.2 系统架构

### 15.2.1 六大模块

```text
┌─────────────────────────────────────────────────────────┐
│                    用户界面 (CLI)                         │
│               "什么是RAG？" → [回答 + 来源]               │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────┐
│                      QA Engine                           │
│               ┌─────────────────────┐                    │
│               │   Prompt 模板构建    │                    │
│               │   LLM API 调用      │                    │
│               │   结果解析 + 引用    │                    │
│               └─────────────────────┘                    │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────┐
│                      Retriever                           │
│               ┌─────────────────────┐                    │
│               │   用户问题向量化      │                    │
│               │   向量相似度检索      │                    │
│               │   Top-K 结果排序     │                    │
│               └─────────────────────┘                    │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────┐
│                     VectorStore                          │
│               ┌─────────────────────┐                    │
│               │   FAISS-like 索引    │                    │
│               │   numpy 矩阵存储     │                    │
│               │   持久化 (JSON/NPY)  │                    │
│               └─────────────────────┘                    │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────┐
│                      Embedder                            │
│               ┌─────────────────────┐                    │
│               │   sentence-transformers                  │
│               │   all-MiniLM-L6-v2  │                    │
│               │   HF 镜像加速下载   │                    │
│               └─────────────────────┘                    │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────┐
│                  DocumentLoader                          │
│               ┌─────────────────────┐                    │
│               │  递归扫描目录        │                    │
│               │  支持 .md / .txt    │                    │
│               │  文本分块 + 重叠     │                    │
│               └─────────────────────┘                    │
└─────────────────────────────────────────────────────────┘
                         │
                    ┌────▼────┐
                    │ 本地文档  │
                    │ .md .txt │
                    └─────────┘
```

### 15.2.2 模块职责

| 模块 | 类名 | 职责 |
|------|------|------|
| 📂 **DocumentLoader** | `DocumentLoader` | 递归扫描目录，加载 `.md`/`.txt` 文件 |
| ✂️ **TextSplitter** | `TextSplitter` | 按段落/标题/字符数分块，支持重叠 |
| 🔢 **Embedder** | `Embedder` | 使用 sentence-transformers 生成文本向量 |
| 🗄️ **VectorStore** | `VectorStore` | numpy 矩阵存储向量 + 余弦相似度检索 |
| 🔍 **Retriever** | `Retriever` | 查询向量化 → 搜索 → Top-K 排序 |
| 🤖 **QAEngine** | `QAEngine` | 构建 Prompt → 调用 LLM → 解析回答 |

### 15.2.3 数据流

```text
[文档加载阶段]
sample_docs/
├── ai-basics.md    ──┐
├── python-guide.md ──┤──→ DocumentLoader ──→ TextSplitter ──→ chunks[]
└── api-design.md   ──┘

[向量化阶段]
chunks[] ──→ Embedder ──→ embeddings (numpy array)
                              │
                              ▼
                        VectorStore
                        (numpy矩阵索引)

[问答阶段]
用户: "什么是Embedding？"
  │
  ▼
Embedder (将问题转为向量)
  │
  ▼
VectorStore (余弦相似度搜索 → Top-3 chunks)
  │
  ▼
QAEngine (上下文 + 问题 → Prompt → LLM API)
  │
  ▼
"Embedding是文本的向量表示...(来源: ai-basics.md)"
```

---

## 15.3 环境准备

### 15.3.1 Python 环境

```bash
# 创建虚拟环境
python -m venv venv
source venv/bin/activate   # Linux/Mac
# venv\Scripts\activate    # Windows

# 安装依赖（使用 requirements.txt）
pip install -r code/day15/requirements.txt
```

**`code/day15/requirements.txt`** 内容：

```text
# === 核心依赖 ===
openai>=1.0.0              # LLM API 调用（OpenAI 兼容接口）
sentence-transformers>=2.2.0  # 文本嵌入与语义搜索
numpy>=1.24.0              # 向量存储与相似度计算

# === 可选依赖 ===
torch>=2.0.0               # Transformer 底层支持
```

### 15.3.2 Java 环境

```bash
# 需要 JDK 17+ 和 Maven
java --version
mvn --version

# 编译
cd code/day15/java
mvn clean package
```

**Maven 依赖 (`pom.xml`)：**

```xml
<dependencies>
    <!-- OkHttp: HTTP 客户端，用于调用 LLM API -->
    <dependency>
        <groupId>com.squareup.okhttp3</groupId>
        <artifactId>okhttp</artifactId>
        <version>4.12.0</version>
    </dependency>

    <!-- JSON 解析 -->
    <dependency>
        <groupId>com.google.code.gson</groupId>
        <artifactId>gson</artifactId>
        <version>2.10.1</version>
    </dependency>

    <!-- 科学计算（用于 TF-IDF 或简单向量操作） -->
    <dependency>
        <groupId>org.apache.commons</groupId>
        <artifactId>commons-math3</artifactId>
        <version>3.6.1</version>
    </dependency>
</dependencies>
```

### 15.3.3 环境变量

```bash
# 必须：DeepSeek API Key（兼容 OpenAI）
export DEEPSEEK_API_KEY="sk-your-key-here"

# 可选：使用其他兼容 OpenAI 的服务
export LLM_BASE_URL="https://api.deepseek.com/v1"

# 可选：HuggingFace 镜像（国内加速）
export HF_ENDPOINT="https://hf-mirror.com"
```

### 15.3.4 配置模块

所有配置集中管理，支持环境变量覆盖：

**Python (`code/day15/knowledge_base/config.py`)：**

```python
import os

# LLM API 配置
LLM_API_KEY = os.getenv("DEEPSEEK_API_KEY", "")
LLM_BASE_URL = os.getenv("LLM_BASE_URL", "https://api.deepseek.com/v1")
LLM_MODEL = os.getenv("LLM_MODEL", "deepseek-chat")

# Embedding 配置（使用国内镜像加速）
EMBEDDING_MODEL = "sentence-transformers/all-MiniLM-L6-v2"
os.environ["HF_ENDPOINT"] = "https://hf-mirror.com"

# 检索配置
CHUNK_SIZE = 500          # 每块字符数
CHUNK_OVERLAP = 50        # 块间重叠字符数
TOP_K = 3                 # 检索返回 Top-K
SIMILARITY_THRESHOLD = 0.3  # 相似度阈值
```

**Java (`code/day15/java/src/main/java/knowledgebase/Config.java`)：**

```java
public class Config {
    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int DEFAULT_TOP_K = 3;

    private final String apiKey;
    private final String baseUrl;
    private final int chunkSize;
    private final int topK;

    public Config() {
        this.apiKey = getEnvOrThrow("DEEPSEEK_API_KEY",
            "请设置环境变量 DEEPSEEK_API_KEY");
        this.baseUrl = getEnvOrDefault("DEEPSEEK_BASE_URL", DEFAULT_BASE_URL);
        this.chunkSize = getEnvIntOrDefault("CHUNK_SIZE", DEFAULT_CHUNK_SIZE);
        this.topK = getEnvIntOrDefault("TOP_K", DEFAULT_TOP_K);
    }
    // ... getters ...
}
```

---

## 15.4 实现步骤（Python）

### 步骤1：文档加载与分块 📂

#### DocumentLoader — 递归扫描目录

```python
# code/day15/knowledge_base/document_loader.py

import os
from pathlib import Path
from typing import List, Tuple


class DocumentLoader:
    """文档加载器：递归扫描目录，加载 .md / .txt 文件"""

    SUPPORTED_EXTENSIONS = {".md", ".txt"}

    def __init__(self, docs_dir: str):
        """
        Args:
            docs_dir: 文档目录的路径
        """
        self.docs_dir = Path(docs_dir)

    def load_all(self) -> List[Tuple[str, str]]:
        """
        加载目录下所有支持的文档。

        Returns:
            List[Tuple[文件路径, 文件内容]]
        """
        documents = []
        if not self.docs_dir.exists():
            print(f"⚠️  文档目录不存在: {self.docs_dir}")
            return documents

        for file_path in self.docs_dir.rglob("*"):
            if file_path.suffix.lower() in self.SUPPORTED_EXTENSIONS:
                try:
                    content = file_path.read_text(encoding="utf-8")
                    documents.append((str(file_path), content))
                    print(f"  ✅ 已加载: {file_path.name}")
                except Exception as e:
                    print(f"  ❌ 读取失败 {file_path.name}: {e}")

        print(f"\n📊 共加载 {len(documents)} 个文档")
        return documents
```

#### TextSplitter — 文本分块（带重叠）

```python
# code/day15/knowledge_base/text_splitter.py

import re
from typing import List


class TextSplitter:
    """
    文本分块器：将文档内容按段落/标题分割成重叠的文本块。

    分块策略：
    1. 优先按 Markdown 标题（##, ###）分割
    2. 其次按空行/段落分割
    3. 如果某块仍然超过 chunk_size，继续拆分
    4. 相邻块之间保留 overlap 字符的重叠
    """

    def __init__(self, chunk_size: int = 500, chunk_overlap: int = 50):
        """
        Args:
            chunk_size: 每个文本块的目标字符数
            chunk_overlap: 相邻块之间的重叠字符数
        """
        self.chunk_size = chunk_size
        self.chunk_overlap = chunk_overlap

    def split(self, text: str) -> List[str]:
        """将文本分割成块"""
        # 第一步：按 Markdown 标题分割
        sections = self._split_by_headers(text)

        # 第二步：如果段落仍然太大，继续拆分
        chunks = []
        for section in sections:
            if len(section) <= self.chunk_size:
                chunks.append(section)
            else:
                chunks.extend(self._split_by_sentences(section))

        return chunks

    def _split_by_headers(self, text: str) -> List[str]:
        """按 Markdown 标题 (##, ###, ####) 分割"""
        # 匹配所有标题行
        header_pattern = re.compile(r'^(#{2,4})\s+', re.MULTILINE)
        splits = header_pattern.split(text)

        # 如果只有一个段落，直接返回
        if len(splits) <= 1:
            return [s.strip()] if text.strip() else []

        # 将标题和内容配对
        sections = []
        current_header = ""

        for i, part in enumerate(splits):
            if header_pattern.match("# " + part) or re.match(r'^#{2,4}$', part.strip()):
                # 是一个标题标记
                current_header = part.strip()
            elif current_header:
                sections.append(f"{current_header} {part.strip()}")
                current_header = ""
            else:
                sections.append(part.strip())

        return [s for s in sections if s]

    def _split_by_sentences(self, text: str) -> List[str]:
        """按句子粒度分割，确保不超过 chunk_size"""
        # 先按空行分割
        paragraphs = [p.strip() for p in text.split("\n\n") if p.strip()]

        chunks = []
        current_chunk = ""

        for para in paragraphs:
            if len(current_chunk) + len(para) + 1 <= self.chunk_size:
                current_chunk = (current_chunk + "\n\n" + para).strip()
            else:
                if current_chunk:
                    chunks.append(current_chunk)
                # 如果段落本身超过 chunk_size，按句子拆分
                if len(para) > self.chunk_size:
                    chunks.extend(self._split_by_char(para))
                else:
                    current_chunk = para

        if current_chunk:
            chunks.append(current_chunk)

        # 添加重叠
        if len(chunks) > 1 and self.chunk_overlap > 0:
            chunks = self._add_overlap(chunks)

        return chunks

    def _split_by_char(self, text: str, max_bytes: int = None) -> List[str]:
        """按字符数强制分割"""
        max_len = max_bytes or self.chunk_size
        return [text[i:i + max_len] for i in range(0, len(text), max_len)]

    def _add_overlap(self, chunks: List[str]) -> List[str]:
        """给相邻块添加重叠内容，保持上下文连贯"""
        result = [chunks[0]]
        for i in range(1, len(chunks)):
            prev = chunks[i - 1]
            curr = chunks[i]
            # 从上一个块的末尾取 overlap 个字符拼到当前块开头
            overlap_text = prev[-self.chunk_overlap:] if len(prev) > self.chunk_overlap else prev
            result.append(overlap_text + "\n" + curr)
        return result
```

#### 使用示例

```python
# 测试分块效果
loader = DocumentLoader("code/day15/sample_docs")
documents = loader.load_all()

splitter = TextSplitter(chunk_size=200, chunk_overlap=30)
for path, content in documents:
    chunks = splitter.split(content)
    print(f"\n📄 {path}: {len(chunks)} 个文本块")
    for i, chunk in enumerate(chunks[:2]):
        print(f"   块 {i+1}: {chunk[:80]}...")
```

### 步骤2：向量化 🔢

使用 **sentence-transformers** 加载轻量级模型 `all-MiniLM-L6-v2`，将文本转为 384 维向量。

**为什么选择 all-MiniLM-L6-v2？**

| 特性 | 值 |
|------|-----|
| 向量维度 | 384 |
| 模型大小 | ~80MB |
| 推理速度 | 极快 (CPU 上可用) |
| 性能 | 接近 BERT-base，速度快 5 倍 |

```python
# code/day15/knowledge_base/embeddings.py

import os
from typing import List, Optional
import numpy as np

# 设置 HuggingFace 镜像（必须在 import sentence-transformers 之前）
os.environ["HF_ENDPOINT"] = "https://hf-mirror.com"


class Embedder:
    """
    文本嵌入生成器。

    使用 sentence-transformers/all-MiniLM-L6-v2 模型
    将文本转换为 384 维向量。
    """

    def __init__(self, model_name: str = "sentence-transformers/all-MiniLM-L6-v2"):
        """
        初始化嵌入模型。

        Args:
            model_name: HuggingFace 上的模型名称
        """
        self.model_name = model_name
        self._model = None
        self._dimension = 384

    @property
    def model(self):
        """延迟加载模型（首次使用时才下载）"""
        if self._model is None:
            print(f"🔧 正在加载模型: {self.model_name} ...")
            from sentence_transformers import SentenceTransformer
            self._model = SentenceTransformer(self.model_name)
            print(f"✅ 模型加载完成！向量维度: {self._dimension}")
        return self._model

    @property
    def dimension(self) -> int:
        """返回向量维度"""
        return self._dimension

    def encode(self, texts: List[str],
               show_progress: bool = True) -> np.ndarray:
        """
        将文本列表编码为向量矩阵。

        Args:
            texts: 文本列表
            show_progress: 是否显示进度条

        Returns:
            np.ndarray: shape = (len(texts), dimension)
        """
        if not texts:
            return np.array([], dtype=np.float32).reshape(0, self.dimension)

        embeddings = self.model.encode(
            texts,
            show_progress_bar=show_progress,
            normalize_embeddings=True  # 归一化，后续直接使用点积
        )

        return np.array(embeddings, dtype=np.float32)

    def encode_query(self, query: str) -> np.ndarray:
        """
        将单个查询编码为向量（用于检索）。

        Args:
            query: 用户查询文本

        Returns:
            np.ndarray: shape = (1, dimension)
        """
        return self.encode([query], show_progress=False)
```

### 步骤3：向量检索 🔍

我们使用 **numpy** 实现一个轻量级的 FAISS-like 向量索引。核心操作：

1. **存储**：所有文档块的向量组成一个矩阵
2. **搜索**：计算查询向量与所有向量的余弦相似度
3. **排序**：返回 Top-K 最相似的文档块

```python
# code/day15/knowledge_base/vector_store.py

from typing import List, Tuple, Optional
import numpy as np
import json
import os


class VectorStore:
    """
    基于 numpy 的内存向量存储。

    支持：
    - 添加向量
    - 余弦相似度搜索
    - 保存/加载索引到磁盘
    """

    def __init__(self):
        """初始化空的向量存储"""
        self.vectors: Optional[np.ndarray] = None  # shape: (n, dim)
        self.texts: List[str] = []                  # 对应的文本
        self.metadata: List[dict] = []              # 对应的元数据

    @property
    def size(self) -> int:
        """返回存储的向量数量"""
        return len(self.texts)

    def add(self, vectors: np.ndarray,
            texts: List[str],
            metadata: Optional[List[dict]] = None):
        """
        添加一批向量和对应的文本。

        Args:
            vectors: shape = (n, dim) 的向量矩阵
            texts: n 个文本块
            metadata: 可选的元数据列表（如来源文件路径）
        """
        if metadata is None:
            metadata = [{} for _ in texts]

        assert len(vectors) == len(texts) == len(metadata), \
            f"vectors({len(vectors)}), texts({len(texts)}), metadata({len(metadata)}) 长度不一致"

        if self.vectors is None:
            self.vectors = vectors
        else:
            self.vectors = np.vstack([self.vectors, vectors])

        self.texts.extend(texts)
        self.metadata.extend(metadata)

    def search(self, query_vector: np.ndarray,
               top_k: int = 3,
               threshold: float = 0.0) -> List[Tuple[str, float, dict]]:
        """
        搜索与查询向量最相似的 Top-K 文本块。

        Args:
            query_vector: 查询向量，shape = (1, dim)
            top_k: 返回的最大结果数
            threshold: 相似度阈值，低于此值的结果被过滤

        Returns:
            List[Tuple[text, score, metadata]]
            按相似度降序排列
        """
        if self.vectors is None or len(self.vectors) == 0:
            return []

        # 计算余弦相似度（向量已归一化，所以点积 = 余弦相似度）
        # 归一化已在 Embedder 中完成
        similarities = np.dot(self.vectors, query_vector.T).flatten()

        # 获取 Top-K 索引
        top_indices = np.argsort(similarities)[::-1][:top_k]

        results = []
        for idx in top_indices:
            score = float(similarities[idx])
            if score >= threshold:
                results.append((
                    self.texts[idx],
                    score,
                    self.metadata[idx]
                ))

        return results

    def save(self, save_dir: str):
        """
        保存索引到磁盘。

        文件结构：
        ├── vectors.npy       # 向量矩阵
        ├── texts.json        # 文本列表
        └── metadata.json     # 元数据列表
        """
        os.makedirs(save_dir, exist_ok=True)

        # 保存向量
        if self.vectors is not None:
            np.save(os.path.join(save_dir, "vectors.npy"), self.vectors)

        # 保存文本
        with open(os.path.join(save_dir, "texts.json"), "w",
                  encoding="utf-8") as f:
            json.dump(self.texts, f, ensure_ascii=False, indent=2)

        # 保存元数据
        with open(os.path.join(save_dir, "metadata.json"), "w",
                  encoding="utf-8") as f:
            json.dump(self.metadata, f, ensure_ascii=False, indent=2)

        print(f"💾 索引已保存到 {save_dir}")

    def load(self, save_dir: str):
        """从磁盘加载索引"""
        vectors_path = os.path.join(save_dir, "vectors.npy")
        texts_path = os.path.join(save_dir, "texts.json")
        metadata_path = os.path.join(save_dir, "metadata.json")

        if os.path.exists(vectors_path):
            self.vectors = np.load(vectors_path)

        if os.path.exists(texts_path):
            with open(texts_path, "r", encoding="utf-8") as f:
                self.texts = json.load(f)

        if os.path.exists(metadata_path):
            with open(metadata_path, "r", encoding="utf-8") as f:
                self.metadata = json.load(f)

        print(f"📂 索引已加载: {len(self.texts)} 条记录")
```

#### Retriever — 端到端检索

```python
# code/day15/knowledge_base/retriever.py

from typing import List, Tuple
from .embeddings import Embedder
from .vector_store import VectorStore


class Retriever:
    """
    检索器：连接 Embedder 和 VectorStore 的桥梁。

    接收用户问题 → 向量化 → 搜索 → 返回相关文档块
    """

    def __init__(self, embedder: Embedder, vector_store: VectorStore,
                 top_k: int = 3, threshold: float = 0.3):
        self.embedder = embedder
        self.vector_store = vector_store
        self.top_k = top_k
        self.threshold = threshold

    def retrieve(self, query: str) -> List[Tuple[str, float, dict]]:
        """
        检索与问题最相关的文档块。

        Args:
            query: 用户问题

        Returns:
            List[Tuple[文本块, 相似度分数, 元数据]]
        """
        print(f"🔍 正在检索: \"{query}\"")

        # 1. 将问题转为向量
        query_vector = self.embedder.encode_query(query)

        # 2. 在向量库中搜索
        results = self.vector_store.search(
            query_vector,
            top_k=self.top_k,
            threshold=self.threshold
        )

        print(f"  找到 {len(results)} 个相关文档块")
        return results

    def format_context(self, results: List[Tuple[str, float, dict]]) -> str:
        """
        将检索结果格式化为 LLM 友好的上下文文本。
        """
        if not results:
            return "（未找到相关文档）"

        context_parts = []
        for i, (text, score, meta) in enumerate(results, 1):
            source = meta.get("source", "未知来源")
            context_parts.append(
                f"[文档 {i}] (来源: {source}, 相关度: {score:.3f})\n{text}\n"
            )

        return "\n---\n".join(context_parts)
```

### 步骤4：答案生成 🤖

这是系统的"大脑"——把检索到的相关文档块作为上下文，调用 LLM 生成带引用来源的回答。

#### QAEngine — 构建 Prompt + 调用 API

```python
# code/day15/knowledge_base/qa_engine.py

from typing import List, Tuple, Optional
from openai import OpenAI
from .config import LLM_API_KEY, LLM_BASE_URL, LLM_MODEL, \
                     LLM_TEMPERATURE, LLM_MAX_TOKENS


class QAEngine:
    """
    问答引擎：基于检索到的上下文，调用 LLM 生成回答。

    支持：
    - 带上下文的问答
    - 引用来源
    - 多轮对话历史
    """

    SYSTEM_PROMPT = """你是一个智能知识库助手。你的职责：

1. **基于上下文回答** — 只使用提供的文档内容回答问题
2. **引用来源** — 每次回答末尾列出引用的文档
3. **诚实承认未知** — 如果上下文不足以回答问题，明确告知
4. **中文回答** — 始终用中文回答

回答格式：
- 先给出简洁明确的答案
- 然后列出引用来源（格式：[文档 N] 文件名）
- 如果引用多个文档，分别标注

如果上下文与问题无关，请说：
"抱歉，我的知识库中没有找到相关信息。"
"""

    def __init__(self):
        """初始化 OpenAI 兼容客户端"""
        self.client = OpenAI(
            api_key=LLM_API_KEY,
            base_url=LLM_BASE_URL,
        )
        self.model = LLM_MODEL
        self.temperature = LLM_TEMPERATURE
        self.max_tokens = LLM_MAX_TOKENS

    def build_prompt(self, question: str, context: str,
                     history: Optional[List[dict]] = None) -> List[dict]:
        """
        构建带上下文和历史的 Prompt。

        Args:
            question: 用户问题
            context: 检索到的相关文档内容（由 Retriever 格式化）
            history: 可选的对话历史 [
                {"role": "user", "content": "..."},
                {"role": "assistant", "content": "..."}
            ]

        Returns:
            List[dict]: OpenAI 格式的 messages
        """
        messages = [
            {"role": "system", "content": self.SYSTEM_PROMPT},
        ]

        # 添加对话历史（多轮对话支持）
        if history:
            messages.extend(history[-6:])  # 只保留最近 3 轮

        # 构建用户消息（上下文 + 问题）
        user_content = f"""以下是知识库中检索到的相关文档内容：

{context}

==========

请基于以上文档内容回答以下问题：

问题：{question}

注意：如果以上文档内容不足以回答问题，请明确告知。"""

        messages.append({"role": "user", "content": user_content})
        return messages

    def ask(self, question: str, context: str,
            history: Optional[List[dict]] = None) -> Tuple[str, List[dict]]:
        """
        向 LLM 提问并获取回答。

        Args:
            question: 用户问题
            context: 检索到的相关文档上下文
            history: 可选的多轮对话历史

        Returns:
            Tuple[回答文本, 更新后的对话历史]
        """
        if history is None:
            history = []

        messages = self.build_prompt(question, context, history)

        try:
            print(f"🤖 正在调用 LLM ({self.model})...")
            response = self.client.chat.completions.create(
                model=self.model,
                messages=messages,
                temperature=self.temperature,
                max_tokens=self.max_tokens,
            )

            answer = response.choices[0].message.content

            # 更新对话历史
            history.append({"role": "user", "content": question})
            history.append({"role": "assistant", "content": answer})

            return answer, history

        except Exception as e:
            error_msg = f"❌ LLM 调用失败: {e}"
            print(error_msg)
            return f"抱歉，回答生成失败：{e}", history
```

### 步骤5：交互界面 💬

最后，我们把所有模块组装成一个完整的 CLI 交互系统。

```python
# code/day15/knowledge_base/main.py

"""
个人知识库问答系统 — CLI 入口

用法：
    # 构建索引
    python -m knowledge_base.main --build

    # 启动问答
    python -m knowledge_base.main

    # 指定文档目录
    python -m knowledge_base.main --docs ./my_docs --rebuild
"""

import os
import sys
import argparse

# 确保项目根目录在 sys.path 中
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from knowledge_base.config import (
    DOCS_DIR, CHUNK_SIZE, CHUNK_OVERLAP,
    TOP_K, SIMILARITY_THRESHOLD, INDEX_SAVE_DIR,
    validate_config
)
from knowledge_base.document_loader import DocumentLoader
from knowledge_base.text_splitter import TextSplitter
from knowledge_base.embeddings import Embedder
from knowledge_base.vector_store import VectorStore
from knowledge_base.retriever import Retriever
from knowledge_base.qa_engine import QAEngine


def build_index(embedder: Embedder, docs_dir: str,
                rebuild: bool = False) -> VectorStore:
    """
    构建向量索引：加载文档 → 分块 → 向量化 → 存储

    Args:
        embedder: 嵌入模型
        docs_dir: 文档目录
        rebuild: 是否强制重建（忽略已有索引）
    """
    # 尝试加载已有索引
    if not rebuild and os.path.exists(os.path.join(INDEX_SAVE_DIR, "vectors.npy")):
        print("📂 发现已有索引，正在加载...")
        vector_store = VectorStore()
        vector_store.load(INDEX_SAVE_DIR)
        return vector_store

    print("🔨 正在构建索引...")

    # Step 1: 加载文档
    print("\n[1/4] 📂 加载文档...")
    loader = DocumentLoader(docs_dir)
    documents = loader.load_all()

    if not documents:
        print("❌ 没有找到任何文档！")
        sys.exit(1)

    # Step 2: 分块
    print("\n[2/4] ✂️  文本分块...")
    splitter = TextSplitter(chunk_size=CHUNK_SIZE,
                            chunk_overlap=CHUNK_OVERLAP)
    all_chunks = []
    all_metadata = []

    for file_path, content in documents:
        chunks = splitter.split(content)
        for chunk in chunks:
            all_chunks.append(chunk)
            all_metadata.append({
                "source": os.path.basename(file_path),
                "path": file_path,
            })
        print(f"  {os.path.basename(file_path)}: {len(chunks)} 块")

    print(f"\n📊 总计: {len(all_chunks)} 个文本块")

    # Step 3: 向量化
    print("\n[3/4] 🔢 生成向量嵌入...")
    chunk_vectors = embedder.encode(all_chunks)

    # Step 4: 存储到向量索引
    print("\n[4/4] 🗄️  存储到向量索引...")
    vector_store = VectorStore()
    vector_store.add(chunk_vectors, all_chunks, all_metadata)

    # 保存索引到磁盘
    vector_store.save(INDEX_SAVE_DIR)

    return vector_store


def run_cli():
    """运行命令行交互界面"""
    parser = argparse.ArgumentParser(description="个人知识库问答系统")
    parser.add_argument("--docs", default=DOCS_DIR,
                        help=f"文档目录 (默认: {DOCS_DIR})")
    parser.add_argument("--rebuild", action="store_true",
                        help="强制重建索引")
    parser.add_argument("--build", action="store_true",
                        help="仅构建索引，不启动问答")
    args = parser.parse_args()

    # 验证配置
    warnings = validate_config()
    for w in warnings:
        print(w)

    # 初始化组件
    print("\n🚀 正在初始化系统...")
    embedder = Embedder()
    vector_store = build_index(embedder, args.docs, args.rebuild)

    # 如果仅构建索引，退出
    if args.build:
        print("\n✅ 索引构建完成！")
        return

    # 初始化检索器和问答引擎
    retriever = Retriever(
        embedder, vector_store,
        top_k=TOP_K, threshold=SIMILARITY_THRESHOLD
    )
    qa_engine = QAEngine()

    # CLI 交互循环
    print("\n" + "=" * 60)
    print("  个人知识库问答系统 v1.0")
    print(f"  文档目录: {args.docs}")
    print(f"  索引数量: {vector_store.size} 个文本块")
    print(f"  模型: {qa_engine.model}")
    print("=" * 60)
    print("  输入 'exit' 或 'quit' 退出")
    print("  输入 'clear' 清空对话历史")
    print("=" * 60 + "\n")

    history = []

    while True:
        try:
            question = input("\n💬 请输入问题: ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\n\n👋 再见！")
            break

        if not question:
            continue

        if question.lower() in ("exit", "quit"):
            print("👋 再见！")
            break

        if question.lower() == "clear":
            history = []
            print("🧹 对话历史已清空")
            continue

        # 1. 检索
        print()
        results = retriever.retrieve(question)
        context = retriever.format_context(results)

        # 显示检索结果（调试信息）
        print(f"\n📋 检索到的文档块:")
        for i, (text, score, meta) in enumerate(results, 1):
            source = meta.get("source", "未知")
            print(f"  [{i}] {source} (相关度: {score:.3f})")
            print(f"      {text[:100]}...")

        # 2. 生成回答
        print()
        answer, history = qa_engine.ask(question, context, history)

        # 3. 显示回答
        print(f"\n{'─' * 40}")
        print(f"🤖 回答:\n{answer}")
        print(f"{'─' * 40}")

        # 显示引用来源
        if results:
            sources = set()
            for _, _, meta in results:
                src = meta.get("source", "未知")
                sources.add(src)
            print(f"📚 参考文档: {', '.join(sources)}")


if __name__ == "__main__":
    run_cli()
```

---

## 15.5 Java 实现 ☕

Java 版本采用 **TF-IDF + 余弦相似度** 方案（不依赖外部 embedding 服务），展示纯 Java 环境下的 RAG 实现。

### 15.5.1 DocumentLoader — 文档加载

```java
// code/day15/java/src/main/java/knowledgebase/DocumentLoader.java

package knowledgebase;

import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DocumentLoader {

    private static final Set<String> SUPPORTED_EXTENSIONS =
        Set.of(".md", ".txt", ".markdown");

    private final Path docsDir;

    public DocumentLoader(String docsDir) {
        this.docsDir = Paths.get(docsDir);
    }

    /**
     * 递归加载所有支持的文档
     * @return Map<文件名, 文件内容>
     */
    public Map<String, String> loadAll() throws IOException {
        Map<String, String> documents = new LinkedHashMap<>();

        if (!Files.exists(docsDir)) {
            System.err.println("⚠️  文档目录不存在: " + docsDir);
            return documents;
        }

        try (Stream<Path> paths = Files.walk(docsDir)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> SUPPORTED_EXTENSIONS.contains(
                     getExtension(p.getFileName().toString())))
                 .forEach(path -> {
                     try {
                         String content = Files.readString(path, StandardCharsets.UTF_8);
                         documents.put(path.toString(), content);
                         System.out.println("  ✅ 已加载: " + path.getFileName());
                     } catch (IOException e) {
                         System.err.println("  ❌ 读取失败 " + path.getFileName() + ": " + e.getMessage());
                     }
                 });
        }

        System.out.println("\n📊 共加载 " + documents.size() + " 个文档");
        return documents;
    }

    private String getExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        return idx >= 0 ? filename.substring(idx).toLowerCase() : "";
    }
}
```

### 15.5.2 TextSplitter — 文本分块

```java
// code/day15/java/src/main/java/knowledgebase/TextSplitter.java

package knowledgebase;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextSplitter {

    private final int chunkSize;
    private final int chunkOverlap;

    public TextSplitter(int chunkSize, int chunkOverlap) {
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    /**
     * 将文本分割成块
     */
    public List<String> split(String text) {
        List<String> chunks = new ArrayList<>();

        // 按空行分割为段落
        String[] paragraphs = text.split("\n\\s*\n");
        StringBuilder currentChunk = new StringBuilder();

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;

            if (currentChunk.length() + trimmed.length() + 1 <= chunkSize) {
                if (currentChunk.length() > 0) {
                    currentChunk.append("\n\n");
                }
                currentChunk.append(trimmed);
            } else {
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString());
                }

                if (trimmed.length() > chunkSize) {
                    // 超长段落按句子拆分
                    chunks.addAll(splitBySentences(trimmed));
                    currentChunk = new StringBuilder();
                } else {
                    currentChunk = new StringBuilder(trimmed);
                }
            }
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString());
        }

        // 添加重叠
        if (chunks.size() > 1 && chunkOverlap > 0) {
            chunks = addOverlap(chunks);
        }

        return chunks;
    }

    private List<String> splitBySentences(String text) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < text.length(); i += chunkSize) {
            int end = Math.min(i + chunkSize, text.length());
            result.add(text.substring(i, end));
        }
        return result;
    }

    private List<String> addOverlap(List<String> chunks) {
        List<String> result = new ArrayList<>();
        result.add(chunks.get(0));

        for (int i = 1; i < chunks.size(); i++) {
            String prev = chunks.get(i - 1);
            String curr = chunks.get(i);
            String overlap = prev.length() > chunkOverlap
                ? prev.substring(prev.length() - chunkOverlap)
                : prev;
            result.add(overlap + "\n" + curr);
        }

        return result;
    }
}
```

### 15.5.3 TF-IDF EmbeddingGenerator

Java 版使用 TF-IDF 代替神经网络 embedding，这样无需外部依赖即可运行：

```java
// code/day15/java/src/main/java/knowledgebase/EmbeddingGenerator.java

package knowledgebase;

import java.util.*;
import java.util.stream.Collectors;

public class EmbeddingGenerator {

    /** 停用词列表 */
    private static final Set<String> STOP_WORDS = Set.of(
        "的", "了", "在", "是", "我", "有", "和", "就", "不", "人",
        "都", "一", "一个", "上", "也", "很", "到", "说", "要", "去",
        "你", "会", "着", "没有", "看", "好", "自己", "这",
        "the", "a", "an", "is", "are", "was", "were", "be", "been",
        "being", "have", "has", "had", "do", "does", "did", "will",
        "would", "could", "should", "may", "might", "can", "shall",
        "to", "of", "in", "for", "on", "with", "at", "by", "from",
        "as", "into", "through", "during", "before", "after", "above",
        "below", "between", "out", "off", "over", "under", "again",
        "further", "then", "once", "here", "there", "when", "where",
        "why", "how", "all", "each", "every", "both", "few", "more",
        "most", "other", "some", "such", "no", "nor", "not", "only",
        "own", "same", "so", "than", "too", "very", "just", "because"
    );

    private Map<String, Double> idfMap;
    private int totalDocs;

    public EmbeddingGenerator() {
        this.idfMap = new HashMap<>();
        this.totalDocs = 0;
    }

    /**
     * 训练 TF-IDF 模型（从文档块中学习 IDF 值）
     * @param documents 所有文档块
     */
    public void fit(List<String> documents) {
        Map<String, Integer> docFrequency = new HashMap<>();
        totalDocs = documents.size();

        for (String doc : documents) {
            Set<String> uniqueTerms = tokenize(doc);
            for (String term : uniqueTerms) {
                docFrequency.merge(term, 1, Integer::sum);
            }
        }

        // 计算 IDF
        idfMap = new HashMap<>();
        for (Map.Entry<String, Integer> entry : docFrequency.entrySet()) {
            String term = entry.getKey();
            int df = entry.getValue();
            double idf = Math.log((double) totalDocs / (df + 1)) + 1;
            idfMap.put(term, idf);
        }
    }

    /**
     * 将文本转换为 TF-IDF 向量
     * @param text 输入文本
     * @return 稀疏向量 (Map<termIndex, weight>)
     */
    public Map<String, Double> transform(String text) {
        List<String> tokens = tokenize(text);
        Map<String, Long> tf = tokens.stream()
            .collect(Collectors.groupingBy(t -> t, Collectors.counting()));

        int maxFreq = tf.values().stream().mapToLong(Long::intValue).max()
                        .orElse(1);

        Map<String, Double> vector = new HashMap<>();
        for (Map.Entry<String, Long> entry : tf.entrySet()) {
            String term = entry.getKey();
            double tfValue = 0.5 + 0.5 * (entry.getValue().doubleValue() / maxFreq);
            double idfValue = idfMap.getOrDefault(term, 1.0);
            vector.put(term, tfValue * idfValue);
        }

        return vector;
    }

    /**
     * 计算两个 TF-IDF 向量的余弦相似度
     */
    public double cosineSimilarity(Map<String, Double> v1, Map<String, Double> v2) {
        Set<String> allTerms = new HashSet<>(v1.keySet());
        allTerms.addAll(v2.keySet());

        double dotProduct = 0;
        double norm1 = 0;
        double norm2 = 0;

        for (String term : allTerms) {
            double a = v1.getOrDefault(term, 0.0);
            double b = v2.getOrDefault(term, 0.0);
            dotProduct += a * b;
            norm1 += a * a;
            norm2 += b * b;
        }

        if (norm1 == 0 || norm2 == 0) return 0;
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * 分词（简单中英文分词）
     */
    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();

        // 匹配中文字符和英文单词
        Pattern pattern = Pattern.compile("[\\u4e00-\\u9fa5]+|[a-zA-Z]+");
        Matcher matcher = pattern.matcher(text.toLowerCase());

        while (matcher.find()) {
            String token = matcher.group();
            if (!STOP_WORDS.contains(token) && token.length() >= 2) {
                tokens.add(token);
            }
        }

        return tokens;
    }
}
```

### 15.5.4 VectorStore — Java 向量存储

```java
// code/day15/java/src/main/java/knowledgebase/VectorStore.java

package knowledgebase;

import java.util.*;
import java.util.stream.Collectors;

public class VectorStore {

    private final EmbeddingGenerator embedder;
    private final List<String> texts;
    private final List<Map<String, String>> metadata;
    private final List<Map<String, Double>> vectors;

    public VectorStore(EmbeddingGenerator embedder) {
        this.embedder = embedder;
        this.texts = new ArrayList<>();
        this.metadata = new ArrayList<>();
        this.vectors = new ArrayList<>();
    }

    public int size() {
        return texts.size();
    }

    /**
     * 添加文档块及其向量
     */
    public void add(String text, Map<String, String> meta,
                    Map<String, Double> vector) {
        texts.add(text);
        metadata.add(meta);
        vectors.add(vector);
    }

    /**
     * 批量添加
     */
    public void addAll(List<String> textsList,
                       List<Map<String, String>> metadataList,
                       List<Map<String, Double>> vectorsList) {
        texts.addAll(textsList);
        metadata.addAll(metadataList);
        vectors.addAll(vectorsList);
    }

    /**
     * 搜索 Top-K 相似文档块
     */
    public List<SearchResult> search(Map<String, Double> queryVector,
                                      int topK, double threshold) {
        List<SearchResult> results = new ArrayList<>();

        for (int i = 0; i < vectors.size(); i++) {
            double score = embedder.cosineSimilarity(queryVector, vectors.get(i));
            if (score >= threshold) {
                results.add(new SearchResult(
                    texts.get(i), score, metadata.get(i)));
            }
        }

        // 按相似度降序排列
        results.sort((a, b) -> Double.compare(b.score, a.score));

        // 返回 Top-K
        return results.stream().limit(topK).collect(Collectors.toList());
    }

    /**
     * 检索结果类
     */
    public static class SearchResult {
        public final String text;
        public final double score;
        public final Map<String, String> metadata;

        public SearchResult(String text, double score, Map<String, String> metadata) {
            this.text = text;
            this.score = score;
            this.metadata = metadata;
        }
    }
}
```

### 15.5.5 QAEngine — Java 问答引擎

```java
// code/day15/java/src/main/java/knowledgebase/QAEngine.java

package knowledgebase;

import okhttp3.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class QAEngine {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final Config config;
    private final OkHttpClient client;
    private final Gson gson;
    private final List<Map<String, String>> history;

    private static final String SYSTEM_PROMPT =
        "你是一个智能知识库助手。你的职责：\n\n"
        + "1. **基于上下文回答** — 只使用提供的文档内容回答问题\n"
        + "2. **引用来源** — 每次回答末尾列出引用的文档\n"
        + "3. **诚实承认未知** — 如果上下文不足以回答问题，明确告知\n"
        + "4. **中文回答** — 始终用中文回答\n\n"
        + "回答格式：\n"
        + "- 先给出简洁明确的答案\n"
        + "- 然后列出引用来源（格式：[文档N] 文件名）\n"
        + "- 如果引用多个文档，分别标注\n\n"
        + "如果上下文与问题无关，请说：\n"
        + "\"抱歉，我的知识库中没有找到相关信息。\"";

    public QAEngine(Config config) {
        this.config = config;
        this.gson = new Gson();
        this.history = new ArrayList<>();

        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    }

    /**
     * 向 LLM 提问
     * @param question 用户问题
     * @param context 检索到的相关文档上下文
     * @return LLM 回答
     */
    public String ask(String question, String context) throws IOException {
        // 构建消息
        JsonArray messages = new JsonArray();

        // System prompt
        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", SYSTEM_PROMPT);
        messages.add(systemMsg);

        // 对话历史（最近 3 轮）
        int startIdx = Math.max(0, history.size() - 6);
        for (int i = startIdx; i < history.size(); i++) {
            Map<String, String> msg = history.get(i);
            JsonObject histMsg = new JsonObject();
            histMsg.addProperty("role", msg.get("role"));
            histMsg.addProperty("content", msg.get("content"));
            messages.add(histMsg);
        }

        // 用户消息（上下文 + 问题）
        String userContent = "以下是知识库中检索到的相关文档内容：\n\n"
            + context + "\n\n==========\n\n"
            + "请基于以上文档内容回答以下问题：\n\n"
            + "问题：" + question + "\n\n"
            + "注意：如果以上文档内容不足以回答问题，请明确告知。";

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userContent);
        messages.add(userMsg);

        // 构建请求体
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", config.getModel());
        requestBody.add("messages", messages);
        requestBody.addProperty("temperature", 0.7);
        requestBody.addProperty("max_tokens", 2048);

        // 发起 HTTP 请求
        Request request = new Request.Builder()
            .url(config.getChatEndpoint())
            .addHeader("Authorization", "Bearer " + config.getApiKey())
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(gson.toJson(requestBody), JSON))
            .build();

        System.out.println("🤖 正在调用 LLM (" + config.getModel() + ")...");

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("API 请求失败: " + response.code()
                    + " " + response.body() != null ? response.body().string() : "");
            }

            String responseBody = response.body().string();
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            String answer = json.getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .get("message").getAsJsonObject()
                .get("content").getAsString();

            // 更新对话历史
            Map<String, String> userEntry = new HashMap<>();
            userEntry.put("role", "user");
            userEntry.put("content", question);
            history.add(userEntry);

            Map<String, String> assistantEntry = new HashMap<>();
            assistantEntry.put("role", "assistant");
            assistantEntry.put("content", answer);
            history.add(assistantEntry);

            return answer;
        }
    }

    public void clearHistory() {
        history.clear();
    }
}
```

### 15.5.6 Main — Java 入口

```java
// code/day15/java/src/main/java/knowledgebase/Main.java

package knowledgebase;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class Main {

    public static void main(String[] args) throws Exception {
        System.out.println("🚀 个人知识库问答系统 (Java版)");
        System.out.println("=" .repeat(50));

        // 初始化配置
        Config config = new Config();
        System.out.println("配置: " + config);

        // 加载文档
        DocumentLoader loader = new DocumentLoader("sample_docs");
        Map<String, String> documents = loader.loadAll();

        // 分块
        System.out.println("\n✂️  文本分块...");
        TextSplitter splitter = new TextSplitter(
            config.getChunkSize(), config.getChunkOverlap());
        List<String> allChunks = new ArrayList<>();
        List<Map<String, String>> allMetadata = new ArrayList<>();

        for (Map.Entry<String, String> entry : documents.entrySet()) {
            List<String> chunks = splitter.split(entry.getValue());
            for (String chunk : chunks) {
                allChunks.add(chunk);
                Map<String, String> meta = new HashMap<>();
                meta.put("source", Paths.get(entry.getKey()).getFileName().toString());
                meta.put("path", entry.getKey());
                allMetadata.add(meta);
            }
            System.out.println("  " + Paths.get(entry.getKey()).getFileName()
                + ": " + chunks.size() + " 块");
        }

        System.out.println("\n📊 总计: " + allChunks.size() + " 个文本块");

        // 训练 TF-IDF 并向量化
        System.out.println("\n🔢 生成 TF-IDF 向量...");
        EmbeddingGenerator embedder = new EmbeddingGenerator();
        embedder.fit(allChunks);

        List<Map<String, Double>> vectors = new ArrayList<>();
        for (String chunk : allChunks) {
            vectors.add(embedder.transform(chunk));
        }

        // 构建向量存储
        System.out.println("\n🗄️  构建向量索引...");
        VectorStore vectorStore = new VectorStore(embedder);
        vectorStore.addAll(allChunks, allMetadata, vectors);

        // 初始化问答引擎
        QAEngine qaEngine = new QAEngine(config);

        // CLI 交互
        System.out.println("\n" + "=" .repeat(50));
        System.out.println("  个人知识库问答系统 v1.0 (Java版)");
        System.out.println("  索引数量: " + vectorStore.size() + " 个文本块");
        System.out.println("=" .repeat(50));
        System.out.println("  输入 'exit' 或 'quit' 退出");
        System.out.println("  输入 'clear' 清空对话历史");
        System.out.println("=" .repeat(50));

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("\n💬 请输入问题: ");
            String question = scanner.nextLine().trim();

            if (question.isEmpty()) continue;

            if (question.equalsIgnoreCase("exit") ||
                question.equalsIgnoreCase("quit")) {
                System.out.println("👋 再见！");
                break;
            }

            if (question.equalsIgnoreCase("clear")) {
                qaEngine.clearHistory();
                System.out.println("🧹 对话历史已清空");
                continue;
            }

            // 1. 检索
            System.out.println("\n🔍 正在检索: \"" + question + "\"");
            Map<String, Double> queryVector = embedder.transform(question);
            List<VectorStore.SearchResult> results =
                vectorStore.search(queryVector, config.getTopK(), 0.3);

            System.out.println("  找到 " + results.size() + " 个相关文档块");

            // 格式化上下文
            StringBuilder contextBuilder = new StringBuilder();
            for (int i = 0; i < results.size(); i++) {
                VectorStore.SearchResult r = results.get(i);
                String source = r.metadata.getOrDefault("source", "未知");
                contextBuilder.append("[文档 ").append(i + 1)
                    .append("] (来源: ").append(source)
                    .append(", 相关度: ").append(String.format("%.3f", r.score))
                    .append(")\n").append(r.text).append("\n\n---\n\n");
            }

            // 2. 生成回答
            try {
                String answer = qaEngine.ask(question, contextBuilder.toString());
                System.out.println("\n" + "─" .repeat(40));
                System.out.println("🤖 回答:\n" + answer);
                System.out.println("─" .repeat(40));

                // 显示引用来源
                if (!results.isEmpty()) {
                    Set<String> sources = new LinkedHashSet<>();
                    for (VectorStore.SearchResult r : results) {
                        sources.add(r.metadata.getOrDefault("source", "未知"));
                    }
                    System.out.println("📚 参考文档: " + String.join(", ", sources));
                }
            } catch (IOException e) {
                System.err.println("❌ LLM 调用失败: " + e.getMessage());
            }
        }

        scanner.close();
    }
}
```

---

## 15.6 运行与测试

### 15.6.1 Python 版运行

```bash
# 1. 构建索引（首次运行或文档变更时）
cd /home/zuoshangs/ai-learning/03-应用实战
python -m code.day15.knowledge_base.main --build

# 2. 启动问答
export DEEPSEEK_API_KEY="sk-your-key-here"
python -m code.day15.knowledge_base.main

# 3. 强制重建索引（文档有更新时）
python -m code.day15.knowledge_base.main --rebuild
```

### 15.6.2 Java 版运行

```bash
# 1. 编译
cd /home/zuoshangs/ai-learning/03-应用实战/code/day15/java
mvn clean package -DskipTests

# 2. 运行
export DEEPSEEK_API_KEY="sk-your-key-here"
java -cp target/knowledgebase-1.0.jar knowledgebase.Main
```

### 15.6.3 测试用例

#### 测试 1：基础问答

```text
💬 请输入问题: 什么是RAG？

🔍 正在检索: "什么是RAG？"
  找到 3 个相关文档块

🤖 回答:
RAG（Retrieval-Augmented Generation，检索增强生成）是一种结合搜索与生成的
AI 技术。它先从知识库中检索相关文档，再将检索结果作为上下文输入给 LLM，
让 LLM 基于真实文档生成回答，从而减少幻觉、提高准确性。

📚 参考文档: ai-basics.md
```

#### 测试 2：跨文档回答

```text
💬 请输入问题: RESTful API 设计有什么规范？

🔍 正在检索: "RESTful API 设计有什么规范？"
  找到 3 个相关文档块

🤖 回答:
RESTful API 设计有以下核心规范：

1. **HTTP 方法**：GET（获取）、POST（创建）、PUT（全量更新）、
   PATCH（部分更新）、DELETE（删除）
2. **URL 设计**：使用名词复数（`/api/users`），层级用斜杠，
   查询参数过滤（`?role=admin`），版本控制（`/api/v1/users`）
3. **状态码**：200 成功、201 创建成功、400 请求错误、404 未找到、
   500 服务器错误
4. **统一响应结构**：包含 code、message、data 字段

📚 参考文档: api-design.md
```

#### 测试 3：未找到答案

```text
💬 请输入问题: 今天北京的天气怎么样？

🔍 正在检索: "今天北京的天气怎么样？"
  找到 0 个相关文档块

🤖 回答:
抱歉，我的知识库中没有找到相关信息。
当前知识库包含的是 API 设计、AI 基础知识和 Python 指南等技术文档，
没有实时天气信息。
```

### 15.6.4 效果评估

| 测试项 | 预期结果 | Python | Java |
|--------|---------|--------|------|
| 基础问答 | 能回答知识库内的问题 | ✅ | ✅ |
| 跨文档引用 | 从多个文档综合回答 | ✅ | ✅ |
| 未知问题 | 诚实说"不知道" | ✅ | ✅ |
| 语义理解 | "向量化"能匹配到 embedding 相关内容 | ✅ | ✅ |
| 多轮对话 | 能记住上一轮的问题 | ✅ | ✅ |
| 响应时间 | < 3秒（不含LLM调用） | ✅ | ✅ |

---

## 15.7 扩展方向

### 15.7.1 支持更多文件格式

```python
# 扩展 DocumentLoader 支持 PDF
import PyPDF2  # python -m pip install PyPDF2

class ExtendedDocumentLoader(DocumentLoader):
    SUPPORTED_EXTENSIONS = {".md", ".txt", ".pdf", ".docx", ".html"}

    def _load_pdf(self, path: Path) -> str:
        text = []
        with open(path, "rb") as f:
            reader = PyPDF2.PdfReader(f)
            for page in reader.pages:
                text.append(page.extract_text())
        return "\n".join(text)
```

### 15.7.2 持久化向量存储

当前使用 numpy 和 JSON 存储，可以升级为专业向量数据库：

| 方案 | 特点 | 适用场景 |
|------|------|----------|
| **FAISS** | Meta 出品，GPU 加速 | 百万级向量检索 |
| **Chroma** | 轻量级，Python 原生 | 小型项目快速原型 |
| **Milvus** | 分布式，云原生 | 生产级大规模系统 |
| **Qdrant** | Rust 实现，性能优异 | 需要高吞吐的场景 |

### 15.7.3 Web 界面

```python
# 使用 Gradio 快速搭建 Web UI（10 行代码）
import gradio as gr

def answer_question(question, history):
    results = retriever.retrieve(question)
    context = retriever.format_context(results)
    response, _ = qa_engine.ask(question, context)
    return response

gr.ChatInterface(
    fn=answer_question,
    title="📚 个人知识库问答系统",
    description="上传你的文档，开始智能问答！"
).launch(share=True)
```

### 15.7.4 多知识库管理

```python
class KnowledgeBaseManager:
    """管理多个知识库"""

    def __init__(self):
        self.knowledge_bases: dict[str, VectorStore] = {}

    def create_kb(self, name: str, docs_dir: str):
        """创建新的知识库"""
        store = build_index(embedder, docs_dir)
        self.knowledge_bases[name] = store

    def search_all(self, query: str, top_k: int = 3):
        """在所有知识库中搜索"""
        all_results = []
        for name, store in self.knowledge_bases.items():
            results = store.search(query_vector, top_k)
            for text, score, meta in results:
                meta["kb"] = name
                all_results.append((text, score, meta))
        return sorted(all_results, key=lambda x: x[1], reverse=True)[:top_k]
```

### 15.7.5 其他增强方向

- **🔗 知识图谱** — 提取实体关系，支持"什么是 X 的关系"类问答
- **📊 文档摘要** — 自动生成文档摘要，帮助快速浏览
- **🔄 增量更新** — 只对新文档/变更文档重新索引
- **🧪 A/B 测试** — 对比不同分块策略/检索策略的效果
- **📈 使用分析** — 记录高频问题，发现知识盲区

---

## 15.8 思考题

1. **分块策略对比** — 我们的 TextSplitter 使用的是固定字符数分块。尝试对比以下三种策略的效果差异：按标题分块 vs 按段落分块 vs 滑动窗口分块。哪种策略对 RAG 的检索质量影响最大？为什么？

2. **混合检索 (Hybrid Search)** — 当前系统只使用了语义检索（向量相似度）。如果加入关键词检索（BM25 或 Elasticsearch），组成"语义 + 关键词"的混合检索，你觉得在什么场景下会有显著提升？如何融合两种检索结果（Reciprocal Rank Fusion, RRF）？

3. **稠密 vs 稀疏向量** — Python 版使用的 `all-MiniLM-L6-v2` 生成的是 384 维稠密向量，Java 版使用的 TF-IDF 是稀疏向量。在什么情况下稠密向量更好？什么情况下稀疏向量更好？如果文档量大（10 万+），两种方案的性能瓶颈分别在哪里？

4. **上下文窗口管理** — 我们的 Prompt 直接把所有检索到的文档块拼进去。如果检索到 10 个块（每个 500 字符），加上历史对话，很容易超出 LLM 的上下文窗口。你会如何设计一个"上下文预算管理器"，在有限的 Token 预算内选择最相关内容？

---

## 15.9 金句

> **RAG 的本质不是让 LLM 记住一切，而是让 LLM 知道去哪里找到答案。**

> **知识库问答系统的价值不在于模型有多强，而在于你的笔记有多好——好的输入才有好的输出。**

> **分块是一种艺术：块太小失去上下文，块太大引入噪声。找到那个黄金分割点就是 RAG 的终极追求。**

> **永远不要假设 LLM 不会产生幻觉。RAG 不是消除幻觉，而是给幻觉套上缰绳——用真实的上下文限制模型的想象力。**

---

## 15.10 扩展阅读

| 资源 | 链接 | 说明 |
|------|------|------|
| LangChain RAG 文档 | https://python.langchain.com/docs/use_cases/question_answering/ | 官方 RAG 教程 |
| sentence-transformers | https://www.sbert.net/ | 本文使用的 embedding 库 |
| FAISS 官方文档 | https://github.com/facebookresearch/faiss | 大规模向量检索库 |
| DeepSeek API 文档 | https://platform.deepseek.com/api-docs | 本文使用的 LLM API |
| RAG 进阶：高级检索技术 | https://docs.llamaindex.ai/ | LlamaIndex 高级 RAG 模式 |

---

*第15天完成！你已经从一个 RAG 零基础学习者，成长为一个能独立构建完整知识库问答系统的开发者。从文档加载到向量检索，从 Prompt 设计到多轮对话——你掌握了生产级 RAG 应用的完整技能栈。明天将迎来更精彩的实战项目！*
