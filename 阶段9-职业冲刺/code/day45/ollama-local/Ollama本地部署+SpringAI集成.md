# Ollama 本地部署 + Spring AI 集成

> Day 45 · 前沿拓展 · 2026-05-28

---

## 是什么 & 为什么

**Ollama** 是本地运行大模型的最简方案。开源、跨平台、一键部署。

**为什么需要本地模型？**
1. ✅ **隐私** — 数据不出本地，适合敏感业务
2. ✅ **零成本** — 没有 API 调用费，适合高频测试
3. ✅ **离线可用** — 断网也能用
4. ✅ **延迟低** — 没有网络开销
5. ⚠️ **能力弱** — 小模型（3B-9B）能力远不如 DeepSeek / GPT-4

---

## 当前环境状态

```
✅ Ollama 运行中  |  pid: 187  |  已安装 7 个模型
```

| 模型 | 大小 | 用途 |
|:-----|:----:|:-----|
| `qwen2.5:0.5b` | 397 MB | 入门测试 |
| `qwen2.5:1.5b` | 986 MB | 轻量中文对话 |
| `qwen2.5:3b` | 1.9 GB | 日常测试推荐 |
| `qwen3.5:9b` | 6.6 GB | 本地最强中文 |
| `llama3.2:latest` | 2.0 GB | 英文场景 |
| `gemma4:26b` | 17 GB | 需要 GPU |
| `gemma4:e4b` | 9.6 GB | Google 最新模型 |

---

## 基础操作

### 安装

```bash
# Linux（一行搞定）
curl -fsSL https://ollama.com/install.sh | sh

# macOS / Windows：官网下载安装包
# https://ollama.com/download
```

### 启动服务

```bash
ollama serve                   # 启动 Ollama 服务（默认 11434 端口）
```

### 模型管理

```bash
# 拉取模型
ollama pull qwen2.5:1.5b       # 下载模型
ollama pull llama3.2           # 不指定 tag = latest

# 列出已安装
ollama list

# 删除模型
ollama rm qwen2.5:0.5b

# 查看模型信息
ollama show qwen2.5:1.5b
```

### 对话测试

```bash
# 交互模式
ollama run qwen2.5:1.5b

# 单次查询
ollama run qwen2.5:1.5b "什么是 RAG？"

# 指定参数
ollama run qwen2.5:1.5b --temperature 0.3 "用一句话解释 Attention"
```

---

## Spring AI 集成 Ollama（核心实践）

### 依赖

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-ollama-spring-boot-starter</artifactId>
    <version>1.0.0-M6</version>
</dependency>
```

### 配置

```yaml
# application.yml
spring:
  ai:
    ollama:
      base-url: http://localhost:11434   # Ollama 默认地址
      chat:
        model: qwen2.5:1.5b              # 使用的模型
        options:
          temperature: 0.7
          top-p: 0.9
          num-predict: 2048               # max tokens
      embedding:
        model: qwen2.5:1.5b              # 嵌入模型（通常用同一个）
```

### 完整 Service 示例

```java
@Service
public class LocalAiService {

    private final ChatClient chatClient;

    public LocalAiService(ChatClient.Builder builder) {
        // Spring AI 自动注入 OllamaChatModel Bean
        this.chatClient = builder.build();
    }

    public String chat(String message) {
        return chatClient.prompt()
            .user(message)
            .call()
            .content();
    }

    public Flux<String> stream(String message) {
        return chatClient.prompt()
            .user(message)
            .stream()
            .content();
    }

    public String chatWithSystem(String system, String user) {
        return chatClient.prompt()
            .system(system)
            .user(user)
            .call()
            .content();
    }
}
```

### 多模型切换

```java
@Configuration
public class OllamaConfig {

    @Bean
    @Primary
    public OllamaChatModel fastChatModel(OllamaApi api) {
        return OllamaChatModel.builder()
            .ollamaApi(api)
            .defaultOptions(OllamaOptions.builder()
                .model("qwen2.5:1.5b")     // 快速模型
                .temperature(0.7)
                .build())
            .build();
    }

    @Bean
    public OllamaChatModel qualityChatModel(OllamaApi api) {
        return OllamaChatModel.builder()
            .ollamaApi(api)
            .defaultOptions(OllamaOptions.builder()
                .model("qwen3.5:9b")       // 高质量模型
                .temperature(0.3)
                .numPredict(4096)
                .build())
            .build();
    }
}
```

**使用：**

```java
@Service
public class RoutingService {
    private final @Qualifier("fastChatModel") OllamaChatModel fastModel;
    private final @Qualifier("qualityChatModel") OllamaChatModel qualityModel;

