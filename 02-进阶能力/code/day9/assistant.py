"""
assistant.py — 智能助手（Function Calling 完整演示）

Function Calling 四步循环：
  用户 → 模型 → tool_call → 执行工具 → 返回结果 → 模型回答

用法：
  python3 assistant.py
  输入问题，助手会自动决定是否调用工具
"""

import os
import json
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from tool_registry import registry

try:
    import requests
except ImportError:
    print("⚠️  需要安装 requests: pip install requests")
    sys.exit(1)

# ─── 配置 ─────────────────────────────────────
# 从 hermes 认证系统中读取 API Key
DEEPSEEK_API_KEY = ""

# 尝试多种方式获取 API Key
if not DEEPSEEK_API_KEY:
    DEEPSEEK_API_KEY = os.environ.get("DEEPSEEK_API_KEY", "")

if not DEEPSEEK_API_KEY:
    try:
        import json as _json
        with open(os.path.expanduser("~/.hermes/auth.json")) as f:
            auth = _json.load(f)
        pool = auth.get("credential_pool", {}).get("deepseek", [])
        if pool:
            DEEPSEEK_API_KEY = pool[0].get("access_token", "")
    except Exception:
        pass

DEEPSEEK_URL = "https://api.deepseek.com/v1/chat/completions"


def call_llm(messages: list, tools: list = None, tool_choice: str = "auto") -> dict:
    """调用 DeepSeek API"""
    payload = {
        "model": "deepseek-chat",
        "messages": messages,
        "temperature": 0.3,
        "max_tokens": 2048,
    }
    if tools:
        payload["tools"] = tools
        payload["tool_choice"] = tool_choice

    resp = requests.post(
        DEEPSEEK_URL,
        headers={
            "Authorization": f"Bearer {DEEPSEEK_API_KEY}",
            "Content-Type": "application/json"
        },
        json=payload,
        timeout=30
    )
    data = resp.json()

    if "choices" not in data:
        raise Exception(f"API 错误: {json.dumps(data, ensure_ascii=False)[:200]}")

    return data["choices"][0]["message"]


def run_conversation(user_input: str, max_turns: int = 5) -> str:
    """
    运行一次完整的 Function Calling 会话。

    循环流程：
      第1步：用户提问
      第2步：模型决定 → 调工具 or 直接回答
      第3步：如果调工具 → 执行 → 返回结果 → 回到第2步
      第4步：直接回答 → 结束
    """
    messages = [
        {
            "role": "system",
            "content": "你是一个智能助手，可以使用多种工具帮助用户解决实际问题。"
                       "调用工具后，用自然语言总结结果回复用户。"
        },
        {"role": "user", "content": user_input}
    ]

    turn = 0
    while turn < max_turns:
        turn += 1
        print(f"\n  ⏳ 第 {turn} 轮推理...", end="")

        # 调用模型（传入工具定义）
        response = call_llm(messages, registry.get_schemas())

        if "tool_calls" in response and response["tool_calls"]:
            # ── 模型请求调工具 ──
            messages.append({
                "role": "assistant",
                "content": response.get("content"),
                "tool_calls": response["tool_calls"]
            })

            # 依次执行每个工具
            for tc in response["tool_calls"]:
                func_name = tc["function"]["name"]
                func_args = json.loads(tc["function"]["arguments"])
                tool_call_id = tc["id"]

                print(f"\n  🔧 调用: {func_name}({json.dumps(func_args, ensure_ascii=False)})")

                # 执行工具
                result = registry.execute(func_name, func_args)
                result_str = json.dumps(result, ensure_ascii=False)

                print(f"  ✅ 返回: {result_str[:80]}...")

                # 把工具结果返回给模型
                messages.append({
                    "role": "tool",
                    "content": result_str,
                    "tool_call_id": tool_call_id
                })

        else:
            # ── 模型直接回答，结束 ──
            return response["content"]

    return "⚠️ 达到最大轮数，请简化你的问题。"


def main():
    print("=" * 55)
    print("  🛠️  智能助手 —— Function Calling 演示")
    print("  三段论：模型决策 → 执行工具 → 总结回答")
    print("=" * 55)

    if not DEEPSEEK_API_KEY:
        print("\n⚠️  未找到 DeepSeek API Key")
        print("   请设置环境变量: export DEEPSEEK_API_KEY=你的key")
        return

    tools = registry.list_tools()
    print(f"\n📦 已加载 {len(tools)} 个工具: {', '.join(tools)}")

    print("\n💡 试试这些问题：")
    examples = [
        "北京的天气怎么样？",
        "计算 123456 × 789012 等于多少？",
        "北京今天和上海的天气对比一下",
        "计算 2 的 32 次方，然后搜索一下 AI 的最新进展",
        "1024 × 768 等于多少，深圳天气怎么样？",
    ]
    for i, ex in enumerate(examples, 1):
        print(f"  {i}. {ex}")

    print("\n💬 输入问题（输入 quit 退出）")
    print("=" * 55)

    while True:
        try:
            user_input = input("\n❓ 你: ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\n👋 再见！")
            break

        if not user_input:
            continue
        if user_input.lower() in ("quit", "exit", "q"):
            print("👋 再见！")
            break

        print(f"\n🤔 思考中...")
        answer = run_conversation(user_input)
        print(f"\n{'='*55}")
        print(f"🤖 {answer}")
        print(f"{'='*55}")


if __name__ == "__main__":
    main()
