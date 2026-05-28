# 第18天：多轮对话记忆 + SSE 流式输出

> **学习目标：** 实现有记忆的多轮对话，掌握 SSE（Server-Sent Events）实现打字机效果
> **预计时间：** 2.5 小时
> **代码语言：** Java（主）+ Python（对照）

---

## 📋 目录

1. [回顾 Day 17](#1-回顾-day-17)
2. [为什么需要对话记忆](#2-为什么需要对话记忆)
3. [记忆实现原理](#3-记忆实现原理)
4. [会话管理实战](#4-会话管理实战)
5. [为什么需要流式输出](#5-为什么需要流式输出)
6. [SSE 原理](#6-sse-原理)
7. [SSE 实战（SseEmitter）](#7-sse-实战sseemitter)
8. [前端聊天界面](#8-前端聊天界面)
9. [Python 对照实现](#9-python-对照实现)
10. [今日小结](#10-今日小结)

---

## 1. 回顾 Day 17

昨天我们学会了：

| 能力 | 代码 |
|------|------|
| **提示词模板** | `String.formatted()` + `.user(template)` |
| **结构化输出** | `BeanOutputConverter<BookInfo>` → JSON → Java 对象 |

但昨天的对话是**无状态的**——每次调用 AI 都不记得之前说过什么。

### 今天要解决的问题

| 问题 | 解决方案 |
|------|---------|
| 如何让 AI 记住之前说过什么？ | **对话记忆** — 把历史拼入每次请求 |
| 如何让回复像打字机一样逐字出现？ | **SSE 流式输出** — 逐块推送 |
| 如何同时支持多个用户？ | **会话管理** — 按 sessionId 隔离 |

---

## 2. 为什么需要对话记忆

### 2.1 无记忆的问题

```java
// 第1次：我叫小明
chat("我叫小明")        → "你好小明！"

// 第2次：我叫什么名字？
chat("我叫什么名字？")  → "我不知道你的名字"  ❌ 忘了！
```

每次调用都是独立的 HTTP 请求，AI 没有"之前说过什么"的概念。

### 2.2 记忆的本质

**记忆 = 把历史对话拼入下次请求的上下文**

```
用户：我叫小明
AI：你好小明！

用户：我叫什么名字？
  ↓ 实际发给AI的是：
  [历史] 用户：我叫小明
  [历史] AI：你好小明！
  [当前] 用户：我叫什么名字？
  ↓
AI：你叫小明！✅
```

### 2.3 两种记忆策略

| 策略 | 实现方式 | 优点 | 缺点 |
|------|---------|:----:|:----:|
| **历史拼接**（本日使用） | 把历史对话文本拼入 user 消息 | 简单易懂 | 长对话 Token 开销大 |
| **消息列表**（标准做法） | 传给 API 完整的 messages 数组 | 标准、精确 | 需要 API 支持多消息 |
| **向量记忆**（进阶） | 用 Embedding 检索相关历史 | 可扩展 | 复杂，需向量数据库 |

**今天我们用手动历史拼接**，因为：
1. 原理透明，适合学习
2. 不依赖框架特定 API
3. 容易迁移到其他语言

---

## 3. 记忆实现原理

### 3.1 数据结构

```java
// 会话存储
Map<String, List<Map<String, String>>> sessions = new ConcurrentHashMap<>();

// 每条消息的格式
{"role": "user", "content": "我叫小明"}
{"role": "assistant", "content": "你好小明！"}
```

### 3.2 核心流程

```
用户输入 "我叫什么名字？"
        │
        ▼
1. 从 sessions 获取该会话的历史
        │
        ▼
2. 构建带上下文的提示词：
   "以下是我们的对话历史：
    用户：我叫小明
    AI助手：你好小明！
    
    当前问题：
    用户：我叫什么名字？"
        │
        ▼
3. 调用 AI
        │
        ▼
4. 保存 {user: "我叫什么名字？", assistant: "你叫小明！"} 到历史
        │
        ▼
5. 返回 AI 回复
```

### 3.3 历史裁剪

对话越长，历史越大，Token 消耗越多。需要设置上限：

```java
// 最多保留最近 10 轮（20 条消息）
while (history.size() > MAX_HISTORY_ROUNDS * 2) {
    history.remove(0);  // 移除最早的用户消息
    history.remove(0);  // 移除对应的 AI 回复
}
```

---

## 4. 会话管理实战

### 4.1 ChatService 核心代码

```java
@Service
public class ChatService {

    /** 会话存储：sessionId → 消息历史 */
    private final Map<String, List<Map<String, String>>> sessions = new ConcurrentHashMap<>();
    private static final int MAX_HISTORY_ROUNDS = 10;
    private final ChatClient chatClient;

    public ChatService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    /** 创建新会话 */
    public String createSession() {
        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        sessions.put(sessionId, new ArrayList<>());
        return sessionId;
    }

    /** 同步对话（带记忆） */
    public String chat(String sessionId, String message) {
        // 1. 构建含上下文的提示词
        String prompt = buildPromptWithHistory(sessionId, message);
        
        // 2. 调用 AI
        String response = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        // 3. 保存到历史
        saveExchange(sessionId, message, response);
        return response;
    }

    /** 构建含历史上下文的提示词 */
    private String buildPromptWithHistory(String sessionId, String currentMessage) {
        List<Map<String, String>> history = sessions.getOrDefault(sessionId, Collections.emptyList());
        if (history.isEmpty()) {
            return currentMessage;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("以下是我们的对话历史（请结合历史回答当前问题）：\n\n");
        for (Map<String, String> entry : history) {
            String role = entry.get("role");
            String content = entry.get("content");
            if ("user".equals(role)) {
                sb.append("用户：").append(content).append("\n");
            } else {
                sb.append("AI助手：").append(content).append("\n");
            }
        }
        sb.append("\n--- 当前问题 ---\n");
        sb.append("用户：").append(currentMessage);
        return sb.toString();
    }
}
```

### 4.2 API 测试

```bash
# 1. 创建会话
curl -X POST http://localhost:8082/session
→ {"sessionId":"a1b2c3d4"}

# 2. 第1轮
curl "http://localhost:8082/chat?session=a1b2c3d4&msg=我叫小明"
→ "你好小明！"

# 3. 第2轮（测试记忆）
curl "http://localhost:8082/chat?session=a1b2c3d4&msg=我叫什么名字"
→ "你叫小明！"  ✅ 记住了

# 4. 查看历史
curl "http://localhost:8082/session/a1b2c3d4/history"
→ [{"role":"user","content":"我叫小明"}, ...]
```

### 4.3 多会话隔离

每个 sessionId 拥有独立的历史空间：

```bash
# 会话A
curl "http://localhost:8082/chat?session=A&msg=我叫张三"
# 会话B  
curl "http://localhost:8082/chat?session=B&msg=我叫李四"
# 会话A 只记得张三
curl "http://localhost:8082/chat?session=A&msg=我叫什么"
→ "你叫张三"  ✅ 隔离正确
```

> **原理：** 每个 sessionId 是 ConcurrentHashMap 的一个独立 key

---

## 5. 为什么需要流式输出

### 5.1 同步调用的体验问题

```java
// 同步：等AI全部生成后再返回
String response = chatClient.prompt().user(msg).call().content();
// 用户看到的是... 空白等待 → 突然出现一整段文字
```

如果 AI 生成需要 5 秒，用户就要**盯着空白界面等 5 秒**。

### 5.2 流式调用的体验

```java
// 流式：AI生成一个字，推送一个字
Flux<String> stream = chatClient.prompt().user(msg).stream().content();
// 用户看到的是... "正" → "在" → "生" → "成" → "。" 逐字出现
```

| 方式 | 用户体验 | 首次看到内容时间 | 技术 |
|:----:|:--------:|:---------------:|:----:|
| 同步 | 等待 → 全部出现 | 5s | HTTP 请求 |
| 流式 | 逐字出现 | 0.5s | SSE |

> **核心区别：** 同步是"全部生成完再返回"，流式是"生成一点就推一点"

---

## 6. SSE 原理

### 6.1 什么是 SSE

SSE（Server-Sent Events）是一种服务器推送技术：

```
客户端 ←────────── 服务器
          data: 正
          data: 在
          data: 生
          data: 成
          data: 。
```

### 6.2 SSE vs WebSocket

| 特性 | SSE | WebSocket |
|:----:|:---:|:---------:|
| 方向 | **服务器→客户端** | 双向 |
| 协议 | HTTP | WS |
| 浏览器支持 | ✅ 原生 EventSource API | ✅ WebSocket API |
| 实现难度 | 简单 | 复杂 |
| 适用场景 | AI 流式输出、通知推送 | 实时游戏、协作编辑 |

> **结论：** AI 流式输出用 SSE 最合适（只需服务器推，不需要客户端推）

### 6.3 SSE 数据格式

```
data: 第1块内容
data: 第2块内容
data: 第3块内容
data: [DONE]
```

每一行以 `data:` 开头，每条消息以两个换行 `\n\n` 结束。

---

## 7. SSE 实战（SseEmitter）

### 7.1 SseEmitter 是什么

Spring MVC 自带的 SSE 实现，不需要任何额外依赖：

```java
@GetMapping("/chat/stream")
public SseEmitter streamChat(@RequestParam String session, @RequestParam String msg) {
    SseEmitter emitter = new SseEmitter(300_000L);  // 5分钟超时
    
    executor.execute(() -> {
        chatService.streamChat(session, msg)
            .subscribe(
                chunk -> emitter.send(chunk),      // 逐块推送
                error -> emitter.completeWithError(error),
                () -> emitter.complete()            // 推送完成
            );
    });
    
    return emitter;
}
```

### 7.2 关键点解析

| 组件 | 作用 |
|------|------|
| `SseEmitter` | Spring 的 SSE 发射器，管理连接生命周期 |
| `emitter.send(chunk)` | 推送一个数据块到客户端 |
| `emitter.complete()` | 正常结束 SSE 连接 |
| `emitter.completeWithError(e)` | 异常结束 |
| `executor.execute()` | 异步执行，不阻塞 Tomcat 线程 |

### 7.3 ChatService 中的流式实现

```java
public Flux<String> streamChat(String sessionId, String message) {
    String prompt = buildPromptWithHistory(sessionId, message);
    StringBuilder collector = new StringBuilder();

    return chatClient.prompt()
            .user(prompt)
            .stream()
            .content()
            .doOnNext(collector::append)       // 逐块收集
            .doOnComplete(() -> {
                saveExchange(sessionId, message, collector.toString());
            });
}
```

**核心设计：** `StringBuilder` 在流过程中逐块收集完整回复，流结束时自动保存到历史。

---

## 8. 前端聊天界面

### 8.1 HTML 页面

我们提供了一个完整的聊天界面，位于：

```
http://localhost:8082/chat.html
```

![聊天界面](https://via.placeholder.com/600x400?text=SSE+Chat+UI)

### 8.2 核心 JS 代码

```javascript
// 创建 EventSource 连接
const eventSource = new EventSource(`/chat/stream?session=${sessionId}&msg=${msg}`);

eventSource.onmessage = (event) => {
    // 每收到一个 chunk，追加到消息气泡
    textContent.textContent += event.data;
    chatBox.scrollTop = chatBox.scrollHeight;
};

eventSource.onerror = () => {
    eventSource.close();  // 连接结束
};
```

### 8.3 打字机效果

```
收到 data: "正"  → 界面显示 "正"
收到 data: "在"  → 界面显示 "正在"
收到 data: "生"  → 界面显示 "正在生"
收到 data: "成"  → 界面显示 "正在生成"
```

前端只需要做一件事：**收到什么就追加显示什么**

---

## 9. Python 对照实现

### 9.1 多轮对话记忆

```python
class ChatSession:
    """多轮对话会话 — 对应 Java ChatService"""
    
    def __init__(self):
        self.messages = []
    
    def chat(self, message: str) -> str:
        """同步对话，自动保存历史"""
        self.messages.append({"role": "user", "content": message})
        
        payload = {
            "model": "deepseek-chat",
            "messages": self.messages,  # 发送完整历史！
            "temperature": 0.7
        }
        resp = requests.post(URL, headers=HEADERS, json=payload)
        reply = resp.json()["choices"][0]["message"]["content"]
        
        self.messages.append({"role": "assistant", "content": reply})
        return reply
```

### 9.2 流式输出

```python
def chat_stream(self, message: str):
    """流式对话 — 逐块输出（打字机效果）"""
    self.messages.append({"role": "user", "content": message})
    
    payload = {
        "model": "deepseek-chat",
        "messages": self.messages,
        "stream": True  # 启用流式
    }
    
    collected = []
    resp = requests.post(URL, headers=HEADERS, json=payload, stream=True)
    
    for line in resp.iter_lines():
        if line:
            line = line.decode('utf-8')
            if line.startswith('data: '):
                data = line[6:]
                if data == '[DONE]':
                    break
                chunk = json.loads(data)['choices'][0]['delta'].get('content', '')
                collected.append(chunk)
                yield chunk  # 逐块返回
    
    # 保存完整回复
    self.messages.append({"role": "assistant", "content": "".join(collected)})
```

### Java vs Python 对比

| 维度 | Java（Spring AI + SseEmitter） | Python |
|------|-------------------------------|--------|
| **流式能力** | `Flux<String>` + `SseEmitter` | `stream=True` + `iter_lines()` |
| **SSE 推送** | 内置 `SseEmitter` | 需手动构建 HTTP 流 |
| **并发安全** | `ConcurrentHashMap` ✅ | 需自己加锁 |
| **前端支持** | `chat.html` 开箱即用 | 需额外开发 |

---

## 10. 今日小结

### 你学到了什么

| 知识点 | 掌握度 |
|--------|:------:|
| 为什么需要对话记忆 | ⭐⭐⭐ |
| 历史拼接实现原理 | ⭐⭐⭐ |
| 会话管理与隔离 | ⭐⭐⭐ |
| 为什么需要流式输出 | ⭐⭐⭐ |
| SSE 原理与格式 | ⭐⭐⭐ |
| SseEmitter 实现流式推送 | ⭐⭐⭐ |
| 前端打字机效果 | ⭐⭐ |

### 三种能力的融合

```
对话记忆（知道之前说了什么）
    │
    ▼
流式输出（逐字告诉用户答案）
    │
    ▼
SSE 推送（打字机效果）

→ 一个完整的 AI 对话体验
```

### 测试结果

```bash
# 多轮记忆
第1次：我叫小明       → "你好小明！"
第2次：我叫什么名字？ → "你叫小明！"      ✅

# 流式输出（SSE）
curl -N "/chat/stream?session=test&msg=你好"
→ data:你 → data:好 → data:！  ✅ 逐字出现

# 会话隔离
会话A（张三） ≠ 会话B（李四）  ✅ 各自独立
```

### 金句

> **记忆让 AI 不再是"每次都是第一次见面"**
> **流式输出让用户不再盯着空白屏幕等待**
> 
> **今天你做出了一个"真正能用"的 AI 聊天服务**

### 课后思考

1. 如果同时有 10000 个用户，`ConcurrentHashMap` 会不会内存溢出？该怎么优化？
2. `SseEmitter` 的连接如果断了，用户需要重新发消息吗？怎么实现断线重连？
3. 历史裁剪的 `MAX_HISTORY_ROUNDS` 设多大最合适？设太小会怎样？设太大会怎样？

---

> **明天预告：** Day 19 — 工具调用 / Function Calling（Java 版）
> 让 AI 能调用你的 Java 方法——查询数据库、发邮件、算数学 🎯
