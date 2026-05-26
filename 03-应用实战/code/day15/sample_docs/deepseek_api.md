# DeepSeek API 使用指南

## 概述

DeepSeek 提供 OpenAI 兼容的 API 接口，支持文本生成、对话等功能。
其核心模型 DeepSeek-V2 和 DeepSeek-Chat 在多项基准测试中表现出色。

## API 配置

### 获取 API Key
1. 访问 DeepSeek 官网注册账号
2. 在控制台中创建 API Key
3. 保存 API Key 到安全位置

### 基础配置

```python
from openai import OpenAI

client = OpenAI(
    api_key="your-deepseek-api-key",
    base_url="https://api.deepseek.com/v1"
)
```

### 对话补全

```python
response = client.chat.completions.create(
    model="deepseek-chat",
    messages=[
        {"role": "system", "content": "你是一个助手"},
        {"role": "user", "content": "你好"}
    ],
    temperature=0.7,
    max_tokens=2048,
)
```

### 流式输出

```python
stream = client.chat.completions.create(
    model="deepseek-chat",
    messages=[...],
    stream=True,
)
for chunk in stream:
    print(chunk.choices[0].delta.content, end="")
```

## 注意事项

1. API Key 请妥善保管，不要提交到版本控制
2. 使用环境变量存储敏感信息：`export DEEPSEEK_API_KEY='your-key'`
3. 注意 API 调用配额和费用
4. 国内用户可以直接访问，无需代理
