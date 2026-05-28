package com.ai.llm.gateway.cache;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Semantic cache using TF-IDF vectorization + cosine similarity.
 * Combines exact match (HashMap O(1)) with semantic match (TF-IDF cosine).
 */
public class SemanticCacheService {

    private final int maxSize;
    private final long ttlMillis;
    private final double similarityThreshold;

    private final LinkedHashMap<String, CacheEntry> exactCache;
    private final List<CacheEntry> semanticEntries;
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);

    public SemanticCacheService(int maxSize, int ttlMinutes, double similarityThreshold) {
        this.maxSize = maxSize;
        this.ttlMillis = ttlMinutes * 60_000L;
        this.similarityThreshold = similarityThreshold;

        this.exactCache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                return size() > SemanticCacheService.this.maxSize;
            }
        };
        this.semanticEntries = new ArrayList<>();
    }

    public synchronized String get(String query) {
        evictExpired();

        // 1. Exact match
        CacheEntry entry = exactCache.get(query);
        if (entry != null) {
            hits.incrementAndGet();
            entry.lastAccess = System.currentTimeMillis();
            return entry.response;
        }

        // 2. Semantic match
        double[] queryVec = tfidfVectorize(query);
        for (CacheEntry sem : semanticEntries) {
            double sim = cosineSimilarity(queryVec, sem.vector);
            if (sim >= similarityThreshold) {
                hits.incrementAndGet();
                sem.lastAccess = System.currentTimeMillis();
                return sem.response;
            }
        }

        misses.incrementAndGet();
        return null;
    }

    public synchronized void put(String query, String response) {
        evictExpired();

        // Enforce LRU limit
        if (exactCache.size() >= maxSize) {
            Iterator<Map.Entry<String, CacheEntry>> it = exactCache.entrySet().iterator();
            if (it.hasNext()) it.remove();
        }

        double[] vec = tfidfVectorize(query);
        CacheEntry entry = new CacheEntry(query, response, vec, System.currentTimeMillis());
        exactCache.put(query, entry);
        semanticEntries.add(entry);

        // Clean semantic list
        while (semanticEntries.size() > maxSize * 2) {
            semanticEntries.removeFirst();
        }
    }

    public double hitRate() {
        long h = hits.get();
        long m = misses.get();
        long total = h + m;
        return total == 0 ? 0 : (double) h / total * 100;
    }

    public long size() { return exactCache.size(); }

    public long hits() { return hits.get(); }

    public long misses() { return misses.get(); }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        exactCache.values().removeIf(e -> (now - e.createdAt) > ttlMillis);
        semanticEntries.removeIf(e -> (now - e.createdAt) > ttlMillis);
    }

    // ---- TF-IDF vectorization ----
    private static final Set<String> STOP_WORDS = Set.of("的", "了", "是", "在", "我", "有", "和",
            "就", "不", "人", "都", "一", "一个", "上", "也", "很", "到", "说", "要", "去", "你",
            "会", "着", "没有", "看", "好", "自己", "这", "他", "她", "它", "们",
            "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "may", "might", "shall", "can", "need", "dare", "ought",
            "used", "to", "of", "in", "for", "on", "with", "at", "by", "from",
            "as", "into", "through", "during", "before", "after", "above", "below",
            "between", "out", "off", "over", "under", "again", "further", "then",
            "once", "here", "there", "when", "where", "why", "how", "all", "each",
            "every", "both", "few", "more", "most", "other", "some", "such", "no",
            "nor", "not", "only", "own", "same", "so", "than", "too", "very",
            "and", "but", "or", "if", "because", "what", "which", "who", "whom",
            "this", "that", "these", "those");

    private Map<String, Double> computeTf(String text) {
        String[] tokens = text.toLowerCase().split("[\\s\\p{P}]+");
        Map<String, Double> tf = new HashMap<>();
        for (String token : tokens) {
            if (token.isEmpty() || STOP_WORDS.contains(token)) continue;
            tf.merge(token, 1.0, Double::sum);
        }
        double total = tf.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total > 0) {
            tf.replaceAll((k, v) -> v / total);
        }
        return tf;
    }

    private double[] tfidfVectorize(String text) {
        Map<String, Double> tf = computeTf(text);
        // Build vocabulary from all cached entries + this query
        Set<String> vocab = new LinkedHashSet<>();
        for (CacheEntry e : semanticEntries) {
            vocab.addAll(e.tfMap.keySet());
        }
        vocab.addAll(tf.keySet());

        double[] vec = new double[vocab.size()];
        int i = 0;
        for (String term : vocab) {
            double tfidf = tf.getOrDefault(term, 0.0);
            // Simple idf: log(N/df), assume N=semanticEntries.size(), df=doc freq
            long df = semanticEntries.stream().filter(e -> e.tfMap.containsKey(term)).count() + 1;
            double idf = Math.log((double) semanticEntries.size() + 1 / df);
            vec[i++] = tfidf * idf;
        }
        return vec;
    }

    private double cosineSimilarity(double[] a, double[] b) {
        // Pad shorter vector if needed
        int len = Math.max(a.length, b.length);
        double[] va = Arrays.copyOf(a, len);
        double[] vb = Arrays.copyOf(b, len);

        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < len; i++) {
            dot += va[i] * vb[i];
            na += va[i] * va[i];
            nb += vb[i] * vb[i];
        }
        double denom = Math.sqrt(na) * Math.sqrt(nb);
        return denom == 0 ? 0 : dot / denom;
    }

    static class CacheEntry {
        final String query;
        final String response;
        final double[] vector;
        final Map<String, Double> tfMap;
        final long createdAt;
        volatile long lastAccess;

        CacheEntry(String query, String response, double[] vector, long createdAt) {
            this.query = query;
            this.response = response;
            this.vector = vector;
            this.tfMap = computeTf(query);
            this.createdAt = createdAt;
            this.lastAccess = createdAt;
        }

        private Map<String, Double> computeTf(String text) {
            String[] tokens = text.toLowerCase().split("[\\s\\p{P}]+");
            Map<String, Double> tf = new HashMap<>();
            for (String token : tokens) {
                if (token.isEmpty() || STOP_WORDS.contains(token)) continue;
                tf.merge(token, 1.0, Double::sum);
            }
            double total = tf.values().stream().mapToDouble(Double::doubleValue).sum();
            if (total > 0) tf.replaceAll((k, v) -> v / total);
            return tf;
        }
    }
}
