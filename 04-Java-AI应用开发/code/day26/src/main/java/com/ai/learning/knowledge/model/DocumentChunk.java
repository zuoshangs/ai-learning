package com.ai.learning.knowledge.model;

/**
 * 文档块 — 切分后的最小检索单元
 */
public class DocumentChunk {

    private String id;
    private String docId;          // 所属文档 ID
    private String parentDocId;    // 父文档 ID（父文档检索用）
    private String content;
    private int chunkIndex;        // 块序号
    private int totalParentChunks; // 父文档包含的总块数（父文档检索用）
    private String source;

    public DocumentChunk() {}

    public DocumentChunk(String id, String docId, String content, int chunkIndex, String source) {
        this.id = id;
        this.docId = docId;
        this.parentDocId = docId;   // 默认父文档 = 所属文档
        this.content = content;
        this.chunkIndex = chunkIndex;
        this.source = source;
    }

    // ===== Getters & Setters =====

    public String getId() { return id; }
    public DocumentChunk setId(String id) { this.id = id; return this; }

    public String getDocId() { return docId; }
    public DocumentChunk setDocId(String docId) { this.docId = docId; return this; }

    public String getParentDocId() { return parentDocId; }
    public DocumentChunk setParentDocId(String parentDocId) { this.parentDocId = parentDocId; return this; }

    public String getContent() { return content; }
    public DocumentChunk setContent(String content) { this.content = content; return this; }

    public int getChunkIndex() { return chunkIndex; }
    public DocumentChunk setChunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; return this; }

    public int getTotalParentChunks() { return totalParentChunks; }
    public DocumentChunk setTotalParentChunks(int totalParentChunks) { this.totalParentChunks = totalParentChunks; return this; }

    public String getSource() { return source; }
    public DocumentChunk setSource(String source) { this.source = source; return this; }
}
