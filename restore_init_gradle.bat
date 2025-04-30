@echo off
echo 正在恢复init.gradle文件...

if exist "C:\Users\thinker\.gradle\init.gradle.bak" (
    ren "C:\Users\thinker\.gradle\init.gradle.bak" "init.gradle"
    echo init.gradle.bak已恢复为init.gradle
) else (
    echo init.gradle.bak文件不存在，无法恢复
)

echo.
echo 操作完成
echo.
pause 