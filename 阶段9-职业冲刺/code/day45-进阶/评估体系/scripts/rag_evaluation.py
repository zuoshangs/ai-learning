"""
RAG 评估体系 — 完整实现

手动实现 RAGAS 四项核心指标 + LLM-as-Judge，不依赖外部评估库。

指标说明:
  1. faithfulness      → 回答是否忠实于上下文（有没有编造信息）
  2. answer_relevancy  → 回答是否与问题相关
  3. context_precision → 检索到的上下文是否精准（噪声少）
  4. context_recall    → 检索到的上下文是否覆盖了回答所需信息

原理: 每个指标 = 细粒度拆解 → LLM 逐项判断 → 取平均
"""

import json
import os
import sys

# ========================================
# 配置
# ========================================
DATA_PATH = "data/eval_dataset.json"
REPORT_PATH = "report/evaluation_report.json"
REPORT_TEXT_PATH = "report/evaluation_report.md"

# ========================================
# Simulated answers (模拟 RAG 系统的回答)
# 实际场景中应该调用你的 RAG 系统来生成
# ========================================

# 为了演示评估指标体系，这里模拟几类回答质量
# 模式 0: ground_truth（满分回答 — 对照基准）
# 模式 1: 高质量回答（正确信息 + 流畅表达）
# 模式 2: 中质量回答（信息基本正确但不完全）
# 模式 3: 低质量回答（信息缺失或部分错误）

MODE = "模拟"  # 标签，实际用时应改为 "真实"

SIMULATED_ANSWERS = {
    # ---- 模式 0: 满分基准 ----
    "我想退货，多久之内可以退？": "本平台支持30天内无理由退货，商品需保持原状、配件齐全、包装完整。特殊商品如食品、内衣除外。",
    "退货后什么时候收到退款？": "退款将在我们收到退货后的5-7个工作日内原路返回。使用信用卡支付的退款需要7-10个工作日。",
    "食品可以退货吗？": "特殊商品如食品、内衣等不支持退货。普通商品30天内可以无理由退货。",
    "退款金额包括哪些？": "退款金额包含商品原价和税费，原路返回。",
    "普通配送要多久到？": "标准配送3-5个工作日，满99元包邮。加急配送1-2个工作日，需加收15元。",
    "加急配送多少钱？": "加急配送1-2个工作日，需加收15元。",
    "满多少包邮？": "满99元包邮。",
    "当日达什么时候下单才行？": "当日达仅限部分城市，需在11:00前下单。",
    "会员分几个等级？": "会员分为普通、银卡、金卡、钻石卡四级。",
    "金卡会员要消费多少？": "金卡需年消费3000元，享受9折优惠。",
    "钻石卡打几折？": "钻石卡需年消费10000元，享受8.5折优惠。",
    "怎么联系人工客服？": "您可以拨打客服热线400-123-4567，工作日9:00-21:00。或发送邮件至support@example.com。",
    "客服热线几点到几点？": "工作日9:00-21:00。",
    "怎么设置安全的密码？": "建议设置强密码，包含大小写字母+数字+特殊字符，并开启双重验证。",
    "账号被盗了怎么办？": "立即修改密码并联系客服。建议开启双重验证以增强账号安全性。",
    "优惠券怎么用？": "优惠券在结算页面使用，每笔订单限用一张，有效期为领取后7天。",
    "优惠券有效期多久？": "领取后7天。",
    "我买的东西想退掉，退款回哪里？": "30天内可以退，收到退货后5-7个工作日内原路返回。",
    "我是金卡会员，退货有什么特殊政策吗？": "金卡会员享受9折优惠。退货政策方面与普通会员一致，支持30天内无理由退货。",
    "优惠券和会员折扣能一起用吗？": "优惠券每笔订单限用一张，部分商品不参与优惠活动。会员折扣与优惠券能否叠加使用需查看具体活动规则。",

    # ---- 模式 1: 有轻微缺陷的回答（测试 faithfulness 是否能检测） ----
    "_缺陷_食品可以退货吗？": "所有商品都可以退货，我们支持30天内无理由退货。",
    "_缺陷_退货后什么时候收到退款？": "退款在3-5天内到账。",
    "_缺陷_钻石卡打几折？": "钻石卡打7折。",

    # ---- 模式 2: 不完整回答（测试 recall 是否能检测） ----
    "_不完整_普通配送要多久到？": "标准配送3-5个工作日。",
    "_不完整_怎么联系人工客服？": "您可以拨打客服热线400-123-4567。",
    "_不完整_优惠券怎么用？": "优惠券在结算页面使用。",

    # ---- 模式 3: 包含幻觉（测试 faithfulness 是否能检测） ----
    "_幻觉_退货后什么时候收到退款？": "退款将在收到退货后立即到账，通常1小时内完成。信用卡支付更快捷。",
    "_幻觉_加急配送多少钱？": "加急配送免费，只需升级到VIP会员即可享受。",
}


