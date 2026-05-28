# 进阶 Day 1：RAG 评估体系 ⚖️

> 2026-05-28 · 进阶计划第一天

---

## 今日成果

目标：让你的 RAG 从"我觉得还行"变成"有数据证明"

### ✅ 完成内容

| 项目 | 说明 |
|:-----|:------|
| **评估数据集** | 基于知识库 7 篇文档构建 20 条 QA 对，覆盖 7 个分类 |
| **4 项自动指标** | Faithfulness / Relevancy / Precision / Recall |
| **LLM-as-Judge** | DeepSeek 综合评分（1-5分，含忠实/完整/相关/简洁四维度） |
| **评估报告** | Markdown + JSON 双格式输出 |

### 评估结果

| 指标 | 分数 | 说明 |
|:-----|:----:|:-----|
| Faithfulness (忠实度) | **0.9250** | 回答忠实于上下文 |
| Context Precision (精确度) | **0.9000** | 检索的上下文精准 |
| Context Recall (召回率) | **0.9008** | 检索覆盖了所需信息 |
| LLM-as-Judge | **4.60/5** | DeepSeek 综合评分 |

### LLM 评估细节（前 5 条）

| # | 问题 | 评分 | 理由 |
|:-:|:-----|:----:|:-----|
| 1 | 我想退货，多久之内可以退？ | **5/5** | 完全忠实，完整覆盖 |
| 2 | 退货后什么时候收到退款？ | **4/5** | 遗漏了"退款包含原价和税费" |
| 3 | 食品可以退货吗？ | **5/5** | 完全忠实，完整覆盖 |
| 4 | 退款金额包括哪些？ | **5/5** | 准确覆盖 |
| 5 | 普通配送要多久到？ | **4/5** | 遗漏了当日达信息 |

### 📁 产出文件

```
阶段9-职业冲刺/code/day45-进阶/评估体系/
├── data/
│   └── eval_dataset.json              # 评估数据集（20条）
├── scripts/
│   ├── build_eval_dataset.py          # 数据集构建器
│   └── rag_evaluation.py              # 评估主程序（4指标 + LLM-as-Judge）
└── report/
    ├── evaluation_report.json         # 完整评估数据
    └── evaluation_report.md           # Markdown 评估报告
```

---

## 技术要点

### 4 项核心指标

| 指标 | 衡量内容 | 实现方式 |
|:-----|:---------|:---------|
| **Faithfulness** | 回答是否忠实于上下文（有没有编造） | 句子拆解 → 3-gram 片段匹配 → 覆盖率 |
| **Answer Relevancy** | 回答是否与问题相关 | TF-IDF 字符 n-gram 余弦相似度 |
| **Context Precision** | 检索的上下文是否精准（噪声少） | 问题与各 context 的语义相似度 |
| **Context Recall** | 检索是否覆盖了回答所需信息 | Ground Truth 信息点在 context 中的覆盖率 |

### LLM-as-Judge

用 DeepSeek 从 4 个维度打分（1-5），每个维度独立评分：

```
忠实度 (Faithfulness)  — 是否编造了上下文没有的信息
完整性 (Completeness)  — 是否回答了用户的所有需求
相关性 (Relevance)    — 是否直接回应问题
简洁性 (Conciseness)  — 是否有冗余信息
```

---

## 明日预告 Day 2

> **LLM-as-Judge 深度实践 + BERTScore**
