plugins {
    java
}

dependencies {
    compileOnly(project(":fireball-api"))
}

tasks.jar {
    archiveBaseName.set("tringus")
    archiveVersion.set("0.0.1")
}