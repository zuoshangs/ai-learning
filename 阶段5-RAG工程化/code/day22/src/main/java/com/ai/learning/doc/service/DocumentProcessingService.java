package com.ai.learning.doc.service;

import com.ai.learning.doc.model.ChunkStrategyResult;
import com.ai.learning.doc.model.DocumentInfo;
import com.ai.learning.doc.strategy.ChunkStrategy;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 文档处理服务
 *
 * 职责：
 *   1. 从文件系统中加载文档（支持 txt / pdf / md / docx / json）
 *   2. 提取文本 + 元数据
 *   3. 用所有注册的 ChunkStrategy 分别切分
 *   4. 返回对比结果
 */
@Service
public class DocumentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingService.class);

    private final List<ChunkStrategy> strategies;

    public DocumentProcessingService(List<ChunkStrategy> strategies) {
        this.strategies = strategies;
    }

    /**
     * 处理单个文件 — 读取 → 提取 → 多策略切分 → 对比
     */
    public DocumentInfo processFile(String filePath) throws IOException {
        Path path = Path.of(filePath).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            throw new IOException("文件不存在: " + path);
        }

        String filename = path.getFileName().toString();
        String fileType = getFileExtension(filename).toLowerCase();
        long fileSize = Files.size(path);

        log.info("📄 处理文档: {} ({} bytes, {})", filename, fileSize, fileType);

        // 1. 读取文档 → 提取文本
        String extractedText = readDocument(path, fileType);

        // 2. 提取元数据
        Map<String, Object> metadata = extractMetadata(path, fileType, extractedText);

        // 3. 估算 Token 数（中英文混合：中文 ≈ 1.5 tokens/字，英文 ≈ 0.25 tokens/字母）
        int estimatedTokens = estimateTokens(extractedText);

        // 4. 所有策略执行切分
        List<ChunkStrategyResult> strategyResults = new ArrayList<>();
        for (ChunkStrategy strategy : strategies) {
            try {
                log.info("  策略: {} ...", strategy.getName());
                ChunkStrategyResult result = strategy.chunk(extractedText);
                strategyResults.add(result);
                log.info("    → {} 块, 平均 {} chars/块",
                    result.getChunkCount(), result.getAvgChunkSize());
            } catch (Exception e) {
                log.error("  策略 {} 执行失败: {}", strategy.getName(), e.getMessage());
                ChunkStrategyResult errorResult = new ChunkStrategyResult();
                errorResult.setStrategyName(strategy.getName());
                errorResult.setDescription("❌ 失败: " + e.getMessage());
                errorResult.setChunkCount(0);
                strategyResults.add(errorResult);
            }
        }

        // 5. 组装结果
        DocumentInfo info = new DocumentInfo();
        info.setFilename(filename);
        info.setFileType(fileType);
        info.setFileSizeBytes(fileSize);
        info.setTotalCharacters(extractedText.length());
        info.setTotalTokens(estimatedTokens);
        info.setMetadata(metadata);
        info.setStrategies(strategyResults);

        return info;
    }

    /**
     * 批量处理目录下所有文档
     */
    public List<DocumentInfo> processDirectory(String dirPath) throws IOException {
        Path dir = Path.of(dirPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            throw new IOException("不是目录: " + dir);
        }

        List<DocumentInfo> results = new ArrayList<>();
        List<Path> files;
        try (var stream = Files.list(dir)) {
            files = stream
                .filter(Files::isRegularFile)
                .sorted()
                .toList();
        }

        for (Path file : files) {
            String ext = getFileExtension(file.getFileName().toString()).toLowerCase();
            if (SUPPORTED_EXTENSIONS.contains(ext)) {
                try {
                    results.add(processFile(file.toString()));
                } catch (Exception e) {
                    log.error("处理 {} 失败: {}", file.getFileName(), e.getMessage());
                }
            }
        }

        return results;
    }

    // ==============================
    //  文档读取
    // ==============================

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("txt", "md", "json", "pdf", "docx", "doc", "html", "xml");

    /**
     * 根据文件类型读取文档内容
     */
    private String readDocument(Path path, String fileType) throws IOException {
        return switch (fileType) {
            case "txt", "md", "json", "xml", "html" -> readPlainText(path);
            case "pdf" -> readPdf(path);
            case "docx", "doc" -> readWithTika(path, fileType);
            default -> throw new IOException("不支持的文件类型: " + fileType);
        };
    }

    /** 读取纯文本文件 */
    private String readPlainText(Path path) throws IOException {
        Resource resource = new FileSystemResource(path.toFile());
        TextReader reader = new TextReader(resource);
        reader.setCharset(java.nio.charset.StandardCharsets.UTF_8);
        List<Document> docs = reader.get();
        return docs.stream()
            .map(Document::getText)
            .reduce("", (a, b) -> a + "\n" + b)
            .trim();
    }

    /** 使用 PDFBox 读取 PDF */
    private String readPdf(Path path) throws IOException {
        try (PDDocument pdfDoc = Loader.loadPDF(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(pdfDoc).trim();
        }
    }

    /** 使用 Spring AI Tika Document Reader 读取复杂格式 */
    private String readWithTika(Path path, String fileType) throws IOException {
        Resource resource = new FileSystemResource(path.toFile());
        TikaDocumentReader reader = new TikaDocumentReader(resource);
        List<Document> docs = reader.get();
        return docs.stream()
            .map(Document::getText)
            .reduce("", (a, b) -> a + "\n" + b)
            .trim();
    }

    // ==============================
    //  元数据提取
    // ==============================

    private Map<String, Object> extractMetadata(Path path, String fileType, String text) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("absolutePath", path.toAbsolutePath().toString());

        try {
            meta.put("lastModified", Files.getLastModifiedTime(path).toString());
        } catch (IOException e) {
            // ignore
        }

        // PDF 特有：页数
        if ("pdf".equals(fileType)) {
            try (PDDocument pdfDoc = Loader.loadPDF(path.toFile())) {
                meta.put("pageCount", pdfDoc.getNumberOfPages());
            } catch (Exception e) {
                meta.put("pageCount", "unknown");
            }
        }

        // 文本统计
        meta.put("lineCount", text.isEmpty() ? 0 : text.split("\n").length);
        meta.put("wordCount", text.isEmpty() ? 0 : text.split("\\s+").length);

        return meta;
    }

    // ==============================
    //  工具方法
    // ==============================

    private String getFileExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "";
    }

    /**
     * 估算 Token 数 — 中英文混合场景的工程近似
     *
     * OpenAI/DeepSeek Tokenizer 实际规则（简化）：
     *   - 中文：≈ 1 token / 1.5~2 个字符
     *   - 英文：≈ 1 token / 3~4 个字符
     *   - 混合：保守取 1 token / 2 字符
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        // 更精确的估算：中文 char * 0.7 + 英文 token * 0.25
        int chineseChars = 0;
        int otherChars = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A) {
                chineseChars++;
            } else if (!Character.isWhitespace(c)) {
                otherChars++;
            }
        }
        return (int) Math.ceil(chineseChars * 0.7 + otherChars * 0.25);
    }
}