    public String route(String query) {
        // 简单问题走快速模型
        if (query.length() < 20) {
            return fastModel.call(query);
        }
        // 复杂问题走高质量模型
        return qualityModel.call(query);
    }
}
```

### Ollama + PgVector 本地 RAG

```java
@Configuration
public class LocalRagConfig {

    @Bean
    public VectorStore localVectorStore(
            JdbcTemplate jdbcTemplate,
            OllamaEmbeddingModel embeddingModel) {

        // PgVector + Ollama Embedding = 完全本地 RAG
        return PgVectorStore.builder()
            .jdbcTemplate(jdbcTemplate)
            .embeddingModel(embeddingModel)
            .distanceType(VectorStore.DistanceType.COSINE)
            .build();
    }

    @Bean
    public ChatClient localRagChatClient(OllamaChatModel chatModel) {
        return ChatClient.builder(chatModel)
            .defaultAdvisors(new QuestionAnswerAdvisor(
                localVectorStore(...),
                SearchRequest.defaults().withTopK(3)
            ))
            .build();
    }
}
```

---

## Python 对接 Ollama

```python
# 通过 OpenAI 兼容 API 调用
from openai import OpenAI

client = OpenAI(
    base_url="http://localhost:11434/v1",  # Ollama 兼容接口
    api_key="ollama",                       # 任意值，本地不验证
)

# Chat
response = client.chat.completions.create(
    model="qwen2.5:1.5b",
    messages=[{"role": "user", "content": "什么是 RAG？"}],
)
print(response.choices[0].message.content)

# Streaming
stream = client.chat.completions.create(
    model="qwen2.5:1.5b",
    messages=[{"role": "user", "content": "讲个故事"}],
    stream=True,
)
for chunk in stream:
    print(chunk.choices[0].delta.content or "", end="", flush=True)

# Embedding
response = client.embeddings.create(
    model="qwen2.5:1.5b",
    input="要嵌入的文本",
)
embedding = response.data[0].embedding
print(f"维度: {len(embedding)}")
```

---

## 生产部署建议

### 1. 模型选择矩阵

| 场景 | 推荐模型 | 大小 | 说明 |
|:-----|:---------|:----:|:-----|
| 开发测试 | qwen2.5:0.5b | 397MB | 最快启动 |
| 日常聊天 | qwen2.5:3b | 1.9GB | 速度/质量均衡 |
| 中文 RAG | qwen3.5:9b | 6.6GB | 本地最强中文 |
| 英文场景 | llama3.2 | 2.0GB | Meta 官方推荐 |
| 有 GPU | gemma4:9b | 9.6GB | Google 最新 |

### 2. Docker 部署

```yaml
# docker-compose.yml
version: '3.8'
services:
  ollama:
    image: ollama/ollama:latest
    ports:
      - "11434:11434"
    volumes:
      - ./ollama_data:/root/.ollama
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: all
              capabilities: [gpu]
    restart: unless-stopped
```

### 3. 性能调优

```yaml
# Ollama 服务端参数
OLLAMA_NUM_PARALLEL: 4       # 并行请求数
OLLAMA_MAX_LOADED_MODELS: 2  # 同时加载的模型数
OLLAMA_KEEP_ALIVE: 5m        # 模型在内存中保留时间
```

### 4. 混合架构（推荐）

```
分层路由策略：

用户请求
    │
    ▼
判断：简单问题？
    ├── ✅ → Ollama 本地模型（0 成本，低延迟）
    └── ❌ → DeepSeek API（强大，按量计费）
```

**实现：**

```java
public class SmartRouter {
    private final ChatClient localClient;     // Ollama
    private final ChatClient cloudClient;     // DeepSeek

    public String smartChat(String query) {
        // 规则判断：简单问题走本地
        if (isSimpleQuery(query)) {
            return localClient.prompt().user(query).call().content();
        }
        // 复杂问题走云端
        return cloudClient.prompt().user(query).call().content();
    }

    private boolean isSimpleQuery(String q) {
        // 长度 < 30 + 无专业术语 + 无多步推理
        return q.length() < 30
            && !q.contains("代码")
            && !q.contains("对比")
            && !q.contains("为什么");
    }
}
```

---

> **一句话总结 Ollama：**  
> 本地模型不求"最强"，只求"够用"。开发测试零成本，简单任务零延迟，隐私数据零泄露。
