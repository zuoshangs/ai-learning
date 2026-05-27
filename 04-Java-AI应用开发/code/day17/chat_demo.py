"""
Python 对照：提示词模板 + 结构化输出

与 Java Spring AI 版本功能完全对应：
1. 模式1: 普通对话
2. 模式2: 提示词模板（参数化注入）
3. 模式3: 结构化输出（JSON → 对象映射）
"""
import os, json, requests

def load_api_key():
    key = os.environ.get("DEEPSEEK_API_KEY")
    if key: return key
    env = os.path.expanduser("~/.hermes/.env")
    if os.path.exists(env):
        with open(env) as f:
            for line in f:
                if "DEEPSEEK_API_KEY" in line:
                    return line.split("=",1)[1].strip().strip("'\"")
    raise ValueError("DEEPSEEK_API_KEY not found")

API_KEY = load_api_key()
URL = "https://api.deepseek.com/chat/completions"
HEADERS = {"Authorization": f"Bearer {API_KEY}", "Content-Type": "application/json"}

def call_llm(messages, temperature=0.3):
    """通用 LLM 调用"""
    payload = {"model": "deepseek-chat", "messages": messages,
               "temperature": temperature, "max_tokens": 1024}
    resp = requests.post(URL, headers=HEADERS, json=payload, timeout=60)
    return resp.json()["choices"][0]["message"]["content"]


# =========================================================
# 模式1：普通对话
# =========================================================
def chat(message: str) -> str:
    """普通对话 — 对应 Java AiChatService.chat()"""
    return call_llm([{"role": "user", "content": message}])

# =========================================================
# 模式2：提示词模板
# =========================================================
def code_review(language: str, code: str) -> str:
    """代码审查模板 — 对应 Java codeReview()"""
    template = f"""你是一个资深的{language}代码审查专家。
请审查以下代码，指出所有问题，包括安全漏洞、性能问题、代码规范、可维护性、潜在Bug。

代码：
```{language}
{code}
```

请按严重程度排列，每个问题标注行号和修改建议。"""
    return call_llm([{"role": "user", "content": template}])

def translate(source_lang: str, target_lang: str, text: str) -> str:
    """翻译模板 — 对应 Java translate()"""
    template = f"""
你是一个专业{source_lang}译{target_lang}专家。只输出翻译结果，不要解释。

原文（{source_lang}）：
{text}"""
    return call_llm([{"role": "user", "content": template}])

def generate_sql(schema: str, query: str) -> str:
    """SQL生成模板 — 对应 Java generateSql()"""
    template = f"""数据库表结构：
{schema}

查询需求：
{query}

只输出 SQL 语句，不要任何解释。"""
    return call_llm([{"role": "user", "content": template}])

# =========================================================
# 模式3：结构化输出（自己解析JSON，对应Java的BeanOutputConverter）
# =========================================================
def get_book_info(book_name: str) -> dict:
    """获取图书信息（结构化）— 对应 Java getBookInfo()"""
    prompt = f"""请提供《{book_name}》的详细信息，只输出 JSON，不要其他内容。

JSON格式：
{{
    "title": "书名",
    "author": "作者",
    "publishYear": 出版年份,
    "genre": "类型",
    "rating": 评分(0-10),
    "tags": ["标签1", "标签2"],
    "summary": "简介"
}}"""
    result = call_llm([{"role": "user", "content": prompt}], temperature=0.1)
    return json.loads(result)

def structured_code_review(code: str) -> dict:
    """结构化代码审查 — 对应 Java structuredCodeReview()"""
    prompt = f"""审查以下 Java 代码，只输出 JSON：

```java
{code}
```

JSON格式：
{{
    "totalScore": 0-100,
    "verdict": "PASS/MINOR/MAJOR/CRITICAL",
    "strengths": ["优点1"],
    "issues": [
        {{"severity": "critical/major/minor/suggestion", "line": 行号, "description": "问题描述", "suggestion": "修改建议"}}
    ],
    "summary": "总结"
}}"""
    result = call_llm([{"role": "user", "content": prompt}], temperature=0.1)
    return json.loads(result)


if __name__ == "__main__":
    print("=" * 55)
    print("  Day 17 Python 对照 — 提示词模板 + 结构化输出")
    print("=" * 55)
    
    # 模式1：普通对话
    print("\n📝 模式1：普通对话")
    print("-" * 40)
    print(chat("你好，今天学提示词模板"))
    
    # 模式2：提示词模板
    print("\n📝 模式2：代码审查模板")
    print("-" * 40)
    bad_code = '''public class Calc {
    public static void main(String[] args) {
        int a = Integer.parseInt(args[0]);
        int b = Integer.parseInt(args[1]);
        System.out.println(a / b);
    }
}'''
    print(code_review("java", bad_code)[:300] + "...")
    
    # 模式3：结构化输出
    print("\n📝 模式3：图书信息（结构化JSON）")
    print("-" * 40)
    book = get_book_info("三体")
    print(f"  书名: {book['title']}")
    print(f"  作者: {book['author']}")
    print(f"  评分: {book['rating']}")
    print(f"  标签: {book.get('tags', [])}")
    
    print("\n📝 模式3：代码审查（结构化JSON）")
    print("-" * 40)
    review = structured_code_review(bad_code)
    print(f"  评分: {review['totalScore']}")
    print(f"  结论: {review['verdict']}")
    for issue in review.get('issues', [])[:2]:
        print(f"  ⚠ [{issue['severity']}] 第{issue.get('line','?')}行: {issue['description']}")
    
    print("\n" + "=" * 55)
    print("  🎉 全部完成！")
    print("=" * 55)
