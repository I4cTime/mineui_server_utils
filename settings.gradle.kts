pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.minecraftforge.net")
    }
}

plugins {
    // Lets Gradle auto-provision a required JDK (toolchains + daemon JVM) when
    // one is not already installed locally.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "mineui-server-utils"

include("core", "forge", "paper")
