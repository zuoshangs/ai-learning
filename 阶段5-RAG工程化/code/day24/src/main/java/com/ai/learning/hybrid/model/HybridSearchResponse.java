package com.ai.learning.hybrid.model;

import java.util.List;

public class HybridSearchResponse {

    private String query;
    private String mode;
    private int totalResults;
    private long durationMs;
    private List<SearchResult> results;
    private String ragAnswer;

    public HybridSearchResponse() {}

    public String getQuery() { return query; }
    public HybridSearchResponse setQuery(String query) { this.query = query; return this; }

    public String getMode() { return mode; }
    public HybridSearchResponse setMode(String mode) { this.mode = mode; return this; }

    public int getTotalResults() { return totalResults; }
    public HybridSearchResponse setTotalResults(int totalResults) { this.totalResults = totalResults; return this; }

    public long getDurationMs() { return durationMs; }
    public HybridSearchResponse setDurationMs(long durationMs) { this.durationMs = durationMs; return this; }

    public List<SearchResult> getResults() { return results; }
    public HybridSearchResponse setResults(List<SearchResult> results) { this.results = results; return this; }

    public String getRagAnswer() { return ragAnswer; }
    public HybridSearchResponse setRagAnswer(String ragAnswer) { this.ragAnswer = ragAnswer; return this; }
}
