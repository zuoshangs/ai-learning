/**
 * SearchAssistant.java — AI 搜索增强助手
 * 
 * 结合实时网页搜索 + RAG 知识库 + 信息融合
 * 第13天项目实战 — Java 版
 */

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SearchAssistant {

    // ═══════════════════════════════════════════
    // 简易向量存储
    // ═══════════════════════════════════════════

    static class VectorStore {
        List<String> documents = new ArrayList<>();
        List<double[]> vectors = new ArrayList<>();

        String[] tokenize(String text) {
            return text.toLowerCase()
                .replaceAll("[^\\w\\u4e00-\\u9fff]+", " ")
                .trim().split("\\s+");
        }

        double[] vectorize(String text) {
            String[] tokens = tokenize(text);
            Set<String> allTerms = new HashSet<>();
            for (String doc : documents) allTerms.addAll(Arrays.asList(tokenize(doc)));
            allTerms.addAll(Arrays.asList(tokens));

            double[] vec = new double[allTerms.size()];
            String[] termList = allTerms.toArray(new String[0]);
            for (String t : tokens) {
                for (int i = 0; i < termList.length; i++) {
                    if (termList[i].equals(t)) { vec[i] += 1.0; break; }
                }
            }
            return vec;
        }

        void addDocument(String text) {
            documents.add(text);
            vectors.add(vectorize(text));
        }

        double cosineSimilarity(double[] a, double[] b) {
            double dot = 0, na = 0, nb = 0;
            for (int i = 0; i < a.length; i++) {
                dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i];
            }
            return (na > 0 && nb > 0) ? dot / (Math.sqrt(na) * Math.sqrt(nb)) : 0;
        }

        List<Map.Entry<String, Double>> search(String query, int topK) {
            if (documents.isEmpty()) return List.of();
            double[] qVec = vectorize(query);
            List<Map.Entry<String, Double>> scored = new ArrayList<>();
            for (int i = 0; i < documents.size(); i++) {
                scored.add(Map.entry(documents.get(i), cosineSimilarity(qVec, vectors.get(i))));
            }
            scored.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            return scored.subList(0, Math.min(topK, scored.size()));
        }
    }

    // ═══════════════════════════════════════════
    // 搜索客户端（模拟）
    // ═══════════════════════════════════════════

    static class SearchClient {
        String engine;

        SearchClient(String engine) {
            this.engine = engine;
        }

        List<Map<String, String>> search(String query, int numResults) {
            if (engine.equals("mock")) return mockSearch(query, numResults);
            return List.of();
        }

        List<Map<String, String>> mockSearch(String query, int numResults) {
            List<Map<String, String>> results = new ArrayList<>();
            String[][] data = {
                {"关于「" + query + "」的最新报道",
                 "近日，" + query + " 领域取得了重要进展。",
                 "https://news.example.com/" + URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8)},
                {"深度分析：" + query + "的趋势与展望",
                 "本文深入分析了 " + query + " 的发展趋势。",
                 "https://analysis.example.com/" + URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8)},
                {query + " 入门指南与实践",
                 "一篇全面的 " + query + " 入门教程。",
                 "https://tutorial.example.com/" + URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8)},
            };
            for (int i = 0; i < Math.min(numResults, data.length); i++) {
                results.add(Map.of("title", data[i][0], "snippet", data[i][1], "url", data[i][2]));
            }
            return results;
        }
    }

    // ═══════════════════════════════════════════
    // 知识库
    // ═══════════════════════════════════════════

    static class KnowledgeBase {
        Map<String, VectorStore> stores = new LinkedHashMap<>();

        void addCategory(String name, List<String> docs) {
            VectorStore store = new VectorStore();
            for (String d : docs) store.addDocument(d);
            stores.put(name, store);
            System.out.printf("  📚 加载知识库 [%s]: %d 条%n", name, docs.size());
        }

        List<String> query(String question, int topK) {
            List<Map.Entry<Double, String>> scored = new ArrayList<>();
            for (var entry : stores.entrySet()) {
                for (var hit : entry.getValue().search(question, topK)) {
                    if (hit.getValue() > 0.1) {
                        scored.add(Map.entry(hit.getValue(),
                            "[" + entry.getKey() + "] " + hit.getKey()));
                    }
                }
            }
            scored.sort((a, b) -> Double.compare(b.getKey(), a.getKey()));
            return scored.subList(0, Math.min(topK, scored.size()))
                .stream().map(Map.Entry::getValue).collect(Collectors.toList());
        }
    }

    // ═══════════════════════════════════════════
    // 信息融合
    // ═══════════════════════════════════════════

    static class FusionEngine {
        String fuse(String query, List<Map<String, String>> searchResults,
                     List<String> kbResults, List<String> history) {
            StringBuilder sb = new StringBuilder();

            if (!history.isEmpty()) {
                sb.append("【对话历史】\n");
                for (int i = Math.max(0, history.size() - 4); i < history.size(); i++) {
                    sb.append(history.get(i)).append("\n");
                }
                sb.append("\n");
            }

            if (!searchResults.isEmpty()) {
                sb.append("【网络搜索结果】\n");
                int i = 1;
                for (var r : searchResults) {
                    sb.append(i++).append(". ").append(r.get("title")).append("\n");
                    sb.append("   摘要: ").append(r.get("snippet")).append("\n");
                    sb.append("   来源: ").append(r.get("url")).append("\n");
                }
                sb.append("\n");
            }

            if (!kbResults.isEmpty()) {
                sb.append("【本地知识库】\n");
                for (String doc : kbResults) sb.append(doc).append("\n");
                sb.append("\n");
            }

            return sb.toString();
        }
    }

    // ═══════════════════════════════════════════
    // 主 Agent
    // ═══════════════════════════════════════════

    static class Agent {
        SearchClient search = new SearchClient("mock");
        KnowledgeBase kb = new KnowledgeBase();
        FusionEngine fusion = new FusionEngine();
        List<String> history = new ArrayList<>();
        int searches = 0;
        int kbHits = 0;

        Agent() {
            kb.addCategory("AI技术", List.of(
                "Transformer 是 2017 年 Google 提出的深度学习架构",
                "GPT 是 Generative Pre-trained Transformer 的缩写",
                "RAG 是 Retrieval-Augmented Generation 的缩写"));
            kb.addCategory("编程", List.of(
                "Python 是一种解释型、面向对象的高级编程语言",
                "Java 是一种静态类型、面向对象的编程语言"));

            System.out.println("=".repeat(55));
            System.out.println("  🔍 AI 搜索增强助手 (Java版)");
            System.out.println("=".repeat(55));
        }

        boolean needsSearch(String query) {
            String[] keywords = {"新闻", "天气", "股价", "最新", "今天", "昨天",
                "news", "today", "stock", "price"};
            for (String kw : keywords) {
                if (query.contains(kw)) return true;
            }
            return false;
        }

        String chat(String query) {
            boolean shouldSearch = needsSearch(query);

            List<Map<String, String>> searchResults = List.of();
            List<String> kbResults = List.of();

            if (shouldSearch) {
                System.out.println("  🔍 正在搜索: " + query);
                searchResults = search.search(query, 3);
                searches++;
            }

            kbResults = kb.query(query, 2);
            if (!kbResults.isEmpty()) kbHits++;

            String context = fusion.fuse(query, searchResults, kbResults, history);

            StringBuilder prompt = new StringBuilder();
            if (shouldSearch && !searchResults.isEmpty()) {
                prompt.append("基于以下信息回答用户问题，标注来源。\n\n");
                prompt.append(context);
            } else {
                prompt.append("你是一个知识渊博的AI助手。回答简洁有条理。");
            }

            String response = String.format(
                "关于「%s」的查询结果：\n\n根据%s，%s 是当前热门话题。"
                + "更多信息请参考搜索结果。",
                query,
                shouldSearch ? "网络搜索" : "本地知识库",
                query
            );

            history.add("用户: " + query);
            history.add("助手: " + response.substring(0, Math.min(50, response.length())));

            if (!searchResults.isEmpty()) {
                response += "\n\n📎 来源：\n";
                int i = 1;
                for (var r : searchResults) {
                    response += "  [" + (i++) + "] " + r.get("url") + "\n";
                }
            }
            return response;
        }

        String getStats() {
            return String.format("""
                📊 使用统计
                  搜索次数: %d
                  知识库命中: %d
                  对话轮次: %d""",
                searches, kbHits, history.size() / 2);
        }
    }

    // ═══════════════════════════════════════════
    // 主入口
    // ═══════════════════════════════════════════

    public static void main(String[] args) {
        Agent agent = new Agent();
        Scanner scanner = new Scanner(System.in);

        System.out.println("\n💡 试试这些：");
        System.out.println("  「什么是 Transformer？」");
        System.out.println("  「今天有什么新闻？」");
        System.out.println("  「/stats」查看统计");

        while (true) {
            System.out.print("\n🧑 你: ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) continue;
            if (input.equalsIgnoreCase("quit") || input.equalsIgnoreCase("exit")) {
                System.out.println("👋 再见！");
                break;
            }
            if (input.equals("/stats")) {
                System.out.println(agent.getStats());
                continue;
            }
            String response = agent.chat(input);
            System.out.println("\n🤖 小搜: " + response);
        }
        scanner.close();
    }
}
