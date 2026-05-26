# 第14天：多Agent协作系统 🤝

## 14.1 为什么需要多Agent？

### 14.1.1 单Agent的局限性

经过前13天的学习，你已经能构建一个功能完整的单Agent系统。但单Agent有三个无法回避的瓶颈：

| 瓶颈 | 说明 | 表现 |
|------|------|------|
| **上下文窗口有限** | 一个Agent无法同时处理大量信息 | 长对话中"忘记"早期内容 |
| **单一视角** | 一个LLM只有一种"思维模式" | 缺乏多角度分析和自我纠错 |
| **工具链耦合** | 所有工具集中在同一个Agent里 | prompt 膨胀、调用冲突、难以维护 |

### 14.1.2 多Agent的核心理念

**多Agent系统 = 多个专业化Agent协同工作，解决单Agent无法胜任的复杂任务。**

想象一个软件公司：
- **产品经理** — 理解需求、规划任务（Orchestrator）
- **前端工程师** — 写页面（Worker A）
- **后端工程师** — 写API（Worker B）
- **测试工程师** — 审查质量（Worker C）

每个人只做自己擅长的事，通过明确的接口沟通——这就是多Agent的本质。

### 14.1.3 什么时候需要多Agent？

```
任务特征                    → 推荐方案
─────────────────────────────────────────
一句话能说清                → 单Agent ✅
需要多个步骤                → 单Agent + 工具链
需要多种专业知识            → 多Agent ✅
需要自我审查和质量控制       → 多Agent ✅
需要并行处理大量信息         → 多Agent ✅
```

---

## 14.2 四大协作模式

多Agent系统有四种经典的协作模式。理解这四种模式是设计任何多Agent系统的基础。

### 14.2.1 模式一：Orchestrator-Worker（编排器-工作者）

最常用、最稳定、最推荐的模式。

```
                 ┌──────────────┐
                 │ Orchestrator │  ← 负责任务规划、分配、汇总
                 └──────┬───────┘
            ┌───────────┼───────────┐
            ▼           ▼           ▼
      ┌─────────┐ ┌─────────┐ ┌─────────┐
      │ Worker 1│ │ Worker 2│ │ Worker 3│
      │ 研究员   │ │ 分析师   │ │ 审查员   │
      └─────────┘ └─────────┘ └─────────┘
```

**适用场景：** 研究分析、内容生成、代码审查、报告撰写

**优点：** 结构清晰、易于扩展、错误隔离
**缺点：** Orchestrator 是单点瓶颈

### 14.2.2 模式二：Pipeline（流水线）

每个Agent的输出是下一个Agent的输入。类似工厂流水线。

```
  用户输入
      │
      ▼
┌─────────────┐
│ Agent A     │  ← 理解需求
│  需求分析    │
└──────┬──────┘
       ▼
┌─────────────┐
│ Agent B     │  ← 生成方案
│  方案设计    │
└──────┬──────┘
       ▼
┌─────────────┐
│ Agent C     │  ← 生成代码
│  代码实现    │
└──────┬──────┘
       ▼
┌─────────────┐
│ Agent D     │  ← 审查结果
│  质量检查    │
└──────┬──────┘
       ▼
   最终输出
```

**适用场景：** 内容生产管线、多步转换、标准化流程

**优点：** 职责分明、易于调试
**缺点：** 延迟累加（串行）、前序错误会传播

### 14.2.3 模式三：Debate（辩论）

多个Agent对同一问题给出各自答案，然后互相讨论，最终达成共识。

```
┌────────────────────────────────────────────┐
│             用户问题                         │
└──────────┬──────────────────┬──────────────┘
           ▼                  ▼
    ┌────────────┐    ┌────────────┐
    │ Agent A    │    │ Agent B    │
    │ 观点1      │◄──►│ 观点2      │
    └──────┬─────┘    └──────┬─────┘
           │                 │
           ▼                 ▼
    ┌─────────────────────────────┐
    │ 辩论 N 轮后达成共识           │
    │ 或由仲裁Agent决定             │
    └─────────────────────────────┘
```

**适用场景：** 事实核查、复杂决策、代码审查、内容评估

