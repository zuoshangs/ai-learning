package com.ai.learning.knowledge.ingestion;

import com.ai.learning.knowledge.model.DocumentChunk;
import com.ai.learning.knowledge.model.KnowledgeDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档切分服务 — 支持多种切分策略
 * 继承 Day 22 的切分能力
 */
@Service
public class ChunkingService {

    private static final Logger log = LoggerFactory.getLogger(ChunkingService.class);

    /** 默认块大小（字符数） */
    private static final int DEFAULT_CHUNK_SIZE = 500;

    /** 重叠字符数 */
    private static final int CHUNK_OVERLAP = 50;

    /** 父文档最大块数（超过则分多个父文档） */
    private static final int PARENT_MAX_CHUNKS = 5;

    /**
     * 将文档切分为块（递归字符切分 + 重叠）
     * 策略：优先按段落切，过长的段落再按句子切
     */
    public List<DocumentChunk> chunkDocument(KnowledgeDocument doc) {
        String content = doc.getContent();
        if (content == null || content.isBlank()) {
            log.warn("文档 {} 内容为空，跳过切分", doc.getId());
            return List.of();
        }

        // 1. 先按段落切
        String[] paragraphs = content.split("\n\n+");

        List<String> rawChunks = new ArrayList<>();
        for (String para : paragraphs) {
            para = para.trim();
            if (para.isBlank()) continue;

            if (para.length() <= DEFAULT_CHUNK_SIZE) {
                rawChunks.add(para);
            } else {
                // 过长的段落再按句子切
                rawChunks.addAll(splitBySentences(para));
            }
        }

        // 2. 如果段落太多，合并小段落
        List<String> mergedChunks = mergeSmallChunks(rawChunks);

        // 3. 添加重叠
        List<String> overlappedChunks = addOverlap(mergedChunks);

        // 4. 构建 DocumentChunk 列表
        List<DocumentChunk> chunks = new ArrayList<>();
        for (int i = 0; i < overlappedChunks.size(); i++) {
            String chunkId = doc.getId() + "-chunk-" + i;
            DocumentChunk chunk = new DocumentChunk(
                chunkId, doc.getId(),
                overlappedChunks.get(i), i, doc.getSource()
            );

            // 父文档：每 PARENT_MAX_CHUNKS 个块为一个父文档
            int parentGroup = i / PARENT_MAX_CHUNKS;
            chunk.setParentDocId(doc.getId() + "-parent-" + parentGroup);

            // 计算该父文档组内的块数
            int groupStart = parentGroup * PARENT_MAX_CHUNKS;
            int groupEnd = Math.min(groupStart + PARENT_MAX_CHUNKS, overlappedChunks.size());
            chunk.setTotalParentChunks(groupEnd - groupStart);

            chunks.add(chunk);
        }

        log.info("📄 {} 切分: {} 字 → {} 块 (父文档组数={})",
            doc.getTitle(), content.length(), chunks.size(),
            (chunks.size() + PARENT_MAX_CHUNKS - 1) / PARENT_MAX_CHUNKS);

        return chunks;
    }

    /** 按句子切分 */
    private List<String> splitBySentences(String text) {
        List<String> sentences = new ArrayList<>();
        // 中文句号、问号、感叹号、英文句点
        String[] parts = text.split("(?<=[。！？.!?])");
        StringBuilder current = new StringBuilder();

        for (String part : parts) {
            if (current.length() + part.length() > DEFAULT_CHUNK_SIZE && !current.isEmpty()) {
                sentences.add(current.toString().trim());
                current = new StringBuilder();
            }
            current.append(part);
        }
        if (!current.isEmpty()) {
            sentences.add(current.toString().trim());
        }

        return sentences;
    }

    /** 合并小段落（< 100 字的与下一个合并） */
    private List<String> mergeSmallChunks(List<String> chunks) {
        List<String> merged = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();

        for (String chunk : chunks) {
            if (buffer.length() + chunk.length() <= DEFAULT_CHUNK_SIZE) {
                if (buffer.length() > 0) buffer.append("\n\n");
                buffer.append(chunk);
            } else {
                if (buffer.length() > 0) {
                    merged.add(buffer.toString());
                }
                buffer = new StringBuilder(chunk);
            }
        }
        if (buffer.length() > 0) {
            merged.add(buffer.toString());
        }

        return merged;
    }

    /** 添加重叠 — 每个块尾部追加下一块的头 */
    private List<String> addOverlap(List<String> chunks) {
        if (chunks.size() <= 1) return chunks;

        List<String> overlapped = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String current = chunks.get(i);
            if (i < chunks.size() - 1) {
                String next = chunks.get(i + 1);
                String overlap = next.substring(0, Math.min(CHUNK_OVERLAP, next.length()));
                current = current + "\n[重叠: " + overlap + "]";
            }
            overlapped.add(current);
        }
        return overlapped;
    }
}
