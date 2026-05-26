# 第7天：阶段复盘 📝

> **学习目标：** 全景回顾 week1 六天内容，构建知识体系，生成速查表，
>   完成自测查漏补缺，明确 week2 学习方向
> **预计时间：** 2小时
> **代码语言：** Python + Java 双版本

---

## 📋 目录

1. [Week1 全景回顾](#1-week1-全景回顾)
2. [知识体系地图](#2-知识体系地图)
3. [核心速查表（Cheat Sheet）](#3-核心速查表cheat-sheet)
4. [提示词库索引 v1](#4-提示词库索引-v1)
5. [常见问题与排错手册](#5-常见问题与排错手册)
6. [自测题（Quiz）](#6-自测题quiz)
7. [Week2 学习路线预览](#7-week2-学习路线预览)
8. [今日小结](#8-今日小结)

---

## 1. Week1 全景回顾

### 📖 我们走过的路

```
第1天 ─── 大模型基础机制入门
│   ├─ Token 概念与 BPE 编码
│   ├─ 中英文 Token 消耗差异（中文 ≈ 英文 1.5-2 倍）
│   ├─ 上下文窗口概念（输入 + 输出共享上限）
│   └─ 实操：Python tiktoken / Java jtokkit
│
第2天 ─── 提示词工程基础
│   ├─ Zero-shot：不给例子直接问
│   ├─ Few-shot：给 2-3 个例子照猫画虎
│   ├─ 结构化提示词模板：角色 + 任务 + 要求 + 格式
│   └─ 心法：清晰 > 模糊，具体 > 抽象
│
第3天 ─── 高级提示词策略
│   ├─ Zero-shot CoT："一步一步思考"
│   ├─ Few-shot CoT：给带推理过程的示例
│   ├─ Self-Consistency：跑 3-5 次取多数
│   └─ Tree-of-Thought：多路径探索 + 剪枝
│
第4天 ─── API 基础对接
│   ├─ 请求体结构：model + messages + 参数
│   ├─ 响应体结构：choices + usage + finish_reason
│   ├─ Messages 三种 role：system / user / assistant
│   ├─ curl / Python / Java 调通第一次 API
│   └─ 错误处理：速率限制、认证失败、超时
│
第5天 ─── 核心参数调优
│   ├─ Temperature：0（确定）→ 1（平衡）→ 2（随机）
│   ├─ Top-P：候选词范围控制
│   ├─ Max Tokens：输出长度上限
│   ├─ Stop Sequences：遇到指定字符串就停止
│   └─ 综合实验：找最佳参数组合
│
第6天 ─── 角色设定与模板
│   ├─ System Prompt：设定人格和规则
│   ├─ 角色设定四步法：身份 → 能力 → 任务 → 约束
│   ├─ 提示词模板：固定结构 + 变量插值
│   └─ 提示词库管理
│
第7天 ─── 阶段复盘 📍（你在这里）
    ├─ 知识体系整理
    ├─ 速查表生成
    ├─ 自测查漏补缺
    └─ Week2 路线图
```

### 📊 学习数据

| 维度 | 数据 |
|------|------|
| **学习天数** | 7 天（含复盘） |
| **教程总字数** | ~120,000 字 |
| **代码文件** | 17 个 Python 文件 + 6 个 Java 文件 |
| **核心概念** | 30+ 个 |
| **实操实验** | 20+ 组 |
| **课堂练习** | 15+ 道 |

---

## 2. 知识体系地图

### 🗺️ Week1 知识全景图

```
                         ┌─────────────────────┐
                         │     🧠 理论基础      │
                         │  Token · 上下文窗口   │
                         │  BPE · 中英文差异    │
                         └─────────┬───────────┘
                                   │
            ┌──────────────────────┼──────────────────────┐
            │                      │                      │
    ┌───────┴───────┐    ┌────────┴────────┐    ┌───────┴───────┐
    │  ✍️ 提示词策略 │    │  🔌 API 实操     │    │  🔧 参数调优   │
    │               │    │                 │    │               │
    │ Zero-shot     │    │ 请求体结构       │    │ Temperature    │
    │ Few-shot      │    │ 响应体结构       │    │ Top-P          │
    │ CoT · ToT     │    │ curl/Python/Java │    │ Max Tokens     │
    │ Role Setting  │    │ 错误处理         │    │ Stop Sequences │
    │ Templates     │    │ 多轮对话         │    │ 组合实验       │
    └───────┬───────┘    └────────┬────────┘    └───────┬───────┘
            │                      │                      │
            └──────────────────────┼──────────────────────┘
                                   │
                         ┌────────┴────────┐
                         │  📦 提示词库 v1  │
                         │  结构化可复用模板  │
                         └─────────────────┘
```

### 🔗 知识点关联图

```
Token  (D1) ──→ 上下文窗口 (D1) ──→ Max Tokens (D5)
   │                                      │
   └──→ 中英文差异 (D1)                   └──→ finish_reason (D4)
            │                                      │
            └──→ API 请求体 (D4)                    ├─ "stop"   = 正常结束
                      │                             ├─ "length" = 被截断
                      ├── model                     └─ "content_filter" = 违规
                      ├── messages
                      │     ├── system ──→ 角色设定 (D6) → 提示词模板 (D6)
                      │     ├── user    ──→ Zero-shot (D2) → CoT (D3)
                      │     │                              → Few-shot CoT (D3)
                      │     │                              → Self-Consistency (D3)
                      │     │                              → ToT (D3)
                      │     └── assistant
                      └── parameters
                            ├── temperature (D4,D5)
                            ├── top_p (D5)
                            ├── max_tokens (D4,D5)
                            └── stop (D5)
```

---

## 3. 核心速查表（Cheat Sheet）

### 3.1 Token 速查

| 场景 | Token 消耗 | 说明 |
|------|-----------|------|
| 英文 100 词 | ~130 tokens | 1 词 ≈ 1.3 token |
| 中文 100 字 | ~180-250 tokens | 1 字 ≈ 2 tokens |
| "Hello, world!" | 4 tokens | 常见短语压缩好 |
| "你好世界" | 6 tokens | 中文常见词也压缩 |
| 生僻专业术语 | 拆成多个 tokens | 建议附英文术语降低消耗 |

**模型上下文窗口一览：**

| 模型 | 窗口大小 | 适合场景 |
|------|---------|---------|
| DeepSeek V3 | 128K | 长文档、代码库 |
| GPT-4o | 128K | 综合场景 |
| Claude Sonnet | 200K | 超长文档 |
| GPT-3.5 | 16K | 短对话、简单任务 |

### 3.2 提示词策略速查

| 策略 | 一句话 | 使用时机 | 示例 |
|------|--------|---------|------|
| **Zero-shot** | 不给例子直接问 | 简单任务、定义清晰的问题 | "将这段文字翻译成英文" |
| **Few-shot** | 给 2-3 个例子 | 格式特殊、需要模仿风格 | "如下格式输出：①...②..." |
| **Zero-shot CoT** | 加"一步一步思考" | 几乎**所有**推理问题 | "请一步一步思考" |
| **Few-shot CoT** | 给带推理的示例 | 专业领域、复杂推理 | 给 2 个带推导步骤的例子 |
| **Self-Consistency** | 跑多次取多数 | 数学、选择题、高可靠场景 | temperature=0.7, 跑 3-5 次 |
| **Tree-of-Thought** | 多路径探索+剪枝 | 开放性方案选择 | 3 条路径分别探索后评估 |

### 3.3 参数调优速查

| 参数 | 作用 | 默认值 | 推荐值 | 场景 |
|------|------|--------|--------|------|
| **temperature** | 随机性控制 | 1.0 | **0** | 代码生成、数学计算 |
| | | | **0.3** | 翻译、摘要、分类 |
| | | | **0.7** | 日常问答、平衡模式 |
| | | | **1.0-1.2** | 创意写作、头脑风暴 |
| **top_p** | 候选词范围 | 1.0 | 0.5-0.9（与 temp 搭配） | 需要稳定输出 |
| | | | 1.0（默认） | 一般场景 |
| **max_tokens** | 输出上限 | 4096 | 先设大再调小 | 避免被截断 |
| | | | 200-500 | 简短回复 |
| | | | 2000+ | 长文生成 |
| **stop** | 提前终止 | 无 | `["\n\n"]` | 控制格式 |
| | | | `["```"]` | 代码块结束时停止 |

### 3.4 参数调优口诀

```
代码数学 temp=0，保证精确不跑偏
翻译总结 temp=3，既准又活最自然
创意写作 temp=7，天马行空有新意
max_tokens 先设大，截断之后再调小
stop 用来控格式，JSON 代码最实用
```

### 3.5 API 速查

```python
# 请求体结构
{
    "model": "deepseek-chat",
    "messages": [
        {"role": "system", "content": "你是一个...（角色设定）"},
        {"role": "user",   "content": "用户问题"},
        {"role": "assistant", "content": "历史回复"},  # 多轮对话
        {"role": "user",   "content": "追问"}
    ],
    "temperature": 0.7,
    "max_tokens": 2048,
    "top_p": 1.0,
    "stop": None
}

# 响应体结构
{
    "choices": [{
        "message": {"content": "模型生成的文本"},
        "finish_reason": "stop"    # stop | length | content_filter
    }],
    "usage": {
        "prompt_tokens": 150,      # 输入消耗
        "completion_tokens": 50,   # 输出消耗
        "total_tokens": 200        # 总消耗
    }
}
```

### 3.6 错误处理速查

| HTTP 状态码 | 含义 | 原因 | 解决 |
|------------|------|------|------|
| 401 | Unauthorized | API Key 无效/过期 | 检查密钥 |
| 429 | Rate Limit | 请求过于频繁 | 加指数退避重试 |
| 503 | Service Unavailable | 服务过载 | 等待后重试 |
| finish_reason=length | 输出被截断 | max_tokens 太小 | 增大或拆分 |
| finish_reason=content_filter | 内容违规 | 触发安全过滤 | 调整提示词 |

---

## 4. 提示词库索引 v1

以下是 week1 积累的所有提示词模板的索引，你可以根据自己的需求扩展。

### 4.1 中文模板库

| 模板名称 | 适用场景 | 变量 | 来源 |
|---------|---------|------|------|
| **代码审查** | Review 代码质量 | `{code}` | Day6 |
| **翻译助手** | 中/英互译 | `{text}`, `{target_lang}` | Day6 |
| **SQL 生成** | 自然语言转 SQL | `{question}`, `{table_schema}` | Day6 |
| **错误分析** | 解释报错信息 | `{error_log}` | Day6 |
| **测试生成** | 自动生成单元测试 | `{class_name}`, `{scenarios}` | Day6 |
| **代码转换** | 语言翻译 | `{source_lang}`, `{target_lang}`, `{code}` | Day6 练习 |
| **结构化问答** | 固定格式输出 | `{question}` | Day2 |
| **思维链推理** | 复杂推理问题 | `{question}` | Day3 |
| **多方案决策** | 开放性方案评估 | `{problem}` | Day3 |
| **多轮对话** | 连续的上下文对话 | `{history}`, `{new_question}` | Day4 |

### 4.2 Python 模板示例

```python
# prompt_templates.py (详见 day6/code/prompt_templates.py)

TEMPLATES = {
    "代码审查": {
        "system": "你是一个资深代码审查专家。擅长发现安全漏洞、性能问题和代码异味。",
        "user": "请审查以下代码，列出所有问题并给出修复建议：\n\n```{language}\n{code}\n```"
    },
    "翻译助手": {
        "system": "你是一个专业翻译，精通技术文档翻译。保持原意，术语准确，符合目标语言习惯。",
        "user": "请将以下内容翻译成{target_lang}：\n\n{text}"
    },
    "SQL生成": {
        "system": "你是一个 SQL 专家。只输出 SQL，不要解释。使用标准 SQL 语法。",
        "user": "数据库表结构：\n{table_schema}\n\n问题：{question}\n\nSQL："
    }
}

def render(template_name: str, **kwargs):
    """根据模板名和变量生成完整的 messages 数组"""
    t = TEMPLATES[template_name]
    user_content = t["user"]
    for k, v in kwargs.items():
        user_content = user_content.replace(f"{{{k}}}", str(v))
    return [
        {"role": "system", "content": t["system"]},
        {"role": "user",   "content": user_content}
    ]
```

### 4.3 Java 模板示例

```java
// PromptTemplate.java (详见 day6/code/PromptTemplate.java)

public class PromptTemplate {
    
    private static final Map<String, TemplateDef> TEMPLATES = Map.of(
        "代码审查", new TemplateDef(
            "你是一个资深代码审查专家。擅长发现安全漏洞、性能问题和代码异味。",
            "请审查以下代码，列出所有问题并给出修复建议：\n\n```{language}\n{code}\n```"),
        "翻译助手", new TemplateDef(
            "你是一个专业翻译，精通技术文档翻译。保持原意，术语准确，符合目标语言习惯。",
            "请将以下内容翻译成{target_lang}：\n\n{text}"),
        "SQL生成", new TemplateDef(
            "你是一个 SQL 专家。只输出 SQL，不要解释。使用标准 SQL 语法。",
            "数据库表结构：\n{table_schema}\n\n问题：{question}\n\nSQL：")
    );
    
    public static List<Message> render(String name, Map<String, String> vars) {
        TemplateDef t = TEMPLATES.get(name);
        String userContent = t.userTemplate;
        for (var e : vars.entrySet()) {
            userContent = userContent.replace("{" + e.getKey() + "}", e.getValue());
        }
        return List.of(
            new Message("system", t.system),
            new Message("user", userContent)
        );
    }
}
```

---

## 5. 常见问题与排错手册

### 5.1 提示词相关

| 问题 | 可能原因 | 解决方案 |
|------|---------|---------|
| 模型答非所问 | 提示词太模糊 | 结构化模板：角色+任务+要求+格式 |
| 输出格式不对 | 没有指定格式 | 明确说出要的格式，给 1 个例子 |
| 输出太长/太短 | max_tokens 不当 | 先设大（4096），根据结果再调小 |
| 答案总是错的 | 推理能力不足 | 加"一步一步思考"（Zero-shot CoT） |
| 多次结果不一样 | temperature 太高 | 降到 0 — 0.3 |
| 模型"忘记"了上下文 | 窗口满了或没传历史 | 检查 messages 数组是否包含历史 |
| 回答太笼统 | 角色设定不够具体 | "你是一个资深 Java 架构师" > "你是专家" |

### 5.2 API 相关

| 问题 | 可能原因 | 解决方案 |
|------|---------|---------|
| 401 错误 | API Key 无效 | 检查密钥是否过期、环境变量是否正确 |
| 429 错误 | 请求太频繁 | 加指数退避重试（第1次等2s，第2次等4s...） |
| 请求超时 | 网络问题或超时太短 | timeout 设到 30s+ |
| JSON 解析失败 | 响应格式变了 | 检查 model 是否正确，查看 raw response |
| 返回空 content | 内容被过滤 | 检查 finish_reason 是否为 content_filter |
| token 消耗爆表 | 上下文太长 | 压缩输入，或改用大窗口模型 |

### 5.3 参数调优常见陷阱

```python
# 🚫 错误写法
temperature = 0    # 认为 0 最便宜（实际上 temperature 不影响价格）
max_tokens = 50    # 设太小导致 90% 的回复被截断
top_p = 0.1        # 过于保守，几乎只有最高概率词可选

# ✅ 正确做法
temperature = 0    # =确定，适合代码/数学（不影响价格）
max_tokens = 2048  # 够大多数场景，发现截断再调大
top_p = 1.0        # 默认值，改它等效于改 temperature
```

### 5.4 角色设定常见陷阱

```python
# 🚫 错误的角色设定
system_prompt = "你是一个助手"          # 太笼统
system_prompt = "你必须严格遵守以下50条规则..."  # 太长，核心被稀释

# ✅ 正确的角色设定
system_prompt = """
你是一个资深 Java 架构师。
- 精通 Spring Cloud、高并发设计、微服务
- 给出方案时对比至少2种方案的优缺点
- 用中文回答，优先用简单易懂的语言
"""
# 核心：身份 + 能力 + 任务 + 约束，4-6 句话完成
```

---

## 6. 自测题（Quiz）

以下题目覆盖 week1 全部核心知识点。每题先自己思考，再点击看答案。

### 基础知识

**Q1：中文 100 个字的 Token 消耗大约是英文 100 个词的几倍？**

<details>
<summary>点击查看答案</summary>

**1.5 — 2 倍**。中文 100 字 ≈ 180-250 tokens，英文 100 词 ≈ 130 tokens。
这就是为什么中文提示词建议简洁、配合英文术语更省 Token。
</details>

**Q2：为什么核心信息要放在提示词的开头或结尾？**

<details>
<summary>点击查看答案</summary>

因为**注意力机制存在"首位效应"和"近因效应"**，模型对开头和结尾的内容注意力更高，中间内容容易被"遗忘"。上下文窗口越长，这个效应越明显。
</details>

**Q3：BPE 编码的核心思想是什么？**

<details>
<summary>点击查看答案</summary>

BPE（Byte Pair Encoding）的核心思想是从字符/字节级别开始，**不断合并最常见的相邻对**形成新的 token。所以"Hello world"是常见组合→压缩成少量 tokens，而"超导量子比特"是生僻组合→被拆成多个 tokens。
</details>

### 提示词策略

**Q4：Zero-shot CoT 和 Few-shot CoT 的核心区别是什么？**

<details>
<summary>点击查看答案</summary>

- **Zero-shot CoT**：不给例子，直接加"请一步一步思考"或"Let's think step by step"——模型自己生成推理过程
- **Few-shot CoT**：给 2-3 个**带推理步骤的示例**，告诉模型"要多详细地推理"、"推理格式是什么"
- **经验法则**：80% 的场景 Zero-shot CoT 就够用；只有当输出格式有严格要求时才用 Few-shot CoT
</details>

**Q5：你的同事说"我试了 Few-shot 给了 10 个例子，结果反而变差了"，你怎么回答？**

<details>
<summary>点击查看答案</summary>

**3-shot 通常是最优的**。例子太多会导致：
1. 上下文窗口被"示例"浪费，留给实际问题的空间变少
2. 太多例子中噪音增多，模型"看花了眼"
3. 质量 > 数量：精心挑选的 3 个样例 > 随便选的 10 个样例
</details>

**Q6：Self-Consistency 为什么能提高结果的可靠性？**

<details>
<summary>点击查看答案</summary>

单次生成可能因采样随机性跑偏。Self-Consistency 跑 3-5 次，取**出现频率最高的答案**——这类似于"多轮投票机制"，偶然的错误答案会被多数正确答案"淹没"。适合数学题、选择题、需要高准确率的场景。
</details>

### API 与参数

**Q7：你调用 API 时收到了 finish_reason="length"，这是什么意思？怎么修正？**

<details>
<summary>点击查看答案</summary>

**"length" 表示输出被 max_tokens 截断了**——模型没说完就被迫停了。修正方法：
1. **增大 max_tokens**（设为当前值的 2 倍）
2. 如果是长文档，考虑**拆分**成多段生成
3. 检查 prompt 是否太长，"吃掉"了输出空间
</details>

**Q8：temperature=0 和 temperature=0.7 分别适合什么场景？**

<details>
<summary>点击查看答案</summary>

- **temperature=0**：每次输出相同。适合：代码生成、数学计算、SQL、翻译、格式转换——需要**精确确定**的场景
- **temperature=0.7**：适度随机。适合：日常问答、摘要、解释——需要**平衡稳定和自然**的场景
- **temperature=1.0-1.2**：高度随机。适合：创意写作、头脑风暴、生成多个方案——需要**多样性**的场景
</details>

**Q9：messages 数组中的 system、user、assistant 三种 role 分别有什么作用？**

<details>
<summary>点击查看答案</summary>

| Role | 作用 | 使用时机 |
|------|------|---------|
| **system** | 设定模型的人格、行为规则、约束条件 | 对话伊始，设置一次 |
| **user** | 用户输入的问题或指令 | 每次用户说话 |
| **assistant** | 模型的回复 | 每次模型回复（多轮对话中用于传递历史） |

核心要点：
- system 只需在第一条设置即可生效
- 多轮对话时，交替传入 user + assistant 让模型"记住"上下文
- system 是最"强"的指令，优先级高于 user prompt
</details>

### 综合应用

**Q10：请为一个"API 文档生成器"设计完整的 System Prompt（角色设定四步法）。**

<details>
<summary>点击查看答案</summary>

```markdown
你是一个资深 API 文档工程师（身份）。
- 精通 OpenAPI/Swagger 规范、RESTful 设计、Markdown 文档写作（能力）
- 根据用户提供的代码或 API 描述，生成标准的 OpenAPI 3.0 文档（任务）
- 输出必须是 YAML 格式
- 每个端点包含：路径、方法、参数说明、请求/响应示例
- 响应示例使用 200 OK 场景（约束）
```
</details>

### 得分对照表

| 答对题数 | 评级 | 建议 |
|---------|------|------|
| 10/10 | 🏆 全栈精通 | 可以进入 week2 学习 |
| 8-9/10 | 🌟 优秀 | 扫一眼错题的答案即可继续 |
| 6-7/10 | ✅ 良好 | 建议复习对应的知识点章节 |
| 4-5/10 | 📖 需要复习 | 建议重新阅读对应天的内容 |
| 0-3/10 | 🔄 建议重学 | 从头过一遍 week1 的重点概念 |

---

## 7. Week2 学习路线预览

恭喜你完成 **Week1：基础入门**！接下来你将进入更实战的阶段。

### Week2：实战进阶 🚀

```
第8天 ─── RAG（检索增强生成）
│   ├─ 为什么需要 RAG？（解决知识过时 + 幻觉问题）
│   ├─ Embedding 基础概念
│   ├─ 文档分块策略（Chunking）
│   ├─ 向量数据库入门
│   └─ 实战：构建一个简单的"文档问答"系统
│
第9天 ─── 工具调用（Function Calling）
│   ├─ 让模型调用外部工具
│   ├─ 天气预报：第一次 Function Call
│   ├─ 多工具编排
│   └─ 实战：构建一个"智能助手"可以查天气+算数学+搜资料
│
第10天 ── 结构化输出
│   ├─ JSON Mode vs JSON Schema
│   ├─ 强制结构化输出的三种方法
│   ├─ Pydantic + LLM 最佳实践
│   └─ 实战：从非结构化文本中提取结构化数据
│
第11天 ── 多轮对话与状态管理
│   ├─ 对话状态设计模式
│   ├─ 记忆管理（滑动窗口 vs 摘要 vs 向量记忆）
│   └─ 实战：构建一个"对话式"智能客服
│
第12天 ── 项目实战：AI 智能助手
│   ├─ 需求分析 + 架构设计
│   ├─ 结合前 11 天所有技术
│   └─ 完整项目：可运行的 AI 助手
│
第13天 ── 部署与最佳实践
│   ├─ 模型部署选项（API / 本地 / Serverless）
│   ├─ 性能优化（缓存、批处理、流式输出）
│   ├─ 成本控制（Token 管理策略）
│   └─ 安全与合规
│
第14天 ── 阶段复盘
    ├─ Week2 知识体系整理
    ├─ 完整的 AI 应用开发流程
    └─ 后续学习路线建议
```

### Week2 相比 Week1 的变化

| 维度 | Week1（基础） | Week2（实战） |
|------|-------------|-------------|
| **重点** | 理解和调优提示词 | 构建完整的 AI 应用 |
| **代码量** | 少量实验代码 | 完整项目代码 |
| **技术栈** | API + 参数 | RAG + Function Calling + 向量库 |
| **输出** | 知识积累 | 可运行的 AI 助手 |
| **难度** | ⭐⭐ | ⭐⭐⭐ |

### 预习建议

在开始 week2 之前，可以提前了解：

1. **向量数据库** — 搜索 "ChromaDB" 或 "FAISS" 概述
2. **Function Calling** — 搜索 "OpenAI function calling 教程"
3. **LangChain 或 Spring AI** — Week2 会用到框架简化开发

---

## 8. 今日小结

### 核心知识点

| 维度 | 一句话 |
|------|--------|
| **Token** | 模型理解的基本单位，中文≈英文1.5-2倍 |
| **上下文窗口** | 模型的"短期记忆"，核心信息放开头结尾 |
| **提示词策略** | Zero-shot(简单) → CoT(推理) → Self-Consistency(可靠) → ToT(方案) |
| **API 结构** | messages 分 system/user/assistant，参数控制输出质量 |
| **参数调优** | temp=0(精确) ~ temp=1(创意)，max_tokens 先大后小 |
| **角色设定** | 身份→能力→任务→约束，4句话说完 |
| **提示词库** | 固定结构+变量插值，一次编写反复使用 |

### Week1 金句集锦

> **第1天：** Token 不是字，不是词，是模型看世界的"像素"
>
> **第2天：** 同一个模型、同一问题，不同提示词 → 完全不同的输出质量
>
> **第3天：** CoT 有效不是因为模型会推理，而是因为它**模仿了推理的文本模式**
>
> **第4天：** 调 API 就像开车——懂仪表盘（响应体）比会踩油门（发请求）更重要
>
> **第5天：** temperature=0 不是"关掉随机性"，是"把随机性压到最低"
>
> **第6天：** 好的角色设定，让模型从"什么都知道的助手"变成"某个领域的专家"

### 今日检查清单

- [ ] 浏览一遍 Week1 全景回顾，回忆每个知识点
- [ ] 下载/打印速查表（Cheat Sheet），日常用
- [ ] 搭建自己的提示词库 v1（基于模板修改）
- [ ] 完成 10 道自测题，查漏补缺
- [ ] 回顾做错的题对应的知识点
- [ ] 预览 Week2 内容，决定是否继续
- [ ] 在 `~/ai-learning/week1/notes/day7.md` 记录自己的学习反思

### 思考题（可选）

> 学完 Week1 后，回顾你最初使用 AI 的方式（ChatGPT/DeepSeek/Kimi 等），
> 你觉得自己最大的改变是什么？原来最常踩的坑是什么？
> 把这些写在学习笔记里，Week2 结束后再看，你会发现自己进步了多少。

---

> 📝 **学习笔记：** 在 `~/ai-learning/week1/notes/day7.md` 中记录今天的收获
> ❓ **遇到问题：** 随时问我
> 🚀 **学有余力：** 把你工作中最常用的 3 个 AI 场景写成模板，加入你的提示词库
> 💡 **感谢陪伴：** Week1 到此结束，你已经在 AI 应用开发的道路上迈出了坚实的第一步！
