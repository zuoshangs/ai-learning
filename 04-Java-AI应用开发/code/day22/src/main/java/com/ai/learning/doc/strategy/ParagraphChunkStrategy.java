package com.ai.learning.doc.strategy;

import com.ai.learning.doc.model.ChunkStrategyResult;
import com.ai.learning.doc.model.ChunkStrategyResult.ChunkSample;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 段落级别切分策略（语义切分）
 *
 * 按段落（连续空行分隔）进行切分，保留语义完整性：
 *   - 每个段落作为一个独立块
 *   - 过长段落（> maxChars）再按句子切分
 *   - 过短段落与下一段合并
 *
 * 这是最简单的"语义切分"方式 — 不丢失段落上下文
 */
@Component
public class ParagraphChunkStrategy implements ChunkStrategy {

    /** 最大段落字符数（超出则按句子拆） */
    private final int maxChunkChars;

    /** 最小段落字符数（低于此值合并） */
    private final int minChunkChars;

    public ParagraphChunkStrategy() {
        this(1500, 200);
    }

    public ParagraphChunkStrategy(int maxChunkChars, int minChunkChars) {
        this.maxChunkChars = maxChunkChars;
        this.minChunkChars = minChunkChars;
    }

    @Override
    public String getName() {
        return "ParagraphSplitter (语义)";
    }

    @Override
    public ChunkStrategyResult chunk(String text) {
        String[] paragraphs = text.split("\\n\\s*\\n");
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String para : paragraphs) {
            para = para.trim();
            if (para.isEmpty()) continue;

            // 过长段落：按句子拆分
            if (para.length() > maxChunkChars) {
                // 先 flush 当前积累
                if (!current.isEmpty()) {
                    chunks.add(current.toString().trim());
                    current = new StringBuilder();
                }
                // 按句子拆分长段落
                List<String> sentences = splitSentences(para);
                for (String sentence : sentences) {
                    if (current.length() + sentence.length() > maxChunkChars && !current.isEmpty()) {
                        chunks.add(current.toString().trim());
                        current = new StringBuilder();
                    }
                    current.append(sentence).append(" ");
                }
                continue;
            }

            // 当前积累 + 新段落 > maxChars → flush 当前
            if (current.length() + para.length() > maxChunkChars && !current.isEmpty()) {
                chunks.add(current.toString().trim());
                current = new StringBuilder();
            }

            current.append(para).append("\n\n");

            // 当前积累 > minChars 就 flush（防止无限积累）
            if (current.length() >= minChunkChars && !current.isEmpty()
                && current.length() >= maxChunkChars * 0.8) {
                chunks.add(current.toString().trim());
                current = new StringBuilder();
            }
        }

        // 最后一段
        if (!current.isEmpty()) {
            chunks.add(current.toString().trim());
        }

        return buildResult(chunks);
    }

    /**
     * 简单句子拆分 — 按中英文句号、问号、感叹号、换行符
     */
    private List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        // 匹配：中文句号/问号/感叹号 + 英文句点/问号/感叹号 + 换行
        String[] parts = text.split("(?<=[。！？.!?\\n])\\s*");
        for (String part : parts) {
            part = part.trim();
            if (!part.isEmpty()) {
                sentences.add(part);
            }
        }
        return sentences;
    }

    private ChunkStrategyResult buildResult(List<String> chunks) {
        ChunkStrategyResult result = new ChunkStrategyResult();
        result.setStrategyName(getName());
        result.setDescription("max=" + maxChunkChars + "c, min=" + minChunkChars + "c");

        result.setChunkCount(chunks.size());

        int[] sizes = chunks.stream().mapToInt(String::length).toArray();
        result.setMinChunkSize(TokenChunkStrategy.findMin(sizes));
        result.setMaxChunkSize(TokenChunkStrategy.findMax(sizes));
        result.setAvgChunkSize((int) Math.round(TokenChunkStrategy.average(sizes)));
        result.setTotalChunkChars(TokenChunkStrategy.sum(sizes));

        // 段落切分无重叠
        result.setOverlapRatio(0);

        List<ChunkSample> samples = new ArrayList<>();
        for (int i = 0; i < Math.min(3, chunks.size()); i++) {
            String sample = chunks.get(i);
            String preview = sample.length() > 120
                ? sample.substring(0, 120) + "..."
                : sample;
            samples.add(new ChunkSample(i, sample.length(), preview));
        }
        result.setSamples(samples);

        return result;
    }
}
