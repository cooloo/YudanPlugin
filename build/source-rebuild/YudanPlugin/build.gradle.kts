plugins {
    id("com.android.library") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "1.8.22" apply false
}
subprojects { layout.buildDirectory.set(rootProject.layout.buildDirectory.dir("modules/$name")) }
tasks.register("packageMusicPlugins") {
    group = "distribution"
    description = "打包三个音乐插件并核对签名、宿主成员及最终 DEX"
    dependsOn(":app:packageReleasePlugin", ":qqmusic:packageReleasePlugin", ":kugoumusic:packageReleasePlugin")
}
tasks.register("publishSdk") {
    group = "publishing"
    description = "将五个公共 SDK 模块发布到本地版本化 Maven 仓库"
    dependsOn(listOf("api", "runtime", "ui", "music-api", "vehicle-model-api").map { ":yd-plugin-$it:publishReleasePublicationToYdPluginSdkRepository" })
}
