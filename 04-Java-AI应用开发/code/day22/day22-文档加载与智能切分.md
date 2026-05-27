# Day 22：文档加载与智能切分（Java 版）

> **日期：** 2026-05-27
> **目标：** 从 Demo RAG 到生产级文档处理管线
> **技术栈：** Spring AI 1.0.0-M6, Tika, PDFBox, Java 21, Maven 3.8.7

---

## 一、为什么需要独立的文档处理管线？

Day 20 我们用 `TextReader` + `TokenTextSplitter` 构建了端到端 RAG。但生产环境的需求远不止于此：

| 需求 | Day 20 能做 | Day 22 改进 |
|:----|:-----------|:-----------|
| 多格式支持 | ❌ 仅 `.txt` | ✅ TXT / PDF / DOCX / MD / JSON |
| 多种切分策略 | ❌ 仅 TokenTextSplitter | ✅ Token / 段落 / 递归字符 |
| 策略对比 | ❌ 无法对比 | ✅ 同一文档多策略并行输出 |
| 元数据提取 | ❌ 无 | ✅ 行数/字数/PDF页数/文件信息 |
| Token 估算 | ❌ 无 | ✅ 中英文混合 Token 估算 |

**文档处理管线 = RAG 的"前端"**。如果文档读不好、切得不合理，后面的向量检索和生成再强也没用。

---

## 二、项目结构

```
code/day22/
├── pom.xml                                          # Maven 依赖
├── doc_pipeline_demo.py                              # Python 对照版
├── src/
│   ├── main/java/com/ai/learning/doc/
│   │   ├── DocApplication.java                      # 启动类
│   │   ├── config/
│   │   │   └── (无需额外配置 — Spring Boot AutoConfig)
│   │   ├── model/
│   │   │   ├── DocumentInfo.java                    # 文档处理结果
│   │   │   └── ChunkStrategyResult.java             # 切分策略结果
│   │   ├── strategy/
│   │   │   ├── ChunkStrategy.java                   # 策略接口
│   │   │   ├── TokenChunkStrategy.java              # Token 切分
│   │   │   ├── ParagraphChunkStrategy.java          # 段落语义切分
│   │   │   └── RecursiveCharacterChunkStrategy.java # 递归字符切分
│   │   ├── service/
│   │   │   └── DocumentProcessingService.java       # 文档处理服务
│   │   └── controller/
│   │       └── DocProcessingController.java         # REST API
│   ├── main/resources/
│   │   ├── application.yml
│   │   └── documents/
│   │       ├── spring-ai-intro.md                   # Markdown 测试
│   │       ├── spring-ai-overview.txt               # TXT 测试
│   │       └── spring-ai-config.json                # JSON 测试
│   └── test/java/com/ai/learning/doc/
│       └── ChunkStrategyTest.java                   # 10 个单元测试
```

---

## 三、三种切分策略的实现

### 3.1 策略接口

所有策略遵循同一个接口，方便注入和扩展：

```java
public interface ChunkStrategy {
    String getName();                // 策略名称
    ChunkStrategyResult chunk(String text);  // 执行切分
}
```

通过 `@Component` 注册 + `List<ChunkStrategy>` 注入，新增策略只需实现接口 + 加 `@Component`，无需改任何其他代码。

### 3.2 TokenTextSplitter（Spring AI 内置）

Spring AI 的 `TokenTextSplitter` 使用 **jtokkit** 库（OpenAI tiktoken 的 Java 移植）进行真正的 Token 计数切分。

```java
TokenTextSplitter splitter = new TokenTextSplitter(
    500,     // defaultChunkSize: 目标 Token 数
    100,     // minChunkSizeChars: 最小块字符数
    50,      // minChunkLengthToEmbed: 需要嵌入的最小长度
    10000,   // maxNumChunks: 最大块数
    false    // keepSeparator: 是否保留分隔符
);
List<Document> docs = splitter.apply(List.of(new Document(text)));
```

**特点：**
- 真正的 Token 感知（中英文混合场景更准确）
- 内置最小块过滤（太短的块自动合并或丢弃）
- 支持 Blocking 模式（保持段落完整）

