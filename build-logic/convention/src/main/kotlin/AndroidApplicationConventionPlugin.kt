import com.android.build.api.dsl.ApplicationExtension
import com.reelcounter.buildlogic.ProjectConfig
import com.reelcounter.buildlogic.configureKotlinAndroid
import com.reelcounter.buildlogic.configureLint
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.android.application")

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = ProjectConfig.TARGET_SDK
                testOptions.animationsDisabled = true
                lint.configureLint()
                // One lint report for the whole app, including every module it depends on.
                lint.checkDependencies = true
            }
        }
    }
}
