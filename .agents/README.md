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
                    └─ exit 40/41/42/44/45 → [ @reviewer fallback (Gemini 4 Argon) ]
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
3. **`@planner`** (`agents/planner/agent.md`, `gemini-4-argon`): khảo sát codebase, tra cứu online, viết plan.
4. **Lead Reviewer**: Claude Code CLI (`scripts/run-claude.ps1` + `scripts/claude-reviewer-prompt.md`); dự phòng là `@reviewer` (`agents/reviewer/agent.md`, `gemini-4-argon`). Quy trình 7 bước, 6 kịch bản S1–S6, thẩm định độc lập, chỉ chạy lệnh read-only.
5. **`@qa`** (`agents/qa/agent.md`, `claude-4.6-opus` / fallback `gemini-4-argon`): chỉ chạy khi User gọi, nghiệm thu 7 bước và audit tính toàn vẹn của test.

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
| 02 | [02-quality.md](./rules/02-quality.md)     | always_on        | Pre-work, verification tiers, triage test A/B/C, post-fix, debug, audit     |
| 03 | [03-ui.md](./rules/03-ui.md)               | model_decision   | Material 3, theme token, UI 100% tiếng Việt, state/event, hiệu năng         |
| 04 | [04-scraping.md](./rules/04-scraping.md)   | model_decision   | BaseScraper, selector, lỗi/độ bền, cookie, test fixture                     |
| 05 | [05-plan-review.md](./rules/05-plan-review.md) | always_on    | Code freeze, Zero-Self-Execution, Dual-Reviewer, severity, pre-flight gate  |
| 06 | [06-docs.md](./rules/06-docs.md)           | always_on        | Ma trận cập nhật docs, template commit                                      |

Workflows: [`/update-docs`](./workflows/update-docs.md), [`/debug`](./workflows/debug.md), [`/post-fix`](./workflows/post-fix.md), [`/audit`](./workflows/audit.md).

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

## 5. Agent Task-to-Model Routing Guide

Hướng dẫn lựa chọn mô hình tối ưu chi phí và năng lực cho từng nhóm tác vụ theo kiến trúc **Orchestrator–Worker Split**:

### 5.1. Ma Trận Phân Bổ Tác Vụ Sang Model Tier

| Nhóm Tác Vụ                                   | Khuyến Nghị Model Tier | Mô Hình Cụ Thể (2026)                                           | Cơ Sở Lựa Chọn                                                                         |
| :-------------------------------------------- | :--------------------: | :-------------------------------------------------------------- | :------------------------------------------------------------------------------------- |
| **Lập kế hoạch kiến trúc (Planning)**         |    High / Thinking     | `gemini-4-argon` (max thinking) / `claude-opus-5-5`             | Cần khả năng tổng hợp ngữ cảnh rộng (1M context) và suy luận chiến lược sâu đa tầng.   |
| **Thẩm định & Review (Reviewing)**            |     Premium / Full     | `claude-opus-5-5` (Claude Pro CLI) / Fallback: `gemini-4-argon` | Yêu cầu đối kháng logic khắt khe (S1–S6), phát hiện edge cases và lỗi kiến trúc.       |
| **Phát triển tính năng & Debug phức tạp**     |       Full Model       | `gemini-4-argon` / `claude-sonnet-5-5` / `gemini-3.8-flash`     | Cần phân tích luồng dữ liệu đa tệp, xử lý race conditions và logic nghiệp vụ phức tạp. |
| **Viết Unit Tests & Integration Tests**       |       Full Model       | `gemini-4-argon` / `gemini-3.8-flash` / `claude-sonnet-5-5`     | Đòi hỏi hiểu sâu boundary conditions, Test Contract và chống specification gaming.     |
| **Sửa lỗi chính tả, format, rename đơn giản** |    Smaller / Light     | `gemini-3.8-flash` (Fast Worker — low/minimal thinking)         | Thao tác tất định, có khuôn mẫu rõ ràng, phản hồi tức thì, không cần suy luận sâu.     |
| **Cập nhật tài liệu văn bản thông thường**    |     Smaller Model      | `gemini-3.8-flash` (Fast Worker)                                | Sinh văn bản mô tả chuẩn hóa, độ phức tạp logic thấp, tối ưu chi phí token.            |

### 5.2. Nguyên Tắc Orchestrator–Worker Split & Nhận Thức Chi Phí (Cost Awareness)

- **Phân tách Orchestrator / Deep Reasoning vs. Fast Worker**: Sử dụng `gemini-4-argon` (hoặc `claude-opus-5-5`) cho các tác vụ đòi hỏi suy luận kiến trúc, thẩm định kế hoạch (`@reviewer` fallback) và kiểm toán chất lượng (`@qa` fallback); sử dụng `gemini-3.8-flash` làm Fast Worker cho các tác vụ thực thi nhẹ, có tính tất định cao.
- **Mặc định dùng Full Model khi chưa rõ ràng**: Tính đúng đắn của giải pháp luôn được ưu tiên hơn tiết kiệm chi phí (`Correctness > Cost savings`).
- **Chi phí phát sinh chủ yếu từ vòng lặp retry**: Việc chọn mô hình quá yếu dẫn đến thất bại kiểm thử nhiều lần (fix → verify → fail → fix) sẽ tốn kém token và thời gian gấp nhiều lần so với việc hoàn thành chuẩn xác ngay từ lượt đầu bằng Full Model (`gemini-4-argon`).
- **Tuyệt đối không dùng mô hình yếu cho phần nhạy cảm**: Các tác vụ liên quan đến bảo mật và toàn vẹn dữ liệu (Room migration, cookie/auth, giải mã nội dung) bắt buộc phải sử dụng Full Model.
