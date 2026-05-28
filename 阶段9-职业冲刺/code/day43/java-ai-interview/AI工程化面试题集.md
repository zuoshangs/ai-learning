# 🎯 Java AI 工程化面试题集

> Day 43 · 职业冲刺 · 2026-05-28  
> 覆盖：RAG / Agent / LLMOps / Spring AI / 综合题

---

## 目录

1. [RAG 专题](#1-rag-专题)
2. [Agent 专题](#2-agent-专题)
3. [LLMOps 专题](#3-llmops-专题)
4. [Spring AI 专题](#4-spring-ai-专题)
5. [架构设计题](#5-架构设计题)
6. [算法 & 数学基础](#6-算法--数学基础)
7. [实战场景题](#7-实战场景题)

---

## 1. RAG 专题

### 1.1 简述 RAG 的完整流程（必考）

**答案：**

```
用户 Query
    │
    ▼
[1] 嵌入 → 向量化用户问题
    │
    ▼
[2] 向量检索 → 在向量数据库中找 Top-K 相似文档
    │
    ▼
[3] 上下文构建 → 将检索到的文档与原始 Query 拼接成 Prompt
    │
    ▼
[4] LLM 生成 → 调用大模型基于上下文回答
    │
    ▼
[5] 后处理 → 引用标注、格式校验、安全过滤
```

**Java（Spring AI）实现：**

```java
@Bean
public VectorStore vectorStore() {
    return new PgVectorStore(jdbcTemplate, embeddingModel);
}

public String ask(String query) {
    var docs = vectorStore.similaritySearch(
        SearchRequest.query(query).withTopK(3)
    );
    var prompt = """
        基于以下上下文回答问题：
        
        上下文：
        {context}
        
        问题：{question}
        """.replace("{context}", docs.stream()
            .map(Document::getText).collect(joining("\n---\n")))
          .replace("{question}", query);
    return chatClient.call(prompt);
}
```

**Python（LangChain）实现：**

```python
def ask(question: str) -> str:
    docs = vector_store.similarity_search(question, k=3)
    context = "\n---\n".join(d.page_content for d in docs)
    prompt = f"""基于以下上下文回答问题：
    
上下文：
{context}

问题：{question}"""
    return llm.invoke(prompt).content
```

### 1.2 RAG 的三大痛点及解决方案

| 痛点 | 原因 | 解决方案 |
|:-----|:-----|:---------|
| **检索质量差** | 语义相似度 ≠ 任务相关度 | Hybrid Search（向量 + BM25）；Query Rewrite；HyDE |
| **上下文窗口浪费** | 检索到不相关的噪声文档 | Reranker 二次排序；Chunk 策略优化 |
| **LLM 不忠实于上下文** | 模型忽略检索结果 | 结构化 Prompt 强调"仅基于上下文回答"；Few-shot 示例 |

### 1.3 什么是 HNSW 索引？为什么适合向量检索？

- **HNSW**（Hierarchical Navigable Small World）：分层可导航小世界图
- **原理**：构建多层图结构，上层为"高速公路"快速定位，下层为精确邻居搜索
- **复杂度**：构建 O(N log N)，搜索 O(log N)
- **优势**：纯内存索引，召回率高，支持增量插入（无重训）

**PgVector 中启用 HNSW：**

```sql
CREATE INDEX ON documents USING hnsw (embedding vector_cosine_ops);
```

### 1.4 对比 Naive RAG → Advanced RAG → Modular RAG

| 阶段 | 特点 | 代表技术 |
|:-----|:-----|:---------|
| **Naive RAG** | 简单检索 + 生成 | Index → Retrieve → Generate |
| **Advanced RAG** | 检索前/后优化 | Query Rewrite、Sliding Window、Rerank |
| **Modular RAG** | 可插拔模块化流水线 | Search → Rerank → Filter → Generate + 路由 |

### 1.5 Chunk 策略最佳实践

| 策略 | 适用场景 | 大小 |
|:-----|:---------|:-----|
| 固定大小 + 重叠 | 通用 | 512-1024 tokens |
| 语义分块 | 长文档、文章 | 按段落/标题分割 |
| 递归分块 | Markdown/HTML 文档 | 按结构层级递归切分 |

**经验法则：**
- 中文文档：256-512 tokens
- 英文文档：512-1024 tokens
- 重叠量：10-15%

---

## 2. Agent 专题

### 2.1 什么是 AI Agent？与 LLM 的区别？

| | LLM | Agent |
|:--|:----|:------|
| **状态** | 无状态，单轮对话 | 有状态，多轮自主决策 |
| **能力** | 文本生成 | 感知 → 规划 → 工具调用 → 执行 |
| **记忆** | 上下文窗口 | 长期记忆（向量库）+ 短期记忆（对话） |
| **工具** | 不内置 | 可调用 API、数据库、代码执行器等 |
| **循环** | 一次推理 | ReAct 循环（Reasoning + Acting） |

### 2.2 解释 ReAct 模式（必考）

ReAct = **Rea**soning + **Act**ing，核心循环：

```
1. Thought：思考当前需要做什么
2. Action：调用一个工具
3. Observation：观察工具返回的结果
4. → 回到 1，直到得到最终答案
```

**Java（Spring AI）实现：**

```java
@Tool
public String searchKnowledgeBase(String query) {
    return vectorStore.similaritySearch(query, 3).stream()
        .map(Document::getText).collect(Collectors.joining("\n"));
}

String answer = chatClient.prompt()
    .user("帮我查一下...")
    .tools(searchKnowledgeBaseTool)
    .call()
    .content();
```

**Python 实现：**

```python
@tool
def search_knowledge_base(query: str) -> str:
    docs = vector_store.similarity_search(query, k=3)
    return "\n".join(d.page_content for d in docs)

response = agent.invoke({"input": "帮我查一下..."})
```

### 2.3 Agent 的三种记忆类型

| 记忆类型 | 存储方式 | 作用 |
|:---------|:---------|:-----|
| **短期记忆** | 对话历史（上下文窗口） | 维持当前会话连贯性 |
| **长期记忆** | 向量数据库 | 跨会话知识、用户偏好 |
| **工作记忆** | Agent 内部状态 | 任务进度、中间推理 |

### 2.4 Function Calling vs Tool Calling

| | Function Calling | Tool Calling |
|:---------------|:-----------------|:-------------|
| **来源** | 模型原生能力 | 框架层抽象 |
| **定义方式** | JSON Schema 描述 | Java @Tool 注解 / Python @tool 装饰器 |
| **执行者** | 模型返回参数，开发者执行 | 框架自动编排执行 |
| **灵活性** | 手写 Schema，精确可控 | 注解声明，开箱即用 |
| **用途** | 底层自定义函数 | 业务层工具链编排 |

### 2.5 多 Agent 架构的选择

| 架构 | 适用场景 | 复杂度 |
|:-----|:---------|:------|
| **Orchestrator + Workers** | 主控分配子任务 | ⭐⭐ |
| **Round-Robin** | 多角色轮流讨论 | ⭐ |
| **Debate** | 批判性任务 | ⭐⭐⭐ |
| **Pipeline** | 流水线处理 | ⭐ |
| **Graph** | 复杂工作流 | ⭐⭐⭐⭐ |

---

## 3. LLMOps 专题

### 3.1 LLMOps 主要关注哪些方面？（必考）

```
LLMOps 成熟度模型
├── ① 成本控制
│   ├── Token 统计 & 计费
│   ├── 缓存（语义缓存 / 精确缓存）
│   └── 模型路由（小模型 + 大模型分级）
├── ② 质量保障
│   ├── Prompt 版本管理
│   ├── 自动评估（BLEU / ROUGE / LLM-as-Judge）
│   └── A/B 测试
├── ③ 可观测性
│   ├── Trace（调用链路追踪）
│   ├── Metrics（延迟 / 吞吐 / 错误率）
│   └── Logging（完整 Prompt-Response 日志）
├── ④ 安全
│   ├── Prompt Injection 防护
│   ├── PII 脱敏
│   └── 内容审核
└── ⑤ CI/CD
    ├── Prompt-as-Code
    ├── 自动化评估流水线
    └── 模型灰度发布
```

### 3.2 Token 计数：Prompt 和 Completion 的计价方式

```json
{
  "input_tokens": 150,    // Prompt 部分
  "output_tokens": 80,    // Completion 部分
  "total_tokens": 230,
  "cost": 0.0000023       // 以 DeepSeek 为例
}
```

**Java（Spring AI）获取 Token 用量：**

```java
ChatResponse response = chatClient.prompt()
    .user("Hello")
    .call()
    .chatResponse();
TokenUsage usage = response.getMetadata().getUsage();
```

**Python 实现：**

```python
response = client.chat.completions.create(
    model="deepseek-chat",
    messages=[{"role": "user", "content": "Hello"}]
)
usage = response.usage
```

### 3.3 对比 Prompt Caching 和 Semantic Caching

| | Prompt Caching | Semantic Caching |
|:--------------|:---------------|:-----------------|
| **匹配方式** | 精确匹配（hash） | 语义相似度阈值 |
| **命中条件** | 完全相同的 Prompt | 含义相似的 Query |
| **存储** | KV Store（Redis） | 向量数据库 |
| **延迟节省** | 首 token 延迟 (TTFT) | 完整 LLM 调用 |
| **适用场景** | 系统 Prompt 重复 | 高频相似问答 |

### 3.4 Rate Limiting 策略对比

| 策略 | 原理 | 优点 | 缺点 |
|:-----|:-----|:-----|:-----|
| **令牌桶** | 恒定速率放 token | 可应对突发流量 | 参数调优复杂 |
| **滑动窗口** | 时间窗口内计数 | 精确控制 | 内存占用高 |
| **漏桶** | 恒定速率出水 | 流量平滑 | 丢弃突发请求 |
| **并发限制** | 最大并行数 | 保护后端 | 不控制速率 |

### 3.5 LLM 评估指标

| 指标 | 衡量内容 | 计算方式 |
|:-----|:---------|:---------|
| **BLEU** | n-gram 精确度 | 基于参考文本的重叠度 |
| **ROUGE** | 召回率 | 覆盖了多少参考内容 |
| **BERTScore** | 语义相似度 | BERT 嵌入余弦相似度 |
| **LLM-as-Judge** | 综合质量 | 用 GPT-4 打分 |

---

## 4. Spring AI 专题

### 4.1 Spring AI 的核心组件有哪些？

| 组件 | 用途 | 核心接口/类 |
|:-----|:-----|:------------|
| **ChatClient** | LLM 对话 | `ChatClient`（流式/非流式） |
| **EmbeddingModel** | 文本嵌入 | `EmbeddingModel` |
| **VectorStore** | 向量存储 | `VectorStore`（PgVector、Redis、Pinecone） |
| **DocumentReader** | 文档解析 | `PagePdfDocumentReader`、`JsonReader` |
| **DocumentTransformer** | 文档处理 | `TextSplitter`、`TokenTextSplitter` |
| **ToolCallback** | 函数调用 | `ToolCallback`、`@Tool` 注解 |
| **Advisor** | 横切关注点 | `QuestionAnswerAdvisor`、`AbstractChatMemoryAdvisor` |

### 4.2 Spring AI 的流式调用如何实现？

**Java：**

```java
@GetMapping("/chat/stream")
public Flux<String> stream(@RequestParam String message) {
    return chatClient.prompt()
        .user(message)
        .stream()
        .content();  // Flux<String>
}
```

**Python：**

```python
@router.get("/chat/stream")
async def stream(message: str):
    response = client.chat.completions.create(
        model="deepseek-chat",
        messages=[{"role": "user", "content": message}],
        stream=True
    )
    for chunk in response:
        yield chunk.choices[0].delta.content or ""
```

### 4.3 Spring AI 的多轮对话如何实现？

```java
@GetMapping("/chat")
public String chat(@RequestParam String message, HttpSession session) {
    var history = session.getAttribute("history");
    var messages = history != null ? (List<Message>) history : new ArrayList<>();
    messages.add(new UserMessage(message));
    
    var response = chatClient.prompt()
        .messages(messages)
        .call()
        .content();
    
    messages.add(new AssistantMessage(response));
    session.setAttribute("history", messages);
    return response;
}
```

### 4.4 Spring AI Tool 注解的底层原理

```java
@Tool("根据用户输入搜索知识库")
public String searchKB(String query) {
    // 方法体
}
```

**原理：**
1. Spring 启动时扫描 `@Tool` 注解
2. 生成对应的 JSON Schema（name + description + parameters）
3. 调用 LLM 时，将 Schema 注入到 `tools` 参数
4. LLM 判断需要调用工具时，返回 `tool_calls`
5. Spring AI 自动反射调用方法，获取结果
6. 将结果作为新的消息注入下一轮对话

### 4.5 Spring AI 的 Advisor 机制

Advisor 类似 Spring AOP 的环绕通知，在执行前后织入逻辑：

```java
// 内置 Advisor
chatClient.prompt()
    .advisors(new QuestionAnswerAdvisor(vectorStore))  // RAG
    .advisors(new SimpleChatMemoryAdvisor("session1"))  // 记忆
    .call();
```

**自定义 Advisor：**

```java
@Component
public class LoggingAdvisor implements CallAroundAdvisor {
    @Override
    public AroundAdvisorChain chain() {
        return (request, chain) -> {
            log.info("Request: {}", request.contents());
            var response = chain.next(request);
            log.info("Response: {}", response.getResult().getOutput());
            return response;
        };
    }
    
    @Override
    public String getName() { return "LoggingAdvisor"; }
    
    @Override
    public int getOrder() { return 0; }
}
```

---

## 5. 架构设计题

### 5.1 设计一个企业级 RAG 系统

**要求：** 日均 10 万 QPS，支持多租户，200ms 内响应

**架构方案：**

```
Client → Load Balancer → API Gateway → RAG Service
                                           │
                    ┌──────────────────────┼──────────────────────┐
                    ▼                      ▼                      ▼
              Query Rewrite           Search Router          Post Processor
                    │                      │                      │
                    ▼                ┌─────┴─────┐                ▼
              Semantic Search ──────► HNSW Index  ◄─── BM25      Reranker
                    │                └─────┬─────┘                │
                    ▼                      ▼                      ▼
              Embedding Service ←── Cache (Redis) ──────► LLM Service
                                                                    │
                                                                    ▼
                                                              Response
```

**关键技术决策：**
- 数据库：PgVector（HNSW 索引）
- 缓存：Redis（语义缓存）
- Reranker：Cohere / BGE Reranker
- 模型：分级路由（简单问用 fast model，复杂问用 strong model）
- 监控：Prometheus + Grafana

### 5.2 设计一个多 Agent 编排引擎

```
用户请求
    │
    ▼
Orchestrator Agent ─── 任务分解
    │
    ├── Research Agent ──→ 信息检索 + 摘要
    ├── Code Agent    ──→ 代码生成 + 测试
    ├── Review Agent  ──→ 代码审查 + 改进建议
    └── Write Agent   ──→ 文档生成
    │
    ▼
Synthesizer Agent ─── 结果合并
    │
    ▼
最终输出
```

**通信机制：**
- 状态共享：Redis / 内存消息队列
- 任务队列：每个 Agent 独立任务队列
- 同步方式：异步 + 回调

---

## 6. 算法 & 数学基础

### 6.1 余弦相似度的计算方式

```java
public double cosineSimilarity(float[] a, float[] b) {
    double dotProduct = 0, normA = 0, normB = 0;
    for (int i = 0; i < a.length; i++) {
        dotProduct += a[i] * b[i];
        normA += a[i] * a[i];
        normB += b[i] * b[i];
    }
    return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
}
```

### 6.2 什么是 Attention 机制？一句话说清楚

> 让模型在生成每个词时，关注输入序列中**最相关的部分**，而不是对所有词一视同仁。

### 6.3 对比 Pre-training / Fine-tuning / RAG / Agent

| 方式 | 修改模型参数？ | 需要数据集？ | 适用场景 |
|:-----|:--------------|:------------|:---------|
| **Pre-training** | ✅ 从头训练 | ✅ 海量（TB级） | 通用基础模型 |
| **Fine-tuning** | ✅ 增量训练 | ✅ 少量（百-千条） | 领域适配、指令跟随 |
| **RAG** | ❌ 不修改 | ✅ 业务文档 | 知识密集型问答 |
| **Agent** | ❌ 不修改 | ❌ 不需要 | 工具调用、多步推理 |

---

## 7. 实战场景题

### 7.1 生产环境 LLM 调用超时怎么处理？

```java
// Java — Spring AI 超时配置
@Bean
public ChatClient.Builder chatClientBuilder(OpenAiChatModel chatModel) {
    var options = OpenAiChatOptions.builder()
        .withMaxTokens(1024)
        .withTemperature(0.7f)
        .build();
    return ChatClient.builder(chatModel)
        .defaultOptions(options);
}
```

**Python：**

```python
# Python — requests 超时
response = client.chat.completions.create(
    model="deepseek-chat",
    messages=[...],
    timeout=30  # 30秒超时
)
```

**降级策略：**
1. 重试（指数退避，最多 3 次）
2. 降级（返回缓存结果 / 预设兜底回答）
3. 熔断（连续失败 > 阈值后停调，5分钟后恢复）
4. 备用模型（A → B → C 模型链）

### 7.2 如何处理用户隐私数据（PII）？

**脱敏策略：**

```java
// Pattern 匹配替换
public String maskPII(String text) {
    text = text.replaceAll("1[3-9]\\d{9}", "[手机号]");         // 手机号
    text = text.replaceAll("\\d{17}[\\dX]", "[身份证]");        // 身份证
    text = text.replaceAll("\\w+@\\w+\\.\\w+", "[邮箱]");       // 邮箱
    return text;
}
```

### 7.3 Logging 方案设计

```yaml
# Spring Boot + Logback 配置
%highlight(%-5level) %d{yyyy-MM-dd HH:mm:ss} [%thread] %cyan(%logger{36}) - %msg%n

# 生产环境建议日志字段
# [用户ID] [会话ID] [Token用量] [延迟ms] [请求内容摘要] [响应内容摘要]
```

**日志安全注意事项：**
- ❌ 不记录完整 Prompt/Response（Token 多 + 含敏感信息）
- ✅ 记录摘要（前 50 字符）+ Token 数 + 延迟
- ✅ 通过 Logstash + Elasticsearch 实现日志检索
- ❌ 日志不落盘（仅 stream 到集中式日志平台）

---

## 附录

### LLM 相关数学概念速查

| 概念 | 一句话说清楚 |
|:-----|:-------------|
| **Embedding** | 把文本变成固定长度的浮点数向量 |
| **余弦相似度** | 两个向量之间的夹角余弦，值越大越相似 |
| **Attention** | 给序列中不同位置分配不同的权重 |
| **Softmax** | 把一组数归一化成概率分布 |
| **Cross-Entropy** | 衡量预测分布和真实分布的差异 |

### 常用模型参数

| 参数 | 作用 | 推荐值 |
|:-----|:-----|:------|
| temperature | 控制随机性（越大越随机） | 0.0-1.0 |
| top_p | 累加概率截断采样 | 0.8-0.95 |
| max_tokens | 最大输出长度 | 视场景而定 |
| presence_penalty | 鼓励新话题 | 0.0-0.6 |
| frequency_penalty | 减少重复 | 0.0-1.0 |

---

> 💡 **面试技巧：**
> 1. **STAR 法则**：Situation → Task → Action → Result，用具体项目经历说话
> 2. **T 型回答**：先讲核心（一句话），再展开细节
> 3. **注意边界感**：不懂的领域直接说"这块我还没深入，但我了解的是……"
> 4. **反问环节**："我们这个岗位主要用 Spring AI 还是 LangChain4j？" 等
