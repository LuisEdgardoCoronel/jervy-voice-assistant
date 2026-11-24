import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Módulo Kotlin/JVM puro: sin dependencia del SDK de Android (ver Arquitectura en CLAUDE.md).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
}

ktlint {
    version.set(libs.versions.ktlint.get())
}

java {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(libs.versions.java.get()))
    }
}

dependencies {
    testImplementation(libs.junit)
}
