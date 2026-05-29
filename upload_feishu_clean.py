"""全新上传课程到飞书云盘 — 创建新根目录 ai-learning/"""

import os, sys, json, time, requests

BASE = 'https://open.feishu.cn/open-apis'
APP_ID = 'cli_a96cf906c738dcc7'
APP_SECRET = 'EIy92uxSScnqYyfrBHkhMbF4qSw6SVjO'
STATE_FILE = os.path.expanduser('~/ai-learning/feishu_state_new.json')
LOCAL_ROOT = os.path.expanduser('~/ai-learning')


def api(method, url, **kw):
    h = kw.pop('headers', {})
    t = kw.pop('token', None)
    if t:
        h['Authorization'] = f'Bearer {t}'
    kw['headers'] = h
    for a in range(3):
        try:
            r = (requests.post if method == 'POST' else requests.get)(url, timeout=30, **kw)
            return r.json()
        except Exception as e:
            if a < 2:
                time.sleep(2)
            else:
                raise


def get_token():
    r = api('POST', f'{BASE}/auth/v3/tenant_access_token/internal',
            json={'app_id': APP_ID, 'app_secret': APP_SECRET})
    return r['tenant_access_token']


def load_state():
    if os.path.exists(STATE_FILE):
        with open(STATE_FILE) as f:
            return json.load(f)
    return {}


def save_state(state):
    with open(STATE_FILE, 'w') as f:
        json.dump(state, f, indent=2, ensure_ascii=False)


def get_or_create_folder(token, parent_token, name):
    """确保文件夹存在，返回其 token。"""
    state = load_state()
    key = f"folder_{name}"
    if key in state:
        return state[key]

    r = api('POST', f'{BASE}/drive/v1/files/create_folder', token=token,
            json={'name': name, 'folder_token': parent_token})
    if r.get('code') == 0:
        ft = r['data']['token']
        state[key] = ft
        save_state(state)
        return ft
    # Feishu 返回已有文件夹的 token（同名冲突）
    if 'data' in r and 'token' in r.get('data', {}):
        ft = r['data']['token']
        state[key] = ft
        save_state(state)
        return ft
    print(f"  ❌ 创建文件夹失败 {name}: {r.get('msg', r)}")
    return None


def upload_file(token, parent_token, local_path, filename):
    """上传单个文件。"""
    if not os.path.exists(local_path):
        print(f"  ⚠️ 文件不存在: {local_path}")
        return False
    with open(local_path, 'rb') as f:
        data = f.read()

    for attempt in range(3):
        r = api('POST', f'{BASE}/drive/v1/files/upload_all', token=token,
                data={
                    'file_name': filename,
                    'parent_type': 'explorer',
                    'parent_node': parent_token,
                    'size': str(len(data)),
                },
                files={'file': (filename, data, 'application/octet-stream')})
        if r.get('code') == 0:
            return True
        if r.get('code') == 121002 and attempt < 2:
            # 文件已存在，重试两次
            time.sleep(1)
            continue
        print(f"  ⚠️ 上传失败 {filename}: {r.get('msg', '')}")
        return False
    return False


