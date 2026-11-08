$ErrorActionPreference = 'Stop'
$utf8 = [Text.UTF8Encoding]::new($false, $true)
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$destination = Join-Path $root 'build/distribution/music-plugins'
$officialCertificate = (Get-FileHash -LiteralPath (Join-Path $root 'docs/official-plugin-certificate.der')).Hash.ToLowerInvariant()
Add-Type -AssemblyName System.IO.Compression.FileSystem
$records = foreach ($relative in @('plugin/plugin.json', 'qqmusic/plugin.json', 'kugoumusic/plugin.json')) {
    $expected = Get-Content -LiteralPath (Join-Path $root $relative) -Raw -Encoding UTF8 | ConvertFrom-Json
    $fileName = $expected.id + '-' + $expected.versionName + '.ydplugin'
    $path = Join-Path $destination $fileName
    $archive = [IO.Compression.ZipFile]::OpenRead($path)
    try {
        $reader = [IO.StreamReader]::new($archive.GetEntry('plugin.json').Open(), $utf8)
        try { $manifest = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
    } finally { $archive.Dispose() }
    if ($manifest.id -cne $expected.id -or $manifest.versionName -cne '1.0.24' -or $manifest.versionCode -ne 25) {
        throw "插件实际版本不符：$fileName"
    }
    $hash = (Get-FileHash -LiteralPath $path).Hash.ToLowerInvariant()
    $report = Get-Content -LiteralPath ($path + '.build.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    $latest = Get-Content -LiteralPath (Join-Path $destination ($expected.id + '.latest.json')) -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($report.packageSha256 -cne $hash -or $report.signerCertificateSha256 -cne $officialCertificate) {
        throw "构建记录或官方证书不符：$fileName"
    }
    if ($latest.file -cne $fileName -or $latest.packageSha256 -cne $hash -or
        $latest.versionCode -ne 25 -or $latest.versionName -cne '1.0.24') {
        throw "最新版本记录不符：$fileName"
    }
    [pscustomobject]@{
        name = $manifest.name; file = $fileName; versionName = $manifest.versionName
        versionCode = $manifest.versionCode; bytes = (Get-Item -LiteralPath $path).Length
        sha256 = $hash; signerCertificateSha256 = $officialCertificate; source = $path
    }
}

# 归档当前源码，与本次三个插件一并交付。
$env:PYTHONIOENCODING = 'utf-8'
Push-Location $root
try {
    & python tools/package-source.py
    if ($LASTEXITCODE -ne 0) { throw '公开源码归档失败' }
} finally { Pop-Location }
$sourceReport = Get-Content -LiteralPath (Join-Path $destination 'source-manifest.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if ((Get-FileHash -LiteralPath (Join-Path $destination $sourceReport.archive)).Hash.ToLowerInvariant() -cne $sourceReport.sha256) {
    throw '公开源码归档哈希不符'
}
[IO.File]::WriteAllText((Join-Path $destination 'manifest.json'), ($records | ConvertTo-Json -Depth 4), $utf8)
$checksums = @(Get-ChildItem -LiteralPath $destination -File | Where-Object {
    $_.Name -ne 'SHA256SUMS.txt'
} | Sort-Object Name | ForEach-Object {
    (Get-FileHash -LiteralPath $_.FullName).Hash.ToLowerInvariant() + '  ' + $_.Name
})
[IO.File]::WriteAllLines((Join-Path $destination 'SHA256SUMS.txt'), [string[]]$checksums, $utf8)
$records | Select-Object name,versionName,versionCode,bytes,file | Format-Table -AutoSize
Write-Output '三个插件实际版本、官方签名记录、最新版本指向及交付哈希均已核对。'
