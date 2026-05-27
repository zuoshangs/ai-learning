"""
Python 对照：多轮对话 + 流式输出

与 Java Spring AI 版本功能对应：
1. 多轮对话记忆 — 手动管理历史
2. 流式输出 — SSE 逐块接收
"""
import os, json, sys, requests

def load_api_key():
    key = os.environ.get("DEEPSEEK_API_KEY")
    if key: return key
    env = os.path.expanduser("~/.hermes/.env")
    if os.path.exists(env):
        with open(env) as f:
            for line in f:
                if "DEEPSEEK_API_KEY" in line:
                    return line.split("=",1)[1].strip().strip("'\"")
    raise ValueError("DEEPSEEK_API_KEY not found")

API_KEY = load_api_key()
URL = "https://api.deepseek.com/chat/completions"
HEADERS = {"Authorization": f"Bearer {API_KEY}", "Content-Type": "application/json"}


class ChatSession:
    """
    多轮对话会话 — 对应 Java ChatService
    
    核心原理：
    每次调用时，把历史消息列表发给 API，
    API 根据上下文理解"之前说了什么"。
    """
    
    def __init__(self, system_prompt: str = "你是一个AI助手"):
        self.messages = [{"role": "system", "content": system_prompt}]
    
    def chat(self, message: str) -> str:
        """
        同步对话 — 对应 Java chatService.chat(sessionId, message)
        把消息加入历史，调用 API，保存回复
        """
        self.messages.append({"role": "user", "content": message})
        
        payload = {
            "model": "deepseek-chat",
            "messages": self.messages,
            "temperature": 0.7,
            "max_tokens": 2048
        }
        
        resp = requests.post(URL, headers=HEADERS, json=payload, timeout=60)
        data = resp.json()
        reply = data["choices"][0]["message"]["content"]
        
        self.messages.append({"role": "assistant", "content": reply})
        return reply
    
    def chat_stream(self, message: str):
        """
        流式对话 — 对应 Java chatService.streamChat()
        用 stream=True 逐块接收，模拟 SSE 打字机效果
        """
        self.messages.append({"role": "user", "content": message})
        
        payload = {
            "model": "deepseek-chat",
            "messages": self.messages,
            "temperature": 0.7,
            "max_tokens": 2048,
            "stream": True  # 启用流式
        }
        
        collected = []
        resp = requests.post(URL, headers=HEADERS, json=payload, stream=True, timeout=120)
        
        for line in resp.iter_lines():
            if line:
                line = line.decode('utf-8')
                if line.startswith('data: '):
                    data_str = line[6:]
                    if data_str == '[DONE]':
                        break
                    chunk = json.loads(data_str)
                    delta = chunk['choices'][0]['delta']
                    if 'content' in delta:
                        content = delta['content']
                        collected.append(content)
                        yield content  # 逐块返回（打字机效果）
        
        # 保存完整回复到历史
        full_reply = ''.join(collected)
        self.messages.append({"role": "assistant", "content": full_reply})
    
    def get_history(self):
        """查看历史"""
        return self.messages
    
    def clear(self):
        """清空历史（保留system prompt）"""
        system = self.messages[0]
        self.messages = [system]


def demo_sync():
    """演示同步多轮对话"""
    print("=" * 55)
    print("  📝 演示1：同步多轮对话（记忆测试）")
    print("=" * 55)
    
    session = ChatSession()
    
    # 第1轮
    reply1 = session.chat("你好，我叫小明")
    print(f"用户：你好，我叫小明")
    print(f"AI：{reply1[:50]}...")
    
    # 第2轮
    reply2 = session.chat("我今天学了 Spring AI")
    print(f"\n用户：我今天学了 Spring AI")
    print(f"AI：{reply2[:50]}...")
    
    # 第3轮 — 测试记忆
    reply3 = session.chat("我叫什么名字？我今天学了什么？")
    print(f"\n用户：我叫什么名字？我今天学了什么？")
    print(f"AI：{reply3}")
    
    # 验证
    assert "小明" in reply3, "❌ AI忘记了我的名字"
    print("\n✅ AI记住了之前的对话！")

    print(f"\n历史记录：{len(session.get_history())} 条消息")


def demo_stream():
    """演示流式输出"""
    print("\n" + "=" * 55)
    print("  📝 演示2：流式输出（打字机效果）")
    print("=" * 55)
    
    session = ChatSession()
    collected = []
    
    print("用户：写一首关于AI的诗")
    print("AI：", end="", flush=True)
    
    for chunk in session.chat_stream("写一首关于AI的诗，4句"):
        print(chunk, end="", flush=True)
        collected.append(chunk)
    
    print("\n\n✅ 流式输出完成")
    print(f"共收到 {len(collected)} 个数据块，合计 {len(''.join(collected))} 字符")


if __name__ == "__main__":
    demo_sync()
    demo_stream()
