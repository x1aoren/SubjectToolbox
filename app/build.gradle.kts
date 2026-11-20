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

// 默认域名：开发调试走本机，生产走 stb.mcobs.cn；可在 local.properties 覆盖。
val debugApiBase = localProps.getProperty("stb.debugApiBase") ?: "http://127.0.0.1:8787"
val releaseApiBase = localProps.getProperty("stb.releaseApiBase") ?: "https://stb.mcobs.cn"
val apiKey = localProps.getProperty("stb.apiKey") ?: ""
// 临时调试开关：IP 直连时服务端证书是自签的，需要跳过系统证书与主机名校验。
// 只在 local.properties 里显式写 stb.allowInsecureTls=true 才生效；切回域名后务必删掉。
val allowInsecureTls = localProps.getProperty("stb.allowInsecureTls")?.trim() == "true"
if (allowInsecureTls) {
    logger.warn("警告：已启用「忽略 HTTPS 证书校验」，仅用于 IP 直连的临时联调，禁止对外分发")
}
// 密钥只存在 local.properties（已 gitignore），换机器时容易漏搬；缺失时云端全部 401，
// 构建阶段先吼一声，避免打出一个「看起来正常、其实连不上」的包。
if (apiKey.isBlank()) {
    logger.warn("警告：stb.apiKey 未配置，产物的云端同步 / 遥测 / 意见反馈都会失败，请检查 local.properties")
}

android {
    namespace = "cn.stb.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "cn.stb.subjecttoolbox"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0"
        buildConfigField("String", "STB_API_KEY", "\"$apiKey\"")
        // 是否跳过 HTTPS 证书校验（临时 IP 直连用），见上方 allowInsecureTls
        buildConfigField("boolean", "STB_ALLOW_INSECURE_TLS", allowInsecureTls.toString())
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
        debug {
            buildConfigField("String", "STB_API_BASE", "\"$debugApiBase\"")
        }
        release {
            buildConfigField("String", "STB_API_BASE", "\"$releaseApiBase\"")
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

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
    implementation(project(":feature:chinese-dictionary"))
    implementation(project(":feature:math-function"))
    implementation(project(":feature:english-dictionary"))
    implementation(project(":feature:english-grammar"))
    implementation(project(":feature:chemistry-equation"))
    implementation(project(":feature:biology-diagram"))
    implementation(project(":feature:geography-diagram"))
    implementation(project(":feature:electronic-textbook"))
    implementation(project(":feature:formula"))
    implementation(project(":feature:periodic-table"))

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
}
