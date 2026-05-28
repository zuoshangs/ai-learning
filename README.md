# AI 学习课程 🎓

> **Java 工程师 → AI 工程化转型** | 已学 31/45 天

完整的学习计划请见：[Java-AI工程化转型_综合学习计划.md](./Java-AI工程化转型_综合学习计划.md)

---

## 📚 已完成课程

### 第一阶段：AI 基础（Python）— Day 1–7 ✅

| Day | 主题 | 核心内容 |
|:---:|------|---------|
| 1 | 大模型机制入门 | Token、上下文窗口、模型选择 |
| 2 | 提示词工程基础 | Zero-shot / Few-shot / CoT |
| 3 | 高级提示词策略 | ToT、Self-Consistency |
| 4 | API 基础对接 | OpenAI 兼容接口、认证 |
| 5 | 核心参数调优 | Temperature / Top-P / Max Tokens |
| 6 | 角色设定与模板 | System Prompt、模板工程 |
| 7 | 阶段复盘 | 全景回顾、知识图谱 |

### 第二阶段：进阶能力（Python）— Day 8–15 ✅

| Day | 主题 | 核心内容 |
|:---:|------|---------|
| 8 | RAG 入门与实战 | 文档加载→分块→向量检索→生成 |
| 9 | 工具调用 | Function Calling、插件系统 |
| 10 | 结构化输出 | JSON Mode、Pydantic |
| 11 | 多轮对话与状态管理 | 对话记忆、Session |
| 12 | 项目：AI 客服系统 | 意图识别 + 多轮对话 + 工具调用 |
| 13 | AI 搜索增强助手 | 搜索 + RAG + 多工具编排 |
| 14 | 多 Agent 协作系统 | Orchestrator-Worker 模式 |
| 15 | 个人知识库问答系统 | Embedding + 向量检索 + LLM 生成 |

### 第三阶段：Java AI 基础落地 — Day 16–21 ✅

| Day | 主题 | 技术栈 |
|:---:|------|--------|
| 16 | Spring AI 环境搭建 | Spring Boot 3.4 + Spring AI 1.0 |
| 17 | 提示词模板与结构化输出 | PromptTemplate + BeanOutputConverter |
| 18 | 多轮对话与 SSE 流式 | ChatMemory + Flux SSE |
| 19 | 工具调用 / Function Calling | @Tool 注解 + 多工具编排 |
| 20 | Spring AI RAG 实战 | PgVector + Embedding + RAG Advisor |
| 21 | 智能客服系统（Java 版） | 意图识别 + 流式输出 + 单元测试 |

### 第四阶段：RAG 工程化 — Day 22–26 ✅

| Day | 主题 | 技术栈 |
|:---:|------|--------|
| 22 | 文档加载与智能切分 | Tika/PDFBox + Token/语义切分 |
| 23 | 向量化入库与相似度检索 | 批量入库 + Top-K + PgVector |
| 24 | 混合检索与重排 | FTS + RRF 融合 + LLM Reranker |
| 25 | 高级 RAG 技术 | 查询重写 + HyDE + 父文档检索 |
| 26 | 企业知识库 V2 | 20+ Java 文件的企业级实现 |

### 第五阶段：Agent 与工作流 — Day 27–31 ✅

| Day | 主题 | 技术栈 |
|:---:|------|--------|
| 27 | Agent 原理与 ReAct | 思考→行动→观察循环 |
| 28 | 多工具编排与 Agent 记忆 | PostgreSQL 持久化 + 长短记忆 |
| 29 | Dify 工作流平台 | Docker Compose 部署 + Java 集成 |
| 30 | 多 Agent 协作系统 | 消息总线 + Orchestrator-Worker |
| 31 | 自研 DAG 执行引擎 | 拓扑排序 + 分层并行 + 条件路由 |

---

## 📁 目录结构

```
ai-learning/
├── 01-基础篇/               (Day 1-7 Python)
├── 02-进阶能力/             (Day 8-12 Python)
├── 03-应用实战/             (Day 13-15 Python)
├── 阶段4-Java-AI应用开发/   (Day 16-21 Java)
│   ├── day16-*.md ~ day21-*.md
│   └── code/day16/ ~ code/day21/
├── 阶段5-RAG工程化/         (Day 22-26 Java)
│   ├── day22-*.md ~ day26-*.md
│   └── code/day22/ ~ code/day26/
├── 阶段6-Agent与工作流/     (Day 27-31+ Java)
│   ├── day27-*.md ~ day31-*.md
│   └── code/day27/ ~ code/day31/
├── 阶段7-LLMOps/            (Day 33-37 预留)
├── 阶段8-综合项目/          (Day 38-42 预留)
├── 阶段9-职业冲刺/          (Day 43-45 预留)
├── Java-AI工程化转型_综合学习计划.md  ← 详细计划
└── sync_to_nas.sh / sync_to_github.sh
```

---

## 🛠️ 技术栈

| 类别 | 技术 |
|------|------|
| **AI 框架** | Spring AI 1.0.0-M6, LangChain4j |
| **运行时** | Java 21, Spring Boot 3.4.4 |
| **构建** | Maven 3.8+ |
| **数据库** | PostgreSQL + PgVector, Elasticsearch |
| **工作流** | Dify (Docker), 自研 DAG 引擎 |
| **部署** | Docker, Docker Compose |
| **可观测** | Prometheus / Micrometer (计划中) |
| **AI 模型** | DeepSeek V4 Flash/Pro, OpenAI 兼容 |
| **嵌入** | sentence-transformers, Ollama |

---

## 🚀 快速开始

```bash
# 查看完整学习计划
cat Java-AI工程化转型_综合学习计划.md

# 运行最新 Java 项目 (Day 31 DAG 引擎)
cd 阶段6-Agent与工作流/code/day31/dag-engine
bash start.sh

# 运行 Python 对照 Demo
cd 阶段6-Agent与工作流/code/day31/python
pip install -r requirements.txt
python3 dag_demo.py
```

---

## 📄 许可证

MIT
