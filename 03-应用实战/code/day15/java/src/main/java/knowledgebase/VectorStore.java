package knowledgebase;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 向量存储 - 存储文档块及其向量表示，支持相似度搜索
 *
 * 内存中的向量数据库，使用简单词频向量 + 余弦相似度进行检索。
 */
public class VectorStore {

    private final SimpleEmbedding embedding;
    private final List<ChunkIndex> chunks;

    public VectorStore(SimpleEmbedding embedding) {
        this.embedding = embedding;
        this.chunks = new ArrayList<>();
    }

    /**
     * 向向量存储中添加一个文档块
     *
     * @param text   块文本
     * @param source 来源文档路径
     */
    public void addChunk(String text, String source) {
        Map<String, Double> vector = embedding.tokenize(text);
        ChunkIndex chunk = new ChunkIndex(text, source, vector);
        chunks.add(chunk);
    }

    /**
     * 批量添加文档块
     *
     * @param documents 文档列表
     */
    public void addDocuments(List<Document> documents) {
        for (Document doc : documents) {
            for (String chunk : doc.getChunks()) {
                addChunk(chunk, doc.getPath());
            }
        }
    }

    /**
     * 搜索与查询向量最相似的文档块
     *
     * @param queryVec 查询向量
     * @param topK     返回结果数量
     * @return 搜索结果列表（按分数降序排列）
     */
    public List<SearchResult> search(Map<String, Double> queryVec, int topK) {
        if (queryVec == null || queryVec.isEmpty() || chunks.isEmpty()) {
            return List.of();
        }

        // 计算每个块与查询的相似度
        List<ScoredChunk> scoredChunks = new ArrayList<>();
        for (ChunkIndex chunk : chunks) {
            double score = embedding.computeSimilarity(queryVec, chunk.vector);
            if (score > 0) {
                scoredChunks.add(new ScoredChunk(chunk, score));
            }
        }

        // 按分数降序排列
        scoredChunks.sort((a, b) -> Double.compare(b.score, a.score));

        // 取 topK
        int resultSize = Math.min(topK, scoredChunks.size());
        List<ScoredChunk> topResults = scoredChunks.subList(0, resultSize);

        // 转换为 SearchResult
        return topResults.stream()
                .map(sc -> new SearchResult(sc.chunk.text, sc.score, sc.chunk.source))
                .collect(Collectors.toList());
    }

    /**
     * 获取存储的块数量
     */
    public int size() {
        return chunks.size();
    }

    /**
     * 清空向量存储
     */
    public void clear() {
        chunks.clear();
    }

    // ===== 内部类 =====

    /**
     * 文档块索引 - 存储块文本、来源和向量
     */
    private static class ChunkIndex {
        final String text;
        final String source;
        final Map<String, Double> vector;

        ChunkIndex(String text, String source, Map<String, Double> vector) {
            this.text = text;
            this.source = source;
            this.vector = vector;
        }
    }

    /**
     * 带分数的块（内部排序用）
     */
    private static class ScoredChunk {
        final ChunkIndex chunk;
        final double score;

        ScoredChunk(ChunkIndex chunk, double score) {
            this.chunk = chunk;
            this.score = score;
        }
    }

    /**
     * 搜索结果
     */
    public static class SearchResult {
        private final String text;
        private final double score;
        private final String source;

        public SearchResult(String text, double score, String source) {
            this.text = text;
            this.score = score;
            this.source = source;
        }

        public String getText() {
            return text;
        }

        public double getScore() {
            return score;
        }

        public String getSource() {
            return source;
        }

        /**
         * 获取来源文件名（不含路径）
         */
        public String getSourceFileName() {
            int lastSep = source.lastIndexOf('/');
            int lastBack = source.lastIndexOf('\\');
            int idx = Math.max(lastSep, lastBack);
            return idx >= 0 ? source.substring(idx + 1) : source;
        }

        @Override
        public String toString() {
            return String.format("SearchResult{score=%.4f, source='%s', textPreview='%s...'}",
                    score, getSourceFileName(), text.substring(0, Math.min(50, text.length())));
        }
    }
}
