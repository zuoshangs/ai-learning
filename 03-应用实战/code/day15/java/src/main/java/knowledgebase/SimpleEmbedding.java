package knowledgebase;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 简单文本向量化与相似度计算
 *
 * 基于词频（TF）的文本表示和余弦相似度计算。
 * 不使用外部 ML 库，实现简单但有效的语义检索。
 *
 * 工作流程：
 * 1. 对文本进行分词（中文按字符 + 英文按单词）
 * 2. 统计词频生成向量（Map<String, Double>）
 * 3. 使用余弦相似度比较两个向量的相似度
 */
public class SimpleEmbedding {

    /** 中文标点符号正则 */
    private static final Pattern CHINESE_PUNCTUATION = Pattern.compile("[\\p{P}\\p{S}。，、；：？！「」『』（）【】《》—…·]");

    /** 英文标点符号正则 */
    private static final Pattern ENGLISH_PUNCTUATION = Pattern.compile("[.,!?;:\"'()\\[\\]{}<>/\\\\|`~@#$%^&*_+=\\-]");

    /** 空白字符正则 */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** 中文词汇正则 */
    private static final Pattern CHINESE_CHARS = Pattern.compile("[\\u4e00-\\u9fff]");

    /** 英文单词正则 */
    private static final Pattern ENGLISH_WORDS = Pattern.compile("[a-zA-Z]+");

    /** 停用词列表（常见无意义词） */
    private static final Set<String> STOP_WORDS = Set.of(
            "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一",
            "一个", "上", "也", "很", "到", "说", "要", "去", "你", "会", "着",
            "没有", "看", "好", "自己", "这", "他", "她", "它", "们",
            "the", "a", "an", "is", "are", "was", "were", "be", "been",
            "being", "have", "has", "had", "do", "does", "did", "will",
            "would", "could", "should", "may", "might", "shall", "can",
            "in", "on", "at", "to", "for", "with", "by", "about", "against",
            "between", "into", "through", "during", "before", "after",
            "above", "below", "from", "up", "down", "of", "off", "over",
            "under", "again", "further", "then", "once", "here", "there",
            "when", "where", "why", "how", "all", "each", "every", "both",
            "few", "more", "most", "other", "some", "such", "no", "nor",
            "not", "only", "own", "same", "so", "than", "too", "very",
            "and", "but", "or", "if", "while", "that", "this", "it", "its"
    );

    /**
     * 对文本进行分词并返回词频向量
     *
     * 处理中文和英文混合文本：
     * - 中文：单字分词（去掉停用词和标点）
     * - 英文：按单词分词（转小写，去掉停用词）
     * - 数字：保留作为标记
     *
     * @param text 输入文本
     * @return 词频向量 (词 -> 频率)
     */
    public Map<String, Double> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return new HashMap<>();
        }

        // 统一转小写
        String normalized = text.toLowerCase().trim();

        // 提取中文单字
        List<String> chineseTokens = extractChineseChars(normalized);

        // 提取英文单词
        List<String> englishTokens = extractEnglishWords(normalized);

        // 合并所有 token
        List<String> allTokens = new ArrayList<>();
        allTokens.addAll(chineseTokens);
        allTokens.addAll(englishTokens);

        // 过滤停用词并统计词频
        Map<String, Long> freqMap = allTokens.stream()
                .filter(token -> !STOP_WORDS.contains(token))
                .filter(token -> token.length() > 0)
                .collect(Collectors.groupingBy(
                        token -> token,
                        Collectors.counting()
                ));

        // 转换为 double 并归一化（TF）
        long totalTokens = freqMap.values().stream().mapToLong(Long::longValue).sum();
        if (totalTokens == 0) {
            return new HashMap<>();
        }

        Map<String, Double> tfMap = new HashMap<>();
        for (Map.Entry<String, Long> entry : freqMap.entrySet()) {
            tfMap.put(entry.getKey(), (double) entry.getValue() / totalTokens);
        }

        return tfMap;
    }

    /**
     * 提取文本中的中文单字
     */
    private List<String> extractChineseChars(String text) {
        List<String> chars = new ArrayList<>();
        Matcher matcher = CHINESE_CHARS.matcher(text);
        while (matcher.find()) {
            chars.add(matcher.group());
        }
        return chars;
    }

    /**
     * 提取文本中的英文单词
     */
    private List<String> extractEnglishWords(String text) {
        List<String> words = new ArrayList<>();
        Matcher matcher = ENGLISH_WORDS.matcher(text);
        while (matcher.find()) {
            String word = matcher.group().toLowerCase();
            if (word.length() >= 2) { // 过滤单字母单词
                words.add(word);
            }
        }
        return words;
    }

    /**
     * 计算两个词频向量的余弦相似度
     *
     * 余弦相似度 = (A·B) / (||A|| * ||B||)
     * 值范围 [0, 1]，值越大越相似
     *
     * @param queryVec 查询向量
     * @param docVec   文档向量
     * @return 余弦相似度 (0.0 ~ 1.0)
     */
    public double computeSimilarity(Map<String, Double> queryVec, Map<String, Double> docVec) {
        if (queryVec == null || docVec == null || queryVec.isEmpty() || docVec.isEmpty()) {
            return 0.0;
        }

        // 计算点积
        double dotProduct = 0.0;
        for (Map.Entry<String, Double> entry : queryVec.entrySet()) {
            String key = entry.getKey();
            Double docValue = docVec.get(key);
            if (docValue != null) {
                dotProduct += entry.getValue() * docValue;
            }
        }

        // 计算向量模长
        double queryNorm = computeNorm(queryVec);
        double docNorm = computeNorm(docVec);

        // 避免除零
        if (queryNorm == 0.0 || docNorm == 0.0) {
            return 0.0;
        }

        return dotProduct / (queryNorm * docNorm);
    }

    /**
     * 计算向量模长 (L2范数)
     */
    private double computeNorm(Map<String, Double> vec) {
        double sum = 0.0;
        for (double value : vec.values()) {
            sum += value * value;
        }
        return Math.sqrt(sum);
    }
}
