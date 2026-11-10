import org.gradle.api.tasks.PathSensitivity
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.dependency.analysis)
}

android {
    namespace = "com.pennywiseai.tracker"
    compileSdk = 37

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.pennywiseai.tracker"
        minSdk = 26
        targetSdk = 36
        versionCode = 109
        versionName = "2.25.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Load RSA public key from local.properties
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            val localProperties = Properties()
            localProperties.load(localPropertiesFile.inputStream())

            val rsaPublicKey = localProperties.getProperty("RSA_PUBLIC_KEY", "")
            buildConfigField("String", "RSA_PUBLIC_KEY", "\"$rsaPublicKey\"")
            // Cloudflare Worker that frees a license key's activation so it
            // can move to a new phone. Empty = "Move to this device" hidden.
            val licenseMoveUrl = localProperties.getProperty("LICENSE_MOVE_URL", "")
            buildConfigField("String", "LICENSE_MOVE_URL", "\"$licenseMoveUrl\"")
        } else {
            // Fallback empty key for CI/CD builds
            buildConfigField("String", "RSA_PUBLIC_KEY", "\"\"")
            buildConfigField("String", "LICENSE_MOVE_URL", "\"\"")
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
            // Type-safe flavor check for code that adapts to F-Droid (everything
            // unlocked, tip-jar instead of Pro). Beats matching BuildConfig.FLAVOR
            // against the "fdroid" string literal, which a typo would silently break.
            buildConfigField("boolean", "IS_FDROID_BUILD", "true")
        }
        create("standard") {
            dimension = "version"
            isDefault = true
            // Standard flavor includes all architectures (including x86 for emulators)
            buildConfigField("boolean", "IS_FDROID_BUILD", "false")
        }
    }

    splits {
        abi {
            // Disable splits for F-Droid builds and bundle builds
            //noinspection WrongGradleMethod
            val runTasks = gradle.startParameter.taskNames.map { it.lowercase() }
            //noinspection WrongGradleMethod
            val isBundleBuild = runTasks.any { it.contains("bundle") }   // e.g., :app:bundleRelease
            //noinspection WrongGradleMethod
            val isFdroidBuild = runTasks.any { it.contains("fdroid") }

            isEnable = !(isBundleBuild || isFdroidBuild)

            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // en-XA / ar-XB pseudo-locales: untranslated (hardcoded) text stands
            // out as plain English, and RTL layout bugs show up without a translation.
            isPseudoLocalesEnabled = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Only apply signing config to standard flavor
            for (flavor in productFlavors) {
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
    // Lists every values-<lang>/ folder as a supported locale, so Android 13+
    // shows PennyWise under System settings → App languages.
    androidResources {
        generateLocaleConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
                "META-INF/NOTICE.md",
            )
        }
    }
    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // Allow JVM unit tests to exercise code that touches android.util.Log
            // and other Android framework stubs without Robolectric.
            isReturnDefaultValues = true
        }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // M3 Expressive (motionScheme, button shapes, LoadingIndicator, emphasized
        // type) is still @Experimental; the whole app is built on it.
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
    }
}

// A single profile for both flavors. Generation runs only when asked for
// (docs/performance.md), never as part of assemble.
baselineProfile {
    mergeIntoMain = true
}

// The plugin's two profileable variants (benchmarkRelease measures, nonMinifiedRelease
// generates) install beside any real build, and they alone carry the benchmark seed.
// release has no isProfileable and no src/benchmarkSeed.
val benchmarkBuildTypes = listOf("benchmarkRelease", "nonMinifiedRelease")
android.sourceSets {
    benchmarkBuildTypes.forEach { maybeCreate(it).kotlin.directories.add("src/benchmarkSeed/java") }
}
androidComponents.onVariants { variant ->
    if (variant.buildType in benchmarkBuildTypes) {
        variant.applicationId.set("com.pennywiseai.tracker.benchmark")
        variant.sources.manifests.addStaticManifestFile("src/benchmarkSeed/AndroidManifest.xml")
    }
}

// Configure Room schema export
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Copy changelog from fastlane to generated assets for What's New dialog
val generatedAssetsDir = layout.buildDirectory.dir("generated/assets/changelog")

tasks.register<Copy>("copyChangelog") {
    val versionCode = android.defaultConfig.versionCode
    val changelogDir = rootProject.file("fastlane/metadata/android/en-US/changelogs")
    val changelogFile = file("$changelogDir/$versionCode.txt")
    val defaultFile = file("$changelogDir/default.txt")

    from(if (changelogFile.exists()) changelogFile else defaultFile)
    into(generatedAssetsDir)
    rename { "whats_new.txt" }
}

