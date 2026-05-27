package com.ai.learning.doc.model;

import java.util.List;

/**
 * 一次切分策略的执行结果
 */
public class ChunkStrategyResult {

    private String strategyName;        // 策略名
    private String description;         // 策略说明（参数）
    private int chunkCount;             // 切分后的块数
    private int minChunkSize;           // 最小块字符数
    private int maxChunkSize;           // 最大块字符数
    private int avgChunkSize;           // 平均块字符数
    private int totalChunkChars;        // 所有块总字符数
    private double overlapRatio;        // 重叠率 (%)
    private List<ChunkSample> samples;  // 前 3 个块样本

    public ChunkStrategyResult() {}

    // --- Getters / Setters ---

    public String getStrategyName() { return strategyName; }
    public void setStrategyName(String strategyName) { this.strategyName = strategyName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getChunkCount() { return chunkCount; }
    public void setChunkCount(int chunkCount) { this.chunkCount = chunkCount; }

    public int getMinChunkSize() { return minChunkSize; }
    public void setMinChunkSize(int minChunkSize) { this.minChunkSize = minChunkSize; }

    public int getMaxChunkSize() { return maxChunkSize; }
    public void setMaxChunkSize(int maxChunkSize) { this.maxChunkSize = maxChunkSize; }

    public int getAvgChunkSize() { return avgChunkSize; }
    public void setAvgChunkSize(int avgChunkSize) { this.avgChunkSize = avgChunkSize; }

    public int getTotalChunkChars() { return totalChunkChars; }
    public void setTotalChunkChars(int totalChunkChars) { this.totalChunkChars = totalChunkChars; }

    public double getOverlapRatio() { return overlapRatio; }
    public void setOverlapRatio(double overlapRatio) { this.overlapRatio = overlapRatio; }

    public List<ChunkSample> getSamples() { return samples; }
    public void setSamples(List<ChunkSample> samples) { this.samples = samples; }

    // --- 内部类：块样本 ---

    public static class ChunkSample {
        private int index;
        private int length;
        private String preview;

        public ChunkSample() {}

        public ChunkSample(int index, int length, String preview) {
            this.index = index;
            this.length = length;
            this.preview = preview;
        }

        public int getIndex() { return index; }
        public void setIndex(int index) { this.index = index; }

        public int getLength() { return length; }
        public void setLength(int length) { this.length = length; }

        public String getPreview() { return preview; }
        public void setPreview(String preview) { this.preview = preview; }
    }

    @Override
    public String toString() {
        return strategyName + ": " + chunkCount + " chunks, avg=" + avgChunkSize + " chars";
    }
}
