@echo off
echo 正在停止Gradle守护进程...
call gradlew --stop

echo 正在清理构建缓存...
rmdir /s /q .gradle\buildOutputCleanup
rmdir /s /q .gradle\kotlin-profile
rmdir /s /q build\
rmdir /s /q app\build\

echo 正在删除.idea目录中的编译缓存...
rmdir /s /q .idea\libraries
rmdir /s /q .idea\modules

echo 开始重新构建项目...
call gradlew clean
call gradlew build --info

echo 完成！ 