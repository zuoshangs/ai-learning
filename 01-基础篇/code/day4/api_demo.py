"""
第4天：API 基础调用演示
"""
import os
import json
import time

API_KEY = os.environ.get("DEEPSEEK_API_KEY")
if not API_KEY:
    env_path = os.path.expanduser("~/.hermes/.env")
    if os.path.exists(env_path):
        with open(env_path) as f:
            for line in f:
                if "DEEPSEEK_API_KEY" in line:
                    API_KEY = line.split("=", 1)[1].strip().strip('"').strip("'")
                    break

import requests


def call_api(messages, temperature=0.7, max_tokens=1024, system=None):
    """调用 DeepSeek API 并返回完整响应"""
    if system:
        messages = [{"role": "system", "content": system}] + messages

    resp = requests.post(
        "https://api.deepseek.com/chat/completions",
        headers={
            "Authorization": f"Bearer {API_KEY}",
            "Content-Type": "application/json"
        },
        json={
            "model": "deepseek-chat",
            "messages": messages,
            "temperature": temperature,
            "max_tokens": max_tokens
        },
        timeout=30
    )
    return resp.json()


def call_with_retry(messages, temperature=0, max_retries=3):
    """带重试的 API 调用"""
    for attempt in range(max_retries):
        try:
            resp = call_api(messages, temperature=temperature)
            if "error" not in resp:
                return resp
            if attempt < max_retries - 1:
                wait = 2 ** attempt
                print(f"  [限流] {wait}s 后重试...")
                time.sleep(wait)
        except Exception as e:
            if attempt < max_retries - 1:
                wait = 2 ** attempt
                print(f"  [错误] {e}，{wait}s 后重试...")
                time.sleep(wait)
            else:
                raise e
    return resp


# ========== 实验1：查看完整响应结构 ==========
print("=" * 60)
print("实验1：完整响应结构")
print("=" * 60)

response = call_api(
    [{"role": "user", "content": "2 + 3 × 4 等于多少？"}],
    temperature=0
)

print(f"\n响应 ID: {response.get('id')}")
print(f"模型: {response.get('model')}")
print(f"回答: {response['choices'][0]['message']['content']}")
print(f"停止原因: {response['choices'][0]['finish_reason']}")
usage = response.get('usage', {})
print(f"\nToken 用量:")
print(f"  输入: {usage.get('prompt_tokens')} tokens")
print(f"  输出: {usage.get('completion_tokens')} tokens")
print(f"  总计: {usage.get('total_tokens')} tokens")

time.sleep(2)

# ========== 实验2：Temperature 对比 ==========
print("\n" + "=" * 60)
print("实验2：Temperature 对比 (0 vs 1.5)")
print("=" * 60)

prompt = "给我一个 Python 函数名建议（只要名字）："

r_cold = call_api([{"role": "user", "content": prompt}], temperature=0)
r_hot = call_api([{"role": "user", "content": prompt}], temperature=1.5)

print(f"\nTemperature=0:   {r_cold['choices'][0]['message']['content'].strip()}")
print(f"Temperature=1.5: {r_hot['choices'][0]['message']['content'].strip()}")

time.sleep(2)

# ========== 实验3：多轮对话 ==========
print("\n" + "=" * 60)
print("实验3：多轮对话")
print("=" * 60)

messages = [
    {"role": "user", "content": "我的名字是小明。"},
]
r1 = call_api(messages, temperature=0)
print(f"用户: 我的名字是小明。")
print(f"助手: {r1['choices'][0]['message']['content']}\n")

messages.append(r1['choices'][0]['message'])
messages.append({"role": "user", "content": "我刚才说我叫什么名字？"})

r2 = call_api(messages, temperature=0)
print(f"用户: 我刚才说我叫什么名字？")
print(f"助手: {r2['choices'][0]['message']['content']}")
