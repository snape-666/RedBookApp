plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 注：原先这里会读取 supabase.properties 并把第三方密钥注入 BuildConfig。
// 那些密钥（邮件服务 / DeepSeek / 豆包）已全部迁到 Supabase Edge Functions 的 Secret，
// 不再进入 APK，因此这段读取逻辑与其对应的 buildConfigField 一并移除。

android {
    namespace = "com.example.redbook"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.redbook"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.compose.foundation.layout)
    // Coil
    implementation(libs.coil.compose)
    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    //  Volley
    implementation(libs.volley)
    //  OkHttp (Realtime WebSocket)
    implementation(libs.okhttp)
    //  Kotlinx Coroutines Android(评论 AI 小助手网络请求线程池)
    implementation(libs.kotlinx.coroutines.android)
    //  Media3 ExoPlayer（视频播放：磁盘缓存 + 预加载，替代系统 VideoView）
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.datasource)
    implementation(libs.media3.ui)
    //  Test
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
