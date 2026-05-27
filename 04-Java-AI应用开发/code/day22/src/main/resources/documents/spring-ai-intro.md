# Spring AI 框架介绍

## 什么是 Spring AI？

Spring AI 是一个面向 AI 应用的 Spring 生态框架。它的目标是将 AI 能力以 Spring 的风格（依赖注入、模板模式、可配置）集成到 Java 应用中。

## 核心特性

### 1. 多模型支持
Spring AI 支持多种大语言模型提供商：
- OpenAI（GPT-4、GPT-3.5）
- Azure OpenAI
- Ollama（本地模型）
- Amazon Bedrock
- Google Vertex AI
- HuggingFace

你只需切换依赖和配置，代码几乎不用改。

### 2. 提示词模板（PromptTemplate）
类似 Spring 的 JdbcTemplate，Spring AI 提供了 PromptTemplate：

```java
PromptTemplate template = new PromptTemplate("你是{role}，请用{style}语气回答");
Prompt prompt = template.create(Map.of("role", "客服", "style", "友好"));
```

支持参数注入，告别字符串拼接。

### 3. 结构化输出（Output Parsers）
自动将 AI 的文本响应解析为 Java 对象：

```java
BeanOutputConverter<BookInfo> converter = new BeanOutputConverter<>(BookInfo.class);
```

### 4. 向量数据库集成
内置支持多种向量数据库：
- PgVector（PostgreSQL 扩展）
- Redis
- MongoDB Atlas
- Chroma
- Pinecone
- Qdrant
- Weaviate
- Milvus

### 5. RAG 支持
Spring AI 提供了标准的 RAG 流程：
- DocumentReader → DocumentTransformer → VectorStore → QuestionAnswerAdvisor
- 内置 TokenTextSplitter 用于文档切分
- TikaDocumentReader 用于解析 PDF/DOCX/HTML

### 6. 工具调用（Function Calling）
通过 @Tool 注解将 Java 方法暴露为 AI 可调用的工具：

```java
@Component
public class MyTools {
    @Tool("获取城市天气")
    public String getWeather(String city) {
        return city + "：晴，25°C";
    }
}
```

## 典型 RAG 流程

1. 加载文档（TextReader / TikaDocumentReader）
2. 文档切分（TokenTextSplitter / 自定义 Splitter）
3. 向量化（EmbeddingModel）
4. 存储（VectorStore.add()）
5. 检索（VectorStore.similaritySearch()）
6. 增强（QuestionAnswerAdvisor）
7. 生成（ChatClient）

## 与 LangChain 的对比

| 维度 | LangChain (Python) | Spring AI (Java) |
|------|-------------------|-----------------|
| 架构 | LCEL (组合式管道) | Advisor (装饰器模式) |
| 切分 | RecursiveCharacterTextSplitter | TokenTextSplitter |
| 文档加载 | DocumentLoader 系列 | DocumentReader 系列 |
| 工具调用 | @tool 装饰器 | @Tool 注解 |
| 记忆 | ChatMessageHistory | ChatMemory 接口 |
| RAG | create_retrieval_chain | QuestionAnswerAdvisor |

## 使用场景

- 智能客服系统
- 企业知识库问答
- 代码审查助手
- 文档分析工具
- 自动化报告生成

---

*本文档用于 Day 22 的文档处理管线测试。*
*包含中英文混合内容以便观察不同切分策略的效果。*
