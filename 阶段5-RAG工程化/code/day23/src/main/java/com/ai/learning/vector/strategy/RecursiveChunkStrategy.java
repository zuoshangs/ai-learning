package com.ai.learning.vector.strategy;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 递归字符切分 — LangChain 式，多级分隔符
 */
@Component
public class RecursiveChunkStrategy implements ChunkStrategy {

    private static final String[] SEPARATORS = {"\n\n", "\n", "。", ".", "，", ",", " ", ""};
    private final int chunkSize;
    private final int overlap;

    public RecursiveChunkStrategy() { this(1000, 100); }
    public RecursiveChunkStrategy(int chunkSize, int overlap) {
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    @Override
    public String getName() { return "RecursiveSplitter (LangChain式)"; }

    @Override
    public List<String> chunk(String text) {
        return split(text, SEPARATORS, 0);
    }

    private List<String> split(String text, String[] seps, int idx) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) return result;

        String sep = seps[idx];
        List<String> parts = sep.isEmpty()
            ? text.chars().mapToObj(c -> String.valueOf((char) c)).toList()
            : splitBy(text, sep);

        List<String> merged = merge(parts);
        for (String seg : merged) {
            if (seg.length() > chunkSize && idx + 1 < seps.length) {
                result.addAll(split(seg, seps, idx + 1));
            } else {
                result.add(seg);
            }
        }
        return result;
    }

    private List<String> splitBy(String text, String sep) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int i = text.indexOf(sep, start);
            if (i < 0) { parts.add(text.substring(start)); break; }
            parts.add(text.substring(start, i + sep.length()));
            start = i + sep.length();
        }
        return parts;
    }

    private List<String> merge(List<String> parts) {
        List<String> merged = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String p : parts) {
            if (cur.length() + p.length() > chunkSize && !cur.isEmpty()) {
                merged.add(cur.toString().trim());
                String overlapText = overlap > 0 && cur.length() > overlap
                    ? cur.substring(cur.length() - overlap) : "";
                cur = new StringBuilder(overlapText);
            }
            cur.append(p);
        }
        if (!cur.isEmpty()) merged.add(cur.toString().trim());
        return merged;
    }
}
