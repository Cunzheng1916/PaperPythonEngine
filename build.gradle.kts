import xyz.jpenilla.resourcefactory.bukkit.BukkitPluginYaml

plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("xyz.jpenilla.resource-factory-bukkit-convention") version "1.3.1"
    id("xyz.jpenilla.resource-factory-paper-convention") version "1.3.1"
    id("com.gradleup.shadow") version "9.6.1"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    implementation("org.graalvm.polyglot:polyglot:25.0.3")
    implementation("org.graalvm.python:python-language:25.0.3")
    implementation("org.graalvm.python:python-resources:25.0.3")
    implementation("org.graalvm.truffle:truffle-runtime:25.0.3")
    implementation("org.graalvm.regex:regex:25.0.3")
}

bukkitPluginYaml {
    main = "org.PFWs.PythonE.paperS"
    apiVersion = "1.21.8"

    load = BukkitPluginYaml.PluginLoadOrder.STARTUP
    authors.addAll("Cunzheng1916")
}

paperPluginYaml {
    main = "org.PFWs.PythonE.paperS"
    apiVersion = "1.21.8"
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        mergeServiceFiles()
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    runServer {
        minecraftVersion("1.21.8")
        jvmArgs("-Xms2G", "-Xmx2G", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    }
}
