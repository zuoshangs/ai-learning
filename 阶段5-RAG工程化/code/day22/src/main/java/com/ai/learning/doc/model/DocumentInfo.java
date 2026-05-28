package com.ai.learning.doc.model;

import java.util.List;
import java.util.Map;

/**
 * 文档处理结果 — 包含文件信息、切分策略对比详情
 */
public class DocumentInfo {

    private String filename;              // 文件名
    private String fileType;              // 文件类型 (txt / pdf / docx / md / json)
    private long fileSizeBytes;           // 文件大小
    private int totalCharacters;          // 原始文本字符数
    private int totalTokens;              // 原始文本估算 Token 数
    private Map<String, Object> metadata; // 额外元数据（PDF 页数等）
    private List<ChunkStrategyResult> strategies; // 各策略结果

    public DocumentInfo() {}

    // --- Getters / Setters ---

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public int getTotalCharacters() { return totalCharacters; }
    public void setTotalCharacters(int totalCharacters) { this.totalCharacters = totalCharacters; }

    public int getTotalTokens() { return totalTokens; }
    public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public List<ChunkStrategyResult> getStrategies() { return strategies; }
    public void setStrategies(List<ChunkStrategyResult> strategies) { this.strategies = strategies; }

    @Override
    public String toString() {
        return "DocumentInfo{filename='" + filename + "', type=" + fileType +
               ", size=" + fileSizeBytes + "B, chars=" + totalCharacters +
               ", tokens≈" + totalTokens + ", strategies=" + strategies.size() + "}";
    }
}
