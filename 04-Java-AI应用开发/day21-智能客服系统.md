# 第21天：实战 — 智能客服系统（Java 版）🏢

> **学习目标：** 综合 Day 16-20 全部知识，构建一个包含意图识别、多轮对话、流式输出、异常重试的完整智能客服系统
> **代码语言：** Java（主）
> **前置知识：** Day 16-20（Spring AI 全部基础能力）

---

## 📋 目录

1. [系统架构设计](#1-系统架构设计)
2. [意图识别](#2-意图识别)
3. [业务提示词模板](#3-业务提示词模板)
4. [统一 AI 服务层](#4-统一-ai-服务层)
5. [会话管理与多轮记忆](#5-会话管理与多轮记忆)
6. [SSE 流式输出](#6-sse-流式输出)
7. [异常重试与降级](#7-异常重试与降级)
8. [单元测试](#8-单元测试)
9. [完整测试结果](#9-完整测试结果)
10. [系统演进建议](#10-系统演进建议)

---

## 1. 系统架构设计

### 架构图

```
┌──────────────────────────────────────────────────┐
│                  前端 (chat.html)                   │
│              SSE 流式渲染 · 打字机效果               │
└──────────────────────┬───────────────────────────┘
                       │ POST /api/chat
                       │ POST /api/chat/stream
                       ▼
┌──────────────────────────────────────────────────┐
│            CsController (REST 控制器)               │
└──────────────────────┬───────────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────────┐
│              AiService (统一服务层)                 │
│  ┌──────────┐  ┌─────────┐  ┌───────────────┐   │
│  │ 意图识别  │→ │ 模板路由  │→ │ 重试 + 降级    │   │
│  └──────────┘  └─────────┘  └───────┬───────┘   │
└──────────────────────────────────────┬────────────┘
                                       │
              ┌────────────────────────┼──────────────┐
              │                        │              │
              ▼                        ▼              ▼
┌──────────────────────┐  ┌──────────────────────┐
│  SessionManager       │  │   PromptTemplates    │
│  多轮对话记忆 · 裁剪   │  │  5 种业务场景提示词    │
└──────────────────────┘  └──────────────────────┘
```

### 核心流程

```
用户发送消息
    │
    ▼
1. 意图识别 ──→ AI 判断属于哪个场景
    │               │
    ▼               ▼
2. 加载模板 ──→ 订单/退款/技术/投诉/咨询
    │
    ▼
3. 拼接历史 ──→ System + 多轮记忆
    │
    ▼
4. AI 调用 ──→ 成功 → 返回结果
    │               │
    │               ▼
    │          保存到会话
    │
    ▼ (失败)
5. 重试 ──→ 最多 2 次，指数退避
    │
    ▼ (全部失败)
6. 降级 ──→ 友好提示 + 建议热线
```

---

## 2. 意图识别

### 核心思路

**用 AI 识别 AI 的意图** — 让大模型自己判断用户问题属于哪类客服场景。

### 分类器提示词

```java
public static final String INTENT_CLASSIFIER = """
    你是一个客服意图分类器。根据用户的第一句话，判断其意图类型。
    只回复 JSON 格式：
    {
        "intent": "order | refund | tech | complaint | general",
        "confidence": 0.0-1.0,
        "reason": "简短判断理由"
    }
    
    判断规则：
    - order：用户询问订单状态、物流、商品信息
    - refund：用户要求退款、退货、售后
    - tech：用户询问技术问题、功能使用
    - complaint：用户表达不满、投诉
    - general：普通咨询、问候、闲聊
    """;
```

### 实现

```java
private IntentType detectIntent(String message) {
    String result = chatClient.prompt()
        .system(PromptTemplates.INTENT_CLASSIFIER)
        .user(message)
        .call()
        .content();
    
    // 解析 JSON 提取 intent
    if (result.contains("\"intent\"") {
        for (IntentType t : IntentType.values()) {
            if (result.contains("\"" + t.code + "\"")) {
                return t;
            }
        }
    }
    return IntentType.GENERAL; // 默认
}
```

### 测试结果

| 用户问题 | 识别意图 | 准确率 |
|---------|---------|:------:|
| "我的订单什么时候到？" | **ORDER** ✅ | 100% |
| "我要退货退款" | **REFUND** ✅ | 100% |
| "你们API怎么调用？" | **TECH_SUPPORT** ✅ | 100% |
| "你们服务太差了！" | **COMPLAINT** ✅ | 100% |

> **启示：** 用 AI 做意图分类比传统规则引擎（关键词匹配）更智能。"API怎么调用"没有"技术"二字，但 AI 能理解这是技术问题。

---

## 3. 业务提示词模板

### 五种业务场景

| 意图 | 提示词风格 | 关键元素 |
|:----:|-----------|---------|
| 🛒 **ORDER** | 专业耐心 | 订单号、物流、地址修改 |
| 💰 **REFUND** | 温暖同理心 | 先安抚、流程指导、时效说明 |
| 🔧 **TECH** | 技术条理 | 环境确认、分步排查、Java 示例 |
| 💬 **COMPLAINT** | 真诚道歉 | 先道歉、记录、解决时限 |
| 💁 **GENERAL** | 热情友好 | 介绍、引导、表情符号 |

### 模板示例：退款售后

```java
public static String refundSystem() {
    return """
        你是一个售后客服专员，处理退款和售后问题。
        
        回答要求：
        - 先安抚情绪，再解决问题
        - 明确告知退款时效（通常3-7个工作日）
        - 需要提供订单号/商品信息时请耐心询问
        - 提供可操作的下一步指引
        - 保持同理心，语气温暖
        
        用户当前问题：
        """;
}
```

### 模板隔离验证

单元测试验证每个意图的提示词都是独立的：

```java
@Test
void testTemplateIsolation() {
    String orderPrompt = PromptTemplates.getSystemPrompt(IntentType.ORDER);
    String refundPrompt = PromptTemplates.getSystemPrompt(IntentType.REFUND);
    assertNotEquals(orderPrompt, refundPrompt);
}
```

---

## 4. 统一 AI 服务层

### AiService.java

```java
@Service
public class AiService {
    
    private final ChatClient chatClient;
    private final SessionManager sessionManager;
    
    public ChatResponse chat(String sessionId, String message) {
        // 1. 新会话 → 创建
        if (sessionId == null) {
            sessionId = sessionManager.createSession();
        }
        
        // 2. 保存用户消息
        sessionManager.addUserMessage(sessionId, message);
        
        // 3. 意图识别
        IntentType intent = detectIntent(message);
        
        // 4. 构建消息列表：系统提示 + 历史
        String systemPrompt = PromptTemplates.getSystemPrompt(intent);
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.addAll(sessionManager.getHistory(sessionId));
        
        // 5. 调用 AI + 自动重试
        String reply = callWithRetry(messages, MAX_RETRIES);
        
        // 6. 保存 AI 回复
        sessionManager.addAssistantMessage(sessionId, reply);
        
        return new ChatResponse(sessionId, reply, intent);
    }
}
```

### 为什么叫"统一服务层"？

**隔离底层框架差异：**
- Controller 不直接调 ChatClient
- 所有 AI 逻辑集中在 AiService 中
- 未来切换模型/框架只需改 AiService 内部
- Controller 永远返回标准 Java DTO

---

## 5. 会话管理与多轮记忆

### SessionManager.java

```java
@Component
public class SessionManager {
    
    /** 最多保留 20 条消息 */
    private static final int MAX_HISTORY = 20;
    
    private final ConcurrentHashMap<String, List<Message>> sessions = new ConcurrentHashMap<>();
    
    public String createSession() {
        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        sessions.put(sessionId, new ArrayList<>());
        return sessionId;
    }
    
    public void addUserMessage(String sessionId, String content) {
        List<Message> history = getOrCreate(sessionId);
        history.add(new UserMessage(content));
        trimHistory(history);
    }
    
    private void trimHistory(List<Message> history) {
        if (history.size() > MAX_HISTORY) {
            int remove = history.size() - MAX_HISTORY;
            history.subList(0, remove).clear();
        }
    }
}
```

### 多轮对话实测

```
第一轮: "我的订单号是 ORD-2026-001"
   → AI: 显示物流信息（广州分拨中心处理中）

第二轮: "能不能改地址到北京？"
   → AI: 记得订单号 ORD-2026-001！
         "您的订单 ORD-2026-001 目前包裹已到达
          广州分拨中心，修改地址可能影响配送..."
```

✅ AI **完全记得**对话历史中的订单号和物流状态

---

## 6. SSE 流式输出

### 服务端：Flux 管道

```java
public Flux<String> chatStream(String sessionId, String message) {
    return Flux.concat(
        // 1. 先发元数据（sessionId + 意图）
        Flux.just("data:{\"type\":\"meta\",\"sessionId\":\"...\",\"intent\":\"...\"}\n\n"),
        
        // 2. 流式内容（逐字推送）
        chatClient.prompt()
            .messages(messages)
            .stream()
            .content()
            .map(chunk -> "data:" + chunk + "\n\n"),
        
        // 3. 完成标记
        Flux.just("data:{\"type\":\"done\"}\n\n")
    );
}
```

### 前端：EventSource 客户端

```javascript
const resp = await fetch('/api/chat/stream', {
    method: 'POST',
    body: JSON.stringify({ sessionId, message: msg })
});

const reader = resp.body.getReader();
while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    // 解析 SSE 数据，逐字显示到界面上
}
```

### 前端效果

```
👋 您好！我是智能客服助手
可以帮您处理：
• 📦 订单查询
• 💰 退款售后
• 🔧 技术支持
• 💬 投诉反馈

每个字流式出现，带打字机效果 ✨
意图标签自动显示（如 📦 订单）
```

---

## 7. 异常重试与降级

### 指数退避重试

```java
private String callWithRetry(List<Message> messages, int maxRetries) {
    for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
        try {
            return chatClient.prompt().messages(messages).call().content();
        } catch (Exception e) {
            if (attempt <= maxRetries) {
                // 指数退避：1s, 2s
                Thread.sleep(1000L * attempt);
            }
        }
    }
    // 全部失败 → 降级
    return "😔 抱歉，系统暂时繁忙...";
}
```

### 降级策略

| 情况 | 响应 |
|------|------|
| API 超时 | 等待 1s → 重试 → 再等待 2s → 再重试 |
| API 都失败 | "系统繁忙，请稍后再试，紧急请拨打 400-xxx" |
| 意图识别失败 | 默认 GENERAL，友好引导 |

---

## 8. 单元测试

### 测试覆盖

```java
// 1. 提示词测试
@Test void testIntentTemplates_AllIntentsHaveContent() — 5种模板都有内容
@Test void testIntentClassifierPrompt() — 分类器包含所有意图类型
@Test void testTemplateIsolation() — 不同意图的模板不同

// 2. 意图映射测试
@Test void testIntentCodeMapping() — code ↔ enum 双向映射正确
@Test void testAllIntentsHaveUniqueCodes() — 所有 code 唯一

// 3. 会话管理测试
@Test void testSessionManager_CreateAndAddMessages() — 会话创建+消息
@Test void testSessionManager_HistoryTrimming() — 历史裁剪≤20条
@Test void testSessionManager_MultipleSessions() — 多会话隔离

// 4. 边界测试
@Test void testUnknownIntentReturnsGeneral() — 未知 code → GENERAL
```

### 运行结果

```
Tests run: 12, Failures: 0, Errors: 0, Skipped: 0 ✅
```

---

## 9. 完整测试结果

### API 测试

| 接口 | 方法 | 测试结果 |
|:----|:----:|:--------:|
| POST /api/chat | 同步对话 | ✅ 意图识别+路由+多轮记忆 |
| POST /api/chat/stream | 流式对话 SSE | ✅ 打字机效果 |
| GET /api/chat | 简单 GET 调用 | ✅ 兼容浏览器测试 |
| GET /api/sessions | 查看会话 | ✅ 显示所有活跃会话 |
| POST /api/sessions | 创建新会话 | ✅ 返回 sessionId |
| GET /chat.html | 聊天界面 | ✅ 浏览器可用 |

### 意图识别准确率

| 用户输入 | 识别为 | 结果 |
|---------|:-----:|:----:|
| "我的订单什么时候到？" | ORDER | ✅ |
| "我要退货退款" | REFUND | ✅ |
| "你们API怎么调用？" | TECH_SUPPORT | ✅ |
| "你们服务太差了！" | COMPLAINT | ✅ |

### 多轮对话记忆

```
第一轮: 用户提供订单号 ORD-2026-001
第二轮: AI 记得订单号并继续处理地址修改 ✅
```

---

## 10. 系统演进建议

### Day 22+ 可扩展的功能

| 功能 | 对应天数 | 说明 |
|------|:-------:|------|
| **RAG 知识库** | Day 22-26 | 把 FAQ/产品文档加载为检索源 |
| **人工客服转接** | - | 复杂问题转人工，AI 先记录上下文 |
| **情感分析** | - | 检测用户情绪，自动升级投诉 |
| **多语言支持** | - | 按用户输入语言切换提示词模板 |
| **A/B 测试** | - | 对比不同模板的客户满意度 |
| **限流与配额** | Day 62 | 防止滥用 |

### 与 Days 16-20 知识的关系

| 知识点 | 在本项目的应用 |
|--------|--------------|
| Day 16: Spring AI 环境 | 项目基础框架 |
| Day 17: 提示词模板 | PromptTemplates 5种业务模板 |
| Day 18: 多轮对话+SSE | SessionManager + 流式输出 |
| Day 19: @Tool 调用 | 可用于查询真实订单/退款接口 |
| Day 20: RAG + PgVector | 可接入产品文档知识库 |

---

### 代码位置

| 文件 | 路径 |
|------|------|
| **教程** | `04-Java-AI应用开发/day21-智能客服系统.md` |
| **DemoApplication.java** | `code/day21/.../DemoApplication.java` |
| **AiService.java** | `code/day21/.../service/AiService.java` |
| **SessionManager.java** | `code/day21/.../service/SessionManager.java` |
| **CsController.java** | `code/day21/.../controller/CsController.java` |
| **IntentType.java** | `code/day21/.../model/IntentType.java` |
| **PromptTemplates.java** | `code/day21/.../prompt/PromptTemplates.java` |
| **chat.html** | `code/day21/src/main/resources/static/chat.html` |
| **AiServiceTest.java** | `code/day21/src/test/.../AiServiceTest.java` |

---

**⏭️ 明日预告：Day 22 — 文档加载与切分（Java 版）**
- Apache Tika/PDFBox 读取 PDF/Word
- Token 切分 + 重叠区
- 语义切分（基于段落/Markdown 层级）
- 正式进入 RAG 工程化阶段
