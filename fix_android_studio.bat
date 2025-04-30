@echo off
echo 正在修复Android Studio "Module not specified"错误...

echo 清理旧的文件...
rmdir /s /q .idea\modules

echo 创建必要的目录结构...
mkdir .idea\modules
mkdir .idea\modules\app

echo 创建项目模块定义文件...
echo ^<?xml version="1.0" encoding="UTF-8"?^> > .idea\modules.xml
echo ^<project version="4"^> >> .idea\modules.xml
echo   ^<component name="ProjectModuleManager"^> >> .idea\modules.xml
echo     ^<modules^> >> .idea\modules.xml
echo       ^<module fileurl="file://$PROJECT_DIR$/.idea/modules/ebook.iml" filepath="$PROJECT_DIR$/.idea/modules/ebook.iml" /^> >> .idea\modules.xml
echo       ^<module fileurl="file://$PROJECT_DIR$/.idea/modules/app/ebook.app.iml" filepath="$PROJECT_DIR$/.idea/modules/app/ebook.app.iml" /^> >> .idea\modules.xml
echo       ^<module fileurl="file://$PROJECT_DIR$/.idea/modules/app/ebook.app.main.iml" filepath="$PROJECT_DIR$/.idea/modules/app/ebook.app.main.iml" /^> >> .idea\modules.xml
echo       ^<module fileurl="file://$PROJECT_DIR$/.idea/modules/app/ebook.app.unitTest.iml" filepath="$PROJECT_DIR$/.idea/modules/app/ebook.app.unitTest.iml" /^> >> .idea\modules.xml
echo     ^</modules^> >> .idea\modules.xml
echo   ^</component^> >> .idea\modules.xml
echo ^</project^> >> .idea\modules.xml

echo 创建根模块定义...
echo ^<?xml version="1.0" encoding="UTF-8"?^> > .idea\modules\ebook.iml
echo ^<module external.linked.project.id="ebook" external.linked.project.path="$MODULE_DIR$/../.." external.root.project.path="$MODULE_DIR$/../.." external.system.id="GRADLE" external.system.module.group="" external.system.module.version="unspecified" type="JAVA_MODULE" version="4"^> >> .idea\modules\ebook.iml
echo   ^<component name="NewModuleRootManager"^> >> .idea\modules\ebook.iml
echo     ^<exclude-output /^> >> .idea\modules\ebook.iml
echo     ^<content url="file://$MODULE_DIR$/../.."^> >> .idea\modules\ebook.iml
echo       ^<excludeFolder url="file://$MODULE_DIR$/../../.gradle" /^> >> .idea\modules\ebook.iml
echo       ^<excludeFolder url="file://$MODULE_DIR$/../../build" /^> >> .idea\modules\ebook.iml
echo     ^</content^> >> .idea\modules\ebook.iml
echo     ^<orderEntry type="inheritedJdk" /^> >> .idea\modules\ebook.iml
echo     ^<orderEntry type="sourceFolder" forTests="false" /^> >> .idea\modules\ebook.iml
echo   ^</component^> >> .idea\modules\ebook.iml
echo ^</module^> >> .idea\modules\ebook.iml

