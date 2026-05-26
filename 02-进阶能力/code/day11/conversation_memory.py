"""
conversation_memory.py — 三种记忆策略实现

滑动窗口 · 摘要记忆 · 向量记忆
支持状态管理、会话持久化
"""

import json
import os
import requests
from datetime import datetime
from math import sqrt


# ─── API 配置 ────────────────────────────────
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


def call_llm(messages, temperature=0.3):
    """通用 LLM 调用"""
    if not API_KEY:
        return "[API Key 未配置]"
    resp = requests.post(
        API_URL,
        headers={"Authorization": f"Bearer {API_KEY}"},
        json={
            "model": "deepseek-chat",
            "messages": messages,
            "temperature": temperature,
            "max_tokens": 1024,
        },
        timeout=30,
    )
    return resp.json()["choices"][0]["message"]["content"]


# ─── 简易 Embedding（懒加载） ─────────────────
_embed_model = None
_embed_available = None


def _init_embedding():
    global _embed_model, _embed_available
    if _embed_available is not None:
        return
    try:
        from sentence_transformers import SentenceTransformer
        hf_endpoint = os.environ.get("HF_ENDPOINT", "https://hf-mirror.com")
        os.environ["HF_ENDPOINT"] = hf_endpoint
        _embed_model = SentenceTransformer("all-MiniLM-L6-v2")
        _embed_available = True
    except ImportError:
        _embed_available = False


def embed_text(text: str) -> list:
    _init_embedding()
    if _embed_available and _embed_model is not None:
        return _embed_model.encode(text).tolist()
    # 退化为伪嵌入（仅用于演示）
    import random
    random.seed(hash(text) % (2 ** 31))
    return [random.random() for _ in range(384)]


# ═══════════════════════════════════════════════
# 状态管理器
# ═══════════════════════════════════════════════
class CustomerState:
    """客户状态管理器——会话级的业务数据"""

    def __init__(self):
        self.user_name = None
        self.order_id = None
        self.issue_type = None  # 退货 | 换货 | 咨询 | 投诉
        self.resolved = False
        self.created_at = datetime.now()
        self.notes = []

    def update(self, key, value):
        setattr(self, key, value)

    def get_summary(self):
        parts = []
        if self.user_name:
            parts.append(f"客户: {self.user_name}")
        if self.order_id:
            parts.append(f"订单: {self.order_id}")
        if self.issue_type:
            parts.append(f"问题: {self.issue_type}")
        parts.append("已解决" if self.resolved else "进行中")
        return " | ".join(parts)

    def to_dict(self):
        return {
            "user_name": self.user_name,
            "order_id": self.order_id,
            "issue_type": self.issue_type,
            "resolved": self.resolved,
            "notes": self.notes,
        }

    @classmethod
    def from_dict(cls, data):
        state = cls()
        state.__dict__.update(data)
        return state


# ═══════════════════════════════════════════════
# 记忆策略 1：滑动窗口
# ═══════════════════════════════════════════════
class SlidingWindowMemory:
    """只保留最近 N 轮对话"""

    def __init__(self, system_prompt: str, window_size: int = 6):
        self.system_prompt = {"role": "system", "content": system_prompt}
        self.history = []
        self.window_size = window_size

    def add(self, role: str, content: str):
        self.history.append({"role": role, "content": content})

    def get_context(self, question: str = None, state: CustomerState = None) -> list:
        ctx = [self.system_prompt]
        if state:
            ctx.append({
                "role": "system",
                "content": f"客户状态：{state.get_summary()}"
            })
        recent = self.history[-(self.window_size * 2):]
        ctx.extend(recent)
        return ctx


# ═══════════════════════════════════════════════
# 记忆策略 2：摘要记忆
# ═══════════════════════════════════════════════
class SummaryMemory:
    """旧对话压缩为摘要，保留最近细节"""

    def __init__(self, system_prompt: str, window_size: int = 3, summary_interval: int = 6):
        self.system_prompt = {"role": "system", "content": system_prompt}
        self.history = []
        self.summary = "对话刚刚开始。"
        self.window_size = window_size
        self.summary_interval = summary_interval
        self.last_summary_idx = 0

    def add(self, role: str, content: str):
        self.history.append({"role": role, "content": content})
        self._maybe_summarize()

    def _maybe_summarize(self):
        if len(self.history) - self.last_summary_idx < self.summary_interval:
            return
        old = self.history[self.last_summary_idx:self.last_summary_idx + self.summary_interval - 2]
        if not old:
            return
        text = "\n".join(f"[{m['role']}] {m['content']}" for m in old)
        try:
            new = call_llm([
                {"role": "system", "content": "你是一个对话摘要助手。用简洁的语言总结对话中出现的所有关键事实。"},
                {"role": "user", "content": f"对话：\n{text}\n\n摘要："}
            ], temperature=0)
            self.summary = f"{self.summary}\n- {new}"
        except Exception as e:
            self.summary += f"\n- (摘要生成失败: {str(e)[:30]})"
        self.last_summary_idx += self.summary_interval - 2

    def get_context(self, question: str = None, state: CustomerState = None) -> list:
        ctx = [self.system_prompt]
        ctx.append({"role": "system", "content": f"对话历史摘要：{self.summary}"})
        if state:
            ctx.append({"role": "system", "content": f"客户状态：{state.get_summary()}"})
        recent = self.history[-(self.window_size * 2):]
        ctx.extend(recent)
        return ctx


