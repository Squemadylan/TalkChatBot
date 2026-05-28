# Github-push.ps1
# 暂存整个仓库 -> 预览变更 -> 确认 -> 提交（说明 + 自动时间戳）-> 推送到 origin 当前分支
#
# 放置位置：与本文件同级的 Github-push.cmd 一起放在**含 .git 的项目根目录**（本仓库即 TalkChatBot 根目录）。
#
# 用法：
#   .\Github-push.ps1
#   .\Github-push.ps1 -Message "更新 README"
#   .\Github-push.ps1 -m "fix chat layout"
#   双击 Github-push.cmd
#
# 远程仓库（本仓库默认）：
#   https://github.com/Squemadylan/TalkChatBot.git
# 若尚未配置：git remote add origin https://github.com/Squemadylan/TalkChatBot.git

param(
    [Parameter(Mandatory = $false)]
    [Alias("m")]
    [string]$Message = ""
)

. (Join-Path $PSScriptRoot '..\lib\Init-Automation.ps1')

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
Set-Location $RepoRoot

function Cn {
    param([int[]]$CodePoints)
    return -join ($CodePoints | ForEach-Object { [char]$_ })
}

$MSG_PROJECT_DIR = Cn @(39033,30446,30446,24405,58)
$MSG_GIT_MISSING = Cn @(26410,25214,21040,32,103,105,116,65292,35831,20808,23433,35013,32,71,105,116,32,24182,30830,20445,22312,32,80,65,84,72,32,20013,12290)
$MSG_NOT_REPO = Cn @(24403,21069,30446,24405,19981,26159,32,71,105,116,32,20179,24211,65288,26410,21457,29616,32,46,103,105,116,65289,65292,35831,22312,39033,30446,26681,30446,24405,36816,34892,35813,33050,26412,12290)
$MSG_NO_ORIGIN = Cn @(26410,37197,32622,32,111,114,105,103,105,110,32,36828,31243,20179,24211,12290)
$MSG_ADD_ORIGIN = Cn @(35831,20808,25191,34892,65306,32,103,105,116,32,114,101,109,111,116,101,32,97,100,100,32,111,114,105,103,105,110,32,104,116,116,112,115,58,47,47,103,105,116,104,117,98,46,99,111,109,47,83,113,117,101,109,97,100,121,108,97,110,47,84,97,108,107,67,104,97,116,66,111,116,46,103,105,116)
$MSG_REMOTE = Cn @(36828,31243,32,111,114,105,103,105,110,58)
$MSG_BRANCH = Cn @(24403,21069,20998,25903,65306)
$MSG_PROMPT_UPDATE = Cn @(35831,36755,20837,26412,27425,26356,26032,35828,26126,65288,24517,22635,19981,33021,20026,31354,65289,65306)
$MSG_COMMIT_WILL_BE = Cn @(23454,38469,25552,20132,35828,26126,65288,24050,38468,21152,26102,38388,25139,65289,65306)
$MSG_NO_CHANGES = Cn @(27809,26377,38656,35201,25552,20132,30340,21464,26356,65288,24037,20316,21306,19982,19978,27425,25552,20132,19968,33268,65289,12290)
$MSG_SKIP_PUSH = Cn @(26080,25913,21160,65292,24050,36339,36807,25512,36865,12290)
$MSG_PREVIEW = Cn @(26412,27425,23558,25552,20132,20197,19979,25991,20214,65306)
$MSG_CONFIRM = Cn @(30830,35748,32487,32493,32,99,111,109,109,105,116,32,43,32,112,117,115,104,32,21527,65311,36755,20837,32,89,32,32487,32493,65292,20854,23427,20219,24847,38190,21462,28040)
$MSG_CANCELED = Cn @(24050,21462,28040,25805,20316,65288,26410,25552,20132,12289,26410,25512,36865,65289,12290)
$MSG_COMMITTED = Cn @(24050,25552,20132,65306)
$MSG_PUSHED = Cn @(24050,25512,36865,21040,36828,31243,12290)

Write-Host ($MSG_PROJECT_DIR + ' ' + $PSScriptRoot) -ForegroundColor Cyan

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Write-Error $MSG_GIT_MISSING
    exit 1
}

$insideRepo = (git rev-parse --is-inside-work-tree 2>$null)
if ($LASTEXITCODE -ne 0 -or $insideRepo.Trim() -ne "true") {
    Write-Host $MSG_NOT_REPO -ForegroundColor Red
    exit 1
}

$originUrl = (git remote get-url origin 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($originUrl)) {
    Write-Host $MSG_NO_ORIGIN -ForegroundColor Red
    Write-Host $MSG_ADD_ORIGIN -ForegroundColor Yellow
    exit 1
}

$branchName = (git branch --show-current 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($branchName)) {
    Write-Host 'Detached HEAD: checkout a branch first (e.g. git checkout main), then retry.' -ForegroundColor Red
    exit 1
}

Write-Host ($MSG_REMOTE + ' ' + $originUrl) -ForegroundColor DarkCyan
Write-Host ($MSG_BRANCH + ' ' + $branchName) -ForegroundColor DarkCyan

while ([string]::IsNullOrWhiteSpace($Message)) {
    $Message = Read-Host $MSG_PROMPT_UPDATE
}

$ts = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
$finalMessage = '{0} | {1}' -f $Message, $ts

Write-Host ($MSG_COMMIT_WILL_BE + ' ' + $finalMessage) -ForegroundColor Gray

git add -A
git diff --cached --quiet
if ($LASTEXITCODE -eq 0) {
    Write-Host $MSG_NO_CHANGES -ForegroundColor Yellow
    Write-Host $MSG_SKIP_PUSH -ForegroundColor Yellow
    exit 0
}

Write-Host $MSG_PREVIEW -ForegroundColor Cyan
git diff --cached --name-status

$confirm = Read-Host $MSG_CONFIRM
if ($confirm -ne "Y" -and $confirm -ne "y") {
    Write-Host $MSG_CANCELED -ForegroundColor Yellow
    exit 0
}

git commit -m "$finalMessage"
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
Write-Host ($MSG_COMMITTED + ' ' + $finalMessage) -ForegroundColor Green

git push -u origin $branchName
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
Write-Host $MSG_PUSHED -ForegroundColor Green
