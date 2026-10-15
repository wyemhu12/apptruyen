# AppTruyen Multi-Agent Governance (`.agents/`)

`.agents/` là trung tâm cấu hình hệ thống đa tác tử của **AppTruyen**, adopt từ **Project AURORA** và **Vikini** rồi điều chỉnh cho Android/Kotlin. Thư mục gồm: rules, subagents (planner / reviewer / qa), workflows, Code Freeze Guard, và skills Android chính thức (`skills/`).

## 1. Luồng Phối Hợp

```text
User (PM / QA) ──[Task]──▶ Orchestrator (luồng chính)
                                 │  Zero-Self-Execution
                                 ▼
                         [ @planner ]  → docs/plans/<date>-<task>-implementation-plan.md
                                 │  Auto-loop tối đa 10 vòng
                                 ▼
                 [ Lead Reviewer: Claude CLI (run-claude.ps1) ]
                    └─ exit 40/41/42 → [ @reviewer fallback ]
                                 │
                  ┌──────────────┴──────────────┐
          [CHANGES_REQUESTED]            [PLAN_APPROVED] (dòng cuối plan)
                  │                              │
          gửi lại @planner             Pre-Flight Gate → trình [NEEDS_PRODUCT_DECISION]
                                                 │
                                   Code Freeze Guard mở khóa → triển khai
                                                 │
                     ktlintCheck; testDebugUnitTest; assembleDebug → cập nhật docs
                                                 │
                                       (User gọi @qa nếu muốn nghiệm thu)
```

### Vai Trò

1. **User**: đặt yêu cầu, chốt quyết định sản phẩm, nghiệm thu.
2. **Orchestrator**: điều phối và không tự viết plan. Sau `[PLAN_APPROVED]` thì làm Lead Android Developer triển khai code.
3. **`@planner`** (`agents/planner/agent.md`): khảo sát codebase, tra cứu online, viết plan.
4. **Lead Reviewer**: Claude Code CLI (`scripts/run-claude.ps1` + `scripts/claude-reviewer-prompt.md`); dự phòng là `@reviewer` (`agents/reviewer/agent.md`). Quy trình 7 bước, 6 kịch bản S1–S6, chỉ chạy lệnh read-only.
5. **`@qa`** (`agents/qa/agent.md`): chỉ chạy khi User gọi, nghiệm thu 7 bước và audit tính toàn vẹn của test.

### Cách Gọi Trong Antigravity

- Planner: `invoke_subagent` (TypeName `self`), prompt: `Đọc và tuân thủ tuyệt đối .agents/agents/planner/agent.md. Task: <mô tả>`
- Reviewer chính: `powershell -ExecutionPolicy Bypass -File .agents/scripts/run-claude.ps1 -PlanFile docs/plans/<plan>.md`
- Reviewer dự phòng: `invoke_subagent`, prompt: `Đọc và tuân thủ tuyệt đối .agents/agents/reviewer/agent.md. Review plan: docs/plans/<plan>.md`
- QA: User gõ `@qa`, Orchestrator gọi `invoke_subagent` với `.agents/agents/qa/agent.md`.

## 2. Rules Catalog (≤ 12.000 ký tự/file)

| #  | File                                       | Trigger          | Trọng tâm                                                                  |
| -- | ------------------------------------------ | ---------------- | -------------------------------------------------------------------------- |
| 00 | [00-core.md](./rules/00-core.md)           | always_on        | Giao tiếp tiếng Việt, stack bất biến, ranh giới kiến trúc, terminal/Gradle, git, release |
| 01 | [01-coding.md](./rules/01-coding.md)       | always_on        | Kotlin/Compose, xử lý lỗi, yêu cầu test, cấu trúc file, đặt tên, kích thước |
| 02 | [02-quality.md](./rules/02-quality.md)     | always_on        | Pre-work, verification tiers, triage test A/B/C, post-fix, debug            |
| 03 | [03-ui.md](./rules/03-ui.md)               | model_decision   | Material 3, theme token, UI 100% tiếng Việt, state/event, hiệu năng         |
| 04 | [04-scraping.md](./rules/04-scraping.md)   | model_decision   | BaseScraper, selector, lỗi/độ bền, cookie, test fixture                     |
| 05 | [05-plan-review.md](./rules/05-plan-review.md) | always_on    | Code freeze, Zero-Self-Execution, Dual-Reviewer, severity, pre-flight gate  |
| 06 | [06-docs.md](./rules/06-docs.md)           | always_on        | Ma trận cập nhật docs, template commit                                      |

Workflows: [`/update-docs`](./workflows/update-docs.md), [`/debug`](./workflows/debug.md), [`/post-fix`](./workflows/post-fix.md).

