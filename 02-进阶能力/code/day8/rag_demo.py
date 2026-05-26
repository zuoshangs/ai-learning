"""
rag_demo.py — 完整 RAG 问答系统

三段论：检索 → 增强 → 生成
- 使用 sentence-transformers 进行本地 Embedding
- 使用 SimpleVectorStore 做向量检索
- 使用 DeepSeek API 做生成

用法：
  1. 把 .txt 文档放入 data/ 目录
  2. python3 rag_demo.py
  3. 输入问题
"""

import os
import sys
import requests

# 确保能找到 simple_vector_store
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from simple_vector_store import SimpleVectorStore


# ─── 配置 ─────────────────────────────────────
DEEPSEEK_API_KEY = os.environ.get("DEEPSEEK_API_KEY", "")
DEEPSEEK_URL = "https://api.deepseek.com/v1/chat/completions"
DATA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data")
CHUNK_SIZE = 500
CHUNK_OVERLAP = 50

# 尝试加载 Embedding 模型
EMBEDDING_MODEL_NAME = "all-MiniLM-L6-v2"
try:
    from sentence_transformers import SentenceTransformer
    embed_model = SentenceTransformer(EMBEDDING_MODEL_NAME)
    print(f"  ✅ 加载 Embedding 模型: {EMBEDDING_MODEL_NAME}")
except ImportError:
    print("  ⚠️  未安装 sentence-transformers，使用随机向量演示模式")
    print("  安装: pip install sentence-transformers")
    embed_model = None


# ─── 文本分割 ─────────────────────────────────
def chunk_text(text: str, chunk_size=500, overlap=50) -> list:
    """递归字符分割——先按段落，再按句子"""
    chunks = []

    # 先按段落分割
    paragraphs = text.split("\n\n")
    current = ""

    for para in paragraphs:
        para = para.strip()
        if not para:
            continue
        if len(current) + len(para) < chunk_size:
            current += para + "\n\n"
        else:
            if current.strip():
                chunks.append(current.strip())
            current = para + "\n\n"

    if current.strip():
        chunks.append(current.strip())

    # 如果单个 chunk 仍然太长，按句子再次分割
    final_chunks = []
    for chunk in chunks:
        if len(chunk) <= chunk_size:
            final_chunks.append(chunk)
        else:
            sentences = chunk.replace("。", "。\n").replace("？", "？\n").replace("！", "！\n").split("\n")
            current = ""
            for sent in sentences:
                sent = sent.strip()
                if not sent:
                    continue
                if len(current) + len(sent) < chunk_size:
                    current += sent
                else:
                    if current.strip():
                        final_chunks.append(current.strip())
                    current = sent

            if current.strip():
                final_chunks.append(current.strip())

    return final_chunks


# ─── Embedding ────────────────────────────────
def get_embedding(text: str) -> list:
    """生成文本的向量"""
    if embed_model is not None:
        return embed_model.encode(text).tolist()
    else:
        # 演示模式：返回一个固定维度的随机向量
        import random
        random.seed(hash(text) % (2**31))
        return [random.random() for _ in range(384)]


# ─── 构建知识库 ──────────────────────────────
def build_knowledge_base(file_path: str, store: SimpleVectorStore):
    """读取文件 → 分割 → Embedding → 存入向量库"""
    with open(file_path, "r", encoding="utf-8") as f:
        text = f.read()

    chunks = chunk_text(text, CHUNK_SIZE, CHUNK_OVERLAP)
    print(f"  📄 已分割为 {len(chunks)} 个片段")

    for i, chunk in enumerate(chunks):
        vector = get_embedding(chunk)
        store.add(
            text=chunk,
            vector=vector,
            metadata={"source": os.path.basename(file_path), "chunk": i}
        )

    print(f"  ✅ 已存储 {len(chunks)} 个向量")


