# Start-All-Automation.ps1
# Starts all automation scripts:
# 1. Auto-Update-Loop.ps1 - monitors git commits -> auto build
# 2. Auto-Task-Manager.ps1 - monitors user prompts -> generate PRD + TODOs
# 3. Auto-Build.ps1 - builds APK
# 4. Auto-Publish.ps1 - full publish pipeline

param(
    [Parameter(Mandatory = $false)]
    [int]$UpdateInterval = 30,

    [Parameter(Mandatory = $false)]
    [int]$TaskManagerInterval = 60,

    [Parameter(Mandatory = $false)]
    [switch]$AutoBuild
)

$ErrorActionPreference = "Continue"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

function Write-Log {
    param([string]$Message, [string]$Level = "INFO")
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $logEntry = "[$timestamp] [$Level] $Message"
    $color = switch ($Level) {
        "INFO" { "Cyan" }
        "WARN" { "Yellow" }
        "ERROR" { "Red" }
        "SUCCESS" { "Green" }
        default { "White" }
    }
    Write-Host $logEntry -ForegroundColor $color
}

Write-Log "========================================" "INFO"
Write-Log "Starting All Automation Scripts" "INFO"
Write-Log "========================================" "INFO"

# Start Auto-Update-Loop
$updateLoopScript = "e:\New\Dubaixia\Auto-Update-Loop.ps1"
if (Test-Path $updateLoopScript) {
    Write-Log "Starting Auto-Update-Loop (git commit monitoring)..." "INFO"
    Start-Process powershell -ArgumentList "-ExecutionPolicy", "Bypass", "-File", $updateLoopScript, "-WatchIntervalSeconds", $UpdateInterval -WindowStyle Hidden
    Write-Log "Auto-Update-Loop started" "SUCCESS"
} else {
    Write-Log "Auto-Update-Loop not found: $updateLoopScript" "ERROR"
}

# Start Auto-Task-Manager
$taskManagerScript = "e:\New\Dubaixia\Auto-Task-Manager.ps1"
if (Test-Path $taskManagerScript) {
    Write-Log "Starting Auto-Task-Manager (prompt monitoring)..." "INFO"
    $autoBuildFlag = if ($AutoBuild) { "-AutoBuild" } else { "" }
    Start-Process powershell -ArgumentList "-ExecutionPolicy", "Bypass", "-File", $taskManagerScript, "-WatchIntervalSeconds", $TaskManagerInterval, $autoBuildFlag -WindowStyle Hidden
    Write-Log "Auto-Task-Manager started" "SUCCESS"
} else {
    Write-Log "Auto-Task-Manager not found: $taskManagerScript" "ERROR"
}

Write-Log "========================================" "INFO"
Write-Log "All Automation Scripts Started" "INFO"
Write-Log "- Update Loop: monitors git commits every ${UpdateInterval}s" "INFO"
Write-Log "- Task Manager: monitors prompts every ${TaskManagerInterval}s" "INFO"
Write-Log "- Auto Build: $AutoBuild" "INFO"
Write-Log "========================================" "INFO"
Write-Log "Logs:" "INFO"
Write-Log "  - auto-update.log" "INFO"
Write-Log "  - task-manager.log" "INFO"
Write-Log "  - build.log" "INFO"
Write-Log "  - publish.log" "INFO"
Write-Log "========================================" "INFO"