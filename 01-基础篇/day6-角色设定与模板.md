# 第6天：角色设定与模板

> **学习目标：** 掌握系统级提示词的角色设定技巧，学会编写结构化提示词模板，
>   提取可复用变量，构建个人提示词库
> **预计时间：** 2小时
> **代码语言：** Python + Java 双版本
> **前置知识：** 第4-5天（API 结构和参数调优）

---

## 📋 目录

1. [为什么需要角色设定与模板](#1-为什么需要角色设定与模板)
2. [系统级提示词（System Prompt）](#2-系统级提示词system-prompt)
3. [角色设定技巧](#3-角色设定技巧)
4. [提示词模板化](#4-提示词模板化)
5. [构建个人提示词库](#5-构建个人提示词库)
6. [动手实操](#6-动手实操)
7. [课堂练习](#7-课堂练习)
8. [今日小结](#8-今日小结)

---

## 1. 为什么需要角色设定与模板

### 1.1 没有角色 vs 有角色

```
❌ 无角色设定：
用户："这段代码有什么问题？"
模型："代码看起来还行，逻辑没什么大问题..."

✅ 有角色设定（代码审查专家）：
用户："这段代码有什么问题？"
模型："发现3个问题：
1. 空指针风险（第15行）：user.getName() 未判空
2. 性能问题（第28行）：for循环内调用数据库
3. 安全漏洞（第42行）：SQL拼接存在注入风险
建议修复方案：..."
```

### 1.2 模板的力量

**不用模板：** 每次重新写提示词 → 格式不统一、效果不稳定

**用模板：** 一套结构反复用 → 稳定的输出、少写代码

> **类比 Java 开发：** 就像从手写 SQL 到用 MyBatis 模板——模板让代码更规范、更可维护。

---

## 2. 系统级提示词（System Prompt）

### 2.1 System Prompt 和 User Prompt 的区别

| 维度 | System Prompt | User Prompt |
|------|---------------|-------------|
| **角色** | 设定模型的"人格" | 用户的输入 |
| **执行时机** | 每次对话都生效 | 仅当前轮次 |
| **优先级** | 基础规则，难以被用户覆盖 | 具体的任务指令 |
| **典型内容** | "你是一个资深 Java 架构师" | "帮我审查这段代码" |

### 2.2 好的 System Prompt 三要素

```
你是一个 [角色]，擅长 [技能]。
你的任务是 [职责]。
输出要求：[格式、风格、约束]。
```

**示例：**
```
你是一个资深 Java 代码审查专家，擅长发现代码中的性能、安全和可维护性问题。
你的任务是对用户提交的代码进行审查。
输出要求：
- 按严重程度排列问题（严重/中等/轻微）
- 每个问题标注行号、原因和修复建议
- 最后给出总体评分（1-10分）
```

### 2.3 角色模板库

| 角色 | 适用场景 | 关键指令 |
|------|----------|---------|
| **代码审查专家** | Review PR、找 Bug | 关注安全、性能、可维护性 |
| **架构师** | 系统设计、技术选型 | 考虑扩展性、成本、权衡 |
| **技术面试官** | 模拟面试、出题 | 考察深度、追问细节 |
| **翻译专家** | 中英互译、术语统一 | 保留格式、解释难点 |
| **API 文档写手** | 生成 API 文档 | 参数、返回值、异常 |
| **教学老师** | 解释概念、写教程 | 分步骤、用比喻、给出例子 |

---

## 3. 角色设定技巧

### 3.1 四步角色法

```
第1步：定义身份    → "你是一个资深 Java 架构师"
第2步：定义能力    → "精通 Spring Cloud、微服务设计、分布式系统"
第3步：定义任务    → "帮助用户设计高可用的微服务架构"
第4步：定义约束    → "用中文回答，优先考虑成本效益，给出至少2个方案对比"
```

### 3.2 角色设定对比实验

| 设定级别 | System Prompt 示例 | 效果 |
|----------|-------------------|------|
| 无角色 | （空） | 回答泛泛，可能跑偏 |
| 简单角色 | "你是一个程序员" | 有帮助，但不够专业 |
| 详细角色 | "你是一个有10年经验的Java架构师，精通Spring Cloud、高并发设计" | 回答有深度、有结构化 |
| 带格式约束 | 上面 + "用表格对比方案，每个方案给出优缺点" | 输出格式稳定，直接可用 |

---

## 4. 提示词模板化

### 4.1 模板结构

一个好的提示词模板应该包含**固定部分**和**变量部分**：

```
## 固定部分（角色设定）
{system_prompt}

## 变量部分（用户输入）
任务：{task}
上下文：{context}
输出格式：{format}
```

### 4.2 Python 模板实现

**Python版** — `prompt_templates.py`：

```python
"""
第6天：提示词模板库
"""
import os, json, time

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
        "system": "你是一个资深代码审查专家，精通 Java、Python、系统设计。\
按严重程度排列问题，每个问题标注行号、原因和修复建议。",
        "user": """请审查以下代码：
语言：{language}
代码：
```{language}
{code}
```"""
    },
    "translate": {
        "name": "中英翻译",
        "system": "你是一个专业中英翻译专家。\
保持原文格式和风格，对专业术语在括号中给出原文。",
        "user": """请将以下{source_lang}翻译成{target_lang}：

{text}"""
    },
    "api_design": {
        "name": "API 设计",
        "system": "你是一个 RESTful API 设计专家。\
返回接口的请求方法、URL、请求体、响应体、状态码。",
        "user": """请设计以下 API 接口：
需求描述：{description}
约束条件：{constraints}"""
    },
    "explain": {
        "name": "概念解释",
        "system": f"你是一个技术教学老师，擅长用比喻和例子解释复杂概念。\
用分步的方式解释，每个步骤给出代码示例。\
代码示例同时给出 Python 和 Java 版本。",
        "user": """请解释以下概念：
概念：{concept}
目标听众：{audience}
要求：{requirements}"""
    },
    "sql_generator": {
        "name": "SQL 生成",
        "system": "你是一个 SQL 专家。只输出 SQL 语句，不要任何解释。",
        "user": """数据库表结构：
{table_schema}
需求：{query_request}"""
    }
}


def call_template(template_name, **kwargs):
    """调用模板"""
    if template_name not in TEMPLATES:
        return f"模板 '{template_name}' 不存在"
    
    t = TEMPLATES[template_name]
    user_prompt = t["user"].format(**kwargs)
    
    resp = requests.post(
        "https://api.deepseek.com/chat/completions",
        headers={"Authorization": f"Bearer {API_KEY}", "Content-Type": "application/json"},
        json={
            "model": "deepseek-chat",
            "messages": [
                {"role": "system", "content": t["system"]},
                {"role": "user", "content": user_prompt}
            ],
            "temperature": 0.3,
            "max_tokens": 1024
        },
        timeout=30
    )
    return resp.json()["choices"][0]["message"]["content"]


# ===== 演示 =====
print("=" * 60)
print("实验1：代码审查模板")
print("=" * 60)

sample_code = '''def get_user(id):
    user = db.query("SELECT * FROM users WHERE id=" + id)
    return user.name
'''
result = call_template("code_review", language="python", code=sample_code)
print(result)

print("\n" + "=" * 60)
print("实验2：翻译模板")
print("=" * 60)
result2 = call_template("translate",
    source_lang="英文", target_lang="中文",
    text="The factory method pattern is a creational pattern that provides an interface for creating objects.")
print(result2)

print("\n" + "=" * 60)
print("实验3：SQL 生成模板")
print("=" * 60)
result3 = call_template("sql_generator",
    table_schema="users(id, name, email, created_at), orders(id, user_id, amount, status)",
    query_request="查询每个用户的总订单金额，按金额降序排列")
print(result3)
```

### 4.3 Java 模板实现

**Java版** — `PromptTemplates.java`：

```java
package ai.learning.day6;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.HashMap;

/**
 * 第6天：提示词模板库
 *
 * 运行前设置环境变量：
 * export DEEPSEEK_API_KEY=$(grep DEEPSEEK_API_KEY ~/.hermes/.env | cut -d= -f2 | tr -d '"')
 */
public class PromptTemplates {

    static final HttpClient client = HttpClient.newHttpClient();

    static Map<String, TemplateDef> templates = new HashMap<>();

    static class TemplateDef {
        String name;
        String system;
        String user;
        TemplateDef(String name, String system, String user) {
            this.name = name; this.system = system; this.user = user;
        }
    }

    static {
        templates.put("code_review", new TemplateDef("代码审查",
            "你是一个资深代码审查专家。按严重程度排列问题，标注行号、原因和修复建议。",
            "请审查以下代码：\n语言：{language}\n代码：\n```{language}\n{code}\n```"));

        templates.put("translate", new TemplateDef("翻译",
            "你是一个专业中英翻译专家。保持格式，术语括号标注原文。",
            "请将以下{source_lang}翻译成{target_lang}：\n{text}"));

        templates.put("explain", new TemplateDef("概念解释",
            "你是一个技术教学老师，擅长用比喻和例子解释复杂概念。用分步方式，给出 Python 和 Java 示例。",
            "解释以下概念：\n{concept}\n目标听众：{audience}"));
    }

    static String callApi(String system, String userPrompt) throws Exception {
        String body = String.format("""
            {"model":"deepseek-chat","messages":[
              {"role":"system","content":"%s"},
              {"role":"user","content":"%s"}
            ],"temperature":0.3,"max_tokens":1024}
            """, escape(system), escape(userPrompt));

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("https://api.deepseek.com/chat/completions"))
            .header("Authorization", "Bearer " + System.getenv("DEEPSEEK_API_KEY"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        HttpResponse<String> r = client.send(req, HttpResponse.BodyHandlers.ofString());
        return extractContent(r.body());
    }

    static String callTemplate(String name, Map<String, String> params) throws Exception {
        TemplateDef t = templates.get(name);
        if (t == null) return "模板不存在";
        String userPrompt = t.user;
        for (Map.Entry<String, String> e : params.entrySet()) {
            userPrompt = userPrompt.replace("{" + e.getKey() + "}", e.getValue());
        }
        return callApi(t.system, userPrompt);
    }

    static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    static String extractContent(String json) {
        int idx = json.indexOf("\"content\":\"");
        if (idx < 0) return "?";
        int start = idx + 11;
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char n = json.charAt(i + 1);
                if (n == 'n') { sb.append('\n'); i++; }
                else if (n == '"') { sb.append('"'); i++; }
                else { sb.append(c); }
            } else if (c == '"') break;
            else sb.append(c);
        }
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(60));
        System.out.println("代码审查模板");
        System.out.println("=".repeat(60));

        Map<String, String> p = new HashMap<>();
        p.put("language", "java");
        p.put("code", "public int calc(int a, int b) { return a / b; }");
        System.out.println(callTemplate("code_review", p));
    }
}
```

---

## 5. 构建个人提示词库

### 5.1 提示词库的目录结构

```
~/.hermes/prompts/
├── code-review.yaml        # 代码审查模板
├── translate.yaml          # 翻译模板
├── api-design.yaml         # API 设计模板
├── explain-concept.yaml    # 概念解释模板
├── sql-gen.yaml            # SQL 生成模板
└── custom/                 # 你的自定义模板
    └── my-project-review.yaml
```

### 5.2 模板文件格式（YAML）

```yaml
# ~/.hermes/prompts/code-review.yaml
name: 代码审查
version: 1.0
system: |
  你是一个资深代码审查专家，精通 Java、Python、系统设计。
  按严重程度排列问题，每个问题标注行号、原因和修复建议。
  最后给出总体评分（1-10分）。
user: |
  请审查以下代码：
  语言：{language}
  代码：
  ```{language}
  {code}
  ```
params:
  - name: language
    type: string
    description: 编程语言
  - name: code
    type: string
    description: 要审查的代码
```

### 5.3 提示词库管理工具

```python
"""
提示词库管理工具
"""
import os
import yaml

PROMPTS_DIR = os.path.expanduser("~/.hermes/prompts")

def list_prompts():
    """列出所有提示词模板"""
    prompts = []
    for root, dirs, files in os.walk(PROMPTS_DIR):
        for f in files:
            if f.endswith((".yaml", ".yml")):
                path = os.path.join(root, f)
                with open(path) as fh:
                    data = yaml.safe_load(fh)
                    prompts.append({
                        "name": data.get("name", f),
                        "file": path,
                        "version": data.get("version", "1.0"),
                        "params": [p["name"] for p in data.get("params", [])]
                    })
    return prompts

def get_prompt(name):
    """按名称获取模板内容"""
    for p in list_prompts():
        if p["name"] == name:
            return p
    return None
```

---

## 6. 动手实操

### 6.1 运行模板演示

```bash
cd ~/ai-learning/week1/code/day6
source ~/.hermes/hermes-agent/venv/bin/activate
python3 prompt_templates.py
```

**预期输出：**
- 代码审查模板 → 找出 SQL 注入漏洞，给出修复建议
- 翻译模板 → 准确翻译，术语保留原文
- SQL 模板 → 只输出 SQL，无废话

### 6.2 创建你的第一个模板

用你自己的项目场景，创建一个模板：

```python
my_template = {
    "name": "我的项目审查",
    "system": "你是一个熟悉我项目的架构师...",
    "user": "请帮我审查以下代码变更：\n{changes}"
}
```

---

## 7. 课堂练习

### 练习1：创建你自己的角色

写一个 System Prompt，让模型扮演**你的专属 AI 助手**，包含：
- 你知道我是谁（填空：____）
- 你擅长帮我做什么（3 个领域）
- 输出风格偏好

<details>
<summary>点击查看示例</summary>

```
system: |
  你是一个 AI 编程助手，你的用户是一名有 5 年经验的 Java 开发者。
  你擅长：
  1. 代码审查（关注安全性和性能）
  2. 技术方案设计（给出至少 2 个方案对比）
  3. Bug 排查（提供复现步骤和修复代码）
  输出风格：简洁、直接、用代码说话。
```
</details>

### 练习2：模板化你的日常任务

想想你工作中**最常问 ChatGPT/DeepSeek 的 3 个问题**，把它们写成可复用的模板。

<details>
<summary>点击查看答案</summary>

常见场景举例：
1. **代码 Review：** `review {code}` → 模板化后：`代码审查(code=...)`
2. **写单元测试：** `为{类名}写单元测试，覆盖{场景}` → `测试生成(class=..., scenarios=...)`
3. **解释报错：** `这个报错是什么意思？{error_log}` → `错误分析(error=...)`
</details>

---

## 8. 今日小结

### 核心概念速查

| 概念 | 一句话 | 关键要点 |
|------|--------|---------|
| **System Prompt** | 设定模型的"人格"和规则 | 角色+能力+任务+约束 |
| **角色设定** | 让模型扮演特定专家 | 越具体越专业 |
| **提示词模板** | 固定结构 + 变量插值 | 一次编写，反复使用 |
| **提示词库** | 管理所有模板的仓库 | YAML 定义，代码调用 |

### 角色设定的黄金四步

```
① 定义身份   → "你是一个资深 Java 架构师"
② 定义能力   → "精通 Spring Cloud、高并发设计"
③ 定义任务   → "帮助用户设计高可用微服务架构"
④ 定义约束   → "用中文回答，给出至少2个方案对比"
```

### 今日检查清单

- [ ] 理解 System Prompt 和 User Prompt 的区别
- [ ] 创建 1 个角色设定 System Prompt
- [ ] 运行 `prompt_templates.py` 体验模板效果
- [ ] 理解模板的固定部分 vs 变量部分
- [ ] 设计 1 个你自己的模板
- [ ] 在 `~/ai-learning/week1/notes/day6.md` 记录学习笔记

### 明天预告

**第 7 天：阶段复盘 📝**

- 整理第 1-6 天的学习笔记
- 总结提示词编写规范
- 构建个人提示词库 v1
- 补漏补缺
