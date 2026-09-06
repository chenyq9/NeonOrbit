plugins {
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}

buildscript {
    dependencies {
        // AGP 9.x uses built-in Kotlin. Pinning the runtime KGP keeps the
        // Compose compiler plugin and Kotlin compiler on the same version.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    }
}
