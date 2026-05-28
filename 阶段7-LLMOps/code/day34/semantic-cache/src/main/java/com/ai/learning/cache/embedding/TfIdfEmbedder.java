package com.ai.learning.cache.embedding;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * TF-IDF based embedding generator.
 * Self-contained — no external API calls needed.
 * Tokenizes Chinese + English text and computes TF-IDF vectors.
 */
@Component
public class TfIdfEmbedder {

    /** Global document frequency across all indexed documents. */
    private final Map<String, Integer> df = new HashMap<>();
    private int totalDocs = 0;
    private boolean frozen = false;

    /**
     * Tokenize text into terms.
     * Handles Chinese characters (each char as a token) and English words.
     */
    public List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder eng = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isIdeographic(c) || c > 0x4E00) {
                // Chinese character — flush any English buffer, then add char
                if (eng.length() > 0) {
                    tokens.add(eng.toString().toLowerCase());
                    eng.setLength(0);
                }
                tokens.add(String.valueOf(c));
            } else if (Character.isLetter(c) || c == '-' || c == '\'') {
                eng.append(c);
            } else if (Character.isDigit(c)) {
                eng.append(c);
            } else {
                if (eng.length() > 0) {
                    tokens.add(eng.toString().toLowerCase());
                    eng.setLength(0);
                }
            }
        }
        if (eng.length() > 0) {
            tokens.add(eng.toString().toLowerCase());
        }
        return tokens;
    }

    /** Add a document to the corpus (updates DF). */
    public void addDocument(String text) {
        if (frozen) throw new IllegalStateException("Embedder is frozen");
        Set<String> unique = new HashSet<>(tokenize(text));
        for (String term : unique) {
            df.merge(term, 1, Integer::sum);
        }
        totalDocs++;
    }

    /** Freeze the embedder — DF no longer updated. */
    public void freeze() {
        frozen = true;
    }

    /**
     * Compute TF-IDF vector for a query.
     * Returns map of term -> tf-idf weight.
     */
    public Map<String, Double> computeVector(String text) {
        List<String> tokens = tokenize(text);
        Map<String, Integer> tf = new HashMap<>();
        for (String t : tokens) {
            tf.merge(t, 1, Integer::sum);
        }

        Map<String, Double> vector = new HashMap<>();
        for (Map.Entry<String, Integer> entry : tf.entrySet()) {
            String term = entry.getKey();
            int count = entry.getValue();
            double tfVal = 1.0 + Math.log(count);
            double idf = Math.log((double) (totalDocs + 1) / (df.getOrDefault(term, 1) + 1)) + 1.0;
            vector.put(term, tfVal * idf);
        }
        return vector;
    }

    /**
     * Compute cosine similarity between two TF-IDF vectors.
     */
    public double cosineSimilarity(Map<String, Double> v1, Map<String, Double> v2) {
        double dot = 0, n1 = 0, n2 = 0;
        // All terms from both vectors
        Set<String> allTerms = new HashSet<>(v1.keySet());
        allTerms.addAll(v2.keySet());

        for (String term : allTerms) {
            double a = v1.getOrDefault(term, 0.0);
            double b = v2.getOrDefault(term, 0.0);
            dot += a * b;
            n1 += a * a;
            n2 += b * b;
        }

        if (n1 == 0 || n2 == 0) return 0.0;
        return dot / (Math.sqrt(n1) * Math.sqrt(n2));
    }
}
