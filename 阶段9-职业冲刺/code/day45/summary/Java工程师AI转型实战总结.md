# 🎉 Java 工程师 AI 转型实战总结

> **45 天 · 从 Spring 到 AI 工程化**  
> 2026-04-14 → 2026-05-28

---

## 旅程概览

```
基础篇（15天） → Java AI落地（6天） → RAG工程化（5天） → Agent（6天） → LLMOps（5天） → 综合项目（5天） → 职业冲刺（3天）
    Python            Spring AI         PgVector         ReAct          限流/缓存      客服平台        面试/简历/前沿
```

### 总数据

| 指标 | 数值 |
|:-----|:----:|
| 📅 总天数 | 45 天 |
| 💻 Java 源文件 | 316 个 |
| 🐍 Python 对照文件 | 71 个 |
| 📄 教程笔记 | 43 篇 |
| 📊 总代码行数 | **85,815 行** |
| 🔧 Git Commit | 46 个 |
| 🧪 Java 测试方法 | 46 个（全部通过） |
| 🧪 Python 测试用例 | 24 个（全部通过） |
| 🌐 HTML 页面 | 35 个 |
| 📦 Maven 依赖 | Spring AI 1.0.0-M6 · Spring Boot 3.4.4 · Java 21 |
| 🐳 Docker | 多阶段构建 + docker-compose + 健康检查 |
| 🔄 CI/CD | GitHub Actions（测试→构建→发布 ghcr.io） |
| 📚 技术栈 | Spring AI · PgVector · Redis · DeepSeek · Ollama |

---

## 路线图回顾

### 第 1-3 阶段：AI 基础（Day 1-15 · Python 入门）

| 阶段 | 天数 | 核心内容 |
|:-----|:----:|:---------|
| **第一阶段：基础篇** | 1-7 | 提示词工程、Prompt 技巧、API 调用、参数调优 |
| **第二阶段：进阶能力** | 8-12 | RAG 概念、工具调用、结构化输出、多轮对话 |
| **第三阶段：应用实战** | 13-15 | 综合练习、项目实战 |

### 第 4-8 阶段：Java AI 工程化（Day 16-42 · Spring AI）

| 阶段 | 天数 | 核心内容 | 关键代码 |
|:-----|:----:|:---------|:---------|
| **④ Java AI 落地** | 16-21 | Spring AI 搭建、ChatClient、Stream、@Tool | `ChatClient` / `Flux<String>` / `@Tool` |
| **⑤ RAG 工程化** | 22-26 | PgVector、嵌入、HyDE、混合检索、Reranker | 5 种检索模式 |
| **⑥ Agent 工作流** | 27-32 | ReAct 循环、多工具编排、记忆、多 Agent 协作 | `QuestionAnswerAdvisor` |
| **⑦ LLMOps** | 33-37 | 限流、缓存、成本监控、生产加固 | 令牌桶 / 语义缓存 / Prometheus |
| **⑧ 综合项目** | 38-42 | 智能客服平台：RAG + Agent + 工单 + 仪表盘 + CI/CD | 46 测试通过 |

### 第 9 阶段：职业冲刺（Day 43-45）

| 天数 | 内容 | 产出 |
|:----:|:-----|:-----|
| **43** | 面试题 + 选型报告 | 40+ 道面试题集 + Spring AI vs LangChain4j 深度对比 |
| **44** | 简历包装 | 中英文简历 + 3 个数据亮点 |
| **45** | 前沿拓展 + 结业 | MCP 协议 + Ollama 本地部署 + 本总结 |

---

## 技术能力成长

### 从零到一的技能树

```
Day 1: "什么是 LLM？"           → 学习 Prompt 工程
Day 16: "Spring AI 怎么配？"   → 配置 ChatClient、@Tool
Day 22: "向量检索怎么玩？"     → PgVector + HNSW + Hybrid Search
Day 27: "Agent 是什么？"       → ReAct 循环 + 多工具编排
Day 33: "线上怎么降成本？"     → 限流 + 缓存 + 成本追踪
Day 38: "能做个项目吗？"      → AI 客服平台（RAG + Agent + LLMOps）
Day 43: "面试怎么准备？"      → 面试题集 + 简历 + MCP + Ollama
```

### 技术栈全景

```
┌────────────────────────────────────────────────┐
│                   AI 工程化                      │
├────────────────────────────────────────────────┤
│ 框架层                                          │
│   Spring AI 1.0.0-M6    LangChain (Python)     │
│   Spring Boot 3.4.4     Java 21                │
├────────────────────────────────────────────────┤
│ AI 能力层                                       │
│   Chat / Stream / ToolCall / Embedding          │
│   RAG (Hybrid Search + Rerank + HyDE)          │
│   Agent (ReAct + 多工具 + 记忆 + 多Agent)      │
│   LLMOps (限流 + 缓存 + 成本 + 监控)           │
├────────────────────────────────────────────────┤
│ 数据层                                          │
│   PgVector (HNSW索引)    Redis (缓存/限流)     │
│   PostgreSQL              Elasticsearch         │
├────────────────────────────────────────────────┤
│ 基础设施                                        │
│   Docker / docker-compose    GitHub Actions     │
│   Prometheus / Grafana       Actuator           │
│   Ollama (本地模型)          MCP 协议           │
├────────────────────────────────────────────────┤
│ AI 模型                                         │
│   DeepSeek Chat/Reasoner     Ollama qwen2.5     │
│   OpenAI GPT                 Ollama llama3.2    │
└────────────────────────────────────────────────┘
```