android.sourceSets["main"].assets.directories.add(generatedAssetsDir.get().asFile.path)

tasks.matching { it.name.startsWith("merge") && it.name.contains("Assets") }.configureEach {
    dependsOn("copyChangelog")
}

// Dependency Analysis (buildHealth) reads the asset sources too.
tasks.matching { it.name.startsWith("explodeAssetSource") }.configureEach {
    dependsOn("copyChangelog")
}

// Lint tasks also need to depend on copyChangelog since they analyze generated assets
tasks.matching { it.name.contains("lint") || it.name.contains("Lint") }.configureEach {
    dependsOn("copyChangelog")
}

dependencies {
    // Local modules
    implementation(project(":parser-core"))
    implementation(project(":shared"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:exchangerates"))
    baselineProfile(project(":baselineprofile"))

    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Material Icons Extended
    implementation(libs.androidx.compose.material.icons.extended)

    // Splash Screen API
    implementation(libs.androidx.core.splashscreen)

    // Lifecycle and ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Navigation
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    // Kotlin Serialization
    implementation(libs.kotlinx.serialization.json)

    // Ktor for HTTP requests
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    // AI access: WebSocket line to the relay (ADR 0002). The WebSockets plugin ships
    // in ktor-client-core; ktor-client-websockets is an empty artifact.
    implementation(libs.ktor.client.okhttp) // platform TLS; CIO rejects Cloudflare certs

    // DataStore
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)

    // Biometric Authentication
    implementation(libs.androidx.biometric)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.sqlite.framework) // AndroidSQLiteDriver (system SQLite)
    ksp(libs.androidx.room.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Hilt WorkManager integration
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Glance Widget
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // LiteRT-LM >= 0.14 calls SendChannel.close$default as an interface static; only
    // coroutines >= 1.11.0 provides it (see the litertlm note in libs.versions.toml, #599).
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    // LiteRT-LM for on-device LLM inference
    implementation(libs.litertlm.android)

    // Google Play In-App Updates (only for standard flavor)
    "standardImplementation"(libs.app.update)

    // Google Play In-App Reviews (only for standard flavor)
    "standardImplementation"(libs.review)

    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.room.testing)
    // Architecture guardrails (ArchitectureTest): parses app sources, test-only.
    testImplementation(libs.konsist)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    debugImplementation(libs.leakcanary.android)

    // Coil for image loading
    implementation(libs.coil.compose)
    // On-device QR for split-bill UPI requests (#346): pure-Java encoder, no Play services, no network.
    implementation(libs.zxing.core)

    // Haze blur effects
    implementation(libs.haze)
    implementation(libs.haze.blur)

    // Compose Charts
    implementation(libs.compose.charts)

    // OpenCSV for CSV export
    implementation(libs.opencsv)

    // PDFBox Android for PDF statement parsing
    implementation(libs.pdfbox.android)

    // Google Play Billing — STANDARD FLAVOR ONLY. F-Droid forbids the
    // proprietary library; its build keeps Pro features unlocked via the
    // FdroidBillingGateway stub and does not need the dep.
    "standardImplementation"(libs.billing.ktx)

    testImplementation(kotlin("test"))
}

android.sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")
// androidx.test asks for concurrent-futures 1.2.0, but AGP holds test classpaths to
// the app's runtime version (1.1.0, via Glance), so no androidTest resolves without
// this. Test-only: the app stays on 1.1.0.
// ponytail: hard-coded versions, drop this once Glance moves the app to 1.2.0.
configurations.matching { "AndroidTest" in it.name }.configureEach {
    resolutionStrategy.force(
        "androidx.concurrent:concurrent-futures:1.1.0",
        "androidx.concurrent:concurrent-futures-ktx:1.1.0",
    )
}
// StringPlaceholderParityTest reads res/ straight off disk, which Gradle can't
// infer. Without this the test task stays UP-TO-DATE when only translations
// change — i.e. for exactly the pull requests it exists to check.
tasks.withType<Test>().configureEach {
    inputs.dir(layout.projectDirectory.dir("src/main/res"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("androidResourcesForParityTest")
    // Both tests also read the feature modules (feature/*/src).
    inputs.files(rootProject.fileTree("feature") { include("*/src/**/res/**/*.xml", "*/src/*/java/**/*.kt") })
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("featureSourcesForArchitectureAndParityTests")
    // Same for ArchitectureTest, which parses the Kotlin sources' imports. An
    // import edit doesn't always change the bytecode (swap one import line for
    // another), and then the compiled classes alone wouldn't re-run the test.
    inputs.files(fileTree("src") { include("*/java/**/*.kt") })
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("kotlinSourcesForArchitectureTest")
}
