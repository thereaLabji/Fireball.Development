pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "fireball"

include("fireball-api")
include("fireball-loader")
include("example-mod-java")
include("example-mod-kotlin")
include("fireball-api-mod")
include("ember-mod")
include("fireball-client")
include("fireball-assistant-mod")
include("tringus")
