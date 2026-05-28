# Auto-Feature-Verify.ps1
# Feature verification based on PRD + complete build flow

param(
    [Parameter(Mandatory=$false)]
    [string]$ProjectDir,

    [Parameter(Mandatory=$false)]
    [string]$LogFile,

    [Parameter(Mandatory=$false)]
    [switch]$UseAndroidStudio
)

. (Join-Path $PSScriptRoot '..\lib\Init-Automation.ps1')
if (-not $ProjectDir) { $ProjectDir = $RepoRoot }
if (-not $LogFile) { $LogFile = Join-Path $Paths.LocalLogs 'feature-verify.log' }

$ErrorActionPreference = "Continue"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

function Write-Log {
    param([string]$Message, [string]$Level = "INFO")
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $logEntry = "[$timestamp] [$Level] $Message"
    Add-Content -Path $LogFile -Value $logEntry -Encoding UTF8
    $color = switch ($Level) {
        "INFO" { "Cyan" }
        "WARN" { "Yellow" }
        "ERROR" { "Red" }
        "SUCCESS" { "Green" }
        "HEADER" { "Magenta" }
        default { "White" }
    }
    Write-Host $logEntry -ForegroundColor $color
}

function Find-JavaHome {
    $javaPaths = @(
        "C:\Program Files\Android\Android Studio\jbr",
        "C:\Program Files\Android\Android Studio\jre",
        "C:\Program Files\Java\jdk-17",
        "${env:JAVA_HOME}"
    )
    foreach ($path in $javaPaths) {
        if ($path -and (Test-Path "$path\bin\java.exe")) {
            return $path
        }
    }
    return $null
}

function Verify-Code {
    param([string]$Name, [string]$Path, [string]$Pattern)
    $files = Get-ChildItem -Path $Path -Filter "*.kt" -Recurse -ErrorAction SilentlyContinue
    foreach ($file in $files) {
        $content = Get-Content $file.FullName -Raw -Encoding UTF8
        if ($content -match $Pattern) {
            Write-Log "[FOUND] $Name - $($file.Name)" "SUCCESS"
            return $true
        }
    }
    Write-Log "[MISSING] $Name" "ERROR"
    return $false
}

function Verify-Xml {
    param([string]$Name, [string]$Path, [string]$Pattern)
    $files = Get-ChildItem -Path $Path -Filter "*.xml" -Recurse -ErrorAction SilentlyContinue
    foreach ($file in $files) {
        $content = Get-Content $file.FullName -Raw -Encoding UTF8
        if ($content -match $Pattern) {
            Write-Log "[FOUND] $Name - $($file.Name)" "SUCCESS"
            return $true
        }
    }
    Write-Log "[MISSING] $Name" "ERROR"
    return $false
}

Write-Log "========================================" "HEADER"
Write-Log "Feature Verification Started" "HEADER"
Write-Log "========================================" "HEADER"

# =============================================
# v1.3 Features (Bubble Style, Reply Style)
# =============================================

Write-Log "`n[=== v1.3 Features: Bubble Style & Reply Style ===]" "HEADER"

# Bubble Style Constants
Write-Log "`n[Check 1] Bubble Style Constants" "INFO"
$found1 = Verify-Code "CHAT_BUBBLE_STYLE_DEFAULT" "$ProjectDir\app\src\main\java" "CHAT_BUBBLE_STYLE_DEFAULT"
$found2 = Verify-Code "CHAT_BUBBLE_STYLE_COMPACT" "$ProjectDir\app\src\main\java" "CHAT_BUBBLE_STYLE_COMPACT"
$found3 = Verify-Code "CHAT_BUBBLE_STYLE_ROUNDED" "$ProjectDir\app\src\main\java" "CHAT_BUBBLE_STYLE_ROUNDED"
$found4 = Verify-Code "CHAT_BUBBLE_STYLE_TRANSLUCENT" "$ProjectDir\app\src\main\java" "CHAT_BUBBLE_STYLE_TRANSLUCENT"

