# Spring AI vs LangChain4j 技术选型对比报告

> Day 43 · 职业冲刺 · 2026-05-28  
> 作者：zuoshangs（全程实战体验后撰写）

---

## 背景说明

本报告基于 **42 天实战项目**（Day 16-42）的亲身经历撰写。项目全部使用 Spring AI 1.0.0-M6 + Spring Boot 3.4.4 + Java 21，涵盖了 RAG、Agent、LLMOps、多模型适配等完整业务场景。

---

## 1. 基本信息对比

| 维度 | Spring AI | LangChain4j |
|:-----|:----------|:-------------|
| **出身** | VMware（Spring 官方） | 社区驱动（由 LangChain 衍生） |
| **当前版本** | 1.0.0-M6（2026.05） | 0.36.x（2026.05） |
| **发布时间** | 2024 年初 | 2023 年中 |
| **License** | Apache 2.0 | Apache 2.0 |
| **GitHub Stars** | 3.5k+ | 5k+ |
| **Maven 坐标** | `org.springframework.ai` | `dev.langchain4j` |
| **文档质量** | ⭐⭐⭐⭐ 官方文档齐全，但 API 变动中 | ⭐⭐⭐⭐ 社区教程丰富 |
| **Spring Boot 集成** | 原生（AutoConfiguration） | 通过 Starter 集成 |
| **定位** | Spring 生态的 AI 扩展 | 独立 AI 框架，可脱离 Spring |

---

## 2. 核心 API 对比

### 2.1 Chat / 对话

**Spring AI：**

```java
// 声明式 Builder
ChatResponse response = chatClient.prompt()
    .user("你好，你是谁？")
    .call()
    .content();
```

**LangChain4j：**

```java
// 接口驱动
String response = model.generate("你好，你是谁？");
```

| 差异点 | Spring AI | LangChain4j |
|:-------|:----------|:-------------|
| API 风格 | Builder 模式（流式链式调用） | 接口/类方法调用 |
| Stream 支持 | `Flux<String>`（Reactor） | `Observable` / `TokenStream` |
| AI Service | ❹ 目前没有（手动构建） | ✅ `AiServices` 声明式接口 |
| Tool 注册 | `@Tool` 注解 + `ToolCallback` | `@Tool` 注解（类似）

### 2.2 Embedding & Vector Store

**Spring AI：**

```java
@Bean
public VectorStore vectorStore(JdbcTemplate jdbc, EmbeddingModel model) {
    return new PgVectorStore(jdbc, model);
}

// 搜索
List<Document> docs = vectorStore.similaritySearch(
    SearchRequest.query("问题").withTopK(3)
);
```

**LangChain4j：**

```java
EmbeddingStore<TextSegment> store = PgVectorEmbeddingStore.builder()
    .host("localhost").port(5432)
    .table("documents").dimension(1536)
    .build();

List<EmbeddingMatch<TextSegment>> matches = store.findRelevant(embedding, 3);
```

| 差异点 | Spring AI | LangChain4j |
|:-------|:----------|:-------------|
| Vector Store 配置 | AutoConfiguration（Spring Boot 自动配） | 手动 Builder |
| 支持数据库 | PgVector, Redis, Pinecone, Chroma | PgVector, Redis, Pinecone, Elasticsearch |
| 抽象层 | `VectorStore` 接口 | `EmbeddingStore` 接口 |
| Filter 表达式 | 每种 Store 不同 | 统一表达式系统 |

### 2.3 RAG 支持

**Spring AI：**

```java
// 一行搞定 RAG（内置 Advisor）
String answer = chatClient.prompt()
    .advisors(new QuestionAnswerAdvisor(vectorStore))
    .user("Spring AI 怎么用？")
    .call()
    .content();
```

**LangChain4j：**

```java
// 使用 ContentRetriever
ContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
    .embeddingStore(store)
    .embeddingModel(model)
    .maxResults(3)
    .build();

String answer = AiServices.builder(Assistant.class)
    .contentRetriever(retriever)
    .build()
    .chat(userMessage);
```

