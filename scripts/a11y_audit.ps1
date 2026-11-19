# Accessibility audit helper (ASCII only).
$ErrorActionPreference = 'Stop'
$adb = 'G:\Dev\AndroidToolchain\sdk\platform-tools\adb.exe'
$outDir = 'C:\Users\Ko_Li\.openclaw\workspace\android-class-tools\artifacts\a11y-audit'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

function Get-Dump([string]$name) {
    & $adb -s emulator-5554 shell uiautomator dump /sdcard/a11y.xml | Out-Null
    $local = Join-Path $outDir "$name.xml"
    & $adb -s emulator-5554 pull /sdcard/a11y.xml $local 2>$null | Out-Null
    return [xml](Get-Content $local -Raw -Encoding UTF8)
}

function Get-NodeLabel($node) {
    $t = [string]$node.text
    $d = [string]$node.'content-desc'
    if ($t.Trim()) { return $t }
    if ($d.Trim()) { return $d }
    return ''
}

function Get-DescendantText($node) {
    $parts = @()
    foreach ($c in $node.SelectNodes('.//node')) {
        $t = [string]$c.text
        $d = [string]$c.'content-desc'
        if ($t.Trim()) { $parts += $t.Trim() }
        if ($d.Trim()) { $parts += $d.Trim() }
    }
    return ($parts -join ' ')
}

function Audit([string]$name) {
    $doc = Get-Dump $name
    $nodes = $doc.SelectNodes('//node')
    $clickable = @($nodes | Where-Object { [string]$_.clickable -eq 'true' })
    $suspicious = @()
    foreach ($n in $clickable) {
        $own = Get-NodeLabel $n
        if ($own) { continue }
        $descs = Get-DescendantText $n
        if ($descs.Trim()) { continue }
        $cls = [string]$n.class
        $bounds = [string]$n.bounds
        $rid = [string]$n.'resource-id'
        $suspicious += ("{0} | {1} | {2}" -f $cls, $bounds, $rid)
    }
    "[$name] clickable=$($clickable.Count) unlabeled=$($suspicious.Count)"
    foreach ($s in ($suspicious | Select-Object -First 12)) { "   $s" }
}

Audit '01-identity-or-workbench'
