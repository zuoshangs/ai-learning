package com.ai.learning.knowledge.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 简单嵌入配置 — 使用本地哈希嵌入，无需外部 API
 * <p>
 * 由于 DeepSeek 不支持 OpenAI 兼容的 Embedding API，
 * 此配置用本地 Deterministic Hash Embedding 替代。
 * <p>
 * 注意：这不是真正的语义嵌入，仅用于演示管线集成。
 * 生产环境应使用真正的嵌入模型（如 Ollama bge-m3）。
 */
@Configuration
public class SimpleEmbeddingConfig {

    private static final Logger log = LoggerFactory.getLogger(SimpleEmbeddingConfig.class);

    @Bean
    @Primary
    public EmbeddingModel simpleEmbeddingModel() {
        return new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                List<String> texts = request.getInstructions();
                List<Embedding> embeddings = new ArrayList<>();
                AtomicInteger idx = new AtomicInteger(0);
                for (String text : texts) {
                    float[] vector = computeHashEmbedding(text);
                    embeddings.add(new Embedding(vector, idx.getAndIncrement()));
                }
                return new EmbeddingResponse(embeddings);
            }

            @Override
            public float[] embed(Document document) {
                return computeHashEmbedding(document.getText());
            }

            @Override
            public int dimensions() {
                return 1024;
            }
        };
    }

    private float[] computeHashEmbedding(String text) {
        float[] vector = new float[1024];
        if (text == null || text.isBlank()) return vector;

        String[] tokens = text.toLowerCase()
            .replaceAll("[\\p{P}，。、；：？！\"\"''【】《》（）]", " ")
            .split("\\s+");

        Map<String, Integer> tf = new HashMap<>();
        for (String t : tokens) if (t.length() >= 1) tf.merge(t, 1, Integer::sum);

        int maxTf = tf.values().stream().mapToInt(Integer::intValue).max().orElse(1);
        for (Map.Entry<String, Integer> e : tf.entrySet()) {
            String token = e.getKey();
            double w = 0.5 + 0.5 * ((double) e.getValue() / maxTf);
            for (int h = 0; h < 8; h++) {
                long hash = hash(token, h);
                int idx = (int) (Math.abs(hash) % 1024);
                vector[idx] += (hash >= 0 ? 1.0f : -1.0f) * (float) w;
            }
            for (int n = 2; n <= 3 && n <= token.length(); n++) {
                for (int i = 0; i <= token.length() - n; i++) {
                    long gh = hash(token.substring(i, i + n), n);
                    int idx = (int) (Math.abs(gh) % 1024);
                    vector[idx] += (gh >= 0 ? 0.5f : -0.5f) * (float) w;
                }
            }
        }

        long gh = hash(text, 42);
        for (int i = 0; i < 16; i++)
            vector[(int) (Math.abs(gh + i * 7919L) % 1024)] += 0.3f;

        double norm = 0;
        for (float v : vector) norm += v * v;
        norm = Math.sqrt(norm);
        if (norm > 1e-8) for (int i = 0; i < 1024; i++) vector[i] /= norm;

        return vector;
    }

    private long hash(String s, int seed) {
        long h = seed * 0x9E3779B97F4A7C15L;
        for (int i = 0; i < s.length(); i++) {
            h ^= (long) s.charAt(i) * 0xCC9E2D51L;
            h = Long.rotateLeft(h, 17);
            h *= 0x1B873593L;
        }
        h ^= s.length();
        h = Long.rotateLeft(h, 13);
        h *= 0xE6546B64L;
        h ^= h >>> 16;
        return h;
    }
}
