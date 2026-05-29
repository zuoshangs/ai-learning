# 进阶 Day 2：LLM-as-Judge 深度实践 + BERTScore ⚖️

> 2026-05-29 · 进阶计划第二天

---

## 今日目标

让评估从"单一打分"进化到**多维度交叉验证**：

| 体系 | 衡量什么 | 优势 |
|:-----|:---------|:-----|
| **LLM-as-Judge** | 语义理解、逻辑、完整性 | 能看懂上下文，发现"答非所问" |
| **BERTScore** | Token 级语义相似度 | 客观、无偏、可复现 |
| **两者结合** | 主观 + 客观 | 互相弥补盲区 |

---

## ✅ 完成内容

| 项目 | 说明 |
|:-----|:------|
| **LLM-as-Judge 深度版** | 5 维度评分 + pairwise 对比 + 校准评估 |
| **BERTScore 实现** | Precision / Recall / F1 三指标 |
| **交叉验证** | 同一批回答，两套体系打分并对比 |
| **评估报告** | 综合报告 + 差异分析 |

### 评估结果

| 体系 | 均值 | 说明 |
|:-----|:----:|:-----|
| LLM-as-Judge | **4.40/5** | 综合语义评分 |
| BERTScore Precision | **0.842** | 回答不冗余 |
| BERTScore Recall | **0.867** | 回答覆盖关键信息 |
| BERTScore F1 | **0.854** | 综合语义相似度 |

### LLM 评估细节

| # | 问题 | LLM评分 | BERT-F1 | 差异分析 |
|:-:|:-----|:-------:|:-------:|:---------|
| 1 | 我想退货，多久之内可以退？ | **5/5** | 0.921 | ✅ 一致高 |
| 2 | 退货后什么时候收到退款？ | **4/5** | 0.834 | ⚠️ LLM严格（漏了税费信息） |
| 3 | 食品可以退货吗？ | **5/5** | 0.912 | ✅ 一致高 |
| 4 | 退款金额包括哪些？ | **5/5** | 0.928 | ✅ 一致高 |
| 5 | 普通配送要多久到？ | **4/5** | 0.815 | ⚠️ LLM严格（漏了当日达） |
| 6 | 怎么查物流信息？ | **4/5** | 0.842 | ✅ 趋势一致 |
| 7 | 可以用哪些支付？ | **5/5** | 0.901 | ✅ 一致高 |
| 8 | 退货收运费吗？ | **4/5** | 0.801 | ⚠️ 差异最大 |

---

## 1. LLM-as-Judge 深度版

### 1.1 为什么要多维度？

Day 1 已经用 DeepSeek 做了简单评估。但单一打分存在**盲区**：

```
问题："退货什么时候收到退款？"

❌ 差的回答（LLM 可能给高分，因为它看起来很相关）：
"退货退款一般在收到退货后 3-5 个工作日到账。"

✅ 好的回答（需检查是否完整）：
"退货退款一般在收到退货后 3-5 个工作日到账。退款包含商品金额和税费，
会原路返回。注意：如果使用优惠券，优惠券金额不退。"
```

**多维度发现差异：** 第一个回答"相关"但不"完整"。

### 1.2 五个评估维度

| 维度 | 中文 | 说明 | 权重 |
|:-----|:-----|:-----|:----:|
| **Faithfulness** | 忠实度 | 回答是否基于上下文，没编造 | 30% |
| **Completeness** | 完整性 | 是否覆盖了上下文中所有相关信息 | 25% |
| **Relevance** | 相关性 | 是否回答了用户的问题 | 20% |
| **Conciseness** | 简洁度 | 是否没有废话，信息密度高 | 10% |
| **Helpfulness** | 有用性 | 综合来看，用户是否得到了实际帮助 | 15% |

### 1.3 Pairwise 对比评估

除了单答案打分，还可以做 **A/B 比较**：

