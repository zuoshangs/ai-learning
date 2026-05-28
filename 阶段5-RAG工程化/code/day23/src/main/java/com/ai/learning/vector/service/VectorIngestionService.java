package com.ai.learning.vector.service;

import com.ai.learning.vector.model.IngestionResult;
import com.ai.learning.vector.model.IngestionResult.StrategyStats;
import com.ai.learning.vector.strategy.ChunkStrategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 向量入库服务
 *
 * 管线：读取文档 → 选择切分策略 → 切分 → 添加元数据 → 向量化入库
 *
 * 支持多种策略并行入库（带策略标签），方便对比检索质量
 */
@Service
public class VectorIngestionService {

    private static final Logger log = LoggerFactory.getLogger(VectorIngestionService.class);

    private final VectorStore vectorStore;
    private final List<ChunkStrategy> strategies;

    /** 支持的文档扩展名 */
    private static final Set<String> TEXT_EXT = Set.of("txt", "md", "json", "xml", "html", "yaml", "yml");
    private static final Set<String> TIKA_EXT = Set.of("pdf", "docx", "doc", "pptx");

    public VectorIngestionService(VectorStore vectorStore, List<ChunkStrategy> strategies) {
        this.vectorStore = vectorStore;
        this.strategies = strategies;
    }

    /**
     * 摄入单个文件（使用所有可用策略）
     */
    public IngestionResult ingestFile(String filePath, String strategyName) throws IOException {
        Path path = Path.of(filePath).toAbsolutePath().normalize();
        if (!Files.exists(path)) throw new IOException("文件不存在: " + path);

        // 1. 读取文档
        String text = readDocument(path);
        String filename = path.getFileName().toString();
        log.info("📄 读取文档: {} ({} chars)", filename, text.length());

        // 2. 选择策略
        List<ChunkStrategy> activeStrategies = strategies;
        if (strategyName != null && !strategyName.isBlank()) {
            activeStrategies = strategies.stream()
                .filter(s -> s.getName().contains(strategyName))
                .toList();
            if (activeStrategies.isEmpty()) {
                throw new IllegalArgumentException("未找到策略: " + strategyName);
            }
        }

        // 3. 各策略并行切分 + 入库
        List<StrategyStats> statsList = new ArrayList<>();
        List<Document> allDocs = new ArrayList<>();

        for (ChunkStrategy strategy : activeStrategies) {
            long t0 = System.currentTimeMillis();
            List<String> chunks = strategy.chunk(text);
            long t1 = System.currentTimeMillis();

            List<Document> docs = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                Document doc = new Document(chunks.get(i), Map.of(
                    "source", filename,
                    "chunkIndex", i,
                    "chunkStrategy", strategy.getName(),
                    "chunkSize", chunks.get(i).length()
                ));
                docs.add(doc);
            }

            // 入库
            vectorStore.add(docs);
            allDocs.addAll(docs);

            int avgSize = chunks.isEmpty() ? 0 :
                (int) chunks.stream().mapToInt(String::length).average().orElse(0);
            statsList.add(new StrategyStats(strategy.getName(), chunks.size(), avgSize));

            log.info("  ✂️  {} → {} chunks (avg={}c) [{}ms]",
                strategy.getName(), chunks.size(), avgSize, t1 - t0);
        }

        IngestionResult result = new IngestionResult()
            .setTotalDocuments(1)
            .setTotalChunks(allDocs.size())
            .setStrategyStats(statsList)
            .setMessage("✅ 成功摄入: " + filename);

        log.info("✅ 入库完成: {} chunks total", allDocs.size());
        return result;
    }

    /**
     * 批量摄入目录
     */
    public List<IngestionResult> ingestDirectory(String dirPath, String strategyName) throws IOException {
        Path dir = Path.of(dirPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) throw new IOException("不是目录: " + dir);

        List<Path> files;
        try (var stream = Files.list(dir)) {
            files = stream.filter(Files::isRegularFile)
                .sorted()
                .toList();
        }

        List<IngestionResult> results = new ArrayList<>();
        for (Path file : files) {
            String ext = getExt(file.getFileName().toString());
            if (TEXT_EXT.contains(ext) || TIKA_EXT.contains(ext)) {
                try {
                    results.add(ingestFile(file.toString(), strategyName));
                } catch (Exception e) {
                    log.error("摄入 {} 失败: {}", file.getFileName(), e.getMessage());
                }
            }
        }
        return results;
    }

    /** 清空向量库 */
    public void clearVectorStore() {
        // Spring AI PgVectorStore.delete(empty list) = delete all
        vectorStore.delete(List.of());
        log.info("🧹 向量库已清空");
    }

    // ====== 文档读取 ======

    private String readDocument(Path path) throws IOException {
        String ext = getExt(path.getFileName().toString()).toLowerCase();
        return TEXT_EXT.contains(ext) ? readText(path) : readTika(path);
    }

    private String readText(Path path) throws IOException {
        TextReader reader = new TextReader(new FileSystemResource(path.toFile()));
        reader.setCharset(java.nio.charset.StandardCharsets.UTF_8);
        return reader.get().stream().map(Document::getText).collect(Collectors.joining("\n")).trim();
    }

    private String readTika(Path path) {
        TikaDocumentReader reader = new TikaDocumentReader(new FileSystemResource(path.toFile()));
        return reader.get().stream().map(Document::getText).collect(Collectors.joining("\n")).trim();
    }

    private String getExt(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1);
    }
}
