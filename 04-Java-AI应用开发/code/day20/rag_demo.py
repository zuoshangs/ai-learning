"""
第20天：Spring AI RAG 实战 — Python 对照版

演示 RAG（Retrieval-Augmented Generation）完整流程：
1. 文档加载与切分
2. Embedding 向量化
3. 向量存储与检索
4. 增强生成（问答）
"""
import requests
import json
import os
import sys

# ============================================================
# 配置
# ============================================================
API_KEY = os.environ.get("DEEPSEEK_API_KEY")
if not API_KEY:
    with open(os.path.expanduser("~/.hermes/.env")) as f:
        for line in f:
            if "DEEPSEEK_API_KEY" in line:
                API_KEY = line.split("=", 1)[1].strip().strip('"').strip("'")
                break

CHAT_URL = "https://api.deepseek.com/v1/chat/completions"
EMBED_URL = "http://localhost:11434/api/embed"  # Ollama
HEADERS = {
    "Authorization": f"Bearer {API_KEY}",
    "Content-Type": "application/json"
}

# ============================================================
# 第一步：文档准备
# ============================================================
DOCUMENTS = {
    "spring-ai-intro.txt": """
Spring AI 是 Spring 生态中的官方 AI 框架，为 Java 开发者提供标准化 AI 接口。

核心特性：
ChatClient：声明式聊天客户端，支持同步/流式/函数式编程
提示词模板：支持参数化提示词模板
模型支持：OpenAI、Anthropic、Google Gemini、DeepSeek 等
结构化输出：自动将 AI 回复映射为 Java 对象
工具调用：通过 @Tool 注解注册工具方法
RAG 支持：内置 QuestionAnswerAdvisor
Embedding：多种 Embedding 模型 API
向量数据库：支持 PgVector、Milvus、Weaviate、Redis

RAG 流程：
1. 文档加载 → 2. 文本切分 → 3. 向量化 → 4. 存储
5. 检索（Query 向量化 + Top-K）→ 6. 增强 → 7. 生成

PgVector 是 PostgreSQL 的向量扩展：
- 精确检索和近似检索（IVFFlat / HNSW 索引）
- 支持 L2、IP、COSINE 距离
- 支持混合搜索（向量 + SQL WHERE）
"""
}

# ============================================================
# 第二步：文本切分
# ============================================================
def chunk_text(text, chunk_size=500, overlap=50):
    """简单按字符切分"""
    chunks = []
    start = 0
    while start < len(text):
        end = min(start + chunk_size, len(text))
        chunks.append(text[start:end])
        if end == len(text):
            break
        start = end - overlap
    return chunks

# ============================================================
# 第三步：Embedding（通过 Ollama 本地模型）
# ============================================================
def get_embedding(text):
    """调用 Ollama 获取文本向量"""
    resp = requests.post(EMBED_URL, json={
        "model": "qwen2.5:0.5b",
        "input": text
    })
    data = resp.json()
    return data["embeddings"][0]

# ============================================================
# 第四步：向量存储（内存）
# ============================================================
import numpy as np

class SimpleVectorStore:
    def __init__(self):
        self.vectors = []   # 存储向量
        self.texts = []     # 存储原文
        self.metadata = []  # 存储元数据
    
    def add(self, text, vector, metadata=None):
        self.vectors.append(vector)
        self.texts.append(text)
        self.metadata.append(metadata or {})
    
    def search(self, query_vector, top_k=3):
        """余弦相似度检索"""
        query_norm = np.array(query_vector)
        query_norm = query_norm / np.linalg.norm(query_norm)
        
        scores = []
        for vec in self.vectors:
            vec_norm = np.array(vec)
            vec_norm = vec_norm / np.linalg.norm(vec_norm)
            similarity = np.dot(query_norm, vec_norm)
            scores.append(similarity)
        
        # Top-K
        indices = np.argsort(scores)[-top_k:][::-1]
        results = []
        for i in indices:
            results.append({
                "text": self.texts[i],
                "score": scores[i],
                "metadata": self.metadata[i]
            })
        return results

# ============================================================
# 第五步：RAG 检索 + 增强 + 生成
# ============================================================
def rag_query(store, question):
    """RAG 问答完整流程"""
    # 1. Query 向量化
    q_vec = get_embedding(question)
    
    # 2. 检索 Top-K
    results = store.search(q_vec, top_k=2)
    
    # 3. 构建增强上下文
    context = "\n\n".join([r["text"] for r in results])
    
    print(f"  📚 检索到 {len(results)} 个文档块")
    for r in results:
        print(f"     相似度: {r['score']:.3f}")
    
    # 4. 生成回答
    messages = [
        {
            "role": "system",
            "content": f"""你是一个知识库问答助手。
基于以下参考文档回答用户的问题。

参考文档：
{context}

- 如果文档中有答案，请引用原文
- 如果文档中没有足够信息，诚实说不知道
- 用中文简洁回答"""
        },
        {"role": "user", "content": question}
    ]
    
    payload = {
        "model": "deepseek-chat",
        "messages": messages,
        "temperature": 0.1,
        "max_tokens": 1000
    }
    
    resp = requests.post(CHAT_URL, headers=HEADERS, json=payload, timeout=60)
    data = resp.json()
    return data["choices"][0]["message"]["content"]


# ============================================================
# 主流程
# ============================================================
print("=" * 65)
print("  RAG 完整流程演示 (Python 对照版)")
print("  Chat: DeepSeek | Embedding: Ollama(qwen2.5:0.5b)")
print("=" * 65)

# 1. 加载文档
print("\n📄 步骤1: 文档加载")
all_chunks = []
for filename, content in DOCUMENTS.items():
    print(f"  加载: {filename}")
    chunks = chunk_text(content)
    print(f"  切分: {len(chunks)} 块")
    all_chunks.extend([(c, filename) for c in chunks])

# 2. Embedding & 入库
print("\n📦 步骤2: 向量化 & 入库")
store = SimpleVectorStore()
for text, filename in all_chunks:
    vec = get_embedding(text)
    store.add(text, vec, {"source": filename})
    print(f"  ✅ {filename} — 向量维度: {len(vec)}")

# 3. RAG 问答
print("\n🤖 步骤3: RAG 问答")
questions = [
    "Spring AI 支持哪些大模型？",
    "RAG 架构的核心流程有哪些步骤？",
    "PgVector 支持哪几种距离类型？",
]

for q in questions:
    print(f"\n{'─' * 55}")
    print(f"❓ {q}")
    print(f"{'─' * 55}")
    answer = rag_query(store, q)
    print(f"💡 {answer}")

print(f"\n{'=' * 55}")
print("  ✅ Python RAG 演示完成")
print(f"{'=' * 55}")