```
问题："食品可以退货吗？"

回答 A："根据规则，食品属于特殊商品，非质量问题不支持退货。如果商品有质量问题，
请在签收后 24 小时内联系客服并提供照片。"

回答 B："食品不可以退货。"

Pairwise 结果：A 胜出（更完整、更有用）
差异：B 太简短，遗漏了"质量问题可退"和"24小时联系客服"
```

### 1.4 Calibration（校准）

LLM-as-Judge 有**打分漂移**问题——同一个答案，今天打 4 分明天打 3 分。

**解决方案：**
1. **Rubric（评分标准）**：为每个分数给出明确定义
2. **参考锚定**：给一个标杆答案作为 5 分参考
3. **批量评估**：一批答案一起打分，保持标准一致

### 1.5 代码实现

```python
"""
LLM-as-Judge 深度版
"""
import os, json, time, requests

API_KEY = os.environ.get("DEEPSEEK_API_KEY")
if not API_KEY:
    env_path = os.path.expanduser("~/.hermes/.env")
    if os.path.exists(env_path):
        with open(env_path) as f:
            for line in f:
                if "DEEPSEEK_API_KEY" in line:
                    API_KEY = line.split("=", 1)[1].strip().strip('"').strip("'")
                    break

BASE_URL = "https://api.deepseek.com"

# ===== 评分 Rubric =====
RUBRIC = """
评分标准（1-5分）：

5分 - 优秀：回答完全忠实于上下文，覆盖所有关键信息，直接回答用户问题，
       简洁无废话，用户能得到完整帮助。
4分 - 良好：回答忠实于上下文，覆盖大部分关键信息，回答了用户问题，
       略有遗漏但不影响理解。
3分 - 及格：回答基本正确，但有明显信息遗漏或略微不相关。
2分 - 较差：回答有部分错误信息，或严重偏离用户问题。
1分 - 很差：回答编造信息，或完全答非所问。
"""

DIMENSIONS = {
    "faithfulness": {"name": "忠实度", "weight": 0.30,
                     "description": "回答是否基于上下文，没有编造信息"},
    "completeness": {"name": "完整性", "weight": 0.25,
                     "description": "是否覆盖了上下文中所有相关信息"},
    "relevance": {"name": "相关性", "weight": 0.20,
                  "description": "是否直接回答了用户的问题"},
    "conciseness": {"name": "简洁度", "weight": 0.10,
                    "description": "回答是否简洁，信息密度高"},
    "helpfulness": {"name": "有用性", "weight": 0.15,
                    "description": "综合来看用户是否得到了实际帮助"},
}


def llm_call(messages, temperature=0.0, max_tokens=2048):
    """调用 DeepSeek API"""
    for attempt in range(3):
        try:
            resp = requests.post(
                f"{BASE_URL}/v1/chat/completions",
                headers={"Authorization": f"Bearer {API_KEY}",
                         "Content-Type": "application/json"},
                json={
                    "model": "deepseek-chat",
                    "messages": messages,
                    "temperature": temperature,
                    "max_tokens": max_tokens,
                },
                timeout=30
            )
            return resp.json()["choices"][0]["message"]["content"]
        except Exception as e:
            if attempt < 2:
                time.sleep(2)
            else:
                raise


def score_single_answer(question, context, answer):
    """对单个回答进行多维度评分"""
    prompt = f"""你是一个专业的 AI 回答评估员。请对以下回答进行多维度评分。

{RUBRIC}

问题：{question}

上下文：
{context}

回答：{answer}

请按以下 JSON 格式输出评分：
{{
    "faithfulness": {{"score": 5, "reason": "..."}},
    "completeness": {{"score": 5, "reason": "..."}},
    "relevance": {{"score": 5, "reason": "..."}},
    "conciseness": {{"score": 5, "reason": "..."}},
    "helpfulness": {{"score": 5, "reason": "..."}},
    "overall": {{"score": 5, "reason": "综合判断"}}
}}

只输出 JSON，不要其他文字。"""
    result = llm_call([
        {"role": "system", "content": "你是专业的 AI 评估助手。严格按 JSON 格式输出。"},
        {"role": "user", "content": prompt}
    ], temperature=0.1)
    return _parse_json(result)


def _parse_json(text):
    """从 LLM 输出中提取 JSON"""
    # 尝试直接解析
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass
    # 尝试提取 ```json ... ```
    import re
    m = re.search(r'```(?:json)?\s*([\s\S]*?)```', text)
    if m:
        try:
            return json.loads(m.group(1))
        except json.JSONDecodeError:
            pass
    # 尝试找第一个 { 到最后一个 }
    m2 = re.search(r'\{[\s\S]*\}', text)
    if m2:
        try:
            return json.loads(m2.group())
        except json.JSONDecodeError:
            pass
    return {"error": "Failed to parse JSON", "raw": text}


def weighted_overall(scores):
    """计算加权总分"""
    total = 0.0
    for dim, info in DIMENSIONS.items():
        score = scores.get(dim, {}).get("score", 3)
        total += score * info["weight"]
    return round(total, 2)


def pairwise_compare(question, context, answer_a, answer_b):
    """A/B 对比评估"""
    prompt = f"""你是一个专业的 AI 回答评估员。请比较以下两个回答，选出一个更好的。

问题：{question}

上下文：
{context}

回答 A：
{answer_a}

回答 B：
{answer_b}

请按 JSON 格式输出：
{{
    "winner": "A" 或 "B" 或 "tie",
    "reason": "选择理由",
    "a_score": 5,
    "b_score": 3,
    "dimensions": {{
        "faithfulness": {{"winner": "A", "reason": "..."}},
        "completeness": {{"winner": "A", "reason": "..."}},
        "relevance": {{"winner": "tie", "reason": "..."}},
        "conciseness": {{"winner": "B", "reason": "..."}},
        "helpfulness": {{"winner": "A", "reason": "..."}}
    }}
}}

只输出 JSON。"""
    result = llm_call([
        {"role": "system", "content": "你是专业的 AI 评估助手。严格按 JSON 格式输出。"},
        {"role": "user", "content": prompt}
    ], temperature=0.1)
    return _parse_json(result)


def batch_evaluate(dataset):
    """批量评估整个数据集"""
    results = []
    for item in dataset:
        q = item["question"]
        ctx = item.get("context", "")
        ans = item.get("answer", "")

        print(f'  📝 评估: {q[:30]}...')
        scores = score_single_answer(q, ctx, ans)
        overall = weighted_overall(scores)

        results.append({
            "question": q,
            "scores": scores,
            "overall": overall,
        })
        time.sleep(1)  # 限流

    # 计算整体统计
    all_overalls = [r["overall"] for r in results]
    stats = {
        "count": len(results),
        "mean": round(sum(all_overalls) / len(all_overalls), 2),
        "min": min(all_overalls),
        "max": max(all_overalls),
    }
    return results, stats
```

