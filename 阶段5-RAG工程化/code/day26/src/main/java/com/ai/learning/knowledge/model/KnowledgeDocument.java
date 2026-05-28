package com.ai.learning.knowledge.model;

/**
 * 知识库文档 — 完整文档实体
 * 每个文档可包含多个 Chunk
 */
public class KnowledgeDocument {

    private String id;
    private String title;
    private String content;
    private String source;       // 来源（文件名/URL）
    private String docType;      // 文档类型（pdf/txt/md）
    private long createdAt;
    private int totalChunks;     // 切分后的块数

    public KnowledgeDocument() {}

    public KnowledgeDocument(String id, String title, String content, String source, String docType) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.source = source;
        this.docType = docType;
        this.createdAt = System.currentTimeMillis();
    }

    // ===== Getters & Setters =====

    public String getId() { return id; }
    public KnowledgeDocument setId(String id) { this.id = id; return this; }

    public String getTitle() { return title; }
    public KnowledgeDocument setTitle(String title) { this.title = title; return this; }

    public String getContent() { return content; }
    public KnowledgeDocument setContent(String content) { this.content = content; return this; }

    public String getSource() { return source; }
    public KnowledgeDocument setSource(String source) { this.source = source; return this; }

    public String getDocType() { return docType; }
    public KnowledgeDocument setDocType(String docType) { this.docType = docType; return this; }

    public long getCreatedAt() { return createdAt; }
    public KnowledgeDocument setCreatedAt(long createdAt) { this.createdAt = createdAt; return this; }

    public int getTotalChunks() { return totalChunks; }
    public KnowledgeDocument setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; return this; }
}
