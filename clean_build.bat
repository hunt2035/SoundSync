@echo off
echo 正在停止Gradle守护进程...
call gradlew --stop

echo 正在关闭Android Studio...
taskkill /F /IM studio64.exe 2>nul
taskkill /F /IM studio.exe 2>nul

echo 正在等待进程完全关闭...
timeout /t 5 /nobreak

echo 正在清理构建目录...
if exist "app\build" (
    echo 正在删除app\build目录...
    rmdir /s /q "app\build"
)

if exist "build" (
    echo 正在删除build目录...
    rmdir /s /q "build"
)

echo 正在清理Gradle缓存...
if exist "%USERPROFILE%\.gradle\caches" (
    echo 正在删除Gradle缓存...
    rmdir /s /q "%USERPROFILE%\.gradle\caches"
)

echo 清理完成！
echo 请重新启动Android Studio并同步项目。 