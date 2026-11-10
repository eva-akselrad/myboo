import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.myboo.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.myboo.app"
        minSdk = 26
        targetSdk = 35
        // Release version comes from -Pmyboo.version=X.Y.Z (the release script); versionCode derives from it.
        val ver = (project.findProperty("myboo.version") as String?) ?: "0.1.0"
        val (maj, min, pat) = ver.split(".").map { it.toInt() }
        versionCode = maj * 10000 + min * 100 + pat
        versionName = ver
        val api = (project.findProperty("myboo.apiUrl") as String?) ?: "https://myboo.evaakselrad.com"
        buildConfigField("String", "API_BASE_URL", "\"$api\"")
    }

    // Release signing: keystore lives outside the repo (see scripts/release.sh). Updates only install over the
    // same signing key, so never lose it.
    val ks = file(System.getProperty("user.home") + "/.myboo/keystore.properties")
    if (ks.exists()) {
        val p = Properties().apply { ks.inputStream().use { load(it) } }
        signingConfigs.create("release") {
            storeFile = file(p.getProperty("storeFile"))
            storePassword = p.getProperty("storePassword")
            keyAlias = p.getProperty("keyAlias")
            keyPassword = p.getProperty("keyPassword")
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("release")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.osmdroid:osmdroid-android:6.1.20") // OpenStreetMap, no API key

    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-messaging")
}
