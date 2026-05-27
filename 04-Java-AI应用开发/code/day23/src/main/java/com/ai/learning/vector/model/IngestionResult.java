package com.ai.learning.vector.model;

import java.util.List;

/**
 * 批量入库结果
 */
public class IngestionResult {

    private int totalChunks;
    private int totalDocuments;
    private List<StrategyStats> strategyStats;
    private long durationMs;
    private String message;

    public IngestionResult() {}

    public int getTotalChunks() { return totalChunks; }
    public IngestionResult setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; return this; }

    public int getTotalDocuments() { return totalDocuments; }
    public IngestionResult setTotalDocuments(int totalDocuments) { this.totalDocuments = totalDocuments; return this; }

    public List<StrategyStats> getStrategyStats() { return strategyStats; }
    public IngestionResult setStrategyStats(List<StrategyStats> strategyStats) { this.strategyStats = strategyStats; return this; }

    public long getDurationMs() { return durationMs; }
    public IngestionResult setDurationMs(long durationMs) { this.durationMs = durationMs; return this; }

    public String getMessage() { return message; }
    public IngestionResult setMessage(String message) { this.message = message; return this; }

    public static class StrategyStats {
        private String strategyName;
        private int chunkCount;
        private int avgChunkSize;

        public StrategyStats() {}

        public StrategyStats(String strategyName, int chunkCount, int avgChunkSize) {
            this.strategyName = strategyName;
            this.chunkCount = chunkCount;
            this.avgChunkSize = avgChunkSize;
        }

        public String getStrategyName() { return strategyName; }
        public int getChunkCount() { return chunkCount; }
        public int getAvgChunkSize() { return avgChunkSize; }
    }
}
