// my-tube core: shared business logic and domain models (AGENTS.md §3.1).
// JVM target is the locally verifiable target; Android/desktop UI targets are
// added when the Android SDK is owner-approved and the UI phase begins.

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
