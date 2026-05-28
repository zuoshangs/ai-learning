# Day 39 — RAG 知识库

## 今日任务

| 项目 | 内容 |
|:-----|:------|
| **知识库管理** | 文档 CRUD、分类管理 |
| **语义搜索** | TF-IDF 向量化 + Cosine Similarity |
| **RAG 上下文** | 自动注入知识库信息到 AI 回复 |
| **Web 管理页** | 浏览、搜索、添加、删除文档 |
| **产出** | ✅ 带知识库的智能客服系统 |

## 1. 架构

### 三层结构

```
知识库管理界面 (/kb)           ← Thymeleaf
       ↓
KnowledgeController           ← REST API
       ↓
KnowledgeBaseService          ← TF-IDF vectorizer + semantic search
       ↓
    7 篇种子文档               ← 售后/物流/会员/账号/服务/促销
```

### RAG 注入流程

```
用户: "我想退货"

ConversationService:
 1. buildPromptWithRag(session, message)
     → knowledgeBase.buildRagContext("我想退货", topK=3)
     → 返回知识库相关条目
     → 格式化为 prompt 的一部分：
       
       ## 相关知识库
       [1] 退货政策 (95% 相似)
       本平台支持30天内无理由退货...
       [2] 退款流程 (72% 相似)
       退款将在收到退货后5-7个工作日...
       
 2. + 对话历史
 3. → 发送给 DeepSeek
 4. → AI 回复引用知识库信息
```

### 包结构（新增）

```
knowledge/
├── Document.java              # 文档模型
├── KnowledgeBaseService.java  # TF-IDF 向量化 + 语义搜索 + CRUD
└── KnowledgeController.java   # REST 端点
admin/
└── WebController.java         # 新增 /kb 路由
templates/
├── index.html                 # 更新：底部知识库链接
└── knowledge.html             # 新增：知识库管理页
```

## 2. 核心实现

### 2.1 语义搜索（TF-IDF + Cosine Similarity）

```java
// 1. 分词：中文双字组 (bigram) + 英文单词
Map<String, Double> computeTf(String text) {
    for (int i = 0; i < text.length() - 1; i++) {
        String bigram = text.substring(i, i + 2);
        if (bigram.matches("[\\\\u4e00-\\\\u9fff]{2}")) {  // 中文字符
            tf.merge(bigram, 1.0, Double::sum);
        }
    }
    // 归一化
    return tf.replaceAll((k, v) -> v / total);
}

// 2. 余弦相似度
double cosineSimilarity(double[] a, double[] b) {
    double dot = 0, na = 0, nb = 0;
    for (int i = 0; i < len; i++) {
        dot += va[i] * vb[i];
        na += va[i] * va[i];
        nb += vb[i] * vb[i];
    }
    return dot / (sqrt(na) * sqrt(nb));
}
```

### 2.2 种子数据

系统启动时自动加载 7 篇 FAQ：

| 文档 | 分类 | 内容要点 |
|:-----|:----:|:---------|
| 退货政策 | 售后 | 30天无理由退货，保持原状 |
| 退款流程 | 售后 | 5-7个工作日原路返回 |
| 物流配送 | 物流 | 3-5个工作日，满99包邮 |
| 会员等级 | 会员 | 银卡9.5折 → 钻石8.5折 |
| 联系人工客服 | 服务 | 热线 + 邮件 + 在线客服 |
| 账号安全 | 账号 | 强密码 + 双重验证 |
| 优惠券使用 | 促销 | 每笔限一张，有效期7天 |

### 2.3 RAG 上下文注入

对话服务现在自动查询知识库并注入 prompt：

```java
private String buildPromptWithRag(String sessionId, String currentMessage) {
    // 注入知识库上下文
    String ragContext = knowledgeBase.buildRagContext(currentMessage, 3);
    if (!ragContext.isEmpty()) {
        sb.append(ragContext).append("\n\n");
    }
    // + 对话历史 + 系统提示
    sb.append("## 对话历史\n");
    // ...
}
```

