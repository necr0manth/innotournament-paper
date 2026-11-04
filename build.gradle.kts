import java.security.MessageDigest

plugins {
    kotlin("jvm") version "2.4.0-Beta2"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
    id("com.gradleup.shadow") version "9.4.1"
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("xyz.jpenilla.resource-factory-paper-convention") version "1.3.1"
    id("xyz.jpenilla.gremlin-gradle") version "0.0.9"
}

repositories {
    mavenCentral()
}

configurations.compileOnly {
    extendsFrom(configurations.runtimeDownload.get())
}
configurations.testImplementation {
    extendsFrom(configurations.runtimeDownload.get())
}

dependencies {
    paperweight.paperDevBundle("26.1.2.build.+")
    implementation("xyz.jpenilla:gremlin-runtime:0.0.9")
    runtimeDownload("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    runtimeDownload("net.kyori:adventure-nbt:4.26.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}

paperPluginYaml {
    main = "dev.necr0manthre.innotournament.Innotournament"
    loader = "xyz.jpenilla.gremlin.runtime.platformsupport.DefaultsPaperPluginLoader"
    apiVersion = "26.1.2"
    permissions.register("innotournament.admin") {
        description.set("Allows access to tournament administration commands")
        default.set(xyz.jpenilla.resourcefactory.bukkit.Permission.Default.OP)
    }
}

kotlin {
    jvmToolchain(25)
}

// Client resource pack is a separate, reproducible ZIP; it is not placed in the plugin JAR.
val packageResourcePack = tasks.register<Zip>("packageResourcePack") {
    group = "build"
    description = "Package the InnoTournament resource pack for Minecraft 26.1.2."
    from(layout.projectDirectory.dir("resource-pack"))
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    archiveFileName.set("innotournament-resource-pack-26.1.2.zip")
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
}

val resourcePackSha1 = tasks.register("resourcePackSha1") {
    group = "build"
    description = "Write the SHA-1 of the exact resource-pack ZIP for server.properties."
    dependsOn(packageResourcePack)

    val archive = packageResourcePack.flatMap { it.archiveFile }
    val sha1File = layout.buildDirectory.file("libs/innotournament-resource-pack-26.1.2.zip.sha1")
    inputs.file(archive)
    outputs.file(sha1File)

    doLast {
        val digest = MessageDigest.getInstance("SHA-1")
        archive.get().asFile.inputStream().buffered().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        sha1File.get().asFile.writeText("$hash\n")
    }
}

tasks {
    build {
        dependsOn(shadowJar, resourcePackSha1)
    }

    test {
        useJUnitPlatform()
        classpath += configurations["compileClasspath"]
        testLogging.showStandardStreams = true
    }

    check {
        dependsOn("verifyTeamModel")
    }

    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("26.1.2")
        jvmArgs("-Xms2G", "-Xmx2G", "-Dcom.mojang.eula.agree=true")
    }
}

// The paperweight API is compile-only for main, but needed by the model check too.
configurations.named("testCompileOnly") {
    extendsFrom(configurations.getByName("compileOnly"))
}

// Keep the focused model check available by name; standard test/check/build run it too.
tasks.register("verifyTeamModel") {
    group = "verification"
    description = "Check team membership, event guards, ownership, snapshots and deletion"
    dependsOn(tasks.named("test"))
}
