---
description: Quy trình debug có hệ thống. Không sửa khi chưa xác nhận root cause.
---

# /debug Workflow

Dùng cho bug phức tạp hoặc tái diễn. Nguyên tắc cốt lõi: không sửa khi chưa xác nhận root cause.

## Phase 1: Chuẩn bị

1. Đọc `docs/lessons-learned.md` để xem pattern lỗi này đã từng gặp chưa.
2. Đọc docs theo domain: `docs/scraping.md` (nguồn truyện), `docs/architecture.md` (Room/luồng dữ liệu), `docs/scraping_lacatruyen_decryption.md` (giải mã).

## Phase 2: Điều tra Root Cause

> **Chưa đề xuất sửa code. Thu thập bằng chứng trước.**

1. Phân tích stack trace: `crash.log` ở root, log Timber/Logcat, output test (`.\gradlew.bat testDebugUnitTest 2>&1 | Tee-Object -FilePath build\test-output.txt`). Xác định file và dòng.
2. Tái hiện lỗi:
   - Lỗi nguồn truyện: lấy HTML thực tế bằng `read_url_content` hoặc `curl.exe -sL <url> | Select-String "<selector>"` và so với selector trong scraper.
   - Lỗi logic: viết unit test tái hiện lỗi (test này fail trước khi sửa).
   - Lỗi UI/TTS/lifecycle: ghi rõ các bước để User tái hiện trên thiết bị (xoay màn hình, tắt mạng, kill app...).
3. Nếu lỗi đi qua nhiều tầng (Screen → ViewModel → Repository → Scraper/Room): thêm log Timber tạm ở ranh giới các tầng để lần theo dữ liệu.

**CHECKPOINT**: Đã xác định root cause?

- **CÓ** → Phase 3
- **CHƯA** → quay lại Phase 2 (tối đa 2 lần, sau đó sang Circuit Breaker)

## Phase 3: Phân tích Pattern

1. Tìm một ví dụ đang chạy đúng của cùng pattern trong codebase (ví dụ một scraper khác, một ViewModel khác).
2. Liệt kê điểm khác biệt giữa ví dụ đúng và code lỗi.

## Phase 4: Giả thuyết

1. Phát biểu: "X hỏng vì Y."
2. Kiểm chứng bằng test tối thiểu (unit test với fixture, hoặc giá trị hardcode tạm).

**CHECKPOINT**: Giả thuyết đã được xác nhận bằng test tối thiểu?

- **CÓ** → Phase 5
- **CHƯA** → quay lại Phase 3 (tối đa 2 lần, sau đó sang Circuit Breaker)

## Phase 5: Sửa

> **Chỉ làm khi Phase 4 đã đạt.**

1. Bug không tầm thường (đụng nhiều file, schema, kiến trúc) thì tạo plan qua `@planner` → Reviewer trước (`.agents/rules/05-plan-review.md`).
2. Thực hiện một bản sửa duy nhất, gỡ log tạm.
3. Chạy Tier 2: `.\gradlew.bat ktlintCheck; .\gradlew.bat testDebugUnitTest; .\gradlew.bat assembleDebug`.
4. Chạy workflow `/post-fix` để ghi bài học.

## Circuit Breaker

> Sửa 3 lần vẫn fail thì **DỪNG** và báo:
> "CẢNH BÁO KIẾN TRÚC: 3 lần sửa thất bại. Chúng ta đang chữa triệu chứng, không phải nguyên nhân gốc. Đề xuất thảo luận refactor."
