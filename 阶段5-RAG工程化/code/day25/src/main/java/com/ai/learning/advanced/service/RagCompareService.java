package com.ai.learning.advanced.service;

import com.ai.learning.advanced.model.RagResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 高级 RAG 对比服务
 *
 * 对同一条查询，用三种技术分别检索 + 标准检索，
 * 并列展示结果，方便对比效果。
 */
@Service
public class RagCompareService {

    private static final Logger log = LoggerFactory.getLogger(RagCompareService.class);

    private final QueryRewriteService rewriteService;
    private final HydeSearchService hydeService;
    private final ParentDocService parentDocService;
    private final VectorStore vectorStore;

    public RagCompareService(QueryRewriteService rewriteService,
                             HydeSearchService hydeService,
                             ParentDocService parentDocService,
                             VectorStore vectorStore) {
        this.rewriteService = rewriteService;
        this.hydeService = hydeService;
        this.parentDocService = parentDocService;
        this.vectorStore = vectorStore;
    }

    public Map<String, Object> compare(String query, int topK) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", query);

        try {
            result.put("direct", rewriteService.search(query, topK));
        } catch (Exception e) {
            result.put("direct", Map.of("error", e.getMessage()));
        }

        // 查询重写
        try {
            result.put("rewrite", rewriteService.search(query, topK));
        } catch (Exception e) {
            result.put("rewrite", Map.of("error", e.getMessage()));
        }

        // HyDE
        try {
            result.put("hyde", hydeService.search(query, topK));
        } catch (Exception e) {
            result.put("hyde", Map.of("error", e.getMessage()));
        }

        // 父文档
        try {
            result.put("parent-doc", parentDocService.search(query, topK));
        } catch (Exception e) {
            result.put("parent-doc", Map.of("error", e.getMessage()));
        }

        return result;
    }
}
