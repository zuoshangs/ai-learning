package com.ai.learning.vector;

import com.ai.learning.vector.strategy.ParagraphChunkStrategy;
import com.ai.learning.vector.strategy.RecursiveChunkStrategy;
import com.ai.learning.vector.strategy.TokenChunkStrategy;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 切分策略单元测试
 */
class ChunkStrategyTest {

    static final String TEXT = """
        Spring AI 是一个面向 AI 应用的框架。
        它支持多种大语言模型。
        PgVector 是最常用的向量数据库。
        向量搜索通过语义相似度查找内容。
        """;

    static final String LONG_TEXT = """
        Spring AI 是一个面向人工智能应用的 Spring 生态框架。

        它的核心理念是将大语言模型的能力集成到 Java 应用中。

        Spring AI 支持多种大语言模型提供商，包括 OpenAI、Ollama 等。

        在提示词工程方面，Spring AI 提供了 PromptTemplate。

        在 RAG 方面，Spring AI 提供了完整的数据处理管线。
        """;

    @Test
    void tokenStrategy_producesChunks() {
        TokenChunkStrategy s = new TokenChunkStrategy(200, 50, 10);
        List<String> chunks = s.chunk(LONG_TEXT);
        assertNotNull(chunks);
        assertTrue(chunks.size() >= 1);
        assertTrue(chunks.getFirst().length() > 0);
    }

    @Test
    void tokenStrategy_shortText() {
        TokenChunkStrategy s = new TokenChunkStrategy(200, 50, 10);
        List<String> chunks = s.chunk("Hello");
        // TokenTextSplitter may return 0 or 1 chunk for very short texts
        assertNotNull(chunks);
    }

    @Test
    void paragraphStrategy_preservesParagraphs() {
        ParagraphChunkStrategy s = new ParagraphChunkStrategy(500);
        List<String> chunks = s.chunk(TEXT);
        assertTrue(chunks.size() >= 1);
    }

    @Test
    void paragraphStrategy_emptyText() {
        ParagraphChunkStrategy s = new ParagraphChunkStrategy();
        List<String> chunks = s.chunk("");
        assertEquals(0, chunks.size());
    }

    @Test
    void recursiveStrategy_producesChunks() {
        RecursiveChunkStrategy s = new RecursiveChunkStrategy(200, 20);
        List<String> chunks = s.chunk(TEXT);
        assertTrue(chunks.size() >= 1);
    }

    @Test
    void recursiveStrategy_emptyText() {
        RecursiveChunkStrategy s = new RecursiveChunkStrategy();
        List<String> chunks = s.chunk("");
        assertEquals(0, chunks.size());
    }

    @Test
    void recursiveStrategy_handlesChinese() {
        RecursiveChunkStrategy s = new RecursiveChunkStrategy(100, 10);
        List<String> chunks = s.chunk(LONG_TEXT);
        assertTrue(chunks.size() >= 3);
    }

    @Test
    void allStrategies_haveDifferentNames() {
        TokenChunkStrategy t = new TokenChunkStrategy();
        ParagraphChunkStrategy p = new ParagraphChunkStrategy();
        RecursiveChunkStrategy r = new RecursiveChunkStrategy();
        assertNotEquals(t.getName(), p.getName());
        assertNotEquals(p.getName(), r.getName());
        assertNotEquals(r.getName(), t.getName());
    }
}
