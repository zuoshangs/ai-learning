# 第4天：API 基础对接

> **学习目标：** 理解 LLM API 的请求/响应结构，掌握核心参数（Temperature、Max Tokens、Top-P）的作用，
>   学会用 curl 和代码调通第一次 API 调用，并处理常见的错误场景
> **预计时间：** 2小时
> **代码语言：** Python + Java 双版本
> **前置知识：** 已注册 DeepSeek API Key（前 3 天已使用）

---

## 📋 目录

1. [为什么需要理解 API 结构](#1-为什么需要理解-api-结构)
2. [API 请求体解剖](#2-api-请求体解剖)
3. [API 响应体解剖](#3-api-响应体解剖)
4. [核心参数详解](#4-核心参数详解)
5. [用 curl 调通第一次 API](#5-用-curl-调通第一次-api)
6. [Python 代码封装](#6-python-代码封装)
7. [Java 代码封装](#7-java-代码封装)
8. [错误处理](#8-错误处理)
9. [课堂练习](#9-课堂练习)
10. [今日小结](#10-今日小结)

---

## 1. 为什么需要理解 API 结构

### 1.1 前 3 天的黑盒

前 3 天你一直在用 DeepSeek API，但用的是我封装好的 `ask()` 函数。你有没有想过：

- 发给服务器的消息到底长什么样？
- 为什么需要 `role: system` 和 `role: user`？
- `temperature=0` 和 `temperature=1` 差别有多大？
- API 返回的 JSON 里除了回答还有什么信息？

### 1.2 理解 API = 掌控模型

| 知道这个 | 能做什么 |
|----------|---------|
| 请求体结构 | 自由定制对话格式、多轮对话、Function Calling |
| 响应体结构 | 获取 Token 用量、做成本分析、提取推理过程 |
| 参数含义 | 精细控制输出质量、调优提示词 |
| 错误码 | 快速定位问题、优雅降级处理 |

> **类比 Java 开发：** 就像你从用现成的 `RestTemplate` 到底层理解 HTTP 协议——知道底层怎么工作的，出问题才能快速定位。

---

## 2. API 请求体解剖

### 2.1 一个完整的请求体

发送给 DeepSeek Chat API 的 HTTP POST 请求体是这样的：

```json
{
  "model": "deepseek-chat",
  "messages": [
    {
      "role": "system",
      "content": "你是一个数学老师，擅长分步推理。"
    },
    {
      "role": "user",
      "content": "一个长方形的长是宽的2倍，周长是36厘米。面积是多少？"
    }
  ],
  "temperature": 0,
  "max_tokens": 1024,
  "top_p": 1,
  "frequency_penalty": 0,
  "presence_penalty": 0,
  "stream": false
}
```

### 2.2 每个字段的含义

| 字段 | 必填 | 说明 | 类比 Java |
|------|------|------|-----------|
| `model` | ✅ | 模型名称，指定用哪个模型 | 类似选择不同的 API 版本或实现 |
| `messages` | ✅ | 对话消息列表，按时间顺序 | 类似聊天记录的 List |
| `temperature` | ❌ | 采样温度，0-2，越低越确定 | 类似 Random 的 seed |
| `max_tokens` | ❌ | 最大输出 token 数 | 类似 StringBuilder 的容量上限 |
| `top_p` | ❌ | 核采样，0-1，替代 temperature | 类似过滤掉低概率词 |
| `frequency_penalty` | ❌ | 频率惩罚，-2 到 2 | 减少重复内容 |
| `presence_penalty` | ❌ | 存在惩罚，-2 到 2 | 鼓励讨论新话题 |
| `stream` | ❌ | 是否流式返回 | 类似 SSE (Server-Sent Events) |

### 2.3 messages 数组的三种角色

messages 数组是 API 的核心，它包含一系列消息对象，每个对象有 `role` 和 `content`：

| role | 用途 | 数量 | 示例 |
|------|------|------|------|
| **system** | 设定模型行为、角色、规则 | 通常 1 条，放在最前面 | "你是一个专业翻译" |
| **user** | 用户的输入/问题 | 1 条或多条（多轮对话） | "请翻译这段话" |
| **assistant** | 模型的回复（用于多轮对话传入历史） | 0 条或多条 | "翻译结果：..." |

**多轮对话示例：**
```json
"messages": [
  {"role": "system", "content": "你是一个友好的助手。"},
  {"role": "user",      "content": "你好！"},
  {"role": "assistant", "content": "你好！有什么可以帮你的吗？"},
  {"role": "user",      "content": "今天天气怎么样？"}
]
```

---

## 3. API 响应体解剖

### 3.1 一个完整的响应体

```json
{
  "id": "chatcmpl-abc123",
  "object": "chat.completion",
  "created": 1716540000,
  "model": "deepseek-chat",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "设宽为 x，则长为 2x。\n周长 = 2(2x + x) = 36\n6x = 36\nx = 6\n面积 = 12 × 6 = 72 平方厘米"
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 45,
    "completion_tokens": 60,
    "total_tokens": 105
  }
}
```

### 3.2 每个字段的含义

| 字段 | 说明 |
|------|------|
| `id` | 本次请求的唯一 ID，用于排查问题 |
| `object` | 响应类型，固定为 `chat.completion` |
| `created` | Unix 时间戳 |
| `model` | 实际处理的模型 |
| `choices` | 生成的回复列表（通常只有 1 个） |
| `choices[].index` | 生成候选项的编号（0 开始） |
| `choices[].message.content` | **模型的回答正文** |
| `choices[].message.role` | 固定为 `assistant` |
| `choices[].finish_reason` | 停止原因：`stop` \| `length` \| `content_filter` |
| `usage.prompt_tokens` | 输入消耗的 token 数 |
| `usage.completion_tokens` | 输出消耗的 token 数 |
| `usage.total_tokens` | 总消耗 token 数 |

### 3.3 finish_reason 详解

| finish_reason | 含义 | 处理方式 |
|---------------|------|----------|
| `stop` | 模型正常完成输出 | ✅ 正常 |
| `length` | 输出达到 max_tokens 上限被截断 | ⚠️ 需要增加 max_tokens 或优化提示词 |
| `content_filter` | 内容被过滤拦截 | ❌ 需调整提示词 |
| `null` | 流式输出中（stream=true 时） | ⏳ 等后续数据 |

---

## 4. 核心参数详解

### 4.1 Temperature（温度）

**控制输出的随机性/创造性。**

```
temperature=0   → 每次都选概率最高的词 → 确定、保守、可复现
temperature=0.7 → 适当引入随机性       → 平衡
temperature=1.0 → 大幅随机选择        → 有创意但可能偏离
temperature=2.0 → 几乎随机输出        → 不可控
```

> **类比 Java 开发：** 就像 `Random.nextInt()` 的 seed——seed 固定（temperature=0）时每次结果一样；seed 不固定时每次不同。

**使用建议：**
| 场景 | 推荐 Temperature |
|------|-----------------|
| 数学计算、代码生成 | 0 — 0.2 |
| 翻译、总结 | 0.3 — 0.5 |
| 创意写作、头脑风暴 | 0.7 — 1.0 |
| 诗歌、故事创作 | 0.8 — 1.2 |

### 4.2 Max Tokens（最大输出长度）

**限制模型一次回复的最大 token 数。**

- 设为过小 → 回答被截断（`finish_reason: length`）
- 设为过大 → 浪费 Token，增加成本和延迟
- 建议值：一般问答 512-1024，代码生成 2048-4096

> **类比 Java 开发：** 类似 `StringBuilder` 的初始容量——设太小会频繁扩容，设太大浪费内存。

### 4.3 Top-P（核采样）

**累积概率阈值，只从概率最高的词中选择，直到累积概率达到 P。**

```
top_p=0.1 → 只从概率最高 10% 的词中选择 → 极保守
top_p=0.9 → 从概率最高 90% 的词中选择 → 默认
top_p=1.0 → 考虑所有词 → 等同于关闭核采样
```

> **注意：** 一般建议修改 `temperature` 或 `top_p` 中的一个，不要同时调整两者。

### 4.4 参数速查表

| 参数 | 范围 | 默认值 | 什么时候改 |
|------|------|--------|-----------|
| `temperature` | 0 - 2 | 1 | 需要控制创造性时 |
| `max_tokens` | 1 - 8192 | 4096 | 回答太短或太长时 |
| `top_p` | 0 - 1 | 1 | 需要更精确控制采样时 |
| `frequency_penalty` | -2 - 2 | 0 | 模型重复内容太多时 |
| `presence_penalty` | -2 - 2 | 0 | 希望模型讨论新话题时 |

---

## 5. 用 curl 调通第一次 API

在写代码之前，先用 curl 直接感受一下原始的 HTTP 请求和响应。

### 5.1 准备 API Key

```bash
# 从 .env 文件读取 API Key
export DEEPSEEK_API_KEY=$(grep DEEPSEEK_API_KEY ~/.hermes/.env | cut -d= -f2 | tr -d '"')
```

### 5.2 第一次 curl 调用

```bash
curl -s https://api.deepseek.com/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $DEEPSEEK_API_KEY" \
  -d '{
    "model": "deepseek-chat",
    "messages": [
      {"role": "user", "content": "你好！请用一句话介绍你自己。"}
    ],
    "temperature": 0.7,
    "max_tokens": 100
  }' | python3 -m json.tool
```

来看看返回的完整 JSON 结构。

---

## 6. Python 代码封装

### 6.1 基础封装

**Python版** — `api_demo.py`：

```python
"""
第4天：API 基础调用演示
"""
import os
import json
import time

# 读取 API Key
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

def call_api(messages, temperature=0.7, max_tokens=1024, system=None):
    """调用 DeepSeek API 并返回完整响应"""
    if system:
        messages = [{"role": "system", "content": system}] + messages
    
    resp = requests.post(
        "https://api.deepseek.com/chat/completions",
        headers={
            "Authorization": f"Bearer {API_KEY}",
            "Content-Type": "application/json"
        },
        json={
            "model": "deepseek-chat",
            "messages": messages,
            "temperature": temperature,
            "max_tokens": max_tokens
        },
        timeout=30
    )
    
    return resp.json()


# ========== 实验1：查看完整响应结构 ==========
print("=" * 60)
print("实验1：完整响应结构")
print("=" * 60)

response = call_api(
    [{"role": "user", "content": "2 + 3 × 4 等于多少？"}],
    temperature=0
)

print(f"\n响应 ID: {response.get('id')}")
print(f"模型: {response.get('model')}")
print(f"回答: {response['choices'][0]['message']['content']}")
print(f"停止原因: {response['choices'][0]['finish_reason']}")
usage = response.get('usage', {})
print(f"\nToken 用量:")
print(f"  输入: {usage.get('prompt_tokens')} tokens")
print(f"  输出: {usage.get('completion_tokens')} tokens")
print(f"  总计: {usage.get('total_tokens')} tokens")


# ========== 实验2：Temperature 对比 ==========
print("\n" + "=" * 60)
print("实验2：Temperature 对比 (temperature=0 vs 1.5)")
print("=" * 60)

prompt = "给我一个 Python 函数名建议（只要名字）："

r_cold = call_api([{"role": "user", "content": prompt}], temperature=0)
r_hot = call_api([{"role": "user", "content": prompt}], temperature=1.5)

print(f"\nTemperature=0:   {r_cold['choices'][0]['message']['content'].strip()}")
print(f"Temperature=1.5: {r_hot['choices'][0]['message']['content'].strip()}")


# ========== 实验3：多轮对话 ==========
print("\n" + "=" * 60)
print("实验3：多轮对话")
print("=" * 60)

messages = [
    {"role": "user", "content": "我的名字是小明。"},
]

r1 = call_api(messages, temperature=0)
print(f"用户: 我的名字是小明。")
print(f"助手: {r1['choices'][0]['message']['content']}\n")

# 把回复加入历史
messages.append(r1['choices'][0]['message'])
messages.append({"role": "user", "content": "我刚才说我叫什么名字？"})

r2 = call_api(messages, temperature=0)
print(f"用户: 我刚才说我叫什么名字？")
print(f"助手: {r2['choices'][0]['message']['content']}")
```

---

## 7. Java 代码封装

**Java版** — `ApiDemo.java`：

```java
package ai.learning.day4;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;

/**
 * 第4天：API 基础调用演示
 * 
 * 运行前设置环境变量：
 * export DEEPSEEK_API_KEY=$(grep DEEPSEEK_API_KEY ~/.hermes/.env | cut -d= -f2 | tr -d '"')
 * 
 * 编译：javac ApiDemo.java
 * 运行：java ai.learning.day4.ApiDemo
 */
public class ApiDemo {

    static final HttpClient client = HttpClient.newHttpClient();

    static String callApi(String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.deepseek.com/chat/completions"))
            .header("Authorization", "Bearer " + System.getenv("DEEPSEEK_API_KEY"))
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString(jsonBody))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    static String extractContent(String json) {
        // 提取 choices[0].message.content
        int idx = json.indexOf("\"content\":\"");
        if (idx < 0) return "解析失败";
        int start = idx + 11;
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\') {
                if (i + 1 < json.length()) {
                    char n = json.charAt(i + 1);
                    if (n == 'n') { sb.append('\n'); i++; }
                    else if (n == '"') { sb.append('"'); i++; }
                    else if (n == '\\') { sb.append('\\'); i++; }
                    else { sb.append(c); }
                }
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    static void printUsage(String json) {
        // 打印 Token 用量
        int pt = json.indexOf("\"prompt_tokens\":");
        int ct = json.indexOf("\"completion_tokens\":");
        int tt = json.indexOf("\"total_tokens\":");
        if (pt > 0) System.out.println("  输入: " + extractNum(json, pt));
        if (ct > 0) System.out.println("  输出: " + extractNum(json, ct));
        if (tt > 0) System.out.println("  总计: " + extractNum(json, tt));
    }

    static String extractNum(String json, int start) {
        int i = json.indexOf(':', start) + 1;
        StringBuilder sb = new StringBuilder();
        while (i < json.length() && Character.isDigit(json.charAt(i))) {
            sb.append(json.charAt(i++));
        }
        return sb.toString();
    }

    static String makeBody(String userMsg, double temp, int maxTokens) {
        return String.format("""
            {
                "model": "deepseek-chat",
                "messages": [{"role": "user", "content": "%s"}],
                "temperature": %.1f,
                "max_tokens": %d
            }
            """, escapeJson(userMsg), temp, maxTokens);
    }

    static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }

    public static void main(String[] args) throws Exception {
        // 实验1：查看 Token 用量
        System.out.println("=".repeat(60));
        System.out.println("实验1：Token 用量");
        System.out.println("=".repeat(60));

        String body1 = makeBody("请用20字以内介绍 Python。", 0.7, 200);
        String resp1 = callApi(body1);
        System.out.println("回答: " + extractContent(resp1));
        printUsage(resp1);

        // 实验2：Temperature 对比
        System.out.println("\n" + "=".repeat(60));
        System.out.println("实验2：Temperature 对比");
        System.out.println("=".repeat(60));

        String cold = callApi(makeBody("说一个数字（只输出数字）：", 0, 50));
        String hot = callApi(makeBody("说一个数字（只输出数字）：", 1.5, 50));
        System.out.println("Temperature=0:   " + extractContent(cold));
        System.out.println("Temperature=1.5: " + extractContent(hot));
    }
}
```

---

## 8. 错误处理

### 8.1 常见错误码

| HTTP 状态码 | 错误原因 | 处理方法 |
|------------|----------|----------|
| 400 | 请求格式错误（如 messages 为空） | 检查请求体 JSON 格式 |
| 401 | API Key 无效或未提供 | 检查 `Authorization` 头 |
| 402 | 余额不足 | 充值 |
| 429 | 请求频率超限（Rate Limit） | 添加重试 + 指数退避 |
| 503 | 服务暂不可用 | 等待后重试 |

### 8.2 添加重试机制

```python
def call_with_retry(messages, temperature=0, max_retries=3):
    for attempt in range(max_retries):
        try:
            resp = call_api(messages, temperature=temperature)
            if "error" not in resp:
                return resp
            if attempt < max_retries - 1:
                wait = 2 ** attempt
                print(f"[429] 限流，{wait}s 后重试...")
                time.sleep(wait)
        except Exception as e:
            if attempt < max_retries - 1:
                wait = 2 ** attempt
                print(f"[错误] {e}，{wait}s 后重试...")
                time.sleep(wait)
            else:
                raise e
```

---

## 9. 课堂练习

### 练习1：参数对比实验

修改你的 API 调用，对比以下参数组合的效果：

| 组合 | temperature | max_tokens | 预期效果 |
|------|-------------|-----------|---------|
| A | 0 | 50 | 确定、简短 |
| B | 1.5 | 50 | 随机、可能截断 |
| C | 0.7 | 500 | 平衡、完整 |

提示词：`"请用中文写一首关于春天的短诗。"`

<details>
<summary>点击查看答案</summary>

**组合 A (temp=0, max_tokens=50)：**
每次输出一样，且可能被截断（finish_reason=length）

**组合 B (temp=1.5, max_tokens=50)：**
每次输出不同，经常被截断

**组合 C (temp=0.7, max_tokens=500)：**
创意适中，输出完整（finish_reason=stop）
</details>

### 练习2：多轮对话模拟

编写代码实现一个 3 轮对话：
1. 用户："我叫张三，是一名 Java 开发者"
2. 用户："帮我写一个 MyBatis 的配置类"
3. 用户："记得我刚才说我是做什么的吗？"

观察模型是否记得上下文。

---

## 10. 今日小结

### 核心概念速查

| 概念 | 一句话总结 |
|------|-----------|
| **请求体** | model + messages + 参数，发给模型的指令 |
| **响应体** | choices[].message.content + usage + finish_reason |
| **Temperature** | 控制创造性（0=确定，1=平衡，2=随机） |
| **Max Tokens** | 输出长度上限 |
| **Messages 数组** | 按 role（system/user/assistant）组织对话历史 |
| **多轮对话** | 把历史消息传入 messages 即可让模型"记住"上下文 |
| **finish_reason** | stop=正常，length=截断，content_filter=违规 |

### 今日检查清单

- [ ] 理解请求体 messages 数组的三种 role
- [ ] 理解响应体 choices 和 usage 的结构
- [ ] 运行 curl 调通第一次 API
- [ ] 运行 `api_demo.py` 查看完整响应结构
- [ ] 对比不同 temperature 的输出差异
- [ ] 理解 finish_reason 的三种状态
- [ ] 完成参数对比练习
- [ ] 在 `~/ai-learning/week1/notes/day4.md` 记录学习笔记

### 明天预告

**第 5 天：核心参数调优 🔧**

- Temperature、Top-P、Max Tokens、Stop Sequences 的实际调优
- 观察生成结果的确定性与创造性差异
- 通过实验找到不同场景的最佳参数组合
