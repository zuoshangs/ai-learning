package com.ai.learning.doc.strategy;

import com.ai.learning.doc.model.ChunkStrategyResult;
import com.ai.learning.doc.model.ChunkStrategyResult.ChunkSample;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 递归字符切分策略（类似 LangChain RecursiveCharacterTextSplitter）
 *
 * 按分隔符优先级递归切分：
 *   1. "\\n\\n" — 段落分隔
 *   2. "\\n"    — 行分隔
 *   3. "。" / "." — 句号
 *   4. "，" / "," — 逗号
 *   5. " "      — 空格
 *   6. ""       — 字符级
 *
 * 这是 LangChain JS/Python 版 RecursiveCharacterTextSplitter 的 Java 移植。
 * 优点是能在尽可能保持语义完整的情况下达到目标块大小。
 */
@Component
public class RecursiveCharacterChunkStrategy implements ChunkStrategy {

    /** 目标块大小（字符） */
    private final int chunkSize;

    /** 块间重叠字符数 */
    private final int overlapChars;

    /** 分隔符优先级从高到低 */
    private static final String[] SEPARATORS = {
        "\n\n",
        "\n",
        "。",
        ".",
        "，",
        ",",
        " ",
        ""
    };

    public RecursiveCharacterChunkStrategy() {
        this(1000, 100);
    }

    public RecursiveCharacterChunkStrategy(int chunkSize, int overlapChars) {
        this.chunkSize = chunkSize;
        this.overlapChars = overlapChars;
    }

    @Override
    public String getName() {
        return "RecursiveSplitter (LangChain式)";
    }

    @Override
    public ChunkStrategyResult chunk(String text) {
        List<String> chunks = splitText(text, SEPARATORS, 0);
        return buildResult(chunks);
    }

    /**
     * 递归切分核心逻辑
     *
     * @param text 待切分文本
     * @param separators 剩余分隔符数组
     * @param sepIndex 当前使用的分隔符索引
     */
    private List<String> splitText(String text, String[] separators, int sepIndex) {
        List<String> result = new ArrayList<>();

        if (text == null || text.isEmpty()) {
            return result;
        }

        String separator = separators[sepIndex];
        List<String> splits;

        // 用当前分隔符拆分
        if (separator.isEmpty()) {
            // 字符级：直接按字符拆
            splits = new ArrayList<>();
            for (char c : text.toCharArray()) {
                splits.add(String.valueOf(c));
            }
        } else {
            splits = splitBySeparator(text, separator);
        }

        // 合并小片段直到达到目标大小
        List<String> merged = mergeSplits(splits, separator);

        // 对仍然过大的片段递归使用下一个分隔符
        for (String segment : merged) {
            if (segment.length() > chunkSize && sepIndex + 1 < separators.length) {
                result.addAll(splitText(segment, separators, sepIndex + 1));
            } else {
                result.add(segment);
            }
        }

        return result;
    }

    /**
     * 按分隔符拆分，保留分隔符在原位（跟随在后面）
     */
    private List<String> splitBySeparator(String text, String separator) {
        List<String> parts = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int idx = text.indexOf(separator, start);
            if (idx < 0) {
                parts.add(text.substring(start));
                break;
            }
            // 将分隔符追加到前一段后面
            parts.add(text.substring(start, idx + separator.length()));
            start = idx + separator.length();
        }

        return parts;
    }

    /**
     * 合并碎片到目标块大小
     */
    private List<String> mergeSplits(List<String> splits, String separator) {
        List<String> merged = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String split : splits) {
            if (current.length() + split.length() > chunkSize && !current.isEmpty()) {
                // 当前块已满 → 保存
                merged.add(current.toString().trim());

                // 重叠：保留最后 overlapChars 字符到新块
                String overlapText = getOverlapText(current.toString(), overlapChars);
                current = new StringBuilder(overlapText);
            }

            current.append(split);
        }

        // 最后一段
        if (!current.isEmpty()) {
            merged.add(current.toString().trim());
        }

        return merged;
    }

    /**
     * 从文本末尾提取重叠部分
     */
    private String getOverlapText(String text, int overlapChars) {
        if (overlapChars <= 0 || text.length() <= overlapChars) {
            return "";
        }
        // 尽量在最后一个分隔符处截断
        String tail = text.substring(text.length() - overlapChars);
        int lastNewline = tail.indexOf('\n');
        return lastNewline >= 0 ? tail.substring(lastNewline) : tail;
    }

    private ChunkStrategyResult buildResult(List<String> chunks) {
        ChunkStrategyResult result = new ChunkStrategyResult();
        result.setStrategyName(getName());
        result.setDescription("chunkSize=" + chunkSize + "c, overlap=" + overlapChars + "c");

        result.setChunkCount(chunks.size());

        int[] sizes = chunks.stream().mapToInt(String::length).toArray();
        result.setMinChunkSize(TokenChunkStrategy.findMin(sizes));
        result.setMaxChunkSize(TokenChunkStrategy.findMax(sizes));
        result.setAvgChunkSize((int) Math.round(TokenChunkStrategy.average(sizes)));
        result.setTotalChunkChars(TokenChunkStrategy.sum(sizes));

        // 重叠率
        result.setOverlapRatio(Math.round(
            (1 - (double) result.getTotalChunkChars() / TokenChunkStrategy.sum(sizes)) * 10000.0) / 100.0);

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
