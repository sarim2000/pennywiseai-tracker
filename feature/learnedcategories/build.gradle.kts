// The learned-categories screen. Depends on :core:designsystem only; :app supplies the
// data (LearnedCategoriesData, bound in :app's di/) and the shared scaffold, grouped
// row and category labels (navigation/LearnedCategoriesEntry.kt).
plugins {
    id("pennywise.android.feature")
    alias(libs.plugins.dependency.analysis)
}

android {
    namespace = "com.pennywiseai.feature.learnedcategories"
}

dependencies {
    implementation(project(":core:designsystem"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.core)

    // pennywise.android.feature adds the Hilt compiler to ksp.
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
