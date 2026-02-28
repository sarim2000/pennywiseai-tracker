plugins {
    kotlin("jvm") version "2.3.0" apply false
    kotlin("multiplatform") version "2.3.0" apply false
    id("io.ktor.plugin") version "3.2.3" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.0" apply false
}

subprojects {
    group = "com.example"
    version = "0.0.1"
}
