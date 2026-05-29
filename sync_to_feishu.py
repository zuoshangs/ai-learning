"""
完整同步课程到飞书云盘 — 按 GitHub 目录结构一对一映射

使用方式：
  python3 sync_to_feishu.py

原理：
  1. 读取本地的 ~/ai-learning/ 文件结构
  2. 在飞书云盘 AI学习/ 下创建同名文件夹
  3. 上传每个阶段文件夹中的教程 .md 文件
  4. 上传根目录下的文档（学习计划、进阶计划等）
  5. 跳过 code/ 目录（代码通过 NAS 同步）
"""

import os
import sys
import json
import time
import requests

BASE = 'https://open.feishu.cn/open-apis'
APP_ID = 'cli_a96cf906c738dcc7'
APP_SECRET = 'EIy92uxSScnqYyfrBHkhMbF4qSw6SVjO'

STATE_FILE = os.path.expanduser('~/ai-learning/feishu_state.json')
LOCAL_ROOT = os.path.expanduser('~/ai-learning')
DRIVE_ROOT_TOKEN = 'VXzPfCAASlZUopdwIXWcAd2QnHg'
DRIVE_ROOT_NAME = 'ai-learning'

# ========================================
# 文件清单：需要上传哪些文件
# ========================================
# 按阶段分，key = 阶段文件夹名, value = 要上传的 .md 文件列表（本地路径相对于 LOCAL_ROOT）
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
    ],
    "阶段9-职业冲刺/前沿拓展": [
        "阶段9-职业冲刺/code/day45/mcp/MCP协议详解.md",
        "阶段9-职业冲刺/code/day45/ollama-local/Ollama本地部署+SpringAI集成.md",
        "阶段9-职业冲刺/code/day45/summary/Java工程师AI转型实战总结.md",
    ],
    "阶段9-职业冲刺/面试与简历": [
        "阶段9-职业冲刺/code/day43/java-ai-interview/AI工程化面试题集.md",
        "阶段9-职业冲刺/code/day43/spring-ai-vs-langchain4j/Spring-AI-vs-LangChain4j-选型对比报告.md",
        "阶段9-职业冲刺/code/day44/resume/AI工程师简历.md",
        "阶段9-职业冲刺/code/day44/resume/AI-Engineer-Resume.md",
    ],
    "阶段9-职业冲刺/进阶计划": [
        "阶段9-职业冲刺/code/day45-进阶/评估体系/data/eval_dataset.json",
        "阶段9-职业冲刺/code/day45-进阶/评估体系/scripts/build_eval_dataset.py",
        "阶段9-职业冲刺/code/day45-进阶/评估体系/scripts/rag_evaluation.py",
        "阶段9-职业冲刺/code/day45-进阶/评估体系/report/evaluation_report.md",
        "阶段9-职业冲刺/笔记/Day45-进阶-Day1-RAG评估体系.md",
        "AI工程化进阶计划_15天.md",
    ],
}

# ========================================
# 根目录文件
# ========================================
ROOT_FILES = [
    "Java-AI工程化转型_综合学习计划.md",
    "AI工程化进阶计划_15天.md",
    "阶段9-职业冲刺/笔记/大模型黑话指南-给非算法开发者的速通版.md",
]


def api(method, url, **kw):
    h = kw.pop('headers', {})
    t = kw.pop('token', None)
    if t:
        h['Authorization'] = f'Bearer {t}'
    kw['headers'] = h
    for a in range(3):
        try:
            r = (requests.post if method == 'POST' else requests.get)(
                url, timeout=30, **kw)
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
    """确保文件夹存在，返回 folder_token。"""
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
    print(f"  ❌ 创建文件夹失败: {r.get('msg', r)}")
    return None


def upload_file(token, parent_token, local_path, filename):
    """上传单个文件到飞书云盘。"""
    if not os.path.exists(local_path):
        print(f"  ⚠️ 文件不存在: {local_path}")
        return None

    with open(local_path, 'rb') as f:
        data = f.read()

    r = api('POST', f'{BASE}/drive/v1/files/upload_all', token=token,
            data={
                'file_name': filename,
                'parent_type': 'explorer',
                'parent_node': parent_token,
                'size': str(len(data)),
            },
            files={'file': (filename, data, 'application/octet-stream')})

    if r.get('code') == 0:
        return r['data']['file_token']
    # 如果文件已存在（code 121002），尝试覆盖更新
    if r.get('code') == 121002:
        r2 = api('POST', f'{BASE}/drive/v1/files/upload_all', token=token,
                 data={
                     'file_name': filename,
                     'parent_type': 'explorer',
                     'parent_node': parent_token,
                     'size': str(len(data)),
                 },
                 files={'file': (filename, data, 'application/octet-stream')})
        if r2.get('code') == 0:
            return r2['data']['file_token']
        print(f"  ⚠️ 覆盖上传失败 {filename}: {r2.get('msg', r2)}")
        return None

    print(f"  ⚠️ 上传失败 {filename}: {r.get('msg', r)}")
    return None


def main():
    token = get_token()
    print(f"🔑 已获取飞书 token")
    print(f"📁 目标: {DRIVE_ROOT_NAME}/\n")

    # 上传根目录文件
    print("📄 根目录文件:")
    stage_folder = get_or_create_folder(token, DRIVE_ROOT_TOKEN, DRIVE_ROOT_NAME)
    for rel_path in ROOT_FILES:
        local_path = os.path.join(LOCAL_ROOT, rel_path)
        filename = os.path.basename(rel_path)
        # 放到 AI学习 根目录下
        ft = upload_file(token, DRIVE_ROOT_TOKEN, local_path, filename)
        if ft:
            print(f"  ✅ {filename}")

    print()

    # 上传各个阶段
    for stage_name, files in STAGES.items():
        print(f"📁 阶段: {stage_name}")
        
        # 创建或获取阶段文件夹（在 AI学习 下）
        if '/' in stage_name:
            # 嵌套路径：先创建父文件夹，再创建子文件夹
            parts = stage_name.split('/')
            parent_token = DRIVE_ROOT_TOKEN
            for part in parts:
                parent_token = get_or_create_folder(token, parent_token, part)
                if not parent_token:
                    break
            folder_token = parent_token
        else:
            folder_token = get_or_create_folder(token, DRIVE_ROOT_TOKEN, stage_name)
        
        if not folder_token:
            print(f"  跳过 {stage_name}")
            continue

        # 上传文件
        for rel_path in files:
            local_path = os.path.join(LOCAL_ROOT, rel_path)
            filename = os.path.basename(rel_path)
            ft = upload_file(token, folder_token, local_path, filename)
            if ft:
                print(f"  ✅ {filename}")
            else:
                print(f"  ❌ {filename} 上传失败")
        print()

    print("=" * 50)
    print("✅ 同步完成！")


if __name__ == '__main__':
    main()
