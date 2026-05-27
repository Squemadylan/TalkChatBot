# Auto-Feature-Verify.ps1
# Feature verification based on PRD + complete build flow

param(
    [Parameter(Mandatory=$false)]
    [string]$ProjectDir = "e:\New\Dubaixia",

    [Parameter(Mandatory=$false)]
    [string]$LogFile = "e:\New\Dubaixia\feature-verify.log",

    [Parameter(Mandatory=$false)]
    [switch]$UseAndroidStudio
)

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

Write-Log "========================================" "HEADER"
Write-Log "Feature Verification Started" "HEADER"
Write-Log "========================================" "HEADER"

# Check 1: Bubble Style Constants
Write-Log "`n[Check 1/8] Bubble Style Constants (App.kt)" "HEADER"
$found1 = Verify-Code "CHAT_BUBBLE_STYLE_DEFAULT" "$ProjectDir\app\src\main\java" "CHAT_BUBBLE_STYLE_DEFAULT"
$found2 = Verify-Code "CHAT_BUBBLE_STYLE_COMPACT" "$ProjectDir\app\src\main\java" "CHAT_BUBBLE_STYLE_COMPACT"
$found3 = Verify-Code "CHAT_BUBBLE_STYLE_ROUNDED" "$ProjectDir\app\src\main\java" "CHAT_BUBBLE_STYLE_ROUNDED"
$found4 = Verify-Code "CHAT_BUBBLE_STYLE_TRANSLUCENT" "$ProjectDir\app\src\main\java" "CHAT_BUBBLE_STYLE_TRANSLUCENT"

# Check 2: Reply Style Constants
Write-Log "`n[Check 2/8] Reply Style Constants (App.kt)" "HEADER"
$found5 = Verify-Code "REPLY_STYLE_STANDARD" "$ProjectDir\app\src\main\java" "REPLY_STYLE_STANDARD"
$found6 = Verify-Code "REPLY_STYLE_SHORT" "$ProjectDir\app\src\main\java" "REPLY_STYLE_SHORT"
$found7 = Verify-Code "REPLY_STYLE_DETAILED" "$ProjectDir\app\src\main\java" "REPLY_STYLE_DETAILED"

# Check 3: Status Bar Immersive
Write-Log "`n[Check 3/8] Status Bar Immersive" "HEADER"
$found8 = Verify-Code "KEY_STATUS_BAR_IMMERSIVE" "$ProjectDir\app\src\main\java" "KEY_STATUS_BAR_IMMERSIVE"

# Check 4: Bubble Style UI
Write-Log "`n[Check 4/8] Bubble Style UI" "HEADER"
$found9 = Verify-Code "showBubbleStyleDialog" "$ProjectDir\app\src\main\java" "showBubbleStyleDialog"
$found10 = Verify-Code "applyBubbleAppearance" "$ProjectDir\app\src\main\java" "applyBubbleAppearance"

# Check 5: Reply Style UI
Write-Log "`n[Check 5/8] Reply Style UI" "HEADER"
$found11 = Verify-Code "showReplyStyleDialog" "$ProjectDir\app\src\main\java" "showReplyStyleDialog"

# Check 6: "Coming Soon" Text
Write-Log "`n[Check 6/8] Coming Soon Text" "HEADER"
$found12 = Verify-Code "Coming Soon / Jijiang Zhichi" "$ProjectDir\app\src\main\java" "(即将支持|Coming Soon)"

# Check 7: MessageAdapter Bubble Implementation
Write-Log "`n[Check 7/8] MessageAdapter Bubble Implementation" "HEADER"
$found13 = Verify-Code "bubbleStyle parameter" "$ProjectDir\app\src\main\java" "bubbleStyle.*Int"

# Check 8: Build Verification
Write-Log "`n[Check 8/8] Build Verification" "HEADER"

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
    }
} else {
    Write-Log "Build FAILED (exit code: $exitCode)" "ERROR"
}

# Summary
$totalChecks = 13
$passedChecks = ($found1,$found2,$found3,$found4,$found5,$found6,$found7,$found8,$found9,$found10,$found11,$found12,$found13 | Where-Object { $_ -eq $true }).Count

Write-Log "`n========================================" "HEADER"
Write-Log "VERIFICATION SUMMARY" "HEADER"
Write-Log "========================================" "HEADER"
Write-Log "Code Checks: $passedChecks / $totalChecks passed" $(if ($passedChecks -eq $totalChecks) { "SUCCESS" } else { "WARN" })
Write-Log "Build: $(if ($exitCode -eq 0) { 'SUCCESS' } else { 'FAILED' })" $(if ($exitCode -eq 0) { "SUCCESS" } else { "ERROR" })

if ($UseAndroidStudio) {
    Write-Log "`n[Android Studio] Opening project..." "HEADER"
    $studioPath = "C:\Program Files\Android\Android Studio\bin\studio64.exe"
    if (Test-Path $studioPath) {
        Start-Process $studioPath -ArgumentList "$ProjectDir" -PassThru | Out-Null
        Write-Log "Android Studio launched for manual verification" "INFO"
    }
}

Write-Log "========================================" "HEADER"

exit $exitCode