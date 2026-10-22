plugins {
    kotlin("multiplatform")
    `maven-publish`
}

group = "com.pennywiseai"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)
    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
        }
        val jvmMain by getting {
            kotlin.srcDir("src/main/kotlin")
        }
        // The same parser sources compile for iOS. They import java.math /
        // java.text / java.util, which src/iosMain stands in for on iOS — so a
        // parser change needs no iOS-specific edit (see BigDecimal.kt there).
        iosMain {
            kotlin.srcDir("src/main/kotlin")
        }
        iosTest.dependencies {
            implementation(kotlin("test"))
        }
        val jvmTest by getting {
            kotlin.srcDir("src/test/kotlin")
            resources.srcDir("src/test/resources")
            dependencies {
                implementation("org.junit.jupiter:junit-jupiter:5.10.0")
                runtimeOnly("org.junit.platform:junit-platform-launcher")
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenKotlin") {
            from(components["kotlin"])
            groupId = group.toString()
            artifactId = "parser-core"
            version = version.toString()
        }
    }
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")

        // Show detailed information for each test
        showExceptions = true
        showCauses = true
        showStackTraces = true

        // Show standard output from println statements
        showStandardStreams = true

        // Display test results in a more readable format
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }

    maxParallelForks = maxOf(1, Runtime.getRuntime().availableProcessors() / 2)
}

// IosParityTest reads the JVM-generated golden file from the host filesystem.
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>().configureEach {
    environment("SIMCTL_CHILD_PARITY_FILE", file("src/test/resources/ios-parity.tsv").absolutePath)
}

// Keep compatibility with existing CI/scripts that invoke :parser-core:test
tasks.register("test") {
    group = "verification"
    dependsOn("jvmTest")
}