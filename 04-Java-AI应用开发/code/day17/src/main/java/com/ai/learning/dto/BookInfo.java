package com.ai.learning.dto;

import java.util.List;

/**
 * 图书信息 — 结构化输出示例
 * AI 将返回符合此结构的 JSON，Spring AI 自动反序列化
 */
public class BookInfo {

    private String title;
    private String author;
    private int publishYear;
    private String genre;
    private double rating;
    private List<String> tags;
    private String summary;

    // Getters and Setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public int getPublishYear() { return publishYear; }
    public void setPublishYear(int publishYear) { this.publishYear = publishYear; }

    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }

    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    @Override
    public String toString() {
        return "BookInfo{" +
                "title='" + title + '\'' +
                ", author='" + author + '\'' +
                ", publishYear=" + publishYear +
                ", genre='" + genre + '\'' +
                ", rating=" + rating +
                ", tags=" + tags +
                '}';
    }
}
