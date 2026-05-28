# Day 43：面试题 + 选型报告 📄

> 2026-05-28 · 职业冲刺第一天

---

## 今日成果

今天不走代码路线，专注做职业冲刺最核心的两件事：

### ✅ 产出一：AI 工程化面试题集

覆盖 7 大专题，共 40+ 道面试题：

| 专题 | 题量 | 核心题 |
|:-----|:----:|:-------|
| RAG | 5 题 | 完整流程、三大痛点、HNSW 索引、Chunk 策略 |
| Agent | 5 题 | ReAct 模式、记忆类型、Function Calling、多 Agent 架构 |
| LLMOps | 5 题 | 成本控制、Token 计价、缓存策略、限流对比 |
| Spring AI | 5 题 | 核心组件、流式调用、多轮对话、Tool 注解原理 |
| 架构设计 | 2 题 | 企业级 RAG、多 Agent 编排 |
| 算法基础 | 3 题 | 余弦相似度、Attention、Pre-training vs RAG |
| 实战场景 | 3 题 | 超时处理、PII 脱敏、日志方案 |

**特点：**
- 每道题都有 Java + Python 双代码示例
- 附答案要点和 STAR 法则面试技巧
- 包含"一句话说清楚"速查表

### ✅ 产出二：Spring AI vs LangChain4j 选型对比报告

基于 42 天实战的深度对比：

| 对比项 | 结论 |
|:-------|:-----|
| **综合评分** | Spring AI: 7.5 / LangChain4j: 7.6 |
| **适用场景** | Spring 技术栈 → Spring AI；独立/多框架 → LangChain4j |
| **最大优势** | Spring AI 开发体验好；LangChain4j 功能丰富 |
| **当前不足** | Spring AI 版本不稳；LangChain4j 概念多一些 |

包含：API 对比表、实战体验细节、走过的坑、技术生态、社区活跃度

### 📁 目录结构

```
阶段9-职业冲刺/
└── code/day43/
    ├── java-ai-interview/
    │   └── AI工程化面试题集.md          # 40+ 高频面试题
    └── spring-ai-vs-langchain4j/
        └── Spring-AI-vs-LangChain4j-选型对比报告.md  # 完整对比
```

---

## 明日预告 Day 44：简历包装

- 提炼 3 个有数据指标的项目亮点
- AI 工程化方向简历优化
