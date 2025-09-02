import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"
}

android {
    namespace = "com.pennywiseai.tracker"
    compileSdk = 36
    
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.pennywiseai.tracker"
        minSdk = 30
        targetSdk = 36
        versionCode = 48
        versionName = "2.15.13"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Load RSA public key from local.properties
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            val localProperties = Properties()
            localProperties.load(localPropertiesFile.inputStream())
            
            val rsaPublicKey = localProperties.getProperty("RSA_PUBLIC_KEY", "")
            buildConfigField("String", "RSA_PUBLIC_KEY", "\"$rsaPublicKey\"")
        } else {
            // Fallback empty key for CI/CD builds
            buildConfigField("String", "RSA_PUBLIC_KEY", "\"\"")
        }
    }

    signingConfigs {
        // Only create signing config for non-F-Droid builds
        if (!gradle.startParameter.taskNames.any { it.contains("fdroid", ignoreCase = true) }) {
            val localPropertiesFile = rootProject.file("local.properties")
            if (localPropertiesFile.exists()) {
                create("release") {
                    val localProperties = Properties()
                    localProperties.load(localPropertiesFile.inputStream())
                    
                    val keystorePath = localProperties.getProperty("RELEASE_STORE_FILE", "")
                    if (keystorePath.isNotEmpty()) {
                        storeFile = file(keystorePath)
                        storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD", "")
                        keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS", "")
                        keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD", "")
                    }
                }
            }
        }
    }
    
    flavorDimensions += "version"
    productFlavors {
        create("fdroid") {
            dimension = "version"
            // F-Droid builds will use their own signing
            // Only include ARM architectures for F-Droid (no x86 emulator support)
            ndk {
                abiFilters += setOf("arm64-v8a", "armeabi-v7a")
            }
        }
        create("standard") {
            dimension = "version"
            isDefault = true
            // Standard flavor includes all architectures (including x86 for emulators)
        }
    }
    
    // Enable APK splits for smaller APKs per architecture (only for APK builds, not bundles)
    splits {
        abi {
            // Disable splits for F-Droid builds and Bundle builds
            // Bundles don't support APK splits, they handle multi-architecture differently
            isEnable = !gradle.startParameter.taskNames.any { 
                it.contains("Fdroid") || 
                it.contains("Bundle") || 
                it.contains("bundle")
            }
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true  // Also generate a universal APK containing all ABIs
        }
    }
    
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            // Only apply signing config to standard flavor
            productFlavors.forEach { flavor ->
                if (flavor.name == "standard") {
                    // Check if release signing config exists
                    val releaseSigningConfig = signingConfigs.findByName("release")
                    // Only use release signing if keystore is configured
                    if (releaseSigningConfig != null && releaseSigningConfig.storeFile != null) {
                        signingConfig = releaseSigningConfig
                    }
                }
            }
            
            // Include debug symbols for native crashes
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

// Configure Room schema export
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    
    // Material Icons Extended
    implementation("androidx.compose.material:material-icons-extended")
    
    // Color Picker for Compose
    implementation("com.github.skydoves:colorpicker-compose:1.1.2")
    
    // Splash Screen API
    implementation("androidx.core:core-splashscreen:1.0.1")
    
    // Lifecycle and ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-process:2.9.2")

    // Navigation
    val navVersion = "2.9.3"
    implementation("androidx.navigation:navigation-compose:$navVersion")
    
    // Kotlin Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    
    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    
    // Hilt
    implementation("com.google.dagger:hilt-android:2.57")
    ksp("com.google.dagger:hilt-android-compiler:2.57")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    
    // Room
    val roomVersion = "2.7.2"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    
    // WorkManager
    val workVersion = "2.10.3"
    implementation("androidx.work:work-runtime-ktx:$workVersion")
    
    // Hilt WorkManager integration
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")
    
    // MediaPipe for LLM inference
    implementation("com.google.mediapipe:tasks-genai:0.10.25")
    
    // Google Play In-App Updates (only for standard flavor)
    "standardImplementation"("com.google.android.play:app-update:2.1.0")
    "standardImplementation"("com.google.android.play:app-update-ktx:2.1.0")
    
    testImplementation(libs.junit)
    testImplementation("androidx.room:room-testing:$roomVersion")
    androidTestImplementation("androidx.work:work-testing:$workVersion")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    
    // Markdown support
    implementation("org.jetbrains:markdown:0.7.3")
    
    // OpenCSV for CSV export
    implementation("com.opencsv:opencsv:5.12.0")
}
