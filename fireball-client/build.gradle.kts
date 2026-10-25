plugins {
    java
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    maven("https://maven.fabricmc.net/")
}

dependencies {
    implementation(project(":fireball-loader"))
    implementation("com.google.code.gson:gson:2.11.0")
    // The first compatibility runtime: Fabric Loader's Knot + Mixin bootstrap.
    implementation("net.fabricmc:fabric-loader:0.16.10")
}

application {
    mainClass.set("dev.fireball.client.FireballClient")
}

tasks.shadowJar {
    archiveBaseName.set("fireball-client")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("")
    mergeServiceFiles()
    manifest {
        attributes[
            "Main-Class"
        ] = "dev.fireball.client.FireballClient"
        attributes["Premain-Class"] = "dev.fireball.client.FireballAgent"
    }
}
