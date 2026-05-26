# 第10天：结构化输出 📐

> **学习目标：** 掌握三种让大模型输出固定格式 JSON 的方法，学会用 Pydantic 验证输出，
>   实战从非结构化文本中提取结构化数据
> **预计时间：** 2.5小时
> **代码语言：** Python + Java 双版本
> **前置知识：** 第4天（API 调用）、第2天（提示词工程）

---

## 📋 目录

1. [为什么需要结构化输出？](#1-为什么需要结构化输出)
2. [方法一：Prompt 约束（手工法）](#2-方法一prompt-约束手工法)
3. [方法二：JSON Mode（API 原生）](#3-方法二json-modeapi-原生)
4. [方法三：Function Calling（最强约束）](#4-方法三function-calling最强约束)
5. [Pydantic + LLM 最佳实践](#5-pydantic--llm-最佳实践)
6. [实战：从简历中提取结构化信息](#6-实战从简历中提取结构化信息)
7. [课堂练习](#7-课堂练习)
8. [今日小结](#8-今日小结)

---

## 1. 为什么需要结构化输出？

### 大模型默认输出是"散文"

当你问大模型"提取张三的姓名、年龄、职业"，它可能回答：

```
根据您提供的信息，张三是一位28岁的软件工程师。
他的姓名是张三，今年28岁，从事软件工程师的工作。
```

这对人类来说没问题，但对程序来说——**没法用**。

### 结构化输出的价值

```
非结构化文本                         结构化 JSON
─────────────────                  ────────────────
"张三，28岁，软件工程师"    ──→    {"name": "张三",
"月薪15000元，本科毕业"              "age": 28,
"精通Python、Java"                  "job": "软件工程师",
                                    "salary": 15000,
                                    "skills": ["Python", "Java"]}
                                         ↓
                                   ✅ 可直接写入数据库
                                   ✅ 可直接传给前端
                                   ✅ 可直接做数据分析
```

### 三种方法的对比

| 方法 | 可靠性 | 实现复杂度 | 适用场景 |
|------|--------|-----------|---------|
| **① Prompt 约束** | ⭐⭐ 低 | ⭐ 最简单 | 快速原型、非关键场景 |
| **② JSON Mode** | ⭐⭐⭐ 中 | ⭐⭐ 简单 | 一般数据提取 |
| **③ Function Calling** | ⭐⭐⭐⭐⭐ 高 | ⭐⭐⭐ 中等 | 生产环境、关键数据 |

---

## 2. 方法一：Prompt 约束（手工法）

最原始的方法：在 prompt 里告诉模型"返回 JSON"。

### 代码实现

```python
import json, requests

def extract_with_prompt(text: str) -> dict:
    """通过 Prompt 让模型返回 JSON"""
    prompt = f"""从以下文本中提取信息，返回 JSON 格式（不要加其他文字）：

文本：{text}

要求的 JSON 格式：
{{
    "name": "姓名",
    "age": "年龄（数字）",
    "job": "职业"
}}"""

    resp = requests.post(
        "https://api.deepseek.com/v1/chat/completions",
        headers={"Authorization": f"Bearer {API_KEY}"},
        json={
            "model": "deepseek-chat",
            "messages": [{"role": "user", "content": prompt}],
            "temperature": 0
        }
    )
    content = resp.json()["choices"][0]["message"]["content"]

    # 手动清理可能多余的文本
    # 有时模型会说"以下是提取结果："之类的废话
    content = content.strip()
    if content.startswith("```"):
        content = content.split("```")[1]
        if content.startswith("json"):
            content = content[4:]
    if content.startswith("{"):
        return json.loads(content)
    else:
        # 尝试找 JSON 部分
        start = content.find("{")
        end = content.rfind("}") + 1
        return json.loads(content[start:end])
```

### 问题

| 问题 | 表现 |
|------|------|
| **格式不稳定** | 有时加说明文字，有时用 markdown 代码块 |
| **字段名不一致** | 有时 `"name"` 有时 `"姓名"` |
| **类型不保证** | `"age"` 可能返回 `"28岁"` 而不是 `28` |
| **缺少字段** | 没有 strong 约束，可能漏掉必填字段 |

> **结论：** 能用，但不可靠。适合自己用，不适合生产环境。

---

## 3. 方法二：JSON Mode（API 原生）

大多数主流 LLM API 提供了 `response_format` 参数，强制模型返回 JSON。

### DeepSeek / OpenAI 的 JSON Mode

```python
def extract_with_json_mode(text: str) -> dict:
    """使用 JSON Mode 让模型返回有效 JSON"""
    resp = requests.post(
        "https://api.deepseek.com/v1/chat/completions",
        headers={"Authorization": f"Bearer {API_KEY}"},
        json={
            "model": "deepseek-chat",
            "messages": [
                {
                    "role": "system",
                    "content": "你是一个信息提取助手。请从用户输入中提取信息，返回 JSON 格式。"
                },
                {
                    "role": "user",
                    "content": f"从以下文本提取姓名、年龄、职业：\n\n{text}"
                }
            ],
            "response_format": {"type": "json_object"},  # ← 关键参数
            "temperature": 0
        }
    )
    content = resp.json()["choices"][0]["message"]["content"]
    return json.loads(content)  # JSON Mode 保证是有效 JSON
```

### JSON Mode 的规则

```
┌─────────────────────────────────────────────┐
│          JSON Mode 使用规则                    │
│                                              │
│  1. system message 中必须包含"JSON"字样        │
│     → 告诉模型你要 JSON 输出                   │
│                                              │
│  2. 模型保证输出是有效 JSON                     │
│     → 不会被 markdown 代码块包裹                │
│     → 不会有多余的说明文字                      │
│                                              │
│  3. 但不保证字段结构                            │
│     → 字段名可能不是你想要的                     │
│     → 可能有额外字段或缺少字段                   │
│                                              │
│  4. 不保证类型正确                              │
│     → "age" 可能是 "28" (str) 而非 28 (int)    │
└─────────────────────────────────────────────┘
```

### 对比：有 JSON Mode vs 没有

```python
# 🚫 没有 JSON Mode（Prompt 约束）
# 可能输出：
# "以下是提取结果：\n```json\n{\"姓名\": \"张三\", \"年龄\": \"28岁\"}\n```"
# → 需要手动清理

# ✅ 有 JSON Mode
# 保证输出：
# {"姓名": "张三", "年龄": 28, "职业": "软件工程师"}
# → 直接 json.loads()
```

### 测试运行

```python
extract_with_json_mode("张三，28岁，是一名软件工程师，月薪15000元")
# → {"姓名": "张三", "年龄": 28, "职业": "软件工程师", "月薪": 15000}

extract_with_json_mode("我叫李四，今年35岁，在北京做产品经理")
# → {"姓名": "李四", "年龄": 35, "职业": "产品经理", "城市": "北京"}
```

> **注意：** 字段名（`姓名` vs `name`）由模型决定。
> 在 system prompt 中给出明确的 schema 示例可以大幅提升一致性。

---

## 4. 方法三：Function Calling（最强约束）

当你需要**字段名绝对确定**、**类型绝对正确**、**必填字段不能缺失**时，用 Function Calling。

### 核心思路

把"结构化输出"伪装成一个函数调用：

```json
{
  "type": "function",
  "function": {
    "name": "extract_person_info",
    "description": "提取人物信息",
    "parameters": {
      "type": "object",
      "properties": {
        "name": {"type": "string"},
        "age": {"type": "integer"},
        "job": {"type": "string"}
      },
      "required": ["name", "age"]   // ← 必填字段
    }
  }
}
```

然后用 `tool_choice` **强制调用这个函数**：

```python
tool_choice = {"type": "function", "function": {"name": "extract_person_info"}}
```

模型**只能**按 schema 输出，没有自由发挥空间。

### 代码实现

```python
def extract_with_function_calling(text: str) -> dict:
    """使用 Function Calling 强制输出结构化 JSON"""

    SCHEMA = {
        "name": "extract_person_info",
        "description": "从文本中提取人物信息",
        "parameters": {
            "type": "object",
            "properties": {
                "name": {"type": "string", "description": "姓名"},
                "age": {"type": "integer", "description": "年龄"},
                "job": {"type": "string", "description": "职业"},
                "city": {"type": "string", "description": "所在城市"},
                "email": {"type": "string", "description": "邮箱地址"},
                "skills": {
                    "type": "array",
                    "items": {"type": "string"},
                    "description": "技能列表"
                }
            },
            "required": ["name", "age", "job"]
        }
    }

    resp = requests.post(
        "https://api.deepseek.com/v1/chat/completions",
        headers={"Authorization": f"Bearer {API_KEY}"},
        json={
            "model": "deepseek-chat",
            "messages": [
                {"role": "system", "content": "从用户输入中提取结构化信息。"},
                {"role": "user", "content": text}
            ],
            "tools": [{"type": "function", "function": SCHEMA}],
            "tool_choice": {                          # ← 强制调用
                "type": "function",
                "function": {"name": "extract_person_info"}
            },
            "temperature": 0
        }
    )

    msg = resp.json()["choices"][0]["message"]
    args = json.loads(msg["tool_calls"][0]["function"]["arguments"])
    return args
```

### 三种方法的 JSON Schema 对比

```yaml
Prompt 约束:
  字段名: 不确定 (模型自由发挥)
  类型: 不确定 (可能是字符串)
  必填字段: 不保证
  额外字段: 可能有

JSON Mode:
  字段名: 受 system prompt 影响
  类型: 不保证 (age 可能返回 "28")
  必填字段: 不保证
  额外字段: 可能有

Function Calling:
  字段名: ✅ 由 schema 定义 (model.name 一定叫 "name")
  类型: ✅ 由 schema 约束 (integer 一定是数字)
  必填字段: ✅ required 中列出的必须提供
  额外字段: ❌ schema 之外的字段不会出现
```

---

## 5. Pydantic + LLM 最佳实践

在生产环境中，你不仅想让模型输出 JSON，还要 **验证 JSON 的结构和类型**。

### Python：Pydantic

```python
from pydantic import BaseModel, Field, ValidationError
from typing import List, Optional


class PersonInfo(BaseModel):
    """人物信息模型"""
    name: str = Field(description="姓名")
    age: int = Field(description="年龄", ge=0, le=150)
    job: Optional[str] = Field(default=None, description="职业")
    city: Optional[str] = Field(default=None, description="所在城市")
    email: Optional[str] = Field(default=None, description="邮箱")
    skills: List[str] = Field(default=[], description="技能列表")


def extract_with_pydantic(text: str) -> PersonInfo:
    """使用 Pydantic 验证结构化输出"""
    raw = extract_with_json_mode(text)  # 或 extract_with_function_calling(text)

    try:
        # Pydantic 自动验证类型和约束
        person = PersonInfo(**raw)
        return person
    except ValidationError as e:
        print(f"❌ 验证失败: {e}")
        print("尝试修复...")

        # 第二种策略：让模型自己修复
        fix_prompt = f"""以下 JSON 验证失败：
{raw}

错误信息：
{e}

请返回修正后的 JSON，确保：
- age 必须是整数
- 字段名使用英文"""

        raw = extract_with_json_mode(fix_prompt)
        return PersonInfo(**raw)
```

### Pydantic 自动验证了什么

```python
# ✅ 正确输入
PersonInfo(name="张三", age=28, job="工程师")
# → 正常创建

# ❌ 类型错误
PersonInfo(name="张三", age="二十八", job="工程师")
# → ValidationError: age 应为整数

# ❌ 范围错误
PersonInfo(name="张三", age=200, job="工程师")
# → ValidationError: age 最大150

# ❌ 缺少必填字段
PersonInfo(name="张三")
# → ValidationError: age 是必填的

# ✅ 可选字段可以缺失
PersonInfo(name="张三", age=28)
# → name="张三", age=28, job=None, skills=[]
```

### 验证策略

```python
def safe_extract(text: str, max_retries: int = 2) -> Optional[PersonInfo]:
    """带重试的安全提取"""
    for attempt in range(max_retries):
        try:
            raw = extract_with_json_mode(text)
            return PersonInfo(**raw)
        except (json.JSONDecodeError, ValidationError) as e:
            if attempt == max_retries - 1:
                print(f"❌ 重试 {max_retries} 次仍失败")
                return None
            # 让模型知道哪里错了
            raw = extract_with_json_mode(
                f"之前的输出验证失败：{e}\n请修正。原文：{text}"
            )
    return None
```

---

## 6. 实战：从简历中提取结构化信息

### 简历文本示例

```text
个人简历

姓名：王小明
电话：138-0000-1234
邮箱：wangxm@example.com
学历：硕士研究生
毕业院校：清华大学
专业：计算机科学与技术

工作经历：
2020.07 - 2023.06  阿里巴巴  高级后端工程师
  - 负责电商平台的订单系统开发
  - 使用 Java、Spring Cloud、Redis
  - 系统日处理订单量 100 万+

2023.07 - 至今     字节跳动  技术专家
  - 主导微服务架构升级
  - 团队规模 10 人
  - 引入 Kubernetes 和 Docker

技能：Java、Python、Kubernetes、Docker、Redis、MySQL、Spring Cloud、微服务架构
证书：PMP、AWS 认证解决方案架构师
```

### 期望的结构化输出

```json
{
  "name": "王小明",
  "phone": "138-0000-1234",
  "email": "wangxm@example.com",
  "education": {
    "degree": "硕士研究生",
    "school": "清华大学",
    "major": "计算机科学与技术"
  },
  "work_experience": [
    {
      "company": "阿里巴巴",
      "period": "2020.07 - 2023.06",
      "position": "高级后端工程师",
      "responsibilities": ["负责电商平台的订单系统开发", "系统日处理订单量 100 万+"],
      "technologies": ["Java", "Spring Cloud", "Redis"]
    },
    {
      "company": "字节跳动",
      "period": "2023.07 - 至今",
      "position": "技术专家",
      "responsibilities": ["主导微服务架构升级", "团队规模 10 人"],
      "technologies": ["Kubernetes", "Docker"]
    }
  ],
  "skills": ["Java", "Python", "Kubernetes", "Docker", "Redis", "MySQL", "Spring Cloud", "微服务架构"],
  "certifications": ["PMP", "AWS 认证解决方案架构师"]
}
```

### 完整代码

```python
"""
resume_extractor.py — 从简历文本中提取结构化信息

使用 Function Calling 强制输出固定格式，
使用 Pydantic 验证输出类型。
"""

import json, os, requests
from pydantic import BaseModel, Field
from typing import List, Optional


# ─── 数据模型 ────────────────────────────────

class Education(BaseModel):
    degree: str = Field(description="学历")
    school: str = Field(description="毕业院校")
    major: str = Field(description="专业")

class WorkExperience(BaseModel):
    company: str = Field(description="公司名称")
    period: str = Field(description="工作时间段")
    position: str = Field(description="职位")
    responsibilities: List[str] = Field(default=[], description="工作职责")
    technologies: List[str] = Field(default=[], description="使用的技术")

class Resume(BaseModel):
    name: str = Field(description="姓名")
    phone: Optional[str] = Field(default=None, description="电话")
    email: Optional[str] = Field(default=None, description="邮箱")
    education: Optional[Education] = Field(default=None, description="教育背景")
    work_experience: List[WorkExperience] = Field(default=[], description="工作经历")
    skills: List[str] = Field(default=[], description="技能列表")
    certifications: List[str] = Field(default=[], description="证书")
    summary: Optional[str] = Field(default=None, description="个人总结")


# ─── 提取器 ──────────────────────────────────

class ResumeExtractor:
    """简历信息提取器"""

    EXTRACTION_SCHEMA = {
        "name": "extract_resume",
        "description": "从简历文本中提取结构化信息",
        "parameters": {
            "type": "object",
            "properties": {
                "name": {"type": "string"},
                "phone": {"type": "string"},
                "email": {"type": "string"},
                "education": {
                    "type": "object",
                    "properties": {
                        "degree": {"type": "string"},
                        "school": {"type": "string"},
                        "major": {"type": "string"}
                    }
                },
                "work_experience": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "company": {"type": "string"},
                            "period": {"type": "string"},
                            "position": {"type": "string"},
                            "responsibilities": {
                                "type": "array", "items": {"type": "string"}
                            },
                            "technologies": {
                                "type": "array", "items": {"type": "string"}
                            }
                        }
                    }
                },
                "skills": {
                    "type": "array", "items": {"type": "string"}
                },
                "certifications": {
                    "type": "array", "items": {"type": "string"}
                },
                "summary": {"type": "string"}
            },
            "required": ["name", "work_experience", "skills"]
        }
    }

    def __init__(self, api_key: str):
        self.api_key = api_key

    def extract(self, text: str) -> Optional[Resume]:
        """提取简历信息 → Pydantic 验证"""
        raw = self._call_llm(text)
        try:
            return Resume(**raw)
        except Exception as e:
            print(f"⚠️ 验证失败: {e}")
            print(f"原始输出: {json.dumps(raw, ensure_ascii=False)[:200]}")
            return None

    def _call_llm(self, text: str) -> dict:
        resp = requests.post(
            "https://api.deepseek.com/v1/chat/completions",
            headers={"Authorization": f"Bearer {self.api_key}"},
            json={
                "model": "deepseek-chat",
                "messages": [
                    {"role": "system", "content": "你是一个简历信息提取助手，从用户提供的简历文本中提取结构化信息。"},
                    {"role": "user", "content": text}
                ],
                "tools": [{"type": "function", "function": self.EXTRACTION_SCHEMA}],
                "tool_choice": {
                    "type": "function",
                    "function": {"name": "extract_resume"}
                },
                "temperature": 0
            },
            timeout=30
        )
        msg = resp.json()["choices"][0]["message"]
        return json.loads(msg["tool_calls"][0]["function"]["arguments"])


# ─── 主程序 ──────────────────────────────────

def main():
    api_key = os.environ.get("DEEPSEEK_API_KEY", "")
    if not api_key:
        print("⚠️  请设置 DEEPSEEK_API_KEY")
        return

    extractor = ResumeExtractor(api_key)

    resume_text = """
个人简历

姓名：王小明
电话：138-0000-1234
邮箱：wangxm@example.com
学历：硕士研究生
毕业院校：清华大学
专业：计算机科学与技术

工作经历：
2020.07 - 2023.06  阿里巴巴  高级后端工程师
  - 负责电商平台的订单系统开发
  - 使用 Java、Spring Cloud、Redis
  - 系统日处理订单量 100 万+

2023.07 - 至今     字节跳动  技术专家
  - 主导微服务架构升级
  - 团队规模 10 人
  - 引入 Kubernetes 和 Docker

技能：Java、Python、Kubernetes、Docker、Redis、MySQL、Spring Cloud、微服务架构
证书：PMP、AWS 认证解决方案架构师
    """.strip()

    print("📄 正在提取简历信息...")
    resume = extractor.extract(resume_text)

    if resume:
        print(f"\n✅ 提取成功！")
        print(f"\n👤 姓名: {resume.name}")
        print(f"📧 邮箱: {resume.email}")
        print(f"🎓 学历: {resume.education.degree} ({resume.education.school})")
        print(f"\n💼 工作经历 ({len(resume.work_experience)} 段):")
        for exp in resume.work_experience:
            print(f"   {exp.period}  {exp.company}  {exp.position}")
        print(f"\n🔧 技能 ({len(resume.skills)} 项):")
        print(f"   {', '.join(resume.skills)}")
        print(f"\n📜 证书: {', '.join(resume.certifications)}")

        # 导出 JSON
        print(f"\n📦 JSON 输出:")
        print(json.dumps(resume.model_dump(), ensure_ascii=False, indent=2))
    else:
        print("❌ 提取失败")


if __name__ == "__main__":
    main()
```

---

## 7. 课堂练习

### 练习1：三种方法对比

用同样的输入运行三种方法，观察输出差异：

```python
text = "我叫张三，28岁，软件工程师，精通Python和Java，邮箱zhangsan@email.com"

# 方法1: Prompt 约束
# 方法2: JSON Mode
# 方法3: Function Calling
```

<details>
<summary>点击查看对比结果</summary>

**Prompt 约束：**
```json
{"name": "张三", "age": "28", "job": "software engineer", "skills": ["Python", "Java"]}
```
→ 字段名可能是英文/中文混搭，age 可能是字符串

**JSON Mode：**
```json
{"姓名": "张三", "年龄": 28, "职业": "软件工程师", "技能": ["Python", "Java"]}
```
→ 字段名可能是中文，但 age 类型正确

**Function Calling：**
```json
{"name": "张三", "age": 28, "job": "软件工程师", "skills": ["Python", "Java"], "email": "zhangsan@email.com"}
```
→ 字段名固定、类型正确、结构一致
</details>

### 练习2：自定义提取 Schema

为以下场景设计结构化输出 Schema：

```
场景A: 从商品评论中提取：评分、优点、缺点、推荐度
场景B: 从新闻中提取：标题、时间、地点、关键人物、事件摘要
场景C: 从会议记录中提取：会议主题、参会人、决策、待办事项
```

<details>
<summary>点击查看参考 Schema</summary>

场景A - 商品评论：
```json
{
  "name": "extract_review",
  "parameters": {
    "properties": {
      "rating": {"type": "integer", "description": "评分 1-5"},
      "pros": {"type": "array", "items": {"type": "string"}},
      "cons": {"type": "array", "items": {"type": "string"}},
      "recommendation": {"type": "string", "enum": ["推荐", "不推荐", "中立"]}
    },
    "required": ["rating", "recommendation"]
  }
}
```
</details>

### 练习3：批量处理

```python
# 批量提取多条记录
texts = [
    "联系人：李四，电话：13900005678，部门：技术部",
    "王五，项目经理，5年经验，PMP认证",
    "赵六，26岁，前端开发，React/Vue"
]

for text in texts:
    info = extract_with_function_calling(text)
    print(f"{info['name']:4s} | {info.get('job', 'N/A'):8s} | ...")
```

### 练习4：错误恢复

修改 `safe_extract` 函数，当模型输出不符合 schema 时：
1. 记录错误原因
2. 让模型基于错误信息修正输出
3. 最多重试 3 次
4. 如果仍失败，返回部分有效数据

---

## 8. 今日小结

### 核心概念速查

| 概念 | 一句话 | 可靠性 |
|------|--------|--------|
| **Prompt 约束** | 告诉模型"返回 JSON" | ⭐⭐ |
| **JSON Mode** | API 原生 `response_format` | ⭐⭐⭐ |
| **Function Calling** | 用 tool_choice 强制调用 | ⭐⭐⭐⭐⭐ |
| **Pydantic 验证** | 自动检查类型和结构 | ✅ 必选补充 |
| **重试策略** | 验证失败时让模型修正 | 🛡️ 最后的防线 |

### 结构化输出决策树

```
需要结构化输出？
├─ 快速验证概念 → Prompt 约束（最简单）
├─ 一般场景 → JSON Mode（够用）
└─ 生产环境 → Function Calling（最可靠）
    ├─ 还需要类型检查？→ 加 Pydantic
    └─ 还需要错误恢复？→ 加重试策略
```

### 最佳实践总结

1. **永远用 Function Calling 做结构化输出** — 字段名、类型、必填都有保证
2. **Pydantic 是必选项** — 模型也可能犯错，验证是最后一道防线
3. **重试机制要加** — 一次不行就告诉模型哪里错了，让它重试
4. **temperature=0** — 结构化输出不需要创意，0 最稳定
5. **description 写清楚** — schema 中每个字段的 description 帮助模型理解
6. **复杂结构用嵌套** — 对象里套对象、数组，schema 表达能力很强

### 今日检查清单

- [ ] 理解三种结构化输出方法的区别
- [ ] 运行 JSON Mode 示例
- [ ] 运行 Function Calling 结构化输出
- [ ] 实现 Pydantic 验证
- [ ] 运行简历提取器
- [ ] 练习 2：设计自己的 Schema
- [ ] 练习 4：实现错误恢复
- [ ] 在 `~/ai-learning/week2/notes/day10.md` 记录学习笔记

### 明天预告

**第 11 天：多轮对话与状态管理 💬**

- 对话状态设计模式
- 记忆管理（滑动窗口 vs 摘要 vs 向量记忆）
- 实战：构建"对话式"智能客服

---

> 📝 **学习笔记：** 在 `~/ai-learning/week2/notes/day10.md` 记录今天的收获
> ❓ **遇到问题：** 随时问我
> 🚀 **学有余力：** 用 Pydantic 写一个"会议纪要提取器"，从一段会议录音转文字中提取待办事项和负责人
> 💡 **思考：** 结构化输出是把大模型从"聊天框"接入"工程系统"的关键桥梁
