// Repository implementations: domain contracts on top of :core:network and local storage.
plugins {
    alias(libs.plugins.reelcounter.android.library)
    alias(libs.plugins.reelcounter.hilt)
}

android {
    namespace = "com.reelcounter.core.data"
}

dependencies {
    api(projects.core.domain)

    implementation(projects.core.common)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.network)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.dataStore.preferences)

    testImplementation(projects.core.testing)
    testImplementation(libs.kotlinx.coroutines.test)
}
