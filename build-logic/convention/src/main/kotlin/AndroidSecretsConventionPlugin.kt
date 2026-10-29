import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.reelcounter.buildlogic.Secrets
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Exposes client configuration (e.g. SUPABASE_URL) as BuildConfig fields of an Android library.
 * Values come from git-ignored secrets.properties, falling back to the committed placeholders in
 * secrets.defaults.properties, and are validated so server-only keys can never be built in.
 *
 * Written in-house rather than using the Secrets Gradle Plugin so it uses only AGP 9's variant
 * API and adds the service-role guard.
 */
class AndroidSecretsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val secrets = Secrets.load(this)
            Secrets.validate(secrets)

            extensions.configure<LibraryExtension> {
                buildFeatures.buildConfig = true
            }
            extensions.configure<LibraryAndroidComponentsExtension> {
                onVariants { variant ->
                    secrets.forEach { (key, value) ->
                        variant.buildConfigFields?.put(
                            key,
                            BuildConfigField("String", "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\"", null),
                        )
                    }
                }
            }
        }
    }
}
