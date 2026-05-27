# Auto-Update-Loop.ps1
# Automated code update loop: Monitor code changes -> Auto build -> Output APK
# Trigger: Auto-detect (triggered when code changes)
# Output: APK installer

param(
    [Parameter(Mandatory = $false)]
    [int]$WatchIntervalSeconds = 30,

    [Parameter(Mandatory = $false)]
    [string]$BuildCommand = "e:\New\Dubaixia\Auto-Build.ps1",

    [Parameter(Mandatory = $false)]
    [string]$LogFile = "e:\New\Dubaixia\auto-update.log"
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
        default { "White" }
    }
    Write-Host $logEntry -ForegroundColor $color
}

function Get-LastCommitHash {
    git -C "e:\New\Dubaixia" rev-parse HEAD 2>$null
}

function Get-ChangedFilesCount {
    git -C "e:\New\Dubaixia" status --porcelain 2>$null | Measure-Object -Line | Select-Object -ExpandProperty Lines
}

function Invoke-Build {
    Write-Log "Code change detected, starting automated build..." "WARN"

    $publishScript = "e:\New\Dubaixia\Auto-Publish.ps1"
    if (Test-Path $publishScript) {
        Write-Log "Executing publish script: $publishScript" "INFO"
        try {
            $result = & $publishScript -SkipTest
            Write-Log "Publish completed: $result" "SUCCESS"
            return $true
        } catch {
            Write-Log "Publish failed: $_" "ERROR"
            return $false
        }
    } else {
        Write-Log "Publish script not found: $publishScript" "ERROR"
        return $false
    }
}

# Main loop
Write-Log "========================================" "INFO"
Write-Log "Automated Update Loop Started" "INFO"
Write-Log "Watch Interval: ${WatchIntervalSeconds}s" "INFO"
Write-Log "Watch Path: e:\New\Dubaixia" "INFO"
Write-Log "========================================" "INFO"

$lastCommitHash = Get-LastCommitHash
$lastChangedCount = Get-ChangedFilesCount
$loopCount = 0

Write-Log "Initial Commit: $lastCommitHash" "INFO"
Write-Log "Initial Changed Files: $lastChangedCount" "INFO"

while ($true) {
    Start-Sleep -Seconds $WatchIntervalSeconds
    $loopCount++

    $currentCommitHash = Get-LastCommitHash
    $currentChangedCount = Get-ChangedFilesCount

    # Check for new commits
    if ($currentCommitHash -ne $lastCommitHash) {
        Write-Log "New commit detected: $lastCommitHash -> $currentCommitHash" "WARN"

        $lastCommitHash = $currentCommitHash
        $lastChangedCount = $currentChangedCount

        $buildSuccess = Invoke-Build

        if ($buildSuccess) {
            Write-Log "Auto-update completed (Round $loopCount)" "SUCCESS"
        } else {
            Write-Log "Auto-update failed (Round $loopCount)" "ERROR"
        }
    }
    # Only uncommitted changes - log only, no build
    elseif ($currentChangedCount -gt 0) {
        Write-Log "[Round $loopCount] Uncommitted changes detected: $currentChangedCount files" "INFO"
    }
}