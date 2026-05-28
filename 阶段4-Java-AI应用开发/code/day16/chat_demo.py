"""
Python 版对照：Spring AI 环境搭建

与 Java Spring AI 实现功能完全相同：
  1. 从环境变量加载 API Key
  2. 调用 DeepSeek 对话 API
  3. 返回 AI 回复文本

运行：python3 chat_demo.py

与 Java 对比：
- Python: 直接手写 HTTP 请求，灵活但无类型安全
- Java: Spring AI 封装了所有细节，配置驱动，适合大型项目
"""

import os
import sys
import requests


def load_api_key() -> str:
    """从环境变量或 .env 文件读取 DeepSeek API Key"""
    # 优先读取环境变量
    key = os.environ.get("DEEPSEEK_API_KEY")
    if key:
        return key

    # 后备：从 ~/.hermes/.env 读取
    env_path = os.path.expanduser("~/.hermes/.env")
    if os.path.exists(env_path):
        with open(env_path) as f:
            for line in f:
                line = line.strip()
                if line.startswith("DEEPSEEK_API_KEY"):
                    return line.split("=", 1)[1].strip().strip("'\"")

    raise ValueError(
        "未找到 DEEPSEEK_API_KEY。\n"
        "请设置环境变量：export DEEPSEEK_API_KEY='sk-xxx'\n"
        "或写入 ~/.hermes/.env"
    )


# ============================================================
# 全局配置（对应 application.yml）
# ============================================================
API_KEY = load_api_key()
URL = "https://api.deepseek.com/chat/completions"  # base-url
HEADERS = {
    "Authorization": f"Bearer {API_KEY}",
    "Content-Type": "application/json"
}
MODEL = "deepseek-chat"          # model
TEMPERATURE = 0.7                # temperature
MAX_TOKENS = 1024                # max-tokens


def chat(message: str, temperature: float = TEMPERATURE) -> str:
    """
    最简单的 AI 对话函数。
    
    对应 Java ChatController.chat() 方法：
    Java:  chatClient.prompt().user(message).call().content()
    我们:  chat(message) — 用 requests 实现同样功能
    """
    payload = {
        "model": MODEL,
        "messages": [
            {"role": "user", "content": message}
        ],
        "temperature": temperature,
        "max_tokens": MAX_TOKENS
    }

    try:
        resp = requests.post(URL, headers=HEADERS, json=payload, timeout=30)
        resp.raise_for_status()  # 非 2xx 状态码抛异常
        data = resp.json()
        return data["choices"][0]["message"]["content"]
    except requests.exceptions.Timeout:
        return "[错误] 请求超时（30秒）"
    except requests.exceptions.HTTPError as e:
        return f"[错误] HTTP {e.response.status_code}: {e.response.text[:200]}"
    except (KeyError, ValueError) as e:
        return f"[错误] 解析响应失败: {e}"


def main():
    """主函数 — 运行测试用例"""
    print("=" * 55)
    print("  Python 版 AI 对话演示（对应 Spring AI ChatClient）")
    print("=" * 55)

    # 测试1：自我介绍
    print("\n📝 测试1：自我介绍")
    print("-" * 40)
    result = chat("你好，请用中文介绍一下你自己")
    print(result)

    # 测试2：概念解释
    print("\n📝 测试2：概念解释")
    print("-" * 40)
    result = chat("用一句话解释什么是上下文窗口（Context Window）")
    print(result)

    # 测试3：温度对比（如果传了 temperature 参数）
    print("\n📝 测试3：创造性对比（temperature=0.1 vs 1.5）")
    print("-" * 40)
    prompt = "用一句诗形容人工智能"
    
    result_low = chat(prompt, temperature=0.1)
    result_high = chat(prompt, temperature=1.5)
    
    print(f"🔵 低温(0.1) — 确定性高:")
    print(f"   {result_low}")
    print()
    print(f"🟠 高温(1.5) — 创造性强:")
    print(f"   {result_high}")

    print("\n" + "=" * 55)
    print("  🎉 全部测试完成！")
    print("  你刚才做了和 Spring AI ChatClient 完全一样的事")
    print("=" * 55)


if __name__ == "__main__":
    main()
