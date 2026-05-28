"""
Day 21 — 智能客服系统（Python 对照版）

模拟 Java 版的核心功能：
1. 意图识别（AI 分类用户问题）
2. 路由（根据意图加载不同提示词模板）
3. 多轮对话记忆（Session 管理）
4. 异常重试 + 降级
5. 流式输出（SSE 模拟）

用法：
  python3 cs_demo.py                           # 交互模式
  python3 cs_demo.py --test                     # 自动测试所有意图
"""

import requests
import json
import os
import sys
import time
import uuid

# ============================================================
# 配置
# ============================================================
API_KEY = os.environ.get("DEEPSEEK_API_KEY")
if not API_KEY:
    try:
        with open(os.path.expanduser("~/.hermes/.env")) as f:
            for line in f:
                if line.startswith("DEEPSEEK_API_KEY"):
                    API_KEY = line.split("=", 1)[1].strip().strip('"')
    except Exception:
        pass

API_URL = "https://api.deepseek.com/v1/chat/completions"
MODEL = "deepseek-chat"
MAX_RETRIES = 2

HEADERS = {
    "Authorization": f"Bearer {API_KEY}",
    "Content-Type": "application/json",
}


# ============================================================
# 意图枚举
# ============================================================
INTENTS = {
    "order": "订单查询",
    "refund": "退款售后",
    "tech": "技术支持",
    "complaint": "投诉反馈",
    "general": "普通咨询",
}


# ============================================================
# 提示词模板
# ============================================================
INTENT_CLASSIFIER_SYSTEM = """你是一个客服意图分类器。根据用户的第一句话，判断其意图类型。
只回复以下 JSON 格式，不要额外输出：

{{
    "intent": "order | refund | tech | complaint | general",
    "confidence": 0.0-1.0,
    "reason": "简短判断理由"
}}

判断规则：
- order：用户询问订单状态、物流、商品信息等
- refund：用户要求退款、退货、售后等
- tech：用户询问技术问题、功能使用、配置等
- complaint：用户表达不满、投诉、差评等
- general：普通咨询、问候、闲聊或其他
"""


def get_system_prompt(intent_code):
    """根据意图获取系统提示词"""
    prompts = {
        "order": """你是一个电商客服助手，专门处理订单查询。

服务范围：
- 查询订单状态和物流信息
- 修改配送地址和联系方式
- 商品库存和到货时间查询
- 发票开具和订单确认

回答要求：
- 需要订单号时，请礼貌地询问用户提供
- 模拟数据可以用"根据系统记录"开头
- 保持专业、耐心的语气
- 如果问题超出范围，引导到对应渠道
""",
        "refund": """你是一个售后客服专员，处理退款和售后问题。

服务范围：
- 退货申请流程指导
- 退款进度查询
- 质量问题处理方案
- 换货和维修安排
- 补偿和优惠方案

回答要求：
- 先安抚情绪，再解决问题
- 明确告知退款时效（通常3-7个工作日）
- 需要提供订单号/商品信息时请耐心询问
- 提供可操作的下一步指引
- 保持同理心，语气温暖
""",
        "tech": """你是一个技术支持工程师，解决产品使用问题。

服务范围：
- API/SDK 使用指导
- 错误码和异常排查
- 功能配置和参数设置
- 性能优化建议
- 版本兼容性问题

回答要求：
- 先确认问题现象和环境信息
- 提供分步骤的排查方案
- 如果问题复杂，建议提供日志信息
- 保持专业、条理清晰
""",
        "complaint": """你是一个投诉处理专员，处理用户投诉和不满。

处理流程：
1. 首先诚恳道歉，安抚情绪
2. 确认问题细节，记录关键信息
3. 给出明确的处理方案和时限
4. 承诺跟进并感谢反馈

回答要求：
- 始终保持真诚、谦逊的语气
- 不要推卸责任或找借口
- 每次回复都要包含具体的解决措施
- 如果无法当场解决，明确告知后续步骤和时限
- 结束时再次道歉并感谢
""",
        "general": """你是一个友好热情的智能客服助手。

服务范围：
- 公司/产品介绍
- 常见问题解答
- 引导到正确的服务渠道
- 闲聊和问候

回答要求：
- 热情友好，多用表情符号
- 简洁明了，不啰嗦
- 如果是复杂问题，引导用户提供更多信息
- 不知道的要诚实告知
""",
    }
    return prompts.get(intent_code, prompts["general"])


