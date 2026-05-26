"""
第5天：Max Tokens 截断实验
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

def ask_with_info(prompt, max_tokens=200):
    resp = requests.post("https://api.deepseek.com/chat/completions",
        headers={"Authorization": f"Bearer {API_KEY}", "Content-Type": "application/json"},
        json={"model":"deepseek-chat","messages":[{"role":"user","content":prompt}],
              "temperature":0.7,"max_tokens":max_tokens}, timeout=30)
    data = resp.json()
    c = data["choices"][0]
    return {
        "content": c["message"]["content"],
        "finish_reason": c["finish_reason"],
        "completion_tokens": data.get("usage",{}).get("completion_tokens",0)
    }

prompt = "请详细介绍 Python 语言的历史、特点和主要应用领域。"

print("=" * 60)
print("Max Tokens 截断实验")
print("=" * 60)

for mt in [30, 80, 200, 500]:
    r = ask_with_info(prompt, mt)
    fr = r["finish_reason"]
    st = "✅ 完整" if fr == "stop" else f"⚠️ 截断({fr})"
    print(f"\n--- max_tokens={mt} (实际={r['completion_tokens']}) {st} ---")
    print(r["content"][:80])
    time.sleep(2)
