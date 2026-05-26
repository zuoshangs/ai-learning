"""
第5天：Stop Sequences 实验
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

def ask_with_stop(prompt, stop=None):
    params = {"model":"deepseek-chat","messages":[{"role":"user","content":prompt}],
              "temperature":0,"max_tokens":200}
    if stop: params["stop"] = stop
    resp = requests.post("https://api.deepseek.com/chat/completions",
        headers={"Authorization": f"Bearer {API_KEY}", "Content-Type": "application/json"},
        json=params, timeout=30)
    data = resp.json()
    c = data["choices"][0]
    return {"content": c["message"]["content"], "finish_reason": c["finish_reason"]}

# 实验1：无 stop
print("=" * 60)
print("实验1：无 stop")
print("=" * 60)
r1 = ask_with_stop("列出3种编程语言：")
print(r1["content"][:100])
print(f"  finish_reason: {r1['finish_reason']}")
time.sleep(2)

# 实验2：stop=["\n"]
print("\n" + "=" * 60)
print('实验2：stop=["\\\\n"]')
print("=" * 60)
r2 = ask_with_stop("列出3种编程语言：", stop=["\n"])
print(r2["content"][:80])
print(f"  finish_reason: {r2['finish_reason']}")
time.sleep(2)

# 实验3：stop=["```"]
print("\n" + "=" * 60)
print('实验3：stop=["```"]')
print("=" * 60)
r3 = ask_with_stop("用 Python 写斐波那契函数，用 ``` 包裹。", stop=["```"])
print(r3["content"][:80])
