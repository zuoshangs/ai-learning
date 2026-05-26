#!/usr/bin/env python3
"""
个人知识库问答系统 — CLI 入口
==============================
支持构建知识库索引和交互式问答两种模式。

用法:
    # 构建知识库索引
    python main.py build

    # 交互式问答
    python main.py qa

    # 搜索测试
    python main.py search "你的问题"

    # 指定文档目录
    python main.py build --docs ./my_docs

    # 查看系统信息
    python main.py info
"""

import os
import sys
import argparse
import logging
from typing import List, Optional

# 确保能导入 knowledge_base 包（项目根目录在父目录）
_project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if _project_root not in sys.path:
    sys.path.insert(0, _project_root)

from knowledge_base import config
from knowledge_base.document_loader import process_documents, load_documents
from knowledge_base.embeddings import EmbeddingGenerator
from knowledge_base.vector_store import VectorStore
from knowledge_base.qa_engine import QaEngine

# 日志配置
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)-8s | %(message)s",
    datefmt="%H:%M:%S",
)
logger = logging.getLogger(__name__)

# ============================================================
# Banner
# ============================================================

BANNER = r"""
╔══════════════════════════════════════════════╗
║     📚  Personal Knowledge Base Q&A          ║
║     个人知识库问答系统 v1.0                  ║
╚══════════════════════════════════════════════╝
"""


def print_banner():
    """打印启动横幅。"""
    print(BANNER)

    # 打印配置状态
    warnings = config._config_warnings
    if warnings:
        for w in warnings:
            print(f"  {w}")
    else:
        print("  ✅ 配置检查通过\n")


def print_section(title: str):
    """打印带格式的章节标题。"""
    print(f"\n{'─' * 50}")
    print(f"  {title}")
    print(f"{'─' * 50}\n")


# ============================================================
# 构建知识库
# ============================================================

def build_knowledge_base(
    docs_dir: Optional[str] = None,
    index_path: Optional[str] = None,
    force_rebuild: bool = False,
) -> VectorStore:
    """
    构建知识库索引。

    流程:
        1. 扫描文档目录
        2. 加载并分块文档
        3. 生成嵌入向量
        4. 存储到向量索引

    Args:
        docs_dir: 文档目录路径。
        index_path: 索引保存路径。
        force_rebuild: 是否强制重建（忽略已有索引）。

    Returns:
        VectorStore: 构建好的向量存储实例。
    """
    docs_dir = docs_dir or config.DOCS_DIR
    print_section("📖 加载文档")

    # 检查是否有已构建的索引
    if not force_rebuild and index_path and os.path.exists(index_path):
        logger.info(f"发现已有索引文件: {index_path}")
        logger.info("如需重建请使用 --force 参数")
        store = VectorStore()
        store.load(index_path)
        return store

    # 如果索引文件不存在但使用默认路径，检查默认位置
    default_index = os.path.join(config.INDEX_SAVE_DIR, "vector_store.pkl")
    if not force_rebuild and os.path.exists(default_index):
        logger.info(f"发现默认索引: {default_index}")
        logger.info("如需重建请使用 --force 参数")
        store = VectorStore()
        store.load(default_index)
        return store

    # 步骤 1: 加载文档
    logger.info(f"文档目录: {docs_dir}")
    chunks = process_documents(docs_dir)

    if not chunks:
        logger.error("未加载到任何文档，请检查文档目录。")
        sys.exit(1)

    texts = [c["text"] for c in chunks]
    metadata = [
        {
            "source": c["source"],
            "title": c["title"],
            "filename": c["filename"],
            "chunk_index": c["chunk_index"],
        }
        for c in chunks
    ]

    print_section("🧠 生成嵌入向量")

    # 步骤 2: 初始化嵌入生成器
    generator = EmbeddingGenerator()
    if generator.is_using_fallback():
        logger.warning("使用备用嵌入方案，检索精度可能受限。")

    # 步骤 3: 构建向量存储
    store = VectorStore(embedding_generator=generator)
    store.add_texts(texts, metadata)

    # 步骤 4: 保存索引
    if index_path:
        store.save(index_path)
    else:
        store.save()

    # 打印统计信息
    info = store.get_info()
    print_section("📊 知识库统计")
    print(f"  总文本块数: {info['total_vectors']}")
    print(f"  向量维度:   {info['dimension']}")
    print(f"  嵌入模式:   {'备用' if info.get('using_fallback') else '标准'}")
    if info["source_distribution"]:
        print(f"  文档来源:")
        for src, count in info["source_distribution"].items():
            filename = os.path.basename(src) if src != "unknown" else src
            print(f"    • {filename}: {count} 块")

    return store


