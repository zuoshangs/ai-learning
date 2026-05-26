"""
第3天：CoT 思维链演示
比较三种提问方式：Zero-shot vs Zero-shot CoT vs Few-shot CoT
"""
import os
import json
import time

# 读取 API Key
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

def ask(prompt, system=None, temp=0, retries=3):
    """带重试的 API 调用"""
    messages = []
    if system:
        messages.append({"role": "system", "content": system})
    messages.append({"role": "user", "content": prompt})

    for attempt in range(retries):
        try:
            resp = requests.post(
                "https://api.deepseek.com/chat/completions",
                headers={
                    "Authorization": f"Bearer {API_KEY}",
                    "Content-Type": "application/json"
                },
                json={
                    "model": "deepseek-chat",
                    "messages": messages,
                    "temperature": temp,
                    "max_tokens": 1024
                },
                timeout=30
            )
            return resp.json()["choices"][0]["message"]["content"]
        except Exception as e:
            if attempt < retries - 1:
                wait = 2 ** attempt
                print(f"  [重试 {attempt+1}/{retries}，等待 {wait}s]")
                time.sleep(wait)
            else:
                return f"[API 错误] {e}"

# ========== 实验1：Zero-shot（直接问，无 system prompt）==========
print("=" * 60)
print("实验1：Zero-shot（直接问 — 不加 system 引导）")
print("=" * 60)

q1 = "一个长方形的长是宽的2倍，周长是36厘米。长方形的面积是多少平方厘米？"
print(f"问题：{q1}\n")
result1 = ask(q1, system=None)
print(f"回答：\n{result1}\n")

# ========== 实验2：Zero-shot CoT（加"一步一步思考"）==========
print("=" * 60)
print("实验2：Zero-shot CoT（让我们一步一步思考）")
print("=" * 60)

q2 = q1 + "\n\n让我们一步一步思考。"
result2 = ask(q2, system=None)
print(f"回答：\n{result2}\n")

# ========== 实验3：Few-shot CoT（给带推理的示例）==========
print("=" * 60)
print("实验3：Few-shot CoT（给带推理的示例）")
print("=" * 60)

q3 = """参考下面的示例，用同样的分步推理方式回答问题：

示例问题：一个正方形周长20厘米，面积是多少？
示例推理：
第1步：正方形周长 = 4 × 边长
第2步：边长 = 20 ÷ 4 = 5厘米
第3步：面积 = 边长 × 边长 = 5 × 5 = 25平方厘米
答案：25平方厘米

示例问题：小明有10个苹果，给了小红3个，又买了5个，现在有几个？
示例推理：
第1步：初始有10个
第2步：给小红3个：10 - 3 = 7
第3步：又买5个：7 + 5 = 12
答案：12个

现在请回答：
一个长方形的长是宽的2倍，周长是36厘米。长方形的面积是多少平方厘米？
让我们一步一步思考。"""
result3 = ask(q3, system=None)
print(f"回答：\n{result3}\n")
