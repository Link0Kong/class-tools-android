# Multi-screen accessibility audit (ASCII only).
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

function Find-Node($doc, [string]$text) {
    return $doc.SelectNodes('//node') | Where-Object { [string]$_.text -eq $text } | Select-Object -First 1
}

function Tap-Text([string]$text) {
    $doc = Get-Doc 'tmp'
    $n = Find-Node $doc $text
    if (-not $n) { return $false }
    $m = [regex]::Match([string]$n.bounds, '\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
    if (-not $m.Success) { return $false }
    $x = [int](([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2)
    $y = [int](([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2)
    & $adb -s emulator-5554 shell input tap $x $y | Out-Null
    Start-Sleep -Milliseconds 1200
    return $true
}

function TextContains($node, [string]$needle) {
    $t = [string]$node.text
    $d = [string]$node.'content-desc'
    return ($t.Contains($needle) -or $d.Contains($needle))
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
    $texts = (@($nodes | ForEach-Object { [string]$_.text } | Where-Object { $_.Trim() } | Select-Object -First 6) -join ' / ')
    "[$name] clickable=$($clickable.Count) unlabeled=$($suspicious.Count) topText=$texts"
    foreach ($s in ($suspicious | Select-Object -First 10)) { "   $s" }
}

# ensure workbench
$doc = Get-Doc 'boot'
$hasDev = ($doc.SelectNodes('//node') | Where-Object { [string]$_.text -eq '开发预览' }).Count -gt 0
if ($hasDev) { $null = Tap-Text '开发预览'; Start-Sleep -Seconds 4 }
$null = Tap-Text '工作台'; Start-Sleep -Milliseconds 800
Audit '10-workbench'
$null = Tap-Text '工具'; Start-Sleep -Milliseconds 800
Audit '11-all-tools'
$null = Tap-Text '我的'; Start-Sleep -Milliseconds 800
Audit '12-profile'
$null = Tap-Text '工具'; Start-Sleep -Milliseconds 800

# tool pages: draw / notices / vote / timetable
if (Tap-Text '抽签') { Audit '13-draw'; & $adb -s emulator-5554 shell input keyevent KEYCODE_BACK | Out-Null; Start-Sleep -Milliseconds 800 }
if (Tap-Text '通知') { Audit '14-notices'; & $adb -s emulator-5554 shell input keyevent KEYCODE_BACK | Out-Null; Start-Sleep -Milliseconds 800 }
if (Tap-Text '投票') { Audit '15-vote'; & $adb -s emulator-5554 shell input keyevent KEYCODE_BACK | Out-Null; Start-Sleep -Milliseconds 800 }
if (Tap-Text '课表') { Audit '16-timetable'; & $adb -s emulator-5554 shell input keyevent KEYCODE_BACK | Out-Null; Start-Sleep -Milliseconds 800 }
"done"
