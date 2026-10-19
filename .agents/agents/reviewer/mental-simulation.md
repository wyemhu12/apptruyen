# Mental Simulation Protocol — 6 Kịch Bản Bắt Buộc (AppTruyen)

> Tài liệu tham chiếu bắt buộc của `@reviewer` và Claude CLI reviewer. Đọc bằng `view_file` ở Bước 0 trước mọi review.
> Đường dẫn rút gọn: `<pkg>` = `app/src/main/java/com/personal/apptruyen`.

## 6 Kịch Bản (IMPERATIVE FORMAT)

> **Nguyên tắc Sàn Tối Thiểu**: 6 kịch bản dưới đây là điều kiện tối thiểu, không phải giới hạn trên. Reviewer BẮT BUỘC chủ động truy quét thêm rủi ro nghiệp vụ (gộp nguồn, TTS, offline) và edge case đặc thù nằm ngoài 6 kịch bản này.

**S1: Cold-start & Build (Gradle / KSP / ktlint)**

- CHẠY `.\gradlew.bat compileDebugKotlin` làm baseline. Nếu plan chạm vào build config (`app/build.gradle.kts`, `.editorconfig`, CI) thì CHẠY thêm `.\gradlew.bat ktlintCheck` và áp dụng mục 4.5.3 của `evidence-bar.md`.
- MỞ `app/build.gradle.kts`. KIỂM TRA dependency mới: có thật sự cần không (plan phải giải thích), version đã được xác minh trong Verified Versions Table chưa, có xung đột với Compose BOM/Kotlin 2.x/KSP không, có cần rule R8 (`app/proguard-rules.pro`) cho release không.
- KIỂM TRA API dùng trong plan có tương thích `minSdk 26` không (nếu API mới hơn thì phải có `Build.VERSION.SDK_INT` guard).
- KIỂM TRA plan có import class chưa tồn tại không (đối chiếu với danh sách file `[NEW]`) và có đăng ký Hilt module/binding cho class mới không (`<pkg>/di/`).

**S2: Lifecycle & Teardown (Coroutines, Service, TTS, WebView)**

- TÌM trong plan mọi tài nguyên cần giải phóng: `TextToSpeech` (`<pkg>/tts/TtsManager.kt`), `MediaSession` và foreground service (`TtsService`, `<pkg>/download/DownloadService.kt`), `WebView` (`<pkg>/data/remote/WebViewContentLoader.kt`), OkHttp `Response` (phải dùng `use {}`), coroutine Job.
- KIỂM TRA scope: ViewModel dùng `viewModelScope`; service dùng scope có `SupervisorJob` và bị hủy trong `onDestroy`; CẤM `GlobalScope`. App-wide scope chỉ dùng `@ApplicationScope` (`<pkg>/di/ApplicationScope.kt`).
- KIỂM TRA Compose: collect Flow theo lifecycle, `LaunchedEffect`/`DisposableEffect` có key đúng và có dọn dẹp.
- KIỂM TRA test: `runTest` + `StandardTestDispatcher`, `Dispatchers.setMain/resetMain`, `MockWebServer` được shutdown, không có mock vòng tròn (mock trả đúng thứ mà assert kiểm tra).

**S3: Database & Temporal Logic (Room + DataStore)**

- MỞ `<pkg>/data/local/AppDatabase.kt` (hiện tại `version = 9`, `exportSchema = true`) và `<pkg>/di/AppModule.kt` (`fallbackToDestructiveMigrationFrom(1..4)` + `MIGRATION_5_6..8_9`).
- Đổi entity thì BẮT BUỘC: tăng version, viết `Migration` thủ công, đăng ký trong `AppModule`, export schema JSON mới vào `app/schemas/`, và có test migration hoặc lý do bằng văn bản. TUYỆT ĐỐI CẤM destructive migration cho version ≥ 5 vì sẽ làm mất thư viện truyện và tiến độ đọc.
- KIỂM TRA quan hệ `stories` → `chapters` / `reading_progress`: khi xóa truyện thì có `ON DELETE CASCADE` hoặc xóa thủ công trong `@Transaction` không, có để lại bản ghi mồ côi không.
- KIỂM TRA thời gian: lưu epoch millis (UTC), hiển thị theo múi giờ thiết bị; thống kê đọc theo ngày (`reading_stats`) phải xét ranh giới nửa đêm.
- KIỂM TRA DataStore (`<pkg>/data/local/SettingsDataStore.kt`): key mới có default, không đổi tên key cũ khiến người dùng mất cài đặt.
- BẮT BUỘC Adversarial Timeline (4.5.2) cho mọi ghi đồng thời: tải chương + đọc, TTS cập nhật tiến độ + Reader cập nhật tiến độ.