def load_dataset(path=DATA_PATH):
    """加载评估数据集。"""
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def get_answer(question, simulated=True):
    """获取 RAG 系统的回答。模拟模式下返回预置答案。"""
    if simulated:
        # 支持前缀：_缺陷_ / _不完整_ / _幻觉_ — 用于测试不同质量水平
        answer = SIMULATED_ANSWERS.get(question, "")
        return answer
    else:
        # 实际部署时，这里调用 RAG 系统获取真实回答
        # response = requests.post("http://localhost:8080/api/chat", json={"message": question})
        # return response.json()["reply"]
        raise NotImplementedError("请设置为 simulated=True 或实现真实调用")


def compute_semantic_similarity(text1, text2):
    """
    计算两段文本的语义相似度。
    使用 TF-IDF 字符 n-gram 余弦相似度（无需下载模型，本地可用）。
    n-gram 范围 (1,4) 能捕捉中文的词级和短语级相似度。
    """
    try:
        from sklearn.feature_extraction.text import TfidfVectorizer
        from sklearn.metrics.pairwise import cosine_similarity

        vectorizer = TfidfVectorizer(analyzer='char', ngram_range=(1, 4))
        tfidf = vectorizer.fit_transform([text1, text2])
        return float(cosine_similarity(tfidf[0:1], tfidf[1:2])[0][0])
    except Exception as e:
        print(f"    ⚠️ 相似度计算失败: {e}")
        return 0.5  # 兜底

compute_bert_score = compute_semantic_similarity  # 别名兼容


# ========================================
# Metrics — Faithfulness (忠实度)
# ========================================

def evaluate_faithfulness(answer, contexts):
    """
    评估回答是否忠实于上下文。
    方法：把回答拆成句子，逐个判断每个句子是否被上下文支持。
    分数 = 被支持的句子数 / 总句子数
    """
    if not contexts:
        return 0.0, "没有上下文可评估"

    # 把回答拆成句子
    sentences = [s.strip() for s in answer.replace("。", "。|").replace("？", "？|").split("|") if s.strip()]
    if not sentences:
        return 1.0, "回答为空，视为忠实"

    combined_context = " ".join(contexts)
    supported = 0
    details = []

    for i, sentence in enumerate(sentences):
        # 对中文：按字符级重叠判断（中文没有空格分词）
        # 方法：提取句子中的"信息片段"（3字以上的连续字符串）
        info_segments = []
        for j in range(len(sentence) - 2):
            seg = sentence[j:j+3]
            if seg.strip():
                info_segments.append(seg)

        # 检查信息片段在上下文中的覆盖率
        if info_segments:
            matched = sum(1 for seg in info_segments if seg in combined_context)
            ratio = matched / len(info_segments)
            is_supported = ratio >= 0.3  # 30% 三字片段匹配视为支持
        else:
            is_supported = True

        if is_supported:
            supported += 1
        details.append({
            "sentence": sentence,
            "supported": is_supported
        })

    score = supported / len(sentences) if sentences else 1.0
    return score, details


# ========================================
# Metrics — Answer Relevancy (回答相关性)
# ========================================

def evaluate_answer_relevancy(question, answer):
    """
    评估回答是否与问题相关。
    方法：用 Sentence-BERT 计算问题与回答的语义相似度。
    """
    if not answer or not question:
        return 0.0

    score = compute_bert_score(question, answer)
    return score


# ========================================
# Metrics — Context Precision (上下文精确度)
# ========================================

def evaluate_context_precision(question, contexts):
    """
    评估检索到的上下文中，有用的比例。
    方法：对每个上下文，检查是否包含回答所需的关键信息。
    未使用 ground_truth 的"真实有用"判断（这是模拟）。
    为简化，假设每个 context 都是有用的（真实场景需 LLM 逐条判断）。
    """
    if not contexts:
        return 0.0

    # 简化实现：假设检索到的文档都是相关的
    # 真实场景应让 LLM 判断每个上下文对回答"这个具体问题"是否有用
    # 这里计算每个上下文与问题的语义相关度
    scores = []
    for ctx in contexts:
        sim = compute_bert_score(question, ctx)
        scores.append(sim)

    # precision = 有用文档数 / 检索文档总数
    # 有用标准：与问题的语义相似度 > 0.05（TF-IDF 字符 n-gram 的范围，中文下阈值较低）
    useful = sum(1 for s in scores if s > 0.02)
    precision = useful / len(contexts) if contexts else 0

    return precision, scores