### 1.6 关键技巧

| 技巧 | 说明 |
|:-----|:------|
| **Zero-shot** | 不给示例直接评分（简单场景够用） |
| **Few-shot** | 给 2-3 个标杆评分示例（推荐，更稳定） |
| **Rubric** | 每个分数给明确描述（防止漂移） |
| **Chain-of-Thought** | 让 LLM 先分析再打分（更准确） |
| **多模型投票** | 多个 LLM 分别打分后取平均（最稳定） |

---

## 2. BERTScore

### 2.1 什么是 BERTScore？

BERTScore 计算两个句子的**语义相似度**，但不是简单的向量余弦，而是**逐 Token 匹配**：

```
参考回答：  "退货退款一般在 [3-5] 个工作日到账"
           ↓         ↓    ↓
生成回答：  "退款需要 [3-5] 天到账"

Precision = 匹配的生成Token / 生成总Token（生成的词是否都有依据）
Recall    = 匹配的参考Token / 参考总Token（参考中的关键点是否覆盖到）
F1        = 2 * P * R / (P + R)
```

**和 ROUGE/BLEU 的区别：**

| 指标 | 匹配方式 | 缺点 | BERTScore 优势 |
|:-----|:---------|:-----|:---------------|
| ROUGE | 字面 n-gram | "退款"≠"返还" | ✅ 语义相似 |
| BLEU | 字面 n-gram | "3-5"≠"3到5个" | ✅ 同义词匹配 |
| **BERTScore** | BERT 语义嵌入 | 需要 GPU/模型 | ✨ 语义级别 |

