package com.reelcounter.core.network

/**
 * Where the backend lives. Comes from BuildConfig (secrets.properties). The key is the
 * publishable/anon client key; it identifies the project and is safe in the app because row level
 * security decides what each user can access.
 */
data class SupabaseConfig(
    val url: String,
    val publishableKey: String,
) {
    /** False when the build still has the placeholders from secrets.defaults.properties. */
    val isConfigured: Boolean
        get() = url != UNCONFIGURED_URL && publishableKey != UNCONFIGURED_KEY

    fun publicObjectUrl(bucket: String, path: String): String =
        "${url.trimEnd('/')}/storage/v1/object/public/$bucket/$path"

    internal companion object {
        const val UNCONFIGURED_URL = "https://unconfigured.invalid"
        const val UNCONFIGURED_KEY = "unconfigured"
    }
}
