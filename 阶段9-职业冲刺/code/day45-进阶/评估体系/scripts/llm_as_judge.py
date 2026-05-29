"""
LLM-as-Judge 深度评估 — 进阶 Day 2

功能：
  1. 多维度单答案评分（忠实/完整/相关/简洁/有用）
  2. Pairwise A/B 对比评估
  3. 批量评估 + 统计报告
  4. 交叉验证（与 BERTScore 结果合并）

原理：
  LLM-as-Judge = 用大语言模型当"裁判"，对回答从多个维度打分。
  关键在于 Rubric（评分标准）和维度拆分，减少单一打分的偏差。

使用方式：
  python3 scripts/llm_as_judge.py              # 运行完整评估
"""

import os
import sys
import json
import time
import re
import math

# ========================================
# 配置
# ========================================
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_PATH = os.path.join(BASE_DIR, "..", "data", "eval_dataset.json")
REPORT_PATH = os.path.join(BASE_DIR, "..", "report", "llm_judge_report.json")
CROSS_REPORT_PATH = os.path.join(BASE_DIR, "..", "report", "cross_validation_report.md")

API_KEY = os.environ.get("DEEPSEEK_API_KEY")
if not API_KEY:
    env_path = os.path.expanduser("~/.hermes/.env")
    if os.path.exists(env_path):
        with open(env_path) as f:
            for line in f:
                if "DEEPSEEK_API_KEY" in line:
                    API_KEY = line.split("=", 1)[1].strip().strip('"').strip("'")
                    break
if not API_KEY:
    print("❌ 请设置 DEEPSEEK_API_KEY 环境变量")
    sys.exit(1)

BASE_URL = "https://api.deepseek.com"

# ========================================
# 评分标准
# ========================================
DIMENSIONS = {
    "faithfulness": {
        "name": "忠实度",
        "weight": 0.30,
        "description": "回答是否基于上下文，没有编造信息",
        "rubric": """
5 - 完全忠实：回答的每个观点都能在上下文中找到依据
4 - 大部分忠实：可能有1处微小推断，但整体正确
3 - 部分忠实：有些观点找不到依据，但核心内容正确
2 - 少量忠实：大部分内容编造或与上下文矛盾
1 - 不忠实：回答与上下文无关或严重矛盾
""",
    },
    "completeness": {
        "name": "完整性",
        "weight": 0.25,
        "description": "是否覆盖了上下文中所有相关信息",
        "rubric": """
5 - 完整覆盖：包含上下文中所有关键信息点
4 - 大部分覆盖：遗漏了1个次要信息点
3 - 部分覆盖：遗漏了多个信息点，但核心在
2 - 少量覆盖：只捕捉了少量关键信息
1 - 不完整：几乎没有用到上下文信息
""",
    },
    "relevance": {
        "name": "相关性",
        "weight": 0.20,
        "description": "是否直接回答了用户的问题",
        "rubric": """
5 - 高度相关：直接、准确回答用户问题
4 - 相关：回答了问题但略有偏移
3 - 部分相关：回答了问题的某一部分
2 - 低相关：勉强相关，但没真正回答问题
1 - 不相关：答非所问
""",
    },
    "conciseness": {
        "name": "简洁度",
        "weight": 0.10,
        "description": "回答是否简洁，信息密度高",
        "rubric": """
5 - 非常简洁：没有废话，每个句子都有信息量
4 - 简洁：略有冗余但不影响理解
3 - 一般：有一些不必要的重复或铺垫
2 - 啰嗦：明显冗余，信息密度低
1 - 非常啰嗦：大量无意义内容
""",
    },
    "helpfulness": {
        "name": "有用性",
        "weight": 0.15,
        "description": "综合来看用户是否得到了实际帮助",
        "rubric": """
5 - 非常有用：用户得到完整、可行动的答案
4 - 有用：用户得到了需要的核心信息
3 - 部分有用：得到了一些信息但不够
2 - 不太有用：提供的信息帮助不大
1 - 没用：对用户毫无帮助
""",
    },
}


