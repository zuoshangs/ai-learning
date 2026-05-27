# Day 30：多 Agent 协作系统 — Orchestrator-Worker 模式

> **目标**：实现完整的 Java 多 Agent 协作系统，支持路由分发、错误隔离、结果汇总
> **日期**：2026-05-28
> **前置**：Java 21, Spring Boot 3.4.4, Maven

---

## 1. 核心架构

### 设计模式：Orchestrator-Worker

```
用户输入 → Orchestrator (路由+调度+容错)
               │
       ┌───────┼───────┬───────┬───────┐
       ▼       ▼       ▼       ▼       ▼
   天气     计算     搜索     笔记     时间
   Worker   Worker   Worker   Worker   Worker
               │
               ▼
         结果汇总 → 用户
```

| 角色 | 职责 |
|------|------|
| **Orchestrator** | 分析用户意图 → 选择 Worker → 分发任务 → 容错 → 汇总 |
| **Worker** | 单一能力单元，只负责一种任务 |
| **Agent 接口** | 统一协议（getName / canHandle / execute） |

### 核心设计原则

1. **统一接口**（Agent.java）：
   - `getName()` — Agent 唯一标识
   - `canHandle(AgentMessage)` — 判断是否能处理
   - `execute(AgentMessage)` — 执行业务逻辑

2. **错误隔离**：
   - 每个 Worker 的 execute() 内部捕获所有异常
   - 返回 `AgentResult` 带 success/fail 标记
   - Orchestrator 对每个 Worker 有单独的 try-catch

3. **路由策略**：
   - `canHandle` 分析关键词 → 匹配的 Worker 都被选中
   - 无匹配 → 兜底 Search Worker
   - 多匹配 → 并行执行（如"北京天气和25×40"同时触发 weather + calculator）

---

## 2. 项目结构

```
multi-agent/
├── pom.xml
├── src/main/resources/application.yml
└── src/main/java/com/ai/learning/multiagent/
    ├── MultiAgentApplication.java      # 启动类
    ├── core/
    │   ├── Agent.java                  # Agent 接口
    │   ├── AgentMessage.java           # 消息模型（内含 MessageType 枚举）
    │   └── AgentResult.java            # 执行结果（success/data/error）
    ├── worker/
    │   ├── WeatherWorker.java          # 天气查询（wttr.in）
    │   ├── CalculatorWorker.java       # 四则运算（自研解析器）
    │   ├── SearchWorker.java           # 搜索（模拟）
    │   ├── NoteWorker.java             # 笔记管理（内存）
    │   └── TimeWorker.java             # 日期时间
    ├── orchestrator/
    │   ├── OrchestratorAgent.java      # 编排器核心
    │   └── OrchestrationResult.java    # 编排结果
    └── controller/
        └── AgentController.java        # REST API
```

---

## 3. 核心代码实现

### 3.1 Agent 接口

```java
public interface Agent {
    String getName();
    boolean canHandle(AgentMessage message);
    AgentResult execute(AgentMessage message);
}
```

### 3.2 AgentMessage 消息模型

```java
public class AgentMessage {
    public enum MessageType { REQUEST, RESPONSE, ERROR }

    private final String id;         // 消息唯一ID
    private final String source;     // 发送方
    private final String target;     // 接收方
    private final MessageType type;  // 类型
    private final String payload;    // 消息内容
    private final Instant timestamp; // 时间戳
}
```

### 3.3 WeatherWorker 示例

```java
@Component
public class WeatherWorker implements Agent {
    @Override
    public String getName() { return "weather"; }

    @Override
    public boolean canHandle(AgentMessage message) {
        String payload = message.getPayload().toLowerCase();
        return payload.contains("天气") || payload.contains("气温");
    }

    @Override
    public AgentResult execute(AgentMessage message) {
        try {
            String city = extractCity(message.getPayload());
            String url = "https://wttr.in/" + city + "?format=%25C+%25t+%25w+%25h";
            // ... HTTP 请求
            return AgentResult.ok(getName(), city + "天气: " + result);
        } catch (Exception e) {
            return AgentResult.fail(getName(), "天气查询失败: " + e.getMessage());
        }
    }
}
```

### 3.4 OrchestratorAgent 编排器

```java
@Component
public class OrchestratorAgent {
    private final List<Agent> workers;

    public OrchestratorAgent() {
        this.workers = List.of(
            new WeatherWorker(),
            new CalculatorWorker(),
            new SearchWorker(),
            new NoteWorker(),
            new TimeWorker()
        );
    }

    public OrchestrationResult process(String userInput) {
        // Step 1: 路由 —— 分析意图
        List<Agent> selected = selectWorkers(userInput);

        // Step 2: 并行执行（错误隔离）
        List<AgentResult> results = new ArrayList<>();
        for (Agent worker : selected) {
            try {
                results.add(worker.execute(request));
            } catch (Exception e) {
                results.add(AgentResult.fail(worker.getName(), "异常: " + e.getMessage()));
            }
        }

        // Step 3: 汇总
        return new OrchestrationResult(true, summary, results, elapsedMs);
    }
}
```

