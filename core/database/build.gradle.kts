// Room: raw sessions, daily totals, coverage, platform settings and ingest state.
plugins {
    alias(libs.plugins.reelcounter.android.library)
    alias(libs.plugins.reelcounter.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "com.reelcounter.core.database"
}

// Exported schemas are committed so future migrations can be written and tested against them.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
}