echo 创建应用模块定义...
echo ^<?xml version="1.0" encoding="UTF-8"?^> > .idea\modules\app\ebook.app.iml
echo ^<module external.linked.project.id=":app" external.linked.project.path="$MODULE_DIR$/../../../app" external.root.project.path="$MODULE_DIR$/../../.." external.system.id="GRADLE" type="JAVA_MODULE" version="4"^> >> .idea\modules\app\ebook.app.iml
echo   ^<component name="FacetManager"^> >> .idea\modules\app\ebook.app.iml
echo     ^<facet type="android-gradle" name="Android-Gradle"^> >> .idea\modules\app\ebook.app.iml
echo       ^<configuration^> >> .idea\modules\app\ebook.app.iml
echo         ^<option name="GRADLE_PROJECT_PATH" value=":app" /^> >> .idea\modules\app\ebook.app.iml
echo       ^</configuration^> >> .idea\modules\app\ebook.app.iml
echo     ^</facet^> >> .idea\modules\app\ebook.app.iml
echo     ^<facet type="android" name="Android"^> >> .idea\modules\app\ebook.app.iml
echo       ^<configuration^> >> .idea\modules\app\ebook.app.iml
echo         ^<option name="SELECTED_BUILD_VARIANT" value="debug" /^> >> .idea\modules\app\ebook.app.iml
echo       ^</configuration^> >> .idea\modules\app\ebook.app.iml
echo     ^</facet^> >> .idea\modules\app\ebook.app.iml
echo   ^</component^> >> .idea\modules\app\ebook.app.iml
echo   ^<component name="NewModuleRootManager" LANGUAGE_LEVEL="JDK_11"^> >> .idea\modules\app\ebook.app.iml
echo     ^<output url="file://$MODULE_DIR$/../../../app/build/intermediates/javac/debug/classes" /^> >> .idea\modules\app\ebook.app.iml
echo     ^<content url="file://$MODULE_DIR$/../../../app"^> >> .idea\modules\app\ebook.app.iml
echo       ^<sourceFolder url="file://$MODULE_DIR$/../../../app/src/main/java" isTestSource="false" /^> >> .idea\modules\app\ebook.app.iml
echo       ^<sourceFolder url="file://$MODULE_DIR$/../../../app/src/main/res" type="java-resource" /^> >> .idea\modules\app\ebook.app.iml
echo     ^</content^> >> .idea\modules\app\ebook.app.iml
echo     ^<orderEntry type="jdk" jdkName="Android API 34 Platform" jdkType="Android SDK" /^> >> .idea\modules\app\ebook.app.iml
echo     ^<orderEntry type="sourceFolder" forTests="false" /^> >> .idea\modules\app\ebook.app.iml
echo   ^</component^> >> .idea\modules\app\ebook.app.iml
echo ^</module^> >> .idea\modules\app\ebook.app.iml

echo 创建app.main模块定义...
echo ^<?xml version="1.0" encoding="UTF-8"?^> > .idea\modules\app\ebook.app.main.iml
echo ^<module external.linked.project.id=":app:main" external.linked.project.path="$MODULE_DIR$/../../../app" external.root.project.path="$MODULE_DIR$/../../.." external.system.id="GRADLE" external.system.module.group="ebook.app" external.system.module.type="sourceSet" external.system.module.version="unspecified" type="JAVA_MODULE" version="4"^> >> .idea\modules\app\ebook.app.main.iml
echo   ^<component name="FacetManager"^> >> .idea\modules\app\ebook.app.main.iml
echo     ^<facet type="android" name="Android"^> >> .idea\modules\app\ebook.app.main.iml
echo       ^<configuration^> >> .idea\modules\app\ebook.app.main.iml
echo         ^<option name="SELECTED_BUILD_VARIANT" value="debug" /^> >> .idea\modules\app\ebook.app.main.iml
echo       ^</configuration^> >> .idea\modules\app\ebook.app.main.iml
echo     ^</facet^> >> .idea\modules\app\ebook.app.main.iml
echo   ^</component^> >> .idea\modules\app\ebook.app.main.iml
echo   ^<component name="NewModuleRootManager" LANGUAGE_LEVEL="JDK_11"^> >> .idea\modules\app\ebook.app.main.iml
echo     ^<output url="file://$MODULE_DIR$/../../../app/build/intermediates/javac/debug/classes" /^> >> .idea\modules\app\ebook.app.main.iml
echo     ^<content url="file://$MODULE_DIR$/../../../app/src/main"^> >> .idea\modules\app\ebook.app.main.iml
echo       ^<sourceFolder url="file://$MODULE_DIR$/../../../app/src/main/java" isTestSource="false" /^> >> .idea\modules\app\ebook.app.main.iml
echo       ^<sourceFolder url="file://$MODULE_DIR$/../../../app/src/main/res" type="java-resource" /^> >> .idea\modules\app\ebook.app.main.iml
echo     ^</content^> >> .idea\modules\app\ebook.app.main.iml
echo     ^<orderEntry type="jdk" jdkName="Android API 34 Platform" jdkType="Android SDK" /^> >> .idea\modules\app\ebook.app.main.iml
echo     ^<orderEntry type="sourceFolder" forTests="false" /^> >> .idea\modules\app\ebook.app.main.iml
echo   ^</component^> >> .idea\modules\app\ebook.app.main.iml
echo ^</module^> >> .idea\modules\app\ebook.app.main.iml

