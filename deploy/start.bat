@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
echo ============================================
echo   家庭房源 家庭房屋租赁管理系统 - 一键启动
echo ============================================

java -version >nul 2>&1
if errorlevel 1 (
  echo [错误] 未检测到 Java，请先安装 JDK 21（https://adoptium.net）
  pause
  exit /b 1
)

rem 部署机默认用 prod profile（不加载开发用 application-local.yml）；可预先设置环境变量覆盖
if not defined SPRING_PROFILES_ACTIVE set SPRING_PROFILES_ACTIVE=prod

rem 一切路径基于脚本自身所在目录，避免从别处调用时找错
set "HERE=%~dp0"
cd /d "%HERE%"

rem 查找顺序：脚本同目录 jar（部署机）→ 同目录 target\ → 父目录 target\（开发机）
set "JAR="
for /F "delims=" %%j in ('dir /b rent-system-java-*.jar 2^>nul') do set "JAR=%HERE%%%j"
if not defined JAR (
  for /F "delims=" %%j in ('dir /b target\rent-system-java-*.jar 2^>nul') do set "JAR=%HERE%target\%%j"
)
if not defined JAR (
  for /F "delims=" %%j in ('dir /b ..\target\rent-system-java-*.jar 2^>nul') do set "JAR=%HERE%..\target\%%j"
)
if not defined JAR (
  echo [错误] 未找到 jar 包。请把 rent-system-java-*.jar 放在本脚本同目录，
  echo        或在开发机执行：mvnw -Pdeploy -DskipTests clean package
  pause
  exit /b 1
)

echo 使用 jar：!JAR!
echo 激活 profile：!SPRING_PROFILES_ACTIVE!
echo 启动后访问 http://localhost:8080 （局域网其他设备用本机 IP 访问）
echo.
java -jar "!JAR!"
pause
