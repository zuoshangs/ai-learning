package com.ai.learning.knowledge.model;

/**
 * 搜索结果条目
 */
public class SearchResultItem {

    private int rank;
    private double score;
    private String content;
    private int contentLength;
    private String source;
    private String method;          // 检索方式: semantic / keyword / hybrid
    private Double rerankScore;     // Reranker 评分 (1-5)
    private String parentContent;   // 父文档内容（父文档检索时填充）

    // ===== Getters & Setters =====

    public int getRank() { return rank; }
    public SearchResultItem setRank(int rank) { this.rank = rank; return this; }

    public double getScore() { return score; }
    public SearchResultItem setScore(double score) { this.score = score; return this; }

    public String getContent() { return content; }
    public SearchResultItem setContent(String content) {
        this.content = content;
        this.contentLength = content != null ? content.length() : 0;
        return this;
    }

    public int getContentLength() { return contentLength; }
    public SearchResultItem setContentLength(int contentLength) { this.contentLength = contentLength; return this; }

    public String getSource() { return source; }
    public SearchResultItem setSource(String source) { this.source = source; return this; }

    public String getMethod() { return method; }
    public SearchResultItem setMethod(String method) { this.method = method; return this; }

    public Double getRerankScore() { return rerankScore; }
    public SearchResultItem setRerankScore(Double rerankScore) { this.rerankScore = rerankScore; return this; }

    public String getParentContent() { return parentContent; }
    public SearchResultItem setParentContent(String parentContent) { this.parentContent = parentContent; return this; }
}
