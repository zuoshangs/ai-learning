# 第16天：Spring AI 环境搭建 + 第一个对话

> **学习目标：** 搭建 Spring Boot 3.x + Spring AI 开发环境，理解 Spring AI 的核心概念，调通第一次 AI 对话
> **预计时间：** 2 小时
> **代码语言：** Java（主）+ Python（对照）
> **前置知识：** Day 4（API 基础对接，已懂 curl 调 API）

---

## 📋 目录

1. [为什么需要 Spring AI](#1-为什么需要-spring-ai)
2. [Spring AI 核心概念](#2-spring-ai-核心概念)
3. [环境准备](#3-环境准备)
4. [项目初始化（Maven）](#4-项目初始化maven)
5. [配置 API Key 与模型](#5-配置-api-key-与模型)
6. [启动类 + ChatController](#6-启动类--chatcontroller)
7. [运行与测试](#7-运行与测试)
8. [Python 对照实现](#8-python-对照实现)
9. [常见问题](#9-常见问题)
10. [今日小结](#10-今日小结)

---

## 1. 为什么需要 Spring AI

### 1.1 回顾之前的做法

Day 4 我们用 Python + `requests` 调通了 DeepSeek API：

```python
import requests
resp = requests.post("https://api.deepseek.com/chat/completions",
    headers={"Authorization": "Bearer <key>"},
    json={"model": "deepseek-chat", "messages": [...]})
print(resp.json()["choices"][0]["message"]["content"])
```

这有两个问题：
1. **每次都要手写 HTTP 请求** — 重复劳动
2. **没有类型安全** — 请求/响应都是裸字典

### 1.2 Spring AI 是什么

Spring AI 是 Spring 官方推出的 AI 框架，对标 Python 的 LangChain，提供：

| 能力 | 说明 |
|------|------|
| **统一的 API 抽象** | 换模型只需改配置，不改代码 |
| **类型安全的客户端** | `ChatClient`、`PromptTemplate` 等 Java 接口 |
| **流式输出** | 原生支持 SSE / Flux |
| **工具调用** | 一行注解让 AI 调用 Java 方法 |
| **RAG 集成** | 内置向量数据库和检索组件 |
| **Spring 生态** | 事务、安全、监控直接复用 |

### 1.3 概念对比

| Python 版 | Spring AI 版 | 进步 |
|-----------|-------------|------|
| `requests.post(url, json, headers)` | `chatClient.call(prompt)` | 一行搞定 |
| 字典 `{"role": "user", "content": "hi"}` | `Prompt` + `Message` 对象 | 类型安全 |
| 手写 JSON 解析 | `BeanOutputConverter` | 自动映射 Java 对象 |
| `for chunk in resp.iter_lines()` | `Flux<ChatResponse>` | 响应式流 |

---

## 2. Spring AI 核心概念

### 2.1 架构概览

```
┌─────────────────────────────────────┐
│          你的 Controller             │
│     @PostMapping("/chat")           │
└────────────┬────────────────────────┘
             │ ChatClient
             ▼
┌─────────────────────────────────────┐
│         Spring AI Core              │
│   ChatClient → Prompt → Messages    │
│         ↓  ↓  ↓                     │
│   OpenAI / DeepSeek / Claude ...    │
└────────────┬────────────────────────┘
             │ HTTP (OpenAI 协议)
             ▼
┌─────────────────────────────────────┐
│          DeepSeek API               │
│   POST /chat/completions            │
└─────────────────────────────────────┘
```

### 2.2 关键对象

| 对象 | 作用 | 类比 |
|------|------|------|
| `ChatClient` | AI 对话客户端，核心入口 | JDBC 的 `Connection` |
| `Prompt` | 一次完整的对话请求 | 包含消息列表 |
| `Message` | 单条消息（user/system/assistant） | 请求体中的 message |
| `ChatResponse` | AI 的完整响应 | 响应体 |
| `StreamSpec` | 流式输出的构建器 | SSE 事件流 |

---

## 3. 环境准备

检查你的环境：

```bash
java -version     # 需要 17+
javac -version    # 需要 JDK（仅 JRE 不够！）
mvn -version      # 需要 3.6+
```

如果你已经有了 Java 但没 `javac`：

```bash
java -version     # 有输出 ✅
javac -version    # command not found ❌
```

这是只装了 JRE 没装 JDK。安装 JDK：

```bash
sudo apt-get install -y openjdk-21-jdk
javac -version    # 确认安装成功
```

你已经有了：

```
java version "21.0.10"
Apache Maven 3.8.7"
```

### 3.1 确认 API Key

我们需要 DeepSeek API Key（Day 4 已经注册过了）：

```bash
# 查看是否在环境变量中
echo $DEEPSEEK_API_KEY

# 或者在 .env 中
grep DEEPSEEK_API_KEY ~/.hermes/.env
```

---

## 4. 项目初始化（Maven）

### 4.1 项目结构

```
code/day16/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/ai/learning/
│   │   │   ├── SpringAiDemoApplication.java
│   │   │   └── ChatController.java
│   │   └── resources/
│   │       └── application.yml
│   └── test/java/com/ai/learning/
│       └── SpringAiDemoApplicationTests.java
```

### 4.2 pom.xml

> **实际踩坑：** Spring AI 的正式版尚未发布到 Maven Central，需要使用里程碑版本。
> 以下配置已在实际环境中编译通过。

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.4</version>
        <relativePath/>
    </parent>

    <groupId>com.ai.learning</groupId>
    <artifactId>spring-ai-demo</artifactId>
    <version>1.0.0</version>
    <name>Spring AI Demo</name>
    <description>Day 16: Spring AI 环境搭建</description>

    <properties>
        <java.version>21</java.version>
        <spring-ai.version>1.0.0-M6</spring-ai.version>
    </properties>

    <dependencies>
        <!-- Spring AI OpenAI（兼容 DeepSeek） -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
        </dependency>

        <!-- Web（REST API） -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>${spring-ai.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### 4.3 关键依赖说明

| 依赖 | 作用 |
|------|------|
| `spring-ai-openai-spring-boot-starter` | Spring AI 对 OpenAI 协议的封装 |
| `spring-boot-starter-web` | 提供 REST API 能力 |
| `spring-ai-bom` | 统一管理 Spring AI 各模块版本 |

> **为什么用 OpenAI starter 调 DeepSeek？**
> DeepSeek API 兼容 OpenAI 的协议格式，所以 Spring AI 的 OpenAI 客户端可以直接对接 DeepSeek，只需把 `base-url` 改成 DeepSeek 的地址。这是大模型行业的通用做法。

---

## 5. 配置 API Key 与模型

### 5.1 application.yml

```yaml
spring:
  application:
    name: spring-ai-demo

  ai:
    openai:
      # 从环境变量读取 API Key，避免硬编码
      api-key: ${DEEPSEEK_API_KEY}
      # DeepSeek API 地址（兼容 OpenAI 协议）
      base-url: https://api.deepseek.com
      chat:
        options:
          # DeepSeek 模型名
          model: deepseek-chat
          # 控制创造性：0=确定  1=完全随机
          temperature: 0.7
          # 最大输出 Token 数
          max-tokens: 1024

server:
  port: 8080
```

### 5.2 配置项详解

| 配置项 | 作用 | 建议值 |
|--------|------|--------|
| `api-key` | API 密钥，永远从环境变量读取 | `\${DEEPSEEK_API_KEY}` |
| `base-url` | API 服务地址 | `https://api.deepseek.com` |
| `model` | 使用的模型名称 | `deepseek-chat` |
| `temperature` | 回答的创造性（0-2） | `0.7`（平衡） |
| `max-tokens` | 单次回答最大长度 | `1024` |

> **安全提醒：** 永远不要在代码里硬写 API Key！用 `\${环境变量名}` 引用，Key 放在 `.env` 或系统的环境变量中。

---

## 6. 启动类 + ChatController

### 6.1 启动类（SpringAiDemoApplication.java）

```java
package com.ai.learning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SpringAiDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringAiDemoApplication.class, args);
    }
}
```

这是一个标准的 Spring Boot 启动类，没有任何 AI 特定的代码。Spring AI 的自动配置会自动注入 `ChatClient.Builder`。

### 6.2 ChatController（对话端点）

```java
package com.ai.learning;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

@RestController
public class ChatController {

    private final ChatClient chatClient;

    /**
     * 构造注入 ChatClient（Spring AI 自动配置）
     * 
     * ChatClient.Builder 由 spring-ai-openai-spring-boot-starter
     * 自动创建，基于 application.yml 的配置
     */
    public ChatController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    /**
     * 最简单的对话接口
     * 用 GET 请求方便测试，后续会改用 POST
     * 
     * 访问：curl "http://localhost:8080/chat?message=你好"
     */
    @GetMapping("/chat")
    public String chat(@RequestParam(defaultValue = "你好，请介绍一下你自己") String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }
}
```

### 6.3 代码解析

| 行 | 说明 |
|----|------|
| `ChatClient.Builder` | Spring AI 自动配置的构造器，内含 API Key、模型等配置 |
| `builder.build()` | 创建 ChatClient 实例 |
| `chatClient.prompt()` | 开始构建一次对话请求 |
| `.user(message)` | 添加用户消息（自动包装为 `UserMessage`） |
| `.call()` | 发送请求并等待响应（同步模式） |
| `.content()` | 提取 AI 返回的文本内容 |

与 Day 4 的 Python 版本对比：

| 环节 | Python（Day 4） | Java（Spring AI） |
|------|----------------|-------------------|
| 认证 | 手动塞 Header | 自动从配置读取 |
| 构建消息 | 手写字典列表 | `prompt().user(msg)` |
| 发送请求 | `requests.post()` | `.call()` |
| 取结果 | `resp["choices"][0]["message"]["content"]` | `.content()` |
| 异常处理 | 自己 try-catch | Spring AI 自动重试 + 降级 |

---

## 7. 运行与测试

### 7.1 编译

```bash
cd ~/ai-learning/04-Java-AI应用开发/code/day16

# 首次编译会下载依赖，需要几分钟
mvn clean compile -q
```

### 7.2 启动服务

```bash
# 方式一：直接运行
mvn spring-boot:run

# 方式二：先打包再运行
mvn package -q -DskipTests
java -jar target/spring-ai-demo-1.0.0.jar
```

### 7.3 测试对话

```bash
# 打开另一个终端
curl "http://localhost:8080/chat?message=你好，请用中文介绍一下你自己"
```

预期返回（类似）：

```
你好！我是 DeepSeek，一个由深度求索公司开发的 AI 助手。我可以...
```

### 7.4 更多测试

```bash
# 测试1：中文问答
curl "http://localhost:8080/chat?message=请用一句话解释什么是上下文窗口"

# 测试2：切换到英文
curl "http://localhost:8080/chat?message=What is RAG?"

# 测试3：修改温度
# 在 application.yml 中把 temperature 改为 0.1，重启后试试：
curl "http://localhost:8080/chat?message=用一句话总结Spring框架"
# 再改为 1.5，重启再试，看效果差异
```

---

## 8. Python 对照实现

使用 `requests` 直接调用 API（与 Day 4 相同结构，便于对比）：

```python
"""
Python 版对照：Spring AI 环境搭建
用 requests 直接调用 DeepSeek API
与上面的 Java Spring AI 实现功能相同
"""
import os
import requests
import json


def load_api_key():
    """从环境变量或 .env 读取 API Key"""
    key = os.environ.get("DEEPSEEK_API_KEY")
    if key:
        return key
    env_path = os.path.expanduser("~/.hermes/.env")
    if os.path.exists(env_path):
        with open(env_path) as f:
            for line in f:
                if "DEEPSEEK_API_KEY" in line:
                    return line.split("=", 1)[1].strip().strip("'\"").strip("'\"")
    raise ValueError("未找到 DEEPSEEK_API_KEY")


API_KEY = load_api_key()
URL = "https://api.deepseek.com/chat/completions"
HEADERS = {
    "Authorization": f"Bearer {API_KEY}",
    "Content-Type": "application/json"
}


def chat(message: str, temperature: float = 0.7) -> str:
    """与 Java ChatController 相同的功能"""
    payload = {
        "model": "deepseek-chat",
        "messages": [
            {"role": "user", "content": message}
        ],
        "temperature": temperature,
        "max_tokens": 1024
    }
    resp = requests.post(URL, headers=HEADERS, json=payload, timeout=30)
    data = resp.json()
    return data["choices"][0]["message"]["content"]


if __name__ == "__main__":
    # 测试1：自我介绍
    print("=" * 50)
    print("测试1：自我介绍")
    print("=" * 50)
    result = chat("你好，请用中文介绍一下你自己")
    print(result)

    print()

    # 测试2：知识问答
    print("=" * 50)
    print("测试2：概念解释")
    print("=" * 50)
    result = chat("什么是上下文窗口？")
    print(result)
```

### Java vs Python 对比

| 维度 | Java（Spring AI） | Python（requests） |
|------|------------------|-------------------|
| **代码量** | 3 个文件 | 1 个文件 |
| **类型安全** | ✅ 强类型，编译检查 | ❌ 运行时字典 |
| **配置管理** | ✅ yaml + 自动注入 | ❌ 手动读环境变量 |
| **换模型** | 改配置即可 | 改代码 |
| **快速上手** | 需要 Maven 构建 | 直接运行 |
| **生产级能力** | 监控/熔断/重试内置 | 全部手写 |

> **结论：** Python 适合快速实验和原型验证，Spring AI 适合构建生产级的 AI 服务。

---

## 9. 常见问题

### Q1: `UnsatisfiedDependencyError` — ChatClient.Builder 注入失败

```
Description: Parameter 0 of constructor in com.ai.learning.ChatController
required a bean of type 'org.springframework.ai.chat.client.ChatClient$Builder'
```

**原因：** Spring AI 自动配置没有生效

**检查：**
1. `pom.xml` 是否包含 `spring-ai-openai-spring-boot-starter`
2. `application.yml` 中 `spring.ai.openai.api-key` 是否正确配置
3. 环境变量 `DEEPSEEK_API_KEY` 是否存在

### Q2: `401 Unauthorized` — API Key 错误

```
{"error":{"message":"Authentication Fails, Your api key is invalid"}}
```

**原因：** Spring AI 从环境变量 `${DEEPSEEK_API_KEY}` 读取 API Key，
但该变量未设置到 Shell 环境中。

**排查：**
```bash
echo $DEEPSEEK_API_KEY    # 如果输出为空，说明没设置
```

**解决：** 启动前加载环境变量：
```bash
# 从 .env 文件加载
export $(grep -v '^\s*#' ~/.hermes/.env | xargs)

# 或直接设置
export DEEPSEEK_API_KEY="sk-你的key"

# 然后启动
java -jar target/spring-ai-demo-1.0.0.jar
```

> **注意：** Python 版可以从 `.env` 文件手动读取，但 Spring AI 只会读系统环境变量。

### Q3: 编译找不到 `ChatClient`

```bash
# 确认 Maven 能拉取到 Spring AI 依赖
mvn dependency:tree | grep spring-ai
```

如果看到类似输出，说明依赖正常：
```
[INFO] +- org.springframework.ai:spring-ai-openai-spring-boot-starter:jar:1.0.5:compile
```

### Q4: 端口被占用

```bash
# 修改端口
# 在 application.yml 中：
server:
  port: 8081

# 或启动时指定
java -jar target/spring-ai-demo-1.0.0.jar --server.port=8081
```

---

## 10. 今日小结

### 你学到了什么

| 知识点 | 掌握度 |
|--------|:------:|
| Spring AI 是什么、为什么用它 | ⭐⭐⭐ |
| 核心概念：ChatClient / Prompt / Message | ⭐⭐ |
| Maven 项目搭建 + Spring AI 依赖配置 | ⭐⭐⭐ |
| application.yml 配置（API Key / 模型 / 参数） | ⭐⭐⭐ |
| 编写 ChatController 并调通对话 | ⭐⭐⭐ |
| 与 Python 实现的对比理解 | ⭐⭐⭐ |

### 与 Day 4 的知识衔接

| Day 4（Python API） | Day 16（Spring AI） | 进步 |
|---------------------|---------------------|------|
| 手写 HTTP 请求头 | 配置驱动，自动注入 | 配置化 |
| 裸字典消息 | 类型安全的 Message 对象 | 类型安全 |
| 自己处理 JSON 解析 | .content() 直接取文本 | 简洁 |
| 无内置容错 | 内置重试 + 降级 | 健壮 |

### 金句

> **Spring AI 让 Java 开发者调 AI 就像调数据库一样简单。**
> 如果说 Day 4 是"会调 API"，那今天就是"有了第一个 AI 工程框架"。

### 课后思考

1. 如果把 `temperature` 设为 0.0 和 2.0，回答会有什么变化？（可以在 Python 版本中试试）
2. 如果你的 Controller 返回的不是 `String` 而是 `ChatResponse` 对象，前端会收到什么？
3. 思考：要支持多个不同的 API Key（比如 DeepSeek + OpenAI），application.yml 该怎么配置？

---

> **明天预告：** Day 17 — 提示词模板 + 结构化输出
> 学会用 `PromptTemplate` 做参数化注入，告别字符串拼接；
> 用 `BeanOutputConverter` 把 AI 的任意输出映射为 Java 对象 🎯
