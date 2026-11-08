plugins {
    id("yd.music.library")
    id("yd.music.package")
}

android { namespace = "com.kugoumusic.car" }

ydMusicPackage {
    manifest.set(layout.projectDirectory.file("plugin.json"))
    icon.set(layout.projectDirectory.file("src/main/res/drawable-nodpi/ic_kugoumusic_app.png"))
}

dependencies {
    implementation(project(":music-plugin-common"))
    implementation(project(":music-nowplaying"))
}
