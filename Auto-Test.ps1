# Auto-Test.ps1
# Automated test script for Android

param(
    [Parameter(Mandatory = $false)]
    [string]$ProjectDir = "e:\New\Dubaixia",

    [Parameter(Mandatory = $false)]
    [string]$LogFile = "e:\New\Dubaixia\test.log"
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

function Find-JavaHome {
    $javaPaths = @(
        "C:\Program Files\Android\Android Studio\jbr",
        "C:\Program Files\Android\Android Studio\jre",
        "C:\Program Files\Java\jdk-17",
        "C:\Program Files\Java\jdk-11",
        "${env:JAVA_HOME}"
    )

    foreach ($path in $javaPaths) {
        if ($path -and (Test-Path "$path\bin\java.exe")) {
            return $path
        }
    }

    try {
        $regPath = "HKLM:\SOFTWARE\JavaSoft\Java Development Kit"
        if (Test-Path $regPath) {
            $version = (Get-ItemProperty $regPath).CurrentVersion
            if ($version) {
                $javaHome = (Get-ItemProperty "$regPath\$version").JavaHome
                if ($javaHome -and (Test-Path "$javaHome\bin\java.exe")) {
                    return $javaHome
                }
            }
        }
    } catch {}

    return $null
}

Write-Log "========================================" "INFO"
Write-Log "Android Automated Test Started" "INFO"
Write-Log "Project Dir: $ProjectDir" "INFO"
Write-Log "========================================" "INFO"

$javaHome = Find-JavaHome
if ($javaHome) {
    $env:JAVA_HOME = $javaHome
    $env:PATH = "$javaHome\bin;$env:PATH"
    Write-Log "JAVA_HOME set to: $javaHome" "INFO"
} else {
    Write-Log "Warning: Java not found" "WARN"
}

Write-Log "Starting test execution..." "INFO"
$startTime = Get-Date

$testCmd = "cd `"$ProjectDir`"; .\gradlew test 2>&1"
Write-Log "Executing: $testCmd" "INFO"

try {
    $output = Invoke-Expression $testCmd
    $exitCode = $LASTEXITCODE

    $output | ForEach-Object { Write-Log $_ "INFO" }

    if ($exitCode -eq 0) {
        $elapsed = ((Get-Date) - $startTime).TotalSeconds
        Write-Log "Test SUCCESS! Time: ${elapsed}s" "SUCCESS"
        Write-Log "========================================" "INFO"
        Write-Log "Test completed" "SUCCESS"
        Write-Log "========================================" "INFO"
    } else {
        Write-Log "Test failed, exit code: $exitCode" "ERROR"
        Write-Log "========================================" "INFO"
        Write-Log "Test failed" "ERROR"
        Write-Log "========================================" "INFO"
    }

    exit $exitCode
} catch {
    Write-Log "Test exception: $_" "ERROR"
    exit 1
}