# ============================================================
# 交互式问答
# ============================================================

def interactive_qa(store: VectorStore, engine: QaEngine):
    """
    交互式问答主循环。

    支持:
        - 多轮对话
        - 来源引用显示
        - 查看对话历史
        - 清空历史

    Args:
        store: 已构建的向量存储。
        engine: 问答引擎。
    """
    print_section("💬 交互式问答")
    print("  输入问题开始问答。支持以下命令:")
    print("    /help    — 显示帮助")
    print("    /history — 查看对话历史")
    print("    /clear   — 清空对话历史")
    print("    /info    — 查看知识库状态")
    print("    /quit    — 退出")
    print()

    while True:
        try:
            # 获取用户输入
            question = input("🙋 你: ").strip()

        except (EOFError, KeyboardInterrupt):
            print("\n\n👋 再见！")
            break

        # 处理特殊命令
        if not question:
            continue

        if question.startswith("/"):
            command = question[1:].lower()
            if _handle_command(command, store, engine):
                continue
            else:
                break

        # 步骤 1: 检索相关上下文
        logger.info("🔍 正在检索相关知识...")
        context_chunks = store.search_with_text(question)

        if not context_chunks:
            print("\n📭 未检索到相关信息。请尝试换个问法或添加更多文档。\n")
            continue

        # 显示检索结果摘要
        print(f"\n📎 找到 {len(context_chunks)} 条相关段落:")
        for c in context_chunks:
            score = c["score"]
            bar = "█" * int(score * 20)
            source = c["metadata"].get("filename", "未知")
            print(f"   [{c['rank']}] {bar} {score:.2f} — {source}")
        print()

        # 步骤 2: 生成回答
        logger.info("🤖 正在生成回答...")
        result = engine.answer_with_fallback(question, context_chunks)

        # 步骤 3: 显示回答
        if result["success"]:
            print(f"\n🤖 助手: {result['answer']}\n")

            # 显示引用来源
            if result["citations"]:
                print(f"  📚 引用来源 ({len(result['citations'])} 篇):")
                for cit in result["citations"]:
                    print(f"     [{cit['index']}] {cit['title'].strip()}")
                print()

            if not result.get("error"):
                # 显示消耗信息
                pass
        else:
            print(f"\n❌ 生成失败: {result['error']}\n")


def _handle_command(command: str, store: VectorStore, engine: QaEngine) -> bool:
    """
    处理交互式命令。

    Args:
        command: 命令字符串。
        store: 向量存储。
        engine: 问答引擎。

    Returns:
        bool: True 表示继续对话，False 表示退出。
    """
    if command in ("quit", "exit", "q"):
        print("👋 再见！")
        return False

    elif command in ("help", "h"):
        print("""
  📋 可用命令:
    /help     — 显示本帮助
    /history  — 查看对话历史
    /clear    — 清空对话历史
    /info     — 查看知识库状态
    /quit     — 退出系统

  💡 提示:
    - 输入任何问题开始问答
    - 回答会自动标注来源
    - 支持追问（多轮对话）
        """)

    elif command == "history":
        summary = engine.get_history_summary()
        if summary:
            print(f"\n📜 对话历史 ({len(summary)} 轮):")
            for turn in summary:
                print(f"   [{turn['turn']}] Q: {turn['question']}")
                print(f"       回答长度: {turn['answer_length']} 字符")
            print()
        else:
            print("\n📭 暂无对话历史\n")

    elif command == "clear":
        engine.clear_history()
        print("\n✅ 对话历史已清空\n")

    elif command == "info":
        info = store.get_info()
        print(f"""
  📊 知识库信息:
     向量数量: {info['total_vectors']}
     向量维度: {info['dimension']}
     嵌入模式: {'备用' if info.get('using_fallback') else '标准'}
     文档数量: {len(info['source_distribution'])}
        """)

    else:
        print(f"\n⚠️  未知命令: /{command}。输入 /help 查看帮助。\n")

    return True


