plugins {
    id("com.gradleup.shadow") version "8.3.2" // Import shadow API.
    java // Tell gradle this is a java project.
    eclipse // Import eclipse plugin for IDE integration.
    kotlin("jvm") version "2.0.20" // Import kotlin jvm plugin for kotlin/java integration.
}

group = "uk.hotten.herobrine"
version = "1.3.3"
val apiVersion = "1.19" // Minecraft server target version.

java {
    sourceCompatibility = JavaVersion.VERSION_17
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
        vendor.set(JvmVendorSpec.GRAAL_VM)
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    gradlePluginPortal()
    maven { url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") }
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
    maven { url = uri("https://oss.sonatype.org/content/repositories/central") }
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://repo.dmulloy2.net/repository/public/") }
    maven { url = uri("https://repo.hotten.cloud/snapshots") }
    maven { url = uri("https://repo.hotten.cloud/releases") }
}

dependencies {
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.16.1")
    api("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    api("commons-io:commons-io:2.11.0")
    api("org.apache.commons:commons-pool2:2.11.1")
    api("com.mysql:mysql-connector-j:8.2.0")
    api("redis.clients:jedis:3.4.1")
    api("xyz.xenondevs:particle:1.8.3")
    api("me.tigerhix.lib:scoreboard:1.0.1-SNAPSHOT")
    compileOnly("org.spigotmc:spigot-api:1.19.4-R0.1-SNAPSHOT")
    compileOnly("com.comphenix.protocol:ProtocolLib:5.1.0")
    compileOnly("org.projectlombok:lombok:1.18.22")
    annotationProcessor("org.projectlombok:lombok:1.18.22")
    implementation("uk.hotten:gxui:1.2.1")
    compileOnly("org.purpurmc.purpur:purpur-api:1.19.4-R0.1-SNAPSHOT")
    compileOnly("io.github.miniplaceholders:miniplaceholders-api:2.2.3")
}

tasks.register("updatePluginYmlVersion") {
    doLast {
        val file = file("src/main/resources/plugin.yml")
        val lines = file.readLines().toMutableList()
        lines[2] = "version: ${version}-trueog"
        file.writeText(lines.joinToString("\n"))
    }
}

fun revertPluginYmlVersion() {
    val file = file("src/main/resources/plugin.yml")
    val lines = file.readLines().toMutableList()
    lines[2] = "version: $version"
    file.writeText(lines.joinToString("\n"))
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:deprecation"))
    options.encoding = "UTF-8"
    options.isFork = true
}

tasks.processResources {
    inputs.property("version", version)
    filesMatching("plugin.yml") {
        expand(mapOf("version" to version, "apiVersion" to apiVersion))
    }
}

tasks.shadowJar {
    minimize()
    archiveClassifier.set("trueog")
    from("LICENSE") { into("/") }
    exclude("io.github.miniplaceholders.*")
    doLast { revertPluginYmlVersion() }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.jar {
    archiveClassifier.set("part")
}

kotlin {
    jvmToolchain(17)
}

