# Day 31 — 自研 DAG 执行引擎

## 📌 今日目标

> 用 Java 实现一个有向无环图（DAG）执行引擎，能够编排 AI 工作流：定义节点、拓扑排序、分层并行执行、条件路由。

## 🧠 核心概念

### 什么是 DAG？

**DAG = Directed Acyclic Graph（有向无环图）**

- **有向**：边有方向，从依赖指向被依赖
- **无环**：不能形成循环依赖
- **图**：节点 + 边的集合

在 AI 工作流中，DAG 用来描述"谁先执行，谁后执行"：

```
     START
    /     \
Weather  Calculator
    \     /
  LLM Summary
       |
      END
```

### DAG vs 传统代码

| | 传统 if-else | DAG 工作流 |
|--|------------|-----------|
| **可读性** | 逻辑散落在多个文件 | 一张图看清全貌 |
| **可扩展** | 改代码、重新编译 | 改 JSON 配置即可 |
| **并行** | 手动写线程 | 同层自动并行 |
| **复用** | 函数调用 | 节点/子图复用 |
| **可视化** | 不直观 | 可直接渲染为流程图 |

### 五种节点类型

| 节点类型 | 作用 | 配置示例 |
|---------|------|---------|
| **START** | 工作流入口，无依赖 | 无 |
| **LLM** | 调用大模型 | `prompt` + `model` |
| **TOOL** | 调用外部工具 | `toolName` + `params` |
| **CONDITION** | 条件分支路由 | `condition` + `trueBranch`/`falseBranch` |
| **END** | 汇总输出 | `output` 模板 |

### 拓扑排序

把 DAG 变成线性的执行顺序，保证所有依赖都被满足。

**Kahn 算法**（我们用的）：
1. 计算所有节点的"入度"（有多少个前置依赖）
2. 入度为 0 的节点入队
3. 出队 → 执行 → 找下游，入度减 1
4. 重复直到队列为空

如果最终执行节点数 ≠ 总节点数 → **有环！**

---

## 💻 Java 实现

### 项目结构

```
dag-engine/
├── pom.xml
├── src/main/java/com/ai/learning/dag/
│   ├── DagApplication.java
│   ├── model/
│   │   ├── NodeType.java          # 节点类型枚举
│   │   └── DagNode.java           # 节点定义
│   ├── graph/
│   │   └── DagGraph.java          # DAG 图（拓扑排序 + 环检测）
│   ├── executor/
│   │   ├── DagContext.java         # 上下文
│   │   ├── DagExecutor.java        # 执行引擎
│   │   └── DagResult.java          # 执行结果
│   ├── service/
│   │   └── DagWorkflowService.java # 工作流加载
│   └── controller/
│       └── DagController.java      # REST API
└── workflows/
    ├── sample-workflow.json        # 天气+计算
    └── qa-workflow.json            # 问答（含条件）
```

### 第 1 步：节点定义

```java
public class DagNode {
    private String id;                    // 唯一标识
    private NodeType type;                // START/LLM/TOOL/CONDITION/END
    private List<String> dependencies;    // 前置依赖 ID 列表
    private Map<String, Object> config;   // 节点专用配置
    // 运行时状态
    private Object output;
    private boolean executed;
    private String error;
}
```

### 第 2 步：DAG 图

```java
public class DagGraph {
    private final Map<String, DagNode> nodes = new LinkedHashMap<>();

    // Kahn 拓扑排序
    public List<String> topologicalSort() {
        Map<String, Integer> inDegree = new HashMap<>();
        // 计算入度...
        Queue<String> queue = new LinkedList<>();
        // 入度为 0 的入队...
        List<String> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String id = queue.poll();
            sorted.add(id);
            // 下游节点入度 -1
            for (DagNode node : nodes.values()) {
                if (node.getDependencies().contains(id)) {
                    int deg = inDegree.merge(node.getId(), -1, Integer::sum);
                    if (deg == 0) queue.add(node.getId());
                }
            }
        }
        if (sorted.size() != nodes.size())
            throw new IllegalArgumentException("存在环!");
        return sorted;
    }

    // 分层执行计划
    public List<List<String>> getLeveledExecutionPlan() {
        // 按拓扑顺序计算每个节点的深度
        // 同深度的节点在同一层（可并行执行）
    }
}
```

