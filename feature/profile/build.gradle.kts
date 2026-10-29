plugins {
    alias(libs.plugins.reelcounter.android.feature)
}

android {
    namespace = "com.reelcounter.feature.profile"
}

dependencies {
    implementation(libs.androidx.activity.compose)
}
