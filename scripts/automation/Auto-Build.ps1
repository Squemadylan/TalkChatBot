# Auto-Build.ps1
# Automated Android build script - executes assembleDebug and outputs APK path

param(
    [Parameter(Mandatory = $false)]
    [string]$ProjectDir,

    [Parameter(Mandatory = $false)]
    [string]$LogFile
)

. (Join-Path $PSScriptRoot '..\lib\Init-Automation.ps1')
if (-not $ProjectDir) { $ProjectDir = $RepoRoot }
if (-not $LogFile) { $LogFile = Join-Path $Paths.LocalLogs 'build.log' }

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

function Get-ApkPath {
    $apkDir = Join-Path $RepoRoot 'app\build\outputs\apk'
    $apk = Get-ChildItem -Path $apkDir -Filter "*.apk" -Recurse -ErrorAction SilentlyContinue | Select-Object -Last 1
    if ($apk) { return $apk.FullName }
    return $null
}

Write-Log "========================================" "INFO"
Write-Log "Android Automated Build Started" "INFO"
Write-Log "Project Dir: $ProjectDir" "INFO"
Write-Log "========================================" "INFO"

Write-Log "Cleaning old build outputs..." "INFO"
$cleanCmd = "cd `"$ProjectDir`"; .\gradlew clean 2>&1"
try {
    Invoke-Expression $cleanCmd | Out-Null
    Write-Log "Clean completed" "SUCCESS"
} catch {
    Write-Log "Clean failed: $_" "WARN"
}

$javaHome = Find-JavaHome
if ($javaHome) {
    $env:JAVA_HOME = $javaHome
    $env:PATH = "$javaHome\bin;$env:PATH"
    Write-Log "JAVA_HOME set to: $javaHome" "INFO"
} else {
    Write-Log "Warning: Java not found" "WARN"
}

Write-Log "Starting assembleDebug build..." "INFO"
$startTime = Get-Date
$buildCmd = "cd `"$ProjectDir`"; .\gradlew assembleDebug 2>&1"
Write-Log "Executing: $buildCmd" "INFO"

try {
    $output = Invoke-Expression $buildCmd
    $exitCode = $LASTEXITCODE
    $output | ForEach-Object { Write-Log $_ "INFO" }

    if ($exitCode -eq 0) {
        $elapsed = ((Get-Date) - $startTime).TotalSeconds
        Write-Log "Build SUCCESS! Time: ${elapsed}s" "SUCCESS"
        $apkPath = Get-ApkPath
        if ($apkPath) {
            Write-Log "APK output: $apkPath" "SUCCESS"
            Write-Log "APK size: $([math]::Round((Get-Item $apkPath).Length / 1MB, 2)) MB" "INFO"
        } else {
            Write-Log "APK not found" "WARN"
        }
        Write-Log "========================================" "INFO"
        Write-Log "Build completed" "SUCCESS"
        Write-Log "========================================" "INFO"
    } else {
        Write-Log "Build failed, exit code: $exitCode" "ERROR"
        exit $exitCode
    }
} catch {
    Write-Log "Build exception: $_" "ERROR"
    exit 1
}
