"""
BERTScore 语义相似度评估 — 进阶 Day 2

功能：
  1. 纯 Python 版 BERTScore（哈希 + 同义词表，无需 GPU）
  2. 真实 BERT 版 BERTScore（sentence-transformers，可选）
  3. 批量评估 + 统计报告
  4. 与 LLM-as-Judge 交叉验证

原理：
  BERTScore = 逐 Token 语义匹配
  - Precision: 生成的每个词是否都有参考中的词对应
  - Recall: 参考中的每个词是否都被生成覆盖了
  - F1: P 和 R 的调和平均

使用方式：
  python3 scripts/bertscore_eval.py                    # 纯 Python 版
  python3 scripts/bertscore_eval.py --real             # 使用 real BERT（需安装 sentence-transformers）
"""

import os
import sys
import json
import re
import math
import time

# ========================================
# 配置
# ========================================
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_PATH = os.path.join(BASE_DIR, "..", "data", "eval_dataset.json")
REPORT_PATH = os.path.join(BASE_DIR, "..", "report", "bertscore_report.json")


# ========================================
# 1. 中文分词器
# ========================================
def tokenize_chinese(text):
    """
    中文分词（混合策略：标点切分 + 双字/单字混合）
    
    不用 jieba 的原因是保持无依赖，且对 BERTScore 来说
    字符级 tokenize 其实够用——BERT 本身就是 char/subword 级别的。
    """
    # 统一标点
    text = re.sub(r'[，。！？、；：""''（）【】《》—…·\s]+', ' ', text)
    text = re.sub(r'[a-zA-Z0-9]+', lambda m: f' {m.group()} ', text)  # 英文/数字加空格
    
    words = text.split()
    tokens = []
    
    for word in words:
        # 纯数字或英文直接保留
        if re.match(r'^[a-zA-Z0-9\.\-\+\/]+$', word):
            tokens.append(word)
        # 中文：按字符切分（BERT 风格）
        else:
            for char in word:
                tokens.append(char)
    
    return [t for t in tokens if t.strip()]


# ========================================
# 2. 语义相似度（哈希 + 同义词）
# ========================================
SYNONYM_GROUPS = [
    {"退款", "退钱", "返还", "退回", "退款金额", "退费"},
    {"退货", "退换", "退"},
    {"配送", "快递", "发货", "物流", "运输", "运送", "寄送"},
    {"支付", "付款", "缴费", "结算", "支付方式", "付款方式"},
    {"客服", "客服人员", "服务人员", "人工客服", "售后"},
    {"订单", "下单", "订单一"},
    {"商品", "产品", "物品", "货物", "商品信息"},
    {"质量", "品质", "好坏", "质量问题"},
    {"时效", "时间", "天数", "周期", "工作日", "天"},
    {"到账", "入账", "收到"},
    {"地址", "收货地址", "收货信息"},
    {"优惠", "折扣", "优惠券", "代金券"},
    {"发票", "票据", "收据"},
    {"食品", "食物", "零食", "生鲜"},
    {"包装", "外包装", "包装盒"},
    {"签收", "收货", "签收确认"},
    {"运费", "邮费", "邮资", "物流费", "快递费"},
    {"说明", "描述", "详情", "信息"},
    {"查询", "查看", "查", "搜索"},
    {"联系", "联络", "打电话", "沟通"},
]

# 构建查表
SYNONYM_MAP = {}
for group in SYNONYM_GROUPS:
    for word in group:
        if word not in SYNONYM_MAP:
            SYNONYM_MAP[word] = set()
        SYNONYM_MAP[word].update(group)

# 常见停用词（忽略）
STOP_WORDS = {"的", "了", "在", "是", "我", "有", "和", "就", "不", "人",
              "都", "一", "一个", "上", "也", "很", "到", "说", "要", "去",
              "你", "会", "着", "没有", "看", "好", "自己", "这", "他", "她",
              "它", "们", "那", "什么", "怎么", "为什么", "可以", "吗", "吧",
              "啊", "呢", "哦", "嗯", "啦"}


