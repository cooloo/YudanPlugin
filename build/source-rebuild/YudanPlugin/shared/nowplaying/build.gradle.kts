plugins {
    id("yd.music.library")
    id("maven-publish")
}

group = "com.egogame.plugin"
version = "1.0.0"
android {
    namespace = "com.egogame.music.nowplaying"
    defaultConfig { minSdk = 22 }
    publishing { singleVariant("release") { withSourcesJar() } }
}

dependencies { implementation(project(":music-plugin-common")) }

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                pom { licenses { license { name.set("GNU Lesser General Public License v3.0"); url.set("https://www.gnu.org/licenses/lgpl-3.0.html") } } }
            }
        }
        repositories { maven { name = "ydMusicShared"; url = rootProject.layout.buildDirectory.dir("music-shared/maven").get().asFile.toURI() } }
    }
}
