plugins {
    id("java") // Tell gradle this is a java project.
    id("java-library") // Import helper for source-based libraries.
    id("com.diffplug.spotless") version "7.0.4" // Import auto-formatter.
    id("com.gradleup.shadow") version "8.3.6" // Import shadow API.
    eclipse // Import eclipse plugin for IDE integration.
    id("io.freefair.lombok") version "8.13.1" // Automatic lombok support.
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
    maven { url = uri("https://repo.purpurmc.org/snapshots") }
    maven { url = uri("https://repo.onarandombox.com/content/groups/public/") }
}

dependencies {
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.16.1")
    api("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    api("commons-io:commons-io:2.11.0")
    api("org.apache.commons:commons-pool2:2.11.1")
    api("com.mysql:mysql-connector-j:8.2.0")
    api("org.mariadb.jdbc:mariadb-java-client:3.1.3")
    api("redis.clients:jedis:3.4.1")
    api("xyz.xenondevs:particle:1.8.3")
    api("me.tigerhix.lib:scoreboard:1.0.1-SNAPSHOT")
    compileOnly("org.purpurmc.purpur:purpur-api:1.19.4-R0.1-SNAPSHOT") // Declare purpur API version to be packaged.
    compileOnly("io.github.miniplaceholders:miniplaceholders-api:2.2.3") // Import MiniPlaceholders API.
    compileOnlyApi(project(":libs:Utilities-OG")) // Import TrueOG Network Utilities-OG API.
    compileOnlyApi(project(":libs:GxUI-OG")) // Import TrueOG Network GxUI-OG API.
    compileOnlyApi(project(":libs:DiamondBank-OG")) // Import TrueOG Network DiamondBank-OG API.
    compileOnly("com.onarandombox.multiversecore:multiverse-core:4.3.12") // Import Multiverse Core API.
    compileOnly("com.comphenix.protocol:ProtocolLib:5.1.0") // Import ProtocolLib API.
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
    options.compilerArgs.add("-parameters")
    options.compilerArgs.add("-Xlint:deprecation") // Triggers deprecation warning messages.
    options.encoding = "UTF-8"
    options.isFork = true
}

tasks.named<ProcessResources>("processResources") {
    val props = mapOf("version" to version, "apiVersion" to apiVersion)

    inputs.properties(props) // Indicates to rerun if version changes.

    filesMatching("plugin.yml") { expand(props) }
    from("LICENSE") { // Bundle license into .jars.
        into("/")
    }
}

tasks.shadowJar {
    archiveClassifier.set("") // Use empty string instead of null.
    from("LICENSE") { into("/") }
    exclude("io.github.miniplaceholders.*")
    minimize()
    doLast { revertPluginYmlVersion() }
}

tasks.withType<AbstractArchiveTask>().configureEach { // Ensure reproducible .jars
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.build {
    dependsOn(tasks.spotlessApply)
    dependsOn(tasks.shadowJar)
}

tasks.jar { archiveClassifier.set("part") }

spotless {
    java {
        removeUnusedImports()
        palantirJavaFormat()
    }
    kotlinGradle {
        ktfmt().kotlinlangStyle().configure { it.setMaxWidth(120) }
        target("build.gradle.kts", "settings.gradle.kts")
    }
}
