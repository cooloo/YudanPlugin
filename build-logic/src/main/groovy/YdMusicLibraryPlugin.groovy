import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project

class YdMusicLibraryPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.pluginManager.apply('com.android.library')
        project.pluginManager.apply('org.jetbrains.kotlin.android')
        def android = project.extensions.getByName('android')
        android.compileSdk = 34
        android.defaultConfig.minSdk = 28
        android.compileOptions.sourceCompatibility = JavaVersion.VERSION_1_8
        android.compileOptions.targetCompatibility = JavaVersion.VERSION_1_8
        android.kotlinOptions.jvmTarget = '1.8'
        android.buildFeatures.compose = true
        android.composeOptions.kotlinCompilerExtensionVersion = '1.4.8'
        android.buildTypes.release.minifyEnabled = false

        // SDK 和第三方运行库只用于编译；两个音乐实现由业务包自行携带。
        [
            'com.egogame.plugin:yd-plugin-api:1.0.0',
            'com.egogame.plugin:yd-plugin-music-api:1.0.0',
            'com.egogame.plugin:yd-plugin-ui:1.0.0',
            'org.jetbrains.kotlin:kotlin-stdlib:1.8.22',
            'androidx.compose.ui:ui:1.6.1',
            'androidx.compose.animation:animation:1.6.1',
            'androidx.compose.foundation:foundation:1.6.1',
            'androidx.compose.material3:material3:1.2.0',
            'androidx.activity:activity-compose:1.7.0',
            'androidx.core:core-ktx:1.12.0',
            'androidx.lifecycle:lifecycle-runtime-compose:2.6.2',
            'androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2',
            'androidx.media3:media3-exoplayer:1.2.1',
            'androidx.media3:media3-session:1.2.1',
            'androidx.media3:media3-datasource-okhttp:1.2.1',
            'androidx.media:media:1.6.0',
            'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3',
            'com.squareup.okhttp3:okhttp:4.9.3',
            'io.coil-kt:coil-compose:2.5.0',
            'androidx.palette:palette-ktx:1.0.0',
            'com.google.zxing:core:3.4.1'
        ].each {
            def sdkModule = it.startsWith('com.egogame.plugin:yd-plugin-') ? ':' + it.split(':')[1] : null
            project.dependencies.add('compileOnly', sdkModule != null ? project.project(sdkModule) : it)
        }
        project.configurations.configureEach {
            resolutionStrategy.eachDependency { details ->
                def group = details.requested.group
                if (group.startsWith('androidx.compose.') && group != 'androidx.compose.compiler' && group != 'androidx.compose.material3') details.useVersion('1.6.1')
                if (group == 'androidx.compose.material3') details.useVersion('1.2.0')
                if (group == 'androidx.lifecycle') details.useVersion('2.6.2')
                if (group == 'androidx.activity') details.useVersion('1.7.0')
                if (group == 'androidx.core') details.useVersion('1.12.0')
                if (group == 'androidx.media3') details.useVersion('1.2.1')
                if (group == 'org.jetbrains.kotlin') details.useVersion('1.8.22')
                if (group == 'org.jetbrains.kotlinx' && details.requested.name.startsWith('kotlinx-coroutines')) details.useVersion('1.7.3')
                if (group == 'io.coil-kt') details.useVersion('2.5.0')
                if (group == 'com.squareup.okhttp3') details.useVersion('4.9.3')
            }
        }
    }
}
