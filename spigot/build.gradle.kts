plugins {
    `java-library`
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("net.minecrell.plugin-yml.bukkit") version "0.5.2"
}

val versionStr = (System.getenv("VERSION") ?: "0.5").removePrefix("v")

group = "ao1roh.dev.nahnominimap"
version = versionStr

repositories {
    mavenCentral()
    maven {
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    }
    maven {
        url = uri("https://oss.sonatype.org/content/groups/public/")
    }
    maven {
        url = uri("https://repo.viaversion.com")
    }
    maven {
        url = uri("https://repo.codemc.io/repository/maven-releases/")
    }
}

dependencies {
    // Main Dependencies
    compileOnly("org.spigotmc:spigot-api:1.16.5-R0.1-SNAPSHOT")
    compileOnly("com.viaversion:viaversion-api:4.9.2")
    compileOnly("com.github.retrooper:packetevents-spigot:2.14.0")
    implementation(project(":common"))

    // Common Dependencies
    implementation("net.kyori:adventure-api:4.10.0")
    implementation("net.kyori:adventure-platform-bukkit:4.3.1")
    implementation("net.kyori:adventure-text-minimessage:4.10.0")
    implementation("net.kyori:adventure-nbt:4.15.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.code.gson:gson:2.8.0")
    testImplementation("org.spigotmc:spigot-api:1.16.5-R0.1-SNAPSHOT")
}

val javaTarget = 11
java {
    sourceCompatibility = JavaVersion.toVersion(javaTarget)
    targetCompatibility = JavaVersion.toVersion(javaTarget)
    if (JavaVersion.current() < JavaVersion.toVersion(javaTarget)) {
        toolchain.languageVersion.set(JavaLanguageVersion.of(javaTarget))
    }
}

tasks {
    build {
        dependsOn(shadowJar)
    }
    shadowJar {
        relocate("net.kyori", "ao1roh.dev.nahnominimap.kyori")
        exclude("com/google/gson/**")
        exclude("org/apache/commons/**")
        exclude("org/yaml/snakeyaml/**")
        archiveBaseName.set("NahNoMinimap")
        archiveClassifier.set("")
        doLast {
            copy {
                from(archiveFile)
                into("${rootProject.projectDir}/build")
            }
        }
    }
}

bukkit {
    name = "NahNoMinimap"
    main = "ao1roh.dev.nahnominimap.Main"
    authors = listOf("Valeim82")
    description = "Control minimap settings from server-side software"

    apiVersion = "1.13"

    softDepend = listOf("viaversion", "ItemsAdder", "Nexo", "CraftEngine", "Oraxen", "CoordinateOffset", "packetevents")
    commands {
        register("nahnominimap") {
            description = "Reload NahNoMinimap configuration"
            aliases = listOf("nm")
            usage = "/nahnominimap reload"
        }
    }
}
