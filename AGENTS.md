# AGENTS.md — AppTruyen Project Context

Đây là ứng dụng Android cá nhân đọc và nghe truyện chữ (TTS), tự động gộp dữ liệu từ 3 nguồn hoạt động (Tàng Thư Viện, SsTruyện, Wikidich), viết bằng Kotlin.

## Quy tắc quan trọng

1. **UI 100% tiếng Việt** — Mọi text hiện cho user phải bằng tiếng Việt
2. **Architecture** — Clean Architecture + MVVM (riêng ReaderScreen dùng MVI). Luồng: Screen → ViewModel → Repository → (Scraper | Room)
3. **Jetpack Compose + Material 3** — Không dùng XML layout
4. **Coroutines + Flow** — Không dùng callbacks hay RxJava
5. **Dependency Injection** — Dagger Hilt (KHÔNG dùng Koin hay manual DI)

## Tài liệu bắt buộc đọc trước khi thay đổi code

| File | Khi nào đọc |
|---|---|
| `docs/architecture.md` | Khi cần hiểu cấu trúc project |
| `docs/progress.md` | Khi cần biết tính năng nào đã/chưa làm |
| `docs/changelog.md` | Khi cần biết lịch sử thay đổi |
| `docs/scraping.md` | Khi sửa hoặc thêm các Scraper |
| `docs/contributing.md` | Khi thêm tính năng mới |
| `docs/lessons-learned.md` | Trước khi lập kế hoạch hoặc sửa bug |
| `docs/plans/` | Kế hoạch triển khai (template trong `docs/plans/README.md`) |

## Hệ thống quản trị agent (`.agents/`)

Adopt từ Project AURORA và Vikini. Tổng quan: `.agents/README.md`.

- **Rules**: `.agents/rules/00-core.md` … `06-docs.md` (core, coding, quality, UI, scraping, plan-review, docs)
- **Quy trình**: task không tầm thường đi theo `@planner` → Lead Reviewer (Claude CLI `.agents/scripts/run-claude.ps1`, dự phòng `@reviewer`) → `[PLAN_APPROVED]` → triển khai. Xem `.agents/rules/05-plan-review.md`
- **Subagents**: `.agents/agents/planner`, `reviewer`, `qa` (`@qa` chỉ chạy khi User gọi)
- **Code Freeze Guard**: hook `.agents/hooks.json` khóa code cho đến khi plan được duyệt, luôn hỏi khi sửa file nhạy cảm
- **Workflows**: `/update-docs`, `/debug`, `/post-fix` trong `.agents/workflows/`

## Tài liệu bắt buộc cập nhật sau khi thay đổi code

Xem chi tiết trong quy tắc `.agents/rules/06-docs.md` hoặc dùng workflow `/update-docs`.
