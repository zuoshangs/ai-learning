"""
文档加载模块
============
负责扫描文档目录、读取 Markdown/文本文件，并进行文本分块处理。
"""

import os
import re
import logging
from typing import List, Dict, Optional

from . import config

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)-8s | %(message)s",
    datefmt="%H:%M:%S",
)
logger = logging.getLogger(__name__)


def load_documents(docs_dir: Optional[str] = None) -> List[Dict]:
    """
    扫描目录并加载所有支持的文档文件。

    支持的格式: .md, .txt, .markdown

    Args:
        docs_dir: 文档目录路径。默认为 config.DOCS_DIR。

    Returns:
        List[Dict]: 文档列表，每个元素包含:
            - "source": 文件路径
            - "filename": 文件名
            - "title": 文档标题（从文件名或首行提取）
            - "content": 完整文本内容

    示例:
        >>> docs = load_documents("./sample_docs")
        >>> print(f"加载了 {len(docs)} 个文档")
    """
    docs_dir = docs_dir or config.DOCS_DIR

    if not os.path.isdir(docs_dir):
        logger.warning(f"文档目录不存在: {docs_dir}")
        return []

    # 支持的文档扩展名
    supported_exts = {".md", ".markdown", ".txt"}

    documents = []

    # 递归遍历目录
    for root, dirs, files in os.walk(docs_dir):
        # 跳过隐藏目录
        dirs[:] = [d for d in dirs if not d.startswith(".")]

        for file in sorted(files):
            ext = os.path.splitext(file)[1].lower()

            if ext not in supported_exts:
                continue

            filepath = os.path.join(root, file)

            try:
                content = _read_file(filepath)
                title = _extract_title(file, content)

                documents.append({
                    "source": filepath,
                    "filename": file,
                    "title": title,
                    "content": content,
                })

                logger.info(f"  ✓ 加载文档: {os.path.relpath(filepath, docs_dir)}")
            except Exception as e:
                logger.error(f"  ✗ 读取失败: {filepath} — {e}")

    logger.info(f"共加载 {len(documents)} 个文档")
    return documents


def _read_file(filepath: str) -> str:
    """
    读取文件内容，自动检测编码。

    Args:
        filepath: 文件绝对路径。

    Returns:
        str: 文件文本内容。

    Raises:
        UnicodeDecodeError: 如果所有编码尝试都失败。
    """
    # 按优先级尝试不同的编码
    encodings = ["utf-8", "utf-8-sig", "gbk", "gb2312", "latin-1"]

    for enc in encodings:
        try:
            with open(filepath, "r", encoding=enc) as f:
                return f.read()
        except UnicodeDecodeError:
            continue
        except Exception:
            raise

    # 最后的尝试：latin-1 永远不会报错
    with open(filepath, "r", encoding="latin-1") as f:
        return f.read()


def _extract_title(filename: str, content: str) -> str:
    """
    从文件内容中提取标题。

    优先级:
        1. Markdown 一级标题 (# 标题)
        2. Markdown 二级标题 (## 标题)
        3. 文件名（去掉扩展名）

    Args:
        filename: 文件名。
        content: 文件内容。

    Returns:
        str: 提取的标题。
    """
    # 尝试匹配 Markdown 一级标题
    h1_match = re.search(r"^#\s+(.+)$", content, re.MULTILINE)
    if h1_match:
        return h1_match.group(1).strip()

    # 尝试匹配 Markdown 二级标题
    h2_match = re.search(r"^##\s+(.+)$", content, re.MULTILINE)
    if h2_match:
        return h2_match.group(1).strip()

    # 回退到文件名
    return os.path.splitext(filename)[0].replace("_", " ").replace("-", " ").title()


def split_text(
    text: str,
    chunk_size: Optional[int] = None,
    chunk_overlap: Optional[int] = None,
) -> List[str]:
    """
    将长文本按段落和句子边界智能分块。

    分块策略:
        1. 优先按 Markdown 章节标题（## 或 ###）分割
        2. 其次按空行分隔的段落分割
        3. 最后按句子边界（。！？）分割
        4. 确保每个 chunk 不超过 chunk_size

    Args:
        text: 要分割的文本。
        chunk_size: 每块最大字符数（默认: config.CHUNK_SIZE）。
        chunk_overlap: 块间重叠字符数（默认: config.CHUNK_OVERLAP）。

    Returns:
        List[str]: 文本块列表。
    """
    chunk_size = chunk_size or config.CHUNK_SIZE
    chunk_overlap = chunk_overlap or config.CHUNK_OVERLAP

    # 如果文本很短，直接返回
    if not text or len(text.strip()) <= chunk_size:
        text = text.strip()
        return [text] if text else []

    # 步骤 1: 按 Markdown 章节标题分割
    sections = _split_by_headings(text)

    # 步骤 2: 对每个大章节，进一步按段落分割
    chunks = []
    for section in sections:
        if len(section) <= chunk_size:
            chunks.append(section)
        else:
            chunks.extend(_split_by_paragraphs(section, chunk_size, chunk_overlap))

    # 步骤 3: 合并过小的相邻块（可选）
    chunks = _merge_small_chunks(chunks, chunk_size)

    logger.debug(f"文本分块: {len(chunks)} 块 (chunk_size={chunk_size})")
    return chunks


