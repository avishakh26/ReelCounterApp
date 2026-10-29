// The only module that talks to Supabase. Supabase types stay internal; other modules see the
// data-source interfaces, plain DTOs and RemoteException.
plugins {
    alias(libs.plugins.reelcounter.android.library)
    alias(libs.plugins.reelcounter.android.secrets)
    alias(libs.plugins.reelcounter.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.reelcounter.core.network"
}

dependencies {
    api(libs.kotlinx.coroutines.core)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.androidx.dataStore.preferences)
    implementation(libs.kotlinx.serialization.json)
}
