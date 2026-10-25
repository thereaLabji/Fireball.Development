plugins { java }

dependencies { compileOnly(project(":fireball-api")) }

tasks.jar {
    archiveBaseName.set("fireball-assistant")
    archiveVersion.set("1.0.0")
}
