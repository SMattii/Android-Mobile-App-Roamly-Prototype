import org.gradle.kotlin.dsl.implementation
import java.util.Properties
import kotlin.io.use

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

val localProps = Properties()
val localPropertiesFile = rootProject.file("local.properties")

if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().buffered().use { stream ->
        localProps.load(stream)
    }
}

android {
    namespace = "com.example.roamly"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.roamly"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"${localProps["SUPABASE_URL"]}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${localProps["SUPABASE_KEY"]}\"")
        buildConfigField("String", "MAPBOX_TOKEN", "\"${localProps["MAPBOX_PUBLIC_TOKEN"]}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
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
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(libs.androidx.core.splashscreen)

    // Gestirà automaticamente le versioni per i moduli Supabase
    implementation(platform("io.github.jan-tennert.supabase:bom:3.1.4"))

    // Moduli Supabase
    implementation ("io.github.jan-tennert.supabase:auth-kt")
    implementation ("io.github.jan-tennert.supabase:postgrest-kt")
    implementation ("io.github.jan-tennert.supabase:storage-kt")

    // Client HTTP esplicito
    implementation("io.ktor:ktor-client-okhttp:3.2.0")

    // Serialization JSON (necessaria per Supabase)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Lifecycle per lifecycleScope
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")

    implementation("com.google.code.gson:gson:2.10.1")

    implementation("com.mapbox.maps:android:11.13.1")

    implementation ("com.google.android.gms:play-services-location:21.0.1")
}