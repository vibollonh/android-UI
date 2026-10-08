plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// Maven coordinates of the libraries: com.example.baseui:<module>:<version>.
// Used by `publishToMavenLocal` and by composite builds (`includeBuild`) in other projects.
subprojects {
    group = "com.example.baseui"
    version = "0.1.0"
}
