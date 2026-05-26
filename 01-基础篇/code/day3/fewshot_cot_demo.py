"""
第3天：Few-shot CoT + Self-Consistency 演示（带重试）
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

def ask(prompt, temp=0.7, retries=3):
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
                    "messages": [{"role": "user", "content": prompt}],
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

# ===== Few-shot CoT =====
print("=" * 60)
print("实验：Few-shot CoT — 给带推理的示例")
print("=" * 60)

prompt = """参考下面的示例，用同样的分步推理方式回答问题：

示例问题：一个正方形周长20厘米，面积是多少？
示例推理：
1. 正方形周长 = 4 × 边长
2. 边长 = 20 ÷ 4 = 5厘米
3. 面积 = 边长 × 边长 = 5 × 5 = 25平方厘米
答案：25平方厘米

示例问题：小明有10个苹果，给了小红3个，又买了5个，现在有几个？
示例推理：
1. 初始有10个
2. 给小红3个：10 - 3 = 7个
3. 又买5个：7 + 5 = 12个
答案：12个

现在请回答：
鸡兔同笼，头35个，脚94只。鸡和兔各有多少只？
让我们一步一步思考。"""

result = ask(prompt, temp=0)
print(f"回答：\n{result}\n")

# ===== Self-Consistency =====
print("=" * 60)
print("实验：Self-Consistency（跑3次取多数）")
print("=" * 60)

q = "鸡兔同笼，头35个，脚94只。鸡和兔各有多少只？\n让我们一步一步思考。"

results = []
for i in range(3):
    print(f"--- 第{i+1}次 ---")
    r = ask(q, temp=0.8)
    print(f"{r}\n")
    results.append(r)
    if i < 2:
        time.sleep(3)  # 每次调用间隔3秒，避免限流

print("=" * 60)
print("Self-Consistency 统计结果（答案末尾部分）：")
print("=" * 60)
for i, r in enumerate(results, 1):
    tail = r[-150:] if len(r) > 150 else r
    print(f"第{i}次末尾：{tail}\n")
