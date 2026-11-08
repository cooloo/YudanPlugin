plugins {
    id("yd.music.library")
    id("yd.music.package")
}

android { namespace = "com.cloudmusic.car" }

ydMusicPackage {
    manifest.set(rootProject.layout.projectDirectory.file("plugin/plugin.json"))
    icon.set(layout.projectDirectory.file("src/main/res/drawable-nodpi/ic_cloudmusic_app.png"))
}

dependencies {
    implementation(project(":music-plugin-common"))
    implementation(project(":music-nowplaying"))
}
