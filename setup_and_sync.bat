@echo off
echo 正在设置项目环境...

REM 检查Java环境
where java >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo Java未找到，尝试设置JAVA_HOME...
    
    REM 尝试寻找 Java 安装位置，首先检查 Android Studio 可能安装的JDK
    set "POSSIBLE_JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
    if exist "%POSSIBLE_JAVA_HOME%" (
        set "JAVA_HOME=%POSSIBLE_JAVA_HOME%"
        echo 已设置JAVA_HOME为 %JAVA_HOME%
        goto :java_found
    )
    
    REM 如果找不到，则检查其他可能的位置
    for %%d in (
        "C:\Program Files\Java\jdk*"
        "C:\Program Files\Java\jre*"
        "C:\Program Files (x86)\Java\jdk*"
        "C:\Program Files (x86)\Java\jre*"
    ) do (
        if exist "%%~d" (
            set "JAVA_HOME=%%~d"
            echo 已设置JAVA_HOME为 %JAVA_HOME%
            goto :java_found
        )
    )
    
    echo 未找到Java，请安装JDK或手动设置JAVA_HOME环境变量。
    exit /b 1
)

:java_found
REM 添加JAVA_HOME/bin到PATH
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo Java版本:
java -version

REM 清理Gradle缓存
echo 正在清理Gradle缓存...
call gradlew --stop
rmdir /s /q %USERPROFILE%\.gradle\caches\modules-2\files-2.1\gradle 2>nul
rmdir /s /q %USERPROFILE%\.gradle\caches\transforms-3 2>nul
mkdir %USERPROFILE%\.gradle 2>nul
cd /d %~dp0

REM 确保使用最新的配置
echo 添加--no-daemon --refresh-dependencies参数，尝试避免依赖解析问题...

REM 使用离线模式尝试同步（如果有本地缓存的话）
echo 尝试执行Gradle同步...
call gradlew --no-daemon --refresh-dependencies cleanBuildCache
call gradlew --no-daemon --refresh-dependencies help

echo.
echo 环境已准备就绪，请在Android Studio中重新同步项目。
echo 如果仍然遇到问题，请尝试编辑gradle.properties文件，调整代理设置或禁用源代码下载。
echo. 