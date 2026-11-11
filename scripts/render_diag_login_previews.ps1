# Render the diag-v2.1 Compose @Preview PNGs (no emulator needed).
# Why a clean copy: on Windows the Android CLI treats a Gradle "build" output dir as Bazel's
# "BUILD" marker ("Multiple build systems detected: gradle, bazel"), so we render from C:\Temp\ct-preview.
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\render_diag_login_previews.ps1
#   pwsh -NoProfile -File .\scripts\render_diag_login_previews.ps1 -OutDir artifacts\previews_diag_login
param(
    [string]$OutDir = '',
    [switch]$NoSync
)
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = (Resolve-Path (Join-Path $here '..')).Path
if (-not $OutDir) { $OutDir = Join-Path $root 'artifacts\previews_diag_v21' }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$copy = 'C:\Temp\ct-preview'
$env:JAVA_HOME = 'C:\AndroidToolchain\jdk17'
$env:ANDROID_HOME = 'C:\AndroidToolchain\sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:PATH = "C:\AndroidToolchain\jdk17\bin;C:\AndroidToolchain\sdk\cmdline-tools\latest\bin;$env:PATH"
$androidCli = 'C:\AndroidToolchain\sdk\cmdline-tools\latest\bin\android.exe'

if (-not $NoSync) {
    Write-Output "syncing project -> $copy"
    New-Item -ItemType Directory -Force -Path $copy | Out-Null
    robocopy $root $copy /MIR /XD build .gradle .kotlin artifacts design docs scripts /NFL /NDL /NJH /NJS /NP | Out-Null
    Get-ChildItem $copy -Recurse -Directory -Force -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -eq 'build' } |
        ForEach-Object { Remove-Item $_.FullName -Recurse -Force -ErrorAction SilentlyContinue }
}

Push-Location $copy
try {
    Write-Output 'android preview list app ...'
    # Windows PowerShell 5.1 turns native stderr ("Build log: ...") into a terminating
    # NativeCommandError when $ErrorActionPreference='Stop'; relax it for CLI calls and
    # rely on $LASTEXITCODE instead.
    $prevEa = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $raw = & $androidCli preview list app --format=json 2>&1 | Out-String
    $listExit = $LASTEXITCODE
    $ErrorActionPreference = $prevEa
    if ($listExit -ne 0) { throw "preview list failed ($listExit)`n$raw" }
    $start = $raw.IndexOf('{')
    $end = $raw.LastIndexOf('}')
    if ($start -lt 0 -or $end -le $start) { throw "preview list produced no JSON:`n$raw" }
    $list = $raw.Substring($start, $end - $start + 1) | ConvertFrom-Json
    $fqns = @($list.previews | ForEach-Object { $_.methodFqn } | Where-Object {
        $_ -match 'DiagScreenKt\.DiagV21(Folded|Expanded)(Light|Dark)?Preview' -or
        $_ -match 'TimetableLoginDialogKt\.TimetableLogin'
    })
    Write-Output ("found {0} target previews" -f $fqns.Count)
    if ($fqns.Count -eq 0) { throw "no diag/login previews found" }
    $fqns | ForEach-Object { Write-Output ("  - " + $_) }

    $renderArgs = @('preview', 'render', 'app', '--output', $OutDir, '--format=json')
    foreach ($fqn in $fqns) { $renderArgs += @('--fqn', $fqn) }
    Write-Output ("android preview render -> {0}" -f $OutDir)
    $prevEa = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & $androidCli @renderArgs 2>&1 | Out-String | Write-Output
    $renderExit = $LASTEXITCODE
    $ErrorActionPreference = $prevEa
    if ($renderExit -ne 0) { throw "preview render failed ($renderExit)" }
} finally {
    Pop-Location
}

Write-Output 'PREVIEW_RENDER_DONE'
Get-ChildItem $OutDir -Recurse -File -Filter '*.png' | ForEach-Object {
    Write-Output ("{0}  {1} bytes" -f $_.FullName, $_.Length)
}
