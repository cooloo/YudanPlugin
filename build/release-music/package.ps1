$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-17'
$env:ANDROID_HOME = 'D:/tools/android-sdk'
$env:PYTHONIOENCODING = 'utf-8'
$env:PATH = (Join-Path $env:JAVA_HOME 'bin') + [IO.Path]::PathSeparator + $env:PATH
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Push-Location $root
try {
    & './gradlew.bat' packageMusicPlugins --console=plain --max-workers=2 2>&1 |
        Tee-Object -FilePath (Join-Path $PSScriptRoot 'build.log')
    if ($LASTEXITCODE -ne 0) { throw "音乐插件打包失败：$LASTEXITCODE" }
} finally {
    Pop-Location
}
