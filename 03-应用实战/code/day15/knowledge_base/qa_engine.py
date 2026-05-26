"""
问答引擎模块
============
通过 LLM API（兼容 OpenAI 格式）生成基于检索上下文的回答。
支持引用来源、多轮对话和流式输出。
"""

import json
import logging
from typing import List, Dict, Optional

from . import config

logger = logging.getLogger(__name__)


class QaEngine:
    """
    问答引擎。

    使用 LLM（如 DeepSeek）基于检索到的上下文生成回答。
    回答会自动包含来源引用，方便验证信息准确性。

    用法:
        >>> engine = QaEngine()
        >>> result = engine.answer("什么是 RAG？", context_chunks)
        >>> print(result["answer"])
        >>> print(result["citations"])
    """

    def __init__(
        self,
        api_key: Optional[str] = None,
        base_url: Optional[str] = None,
        model: Optional[str] = None,
        temperature: Optional[float] = None,
        max_tokens: Optional[int] = None,
    ):
        """
        初始化问答引擎。

        Args:
            api_key: API 密钥（默认: config.LLM_API_KEY）。
            base_url: API 基础地址。
            model: 模型名称。
            temperature: 生成温度（0~1，越低越确定）。
            max_tokens: 最大生成 token 数。
        """
        self.api_key = api_key or config.LLM_API_KEY
        self.base_url = base_url or config.LLM_BASE_URL
        self.model = model or config.LLM_MODEL
        self.temperature = temperature if temperature is not None else config.LLM_TEMPERATURE
        self.max_tokens = max_tokens or config.LLM_MAX_TOKENS

        # 对话历史（多轮对话支持）
        self.conversation_history: List[Dict] = []

        # API 客户端（延迟初始化）
        self._client = None

        # 检查 API Key 是否配置
        if not self.api_key:
            logger.warning(
                "⚠️  API Key 未配置！\n"
                "   请设置环境变量: export DEEPSEEK_API_KEY='your-key-here'\n"
                "   或在 .env 文件中配置。"
            )

    # ============================================================
    # 核心问答方法
    # ============================================================

    def answer(
        self,
        question: str,
        context_chunks: List[Dict],
        stream: bool = False,
    ) -> Dict:
        """
        基于检索上下文生成回答。

        Args:
            question: 用户问题。
            context_chunks: 检索到的上下文块列表（来自 VectorStore）。
            stream: 是否使用流式输出（默认: False）。

        Returns:
            Dict: 包含以下字段:
                - "answer": 生成的回答文本
                - "citations": 引用来源列表
                - "context_count": 使用的上下文块数
                - "model": 使用的模型
                - "success": 是否成功
                - "error": 错误信息（如果有）
        """
        # 构建带上下文的提示词
        prompt = self._build_prompt(question, context_chunks)

        # 构建消息
        messages = self._build_messages(prompt)
        citations = self._extract_citations(context_chunks)

        # 调用 API
        try:
            if stream:
                answer_text = self._call_api_stream(messages)
            else:
                answer_text = self._call_api(messages)

            result = {
                "answer": answer_text,
                "citations": citations,
                "context_count": len(context_chunks),
                "model": self.model,
                "success": True,
                "error": None,
            }

            # 更新对话历史
            self._update_history(question, answer_text)

            return result

        except Exception as e:
            error_msg = str(e)
            logger.error(f"API 调用失败: {error_msg}")

            return {
                "answer": "",
                "citations": citations,
                "context_count": len(context_chunks),
                "model": self.model,
                "success": False,
                "error": error_msg,
            }

    def answer_with_fallback(
        self,
        question: str,
        context_chunks: List[Dict],
        stream: bool = False,
    ) -> Dict:
        """
        带降级策略的问答。

        如果 LLM API 调用失败，尝试使用本地简单策略回答。

        Args:
            question: 用户问题。
            context_chunks: 检索到的上下文块。
            stream: 是否流式输出。

        Returns:
            Dict: 回答结果。
        """
        result = self.answer(question, context_chunks, stream)

        if not result["success"]:
            logger.info("API 调用失败，使用本地降级回答")
            result["answer"] = self._fallback_answer(question, context_chunks)
            result["success"] = True
            result["fallback"] = True

        return result

    # ============================================================
    # 提示词构建
    # ============================================================

    def _build_prompt(self, question: str, context_chunks: List[Dict]) -> str:
        """
        构建包含上下文的提示词。

        将检索到的文本块组装为结构化上下文，
        并指示 LLM 基于上下文回答且注明来源。

        Args:
            question: 用户问题。
            context_chunks: 上下文块列表。

        Returns:
            str: 完整的提示词文本。
        """
        # 组装上下文
        context_parts = []
        for i, chunk in enumerate(context_chunks):
            source = chunk.get("metadata", {}).get(
                "source", chunk.get("metadata", {}).get("title", f"来源 {i + 1}")
            )
            # 提取文件名用于引用
            filename = chunk.get("metadata", {}).get("filename", source)
            score = chunk.get("score", 0)

            context_parts.append(
                f"[{i + 1}] 来自《{filename}》"
                f"（相关度: {score:.2f}）:\n{chunk['text']}"
            )

        context_str = "\n\n---\n\n".join(context_parts)

        # 完整提示词
        prompt = f"""你是一个专业的个人知识库助手。请根据以下提供的参考资料回答用户的问题。

【参考上下文】
{context_str}

【用户问题】
{question}

【回答要求】
1. 请基于参考上下文回答问题，不要凭空编造信息。
2. 如果参考上下文中没有足够信息，请明确说明"根据现有资料，我无法回答这个问题"。
3. 在回答中引用对应的来源编号，格式如 [1]、[2]。
4. 回答应清晰、有条理，使用中文。
5. 如果问题涉及多个方面，请分点阐述。

【回答】"""

        return prompt

    def _build_messages(self, prompt: str) -> List[Dict]:
        """
        构建 API 消息列表（支持多轮对话）。

        Args:
            prompt: 当前提示词。

        Returns:
            List[Dict]: API 消息列表。
        """
        system_message = {
            "role": "system",
            "content": (
                "你是一个知识库问答助手，基于用户提供的参考资料回答问题。"
                "你的回答必须基于参考资料，并在引用时标注来源编号。"
                "如果资料不足，请如实告知用户。"
            )
        }

        messages = [system_message]

        # 加入对话历史（最近 N 轮）
        history = self.conversation_history[-6:]  # 保留最近 3 轮（6 条消息）
        messages.extend(history)

        messages.append({"role": "user", "content": prompt})

        return messages

    # ============================================================
    # API 调用
    # ============================================================

    def _get_client(self):
        """
        获取或创建 OpenAI 兼容客户端。

        Returns:
            OpenAI 客户端实例。

        Raises:
            ValueError: 如果 API Key 未配置。
        """
        if self._client is not None:
            return self._client

        if not self.api_key:
            raise ValueError(
                "API Key 未配置！请设置环境变量 DEEPSEEK_API_KEY。"
            )

        try:
            from openai import OpenAI

            self._client = OpenAI(
                api_key=self.api_key,
                base_url=self.base_url,
            )
            logger.info(f"API 客户端已创建: {self.base_url}")

        except ImportError:
            raise ImportError(
                "需要安装 openai 库: pip install openai>=1.0.0"
            )

        return self._client

    def _call_api(self, messages: List[Dict]) -> str:
        """
        调用 LLM API 生成回答（非流式）。

        Args:
            messages: 消息列表。

        Returns:
            str: 生成的回答文本。
        """
        client = self._get_client()

        logger.info(f"正在调用 {self.model} ...")

        response = client.chat.completions.create(
            model=self.model,
            messages=messages,
            temperature=self.temperature,
            max_tokens=self.max_tokens,
            stream=False,
        )

        answer = response.choices[0].message.content.strip()
        usage = getattr(response, "usage", None)

        if usage:
            logger.info(
                f"  ✓ 生成完成 (输入: {usage.prompt_tokens} tokens, "
                f"输出: {usage.completion_tokens} tokens)"
            )
        else:
            logger.info("  ✓ 生成完成")

        return answer

    def _call_api_stream(self, messages: List[Dict]) -> str:
        """
        调用 LLM API 生成回答（流式）。

        Args:
            messages: 消息列表。

        Returns:
            str: 完整回答文本。
        """
        client = self._get_client()

        logger.info(f"正在流式调用 {self.model} ...")

        stream = client.chat.completions.create(
            model=self.model,
            messages=messages,
            temperature=self.temperature,
            max_tokens=self.max_tokens,
            stream=True,
        )

        collected = []
        for chunk in stream:
            if chunk.choices[0].delta.content:
                content = chunk.choices[0].delta.content
                collected.append(content)
                print(content, end="", flush=True)

        print()  # 换行
        answer = "".join(collected).strip()
        logger.info(f"  ✓ 流式生成完成 ({len(answer)} 字符)")

        return answer

    # ============================================================
    # 辅助方法
    # ============================================================

    def _extract_citations(self, context_chunks: List[Dict]) -> List[Dict]:
        """
        从上下文块中提取引用信息。

        Args:
            context_chunks: 上下文块列表。

        Returns:
            List[Dict]: 引用列表，每个包含编号、来源、相关度等。
        """
        citations = []

        for i, chunk in enumerate(context_chunks):
            meta = chunk.get("metadata", {})
            citations.append({
                "index": i + 1,
                "source": meta.get("source", ""),
                "title": meta.get("title", meta.get("filename", f"来源 {i + 1}")),
                "filename": meta.get("filename", ""),
                "score": chunk.get("score", 0),
                "preview": chunk.get("text", "")[:100] + "...",
            })

        return citations

    def _update_history(self, question: str, answer: str):
        """
        更新对话历史。

        Args:
            question: 用户问题。
            answer: 系统回答。
        """
        self.conversation_history.append({"role": "user", "content": question})
        self.conversation_history.append({"role": "assistant", "content": answer})

        # 限制历史长度（最多保留 10 轮对话）
        max_history = 20  # 10 轮 × 2 条/轮
        if len(self.conversation_history) > max_history:
            self.conversation_history = self.conversation_history[-max_history:]

    def _fallback_answer(self, question: str, context_chunks: List[Dict]) -> str:
        """
        当 LLM API 不可用时的降级回答。

        简单提取相关度最高的上下文块作为回答。

        Args:
            question: 用户问题。
            context_chunks: 上下文块。

        Returns:
            str: 降级回答文本。
        """
        if not context_chunks:
            return "（由于 LLM API 不可用且没有检索到相关资料，无法回答问题。）"

        # 取最相关的一段
        best = context_chunks[0]
        source = best.get("metadata", {}).get("title", "未知来源")

        return (
            f"（LLM API 暂不可用，以下是检索到的最相关参考资料）\n\n"
            f"相关度: {best.get('score', 0):.2f}\n"
            f"来源: {source}\n\n"
            f"{best['text'][:500]}"
        )

    def clear_history(self):
        """清空对话历史。"""
        self.conversation_history.clear()
        logger.info("对话历史已清空")

    def get_history_summary(self) -> List[Dict]:
        """
        获取对话历史摘要。

        Returns:
            List[Dict]: 对话轮次摘要。
        """
        summary = []
        for i in range(0, len(self.conversation_history), 2):
            if i + 1 < len(self.conversation_history):
                summary.append({
                    "turn": len(summary) + 1,
                    "question": self.conversation_history[i]["content"][:100],
                    "answer_length": len(self.conversation_history[i + 1]["content"]),
                })
        return summary

    def __repr__(self) -> str:
        return (
            f"<QaEngine model={self.model} "
            f"history={len(self.conversation_history)//2} turns>"
        )
