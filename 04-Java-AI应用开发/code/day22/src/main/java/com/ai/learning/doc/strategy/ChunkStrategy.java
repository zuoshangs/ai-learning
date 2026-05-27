package com.ai.learning.doc.strategy;

import com.ai.learning.doc.model.ChunkStrategyResult;

import java.util.List;

/**
 * 文档切分策略接口
 *
 * 每种策略实现两个方法：
 *   - getName()       — 策略唯一名称
 *   - chunk(text)     — 执行切分并返回统计结果
 */
public interface ChunkStrategy {

    /** 策略名称 */
    String getName();

    /** 执行切分 */
    ChunkStrategyResult chunk(String text);
}
