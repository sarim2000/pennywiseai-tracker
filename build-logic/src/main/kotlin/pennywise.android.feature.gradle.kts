// A feature module: a Compose library whose ViewModels Hilt injects, with the Hilt
// compiler wired into KSP. The module still declares the Hilt runtime it compiles
// against, next to its other dependencies.
plugins {
    id("pennywise.android.compose-library")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    "ksp"(libs.findLibrary("hilt-android-compiler").get())
}
