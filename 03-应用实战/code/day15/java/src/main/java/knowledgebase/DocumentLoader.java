package knowledgebase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档加载器 - 从文件系统加载文档并切分
 *
 * 支持 .txt 和 .md 格式的文本文件。
 * 使用简单的递归字符切分策略，按指定块大小和重叠大小切分文本。
 */
public class DocumentLoader {

    private final int chunkSize;
    private final int chunkOverlap;

    /**
     * 构造文档加载器
     *
     * @param chunkSize    切分块大小（字符数）
     * @param chunkOverlap 块间重叠大小（字符数）
     */
    public DocumentLoader(int chunkSize, int chunkOverlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize 必须大于 0");
        }
        if (chunkOverlap < 0 || chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException("chunkOverlap 必须在 0 到 chunkSize-1 之间");
        }
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    /**
     * 从指定目录递归加载所有文本文档
     *
     * @param dirPath 目录路径
     * @return 文档列表
     * @throws IOException 如果读取文件出错
     */
    public List<Document> loadDocuments(String dirPath) throws IOException {
        Path dir = Paths.get(dirPath);
        if (!Files.exists(dir)) {
            throw new IOException("目录不存在: " + dirPath);
        }
        if (!Files.isDirectory(dir)) {
            throw new IOException("路径不是目录: " + dirPath);
        }

        List<Document> documents = new ArrayList<>();
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String fileName = file.getFileName().toString().toLowerCase();
                // 只处理文本文件
                if (fileName.endsWith(".txt") || fileName.endsWith(".md")) {
                    try {
                        String content = Files.readString(file, StandardCharsets.UTF_8);
                        Document doc = new Document(file.toString(), content);
                        // 立即切分文档
                        List<String> chunks = splitText(content);
                        chunks.forEach(doc::addChunk);
                        documents.add(doc);
                        System.out.println("  加载文档: " + file + " (" + content.length() + " 字符, " + chunks.size() + " 个块)");
                    } catch (IOException e) {
                        System.err.println("  警告: 无法读取文件 " + file + " - " + e.getMessage());
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                System.err.println("  警告: 访问文件失败 " + file + " - " + exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });

        System.out.println("共加载 " + documents.size() + " 个文档");
        return documents;
    }

    /**
     * 将文本切分为块
     *
     * 使用简单的递归字符切分策略：
     * 1. 如果文本长度 <= chunkSize，直接作为一个块
     * 2. 否则，从 chunkSize 位置向前查找最后一个换行符或句号
     * 3. 如果找到合适分割点，从此处切分；否则在 chunkSize 处硬切
     * 4. 递归处理剩余文本
     *
     * @param text 要切分的文本
     * @return 文本块列表
     */
    public List<String> splitText(String text) {
        List<String> chunks = new ArrayList<>();
        splitTextRecursive(text, chunks);
        return chunks;
    }

    /**
     * 递归切分文本
     */
    private void splitTextRecursive(String text, List<String> chunks) {
        if (text == null || text.isBlank()) {
            return;
        }

        // 移除首尾空白
        text = text.trim();

        if (text.length() <= chunkSize) {
            if (!text.isBlank()) {
                chunks.add(text);
            }
            return;
        }

        // 在 chunkSize 范围内寻找最佳切分点
        int splitPos = findSplitPoint(text, chunkSize);

        // 提取当前块
        String chunk = text.substring(0, splitPos).trim();
        if (!chunk.isBlank()) {
            chunks.add(chunk);
        }

        // 剩余文本（带重叠）
        int overlapStart = Math.max(0, splitPos - chunkOverlap);
        String remaining = text.substring(overlapStart).trim();
        if (!remaining.isBlank()) {
            splitTextRecursive(remaining, chunks);
        }
    }

    /**
     * 寻找最佳切分位置
     *
     * 优先在换行符处切分，其次在句号处，最后在空格处。
     * 如果都没找到，在 maxPos 处硬切。
     *
     * @param text   完整文本
     * @param maxPos 最大切分位置
     * @return 实际切分位置
     */
    private int findSplitPoint(String text, int maxPos) {
        int actualMax = Math.min(maxPos, text.length());

        // 1. 从 actualMax 向前查找换行符
        for (int i = actualMax; i >= Math.max(0, actualMax - 50); i--) {
            if (text.charAt(i) == '\n') {
                return i + 1; // 包含换行符后的位置
            }
        }

        // 2. 查找句号（中文句号和英文句号）
        for (int i = actualMax; i >= Math.max(0, actualMax - 30); i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '.' || c == '！' || c == '？' || c == '\n') {
                return i + 1;
            }
        }

        // 3. 查找空格
        for (int i = actualMax; i >= Math.max(0, actualMax - 20); i--) {
            if (text.charAt(i) == ' ') {
                return i + 1;
            }
        }

        // 4. 硬切
        return actualMax;
    }
}
