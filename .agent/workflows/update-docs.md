---
description: Cập nhật documentation sau khi thay đổi code
---

# Cập nhật Documentation

// turbo-all

Workflow này tự động cập nhật các file docs sau khi thay đổi code, theo quy tắc trong `.agent/rules/update-docs.md`.

## Bước 1: Xác định loại thay đổi

Xem xét code vừa thay đổi và xác định loại thay đổi (thêm file, fix bug, thêm tính năng, thay đổi DB schema, v.v.)

## Bước 2: Xác định docs cần cập nhật

Dựa theo ma trận trong `.agent/rules/update-docs.md`, xác định file docs nào cần update:

- **Thêm/xóa file** → architecture, changelog
- **Thay đổi database schema** → architecture, changelog
- **Thêm dependency mới** → architecture, changelog
- **Thay đổi kiến trúc** → architecture, changelog, contributing
- **Hoàn thành/thêm task** → progress, changelog
- **Sửa scraper/selectors** → changelog, scraping
- **Thay đổi URL patterns** → changelog, scraping
- **Thêm tính năng mới** → architecture, progress, changelog
- **Fix bug** → changelog
- **Thay đổi coding convention** → contributing

## Bước 3: Cập nhật `docs/changelog.md`

Thêm entry mới dưới `## [Unreleased]` với format: `### Thêm mới`, `### Sửa đổi`, `### Xóa bỏ`, `### Fix bug`

## Bước 4: Cập nhật `docs/architecture.md` (nếu cần)

- Cập nhật "Cấu trúc thư mục" nếu thêm/xóa file
- Cập nhật "Database Schema" nếu thay đổi entities
- Cập nhật "Dependencies chính" nếu thêm dependency
- Cập nhật "Luồng dữ liệu" nếu thay đổi data flow
- **Luôn cập nhật timestamp "Cập nhật lần cuối"**

## Bước 5: Cập nhật `docs/progress.md` (nếu cần)

- Di chuyển task từ "Chưa làm" → "Hoàn thành" khi xong
- Thêm task mới vào "Chưa làm" khi phát hiện cần làm thêm
- **Luôn cập nhật timestamp "Cập nhật lần cuối"**

## Bước 6: Cập nhật các docs khác (nếu cần)

- `docs/scraping.md` — nếu thay đổi selectors hoặc URL patterns
- `docs/contributing.md` — nếu thay đổi coding conventions

## Bước 7: Verify

Kiểm tra lại tất cả docs đã cập nhật có chính xác và nhất quán với nhau.
