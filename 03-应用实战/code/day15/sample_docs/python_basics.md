# Python 基础入门

## 什么是 Python？

Python 是一种高级、解释型、面向对象的编程语言，由 Guido van Rossum 于 1991 年创建。
Python 的设计哲学强调代码的可读性和简洁性，使用缩进来定义代码块。

## Python 的特点

1. **易学易用**：Python 语法简洁清晰，适合初学者入门。
2. **丰富的标准库**：Python 内置了大量实用模块，覆盖文件操作、网络通信、数据处理等。
3. **跨平台**：Python 可以在 Windows、macOS、Linux 等主流操作系统上运行。
4. **强大的社区生态**：PyPI 上有超过 40 万个第三方包。

## 基本数据类型

- `int`：整数，如 `42`
- `float`：浮点数，如 `3.14`
- `str`：字符串，如 `"Hello"`
- `bool`：布尔值，`True` 或 `False`
- `list`：列表，如 `[1, 2, 3]`
- `dict`：字典，如 `{"key": "value"}`

## 控制流

### 条件判断
```python
if x > 0:
    print("正数")
elif x == 0:
    print("零")
else:
    print("负数")
```

### 循环
```python
# for 循环
for i in range(5):
    print(i)

# while 循环
while count < 10:
    print(count)
    count += 1
```
