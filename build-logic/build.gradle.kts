plugins { `groovy-gradle-plugin` }

layout.buildDirectory.set(layout.projectDirectory.dir("../build/build-logic"))

repositories { google(); mavenCentral(); gradlePluginPortal() }
dependencies {
    implementation("com.android.tools.build:gradle:8.7.3")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.22")
}

gradlePlugin {
    plugins {
        create("musicLibrary") {
            id = "yd.music.library"
            implementationClass = "YdMusicLibraryPlugin"
        }
        create("musicPackage") {
            id = "yd.music.package"
            implementationClass = "YdMusicPackagePlugin"
        }
    }
}
