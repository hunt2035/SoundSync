allprojects {
    buildscript {
        repositories {
            // 设置超时配置
            resources.timeoutSecs = 300  // 增加超时时间到5分钟
            
            // 优先使用国内镜像仓库，并调整顺序
            maven("https://mirrors.cloud.tencent.com/gradle/")
            maven("https://maven.aliyun.com/repository/gradle-plugin")
            maven("https://maven.aliyun.com/repository/public")
            maven("https://maven.aliyun.com/repository/central")
            maven("https://maven.aliyun.com/repository/google")
            maven("https://jitpack.io")  // 添加JitPack仓库
          //  maven("https://plugins.gradle.org/m2/")
          //  mavenCentral()
          //  google()
          //  gradlePluginPortal()
            // 这些放在后面作为备用
         //   maven("https://dl.bintray.com/gradle/gradle-distributions/")
         //   maven("https://services.gradle.org/distributions/")
         //   maven("https://repo.gradle.org/gradle/libs-releases/")
        }
    }

    repositories {
        // 设置超时配置
        resources.timeoutSecs = 300  // 增加超时时间到5分钟
        
        // 优先使用国内镜像仓库，并调整顺序
        maven("https://mirrors.cloud.tencent.com/gradle/")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://jitpack.io")  // 添加JitPack仓库
      //  mavenCentral()
      //  google()
        // 这些放在后面作为备用
      //  maven("https://dl.bintray.com/gradle/gradle-distributions/")
      //  maven("https://services.gradle.org/distributions/")
      //  maven("https://repo.gradle.org/gradle/libs-releases/")
    }
}

// 修复Gradle解析问题的特殊配置
gradle.allprojects {
    repositories {
        // 确保可以解析常用Gradle依赖
        resources.timeoutSecs = 300  // 增加超时时间到5分钟
        
        maven("https://mirrors.cloud.tencent.com/gradle/")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://jitpack.io")  // 添加JitPack仓库
      //  maven("https://repo.gradle.org/gradle/libs-releases/")
      //  maven("https://dl.bintray.com/gradle/maven/")
    }
} 