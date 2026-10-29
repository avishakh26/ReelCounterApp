import com.reelcounter.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies

/**
 * A feature module: Compose UI, Hilt ViewModels and type-safe navigation routes.
 * Features depend on core modules only, never on each other; the app module wires them together.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "reelcounter.android.library")
            apply(plugin = "reelcounter.android.library.compose")
            apply(plugin = "reelcounter.hilt")
            apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

            dependencies {
                "implementation"(project(":core:designsystem"))
                "implementation"(project(":core:domain"))
                "implementation"(project(":core:ui"))

                "implementation"(libs.findLibrary("androidx-hilt-lifecycle-viewModelCompose").get())
                "implementation"(libs.findLibrary("androidx-lifecycle-runtimeCompose").get())
                "implementation"(libs.findLibrary("androidx-lifecycle-viewModelCompose").get())
                "implementation"(libs.findLibrary("androidx-navigation-compose").get())
                "implementation"(libs.findLibrary("kotlinx-serialization-json").get())

                "debugImplementation"(project(":core:preview"))

                "testImplementation"(project(":core:testing"))
            }
        }
    }
}
