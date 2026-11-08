$ErrorActionPreference = 'Stop'
$utf8 = [Text.UTF8Encoding]::new($false, $true)
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$sourceDirectory = Join-Path $root 'build/plugin-device-116-20261104/YudanPlugin/root/music-plugins'
$destination = Join-Path $root 'build/distribution/music-plugins'
$packages = @(
    @{ Name = '云音乐'; File = 'netease-1.0.21.ydplugin'; Version = '1.0.21'; Code = 22 },
    @{ Name = 'QQ 音乐'; File = 'qqmusic-1.0.23.ydplugin'; Version = '1.0.23'; Code = 24 },
    @{ Name = '酷狗音乐'; File = 'kugoumusic-1.0.20.ydplugin'; Version = '1.0.20'; Code = 21 }
)
New-Item -ItemType Directory -Path $destination -Force | Out-Null
$records = foreach ($package in $packages) {
    $source = Join-Path $sourceDirectory $package.File
    $report = Get-Content -LiteralPath ($source + '.build.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    $hash = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($hash -cne $report.packageSha256) { throw "原始文件与构建记录不符：$source" }
    foreach ($name in @($package.File, ($package.File + '.build.json'))) {
        $original = Join-Path $sourceDirectory $name
        $target = Join-Path $destination $name
        if ((Test-Path -LiteralPath $target) -and
            (Get-FileHash -LiteralPath $original).Hash -cne (Get-FileHash -LiteralPath $target).Hash) {
            throw "固定目录已有不同内容的同名文件：$target"
        }
        Copy-Item -LiteralPath $original -Destination $target -Force
        if ((Get-FileHash -LiteralPath $original).Hash -cne (Get-FileHash -LiteralPath $target).Hash) {
            throw "复制后校验失败：$target"
        }
    }
    $latest = [ordered]@{
        file = $package.File; versionName = $package.Version; versionCode = $package.Code
        packageSha256 = $hash; signerCertificateSha256 = $report.signerCertificateSha256
    }
    $id = $package.File.Substring(0, $package.File.IndexOf('-'))
    [IO.File]::WriteAllText((Join-Path $destination ($id + '.latest.json')), ($latest | ConvertTo-Json), $utf8)
    [pscustomobject]@{
        name = $package.Name; file = $package.File; versionName = $package.Version; versionCode = $package.Code
        sha256 = $hash; signerCertificateSha256 = $report.signerCertificateSha256; source = $source
    }
}
[IO.File]::WriteAllText((Join-Path $destination 'manifest.json'), ($records | ConvertTo-Json -Depth 4), $utf8)
$checksums = @(Get-ChildItem -LiteralPath $destination -File | Where-Object {
    $_.Name -ne 'SHA256SUMS.txt'
} | Sort-Object Name | ForEach-Object {
    (Get-FileHash -LiteralPath $_.FullName).Hash.ToLowerInvariant() + '  ' + $_.Name
})
[IO.File]::WriteAllLines((Join-Path $destination 'SHA256SUMS.txt'), [string[]]$checksums, $utf8)
$records | Select-Object name, versionName, versionCode, file | Format-Table -AutoSize
