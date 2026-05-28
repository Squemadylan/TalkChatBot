# Auto-Task-Manager.ps1
# Auto Task Manager: Monitor user prompts -> Generate PRD -> Create TODOs -> Trigger Iteration
# Trigger: Auto-detect "开始迭代" or "开始更新" command

param(
    [Parameter(Mandatory = $false)]
    [int]$WatchIntervalSeconds = 30,

    [Parameter(Mandatory = $false)]
    [string]$PromptFile,

    [Parameter(Mandatory = $false)]
    [string]$TodoFile,

    [Parameter(Mandatory = $false)]
    [string]$PrdOutputDir,

    [Parameter(Mandatory = $false)]
    [string]$LogFile,

    [Parameter(Mandatory = $false)]
    [switch]$AutoBuild,

    [Parameter(Mandatory = $false)]
    [switch]$AutoIterate
)

. (Join-Path $PSScriptRoot '..\lib\Init-Automation.ps1')
if (-not $PromptFile) { $PromptFile = Join-Path $Paths.ScriptsState 'user-prompts.txt' }
if (-not $TodoFile) { $TodoFile = Join-Path $Paths.ScriptsState 'backlog-todos.md' }
if (-not $PrdOutputDir) { $PrdOutputDir = $Paths.AiPrd }
if (-not $LogFile) { $LogFile = Join-Path $Paths.LocalLogs 'task-manager.log' }
if (-not (Test-Path $PromptFile)) { New-Item -ItemType File -Path $PromptFile -Force | Out-Null }
if (-not (Test-Path $TodoFile)) { New-Item -ItemType File -Path $TodoFile -Force | Out-Null }

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

function Ensure-Directory {
    param([string]$Path)
    if (-not (Test-Path $Path)) {
        New-Item -ItemType Directory -Path $Path -Force | Out-Null
        Write-Log "Created directory: $Path" "INFO"
    }
}

function Parse-UserPrompt {
    param([string]$Content)

    $result = @{
        Title = ""
        Description = ""
        Features = @()
        Priority = "Medium"
        Type = "Feature"
        EstimatedHours = 0
        IsIterationCommand = $false
        IsUpdateCommand = $false
    }

    # Check for iteration/update commands
    if ($Content -match '(?i)开始迭代|开始更新') {
        $result.IsIterationCommand = $true
        Write-Log "Iteration command detected" "WARN"
    }

    # Extract title (first line or # heading)
    if ($Content -match '^#\s*(.+)$') {
        $result.Title = $Matches[1].Trim()
    } elseif ($Content -match '^([^\r\n]+)') {
        $result.Title = $Matches[1].Trim()
    }

    # Detect priority
    if ($Content -match '(?i)(urgent|紧急|高优先|top priority|p0)') {
        $result.Priority = "High"
    } elseif ($Content -match '(?i)(low priority|低优先|p2|p3)') {
        $result.Priority = "Low"
    }

    # Detect type
    if ($Content -match '(?i)(bug|fix|修复|错误)') {
        $result.Type = "BugFix"
    } elseif ($Content -match '(?i)(refactor|重构)') {
        $result.Type = "Refactor"
    } elseif ($Content -match '(?i)(test|测试)') {
        $result.Type = "Test"
    }

    # Extract features (lines starting with - or *)
    $lines = $Content -split "`n"
    foreach ($line in $lines) {
        $line = $line.Trim()
        if ($line -match '^[-*]\s+(.+)') {
            $result.Features += $Matches[1].Trim()
        }
    }

    # Estimate hours based on content length and features
    $wordCount = ($Content -split '\s+').Count
    $result.EstimatedHours = [math]::Max(1, [int]($wordCount / 50))

    return $result
}