# ====== 文件清单 ======
STAGES = {
    "01-基础篇": [
        "01-基础篇/day1-大模型机制入门.md",
        "01-基础篇/day2-提示词工程基础.md",
        "01-基础篇/day3-高级提示词策略.md",
        "01-基础篇/day4-API基础对接.md",
        "01-基础篇/day5-核心参数调优.md",
        "01-基础篇/day6-角色设定与模板.md",
        "01-基础篇/day7-阶段复盘.md",
    ],
    "02-进阶能力": [
        "02-进阶能力/day8-RAG入门与实战.md",
        "02-进阶能力/day9-工具调用.md",
        "02-进阶能力/day10-结构化输出.md",
        "02-进阶能力/day11-多轮对话与状态管理.md",
        "02-进阶能力/day12-项目实战-完整AI客服系统.md",
    ],
    "03-应用实战": [
        "03-应用实战/day13-AI搜索增强助手.md",
        "03-应用实战/day14-多Agent协作系统.md",
        "03-应用实战/day15-个人知识库问答系统.md",
    ],
    "阶段4-Java-AI应用开发": [
        "阶段4-Java-AI应用开发/day16-Spring-AI环境搭建.md",
        "阶段4-Java-AI应用开发/day17-提示词模板与结构化输出.md",
        "阶段4-Java-AI应用开发/day18-多轮对话与SSE流式输出.md",
        "阶段4-Java-AI应用开发/day19-工具调用FunctionCalling.md",
        "阶段4-Java-AI应用开发/day20-Spring-AI-RAG实战.md",
        "阶段4-Java-AI应用开发/day21-智能客服系统.md",
    ],
    "阶段5-RAG工程化": [
        "阶段5-RAG工程化/day22-文档加载与智能切分.md",
        "阶段5-RAG工程化/day23-向量化入库与相似度检索.md",
        "阶段5-RAG工程化/day24-混合检索与重排.md",
        "阶段5-RAG工程化/day25-高级RAG技术.md",
        "阶段5-RAG工程化/day26-企业知识库V2.md",
    ],
    "阶段6-Agent与工作流": [
        "阶段6-Agent与工作流/day27-Agent原理与ReAct.md",
        "阶段6-Agent与工作流/day28-多工具编排与Agent记忆.md",
        "阶段6-Agent与工作流/day29-Dify工作流平台.md",
        "阶段6-Agent与工作流/day30-多Agent协作系统.md",
        "阶段6-Agent与工作流/day31-DAG执行引擎.md",
        "阶段6-Agent与工作流/day32-安全防护与Agent评估.md",
    ],
    "阶段7-LLMOps": [
        "阶段7-LLMOps/day33-LLM网关与限流.md",
        "阶段7-LLMOps/day34-语义缓存.md",
        "阶段7-LLMOps/day35-可观测性与指标监控.md",
        "阶段7-LLMOps/day36-性能调优与成本分析.md",
        "阶段7-LLMOps/day37-生产加固.md",
    ],
    "阶段8-综合项目": [
        "阶段8-综合项目/day38-多轮对话核心.md",
        "阶段8-综合项目/day39-RAG知识库.md",
        "阶段8-综合项目/day40-工单系统.md",
        "阶段8-综合项目/day41-管理仪表盘+LLMOps集成.md",
        "阶段8-综合项目/day42-项目总结.md",
    ],
    "阶段9-职业冲刺": [
        "阶段9-职业冲刺/笔记/day43-面试题+选型报告.md",
        "阶段9-职业冲刺/笔记/day44-简历包装.md",
        "阶段9-职业冲刺/笔记/day45-前沿拓展+结业.md",
        "阶段9-职业冲刺/code/day45/mcp/MCP协议详解.md",
        "阶段9-职业冲刺/code/day45/ollama-local/Ollama本地部署+SpringAI集成.md",
        "阶段9-职业冲刺/code/day45/summary/Java工程师AI转型实战总结.md",
        "阶段9-职业冲刺/code/day43/java-ai-interview/AI工程化面试题集.md",
        "阶段9-职业冲刺/code/day43/spring-ai-vs-langchain4j/Spring-AI-vs-LangChain4j-选型对比报告.md",
        "阶段9-职业冲刺/code/day44/resume/AI工程师简历.md",
        "阶段9-职业冲刺/code/day44/resume/AI-Engineer-Resume.md",
        "阶段9-职业冲刺/code/day45-进阶/评估体系/data/eval_dataset.json",
        "阶段9-职业冲刺/code/day45-进阶/评估体系/scripts/build_eval_dataset.py",
        "阶段9-职业冲刺/code/day45-进阶/评估体系/scripts/rag_evaluation.py",
        "阶段9-职业冲刺/code/day45-进阶/评估体系/report/evaluation_report.md",
        "阶段9-职业冲刺/笔记/Day45-进阶-Day1-RAG评估体系.md",
        "阶段9-职业冲刺/笔记/大模型黑话指南-给非算法开发者的速通版.md",
    ],
}

ROOT_FILES = [
    "Java-AI工程化转型_综合学习计划.md",
    "AI工程化进阶计划_15天.md",
]


def main():
    # 全新 state
    save_state({})

    token = get_token()
    print(f"🔑 已获取飞书 token\n")

    # 创建根目录 ai-learning（传空字符串 = 飞书云盘根目录）
    r = api('POST', f'{BASE}/drive/v1/files/create_folder', token=token,
            json={'name': 'ai-learning', 'folder_token': ''})
    if r.get('code') != 0:
        print(f"❌ 创建根目录失败: {r.get('msg', r)}")
        return
    root_token = r['data']['token']
    print(f"✅ 创建根目录 ai-learning/ (token: {root_token})\n")

    # 上传根文件
    print("📄 根目录:")
    for rel_path in ROOT_FILES:
        local_path = os.path.join(LOCAL_ROOT, rel_path)
        name = os.path.basename(rel_path)
        if upload_file(token, root_token, local_path, name):
            print(f"  ✅ {name}")
    print()

    # 上传各阶段
    total = 0
    for stage_name, files in STAGES.items():
        print(f"📁 {stage_name}/")
        ft = get_or_create_folder(token, root_token, stage_name)
        if not ft:
            continue
        for rel_path in files:
            local_path = os.path.join(LOCAL_ROOT, rel_path)
            name = os.path.basename(rel_path)
            if upload_file(token, ft, local_path, name):
                total += 1
                print(f"  ✅ {name}")
    print(f"\n✅ 上传完成！共 {total} 个文件")

    # 保存根目录 token 到 state
    state = load_state()
    state["root_token"] = root_token
    save_state(state)
    print(f"📝 根目录 token 已保存，以后增量上传用此 token")


if __name__ == '__main__':
    main()