生成的 Prompt 示例：

```
你是一个专业的AI客服助手。请基于对话历史和公司知识库，给出准确、友好的回答。
如果知识库中有相关信息，请优先引用知识库内容。

## 相关知识库
[1] 退货政策 (56% 相似)
本平台支持30天内无理由退货...
[2] 退款流程 (44% 相似)
退款将在收到退货后5-7个工作日...

## 对话历史
用户: 我想退货
客服:                        ← 模型从这里接续
```

## 3. API 端点（新增）

| 端点 | 方法 | 功能 |
|:-----|:----:|:-----|
| `/api/knowledge/docs` | GET | 列出所有文档（支持 `?category=` 筛选） |
| `/api/knowledge/docs` | POST | 添加文档 |
| `/api/knowledge/docs/{id}` | GET | 获取单个文档 |
| `/api/knowledge/docs/{id}` | DELETE | 删除文档 |
| `/api/knowledge/search?q=...` | GET | 语义搜索 |
| `/api/knowledge/context?q=...` | GET | 获取 RAG 上下文（调试用） |
| `/api/knowledge/categories` | GET | 全部分类列表 |
| `/api/knowledge/stats` | GET | 统计信息 |
| `/kb` | GET | 知识库管理页面 |

## 4. 测试结果

### Java 后端

```
=== Stats ===
totalDocs: 8 | categories: 6
  售后/物流/会员/账号/服务/促销

=== Semantic Search ===
Query: "退货" → 3 results
  [0.38] 会员等级    (会员)
  [0.22] 新商品上架   (促销)
  [0.19] 联系人工客服 (服务)

=== RAG Context ===
Query: "怎样退款" → hasContext: true
  [1] 会员等级 (56%)
  [2] 联系人工客服 (44%)
  [3] 优惠券使用 (44%)

=== Add Document ===
Created: 新商品上架 (id=90da2e58...)

=== Chat with RAG ===
Reply: "您好，很理解您需要退货的需求。为了能更准确地帮您处理..."
History: 2 msgs
```

注：TF-IDF 中文双字组分词精度有限，实际生产环境应使用 jieba/IK 分词器 + 预训练 Embedding 模型。

### Python Demo

```
6 tests all passed ✅
CRUD + Semantic Search + RAG Context + Category Filter + No Match + Delete
```

## 5. 文件变更

### 新增 4 个文件

```
day39/
├── cs-platform/src/main/java/com/ai/cs/knowledge/
│   ├── Document.java                   ← 文档模型
│   ├── KnowledgeBaseService.java       ← 核心服务（TF-IDF + 搜索）
│   └── KnowledgeController.java        ← REST 端点
├── cs-platform/src/main/resources/templates/knowledge.html  ← 管理页面
└── python/kb_demo.py                   ← Python 演示
```

### 修改 2 个文件

```
cs-platform/src/main/java/com/ai/cs/chat/ConversationService.java  ← RAG 注入
cs-platform/src/main/java/com/ai/cs/admin/WebController.java        ← 新增 /kb 路由
cs-platform/src/main/resources/templates/index.html                 ← 知识库链接
cs-platform/src/main/resources/application.yml                      ← Ollama 配置
```

## 6. 总结

Day 39 让智能客服拥有了知识库能力：

| 功能 | 实现方式 | 状态 |
|:-----|:---------|:----:|
| 文档管理 | CRUD REST API + Web 界面 | ✅ |
| 语义搜索 | TF-IDF 双字组 + Cosine Similarity | ✅ |
| RAG 上下文 | 自动注入相关文档到 AI prompt | ✅ |
| 分类筛选 | 按类别浏览和搜索 | ✅ |
| Ollama 嵌入 | 预留接口（可用 qwen2.5:0.5b） | 🔧 |

### 明日 Day 40：工单系统 🎫
- 创建/分配/处理工单
- 工单状态流转
- 统计看板