echo 创建runConfigurations目录...
mkdir .idea\runConfigurations

echo 创建app运行配置...
echo ^<?xml version="1.0" encoding="UTF-8"?^> > .idea\runConfigurations\app.xml
echo ^<component name="ProjectRunConfigurationManager"^> >> .idea\runConfigurations\app.xml
echo   ^<configuration default="false" name="app" type="AndroidRunConfigurationType" factoryName="Android App"^> >> .idea\runConfigurations\app.xml
echo     ^<module name="ebook.app.main" /^> >> .idea\runConfigurations\app.xml
echo     ^<option name="DEPLOY" value="true" /^> >> .idea\runConfigurations\app.xml
echo     ^<option name="DEPLOY_APK_FROM_BUNDLE" value="false" /^> >> .idea\runConfigurations\app.xml
echo     ^<option name="DEPLOY_AS_INSTANT" value="false" /^> >> .idea\runConfigurations\app.xml
echo     ^<option name="ARTIFACT_NAME" value="" /^> >> .idea\runConfigurations\app.xml
echo     ^<option name="PM_INSTALL_OPTIONS" value="" /^> >> .idea\runConfigurations\app.xml
echo     ^<option name="ALL_USERS" value="false" /^> >> .idea\runConfigurations\app.xml
echo     ^<option name="ALWAYS_INSTALL_WITH_PM" value="false" /^> >> .idea\runConfigurations\app.xml
echo     ^<option name="CLEAR_APP_STORAGE" value="false" /^> >> .idea\runConfigurations\app.xml
echo     ^<option name="DYNAMIC_FEATURES_DISABLED_LIST" value="" /^> >> .idea\runConfigurations\app.xml
echo     ^<option name="ACTIVITY_EXTRA_FLAGS" value="" /^> >> .idea\runConfigurations\app.xml
echo     ^<option name="MODE" value="default_activity" /^> >> .idea\runConfigurations\app.xml
echo     ^<option name="CLEAR_LOGCAT" value="false" /^> >> .idea\runConfigurations\app.xml
echo     ^<option name="SHOW_LOGCAT_AUTOMATICALLY" value="false" /^> >> .idea\runConfigurations\app.xml
echo     ^<option name="INSPECTION_WITHOUT_ACTIVITY_RESTART" value="false" /^> >> .idea\runConfigurations\app.xml
echo     ^<option name="TARGET_SELECTION_MODE" value="DEVICE_AND_SNAPSHOT_COMBO_BOX" /^> >> .idea\runConfigurations\app.xml
echo     ^<option name="DEBUGGER_TYPE" value="Auto" /^> >> .idea\runConfigurations\app.xml
echo     ^<Auto^> >> .idea\runConfigurations\app.xml
echo       ^<option name="USE_JAVA_AWARE_DEBUGGER" value="false" /^> >> .idea\runConfigurations\app.xml
echo       ^<option name="SHOW_STATIC_VARS" value="true" /^> >> .idea\runConfigurations\app.xml
echo       ^<option name="WORKING_DIR" value="" /^> >> .idea\runConfigurations\app.xml
echo       ^<option name="TARGET_LOGGING_CHANNELS" value="lldb process:gdb-remote packets" /^> >> .idea\runConfigurations\app.xml
echo       ^<option name="SHOW_OPTIMIZED_WARNING" value="true" /^> >> .idea\runConfigurations\app.xml
echo     ^</Auto^> >> .idea\runConfigurations\app.xml
echo     ^<Hybrid^> >> .idea\runConfigurations\app.xml
echo       ^<option name="USE_JAVA_AWARE_DEBUGGER" value="false" /^> >> .idea\runConfigurations\app.xml
echo       ^<option name="SHOW_STATIC_VARS" value="true" /^> >> .idea\runConfigurations\app.xml
echo       ^<option name="WORKING_DIR" value="" /^> >> .idea\runConfigurations\app.xml
echo       ^<option name="TARGET_LOGGING_CHANNELS" value="lldb process:gdb-remote packets" /^> >> .idea\runConfigurations\app.xml
echo       ^<option name="SHOW_OPTIMIZED_WARNING" value="true" /^> >> .idea\runConfigurations\app.xml
echo     ^</Hybrid^> >> .idea\runConfigurations\app.xml
echo     ^<Java /^> >> .idea\runConfigurations\app.xml
echo     ^<Native^> >> .idea\runConfigurations\app.xml
echo       ^<option name="USE_JAVA_AWARE_DEBUGGER" value="false" /^> >> .idea\runConfigurations\app.xml
echo       ^<option name="SHOW_STATIC_VARS" value="true" /^> >> .idea\runConfigurations\app.xml
echo       ^<option name="WORKING_DIR" value="" /^> >> .idea\runConfigurations\app.xml
echo       ^<option name="TARGET_LOGGING_CHANNELS" value="lldb process:gdb-remote packets" /^> >> .idea\runConfigurations\app.xml
echo       ^<option name="SHOW_OPTIMIZED_WARNING" value="true" /^> >> .idea\runConfigurations\app.xml
echo     ^</Native^> >> .idea\runConfigurations\app.xml
echo     ^<Profilers^> >> .idea\runConfigurations\app.xml
echo       ^<option name="ADVANCED_PROFILING_ENABLED" value="false" /^> >> .idea\runConfigurations\app.xml
echo       ^<option name="STARTUP_PROFILING_ENABLED" value="false" /^> >> .idea\runConfigurations\app.xml
echo       ^<option name="STARTUP_CPU_PROFILING_ENABLED" value="false" /^> >> .idea\runConfigurations\app.xml
echo       ^<option name="STARTUP_CPU_PROFILING_CONFIGURATION_NAME" value="Java/Kotlin Method Sample (legacy)" /^> >> .idea\runConfigurations\app.xml
echo       ^<option name="STARTUP_NATIVE_MEMORY_PROFILING_ENABLED" value="false" /^> >> .idea\runConfigurations\app.xml
echo       ^<option name="NATIVE_MEMORY_SAMPLE_RATE_BYTES" value="2048" /^> >> .idea\runConfigurations\app.xml
echo     ^</Profilers^> >> .idea\runConfigurations\app.xml
echo     ^<option name="DEEP_LINK" value="" /^> >> .idea\runConfigurations\app.xml
echo     ^<option name="ACTIVITY_CLASS" value="" /^> >> .idea\runConfigurations\app.xml
echo     ^<option name="SEARCH_ACTIVITY_IN_GLOBAL_SCOPE" value="false" /^> >> .idea\runConfigurations\app.xml
echo     ^<option name="SKIP_ACTIVITY_VALIDATION" value="false" /^> >> .idea\runConfigurations\app.xml
echo     ^<method v="2"^> >> .idea\runConfigurations\app.xml
echo       ^<option name="Android.Gradle.BeforeRunTask" enabled="true" /^> >> .idea\runConfigurations\app.xml
echo     ^</method^> >> .idea\runConfigurations\app.xml
echo   ^</configuration^> >> .idea\runConfigurations\app.xml
echo ^</component^> >> .idea\runConfigurations\app.xml

echo 更新本地缓存...
cd .
call gradlew --stop
rmdir /s /q .gradle\buildOutputCleanup
call gradlew clean

echo 修复完成！现在您应该能够在Android Studio中运行该应用。
echo 1. 重新启动Android Studio
echo 2. 重新打开项目
echo 3. 选择"app"配置并运行 