function Generate-PRD {
    param(
        [string]$Title,
        [string]$Description,
        [string]$Type,
        [string]$Priority,
        [array]$Features,
        [int]$EstimatedHours
    )

    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $safeTitle = $Title -replace '[^\w\u4e00-\u9fff-]', '_' | Select-Object -First 50
    $filename = "${timestamp}-${safeTitle}.md"
    $filepath = Join-Path $PrdOutputDir $filename

    Ensure-Directory $PrdOutputDir

    $featureList = $Features | ForEach-Object { "- [ ] $_" } | Out-String

    $prdContent = @"
# PRD: $Title

> Generated: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
> Type: $Type | Priority: $Priority | Est: ${EstimatedHours}h

## 1. 目标用户/场景

[TODO: 描述目标用户和使用场景]

## 2. 功能列表

### MVP 功能
$featureList

### 扩展功能
- [ ] (待定)

## 3. 数据模型

| 实体 | 字段 | 说明 |
|------|------|------|
| (待定) | | |

## 4. 接口草图

```
请求: POST /api/...
响应: { ... }
错误码: 200 OK, 400 Bad Request, 500 Server Error
```

## 5. 用户旅程

1. 用户打开应用
2. 用户进行操作
3. 系统返回结果

## 6. 非功能需求

- 性能: 响应时间 < 2s
- 兼容性: Android 8.0+ (API 26)
- 安全: 不存储敏感信息

## 7. 风险与边界

> ⚠️ Assumption: 假设基于现有架构进行开发

## 8. MVP 范围与不做清单

### MVP 包含
$($Features | Select-Object -First 3 | ForEach-Object { "- $_" } | Out-String)

### MVP 不包含
- 非核心功能
- 高级特性

---

## 验收标准

- [ ] 功能按描述实现
- [ ] 无崩溃或严重错误
- [ ] 代码符合项目规范

---
*Generated by Auto-Task-Manager*
"@

    $prdContent | Out-File -FilePath $filepath -Encoding UTF8
    Write-Log "Generated PRD: $filepath" "SUCCESS"
    return $filepath
}

function Add-Todo-Entry {
    param(
        [string]$Title,
        [string]$Type,
        [string]$Priority,
        [int]$EstimatedHours,
        [string]$PrdFile
    )

    $timestamp = Get-Date -Format "yyyy-MM-dd"
    $todoId = "TODO-$(Get-Date -Format 'yyyyMMdd-HHmm')"

    $entry = @"

## $todoId | $timestamp
- **标题**: $Title
- **类型**: $Type
- **优先级**: $Priority
- **预估**: ${EstimatedHours}h
- **PRD**: $([System.IO.Path]::GetFileName($PrdFile))
- **状态**: [ ]

[ ] $Title
"@

    if (Test-Path $TodoFile) {
        $content = Get-Content $TodoFile -Raw -Encoding UTF8
        if (-not $content.Contains($todoId)) {
            $entry + "`n" | Add-Content -Path $TodoFile -Encoding UTF8
            Write-Log "Added TODO: $todoId - $Title" "SUCCESS"
        } else {
            Write-Log "TODO already exists: $todoId" "INFO"
        }
    } else {
        $header = "# Backlog TODOs`n`n## 历史记录`n"
        $header + $entry + "`n" | Out-File -FilePath $TodoFile -Encoding UTF8
        Write-Log "Created TODO file with first entry: $Title" "SUCCESS"
    }

    return $todoId
}

function Invoke-AutoBuild {
    Write-Log "Triggering automated build..." "WARN"

    $buildScript = Join-Path $AutomationDir 'Auto-Publish.ps1'
    if (Test-Path $buildScript) {
        try {
            $result = & $buildScript -SkipTest
            Write-Log "Build completed: $result" "SUCCESS"
            return $true
        } catch {
            Write-Log "Build failed: $_" "ERROR"
            return $false
        }
    } else {
        Write-Log "Build script not found: $buildScript" "ERROR"
        return $false
    }
}

function Invoke-Iteration {
    Write-Log "Triggering iteration..." "WARN"

    $iterationScript = Join-Path $AutomationDir 'Start-Iteration.ps1'
    if (Test-Path $iterationScript) {
        try {
            Write-Log "Executing iteration script..." "INFO"
            $result = & $iterationScript -SkipBuild
            Write-Log "Iteration completed" "SUCCESS"
            return $true
        } catch {
            Write-Log "Iteration failed: $_" "ERROR"
            return $false
        }
    } else {
        Write-Log "Iteration script not found: $iterationScript" "ERROR"
        return $false
    }
}

