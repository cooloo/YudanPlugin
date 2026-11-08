plugins { id("com.android.library"); id("yd.music.package") }
android {
    namespace = "com.egogame.example.plugin"
    compileSdk = 34
    defaultConfig { minSdk = 28 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
ydMusicPackage {
    manifest.set(layout.projectDirectory.file("plugin.json"))
    icon.set(layout.projectDirectory.file("icon.png"))
}
dependencies { compileOnly(project(":yd-plugin-api")); compileOnly(project(":yd-plugin-music-api")) }
