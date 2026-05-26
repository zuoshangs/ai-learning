# Python 快速指南

## 基础语法

### 变量与数据类型

```python
# 基本类型
name = "Alice"       # 字符串
age = 25             # 整数
height = 1.68        # 浮点数
is_student = True    # 布尔值

# 集合类型
fruits = ["apple", "banana", "cherry"]  # 列表
person = {"name": "Bob", "age": 30}     # 字典
numbers = {1, 2, 3}                     # 集合
```

### 控制流

```python
# 条件判断
if age >= 18:
    print("成年")
elif age >= 12:
    print("青少年")
else:
    print("儿童")

# 循环
for fruit in fruits:
    print(fruit)

for i in range(5):
    print(i)
```

### 函数

```python
def greet(name, greeting="你好"):
    return f"{greeting}，{name}！"

# 可变参数
def sum_all(*args):
    return sum(args)

# 关键字参数
def create_user(**kwargs):
    return kwargs
```

## 面向对象编程

```python
class Dog:
    def __init__(self, name, breed):
        self.name = name
        self.breed = breed

    def bark(self):
        return f"{self.name} 在汪汪叫！"

# 继承
class Poodle(Dog):
    def bark(self):
        return f"{self.name} 在优雅地叫！"
```

## 文件操作

```python
# 读取文件
with open("file.txt", "r", encoding="utf-8") as f:
    content = f.read()

# 写入文件
with open("output.txt", "w", encoding="utf-8") as f:
    f.write("Hello, World!")

# 逐行读取
with open("file.txt", "r") as f:
    for line in f:
        print(line.strip())
```

## 常用标准库

| 模块 | 用途 |
|------|------|
| os | 操作系统接口 |
| sys | 系统参数 |
| json | JSON 处理 |
| re | 正则表达式 |
| datetime | 日期时间 |
| collections | 高级容器类型 |
| pathlib | 路径操作 |
| typing | 类型提示 |
