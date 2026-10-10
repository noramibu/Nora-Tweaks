import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.file.DuplicatesStrategy

plugins {
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
    id("com.gradleup.shadow") version "9.0.0-beta12"
    id("maven-publish")
}

fun prop(name: String): String =
    project.findProperty(name)?.toString()
        ?: rootProject.findProperty(name)?.toString()
        ?: error("Missing property: $name")

val minecraftVersion = prop("minecraft_version")
val loaderVersion = prop("loader_version")
val fabricApiVersion = prop("fabric_api_version")
val meteorVersion = prop("meteor_version")
val baritoneVersion = prop("baritone_version")
val xppleCubiomesVersion = prop("xpple_cubiomes_version")
val modVersion = prop("mod_version")
val mavenGroup = prop("maven_group")
val archivesBaseName = prop("archives_base_name")

version = "$modVersion-$minecraftVersion"
group = mavenGroup

base {
    archivesName.set(archivesBaseName)
}

repositories {
    maven("https://maven.fabricmc.net/")
    maven("https://libraries.minecraft.net/")
    maven("https://maven.meteordev.org/releases")
    maven("https://maven.meteordev.org/snapshots")
    maven("https://maven.xpple.dev/maven2")
    maven("https://jitpack.io")
    maven("https://maven.bawnorton.com/releases")
    flatDir { dirs(rootProject.file("libs")) }
    mavenCentral()
    mavenLocal()
}

val shade by configurations.creating
configurations.named("implementation") {
    extendsFrom(shade)
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:$loaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    compileOnly("meteordevelopment:meteor-client:$meteorVersion-SNAPSHOT")
    compileOnly("meteordevelopment:orbit:0.2.4")
    compileOnly("meteordevelopment:baritone:$baritoneVersion-SNAPSHOT") {
        isTransitive = false
    }
    shade("com.github.bawnorton.mixinsquared:mixinsquared-fabric:0.2.0") {
        isTransitive = false
    }
    shade("dev.xpple:cubiomes:$xppleCubiomesVersion") {
        isTransitive = false
    }
}

tasks {
    processResources {
        val ver = project.version.toString()
        val mc = minecraftVersion
        inputs.property("version", ver)
        inputs.property("minecraft_version", mc)
        filteringCharset = "UTF-8"
        filesMatching("fabric.mod.json") {
            filter { line: String ->
                line
                    .replace("\${version}", ver)
                    .replace("\${minecraft_version}", mc)
            }
        }
    }

    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = 25
        options.compilerArgs.add("-Xlint:deprecation")
        options.compilerArgs.add("-Xlint:unchecked")
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
        withSourcesJar()
    }

    val shadowJar by getting(ShadowJar::class) {
        configurations = listOf(shade)
        archiveClassifier.set("")
        from(rootProject.file("LICENSE")) {
            rename { "${it}_$archivesBaseName" }
        }
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    named<Jar>("jar") {
        enabled = false
    }

    matching { it.name == "remapJar" }.configureEach {
        enabled = false
    }

    named("build") {
        dependsOn(shadowJar)
    }
}
