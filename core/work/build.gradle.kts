// Background work: periodic usage-log reads and reacting to time/zone changes.
plugins {
    alias(libs.plugins.reelcounter.android.library)
    alias(libs.plugins.reelcounter.hilt)
}

android {
    namespace = "com.reelcounter.core.work"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.domain)

    api(libs.androidx.work.ktx)
    api(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
}
