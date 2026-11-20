// my-tube db: SQLDelight schema and queries for libraries, tracks, and
// playlists (PLAN-PHASE1 step 4). JVM target is the locally verifiable
// target; platform drivers for Android/desktop arrive with their phases.
// Database contents are protected at rest by the ADR-004 application-level
// encryption layer (:crypto); this schema deliberately does not depend on
// any encryption mechanism.

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.sqldelight)
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
        // JDBC (sqlite) driver is test-only for now; platform drivers for the
        // Android/desktop targets arrive with their phases.
        jvmTest.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}

sqldelight {
    databases {
        create("MyTubeDatabase") {
            packageName.set("org.mytube.db")
        }
    }
}
