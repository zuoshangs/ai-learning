"""第1天：Token 基础计数演示"""

import tiktoken

# 初始化编码器（GPT-4 使用的编码方案，主流模型通用）
enc = tiktoken.get_encoding("cl100k_base")

# 测试不同文本
texts = [
    "Hello, how are you today?",
    "你好，今天怎么样？",
    "Machine learning is transforming the world.",
    "def fibonacci(n): return n if n <= 1 else fibonacci(n-1) + fibonacci(n-2)",
]

for text in texts:
    tokens = enc.encode(text)
    print(f"原文: {text}")
    print(f"Token 数量: {len(tokens)}")
    print(f"字符数: {len(text)}")
    print(f"Token IDs: {tokens}")
    print("-" * 60)
