package com.ai.learning.knowledge.model;

import java.util.List;
import java.util.Map;

/**
 * 文档摄入请求
 */
public class IngestRequest {

    private String title;
    private String content;
    private String source;
    private String docType = "txt";

    public String getTitle() { return title; }
    public IngestRequest setTitle(String title) { this.title = title; return this; }

    public String getContent() { return content; }
    public IngestRequest setContent(String content) { this.content = content; return this; }

    public String getSource() { return source; }
    public IngestRequest setSource(String source) { this.source = source; return this; }

    public String getDocType() { return docType; }
    public IngestRequest setDocType(String docType) { this.docType = docType; return this; }
}

/**
 * 批量摄入请求
 */
class BatchIngestRequest {
    private List<IngestRequest> documents;

    public List<IngestRequest> getDocuments() { return documents; }
    public BatchIngestRequest setDocuments(List<IngestRequest> documents) { this.documents = documents; return this; }
}

/**
 * 摄入状态响应
 */
class IngestStatusResponse {
    private String status;       // pending / processing / completed / failed
    private String docId;
    private int totalChunks;
    private String error;

    public String getStatus() { return status; }
    public IngestStatusResponse setStatus(String status) { this.status = status; return this; }

    public String getDocId() { return docId; }
    public IngestStatusResponse setDocId(String docId) { this.docId = docId; return this; }

    public int getTotalChunks() { return totalChunks; }
    public IngestStatusResponse setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; return this; }

    public String getError() { return error; }
    public IngestStatusResponse setError(String error) { this.error = error; return this; }
}
