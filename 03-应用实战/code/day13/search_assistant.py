"""
search_assistant.py — AI 搜索增强助手

结合实时网页搜索 + RAG 知识库 + 信息融合
第13天项目实战
"""

import json
import os
import re
import requests
import math
from datetime import datetime
from urllib.parse import quote_plus


# ═══════════════════════════════════════════════
# API & 配置
# ═══════════════════════════════════════════════

def get_api_key():
    key = os.environ.get("DEEPSEEK_API_KEY", "")
    if not key:
        try:
            with open(os.path.expanduser("~/.hermes/auth.json")) as f:
                auth = json.load(f)
            pool = auth.get("credential_pool", {}).get("deepseek", [])
            if pool:
                key = pool[0].get("access_token", "")
        except Exception:
            pass
    return key

API_KEY = get_api_key()
API_URL = "https://api.deepseek.com/v1/chat/completions"


def call_llm(messages, temperature=0.3, response_format=None, max_tokens=1024):
    if not API_KEY:
        return {"content": "[API Key 未配置]"}
    
    payload = {
        "model": "deepseek-chat",
        "messages": messages,
        "temperature": temperature,
        "max_tokens": max_tokens,
    }
    if response_format:
        payload["response_format"] = response_format
    
    try:
        resp = requests.post(API_URL, headers={"Authorization": f"Bearer {API_KEY}"},
                             json=payload, timeout=30)
        return resp.json()["choices"][0]["message"]
    except Exception as e:
        return {"content": f"[网络错误: {str(e)[:30]}]"}


# ═══════════════════════════════════════════════
# 简易向量存储（RAG 知识库）
# ═══════════════════════════════════════════════

def cosine_similarity(a, b):
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(x * x for x in b))
    return dot / (na * nb) if na > 0 and nb > 0 else 0


class SimpleVectorStore:
    """轻量级向量存储"""
    
    def __init__(self):
        self.documents = []
        self.vectors = []
    
    def _tokenize(self, text):
        return re.findall(r'[\w\u4e00-\u9fff]+', text.lower())
    
    def _vectorize(self, text):
        tokens = self._tokenize(text)
        all_terms = set()
        for doc in self.documents:
            all_terms.update(self._tokenize(doc))
        all_terms.update(tokens)
        
        term_list = sorted(all_terms)
        vec = [0.0] * len(term_list)
        for t in tokens:
            if t in term_list:
                vec[term_list.index(t)] += 1.0
        return vec
    
    def add_document(self, text):
        self.documents.append(text)
        self.vectors.append(self._vectorize(text))
    
    def search(self, query, top_k=2):
        if not self.documents:
            return []
        q_vec = self._vectorize(query)
        scored = []
        for i, (doc, vec) in enumerate(zip(self.documents, self.vectors)):
            score = cosine_similarity(q_vec, vec)
            scored.append((doc, score))
        scored.sort(key=lambda x: -x[1])
        return scored[:top_k]


class MultiKnowledgeBase:
    """多类别知识库"""
    
    def __init__(self):
        self.stores = {}
    
    def add_category(self, name, documents):
        store = SimpleVectorStore()
        for doc in documents:
            store.add_document(doc)
        self.stores[name] = store
        print(f"  📚 加载知识库 [{name}]: {len(documents)} 条")
    
    def query(self, question, top_k=2):
        results = []
        for category, store in self.stores.items():
            for doc, score in store.search(question, top_k):
                results.append((category, doc, score))
        results.sort(key=lambda x: -x[2])
        return results[:top_k]


# ═══════════════════════════════════════════════
# 模块一：搜索客户端
# ═══════════════════════════════════════════════

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                  "AppleWebKit/537.36 (KHTML, like Gecko) "
                  "Chrome/120.0.0.0 Safari/537.36"
}