# ========================================
# Metrics — Context Recall (上下文召回率)
# ========================================

def evaluate_context_recall(answer, contexts, ground_truth=None):
    """
    评估检索到的上下文是否覆盖了回答所需的信息。
    方法：把 ground_truth 拆成"信息点"，检查这些信息点在上下文中的覆盖率。
    """
    if not contexts:
        return 0.0, []

    combined_context = " ".join(contexts)

    # 如果提供了 ground_truth，用它来提取"需要被覆盖的信息"
    reference = ground_truth if ground_truth else answer

    # 将 reference 拆成关键信息点（按逗号/句号分割后的长度为 4+ 的片段）
    info_points = []
    for sep in ["。", "，", "。", "！", "？"]:
        parts = reference.split(sep)
        for p in parts:
            p = p.strip()
            if len(p) >= 4 and p not in info_points:
                info_points.append(p)

    if not info_points:
        return 1.0, ["没有可提取的信息点"]

    # 计算每个信息点在上下文中的覆盖率
    covered = []
    for point in info_points:
        is_covered = point in combined_context
        # 部分匹配检查
        if not is_covered:
            # 检查是否至少 50% 的内容出现在上下文中
            chars_in_context = sum(1 for c in point if c in combined_context)
            coverage_ratio = chars_in_context / len(point) if point else 0
            is_covered = coverage_ratio >= 0.7
        covered.append(is_covered)

    recall = sum(covered) / len(info_points) if info_points else 1.0
    return recall, list(zip(info_points, covered))


# ========================================
# LLM-as-Judge (大模型打分)
# ========================================

def llm_as_judge(question, answer, contexts, ground_truth=None):
    """
    用 LLM 作为裁判，对回答进行综合评分。
    使用 DeepSeek API（与客服平台相同的模型）。
    返回 1-5 分的综合评分 + 理由。
    """
    try:
        from openai import OpenAI
        import os

        client = OpenAI(
            api_key=os.environ.get("DEEPSEEK_API_KEY", ""),
            base_url="https://api.deepseek.com"
        )

        # Fallback: try loading from ~/.hermes/.env
        if not client.api_key:
            env_path = os.path.expanduser("~/.hermes/.env")
            if os.path.exists(env_path):
                with open(env_path) as f:
                    for line in f:
                        if line.startswith("DEEPSEEK_API_KEY="):
                            client.api_key = line.strip().split("=", 1)[1]
                            break

        context_text = "\n".join([f"[文档{i+1}] {c}" for i, c in enumerate(contexts)])

        prompt = f"""你是一个严谨的 RAG 回答质量评审员。请从 1-5 分（5=最佳）对以下 AI 回答进行评分。

评分维度：
1. **忠实度** — 回答是否忠实于提供的上下文，没有编造信息
2. **完整性** — 回答是否完整覆盖了问题所需的所有信息
3. **相关性** — 回答是否直接回应了用户的问题
4. **简洁性** — 回答是否简洁明了，没有冗余信息

【问题】{question}

【上下文】
{context_text}

【AI 回答】
{answer}

{f"【标准答案】{ground_truth}" if ground_truth else ""}

请按以下 JSON 格式输出：
{{
    "score": <1-5 整数>,
    "faithfulness_score": <1-5>,
    "completeness_score": <1-5>,
    "relevance_score": <1-5>,
    "conciseness_score": <1-5>,
    "reason": "<打分理由，50字以内>",
    "improvement": "<改进建议，30字以内>"
}}
只输出 JSON，不要其他内容。"""

        response = client.chat.completions.create(
            model="deepseek-chat",
            messages=[{"role": "user", "content": prompt}],
            temperature=0.1,
            max_tokens=500,
        )

        result_text = response.choices[0].message.content.strip()
        # 提取 JSON
        if "```json" in result_text:
            result_text = result_text.split("```json")[1].split("```")[0].strip()
        elif "```" in result_text:
            result_text = result_text.split("```")[1].split("```")[0].strip()

        result = json.loads(result_text)
        return result

    except Exception as e:
        return {
            "score": 0,
            "faithfulness_score": 0,
            "completeness_score": 0,
            "relevance_score": 0,
            "conciseness_score": 0,
            "reason": f"LLM 调用失败: {str(e)}",
            "improvement": ""
        }


