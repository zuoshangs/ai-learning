package com.ai.learning.cs.model;

/**
 * 客服意图枚举
 * 每个枚举值对应一个提示词模板
 */
public enum IntentType {
    /** 普通咨询（默认） */
    GENERAL("general", "普通咨询"),

    /** 订单查询 */
    ORDER("order", "订单查询"),

    /** 退款/售后 */
    REFUND("refund", "退款售后"),

    /** 技术支持 */
    TECH_SUPPORT("tech", "技术支持"),

    /** 投诉 */
    COMPLAINT("complaint", "投诉反馈");

    public final String code;
    public final String label;

    IntentType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static IntentType fromCode(String code) {
        for (IntentType t : values()) {
            if (t.code.equals(code)) return t;
        }
        return GENERAL;
    }
}
