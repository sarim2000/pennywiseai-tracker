val h2_version: String by project
val koin_version: String by project
val kotlin_version: String by project
val kotlinx_browser_version: String by project
val kotlinx_html_version: String by project
val logback_version: String by project
val postgres_version: String by project
val exposed_version: String by project
val hikari_version: String by project

plugins {
    kotlin("jvm") version "2.3.0"
    id("io.ktor.plugin") version "3.2.3"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.0"
}

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

ktor {
    docker {
        jreVersion.set(JavaVersion.VERSION_21)
        localImageName.set("pennywise-ktor")
        imageTag.set("latest")
        portMappings.set(listOf(
            io.ktor.plugin.features.DockerPortMapping(
                8080,
                8080,
                io.ktor.plugin.features.DockerPortMappingProtocol.TCP
            )
        ))
    }
}

dependencies {
    // Core Ktor dependencies
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-config-yaml")

    // Content negotiation and serialization
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")

    // HTML templating
    implementation("io.ktor:ktor-server-html-builder")
    implementation("org.jetbrains.kotlinx:kotlinx-html:$kotlinx_html_version")

    // CORS support
    implementation("io.ktor:ktor-server-cors")

    // Database
    implementation("org.postgresql:postgresql:$postgres_version")
    implementation("com.h2database:h2:$h2_version")

    // Logging
    implementation("ch.qos.logback:logback-classic:$logback_version")

    // Parser core
    implementation("com.pennywiseai:parser-core:0.1.0-SNAPSHOT")

    // Testing
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}
