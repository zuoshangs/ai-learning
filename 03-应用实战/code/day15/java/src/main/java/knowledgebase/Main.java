package knowledgebase;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * 主程序入口 - 个人知识库问答系统
 *
 * 基于 RAG（检索增强生成）架构的知识库问答系统。
 * 工作流程：
 * 1. 从指定目录加载文本文档
 * 2. 切分文档为文本块
 * 3. 计算每个文本块的词频向量
 * 4. 用户提问时，将问题向量化并与所有文档块计算相似度
 * 5. 检索最相关的文档块作为上下文
 * 6. 调用 LLM API 生成带引用的回答
 *
 * 使用方式：
 *   export DEEPSEEK_API_KEY=your_api_key_here
 *   java -jar knowledge-base-qa-1.0-SNAPSHOT.jar [知识库目录路径]
 *
 * 如果未指定知识库目录，默认使用:
 *   ~/ai-learning/03-应用实战/code/day15/sample_docs/
 */
public class Main {

    /** 默认的知识库目录 */
    private static final String DEFAULT_DOCS_DIR = System.getProperty("user.home")
            + "/ai-learning/03-应用实战/code/day15/sample_docs";

    private final Config config;
    private final SimpleEmbedding embedding;
    private final VectorStore vectorStore;
    private final DocumentLoader documentLoader;
    private final QaEngine qaEngine;

    private boolean knowledgeBaseBuilt = false;

    public Main() {
        this.config = new Config();
        this.embedding = new SimpleEmbedding();
        this.vectorStore = new VectorStore(embedding);
        this.documentLoader = new DocumentLoader(config.getChunkSize(), config.getChunkOverlap());
        this.qaEngine = new QaEngine(config);
    }

    /**
     * 构建知识库 - 从目录加载文档并建立索引
     *
     * @param docsDir 文档目录路径
     * @throws IOException 读取文件失败时抛出
     */
    public void buildKnowledgeBase(String docsDir) throws IOException {
        System.out.println("=".repeat(60));
        System.out.println("  个人知识库问答系统 v1.0");
        System.out.println("=".repeat(60));
        System.out.println();
        System.out.println("正在加载知识库文档...");
        System.out.println("  文档目录: " + docsDir);
        System.out.println("  切分配置: chunkSize=" + config.getChunkSize()
                + ", chunkOverlap=" + config.getChunkOverlap());
        System.out.println();

        // 加载并切分文档
        List<Document> documents = documentLoader.loadDocuments(docsDir);

        if (documents.isEmpty()) {
            System.out.println("警告: 未找到任何文档文件！");
            System.out.println("请确保目录中包含 .txt 或 .md 文件。");
            return;
        }

        // 构建向量索引
        System.out.println();
        System.out.println("正在构建向量索引...");
        long startTime = System.currentTimeMillis();
        vectorStore.addDocuments(documents);
        long elapsed = System.currentTimeMillis() - startTime;

        // 统计
        int totalChunks = vectorStore.size();
        int totalChars = documents.stream().mapToInt(d -> d.getContent().length()).sum();

        System.out.println("  索引构建完成！");
        System.out.println("  " + documents.size() + " 个文档, "
                + totalChunks + " 个文本块, "
                + totalChars + " 个字符");
        System.out.println("  构建耗时: " + elapsed + " 毫秒");
        System.out.println();

        knowledgeBaseBuilt = true;
    }

    /**
     * 交互式问答循环
     */
    public void interactiveQA() {
        if (!knowledgeBaseBuilt) {
            System.out.println("错误: 知识库尚未构建，请先调用 buildKnowledgeBase()。");
            return;
        }

        System.out.println("-".repeat(60));
        System.out.println("  知识库问答模式已启动！");
        System.out.println("  输入问题开始问答，输入 'quit' 或 'exit' 退出");
        System.out.println("-".repeat(60));
        System.out.println();

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("问题: ");
            String question = scanner.nextLine().trim();

            if (question.isEmpty()) {
                continue;
            }

            if (question.equalsIgnoreCase("quit") || question.equalsIgnoreCase("exit")) {
                System.out.println("感谢使用，再见！");
                break;
            }

            // 执行检索和问答
            processQuestion(question);

            System.out.println(); // 空行分隔
        }

        scanner.close();
    }

    /**
     * 处理单个问题
     */
    private void processQuestion(String question) {
        try {
            // 1. 向量化查询
            System.out.println("  [1/3] 正在分析问题...");
            Map<String, Double> queryVec = embedding.tokenize(question);

            // 2. 检索相关文档块
            System.out.println("  [2/3] 正在检索知识库...");
            List<VectorStore.SearchResult> contexts = vectorStore.search(queryVec, config.getTopK());

            if (contexts.isEmpty()) {
                System.out.println("  抱歉，知识库中没有找到与问题相关的信息。");
                return;
            }

            // 打印检索结果
            System.out.println("  找到 " + contexts.size() + " 个相关文档块：");
            for (int i = 0; i < contexts.size(); i++) {
                VectorStore.SearchResult ctx = contexts.get(i);
                System.out.println("    [" + (i + 1) + "] " + ctx.getSourceFileName()
                        + " (相关度: " + String.format("%.4f", ctx.getScore()) + ")");
            }
            System.out.println();

            // 3. 生成回答
            System.out.println("  [3/3] 正在生成回答...");
            String answer = qaEngine.answer(question, contexts);

            // 打印回答
            System.out.println();
            System.out.println("回答:");
            System.out.println(answer);

        } catch (Exception e) {
            System.out.println("处理问题时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 程序入口
     */
    public static void main(String[] args) {
        try {
            // 读取知识库目录（支持命令行参数或默认路径）
            String docsDir;
            if (args.length > 0 && !args[0].isBlank()) {
                docsDir = args[0];
            } else {
                docsDir = DEFAULT_DOCS_DIR;
                System.out.println("提示: 可使用命令行参数指定知识库目录");
                System.out.println("  用法: java -jar knowledge-base-qa.jar <文档目录>");
                System.out.println("  使用默认目录: " + docsDir);
                System.out.println();
            }

            // 创建主程序实例
            Main main = new Main();
            System.out.println("配置信息: " + main.config);
            System.out.println();

            // 构建知识库
            main.buildKnowledgeBase(docsDir);

            // 进入问答模式
            main.interactiveQA();

        } catch (IllegalStateException e) {
            System.err.println("配置错误: " + e.getMessage());
            System.err.println();
            System.err.println("请设置必要的环境变量:");
            System.err.println("  export DEEPSEEK_API_KEY=your_api_key_here");
            System.err.println();
            System.err.println("可选环境变量:");
            System.err.println("  DEEPSEEK_BASE_URL  (默认: https://api.deepseek.com)");
            System.err.println("  DEEPSEEK_MODEL     (默认: deepseek-chat)");
            System.err.println("  CHUNK_SIZE         (默认: 500)");
            System.err.println("  CHUNK_OVERLAP      (默认: 50)");
            System.err.println("  TOP_K              (默认: 3)");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("文件读取错误: " + e.getMessage());
            System.exit(1);
        }
    }
}
