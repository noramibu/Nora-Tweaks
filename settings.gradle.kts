pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT" apply false
}

rootProject.name = "nora-tweaks"