# Reply Style Constants
Write-Log "`n[Check 2] Reply Style Constants" "INFO"
$found5 = Verify-Code "REPLY_STYLE_STANDARD" "$ProjectDir\app\src\main\java" "REPLY_STYLE_STANDARD"
$found6 = Verify-Code "REPLY_STYLE_SHORT" "$ProjectDir\app\src\main\java" "REPLY_STYLE_SHORT"
$found7 = Verify-Code "REPLY_STYLE_DETAILED" "$ProjectDir\app\src\main\java" "REPLY_STYLE_DETAILED"

# Status Bar Immersive
Write-Log "`n[Check 3] Status Bar Immersive" "INFO"
$found8 = Verify-Code "KEY_STATUS_BAR_IMMERSIVE" "$ProjectDir\app\src\main\java" "KEY_STATUS_BAR_IMMERSIVE"

# Bubble Style UI
Write-Log "`n[Check 4] Bubble Style UI" "INFO"
$found9 = Verify-Code "showBubbleStyleDialog" "$ProjectDir\app\src\main\java" "showBubbleStyleDialog"
$found10 = Verify-Code "applyBubbleAppearance" "$ProjectDir\app\src\main\java" "applyBubbleAppearance"

# Reply Style UI
Write-Log "`n[Check 5] Reply Style UI" "INFO"
$found11 = Verify-Code "showReplyStyleDialog" "$ProjectDir\app\src\main\java" "showReplyStyleDialog"

# Coming Soon Text (check showToast method used for unavailable features)
Write-Log "`n[Check 6] Coming Soon Text" "INFO"
$found12 = Verify-Code "showToast method" "$ProjectDir\app\src\main\java" "fun showToast"

# MessageAdapter Bubble Implementation
Write-Log "`n[Check 7] MessageAdapter Bubble Implementation" "INFO"
$found13 = Verify-Code "bubbleStyle parameter" "$ProjectDir\app\src\main\java" "bubbleStyle.*Int"

# =============================================
# v1.4 Features (API Connection Checker, Model Presets, Config Summary)
# =============================================

Write-Log "`n[=== v1.4 Features: API Connection Checker ===]" "HEADER"

# API Connection Checker
Write-Log "`n[Check 8] API Connection Checker" "INFO"
$found14 = Verify-Code "ApiConnectionChecker" "$ProjectDir\app\src\main\java" "object ApiConnectionChecker"
$found15 = Verify-Code "ApiCheckResult" "$ProjectDir\app\src\main\java" "data class ApiCheckResult"
$found16 = Verify-Code "ErrorType" "$ProjectDir\app\src\main\java" "enum class ErrorType"
$found17 = Verify-Code "checkConnection" "$ProjectDir\app\src\main\java" "suspend fun checkConnection"

# Error Types
Write-Log "`n[Check 9] Error Type Recognition" "INFO"
$found18 = Verify-Code "INVALID_KEY error" "$ProjectDir\app\src\main\java" "INVALID_KEY"
$found19 = Verify-Code "INSUFFICIENT_QUOTA error" "$ProjectDir\app\src\main\java" "INSUFFICIENT_QUOTA"
$found20 = Verify-Code "MODEL_NOT_FOUND error" "$ProjectDir\app\src\main\java" "MODEL_NOT_FOUND"
$found21 = Verify-Code "TIMEOUT error" "$ProjectDir\app\src\main\java" "TIMEOUT"

# Model Presets
Write-Log "`n[Check 10] Model Presets" "INFO"
$found22 = Verify-Code "MODEL_PRESETS" "$ProjectDir\app\src\main\java" "MODEL_PRESETS"
$found23 = Verify-Code "setupModelPresetSpinner" "$ProjectDir\app\src\main\java" "setupModelPresetSpinner"

# Config Summary
Write-Log "`n[Check 11] Config Summary" "INFO"
$found24 = Verify-Code "updateConfigSummary" "$ProjectDir\app\src\main\java" "updateConfigSummary"
$found25 = Verify-Code "copyConfigToClipboard" "$ProjectDir\app\src\main\java" "copyConfigToClipboard"
$found26 = Verify-Code "tvSummaryBaseUrl" "$ProjectDir\app\src\main\java" "tvSummaryBaseUrl"

# Check Connection Button
Write-Log "`n[Check 12] Check Connection Button" "INFO"
$found27 = Verify-Code "btnCheckConnection" "$ProjectDir\app\src\main\java" "btnCheckConnection"
$found28 = Verify-Code "performConnectionCheck" "$ProjectDir\app\src\main\java" "performConnectionCheck"

