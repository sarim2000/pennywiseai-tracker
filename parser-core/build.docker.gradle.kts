plugins {
    kotlin("jvm") version "2.1.0"
    `maven-publish`
}

group = "com.pennywiseai"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

sourceSets {
    main {
        kotlin {
            srcDir("src/commonMain/kotlin")
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = group.toString()
            artifactId = "parser-core"
            version = version.toString()
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Configure JUnit testing
tasks.test {
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

    // Optional: Run tests in parallel for faster execution
    maxParallelForks = maxOf(1, Runtime.getRuntime().availableProcessors() / 2)
}
