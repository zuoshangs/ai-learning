package com.ai.learning.cache.model;

/**
 * Response from the semantic cache.
 */
public class CacheResponse {
    private boolean hit;
    private String source;   // "cache" or "llm"
    private String response;
    private double similarity;
    private String cachedQuery;  // the matching cache entry query
    private String model;
    private long latencyMs;

    public static CacheResponse cacheHit(String response, double similarity, String cachedQuery) {
        CacheResponse r = new CacheResponse();
        r.hit = true;
        r.source = "cache";
        r.response = response;
        r.similarity = similarity;
        r.cachedQuery = cachedQuery;
        return r;
    }

    public static CacheResponse llmResponse(String response, String model, long latencyMs) {
        CacheResponse r = new CacheResponse();
        r.hit = false;
        r.source = "llm";
        r.response = response;
        r.model = model;
        r.latencyMs = latencyMs;
        return r;
    }

    // getters / setters
    public boolean isHit() { return hit; }
    public void setHit(boolean v) { hit = v; }
    public String getSource() { return source; }
    public void setSource(String v) { source = v; }
    public String getResponse() { return response; }
    public void setResponse(String v) { response = v; }
    public double getSimilarity() { return similarity; }
    public void setSimilarity(double v) { similarity = v; }
    public String getCachedQuery() { return cachedQuery; }
    public void setCachedQuery(String v) { cachedQuery = v; }
    public String getModel() { return model; }
    public void setModel(String v) { model = v; }
    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long v) { latencyMs = v; }
}
