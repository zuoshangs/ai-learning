# 第12天：项目实战 — 构建完整 AI 客服系统 🚀

## 12.1 项目概述

经过前几天的学习，我们已经掌握了构建 AI 应用的五个核心能力：

| 天数 | 核心能力 | 关键技术 |
|------|----------|----------|
| 第8天 | 📖 RAG 知识库 | 向量检索、文档问答 |
| 第9天 | 🔧 工具调用 | Function Calling、多工具编排 |
| 第10天 | 📋 结构化输出 | JSON Mode、Pydantic |
| 第11天 | 💬 多轮对话 | 记忆策略、状态管理 |

**第12天目标：** 将这些能力整合到一个**完整的 AI 客服系统**中。

### 12.1.1 业务场景

某电商平台需要开发一个 AI 客服助手，支持以下功能：

1. **商品咨询** — 用户询问商品信息、价格、库存
2. **订单查询** — 根据订单号查询物流、状态
3. **退换货处理** — 了解政策、发起流程
4. **多轮对话** — 记住用户身份和历史，上下文连续
5. **智能摘要** — 对话结束后生成结构化摘要

### 12.1.2 系统架构

```
┌─────────────────────────────────────────────┐
│              用户输入 (User Input)            │
└──────────────────┬──────────────────────────┘
                   ▼
┌─────────────────────────────────────────────┐
│          对话管理器 (Conversation Manager)     │
│        • 滑动窗口记忆  • 客户状态追踪           │
└──────────────────┬──────────────────────────┘
                   ▼
┌─────────────────────────────────────────────┐
│           意图识别器 (Intent Classifier)       │
│         判断 → 需要查知识库还是调工具          │
└──────┬──────────────┬──────────────┬────────┘
       ▼              ▼              ▼
┌──────────┐  ┌────────────┐  ┌──────────────┐
│ RAG 知识库 │  │  工具管理器  │  │  状态更新器   │
│ (商品/政策)│  │(查单/算价)  │  │ (姓名/订单)   │
└──────┬────┘  └──────┬─────┘  └──────┬───────┘
       └──────────────┼───────────────┘
                      ▼
┌─────────────────────────────────────────────┐
│          响应生成器 (Response Generator)      │
│   • 综合上下文 + 检索结果 + 工具结果 + 状态    │
│   • 必要时输出结构化 JSON（订单摘要、报告）     │
└──────────────────┬──────────────────────────┘
                   ▼
┌─────────────────────────────────────────────┐
│              输出给用户 (Final Response)       │
└─────────────────────────────────────────────┘
```

---

## 12.2 模块一：知识库 (RAG)

这里我们直接使用第8天构建的 `SimpleVectorStore`，但扩展为支持多种知识类别：

### 12.2.1 数据准备

```python
# 知识库数据示例
PRODUCT_KB = [
    "iPhone 15 Pro 搭载 A17 Pro 芯片，起售价 7999 元，提供 128GB/256GB/512GB 版本",
    "MacBook Air M3 配备 13.6 英寸 Liquid Retina 显示屏，起售价 8999 元",
    "AirPods Pro 第二代支持主动降噪，售价 1899 元，续航 6 小时",
]

POLICY_KB = [
    "退货政策：自签收之日起 7 天内可无理由退货，需保持商品完好",
    "换货政策：自签收之日起 15 天内出现质量问题可免费换货",
    "物流政策：满 199 元包邮，普通快递 3-5 个工作日，顺丰加急次日达",
]

FAQ_KB = [
    "问：如何查询订单？答：在 APP 中点击「我的订单」或提供订单号",
    "问：支持哪些支付方式？答：微信支付、支付宝、银行卡、花呗",
    "问：发票怎么开？答：下单时可选择电子发票，下单后可在订单详情页补开",
]
```

### 12.2.2 多知识库检索

我们创建 `MultiKnowledgeBase` 类，支持按类别检索：

```python
class MultiKnowledgeBase:
    """多类别知识库"""
    
    def __init__(self):
        self.stores = {}  # category -> SimpleVectorStore
    
    def add_category(self, name, documents):
        store = SimpleVectorStore()
        for doc in documents:
            store.add_document(doc)
        self.stores[name] = store
    
    def query(self, question, top_k=2):
        """在所有知识库中检索"""
        results = []
        for category, store in self.stores.items():
            for doc, score in store.search(question, top_k):
                results.append((category, doc, score))
        results.sort(key=lambda x: -x[2])
        return results[:top_k]
```

---

## 12.3 模块二：工具管理器

工具调用是从第9天沿用的核心能力。我们为客服场景设计了以下工具：

### 12.3.1 工具定义

```python
TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "lookup_order",
            "description": "查询订单状态和物流信息",
            "parameters": {
                "type": "object",
                "properties": {
                    "order_id": {"type": "string", "description": "订单号"}
                },
                "required": ["order_id"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "calculate_shipping",
            "description": "计算运费",
            "parameters": {
                "type": "object",
                "properties": {
                    "amount": {"type": "number", "description": "商品金额"},
                    "express": {"type": "string", "enum": ["普通", "加急"]}
                },
                "required": ["amount"]
            }
        }
    }
]
```

### 12.3.2 工具执行器

```python
class ToolExecutor:
    def execute(self, tool_name, args):
        handlers = {
            "lookup_order": self._lookup_order,
            "calculate_shipping": self._calc_shipping,
        }
        func = handlers.get(tool_name)
        if func:
            return func(**args)
        return {"error": f"未知工具: {tool_name}"}
    
    def _lookup_order(self, order_id):
        # 模拟订单查询
        orders = {
            "ORD-2024-001": {"status": "已发货", "logistics": "顺丰快递 SF1234567890"},
            "ORD-2024-002": {"status": "待发货", "logistics": "预计明天发货"},
        }
        return orders.get(order_id, {"status": "未找到该订单"})
```

