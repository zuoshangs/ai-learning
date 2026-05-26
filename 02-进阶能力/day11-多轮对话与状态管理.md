# 第11天：多轮对话与状态管理 💬

> **学习目标：** 理解多轮对话的核心机制，掌握三种记忆管理策略（滑动窗口、摘要、向量记忆），
>   构建一个"对话式"智能客服——能记住上下文，能处理复杂多轮交互
> **预计时间：** 2.5小时
> **代码语言：** Python + Java 双版本
> **前置知识：** 第4天（API 调用）、第8天（RAG 基础）

---

## 📋 目录

1. [为什么需要多轮对话？](#1-为什么需要多轮对话)
2. [对话状态与记忆模型](#2-对话状态与记忆模型)
3. [策略一：滑动窗口](#3-策略一滑动窗口)
4. [策略二：摘要记忆](#4-策略二摘要记忆)
5. [策略三：向量记忆（RAG 记忆）](#5-策略三向量记忆rag-记忆)
6. [实战：智能客服系统](#6-实战智能客服系统)
7. [课堂练习](#7-课堂练习)
8. [今日小结](#8-今日小结)

---

## 1. 为什么需要多轮对话？

### 大模型天生"健忘"

API 调用本身是**无状态**的——每次请求模型都不记得之前说过什么。

```python
# 第1轮
response = llm("我叫张三")
# → "你好张三！很高兴认识你。"

# 第2轮（如果只传当前问题）
response = llm("我叫什么名字？")  
# → "抱歉，我不知道你的名字。"  ← 忘了！
```

### 解决方案：传递历史消息

```python
# 第2轮（传入历史）
response = llm([
    {"role": "user", "content": "我叫张三"},
    {"role": "assistant", "content": "你好张三！很高兴认识你。"},
    {"role": "user", "content": "我叫什么名字？"}
])
# → "你叫张三！"  ← 记住了
```

### 多轮对话的核心挑战

| 挑战 | 说明 | 解决方案 |
|------|------|---------|
| **上下文过长** | 聊久了 messages 数组膨胀，超 token 限制 | 滑动窗口 / 摘要 |
| **信息遗忘** | 太久远的信息被窗口切掉 | 向量记忆检索 |
| **成本控制** | 每次请求都传历史，Token 消耗大 | 摘要压缩 |
| **状态维护** | 需要记住用户偏好、订单号等会话级数据 | 状态管理 |

---

## 2. 对话状态与记忆模型

### 消息结构

```python
messages = [
    # ── 系统设定（始终保留） ──
    {"role": "system", "content": "你是一个智能客服，耐心解答用户问题。"},
    
    # ── 对话历史（动态变化） ──
    {"role": "user",      "content": "你好，我想退货"},
    {"role": "assistant",  "content": "好的，请问您的订单号是？"},
    {"role": "user",      "content": "ORD-2024-1234"},
    {"role": "assistant",  "content": "已查到您的订单，请问退货原因是？"},
    {"role": "user",      "content": "尺寸不合适"},
    
    # ── 当前问题 ──
    {"role": "user",      "content": "可以换货吗？"}
]
```

### 三种记忆策略对比

```
策略            token开销    记忆容量    实现复杂度
─────────────────────────────────────────────
滑动窗口        低           近 N 轮     ⭐ 简单
摘要记忆        中           全部        ⭐⭐ 中等
向量记忆        低           全部        ⭐⭐⭐ 复杂
```

### 记忆管理器架构

```python
class ConversationManager:
    """对话管理器：统一管理消息历史"""
    
    def __init__(self, system_prompt: str, strategy: str = "window"):
        self.system_prompt = {"role": "system", "content": system_prompt}
        self.history = []       # 所有消息
        self.strategy = strategy  # "window" | "summary" | "vector"
        
        # 配置
        self.window_size = 10       # 滑动窗口保留的轮数
        self.max_tokens = 4000      # 摘要触发阈值
        self.summary = ""           # 累积摘要
    
    def add_message(self, role: str, content: str):
        """添加一条消息"""
        self.history.append({"role": role, "content": content})
    
    def get_context(self) -> list:
        """根据策略获取当前上下文"""
        if self.strategy == "window":
            return self._window_context()
        elif self.strategy == "summary":
            return self._summary_context()
        elif self.strategy == "vector":
            return self._vector_context()
    
    def _window_context(self) -> list:
        """滑动窗口：只保留最近 N 轮"""
        recent = self.history[-self.window_size * 2:]  # 每轮2条消息
        return [self.system_prompt] + recent
    
    def _summary_context(self) -> list:
        """摘要记忆：压缩历史 + 最近细节"""
        if len(self.history) > self.window_size * 2:
            return [
                self.system_prompt,
                {"role": "system", "content": f"对话历史摘要：{self.summary}"}
            ] + self.history[-self.window_size * 2:]
        return [self.system_prompt] + self.history
    
    def _vector_context(self) -> list:
        """向量记忆：RAG 检索相关历史"""
        # 结合最近的对话 + 检索到的相关历史
        recent = self.history[-4:]  # 最近2轮
        # relevant = vector_store.search(current_question)
        return [self.system_prompt] + recent  # + relevant
```

---

## 3. 策略一：滑动窗口

最简单、最常用的策略：**只保留最近 N 轮对话**。

### 实现

```python
class SlidingWindowMemory:
    """滑动窗口记忆——只保留最近 N 轮对话"""
    
    def __init__(self, system_prompt: str, window_size: int = 5):
        self.system_prompt = {"role": "system", "content": system_prompt}
        self.messages = []
        self.window_size = window_size  # 保留的对话轮数
    
    def add(self, role: str, content: str):
        self.messages.append({"role": role, "content": content})
    
    def get_context(self) -> list:
        """获取当前上下文（system + 最近 N 轮）"""
        # 只保留最近 window_size 轮（每轮 = user + assistant）
        recent = self.messages[-(self.window_size * 2):]
        return [self.system_prompt] + recent
    
    def total_tokens(self, encoder=None) -> int:
        """估算当前上下文 Token 数"""
        total = 0
        for msg in [self.system_prompt] + self.messages:
            total += len(msg["content"]) * 1.5  # 中文约 1 char ≈ 2 tokens
        return int(total)
```

### 工作示意图

```
时间线 →
──────────────────────────────────────────────────
第1轮: user 你好            → messages[0]
       assistant 你好请问?  → messages[1]
第2轮: user 我想退货         → messages[2]
       assistant 订单号?     → messages[3]
第3轮: user ORD-1234        → messages[4]  ← window_size=3
       assistant 已查到      → messages[5]  ← 保留这6条
第4轮: user 尺寸不合适       → messages[6]
       assistant 可以换货    → messages[7]
──────────────────────────────────────────────────

get_context() → [system, m[2], m[3], m[4], m[5], m[6], m[7]]
                ↑ 窗口之外的 history[0,1] 被丢弃了
```

### 优缺点

| 优点 | 缺点 |
|------|------|
| ✅ 实现简单 | ❌ 久远信息被丢弃 |
| ✅ Token 开销可控 | ❌ 用户重复问题时无法回溯 |
| ✅ 99% 场景够用 | ❌ 不适合长对话 |

---

## 4. 策略二：摘要记忆

当对话很长时，把早期的内容**压缩成摘要**，保留在上下文中。

### 实现

```python
class SummaryMemory:
    """摘要记忆——把旧对话压缩成摘要，保留最近细节"""
    
    def __init__(self, system_prompt: str, api_func, 
                 window_size: int = 3, summary_interval: int = 6):
        self.system_prompt = {"role": "system", "content": system_prompt}
        self.messages = []
        self.summary = "对话尚未开始。"
        self.window_size = window_size
        self.summary_interval = summary_interval  # 每 N 轮摘要一次
        self.api = api_func  # 用于生成摘要的 LLM 函数
    
    def add(self, role: str, content: str):
        self.messages.append({"role": role, "content": content})
        self._maybe_summarize()
    
    def _maybe_summarize(self):
        """当消息数到达阈值时，触发摘要"""
        if len(self.messages) < self.summary_interval * 2:
            return
        
        # 只对"旧"消息做摘要
        old_msgs = self.messages[:-(self.summary_interval * 2)]
        if not old_msgs:
            return
        
        text = "\n".join(f"[{m['role']}] {m['content']}" for m in old_msgs)
        
        # 调用 LLM 生成摘要
        summary_prompt = f"""请总结以下对话的核心信息（保持关键事实，如用户姓名、订单号、偏好等）：

{text}

简洁摘要："""
        
        new_summary = self.api(summary_prompt)
        self.summary = new_summary
        
        # 丢弃已摘要的旧消息
        self.messages = self.messages[-(self.summary_interval * 2):]
    
    def get_context(self) -> list:
        return [
            self.system_prompt,
            {"role": "system", "content": f"对话历史摘要：{self.summary}"}
        ] + self.messages
```

### 工作示意图

```
第1-6轮: 用户咨询订单问题
          ↓
          LLM 生成摘要 → "用户张三，订单ORD-1234，要求退货"
          ↓
          丢弃第1-6轮原始消息
          ↓
第7-12轮: 用户咨询换货流程
          ↓
          LLM 生成摘要 → "用户张三，订单ORD-1234，已退货，现在询问换货政策"
          ↓
          丢弃第7-12轮原始消息

当前上下文：
[system] 你是一个客服...
[system] 对话历史摘要：用户张三，订单ORD-1234，已退货，现在询问换货政策
[user] 尺寸不合适
[assistant] 可以换货，但需要...
```

### 优缺点

| 优点 | 缺点 |
|------|------|
| ✅ 保留全部对话上下文 | ❌ 每次摘要消耗额外 Token |
| ✅ 信息不丢失 | ❌ 摘要可能丢失细节 |
| ✅ 适合长对话 | ❌ 实现稍复杂 |

---

## 5. 策略三：向量记忆（RAG 记忆）

把对话历史存入向量数据库，根据当前问题检索相关的历史片段。

### 实现

```python
class VectorMemory:
    """向量记忆——用 RAG 检索相关历史"""
    
    def __init__(self, system_prompt: str, embed_func, window_size: int = 2):
        self.system_prompt = {"role": "system", "content": system_prompt}
        self.messages = []          # 完整历史
        self.window_size = window_size  # 始终保留的最近轮数
        self.embed = embed_func     # Embedding 函数
        self.vector_store = []      # [(vector, message_index), ...]
    
    def add(self, role: str, content: str):
        idx = len(self.messages)
        self.messages.append({"role": role, "content": content})
        
        # 对用户消息做向量化索引
        if role == "user":
            vec = self.embed(content)
            self.vector_store.append((vec, idx))
    
    def get_context(self, question: str = None) -> list:
        """获取上下文：最近 N 轮 + 检索到的相关历史"""
        context = [self.system_prompt]
        
        # 1. 检索相关历史
        if question and self.vector_store:
            q_vec = self.embed(question)
            scored = []
            from math import sqrt
            
            for vec, idx in self.vector_store:
                # 余弦相似度
                dot = sum(a * b for a, b in zip(q_vec, vec))
                n1 = sqrt(sum(a * a for a in q_vec))
                n2 = sqrt(sum(b * b for b in vec))
                score = dot / (n1 * n2) if n1 > 0 and n2 > 0 else 0
                scored.append((score, idx))
            
            scored.sort(reverse=True)
            
            # 取 Top-3 相关的历史消息及其回复
            retrieved_indices = set()
            for score, idx in scored[:3]:
                retrieved_indices.add(idx)
                if idx + 1 < len(self.messages):  # 也取 assistant 回复
                    retrieved_indices.add(idx + 1)
            
            # 按时间排序加入上下文
            retrieved = [self.messages[i] for i in sorted(retrieved_indices)
                        if i >= len(self.messages) - self.window_size * 2]  # 不重复
            context.append({
                "role": "system",
                "content": f"以下是与当前问题相关的历史对话："
            })
            context.extend(retrieved)
        
        # 2. 最近的对话
        recent = self.messages[-(self.window_size * 2):]
        context.extend(recent)
        
        return context
```

### 三种策略对比总结

```
                 滑动窗口             摘要记忆              向量记忆
                ──────────          ──────────           ──────────
  token开销      低（固定）          中（摘要+近轮）       低（检索+近轮）
  记忆力         最近 N 轮           全部（摘要形式）      全部（检索形式）
  实现难度       ⭐ 简单             ⭐⭐ 中等            ⭐⭐⭐ 复杂
  适用场景       短对话、简单任务     长对话、客服系统      超长对话、知识密集型
  信息丢失       抛弃旧信息           摘要可能遗漏细节      检索不到的不会出现
```

---

## 6. 实战：智能客服系统

### 系统架构

```
用户 ──→ 对话管理器 ──→ 记忆策略 ──→ LLM API ──→ 回复
              │                    │
              │              [滑动窗口 | 摘要 | 向量]
              │
        状态管理器
         (用户信息、订单、偏好)
```

### 完整代码

```python
"""
客服助手 — 多轮对话 + 状态管理 + 三种记忆策略
"""

import json
import os
import requests
from datetime import datetime

# ─── API 配置 ────────────────────────────────
API_KEY = os.environ.get("DEEPSEEK_API_KEY", "")
if not API_KEY:
    with open(os.path.expanduser("~/.hermes/auth.json")) as f:
        pool = json.load(f).get("credential_pool", {}).get("deepseek", [])
        if pool: API_KEY = pool[0].get("access_token", "")


def call_llm(messages):
    """调用 API"""
    resp = requests.post(
        "https://api.deepseek.com/v1/chat/completions",
        headers={"Authorization": f"Bearer {API_KEY}"},
        json={
            "model": "deepseek-chat",
            "messages": messages,
            "temperature": 0.3,
            "max_tokens": 1024,
        },
        timeout=30,
    )
    return resp.json()["choices"][0]["message"]["content"]


# ─── 状态管理器 ──────────────────────────────
class CustomerState:
    """客户状态管理器（记忆会话级的业务数据）"""
    
    def __init__(self):
        self.user_name = None
        self.order_id = None
        self.issue_type = None         # 退货 | 换货 | 咨询 | 投诉
        self.resolved = False
        self.created_at = datetime.now()
        self.notes = []
    
    def update(self, key: str, value):
        setattr(self, key, value)
    
    def get_summary(self) -> str:
        """生成状态摘要"""
        parts = []
        if self.user_name: parts.append(f"客户: {self.user_name}")
        if self.order_id: parts.append(f"订单: {self.order_id}")
        if self.issue_type: parts.append(f"问题类型: {self.issue_type}")
        parts.append(f"已解决: {'是' if self.resolved else '否'}")
        return " | ".join(parts)
    
    def to_dict(self) -> dict:
        return {
            "user_name": self.user_name,
            "order_id": self.order_id,
            "issue_type": self.issue_type,
            "resolved": self.resolved,
            "notes": self.notes,
        }


# ─── 记忆策略 ────────────────────────────────
class SlidingWindowMemory:
    def __init__(self, system_prompt: str, window_size: int = 6):
        self.system_prompt = {"role": "system", "content": system_prompt}
        self.history = []
        self.window_size = window_size
    
    def add(self, role: str, content: str):
        self.history.append({"role": role, "content": content})
    
    def get_context(self, state: CustomerState = None) -> list:
        ctx = [self.system_prompt]
        if state:
            ctx.append({
                "role": "system",
                "content": f"当前客户状态：{state.get_summary()}"
            })
        recent = self.history[-(self.window_size * 2):]
        ctx.extend(recent)
        return ctx


class SummaryMemory:
    def __init__(self, system_prompt: str, window_size: int = 3):
        self.system_prompt = {"role": "system", "content": system_prompt}
        self.history = []
        self.summary = "对话刚刚开始。"
        self.window_size = window_size
        self.last_summary_idx = 0
    
    def add(self, role: str, content: str):
        self.history.append({"role": role, "content": content})
        self._maybe_summarize()
    
    def _maybe_summarize(self):
        if len(self.history) - self.last_summary_idx < 8:
            return
        old = self.history[self.last_summary_idx:self.last_summary_idx + 6]
        text = "\n".join(f"[{m['role']}] {m['content']}" for m in old)
        try:
            new_summary = call_llm([
                {"role": "system", "content": "你是一个对话摘要助手。用一句话总结对话核心信息。"},
                {"role": "user", "content": f"对话：\n{text}\n\n摘要："}
            ])
            self.summary = f"{self.summary}\n- {new_summary}"
        except:
            pass
        self.last_summary_idx += 6
    
    def get_context(self, state: CustomerState = None) -> list:
        ctx = [self.system_prompt]
        ctx.append({"role": "system", "content": f"对话历史摘要：{self.summary}"})
        if state:
            ctx.append({"role": "system", "content": f"状态：{state.get_summary()}"})
        recent = self.history[-(self.window_size * 2):]
        ctx.extend(recent)
        return ctx


# ─── 客服系统 ────────────────────────────────
class CustomerServiceBot:
    """智能客服助手——多轮对话 + 状态管理"""
    
    def __init__(self, memory_type: str = "window"):
        self.state = CustomerState()
        
        system_prompt = """你是一个专业友好的电商客服助手。你的职责：
1. 耐心解答用户关于订单、商品、物流的问题
2. 如果用户未提供姓名，礼貌地询问
3. 如果用户提到订单号，记录下来
4. 根据用户问题判断问题类型（退货/换货/咨询/投诉）
5. 可以提供退换货政策、物流查询、商品推荐等服务
6. 回答简洁、有条理

如果被问到不知道的信息，诚实地说不清楚，不要编造。"""
        
        if memory_type == "window":
            self.memory = SlidingWindowMemory(system_prompt, window_size=6)
        elif memory_type == "summary":
            self.memory = SummaryMemory(system_prompt, window_size=3)
        else:
            self.memory = SlidingWindowMemory(system_prompt, window_size=6)
        
        self.memory_type = memory_type
    
    def chat(self, user_input: str) -> str:
        """处理用户输入，返回回复"""
        # 1. 保存用户消息
        self.memory.add("user", user_input)
        
        # 2. 状态提取（用 LLM 提取结构化信息）
        self._update_state(user_input)
        
        # 3. 获取上下文
        context = self.memory.get_context(self.state)
        
        # 4. 生成回复
        response = call_llm(context)
        
        # 5. 保存回复
        self.memory.add("assistant", response)
        
        return response
    
    def _update_state(self, text: str):
        """从用户输入中提取状态信息"""
        try:
            resp = requests.post(
                "https://api.deepseek.com/v1/chat/completions",
                headers={"Authorization": f"Bearer {API_KEY}"},
                json={
                    "model": "deepseek-chat",
                    "messages": [
                        {"role": "system", "content": "从用户输入中提取信息，返回 JSON。"},
                        {"role": "user", "content": f"从以下文本提取 name(姓名)、order_id(订单号)、issue_type(问题类型:退货/换货/咨询/投诉)：\n\n{text}"}
                    ],
                    "response_format": {"type": "json_object"},
                    "temperature": 0,
                },
                timeout=10,
            )
            data = json.loads(resp.json()["choices"][0]["message"]["content"])
            if data.get("name"): self.state.user_name = data["name"]
            if data.get("order_id"): self.state.order_id = data["order_id"]
            if data.get("issue_type"): self.state.issue_type = data["issue_type"]
            self.state.notes.append(f"[{datetime.now().strftime('%H:%M')}] {text[:30]}...")
        except:
            pass


def main():
    print("=" * 55)
    print("  💬 智能客服助手 (多轮对话)")
    print("  📌 记忆策略: 滑动窗口 (保留最近6轮)")
    print("  📌 自动提取: 姓名/订单号/问题类型")
    print("=" * 55)
    
    if not API_KEY:
        print("\n⚠️  未找到 API Key")
        return
    
    bot = CustomerServiceBot(memory_type="window")
    
    print("\n💡 试试这些对话场景：")
    print("  场景1: 我是张三，想退货，订单号 ORD-2024-1234")
    print("  场景2: 可以换货吗？有什么条件？")
    print("  场景3: 我的名字和订单号是什么？（测试记忆）")
    
    while True:
        try:
            user_input = input("\n🧑 你: ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\n👋 再见！")
            break
        
        if not user_input: continue
        if user_input.lower() in ("quit", "exit", "q"):
            print("👋 感谢咨询，再见！")
            break
        
        response = bot.chat(user_input)
        print(f"\n🤖 客服: {response}")
        
        # 显示当前状态
        print(f"\n  📋 [状态] {bot.state.get_summary()}")


if __name__ == "__main__":
    main()
```

### 对话示例

```
🧑 你: 你好，我叫张三，想退货

🤖 客服: 您好张三！很高兴为您服务。
请问您的订单号是多少？我帮您查询订单信息。

  📋 [状态] 客户: 张三 | 问题类型: 退货

---

🧑 你: 订单号是 ORD-2024-1234

🤖 客服: 已查到您的订单 ORD-2024-1234。
该订单还在退货期内，可以办理退货。
请问您退货的原因是？

  📋 [状态] 客户: 张三 | 订单: ORD-2024-1234 | 问题类型: 退货

---

🧑 你: 尺寸不合适，可以换货吗？

🤖 客服: 可以的！您可以在退货时选择"换货"选项。
换货流程：
1. 在订单页面选择"换货"
2. 选择要换的尺码
3. 快递员上门取件（免费）
4. 新商品 3-5 个工作日发出

  📋 [状态] 客户: 张三 | 订单: ORD-2024-1234 | 问题类型: 退货

---

🧑 你: 你还记得我的名字和订单号吗？

🤖 客服: 当然记得！
您叫张三，订单号是 ORD-2024-1234。
需要我帮您处理换货吗？

  📋 [状态] 客户: 张三 | 订单: ORD-2024-1234 | 问题类型: 退货
```

---

## 7. 课堂练习

### 练习1：测试记忆能力

运行客服系统，按以下顺序提问，观察是否能记住：

```
Q1: 我叫李四，想查询我的订单 SHOP-2024-5678 到哪里了
Q2: 我的名字是什么？
Q3: 订单号还记得吗？
Q4: 我之前问的是什么问题？
```

<details>
<summary>点击查看预期效果</summary>

- Q2 应回答"李四" ✅
- Q3 应回答"SHOP-2024-5678" ✅
- Q4 应回答"查询订单物流状态" ✅

这些都是通过 messages 历史（滑动窗口）实现的基础记忆。
</details>

### 练习2：对比三种记忆策略

修改 `main()` 中 `memory_type` 参数，体验差异：

```python
bot = CustomerServiceBot(memory_type="window")   # 滑动窗口
bot = CustomerServiceBot(memory_type="summary")  # 摘要记忆
```

测试长对话（10轮以上），观察哪种策略记得更久。

### 练习3：添加自定义状态字段

在 `CustomerState` 中添加新字段，支持以下场景：

```python
# 添加字段
self.product_name = None    # 商品名称
self.refund_amount = None   # 退款金额
self.delivery_address = None # 收货地址

# 在 _update_state 的提取 prompt 中添加对应字段
```

<details>
<summary>点击查看参考代码</summary>

在 `_update_state` 的 JSON Mode prompt 中添加：
```python
"从以下文本提取 name(姓名)、order_id(订单号)、product(商品名)、"
"issue_type(问题类型:退货/换货/咨询/投诉)："
```
</details>

### 练习4：会话持久化

添加保存和加载功能，让客服可以在重启后继续之前的对话：

```python
def save_session(self, path: str):
    data = {
        "state": self.state.to_dict(),
        "history": self.memory.history,
        "summary": getattr(self.memory, "summary", ""),
    }
    with open(path, "w") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

def load_session(self, path: str):
    with open(path) as f:
        data = json.load(f)
    self.state = CustomerState()
    self.state.__dict__.update(data["state"])
    self.memory.history = data["history"]
```

---

## 8. 今日小结

### 核心概念速查

| 概念 | 一句话 | 适用场景 |
|------|--------|---------|
| **多轮对话** | 传历史消息让模型有上下文 | 所有对话场景 |
| **滑动窗口** | 只保留最近 N 轮 | 短对话、简单任务 |
| **摘要记忆** | 旧内容压缩成摘要 | 长对话、客服系统 |
| **向量记忆** | RAG 检索相关历史 | 超长对话、知识密集型 |
| **状态管理** | 维护业务级数据（订单、用户） | 客服、业务系统 |

### 记忆策略选用决策树

```
对话需要持久记忆吗？
├─ 短对话 (5轮以内)
│   └─ 滑动窗口 ⭐（最简单，够用）
├─ 中等长度 (5-15轮)
│   ├─ 信息不重要 → 滑动窗口
│   └─ 信息重要 → 摘要记忆
└─ 超长对话 (15轮以上)
    ├─ 全部信息都要 → 向量记忆
    └─ 只要最近和关键信息 → 摘要记忆
```

### 最佳实践

1. **System Prompt 永远保留** — 即使是滑动窗口，system prompt 也不在窗口中
2. **状态管理器是必选项** — 不要只依赖 messages 传业务数据，用独立的状态对象
3. **摘要时机要合理** — 每 6-8 轮摘要一次，太频繁浪费 Token，太稀疏会爆窗口
4. **向量记忆的窗口保留** — 即使有向量检索，也要保留最近 2 轮的全部内容
5. **会话持久化** — 生产环境一定要支持会话恢复

### 今日检查清单

- [ ] 理解多轮对话的原理（传递历史 messages）
- [ ] 运行客服系统，体验滑动窗口记忆
- [ ] 测试客服是否能记住用户信息
- [ ] 练习 1：验证记忆能力
- [ ] 练习 2：对比三种记忆策略
- [ ] 练习 3：添加自定义状态字段
- [ ] 理解状态管理器和记忆策略的区别
- [ ] 在 `~/ai-learning/week2/notes/day11.md` 记录学习笔记

### 明天预告

**第 12 天：项目实战 🚀**

- 综合 Week2 全部技术（RAG + Function Calling + 结构化输出 + 多轮对话）
- 构建一个完整的 AI 助手
- 需求分析 + 架构设计 + 代码实现

---

> 📝 **学习笔记：** 在 `~/ai-learning/week2/notes/day11.md` 记录今天的收获
> ❓ **遇到问题：** 随时问我
> 🚀 **学有余力：** 给客服系统加上"转人工"逻辑——当用户情绪负面或连续 3 次不满意时，自动转接人工客服
> 💡 **思考：** 多轮对话的本质不是"让模型记住"，而是"帮模型找到它需要的信息"