def semantic_similarity(w1, w2):
    """
    计算两个 Token 的语义相似度
    
    策略（从精确到模糊）：
      1. 完全相同 → 1.0
      2. 同义词 → 0.85
      3. 包含关系 → 0.7
      4. 数字相似 → 0.6-0.8
      5. 公共字符 → 0.3-0.5
      6. 其他 → 0.0
    """
    if w1 == w2:
        return 1.0
    
    # 检查停用词
    if w1 in STOP_WORDS or w2 in STOP_WORDS:
        return 0.0
    
    # 同义词检查
    syn_set1 = SYNONYM_MAP.get(w1, {w1})
    syn_set2 = SYNONYM_MAP.get(w2, {w2})
    if w1 in syn_set2 or w2 in syn_set1:
        return 0.85
    if syn_set1 & syn_set2:
        return 0.85
    
    # 包含关系
    if w1 in w2 or w2 in w1:
        # "退货" in "退货流程" 比 "退" in "退款" 更精确
        min_len = min(len(w1), len(w2))
        max_len = max(len(w1), len(w2))
        ratio = min_len / max_len
        if ratio > 0.5:
            return 0.75
        return 0.55
    
    # 数字/日期匹配
    if re.match(r'^\d+$', w1) and re.match(r'^\d+$', w2):
        return 1.0 if w1 == w2 else 0.3
    if re.match(r'^\d+', w1) and re.match(r'^\d+', w2):
        num1 = re.match(r'\d+', w1).group()
        num2 = re.match(r'\d+', w2).group()
        if num1 == num2:
            return 0.8
    
    # 公共字符比例
    common = set(w1) & set(w2)
    if common:
        jaccard = len(common) / max(len(set(w1) | set(w2)), 1)
        if jaccard > 0.5:
            return 0.5
        return 0.3 * jaccard
    
    return 0.0


# ========================================
# 3. BERTScore 核心计算
# ========================================
def compute_bertscore(reference, candidate):
    """
    计算 BERTScore (Precision, Recall, F1)
    
    参数:
        reference: 参考回答（标准答案）
        candidate: 待评估回答（RAG 生成）
    
    返回:
        {"precision": float, "recall": float, "f1": float, 
         "n_reference_tokens": int, "n_candidate_tokens": int,
         "token_detail": [...]}
    
    算法:
        1. 分别对参考和生成做 tokenize
        2. 计算相似度矩阵 (N_ref × N_gen)
        3. 按行(P)/按列(R)取最大值
        4. 取平均 → P 和 R
        5. 算 F1
    """
    ref_tokens = tokenize_chinese(reference)
    can_tokens = tokenize_chinese(candidate)
    
    if not ref_tokens or not can_tokens:
        return {"precision": 0.0, "recall": 0.0, "f1": 0.0,
                "n_reference_tokens": len(ref_tokens), "n_candidate_tokens": len(can_tokens)}
    
    # 计算相似度矩阵
    sim_matrix = []
    for c in can_tokens:
        row = [semantic_similarity(c, r) for r in ref_tokens]
        sim_matrix.append(row)
    
    # Precision: 每个生成 Token 匹配参考中最好的
    # "生成的词都靠谱吗？"
    precision_per_token = [max(row) for row in sim_matrix]
    precision = sum(precision_per_token) / len(can_tokens)
    
    # Recall: 每个参考 Token 被生成中最好的匹配
    # "参考中的要点都被覆盖了吗？"
    recall_per_token = []
    for j in range(len(ref_tokens)):
        recall_per_token.append(max(sim_matrix[i][j] for i in range(len(can_tokens))))
    recall = sum(recall_per_token) / len(ref_tokens)
    
    # F1
    f1 = 2 * precision * recall / (precision + recall + 1e-10)
    
    # Token 级别详情（方便排查）
    token_detail = []
    for i, can in enumerate(can_tokens):
        best_idx = sim_matrix[i].index(max(sim_matrix[i]))
        token_detail.append({
            "token": can,
            "best_match": ref_tokens[best_idx],
            "similarity": round(precision_per_token[i], 4),
        })
    
    return {
        "precision": round(precision, 4),
        "recall": round(recall, 4),
        "f1": round(f1, 4),
        "n_reference_tokens": len(ref_tokens),
        "n_candidate_tokens": len(can_tokens),
        "token_detail": token_detail,
    }


