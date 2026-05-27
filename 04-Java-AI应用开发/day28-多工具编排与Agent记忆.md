# Day 28：多工具编排 + Agent 记忆系统

> **核心主题**：在 Day 27 ReAct 模式基础上，构建支持多工具组合调用、长短记忆管理、错误自修正的完整 Agent 系统。

---

## 1. 今日目标

| 模块 | 内容 | 难度 |
|------|------|------|
| 多工具编排 | 至少 4 个工具，编排器自动选择工具链 | ⭐⭐⭐ |
| 长短记忆 | 短期（InMemory）+ 长期（PostgreSQL/SQLite） | ⭐⭐⭐ |
| 错误自修正 | 工具失败自动重试最多 3 次 + 备选策略 | ⭐⭐⭐ |
| 文档工具 | 读写临时笔记（DocumentTool） | ⭐⭐ |
| 演示场景 | 查天气 → 温度转换 → 记录笔记 | ⭐⭐ |

---

## 2. 系统架构

```
用户请求
    │
    ▼
┌─────────────────────────────────────┐
│          AgentController            │  ← REST API 入口
│  GET /chat?msg=...                  │
└────────────┬────────────────────────┘
             │
             ▼
┌─────────────────────────────────────┐
│       OrchestratorAgent             │  ← 编排器（大脑）
│  1. 获取上下文（短期+长期）          │
│  2. LLM 生成工具调用计划             │
│  3. 解析 TOOL_CALL                  │
│  4. 依次执行工具（含重试）           │
│  5. LLM 生成最终回复                │
└──┬───┬───┬───┬───┬─────────────────┘
   │   │   │   │   │
   ▼   ▼   ▼   ▼   ▼
┌──┐ ┌──┐ ┌──┐ ┌──┐ ┌──────────────┐
│天气│ │计算│ │日期│ │文档│ │ 记忆服务     │
│工具│ │工具│ │工具│ │工具│ │ 短期+长期    │
└──┘ └──┘ └──┘ └──┘ └──────────────┘
                                    │
                                    ▼
                            ┌──────────────┐
                            │  PostgreSQL   │
                            │  长期记忆表   │
                            └──────────────┘
```

---

## 3. 核心代码解析

### 3.1 记忆系统（MemoryService）

```java
// ConversationMemory.java — 会话记忆实体
public class ConversationMemory {
    private String sessionId;
    private List<Message> messages;  // role + content
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

// MemoryService.java — 记忆服务
@Service
public class MemoryService {
    // 短期记忆：ConcurrentHashMap（当前 JVM 内存）
    private final Map<String, ConversationMemory> shortTermMemory = new ConcurrentHashMap<>();
    
    // 长期记忆：PostgreSQL
    private final JdbcTemplate jdbcTemplate;
    
    // 自动裁剪：超 4000 tokens 时删除最旧消息（保留 system 消息）
    private void trimToTokenLimit(ConversationMemory mem) { ... }
}
```

**关键设计**：
- 短期记忆用 `ConcurrentHashMap`，读写快、无 IO
- 长期记忆用 PostgreSQL `conversation_memory` 表
- `getFullContext()` 合并短期+长期，短期不足时补充长期
- 自动裁剪：超过 4000 tokens 估算上限时，删除最早的 user/assistant 消息

### 3.2 工具层（4+ 工具）

| 工具类 | 方法 | 用途 |
|--------|------|------|
| `WeatherTool` | `getWeather(city)` | 查询实时天气 |
| `CalculatorTool` | `calculate(expr)` / `celsiusToFahrenheit(c)` | 计算与温度转换 |
| `DateTimeTool` | `getCurrentDateTime()` / `daysBetween(d1, d2)` | 日期时间 |
| `DocumentTool` | `saveNote(title, content)` / `readNote(title)` / `listNotes()` | 笔记管理 |

**DocumentTool 亮点**：
```java
// 笔记保存在 ~/.agent-notes/ 目录，Markdown 格式
public String saveNote(String title, String content) {
    Path filePath = NOTES_DIR.resolve(safeTitle + ".md");
    String noteContent = String.format("""
        # %s
        创建时间：%s
        ---
        %s
        """, title, LocalDateTime.now(), content);
    Files.writeString(filePath, noteContent);
    return "✅ 笔记《" + title + "》已保存";
}
```

### 3.3 编排器（OrchestratorAgent）

编排器是整个系统的核心，它负责：

