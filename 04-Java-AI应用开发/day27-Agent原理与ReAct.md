# Day 27：Agent 原理与 ReAct 模式 — 手动实现思考-行动循环

## 1. 为什么需要 Agent？

大语言模型（LLM）虽然强大，但有三个核心局限：
1. **知识截止**：训练数据有时间边界，不知道最新信息
2. **无法精确计算**：复杂的数学运算容易出错
3. **无法感知实时**：不知道当前时间、天气等实时数据

**Agent** 通过让 LLM 调用外部工具来解决这些问题。但 Day 19 使用了 Spring AI 的 `@Tool` 注解自动完成工具调用，对开发者隐藏了底层机制。今天我们将**手动实现**完整的 ReAct（Reasoning + Acting）循环，深入理解 Agent 的工作原理。

## 2. 什么是 ReAct 模式？

ReAct（Reasoning + Acting）由 Shunyu Yao 等人在 2022 年提出，核心思想是让 LLM 交替进行推理和行动：

```
问题 → 思考(Thought) → 行动(Action) → 观察(Observation) → 继续思考 → ... → 最终答案(Answer)
```

**标准 ReAct 格式**：
```
Thought: 我需要查询北京今天的天气
Action: get_weather
Action Input: {"city": "北京"}
Observation: ☀️ 晴 25°C ...
Thought: 我现在有天气信息了，可以回答用户
Answer: 北京今天天气晴朗，温度25°C...
```

### 与 Day 19 的对比

| 特性 | Day 19（自动工具调用） | Day 27（手动 ReAct） |
|------|----------------------|---------------------|
| 工具注册 | `@Tool` 注解 + MethodToolCallbackProvider | 手动 Map 注册 |
| 工具调用 | Spring AI 自动解析 LLM 响应并调用 | 我们自己解析 Thought/Action/Observation |
| 可见性 | 最终结果（内部过程不可见） | 完整思考过程可见 |
| 控制力 | Spring AI 框架控制 | 完全自己控制 |
| 复杂度 | 低（框架处理） | 中（自己实现循环） |

## 3. 项目结构

```
day27/
├── pom.xml
├── src/main/
│   ├── resources/
│   │   └── application.yml
│   └── java/com/example/react/
│       ├── ReActApplication.java          # 入口
│       ├── core/
│       │   └── ReActAgent.java            # 核心循环逻辑
│       ├── tools/
│       │   ├── ToolRegistry.java          # 工具注册中心
│       │   ├── CalculatorTool.java        # 计算器
│       │   ├── WeatherTool.java           # 天气
│       │   └── DateTimeTool.java          # 日期时间
│       └── controller/
│           └── AgentController.java       # REST API
└── python/
    └── react_agent_demo.py                # Python 对照版
```

## 4. 核心实现详解

### 4.1 工具注册中心（ToolRegistry）

与 Day 19 的 `@Tool` 注解不同，我们使用 Map 手动注册：

```java
@Component
public class ToolRegistry {
    private final Map<String, ToolEntry> tools = new LinkedHashMap<>();

    public ToolRegistry(CalculatorTool calculator, WeatherTool weather, DateTimeTool dateTime) {
        // 手动注册每个工具
        register("calculate", "执行数学计算...", 
            args -> calculator.calculate(args.get("expression").asText()));
        register("get_weather", "查询天气...", 
            args -> weather.getWeather(args.get("city").asText()));
        register("get_current_time", "获取当前时间...", 
            args -> dateTime.getCurrentTime());
    }

    public record ToolEntry(String name, String description, 
                           Function<JsonNode, String> executor) {}
}
```

**优点**：完全掌控工具的注册和调用逻辑，不依赖框架自动注入。

### 4.2 ReAct 循环（ReActAgent）

这是项目的核心——手动实现 Thought → Action → Observation 循环：

```java
public ReActResult execute(String userMessage) {
    List<Message> messages = new ArrayList<>();
    messages.add(new Message("system", systemPrompt));
    messages.add(new Message("user", userMessage));
    
    for (int i = 0; i < MAX_ITERATIONS; i++) {
        // Step 1: 调用 LLM
        String response = callLLM(messages);
        
        // Step 2: 检查是否有最终答案
        String answer = extractAnswer(response);
        if (answer != null) return new ReActResult(answer, steps, i, true);
        
        // Step 3: 解析 Thought 和 Action
        String thought = extractThought(response);
        String action = extractAction(response);
        String actionInput = extractActionInput(response);
        
        // Step 4: 执行工具
        String observation = executeTool(action, actionInput);
        
        // Step 5: 记录并继续
        steps.add(new ReActStep(thought, action, actionInput, observation));
        messages.add(new Message("assistant", response));
        messages.add(new Message("user", "Observation: " + observation));
    }
}
```

