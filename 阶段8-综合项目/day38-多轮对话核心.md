# Day 38 — 项目搭建 + 多轮对话核心

## 今日任务

| 项目 | 内容 |
|:-----|:-----|
| **智能客服平台** | Spring Boot + Spring AI + PgVector 全栈项目 |
| **多轮对话** | 会话记忆 + DeepSeek API 集成 |
| **Web 前端** | Thymeleaf 聊天界面 |
| **产出** | ✅ 可用的多轮对话客服系统 |

## 1. 项目架构

### 包结构

```
cs-platform/
├── pom.xml
├── src/main/java/com/ai/cs/
│   ├── CustomerServiceApplication.java     # 入口
│   ├── config/
│   │   └── AppConfig.java                  # HttpClient + CORS
│   ├── chat/
│   │   ├── ChatController.java             # /api/chat
│   │   ├── ConversationService.java        # 对话编排
│   │   ├── ConversationMemory.java         # 会话记忆
│   │   ├── ChatRequest.java                # 请求DTO
│   │   └── ChatResponse.java               # 响应DTO
│   ├── admin/
│   │   ├── WebController.java              # 前端页面路由
│   │   └── AdminController.java            # /api/admin 监控
├── src/main/resources/
│   ├── application.yml                     # 配置
│   ├── local.properties                    # API Key（不提交）
│   └── templates/
│       └── index.html                      # 聊天界面
└── Dockerfile                              # TODO: Day 42
```

### 请求流程

```
用户 → index.html (Thymeleaf)
         ↓ POST /api/chat {message, sessionId?}
    ChatController
         ↓
    ConversationService
         ↓ (1) ConversationMemory.addMessage("user", ...)
         ↓ (2) buildPrompt(history) → 带上下文的 prompt
         ↓ (3) DeepSeek API → reply
         ↓ (4) ConversationMemory.addMessage("assistant", ...)
         ↓
    ChatResponse {sessionId, reply, historySize}
         ↓
    index.html 显示回复
```

## 2. 核心实现

### 2.1 会话记忆（ConversationMemory）

```java
@Service
public class ConversationMemory {
    private final ConcurrentHashMap<String, List<Map<String,Object>>> sessions;
    private final int maxHistory;  // 默认 10 条

    public String addMessage(String sessionId, String role, String content) {
        // 自动生成 sessionId
        if (sessionId == null) {
            sessionId = UUID.randomUUID().toString();
        }
        // 存储消息 + 自动裁剪
        history.add(message);
        trimHistory(history);  // 超出 maxHistory 自动删除最旧的
        return sessionId;
    }

    public List<Map<String,Object>> getHistory(String sessionId) {
        // 返回仅含 role + content 的副本，防止外部修改
    }
}
```

### 2.2 对话编排（ConversationService）

```java
public ChatResponse processMessage(String sessionId, String message) {
    // 1. 存储用户消息
    String sid = memory.addMessage(sessionId, "user", message);

    // 2. 构建带上下文的 prompt
    String prompt = buildPrompt(sid);
    // → "你是一个专业的AI客服助手。
    //    ## 对话历史
    //    用户: 你好，我想退货
    //    客服: (上轮回复)
    //    用户: 退货流程是什么？
    //    客服:"

    // 3. 调用 DeepSeek API
    String reply = callLLM(prompt);

    // 4. 存储助手回复
    memory.addMessage(sid, "assistant", reply);

    return new ChatResponse(sid, reply, ...);
}
```

### 2.3 Prompt 格式

构建带对话历史上下文的 Prompt：

```
系统: 你是一个专业的AI客服助手...

## 对话历史
用户: 你好，我想退货
客服: 请问您想退什么商品？
用户: 退货流程是什么？
客服:                        ← 模型从这里接续生成
```

## 3. API 端点

