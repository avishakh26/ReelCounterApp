// Sample data and a preview theme wrapper for Compose previews.
// Other modules consume this ONLY as debugImplementation, so none of it can reach a release build.
plugins {
    alias(libs.plugins.reelcounter.android.library)
    alias(libs.plugins.reelcounter.android.library.compose)
}

android {
    namespace = "com.reelcounter.core.preview"
}

dependencies {
    api(projects.core.designsystem)
    api(projects.core.model)
    api(libs.androidx.compose.ui.tooling.preview)
}
