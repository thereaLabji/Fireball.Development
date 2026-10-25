
plugins {
    java
}

dependencies {
    compileOnly(project(":fireball-api"))
}

tasks.jar {
    archiveBaseName.set("ember")
    archiveVersion.set("1.0.0")
}
