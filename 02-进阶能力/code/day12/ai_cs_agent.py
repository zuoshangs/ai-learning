"""
ai_cs_agent.py — 完整 AI 客服系统

整合 RAG + Function Calling + 结构化输出 + 多轮对话
第12天项目实战
"""

import json
import os
import re
import requests
import math
from datetime import datetime
from dataclasses import dataclass, field, asdict
from typing import Optional


# ═══════════════════════════════════════════════
# API 配置
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


def call_llm(messages, temperature=0.3, response_format=None, tools=None, max_tokens=1024):
    """通用 LLM 调用，支持 response_format 和 tools"""
    if not API_KEY:
        return "[API Key 未配置]"
    
    payload = {
        "model": "deepseek-chat",
        "messages": messages,
        "temperature": temperature,
        "max_tokens": max_tokens,
    }
    if response_format:
        payload["response_format"] = response_format
    if tools:
        payload["tools"] = tools
        payload["tool_choice"] = "auto"
    
    resp = requests.post(API_URL, headers={"Authorization": f"Bearer {API_KEY}"},
                         json=payload, timeout=30)
    data = resp.json()
    return data["choices"][0]["message"]


# ═══════════════════════════════════════════════
# 模块一：简易向量存储（RAG 知识库）
# ═══════════════════════════════════════════════

def cosine_similarity(a, b):
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(x * x for x in b))
    return dot / (na * nb) if na > 0 and nb > 0 else 0


class SimpleVectorStore:
    """轻量级向量存储——基于 TF-IDF 风格的简单特征"""
    
    def __init__(self):
        self.documents = []
        self.vectors = []
    
    def _tokenize(self, text):
        """简单分词（中英文兼容）"""
        tokens = re.findall(r'[\w\u4e00-\u9fff]+', text.lower())
        return tokens
    
    def _vectorize(self, text):
        """基于词袋模型的简单特征向量"""
        tokens = self._tokenize(text)
        all_terms = set()
        for doc in self.documents:
            all_terms.update(self._tokenize(doc))
        all_terms.update(tokens)
        
        # 构建词频向量
        term_list = sorted(all_terms)
        vec = [0.0] * len(term_list)
        for t in tokens:
            if t in term_list:
                vec[term_list.index(t)] += 1.0
        return vec
    
    def add_document(self, text):
        self.documents.append(text)
        vec = self._vectorize(text)
        self.vectors.append(vec)
    
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
        """在所有知识库中检索"""
        results = []
        for category, store in self.stores.items():
            for doc, score in store.search(question, top_k):
                results.append((category, doc, score))
        results.sort(key=lambda x: -x[2])
        return results[:top_k]


# ═══════════════════════════════════════════════
# 模块二：工具管理器
# ═══════════════════════════════════════════════

ORDER_DB = {
    "ORD-2024-001": {
        "customer": "张三",
        "product": "iPhone 15 Pro 256GB",
        "amount": 8999.00,
        "status": "已发货",
        "logistics": "顺丰快递 SF1234567890",
        "estimated_delivery": "2024-03-20",
    },
    "ORD-2024-002": {
        "customer": "李四",
        "product": "MacBook Air M3",
        "amount": 8999.00,
        "status": "待发货",
        "logistics": "预计明天发货",
        "estimated_delivery": "2024-03-22",
    },
    "ORD-2024-003": {
        "customer": "王五",
        "product": "AirPods Pro 第二代",
        "amount": 1899.00,
        "status": "已签收",
        "logistics": "圆通快递 YT9876543210",
        "estimated_delivery": "2024-03-15",
    },
}

PRODUCT_CATALOG = {
    "iphone 15 pro": {"name": "iPhone 15 Pro", "price": 7999, "stock": "充足"},
    "iphone 15": {"name": "iPhone 15", "price": 5999, "stock": "充足"},
    "macbook air m3": {"name": "MacBook Air M3", "price": 8999, "stock": "现货"},
    "macbook pro": {"name": "MacBook Pro 14英寸", "price": 12999, "stock": "需预订"},
    "airpods pro": {"name": "AirPods Pro 第二代", "price": 1899, "stock": "充足"},
    "ipad air": {"name": "iPad Air M2", "price": 4799, "stock": "现货"},
}


