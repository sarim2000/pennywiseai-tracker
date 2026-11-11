plugins {
    `kotlin-dsl`
}

// The Gradle plugins the convention plugins apply, at the catalog's versions. They
// resolve through each plugin's marker artifact, so the catalog needs no extra entries.
fun plugin(plugin: Provider<PluginDependency>) =
    plugin.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }

dependencies {
    implementation(plugin(libs.plugins.android.library))
    // AGP's built-in Kotlin: the kotlin { compilerOptions } block's types.
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    implementation(plugin(libs.plugins.kotlin.compose))
    implementation(plugin(libs.plugins.ksp))
    implementation(plugin(libs.plugins.hilt.android))
}