1. **接收用户请求** → 从记忆服务获取上下文
2. **LLM 生成计划** → 系统提示词指导 LLM 输出 `TOOL_CALL:` 格式
3. **解析工具调用** → 正则提取 `TOOL_CALL: 工具名 | 参数1 | 参数2`
4. **执行工具链** → 按顺序执行，失败自动重试
5. **结果组合** → 将工具结果交给 LLM 生成自然语言回答

**系统提示词关键部分**：
```
当你需要调用工具时，请使用以下格式：
TOOL_CALL: 工具名称 | 参数1 | 参数2 | ...

例如：
TOOL_CALL: getWeather | Beijing
TOOL_CALL: celsiusToFahrenheit | 25
TOOL_CALL: saveNote | 北京天气 | 2024年北京天气记录...
```

**工具调用解析**：
```java
private List<ToolCall> parseToolCalls(String plan) {
    Pattern pattern = Pattern.compile(
        "TOOL_CALL:\\s*(\\w+)\\s*\\|?\\s*(.*?)(?=TOOL_CALL:|$)", 
        Pattern.DOTALL
    );
    Matcher matcher = pattern.matcher(plan);
    while (matcher.find()) {
        String toolName = matcher.group(1).trim();
        String argsStr = matcher.group(2).trim();
        // 按 | 分割参数
        List<String> args = parseArgs(argsStr);
        calls.add(new ToolCall(toolName, args));
    }
}
```

### 3.4 错误自修正机制

```java
private String executeToolWithRetry(ToolCall call, int maxRetries) {
    for (int attempt = 1; attempt <= maxRetries; attempt++) {
        try {
            String result = func.execute(call.args);
            if (result.startsWith("错误") || result.startsWith("❌")) {
                throw new RuntimeException("工具返回错误: " + result);
            }
            return result;
        } catch (Exception e) {
            if (attempt < maxRetries) {
                // 调用 LLM 修正参数
                String fixedArgs = tryFixArgs(call, e.getMessage());
                if (fixedArgs != null) {
                    call.args = parseArgs(fixedArgs);
                }
            }
        }
    }
    return "❌ 工具执行失败（已重试" + maxRetries + "次）";
}
```

**重试策略**：
- 同一工具最多重试 3 次
- 每次重试前调用 LLM 修正参数（`tryFixArgs`）
- 连续失败返回友好错误信息

---

## 4. 演示场景

用户输入："帮我查北京天气，然后计算 25°C 是多少华氏度，最后记录下来"

### 执行流程

```
步骤 1: LLM 生成计划
THOUGHT: 用户需要3个操作：查天气、温度转换、记录笔记
TOOL_CALL: getWeather | Beijing
TOOL_CALL: celsiusToFahrenheit | 25
TOOL_CALL: saveNote | 北京天气与温度转换 | 北京天气：...\n25°C = 77°F

步骤 2: 工具执行
  ✅ getWeather → 北京天气：25°C，晴
  ✅ celsiusToFahrenheit → 25.0°C = 77.0°F
  ✅ saveNote → 笔记已保存

步骤 3: LLM 生成回复
"好的！已为您完成以下操作：
1. 查询了北京的实时天气：25°C，晴
2. 温度转换：25°C = 77°F
3. 已将结果保存到笔记《北京天气与温度转换》中"
```

---

## 5. API 接口

| 端点 | 方法 | 参数 | 说明 |
|------|------|------|------|
| `/chat` | GET | `msg` (必填), `session` (选填) | 对话（带记忆） |
| `/chat/new` | GET | `session` (选填) | 清空记忆 |
| `/chat/memory` | GET | `session` (选填) | 查看历史 |
| `/chat/sessions` | GET | 无 | 列出所有会话 |

**示例请求**：
```bash
# 对话
curl "http://localhost:8090/chat?msg=北京天气怎么样&session=demo1"

# 清空记忆
curl "http://localhost:8090/chat/new?session=demo1"

# 查看历史
curl "http://localhost:8090/chat/memory?session=demo1"
```

---

## 6. 项目文件结构

