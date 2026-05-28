package com.ai.learning.cache.service;

import com.ai.learning.cache.config.CacheConfig;
import com.ai.learning.cache.embedding.TfIdfEmbedder;
import com.ai.learning.cache.model.CacheEntry;
import com.ai.learning.cache.model.CacheResponse;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Main semantic cache service.
 * Stores query-response pairs keyed by TF-IDF vectors.
 * Supports:
 * - Semantic lookup (cosine similarity against all cached entries)
 * - Exact match lookup (O(1) via query hash)
 * - LRU eviction when maxSize exceeded
 * - TTL-based expiry
 * - Hit rate statistics
 */
@Service
public class SemanticCache {

    private static final Logger log = LoggerFactory.getLogger(SemanticCache.class);

    private final CacheConfig config;
    private final TfIdfEmbedder embedder;
    private final LlmService llmService;

    /** Exact-match map: query hash -> entry */
    private final Map<String, CacheEntry> exactCache = new ConcurrentHashMap<>();

    /** All entries for semantic search (ordered by recency for LRU) */
    private final ConcurrentLinkedDeque<CacheEntry> entries = new ConcurrentLinkedDeque<>();

    /** Statistics */
    private final AtomicLong totalLookups = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);

    public SemanticCache(CacheConfig config, TfIdfEmbedder embedder, LlmService llmService) {
        this.config = config;
        this.embedder = embedder;
        this.llmService = llmService;
    }

    @PostConstruct
    public void init() {
        // Pre-populate with some common Q&A pairs
        warmup();
        log.info("SemanticCache initialized: maxSize={}, threshold={}, ttl={}s, entries={}",
                config.getMaxSize(), config.getSimilarityThreshold(),
                config.getDefaultTtlSeconds(), entries.size());
    }

    /**
     * Lookup or generate response.
     * 1. Check exact cache (O(1))
     * 2. Check semantic cache (cosine similarity)
     * 3. If miss, call LLM and cache result
     */
    public CacheResponse getOrGenerate(String query) {
        totalLookups.incrementAndGet();

        // 1. Exact match
        String queryKey = normalize(query);
        CacheEntry exact = exactCache.get(queryKey);
        if (exact != null && !exact.isExpired()) {
            exact.incrementHit();
            cacheHits.incrementAndGet();
            touch(exact);
            log.debug("Cache EXACT hit: '{}'", query);
            return CacheResponse.cacheHit(exact.getResponse(), 1.0, exact.getQuery());
        }

        // 2. Semantic match — compute query vector
        Map<String, Double> queryVec = embedder.computeVector(query);

        CacheEntry best = null;
        double bestSim = 0;

        // Expire stale entries while scanning
        Iterator<CacheEntry> it = entries.iterator();
        while (it.hasNext()) {
            CacheEntry entry = it.next();
            if (entry.isExpired()) {
                it.remove();
                exactCache.remove(normalize(entry.getQuery()));
                continue;
            }
            double sim = embedder.cosineSimilarity(queryVec, entry.getVector());
            if (sim > bestSim) {
                bestSim = sim;
                best = entry;
            }
        }

        if (best != null && bestSim >= config.getSimilarityThreshold()) {
            best.incrementHit();
            cacheHits.incrementAndGet();
            touch(best);
            log.debug("Cache SEMANTIC hit: query='{}' sim={} matched='{}'", query, String.format("%.3f", bestSim), best.getQuery());
            return CacheResponse.cacheHit(best.getResponse(), bestSim, best.getQuery());
        }

        // 3. Miss — call LLM
        cacheMisses.incrementAndGet();
        long start = System.currentTimeMillis();
        String response = llmService.call(query);
        long latency = System.currentTimeMillis() - start;

        // Cache the result
        put(query, queryVec, response);

        log.debug("Cache MISS: query='{}' llm={}ms", query, latency);
        return CacheResponse.llmResponse(response, config.getLlm().getModel(), latency);
    }

    /** Add an entry to the cache. */
    public void put(String query, Map<String, Double> vector, String response) {
        // Evict if at capacity
        while (entries.size() >= config.getMaxSize()) {
            CacheEntry oldest = entries.pollLast();  // LRU: evict least recently used
            if (oldest != null) {
                exactCache.remove(normalize(oldest.getQuery()));
                log.debug("Evicted LRU: '{}'", oldest.getQuery());
            }
        }

        CacheEntry entry = new CacheEntry(query, vector, response, config.getDefaultTtlSeconds());
        entries.addFirst(entry);  // most recent at front
        exactCache.put(normalize(query), entry);
    }

    /** Move entry to front (recently used). */
    private void touch(CacheEntry entry) {
        entries.remove(entry);
        entries.addFirst(entry);
    }

    /** Normalize a query for exact matching. */
    private String normalize(String s) {
        return s.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    /** Pre-populate cache with common queries. */
    private void warmup() {
        // These are pre-cached without calling LLM
        String[][] warmups = {
            {"什么是LLM网关", "LLM网关是介于应用程序和大语言模型之间的中间层服务，用于统一管理请求路由、访问控制、速率限制和成本监控。"},
            {"什么是语义缓存", "语义缓存是一种基于查询含义而非精确字面匹配的缓存技术。它通过将查询转为向量并计算相似度，返回语义相近的缓存结果。"},
            {"什么是API限流", "API限流是控制客户端在特定时间窗口内可发起的请求数量的机制。常见算法有令牌桶、滑动窗口、漏桶等。"},
            {"什么是熔断器", "熔断器是一种保护分布式系统的容错模式。当上游服务连续失败超过阈值时，熔断器打开，快速拒绝请求给系统恢复时间。"},
            {"今天天气怎么样", "这是一个关于天气的问题。如需获取实时天气信息，请使用专门的天气API服务。"},
        };

        for (String[] w : warmups) {
            Map<String, Double> vec = embedder.computeVector(w[0]);
            put(w[0], vec, w[1]);
        }
        log.info("Warmed up with {} entries", warmups.length);
    }

    /** Direct semantic lookup only (no LLM fallback). For testing. */
    public CacheResponse semanticLookup(String query, Map<String, Double> queryVec) {
        CacheEntry best = null;
        double bestSim = 0;
        for (CacheEntry entry : entries) {
            if (entry.isExpired()) continue;
            double sim = embedder.cosineSimilarity(queryVec, entry.getVector());
            if (sim > bestSim) {
                bestSim = sim;
                best = entry;
            }
        }
        if (best != null && bestSim >= config.getSimilarityThreshold()) {
            return CacheResponse.cacheHit(best.getResponse(), bestSim, best.getQuery());
        }
        CacheResponse r = new CacheResponse();
        r.setHit(false);
        r.setSource("miss");
        r.setResponse("No matching cache entry found");
        r.setSimilarity(bestSim);
        return r;
    }

    public TfIdfEmbedder getEmbedder() { return embedder; }

    public int getSize() { return entries.size(); }

    /** Public warmup trigger. */
    public void doWarmup() { warmup(); }

    /** Remove expired entries. */
    public int evictExpired() {
        int count = 0;
        Iterator<CacheEntry> it = entries.iterator();
        while (it.hasNext()) {
            CacheEntry entry = it.next();
            if (entry.isExpired()) {
                it.remove();
                exactCache.remove(normalize(entry.getQuery()));
                count++;
            }
        }
        if (count > 0) log.debug("Evicted {} expired entries", count);
        return count;
    }

    /** Clear all cache entries. */
    public void clear() {
        entries.clear();
        exactCache.clear();
        log.info("Cache cleared");
    }

    // Statistics
    public double getHitRate() {
        long total = totalLookups.get();
        return total > 0 ? (double) cacheHits.get() / total : 0;
    }

    public Map<String, Object> getStats() {
        return Map.of(
                "totalLookups", totalLookups.get(),
                "cacheHits", cacheHits.get(),
                "cacheMisses", cacheMisses.get(),
                "hitRate", String.format("%.2f%%", getHitRate() * 100),
                "size", entries.size(),
                "maxSize", config.getMaxSize(),
                "threshold", config.getSimilarityThreshold(),
                "ttlSeconds", config.getDefaultTtlSeconds()
        );
    }

    /** List cache entries (for admin). */
    public List<Map<String, Object>> listEntries(int limit) {
        return entries.stream()
                .limit(limit)
                .map(e -> Map.<String, Object>of(
                        "query", e.getQuery(),
                        "responsePreview", e.getResponse().length() > 60
                                ? e.getResponse().substring(0, 60) + "..."
                                : e.getResponse(),
                        "hitCount", e.getHitCount(),
                        "age", java.time.Duration.between(e.getCreatedAt(), java.time.Instant.now()).getSeconds() + "s",
                        "expired", e.isExpired()
                ))
                .collect(Collectors.toList());
    }
}
