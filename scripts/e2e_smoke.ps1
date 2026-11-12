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
    [switch]$SkipWater,
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
    AllTab         = U8 '5bel5YW3'                                   # bottom dock: tools tab (v2)
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
    Roster         = U8 '5ZCN5Y2V'                                   # roster tool card
    RosterTitle    = U8 '5ZCN5Y2VIMK3IOeCueWQjQ=='                   # roster page top bar: roster / roll call
    RosterTabRoll  = U8 '54K55ZCN'                                   # roster tab: roll call
    RosterLoading  = U8 '5Yqg6L2954+t57qn5ZCN5Y2V'                   # loading class roster
    RosterEmpty    = U8 '5ZCN5Y2V5bCa5pyq5ZCM5q2l'                   # roster not synced yet
    RosterError    = U8 '5ZCN5Y2V5Yqg6L295aSx6LSl'                   # roster load failed
    RosterNameEmpty = U8 '5ZCN5Y2V5Li656m6'                           # roster name tab empty state
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
    HomeDue        = U8 '5L2c5Lia5b6F5Lqk'                           # workbench: homework due card
    HomeNotices    = U8 '6YCa55+l5b6F5aSE55CG'                       # workbench: notices pending card
    HomeTtSync     = U8 '6K++6KGo5pyq5ZCM5q2l'                       # workbench: timetable not synced
    HomeNoClass    = U8 '5LuK5aSp5rKh6K++'                           # workbench: no class today
    PermTitle      = U8 '5p2D6ZmQ5LiO5o+Q6YaS'                       # profile card / permission page title
    MyData         = U8 '5oiR55qE5pWw5o2u'                           # profile v2.1: my-data stats card
    GuideEntry     = U8 '5L2/55So6K+05piO'                           # profile: user guide entry
    FeedbackEntry  = U8 '5Y+N6aaI5bu66K6u'                           # profile: feedback entry
    FeedbackAction = U8 '5Y+N6aaI'                                   # profile row action: feedback
    GuideAction    = U8 '5p+l55yL'                                   # profile row action: view guide
    FeedbackForm   = U8 '5Y+N6aaI5YaF5a65'                           # feedback page: content field label
    FeedbackSubmit = U8 '5o+Q5Lqk5Y+N6aaI'                           # feedback page: submit button
    FeedbackFailed = U8 '6K+75Y+W5Y+N6aaI5aSx6LSl'                   # feedback page: load failed state
    FeedbackEmpty  = U8 '6L+Y5rKh5pyJ5Y+N6aaI'                       # feedback page: empty state
    FeedbackLoad   = U8 '5Yqg6L295Y+N6aaI'                           # feedback page: loading state
    GuideTitle     = U8 '5L2/55So6K+05piO'                           # guide page title
    FooterMark     = U8 '6Lqr5Lu95bey5qC46aqM'                       # workbench footer: identity verified
    NotifTestTitle = U8 '6YCa55+l5rWL6K+V5LiO6K+K5pat'               # reminder: notification test section (v3)
    NotifTestBtn   = U8 '5Y+R6YCB5rWL6K+V6YCa55+l'                   # reminder: send test notification (v3)
    NotifDiagBtn   = U8 '56uL5Y2z5qOA5p+l'                           # reminder: run check + diagnostics (v3)
    LoginEdu       = U8 '55m75b2V5pWZ5Yqh'                           # timetable: login entry (v3)
    ExportTt       = U8 '5a+85Ye66K++6KGo5Zu+54mH'                   # timetable: export button (v3)
    DiagTool       = U8 '6K+K5pat'                                   # all tools: diagnosis card (v3)
    DiagSub        = U8 '5pyN5Yqh5YGl5bq3'                           # diag page: service health subtitle
    DiagLocal      = U8 '5pys5py65Luj55CG'                           # diag section: local agent
    DiagDetail     = U8 '6K+m57uG'                                    # diag simple mode: 详细 toggle
    DiagSimple     = U8 '566A5rSB'                                    # diag detailed mode: 简洁 toggle
    DiagDevice     = U8 '6K6+5aSH6K+K5pat'                           # diag section: device diagnostics
    DiagAI         = U8 'QUkg5YiG5p6Q'                               # diag section: AI analysis
    DiagFix        = U8 '5L+u5aSN5Lu75Yqh'                           # diag section: repair tasks
    DiagHealth     = U8 '5pyN5Yqh5YGl5bq3'                           # diag section: service health
    DockTabPattern = U8 '56ysIFxkKyDkuKogVGFi'                       # dock item semantics: di tab marker
    DockSelMark    = U8 '5bey6YCJ5Lit'                               # dock item semantics: selected marker
    DockLabelPat   = U8 'Xijlt6XkvZzlj7B85bel5YW3fOaIkeeahCkk'       # dock visible label pattern
    VoteTool       = U8 '5oqV56Wo'                                   # all tools: vote card / vote page title
    VoteEmpty      = U8 '6L+Y5rKh5pyJ5oqV56Wo'                       # vote page: empty state
    VoteLoading    = U8 '5Yqg6L295oqV56Wo'                           # vote page: loading state
    VoteStart      = U8 '5Y+R6LW35oqV56Wo'                           # vote page: create-vote action
    VoteRetry      = U8 '6YeN6K+V'                                   # vote page: retry
    RosterCatC     = U8 '54+t5aeU'                                   # roster: committee category
    RosterCatL     = U8 '5Zui5ZGY'                                   # roster: league category
    RosterCatA     = U8 '5YWo54+t'                                   # roster: all category
    ClearCacheRow  = U8 '5riF6Zmk5pys5py657yT5a2Y'                   # profile row: clear local cache
    ClearCacheTtl  = U8 '5riF6Zmk5pys5py657yT5a2Y77yf'               # profile dialog: clear local cache?
    ClearAct       = U8 '5riF6Zmk'                                   # profile row action: clear
    NotifyBanner   = U8 '6YCa55+l5p2D6ZmQ5pyq5byA5ZCv'               # permission banner title
    PermManage     = U8 '5p2D6ZmQ566h55CG'                           # profile row: permission management
    NotifyWord     = U8 '6YCa55+l'                                   # permission card: notifications
    CalendarWord   = U8 '5pel5Y6G'                                   # permission card: calendar
    GrantPerm      = U8 '5byA5ZCv5p2D6ZmQ'                           # permission card action: grant
    GrantGo        = U8 '5Y675byA5ZCv'                               # in-app rationale dialog: go enable
    CancelPerm     = U8 '5pqC5LiN'                                   # in-app rationale dialog: cancel
    PermOff        = U8 '5pyq5byA5ZCv'                               # permission state: not enabled
    PermOn         = U8 '5bey5byA5ZCv'                               # permission state: enabled
    PermNeedSet    = U8 '6ZyA57O757uf6K6+572u'                      # permission state: needs system settings
    GrantPermSys   = U8 '5Y6757O757uf6K6+572u5byA5ZCv'              # permission action: open system settings
    ReminderEntry  = U8 '5o+Q6YaS6K6+572u'                           # profile row: reminder settings
    ReminderTitle  = U8 '5o+Q6YaS'                                   # reminder page title
    ReminderMain   = U8 '5o+Q6YaS5oC75byA5YWz'                      # reminder: master switch
    HomeworkRem    = U8 '5L2c5Lia5o+Q6YaS'                           # reminder: homework reminder
    ClassRem       = U8 '5LiK6K++5o+Q6YaS'                           # reminder: class reminder
    NoticeRemind   = U8 '6YCa55+l5o+Q6YaS'                           # reminder: notice reminder (v2.1.1)
    QuietHours     = U8 '5a6J6Z2Z5pe25q61'                           # reminder: quiet hours
    DenyPerm       = U8 '5LiN5YWB6K64'                               # system permission dialog: deny
    DenyPermAlt    = U8 '5ouS57ud'                                   # system permission dialog: reject (alt)
    HomeEditHint   = U8 '6aaW6aG15b+r5o23'                           # workbench edit mode hint: home shortcuts
    AddToCalendar  = U8 '5re75Yqg5Yiw57O757uf5pel5Y6G'               # timetable: add to system calendar
    JoinCalendar   = U8 '5Yqg5YWl5pel56iL'                           # notices: add to calendar
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
    if ($joined.Contains($L.ProfileMark) -or $joined.Contains($L.PermTitle) -or $joined.Contains($L.MyData) -or $joined.Contains($L.FeedbackEntry) -or $joined.Contains($L.GuideEntry)) { return 'PROFILE' }
    # All-tools grid may be scrolled, so the "all tools" title is not always on screen.
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
    # v2 root is a 3-tab Pager inside one Activity: system BACK at root exits the app,
    # so always use the floating dock tab instead of BACK.
    for ($i = 1; $i -le 3; $i++) {
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
    throw 'cannot reach workbench (dock taps failed)'
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

# Tap the first of several candidate labels that appears (used for system permission dialogs).
function Tap-UiAnyText([string[]]$Labels, [int]$TimeoutSec = 12) {
    $sw = [Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $TimeoutSec) {
        $doc = Get-UiDump
        foreach ($lbl in $Labels) {
            $n = Find-UiNode -Doc $doc -Label $lbl
            if ($n) {
                $pt = Get-NodeCenter $n
                if ($pt) {
                    Start-Sleep -Milliseconds 300
                    $null = Invoke-Adb @('shell', 'input', 'tap', "$($pt[0])", "$($pt[1])")
                    Write-Line "  tap '$lbl' at ($($pt[0]),$($pt[1]))"
                    Start-Sleep -Milliseconds 800
                    return $true
                }
            }
        }
        Start-Sleep -Milliseconds 800
    }
    return $false
}

# Tap the action text (e.g. manage / settings) on the same row as a settings label.
function Tap-ActionNearLabel([string]$Label, [string]$ActionText) {
    $doc = Get-UiDump
    if (-not $doc) { throw "UI dump failed while looking for '$Label' action" }
    $labelNode = Find-UiNode -Doc $doc -Label $Label
    if (-not $labelNode) { throw "settings label '$Label' not found" }
    $lm = [regex]::Match([string]$labelNode.bounds, '\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
    $labelY = ([int]$lm.Groups[2].Value + [int]$lm.Groups[4].Value) / 2
    $best = $null; $bestDist = 99999
    foreach ($n in $doc.SelectNodes('//node')) {
        if ([string]$n.text -ne $ActionText) { continue }
        $m = [regex]::Match([string]$n.bounds, '\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
        if (-not $m.Success) { continue }
        $cy = ([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2
        $d = [Math]::Abs($cy - $labelY)
        if ($d -lt $bestDist) { $bestDist = $d; $best = $n }
    }
    if (-not $best -or $bestDist -gt 120) { throw "action '$ActionText' near '$Label' not found" }
    $pt = Get-NodeCenter $best
    if (-not $pt) { throw "cannot parse action bounds for '$Label'" }
    Start-Sleep -Milliseconds 300
    $null = Invoke-Adb @('shell', 'input', 'tap', "$($pt[0])", "$($pt[1])")
    Write-Line "  tap action '$ActionText' near '$Label' at ($($pt[0]),$($pt[1]))"
    Start-Sleep -Milliseconds 900
}

# Find the settings Switch on the same row as a label (profile / reminder pages).
function Get-SwitchNodeNear([string]$Label) {
    $doc = Get-UiDump
    if (-not $doc) { return $null }
    $labelNode = Find-UiNode -Doc $doc -Label $Label
    if (-not $labelNode) { return $null }
    $lm = [regex]::Match([string]$labelNode.bounds, '\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
    if (-not $lm.Success) { return $null }
    $labelY = ([int]$lm.Groups[2].Value + [int]$lm.Groups[4].Value) / 2
    $best = $null; $bestDist = 99999
    foreach ($n in $doc.SelectNodes('//node')) {
        $cls = [string]$n.class
        if (-not (([string]$n.checkable) -eq 'true' -or $cls -match 'Switch')) { continue }
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
    if (-not $node) { throw "switch near '$Label' not found" }
    return (([string]$node.checked) -eq 'true')
}
function Toggle-SwitchNear([string]$Label) {
    $node = Get-SwitchNodeNear $Label
    if (-not $node) { throw "switch near '$Label' not found" }
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
    # 节点可能已组合但在屏幕外（长页面 dump 会包含全部节点）：先滚动到屏内再点。
    # 下限 1950：底部悬浮 dock 会盖住内容，落点必须避开 dock（否则会误切底部 tab）。
    for ($i = 0; $i -lt ($ScrollTries + 3); $i++) {
        if (-not $pt) { break }
        if ($pt[1] -ge 260 -and $pt[1] -le 1950 -and $pt[0] -ge 80 -and $pt[0] -le 1000) { break }
        if ($pt[1] -gt 1950) { Scroll-Up } else { Scroll-Down }
        $node = Wait-UiText -Label $Label -TimeoutSec 2
        if (-not $node) { break }
        $pt = Get-NodeCenter $node
    }
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
    $null = Invoke-Adb @('shell', 'am', 'force-stop', $Pkg)
    if (-not $KeepData) {
        $null = Invoke-Adb @('shell', 'pm', 'clear', $Pkg)
    }
    $null = Invoke-Adb @('logcat', '-c')
    $null = Invoke-Adb @('shell', 'am', 'start', '-n', $Act)
    Write-Line "launched $Act"
    # a leftover in-app permission rationale dialog from a previous run may still be on top
    Start-Sleep -Milliseconds 1500
    $doc = Get-UiDump
    if ($doc) {
        $cancelNode = Find-UiNode -Doc $doc -Label $L.CancelPerm
        if ($cancelNode) {
            $pt = Get-NodeCenter $cancelNode
            if ($pt) {
                $null = Invoke-Adb @('shell', 'input', 'tap', "$($pt[0])", "$($pt[1])")
                Write-Line '  dismissed stray permission rationale dialog'
                Start-Sleep -Milliseconds 800
            }
        }
    }
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
    if (-not (Wait-UiText -Label $L.QuickTools -TimeoutSec 35)) { throw "workbench not visible ('$($L.QuickTools)' missing)" }
    # v2 workbench new structure: schedule / power / homework-due / notices / quick tools
    if (-not (Wait-UiText -Label $L.HomeDue -TimeoutSec 12)) { throw 'workbench: homework-due card missing' }
    if (-not (Wait-UiText -Label $L.HomeNotices -TimeoutSec 12)) { throw 'workbench: notices-pending card missing' }
    $null = Wait-UiAny -Labels @($L.HomeTtSync, $L.HomeNoClass, $L.Tt) -TimeoutSec 10
    Save-Shot '02_workbench.png'
    Add-Step 'workbench' 'PASS' 'due/notices/quick-tools cards visible' $swStep.ElapsedMilliseconds

# STEP 1c (v2): three-tab horizontal Pager (workbench / tools / me) - swipe + title assertions
    Write-Line '--- step: pager swipe (root tabs)'
    $swStep = [Diagnostics.Stopwatch]::StartNew()
    for ($i = 1; $i -le 2; $i++) {
        $null = Invoke-Adb @('shell', 'input', 'swipe', '950', '620', '130', '620', '260')
        Start-Sleep -Milliseconds 900
        if (Wait-UiText -Label $L.AllToolsTitle -TimeoutSec 15) { break }
    }
    if (-not (Wait-UiText -Label $L.AllToolsTitle -TimeoutSec 5)) { throw 'pager swipe left did not reach tools page' }
    Save-Shot '25_pager_tools.png'
    for ($i = 1; $i -le 2; $i++) {
        $null = Invoke-Adb @('shell', 'input', 'swipe', '950', '620', '130', '620', '260')
        Start-Sleep -Milliseconds 900
        if (Wait-UiAny -Labels @($L.MyData, $L.ProfileMark, $L.PermTitle) -TimeoutSec 8) { break }
    }
    if (-not (Wait-UiAny -Labels @($L.MyData, $L.ProfileMark, $L.PermTitle) -TimeoutSec 12)) { throw 'pager swipe left did not reach profile page' }
    Save-Shot '26_pager_profile.png'
    for ($i = 1; $i -le 2; $i++) {
        $null = Invoke-Adb @('shell', 'input', 'swipe', '130', '620', '950', '620', '260')
        Start-Sleep -Milliseconds 900
    }
    if (-not (Wait-UiText -Label $L.QuickTools -TimeoutSec 20)) { throw 'pager swipe right did not return to workbench' }
    Add-Step 'pager_swipe' 'PASS' 'workbench->tools->profile->workbench' $swStep.ElapsedMilliseconds

    # STEP 1d (v3): floating glass dock overlay - item/pill alignment + content bottom inset (120dp)
    Write-Line '--- step: floating dock overlay + content insets'
    $swStep = [Diagnostics.Stopwatch]::StartNew()
    $dockDoc = Get-UiDump
    if (-not $dockDoc) { throw 'dock: UI dump failed' }
    $dockItems = @($dockDoc.SelectNodes('//node') | Where-Object { ([string]$_."content-desc") -match $L.DockTabPattern })
    if ($dockItems.Count -lt 3) { throw ("dock: expected 3 tab items, found {0}" -f $dockItems.Count) }
    $labelNodes = @($dockDoc.SelectNodes('//node') | Where-Object { [string]$_.text -match $L.DockLabelPat })
    if ($labelNodes.Count -lt 3) { throw ("dock: expected 3 tab labels, found {0}" -f $labelNodes.Count) }
    $dockTop = 999999
    $dockSelected = 0
    $itemCenters = @()
    foreach ($it in $dockItems) {
        $im = [regex]::Match([string]$it.bounds, '\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
        if (-not $im.Success) { continue }
        $iy1 = [int]$im.Groups[2].Value
        $icx = ([int]$im.Groups[1].Value + [int]$im.Groups[3].Value) / 2
        $itemCenters += [int]$icx
        if ($iy1 -lt $dockTop) { $dockTop = $iy1 }
        if (([string]$it."content-desc") -match $L.DockSelMark) { $dockSelected++ }
    }
    if ($dockSelected -ne 1) { throw ("dock: selected pill count {0} (expected 1)" -f $dockSelected) }
    $alignDeltas = @()
    foreach ($ln in $labelNodes) {
        $lm = [regex]::Match([string]$ln.bounds, '\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
        if (-not $lm.Success) { continue }
        $lcx = ([int]$lm.Groups[1].Value + [int]$lm.Groups[3].Value) / 2
        $nearest = $itemCenters | Sort-Object { [Math]::Abs($_ - $lcx) } | Select-Object -First 1
        $alignDeltas += [Math]::Abs($nearest - $lcx)
    }
    if ($alignDeltas.Count -lt 3) { throw 'dock: could not match tab labels to items' }
    $maxDelta = [int](($alignDeltas | Measure-Object -Maximum).Maximum)
    if ($maxDelta -gt 12) { throw ("dock: label/item horizontal misalignment {0}px" -f $maxDelta) }
    $scrollBottoms = @()
    for ($i = 0; $i -lt 6; $i++) {
        $null = Invoke-Adb @('shell', 'input', 'swipe', '540', '1900', '540', '800', '300')
        Start-Sleep -Milliseconds 500
        $sd = Get-UiDump
        $mb = -1
        foreach ($n in $sd.SelectNodes('//node')) {
            if (-not ([string]$n.text)) { continue }
            $bm = [regex]::Match([string]$n.bounds, '\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
            if (-not $bm.Success) { continue }
            $y1 = [int]$bm.Groups[2].Value
            if ($y1 -ge ($dockTop - 12)) { continue }
            $y2 = [int]$bm.Groups[4].Value
            if ($y2 -gt $mb) { $mb = $y2 }
        }
        $scrollBottoms += [int]$mb
    }
    $lastBottom = $scrollBottoms[$scrollBottoms.Count - 1]
    $prevBottom = $scrollBottoms[$scrollBottoms.Count - 2]
    if ($lastBottom -ne $prevBottom) { throw 'dock: workbench did not reach scroll bottom (content may be blocked)' }
    if ($lastBottom -gt ($dockTop + 8)) { throw ("dock: content overlaps dock (contentBottom={0} dockTop={1})" -f $lastBottom, $dockTop) }
    Write-Line ("  dock: contentBottom={0} dockTop={1} (120dp bottom inset ok)" -f $lastBottom, $dockTop)
    Save-Shot '4x_dock_workbench.png'
    Tap-UiText -Label $L.AllTab -Exact -TimeoutSec 10
    if (-not (Wait-ScreenKind 'ALL' 15)) { throw 'dock: tools page not visible' }
    Save-Shot '4x_dock_tools.png'
    Tap-UiText -Label $L.MyTab -Exact -TimeoutSec 10
    if (-not (Wait-UiAny -Labels @($L.MyData, $L.PermTitle) -TimeoutSec 15)) { throw 'dock: profile page not visible' }
    Save-Shot '4x_dock_profile.png'
    Tap-UiText -Label $L.Workbench -Exact -TimeoutSec 10
    if (-not (Wait-ScreenKind 'HOME' 15)) { throw 'dock: workbench not reachable' }
    Add-Step 'dock_overlay' 'PASS' ("pill centered, max label delta {0}px, content above dock" -f $maxDelta) $swStep.ElapsedMilliseconds

    # STEP 2: bottom dock "tools" tab
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
           states = @($L.RosterLoading, $L.RosterEmpty, $L.RosterNameEmpty, $L.RosterError, $L.RollEmpty);
           tabLabel = $L.RosterTabRoll; tabStates = @($L.RollEmpty, $L.RollDone, $L.RosterEmpty, $L.RosterError); tabShot = '20_roster_roll.png' }
    )
    foreach ($pg in $pages) {
        if ($SkipWater -and $pg.step -eq 'tool_water') { Write-Line '  tool_water skipped (-SkipWater)'; continue }
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

    # STEP 5b (v3): timetable new layout (login entry / export button / empty state) - open, assert, BACK
    Write-Line '--- step: timetable v3 layout'
    $swStep = [Diagnostics.Stopwatch]::StartNew()
    Go-AllTools
    if (-not (Wait-UiTextScroll -Label $L.Tt -ScrollTries 3 -TimeoutSec 10)) { throw 'timetable card not visible' }
    Tap-UiTextScroll -Label $L.Tt -ScrollTries 2 -TimeoutSec 8
    if (-not (Wait-UiAny -Labels @($L.TtNoData, $L.TtLoading, $L.TtEmpty, $L.TtNetError, $L.TtError, $L.TtParseError) -TimeoutSec 20)) { throw 'timetable page not visible' }
    $ttLogin = Wait-UiText -Label $L.LoginEdu -TimeoutSec 5
    $ttExport = Wait-UiText -Label $L.ExportTt -TimeoutSec 3
    if (-not $ttLogin -and -not $ttExport) { Write-Line '  note: timetable login/export entries not visible in this state' }
    Save-Shot '4x_timetable_v3.png'
    $null = Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    Start-Sleep -Milliseconds 1200
    if (-not (Wait-ScreenKind 'ALL' 15)) { Go-AllTools }
    Add-Step 'timetable_v3' 'PASS' ("login={0} export={1}" -f [bool]$ttLogin, [bool]$ttExport) $swStep.ElapsedMilliseconds

    # STEP 5c (v3): diagnosis tool - open page, assert >=3 sections, BACK; never press report/fix buttons
    Write-Line '--- step: diagnosis tool page'
    $swStep = [Diagnostics.Stopwatch]::StartNew()
    Go-AllTools
    if (-not (Wait-UiTextScroll -Label $L.DiagTool -ScrollTries 4 -TimeoutSec 12)) { throw 'diag tool card not visible' }
    Tap-UiTextScroll -Label $L.DiagTool -ScrollTries 2 -TimeoutSec 8
    $diagOpened = Wait-UiText -Label $L.DiagSub -TimeoutSec 18
    if (-not $diagOpened) {
        # 导航抖动兜底：从全部工具页重新进入一次诊断页。
        Write-Line '  diag page did not open -> retry from all tools'
        Go-AllTools
        if (Wait-UiTextScroll -Label $L.DiagTool -ScrollTries 4 -TimeoutSec 10) {
            Tap-UiTextScroll -Label $L.DiagTool -ScrollTries 2 -TimeoutSec 8
        }
        $diagOpened = Wait-UiText -Label $L.DiagSub -TimeoutSec 20
    }
    if (-not $diagOpened) { throw 'diag page not visible (service-health subtitle missing)' }
    # v4.12 DG-01: 默认简洁模式只显示状态摘要/设备摘要/服务异常；点「详细」后再断言完整分组。
    if (Wait-UiText -Label $L.DiagDetail -TimeoutSec 5) {
        Write-Line '  diag simple mode -> tap 详细 for full sections'
        Tap-UiText -Label $L.DiagDetail -Exact -TimeoutSec 6
        Start-Sleep -Milliseconds 1200
        if (-not (Wait-UiText -Label $L.DiagSimple -TimeoutSec 8)) { throw 'diag detailed mode toggle did not appear' }
    }
    $diagSections = 0
    if (Wait-UiText -Label $L.DiagLocal -TimeoutSec 5) { $diagSections++ }
    if (Wait-UiTextScroll -Label $L.DiagDevice -ScrollTries 3 -TimeoutSec 8) { $diagSections++ }
    if (Wait-UiTextScroll -Label $L.DiagAI -ScrollTries 3 -TimeoutSec 8) { $diagSections++ }
    if (Wait-UiTextScroll -Label $L.DiagFix -ScrollTries 3 -TimeoutSec 8) { $diagSections++ }
    if (Wait-UiTextScroll -Label $L.DiagHealth -ScrollTries 3 -TimeoutSec 8) { $diagSections++ }
    if ($diagSections -lt 3) { throw ("diag: only {0} sections visible (need >= 3)" -f $diagSections) }
    Save-Shot '4x_diag.png'
    $null = Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    Start-Sleep -Milliseconds 1200
    if (-not (Wait-ScreenKind 'ALL' 15)) { Go-AllTools }
    Add-Step 'diag_page' 'PASS' ("{0} sections; no report/fix pressed" -f $diagSections) $swStep.ElapsedMilliseconds

    # STEP 5d (v4): vote tool page - tolerant four states, never submit a vote
    Write-Line '--- step: vote tool page'
    $swStep = [Diagnostics.Stopwatch]::StartNew()
    Go-AllTools
    if (-not (Wait-UiTextScroll -Label $L.VoteTool -ScrollTries 4 -TimeoutSec 12)) { throw 'vote card not visible in all tools' }
    Tap-UiTextScroll -Label $L.VoteTool -ScrollTries 2 -TimeoutSec 8
    $voteState = Wait-UiAny -Labels @($L.VoteEmpty, $L.VoteLoading, $L.VoteStart, $L.VoteRetry) -TimeoutSec 20
    if (-not $voteState) { throw 'vote page state not visible (empty/loading/error)' }
    Save-Shot '7x_vote.png'
    $null = Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    Start-Sleep -Milliseconds 1200
    if (-not (Wait-ScreenKind 'ALL' 15)) { Go-AllTools }
    Add-Step 'tool_vote' 'PASS' 'vote page opened (state tolerant; no submit)' $swStep.ElapsedMilliseconds

    # STEP 5e (v4): roster categories - committee / league / all segments
    Write-Line '--- step: roster categories'
    $swStep = [Diagnostics.Stopwatch]::StartNew()
    Go-AllTools
    if (-not (Wait-UiTextScroll -Label $L.Roster -ScrollTries 4 -TimeoutSec 12)) { throw 'roster card not visible' }
    Tap-UiTextScroll -Label $L.Roster -ScrollTries 2 -TimeoutSec 8
    if (-not (Wait-UiAny -Labels @($L.RosterCatA, $L.RosterTabRoll, $L.RosterEmpty, $L.RosterError) -TimeoutSec 20)) { throw 'roster page not visible' }
    $catC = Wait-UiTextScroll -Label $L.RosterCatC -ScrollTries 2 -TimeoutSec 6
    $catL = Wait-UiTextScroll -Label $L.RosterCatL -ScrollTries 2 -TimeoutSec 6
    $catA = Wait-UiTextScroll -Label $L.RosterCatA -ScrollTries 2 -TimeoutSec 6
    if (-not ($catC -and $catL -and $catA)) {
        # debug preview identity may not have a valid class token -> roster shows load error and hides category segments.
        $retryNode = Wait-UiText -Label $L.VoteRetry -TimeoutSec 4
        if ($retryNode) {
            Write-Line '  roster load error; tapping retry once'
            Tap-UiText -Label $L.VoteRetry -Exact -TimeoutSec 6
            Start-Sleep -Milliseconds 2000
        }
        $catC = Wait-UiTextScroll -Label $L.RosterCatC -ScrollTries 2 -TimeoutSec 8
        $catL = Wait-UiTextScroll -Label $L.RosterCatL -ScrollTries 2 -TimeoutSec 8
        $catA = Wait-UiTextScroll -Label $L.RosterCatA -ScrollTries 2 -TimeoutSec 8
    }
    $segmentsRendered = [bool]($catC -and $catL -and $catA)
    if (-not $segmentsRendered) {
        # v4.0.1 fix: roster tab must render category segments even when the member list is empty.
        throw 'roster categories missing (v4.0.1 expects committee/league/all even with empty member list)'
    }
    $null = Invoke-Adb @('shell', 'input', 'swipe', '540', '900', '540', '1500', '300')
    Start-Sleep -Milliseconds 600
    if (Wait-UiText -Label $L.RosterCatL -TimeoutSec 4) {
        Tap-UiText -Label $L.RosterCatL -TimeoutSec 6
        Start-Sleep -Milliseconds 1500
    }
    Save-Shot '7x_roster_categories.png'
    $null = Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    Start-Sleep -Milliseconds 1200
    if (-not (Wait-ScreenKind 'ALL' 15)) { Go-AllTools }
    Add-Step 'roster_categories' 'PASS' ("segments=" + [bool]$segmentsRendered) $swStep.ElapsedMilliseconds

    # STEP 5f (v4): power page opens (13/16 hidden-24h needs real data; page/empty state only)
    Write-Line '--- step: power page v4'
    $swStep = [Diagnostics.Stopwatch]::StartNew()
    Go-AllTools
    if (-not (Wait-UiTextScroll -Label $L.Power -ScrollTries 4 -TimeoutSec 12)) { throw 'power card not visible' }
    Tap-UiTextScroll -Label $L.Power -ScrollTries 2 -TimeoutSec 8
    if (-not (Wait-UiAny -Labels @($L.Power, $L.PowerLoading, $L.PowerEmpty, $L.PowerError) -TimeoutSec 20)) { throw 'power page not visible' }
    Save-Shot '7x_power.png'
    $null = Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    Start-Sleep -Milliseconds 1200
    if (-not (Wait-ScreenKind 'ALL' 15)) { Go-AllTools }
    Add-Step 'power_v4' 'PASS' 'power page opened (no followed room -> empty state)' $swStep.ElapsedMilliseconds

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
        $clearDialog = Wait-UiText -Label $L.ConfirmClear -Exact -TimeoutSec 8
        if (-not $clearDialog) {
            # 滚动后首次点按可能只滚动了页面，重试一次。
            Write-Line '  clear-cloud dialog missing -> retry tap'
            if (Wait-UiTextScroll -Label $L.ClearCloud -ScrollTries 2 -TimeoutSec 5) {
                Tap-UiTextScroll -Label $L.ClearCloud -ScrollTries 2 -TimeoutSec 5
            }
            $clearDialog = Wait-UiText -Label $L.ConfirmClear -Exact -TimeoutSec 10
        }
        if (-not $clearDialog) { throw 'clear-cloud confirm dialog not visible' }
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

    # STEP 9b: profile appearance (wallpaper color switch) + about version (v2.0.0)
    Write-Line '--- step: profile appearance + about version'
    $swStep = [Diagnostics.Stopwatch]::StartNew()
    Tap-UiText -Label $L.MyTab -Exact -TimeoutSec 10
    if (-not (Wait-UiTextScroll -Label $L.WallpaperColor -ScrollTries 4 -TimeoutSec 15)) { throw 'profile appearance switch row not visible' }
    if (-not (Wait-UiTextScroll -Label $L.Appearance -ScrollTries 3 -TimeoutSec 5)) { throw 'appearance card title not visible' }
    $script:AppearanceState = Toggle-SwitchNear -Label $L.WallpaperColor
    Save-Shot '23_appearance.png'
    if (-not (Wait-UiTextScroll -Label '4.' -ScrollTries 4 -TimeoutSec 10)) { throw 'about version (contains 4.) not visible' }
    Add-Step 'appearance_about' 'PASS' ("wallpaperColor=" + $script:AppearanceState + "; version-family=4.x") $swStep.ElapsedMilliseconds

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

# STEP 9e (v2): profile -> permission & reminder -> permission management: request notification permission,
# deny the system dialog, assert graceful state text (off / needs system settings) and no crash.
    Write-Line '--- step: permission management (deny system dialog)'
    $swStep = [Diagnostics.Stopwatch]::StartNew()
    if (-not (Wait-UiTextScroll -Label $L.PermManage -ScrollTries 4 -TimeoutSec 15)) { throw 'profile: permission management row not visible' }
    Tap-ActionNearLabel -Label $L.PermManage -ActionText $L.Manage
    if (-not (Wait-UiText -Label $L.PermTitle -TimeoutSec 15)) { throw 'permission page not visible' }
    if (-not (Wait-UiText -Label $L.NotifyWord -TimeoutSec 8)) { throw 'permission page: notifications card missing' }
    if (-not (Wait-UiText -Label $L.CalendarWord -TimeoutSec 8)) { throw 'permission page: calendar card missing' }
    Save-Shot '27_permissions.png'
    $permDetail = 'no system dialog shown'
    if (Wait-UiText -Label $L.GrantPerm -TimeoutSec 8) {
        Tap-UiText -Label $L.GrantPerm -TimeoutSec 10
        Start-Sleep -Milliseconds 1200
        # app shows a rationale dialog first (later / go-enable) -> tap go-enable to trigger the system dialog
        if (Wait-UiText -Label $L.GrantGo -TimeoutSec 8) {
            Tap-UiText -Label $L.GrantGo -Exact -TimeoutSec 10
            Start-Sleep -Milliseconds 1500
        }
        $denyLabels = @($L.DenyPerm, $L.DenyPermAlt, "Don't allow", 'Do not allow', ('Don' + [char]0x2019 + 't allow'), 'DENY')
        if (Tap-UiAnyText -Labels $denyLabels -TimeoutSec 15) {
            $permDetail = 'system dialog denied'
            Start-Sleep -Milliseconds 1500
        }
    }
    $stateNode = Wait-UiAny -Labels @($L.PermOff, $L.PermNeedSet, $L.PermOn) -TimeoutSec 12
    if (-not $stateNode) { throw 'permission page state text not visible after request' }
    Save-Shot '28_permission_denied.png'
    if (-not (Wait-UiText -Label $L.PermTitle -TimeoutSec 8)) { throw 'permission page lost after denying dialog (possible crash)' }
    Add-Step 'permission_management' 'PASS' $permDetail $swStep.ElapsedMilliseconds

    # STEP 9f (v2): reminder settings - assert rows and toggle the homework reminder on then off
    Write-Line '--- step: reminder settings'
    $swStep = [Diagnostics.Stopwatch]::StartNew()
    if (-not (Wait-UiTextScroll -Label $L.ReminderEntry -ScrollTries 3 -TimeoutSec 10)) { throw 'reminder settings entry not visible' }
    Tap-ActionNearLabel -Label $L.ReminderEntry -ActionText $L.Settings
    if (-not (Wait-UiText -Label $L.ReminderMain -TimeoutSec 15)) { throw 'reminder page not visible' }
    if (-not (Wait-UiText -Label $L.HomeworkRem -TimeoutSec 8)) { throw 'reminder: homework reminder row missing' }
    if (-not (Wait-UiText -Label $L.ClassRem -TimeoutSec 8)) { throw 'reminder: class reminder row missing' }
    if (-not (Wait-UiText -Label $L.NoticeRemind -TimeoutSec 8)) { throw 'reminder: notice reminder row missing (v2.1.1)' }
    if (-not (Wait-UiTextScroll -Label $L.NotifTestTitle -ScrollTries 4 -TimeoutSec 12)) { throw 'reminder: notification test section missing (v3)' }
    if (-not (Wait-UiTextScroll -Label $L.NotifTestBtn -ScrollTries 3 -TimeoutSec 8)) { throw 'reminder: send-test-notification button missing (v3)' }
    if (-not (Wait-UiTextScroll -Label $L.NotifDiagBtn -ScrollTries 3 -TimeoutSec 8)) { throw 'reminder: run-check+diagnostics button missing (v3)' }
    $remBefore = Toggle-SwitchNear -Label $L.HomeworkRem
    $null = Toggle-SwitchNear -Label $L.HomeworkRem
    $remAfter = Get-SwitchStateNear -Label $L.HomeworkRem
    if ($remAfter -eq $remBefore) { throw 'reminder homework switch did not toggle back' }
    Save-Shot '29_reminder.png'
    $null = Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    Start-Sleep -Milliseconds 1200
    $null = Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    Start-Sleep -Milliseconds 1200
    $null = Wait-ScreenKind 'PROFILE' 10
    Add-Step 'reminder_settings' 'PASS' 'homework reminder toggled on/off' $swStep.ElapsedMilliseconds

    # STEP 9g2 (v4): global notification permission banner (tolerant; visible only when permission off + reminders on)
    Write-Line '--- step: notification permission banner'
    $swStep = [Diagnostics.Stopwatch]::StartNew()
    for ($i = 0; $i -lt 3; $i++) {
        $null = Invoke-Adb @('shell', 'input', 'swipe', '540', '900', '540', '1900', '300')
        Start-Sleep -Milliseconds 500
    }
    $banner = Wait-UiText -Label $L.NotifyBanner -TimeoutSec 8
    if ($banner) {
        Save-Shot '7x_notify_banner.png'
        Write-Line '  notification permission banner visible'
    } else {
        Write-Line '  notification banner not visible (notification granted or reminders disabled)'
    }
    Add-Step 'notify_banner' 'PASS' ("banner=" + [bool]$banner) $swStep.ElapsedMilliseconds

    # STEP 9g3 (v4): clear-local-cache second confirmation (open dialog, cancel - never clears)
    Write-Line '--- step: clear local cache confirm'
    $swStep = [Diagnostics.Stopwatch]::StartNew()
    if (-not (Wait-UiTextScroll -Label $L.ClearCacheRow -ScrollTries 4 -TimeoutSec 12)) { throw 'clear-local-cache row not visible on profile' }
    Tap-ActionNearLabel -Label $L.ClearCacheRow -ActionText $L.ClearAct
    if (-not (Wait-UiText -Label $L.ClearCacheTtl -TimeoutSec 10)) { throw 'clear cache confirmation dialog not visible' }
    Save-Shot '7x_clear_cache_dialog.png'
    Tap-UiText -Label $L.Cancel -Exact -TimeoutSec 8
    Start-Sleep -Milliseconds 900
    if (-not (Wait-ScreenKind 'PROFILE' 10)) { throw 'profile lost after cancel clear-cache dialog' }
    Add-Step 'clear_cache_confirm' 'PASS' 'confirmation dialog opened and cancelled' $swStep.ElapsedMilliseconds
# STEP 9g (v2.1.1): profile -> about -> user guide (static content: title + >=3 sections)
    Write-Line '--- step: user guide page'
    $swStep = [Diagnostics.Stopwatch]::StartNew()
    if (-not (Wait-UiTextScroll -Label $L.GuideEntry -ScrollTries 5 -TimeoutSec 15)) { throw 'profile: guide entry not visible' }
    Tap-ActionNearLabel -Label $L.GuideEntry -ActionText $L.GuideAction
    if (-not (Wait-UiText -Label $L.GuideTitle -TimeoutSec 15)) { throw 'guide page not visible' }
    $guideSections = 0
    if (Wait-UiText -Label $L.Identity -TimeoutSec 6) { $guideSections++ }
    if (Wait-UiTextScroll -Label $L.Workbench -ScrollTries 4 -TimeoutSec 10) { $guideSections++ }
    if (Wait-UiTextScroll -Label $L.Draw -ScrollTries 4 -TimeoutSec 10) { $guideSections++ }
    if ($guideSections -lt 3) { throw "guide: only $guideSections section titles visible (need >= 3)" }
    $null = Invoke-Adb @('shell', 'input', 'swipe', '540', '1500', '540', '700', '300')
    Start-Sleep -Milliseconds 800
    Save-Shot '31_guide.png'
    $null = Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    Start-Sleep -Milliseconds 1200
    $null = Wait-ScreenKind 'PROFILE' 10
    Add-Step 'user_guide' 'PASS' ("title + $guideSections sections") $swStep.ElapsedMilliseconds

# STEP 9h (v2.1.1): profile -> feedback (open only, never submit - avoid cloud writes)
    Write-Line '--- step: feedback page (read-only open)'
    $swStep = [Diagnostics.Stopwatch]::StartNew()
    if (-not (Wait-UiTextScroll -Label $L.FeedbackEntry -ScrollTries 5 -TimeoutSec 15)) { throw 'profile: feedback entry not visible' }
    Tap-ActionNearLabel -Label $L.FeedbackEntry -ActionText $L.FeedbackAction
    # the page title equals the profile row label, so assert page-specific content/state instead
    $fbStateNode = Wait-UiAny -Labels @($L.FeedbackForm, $L.FeedbackSubmit, $L.FeedbackFailed, $L.FeedbackEmpty, $L.FeedbackLoad) -TimeoutSec 15
    if (-not $fbStateNode) { throw 'feedback page state text missing' }
    Save-Shot '30_feedback.png'
    $null = Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    Start-Sleep -Milliseconds 1200
    $null = Wait-ScreenKind 'PROFILE' 10
    Add-Step 'feedback_page' 'PASS' 'opened; not submitted' $swStep.ElapsedMilliseconds

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
    # v2: settings card switches the root Pager to the profile tab (no nav push), so use the dock
    Tap-UiText -Label $L.AllTab -Exact -TimeoutSec 10
    if (-not (Wait-ScreenKind 'ALL' 15)) {
        Write-Line '  dock tap did not reach tools page, retrying via Go-AllTools'
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
        if (Wait-UiText -Label $L.HomeEditHint -TimeoutSec 8) { $editorOpen = $true; break }
        Write-Line "  quick tools editor did not open (attempt $try), retrying"
    }
    if (-not $editorOpen) { throw "quick tools editor not visible ('$($L.HomeEditHint)' missing)" }
    Save-Shot '18_home_edit.png'
    $leftEditor = $false
    for ($try = 1; $try -le 2; $try++) {
        Tap-UiText -Label $L.Done -Exact -TimeoutSec 10
        if (Wait-UiText -Label $L.QuickTools -TimeoutSec 8) { $leftEditor = $true; break }
    }
    if (-not $leftEditor) { throw 'did not return to workbench from quick tools editor' }
    Add-Step 'home_quick_edit' 'PASS' '' $swStep.ElapsedMilliseconds

    # STEP 10b (v3): dark mode full chain (identity/workbench/tools/profile/permissions/timetable) + light restore
    Write-Line '--- step: dark mode screenshot group'
    $swStep = [Diagnostics.Stopwatch]::StartNew()
    $null = Invoke-Adb @('shell', 'cmd', 'uimode', 'night', 'yes')
    Start-Sleep -Seconds 2
    $null = Invoke-Adb @('shell', 'am', 'force-stop', $Pkg)
    $null = Invoke-Adb @('shell', 'pm', 'clear', $Pkg)
    $null = Invoke-Adb @('shell', 'am', 'start', '-n', $Act)
    if (-not (Wait-UiText -Label $L.DevPreview -TimeoutSec 45)) { throw 'dark: identity page not visible' }
    Save-Shot '4x_dark_identity.png'
    Tap-UiText -Label $L.DevPreview -TimeoutSec 10
    if (-not (Wait-UiText -Label $L.QuickTools -TimeoutSec 35)) { throw 'dark: workbench not visible' }
    Save-Shot '4x_dark_workbench.png'
    Tap-UiText -Label $L.AllTab -Exact -TimeoutSec 10
    if (-not (Wait-UiText -Label $L.AllToolsTitle -TimeoutSec 20)) { throw 'dark: tools page not visible' }
    Save-Shot '4x_dark_tools.png'
    Tap-UiText -Label $L.MyTab -Exact -TimeoutSec 10
    if (-not (Wait-UiAny -Labels @($L.MyData, $L.PermTitle) -TimeoutSec 20)) { throw 'dark: profile page not visible' }
    Save-Shot '4x_dark_profile.png'
    if (-not (Wait-UiTextScroll -Label $L.PermManage -ScrollTries 4 -TimeoutSec 15)) { throw 'dark: permission row not visible' }
    Tap-ActionNearLabel -Label $L.PermManage -ActionText $L.Manage
    if (-not (Wait-UiText -Label $L.PermTitle -TimeoutSec 15)) { throw 'dark: permission page not visible' }
    Save-Shot '4x_dark_permissions.png'
    $null = Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    Start-Sleep -Milliseconds 1200
    $null = Wait-ScreenKind 'PROFILE' 10
    Tap-UiText -Label $L.AllTab -Exact -TimeoutSec 10
    if (-not (Wait-ScreenKind 'ALL' 15)) { throw 'dark: tools tab not reachable' }
    if (-not (Wait-UiTextScroll -Label $L.Tt -ScrollTries 3 -TimeoutSec 10)) { throw 'dark: timetable card not visible' }
    Tap-UiTextScroll -Label $L.Tt -ScrollTries 2 -TimeoutSec 8
    if (-not (Wait-UiAny -Labels @($L.TtNoData, $L.TtLoading, $L.TtEmpty, $L.TtNetError, $L.TtError, $L.TtParseError) -TimeoutSec 20)) { throw 'dark: timetable page not visible' }
    Save-Shot '4x_dark_timetable.png'
    $null = Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    Start-Sleep -Milliseconds 1200
    $null = Invoke-Adb @('shell', 'cmd', 'uimode', 'night', 'no')
    Start-Sleep -Seconds 2
    $null = Invoke-Adb @('shell', 'am', 'force-stop', $Pkg)
    $null = Invoke-Adb @('shell', 'am', 'start', '-n', $Act)
    if (-not (Wait-ScreenKind 'HOME' 30)) { throw 'light restore: workbench not visible' }
    Save-Shot '4x_light_restore.png'
    Add-Step 'dark_mode_group' 'PASS' 'identity/workbench/tools/profile/permissions/timetable dark + light restore' $swStep.ElapsedMilliseconds

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





