def llm_call(messages, temperature=0.0, max_tokens=4096):
    """调用 DeepSeek API"""
    import requests
    for attempt in range(3):
        try:
            resp = requests.post(
                f"{BASE_URL}/v1/chat/completions",
                headers={
                    "Authorization": f"Bearer {API_KEY}",
                    "Content-Type": "application/json",
                },
                json={
                    "model": "deepseek-chat",
                    "messages": messages,
                    "temperature": temperature,
                    "max_tokens": max_tokens,
                },
                timeout=60,
            )
            return resp.json()["choices"][0]["message"]["content"]
        except Exception as e:
            if attempt < 2:
                print(f"  ⚠️ 重试 {attempt+1}/3: {e}")
                time.sleep(2)
            else:
                raise


def parse_json(text):
    """从 LLM 输出中提取 JSON"""
    # 直接解析
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass
    # 提取 ```json ``` 代码块
    m = re.search(r'```(?:json)?\s*([\s\S]*?)```', text)
    if m:
        try:
            return json.loads(m.group(1))
        except json.JSONDecodeError:
            pass
    # 找第一个 { 到最后一个 }
    m2 = re.search(r'\{[\s\S]*\}', text)
    if m2:
        try:
            return json.loads(m2.group())
        except json.JSONDecodeError:
            pass
    return None


# ========================================
# 1. 单答案多维度评分
# ========================================
def score_single_answer(question, context, answer):
    """对单个回答进行多维度评分"""
    rubric_text = "\n".join([
        f"\n### {v['name']}（权重: {v['weight']}）\n{v['description']}{v['rubric']}"
        for k, v in DIMENSIONS.items()
    ])

    prompt = f"""你是一个专业的 AI 回答评估员。请对以下回答进行多维度评分。

## 评分标准（Rubric）

每个维度 1-5 分，分数越高越好。
{rubric_text}

## 待评估

问题：{question}

上下文：
{context}

回答：{answer}

## 输出格式

请输出 JSON，格式如下，只输出 JSON 不要其他文字：

{{
    "faithfulness": {{"score": 5, "reason": "..."}},
    "completeness": {{"score": 5, "reason": "..."}},
    "relevance": {{"score": 5, "reason": "..."}},
    "conciseness": {{"score": 5, "reason": "..."}},
    "helpfulness": {{"score": 5, "reason": "..."}},
    "overall_assessment": "综合评语"
}}"""

    result = llm_call([
        {"role": "system", "content": "你是严谨的 AI 评估助手。严格按 JSON 格式输出。"},
        {"role": "user", "content": prompt},
    ], temperature=0.1)

    parsed = parse_json(result)
    if parsed is None:
        return {"error": "Parse failed", "raw": result}
    return parsed


def weighted_overall(scores):
    """计算加权总分"""
    total = 0.0
    for dim, info in DIMENSIONS.items():
        score = scores.get(dim, {}).get("score", 3)
        total += score * info["weight"]
    return round(total, 2)


