plugins {
    kotlin("jvm")
}

dependencies {
    compileOnly(project(":fireball-api"))
    implementation(kotlin("stdlib"))
}

tasks.jar {
    archiveBaseName.set("example-mod-kotlin")
    archiveVersion.set("1.0.0")
    // Include Kotlin stdlib so the mod is self-contained
    from({
        configurations.runtimeClasspath.get().filter { it.name.contains("kotlin-stdlib") }.map { if (it.isDirectory) it else zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
