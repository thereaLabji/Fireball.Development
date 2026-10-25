plugins {
    `java-library`
}

dependencies {
    api(project(":fireball-api"))
}

tasks.jar {
    archiveBaseName.set("fireball-api")
    archiveVersion.set(project.version.toString())

    // Bundle fireball-api classes into this mod JAR
    from(project(":fireball-api").sourceSets.main.get().output)

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