| 差异点 | Spring AI | LangChain4j |
|:-------|:----------|:-------------|
| 开箱即用 | ✅ Advisor 机制非常简洁 | ✅ AiServices 声明式 |
| Query Rewrite | ❌ 需要手动实现 | ✅ 内置 |
| Document Reader | `PagePdfDocumentReader` 等 | `DocumentParser` 体系 |
| Splitter | `TokenTextSplitter` | `DocumentSplitter` 体系 |

---

## 3. 实战体验对比（基于 42 天项目）

### 3.1 学习曲线

```
上手难度
    高 ▲
       │            ● LangChain4j（概念多，但文档全）
       │
       │      ● Spring AI（Spring 老手：低；新手：中）
       │
       └──────────────────────────────►
               1天    1周    1月

Spring AI：如果你熟悉 Spring Boot，基本零学习成本
LangChain4j：概念更细（AiServices / Content / Document 体系需要先了解）
```

### 3.2 实际项目中的表现

| 场景 | Spring AI 感受 | LangChain4j 感受（推测/参考） |
|:-----|:---------------|:------------------------------|
| **多模型切换** | ✅ 换 ChatModel Bean 即可，几乎零改代码 | ✅ 同样方便 |
| **Tool 注册** | ✅ `@Tool` 注解 + `@Bean`，非常 Spring | ✅ 注解风格类似 |
| **Stream 输出** | ✅ `Flux<String>` 自然与 WebFlux 整合 | ✅ 支持 |
| **错误处理** | ⚠️ 异常信息不够友好，常需查源码 | ⚠️ 类似 |
| **版本稳定性** | ⚠️ 1.0.0-M6 还是里程碑版，API 有变动 | ✅ 较稳定 |
| **Spring Boot 集成** | ✅ 天然第一公民 | ✅ 通过 Starter 支持 |
| **复杂 Workflow** | ❌ 没有内置 Chain/Pipeline | ✅ `Chain` + `ExecutionPlan` |
| **Memory 管理** | Advisor 实现 | `ChatMemory` 接口 |

### 3.3 走过的坑（Spring AI）

| 坑 | 描述 | 解决方案 |
|:---|:-----|:---------|
| PostProcessors 不生效 | 配置后未自动注册 | 需要 `@Bean` 显式注入 |
| @Value 解析不了环境变量 | 早期版本不支持 | 用 `local.properties` 中转 |
| 某些 Model 的 Tool 调用返回格式问题 | DeepSeek 的 `tool_calls` 格式差异 | 添加 `strict` 参数控制 |
| 多模型 Provider 冲突 | 多个 `ChatModel` Bean 冲突 | `@Qualifier` 指定 |
| `MessageAggregator` 被移除 | 版本升级后 API 变更 | 改用 `advisor` 机制 |

---

## 4. 技术生态对比

### 4.1 支持的模型提供商

| 提供商 | Spring AI | LangChain4j |
|:-------|:----------|:-------------|
| OpenAI | ✅ | ✅ |
| Anthropic Claude | ✅ | ✅ |
| DeepSeek | ✅（OpenAI 兼容） | ✅（OpenAI 兼容） |
| Google Gemini | ✅ | ✅ |
| Ollama（本地） | ✅ | ✅ |
| DashScope（阿里） | ✅ | ✅ |
| 智谱 GLM | ✅ | ✅ |
| Azure OpenAI | ✅ | ✅ |
| Amazon Bedrock | ✅ | ✅ |
| HuggingFace | ✅ | ✅ |
| 自定义 | ✅ 通过自定义 `ChatModel` | ✅ 通过 `CustomModel` |

**结论：** 两者的提供商覆盖基本一样，差异不大。

### 4.2 文档 & 学习资源