### 3.3 ParagraphSplitter（语义切分）

按段落（空行分隔）切分，保留语义完整性：

```
原始文本流:  [段1] \n\n [段2] \n\n [段3] \n\n [段4]
                              ↓
ParagraphSplitter:  [段1 + 段2]  |  [段3]  |  [段4]
```

**核心逻辑：**
1. 按 `\n\n` 拆分成段落
2. 过长段落（> maxChars）按句子（。！？）拆
3. 过短段落合并到上一个块
4. **0 重叠** — 段落天然不重叠

**适用场景：** Markdown 文档、结构化文章、法律文件

### 3.4 RecursiveCharacterSplitter（LangChain 式）

模拟 LangChain 的 `RecursiveCharacterTextSplitter`，按分隔符优先级递归切分：

```
分隔符优先级: \n\n > \n > 。 > . > ， > , > 空格 > 字符
                    
按 \n\n 切 → 过大的块按 \n 切 → 还过大按 。切 → ...直到全部达标
```

**核心逻辑（递归）：**
1. 用当前分隔符拆分子片段
2. 合并到目标块大小（chunkSize）
3. 如果仍有块 > chunkSize，用下一个分隔符递归拆分
4. 保留重叠区域（overlapChars）保持上下文连续性

**适用场景：** 混合格式文档、代码文档、通用场景

---

## 四、文档读取支持

| 格式 | 读取方式 | 特点 |
|:----|---------|:----|
| `.txt` | `TextReader` (Spring AI) | 最简，UTF-8 |
| `.md` | `TextReader` (Spring AI) | 同 TXT，但保留 Markdown 结构 |
| `.json` | `TextReader` (Spring AI) | 作为纯文本读取 |
| `.pdf` | `PDFBox` | 提取文本 + 页数元数据 |
| `.docx` / `.doc` | `TikaDocumentReader` (Spring AI) | Apache Tika 解析 |
| `.html` / `.xml` | `TikaDocumentReader` | 自动提取可见文本 |

```java
// PDF 读取 — 使用 PDFBox
try (PDDocument pdfDoc = Loader.loadPDF(path.toFile())) {
    PDFTextStripper stripper = new PDFTextStripper();
    return stripper.getText(pdfDoc);
}

// DOCX 读取 — 使用 Spring AI Tika
TikaDocumentReader reader = new TikaDocumentReader(resource);
List<Document> docs = reader.get();
```

---

## 五、API 端点

| 方法 | 路径 | 功能 | 请求示例 |
|:----|:----|:----|:--------|
| `GET` | `/doc/strategies` | 列出可用策略 | — |
| `POST` | `/doc/process` | 处理单个文件 | `{"filePath": "/xxx/sample.md"}` |
| `POST` | `/doc/process/dir` | 批量处理目录 | `{"dirPath": "/xxx/documents/"}` |

**返回结构（单文件）：**
```json
{
  "filename": "spring-ai-intro.md",
  "fileType": "md",
  "fileSizeBytes": 2794,
  "totalCharacters": 1982,
  "totalTokens": 577,
  "metadata": { "lineCount": 99, "wordCount": 269 },
  "strategies": [
    {
      "strategyName": "ParagraphSplitter (语义)",
      "chunkCount": 2,
      "avgChunkSize": 990,
      "maxChunkSize": 1239,
      "samples": [
        { "index": 0, "length": 1239, "preview": "# Spring AI 框架介绍..." },
        { "index": 1, "length": 741, "preview": "## 与 LangChain 的对比..." }
      ]
    }
  ]
}
```

---

## 六、实测对比

在 `spring-ai-intro.md`（1982 字符）上三种策略的表现：

| 策略 | 块数 | 平均大小 | 最大块 | 特点 |
|:----|:---:|:--------:|:-----:|:----|
| **TokenSplitter (500t)** | 2 | 977c | 1087c | 最均匀，但可能断句子 |
| **ParagraphSplitter (语义)** | 2 | 990c | 1239c | 保留完整段落，无重叠 |
| **RecursiveSplitter (1000c)** | 3 | 707c | 834c | 兼顾语义与大小，有重叠 |

