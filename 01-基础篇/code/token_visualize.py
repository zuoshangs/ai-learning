"""第1天：Token 可视化 - 看模型如何拆分文本"""

import tiktoken

enc = tiktoken.get_encoding("cl100k_base")


def visualize_tokenization(text):
    """可视化展示文本如何被拆分成 Token"""
    tokens = enc.encode(text)
    decoded_tokens = [enc.decode([t]) for t in tokens]

    print(f"原文: {text}")
    print(f"总 Token 数: {len(tokens)}")
    print(f"总字符数: {len(text)}")
    print(f"Token/字符比: {len(tokens)/len(text):.2f}")
    print()
    print("拆分明细:")
    print("-" * 50)
    for i, (token_id, decoded) in enumerate(zip(tokens, decoded_tokens), 1):
        # 转义特殊字符以便显示
        display = repr(decoded)[1:-1]  # 去掉引号
        print(f"  Token {i:2d} (ID:{token_id:5d}) → '{display}'")
    print("-" * 50)
    print(f"重组后: '{enc.decode(tokens)}'")
    print()


# === 测试用例 ===

print("=" * 60)
print("🔍 测试 1：英文句子")
print("=" * 60)
visualize_tokenization("Hello! How are you doing today?")

print("=" * 60)
print("🔍 测试 2：中文句子")
print("=" * 60)
visualize_tokenization("你好，今天过得怎么样？")

print("=" * 60)
print("🔍 测试 3：代码片段")
print("=" * 60)
visualize_tokenization("if (count > 10) { return true; }")

print("=" * 60)
print("🔍 测试 4：英文词根拆分")
print("=" * 60)
visualize_tokenization("unbelievable tokenization")

print("=" * 60)
print("🔍 测试 5：URL 和特殊符号")
print("=" * 60)
visualize_tokenization("https://api.example.com/v1/chat/completions")
