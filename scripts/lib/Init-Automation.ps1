# Dot-source from scripts/automation/*.ps1 (after param block)
# Note: when dot-sourced, $PSScriptRoot is scripts/lib; RepoPaths handles that.
. (Join-Path $PSScriptRoot 'RepoPaths.ps1')
$script:RepoRoot = Get-RepoRoot -FromScriptRoot $PSScriptRoot
$script:Paths = Get-RepoPaths -RepoRoot $script:RepoRoot
Ensure-RepoDirs -Paths $script:Paths
$script:AutomationDir = $script:Paths.ScriptsAuto
