param(
    [Parameter(Position=0)]
    [string]$PlanFile,

    [string]$Model = "",

    [switch]$CheckOnly,

    [switch]$NoGradle
)

# Primary Lead Reviewer cho AppTruyen: chay Claude Code CLI (tai khoan Claude Pro) de tham dinh plan.
# Exit codes: 0 = xong (APPROVED / CHANGES_REQUESTED)
#             1 = thieu plan / template / duong dan sai / plan da dong
#             40 = khong co claude.exe
#             41 = chua dang nhap
#             42 = het quota Claude Pro
#             43 = Claude ghi sai pham vi / tampered
#             44 = khong co Audit Run moi / inconclusive
#             45 = loi CLI khong phan loai duoc hoac script bi ngat
#             46 = mat snapshot
#             47 = luot cu chua don (STALE_RUN / STALE_APPROVAL_TAG)

# 0. Dam bao UTF-8 cho tien trinh con
if (Get-Command chcp.com -ErrorAction SilentlyContinue) {
    chcp.com 65001 >$null
}
try {
    [Console]::InputEncoding = [System.Text.Encoding]::UTF8
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
} catch {
}
$OutputEncoding = [System.Text.Encoding]::UTF8

# Cwd: luon dat ve repo root va don dep bang Pop-Location
Push-Location -LiteralPath (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
try {
    # 1. Xoa bien moi truong proxy de Claude Code dung xac thuc Claude Pro chinh chu
    $env:ANTHROPIC_BASE_URL = $null
    $env:ANTHROPIC_AUTH_TOKEN = $null
    $env:ANTHROPIC_API_KEY = $null

    # 2. Dam bao .local/bin co trong PATH
    $claudeBin = Join-Path $env:USERPROFILE ".local\bin"
    if ($env:PATH -notlike "*$claudeBin*") {
        $env:PATH = "$claudeBin;$env:PATH"
    }

    # 3. Pre-flight kiem tra theo thu tu co dinh (m-h):
    # 3a. Tim claude.exe
    $claudeExe = Join-Path $claudeBin "claude.exe"
    if (-not (Test-Path $claudeExe)) {
        $found = Get-Command claude.exe -ErrorAction SilentlyContinue
        if ($found) {
            $claudeExe = $found.Source
        } else {
            Write-Error "[CLAUDE_NOT_FOUND] Khong tim thay claude.exe trong .local/bin hoac PATH."
            exit 40
        }
    }

    # 3b. Kiem tra template va settings
    $templatePath = ".agents/scripts/claude-reviewer-prompt.md"
    $settingsPath = ".agents/scripts/claude-reviewer-settings.json"
    if (-not (Test-Path $templatePath) -or -not (Test-Path $settingsPath)) {
        Write-Error "[TEMPLATE_NOT_FOUND] Khong tim thay template hoac settings tai .agents/scripts/"
        exit 1
    }

    # 3c. Xac dinh file plan
    if (-not $PlanFile) {
        $latestPlan = Get-ChildItem -Path "docs/plans/*.md" -Exclude "README.md" -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
        if ($latestPlan) {
            $PlanFile = $latestPlan.FullName
        } else {
            Write-Error "[PLAN_NOT_FOUND] Khong tim thay file ke hoach trong docs/plans/."
            exit 1
        }
    }

    if (-not (Test-Path $PlanFile)) {
        Write-Error "[PLAN_NOT_FOUND] File plan khong ton tai: $PlanFile"
        exit 1
    }

    $relPlan = (Resolve-Path -Relative -ErrorAction Stop $PlanFile).Replace('\', '/')
    if ($relPlan.StartsWith('./')) {
        $relPlan = $relPlan.Substring(2)
    }

    if (-not ($relPlan -match '^docs/plans/\d{4}-\d{2}-\d{2}-[a-z0-9-]+-implementation-plan\.md$')) {
        Write-Error "[PLAN_PATH_INVALID] Duong dan plan khong dung dinh dang docs/plans/YYYY-MM-DD-*-implementation-plan.md: $relPlan"
        exit 1
    }

    # 3d. Kiem tra dang nhap va CheckOnly
    $claudeConfig = Join-Path $env:USERPROFILE ".claude.json"
    if (-not (Test-Path $claudeConfig)) {
        Write-Error "[CLAUDE_NOT_AUTHENTICATED] Khong tim thay .claude.json. Hay chay 'claude' tren terminal de dang nhap."
        exit 41
    }

    if ($CheckOnly) {
        Write-Host "[CLAUDE_READY] Claude Code CLI da duoc cau hinh va san sang."
        exit 0
    }

    # 4. Goi review-run.js prepare
    $prepareArgs = @(".agents/scripts/review-run.js", "prepare", "--plan", $relPlan)
    if ($NoGradle) {
        $prepareArgs += "--no-gradle"
    }

    $prepareOutput = & node @prepareArgs 2>&1
    $prepExit = $LASTEXITCODE
    if ($prepExit -ne 0) {
        $msg = ($prepareOutput | ForEach-Object { "$_" }) -join "`n"
        Write-Host $msg
        exit $prepExit
    }

    $runDir = ($prepareOutput | ForEach-Object { "$_" } | Where-Object { $_ -match '\S' } | Select-Object -Last 1).Trim().Replace('\', '/')

    # 5. Review run trong khoi try/finally rieng
    $finalized = $false
    try {
        [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

        $outPath = Join-Path $runDir "claude-output.txt"
        $promptPath = (Join-Path $runDir "prompt.md").Replace('\', '/')
        $evidencePath = (Join-Path $runDir "evidence.md").Replace('\', '/')

        $promptArg = "Read and follow every instruction in $promptPath"
        $allowedTools = "Edit($relPlan) Read($promptPath) Read($evidencePath)"

        $claudeArgs = @(
            "-p", $promptArg,
            "--permission-mode", "dontAsk",
            "--tools", "Read,Grep,Glob,Edit,WebFetch,WebSearch",
            "--settings", ".agents/scripts/claude-reviewer-settings.json",
            "--allowedTools", $allowedTools,
            "--disallowedTools", "Write", "NotebookEdit", "Bash", "PowerShell", "mcp__*"
        )
        if ($Model) {
            $claudeArgs += @("--model", $Model)
        }

        Write-Host "[CLAUDE_REVIEWER] Bat dau tham dinh: $relPlan (Run dir: $runDir)"
        $prevEap = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            $lines = & $claudeExe @claudeArgs 2>&1 | ForEach-Object { "$_" }
            $claudeExit = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $prevEap
        }

        [System.IO.File]::WriteAllText($outPath, ($lines -join "`n"), [System.Text.UTF8Encoding]::new($false))
        Write-Host ($lines -join "`n")

        $finalizeArgs = @(
            ".agents/scripts/review-run.js", "finalize",
            "--plan", $relPlan,
            "--run-dir", $runDir,
            "--exit", "$claudeExit"
        )
        if ($Model) {
            $finalizeArgs += @("--model", $Model)
        }

        $finOutput = & node @finalizeArgs 2>&1
        $finExit = $LASTEXITCODE
        $finalized = $true
        Write-Host (($finOutput | ForEach-Object { "$_" }) -join "`n")
        exit $finExit
    } finally {
        if (-not $finalized -and $runDir) {
            Write-Warning "[CLAUDE_REVIEW_INTERRUPTED] Luot review bi gian doan. Dang khoi phuc snapshot..."
            & node .agents/scripts/review-run.js restore --plan $relPlan --run-dir $runDir
            exit 45
        }
    }
} finally {
    Pop-Location
}
