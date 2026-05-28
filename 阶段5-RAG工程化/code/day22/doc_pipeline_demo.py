"""
Day 22 — 文档加载与智能切分（Python 对照版）

演示三种切分策略，并与 Java 版对比。
"""

import json
import re

# ============================================================
# 测试文本（与 Java 版一致）
# ============================================================

SHORT_TEXT = "Spring AI 是一个AI框架。"

LONG_TEXT = """
Spring AI 是一个面向人工智能应用的 Spring 生态框架。

它的核心理念是将大语言模型的能力集成到 Java 应用中。

Spring AI 支持多种大语言模型提供商，包括 OpenAI、Ollama 等。

在提示词工程方面，Spring AI 提供了 PromptTemplate。

在 RAG 方面，Spring AI 提供了完整的数据处理管线。

从文档加载开始，支持 PDF、Word、HTML 和纯文本格式。

加载后的文档可以通过 TokenTextSplitter 进行切分。

切分后的文档块通过 Embedding API 向量化后存入向量数据库。

在查询时，框架自动从向量库中检索相关文档块作为上下文。

最终基于事实生成回答。
""".strip()

# 模拟的 Token 估算（中文 char ≈ 0.7 tokens）
def estimate_tokens(text):
    chinese = sum(1 for c in text if '\u4e00' <= c <= '\u9fff')
    other = sum(1 for c in text if not c.isspace() and (c < '\u4e00' or c > '\u9fff'))
    return int(chinese * 0.7 + other * 0.25)


# ============================================================
# 策略 1: Token 级切分（模拟 Spring AI TokenTextSplitter）
# ============================================================

def token_chunk(text, chunk_size=500, min_chunk=100, max_chunk=600, overlap=50):
    """模拟基于 Token 的切分"""
    tokens = list(text)  # 简化：每个字符算一个单位
    chunks = []

    i = 0
    while i < len(tokens):
        end = min(i + chunk_size, len(tokens))
        chunk_text = ''.join(tokens[i:end])
        if len(chunk_text) >= min_chunk or i + chunk_size >= len(tokens):
            chunks.append(chunk_text)
        i += chunk_size - overlap

    return chunks


# ============================================================
# 策略 2: 段落级切分（语义保持）
# ============================================================

def paragraph_chunk(text, max_chars=1500, min_chars=200):
    """按段落切分，保留语义完整"""
    paragraphs = re.split(r'\n\s*\n', text)
    chunks = []
    current = []

    for para in paragraphs:
        para = para.strip()
        if not para:
            continue

        if len(para) > max_chars:
            if current:
                chunks.append('\n\n'.join(current))
                current = []
            # 过长段落按句子拆分
            sentences = re.split(r'(?<=[。！？.!?])', para)
            for sent in sentences:
                sent = sent.strip()
                if not sent:
                    continue
                current_len = sum(len(p) for p in current) + 2 * len(current)
                if current and current_len + len(sent) > max_chars:
                    chunks.append('\n\n'.join(current))
                    current = []
                current.append(sent)
            continue

        current_len = sum(len(p) for p in current) + 2 * len(current)
        if current and current_len + len(para) > max_chars:
            chunks.append('\n\n'.join(current))
            current = []

        current.append(para)

        if current and sum(len(p) for p in current) >= min_chars:
            chunks.append('\n\n'.join(current))
            current = []

    if current:
        chunks.append('\n\n'.join(current))

    return chunks


# ============================================================
# 策略 3: 递归字符切分（LangChain 式）
# ============================================================

SEPARATORS = ['\n\n', '\n', '。', '.', '，', ',', ' ', '']

