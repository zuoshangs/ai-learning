"""
第5天：Temperature 对比实验
"""
import os, time

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

def ask(prompt, temp=0.7):
    resp = requests.post("https://api.deepseek.com/chat/completions",
        headers={"Authorization": f"Bearer {API_KEY}", "Content-Type": "application/json"},
        json={"model":"deepseek-chat","messages":[{"role":"user","content":prompt}],
              "temperature":temp,"max_tokens":200}, timeout=30)
    return resp.json()["choices"][0]["message"]["content"]

prompt = "用一句话介绍 Python。"

print("=" * 60)
print("Temperature 阶梯对比")
print("=" * 60)

for temp in [0.0, 0.3, 0.7, 1.2, 1.8]:
    result = ask(prompt, temp=temp)
    print(f"\n--- temperature={temp} ---")
    print(result[:120])
    time.sleep(2)

# 可复现性
print("\n" + "=" * 60)
print("可复现性测试 (temp=0 × 3次)")
print("=" * 60)
for i in range(3):
    r = ask(prompt, temp=0)
    print(f"第{i+1}次: {r[:80]}")
