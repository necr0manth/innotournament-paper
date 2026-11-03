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

dependencies {
    paperweight.paperDevBundle("26.1.2.build.+")
    implementation("xyz.jpenilla:gremlin-runtime:0.0.9")
    runtimeDownload("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}

paperPluginYaml {
    main = "dev.necr0manthre.innotournament.Innotournament"
    bootstrapper = "dev.necr0manthre.innotournament.InnotournamentBootstrap"
    loader = "xyz.jpenilla.gremlin.runtime.platformsupport.DefaultsPaperPluginLoader"
    apiVersion = "26.1.2"
}

kotlin {
    jvmToolchain(25)
}

tasks {
    build {
        dependsOn(shadowJar)
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
