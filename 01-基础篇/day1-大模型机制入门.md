# 第1天：大模型基础机制入门

> **学习目标：** 理解 Token 和上下文窗口这两个核心概念，能用工具计算不同文本的 Token 消耗。
> **预计时间：** 2小时
> **前置知识：** 无（Java 版需要 Maven/Gradle 基础）
> **代码语言：** Python + Java 双版本

---

## 📋 目录

1. [为什么需要理解 Token？](#1-为什么需要理解-token)
2. [Token 是什么](#2-token-是什么)
3. [中英文 Token 差异](#3-中英文-token-差异)
4. [上下文窗口](#4-上下文窗口)
5. [动手实操](#5-动手实操)
6. [课堂练习](#6-课堂练习)
7. [今日小结](#7-今日小结)

---

## 1. 为什么需要理解 Token？

想象一下：你要跟一个外国朋友聊天，但你的表达方式会影响他理解你的速度和准确性。

大模型就像这个朋友，而 **Token 就是它理解语言的"基本单位"**。

**不理解 Token 会遇到的坑：**

| 场景 | 不懂 Token 的人 | 懂 Token 的人 |
|------|----------------|--------------|
| 写提示词 | 写了一段超长英文，结果被截断了 | 知道中文占 Token 多，精简表达 |
| 选模型 | 看到 128K 上下文觉得"够用了" | 知道实际可用量要打折扣 |
| 算成本 | 按字符数预估，实际账单贵 2-3 倍 | 按 Token 准确估算 |

---

## 2. Token 是什么

### 2.1 直观理解

**Token 是模型阅读和理解文本的"基本单位"。**

- 对人类来说，基本单位是 **字/词**
- 对模型来说，基本单位是 **Token**

```
人类视角： "我今天很开心"  →  6 个字
模型视角： "我今天很开心"  →  9 个 Token（拆分更细）
```

### 2.2 Token 是怎么切分的？

模型使用一种叫 **BPE（Byte Pair Encoding）** 的算法来切分文本。它的规则是：

**常见词/词根 → 1 个 Token**
**不常见词 → 拆成多个 Token**

```
常见词 → 1 Token：
  "the" → 1 Token
  "is"  → 1 Token
  "好"   → 1 Token

不常见词 → 多个 Token：
  "Tokenization" → "Token" + "ization" = 2 Tokens
  "魑魅魍魉"     → 可能要 4-6 个 Token
```

### 2.3 为什么要有 Token，而不是按字？

**效率原因：**

```
"unbelievable" 这个单词：
  ❌ 按字母：u-n-b-e-l-i-e-v-a-b-l-e = 12 个单位
  ✅ 按 Token："un" + "believable" = 2 个 Token（效率高 6 倍）
```

模型见过的训练数据中，"un" 和 "believable" 经常出现，所以它们被合并成独立的 Token，模型一眼就能识别，不需要逐个字母拼读。

---

## 3. 中英文 Token 差异

这是 **最实用** 的知识点，直接影响你写提示词和选模型。

### 3.1 核心规律

| 语言 | Token 消耗 | 说明 |
|------|-----------|------|
| **英文** | 1 Token ≈ 0.75 单词 | 效率很高，常见词基本 1:1 |
| **中文** | 1 Token ≈ 1.25-1.5 字 | 效率较低，中文被拆得更细 |
| **代码** | 1 Token ≈ 1 个符号 | 关键词 1 Token，长变量名可能拆开 |
| **数字** | 1 Token ≈ 1-3 位 | 123 → 1 Token，12345 → 2 Token |

### 3.2 为什么中文 Token 更多？

因为模型主要是用英文语料训练的，英文的常见模式已经被压缩成单个 Token，而中文的常见模式需要模型重新学习。

**直观对比：**

```
"Hello world"              → 2 Tokens
"你好世界"                  → 4 Tokens

同样是打招呼，中文需要 2 倍的 Token！
```

### 3.3 实战意义

**写提示词时：**
```
❌ 长篇大论的中文提示词 → 贵且容易超上下文
✅ 用简洁准确的中文 → 省 Token 又清晰
✅ 结合英文术语 → 更省 Token（"RAG系统"比"检索增强生成系统"省 3 倍）
```

**选模型时：**
```
如果你的场景：
  - 主要处理中文 → 需要更大的上下文窗口
  - 主要处理英文/代码 → 标准窗口就够了
```

---

## 4. 上下文窗口

### 4.1 是什么

**上下文窗口（Context Window）** 是模型一次能处理的最大 Token 数量。

可以理解为模型的 **"短期记忆容量"**。

```
人类类比：
  你一边听别人说话一边理解，你能记住刚才说过的内容。
  但如果对方连续说 2 小时不停，你肯定忘了开头说了什么。

模型也一样：
  上下文窗口就是它能"记住"的上限。
  超出的内容会被"遗忘"。
```

### 4.2 主流模型的上下文窗口

| 模型 | 上下文窗口 | 约等于中文字数 | 约等于英文词数 |
|------|-----------|---------------|---------------|
| DeepSeek V4 | 64K - 128K Tokens | 4-9 万中文 | 8-17 万英文 |
| GPT-4 | 128K Tokens | 9 万中文 | 17 万英文 |
| Claude 3.5 Sonnet | 200K Tokens | 14 万中文 | 27 万英文 |
| Gemini 1.5 Pro | 2M Tokens | 140 万中文 | 270 万英文 |

### 4.3 上下文窗口的陷阱

**⚠️ 常见误解：** "我的模型支持 128K，我一次性丢 128K 内容进去应该没问题"

**实际情况：**

```
1. 输入 + 输出 共用窗口
   你传了 100K 的文档 + 50K 的对话历史 = 150K，超了！

2. 长上下文的质量下降
   模型虽然能"看到"128K 的内容，但对中间部分的"注意力"会减弱
   测试表明：超出 64K 后，中间部分的信息召回率明显下降

3. 成本暴增
   Token 数 = 成本 × 输入（通常是输出的 3 倍）
   128K 的一次输入 ≈ 36 次正常对话的成本
```

**正确用法：**

```
✓ 输入控制在窗口的 60-70%
✓ 核心信息放在开头和结尾（模型关注力最强的地方）
✓ 长文档用 RAG（检索+生成），不要一股脑全丢进去
```

---

## 5. 动手实操

### 5.1 环境准备

**Python 版：**

```bash
# 安装 tiktoken（OpenAI 开源的 Tokenizer 库）
pip install tiktoken
```

**Java 版：**

使用 Maven 项目，在 `pom.xml` 中添加依赖：

```xml
<dependency>
    <groupId>com.knuddels</groupId>
    <artifactId>jtokkit</artifactId>
    <version>1.1.0</version>
</dependency>
```

或 Gradle：

```gradle
implementation 'com.knuddels:jtokkit:1.1.0'
```

> `jtokkit` 是 tiktoken 的 Java 移植版，接口几乎一一对应。

---

### 5.2 基础 Token 计数

**Python 版** — 创建 `token_demo.py`：

```python
import tiktoken

# 初始化编码器（GPT-4 使用的编码方案，主流模型通用）
enc = tiktoken.get_encoding("cl100k_base")

# 测试不同文本
text = "Hello, how are you today?"
tokens = enc.encode(text)

print(f"原文: {text}")
print(f"Token 数量: {len(tokens)}")
print(f"字符数: {len(text)}")
print(f"Token IDs: {tokens}")
```

运行：
```bash
python token_demo.py
```

**Java 版** — 创建 `TokenDemo.java`：

```java
package ai.learning.day1;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.ModelType;

public class TokenDemo {
    public static void main(String[] args) {
        // 初始化编码器（GPT-4 使用的编码方案）
        Encoding enc = Encodings.newDefaultEncodingRegistry()
                .getEncodingForModel(ModelType.GPT_4);

        // 测试不同文本
        String text = "Hello, how are you today?";
        var tokens = enc.encode(text);

        System.out.println("原文: " + text);
        System.out.println("Token 数量: " + tokens.size());
        System.out.println("字符数: " + text.length());
        System.out.println("Token IDs: " + tokens);
    }
}
```

运行：
```bash
# 先编译
javac -cp "jtokkit-1.1.0.jar" TokenDemo.java
# 再运行
java -cp ".:jtokkit-1.1.0.jar" ai.learning.day1.TokenDemo
```

---

### 5.3 Token 可视化（看模型如何拆分）

**Python 版** — 创建 `token_visualize.py`：

```python
import tiktoken

enc = tiktoken.get_encoding("cl100k_base")

def visualize_tokenization(text):
    """可视化展示文本如何被拆分成 Token"""
    tokens = enc.encode(text)
    decoded_tokens = [enc.decode([t]) for t in tokens]
    
    print(f"原文: {text}")
    print(f"总 Token 数: {len(tokens)}")
    print(f"总字符数: {len(text)}")
    print(f"Token/字符比: {len(tokens)/len(text):.2f}")
    print()
    print("拆分明细:")
    print("-" * 40)
    for i, (token_id, decoded) in enumerate(zip(tokens, decoded_tokens), 1):
        # 转义特殊字符以便显示
        display = repr(decoded)[1:-1]
        print(f"  Token {i:2d} (ID:{token_id:5d}) → '{display}'")
    print("-" * 40)
    print(f"重组后: '{enc.decode(tokens)}'")
    print()

# 测试不同场景
visualize_tokenization("Hello! How are you doing today?")
visualize_tokenization("你好，今天过得怎么样？")
visualize_tokenization("if (count > 10) { return true; }")
visualize_tokenization("unbelievable tokenization")
```

运行：
```bash
python token_visualize.py
```

**Java 版** — 创建 `TokenVisualize.java`：

```java
package ai.learning.day1;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.ModelType;
import java.util.List;

public class TokenVisualize {
    private static final Encoding enc = Encodings.newDefaultEncodingRegistry()
            .getEncodingForModel(ModelType.GPT_4);

    public static void visualizeTokenization(String text) {
        List<Integer> tokens = enc.encode(text);
        
        System.out.println("原文: " + text);
        System.out.println("总 Token 数: " + tokens.size());
        System.out.println("总字符数: " + text.length());
        System.out.printf("Token/字符比: %.2f%n", (double) tokens.size() / text.length());
        System.out.println();
        System.out.println("拆分明细:");
        System.out.println("-".repeat(50));
        
        for (int i = 0; i < tokens.size(); i++) {
            int tokenId = tokens.get(i);
            String decoded = enc.decode(List.of(tokenId));
            // 对特殊字符做转义显示
            String display = decoded.replace("\n", "\\n").replace("\r", "\\r");
            System.out.printf("  Token %2d (ID:%5d) → '%s'%n", i + 1, tokenId, display);
        }
        
        System.out.println("-".repeat(50));
        System.out.println("重组后: '" + enc.decode(tokens) + "'");
        System.out.println();
    }

    public static void main(String[] args) {
        // 测试不同场景
        visualizeTokenization("Hello! How are you doing today?");
        visualizeTokenization("你好，今天过得怎么样？");
        visualizeTokenization("if (count > 10) { return true; }");
        visualizeTokenization("unbelievable tokenization");
        visualizeTokenization("https://api.example.com/v1/chat/completions");
    }
}
```

---

### 5.4 对比分析工具

**Python 版** — 创建 `token_comparison.py`：

```python
import tiktoken

enc = tiktoken.get_encoding("cl100k_base")

def analyze_text(label, text):
    """分析一段文本的 Token 消耗并估算成本"""
    tokens = enc.encode(text)
    # 以 DeepSeek V4 价格为例
    input_cost = len(tokens) * 0.0000005   # $0.5/1M tokens
    output_cost = len(tokens) * 0.000002   # $2/1M tokens
    
    print(f"\n{'='*50}")
    print(f"📝 {label}")
    print(f"{'='*50}")
    display = text[:60] + "..." if len(text) > 60 else text
    print(f"原文: {display}")
    print(f"字符数: {len(text):>8,}")
    print(f"Token 数: {len(tokens):>8,}")
    print(f"Token/字符比: {len(tokens)/len(text):>8.2f}")
    print(f"估算输入成本: ${input_cost:<10.6f}")
    print(f"估算输出成本: ${output_cost:<10.6f}")
    return len(tokens)

# === 实验1：同一语义中英文对比 ===
print("\n🆚 实验1：同一语义的中英文对比")
en_t = analyze_text("英文版", "Machine learning is transforming the world")
cn_t = analyze_text("中文版", "机器学习正在改变世界")
print(f"\n  → 中文 Token 是英文的 {cn_t/en_t:.1f} 倍")

# === 实验2：中英混用优化 ===
print("\n🆚 实验2：纯中文 vs 中英混用")
pure_cn = "请实现一个检索增强生成系统，用于企业知识库问答"
mixed   = "请实现一个 RAG 系统，用于企业知识库问答"
cn_t2 = analyze_text("纯中文版", pure_cn)
mixed_t = analyze_text("中英混用版", mixed)
saving = (cn_t2 - mixed_t) / cn_t2 * 100
print(f"\n  → 混用英文术语节省了 {saving:.0f}% 的 Token！")
```

**Java 版** — 创建 `TokenComparison.java`：

```java
package ai.learning.day1;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.ModelType;

public class TokenComparison {
    private static final Encoding enc = Encodings.newDefaultEncodingRegistry()
            .getEncodingForModel(ModelType.GPT_4);
    
    static class Result {
        String label;
        String text;
        int tokens;
        int chars;
        
        Result(String label, String text) {
            this.label = label;
            this.text = text;
            this.tokens = enc.encode(text).size();
            this.chars = text.length();
        }
    }
    
    static Result analyze(String label, String text) {
        Result r = new Result(label, text);
        double inputCost = r.tokens * 0.0000005;
        double outputCost = r.tokens * 0.000002;
        
        String display = text.length() > 60 ? text.substring(0, 60) + "..." : text;
        
        System.out.println("\n" + "=".repeat(50));
        System.out.println("📝 " + label);
        System.out.println("=".repeat(50));
        System.out.println("原文: " + display);
        System.out.printf("字符数: %,8d%n", r.chars);
        System.out.printf("Token 数: %,8d%n", r.tokens);
        System.out.printf("Token/字符比: %8.2f%n", (double) r.tokens / r.chars);
        System.out.printf("估算输入成本: $%.6f%n", inputCost);
        System.out.printf("估算输出成本: $%.6f%n", outputCost);
        return r;
    }

    public static void main(String[] args) {
        // 实验1：同一语义中英文对比
        System.out.println("\n🆚 实验1：同一语义的中英文对比");
        Result en = analyze("英文版", "Machine learning is transforming the world");
        Result cn = analyze("中文版", "机器学习正在改变世界");
        System.out.printf("\n  → 中文 Token 是英文的 %.1f 倍%n", (double) cn.tokens / en.tokens);
        
        // 实验2：中英混用优化
        System.out.println("\n🆚 实验2：纯中文 vs 中英混用");
        Result pureCn = analyze("纯中文版", "请实现一个检索增强生成系统，用于企业知识库问答");
        Result mixed   = analyze("中英混用版", "请实现一个 RAG 系统，用于企业知识库问答");
        double saving = (double) (pureCn.tokens - mixed.tokens) / pureCn.tokens * 100;
        System.out.printf("\n  → 混用英文术语节省了 %.0f%% 的 Token！%n", saving);
    }
}
```

---

### 5.5 计算上下文窗口使用率

**Python 版** — 创建 `context_calculator.py`：

```python
import tiktoken

enc = tiktoken.get_encoding("cl100k_base")

def context_usage(text, model_name="DeepSeek V4", max_tokens=128000, reserved_output=4000):
    """
    计算文本占用上下文窗口的百分比
    
    参数:
        text: 输入文本
        model_name: 模型名称
        max_tokens: 模型最大上下文
        reserved_output: 为模型输出预留的 Token
    """
    input_tokens = len(enc.encode(text))
    available = max_tokens - reserved_output
    usage_pct = (input_tokens / available) * 100

    print(f"\n{'='*55}")
    print(f"📊 上下文窗口分析 — {model_name}")
    print(f"{'='*55}")
    print(f" 输入文本 Token:      {input_tokens:>10,}")
    print(f" 预留输出 Token:      {reserved_output:>10,}")
    print(f" 最大上下文:          {max_tokens:>10,}")
    print(f" 可用输入空间:        {available:>10,}")
    print(f" 使用率:              {usage_pct:>9.1f}%")
    print(f"{'='*55}")

    if usage_pct > 90:
        print("  ⚠️  警告：内容接近上限！模型容易丢失信息")
    elif usage_pct > 70:
        print("  ⚡ 注意：内容较多，建议适当精简")
    elif usage_pct > 50:
        print("  📗 合理范围，表现良好")
    else:
        print("  ✅ 轻松应对，模型有充足空间处理")
    
    return usage_pct

# 测试不同场景
print("测试不同输入长度的上下文窗口占用：")
context_usage("简短对话", "你好，请问今天天气怎么样？")
context_usage("长文档（500句）", "这是一段文档内容。" * 500)

# 对比不同模型的窗口
print("\n\n🔄 同一文本在不同模型上的表现：")
long_text = "AI技术文档。" * 3000
for model, window in [("DeepSeek V4", 128000), ("Claude 3.5", 200000), ("Gemini 1.5", 2000000)]:
    context_usage(model, long_text, max_tokens=window)
```

**Java 版** — 创建 `ContextCalculator.java`：

```java
package ai.learning.day1;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.ModelType;

public class ContextCalculator {
    private static final Encoding enc = Encodings.newDefaultEncodingRegistry()
            .getEncodingForModel(ModelType.GPT_4);

    public static void contextUsage(String text, String modelName, int maxTokens) {
        contextUsage(text, modelName, maxTokens, 4000);
    }

    public static void contextUsage(String text, String modelName, int maxTokens, int reservedOutput) {
        int inputTokens = enc.encode(text).size();
        int available = maxTokens - reservedOutput;
        double usagePct = (double) inputTokens / available * 100;

        System.out.println("\n" + "=".repeat(55));
        System.out.println("📊 上下文窗口分析 — " + modelName);
        System.out.println("=".repeat(55));
        System.out.printf(" 输入文本 Token:      %,10d%n", inputTokens);
        System.out.printf(" 预留输出 Token:      %,10d%n", reservedOutput);
        System.out.printf(" 最大上下文:          %,10d%n", maxTokens);
        System.out.printf(" 可用输入空间:        %,10d%n", available);
        System.out.printf(" 使用率:              %9.1f%%%n", usagePct);
        System.out.println("=".repeat(55));

        if (usagePct > 90) {
            System.out.println("  ⚠️  警告：内容接近上限！模型容易丢失信息");
        } else if (usagePct > 70) {
            System.out.println("  ⚡ 注意：内容较多，建议适当精简");
        } else if (usagePct > 50) {
            System.out.println("  📗 合理范围，表现良好");
        } else {
            System.out.println("  ✅ 轻松应对，模型有充足空间处理");
        }
    }

    public static void main(String[] args) {
        System.out.println("测试不同输入长度的上下文窗口占用：");
        contextUsage("你好，请问今天天气怎么样？", "DeepSeek V4", 128000);
        contextUsage("这是一段文档内容。".repeat(500), "长文档（500句）", 128000);

        System.out.println("\n\n🔄 同一文本在不同模型上的表现：");
        String longText = "AI技术文档。".repeat(3000);
        contextUsage(longText, "DeepSeek V4", 128000);
        contextUsage(longText, "Claude 3.5", 200000);
        contextUsage(longText, "Gemini 1.5", 2000000);
    }
}
```

---

## 6. 课堂练习

### 练习 1：估算 Token

不看工具，估算以下文本的 Token 数：

```
1. "The cat sat on the mat."       → ? Tokens
2. "人工智能"                         → ? Tokens
3. "return list.stream().map(...)"  → ? Tokens
```

<details>
<summary>点击查看答案</summary>

1. "The cat sat on the mat." → 约 **7 Tokens**（每个常见词 1 Token + 标点）
2. "人工智能" → 约 **4-5 Tokens**（中文每字 1-2 Tokens）
3. 代码片段 → 约 **8-10 Tokens**
</details>

### 练习 2：优化提示词

以下提示词太费 Token，请优化：

```
❌ 原始版（用中文啰嗦描述）：
"请帮我分析一下用户在使用我们这个电商平台的时候，在浏览商品页面到最终下单
这个过程中，可能会在哪些环节遇到困难或者放弃购买？"

✅ 优化版（简洁 + 英文术语）：
"分析电商用户从浏览到下单的购买漏斗中的流失环节"
```

### 练习 3：动手算（任选 Python 或 Java）

**Python：** 用 `tiktoken` 计算你最近写的一段真实代码或文档的 Token 消耗。

**Java：** 用 `jtokkit` 做同样的计算，对比结果是否一致。

```java
// Java 补充练习：计算自己的代码
public class MyCodeTokens {
    public static void main(String[] args) throws Exception {
        // 读取你自己的 Java 文件并计算 Token
        String code = new String(java.nio.file.Files.readAllBytes(
            java.nio.file.Paths.get("YourFile.java")));
            
        var enc = com.knuddels.jtokkit.Encodings.newDefaultEncodingRegistry()
                .getEncodingForModel(com.knuddels.jtokkit.api.ModelType.GPT_4);
        var tokens = enc.encode(code);
        
        System.out.println("文件字符数: " + code.length());
        System.out.println("Token 数: " + tokens.size());
        System.out.println("Token/字符比: " + String.format("%.2f", 
            (double) tokens.size() / code.length()));
    }
}
```

---

## 7. 今日小结

### 核心知识点

| 概念 | 一句话记住 |
|------|-----------|
| **Token** | 模型理解文本的基本单位，不是按字/词 |
| **BPE 编码** | 常见组合→1 Token，生僻组合→拆散 |
| **中英文差异** | 中文 Token 消耗是英文的 1.5-2 倍 |
| **上下文窗口** | 模型短期记忆上限，输入+输出共享 |
| **窗口使用建议** | 用到 60-70%，核心放开头结尾 |

### 实用技巧

1. **写提示词时** — 中文尽量简洁，结合英文术语更省 Token
2. **选模型时** — 中文多就选大窗口模型（128K+）
3. **算成本时** — 先算 Token 数，再算价格
4. **做项目时** — 长文档不要全塞，用 RAG 分片检索

### Python vs Java 关键差异

| 维度 | Python (tiktoken) | Java (jtokkit) |
|------|------------------|---------------|
| 安装 | `pip install tiktoken` | Maven: `com.knuddels:jtokkit:1.1.0` |
| 初始化 | `tiktoken.get_encoding("cl100k_base")` | `Encodings.newDefaultEncodingRegistry().getEncodingForModel(ModelType.GPT_4)` |
| 编码 | `enc.encode(text)` → `List[int]` | `enc.encode(text)` → `List<Integer>` |
| 解码 | `enc.decode([id])` → `str` | `enc.decode(List.of(id))` → `String` |
| 特点 | 更简洁，一行搞定 | 类型安全，Maven 生态 |

### 后续预告

**第2天：提示词工程基础**
- Zero-shot vs Few-shot
- 相同的模型，不同的提示词 → 截然不同的输出
- Python 和 Java 双版本实操

---

> 📝 **学习笔记：** 在 `~/ai-learning/week1/notes/day1.md` 中记录今天的收获
> ❓ **遇到问题：** 随时问我
> 🚀 **学有余力：** 试试用不同的 encoder（`o200k_base`）对比结果差异
