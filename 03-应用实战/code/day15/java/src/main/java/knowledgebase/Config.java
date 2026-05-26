package knowledgebase;

/**
 * 配置类 - 从环境变量读取配置
 *
 * 支持的环境变量：
 *   DEEPSEEK_API_KEY    - DeepSeek API 密钥（必填）
 *   DEEPSEEK_BASE_URL   - API 基础地址（可选，默认 https://api.deepseek.com）
 *   DEEPSEEK_MODEL      - 模型名称（可选，默认 deepseek-chat）
 *   CHUNK_SIZE          - 文本切分块大小（可选，默认 500）
 *   CHUNK_OVERLAP       - 文本切分重叠大小（可选，默认 50）
 *   TOP_K               - 检索返回的文档块数量（可选，默认 3）
 */
public class Config {

    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    private static final String DEFAULT_MODEL = "deepseek-chat";
    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int DEFAULT_CHUNK_OVERLAP = 50;
    private static final int DEFAULT_TOP_K = 3;

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int chunkSize;
    private final int chunkOverlap;
    private final int topK;

    public Config() {
        this.apiKey = getEnvOrThrow("DEEPSEEK_API_KEY", "请设置环境变量 DEEPSEEK_API_KEY");
        this.baseUrl = getEnvOrDefault("DEEPSEEK_BASE_URL", DEFAULT_BASE_URL);
        this.model = getEnvOrDefault("DEEPSEEK_MODEL", DEFAULT_MODEL);
        this.chunkSize = getEnvIntOrDefault("CHUNK_SIZE", DEFAULT_CHUNK_SIZE);
        this.chunkOverlap = getEnvIntOrDefault("CHUNK_OVERLAP", DEFAULT_CHUNK_OVERLAP);
        this.topK = getEnvIntOrDefault("TOP_K", DEFAULT_TOP_K);
    }

    /**
     * 读取必需的环境变量，如果缺失则抛出异常
     */
    private String getEnvOrThrow(String key, String errorMessage) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(errorMessage);
        }
        return value.trim();
    }

    /**
     * 读取环境变量，如果缺失则返回默认值
     */
    private String getEnvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isBlank()) ? value.trim() : defaultValue;
    }

    /**
     * 读取整数型环境变量
     */
    private int getEnvIntOrDefault(String key, int defaultValue) {
        String value = System.getenv(key);
        if (value != null && !value.isBlank()) {
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                System.err.println("警告: 环境变量 " + key + " 的值 '" + value + "' 不是有效整数，使用默认值 " + defaultValue);
            }
        }
        return defaultValue;
    }

    // ===== Getters =====

    public String getApiKey() {
        return apiKey;
    }

    public String getBaseUrl() {
        // 移除末尾可能的斜杠
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public String getModel() {
        return model;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public int getTopK() {
        return topK;
    }

    /**
     * 获取聊天补全 API 端点地址
     */
    public String getChatEndpoint() {
        return getBaseUrl() + "/v1/chat/completions";
    }

    @Override
    public String toString() {
        return "Config{" +
                "baseUrl='" + baseUrl + '\'' +
                ", model='" + model + '\'' +
                ", chunkSize=" + chunkSize +
                ", chunkOverlap=" + chunkOverlap +
                ", topK=" + topK +
                '}';
    }
}
