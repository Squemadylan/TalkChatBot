# Start-All-Automation.ps1
# Starts automation: Auto-Update-Loop, Auto-Task-Manager

param(
    [Parameter(Mandatory = $false)]
    [int]$UpdateInterval = 30,

    [Parameter(Mandatory = $false)]
    [int]$TaskManagerInterval = 60,

    [Parameter(Mandatory = $false)]
    [switch]$AutoBuild
)

. (Join-Path $PSScriptRoot '..\lib\Init-Automation.ps1')

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
Write-Log "Repo: $RepoRoot" "INFO"
Write-Log "========================================" "INFO"

$updateLoopScript = Join-Path $AutomationDir 'Auto-Update-Loop.ps1'
if (Test-Path $updateLoopScript) {
    Write-Log "Starting Auto-Update-Loop..." "INFO"
    Start-Process powershell -ArgumentList "-ExecutionPolicy", "Bypass", "-File", $updateLoopScript, "-WatchIntervalSeconds", $UpdateInterval -WindowStyle Hidden
    Write-Log "Auto-Update-Loop started" "SUCCESS"
} else {
    Write-Log "Auto-Update-Loop not found: $updateLoopScript" "ERROR"
}

$taskManagerScript = Join-Path $AutomationDir 'Auto-Task-Manager.ps1'
if (Test-Path $taskManagerScript) {
    Write-Log "Starting Auto-Task-Manager..." "INFO"
    $taskManagerArgs = @("-ExecutionPolicy", "Bypass", "-File", $taskManagerScript, "-WatchIntervalSeconds", $TaskManagerInterval)
    if ($AutoBuild) { $taskManagerArgs += "-AutoBuild" }
    Start-Process powershell -ArgumentList $taskManagerArgs -WindowStyle Hidden
    Write-Log "Auto-Task-Manager started" "SUCCESS"
} else {
    Write-Log "Auto-Task-Manager not found: $taskManagerScript" "ERROR"
}

Write-Log "Logs: $(Join-Path $Paths.LocalLogs '*.log')" "INFO"
Write-Log "========================================" "INFO"
