# Spring AI 向量数据库集成指南

## 支持的向量数据库

Spring AI 内置支持以下向量数据库：

1. **PgVector** (PostgreSQL 扩展) — 最常用，与 Spring JDBC 无缝集成
2. **Chroma** — 轻量级，适合开发环境
3. **Redis** — 缓存 + 向量混合
4. **MongoDB Atlas** — 文档数据库 + 向量搜索
5. **Pinecone** — 纯云向量数据库
6. **Qdrant** — Rust 编写，高性能
7. **Weaviate** — 云原生
8. **Milvus** — 大规模分布式

## PgVector 快速配置

1. 启动 PostgreSQL（需安装 pgvector 扩展）
2. 创建数据库：`CREATE DATABASE vectordb;`
3. Spring Boot 配置：
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/vectordb
    username: vector
    password: vector123
```

## 相似度检索流程

用户查询 → Embedding 向量化 → PgVector 相似度搜索 → 返回 Top-K 结果

Spring AI 自动处理向量化，开发者只需调用 `vectorStore.similaritySearch(SearchRequest)`。

## 向量数据库 vs 传统数据库

| 特性 | 传统数据库 (SQL) | 向量数据库 (PgVector) |
|-----|-----------------|---------------------|
| 查询方式 | 精确匹配 (WHERE) | 语义相似度 |
| 索引类型 | B-tree | HNSW / IVFFlat |
| 数据类型 | 结构化数据 | 向量嵌入 (float[]) |
| 搜索类型 | 关键词 | 语义/模糊搜索 |
| 适用场景 | 事务系统 | AI/ML 应用 |