在纯文本 `spring-ai-overview.txt`（1097 字符）上：

| 策略 | 块数 | 平均大小 | 特点 |
|:----|:---:|:--------:|:----|
| **TokenSplitter (500t)** | 3 | 365c | 最细粒度 |
| **ParagraphSplitter (语义)** | 1 | 1097c | 无换行段落 → 整体 |
| **RecursiveSplitter (1000c)** | 2 | 548c | 居中 |

**结论：**
- **需要语义完整 → ParagraphSplitter**（法律文档、Markdown）
- **需要均匀块大小 → TokenSplitter**（通用 RAG）
- **需要兼顾两者 → RecursiveSplitter**（混合格式）

---

## 七、运行方式

```bash
# 1. 编译
cd code/day22 && mvn clean compile

# 2. 测试
mvn test

# 3. 启动服务（需 DEEPSEEK_API_KEY 环境变量）
export DEEPSEEK_API_KEY=sk-xxx
mvn spring-boot:run

# 4. 测试 API
curl http://localhost:8080/doc/strategies

# 处理文件
curl -X POST http://localhost:8080/doc/process \
  -H "Content-Type: application/json" \
  -d '{"filePath": "/path/to/sample.md"}'

# 批量处理
curl -X POST http://localhost:8080/doc/process/dir \
  -H "Content-Type: application/json" \
  -d '{"dirPath": "/path/to/documents/"}'
```

---

## 八、遇到的坑

### 1. TokenTextSplitter 构造函数签名
Spring AI 1.0.0-M6 的 `TokenTextSplitter` 只有三种构造函数：
```java
new TokenTextSplitter();                          // 默认
new TokenTextSplitter(boolean keepSeparator);     // 仅分隔符
new TokenTextSplitter(int,int,int,int,boolean);   // 完整参数
```
没有"重叠 tokens"参数 — 重叠通过 `maxNumChunks` 间接控制。

### 2. splitText 是 protected
`TokenTextSplitter.splitText()` 是 `protected` 方法，不能外部调用。必须用 `apply()` 接收 `List<Document>`。

### 3. 换行符导致段落数偏差
中文文档中 `\n\n` 分段不一定准确（中文自然分段可能只有一个换行）。`ParagraphSplitter` 中用 `\n\s*\n` 正则更鲁棒。

### 4. YAML multi-document 警告
Spring Boot 的 `---` 分隔符会被 Enonic XP 等非 Spring 的 YAML 校验器误报。多 profile 场景下不影响运行，但单文件建议去掉。

---

## 九、从 Day 20 到 Day 22 的升级路径

```
Day 20（Demo RAG）
├── TextReader（仅 txt）
├── TokenTextSplitter（默认参数）
└── 直接入库

         ↓ 升级 ↓

Day 22（生产级文档管线）
├── TextReader + Tika + PDFBox（多格式）
├── TokenSplitter + ParagraphSplitter + RecursiveSplitter（三策略可选）
├── 元数据提取（行数/字数/页数）
├── Token 估算
└── 策略对比分析
```

**Day 23 预告：** 将今天切分好的文档块 → 向量化 → 入库 PgVector → 实现可配置的相似度检索服务。

---

## 代码清单

| 文件 | 行数 | 说明 |
|:----|:---:|:----|
| `pom.xml` | 99 | Maven 依赖 |
| `DocApplication.java` | 15 | Spring Boot 启动类 |
| `ChunkStrategy.java` | 18 | 策略接口 |
| `TokenChunkStrategy.java` | 150 | Token 切分 |
| `ParagraphChunkStrategy.java` | 118 | 段落语义切分 |
| `RecursiveCharacterChunkStrategy.java` | 170 | 递归字符切分 |
| `DocumentProcessingService.java` | 210 | 文档处理核心服务 |
| `DocProcessingController.java` | 72 | REST API |
| `DocumentInfo.java` | 62 | 文档信息模型 |
| `ChunkStrategyResult.java` | 80 | 策略结果模型 |
| `ChunkStrategyTest.java` | 133 | 10 个单元测试 |
| `doc_pipeline_demo.py` | 240 | Python 对照版 |
