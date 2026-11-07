# Download Android toolchain prerequisites (JDK / cmdline-tools / Gradle) via fast mirrors.
# Idempotent: skips files that already exist and look complete.
# Speed notes (probed 2026-11-07): Huawei OpenJDK mirror ~4MB/s, Tencent Gradle mirror ~4.6MB/s, dl.google.com ~4.7MB/s.
# api.adoptium.net and services.gradle.org are unusably slow on this network.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$root = 'C:\AndroidToolchain'
$dl   = Join-Path $root 'downloads'
$sdk  = Join-Path $root 'sdk'
$jdk  = Join-Path $root 'jdk'
$grl  = Join-Path $root 'gradle'
New-Item -ItemType Directory -Force -Path $dl, $sdk, $jdk, $grl | Out-Null

function Fetch($url, $dest, $minBytes) {
    if ((Test-Path $dest) -and ((Get-Item $dest).Length -ge $minBytes)) {
        Write-Output ("SKIP {0} ({1:N0} bytes)" -f $dest, (Get-Item $dest).Length)
        return
    }
    Write-Output ("GET  {0}" -f $url)
    $sw = [Diagnostics.Stopwatch]::StartNew()
    & curl.exe -L --fail --retry 3 --retry-delay 2 --speed-time 30 --speed-limit 200000 -o $dest $url
    if ($LASTEXITCODE -ne 0) { throw "curl failed ($LASTEXITCODE) for $url" }
    $len = (Get-Item $dest).Length
    Write-Output ("OK   {0:N0} bytes in {1:n0}s" -f $len, $sw.Elapsed.TotalSeconds)
    if ($len -lt $minBytes) { throw "download too small: $dest ($len < $minBytes)" }
}

# 1) OpenJDK 21 (Huawei mirror of OpenJDK GA build, windows x64 zip)
$jdkZip = Join-Path $dl 'openjdk-21-windows-x64.zip'
Fetch 'https://mirrors.huaweicloud.com/openjdk/21.0.2/openjdk-21.0.2_windows-x64_bin.zip' $jdkZip 100MB

# 2) Android cmdline-tools (latest, windows) - dl.google.com is fast here
$cmdZip = Join-Path $dl 'commandlinetools-win-latest.zip'
Fetch 'https://dl.google.com/android/repository/commandlinetools-win-16111833_latest.zip' $cmdZip 100MB

# 3) Gradle 9.9.0 (Tencent mirror; services.gradle.org is slow)
$grlZip = Join-Path $dl 'gradle-9.9.0-bin.zip'
Fetch 'https://mirrors.cloud.tencent.com/gradle/gradle-9.9.0-bin.zip' $grlZip 80MB

# ---- extract ----
"=== extracting ==="
if (-not (Test-Path (Join-Path $jdk 'bin\java.exe'))) {
    $tmp = Join-Path $root 'work\jdk-extract'
    Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $tmp | Out-Null
    & tar.exe -xf $jdkZip -C $tmp
    $inner = Get-ChildItem $tmp -Directory | Select-Object -First 1
    if (-not $inner) { throw 'JDK zip extraction produced no folder' }
    Remove-Item $jdk -Recurse -Force -ErrorAction SilentlyContinue
    Move-Item $inner.FullName $jdk
    Write-Output ("JDK -> {0}" -f $jdk)
}
if (-not (Test-Path (Join-Path $sdk 'cmdline-tools\latest\bin\sdkmanager.bat'))) {
    $tmp = Join-Path $root 'work\cmdline-extract'
    Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $tmp | Out-Null
    & tar.exe -xf $cmdZip -C $tmp
    New-Item -ItemType Directory -Force -Path (Join-Path $sdk 'cmdline-tools') | Out-Null
    $inner = Join-Path $tmp 'cmdline-tools'
    if (-not (Test-Path $inner)) { throw 'cmdline-tools extraction missing cmdline-tools folder' }
    Remove-Item (Join-Path $sdk 'cmdline-tools\latest') -Recurse -Force -ErrorAction SilentlyContinue
    Move-Item $inner (Join-Path $sdk 'cmdline-tools\latest')
    Write-Output ("cmdline-tools -> {0}" -f (Join-Path $sdk 'cmdline-tools\latest'))
}
if (-not (Test-Path (Join-Path $grl 'gradle-9.9.0\bin\gradle.bat'))) {
    $tmp = Join-Path $root 'work\gradle-extract'
    Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $tmp | Out-Null
    & tar.exe -xf $grlZip -C $tmp
    $inner = Get-ChildItem $tmp -Directory | Select-Object -First 1
    if (-not $inner) { throw 'Gradle zip extraction produced no folder' }
    Remove-Item (Join-Path $grl $inner.Name) -Recurse -Force -ErrorAction SilentlyContinue
    Move-Item $inner.FullName (Join-Path $grl $inner.Name)
    Write-Output ("Gradle -> {0}" -f (Join-Path $grl $inner.Name))
}

$jav = Join-Path $jdk 'bin\java.exe'
& $jav -version 2>&1 | ForEach-Object { Write-Output $_ }
$env:JAVA_HOME = $jdk
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$env:PATH = "$jdk\bin;$sdk\cmdline-tools\latest\bin;$sdk\platform-tools;$env:PATH"
Write-Output '=== sdkmanager version ==='
& (Join-Path $sdk 'cmdline-tools\latest\bin\sdkmanager.bat') --version
Write-Output '=== gradle version ==='
& (Join-Path $grl 'gradle-9.9.0\bin\gradle.bat') --version
Write-Output 'DOWNLOAD_AND_EXTRACT_DONE'