# ========================================
# 2. Pairwise A/B 对比评估
# ========================================
def pairwise_compare(question, context, answer_a, answer_b):
    """比较两个回答，选出更好的"""
    prompt = f"""你是一个专业的 AI 回答评估员。请比较以下两个回答。

问题：{question}

上下文：
{context}

回答 A：
{answer_a}

回答 B：
{answer_b}

请从以下维度比较，并输出 JSON：

{{
    "winner": "A" 或 "B" 或 "tie",
    "overall_reason": "为什么这个更好",
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
        {"role": "system", "content": "你是严谨的 AI 评估助手。严格按 JSON 格式输出。"},
        {"role": "user", "content": prompt},
    ], temperature=0.1)

    parsed = parse_json(result)
    if parsed is None:
        return {"error": "Parse failed", "raw": result}
    return parsed


# ========================================
# 3. 批量评估
# ========================================
def load_dataset():
    """加载评估数据集"""
    if not os.path.exists(DATA_PATH):
        print(f"❌ 数据集不存在: {DATA_PATH}")
        # 尝试相对路径
        alt_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "data", "eval_dataset.json")
        if os.path.exists(alt_path):
            with open(alt_path) as f:
                return json.load(f)
        return None
    with open(DATA_PATH) as f:
        return json.load(f)


def ensure_dataset_format(dataset):
    """确保数据集包含 'answer' 字段（RAG 系统生成的回答）"""
    if not dataset:
        return []
    # 如果数据集中没有 answer，使用 simulated_answer
    for item in dataset:
        if "answer" not in item:
            item["answer"] = item.get("simulated_answer", "")
        if "reference" not in item:
            item["reference"] = item.get("expected_answer", item.get("answer", ""))
    return dataset


def batch_evaluate(dataset, max_items=None):
    """批量评估"""
    items = dataset[:max_items] if max_items else dataset
    results = []

    print(f"\n📊 LLM-as-Judge 批量评估（共 {len(items)} 条）")
    print("=" * 60)

    for i, item in enumerate(items):
        q = item["question"]
        ctx = item.get("context", "")
        ans = item.get("answer", "")

        if not ans:
            print(f"  ⏭️ [{i+1}/{len(items)}] 跳过（无回答）: {q[:30]}...")
            continue

        print(f"  🔍 [{i+1}/{len(items)}] {q[:40]}...")
        scores = score_single_answer(q, ctx, ans)
        overall = weighted_overall(scores)

        # 打印简评
        if "error" in scores:
            print(f"    ❌ 解析失败: {scores['error']}")
            overall = 0
        else:
            dim_scores = {k: scores[k]["score"] for k in DIMENSIONS if k in scores}
            dim_str = " | ".join([f"{v['name']}:{dim_scores.get(k, '?')}" for k, v in DIMENSIONS.items()])
            print(f"    ✅ 总分: {overall}  ({dim_str})")

        results.append({
            "index": i,
            "question": q,
            "answer": ans[:200],
            "scores": scores,
            "overall": overall,
        })
        time.sleep(1)  # 限流

    # 统计
    valid = [r for r in results if "error" not in r.get("scores", {})]
    if valid:
        all_scores = [r["overall"] for r in valid]
        stats = {
            "count": len(valid),
            "mean": round(sum(all_scores) / len(all_scores), 2),
            "min": min(all_scores),
            "max": max(all_scores),
            "std": round(math.sqrt(sum((x - sum(all_scores)/len(all_scores))**2 for x in all_scores) / len(all_scores)), 2),
        }
    else:
        stats = {"count": 0, "mean": 0, "min": 0, "max": 0, "std": 0}

    return results, stats


# ========================================
# 4. 报告生成
# ========================================
def save_report(results, stats):
    """保存评估报告"""
    report = {
        "metadata": {
            "date": time.strftime("%Y-%m-%d %H:%M:%S"),
            "method": "LLM-as-Judge",
            "model": "deepseek-chat",
            "dimensions": {k: {"name": v["name"], "weight": v["weight"]} for k, v in DIMENSIONS.items()},
        },
        "stats": stats,
        "results": results,
    }

    os.makedirs(os.path.dirname(REPORT_PATH), exist_ok=True)
    with open(REPORT_PATH, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    print(f"\n📄 报告已保存: {REPORT_PATH}")
    return report


def generate_cross_report(llm_results, bert_results):
    """生成交叉验证报告"""
    # 合并两份结果
    combined = []
    for lr in llm_results:
        q = lr["question"]
        br = next((b for b in bert_results if b["question"] == q), None)
        combined.append({
            "question": q[:50],
            "llm_score": lr["overall"],
            "bert_precision": br["precision"] if br else "N/A",
            "bert_recall": br["recall"] if br else "N/A",
            "bert_f1": br["f1"] if br else "N/A",
        })

    # 分析差异
    discrepancies = [c for c in combined if c["llm_score"] != "N/A" and c["bert_f1"] != "N/A"
                     and isinstance(c["llm_score"], (int, float))
                     and isinstance(c["bert_f1"], (int, float))]

    report_text = f"""# 交叉验证报告

> 生成时间: {time.strftime("%Y-%m-%d %H:%M:%S")}
> 方法: LLM-as-Judge（主观） + BERTScore（客观）

## 整体统计

