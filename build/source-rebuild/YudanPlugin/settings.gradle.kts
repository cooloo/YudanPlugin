pluginManagement {
    includeBuild("build-logic")
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "YudanPlugin"
include(":yd-plugin-api", ":yd-plugin-runtime", ":yd-plugin-ui", ":yd-plugin-music-api", ":yd-plugin-vehicle-model-api")
include(":app", ":qqmusic", ":kugoumusic", ":music-plugin-common", ":music-nowplaying", ":example-plugin")
project(":music-nowplaying").projectDir = file("shared/nowplaying")
