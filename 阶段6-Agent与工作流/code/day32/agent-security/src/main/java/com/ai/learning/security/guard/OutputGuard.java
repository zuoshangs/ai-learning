package com.ai.learning.security.guard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 输出审核 — 检查 AI 响应是否包含不当内容
 */
@Component
public class OutputGuard {
    private static final Logger log = LoggerFactory.getLogger(OutputGuard.class);

    // 不允许的输出模式
    private static final List<Pattern> BLOCKED_PATTERNS = List.of(
            Pattern.compile("(?i)你的API[密钥键].*是"),
            Pattern.compile("(?i)my (api )?key is"),
            Pattern.compile("(?i)(password|secret) is"),
            Pattern.compile("(?i)以下是.*(密码|密钥|配置)")
    );

    /**
     * 审核并修正输出内容
     */
    public String review(String text) {
        if (text == null || text.isBlank()) return text;

        for (Pattern pattern : BLOCKED_PATTERNS) {
            if (pattern.matcher(text).find()) {
                log.warn("🚫 输出包含敏感信息，已拦截");
                return "[内容已过滤：输出包含敏感信息]";
            }
        }

        return text;
    }

    /**
     * 快速检查：内容是否安全
     */
    public boolean isSafe(String text) {
        return review(text).equals(text);
    }
}