## 3. Code Freeze Guard & Phê Duyệt Kế Hoạch

Hook `PreToolUse` và `PostToolUse` trong [hooks.json](./hooks.json) chạy [code-freeze-guard.js](./scripts/code-freeze-guard.js) cho cả 4 tool: `write_to_file | replace_file_content | multi_replace_file_content | run_command`:

- **Luôn cho phép**: ghi `docs/**` (trừ thay đổi trạng thái phê duyệt trong `docs/plans/`), artifact planning ở root, artifact Antigravity; lệnh read-only (`gradlew` verify, git read-only trong repo, `Get-Content`/`Select-String`, `curl.exe -q` chỉ tới host nguồn truyện, `node --test`, `plan-approval.js status`).
- **Trước khi mở khóa**: hỏi User (`ask`) khi ghi vào `app/**` hoặc chạy lệnh ngoài allowlist.
- **Điều kiện mở khóa**: mọi plan trong `docs/plans/` (trừ README) không bị blocking, và có ít nhất một plan có header ∈ {`Approved`, `In Progress`} kèm dòng không trống cuối cùng là `[PLAN_APPROVED]`. Kiểm tra độc lập: `node .agents/scripts/plan-approval.js status`.
- **Sau khi mở khóa**: cho phép ghi source thông thường và chạy `ktlintFormat`/`clean`/`assembleRelease`/`installDebug`.
- **Khóa nội dung plan**: khi plan đã duyệt (`Approved`/`In Progress`), toàn bộ nội dung plan bị khóa để chống sửa scope sau duyệt (`[APPROVED_PLAN_LOCKED]`).
- **Khóa ghi đồng thời (chống TOCTOU)**: các thao tác ghi song song lên cùng plan nháp/đã đóng bị chặn ngay bởi `[CONCURRENT_PLAN_WRITE]` (`deny`), giải phóng qua `PostToolUse`.
- **Luôn hỏi**: Room DB/schema, Manifest, network/backup config, cookie/auth, build/CI config, test files (kèm tín hiệu Test Integrity), secrets, `.agents/**`, `AGENTS.md`. User lưu ý **không bấm "Always Allow"** cho các hộp hỏi này để tránh bypass.
- **Reviewer Sandbox**: Claude Code CLI chạy không có tool `Bash` (`--disallowedTools Bash`); bằng chứng lệnh được script thu thập độc lập vào `evidence.md`.
- Tạm tắt khẩn cấp: đặt `"enabled": false` trong `hooks.json` hoặc tạo file marker `.agents/.plan-approved` chứa chuỗi `true`.
- Test: `node --test .agents/scripts/code-freeze-guard.test.js`.

## 4. Khác Biệt So Với Aurora / Vikini

| Hạng mục           | Aurora / Vikini (Next.js)                     | AppTruyen (Android)                                                      |
| ------------------ | --------------------------------------------- | ------------------------------------------------------------------------ |
| Verify             | `npm run type-check / lint / test:run`        | `.\gradlew.bat compileDebugKotlin / ktlintCheck / testDebugUnitTest / assembleDebug` |
| Rule 04            | Song ngữ VI/EN                                | Scraping (UI chỉ tiếng Việt, gộp vào 03-ui)                               |
| Rule 06            | (không có)                                    | Cập nhật docs (chuyển từ `.agent/rules/update-docs.md`)                   |
| File nhạy cảm      | `*.server.ts`, Supabase migrations            | Room DB/schemas, Manifest, cookie/auth, Gradle/CI, keystore               |
| Test Integrity     | Vitest `expect`, `.skip`                      | JUnit 5 `assert*`, MockK `verify/coVerify`, `@Disabled`, `assume*`        |
| Mental simulation  | Supabase RLS, SSE, serverless                 | Room migration, Coroutines/Service lifecycle, TTS, nguồn truyện, cookie   |
| Test của guard     | Vitest (`.ts`)                                | `node:test` (`.js`), không thêm dependency                                |

## 5. Model Routing

| Tác vụ                                  | Tier đề xuất                                   |
| --------------------------------------- | ---------------------------------------------- |
| Lập kế hoạch kiến trúc                  | High/Thinking (`gemini-3.8-flash` max thinking / Claude Opus) |
| Review plan                             | Premium (Claude Opus qua Claude CLI)           |
| Code tính năng, debug phức tạp, viết test | Full model                                   |
| Sửa typo, rename, cập nhật docs đơn giản | Light/Flash                                   |

Mặc định chọn full model khi không chắc (`Correctness > Cost`). Phần nhạy cảm (Room migration, cookie/auth, giải mã) luôn dùng full model.
