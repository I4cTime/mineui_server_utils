plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Logging facade only; the loader (Forge/Paper) provides the binding at runtime.
    compileOnly("org.slf4j:slf4j-api:2.0.18")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
