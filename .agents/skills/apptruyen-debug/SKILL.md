---
name: apptruyen-debug
description: "BẮT BUỘC dùng khi điều tra lỗi phức tạp hoặc tái diễn (/debug) và khi thực thi quy trình xác minh, ghi bài học sau khi sửa lỗi (/post-fix) trong AppTruyen."
---

# AppTruyen Systematic Debugging & Post-Fix Protocol (`/debug` & `/post-fix`)

Sử dụng kỹ năng này cho các lỗi phức tạp hoặc tái diễn (`/debug`) và sau mỗi lần sửa lỗi (`/post-fix`). Nguyên tắc cốt lõi: **không sửa code khi chưa xác nhận nguyên nhân gốc (root cause)**.

---

## Part 1: Systematic Debugging Protocol (`/debug`)

### Phase 1: Chuẩn Bị (Preparation)

1. Đọc `docs/lessons-learned.md` để kiểm tra xem mẫu lỗi này đã từng xuất hiện trước đây chưa.
2. Đọc tài liệu theo miền (domain): `docs/scraping.md` (nguồn truyện), `docs/architecture.md` (Room 3, luồng dữ liệu, TTS/Media3).

### Phase 2: Điều Tra Nguyên Nhân Gốc (Root Cause Investigation)

> **Chưa đề xuất sửa code. Thu thập bằng chứng trước.**

1. Phân tích stack trace và log: `crash.log` ở thư mục gốc, log `Timber`/Logcat, hoặc output kiểm thử (`.\gradlew.bat testDebugUnitTest 2>&1 | Tee-Object -FilePath build\test-output.txt`). Xác định chính xác đường dẫn file và số dòng (`file:line`).
2. Tái hiện lỗi:
   - **Lỗi nguồn truyện (Scraper)**: Lấy HTML thực tế bằng `curl.exe -q -s <url> | Select-String "<selector>"` (tuân thủ `.agents/agents/reviewer/allowlist.md`) và đối chiếu với selector trong scraper.
   - **Lỗi logic / parser / repository / ViewModel**: Viết unit test tái hiện lỗi trong `app/src/test/` (test phải fail trước khi sửa).
   - **Lỗi UI / TTS / Lifecycle**: Ghi rõ các bước tái hiện trên thiết bị (xoay màn hình, tắt mạng, thu nhỏ ứng dụng, chuyển chương khi đang phát TTS...).
3. Nếu lỗi đi qua nhiều tầng (`Screen` → `ViewModel` → `Repository` → `Scraper` / `Room` / `TTS`): thêm log `Timber` tạm ở ranh giới các tầng để lần theo luồng dữ liệu.

**CHECKPOINT**: Đã xác định được nguyên nhân gốc?

- **CÓ** → chuyển sang Phase 3
- **CHƯA** → quay lại Phase 2 (tối đa 2 lần thử lại, sau đó chuyển sang Circuit Breaker)

### Phase 3: Phân Tích Mẫu (Pattern Analysis)

1. Tìm một ví dụ đang hoạt động đúng của cùng mẫu thiết kế trong codebase (ví dụ một scraper khác kế thừa `BaseScraper`, một `ViewModel` khác, hoặc một truy vấn DAO tương đương).
2. Liệt kê các điểm khác biệt giữa đoạn code chạy đúng và đoạn code đang lỗi.

### Phase 4: Giả Thuyết (Hypothesis)

1. Phát biểu rõ ràng: `"X bị lỗi vì Y."`
2. Kiểm chứng bằng một bài test tối thiểu (unit test với HTML fixture / `MockWebServer` / `runTest`) để chứng minh giả thuyết.

**CHECKPOINT**: Giả thuyết đã được xác nhận bằng bài test tối thiểu?

- **CÓ** → chuyển sang Phase 5
- **CHƯA** → quay lại Phase 3 (tối đa 2 lần thử lại, sau đó chuyển sang Circuit Breaker)

### Phase 5: Triển Khai Bản Sửa (Implementation)

> **Chỉ thực hiện khi Phase 4 đã vượt qua.**

1. Nếu lỗi không tầm thường (chạm nhiều file, thay đổi schema Room, sửa kiến trúc), bắt buộc lập kế hoạch qua `@planner` → Reviewer → `[PLAN_APPROVED]` theo `.agents/rules/05-plan-review.md`.
2. Thực hiện một bản sửa tối giản duy nhất, gỡ bỏ log tạm, và thêm/cập nhật unit test tương ứng trong `app/src/test/`.
3. Chạy kiểm chứng Tier 2:
   ```powershell
   .\gradlew.bat ktlintCheck; .\gradlew.bat testDebugUnitTest; .\gradlew.bat assembleDebug
   ```
4. Thực thi ngay **Part 2: Post-Fix Protocol (`/post-fix`)** bên dưới để ghi nhận bài học.

### Circuit Breaker (Quy Tắc 3 Lần Thất Bại)

> Nếu 3 lần thử sửa liên tiếp vẫn thất bại: **DỪNG LẠI NGAY**. Báo cáo:
> `"CẢNH BÁO KIẾN TRÚC: 3 lần sửa thất bại. Chúng ta đang chữa triệu chứng, không phải nguyên nhân gốc. Đề xuất thảo luận refactor."`

---

## Part 2: Post-Fix Protocol (`/post-fix`)

Chạy quy trình này sau **mọi** lần sửa lỗi để tích lũy bộ nhớ tổ chức và đảm bảo chất lượng:

1. **Chạy kiểm chứng (Verify)** — Xác nhận bản sửa hoạt động đúng trên toàn bộ bộ kiểm thử:

   ```powershell
   .\gradlew.bat ktlintCheck; .\gradlew.bat testDebugUnitTest; .\gradlew.bat assembleDebug
   ```

2. **Ghi nhận bài học (Record the lesson)** — Thêm mục mới vào `docs/lessons-learned.md` dưới nhóm phù hợp (`Scraper`, `Room`, `TTS`, `Compose/UI`, `Coroutines/Lifecycle`, `Build/Gradle`, `Governance/Hook`):

   ```markdown
   ### YYYY-MM-DD: [Mô tả ngắn]

   - **Triệu chứng**: Lỗi biểu hiện như thế nào
   - **Nguyên nhân gốc**: Vì sao lỗi xảy ra
   - **Cách sửa**: Đã thay đổi gì (kèm file chính)
   - **Quy tắc phòng ngừa**: Làm sao tránh lặp lại trong tương lai
   ```

3. **Kiểm tra nâng cấp thành Rule (Pattern Promotion)** — Nếu cùng một nhóm lỗi xuất hiện từ 3 lần trở lên, trích xuất thành quy tắc chính thức trong `.agents/rules/`:
   - Lỗi Kotlin / Coroutines / Unit test → `.agents/rules/01-coding.md`
   - Lỗi Compose / Material 3 / UI tiếng Việt → `.agents/rules/03-ui.md`
   - Lỗi Scraper / Nguồn truyện / Selector → `.agents/rules/04-scraping.md`
   - Lỗi Quy trình / Quality Gate → `.agents/rules/02-quality.md`

4. **Cập nhật Changelog & Docs** — Thêm mục sửa lỗi vào `docs/changelog.md` dưới `## [Unreleased]` → `### Fix bug`. Nếu thay đổi selector hoặc URL nguồn truyện thì cập nhật thêm `docs/scraping.md`.

5. **Xác nhận hoàn tất** — Kết thúc báo cáo bằng câu:
   > `Đã ghi bài học vào docs/lessons-learned.md: [mô tả 1 dòng]`