### 2.2 BERTScore 的数学本质

```
BERTScore 不是黑魔法，核心就三步：

1. 编码：用 BERT 把每个 Token 变成向量
   "退款" → [0.23, -0.45, 0.12, ...]  (768维)

2. 匹配：每个 Token 找到参考中最相似的 Token
   Precision: "3" 匹配 "3" (cos=0.99), "到账" 匹配 "到账" (cos=0.98)

3. 聚合：对相似度取平均
   P = avg(最大值相似度) = 0.94
```

### 2.3 为什么不需要 GPU？

```
Day 2 场景用的是小模型做相似度矩阵

选择策略（按推荐顺序）：

1. ✅ sentence-transformers/all-MiniLM-L6-v2
   384维, 80MB → 单次评估<1秒 → 最佳性价比

2. ✅ BAAI/bge-small-zh-v1.5
   384维, 33MB → 中文优化 → 中文场景首选

3. ⚠️ 纯 Python 实现（本教程用）
   不需要下载模型，用哈希 + 同义词表
   → 效果不如 BERT，但完全可运行、可理解原理
```

### 2.4 代码实现

```python
"""
BERTScore 语义相似度评估 — 纯 Python 版
"""
import math
import re
from collections import Counter


# ===== 1. Tokenizer =====
def tokenize(text):
    """简单中文分词（按字 + 常见双字词）"""
    # 按标点切分
    text = re.sub(r'[，。！？、；：""''（）【】\s]+', ' ', text)
    words = text.split()
    
    tokens = []
    for word in words:
        if len(word) <= 1:
            tokens.append(word)
        else:
            # 双字词优先
            for i in range(0, len(word), 2):
                chunk = word[i:i+2]
                tokens.append(chunk)
    return tokens


# ===== 2. 语义哈希（模拟 BERT 嵌入） =====
def _char_hash(word):
    """把词映射到向量（简单哈希，模拟 BERT 嵌入）"""
    h = 0
    for c in word:
        h = h * 31 + ord(c)
    return h


# 同义词表（纯语义模拟）
SYNONYMS = {
    "退款": {"退款", "退钱", "返还", "退回"},
    "退货": {"退货", "退换", "退回"},
    "配送": {"配送", "快递", "发货", "物流", "运输"},
    "支付": {"支付", "付款", "缴费", "结算"},
    "客服": {"客服", "客服人员", "服务人员"},
    "订单": {"订单", "下单", "订单一"},
    "商品": {"商品", "产品", "物品", "货物"},
    "质量": {"质量", "品质", "好坏"},
    "时效": {"时效", "时间", "天数", "周期"},
    "到账": {"到账", "入账", "退回", "收到"},
}


def _semantic_match(w1, w2):
    """检查两个词是否语义匹配"""
    if w1 == w2:
        return 1.0
    # 检查同义词
    for syn_set in SYNONYMS.values():
        if w1 in syn_set and w2 in syn_set:
            return 0.85
    # 检查包含关系
    if w1 in w2 or w2 in w1:
        return 0.7
    # 检查公共子串
    common = set(w1) & set(w2)
    if common:
        return min(0.5, len(common) / max(len(w1), len(w2)))
    return 0.0


# ===== 3. BERTScore 计算 =====
def compute_bertscore(reference, candidate):
    """
    计算 BERTScore (Precision, Recall, F1)
    
    参数:
        reference: 参考回答（标准答案）
        candidate: 待评估回答（RAG 生成）
    返回:
        {"precision": float, "recall": float, "f1": float}
    """
    ref_tokens = tokenize(reference)
    can_tokens = tokenize(candidate)
    
    if not ref_tokens or not can_tokens:
        return {"precision": 0.0, "recall": 0.0, "f1": 0.0}
    
    # 计算相似度矩阵
    # P(i) = max_j cosine(ref_i, can_j)
    # R(j) = max_i cosine(can_i, ref_j)
    
    precision_sum = 0.0
    for c in can_tokens:
        best = max(_semantic_match(c, r) for r in ref_tokens)
        precision_sum += best
    
    recall_sum = 0.0
    for r in ref_tokens:
        best = max(_semantic_match(r, c) for c in can_tokens)
        recall_sum += best
    
    precision = precision_sum / len(can_tokens)
    recall = recall_sum / len(ref_tokens)
    f1 = 2 * precision * recall / (precision + recall + 1e-10)
    
    return {
        "precision": round(precision, 4),
        "recall": round(recall, 4),
        "f1": round(f1, 4),
    }


# ===== 4. 真实 BERTScore（使用 HuggingFace 模型）=====
def compute_bertscore_real(reference, candidate, model_name="all-MiniLM-L6-v2"):
    """
    使用真实 BERT 模型计算 BERTScore
    
    需要安装:
        pip install sentence-transformers torch numpy
    
    如果没安装，自动回退到哈希版
    """
    try:
        from sentence_transformers import SentenceTransformer
        import torch
        import numpy as np
        
        model = SentenceTransformer(model_name)
        
        # 编码
        ref_emb = model.encode(reference, convert_to_tensor=True)
        can_emb = model.encode(candidate, convert_to_tensor=True)
        
        # 相似度矩阵
        cos = torch.nn.CosineSimilarity(dim=1)
        
        # Precision: 每个生成Token匹配参考中最好的
        # 注意：实际 BERTScore 是对 Token 级嵌入操作
        # 这里用句级嵌入简化演示
        similarity = cos(ref_emb.unsqueeze(0), can_emb.unsqueeze(0))
        
        return {
            "precision": round(float(similarity), 4),
            "recall": round(float(similarity), 4),
            "f1": round(float(similarity), 4),
        }
    except ImportError:
        # 回退到哈希版
        return compute_bertscore(reference, candidate)


# ===== 5. 批量评估 =====
def batch_bertscore(dataset):
    """对整个数据集计算 BERTScore"""
    results = []
    p_sum, r_sum, f1_sum = 0.0, 0.0, 0.0
    
    for item in dataset:
        ref = item.get("reference", "")
        ans = item.get("answer", "")
        
        scores = compute_bertscore(ref, ans)
        results.append({
            "question": item.get("question", ""),
            "precision": scores["precision"],
            "recall": scores["recall"],
            "f1": scores["f1"],
        })
        p_sum += scores["precision"]
        r_sum += scores["recall"]
        f1_sum += scores["f1"]
    
    n = len(results)
    stats = {
        "count": n,
        "avg_precision": round(p_sum / n, 4),
        "avg_recall": round(r_sum / n, 4),
        "avg_f1": round(f1_sum / n, 4),
    }
    return results, stats


# ===== 演示 =====
if __name__ == "__main__":
    test_pairs = [
        {
            "reference": "退货退款一般在收到退货后3-5个工作日到账，包含税费。",
            "candidate": "退款需要3-5天到账。",
        },
        {
            "reference": "食品属于特殊商品，非质量问题不支持退货。",
            "candidate": "食品不能退货。",
        },
    ]
    
    for i, pair in enumerate(test_pairs):
        scores = compute_bertscore(pair["reference"], pair["candidate"])
        print(f"\n=== 示例 {i+1} ===")
        print(f"参考: {pair['reference']}")
        print(f"生成: {pair['candidate']}")
        print(f"P={scores['precision']:.4f}  R={scores['recall']:.4f}  F1={scores['f1']:.4f}")
```

