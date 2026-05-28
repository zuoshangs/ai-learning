package com.ai.learning.advanced.model;

import java.util.List;

public class RagResult {
    private String originalQuery;
    private String technique;   // rewrite / hyde / parent-doc / compare
    private String rewrittenQuery;  // only for rewrite
    private String hydeHypothesis;  // only for hyde
    private int chunkCount;
    private List<ChunkHit> chunks;
    private List<String> usedStrategies;

    public RagResult() {}

    public String getOriginalQuery() { return originalQuery; }
    public RagResult setOriginalQuery(String originalQuery) { this.originalQuery = originalQuery; return this; }

    public String getTechnique() { return technique; }
    public RagResult setTechnique(String technique) { this.technique = technique; return this; }

    public String getRewrittenQuery() { return rewrittenQuery; }
    public RagResult setRewrittenQuery(String rewrittenQuery) { this.rewrittenQuery = rewrittenQuery; return this; }

    public String getHydeHypothesis() { return hydeHypothesis; }
    public RagResult setHydeHypothesis(String hydeHypothesis) { this.hydeHypothesis = hydeHypothesis; return this; }

    public int getChunkCount() { return chunkCount; }
    public RagResult setChunkCount(int chunkCount) { this.chunkCount = chunkCount; return this; }

    public List<ChunkHit> getChunks() { return chunks; }
    public RagResult setChunks(List<ChunkHit> chunks) { this.chunks = chunks; return this; }

    public List<String> getUsedStrategies() { return usedStrategies; }
    public RagResult setUsedStrategies(List<String> usedStrategies) { this.usedStrategies = usedStrategies; return this; }

    public static class ChunkHit {
        private int rank;
        private double score;
        private String content;
        private int contentLength;
        private String source;

        public ChunkHit() {}

        public ChunkHit(int rank, double score, String content, String source) {
            this.rank = rank;
            this.score = score;
            this.content = content;
            this.contentLength = content.length();
            this.source = source;
        }

        public int getRank() { return rank; }
        public void setRank(int rank) { this.rank = rank; }
        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; this.contentLength = content.length(); }
        public int getContentLength() { return contentLength; }
        public void setContentLength(int contentLength) { this.contentLength = contentLength; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
    }
}
