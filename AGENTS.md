# AGENTS.md — AppTruyen Project Context

Đây là ứng dụng Android cá nhân đọc truyện từ truyencom.com, viết bằng Kotlin + Jetpack Compose.

## Quy tắc quan trọng

1. **UI 100% tiếng Việt** — Mọi text hiện cho user phải bằng tiếng Việt
2. **MVVM + Clean Architecture** — Screen → ViewModel → Repository → (Scraper | Room)
3. **Jetpack Compose + Material 3** — Không dùng XML layout
4. **Coroutines + Flow** — Không dùng callbacks hay RxJava

## Tài liệu bắt buộc đọc trước khi thay đổi code

| File | Khi nào đọc |
|---|---|
| `docs/architecture.md` | Khi cần hiểu cấu trúc project |
| `docs/progress.md` | Khi cần biết tính năng nào đã/chưa làm |
| `docs/changelog.md` | Khi cần biết lịch sử thay đổi |
| `docs/scraping.md` | Khi sửa TruyenComScraper |
| `docs/contributing.md` | Khi thêm tính năng mới |

## Tài liệu bắt buộc cập nhật sau khi thay đổi code

Xem chi tiết trong `.agent/rules/update-docs.md`
