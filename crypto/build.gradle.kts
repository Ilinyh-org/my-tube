// my-tube crypto: key derivation, encryption, key management (AGENTS.md §3.1).
// NOTE (repo AGENTS.md §12): no production cryptographic format is implemented
// until ADRs for the key hierarchy (ADR-005) and DB encryption (ADR-004) are
// documented and reviewed. This module currently contains the module skeleton
// and placeholder-free shared types only.

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvm()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