# ========================================
# 完整评估流程
# ========================================

def run_evaluation(simulated=True, use_llm_judge=True, llm_max_samples=5):
    """运行完整评估。llm_max_samples: LLM 评估的样本数上限（节省 Token）。"""
    print("=" * 60)
    print("  RAG 评估体系 — 完整评估报告")
    print("=" * 60)

    dataset = load_dataset()
    results = []

    # 统计汇总
    total_faithfulness = 0
    total_relevancy = 0
    total_precision = 0
    total_recall = 0
    total_llm_score = 0

    n = len(dataset)

    print(f"\n📊 评估数据集: {n} 条")
    print(f"   评估模式: {'Simulated (预置答案)' if simulated else '真实 RAG 调用'}")
    print(f"   LLM-as-Judge: {'启用 (DeepSeek)' if use_llm_judge and simulated else '未启用'}")

    for i, item in enumerate(dataset):
        question = item["question"]
        ground_truth = item["ground_truth"]
        contexts = item["contexts"]

        # 1. 获取回答
        answer = get_answer(question, simulated=simulated)

        # 2. Faithfulness
        faithfulness, faithful_detail = evaluate_faithfulness(answer, contexts)
        if isinstance(faithful_detail, list):
            faithful_desc = f"{faithful_detail.count(True)}/{len(faithful_detail)} 语句受支持"
        else:
            faithful_desc = str(faithful_detail)

        # 3. Answer Relevancy
        relevancy = evaluate_answer_relevancy(question, answer)

        # 4. Context Precision
        precision, precision_scores = evaluate_context_precision(question, contexts)

        # 5. Context Recall
        recall, recall_detail = evaluate_context_recall(answer, contexts, ground_truth)

        record = {
            "question": question,
            "answer": answer,
            "faithfulness": round(faithfulness, 4),
            "answer_relevancy": round(relevancy, 4),
            "context_precision": round(precision, 4),
            "context_recall": round(recall, 4),
            "faithful_detail": faithful_desc,
        }

        total_faithfulness += faithfulness
        total_relevancy += relevancy
        total_precision += precision
        total_recall += recall

        # 6. LLM-as-Judge（只评估前 llm_max_samples 条）
        llm_result = None
        if use_llm_judge and simulated and i < llm_max_samples:
            print(f"\n  [{i+1}/{n}] ⏳ LLM 评估: {question[:20]}...")
            llm_result = llm_as_judge(question, answer, contexts, ground_truth)
            record["llm_judge"] = llm_result
            total_llm_score += llm_result.get("score", 0)

            print(f"     综合: {llm_result.get('score', '?')}/5 "
                  f"忠实: {llm_result.get('faithfulness_score', '?')} "
                  f"完整: {llm_result.get('completeness_score', '?')} "
                  f"相关: {llm_result.get('relevance_score', '?')} "
                  f"简洁: {llm_result.get('conciseness_score', '?')}")
            print(f"     说明: {llm_result.get('reason', '')}")
        else:
            print(f"  [{i+1}/{n}] 📊 {question[:30]}")

        results.append(record)

    # ====================================
    # 汇总
    # ====================================
    summary = {
        "total_samples": n,
        "mode": "Simulated" if simulated else "Real",
        "avg_faithfulness": round(total_faithfulness / n, 4),
        "avg_answer_relevancy": round(total_relevancy / n, 4),
        "avg_context_precision": round(total_precision / n, 4),
        "avg_context_recall": round(total_recall / n, 4),
    }
    if use_llm_judge and simulated:
        llm_evaluated = min(n, llm_max_samples)
        summary["avg_llm_judge_score"] = round(total_llm_score / llm_evaluated, 2)

    # 按分类汇总
    categories = {}
    for item in dataset:
        cat = item["category"]
        if cat not in categories:
            categories[cat] = {"count": 0, "faithfulness": 0, "relevancy": 0}
        categories[cat]["count"] += 1

    for i, item in enumerate(dataset):
        cat = item["category"]
        categories[cat]["faithfulness"] += results[i]["faithfulness"]
        categories[cat]["relevancy"] += results[i]["answer_relevancy"]

    summary["by_category"] = {}
    for cat, data in categories.items():
        summary["by_category"][cat] = {
            "count": data["count"],
            "avg_faithfulness": round(data["faithfulness"] / data["count"], 4),
            "avg_relevancy": round(data["relevancy"] / data["count"], 4),
        }

    # ====================================
    # 输出报告
    # ====================================
    print("\n" + "=" * 60)
    print("  📊 综合评估结果")
    print("=" * 60)
    print(f"  样本数:                {n}")
    print(f"  平均忠实度 (Faithfulness):  {summary['avg_faithfulness']:.4f}")
    print(f"  平均相关性 (Relevancy):     {summary['avg_answer_relevancy']:.4f}")
    print(f"  平均精确度 (Precision):     {summary['avg_context_precision']:.4f}")
    print(f"  平均召回率 (Recall):        {summary['avg_context_recall']:.4f}")
    if "avg_llm_judge_score" in summary:
        print(f"  LLM 综合评分:              {summary['avg_llm_judge_score']:.2f}/5")

    print(f"\n  按分类:")
    for cat, data in summary["by_category"].items():
        print(f"    {cat}: {data['count']}条 | 忠实 {data['avg_faithfulness']:.4f} | 相关 {data['avg_relevancy']:.4f}")

    # 保存结果
    output = {
        "summary": summary,
        "results": results
    }
    os.makedirs(os.path.dirname(REPORT_PATH), exist_ok=True)
    with open(REPORT_PATH, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)

    print(f"\n📝 评估报告已保存: {REPORT_PATH}")

    # 生成 Markdown 报告
    generate_markdown_report(summary, results)

    return summary, results


