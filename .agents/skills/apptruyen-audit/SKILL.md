---
name: apptruyen-audit
description: "BẮT BUỘC dùng khi rà soát chất lượng code, ranh giới Clean Architecture, chuẩn Room 3/Scraper, độ ổn định Compose, hoặc tuân thủ 100% UI tiếng Việt (/audit) trên một file hoặc cụm tính năng của AppTruyen."
---

# AppTruyen Code Quality & Architecture Audit (`/audit`)

Sử dụng kỹ năng này để rà soát tập trung chất lượng code, ranh giới kiến trúc, chuẩn dữ liệu Room 3 / Scraper, hiệu năng Jetpack Compose và tính tuân thủ 100% giao diện tiếng Việt trên một file hoặc cụm tính năng của **AppTruyen**.

## Các Bước Rà Soát (Audit Steps)

1. **Xác định phạm vi (Identify scope)** — Xác định rõ các file hoặc cụm tính năng cần kiểm toán (`ui/<feature>/`, `data/repository/`, `data/remote/`, `data/local/`, `tts/`, `download/`, `util/`).

2. **Kiểm tra chuẩn Kotlin & Ranh giới Kiến trúc** (theo `.agents/rules/00-core.md` và `.agents/rules/01-coding.md`):
   - Tuân thủ luồng Clean Architecture + MVVM (riêng `ReaderScreen` dùng MVI): `Screen` → `ViewModel` → `Repository` → (`Scraper` | `Room`).
   - Composable không gọi trực tiếp scraper hay DAO; `ViewModel` không phụ thuộc `android.content.Context` (trừ TTS).
   - Không xuất hiện toán tử `!!` mới, không dùng `GlobalScope` hay `runBlocking` trong `app/src/main/`, không dùng `Thread.sleep`.
   - Không có khối `catch` chỉ ghi log rồi nuốt lỗi im lặng: mọi hành động do người dùng kích hoạt khi thất bại phải ghi log bằng `Timber` và cập nhật trạng thái lỗi tiếng Việt lên UI; `CancellationException` bắt buộc phải được ném lại (`throw e`).
   - Không thực hiện gọi mạng, phân tích HTML (`Jsoup`) hoặc truy vấn DB trên main thread.
   - Kích thước file nằm trong ngưỡng chuẩn (`Screen`/`Scraper` ≤ 600 dòng, `ViewModel`/`Repository`/`util` ≤ 450 dòng, hàm < 50 dòng).

3. **Kiểm tra tầng Scraper & Nguồn truyện** (nếu thuộc `data/remote/`, theo `.agents/rules/04-scraping.md`):
   - Kế thừa `BaseScraper`, định nghĩa `sourceName` và `baseUrl` rõ ràng; toàn bộ hằng số selector/URL/timeout đặt trong `companion object`.
   - Mọi lỗi mạng/HTTP/parse đều chuyển qua `ScraperException` và ghi nhận vào `ScraperCircuitBreaker`.
   - Không trả về danh sách chương rỗng hoặc nội dung chương rỗng như thể thành công khi selector bị gãy (phải ném `ScraperException.ParseError`).
   - Không ghi log chứa cookie, token hoặc dữ liệu phiên đăng nhập nhạy cảm.

4. **Kiểm tra tầng Database & Room 3** (nếu thuộc `data/local/`, theo `docs/architecture.md`):
   - Danh sách chương dùng truy vấn chiếu cột nhẹ (`SELECT ... NULL AS content ...`) thay vì tải toàn bộ `content` của hàng nghìn chương vào bộ nhớ.
   - Các thao tác ghi nhiều bước trong Repository sử dụng `database.withWriteTransaction { ... }`.
   - Mọi thay đổi trên `@Entity` đều có `Migration(X, Y)` tường minh, cập nhật `version` trong `AppDatabase.kt` và xuất schema JSON vào `app/schemas/`.

5. **Kiểm tra chuẩn Jetpack Compose & Giao diện 100% Tiếng Việt** (nếu thuộc `ui/`, theo `.agents/rules/03-ui.md`):
   - 100% chuỗi hiển thị cho người dùng (tiêu đề, nút bấm, nhãn `contentDescription`, `Snackbar`, `AlertDialog`, thông báo lỗi) phải bằng tiếng Việt tự nhiên; tuyệt đối không hiển thị `e.message` thô bằng tiếng Anh.
   - Sử dụng màu sắc và kiểu chữ từ `MaterialTheme` / `ui/theme/`, không hardcode mã màu tùy tiện trong màn hình.
   - `LazyColumn` / `LazyVerticalGrid` truyền `key` ổn định và sử dụng `ImmutableList` trong `UiState`; thu thập state bằng `collectAsStateWithLifecycle()`.

6. **Kiểm tra Stale Patterns, Dead Code & Rò rỉ Tài nguyên**:
   - Kiểm tra API đã deprecated, import thừa, hàm/biến không còn nơi gọi (xem `.agents/skills/apptruyen-simplify/SKILL.md`), hoặc lệnh `android.util.Log` sót lại (phải dùng `Timber`, trừ `CrashHandler`).
   - Kiểm tra việc đóng `OkHttp Response` (`use {}`) và giải phóng `TextToSpeech`, `MediaSession`, `WebView`, coroutine `Job` trong `onCleared()` / `onDestroy()`.

7. **Chạy Kiểm Chứng (Verification)**:

   ```powershell
   .\gradlew.bat ktlintCheck; .\gradlew.bat testDebugUnitTest; .\gradlew.bat assembleDebug
   ```

8. **Báo cáo (Report)** — Tóm tắt kết quả theo mức độ nghiêm trọng (`[BLOCKER]`, `[MAJOR]`, `[MINOR]`) kèm dẫn chứng `file:line` (`[SRC]`) và hướng khắc phục cụ thể.

## Tiêu Chí Đạt (Exit Criteria — TẤT CẢ phải thỏa mãn)

- [ ] Không vi phạm ranh giới Clean Architecture (`Screen` → `ViewModel` → `Repository` → `Scraper`/`Room`)
- [ ] Không có toán tử `!!`, `GlobalScope`, hoặc khối `catch` nuốt lỗi im lặng trong phạm vi kiểm toán
- [ ] Mọi hành động người dùng khi thất bại đều cập nhật thông báo tiếng Việt lên UI (và rethrow `CancellationException`)
- [ ] 100% văn bản giao diện người dùng bằng tiếng Việt, không dùng `android.util.Log` trực tiếp
- [ ] Logic nghiệp vụ trong `repository/`, `remote/`, `tts/`, `util/`, `ViewModel` có unit test tương ứng trong `app/src/test/`
- [ ] `.\gradlew.bat ktlintCheck; .\gradlew.bat testDebugUnitTest; .\gradlew.bat assembleDebug` đạt 100% không lỗi
