"""第2天：Zero-shot 提示对比实验

同一个问题，三种不同的提示写法 → 对比模型输出质量"""

import requests
import json
import os

API_KEY = os.environ.get("DEEPSEEK_API_KEY")
if not API_KEY:
    # 尝试从 .env 读取
    with open(os.path.expanduser("~/.hermes/.env")) as f:
        for line in f:
            if "DEEPSEEK_API_KEY" in line:
                API_KEY = line.split("=", 1)[1].strip().strip('"').strip("'")
                break

URL = "https://api.deepseek.com/v1/chat/completions"


def call_deepseek(system_prompt, user_message, temperature=0.3):
    """调用 DeepSeek API"""
    headers = {
        "Authorization": f"Bearer {API_KEY}",
        "Content-Type": "application/json"
    }
    payload = {
        "model": "deepseek-chat",
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_message}
        ],
        "temperature": temperature,
        "max_tokens": 600
    }
    resp = requests.post(URL, headers=headers, json=payload, timeout=30)
    data = resp.json()
    return data["choices"][0]["message"]["content"]


print("=" * 60)
print("🧪 实验：同一个问题，三种不同提示词")
print("=" * 60)

question = "请用Java写一个方法，判断一个字符串是否是回文。"

# === 实验A：最简提示 ===
print("\n" + "=" * 60)
print("🔴 实验A：最简提示")
print("   系统提示: '你是一个助手。'")
print("   用户消息: '请用Java写一个方法，判断一个字符串是否是回文。'")
print("=" * 60)
result_a = call_deepseek("你是一个助手。", question)
print(result_a)

# === 实验B：加角色设定 ===
print("\n" + "=" * 60)
print("🟡 实验B：角色设定")
print("   系统提示: '你是一位资深Java开发工程师...'")
print("   用户消息: 同上")
print("=" * 60)
result_b = call_deepseek(
    "你是一位资深Java开发工程师，精通算法和数据结构。回答要简洁专业，直接给出代码。",
    question
)
print(result_b)

# === 实验C：结构化提示 ===
print("\n" + "=" * 60)
print("🟢 实验C：结构化提示（角色 + 要求 + 格式约束）")
print("=" * 60)
structured_prompt = """请用Java实现判断回文字符串的方法。

要求：
1. 方法签名：public static boolean isPalindrome(String s)
2. 忽略大小写和非字母数字字符
3. 处理 null 和空字符串（返回 false）
4. 用双指针实现，不要用 StringBuilder.reverse()
5. 添加详细的中文注释
6. 包含 main 方法，测试至少3个用例

输出格式：
- 先给出完整代码
- 然后给出算法复杂度分析"""
result_c = call_deepseek("你是一位精通Java和算法的专家。回答要严谨、完整。", structured_prompt)
print(result_c)

# 总结
print("\n\n" + "=" * 60)
print("📊 对比总结")
print("=" * 60)
print(f"实验A（最简）: {'✅' if len(result_a) > 100 else '❌'} 长度={len(result_a)}字")
print(f"实验B（角色）: {'✅' if len(result_b) > 100 else '❌'} 长度={len(result_b)}字")
print(f"实验C（结构）: {'✅' if len(result_c) > 100 else '❌'} 长度={len(result_c)}字")
print("\n💡 结论：提示词越具体，输出质量越高！")
