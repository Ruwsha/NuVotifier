import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    `kotlin-dsl`
    kotlin("jvm") version embeddedKotlinVersion
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven {
        name = "sponge"
        url = uri("https://repo.spongepowered.org/repository/maven-public/")
    }
    maven {
        name = "fabric"
        url = uri("https://maven.fabricmc.net/")
    }
    maven {
        name = "Nucleoid"
        url = uri("https://maven.nucleoid.xyz")
    }
}

dependencies {
    implementation(gradleApi())
    implementation("org.ajoberstar.grgit:grgit-gradle:5.2.2")
    implementation("com.github.johnrengelman:shadow:8.1.1")
    implementation("org.jfrog.buildinfo:build-info-extractor-gradle:5.2.3")
    implementation("org.spongepowered:spongegradle-plugin-development:2.2.0")
    implementation("net.fabricmc:fabric-loom:1.9-SNAPSHOT")
}
