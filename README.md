# AI 学习课程 🎓

从零开始构建 AI 实战能力的完整课程体系，覆盖提示词工程、RAG、多Agent协作、知识库问答系统等核心主题。

## 📚 课程结构

### 01-基础篇 (Day 1-7)
| 天数 | 主题 | 知识点 |
|------|------|--------|
| Day 1 | 大模型机制入门 | Token、上下文窗口、模型选择 |
| Day 2 | 提示词工程基础 | Zero-shot / Few-shot / CoT |
| Day 3 | 高级提示词策略 | ToT、Self-Consistency、结构化提示 |
| Day 4 | API 基础对接 | OpenAI 兼容接口、认证、错误处理 |
| Day 5 | 核心参数调优 | Temperature/Top-P/Max Tokens/Stop |
| Day 6 | 角色设定与模板 | System Prompt、模板工程 |
| Day 7 | 阶段复盘 | 全景回顾、知识图谱、期中项目 |

### 02-进阶能力 (Day 8-12)
| 天数 | 主题 | 知识点 |
|------|------|--------|
| Day 8 | RAG 入门与实战 | 文档加载、分块、向量检索、检索增强生成 |
| Day 9 | 工具调用 | Function Calling、插件系统 |
| Day 10 | 结构化输出 | JSON Mode、Pydantic 输出解析 |
| Day 11 | 多轮对话与状态管理 | 对话记忆、上下文管理、Session |
| Day 12 | 项目实战 | 完整 AI 客服系统 |

### 03-应用实战 (Day 13+)
| 天数 | 主题 | 知识点 |
|------|------|--------|
| Day 13 | AI 搜索增强助手 | 搜索 + RAG + 多工具编排 |
| Day 14 | 多Agent协作系统 | Orchestrator-Worker、Pipeline、Debate 模式 |
| Day 15 | 个人知识库问答系统 | Embedding + 向量检索 + LLM 生成（本项目） |

## 💻 代码

每个 Day 包含 **Python** 和 **Java** 双版本实现：

- **Python**: 完整可运行代码，依赖见 `requirements.txt`
- **Java**: Maven 项目，`mvn package` 构建

## 🚀 快速开始

```bash
# Python 知识库问答系统
cd 03-应用实战/code/day15
pip install -r requirements.txt
export DEEPSEEK_API_KEY=your_key
python3 -m knowledge_base.main build
python3 -m knowledge_base.main qa

# Java 版本
cd java
mvn package
java -jar target/knowledge-base-qa-1.0-SNAPSHOT.jar
```

## 🛠️ 技术栈

- **LLM API**: OpenAI 兼容接口 (DeepSeek)
- **嵌入模型**: sentence-transformers (all-MiniLM-L6-v2)
- **向量检索**: NumPy 余弦相似度
- **Java**: Java 21+, Maven, OkHttp

## 📄 许可证

MIT