### 第 3 步：执行引擎

```java
@Component
public class DagExecutor {
    public DagResult execute(DagGraph graph, String initialInput) {
        graph.validate();                                 // 1. 验证
        List<List<String>> levels = graph.getLeveledExecutionPlan();  // 2. 分层

        DagContext context = new DagContext();
        context.set("_input", initialInput);

        for (List<String> level : levels) {
            // 同层节点并发执行
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (String nodeId : level) {
                futures.add(CompletableFuture.runAsync(() -> {
                    executeNode(node, context);
                }));
            }
            CompletableFuture.allOf(futures.toArray()).join();
        }

        return new DagResult(success, finalOutput, nodeOutputs, ...);
    }

    // 根据节点类型分派
    private void executeNode(DagNode node, DagContext context) {
        Object result = switch (node.getType()) {
            case START     -> executeStart(node, context);
            case LLM       -> executeLlm(node, context);   // 调 DeepSeek
            case TOOL      -> executeTool(node, context);  // 天气/计算
            case CONDITION -> executeCondition(node, context);
            case END       -> executeEnd(node, context);
        };
        node.setOutput(result);
        node.setExecuted(true);
        context.set(node.getId(), result);
    }
}
```

### 第 4 步：工具节点（真实 API）

```java
// 天气查询 — 调用 wttr.in 免费 API
private String callWeatherApi(String city) throws IOException {
    String encoded = URLEncoder.encode(cityName, StandardCharsets.UTF_8);
    URL url = new URL("https://wttr.in/" + encoded + "?format=%25C+%25t+%25w+%25h");
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    // ... 读取响应
    return city + " 天气: " + raw;
}

// 数学计算 — Java 脚本引擎
private String evaluateMath(String expression) throws Exception {
    if (!expression.matches("[0-9+\\-*/().%\\s]+"))
        return "不支持的表达式";
    ScriptEngine engine = new ScriptEngineManager().getEngineByName("JavaScript");
    Object result = engine.eval(expression);
    return expression + " = " + result;
}
```

### 第 5 步：条件路由

```java
private Object executeCondition(DagNode node, DagContext context) {
    String condition = node.getConfigString("condition", "");
    // 支持: "varName contains 值"
    //       "varName equals 值"
    //       "varName notNull"
    boolean result = evaluateCondition(condition, context);
    String chosenBranch = result ? trueBranch : falseBranch;
    context.set("_condition_" + nodeId + "_branch", chosenBranch);
    return Map.of("condition", condition, "result", result, "branch", chosenBranch);
}
```

### 第 6 步：JSON 工作流定义

```json
{
  "nodes": [
    {"id": "start", "type": "START", "dependencies": []},
    {"id": "weather", "type": "TOOL", "dependencies": ["start"],
     "config": {"toolName": "weather", "params": "{_input}"}},
    {"id": "calculator", "type": "TOOL", "dependencies": ["start"],
     "config": {"toolName": "calculator", "params": "{_input}"}},
    {"id": "summary", "type": "LLM", "dependencies": ["weather", "calculator"],
     "config": {"prompt": "总结: 天气={weather}, 输入={_input}"}},
    {"id": "end", "type": "END", "dependencies": ["summary"],
     "config": {"output": "完成: {_summary}"}}
  ]
}
```

---

## 🐍 Python 对照版

核心代码结构完全一致，差异点：

```python
# Kahn 拓扑排序
def topological_sort(self):
    in_degree = {nid: 0 for nid in self._nodes}
    for node in self._nodes.values():
        for dep in node.dependencies:
            in_degree[node.id] += 1
    queue = deque([nid for nid, deg in in_degree.items() if deg == 0])
    sorted_nodes = []
    while queue:
        nid = queue.popleft()
        sorted_nodes.append(nid)
        for node in self._nodes.values():
            if nid in node.dependencies:
                in_degree[node.id] -= 1
                if in_degree[node.id] == 0:
                    queue.append(node.id)
    if len(sorted_nodes) != len(self._nodes):
        raise ValueError(f"存在环!")
    return sorted_nodes

# DFS 环检测
def detect_cycle(self):
    WHITE, GRAY, BLACK = 0, 1, 2
    color = {nid: WHITE for nid in self._nodes}
    # ... DFS 三色标记法
```