**优点：** 多视角验证、减少幻觉
**缺点：** 成本高（多次LLM调用）、可能陷入循环

### 14.2.4 模式四：Hierarchical（层级）

多级Orchestrator，适合超大规模任务。

```
                    ┌──────────────────┐
                    │  Master Orchestrator │
                    └────────┬─────────┘
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
      ┌────────────┐ ┌────────────┐ ┌────────────┐
      │ Sub-Orch 1 │ │ Sub-Orch 2 │ │ Sub-Orch 3 │
      │  研究组     │ │  开发组     │ │  测试组     │
      └────┬───────┘ └────┬───────┘ └────┬───────┘
     ┌─────┼─────┐   ┌────┼────┐   ┌────┼────┐
     ▼     ▼     ▼   ▼    ▼    ▼   ▼    ▼    ▼
     W1   W2    W3  W4   W5   W6  W7   W8   W9
```

**适用场景：** 大型软件项目、企业级自动化、多团队协作

**优点：** 高度可扩展、分工明确
**缺点：** 复杂度高、延迟大

### 14.2.5 模式选择矩阵

| 因素 | Orchestrator | Pipeline | Debate | Hierarchical |
|------|:-----------:|:--------:|:------:|:-----------:|
| 任务复杂度 | 中高 | 中 | 中 | 极高 |
| 并行度 | 高 | 低 | 中 | 极高 |
| 错误隔离 | ✅ | ❌ | ✅ | ✅ |
| 实现难度 | 低 | 低 | 中 | 高 |
| 成本控制 | ✅ | ✅ | ❌ | ❌ |
| 推荐场景 | 通用首选 | 固定流程 | 质量敏感 | 超大规模 |

---

## 14.3 深度实现：Orchestrator-Worker 模式

今天我们会构建一个 **多Agent研究报告生成系统**，使用 Orchestrator-Worker 模式。

### 14.3.1 系统架构

```
用户： "帮我分析AI Agent框架的现状"
       │
       ▼
┌──────────────────────────────────────────────┐
│              Orchestrator Agent               │
│  ▸ 接收用户请求                               │
│  ▸ 分解为子任务                                │
│  ▸ 分配Worker执行                              │
│  ▸ 收集结果                                    │
│  ▸ 合成最终报告                                │
└──────┬──────────────┬──────────────┬──────────┘
       │              │              │
       ▼              ▼              ▼
┌──────────┐  ┌──────────┐  ┌──────────┐
│ Research  │  │ Analyze  │  │  Review   │
│  Worker   │  │  Worker  │  │  Worker   │
│           │  │          │  │           │
│ 搜索信息   │  │ 分析数据  │  │ 质量审查  │
│ 收集资料   │  │ 提炼观点  │  │ 格式校验  │
└──────────┘  └──────────┘  └──────────┘
       │              │              │
       └──────────────┼──────────────┘
                      ▼
         ┌──────────────────────┐
         │   Final Report       │
         │  (结构化 + 带来源)    │
         └──────────────────────┘
```

### 14.3.2 核心组件详解

#### 组件1: Agent通信协议

Agent之间不直接对话，而是通过**结构化消息**交换信息：

```python
@dataclass
class AgentMessage:
    sender: str          # 发送者名称
    receiver: str        # 接收者名称 (或 "orchestrator")
    msg_type: str        # task / result / error / status
    task_id: str         # 任务ID
    content: dict        # 消息体
    timestamp: float     # 时间戳
    metadata: dict       # 元数据（优先级、重试次数等）
```

**设计原则：**
- **异步通信** — Worker 不等待其他 Worker
- **有状态路由** — Orchestrator 维护所有消息的路由表
- **幂等性** — 同一条消息处理多次结果一致

#### 组件2: 任务分解器

Orchestrator 的核心——把用户问题拆成可并行执行的子任务：

```python
def decompose_task(user_request: str) -> List[SubTask]:
    """
    1. LLM 分析用户请求，识别需要哪些专业知识
    2. 生成子任务列表（每个子任务有明确的目标和输出格式）
    3. 分配给对应的 Worker
    
    示例输入: "分析AI Agent框架的现状"
    示例输出: [
        SubTask("research", "收集主流Agent框架信息"),
        SubTask("analyze", "对比各框架优劣势"),
        SubTask("review", "审查报告完整性和准确性"),
    ]
    """
```

