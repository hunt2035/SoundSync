plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    kotlin("kapt")
}

android {
    namespace = "org.soundsync.ebook"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.soundsync.ebook"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.36"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // 添加Lint配置
    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = false
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
        }
    }
}

// 添加 kapt 配置
kapt {
    correctErrorTypes = true
    useBuildCache = true
}

// 添加JitPack仓库
repositories {
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://maven.google.com") }
}

dependencies {
    // 强制使用兼容的Kotlin版本
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.22")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.22")
    
    // AndroidX 核心库
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    
    // Compose 相关
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    
    // 导航组件
    implementation(libs.androidx.navigation.compose)
    
    // Room 数据库
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)
    
    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    
    // DataStore
    implementation(libs.androidx.datastore.preferences)
    
    // Accompanist
    implementation(libs.accompanist.permissions)
    implementation(libs.accompanist.systemuicontroller)
    
    // 图片加载
    implementation(libs.coil.compose)
    
    // PDF支持
    implementation(libs.android.pdf.viewer)
    
    // 取消注释并使用Tom Roush PDFBox库 (Android兼容版)
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    
    // 移除标准PDFBox库 (不兼容Android)
    // implementation("org.apache.pdfbox:pdfbox:3.0.1")
    
    // Gson
    implementation("com.google.code.gson:gson:2.10.1")
    
    // 测试依赖
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // 添加依赖冲突解决配置
    configurations.all {
        resolutionStrategy {
            force("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.22")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.22")
            force("androidx.core:core:1.12.0")
            exclude(group = "com.android.support", module = "support-compat")
        }
    }

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // Lottie
    implementation("com.airbnb.android:lottie-compose:6.3.0")
    
    // Jsoup
    implementation("org.jsoup:jsoup:1.17.2")
    
    // Google Fonts
    implementation("androidx.compose.ui:ui-text-google-fonts:1.6.1")
    
    // DocumentFile
    implementation("androidx.documentfile:documentfile:1.0.1")
    
    // Apache POI for Word files
    implementation("org.apache.poi:poi:5.2.3")
    implementation("org.apache.poi:poi-ooxml:5.2.3")
    implementation("org.apache.poi:poi-scratchpad:5.2.3")
    
    // 微信分享SDK
    implementation("com.tencent.mm.opensdk:wechat-sdk-android-without-mta:6.8.0")
    
    // ML Kit OCR依赖
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0")
    
    // CameraX依赖
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")

    // 图片裁剪库 - 使用 ArthurHub 的 Android-Image-Cropper 替代 ImageCropView
    implementation("com.theartofdev.edmodo:android-image-cropper:2.8.0")
    
    // 原来的图片裁剪库（已废弃）
    // implementation("io.github.rroohit:ImageCropView:2.8.0")
} 