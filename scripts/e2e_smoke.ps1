<#
.SYNOPSIS
    Android emulator E2E smoke test for ClassTools (text-driven, screenshot per step).

.DESCRIPTION
    Runs on the ct-api37 emulator (Android 17 / API 37):
      1. optionally build Debug APK (gradle :app:assembleDebug)
      2. optionally adb install -r the APK
      3. reset app data (so the identity gate shows up), launch, then walk:
         identity -> Dev Preview -> Workbench -> All -> Draw
         -> STEP1 -> STEP2 -> STEP3 -> export image -> back -> workbench
      4. screenshot every step into artifacts/e2e/
      5. regression checks: no FATAL/ANR in logcat, exported PNG appears in gallery
      6. writes artifacts/e2e/summary.json

    All UI actions are TEXT driven (uiautomator dump -> node text/bounds -> input tap),
    no hard-coded screen coordinates, so it survives minor layout changes.

    The script file itself is pure ASCII (Windows PowerShell 5.1 safety); the Chinese
    labels used to find UI nodes are stored as base64 UTF-8 and decoded at runtime.

.PARAMETER Build
    Run `gradle :app:assembleDebug` first. If the APK is missing this happens anyway.

.PARAMETER Install
    Force `adb install -r` of the built APK. Without it the already installed app is used.

.PARAMETER Screens
    Screenshot output directory. Default: <repo>\artifacts\e2e

.PARAMETER KeepData
    Do not `pm clear` app data before launch (keeps the saved debug session).

.PARAMETER BootTimeoutSec
    Max seconds to wait for the emulator to boot when it is not running. Default 180.

.EXAMPLE
    pwsh -File .\scripts\e2e_smoke.ps1 -Build -Install
    full run: build + install + walkthrough + screenshots + regression checks

.EXAMPLE
    pwsh -File .\scripts\e2e_smoke.ps1
    fast re-run against the currently installed APK

.NOTES
    Exit code 0 = all steps PASS, 1 = failure (prints logcat tail + UI dump path).
#>
param(
    [switch]$Build,
    [switch]$Install,
    [string]$Screens = '',
    [switch]$KeepData,
    [int]$BootTimeoutSec = 180
)

$ErrorActionPreference = 'Stop'

# ---------------------------------------------------------------- paths / consts
$Root    = Split-Path -Parent $PSScriptRoot
$Adb     = 'C:\AndroidToolchain\sdk\platform-tools\adb.exe'
$Emu     = 'C:\AndroidToolchain\sdk\emulator\emulator.exe'
$Gradle  = 'C:\AndroidToolchain\gradle\gradle-9.9.0\bin\gradle.bat'
$Jdk17   = 'C:\AndroidToolchain\jdk17'
$SdkRoot = 'C:\AndroidToolchain\sdk'
$Pkg     = 'com.furina.classtools.debug'
$Act     = 'com.furina.classtools.debug/com.furina.classtools.MainActivity'
$Apk     = Join-Path $Root 'app\build\outputs\apk\debug\app-debug.apk'
$AvdName = 'ct-api37'

if ([string]::IsNullOrWhiteSpace($Screens)) { $Screens = Join-Path $Root 'artifacts\e2e' }
$Screens = [IO.Path]::GetFullPath($Screens)
New-Item -ItemType Directory -Force -Path $Screens | Out-Null
# keep the output directory clean so artifacts always reflect the latest run
Get-ChildItem $Screens -Filter '*.png' -File -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue
Get-ChildItem $Screens -Filter 'failure_*' -File -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue
Get-ChildItem $Screens -Filter 'nav_probe_fail_*' -File -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue
Get-ChildItem $Screens -Filter 'summary.json' -File -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue
$TmpUi   = Join-Path $Screens '_ui_dump.xml'
$LogFile = Join-Path $Screens 'run.log'

