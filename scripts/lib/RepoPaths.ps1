# Shared repo path helpers for automation scripts under scripts/automation/

function Get-RepoRoot {
    param(
        [string]$FromScriptRoot = $PSScriptRoot
    )
    $dir = $FromScriptRoot.TrimEnd('\', '/')
    if ($dir -match '[\\/]scripts[\\/]automation$') {
        return (Resolve-Path (Join-Path $dir '..\..')).Path
    }
    if ($dir -match '[\\/]scripts[\\/]dev$') {
        return (Resolve-Path (Join-Path $dir '..')).Path
    }
    if ($dir -match '[\\/]scripts[\\/]lib$') {
        return (Resolve-Path (Join-Path $dir '..\..')).Path
    }
    if ($dir -match '[\\/]scripts$') {
        return (Resolve-Path (Join-Path $dir '..')).Path
    }
    return (Resolve-Path $dir).Path
}

function Get-RepoPaths {
    param([string]$RepoRoot = (Get-RepoRoot))
    @{
        Root           = $RepoRoot
        App            = Join-Path $RepoRoot 'app'
        ApkDebug       = Join-Path $RepoRoot 'app\build\outputs\apk\debug'
        AiPrd          = Join-Path $RepoRoot '.ai\prd'
        ScriptsAuto    = Join-Path $RepoRoot 'scripts\automation'
        ScriptsState   = Join-Path $RepoRoot 'scripts\state'
        LocalLogs      = Join-Path $RepoRoot '_local\logs'
        LocalApks      = Join-Path $RepoRoot '_local\apks'
        LocalArchives  = Join-Path $RepoRoot '_local\archives'
        ThirdParty     = Join-Path $RepoRoot 'third_party'
    }
}

function Ensure-RepoDirs {
    param([hashtable]$Paths)
    foreach ($key in @('LocalLogs', 'LocalApks', 'LocalArchives', 'ScriptsState')) {
        $p = $Paths[$key]
        if ($p -and -not (Test-Path $p)) {
            New-Item -ItemType Directory -Path $p -Force | Out-Null
        }
    }
}
