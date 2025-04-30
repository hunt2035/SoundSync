pluginManagement {
    repositories {
      //  maven { url = uri("https://repo.gradle.org/gradle/libs-releases/") }
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-google/") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://mirrors.cloud.tencent.com/gradle/") }
     //   maven { url = uri("https://dl.bintray.com/gradle/gradle-distributions/") }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    // 从 FAIL_ON_PROJECT_REPOS 改为 PREFER_SETTINGS
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
    //    maven { url = uri("https://repo.gradle.org/gradle/libs-releases/") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-google/") }
        maven { url = uri("https://jitpack.io") }
        google()
        mavenCentral()
        jcenter()
    }
}


rootProject.name = "ebook"
include(":app")
