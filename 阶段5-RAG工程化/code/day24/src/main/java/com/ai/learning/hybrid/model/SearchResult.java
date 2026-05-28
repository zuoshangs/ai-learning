package com.ai.learning.hybrid.model;

/**
 * 搜索结果实体
 */
public class SearchResult implements Comparable<SearchResult> {

    private int rank;
    private double score;
    private String content;
    private int contentLength;
    private String source;
    private String method; // semantic / keyword / hybrid
    private double semanticScore;
    private double keywordScore;
    private double rerankScore;

    public SearchResult() {}

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

    public String getMethod() { return method; }
    public SearchResult setMethod(String method) { this.method = method; return this; }

    public double getSemanticScore() { return semanticScore; }
    public SearchResult setSemanticScore(double semanticScore) { this.semanticScore = semanticScore; return this; }

    public double getKeywordScore() { return keywordScore; }
    public SearchResult setKeywordScore(double keywordScore) { this.keywordScore = keywordScore; return this; }

    public double getRerankScore() { return rerankScore; }
    public SearchResult setRerankScore(double rerankScore) { this.rerankScore = rerankScore; return this; }

    @Override
    public int compareTo(SearchResult o) {
        return Double.compare(o.score, this.score);
    }
}
