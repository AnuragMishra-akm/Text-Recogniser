plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.textrecogniser"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.textrecogniser"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

        // To recognize Latin script
    implementation ("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")

        // To recognize Chinese script
    implementation ("com.google.android.gms:play-services-mlkit-text-recognition-chinese:16.0.1")

        // To recognize Devanagari script
    implementation ("com.google.android.gms:play-services-mlkit-text-recognition-devanagari:16.0.1")

        // To recognize Japanese script
    implementation ("com.google.android.gms:play-services-mlkit-text-recognition-japanese:16.0.1")

        // To recognize Korean script
    implementation ("com.google.android.gms:play-services-mlkit-text-recognition-korean:16.0.1")

    val cameraxVersion = "1.3.0-rc01"

    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-video:$cameraxVersion")

    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("androidx.camera:camera-extensions:$cameraxVersion")

    implementation("androidx.compose.material:material-icons-extended:1.5.1")
    implementation("com.google.android.gms:play-services-mlkit-language-id:17.0.0")


}