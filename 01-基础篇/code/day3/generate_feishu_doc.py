"""
飞书文档生成工具 — 将课程 Markdown 转为飞书文档
适配飞书 Docx API 可用 block_type
"""
import os
import sys
import requests
import time

APP_ID = "cli_a96cf906c738dcc7"
APP_SECRET = "EIy92uxSScnqYyfrBHkhMbF4qSw6SVjO"
BASE = "https://open.feishu.cn/open-apis"


def api_call(method, url, **kwargs):
    """带重试的 API 调用"""
    for attempt in range(5):
        try:
            if method == "POST":
                resp = requests.post(url, timeout=15, **kwargs)
            else:
                resp = requests.get(url, timeout=15, **kwargs)
            return resp.json()
        except Exception as e:
            if attempt < 4:
                wait = 2 ** attempt
                print(f"  [重试 {attempt+1}/5, {wait}s] {type(e).__name__}")
                time.sleep(wait)
            else:
                raise e


def get_token():
    data = api_call("POST", f"{BASE}/auth/v3/tenant_access_token/internal",
        json={"app_id": APP_ID, "app_secret": APP_SECRET})
    if data.get("code") != 0:
        raise Exception(f"Get token failed: {data}")
    print(f"  ✅ Token 获取成功 (expire={data.get('expire')}s)")
    return data["tenant_access_token"]


def create_doc(token, title):
    data = api_call("POST", f"{BASE}/docx/v1/documents",
        headers={"Authorization": f"Bearer {token}"},
        json={"title": title})
    if data.get("code") != 0:
        raise Exception(f"Create doc failed: {data}")
    return data["data"]["document"]["document_id"]


def add_blocks(token, doc_id, parent_id, blocks, label=""):
    if not blocks:
        return
    data = api_call("POST",
        f"{BASE}/docx/v1/documents/{doc_id}/blocks/{parent_id}/children",
        headers={"Authorization": f"Bearer {token}"},
        json={"children": blocks, "index": -1})
    if data.get("code") != 0:
        err = data.get("msg", "")
        field = ""
        violations = data.get("error", {}).get("field_violations", [])
        if violations:
            field = f" ({violations[0].get('field','')})"
        print(f"  ⚠ {label}{err}{field}")
    return data


def text_block(text, bold=False, inline_code=False):
    style = {}
    if bold: style["bold"] = True
    if inline_code: style["inline_code"] = True
    return {
        "block_type": 2,
        "text": {
            "elements": [{"text_run": {"content": text, "text_element_style": style}}],
            "style": {"align": 1}
        }
    }


def heading_block(text, level=2):
    bt_map = {1: 3, 2: 4, 3: 5, 4: 6}
    bt = bt_map.get(level, 4)
    return {
        "block_type": bt,
        f"heading{level}": {
            "elements": [{"text_run": {"content": text, "text_element_style": {}}}]
        }
    }


def code_block_lines(code_text, language="Python"):
    """用文本块（inline_code样式）模拟代码块"""
    lines = code_text.split("\n")
    blocks = []
    blocks.append(text_block(f"  [{language}]", inline_code=True))
    for line in lines:
        text = f"  {line}" if line.strip() else "  "
        blocks.append(text_block(text, inline_code=True))
    return blocks


def bullet_block(text, bold_prefix=""):
    elements = []
    if bold_prefix:
        elements.append({"text_run": {"content": bold_prefix, "text_element_style": {"bold": True}}})
    elements.append({"text_run": {"content": text, "text_element_style": {}}})
    return {
        "block_type": 12,
        "bullet": {"elements": elements, "style": {"align": 1}}
    }


def divider_block():
    return {"block_type": 22, "divider": {}}


