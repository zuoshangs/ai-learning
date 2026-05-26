"""第2天：Few-shot 提示对比实验（带重试）"""

import requests
import json
import os
import time

API_KEY = os.environ.get("DEEPSEEK_API_KEY")
if not API_KEY:
    with open(os.path.expanduser("~/.hermes/.env")) as f:
        for line in f:
            if "DEEPSEEK_API_KEY" in line:
                API_KEY = line.split("=", 1)[1].strip().strip('"').strip("'")
                break

URL = "https://api.deepseek.com/v1/chat/completions"


def call_deepseek(messages, temperature=0.3, retries=3):
    """带重试的 API 调用"""
    headers = {
        "Authorization": f"Bearer {API_KEY}",
        "Content-Type": "application/json"
    }
    payload = {
        "model": "deepseek-chat",
        "messages": messages,
        "temperature": temperature,
        "max_tokens": 300
    }
    
    for attempt in range(retries):
        try:
            resp = requests.post(URL, headers=headers, json=payload, timeout=60)
            data = resp.json()
            return data["choices"][0]["message"]["content"]
        except Exception as e:
            if attempt < retries - 1:
                wait = 2 ** attempt
                print(f"  连接失败，{wait}秒后重试... ({e})")
                time.sleep(wait)
            else:
                return f"[API请求失败: {e}]"


# =========================================================
# 实验1：情感分类 — Zero-shot vs Few-shot
# =========================================================
print("=" * 65)
print("📊 实验1：情感分类 — Zero-shot vs Few-shot")
print("=" * 65)

question = '分析以下评论的情感倾向：\"手机质量不错，但屏幕有点暗。\"'

# Zero-shot
print("\n🔴 Zero-shot（无示例）:")
result_zs = call_deepseek([
    {"role": "user", "content": f"{question}\n只输出：正面/负面/中性"}
])
print(f"  输出: {result_zs}")

# Few-shot
print("\n🟢 Few-shot（3个示例）:")
result_fs = call_deepseek([
    {"role": "system", "content": "你是一个文本情感分析专家。"},
    {"role": "user", "content": "这个产品太棒了！"},
    {"role": "assistant", "content": "正面"},
    {"role": "user", "content": "质量太差，用一次就坏了。"},
    {"role": "assistant", "content": "负面"},
    {"role": "user", "content": "不好不坏，一般般吧。"},
    {"role": "assistant", "content": "中性"},
    {"role": "user", "content": "手机质量不错，但屏幕有点暗。"}
])
print(f"  输出: {result_fs}")


# =========================================================
# 实验2：SQL生成 — Zero-shot vs Few-shot
# =========================================================
print("\n\n" + "=" * 65)
print("📊 实验2：SQL生成 — Zero-shot vs Few-shot")
print("=" * 65)

sql_q = "查询本月注册的用户。users(id,name,email,created_at)"

# Zero-shot
print("\n🔴 Zero-shot:")
result_sql_zs = call_deepseek([
    {"role": "user", "content": sql_q}
])
print(f"  输出: {result_sql_zs[:200]}...")

# Few-shot
print("\n🟢 Few-shot（2个示例）:")
result_sql_fs = call_deepseek([
    {"role": "system", "content": "只输出SQL，不要解释。"},
    {"role": "user", "content": "查询价格大于100的商品。表：products(id,name,price,stock)"},
    {"role": "assistant", "content": "SELECT * FROM products WHERE price > 100;"},
    {"role": "user", "content": "统计每个分类的商品数量。表：products(id,name,category_id), categories(id,name)"},
    {"role": "assistant", "content": "SELECT c.name, COUNT(p.id) FROM categories c LEFT JOIN products p ON c.id=p.category_id GROUP BY c.id;"},
    {"role": "user", "content": sql_q}
])
print(f"  输出: {result_sql_fs}")


# =========================================================
print("\n\n" + "=" * 65)
print("📊 结论")
print("=" * 65)
print("""
1. Zero-shot：速度快，但输出格式不可控
2. Few-shot：格式一致，输出稳定
3. 3-shot 能覆盖大部分场景
4. 系统提示 + Few-shot 效果最好
""")