class SearchClient:
    """搜索客户端——支持 DuckDuckGo / 模拟搜索"""
    
    def __init__(self, engine="duckduckgo"):
        self.engine = engine
    
    def search(self, query, num_results=4):
        """执行搜索"""
        if self.engine == "duckduckgo":
            return self._search_duckduckgo(query, num_results)
        elif self.engine == "mock":
            return self._search_mock(query, num_results)
        return []
    
    def _search_duckduckgo(self, query, num_results):
        """DuckDuckGo HTML 搜索"""
        try:
            url = f"https://html.duckduckgo.com/html/?q={quote_plus(query)}"
            resp = requests.get(url, headers=HEADERS, timeout=10)
            
            if resp.status_code != 200:
                print(f"  ⚠️  搜索返回状态码 {resp.status_code}")
                return self._search_mock(query)
            
            results = self._parse_ddg_results(resp.text, num_results)
            if results:
                print(f"  🔍 搜索到 {len(results)} 条结果")
            else:
                print(f"  ⚠️  搜索无结果，使用模拟数据")
                results = self._search_mock(query)
            return results
            
        except requests.Timeout:
            print(f"  ⚠️  搜索超时，使用模拟数据")
            return self._search_mock(query)
        except Exception as e:
            print(f"  ⚠️  搜索失败: {str(e)[:40]}，使用模拟数据")
            return self._search_mock(query)
    
    def _parse_ddg_results(self, html, num_results):
        """解析 DuckDuckGo HTML 搜索结果（正则，避免依赖 BeautifulSoup）"""
        results = []
        
        # 查找结果块
        blocks = re.findall(
            r'<a rel="nofollow noopener noreferrer" class="result__a" href="([^"]+)"[^>]*>'
            r'\s*(.*?)\s*</a>',
            html, re.DOTALL
        )
        
        # 查找摘要
        snippets = re.findall(
            r'<a class="result__snippet"[^>]*>(.*?)</a>',
            html, re.DOTALL
        )
        
        for i, (url, title) in enumerate(blocks[:num_results]):
            snippet = snippets[i] if i < len(snippets) else ""
            # 清理 HTML 标签
            snippet = re.sub(r'<[^>]+>', '', snippet).strip()
            title = re.sub(r'<[^>]+>', '', title).strip()
            
            results.append({
                "title": title,
                "snippet": snippet,
                "url": url,
            })
        
        return results
    
    def _search_mock(self, query, num_results=4):
        """模拟搜索结果（演示/离线备用）"""
        all_results = [
            {
                "title": f"关于「{query}」的最新报道",
                "snippet": f"近日，{query} 领域取得了重要进展。"
                          f"业内人士表示，这项技术将对行业产生深远影响。"
                          f"多家公司已经宣布加大在该领域的投入。",
                "url": f"https://news.example.com/{quote_plus(query)}",
            },
            {
                "title": f"深度分析：{query}的趋势与展望",
                "snippet": f"本文深入分析了 {query} 的发展趋势、"
                          f"关键技术和市场前景。专家认为未来一年将是该领域的关键期。",
                "url": f"https://analysis.example.com/{quote_plus(query)}",
            },
            {
                "title": f"{query} 入门指南与实践",
                "snippet": f"一篇全面的 {query} 入门教程，"
                          f"涵盖基础概念、核心技术和最佳实践。适合初学者快速上手。",
                "url": f"https://tutorial.example.com/{quote_plus(query)}",
            },
        ]
        return all_results[:num_results]


# ═══════════════════════════════════════════════
# 模块二：搜索意图分类
# ═══════════════════════════════════════════════

def needs_search(query: str) -> bool:
    """判断是否需要实时搜索（关键词优先，LLM 辅助）"""
    # 关键词快速匹配（不需要调 API）
    now_keywords = [
        "新闻", "天气", "股价", "最新", "今天", "昨天", "现在",
        "趋势", "比分", "汇率", "股票", "行情", "热点", "实时",
        "news", "today", "weather", "stock", "price", "latest",
        "current", "now", "update", "breaking", "report",
        "财报", "发布", "宣布", "推出", "涨价", "降价", "2025", "2026",
    ]
    
    for kw in now_keywords:
        if kw in query:
            return True
    
    # LLM 辅助判断（仅当关键词没匹配到时）
    try:
        msg = call_llm([
            {"role": "system", "content": "判断用户问题是否需要实时网络信息。"
             "需要: 新闻/天气/股票/最新进展/当前情况。"
             "不需要: 概念解释/技术问题/历史/代码/理论。"
             "只返回 JSON: {\"needs_search\": true/false}"},
            {"role": "user", "content": query}
        ], temperature=0, response_format={"type": "json_object"}, max_tokens=100)
        
        result = json.loads(msg.get("content", "{}"))
        return result.get("needs_search", False)
    except Exception:
        return False


# ═══════════════════════════════════════════════
# 模块三：信息融合引擎
# ═══════════════════════════════════════════════

