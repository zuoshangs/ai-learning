package com.ai.learning.vector.strategy;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 段落语义切分 — 按空行分隔，保留段落完整性
 */
@Component
public class ParagraphChunkStrategy implements ChunkStrategy {

    private final int maxChunkChars;

    public ParagraphChunkStrategy() { this(1500); }
    public ParagraphChunkStrategy(int maxChunkChars) { this.maxChunkChars = maxChunkChars; }

    @Override
    public String getName() { return "ParagraphSplitter (语义)"; }

    @Override
    public List<String> chunk(String text) {
        String[] paragraphs = text.split("\\n\\s*\\n");
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String para : paragraphs) {
            para = para.trim();
            if (para.isEmpty()) continue;

            if (current.length() + para.length() > maxChunkChars && !current.isEmpty()) {
                chunks.add(current.toString().trim());
                current = new StringBuilder();
            }
            current.append(para).append("\n\n");
        }
        if (!current.isEmpty()) chunks.add(current.toString().trim());

        return chunks;
    }
}
