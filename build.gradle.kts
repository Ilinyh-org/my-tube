// Top-level build configuration for the my-tube encrypted music player.
// Technology stack is fixed by AGENTS.md; do not add plugins/dependencies without approval.

plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidApplication) apply false
}

subprojects {
    // Keep daemons off in CI-like/bot environments; builds are run per-invocation.
}