# ─── RAG 查询 ─────────────────────────────────
def rag_query(question: str, store: SimpleVectorStore, top_k: int = 3) -> tuple:
    """RAG 三段论：检索 → 增强 → 生成"""

    # ① 检索：问题向量化 + 相似度搜索
    print("   🔍 检索中...")
    query_vector = get_embedding(question)
    results = store.search(query_vector, top_k=top_k)

    # ② 增强：把检索结果拼入 prompt
    context = "\n\n".join([r["text"] for r in results])

    prompt = f"""你是一个专业的文档问答助手。请根据以下资料回答用户的问题。

资料：
{context}

问题：{question}

要求：
- 如果资料中有相关信息，请基于资料回答
- 如果资料中没有相关信息，请明确说"资料中没有提到"
- 不要编造信息
- 用中文回答"""

    # ③ 生成：调用 DeepSeek API
    print("   🤖 生成回答中...")
    resp = requests.post(
        DEEPSEEK_URL,
        headers={
            "Authorization": f"Bearer {DEEPSEEK_API_KEY}",
            "Content-Type": "application/json"
        },
        json={
            "model": "deepseek-chat",
            "messages": [
                {"role": "system", "content": "你是一个文档问答助手，基于用户提供的资料回答问题。"},
                {"role": "user", "content": prompt}
            ],
            "temperature": 0.3,
            "max_tokens": 1024
        },
        timeout=30
    )

    data = resp.json()
    if "choices" not in data:
        return f"⚠️ API 错误: {data}", results

    answer = data["choices"][0]["message"]["content"]
    return answer, results


# ─── 无 RAG 对比版 ────────────────────────────
def no_rag_query(question: str) -> str:
    """直接问大模型，不检索——对比用"""
    resp = requests.post(
        DEEPSEEK_URL,
        headers={
            "Authorization": f"Bearer {DEEPSEEK_API_KEY}",
            "Content-Type": "application/json"
        },
        json={
            "model": "deepseek-chat",
            "messages": [{"role": "user", "content": question}],
            "temperature": 0.3,
            "max_tokens": 1024
        },
        timeout=30
    )
    data = resp.json()
    return data["choices"][0]["message"]["content"]


# ─── 主函数 ────────────────────────────────────
def main():
    print("=" * 55)
    print("  🔍 RAG 文档问答系统")
    print("  三段论：检索 → 增强 → 生成")
    print("=" * 55)

    # 初始化向量库
    store = SimpleVectorStore()

    # 检查 data 目录
    if not os.path.exists(DATA_DIR):
        os.makedirs(DATA_DIR)
        print(f"\n📁 已创建 {DATA_DIR}/")
        print("   请放入一个 .txt 文档后重新运行")
        print(f"   或用 cp 命令复制: cp your_doc.txt {DATA_DIR}/")
        return

    files = [f for f in os.listdir(DATA_DIR) if f.endswith(".txt")]
    if not files:
        print(f"\n📁 {DATA_DIR}/ 目录为空")
        print("   请放入一个 .txt 文档后重新运行")
        return

    # 构建知识库
    print(f"\n📚 构建知识库...")
    for file_name in files:
        file_path = os.path.join(DATA_DIR, file_name)
        print(f"\n  📖 处理: {file_name}")
        build_knowledge_base(file_path, store)

    print(f"\n✅ 知识库构建完成（共 {len(store)} 个文档片段）")

    # 示例文档提示
    print(f"\n💡 你可以问关于 {', '.join(f[:-4] for f in files)} 的问题")

    # 问答循环
    print("\n" + "=" * 55)
    print("💬 输入问题（输入 quit 退出, no-rag:问题 可对比无RAG效果）")
    print("=" * 55)

    while True:
        try:
            question = input("\n❓ 你: ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\n👋 再见！")
            break

        if not question:
            continue
        if question.lower() in ("quit", "exit", "q"):
            print("👋 再见！")
            break

        # 无 RAG 对比模式
        if question.startswith("no-rag:"):
            q = question[7:].strip()
            print("\n   📡 直接问模型（无 RAG）：")
            answer = no_rag_query(q)
            print(f"\n📝 回答:\n{answer}\n")
            continue

        # RAG 模式
        answer, sources = rag_query(question, store)

        print(f"\n📝 回答:\n{answer}")

        # 显示参考来源
        print(f"\n📎 参考来源（Top {len(sources)}）:")
        for i, s in enumerate(sources, 1):
            preview = s["text"][:100].replace("\n", " ").strip()
            print(f"   {i}. [{s['score']:.4f}] {preview}...")


if __name__ == "__main__":
    main()
