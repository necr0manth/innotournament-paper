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

    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("26.1.2")
        jvmArgs("-Xms2G", "-Xmx2G", "-Dcom.mojang.eula.agree=true")
    }
}
