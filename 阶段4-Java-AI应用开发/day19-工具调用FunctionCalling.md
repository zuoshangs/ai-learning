# 第19天：工具调用 / Function Calling（Java 版）🛠️

> **学习目标：** 理解 Function Calling 核心原理，用 Spring AI 的 `@Tool` 注解注册 Java 方法为 AI 可调用的工具，构建一个能查天气、算数学、搜新闻的智能助手
> **代码语言：** Java（主）+ Python（对照）
> **前置知识：** Day 18（Spring AI ChatClient + SSE）/ Day 9（Python 版 Function Calling）

---

## 📋 目录

1. [为什么需要工具调用？](#1-为什么需要工具调用)
2. [Function Calling 核心原理](#2-function-calling-核心原理)
3. [Spring AI @Tool 注解](#3-spring-ai-tool-注解)
4. [工具一：天气查询](#4-工具一天气查询)
5. [工具二：计算器+单位换算](#5-工具二计算器单位换算)
6. [工具三：信息搜索+当前时间](#6-工具三信息搜索当前时间)
7. [MethodToolCallbackProvider 注册](#7-methodtoolcallbackprovider-注册)
8. [多工具编排测试](#8-多工具编排测试)
9. [课堂练习](#9-课堂练习)
10. [今日小结](#10-今日小结)

---

## 1. 为什么需要工具调用？

### 大模型的三个天花板

| 做不到的事 | 举例 | 原因 |
|-----------|------|------|
| **实时信息** | "北京今天多少度？" | 模型知识截止于训练时间 |
| **精确计算** | "123456 × 789012 = ？" | 大模型不擅长精确数学运算 |
| **操作外部系统** | "帮我发一封邮件" | 模型没有操作系统权限 |

### Function Calling 的解决思路

```
用户："北京天气怎么样？"
          │
          ▼
  ┌──────────────────┐
  │    大模型判断      │
  │ "这个我需要查工具"  │
  │ 返回: get_weather  │
  │      (北京)        │
  └───────┬──────────┘
          │
          ▼
  ┌──────────────────┐
  │   Java @Tool 方法  │
  │  getWeather("北京")│
  │  返回：天气数据     │
  └───────┬──────────┘
          │
          ▼
  ┌──────────────────┐
  │  大模型生成最终回答  │
  │ "北京今天☀️晴25°C"│
  └──────────────────┘
```

**核心流程：** AI 请求调用工具 → 开发者执行 → 结果返回给 AI → AI 基于结果回答

---

## 2. Function Calling 核心原理

### 传统 API 调用 vs Tool Calling

```java
// ❌ 传统：规则式判断（硬编码，不灵活）
if (msg.contains("天气")) {
    return weatherService.query(msg);
} else if (msg.contains("计算")) {
    return calculator.calc(msg);
}

// ✅ Tool Calling：AI 自主判断（AI 决定何时调用哪个工具）
// 给 AI 注册工具列表，它自己推理用哪个
```

### 工具调用生命周期

1. **注册工具**：开发者用 `@Tool` 注解暴露 Java 方法
2. **触发条件**：AI 在对话中判断需要工具帮助
3. **调用决策**：AI 返回 `tool_call` 请求，含方法名+参数
4. **执行方法**：Spring AI 自动调用对应的 Java 方法
5. **返回结果**：工具执行结果传给 AI
6. **最终回答**：AI 根据工具结果生成自然语言回答

---

## 3. Spring AI @Tool 注解

### 核心 API

```java
package org.springframework.ai.tool.annotation;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Tool {
    String description() default "";
}
```

### 使用方式

```java
@Component  // 必须注册为 Spring Bean
public class MyTools {
    
    @Tool(description = "查询指定城市的天气")
    public String getWeather(String city) {
        // ... 工具实现
        return weatherData;
    }
}
```

### 工具自动发现

Spring AI 通过 `MethodToolCallbackProvider` 扫描 `@Component` 类中的 `@Tool` 方法，自动生成 `ToolCallback` 实例。

```java
@Bean
public ToolCallbackProvider weatherToolProvider(WeatherTools tools) {
    return MethodToolCallbackProvider.builder()
        .toolObjects(tools)  // 传入 @Component 实例
        .build();
}
```

然后通过 `ChatClient.Builder.defaultTools(providers)` 注册到对话客户端。

---

## 4. 工具一：天气查询

### WeatherTools.java

```java
package com.ai.learning.tools.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class WeatherTools {

    @Tool(description = "查询指定城市的当前天气情况")
    public String getWeather(String city) {
        return switch (city) {
            case "北京" -> """
                🌡️ 北京 当前天气
                天气：☀️ 晴
                温度：25°C（体感 23°C）
                湿度：40%  风力：北风 2 级
                PM2.5：55（良）
                """;
            case "上海" -> """
                🌡️ 上海 当前天气
                天气：⛅ 多云
                温度：28°C（体感 30°C）
                湿度：68%  风力：东南风 3 级
                """;
            // ... 更多城市
            default -> "🌡️ %s：晴，23°C".formatted(city);
        };
    }

    @Tool(description = "获取指定城市未来3天的天气预报")
    public String getForecast(String city) {
        return switch (city) {
            case "北京" -> """
                📅 北京 未来3天预报
                明天：晴 18°C~27°C
                后天：多云 20°C~25°C
                大后天：小雨 16°C~22°C
                """;
            // ...
        };
    }
}
```

### 关键设计原则

- **纯函数**：输入确定 → 输出确定，无副作用
- **返回值字符串**：@Tool 方法返回 String，AI 读取自然语言结果
- **描述清晰**：`description` 要足够具体，帮助 AI 判断何时用
- **模拟实现**：生产环境对接真实天气 API

---

## 5. 工具二：计算器+单位换算

### CalculatorTools.java

```java
@Component
public class CalculatorTools {

    @Tool(description = "执行数学计算，支持加(+)、减(-)、乘(*)、除(/)，格式如 '123 * 456'")
    public String calculate(String expression) {
        // 统一运算符格式
        String cleaned = expression
            .replace("×", "*").replace("÷", "/");
        
        // 解析 "数字 运算符 数字"
        String operator = "";
        // ... 解析逻辑
        
        BigDecimal left = new BigDecimal(parts[0].trim());
        BigDecimal right = new BigDecimal(parts[1].trim());
        BigDecimal result;
        
        switch (operator) {
            case "+" -> result = left.add(right);
            case "*" -> result = left.multiply(right);
            case "/" -> {
                if (right.compareTo(BigDecimal.ZERO) == 0)
                    return "❌ 除数不能为零";
                result = left.divide(right, 10, RoundingMode.HALF_UP);
            }
            // ...
        }
        return "📊 %s %s %s = %s".formatted(left, operator, right, result);
    }

    @Tool(description = "单位换算，支持长度(米/千米/英尺)、重量(克/千克/斤/磅)")
    public String convertUnit(String value, String fromUnit, String toUnit) {
        // 统一转标准单位 → 再转目标单位
        // ...
    }
}
```

### 为什么不用模型做计算？

| 方法 | 12345 × 6789 的准确率 |
|------|----------------------|
| 人类口算 | ~60% |
| 大模型直接计算 | ~40% |
| Java BigDecimal | **100%** |

大模型本质是概率模型，精确运算需要委托给确定性的计算工具。

---

## 6. 工具三：信息搜索+当前时间

### SearchTools.java

```java
@Component
public class SearchTools {

    @Tool(description = "搜索最新信息或新闻，返回相关结果列表（模拟搜索）")
    public String searchWeb(String query) {
        if (query.contains("AI")) {
            return """
                📰 搜索结果
                1. DeepSeek发布新一代大模型
                2. Spring AI 1.0.0 正式发布
                3. 研究表明：工具调用可提升LLM准确率
                """;
        }
        // ...
    }

    @Tool(description = "获取当前的日期和时间")
    public String getCurrentTime() {
        LocalDateTime now = LocalDateTime.now();
        return "🕐 %s %s 星期%s".formatted(
            now.toLocalDate(), now.toLocalTime(),
            switch (now.getDayOfWeek()) { ... }
        );
    }
}
```

> 注意：在 API 达到 1000 RPM/100K TPM 限流之前，免费版 DeepSeek 建议对高频工具调用做缓存或降级处理。

---

## 7. MethodToolCallbackProvider 注册

### ToolConfig.java

```java
@Configuration
public class ToolConfig {

    // 每个 @Component 工具类用单独的 Provider 注册
    @Bean
    public ToolCallbackProvider weatherToolProvider(WeatherTools tools) {
        return MethodToolCallbackProvider.builder()
            .toolObjects(tools).build();
    }

    @Bean
    public ToolCallbackProvider calculatorToolProvider(CalculatorTools tools) {
        return MethodToolCallbackProvider.builder()
            .toolObjects(tools).build();
    }

    @Bean
    public ToolCallbackProvider searchToolProvider(SearchTools tools) {
        return MethodToolCallbackProvider.builder()
            .toolObjects(tools).build();
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, 
                                  ToolCallbackProvider... providers) {
        return builder
            .defaultSystem("""
                你是一个智能助手，可以使用多种工具。
                可用的工具：
                - getWeather：查询指定城市的天气
                - getForecast：未来3天预报
                - calculate：数学计算，如"123 * 456"
                - convertUnit：单位换算
                - searchWeb：搜索最新信息
                - getCurrentTime：当前日期时间
                """)
            .defaultTools(providers)  // 注册所有工具
            .build();
    }
}
```

### 为什么用 MethodToolCallbackProvider？

`List<ToolCallback>` 自动注入不能扫描 `@Tool` 注解的方法。必须显式使用 `MethodToolCallbackProvider` 来：

1. 扫描指定 Bean 中的 `@Tool` 方法
2. 生成 JSON Schema 描述（自动生成参数 schema）
3. 建立方法调用映射

### Controller

```java
@RestController
public class ToolController {
    private final ChatClient chatClient;

    @GetMapping("/chat")
    public String chat(@RequestParam String msg) {
        return chatClient.prompt().user(msg).call().content();
    }

    @GetMapping(value = "/chat/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> chatStream(@RequestParam String msg) {
        return chatClient.prompt().user(msg)
            .stream().content()
            .map(chunk -> "data:" + chunk + "\n\n");
    }
}
```

---

## 8. 多工具编排测试

### 测试 1：基础工具调用

```bash
# 查询天气
curl "http://localhost:8080/chat?msg=北京天气怎么样"

# 精确计算
curl "http://localhost:8080/chat?msg=计算12345×6789"

# 单位换算
curl "http://localhost:8080/chat?msg=5公里等于多少米"

# 搜索信息
curl "http://localhost:8080/chat?msg=搜一下最新的AI新闻"

# 当前时间
curl "http://localhost:8080/chat?msg=现在几点了"
```

### 测试 2：多工具组合（关键测试 🔥）

```bash
curl "http://localhost:8080/chat?msg=北京天气怎么样？顺便帮我算1024×768，再查查几点了"
```

**实际执行日志：**
```
Executing tool call: getWeather        ✅
Executing tool call: calculate         ✅
Executing tool call: getCurrentTime    ✅
```

**AI 输出：**
```
✅ 北京天气：25°C ☀️晴
✅ 1024×768 = 786,432
✅ 当前时间：2026年5月27日 15:18
```

AI **自主判断**需要 3 个独立工具，**一次性全部调用**，组合回答！

### 测试 3：流式输出

```bash
curl -N "http://localhost:8080/chat/stream?msg=北京天气和上海天气对比一下"
```

---

## 9. 课堂练习

### 练习 1：扩展工具集 ✏️

新增一个 `CryptoPriceTools`，提供加密货币价格查询：

```java
@Component
public class CryptoPriceTools {
    @Tool(description = "查询加密货币的当前价格")
    public String getCryptoPrice(String symbol) {
        // 模拟价格数据
        return switch (symbol.toUpperCase()) {
            case "BTC" -> "💰 BTC: $68,432";
            case "ETH" -> "💰 ETH: $3,521";
            default -> "💰 %s: 暂无数据".formatted(symbol);
        };
    }
}
```

**要求：**
1. 新建工具类，用 `@Tool` 注解
2. 注册到 ToolConfig
3. 测试：`curl "localhost:8080/chat?msg=比特币多少钱"`

### 练习 2：错误处理提升 🛡️

给 `calculate` 方法添加更多健壮性：
- 支持连续运算：`1+2+3`
- 支持括号：`(1+2)*3`
- 返回友好的错误提示而不是抛异常

### 练习 3：对接真实 API 🌐

将 `searchWeb` 改为真实搜索（选做）：
```java
@Tool(description = "搜索最新信息")
public String searchRealWeb(String query) {
    // 调用 Bing Search API / Google Search API
    // 返回真实搜索结果
}
```

---

## 10. 今日小结

### 核心概念

| 概念 | 说明 |
|------|------|
| **Function Calling** | 让 AI 可以请求调用外部工具函数 |
| **@Tool 注解** | 标记 Java 方法为 AI 可调用工具 |
| **MethodToolCallbackProvider** | 扫描 @Tool 并注册到 ChatClient |
| **工具编排** | AI 自主决策调用顺序和参数 |
| **容错设计** | 工具内部 try-catch 返回友好信息 |

### 代码位置

| 文件 | 路径 |
|------|------|
| **教程** | `04-Java-AI应用开发/day19-工具调用FunctionCalling.md` |
| **DemoApplication.java** | `code/day19/src/main/java/com/ai/learning/tools/DemoApplication.java` |
| **WeatherTools.java** | `code/day19/.../tools/WeatherTools.java` |
| **CalculatorTools.java** | `code/day19/.../tools/CalculatorTools.java` |
| **SearchTools.java** | `code/day19/.../tools/SearchTools.java` |
| **ToolConfig.java** | `code/day19/.../config/ToolConfig.java` |
| **ToolController.java** | `code/day19/.../controller/ToolController.java` |
| **Python 对照** | `code/day19/chat_demo.py` |

### 关键区别：Spring AI vs Python 原生

| 对比项 | Python（原生 API） | Java（Spring AI） |
|--------|-------------------|-------------------|
| **工具注册** | JSON Schema 手动编写 | `@Tool` 注解 + 自动反射生成 Schema |
| **工具标准** | 需手动匹配用哪个 | `MethodToolCallbackProvider` 自动扫描 |
| **执行循环** | 手动 while 循环处理 | Spring AI 自动处理工具调用-执行-返回 |
| **错误处理** | try-catch 在工具函数 | try-catch + 自动回退 |

### 搭建心得

> Spring AI 的 `@Tool` 注解让 Java 方法的工具化变得非常优雅——写一个普通方法，加个注解，AI 就能自动调用。相比 Python 版需要手写 JSON Schema、手动管理工具调用循环，Spring AI 把很多样板代码都省掉了。

---

**⏭️ 明日预告：Day 20 — Spring AI RAG 实战**
- 向量数据库部署（Docker PgVector）
- Embedding API 集成
- Spring AI QuestionAnswerAdvisor
- Java 版 RAG 问答服务
