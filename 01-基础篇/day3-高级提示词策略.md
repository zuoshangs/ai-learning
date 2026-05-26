# 第3天：高级提示词策略

> **学习目标：** 掌握 Chain-of-Thought（思维链）和 Tree-of-Thought（思维树）等高级提示策略，
>   让大模型执行复杂的多步推理任务，并了解 Self-Consistency（自一致性）提升结果可靠性
> **预计时间：** 2小时
> **代码语言：** Python + Java 双版本
> **API 要求：** DeepSeek API（已配置）

---

## 📋 目录

1. [为什么需要高级提示词](#1-为什么需要高级提示词)
2. [Chain-of-Thought（思维链）](#2-chain-of-thought思维链)
3. [Zero-shot CoT](#3-zero-shot-cot)
4. [Few-shot CoT](#4-few-shot-cot)
5. [Self-Consistency（自一致性）](#5-self-consistency自一致性)
6. [Tree-of-Thought（思维树）](#6-tree-of-thought思维树)
7. [动手实操](#7-动手实操)
8. [课堂练习](#8-课堂练习)
9. [今日小结](#9-今日小结)

---

## 1. 为什么需要高级提示词

### 1.1 基础提示词的局限

还记得第 2 天学的 Zero-shot 和 Few-shot 吗？它们对**简单任务**效果很好，但遇到**需要多步推理**的问题时，模型容易"跳步"而出错。

看个例子：

```
问题：一个长方形的长是宽的2倍，周长是36厘米。长方形的面积是多少？

❌ 直接问：「面积是多少？」
    → 可能胡猜一个数

✅ 加引导：「让我们一步一步思考。」
    → 先算宽 → 再算长 → 再算面积 → 正确！
```

**核心问题：** 大模型在推理类任务上，如果不引导它"显式思考"，它倾向于直接跳到答案——而跳步 = 容易出错。

### 1.2 三种提问方式对比

| 方式 | 描述 | 适合场景 | 效果 |
|------|------|----------|------|
| **Zero-shot** | 直接问问题 | 简单事实问答 | ⭐ |
| **Zero-shot CoT** | 加"一步一步思考" | 逻辑推理、数学题 | ⭐⭐⭐ |
| **Few-shot CoT** | 给带推理过程的示例 | 复杂推理、专业领域 | ⭐⭐⭐⭐⭐ |

---

## 2. Chain-of-Thought（思维链）

### 2.1 什么是思维链？

**Chain-of-Thought（CoT，思维链）** 是让大模型在给出最终答案之前，先输出中间推理步骤的技巧。

> **类比 Java 开发：** 就像 debug 时在代码里加 `System.out.println` 打印中间变量——不是直接看结果，而是看每一步的计算过程。

### 2.2 为什么 CoT 有效？

| 原因 | 解释 |
|------|------|
| **扩展计算步数** | 模型输出 token 越多，内部"思考时间"越长 |
| **显式中间状态** | 每一步的结果成为下一步的输入，减少跳步错误 |
| **人类可审计** | 推理过程可见，错误可以定位到具体步骤 |

### 2.3 三种 CoT 策略

```
策略          示例关键词                          用途
───────      ──────────────────                  ──────────
Zero-shot    "让我们一步一步思考"                  快速思路引导
Few-shot     给 2-3 个带推理的示例 + 新问题       复杂推理任务
Self-Cons.   重复问 3-5 次，取多数答案             高可靠性场景
```

---

## 3. Zero-shot CoT

### 3.1 核心思想

不修改问题，只在末尾加一句 **"让我们一步一步思考"**——奇迹般提升推理准确率。

> 这是 2022 年 Kojima 等人发现的：仅加这一句话，模型在数学推理上的准确率从 18% 提升到 79%（在 PaLM 540B 上）。

### 3.2 示例对比

**直接问：**
```
问题：一个水池，进水管3小时可注满，排水管5小时可排空。
      如果同时打开进水管和排水管，需要多少小时才能注满？
回答：2小时 ❌（跳步错误）
```

**加 CoT：**
```
问题：一个水池，进水管3小时可注满，排水管5小时可排空。
      如果同时打开进水管和排水管，需要多少小时才能注满？
      让我们一步一步思考。
回答：
1. 进水管每小时注水 1/3 池
2. 排水管每小时排水 1/5 池
3. 同时打开，净注水 = 1/3 - 1/5 = 5/15 - 3/15 = 2/15 池/小时
4. 注满时间 = 1 ÷ 2/15 = 7.5 小时 ✅
```

### 3.3 触发词变体

不仅限于"一步一步思考"，你还可以：

| 触发词 | 适用场景 | 示例 |
|--------|----------|------|
| "让我们一步一步思考" | 通用 | 数学、逻辑推理 |
| "请详细分析每个步骤" | 技术分析 | 代码审查、系统设计 |
| "先思考再回答" | 通用增强 | 所有推理类问题 |
| "请列出推理过程" | 学术 | 解题、证明 |

---

## 4. Few-shot CoT

### 4.1 核心思想

比 Zero-shot CoT 更进一步：**给模型提供 2-3 个带完整推理过程的示例**，让模型学会"怎么推理"，而不仅仅是"要推理"。

### 4.2 示例结构

```
示例问题：{问题}
示例推理：
1. {步骤1}
2. {步骤2}
3. {步骤3}
答案：{最终答案}

现在请回答：
{新问题}
```

### 4.3 Zero-shot CoT vs Few-shot CoT

| 维度 | Zero-shot CoT | Few-shot CoT |
|------|---------------|--------------|
| **实现成本** | 加一句话 | 需准备 2-3 个示例 |
| **格式可控性** | 一般（推理格式不固定） | 强（按示例格式输出） |
| **专业领域** | 效果有限 | 能教模型特定推理模式 |
| **Token 消耗** | 低（+几个 token） | 高（+几百 token） |

---

## 5. Self-Consistency（自一致性）

### 5.1 核心思想

同一个问题，用 CoT 问 **多次**（3-5 次），取**出现次数最多的答案**。

> 类比 Java 开发：就像跑单元测试时，不是只跑一次看结果，而是跑 3 次确保 flaky test 不干扰结论。

### 5.2 流程

```
            ┌→ CoT推理 → 答案A ─┐
            │                    │
  问题 ────→┼→ CoT推理 → 答案B ─┼→ 统计 → 选出出现最多的答案
            │                    │
            └→ CoT推理 → 答案A ─┘
```

### 5.3 效果数据

| 模型 | 无 CoT | CoT | CoT + Self-Consistency |
|------|--------|-----|----------------------|
| GPT-3 (175B) | 18% | 45% | **57%** |
| PaLM 540B | 18% | 79% | **83%** |

（数据来源：Wang et al., 2022）

### 5.4 适用场景

| 场景 | 建议 | 原因 |
|------|------|------|
| ✅ 数学题 | 跑 3-5 次取多数 | 答案唯一，容易统计 |
| ✅ 选择题 | 跑 3 次取多数 | 选项有限 |
| ✅ 代码生成 | 跑 3 次取最佳 | 正确答案可能不同但均可运行 |
| ❌ 开放式创作 | 不适用 | 答案没有"正确"标准 |

---

## 6. Tree-of-Thought（思维树）

### 6.1 什么是思维树？

**Tree-of-Thought（ToT，思维树）** 是思维链的进一步扩展——不沿着一条路径推理，而是**同时探索多条推理路径**，并评估每条路径的可行性。

> **类比 Java 开发：** CoT 是单线程顺序执行，ToT 是多线程并行搜索 + 剪枝（pruning）。

### 6.2 CoT vs ToT 对比

```
CoT（思维链）：
  问题 → 步骤1 → 步骤2 → 步骤3 → 答案
  (单一路径，一步错步步错)

ToT（思维树）：
          ┌→ 路径A1 → A2 → (评估:高) ─→ 继续
  问题 ──→┼→ 路径B1 → (评估:低) ──→ 剪枝 ✂️
          └→ 路径C1 → C2 → C3 → (评估:高) → 继续
```

### 6.3 ToT 三步法

| 步骤 | 名称 | 做什么 | 提示词 |
|------|------|--------|--------|
| 1 | **探索** | 生成 3 条不同的解决路径 | "列出3种不同的思路" |
| 2 | **评估** | 判断每条路径可行性 | "评估每种思路的优缺点" |
| 3 | **选择** | 选最佳路径深入 | "基于评估，选择最佳路径继续推理" |

### 6.4 实战场景

ToT 最适合**开放性高、有多条可行路径**的问题：

| 场景 | 说明 |
|------|------|
| 🧩 **逻辑谜题** | 24点游戏、数独、填字游戏 |
| 🖊️ **创意写作** | 先规划多条故事线，选最佳 |
| 💻 **架构设计** | 先列出多个方案，评估后选最优 |
| 📊 **数据分析** | 多种分析方法，选最合适的 |

---

## 7. 动手实操

### 7.1 准备代码

先创建代码目录：

```bash
mkdir -p ~/ai-learning/week1/code/day3
```

### 7.2 实验1：Zero-shot vs CoT 对比

**Python版** — `cot_demo.py`：

```python
"""
第3天：CoT 思维链演示
比较三种提问方式：Zero-shot vs Zero-shot CoT vs Few-shot CoT
"""
import os
from openai import OpenAI

client = OpenAI(
    api_key=os.getenv("DEEPSEEK_API_KEY"),
    base_url="https://api.deepseek.com"
)

def ask(prompt, system="你是一个数学老师，擅长分步推理。"):
    response = client.chat.completions.create(
        model="deepseek-chat",
        messages=[
            {"role": "system", "content": system},
            {"role": "user", "content": prompt}
        ],
        temperature=0,
        max_tokens=1024
    )
    return response.choices[0].message.content

# 实验1：Zero-shot（直接问）
print("=" * 60)
print("实验1：Zero-shot（直接问）")
print("=" * 60)

q1 = "一个长方形的长是宽的2倍，周长是36厘米。长方形的面积是多少平方厘米？"
print(f"问题：{q1}\n")
result1 = ask(q1)
print(f"回答：\n{result1}\n")

# 实验2：Zero-shot CoT
print("=" * 60)
print("实验2：Zero-shot CoT（让我们一步一步思考）")
print("=" * 60)

q2 = q1 + "\n\n让我们一步一步思考。"
result2 = ask(q2)
print(f"回答：\n{result2}\n")
```

**Java版** — `CotDemo.java`：

```java
package ai.learning.day3;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * CoT思维链演示 - Zero-shot vs Zero-shot CoT对比
 */
public class CotDemo {

    static final String API_KEY = System.getenv("DEEPSEEK_API_KEY");
    static final String API_URL = "https://api.deepseek.com/chat/completions";

    static HttpClient client = HttpClient.newHttpClient();

    static String ask(String prompt, String system) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("model", "deepseek-chat");

        JsonArray messages = new JsonArray();

        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", system);
        messages.add(sysMsg);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", prompt);
        messages.add(userMsg);

        body.add("messages", messages);
        body.addProperty("temperature", 0);
        body.addProperty("max_tokens", 1024);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .header("Authorization", "Bearer " + API_KEY)
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString(body.toString()))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        return json.getAsJsonArray("choices")
            .get(0).getAsJsonObject()
            .get("message").getAsJsonObject()
            .get("content").getAsString();
    }

    public static void main(String[] args) throws Exception {
        String system = "你是一个数学老师，擅长分步推理。";
        String q1 = "一个长方形的长是宽的2倍，周长是36厘米。长方形的面积是多少平方厘米？";

        // 实验1
        System.out.println("=".repeat(60));
        System.out.println("实验1：Zero-shot（直接问）");
        System.out.println("=".repeat(60));
        System.out.println("问题：" + q1 + "\n");
        String r1 = ask(q1, system);
        System.out.println("回答：\n" + r1 + "\n");

        // 实验2
        System.out.println("=".repeat(60));
        System.out.println("实验2：Zero-shot CoT（让我们一步一步思考）");
        System.out.println("=".repeat(60));
        String q2 = q1 + "\n\n让我们一步一步思考。";
        String r2 = ask(q2, system);
        System.out.println("回答：\n" + r2 + "\n");
    }
}
```

### 7.3 实验2：Few-shot CoT + Self-Consistency

**Python版** — `fewshot_cot_demo.py`：

```python
"""
第3天：Few-shot CoT + Self-Consistency 演示
"""
import os
from openai import OpenAI

client = OpenAI(
    api_key=os.getenv("DEEPSEEK_API_KEY"),
    base_url="https://api.deepseek.com"
)

def ask(prompt, temp=0.7):
    """温度设高一点，让 self-consistency 有变化"""
    response = client.chat.completions.create(
        model="deepseek-chat",
        messages=[{"role": "user", "content": prompt}],
        temperature=temp,
        max_tokens=1024
    )
    return response.choices[0].message.content

# ===== Few-shot CoT =====
print("=" * 60)
print("实验：Few-shot CoT — 给带推理的示例")
print("=" * 60)

prompt = """参考下面的示例，用同样的分步推理方式回答问题：

示例问题：一个正方形周长20厘米，面积是多少？
示例推理：
1. 正方形周长 = 4 × 边长
2. 边长 = 20 ÷ 4 = 5厘米
3. 面积 = 边长 × 边长 = 5 × 5 = 25平方厘米
答案：25平方厘米

示例问题：小明有10个苹果，给了小红3个，又买了5个，现在有几个？
示例推理：
1. 初始有10个
2. 给小红3个：10 - 3 = 7个
3. 又买5个：7 + 5 = 12个
答案：12个

现在请回答：
鸡兔同笼，头35个，脚94只。鸡和兔各有多少只？
让我们一步一步思考。"""

result = ask(prompt, temp=0)
print(f"提示词：\n{prompt}\n")
print(f"回答：\n{result}\n")

# ===== Self-Consistency =====
print("=" * 60)
print("实验：Self-Consistency（跑3次取多数）")
print("=" * 60)

q = "鸡兔同笼，头35个，脚94只。鸡和兔各有多少只？\n让我们一步一步思考。"

results = []
for i in range(3):
    r = ask(q, temp=0.8)
    print(f"--- 第{i+1}次 ---\n{r}\n")
    results.append(r)

# 简单统计：提取答案中的数字
print("=" * 60)
print("Self-Consistency 统计结果：")
print("=" * 60)
for i, r in enumerate(results, 1):
    # 提取最后的数字
    print(f"第{i}次答案：\n{r[-200:]}\n")
```

### 7.4 实验3：Tree-of-Thought 演示

**Python版** — `tot_demo.py`：

```python
"""
第3天：Tree-of-Thought（思维树）演示
三步法：探索 → 评估 → 选择
"""
import os
from openai import OpenAI

client = OpenAI(
    api_key=os.getenv("DEEPSEEK_API_KEY"),
    base_url="https://api.deepseek.com"
)

def ask(prompt, system="你是一个逻辑推理专家。"):
    response = client.chat.completions.create(
        model="deepseek-chat",
        messages=[
            {"role": "system", "content": system},
            {"role": "user", "content": prompt}
        ],
        temperature=0.7,
        max_tokens=1024
    )
    return response.choices[0].message.content

print("=" * 60)
print("Tree-of-Thought（思维树）演示")
print("问题：24点游戏 — 用 3, 3, 8, 8 算出24")
print("=" * 60)

# 第一步：探索 — 列出多种思路
print("\n【第1步：探索 — 列出多种思路】\n")
step1 = ask(
    "用 3, 3, 8, 8 四个数字，通过加减乘除和括号，算出24。"
    "请列出3-4种不同的解题思路，不需要给出完整计算，只描述策略方向。"
)
print(step1)

# 第二步：评估 — 评估每种思路
print("\n【第2步：评估 — 判断每种思路可行性】\n")
step2 = ask(
    f"以下是用 3,3,8,8 算24的几种思路：\n{step1}\n\n"
    "请客观评估每种思路的可行性，指出可能的陷阱。"
)
print(step2)

# 第三步：选择 — 选最佳路径深入
print("\n【第3步：选择 — 选出最佳方案并完整推导】\n")
step3 = ask(
    f"基于以上分析，选择最佳的解题思路，给出完整的计算过程。\n"
    f"题目：用 3, 3, 8, 8 算出24。"
)
print(step3)
```

---

## 8. 课堂练习

### 练习1：CoT 实战

用 CoT 方法解决下面两个问题，先自己思考，再看答案：

**问题1：** 小明从家到学校，步行需要 20 分钟，骑自行车需要 8 分钟。如果先步行 5 分钟再骑自行车，总共需要多少分钟？

**问题2：** 一个数加上 15 再乘以 3 等于 90，这个数是多少？

<details>
<summary>点击查看答案</summary>

**问题1 推理过程：**
1. 步行速度 = 1/20 路程/分钟
2. 先步行 5 分钟，走了 5/20 = 1/4 路程
3. 剩余 3/4 路程
4. 自行车速度 = 1/8 路程/分钟
5. 骑车需要：3/4 ÷ 1/8 = 3/4 × 8 = 6 分钟
6. 总共：5 + 6 = 11 分钟 ✅

**问题2 推理过程：**
1. 设这个数为 x
2. (x + 15) × 3 = 90
3. x + 15 = 30
4. x = 15 ✅
</details>

### 练习2：比较不同策略

修改 `cot_demo.py`，将问题改为你自己的一个真实问题（比如工作中的一个技术决策题），对比三种策略的输出差异。

---

## 9. 今日小结

### 核心概念速查

| 概念 | 一句话总结 | 使用时机 |
|------|-----------|----------|
| **Zero-shot CoT** | 加"一步一步思考"让模型分步推理 | 几乎所有的推理问题都该用 |
| **Few-shot CoT** | 给 2-3 个带推理示例，教模型"怎么推理" | 特定格式要求或专业领域 |
| **Self-Consistency** | 跑 3-5 次 CoT，取多数答案 | 高可靠性场景（数学、选择题） |
| **Tree-of-Thought** | 多路径探索 + 评估 + 剪枝 | 开放性、多方案问题 |
| **Temperature** | 控制输出的随机性（0=确定, 1=创意） | 需要多样性时设高，需要可靠时设低 |

### 三种策略的选用决策树

```
需要推理？
├─ 简单计算 → Zero-shot CoT（加"一步一步思考"）
├─ 复杂推理 → Few-shot CoT（准备2-3个示例）
├─ 高可靠性 → CoT + Self-Consistency（跑3-5次）
└─ 多方案选择 → Tree-of-Thought（探索→评估→选择）
```

### 进阶思考

> **为什么 CoT 有效？** 一个有趣的解释是：大模型的"推理"本质上是在**模仿训练数据中的推理文本模式**。当你让模型"一步一步思考"时，你激活了它记忆中那些带有推理过程的文档——它不是在真正推理，而是在**模仿推理过程**。但正因如此，它确实得到了更好的答案。

### 今日检查清单

- [ ] 理解 CoT（思维链）的核心原理
- [ ] 区分 Zero-shot CoT 和 Few-shot CoT
- [ ] 运行 `cot_demo.py` 对比三种输出差异
- [ ] 运行 `fewshot_cot_demo.py` 体验 Self-Consistency
- [ ] 运行 `tot_demo.py` 体验 Tree-of-Thought
- [ ] 完成课堂练习
- [ ] 在 `~/ai-learning/week1/notes/day3.md` 记录学习笔记

### 明天预告

**第 4 天：API 基础对接 🔌**

- DeepSeek/OpenAI API 注册与密钥配置
- 用 Postman / curl 完成第一次 API 调用
- 理解请求体与响应体结构
- Java 环境配置与 Spring AI 预热
