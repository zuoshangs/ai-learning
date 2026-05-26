"""
第5天：参数组合对比
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

def ask_params(prompt, temp=0.7, top_p=1, max_tokens=200):
    resp = requests.post("https://api.deepseek.com/chat/completions",
        headers={"Authorization": f"Bearer {API_KEY}", "Content-Type": "application/json"},
        json={"model":"deepseek-chat","messages":[{"role":"user","content":prompt}],
              "temperature":temp,"top_p":top_p,"max_tokens":max_tokens}, timeout=30)
    data = resp.json()
    return {"content":data["choices"][0]["message"]["content"],
            "finish_reason":data["choices"][0]["finish_reason"],
            "usage":data.get("usage",{})}

prompt = "从以下文本中提取日期和金额：'订单号ORD-2024-001，于2024年3月15日支付，1,280元。'"

configs = [
    ("精确模式",  {"temp":0,   "top_p":0.5, "max_tokens":100}),
    ("默认模式",  {"temp":0.7, "top_p":1,   "max_tokens":100}),
    ("创意模式",  {"temp":1.2, "top_p":0.95,"max_tokens":100}),
]

print("=" * 60)
print("参数组合对比（信息提取）")
print("=" * 60)

for name, cfg in configs:
    r = ask_params(prompt, **cfg)
    print(f"\n--- {name} (temp={cfg['temp']}, top_p={cfg['top_p']}) ---")
    print(f"输出: {r['content'][:80]}")
    print(f"状态: {r['finish_reason']}")
    time.sleep(2)
