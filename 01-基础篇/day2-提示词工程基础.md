# 第2天：提示词工程基础

> **学习目标：** 理解 Zero-shot 和 Few-shot 两种提示策略，掌握结构化提示词的基本写法，
>   亲眼看到——同一个模型、同一问题，不同提示词 → 完全不同的输出质量。
> **预计时间：** 2小时
> **代码语言：** Python + Java 双版本
> **API 要求：** DeepSeek API（已配置）

---

## 📋 目录

1. [什么是提示词工程](#1-什么是提示词工程)
2. [Zero-shot 提示（零样本）](#2-zero-shot-提示零样本)
3. [Few-shot 提示（少样本）](#3-few-shot-提示少样本)
4. [结构化提示词模板](#4-结构化提示词模板)
5. [动手实操](#5-动手实操)
6. [课堂练习](#6-课堂练习)
7. [今日小结](#7-今日小结)

---

## 1. 什么是提示词工程

### 1.1 为什么提示词如此重要？

同一个模型，同一个问题 — 只因为**提问方式不同**，回答质量天差地别：

```
❌ 差提示词：           "帮我写段代码"
    → 可能给你 Hello World，不是你想要的

✅ 好提示词：           "你是一个Java专家。请用Spring Boot 3.x + Spring AI实现
                        一个RAG知识库的文档上传接口，要求包含文件类型校验、
                        分片处理和元数据提取。输出完整代码。"
    → 直接拿到可用的生产级代码
```

**核心原理：** 大模型训练时见过海量数据，你的提示词就是"搜索关键词"。
好的提示词能精准定位到它见过的最佳答案。

### 1.2 三个黄金原则

| 原则 | 说明 | 反例 | 正例 |
|------|------|------|------|
| **角色设定** | 告诉模型"你是谁" | "翻译这个" | "你是一位资深中英翻译专家" |
| **任务明确** | 说清楚要做什么 | "解释一下RAG" | "用500字以内，给Java开发者解释RAG是什么" |
| **格式约束** | 指定输出格式 | "列出优缺点" | "用Markdown表格列出3个优点和3个缺点" |

### 1.3 两种核心策略

```
Zero-shot（零样本）：
  用户：将这个句子翻译成英文："今天天气很好"
  模型：The weather is great today.
  → 没有给例子，模型直接做

Few-shot（少样本）：
  用户：将以下中文翻译成英文：
        示例1：我很开心 → I am very happy
        示例2：今天很冷 → It is cold today
        现在翻译：今天天气很好
  模型：The weather is great today.
  → 给了2个例子，模型按例子的模式输出
```

---

## 2. Zero-shot 提示（零样本）

### 2.1 定义

> Zero-shot = 零样本 = 不给任何示例，直接让模型完成任务。

模型依靠训练时学到的知识来理解你的意图。

### 2.2 什么时候用？

| 适合 | 不适合 |
|------|--------|
| 简单、通用的任务 | 需要特定格式/风格的任务 |
| 模型训练数据中常见的任务 | 生僻、专业领域的任务 |
| 快速原型验证 | 需要稳定一致性输出的生产环境 |

### 2.3 实战对比

同一个问题，三种不同的 Zero-shot 写法：

```
编写一个Java方法，将字符串反转

写法A（太简单、太模糊）：
  "反转字符串"
  → 可能只用 StringBuilder.reverse()，没有任何说明

写法B（清晰但缺乏约束）：
  "写一个Java方法反转字符串，包含主方法调用"
  → 会给代码，但可能没有注释和异常处理

写法C（结构化的Zero-shot）：
  "你是一个Java专家。请编写一个反转字符串的方法：
   1. 方法签名：public static String reverse(String input)
   2. 处理 null 和空字符串的边界情况
   3. 用两种方式实现：StringBuilder 和 双指针
   4. 添加中文注释
   5. 包含 main 方法测试"
  → 拿到完整、健壮、带测试的代码
```

---

## 3. Few-shot 提示（少样本）

### 3.1 定义

> Few-shot = 少样本 = 在提示词中给出几个示例，让模型"照猫画虎"。

模型会从示例中**推断出模式**，然后应用到新的输入上。

### 3.2 示例数量怎么选？

| 示例数 | 优点 | 缺点 |
|--------|------|------|
| **1-shot** | 给个方向就够了 | 可能理解偏 |
| **3-shot** | 模式清晰 | 要写3个例子 |
| **5-shot** | 模式非常明确 | 占 Token 多 |
| **10+ shot** | 极高一致性 | 成本高 |

**经验法则：** 大部分场景 3-shot 就够了。如果 3-shot 还不行，说明这个任务不适合 Few-shot。

### 3.3 Few-shot 的三种用法

**用法1：格式引导**
```
用户评论情感分析：

评论：这个产品太棒了，物流也快！→ 正面
评论：质量太差，用一次就坏了。→ 负面
评论：一般般吧，不好不坏。→ 中性

现在分析：客服态度很好，但价格有点贵。
模型：→ 正面（识别出"态度很好"是正面信号）
```

**用法2：推理过程引导（Chain-of-Thought，第二天会深入）**
```
问题：小明有5个苹果，给了小红2个，又买了3个，现在有几个？
解答：5 - 2 + 3 = 6个

问题：书店有20本书，卖了8本，又进了12本，现在有几本？
解答：
模型：20 - 8 + 12 = 24本
```

**用法3：输出格式约束**
```
请将以下内容格式化为JSON：

输入：张三，25岁，Java开发工程师
输出：{"name": "张三", "age": 25, "job": "Java开发工程师"}

输入：李四，30岁，产品经理
输出：
```

---

## 4. 结构化提示词模板

结合 Zero-shot 和 Few-shot 的优点，形成以下通用模板：

### 4.1 通用结构化模板

```
# 角色
你是一位{领域}专家

# 任务
请帮我{具体任务描述}

# 要求
1. {要求1}
2. {要求2}
3. {要求3}

# 输出格式
{指定格式：Markdown/JSON/代码/表格...}

# 示例（可选 - Few-shot 部分）
{示例1}
{示例2}
```

### 4.2 针对 Java 开发者的实用模板

**代码生成模板：**
```
你是一位资深Java/Spring Boot开发专家。

请帮我实现一个{功能描述}。

要求：
1. 使用Spring Boot 3.x + {技术栈}
2. 包含完整的异常处理
3. 添加中文注释
4. 输出为可以直接运行的代码

输出格式：
- 每个文件单独用代码块标注文件名
```

**代码审查模板：**
```
你是一位Java代码审查专家。

请审查以下代码，从以下维度给出建议：
1. 代码规范（命名/缩进/注释）
2. 性能问题
3. 安全隐患
4. 可维护性

代码：
```java
{你的代码}
```

输出格式：用Markdown表格列出问题、严重程度、修改建议。
```

---

## 5. 动手实操

### 5.1 环境准备

**Python 版：**

```bash
pip install requests
```

**Java 版：**

无需额外依赖，使用 JDK 11+ 内置的 `java.net.http.HttpClient`。

### 5.2 实验1：Zero-shot 对比

**Python 版** — 创建 `zero_shot_demo.py`：

```python
import requests
import json
import os

API_KEY = os.environ.get("DEEPSEEK_API_KEY", "your-api-key")
URL = "https://api.deepseek.com/v1/chat/completions"

def call_deepseek(system_prompt, user_message):
    """调用 DeepSeek API"""
    headers = {
        "Authorization": f"Bearer {API_KEY}",
        "Content-Type": "application/json"
    }
    payload = {
        "model": "deepseek-chat",
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_message}
        ],
        "temperature": 0.3,  # 低温度，更确定性
        "max_tokens": 500
    }
    resp = requests.post(URL, headers=headers, json=payload, timeout=30)
    return resp.json()["choices"][0]["message"]["content"]


# === 实验：同一个问题，三种不同的 Zero-shot 提示 ===

question = "请用Java写一个方法，判断一个字符串是否是回文。"

# 实验A：最简提示（不好）
print("=" * 60)
print("🔴 实验A：最简提示 — '判断回文'")
print("=" * 60)
result_a = call_deepseek("你是一个助手。", question)
print(result_a)
print()

# 实验B：加角色设定（中等）
print("=" * 60)
print("🟡 实验B：角色设定 — '你是一位Java专家'")
print("=" * 60)
result_b = call_deepseek(
    "你是一位资深Java开发工程师，精通算法和数据结构。回答要简洁专业。",
    question
)
print(result_b)
print()

# 实验C：结构化提示（最好）
print("=" * 60)
print("🟢 实验C：结构化提示 — 有角色、有要求、有格式")
print("=" * 60)
structured_prompt = """请用Java实现判断回文字符串的方法。

要求：
1. 方法签名：public static boolean isPalindrome(String s)
2. 忽略大小写和非字母数字字符
3. 处理 null 和空字符串
4. 用双指针实现，不要用 StringBuilder.reverse()
5. 添加详细的中文注释
6. 包含 main 方法测试多个用例

输出格式：
- 先给出完整代码
- 然后解释算法思路（100字以内）"""
result_c = call_deepseek("你是一位精通Java和算法的专家。", structured_prompt)
print(result_c)
```

运行：
```bash
python zero_shot_demo.py
```

**Java 版** — 创建 `ZeroShotDemo.java`：

```java
package ai.learning.day2;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ZeroShotDemo {
    static final String API_KEY = System.getenv().getOrDefault("DEEPSEEK_API_KEY", "your-api-key");
    static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    static String callDeepSeek(String systemPrompt, String userMessage) throws Exception {
        String json = """
            {
                "model": "deepseek-chat",
                "messages": [
                    {"role": "system", "content": "%s"},
                    {"role": "user", "content": "%s"}
                ],
                "temperature": 0.3,
                "max_tokens": 500
            }
            """.formatted(
                systemPrompt.replace("\"", "\\\"").replace("\n", "\\n"),
                userMessage.replace("\"", "\\\"").replace("\n", "\\n")
            );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.deepseek.com/v1/chat/completions"))
                .header("Authorization", "Bearer " + API_KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        // 简单解析 JSON 提取 content
        String body = resp.body();
        int contentStart = body.indexOf("\"content\":\"") + 11;
        int contentEnd = body.indexOf("\"", contentStart);
        return body.substring(contentStart, contentEnd)
                   .replace("\\n", "\n").replace("\\\"", "\"");
    }

    public static void main(String[] args) throws Exception {
        String question = "请用Java写一个方法，判断一个字符串是否是回文。";

        // 实验A：最简提示
        System.out.println("=".repeat(60));
        System.out.println("🔴 实验A：最简提示 — '判断回文'");
        System.out.println("=".repeat(60));
        System.out.println(callDeepSeek("你是一个助手。", question));
        System.out.println();

        // 实验B：加角色设定
        System.out.println("=".repeat(60));
        System.out.println("🟡 实验B：角色设定 — '你是一位Java专家'");
        System.out.println("=".repeat(60));
        System.out.println(callDeepSeek(
            "你是一位资深Java开发工程师，精通算法和数据结构。回答要简洁专业。",
            question
        ));
        System.out.println();

        // 实验C：结构化提示
        System.out.println("=".repeat(60));
        System.out.println("🟢 实验C：结构化提示 — 有角色、有要求、有格式");
        System.out.println("=".repeat(60));
        String structuredPrompt = """
            请用Java实现判断回文字符串的方法。
            
            要求：
            1. 方法签名：public static boolean isPalindrome(String s)
            2. 忽略大小写和非字母数字字符
            3. 处理 null 和空字符串
            4. 用双指针实现，不要用 StringBuilder.reverse()
            5. 添加详细的中文注释
            6. 包含 main 方法测试多个用例
            """;
        System.out.println(callDeepSeek("你是一位精通Java和算法的专家。", structuredPrompt));
    }
}
```

### 5.3 实验2：Few-shot 对比

**Python 版** — 创建 `few_shot_demo.py`：

```python
import requests
import json
import os

API_KEY = os.environ.get("DEEPSEEK_API_KEY", "your-api-key")
URL = "https://api.deepseek.com/v1/chat/completions"

def call_deepseek(messages, temperature=0.3):
    """调用 DeepSeek API"""
    headers = {
        "Authorization": f"Bearer {API_KEY}",
        "Content-Type": "application/json"
    }
    payload = {
        "model": "deepseek-chat",
        "messages": messages,
        "temperature": temperature,
        "max_tokens": 500
    }
    resp = requests.post(URL, headers=headers, json=payload, timeout=30)
    return resp.json()["choices"][0]["message"]["content"]


# === 实验1：情感分类（0-shot vs 3-shot） ===

print("=" * 60)
print("📊 实验1：情感分类 — Zero-shot vs Few-shot")
print("=" * 60)

question = "分析以下评论的情感倾向（正面/负面/中性）："手机质量不错，但屏幕有点暗。""

# Zero-shot
print("\n🔴 Zero-shot（无示例）:")
zero_shot = call_deepseek([
    {"role": "user", "content": question}
])
print(zero_shot)

# Few-shot（3个示例）
print("\n🟢 Few-shot（3个示例）:")
few_shot_messages = [
    {"role": "system", "content": "你是一个文本情感分析专家。严格按照示例格式输出。"},
    {"role": "user", "content": "评论：这个产品太棒了，物流也快！"},
    {"role": "assistant", "content": "情感：正面\n置信度：95%\n关键词：太棒了、物流快"},
    {"role": "user", "content": "评论：质量太差，用一次就坏了。"},
    {"role": "assistant", "content": "情感：负面\n置信度：98%\n关键词：质量差、坏了"},
    {"role": "user", "content": "评论：一般般吧，不好不坏。"},
    {"role": "assistant", "content": "情感：中性\n置信度：80%\n关键词：一般般"},
    {"role": "user", "content": "评论：手机质量不错，但屏幕有点暗。"},
]
few_shot = call_deepseek(few_shot_messages)
print(few_shot)


# === 实验2：SQL生成（0-shot vs 2-shot） ===

print("\n\n" + "=" * 60)
print("📊 实验2：SQL生成 — Zero-shot vs Few-shot")
print("=" * 60)

# Zero-shot
print("\n🔴 Zero-shot:")
zero_sql = call_deepseek([
    {"role": "user", "content": "有一个用户表 users（id, name, email, created_at），查询本月注册的用户"}
])
print(zero_sql)

# Few-shot（2个示例）
print("\n🟢 Few-shot:")
few_sql = call_deepseek([
    {"role": "system", "content": "你是一个SQL专家。根据中文描述生成MySQL查询语句。"},
    {"role": "user", "content": "查询价格大于100的商品。表结构：products(id, name, price, stock)"},
    {"role": "assistant", "content": "SELECT * FROM products WHERE price > 100;"},
    {"role": "user", "content": "统计每个分类的商品数量。表结构：products(id, name, category_id, price), categories(id, name)"},
    {"role": "assistant", "content": "SELECT c.name, COUNT(p.id) as product_count FROM categories c LEFT JOIN products p ON c.id = p.category_id GROUP BY c.id, c.name;"},
    {"role": "user", "content": "有一个用户表 users（id, name, email, created_at），查询本月注册的用户"}
])
print(few_sql)
```

运行：
```bash
python few_shot_demo.py
```

**Java 版** — 创建 `FewShotDemo.java`：

```java
package ai.learning.day2;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class FewShotDemo {
    static final String API_KEY = System.getenv().getOrDefault("DEEPSEEK_API_KEY", "your-api-key");
    static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    static String callDeepSeek(String jsonPayload) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.deepseek.com/v1/chat/completions"))
                .header("Authorization", "Bearer " + API_KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
        String body = resp.body();
        int contentStart = body.indexOf("\"content\":\"") + 11;
        int contentEnd = body.indexOf("\"", contentStart);
        return body.substring(contentStart, contentEnd)
                   .replace("\\n", "\n").replace("\\\"", "\"");
    }

    public static void main(String[] args) throws Exception {
        String question = "分析以下评论的情感倾向（正面/负面/中性）：\"手机质量不错，但屏幕有点暗。\"";

        System.out.println("=".repeat(60));
        System.out.println("📊 实验1：情感分类 — Zero-shot vs Few-shot");
        System.out.println("=".repeat(60));

        // Zero-shot
        System.out.println("\n🔴 Zero-shot（无示例）:");
        String zeroShotJson = """
            {
                "model": "deepseek-chat",
                "messages": [{"role": "user", "content": "%s"}],
                "temperature": 0.3,
                "max_tokens": 500
            }
            """.formatted(question.replace("\"", "\\\"").replace("\n", "\\n"));
        System.out.println(callDeepSeek(zeroShotJson));

        // Few-shot
        System.out.println("\n🟢 Few-shot（3个示例）:");
        String fewShotJson = """
            {
                "model": "deepseek-chat",
                "messages": [
                    {"role": "system", "content": "你是一个文本情感分析专家。"},
                    {"role": "user", "content": "评论：这个产品太棒了，物流也快！"},
                    {"role": "assistant", "content": "情感：正面\u005cn置信度：95%"},
                    {"role": "user", "content": "评论：质量太差，用一次就坏了。"},
                    {"role": "assistant", "content": "情感：负面\u005cn置信度：98%"},
                    {"role": "user", "content": "评论：一般般吧，不好不坏。"},
                    {"role": "assistant", "content": "情感：中性\u005cn置信度：80%"},
                    {"role": "user", "content": "%s"}
                ],
                "temperature": 0.3,
                "max_tokens": 500
            }
            """.formatted(question.replace("\"", "\\\"").replace("\n", "\\n"));
        System.out.println(callDeepSeek(fewShotJson));
    }
}
```

### 5.4 实验3：结构化提示词模板

**Python 版** — 创建 `prompt_template.py`：

```python
import requests
import json
import os

API_KEY = os.environ.get("DEEPSEEK_API_KEY", "your-api-key")
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
    return resp.json()["choices"][0]["message"]["content"]


# 结构化提示词模板类
class PromptTemplate:
    """提示词模板引擎"""
    
    @staticmethod
    def code_review(code, language="Java"):
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

    @staticmethod
    def api_design(endpoint, method, description):
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

    @staticmethod
    def sql_query(description, tables):
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

输出：仅输出SQL语句，不需要解释。"""


# === 演示：使用模板 ===

# 示例1：代码审查
print("=" * 60)
print("📝 示例1：代码审查模板")
print("=" * 60)

bad_code = """
public class test {
    public static void main(String args[]) {
        String a = "hello";
        String b = "world";
        String c = a + b;
        System.out.println(c);
    }
}"""

result = call_deepseek([
    {"role": "system", "content": "你是一位经验丰富的Java代码审查专家。"},
    {"role": "user", "content": PromptTemplate.code_review(bad_code)}
])
print(result)


# 示例2：SQL生成
print("\n\n" + "=" * 60)
print("📝 示例2：SQL生成模板")
print("=" * 60)

result2 = call_deepseek([
    {"role": "system", "content": "你是一位SQL专家。"},
    {"role": "user", "content": PromptTemplate.sql_query(
        "查询每个分类下销量前3的商品",
        ["products(id, name, category_id, price, sales_count)", 
         "categories(id, name)"]
    )}
])
print(result2)
```

运行：
```bash
python prompt_template.py
```

---

## 6. 课堂练习

### 练习1：优化下面的提示词

```
❌ 原始版：
"分析这个数据"

✅ 你的优化版：
（用结构化模板重写，给出角色、任务、要求、输出格式）
```

### 练习2：设计 Few-shot 示例

设计一个 3-shot 示例，让模型能把**自然语言日期**转成 **Java LocalDate 代码**：

```
输入：下周一
输出：

输入：这个月最后一天
输出：

输入：明年3月15日
输出：

输入：三天后
输出：
```

<details>
<summary>点击查看参考答案</summary>

```
输入：下周一
输出：LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY))

输入：这个月最后一天
输出：LocalDate.now().with(TemporalAdjusters.lastDayOfMonth())

输入：明年3月15日
输出：LocalDate.of(LocalDate.now().plusYears(1).getYear(), 3, 15)

输入：三天后
输出：LocalDate.now().plusDays(3)
```
</details>

### 练习3：实战挑战

用今天学的知识，给 DeepSeek 写一个提示词，让它：

"用 Spring Boot 3.x 实现一个带缓存的用户查询接口"

要求使用**结构化模板**，包含：
- 角色设定
- 具体的技术要求
- 输出格式约束

写好提示词后，直接在当前对话里发给模型测试效果！

---

## 7. 今日小结

### 核心知识点

| 概念 | 一句话记住 |
|------|-----------|
| **Zero-shot** | 不给例子，直接问 |
| **Few-shot** | 给几个例子，照猫画虎 |
| **结构化模板** | 角色 + 任务 + 要求 + 格式 |
| **提示词原则** | 清晰 > 模糊，具体 > 抽象 |
| **Few-shot 数量** | 3-shot 够用，再多就是浪费 |

### Python vs Java 差异

| 维度 | Python | Java |
|------|--------|------|
| API调用 | `requests.post()` | `HttpClient.send()` |
| JSON构造 | dict + json.dumps | 字符串模板 or Jackson |
| JSON解析 | `resp.json()` | 手动 substring or Jackson |
| **易用性** | ✅ 简单 | ⚠️ 稍复杂 |
| **生产友好** | ⚠️ 一般 | ✅ 类型安全 |

> 提示：生产环境用 Java 建议引入 Jackson 或 Gson 处理 JSON，
> 本文为了无依赖演示，用了最简方式。

### 日常心法

> **每次写提示词前，先想三件事：**
> 1. 我给它什么角色？
> 2. 我要什么格式？
> 3. 要不要给例子？

### 后续预告

**第3天：高级提示词策略**
- Chain-of-Thought（思维链）
- Tree-of-Thought（思维树）
- 让模型分步推理解决逻辑问题

---

> 📝 **学习笔记：** 在 `~/ai-learning/week1/notes/day2.md` 中记录今天的收获
> ❓ **遇到问题：** 随时问我
> 🚀 **学有余力：** 试着自己写一个"中文→SQL"的 Few-shot 提示词模板