# ========================================
# 4. 真实 BERT 版（回退到哈希版）
# ========================================
def compute_bertscore_real(reference, candidate, model_name="all-MiniLM-L6-v2"):
    """
    使用 HuggingFace sentence-transformers 计算 BERTScore
    
    需要:
        pip install sentence-transformers torch numpy
    
    如果不可用，自动回退到哈希版
    """
    try:
        from sentence_transformers import SentenceTransformer
        import torch
        import numpy as np

        model = SentenceTransformer(model_name)
        
        ref_tokens = tokenize_chinese(reference)
        can_tokens = tokenize_chinese(candidate)
        
        if not ref_tokens or not can_tokens:
            return {"precision": 0.0, "recall": 0.0, "f1": 0.0}
        
        # 编码每个 Token
        ref_embs = model.encode(ref_tokens, convert_to_tensor=True)
        can_embs = model.encode(can_tokens, convert_to_tensor=True)
        
        # 余弦相似度矩阵
        ref_norm = torch.nn.functional.normalize(ref_embs, p=2, dim=1)
        can_norm = torch.nn.functional.normalize(can_embs, p=2, dim=1)
        cos_matrix = torch.mm(can_norm, ref_norm.T)  # N_gen × N_ref
        
        # Precision
        precision = torch.mean(torch.max(cos_matrix, dim=1)[0]).item()
        # Recall
        recall = torch.mean(torch.max(cos_matrix, dim=0)[0]).item()
        # F1
        f1 = 2 * precision * recall / (precision + recall + 1e-10)
        
        return {
            "precision": round(precision, 4),
            "recall": round(recall, 4),
            "f1": round(f1, 4),
            "model": model_name,
            "real_bert": True,
        }
    except ImportError:
        return compute_bertscore(reference, candidate)


# ========================================
# 5. 批量评估
# ========================================
def load_dataset():
    """加载评估数据集"""
    if not os.path.exists(DATA_PATH):
        alt_path = os.path.join(BASE_DIR, "..", "..", "data", "eval_dataset.json")
        if os.path.exists(alt_path):
            with open(alt_path) as f:
                return json.load(f)
        print(f"❌ 数据集不存在: {DATA_PATH}")
        return None
    with open(DATA_PATH, "r", encoding="utf-8") as f:
        return json.load(f)


def ensure_fields(dataset):
    """确保数据集中有 reference 和 answer 字段"""
    for item in dataset:
        if "answer" not in item:
            item["answer"] = item.get("simulated_answer", "")
        if "reference" not in item:
            item["reference"] = item.get("expected_answer", item.get("answer", ""))
    return dataset


def batch_evaluate(dataset, use_real_bert=False):
    """对整个数据集计算 BERTScore"""
    items = dataset  # 不需要取子集
    results = []

    print(f"\n📊 BERTScore {'(real BERT)' if use_real_bert else '(hash-based)'} 批量评估（共 {len(items)} 条）")
    print("=" * 60)

    p_sum, r_sum, f1_sum = 0.0, 0.0, 0.0

    for i, item in enumerate(items):
        ref = item.get("reference", "")
        ans = item.get("answer", "")
        q = item.get("question", "")

        if not ref or not ans:
            print(f"  ⏭️ [{i+1}/{len(items)}] 跳过: {q[:30]}...")
            continue

        if use_real_bert:
            scores = compute_bertscore_real(ref, ans)
        else:
            scores = compute_bertscore(ref, ans)

        results.append({
            "index": i,
            "question": q[:50],
            "precision": scores["precision"],
            "recall": scores["recall"],
            "f1": scores["f1"],
            "n_ref": scores.get("n_reference_tokens", 0),
            "n_can": scores.get("n_candidate_tokens", 0),
            "token_detail": scores.get("token_detail", []),
        })

        p_sum += scores["precision"]
        r_sum += scores["recall"]
        f1_sum += scores["f1"]

        print(f"  ✅ [{i+1}/{len(items)}] {q[:30]}...  P={scores['precision']:.4f}  R={scores['recall']:.4f}  F1={scores['f1']:.4f}")

    n = len(results)
    stats = {
        "count": n,
        "avg_precision": round(p_sum / n, 4),
        "avg_recall": round(r_sum / n, 4),
        "avg_f1": round(f1_sum / n, 4),
    }

    return results, stats


