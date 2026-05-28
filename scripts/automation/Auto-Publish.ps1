# Auto-Publish.ps1
# Automated publish script: Build -> Test -> Package -> Output APK

param(
    [Parameter(Mandatory = $false)]
    [string]$ProjectDir,

    [Parameter(Mandatory = $false)]
    [string]$ApkOutputDir,

    [Parameter(Mandatory = $false)]
    [string]$LogFile,

    [Parameter(Mandatory = $false)]
    [switch]$SkipTest
)

. (Join-Path $PSScriptRoot '..\lib\Init-Automation.ps1')
if (-not $ProjectDir) { $ProjectDir = $RepoRoot }
if (-not $ApkOutputDir) { $ApkOutputDir = Join-Path $Paths.ApkDebug '' }
if (-not $LogFile) { $LogFile = Join-Path $Paths.LocalLogs 'publish.log' }

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
    if ($apk) {
        return $apk.FullName
    }
    return $null
}

Write-Log "========================================" "INFO"
Write-Log "Android Automated Publish Started" "INFO"
Write-Log "Project Dir: $ProjectDir" "INFO"
Write-Log "APK Output Dir: $ApkOutputDir" "INFO"
Write-Log "Skip Test: $SkipTest" "INFO"
Write-Log "========================================" "INFO"

$javaHome = Find-JavaHome
if ($javaHome) {
    $env:JAVA_HOME = $javaHome
    $env:PATH = "$javaHome\bin;$env:PATH"
    Write-Log "JAVA_HOME set to: $javaHome" "INFO"
} else {
    Write-Log "Warning: Java not found" "WARN"
}

$totalStartTime = Get-Date

# Stage 1: Clean
Write-Log "[Stage 1/3] Cleaning old build outputs..." "INFO"
$cleanCmd = "cd `"$ProjectDir`"; .\gradlew clean 2>&1"
try {
    Invoke-Expression $cleanCmd | Out-Null
    Write-Log "Clean completed" "SUCCESS"
} catch {
    Write-Log "Clean failed: $_" "WARN"
}

# Stage 2: Test
if (-not $SkipTest) {
    Write-Log "[Stage 2/3] Running tests..." "INFO"
    $testStartTime = Get-Date

    $testCmd = "cd `"$ProjectDir`"; .\gradlew test 2>&1"
    try {
        $output = Invoke-Expression $testCmd
        $exitCode = $LASTEXITCODE

        $output | ForEach-Object { Write-Log $_ "INFO" }

        if ($exitCode -eq 0) {
            $elapsed = ((Get-Date) - $testStartTime).TotalSeconds
            Write-Log "Test passed! Time: ${elapsed}s" "SUCCESS"
        } else {
            Write-Log "Test failed, exit code: $exitCode" "ERROR"
            Write-Log "Publish aborted" "ERROR"
            exit $exitCode
        }
    } catch {
        Write-Log "Test exception: $_" "ERROR"
        exit 1
    }
} else {
    Write-Log "[Stage 2/3] Skipping test" "WARN"
}

# Stage 3: Build
Write-Log "[Stage 3/3] Running assembleDebug build..." "INFO"
$buildStartTime = Get-Date

$buildCmd = "cd `"$ProjectDir`"; .\gradlew assembleDebug 2>&1"
try {
    $output = Invoke-Expression $buildCmd
    $exitCode = $LASTEXITCODE

    $output | ForEach-Object { Write-Log $_ "INFO" }

    if ($exitCode -eq 0) {
        $elapsed = ((Get-Date) - $buildStartTime).TotalSeconds
        Write-Log "Build SUCCESS! Time: ${elapsed}s" "SUCCESS"
    } else {
        Write-Log "Build failed, exit code: $exitCode" "ERROR"
        Write-Log "Publish aborted" "ERROR"
        exit $exitCode
    }
} catch {
    Write-Log "Build exception: $_" "ERROR"
    exit 1
}

# Output APK info
Write-Log "========================================" "INFO"
Write-Log "Searching for APK files..." "INFO"

$apkPath = Get-ApkPath
if ($apkPath) {
    $apkSize = [math]::Round((Get-Item $apkPath).Length / 1MB, 2)
    $totalElapsed = ((Get-Date) - $totalStartTime).TotalSeconds

    Write-Log "========================================" "SUCCESS"
    Write-Log "Automated publish COMPLETED!" "SUCCESS"
    Write-Log "APK Path: $apkPath" "SUCCESS"
    Write-Log "APK Size: ${apkSize} MB" "SUCCESS"
    Write-Log "Total Time: ${totalElapsed}s" "SUCCESS"
    Write-Log "========================================" "SUCCESS"

    Write-Output $apkPath
} else {
    Write-Log "APK not found" "ERROR"
    exit 1
}