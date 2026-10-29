plugins {
    alias(libs.plugins.reelcounter.android.library)
    alias(libs.plugins.reelcounter.android.library.compose)
}

android {
    namespace = "com.reelcounter.core.ui"
}

dependencies {
    api(projects.core.designsystem)
    api(projects.core.model)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    debugImplementation(projects.core.preview)
}
