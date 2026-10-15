# Kế hoạch triển khai: Nâng cấp kiến trúc lớn (Media3, Room 3, Kotlin 2.4, AGP 9.4)

> **Trạng thái**: Completed
> **Revision**: 7
> **Ngày tạo**: 2026-10-13
> **Planner**: @planner (gemini-3.8-flash)

## Technical Plan

### 1. Mục Tiêu

Nâng cấp hiện đại hóa toàn diện 4 trụ cột kiến trúc cốt lõi của **AppTruyen**:
1. **Hạ tầng âm thanh TTS**: Chuyển đổi từ `androidx.media:media` (`MediaSessionCompat` — đã bị AndroidX đánh dấu deprecated) sang `androidx.media3:media3-session:1.11.1` (`MediaSessionService` + `MediaSession` + `SimpleBasePlayer`) trong `TtsService.kt` và `TtsManager.kt`. Chuyển hoàn toàn sang `DefaultMediaNotificationProvider` của Media3 session (loại bỏ toàn bộ hệ thống notification thủ công cũ). Bổ sung đầy đủ các commands cần thiết (`COMMAND_GET_CURRENT_MEDIA_ITEM`, `COMMAND_GET_TIMELINE`, `COMMAND_GET_METADATA`) để notification đọc metadata tiêu đề chương. Hoàn thiện cơ chế điều khiển tai nghe Bluetooth / TWS với debounce 400ms và chặn double-tap mặc định. Phân định rõ 2 tầng state auto-advance: `TtsState.isPlaying = false` ở tầng app để `ReaderViewModel` kích hoạt tải chương mới, trong khi `TtsPlayer` ở tầng Media3 giữ `playWhenReady = true` + `STATE_BUFFERING` cùng WakeLock/AudioFocus để giữ Foreground Service. **`TtsPlaybackStateMachine` là nơi DUY NHẤT quản lý job timeout 10s** (nhận `CoroutineScope` qua constructor; `TtsService` ủy quyền hoàn toàn và nhận callback `onTimeout` để chuyển player sang `STATE_ENDED`, nhả WakeLock/AudioFocus). **Khắc phục triệt để race condition khi Pause lúc Buffering bằng trạng thái chuyên biệt `PausedDuringAutoAdvance` và cơ chế cờ nội bộ `suppressNextPlay` trong `TtsPlaybackStateMachine` (M-SM3)**:
   - Các lối vào `pause()` trong lúc buffering (1 chạm tai nghe ở `TtsService.kt:543`, listener AudioFocus ở `TtsService.kt:515-520`) dùng điều kiện `stateMachine.isActiveOrBuffering` (thay vì `_state.value.isPlaying`) để chuyển đúng vào `PausedDuringAutoAdvance`, hủy ngay timer 10s.
   - `TtsService.setContentForAutoAdvance()` **không bao giờ** tự gọi `play()` (loại bỏ `m-DUP`), chỉ gọi `stateMachine.onAutoAdvanceContentLoaded()`.
   - Nếu đang ở `PausedDuringAutoAdvance`: `stateMachine` chuyển về `Paused` và bật cờ một lần `suppressNextPlay = true`. Khi `ReaderViewModel.kt:455` gọi `ttsManager.play()` ngay sau đó, `TtsService.play()` gọi `stateMachine.onPlayRequested()`: do cờ đang bật, state machine xóa cờ, giữ nguyên `Paused` và trả về `false`, nuốt đúng lời gọi `play()` này. Lần user bấm Play tiếp theo sẽ gọi `onPlayRequested()` khi cờ đã tắt, chuyển sang `Playing` và phát bình thường.
   - Đối với mọi trường hợp chuyển chương thủ công (`nextChapter()`, `prevChapter()`, `navigateToChapter()`, tai nghe 2-chạm/3-chạm ở TC-03): khi state là `Paused` hoặc `Idle` thường, `suppressNextPlay` vẫn là `false` nên `onPlayRequested()` trả về `true` (tiếp tục phát liền mạch), loại bỏ hoàn toàn nguy cơ nuốt lệnh phát hợp lệ.
   - Bảo vệ service bằng việc giữ nguyên `android:exported="false"`, phê duyệt controller an toàn (`isMediaNotificationController`, `isTrusted`, `packageName == packageName`). Bảo toàn tương thích dual-binding với UI thông qua cú pháp chuẩn `super.onBind(intent) ?: binder`. Tách logic state machine sang lớp thuần `TtsPlaybackStateMachine.kt` để kiểm thử 100% trên JVM JUnit 5.
2. **Hạ tầng lưu trữ dữ liệu (Room Database)**: Nâng cấp từ Room 2.8.4 lên Room 3.0 (`androidx.room3:3.0.3` — KMP Room natively, KSP mandatory, loại bỏ hoàn toàn KAPT), tích hợp driver `androidx.sqlite:sqlite-bundled:2.7.1` với `.setDriver(BundledSQLiteDriver())`. Áp dụng cây quyết định constructor Room 3 rõ ràng: Nhánh chính (theo tài liệu Room 3) suppress RoomWarning (lấy hằng số chính xác từ `androidx.room3.RoomWarnings` hoặc log compiler KSP khi nâng cấp) và truyền factory lambda `{ AppDatabase_Impl() }` trực tiếp vào `Room.databaseBuilder<AppDatabase>(context, "apptruyen.db", factory = { AppDatabase_Impl() })`; Nhánh dự phòng dùng `@ConstructedBy(AppDatabaseConstructor::class)` với `object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> { override fun initialize(): AppDatabase = AppDatabase_Impl() }` (xóa bỏ hoàn toàn `AppDatabase::class.instantiateImpl()`). Cung cấp interface mỏng `DatabaseTransactionRunner` để `DownloadRepository.kt` thực thi transaction an toàn và test JVM không phụ thuộc bytecode KMP nội bộ (`mockkStatic`). Cập nhật migrations v5→v9 sang `SQLiteConnection` theo đúng chữ ký chuẩn duy nhất của Room 3: `override suspend fun onMigrate(connection: SQLiteConnection)`.
3. **Ngôn ngữ & Trình biên dịch (Kotlin & KSP)**: Nâng cấp Kotlin lên `2.4.21`, pin Kotlin Gradle Plugin trong `build.gradle.kts`, cập nhật Compose compiler plugin `2.4.21`, serialization plugin `2.4.21`, đồng bộ version Google KSP `com.google.devtools.ksp` độc lập (`2.3.12`), và nâng cấp `kotlinx-serialization-json` lên `1.11.0`. Xác nhận môi trường qua `.\gradlew.bat buildEnvironment`.
4. **Hệ thống Build & Code Quality Gate**: Nâng cấp Android Gradle Plugin (AGP) từ `9.3.3` lên `9.4.0` trong `build.gradle.kts`, nâng Gradle Wrapper lên `9.6.0` trong `gradle/wrapper/gradle-wrapper.properties`, cập nhật ProGuard rules tinh gọn cho `androidx.room3` và `androidx.media3` (gỡ bỏ rule cũ `androidx.media.**`). Thiết lập cấu hình schema assets và `room3-testing` cho androidTest. Thêm Bước 1.0 pre-flight để reset daemon, có baseline unit test xanh và đếm chính xác số lỗi ktlint baseline. Thực hiện chiến lược ktlint tuân thủ Minimal Diffs Policy đối chiếu baseline (không thêm vi phạm mới, sửa triệt để các file thuộc phạm vi nâng cấp như `AppModule.kt:46-47` đạt 100% zero-violation).

---

### 2. Pre-Work Reading

Đã kiểm tra và đối chiếu các tài liệu và source code liên quan theo Pre-Work Protocol (`.agents/rules/02-quality.md`):
- **Tài liệu quản trị & quy tắc**:
  - `AGENTS.md` (Quy tắc kiến trúc MVVM/MVI, UI 100% tiếng Việt, Dagger Hilt, Coroutines/Flow, Room).
  - `.agents/rules/00-core.md`, `.agents/rules/01-coding.md` (Cấm `!!`, cấm `GlobalScope`, chuẩn kiểm tra lỗi, đơn nhiệm file, quy định test).
  - `.agents/rules/02-quality.md` (Pre-Work Protocol, Tier 1/2 Verification Checklist, Test Failure Triage).
  - `.agents/rules/05-plan-review.md` (Quy trình plan → review → revise, máy trạng thái plan, Code Freeze Guard).
  - `docs/lessons-learned.md` (Các bài học về shell filter, reviewer sandbox, audio focus, offline storage).
  - `docs/progress.md` (Trạng thái MVP và tính năng hiện tại v2.8.3).
  - `docs/architecture.md` (Cấu trúc thư mục, sơ đồ luồng dữ liệu, database schema v9, dependencies chính).
  - `docs/plans/README.md` (Template chuẩn và bảng trạng thái kế hoạch).
- **Mã nguồn thực tế đã khảo sát**:
  - `build.gradle.kts`, `app/build.gradle.kts`, `gradle/wrapper/gradle-wrapper.properties` (Cấu hình build toolchain hiện tại).
  - `app/proguard-rules.pro` (Các rule keep hiện có cho Room, Media, Coroutines, Serialization).
  - `app/src/main/AndroidManifest.xml` (Khai báo service, permission, receiver MediaButtonReceiver, intent filters).
  - `app/src/main/java/com/personal/apptruyen/tts/TtsService.kt` (Foreground Service TTS, `MediaSessionCompat`, AudioFocus, WakeLock, SilentPlayer).
  - `app/src/main/java/com/personal/apptruyen/tts/TtsManager.kt` (Proxy kết nối UI và `TtsService` qua Binder).
  - `app/src/main/java/com/personal/apptruyen/tts/TtsTextPreprocessor.kt` (Tiền xử lý văn bản tiếng Việt cho TTS).
  - `app/src/main/java/com/personal/apptruyen/data/local/AppDatabase.kt` (RoomDatabase, 5 entities, migrations v5→v9).
  - `app/src/main/java/com/personal/apptruyen/data/repository/DownloadRepository.kt` (Sử dụng `withTransaction` ở dòng 4, 198; gọi tuần tự `progressDao.deleteProgress`, `chapterDao.deleteChaptersByStory`, `storyDao.deleteStory`).
  - `app/src/main/java/com/personal/apptruyen/data/local/StoryDao.kt`, `ChapterDao.kt`, `ReadingProgressDao.kt`, `ReadingStatsDao.kt`, `SearchHistoryDao.kt` (Tất cả DAO methods đều là `suspend` hoặc `Flow`).
  - `app/src/main/java/com/personal/apptruyen/data/local/entity/*.kt` (`StoryEntity`, `ChapterEntity`, `ReadingProgressEntity`, `ReadingStatsEntity`, `SearchHistoryEntity`).
  - `app/src/main/java/com/personal/apptruyen/di/AppModule.kt` (Cung cấp `AppDatabase` qua `Room.databaseBuilder`, migrations, DAOs).
  - `app/schemas/com.personal.apptruyen.data.local.AppDatabase/` (Schema Room đầy đủ từ 3.json, 4.json, 5.json, 6.json, 7.json, 8.json đến 9.json).
  - `app/src/test/java/com/personal/apptruyen/tts/TtsManagerTest.kt`, `app/src/test/java/com/personal/apptruyen/data/repository/StoryRepositoryTest.kt` (Test suites hiện tại).

---

### 3. Assumptions & Cross-Task Dependencies

1. **Khả năng tương thích của Hilt**: Dagger Hilt hiện đang ở version `2.60.1` (trong cả `build.gradle.kts` và `app/build.gradle.kts`), phiên bản này đã hỗ trợ đầy đủ Kotlin 2.x và KSP 2 độc lập.
2. **Bảo toàn dữ liệu Room v9**: Schema hiện tại đang ở version 9. Việc nâng cấp Room 3.0 chỉ thay đổi runtime engine và driver (từ `SupportSQLiteDatabase` sang `SQLiteConnection` với `BundledSQLiteDriver`), không thay đổi bảng, cột hay chỉ mục nào của DB. Toàn bộ dữ liệu thư viện, chương đã tải và tiến độ đọc của người dùng được giữ nguyên 100%.
3. **Cơ chế Dual-Binding của TTS với `super.onBind(intent) ?: binder`**: `TtsManager` bind trực tiếp với `TtsService` thông qua `TtsBinder` để đồng bộ UI state. Trong `TtsService.onBind(intent)`, gọi `val superBinder = super.onBind(intent)`: nếu intent là `MediaSessionService.SERVICE_INTERFACE` (hoặc media browser action), `superBinder` khác null và được trả về cho hệ thống; nếu là intent bind nội bộ từ `TtsManager`, `superBinder` là null và trả về `binder` (`TtsBinder`) qua toán tử `?:`.
4. **Room 3 Constructor & Migrations**:
   - Sử dụng chữ ký chuẩn duy nhất của Room 3 cho Migration: `override suspend fun onMigrate(connection: SQLiteConnection)`.
   - Cây quyết định constructor cho `AppDatabase` trên Android:
     * **Nhánh chính (Khuyến nghị theo tài liệu Room 3)**: Suppress RoomWarning trên `AppDatabase` (lấy hằng số chính xác từ `androidx.room3.RoomWarnings` hoặc log cảnh báo của KSP compiler khi nâng cấp Phase 2, ví dụ `RoomWarnings.CANNOT_CREATE_CONSTRUCTOR` hoặc `@Suppress("ROOM_WARNING_CANNOT_CREATE_CONSTRUCTOR")`) và truyền factory lambda `{ AppDatabase_Impl() }` trực tiếp vào `Room.databaseBuilder<AppDatabase>(context, "apptruyen.db", factory = { AppDatabase_Impl() })`, bỏ `@ConstructedBy`.
     * **Nhánh dự phòng (nếu KSP compiler 3.0.3 bắt buộc `@ConstructedBy`)**: Khai báo `@ConstructedBy(AppDatabaseConstructor::class)` trên `AppDatabase` và triển khai `object AppDatabaseConstructor : androidx.room3.RoomDatabaseConstructor<AppDatabase> { override fun initialize(): AppDatabase = AppDatabase_Impl() }`.
     * Xóa bỏ hoàn toàn `AppDatabase::class.instantiateImpl()` (API không có trong tài liệu). Ghi nhận kết quả thực tế vào `docs/lessons-learned.md`.
5. **State Machine Auto-Advance, Trạng Thái `PausedDuringAutoAdvance`, Cơ Chế `suppressNextPlay` & Sửa Lối Vào Pause (M-SM3)**:
   - `TtsPlaybackStateMachine` là **nơi DUY NHẤT** quản lý job timeout 10s: Nhận `CoroutineScope` qua constructor (`TtsService` truyền `serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)`, `TtsPlaybackStateTest` truyền `TestScope`). `TtsService` ủy quyền hoàn toàn cho `stateMachine` và nhận callback `onTimeout` để chuyển `TtsPlayer` sang `STATE_ENDED`, nhả WakeLock và AudioFocus. Xóa bỏ `autoAdvanceTimeoutJob` riêng biệt trong service.
   - Khi đọc xong đoạn cuối cùng của chương (`currentParagraphIndex == paragraphs.size - 1`) và `autoAdvance = true`: tầng ứng dụng (`TtsState`) đặt `isPlaying = false` để kích hoạt `ReaderViewModel.kt:329` (`justStopped = true`) gọi `autoNextChapter()`; trong khi đó tầng Media3 (`TtsPlayer`) chuyển sang `Player.STATE_BUFFERING` với `playWhenReady = true`, tiếp tục giữ WakeLock và AudioFocus để Media3 duy trì Foreground notification trong khoảng 1–3s khi tải chương mới.
   - **Sửa 2 lối vào `pause()` lúc buffering (M-SM3b)**: Vì trong lúc `BufferingAutoAdvance`, `TtsState.isPlaying == false`, 2 lối vào pause trong `TtsService.kt` phải dùng thuộc tính của state machine: `stateMachine.isActiveOrBuffering` (tức `state == Playing || state == BufferingAutoAdvance`) thay vì `_state.value.isPlaying`:
     1. Nút 1 chạm tai nghe (`TtsService.kt:543`): `if (stateMachine.isActiveOrBuffering) pause() else play()`. Khi đang buffering mà user bấm 1 chạm tai nghe → gọi `pause()` để vào `PausedDuringAutoAdvance` (tránh bị gọi nhầm `play()` đọc lặp đoạn cuối chương cũ).
     2. Listener AudioFocus `AUDIOFOCUS_LOSS` / `AUDIOFOCUS_LOSS_TRANSIENT` (`TtsService.kt:515-520`): `if (stateMachine.isActiveOrBuffering) pause()`. Khi có cuộc gọi đến lúc đang buffering → gọi `pause()` để vào `PausedDuringAutoAdvance` (tránh tự phát chương mới đè lên cuộc gọi).
     3. Tương tự, `TtsPlayer.handleSetPlayWhenReady(false)` gọi `pause()` route qua state machine để vào `PausedDuringAutoAdvance`.
   - **Bảo vệ đường gọi thật từ `ReaderViewModel.kt:448,455` (M-SM3a, Hướng 1 — không sửa `ReaderViewModel.kt`)**:
     * `ReaderViewModel.kt:448,455` gọi `ttsManager.setContentForAutoAdvance(...)` rồi ngay lập tức gọi `ttsManager.play()` trên Main thread.
     * `TtsService.setContentForAutoAdvance()` **KHÔNG BAO GIỜ** tự gọi `ttsPlayer.play()` hay `play()` (giải quyết triệt để `m-DUP`), chỉ nạp danh sách câu ở `currentIndex = 0` và gọi `stateMachine.onAutoAdvanceContentLoaded()`.
     * Trong `TtsPlaybackStateMachine`:
       - Có biến nội bộ một lần `private var suppressNextPlay = false`.
       - Khi `onAutoAdvanceContentLoaded()` được gọi:
         * Nếu state hiện tại là `PausedDuringAutoAdvance`: chuyển về `Paused`, bật `suppressNextPlay = true`.
         * Nếu state hiện tại là `BufferingAutoAdvance`: hủy timer 10s, giữ `suppressNextPlay = false`.
         * Nếu state hiện tại là `Paused` hoặc `Idle` thường: giữ `suppressNextPlay = false`.
       - Hàm `stateMachine.onPlayRequested(): Boolean`: được gọi ở ngay đầu hàm `TtsService.play()`.
         * Nếu `suppressNextPlay == true`: xóa cờ `suppressNextPlay = false`, giữ nguyên `Paused` và trả về `false` (khiến `TtsService.play()` return sớm, nuốt đúng lời gọi `ttsManager.play()` từ `ReaderViewModel.kt:455`).
         * Nếu `suppressNextPlay == false`: chuyển state sang `Playing` và trả về `true` (cho phép `TtsService.play()` phát bình thường).
       - Khi user bấm Play sau đó: `onPlayRequested()` được gọi lúc `suppressNextPlay == false` → trả về `true` và phát bình thường.
       - Mọi lời gọi `pause()`, `stop()`, hoặc `setContent()` (nạp chương mới thủ công) đều reset `suppressNextPlay = false` (và nếu đang `PausedDuringAutoAdvance` thì thoát về `Paused`/`Idle` thường).
   - **Chống Hồi Quy Chuyển Chương Thủ Công (M-SM2)**: Khi user bấm Next/Prev hoặc 2-chạm/3-chạm tai nghe từ `Playing` (ReaderViewModel gọi `pause()` trước), state chuyển sang `Paused` thường (KHÔNG phải `PausedDuringAutoAdvance`). Do đó `suppressNextPlay = false`, khi `ReaderViewModel.kt:455` gọi `play()` thì `onPlayRequested()` trả về `true` và TTS tiếp tục phát chương mới liền mạch!
6. **Bảo mật Service & Tiêu chí onConnect**: Giữ nguyên `android:exported="false"` cho `TtsService` trong `AndroidManifest.xml` kèm intent-filter `MEDIA_BUTTON` và `MediaSessionService`. Trong `MediaSession.Callback.onConnect`, chấp nhận kết nối khi `session.isMediaNotificationController(controller) || controller.isTrusted || controller.packageName == packageName` sử dụng `AcceptedResultBuilder(session)` (hoặc overload `AcceptedResultBuilder(session, controller)` / `onConnectAsync` tương thích với Media3 1.11.1).
7. **Chiến lược ktlint theo Minimal Diffs Policy**: Baseline hiện tại có lỗi ktlint ở các file legacy ngoài phạm vi. Bước 1.0 sẽ đếm chính xác số lỗi baseline. Tiêu chí Pass cho Phase 4 là Zero-Regression (tổng số lỗi không vượt baseline) và Tinh khiết cục bộ (100% zero-violation trên các file thuộc phạm vi nâng cấp, đặc biệt `AppModule.kt:46-47`).
8. **Target SDK & Java Runtime**: Ứng dụng duy trì `compileSdk = 36`, `targetSdk = 36`, `minSdk = 26`, và Java target JVM 17.

---

### 4. Verified Versions Table

