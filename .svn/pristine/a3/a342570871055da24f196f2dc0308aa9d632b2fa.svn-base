import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifacts
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.attributes.Attribute
import org.gradle.api.artifacts.component.ProjectComponentIdentifier

abstract class YdMusicPackageExtension {
    abstract RegularFileProperty getManifest()
    abstract RegularFileProperty getIcon()
}

class YdMusicPackagePlugin implements Plugin<Project> {
    void apply(Project project) {
        def config = project.extensions.create('ydMusicPackage', YdMusicPackageExtension)
        def components = project.extensions.getByName('androidComponents')
        components.onVariants(components.selector().withBuildType('release')) { variant ->
            def task = project.tasks.register('packageReleasePlugin', PackageYdMusicTask) {
                group = 'distribution'
                description = '检查成员 ABI、生成全部 DEX 并签名鱼蛋插件'
                manifest.set(config.manifest)
                icon.set(config.icon)
                rootDirectory.set(project.rootProject.layout.projectDirectory)
                sdkDirectory.set(components.sdkComponents.sdkDirectory)
                buildToolsVersion.set(project.extensions.getByName('android').buildToolsVersion)
                compileClasspath.from(variant.compileConfiguration.incoming.artifactView {
                    attributes.attribute(Attribute.of('artifactType', String), 'android-classes-jar')
                }.files)
                bundledClasses.from(variant.runtimeConfiguration.incoming.artifactView {
                    attributes.attribute(Attribute.of('artifactType', String), 'android-classes-jar')
                    componentFilter { id -> id instanceof ProjectComponentIdentifier && id.projectPath in [':music-plugin-common', ':music-nowplaying'] }
                }.files)
                pythonExecutable.set(project.providers.gradleProperty('ydPython').orElse('python'))
                baseline.set(project.rootProject.layout.file(project.providers.gradleProperty('ydHostAbi').orElse('docs/runtime/host-abi.json').map { project.rootProject.file(it) }))
                signingProperties.set(project.rootProject.layout.file(project.providers.gradleProperty('ydSigningProperties').orElse('.local/signing/plugin-signing.properties').map { project.rootProject.file(it) }))
                assetsDirectory.set(project.layout.projectDirectory.dir('src/main/assets'))
                workDirectory.set(project.rootProject.layout.buildDirectory.dir('plugin-packaging/' + project.name))
                outputDirectory.set(project.rootProject.layout.buildDirectory.dir('music-plugins'))
            }
            variant.artifacts.forScope(ScopedArtifacts.Scope.PROJECT).use(task).toGet(
                ScopedArtifact.CLASSES.INSTANCE,
                { it.allJars }, { it.allDirectories }
            )
        }
    }
}
