package com.ai.learning.dify.model;

public class ToolResponse {
    private boolean success;
    private String result;
    private String error;

    public ToolResponse() {}

    public ToolResponse(boolean success, String result) {
        this.success = success;
        this.result = result;
    }

    public ToolResponse(boolean success, String result, String error) {
        this.success = success;
        this.result = result;
        this.error = error;
    }

    public static ToolResponse ok(String result) {
        return new ToolResponse(true, result);
    }

    public static ToolResponse fail(String error) {
        return new ToolResponse(false, null, error);
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}