# XML Layout Checks
Write-Log "`n[Check 13] XML Layout Verification" "INFO"
$found29 = Verify-Xml "spinnerModelPreset in layout" "$ProjectDir\app\src\main\res\layout" "spinnerModelPreset"
$found30 = Verify-Xml "btnCheckConnection in layout" "$ProjectDir\app\src\main\res\layout" "btnCheckConnection"
$found31 = Verify-Xml "tvSummaryBaseUrl in layout" "$ProjectDir\app\src\main\res\layout" "tvSummaryBaseUrl"
$found32 = Verify-Xml "tvConnectionStatus in layout" "$ProjectDir\app\src\main\res\layout" "tvConnectionStatus"

# =============================================
# Build Verification
# =============================================

Write-Log "`n[=== Build Verification ===]" "HEADER"

$javaHome = Find-JavaHome
if ($javaHome) {
    $env:JAVA_HOME = $javaHome
    $env:PATH = "$javaHome\bin;$env:PATH"
    Write-Log "JAVA_HOME: $javaHome" "INFO"
}

$startTime = Get-Date
Write-Log "Running gradlew assembleDebug..." "INFO"

$output = Invoke-Expression "cd `"$ProjectDir`"; .\gradlew assembleDebug 2>&1"
$exitCode = $LASTEXITCODE

$output | Select-Object -Last 10 | ForEach-Object { Write-Log $_ "INFO" }

if ($exitCode -eq 0) {
    $elapsed = ((Get-Date) - $startTime).TotalSeconds
    Write-Log "Build SUCCESS (${elapsed}s)" "SUCCESS"

    $apk = Get-ChildItem -Path "$ProjectDir\app\build\outputs\apk\debug" -Filter "*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($apk) {
        Write-Log "APK: $($apk.FullName)" "SUCCESS"
        Write-Log "Size: $([math]::Round($apk.Length / 1MB, 2)) MB" "SUCCESS"
    } else {
        Write-Log "APK not found in debug folder" "WARN"
    }
} else {
    Write-Log "Build FAILED (exit code: $exitCode)" "ERROR"
}

# =============================================
# Summary
# =============================================

$totalChecks = 32
$passedChecks = ($found1,$found2,$found3,$found4,$found5,$found6,$found7,$found8,$found9,$found10,$found11,$found12,$found13,$found14,$found15,$found16,$found17,$found18,$found19,$found20,$found21,$found22,$found23,$found24,$found25,$found26,$found27,$found28,$found29,$found30,$found31,$found32 | Where-Object { $_ -eq $true }).Count

Write-Log "`n========================================" "HEADER"
Write-Log "VERIFICATION SUMMARY" "HEADER"
Write-Log "========================================" "HEADER"
Write-Log "Total Checks: $totalChecks" "INFO"
Write-Log "Passed: $passedChecks" $(if ($passedChecks -eq $totalChecks) { "SUCCESS" } else { "WARN" })
Write-Log "Failed: $($totalChecks - $passedChecks)" $(if ($passedChecks -lt $totalChecks) { "ERROR" } else { "SUCCESS" })
Write-Log "Build: $(if ($exitCode -eq 0) { 'SUCCESS' } else { 'FAILED' })" $(if ($exitCode -eq 0) { "SUCCESS" } else { "ERROR" })

if ($UseAndroidStudio) {
    Write-Log "`n[Android Studio] Opening project..." "HEADER"
    $studioPath = "C:\Program Files\Android\Android Studio\bin\studio64.exe"
    if (Test-Path $studioPath) {
        Start-Process $studioPath -ArgumentList "$ProjectDir" -PassThru | Out-Null
        Write-Log "Android Studio launched for manual verification" "INFO"
        Write-Log "Please verify manually:" "WARN"
        Write-Log "  1. Run app on emulator/device" "WARN"
        Write-Log "  2. Go to Config page" "WARN"
        Write-Log "  3. Test Check Connection button" "WARN"
        Write-Log "  4. Test Model Preset spinner" "WARN"
        Write-Log "  5. Test Copy Config button" "WARN"
    }
}

Write-Log "========================================" "HEADER"

exit $exitCode