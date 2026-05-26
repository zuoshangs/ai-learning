package knowledgebase;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 文档类 - 表示知识库中的一个文档文件
 */
public class Document {

    /** 文档路径 */
    private final String path;

    /** 文档完整内容 */
    private final String content;

    /** 文档切分后的文本块列表 */
    private final List<String> chunks;

    /**
     * 构造一个文档对象
     *
     * @param path    文档文件路径
     * @param content 文档完整内容
     */
    public Document(String path, String content) {
        this.path = path;
        this.content = content;
        this.chunks = new ArrayList<>();
    }

    /**
     * 构造一个文档对象（含预切分块）
     *
     * @param path    文档文件路径
     * @param content 文档完整内容
     * @param chunks  切分后的文本块
     */
    public Document(String path, String content, List<String> chunks) {
        this.path = path;
        this.content = content;
        this.chunks = chunks != null ? new ArrayList<>(chunks) : new ArrayList<>();
    }

    public String getPath() {
        return path;
    }

    public String getContent() {
        return content;
    }

    public List<String> getChunks() {
        return chunks;
    }

    public void addChunk(String chunk) {
        this.chunks.add(chunk);
    }

    /**
     * 获取文件名称（不含路径）
     */
    public String getFileName() {
        int lastSep = path.lastIndexOf('/');
        int lastBack = path.lastIndexOf('\\');
        int idx = Math.max(lastSep, lastBack);
        return idx >= 0 ? path.substring(idx + 1) : path;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Document document = (Document) o;
        return Objects.equals(path, document.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path);
    }

    @Override
    public String toString() {
        return "Document{path='" + path + "', contentLength=" + content.length() + ", chunks=" + chunks.size() + "}";
    }
}
