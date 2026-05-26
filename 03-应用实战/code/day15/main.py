#!/usr/bin/env python3
"""
Personal Knowledge Base Q&A System — 顶层入口
===============================================
用法:
    python main.py build         # 构建知识库
    python main.py qa            # 交互式问答
    python main.py search "xxx"  # 搜索
    python main.py info          # 系统信息
    python main.py sample        # 创建示例文档
"""

import sys
import os

# 确保能导入 knowledge_base 包
_project_root = os.path.dirname(os.path.abspath(__file__))
if _project_root not in sys.path:
    sys.path.insert(0, _project_root)

if __name__ == "__main__":
    from knowledge_base.main import main
    main()