# ============================================================
# 单次搜索
# ============================================================

def search_knowledge_base(
    query: str,
    store: VectorStore,
    top_k: int = 5,
) -> List[dict]:
    """
    执行一次知识库搜索并打印结果。

    Args:
        query: 搜索查询。
        store: 向量存储。
        top_k: 返回结果数。

    Returns:
        List[dict]: 搜索结果列表。
    """
    print(f"\n🔍 搜索: \"{query}\"\n")
    results = store.search_with_text(query, top_k=top_k)

    if not results:
        print("  未找到匹配结果。\n")
        return results

    print(f"  找到 {len(results)} 条结果:\n")
    for r in results:
        print(f"  ┌─ [{r['rank']}] 相关度: {r['score']:.4f}")
        src = r["metadata"].get("filename", "未知")
        print(f"  ├─ 来源: {src}")
        print(f"  └─ 内容: {r['text'][:200]}...")
        print()

    return results


# ============================================================
# 系统信息
# ============================================================

def show_info():
    """显示系统信息和配置状态。"""
    print_section("ℹ️  系统信息")

    print(f"  Python 版本:     {sys.version.split()[0]}")
    print(f"  文档目录:        {config.DOCS_DIR}")
    print(f"  嵌入模型:        {config.EMBEDDING_MODEL}")
    print(f"  LLM 模型:        {config.LLM_MODEL}")
    print(f"  LLM 地址:        {config.LLM_BASE_URL}")
    print(f"  API Key 已配置:  {'✅' if config.LLM_API_KEY else '❌'}")
    print(f"  分块大小:        {config.CHUNK_SIZE}")
    print(f"  分块重叠:        {config.CHUNK_OVERLAP}")
    print(f"  检索 Top-K:      {config.TOP_K}")
    print(f"  相似度阈值:      {config.SIMILARITY_THRESHOLD}")
    print()

    # 检查依赖
    print_section("📦 依赖检查")
    for mod_name, import_name in [
        ("openai", "openai"),
        ("numpy", "numpy"),
        ("sentence-transformers", "sentence_transformers"),
        ("torch", "torch"),
    ]:
        try:
            __import__(import_name)
            print(f"  ✅ {mod_name} 已安装")
        except ImportError:
            print(f"  ⚠️  {mod_name} 未安装")

    # 检查索引文件
    index_path = os.path.join(config.INDEX_SAVE_DIR, "vector_store.pkl")
    if os.path.exists(index_path):
        size = os.path.getsize(index_path)
        size_str = f"{size / 1024:.1f} KB" if size < 1024 * 1024 else f"{size / 1024 / 1024:.1f} MB"
        print(f"\n  📂 索引文件: {index_path} ({size_str})")
    else:
        print(f"\n  📂 索引文件: 未构建（运行 python main.py build）")

    # 检查文档目录
    if os.path.exists(config.DOCS_DIR):
        files = [f for f in os.listdir(config.DOCS_DIR)
                 if f.endswith((".md", ".txt", ".markdown"))]
        print(f"  📄 文档数量: {len(files)} 个")
        if files:
            for f in files[:5]:
                print(f"      • {f}")
            if len(files) > 5:
                print(f"      ... 及其他 {len(files) - 5} 个文件")
    else:
        print(f"  📄 文档目录不存在")

    print()


# ============================================================
# 创建示例文档
# ============================================================