# ============================================================
# Session 管理
# ============================================================
class SessionManager:
    """管理多轮对话的上下文记忆"""

    def __init__(self):
        self.sessions = {}  # session_id -> [messages]

    def create_session(self):
        session_id = str(uuid.uuid4())[:8]
        self.sessions[session_id] = []
        print(f"  🔖 新会话: {session_id}")
        return session_id

    def add_user_message(self, session_id, message):
        if session_id not in self.sessions:
            self.sessions[session_id] = []
        self.sessions[session_id].append({"role": "user", "content": message})

    def add_assistant_message(self, session_id, message):
        if session_id in self.sessions:
            self.sessions[session_id].append({"role": "assistant", "content": message})

    def get_history(self, session_id):
        """返回历史消息列表（不含系统提示）"""
        return self.sessions.get(session_id, [])

    def truncate_history(self, session_id, max_rounds=10):
        """截断历史，保留最近 N 轮对话"""
        if session_id in self.sessions:
            msgs = self.sessions[session_id]
            if len(msgs) > max_rounds * 2:
                self.sessions[session_id] = msgs[-(max_rounds * 2):]


# ============================================================
# AI 调用
# ============================================================
def call_deepseek(messages, max_retries=MAX_RETRIES):
    """
    调用 DeepSeek API，带重试和降级

    Args:
        messages: [{"role": "system"|"user"|"assistant", "content": str}, ...]
        max_retries: 最大重试次数

    Returns:
        str: AI 回复内容
    """
    last_error = None

    for attempt in range(max_retries + 1):
        try:
            resp = requests.post(
                API_URL,
                headers=HEADERS,
                json={
                    "model": MODEL,
                    "messages": messages,
                    "temperature": 0.3,
                    "max_tokens": 1024,
                },
                timeout=30,
            )
            resp.raise_for_status()
            data = resp.json()
            return data["choices"][0]["message"]["content"]

        except requests.exceptions.Timeout:
            last_error = "请求超时"
            print(f"  ⚠️  第{attempt + 1}次调用超时")
        except requests.exceptions.RequestException as e:
            last_error = str(e)
            print(f"  ⚠️  第{attempt + 1}次调用失败: {e}")
        except (KeyError, json.JSONDecodeError) as e:
            last_error = str(e)
            print(f"  ⚠️  第{attempt + 1}次解析失败: {e}")

        if attempt < max_retries:
            wait = 2 ** attempt  # 指数退避：1s, 2s
            print(f"  ⏳ {wait}秒后重试...")
            time.sleep(wait)

    # 所有重试失败 → 降级
    print(f"  ❌ 所有重试失败 ({last_error})，使用降级回复")
    return "😔 抱歉，系统暂时繁忙，请稍后再试。如果问题紧急，请拨打客服热线 400-xxx-xxxx。"


# ============================================================
# 意图识别
# ============================================================
def detect_intent(user_message):
    """
    用 AI 识别用户意图

    Returns:
        (intent_code, confidence, reason)
    """
    messages = [
        {"role": "system", "content": INTENT_CLASSIFIER_SYSTEM},
        {"role": "user", "content": user_message},
    ]

    result = call_deepseek(messages, max_retries=1)

    # 解析 JSON 提取意图
    try:
        # 提取 JSON 部分（AI 可能带 markdown 包裹）
        json_str = result.strip()
        if "```json" in json_str:
            json_str = json_str.split("```json")[1].split("```")[0].strip()
        elif "```" in json_str:
            json_str = json_str.split("```")[1].split("```")[0].strip()

        data = json.loads(json_str)
        intent = data.get("intent", "general")
        confidence = data.get("confidence", 0.0)
        reason = data.get("reason", "")

        # 验证意图是否合法
        if intent not in INTENTS:
            intent = "general"

        print(f"  🎯 意图: {INTENTS[intent]} ({intent}, 置信度={confidence:.2f})")
        if reason:
            print(f"    理由: {reason}")
        return intent, confidence, reason

    except (json.JSONDecodeError, KeyError) as e:
        print(f"  ⚠️  意图解析失败: {e}，默认 general")
        return "general", 0.0, ""


