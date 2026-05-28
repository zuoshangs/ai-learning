#!/usr/bin/env python3
"""
Day 38 — Multi-turn Conversation Demo
Smart Customer Service Platform core: conversation memory + AI chat
"""
import json
import time
import uuid
from dataclasses import dataclass, field
from typing import Optional


# ============================================================
# Conversation Memory (same logic as ConversationMemory.java)
# ============================================================
class ConversationMemory:
    def __init__(self, max_history: int = 10):
        self.max_history = max_history
        self.sessions: dict[str, list[dict]] = {}

    def add_message(self, session_id: Optional[str], role: str, content: str) -> str:
        if not session_id:
            session_id = str(uuid.uuid4())
        if session_id not in self.sessions:
            self.sessions[session_id] = []

        self.sessions[session_id].append({
            "role": role,
            "content": content,
            "timestamp": int(time.time() * 1000)
        })

        # Trim to max_history
        if len(self.sessions[session_id]) > self.max_history * 2:  # user + assistant pairs
            excess = len(self.sessions[session_id]) - self.max_history * 2
            self.sessions[session_id] = self.sessions[session_id][excess:]

        return session_id

    def get_history(self, session_id: str) -> list[dict]:
        return self.sessions.get(session_id, [])

    def clear(self, session_id: str):
        self.sessions.pop(session_id, None)

    @property
    def active_sessions(self) -> int:
        return len(self.sessions)

    def format_prompt(self, session_id: str, system_prompt: str = None) -> str:
        """Format conversation history into a prompt."""
        history = self.get_history(session_id)
        if not history:
            return ""

        lines = []
        if system_prompt:
            lines.append(system_prompt)
            lines.append("")

        lines.append("## 对话历史")
        for msg in history:
            role_label = "用户" if msg["role"] == "user" else "客服"
            lines.append(f"{role_label}: {msg['content']}")

        lines.append("客服:")
        return "\n".join(lines)


# ============================================================
# Simulated AI Customer Service
# ============================================================
class CustomerServiceBot:
    """Simulates the Java ConversationService for testing."""

    KNOWLEDGE_BASE = {
        "退货": "根据我们的退货政策，您可以在购买后 30 天内申请退货。商品需保持原状且包装完整。",
        "退款": "退款会在我们收到退货后的 5-7 个工作日内原路返回。",
        "物流": "标准配送通常需要 3-5 个工作日。加急配送需要 1-2 个工作日。",
        "会员": "会员等级分为普通、银卡、金卡和钻石卡。消费越多等级越高，享受的折扣也越多。",
        "联系人工": "您可以通过在线客服、电话 400-123-4567 或发送邮件至 support@example.com 联系我们。",
        "默认": "感谢您的咨询！我帮您查一下相关信息。请问还有什么具体的问题吗？",
    }

    def __init__(self, memory: ConversationMemory):
        self.memory = memory

    def respond(self, session_id: Optional[str], message: str) -> dict:
        """Process a message and return a response (simulated)."""
        sid = self.memory.add_message(session_id, "user", message)

        # Simulate AI thinking time
        time.sleep(0.3)

        # Find matching knowledge
        reply = "请问您能说得更具体一些吗？"
        for keyword, answer in self.KNOWLEDGE_BASE.items():
            if keyword in message:
                reply = answer
                break

        # Add to history
        self.memory.add_message(sid, "assistant", reply)

        return {
            "sessionId": sid,
            "reply": reply,
            "historySize": len(self.memory.get_history(sid)),
            "timestamp": int(time.time() * 1000)
        }


# ============================================================
# Tests
# ============================================================
def test_memory_basics():
    print("  [Test 1] Memory Basics")
    mem = ConversationMemory(max_history=10)

    # Session creation
    sid = mem.add_message(None, "user", "hello")
    assert sid is not None, "Should auto-generate session ID"
    print(f"    Auto-generated session: {sid[:8]}... ✅")

    # Add messages
    mem.add_message(sid, "assistant", "Hi there!")
    history = mem.get_history(sid)
    assert len(history) == 2, f"Expected 2 messages, got {len(history)}"
    print(f"    History size: {len(history)} ✅")

    # Clear
    mem.clear(sid)
    assert len(mem.get_history(sid)) == 0
    print(f"    Clear works ✅")

    return mem


