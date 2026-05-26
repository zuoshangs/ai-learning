"""第1天：中英文 Token 对比 + 成本估算"""

import tiktoken

enc = tiktoken.get_encoding("cl100k_base")


def analyze_text(label, text):
    """分析一段文本的 Token 消耗并估算成本"""
    tokens = enc.encode(text)
    # 以 DeepSeek V4 价格为例（实际价格可能有变动）
    input_cost = len(tokens) * 0.0000005   # 输入: $0.5/1M tokens
    output_cost = len(tokens) * 0.000002   # 输出: $2/1M tokens

    print(f"\n{'='*50}")
    print(f"📝 {label}")
    print(f"{'='*50}")
    display = text[:60] + "..." if len(text) > 60 else text
    print(f"原文: {display}")
    print(f"字符数: {len(text):>8,}")
    print(f"Token 数: {len(tokens):>8,}")
    print(f"Token/字符比: {len(tokens)/len(text):>8.2f}")
    print(f"估算输入成本: ${input_cost:<10.6f}")
    print(f"估算输出成本: ${output_cost:<10.6f}")
    return len(tokens)


print("""
╔════════════════════════════════════════════════════╗
║            中英文 Token 成本对比实验              ║
╚════════════════════════════════════════════════════╝
""")

# ===== 实验1：同一句话 =====
print("\n🆚 实验1：同一语义的中英文对比")
print("-" * 55)

en_short = "Machine learning is transforming the world"
cn_short = "机器学习正在改变世界"

en_t = analyze_text("英文版", en_short)
cn_t = analyze_text("中文版", cn_short)
print(f"\n  → 中文 Token 是英文的 {cn_t/en_t:.1f} 倍")

# ===== 实验2：同一篇文章 =====
print("\n\n🆚 实验2：同一篇文章的中英文版")
print("-" * 55)

english_article = """Artificial intelligence has made remarkable progress in recent years.
Large language models can now understand and generate human-like text with
impressive accuracy. These models are being used in various applications,
from chatbots to code generation. The technology continues to evolve rapidly,
with new breakthroughs announced almost every week."""

chinese_article = """人工智能近年来取得了显著的进展。大语言模型现在能够理解和生成
类似人类的文本，准确度令人印象深刻。这些模型正被应用于各种场景，
从聊天机器人到代码生成。这项技术正在快速发展，几乎每周都有新的突破宣布。"""

en_t2 = analyze_text("英文版", english_article)
cn_t2 = analyze_text("中文版", chinese_article)
print(f"\n  → 中文 Token 是英文的 {cn_t2/en_t2:.1f} 倍")

# ===== 实验3：高频词 vs 生僻词 =====
print("\n\n🆚 实验3：常见词 vs 生僻词")
print("-" * 55)

common = "The quick brown fox jumps over the lazy dog"  # 常见英文
uncommon = "Pneumonoultramicroscopicsilicovolcanoconiosis"  # 最长的英文单词

analyze_text("常见英文", common)
analyze_text("生僻英文（超长单词）", uncommon)

# ===== 实验4：混合使用英文术语 =====
print("\n\n🆚 实验4：纯中文 vs 中英混用（提示词优化技巧）")
print("-" * 55)

pure_cn = "请实现一个检索增强生成系统，用于企业知识库问答"
mixed = "请实现一个 RAG 系统，用于企业知识库问答"

cn_t4 = analyze_text("纯中文版", pure_cn)
mixed_t = analyze_text("中英混用版", mixed)
saving = (cn_t4 - mixed_t) / cn_t4 * 100
print(f"\n  → 混用英文术语节省了 {saving:.0f}% 的 Token！")
