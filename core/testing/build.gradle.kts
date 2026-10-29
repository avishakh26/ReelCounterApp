// Test doubles and rules shared by unit tests. Consumed only as testImplementation.
plugins {
    alias(libs.plugins.reelcounter.jvm.library)
}

dependencies {
    api(projects.core.domain)
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
}
