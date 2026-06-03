@echo off
title 构建 七域逐鹿 · Java版

echo.
echo  ╔══════════════════════════════════════════════════╗
echo  ║       七域逐鹿 · Java版 · 构建脚本              ║
echo  ╚══════════════════════════════════════════════════╝
echo.

echo  [1/2] Maven 构建中...
call mvn package -DskipTests -q
if %ERRORLEVEL% NEQ 0 (
    echo  [FAIL] Maven 构建失败!
    pause
    exit /b %ERRORLEVEL%
)
echo  [OK] Maven 构建成功

echo.
echo  [2/2] 复制 JAR → release\
xcopy /Y target\qiyuzhulu-2.2.0.jar release\ > nul
echo  [OK] release\qiyuzhulu-2.2.0.jar

echo.
echo  ══════════════════════════════════════════════════
echo   构建完成! 运行 start.bat 启动游戏
echo  ══════════════════════════════════════════════════

if "%1"=="commit" (
    echo.
    echo  [git] 提交 release JAR...
    git add release/ && git commit -m "release: update qiyuzhulu-2.2.0.jar"
)

pause
