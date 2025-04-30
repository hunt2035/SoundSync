@echo off
chcp 936
echo Stopping all Gradle daemons...
call gradlew --stop

echo Closing Android Studio...
taskkill /F /IM studio64.exe 2>nul
taskkill /F /IM studio.exe 2>nul

echo Waiting for processes to close...
timeout /t 5 /nobreak

echo Cleaning project build directories...
if exist "app\build" (
    echo Deleting app\build directory...
    rmdir /s /q "app\build"
)

if exist "build" (
    echo Deleting build directory...
    rmdir /s /q "build"
)

echo Cleaning Gradle cache...
if exist "%USERPROFILE%\.gradle\caches" (
    echo Deleting Gradle cache...
    rmdir /s /q "%USERPROFILE%\.gradle\caches"
)

if exist "%USERPROFILE%\.gradle\daemon" (
    echo Deleting Gradle daemon directory...
    rmdir /s /q "%USERPROFILE%\.gradle\daemon"
)

if exist "%USERPROFILE%\.gradle\wrapper" (
    echo Deleting Gradle wrapper directory...
    rmdir /s /q "%USERPROFILE%\.gradle\wrapper"
)

echo Creating Gradle wrapper directories...
set GRADLE_USER_HOME=%USERPROFILE%\.gradle
set GRADLE_HOME=%GRADLE_USER_HOME%\wrapper\dists\gradle-8.11-bin
if not exist "%GRADLE_HOME%" (
    echo Creating directory: %GRADLE_HOME%
    mkdir "%GRADLE_HOME%"
)

echo Setting up Java environment...
set JAVA_HOME=D:\Programs\Android2\Android Studio\jbr
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo Error: Java not found at %JAVA_HOME%
    exit /b 1
)
set PATH=%JAVA_HOME%\bin;%PATH%

echo Updating Gradle wrapper...
set GRADLE_OPTS=-Dgradle.user.home="%USERPROFILE%\.gradle"
call gradlew wrapper --gradle-version 8.11 --distribution-type bin

echo Initializing Gradle...
call gradlew --version

echo Reset completed!
echo Please restart Android Studio and sync the project. 