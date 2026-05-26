"""第2天：结构化提示词模板实战"""

import requests
import json
import os

API_KEY = os.environ.get("DEEPSEEK_API_KEY")
if not API_KEY:
    with open(os.path.expanduser("~/.hermes/.env")) as f:
        for line in f:
            if "DEEPSEEK_API_KEY" in line:
                API_KEY = line.split("=", 1)[1].strip().strip('"').strip("'")
                break

URL = "https://api.deepseek.com/v1/chat/completions"


def call_deepseek(messages, temperature=0.3):
    headers = {
        "Authorization": f"Bearer {API_KEY}",
        "Content-Type": "application/json"
    }
    payload = {
        "model": "deepseek-chat",
        "messages": messages,
        "temperature": temperature,
        "max_tokens": 800
    }
    resp = requests.post(URL, headers=headers, json=payload, timeout=30)
    data = resp.json()
    return data["choices"][0]["message"]["content"]


# ========== 提示词模板库 ==========

def code_review_template(code, language="Java"):
    """代码审查模板"""
    return f"""你是一位{language}代码审查专家。

请审查以下代码，从以下维度给出建议：
1. 代码规范（命名、缩进、注释）
2. 性能问题
3. 安全隐患
4. 可维护性改进

代码：
```{language.lower()}
{code}
```

输出格式：用Markdown表格列出问题、严重程度（高/中/低）、修改建议。"""


def sql_template(description, tables):
    """SQL生成模板"""
    table_desc = "\n".join([f"- {t}" for t in tables])
    return f"""你是一位SQL专家。

需求：{description}

表结构：
{table_desc}

要求：
1. 考虑性能优化（索引使用）
2. 处理NULL值边界情况
3. 使用JOIN代替子查询（如果适用）

只输出SQL语句，不需要解释。"""


def api_design_template(endpoint, method, description):
    """API设计模板"""
    return f"""你是一位RESTful API设计专家。

请设计以下API端点：

端点：{endpoint}
方法：{method}
功能描述：{description}

要求：
1. 给出请求体JSON Schema
2. 给出成功响应JSON Schema
3. 给出错误响应JSON Schema
4. 标注HTTP状态码含义
5. 说明认证方式

输出格式：Markdown"""


# ========== 演示1：代码审查 ==========
print("=" * 65)
print("📝 模板演示1：代码审查")
print("=" * 65)

bad_code = """public class UserService {
    private String n;
    private int a;
    
    public void set(String n, int a) {
        this.n = n;
        this.a = a;
    }
    
    public void p() {
        System.out.println("Name: " + n + ", Age: " + a);
    }
}"""

print("待审查代码：")
print(bad_code)
print()

result = call_deepseek([
    {"role": "system", "content": "你是一位经验丰富的Java代码审查专家，要求严格，有实战经验。"},
    {"role": "user", "content": code_review_template(bad_code)}
])
print(result)


# ========== 演示2：SQL生成 ==========
print("\n\n" + "=" * 65)
print("📝 模板演示2：SQL生成")
print("=" * 65)

result2 = call_deepseek([
    {"role": "system", "content": "你是一位Oracle/MySQL专家，注重SQL性能优化。"},
    {"role": "user", "content": sql_template(
        "查询每个分类下销量前3的商品名称和销量",
        [
            "products(id, name, category_id, price, sales_count)",
            "categories(id, name)"
        ]
    )}
])
print(result2)


# ========== 演示3：API设计 ==========
print("\n\n" + "=" * 65)
print("📝 模板演示3：API设计")
print("=" * 65)

result3 = call_deepseek([
    {"role": "system", "content": "你是一位资深后端架构师，精通RESTful API设计。"},
    {"role": "user", "content": api_design_template(
        "/api/v1/orders",
        "POST",
        "创建新订单，包含商品列表、收货地址、支付方式"
    )}
])
# 内容较长，只显示开头
short = result3[:600] + "\n..." if len(result3) > 600 else result3
print(short)


# ========== 总结 ==========
print("\n\n" + "=" * 65)
print("💡 模板化提示词的好处")
print("=" * 65)
print("""
1. ✅ 一致性 — 每次用同样的结构，输出质量稳定
2. ✅ 可复用 — 一个模板可以在多个项目中使用
3. ✅ 可维护 — 修改模板就能影响所有输出的风格
4. ✅ 省 Token — 结构化的提示词比长篇大论更高效
5. ✅ 方便团队协作 — 模板本身就是"最佳实践"的文档
""")