**分解策略对比：**

| 策略 | 原理 | 优点 | 缺点 |
|------|------|------|------|
| **LLM分解** | LLM 动态分析任务 | 灵活、适应性强 | 不稳定、成本高 |
| **模板分解** | 预定义任务模板 | 快速、稳定 | 不够灵活 |
| **混合分解** | LLM 选择模板 + 动态调整 | 两者兼顾 | 实现复杂 |

#### 组件3: Worker 执行器

每个 Worker 是一个独立的功能单元，有自己的系统提示词和工具集：

```python
class ResearchWorker:
    """研究员 Worker：搜索、收集、整理信息"""
    
    SYSTEM_PROMPT = """你是一个专业的研究员。你的职责：
1. 根据给定的研究主题，收集相关信息
2. 整理成结构化的笔记
3. 标注每个信息的来源和可信度
4. 输出格式必须是JSON

注意事项：
- 如果信息不足，明确说明"未找到相关信息"
- 区分事实和推测
- 不要编造数据或来源"""
    
    def execute(self, task: SubTask) -> AgentMessage:
        # 1. 搜索相关信息
        search_results = self.search_engine.search(task.query)
        
        # 2. LLM 整理搜索结果
        notes = self.llm_synthesize(search_results, task)
        
        # 3. 返回结构化结果
        return AgentMessage(
            sender="research_worker",
            receiver="orchestrator",
            msg_type="result",
            task_id=task.id,
            content={"notes": notes, "sources": search_results}
        )
```

#### 组件4: 结果合成器

Orchestrator 收集所有 Worker 结果后，合成最终输出：

```python
def synthesize(self, results: Dict[str, AgentMessage]) -> str:
    """
    合成策略：
    1. 按优先级排序结果（research → analyze → review）
    2. 合并重复信息
    3. 处理矛盾信息（标记给用户）
    4. 生成结构化报告
    """
```

**合成中的冲突处理：**

| 冲突类型 | 处理策略 |
|----------|----------|
| 信息重复 | 去重，保留来源更可靠的版本 |
| 信息矛盾 | 双方观点都呈现，标注分歧 |
| 信息缺失 | 标记为"需要进一步调研" |
| 格式不一致 | 统一为标准格式 |

---

## 14.4 错误处理与恢复

多Agent系统的错误处理远比单Agent复杂，因为错误可能在任何环节发生，而且会传播。

### 14.4.1 错误类型

```
错误类型树状图：

┌─ Worker 级错误
│   ├── LLM 调用超时 → 重试 2 次，失败则跳过
│   ├── 工具调用失败 → 降级为纯LLM推理
│   └── 输出格式错误 → 要求重新生成
│
├─ Orchestrator 级错误
│   ├── 任务分解失败 → 回退为单Agent模式
│   ├── 结果合成失败 → 返回原始结果集合
│   └── Worker 全部失败 → 告知用户"无法完成"
│
└─ 系统级错误
    ├── API配额耗尽 → 排队等待
    ├── 网络中断 → 缓存已完成的Worker结果
    └── 上下文超长 → 启用摘要压缩
```

### 14.4.2 重试策略

```python
class RetryStrategy:
    """带退避的重试策略"""
    
    def __init__(self, max_retries=3, base_delay=1.0, backoff=2.0):
        self.max_retries = max_retries
        self.base_delay = base_delay
        self.backoff = backoff
    
    def execute(self, fn, *args, **kwargs):
        last_error = None
        for attempt in range(1, self.max_retries + 1):
            try:
                return fn(*args, **kwargs)
            except Exception as e:
                last_error = e
                if attempt < self.max_retries:
                    delay = self.base_delay * (self.backoff ** (attempt - 1))
                    logger.warning(f"第{attempt}次失败 ({e}), {delay}s后重试...")
                    time.sleep(delay)
        raise last_error
```

### 14.4.3 优雅降级

当某个 Worker 失败时，系统不应该整体崩溃：

