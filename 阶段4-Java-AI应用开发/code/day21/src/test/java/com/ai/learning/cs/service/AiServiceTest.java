package com.ai.learning.cs.service;

import com.ai.learning.cs.model.ChatResponse;
import com.ai.learning.cs.model.IntentType;
import com.ai.learning.cs.prompt.PromptTemplates;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AI 服务单元测试
 * 
 * 测试核心逻辑：意图识别、提示词路由、会话管理
 * 不依赖实际 API 调用（使用模拟数据和逻辑验证）
 */
public class AiServiceTest {

    @Test
    void testIntentTemplates_AllIntentsHaveContent() {
        // 验证每个意图都有对应的提示词模板
        for (IntentType intent : IntentType.values()) {
            String prompt = PromptTemplates.getSystemPrompt(intent);
            assertNotNull(prompt, "intent " + intent + " 应有提示词模板");
            assertFalse(prompt.isBlank(), "intent " + intent + " 的提示词不应为空");
            System.out.println("✅ " + intent.label + " → 提示词长度: " + prompt.length());
        }
    }

    @Test
    void testIntentClassifierPrompt() {
        // 验证分类器提示词包含所有意图
        String classifier = PromptTemplates.INTENT_CLASSIFIER;
        assertTrue(classifier.contains("order"), "分类器应包含 order");
        assertTrue(classifier.contains("refund"), "分类器应包含 refund");
        assertTrue(classifier.contains("tech"), "分类器应包含 tech");
        assertTrue(classifier.contains("complaint"), "分类器应包含 complaint");
        assertTrue(classifier.contains("general"), "分类器应包含 general");
    }

    @Test
    void testOrderPrompt_ContainsOrderKeywords() {
        String prompt = PromptTemplates.orderSystem();
        assertTrue(prompt.contains("订单") || prompt.contains("order"), "订单提示词应包含相关关键词");
    }

    @Test
    void testRefundPrompt_ContainsRefundGuidance() {
        String prompt = PromptTemplates.refundSystem();
        assertTrue(prompt.contains("退款") || prompt.contains("退货"), "退款提示词应包含退款/退货指导");
    }

    @Test
    void testTechSupportPrompt_ContainsCodeExample() {
        String prompt = PromptTemplates.techSupportSystem();
        assertTrue(prompt.contains("Java"), "技术支持提示词应提及 Java 示例");
    }

    @Test
    void testIntentCodeMapping() {
        // 验证 code 到 enum 的映射
        assertEquals(IntentType.ORDER, IntentType.fromCode("order"));
        assertEquals(IntentType.REFUND, IntentType.fromCode("refund"));
        assertEquals(IntentType.TECH_SUPPORT, IntentType.fromCode("tech"));
        assertEquals(IntentType.COMPLAINT, IntentType.fromCode("complaint"));
        assertEquals(IntentType.GENERAL, IntentType.fromCode("general"));
        // 未知 code 应返回 GENERAL
        assertEquals(IntentType.GENERAL, IntentType.fromCode("unknown"));
    }

    @Test
    void testAllIntentsHaveUniqueCodes() {
        // 验证所有意图 code 唯一
        long uniqueCount = java.util.Arrays.stream(IntentType.values())
            .map(i -> i.code)
            .distinct()
            .count();
        assertEquals(IntentType.values().length, uniqueCount, "每个意图应有唯一 code");
    }

    @Test
    void testTemplateIsolation() {
        // 验证不同意图的提示词内容不重复
        String orderPrompt = PromptTemplates.getSystemPrompt(IntentType.ORDER);
        String refundPrompt = PromptTemplates.getSystemPrompt(IntentType.REFUND);
        assertNotEquals(orderPrompt, refundPrompt, "不同意图的提示词应不同");
    }

    @Test
    void testSessionManager_CreateAndAddMessages() {
        SessionManager sm = new SessionManager();

        // 创建会话
        String sid = sm.createSession();
        assertNotNull(sid);
        assertFalse(sid.isBlank());

        // 添加消息
        sm.addUserMessage(sid, "你好");
        sm.addAssistantMessage(sid, "你好！有什么可以帮您？");

        List<Message> history = sm.getHistory(sid);
        assertEquals(2, history.size());
        assertInstanceOf(UserMessage.class, history.get(0));
        assertEquals("你好", history.get(0).getText());
    }

    @Test
    void testSessionManager_HistoryTrimming() {
        SessionManager sm = new SessionManager();
        String sid = sm.createSession();

        // 添加超过限制的消息
        for (int i = 0; i < 25; i++) {
            sm.addUserMessage(sid, "消息" + i);
            sm.addAssistantMessage(sid, "回复" + i);
        }

        List<Message> history = sm.getHistory(sid);
        assertTrue(history.size() <= 20, "历史应被裁剪到20条");
    }

    @Test
    void testSessionManager_MultipleSessions() {
        SessionManager sm = new SessionManager();

        String sid1 = sm.createSession();
        String sid2 = sm.createSession();

        sm.addUserMessage(sid1, "订单问题");
        sm.addUserMessage(sid2, "技术问题");

        // 验证会话隔离
        assertNotEquals(sid1, sid2);
        assertEquals(1, sm.getHistory(sid1).size());
        assertEquals(1, sm.getHistory(sid2).size());
    }

    @Test
    void testPromptTemplates_AllIntentsHaveDifferentContent() {
        String[] prompts = new String[IntentType.values().length];
        for (int i = 0; i < IntentType.values().length; i++) {
            prompts[i] = PromptTemplates.getSystemPrompt(IntentType.values()[i]);
        }
        // 验证所有提示词都不相同
        for (int i = 0; i < prompts.length; i++) {
            for (int j = i + 1; j < prompts.length; j++) {
                assertNotEquals(prompts[i], prompts[j], 
                    IntentType.values()[i].label + " 和 " + IntentType.values()[j].label + " 的提示词应不同");
            }
        }
    }
}