| 端点 | 方法 | 功能 | 请求体 |
|:-----|:----:|:-----|:-------|
| `/api/chat` | POST | 发送消息 | `{message, sessionId?}` |
| `/api/chat/history/{sessionId}` | GET | 查看历史 | - |
| `/api/chat/{sessionId}` | DELETE | 清除会话 | - |
| `/api/admin/status` | GET | 系统状态 | - |
| `/api/admin/health` | GET | 健康检查 | - |
| `/` | GET | 聊天界面 | - |

### 调用示例

```json
// 请求
POST /api/chat
{"message": "你好，我想退货"}

// 响应
{
  "sessionId": "00ed1dc3-5759-47b9-8c34-a3fe20d90919",
  "reply": "您好！很高兴为您服务。请问您想退货的商品是什么？",
  "historySize": 2,
  "timestamp": 1779950584565
}
```

### 多轮对话

```json
// 第二轮
POST /api/chat
{"message": "退货流程是什么？", "sessionId": "00ed1dc3-..."}

// 响应 (historySize=4, 说明记住了前一轮)
{
  "reply": "退货流程大致如下：\n1. 确认退货条件...\n2. 联系客服...",
  "historySize": 4
}
```

## 4. 前端界面

使用 Thymeleaf 模板提供简洁的聊天界面：

| 功能 | 实现 |
|:-----|:-----|
| 消息气泡 | 用户蓝色右对齐，客服白色左对齐 |
| 打字指示器 | 等待回复时显示动画 |
| 消息时间戳 | 每条消息显示发送时间 |
| 新对话按钮 | 清除会话，开始新对话 |
| 会话 ID | localStorage 持久化，刷新不丢失 |
| 轮次统计 | 底部信息栏实时更新 |

## 5. 测试结果

### Java 后端

```
=== Health ===           UP
=== Status ===           CS Platform v1.0.0, 64MB/104MB

=== Chat Turn 1 ===
  sessionId: 00ed1dc3-...
  reply: "您好！很高兴为您服务。请问您想退货的商品是什么？"
  historySize: 2

=== Chat Turn 2 (same session) ===
  reply: "退货流程大致如下：1.确认退货条件 2.联系客服 3.准备商品..."
  historySize: 4   ← 记住了上一轮

=== Web UI ===          HTTP 200
```

### Python Demo

```
Test 1: Memory Basics              ✅ Auto-generate, add, clear
Test 2: Multi-turn Conversation     ✅ 4 turns, 8 messages
Test 3: Session Isolation           ✅ A=退货 B=会员 无串扰
Test 4: Memory Trimming             ✅ 5 turns → 6 messages (max=3)
Test 5: Prompt Format               ✅ 包含对话历史+系统提示
Test 6: Full Scenario               ✅ 4 turns realistic flow
```

## 6. 关键技术决策

| 决策 | 理由 |
|:-----|:------|
| `@Service ConversationMemory` | 单例 Bean 全局持有所有会话 |
| `ConcurrentHashMap` + `synchronizedList` | 线程安全，支持并发请求 |
| `getHistory()` 返回过滤副本 | 防止外部代码修改内部状态 |
| Prompt 带完整历史 | 多轮上下文不丢失 |
| localStorage 存 sessionId | 刷新页面不丢失会话 |
| Thymeleaf 而不是 VUE/React | 零前端构建，单 JAR 部署 |

## 7. 文件清单

```
day38/
├── cs-platform/
│   ├── pom.xml
│   ├── local.properties
│   └── src/main/java/com/ai/cs/
│       ├── CustomerServiceApplication.java
│       ├── config/AppConfig.java
│       ├── chat/
│       │   ├── ChatController.java
│       │   ├── ConversationService.java
│       │   ├── ConversationMemory.java
│       │   ├── ChatRequest.java
│       │   └── ChatResponse.java
│       └── admin/
│           ├── WebController.java
│           └── AdminController.java
│   └── src/main/resources/
│       ├── application.yml
│       └── templates/index.html
└── python/
    └── chat_demo.py
```

## 8. 下一步（Day 39）

明天将加入 **RAG 知识库**：
- 文档管理与上传
- 向量化入库（PgVector）
- 语义检索（知识库问答）
- 智能客服引用知识库回复
