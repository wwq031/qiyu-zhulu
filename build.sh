#!/bin/bash
# 七域逐鹿 Java版 · 构建脚本
# 用法: bash build.sh [--commit]

set -e

echo "═══ 七域逐鹿 · Java版 · 构建 ═══"
echo ""

# Maven 构建
echo "[1/2] Maven package..."
mvn package -DskipTests -q
echo "  ✅ 构建成功"

# 复制到 release
echo "[2/2] 复制 JAR → release/"
cp target/qiyuzhulu-2.2.0.jar release/
echo "  ✅ release/qiyuzhulu-2.2.0.jar"

echo ""
echo "════════════════════════════════════"
echo "  构建完成! java -jar release/qiyuzhulu-2.2.0.jar"
echo "════════════════════════════════════"

# 可选 git commit
if [ "$1" = "--commit" ]; then
    echo ""
    echo "[git] 提交 release JAR..."
    git add release/
    git commit -m "release: update qiyuzhulu-2.2.0.jar

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
    echo "  ✅ 已提交"
fi
