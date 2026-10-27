import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.genowhirl.breathe"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.genowhirl.breathe"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    // The Google Play upload key, from keystore.properties (local, never committed) or the
    // BREATHE_UPLOAD_* environment variables (CI). Without it, release builds stay unsigned.
    val uploadKey = Properties().apply {
        rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        System.getenv("BREATHE_UPLOAD_STORE_FILE")?.let { put("storeFile", it) }
        System.getenv("BREATHE_UPLOAD_STORE_PASSWORD")?.let { put("storePassword", it) }
        System.getenv("BREATHE_UPLOAD_KEY_ALIAS")?.let { put("keyAlias", it) }
        System.getenv("BREATHE_UPLOAD_KEY_PASSWORD")?.let { put("keyPassword", it) }
    }

    signingConfigs {
        // A committed debug key, so every CI build can be installed over the previous one.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (uploadKey.getProperty("storeFile") != null) {
            create("upload") {
                storeFile = rootProject.file(uploadKey.getProperty("storeFile"))
                storePassword = uploadKey.getProperty("storePassword")
                keyAlias = uploadKey.getProperty("keyAlias")
                keyPassword = uploadKey.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Unsigned unless an upload key is configured: F-Droid signs with its own key, and
            // Google Play re-signs uploads with the app signing key it holds.
            signingConfig = signingConfigs.findByName("upload")
        }
    }

    // F-Droid rejects the dependency metadata block that Google encrypts into APKs by default.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                // Store screenshots are rendered on demand, into the fastlane listing:
                // ./gradlew testDebugUnitTest -Pscreenshots --tests '*StoreScreenshots'
                if (project.hasProperty("screenshots")) {
                    test.systemProperty(
                        "breathe.screenshotDir",
                        rootProject.file("fastlane/metadata/android/en-US/images/phoneScreenshots").path,
                    )
                } else {
                    test.exclude("**/StoreScreenshots*")
                }
            }
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