| Thư viện / Plugin | Version hiện tại | Version đề xuất | Latest đã xác minh | Nguồn URL | Tương thích? |
|---|---|---|---|---|---|
| **Android Gradle Plugin (AGP)** | `9.3.3` | `9.4.0` | `9.4.0` | [developer.android.com/build/releases/gradle-plugin](https://developer.android.com/build/releases/gradle-plugin) | ✅ Tương thích (yêu cầu Gradle 9.6.0+, Java 17, SDK Build Tools 36) |
| **Gradle Wrapper** | `9.5.1` | `9.6.0` | `9.6.0` | [services.gradle.org/distributions](https://services.gradle.org/distributions/) | ✅ Khớp chuẩn yêu cầu tối thiểu của AGP 9.4.0 |
| **Kotlin Language** | `2.3.10` | `2.4.21` | `2.4.21` | [kotlinlang.org/docs/releases.html](https://kotlinlang.org/docs/releases.html) | ✅ Bản stable mới nhất (phát hành tháng 10/2026) |
| **Kotlin Compose Compiler Plugin** | `2.3.10` | `2.4.21` | `2.4.21` | [developer.android.com/jetpack/androidx/releases/compose-compiler](https://developer.android.com/jetpack/androidx/releases/compose-compiler) | ✅ Đồng bộ 1:1 với Kotlin version |
| **Kotlin Serialization Plugin** | `2.3.10` | `2.4.21` | `2.4.21` | [github.com/Kotlin/kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) | ✅ Đồng bộ 1:1 với Kotlin version |
| **Google KSP (`com.google.devtools.ksp`)** | `2.3.6` | `2.3.12` | `2.3.12` | [github.com/google/ksp/releases](https://github.com/google/ksp/releases) | ✅ Tương thích hoàn toàn với Kotlin 2.4.x (KSP độc lập) |
| **kotlinx-serialization-json** | `1.8.1` | `1.11.0` | `1.11.0` | [mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-serialization-json](https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-serialization-json) | ✅ Tương thích Kotlin 2.4 |
| **Room 3.0 Runtime (`androidx.room3:room3-runtime`)** | `2.8.4` (`androidx.room`) | `3.0.3` | `3.0.3` | [developer.android.com/jetpack/androidx/releases/room3](https://developer.android.com/jetpack/androidx/releases/room3) | ✅ KMP Room native, coroutine-first (Room 2.8.5 là fallback) |
| **Room 3.0 Compiler (`androidx.room3:room3-compiler`)** | `2.8.4` (`androidx.room`) | `3.0.3` | `3.0.3` | [developer.android.com/jetpack/androidx/releases/room3](https://developer.android.com/jetpack/androidx/releases/room3) | ✅ KSP mandatory, loại bỏ KAPT |
| **SQLite Bundled Driver (`androidx.sqlite:sqlite-bundled`)** | _(chưa dùng)_ | `2.7.1` | `2.7.1` | [developer.android.com/jetpack/androidx/releases/sqlite](https://developer.android.com/jetpack/androidx/releases/sqlite) | ✅ Driver SQLite bundled ổn định mới nhất (09/09/2026) |
| **Media3 Session (`androidx.media3:media3-session`)** | `1.7.1` (`androidx.media:media`) | `1.11.1` | `1.11.1` | [developer.android.com/jetpack/androidx/releases/media3](https://developer.android.com/jetpack/androidx/releases/media3) | ✅ minSdk 23, tương thích minSdk 26, chuẩn thay thế MediaSessionCompat |

---

### 5. Files Cần Chỉnh Sửa / Tạo Mới

| Nhãn | File | Tầng | Ghi chú & Lý do (file nhạy cảm được đánh dấu) |
|---|---|---|---|
| `[MODIFY]` | `build.gradle.kts` | Build Infra | **[NHẠY CẢM - Build Script]**: Cập nhật AGP 9.4.0, Kotlin plugins (compose, serialization) 2.4.21, KSP 2.3.12, thêm plugin `id("androidx.room3") version "3.0.3" apply false`, xác nhận pin KGP 2.4.21. |
| `[MODIFY]` | `gradle/wrapper/gradle-wrapper.properties` | Build Infra | **[NHẠY CẢM - Wrapper]**: Nâng `distributionUrl` lên `gradle-9.6.0-bin.zip`. |
| `[MODIFY]` | `app/build.gradle.kts` | Build Infra | **[NHẠY CẢM - Build Script]**: Thay thế `androidx.room:*` bằng `androidx.room3:*`, thêm `androidx.sqlite:sqlite-bundled:2.7.1`, thay `androidx.media:media` bằng `androidx.media3:media3-session:1.11.1`, nâng `kotlinx-serialization-json:1.11.0`, cấu hình plugin `room { schemaDirectory(...) }`, gỡ bỏ `ksp { arg("room.schemaLocation") }`, cấu hình assets schema cho androidTest và thêm dependency `androidx.room3:room3-testing:3.0.3`. |
| `[MODIFY]` | `app/proguard-rules.pro` | Build Infra | **[NHẠY CẢM - R8/ProGuard]**: Cập nhật keep rules tinh gọn cho `androidx.room3` và `androidx.media3`, gỡ bỏ triệt để rule cũ `-keep class androidx.media.** { *; }`. |
| `[MODIFY]` | `app/src/main/AndroidManifest.xml` | Manifest | **[NHẠY CẢM - Manifest]**: Giữ nguyên `android:exported="false"` cho `TtsService`, thêm intent filter `androidx.media3.session.MediaSessionService` và giữ `android.intent.action.MEDIA_BUTTON`, gỡ receiver `MediaButtonReceiver` cũ. |
| `[NEW]` | `app/src/main/java/com/personal/apptruyen/tts/TtsPlaybackStateMachine.kt` | `tts/` | Tạo lớp thuần Kotlin quản lý trạng thái playback, chuyển câu/đoạn, auto-advance state mapping (tách biệt `TtsState.isPlaying = false` ở app layer và `Player.STATE_BUFFERING` ở Media3 layer). **Nơi DUY NHẤT quản lý job timeout 10s** qua `CoroutineScope` được inject. **Bổ sung trạng thái `PausedDuringAutoAdvance`, cờ một lần `suppressNextPlay`, hàm `onPlayRequested(): Boolean` và thuộc tính `isActiveOrBuffering`** để xử lý triệt để race condition và bảo vệ chuyển chương thủ công (M-SM3). Độc lập hoàn toàn với Android Looper để test 100% trên JVM JUnit 5. |
| `[NEW]` | `app/src/main/java/com/personal/apptruyen/tts/TtsPlayer.kt` | `tts/` | Tạo custom Player kế thừa `SimpleBasePlayer(Looper.getMainLooper())` wrap TextToSpeech và silent MediaPlayer để cấp cho Media3 `MediaSession`. Khai báo đủ 8 commands (bao gồm `COMMAND_GET_CURRENT_MEDIA_ITEM`, `COMMAND_GET_TIMELINE`, `COMMAND_GET_METADATA`). Duy trì timeline ít nhất 1 `MediaItem` với `MediaMetadata`. Đảm bảo mọi state update post về Main Looper. |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/tts/TtsService.kt` | `tts/` | Chuyển sang kế thừa `MediaSessionService()`. Dùng hoàn toàn `DefaultMediaNotificationProvider` của Media3, xóa bỏ `buildNotification()` thủ công. Xử lý `onMediaButtonEvent` với debounce 400ms và trả `true` chặn double-tap mặc định. Khai báo `serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)` truyền vào `TtsPlaybackStateMachine`. Sửa 1 chạm tai nghe và AudioFocus listener dùng `stateMachine.isActiveOrBuffering`. `setContentForAutoAdvance` **không tự gọi `play()`** (m-DUP). `play()` kiểm tra `stateMachine.onPlayRequested()` để nuốt đúng lệnh phát khi vừa pause lúc buffering (M-SM3). Triển khai `onConnect` phê duyệt an toàn (`isMediaNotificationController`, `isTrusted`, `packageName == packageName`) dùng `AcceptedResultBuilder(session)`. Triển khai `onBind` với `super.onBind(intent) ?: binder`. |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/tts/TtsManager.kt` | `tts/` | Dùng safe cast `as? TtsService.TtsBinder`, bảo toàn API công khai cho `ReaderViewModel`. |
| `[NEW]` | `app/src/main/java/com/personal/apptruyen/data/local/DatabaseTransactionRunner.kt` | `data/local/` | Interface mỏng quản lý transaction: `suspend fun <R> withWriteTransaction(block: suspend () -> R): R`. Giúp `DownloadRepository` và các class khác thực thi transaction mà không phụ thuộc tên class nội bộ Room 3 KMP trong unit test (xử lý m-MK, m-NAME). |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/data/local/AppDatabase.kt` | `data/local/` | **[NHẠY CẢM - Room Database]**: Đổi package sang `androidx.room3.*`, áp dụng decision tree constructor Room 3 (nhánh chính: suppress RoomWarning + factory lambda `{ AppDatabase_Impl() }`; nhánh dự phòng: `@ConstructedBy(AppDatabaseConstructor::class)`), cập nhật 4 migration v5→v9 sang `SQLiteConnection` theo đúng chữ ký chuẩn duy nhất: `override suspend fun onMigrate(connection: SQLiteConnection)`. |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/data/repository/DownloadRepository.kt` | `data/repository/` | Inject `transactionRunner: DatabaseTransactionRunner` (gỡ bỏ tham số `database: AppDatabase` khỏi constructor, xử lý m-CTOR) và thực thi transaction an toàn qua `transactionRunner.withWriteTransaction { ... }`, gọi tuần tự 3 DAO thật (`progressDao.deleteProgress`, `chapterDao.deleteChaptersByStory`, `storyDao.deleteStory`). |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/data/local/StoryDao.kt` | `data/local/` | Chuyển import sang `androidx.room3.*`. |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/data/local/ChapterDao.kt` | `data/local/` | Chuyển import sang `androidx.room3.*`. |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/data/local/ReadingProgressDao.kt` | `data/local/` | Chuyển import sang `androidx.room3.*`. |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/data/local/ReadingStatsDao.kt` | `data/local/` | Chuyển import sang `androidx.room3.*`. |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/data/local/SearchHistoryDao.kt` | `data/local/` | Chuyển import sang `androidx.room3.*`. |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/data/local/entity/*.kt` | `data/local/entity/` | Chuyển import sang `androidx.room3.*` cho 5 entities. |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/di/AppModule.kt` | `di/` | Cập nhật `Room.databaseBuilder<AppDatabase>` dùng `androidx.room3`, bổ sung `.setDriver(androidx.sqlite.driver.bundled.BundledSQLiteDriver())`, factory lambda `{ AppDatabase_Impl() }` (xóa `instantiateImpl()`), đăng ký migrations mới và bind `DatabaseTransactionRunner` gọi `db.withWriteTransaction { block() }`. Sửa triệt để format parameter wrapping dòng 46-47 để pass ktlint. |
| `[MODIFY]` | `.agents/rules/00-core.md` | Governance | **[NHẠY CẢM - Quy tắc Quản trị]**: Cập nhật mục Technology Stack bất biến từ "AndroidX Media (MediaSession)" thành "AndroidX Media3 (MediaSession)" và Room 3.0. |
| `[NEW]` | `app/src/test/java/com/personal/apptruyen/tts/TtsPlaybackStateTest.kt` | Test | **[NHẠY CẢM - Unit Test]**: Unit test thuần JVM JUnit 5 kiểm tra `TtsPlaybackStateMachine` với virtual time (`TestScope`), xác minh các chuyển trạng thái, buffering khi auto-advance, timeout 10s, hủy job an toàn, và 4 ca test bảo vệ đường gọi thật từ ViewModel, 1 chạm tai nghe, AudioFocus và chuyển chương thủ công (M-SM3, TC-03, TC-10). |
| `[NEW]` | `app/src/test/java/com/personal/apptruyen/data/repository/DownloadRepositoryTest.kt` | Test | **[NHẠY CẢM - Unit Test]**: Unit test JVM kiểm tra `DownloadRepository.deleteStory` thông qua mock `DatabaseTransactionRunner` (không cần mock `AppDatabase`, xử lý m-CTOR), xác minh đúng thứ tự DAO thật (`progressDao.deleteProgress` → `chapterDao.deleteChaptersByStory` → `storyDao.deleteStory`) và rethrow exception khi lỗi mà không phụ thuộc bytecode KMP (m-MK). |
| `[NEW]` | `app/src/androidTest/java/com/personal/apptruyen/data/local/AppDatabaseMigrationTest.kt` | Test | **[NHẠY CẢM - Instrumented Test]**: Test thực thi các migration v5→v9 của Room 3 và test rollback transaction thực tế (chèn story/chapter/progress, ném lỗi trong `withWriteTransaction`, assert cả 3 bảng còn nguyên) trên môi trường Android runtime với `BundledSQLiteDriver` (m-TN). |

---

### [NEEDS_PRODUCT_DECISION] Quyết Định Sản Phẩm & Kiến Trúc

#### 1. Chiến Lược Triển Khai Kiến Trúc (Phased Rollout vs Monolithic)
- **Vấn đề**: Việc thay đổi đồng thời 4 nền tảng kiến trúc (Build Toolchain, Room 3 KMP, Media3 TTS Session, Gradle Wrapper) có phạm vi ảnh hưởng lớn. Nếu gộp tất cả trong một lần commit/build duy nhất, việc cô lập nguyên nhân khi xảy ra lỗi compiler/KSP/runtime sẽ rất khó khăn.
- **Phương án A (Khuyến nghị)**: Triển khai theo 4 Phase tuần tự trong cùng một Implementation Plan (Phase 1: Build Toolchain; Phase 2: Room 3.0 KMP; Phase 3: Media3 TTS Session; Phase 4: Full Quality Gate & Cleanup). Sau mỗi phase đều kiểm tra `compileDebugKotlin` để fail-fast.
- **Phương án B**: Triển khai toàn bộ các file cùng một lúc trong một lượt sửa (Monolithic change).
- **Default Assumption**: Áp dụng **Phương án A** (Phased Rollout tuần tự theo 4 giai đoạn).

#### 2. Cập Nhật Technology Stack Bất Biến trong `.agents/rules/00-core.md`
- **Vấn đề**: `.agents/rules/00-core.md` dòng 34 quy định stack bất biến "AndroidX Media (MediaSession)". Việc nâng cấp lên `androidx.media3` thay thế hoàn toàn thư viện deprecated cũ.
- **Phương án A (Khuyến nghị)**: Đồng ý cập nhật `.agents/rules/00-core.md` sang "AndroidX Media3 (MediaSessionService + MediaSession)" và ghi nhận Room 3.0.
- **Phương án B**: Giữ nguyên text trong rule.
- **Default Assumption**: Áp dụng **Phương án A**.

---

### 6. Các Bước Thực Hiện

#### Phase 1: Nâng Cấp Build System & Kotlin/KSP Toolchain
- [ ] **Bước 1.0 (Pre-flight Baseline - xử lý m-BASE)**:
  - Dừng các Kotlin daemon cũ để tránh lỗi worker: `.\gradlew.bat --stop`.
  - Chạy `.\gradlew.bat testDebugUnitTest` để xác nhận baseline unit test xanh trên toolchain hiện tại (ghi lại tổng số test cases pass).
  - Chạy `.\gradlew.bat ktlintCheck` và lưu lại báo cáo, đếm chính xác số lượng lỗi vi phạm baseline hiện tại (để làm mốc đối chiếu Zero-Regression chính xác cho Phase 4).
- [ ] **Bước 1.1**: Cập nhật `gradle/wrapper/gradle-wrapper.properties` lên Gradle `9.6.0` (`distributionUrl=https\://services.gradle.org/distributions/gradle-9.6.0-bin.zip`).
- [ ] **Bước 1.2**: Cập nhật `build.gradle.kts` (xử lý m-KGP):
  - `com.android.application` version `9.4.0`
  - `id("org.jetbrains.kotlin.android") version "2.4.21" apply false` (xác nhận pin KGP 2.4.21)
  - `org.jetbrains.kotlin.plugin.compose` version `2.4.21`
  - `org.jetbrains.kotlin.plugin.serialization` version `2.4.21`
  - `com.google.devtools.ksp` version `2.3.12`
  - Thêm `id("androidx.room3") version "3.0.3" apply false`
- [ ] **Bước 1.3**: Cập nhật `app/build.gradle.kts` (xử lý M-T3):
  - Thêm plugin `id("androidx.room3")`
  - Cập nhật dependency `org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0`
  - Cấu hình assets schema cho `androidTest`:
    ```kotlin
    sourceSets {
        getByName("androidTest") {
            assets.srcDirs(files("$projectDir/schemas"))
        }
    }
    ```
  - Thêm dependency `androidTestImplementation("androidx.room3:room3-testing:3.0.3")`
- [ ] **Bước 1.4**: Chạy `.\gradlew.bat compileDebugKotlin` và `.\gradlew.bat buildEnvironment` để xác nhận AGP 9.4.0, Gradle 9.6.0, Kotlin 2.4.21 và KSP 2.3.12 biên dịch thành công source hiện tại. (Nếu fail, dừng lại xử lý triệt để trước khi sang Phase 2).

#### Phase 2: Nâng Cấp Room 3.0 KMP (`androidx.room3`)
- [ ] **Bước 2.1**: Cập nhật dependencies và cấu hình trong `app/build.gradle.kts`:
  - Gỡ bỏ `androidx.room:room-runtime:2.8.4`, `androidx.room:room-ktx:2.8.4`, `androidx.room:room-compiler:2.8.4`.
  - Bổ sung `implementation("androidx.room3:room3-runtime:3.0.3")`
  - Bổ sung `ksp("androidx.room3:room3-compiler:3.0.3")`
  - Bổ sung `implementation("androidx.sqlite:sqlite-bundled:2.7.1")`
  - Cấu hình Room extension:
    ```kotlin
    room {
        schemaDirectory("$projectDir/schemas")
    }
    ```
  - **Gỡ bỏ cấu hình KSP cũ**: Xóa dòng `arg("room.schemaLocation", "$projectDir/schemas")` khỏi khối `ksp { ... }`.
- [ ] **Bước 2.2**: Cập nhật 5 Entity files trong `app/src/main/java/com/personal/apptruyen/data/local/entity/` (`StoryEntity.kt`, `ChapterEntity.kt`, `ReadingProgressEntity.kt`, `ReadingStatsEntity.kt`, `SearchHistoryEntity.kt`): thay toàn bộ `import androidx.room.*` thành `import androidx.room3.*`.
- [ ] **Bước 2.3**: Cập nhật 5 DAO files trong `app/src/main/java/com/personal/apptruyen/data/local/` (`StoryDao.kt`, `ChapterDao.kt`, `ReadingProgressDao.kt`, `ReadingStatsDao.kt`, `SearchHistoryDao.kt`): thay `import androidx.room.*` thành `import androidx.room3.*`.
- [ ] **Bước 2.4**: Tạo `DatabaseTransactionRunner` & Cập nhật `DownloadRepository.kt` (xử lý m-MK, m-NAME, m-CTOR):
  - Tạo file interface mỏng: `app/src/main/java/com/personal/apptruyen/data/local/DatabaseTransactionRunner.kt` (`[NEW]`):
    ```kotlin
    interface DatabaseTransactionRunner {
        suspend fun <R> withWriteTransaction(block: suspend () -> R): R
    }
    ```
  - Cập nhật `DownloadRepository.kt`:
    - **Gỡ bỏ hoàn toàn tham số `private val database: AppDatabase` khỏi `@Inject constructor`** (`DownloadRepository.kt:35`), thay thế bằng `private val transactionRunner: DatabaseTransactionRunner` (cùng 3 DAO: `storyDao`, `chapterDao`, `progressDao`).
    - Cập nhật hàm `deleteStory`: gọi `transactionRunner.withWriteTransaction { ... }` đảm bảo tính atomic khi xóa progress, chapters và story.
    - Cách tiếp cận này loại trừ hoàn toàn sự phụ thuộc vào class JVM nội bộ của Room 3 KMP (`androidx.room3.RoomDatabaseKt`) và giúp unit test JVM `DownloadRepositoryTest.kt` chỉ cần mock `DatabaseTransactionRunner` mà không cần mock `AppDatabase`.
- [ ] **Bước 2.5**: Cập nhật `AppDatabase.kt` (xử lý M-R1, m-RW):
  - Thay import sang `androidx.room3.*`.
  - **Cây quyết định Constructor Room 3**:
    * **Nhánh chính (Khuyến nghị theo tài liệu Room 3)**: Khai báo `@Database(entities = [...], version = 9)` kèm `@Suppress("ROOM_WARNING_CANNOT_CREATE_CONSTRUCTOR")` (hoặc hằng số tương ứng từ `androidx.room3.RoomWarnings` / log compiler KSP khi nâng cấp Phase 2) trên `abstract class AppDatabase : RoomDatabase()`. Không dùng `@ConstructedBy` và không viết object constructor tay. Khởi tạo DatabaseBuilder qua factory lambda ở Bước 2.6.
    * **Nhánh dự phòng (nếu KSP compiler 3.0.3 bắt buộc `@ConstructedBy`)**: Khai báo `@ConstructedBy(AppDatabaseConstructor::class)` và triển khai constructor object cụ thể cho module Android:
      ```kotlin
      object AppDatabaseConstructor : androidx.room3.RoomDatabaseConstructor<AppDatabase> {
          override fun initialize(): AppDatabase = AppDatabase_Impl()
      }
      ```
    * Ghi nhận: Cổng Bước 2.8 sẽ kiểm chứng nhánh nào biên dịch sạch; kết quả thực tế và tên RoomWarning chính thức sẽ được ghi vào `docs/lessons-learned.md`.
  - Thay thế `androidx.sqlite.db.SupportSQLiteDatabase` bằng `androidx.sqlite.SQLiteConnection` và `androidx.sqlite.execSQL`.
  - Cập nhật các đối tượng `MIGRATION_5_6`, `MIGRATION_6_7`, `MIGRATION_7_8`, `MIGRATION_8_9` sang `androidx.room3.migration.Migration(from, to)` với chữ ký chuẩn duy nhất của Room 3:
    ```kotlin
    override suspend fun onMigrate(connection: SQLiteConnection) {
        connection.execSQL(...)
    }
    ```
- [ ] **Bước 2.6**: Cập nhật `AppModule.kt` (xử lý M-R1, M-Q1, m-MK, m-NAME):
  - Khởi tạo DatabaseBuilder:
    * Nhánh chính: `Room.databaseBuilder<AppDatabase>(context, "apptruyen.db", factory = { AppDatabase_Impl() })`.
    * Nhánh dự phòng: `Room.databaseBuilder<AppDatabase>(context, "apptruyen.db")` (sử dụng constructor từ `@ConstructedBy`).
    * *(Xóa bỏ hoàn toàn `AppDatabase::class.instantiateImpl()`)*.
  - **Bắt buộc**: Bổ sung `.setDriver(androidx.sqlite.driver.bundled.BundledSQLiteDriver())` để cung cấp SQLite driver cho Room 3.
  - Cấu hình `fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4)` và đăng ký 4 migrations mới.
  - Cung cấp bean `DatabaseTransactionRunner`:
    ```kotlin
    @Provides
    @Singleton
    fun provideDatabaseTransactionRunner(database: AppDatabase): DatabaseTransactionRunner =
        object : DatabaseTransactionRunner {
            override suspend fun <R> withWriteTransaction(block: suspend () -> R): R =
                database.withWriteTransaction(block)
        }
    ```
  - Sửa triệt để format parameter wrapping tại dòng 46-47 để đảm bảo zero-violation ktlint.
- [ ] **Bước 2.7**: Cập nhật `app/proguard-rules.pro` cho Room 3 và dọn dẹp (xử lý m-R8):
  - Thêm keep rules tinh gọn cho Room 3:
    ```proguard
    # Room 3 rules
    -keep class * extends androidx.room3.RoomDatabase
    -keep @androidx.room3.Entity class *
    -keep @androidx.room3.Dao class *
    -keepclassmembers class * extends androidx.room3.RoomDatabase {
        <init>();
    }
    -keep class * extends androidx.room3.RoomDatabaseConstructor {
        <init>();
        public *** initialize();
    }
    ```
  - **Gỡ bỏ rule cũ**: Xóa dòng `-keep class androidx.media.** { *; }`.
- [ ] **Bước 2.8**: Chạy kiểm tra cổng Phase 2 (xử lý m-RT):
  - `.\gradlew.bat compileDebugKotlin` để xác nhận KSP Room 3.0 generate code và biên dịch thành công.
  - `.\gradlew.bat compileDebugAndroidTestKotlin` để xác nhận sớm dependency `room3-testing` và source androidTest resolve hợp lệ (fail-fast).

#### Phase 3: Nâng Cấp Hạ Tầng Âm Thanh Media3 (`androidx.media3`)
- [ ] **Bước 3.1**: Cập nhật dependencies trong `app/build.gradle.kts`:
  - Gỡ bỏ `androidx.media:media:1.7.1`.
  - Thêm `implementation("androidx.media3:media3-session:1.11.1")`.
- [ ] **Bước 3.2**: Tạo file `app/src/main/java/com/personal/apptruyen/tts/TtsPlayer.kt` kế thừa `SimpleBasePlayer(Looper.getMainLooper())`:
  - **Khai báo Commands đầy đủ (xử lý M-M2)**: Cung cấp đầy đủ 8 lệnh điều khiển trong `Player.Commands`:
    `COMMAND_PLAY_PAUSE`, `COMMAND_STOP`, `COMMAND_SEEK_TO_NEXT`, `COMMAND_SEEK_TO_PREVIOUS`, `COMMAND_SET_SPEED_AND_PITCH`, `COMMAND_GET_CURRENT_MEDIA_ITEM`, `COMMAND_GET_TIMELINE`, `COMMAND_GET_METADATA`.
  - **Quản lý Timeline & Metadata (xử lý M-M2)**:
    - Timeline luôn duy trì ít nhất 1 `MediaItem` với `MediaMetadata.Builder()` chứa đầy đủ `title`, `artist`, `albumTitle` để `DefaultMediaNotificationProvider` có đủ dữ liệu render các nút play/pause/prev/next, loại bỏ hoàn toàn nguy cơ notification trống nút hoặc văng lỗi.
  - **Xử lý các hooks điều khiển**: Triển khai `handleSetPlayWhenReady(playWhenReady)`: khi `playWhenReady == false`, gọi thẳng `pause()` đi qua `stateMachine.onPauseRequested()` để chuyển vào `PausedDuringAutoAdvance` nếu đang ở `BufferingAutoAdvance`; triển khai `handleStop()`, `handleSeek()`.
  - **Đảm bảo Thread Looper**: Cung cấp `updatePlaybackState()` và gọi `invalidateState()` với ràng buộc: **mọi cuộc gọi cập nhật state đều phải được post về Main Looper** (`Handler(Looper.getMainLooper()).post { ... }`), loại trừ triệt để nguy cơ `IllegalStateException` khi gọi từ thread của TextToSpeech engine.
- [ ] **Bước 3.3**: Refactor `TtsService.kt`:
  - Đổi class declaration sang `class TtsService : MediaSessionService()`.
  - Thay thế `MediaSessionCompat` bằng `androidx.media3.session.MediaSession`.
  - Khởi tạo State Machine: `private val stateMachine = TtsPlaybackStateMachine(scope = serviceScope, onTimeout = { handleAutoAdvanceTimeout() })`, trong đó `serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)`.
  - Trong `onCreate()`: Xây dựng `mediaSession = MediaSession.Builder(this, ttsPlayer).setCallback(sessionCallback).build()`.
  - **Notification Provider**: Chuyển hoàn toàn sang `DefaultMediaNotificationProvider` của Media3. Xóa bỏ toàn bộ các hàm notification thủ công cũ (`buildNotification()`, `startForeground()`/`stopForeground()` tùy tiện, `NotificationCompat.MediaStyle`, channel management thủ công). Để `MediaSessionService` tự động quản lý Foreground Service lifecycle và Notification updates.
  - Triển khai `override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession`.
  - **Bảo mật Controller onConnect (xử lý M-M3, m-API)**:
    ```kotlin
    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        // Chỉ chấp nhận controller nội bộ ứng dụng, notification controller của Media3, hoặc system trusted controller
        return if (session.isMediaNotificationController(controller) ||
            controller.isTrusted ||
            controller.packageName == packageName
        ) {
            MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailablePlayerCommands(ttsPlayer.availableCommands)
                .build() // Hỗ trợ AcceptedResultBuilder(session) hoặc AcceptedResultBuilder(session, controller) / onConnectAsync tùy chữ ký thực tế
        } else {
            MediaSession.ConnectionResult.reject()
        }
    }
    ```
  - Triển khai `onMediaButtonEvent`:
    ```kotlin
    override fun onMediaButtonEvent(
        session: MediaSession,
        controllerInfo: MediaSession.ControllerInfo,
        intent: Intent,
    ): Boolean {
        // Xử lý debounce 400ms đa chạm (1 tap Play/Pause, 2 taps Next, 3 taps Prev)
        // BẮT BUỘC return true để thông báo sự kiện đã được tiêu thụ, chặn double-tap mặc định của Media3
        return handleMediaButtonKeyEvent(intent)
    }
    ```
  - **Sửa 2 lối vào Pause khi Buffering (xử lý M-SM3b)**:
    - **1 chạm tai nghe (`TtsService.kt:543`)**: Đổi điều kiện từ `if (_state.value.isPlaying)` sang `if (stateMachine.isActiveOrBuffering) pause() else play()`. Khi đang ở `BufferingAutoAdvance` mà user bấm 1 chạm tai nghe → gọi `pause()` để chuyển sang `PausedDuringAutoAdvance` (tránh bị gọi nhầm `play()` đọc lặp đoạn cuối chương cũ).
    - **Listener AudioFocus (`TtsService.kt:515-520`)**: Trong `onAudioFocusChange`, khi nhận `AUDIOFOCUS_LOSS` hoặc `AUDIOFOCUS_LOSS_TRANSIENT`: đổi điều kiện từ `if (_state.value.isPlaying)` sang `if (stateMachine.isActiveOrBuffering) pause()`. Khi có cuộc gọi đến lúc đang buffering tải chương → gọi `pause()` để chuyển sang `PausedDuringAutoAdvance` (tránh phát đè âm thanh lên cuộc gọi khi chương mới tải xong).
  - **Triển khai Auto-Advance, State Machine & Chống Hồi Quy Chuyển Chương (xử lý M-SM3, m-DUP, TC-03, TC-04, TC-10)**:
    - **Tầng App State (`TtsState`)**: Khi đọc xong đoạn cuối cùng của chương (`currentParagraphIndex == paragraphs.size - 1`) và `autoAdvance = true`: `_state.update { it.copy(isPlaying = false, currentParagraphIndex = lastIndex) }` để `ReaderViewModel.kt:329` nhận diện `justStopped == true` và kích hoạt `autoNextChapter()`.
    - **Tầng Media3 (`TtsPlayer`)**: Chuyển sang `Player.STATE_BUFFERING` với `playWhenReady = true`, tiếp tục giữ nguyên WakeLock và AudioFocus để `MediaSessionService` duy trì Foreground notification trong khoảng 1–3s khi ứng dụng tải nội dung chương mới.
    - **Ủy quyền Timeout duy nhất cho State Machine**: `stateMachine.startAutoAdvanceBuffering()` kích hoạt timer 10s. Nếu timeout, callback `onTimeout` chuyển `TtsPlayer` sang `Player.STATE_ENDED` và nhả WakeLock/AudioFocus an toàn (Media3 giữ notification ở trạng thái kết thúc và tự thoát foreground sau tối đa 10 phút, hoặc gọi `player.clearMediaItems()` nếu muốn hạ ngay).
    - **Bảo vệ đường gọi thật từ `ReaderViewModel.kt:448,455` (xử lý M-SM3a, m-DUP)**:
      * Trong `setContentForAutoAdvance()`: `TtsService` chỉ nạp danh sách đoạn mới ở `currentIndex = 0` và gọi `stateMachine.onAutoAdvanceContentLoaded()`. **TUYỆT ĐỐI KHÔNG TỰ GỌI `play()` hay `ttsPlayer.play()`** (loại bỏ hoàn toàn lệnh phát trùng lặp `m-DUP`).
      * Trong `play()`: Ở ngay đầu hàm, `TtsService` gọi `if (!stateMachine.onPlayRequested()) return`.
        - Nếu user bấm Pause lúc đang buffering: `stateMachine` vào `PausedDuringAutoAdvance`, hủy timer 10s. Khi nạp content xong, `onAutoAdvanceContentLoaded()` chuyển state về `Paused` và bật `suppressNextPlay = true`. Lời gọi `ttsManager.play()` từ `ReaderViewModel.kt:455` ngay sau đó sẽ gọi `onPlayRequested()`: do cờ bật, state machine xóa cờ và trả về `false`, khiến `TtsService.play()` return sớm, nuốt đúng lời gọi này và giữ nguyên `Paused`!
        - Khi user chủ động bấm Play lại sau đó: `onPlayRequested()` được gọi lúc `suppressNextPlay == false` → trả về `true` và phát bình thường.
        - Khi chuyển chương thủ công (Next/Prev button hoặc 2 chạm tai nghe) từ `Playing` (ReaderViewModel gọi `pause()` trước): state chuyển sang `Paused` thường (KHÔNG phải `PausedDuringAutoAdvance`), `suppressNextPlay` vẫn là `false`. Khi `ReaderViewModel.kt:455` gọi `play()`, `onPlayRequested()` trả về `true` và TTS tiếp tục phát chương mới liền mạch (bảo vệ tuyệt đối TC-03).
      * Mọi lời gọi `pause()`, `stop()`, `setContent()` (nạp thủ công) đều reset `suppressNextPlay = false` (và nếu đang `PausedDuringAutoAdvance` thì thoát về `Paused`/`Idle` thường).
  - Trong `onStartCommand()`:
    - BẮT BUỘC gọi `super.onStartCommand(intent, flags, startId)` ở đầu hàm.
    - Xóa bỏ `MediaButtonReceiver.handleIntent(...)` cũ và xóa bỏ các `ACTION_*` tự chế.
  - Trong `onDestroy()`:
    - Hủy `serviceScope.cancel()`, hủy media button handler, giải phóng silentPlayer, abandon audio focus, release wake lock.
    - Gọi `mediaSession?.run { player.release(); release() }; mediaSession = null`, `tts?.shutdown()`.
    - Cuối cùng gọi `super.onDestroy()`.
  - **Triển khai Dual `onBind` Chuẩn (xử lý M-M2, m-BIND)**:
    ```kotlin
    override fun onBind(intent: Intent?): IBinder? {
        // super.onBind(intent) trả về MediaSessionService Binder nếu intent khớp SERVICE_INTERFACE,
        // hoặc null nếu là intent nội bộ không có action Media3 -> trả về local binder cho TtsManager
        return super.onBind(intent) ?: binder
    }
    ```
- [ ] **Bước 3.4**: Cập nhật `TtsManager.kt`:
  - Thay thế `val ttsBinder = binder as TtsService.TtsBinder` bằng `val ttsBinder = binder as? TtsService.TtsBinder ?: return`. Log warning qua Timber nếu nhận binder không khớp kiểu.
  - Giữ nguyên toàn bộ chữ ký API công khai (`play()`, `pause()`, `stop()`, `setContent()`, `setSpeed()`, `skipNext()`, v.v.) để bảo đảm tính tương thích tuyệt đối cho `ReaderViewModel`.
- [ ] **Bước 3.5**: Cập nhật `app/src/main/AndroidManifest.xml` (xử lý m-MF):
  - **Giữ nguyên `android:exported="false"`** cho `TtsService` (app cá nhân, hệ thống Android điều phối playback qua MediaSession token nội bộ).
  - Giữ cả 2 intent-filters cho `TtsService`:
    ```xml
    <intent-filter>
        <action android:name="androidx.media3.session.MediaSessionService" />
        <action android:name="android.intent.action.MEDIA_BUTTON" />
    </intent-filter>
    ```
  - Xóa receiver `androidx.media.session.MediaButtonReceiver`.
- [ ] **Bước 3.6**: Cập nhật `app/proguard-rules.pro` cho Media3 (xử lý m-R8, tinh gọn, không blanket keep):
  ```proguard
  # Media3 Session rules (tinh gọn)
  -keepclassmembers class * extends androidx.media3.session.MediaSessionService {
      <init>();
  }
  -keep class androidx.media3.session.MediaSessionService
  -keep class androidx.media3.session.MediaSession
  -keep class androidx.media3.common.MediaMetadata
  ```
- [ ] **Bước 3.7**: Chạy `.\gradlew.bat compileDebugKotlin` để xác nhận toàn bộ mã nguồn biên dịch sạch với Media3.

#### Phase 4: Unit Testing, Quality Gate & Documentation
- [ ] **Bước 4.1**: Tách state machine & viết unit test thuần JVM JUnit 5 (xử lý M-T2, M-SM3, M6'):
  - Tạo file production thuần Kotlin: `app/src/main/java/com/personal/apptruyen/tts/TtsPlaybackStateMachine.kt` (`[NEW]`), độc lập với Android framework, Context và Handler/Looper. Nhận `CoroutineScope` qua constructor để quản lý timer 10s.
  - Mô hình hóa các trạng thái playback: `Idle`, `Playing`, `Paused`, `BufferingAutoAdvance`, `PausedDuringAutoAdvance`, `Ended`.
  - Khai báo thuộc tính và hàm hỗ trợ:
    * `val isActiveOrBuffering: Boolean get() = state == State.Playing || state == State.BufferingAutoAdvance`
    * `private var suppressNextPlay = false`
    * `fun onAutoAdvanceContentLoaded()`
    * `fun onPlayRequested(): Boolean`
  - Tạo file unit test: `app/src/test/java/com/personal/apptruyen/tts/TtsPlaybackStateTest.kt` (`[NEW]`) chạy thuần JVM JUnit 5:
    - Sử dụng `kotlinx.coroutines.test.runTest` và virtual time (`TestScope`, `advanceTimeBy`).
    - Kiểm tra các chuyển trạng thái: `Idle` → `Playing` → `Paused` → `Idle`.
    - Kiểm tra luồng auto-advance buffering: khi đọc xong đoạn cuối cùng, chuyển sang `BufferingAutoAdvance` với `playWhenReady = true`.
    - Kiểm tra luồng nạp content mới trước 10s: gọi `onAutoAdvanceContentLoaded()` trong khoảng thời gian buffering → timer 10s bị hủy, trạng thái giữ `suppressNextPlay = false`. Tiếp nối bởi `onPlayRequested()` ⇒ trả về `true` và chuyển sang `Playing`.
    - Kiểm tra luồng timeout 10s: nếu trong 10s không có content mới → timer kích hoạt callback `onTimeout`, chuyển sang `Ended`.
    - **Kiểm tra 4 ca test bảo vệ đường gọi thật từ ViewModel, 1 chạm tai nghe, AudioFocus và chuyển chương thủ công (xử lý M-SM3, TC-03, TC-10)**:
      * **Ca 1 (Bảo vệ `nextChapter`/`prevChapter` & TC-03 tai nghe 2-chạm)**: State đang `Playing` → gọi `pause()` (vào `Paused` thường) → gọi `onAutoAdvanceContentLoaded()` (state `Paused`, `suppressNextPlay = false`) → gọi `onPlayRequested()` lần 1 (từ ViewModel) ⇒ trả về `true` và state chuyển sang `Playing` (tiếp tục phát).
      * **Ca 2 (Bảo vệ `navigateToChapter`)**: State đang `Idle` (sau khi gọi `stop()`) → gọi `onAutoAdvanceContentLoaded()` (state `Idle`, `suppressNextPlay = false`) → gọi `onPlayRequested()` lần 1 (từ ViewModel) ⇒ trả về `true` và state chuyển sang `Playing`.
      * **Ca 3 (TC-10 Race Condition trên đường gọi thật từ `ReaderViewModel.kt:448,455` - M-SM3a)**: State đang `BufferingAutoAdvance` → user gọi `pause()` (vào `PausedDuringAutoAdvance`), timer 10s bị hủy → gọi `onAutoAdvanceContentLoaded()` (state về `Paused` thường, bật `suppressNextPlay = true`) → gọi `onPlayRequested()` lần 1 (từ `ReaderViewModel:455`) ⇒ trả về `false`, state vẫn là `Paused` (nuốt đúng lệnh phát) → gọi `onPlayRequested()` lần 2 (user bấm Play lại) ⇒ trả về `true`, state chuyển sang `Playing`.
      * **Ca 4 (Sửa lối vào pause lúc buffering - M-SM3b)**: Đang ở `BufferingAutoAdvance`, kiểm chứng `stateMachine.isActiveOrBuffering == true`. Kiểm chứng cả 2 sự kiện: toggle play/pause 1 chạm tai nghe (`if (stateMachine.isActiveOrBuffering) pause()`) và AudioFocus loss (`AUDIOFOCUS_LOSS_TRANSIENT`) đều kích hoạt `pause()` và chuyển state machine sang `PausedDuringAutoAdvance`.
- [ ] **Bước 4.2**: Bổ sung unit test atomic deletion trong `DownloadRepositoryTest.kt` (xử lý M-T1b, m-MK, m-NAME, m-CTOR, M-B2, TC-09):
  - Tạo file: `app/src/test/java/com/personal/apptruyen/data/repository/DownloadRepositoryTest.kt` (`[NEW]`) chạy trên JVM JUnit 5 với MockK.
  - Inject mock `DatabaseTransactionRunner`: mock thực thi lambda block trực tiếp (`coEvery { transactionRunner.withWriteTransaction<Any>(any()) } coAnswers { (firstArg() as suspend () -> Any).invoke() }`).
  - **Không cần mock `AppDatabase`** vì đã gỡ bỏ hoàn toàn tham số `database: AppDatabase` khỏi constructor của `DownloadRepository` (xử lý m-CTOR).
  - Kiểm tra `DownloadRepository.deleteStory(storyId)`:
    - Test happy path: xác minh `transactionRunner.withWriteTransaction` được gọi, và bên trong block xác minh đúng thứ tự 3 DAO thật bằng `coVerifyOrder`:
      `progressDao.deleteProgress(storyId)` → `chapterDao.deleteChaptersByStory(storyId)` → `storyDao.deleteStory(storyId)`.
    - Test error case: Giả lập `storyDao.deleteStory` ném ngoại lệ (`SQLiteException`), kiểm chứng exception được rethrow lên caller và transaction không nuốt lỗi.
- [ ] **Bước 4.3**: Viết instrumented test Room 3 Migration & Physical Rollback trên Android runtime (xử lý M-T1b, M-T3, m-TN, TC-06, TC-09, M6'):
  - Tạo file instrumented test: `app/src/androidTest/java/com/personal/apptruyen/data/local/AppDatabaseMigrationTest.kt` (`[NEW]`).
  - Chạy trên Android runtime / giả lập với `BundledSQLiteDriver`.
  - Tận dụng cấu hình assets schemas (`sourceSets.androidTest.assets.srcDirs(files("$projectDir/schemas"))`) và dependency `room3-testing:3.0.3`.
  - Sử dụng `MigrationTestHelper` nạp schema v5 từ `5.json`, chạy tuần tự qua các migration `MIGRATION_5_6`, `MIGRATION_6_7`, `MIGRATION_7_8`, `MIGRATION_8_9` thực thi trên `SQLiteConnection`.
  - Xác minh các thay đổi schema:
    - v5→v6: Bảng `reading_stats` được tạo với đủ 4 cột (`storyId`, `chapterIndex`, `readAt`, `durationSeconds`).
    - v6→v7: Thêm cột `sourceId` với default `'truyencom'`.
    - v7→v8: Thêm cột `status` với default `''`.
    - v8→v9: Thêm 2 cột `storyReplacementsEnabled` và `storyCustomReplacements`.
  - **Kiểm chứng Rollback vật lý (TC-09, m-TN)**: Dựng DB in-memory thật với `BundledSQLiteDriver`, chèn 1 story + chapter + progress, chạy `db.withWriteTransaction` gọi `progressDao.deleteProgress` và `chapterDao.deleteChaptersByStory`, rồi ném `IllegalStateException` giả lập trước khi gọi `storyDao.deleteStory`. Sau đó assert cả story, chapter và progress vẫn còn nguyên vẹn trong DB (chứng minh transaction rollback vật lý thực sự).
  - Lệnh chạy: `.\gradlew.bat connectedDebugAndroidTest`.
- [ ] **Bước 4.4**: Xử lý ktlint & Quality Gate Tier 2 (xử lý M-Q1, m-BASE, M9'):
  - **Đối chiếu Baseline**: So sánh số lượng vi phạm ktlint với số đếm chính xác đã lưu từ Bước 1.0.
  - **Tiêu chuẩn Pass Quality Gate**:
    1. **Zero-Regression**: Tổng số vi phạm ktlint của codebase không được vượt quá con số baseline của Bước 1.0.
    2. **Tinh khiết cục bộ (Local Zero-Violation)**: Toàn bộ các file nằm trong scope của plan này BẮT BUỘC đạt zero-violation (0 lỗi ktlint), bao gồm:
       - `app/src/main/java/com/personal/apptruyen/di/AppModule.kt` (đặc biệt sửa sạch lỗi ở dòng 46-47).
       - `app/src/main/java/com/personal/apptruyen/data/local/AppDatabase.kt`.
       - `app/src/main/java/com/personal/apptruyen/data/local/DatabaseTransactionRunner.kt`.
       - `app/src/main/java/com/personal/apptruyen/data/repository/DownloadRepository.kt`.
       - `app/src/main/java/com/personal/apptruyen/tts/TtsPlayer.kt`.
       - `app/src/main/java/com/personal/apptruyen/tts/TtsService.kt`.
       - `app/src/main/java/com/personal/apptruyen/tts/TtsManager.kt`.
       - `app/src/main/java/com/personal/apptruyen/tts/TtsPlaybackStateMachine.kt`.
       - `app/src/test/java/com/personal/apptruyen/tts/TtsPlaybackStateTest.kt`.
       - `app/src/test/java/com/personal/apptruyen/data/repository/DownloadRepositoryTest.kt`.
       - `app/src/androidTest/java/com/personal/apptruyen/data/local/AppDatabaseMigrationTest.kt`.
    3. **Tuân thủ Minimal Diffs Policy**: Tuyệt đối không format lan man sang các file ngoài scope để tránh rủi ro diff nhiễu.
    4. **Unit Tests**: 100% tests chạy qua: `.\gradlew.bat testDebugUnitTest`.
    5. **Biên dịch APK Debug**: `.\gradlew.bat assembleDebug` thành công.
- [ ] **Bước 4.5**: Cập nhật tài liệu kỹ thuật (`.agents/rules/00-core.md`, `docs/architecture.md`, `docs/progress.md`, `docs/changelog.md`, `docs/plans/README.md`) theo `.agents/rules/06-docs.md`.

---

### 7. Test Contract

| # | Hành vi nghiệp vụ (Business Behavior) | Điều kiện kích hoạt (Input) | Kết quả mong đợi / Thông báo lỗi tiếng Việt | Boundary / Failure Mode |
|---|---|---|---|---|
| **TC-01** | Khởi tạo MediaSession Media3 khi bật đọc TTS | User nhấn nút "Phát" hoặc gọi `TtsManager.play()` | `TtsService` khởi động Foreground Service `mediaPlayback`, `mediaSession` active với 8 commands, Timeline có ít nhất 1 `MediaItem`, `DefaultMediaNotificationProvider` của Media3 hiển thị notification với tiêu đề truyện, tiêu đề chương và đầy đủ các nút điều khiển. | Nếu khởi tạo TextToSpeech engine thất bại → UI hiện: `"Không thể khởi tạo TTS."`. |
| **TC-02** | Điều khiển tai nghe Bluetooth 1 chạm (Play/Pause) | Nhấn nút Play/Pause trên tai nghe Bluetooth / TWS (`KEYCODE_MEDIA_PLAY_PAUSE`) 1 lần | Tạm dừng nếu đang đọc hoặc đang buffering (`stateMachine.isActiveOrBuffering == true`); tiếp tục đọc nếu đang dừng. `TtsState.isPlaying` và `TtsPlayer` cập nhật ngay lập tức. Hàm `onMediaButtonEvent` trả về `true` để chặn xử lý mặc định. | Chờ 400ms debounce để phân biệt với 2 chạm / 3 chạm. Khi đang buffering, bấm 1 chạm chuyển sang `PausedDuringAutoAdvance` thay vì gọi nhầm `play()`. |
| **TC-03** | Điều khiển tai nghe 2 chạm / Chuyển chương thủ công không bị nuốt lệnh (xử lý M-SM2, M-SM3) | Nhấn nút trên tai nghe 2 lần liên tiếp trong 400ms, hoặc bấm nút Next/Prev trên `ReaderScreen` | `TtsService` phát `ChapterSkipEvent.NEXT`, ReaderViewModel tiếp nhận và gọi `loadChapter(autoPlay = true)`. Khi nội dung nạp xong (`onAutoAdvanceContentLoaded()`), do thao tác chuyển chương bắt đầu từ `Playing` (chỉ chuyển sang `Paused` thông thường, KHÔNG phải `PausedDuringAutoAdvance`), `suppressNextPlay` vẫn là `false`. Khi `ReaderViewModel.kt:455` gọi `ttsManager.play()`, `stateMachine.onPlayRequested()` trả về `true` và `TtsService` tiếp tục đọc liền mạch chương mới. Hàm `onMediaButtonEvent` trả về `true`. | Ở chương cuối cùng → không phát lỗi, giữ nguyên vị trí. Thao tác Next/Prev thủ công tuyệt đối không bị nuốt lệnh phát. |
| **TC-04** | Tự động chuyển chương khi đọc hết (Auto-advance 2 tầng state) | TTS đọc xong đoạn cuối cùng của chương hiện tại và `autoAdvance = true` | (1) `TtsState` cập nhật `isPlaying = false`, `currentParagraphIndex = lastIndex` kích hoạt `ReaderViewModel.autoNextChapter()`.<br>(2) `TtsPlayer` chuyển sang `Player.STATE_BUFFERING` với `playWhenReady = true`, giữ WakeLock và AudioFocus, Media3 giữ nguyên Foreground notification.<br>(3) Khi có chương mới trước 10s, nạp text và tiếp tục đọc liền mạch. | Nếu quá 10s không có chương mới (mất mạng kéo dài), `TtsPlaybackStateMachine` kích hoạt callback `onTimeout` chuyển sang `Player.STATE_ENDED`, nhả WakeLock và AudioFocus an toàn (Media3 giữ notification ở trạng thái kết thúc và tự thoát foreground sau tối đa 10 phút, hoặc gọi `player.clearMediaItems()` nếu muốn hạ ngay). |
| **TC-05** | Nạp dữ liệu Room Database v9 với Room 3.0 & Bundled SQLite | Mở ứng dụng lần đầu sau khi cập nhật Room 3.0 | `AppDatabase` nạp thành công DB `apptruyen.db` với `BundledSQLiteDriver`, khởi tạo theo cây quyết định constructor Room 3 (nhánh chính factory lambda `{ AppDatabase_Impl() }`, nhánh dự phòng `@ConstructedBy`), nhận diện 5 entities và schema v9, toàn bộ danh sách truyện trong Thư viện hiển thị đầy đủ. | Nếu DB cũ v1–v4 → fallback destructive migration theo đúng cấu hình an toàn. |
| **TC-06** | Thực thi Room 3 Migration qua `SQLiteConnection` | Cập nhật từ DB version 5 lên version 9 | Các migration `MIGRATION_5_6`, `6_7`, `7_8`, `8_9` thực thi tuần tự hàm `override suspend fun onMigrate(connection: SQLiteConnection)` chạy các lệnh `connection.execSQL(...)` an toàn trên `BundledSQLiteDriver`, không ném ngoại lệ. | Nếu SQL syntax sai → throw exception rõ ràng, không nuốt lỗi. |
| **TC-07** | Không có giọng đọc tiếng Việt | Thiết bị chưa cài engine tiếng Việt | `TtsState.hasVietnameseVoice = false`, errorMessage được gán: `"Thiết bị chưa cài giọng đọc tiếng Việt."`. | UI hiển thị Dialog/Snackbar tiếng Việt hướng dẫn cài đặt. |
| **TC-08** | Bảo vệ Controller kết nối tới MediaSession (xử lý M-M3, m-API) | Ứng dụng bên thứ ba không xác định cố gắng kết nối `MediaController` tới `TtsService` | `onConnect` kiểm tra điều kiện an toàn (`session.isMediaNotificationController(controller) || controller.isTrusted || controller.packageName == packageName`). Từ chối controller không thỏa mãn (trả về `ConnectionResult.reject()`). Service giữ `exported="false"`. | Chỉ cấp quyền điều khiển cho chính ứng dụng, Media3 notification controller và system trusted UI. |
| **TC-09** | Xóa truyện nguyên tử (Atomic Story Deletion - xử lý M-T1b, m-MK, m-NAME, m-CTOR, m-TN) | Người dùng nhấn xóa truyện khỏi danh sách đã tải trong UI (`DownloadRepository.deleteStory(storyId)`) | `DownloadRepository.deleteStory` thực thi bên trong `transactionRunner.withWriteTransaction { ... }` (sử dụng `database.withWriteTransaction`), gọi tuần tự `progressDao.deleteProgress(storyId)` → `chapterDao.deleteChaptersByStory(storyId)` → `storyDao.deleteStory(storyId)` trong một transaction duy nhất. Constructor `DownloadRepository` không còn phụ thuộc `AppDatabase`. | Nếu lỗi giữa chừng (DAO ném ngoại lệ giả lập) → tự động rollback toàn bộ các bảng, rethrow exception lên caller, không để lại dữ liệu mồ côi (chương bị xóa mà truyện vẫn còn). |
| **TC-10** | Chặn Auto-Play khi User Pause trong lúc Buffering qua `PausedDuringAutoAdvance` & `suppressNextPlay` (xử lý M-SM3, m-DUP) | User bấm Pause (trên notification, 1 chạm tai nghe với `isActiveOrBuffering`, hoặc mất AudioFocus do cuộc gọi) trong lúc TTS đang `BufferingAutoAdvance` | `TtsPlaybackStateMachine` chuyển sang `PausedDuringAutoAdvance`, timer 10s bị hủy ngay. Khi `ReaderViewModel.kt:448` nạp content mới qua `setContentForAutoAdvance`, `TtsService` không tự phát mà gọi `onAutoAdvanceContentLoaded()`: state machine chuyển về `Paused` và bật `suppressNextPlay = true`. Khi `ReaderViewModel.kt:455` gọi `ttsManager.play()` ngay sau đó, `stateMachine.onPlayRequested()` xóa cờ và trả về `false`, nuốt đúng lời gọi này và giữ nguyên `Paused`. `TtsService` nạp nội dung ở câu 0 và KHÔNG phát. Khi user chủ động bấm Play lại sau đó, âm thanh phát bình thường. | TTS tuyệt đối không tự phát lại khi user vừa chủ động pause hoặc đang trong cuộc gọi điện thoại. Phân biệt hoàn toàn với thao tác chuyển chương chủ động (Next/Prev) ở TC-03. |

---

### 8. Verification Plan

#### 1. Unit Tests & Instrumented Tests
- `app/src/test/java/com/personal/apptruyen/tts/TtsPlaybackStateTest.kt` (`[NEW]`):
  - Unit test thuần JVM JUnit 5 kiểm thử `TtsPlaybackStateMachine` với `TestScope` và virtual time (`advanceTimeBy`).
  - Test chuyển trạng thái `Idle` → `Playing` → `Paused` → `Idle`.
  - Test auto-advance buffering: xác minh `playWhenReady` duy trì `true` khi ở `BufferingAutoAdvance`.
  - Test nạp content mới trước 10s: hủy timer 10s kịp thời, `suppressNextPlay = false`. Tiếp nối `onPlayRequested()` trả về `true`, quay lại `Playing`.
  - Test timeout 10s: timer kích hoạt callback `onTimeout`, chuyển về `Ended`.
  - **Test Race Condition trên đường gọi thật từ ViewModel:448,455 (TC-10, M-SM3a)**: Đang ở `BufferingAutoAdvance` → kích hoạt `pause()` → kiểm chứng state chuyển sang `PausedDuringAutoAdvance` và timer 10s bị hủy. Giả lập tiếp tục nhận content mới (`onAutoAdvanceContentLoaded()`) → kiểm chứng state về `Paused` và `suppressNextPlay = true`. Gọi `onPlayRequested()` lần 1 (từ ViewModel:455) ⇒ trả về `false`, state vẫn là `Paused` (nuốt lệnh phát). Gọi `onPlayRequested()` lần 2 (user bấm Play lại) ⇒ trả về `true`, state chuyển sang `Playing`.
  - **Test Sửa lối vào pause lúc buffering (M-SM3b)**: Đang ở `BufferingAutoAdvance`, kiểm chứng `stateMachine.isActiveOrBuffering == true`. Kiểm chứng cả 2 sự kiện: toggle play/pause 1 chạm tai nghe (`if (stateMachine.isActiveOrBuffering) pause()`) và AudioFocus loss (`AUDIOFOCUS_LOSS_TRANSIENT`) đều kích hoạt `pause()` và chuyển state sang `PausedDuringAutoAdvance`.
  - **Test Chuyển chương thủ công từ Playing (M-SM2, M-SM3 chống hồi quy TC-03)**: Đang ở `Playing` → user bấm Next / 2 chạm tai nghe (ReaderViewModel gọi `pause()`) → kiểm chứng state chuyển sang `Paused` thông thường (KHÔNG phải `PausedDuringAutoAdvance`). Khi tải xong chương mới (`onAutoAdvanceContentLoaded()`) → `suppressNextPlay` vẫn là `false`. Khi ViewModel:455 gọi `onPlayRequested()` → kiểm chứng trả về `true`, state chuyển sang `Playing` và tiếp tục phát liền mạch.
  - **Test Chuyển chương thủ công từ Idle (M-SM2 chống hồi quy)**: Đang ở `Idle` (ReaderViewModel gọi `stop()`) → khi tải xong chương mới (`onAutoAdvanceContentLoaded()`) → khi ViewModel:455 gọi `onPlayRequested()` → kiểm chứng trả về `true`, state chuyển sang `Playing`.
- `app/src/test/java/com/personal/apptruyen/data/repository/DownloadRepositoryTest.kt` (`[NEW]`):
  - Unit test JVM JUnit 5 với MockK kiểm tra tính nguyên tử của `DownloadRepository.deleteStory` (TC-09, m-MK, m-NAME, m-CTOR).
  - Sử dụng mock `DatabaseTransactionRunner` (`mockk<DatabaseTransactionRunner>()`) thực thi block transaction trực tiếp qua `transactionRunner.withWriteTransaction`. Không cần mock `AppDatabase`.
  - Test case thành công: verify `transactionRunner.withWriteTransaction` được gọi, và bên trong block xác minh đúng thứ tự 3 DAO thật qua `coVerifyOrder`:
    `progressDao.deleteProgress(storyId)` → `chapterDao.deleteChaptersByStory(storyId)` → `storyDao.deleteStory(storyId)`.
  - Test case thất bại: mock DAO ném ngoại lệ (`SQLiteException`), verify exception được rethrow nguyên vẹn lên caller mà không bị nuốt lỗi.
- `app/src/test/java/com/personal/apptruyen/tts/TtsManagerTest.kt` (Hiện có):
  - Chạy lại toàn bộ 10 tests kiểm tra logic ngắt đoạn văn bản tiếng Việt.
- `app/src/test/java/com/personal/apptruyen/data/repository/StoryRepositoryTest.kt` (Hiện có):
  - Chạy lại toàn bộ 13 test cases đảm bảo tương thích 100% với DAO Room 3.0 và mock `AppDatabase`.
- `app/src/androidTest/java/com/personal/apptruyen/data/local/AppDatabaseMigrationTest.kt` (`[NEW]`, instrumented):
  - Chạy trên thiết bị / Android emulator với `BundledSQLiteDriver` và `room3-testing` (xử lý M-T1b, m-TN).
  - Nạp schema `5.json` từ assets.
  - Test thực thi migration v5→v6: bảng `reading_stats` được tạo với đủ 4 cột.
  - Test thực thi migration v6→v7: thêm cột `sourceId` với default `'truyencom'`.
  - Test thực thi migration v7→v8: thêm cột `status` với default `''`.
  - Test thực thi migration v8→v9: thêm 2 cột `storyReplacementsEnabled` và `storyCustomReplacements`.
  - **Test Rollback vật lý (TC-09, m-TN)**: Dựng DB in-memory thật với `BundledSQLiteDriver`, chèn 1 story + chapter + progress, chạy `db.withWriteTransaction` xóa `progressDao` và `chapterDao`, rồi ném `IllegalStateException` giả lập trước khi xóa `storyDao`. Sau đó assert cả story, chapter và progress vẫn còn nguyên vẹn trong DB (chứng minh transaction rollback vật lý thực sự).
  - Test `BundledSQLiteDriver` mở file DB thực tế.

#### 2. Lệnh Kiểm Thử Tự Động (Quality Gate)
```powershell
# Pre-flight Baseline (reset daemon và lấy baseline)
.\gradlew.bat --stop
.\gradlew.bat testDebugUnitTest
.\gradlew.bat ktlintCheck

# Tier 1: Kiểm tra biên dịch Kotlin
.\gradlew.bat compileDebugKotlin

# Kiểm tra sớm androidTest toolchain (Phase 2 gate)
.\gradlew.bat compileDebugAndroidTestKotlin

# Tier 2: Quality gate đầy đủ (ktlint + unit tests + build debug)
.\gradlew.bat ktlintCheck
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug

# Instrumented test (chạy khi có thiết bị / emulator kết nối)
.\gradlew.bat connectedDebugAndroidTest
```

#### 3. Quy Trình Kiểm Thử Thủ Công Trên Thiết Bị Thực Tế
1. **Kiểm tra TTS Media3 Notification & Background**:
   - Mở một chương truyện bất kỳ, bấm "Nghe".
   - Kéo thanh thông báo: xác nhận xuất hiện notification Media3 của `DefaultMediaNotificationProvider` với 8 commands, Timeline hợp lệ, các nút Trước, Phát/Dừng, Sau và text tiếng Việt rõ ràng.
   - Khóa màn hình: xác nhận TTS tiếp tục đọc liền mạch, không bị hệ thống kill (WakeLock & Foreground Service hoạt động).
2. **Kiểm tra Auto-Advance chuyển chương không ngắt âm thanh**:
   - Bật tính năng tự chuyển chương, nghe đến hết đoạn cuối của chương.
   - Xác nhận âm thanh không bị crash hay tắt notification, chuyển chương mượt mà trong 1-2s và tiếp tục đọc.
3. **Kiểm tra Pause trong lúc Auto-Advance Buffering (TC-10)**:
   - Nghe đến hết chương, ngay khi vừa đọc xong đoạn cuối (đang buffering chờ tải chương kế), bấm nút Pause trên notification hoặc tai nghe (hoặc gọi điện thoại vào máy).
   - Xác nhận âm thanh dừng lại, notification chuyển sang trạng thái tạm dừng, và khi app tải xong chương mới thì KHÔNG tự ý phát lại (lời gọi `play()` từ ViewModel bị nuốt chính xác).
4. **Kiểm tra Chuyển chương thủ công và Tai nghe Bluetooth / TWS (TC-03)**:
   - Kết nối tai nghe không dây. Bấm 1 lần → tạm dừng; bấm tiếp 1 lần → tiếp tục nghe.
   - Bấm 2 lần liên tục (hoặc bấm nút Next trên app) → xác nhận app chuyển sang chương tiếp theo và TIẾP TỤC ĐỌC LIỀN MẠCH (không bị nuốt lệnh phát, không bị double-tap seek của Media3 ghi đè).
5. **Kiểm tra Dữ Liệu Thư Viện (Room 3.0 & Bundled SQLite)**:
   - Vào tab "Thư viện": xác nhận toàn bộ truyện đã lưu trước đó vẫn còn nguyên vẹn.
   - Vào tab "Đã tải": mở một chương đã tải offline, bật chế độ máy bay (ngắt mạng) → xác nhận nội dung chương hiển thị và đọc được bình thường.
6. **Kiểm tra R8 Minify (Release Build)**:
   - Chạy lệnh `.\gradlew.bat assembleRelease` để đảm bảo ProGuard keep rules giữ nguyên các constructor và metadata của Room 3 và Media3.

---

### 9. Rủi Ro & Phòng Ngừa

| Rủi ro kỹ thuật | Mức độ | Technical Control (Biện pháp phòng ngừa trong code) |
|---|:---:|---|
| **Room 3 thiếu SQLiteDriver gây crash cold-start (B1)** | **Cao** | Bắt buộc gọi `.setDriver(androidx.sqlite.driver.bundled.BundledSQLiteDriver())` trong `AppModule.kt`. Cung cấp driver độc lập OS, nhất quán trên mọi phiên bản Android. |
| **API `withTransaction` bị gỡ trong Room 3 (B2, m-MK, m-NAME, m-CTOR)** | **Cao** | Chuyển toàn bộ cuộc gọi trong `DownloadRepository.kt` sang `transactionRunner.withWriteTransaction { ... }` bao bọc `database.withWriteTransaction`, bảo đảm tính atomic khi xóa truyện và các liên kết. Dùng interface mỏng `DatabaseTransactionRunner` giúp unit test trên JVM mock sạch sẽ không phụ thuộc bytecode KMP Room 3. Gỡ bỏ tham số `database: AppDatabase` khỏi constructor của `DownloadRepository` để test không cần mock AppDatabase. Có test TC-09 kiểm chứng rollback vật lý trên `androidTest`. |
| **Room 3.0 constructor linkage và RoomWarnings (M-R1, M1', m-RW)** | **Cao** | Áp dụng cây quyết định rõ ràng: Nhánh chính suppress RoomWarning (tra cứu tên hằng số chính xác từ `androidx.room3.RoomWarnings` hoặc log compiler KSP khi nâng cấp Phase 2) và truyền factory lambda `{ AppDatabase_Impl() }` vào `Room.databaseBuilder`; Nhánh dự phòng dùng `@ConstructedBy(AppDatabaseConstructor::class)`. Xóa bỏ hoàn toàn `AppDatabase::class.instantiateImpl()`. |
| **Media3 Notification thiếu Commands / Timeline trống gây rớt notification (M-M2, M4')** | **Cao** | Cung cấp đầy đủ 8 commands trong `TtsPlayer.availableCommands`. Luôn duy trì ít nhất 1 `MediaItem` trong timeline kèm `MediaMetadata` hợp lệ để `DefaultMediaNotificationProvider` render ổn định các action buttons. |
| **Auto-advance race condition và rớt Foreground Service (M-SM, M-SM2, M-SM3, m-DUP, M-M1, m-END)** | **Cao** | `TtsPlaybackStateMachine` là nơi DUY NHẤT giữ timer 10s, quản lý trạng thái `PausedDuringAutoAdvance`, cờ một lần `suppressNextPlay` và thuộc tính `isActiveOrBuffering`. Sửa 2 lối vào pause (1 chạm tai nghe, AudioFocus) dùng `isActiveOrBuffering` để chuyển đúng vào `PausedDuringAutoAdvance` khi đang buffering. Trong `setContentForAutoAdvance`, service không tự gọi `play()` (xóa bỏ `m-DUP`), chỉ gọi `onAutoAdvanceContentLoaded()`: state chuyển về `Paused` và bật `suppressNextPlay = true`. Khi `ReaderViewModel.kt:455` gọi `ttsManager.play()`, `stateMachine.onPlayRequested()` xóa cờ và trả về `false`, nuốt đúng lệnh phát này. Khi chuyển chương thủ công từ `Playing`/`Idle`, `suppressNextPlay` là `false` nên `onPlayRequested()` trả về `true` và tiếp tục phát liền mạch. Khi timeout 10s, chuyển sang `STATE_ENDED` và nhả WakeLock/AudioFocus an toàn (Media3 giữ notification tối đa 10 phút trước khi tự thoát foreground). |
| **Bảo mật Service và IPC unauthorized control (M-M3, m-API)** | **Trung bình** | Giữ nguyên `android:exported="false"` cho `TtsService`. Trong `sessionCallback.onConnect`, chỉ phê duyệt khi `session.isMediaNotificationController(controller) || controller.isTrusted || controller.packageName == packageName` sử dụng `AcceptedResultBuilder(session)` (hoặc chữ ký phù hợp của Media3 1.11.1). Từ chối mọi controller bên thứ ba. |
| **Lỗi Thread Looper khi cập nhật Media3 Player (M4)** | **Cao** | Mọi cuộc gọi `invalidateState()` hoặc cập nhật state từ TTS engine thread đều phải qua `Handler(Looper.getMainLooper()).post { ... }`, ngăn chặn `IllegalStateException` của Media3 ≥ 1.10. |
| **Double-tap tai nghe bị Media3 ghi đè (M7)** | **Trung bình** | Trong `MediaSession.Callback.onMediaButtonEvent`, tiêu thụ sự kiện và trả về `true` để chặn hành vi seek-to-next mặc định của Media3, bảo toàn cơ chế chuyển chương 2 chạm. |
| **Hạ tầng Test JVM vs Instrumented Test (M-T1b, M-T2, M-T3, m-RT, m-TN, m-MK)** | **Trung bình** | Tách logic state machine thuần Kotlin `TtsPlaybackStateMachine` để chạy 100% trên JVM JUnit 5 (hỗ trợ `TestScope` virtual time). Dùng `DatabaseTransactionRunner` để unit test `DownloadRepositoryTest.kt` độc lập bytecode KMP. Viết test rollback vật lý và migration SQLiteConnection trên `androidTest` trong `AppDatabaseMigrationTest.kt` với in-memory DB thật. Cổng `compileDebugAndroidTestKotlin` ở Bước 2.8 giúp fail-fast. |
| **ktlint Baseline và rủi ro vi phạm Quality Gate (M-Q1, m-BASE)** | **Trung bình** | Chạy reset daemon và lưu số lượng vi phạm baseline chính xác tại Bước 1.0. Áp dụng nguyên tắc Zero-Regression (tổng lỗi không vượt baseline) và Tinh khiết cục bộ (100% zero-violation trên các file trong scope, đặc biệt `AppModule.kt:46-47` và `DatabaseTransactionRunner.kt`). Tuyệt đối không format lan man ngoài scope (Minimal Diffs). |
| **R8 strip mất các lớp Room 3 và Media3 trong release build (M3, m-R8)** | **Trung bình** | Xóa rule cũ `androidx.media.**`. Thêm keep rules tinh gọn vào `app/proguard-rules.pro` cho `androidx.room3` và `androidx.media3` (`MediaSessionService`, `MediaSession`, `MediaMetadata`), không dùng blanket wildcard. |

---

### 10. Cập Nhật Docs

Sau khi triển khai hoàn tất, cập nhật đầy đủ các tài liệu theo quy tắc `.agents/rules/06-docs.md`:
- `.agents/rules/00-core.md`: Cập nhật mục Technology Stack bất biến từ "AndroidX Media (MediaSession)" thành "AndroidX Media3 (MediaSessionService + MediaSession)" và Room 3.0.
- `docs/architecture.md`:
  - Cập nhật mục "Dependencies chính": AGP 9.4.0, Gradle 9.6.0, Kotlin 2.4.21, Media3 1.11.1, Room 3.0.3, KSP 2.3.12, SQLite Bundled 2.7.1.
  - Cập nhật cấu trúc thư mục với các file mới: `DatabaseTransactionRunner.kt`, `TtsPlayer.kt`, `TtsPlaybackStateMachine.kt`, `AppDatabaseMigrationTest.kt`, `TtsPlaybackStateTest.kt`, `DownloadRepositoryTest.kt`.
  - Cập nhật sơ đồ luồng TTS với `MediaSessionService`, `SimpleBasePlayer` và `DefaultMediaNotificationProvider`.
  - Cập nhật mục Database Layer với kiến trúc Room 3.0 KMP, `BundledSQLiteDriver`, `withWriteTransaction` và `SQLiteConnection`.
  - Cập nhật timestamp.
- `docs/progress.md`:
  - Thêm entry vào mục "Nâng cấp (sau MVP)": "Nâng cấp kiến trúc lớn: Media3 Session (1.11.1), Room 3.0 KMP (3.0.3) + Bundled SQLite (2.7.1), Kotlin 2.4.21, AGP 9.4.0, Gradle 9.6.0".
  - Cập nhật timestamp và phiên bản.
- `docs/changelog.md`:
  - Thêm mục dưới `## [Unreleased]` với các phần: `### Thêm mới` (Media3 TtsPlayer, Room 3 BundledSQLiteDriver, DatabaseTransactionRunner, TtsPlaybackStateMachine), `### Sửa đổi` (Nâng cấp AGP 9.4, Kotlin 2.4, KSP 2.3.12, Room 3.0, TtsService MediaSessionService, withWriteTransaction), `### Xóa bỏ` (Gỡ bỏ `androidx.media:media` và `MediaSessionCompat`).
- `docs/plans/README.md`:
  - Cập nhật bảng kế hoạch với plan mới này.

---

## Audit History

<!-- Reviewer chỉ append "### Audit Run N" ở dưới. Không sửa phần Technical Plan. -->

### Audit Run 1

- **Reviewer**: Claude Code CLI (claude-opus-5-5)
- **Ngày**: 2026-10-13
- **Đối tượng**: Technical Plan Revision 1

#### Kiểm chứng lỗi cũ

| # | Lỗi từ Run trước | Trạng thái |
|---|---|---|
| — | Đây là Audit Run đầu tiên, chưa có lỗi cũ cần đối chiếu | N/A |

#### Xác minh Verified Versions Table (`[URL]`)

| Thành phần | Plan khẳng định | Kết quả xác minh | Nguồn |
|---|---|---|---|
| AGP | 9.4.0, cần Gradle 9.6.0, JDK 17 | ✅ Khớp: "AGP 9.4.0 (September 2026)… Gradle 9.6.0… JDK 17" | [URL] developer.android.com/build/releases/gradle-plugin |
| Kotlin | 2.4.21 | ✅ Khớp: "Kotlin 2.4.21 – Released October 8, 2026" | [URL] kotlinlang.org/docs/releases.html |
| Room 3 | `androidx.room3:room3-runtime/compiler:3.0.3`, plugin `androidx.room3` | ✅ Khớp tọa độ Maven và plugin id (3.0.3 stable, 09/09/2026) | [URL] developer.android.com/jetpack/androidx/releases/room3 |
| Media3 | 1.11.1 | ✅ Khớp (10/09/2026). minSdk Media3 = 23, tương thích `minSdk = 26` | [URL] developer.android.com/jetpack/androidx/releases/media3 |
| `androidx.sqlite:sqlite-bundled` | 2.5.0 là "Latest đã xác minh" | ❌ **SAI**: latest stable là **2.7.1** (09/09/2026) | [URL] developer.android.com/jetpack/androidx/releases/sqlite |
| KSP 2.3.12, kotlinx-serialization 1.11.0, Gradle 9.6.0 wrapper | Tương thích Kotlin 2.4 | UNVERIFIED: nguồn plan dẫn (github.com, mvnrepository.com, services.gradle.org) nằm ngoài danh sách domain reviewer được phép. Có cổng chặn ở Bước 1.4 (`compileDebugKotlin`) nên chấp nhận được nếu Phase 1 dừng hẳn khi fail | — |
| Room 2.x hiện hành | Plan coi 2.8.4 là bản hiện tại | Lưu ý: Room 2.x đã có 2.8.5 (09/09/2026) | [URL] developer.android.com/jetpack/androidx/releases/room |

#### Mental Simulation Matrix (S1–S6 + Domain)

| Kịch bản | Kết quả | Bằng chứng | Chi tiết |
|---|---|---|---|
| **S1: Build / KSP** | **FAIL** | [SRC] [URL] [CMD] | (a) `app/src/main/java/com/personal/apptruyen/data/repository/DownloadRepository.kt:4` import `androidx.room.withTransaction` và dùng ở `:198`. File này **không có trong bảng Files** của plan. Room 3 thay bằng `withWriteTransaction` ("New (Room 3.x): roomDatabase.withWriteTransaction { ... }", trang room3). Sau Bước 2.1 gỡ `androidx.room:*` thì compile hỏng. (b) Bước 2.4 khai báo `expect object AppDatabaseConstructor` trong module Android thuần (`app/build.gradle.kts:1-8` không có plugin `kotlin("multiplatform")`). `@Suppress("NO_ACTUAL_FOR_EXPECT")` không khiến `expect` hợp lệ ngoài KMP. Tài liệu room3 chỉ nhắc một `RoomWarning` thử nghiệm để bỏ `@ConstructedBy` và "a factory implementation must be provided via `DatabaseBuilder`". Plan chưa dẫn hướng dẫn chính thức cho Android-only → UNVERIFIED. (c) `app/proguard-rules.pro:11-13` keep `androidx.room.RoomDatabase/@Entity/@Dao`, còn `:42` keep `androidx.media.**`. Plan không cập nhật R8 cho `androidx.room3`/`androidx.media3` dù release bật `isMinifyEnabled = true` (`app/build.gradle.kts:50`). (d) `app/build.gradle.kts:94-96` vẫn có `ksp { arg("room.schemaLocation", ...) }`. Plan thêm `room { schemaDirectory }` nhưng không ghi bỏ arg cũ, có nguy cơ cấu hình trùng hoặc xung đột. Baseline: `[CMD] cmd.exe /d /c gradlew.bat compileDebugKotlin` Exit 0 (toolchain hiện tại 9.5.1). |
| **S2: Lifecycle / Service** | **FAIL** | [SRC] [URL] | (a) `TtsService.kt:113-132` override `onStartCommand` và tự xử lý `ACTION_*`. Với `MediaSessionService`, Media3 xử lý intent media button/foreground trong `onStartCommand` của lớp cha. Plan không yêu cầu gọi `super.onStartCommand` hoặc định tuyến lại `MediaButtonReceiver.handleIntent` (`:119`, sẽ biến mất). (b) `MediaSessionService` tự quản lý foreground notification qua `MediaNotification.Provider` (release notes 1.11: "Detect stale Intent instances that started the service as a foreground service… new required method" cho custom provider). Plan vẫn giữ `startForeground(NOTIFICATION_ID, buildNotification())` thủ công (`TtsService.kt:304, 435`) và `stopForeground` (`:332`) mà không chọn rõ: dùng `DefaultMediaNotificationProvider` hay custom provider. Hệ quả: hai notification, hoặc Media3 gỡ foreground ngay khi player chuyển sang `STATE_IDLE`/`STATE_ENDED` lúc auto-advance (mâu thuẫn TC-04). (c) `onDestroy` phải `mediaSession.release()` và `player.release()` trước `super.onDestroy()`. Plan không nêu. (d) Media3 ≥ 1.10: "state accessors… throw IllegalStateException if called from a thread other than the application looper". Callback `UtteranceProgressListener` chạy trên thread engine (`TtsService.kt:204-240`) nên mọi `invalidateState()`/getter của `TtsPlayer` phải post về main. Plan không ghi ràng buộc này. |
| **S3: Database** | **FAIL** | [SRC] [URL] [ADV] | (a) Room 3: "A `SQLiteDriver` is now _required_ to build a `RoomDatabase`" (trang room3). Bước 2.5 **không gọi `.setDriver(BundledSQLiteDriver())`** (hay `AndroidSQLiteDriver`). `build()` sẽ ném lỗi ngay khi Hilt tạo `AppDatabase` → app crash cold-start, người dùng không vào được Thư viện. Đây là BLOCKER. (b) Chưa đánh giá việc đổi engine từ framework SQLite sang bundled SQLite trên file `apptruyen.db` hiện có (WAL/journal mode, `PRAGMA user_version` = 9). Cần bước xác minh trên thiết bị có dữ liệu thật trước khi phát hành. (c) Test migration: plan đặt `AppDatabaseMigrationTest` trong `app/src/test` (JVM host). `sqlite-bundled` cho Android chỉ chứa native `.so` Android. Plan không thêm dependency driver cho JVM host (`testImplementation`) hay `room3-testing`/`MigrationTestHelper`, và không chỉ định cách dựng DB v5 từ `app/schemas/.../5.json`. Theo `01-coding.md:65-66`, test Room phức tạp nên đặt ở `androidTest`. Test Contract TC-06 hiện không khả thi như mô tả. |
| **S4: Cross-task state** | **FAIL** | [SRC] | (a) `TtsManager.kt:58` ép kiểu `binder as TtsService.TtsBinder`. Nếu `onBind` phân nhánh sai (vd. `Intent(context, TtsService::class.java)` tại `TtsManager.kt:119` không có action, OK; nhưng Media3 cũng bind nội bộ với action `MediaSessionService.SERVICE_INTERFACE`), lỗi cast sẽ crash. Plan cần dùng `as?` và test được nhánh này. (b) `TtsManager.kt:120` gọi `context.startService(intent)` khi khởi tạo singleton. Với Media3 + `exported=true`, service khởi động mà không có session/foreground; cần làm rõ. (c) Bước 3.5 "Đồng bộ `TtsManager.kt`" quá mơ hồ, không nêu thay đổi API công khai nào. Cần khẳng định rõ: API của `TtsManager` (được `ReaderViewModel` dùng) giữ nguyên chữ ký. (d) `StoryRepositoryTest.kt:6,41` mock `AppDatabase`. Cần xác nhận mock vẫn chạy với `RoomDatabase` của Room 3. |
| **S5: Security** | **FAIL** | [SRC] [ADV] | Bước 3.4 đổi `TtsService` từ `android:exported="false"` (`AndroidManifest.xml:34`) sang `exported="true"`. Hệ quả: (a) mọi app bên thứ ba có thể `startService` với `ACTION_STOP/PLAY/NEXT` (`TtsService.kt:121-130`) để điều khiển hoặc dừng TTS; (b) mọi app có thể kết nối `MediaController`. Media3 mặc định chấp nhận mọi controller nếu `Callback.onConnect` không lọc. Plan không có `onConnect` kiểm tra `controllerInfo.packageName` / `isTrusted` và không tách custom action khỏi `onStartCommand` công khai. Cần control rõ (vd. chỉ xử lý custom action khi intent đến từ `PendingIntent` nội bộ, hoặc chuyển các nút notification sang `CommandButton`/`SessionCommand` của Media3). |
| **S6: External resilience (TTS engine)** | PASS (có điều kiện) | [SRC] | Logic `hasVietnameseVoice` và thông báo tiếng Việt "Thiết bị chưa cài giọng đọc tiếng Việt." / "Không thể khởi tạo TTS." đã tồn tại tại `TtsService.kt:181, 247`. Plan không đổi phần này. Không chạm scraper. |
| **Domain: TTS / tai nghe** | **FAIL** | [SRC] [ADV] | Multi-tap 400ms hiện nằm trong `MediaSessionCompat.Callback.onMediaButtonEvent` (`TtsService.kt:593-628`). Media3 `MediaSession` có xử lý mặc định cho double-tap `KEYCODE_HEADSETHOOK`/`PLAY_PAUSE` (seek-to-next). Plan phải khẳng định override `MediaSession.Callback.onMediaButtonEvent` trả `true` để chặn xử lý mặc định, và nêu chữ ký API Media3 1.11 kèm `[URL]`. Hiện là UNVERIFIED. |

#### Adversarial Timelines

```
Cơ chế: Khởi tạo AppDatabase Room 3 (S3)
T1: User cập nhật app, mở lần đầu | T2: Hilt gọi provideDatabase() → Room.databaseBuilder<AppDatabase>(...).build() không có setDriver | T3: Room 3 ném IllegalArgumentException/IllegalStateException "SQLiteDriver required"
Kết cục: Crash cold-start lặp lại, người dùng không truy cập được Thư viện/Đã tải → LỖI (BLOCKER)
```

```
Cơ chế: Xóa truyện trong transaction (S3, trục Đồng thời + Thất bại giữa chừng)
T1: Reader đang ghi tiến độ (ReadingProgressDao) | T2: User xóa truyện → DownloadRepository.deleteStory() dùng withTransaction (Room 2 API, đã bị gỡ) | T3: Không biên dịch được; nếu dev "sửa nhanh" bằng cách bỏ transaction thì 3 lệnh delete chạy rời, crash giữa chừng để lại chapters/progress mồ côi
Kết cục: Phải chuyển sang withWriteTransaction và có test → hiện LỖI (MAJOR)
```

```
Cơ chế: TOCTOU điều khiển service từ app ngoài (S5)
T1: TtsService exported=true đang phát | T2: App bên thứ ba gửi Intent(action="com.personal.apptruyen.tts.STOP") tới service | T3: onStartCommand → stop(); stopSelf()
Kết cục: App lạ dừng/điều khiển TTS tùy ý, không có kiểm tra caller → LỖI (MAJOR)
```

```
Cơ chế: Auto-advance chương dưới MediaSessionService (S2, trục Vòng đời Android)
T1: TTS đọc xong đoạn cuối, TtsPlayer báo STATE_ENDED (hoặc IDLE) | T2: Media3 thấy player không còn phát → gỡ foreground/notification | T3: ReaderViewModel tải chương kế (mạng ~1–3s) rồi gọi setContentForAutoAdvance + play() → startForeground từ nền trên Android 12+
Kết cục: ForegroundServiceStartNotAllowedException (bị nuốt ở TtsService.kt:305-307), service có thể bị kill khi màn hình khóa → TC-04 FAIL → LỖI (MAJOR)
```

```
Cơ chế: Callback TTS engine cập nhật Player (S2, trục Đồng thời)
T1: UtteranceProgressListener.onDone chạy trên thread engine | T2: Dev gọi ttsPlayer.invalidateState()/đọc state trực tiếp | T3: Media3 ≥1.10 ném IllegalStateException khi getter gọi ngoài application looper
Kết cục: Crash ngẫu nhiên khi đang nghe nền nếu plan không bắt buộc post về main → LỖI (MAJOR, cần ràng buộc ghi rõ trong plan)
```

Trục **Thất bại của bước dọn dẹp**: `onDestroy` (`TtsService.kt:134-143`) gọi `super.onDestroy()` trước khi release. Với `MediaSessionService`, nên release session/player trước `super.onDestroy()`. Plan chưa nêu → MINOR, gộp vào yêu cầu S2.

#### Commands Executed (trích từ evidence.md)

- `### [CMD] cmd.exe /d /c gradlew.bat compileDebugKotlin`, `Exit: 0`, "BUILD SUCCESSFUL in 6s". Baseline toolchain hiện tại (Gradle 9.5.1) biên dịch được.
- `### [CMD] cmd.exe /d /c gradlew.bat testDebugUnitTest`, `Exit: 0`, "BUILD SUCCESSFUL in 1s… 34 actionable tasks: 34 up-to-date". Baseline unit test pass (kết quả cache).
- `### [CMD] cmd.exe /d /c gradlew.bat ktlintCheck`, `Exit: 1`, "KtLint found code style violations" tại `SourceAuthManager.kt`, `TangThuVienScraper.kt`, `WikidichScraper.kt`, `AppModule.kt:46-47`… Baseline ktlint **đang đỏ** sẵn, không do plan này gây ra. Tuy vậy, Bước 4.3 sẽ không thể pass Tier 2 nếu không có phương án (sửa trước hay chấp nhận baseline). Plan phải nêu rõ.
- `### [CMD] git status --short`, `Exit: 0`. Working tree còn rất nhiều thay đổi chưa commit (tính năng login/Wikidich). Nâng cấp lớn chồng lên đó khiến rollback khó.
- UNVERIFIED (đề xuất User chạy): `.\gradlew.bat dependencyInsight --dependency androidx.sqlite:sqlite-bundled --configuration debugRuntimeClasspath` (sau Phase 2, để xác nhận version thực resolve so với 2.5.0 khai báo).

#### Risk Table Audit (4.5.4)

| Rủi ro (mức) | Control trong plan | Đánh giá |
|---|---|---|
| KSP + Kotlin 2.4 + Hilt (Cao) | Cổng `compileDebugKotlin` ở Bước 1.4 | Chấp nhận (verification step có thật) |
| `@ConstructedBy` linkage (Cao) | `expect object` + plugin | **Không đạt**: control dựa trên cú pháp KMP chưa xác minh cho Android-only, thiếu `setDriver` |
| Mất Binder cục bộ (Cao) | Phân nhánh `onBind` theo `SERVICE_INTERFACE` | Thiếu test/verification cho nhánh này và cast `as` không an toàn → cần bước kiểm chứng |
| Focus âm thanh / tai nghe (TB) | Giữ silentPlayer + `onMediaButtonEvent` | UNVERIFIED với hành vi double-tap mặc định của Media3 |
| SQL migration (TB) | `AppDatabaseMigrationTest` JVM | Không khả thi như mô tả (thiếu driver JVM / MigrationTestHelper) |

#### Phân loại lỗi

- **[BLOCKER] B1**: Bước 2.5 thiếu `setDriver(...)`. Room 3 bắt buộc `SQLiteDriver` ([URL] room3 release notes). Kết quả là crash cold-start, người dùng không truy cập được dữ liệu. Phải thêm `.setDriver(BundledSQLiteDriver())` (hoặc `AndroidSQLiteDriver` từ `sqlite-framework`), ghi rõ lý do chọn và bước kiểm thử mở DB v9 thật.
- **[BLOCKER] B2**: Bỏ sót `DownloadRepository.kt:4,198` (`androidx.room.withTransaction`). Phải thêm vào bảng Files, chuyển sang `withWriteTransaction` (Room 3), và có test cho `deleteStory` vẫn atomic.
- **[MAJOR] M1**: Bước 2.4 dùng `expect object AppDatabaseConstructor` trong module không phải KMP. Cần dẫn `[URL]` chính thức cách dùng `@ConstructedBy` cho Android-only (hoặc phương án factory qua `DatabaseBuilder`) và bỏ `@Suppress` che lỗi.
- **[MAJOR] M2**: Verified Versions Table ghi sai `sqlite-bundled` 2.5.0 là "latest". Thực tế 2.7.1. Phải cập nhật version tương thích với Room 3.0.3 và dẫn nguồn.
- **[MAJOR] M3**: Thiếu cập nhật `app/proguard-rules.pro` (`:11-13` Room, `:42` `androidx.media.**`) cho `androidx.room3`/`androidx.media3`. Thiếu bước verify `assembleRelease`/R8 (do User chạy, vì reviewer không được chạy lệnh này).
- **[MAJOR] M4**: Thiết kế `MediaSessionService` chưa đầy đủ: chưa chọn chiến lược notification (`DefaultMediaNotificationProvider` hay custom), chưa xử lý `super.onStartCommand`, foreground khi auto-advance (TC-04), thứ tự release trong `onDestroy`, ràng buộc application looper cho `TtsPlayer`.
- **[MAJOR] M5 (S5)**: `exported="true"` mở `onStartCommand` (`ACTION_STOP/PLAY/NEXT/PREV`) và `MediaSession` cho mọi app. Cần `MediaSession.Callback.onConnect` lọc controller, và custom action chỉ chấp nhận từ `PendingIntent` nội bộ (hoặc chuyển sang `SessionCommand`). Bổ sung Test Contract tương ứng.
- **[MAJOR] M6**: Test Contract không khả thi trên JVM. `TtsPlayerTest` (`SimpleBasePlayer(Looper.getMainLooper())`) cần Looper thật, nhưng dự án không có Robolectric hay `unitTests.isReturnDefaultValues` ([SRC] grep `app/build.gradle.kts`: không có). `AppDatabaseMigrationTest` thiếu driver JVM / `room3-testing`. Cần chỉ định rõ: chuyển sang `androidTest` (instrumented, User chạy) hoặc thêm dependency test cụ thể đã xác minh version, và cách dựng DB v5 từ schema JSON.
- **[MAJOR] M7**: Multi-tap tai nghe dưới Media3 (TC-02/TC-03) chưa xác minh chữ ký `MediaSession.Callback.onMediaButtonEvent` của Media3 1.11.1, cũng như việc chặn double-tap mặc định. Cần `[URL]` và bước kiểm thử thủ công cụ thể (đã có một phần ở mục 8.3).
- **[MAJOR] M8 (Docs/quy tắc)**: `.agents/rules/00-core.md:34` ghi stack bất biến "AndroidX Media (MediaSession)". Plan đổi sang Media3 và Room 3 nhưng không liệt kê cập nhật `00-core.md`, và không gắn `[NEEDS_PRODUCT_DECISION]` cho việc thay stack bất biến. Cần User xác nhận và bổ sung file vào mục 10.
- **[MAJOR] M9**: Quality Gate Bước 4.3 chạy `ktlintCheck`, nhưng baseline đang Exit 1 (evidence). Plan phải nêu phương án: sửa baseline ở task riêng trước, hoặc chỉ chấp nhận ktlint trên các file plan chạm vào. Nếu không, Phase 4 không thể hoàn thành.
- **[MINOR] m1**: `app/build.gradle.kts:94-96` `ksp { arg("room.schemaLocation") }` cần được gỡ khi chuyển sang `room { schemaDirectory(...) }`. Ghi rõ trong Bước 2.1.
- **[MINOR] m2**: `TtsManager.kt:58` dùng `as` cứng. Nên đổi `as?` kèm log/báo lỗi tiếng Việt khi chuyển sang dual-binder.
- **[MINOR] m3**: Nên tách Phase 1 (toolchain) thành commit/plan riêng, vì working tree còn nhiều thay đổi chưa commit (evidence `git status --short`). Rollback Room 3 khó khi dữ liệu người dùng đã mở bằng engine mới.
- **[MINOR] m4**: Bảng "Version hiện tại" ghi Room 2.8.4 đúng với code, nhưng plan có thể ghi chú Room 2.8.5 là phương án dự phòng ít rủi ro nếu Room 3 bị hoãn.

**Kết luận**: Còn 2 BLOCKER và 9 MAJOR. Planner cần sửa Technical Plan (Revision 2) theo các mục trên rồi gửi review lại.

[CHANGES_REQUESTED]

### Audit Run 2

- **Reviewer**: Claude Code CLI (claude-opus-5-5)
- **Ngày**: 2026-10-13
- **Đối tượng**: Technical Plan Revision 3

#### Kiểm chứng lỗi cũ (so với Audit Run 1, kết luận CHANGES_REQUESTED)

| # | Lỗi Run 1 | Trạng thái | Bằng chứng |
|---|---|---|---|
| B1 | Thiếu `setDriver` | ✅ Đã sửa | Bước 2.6 bắt buộc `.setDriver(BundledSQLiteDriver())`. [URL] room3: "A `SQLiteDriver` is now _required_ to build a `RoomDatabase`" |
| B2 | Sót `DownloadRepository.kt:4,198` | ✅ Đã sửa (phần code) | Bảng Files + Bước 2.4. [SRC] `DownloadRepository.kt:197-203` (`withTransaction` bọc 3 lệnh delete). [URL] room3: `roomDatabase.withWriteTransaction { ... }`. Phần test xem M-T1 bên dưới |
| M1 | `expect object` trong module không KMP | ⚠️ Sửa một nửa | Đã bỏ `expect`, nhưng `object AppDatabaseConstructor { initialize() = AppDatabase_Impl() }` viết tay và "fallback reflection" vẫn chưa có nguồn. Xem M-R1 |
| M2 | Sai version `sqlite-bundled` | ✅ Đã sửa | Bảng version ghi 2.7.1 |
| M3 | ProGuard Room3/Media3 + verify R8 | ✅ Đã sửa | Bước 2.7, 3.6, mục 8.3.5 (`assembleRelease` do User chạy). Lưu ý m-R8 bên dưới |
| M4 | Thiết kế `MediaSessionService` chưa đủ | ⚠️ Phần lớn đã sửa | Đã có: `DefaultMediaNotificationProvider`, `super.onStartCommand`, thứ tự release trong `onDestroy` (khớp [URL] background-playback), ràng buộc Main Looper. Còn thiếu: tương tác auto-advance với `ReaderViewModel` và scope của timeout 10s. Xem M-M1, M-M2 |
| M5 | `exported="true"` | ✅ Đã sửa | Giữ `exported="false"` ([SRC] `AndroidManifest.xml:34`), bỏ `ACTION_*`. Tiêu chí `onConnect` còn mơ hồ, xem M-M3 |
| M6 | Test không khả thi trên JVM | ⚠️ Sửa một nửa | Migration test đã chuyển sang `androidTest`, nhưng thiếu dependency/wiring. `TtsPlaybackStateTest` không gắn với lớp production nào. Xem M-T2, M-T3 |
| M7 | Multi-tap dưới Media3 | ✅ Chấp nhận | [URL] control-playback: "media button events can be intercepted in `MediaSession.Callback.onMediaButtonEvent(Intent)`". Chữ ký 3 tham số được chặn bởi cổng compile Bước 3.7 và kiểm thử thủ công 8.3.3 |
| M8 | Cập nhật `00-core.md` | ✅ Đã sửa | Mục `[NEEDS_PRODUCT_DECISION]` #2 + mục 10 |
| M9 | ktlint baseline đỏ | ❌ Chưa sửa | Bước 4.4 tự mâu thuẫn. Xem M-Q1 |
| m1 | Gỡ `ksp { arg("room.schemaLocation") }` | ✅ Đã sửa | Bước 2.1 |
| m2 | `as` cứng ở `TtsManager.kt:58` | ✅ Đã sửa | Bước 3.4 |
| m3 | Tách Phase 1 | ✅ Một phần | Phương án A: 4 phase có cổng compile |
| m4 | Room 2.8.5 làm dự phòng | ✅ Đã ghi chú | Bảng version |

#### Mental Simulation Matrix (S1–S6 + Domain)

| Kịch bản | Kết quả | Bằng chứng | Chi tiết |
|---|---|---|---|
| **S1: Build / KSP** | **FAIL** | [URL] [SRC] [CMD] | (a) Bước 2.5 và Assumption 3.4 khẳng định `override fun migrate(connection: SQLiteConnection)` **không có `suspend`**. Trang room3 ghi ngược lại: "Room APIs that took `SQLiteStatement` as argument are now suspend functions. Example of these functions are `Migration.onMigrate()`, `RoomDatabase.Callback.onCreate()`…", tức là tên hàm là `onMigrate` và có `suspend`. Hiện tại `AppDatabase.kt:39,56,64,72` có 4 migration sẽ phải viết lại theo đúng chữ ký này. (b) Trang room3 nói `@ConstructedBy` là yêu cầu mặc định. Cách bỏ được ghi trong tài liệu là: "experimental `RoomWarning` … `DatabaseConstructor` will not be generated, and a factory implementation must be provided via `DatabaseBuilder`". Trang này **không nhắc reflection fallback**. Cách plan tự viết một `object` không phải `expect` để gán vào `@ConstructedBy` chưa có tài liệu xác nhận (UNVERIFIED). (c) AGP 9 dùng Kotlin tích hợp sẵn: `app/build.gradle.kts:1-8` không có `org.jetbrains.kotlin.android`, và root `build.gradle.kts:2-9` không khai báo KGP. Bước 1.2 chỉ nâng plugin compose/serialization lên 2.4.21, không có bước nào thực sự đưa KGP 2.4.21 vào classpath (xem m-KGP). (d) Baseline `compileDebugKotlin` Exit 0. |
| **S2: Lifecycle / Service** | **FAIL** | [SRC] [URL] [ADV] | (a) [SRC] `TtsService.kt` hiện **không có CoroutineScope** nào. Plan nói "timeout coroutine 10s" nhưng không nêu scope (`SupervisorJob` + `Dispatchers.Main`), không hủy trong `onDestroy` (danh sách Bước 3.3 thiếu), và không hủy timeout khi `setContentForAutoAdvance`/`play()` đến. (b) `onBind` mẫu ở Bước 3.3 bỏ giá trị của `super.onBind(intent)` và trả `null` cho `SERVICE_INTERFACE`. Comment "để MediaSessionService tự quản lý qua super" sai, vì binder session của Media3 bị vứt đi. Với action `android.media.browse.MediaBrowserService` (là action [URL] background-playback khuyến nghị khai báo), hàm lại trả `TtsBinder`. Cách đúng: `super.onBind(intent) ?: binder`. (c) [URL] background-playback: "The notification is created automatically when the player has `MediaItem` instances". Bước 3.2 chỉ khai báo `COMMAND_PLAY_PAUSE/STOP/SEEK_TO_NEXT/SEEK_TO_PREVIOUS/SET_SPEED_AND_PITCH`, thiếu `COMMAND_GET_CURRENT_MEDIA_ITEM`, `COMMAND_GET_TIMELINE`, `COMMAND_GET_METADATA`. Thiếu các lệnh này thì controller/notification không đọc được MediaItem và metadata, nên TC-01 (notification có tiêu đề truyện/chương) không đạt được. |
| **S3: Database** | **FAIL** | [URL] [SRC] | (a) Chữ ký migration sai so với tài liệu (S1a). Đây là đường dữ liệu v5→v9 của người dùng thật. (b) TC-09 "kiểm chứng rollback" bằng unit test JVM + MockK là mock vòng tròn: `withWriteTransaction` là extension `suspend` của Room, mock nó thì không có rollback thật để kiểm. Rollback chỉ kiểm được trên DB thật (`androidTest`). Thêm nữa, [Glob] `app/src/test/**/DownloadRepositoryTest.kt` **không tồn tại**, nhưng plan gắn nhãn `[MODIFY]`. (c) Thứ tự xóa trong transaction ([SRC] `DownloadRepository.kt:199-201`: progress → chapters → story) vẫn giữ nguyên, OK. (d) Plan dùng `fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4)` đúng với hiện trạng `AppModule.kt:74`. Destructive chỉ áp cho v1–v4, tuân thủ quy tắc S3. |
| **S4: Cross-task state** | **FAIL** | [SRC] [ADV] | [SRC] `ReaderViewModel.kt:326-334` chỉ kích hoạt `autoNextChapter()` khi `prevPlaying && !isPlaying && !isPaused && atLastParagraph`. Service không có cờ `autoAdvance` nào ([Grep] chỉ có `setContentForAutoAdvance`). Plan (Bước 3.3, TC-04) yêu cầu cuối chương thì "giữ `playWhenReady = true`, `STATE_BUFFERING`, giữ WakeLock/AudioFocus" nhưng không định nghĩa `TtsState.isPlaying` sẽ thành gì. Nếu dev đồng bộ `isPlaying` theo player (BUFFERING ⇒ đang phát) thì ViewModel không bao giờ thấy `justStopped`, auto-advance chết hẳn. Nếu giữ `isPlaying=false` thì phải ghi rõ hai trạng thái được tách nhau. Plan cũng chưa nêu điều kiện "`autoAdvance = true`" lấy từ đâu. API công khai `TtsManager` giữ nguyên (Bước 3.4), OK. `StoryRepositoryTest` mock `AppDatabase` chưa kiểm lại, chặn bởi Bước 4.4. |
| **S5: Security** | PASS (có điều kiện) | [SRC] [URL] | `exported="false"` giữ nguyên ([SRC] `AndroidManifest.xml:34`), bỏ `ACTION_*` công khai ([SRC] `TtsService.kt:121-130` sẽ bị xóa). Rủi ro còn lại nằm ở tính năng chứ không phải bảo mật: tiêu chí `onConnect` "hoặc system controller đáng tin cậy" quá mơ hồ. [URL] background-playback: "**Do not reject** the media notification controller connection—it will break your app" và "Use `ControllerInfo.isTrusted()` for basic access control". Controller legacy (Bluetooth/SystemUI qua platform session) có `packageName` khác app. Lọc sai sẽ làm hỏng TC-02/TC-03 (xem M-M3). |
| **S6: External resilience (TTS engine)** | PASS | [SRC] | Thông báo "Thiết bị chưa cài giọng đọc tiếng Việt." / "Không thể khởi tạo TTS." tại `TtsService.kt:181,247` được giữ (TC-01, TC-07). Không chạm scraper. |
| **Domain: TTS / tai nghe** | PASS (có điều kiện) | [SRC] [URL] | Logic multi-tap 400ms hiện tại ([SRC] `TtsService.kt:537-628`) được chuyển sang `MediaSession.Callback.onMediaButtonEvent`. Media3 cho phép chặn sự kiện tại đây ([URL] control-playback). Phụ thuộc vào M-M3 (không reject controller mang media button). |

#### Adversarial Timelines

```
Cơ chế: Auto-advance cuối chương (S4 + S2, trục Đồng thời/Vòng đời)
T1: TTS đọc xong đoạn cuối, onDone post về Main | T2: TtsPlayer chuyển STATE_BUFFERING + playWhenReady=true, dev đồng bộ TtsState.isPlaying=true để khớp player | T3: ReaderViewModel.kt:329 không thấy justStopped → không gọi autoNextChapter(); sau 10s timeout → STATE_ENDED
Kết cục: Mọi lần hết chương đều dừng hẳn, auto-advance hỏng hoàn toàn → LỖI (MAJOR, plan chưa định nghĩa mapping TtsState ↔ Player state)
```

```
Cơ chế: Timeout 10s vs chương mới đến muộn (S2, trục TOCTOU)
T1: t=9.9s, ReaderViewModel gọi setContentForAutoAdvance + play() | T2: t=10.0s, coroutine timeout (chưa bị hủy) chuyển STATE_ENDED, abandonAudioFocus, release WakeLock | T3: TTS đang đọc chương mới nhưng player ở ENDED → Media3 gỡ foreground; màn hình khóa → hệ thống có thể kill service
Kết cục: Mất foreground giữa chừng → LỖI (MAJOR, thiếu quy định hủy timeoutJob khi có nội dung mới / play())
```

```
Cơ chế: Service bị hủy khi timeout đang chờ (S2, trục Thất bại của bước dọn dẹp)
T1: Auto-advance đang BUFFERING, timeoutJob delay(10s) | T2: User vuốt tắt app → onDestroy: mediaSession.release(), player.release() | T3: timeoutJob chạy tiếp (scope không bị hủy) → gọi player đã release / abandonAudioFocus trên service đã chết
Kết cục: IllegalStateException hoặc rò rỉ coroutine → LỖI (MAJOR, onDestroy ở Bước 3.3 thiếu serviceScope.cancel())
```

```
Cơ chế: Lọc controller trong onConnect (S5 + Domain, trục Vòng đời/Hệ thống)
T1: User bấm nút tai nghe Bluetooth | T2: Hệ thống gửi KEYCODE qua platform session; Media3 gán cho controller legacy/media-notification có packageName ≠ app (hoặc package hệ thống) | T3: onConnect kiểm tra cứng packageName == packageName → reject
Kết cục: Nút tai nghe và controls trên SystemUI/lockscreen không hoạt động → LỖI (MAJOR). Cần dùng isMediaNotificationController(controller) || controller.isTrusted || packageName == packageName
```

```
Cơ chế: Xóa truyện trong withWriteTransaction (S3, trục Thất bại giữa chừng)
T1: deleteStory: progressDao.deleteProgress OK | T2: chapterDao.deleteChaptersByStory ném lỗi | T3: Room rollback → progress còn nguyên
Kết cục: AN TOÀN về thiết kế ([SRC] DownloadRepository.kt:198-202 + [URL] withWriteTransaction), nhưng unit test JVM với mock không chứng minh được điều này → test phải đặt ở androidTest
```

#### Commands Executed (trích từ evidence.md)

- `### [CMD] cmd.exe /d /c gradlew.bat compileDebugKotlin`, `Exit: 0`, "BUILD SUCCESSFUL in 6s / 8 actionable tasks: 8 up-to-date". Baseline Gradle 9.5.1 biên dịch sạch.
- `### [CMD] cmd.exe /d /c gradlew.bat testDebugUnitTest`, `Exit: 0`, "BUILD SUCCESSFUL in 1m 21s". Baseline unit test pass.
- `### [CMD] cmd.exe /d /c gradlew.bat ktlintCheck`, `Exit: 1`, "KtLint found code style violations". Vi phạm nằm trong nhiều file **ngoài phạm vi plan**: `SourceAuthManager.kt:60:60`, `AndroidCookieStore.kt:8:25`, `TangThuVienScraper.kt:3:1`, `WikidichScraper.kt:10:1`, `SourceLoginScreen.kt:5:1`, `SettingsViewModel.kt:100:21` ("cannot be auto-corrected")… Chỉ `AppModule.kt:46:72`, `:47:50` nằm trong phạm vi.
- `### [CMD] git status --short`, `Exit: 0`. Working tree còn nhiều thay đổi chưa commit (login/Wikidich, `gradle-wrapper.properties` đã `M`).
- UNVERIFIED (đề xuất chạy sau Phase 1): `.\gradlew.bat buildEnvironment` để xác nhận KGP thực tế là 2.4.21.

#### Risk Table Audit (4.5.4)

| Rủi ro (mức) | Control | Đánh giá |
|---|---|---|
| B1 thiếu driver (Cao) | `setDriver` Bước 2.6 + cổng compile + kiểm thử thủ công 8.3.4 | Đạt |
| B2 `withTransaction` (Cao) | `withWriteTransaction` | Đạt về code. Test TC-09 không chứng minh rollback (M-T1) |
| M1' constructor (Cao) | `object` viết tay | **Chưa xác minh** ([URL] chỉ nêu phương án factory qua `DatabaseBuilder`) → M-R1 |
| M4' notification (Cao) | `DefaultMediaNotificationProvider` | Thiếu `COMMAND_GET_*` cho MediaItem/metadata → M-M2 |
| M-AA auto-advance (Cao) | BUFFERING + timeout 10s | Thiếu scope/hủy timeout và mapping với `ReaderViewModel` → M-M1 |
| Thread Looper (Cao) | post về Main | Đạt (đã là pattern hiện có ở [SRC] `TtsService.kt:205,214,232`) |
| M7 double-tap (TB) | `onMediaButtonEvent` trả `true` | Đạt có điều kiện (M-M3) |
| M6' test (TB) | JVM + androidTest | Thiếu wiring → M-T2, M-T3 |

#### Phân loại lỗi

- **[MAJOR] M-R1 (Room 3 API, S1/S3)**: Chữ ký migration sai so với [URL] room3 (`Migration.onMigrate()` là `suspend`, nhận `SQLiteConnection`). Plan khẳng định ngược lại ("không có `suspend`", tên `migrate`). Cơ chế `@ConstructedBy` + `object` viết tay và "fallback reflection" chưa có nguồn chính thức. Cách duy nhất có tài liệu là suppress `RoomWarning` + truyền factory qua `DatabaseBuilder`. Yêu cầu: sửa chữ ký theo tài liệu, chọn một phương án constructor có `[URL]`, bỏ câu "fallback reflection".
- **[MAJOR] M-M1 (S2/S4, auto-advance)**: (1) Định nghĩa rõ mapping `TtsState.isPlaying/isPaused` với `Player` state lúc BUFFERING sao cho `ReaderViewModel.kt:326-334` vẫn phát hiện hết chương. Điều kiện "autoAdvance = true" cũng chưa tồn tại trong service. (2) Khai báo `serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)` trong `TtsService`, `cancel()` trong `onDestroy`. `timeoutJob` phải bị hủy ở `setContentForAutoAdvance`/`setContent`/`play()`/`stop()`. (3) Thêm Test Contract/test cho hai nhánh: chương mới đến trước 10s và timeout. Nhánh "chương cuối, không có chương kế" phải kết thúc ENDED sạch.
- **[MAJOR] M-M2 (S2, Media3 player/binder)**: (1) Bổ sung `COMMAND_GET_CURRENT_MEDIA_ITEM`, `COMMAND_GET_TIMELINE`, `COMMAND_GET_METADATA` và playlist ít nhất 1 `MediaItem`, để notification tự tạo có tiêu đề (TC-01, [URL] background-playback). (2) Sửa mẫu `onBind` thành `super.onBind(intent) ?: binder`. Không trả `null` cho `SERVICE_INTERFACE`, không trả `TtsBinder` cho action media browser.
- **[MAJOR] M-M3 (S5/Domain)**: Tiêu chí `onConnect` phải cụ thể: chấp nhận `session.isMediaNotificationController(controller)`, `controller.isTrusted` hoặc `controller.packageName == packageName`; reject phần còn lại. Theo [URL] background-playback, reject notification controller "will break your app". Sửa TC-08 cho khớp ("chính ứng dụng và system UI" → tiêu chí trên).
- **[MAJOR] M-T1 (Test Contract TC-09)**: `DownloadRepositoryTest.kt` chưa tồn tại (đổi nhãn sang `[NEW]`). Rollback của `withWriteTransaction` không kiểm được bằng mock JVM (mock vòng tròn, vi phạm S2). Chuyển phần rollback sang `androidTest` với DB thật (in-memory + `BundledSQLiteDriver`). Unit test JVM chỉ kiểm thứ tự gọi DAO và việc ném lại exception.
- **[MAJOR] M-T2 (Test Contract TTS)**: `TtsPlaybackStateTest` yêu cầu "tách logic state machine" nhưng bảng Files không có lớp production `[NEW]` nào (ví dụ `tts/TtsPlaybackStateMachine.kt` thuần Kotlin, nhận `CoroutineDispatcher`/`TestScope` để kiểm timeout 10s bằng virtual time). Thiếu lớp này thì test không có đối tượng thật để kiểm, và `TtsService`/`TtsPlayer` không dùng logic đã test.
- **[MAJOR] M-T3 (Migration test, M6 còn sót)**: Repo **chưa có thư mục `app/src/androidTest`**. `app/build.gradle.kts:171-173` chỉ có `ext:junit`, `espresso`, `ui-test-junit4`. Plan phải thêm dependency test Room 3 (artifact testing/`MigrationTestHelper` của room3, version có `[URL]`), cấu hình schema JSON làm assets cho androidTest, và nêu cách dựng DB v5 từ `app/schemas/.../5.json` (cần xác nhận file 5.json tồn tại). Nếu không có helper chính thức thì ghi phương án tạo DB v5 bằng SQL thủ công.
- **[MAJOR] M-Q1 (M9 chưa sửa)**: Bước 4.4 vừa nói "Minimal Diffs, chỉ sửa vi phạm trong scope" vừa yêu cầu "`ktlintCheck` zero-violation trên **toàn bộ codebase**". Evidence cho thấy baseline Exit 1 với hơn 150 vi phạm ở file ngoài scope (có vi phạm "cannot be auto-corrected" ở `SettingsViewModel.kt:100`). Phase 4 sẽ không thể hoàn thành. Chọn một trong hai: (a) tiêu chí pass là "không thêm vi phạm mới; các file plan chạm vào sạch", kiểm bằng so sánh report trước/sau; hoặc (b) tách task sửa ktlint baseline làm điều kiện tiên quyết.
- **[MINOR] m-KGP**: AGP 9 dùng built-in Kotlin. Plan chưa có bước pin KGP 2.4.21 (vd. `id("org.jetbrains.kotlin.android") version "2.4.21" apply false` hoặc classpath theo hướng dẫn AGP) và chưa có bước xác nhận bằng `buildEnvironment`. Cổng Bước 1.4 sẽ bắt lỗi lệch version compose compiler, nên không chặn.
- **[MINOR] m-R8**: `room3-runtime` thường kèm consumer rules. Rule `-keep class * extends androidx.room3.RoomDatabase` + `-keep @androidx.room3.Entity class *` có thể thừa. Giữ thì được, nhưng nên gỡ `-keep class androidx.media.** { *; }` (`proguard-rules.pro:41-42`) khi bỏ `androidx.media`.
- **[MINOR] m-MF**: Bước 3.5 không nói rõ có giữ intent-filter `android.intent.action.MEDIA_BUTTON` trên `TtsService` (`AndroidManifest.xml:35-37`) hay không. Nên ghi rõ.

**Kết luận**: B1, B2, M2, M3, M5, M7, M8 đã được khắc phục. Còn 8 MAJOR (M-R1, M-M1, M-M2, M-M3, M-T1, M-T2, M-T3, M-Q1; trong đó M-Q1 là M9 chưa sửa), không còn BLOCKER. Planner cần ra Revision 4 xử lý các mục trên rồi gửi review lại.

[CHANGES_REQUESTED]

### Audit Run 3

- **Reviewer**: Claude Code CLI (claude-opus-5-5)
- **Ngày**: 2026-10-14
- **Đối tượng**: Technical Plan Revision 4

#### Kiểm chứng lỗi cũ (so với Audit Run 2, kết luận CHANGES_REQUESTED)

| # | Lỗi Run 2 | Trạng thái | Bằng chứng |
|---|---|---|---|
| M-R1 | Chữ ký migration + cơ chế constructor Room 3 | ❌ Chưa sửa | (a) Plan vẫn lấy `override suspend fun migrate(connection)` làm chính, `onMigrate` chỉ là "hoặc". [URL] room3: "migrations callback functions such as `Migration.onMigrate()`… replaced by their equivalent API that has a `SQLiteConnection` as argument". (b) Plan dùng **đồng thời** `@ConstructedBy(AppDatabaseConstructor::class)` + `object` viết tay (Bước 2.5) **và** factory lambda `{ AppDatabase::class.instantiateImpl() }` (Bước 2.6, TC-05, Rủi ro). [URL] room3 **không có** `instantiateImpl` ở bất kỳ đâu. Tài liệu chỉ có một cách bỏ `@ConstructedBy`: "experimental `RoomWarning`… `DatabaseConstructor` will not be generated, and a factory implementation must be provided via `DatabaseBuilder`". Hai cơ chế loại trừ nhau, và một trong hai dùng API không có trong tài liệu |
| M-M1 | Mapping auto-advance + scope timeout | ✅ Phần lớn đã sửa | Assumption 5, Bước 3.3: `TtsState.isPlaying = false` khớp [SRC] `ReaderViewModel.kt:329-333` (`justStopped && atLastParagraph` → `autoNextChapter()`). `serviceScope` + `SupervisorJob`, hủy trong `onDestroy`, hủy khi `setContent`/`setContentForAutoAdvance`/`play`/`pause`/`stop`. Còn sót cạnh pause trong lúc buffering, xem M-SM |
| M-M2 | `COMMAND_GET_*` + `onBind` | ✅ Đã sửa | Bước 3.2 có đủ 8 commands và luôn có 1 `MediaItem`. Bước 3.3 dùng `super.onBind(intent) ?: binder`. [SRC] `TtsService.kt:103` hiện trả `binder` cố định |
| M-M3 | Tiêu chí `onConnect` | ✅ Đã sửa | Bước 3.3, TC-08 khớp [URL] background-playback ("Rejecting an attempt of the media notification controller to connect, causes your app to not work properly"; "use `ControllerInfo.isTrusted()` for a basic access check") |
| M-T1 | `DownloadRepositoryTest` + rollback | ⚠️ Sửa một nửa | Đã đổi sang `[NEW]` và tách phạm vi JVM/androidTest. Nhưng tên DAO trong test sai so với code, và không có test rollback cụ thể nào trong androidTest. Xem M-T1b |
| M-T2 | Lớp production cho state machine | ⚠️ Sửa một nửa | Đã thêm `[NEW] TtsPlaybackStateMachine.kt`. Nhưng timeout 10s vừa nằm trong state machine (bảng Files) vừa là `autoAdvanceTimeoutJob` trong `TtsService` (Bước 3.3). Không nói rõ service ủy quyền cho state machine. Xem M-SM |
| M-T3 | Wiring androidTest Room 3 | ✅ Chấp nhận (có điều kiện) | Bước 1.3 thêm `assets.srcDirs(files("$projectDir/schemas"))` và `room3-testing:3.0.3`. [Glob] `app/schemas/com.personal.apptruyen.data.local.AppDatabase/5.json` … `9.json` tồn tại. Artifact `room3-testing` **không xuất hiện** trên trang [URL] room3 (chỉ có runtime, compiler, sqlite-wrapper, paging, rxjava3, guava, livedata). Xem m-RT |
| M-Q1 | ktlint baseline | ✅ Đã sửa | Assumption 7, Bước 4.4 chọn phương án "không thêm vi phạm mới + file trong scope sạch". [CMD] ktlintCheck cho thấy `AppModule.kt:46:72`, `:47:50` là vi phạm duy nhất trong scope, khớp với plan |
| m-KGP | Pin KGP | ✅ Đã sửa | Bước 1.2 + `buildEnvironment` ở Bước 1.4 |
| m-R8 | Gỡ `androidx.media.**` | ✅ Đã sửa | Bước 2.7 |
| m-MF | Intent-filter `MEDIA_BUTTON` | ✅ Đã sửa | Bước 3.5 |

#### Mental Simulation Matrix (S1–S6 + Domain)

| Kịch bản | Kết quả | Bằng chứng | Chi tiết |
|---|---|---|---|
| **S1: Build / KSP** | **FAIL** | [URL] [SRC] [CMD] | Cơ chế constructor Room 3 còn mâu thuẫn và dựa vào `instantiateImpl()`, API không có trong [URL] room3 (M-R1). Cổng `compileDebugKotlin` ở Bước 2.8 sẽ bắt lỗi, nhưng plan không cho người triển khai một đường đi đã xác minh nên họ sẽ phải đoán. Gói `androidx.room3.*` khớp [URL] ("classes such as `androidx.room.RoomDatabase` will now be located at `androidx.room3.RoomDatabase`"). Plugin `androidx.room3` + `schemaDirectory` khớp [URL]. Baseline `compileDebugKotlin` Exit 0. |
| **S2: Lifecycle / Service** | PASS (có điều kiện) | [SRC] [URL] | Thứ tự `onDestroy` (hủy job → release player/session → `super.onDestroy()`), `super.onStartCommand`, post về Main Looper (pattern sẵn có tại [SRC] `TtsService.kt:205,214,232`) đều đã có. [URL] background-playback: "If the player is paused, stopped or failed for more than 10 minutes… the service is automatically transitioned out of the foreground service state". Như vậy BUFFERING + `playWhenReady` và cả `pause()` trước khi chuyển chương thủ công ([SRC] `ReaderViewModel.kt:540-542`) vẫn giữ foreground. Lưu ý: chuyển sang `STATE_ENDED` khi timeout **không** nhả foreground ngay, mà Media3 giữ thêm tối đa 10 phút. Mô tả "nhả Foreground" ở TC-04 chưa chính xác (m-END). |
| **S3: Database** | **FAIL** | [SRC] [ADV] | Thứ tự xóa thật là `progressDao.deleteProgress` → `chapterDao.deleteChaptersByStory` → `storyDao.deleteStory` ([SRC] `DownloadRepository.kt:198-201`). Bước 4.2 lại kiểm `downloadDao.deleteChaptersByStoryId` và `downloadDao.deleteStory`. Không có `downloadDao` nào, và test bỏ sót `progressDao`. TC-09 hứa "tự động rollback… không để lại dữ liệu mồ côi", nhưng Bước 4.3 và mục 8.1 không có ca test rollback nào trong `AppDatabaseMigrationTest`, dù bảng Files có nhắc tới. Migration v5→v9 có test instrumented, destructive chỉ áp cho v1–v4 ([SRC] `AppModule.kt:74`), OK. |
| **S4: Cross-task state** | **FAIL** | [SRC] [ADV] | Mapping `isPlaying=false` ở cuối chương đúng với [SRC] `ReaderViewModel.kt:326-333`. Nhưng [SRC] `ReaderViewModel.kt:445-455`: sau khi tải xong chương, `loadChapter(autoPlay=true)` **luôn** gọi `setContentForAutoAdvance` rồi `ttsManager.play()`. Plan chỉ hủy timeout khi user pause trong lúc BUFFERING, không chặn lần `play()` tự động đến sau. Xem timeline bên dưới (M-SM). API công khai của `TtsManager` giữ nguyên (Bước 3.4), `as?` thay `as` ([SRC] `TtsManager.kt:58`), OK. |
| **S5: Security** | PASS | [SRC] [URL] | `exported="false"` giữ nguyên ([SRC] `AndroidManifest.xml:34`), các `ACTION_*` công khai bị xóa, `onConnect` lọc theo `isMediaNotificationController \|\| isTrusted \|\| packageName`, khớp [URL]. Không chạm cookie/token. |
| **S6: External resilience (TTS engine)** | PASS | [SRC] | Thông báo "Không thể khởi tạo TTS." ([SRC] `TtsService.kt:247`) và "Thiết bị chưa cài giọng đọc tiếng Việt." được giữ (TC-01, TC-07). Mất mạng khi tải chương kế có timeout 10s xử lý (TC-04). |
| **Domain: TTS / tai nghe** | PASS (có điều kiện) | [URL] [SRC] | Multi-tap 400ms trong `onMediaButtonEvent` trả `true`. Phụ thuộc M-SM: 1 chạm Pause trong lúc buffering (sau debounce 400ms) phải được tôn trọng. |

#### Adversarial Timelines

```
Cơ chế: User pause trong lúc auto-advance đang BUFFERING (S4, trục TOCTOU)
T1: Đọc xong đoạn cuối → TtsState.isPlaying=false, TtsPlayer BUFFERING + playWhenReady=true; ReaderViewModel.autoNextChapter() bắt đầu tải mạng | T2: t=1s, user bấm Pause trên notification hoặc 1 chạm tai nghe (hoặc mất AudioFocus do cuộc gọi đến) → handleSetPlayWhenReady(false) → hủy autoAdvanceTimeoutJob | T3: t=2s, tải xong → ReaderViewModel.kt:448,455 gọi setContentForAutoAdvance + play() vô điều kiện
Kết cục: TTS tự phát lại ngay sau khi user vừa dừng (hoặc đang có cuộc gọi) → LỖI (MAJOR, M-SM)
```

```
Cơ chế: Timeout 10s chạy ở hai nơi (S2, trục Đồng thời)
T1: Dev cài autoAdvanceTimeoutJob trong TtsService.serviceScope theo Bước 3.3 | T2: TtsPlaybackStateMachine cũng tự giữ timeout theo bảng Files/Bước 4.1, được test bằng virtual time | T3: Service không ủy quyền cho state machine; khi có chương mới chỉ một job bị hủy
Kết cục: Hoặc state machine là code chết (test không bảo vệ code chạy thật), hoặc job còn lại bắn STATE_ENDED giữa chương mới → LỖI (MAJOR, M-SM)
```

```
Cơ chế: Xóa truyện giữa chừng (S3, trục Thất bại giữa chừng)
T1: deleteStory: progressDao.deleteProgress OK | T2: chapterDao.deleteChaptersByStory ném SQLiteException | T3: withWriteTransaction rollback ([URL] room3)
Kết cục: AN TOÀN về thiết kế, nhưng TC-09 không có test nào trên DB thật chứng minh rollback → UNVERIFIED (MAJOR, M-T1b)
```

```
Cơ chế: Chương cuối cùng, không có chương kế (S2, trục Vòng đời)
T1: Đọc xong đoạn cuối chương cuối → BUFFERING | T2: autoNextChapter() không tìm thấy chương, tryConstructChapterUrl trả null → không gọi gì (ReaderViewModel.kt:345-352) | T3: t=10s timeout → STATE_ENDED, nhả WakeLock/AudioFocus
Kết cục: AN TOÀN. Notification Media3 còn tối đa 10 phút theo [URL] (chấp nhận được)
```

Trục **Vòng đời Android** (vuốt tắt app khi đang buffering): `onDestroy` hủy job trước khi release player. AN TOÀN theo Bước 3.3.

#### Commands Executed (trích từ evidence.md)

- `### [CMD] cmd.exe /d /c gradlew.bat compileDebugKotlin`, `Exit: 0`, "BUILD SUCCESSFUL in 6s / 8 actionable tasks: 2 executed, 6 up-to-date". Baseline Gradle 9.5.1 biên dịch sạch.
- `### [CMD] cmd.exe /d /c gradlew.bat ktlintCheck`, `Exit: 1`, "KtLint found code style violations". Trong scope plan chỉ có `AppModule.kt:46:72 First line of body expression fits on same line as function signature` và `AppModule.kt:47:50 Expected newline before '.'`. Phần còn lại nằm ngoài scope (`SourceAuthManager.kt`, `TangThuVienScraper.kt`, `WikidichScraper.kt`, `SettingsViewModel.kt:100:21`…).
- `### [CMD] cmd.exe /d /c gradlew.bat testDebugUnitTest`, `Exit: 1`, "Execution failed for task ':app:testDebugUnitTest'… > java.io.EOFException", kèm "w: Detected multiple Kotlin daemon sessions". Đây là lỗi worker/hạ tầng, không phải assertion fail. Tuy vậy, **baseline unit test hiện không xanh** (Run 2 Exit 0). Xem m-BASE.
- `### [CMD] git status --short`, `Exit: 0`. Working tree còn nhiều thay đổi chưa commit (`DownloadRepository.kt`, `AppModule.kt`, `TtsManager.kt`, `build.gradle.kts`, `gradle-wrapper.properties` đều `M`).
- UNVERIFIED (đề xuất chạy sau Phase 1/2): `.\gradlew.bat buildEnvironment`; `.\gradlew.bat compileDebugAndroidTestKotlin` để xác nhận `androidx.room3:room3-testing:3.0.3` resolve được.

#### Risk Table Audit (4.5.4)

| Rủi ro (mức) | Control | Đánh giá |
|---|---|---|
| B1 driver (Cao) | `setDriver(BundledSQLiteDriver())` + cổng compile + kiểm thử thủ công 8.3.4 | Đạt |
| B2 `withWriteTransaction` (Cao) | Code đúng [URL] | Đạt về code. Test rollback còn thiếu → M-T1b |
| Constructor Room 3 (Cao) | `@ConstructedBy` + `object` + `instantiateImpl()` | **Không đạt**: control dựa trên API không có trong tài liệu → M-R1 |
| Notification/Timeline (Cao) | 8 commands + 1 `MediaItem` | Đạt |
| Auto-advance FGS (Cao) | BUFFERING + `playWhenReady` + timeout | Đạt về foreground ([URL] 10 phút). Thiếu xử lý pause trong lúc buffering + trùng chỗ đặt timeout → M-SM |
| Thread Looper (Cao) | Post về Main | Đạt |
| IPC (TB), double-tap (TB), ktlint (TB), R8 (TB) | Như plan | Đạt (R8 xác minh bằng `assembleRelease` do User chạy) |

#### Phân loại lỗi

- **[MAJOR] M-R1 (lần 3, S1)**: Chọn **một** cơ chế constructor có tài liệu và xóa cơ chế kia khỏi Bước 2.5, Bước 2.6, TC-05 và bảng Rủi ro. Bỏ hoàn toàn `AppDatabase::class.instantiateImpl()` vì [URL] room3 không có API này. Cách duy nhất có tài liệu để bỏ `@ConstructedBy` là suppress `RoomWarning` thử nghiệm, rồi truyền factory (vd. `{ AppDatabase_Impl() }`) qua `DatabaseBuilder`. Nếu giữ `@ConstructedBy`, phải dẫn `[URL]` cho cách khai báo `AppDatabaseConstructor` trong module Android thuần. Nếu không tìm được nguồn, thêm một bước spike có cây quyết định rõ ràng: thử phương án X; nếu compile fail thì dùng Y; ghi kết quả vào `docs/lessons-learned.md`. Đổi chữ ký migration chính thành `onMigrate(connection: SQLiteConnection)` (suspend) theo [URL]. Không để "`migrate` hoặc `onMigrate`".
- **[MAJOR] M-SM (S2/S4, state machine auto-advance)**: (1) Nêu rõ `TtsPlaybackStateMachine` là nơi **duy nhất** giữ timeout 10s, nhận `CoroutineScope` qua constructor (service truyền `serviceScope`, test truyền `TestScope`). `TtsService` chỉ ủy quyền và phản ứng qua callback (`onTimeout` → `STATE_ENDED`, nhả WakeLock/AudioFocus). Bỏ `autoAdvanceTimeoutJob` riêng trong service, hoặc ghi rõ nó chính là job của state machine. (2) Thêm chuyển trạng thái `BufferingAutoAdvance` + pause (user, tai nghe, mất AudioFocus) → `Paused`, và `play()`/`setContentForAutoAdvance` đến sau đó **không được** tự phát. Ví dụ: `setContentForAutoAdvance` chỉ nạp nội dung, còn `play()` từ luồng auto-advance bị bỏ qua khi state machine không còn ở `BufferingAutoAdvance`. Đặt chặn ở `TtsService`/`TtsManager` để không phải đổi `ReaderViewModel`. (3) Bổ sung ca test tương ứng vào `TtsPlaybackStateTest` và một dòng Test Contract.
- **[MAJOR] M-T1b (S3, TC-09)**: (1) Sửa Bước 4.2 và mục 8.1 theo code thật: verify thứ tự `progressDao.deleteProgress` → `chapterDao.deleteChaptersByStory` → `storyDao.deleteStory` ([SRC] `DownloadRepository.kt:199-201`). Nêu cách mock extension `withWriteTransaction` (`mockkStatic` lớp chứa top-level function, hoặc một seam nhỏ). (2) Thêm ca test rollback cụ thể vào androidTest: DB in-memory + `BundledSQLiteDriver`, chèn story/chapter/progress, gây lỗi bên trong `withWriteTransaction`, rồi assert cả 3 bảng còn nguyên. Liệt kê ca này ở Bước 4.3 và mục 8.1. Nếu không làm, sửa TC-09 để bỏ khẳng định "tự động rollback".
- **[MINOR] m-RT**: `androidx.room3:room3-testing:3.0.3` và constructor `MigrationTestHelper` của Room 3 chưa có `[URL]`. Thêm `.\gradlew.bat compileDebugAndroidTestKotlin` vào cổng Phase 1/2 để fail-fast.
- **[MINOR] m-END**: TC-04 và mục Rủi ro ghi timeout "nhả Foreground Service". Theo [URL] background-playback, `STATE_ENDED` chỉ thoát foreground sau 10 phút. Muốn gỡ ngay thì gọi `player.clearMediaItems()`/`release()`; nếu không thì sửa câu chữ cho đúng.
- **[MINOR] m-API**: [URL] background-playback dùng `onConnectAsync` và `AcceptedResultBuilder(session, controller)`, còn mẫu ở Bước 3.3 dùng `onConnect` + `AcceptedResultBuilder(session)`. Cổng compile Bước 3.7 sẽ bắt lỗi, nhưng nên theo đúng mẫu tài liệu.
- **[MINOR] m-BASE**: Evidence cho thấy `testDebugUnitTest` baseline đang Exit 1 (`java.io.EOFException`). Trước Phase 1, thêm bước dừng daemon/chạy lại để có baseline xanh, kèm số lượng test. Nếu không có baseline này, không quy được lỗi ở Phase 4 về đúng nguyên nhân. Đồng thời lưu report ktlint trước khi sửa (đếm chính xác thay cho "~150") để kiểm Zero-Regression.

**Kết luận**: M-M1, M-M2, M-M3, M-Q1, M-T3 cùng các MINOR của Run 2 đã được khắc phục. Còn 3 MAJOR (M-R1 lần thứ 3, M-SM, M-T1b), không có BLOCKER. Planner cần ra Revision 5 sửa ba mục trên, chủ yếu là sửa nội dung trong plan, không đổi kiến trúc.

[CHANGES_REQUESTED]

### Audit Run 4

- **Reviewer**: Claude Code CLI (claude-opus-5-5)
- **Ngày**: 2026-10-14
- **Đối tượng**: Technical Plan Revision 5

#### Kiểm chứng lỗi cũ (so với Audit Run 3, kết luận CHANGES_REQUESTED)

| # | Lỗi Run 3 | Trạng thái | Bằng chứng |
|---|---|---|---|
| M-R1 | Cơ chế constructor Room 3 + chữ ký migration | ✅ Đã sửa | Assumption 4, Bước 2.5, 2.6, TC-05 và bảng Rủi ro giờ dùng cùng một cây quyết định: nhánh chính suppress `RoomWarning` + factory qua `DatabaseBuilder`, nhánh dự phòng `@ConstructedBy`. `instantiateImpl()` đã bị xóa. Cổng Bước 2.8 quyết định nhánh, kết quả ghi vào `lessons-learned.md`. [URL] room3: "Added an experimental `RoomWarning` to suppress the requirement for `@ConstructedBy`… a factory implementation must be provided via `DatabaseBuilder`". Chữ ký migration duy nhất là `onMigrate(connection: SQLiteConnection)`, khớp [URL] ("`Migration.onMigrate()`… replaced by their equivalent API that has a `SQLiteConnection`"). Tên chính xác của RoomWarning vẫn chưa có nguồn, xem m-RW |
| M-SM (1) | Timeout 10s chỉ nằm ở một nơi | ✅ Đã sửa | Assumption 5, Bước 3.3: `TtsPlaybackStateMachine(scope, onTimeout)` là nơi duy nhất giữ timer, `autoAdvanceTimeoutJob` đã bị xóa khỏi service |
| M-SM (2) | Pause trong lúc buffering không được tự phát lại | ❌ Sửa sai, gây regression | Cờ `ignoreNextAutoPlay` được bật khi `state != BufferingAutoAdvance`. Điều kiện này cũng đúng với mọi lần chuyển chương thủ công, nên chặn luôn `play()` hợp lệ. Xem M-SM2 và timeline bên dưới |
| M-SM (3) | Test + Test Contract cho TC-10 | ⚠️ Một nửa | Đã có TC-10 và ca test trong Bước 4.1. Nhưng cờ `ignoreNextAutoPlay` nằm trong `TtsService` (Bước 3.3), không nằm trong `TtsPlaybackStateMachine`, nên test JVM không bảo vệ đoạn code thật sự chặn `play()` |
| M-T1b | DAO thật + test rollback | ✅ Đã sửa | Bước 4.2, mục 8.1 dùng `progressDao.deleteProgress` → `chapterDao.deleteChaptersByStory` → `storyDao.deleteStory`, khớp [SRC] `DownloadRepository.kt:197-201`. Bước 4.3, mục 8.1 có ca rollback vật lý trên DB in-memory + `BundledSQLiteDriver` |
| m-RT | Fail-fast `room3-testing` | ✅ Đã sửa (phần cổng) | Bước 2.8 có `compileDebugAndroidTestKotlin`. Artifact `room3-testing` vẫn không có trên [URL] room3 (chỉ có runtime, compiler, sqlite-wrapper, paging, rxjava3, guava, livedata). Cổng compile sẽ bắt lỗi nên chấp nhận |
| m-END | Câu chữ "nhả Foreground" | ✅ Đã sửa | TC-04, mục Rủi ro ghi rõ Media3 giữ notification tối đa 10 phút hoặc `clearMediaItems()` |
| m-API | `onConnectAsync` / `AcceptedResultBuilder(session, controller)` | ❌ Chưa sửa (MINOR) | Bước 3.3 vẫn dùng `onConnect` + `AcceptedResultBuilder(session)`. Cổng Bước 3.7 sẽ bắt lỗi |
| m-BASE | Baseline daemon / test / ktlint | ✅ Đã sửa | Bước 1.0 có `--stop`, `testDebugUnitTest` và đếm ktlint |

#### Mental Simulation Matrix (S1–S6 + Domain)

| Kịch bản | Kết quả | Bằng chứng | Chi tiết |
|---|---|---|---|
| **S1: Build / KSP** | PASS (có điều kiện) | [URL] [CMD] | Cây quyết định constructor có một nhánh có tài liệu và một nhánh dự phòng, cổng `compileDebugKotlin` ở Bước 2.8 quyết định nhánh. Gói `androidx.room3.*`, `setDriver` bắt buộc ("A `SQLiteDriver` is now _required_"), `withWriteTransaction` đều khớp [URL] room3. Baseline `compileDebugKotlin` Exit 0. Tên `ROOM_WARNING_CANNOT_CREATE_CONSTRUCTOR` chưa có nguồn (m-RW), nhưng plan đã có đường lui. |
| **S2: Lifecycle / Service** | PASS | [SRC] [URL] | Timer duy nhất trong state machine, chạy trên `serviceScope` (`SupervisorJob`), hủy trong `onDestroy` trước khi release player/session. `super.onStartCommand`, `super.onDestroy()` cuối cùng, post state về Main Looper. Không có `GlobalScope`. |
| **S3: Database** | PASS | [SRC] [URL] [ADV] | Thứ tự xóa khớp [SRC] `DownloadRepository.kt:198-201`. Rollback có test trên DB thật (androidTest). Migration v5→v9 có test instrumented, destructive chỉ áp cho v1–v4. Schema không đổi, giữ version 9. |
| **S4: Cross-task state** | **FAIL** | [SRC] [ADV] | [SRC] `ReaderViewModel.kt:540-546` (`nextChapter`) và `:608-612` (`prevChapter`): khi TTS đang phát, ViewModel gọi `ttsManager.pause()` **trước**, rồi `loadChapter(autoPlay = true)`, hàm này gọi `setContentForAutoAdvance` + `play()` ([SRC] `:445-455`). `navigateToChapter` ([SRC] `:778-782`) gọi `stop()` rồi cũng đi đường `autoPlay`. Lúc đó state machine đang ở `Paused`/`Idle`, tức `!= BufferingAutoAdvance`, nên theo Bước 3.3 cờ `ignoreNextAutoPlay` bị bật và `play()` bị bỏ qua. Xem M-SM2. |
| **S5: Security** | PASS | [SRC] [URL] | `exported="false"` giữ nguyên, `ACTION_*` công khai bị xóa, `onConnect` lọc `isMediaNotificationController \|\| isTrusted \|\| packageName`, khớp [URL] background-playback. Không chạm cookie/token. |
| **S6: External resilience (TTS engine)** | PASS | [SRC] | Giữ thông báo tiếng Việt "Không thể khởi tạo TTS." và "Thiết bị chưa cài giọng đọc tiếng Việt." (TC-01, TC-07). Mất mạng khi tải chương kế có timeout 10s (TC-04). |
| **Domain: TTS / tai nghe** | **FAIL** | [SRC] [ADV] | 2 chạm tai nghe / nút Next trên notification → `skipNext()` phát `ChapterSkipEvent.NEXT` ([SRC] `TtsService.kt:361-363`) → `ReaderViewModel.nextChapter()` ([SRC] `ReaderViewModel.kt:382-384`) → bị chặn như S4. TC-03 và kiểm thử thủ công 8.3.4 ("tự chuyển sang chương tiếp theo và tiếp tục đọc") sẽ fail. |

#### Adversarial Timelines

```
Cơ chế: Chuyển chương thủ công khi đang nghe (S4, trục TOCTOU, hồi quy do bản sửa M-SM)
T1: TTS đang Playing ở giữa chương; user bấm 2 lần tai nghe (hoặc Next trên notification, hoặc nút Next trong UI) → skipNext() → ChapterSkipEvent.NEXT → ReaderViewModel.nextChapter(): wasActive=true → ttsManager.pause() → stateMachine = Paused | T2: Tải chương xong → loadChapter(autoPlay=true) → setContentForAutoAdvance(): state (Paused) != BufferingAutoAdvance → ignoreNextAutoPlay = true | T3: ttsManager.play() → cờ đang bật → xóa cờ, bỏ qua không phát
Kết cục: Chương mới được nạp nhưng TTS đứng im; mọi thao tác chuyển chương khi đang nghe đều làm dừng đọc → LỖI (MAJOR, M-SM2)
```

```
Cơ chế: Chọn chương từ danh sách khi đang nghe (S4)
T1: Playing → navigateToChapter(): wasActive=true → ttsManager.stop() → stateMachine = Idle | T2: loadChapter(autoPlay=true) → setContentForAutoAdvance(): Idle != BufferingAutoAdvance → ignoreNextAutoPlay = true | T3: play() bị bỏ qua
Kết cục: Hành vi hiện tại (tiếp tục đọc chương đã chọn) bị mất → LỖI (MAJOR, M-SM2)
```

```
Cơ chế: User pause trong lúc buffering auto-advance (S4, kịch bản mà M-SM nhắm tới)
T1: Hết chương → BufferingAutoAdvance, timer 10s chạy | T2: t=1s user Pause → stateMachine = Paused, hủy timer | T3: t=2s tải xong → setContentForAutoAdvance bật cờ → play() bị bỏ qua
Kết cục: AN TOÀN cho đúng kịch bản này. Nhưng điều kiện "state != BufferingAutoAdvance" quá rộng nên gây hai lỗi ở trên
```

```
Cơ chế: Xóa truyện, DAO thứ 2 ném lỗi (S3, trục Thất bại giữa chừng)
T1: deleteStory → withWriteTransaction → progressDao.deleteProgress OK | T2: chapterDao.deleteChaptersByStory ném SQLiteException | T3: Room rollback ([URL] room3), exception ném lên caller
Kết cục: AN TOÀN, có ca rollback vật lý ở Bước 4.3 kiểm chứng
```

Trục **Đồng thời**: timer 10s và `setContentForAutoAdvance` cùng chạy trên `Dispatchers.Main.immediate` nên được tuần tự hóa. AN TOÀN. Trục **Vòng đời Android** (vuốt tắt app khi buffering): `onDestroy` hủy `serviceScope` trước khi release. AN TOÀN.

#### Commands Executed (trích từ evidence.md)

- `### [CMD] cmd.exe /d /c gradlew.bat compileDebugKotlin`, `Exit: 0`, "BUILD SUCCESSFUL in 6s / 8 actionable tasks: 8 up-to-date" (Gradle 9.5.1, baseline).
- `### [CMD] cmd.exe /d /c gradlew.bat ktlintCheck`, `Exit: 1`, "KtLint found code style violations". Trong scope plan vẫn chỉ có `AppModule.kt:46:72 First line of body expression fits on same line as function signature` và `AppModule.kt:47:50 Expected newline before '.'`. Khớp Assumption 7 và Bước 4.4.
- `### [CMD] cmd.exe /d /c gradlew.bat testDebugUnitTest`, `Exit: 0`, "BUILD SUCCESSFUL in 1s / 34 actionable tasks: 34 up-to-date". Baseline xanh trở lại (Run 3 là Exit 1 do `EOFException`). Task ở trạng thái UP-TO-DATE nên output không cho biết số test, Bước 1.0 vẫn cần ghi số lượng.
- `### [CMD] git status --short`, `Exit: 0`. Các file trong scope (`DownloadRepository.kt`, `AppModule.kt`, `TtsManager.kt`, `build.gradle.kts`, `gradle-wrapper.properties`) vẫn đang `M` chưa commit.
- UNVERIFIED (chạy sau khi triển khai): `.\gradlew.bat buildEnvironment`, `.\gradlew.bat compileDebugAndroidTestKotlin`, `.\gradlew.bat connectedDebugAndroidTest`, `.\gradlew.bat assembleRelease` (User chạy).

#### Risk Table Audit (4.5.4)

| Rủi ro (mức) | Control | Đánh giá |
|---|---|---|
| B1 driver (Cao) | `setDriver(BundledSQLiteDriver())` | Đạt ([URL]) |
| B2 `withWriteTransaction` (Cao) | Code + test rollback androidTest | Đạt |
| Constructor Room 3 (Cao) | Cây quyết định + cổng Bước 2.8 | Đạt |
| Notification/Timeline (Cao) | 8 commands + 1 `MediaItem` | Đạt |
| Auto-advance race (Cao) | Timer duy nhất + `ignoreNextAutoPlay` | **Không đạt**: control sai điều kiện, gây hồi quy chuyển chương thủ công (M-SM2) |
| Thread Looper (Cao) | Post về Main | Đạt |
| IPC (TB), double-tap (TB), test infra (TB), ktlint (TB), R8 (TB) | Như plan | Đạt. Riêng double-tap phụ thuộc M-SM2 |

#### Phân loại lỗi

- **[MAJOR] M-SM2 (S4/Domain, hồi quy chuyển chương)**: Điều kiện bật `ignoreNextAutoPlay` (`state != BufferingAutoAdvance`) đúng cả với `nextChapter()`/`prevChapter()` (gọi `pause()` trước, [SRC] `ReaderViewModel.kt:542,610`) và `navigateToChapter()` (gọi `stop()`, [SRC] `:780`). Hậu quả: Next/Prev trên notification, 2 chạm/3 chạm tai nghe (TC-03), nút chuyển chương trong UI và chọn chương từ danh sách đều làm TTS dừng đọc. Cách sửa khuyến nghị (không cần đổi `ReaderViewModel`):
  1. Thêm trạng thái riêng `PausedDuringAutoAdvance` vào `TtsPlaybackStateMachine`. Chỉ vào trạng thái này khi `pause()` (user, tai nghe, mất AudioFocus) xảy ra lúc đang `BufferingAutoAdvance`.
  2. `setContentForAutoAdvance` chỉ bỏ qua `play()` kế tiếp khi state là `PausedDuringAutoAdvance`. Các trạng thái `Playing`/`Paused`/`Idle` khác giữ hành vi hiện tại là `play()` sẽ phát.
  3. Đưa quyết định "có phát hay không" vào state machine (ví dụ `onAutoAdvanceContentLoaded()` / `shouldHonorPlay()`), để `TtsService` chỉ đọc kết quả. Như vậy test JVM bảo vệ đúng code chạy thật.
  4. Ghi rõ hành vi cho ca biên: user pause lúc buffering rồi bấm Next thủ công (`pause()` lần hai lúc đang `PausedDuringAutoAdvance`). Chọn một hành vi và ghi vào Test Contract.
  5. Bổ sung ca test vào `TtsPlaybackStateTest`: (a) `Playing` → `pause()` → `setContentForAutoAdvance` → `play()` ⇒ `Playing` (bảo vệ TC-03). (b) `Idle` (sau `stop()`) → `setContentForAutoAdvance` → `play()` ⇒ `Playing`. (c) Ca TC-10 hiện có. Sửa TC-10 cho khớp tên trạng thái mới.

  Cũng có thể đổi `ReaderViewModel` để truyền rõ nguồn gọi (`autoNextChapter` khác `nextChapter`). Nếu chọn cách này thì thêm file vào bảng Files và test ViewModel tương ứng.
- **[MINOR] m-RW**: Tên `ROOM_WARNING_CANNOT_CREATE_CONSTRUCTOR` chưa có `[URL]` ([URL] room3 không nêu tên enum). Plan đã ghi "hoặc RoomWarning tương ứng" và có nhánh dự phòng. Khi triển khai, lấy tên thật từ thông báo lỗi KSP hoặc từ `androidx.room3.RoomWarnings`, rồi ghi vào `lessons-learned.md`.
- **[MINOR] m-API** (giữ từ Run 3): nên theo mẫu `onConnectAsync` / `AcceptedResultBuilder(session, controller)` của [URL] background-playback nếu `onConnect` một tham số không biên dịch được ở 1.11.1.
- **[MINOR] m-MK**: Tên lớp JVM `androidx.room3.RoomDatabaseKt` dùng cho `mockkStatic` chưa được xác minh. Nên ưu tiên seam nhỏ (interface `TransactionRunner`) để test không phụ thuộc tên file nội bộ của Room 3.
- **[MINOR] m-TN**: Ca rollback vật lý nằm trong `AppDatabaseMigrationTest.kt` nên tên file không phản ánh nội dung. Có thể tách sang `DownloadRepositoryRollbackTest.kt` (không chặn plan).

**Kết luận**: M-R1 (sau 3 lần) và M-T1b đã được khắc phục, phần timer của M-SM cũng vậy. Phần chặn auto-play của M-SM gây hồi quy MAJOR mới (M-SM2): chuyển chương thủ công hay qua tai nghe khi đang nghe sẽ làm TTS dừng đọc. Không có BLOCKER. Planner cần ra Revision 6 thu hẹp điều kiện chặn `play()` vào trạng thái `PausedDuringAutoAdvance` (hoặc phân biệt nguồn gọi), đưa logic đó vào state machine và bổ sung ca test chống hồi quy.

[CHANGES_REQUESTED]

### Audit Run 5

- **Reviewer**: Claude Code CLI (claude-opus-5-5)
- **Ngày**: 2026-10-14
- **Đối tượng**: Technical Plan Revision 6

#### Kiểm chứng lỗi cũ (so với Audit Run 4, kết luận CHANGES_REQUESTED)

| # | Lỗi Run 4 | Trạng thái | Bằng chứng |
|---|---|---|---|
| M-SM2 (1) | Trạng thái riêng `PausedDuringAutoAdvance` | ✅ Đã sửa | Assumption 5, Bước 3.3, Bước 4.1: chỉ vào trạng thái này khi `pause()` xảy ra lúc đang `BufferingAutoAdvance` |
| M-SM2 (2) | Không chặn `play()` hợp lệ khi chuyển chương thủ công | ✅ Đã sửa về logic state machine | `Paused`/`Idle` thường → `onAutoAdvanceContentLoaded()` trả `true`. Khớp đường đi [SRC] `ReaderViewModel.kt:540-546` (`pause()` rồi `loadChapter(autoPlay = wasActive)`), `:608-616`, `:778-782` |
| M-SM2 (3) | Quyết định nằm trong state machine | ⚠️ Một nửa | Quyết định đã nằm trong `TtsPlaybackStateMachine`, nhưng nó được tiêu thụ **sai lời gọi**: tại `setContentForAutoAdvance`, không phải tại `play()` mà `ReaderViewModel` gọi ngay sau đó. Xem M-SM3 |
| M-SM2 (4) | Ca biên pause lúc buffering rồi bấm Next | ✅ Đã sửa | Assumption 5 "Hành vi biên": thoát `PausedDuringAutoAdvance` về `Paused`/`Idle`, lệnh chuyển chương tiếp theo phát bình thường |
| M-SM2 (5) | 3 ca test chống hồi quy | ✅ Đã có | Bước 4.1 Ca 1–3, mục 8.1. Tuy vậy Ca 3 không bảo vệ đường gọi thật (M-SM3) |
| m-RW | Tên RoomWarning | ✅ Chấp nhận | Plan ghi lấy tên thật từ `androidx.room3.RoomWarnings`/log KSP và ghi vào `lessons-learned.md`, có nhánh dự phòng |
| m-API | `onConnectAsync` / `AcceptedResultBuilder(session, controller)` | ✅ Chấp nhận | Bước 3.3 ghi chú "hoặc overload… tùy chữ ký thực tế", cổng compile Bước 3.7 bắt lỗi |
| m-MK | Seam thay `mockkStatic` | ✅ Đã sửa | `[NEW] DatabaseTransactionRunner.kt`, Bước 2.4, 2.6, 4.2 |
| m-TN | Tên file test rollback | ✅ Chấp nhận | Giữ trong `AppDatabaseMigrationTest.kt`, không chặn |

#### Mental Simulation Matrix (S1–S6 + Domain)

| Kịch bản | Kết quả | Bằng chứng | Chi tiết |
|---|---|---|---|
| **S1: Build / KSP** | PASS (có điều kiện) | [CMD] [SRC] | Baseline `compileDebugKotlin` Exit 0. Cây quyết định constructor Room 3, `setDriver`, `onMigrate(SQLiteConnection)`, plugin `androidx.room3` không đổi so với Run 4 (đã đạt). `DownloadRepository` đang `@Inject constructor(..., database: AppDatabase)` ([SRC] `DownloadRepository.kt:28-36`). Thêm `DatabaseTransactionRunner` có binding `@Provides` ở Bước 2.6 nên Hilt resolve được. Các cổng Bước 1.4, 2.8, 3.7 bảo vệ phần còn lại. |
| **S2: Lifecycle / Service** | PASS | [SRC] | Timer duy nhất trong state machine trên `serviceScope` (`SupervisorJob` + `Main.immediate`), `serviceScope.cancel()` trước khi release session/player, `super.onDestroy()` cuối. Không có `GlobalScope`/`runBlocking`. |
| **S3: Database** | PASS | [SRC] [ADV] | Thứ tự xóa khớp [SRC] `DownloadRepository.kt:198-201`. Có unit test thứ tự + rethrow (JVM, seam) và rollback vật lý (androidTest). Schema giữ v9, destructive chỉ v1–v4. |
| **S4: Cross-task state** | **FAIL** | [SRC] [ADV] | [SRC] `ReaderViewModel.kt:445-456`: sau khi tải xong, `loadChapter(autoPlay=true)` gọi `ttsManager.setContentForAutoAdvance(...)` rồi **luôn** gọi `ttsManager.play()`. [SRC] `TtsManager.kt:146-148`: `play()` chuyển thẳng tới `service?.play()`, không điều kiện. Plan đặt quyết định ở `setContentForAutoAdvance` (`shouldPlay == false` → state về `Paused` thường, "bỏ qua không gọi `play()`"), nhưng lệnh `play()` thật đến từ ViewModel ngay sau đó, lúc state đã là `Paused` thường. Plan không quy định `TtsService.play()` từ chối lời gọi này, và cam kết "không sửa dòng nào của `ReaderViewModel.kt`". Kết quả: TC-10 vẫn tự phát lại. Xem M-SM3(a). |
| **S5: Security** | PASS | [SRC] | `exported="false"` giữ nguyên ([SRC] `AndroidManifest.xml`), `ACTION_*` công khai bị xóa, `onConnect` lọc `isMediaNotificationController \|\| isTrusted \|\| packageName`. Không chạm cookie/token. |
| **S6: External resilience (TTS engine)** | PASS | [SRC] | Thông báo tiếng Việt "Không thể khởi tạo TTS." và "Thiết bị chưa cài giọng đọc tiếng Việt." ([SRC] `TtsService.kt:181,247`) được giữ. Mất mạng khi tải chương kế có timeout 10s. |
| **Domain: TTS / tai nghe / AudioFocus** | **FAIL** | [SRC] [ADV] | Trong lúc `BufferingAutoAdvance`, plan bắt buộc `TtsState.isPlaying = false`. Nhưng hai lối vào `pause()` hiện tại đều dựa vào `isPlaying`: (1) 1 chạm tai nghe [SRC] `TtsService.kt:543` `if (_state.value.isPlaying) pause() else play()` → lúc buffering sẽ gọi **`play()`** (đọc lại đoạn cuối chương cũ, vì `currentIndex` vẫn là đoạn cuối) thay vì pause. (2) Mất AudioFocus [SRC] `TtsService.kt:515-520` `if (_state.value.isPlaying) pause()` → lúc buffering **không pause**, state machine không vào `PausedDuringAutoAdvance`, chương mới phát trong lúc đang có cuộc gọi. TC-10 liệt kê đúng hai nguồn này, nhưng plan không yêu cầu đổi điều kiện. Xem M-SM3(b). |

#### Adversarial Timelines

```
Cơ chế: Pause trên notification trong lúc buffering auto-advance (S4, trục TOCTOU)
T1: Hết chương → stateMachine = BufferingAutoAdvance, TtsState.isPlaying=false; ReaderViewModel.autoNextChapter() bắt đầu tải | T2: t=1s user bấm Pause trên notification → handleSetPlayWhenReady(false) → pause() → PausedDuringAutoAdvance, hủy timer | T3: t=2s tải xong → ReaderViewModel.kt:448 setContentForAutoAdvance → onAutoAdvanceContentLoaded() trả false, state = Paused, service không tự play → ReaderViewModel.kt:455 ttsManager.play() → TtsManager.kt:147 service.play() → state Paused hợp lệ để play → Playing
Kết cục: TTS vẫn tự phát lại ngay sau khi user vừa pause → LỖI (MAJOR, M-SM3a). Ca test 3 ở Bước 4.1 chỉ kiểm giá trị trả về của onAutoAdvanceContentLoaded(), không kiểm lần play() đến sau, nên không bắt được lỗi này
```

```
Cơ chế: 1 chạm tai nghe trong lúc buffering (Domain, trục Đồng thời)
T1: BufferingAutoAdvance, TtsState.isPlaying=false, currentIndex = đoạn cuối chương cũ | T2: User bấm 1 lần tai nghe → sau 400ms mediaButtonRunnable: isPlaying=false → play() | T3: play() đọc lại đoạn cuối chương cũ; stateMachine thoát Buffering (hoặc rơi vào trạng thái không xác định), timer 10s có thể bị hủy; khi chương mới tải xong lại bị cắt ngang
Kết cục: Thay vì pause, app đọc lặp đoạn cuối → LỖI (MAJOR, M-SM3b)
```

```
Cơ chế: Cuộc gọi đến trong lúc buffering (Domain, trục Vòng đời Android)
T1: BufferingAutoAdvance, isPlaying=false, vẫn giữ AudioFocus theo plan | T2: Cuộc gọi đến → AUDIOFOCUS_LOSS_TRANSIENT → TtsService.kt:519 `if (isPlaying) pause()` → không làm gì | T3: Chương mới tải xong → onAutoAdvanceContentLoaded() trả true (vẫn Buffering) → phát
Kết cục: TTS phát chồng lên cuộc gọi → LỖI (MAJOR, M-SM3b)
```

```
Cơ chế: Chuyển chương thủ công khi đang nghe (kiểm lại M-SM2)
T1: Playing → 2 chạm tai nghe → skipNext() → ReaderViewModel.nextChapter(): wasActive=true → pause() → Paused | T2: loadChapter(autoPlay=true) → setContentForAutoAdvance → onAutoAdvanceContentLoaded() trả true → Playing, service tự play | T3: ReaderViewModel.kt:455 play() lần hai → speakCurrent() QUEUE_FLUSH đoạn 0
Kết cục: AN TOÀN về hành vi (phát tiếp chương mới). Nhưng có hai lần play() liên tiếp → MINOR m-DUP
```

```
Cơ chế: Xóa truyện, DAO thứ 2 ném lỗi (S3, trục Thất bại giữa chừng)
T1: transactionRunner.withWriteTransaction → progressDao.deleteProgress OK | T2: chapterDao.deleteChaptersByStory ném SQLiteException | T3: Room rollback, exception lên caller
Kết cục: AN TOÀN, có test rollback vật lý ở Bước 4.3
```

Trục **Vòng đời Android** (vuốt tắt app khi đang buffering): `onDestroy` hủy `serviceScope` trước khi release. AN TOÀN.

#### Commands Executed (trích từ evidence.md)

- `### [CMD] cmd.exe /d /c gradlew.bat compileDebugKotlin`, `Exit: 0`, "BUILD SUCCESSFUL in 5s / 8 actionable tasks: 8 up-to-date" (Gradle 9.5.1, baseline).
- `### [CMD] cmd.exe /d /c gradlew.bat ktlintCheck`, `Exit: 1`, "KtLint found code style violations". Trong scope plan chỉ có `AppModule.kt:46:72 First line of body expression fits on same line as function signature` và `AppModule.kt:47:50 Expected newline before '.'`. Khớp Assumption 7 và Bước 4.4.
- `### [CMD] cmd.exe /d /c gradlew.bat testDebugUnitTest`, `Exit: 0`, "BUILD SUCCESSFUL in 1s / 34 actionable tasks: 34 up-to-date". Baseline xanh (UP-TO-DATE, Bước 1.0 vẫn cần ghi số test).
- `### [CMD] git status --short`, `Exit: 0`. `ReaderViewModel.kt`, `TtsManager.kt`, `DownloadRepository.kt`, `AppModule.kt`, `build.gradle.kts`, `gradle-wrapper.properties` đang `M` chưa commit.
- UNVERIFIED (chạy sau khi triển khai): `.\gradlew.bat buildEnvironment`, `.\gradlew.bat compileDebugAndroidTestKotlin`, `.\gradlew.bat connectedDebugAndroidTest`, `.\gradlew.bat assembleRelease` (User chạy).

#### Risk Table Audit (4.5.4)

| Rủi ro (mức) | Control | Đánh giá |
|---|---|---|
| Driver Room 3 (Cao) | `setDriver(BundledSQLiteDriver())` | Đạt |
| `withWriteTransaction` (Cao) | `DatabaseTransactionRunner` + test rollback androidTest | Đạt |
| Constructor Room 3 (Cao) | Cây quyết định + cổng Bước 2.8 | Đạt |
| Notification/Timeline (Cao) | 8 commands + 1 `MediaItem` | Đạt |
| Auto-advance race (Cao) | `PausedDuringAutoAdvance` + `onAutoAdvanceContentLoaded()` | **Không đạt**: control đặt ở `setContentForAutoAdvance`, nhưng lệnh phát thật là `play()` ở `ReaderViewModel.kt:455`; các lối vào pause dựa trên `isPlaying` không kích hoạt lúc buffering (M-SM3) |
| Thread Looper (Cao) | Post về Main | Đạt |
| IPC (TB), double-tap (TB), test infra (TB), ktlint (TB), R8 (TB) | Như plan | Đạt |

#### Phân loại lỗi

- **[MAJOR] M-SM3 (S4/Domain, TC-10 vẫn chưa được bảo vệ trên đường gọi thật)**:
  - **(a) Chặn sai lời gọi.** `ReaderViewModel.kt:448,455` gọi `setContentForAutoAdvance` rồi `play()` liên tiếp, và `TtsManager.kt:146-148` chuyển `play()` thẳng tới service. Quyết định `shouldPlay == false` phải chặn **chính lời gọi `play()` này**, không phải một lệnh `play()` nội bộ trong service. Hai hướng sửa, chọn một và ghi rõ:
    1. Không đổi ViewModel: `setContentForAutoAdvance` **không bao giờ** tự gọi `play()`. Khi state là `PausedDuringAutoAdvance`, state machine chuyển sang `Paused` và bật cờ một lần `suppressNextPlay` (nằm **trong** `TtsPlaybackStateMachine`). `TtsService.play()` gọi `stateMachine.onPlayRequested(): Boolean` trước khi phát. Nếu cờ đang bật thì xóa cờ và trả `false`. Lời gọi `play()` ở `:455` chạy đồng bộ trên Main ngay sau `:448`, nên cờ chỉ nuốt đúng lời gọi đó. Lần bấm Play sau của user vẫn phát. Mọi lời gọi `pause()`/`stop()`/`setContent()` phải xóa cờ.
    2. Đổi ViewModel (thêm `ReaderViewModel.kt` vào bảng Files): `setContentForAutoAdvance` trả `Boolean`, và `:454-456` chỉ gọi `play()` khi kết quả là `true`. Cần cập nhật test ViewModel tương ứng.
    
    Bỏ việc `TtsService` tự gọi `ttsPlayer.play()` trong nhánh `shouldPlay == true` (Bước 3.3 dòng "nạp nội dung và gọi `ttsPlayer.play()`"), để chỉ còn một lệnh phát (xem m-DUP). Sửa Ca 3 ở Bước 4.1 và mục 8.1 thành chuỗi đầy đủ: `BufferingAutoAdvance` → `pause()` → `onAutoAdvanceContentLoaded()` → `onPlayRequested()` ⇒ `false`, rồi `onPlayRequested()` lần nữa ⇒ `true`.
  - **(b) Lối vào pause lúc buffering.** Vì `TtsState.isPlaying = false` lúc `BufferingAutoAdvance`, phải đổi điều kiện ở hai chỗ sang trạng thái của state machine (ví dụ `stateMachine.isActivelyPlaying` = `Playing || BufferingAutoAdvance`), không dùng `_state.value.isPlaying`: (1) 1 chạm tai nghe ([SRC] `TtsService.kt:543`), (2) listener AudioFocus `LOSS`/`LOSS_TRANSIENT` ([SRC] `TtsService.kt:515-520`). `handleSetPlayWhenReady` của `TtsPlayer` cũng phải route qua cùng logic. Thêm ca test JVM: `BufferingAutoAdvance` + sự kiện toggle ⇒ `PausedDuringAutoAdvance`.
- **[MINOR] m-DUP**: Ở nhánh `shouldPlay == true`, plan để service tự `play()` rồi ViewModel lại `play()` lần nữa ([SRC] `ReaderViewModel.kt:455`). Hai lần `speakCurrent()` với `QUEUE_FLUSH` không làm hỏng hành vi, nhưng gây `acquireWakeLock()` thừa và nhiễu state. Sẽ hết khi sửa theo M-SM3(a).
- **[MINOR] m-NAME**: TC-09, mục 8.1 và bảng Rủi ro (B2) dùng `transactionRunner.runInTransaction`, còn interface ở Bước 2.4/2.6 tên là `withWriteTransaction`. Thống nhất một tên.
- **[MINOR] m-CTOR**: Bước 2.4 nên ghi rõ gỡ tham số `database: AppDatabase` khỏi constructor `DownloadRepository` ([SRC] `DownloadRepository.kt:35`) sau khi chuyển sang `DatabaseTransactionRunner`, để test JVM không phải mock `AppDatabase`.

**Kết luận**: M-SM2 đã được khắc phục ở tầng state machine: chuyển chương thủ công không còn bị nuốt lệnh. Các MINOR của Run 4 (m-RW, m-API, m-MK, m-TN) đã được xử lý hoặc chấp nhận. Còn 1 MAJOR (M-SM3): quyết định chặn tự phát đặt ở `setContentForAutoAdvance`, trong khi lệnh phát thật là `play()` từ `ReaderViewModel.kt:455`. Thêm nữa, 1 chạm tai nghe và mất AudioFocus lúc buffering không đi vào `pause()`. Vì vậy TC-10 vẫn fail trên đường gọi thật. Không có BLOCKER. Planner cần ra Revision 7 sửa M-SM3 (a) và (b), kèm ca test tương ứng. Phần còn lại của plan đã đạt chuẩn.

[CHANGES_REQUESTED]

### Audit Run 6

- **Reviewer**: Claude Code CLI (claude-opus-5-5)
- **Ngày**: 2026-10-14
- **Đối tượng**: Technical Plan Revision 7

#### Kiểm chứng lỗi cũ (so với Audit Run 5, kết luận CHANGES_REQUESTED)

| # | Lỗi Run 5 | Trạng thái | Bằng chứng |
|---|---|---|---|
| M-SM3(a) | Quyết định chặn tự phát phải áp lên đúng lời gọi `play()` từ `ReaderViewModel.kt:455` | ✅ Đã sửa (Hướng 1, không đổi ViewModel) | Assumption 5, Bước 3.3, Bước 4.1: `setContentForAutoAdvance` chỉ gọi `stateMachine.onAutoAdvanceContentLoaded()`. Nếu state là `PausedDuringAutoAdvance` thì chuyển về `Paused` và bật cờ `suppressNextPlay` (nằm **trong** state machine). `TtsService.play()` gọi `if (!stateMachine.onPlayRequested()) return` ở đầu hàm. [SRC] `ReaderViewModel.kt:445-456`: `:448` và `:455` chạy liền nhau trong cùng một coroutine, không có điểm suspend ở giữa. [SRC] `TtsManager.kt:135-148`: cả hai lời gọi chuyển thẳng, đồng bộ, tới service. Vì vậy cờ chỉ nuốt đúng lời gọi `:455` |
| M-SM3(b) | Hai lối vào pause lúc buffering dựa trên `isPlaying` | ✅ Đã sửa | Bước 3.3 đổi điều kiện ở [SRC] `TtsService.kt:543` (1 chạm) và `:515-520` (AudioFocus `LOSS`/`LOSS_TRANSIENT`) sang `stateMachine.isActiveOrBuffering`. `handleSetPlayWhenReady(false)` route qua `pause()`. Số dòng khớp source hiện tại (`:516`, `:519`, `:543`) |
| M-SM3 (test) | Ca 3 phải là chuỗi đầy đủ tới `onPlayRequested()` | ✅ Đã sửa | Bước 4.1 Ca 3 và mục 8.1: `BufferingAutoAdvance` → `pause()` → `onAutoAdvanceContentLoaded()` → `onPlayRequested()` ⇒ `false` → `onPlayRequested()` ⇒ `true`. Ca 4 bảo vệ `isActiveOrBuffering` |
| m-DUP | Hai lệnh `play()` liên tiếp | ✅ Đã sửa | Bước 3.3: `setContentForAutoAdvance` "TUYỆT ĐỐI KHÔNG TỰ GỌI `play()`". Chỉ còn lời gọi của ViewModel |
| m-NAME | `runInTransaction` / `withWriteTransaction` | ✅ Đã sửa | TC-09, mục 8.1, bảng Rủi ro (B2) đều dùng `withWriteTransaction`, khớp interface ở Bước 2.4 |
| m-CTOR | Gỡ `database: AppDatabase` khỏi constructor | ✅ Đã sửa | Bước 2.4 ghi rõ gỡ tham số ở `DownloadRepository.kt:35`. Bước 4.2 không mock `AppDatabase` |
| M-SM2 | Không nuốt `play()` khi chuyển chương thủ công | ✅ Vẫn đạt | `pause()` từ `Playing` ([SRC] `ReaderViewModel.kt:540-542`, `:608-610`) cho ra `Paused` thường. `stop()` ([SRC] `:779-780`) cho ra `Idle`. Cả hai giữ `suppressNextPlay = false`. Ca 1, Ca 2 bảo vệ |

#### Mental Simulation Matrix (S1–S6 + Domain)

| Kịch bản | Kết quả | Bằng chứng | Chi tiết |
|---|---|---|---|
| **S1: Build / KSP** | PASS (có điều kiện) | [CMD] [SRC] | Baseline `compileDebugKotlin` Exit 0 trên Gradle 9.5.1. Phần Room 3 (cây quyết định constructor, `setDriver`, `onMigrate(SQLiteConnection)`, plugin `androidx.room3`) không đổi so với Run 4–5, đã đạt. `DatabaseTransactionRunner` có `@Provides @Singleton` ở Bước 2.6, nên Hilt resolve được constructor mới của `DownloadRepository`. Các cổng Bước 1.4, 2.8, 3.7 chặn mọi lỗi version/API chưa xác minh (KSP 2.3.12, serialization 1.11.0, `room3-testing`, chữ ký `onConnect`). |
| **S2: Lifecycle / Service** | PASS | [SRC] | Timer duy nhất nằm trong state machine, chạy trên `serviceScope` (`SupervisorJob` + `Main.immediate`). `serviceScope.cancel()` chạy trước khi release session/player, `super.onDestroy()` gọi cuối. `super.onStartCommand` ở đầu. Không có `GlobalScope`/`runBlocking`. |
| **S3: Database** | PASS | [SRC] [ADV] | Thứ tự xóa khớp [SRC] `DownloadRepository.kt:197-201`. Có unit test thứ tự + rethrow (JVM, qua seam) và test rollback vật lý (androidTest). Schema giữ v9, destructive chỉ áp cho v1–v4. |
| **S4: Cross-task state** | PASS | [SRC] [ADV] | TC-10 giờ được bảo vệ trên đường gọi thật (timeline 1). Chuyển chương thủ công từ `Playing`/`Idle` vẫn phát (timeline 2). Lối vào `setContent()` ([SRC] `TtsService.kt:259` gọi `stop()` bên trong) xóa cờ, nên không có cờ treo. Còn khoảng trống nhỏ ở các chuyển trạng thái phụ, xem m-TMR. |
| **S5: Security** | PASS | [SRC] | `exported="false"` giữ nguyên, các `ACTION_*` công khai bị xóa, `onConnect` lọc `isMediaNotificationController \|\| isTrusted \|\| packageName == packageName`. Không chạm cookie/token. |
| **S6: External resilience (TTS engine)** | PASS | [SRC] | Giữ thông báo tiếng Việt "Không thể khởi tạo TTS." ([SRC] `TtsService.kt:247`). Mất mạng khi tải chương kế có timeout 10s (TC-04). |
| **Domain: TTS / tai nghe / AudioFocus** | PASS | [SRC] [ADV] | 1 chạm lúc buffering ⇒ `pause()` ⇒ `PausedDuringAutoAdvance` (timeline 3). Mất AudioFocus lúc buffering cũng vậy (timeline 4). Listener hiện tại không tự resume khi có `AUDIOFOCUS_GAIN` ([SRC] `TtsService.kt:514-521`), nên sau cuộc gọi app không tự phát lại. 2/3 chạm đi qua `skipNext()`/`skipPrev()` → `nextChapter()`/`prevChapter()` như S4. |

#### Adversarial Timelines

```
Cơ chế: Pause trên notification trong lúc buffering auto-advance (S4, trục TOCTOU, kiểm lại M-SM3a)
T1: Hết chương → BufferingAutoAdvance, TtsState.isPlaying=false, ReaderViewModel.autoNextChapter() bắt đầu tải | T2: t=1s user bấm Pause → handleSetPlayWhenReady(false) → pause() → PausedDuringAutoAdvance, hủy timer | T3: t=2s tải xong → ReaderViewModel.kt:448 setContentForAutoAdvance → onAutoAdvanceContentLoaded(): Paused + suppressNextPlay=true → :455 (cùng lượt chạy trên Main) play() → onPlayRequested() xóa cờ, trả false → return sớm
Kết cục: AN TOÀN. TTS không tự phát. Lần bấm Play sau đó phát bình thường từ đoạn 0 chương mới
```

```
Cơ chế: Chuyển chương thủ công khi đang nghe (S4, kiểm lại M-SM2)
T1: Playing → 2 chạm tai nghe → skipNext() → nextChapter(): wasActive=true → pause() → Paused thường (không phải PausedDuringAutoAdvance) | T2: loadChapter(autoPlay=true) → setContentForAutoAdvance → onAutoAdvanceContentLoaded(): cờ giữ false | T3: :455 play() → onPlayRequested() true → Playing
Kết cục: AN TOÀN, chỉ một lệnh play()
```

```
Cơ chế: 1 chạm tai nghe lúc buffering (Domain, trục Đồng thời)
T1: BufferingAutoAdvance, isActiveOrBuffering=true | T2: sau debounce 400ms → mediaButtonRunnable → pause() → PausedDuringAutoAdvance, hủy timer | T3: tải xong → cờ bật → play() của ViewModel bị nuốt
Kết cục: AN TOÀN. Không đọc lặp đoạn cuối chương cũ
```

```
Cơ chế: Cuộc gọi đến lúc buffering (Domain, trục Vòng đời Android)
T1: BufferingAutoAdvance, vẫn giữ AudioFocus | T2: AUDIOFOCUS_LOSS_TRANSIENT → isActiveOrBuffering=true → pause() → PausedDuringAutoAdvance | T3: tải xong → play() bị nuốt. Hết cuộc gọi: listener không resume khi GAIN
Kết cục: AN TOÀN. Không phát chồng lên cuộc gọi
```

```
Cơ chế: Bấm Play trong app lúc đang buffering, mạng chậm (S4, trục Đồng thời, ca biên)
T1: BufferingAutoAdvance, timer 10s chạy. UI hiện nút Play vì TtsState.isPlaying=false | T2: t=1s user bấm Play trong app → ttsPlay() → onPlayRequested(): cờ false → Playing, speakCurrent() đọc lại đoạn cuối chương cũ. Plan không nói rõ có hủy timer khi rời BufferingAutoAdvance theo đường này hay không | T3: nếu timer không bị hủy và vẫn chạy tới t=10s trong lúc Playing → onTimeout → TtsPlayer STATE_ENDED, nhả WakeLock/AudioFocus trong khi TTS vẫn đang đọc
Kết cục: RỦI RO THẤP, cửa sổ hẹp, không mất dữ liệu. Ghi là MINOR m-TMR
```

```
Cơ chế: Xóa truyện, DAO thứ 2 ném lỗi (S3, trục Thất bại giữa chừng)
T1: transactionRunner.withWriteTransaction → progressDao.deleteProgress OK | T2: chapterDao.deleteChaptersByStory ném SQLiteException | T3: Room rollback, exception ném lên caller
Kết cục: AN TOÀN, có test rollback vật lý ở Bước 4.3
```

Trục **Vòng đời Android** (vuốt tắt app lúc đang buffering): `onDestroy` hủy `serviceScope` trước khi release. AN TOÀN.

#### Commands Executed (trích từ evidence.md)

- `### [CMD] cmd.exe /d /c gradlew.bat compileDebugKotlin`, `Exit: 0`, "BUILD SUCCESSFUL in 5s / 8 actionable tasks: 8 up-to-date" (Gradle 9.5.1, baseline).
- `### [CMD] cmd.exe /d /c gradlew.bat ktlintCheck`, `Exit: 1`, "KtLint found code style violations". Có 181 dòng vi phạm, tất cả trong các file ngoài scope trừ `AppModule.kt:46:72 First line of body expression fits on same line as function signature` và `AppModule.kt:47:50 Expected newline before '.'`. Khớp Assumption 7 và Bước 4.4. Bước 1.0 vẫn nên đếm lại trên report thật trước khi sửa.
- `### [CMD] cmd.exe /d /c gradlew.bat testDebugUnitTest`, `Exit: 0`, "BUILD SUCCESSFUL in 1s / 34 actionable tasks: 34 up-to-date". Baseline xanh. Task UP-TO-DATE nên output không cho biết số test, Bước 1.0 cần ghi số lượng.
- `### [CMD] git status --short`, `Exit: 0`. Các file trong scope (`DownloadRepository.kt`, `AppModule.kt`, `TtsManager.kt`, `ReaderViewModel.kt`, `build.gradle.kts`, `app/build.gradle.kts`, `AndroidManifest.xml`, `gradle-wrapper.properties`) đang `M` chưa commit. Nên commit hoặc stash phần việc đang dở trước Phase 1 để dễ cô lập lỗi.
- UNVERIFIED (chạy sau khi triển khai): `.\gradlew.bat buildEnvironment`, `.\gradlew.bat compileDebugAndroidTestKotlin`, `.\gradlew.bat connectedDebugAndroidTest`, `.\gradlew.bat assembleRelease` (User chạy).

#### Risk Table Audit (4.5.4)

| Rủi ro (mức) | Control | Đánh giá |
|---|---|---|
| Driver Room 3 (Cao) | `setDriver(BundledSQLiteDriver())` | Đạt |
| `withWriteTransaction` (Cao) | `DatabaseTransactionRunner` + test rollback androidTest | Đạt |
| Constructor Room 3 (Cao) | Cây quyết định + cổng Bước 2.8 | Đạt |
| Notification/Timeline (Cao) | 8 commands + 1 `MediaItem` | Đạt |
| Auto-advance race (Cao) | `PausedDuringAutoAdvance` + `suppressNextPlay` + `onPlayRequested()` + `isActiveOrBuffering` | Đạt. Còn m-TMR (guard timer) |
| Thread Looper (Cao) | Post về Main | Đạt |
| IPC (TB), double-tap (TB), test infra (TB), ktlint (TB), R8 (TB) | Như plan | Đạt |

#### Phân loại lỗi

- **[MINOR] m-TMR (bảng chuyển trạng thái chưa đủ)**: Plan định nghĩa `onAutoAdvanceContentLoaded()` cho `PausedDuringAutoAdvance`, `BufferingAutoAdvance`, `Paused`, `Idle`, nhưng chưa nói gì về `Playing` và `Ended`. Plan cũng chưa nói `onPlayRequested()` có hủy timer khi được gọi lúc `BufferingAutoAdvance` (nút Play trong app vẫn hiện vì `isPlaying=false`) hay không. Khi triển khai: (1) mọi lối ra khỏi `BufferingAutoAdvance` đều hủy timer, (2) callback `onTimeout` chỉ chạy khi state vẫn là `BufferingAutoAdvance`, (3) `startAutoAdvanceBuffering()` hủy job cũ trước khi tạo job mới, (4) `Ended` + content tới muộn: chọn một hành vi (khuyến nghị coi như `Idle`, để `play()` của ViewModel phát tiếp) và thêm một ca test JVM.
- **[MINOR] m-FGS**: Sau timeout (`STATE_ENDED`), nếu chương tới muộn và `play()` được gọi lúc app ở background, Media3 có thể phải vào lại foreground. Trên Android 12+ việc này có thể bị chặn. Nên log/xử lý `onForegroundServiceStartNotAllowedException` (hoặc tương đương ở 1.11.1) và ghi kết quả kiểm thử thủ công vào `lessons-learned.md`.
- **[MINOR] m-VER** (giữ từ Run 1): KSP 2.3.12, kotlinx-serialization 1.11.0, Gradle 9.6.0 và artifact `androidx.room3:room3-testing` chưa có `[URL]` trong danh sách domain được phép. Các cổng Bước 1.4 và 2.8 chặn nếu sai.
- **[MINOR] m-API** (giữ từ Run 3–5): chữ ký `onConnect`/`AcceptedResultBuilder` để cổng Bước 3.7 quyết định.

**Kết luận**: M-SM3 (a) và (b) đã được khắc phục. `suppressNextPlay` nằm trong state machine và chặn đúng lời gọi `play()` từ `ReaderViewModel.kt:455`. Hai lối vào pause (1 chạm tai nghe, AudioFocus) dùng `isActiveOrBuffering`. m-DUP, m-NAME, m-CTOR cũng đã được sửa. Chuyển chương thủ công (M-SM2) vẫn an toàn. Không còn BLOCKER hay MAJOR. Các MINOR còn lại (m-TMR, m-FGS, m-VER, m-API) xử lý trong lúc triển khai và ghi vào `docs/lessons-learned.md`. Kết luận: APPROVED.

[PLAN_APPROVED]
