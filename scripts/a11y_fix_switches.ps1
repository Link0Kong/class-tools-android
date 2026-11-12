# Add accessibility labels to bare Switch controls (a11y audit fix).
$ErrorActionPreference = 'Stop'
$base = 'C:\Users\Ko_Li\.openclaw\workspace\android-class-tools\app\src\main\kotlin\com\furina\classtools\ui\screens'

$files = @(
    (Join-Path $base 'draw\DrawSections.kt'),
    (Join-Path $base 'group\GroupScreen.kt'),
    (Join-Path $base 'profile\ProfileScreen.kt'),
    (Join-Path $base 'settings\ReminderSettingsScreen.kt'),
    (Join-Path $base 'vote\VoteCreateScreen.kt')
)

foreach ($f in $files) {
    $t = Get-Content $f -Raw
    if ($t -notmatch 'import androidx.compose.ui.semantics.contentDescription') {
        $t = $t.Replace("import androidx.compose.ui.Modifier", "import androidx.compose.ui.Modifier`r`nimport androidx.compose.ui.semantics.contentDescription`r`nimport androidx.compose.ui.semantics.semantics")
    }
    Set-Content -Path $f -Value $t -Encoding UTF8 -NoNewline
}

# draw: allow duplicates
$f = $files[0]
$t = Get-Content $f -Raw
$t = $t.Replace(@"
                Switch(
                    checked = config.allowDuplicates,
                    onCheckedChange = { onConfigChange(config.copy(allowDuplicates = it)) },
                    enabled = enabled,
                )
"@, @"
                Switch(
                    checked = config.allowDuplicates,
                    onCheckedChange = { onConfigChange(config.copy(allowDuplicates = it)) },
                    enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = "允许重复" },
                )
"@)
$t = $t.Replace("Switch(checked = autoRedraw, onCheckedChange = onToggleAutoRedraw)", 'Switch(checked = autoRedraw, onCheckedChange = onToggleAutoRedraw, modifier = Modifier.semantics { contentDescription = "剔除后自动重抽" })')
Set-Content -Path $f -Value $t -Encoding UTF8 -NoNewline

# group: shuffle
$f = $files[1]
$t = Get-Content $f -Raw
$t = $t.Replace(@"
                Switch(
                    checked = config.shuffle,
                    onCheckedChange = { onUpdate(config.copy(shuffle = it)) },
                )
"@, @"
                Switch(
                    checked = config.shuffle,
                    onCheckedChange = { onUpdate(config.copy(shuffle = it)) },
                    modifier = Modifier.semantics { contentDescription = "打乱后再分组" },
                )
"@)
Set-Content -Path $f -Value $t -Encoding UTF8 -NoNewline

# profile: row switch label
$f = $files[2]
$t = Get-Content $f -Raw
$t = $t.Replace("Switch(checked = checked, onCheckedChange = onCheckedChange)", 'Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.semantics { contentDescription = label })')
Set-Content -Path $f -Value $t -Encoding UTF8 -NoNewline

# reminder settings: title as label
$f = $files[3]
$t = Get-Content $f -Raw
$t = $t.Replace(@"
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                )
"@, @"
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = title },
                )
"@)
Set-Content -Path $f -Value $t -Encoding UTF8 -NoNewline

# vote create: title as label
$f = $files[4]
$t = Get-Content $f -Raw
$t = $t.Replace("Switch(checked = checked, onCheckedChange = onCheckedChange)", 'Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.semantics { contentDescription = title })')
Set-Content -Path $f -Value $t -Encoding UTF8 -NoNewline

"switch labels patched"