# UI labels (base64 of UTF-8 Chinese, keeps this file ASCII)
function U8([string]$b64) { [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($b64)) }
$L = @{
    DevPreview     = U8 '5byA5Y+R6aKE6KeI'                            # Dev Preview (identity)
    IdentityTitle  = U8 '6Lqr5Lu95qC46aqM'                           # identity gate title
    QuickTools     = U8 '5bi455So5bel5YW3'                           # quick tools section
    Today          = U8 '5LuK5pel'                                   # today section
    AllTab         = U8 '5YWo6YOo'                                   # bottom nav: all
    AllToolsTitle  = U8 '5YWo6YOo5bel5YW3'                           # all tools title
    Draw           = U8 '5oq9562+'                                   # draw tool card
    DrawParams     = U8 '5oq9562+5Y+C5pWw'                            # draw params card
    NextStep       = U8 '5LiL5LiA5q2l77ya6aKE6KeI5YCZ6YCJ'           # next: preview candidates
    Candidates     = U8 '5YCZ6YCJ5ZCN5Y2V'                           # candidate list
    StartDraw      = U8 '5byA5aeL5oq9562+'                           # start draw
    DrawResult     = U8 '5oq9562+57uT5p6c'                           # draw result
    Export         = U8 '5a+85Ye65oq9562+5Zu+'                      # export draw image button
    Workbench      = U8 '5bel5L2c5Y+w'                               # bottom nav: workbench
    Edit           = U8 '57yW6L6R'                                   # home: edit
    EditTitle      = U8 '6YCJ5oup6aaW6aG15b+r5o23'                   # quick tools editor title
    EditSelected   = U8 '5bey6YCJ'                                   # selected counter prefix
    Done           = U8 '5a6M5oiQ'                                   # done button
    Hw             = U8 '5L2c5Lia'                                   # homework tool / title
    Nt             = U8 '6YCa55+l'                                   # notices tool / title
    Tt             = U8 '6K++6KGo'                                   # timetable tool / title
    Power          = U8 '55S16LS5'                                   # power tool / title
    Water          = U8 '5rC06YeP'                                   # water tool / title
    HwEmpty        = U8 '5pqC5peg5L2c5Lia'                           # no homework
    HwEmptyFilter  = U8 '5rKh5pyJ56ym5ZCI5p2h5Lu255qE5L2c5Lia'       # no matching homework
    HwError        = U8 '5L2c5Lia5Yqg6L295aSx6LSl'                    # homework load failed
    HwLoading      = U8 '5Yqg6L295L2c5Lia'                           # loading homework
    NtEmpty        = U8 '5pqC5peg6YCa55+l'                           # no notices
    NtEmptyPending = U8 '5rKh5pyJ5pyq5aSE55CG6YCa55+l'               # no pending notices
    NtEmptyPinned  = U8 '5rKh5pyJ572u6aG26YCa55+l'                   # no pinned notices
    NtError        = U8 '6YCa55+l5Yqg6L295aSx6LSl'                    # notices load failed
    NtLoading      = U8 '5Yqg6L296YCa55+l'                           # loading notices
    TtEmpty        = U8 '6K++6KGo6L+Y5piv56m655qE'                   # timetable still empty
    TtEmptyHint    = U8 '5pWZ5Yqh6K++6KGo5ZCM5q2l5ZCO5Lya6Ieq5Yqo5Ye6546w'  # sync hint
    TtError        = U8 '6K++6KGo5Yqg6L295aSx6LSl'                    # timetable load failed
    TtLoading      = U8 '6K++6KGo5Yqg6L295Lit'                       # loading timetable
    PowerEmpty     = U8 '5YWz5rOo5LiA5Liq5a6/6IiN'                   # watch a dorm room
    PowerLoading   = U8 '55S16YeP5Yqg6L295Lit'                       # loading power
    PowerError     = U8 '55S16LS55Yqg6L295aSx6LSl'                    # power load failed
    PlaceholderPill = U8 '5Y6f55Sf54mI5byA5Y+R5Lit'                  # placeholder: native version in progress
    PlaceholderPlan = U8 '6K6h5YiS5Yqf6IO9'                          # placeholder: planned features
    MyTab          = U8 '5oiR55qE'                                   # bottom nav: my/profile
    Group          = U8 '5YiG57uE'                                   # group tool / title
    Fit            = U8 '5L2T5rWL'                                   # fitness tool / title
    GroupStep1     = U8 'MSDphY3nva4='                               # group screen: step 1 config
    FitHistory     = U8 '5Y6G5Y+y6K6w5b2V'                           # fitness screen: history
    AuthError      = U8 '6K+35YWI5a6M5oiQ6Lqr5Lu96aqM6K+B'          # please verify identity first
    NtTitle        = U8 '54+t57qn6YCa55+l'                           # notices page top bar: class notices
    PowerTitle     = U8 '5a6/6IiN55S16LS5'                           # power page top bar: dorm power
    FitTerms       = U8 '5pyq5aGr6aG55oyJ'                           # fitness: "missing items count as 0"
    WaterTitle     = U8 '5a6/6IiN5rC06YeP'                           # water page top bar: dorm water
    WaterEmpty     = U8 '6L+Y5rKh5pyJ5YWz5rOo5a6/6IiN'               # water empty: no dorm followed yet
    WaterLoading   = U8 '5Yqg6L295rC06YeP5pWw5o2u'                   # loading water data
    WaterError     = U8 '5rC06YeP5pWw5o2u5LiN5Y+v55So'               # water data unavailable
    Roster         = U8 '5ZCN5YaM'                                   # roster tool card
    RosterTitle    = U8 '5ZCN5YaMIMK3IOeCueWQjQ=='                   # roster page top bar: roster / roll call
    RosterTabRoll  = U8 '54K55ZCN'                                   # roster tab: roll call
    RosterLoading  = U8 '5Yqg6L2954+t57qn5ZCN5Y2V'                   # loading class roster
    RosterEmpty    = U8 '5ZCN5Y2V5bCa5pyq5ZCM5q2l'                   # roster not synced yet
    RosterError    = U8 '5ZCN5YaM5Yqg6L295aSx6LSl'                   # roster load failed
    RollEmpty      = U8 '6L+Y5rKh5pyJ54K55ZCN5ZCN5Y2V'               # no roll-call list yet
    RollDone       = U8 '5pys6L2u5a6M5oiQ'                           # roll call round done
    ProfileMark    = U8 '5LqR5ZCM5q2l'                               # profile page marker: cloud sync row
    Consent        = U8 '5oiR5bey6ZiF6K+75bm25ZCM5oSP'               # identity: consent text
    SidField       = U8 '5a2m5Y+3'                                   # identity: student id field label
    NameField      = U8 '5aeT5ZCN'                                   # identity: name field label
    SubmitIdentity = U8 '6L+b5YWl5bel5YW356uZ'                       # identity: submit button
    AuthFailMark   = U8 '5LiN5Zyo54+t57qn5ZCN5Y2V'                   # identity error: not in class roster
    AuthFailAlt    = U8 '5qC46aqM5aSx6LSl'                           # identity error fallback: verify failed
    Manage         = U8 '566h55CG'                                   # profile: data & privacy row action
    PrivacyTitle   = U8 '5pWw5o2u5LiO6ZqQ56eB'                       # data & privacy page title
    PrivacyNote    = U8 '6ZqQ56eB6K+05piO'                           # data & privacy: privacy notes heading
    ClearCloud     = U8 '5riF6Zmk5LqR56uv5pWw5o2u'                   # data & privacy: clear cloud data
    ConfirmClear   = U8 '56Gu6K6k5riF6Zmk'                           # confirm dialog: confirm clear
    Cancel         = U8 '5Y+W5raI'                                   # confirm dialog: cancel
    ExportImg      = U8 '5a+85Ye65oq9562+5Zu+'                      # draw result: export image
    ExportSheet    = U8 '5a+85Ye657uT5p6c5Y2V'                       # draw result: export result sheet
    UseResultGroup = U8 '55So5q2k57uT5p6c5YiG57uE'                   # draw result: use result for grouping
    GroupPrevSrc   = U8 '5LiK5LiA5q2l5oq9562+5bqP5Y+3'              # group page: previous draw source
    PrivacyLoadErr = U8 '5pWw5o2u5qaC6KeI5Yqg6L295aSx6LSl'           # privacy page: overview load failed
    PrivacyAuth    = U8 '6K+35YWI5a6M5oiQ6Lqr5Lu96aqM6K+B'          # privacy page: auth required empty state
    PrivacyLoading = U8 '5Yqg6L295pWw5o2u5qaC6KeI'                   # privacy page: loading overview
    TtNoData       = U8 '5pqC5peg6K++6KGo5pWw5o2u'                   # timetable: no data
    TtNoDataHint   = U8 '5pWZ5Yqh5b+r54Wn5Li656m6'                   # timetable: snapshot empty hint
    TtNetError     = U8 '572R57uc5byC5bi4'                           # timetable: network error
    TtParseError   = U8 '6K++6KGo5pWw5o2u5qC85byP5byC5bi4'           # timetable: parse error
    WallpaperColor = U8 '5aOB57q45Y+W6Imy'                           # profile: wallpaper color (Material You) switch row
    About          = U8 '5YWz5LqO'                                   # profile: about card
    Settings       = U8 '6K6+572u'                                   # all-tools: settings card
    Appearance     = U8 '5aSW6KeC'                                   # profile: appearance card
    VersionLabel   = U8 '54mI5pys'                                   # profile: version row label
}

$script:Steps   = New-Object System.Collections.Generic.List[object]
$script:Shots   = New-Object System.Collections.Generic.List[object]
$script:Log     = New-Object System.Collections.Generic.List[string]
$script:NavProbe = $null
$script:AppearanceState = $null
$script:Started = Get-Date

# ---------------------------------------------------------------- tiny helpers
function Write-Line([string]$Text, [string]$Color = 'Gray') {
    $line = "[{0:HH:mm:ss}] {1}" -f (Get-Date), $Text
    $script:Log.Add($line)
    Write-Host $line -ForegroundColor $Color
}

function Add-Step([string]$Name, [string]$Status, [string]$Detail, [long]$Ms) {
    $script:Steps.Add([pscustomobject]@{ step = $Name; status = $Status; detail = $Detail; ms = $Ms })
    $color = switch ($Status) { 'PASS' { 'Green' } 'WARN' { 'Yellow' } default { 'Red' } }
    Write-Line ("[{0}] {1} ({2} ms){3}" -f $Status, $Name, $Ms, $(if ($Detail) { " - $Detail" } else { '' })) $color
}

function Invoke-Adb([string[]]$AdbArgs) {
    $out = & $Adb @AdbArgs 2>&1
    return ($out | Out-String).TrimEnd()
}

function Get-UiDump {
    # uiautomator can fail while an animation is running -> retry
    for ($i = 0; $i -lt 4; $i++) {
        $null = Invoke-Adb @('shell', 'uiautomator', 'dump', '/sdcard/e2e_ui.xml')
        Remove-Item $TmpUi -Force -ErrorAction SilentlyContinue
        $null = Invoke-Adb @('pull', '/sdcard/e2e_ui.xml', $TmpUi)
        if (Test-Path $TmpUi) {
            try {
                $doc = [xml](Get-Content $TmpUi -Raw -Encoding UTF8)
                if ($doc -and $doc.SelectNodes('//node').Count -gt 3) { return $doc }
            } catch { }
        }
        Start-Sleep -Milliseconds 400
    }
    return $null
}

function Find-UiNode($Doc, [string]$Label, [switch]$Exact) {
    if (-not $Doc) { return $null }
    foreach ($n in $Doc.SelectNodes('//node')) {
        $t = [string]$n.text
        $d = [string]$n.'content-desc'
        if ($Exact) {
            if ($t -eq $Label -or $d -eq $Label) { return $n }
        } else {
            if ($t -and $t.Contains($Label)) { return $n }
            if ($d -and $d.Contains($Label)) { return $n }
        }
    }
    return $null
}

