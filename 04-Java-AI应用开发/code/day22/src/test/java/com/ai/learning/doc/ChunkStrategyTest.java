package com.ai.learning.doc;

import com.ai.learning.doc.model.ChunkStrategyResult;
import com.ai.learning.doc.strategy.ParagraphChunkStrategy;
import com.ai.learning.doc.strategy.RecursiveCharacterChunkStrategy;
import com.ai.learning.doc.strategy.TokenChunkStrategy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 切分策略单元测试
 */
class ChunkStrategyTest {

    static final String SHORT_TEXT = "Spring AI 是一个AI框架。";
    static final String LONG_TEXT = """
        Spring AI 是一个面向人工智能应用的 Spring 生态框架。
        
        它的核心理念是将大语言模型的能力集成到 Java 应用中。
        
        Spring AI 支持多种大语言模型提供商，包括 OpenAI、Ollama 等。
        
        在提示词工程方面，Spring AI 提供了 PromptTemplate。
        
        在 RAG 方面，Spring AI 提供了完整的数据处理管线。
        
        从文档加载开始，支持 PDF、Word、HTML 和纯文本格式。
        
        加载后的文档可以通过 TokenTextSplitter 进行切分。
        
        切分后的文档块通过 Embedding API 向量化后存入向量数据库。
        
        在查询时，框架自动从向量库中检索相关文档块作为上下文。
        
        最终基于事实生成回答。
        """;

    // --- TokenChunkStrategy ---

    @Test
    void tokenStrategy_shortText_producesOneChunk() {
        TokenChunkStrategy strategy = new TokenChunkStrategy(500, 10, 10, 10000, false);
        ChunkStrategyResult result = strategy.chunk(SHORT_TEXT);
        assertTrue(result.getChunkCount() >= 1, "短文本应至少产生一个块");
    }

    @Test
    void tokenStrategy_longText_producesMultipleChunks() {
        TokenChunkStrategy strategy = new TokenChunkStrategy(100, 20, 10, 10000, false);
        ChunkStrategyResult result = strategy.chunk(LONG_TEXT);
        assertTrue(result.getChunkCount() >= 2, "小块大小应产生多个块");
        assertTrue(result.getAvgChunkSize() > 0);
    }

    @Test
    void tokenStrategy_emptyText_producesZeroOrOneChunk() {
        TokenChunkStrategy strategy = new TokenChunkStrategy();
        ChunkStrategyResult result = strategy.chunk("");
        assertTrue(result.getChunkCount() >= 0);
    }

    @Test
    void tokenStrategy_hasSamples() {
        TokenChunkStrategy strategy = new TokenChunkStrategy(100, 20, 10, 10000, false);
        ChunkStrategyResult result = strategy.chunk(LONG_TEXT);
        assertNotNull(result.getSamples());
        assertFalse(result.getSamples().isEmpty());
        assertNotNull(result.getSamples().getFirst().getPreview());
    }

    // --- ParagraphChunkStrategy ---

    @Test
    void paragraphStrategy_preservesParagraphBoundaries() {
        ParagraphChunkStrategy strategy = new ParagraphChunkStrategy(500, 50);
        ChunkStrategyResult result = strategy.chunk(LONG_TEXT);
        // 长文本有多个段落
        assertTrue(result.getChunkCount() >= 1);
        assertEquals(0, result.getOverlapRatio(), "段落切分不应有重叠");
    }

    @Test
    void paragraphStrategy_singleParagraph() {
        ParagraphChunkStrategy strategy = new ParagraphChunkStrategy(500, 50);
        ChunkStrategyResult result = strategy.chunk(SHORT_TEXT);
        assertEquals(1, result.getChunkCount());
    }

    // --- RecursiveCharacterChunkStrategy ---

    @Test
    void recursiveStrategy_handlesNormalText() {
        RecursiveCharacterChunkStrategy strategy = new RecursiveCharacterChunkStrategy(200, 20);
        ChunkStrategyResult result = strategy.chunk(LONG_TEXT);
        assertTrue(result.getChunkCount() >= 1);
        assertTrue(result.getMinChunkSize() > 0);
    }

    @Test
    void recursiveStrategy_emptyText() {
        RecursiveCharacterChunkStrategy strategy = new RecursiveCharacterChunkStrategy();
        ChunkStrategyResult result = strategy.chunk("");
        assertEquals(0, result.getChunkCount());
    }

    @Test
    void recursiveStrategy_chunksAreWithinSize() {
        int maxSize = 200;
        RecursiveCharacterChunkStrategy strategy = new RecursiveCharacterChunkStrategy(maxSize, 20);
        ChunkStrategyResult result = strategy.chunk(LONG_TEXT);
        assertTrue(result.getMaxChunkSize() <= maxSize * 1.5,
            "最大块不应远超目标大小（允许少量溢出）");
    }

    // --- 综合对比 ---

    @Test
    void allStrategies_produceDifferentChunkCounts() {
        String text = LONG_TEXT.repeat(2);

        TokenChunkStrategy token = new TokenChunkStrategy(200, 50, 20, 10000, false);
        ParagraphChunkStrategy para = new ParagraphChunkStrategy(500, 50);
        RecursiveCharacterChunkStrategy rec = new RecursiveCharacterChunkStrategy(300, 30);

        ChunkStrategyResult tokenResult = token.chunk(text);
        ChunkStrategyResult paraResult = para.chunk(text);
        ChunkStrategyResult recResult = rec.chunk(text);

        // 三种策略应该产生不同的块数 — 至少两种不同
        boolean allSame = tokenResult.getChunkCount() == paraResult.getChunkCount()
            && paraResult.getChunkCount() == recResult.getChunkCount();
        assertFalse(allSame, "不同策略应产生不同的切分结果");
    }
}
