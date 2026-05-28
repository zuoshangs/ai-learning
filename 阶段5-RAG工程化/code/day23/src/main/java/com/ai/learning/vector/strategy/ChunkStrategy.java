package com.ai.learning.vector.strategy;

import java.util.List;

/**
 * 文档切分策略接口（与 Day 22 相同）
 */
public interface ChunkStrategy {
    String getName();
    List<String> chunk(String text);
}
