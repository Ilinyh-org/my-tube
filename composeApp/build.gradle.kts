// my-tube composeApp: shared Compose UI and platform entry points (AGENTS.md §3.1).
// Issue #18: application foundation only — no playback, library, cloud, or sync features.

import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

android {
    namespace = "dev.mytube.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.mytube.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.desktop.application {
    mainClass = "dev.mytube.app.MainKt"

    nativeDistributions {
        targetFormats(TargetFormat.Deb, TargetFormat.Msi)
        packageName = "my-tube"
        packageVersion = "0.1.0"
    }
}