def test_multi_turn():
    print("\n  [Test 2] Multi-turn Conversation")
    mem = ConversationMemory(max_history=10)
    bot = CustomerServiceBot(mem)
    sid = None

    conversation = [
        "你好，我想退货",
        "退货的流程是什么？",
        "退款需要多久？",
        "好的，谢谢"
    ]

    for i, msg in enumerate(conversation):
        result = bot.respond(sid, msg)
        sid = result["sessionId"]
        print(f"    Turn {i+1}:")
        print(f"      User: {msg}")
        print(f"      Bot:  {result['reply'][:40]}...")
        print(f"      History: {result['historySize']} msgs")

    history = mem.get_history(sid)
    assert len(history) == 8, f"Should have 8 messages, got {len(history)}"
    print(f"    Total turns: {len(history)//2} ✅")
    print(f"    Multi-turn context maintained ✅")


def test_session_isolation():
    print("\n  [Test 3] Session Isolation")
    mem = ConversationMemory(max_history=10)
    bot = CustomerServiceBot(mem)

    # Session A: talks about refunds
    sid_a = bot.respond(None, "我要退款")["sessionId"]
    bot.respond(sid_a, "退货怎么操作")

    # Session B: talks about membership
    sid_b = bot.respond(None, "会员有什么好处")["sessionId"]
    bot.respond(sid_b, "如何升级到金卡")

    hist_a = mem.get_history(sid_a)
    hist_b = mem.get_history(sid_b)

    assert len(hist_a) == 4, f"Session A should have 4 msgs, got {len(hist_a)}"
    assert len(hist_b) == 4, f"Session B should have 4 msgs, got {len(hist_b)}"

    # Messages should not be shared
    # Session A should have "退款" not "金卡"
    a_content = str(hist_a)
    assert "退款" in a_content
    assert "金卡" not in a_content

    b_content = str(hist_b)
    assert "金卡" in b_content
    assert "退款" not in b_content

    print(f"    Session A: {len(hist_a)} msgs (退款 related) ✅")
    print(f"    Session B: {len(hist_b)} msgs (会员 related) ✅")
    print(f"    No cross-contamination between sessions ✅")


def test_memory_trimming():
    print("\n  [Test 4] Memory Trimming")
    mem = ConversationMemory(max_history=3)  # Keep only 3 turns
    bot = CustomerServiceBot(mem)
    sid = None

    # Send 5 messages (5 turns = 10 messages user+assistant)
    for i in range(5):
        result = bot.respond(sid, f"测试问题 {i+1}")
        sid = result["sessionId"]

    history = mem.get_history(sid)
    print(f"    After 5 turns: {len(history)} messages (max_history=3 should keep 6)")
    assert len(history) <= 6, f"Should be trimmed to <= 6, got {len(history)}"
    print(f"    Memory trimming works ✅")


def test_prompt_format():
    print("\n  [Test 5] Prompt Format")
    mem = ConversationMemory(max_history=10)
    bot = CustomerServiceBot(mem)

    sid = bot.respond(None, "你好")["sessionId"]
    bot.respond(sid, "我想退货")

    prompt = mem.format_prompt(sid, "你是一个专业的AI客服助手。")
    print(f"    Generated prompt ({len(prompt)} chars):")
    for line in prompt.split("\n")[:6]:
        print(f"      {line}")

    assert "对话历史" in prompt
    assert "用户: 你好" in prompt
    assert "客服: " in prompt  # Trailing assistant prefix
    print(f"    Prompt format correct ✅")


def test_full_scenario():
    print("\n  [Test 6] Realistic Customer Service Scenario")
    mem = ConversationMemory(max_history=10)
    bot = CustomerServiceBot(mem)
    sid = None

    scenario = [
        "你好",
        "我想了解一下你们的会员制度",
        "银卡会员有什么权益？",
        "如果我想联系人工客服怎么办？",
    ]

    print(f"    Simulating {len(scenario)}-turn conversation...")
    for msg in scenario:
        result = bot.respond(sid, msg)
        sid = result["sessionId"]
        msgs = result["historySize"]

    assert msgs == len(scenario) * 2, f"Expected {len(scenario)*2} msgs, got {msgs}"
    print(f"    Scenario completed: {len(scenario)} turns, {msgs} messages ✅")


# ============================================================
# Main
# ============================================================
def main():
    print("=" * 60)
    print("  Day 38: Multi-turn Conversation Demo")
    print("  Smart Customer Service Platform")
    print("=" * 60)

    test_memory_basics()
    test_multi_turn()
    test_session_isolation()
    test_memory_trimming()
    test_prompt_format()
    test_full_scenario()

    print("\n" + "=" * 60)
    print("  ✅ All Day 38 tests passed!")
    print("  Core features: Memory + Multi-turn + Session\n")
    print("  Next: Day 39 — RAG Knowledge Base 📚")
    print("=" * 60)


if __name__ == "__main__":
    main()
