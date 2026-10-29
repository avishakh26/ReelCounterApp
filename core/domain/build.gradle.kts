// Use cases, repository contracts and validation rules. Pure Kotlin: no Android, no Supabase.
plugins {
    alias(libs.plugins.reelcounter.jvm.library)
}

dependencies {
    api(projects.core.model)
    api(libs.kotlinx.coroutines.core)
}