def _split_by_headings(text: str) -> List[str]:
    """
    按 Markdown 二级/三级标题分割文本。

    Args:
        text: 输入文本。

    Returns:
        List[str]: 按标题分割后的文本段。
    """
    # 匹配 ## 或 ### 标题，并保留标题行
    pattern = r"^(#{2,3})\s+.*$"

    lines = text.split("\n")
    sections = []
    current_section = []

    for line in lines:
        if re.match(pattern, line, re.MULTILINE) and current_section:
            # 新标题开始，保存当前段
            sections.append("\n".join(current_section).strip())
            current_section = [line]
        else:
            current_section.append(line)

    # 最后一段
    if current_section:
        sections.append("\n".join(current_section).strip())

    return [s for s in sections if s]


def _split_by_paragraphs(text: str, chunk_size: int, chunk_overlap: int) -> List[str]:
    """
    按空行（段落）分割文本，确保每块不超过 chunk_size。

    如果单个段落超过 chunk_size，则按句子边界切割。

    Args:
        text: 输入文本。
        chunk_size: 最大块大小。
        chunk_overlap: 块间重叠。

    Returns:
        List[str]: 分割后的文本块列表。
    """
    # 按空行分割段落（支持多个空行）
    paragraphs = re.split(r"\n\s*\n", text)
    paragraphs = [p.strip() for p in paragraphs if p.strip()]

    chunks = []
    current_chunk = ""
    overlap_text = ""

    for para in paragraphs:
        # 单段落超长，按句子切割
        if len(para) > chunk_size:
            # 先把当前累积的 chunk 保存
            if current_chunk:
                chunks.append(current_chunk.strip())

            # 按中文/英文句子边界切割
            sentences = _split_by_sentences(para)
            temp_chunk = ""

            for sent in sentences:
                if len(temp_chunk) + len(sent) + 1 > chunk_size:
                    if temp_chunk:
                        chunks.append(temp_chunk.strip())
                    temp_chunk = sent
                else:
                    temp_chunk = temp_chunk + sent if temp_chunk else sent

            if temp_chunk:
                chunks.append(temp_chunk.strip())

            current_chunk = ""
            continue

        # 正常段落：尝试合并到当前块
        if not current_chunk:
            current_chunk = para
        elif len(current_chunk) + len(para) + 1 <= chunk_size:
            current_chunk += "\n\n" + para
        else:
            chunks.append(current_chunk.strip())
            # 重叠：取当前块末尾若干字符
            if chunk_overlap > 0 and len(current_chunk) > chunk_overlap:
                overlap_text = _get_tail(current_chunk, chunk_overlap)
                current_chunk = overlap_text + "\n\n" + para
            else:
                current_chunk = para

    if current_chunk:
        chunks.append(current_chunk.strip())

    return chunks


def _split_by_sentences(text: str) -> List[str]:
    """
    按句子边界分割文本，支持中英文混合。

    Args:
        text: 输入文本。

    Returns:
        List[str]: 句子列表。
    """
    # 匹配中文句号、问号、叹号、英文句点、问号、叹号
    # 保留分隔符在句子末尾
    sentences = re.split(r"(?<=[。！？.!?])\s*", text)
    return [s.strip() for s in sentences if s.strip()]


def _get_tail(text: str, length: int) -> str:
    """
    获取文本末尾指定长度的内容，尽量在单词或句子边界截断。

    Args:
        text: 输入文本。
        length: 需要的字符数。

    Returns:
        str: 文本末尾部分。
    """
    if len(text) <= length:
        return text

    tail = text[-length:]
    # 尝试在句子边界开始
    idx = tail.find("。")
    if idx > 0:
        return tail[idx + 1:]

    idx = tail.find("\n")
    if idx > 0:
        return tail[idx + 1:]

    return tail


def _merge_small_chunks(chunks: List[str], chunk_size: int) -> List[str]:
    """
    合并过小的相邻文本块（少于 chunk_size 的 30%）。

    避免产生大量只有一两句话的碎片块。

    Args:
        chunks: 文本块列表。
        chunk_size: 目标块大小。

    Returns:
        List[str]: 合并后的文本块列表。
    """
    if len(chunks) <= 1:
        return chunks

    min_size = int(chunk_size * 0.3)
    merged = []

    i = 0
    while i < len(chunks):
        current = chunks[i]

        # 如果当前块太小且后面还有块，尝试合并
        while (len(current) < min_size
               and i + 1 < len(chunks)
               and len(current) + len(chunks[i + 1]) + 1 <= chunk_size):
            i += 1
            current += "\n\n" + chunks[i]

        merged.append(current)
        i += 1

    return merged


def process_documents(
    docs_dir: Optional[str] = None,
    chunk_size: Optional[int] = None,
    chunk_overlap: Optional[int] = None,
) -> List[Dict]:
    """
    一站式流程：加载文档 → 分块 → 返回结构化数据。

    每个输出元素包含文档来源信息和文本块内容。

    Args:
        docs_dir: 文档目录路径。
        chunk_size: 每块最大字符数。
        chunk_overlap: 块间重叠字符数。

    Returns:
        List[Dict]: 文本块列表，每个包含:
            - "text": 块文本内容
            - "source": 来源文件路径
            - "title": 文档标题
            - "chunk_index": 块序号
    """
    documents = load_documents(docs_dir)
    all_chunks = []

    for doc in documents:
        chunks = split_text(doc["content"], chunk_size, chunk_overlap)

        for idx, chunk_text in enumerate(chunks):
            all_chunks.append({
                "text": chunk_text,
                "source": doc["source"],
                "title": doc["title"],
                "chunk_index": idx,
                "filename": doc["filename"],
            })

    logger.info(f"文档处理完成: {len(all_chunks)} 个文本块")
    return all_chunks
