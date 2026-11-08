import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.file.*
import org.gradle.api.provider.*
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import javax.inject.Inject

abstract class PackageYdMusicTask extends DefaultTask {
    @InputFiles @PathSensitive(PathSensitivity.RELATIVE) abstract ListProperty<RegularFile> getAllJars()
    @InputFiles @PathSensitive(PathSensitivity.RELATIVE) abstract ListProperty<Directory> getAllDirectories()
    @Classpath abstract ConfigurableFileCollection getCompileClasspath()
    @Classpath abstract ConfigurableFileCollection getBundledClasses()
    @InputFile abstract RegularFileProperty getManifest()
    @InputFile abstract RegularFileProperty getIcon()
    @InputFile abstract RegularFileProperty getBaseline()
    @InputFile abstract RegularFileProperty getSigningProperties()
    @Internal abstract DirectoryProperty getRootDirectory()
    @Internal abstract DirectoryProperty getSdkDirectory()
    @Internal abstract DirectoryProperty getAssetsDirectory()
    @Input abstract Property<String> getBuildToolsVersion()
    @Input abstract Property<String> getPythonExecutable()
    @LocalState abstract DirectoryProperty getWorkDirectory()
    @OutputDirectory abstract DirectoryProperty getOutputDirectory()
    @Inject abstract ExecOperations getExecOperations()

    PackageYdMusicTask() {
        // 签名材料、宿主 ABI 与构建来源每次都需重新核对，不复用已签名缓存。
        outputs.upToDateWhen { false }
    }

    @TaskAction void packagePlugin() {
        def work = workDirectory.get().asFile
        work.mkdirs()
        def request = new File(work, 'request.json')
        request.setText(JsonOutput.toJson([
            root: rootDirectory.get().asFile.absolutePath,
            jars: (allJars.get().collect { it.asFile.absolutePath } + bundledClasses.files.collect { it.absolutePath }).unique(),
            directories: allDirectories.get().collect { it.asFile.absolutePath },
            classpath: compileClasspath.files.collect { it.absolutePath },
            manifest: manifest.get().asFile.absolutePath,
            icon: icon.get().asFile.absolutePath,
            baseline: baseline.get().asFile.absolutePath,
            signing: signingProperties.get().asFile.absolutePath,
            assets: assetsDirectory.get().asFile.absolutePath,
            sdk: sdkDirectory.get().asFile.absolutePath,
            buildTools: buildToolsVersion.get(),
            work: work.absolutePath,
            output: outputDirectory.get().asFile.absolutePath
        ]), 'UTF-8')
        execOperations.exec {
            commandLine(pythonExecutable.get(), new File(rootDirectory.get().asFile, 'tools/packaging/package_plugin.py'), '--request', request.absolutePath)
        }.assertNormalExitValue()
    }
}
