// Small user preferences in DataStore: disclosure consent and pause state.
plugins {
    alias(libs.plugins.reelcounter.android.library)
    alias(libs.plugins.reelcounter.hilt)
}

android {
    namespace = "com.reelcounter.core.datastore"
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.dataStore.preferences)
}