class FusionEngine:
    """信息融合引擎"""
    
    def fuse(self, query, search_results, kb_results, history=None):
        parts = []
        
        # 对话历史
        if history and len(history) >= 2:
            parts.append("【对话历史】")
            for msg in history[-4:]:
                parts.append(msg)
            parts.append("")
        
        # 网络搜索结果
        if search_results:
            parts.append("【网络搜索结果】")
            for i, r in enumerate(search_results, 1):
                parts.append(f"{i}. {r['title']}")
                parts.append(f"   摘要: {r['snippet']}")
                parts.append(f"   来源: {r['url']}")
            parts.append("")
        
        # 本地知识
        if kb_results:
            parts.append("【本地知识库】")
            for cat, doc, score in kb_results:
                if score > 0.1:
                    parts.append(f"[{cat}] {doc}")
            parts.append("")
        
        return "\n".join(parts)
    
    def get_system_prompt(self, context):
        return f"""你是一个信息搜索助手「小搜」。你的职责：

1. 基于提供的信息回答用户问题
2. 回答时标注信息来源（如 [来源1]）
3. 如果信息不足以回答，诚实地说"搜索结果中未找到相关信息"
4. 用中文回答，简洁有条理

参考资料：
{context}"""


# ═══════════════════════════════════════════════
# 模块四：搜索协调器
# ═══════════════════════════════════════════════

class SearchCoordinator:
    """搜索协调器——决定搜索策略"""
    
    STRATEGIES = {
        "news": {"depth": "deep", "sources": ["web", "kb"]},
        "weather": {"depth": "quick", "sources": ["web"]},
        "tech": {"depth": "deep", "sources": ["web", "kb"]},
        "general": {"depth": "normal", "sources": ["web", "kb"]},
        "knowledge": {"depth": "quick", "sources": ["kb"]},
    }
    
    @staticmethod
    def get_strategy(query):
        """根据查询类型返回搜索策略"""
        query_lower = query.lower()
        if any(kw in query_lower for kw in ["新闻", "news", "最新", "发布"]):
            return SearchCoordinator.STRATEGIES["news"]
        if any(kw in query_lower for kw in ["天气", "weather", "温度"]):
            return SearchCoordinator.STRATEGIES["weather"]
        if any(kw in query_lower for kw in ["python", "代码", "编程", "算法", "技术"]):
            return SearchCoordinator.STRATEGIES["tech"]
        if any(kw in query_lower for kw in ["什么是", "概念", "定义", "意思", "history"]):
            return SearchCoordinator.STRATEGIES["knowledge"]
        return SearchCoordinator.STRATEGIES["general"]


# ═══════════════════════════════════════════════
# 主 Agent 类
# ═══════════════════════════════════════════════

