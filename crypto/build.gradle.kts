// my-tube crypto: key hierarchy implementation per ADR-005 (Argon2id + XChaCha20-Poly1305).
// Common sources hold the versioned v1 wrap-blob format and recovery-key codec;
// JVM sources hold the BouncyCastle-backed derivation/AEAD engine (ADR-005).

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            api(project(":core"))
        }
        jvmMain.dependencies {
            implementation("org.bouncycastle:bcprov-jdk18on:1.79")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
