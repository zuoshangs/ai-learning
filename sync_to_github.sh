#!/bin/bash
# 自动同步 AI 课程到 GitHub
# 用于每日课程完成后自动 commit + push
# 用法: ./sync_to_github.sh "Day 16: 新内容"

set -e

cd ~/ai-learning || exit 1

# 参数: 提交信息（可选）
COMMIT_MSG="${1:-每日课程更新}"

# 确保 git 凭据可用
if [ ! -f ~/.git-credentials ] || ! grep -q "github.com" ~/.git-credentials 2>/dev/null; then
    echo "⚠️  未找到 GitHub 凭据，跳过 git 同步"
    exit 0
fi

# 检查是否有变更
if [ -z "$(git status --porcelain)" ]; then
    echo "ℹ️  没有新的变更，跳过提交"
    exit 0
fi

# 暂存所有新文件（排除 .gitignore 中已配置的）
git add -A

# 提交
git commit -m "$COMMIT_MSG" --no-verify 2>/dev/null || echo "ℹ️  没有变更需要提交"

# 推送
git push origin main 2>&1 || {
    echo "⚠️  Push 失败，尝试 pull --rebase 后重试"
    git pull --rebase origin main
    git push origin main
}

echo "✅ GitHub 同步完成"
