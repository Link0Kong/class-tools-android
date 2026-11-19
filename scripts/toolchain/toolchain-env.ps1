# Class Tools Android - toolchain environment setup (dot-source this file).
#
# Usage (PowerShell):
#   . G:\ClassTools\android-class-tools\scripts\toolchain\toolchain-env.ps1
#   . G:\ClassTools\android-class-tools\scripts\toolchain\toolchain-env.ps1 -Jdk 17
#
# Sets JAVA_HOME / ANDROID_HOME / ANDROID_SDK_ROOT / PATH and prints a short self-check.
# NOTE: keep this file pure ASCII - Windows PowerShell 5.1 parses non-BOM .ps1 as ANSI
#       and breaks on non-ASCII characters (known issue in this repo's notes).
param(
    [ValidateSet('17', '21')]
    [string]$Jdk = '21'
)

$toolRoot   = 'G:\Dev\AndroidToolchain'
$jdkHome    = if ($Jdk -eq '17') { Join-Path $toolRoot 'jdk17' } else { Join-Path $toolRoot 'jdk' }
$sdkHome    = Join-Path $toolRoot 'sdk'
$gradleHome = Join-Path $toolRoot 'gradle\gradle-9.9.0'

foreach ($p in @($jdkHome, $sdkHome, $gradleHome)) {
    if (-not (Test-Path $p)) {
        Write-Error "Missing toolchain component: $p (run scripts/setup_toolchain_download.ps1 first)"
        return
    }
}

$env:JAVA_HOME        = $jdkHome
$env:ANDROID_HOME     = $sdkHome
$env:ANDROID_SDK_ROOT = $sdkHome
$env:PATH = "$jdkHome\bin;$sdkHome\cmdline-tools\latest\bin;$sdkHome\platform-tools;$sdkHome\emulator;$gradleHome\bin;$env:PATH"

$javaLine = (& "$jdkHome\bin\java.exe" -version 2>&1 | Select-Object -First 1)
$androidJar = Join-Path $sdkHome 'platforms\android-37.0\android.jar'
$sdkLine = if (Test-Path $androidJar) { "OK ($androidJar)" } else { "MISSING platforms/android-37.0" }

Write-Host "JAVA_HOME     = $env:JAVA_HOME"
Write-Host "ANDROID_HOME  = $env:ANDROID_HOME"
Write-Host "gradle        = $gradleHome\bin\gradle.bat"
Write-Host "java          = $javaLine"
Write-Host "compileSdk 37 = $sdkLine"
