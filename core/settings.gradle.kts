// Standalone build: compiles and tests without the Android SDK (ADR 0002).
pluginManagement {
    repositories { gradlePluginPortal(); mavenCentral() }
}
dependencyResolutionManagement {
    repositories { mavenCentral() }
}
rootProject.name = "guideglass-core"