def recursive_chunk(text, chunk_size=1000, overlap=100):
    """递归字符切分 — 模拟 LangChain RecursiveCharacterTextSplitter"""

    def _split(text, separators, sep_idx):
        if not text:
            return []

        sep = separators[sep_idx]
        if not sep:
            # 字符级
            splits = list(text)
        else:
            parts = []
            start = 0
            while start < len(text):
                idx = text.find(sep, start)
                if idx < 0:
                    parts.append(text[start:])
                    break
                parts.append(text[start:idx + len(sep)])
                start = idx + len(sep)
            splits = parts

        # 合并到目标大小
        merged = []
        current = ''
        for split in splits:
            if len(current) + len(split) > chunk_size and current:
                merged.append(current.strip())
                current = current[-overlap:] if overlap > 0 else ''
            current += split

        if current:
            merged.append(current.strip())

        # 递归处理过大的块
        result = []
        for segment in merged:
            if len(segment) > chunk_size and sep_idx + 1 < len(separators):
                result.extend(_split(segment, separators, sep_idx + 1))
            else:
                result.append(segment)

        return result

    return _split(text, SEPARATORS, 0)


# ============================================================
# 统计辅助
# ============================================================

def stats(chunks, name, desc, original_text):
    if not chunks:
        return {'strategy': name, 'description': desc, 'chunkCount': 0}

    sizes = [len(c) for c in chunks]
    total_chars = sum(sizes)
    overlap_ratio = (total_chars / len(original_text) - 1) * 100 if original_text else 0

    return {
        'strategyName': name,
        'description': desc,
        'chunkCount': len(chunks),
        'minChunkSize': min(sizes),
        'maxChunkSize': max(sizes),
        'avgChunkSize': int(round(sum(sizes) / len(sizes))),
        'totalChunkChars': total_chars,
        'overlapRatio': round(overlap_ratio, 2),
        'samples': [
            {'index': i, 'length': len(c), 'preview': c[:120] + '...' if len(c) > 120 else c}
            for i, c in enumerate(chunks[:3])
        ]
    }


# ============================================================
# 主测试
# ============================================================

def main():
    text = LONG_TEXT

    print("=" * 60)
    print("📄 原始文档信息")
    print("=" * 60)
    print(f"  文本长度: {len(text)} 字符")
    print(f"  估算 Token: {estimate_tokens(text)}")
    para_count = len(re.split(r'\n\s*\n', text))
    print(f"  段落数: {para_count}")

    # Token 策略
    token_chunks = token_chunk(text, chunk_size=100, overlap=10)
    r1 = stats(token_chunks, "TokenSplitter (模拟)", "chunkSize=100t, overlap=10t", text)

    # 段落策略
    para_chunks = paragraph_chunk(text, max_chars=200, min_chars=30)
    r2 = stats(para_chunks, "ParagraphSplitter (语义)", "max=200c, min=30c", text)

    # 递归策略
    rec_chunks = recursive_chunk(text, chunk_size=150, overlap=15)
    r3 = stats(rec_chunks, "RecursiveSplitter (LangChain式)", "chunkSize=150c, overlap=15c", text)

    for r in [r1, r2, r3]:
        print()
        print("-" * 60)
        print(f"📊 {r['strategyName']}")
        print(f"  参数: {r['description']}")
        print(f"  块数: {r['chunkCount']}")
        print(f"  大小: min={r['minChunkSize']} / avg={r['avgChunkSize']} / max={r['maxChunkSize']} chars")
        print(f"  重叠率: {r['overlapRatio']}%")

        if r.get('samples'):
            for s in r['samples'][:2]:
                print(f"  块 #{s['index']}: ({s['length']} chars) {s['preview'][:60]}...")

    # 结果对比
    print()
    print("=" * 60)
    print("📊 策略对比总结")
    print("=" * 60)
    print(f"{'策略':<35} {'块数':>6} {'平均大小':>10}")
    print("-" * 55)
    for r in [r1, r2, r3]:
        print(f"{r['strategyName']:<35} {r['chunkCount']:>6} {r['avgChunkSize']:>10}c")


if __name__ == '__main__':
    main()