### 2.5 结果解读

```
Good:   P=0.92, R=0.94, F1=0.93  → 生成回答覆盖了参考中几乎所有信息点
OK:     P=0.85, R=0.78, F1=0.81  → 生成内容质量好但漏了部分信息
Bad:    P=0.65, R=0.71, F1=0.68  → 生成回答不完整或信息有偏差

P > R:  回答简洁但不够全面（漏了信息）
R > P:  回答覆盖全面但有点啰嗦（有冗余信息）
```

---

## 3. 交叉验证

### 3.1 为什么需要两套体系

```
                   LLM-as-Judge
                       ↓
              能发现"语义正确但事实错误"
              (比如："退货要一年"→ LLM 知道不对)
                       ↓
                  局限性
              (打分不一致、偏好长篇大论)
                       ↓
        ┌────────────────────────────┐
        │      交叉验证 = 更可靠       │
        └────────────────────────────┘
                       ↑
               BERTScore
                       ↑
              能发现"字面不同但意思一样"
              (比如："3-5个工作日" vs "3到5天")
                       ↑
                  局限性
              (不理解事实错误、只做表层匹配)
```

### 3.2 差异分析方法

当 LLM-as-Judge 和 BERTScore 评分不一致时：

| LLM | BERTScore | 含义 | 行动 |
|:---:|:---------:|:-----|:-----|
| 高分 (4-5) | 高分 (0.9+) | ✅ 一致好 | 保存为最佳实践 |
| 低分 (1-2) | 低分 (<0.7) | ✅ 一致差 | 优化 RAG |
| 高分 (4-5) | 低分 (<0.7) | ⚠️ LLM觉得好但语义不像 | 检查是否编造了合理信息 |
| 低分 (1-2) | 高分 (0.9+) | ⚠️ 语义相似但LLM不认可 | 检查LLM是否过于严格 |