**S4: Cross-Task State Flow (Soi Code Thực Tế)**

- BẮT BUỘC dùng `view_file`/`grep_search` soi trực tiếp state hiện có: UI state của ViewModel (`StateFlow`), MVI của `<pkg>/ui/reader/ReaderViewModel.kt` (intent → reducer → state), route/argument trong `<pkg>/ui/navigation/AppNavigation.kt`, interface repository (`<pkg>/data/repository/I*Repository.kt`).
- KIỂM TRA plan có phá vỡ chữ ký interface đang được ViewModel/test khác dùng không; fake/mock trong test có cần cập nhật không.
- KIỂM TRA nguồn sự thật duy nhất: Repository là single source of truth, UI không tự cache song song.
- KHÔNG chỉ đọc bản kế hoạch, phải kiểm tra source code thực tế.

**S5: Security & Privacy Boundary**

- KIỂM TRA cookie/phiên đăng nhập nguồn truyện (`<pkg>/data/remote/cookie/`, `auth/`): không log cookie/token qua Timber, không đưa vào crash log (`<pkg>/util/CrashHandler.kt`), không đưa vào file backup JSON (`BackupData`).
- KIỂM TRA `AndroidManifest.xml`: component mới có `exported` đúng mức tối thiểu; intent nhận từ ngoài phải được validate (xem skill `android-intent-security`). Đảm bảo giữ nguyên `usesCleartextTraffic="false"`; nếu plan thêm dữ liệu nhạy cảm thì phải đánh giá lại `allowBackup="true"` và `backup_rules.xml`.
- KIỂM TRA WebView: bật JavaScript thì có `addJavascriptInterface` không; có tải URL ngoài domain nguồn không.
- KIỂM TRA giải mã nội dung (nếu có; lưu ý `CryptoJS.kt` của nguồn cũ LacaTruyen đã được gỡ bỏ khỏi codebase): key/IV không hardcode lan man, lỗi giải mã không làm crash app.
- BẮT BUỘC Adversarial Timeline cho TOCTOU: cookie hết hạn giữa chừng, redirect sang trang đăng nhập/Cloudflare bị parse nhầm thành nội dung chương.

**S6: External Resilience (Nguồn Truyện, Mạng, TTS Engine)**

- XỬ LÝ lỗi nguồn: HTTP 403/429/5xx, Cloudflare challenge, HTML đổi cấu trúc (selector trả rỗng), encoding sai. Đi qua `ScraperException` (`<pkg>/data/remote/ScraperException.kt`) và `ScraperCircuitBreaker`. KHÔNG trả về chương rỗng như thể thành công.
- THIẾT LẬP timeout OkHttp, giới hạn retry/backoff, không spam request khi tải hàng loạt (tôn trọng nguồn).
- FALLBACK giữa các nguồn khi một nguồn lỗi; thông báo lỗi tiếng Việt rõ ràng qua UI state/Snackbar; offline thì dùng chương đã tải.
- TTS: engine không có giọng tiếng Việt, `onInit` lỗi, văn bản vượt `getMaxSpeechInputLength()` (cắt câu trong `TtsTextPreprocessor`).
- Test bằng `MockWebServer` hoặc fixture HTML, KHÔNG gọi mạng thật trong unit test.

**Domain: Open Inquiry (AppTruyen)**

- _Gộp đa nguồn_: khử trùng lặp truyện giữa các nguồn (so tên/tác giả đã chuẩn hóa dấu), căn số chương lệch giữa nguồn, `sourceId` trên `stories`.
- _Làm sạch nội dung chương_: loại quảng cáo/watermark/ký tự rác, giữ đoạn văn; quy tắc thay thế văn bản theo truyện (`TextReplacementHelper`, cột replacement ở DB v9).
- _TTS_: tiền xử lý (`TtsTextPreprocessor`: số, viết tắt, ký tự đặc biệt), chuyển chương tự động, hẹn giờ tắt, MediaSession/nút tai nghe.
- _Tải offline_: tiến độ, hủy giữa chừng, dung lượng, resume sau khi process bị kill.
- _Hiệu năng Compose_: danh sách hàng nghìn chương (LazyColumn + key + `ImmutableList`), recomposition thừa, parse HTML trên main thread (bắt buộc `Dispatchers.IO`/`Default`).
- _UI tiếng Việt 100%_: không có chuỗi tiếng Anh lộ ra người dùng, kể cả thông báo lỗi.
