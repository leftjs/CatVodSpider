#!/usr/bin/env bash
# CatVodSpider 构建与发布流程脚本
# 用法: bash scripts/build_and_push.sh [commit_message]

set -e

# ========== 环境配置 ==========
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/root/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR/.."
COMMIT_MSG="${1:-更新 custom_spider.jar MD5}"

cd "$PROJECT_DIR"

echo "=== Step 1: 构建 APK ==="
./gradlew assembleRelease

echo "=== Step 2: 生成 custom_spider.jar ==="
bash jar/genJar.sh

# 获取新 MD5
NEW_MD5=$(cat jar/custom_spider.jar.md5)
echo "新 MD5: $NEW_MD5"

echo "=== Step 3: 更新 adult.json MD5 ==="
# 替换 adult.json 中的 spider MD5（简单粗暴：替换整个 md5;xxx 部分）
sed -i "s|md5;[a-f0-9]\{32\}|md5;$NEW_MD5|g" json/adult.json
echo "adult.json MD5 已更新"

echo "=== Step 4: Git 提交 ==="
git add jar/custom_spider.jar jar/custom_spider.jar.md5 json/adult.json
git commit -m "$COMMIT_MSG"

echo "=== Step 5: Git Push ==="
git push origin main

echo "=== 完成! ==="
echo "MD5: $NEW_MD5"