function Process-PromptFile {
    if (-not (Test-Path $PromptFile)) {
        Write-Log "Prompt file not found: $PromptFile" "WARN"
        return $false
    }

    $content = Get-Content $PromptFile -Raw -Encoding UTF8
    if ([string]::IsNullOrWhiteSpace($content)) {
        return $false
    }

    # Parse the prompt
    $parsed = Parse-UserPrompt $Content

    # Check for iteration command
    if ($parsed.IsIterationCommand) {
        Write-Log "Detected iteration/update command" "WARN"

        # Clear prompt file first
        "" | Out-File -FilePath $PromptFile -Encoding UTF8

        # Trigger iteration
        $iterationSuccess = Invoke-Iteration
        return $iterationSuccess
    }

    if ([string]::IsNullOrWhiteSpace($parsed.Title)) {
        Write-Log "Could not extract title from prompt" "WARN"
        return $false
    }

    Write-Log "Processing prompt: $($parsed.Title)" "INFO"
    Write-Log "  Type: $($parsed.Type), Priority: $($parsed.Priority), Est: $($parsed.EstimatedHours)h" "INFO"

    # Generate PRD
    $prdFile = Generate-PRD `
        -Title $parsed.Title `
        -Description $parsed.Description `
        -Type $parsed.Type `
        -Priority $parsed.Priority `
        -Features $parsed.Features `
        -EstimatedHours $parsed.EstimatedHours

    # Add TODO entry
    $todoId = Add-Todo-Entry `
        -Title $parsed.Title `
        -Type $parsed.Type `
        -Priority $parsed.Priority `
        -EstimatedHours $parsed.EstimatedHours `
        -PrdFile $prdFile

    # Clear the prompt file after processing
    "" | Out-File -FilePath $PromptFile -Encoding UTF8
    Write-Log "Prompt file cleared" "INFO"

    # Trigger build if enabled
    if ($AutoBuild) {
        $buildSuccess = Invoke-AutoBuild
        if ($buildSuccess) {
            Write-Log "Auto-build completed for TODO: $todoId" "SUCCESS"
        }
    }

    # Auto-iterate if enabled
    if ($AutoIterate) {
        Start-Sleep -Seconds 2
        $iterationSuccess = Invoke-Iteration
        if ($iterationSuccess) {
            Write-Log "Auto-iteration completed" "SUCCESS"
        }
    }

    return $true
}

# Main
Write-Log "========================================" "INFO"
Write-Log "Auto Task Manager Started" "INFO"
Write-Log "Watch Interval: ${WatchIntervalSeconds}s" "INFO"
Write-Log "Prompt File: $PromptFile" "INFO"
Write-Log "TODO File: $TodoFile" "INFO"
Write-Log "PRD Output: $PrdOutputDir" "INFO"
Write-Log "Auto Build: $AutoBuild" "INFO"
Write-Log "Auto Iterate: $AutoIterate" "INFO"
Write-Log "========================================" "INFO"
Write-Log "Listening for commands:" "INFO"
Write-Log "  - Write prompts to scripts/state/user-prompts.txt" "INFO"
Write-Log "  - Write '开始迭代' or '开始更新' to trigger iteration" "INFO"
Write-Log "========================================" "INFO"

# Initialize
Ensure-Directory $PrdOutputDir

$lastContent = ""
$loopCount = 0

while ($true) {
    Start-Sleep -Seconds $WatchIntervalSeconds
    $loopCount++

    if (Test-Path $PromptFile) {
        $currentContent = Get-Content $PromptFile -Raw -Encoding UTF8

        if ($currentContent -ne $lastContent -and -not [string]::IsNullOrWhiteSpace($currentContent)) {
            Write-Log "[Round $loopCount] New content detected" "WARN"

            $success = Process-PromptFile

            if ($success) {
                $lastContent = ""
                Write-Log "Processing completed" "SUCCESS"
            }
        } else {
            Write-Log "[Round $loopCount] No new content" "INFO"
        }
    } else {
        Write-Log "[Round $loopCount] Prompt file not found, waiting..." "INFO"
    }
}