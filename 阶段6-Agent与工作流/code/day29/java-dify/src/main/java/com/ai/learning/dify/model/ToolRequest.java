package com.ai.learning.dify.model;

public class ToolRequest {
    private String query;
    private String params;

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public String getParams() { return params; }
    public void setParams(String params) { this.params = params; }
}