plugins {
    java
    id("net.minecraftforge.gradle") version "6.0.46"
}

// Ensure :core is configured before we reference its jar task below.
evaluationDependsOn(":core")

val minecraftVersion: String by project
val forgeVersion: String by project

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
    maven("https://maven.minecraftforge.net")
}

minecraft {
    mappings("official", minecraftVersion)
    copyIdeResources.set(true)
}

dependencies {
    "minecraft"("net.minecraftforge:forge:${minecraftVersion}-${forgeVersion}")
    implementation(project(":core"))
}

// Bundle the loader-agnostic core classes into the mod jar. core has no
// Minecraft references, so it passes through ForgeGradle's reobfuscation
// untouched — no shadow plugin needed.
val coreJar = project(":core").tasks.named<Jar>("jar")
tasks.named<Jar>("jar") {
    dependsOn(coreJar)
    from(provider { zipTree(coreJar.get().archiveFile.get().asFile) })
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
