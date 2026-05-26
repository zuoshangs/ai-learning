"""
resume_extractor.py — 从简历文本中提取结构化信息

使用 Function Calling（最可靠方式）+ Pydantic（类型验证）

用法：
  python3 resume_extractor.py
"""

import json
import os
import sys
import requests
from typing import List, Optional
from pydantic import BaseModel, Field


# ─── 读取 API Key ─────────────────────────────
def get_api_key():
    key = os.environ.get("DEEPSEEK_API_KEY", "")
    if not key:
        try:
            with open(os.path.expanduser("~/.hermes/auth.json")) as f:
                auth = json.load(f)
            pool = auth.get("credential_pool", {}).get("deepseek", [])
            if pool:
                key = pool[0].get("access_token", "")
        except Exception:
            pass
    return key


API_KEY = get_api_key()
API_URL = "https://api.deepseek.com/v1/chat/completions"


# ─── Pydantic 数据模型 ────────────────────────

class Education(BaseModel):
    """教育背景"""
    degree: str = Field(description="学历，如本科、硕士研究生")
    school: str = Field(description="毕业院校")
    major: str = Field(description="专业")


class WorkExperience(BaseModel):
    """工作经历"""
    company: str = Field(description="公司名称")
    period: str = Field(description="工作时间段，如 2020.07 - 2023.06")
    position: str = Field(description="职位")
    responsibilities: List[str] = Field(default=[], description="工作职责列表")
    technologies: List[str] = Field(default=[], description="使用的技术栈")


class Resume(BaseModel):
    """简历"""
    name: str = Field(description="姓名")
    phone: Optional[str] = Field(default=None, description="电话号码")
    email: Optional[str] = Field(default=None, description="邮箱地址")
    education: Optional[Education] = Field(default=None, description="教育背景")
    work_experience: List[WorkExperience] = Field(default=[], description="工作经历")
    skills: List[str] = Field(default=[], description="技能列表")
    certifications: List[str] = Field(default=[], description="证书/资质")
    summary: Optional[str] = Field(default=None, description="个人总结")


# ─── 简历提取器 ───────────────────────────────

class ResumeExtractor:
    """简历信息提取器（Function Calling + Pydantic 验证）"""

    SCHEMA = {
        "name": "extract_resume",
        "description": "从简历文本中提取结构化信息",
        "parameters": {
            "type": "object",
            "properties": {
                "name": {"type": "string", "description": "姓名"},
                "phone": {"type": "string", "description": "电话号码"},
                "email": {"type": "string", "description": "邮箱地址"},
                "education": {
                    "type": "object",
                    "description": "教育背景",
                    "properties": {
                        "degree": {"type": "string", "description": "学历"},
                        "school": {"type": "string", "description": "毕业院校"},
                        "major": {"type": "string", "description": "专业"},
                    },
                },
                "work_experience": {
                    "type": "array",
                    "description": "工作经历列表",
                    "items": {
                        "type": "object",
                        "properties": {
                            "company": {"type": "string", "description": "公司名称"},
                            "period": {"type": "string", "description": "工作时间段"},
                            "position": {"type": "string", "description": "职位"},
                            "responsibilities": {
                                "type": "array", "items": {"type": "string"},
                                "description": "工作职责",
                            },
                            "technologies": {
                                "type": "array", "items": {"type": "string"},
                                "description": "使用的技术",
                            },
                        },
                    },
                },
                "skills": {
                    "type": "array", "items": {"type": "string"},
                    "description": "技能列表",
                },
                "certifications": {
                    "type": "array", "items": {"type": "string"},
                    "description": "证书列表",
                },
                "summary": {"type": "string", "description": "个人总结"},
            },
            "required": ["name", "work_experience", "skills"],
        },
    }

    def __init__(self, api_key: str):
        self.api_key = api_key

    def extract(self, text: str) -> Optional[Resume]:
        """提取简历信息 → Pydantic 验证"""
        raw = self._call_llm(text)
        try:
            return Resume(**raw)
        except Exception as e:
            print(f"  ⚠️  Pydantic 验证失败: {e}")
            # 尝试返回部分数据
            try:
                return Resume.model_validate(raw, strict=False)
            except Exception:
                return None

    def extract_safe(self, text: str, max_retries: int = 2) -> Optional[Resume]:
        """带重试的安全提取"""
        for attempt in range(max_retries):
            print(f"  📝 尝试第 {attempt + 1} 次...")
            raw = self._call_llm(text)
            try:
                return Resume(**raw)
            except Exception as e:
                if attempt == max_retries - 1:
                    print(f"  ❌ 重试 {max_retries} 次仍失败")
                    return None
                # 让模型修正
                text = f"""之前提取的 JSON 验证失败。错误信息：
{e}

请修正后重新提取。原文如下：
{text}"""
        return None

    def _call_llm(self, text: str) -> dict:
        resp = requests.post(
            API_URL,
            headers={"Authorization": f"Bearer {self.api_key}"},
            json={
                "model": "deepseek-chat",
                "messages": [
                    {
                        "role": "system",
                        "content": "你是一个简历信息提取助手，从用户提供的简历文本中提取结构化信息。",
                    },
                    {"role": "user", "content": text},
                ],
                "tools": [{"type": "function", "function": self.SCHEMA}],
                "tool_choice": {
                    "type": "function",
                    "function": {"name": "extract_resume"},
                },
                "temperature": 0,
            },
            timeout=30,
        )
        data = resp.json()
        if "choices" not in data:
            raise Exception(f"API 错误: {data.get('error', {}).get('message', str(data))[:100]}")
        msg = data["choices"][0]["message"]
        return json.loads(msg["tool_calls"][0]["function"]["arguments"])


