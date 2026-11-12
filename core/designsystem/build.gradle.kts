// PennyWise's theme, design tokens and fonts. Depends on no project module.
// See docs/architecture.md for what belongs here.
plugins {
    id("pennywise.android.compose-library")
    alias(libs.plugins.dependency.analysis)
}

android {
    namespace = "com.pennywiseai.core.designsystem"
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    // ColorScheme, Typography and Shapes are part of this module's public API.
    api(libs.androidx.material3)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.core) // WindowCompat

    testImplementation(libs.junit)
}