```
day28/
├── pom.xml                          # Maven 配置（Spring Boot 3.4.4, Spring AI 1.0.0-M6）
├── src/main/
│   ├── resources/
│   │   └── application.yml          # 端口 8090, PostgreSQL 连接
│   └── java/com/ai/learning/agent/
│       ├── MultiAgentApplication.java   # Spring Boot 入口
│       ├── memory/
│       │   ├── ConversationMemory.java  # 记忆实体
│       │   └── MemoryService.java       # 短期+长期记忆服务
│       ├── tools/
│       │   ├── CalculatorTool.java      # 计算器
│       │   ├── WeatherTool.java         # 天气查询
│       │   ├── DateTimeTool.java        # 日期时间
│       │   └── DocumentTool.java        # 文档笔记
│       ├── orchestrator/
│       │   └── OrchestratorAgent.java   # 编排器
│       └── controller/
│           └── AgentController.java     # REST API
└── python/
    └── multi_agent_demo.py          # Python 对照版
```

---

## 7. 数据库配置

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ailearning
    username: aiuser
    password: aipass
  autoconfigure:
    exclude:
      - org.springframework.ai.autoconfigure.openai.OpenAiEmbeddingAutoConfiguration
```

数据库表结构（自动创建）：
```sql
CREATE TABLE IF NOT EXISTS conversation_memory (
    id SERIAL PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_conversation_memory_session
ON conversation_memory(session_id, created_at);
```

---

## 8. 运行方式

### Java 版
```bash
# 设置 API Key
export DEEPSEEK_API_KEY=sk-your-key-here

# 编译运行
cd ~/ai-learning/04-Java-AI应用开发/code/day28
mvn spring-boot:run
```

### Python 版
```bash
# 安装依赖
pip install openai requests

# 设置 API Key
export DEEPSEEK_API_KEY=sk-your-key-here

# 运行
cd ~/ai-learning/04-Java-AI应用开发/code/day28
python python/multi_agent_demo.py
```

---

## 9. 关键设计决策

### 为什么用 TOOL_CALL 格式而不是 Function Calling？
- Spring AI 1.0.0-M6 的 Function Calling 需要定义 `@Bean` + `@Tool`，不够灵活
- 自定义 `TOOL_CALL:` 格式让 LLM 自由组合工具链，编排器自行解析执行
- 这种模式更接近 Agent 的「思考-行动-观察」循环

### 为什么短期+长期双存储？
- **短期**：当前会话上下文，读写快，支持裁剪（~4000 tokens）
- **长期**：全量历史记录，持久化到 PostgreSQL，支持跨会话
- 合并策略：短期优先，不足时补充长期

### 为什么用 3 次重试？
- 第 1 次：正常执行
- 第 2 次：参数格式修正（LLM 重新解析）
- 第 3 次：最终尝试
- 3 次后仍失败 → 返回友好错误，不阻塞后续工具

---

## 10. 进阶思考

1. **工具并行**：当前是串行执行，可以改成并行（CompletableFuture）提升性能
2. **工具依赖图**：部分工具有依赖关系（如先查天气再记录），可以构建 DAG
3. **人机交互**：工具执行不确定时可询问用户确认
4. **记忆持久化增强**：可以加 Redis 做缓存层，加速短期记忆访问
5. **多模型支持**：编排器用更强模型（DeepSeek），工具调用用更轻模型

---

## 11. 常见问题

**Q: PostgreSQL 连接不上怎么办？**
- 检查 PostgreSQL 是否运行：`sudo service postgresql status`
- 检查数据库和用户：`sudo -u postgres psql -c "CREATE DATABASE ailearning; CREATE USER aiuser WITH PASSWORD 'aipass'; GRANT ALL ON DATABASE ailearning TO aiuser;"`
- 如果不想用数据库，MemoryService 会自动降级为纯短期记忆

**Q: 工具执行一直失败？**
- 检查网络连接（天气工具需要访问 wttr.in）
- 检查 API Key 是否设置正确
- 查看日志中的详细错误信息

**Q: LLM 不输出 TOOL_CALL 格式？**
- 检查系统提示词中的格式说明
- 在 `generateToolPlan` 方法中加更多示例
- 降低 temperature（0.7 → 0.3）提高格式一致性

---

## 12. 总结

Day 28 构建了一个完整的 Agent 系统：

| 能力 | 实现方式 | 效果 |
|------|----------|------|
| **多工具编排** | LLM 生成计划 + 编排器执行 | 用户一句话触发多个工具 |
| **长短记忆** | InMemory + PostgreSQL | 跨会话保持上下文 |
| **错误自修正** | 3 次重试 + LLM 参数修正 | 提高工具调用成功率 |
| **文档工具** | 文件系统 + Markdown | 读写临时笔记 |
| **Python 对照** | 相同架构的 Python 实现 | 便于对比学习 |

**下节预告**：Day 29 — 多 Agent 协作系统（Multi-Agent Collaboration）