def generate_course_doc(doc_id, token):
    print("  📝 添加课程内容...")

    def add(blocks, label=""):
        add_blocks(token, doc_id, doc_id, blocks, label)
        time.sleep(0.5)

    # === 标题 ===
    add([heading_block("第3天：高级提示词策略", 1)], "标题")
    add([
        text_block("学习目标：掌握 Chain-of-Thought（思维链）和 Tree-of-Thought（思维树）等高级提示策略，让大模型执行复杂的多步推理任务"),
        text_block("代码语言：Python + Java 双版本  |  预计时间：2小时"),
    ], "元信息")

    # === 1. 为什么需要高级提示词 ===
    add([heading_block("1. 为什么需要高级提示词", 2)], "1")
    add([heading_block("1.1 基础提示词的局限", 3)], "1.1")
    add([text_block("还记得第 2 天学的 Zero-shot 和 Few-shot 吗？它们对简单任务效果很好，但遇到需要多步推理的问题时，模型容易「跳步」而出错。")], "1.1a")
    add([text_block("核心问题：大模型在推理类任务上，如果不引导它「显式思考」，它倾向于直接跳到答案。", bold=True)], "1.1b")

    add(code_block_lines("""问题：长方形的长是宽的2倍，周长36厘米。面积是多少？
❌ 直接问 → 可能胡猜一个数
✅ 加「让我们一步一步思考」→ 先算宽→算长→算面积→正确！""", "对比"), "代码1")

    add([heading_block("1.2 三种提问方式对比", 3)], "1.2")
    add([
        bullet_block(" — 直接问问题，适合简单事实问答 ⭐", "Zero-shot  "),
        bullet_block(" — 加「一步一步思考」，适合逻辑推理 ⭐⭐⭐", "Zero-shot CoT  "),
        bullet_block(" — 给带推理的示例，适合复杂推理 ⭐⭐⭐⭐⭐", "Few-shot CoT  "),
    ], "对比")
    add([divider_block()], "分隔1")

    # === 2. Chain-of-Thought ===
    add([heading_block("2. Chain-of-Thought（思维链）", 2)], "2")
    add([heading_block("2.1 什么是思维链？", 3)], "2.1")
    add([text_block("Chain-of-Thought（CoT）是让大模型在给出答案前，先输出中间推理步骤的技巧。类比 Java Debug 时加 print() 打印中间变量。")], "2.1a")

    add([heading_block("2.2 为什么 CoT 有效？", 3)], "2.2")
    add([
        bullet_block(" — 模型输出越多 token，内部「思考时间」越长", "扩展计算步数  "),
        bullet_block(" — 每一步结果成为下一步输入，减少跳步错误", "显式中间状态  "),
        bullet_block(" — 推理过程可见，错误可定位到具体步骤", "人类可审计  "),
    ], "2.2列表")

    # === 3. Zero-shot CoT ===
    add([heading_block("3. Zero-shot CoT", 2)], "3")
    add([text_block("不修改问题，只在末尾加一句「让我们一步一步思考」—— 推理准确率从 18% 提升到 79%（PaLM 540B）。")], "3a")
    add([heading_block("示例对比", 3)], "3b")
    add(code_block_lines("""❌ 直接问：进水管3h注满，排水管5h排空。同时开多久？
回答：2小时 ❌（跳步错误）

✅ 加CoT：1. 进水管每小时注水1/3池
2. 排水管每小时排水1/5池
3. 净注水=1/3-1/5=2/15
4. 时间=1÷2/15=7.5小时 ✅""", "示例"), "代码2")
    add([divider_block()], "分隔2")

    # === 4. Few-shot CoT ===
    add([heading_block("4. Few-shot CoT", 2)], "4")
    add([text_block("比 Zero-shot CoT 更进一步：给 2-3 个带推理过程的示例，教模型「怎么推理」。")], "4a")
    add(code_block_lines("""示例：正方形周长20厘米，面积？
推理：周长=4×边长→边长=5→面积=25
答案：25

现在请回答：鸡兔同笼，头35个，脚94只？""", "模板"), "代码3")
    add([divider_block()], "分隔3")

    # === 5. Self-Consistency ===
    add([heading_block("5. Self-Consistency（自一致性）", 2)], "5")
    add([text_block("同一个问题，用 CoT 问多次（3-5 次），取出现次数最多的答案。")], "5a")
    add([
        bullet_block(" ✅ 跑 3-5 次取多数，答案唯一", "数学题："),
        bullet_block(" ✅ 跑 3 次取多数，选项有限", "选择题："),
        bullet_block(" ❌ 答案没有「正确」标准", "创作类："),
    ], "5场景")
    add([divider_block()], "分隔4")

    # === 6. Tree-of-Thought ===
    add([heading_block("6. Tree-of-Thought（思维树）", 2)], "6")
    add([text_block("ToT 不沿着一条路径推理，而是同时探索多条路径，评估并剪枝。类比：CoT 是单线程，ToT 是多线程并行搜索。")], "6a")
    add([heading_block("ToT 三步法", 3)], "6b")
    add([
        bullet_block(" — 生成 3 条不同路径", "①探索 "),
        bullet_block(" — 判断每条路径可行性", "②评估 "),
        bullet_block(" — 选最佳路径深入", "③选择 "),
    ], "6步骤")
    add([divider_block()], "分隔5")

    # === 7. 实操 ===
    add([heading_block("7. 动手实操", 2)], "7")
    add([text_block("代码文件在 ~/ai-learning/week1/code/day3/ 目录下")], "7a")

    add([heading_block("实验1：Zero-shot vs CoT 对比", 3)], "7.1")
    add(code_block_lines("""实验结果（长方形面积问题）：
=== Zero-shot（直接问）===
设宽=x，长=2x，周长=2(2x+x)=36
6x=36 → x=6 → 长=12
面积=12×6=72 平方厘米

=== Zero-shot CoT（分步思考）===
第1步：设宽=x，长=2x
第2步：2(2x+x)=36 → x=6
第3步：长=12 → 面积=72
答案：72平方厘米 ✅""", "Python"), "代码4")

    add([heading_block("实验2：Few-shot CoT + Self-Consistency", 3)], "7.2")
    add(code_block_lines("""鸡兔同笼问题 — Few-shot CoT 结果：
1. 总头数35 → 鸡+兔=35
2. 假设全是鸡 → 70只脚
3. 实际94只 → 多24只脚
4. 每只兔多2只脚 → 兔=24÷2=12
5. 鸡=35-12=23
答案：鸡23只，兔12只 ✅

Self-Consistency 跑3次：
第1次：鸡23 兔12 ✅
第2次：鸡23 兔12 ✅
第3次：鸡23 兔12 ✅
三次完全一致！""", "输出"), "代码5")

    add([heading_block("实验3：Tree-of-Thought — 24点", 3)], "7.3")
    add(code_block_lines("""用 3,3,8,8 算出24
①探索 → 列出4种思路
②评估 → 排除不可行的，聚焦分数解法
③选择 → 8÷(3-8÷3)=8÷(1/3)=24 ✅""", "输出"), "代码6")

    add([divider_block()], "分隔6")

    # === 8. 小结 ===
    add([heading_block("8. 今日小结", 2)], "8")
    add([
        bullet_block(" — 加「一步一步思考」让模型分步推理", "Zero-shot CoT  "),
        bullet_block(" — 给 2-3 个带推理的示例", "Few-shot CoT  "),
        bullet_block(" — 跑 3-5 次取多数答案", "Self-Consistency  "),
        bullet_block(" — 多路径探索 + 评估 + 剪枝", "Tree-of-Thought  "),
    ], "8列表")

    add([divider_block()], "分隔7")
    add([heading_block("明天预告：第4天 — API 基础对接 🔌", 2)], "预告")
    add([text_block("注册 API Key → curl 调通第一次调用 → 理解请求/响应体结构 → Spring AI 环境预热")], "预告文")

    print("  ✅ 所有内容添加完成！")


def main(title="第3天：高级提示词策略"):
    print(f"🚀 创建飞书文档: {title}")
    token = get_token()
    doc_id = create_doc(token, title)
    url = f"https://bytedance.feishu.cn/docx/{doc_id}"
    print(f"  ✅ 文档创建成功！")
    print(f"  📎 {url}")

    generate_course_doc(doc_id, token)
    return doc_id, url


if __name__ == "__main__":
    main()
