# 第17天：提示词模板 + 结构化输出（Java 版）

> **学习目标：** 掌握 Spring AI 的提示词模板（PromptTemplate）实现参数化注入，学习 BeanOutputConverter 将 AI 输出自动映射为 Java 对象
> **预计时间：** 2 小时
> **代码语言：** Java（主）+ Python（对照）

---

## 📋 目录

1. [回顾 Day 16](#1-回顾-day-16)
2. [为什么需要提示词模板](#2-为什么需要提示词模板)
3. [模板 vs 字符串拼接](#3-模板-vs-字符串拼接)
4. [三种实用模板实战](#4-三种实用模板实战)
5. [为什么需要结构化输出](#5-为什么需要结构化输出)
6. [BeanOutputConverter 详解](#6-beanoutputconverter-详解)
7. [结构化输出实战](#7-结构化输出实战)
8. [Python 对照实现](#8-python-对照实现)
9. [异常处理](#9-异常处理)
10. [今日小结](#10-今日小结)

---

## 1. 回顾 Day 16

昨天我们学会了 Spring AI 的基础用法：

```java
chatClient.prompt()
    .user(message)
    .call()
    .content();
```

但这只能处理**固定消息**。如果你想构建**可复用的、参数化的**提示词，就需要模板了。

### 今天的两个核心新能力

| 能力 | 解决的问题 | 类比 |
|------|-----------|------|
| **提示词模板** | 重复写提示词，只有参数不同 | 像 SQL 的 PreparedStatement |
| **结构化输出** | AI 返回自由文本，需要解析成 Java 对象 | 像 JSON 反序列化 |

---

## 2. 为什么需要提示词模板

### 2.1 问题场景

假设你经常需要做三件事：**代码审查**、**翻译**、**SQL 生成**。

没有模板的写法：

```java
// 代码审查（每次都要写完整提示词）
String review1 = chat("你是一个Java专家，请审查这段代码...（200字提示词）...");
String review2 = chat("你是一个Python专家，请审查这段代码...（200字提示词）...");

// 翻译（每次改提示词）
String trans1 = chat("将以下英文翻译成中文：Hello World");
String trans2 = chat("将以下中文翻译成英文：你好世界");
```

**问题：** 同一件事的提示词 90% 相同，只有参数不同 → 大量重复代码

### 2.2 模板思维

```
不变的部分（模板结构） + 可变的部分（参数）
```

Java 中用 `String.formatted()` 实现：

```java
String template = """
    你是%s专家，请审查以下%s代码。
    代码：
    ```%s
    %s
    ```
    """.formatted(language, language, language, code);
```

---

## 3. 模板 vs 字符串拼接

| 方式 | 代码 | 可读性 | 维护性 |
|------|------|:------:|:------:|
| 字符串拼接 | `"你好" + name + "，你的年龄是" + age` | ❌ | ❌ |
| `String.format()` | `String.format("你好%s，年龄%d", name, age)` | ⚠️ | ⚠️ |
| `String.formatted()` (Java 15+) | `"你好%s，年龄%d".formatted(name, age)` | ✅ | ✅ |
| **模板字符串** (Java 21+) | `STR."你好\{name}，年龄\{age}"` | ✅✅ | ✅✅ |

### 实战建议

在 Spring AI 1.0.0-M6 中，`PromptUserSpec.text()` 不支持多参数，所以最实用的方案是：

```java
// ✅ 推荐：先用 formatted() 构建完整字符串，再传入
String msg = template.formatted(param1, param2, param3);
chatClient.prompt().user(msg).call().content();
```

---

## 4. 三种实用模板实战

### 4.1 代码审查模板

```java
public String codeReview(String language, String code) {
    String template = """
            你是一个资深的%s代码审查专家。
            请审查以下代码，指出所有问题，包括：
            1. 安全漏洞
            2. 性能问题
            3. 代码规范
            4. 可维护性
            5. 潜在Bug
            
            代码：
            ```%s
            %s
            ```
            
            请按严重程度排列，每个问题标注行号和修改建议。
            """.formatted(language, language, code);

    return chatClient.prompt()
            .user(template)
            .call()
            .content();
}
```

### 4.2 翻译模板

```java
public String translate(String sourceLang, String targetLang, String text) {
    String template = """
            你是一个专业%s译%s专家。
            只输出翻译结果，不要解释。
            
            原文（%s）：
            %s
            """.formatted(sourceLang, targetLang, sourceLang, text);

    return chatClient.prompt()
            .user(template)
            .call()
            .content();
}
```

### 4.3 SQL 生成模板

```java
public String generateSql(String tableSchema, String queryRequest) {
    String template = """
            数据库表结构：
            %s
            
            查询需求：
            %s
            
            请只输出 SQL 语句，不要任何解释。
            考虑使用索引、避免全表扫描。
            """.formatted(tableSchema, queryRequest);

    return chatClient.prompt()
            .user(template)
            .call()
            .content();
}
```

### 模板设计原则

| 原则 | 说明 | 反例 |
|------|------|------|
| **角色明确** | 告诉 AI 它是谁 | ❌ "做代码审查" → ✅ "你是资深Java审查专家" |
| **任务具体** | 列出明确的要求 | ❌ "审查这段代码" → ✅ 列出5个检查维度 |
| **格式约束** | 规定输出格式 | ❌ 自由发挥 → ✅ "按严重程度排列" |
| **精简上下文** | 只给必要信息 | ❌ 给整段无关背景 → ✅ 只给代码+需求 |

---

## 5. 为什么需要结构化输出

### 5.1 问题场景

用上面的模板审查代码，返回的是**大段文字**：

```
**严重：除零异常**
- 行号: 5
- 问题: int a / 0 会导致 ArithmeticException
- 建议: 添加除数校验
```

要程序化处理这段文字，你需要：

```java
// 正则提取每个字段 — 脆弱、难维护
String severity = extractByRegex(text, "\\*\\*(\\w+)\\*\\*");
int line = extractLineNumber(text); 
```

**问题：** 自然语言 → 程序难解析，格式稍有变化就崩

### 5.2 解决方案：结构化输出

让 AI **直接输出 JSON**，然后用 Java 类自动反序列化：

```java
// AI 输出：
{
    "totalScore": 65,
    "verdict": "MAJOR",
    "issues": [
        {"severity": "critical", "line": 5, "description": "除零异常"}
    ]
}

// Spring AI 自动转成 Java 对象
CodeReviewResult result = converter.convert(json);
result.getIssues().get(0).getDescription(); // "除零异常"
```

### 5.3 核心原理

```
BeanOutputConverter<BookInfo>
         │
         ▼
1. 分析 BookInfo 类的字段结构
         │
         ▼
2. 生成 JSON Schema（告诉AI应该输出什么格式）
         │
         ▼
3. 将 JSON Schema 拼入提示词
         │
         ▼
4. AI 按 Schema 输出 JSON
         │
         ▼
5. Jackson 反序列化为 BookInfo 对象
```

---

## 6. BeanOutputConverter 详解

### 6.1 基本用法

```java
// 1. 创建转换器
var converter = new BeanOutputConverter<>(BookInfo.class);

// 2. 获取格式描述（JSON Schema）
String format = converter.getFormat();
// 输出类似：
// {
//   "type": "object",
//   "properties": {
//     "title": {"type": "string"},
//     "author": {"type": "string"},
//     "publishYear": {"type": "integer"},
//     ...
//   }
// }

// 3. 把格式描述放入提示词，让AI用这个格式输出
String prompt = STR."""
    请提供书籍信息，按以下JSON格式输出：
    \{format}
    """;

// 4. 获取AI的JSON响应并转换
String json = chatClient.prompt().user(prompt).call().content();
BookInfo book = converter.convert(json);
```

### 6.2 约定

要让 `BeanOutputConverter` 正常工作，Java 类需要：

| 要求 | 说明 | 示例 |
|------|------|------|
| **无参构造器** | Jackson 反序列化需要 | 默认就有 |
| **Getter/Setter** | 字段需要可读写 | `getTitle()` / `setTitle()` |
| **基本类型** | int、double、String 等 | 自动支持 |
| **嵌套对象** | 成员变量是另一个 DTO | `List<Issue>` 自动递归 |
| **枚举** | 可以用 Java enum | 自动映射字符串 |

### 6.3 异常处理

```java
try {
    BookInfo book = converter.convert(json);
} catch (JsonProcessingException e) {
    // JSON 格式不对 — 让AI重试
    log.warn("AI输出格式异常，重试...");
    // 或者降级为文本解析
}
```

---

## 7. 结构化输出实战

### 7.1 DTO 定义

```java
// 图书信息（简单结构）
public class BookInfo {
    private String title;
    private String author;
    private int publishYear;
    private String genre;
    private double rating;
    private List<String> tags;
    private String summary;
    // getters/setters...
}

// 代码审查结果（嵌套结构）
public class CodeReviewResult {
    private int totalScore;       // 0-100
    private String verdict;       // PASS/MINOR/MAJOR/CRITICAL
    private List<String> strengths;
    private List<Issue> issues;   // 嵌套对象
    
    public static class Issue {
        private String severity;   // critical/major/minor/suggestion
        private int line;          // 行号
        private String description;// 问题描述
        private String suggestion; // 修改建议
        // getters/setters...
    }
    // getters/setters...
}
```

### 7.2 服务层实现

```java
@Service
public class AiChatService {

    private final ChatClient chatClient;

    public AiChatService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    /** 获取结构化图书信息 */
    public BookInfo getBookInfo(String bookName) {
        var converter = new BeanOutputConverter<>(BookInfo.class);
        
        String prompt = """
                请提供《%s》的详细信息。
                请按以下 JSON 格式输出：
                %s
                """.formatted(bookName, converter.getFormat());

        String json = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        return converter.convert(json);
    }

    /** 获取结构化代码审查 */
    public CodeReviewResult structuredCodeReview(String code) {
        var converter = new BeanOutputConverter<>(CodeReviewResult.class);
        
        String prompt = """
                请审查以下 Java 代码：
                ```java
                %s
                ```
                请按以下 JSON 格式输出：
                %s
                """.formatted(code, converter.getFormat());

        String json = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        return converter.convert(json);
    }
}
```

### 7.3 Controller

```java
@RestController
public class ChatController {

    private final AiChatService aiChatService;

    public ChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    // 普通对话
    @GetMapping("/chat")
    public String chat(@RequestParam(defaultValue = "你好") String message) {
        return aiChatService.chat(message);
    }

    // 提示词模板：代码审查
    @GetMapping("/review")
    public String reviewCode(
            @RequestParam(defaultValue = "java") String language,
            @RequestParam(defaultValue = "public class Test {}") String code) {
        return aiChatService.codeReview(language, code);
    }

    // 提示词模板：SQL生成
    @GetMapping("/sql")
    public String generateSql(
            @RequestParam(defaultValue = "users(id,name,email)") String schema,
            @RequestParam(defaultValue = "查询所有用户") String query) {
        return aiChatService.generateSql(schema, query);
    }

    // 结构化输出：图书信息
    @GetMapping("/book")
    public BookInfo getBookInfo(@RequestParam(defaultValue = "三体") String name) {
        return aiChatService.getBookInfo(name);
        // Spring MVC 自动把 BookInfo 序列化为 JSON 返回
    }

    // 结构化输出：代码审查
    @PostMapping("/review/structured")
    public CodeReviewResult structuredReview(@RequestBody String code) {
        return aiChatService.structuredCodeReview(code);
    }
}
```

---

## 8. Python 对照实现

### 8.1 提示词模板（Python）

```python
def code_review(language: str, code: str) -> str:
    """代码审查模板 — 对应 Java codeReview()"""
    template = f"""你是一个资深的{language}代码审查专家。
请审查以下代码：
```{language}
{code}
```
请按严重程度排列问题，标注行号和修改建议。"""
    return call_llm([{"role": "user", "content": template}])
```

### 8.2 结构化输出（Python）

```python
def get_book_info(book_name: str) -> dict:
    """获取图书信息（结构化）— 对应 Java getBookInfo()"""
    prompt = f"""请提供《{book_name}》的详细信息，只输出 JSON。

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
    return json.loads(result)  # 对应 Java 的 converter.convert(json)
```

### Java vs Python 对比

| 维度 | Java（Spring AI） | Python |
|------|------------------|--------|
| **模板参数化** | `String.formatted()` | f-string |
| **结构化输出** | `BeanOutputConverter` → 强类型 DTO | 手动 `json.loads()` |
| **类型安全** | ✅ 编译时检查字段类型 | ❌ 运行时才知道字段 |
| **IDE 支持** | 自动补全 BookInfo.getTitle() | 字典键名手写 |
| **异常处理** | `JsonProcessingException` + 重试 | `json.JSONDecodeError` + 重试 |

---

## 9. 异常处理

### 9.1 JSON 解析失败

```java
try {
    BookInfo book = converter.convert(json);
    return book;
} catch (Exception e) {
    // 常见原因：AI输出格式与预期不匹配
    log.warn("结构化输出解析失败，原始响应：\n{}", json);
    // 策略：让AI重试一次
    String retryJson = chatClient.prompt()
            .user("请严格按照JSON格式输出，不要添加任何额外内容。\n" + json)
            .call()
            .content();
    return converter.convert(retryJson);
}
```

### 9.2 temperature 的重要性

| 温度 | 适用场景 | 结构化输出效果 |
|:----:|---------|:-------------:|
| 0.1 | 结构化输出、SQL生成 | ✅ 输出稳定，格式一致 |
| 0.3 | 代码审查 | ✅ 好 |
| 0.7 | 创意写作、翻译 | ⚠️ 可能偏离格式 |
| 1.5 | 头脑风暴 | ❌ 经常不按格式输出 |

> **经验：** 结构化输出时建议 `temperature <= 0.3`

---

## 10. 今日小结

### 你学到了什么

| 知识点 | 掌握度 |
|--------|:------:|
| 为什么需要提示词模板 | ⭐⭐⭐ |
| `String.formatted()` 参数化注入 | ⭐⭐⭐ |
| 三种实用模板（代码审查/翻译/SQL） | ⭐⭐⭐ |
| 为什么需要结构化输出 | ⭐⭐⭐ |
| `BeanOutputConverter` 原理与使用 | ⭐⭐⭐ |
| DTO 定义 + 嵌套对象 | ⭐⭐ |
| 异常处理与重试策略 | ⭐⭐ |

### 三种模式对比

```
模式1：普通对话
  Java: chatClient.prompt().user(msg).call().content()
  Python: requests.post + json提取
  特点：最基础，无模板无结构

模式2：提示词模板
  Java: String.formatted() + chatClient.prompt().user(msg)
  Python: f-string + requests
  特点：参数化，可复用

模式3：结构化输出
  Java: BeanOutputConverter + DTO
  Python: json.loads()
  特点：JSON ↔ Java对象，类型安全
```

### 金句

> **提示词模板 = 批量生产标准提示词**
> **结构化输出 = 让 AI 说"机器能听懂的话"**
> 
> 从今天开始，你已经从"会调 API"升级为"能构建 AI 工程化接口"了。

### 课后思考

1. 如果 BookInfo 里有一个 `Date publishDate` 字段，JSON 里应该传什么格式？`BeanOutputConverter` 能自动处理吗？
2. `temperature=0` 能保证输出 100% 符合 JSON Schema 吗？如果不能，还有什么兜底方案？
3. 如果一个 Controller 需要返回 `List<BookInfo>`，`BeanOutputConverter` 该怎么改？

---

> **明天预告：** Day 18 — 多轮对话 + 流式输出 SSE
> 让 AI 实现有记忆的对话，并且像打字机一样逐字输出 🎯
