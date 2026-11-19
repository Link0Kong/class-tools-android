# Extended accessibility audit: tool pages + profile subpages (ASCII wrapper, UTF8 labels).
$ErrorActionPreference = 'Stop'
$adb = 'G:\Dev\AndroidToolchain\sdk\platform-tools\adb.exe'
$outDir = 'C:\Users\Ko_Li\.openclaw\workspace\android-class-tools\artifacts\a11y-audit'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

function Get-Doc([string]$name) {
    & $adb -s emulator-5554 shell uiautomator dump /sdcard/a11y.xml | Out-Null
    $local = Join-Path $outDir "$name.xml"
    & $adb -s emulator-5554 pull /sdcard/a11y.xml $local 2>$null | Out-Null
    return [xml](Get-Content $local -Raw -Encoding UTF8)
}

function Find-Exact($doc, [string]$text) {
    return $doc.SelectNodes('//node') | Where-Object { [string]$_.text -eq $text } | Select-Object -First 1
}

function Tap-Node($node) {
    if (-not $node) { return $false }
    $m = [regex]::Match([string]$node.bounds, '\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
    if (-not $m.Success) { return $false }
    $x = [int](([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2)
    $y = [int](([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2)
    & $adb -s emulator-5554 shell input tap $x $y | Out-Null
    Start-Sleep -Milliseconds 1300
    return $true
}

function Tap-TextScrolled([string]$text, [int]$tries = 6) {
    for ($i = 0; $i -lt $tries; $i++) {
        $doc = Get-Doc 'nav'
        $n = Find-Exact $doc $text
        if ($n) { return (Tap-Node $n) }
        & $adb -s emulator-5554 shell input swipe 540 1750 540 900 300 | Out-Null
        Start-Sleep -Milliseconds 600
    }
    return $false
}

function Audit([string]$name) {
    $doc = Get-Doc $name
    $nodes = $doc.SelectNodes('//node')
    $clickable = @($nodes | Where-Object { [string]$_.clickable -eq 'true' })
    $suspicious = @()
    foreach ($n in $clickable) {
        $t = [string]$n.text
        $d = [string]$n.'content-desc'
        if ($t.Trim() -or $d.Trim()) { continue }
        $desc = @()
        foreach ($c in $n.SelectNodes('.//node')) {
            $ct = [string]$c.text
            $cd = [string]$c.'content-desc'
            if ($ct.Trim()) { $desc += $ct.Trim() }
            if ($cd.Trim()) { $desc += $cd.Trim() }
        }
        if (($desc -join ' ').Trim()) { continue }
        $suspicious += ("{0} | {1}" -f [string]$n.class, [string]$n.bounds)
    }
    "[$name] clickable=$($clickable.Count) unlabeled=$($suspicious.Count)"
    foreach ($s in ($suspicious | Select-Object -First 8)) { "   $s" }
}

function Back() {
    & $adb -s emulator-5554 shell input keyevent KEYCODE_BACK | Out-Null
    Start-Sleep -Milliseconds 900
}

# Ensure workbench
$doc = Get-Doc 'boot'
if ((Find-Exact $doc '开发预览')) { $null = Tap-TextScrolled '开发预览' 2; Start-Sleep -Seconds 4 }
$null = Tap-TextScrolled '工具' 2

$tools = @('电费', '名单', '演示', '体测', '诊断', '作业', '分组', '水量')
$idx = 20
foreach ($tool in $tools) {
    $null = Tap-TextScrolled '工具' 2
    if (Tap-TextScrolled $tool 6) {
        Start-Sleep -Milliseconds 800
        Audit ("{0}-tool-{1}" -f $idx, $tool)
        Back
    } else {
        "[{0}-tool-{1}] not found" -f $idx, $tool
    }
    $idx++
}

# Profile subpages
$null = Tap-TextScrolled '我的' 2
Audit '30-profile'
foreach ($entry in @('数据与隐私', '权限管理', '提醒设置', '反馈建议', '使用说明')) {
    $null = Tap-TextScrolled '我的' 2
    if (Tap-TextScrolled $entry 6) {
        Start-Sleep -Milliseconds 900
        Audit ("31-profile-{0}" -f $entry)
        Back
    } else {
        "[31-profile-{0}] not found" -f $entry
    }
}
"done"
