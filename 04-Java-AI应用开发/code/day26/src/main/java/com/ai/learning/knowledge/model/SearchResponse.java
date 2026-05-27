package com.ai.learning.knowledge.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 搜索响应
 */
public class SearchResponse {

    private String query;
    private String rewrittenQuery;      // 重写后的查询
    private String hydeHypothesis;       // HyDE 假设回答
    private String pipeline;             // 使用的管线描述
    private List<SearchResultItem> results;
    private String answer;               // LLM 基于检索结果的回答
    private long durationMs;
    private Map<String, Object> metadata = new HashMap<>();

    // ===== Getters & Setters =====

    public String getQuery() { return query; }
    public SearchResponse setQuery(String query) { this.query = query; return this; }

    public String getRewrittenQuery() { return rewrittenQuery; }
    public SearchResponse setRewrittenQuery(String rewrittenQuery) { this.rewrittenQuery = rewrittenQuery; return this; }

    public String getHydeHypothesis() { return hydeHypothesis; }
    public SearchResponse setHydeHypothesis(String hydeHypothesis) { this.hydeHypothesis = hydeHypothesis; return this; }

    public String getPipeline() { return pipeline; }
    public SearchResponse setPipeline(String pipeline) { this.pipeline = pipeline; return this; }

    public List<SearchResultItem> getResults() { return results; }
    public SearchResponse setResults(List<SearchResultItem> results) { this.results = results; return this; }

    public String getAnswer() { return answer; }
    public SearchResponse setAnswer(String answer) { this.answer = answer; return this; }

    public long getDurationMs() { return durationMs; }
    public SearchResponse setDurationMs(long durationMs) { this.durationMs = durationMs; return this; }

    public Map<String, Object> getMetadata() { return metadata; }
    public SearchResponse setMetadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
    public SearchResponse addMetadata(String key, Object value) { this.metadata.put(key, value); return this; }
}
