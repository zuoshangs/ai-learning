# 第13天：AI 搜索增强助手 🔍

## 13.1 为什么要做搜索增强？

大语言模型有一个核心局限：**知识截止日期**。

| 模型 | 知识截止 |
|------|----------|
| GPT-4 | 2023年10月 |
| DeepSeek | 2024年7月 |
| Claude | 2025年初 |

如果你问"今天天气怎么样"、"昨天发生了什么新闻"或"某公司最新财报"，模型只能回答"我不知道"或编造信息。

**搜索增强（Search-Augmented Generation, SAG）** 就是解决这个问题的方案：

```
用户提问 → 实时搜索 → 获取最新信息 → LLM 整合 → 带来源的回答
```

## 13.2 系统架构

```
┌─────────────────────────────────────────────┐
│              用户提问: "今天AI领域有什么新闻"     │
└──────────────────┬──────────────────────────┘
                   ▼
┌─────────────────────────────────────────────┐
│           意图判断: 需要实时信息吗？             │
│        ↓ 是              ↓ 否                 │
│   ┌──────────┐    ┌──────────────┐           │
│   │ 网络搜索  │    │ RAG 知识库    │          │
│   │ DuckDuckGo│    │ 本地文档      │          │
│   └────┬─────┘    └──────┬───────┘           │
│        └────────┬────────┘                    │
│                 ▼                             │
│   ┌──────────────────────────┐                │
│   │  信息融合器 (Fusion Engine)│              │
│   │  搜索结果 + 知识库 + 上下文│              │
│   └──────────┬───────────────┘               │
│              ▼                                │
│   ┌──────────────────────────┐                │
│   │  LLM 生成最终回答 (带来源) │                │
│   └──────────────────────────┘                │
└─────────────────────────────────────────────┘
```

### 13.2.1 核心流程

1. **意图判断** — 分析用户问题是否需要实时信息
2. **数据采集** — 并行搜索网络 + 检索知识库
3. **信息融合** — 把多种来源的信息整理成 LLM 友好的格式
4. **答案生成** — LLM 基于融合信息生成带来源引用的回答
5. **结果呈现** — 显示答案 + 来源链接

## 13.3 模块一：搜索客户端

我们使用 **DuckDuckGo** 搜索（无需 API Key，免费），并支持切换为 Bing/Google。

```python
class SearchClient:
    """搜索客户端——支持 DuckDuckGo / Bing 切换"""
    
    def __init__(self, engine="duckduckgo"):
        self.engine = engine
    
    def search(self, query, num_results=5):
        if self.engine == "duckduckgo":
            return self._search_duckduckgo(query, num_results)
        return []
    
    def _search_duckduckgo(self, query, num_results):
        # DuckDuckGo HTML 解析搜索
        url = f"https://html.duckduckgo.com/html/?q={quote(query)}"
        resp = requests.get(url, headers=HEADERS, timeout=10)
        soup = BeautifulSoup(resp.text, "html.parser")
        
        results = []
        for result in soup.select(".result")[:num_results]:
            title = result.select_one(".result__title")
            snippet = result.select_one(".result__snippet")
            if title and snippet:
                results.append({
                    "title": title.get_text(strip=True),
                    "snippet": snippet.get_text(strip=True),
                    "url": title.a.get("href") if title.a else "",
                })
        return results
```

### 为什么用 DuckDuckGo？

| 搜索引擎 | API Key | 免费额度 | 中文支持 |
|----------|---------|----------|----------|
| DuckDuckGo | ❌ 不需要 | 无限制 | ✅ 好 |
| Bing | ✅ 需要 | 每月1000次 | ✅ 好 |
| Google | ✅ 需要 | 每月100次 | ✅ 好 |
| Serper | ✅ 需要 | 每月2500次 | ✅ 好 |

## 13.4 模块二：搜索意图分类

不是所有问题都需要实时搜索。我们用 LLM 来判断：

