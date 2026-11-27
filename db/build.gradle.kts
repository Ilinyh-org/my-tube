// my-tube db: SQLDelight schema and queries for libraries, tracks, and
// playlists (PLAN-PHASE1 step 4). JVM target is the locally verifiable
// target; platform drivers for Android/desktop arrive with their phases.
// Privacy-relevant column values are protected at rest by the ADR-004
// application-level encryption layer (:crypto, applied in Repositories.kt
// before these statements run); this schema deliberately does not depend on
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
            api(project(":core"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        // JDBC (sqlite) driver is test-only for now; platform drivers for the
        // Android/desktop targets arrive with their phases. :crypto supplies
        // the concrete ADR-004 ValueCipher under test.
        jvmTest.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
            implementation(project(":crypto"))
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
