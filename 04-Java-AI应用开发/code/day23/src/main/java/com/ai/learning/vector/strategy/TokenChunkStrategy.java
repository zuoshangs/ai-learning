package com.ai.learning.vector.strategy;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Token 级别切分 — 使用 Spring AI TokenTextSplitter
 */
@Component
public class TokenChunkStrategy implements ChunkStrategy {

    private final int chunkSize;
    private final int minChunkSize;
    private final int minChunkToEmbed;

    public TokenChunkStrategy() {
        this(500, 100, 50);
    }

    public TokenChunkStrategy(int chunkSize, int minChunkSize, int minChunkToEmbed) {
        this.chunkSize = chunkSize;
        this.minChunkSize = minChunkSize;
        this.minChunkToEmbed = minChunkToEmbed;
    }

    @Override
    public String getName() {
        return "TokenSplitter (Spring AI)";
    }

    @Override
    public List<String> chunk(String text) {
        TokenTextSplitter splitter = new TokenTextSplitter(
            chunkSize, minChunkSize, minChunkToEmbed, 10000, false);
        return splitter.apply(List.of(new Document(text)))
            .stream()
            .map(Document::getText)
            .toList();
    }
}
