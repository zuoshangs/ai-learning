package com.ai.learning.vector.model;

import java.util.List;

/**
 * 检索响应 — 包含检索结果 + 可选的 RAG 答案
 */
public class SearchResponse {

    private String query;
    private String mode;            // top-k / threshold / window / compare
    private int totalResults;
    private List<SearchResult> results;
    private String ragAnswer;       // 可选：RAG 增强回答

    public SearchResponse() {}

    public String getQuery() { return query; }
    public SearchResponse setQuery(String query) { this.query = query; return this; }

    public String getMode() { return mode; }
    public SearchResponse setMode(String mode) { this.mode = mode; return this; }

    public int getTotalResults() { return totalResults; }
    public SearchResponse setTotalResults(int totalResults) { this.totalResults = totalResults; return this; }

    public List<SearchResult> getResults() { return results; }
    public SearchResponse setResults(List<SearchResult> results) { this.results = results; return this; }

    public String getRagAnswer() { return ragAnswer; }
    public SearchResponse setRagAnswer(String ragAnswer) { this.ragAnswer = ragAnswer; return this; }
}