**Python vs Java 差异**：
- Python 用 `queue = deque()`，Java 用 `Queue = new LinkedList<>()`
- Python 字典/列表更简洁，Java 泛型更啰嗦
- Python 用 `@dataclass` 或普通类，Java 用 POJO + getter/setter
- Python 模拟模式更简单（没有 Spring AI 配置）

---

## 🔌 REST API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/dag/workflows` | GET | 列出可用工作流 |
| `/api/dag/run/{name}` | POST | 执行预定义工作流 |
| `/api/dag/custom` | POST | 执行自定义 JSON 工作流 |
| `/api/dag/validate` | POST | 验证 DAG 定义 |

### 测试请求

```bash
# 天气 + 计算
curl -X POST http://localhost:8080/api/dag/run/sample \
  -H "Content-Type: application/json" \
  -d '{"input":"北京天气"}'

# 问答（条件路由）
curl -X POST http://localhost:8080/api/dag/run/qa \
  -H "Content-Type: application/json" \
  -d '{"input":"中国的首都是哪里"}'
```

---

## 🧪 测试结果

### 演示 1：天气 + 计算（并行执行）

```
执行计划: [start] → [weather, calculator] → [summary] → [end]

weather  → 北京天气: Partly cloudy +23°C ←4km/h 78%   ✅
calculator → 不支持的表达式                            ✅
llm_summary → 北京当前天气：部分多云，23°C...           ✅  (真实DeepSeek)
耗时: 2.9s
```

### 演示 2：问答工作流（条件分支）

| 用户输入 | 路由结果 | 最终回答 |
|---------|---------|---------|
| 今天天气怎么样 | → search 分支 | 抱歉，天气需查天气预报 |
| 中国的首都是哪里 | → direct_answer 分支 | 中国的首都是北京 |

### 演示 3：环检测

```
输入: a → b → c → a（环）
输出: 检测到环! 环路径: a → c → b
```

### 演示 4：JSON 加载

```json
{ "nodes": [{"id":"start","type":"START"}, {"id":"greet","type":"LLM","dependencies":["start"]}, ...] }
→ 执行: "Hello World" → "用中文问候: Hello World"
```

---

## 📊 执行流程

```
用户输入 "北京天气"
      ↓
[验证 DAG] 检查环、依赖完整性
      ↓
[分层计划] start(0) | weather(1), calculator(1) | summary(2) | end(3)
      ↓
第1层: START  →  初始化上下文
      ↓
第2层: ┌──────┐  ┌───────────┐  ← 同层并行
       │weather│  │calculator  │
       └──────┘  └───────────┘
      ↓
第3层: LLM Summary  →  调用 DeepSeek 生成总结
      ↓
第4层: END  →  渲染输出模板，返回结果
```

---

## 🌟 扩展思路

1. **真正的条件短路**：CONDITION 节点执行后跳过非选中分支的下游节点
2. **子图嵌套**：一个节点可以引用另一个 DAG 作为子流程
3. **重试策略**：为每个节点配置 `maxRetries` + `retryDelay`
4. **超时控制**：为每个节点或整个 DAG 设置超时
5. **持久化**：将执行上下文写入数据库，支持断点恢复
6. **可视化**：基于 `graphviz` 或 `mermaid.js` 渲染工作流图
7. **循环节点**：支持 LOOP 节点，在 DAG 内做迭代（注意环检测需特殊处理）

---

## 📝 今日总结

| 知识点 | 掌握 |
|--------|:---:|
| DAG 图的数据结构 | ✅ Node + Edge |
| Kahn 拓扑排序 | ✅ 入度法 |
| DFS 环检测 | ✅ 三色标记法 |
| 分层执行计划 | ✅ 同层并行 |
| 条件路由 | ✅ contains/equals/notNull |
| JSON 配置工作流 | ✅ 无需改代码 |
| 真实 API 调用 | ✅ wttr.in + DeepSeek |

**核心思想**：DAG 执行的本质是"告诉机器做什么，而不是怎么做"。工作流定义 = JSON 配置，执行引擎 = 通用调度器，节点 = 可复用的积木块。

> 下一节预告：**Day 32 — 任务队列与异步处理**
> 学习用消息队列（Redis/内存）解耦工作流节点，实现异步非阻塞执行。