| 资源 | Spring AI | LangChain4j |
|:-----|:----------|:-------------|
| 官方文档 | [docs.spring.io](https://docs.spring.io/spring-ai/) | [docs.langchain4j.dev](https://docs.langchain4j.dev/) |
| 示例项目 | Spring AI 官方 Samples | langchain4j-examples |
| Baeldung 教程 | ✅ 多 | ✅ 多 |
| Stack Overflow 提问数 | ~500+ | ~800+ |
| 中文资源 | 一般 | 一般 |

### 4.3 社区活跃度（截至 2026.05）

| 指标 | Spring AI | LangChain4j |
|:-----|:----------|:-------------|
| 贡献者 | ~150+ | ~200+ |
| 周更新 | 3-5 PR | 5-10 PR |
| Issue 响应 | 3-5 天 | 1-3 天 |
| 最新 Release | 2026-04 | 2026-04 |

---

## 5. 综合评估矩阵

| 评估维度 | 权重 | Spring AI (1-10) | LangChain4j (1-10) |
|:---------|:----:|:----------------:|:------------------:|
| **Spring Boot 集成度** | ★★★★★ | **10** | 7 |
| **开箱即用性** | ★★★★ | **8** | 7 |
| **API 优雅度** | ★★★★ | **9** | 7 |
| **功能丰富度** | ★★★★ | 7 | **9** |
| **版本稳定性** | ★★★ | 6 | **8** |
| **学习资源** | ★★★ | 7 | **8** |
| **社区活跃度** | ★★★ | 7 | **8** |
| **复杂工作流** | ★★★ | 5 | **8** |
| **生产成熟度** | ★★★★ | 6 | **7** |
| **文档质量** | ★★★ | 7 | **7** |
| **综合加权** | | **7.5** | **7.6** |

---

## 6. 选型建议

### 选 Spring AI 当你的团队：

- ✅ **已经是 Spring Boot / Spring Cloud 技术栈**
- ✅ 希望利用 Spring 的 AutoConfiguration / IoC / AOP 生态
- ✅ 团队 Java 能力> AI 能力（用 Spring 的方式学习 AI）
- ✅ 项目以 CRUD + AI 增强为主（咨询/问答/知识库）
- 团队平均使用 Spring Boot > 2 年：🔟 **强力推荐**

### 选 LangChain4j 当你的团队：

- ✅ 需要更丰富的 AI 功能（复杂 Workflow / Chain / Agent 编排）
- ✅ **不依赖 Spring**，纯 Java / Quarkus / Micronaut 技术栈
- ✅ 需要从 Python LangChain 迁移到 Java（概念对齐度高）
- ✅ 需要更成熟的 Agent 和 Tool 调用体系
- 团队 AI 能力 > Java 能力：🔟 **强力推荐**

### 我们的选择 & 评价

**本次项目选 Spring AI** — 作为 Spring Boot 深度用户，42 天体验下来：

| 优点 | 不足 |
|:-----|:-----|
| ✅ 零学习成本上手 Spring 开发者 | ❌ 版本仍在大改，API 可能变化 |
| ✅ Advisor 机制非常优雅（RAG / 记忆 / 日志) | ❌ 复杂 Workflow 支持偏弱 |
| ✅ 多模型切换只需改 Bean | ❌ 部分异常信息不够友好 |
| ✅ 流式输出天然集成 WebFlux | ❌ 内置 Memory 方案有限 |
| ✅ Spring Boot 全栈一致体验 | ❌ 开箱功能不如 LangChain4j 丰富 |

### 最终评分

```
                     Spring AI
                  ┌─────────────┐
    学习成本低    │  ████████░░  │ 深度功能不足
                  │  8/10        │
   Spring 集成好 │  ████████░░  │ 工作流偏弱
                  └─────────────┘

                  ┌─────────────┐
    LangChain4j  │  ████████░░  │
                  │  7.6/10      │
                  └─────────────┘
```

> **一句话总结：**
> Spring AI 是「Spring 的方式做 AI」，LangChain4j 是「Java 的方式做 AI」。
> 如果你的代码里已经有 `@Service`、`@Repository`、`@Autowired`，选 Spring AI 会更自然。

---

## 7. 未来趋势展望

| 趋势 | Spring AI | LangChain4j |
|:-----|:----------|:-------------|
| MCP 协议支持 | ⏳ 开发中 | ✅ 已支持 |
| Structured Output | ✅ `BeanOutputConverter` | ✅ `JsonSchemaGenerator` |
| Agent（多步骤推理） | ❌ 待完善 | ✅ 已有部分支持 |
| 可观测性集成 | ✅ Micrometer / Actuator | ✅ 内置 |
| Spring AI 2.0 路线图 | 计划 2026 年底 | — |

---

> 📌 **实践建议：**
> - **入门推荐 Spring AI**（尤其是 Spring Boot 老用户）
> - **进阶推荐同时了解 LangChain4j**（取长补短）
> - **生产项目**可考虑两者并用：Spring AI 做基础通信层，LangChain4j 做高级编排
