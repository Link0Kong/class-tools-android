# 开发期截图脚本（模拟器实测用）：安装 debug APK → 冷启动 → 截图。
#
# 用法：
#   .\tools\dev\shot.ps1                       # 只重启并截图 home（浅色）
#   .\tools\dev\shot.ps1 -Tab tools -Dark      # 深色 + 切到「工具」页
#   .\tools\dev\shot.ps1 -Tab me -Out shots\me.png -Scroll
#
# 说明：本脚本只做「构建产物已存在时的安装/截图」，不负责编译（避免误触发 10 分钟构建）。
param(
    [string]$Apk = "app\build\outputs\apk\debug\app-debug.apk",
    [ValidateSet("home", "tools", "me")][string]$Tab = "home",
    [switch]$Dark,
    [switch]$Scroll,
    [switch]$NoLaunch,
    [string]$Out = "",
    [int]$WaitSeconds = 4
)

$ErrorActionPreference = "Stop"
$sdk = "G:\Dev\AndroidToolchain\sdk"
$env:Path = "$sdk\platform-tools;$env:Path"
$pkg = "com.furina.classtools.debug"
$activity = "$pkg/com.furina.classtools.MainActivity"

if (-not $NoLaunch) {
    adb install -r -t $Apk | Out-Null
}

# 深/浅色
if ($Dark) {
    adb shell cmd uimode night yes | Out-Null
} else {
    adb shell cmd uimode night no | Out-Null
}

if (-not $NoLaunch) {
    adb shell am force-stop $pkg | Out-Null
    adb shell am start -n $activity | Out-Null
    Start-Sleep -Seconds $WaitSeconds
}

# 底部浮岛三档（1080x2400 / 420dpi）：home=180, tools=540, me=900；y≈2290
$x = switch ($Tab) { "home" { 180 } "tools" { 540 } "me" { 900 } }
adb shell input tap $x 2290 | Out-Null
Start-Sleep -Seconds 2

if ($Scroll) {
    adb shell input swipe 540 1700 540 900 220 | Out-Null
    Start-Sleep -Seconds 2
}

if ([string]::IsNullOrWhiteSpace($Out)) {
    $name = "$Tab-" + $(if ($Dark) { "dark" } else { "light" }) + $(if ($Scroll) { "-scrolled" } else { "" }) + ".png"
    $Out = Join-Path "shots" $name
}
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Out) | Out-Null
# adb exec-out 输出二进制：用 cmd 重定向避免 PowerShell 文本化
cmd /c "adb exec-out screencap -p > `"$Out`""
Get-Item $Out | Select-Object FullName, Length
