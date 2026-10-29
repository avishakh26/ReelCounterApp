plugins {
    alias(libs.plugins.reelcounter.android.application)
    alias(libs.plugins.reelcounter.android.application.compose)
    alias(libs.plugins.reelcounter.hilt)
}

android {
    namespace = "com.reelcounter.app"

    defaultConfig {
        applicationId = "com.reelcounter.app"
        versionCode = 1
        versionName = "0.1.0" // X.Y.Z: major.minor.patch

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Debug and release builds can be installed side by side.
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Release signing is configured in Phase 10; until then release builds are unsigned.
        }
    }

    packaging {
        resources {
            excludes.add("/META-INF/{AL2.0,LGPL2.1}")
        }
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.domain)
    implementation(projects.core.tracking)
    implementation(projects.core.ui)
    implementation(projects.core.work)
    implementation(projects.feature.auth)
    implementation(projects.feature.friends)
    implementation(projects.feature.insights)
    implementation(projects.feature.privacy)
    implementation(projects.feature.profile)
    implementation(projects.feature.settings)
    implementation(projects.feature.today)
    implementation(projects.feature.tracking)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3.navigationSuite)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.navigation.compose)
}

// `-PreelCounting=false` leaves out the reel-counting accessibility service. Play Protect blocks
// APKs installed from a file that declare one, so the APK given to testers is built this way.
if (providers.gradleProperty("reelCounting").orNull == "false") {
    androidComponents {
        onVariants { variant ->
            variant.sources.manifests.addStaticManifestFile("src/withoutCounting/AndroidManifest.xml")
        }
    }
}
