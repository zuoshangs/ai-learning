"""
Day 45-进阶 · 评估数据集构建器
基于智能客服平台的知识库文档，生成 RAG 评估数据集。
"""

import json
import os

# ========================================
# 知识库文档（来自 KnowledgeBaseService.seedDocuments()）
# ========================================
KNOWLEDGE_DOCS = [
    {
        "id": "doc-1",
        "title": "退货政策",
        "content": "本平台支持30天内无理由退货。商品需保持原状、配件齐全、包装完整。特殊商品（如食品、内衣）除外。",
        "category": "售后"
    },
    {
        "id": "doc-2",
        "title": "退款流程",
        "content": "退款将在我们收到退货后的5-7个工作日内原路返回。使用信用卡支付的退款需要7-10个工作日。退款金额包含商品原价和税费。",
        "category": "售后"
    },
    {
        "id": "doc-3",
        "title": "物流配送",
        "content": "标准配送3-5个工作日，满99元包邮。加急配送1-2个工作日，需加收15元。当日达仅限部分城市，需在11:00前下单。",
        "category": "物流"
    },
    {
        "id": "doc-4",
        "title": "会员等级",
        "content": "会员分为普通、银卡、金卡、钻石卡四级。银卡需年消费1000元（9.5折），金卡3000元（9折），钻石卡10000元（8.5折）。",
        "category": "会员"
    },
    {
        "id": "doc-5",
        "title": "联系人工客服",
        "content": "您可以拨打客服热线 400-123-4567（工作日9:00-21:00），或发送邮件至 support@example.com。在线客服在APP内随时可用。",
        "category": "服务"
    },
    {
        "id": "doc-6",
        "title": "账号安全",
        "content": "建议您设置强密码（包含大小写字母+数字+特殊字符），开启双重验证。如发现异常登录，请立即修改密码并联系客服。",
        "category": "账号"
    },
    {
        "id": "doc-7",
        "title": "优惠券使用",
        "content": "优惠券可在结算页面使用。每笔订单限用一张。部分商品不参与优惠活动。优惠券有效期为领取后7天。",
        "category": "促销"
    }
]