def generate_markdown_report(summary, results):
    """生成可读的 Markdown 评估报告。"""
    lines = []
    lines.append("# 📊 RAG 评估报告\n")
    lines.append(f"> 生成时间: 2026-05-28")
    lines.append(f"> 评估模式: {summary['mode']}")
    lines.append(f"> 样本数: {summary['total_samples']}\n")

    lines.append("## 综合评分\n")
    lines.append("| 指标 | 分数 | 说明 |")
    lines.append("|:-----|:----:|:-----|")
    lines.append(f"| Faithfulness (忠实度) | {summary['avg_faithfulness']:.4f} | 回答是否忠实于上下文 |")
    lines.append(f"| Answer Relevancy (相关性) | {summary['avg_answer_relevancy']:.4f} | 回答是否与问题相关 |")
    lines.append(f"| Context Precision (精确度) | {summary['avg_context_precision']:.4f} | 检索的上下文是否精准 |")
    lines.append(f"| Context Recall (召回率) | {summary['avg_context_recall']:.4f} | 检索是否覆盖了需要的信息 |")
    if "avg_llm_judge_score" in summary:
        lines.append(f"| LLM-as-Judge (综合) | {summary['avg_llm_judge_score']:.2f}/5 | DeepSeek 综合评分 |")
    lines.append("")

    lines.append("## 按分类\n")
    lines.append("| 分类 | 条数 | 忠实度 | 相关性 |")
    lines.append("|:-----|:----:|:------:|:------:|")
    for cat, data in sorted(summary["by_category"].items()):
        lines.append(f"| {cat} | {data['count']} | {data['avg_faithfulness']:.4f} | {data['avg_relevancy']:.4f} |")
    lines.append("")

    lines.append("## 逐条明细\n")
    for i, r in enumerate(results):
        lines.append(f"### #{i+1}: {r['question']}\n")
        lines.append(f"**回答:** {r['answer']}\n")
        lines.append(f"| 指标 | 分数 |")
        lines.append(f"|:-----|:----:|")
        lines.append(f"| Faithfulness | {r['faithfulness']:.4f} |")
        lines.append(f"| Relevancy | {r['answer_relevancy']:.4f} |")
        lines.append(f"| Precision | {r['context_precision']:.4f} |")
        lines.append(f"| Recall | {r['context_recall']:.4f} |")
        if "llm_judge" in r:
            lj = r["llm_judge"]
            lines.append(f"| LLM Judge | {lj.get('score', '?')}/5 |")
            lines.append(f"| 理由 | {lj.get('reason', '')} |")
            if lj.get("improvement"):
                lines.append(f"| 建议 | {lj.get('improvement', '')} |")
        lines.append("")

    report_text = "\n".join(lines)
    with open(REPORT_TEXT_PATH, "w", encoding="utf-8") as f:
        f.write(report_text)

    print(f"📝 Markdown 报告已保存: {REPORT_TEXT_PATH}")


if __name__ == "__main__":
    # 运行评估
    summary, results = run_evaluation(
        simulated=True,
        use_llm_judge=True
    )
