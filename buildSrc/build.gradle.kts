plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

// No dependency on architectury-loom here: buildSrc can't hold any copy of a plugin the root
// project also applies for real (see README.md). Shadow is different -- nothing else applies it,
// so a normal dependency is safe. Version pinned per this project's Gradle 9.4.1 requirement.
dependencies {
    implementation("com.gradleup.shadow:com.gradleup.shadow.gradle.plugin:9.3.2")
}
