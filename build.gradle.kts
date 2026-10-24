import org.gradle.api.file.DuplicatesStrategy
import org.gradle.jvm.tasks.Jar
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("net.fabricmc.fabric-loom") version "1.18.2"
    id("com.gradleup.shadow") version "9.0.0-beta12"
}

val minecraftVersion = project.property("minecraft_version") as String
val loaderVersion = project.property("loader_version") as String
val fabricApiVersion = project.property("fabric_api_version") as String
val meteorVersion = project.property("meteor_version") as String
val baritoneVersion = project.property("baritone_version") as String
val xppleCubiomesVersion = project.property("xpple_cubiomes_version") as String
val modVersion = project.property("mod.version") as String
val mavenGroup = project.property("maven_group") as String

base {
    archivesName = project.property("archives_base_name") as String
    version = "${minecraftVersion}-${modVersion}"
    group = mavenGroup
}

repositories {
    mavenCentral()
    mavenLocal()
    maven("xpple") { url = uri("https://maven.xpple.dev/maven2") }
    maven("meteor-maven") { url = uri("https://maven.meteordev.org/releases") }
    maven("meteor-maven-snapshots") { url = uri("https://maven.meteordev.org/snapshots") }
    maven("JitPack") { url = uri("https://jitpack.io") }
}

val shade by configurations.creating

configurations.named("implementation") {
    extendsFrom(shade)
}

dependencies {
    // Fabric
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:$loaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    // Meteor / Baritone
    implementation("meteordevelopment:meteor-client:${meteorVersion}-SNAPSHOT")
    compileOnly("meteordevelopment:orbit:0.2.4")
    compileOnly("meteordevelopment:baritone:${baritoneVersion}-SNAPSHOT") {
        isTransitive = false
    }

    // Cubiomes Java bindings (FFM). Native is loaded from resources at runtime.
    shade("dev.xpple:cubiomes:$xppleCubiomesVersion") { isTransitive = false }
}

tasks {
    processResources {
        val propertyMap = mapOf(
            "version" to project.version,
            "mc_version" to minecraftVersion,
        )

        inputs.properties(propertyMap)

        filteringCharset = "UTF-8"

        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    val shadowJar by getting(ShadowJar::class) {
        configurations = listOf(shade)
        archiveClassifier.set("")
        inputs.property("archivesName", project.base.archivesName.get())

        from("LICENSE") {
            rename { "${it}_${inputs.properties["archivesName"]}" }
        }
    }

    named<Jar>("jar") {
        enabled = false
        inputs.property("archivesName", project.base.archivesName.get())

        from("LICENSE") {
            rename { "${it}_${inputs.properties["archivesName"]}" }
        }

        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    matching { it.name == "remapJar" }.configureEach {
        enabled = false
    }

    named("build") {
        dependsOn(shadowJar)
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release = 25
        options.compilerArgs.add("-Xlint:deprecation")
        options.compilerArgs.add("-Xlint:unchecked")
    }
}
