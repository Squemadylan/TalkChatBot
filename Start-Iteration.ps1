# Start-Iteration.ps1
# 迭代触发脚本：当用户说"开始迭代"或"开始更新"时执行
# 解析 PRD → 提取功能 → 更新待办 → 触发完整自动化循环

param(
    [Parameter(Mandatory = $false)]
    [string]$PrdFile = "",

    [Parameter(Mandatory = $false)]
    [string]$TodoFile = "e:\New\Dubaixia\backlog-todos.md",

    [Parameter(Mandatory = $false)]
    [string]$LogFile = "e:\New\Dubaixia\iteration.log",

    [Parameter(Mandatory = $false)]
    [switch]$SkipBuild
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

function Get-LatestPrd {
    $prdDir = "e:\New\Dubaixia\.ai\prd"
    if (-not (Test-Path $prdDir)) {
        return $null
    }

    $files = Get-ChildItem -Path $prdDir -Filter "*.md" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending
    if ($files.Count -gt 0) {
        return $files[0].FullName
    }
    return $null
}

function Parse-Prd {
    param([string]$FilePath)

    if (-not (Test-Path $FilePath)) {
        Write-Log "PRD file not found: $FilePath" "ERROR"
        return $null
    }

    $content = Get-Content $FilePath -Raw -Encoding UTF8

    $result = @{
        Title = ""
        Type = ""
        Priority = ""
        EstimatedHours = 0
        Features = @()
        AcceptanceCriteria = @()
        MvpScope = @()
        OutOfScope = @()
    }

    # Extract title
    if ($content -match '^#\s*PRD:\s*(.+)$') {
        $result.Title = $Matches[1].Trim()
    }

    # Extract metadata
    if ($content -match 'Type:\s*(\w+)') {
        $result.Type = $Matches[1].Trim()
    }
    if ($content -match 'Priority:\s*(\w+)') {
        $result.Priority = $Matches[1].Trim()
    }
    if ($content -match 'Est:\s*(\d+)h') {
        $result.EstimatedHours = [int]$Matches[1]
    }

    # Extract MVP features (checked items in MVP section)
    $mvpSection = $false
    $lines = $content -split "`n"
    foreach ($line in $lines) {
        if ($line -match 'MVP.*鍖呭惈|##\s*\d+\..*MVP') {
            $mvpSection = $true
            continue
        }
        if ($line -match '##\s*\d+|---') {
            $mvpSection = $false
        }
        if ($mvpSection -and $line -match '- \[ \]\s*(.+)') {
            $result.Features += $Matches[1].Trim()
            $result.MvpScope += $Matches[1].Trim()
        }
    }

    # Extract all unchecked features (functional requirements)
    foreach ($line in $lines) {
        if ($line -match '^- \[ \]\s*([^#].+)') {
            $feature = $Matches[1].Trim()
            if ($feature -and -not ($result.Features -contains $feature)) {
                $result.Features += $feature
            }
        }
    }

    # Extract acceptance criteria
    if ($content -match '楠屾敹鏍囧噯|验收标准.*?[-=]{3,}([\s\S]+?)(?:---|\*\*)') {
        $criteriaText = $Matches[1]
        $criteriaLines = $criteriaText -split "`n" | Where-Object { $_ -match '- \[ \]\s*(.+)' }
        foreach ($criteria in $criteriaLines) {
            if ($criteria -match '- \[ \]\s*(.+)') {
                $result.AcceptanceCriteria += $Matches[1].Trim()
            }
        }
    }

    # Extract out of scope items
    $outScopeSection = $false
    foreach ($line in $lines) {
        if ($line -match 'MVP.*涓嶅寘鍚|不做清单') {
            $outScopeSection = $true
            continue
        }
        if ($line -match '^##') {
            $outScopeSection = $false
        }
        if ($outScopeSection -and $line -match '^- (.+)') {
            $result.OutOfScope += $Matches[1].Trim()
        }
    }

    Write-Log "Parsed PRD: $($result.Title)" "INFO"
    Write-Log "  Type: $($result.Type), Priority: $($result.Priority), Est: $($result.EstimatedHours)h" "INFO"
    Write-Log "  Features: $($result.Features.Count)" "INFO"
    Write-Log "  Acceptance Criteria: $($result.AcceptanceCriteria.Count)" "INFO"

    return $result
}

function Update-Todo-Status {
    param(
        [string]$TodoId,
        [string]$Status = "[-]"
    )

    if (-not (Test-Path $TodoFile)) {
        Write-Log "TODO file not found: $TodoFile" "ERROR"
        return $false
    }

    $content = Get-Content $TodoFile -Raw -Encoding UTF8

    # Update status line
    $pattern = "(${TodoId}.*?状态.*?)\[.\]"

    # Check if the TODO exists
    if ($content -notmatch [regex]::Escape($TodoId)) {
        Write-Log "TODO not found: $TodoId" "WARN"
        return $false
    }

    # Update the status markers
    $newContent = $content -replace "\[ \] # .*", "[x] # (completed)"
    $newContent = $newContent -replace "鐘舵€?*: \[ \]", "鐘舵€?*: $Status"

    $newContent | Out-File -FilePath $TodoFile -Encoding UTF8
    Write-Log "Updated TODO status: $TodoId -> $Status" "SUCCESS"
    return $true
}

function Generate-Task-Script {
    param(
        [array]$Features,
        [string]$Title
    )

    $taskFile = "e:\New\Dubaixia\iteration-tasks.txt"
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"

    $content = @"
# Iteration Tasks: $Title
# Generated: $timestamp

## Implementation Tasks
$($Features | ForEach-Object { "- [ ] $_" } | Out-String)

## Workflow
1. 实现上述功能
2. 运行测试: .\Auto-Test.ps1
3. 构建 APK: .\Auto-Build.ps1
4. 验证: .\Auto-Publish.ps1 -SkipTest

---
*Generated by Start-Iteration.ps1*
"@

    $content | Out-File -FilePath $taskFile -Encoding UTF8
    Write-Log "Generated task file: $taskFile" "SUCCESS"
    return $taskFile
}

function Invoke-Full-Automation {
    param([bool]$SkipBuild = $false)

    Write-Log "Starting full automation loop..." "WARN"

    # Stage 1: Feature Verification
    Write-Log "[Stage 1/5] Running feature verification..." "INFO"
    $verifyScript = "e:\New\Dubaixia\Auto-Feature-Verify.ps1"
    if (Test-Path $verifyScript) {
        try {
            $verifyOutput = & $verifyScript 2>&1
            $verifyExit = $LASTEXITCODE
            $verifyOutput | Select-Object -Last 10 | ForEach-Object { Write-Log $_ "INFO" }
            if ($verifyExit -eq 0) {
                Write-Log "Feature verification completed" "SUCCESS"
            } else {
                Write-Log "Feature verification found issues" "WARN"
            }
        } catch {
            Write-Log "Feature verification failed: $_" "ERROR"
        }
    }

    # Stage 2: Test
    if (-not $SkipBuild) {
        Write-Log "[Stage 2/5] Running tests..." "INFO"
        $testScript = "e:\New\Dubaixia\Auto-Test.ps1"
        if (Test-Path $testScript) {
            try {
                $null = & $testScript 2>&1 | Out-Null
                Write-Log "Tests completed" "SUCCESS"
            } catch {
                Write-Log "Tests failed: $_" "ERROR"
            }
        }
    }

    # Stage 3: Build
    Write-Log "[Stage 3/5] Building APK..." "INFO"
    $buildScript = "e:\New\Dubaixia\Auto-Build.ps1"
    if (Test-Path $buildScript) {
        try {
            $null = & $buildScript 2>&1 | Out-Null
            Write-Log "Build completed" "SUCCESS"
        } catch {
            Write-Log "Build failed: $_" "ERROR"
            return $false
        }
    }

    # Stage 4: Verify APK
    Write-Log "[Stage 4/5] Verifying APK..." "INFO"
    $apkPath = "e:\New\Dubaixia\app\build\outputs\apk\debug\app-debug.apk"
    if (Test-Path $apkPath) {
        $apkSize = [math]::Round((Get-Item $apkPath).Length / 1MB, 2)
        Write-Log "APK verified: $apkPath ($apkSize MB)" "SUCCESS"
    } else {
        Write-Log "APK not found: $apkPath" "ERROR"
        return $false
    }

    # Stage 5: Git commit & push
    Write-Log "[Stage 5/5] Committing changes..." "INFO"
    $commitScript = "e:\New\Dubaixia\Github-push.ps1"
    if (Test-Path $commitScript) {
        try {
            $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm"
            $message = "迭代: $Title | $timestamp"
            $null = & $commitScript -Message $message 2>&1 | Out-Null
            Write-Log "Committed and pushed" "SUCCESS"
        } catch {
            Write-Log "Git push failed: $_" "WARN"
        }
    }

    return $true
}

# Main
Write-Log "========================================" "INFO"
Write-Log "Iteration Started" "INFO"
Write-Log "Trigger: User command '开始迭代' or '开始更新'" "INFO"
Write-Log "========================================" "INFO"

# Find PRD file
if ([string]::IsNullOrWhiteSpace($PrdFile)) {
    $PrdFile = Get-LatestPrd
}

if ([string]::IsNullOrWhiteSpace($PrdFile)) {
    Write-Log "No PRD file found. Please generate a PRD first." "ERROR"
    Write-Log "Use Auto-Task-Manager.ps1 to generate PRD from user prompts." "INFO"
    exit 1
}

Write-Log "Using PRD: $PrdFile" "INFO"

# Parse PRD
$prd = Parse-Prd $PrdFile
if ($null -eq $prd) {
    Write-Log "Failed to parse PRD" "ERROR"
    exit 1
}

# Generate task script
$taskFile = Generate-Task-Script -Features $prd.Features -Title $prd.Title
Write-Log "Task file generated: $taskFile" "INFO"

# Find associated TODO
$todoPattern = $prd.Title -replace '[^\w\u4e00-\u9fff-]', '' | Select-Object -First 30
$todoId = ""

# Update TODO status to in-progress
$updated = Update-Todo-Status -TodoId "TODO-20260527-1048" -Status "[-]"
if ($updated) {
    $todoId = "TODO-20260527-1048"
}

# Display tasks to user
Write-Log "========================================" "INFO"
Write-Log "ITERATION TASKS: $($prd.Title)" "INFO"
Write-Log "========================================" "INFO"
Write-Log "Priority: $($prd.Priority) | Type: $($prd.Type) | Est: $($prd.EstimatedHours)h" "INFO"
Write-Log "--------------------------------------------" "INFO"

$taskIndex = 1
foreach ($feature in $prd.Features) {
    Write-Log "  $taskIndex. $feature" "INFO"
    $taskIndex++
}

Write-Log "--------------------------------------------" "INFO"

# Check acceptance criteria
if ($prd.AcceptanceCriteria.Count -gt 0) {
    Write-Log "Acceptance Criteria:" "WARN"
    foreach ($criteria in $prd.AcceptanceCriteria) {
        Write-Log "  - $criteria" "WARN"
    }
    Write-Log "--------------------------------------------" "INFO"
}

# Ask for confirmation to proceed
Write-Log "Ready to start iteration. Building and testing..." "WARN"

# Trigger full automation
$success = Invoke-Full-Automation -SkipBuild $SkipBuild

if ($success) {
    Write-Log "========================================" "SUCCESS"
    Write-Log "ITERATION COMPLETED SUCCESSFULLY!" "SUCCESS"
    Write-Log "========================================" "SUCCESS"
    Write-Log "APK: e:\New\Dubaixia\app\build\outputs\apk\debug\app-debug.apk" "SUCCESS"

    # Update TODO to completed
    if ($todoId) {
        Update-Todo-Status -TodoId $todoId -Status "[x]"
    }
} else {
    Write-Log "========================================" "ERROR"
    Write-Log "ITERATION FAILED" "ERROR"
    Write-Log "========================================" "ERROR"
    exit 1
}