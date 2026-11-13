// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath(libs.google.services.plugin)

        // StringFog 字符串加密插件 + 加解密算法库（照 SmartPdfScan_luow 根 build.gradle）
        classpath("com.github.megatronking.stringfog:gradle-plugin:5.2.0")
        classpath("com.github.megatronking.stringfog:xor:5.0.0")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    id("com.google.firebase.crashlytics") version "3.0.8" apply false

}
