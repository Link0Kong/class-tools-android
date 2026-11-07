# 渲染 Compose @Preview 为 PNG（Android CLI，无需模拟器/IDE）。
# 背景：
#  - 新 Android CLI 的 `preview render` 需要：① 项目内有 gradlew.bat；② reference 用相对 Gradle 路径（"app"）；
#  - 该项目根目录存在 gradle 的 "build" 输出目录时，CLI 在 Windows（大小写不敏感）会把 build 误判成 Bazel 的 "BUILD" 标记，
#    报 "Multiple build systems detected: gradle, bazel"。因此本脚本在 C:\Temp 的干净副本里渲染：
#    robocopy 同步源码（排除 build/.gradle/.kotlin/artifacts 等），删掉副本里的 build 目录，再执行 render。
param(
    [string]$OutDir = '',
    [switch]$NoSync
)
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = (Resolve-Path (Join-Path $here '..')).Path
if (-not $OutDir) { $OutDir = Join-Path $root 'artifacts\previews' }
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
    # 清掉可能残留的 build 输出（Windows 大小写不敏感会触发 Bazel 误判）
    Get-ChildItem $copy -Recurse -Directory -Force -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -eq 'build' } |
        ForEach-Object { Remove-Item $_.FullName -Recurse -Force -ErrorAction SilentlyContinue }
}

Push-Location $copy
try {
    Write-Output 'android preview list app ...'
    $raw = & $androidCli preview list app --format=json 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0) { throw "preview list failed ($LASTEXITCODE)`n$raw" }
    # CLI 会在 JSON 前后混入进度/日志行，截取第一个 '{' 到最后一个 '}'。
    $start = $raw.IndexOf('{')
    $end = $raw.LastIndexOf('}')
    if ($start -lt 0 -or $end -le $start) { throw "preview list produced no JSON:`n$raw" }
    $list = $raw.Substring($start, $end - $start + 1) | ConvertFrom-Json
    $fqns = @($list.previews | ForEach-Object { $_.methodFqn })
    Write-Output ("found {0} previews" -f $fqns.Count)
    if ($fqns.Count -eq 0) { throw "no previews found" }

    $renderArgs = @('preview', 'render', 'app', '--output', $OutDir, '--format=json')
    foreach ($fqn in $fqns) { $renderArgs += @('--fqn', $fqn) }
    Write-Output ("android preview render -> {0}" -f $OutDir)
    & $androidCli @renderArgs
    if ($LASTEXITCODE -ne 0) { throw "preview render failed ($LASTEXITCODE)" }
} finally {
    Pop-Location
}
Write-Output 'PREVIEW_RENDER_DONE'