# ─── 主程序 ──────────────────────────────────
def main():
    if not API_KEY:
        print("⚠️  未找到 API Key")
        return

    extractor = ResumeExtractor(API_KEY)

    resume_text = """
个人简历

姓名：王小明
电话：138-0000-1234
邮箱：wangxm@example.com
学历：硕士研究生
毕业院校：清华大学
专业：计算机科学与技术

工作经历：
2020.07 - 2023.06  阿里巴巴  高级后端工程师
  - 负责电商平台的订单系统开发
  - 使用 Java、Spring Cloud、Redis
  - 系统日处理订单量 100 万+

2023.07 - 至今     字节跳动  技术专家
  - 主导微服务架构升级
  - 团队规模 10 人
  - 引入 Kubernetes 和 Docker

技能：Java、Python、Kubernetes、Docker、Redis、MySQL、Spring Cloud、微服务架构
证书：PMP、AWS 认证解决方案架构师
    """.strip()

    print("=" * 55)
    print("  📄 简历信息提取器")
    print("  Function Calling + Pydantic 双重保障")
    print("=" * 55)

    print("\n📄 正在提取简历信息...")
    resume = extractor.extract(resume_text)

    if resume:
        print(f"\n✅ 提取成功！\n")

        print(f"  👤 姓名: {resume.name}")
        print(f"  📞 电话: {resume.phone}")
        print(f"  📧 邮箱: {resume.email}")

        if resume.education:
            print(f"\n  🎓 教育背景:")
            print(f"     学历: {resume.education.degree}")
            print(f"     学校: {resume.education.school}")
            print(f"     专业: {resume.education.major}")

        if resume.work_experience:
            print(f"\n  💼 工作经历 ({len(resume.work_experience)} 段):")
            for exp in resume.work_experience:
                print(f"     [{exp.period}] {exp.company} - {exp.position}")
                if exp.responsibilities:
                    for r in exp.responsibilities:
                        print(f"       · {r}")
                if exp.technologies:
                    print(f"       🛠️  {', '.join(exp.technologies)}")

        if resume.skills:
            print(f"\n  🔧 技能 ({len(resume.skills)} 项):")
            print(f"     {', '.join(resume.skills)}")

        if resume.certifications:
            print(f"\n  📜 证书: {', '.join(resume.certifications)}")

        # 导出完整 JSON
        print(f"\n{'='*55}")
        print("📦 完整 JSON 输出:")
        print(json.dumps(resume.model_dump(), ensure_ascii=False, indent=2))

    else:
        print("\n❌ 提取失败")


if __name__ == "__main__":
    main()
