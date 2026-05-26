"""
第6天：提示词模板库演示
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

# ===== 模板库 =====
TEMPLATES = {
    "code_review": {
        "name": "代码审查",
        "system": "你是一个资深代码审查专家，精通 Java、Python。按严重程度排列问题，每个问题标注行号、原因和修复建议。",
        "user": "请审查以下代码：\n语言：{language}\n代码：\n```{language}\n{code}\n```"
    },
    "translate": {
        "name": "翻译",
        "system": "你是一个专业中英翻译专家。保持格式，术语括号标注原文。",
        "user": "请将以下{source_lang}翻译成{target_lang}：\n{text}"
    },
    "sql_generator": {
        "name": "SQL 生成",
        "system": "你是一个 SQL 专家。只输出 SQL 语句，不要任何解释。",
        "user": "数据库表结构：\n{table_schema}\n需求：{query_request}"
    }
}

def call_template(template_name, **kwargs):
    t = TEMPLATES[template_name]
    user_prompt = t["user"].format(**kwargs)
    resp = requests.post("https://api.deepseek.com/chat/completions",
        headers={"Authorization": f"Bearer {API_KEY}", "Content-Type": "application/json"},
        json={"model":"deepseek-chat","messages":[
            {"role":"system","content":t["system"]},
            {"role":"user","content":user_prompt}],
            "temperature":0.3,"max_tokens":1024}, timeout=30)
    return resp.json()["choices"][0]["message"]["content"]

# 实验1：代码审查
print("=" * 60)
print("实验1：代码审查模板")
print("=" * 60)
code = """def get_user(id):
    user = db.query("SELECT * FROM users WHERE id=" + id)
    return user.name"""
print(call_template("code_review", language="python", code=code))
time.sleep(2)

# 实验2：翻译
print("\n" + "=" * 60)
print("实验2：翻译模板")
print("=" * 60)
print(call_template("translate",
    source_lang="英文", target_lang="中文",
    text="The factory method pattern is a creational pattern that provides an interface for creating objects."))
time.sleep(2)

# 实验3：SQL 生成
print("\n" + "=" * 60)
print("实验3：SQL 生成模板（只输出SQL）")
print("=" * 60)
print(call_template("sql_generator",
    table_schema="users(id,name,email), orders(id,user_id,amount,status)",
    query_request="查询每个用户的总订单金额，按金额降序"))