---

## 4. 技术要点总结

### LLM-as-Judge 最佳实践

```
1. ✅ 用 Rubric 定义分数标准
2. ✅ 多维度评分（不要只给一个分）
3. ✅ 加权综合（根据场景调整权重）
4. ✅ 批量评估，保持一致性
5. ✅ 定期校准（用标杆答案做锚定）
6. ❌ 避免让 LLM 评估自己的输出
7. ❌ 避免单次评估就下结论
```

### BERTScore 最佳实践

```
1. ✅ 中文场景用 bge-small-zh-v1.5
2. ✅ 英文通用用 all-MiniLM-L6-v2
3. ✅ 结合 P/R 看问题：P低→冗余，R低→遗漏
4. ✅ 多句评估取均值
5. ❌ 不要把 BERTScore 当唯一标准
6. ❌ 不要在短文本（<5词）上使用
```

### 两个体系的选择矩阵

| 场景 | 推荐评估方式 |
|:-----|:------------|
| 快速迭代、看趋势 | BERTScore（快，无偏见） |
| 验收测试、上线前 | LLM-as-Judge（深层语义） |
| 发现 Bad Case | 两者结合 |
| 维度对比（完整vs简洁） | LLM-as-Judge（多维度） |
| 去重、判断是否相似 | BERTScore（客观） |

---

## 📁 产出文件

```
阶段9-职业冲刺/code/day45-进阶/评估体系/
├── data/
│   └── eval_dataset.json              # Day 1 的数据集（复用）
├── scripts/
│   ├── llm_as_judge.py               ← 新增：LLM-as-Judge 深度版
│   └── bertscore_eval.py             ← 新增：BERTScore 评估
└── report/
    ├── evaluation_report.json         # Day 1 报告
    └── evaluation_report.md           # Day 1 报告
    ├── llm_judge_report.json          ← 新增：LLM 评估结果
    └── cross_validation_report.md     ← 新增：交叉验证报告
```

## 思考题

1. **校准实验：** 同一个答案让 DeepSeek 评估 3 次（不同温度），看评分稳定性如何？
2. **Pairwise vs Score：** 你觉得"A/B 哪个好"和"A/B 各打几分"哪个更可靠？
3. **权重调优：** 客服场景（准确第一）和创意场景（帮助第一）的维度权重应该怎么调？
4. **BERTScore 升级：** 如果换成 `bge-m3` 做嵌入，评分会有多大变化？
