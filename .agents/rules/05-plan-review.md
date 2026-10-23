---
trigger: always_on
description: Giao thức Plan → Review → Revise, Code Freeze Guard, Zero-Self-Execution, Dual-Reviewer (Claude CLI primary + Gemini 4 Argon @reviewer fallback), severity rubric, pre-flight gate.
---

# Giao Thức Plan → Review → Revise & Cơ Chế Cưỡng Chế

## 1. Code Freeze & Hook Tự Động

- **Nguyên tắc**: KHÔNG agent nào (`@planner`, `@reviewer`, Orchestrator) được tạo, sửa hay xóa source (`app/`, `*.gradle.kts`, `.github/`, cấu hình...) cho task không tầm thường khi plan chưa có thẻ `[PLAN_APPROVED]`.
- **Layer 2 `.md`-Only Guard**: trước khi duyệt, chỉ cho phép ghi file có đuôi `.md` trong `docs/**` (gồm `docs/plans/`), `implementation_plan.md`/`task.md`/`walkthrough.md` ở root, và artifact trong `~/.gemini/antigravity/brain/`. Ghi file không phải `.md` (`.js`, `.mjs`, `.ps1`, `.bat`) vào `docs/**` bị chặn (`ask`).
- **Platform Hook Guard**: hook `PreToolUse` và `PostToolUse` (`.agents/scripts/code-freeze-guard.js` qua `.agents/hooks.json`) kiểm soát `write_to_file`/`write_file`/`Write`, `replace_file_content`/`edit_file`/`Edit`, `multi_replace_file_content`/`MultiEdit`, `Read`/`read_file`/`view_file`, và `run_command`:
  1. _Read Guard_: chặn (`ask`) đọc file secret (`keystore*`, `*.jks`, `local.properties`, `.env*`, `.git/`, `.gemini/`) hoặc đường dẫn ngoài repo (trừ Antigravity artifact).
  2. _Chưa duyệt_: ghi vào `app/**` và chạy lệnh ngoài allowlist đều hỏi User (`ask`). `force_ask` chỉ dùng cho lỗi nội bộ guard (`[GUARD_MODULE_LOAD_FAILED]`, `[PAYLOAD_UNREADABLE]`).
  3. _Đã duyệt_: tự cho phép ghi vào source thông thường (UI, ViewModel, scraper, repository, util...) và các lệnh `ktlintFormat`, `clean`, `assembleRelease`, `installDebug`.
  4. _Luôn hỏi_, kể cả khi đã duyệt:
     - Room: `data/local/AppDatabase.kt`, `app/schemas/**`
     - Manifest & Security: `AndroidManifest.xml`, `res/xml/network_security_config*`, `backup_rules*`, `data_extraction_rules*`
     - Cookie & Auth: `data/remote/cookie/**`, `data/remote/auth/**`
     - Build/CI & Test Infra: `build.gradle.kts`, `app/build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, wrapper, `.editorconfig`, `.github/workflows/ci.yml`, `app/proguard-rules.pro`
     - Test & Secret & Governance: `app/src/test/**`, `app/src/androidTest/**` (Test Integrity), `keystore*`, `*.jks`, `local.properties`, `.env*`, `.agents/**`, `AGENTS.md`, `.claude/**`, `CLAUDE.md`, `.mcp.json`
  5. Payload không đọc được thì fail closed. Allowlist lệnh read-only: `.agents/agents/reviewer/allowlist.md`.

## 2. Zero-Self-Execution

- Orchestrator chỉ điều phối, KHÔNG tự viết plan trên luồng chính, KHÔNG tự sinh `[PLAN_APPROVED]`.
- Plan mới BẮT BUỘC dispatch qua `@planner`: `invoke_subagent` (TypeName `self`) với prompt `"Đọc và tuân thủ tuyệt đối .agents/agents/planner/agent.md. Task: …"`.
- Task tầm thường (typo, đổi 1 chuỗi, chỉ sửa docs, câu hỏi) không cần plan.

## 3. Dual-Reviewer & Uninterrupted Chain

1. **Primary: Claude Code CLI** (tài khoản Claude Pro của User):
   `powershell -ExecutionPolicy Bypass -File .agents/scripts/run-claude.ps1 -PlanFile docs/plans/<plan>.md` (thêm `-NoGradle` nếu chỉ chạm `.agents/**` hoặc `docs/**`).
   - Tự chụp `before.md`, thu thập `evidence.md`, chạy Claude CLI trong sandbox (không Bash, deny secret), xác minh append-only qua `review-run.js finalize`, tự khôi phục nếu lỗi/tampered.
   - Exit codes: `0` (Hoàn tất; nếu `APPROVED`, `finalize` tự đặt header `Approved`); `1` (Sai định dạng/đã đóng); `40/41/42/44/45` (Chuyển sang fallback `@reviewer`); `43/46/47` (Dừng chuỗi và báo User; gỡ `47` bằng `node .agents/scripts/review-run.js restore --plan <rel> --run-dir <dir>`).
2. **Fallback: subagent `@reviewer` (`gemini-4-argon`, `model: pro`, thinking tối đa)**:
   - Kích hoạt khi Primary trả exit `40/41/42/44/45`. Dispatch qua `invoke_subagent` với prompt `"Đọc và tuân thủ tuyệt đối .agents/agents/reviewer/agent.md. Review plan: docs/plans/<plan>.md"`.
   - `@reviewer` CHỈ append Audit Run bằng 1 chunk (`[REVIEWER_MODEL: <model>]` … dòng kết luận chuẩn), TUYỆT ĐỐI KHÔNG ghi thẻ hay đổi header.
   - Nếu `APPROVED`: Orchestrator append `[PLAN_APPROVED]` và đổi header sang `Approved`; hook hỏi User xác nhận kèm `summarizeFallback`.
3. **Chuỗi không ngắt quãng**: `@planner` → Reviewer → nếu `[CHANGES_REQUESTED]` thì gửi lại `@planner` sửa rồi gọi Reviewer lại (tối đa **10 vòng**). `[NEEDS_PRODUCT_DECISION]` trình User SAU khi có `[PLAN_APPROVED]`.

## 4. Severity Rubric

Đối chiếu `allowlist.md`, `evidence-bar.md`, `mental-simulation.md` (S1–S6 + Domain):
- **`[BLOCKER]`**: vi phạm layer, công nghệ cấm, Room migration mất dữ liệu, lỗi bảo mật/riêng tư, rủi ro Cao không có control → `[CHANGES_REQUESTED]`.
- **`[MAJOR]`**: thiếu test/Test Contract/Skills Loaded & Applied/Caller-First Design Sketch, sót edge case nghiêm trọng, UI tiếng Anh, `!!`/`GlobalScope`/catch nuốt lỗi, thiếu cập nhật docs, S1–S5 `UNVERIFIED` → `[CHANGES_REQUESTED]`.
- **`[MINOR]`**: style, đặt tên, tối ưu nhỏ. Không chặn; được cấp `[PLAN_APPROVED]` kèm `[Advisory Recommendations]`.

## 5. Cấu Trúc File Plan & Máy Trạng Thái

File `docs/plans/YYYY-MM-DD-<task>-implementation-plan.md` gồm `## Technical Plan` và `## Audit History`.
- `Draft` / `Ready for Review`: **blocking** → repo đóng băng.
- `Approved` / `In Progress`: mở khóa (`isUnlocking`) khi dòng cuối là `[PLAN_APPROVED]`; nội dung plan bị khóa (`[APPROVED_PLAN_LOCKED]`), chỉ cho phép 1 chunk đổi dòng trạng thái header.
- `Completed` / `Superseded`: đã đóng (`isClosed`), không mở khóa, không blocking.
- Kiểm tra trạng thái độc lập: `node .agents/scripts/plan-approval.js status`.

## 6. Pre-Flight Gate & `@qa` (On-Demand)

- **Pre-Flight Gate**: (1) Plan ghi đủ trong `docs/plans/`? (2) Đã qua Reviewer? (3) `plan-approval.js status` báo `approved: true`? (4) `[NEEDS_PRODUCT_DECISION]` đã trình User?
- **`@qa`**: chỉ chạy khi User gọi `@qa` để nghiệm thu độc lập 7 bước (`[QA_PASSED]` / `[QA_FAILED]`).
