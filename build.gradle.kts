plugins {
    id("net.minecraftforge.gradle") version "6.0.46"
    java
}

val modId: String by project
val modGroup: String by project
val modVersion: String by project
val minecraftVersion: String by project
val forgeVersion: String by project

group = modGroup
version = modVersion

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

minecraft {
    mappings("official", minecraftVersion)
    copyIdeResources.set(true)
    runs {
        create("server") {
            workingDirectory = "run"
            property("forge.logging.markers", "REGISTRIES")
            property("forge.logging.console.level", "debug")
        }
    }
}

repositories {
    mavenCentral()
    maven("https://maven.minecraftforge.net")
    maven("https://maven.parchmentmc.org") // For mappings if needed
}

dependencies {
    "minecraft"("net.minecraftforge:forge:${minecraftVersion}-${forgeVersion}")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}