---

## 实战产出总结

### 🏆 智能客服平台（核心项目）

**项目定位：** 一个给"真有 AI 客服需求的企业"用的可部署平台

**包含 4 大模块：**

| 模块 | 功能 | 技术实现 |
|:-----|:-----|:---------|
| 💬 **AI 对话** | RAG 问答、多轮记忆、流式输出、多模型 | Spring AI ChatClient + Advisor + WebFlux |
| 📋 **工单系统** | 6 状态流转、智能分配、历史追踪 | 状态机模式 + REST API |
| 📊 **管理仪表盘** | Token 用量、速率、成本、缓存命中率实时展示 | Server-Sent Events + 10s 轮询 |
| ⚙️ **LLMOps 治理** | 限流（令牌桶） + 语义缓存 + 成本追踪 + 可观测性 | 自定义组件 + Prometheus |

**4 个关键技术挑战的解法：**

1. **检索准确率低** → Hybrid Search (Vector + BM25) + RRF 融合 + BGE Reranker
2. **API 成本高** → 语义缓存（>60% 命中率）+ 速率限制（5req/s）
3. **大模型幻觉** → RAG + 限定上下文 + 引用标注
4. **多用户并发** → 令牌桶 + 滑动窗口 + Redis 分布式计数

---

## 学到的 10 条最重要经验

### 💡 技术篇

1. **Spring AI 的 Advisor 机制是真香** — 一行代码搞定 RAG、记忆、日志，这是 LangChain4j 没有的
2. **版本选型要保守** — Spring AI 1.0.0-M6 还在里程碑版，API 变过若干次
3. **DeepSeek API 最省钱** — 45 天下来 Token 费约 3 块钱（主要是测试）
4. **本地模型不要追求"最强"** — qwen2.5:1.5b 做测试足够了
5. **双语言对照是最有效的学习方法** — Python 跑通概念 → Java 工程化落地

### 🎯 方法篇

6. **"宁顺延不跳过"** — 每天必须跑通，不建空中楼阁
7. **文档等于复盘** — 每天写教程笔记就是在固化知识
8. **测试先行能省大量调试时间** — JUnit 5 + Mockito 比手动 curl 高效 10 倍
9. **增量同步比大包大揽靠谱** — GitHub + NAS + rsync 三保险

### 🚀 进阶篇

10. **面试准备从第一天开始** — 每天的项目亮点、技术决策、踩坑记录，都是面试素材

---

## 下一步方向

### 深入方向

| 方向 | 建议资源 | 预估时间 |
|:-----|:---------|:--------:|
| **LangChain4j** | 官方文档 + 用它重写客服平台 | 2 周 |
| **MCP 协议开发** | 自己写一个 MCP Server 并贡献到社区 | 1 周 |
| **Spring AI 2.0** | 关注 Spring AI 正式版发布 | 等发版 |
| **Agentic RAG** | 研究 Self-RAG、Corrective RAG 等 Advanced RAG | 2 周 |
| **微调实战** | 用 Axolotl 微调小模型（LoRA） | 1 周 |

### 持续维护

- ⭐ 给 [ai-learning](https://github.com/zuoshangs/ai-learning) 加 Star
- 📝 把教程整理成技术博客发布
- 🔗 在简历和 LinkedIn 上链接项目
- 🔄 保持每周 1-2 次更新（修复 issue、补充内容）

### 推荐阅读

| 书/资源 | 理由 |
|:--------|:-----|
| 《李沐·动手学深度学习》 | 理论基础补强 |
| Andrej Karpathy 的 LLM 教程 | 最易懂的 LLM 原理讲解 |
| Spring AI 官方 Reference | 每天看 10 分钟，版本更新跟得上 |
| LangChain4j 官方文档 | 对比学习，取长补短 |

---

## 致谢

> **给自己的话：**
>
> 45 天前，你还不懂怎么在 Java 里调大模型。  
> 45 天后，你写了一个完整的 AI 客服平台——有 RAG、有 Agent、有限流缓存、有工单引擎、有 CI/CD。
>
> 这 85,815 行代码，每一行都是自己亲手写的。  
> 这 43 篇教程，每一篇都是自己踩坑后的真实记录。
>
> **你不是 AI 研究员，你是 Java 工程化工程师。**  
> 而 AI 工程化，正是市场最需要的那类人。 🚀

---

> 📍 项目地址：https://github.com/zuoshangs/ai-learning  
> 📅 完成时间：2026-05-28  
> 🎓 学习周期：45 天  
> 🏅 状态：✅ 结业
