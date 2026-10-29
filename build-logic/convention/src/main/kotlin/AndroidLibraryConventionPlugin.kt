import com.android.build.api.dsl.LibraryExtension
import com.reelcounter.buildlogic.ProjectConfig
import com.reelcounter.buildlogic.configureKotlinAndroid
import com.reelcounter.buildlogic.configureLint
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.android.library")

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                testOptions.targetSdk = ProjectConfig.TARGET_SDK
                testOptions.animationsDisabled = true
                lint.targetSdk = ProjectConfig.TARGET_SDK
                lint.configureLint()
                // Resources must carry the module name, so ":core:ui" uses "core_ui_" and
                // ":feature:today" uses "feature_today_". This avoids clashes in the merged app.
                resourcePrefix = path.split("""\W""".toRegex())
                    .drop(1)
                    .distinct()
                    .joinToString(separator = "_")
                    .lowercase() + "_"
            }
        }
    }
}