def create_sample_docs():
    """创建示例文档供测试使用。"""
    docs_dir = config.DOCS_DIR
    os.makedirs(docs_dir, exist_ok=True)

    samples = [
        {
            "filename": "python_basics.md",
            "content": """# Python 基础入门

## 什么是 Python？

Python 是一种高级、解释型、面向对象的编程语言，由 Guido van Rossum 于 1991 年创建。
Python 的设计哲学强调代码的可读性和简洁性，使用缩进来定义代码块。

## Python 的特点

1. **易学易用**：Python 语法简洁清晰，适合初学者入门。
2. **丰富的标准库**：Python 内置了大量实用模块，覆盖文件操作、网络通信、数据处理等。
3. **跨平台**：Python 可以在 Windows、macOS、Linux 等主流操作系统上运行。
4. **强大的社区生态**：PyPI 上有超过 40 万个第三方包。

## 基本数据类型

- `int`：整数，如 `42`
- `float`：浮点数，如 `3.14`
- `str`：字符串，如 `"Hello"`
- `bool`：布尔值，`True` 或 `False`
- `list`：列表，如 `[1, 2, 3]`
- `dict`：字典，如 `{"key": "value"}`

## 控制流

### 条件判断
```python
if x > 0:
    print("正数")
elif x == 0:
    print("零")
else:
    print("负数")
```

### 循环
```python
# for 循环
for i in range(5):
    print(i)

# while 循环
while count < 10:
    print(count)
    count += 1
```
""",
        },
        {
            "filename": "rag_intro.md",
            "content": """# RAG（检索增强生成）技术介绍

## 什么是 RAG？

RAG（Retrieval-Augmented Generation，检索增强生成）是一种结合信息检索和文本生成的技术框架。
它在 LLM 生成回答之前，先从知识库中检索相关文档，然后将检索结果作为上下文提供给模型。

## RAG 的核心流程

### 1. 文档加载与分块
将知识库文档加载到系统中，并按一定策略（段落、章节、固定长度）切分为文本块。

### 2. 向量化与索引
使用嵌入模型（如 Sentence-Transformer）将文本块转换为向量，并建立向量索引。

### 3. 检索
当用户提问时，将问题同样转换为向量，在向量库中查找最相似的文本块。

### 4. 生成
将检索到的文本块作为上下文，连同用户问题一起发送给 LLM，生成带有引用的回答。

## RAG 的优势

- **知识实时性**：无需重新训练模型，更新知识库即可
- **可解释性**：回答有明确的来源引用，便于验证
- **低成本**：相比微调，RAG 的实施成本更低
- **领域适应性**：可以快速适配任何垂直领域

## 常见应用场景

1. **企业知识库问答**：员工可以自然语言查询内部文档
2. **智能客服**：基于产品文档回答用户问题
3. **学术研究助手**：基于论文库辅助文献调研
4. **个人笔记助手**：对自己的笔记和文章进行问答
""",
        },
        {
            "filename": "deepseek_api.md",
            "content": """# DeepSeek API 使用指南

## 概述

DeepSeek 提供 OpenAI 兼容的 API 接口，支持文本生成、对话等功能。
其核心模型 DeepSeek-V2 和 DeepSeek-Chat 在多项基准测试中表现出色。

## API 配置

### 获取 API Key
1. 访问 DeepSeek 官网注册账号
2. 在控制台中创建 API Key
3. 保存 API Key 到安全位置

### 基础配置

```python
from openai import OpenAI

client = OpenAI(
    api_key="your-deepseek-api-key",
    base_url="https://api.deepseek.com/v1"
)
```

### 对话补全

```python
response = client.chat.completions.create(
    model="deepseek-chat",
    messages=[
        {"role": "system", "content": "你是一个助手"},
        {"role": "user", "content": "你好"}
    ],
    temperature=0.7,
    max_tokens=2048,
)
```

### 流式输出

```python
stream = client.chat.completions.create(
    model="deepseek-chat",
    messages=[...],
    stream=True,
)
for chunk in stream:
    print(chunk.choices[0].delta.content, end="")
```

## 注意事项

1. API Key 请妥善保管，不要提交到版本控制
2. 使用环境变量存储敏感信息：`export DEEPSEEK_API_KEY='your-key'`
3. 注意 API 调用配额和费用
4. 国内用户可以直接访问，无需代理
""",
        },
        {
            "filename": "sentence_transformers.txt",
            "content": """Sentence-Transformers 使用说明

Sentence-Transformers 是一个用于生成句子、文本和图像嵌入的 Python 框架。
它基于预训练的 Transformer 模型，可以生成高质量的语义向量表示。

常用模型：
1. all-MiniLM-L6-v2 - 轻量级模型，384维，速度快，适合大多数场景
2. all-mpnet-base-v2 - 更高质量的模型，768维，速度较慢但精度更高
3. multi-qa-MiniLM-L6-cos-v1 - 针对问答场景优化的模型

基本用法：

from sentence_transformers import SentenceTransformer

model = SentenceTransformer('all-MiniLM-L6-v2')
embeddings = model.encode([
    "这是第一个句子",
    "这是第二个句子，语义相似"
])

相似度计算使用余弦相似度：
from sklearn.metrics.pairwise import cosine_similarity
similarity = cosine_similarity([embeddings[0]], [embeddings[1]])

安装方法：
pip install sentence-transformers

如果需要使用国内镜像加速模型下载：
export HF_ENDPOINT=https://hf-mirror.com

第一次运行时模型会自动下载到本地缓存目录。
""",
        },
    ]

    for sample in samples:
        filepath = os.path.join(docs_dir, sample["filename"])
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(sample["content"])
        print(f"  ✓ 已创建: {sample['filename']}")

    print(f"\n📄 共创建 {len(samples)} 个示例文档到: {docs_dir}")