### 3.5 CalculatorWorker — 自研四则运算解析器

Java 21 已移除 Nashorn 引擎，因此自研简单解析器：

```java
private double evaluate(String expr) {
    // 1. 先算乘除 (* /)
    // 2. 再算加减 (+ -)
    // 支持: 25*40+10, 123+456*2 等
    // 结果: 25*40+10 = 1010
    //       123+456*2 = 1035
}
```

---

## 4. API 测试

### 单 Agent 路由

```bash
# 天气 → 路由到 weather
curl --get --data-urlencode "query=北京天气" http://localhost:8080/api/agent/ask
# → {"agentName":"weather", "data":"北京天气: Sunny +23°C ↗15km/h 38%"}

# 计算 → 路由到 calculator
curl --get --data-urlencode "query=25*40+10" http://localhost:8080/api/agent/ask
# → {"agentName":"calculator", "data":"25*40+10 = 1010"}

# 时间 → 路由到 time
curl --get --data-urlencode "query=现在几点" http://localhost:8080/api/agent/ask
# → {"agentName":"time", "data":"当前时间: 2026年05月28日 07:44:06 星期四"}

# 笔记 → 路由到 note
curl --get --data-urlencode "query=记住明天开会" http://localhost:8080/api/agent/ask
# → {"agentName":"note", "data":"已保存笔记: 记住明天开会"}
```

### 多 Agent 协作（路由到多个 Worker）

```bash
# 同时包含"天气"和"计算"关键词 → 同时路由到 weather + calculator
curl --get --data-urlencode "query=北京天气和25*40等于多少" http://localhost:8080/api/agent/ask
```

返回：
```json
{
  "success": true,
  "summary": "【weather】\n北京天气: Sunny +23°C ↘17km/h 38%\n---\n【calculator】\n25*40 = 1000",
  "elapsedMs": 896
}
```

---

## 5. 错误隔离机制

单个 Worker 失败不影响整体：

```java
// 每个 Worker 独立 try-catch
for (Agent worker : selected) {
    try {
        AgentResult result = worker.execute(request);
        results.add(result);
    } catch (Exception e) {
        // Worker 抛异常只影响自己，其他 Worker 继续执行
        results.add(AgentResult.fail(worker.getName(), "异常: " + e.getMessage()));
    }
}
```

---

## 6. Python 对照版

```python
class OrchestratorAgent:
    def __init__(self):
        self.workers = [WeatherWorker(), CalculatorWorker(), ...]

    def process(self, text):
        selected = [w for w in self.workers if w.can_handle(text)]
        results = [w.execute(text) for w in selected]
        return {"summary": "\n---\n".join(r["data"] for r in results)}
```

运行：
```bash
cd code/day30
python python/multi_agent_demo.py
```

---

## 7. 与 Day 27-29 的关联

| Day | 核心概念 | Day 30 的升级 |
|-----|---------|--------------|
| Day 27 手动 ReAct | Thought→Action→Observation→Answer | Automator 自动路由 |
| Day 28 多工具编排 | 单一 Agent 调多种工具 | 多 Agent 各自擅长一种 |
| Day 29 Dify 平台 | 可视化编排 LLM 工作流 | Java 原生实现类似架构 |

---

## 8. 产出总结

| 项目 | 文件 | 版本 | 状态 |
|------|------|------|------|
| Agent 接口 | `core/Agent.java` | 1.0 | ✅ |
| 消息模型 | `core/AgentMessage.java` | 1.0 | ✅ |
| 执行结果 | `core/AgentResult.java` | 1.0 | ✅ |
| 天气 Worker | `worker/WeatherWorker.java` | 1.0 | ✅ |
| 计算 Worker | `worker/CalculatorWorker.java` | 1.0 | ✅ |
| 搜索 Worker | `worker/SearchWorker.java` | 1.0 | ✅ |
| 笔记 Worker | `worker/NoteWorker.java` | 1.0 | ✅ |
| 时间 Worker | `worker/TimeWorker.java` | 1.0 | ✅ |
| 编排器 | `orchestrator/OrchestratorAgent.java` | 1.0 | ✅ |
| REST API | `controller/AgentController.java` | 1.0 | ✅ |
| Python 对照 | `python/multi_agent_demo.py` | 1.0 | ✅ |

**运行时验证结果**：
- 天气查询: `Sunny +23°C` ✅
- 数学计算: `25*40+10 = 1010` ✅
- 日期时间: `2026年05月28日 星期四` ✅
- 笔记管理: `已保存笔记` ✅
- 搜索模拟: `已收到查询` ✅
- **多 Agent 协作**: 天气+计算同时返回 ✅

---

**下节预告**：Day 31 — 自研 DAG 执行引擎（把 Day 30 的编排器升级为有向无环图引擎）