function Get-NodeCenter($Node) {
    $m = [regex]::Match([string]$Node.bounds, '\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
    if (-not $m.Success) { return $null }
    $x = ([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2
    $y = ([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2
    return @([int]$x, [int]$y)
}

function Wait-UiText([string]$Label, [int]$TimeoutSec = 20, [switch]$Exact) {
    $sw = [Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $TimeoutSec) {
        $doc = Get-UiDump
        $node = Find-UiNode -Doc $doc -Label $Label -Exact:$Exact
        if ($node) { return $node }
        # 800ms poll: every uiautomator dump spawns a Java process on the emulator,
        # polling too fast saturates the CPU and makes input taps get dropped.
        Start-Sleep -Milliseconds 800
    }
    return $null
}

function Wait-UiAny([string[]]$Labels, [int]$TimeoutSec = 25) {
    $sw = [Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $TimeoutSec) {
        $doc = Get-UiDump
        foreach ($lbl in $Labels) {
            $node = Find-UiNode -Doc $doc -Label $lbl
            if ($node) { return $node }
        }
        Start-Sleep -Milliseconds 800
    }
    return $null
}

function Scroll-Up { $null = Invoke-Adb @('shell', 'input', 'swipe', '540', '1750', '540', '850', '320'); Start-Sleep -Milliseconds 500 }
function Scroll-Down { $null = Invoke-Adb @('shell', 'input', 'swipe', '540', '900', '540', '1750', '320'); Start-Sleep -Milliseconds 500 }

function Get-ScreenKind {
    $doc = Get-UiDump
    if (-not $doc) { return 'UNKNOWN' }
    $joined = ((@($doc.SelectNodes('//node') | ForEach-Object { [string]$_.text } | Where-Object { $_ })) -join ' | ')
    if ($joined.Contains($L.EditTitle)) { return 'HOME_EDIT' }
    if ($joined.Contains($L.IdentityTitle)) { return 'IDENTITY' }
    if ($joined.Contains($L.QuickTools)) { return 'HOME' }
    if ($joined.Contains($L.ProfileMark)) { return 'PROFILE' }    # All-tools grid may be scrolled, so the "all tools" title is not always on screen.
    # Fall back to the triple bottom-nav labels (only top-level pages have them).
    if ($joined.Contains($L.Workbench) -and $joined.Contains($L.AllTab) -and $joined.Contains($L.MyTab)) { return 'ALL' }
    return 'OTHER'
}

function Wait-ScreenKind([string]$Kind, [int]$TimeoutSec = 15) {
    $sw = [Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $TimeoutSec) {
        if ((Get-ScreenKind) -eq $Kind) { return $true }
        Start-Sleep -Milliseconds 800
    }
    return $false
}

# Top-level navigation helpers with retries + fallback routes. Measured on this
# emulator/app build: HOME->ALL bottom-tab tap works; ALL->HOME bottom-tab tap is
# ignored by the app (system BACK works); adb taps are sometimes dropped while
# uiautomator dumps run. So every nav is verified and retried.
function Go-AllTools {
    if ((Get-ScreenKind) -eq 'ALL') { return }
    for ($i = 1; $i -le 2; $i++) {
        Tap-UiText -Label $L.AllTab -Exact -TimeoutSec 8
        if (Wait-ScreenKind 'ALL' 6) { return }
        Write-Line ("  nav tap all-tab did not reach all tools (attempt {0})" -f $i)
    }
    Write-Line '  fallback: My tab -> All tab'
    Tap-UiText -Label $L.MyTab -Exact -TimeoutSec 8
    Start-Sleep -Milliseconds 800
    for ($i = 1; $i -le 2; $i++) {
        Tap-UiText -Label $L.AllTab -Exact -TimeoutSec 8
        if (Wait-ScreenKind 'ALL' 6) { return }
    }
    throw 'cannot reach all tools page (direct + fallback failed)'
}

function Go-Workbench {
    if ((Get-ScreenKind) -eq 'HOME') { return }
    if ((Get-ScreenKind) -eq 'ALL') {
        Write-Line '  ALL -> HOME via system BACK (tab tap is a known app rough edge)'
        $null = Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_BACK')
        Start-Sleep -Milliseconds 1000
        if (Wait-ScreenKind 'HOME' 8) { return }
    }
    for ($i = 1; $i -le 2; $i++) {
        Tap-UiText -Label $L.Workbench -Exact -TimeoutSec 8
        if (Wait-ScreenKind 'HOME' 6) { return }
        Write-Line ("  nav tap workbench did not reach home (attempt {0})" -f $i)
    }
    Write-Line '  fallback: My tab -> Workbench tab'
    Tap-UiText -Label $L.MyTab -Exact -TimeoutSec 8
    Start-Sleep -Milliseconds 800
    for ($i = 1; $i -le 2; $i++) {
        Tap-UiText -Label $L.Workbench -Exact -TimeoutSec 8
        if (Wait-ScreenKind 'HOME' 6) { return }
    }
    if ((Get-ScreenKind) -eq 'ALL') {
        Write-Line '  fallback: system BACK from all tools'
        $null = Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_BACK')
        Start-Sleep -Milliseconds 1000
        if (Wait-ScreenKind 'HOME' 8) { return }
    }
    throw 'cannot reach workbench (direct + fallbacks failed)'
}

function Tap-UiText([string]$Label, [int]$TimeoutSec = 20, [switch]$Exact, [int]$ScrollTries = 3) {
    $node = Wait-UiText -Label $Label -TimeoutSec $TimeoutSec -Exact:$Exact
    while (-not $node -and $ScrollTries -gt 0) {
        Write-Line "  text '$Label' not visible, scrolling up ($ScrollTries left)"
        Scroll-Up
        $ScrollTries--
        $node = Wait-UiText -Label $Label -TimeoutSec 6 -Exact:$Exact
    }
    if (-not $node) { throw "UI text not found: '$Label'" }
    $pt = Get-NodeCenter $node
    if (-not $pt) { throw "cannot parse bounds for '$Label': $($node.bounds)" }
    # small settle before tapping - input events sent while the UI thread is busy are dropped
    Start-Sleep -Milliseconds 300
    $null = Invoke-Adb @('shell', 'input', 'tap', "$($pt[0])", "$($pt[1])")
    Write-Line "  tap '$Label' at ($($pt[0]),$($pt[1]))"
    Start-Sleep -Milliseconds 800
}

# Type text into an input field identified by its label text (uses adb input text).
# Closes the soft keyboard afterwards: an open IME covers the form, so later taps
# would hit keyboard keys instead of app buttons (observed: submit tap typed 'e').
function Tap-UiTextInput([string]$Label, [string]$Value) {
    Tap-UiText -Label $Label -Exact -TimeoutSec 10
    Start-Sleep -Milliseconds 400
    $null = Invoke-Adb @('shell', 'input', 'text', $Value)
    Start-Sleep -Milliseconds 500
    $ime = Invoke-Adb @('shell', 'dumpsys', 'input_method')
    if ($ime -match 'mInputShown=true') {
        $null = Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    } else {
        $null = Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_ESCAPE')
    }
    Start-Sleep -Milliseconds 800
}

# Tick the consent checkbox on the identity page (find by accessibility class/checkable).
function Tap-ConsentCheckbox {
    $doc = Get-UiDump
    if (-not $doc) { throw 'UI dump failed while looking for consent checkbox' }
    $node = $null
    foreach ($n in $doc.SelectNodes('//node')) {
        if (([string]$n.class) -match 'CheckBox') { $node = $n; break }
        if (([string]$n.checkable) -eq 'true') { $node = $n; break }
    }
    if ($node) {
        $pt = Get-NodeCenter $node
        if ($pt) {
            $null = Invoke-Adb @('shell', 'input', 'tap', "$($pt[0])", "$($pt[1])")
            Write-Line "  tap consent checkbox at ($($pt[0]),$($pt[1]))"
            Start-Sleep -Milliseconds 500
            return
        }
    }
    # fallback: tap a bit left of the consent text
    $txt = Find-UiNode -Doc $doc -Label $L.Consent
    if (-not $txt) { throw 'consent checkbox/text not found on identity page' }
    $pt = Get-NodeCenter $txt
    $x = [Math]::Max(30, $pt[0] - 90)
    $null = Invoke-Adb @('shell', 'input', 'tap', "$x", "$($pt[1])")
    Write-Line "  tap consent checkbox fallback at ($x,$($pt[1]))"
    Start-Sleep -Milliseconds 500
}

# Find the settings Switch that sits on the same row as a given label text
# (profile page: the "wallpaper color (Material You)" row has a Switch on the right).
function Get-SwitchNodeNear([string]$Label) {
    $doc = Get-UiDump
    if (-not $doc) { return $null }
    $labelNode = Find-UiNode -Doc $doc -Label $Label
    if (-not $labelNode) { return $null }
    $lm = [regex]::Match([string]$labelNode.bounds, '\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
    if (-not $lm.Success) { return $null }
    $labelY = ([int]$lm.Groups[2].Value + [int]$lm.Groups[4].Value) / 2
    $best = $null
    $bestDist = 99999
    foreach ($n in $doc.SelectNodes('//node')) {
        $cls = [string]$n.class
        $checkable = ([string]$n.checkable) -eq 'true'
        if (-not $checkable -and ($cls -notmatch 'Switch')) { continue }
        $m = [regex]::Match([string]$n.bounds, '\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
        if (-not $m.Success) { continue }
        $cy = ([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2
        $d = [Math]::Abs($cy - $labelY)
        if ($d -lt $bestDist) { $bestDist = $d; $best = $n }
    }
    if ($best -and $bestDist -le 80) { return $best }
    return $null
}

function Get-SwitchStateNear([string]$Label) {
    $node = Get-SwitchNodeNear $Label
    if (-not $node) { throw "settings switch near '$Label' not found" }
    return (([string]$node.checked) -eq 'true')
}

# Toggle the switch once and verify its state actually changed; returns the new state.
function Toggle-SwitchNear([string]$Label) {
    $node = Get-SwitchNodeNear $Label
    if (-not $node) { throw "settings switch near '$Label' not found" }
    $before = ([string]$node.checked) -eq 'true'
    $pt = Get-NodeCenter $node
    if (-not $pt) { throw "cannot parse switch bounds near '$Label'" }
    $null = Invoke-Adb @('shell', 'input', 'tap', "$($pt[0])", "$($pt[1])")
    Write-Line "  tap switch near '$Label' at ($($pt[0]),$($pt[1]))"
    Start-Sleep -Milliseconds 800
    $after = Get-SwitchStateNear $Label
    if ($after -eq $before) { throw "switch near '$Label' did not change state" }
    return $after
}

# Wait for a text, scrolling the page (down then up) up to N times if not visible yet.
function Wait-UiTextScroll([string]$Label, [int]$ScrollTries = 3, [int]$TimeoutSec = 8) {    $node = Wait-UiText -Label $Label -TimeoutSec 2
    if ($node) { return $node }
    for ($i = 1; $i -le $ScrollTries; $i++) {
        Scroll-Up
        $node = Wait-UiText -Label $Label -TimeoutSec 2
        if ($node) { return $node }
    }
    for ($i = 1; $i -le $ScrollTries; $i++) {
        Scroll-Down
        $node = Wait-UiText -Label $Label -TimeoutSec 2
        if ($node) { return $node }
    }
    return Wait-UiText -Label $Label -TimeoutSec $TimeoutSec
}

# Find a text with bidirectional scrolling and tap its centre.
function Tap-UiTextScroll([string]$Label, [int]$ScrollTries = 3, [int]$TimeoutSec = 8) {
    $node = Wait-UiTextScroll -Label $Label -ScrollTries $ScrollTries -TimeoutSec $TimeoutSec
    if (-not $node) { throw "UI text not found (scrolled): '$Label'" }
    $pt = Get-NodeCenter $node
    if (-not $pt) { throw "cannot parse bounds for '$Label'" }
    Start-Sleep -Milliseconds 300
    $null = Invoke-Adb @('shell', 'input', 'tap', "$($pt[0])", "$($pt[1])")
    Write-Line "  tap '$Label' at ($($pt[0]),$($pt[1])) [scrolled]"
    Start-Sleep -Milliseconds 800
}

# Tap a bottom-nav label and verify the expected screen appears; retried because
# adb input taps are occasionally dropped while heavy uiautomator dumps run.
function Tap-NavUntil([string]$Label, [string]$Expect, [int]$Tries = 3, [int]$PerTrySec = 6) {
    for ($i = 1; $i -le $Tries; $i++) {
        Tap-UiText -Label $Label -Exact -TimeoutSec 8
        if (Wait-UiText -Label $Expect -TimeoutSec $PerTrySec) { return $true }
        Write-Line ("  nav tap '{0}' did not reach '{1}' (attempt {2}/{3})" -f $Label, $Expect, $i, $Tries)
        Start-Sleep -Milliseconds 400
    }
    return $false
}

function Save-Shot([string]$Name) {
    # let spring/fade transitions settle so the PNG shows the final frame
    Start-Sleep -Milliseconds 600
    $path = Join-Path $Screens $Name
    $cmd = '"' + $Adb + '" exec-out screencap -p > "' + $path + '"'
    $null = cmd /c $cmd
    if (-not (Test-Path $path)) { throw "screenshot failed: $Name" }
    $bytes = [IO.File]::ReadAllBytes($path)
    if ($bytes.Length -lt 5000 -or $bytes[0] -ne 0x89 -or $bytes[1] -ne 0x50) {
        throw "screenshot invalid (corrupt PNG): $Name"
    }
    $script:Shots.Add([pscustomobject]@{ file = $Name; bytes = $bytes.Length })
    Write-Line "  shot $Name ($($bytes.Length) B)"
}

function Get-GalleryPngCount {
    $raw = Invoke-Adb @('shell', 'ls /sdcard/Pictures/*/*.png 2>/dev/null | wc -l')
    $n = 0
    if ([int]::TryParse(($raw.Trim()), [ref]$n)) { return $n }
    return 0
}

function Assert-Emulator {
    $raw = Invoke-Adb @('devices')
    if ($raw -match 'emulator-\d+\s+device') {
        Write-Line 'emulator already online'
        return
    }
    Write-Line "emulator not online, starting AVD '$AvdName' ..."
    if (-not (Test-Path $Emu)) { throw "emulator binary missing: $Emu" }
    Start-Process -FilePath $Emu -ArgumentList @(
        '-avd', $AvdName, '-no-window', '-no-audio', '-no-boot-anim',
        '-gpu', 'swiftshader_indirect', '-no-snapshot'
    ) -WindowStyle Hidden | Out-Null
    $sw = [Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $BootTimeoutSec) {
        Start-Sleep -Seconds 5
        $boot = (Invoke-Adb @('shell', 'getprop', 'sys.boot_completed')).Trim()
        if ($boot -eq '1') { Write-Line "emulator booted in $([int]$sw.Elapsed.TotalSeconds)s"; return }
    }
    throw "emulator did not boot within ${BootTimeoutSec}s"
}

function Invoke-Build {
    if (-not (Test-Path $Jdk17)) { throw "JDK17 missing: $Jdk17" }
    $env:JAVA_HOME = $Jdk17
    $env:ANDROID_HOME = $SdkRoot
    $env:ANDROID_SDK_ROOT = $SdkRoot
    $env:PATH = "$Jdk17\bin;$SdkRoot\platform-tools;$env:PATH"
    $gradlew = Join-Path $Root 'gradlew.bat'
    $launcher = if (Test-Path $gradlew) { $gradlew } else { $Gradle }
    $buildLog = Join-Path $Screens 'build.log'
    Write-Line "build: $launcher :app:assembleDebug (cwd=$Root)"
    $sw = [Diagnostics.Stopwatch]::StartNew()
    Push-Location $Root
    try {
        # --no-daemon: a Gradle daemon would inherit this process' stdout pipe and keep it open
        $out = & $launcher ':app:assembleDebug' '--console=plain' '--no-daemon' 2>&1
        $code = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    ($out | Out-String) | Out-File -FilePath $buildLog -Encoding utf8
    # Another agent building the same project concurrently can corrupt the incremental
    # kotlin compile cache (FileAnalysisException / missing built_in_kotlinc classes).
    # Retry once with a clean build in that case.
    if ($code -ne 0 -and ($out | Out-String) -match 'FileAnalysisException|FileNotFoundException|built_in_kotlinc') {
        Write-Line '  build looks like a concurrent-build cache collision, retrying with clean ...' 'Yellow'
        Push-Location $Root
        try {
            $out = & $launcher ':app:clean' ':app:assembleDebug' '--console=plain' '--no-daemon' 2>&1
            $code = $LASTEXITCODE
        } finally {
            Pop-Location
        }
        ($out | Out-String) | Out-File -FilePath $buildLog -Encoding utf8
    }
    if ($code -ne 0) {
        Write-Host (($out | Select-Object -Last 40) | Out-String)
        throw "gradle assembleDebug failed (exit $code), full log: $buildLog"
    }
    Write-Line "build OK in $([int]$sw.Elapsed.TotalSeconds)s"
    Add-Step 'build' 'PASS' ('{0}s' -f [int]$sw.Elapsed.TotalSeconds) $sw.ElapsedMilliseconds
}

function Invoke-Install {
    if (-not (Test-Path $Apk)) { throw "APK missing: $Apk (run with -Build)" }
    Write-Line "install: $Apk ($((Get-Item $Apk).Length) B)"
    $out = Invoke-Adb @('install', '-r', $Apk)
    if ($out -notmatch 'Success') { throw "adb install failed: $out" }
    Add-Step 'install' 'PASS' '' 0
}

function Invoke-AppLaunch {
    if (-not $KeepData) {
        $null = Invoke-Adb @('shell', 'pm', 'clear', $Pkg)
    }
    $null = Invoke-Adb @('shell', 'am', 'force-stop', $Pkg)
    $null = Invoke-Adb @('logcat', '-c')
    $null = Invoke-Adb @('shell', 'am', 'start', '-n', $Act)
    Write-Line "launched $Act"
}

function Dump-FailureContext {
    Write-Host ''
    Write-Host '================ FAILURE CONTEXT ================' -ForegroundColor Red
    $shot = Join-Path $Screens 'failure.png'
    $cmd = '"' + $Adb + '" exec-out screencap -p > "' + $shot + '"'
    $null = cmd /c $cmd
    Write-Host "screenshot: $shot"
    $doc = Get-UiDump
    if ($doc) {
        $dumpPath = Join-Path $Screens 'failure_ui.xml'
        try { $doc.Save($dumpPath) } catch { }
        Write-Host "current UI dump: $dumpPath"
        $texts = @()
        foreach ($n in $doc.SelectNodes('//node')) {
            $t = [string]$n.text
            $d = [string]$n.'content-desc'
            if ($t) { $texts += $t }
            if ($d) { $texts += "[$d]" }
        }
        Write-Host ("visible texts: " + (($texts | Select-Object -Unique) -join ' | '))
    }
    Write-Host '--- logcat tail (package / FATAL / Exception / ANR) ---' -ForegroundColor Yellow
    $logcat = Invoke-Adb @('logcat', '-d', '-t', '400')
    ($logcat -split "`r?`n") |
        Where-Object { $_ -match 'classtools|FATAL|Exception|ANR in|AndroidRuntime|Force finishing' } |
        Select-Object -Last 80 | ForEach-Object { Write-Host $_ }
    $file = Join-Path $Screens 'failure_logcat.txt'
    $logcat | Out-File -FilePath $file -Encoding utf8
    Write-Host "logcat: $file"
    Write-Host '=================================================' -ForegroundColor Red
}

# ---------------------------------------------------------------- scenario
function Invoke-Scenario {
    $sw = [Diagnostics.Stopwatch]::StartNew()

    # STEP 0: identity gate visible
    Write-Line '--- step: identity gate'
    $swStep = [Diagnostics.Stopwatch]::StartNew()
    $node = Wait-UiText -Label $L.DevPreview -TimeoutSec 30
    if (-not $node) {
        $null = Wait-UiText -Label $L.IdentityTitle -TimeoutSec 5
        throw 'identity gate not visible (no Dev Preview button)'
    }
    Save-Shot '01_identity.png'
    Add-Step 'identity_gate' 'PASS' '' $swStep.ElapsedMilliseconds

    # STEP 1b: real identity verification failure path (no credentials needed).
    # Fills a bogus sid/name, ticks consent and submits: this exercises the real
    # class_verify network round trip and the app's auth failure handling.
    Write-Line '--- step: real identity failure path'
    $swStep = [Diagnostics.Stopwatch]::StartNew()
    Tap-UiTextInput -Label $L.SidField -Value '0000000000'
    Tap-UiTextInput -Label $L.NameField -Value 'Test'
    Tap-ConsentCheckbox
    Tap-UiText -Label $L.SubmitIdentity -Exact -TimeoutSec 10
    $authErrNode = Wait-UiAny -Labels @($L.AuthFailMark, $L.AuthFailAlt) -TimeoutSec 12
    if (-not $authErrNode) { throw 'identity verify error message not shown within 12s' }
    Save-Shot '20_auth_error.png'
    Add-Step 'auth_failure_path' 'PASS' '' $swStep.ElapsedMilliseconds

    # STEP 1: Dev Preview -> workbench
    Write-Line '--- step: enter workbench (Dev Preview)'
    $swStep = [Diagnostics.Stopwatch]::StartNew()
    Tap-UiText -Label $L.DevPreview -TimeoutSec 10
    $null = Wait-UiAny -Labels @($L.QuickTools, $L.Today) -TimeoutSec 30
    $quick = Wait-UiText -Label $L.QuickTools -TimeoutSec 8
    if (-not $quick) { throw "workbench not visible ('$($L.QuickTools)' missing)" }
    $null = Wait-UiText -Label $L.Today -TimeoutSec 8
    Save-Shot '02_workbench.png'
    Add-Step 'workbench' 'PASS' '' $swStep.ElapsedMilliseconds

    # STEP 2: bottom tab "All"
    Write-Line '--- step: all tools'
    $swStep = [Diagnostics.Stopwatch]::StartNew()
    Go-AllTools
    Save-Shot '03_all_tools.png'
    Add-Step 'all_tools' 'PASS' '' $swStep.ElapsedMilliseconds

    # STEP 3: open draw tool, STEP1
    Write-Line '--- step: draw STEP1'
    $swStep = [Diagnostics.Stopwatch]::StartNew()
    Tap-UiText -Label $L.Draw -Exact -TimeoutSec 15 -ScrollTries 2
    if (-not (Wait-UiText -Label $L.DrawParams -TimeoutSec 20)) { throw 'draw STEP1 (params card) not visible' }
    Save-Shot '04_draw_step1.png'
    Add-Step 'draw_step1' 'PASS' '' $swStep.ElapsedMilliseconds

    # STEP 4: STEP1 -> STEP2 preview candidates
    Write-Line '--- step: draw STEP2 (preview candidates)'
    $swStep = [Diagnostics.Stopwatch]::StartNew()
    Tap-UiText -Label $L.NextStep -Exact -TimeoutSec 15
    if (-not (Wait-UiText -Label $L.Candidates -TimeoutSec 20)) { throw 'draw STEP2 (candidates) not visible' }
    Save-Shot '05_draw_step2.png'
    Add-Step 'draw_step2' 'PASS' '' $swStep.ElapsedMilliseconds

    # STEP 5: start draw -> STEP3 result
    # Exact match matters: the STEP2 intro text also contains the words "start draw".
    Write-Line '--- step: draw STEP3 (result)'
    $swStep = [Diagnostics.Stopwatch]::StartNew()
    Tap-UiText -Label $L.StartDraw -Exact -TimeoutSec 15
    $result = Wait-UiText -Label $L.DrawResult -TimeoutSec 20
    if (-not $result) {
        Write-Line '  result not shown, tapping start draw once more'
        Tap-UiText -Label $L.StartDraw -Exact -TimeoutSec 10
        $result = Wait-UiText -Label $L.DrawResult -TimeoutSec 20
    }
    if (-not $result) { throw 'draw STEP3 (result) not visible' }
    Save-Shot '06_draw_step3.png'
    Add-Step 'draw_step3' 'PASS' '' $swStep.ElapsedMilliseconds

    # STEP 6: export image -> gallery file appears
    Write-Line '--- step: export image to gallery'
    $swStep = [Diagnostics.Stopwatch]::StartNew()
    $before = Get-GalleryPngCount
    Tap-UiTextScroll -Label $L.Export -ScrollTries 3 -TimeoutSec 8
    $after = $before
    $wait = [Diagnostics.Stopwatch]::StartNew()
    while ($wait.Elapsed.TotalSeconds -lt 25 -and $after -le $before) {
        Start-Sleep -Milliseconds 800
        $after = Get-GalleryPngCount
    }
    Save-Shot '07_export.png'
    if ($after -le $before) { throw "gallery PNG count did not increase (before=$before, after=$after)" }
    Add-Step 'export_image' 'PASS' "gallery png: $before -> $after" $swStep.ElapsedMilliseconds

    # STEP 8b: draw STEP3 extra actions - result sheet button + "use result for grouping".
    Write-Line '--- step: draw result actions'
    $swStep = [Diagnostics.Stopwatch]::StartNew()
    if (-not (Wait-UiTextScroll -Label $L.ExportSheet -ScrollTries 3 -TimeoutSec 8)) {
        throw 'draw result sheet button not visible'
    }
    Tap-UiTextScroll -Label $L.UseResultGroup -ScrollTries 3 -TimeoutSec 8
    if (-not (Wait-UiAny -Labels @($L.GroupPrevSrc, $L.GroupStep1) -TimeoutSec 15)) {
        throw 'group page did not show the previous draw result source'
    }
    Save-Shot '22_draw_to_group.png'
    $null = Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    Start-Sleep -Milliseconds 1000
    # After task-17 (rememberSaveable + Saver) this MUST land back on the STEP3
    # result page; landing on STEP1 is the known app defect fixed by contract.
    if (-not (Wait-UiText -Label $L.DrawResult -TimeoutSec 12)) {
        throw 'back from group did not return to draw result page (state lost: got STEP1?)'
    }
    Add-Step 'draw_result_actions' 'PASS' '' $swStep.ElapsedMilliseconds

    # STEP 7: back key from the draw tool -> all tools. The tool page tour runs
    # right here (all tool pages are opened from the All tab), because re-entering
    # the All tab after a BACK-to-home round trip is unreliable on this build.
    Write-Line '--- step: back to all tools'
    $swStep = [Diagnostics.Stopwatch]::StartNew()
    $null = Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    Start-Sleep -Milliseconds 900
    if (-not (Wait-UiText -Label $L.AllToolsTitle -TimeoutSec 15)) { throw 'back key did not return to all tools' }
    Save-Shot '08_back_all_tools.png'
    Add-Step 'back_to_all_tools' 'PASS' '' $swStep.ElapsedMilliseconds

    Write-Line '--- step: tool pages (draw/hw/nt/tt/power/water/group/fit)'
    $swStep = [Diagnostics.Stopwatch]::StartNew()
    Go-AllTools
    $pages = @(
        @{ step = 'tool_hw';    card = $L.Hw;    title = $L.Hw;         shot = '11_hw.png';    states = @($L.HwLoading, $L.HwEmpty, $L.HwEmptyFilter, $L.HwError) },
        @{ step = 'tool_nt';    card = $L.Nt;    title = $L.NtTitle;    shot = '12_nt.png';    states = @($L.NtLoading, $L.NtEmpty, $L.NtEmptyPending, $L.NtEmptyPinned, $L.NtError) },
        @{ step = 'tool_tt';    card = $L.Tt;    title = $L.Tt;         shot = '13_tt.png';    states = @($L.TtLoading, $L.TtNoData, $L.TtNoDataHint, $L.TtNetError, $L.TtParseError, $L.TtEmpty, $L.TtEmptyHint, $L.TtError) },
        @{ step = 'tool_power'; card = $L.Power; title = $L.PowerTitle; shot = '14_power.png'; states = @($L.PowerLoading, $L.PowerEmpty, $L.PowerError) },
        @{ step = 'tool_water'; card = $L.Water; title = $L.WaterTitle; shot = '15_water.png'; states = @($L.WaterEmpty, $L.WaterLoading, $L.WaterError, $L.PlaceholderPill) },
        @{ step = 'tool_group'; card = $L.Group; title = $L.Group;      shot = '16_group.png'; states = @($L.GroupStep1, $L.AuthError, $L.PlaceholderPill) },
        @{ step = 'tool_fit';   card = $L.Fit;   title = $L.Fit;        shot = '17_fit.png';   states = @($L.FitTerms, $L.FitHistory, $L.AuthError, $L.PlaceholderPill) },
        @{ step = 'tool_roster'; card = $L.Roster; title = $L.RosterTitle; shot = '19_roster.png';
           states = @($L.RosterLoading, $L.RosterEmpty, $L.RosterError, $L.RollEmpty);
           tabLabel = $L.RosterTabRoll; tabStates = @($L.RollEmpty, $L.RollDone, $L.RosterEmpty, $L.RosterError); tabShot = '20_roster_roll.png' }
    )
    foreach ($pg in $pages) {
        Write-Line ("--- step: tool page {0}" -f $pg.step)
        $swPage = [Diagnostics.Stopwatch]::StartNew()
        $stateNode = $null
        for ($try = 1; $try -le 2; $try++) {
            Tap-UiText -Label $pg.card -Exact -TimeoutSec 15 -ScrollTries 3
            # slow pages: poll up to 8s every 800ms for an allowed four-state text
            $stateNode = Wait-UiAny -Labels $pg.states -TimeoutSec 8
            if ($stateNode) { break }
            Write-Line ("  no expected state on '{0}' (attempt {1}), retrying" -f $pg.card, $try)
        }
        if (-not $stateNode) {
            throw "none of the expected state texts appeared on '$($pg.card)' page (2 attempts x 8s)"
        }
        if (-not (Wait-UiText -Label $pg.title -TimeoutSec 10)) {
            throw "tool page title '$($pg.title)' not visible after tap"
        }
        Save-Shot $pg.shot
        # optional secondary tab inside a page (e.g. roster: roster / roll-call tabs)
        if ($pg.ContainsKey('tabLabel')) {
            Tap-UiText -Label $pg.tabLabel -Exact -TimeoutSec 10
            if (-not (Wait-UiAny -Labels $pg.tabStates -TimeoutSec 8)) {
                throw "tab '$($pg.tabLabel)' content not visible on '$($pg.card)' page"
            }
            Save-Shot $pg.tabShot
        }
        Add-Step $pg.step 'PASS' '' $swPage.ElapsedMilliseconds
        $null = Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_BACK')
        Start-Sleep -Milliseconds 1000
        if (-not (Wait-ScreenKind 'ALL' 15)) { throw "back from '$($pg.card)' did not return to all tools" }
    }
    Add-Step 'tool_pages' 'PASS' ('{0} pages' -f $pages.Count) $swStep.ElapsedMilliseconds

    # STEP 9a: profile -> data & privacy page. Opens the clear-cloud confirm dialog
    # and cancels it (never actually clears anything). If the page cannot load the
    # overview without a real token, record WARN instead of failing.
    Write-Line '--- step: data & privacy page'
    $swStep = [Diagnostics.Stopwatch]::StartNew()
    Tap-UiText -Label $L.MyTab -Exact -TimeoutSec 10
    if (-not (Wait-UiText -Label $L.Manage -Exact -TimeoutSec 15)) { throw 'profile page data & privacy entry not visible' }
    Tap-UiText -Label $L.Manage -Exact -TimeoutSec 10
    $privacyState = Wait-UiAny -Labels @($L.PrivacyNote, $L.PrivacyLoadErr, $L.PrivacyAuth, $L.PrivacyLoading) -TimeoutSec 15
    if (-not $privacyState) { throw 'data & privacy page content not visible' }
    if (Wait-UiTextScroll -Label $L.ClearCloud -ScrollTries 4 -TimeoutSec 6) {
        Tap-UiTextScroll -Label $L.ClearCloud -ScrollTries 2 -TimeoutSec 6
        if (-not (Wait-UiText -Label $L.ConfirmClear -Exact -TimeoutSec 8)) { throw 'clear-cloud confirm dialog not visible' }
        Save-Shot '21_privacy.png'
        Tap-UiText -Label $L.Cancel -Exact -TimeoutSec 10
        if (-not (Wait-UiTextScroll -Label $L.PrivacyNote -ScrollTries 3 -TimeoutSec 10)) { throw 'privacy page did not come back after cancel' }
        Add-Step 'privacy_page' 'PASS' 'clear-cloud dialog opened and cancelled' $swStep.ElapsedMilliseconds
    } else {
        Save-Shot '21_privacy.png'
        Add-Step 'privacy_page' 'WARN' 'no real token: privacy page shows empty/error state; confirm dialog not reachable' $swStep.ElapsedMilliseconds
    }
    $null = Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    Start-Sleep -Milliseconds 1200
    $null = Wait-ScreenKind 'PROFILE' 10
    Go-Workbench
    Add-Step 'privacy_back_home' 'PASS' '' 0

    # STEP 9b: profile appearance (wallpaper color switch) + about version (v1.0.1)
    Write-Line '--- step: profile appearance + about version'
    $swStep = [Diagnostics.Stopwatch]::StartNew()
    Tap-UiText -Label $L.MyTab -Exact -TimeoutSec 10
    if (-not (Wait-UiTextScroll -Label $L.WallpaperColor -ScrollTries 4 -TimeoutSec 15)) { throw 'profile appearance switch row not visible' }
    if (-not (Wait-UiTextScroll -Label $L.Appearance -ScrollTries 3 -TimeoutSec 5)) { throw 'appearance card title not visible' }
    $script:AppearanceState = Toggle-SwitchNear -Label $L.WallpaperColor
    Save-Shot '23_appearance.png'
    if (-not (Wait-UiTextScroll -Label '1.0.1' -ScrollTries 4 -TimeoutSec 10)) { throw 'about version 1.0.1 not visible' }
    Add-Step 'appearance_about' 'PASS' ("wallpaperColor=" + $script:AppearanceState + "; version=1.0.1") $swStep.ElapsedMilliseconds

    # STEP 9c: restart the app and verify the appearance switch value persisted (DataStore)
    Write-Line '--- step: appearance persists after restart'
    $swStep = [Diagnostics.Stopwatch]::StartNew()
    $null = Invoke-Adb @('shell', 'am', 'force-stop', $Pkg)
    $null = Invoke-Adb @('shell', 'am', 'start', '-n', $Act)
    if (-not (Wait-ScreenKind 'HOME' 25)) { throw 'app did not restore to workbench after restart' }
    Tap-UiText -Label $L.MyTab -Exact -TimeoutSec 10
    if (-not (Wait-UiTextScroll -Label $L.WallpaperColor -ScrollTries 4 -TimeoutSec 15)) { throw 'appearance switch row not visible after restart' }
    $persisted = Get-SwitchStateNear -Label $L.WallpaperColor
    if ($persisted -ne $script:AppearanceState) {
        throw "appearance switch not persisted (expected $($script:AppearanceState), got $persisted)"
    }
    Add-Step 'appearance_persist' 'PASS' ("persisted=" + $persisted) $swStep.ElapsedMilliseconds

    # STEP 9d: all tools -> "settings" card must land on the profile page (not a placeholder)
    Write-Line '--- step: settings card redirects to profile'
    $swStep = [Diagnostics.Stopwatch]::StartNew()
    Go-AllTools
    if (-not (Wait-UiTextScroll -Label $L.Settings -ScrollTries 5 -TimeoutSec 12)) { throw 'settings tool card not visible on all tools page' }
    Tap-UiTextScroll -Label $L.Settings -ScrollTries 3 -TimeoutSec 8
    if (-not (Wait-UiTextScroll -Label $L.WallpaperColor -ScrollTries 4 -TimeoutSec 15)) { throw 'settings card did not land on profile page (appearance row missing)' }
    if (-not (Wait-UiTextScroll -Label $L.About -ScrollTries 3 -TimeoutSec 8)) { throw 'settings card did not land on profile page (about card missing)' }
    $settingsDump = Get-UiDump
    if ($settingsDump) {
        $placeholder = Find-UiNode -Doc $settingsDump -Label $L.PlaceholderPill
        if ($placeholder) { throw 'settings card opened a placeholder page instead of profile' }
    }
    Save-Shot '24_settings_redirect.png'
    $null = Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    Start-Sleep -Milliseconds 1200
    if (-not (Wait-ScreenKind 'ALL' 10)) {
        Write-Line '  back from profile did not reach all tools, relaunching app'
        $null = Invoke-Adb @('shell', 'am', 'force-stop', $Pkg)
        $null = Invoke-Adb @('shell', 'am', 'start', '-n', $Act)
        $null = Wait-ScreenKind 'HOME' 25
        Go-AllTools
    }
    Add-Step 'settings_redirect' 'PASS' 'settings card -> profile page' $swStep.ElapsedMilliseconds

    # STEP 9: back to workbench. System BACK from the All tab is the reliable route
    # (bottom-tab tap on this build is ignored here - reported to Lead as app bug).
    Write-Line '--- step: back to workbench'
    $swStep = [Diagnostics.Stopwatch]::StartNew()
    Go-Workbench
    Save-Shot '09_home_after_back.png'
    Add-Step 'back_to_workbench' 'PASS' '' $swStep.ElapsedMilliseconds

    # STEP 9b: navigation stability probe - 10 consecutive ALL -> Workbench bottom-tab taps.
    # Diagnostic only: reports WARN (not FAIL) when some taps are ignored, so a known
    # app navigation rough edge does not mask other regressions. Failure UI dumps are kept.
    # Each cycle restarts the app to start from a clean nav state (navigating back from
    # the all-tools page via system BACK leaves a state where tab taps are ignored).
    Write-Line '--- step: navigation probe (all -> workbench tab x10)'
    $swStep = [Diagnostics.Stopwatch]::StartNew()
    $probeAttempts = 10
    $probeOk = 0
    $probeFail = 0
    for ($i = 1; $i -le $probeAttempts; $i++) {
        $null = Invoke-Adb @('shell', 'am', 'force-stop', $Pkg)
        $null = Invoke-Adb @('shell', 'am', 'start', '-n', $Act)
        if (-not (Wait-ScreenKind 'HOME' 25)) {
            $probeFail++
            Write-Line ("  probe {0}: app did not reach workbench after restart" -f $i) 'Yellow'
            continue
        }
        $cycleOk = $false
        Tap-UiText -Label $L.AllTab -Exact -TimeoutSec 8
        if (Wait-ScreenKind 'ALL' 6) {
            Tap-UiText -Label $L.Workbench -Exact -TimeoutSec 8
            if (Wait-ScreenKind 'HOME' 5) { $cycleOk = $true }
        }
        if ($cycleOk) {
            $probeOk++
        } else {
            $probeFail++
            Write-Line ("  probe {0}: workbench tab tap ignored by app" -f $i) 'Yellow'
            $probeDump = Get-UiDump
            if ($probeDump) {
                try { $probeDump.Save((Join-Path $Screens ("nav_probe_fail_{0}.xml" -f $i))) } catch { }
            }
        }
        Write-Line ("  probe {0}/{1}: ok={2} fail={3}" -f $i, $probeAttempts, $probeOk, $probeFail)
    }
    $null = Invoke-Adb @('shell', 'am', 'force-stop', $Pkg)
    $null = Invoke-Adb @('shell', 'am', 'start', '-n', $Act)
    $null = Wait-ScreenKind 'HOME' 25
    $script:NavProbe = [pscustomobject]@{ attempts = $probeAttempts; ok = $probeOk; fail = $probeFail }
    if ($probeFail -eq 0) {
        Add-Step 'nav_probe_10x' 'PASS' ("{0}/{1} ok" -f $probeOk, $probeAttempts) $swStep.ElapsedMilliseconds
    } else {
        Add-Step 'nav_probe_10x' 'WARN' ("{0}/{1} ok, {2} ignored" -f $probeOk, $probeAttempts, $probeFail) $swStep.ElapsedMilliseconds
    }
    Go-Workbench

    # STEP 10: workbench quick tools editor (open -> assert -> done).
    # Kept as the last step on purpose: bottom-tab behaviour after leaving the
    # editor has been flaky on this build, and nothing needs navigating afterwards.
    Write-Line '--- step: home quick tools editor'
    $swStep = [Diagnostics.Stopwatch]::StartNew()
    Go-Workbench
    $editorOpen = $false
    for ($try = 1; $try -le 2; $try++) {
        Tap-UiText -Label $L.Edit -Exact -TimeoutSec 15
        if (Wait-UiText -Label $L.EditTitle -TimeoutSec 8) { $editorOpen = $true; break }
        Write-Line "  quick tools editor did not open (attempt $try), retrying"
    }
    if (-not $editorOpen) { throw "quick tools editor not visible ('$($L.EditTitle)' missing)" }
    if (-not (Wait-UiText -Label $L.EditSelected -TimeoutSec 8)) { throw "quick tools editor selected counter not visible ('$($L.EditSelected)' missing)" }
    Save-Shot '18_home_edit.png'
    $leftEditor = $false
    for ($try = 1; $try -le 2; $try++) {
        Tap-UiText -Label $L.Done -Exact -TimeoutSec 10
        if (Wait-UiText -Label $L.QuickTools -TimeoutSec 8) { $leftEditor = $true; break }
    }
    if (-not $leftEditor) { throw 'did not return to workbench from quick tools editor' }
    Add-Step 'home_quick_edit' 'PASS' '' $swStep.ElapsedMilliseconds

    Write-Line ("scenario done in {0}s" -f [int]$sw.Elapsed.TotalSeconds)
}

# ---------------------------------------------------------------- main
Write-Line '============================================================='
Write-Line ' ClassTools Android E2E smoke (text-driven, emulator)'
Write-Line (" repo   : {0}" -f $Root)
Write-Line (" screens: {0}" -f $Screens)
Write-Line (" flags  : Build={0} Install={1} KeepData={2}" -f $Build, $Install, $KeepData)
Write-Line '============================================================='

$failed = $false
$failureMsg = ''
$galleryBefore = 0
$galleryAfter = 0
$logcatProblems = @()

try {
    Assert-Emulator
    $device = ((Invoke-Adb @('devices')) -split "`r?`n" | Where-Object { $_ -match 'emulator-\d+\s+device' } | Select-Object -First 1)
    Write-Line ("device: {0}" -f $device.Trim())

    if ($Build -or -not (Test-Path $Apk)) { Invoke-Build }
    if ($Install -or $Build) { Invoke-Install } else { Write-Line 'install skipped (use -Install to reinstall)' }

    $galleryBefore = Get-GalleryPngCount
    Write-Line "gallery PNG count before run: $galleryBefore"

    Invoke-AppLaunch
    Invoke-Scenario

    # ---- regression: logcat FATAL / ANR
    Write-Line '--- regression: logcat FATAL/ANR check'
    $logcat = Invoke-Adb @('logcat', '-d')
    $logcatPath = Join-Path $Screens 'logcat_run.txt'
    $logcat | Out-File -FilePath $logcatPath -Encoding utf8
    $logcatProblems = @(($logcat -split "`r?`n") | Where-Object {
        $_ -match 'FATAL EXCEPTION|ANR in |AndroidRuntime.*FATAL|Force finishing activity'
    })
    if ($logcatProblems.Count -gt 0) {
        Write-Line "logcat problems found: $($logcatProblems.Count)" 'Red'
        $logcatProblems | Select-Object -Last 20 | ForEach-Object { Write-Line "  $_" 'Red' }
        Add-Step 'logcat_no_fatal' 'FAIL' ("{0} FATAL/ANR lines" -f $logcatProblems.Count) 0
    } else {
        Write-Line 'logcat: no FATAL / ANR' 'Green'
        Add-Step 'logcat_no_fatal' 'PASS' '' 0
    }

    # ---- regression: gallery export file
    Write-Line '--- regression: gallery export check'
    $galleryAfter = Get-GalleryPngCount
    $delta = $galleryAfter - $galleryBefore
    $listing = Invoke-Adb @('shell', 'ls -1 /sdcard/Pictures/*/ 2>/dev/null')
    ($listing | Out-String) | Out-File -FilePath (Join-Path $Screens 'gallery_listing.txt') -Encoding utf8
    if ($delta -ge 1) {
        Write-Line "gallery: $galleryBefore -> $galleryAfter (+$delta)" 'Green'
        Add-Step 'gallery_export_file' 'PASS' "+$delta png" 0
    } else {
        Write-Line "gallery: no new PNG (before=$galleryBefore after=$galleryAfter)" 'Red'
        Add-Step 'gallery_export_file' 'FAIL' 'no new PNG' 0
        $failed = $true
        $failureMsg = 'exported PNG not found in gallery'
    }
} catch {
    $failed = $true
    $failureMsg = $_.Exception.Message
    Add-Step 'exception' 'FAIL' $failureMsg 0
    Dump-FailureContext
}

# ---------------------------------------------------------------- summary
$totalMs = [int]((Get-Date) - $script:Started).TotalMilliseconds
$pass = @($script:Steps | Where-Object { $_.status -eq 'PASS' }).Count
$warn = @($script:Steps | Where-Object { $_.status -eq 'WARN' }).Count
$fail = @($script:Steps | Where-Object { $_.status -eq 'FAIL' }).Count
if ($fail -gt 0) { $failed = $true }

Write-Line '============================ SUMMARY ============================'
foreach ($s in $script:Steps) {
    Write-Line ("  {0,-18} {1,-5} {2}" -f $s.step, $s.status, $s.detail)
}
$shotList = ($script:Shots | ForEach-Object { $_.file }) -join ', '
Write-Line ("  screenshots: {0}" -f $shotList)
if ($script:NavProbe) {
    Write-Line ("  nav probe: {0}/{1} ok, {2} ignored" -f $script:NavProbe.ok, $script:NavProbe.attempts, $script:NavProbe.fail)
}
Write-Line ("  total: {0:N1}s  PASS={1} WARN={2} FAIL={3}" -f ($totalMs / 1000.0), $pass, $warn, $fail)
Write-Line ("  screenshots dir: {0}" -f $Screens)
Write-Line '================================================================'

$summary = [pscustomobject]@{
    startedAt = $script:Started.ToString('o')
    finishedAt = (Get-Date).ToString('o')
    durationMs = $totalMs
    result = $(if ($failed) { 'FAIL' } else { 'PASS' })
    failureMessage = $failureMsg
    flags = [pscustomobject]@{ build = [bool]$Build; install = [bool]$Install; keepData = [bool]$KeepData }
    device = $device
    steps = $script:Steps
    screenshots = $script:Shots
    gallery = [pscustomobject]@{ before = $galleryBefore; after = $galleryAfter }
    logcatProblems = $logcatProblems
    navProbe = $script:NavProbe
}
$summaryPath = Join-Path $Screens 'summary.json'
$summary | ConvertTo-Json -Depth 6 | Out-File -FilePath $summaryPath -Encoding utf8
Write-Line "summary: $summaryPath"

$script:Log | Out-File -FilePath $LogFile -Encoding utf8
Remove-Item $TmpUi -Force -ErrorAction SilentlyContinue
if ($failed) { exit 1 } else { exit 0 }
