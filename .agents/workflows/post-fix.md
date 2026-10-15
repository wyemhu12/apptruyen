---
description: Sau khi sửa bug — verify, ghi bài học vào docs/lessons-learned.md, cập nhật changelog.
---

# /post-fix Workflow

Chạy sau MỖI lần sửa bug để tích lũy kinh nghiệm và đảm bảo chất lượng.

// turbo-all

## Các bước

1. **Verify**: xác nhận bản sửa chạy đúng.

   ```powershell
   .\gradlew.bat ktlintCheck; .\gradlew.bat testDebugUnitTest; .\gradlew.bat assembleDebug
   ```

2. **Ghi bài học**: thêm entry vào `docs/lessons-learned.md`, đặt dưới nhóm phù hợp (Scraper, Room, TTS, Compose/UI, Coroutines/Lifecycle, Build/Gradle):

   ```markdown
   ### YYYY-MM-DD: [Mô tả ngắn]

   - **Triệu chứng**: Lỗi biểu hiện thế nào
   - **Nguyên nhân gốc**: Vì sao xảy ra
   - **Cách sửa**: Đã thay đổi gì (file chính)
   - **Quy tắc phòng ngừa**: Làm sao tránh lặp lại
   ```

3. **Nâng cấp thành rule**: nếu cùng một loại lỗi xuất hiện từ 3 lần trở lên:
   - Kotlin/coroutines/test → `.agents/rules/01-coding.md`
   - Compose/UI → `.agents/rules/03-ui.md`
   - Scraper/nguồn truyện → `.agents/rules/04-scraping.md`
   - Quy trình/verify → `.agents/rules/02-quality.md`

4. **Cập nhật changelog**: thêm vào `docs/changelog.md` dưới `## [Unreleased]` → `### Fix bug`. Nếu sửa selector thì cập nhật cả `docs/scraping.md`.

5. **Xác nhận**: kết thúc bằng câu:
   > Đã ghi bài học vào `docs/lessons-learned.md`: [mô tả 1 dòng]
