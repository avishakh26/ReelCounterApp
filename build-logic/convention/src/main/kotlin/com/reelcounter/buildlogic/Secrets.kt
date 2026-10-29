package com.reelcounter.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.StringReader
import java.net.URI
import java.util.Base64
import java.util.Properties

/**
 * Client configuration read from `secrets.properties` (git-ignored, per machine or CI secret) with
 * fallbacks from `secrets.defaults.properties` (committed). The defaults file is the schema: only
 * keys it declares are exported, so unrelated secrets in the local file never reach the app.
 */
internal object Secrets {
    const val LOCAL_FILE = "secrets.properties"
    const val DEFAULTS_FILE = "secrets.defaults.properties"

    fun load(project: Project): Map<String, String> {
        val root = project.rootProject.layout.projectDirectory
        fun read(name: String): Properties = Properties().apply {
            project.providers.fileContents(root.file(name)).asText.orNull?.let { load(StringReader(it)) }
        }

        val defaults = read(DEFAULTS_FILE)
        if (defaults.isEmpty) {
            throw GradleException("$DEFAULTS_FILE is missing or empty; it declares which secrets the app reads.")
        }
        val local = read(LOCAL_FILE)
        return defaults.stringPropertyNames().sorted().associateWith { key ->
            (local.getProperty(key) ?: defaults.getProperty(key)).trim()
        }
    }

    /**
     * Fails the build if a value could grant more than client-level access. Supabase secret keys
     * (`sb_secret_…`) and legacy `service_role` JWTs bypass row level security and must only ever
     * live on a server.
     */
    fun validate(secrets: Map<String, String>) {
        secrets.forEach { (key, value) ->
            if (value.startsWith("sb_secret_")) {
                throw GradleException("$key holds a Supabase secret key. Use the publishable key; secret keys never ship in the app.")
            }
            if (jwtRole(value) == "service_role") {
                throw GradleException("$key holds a service_role JWT. Use the anon/publishable key; service-role keys never ship in the app.")
            }
            if (key.endsWith("_URL")) validateUrl(key, value)
        }
    }

    private fun validateUrl(key: String, value: String) {
        val uri = runCatching { URI(value) }.getOrNull()
            ?: throw GradleException("$key is not a valid URL: $value")
        val localHosts = setOf("localhost", "127.0.0.1", "10.0.2.2")
        val ok = uri.scheme == "https" || (uri.scheme == "http" && uri.host in localHosts)
        if (!ok) throw GradleException("$key must use https (plain http is allowed only for a local Supabase): $value")
    }

    /** The `role` claim if [value] looks like a JWT, otherwise null. */
    private fun jwtRole(value: String): String? {
        val parts = value.split('.')
        if (parts.size != 3) return null
        val payload = runCatching { String(Base64.getUrlDecoder().decode(parts[1].padEnd((parts[1].length + 3) / 4 * 4, '='))) }
            .getOrNull() ?: return null
        return Regex(""""role"\s*:\s*"([^"]+)"""").find(payload)?.groupValues?.get(1)
    }
}
