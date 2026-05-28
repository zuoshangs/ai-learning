# 个人简历 — AI 工程化方向

> **目标岗位：** Java AI 工程师 / AI 应用开发 / LLM 工程化工程师  
> **更新日期：** 2026-05-28

---

## 基本信息

| 项目 | 内容 |
|:-----|:------|
| **姓名** | zuoshangs |
| **技术栈** | Java 21 / Spring Boot / Spring AI / Python / DeepSeek / PgVector |
| **方向** | AI 工程化 · RAG · Agent · LLMOps |
| **GitHub** | [github.com/zuoshangs/ai-learning](https://github.com/zuoshangs/ai-learning) |

---

## 技能树

### 核心能力

```
Java AI 工程化  ████████░░  80%  Spring AI + Spring Boot 全栈
RAG 系统设计    ████████░░  80%  向量检索 + Hybrid Search + Reranker
Agent 编排      ███████░░░  70%  ReAct + Tool Calling + 多Agent协作
LLMOps          ███████░░░  70%  限流/缓存/成本追踪/可观测性
Python AI       ████████░░  80%  LangChain + 双语言对照实现
```

### 技术栈详情

| 分类 | 技术 |
|:-----|:-----|
| **语言** | Java 21（主）、Python 3.x（副） |
| **框架** | Spring Boot 3.4.4、Spring AI 1.0.0-M6、LangChain |
| **AI 模型** | DeepSeek Chat/Reasoner、OpenAI GPT、Ollama qwen2.5 |
| **向量数据库** | PgVector（HNSW 索引） |
| **数据库** | PostgreSQL、Redis |
| **基础设施** | Docker、Docker Compose、GitHub Actions CI/CD |
| **监控** | Prometheus、Grafana、Actuator |
| **工具** | Maven、Git、JUnit 5、Mockito |

---

## 项目经验

### 🏆 AI 智能客服平台（核心项目）

**时间：** 2026.04 - 2026.05 · 42 天工程化实战  
**技术栈：** Spring AI 1.0.0-M6 / Spring Boot 3.4.4 / Java 21 / PgVector / Redis / Docker  
**代码量：** 316 个 Java 源文件 + 71 个 Python 对照文件 | 85,815 行代码 | 46 个单元/集成测试全部通过  
**源码：** [github.com/zuoshangs/ai-learning](https://github.com/zuoshangs/ai-learning)

#### 三个数据亮点

| # | 亮点 | 数据指标 | 技术含量 |
|:-:|:-----|:---------|:---------|
| ① | **RAG 知识库全链路** | 5 种检索模式（精确/向量/混合/HyDE/重排），检索延迟 < 200ms | Hybrid Search + RRF 融合排名 + BGE Reranker 二次排序 |
| ② | **LLMOps 治理体系** | 令牌桶限流 5req/s，语义缓存命中率 > 60%，成本精确追踪到每次调用 | 缓存归一化 + 实时仪表盘 + Prometheus 监控 |
| ③ | **工单状态机引擎** | 6 种状态自动流转（新建→分配→处理→待确认→关闭→重开），支持智能分配 | 状态机 + 权限校验 + 历史追踪 |

#### 项目架构

```
┌─────────┐   ┌─────────┐   ┌──────────┐   ┌──────────┐
│  前端    │   │ AI 对话  │   │ 工单系统  │   │ 仪表盘   │
│ HTML/JS  │──▶│ ChatCtrl│──▶│ Ticket   │──▶│ Dashboard│
└─────────┘   └────┬────┘   └────┬─────┘   └──────────┘
                   │              │
                   ▼              ▼
            ┌────────────┐ ┌────────────┐
            │ RAG 知识库  │ │ LLMOps 治理 │
            │ PgVector    │ │ 限流/缓存   │
            │ HybridSearch│ │ 成本追踪    │
            └────────────┘ └────────────┘
```

#### 技术亮点

| 场景 | 实现方式 | 技术深度 |
|:-----|:---------|:---------|
| **多模态对话** | 流式 SSE + 多轮记忆 + Prompt 模板 | Spring AI ChatClient + WebFlux |
| **RAG 增强** | Query Rewrite → 混合检索 → Rerank → 上下文注入 | 四阶段流水线 |
| **Agent 工具** | @Tool 天气/计算/搜索/知识库，自动编排 | ReAct 循环 |
| **限流保护** | 令牌桶算法（滑动窗口 + 并发控制） | 自定义 RateLimiter |
| **语义缓存** | SHA-256 归一化 + 嵌入相似度双缓存 | ResponseCache 组件 |
| **成本追踪** | 每次调用的 Prompt/Completion Token 精确统计 | CostTracker + 实时汇率 |
| **CI/CD** | GitHub Actions 自动测试→构建→发布 ghcr.io | Docker 多阶段构建 |

---

## 技术广度

### 从 0 到 1 构建 AI 工程化知识体系（42 天系统学习）

```
阶段1-3：AI 基础  ─── Python 入门 + 提示词工程 + API 调用
  │
  ▼
阶段4：Java AI 落地 ─── Spring AI 环境搭建 / ChatClient / Stream / Tool
  │
  ▼
阶段5：RAG 工程化  ─── 文档加载 / 向量化 / HyDE / 混合检索 / 重排（5种检索模式）
  │
  ▼
阶段6：Agent 工作流 ─── ReAct / 多工具编排 / 记忆 / 多Agent协作
  │
  ▼
阶段7：LLMOps 治理  ─── 限流 / 缓存 / 成本 / 监控 / 生产加固
  │
  ▼
阶段8：综合项目  ─── 智能客服平台（RAG + Agent + LLMOps + 工单 + 仪表盘 + CI/CD）
```

**产出物：** 43 篇教程笔记 | 双语言（Java + Python）对照代码 | 完整的 GitHub 仓库

### 面试准备

- 完成 40+ 道高频面试题集（RAG / Agent / LLMOps / Spring AI / 架构设计）
- 撰写 Spring AI vs LangChain4j 深度对比报告

---

## 教育与证书

| 项目 | 说明 |
|:-----|:------|
| **AI 工程化实战课程** | 45 天系统化学习，覆盖 AI 全栈工程化技术栈 |
| **语言能力** | 中文（母语）、英文（技术文档读写） |

---

## 为什么找我

> **我是 Java 工程师，不是 AI 研究员。**  
> 我不做模型训练，不做论文复现。  
> 我做的事情是：**用 AI 技术解决真实业务问题** — RAG 改善客服响应、Agent 自动化工作流、LLMOps 控制成本。  
> 如果你需要有人把大模型落地到 Spring Boot 项目里，我就是你要找的人。

---

> 💡 **简历优化建议：**
> - 根据目标公司调整项目亮点顺序（银行/保险 → 强调工单状态机和安全；SaaS → 强调 LLMOps 成本控制）
> - 面试时准备 STAR 故事：每个亮点准备一个「Situation → Task → Action → Result」的完整叙述
> - 开源项目 AI-learning 本身就是最好的作品集
