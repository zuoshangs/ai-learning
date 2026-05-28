package com.ai.learning.security.detector;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 注入检测器 — 基于规则的正则匹配
 *
 * 检测攻击类型：
 * - command_injection: 命令注入
 * - prompt_leak: 提示词泄露
 * - role_hijack: 角色劫持
 * - data_extraction: 数据提取
 * - harmful_content: 有害内容
 */
@Component
public class InjectionDetector {
    private static final Logger log = LoggerFactory.getLogger(InjectionDetector.class);

    @Value("${security.injection.pattern-file:classpath:injection-patterns.json}")
    private Resource patternResource;

    private final Map<String, List<Pattern>> compiledPatterns = new LinkedHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        try {
            loadPatterns();
            log.info("✅ 注入检测器已初始化，共 {} 个模式类别",
                    compiledPatterns.values().stream().mapToLong(List::size).sum());
        } catch (Exception e) {
            log.warn("⚠️ 加载注入模式文件失败，使用内置默认模式: {}", e.getMessage());
            loadDefaultPatterns();
        }
    }

    /**
     * 从 JSON 文件加载模式
     */
    private void loadPatterns() throws Exception {
        try (InputStream is = patternResource.getInputStream()) {
            Map<String, Object> root = objectMapper.readValue(is, new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            Map<String, List<String>> patterns = (Map<String, List<String>>) root.get("patterns");

            for (Map.Entry<String, List<String>> entry : patterns.entrySet()) {
                String category = entry.getKey();
                List<Pattern> compiled = entry.getValue().stream()
                        .map(regex -> Pattern.compile(regex, Pattern.CASE_INSENSITIVE))
                        .collect(Collectors.toList());
                compiledPatterns.put(category, compiled);
            }
        }
    }

    /**
     * 内置默认模式（文件加载失败时的 fallback）
     */
    private void loadDefaultPatterns() {
        compiledPatterns.put("prompt_leak", List.of(
                Pattern.compile("忽略.*(指令|命令|规则|设定)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("ignore.*(instruction|prompt|rule)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("bypass.*(system|rule|security)", Pattern.CASE_INSENSITIVE)
        ));
        compiledPatterns.put("role_hijack", List.of(
                Pattern.compile("DAN\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("jailbreak", Pattern.CASE_INSENSITIVE)
        ));
    }

    /**
     * 检测输入是否存在注入攻击
     *
     * @param text 用户输入文本
     * @return 检测结果（空列表表示安全）
     */
    public DetectionResult analyze(String text) {
        if (text == null || text.isBlank()) {
            return new DetectionResult(false, Collections.emptyList());
        }

        List<DetectionFinding> findings = new ArrayList<>();

        for (Map.Entry<String, List<Pattern>> entry : compiledPatterns.entrySet()) {
            String category = entry.getKey();
            for (Pattern pattern : entry.getValue()) {
                var matcher = pattern.matcher(text);
                if (matcher.find()) {
                    findings.add(new DetectionFinding(
                            category,
                            pattern.pattern(),
                            matcher.group(),
                            matcher.start()
                    ));
                }
            }
        }

        boolean isAttack = !findings.isEmpty();
        if (isAttack) {
            log.warn("⚠️ 检测到注入攻击！类别: {}, 位置: {}",
                    findings.stream().map(DetectionFinding::category).distinct().collect(Collectors.joining(",")),
                    findings.getFirst().position());
        }

        return new DetectionResult(isAttack, findings);
    }

    /**
     * 检测结果
     */
    public record DetectionResult(boolean isAttack, List<DetectionFinding> findings) {
        public String getSummary() {
            if (!isAttack) return "✅ 安全";
            return "🚫 检测到 " + findings.size() + " 处攻击: "
                    + findings.stream()
                    .map(f -> f.category() + "(" + f.matchedText() + ")")
                    .collect(Collectors.joining(", "));
        }
    }

    /**
     * 单个发现
     */
    public record DetectionFinding(String category, String pattern, String matchedText, int position) {}
}
