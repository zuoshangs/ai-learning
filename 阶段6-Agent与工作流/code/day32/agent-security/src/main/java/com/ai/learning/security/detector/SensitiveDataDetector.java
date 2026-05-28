package com.ai.learning.security.detector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 敏感信息检测器 — 防止敏感数据泄露到 AI 请求或响应中
 */
@Component
public class SensitiveDataDetector {
    private static final Logger log = LoggerFactory.getLogger(SensitiveDataDetector.class);

    // 手机号: 1开头的11位数字
    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    // 身份证: 18位（最后一位可能是X）
    private static final Pattern ID_CARD = Pattern.compile("\\d{18}[0-9Xx]");
    // API Key 模式: sk- 开头
    private static final Pattern API_KEY = Pattern.compile("sk-[a-zA-Z0-9]{20,}");
    // 邮箱
    private static final Pattern EMAIL = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    // IP 地址
    private static final Pattern IP_ADDR = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");

    /**
     * 检测并脱敏文本中的敏感信息
     */
    public String sanitize(String text) {
        if (text == null || text.isBlank()) return text;

        String result = text;
        int before = result.length();

        result = PHONE.matcher(result).replaceAll("138****5678");
        result = ID_CARD.matcher(result).replaceAll("******************");
        result = API_KEY.matcher(result).replaceAll("sk-****...****");
        result = EMAIL.matcher(result).replaceAll("***@***.com");
        result = IP_ADDR.matcher(result).replaceAll("***.***.***.***");

        int after = result.length();
        if (before != after) {
            log.info("🔒 已脱敏 {} 个字符", before - after);
        }

        return result;
    }

    /**
     * 检查文本是否包含敏感信息
     */
    public boolean containsSensitive(String text) {
        return PHONE.matcher(text).find()
                || ID_CARD.matcher(text).find()
                || API_KEY.matcher(text).find()
                || EMAIL.matcher(text).find();
    }
}
