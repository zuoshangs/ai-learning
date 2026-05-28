package com.ai.learning.knowledge.model;

import java.util.List;

/**
 * 搜索请求
 */
public class SearchRequest {

    private String query;
    private int topK = 5;
    private boolean useRewrite = true;
    private boolean useHyde = false;
    private boolean useParentDoc = true;
    private boolean useHybridSearch = true;
    private boolean useReranker = true;

    // ===== Getters & Setters =====

    public String getQuery() { return query; }
    public SearchRequest setQuery(String query) { this.query = query; return this; }

    public int getTopK() { return topK; }
    public SearchRequest setTopK(int topK) { this.topK = topK; return this; }

    public boolean isUseRewrite() { return useRewrite; }
    public SearchRequest setUseRewrite(boolean useRewrite) { this.useRewrite = useRewrite; return this; }

    public boolean isUseHyde() { return useHyde; }
    public SearchRequest setUseHyde(boolean useHyde) { this.useHyde = useHyde; return this; }

    public boolean isUseParentDoc() { return useParentDoc; }
    public SearchRequest setUseParentDoc(boolean useParentDoc) { this.useParentDoc = useParentDoc; return this; }

    public boolean isUseHybridSearch() { return useHybridSearch; }
    public SearchRequest setUseHybridSearch(boolean useHybridSearch) { this.useHybridSearch = useHybridSearch; return this; }

    public boolean isUseReranker() { return useReranker; }
    public SearchRequest setUseReranker(boolean useReranker) { this.useReranker = useReranker; return this; }
}
