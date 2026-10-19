---
description: Rà soát nhanh chất lượng code cho một file hoặc cụm tính năng trong AppTruyen.
---

# /audit Workflow

Dùng để rà soát chất lượng code tập trung cho một file hoặc cụm tính năng trong **AppTruyen** (Android, Kotlin, Jetpack Compose).

## Các bước

1. **Xác định phạm vi** — File hoặc cụm tính năng nào cần audit?

2. **Kiểm tra chuẩn Kotlin & Kiến trúc** (theo `.agents/rules/00-core.md` và `.agents/rules/01-coding.md`):
   - Giữ đúng ranh giới Clean Architecture + MVVM (MVI cho `ReaderScreen`): `Screen` → `ViewModel` → `Repository` → (`Scraper` | `Room`).
   - Không có `!!` mới, không dùng `GlobalScope` hoặc `runBlocking` ở main source set, không dùng `Thread.sleep`.
   - Không có catch block nuốt lỗi im lặng (bắt buộc log bằng `Timber` và báo lỗi tiếng Việt lên UI state cho hành động người dùng kích hoạt; ném lại `CancellationException`).
   - Không gọi mạng/parse HTML/truy vấn DB trên main thread.
   - Kích thước file trong ngưỡng khuyến nghị (Screen/Scraper ≤ 600 dòng, ViewModel/Repository ≤ 450 dòng).

3. **Kiểm tra Scraper / Room** (nếu thuộc tầng `data/`):
   - Scraper kế thừa `BaseScraper`, lỗi đi qua `ScraperException` và `ScraperCircuitBreaker`, không trả về chương rỗng như thể thành công (`.agents/rules/04-scraping.md`).
   - Thay đổi Room entity có `Migration` + schema JSON đầy đủ, không dùng destructive migration cho version ≥ 5.

4. **Kiểm tra chuẩn UI & Tiếng Việt** (nếu thuộc tầng `ui/`, theo `.agents/rules/03-ui.md`):
   - 100% văn bản hiển thị và thông báo lỗi bằng tiếng Việt (không lộ chuỗi tiếng Anh hay raw exception message).
   - Dùng token màu/typography từ `MaterialTheme` và `ui/theme/`, không hardcode màu tùy tiện.
   - `LazyColumn`/`LazyVerticalGrid` có `key` ổn định, dùng `ImmutableList`.

5. **Kiểm tra stale patterns & rò rỉ tài nguyên**:
   - API deprecated, import thừa, `android.util.Log` sót lại (phải dùng `Timber`).
   - OkHttp `Response` được đóng (`use {}`), `TextToSpeech`/`MediaSession`/`WebView`/coroutine `Job` được giải phóng đúng vòng đời.

6. **Chạy kiểm chứng (Verification)**:

   ```powershell
   .\gradlew.bat ktlintCheck; .\gradlew.bat testDebugUnitTest; .\gradlew.bat assembleDebug
   ```

7. **Báo cáo** — Tóm tắt phát hiện theo mức độ (`[BLOCKER]`, `[MAJOR]`, `[MINOR]`) kèm `file:line` và hướng xử lý.

## Tiêu chí đạt (Exit Criteria — TẤT CẢ phải đạt)

- [ ] Không vi phạm ranh giới kiến trúc, không có `!!` hay `GlobalScope` mới
- [ ] Mọi hành động do người dùng kích hoạt khi lỗi đều cập nhật thông báo tiếng Việt trên UI
- [ ] 100% văn bản giao diện người dùng bằng tiếng Việt
- [ ] Không dùng `android.util.Log` trực tiếp (chỉ dùng `Timber`, trừ `CrashHandler`)
- [ ] Logic nghiệp vụ trong `repository/`, `remote/`, `tts/`, `util/`, `ViewModel` có unit test tương ứng
- [ ] `.\gradlew.bat ktlintCheck; .\gradlew.bat testDebugUnitTest; .\gradlew.bat assembleDebug` pass 100%
