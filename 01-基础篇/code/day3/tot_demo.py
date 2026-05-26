"""
第3天：Tree-of-Thought（思维树）演示（带重试）
三步法：探索 → 评估 → 选择
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

def ask(prompt, system="你是一个逻辑推理专家。", retries=3):
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
                    "messages": [
                        {"role": "system", "content": system},
                        {"role": "user", "content": prompt}
                    ],
                    "temperature": 0.7,
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

print("=" * 60)
print("Tree-of-Thought（思维树）演示")
print("问题：24点游戏 — 用 3, 3, 8, 8 算出24")
print("=" * 60)

# 第一步：探索
print("\n【第1步：探索 — 列出多种思路】\n")
step1 = ask(
    "用 3, 3, 8, 8 四个数字，通过加减乘除和括号，算出24。"
    "请列出3-4种不同的解题思路，不需要给出完整计算，只描述策略方向。"
)
print(step1)
time.sleep(2)

# 第二步：评估
print("\n【第2步：评估 — 判断每种思路可行性】\n")
step2 = ask(
    f"以下是用 3,3,8,8 算24的几种思路：\n{step1}\n\n"
    "请客观评估每种思路的可行性，指出可能的陷阱。"
)
print(step2)
time.sleep(2)

# 第三步：选择
print("\n【第3步：选择 — 选出最佳方案并完整推导】\n")
step3 = ask(
    "基于以上分析，选择最佳的解题思路，给出完整的计算过程。\n"
    "题目：用 3, 3, 8, 8 算出24。"
)
print(step3)
