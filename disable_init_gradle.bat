@echo off
echo 正在禁用init.gradle文件...

if exist "C:\Users\thinker\.gradle\init.gradle" (
    ren "C:\Users\thinker\.gradle\init.gradle" "init.gradle.bak"
    echo init.gradle已重命名为init.gradle.bak
) else (
    echo init.gradle文件不存在
)

echo.
echo 现在您可以在Android Studio中进行Gradle同步
echo 同步完成后，请运行restore_init_gradle.bat来恢复文件
echo.
pause 