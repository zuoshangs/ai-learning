# 第5天：核心参数调优

> **学习目标：** 深入理解 Temperature、Top-P、Max Tokens、Stop Sequences 四个核心参数的实际效果，
>   通过多组对比实验掌握参数调优方法，能为不同任务选择最佳参数组合
> **预计时间：** 2.5小时
> **代码语言：** Python + Java 双版本
> **前置知识：** 第4天（API 请求/响应结构）

---

## 📋 目录

1. [为什么要调参数](#1-为什么要调参数)
2. [Temperature 深度实验](#2-temperature-深度实验)
3. [Top-P 深度实验](#3-top-p-深度实验)
4. [Max Tokens 深度实验](#4-max-tokens-深度实验)
5. [Stop Sequences 深度实验](#5-stop-sequences-深度实验)
6. [综合实验：找最佳组合](#6-综合实验找最佳组合)
7. [课堂练习](#7-课堂练习)
8. [今日小结](#8-今日小结)

---

## 1. 为什么要调参数

### 1.1 同一个提示词，不同参数 → 天壤之别

```text
提示词：用一句话介绍 Python。

temperature=0.0 → "Python 是一种高级、解释型、通用编程语言，以简洁语法和丰富生态著称。"
temperature=0.5 → "Python 是一种易学易用的编程语言，适合数据分析、AI 和 Web 开发。"
temperature=1.0 → "Python 是程序员的好朋友，写起来像在说大白话，用起来像瑞士军刀！"
temperature=2.0 → "Python 是..嗯..一个东西？大概是写代码用的，蓝色logo那个？"
```

### 1.2 每个参数的职责

| 参数 | 控制什么 | 调高效果 | 调低效果 |
|------|---------|---------|---------|
| **Temperature** | 输出的随机性 | 更创意、更多样、可能偏离 | 更保守、更确定、可复现 |
| **Top-P** | 候选词范围 | 更多词可选、更丰富 | 只从高概率词选、更精确 |
| **Max Tokens** | 输出长度上限 | 回答更完整 | 可能被截断 |
| **Stop Sequences** | 何时停止生成 | 提前终止输出 | 让模型自然结束 |

---

## 2. Temperature 深度实验

### 2.1 原理回顾

Temperature 控制的是 Softmax 输出概率分布的"尖锐程度"：

```
temperature → 0:  概率最高的词几乎=100%，其他≈0%  → 每次选同一个词
temperature → 1:  按原始概率分布采样              → 自然多样
temperature → 2:  概率分布被"压平"，低概率词也有机会 → 几乎随机
```

### 2.2 对比实验设计

**Python版** — `temperature_demo.py`：

```python
"""
第5天：Temperature 深度对比实验
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

def ask(prompt, temp=0.7):
    resp = requests.post(
        "https://api.deepseek.com/chat/completions",
        headers={"Authorization": f"Bearer {API_KEY}", "Content-Type": "application/json"},
        json={
            "model": "deepseek-chat",
            "messages": [{"role": "user", "content": prompt}],
            "temperature": temp,
            "max_tokens": 200
        },
        timeout=30
    )
    return resp.json()["choices"][0]["message"]["content"]


def ask_full(prompt, temp=0.7, max_tokens=200, top_p=1, stop=None):
    """返回完整响应用于分析"""
    params = {
        "model": "deepseek-chat",
        "messages": [{"role": "user", "content": prompt}],
        "temperature": temp,
        "max_tokens": max_tokens,
        "top_p": top_p
    }
    if stop:
        params["stop"] = stop
    resp = requests.post(
        "https://api.deepseek.com/chat/completions",
        headers={"Authorization": f"Bearer {API_KEY}", "Content-Type": "application/json"},
        json=params,
        timeout=30
    )
    data = resp.json()
    return {
        "content": data["choices"][0]["message"]["content"],
        "finish_reason": data["choices"][0]["finish_reason"],
        "usage": data.get("usage", {})
    }


prompt = "用一句话介绍 Python。"

# 实验：从 0 到 2.0 逐步升温
print("=" * 60)
print("实验：Temperature 阶梯对比")
print("=" * 60)

for temp in [0.0, 0.3, 0.7, 1.2, 1.8]:
    result = ask(prompt, temp=temp)
    print(f"\n--- temperature={temp} ---")
    print(result)
    time.sleep(2)

# 可复现性测试：temp=0 跑3次
print("\n" + "=" * 60)
print("实验：可复现性测试 (temperature=0 × 3次)")
print("=" * 60)

for i in range(3):
    r = ask(prompt, temp=0)
    print(f"\n第{i+1}次: {r[:50]}...")
```

**Java版** — `TemperatureDemo.java`：

```java
package ai.learning.day5;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;

/**
 * 第5天：Temperature 深度对比实验
 *
 * 设置环境变量：
 * export DEEPSEEK_API_KEY=$(grep DEEPSEEK_API_KEY ~/.hermes/.env | cut -d= -f2 | tr -d '"')
 * 编译 + 运行：javac TemperatureDemo.java && java ai.learning.day5.TemperatureDemo
 */
public class TemperatureDemo {

    static final HttpClient client = HttpClient.newHttpClient();

    static String callApi(String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("https://api.deepseek.com/chat/completions"))
            .header("Authorization", "Bearer " + System.getenv("DEEPSEEK_API_KEY"))
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString(body))
            .build();
        HttpResponse<String> r = client.send(req, HttpResponse.BodyHandlers.ofString());
        return r.body();
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

    static String makeBody(String prompt, double temp, int maxTokens) {
        return String.format("""
            {"model":"deepseek-chat","messages":[{"role":"user","content":"%s"}],
             "temperature":%.1f,"max_tokens":%d}
            """, prompt.replace("\"", "\\\""), temp, maxTokens);
    }

    public static void main(String[] args) throws Exception {
        String prompt = "用一句话介绍 Python。";

        System.out.println("=".repeat(60));
        System.out.println("Temperature 阶梯对比");
        System.out.println("=".repeat(60));

        double[] temps = {0.0, 0.3, 0.7, 1.2, 1.8};
        for (double t : temps) {
            String resp = callApi(makeBody(prompt, t, 200));
            System.out.println("\n--- temp=" + t + " ---");
            System.out.println(extractContent(resp));
        }
    }
}
```

### 2.3 预期结果分析

| Temperature | 回答特点 | 适用场景 |
|-------------|---------|----------|
| 0.0 | 精确、一致、像教科书 | 代码生成、数学、数据提取 |
| 0.3 | 略有变化但不偏离 | 翻译、总结、格式化输出 |
| 0.7 | 自然多样，有创造性 | 默认场景、对话、文案 |
| 1.2 | 创意丰富，可能跑偏 | 头脑风暴、创意写作 |
| 1.8+ | 几乎随机，不可控 | 诗歌实验（一般不推荐） |

---

## 3. Top-P 深度实验

### 3.1 原理

Top-P（核采样）限制模型从累积概率达到 P 的最小词集合中采样。

```
例子：模型下一个词的概率分布
   "Python"     p=0.45
   "它"         p=0.20
   "一种"       p=0.15
   "这门"       p=0.08
   "那个"       p=0.05
   ...其他      p=0.07

top_p=0.5 → 只从 "Python"(0.45) 中选择         → 很保守
top_p=0.9 → 从 "Python"+"它"+"一种"(0.80) 中选择 → 更自然
top_p=1.0 → 所有词都可能                         → 完全自由
```

### 3.2 Temperature  vs Top-P

| 对比维度 | Temperature | Top-P |
|----------|-------------|-------|
| 作用方式 | 缩放整个概率分布 | 截断概率分布尾部 |
| 直观效果 | 整体"热度"控制 | 候选词数量控制 |
| 配合使用 | 建议二选一调，或同时微调 | 同左 |

---

## 4. Max Tokens 深度实验

### 4.1 不同 max_tokens 的效果

**Python版** — `maxtokens_demo.py`：

```python
"""
第5天：Max Tokens 截断实验
"""
import os, time

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

def ask_with_info(prompt, max_tokens=200):
    resp = requests.post(
        "https://api.deepseek.com/chat/completions",
        headers={"Authorization": f"Bearer {API_KEY}", "Content-Type": "application/json"},
        json={
            "model": "deepseek-chat",
            "messages": [{"role": "user", "content": prompt}],
            "temperature": 0.7,
            "max_tokens": max_tokens
        },
        timeout=30
    )
    data = resp.json()
    c = data["choices"][0]
    return {
        "content": c["message"]["content"],
        "finish_reason": c["finish_reason"],
        "completion_tokens": data.get("usage", {}).get("completion_tokens", 0)
    }


prompt = "请详细介绍 Python 语言的历史、特点和主要应用领域。"

print("=" * 60)
print("实验：Max Tokens 截断实验")
print("=" * 60)

for mt in [30, 80, 200, 500]:
    result = ask_with_info(prompt, mt)
    fr = result["finish_reason"]
    status = "✅ 完整" if fr == "stop" else f"⚠️ 截断(finish_reason={fr})"
    print(f"\n--- max_tokens={mt} (实际输出 {result['completion_tokens']} tokens) {status} ---")
    print(result["content"][:100] + "...")
    time.sleep(2)
```

### 4.2 finish_reason 解读

| finish_reason | 含义 | 怎么做 |
|---------------|------|--------|
| `stop` | 正常结束 | ✅ 不用管 |
| `length` | token 用完了 | ⬆️ 增加 max_tokens 或缩短提示词 |
| `content_filter` | 内容违规 | 🔄 调整提示词 |

---

## 5. Stop Sequences 深度实验

### 5.1 什么是 Stop Sequences？

Stop Sequences（停止序列）告诉模型：**看到这个字符串就停下来**。常用于精确控制输出格式。

### 5.2 使用场景

```
# 无 stop：模型可能继续解释
用户："列出3种水果："
模型："1. 苹果 2. 香蕉 3. 橙子 这些都是常见水果，富含维生素..."

# 有 stop="\n"：一行就停
用户："列出3种水果："
模型："1. 苹果 2. 香蕉 3. 橙子"
```

### 5.3 实战代码

**Python版** — `stop_demo.py`：

```python
"""
第5天：Stop Sequences 实验
"""
import os, time

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

def ask_with_stop(prompt, stop=None):
    params = {
        "model": "deepseek-chat",
        "messages": [{"role": "user", "content": prompt}],
        "temperature": 0,
        "max_tokens": 200
    }
    if stop:
        params["stop"] = stop

    resp = requests.post(
        "https://api.deepseek.com/chat/completions",
        headers={"Authorization": f"Bearer {API_KEY}", "Content-Type": "application/json"},
        json=params,
        timeout=30
    )
    data = resp.json()
    c = data["choices"][0]
    return {
        "content": c["message"]["content"],
        "finish_reason": c["finish_reason"]
    }


# 实验1：无 stop
print("=" * 60)
print("实验1：无 stop")
print("=" * 60)
r1 = ask_with_stop("列出3种编程语言：")
print(r1["content"])
print(f"  (finish_reason: {r1['finish_reason']})")

time.sleep(2)

# 实验2：stop = ["\n"]
print("\n" + "=" * 60)
print('实验2：stop=["\\\\n"]')
print("=" * 60)
r2 = ask_with_stop("列出3种编程语言：", stop=["\n"])
print(r2["content"])
print(f"  (finish_reason: {r2['finish_reason']})")

time.sleep(2)

# 实验3：stop = ["```"]
print("\n" + "=" * 60)
print('实验3：stop=["```"] 用于代码生成')
print("=" * 60)
r3 = ask_with_stop(
    "用 Python 写一个斐波那契函数，用 ``` 包裹。",
    stop=["```"]
)
print(r3["content"])
```

---

## 6. 综合实验：找最佳组合

### 6.1 不同场景的推荐参数

| 任务场景 | Temperature | Top-P | Max Tokens | Stop | 原因 |
|----------|-------------|-------|-----------|------|------|
| 代码生成 | 0 - 0.2 | 0.9 | 2048+ | 无 | 需要精确、完整 |
| 数学计算 | 0 | 1 | 512 | 无 | 必须精确，一次过 |
| 文本翻译 | 0.3 | 0.9 | 1024 | 无 | 既要准确又要自然 |
| 文章总结 | 0.3 - 0.5 | 0.9 | 512-1024 | 无 | 精炼但不过于死板 |
| 头脑风暴 | 0.8 - 1.2 | 0.95 | 1024 | 无 | 鼓励多样性 |
| 创意写作 | 0.7 - 1.0 | 0.95 | 2048+ | 无 | 平衡创造性和连贯性 |
| 分类/提取 | 0 - 0.1 | 0.5 | 200 | \n | 高精度、短输出 |

### 6.2 组合对比实验

```python
"""
第5天：参数组合对比
"""
import os, time

API_KEY = os.environ.get("DEEPSEEK_API_KEY")
if not API_KEY:
    # ... (同上)
    pass

import requests

def ask_params(prompt, temp=0.7, top_p=1, max_tokens=200, stop=None):
    params = {
        "model": "deepseek-chat",
        "messages": [{"role": "user", "content": prompt}],
        "temperature": temp,
        "top_p": top_p,
        "max_tokens": max_tokens
    }
    if stop:
        params["stop"] = stop
    resp = requests.post(
        "https://api.deepseek.com/chat/completions",
        headers={"Authorization": f"Bearer {API_KEY}", "Content-Type": "application/json"},
        json=params,
        timeout=30
    )
    data = resp.json()
    return {
        "content": data["choices"][0]["message"]["content"],
        "finish_reason": data["choices"][0]["finish_reason"],
        "usage": data.get("usage", {})
    }


# 提示词：提取信息
prompt = "从以下文本中提取日期和金额：'订单号ORD-2024-001，于2024年3月15日支付，金额为人民币1,280元。'"

configs = [
    ("精确模式",   {"temp": 0,   "top_p": 0.5, "max_tokens": 100}),
    ("默认模式",   {"temp": 0.7, "top_p": 1,   "max_tokens": 100}),
    ("创意模式",   {"temp": 1.2, "top_p": 0.95,"max_tokens": 100}),
]

print("=" * 60)
print("实验：参数组合对比（信息提取）")
print("=" * 60)

for name, cfg in configs:
    result = ask_params(prompt, **cfg)
    print(f"\n--- {name} (temp={cfg['temp']}, top_p={cfg['top_p']}) ---")
    print(f"输出: {result['content'][:80]}...")
    print(f"状态: {result['finish_reason']}")
    time.sleep(2)
```

---

## 7. 课堂练习

### 练习1：找最优参数

给定一个任务：**"将以下英文翻译成中文：'The quick brown fox jumps over the lazy dog.'"**

请通过实验找出你认为最优的参数组合，并说明理由。

<details>
<summary>点击查看参考答案</summary>

推荐：`temperature=0.3, top_p=0.9, max_tokens=200`

理由：
- 翻译需要准确（低 temperature），但也不能太死板
- 0.3 是最佳平衡点：每次略有不同但意思一致
- top_p=0.9 允许适当的用词多样性
- max_tokens=200 对于单句翻译足够
</details>

### 练习2：用 Stop Sequences 控制格式

要求模型每次只输出一个 JSON 对象，用 `}` 作为 stop sequence。

提示词：
```text
请生成一个用户信息的 JSON，包含 name、age、email 三个字段。
只输出 JSON，不要其他文字。
```

<details>
<summary>点击查看答案</summary>

```python
result = ask_with_stop(
    "请生成一个用户信息的 JSON，包含 name、age、email 三个字段。只输出 JSON。",
    stop=["}"]
)
print(result["content"] + "}")
# 输出：{"name": "张三", "age": 28, "email": "zhangsan@example.com"
```
</details>

---

## 8. 今日小结

### 核心概念速查

| 概念 | 一句话 | 默认值 | 推荐调整范围 |
|------|--------|--------|-------------|
| **Temperature** | 控制随机性，低=确定，高=创意 | 1.0 | 0 — 1.5 |
| **Top-P** | 控制候选词范围，低=保守，高=自由 | 1.0 | 0.5 — 1.0 |
| **Max Tokens** | 输出长度上限，过短会截断 | 4096 | 200 — 4096 |
| **Stop Sequences** | 遇到指定字符串就停止 | 无 | 按需设置 |

### 参数调优口诀

```
代码数学 temp=0，保证精确不跑偏
翻译总结 temp=3，既准又活最自然
创意写作 temp=7，天马行空有新意
max_tokens 先设大，截断之后再调小
stop 用来控格式，JSON 代码最实用
```

### 参数调优决策树

```
任务需要精确？
├─ 是 → temperature=0, top_p=0.5-0.9
│   ├─ 而且输出要简短？→ 加 stop
│   └─ 输出要完整？→ max_tokens 设大
├─ 否 → 需要创意？
│   ├─ 有一点 → temperature=0.3-0.5
│   ├─ 比较有 → temperature=0.7
│   └─ 随便发挥 → temperature=1.0-1.2
└─ 摸不准？→ temperature=0.7, top_p=1.0（默认最稳）
```

### 今日检查清单

- [ ] 完成 Temperature 阶梯对比实验
- [ ] 理解 Temperature=0 的可复现性
- [ ] 完成 Max Tokens 截断实验，理解 finish_reason
- [ ] 完成 Stop Sequences 实验
- [ ] 理解不同任务的推荐参数组合
- [ ] 完成课堂练习
- [ ] 在 `~/ai-learning/week1/notes/day5.md` 记录学习笔记

### 明天预告

**第 6 天：角色设定与模板 👤**

- 编写系统级提示词，构建特定领域专家角色
- 提取变量形成基础模板
- 构建个人提示词库
