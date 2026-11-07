plugins {
    kotlin("jvm") version "2.4.21"
    application
}

// The app's own tool schema and chat template, compiled in rather than copied.
val appService = "../../app/src/main/java/com/pennywiseai/tracker/data/service"
kotlin.sourceSets["main"].kotlin.apply {
    srcDir(appService)
    include("Eval.kt", "Perf.kt", "PennyWiseTools.kt", "QwenChatTemplate.kt")
}

dependencies {
    implementation("com.google.ai.edge.litertlm:litertlm-jvm:0.19.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("com.google.code.gson:gson:2.14.0")
}

application {
    mainClass.set("EvalKt")
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED", "-Xmx2g")
}
