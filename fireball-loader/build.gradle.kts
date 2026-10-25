plugins {
    java
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    implementation(project(":fireball-api"))

    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("ch.qos.logback:logback-classic:1.5.12")

    // YAML config
    implementation("org.yaml:snakeyaml:2.3")
}

val loaderVersion: String = project.version.toString()

// ---- Normal (generic) loader ----
application {
    mainClass.set("dev.fireball.Fireball")
}

tasks.shadowJar {
    archiveBaseName.set("fireball")
    archiveVersion.set(loaderVersion)
    archiveClassifier.set("")
    mergeServiceFiles()
}

// ---- Server loader (separate JAR) ----
tasks.register<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJarServer") {
    group = "build"
    description = "Build the Fireball Server JAR"

    from(sourceSets.main.get().output)
    configurations = listOf(project.configurations.runtimeClasspath.get())

    archiveBaseName.set("fireball-server")
    archiveVersion.set(loaderVersion)
    archiveClassifier.set("")
    mergeServiceFiles()

    manifest {
        attributes["Main-Class"] = "dev.fireball.FireballServer"
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
    dependsOn(tasks.named("shadowJarServer"))
}

// Produce final artifacts in build/dist/
tasks.register<Copy>("dist") {
    dependsOn(tasks.shadowJar, tasks.named("shadowJarServer"))

    from(tasks.shadowJar.get().archiveFile)
    from(tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJarServer").get().archiveFile)
    into(layout.buildDirectory.dir("dist"))

    // Ensure clean names
    rename { name ->
        when {
            name.startsWith("fireball-server") -> "fireball-server-${loaderVersion}.jar"
            name.startsWith("fireball") -> "fireball-${loaderVersion}.jar"
            else -> name
        }
    }

    doLast {
        println("Created:")
        println("  build/dist/fireball-${loaderVersion}.jar")
        println("  build/dist/fireball-server-${loaderVersion}.jar")
    }
}
