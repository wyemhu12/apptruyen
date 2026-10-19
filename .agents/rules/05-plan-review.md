---
trigger: always_on
description: Giao thức Plan → Review → Revise, Code Freeze Guard, Zero-Self-Execution, Dual-Reviewer (Claude CLI primary + Gemini 4 Argon @reviewer fallback), severity rubric, pre-flight gate.
---

# Giao Thức Plan → Review → Revise & Cơ Chế Cưỡng Chế

## 1. Code Freeze & Hook Tự Động

- **Nguyên tắc**: KHÔNG agent nào (`@planner`, `@reviewer`, Orchestrator) được tạo, sửa hay xóa source (`app/`, `*.gradle.kts`, `.github/`, cấu hình...) cho task không tầm thường khi plan chưa có thẻ `[PLAN_APPROVED]` từ Lead Reviewer.
- **Được ghi trước khi duyệt**: `docs/**` (bao gồm `docs/plans/`), `implementation_plan.md`/`task.md`/`walkthrough.md` ở root, và artifact trong `~/.gemini/antigravity/brain/`.
- **Platform Hook Guard**: hook `PreToolUse` và `PostToolUse` (`.agents/scripts/code-freeze-guard.js`, khai báo trong `.agents/hooks.json` với `--event pre/post`) chặn `write_to_file`, `replace_file_content`, `multi_replace_file_content`, `run_command`:
  1. _Chưa duyệt_: ghi vào `app/**` và chạy lệnh ngoài allowlist đều phải hỏi User (`ask`). `force_ask` chỉ dùng duy nhất cho lỗi nội bộ guard (`[GUARD_MODULE_LOAD_FAILED]`, `[PAYLOAD_UNREADABLE]`).
  2. _Đã duyệt_: tự cho phép ghi vào source thông thường (UI, ViewModel, scraper, repository, util...) và các lệnh `ktlintFormat`, `clean`, `assembleRelease`, `installDebug`.
  3. _Luôn hỏi_, kể cả khi đã duyệt:
     - Room: `data/local/AppDatabase.kt`, `app/schemas/**`
     - Manifest & Security: `AndroidManifest.xml`, `res/xml/network_security_config*`, `backup_rules*`, `data_extraction_rules*`
     - Cookie & Auth: `data/remote/cookie/**`, `data/remote/auth/**`
     - Build/CI & Test Infra: `build.gradle.kts`, `app/build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, wrapper, `.editorconfig`, `.github/workflows/ci.yml`, `app/proguard-rules.pro`
     - Test: `app/src/test/**`, `app/src/androidTest/**`, kèm tín hiệu Test Integrity
     - Secret & Governance: `keystore*`, `*.jks`, `local.properties`, `.env*`, `.agents/**`, `AGENTS.md`, `.claude/**`, `CLAUDE.md`, `.mcp.json`
  4. Payload không đọc được thì hỏi (fail closed).
- Danh sách lệnh read-only được tự động cho phép: xem `.agents/agents/reviewer/allowlist.md`.

## 2. Zero-Self-Execution

- Orchestrator chỉ điều phối. KHÔNG tự viết plan trên luồng chính, KHÔNG tự sinh `[PLAN_APPROVED]`.
- Plan mới BẮT BUỘC dispatch qua `@planner`. Trong Antigravity: `invoke_subagent` (TypeName `self`) với prompt "Đọc và tuân thủ tuyệt đối `.agents/agents/planner/agent.md`. Task: …".
- Task tầm thường (typo, đổi 1 chuỗi, chỉ sửa docs, câu hỏi/khảo sát) không cần plan. Nếu không chắc thì coi là không tầm thường.

## 3. Dual-Reviewer & Uninterrupted Chain

1. **Primary: Claude Code CLI** (tài khoản Claude Pro của User):
   `powershell -ExecutionPolicy Bypass -File .agents/scripts/run-claude.ps1 -PlanFile docs/plans/<plan>.md`
   - Khi plan chỉ sửa `.agents/**` và/hoặc `docs/**` (không chạm `app/` hay file Gradle), thêm cờ `-NoGradle` để bỏ qua các lệnh Gradle trong `evidence.md`.
   - Script tự động chụp snapshot `before.md`, thu thập bằng chứng vào `evidence.md`, gọi Claude CLI với sandbox giới hạn (không Bash, deny secret), xác minh kết quả append-only qua `review-run.js finalize`, và tự động khôi phục nếu thất bại hoặc tampered.
   - Exit codes và hành vi điều phối:
     * `0`: Hoàn tất (`APPROVED` hoặc `CHANGES_REQUESTED`). Nếu APPROVED, `finalize` đã tự động cập nhật header sang `Approved`.
     * `1`: Thiếu plan/template, sai định dạng, hoặc plan đã đóng (`Completed`/`Superseded`).
     * `40` (không có CLI), `41` (chưa đăng nhập), `42` (`[CLAUDE_PRO_QUOTA_EXCEEDED]`), `44` (`[REVIEW_INCONCLUSIVE]`), `45` (`[CLAUDE_CLI_ERROR]`): Tự động chuyển sang fallback `@reviewer`.
     * `43` (`[APPROVAL_ATTESTATION_FAILED]` do Claude sửa ngoài phần append), `46` (mất snapshot), `47` (`[STALE_RUN]` / `[STALE_APPROVAL_TAG]` lượt cũ chưa dọn): Dừng chuỗi và báo User. Cách gỡ 47: chạy `node .agents/scripts/review-run.js restore --plan <rel> --run-dir <dir>` với sự xác nhận của User.
2. **Fallback: subagent `@reviewer` (`gemini-4-argon` với thinking budget tối đa)**:
   - Dùng khi Primary trả exit 40, 41, 42, 44, 45 hoặc lỗi kết nối. Dispatch bằng `invoke_subagent` với prompt "Đọc và tuân thủ tuyệt đối `.agents/agents/reviewer/agent.md`. Review plan: docs/plans/<plan>.md".
   - `@reviewer` CHỈ append Audit Run vào cuối file plan bằng 1 chunk (bắt đầu bằng `[REVIEWER_MODEL: <model>]`, kết thúc bằng dòng kết luận chuẩn). `@reviewer` TUYỆT ĐỐI KHÔNG ghi dòng thẻ hay đổi header.
   - Nếu kết luận là APPROVED: Orchestrator dùng `multi_replace_file_content` ghi header `Approved` và append dòng thẻ. Hook sẽ hỏi User xác nhận kèm tóm tắt `summarizeFallback` (User xác nhận 1 lần).
3. **Chuỗi không ngắt quãng**: `@planner` xong thì gọi Reviewer ngay. Nếu `[CHANGES_REQUESTED]` thì gửi lại `@planner` sửa, rồi gọi Reviewer lại. Không dừng lại hỏi User giữa các bước.
   - Tối đa **10 vòng**. Hết 10 vòng mà chưa có `[PLAN_APPROVED]` thì Circuit Breaker dừng chuỗi và trình User.
   - `[NEEDS_PRODUCT_DECISION]` trong plan không làm dừng chuỗi; câu hỏi được trình User SAU khi plan được duyệt.

## 4. Severity Rubric

Mọi lượt review đối chiếu `allowlist.md`, `evidence-bar.md`, `mental-simulation.md` (S1 Build, S2 Lifecycle, S3 Room/DataStore, S4 State Flow, S5 Security/Privacy, S6 External Resilience + Domain).

- **`[BLOCKER]`**: vi phạm layer (logic nghiệp vụ trong Composable, ViewModel gọi thẳng scraper/DAO), công nghệ cấm (XML layout, RxJava, Koin/manual DI, Retrofit cho scraping, SQLite thô), Room migration mất dữ liệu, lỗi bảo mật/riêng tư, rủi ro Cao không có control → `[CHANGES_REQUESTED]`.
- **`[MAJOR]`**: thiếu test/Test Contract, sót edge case nghiêm trọng, UI không phải tiếng Việt, `!!`/`GlobalScope`/catch nuốt lỗi, thiếu cập nhật docs, S1–S5 `UNVERIFIED` → `[CHANGES_REQUESTED]`.
- **`[MINOR]`**: style, đặt tên, tối ưu nhỏ. Không chặn; được cấp `[PLAN_APPROVED]` kèm `[Advisory Recommendations]`.

## 5. Cấu Trúc File Plan & Phê Duyệt

File `docs/plans/YYYY-MM-DD-<task>-implementation-plan.md` (template ở `docs/plans/README.md`) có 2 phần:

1. `## Technical Plan`: do `@planner` viết và cập nhật. Header gồm H1 là dòng không trống đầu tiên, theo sau là khối blockquote chứa `Trạng thái` và `Revision`.
2. `## Audit History`: Reviewer chỉ append `### Audit Run N`, không sửa phần Technical Plan.

### Máy Trạng Thái Plan
- `Draft` / `Ready for Review`: `@planner` đặt. Trạng thái **blocking** → repo đóng băng.
- `Approved`: Primary đặt tự động qua `finalize`, fallback do Orchestrator đặt sau khi User duyệt. Mở khóa khi dòng không trống cuối cùng là thẻ `[PLAN_APPROVED]`; ngược lại là blocking.
- `In Progress`: Orchestrator đặt khi bắt đầu code (1 chunk, tự cho phép). Mở khóa nếu có thẻ.
- `Completed`: Orchestrator đặt khi triển khai xong (1 chunk, tự cho phép; từ blocking thì hỏi). Trạng thái đóng: không mở khóa, không blocking.
- `Superseded`: Orchestrator đặt khi hủy/thay thế plan (1 chunk; từ blocking thì hỏi). Trạng thái đóng.
- Thiếu header hoặc giá trị khác: **blocking** (fail closed).

### Khóa Nội Dung Plan Đã Duyệt
- Khi header ∈ {`Approved`, `In Progress`}, toàn bộ nội dung plan bị khóa. Mọi thao tác sửa Technical Plan đều bị hook hỏi (`[APPROVED_PLAN_LOCKED]`).
- Muốn đổi phạm vi task sau khi đã duyệt: đổi header sang `Superseded` rồi lập plan mới.
- Ghi chú hoàn tất hoặc kết quả probe chỉ ghi vào `walkthrough.md`, hoặc ghi vào plan sau khi đã chuyển header sang `Completed`.

### Điều Kiện Mở Khóa Toàn Cục
- Repo được mở khóa khi và chỉ khi: (1) Marker khẩn cấp `.agents/.plan-approved` có nội dung `true`; HOẶC (2) KHÔNG có bất kỳ plan nào trong `docs/plans/*.md` (trừ README.md) ở trạng thái blocking (`!isUnlocking && !isClosed`), VÀ có ít nhất 1 plan `isUnlocking` (header `Approved`/`In Progress` + dòng cuối là `[PLAN_APPROVED]`).
- Kiểm tra trạng thái mở khóa độc lập qua lệnh: `node .agents/scripts/plan-approval.js status`.

## 6. Pre-Flight Gate (Orchestrator tự kiểm trước khi bắt đầu code)

- [ ] **Gate 1, Plan Persistence**: plan đã được ghi đủ trong `docs/plans/`?
- [ ] **Gate 2, Reviewer Dispatch**: đã được Claude CLI hoặc `@reviewer` thẩm định?
- [ ] **Gate 3, Approval Token**: `node .agents/scripts/plan-approval.js status` báo `approved: true`?
- [ ] **Gate 4, Product Decisions**: các `[NEEDS_PRODUCT_DECISION]` đã được trình User?

Sau khi triển khai: Tier 2 (`02-quality.md`) và cập nhật docs (`06-docs.md`).

## 7. `@qa` (On-Demand)

- Bên thứ ba độc lập: nghiệm thu theo plan, Verification Gate fail-fast, săn lỗi, Test Integrity Audit, đề xuất cải tiến.
- Chỉ chạy khi User gọi `@qa`. Kết quả là `[QA_PASSED]` hoặc `[QA_FAILED]`. `@qa` không tự sửa code hay kích hoạt revision loop.
