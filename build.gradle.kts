plugins {
    id("com.android.application") version "9.1.1" apply false
    kotlin("jvm") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}

// Keep generated files out of OneDrive to avoid sync locks and long cache paths.
allprojects {
    layout.buildDirectory.set(file("${gradle.gradleUserHomeDir}/outputs/SimWarning/${project.name}"))
}