class SearchAssistant:
    """AI 搜索增强助手"""
    
    def __init__(self):
        # 搜索客户端
        self.search = SearchClient(engine="duckduckgo")
        
        # 知识库
        self.kb = MultiKnowledgeBase()
        self._init_knowledge_base()
        
        # 融合引擎
        self.fusion = FusionEngine()
        
        # 对话记忆
        self.history = []
        
        # 统计
        self.stats = {"searches": 0, "kb_hits": 0}
        
        print("=" * 55)
        print("  🔍 AI 搜索增强助手「小搜」")
        print("  结合实时搜索 + 本地知识库")
        print("=" * 55)
        print("  📌 /search:force <query>  强制搜索")
        print("  📌 /search:off <query>    关闭搜索")
        print("  📌 /stats                 查看统计")
        print("  📌 /history               查看对话历史")
    
    def _init_knowledge_base(self):
        """初始化本地知识库"""
        kb_data = {
            "AI技术": [
                "Transformer 是 2017 年 Google 提出的深度学习架构，核心是自注意力机制",
                "GPT 是 Generative Pre-trained Transformer 的缩写，由 OpenAI 开发",
                "RAG 是 Retrieval-Augmented Generation 的缩写，结合检索和生成",
                "Agent 指能自主使用工具完成任务的 AI 系统",
                "Function Calling 是让 LLM 能调用外部函数的技术",
            ],
            "编程": [
                "Python 是一种解释型、面向对象的高级编程语言",
                "Java 是一种静态类型、面向对象的编程语言，运行在 JVM 上",
                "REST API 是基于 HTTP 协议的应用程序接口设计风格",
                "JSON 是一种轻量级的数据交换格式",
            ],
        }
        for cat, docs in kb_data.items():
            self.kb.add_category(cat, docs)
    
    def chat(self, query: str, force_search=None) -> str:
        """
        处理用户查询
        force_search: None=自动判断, True=强制搜索, False=不搜索
        """
        
        # 判断搜索策略
        if force_search is None:
            should_search = needs_search(query)
        else:
            should_search = force_search
        
        strategy = SearchCoordinator.get_strategy(query)
        
        search_results = []
        kb_results = []
        search_info = []
        
        # 如果需要搜索
        if should_search:
            if "web" in strategy["sources"]:
                print(f"  🔍 正在搜索: {query}")
                search_results = self.search.search(query)
                self.stats["searches"] += 1
                search_info.append(f"网络搜索: {len(search_results)} 条结果")
            
            if "kb" in strategy["sources"]:
                kb_results = self.kb.query(query, top_k=2)
                if kb_results:
                    self.stats["kb_hits"] += 1
                    search_info.append(f"知识库: {len(kb_results)} 条匹配")
        
        # 如果没有搜索，只查知识库
        if not should_search and "kb" in strategy["sources"]:
            kb_results = self.kb.query(query, top_k=2)
            if kb_results:
                self.stats["kb_hits"] += 1
                search_info.append(f"知识库: {len(kb_results)} 条匹配")
        
        # 信息融合
        context = self.fusion.fuse(query, search_results, kb_results, self.history)
        
        # 构建消息
        messages = []
        if should_search and search_results:
            system_prompt = self.fusion.get_system_prompt(context)
            messages.append({"role": "system", "content": system_prompt})
        else:
            messages.append({"role": "system", "content": 
                "你是一个知识渊博的AI助手「小搜」。回答简洁有条理。"
                "如果问题涉及实时信息，诚实地说「我的知识截止XX，建议联网搜索」。",
            })
        
        # 加入最近对话（最多3轮）
        for msg in self.history[-6:]:
            messages.append({"role": "user" if msg.startswith("用户:") else "assistant",
                            "content": msg[3:] if ":" in msg else msg})
        
        messages.append({"role": "user", "content": query})
        
        # 生成回答
        response = call_llm(messages)
        answer = response.get("content", "[生成失败]")
        
        # 保存记忆
        self.history.append(f"用户: {query}")
        self.history.append(f"助手: {answer[:100]}...")
        
        # 如果有搜索来源，追加链接
        if search_results:
            answer += "\n\n📎 来源："
            for i, r in enumerate(search_results[:3], 1):
                answer += f"\n  [{i}] {r['url']}"
        
        return answer
    
    def get_stats(self):
        """查看统计"""
        return f"""
📊 使用统计
  搜索次数: {self.stats['searches']}
  知识库命中: {self.stats['kb_hits']}
  对话轮次: {len(self.history) // 2}"""
    
    def get_history(self):
        """查看对话历史"""
        if not self.history:
            return "暂无对话历史"
        lines = ["📋 最近对话："]
        for i, msg in enumerate(self.history[-8:]):
            prefix = "🧑" if "用户:" in msg else "🤖"
            content = msg[3:] if ":" in msg else msg
            lines.append(f"  {prefix} {content[:60]}...")
        return "\n".join(lines)


# ═══════════════════════════════════════════════
# 主程序
# ═══════════════════════════════════════════════

def main():
    if not API_KEY:
        print("❌ 未找到 API Key")
        return
    
    assistant = SearchAssistant()
    
    print("\n💡 试试这些：")
    print("  「什么是 Transformer？」")
    print("  「最近AI领域有什么新闻？」")
    print("  「Python 和 Java 的区别」")
    print("  「/search:force 今天的天气」 强制搜索")
    print("  「/search:off Python list」   关闭搜索")
    
    while True:
        try:
            user_input = input("\n🧑 你: ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\n👋 再见！")
            break
        
        if not user_input:
            continue
        
        if user_input.lower() in ("quit", "exit", "q"):
            print("👋 再见！")
            break
        
        # 特殊指令
        if user_input == "/stats":
            print(assistant.get_stats())
            continue
        if user_input == "/history":
            print(assistant.get_history())
            continue
        if user_input.startswith("/search:force "):
            q = user_input[14:]
            response = assistant.chat(q, force_search=True)
            print(f"\n🤖 小搜: {response}")
            continue
        if user_input.startswith("/search:off "):
            q = user_input[12:]
            response = assistant.chat(q, force_search=False)
            print(f"\n🤖 小搜: {response}")
            continue
        
        response = assistant.chat(user_input)
        print(f"\n🤖 小搜: {response}")


if __name__ == "__main__":
    main()
