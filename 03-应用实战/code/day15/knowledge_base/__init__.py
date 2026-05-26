"""
Personal Knowledge Base Q&A System
==================================
基于 RAG（检索增强生成）的个人知识库问答系统。
支持加载本地 Markdown/文本文件，通过语义检索和 LLM 生成回答。

模块:
    config           — 配置管理
    document_loader  — 文档加载与分块
    embeddings       — 文本嵌入生成
    vector_store     — 向量存储与检索
    qa_engine        — 问答引擎（调用 LLM）
    main             — CLI 入口
"""

__version__ = "1.0.0"
