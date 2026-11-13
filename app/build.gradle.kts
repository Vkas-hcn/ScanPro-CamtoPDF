import com.github.megatronking.stringfog.plugin.StringFogExtension

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

// ---- StringFog 字符串加密（照 SmartPdfScan_luow app/build.gradle，转为 Kotlin DSL）----
apply(plugin = "stringfog")

configure<StringFogExtension> {
    // 加解密实现类（与根 build.gradle.kts 的 xor classpath 一致）
    implementation = "com.github.megatronking.stringfog.xor.StringFogImpl"
    // 生成的 StringFog 辅助类所在包名
    packageName = "com.github.megatronking.stringfog.app"
    // 加密开关
    enable = true
    // 需要加密的包（本项目 com.scanpro.cam.pdf）
    fogPackages = arrayOf(
        "com.bockcock.adjust",
        "com.buncead.common.util",
        "com.mymy.mymy",
        "com.scanpro.cam.pdf",
        "com.zppko.pdfplus.nor",
    )
    // 随机密钥生成器
    kg = com.github.megatronking.stringfog.plugin.kg.RandomKeyGenerator()
    // bytes 模式
    mode = com.github.megatronking.stringfog.plugin.StringFogMode.bytes
}
android {
    namespace = "com.scanpro.cam.pdf"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.scanpro.cam.pdf"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "1.0.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Overridden per buildType; debug keeps Google sample App ID.
        manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
    }

    signingConfigs {
        create("common") {
            storeFile = rootProject.file("cam.jks")
            storePassword = "123456"
            keyAlias = "key0"
            keyPassword = "123456"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("common")
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("Boolean", "IS_BUNDLE", "true")
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3840731205415196~4572255037"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("common")
            isMinifyEnabled = false
            buildConfigField("Boolean", "IS_BUNDLE", "false")
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.applicationId.set("com.npro.mac.fdp.debug")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.android.doc.viewer)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.work.runtime)
    implementation(libs.play.services.ads.identifier)
    implementation(libs.install.referrer)
    implementation(libs.play.review)
    implementation(libs.play.review.ktx)
    implementation(libs.user.messaging.platform)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.config)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.messaging)

    // StringFog 运行时解密库（与插件配置的加解密算法一致）
    implementation("com.github.megatronking.stringfog:xor:5.0.0")

    implementation("com.flux.tracksdk:tracksdk:3.0.3")
    implementation("com.adjust.sdk:adjust-android:5.6.1")
    implementation("com.facebook.android:facebook-android-sdk:18.3.0")
    implementation("androidx.media:media:1.7.0")

    // ---- TradPlus 聚合（照 SmartPdfScan_luow app/build.gradle，16.9.0.1 系列） ----
    implementation("com.tradplusad:tradplus:16.9.0.1")
    // Meta
    implementation("com.facebook.android:audience-network-sdk:6.22.0")
    implementation("com.tradplusad:tradplus-facebook:1.16.9.0.1")
    // Pangle
    implementation("com.tradplusad:tradplus-pangle:19.16.9.0.1")
    implementation("com.pangle.global:pag-sdk:8.2.0.4")
    // Mintegral
    implementation("com.tradplusad:tradplus-mintegralx_overseas:18.16.9.0.1")
    implementation("com.mbridge.msdk.oversea:mbridge_android_sdk:17.1.81")
    // TP Exchange
    implementation("com.google.code.gson:gson:2.8.6")
    implementation("com.tradplusad:tp_exchange:40.16.9.0.1")
    // GMA Next-Gen（AdMob 作为聚合渠道，替换旧 play-services-ads）
    implementation("com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.4.0")
    implementation("com.tradplusad:tradplus-gma-nextgen:2.16.9.0.1")

    // ---- TradPlus 比价 SDK（compare_price-release.aar，本地 AAR） ----
    implementation(files("libs/compare_price-release.aar"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

apply(plugin = "com.google.gms.google-services")
