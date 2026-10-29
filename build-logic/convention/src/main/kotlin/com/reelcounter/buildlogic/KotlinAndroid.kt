package com.reelcounter.buildlogic

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.Lint
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

internal object ProjectConfig {
    /** Highest platform available in the local Android SDK installation. */
    const val COMPILE_SDK = 37

    /** Google Play's current minimum target level. Raising it is a deliberate, tested change. */
    const val TARGET_SDK = 36

    const val MIN_SDK = 26
}

internal fun Project.configureKotlinAndroid(commonExtension: CommonExtension) {
    commonExtension.apply {
        compileSdk = ProjectConfig.COMPILE_SDK

        defaultConfig.apply {
            minSdk = ProjectConfig.MIN_SDK
        }

        compileOptions.apply {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }

    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions.configureCommon(this@configureKotlinAndroid)
    }

    addUnitTestDependencies()
}

internal fun Project.configureKotlinJvm() {
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    extensions.configure<KotlinJvmProjectExtension> {
        compilerOptions.configureCommon(this@configureKotlinJvm)
    }

    addUnitTestDependencies()
}

internal fun Lint.configureLint() {
    abortOnError = true
    // Lint (32.4.1) intermittently crashes inside its Kotlin analysis when reading unit-test
    // sources compiled with Kotlin 2.4 ("bug in lint", FIR resolve error). Production code is
    // still fully linted. Re-enable after the next lint/AGP update.
    ignoreTestSources = true
    // Dependency versions are pinned deliberately in the version catalog and bumped as a reviewed change.
    disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
}

private fun KotlinJvmCompilerOptions.configureCommon(project: Project) {
    jvmTarget.set(JvmTarget.JVM_17)
    allWarningsAsErrors.set(
        project.providers.gradleProperty("warningsAsErrors").map(String::toBoolean).orElse(false),
    )
}

private fun Project.addUnitTestDependencies() {
    dependencies {
        // The JUnit flavor explicitly: AGP's built-in Kotlin doesn't infer it the way the Kotlin JVM plugin does.
        "testImplementation"(libs.findLibrary("kotlin-test-junit").get())
        "testImplementation"(libs.findLibrary("junit").get())
    }
}