**关键设计**：
- **对话历史管理**：每次迭代后将 LLM 响应和观察结果追加到消息列表，维持上下文
- **正则解析**：用正则表达式从 LLM 响应中提取 Thought、Action、Action Input
- **最大迭代限制**：防止无限循环（默认 10 轮）

### 4.3 LLM 响应解析

LLM 返回的文本需要解析为结构化数据：

| 提取内容 | 正则表达式 | 示例匹配 |
|---------|-----------|---------|
| Answer | `(?:Answer\|最终答案)\s*[:：]\s*(.*)` | `Answer: 北京天气晴朗...` |
| Thought | `Thought\s*[:：]\s*(.*?)...` | `Thought: 我需要查询天气` |
| Action | `Action\s*[:：]\s*(\w+)` | `Action: get_weather` |
| Action Input | `Action\s+Input\s*[:：]\s*(\{.*?\})` | `Action Input: {"city":"北京"}` |

**重要**：系统提示词中的格式要求必须严格，LLM 才能稳定输出可解析的格式。

### 4.4 控制器（REST API）

提供两个端点：

```java
@GetMapping("/react")        // 返回完整 ReAct 过程
@GetMapping("/react/answer") // 只返回最终答案
```

`/react` 端点返回 JSON：
```json
{
  "question": "北京今天天气怎么样？",
  "answer": "北京今天天气晴朗，温度25°C...",
  "steps": [
    {
      "thought": "我需要查询北京的天气",
      "action": "get_weather",
      "action_input": "{\"city\": \"北京\"}",
      "observation": "🌡️ 北京 当前天气..."
    }
  ],
  "total_iterations": 1,
  "success": true,
  "elapsed_ms": 2345
}
```

### 4.5 工具实现

**计算器工具**：使用 `BigDecimal` 进行精确计算，避免浮点精度问题
**天气工具**：模拟数据（真实场景可对接第三方天气 API）
**日期时间工具**：使用 Java `LocalDateTime`，无需外部 API

所有工具**不带 `@Tool` 注解**，由 `ToolRegistry` 手动注册。

## 5. 与 Day 19 的对比总结

### Day 19（自动工具调用）

```java
// ✅ 简单：@Tool 注解 + ChatClient.defaultTools()
// ❌ 黑盒：看不到工具调用过程
// ❌ 框架控制：Spring AI 决定何时调用什么工具
```

### Day 27（手动 ReAct 循环）

```java
// ❌ 复杂：需要自行解析 LLM 响应
// ✅ 透明：完整展示思考过程
// ✅ 完全控制：自己管理循环逻辑
// ✅ 教育意义：深入理解 Agent 原理
```

## 6. 关键要点

1. **Prompt Engineering**：系统提示词的格式直接影响 LLM 输出的质量
2. **解析健壮性**：LLM 输出不稳定，正则表达式需要容错
3. **上下文管理**：将历史消息（含 Observation）持续传入 LLM
4. **终止条件**：检测最终答案或超过最大迭代次数

## 7. 运行测试

### Java 版
```bash
cd ~/ai-learning/04-Java-AI应用开发/code/day27
export DEEPSEEK_API_KEY='sk-...'
mvn spring-boot:run
# 访问 http://localhost:8089/react?msg=北京天气
# 访问 http://localhost:8089/react?msg=计算12345×6789
# 访问 http://localhost:8089/react?msg=现在几点了
# 访问 http://localhost:8089/react?msg=5公里等于多少米
```

### Python 版
```bash
cd ~/ai-learning/04-Java-AI应用开发/code/day27/python
export DEEPSEEK_API_KEY='sk-...'
pip install requests
python react_agent_demo.py
```

## 8. 扩展思考

1. **多工具协作**：如何让 Agent 在一次回答中调用多个工具？
2. **错误处理**：工具执行失败时如何让 Agent 重试？
3. **记忆机制**：如何让 Agent 记住之前的推理步骤？
4. **工具选择优化**：如何让 LLM 更准确地选择合适的工具？
5. **混合模式**：将手动 ReAct 与 Spring AI 的自动工具调用结合使用

---

> **提示**：Day 19 和 Day 27 展示了两种不同的 Agent 实现方式——自动 vs 手动。理解两者的差异，能够帮助你在实际项目中选择合适的方案。
