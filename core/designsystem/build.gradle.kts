plugins {
    alias(libs.plugins.reelcounter.android.library)
    alias(libs.plugins.reelcounter.android.library.compose)
}

android {
    namespace = "com.reelcounter.core.designsystem"
}

dependencies {
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material.iconsExtended)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.runtime)
    api(libs.androidx.compose.ui)
}