# ============================================================
# CLI 入口
# ============================================================

def main():
    """主入口函数。"""
    parser = argparse.ArgumentParser(
        description="个人知识库问答系统 (Personal Knowledge Base Q&A)",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
使用示例:
  %(prog)s build              # 构建知识库索引
  %(prog)s build --force      # 强制重建索引
  %(prog)s qa                 # 启动交互式问答
  %(prog)s search "RAG是什么" # 搜索知识库
  %(prog)s info               # 查看系统信息
  %(prog)s sample             # 创建示例文档
        """,
    )

    parser.add_argument(
        "command",
        nargs="?",
        default="qa",
        choices=["build", "qa", "search", "info", "sample"],
        help="操作命令 (默认: qa)",
    )

    # 构建参数
    parser.add_argument(
        "--docs", "-d",
        default=None,
        help="文档目录路径",
    )
    parser.add_argument(
        "--index", "-i",
        default=None,
        help="索引保存路径",
    )
    parser.add_argument(
        "--force", "-f",
        action="store_true",
        help="强制重建索引",
    )
    parser.add_argument(
        "--top-k", "-k",
        type=int,
        default=5,
        help="搜索结果数量 (默认: 5)",
    )

    # 搜索参数
    parser.add_argument(
        "query",
        nargs="?",
        default=None,
        help="搜索查询 (用于 search 命令)",
    )

    args = parser.parse_args()

    # 处理命令
    if args.command == "info":
        print_banner()
        show_info()

    elif args.command == "sample":
        print_banner()
        print_section("📄 创建示例文档")
        create_sample_docs()

    elif args.command == "build":
        print_banner()
        build_knowledge_base(
            docs_dir=args.docs,
            index_path=args.index,
            force_rebuild=args.force,
        )
        print("\n✅ 知识库构建完成！运行 'python main.py qa' 开始问答。\n")

    elif args.command == "search":
        print_banner()
        query = args.query
        if not query:
            query = input("🔍 请输入搜索内容: ").strip()
            if not query:
                print("请输入搜索内容。")
                return

        store = build_knowledge_base(
            docs_dir=args.docs,
            index_path=args.index,
            force_rebuild=args.force,
        )
        search_knowledge_base(query, store, top_k=args.top_k)

    elif args.command == "qa":
        print_banner()

        # 构建或加载知识库
        store = build_knowledge_base(
            docs_dir=args.docs,
            index_path=args.index,
            force_rebuild=args.force,
        )

        if store.count() == 0:
            logger.error("知识库为空，请先添加文档并构建索引。")
            logger.info("使用示例: python main.py sample && python main.py build")
            sys.exit(1)

        # 初始化问答引擎
        engine = QaEngine()

        # 启动交互式问答
        interactive_qa(store, engine)

    else:
        parser.print_help()


if __name__ == "__main__":
    main()