# ========================================
# 评估数据集：question → ground_truth + relevant_doc_ids
# ========================================
EVAL_QUESTIONS = [
    # ---- 退货相关 ----
    {
        "question": "我想退货，多久之内可以退？",
        "ground_truth": "30天内无理由退货，商品需保持原状、配件齐全、包装完整，特殊商品除外。",
        "relevant_doc_ids": ["doc-1"],
        "category": "售后"
    },
    {
        "question": "退货后什么时候收到退款？",
        "ground_truth": "收到退货后5-7个工作日内原路返回，信用卡支付需7-10个工作日。",
        "relevant_doc_ids": ["doc-2"],
        "category": "售后"
    },
    {
        "question": "食品可以退货吗？",
        "ground_truth": "特殊商品如食品、内衣等不支持退货。普通商品30天内可以无理由退货。",
        "relevant_doc_ids": ["doc-1"],
        "category": "售后"
    },
    {
        "question": "退款金额包括哪些？",
        "ground_truth": "退款金额包含商品原价和税费，原路返回。",
        "relevant_doc_ids": ["doc-2"],
        "category": "售后"
    },

    # ---- 物流相关 ----
    {
        "question": "普通配送要多久到？",
        "ground_truth": "标准配送3-5个工作日，满99元包邮。加急配送1-2个工作日需加收15元。",
        "relevant_doc_ids": ["doc-3"],
        "category": "物流"
    },
    {
        "question": "加急配送多少钱？",
        "ground_truth": "加急配送1-2个工作日，需加收15元。",
        "relevant_doc_ids": ["doc-3"],
        "category": "物流"
    },
    {
        "question": "满多少包邮？",
        "ground_truth": "满99元包邮。",
        "relevant_doc_ids": ["doc-3"],
        "category": "物流"
    },
    {
        "question": "当日达什么时候下单才行？",
        "ground_truth": "当日达仅限部分城市，需在11:00前下单。",
        "relevant_doc_ids": ["doc-3"],
        "category": "物流"
    },

    # ---- 会员相关 ----
    {
        "question": "会员分几个等级？",
        "ground_truth": "会员分为普通、银卡、金卡、钻石卡四级。",
        "relevant_doc_ids": ["doc-4"],
        "category": "会员"
    },
    {
        "question": "金卡会员要消费多少？",
        "ground_truth": "金卡需年消费3000元，享受9折优惠。",
        "relevant_doc_ids": ["doc-4"],
        "category": "会员"
    },
    {
        "question": "钻石卡打几折？",
        "ground_truth": "钻石卡需年消费10000元，享受8.5折优惠。",
        "relevant_doc_ids": ["doc-4"],
        "category": "会员"
    },

    # ---- 客服联系 ----
    {
        "question": "怎么联系人工客服？",
        "ground_truth": "拨打客服热线400-123-4567（工作日9:00-21:00），或发邮件至support@example.com。",
        "relevant_doc_ids": ["doc-5"],
        "category": "服务"
    },
    {
        "question": "客服热线几点到几点？",
        "ground_truth": "工作日9:00-21:00。",
        "relevant_doc_ids": ["doc-5"],
        "category": "服务"
    },

    # ---- 账号安全 ----
    {
        "question": "怎么设置安全的密码？",
        "ground_truth": "建议设置强密码，包含大小写字母+数字+特殊字符，并开启双重验证。",
        "relevant_doc_ids": ["doc-6"],
        "category": "账号"
    },
    {
        "question": "账号被盗了怎么办？",
        "ground_truth": "立即修改密码并联系客服。建议开启双重验证增强安全性。",
        "relevant_doc_ids": ["doc-6"],
        "category": "账号"
    },

    # ---- 优惠券 ----
    {
        "question": "优惠券怎么用？",
        "ground_truth": "在结算页面使用，每笔订单限用一张，有效期为领取后7天。",
        "relevant_doc_ids": ["doc-7"],
        "category": "促销"
    },
    {
        "question": "优惠券有效期多久？",
        "ground_truth": "领取后7天。",
        "relevant_doc_ids": ["doc-7"],
        "category": "促销"
    },

    # ---- 综合多文档问题 ----
    {
        "question": "我买的东西想退掉，退款回哪里？",
        "ground_truth": "30天内可以退，收到退货后5-7个工作日内原路返回。",
        "relevant_doc_ids": ["doc-1", "doc-2"],
        "category": "综合"
    },
    {
        "question": "我是金卡会员，退货有什么特殊政策吗？",
        "ground_truth": "金卡会员享受9折优惠，退货政策与普通会员一致—30天内无理由退货。",
        "relevant_doc_ids": ["doc-4", "doc-1"],
        "category": "综合"
    },
    {
        "question": "优惠券和会员折扣能一起用吗？",
        "ground_truth": "优惠券每笔订单限用一张，部分商品不参与优惠活动。会员折扣与优惠券能否叠加需看具体活动规则。",
        "relevant_doc_ids": ["doc-7", "doc-4"],
        "category": "促销"
    }
]


def build_dataset():
    """构建完整的评估数据集，输出为 JSON 文件。"""
    dataset = []
    for item in EVAL_QUESTIONS:
        # 获取相关的知识库文档内容
        contexts = []
        for doc_id in item["relevant_doc_ids"]:
            doc = next(d for d in KNOWLEDGE_DOCS if d["id"] == doc_id)
            contexts.append(doc["content"])

        entry = {
            "question": item["question"],
            "ground_truth": item["ground_truth"],
            "contexts": contexts,
            "category": item["category"],
            "relevant_doc_titles": [
                next(d["title"] for d in KNOWLEDGE_DOCS if d["id"] == did)
                for did in item["relevant_doc_ids"]
            ]
        }
        dataset.append(entry)

    return dataset


def save_dataset(dataset, path):
    """保存数据集到 JSON 文件。"""
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(dataset, f, ensure_ascii=False, indent=2)
    print(f"📝 数据集已保存: {path}")
    print(f"   总样本: {len(dataset)} 条")
    print(f"   覆盖分类: {list(dict.fromkeys(d['category'] for d in dataset))}")


if __name__ == "__main__":
    dataset = build_dataset()

    # 打印统计
    categories = {}
    for d in dataset:
        cat = d["category"]
        categories[cat] = categories.get(cat, 0) + 1

    print("=" * 50)
    print("评估数据集统计")
    print("=" * 50)
    print(f"总样本数: {len(dataset)}")
    print(f"按分类:")
    for cat, count in sorted(categories.items()):
        print(f"  {cat}: {count} 条")
    print()

    # 打印前 5 条预览
    print("前 5 条预览:")
    for i, d in enumerate(dataset[:5]):
        print(f"  [{i+1}] Q: {d['question']}")
        print(f"      相关文档: {', '.join(d['relevant_doc_titles'])}")
        print()

    # 保存
    save_dataset(dataset, "data/eval_dataset.json")