---

## 12.4 模块三：状态与记忆管理

沿用第11天的状态管理器，但扩展更多字段：

```python
@dataclass
class CustomerState:
    user_name: str = ""
    user_id: str = ""
    order_id: str = ""
    intent: str = ""         # 当前意图：query_order / return_product / ask_product / other
    conversation_rounds: int = 0
    resolved: bool = False
    context: Dict[str, Any] = field(default_factory=dict)
    
    def to_system_prompt(self) -> str:
        return f"""当前客户信息：
- 姓名：{self.user_name or '未知'}
- 当前意图：{self.intent or '未知'}
- 关联订单：{self.order_id or '无'}
- 已对话 {self.conversation_rounds} 轮
- 是否已解决：{'是' if self.resolved else '否'}"""
```

---

## 12.5 模块四：结构化输出

用于生成订单摘要和对话总结。沿用第10天的 JSON Mode 技术：

```python
def generate_order_summary(order_data, customer_name):
    """生成结构化订单摘要"""
    prompt = f"""请根据以下订单信息生成结构化摘要：
    
客户姓名：{customer_name}
订单号：{order_data.get('order_id')}
订单状态：{order_data.get('status')}
物流信息：{order_data.get('logistics')}

请按以下 JSON 格式输出：
{{"summary": "一句话总结",
 "order_status": "状态",
 "logistics": "物流信息",
 "recommendation": "建议",}}"""
    
    response = call_llm([
        {"role": "system", "content": "你是一个订单处理助手。"},
        {"role": "user", "content": prompt}
    ], response_format={"type": "json_object"})
    return json.loads(response)
```

---

## 12.6 模块五：系统集成 (Agent)

这是核心——把所有模块串起来的调度器：

```python
class CustomerServiceAgent:
    def __init__(self):
        self.kb = MultiKnowledgeBase()
        self.tools = ToolExecutor()
        self.state = CustomerState()
        self.memory = SlidingWindowMemory(SYSTEM_PROMPT)
        
    def chat(self, user_input):
        # 1. 更新记忆
        self.memory.add("user", user_input)
        self.state.conversation_rounds += 1
        
        # 2. 更新客户状态（结构化提取）
        self._extract_state(user_input)
        
        # 3. 判断是否需要工具调用或知识库
        intent = self._classify_intent(user_input)
        tool_calls = None
        kb_results = None
        
        if intent in ("query_order", "calculate"):
            tool_calls = self._execute_tools(user_input, intent)
        elif intent in ("ask_product", "ask_policy"):
            kb_results = self.kb.query(user_input)
        
        # 4. 生成回复
        response = self._generate_response(
            user_input, intent, tool_calls, kb_results
        )
        
        self.memory.add("assistant", response)
        return response
```

### 12.6.1 对话流程

```
用户: "你好，我叫小明"
  → 状态更新: user_name="小明"
  → 意图: greeting
  → 回复: "你好小明！有什么可以帮你的？"

用户: "我想查一下 ORD-2024-001 的物流"
  → 状态更新: order_id="ORD-2024-001", intent="query_order"
  → 工具调用: lookup_order("ORD-2024-001")
  → 结果: {status: "已发货", logistics: "顺丰快递 SF1234567890"}
  → 回复: "您的订单 ORD-2024-001 已通过顺丰发出..."

用户: "iPhone 15 Pro 多少钱？"
  → RAG 检索: "iPhone 15 Pro 搭载 A17 Pro 芯片，起售价 7999 元"
  → 回复: "iPhone 15 Pro 起售价 7999 元..."

用户: "帮我总结一下今天的对话"
  → 结构化输出: 生成对话摘要 JSON
  → 回复: 显示完整摘要
```

---

## 12.7 完整代码

完整实现见 `code/day12/ai_cs_agent.py`（Python）和 `code/day12/AiCustomerServiceAgent.java`（Java）。

### 12.7.1 运行方式

```bash
cd week1/code/day12
python3 ai_cs_agent.py
```

系统启动后呈现交互式菜单，支持以下指令：

| 指令 | 功能 |
|------|------|
| 对话 | 输入任意内容开始咨询 |
| `/tools` | 查看可用工具列表 |
| `/state` | 查看当前客户状态 |
| `/summary` | 生成对话摘要 |
| `/save` | 保存会话 |
| `/load` | 恢复会话 |
| `quit` | 退出 |

---

## 12.8 思考题

1. **如果同时有多个工具需要调用（比如查订单 + 算运费），如何编排调用顺序？**
2. **RAG 检索的结果和工具调用的结果冲突时，以哪个为准？**
3. **如何防止用户利用客服系统做恶意查询（如重复查询大量订单）？**
4. **如果 LLM 调用超时了，系统应该如何优雅降级？**

---

## 12.9 金句

> **RAG 给模型知识，Function Calling 给模型能力，结构化输出给模型纪律，多轮对话给模型记忆——四者合一，才是完整的 Agent。**

> **好的架构不是把所有代码写在一起，而是让每个模块各司其职，只通过明确的接口通信。**

> **从"调用 API"到"构建 Agent"，差距不在于技术，而在于系统思维。**

---

*第12天完成！你已经学会了如何构建一个完整的 AI 客服系统。明天进入 Week2，学习更高级的主题！*
