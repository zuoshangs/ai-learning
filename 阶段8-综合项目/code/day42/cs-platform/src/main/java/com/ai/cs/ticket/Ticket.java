package com.ai.cs.ticket;

import java.time.Instant;

/**
 * Customer service ticket / work order model.
 */
public class Ticket {

    public enum Status {
        PENDING("待处理"),
        IN_PROGRESS("处理中"),
        RESOLVED("已解决"),
        CLOSED("已关闭");

        public final String displayName;
        Status(String displayName) { this.displayName = displayName; }
    }

    public enum Priority {
        LOW("低"),
        MEDIUM("中"),
        HIGH("高"),
        URGENT("紧急");

        public final String displayName;
        Priority(String displayName) { this.displayName = displayName; }
    }

    private String id;
    private String title;
    private String description;
    private String category;
    private Priority priority;
    private Status status;
    private String creatorName;
    private String creatorContact;
    private String assignee;
    private String sessionId;
    private String resolution;
    private long createdAt;
    private long updatedAt;
    private Long resolvedAt;

    public Ticket() {}

    public Ticket(String title, String description, String category, Priority priority, String creatorName) {
        this.id = java.util.UUID.randomUUID().toString();
        this.title = title;
        this.description = description;
        this.category = category != null ? category : "其他";
        this.priority = priority != null ? priority : Priority.MEDIUM;
        this.status = Status.PENDING;
        this.creatorName = creatorName != null ? creatorName : "匿名用户";
        this.createdAt = Instant.now().toEpochMilli();
        this.updatedAt = this.createdAt;
    }

    // --- Getters & Setters ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getCreatorName() { return creatorName; }
    public void setCreatorName(String creatorName) { this.creatorName = creatorName; }

    public String getCreatorContact() { return creatorContact; }
    public void setCreatorContact(String creatorContact) { this.creatorContact = creatorContact; }

    public String getAssignee() { return assignee; }
    public void setAssignee(String assignee) { this.assignee = assignee; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public Long getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Long resolvedAt) { this.resolvedAt = resolvedAt; }

    /** Time to resolution in hours */
    public String getResolutionTime() {
        if (resolvedAt == null) return "-";
        long hours = (resolvedAt - createdAt) / 3600000;
        if (hours < 1) return "< 1小时";
        if (hours < 24) return hours + "小时";
        return (hours / 24) + "天" + (hours % 24) + "小时";
    }

    public String getPreview() {
        if (description == null) return "";
        return description.length() > 80 ? description.substring(0, 80) + "..." : description;
    }

    public String getStatusDisplay() { return status.displayName; }
    public String getPriorityDisplay() { return priority.displayName; }
}
