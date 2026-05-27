package com.ai.learning.vector.model;

/**
 * 单条检索结果
 */
public class SearchResult {

    private int rank;
    private double score;
    private String content;
    private int contentLength;
    private String source;
    private String chunkStrategy;
    private String chunkId;

    public SearchResult() {}

    // top level getters/setters with fluent interface
    public int getRank() { return rank; }
    public SearchResult setRank(int rank) { this.rank = rank; return this; }

    public double getScore() { return score; }
    public SearchResult setScore(double score) { this.score = score; return this; }

    public String getContent() { return content; }
    public SearchResult setContent(String content) { this.content = content; return this; }

    public int getContentLength() { return contentLength; }
    public SearchResult setContentLength(int contentLength) { this.contentLength = contentLength; return this; }

    public String getSource() { return source; }
    public SearchResult setSource(String source) { this.source = source; return this; }

    public String getChunkStrategy() { return chunkStrategy; }
    public SearchResult setChunkStrategy(String chunkStrategy) { this.chunkStrategy = chunkStrategy; return this; }

    public String getChunkId() { return chunkId; }
    public SearchResult setChunkId(String chunkId) { this.chunkId = chunkId; return this; }
}
