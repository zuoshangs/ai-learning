# 第8天：RAG（检索增强生成）🔍

> **学习目标：** 理解 RAG 的核心原理，掌握 Embedding 和向量检索的基础知识，
>   从零搭建一个"文档问答"系统——上传文档，问问题，AI 基于文档内容回答
> **预计时间：** 2.5小时
> **代码语言：** Python + Java 双版本
> **前置知识：** 第4天（API 调用）、第2-3天（提示词工程）

---

## 📋 目录

1. [为什么需要 RAG？](#1-为什么需要-rag)
2. [RAG 核心原理](#2-rag-核心原理)
3. [Embedding 基础概念](#3-embedding-基础概念)
4. [文档分块策略（Chunking）](#4-文档分块策略chunking)
5. [从零搭建：向量检索器](#5-从零搭建向量检索器)
6. [实战：完整 RAG 问答系统](#6-实战完整-rag-问答系统)
7. [课堂练习](#7-课堂练习)
8. [今日小结](#8-今日小结)

---

## 1. 为什么需要 RAG？

### 大模型的两大硬伤

**硬伤一：知识截止日期**

> GPT-4 的知识截止于 2023 年 10 月。问它"2024 年世界杯冠军是谁？"——它会编一个答案（幻觉）。

**硬伤二：无法知道你的私有数据**

> 你的公司内部文档、技术手册、产品规格——模型训练时根本没看过这些数据。

### RAG 的解决方案

RAG（Retrieval-Augmented Generation）= **检索 + 生成**。

```
用户提问 ──→ ① 检索相关文档 ──→ ② 把文档 + 问题一起给模型 ──→ ③ 模型基于文档回答
```

**RAG 的核心思想：** 不让模型"凭记忆"回答，而是先给它"翻书"，再让它回答。

### 对比：纯 LLM vs RAG

| 场景 | 纯 LLM | RAG |
|------|--------|-----|
| **"Python 怎么读文件？"** | ✅ 训练数据里有，直接答 | ✅ 也能答（多此一举） |
| **"我们公司 2024 年的安全规范是什么？"** | ❌ 不知道 | ✅ 检索内部文档后回答 |
| **"React 19 的新特性？"** | ⚠️ 可能过时 | ✅ 检索最新文档 |
| **"这份 PDF 总结了什么？"** | ❌ 太长塞不进上下文 | ✅ 分块检索后总结 |

### RAG 的典型应用场景

1. **客服系统** — 基于产品手册回答用户问题
2. **内部知识库** — 员工搜公司政策、流程文档
3. **文档分析** — 合同审查、论文分析、报告总结
4. **代码库问答** — 基于项目代码回答问题
5. **学习助手** — 基于教材内容回答学生提问

---

## 2. RAG 核心原理

### RAG 三段论

```
            ┌──────────────┐
            │   用户提问    │
            └──────┬───────┘
                   │
                   ▼
    ┌─────────────────────────────┐
    │  ① 检索 (Retrieval)        │
    │  ┌─────────────────────┐   │
    │  │ 文档 → 分块 → 向量化 │   │
    │  │ → 向量数据库        │   │
    │  └─────────────────────┘   │
    │         │  相似度搜索       │
    │         ▼                  │
    │  找到 Top-K 最相关片段     │
    └───────────┬─────────────────┘
                │
                ▼
    ┌─────────────────────────────┐
    │  ② 增强 (Augmentation)     │
    │  把检索到的文档片段         │
    │  拼接到用户问题前或后       │
    │                            │
    │  典型格式：                 │
    │  "根据以下资料回答问题：     │
    │   资料1：...                │
    │   资料2：...                │
    │   问题：...                │
    └───────────┬─────────────────┘
                │
                ▼
    ┌─────────────────────────────┐
    │  ③ 生成 (Generation)       │
    │  大模型基于上下文生成回答   │
    │  （有资料支撑，减少幻觉）    │
    └─────────────────────────────┘
```

### RAG 核心组件

| 组件 | 作用 | 我们用什么 |
|------|------|-----------|
| **文档加载器** | 从 PDF/Word/TXT 读取文本 | 纯文本文件（.txt） |
| **文本分割器** | 把长文档切成小段（Chunk） | 递归字符分割 |
| **Embedding 模型** | 把文本变成向量 | API Embedding 或本地模型 |
| **向量数据库** | 存储向量 + 相似度搜索 | 自建内存向量库 / ChromaDB |
| **大模型** | 基于检索结果生成回答 | DeepSeek API |
| **提示词模板** | 把检索结果 + 问题拼成 prompt | 结构化模板 |

---

## 3. Embedding 基础概念

### 什么是 Embedding？

**Embedding = 把文字变成一组数字（向量）。**

```
"猫"  →  [0.23, 0.87, -0.15, 0.42, ...]  (768维向量)
"狗"  →  [0.21, 0.85, -0.10, 0.39, ...]  (语义相近，向量也相近)
"汽车" →  [-0.11, 0.33, 0.76, -0.28, ...] (语义不同，向量也不同)
```

**关键性质：** 语义相近的文本，它们的向量在空间中距离也近。

### 直观理解

想象一个 2D 平面（虽然真实向量有几百上千维）：

```
          美食领域
             ↑
     "火锅"  ·  "烧烤"
             |
  "篮球" ·   |   "跑步"
  体育领域    |   体育领域
             |
             └───────→  科技领域
              "编程"  ·  "算法"
```

- "火锅"和"烧烤"都在"美食"区域，距离近
- "火锅"和"算法"距离远
- 向量就是每个点在 N 维空间中的坐标

### 相似度计算：余弦相似度

两个向量越相似，余弦值越接近 1：

```
cos(A, B) = (A · B) / (|A| × |B|)

示例：
"猫" vs "狗"   → cos ≈ 0.85  （语义相近）
"猫" vs "汽车" → cos ≈ 0.12  （语义不相关）
"猫" vs "猫"   → cos = 1.0   （完全相同）
```

### 常用的 Embedding 模型

| 模型 | 维度 | 特点 |
|------|------|------|
| `text-embedding-ada-002` (OpenAI) | 1536 | 闭源、付费、效果好 |
| `BAAI/bge-small-zh-v1.5` | 384 | 开源、中文友好、轻量 |
| `all-MiniLM-L6-v2` | 384 | 开源、英文好、速度快 |
| `DeepSeek Embedding` | 1024/2048 | 若可用，统一供应商 |

> **本教程使用方案：** 首次运行自动下载 `all-MiniLM-L6-v2`（~80MB），无需 API Key。

---

## 4. 文档分块策略（Chunking）

### 为什么要分块？

1. **模型上下文窗口有限** — 128K 也装不下一整本手册
2. **检索精度更高** — 一段话 vs 整本书，段落级别更精准
3. **成本更低** — 只 embedding 相关片段，不浪费 token

### 三种常用分块策略

```
策略一：固定长度分割
─────────────────
"第1章 引言......第2章... | 背景介绍......第3章..."
         chunk1               chunk2        chunk3
         [200字]             [200字]       [200字]
  优点：简单粗暴
  缺点：可能切断句子、段落

策略二：递归字符分割（推荐）
─────────────────
"第1章 引言\n\n大模型技术...\n\n1.1 背景\n\n..."
         ↓ 按段落分割
  chunk1: "第1章 引言"
  chunk2: "大模型技术..."
  chunk3: "1.1 背景"
  优点：保留语义边界
  缺点：chunk 大小不均匀

策略三：语义分割（高级）
─────────────────
  用模型判断"这里话题变了"再切
  优点：语义完整
  缺点：速度慢、成本高
```

### 本教程使用的分块策略

采用**递归字符分割**，其工作方式如下：

```
原始文本：
"第1章 引言\n\n大模型是当前 AI 领域的核心技术之一。\n\n1.1 背景\n\n近年来..."

分割参数：
  - chunk_size = 500     # 每个块最多 500 字符
  - chunk_overlap = 50   # 块间重叠 50 字符

分割过程：
  第1步：按 \n\n（段落）切
  第2步：如果段落太长，再按句子（。）切
  第3步：如果还太长，按字符数硬切
  第4步：加 overlap 防止信息断崖

结果：
  chunk1: "第1章 引言\n\n大模型是当前 AI 领域的核心技术之一。"
  chunk2: "核心技术之一。\n\n1.1 背景\n\n近年来..."      ← overlap 连接上下文
  chunk3: "近年来...（后续内容）"
```

> **overlap（重叠）为什么重要？**
> 如果不重叠，"1.1 背景"和解释它的文字可能被切到两个 chunk 里，模型检索到 chunk2 时不知道它在说"背景"。

---

## 5. 从零搭建：向量检索器

在引入 ChromaDB 之前，我们先手写一个简单的向量检索器——这能让你**真正理解向量数据库的工作原理**。

### 5.1 核心代码

```python
import math
import json

class SimpleVectorStore:
    """一个最简单的内存向量数据库"""
    
    def __init__(self):
        self.vectors = []     # [[vec1], [vec2], ...]
        self.texts = []       # ["文本1", "文本2", ...]
        self.metadatas = []   # [{"source": "...", ...}, ...]
    
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
```

### 5.2 测试检索器

```python
# 测试数据
store = SimpleVectorStore()

store.add("猫是哺乳动物，喜欢吃鱼", [0.9, 0.1, 0.0], {"topic": "动物"})
store.add("狗是哺乳动物，喜欢出去玩", [0.8, 0.2, 0.1], {"topic": "动物"})
store.add("Python 是一种编程语言", [0.1, 0.3, 0.9], {"topic": "编程"})
store.add("Java 也是一种编程语言", [0.0, 0.2, 0.8], {"topic": "编程"})

# 搜索"猫"
query = [0.85, 0.15, 0.05]
results = store.search(query, top_k=2)

for r in results:
    print(f"[{r['score']:.4f}] {r['text']} ({r['metadata']['topic']})")

# 输出：
# [0.9952] 猫是哺乳动物，喜欢吃鱼 (动物)
# [0.8785] 狗是哺乳动物，喜欢出去玩 (动物)
```

---

## 6. 实战：完整 RAG 问答系统

### 完整流程

```
                    用户问题
                       │
                       ▼
               ┌─────────────────┐
               │ ① 问题向量化     │
               │ 用 Embedding 模型 │
               │ 把问题转成向量    │
               └────────┬────────┘
                        │
                        ▼
               ┌─────────────────┐
               │ ② 向量检索       │
               │ 在向量库中搜索   │
               │ 找到 Top-3 相关  │
               └────────┬────────┘
                        │
                        ▼
               ┌─────────────────┐
               │ ③ 构造 Prompt    │
               │ "根据以下资料     │
               │  回答问题..."    │
               └────────┬────────┘
                        │
                        ▼
               ┌─────────────────┐
               │ ④  LLM 生成      │
               │ 调用 DeepSeek    │
               │ 生成最终回答     │
               └─────────────────┘
```

### 6.1 Python 完整实现

```python
"""
rag_demo.py — 完整 RAG 问答系统
用法：
  1. 准备一个 .txt 文档放在 data/ 目录
  2. 运行 python3 rag_demo.py
  3. 输入问题，AI 基于文档内容回答
"""

import os, json, math, requests
from sentence_transformers import SentenceTransformer
from simple_vector_store import SimpleVectorStore  # 上面写好的

# ─── 配置 ─────────────────────────────────────
DEEPSEEK_API_KEY = os.environ.get("DEEPSEEK_API_KEY", "你的API_KEY")
DEEPSEEK_URL = "https://api.deepseek.com/v1/chat/completions"
EMBEDDING_MODEL = "all-MiniLM-L6-v2"  # 自动下载
DATA_DIR = "data"
CHUNK_SIZE = 500
CHUNK_OVERLAP = 50

# ─── 文本分割 ─────────────────────────────────
def chunk_text(text: str, chunk_size=500, overlap=50) -> list:
    """递归字符分割"""
    chunks = []
    
    # 先按段落分割
    paragraphs = text.split("\n\n")
    
    current = ""
    for para in paragraphs:
        if len(current) + len(para) < chunk_size:
            current += para + "\n\n"
        else:
            if current.strip():
                chunks.append(current.strip())
            current = para + "\n\n"
    
    if current.strip():
        chunks.append(current.strip())
    
    # 如果单个 chunk 仍然超过 chunk_size，按句子再次分割
    final_chunks = []
    for chunk in chunks:
        if len(chunk) <= chunk_size:
            final_chunks.append(chunk)
        else:
            sentences = chunk.replace("。", "。\n").split("\n")
            current = ""
            for sent in sentences:
                if len(current) + len(sent) < chunk_size:
                    current += sent
                else:
                    if current.strip():
                        final_chunks.append(current.strip())
                    current = sent
            
            if current.strip():
                final_chunks.append(current.strip())
    
    return final_chunks

# ─── 构建知识库 ─────────────────────────────────
def build_knowledge_base(file_path: str, store: SimpleVectorStore,
                         embed_model):
    """读取文件，分割，embedding，存入向量库"""
    with open(file_path, "r", encoding="utf-8") as f:
        text = f.read()
    
    chunks = chunk_text(text, CHUNK_SIZE, CHUNK_OVERLAP)
    print(f"  📄 文档已分割为 {len(chunks)} 个片段")
    
    for i, chunk in enumerate(chunks):
        # 生成向量
        vector = embed_model.encode(chunk).tolist()
        store.add(
            text=chunk,
            vector=vector,
            metadata={"source": os.path.basename(file_path), "chunk": i}
        )
    
    print(f"  ✅ 已存储 {len(chunks)} 个向量")
    return chunks

# ─── RAG 查询 ──────────────────────────────────
def rag_query(question: str, store: SimpleVectorStore,
              embed_model, top_k: int = 3) -> str:
    """RAG 查询：检索 + 生成"""
    
    # ① 问题向量化
    query_vector = embed_model.encode(question).tolist()
    
    # ② 检索相关文档
    results = store.search(query_vector, top_k=top_k)
    
    # ③ 构造 Prompt
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
    
    # ④ 调用大模型生成
    resp = requests.post(
        DEEPSEEK_URL,
        headers={
            "Authorization": f"Bearer {DEEPSEEK_API_KEY}",
            "Content-Type": "application/json"
        },
        json={
            "model": "deepseek-chat",
            "messages": [
                {"role": "system", "content": "你是一个文档问答助手。"},
                {"role": "user", "content": prompt}
            ],
            "temperature": 0.3,
            "max_tokens": 1024
        },
        timeout=30
    )
    
    data = resp.json()
    answer = data["choices"][0]["message"]["content"]
    
    return answer, results

# ─── 主函数 ────────────────────────────────────
def main():
    print("=" * 50)
    print("🔍 RAG 文档问答系统")
    print("=" * 50)
    
    # 加载 Embedding 模型
    print("\n⏳ 加载 Embedding 模型...")
    embed_model = SentenceTransformer(EMBEDDING_MODEL)
    print("   ✅ 模型加载完成")
    
    # 初始化向量库
    store = SimpleVectorStore()
    
    # 检查 data 目录
    if not os.path.exists(DATA_DIR):
        os.makedirs(DATA_DIR)
        print(f"\n📁 请把 .txt 文档放入 {DATA_DIR}/ 目录后重新运行")
        return
    
    files = [f for f in os.listdir(DATA_DIR) if f.endswith(".txt")]
    if not files:
        print(f"\n📁 {DATA_DIR}/ 目录中没有 .txt 文件")
        print("   请放入一个 .txt 文档后重新运行")
        return
    
    # 构建知识库
    print(f"\n📚 构建知识库...")
    for file_name in files:
        file_path = os.path.join(DATA_DIR, file_name)
        print(f"\n   📖 处理: {file_name}")
        build_knowledge_base(file_path, store, embed_model)
    
    print(f"\n✅ 知识库构建完成（共 {len(store.texts)} 个片段）")
    
    # 问答循环
    print("\n" + "=" * 50)
    print("💬 可以开始提问了（输入 quit 退出）")
    print("=" * 50)
    
    while True:
        question = input("\n❓ 问题: ").strip()
        if question.lower() in ("quit", "exit", "q"):
            break
        if not question:
            continue
        
        print("   ⏳ 检索中...")
        answer, sources = rag_query(question, store, embed_model)
        
        print(f"\n📝 回答: {answer}")
        print(f"\n📎 参考来源 ({len(sources)} 条):")
        for i, s in enumerate(sources, 1):
            print(f"   {i}. [相似度 {s['score']:.4f}] {s['text'][:80]}...")


if __name__ == "__main__":
    main()
```

### 6.2 Java 完整实现

```java
// RagDemo.java — 完整 RAG 问答系统
// 依赖：org.json（处理 JSON）、java.net.http（JDK 11+）
// Maven: implementation 'org.json:json:20231013'

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class RagDemo {

    // ─── 数据结构 ─────────────────────────────
    static class VectorRecord {
        double[] vector;
        String text;
        Map<String, String> metadata;
        
        VectorRecord(double[] vector, String text, Map<String, String> metadata) {
            this.vector = vector;
            this.text = text;
            this.metadata = metadata;
        }
    }
    
    static class SearchResult {
        double score;
        String text;
        Map<String, String> metadata;
        
        SearchResult(double score, String text, Map<String, String> metadata) {
            this.score = score;
            this.text = text;
            this.metadata = metadata;
        }
    }
    
    static class SimpleVectorStore {
        List<VectorRecord> records = new ArrayList<>();
        
        void add(double[] vector, String text, Map<String, String> metadata) {
            records.add(new VectorRecord(vector, text, metadata));
        }
        
        double cosineSimilarity(double[] a, double[] b) {
            double dot = 0, normA = 0, normB = 0;
            for (int i = 0; i < a.length; i++) {
                dot += a[i] * b[i];
                normA += a[i] * a[i];
                normB += b[i] * b[i];
            }
            normA = Math.sqrt(normA);
            normB = Math.sqrt(normB);
            if (normA == 0 || normB == 0) return 0;
            return dot / (normA * normB);
        }
        
        List<SearchResult> search(double[] queryVector, int topK) {
            List<SearchResult> scored = new ArrayList<>();
            for (VectorRecord r : records) {
                double score = cosineSimilarity(queryVector, r.vector);
                scored.add(new SearchResult(score, r.text, r.metadata));
            }
            scored.sort((a, b) -> Double.compare(b.score, a.score));
            return scored.subList(0, Math.min(topK, scored.size()));
        }
    }
    
    // ─── 简单的 Embedding（使用向量 API）───────────
    // 注：Java 中调用 Embedding API，这里使用 DeepSeek API
    static class EmbeddingClient {
        String apiKey;
        HttpClient client = HttpClient.newHttpClient();
        
        EmbeddingClient(String apiKey) {
            this.apiKey = apiKey;
        }
        
        double[] embed(String text) throws Exception {
            String json = String.format(
                "{\"model\":\"deepseek-embedding\",\"input\":[\"%s\"]}",
                text.replace("\"", "\\\"").replace("\n", "\\n")
            );
            
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.deepseek.com/v1/embeddings"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
            
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            
            // 简易 JSON 解析
            String body = resp.body();
            int start = body.indexOf("\"embedding\":[") + 12;
            int end = body.indexOf("]", start);
            String[] parts = body.substring(start, end).split(",");
            double[] vec = new double[parts.length];
            for (int i = 0; i < parts.length; i++) {
                vec[i] = Double.parseDouble(parts[i].trim());
            }
            return vec;
        }
    }
    
    // ─── 文本分割 ─────────────────────────────
    static List<String> chunkText(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        String[] paragraphs = text.split("\n\n");
        
        StringBuilder current = new StringBuilder();
        for (String para : paragraphs) {
            if (current.length() + para.length() < chunkSize) {
                current.append(para).append("\n\n");
            } else {
                if (current.toString().trim().length() > 0) {
                    chunks.add(current.toString().trim());
                }
                current = new StringBuilder(para).append("\n\n");
            }
        }
        if (current.toString().trim().length() > 0) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }
    
    // ─── RAG 查询 ─────────────────────────────
    static String ragQuery(String question, SimpleVectorStore store,
                           EmbeddingClient embedClient, int topK) throws Exception {
        // ① 问题向量化
        double[] queryVec = embedClient.embed(question);
        
        // ② 检索
        List<SearchResult> results = store.search(queryVec, topK);
        
        // ③ 构造 Prompt
        String context = results.stream()
            .map(r -> r.text)
            .collect(Collectors.joining("\n\n"));
        
        String prompt = String.format(
            "你是一个专业的文档问答助手。请根据以下资料回答用户的问题。\n\n" +
            "资料：\n%s\n\n问题：%s\n\n" +
            "要求：\n- 如果资料中有相关信息，请基于资料回答\n" +
            "- 如果资料中没有相关信息，请明确说'资料中没有提到'\n" +
            "- 不要编造信息\n- 用中文回答",
            context, question
        );
        
        // ④ 调用 DeepSeek
        String json = String.format(
            "{\"model\":\"deepseek-chat\",\"messages\":[" +
            "{\"role\":\"system\",\"content\":\"你是一个文档问答助手。\"}," +
            "{\"role\":\"user\",\"content\":\"%s\"}]," +
            "\"temperature\":0.3,\"max_tokens\":1024}",
            prompt.replace("\"", "\\\"").replace("\n", "\\n")
        );
        
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("https://api.deepseek.com/v1/chat/completions"))
            .header("Authorization", "Bearer " + embedClient.apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
        
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        
        // 简易解析
        String body = resp.body();
        int start = body.indexOf("\"content\":\"") + 11;
        int end = body.indexOf("\"", start);
        return body.substring(start, end)
            .replace("\\n", "\n")
            .replace("\\\"", "\"");
    }
    
    // ─── 主函数 ────────────────────────────────
    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv().getOrDefault("DEEPSEEK_API_KEY", "你的API_KEY");
        
        System.out.println("=".repeat(50));
        System.out.println("🔍 RAG 文档问答系统 (Java)");
        System.out.println("=".repeat(50));
        
        // 初始化
        System.out.println("\n⏳ 初始化 Embedding 客户端...");
        EmbeddingClient embedClient = new EmbeddingClient(apiKey);
        SimpleVectorStore store = new SimpleVectorStore();
        
        // 读取 data 目录
        Path dataDir = Paths.get("data");
        if (!Files.exists(dataDir)) {
            Files.createDirectories(dataDir);
            System.out.println("\n📁 请把 .txt 文档放入 data/ 目录后重新运行");
            return;
        }
        
        List<Path> files = Files.list(dataDir)
            .filter(p -> p.toString().endsWith(".txt"))
            .collect(Collectors.toList());
        
        if (files.isEmpty()) {
            System.out.println("\n📁 data/ 目录中没有 .txt 文件");
            return;
        }
        
        // 构建知识库
        System.out.println("\n📚 构建知识库...");
        for (Path file : files) {
            System.out.println("\n   📖 处理: " + file.getFileName());
            String text = Files.readString(file);
            List<String> chunks = chunkText(text, 500, 50);
            System.out.println("   📄 文档已分割为 " + chunks.size() + " 个片段");
            
            for (int i = 0; i < chunks.size(); i++) {
                double[] vec = embedClient.embed(chunks.get(i));
                store.add(vec, chunks.get(i), Map.of(
                    "source", file.getFileName().toString(),
                    "chunk", String.valueOf(i)
                ));
            }
        }
        
        System.out.println("\n✅ 知识库构建完成（共 " + store.records.size() + " 个片段）");
        
        // 问答循环
        System.out.println("\n💬 可以开始提问了（输入 quit 退出）");
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("\n❓ 问题: ");
                String question = scanner.nextLine().trim();
                if (question.equalsIgnoreCase("quit")) break;
                if (question.isEmpty()) continue;
                
                System.out.print("   ⏳ 检索中...\n");
                String answer = ragQuery(question, store, embedClient, 3);
                System.out.println("\n📝 回答: " + answer);
            }
        }
    }
}
```

---

## 7. 课堂练习

### 练习1：第一次 RAG 体验

1. 在 `data/` 目录下创建一个 `.txt` 文件，写入如下内容：

```
# AI 大模型技术发展简史

## 2017年
Google 发表论文《Attention Is All You Need》，提出了 Transformer 架构。
这是现代大模型的基础。

## 2018年
OpenAI 发布 GPT-1，参数量 1.17 亿。
BERT 模型发布，参数量 3.4 亿。

## 2019年
OpenAI 发布 GPT-2，参数量 15 亿。

## 2020年
OpenAI 发布 GPT-3，参数量 1750 亿。
标志着大模型时代的正式来临。

## 2022年
ChatGPT 发布，两个月内用户突破 1 亿。
Stable Diffusion 发布，开源文生图模型。

## 2023年
GPT-4 发布，支持多模态输入。
Llama 2 开源，推动开源大模型发展。

## 2024年
Sora 发布，文生视频模型。
DeepSeek V2 发布，创新的 MoE 架构。
Claude 3.5 Sonnet 发布。
```

2. 运行 RAG 系统，提问以下问题：

```
Q1: Transformer 架构是哪一年提出的？
Q2: GPT-3 有多少参数？
Q3: 2024年有哪些重要发布？
Q4: ChatGPT 用了多久达到 1 亿用户？
Q5: DeepSeek V2 有什么创新？
```

3. 观察回答是否准确、是否基于文档内容。

<details>
<summary>点击查看预期结果</summary>

- Q1: 2017年 ✅（文档中有）
- Q2: 1750 亿 ✅（文档中有）
- Q3: Sora / DeepSeek V2 / Claude 3.5 ✅（文档中有）
- Q4: 2个月 ✅（文档中有）
- Q5: 创新的 MoE 架构 ✅（文档中有）

所有问题都应该能准确回答，因为信息都在文档中。
</details>

### 练习2：文档中没有的信息

提问以下问题，观察模型如何回应：

```
Q: Linux 命令 ls 的用法是什么？
Q: Python 的 requests 库怎么用？
```

<details>
<summary>点击查看预期结果</summary>

模型应该回答"资料中没有提到"或类似表述，而不应该编造答案。

如果模型编造了答案（它可能因为训练数据中有这些知识而回答），说明你的提示词中"不要编造信息"的约束不够强。尝试修改 system prompt 或 user prompt 中的约束。
</details>

### 练习3：对比有 RAG 和无 RAG

修改代码，去掉检索步骤，直接用大模型回答同一个问题。对比两种方式的差异：

```
问题：DeepSeek V2 有什么创新？

无 RAG：模型可能回答不准确或泛泛而谈
有 RAG：准确回答"创新的 MoE 架构"
```

<details>
<summary>点击查看参考代码</summary>

```python
# 无 RAG 版本
def no_rag_query(question):
    resp = requests.post(DEEPSEEK_URL,
        json={
            "model": "deepseek-chat",
            "messages": [{"role": "user", "content": question}],
            "temperature": 0.3
        })
    return resp.json()["choices"][0]["message"]["content"]
```
</details>

### 练习4：调整 Chunk 大小

修改 `CHUNK_SIZE` 和 `CHUNK_OVERLAP` 参数，观察对检索效果的影响：

```
实验组 A: chunk_size=200, overlap=20
实验组 B: chunk_size=500, overlap=50  (默认)
实验组 C: chunk_size=1000, overlap=100

提问："Transformer 架构"
观察哪个 chunk 大小的检索结果最相关。
```

---

## 8. 今日小结

### 核心概念速查

| 概念 | 一句话 | 关键要点 |
|------|--------|---------|
| **RAG** | 检索 + 生成，让模型先翻书再回答 | 解决知识截止 + 私有数据问题 |
| **Embedding** | 把文字变成向量，语义相近则距离近 | 余弦相似度衡量距离 |
| **Chunking** | 把长文档切成小段 | chunk_size + overlap 是关键参数 |
| **向量检索** | 在向量空间中找最相似的片段 | top_k 控制召回数量 |
| **Prompt 增强** | 把检索结果拼入 prompt | 告诉模型"基于资料回答" |
| **Cosine Similarity** | A·B / (|A|×|B|) | 越接近 1 越相似 |

### RAG 调优口诀

```
文档先分块，chunk 大小五百好
重叠五十不断桥，检索召回要三条
问题也要向量化，余弦相似比一比
prompt 里面放资料，模型看了不乱跑
资料没有就说无，绝不凭空瞎编造
```

### 今日检查清单

- [ ] 理解 RAG 的三段论：检索 → 增强 → 生成
- [ ] 理解 Embedding 和余弦相似度原理
- [ ] 理解 chunk_size 和 overlap 的作用
- [ ] 运行 `rag_demo.py` 构建第一个知识库
- [ ] 提问至少 3 个问题，验证 RAG 效果
- [ ] 练习 3：对比有 RAG vs 无 RAG 的区别
- [ ] 理解 `SimpleVectorStore` 的搜索原理
- [ ] 在 `~/ai-learning/week1/notes/day8.md` 记录学习笔记

### 明天预告

**第 9 天：工具调用（Function Calling）🛠️**

- 让大模型调用外部工具（天气、搜索、计算器）
- 第一次 Function Call
- 多工具编排
- 构建"智能助手"——可以查天气 + 算数学 + 搜资料

---

> 📝 **学习笔记：** 在 `~/ai-learning/week2/notes/day8.md` 记录今天的收获
> ❓ **遇到问题：** 随时问我
> 🚀 **学有余力：** 试试用不同的 Embedding 模型（如 BAAI/bge-small-zh-v1.5）对比检索效果
> 💡 **思考：** RAG 相当于给大模型配了一本"参考答案"，但参考答案不全面怎么办？
