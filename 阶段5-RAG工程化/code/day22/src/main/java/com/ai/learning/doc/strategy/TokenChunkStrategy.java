package com.ai.learning.doc.strategy;

import com.ai.learning.doc.model.ChunkStrategyResult;
import com.ai.learning.doc.model.ChunkStrategyResult.ChunkSample;

import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Token 级别切分策略
 *
 * 使用 Spring AI 的 TokenTextSplitter：
 *   - 按 Token 数切分（默认 800 tokens/chunk）
 *   - 带有重叠（最小块长度 / maxNumChunks 等参数）
 *   - 比字符切分更语义准确
 *
 * Spring AI 1.0.0-M6 TokenTextSplitter 构造函数：
 *   TokenTextSplitter(int chunkSize, int minChunkSizeChars,
 *                     int minChunkLengthToEmbed, int maxNumChunks,
 *                     boolean keepSeparator)
 */
@Component
public class TokenChunkStrategy implements ChunkStrategy {

    /** 目标块大小（tokens） */
    private final int chunkSize;

    /** 最小块字符数（太短的块会被合并） */
    private final int minChunkSizeChars;

    /** 需要嵌入的最小长度 */
    private final int minChunkLengthToEmbed;

    /** 最大块数 */
    private final int maxNumChunks;

    /** 是否保留分隔符 */
    private final boolean keepSeparator;

    public TokenChunkStrategy() {
        this(500, 100, 50, 10000, false);
    }

    public TokenChunkStrategy(int chunkSize, int minChunkSizeChars,
                               int minChunkLengthToEmbed, int maxNumChunks,
                               boolean keepSeparator) {
        this.chunkSize = chunkSize;
        this.minChunkSizeChars = minChunkSizeChars;
        this.minChunkLengthToEmbed = minChunkLengthToEmbed;
        this.maxNumChunks = maxNumChunks;
        this.keepSeparator = keepSeparator;
    }

    @Override
    public String getName() {
        return "TokenSplitter (Spring AI)";
    }

    @Override
    public ChunkStrategyResult chunk(String text) {
        // 使用 TokenTextSplitter — 显式设置参数
        TokenTextSplitter splitter = new TokenTextSplitter(
            chunkSize,              // defaultChunkSize
            minChunkSizeChars,      // minChunkSizeChars
            minChunkLengthToEmbed,  // minChunkLengthToEmbed
            maxNumChunks,           // maxNumChunks
            keepSeparator           // keepSeparator
        );

        // apply() 接收 List<Document>，返回 List<Document>
        List<org.springframework.ai.document.Document> documents = splitter.apply(
            List.of(new org.springframework.ai.document.Document(text)));
        List<String> chunks = documents.stream()
            .map(org.springframework.ai.document.Document::getText)
            .toList();

        return buildResult(chunks);
    }

    /** 构建统计结果 */
    protected ChunkStrategyResult buildResult(List<String> chunks) {
        ChunkStrategyResult result = new ChunkStrategyResult();
        result.setStrategyName(getName());
        result.setDescription("chunkSize=" + chunkSize + "t, minChars=" + minChunkSizeChars);

        if (chunks == null || chunks.isEmpty()) {
            result.setChunkCount(0);
            result.setMinChunkSize(0);
            result.setMaxChunkSize(0);
            result.setAvgChunkSize(0);
            result.setTotalChunkChars(0);
            result.setOverlapRatio(0);
            result.setSamples(List.of());
            return result;
        }

        result.setChunkCount(chunks.size());

        int[] sizes = chunks.stream()
            .mapToInt(String::length)
            .toArray();

        result.setMinChunkSize(findMin(sizes));
        result.setMaxChunkSize(findMax(sizes));
        result.setAvgChunkSize((int) Math.round(average(sizes)));
        result.setTotalChunkChars(sum(sizes));

        // 估算重叠率：总字符 / 原始字符 — 1
        // 使用原始字符估算（sum of chunk chars vs unique chars）
        // 简化：totalChars 是原始文本的总和，重叠率 ≈ 0 因为没有显式重叠
        result.setOverlapRatio(0);

        // 前 3 个样本
        List<ChunkSample> samples = new ArrayList<>();
        for (int i = 0; i < Math.min(3, chunks.size()); i++) {
            String textSample = chunks.get(i);
            String preview = textSample.length() > 120
                ? textSample.substring(0, 120) + "..."
                : textSample;
            samples.add(new ChunkSample(i, textSample.length(), preview));
        }
        result.setSamples(samples);

        return result;
    }

    // --- 辅助方法 ---

    protected static int findMin(int[] arr) {
        int min = Integer.MAX_VALUE;
        for (int v : arr) if (v < min) min = v;
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    protected static int findMax(int[] arr) {
        int max = 0;
        for (int v : arr) if (v > max) max = v;
        return max;
    }

    protected static double average(int[] arr) {
        return arr.length == 0 ? 0 : (double) sum(arr) / arr.length;
    }

    protected static int sum(int[] arr) {
        int s = 0;
        for (int v : arr) s += v;
        return s;
    }
}