```python
def needs_search(query: str) -> bool:
    """判断是否需要实时搜索"""
    msg = call_llm([
        {"role": "system", "content": "判断用户问题是否需要实时信息。"
         "需要: 新闻/天气/股价/最新/今天/昨天/现在/趋势/比分/汇率等"
         "不需要: 概念解释/技术问题/历史知识/理论/代码等。"
         "只返回 JSON: {\"needs_search\": bool, \"reason\": \"原因\"}"},
        {"role": "user", "content": query}
    ], response_format={"type": "json_object"})
    return json.loads(msg).get("needs_search", False)
```

### 分类规则

| 需要搜索 ✅ | 不需要搜索 ❌ |
|------------|-------------|
| "今天AI有什么新闻" | "什么是 Transformer" |
| "苹果最新股价" | "Python 怎么排序" |
| "明天上海天气" | "二分查找算法" |
| "2026年世界杯" | "RESTful API 设计原则" |

## 13.5 模块三：信息融合引擎

这是核心——把多个来源的信息组织成 LLM 能理解的格式：

```python
class FusionEngine:
    """信息融合引擎——合并多来源信息"""
    
    def fuse(self, query, search_results, kb_results, history):
        context = []
        
        # 对话历史
        if history:
            context.append("【对话历史】")
            context.extend(history[-4:])  # 最近2轮
        
        # 网络搜索结果
        if search_results:
            context.append("\n【网络搜索结果】")
            for i, r in enumerate(search_results, 1):
                context.append(f"{i}. {r['title']}")
                context.append(f"   摘要: {r['snippet']}")
                context.append(f"   来源: {r['url']}")
        
        # 本地知识库
        if kb_results:
            context.append("\n【本地知识】")
            for cat, doc, score in kb_results:
                if score > 0.15:
                    context.append(f"[{cat}] {doc}")
        
        return "\n".join(context)
```

### 融合后的 prompt 示例

```
【对话历史】
用户: 最近AI有什么大新闻？
助手: 我来查一下...

【网络搜索结果】
1. OpenAI 发布 GPT-5
   摘要: OpenAI 今日发布 GPT-5...
   来源: https://...

2. Google 推出 Gemini 2.0
   摘要: Google DeepMind 宣布...

【本地知识】
[技术概念] GPT 是 Generative Pre-trained Transformer 的缩写

问题: 最近AI领域有什么重要进展？
请基于以上信息回答，标明来源。
```

## 13.6 完整搜索流程

```python
class SearchAssistant:
    def __init__(self):
        self.search = SearchClient()
        self.kb = MultiKnowledgeBase()
        self.fusion = FusionEngine()
        self.memory = []
    
    def chat(self, query):
        # 1. 判断是否需要搜索
        if not needs_search(query):
            return call_llm(self._build_context(query))
        
        # 2. 并行获取数据
        search_results = self.search.search(query)
        kb_results = self.kb.query(query)
        
        # 3. 融合信息
        context = self.fusion.fuse(query, search_results, kb_results, self.memory)
        
        # 4. 生成回答
        response = call_llm([
            {"role": "system", "content": f"基于以下信息回答用户问题，并在文中标注来源。\n\n{context}"},
            {"role": "user", "content": query}
        ])
        
        # 5. 保存记忆
        self.memory.append(f"用户: {query}")
        self.memory.append(f"助手: {response}")
        
        return response
```

## 13.7 思考题

1. **如果搜索结果为空，应该怎么处理？**
2. **搜索到的信息可能过时或错误，LLM 如何验证？**
3. **同时搜索多个引擎时，相同信息的去重策略是什么？**
4. **如何防止用户通过搜索获取敏感信息（越狱）？**

## 13.8 金句

> **搜索不是替代模型的知识，而是补充模型不知道的知识。**

> **好的搜索增强系统知道"什么时候该搜"和"什么时候不该搜"。**

> **LLM + 搜索 ≈ 一个永远不会过时的 AI 系统。**

---

*第13天完成！你已经学会了如何构建一个带实时搜索能力的 AI 助手。明天继续更高级的 Agent 应用！*