TOOLS_FOR_LLM = [
    {
        "type": "function",
        "function": {
            "name": "lookup_order",
            "description": "查询订单状态和物流信息",
            "parameters": {
                "type": "object",
                "properties": {
                    "order_id": {"type": "string", "description": "订单号，如 ORD-2024-001"}
                },
                "required": ["order_id"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "query_product",
            "description": "查询商品信息和价格",
            "parameters": {
                "type": "object",
                "properties": {
                    "product_name": {"type": "string", "description": "商品名称，如 iPhone 15 Pro"}
                },
                "required": ["product_name"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "calculate_shipping",
            "description": "计算运费，满199包邮",
            "parameters": {
                "type": "object",
                "properties": {
                    "amount": {"type": "number", "description": "商品总金额"},
                    "express": {"type": "string", "enum": ["普通", "加急"], "description": "快递类型"}
                },
                "required": ["amount"]
            }
        }
    },
]


class ToolExecutor:
    """工具执行器"""
    
    def execute(self, tool_name, args):
        handlers = {
            "lookup_order": self._lookup_order,
            "query_product": self._query_product,
            "calculate_shipping": self._calc_shipping,
        }
        func = handlers.get(tool_name)
        if func:
            return func(**args)
        return {"error": f"未知工具: {tool_name}"}
    
    def _lookup_order(self, order_id):
        order = ORDER_DB.get(order_id)
        if order:
            return {
                "found": True,
                "order_id": order_id,
                **order
            }
        return {"found": False, "order_id": order_id, "message": "未找到该订单"}
    
    def _query_product(self, product_name):
        key = product_name.lower().strip()
        # 模糊匹配
        for pk, info in PRODUCT_CATALOG.items():
            if pk in key or key in pk:
                return {"found": True, **info}
        return {"found": False, "query": product_name, "message": "未找到该商品"}
    
    def _calc_shipping(self, amount, express="普通"):
        if amount >= 199:
            return {"fee": 0, "message": "满199包邮，免运费"}
        base = 10 if express == "普通" else 25
        return {"fee": base, "express": express, "message": f"运费 {base} 元"}

    def describe_tools(self):
        """返回工具描述，供客户端显示"""
        lines = ["📌 可用工具："]
        for t in TOOLS_FOR_LLM:
            fn = t["function"]
            params = list(fn["parameters"]["properties"].keys())
            lines.append(f"  🔧 {fn['name']}: {fn['description']} (参数: {', '.join(params)})")
        return "\n".join(lines)


# ═══════════════════════════════════════════════
# 模块三：状态与记忆管理
# ═══════════════════════════════════════════════

@dataclass
class CustomerState:
    """客户状态"""
    user_name: str = ""
    user_id: str = ""
    order_id: str = ""
    intent: str = ""
    conversation_rounds: int = 0
    resolved: bool = False
    context: dict = field(default_factory=dict)
    
    def to_prompt(self) -> str:
        return f"""当前客户信息：
- 姓名：{self.user_name or '未知'}
- 当前意图：{self.intent or '未知'}
- 关联订单：{self.order_id or '无'}
- 已对话 {self.conversation_rounds} 轮
- 是否已解决：{'是' if self.resolved else '否'}"""
    
    def to_dict(self):
        return asdict(self)
    
    @classmethod
    def from_dict(cls, data):
        return cls(**{k: data.get(k, v.default if hasattr(v, 'default') else v)
                      for k, v in cls.__dataclass_fields__.items()})


class SlidingWindowMemory:
    """滑动窗口记忆"""
    
    def __init__(self, system_prompt: str, window_size: int = 6):
        self.system_prompt = {"role": "system", "content": system_prompt}
        self.history = []
        self.window_size = window_size
    
    def add(self, role: str, content: str):
        self.history.append({"role": role, "content": content})
    
    def get_context(self, state_prompt: str = "") -> list:
        ctx = [self.system_prompt]
        if state_prompt:
            ctx.append({"role": "system", "content": state_prompt})
        recent = self.history[-(self.window_size * 2):]
        ctx.extend(recent)
        return ctx


# ═══════════════════════════════════════════════
# 模块四：客服系统 Agent
# ═══════════════════════════════════════════════

SYSTEM_PROMPT = """你是一个专业友好的电商客服助手「小智」。你的职责：

1. 回答商品咨询、查询订单、处理退换货
2. 回答简洁有礼貌，语气亲切自然
3. 需要查信息时，使用提供的工具
4. 用户首次对话时，主动询问名字
5. 始终基于对话历史和客户状态回应用户

核心原则：
- 不知道的信息不要编造，诚实地说不清楚
- 涉及用户隐私的不要问（如密码、身份证号）
- 需要多次调用工具时，先告诉用户你在做什么"""


class CustomerServiceAgent:
    """完整 AI 客服系统——整合 RAG + 工具 + 结构化输出 + 多轮对话"""
    
    def __init__(self):
        # RAG 知识库
        self.kb = MultiKnowledgeBase()
        self._init_knowledge_base()
        
        # 工具
        self.tools = ToolExecutor()
        
        # 状态与记忆
        self.state = CustomerState()
        self.memory = SlidingWindowMemory(SYSTEM_PROMPT, window_size=6)
        
        print("=" * 55)
        print("  🛒 智能客服「小智」已就绪")
        print("=" * 55)
    
    def _init_knowledge_base(self):
        """初始化知识库"""
        PRODUCT_KB = [
            "iPhone 15 Pro 搭载 A17 Pro 芯片，起售价 7999 元，提供 128GB/256GB/512GB 三种存储版本",
            "iPhone 15 搭载 A16 芯片，起售价 5999 元，提供 128GB/256GB 版本，有粉色/黄色/绿色/蓝色/黑色可选",
            "MacBook Air M3 配备 13.6 英寸 Liquid Retina 显示屏，起售价 8999 元，续航长达 18 小时",
            "MacBook Pro 14英寸搭载 M3 Pro/Max 芯片，起售价 12999 元，适合专业视频剪辑和编程",
            "AirPods Pro 第二代支持主动降噪和自适应通透模式，售价 1899 元，续航 6 小时",
            "iPad Air M2 配备 11 英寸 Liquid Retina 显示屏，起售价 4799 元，支持 Apple Pencil Pro",
        ]
        self.kb.add_category("商品信息", PRODUCT_KB)
        
        POLICY_KB = [
            "退货政策：自签收之日起 7 天内可无理由退货，需保持商品完好、配件齐全",
            "换货政策：自签收之日起 15 天内出现质量问题可免费换货，非质量问题需协商",
            "物流政策：满 199 元包邮，普通快递 3-5 个工作日，顺丰加急次日达，加急费 15 元",
            "保修政策：所有商品享受一年官方保修，Apple 产品可额外购买 AppleCare+",
        ]
        self.kb.add_category("服务政策", POLICY_KB)
        
        FAQ_KB = [
            "问：如何查询订单？答：提供订单号可以帮你查询物流和状态",
            "问：支持哪些支付方式？答：微信支付、支付宝、银行卡、花呗分期",
            "问：发票怎么开？答：下单时可选择电子发票，下单后在订单详情页可补开",
            "问：可以改地址吗？答：未发货的订单可以在 APP 中修改地址，已发货的请联系客服",
        ]
        self.kb.add_category("常见问题", FAQ_KB)
    
    def _structure_state_extraction(self, text: str) -> dict:
        """使用 LLM 从用户输入中提取结构化状态"""
        try:
            msg = call_llm([
                {"role": "system", "content": "从对话中提取客户信息，只返回JSON。"},
                {"role": "user", "content": f"从以下文本提取: user_name(客户姓名), order_id(订单号), intent(意图)。意图分类: query_order(查订单)/return_product(退货)/exchange_product(换货)/ask_product(问商品)/greeting(打招呼)/other(其他)。\n\n文本: {text}"}
            ], temperature=0, response_format={"type": "json_object"}, max_tokens=200)
            return json.loads(msg.get("content", "{}"))
        except Exception:
            return {}
    
    def _update_state(self, user_input: str):
        """从输入中更新客户状态"""
        extracted = self._structure_state_extraction(user_input)
        
        if extracted.get("user_name"):
            self.state.user_name = extracted["user_name"]
        if extracted.get("order_id"):
            self.state.order_id = extracted["order_id"]
        if extracted.get("intent"):
            self.state.intent = extracted["intent"]
        
        # 额外的关键词提取
        if not extracted.get("order_id"):
            match = re.search(r'ORD[-_][\w-]+|ORD\d+', user_input, re.I)
            if match:
                self.state.order_id = match.group()
        
        self.state.conversation_rounds += 1
    
    def _get_rag_context(self, question: str) -> str:
        """从知识库检索相关信息"""
        results = self.kb.query(question, top_k=3)
        if not results:
            return ""
        
        parts = []
        for category, doc, score in results:
            if score > 0.1:  # 过滤低分
                parts.append(f"[{category}] {doc}")
        
        if parts:
            return "参考资料：\n" + "\n".join(parts)
        return ""
    
    def _process_tool_call(self, user_input: str) -> list:
        """判断并执行工具调用"""
        # 让 LLM 决定是否要调用工具
        msg = call_llm([
            {"role": "system", "content": "判断用户意图，如果需要工具就调用。"},
            {"role": "user", "content": f"用户说: {user_input}"}
        ], tools=TOOLS_FOR_LLM, max_tokens=512)
        
        results = []
        if "tool_calls" in msg:
            for tc in msg["tool_calls"]:
                fn_name = tc["function"]["name"]
                try:
                    args = json.loads(tc["function"]["arguments"])
                except json.JSONDecodeError:
                    args = {}
                
                result = self.tools.execute(fn_name, args)
                results.append({
                    "tool": fn_name,
                    "args": args,
                    "result": result
                })
                print(f"  🔧 调用工具: {fn_name}({json.dumps(args, ensure_ascii=False)})")
        return results
    
    def chat(self, user_input: str) -> str:
        """处理用户消息"""
        
        # 1. 更新状态
        self._update_state(user_input)
        
        # 2. RAG 检索
        rag_context = self._get_rag_context(user_input)
        
        # 3. 工具调用
        tool_results = self._process_tool_call(user_input)
        
        # 4. 构造回复
        messages = self.memory.get_context(self.state.to_prompt())
        
        # 注入 RAG 结果
        if rag_context:
            messages.append({"role": "system", "content": rag_context})
        
        # 注入工具结果
        if tool_results:
            for tr in tool_results:
                messages.append({
                    "role": "tool",
                    "tool_call_id": tr["tool"],
                    "content": json.dumps(tr["result"], ensure_ascii=False)
                })
        
        # 5. 添加用户消息（如果还没在记忆里）
        messages.append({"role": "user", "content": user_input})
        
        # 6. 生成回复
        msg = call_llm(messages, tools=TOOLS_FOR_LLM)
        response = msg.get("content", "")
        
        # 7. 如果 LLM 又想调用工具（第二轮调用）
        if "tool_calls" in msg:
            for tc in msg["tool_calls"]:
                fn_name = tc["function"]["name"]
                try:
                    args = json.loads(tc["function"]["arguments"])
                except json.JSONDecodeError:
                    args = {}
                result = self.tools.execute(fn_name, args)
                tool_results.append({
                    "tool": fn_name,
                    "args": args,
                    "result": result
                })
                print(f"  🔧 第二轮调用: {fn_name}()")
            
            # 带工具结果重新请求
            messages.append(msg)
            for tr in tool_results:
                messages.append({
                    "role": "tool",
                    "tool_call_id": id(tr),
                    "content": json.dumps(tr["result"], ensure_ascii=False)
                })
            msg = call_llm(messages)
            response = msg.get("content", "")
        
        if not response:
            response = "抱歉，我暂时无法回答这个问题，请稍后再试。"
        
        # 8. 保存到记忆
        self.memory.add("user", user_input)
        self.memory.add("assistant", response)
        
        return response
    
    def generate_summary(self) -> str:
        """生成对话结构化摘要"""
        messages = self.memory.get_context()
        messages.append({
            "role": "user",
            "content": "请总结本次对话，按 JSON 格式输出包含：customer_name(客户姓名), topics(讨论主题列表), order_info(订单信息), resolution(处理结果), suggestions(建议)"
        })
        
        msg = call_llm(messages, temperature=0,
                       response_format={"type": "json_object"},
                       max_tokens=512)
        try:
            summary = json.loads(msg.get("content", "{}"))
            output = [
                "📋 对话摘要",
                "-" * 30,
                f"客户：{summary.get('customer_name', '未知')}",
                f"主题：{' · '.join(summary.get('topics', ['-']))}",
                f"订单：{summary.get('order_info', '无')}",
                f"处理结果：{summary.get('resolution', '进行中')}",
                f"建议：{summary.get('suggestions', '无')}",
            ]
            return "\n".join(output)
        except Exception:
            return msg.get("content", "生成摘要失败")
    
    def save_session(self, path="session_cs_agent.json"):
        data = {
            "state": self.state.to_dict(),
            "history": self.memory.history,
            "saved_at": datetime.now().isoformat(),
        }
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        print(f"  💾 会话已保存到 {path}")
    
    def load_session(self, path="session_cs_agent.json"):
        if not os.path.exists(path):
            print("  ⚠️  没有找到保存的会话")
            return
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
        self.state = CustomerState.from_dict(data["state"])
        self.memory.history = data.get("history", [])
        print(f"  📂 会话已恢复（{len(self.memory.history)} 条消息）")
    
    def get_status(self) -> str:
        """当前状态概览"""
        lines = [
            "📊 当前状态",
            "-" * 30,
            f"  客户: {self.state.user_name or '未知'}",
            f"  意图: {self.state.intent or '未知'}",
            f"  订单: {self.state.order_id or '无'}",
            f"  对话轮次: {self.state.conversation_rounds}",
            f"  解决状态: {'✅ 已解决' if self.state.resolved else '⏳ 进行中'}",
        ]
        return "\n".join(lines)


# ═══════════════════════════════════════════════
# 主程序
# ═══════════════════════════════════════════════

def main():
    if not API_KEY:
        print("❌ 未找到 API Key，请设置 DEEPSEEK_API_KEY 环境变量")
        return
    
    agent = CustomerServiceAgent()
    
    print("\n💡 试试这些场景：")
    print("  「你好，我叫小明」")
    print("  「iPhone 15 Pro 多少钱？」")
    print("  「帮我查一下 ORD-2024-001」")
    print("  「满多少包邮？」")
    print("  「算一下 8999 加急运费」")
    print("\n📌 特殊指令：/tools  /state  /summary  /save  /load  quit")
    
    while True:
        try:
            user_input = input("\n🧑 你: ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\n👋 再见！")
            break
        
        if not user_input:
            continue
        
        if user_input.lower() in ("quit", "exit", "q"):
            print("👋 感谢咨询，再见！")
            break
        
        # 特殊指令
        if user_input == "/tools":
            print(agent.tools.describe_tools())
            continue
        if user_input == "/state":
            print(agent.get_status())
            continue
        if user_input == "/summary":
            print(agent.generate_summary())
            continue
        if user_input == "/save":
            agent.save_session()
            continue
        if user_input == "/load":
            agent.load_session()
            continue
        
        # 正常对话
        response = agent.chat(user_input)
        print(f"\n🤖 小智: {response}")


if __name__ == "__main__":
    main()
