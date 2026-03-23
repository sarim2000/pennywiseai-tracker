plugins {
    kotlin("multiplatform")
    `maven-publish`
}

group = "com.pennywiseai"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(25)
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
        }
        val jvmMain by getting {
            kotlin.srcDir("src/main/kotlin")
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

// Keep compatibility with existing CI/scripts that invoke :parser-core:test
tasks.register("test") {
    group = "verification"
    dependsOn("jvmTest")
}