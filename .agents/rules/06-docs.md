---
trigger: always_on
description: Quy tắc cập nhật documentation sau mọi thay đổi code (ma trận thay đổi → file docs) và template commit message.
---

# Quy tắc cập nhật documentation

Sau **MỌI** thay đổi code, AI agent PHẢI cập nhật các file docs tương ứng (hoặc chạy workflow `/update-docs` tại `.agents/workflows/update-docs.md`). Ma trận thay đổi:

## Ma trận: Khi nào cập nhật file nào

- **Thêm/xóa file** → architecture, changelog
- **Thay đổi database schema** → architecture, changelog
- **Thêm dependency mới** → architecture, changelog
- **Thay đổi kiến trúc** → architecture, changelog, contributing
- **Hoàn thành/thêm task** → progress, changelog
- **Sửa scraper/selectors** → changelog, scraping
- **Thay đổi URL patterns** → changelog, scraping
- **Thêm tính năng mới** → architecture, progress, changelog
- **Fix bug** → changelog, lessons-learned (theo `.agents/workflows/post-fix.md`)
- **Thay đổi coding convention** → contributing, `.agents/rules/01-coding.md`
- **Hoàn thành một plan** → `docs/plans/README.md` (trạng thái), header plan `Trạng thái: Completed`

## Chi tiết cập nhật từng file

### `docs/architecture.md`

- Cập nhật "Cấu trúc thư mục" nếu thêm/xóa file
- Cập nhật "Database Schema" nếu thay đổi entities
- Cập nhật "Dependencies chính" nếu thêm dependency
- Cập nhật "Luồng dữ liệu" nếu thay đổi data flow
- **Luôn cập nhật timestamp "Cập nhật lần cuối"**

### `docs/progress.md`

- Di chuyển task từ "Chưa làm" → "Hoàn thành" khi xong
- Thêm task mới vào "Chưa làm" khi phát hiện cần làm thêm
- Cập nhật bảng "Lịch sử thay đổi" cuối file
- **Luôn cập nhật timestamp "Cập nhật lần cuối"**

### `docs/changelog.md`

- Thêm entry mới dưới `## [Unreleased]`
- Format: `### Thêm mới`, `### Sửa đổi`, `### Xóa bỏ`, `### Fix bug`
- Khi release version mới, chuyển entries từ Unreleased sang section version mới

### `docs/scraping.md`

- Cập nhật "CSS Selectors" nếu thay đổi selectors
- Cập nhật "URL Patterns" nếu phát hiện URL mới
- Thêm notes về edge cases mới

### `docs/contributing.md`

- Cập nhật nếu thay đổi coding conventions
- Cập nhật "Quy trình thêm tính năng mới" nếu workflow thay đổi

### `docs/lessons-learned.md`

- Thêm entry sau mỗi bug fix: Triệu chứng / Nguyên nhân gốc / Cách sửa / Quy tắc phòng ngừa

## Template cho commit message

```text
[TYPE] Mô tả ngắn

Docs updated:
- architecture.md: [mô tả thay đổi]
- progress.md: [task đã hoàn thành]
- changelog.md: [entries đã thêm]
```

TYPE: feat | fix | refactor | docs | chore