# ========================================
# 6. 演示：单条分析
# ========================================
def demo_single():
    """演示单条 BERTScore 计算"""
    print("\n" + "=" * 60)
    print("  单条 BERTScore 演示")
    print("=" * 60)

    test_cases = [
        {
            "reference": "退货退款一般在收到退货后 3-5 个工作日到账，退款包含商品金额和税费。",
            "candidate": "退款需要 3-5 天到账。",
            "expect": "✅ 简洁版（P高R低，信息不够完整）",
        },
        {
            "reference": "食品属于特殊商品，非质量问题不支持退货。如果商品有质量问题，请在签收后24小时内联系客服并提供照片。",
            "candidate": "食品不能退货。如果有质量问题可以联系客服。",
            "expect": "✅ 简略版（丢失了关键细节：24小时、提供照片）",
        },
        {
            "reference": "普通配送一般 3-5 个工作日送达，加急配送 1-2 个工作日送达。",
            "candidate": "普通配送 3-5 天，加急配送 1-2 天。",
            "expect": "✅ 高相似度（语义几乎一致）",
        },
    ]

    for i, case in enumerate(test_cases):
        scores = compute_bertscore(case["reference"], case["candidate"])
        print(f"\n--- 示例 {i+1}: {case['expect']} ---")
        print(f"参考:   {case['reference']}")
        print(f"生成:   {case['candidate']}")
        print(f"P={scores['precision']:.4f}  R={scores['recall']:.4f}  F1={scores['f1']:.4f}")
        print(f"参考 Token: {scores['n_reference_tokens']}, 生成 Token: {scores['n_candidate_tokens']}")

        # 显示 Token 级匹配详情
        detail = scores.get("token_detail", [])
        if detail:
            low_sim = [d for d in detail if d["similarity"] < 0.5]
            if low_sim:
                print(f"  ⚠️ 低匹配 Token:")
                for d in low_sim:
                    print(f"    '{d['token']}' → 最近匹配 '{d['best_match']}' (sim={d['similarity']:.2f})")


# ========================================
# 7. 主流程
# ========================================
def main():
    import argparse

    parser = argparse.ArgumentParser(description="BERTScore 语义相似度评估")
    parser.add_argument("--demo", action="store_true", help="运行单条演示")
    parser.add_argument("--real", action="store_true", help="使用真实 BERT 模型（需安装 sentence-transformers）")
    args = parser.parse_args()

    if args.demo:
        demo_single()
        return

    # 加载数据集
    dataset = load_dataset()
    if not dataset:
        print("❌ 无法加载数据集")
        sys.exit(1)
    dataset = ensure_fields(dataset)
    print(f"📂 加载数据集: {len(dataset)} 条")

    # 批量评估
    results, stats = batch_evaluate(dataset, use_real_bert=args.real)

    print(f"\n📊 BERTScore 统计:")
    print(f"   总量: {stats['count']} 条")
    print(f"   均 Precision: {stats['avg_precision']:.4f}")
    print(f"   均 Recall:    {stats['avg_recall']:.4f}")
    print(f"   均 F1:        {stats['avg_f1']:.4f}")

    # 趋势解读
    if stats["avg_precision"] > stats["avg_recall"]:
        print(f"\n📈 趋势: P({stats['avg_precision']:.3f}) > R({stats['avg_recall']:.3f})")
        print("   回答整体简洁但可能遗漏了一些参考信息")
    elif stats["avg_precision"] < stats["avg_recall"]:
        print(f"\n📈 趋势: R({stats['avg_recall']:.3f}) > P({stats['avg_precision']:.3f})")
        print("   回答覆盖了参考信息但可能有些冗余")
    else:
        print(f"\n📈 趋势: P ≈ R，信息覆盖均衡")

    # 保存报告
    report = {
        "metadata": {
            "date": time.strftime("%Y-%m-%d %H:%M:%S"),
            "method": "BERTScore",
            "type": "real_bert" if args.real else "hash_based",
            "model": "sentence-transformers" if args.real else "hash+synonym",
        },
        "stats": stats,
        "results": results,
    }

    os.makedirs(os.path.dirname(REPORT_PATH), exist_ok=True)
    with open(REPORT_PATH, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    print(f"\n📄 报告已保存: {REPORT_PATH}")

    print("\n✅ BERTScore 评估完成！")
    print("💡 运行 llm_as_judge.py 可生成交叉验证报告。")


if __name__ == "__main__":
    main()
