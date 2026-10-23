---
trigger: always_on
description: Quy tắc cập nhật documentation sau mọi thay đổi code (ma trận thay đổi → file docs) và template commit message.
---

# Quy tắc cập nhật documentation

Sau **MỌI** thay đổi code, AI agent PHẢI cập nhật các file docs tương ứng theo ma trận dưới đây:

## Ma trận: Khi nào cập nhật file nào

- **Thêm/xóa file** → `docs/architecture.md`, `docs/changelog.md`
- **Thay đổi database schema** → `docs/architecture.md`, `docs/changelog.md`
- **Thêm dependency mới** → `docs/architecture.md`, `docs/changelog.md`
- **Thay đổi kiến trúc** → `docs/architecture.md`, `docs/changelog.md`, `docs/contributing.md`
- **Hoàn thành/thêm task** → `docs/progress.md`, `docs/changelog.md`
- **Sửa scraper/selectors hoặc URL patterns** → `docs/changelog.md`, `docs/scraping.md`
- **Thêm tính năng mới** → `docs/architecture.md`, `docs/progress.md`, `docs/changelog.md`
- **Fix bug** → `docs/changelog.md`, `docs/lessons-learned.md` (theo `.agents/skills/apptruyen-debug/SKILL.md` §Part 2)
- **Thay đổi coding convention** → `docs/contributing.md`, `.agents/rules/01-coding.md`
- **Hoàn thành một plan** → `docs/plans/README.md` (bảng trạng thái), header plan `> **Trạng thái**: Completed`

## Chi tiết cập nhật từng file

- **`docs/architecture.md`**: cập nhật "Cấu trúc thư mục", "Database Schema", "Dependencies chính", "Luồng dữ liệu" và timestamp "Cập nhật lần cuối".
- **`docs/progress.md`**: chuyển task từ "Chưa làm" → "Hoàn thành", cập nhật bảng "Lịch sử thay đổi" và timestamp "Cập nhật lần cuối".
- **`docs/changelog.md`**: thêm entry dưới `## [Unreleased]` (`### Thêm mới`, `### Sửa đổi`, `### Xóa bỏ`, `### Fix bug`). Khi release version mới, chuyển sang section version mới.
- **`docs/scraping.md`**: cập nhật "CSS Selectors", "URL Patterns" và ghi chú edge cases mới.
- **`docs/lessons-learned.md`**: thêm entry sau mỗi bug fix (Triệu chứng / Nguyên nhân gốc / Cách sửa / Quy tắc phòng ngừa).

## Template cho commit message

```text
[TYPE] Mô tả ngắn

Docs updated:
- architecture.md: [mô tả thay đổi]
- progress.md: [task đã hoàn thành]
- changelog.md: [entries đã thêm]
```

TYPE: `feat` | `fix` | `refactor` | `docs` | `chore`