# ============================================================
# 客服对话
# ============================================================
def customer_service_chat(session_manager, session_id, user_message):
    """
    客服对话主流程

    Args:
        session_manager: SessionManager 实例
        session_id: 会话 ID（为空则创建新会话）
        user_message: 用户消息

    Returns:
        (session_id, reply, intent_code)
    """
    # 1. 新会话 → 创建
    if not session_id:
        session_id = session_manager.create_session()

    # 2. 保存用户消息
    session_manager.add_user_message(session_id, user_message)

    # 3. 意图识别
    intent_code, confidence, reason = detect_intent(user_message)

    # 4. 构建消息列表
    system_prompt = get_system_prompt(intent_code)
    messages = [{"role": "system", "content": system_prompt}]
    messages.extend(session_manager.get_history(session_id))

    # 5. 调用 AI
    print(f"  💬 正在生成回复...", end=" ", flush=True)
    reply = call_deepseek(messages)

    # 6. 保存 AI 回复
    session_manager.add_assistant_message(session_id, reply)

    # 7. 截断历史（保留最近 10 轮）
    session_manager.truncate_history(session_id, max_rounds=10)

    return session_id, reply, intent_code


# ============================================================
# 自动测试
# ============================================================
def run_tests():
    """测试所有意图的识别 + 对话"""
    sm = SessionManager()

    test_cases = [
        # (描述, 消息, 期望意图)
        ("订单查询", "帮我查一下订单ORD-2026-001到哪了", "order"),
        ("退款售后", "我想退货退款，商品有质量问题", "refund"),
        ("技术支持", "我的API一直返回401错误，怎么解决？", "tech"),
        ("投诉反馈", "你们客服太差了，等了半小时没人理！", "complaint"),
        ("普通咨询", "你好，请问你们有什么产品？", "general"),
    ]

    print("=" * 60)
    print("🧪 客服系统自动化测试")
    print("=" * 60)

    session_id = None

    for desc, message, expected in test_cases:
        print(f"\n📝 [{desc}] 期望意图: {expected}")
        print(f"  用户: {message}")

        try:
            session_id, reply, detected = customer_service_chat(sm, session_id, message)

            # 验证意图识别
            status = "✅" if detected == expected else "⚠️"
            print(f"  {status} 实际意图: {detected} (期望: {expected})")
            print(f"  🤖 客服: {reply[:120]}...")

        except Exception as e:
            print(f"  ❌ 测试失败: {e}")

    # 测试多轮对话记忆
    print(f"\n📝 [多轮对话] 在同个会话中继续提问")
    session_id, _, _ = customer_service_chat(sm, session_id, "还在吗？我刚刚问的那个订单怎么样了？")
    history_count = len(sm.get_history(session_id))
    print(f"  🤖 回复成功，历史消息: {history_count} 条")

    print(f"\n{'=' * 60}")
    print("✅ 测试完成!")
    print(f"  总会话数: {len(sm.sessions)}")
    print(f"  历史消息总数: {sum(len(v) for v in sm.sessions.values())}")
    print(f"{'=' * 60}")


# ============================================================
# 交互模式
# ============================================================
def interactive_mode():
    """用户手动输入对话"""
    sm = SessionManager()
    print("=" * 60)
    print("🤖 智能客服系统 (Python 版)")
    print("输入 'quit' 退出, 'new' 新建会话")
    print("=" * 60)

    session_id = None

    while True:
        try:
            user_input = input("\n👤 你: ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\n👋 再见！")
            break

        if not user_input:
            continue
        if user_input.lower() == "quit":
            print("👋 再见！")
            break
        if user_input.lower() == "new":
            session_id = None
            print("  🔄 已创建新会话")
            continue

        print()
        session_id, reply, intent = customer_service_chat(sm, session_id, user_input)

        # 显示意图标签
        intent_label = INTENTS.get(intent, "普通咨询")
        print(f"\n  🏷️ [{intent_label}]")
        print(f"  🤖 客服: {reply}")

        print(f"\n  {'─' * 50}")


# ============================================================
# 主入口
# ============================================================
if __name__ == "__main__":
    if "--test" in sys.argv:
        run_tests()
    else:
        interactive_mode()
