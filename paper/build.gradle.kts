plugins {
    java
}

evaluationDependsOn(":core")

val paperApiVersion: String by project

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${paperApiVersion}")
    implementation(project(":core"))
}

// Bundle core into the plugin jar. core depends only on the JDK and the SLF4J
// facade (which Paper provides at runtime), so a plain copy is a complete jar.
val coreJar = project(":core").tasks.named<Jar>("jar")
tasks.named<Jar>("jar") {
    dependsOn(coreJar)
    from(provider { zipTree(coreJar.get().archiveFile.get().asFile) })
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
