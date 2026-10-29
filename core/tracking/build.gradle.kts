// Android adapters for the tracking engine: the usage log, the usage-access permission,
// installed-app checks and the clock. The engine itself is pure Kotlin in :core:domain.
plugins {
    alias(libs.plugins.reelcounter.android.library)
    alias(libs.plugins.reelcounter.hilt)
}

android {
    namespace = "com.reelcounter.core.tracking"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.domain)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
