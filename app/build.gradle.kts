import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 签名信息放 local.properties（已 gitignore），keystore 放 keystore/（同样不提交）。
// 换了机器就把这两样一起搬过去，否则无法覆盖升级。
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val releaseStorePath = localProps.getProperty("stb.storeFile")
val hasReleaseKey = releaseStorePath != null && rootProject.file(releaseStorePath).exists()

android {
    namespace = "cn.stb.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "cn.stb.subjecttoolbox"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file(releaseStorePath!!)
                storePassword = localProps.getProperty("stb.storePassword")
                keyAlias = localProps.getProperty("stb.keyAlias")
                keyPassword = localProps.getProperty("stb.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // 上架形态：R8 代码压缩 + 资源收缩。必须实测，混淆常把反射/序列化打坏
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 没有密钥时退化成未签名包，保证别的机器也能构建
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:registry"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":feature:home"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:chinese-poetry"))
    implementation(project(":feature:math-function"))
    implementation(project(":feature:english-dictionary"))
    implementation(project(":feature:chemistry-equation"))
    implementation(project(":feature:biology-diagram"))
    implementation(project(":feature:geography-diagram"))
    implementation(project(":feature:electronic-textbook"))
    implementation(project(":feature:formula"))

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
}