# ═══════════════════════════════════════════════
# 记忆策略 3：向量记忆
# ═══════════════════════════════════════════════
class VectorMemory:
    """用 RAG 检索相关历史"""

    def __init__(self, system_prompt: str, window_size: int = 2):
        self.system_prompt = {"role": "system", "content": system_prompt}
        self.history = []
        self.window_size = window_size
        self._vectors = []  # [(vector, msg_index)]

    def add(self, role: str, content: str):
        idx = len(self.history)
        self.history.append({"role": role, "content": content})
        if role == "user":
            vec = embed_text(content)
            self._vectors.append((vec, idx))

    def get_context(self, question: str = None, state: CustomerState = None) -> list:
        ctx = [self.system_prompt]
        if state:
            ctx.append({"role": "system", "content": f"客户状态：{state.get_summary()}"})

        if question and self._vectors:
            q_vec = embed_text(question)
            scored = []
            for vec, idx in self._vectors:
                dot = sum(a * b for a, b in zip(q_vec, vec))
                n1 = sqrt(sum(a * a for a in q_vec))
                n2 = sqrt(sum(b * b for b in vec))
                score = dot / (n1 * n2) if n1 > 0 and n2 > 0 else 0
                scored.append((score, idx))
            scored.sort(reverse=True)

            retrieved_indices = set()
            for score, idx in scored[:3]:
                retrieved_indices.add(idx)
                if idx + 1 < len(self.history):
                    retrieved_indices.add(idx + 1)

            recent_start = max(0, len(self.history) - self.window_size * 2)
            relevant = [self.history[i] for i in sorted(retrieved_indices)
                        if i < recent_start]

            if relevant:
                ctx.append({
                    "role": "system",
                    "content": "以下是相关的历史对话片段："
                })
                ctx.extend(relevant)

        recent = self.history[-(self.window_size * 2):]
        ctx.extend(recent)
        return ctx


# ═══════════════════════════════════════════════
# 客服系统
# ═══════════════════════════════════════════════
class CustomerServiceBot:
    """智能客服——可切换记忆策略"""

    STRATEGIES = {
        "window": SlidingWindowMemory,
        "summary": SummaryMemory,
        "vector": VectorMemory,
    }

    SYSTEM_PROMPT = """你是一个专业友好的电商客服助手。你的职责：
1. 耐心解答用户关于订单、商品、物流的问题
2. 提供退换货政策、物流查询、商品推荐等服务
3. 回答简洁有条理，语气亲切
4. 如果不知道的信息，诚实地说不清楚

请基于对话历史和客户状态回应用户。"""

    def __init__(self, memory_type: str = "window"):
        self.state = CustomerState()
        MemoryClass = self.STRATEGIES.get(memory_type, SlidingWindowMemory)
        self.memory = MemoryClass(self.SYSTEM_PROMPT)
        self.memory_type = memory_type

    def chat(self, user_input: str) -> str:
        """处理用户输入 → 更新状态 → 生成回复"""
        self.memory.add("user", user_input)
        self._update_state(user_input)

        context = self.memory.get_context(
            question=user_input if self.memory_type == "vector" else None,
            state=self.state,
        )
        response = call_llm(context)
        self.memory.add("assistant", response)
        return response

    def _update_state(self, text: str):
        """从输入中提取结构化状态"""
        try:
            resp = requests.post(
                API_URL,
                headers={"Authorization": f"Bearer {API_KEY}"},
                json={
                    "model": "deepseek-chat",
                    "messages": [
                        {"role": "system", "content": "从文本中提取信息，返回JSON。"},
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
        except Exception:
            pass

    def save_session(self, path: str):
        data = {
            "state": self.state.to_dict(),
            "history": self.memory.history,
            "summary": getattr(self.memory, "summary", ""),
        }
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        print(f"  💾 会话已保存到 {path}")

    def load_session(self, path: str):
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
        self.state = CustomerState.from_dict(data["state"])
        self.memory.history = data.get("history", [])
        if "summary" in data:
            self.memory.summary = data["summary"]


# ─── 主程序 ──────────────────────────────────
def main():
    print("=" * 55)
    print("  💬 智能客服助手 (多轮对话 + 状态管理)")
    print("=" * 55)

    if not API_KEY:
        print("\n⚠️  未找到 API Key")
        return

    print("\n📌 请选择记忆策略：")
    print("   1. 滑动窗口 (window) — 默认，简单够用")
    print("   2. 摘要记忆 (summary) — 长对话推荐")
    print("   3. 向量记忆 (vector) — 超长对话专用")
    choice = input("\n选择 (1/2/3) [默认1]: ").strip()

    strategy = {"1": "window", "2": "summary", "3": "vector"}.get(choice, "window")
    bot = CustomerServiceBot(memory_type=strategy)
    print(f"\n  ✅ 已加载策略: {strategy}")

    print("\n💡 试试这个对话场景：")
    print("  你: 你好，我叫张三，想退货，订单号 ORD-2024-1234")
    print("  你: 可以换货吗？有什么条件？")
    print("  你: 你还记得我的名字和订单号吗？")

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
        if user_input.lower() == "/save":
            bot.save_session("session_backup.json")
            continue
        if user_input.lower() == "/load":
            bot.load_session("session_backup.json")
            print("  📂 会话已恢复")
            continue
        if user_input.lower() == "/state":
            print(f"  📋 {bot.state.get_summary()}")
            continue

        response = bot.chat(user_input)
        print(f"\n🤖 客服: {response}")

        # 简短状态
        print(f"  📋 状态: {bot.state.get_summary()}")


if __name__ == "__main__":
    main()