| 指标 | LLM-as-Judge | BERTScore F1 |
|:-----|:-----------:|:------------:|
| 均值 | {sum(d["llm_score"] for d in discrepancies)/len(discrepancies):.2f}/5 | {sum(d["bert_f1"] for d in discrepancies)/len(discrepancies):.4f} |
| 最低 | {min(d["llm_score"] for d in discrepancies):.2f} | {min(d["bert_f1"] for d in discrepancies):.4f} |
| 最高 | {max(d["llm_score"] for d in discrepancies):.2f} | {max(d["bert_f1"] for d in discrepancies):.4f} |

## 逐条对比

| # | 问题 | LLM评分 | BERT-F1 | 差异度 |
|:-:|:-----|:-------:|:-------:|:------:|
"""

    for i, c in enumerate(combined):
        llm_score = f"{c['llm_score']:.2f}" if isinstance(c['llm_score'], float) else c['llm_score']
        bert_f1 = f"{c['bert_f1']:.4f}" if isinstance(c['bert_f1'], float) else c['bert_f1']
        diff = "—"
        if isinstance(c['llm_score'], (int, float)) and isinstance(c['bert_f1'], (int, float)):
            normalized_bert = c['bert_f1'] * 5  # 归一化到 5 分制
            diff = f"{abs(c['llm_score'] - normalized_bert):.2f}"
        report_text += f"| {i+1} | {c['question']} | {llm_score} | {bert_f1} | {diff} |\n"

    report_text += """

## 差异分析

| 类型 | 含义 | 数量 |
|:-----|:-----|:----:|
| ✅ 一致高 | LLM≥4 且 BERT≥0.85 | """
    high_high = sum(1 for c in discrepancies if c['llm_score'] >= 4 and c['bert_f1'] >= 0.85)
    high_low = sum(1 for c in discrepancies if c['llm_score'] >= 4 and c['bert_f1'] < 0.85)
    low_high = sum(1 for c in discrepancies if c['llm_score'] < 4 and c['bert_f1'] >= 0.85)
    low_low = sum(1 for c in discrepancies if c['llm_score'] < 4 and c['bert_f1'] < 0.85)
    report_text += f"{high_high} |\n| ⚠️ LLM高但BERT低 | LLM认为好但语义匹配不足 | {high_low} |\n| ⚠️ LLM低但BERT高 | BERT认为相似但LLM不认可 | {low_high} |\n| ✅ 一致低 | LLM<4 且 BERT<0.85 | {low_low} |\n"

    os.makedirs(os.path.dirname(CROSS_REPORT_PATH), exist_ok=True)
    with open(CROSS_REPORT_PATH, "w", encoding="utf-8") as f:
        f.write(report_text)
    print(f"📄 交叉验证报告: {CROSS_REPORT_PATH}")

    return combined


# ========================================
# 主流程
# ========================================
def main():
    print("=" * 60)
    print("  LLM-as-Judge 深度评估")
    print("=" * 60)

    # 加载数据集
    dataset = load_dataset()
    if not dataset:
        print("❌ 无法加载数据集")
        sys.exit(1)
    dataset = ensure_dataset_format(dataset)
    print(f"📂 加载数据集: {len(dataset)} 条")

    # 批量评估
    results, stats = batch_evaluate(dataset)

    if stats["count"] == 0:
        print("❌ 没有有效评估结果")
        sys.exit(1)

    print(f"\n📊 评估统计:")
    print(f"   总量: {stats['count']} 条")
    print(f"   均分: {stats['mean']}/5")
    print(f"   最低: {stats['min']}/5")
    print(f"   最高: {stats['max']}/5")
    print(f"   标准差: {stats['std']}")

    # 保存报告
    save_report(results, stats)

    # 如果有 BERTScore 结果，生成交叉验证报告
    bert_report_path = os.path.join(os.path.dirname(REPORT_PATH), "bertscore_report.json")
    if os.path.exists(bert_report_path):
        print("\n🔗 检测到 BERTScore 报告，生成交叉验证...")
        with open(bert_report_path) as f:
            bert_data = json.load(f)
        generate_cross_report(results, bert_data.get("results", []))
    else:
        print("\nℹ️ 未检测到 BERTScore 报告，跳过交叉验证。")
        print("   运行 python3 bertscore_eval.py 后交叉验证自动生效。")

    print("\n✅ 完成！")


if __name__ == "__main__":
    main()
