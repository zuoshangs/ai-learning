"""第1天：上下文窗口使用率计算器"""

import tiktoken

enc = tiktoken.get_encoding("cl100k_base")


def context_usage(text, model_name="DeepSeek V4", max_tokens=128000, reserved_output=4000):
    """
    计算文本占用上下文窗口的百分比
    
    参数:
        text: 输入文本
        model_name: 模型名称
        max_tokens: 模型最大上下文
        reserved_output: 为模型输出预留的 Token
    """
    input_tokens = len(enc.encode(text))
    available = max_tokens - reserved_output
    usage_pct = (input_tokens / available) * 100

    print(f"\n{'='*55}")
    print(f"📊 上下文窗口分析 — {model_name}")
    print(f"{'='*55}")
    print(f" 输入文本 Token:      {input_tokens:>10,}")
    print(f" 预留输出 Token:      {reserved_output:>10,}")
    print(f" 最大上下文:          {max_tokens:>10,}")
    print(f" 可用输入空间:        {available:>10,}")
    print(f" 使用率:              {usage_pct:>9.1f}%")
    print(f"{'='*55}")

    if usage_pct > 90:
        print("  ⚠️  警告：内容接近上限！模型容易丢失信息")
        print("  ⚠️  强烈建议精简输入或升级模型")
    elif usage_pct > 70:
        print("  ⚡ 注意：内容较多，建议适当精简")
    elif usage_pct > 50:
        print("  📗 合理范围，表现良好")
    else:
        print("  ✅ 轻松应对，模型有充足空间处理")

    return usage_pct


# ===== 测试不同场景 =====

print("""
╔══════════════════════════════════════════════════╗
║             上下文窗口使用率测试                  ║
╚══════════════════════════════════════════════════╝
""")

# 场景1：简短对话
context_usage(
    "简短对话",
    "你好，请问今天天气怎么样？",
    max_tokens=128000
)

# 场景2：单篇技术文档（约5000字）
short_doc = "本文档描述了系统的架构设计和技术选型方案。" * 300
context_usage(
    "单篇技术文档（300句）",
    short_doc,
    max_tokens=128000
)

# 场景3：多轮对话历史
conversation = (
    "用户: 帮我查一下上个月的销售数据\n"
    "助手: 好的，让我查一下数据库。\n"
    "用户: 按区域分组看一下\n"
    "助手: 以下是按区域的销售数据...\n"
) * 50  # 200轮对话
context_usage(
    "长对话历史（200轮）",
    conversation,
    max_tokens=128000
)

# 场景4：大型代码库上下文
code_context = """
    这是一个 Spring Boot 项目的主要类文件，包含控制器、服务层、数据访问层
    以及相关的配置文件和资源文件。在代码审查时需要提供完整的项目上下文。
""".strip() * 500
context_usage(
    "大型代码库上下文",
    code_context,
    max_tokens=128000
)

# 场景5：不同模型对比
print("\n\n" + "=" * 55)
print("🔄 不同模型上下文窗口对比（以场景4为例）")
print("=" * 55)

for model, window in [("DeepSeek V4", 128000), ("GPT-4", 128000),
                       ("Claude 3.5", 200000), ("Gemini 1.5", 2000000)]:
    context_usage(f"{model} (窗口: {window//1000}K)", code_context,
                  max_tokens=window)

# 使用建议
print("\n" + "=" * 55)
print("💡 使用建议总结")
print("=" * 55)
print("""
  1. 控制输入占窗口的 60-70%，给输出留空间
  2. 核心信息放开头和结尾（模型的注意力最强）
  3. 长文档应该用 RAG（分片+检索），不要全塞
  4. 不同模型搜索同一窗口大小时效果不同
  5. 预留输出空间不足会导致回答被截断
""")
