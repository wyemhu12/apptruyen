plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("androidx.room3")
    id("org.jlleitschuh.gradle.ktlint")
}

import java.util.Properties
import java.io.FileInputStream

// Load signing properties
val keystoreProperties = Properties().apply {
    val propsFile = rootProject.file("keystore.properties")
    if (propsFile.exists()) load(FileInputStream(propsFile))
}

android {
    namespace = "com.personal.apptruyen"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.personal.apptruyen"
        minSdk = 26
        targetSdk = 36
        versionCode = 52
        versionName = "3.0.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        getByName("debug") {
            // Uses default debug keystore
        }
        create("release") {
            storeFile = file(keystoreProperties.getProperty("storeFile", "../keystore/release.jks"))
            storePassword = keystoreProperties.getProperty("storePassword", "")
            keyAlias = keystoreProperties.getProperty("keyAlias", "apptruyen")
            keyPassword = keystoreProperties.getProperty("keyPassword", "")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    // APK renaming is handled via androidComponents API below
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = true
        htmlReport = true
        htmlOutput = layout.buildDirectory.file("reports/lint/lint-report.html").get().asFile
    }
    sourceSets {
        getByName("androidTest") {
            assets.srcDirs(files("$projectDir/schemas"))
        }
    }
}

androidComponents {
    finalizeDsl { ext ->
        val vName = ext.defaultConfig.versionName?.replace(".", "") ?: "0"
        project.base.archivesName.set("apptruyenv${vName}")
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2026.05.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Core
    implementation("androidx.core:core:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.9.8")

    // Image loading (Coil 3)
    implementation("io.coil-kt.coil3:coil-compose:3.4.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.4.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-compiler:2.60.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")

    // Room 3
    implementation("androidx.room3:room3-runtime:3.0.3")
    ksp("androidx.room3:room3-compiler:3.0.3")
    implementation("androidx.sqlite:sqlite-bundled:2.7.1")

    // Network + Scraping
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("org.jsoup:jsoup:1.22.2")

    // DataStore (for settings)
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // Splash Screen
    implementation("androidx.core:core-splashscreen:1.2.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Immutable collections (Compose stability)
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.8")

    // JSON serialization (backup/restore)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // Baseline Profiles
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    // Media3 (for MediaSessionService + notification controls)
    implementation("androidx.media3:media3-session:1.11.1")

    // Structured Logging (harness engineering — observability)
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("com.squareup.okhttp3:mockwebserver3:5.3.2")
    testImplementation("org.json:json:20260522") // Android org.json stub for JVM tests
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.room3:room3-testing:3.0.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Memory leak detection (debug only)
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

ktlint {
    android.set(true)
    outputToConsole.set(true)
    ignoreFailures.set(false)
    enableExperimentalRules.set(false)
    filter {
        exclude("**/generated/**")
        exclude("**/*.kts")
    }
}
