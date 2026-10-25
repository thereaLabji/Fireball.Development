plugins {
    java
}

dependencies {
    compileOnly(project(":fireball-api"))
}

tasks.jar {
    archiveBaseName.set("example-mod-java")
    archiveVersion.set("1.0.0")
}
