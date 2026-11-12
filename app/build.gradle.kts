plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
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
        versionCode = 5
        versionName = "1.0.4"

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
            buildConfigField("boolean", "USE_TEST_ADS", "false")
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3840731205415196~4572255037"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("common")
            isMinifyEnabled = false
            buildConfigField("boolean", "USE_TEST_ADS", "true")
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
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
