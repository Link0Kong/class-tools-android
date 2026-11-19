# 设计令牌硬编码自查（docs/DESIGN_SYSTEM.md §7）
# 统计「业务 UI 层」里绕过设计令牌的魔法值；允许清单 = 唯一令牌源 ui/theme/ 与 @Preview 的 ui/preview/。
param(
    [string]$Root = "app/src/main/kotlin/com/furina/classtools",
    [switch]$Verbose
)
$ui = Join-Path $Root "ui"
$files = Get-ChildItem -Path $ui -Recurse -File -Filter *.kt |
    Where-Object { $_.FullName -notmatch '[/\\]ui[/\\]theme[/\\]' -and $_.FullName -notmatch '[/\\]ui[/\\]preview[/\\]' }
$rules = [ordered]@{
    "Color(0x"                 = 'Color\(0x[0-9A-Fa-f]{6,8}\)'
    "Color.<literal>"          = 'Color\.(White|Black|Red|Green|Blue|Gray|Yellow|Cyan|Magenta)\b'
    "RoundedCornerShape(N.dp)" = 'RoundedCornerShape\(\s*[0-9]'
    "fontSize = N.sp"          = 'fontSize\s*=\s*[0-9]'
    "N.sp literal"             = '(?<![\w.])\d+(\.\d+)?\.sp\b'
}
$total = 0
foreach ($name in $rules.Keys) {
    $pattern = $rules[$name]
    $hits = Select-String -Path $files.FullName -Pattern $pattern |
        Where-Object { $_.Line -notmatch '^\s*(//|\*)' -and $_.Line -notmatch '设计令牌-允许' }
    "== {0,-26} {1}" -f $name, $hits.Count
    $total += $hits.Count
    if ($Verbose) { $hits | ForEach-Object { "   {0}:{1}: {2}" -f $_.Filename, $_.LineNumber, $_.Line.Trim() } }
}
"TOTAL UI hardcode = $total (files scanned: $($files.Count))"
if ($total -ne 0) { exit 1 }
