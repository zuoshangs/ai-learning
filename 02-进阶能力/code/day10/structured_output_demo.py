"""
structured_output_demo.py — 三种结构化输出方法对比

方法1: Prompt 约束（手工法）
方法2: JSON Mode（API 原生）
方法3: Function Calling（最强约束）

用法：
  python3 structured_output_demo.py
  对比三种方法的输出差异
"""

import json
import os
import sys
import requests


# ─── 读取 API Key ─────────────────────────────
def get_api_key():
    key = os.environ.get("DEEPSEEK_API_KEY", "")
    if not key:
        try:
            with open(os.path.expanduser("~/.hermes/auth.json")) as f:
                auth = json.load(f)
            pool = auth.get("credential_pool", {}).get("deepseek", [])
            if pool:
                key = pool[0].get("access_token", "")
        except Exception:
            pass
    return key


API_KEY = get_api_key()
API_URL = "https://api.deepseek.com/v1/chat/completions"


def call_llm(messages, **kwargs):
    """调用 DeepSeek API"""
    payload = {
        "model": "deepseek-chat",
        "messages": messages,
        "temperature": 0,
    }
    payload.update(kwargs)

    resp = requests.post(
        API_URL,
        headers={"Authorization": f"Bearer {API_KEY}", "Content-Type": "application/json"},
        json=payload,
        timeout=30,
    )
    return resp.json()["choices"][0]["message"]


# ═══════════════════════════════════════════════
# 方法1: Prompt 约束
# ═══════════════════════════════════════════════
def method1_prompt(text: str) -> dict:
    """通过 Prompt 让模型返回 JSON"""
    prompt = f"""从以下文本中提取信息，只返回 JSON（不要加任何其他文字）：

文本：{text}

要求的 JSON 格式：
{{
    "name": "姓名",
    "age": "年龄（数字）",
    "job": "职业",
    "city": "所在城市",
    "skills": ["技能列表"]
}}"""

    msg = call_llm([{"role": "user", "content": prompt}])
    content = msg["content"].strip()

    # 清理可能的 markdown 包裹
    if "```" in content:
        content = content.split("```")[1]
        if content.startswith("json"):
            content = content[4:]

    # 提取 JSON 部分
    start = content.find("{")
    end = content.rfind("}") + 1
    return json.loads(content[start:end]) if start >= 0 else {"error": "未找到 JSON"}


# ═══════════════════════════════════════════════
# 方法2: JSON Mode
# ═══════════════════════════════════════════════
def method2_json_mode(text: str) -> dict:
    """使用 JSON Mode"""
    msg = call_llm(
        messages=[
            {"role": "system", "content": "你是一个信息提取助手。请从用户输入中提取信息，返回 JSON 格式。"},
            {"role": "user", "content": f"从以下文本提取姓名、年龄、职业、城市、技能：\n\n{text}"},
        ],
        response_format={"type": "json_object"},
    )
    return json.loads(msg["content"])


# ═══════════════════════════════════════════════
# 方法3: Function Calling
# ═══════════════════════════════════════════════
EXTRACTION_SCHEMA = {
    "name": "extract_person_info",
    "description": "从文本中提取人物信息",
    "parameters": {
        "type": "object",
        "properties": {
            "name": {"type": "string", "description": "姓名"},
            "age": {"type": "integer", "description": "年龄"},
            "job": {"type": "string", "description": "职业"},
            "city": {"type": "string", "description": "所在城市"},
            "skills": {"type": "array", "items": {"type": "string"}, "description": "技能列表"},
            "email": {"type": "string", "description": "邮箱地址"},
        },
        "required": ["name", "age", "job"],
    },
}


def method3_function_calling(text: str) -> dict:
    """使用 Function Calling 强制输出"""
    msg = call_llm(
        messages=[
            {"role": "system", "content": "从用户输入中提取结构化信息。"},
            {"role": "user", "content": text},
        ],
        tools=[{"type": "function", "function": EXTRACTION_SCHEMA}],
        tool_choice={"type": "function", "function": {"name": "extract_person_info"}},
    )
    return json.loads(msg["tool_calls"][0]["function"]["arguments"])


# ═══════════════════════════════════════════════
# 主程序
# ═══════════════════════════════════════════════
def main():
    if not API_KEY:
        print("⚠️  未找到 API Key")
        return

    test_cases = [
        "我叫张三，28岁，软件工程师，精通Python和Java，邮箱zhangsan@email.com",
        "李四，35岁，北京，产品经理，5年互联网经验",
        "王五，42岁，深圳，技术总监，擅长团队管理和架构设计",
    ]

    methods = [
        ("📝 Prompt 约束", method1_prompt),
        ("🔧 JSON Mode", method2_json_mode),
        ("⚡ Function Calling", method3_function_calling),
    ]

    for text in test_cases:
        print("\n" + "=" * 60)
        print(f"📄 输入: {text}")
        print("=" * 60)

        for name, method in methods:
            try:
                result = method(text)
                # 验证是否为有效 JSON
                json.dumps(result, ensure_ascii=False)
                valid = "✅"
            except Exception as e:
                result = {"error": str(e)[:60]}
                valid = "❌"

            print(f"\n{valid} {name}:")
            print(f"   {json.dumps(result, ensure_ascii=False)}")


if __name__ == "__main__":
    main()
