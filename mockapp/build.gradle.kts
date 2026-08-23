// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 marshsong
// Mock 示例应用：仅用于 T2 自动化测试与演示，无任何真实功能。

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.mocksuperapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.mocksuperapp"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
