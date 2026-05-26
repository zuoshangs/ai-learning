"""
配置模块
========
集中管理系统配置项，支持通过环境变量覆盖默认值。
"""

import os


# ============================================================
# LLM API 配置
# ============================================================

# DeepSeek API Key（从环境变量读取）
LLM_API_KEY = os.getenv("DEEPSEEK_API_KEY", "")

# LLM API 基础地址（默认 DeepSeek，可切换为 OpenAI 等）
LLM_BASE_URL = os.getenv("LLM_BASE_URL", "https://api.deepseek.com/v1")

# 使用的模型名称
LLM_MODEL = os.getenv("LLM_MODEL", "deepseek-chat")

# LLM 请求参数
LLM_TEMPERATURE = float(os.getenv("LLM_TEMPERATURE", "0.7"))
LLM_MAX_TOKENS = int(os.getenv("LLM_MAX_TOKENS", "2048"))


# ============================================================
# Embedding 配置
# ============================================================

# Sentence-Transformer 模型名称（轻量级，适合本地运行）
EMBEDDING_MODEL = "sentence-transformers/all-MiniLM-L6-v2"

# HuggingFace 镜像源（国内下载加速）
os.environ["HF_ENDPOINT"] = "https://hf-mirror.com"


# ============================================================
# 文档检索配置
# ============================================================

# 文本分块大小（字符数）
CHUNK_SIZE = 500

# 分块重叠（字符数）—— 保持上下文连贯性
CHUNK_OVERLAP = 50

# 检索返回的 Top-K 结果数
TOP_K = 3

# 相似度阈值（低于此值的检索结果将被过滤掉）
SIMILARITY_THRESHOLD = 0.3


# ============================================================
# 文档目录配置
# ============================================================

# 默认文档目录（相对于项目根目录）
DOCS_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "sample_docs")


# ============================================================
# 向量存储配置
# ============================================================

# 索引文件保存路径
INDEX_SAVE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "index_data")


# ============================================================
# 验证函数
# ============================================================

def validate_config() -> list:
    """
    验证配置项是否有效，返回警告/错误信息列表。

    Returns:
        List[str]: 配置问题列表，空列表表示无问题。
    """
    warnings = []

    if not LLM_API_KEY:
        warnings.append("⚠️  DEEPSEEK_API_KEY 未设置！问答功能将不可用。")

    if CHUNK_SIZE <= 0:
        warnings.append("⚠️  CHUNK_SIZE 必须大于 0，已重置为默认值 500。")

    if TOP_K <= 0:
        warnings.append("⚠️  TOP_K 必须大于 0，已重置为默认值 3。")

    if not os.path.exists(DOCS_DIR):
        warnings.append(f"📂  文档目录不存在: {DOCS_DIR}")

    return warnings


# 模块加载时自动验证
_config_warnings = validate_config()