```python
def execute_with_fallback(self, worker_name: str, task: SubTask) -> Optional[AgentMessage]:
    """执行任务，失败时降级"""
    try:
        return self.workers[worker_name].execute(task)
    except TimeoutError:
        logger.warning(f"{worker_name} 超时，使用简化版本")
        return self._simple_execute(task)
    except ToolError:
        logger.warning(f"{worker_name} 工具调用失败，使用纯LLM")
        return self._llm_only_execute(task)
    except Exception as e:
        logger.error(f"{worker_name} 不可恢复错误: {e}")
        return None  # 标记失败，Orchestrator会处理
```

---

## 14.5 完整实现

今天构建的**多Agent研究报告生成系统**包含：

| Agent | 角色 | 职责 |
|-------|------|------|
| 🎯 **Orchestrator** | 主编 | 接收需求、分解任务、合成报告 |
| 🔍 **Research Worker** | 研究员 | 搜索信息、收集资料 |
| 📊 **Analyze Worker** | 分析师 | 分析数据、提炼观点 |
| ✅ **Review Worker** | 审查员 | 质量检查、格式校验 |

系统支持：
- ✅ 三种任务分解策略（LLM/模板/混合）
- ✅ 并行 Worker 执行
- ✅ 错误重试与优雅降级
- ✅ 结果冲突处理
- ✅ 结构化报告输出
- ✅ 带退避的重试机制

---

## 14.6 生产环境考量

### 14.6.1 性能优化

```python
# 并行执行Worker（使用线程池）
from concurrent.futures import ThreadPoolExecutor, as_completed

def execute_workers_parallel(self, tasks: List[SubTask]) -> Dict[str, AgentMessage]:
    results = {}
    with ThreadPoolExecutor(max_workers=3) as executor:
        futures = {
            executor.submit(self.workers[t.worker_type].execute, t): t.id
            for t in tasks
        }
        for future in as_completed(futures, timeout=30):
            task_id = futures[future]
            try:
                results[task_id] = future.result()
            except Exception as e:
                results[task_id] = self._handle_error(task_id, e)
    return results
```

### 14.6.2 成本控制

| 策略 | 效果 |
|------|------|
| 设置 max_tokens 上限 | 避免单次调用过度消耗 |
| Worker 使用不同模型 | 简单任务用小模型，复杂任务用大模型 |
| 缓存重复查询 | 避免相同问题反复搜索 |
| 限制辩论轮数 | 防止 Debate 模式无限循环 |
| 设置总预算 | 超过预算自动降级 |

### 14.6.3 监控与日志

关键监控指标：
- 每个 Worker 的完成时间
- 重试次数统计
- 失败率趋势
- 成本累积
- Worker 间消息数量

---

## 14.7 思考题

1. **如果三个Worker给出互相矛盾的结果，Orchestrator应该如何判断谁是对的？**
2. **如何设计一个"人类介入"机制——当系统无法决策时，把问题转给人类？**
3. **Worker 之间的上下文如何共享？是否需要一个共享的"黑板"（Blackboard）？**
4. **在多Agent系统中，如何防止"级联幻觉"——一个Agent的错误被另一个Agent放大？**
5. **Orchestrator 本身是否也可能出错？如何监控 Orchestrator 的质量？**

---

## 14.8 扩展阅读

- **AutoGPT / BabyAGI** — 最早的多Agent实验项目
- **CrewAI** — Python 多Agent框架
- **Microsoft AutoGen** — 微软的多Agent对话框架
- **LangGraph** — LangChain 的图编排引擎
- **Google ADK** — Google 的 Agent 开发套件

---

## 14.9 金句

> **多Agent不是把一个问题分给多个LLM去回答，而是把一项复杂任务拆成多个专业角色去协作完成。**

> **好的多Agent系统像一支交响乐团——每个乐手只演奏自己的乐器，但指挥让所有声音和谐统一。**

> **如果单Agent能解决的问题，不要用多Agent。多Agent的复杂度是指数级增长的。**

> **错误处理不是多Agent系统的附加功能，而是核心设计。**

---

*第14天完成！你已经学会了多Agent协作系统的四大模式、深度实现了Orchestrator-Worker架构、掌握了错误处理和降级策略。明天继续更深入的应用！*
