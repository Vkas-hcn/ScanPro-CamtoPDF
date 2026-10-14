// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath(libs.google.services.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    id("com.google.firebase.crashlytics") version "3.0.8" apply false

}
