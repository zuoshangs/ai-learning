package com.ai.cs.knowledge;

import java.time.Instant;

/**
 * Knowledge document model.
 */
public class Document {

    private String id;
    private String title;
    private String content;
    private String category;
    private String tags;
    private long createdAt;
    private long updatedAt;

    public Document() {}

    public Document(String id, String title, String content, String category) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.category = category;
        this.createdAt = Instant.now().toEpochMilli();
        this.updatedAt = this.createdAt;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    /** Truncate content for list views */
    public String getPreview() {
        if (content == null) return "";
        return content.length() > 120 ? content.substring(0, 120) + "..." : content;
    }
}
