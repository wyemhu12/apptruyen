---
trigger: always_on
description: Pre-work protocol, verification tiers, test failure triage, post-fix và debugging protocol, skill precedence, governance, minimal diffs.
---

# Quality Gates (AppTruyen)

## Pre-Work Protocol (BẮT BUỘC)

Trước khi viết code, xác định domain của task và đọc tài liệu / `SKILL.md` tương ứng:

| Domain | Đọc trước | Quy tắc / Skill bắt buộc |
| :--- | :--- | :--- |
| Scraper / nguồn truyện | `docs/scraping.md`, `.agents/rules/04-scraping.md` | `.agents/rules/04-scraping.md` |
| Database / Room schema | `docs/architecture.md` (Database Schema), `data/local/AppDatabase.kt` | `.agents/skills/apptruyen-investigate/SKILL.md` |
| TTS / MediaSession | `docs/architecture.md`, `tts/` | `.agents/skills/apptruyen-investigate/SKILL.md` |
| UI / Compose | `.agents/rules/03-ui.md` | `.agents/rules/03-ui.md` |
| Tính năng mới / Lập plan | `docs/architecture.md`, `docs/progress.md`, `docs/plans/README.md` | **.agents/rules/05-plan-review.md** |
| Sửa bug (`/debug`, `/post-fix`) | `docs/lessons-learned.md` | **.agents/skills/apptruyen-debug/SKILL.md** |
| Rà soát chất lượng (`/audit`) | `.agents/rules/01-coding.md` | **.agents/skills/apptruyen-audit/SKILL.md** |
| Điều tra / Blast-radius | `.agents/rules/00-core.md` | **.agents/skills/apptruyen-investigate/SKILL.md** |
| Tinh giản / xóa dead code | `.agents/rules/01-coding.md` (Kích thước file) | **.agents/skills/apptruyen-simplify/SKILL.md** |
| Sau khi đổi code | `.agents/rules/06-docs.md` | **.agents/rules/06-docs.md** |

<important>
Skill hoặc rule trong bảng là bắt buộc, PHẢI đọc bằng `view_file`/`read_file` và làm theo từng bước.
Task không tầm thường: code freeze áp dụng cho đến khi có `[PLAN_APPROVED]` (`05-plan-review.md`). Task tầm thường không cần plan, nhưng hook vẫn hỏi User khi ghi vào `app/`.
</important>

## Skill Precedence Hierarchy (AppTruyen-First, Superpowers-Complementary)

1. **Ưu tiên 1 — Quy tắc & Skills nội bộ AppTruyen**: `.agents/rules/00-core.md` … `06-docs.md`, `.agents/agents/{planner,reviewer,qa}/agent.md`, và `.agents/skills/apptruyen-{debug,audit,investigate,simplify}/SKILL.md` là nguồn sự thật tối cao.
2. **Ưu tiên 2 — Plugin `superpowers` chỉ bổ trợ tư duy**: TUYỆT ĐỐI KHÔNG để các skill của `superpowers` ghi đè quy tắc của AppTruyen:
   - Không chạy lệnh git mutating (`git worktree`, `branch`, `checkout`, `commit`, `merge`) theo `using-git-worktrees` / `finishing-a-development-branch` (`00-core.md`).
   - Mọi kế hoạch phải lưu tại `docs/plans/YYYY-MM-DD-<task>-implementation-plan.md` và đi qua `@planner` → Reviewer → `[PLAN_APPROVED]` (`05-plan-review.md`).

## Post-Change Checklist (BẮT BUỘC)

| Tier | Khi nào | Lệnh | Mục đích |
| :---: | :--- | :--- | :--- |
| **1** | Sau mỗi cụm sửa | `.\gradlew.bat compileDebugKotlin` | Bắt lỗi biên dịch sớm |
| **2** | Trước khi báo xong | `.\gradlew.bat ktlintCheck; .\gradlew.bat testDebugUnitTest; .\gradlew.bat assembleDebug` | Quality gate đầy đủ |
| **3** | Trước merge/release | CI `.github/workflows/ci.yml` (ktlint → lint → test → assembleDebug) | Xác minh trên môi trường sạch |

- Chạy Tier 1 sau mỗi cụm sửa, Tier 2 trước khi tuyên bố xong. Đổi logic phải thêm/cập nhật test TRƯỚC khi chạy Tier 2, rồi cập nhật docs (`06-docs.md`).

## Test Failure Triage Protocol

- **Loại A (Code Bug)**: code ứng dụng sai so với đặc tả → **SỬA CODE**, không đụng vào test.
- **Loại B (Test Harness)**: mock/fixture/dispatcher/import lỗi thời do refactor, đặc tả không đổi → được sửa harness/mock, KHÔNG giảm số assert và KHÔNG nới matcher.
- **Loại C (Approved Spec Change)**: đặc tả đổi theo Test Contract trong plan đã có `[PLAN_APPROVED]` → được cập nhật test cho khớp Test Contract mới.

<important>
NGHIÊM CẤM HẠ THẤP TIÊU CHUẨN TEST: xóa `assert*`/`verify {}`/`coVerify {}`, comment-out test, nới matcher (`assertEquals` → `assertNotNull`/`assertTrue`, khớp chính xác → `any()`), thêm `@Disabled`/`@Ignore`/`assumeTrue`, hoặc bọc assert trong `runCatching`/`try` khi không có căn cứ Loại C.
</important>

## Phân Định Skills Nội Bộ, Governance & Minimal Diffs

- **Skills nội bộ**: `apptruyen-audit` (`/audit`), `apptruyen-debug` (`/debug` 6 bước, dừng sau 3 lần thất bại + `/post-fix` 5 bước bắt buộc ghi `docs/lessons-learned.md` và `docs/changelog.md`), `apptruyen-investigate` (`/how`, `/why`, `/recall`, `/blast-radius`), `apptruyen-simplify` (`/simplify`). `@qa` chỉ chạy khi User gọi.
- **Governance-Only Changes**: file trong `.agents/rules/`, `.agents/agents/`, `.agents/skills/` phải có YAML frontmatter chuẩn; rule `<= 12.000` ký tự/file, tổng các file `always_on < 18.000` ký tự, agent `<= 10.000` ký tự/file. Sửa `.agents/scripts/` phải chạy `node --test .agents/scripts/*.test.js`.
- **Minimal Diffs**: chỉ sửa những gì cần, theo style của file đang sửa, không format lại vùng code không đổi logic.
