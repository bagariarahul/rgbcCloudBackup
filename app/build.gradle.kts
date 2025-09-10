@Suppress("DSL_SCOPE_VIOLATION") // if using Gradle < 8.1
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)     // 💡 **Compose compiler plugin (Kotlin 2.0+)**
    alias(libs.plugins.kotlin.parcelize)   // optional, if using `@Parcelize`
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.rgbc.cloudBackup"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rgbc.cloudBackup"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        vectorDrawables.useSupportLibrary = true
        testInstrumentationRunner = "com.rgbc.cloudBackup.HiltTestRunner"
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging { resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }}
}

kapt {
    correctErrorTypes = true
    arguments { arg("room.schemaLocation", "$projectDir/schemas") }
}

dependencies {
    // Core & Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose BOM + UI libraries
    implementation(platform(libs.androidx.compose.bom))              // Maps to Compose 1.6.x etc :contentReference[oaicite:8]{index=8}
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.google.material)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Navigation & Hilt in Compose
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    // Dependency Injection
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // Room & SQLCipher
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.sqlcipher)
    implementation(libs.androidx.sqlite)
    kapt(libs.androidx.room.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // Security
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Logging & Debug
    implementation(libs.timber)
    debugImplementation(libs.leakcanary.android)

    // Unit Test dependencies
    testImplementation(libs.junit)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.hilt.android.testing)         // Hilt for Robolectric/UI unit tests :contentReference[oaicite:9]{index=9}
    kaptTest(libs.hilt.compiler)

    // Android Instrumented/UI Tests
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)       // Compose UI tests :contentReference[oaicite:10]{index=10}
    debugImplementation(libs.androidx.ui.test.manifest)        // debug-only manifest provider :contentReference[oaicite:11]{index=11}

    androidTestImplementation(libs.androidx.test.runner)                   // Needed for Hilt runner
    androidTestImplementation(libs.hilt.android.testing)                 // Hilt injection in Android tests :contentReference[oaicite:12]{index=12}
    kaptAndroidTest(libs.hilt.compiler)

    // Remember: Espresso Core belongs in androidTest, NOT testImplementation
    androidTestImplementation(libs.espresso.core)

    implementation(libs.dagger.core)
    kapt(libs.dagger.compiler)

    // Unit tests
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.arch.core.testing)

    // Instrumented tests
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.arch.core.testing)
    // Unit test dependencies (these work)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)

    // ADD THESE for Android instrumented tests
    androidTestImplementation(libs.mockito.core)
    androidTestImplementation(libs.mockito.kotlin)
    implementation(libs.document.file)
    implementation(libs.accompanist.permissions)

    implementation(libs.androidx.foundation)



}

