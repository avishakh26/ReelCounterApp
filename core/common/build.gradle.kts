plugins {
    alias(libs.plugins.reelcounter.android.library)
    alias(libs.plugins.reelcounter.hilt)
}

android {
    namespace = "com.reelcounter.core.common"
}

dependencies {
    // Flow is part of this module's public API (NetworkMonitor), so consumers need coroutines too.
    api(libs.kotlinx.coroutines.core)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.kotlinx.coroutines.android)
}
