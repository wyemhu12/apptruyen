# Kế hoạch triển khai: Tối ưu tốc độ tải chương truyện song song theo lô (Parallel Batch Download with Ordered Persistence)

> **Trạng thái**: Completed
> **Revision**: 3
> **Ngày tạo**: 2026-10-14
> **Planner**: @planner (gemini-3.8-flash)

## Technical Plan

### 1. Mục Tiêu

Khắc phục triệt để tình trạng tải chương truyện offline chậm bằng cơ chế **tải song song có giới hạn theo lô (Bounded Parallel Batch Download)** kết hợp **sắp xếp và lưu tuần tự đúng thứ tự chương (Ordered Persistence & Emission)** và **cơ chế dừng sớm an toàn (Fail-Fast Resilience)** trong `DownloadRepository` và `DownloadService`:

1. **Tải song song theo lô (`DOWNLOAD_BATCH_SIZE = 4`)**:
   - Thay thế vòng lặp tải tuần tự từng chương một (`for (chapter in chapters)`) bằng cơ chế chia lô `sortedChapters.chunked(DOWNLOAD_BATCH_SIZE)` (4 chương/lô) và tải đồng thời trong từng lô qua `coroutineScope { batch.map { chapter -> async { fetchChapterWithRetry(...) } }.awaitAll() }`.
   - Vì `BaseScraper.kt:125` thực thi `client.newCall(...).execute()` đồng bộ trên mỗi coroutine, giới hạn đồng thời thực tế bắn vào nguồn truyện trong quá trình tải được kiểm soát trực tiếp bởi hằng số `DOWNLOAD_BATCH_SIZE = 4` (tuân thủ nghiêm ngặt `.agents/rules/04-scraping.md`: *"Tải hàng loạt không được bắn request song song không giới hạn vào cùng một nguồn"*). Đồng thời, mức đồng thời 4 luồng nằm gọn trong giới hạn giữ kết nối nhàn rỗi của `ConnectionPool(5, 30, TimeUnit.SECONDS)` tại `di/AppModule.kt:60`, giúp tái sử dụng trọn vẹn 4 kết nối HTTP/TLS giữa các lô liên tiếp.
2. **Đảm bảo thứ tự chương nghiêm ngặt (`Ordered Persistence & Progress Emission`)**:
   - Chuẩn hóa đầu vào: sắp xếp danh sách chương cần tải theo `chapterNumber` tăng dần (`chapters.sortedBy { it.chapterNumber }`) ngay tại đầu hàm `DownloadRepository.downloadChapters`, và phân mảnh phòng thủ theo `SQLITE_MAX_BIND_ARGS = 900` kèm `.sortedBy { it.chapterNumber }` trong `DownloadRepository.getChaptersByNumbers`.
   - Tách biệt hoàn toàn giai đoạn gọi mạng song song (`async` chỉ trả về kết quả trong bộ nhớ `ChapterFetchOutcome`) với giai đoạn ghi cơ sở dữ liệu Room.
   - Sau khi các luồng trong lô hoàn tất (`awaitAll()`), sắp xếp kết quả của lô theo `outcome.chapter.chapterNumber` tăng dần (`batchOutcomes.sortedBy { it.chapter.chapterNumber }`), sau đó mới tuần tự gọi `chapterDao.saveChapterContent`, `chapterDao.updateChapterMetadata` và `emit(DownloadProgress(...))`. Nhờ đó, dù chương sau trong lô tải xong trước chương trước, dữ liệu lưu vào Room DB và tiến độ phát ra UI/Notification luôn theo đúng thứ tự `chapterNumber` tăng dần (`1 → 2 → 3 → 4`).
3. **Giảm 8x thời gian chờ điều tiết (Inter-Batch Adaptive Rate Limit), Hủy tức thời (`CancellationException`) & Dừng sớm khi lỗi nghiêm trọng (S6 Fail-Fast)**:
   - Chuyển `delay` điều tiết từ sau mỗi chương (500ms/chương = 50 giây chờ cho 100 chương) sang **giữa các lô** (khởi tạo `var currentDelay = BASE_BATCH_DELAY_MS` với `BASE_BATCH_DELAY_MS = 250L`, nhân đôi lên tối đa `MAX_BATCH_DELAY_MS = 3000L` khi có lỗi trong lô, và không chờ sau lô cuối cùng), giảm tổng thời gian chờ điều tiết của 100 chương từ 50 giây xuống ~6 giây.
   - Sửa lỗi nuốt `CancellationException` trong vòng lặp retry hiện tại (`DownloadRepository.kt:123`): bắt và ném lại `catch (e: CancellationException) { throw e }` ngay lập tức trong `fetchChapterWithRetry`, kết hợp `currentCoroutineContext().ensureActive()` ở đầu mỗi lô, đầu mỗi lần thử và trước mỗi lần ghi DB.
   - **Fail-Fast cho lỗi không thể phục hồi (`LoginRequiredException`, và phòng thủ cho `CircuitOpenException`)**: Trong `fetchChapterWithRetry`, không retry khi gặp `ScraperException.LoginRequiredException` (phiên Wikidich hết hạn) hoặc `ScraperException.CircuitOpenException` (kiểm tra phòng thủ). Sau khi lưu xong các chương thành công (nếu có) của lô hiện tại, ném lại ngoại lệ này ra khỏi `flow` để dừng ngay lập tức toàn bộ các lô tiếp theo. Tại `DownloadService`, đưa `var lastProgress` ra ngoài khối `try` và bắt riêng `catch (e: ScraperException.LoginRequiredException)` để bảo toàn số chương đã xử lý và hiển thị thông báo tiếng Việt yêu cầu đăng nhập lại: `"Đã tải $downloaded chương. Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại để tải tiếp."` (tuân thủ `.agents/rules/04-scraping.md:39`).
   - **Ngưỡng dừng khi lỗi liên tiếp toàn lô (`MAX_CONSECUTIVE_FAILED_BATCHES = 3`)**: Theo dõi số lô thất bại hoàn toàn liên tiếp (`batchErrors == batch.size`). Nếu đạt 3 lô liên tiếp thất bại hoàn toàn (12 chương liên tiếp lỗi sau retry do HTTP 429/503 hoặc mất kết nối nguồn), dừng sớm vòng lặp tải (`break`) mà không chờ `delay` cuối và không bắn tiếp hàng trăm lô còn lại.

---

### 2. Pre-Work Reading

Đã đọc và đối chiếu đầy đủ các tài liệu quản trị và mã nguồn thực tế theo Pre-Work Protocol (`.agents/rules/02-quality.md`):

- **Tài liệu quy tắc & kiến trúc**:
  - `AGENTS.md`, `.agents/rules/00-core.md`, `.agents/rules/01-coding.md`, `.agents/rules/02-quality.md`, `.agents/rules/04-scraping.md`, `.agents/rules/05-plan-review.md`, `.agents/rules/06-docs.md`.
  - `.agents/agents/reviewer/mental-simulation.md`, `.agents/agents/reviewer/evidence-bar.md`, `.agents/agents/reviewer/allowlist.md`.
  - `docs/architecture.md`, `docs/progress.md`, `docs/lessons-learned.md`, `docs/scraping.md`, `docs/plans/README.md`.
  - Kế hoạch gần nhất: `docs/plans/2026-10-13-major-architecture-upgrades-implementation-plan.md`.
- **File source & test đã khảo sát**:
  - `app/src/main/java/com/personal/apptruyen/data/repository/DownloadRepository.kt` (dòng 1–204: `getChaptersByNumbers`, `downloadChapters`, vòng lặp tuần tự và retry).
  - `app/src/main/java/com/personal/apptruyen/data/repository/IDownloadRepository.kt` (dòng 1–44: chữ ký interface `IDownloadRepository`).
  - `app/src/main/java/com/personal/apptruyen/data/repository/StoryRepository.kt` (dòng 670–675: `DownloadProgress` data class; dòng 638–664: mẫu `coroutineScope` + `Semaphore`).
  - `app/src/main/java/com/personal/apptruyen/data/local/ChapterDao.kt` (dòng 55–107: `updateChapterMetadata`, `@Transaction saveChapterContent`; dòng 133–140: `getChaptersByNumbers` với `ORDER BY chapterNumber ASC`).
  - `app/src/main/java/com/personal/apptruyen/download/DownloadService.kt` (dòng 49–88: `companion object`; dòng 151–239: `startDownload`, `lastProgress`, `cancelCurrentDownload`, khối `catch`; dòng 321–338: `sendCompletionNotification`).
  - `app/src/main/java/com/personal/apptruyen/ui/detail/StoryDetailViewModel.kt` (dòng 157, 173, 236–321: `state.chapters` đã được `observeChapters` sắp xếp `ORDER BY chapterNumber ASC` và `observeDownloadService()`).
  - `app/src/main/java/com/personal/apptruyen/ui/detail/StoryDetailScreen.kt` (dòng 545–606: `DownloadProgressCard`).
  - `app/src/main/java/com/personal/apptruyen/data/remote/BaseScraper.kt` (dòng 53–60: `Collections.synchronizedMap` cache; dòng 69–159: `fetchDocumentWithRetry` và `execute()`).
  - `app/src/main/java/com/personal/apptruyen/data/remote/StorySource.kt` (dòng 48: default `getChapterContentWithTitle`).
  - `app/src/main/java/com/personal/apptruyen/data/remote/TangThuVienScraper.kt` (dòng 297–359: `getChapterContentWithTitle`), `SsTruyenScraper.kt` (dòng 67: `ConcurrentHashMap`, dòng 616–651: `getChapterContent`), `WikidichScraper.kt` (dòng 209–239: `getChapterContent`, dòng 297–305: `checkLoginRequired` ném `LoginRequiredException`).
  - `app/src/main/java/com/personal/apptruyen/data/remote/ScraperException.kt` (dòng 1–82: hệ phân cấp `ScraperException`, bao gồm `LoginRequiredException` và `CircuitOpenException`).
  - `app/src/main/java/com/personal/apptruyen/di/AppModule.kt` (dòng 53–63: `OkHttpClient` với `ConnectionPool(5, 30, TimeUnit.SECONDS)`).
  - `app/src/test/java/com/personal/apptruyen/data/repository/DownloadRepositoryTest.kt` (dòng 1–89: unit test hiện có của `DownloadRepository`).
  - `app/src/test/java/com/personal/apptruyen/data/repository/StoryRepositoryTest.kt` (dòng 194–232, 489–502: các unit test tích hợp của `DownloadRepository`).

---

### 3. Assumptions & Cross-Task Dependencies

1. **Giữ nguyên 100% chữ ký `IDownloadRepository`, `DownloadProgress`, `DownloadState` và `StoryDetailViewModel` (Minimal Diffs)**:
   - Hàm `fun downloadChapters(storyId: String, chapters: List<Chapter>): Flow<DownloadProgress>` trong `IDownloadRepository.kt:20-23`, `DownloadProgress` trong `StoryRepository.kt:670-675` và `DownloadService.DownloadState` trong `DownloadService.kt:90-111` giữ nguyên chữ ký.
   - Không cần sửa `StoryDetailViewModel.kt` vì `ChapterDao.getChaptersByStory` (`ChapterDao.kt:10`), `ChapterDao.getChaptersByNumbers` (`ChapterDao.kt:134`) và `DownloadRepository.downloadChapters` đều đã sắp xếp tăng dần theo `chapterNumber`.
2. **Thread-safety của tầng Scraper (`StorySource`) khi gọi song song 4 chương**:
   - `BaseScraper.cache` (`BaseScraper.kt:54-59`) đã được bọc bằng `Collections.synchronizedMap(...)`.
   - `SsTruyenScraper.novelIdCache` (`SsTruyenScraper.kt:67`) dùng `ConcurrentHashMap<String, Long>()`.
   - `TangThuVienScraper.getChapterContentWithTitle` (`TangThuVienScraper.kt:297-359`), `SsTruyenScraper.getChapterContent` (`SsTruyenScraper.kt:616-651`) và `WikidichScraper.getChapterContent` (`WikidichScraper.kt:209-239`, gọi qua default `StorySource.getChapterContentWithTitle` tại `StorySource.kt:48`) đều chỉ sử dụng biến cục bộ và các hằng số `Regex` bất biến (thread-safe).
3. **Tương thích với `@Transaction` của `ChapterDao` và hành vi phát tiến độ theo lô**:
   - Hàm `chapterDao.saveChapterContent(storyId, chapterNumber, content)` (`ChapterDao.kt:72-90`) đã là một `@Transaction` nguyên tử của Room 3 (`insertChapterIfNotExists` + `updateChapterContent`).
   - Do giai đoạn ghi DB được thực hiện **tuần tự trên duy nhất 1 coroutine** (sau khi `awaitAll()` và `.sortedBy { it.chapter.chapterNumber }`), không có 2 coroutine nào ghi `chapterDao` cùng lúc, và giữ nguyên lời gọi 3 tham số `chapterDao.saveChapterContent(storyId, chapterNumber, content)` để tương thích 100% với các mock trong `StoryRepositoryTest.kt:205,225`.
   - Tiến độ `DownloadProgress` được phát ra ở đầu mỗi lô (hiển thị `batch.first().title`) và phát tuần tự 4 lần liên tiếp ngay khi ghi xong từng chương của lô. Nếu người dùng bấm Hủy tải giữa lúc một lô đang tải dở, tối đa `DOWNLOAD_BATCH_SIZE - 1 = 3` chương đã tải xong trong RAM của lô đang chạy sẽ bị bỏ qua, trong khi toàn bộ các lô đã hoàn tất trước đó đều được bảo toàn nguyên vẹn trong Room DB.
4. **Hành vi Fail-Fast và Thông báo tiếng Việt trên `DownloadService` khi hết hạn phiên đăng nhập hoặc lỗi liên tiếp**:
   - Khi gặp `ScraperException.LoginRequiredException` (phiên Wikidich hết hạn, `WikidichScraper.kt:297-305`) hoặc `ScraperException.CircuitOpenException` (nhánh kiểm tra phòng thủ phòng khi `ScraperCircuitBreaker` được tích hợp vào luồng tải trong tương lai): `fetchChapterWithRetry` không retry chương đó; sau khi vòng lặp ghi DB của lô hiện tại lưu xong các chương thành công (nếu có), `downloadChapters` ném lại ngoại lệ `fatalError` ra khỏi `flow`.
   - Tại `DownloadService.kt:169-218`, biến `var lastProgress: DownloadProgress? = null` được đưa ra ngoài khối `try` để bảo toàn tiến độ đã xử lý (`val downloaded = lastProgress?.current ?: 0`, `val errors = (lastProgress?.errors ?: 0).coerceAtLeast(1)`). Nhánh `catch (e: ScraperException.LoginRequiredException)` đặt `DownloadState.Completed(storyId, storyTitle, downloaded, errors)` và gọi `sendCompletionNotification(storyTitle, downloaded, errors, loginRequired = true)` sử dụng hàm thuần `buildCompletionText(downloaded, errors, loginRequired = true)` để hiển thị thông báo tiếng Việt rõ ràng: `"Đã tải $downloaded chương. Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại để tải tiếp."` (tuân thủ `.agents/rules/04-scraping.md:39`).
   - Khi 3 lô liên tiếp thất bại hoàn toàn (`consecutiveFailedBatches >= MAX_CONSECUTIVE_FAILED_BATCHES = 3`, tức 12 chương liên tiếp lỗi sau retry): `downloadChapters` thoát sớm vòng lặp bằng `break`; `DownloadService.kt:203-208` đọc `lastProgress` và hiển thị thông báo tiếng Việt `"Đã tải $downloaded chương ($errors lỗi)"` (giữ nguyên ngữ nghĩa `downloaded = progress.current` có sẵn của dự án là tổng số chương đã xử lý qua vòng lặp kèm số lỗi).
5. **Kế thừa CoroutineContext trong `coroutineScope` (S2 Testability)**:
   - Bên trong `flow { ... }`, sử dụng `coroutineScope { batch.map { chapter -> async { ... } }.awaitAll() }` kế thừa trực tiếp `CoroutineContext` của collector (trong production là `Dispatchers.IO` từ `DownloadService.serviceScope` tại `DownloadService.kt:115`; trong unit test là `StandardTestDispatcher` của `runTest`). Không hardcode `async(Dispatchers.IO)` trong `DownloadRepository` để bộ lập lịch thời gian ảo (`TestCoroutineScheduler`) của `kotlinx-coroutines-test` kiểm soát hoàn toàn `delay` và chứng minh được tính song song deterministic.

---

### 4. Verified Versions Table

> Không thêm/đổi dependency trong `app/build.gradle.kts`. Bảng dưới đây xác minh các thư viện liên quan trực tiếp đang sử dụng trong dự án:

| Thư viện | Version hiện tại | Version đề xuất | Latest đã xác minh | Nguồn URL | Tương thích? |
| -------- | ---------------- | --------------- | ------------------ | --------- | ------------ |
| `kotlinx-coroutines-android` / `kotlinx-coroutines-test` | `1.11.0` | `1.11.0` (giữ nguyên) | `1.11.0` | [kotlinlang.org/docs/composing-suspending-functions.html](https://kotlinlang.org/docs/composing-suspending-functions.html) | ✅ `coroutineScope`, `async`, `awaitAll`, `ensureActive` ổn định, tương thích Kotlin 2.4.21 |
| `androidx.room3:room3-runtime` | `3.0.3` | `3.0.3` (giữ nguyên) | `3.0.3` | [developer.android.com/jetpack/androidx/releases/room3](https://developer.android.com/jetpack/androidx/releases/room3) | ✅ `@Transaction suspend fun saveChapterContent` tương thích `BundledSQLiteDriver` |
| `com.squareup.okhttp3:okhttp` | `5.3.2` | `5.3.2` (giữ nguyên) | `5.3.2` | [square.github.io/okhttp](https://square.github.io/okhttp/) | ✅ `ConnectionPool(5, 30, TimeUnit.SECONDS)` giữ tối đa 5 kết nối nhàn rỗi để tái sử dụng cho 4 luồng tải song song |

---

### 5. Files Cần Chỉnh Sửa / Tạo Mới

| Nhãn       | File | Tầng | Ghi chú (file nhạy cảm → lý do) |
| ---------- | ---- | ---- | ------------------------------- |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/data/repository/DownloadRepository.kt` | `data/repository` | Chuyển `downloadChapters` sang tải song song theo lô (`DOWNLOAD_BATCH_SIZE = 4`), chọn nguồn an toàn qua `firstOrNull { it.url.isNotBlank() }`, sắp xếp kết quả theo `chapterNumber` tăng dần trước khi lưu Room DB và phát `DownloadProgress`, ném lại `CancellationException`, fail-fast khi gặp `LoginRequiredException`/`CircuitOpenException` hoặc `MAX_CONSECUTIVE_FAILED_BATCHES = 3`, tối ưu adaptive delay giữa các lô, và chunk `getChaptersByNumbers` theo `SQLITE_MAX_BIND_ARGS = 900`. |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/download/DownloadService.kt` | `download` | Đưa `var lastProgress: DownloadProgress? = null` ra ngoài khối `try`, bắt riêng `catch (e: ScraperException.LoginRequiredException)` để bảo toàn số chương đã xử lý và thông báo tiếng Việt yêu cầu đăng nhập lại qua hàm thuần `internal fun buildCompletionText(downloaded: Int, errors: Int, loginRequired: Boolean = false): String`. |
| `[MODIFY]` | `app/src/test/java/com/personal/apptruyen/data/repository/DownloadRepositoryTest.kt` | `test` | **[NHẠY CẢM - Test File]**: Chỉ **THÊM MỚI** 8 unit test kiểm chứng tải song song bằng thời gian ảo (`testScheduler.currentTime`), giới hạn đồng thời tối đa (`maxInFlight == 4`), bảo toàn thứ tự lưu DB/emit progress khi mạng hoàn tất ngược thứ tự, adaptive backoff giữa ≥ 2 lô, fail-fast `LoginRequiredException` (rõ hành vi cả 4 chương trong lô 1), dừng sớm sau 3 lô lỗi liên tiếp, 2 kịch bản hủy coroutine (`CancellationException` trực tiếp và `job.cancel()` khi scraper bọc lỗi thành `ParseException`), và phân mảnh `getChaptersByNumbers`. Giữ nguyên 100% các test hiện có. |
| `[NEW]` | `app/src/test/java/com/personal/apptruyen/download/DownloadServiceTest.kt` | `test` | **[NHẠY CẢM - Test File]**: Tạo mới unit test cho hàm thuần `DownloadService.buildCompletionText(downloaded, errors, loginRequired)` kiểm chứng cả 3 nhánh: hết hạn phiên đăng nhập (`loginRequired = true`), hoàn tất có lỗi (`errors > 0`), và hoàn tất thành công (`errors == 0`). |
| `[MODIFY]` | `docs/architecture.md` | `docs` | Cập nhật mô tả luồng tải chương song song theo lô và timestamp. |
| `[MODIFY]` | `docs/progress.md` | `docs` | Ghi nhận hoàn thành tính năng tối ưu tốc độ tải chương song song theo lô. |
| `[MODIFY]` | `docs/changelog.md` | `docs` | Thêm mục cải tiến tốc độ tải chương dưới `## [Unreleased]`. |
| `[MODIFY]` | `docs/plans/README.md` | `docs` | Cập nhật trạng thái plan trong Bảng Trạng Thái. |

---

### 6. Các Bước Thực Hiện

- [ ] **Bước 1: Chuẩn hóa hằng số và cấu trúc kết quả tải chương trong `DownloadRepository.kt`**
  - Tại `app/src/main/java/com/personal/apptruyen/data/repository/DownloadRepository.kt`:
    - Thêm `companion object` chứa các hằng số:
      - `const val DOWNLOAD_BATCH_SIZE = 4` (số chương tải song song tối đa mỗi lô).
      - `const val MAX_RETRY_ATTEMPTS = 2` (số lần thử tối đa cho mỗi chương đối với lỗi tạm thời).
      - `const val BASE_BATCH_DELAY_MS = 250L` (thời gian nghỉ cơ sở giữa 2 lô liên tiếp và giữa 2 lần thử).
      - `const val MAX_BATCH_DELAY_MS = 3000L` (thời gian nghỉ tối đa khi backoff do lỗi).
      - `const val MAX_CONSECUTIVE_FAILED_BATCHES = 3` (ngưỡng số lô thất bại hoàn toàn liên tiếp trước khi dừng sớm).
      - `const val SQLITE_MAX_BIND_ARGS = 900` (giới hạn phòng thủ cho mệnh đề `IN (:chapterNumbers)`).
      - `private val DUMMY_TITLE_REGEX = Regex("^Chương \\d+$")` (biên dịch sẵn regex kiểm tra tiêu đề giả, thay vì khởi tạo lại mỗi chương ở dòng 110).
    - Khai báo `private data class ChapterFetchOutcome(val chapter: Chapter, val result: ChapterContentResult? = null, val error: Exception? = null)` để đóng gói kết quả tải mạng của từng chương mà không chạm vào DB trong nhánh song song.

- [ ] **Bước 2: Nâng cấp `getChaptersByNumbers` trong `DownloadRepository.kt` hỗ trợ danh sách chương lớn và đảm bảo thứ tự**
  - Tại `DownloadRepository.kt:41-53`:
    - Nếu `chapterNumbers.isEmpty()` trả về `emptyList()`.
    - Thực hiện `chapterNumbers.distinct().chunked(SQLITE_MAX_BIND_ARGS).flatMap { chunk -> chapterDao.getChaptersByNumbers(storyId, chunk) }.sortedBy { it.chapterNumber }.map { entity -> ... }` để phòng thủ giới hạn biến SQLite và đảm bảo danh sách trả về luôn tăng dần theo `chapterNumber`.

- [ ] **Bước 3: Triển khai hàm `fetchChapterWithRetry` và luồng tải song song theo lô có sắp xếp + fail-fast trong `DownloadRepository.downloadChapters`**
  - Tại `DownloadRepository.kt:85-144`:
    - Tách hàm hỗ trợ `private suspend fun fetchChapterWithRetry(source: StorySource, chapter: Chapter, retryDelayMs: Long): ChapterFetchOutcome`:
      - Nếu `chapter.url.isBlank()` trả về ngay `ChapterFetchOutcome(chapter = chapter, error = ScraperException.ParseException("URL chương rỗng", ""))` (không retry).
      - Lặp `for (attempt in 1..MAX_RETRY_ATTEMPTS)`:
        - Kiểm tra `currentCoroutineContext().ensureActive()`.
        - Trong khối `try`: gọi `val result = source.getChapterContentWithTitle(chapter.url)` và trả về `ChapterFetchOutcome(chapter = chapter, result = result)`.
        - Trong khối `catch (e: CancellationException)`: **bắt buộc `throw e`** ngay lập tức để không nuốt tín hiệu hủy.
        - Trong khối `catch (e: ScraperException.LoginRequiredException)`: trả về ngay `ChapterFetchOutcome(chapter = chapter, error = e)` (**không retry** khi phiên đăng nhập hết hạn).
        - Trong khối `catch (e: ScraperException.CircuitOpenException)`: trả về ngay `ChapterFetchOutcome(chapter = chapter, error = e)` (**không retry** — nhánh phòng thủ khi mạch nguồn mở).
        - Trong khối `catch (e: Exception)`: lưu `lastError = e`; nếu `attempt < MAX_RETRY_ATTEMPTS` thì `delay(retryDelayMs)`.
      - Nếu hết số lần thử vẫn lỗi: trả về `ChapterFetchOutcome(chapter = chapter, error = lastError)`.
    - Trong `override fun downloadChapters(storyId: String, chapters: List<Chapter>): Flow<DownloadProgress> = flow { ... }`:
      - Nếu `chapters.isEmpty()` thì `return@flow`.
      - Khởi tạo biến trạng thái:
        - `var downloaded = 0`
        - `var errors = 0`
        - `var currentDelay = BASE_BATCH_DELAY_MS`
        - `var consecutiveFailedBatches = 0`
      - Sắp xếp đầu vào: `val sortedChapters = chapters.sortedBy { it.chapterNumber }`.
      - Xác định nguồn an toàn: `val firstValidUrl = sortedChapters.firstOrNull { it.url.isNotBlank() }?.url ?: ""` và `val source = sourceFor(firstValidUrl)` (ném ngay `ScraperException.ParseException` nếu nguồn không hỗ trợ).
      - Chia thành các lô: `val batches = sortedChapters.chunked(DOWNLOAD_BATCH_SIZE)`.
      - Duyệt `for ((batchIndex, batch) in batches.withIndex())`:
        1. Gọi `currentCoroutineContext().ensureActive()`.
        2. Phát tiến độ bắt đầu lô: `emit(DownloadProgress(downloaded, sortedChapters.size, batch.first().title, errors))`.
        3. Tải song song các chương trong lô:
           ```kotlin
           val batchOutcomes =
               coroutineScope {
                   batch
                       .map { chapter ->
                           async {
                               fetchChapterWithRetry(source, chapter, currentDelay)
                           }
                       }.awaitAll()
               }
           ```
        4. **Sắp xếp kết quả theo đúng thứ tự `chapterNumber` tăng dần**:
           `val orderedOutcomes = batchOutcomes.sortedBy { it.chapter.chapterNumber }`
        5. **Lưu tuần tự vào Room DB và phát tiến độ theo đúng thứ tự chương**:
           - Khởi tạo `var batchErrors = 0` và `var fatalError: ScraperException? = null`.
           - Với mỗi `outcome` trong `orderedOutcomes`:
             - Gọi `currentCoroutineContext().ensureActive()`.
             - Nếu `outcome.result != null`:
               - Gọi `chapterDao.saveChapterContent(storyId, outcome.chapter.chapterNumber, outcome.result.content)`.
               - Nếu `!outcome.result.title.isNullOrBlank()` và `(outcome.chapter.title.matches(DUMMY_TITLE_REGEX) || outcome.chapter.title.isBlank())`: gọi `chapterDao.updateChapterMetadata(storyId = storyId, chapterNumber = outcome.chapter.chapterNumber, title = outcome.result.title, url = outcome.chapter.url)`.
             - Ngược lại (`outcome.result == null`):
               - Tăng `errors++`, `batchErrors++` và ghi log `Log.e("DownloadRepo", "Download error for ${outcome.chapter.title}", outcome.error)`.
               - Nếu `fatalError == null && (outcome.error is ScraperException.LoginRequiredException || outcome.error is ScraperException.CircuitOpenException)`: gán `fatalError = outcome.error`.
             - Tăng `downloaded++` và gọi `emit(DownloadProgress(downloaded, sortedChapters.size, outcome.chapter.title, errors))`.
        6. **Kiểm tra điều kiện dừng sớm (Fail-Fast)**:
           - Nếu `fatalError != null`: `throw fatalError` ngay sau khi đã lưu xong các chương thành công trong lô hiện tại để dừng toàn bộ các lô kế tiếp.
           - Cập nhật `consecutiveFailedBatches = if (batchErrors == batch.size) consecutiveFailedBatches + 1 else 0`.
           - Nếu `consecutiveFailedBatches >= MAX_CONSECUTIVE_FAILED_BATCHES`: ghi log `Log.w("DownloadRepo", "Aborting download after $consecutiveFailedBatches consecutive failed batches for $storyId")` và `break` ngay lập tức (không chờ `delay` và không chạy các lô tiếp theo).
        7. **Điều tiết tốc độ thích ứng giữa các lô (Inter-Batch Adaptive Delay)**:
           - Cập nhật `currentDelay = if (batchErrors == 0) BASE_BATCH_DELAY_MS else (currentDelay * 2).coerceAtMost(MAX_BATCH_DELAY_MS)`.
           - Nếu chưa phải lô cuối (`batchIndex < batches.lastIndex`), gọi `delay(currentDelay)`.

- [ ] **Bước 4: Cập nhật `DownloadService.kt` để bảo toàn số chương đã xử lý và báo người dùng đăng nhập lại bằng tiếng Việt khi hết hạn phiên**
  - Tại `app/src/main/java/com/personal/apptruyen/download/DownloadService.kt`:
    - Thêm `import com.personal.apptruyen.data.remote.ScraperException`.
    - Trong `companion object` (`DownloadService.kt:49-88`), tách hàm thuần dựng nội dung thông báo hoàn tất:
      ```kotlin
      internal fun buildCompletionText(
          downloaded: Int,
          errors: Int,
          loginRequired: Boolean = false,
      ): String =
          when {
              loginRequired ->
                  "Đã tải $downloaded chương. Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại để tải tiếp."
              errors > 0 -> "Đã tải $downloaded chương ($errors lỗi)"
              else -> "Đã tải $downloaded chương thành công"
          }
      ```
    - Trong `startDownload` (`DownloadService.kt:167-219`):
      - Đưa `var lastProgress: DownloadProgress? = null` ra ngay đầu `serviceScope.launch` (bên ngoài khối `try`).
      - Bổ sung nhánh `catch (e: ScraperException.LoginRequiredException)` trước `catch (e: Exception)` và dùng `lastProgress` trong cả 2 nhánh `catch`:
        ```kotlin
        } catch (e: CancellationException) {
            // User hủy — đã xử lý trong cancelCurrentDownload
            throw e
        } catch (e: ScraperException.LoginRequiredException) {
            Timber.w(e, "Download stopped: login required")
            val downloaded = lastProgress?.current ?: 0
            val errors = (lastProgress?.errors ?: 0).coerceAtLeast(1)
            _downloadState.value = DownloadState.Completed(storyId, storyTitle, downloaded, errors)
            sendCompletionNotification(storyTitle, downloaded, errors, loginRequired = true)
        } catch (e: Exception) {
            Timber.e(e, "Download failed")
            val downloaded = lastProgress?.current ?: 0
            val errors = (lastProgress?.errors ?: 0).coerceAtLeast(1)
            _downloadState.value = DownloadState.Completed(storyId, storyTitle, downloaded, errors)
            sendCompletionNotification(storyTitle, downloaded, errors)
        }
        ```
    - Tại `sendCompletionNotification` (`DownloadService.kt:321-338`), thêm tham số mặc định `loginRequired: Boolean = false` và gọi `val text = buildCompletionText(downloaded, errors, loginRequired)`.

- [ ] **Bước 5: Bổ sung Unit Tests trong `DownloadRepositoryTest.kt` và `DownloadServiceTest.kt`**
  - Tại `app/src/test/java/com/personal/apptruyen/data/repository/DownloadRepositoryTest.kt`:
    - Trong `@BeforeEach setUp()`: thêm `mockkStatic(android.util.Log::class)` (stub `Log.d`, `Log.w`, `Log.e` trả về `0`) và thêm `@AfterEach tearDown()` gọi `unmockkStatic(android.util.Log::class)`.
    - Giữ nguyên 2 test `deleteStory` hiện có, bổ sung 8 test mới bao phủ toàn bộ Test Contract của `DownloadRepository` (TC-01..TC-08):
      1. `downloadChapters executes batch in parallel and persists in ascending chapterNumber order when network finishes out of order` (TC-01): truyền `[Ch 3, Ch 1, Ch 4, Ch 2]` với độ trễ mạng `Ch 1 = 80ms, Ch 2 = 60ms, Ch 3 = 40ms, Ch 4 = 10ms`; xác nhận `testScheduler.currentTime == 80L` (chứng minh chạy song song, khác `190L` tuần tự), thứ tự hoàn tất mạng trong RAM là `[4, 3, 2, 1]`, nhưng `coVerifyOrder` trên `chapterDao.saveChapterContent` và `DownloadProgress` phát ra đúng thứ tự `1 → 2 → 3 → 4`.
      2. `downloadChapters bounds concurrent requests to DOWNLOAD_BATCH_SIZE and updates dummy titles across batches` (TC-02): truyền 10 chương (`1..10`), dùng `AtomicInteger` đo `inFlight` và `maxInFlight` quanh `delay(50L)`; xác nhận `maxInFlight.get() == 4` (không bao giờ > 4), và kiểm tra `chapterDao.updateChapterMetadata` chỉ gọi cho chương có tiêu đề dummy `"Chương X"` hoặc rỗng.
      3. `downloadChapters retries failed chapter and doubles inter-batch delay on batch error` (TC-03): truyền 8 chương (2 lô: `1..4` và `5..8`); ở lô 1, Ch 2 lỗi lần 1 nhưng thành công lần 2, Ch 3 lỗi cả 2 lần (`batchErrors == 1`), Ch 1 và Ch 4 thành công ngay; ở lô 2 (`5..8`) tất cả thành công ngay (`0ms`); xác nhận `testScheduler.currentTime == 750L` (`250ms` retry trong lô 1 + `500ms` backoff delay giữa lô 1 và lô 2, gấp đôi mức `250L` khi không lỗi), lưu đúng thứ tự `1, 2, 4, 5, 6, 7, 8`, và `last().errors == 1`.
      4. `downloadChapters rethrows CancellationException immediately without retrying` (TC-04a): khi `source.getChapterContentWithTitle` ném `CancellationException` ở lần thử 1, bắt bằng `try/catch (e: CancellationException)` trong `runTest`, xác nhận ngoại lệ là `CancellationException` và `coVerify(exactly = 1)` (không retry lần 2).
      5. `downloadChapters stops before DB write when job is cancelled while scraper wraps CancellationException into ParseException` (TC-04b): mô phỏng `TangThuVienScraper.kt:355` bằng mock gọi `try { delay(100L); ... } catch (e: Exception) { throw ScraperException.ParseException("wrapped", "") }`; khởi chạy collector trong `val job = launch { repo.downloadChapters(...).collect {} }`, `advanceTimeBy(50L)`, gọi `job.cancel()`, `advanceUntilIdle()`; xác nhận `ensureActive()` chặn đứng ngay và `coVerify(exactly = 0) { chapterDao.saveChapterContent(any(), any(), any()) }`.
      6. `downloadChapters fails fast without retry on LoginRequiredException after saving successful chapters in current batch` (TC-05): với 8 chương (2 lô `1..4` và `5..8`), trong lô 1 cấu hình tường minh: `Ch 1` trả về nội dung thành công, còn `Ch 2, Ch 3, Ch 4` đều ném `ScraperException.LoginRequiredException("wikidich")`; thu thập flow trong `try/catch (e: ScraperException.LoginRequiredException)` của `runTest`, xác nhận `Ch 2, Ch 3, Ch 4` mỗi chương chỉ gọi mạng đúng 1 lần (`coVerify(exactly = 1)`, không retry), chỉ `Ch 1` được lưu vào `chapterDao.saveChapterContent("s1", 1, "Nội dung 1")` (`coVerify(exactly = 1)` cho tổng số lần gọi `saveChapterContent`), luồng ném `LoginRequiredException` ngay sau lô 1 và tuyệt đối không gọi mạng cho lô 2 (`Ch 5..8`, `coVerify(exactly = 0)`).
      7. `downloadChapters aborts early after MAX_CONSECUTIVE_FAILED_BATCHES completely failed batches` (TC-06): với 16 chương (4 lô), khi mọi chương đều ném lỗi mạng, vòng lặp dừng ngay sau lô thứ 3 (12 chương, `last().current == 12, last().errors == 12`) và không gọi mạng cho lô thứ 4 (`Ch 13..16`).
      8. `getChaptersByNumbers chunks large chapter lists and returns sorted chapters` (TC-08): truyền 1.200 số chương ngược thứ tự (`1200 downTo 1`), xác nhận `chapterDao.getChaptersByNumbers` được gọi 2 lần (900 + 300 phần tử) và kết quả trả về tăng dần `1..1200`.
  - Tạo mới `app/src/test/java/com/personal/apptruyen/download/DownloadServiceTest.kt` (TC-09):
    - Kiểm thử 3 test case cho `DownloadService.buildCompletionText`:
      1. `buildCompletionText returns Vietnamese re-login message with downloaded count when loginRequired is true`: `assertEquals("Đã tải 44 chương. Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại để tải tiếp.", DownloadService.buildCompletionText(downloaded = 44, errors = 3, loginRequired = true))`.
      2. `buildCompletionText returns error count summary when loginRequired is false and errors greater than zero`: `assertEquals("Đã tải 12 chương (3 lỗi)", DownloadService.buildCompletionText(downloaded = 12, errors = 3, loginRequired = false))`.
      3. `buildCompletionText returns success message when loginRequired is false and errors is zero`: `assertEquals("Đã tải 20 chương thành công", DownloadService.buildCompletionText(downloaded = 20, errors = 0, loginRequired = false))`.

- [ ] **Bước 6: Chạy Verification Gate & Cập nhật Documentation**
  - Chạy `.\gradlew.bat compileDebugKotlin`, `.\gradlew.bat testDebugUnitTest`, `.\gradlew.bat assembleDebug` và kiểm tra `.\gradlew.bat ktlintCheck` (đảm bảo 0 lỗi ktlint trên `DownloadRepository.kt`, `DownloadService.kt`, `DownloadRepositoryTest.kt` và `DownloadServiceTest.kt`; không chạm vào 2 file ngoài phạm vi đang có lỗi ktlint sẵn là `SourceAuthManager.kt` và `SettingsViewModel.kt`).
  - Cập nhật `docs/architecture.md`, `docs/progress.md`, `docs/changelog.md`, `docs/plans/README.md` và chuyển trạng thái plan sang `Completed` sau khi hoàn tất triển khai.

---

### 7. Test Contract

| # | Hành vi (ngôn ngữ nghiệp vụ) | Input | Kết quả mong đợi / thông báo lỗi tiếng Việt |
| - | ---------------------------- | ----- | ------------------------------------------- |
| TC-01 | Tải song song 1 lô 4 chương khi thời gian mạng trả về ngược thứ tự (chương 4 xong trước chương 1) | `chapters = [Ch 3, Ch 1, Ch 4, Ch 2]`; thời gian phản hồi mạng: `Ch 4 (10ms) < Ch 3 (40ms) < Ch 2 (60ms) < Ch 1 (80ms)` | Thời gian ảo `testScheduler.currentTime == 80L` (chứng minh chạy song song, khác `190L` tuần tự); thứ tự hoàn tất mạng trong RAM là `[4, 3, 2, 1]`; `chapterDao.saveChapterContent` được gọi tuần tự đúng thứ tự `chapterNumber = 1, 2, 3, 4`; `DownloadProgress` hoàn thành theo đúng thứ tự `Ch 1 → Ch 2 → Ch 3 → Ch 4` với `last().current == 4, errors == 0`. |
| TC-02 | Giới hạn cứng số luồng đồng thời (`maxInFlight == 4`) và cập nhật tiêu đề thực cho chương có tiêu đề tạm `"Chương X"` qua nhiều lô | `chapters` gồm 10 chương (`1..10`, chia thành 3 lô 4+4+2), mỗi chương `delay(50L)`; chương 1 có title `"Chương 1"` (scraper trả về `"Chương 1: Mở đầu"`), chương 2 có title `"Chương 2: Đã có tên"` | Số coroutine chạy đồng thời tối đa `maxInFlight == 4` (không bao giờ > 4); lưu đủ 10 chương theo thứ tự `1..10`; chỉ gọi `chapterDao.updateChapterMetadata` cho chương có tiêu đề dummy/rỗng (như chương 1), không ghi đè tiêu đề đã có của chương 2. |
| TC-03 | Xử lý lỗi cục bộ, thử lại (retry) và nhân đôi thời gian chờ (`backoff`) giữa 2 lô liên tiếp | 8 chương (`1..8`, chia làm 2 lô): ở lô 1 (`1..4`), Ch 2 lỗi lần 1 và thành công lần 2 (sau `250ms`), Ch 3 lỗi cả 2 lần (sau `250ms`), Ch 1 và Ch 4 thành công ngay (`0ms`); ở lô 2 (`5..8`), cả 4 chương thành công ngay (`0ms`) | Ch 1, 2, 4, 5, 6, 7, 8 được lưu vào Room DB theo đúng thứ tự tăng dần; Ch 3 không lưu vào DB; `DownloadProgress` cuối có `current == 8, errors == 1`; tổng thời gian ảo `testScheduler.currentTime == 750L` (`250ms` retry trong lô 1 + `500ms` backoff giữa lô 1 và lô 2, gấp đôi mức `250L` khi không có lỗi). |
| TC-04 | Hủy tải giữa chừng (`CancellationException` trực tiếp hoặc `job.cancel()` khi scraper bọc lỗi thành `ParseException`) | (a) `source.getChapterContentWithTitle` ném `CancellationException` ở attempt 1; (b) `job.cancel()` tại `t = 50ms` khi scraper đang `delay(100ms)` và bọc `CancellationException` thành `ParseException` (giống `TangThuVienScraper.kt:355`) | (a) Bắt được `CancellationException` ngay lập tức, `coVerify(exactly = 1)` (không retry lần 2); (b) `ensureActive()` chặn đứng ngay khi thoát khỏi scraper, `coVerify(exactly = 0) { chapterDao.saveChapterContent(any(), any(), any()) }` (không ghi rác vào DB). |
| TC-05 | Dừng sớm (Fail-Fast) khi gặp `ScraperException.LoginRequiredException` (phiên đăng nhập hết hạn) | 8 chương (`1..8`, 2 lô): trong lô 1 (`1..4`), `Ch 1` thành công, còn `Ch 2, Ch 3, Ch 4` đều ném `LoginRequiredException("wikidich", "Vui lòng đăng nhập để xem nội dung từ Wikidich")` | `Ch 2, Ch 3, Ch 4` mỗi chương chỉ gọi mạng đúng 1 lần (`coVerify(exactly = 1)`, không retry); chỉ `Ch 1` được lưu vào Room DB; ngay sau lô 1, `downloadChapters` ném `LoginRequiredException` ra ngoài và tuyệt đối không gọi mạng cho lô 2 (`Ch 5..8`, `coVerify(exactly = 0)`). |
| TC-06 | Dừng sớm khi 3 lô liên tiếp thất bại hoàn toàn (`MAX_CONSECUTIVE_FAILED_BATCHES = 3`) | 16 chương (`1..16`, 4 lô): nguồn liên tục trả lỗi HTTP 429 / `NetworkException` cho mọi chương | Sau khi 3 lô đầu tiên (12 chương) đều thất bại hoàn toàn (`batchErrors == 4`), vòng lặp dừng sớm (`break`); tiến độ cuối có `current == 12, errors == 12`; lô thứ 4 (`Ch 13..16`) không bao giờ được gọi mạng. |
| TC-07 | Nguồn truyện không được hỗ trợ | `chapters = [Chapter(url = "https://unsupported.com/ch-1")]` (đã kiểm thử sẵn trong `StoryRepositoryTest`) | Ném `ScraperException.ParseException` với thông báo tiếng Việt `"Nguồn truyện không được hỗ trợ"`. |
| TC-08 | Truy vấn danh sách chương lớn (> 900 chương) qua `getChaptersByNumbers` | Danh sách 1.200 số chương không theo thứ tự (`1200 downTo 1`) | Chia thành 2 truy vấn `chapterDao.getChaptersByNumbers` (900 + 300 phần tử), kết quả trả về gồm 1.200 chương sắp xếp tăng dần `1..1200`. |
| TC-09 | Định dạng thông báo hoàn tất tiếng Việt trong `DownloadService.buildCompletionText` khi hết hạn phiên, có lỗi, hoặc thành công | `(downloaded = 44, errors = 3, loginRequired = true)`; `(downloaded = 12, errors = 3, loginRequired = false)`; `(downloaded = 20, errors = 0, loginRequired = false)` | Trả về lần lượt: `"Đã tải 44 chương. Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại để tải tiếp."`; `"Đã tải 12 chương (3 lỗi)"`; `"Đã tải 20 chương thành công"`. |

---

### 8. Verification Plan

- **Unit Tests**:
  - File test: `app/src/test/java/com/personal/apptruyen/data/repository/DownloadRepositoryTest.kt`, `app/src/test/java/com/personal/apptruyen/download/DownloadServiceTest.kt` và `app/src/test/java/com/personal/apptruyen/data/repository/StoryRepositoryTest.kt`
  - Chạy riêng các test class liên quan:
    ```powershell
    .\gradlew.bat testDebugUnitTest --tests "com.personal.apptruyen.data.repository.DownloadRepositoryTest" --tests "com.personal.apptruyen.download.DownloadServiceTest" --tests "com.personal.apptruyen.data.repository.StoryRepositoryTest"
    ```
- **Tier 2 Quality Gate (Bắt buộc trước khi báo cáo hoàn tất)**:
  ```powershell
  .\gradlew.bat ktlintCheck; .\gradlew.bat testDebugUnitTest; .\gradlew.bat assembleDebug
  ```
  > **Lưu ý về baseline `ktlintCheck` (Minimal Diffs Policy)**: Hiện tại `ktlintCheck` trên repo đang báo 2 lỗi có sẵn ngoài phạm vi task tại `SourceAuthManager.kt:51` và `SettingsViewModel.kt:100`. Khi triển khai, tuyệt đối giữ **0 lỗi ktlint** trên mọi file thuộc phạm vi task (`DownloadRepository.kt`, `DownloadService.kt`, `DownloadRepositoryTest.kt`, `DownloadServiceTest.kt`) và không tự ý sửa các file ngoài phạm vi nếu không được yêu cầu.
- **Kiểm thử thủ công trên thiết bị (dành cho User)**:
  1. Mở một truyện bất kỳ từ Tàng Thư Viện, SsTruyện hoặc Wikidich trên `StoryDetailScreen`.
  2. Bấm tải 20–50 chương (hoặc Tải tất cả): quan sát thanh tiến độ trên `DownloadProgressCard` và Notification tăng theo từng cụm 4 chương/lô, tốc độ tải nhanh hơn rõ rệt (~4–5 lần so với trước).
  3. Mở `ReaderScreen` đọc offline và kiểm tra `ChapterListPanel`: các chương đã tải có dấu tích xanh đầy đủ, nội dung và số thứ tự chương khớp chính xác 100%, không bị lệch chương.
  4. Thử bấm nút **Hủy (X)** khi đang tải: tiến trình dừng ngay lập tức, các chương ở các lô đã tải trước đó vẫn đọc offline bình thường.
  5. Với nguồn Wikidich khi phiên đăng nhập hết hạn (hoặc sau khi đăng xuất trong Cài đặt nguồn): bấm tải chương bị khóa → tiến trình dừng ngay sau lô đầu tiên và hiển thị thông báo tiếng Việt `"Đã tải X chương. Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại để tải tiếp."`.
- **Negative Probe**: Không thêm rule ktlint/lint/hook mới nên không yêu cầu negative probe.

---

### 9. Rủi Ro & Phòng Ngừa

| Rủi ro | Mức (Cao/TB/Thấp) | Technical control |
| ------ | ----------------- | ----------------- |
| **R1: Các chương trong cùng một lô hoàn tất HTTP lệch thứ tự làm xáo trộn thứ tự lưu DB và hiển thị tiến độ** | **Cao** | Sắp xếp danh sách chương đầu vào `chapters.sortedBy { it.chapterNumber }` trước khi `chunked(DOWNLOAD_BATCH_SIZE)`, và sau khi `awaitAll()` bắt buộc sắp xếp `batchOutcomes.sortedBy { it.chapter.chapterNumber }` rồi mới lưu tuần tự vào `chapterDao` và `emit(DownloadProgress)`. Kiểm chứng bằng thời gian ảo + `coVerifyOrder` trong unit test (TC-01). |
| **R2: Bắn quá nhiều request đồng thời hoặc lặp vô tận khi nguồn trả HTTP 429/503 hoặc hết hạn phiên đăng nhập (`LoginRequiredException`)** | **Cao** | (1) Giới hạn cứng `DOWNLOAD_BATCH_SIZE = 4` (kiểm chứng `maxInFlight == 4` ở TC-02); (2) Duy trì `BASE_BATCH_DELAY_MS = 250L` giữa các lô và nhân đôi thời gian nghỉ lên tối đa `MAX_BATCH_DELAY_MS = 3000L` khi có lỗi trong lô (TC-03); (3) Không retry `LoginRequiredException` (và phòng thủ cho `CircuitOpenException`), ném ra khỏi `flow` ngay sau lô hiện tại để `DownloadService` dừng tải, giữ đúng số chương đã xử lý và thông báo tiếng Việt yêu cầu đăng nhập lại (TC-05, TC-09); (4) Tự động dừng vòng lặp tải (`break`) khi có `MAX_CONSECUTIVE_FAILED_BATCHES = 3` lô liên tiếp thất bại hoàn toàn (TC-06). |
| **R3: Khối `catch (e: Exception)` trong vòng lặp retry nuốt `CancellationException` khiến lệnh Hủy tải bị trễ** | **Trung bình** | Bắt riêng `catch (e: CancellationException) { throw e }` trước `catch (e: Exception)` trong `fetchChapterWithRetry` và gọi `currentCoroutineContext().ensureActive()` ở đầu mỗi lô, đầu mỗi attempt và trước mỗi lần ghi DB (TC-04). |
| **R4: Hàng chương trong DB có `url` rỗng hoặc danh sách số chương truyền vào `getChaptersByNumbers` quá lớn** | **Thấp** | Chọn nguồn qua `sortedChapters.firstOrNull { it.url.isNotBlank() }`, bỏ qua retry nếu `chapter.url.isBlank()`, và phân mảnh phòng thủ `.distinct().chunked(SQLITE_MAX_BIND_ARGS)` (`900`) kèm `.sortedBy { it.chapterNumber }` trong `getChaptersByNumbers` (TC-08). |

#### Phân Tích Đối Kháng (Adversarial Timeline — S3, S5, S6)

```text
Cơ chế 1 (Trục Đồng thời): Tải song song 4 chương trong 1 lô và ghi Room DB
T1: Collector khởi chạy 4 coroutine async cho [Ch 1, Ch 2, Ch 3, Ch 4]. Do kích thước chương và mạng, Ch 4 xong ở t=10ms, Ch 3 xong ở t=40ms, Ch 2 xong ở t=60ms, Ch 1 xong ở t=80ms.
T2: Trong suốt t=0..80ms, các coroutine async chỉ giữ ChapterFetchOutcome trong RAM, tuyệt đối không gọi chapterDao nên không xảy ra tranh chấp lock SQLite.
T3: Tại t=80ms, awaitAll() trả về danh sách kết quả; collector thực hiện batchOutcomes.sortedBy { it.chapter.chapterNumber } và ghi tuần tự Ch 1 → Ch 2 → Ch 3 → Ch 4 vào chapterDao trên cùng 1 coroutine.
Kết cục: Thứ tự ghi DB, downloadedTimestamp và thứ tự emit DownloadProgress luôn tăng dần nghiêm ngặt 1 → 2 → 3 → 4 → AN TOÀN.
```

```text
Cơ chế 2 (Trục TOCTOU & Hết hạn phiên): Phiên đăng nhập Wikidich hết hạn giữa chừng khi đang tải hàng trăm chương
T1: Bắt đầu tải 500 chương Wikidich; các lô 1..10 tải thành công (lastProgress.current = 40). Đến lô 11, Ch 41 thành công, còn Ch 42..44 gặp cookie express.sid hết hạn nên WikidichScraper.checkLoginRequired ném LoginRequiredException.
T2: Trong fetchChapterWithRetry, nhánh catch (e: ScraperException.LoginRequiredException) bắt lấy lỗi và trả về ngay ChapterFetchOutcome(error = e) mà KHÔNG retry lần 2.
T3: Sau khi lưu Ch 41 vào Room DB và cập nhật lastProgress (current = 44, errors = 3), downloadChapters ném fatalError (LoginRequiredException) ra khỏi flow. Nhờ lastProgress khai báo ngoài try, nhánh catch (e: ScraperException.LoginRequiredException) trong DownloadService giữ được downloaded = 44, dừng service ngay lập tức và phát thông báo tiếng Việt: "Đã tải 44 chương. Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại để tải tiếp.", không bắn request cho 456 chương còn lại.
Kết cục: Không retry vô hạn, không spam nguồn, bảo toàn số chương đã xử lý và thông báo rõ ràng bằng tiếng Việt theo 04-scraping.md:39 → AN TOÀN.
```

```text
Cơ chế 3 (Trục Thất bại giữa chừng & Quá tải nguồn): Lỗi cục bộ 1 chương so với lỗi toàn bộ 3 lô liên tiếp (HTTP 429/503)
T1: Nếu chỉ 1–3 chương trong lô lỗi tạm thời (còn ít nhất 1 chương thành công): các chương thành công vẫn được lưu đúng thứ tự, consecutiveFailedBatches reset về 0, và currentDelay nhân đôi (250ms → 500ms → ... → 3000ms) để giảm nhịp cho lô sau.
T2: Nếu nguồn sập hoặc chặn IP trả HTTP 429/503 khiến cả 4 chương trong lô đều lỗi (batchErrors == batch.size) suốt 3 lô liên tiếp (consecutiveFailedBatches == 3).
T3: Ngay khi kết thúc lô thứ 3 thất bại hoàn toàn, downloadChapters gọi break thoát khỏi vòng lặp mà không chạy tiếp các lô 4..N, trả về tiến độ cuối cho DownloadService (giữ nguyên ngữ nghĩa downloaded = số chương đã thử kèm số lỗi).
Kết cục: Bảo toàn các chương đã tải ở các lô trước, đồng thời chặn đứng nguy cơ bắn hàng nghìn request thất bại vào nguồn → AN TOÀN.
```

```text
Cơ chế 4 (Trục Vòng đời Android): Người dùng bấm Hủy tải hoặc DownloadService bị hủy (onDestroy) giữa lô
T1: Lô 4 chương đang thực thi request mạng hoặc đang delay retry (kể cả khi TangThuVienScraper bọc CancellationException thành ParseException).
T2: Người dùng bấm nút Hủy trên Notification/UI → DownloadService.cancelCurrentDownload() gọi downloadJob?.cancel().
T3: Nhánh async nhận CancellationException (hoặc bị chặn bởi currentCoroutineContext().ensureActive() ở đầu attempt kế tiếp và trước mỗi lệnh ghi DB), làm coroutineScope hủy toàn bộ lô và thoát khỏi flow ngay lập tức; tối đa 3 chương đang chờ trong RAM của lô dở dang bị hủy bỏ, các lô trước đã lưu trong Room DB giữ nguyên 100%.
Kết cục: Dừng tức thì, không ghi dữ liệu dở dang, giải phóng sạch WakeLock và Foreground notification → AN TOÀN.
```

---

### 10. Cập Nhật Docs

- `docs/architecture.md`: Cập nhật mục "Luồng dữ liệu" (mục 6. Tải: tải song song theo lô 4 chương kèm sắp xếp theo `chapterNumber` trước khi lưu Room DB, cơ chế dừng sớm fail-fast và thông báo tiếng Việt khi hết hạn phiên trên `DownloadService`) và cập nhật timestamp.
- `docs/progress.md`: Bổ sung mục hoàn thành tối ưu tải chương song song theo lô và cập nhật bảng lịch sử thay đổi + timestamp.
- `docs/changelog.md`: Thêm mục dưới `## [Unreleased]` → `### Sửa đổi` mô tả cơ chế tải chương song song theo lô 4 chương, tự động sắp xếp đúng thứ tự và dừng sớm kèm thông báo đăng nhập lại khi nguồn lỗi liên tiếp/hết hạn phiên.
- `docs/plans/README.md`: Thêm kế hoạch `2026-10-14-parallel-chapter-download-implementation-plan.md` vào Bảng Trạng Thái (và chuyển sang `Completed` khi hoàn tất).

## Audit History

<!-- Reviewer chỉ append "### Audit Run N" ở dưới. Không sửa phần Technical Plan. -->

### Audit Run 1

- **Reviewer**: Claude Code CLI (Claude Opus 5.5 — `claude-opus-5-5`)
- **Ngày**: 2026-10-14
- **Đối tượng**: Technical Plan Revision 1

#### Kiểm chứng lỗi cũ

| # | Lỗi từ Run trước | Trạng thái |
| - | ---------------- | ---------- |
| — | Không có (đây là Audit Run đầu tiên) | N/A |

#### Khảo sát source thực tế (tóm tắt)

- `[SRC]` `DownloadRepository.kt:85-144`: vòng lặp tuần tự hiện tại; `catch (e: Exception)` ở dòng 123 nuốt cả `CancellationException` trong lúc `delay(currentDelay)` retry → plan sửa đúng chỗ.
- `[SRC]` `DownloadRepository.kt:96`: chương rỗng thì gọi `sources.first()` (có thể `NoSuchElementException` khi `sources` rỗng) → plan đổi sang `return@flow` là cải thiện.
- `[SRC]` `ChapterDao.kt:72-90`: `saveChapterContent` là `@Transaction` (insert IGNORE + UPDATE content) → mỗi chương nguyên tử; `ChapterDao.kt:133-139` `getChaptersByNumbers` đã có `ORDER BY chapterNumber ASC`.
- `[SRC]` `DownloadService.kt:115` `serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)`; `:144-149` `onDestroy` hủy job + scope + nhả WakeLock; `:209-211` ném lại `CancellationException`; `:212-215` lỗi khác → `Completed(storyId, title, 0, 1)`.
- `[SRC]` `BaseScraper.kt:54-59` cache `Collections.synchronizedMap`; `BaseScraper.kt:69-91` `fetchDocumentWithRetry` tự retry `MAX_RETRIES = 3` (backoff 1s, 2s) cho cả `HttpException` (bao gồm 429/503).
- `[SRC]` `TangThuVienScraper.kt:297-359` (không phải 280-336 như plan ghi): chỉ dùng biến cục bộ, nhưng `catch (e: Exception)` ở dòng 355 bọc mọi exception (kể cả `CancellationException`) thành `ParseException`.
- `[SRC]` `SsTruyenScraper.kt:616-651` `getChapterContent` dùng biến cục bộ + `fetchDocumentWithRetry`; `novelIdCache` là `ConcurrentHashMap` (dòng 67).
- `[SRC]` `WikidichScraper.kt:209-239` (không phải 170-195 — đoạn đó là `getRemainingChapters`): `getChapterContent` gọi `fetchDocument` (không retry) + `checkLoginRequired` (dòng 297-305) ném `LoginRequiredException` và `cache.clear()`. Wikidich không override `getChapterContentWithTitle` → dùng mặc định `StorySource.kt:48`.
- `[SRC]` `WebViewCookieHandler.kt:20-39`: không giữ state cục bộ, ủy quyền cho `CookieStore` → gọi song song an toàn ở tầng app.
- `[SRC]` `AppModule.kt:60` `ConnectionPool(5, 30, TimeUnit.SECONDS)`.
- `[SRC]` `StoryRepository.kt:25-26,647-648`: dự án đã dùng `awaitAll`/`coroutineScope`/`Semaphore` → không cần import/dependency mới.
- `[SRC]` `StoryDetailViewModel.kt:157,173`: `state.chapters` được ghi đè bởi `observeChapters` (DAO `ORDER BY chapterNumber ASC`, `ChapterDao.kt:10`).
- `[SRC]` Đã tồn tại `app/src/test/java/com/personal/apptruyen/data/repository/DownloadRepositoryTest.kt` (untracked, theo `git status --short` trong evidence) chứa test `deleteStory` của `DownloadRepository`; plan không liệt kê file này trong Pre-Work.

#### Mental Simulation Matrix (S1–S6 + Domain)

| Kịch bản | Kết quả | Bằng chứng | Chi tiết |
| -------- | ------- | ---------- | -------- |
| S1 Build | PASS | `[CMD]` `cmd.exe /d /c gradlew.bat compileDebugKotlin` Exit 0; `[SRC]` `StoryRepository.kt:25-26` | Không đổi dependency/build config; `coroutineScope`/`async`/`awaitAll`/`ensureActive` đã dùng trong dự án, không cần Hilt binding mới. Lưu ý `[CMD]` `ktlintCheck` hiện **Exit 1** do lỗi có sẵn ở `SourceAuthManager.kt:51` và `SettingsViewModel.kt:100` (ngoài phạm vi plan) → xem MINOR-5. |
| S2 Lifecycle | PASS | `[SRC]` `DownloadService.kt:115,144-149,209-218`; `[ADV]` Timeline B | Không tạo scope mới, `async` là con của `coroutineScope` trong `flow` → hủy lan truyền theo `downloadJob`. Không hardcode dispatcher → `runTest` kiểm soát được thời gian ảo. |
| S3 Database | PASS | `[SRC]` `ChapterDao.kt:72-90`; `[ADV]` Timeline A | Không đổi schema. Ghi DB tuần tự trên 1 coroutine sau `awaitAll()`, mỗi chương là 1 `@Transaction`. Hủy giữa lúc ghi lô chỉ để lại các chương đã ghi trọn vẹn. |
| S4 Cross-task state | PASS (kèm MAJOR-3) | `[SRC]` `IDownloadRepository`, `DownloadService.kt:177-200`, `StoryRepositoryTest.kt:197-231,490-502` | Chữ ký interface và `DownloadProgress` giữ nguyên; các test hiện có vẫn thỏa (`first().current == 0`, `last().current == N`). Thay đổi ViewModel (Bước 4) dư thừa và thiếu test → MAJOR-3. |
| S5 Security/Privacy | PASS | `[SRC]` `WebViewCookieHandler.kt:20-39`; `[ADV]` Timeline D | Không log cookie; `Log.e` chỉ in `chapter.title` + exception. Không thêm component Manifest. TOCTOU phiên Wikidich hết hạn giữa chừng không làm lộ dữ liệu, nhưng gây spam request → xếp vào S6 (MAJOR-2). |
| S6 External resilience | **FAIL** | `[SRC]` `BaseScraper.kt:69-91`, `WikidichScraper.kt:297-305`; `[ADV]` Timeline C, D | Song song ×4 khuếch đại request khi nguồn trả 429/503 hoặc phiên hết hạn: không có fail-fast cho `LoginRequiredException`/lỗi liên tiếp → MAJOR-2. |
| Domain (tải offline, tiến độ, hủy) | PASS (kèm MINOR) | `[SRC]` `DownloadService.kt:184-208`; `[ADV]` Timeline B | Tiến độ phát theo đúng thứ tự; hủy giữa lô bỏ phí tối đa 3 chương đã tải xong trong RAM (chấp nhận được, nên ghi nhận — MINOR-6). |

#### Adversarial Timelines

```text
Timeline A (Trục Đồng thời — ghi Room khi Reader đang đọc cùng truyện)
T1: Lô [Ch 5..8] awaitAll() xong; collector ghi tuần tự saveChapterContent(5..8) (mỗi lần 1 @Transaction).
T2: Người dùng mở Reader chương 6, StoryRepository.getChapterContent đọc/ghi cache chương 6 cùng lúc.
T3: Room 3 tuần tự hóa các write transaction; chỉ còn 1 writer từ DownloadRepository (khác trước: vẫn 1 writer) → không tăng tranh chấp lock so với hiện trạng.
Kết cục: Không mất dữ liệu, thứ tự ghi 5 → 8 giữ nguyên → AN TOÀN.
```

```text
Timeline B (Trục Vòng đời — hủy khi TTV đang ở delay backoff bên trong scraper)
T1: async Ch 2 đang ở delay(1000) trong BaseScraper.fetchDocumentWithRetry (TTV) bên trong withContext(IO).
T2: Người dùng bấm Hủy → DownloadService.cancelCurrentDownload() → downloadJob.cancel().
T3: delay ném CancellationException → TangThuVienScraper.kt:355 catch (e: Exception) bọc thành ParseException → fetchChapterWithRetry nhận như lỗi thường (KHÔNG vào nhánh catch CancellationException). Ở attempt 1: delay(retryDelayMs) ném CancellationException ngay vì job đã hủy → thoát. Ở attempt 2: trả ChapterFetchOutcome(error) → collector gọi ensureActive() trước khi ghi DB → ném CancellationException.
Kết cục: Nhờ ensureActive() ở đầu attempt và trước mỗi lần ghi DB, hủy vẫn có hiệu lực ngay cả khi scraper nuốt/bọc CancellationException; không ghi rác, các lô trước nguyên vẹn → AN TOÀN (với điều kiện giữ đủ 3 vị trí ensureActive như plan mô tả).
```

```text
Timeline C (Trục Thất bại giữa chừng — nguồn trả HTTP 429 liên tục cho "Tải tất cả" 1.200 chương TTV)
T1: Lô 1 gồm 4 chương bắn đồng thời; mỗi chương: fetchDocumentWithRetry 3 lần (backoff 1s, 2s) × fetchChapterWithRetry 2 lần = tối đa 6 request/chương → tối đa 24 request/lô trong ~6–7 giây.
T2: Nguồn tiếp tục trả 429 vì tần suất cao hơn trước ~4 lần.
T3: batchErrors > 0 → currentDelay 250 → 500 → 1000 → 2000 → 3000ms (trần) rồi đứng yên; vòng lặp tiếp tục đủ 300 lô, không có điểm dừng.
Kết cục: ~7.200 request thất bại bắn vào nguồn, người dùng nhận "1200 lỗi" sau thời gian dài, có nguy cơ bị chặn IP → LỖI (MAJOR-2).
```

```text
Timeline D (Trục TOCTOU — phiên Wikidich hết hạn giữa chừng)
T1: Bắt đầu tải 500 chương Wikidich, cookie hợp lệ lúc lập danh sách.
T2: Ở lô 30, cookie hết hạn; mọi chương trả trang "Đăng nhập để xem nội dung" → checkLoginRequired ném LoginRequiredException (WikidichScraper.kt:297-305) và cache.clear().
T3: fetchChapterWithRetry coi là lỗi thường, retry rồi trả error; vòng lặp tiếp tục 95 lô còn lại, mỗi lô 8 request.
Kết cục: Không báo người dùng đăng nhập lại, vi phạm `.agents/rules/04-scraping.md` ("Phiên hết hạn thì báo người dùng đăng nhập lại, không retry vô hạn"); song song hóa làm tăng tốc độ spam → LỖI (MAJOR-2).
```

```text
Timeline E (Trục Đồng thời — kết quả mạng về ngược thứ tự)
T1: async Ch 4 xong t=10ms, Ch 3 t=40ms, Ch 2 t=60ms, Ch 1 t=80ms; các nhánh chỉ giữ ChapterFetchOutcome trong RAM.
T2: awaitAll() trả list theo thứ tự batch (đã sort đầu vào), sau đó sortedBy chapterNumber lần nữa.
T3: Ghi + emit 1 → 2 → 3 → 4 trên 1 coroutine.
Kết cục: Thứ tự ghi DB/emit đúng → AN TOÀN. (Lưu ý: awaitAll đã trả theo thứ tự Deferred, nên bước sortedBy thứ hai là phòng thủ, không sai.)
```

#### Commands Executed (trích từ evidence.md)

- `### [CMD] cmd.exe /d /c gradlew.bat compileDebugKotlin` — `Exit: 0` — `BUILD SUCCESSFUL in 7s`.
- `### [CMD] cmd.exe /d /c gradlew.bat testDebugUnitTest` — `Exit: 0` — `BUILD SUCCESSFUL in 1m 21s` (baseline test hiện có đều xanh).
- `### [CMD] cmd.exe /d /c gradlew.bat ktlintCheck` — `Exit: 1` — `SourceAuthManager.kt:51:151 Exceeded max line length (150)`, `SettingsViewModel.kt:100:21 Backing property is only allowed when a matching property or function exists` (lỗi có sẵn, ngoài phạm vi plan).
- `### [CMD] git status --short` — `Exit: 0` — xác nhận `?? app/src/test/java/com/personal/apptruyen/data/repository/DownloadRepositoryTest.kt`.
- `### [CMD] node --test .agents/scripts/code-freeze-guard.test.js` — `Exit: 0` — `pass 31, fail 0`.
- UNVERIFIED (đề xuất User/implementer chạy sau khi triển khai): `.\gradlew.bat testDebugUnitTest --tests "com.personal.apptruyen.data.repository.DownloadRepositoryTest"`.

#### Phân loại lỗi

- **[MAJOR-1] Test Contract không chứng minh được tính song song, giới hạn đồng thời và backoff giữa lô.**
  - TC-01 chỉ dùng `coVerifyOrder` trên `saveChapterContent`. Vì đầu vào đã được sort, implementation tuần tự cũ cũng PASS test này → test không phân biệt được thay đổi cốt lõi của plan (mock vòng tròn theo S2).
  - R2 (rủi ro Trung bình) dựa vào "giới hạn cứng 4 luồng" nhưng không có test nào khẳng định số request đồng thời tối đa = 4 (evidence-bar 4.5.4).
  - TC-03 yêu cầu "thời gian chờ giữa các lô tăng gấp đôi" nhưng input chỉ có 1 lô (`1..4`) và không có `delay` sau lô cuối → không quan sát được backoff. Ngoài ra plan không nêu giá trị khởi tạo của `currentDelay`.
  - **Yêu cầu sửa**: (a) TC-01 phải khẳng định song song bằng thời gian ảo, ví dụ mock `coAnswers { delay(80/60/40/10); ... }` rồi assert `testScheduler.currentTime` sau lô 1 ≈ 80ms (không phải 190ms); (b) thêm assertion max-in-flight: mock tăng/giảm `AtomicInteger` quanh `delay`, với 10 chương assert `maxInFlight == 4` và không bao giờ > 4; (c) TC-03 dùng ≥ 2 lô (ví dụ 8 chương) và assert khoảng cách thời gian ảo giữa lô 1 và lô 2 là `2 * BASE_BATCH_DELAY_MS` khi lô 1 có lỗi, `BASE_BATCH_DELAY_MS` khi không lỗi; (d) ghi rõ `var currentDelay = BASE_BATCH_DELAY_MS`.

- **[MAJOR-2] S6 FAIL — Không có fail-fast cho lỗi không thể phục hồi; song song hóa khuếch đại request khi nguồn chặn/phiên hết hạn (Timeline C, D).**
  - `LoginRequiredException` (Wikidich) bị coi như lỗi tạm thời, retry và tiếp tục toàn bộ danh sách, vi phạm `.agents/rules/04-scraping.md` mục Đăng Nhập/Cookie.
  - HTTP 429/403/503 kéo dài: tối đa 24 request/lô, trần backoff 3s, không bao giờ dừng.
  - **Yêu cầu sửa**: (a) trong `fetchChapterWithRetry`, không retry `LoginRequiredException` (và `CircuitOpenException`); sau khi ghi xong các chương thành công của lô hiện tại, ném lại lỗi đó ra khỏi `flow` để `DownloadService` dừng (nêu rõ cách hiển thị thông báo tiếng Việt yêu cầu đăng nhập lại — tối thiểu qua đường `catch (e: Exception)` hiện có ở `DownloadService.kt:212-215`, hoặc đề xuất thay đổi tối thiểu nếu muốn giữ số chương đã tải); (b) thêm ngưỡng dừng khi lỗi liên tiếp, ví dụ `MAX_CONSECUTIVE_FAILED_BATCHES = 3` (cả lô đều lỗi) thì dừng và báo lỗi tiếng Việt, hoặc khi `batchErrors > 0` thì hạ song song xuống 1 cho lô kế tiếp; (c) bổ sung TC tương ứng (LoginRequired dừng ngay, không gọi lô kế; N lô lỗi liên tiếp thì dừng) và cập nhật bảng Rủi ro.

- **[MAJOR-3] Bước 4 sửa logic ViewModel nhưng không có unit test (vi phạm `.agents/rules/01-coding.md` mục Yêu Cầu Test), đồng thời thay đổi gần như dư thừa.**
  - Thứ tự `chapterNumbers` gửi qua Intent không ảnh hưởng kết quả vì `DownloadService.kt:177` → `ChapterDao.getChaptersByNumbers` (`ORDER BY chapterNumber ASC`) và `downloadChapters` (plan) đều sort lại. `state.chapters` sau khi `observeChapters` emit đã tăng dần (`ChapterDao.kt:10`, `StoryDetailViewModel.kt:173`).
  - **Yêu cầu sửa**: chọn một trong hai — (a) bỏ Bước 4 (minimal diff, khuyến nghị) và bỏ `StoryDetailViewModel.kt` khỏi bảng Files; hoặc (b) giữ và thêm test trong `app/src/test/java/com/personal/apptruyen/ui/detail/StoryDetailViewModelTest.kt` chứng minh `downloadChapters(count)` chọn đúng `count` chương nhỏ nhất khi `state.chapters` không theo thứ tự.

- **[MINOR-1] Khẳng định thư viện sai về `ConnectionPool`.** `ConnectionPool(5, 30, SECONDS)` giới hạn số kết nối **nhàn rỗi** được giữ lại, không giới hạn số request đồng thời; `Dispatcher.maxRequestsPerHost` chỉ áp dụng cho `enqueue()`, còn `BaseScraper.kt:125` dùng `execute()` đồng bộ. Lập luận "chừa 1 kết nối cho người dùng" ở mục 1 và R2 không đúng; giới hạn thực tế chỉ đến từ `DOWNLOAD_BATCH_SIZE`. Đề nghị sửa câu chữ (`[URL]` tham khảo: square.github.io/okhttp — ConnectionPool/Dispatcher).
- **[MINOR-2] Vị trí test.** Đã có `DownloadRepositoryTest.kt` cho đúng lớp `DownloadRepository` (quy ước `{Class}Test.kt` trong `01-coding.md`). Nên đặt 5 test mới vào file này thay vì `StoryRepositoryTest.kt`, và bổ sung file vào Pre-Work. Nhớ mock `android.util.Log` (như `StoryRepositoryTest.kt:61-66`) vì code vẫn gọi `Log.e`.
- **[MINOR-3] Sai số dòng tham chiếu**: `TangThuVienScraper.getChapterContentWithTitle` ở dòng 297-359; `WikidichScraper.getChapterContent` ở dòng 209-239 (dòng 170-195 là `getRemainingChapters`); Wikidich không override `getChapterContentWithTitle`.
- **[MINOR-4] R4 có thể bị đánh giá quá mức.** `BundledSQLiteDriver` đóng gói SQLite bản mới (giới hạn biến mặc định 32766 từ SQLite 3.32), nên lỗi "too many SQL variables" với ~1.000 tham số là UNVERIFIED. Chunk 900 vô hại, giữ được, nhưng nên hạ mức rủi ro hoặc ghi rõ đây là phòng thủ.
- **[MINOR-5] Verification Gate**: `ktlintCheck` hiện Exit 1 do lỗi có sẵn ngoài phạm vi. Plan nên ghi rõ implementer chỉ cần đảm bảo không phát sinh vi phạm mới trong file mình sửa, và không tự sửa `SourceAuthManager.kt`/`SettingsViewModel.kt` ngoài phạm vi.
- **[MINOR-6] Hủy giữa lô bỏ phí tối đa 3 chương đã tải xong trong RAM**, và progress phát theo cụm 4 lần liên tiếp sau mỗi lô (text "đang tải" chỉ hiện `batch.first().title`). Chấp nhận được; nên ghi vào mục Rủi ro/Docs để User biết khi test thủ công.
- **[MINOR-7] Chọn nguồn theo chương đầu**: sau khi sort, nếu chương nhỏ nhất có `url` rỗng (hàng được tạo bởi `saveChapterContent` với `url = ""`, `ChapterDao.kt:80-87`) thì `sourceFor("")` ném lỗi cho cả lượt tải. Cân nhắc `sortedChapters.firstOrNull { it.url.isNotBlank() }`.
- **[MINOR-8] Log**: quy tắc yêu cầu Timber; giữ `android.util.Log` để khớp test hiện có là chấp nhận được trong phạm vi minimal diff, không bắt buộc đổi.

#### Kết luận

Thiết kế cốt lõi (tách pha fetch song song khỏi pha ghi tuần tự, sort trước khi persist/emit, ném lại `CancellationException` kèm `ensureActive()`) đúng và an toàn về DB/lifecycle. Tuy nhiên còn 3 lỗi MAJOR: Test Contract chưa chứng minh được song song/giới hạn đồng thời/backoff (MAJOR-1), S6 thiếu fail-fast khi phiên hết hạn hoặc nguồn chặn liên tục (MAJOR-2), và Bước 4 sửa ViewModel không có test (MAJOR-3). Planner cần ra Revision 2 xử lý các điểm trên.

**Kết luận: [CHANGES_REQUESTED]**

### Audit Run 2

- **Reviewer**: Claude Code CLI (Claude Opus 5.5 — `claude-opus-5-5`)
- **Ngày**: 2026-10-14
- **Đối tượng**: Technical Plan Revision 2

#### Kiểm chứng lỗi cũ

| # | Lỗi từ Run 1 (kết luận CHANGES_REQUESTED) | Trạng thái | Bằng chứng trong Revision 2 |
| - | ----------------------------------------- | ---------- | --------------------------- |
| MAJOR-1 | Test Contract không chứng minh song song / giới hạn đồng thời / backoff giữa lô | ✅ Đã khắc phục | TC-01 assert `testScheduler.currentTime == 80L` (khác `190L` tuần tự); TC-02 assert `maxInFlight == 4` với 10 chương; TC-03 dùng 2 lô, assert `750L`; Bước 3 ghi rõ `var currentDelay = BASE_BATCH_DELAY_MS`. Tự mô phỏng TC-03: lô 1 kết thúc ở t=250 (Ch 2 và Ch 3 retry song song với `retryDelayMs = 250`), `batchErrors = 1` → `currentDelay = 500` → lô 2 bắt đầu t=750, không delay sau lô cuối → `750L` khớp. |
| MAJOR-2(a) | Không retry `LoginRequiredException`/`CircuitOpenException`, ném ra khỏi flow, **nêu rõ thông báo tiếng Việt yêu cầu đăng nhập lại** | ⚠️ Khắc phục một phần | Phần repository đúng (Bước 3, TC-05). Nhưng Assumption 4 chọn đường `catch (e: Exception)` ở `DownloadService.kt:212-215`, hiển thị `"Đã tải 0 chương (1 lỗi)"` (`DownloadService.kt:326`) — không yêu cầu đăng nhập lại và báo sai số chương đã lưu → xem MAJOR-A. |
| MAJOR-2(b) | Ngưỡng dừng khi lỗi liên tiếp | ✅ Đã khắc phục | `MAX_CONSECUTIVE_FAILED_BATCHES = 3`, `break` trước `delay` (Bước 3.6), TC-06. |
| MAJOR-2(c) | TC + bảng Rủi ro | ✅ Đã khắc phục | TC-05, TC-06, R2 cập nhật, Cơ chế 2–3 trong Adversarial Timeline. |
| MAJOR-3 | Bước sửa ViewModel không có test | ✅ Đã khắc phục | Đã bỏ bước ViewModel; `StoryDetailViewModel.kt` không còn trong bảng Files (Assumption 1 giải thích lý do). |
| MINOR-1 | Khẳng định sai về `ConnectionPool` | ✅ Đã khắc phục | Mục 1 nay ghi đúng: giới hạn đồng thời do `DOWNLOAD_BATCH_SIZE`, pool chỉ giữ kết nối nhàn rỗi. |
| MINOR-2 | Vị trí test | ✅ Đã khắc phục | Test mới đặt trong `DownloadRepositoryTest.kt`, có `mockkStatic(Log)` + `unmockkStatic`. |
| MINOR-3 | Sai số dòng | ✅ Đã khắc phục | `TangThuVienScraper.kt:297-359`, `WikidichScraper.kt:209-239`, `StorySource.kt:48` đúng với source. |
| MINOR-4 | R4 đánh giá quá mức | ✅ Đã khắc phục | R4 hạ xuống "Thấp", ghi rõ "phòng thủ". |
| MINOR-5 | Baseline ktlint | ✅ Đã khắc phục | Bước 5 + Verification Plan ghi rõ 2 lỗi có sẵn, không sửa ngoài phạm vi. |
| MINOR-6 | Hủy giữa lô bỏ phí ≤ 3 chương; progress theo cụm | ✅ Đã khắc phục | Assumption 3. |
| MINOR-7 | Chọn nguồn theo chương đầu có url rỗng | ✅ Đã khắc phục | `firstOrNull { it.url.isNotBlank() }` + bỏ qua chương url rỗng không retry. |
| MINOR-8 | `Log` thay vì Timber | Giữ nguyên (chấp nhận) | Minimal diff, khớp test hiện có. |

#### Khảo sát source thực tế (bổ sung cho Run 2)

- `[SRC]` `DownloadRepository.kt:85-144` không đổi so với Run 1; `catch (e: Exception)` dòng 123 vẫn nuốt `CancellationException` → Bước 3 sửa đúng chỗ.
- `[SRC]` `DownloadService.kt:184-215`: `lastProgress` khai báo **bên trong** `try` (dòng 184), nên nhánh `catch (e: Exception)` (dòng 212-215) không truy cập được và cứng `Completed(storyId, storyTitle, 0, 1)`; `sendCompletionNotification` (dòng 321-338) chỉ in `"Đã tải $downloaded chương ($errors lỗi)"`, bỏ qua `e.message`.
- `[SRC]` `.agents/rules/04-scraping.md:39`: "Phiên hết hạn thì báo người dùng đăng nhập lại (tiếng Việt), không retry vô hạn."
- `[SRC]` `ScraperException.kt:39-49`: `CircuitOpenException`, `LoginRequiredException` tồn tại; `LoginRequiredException` có message tiếng Việt mặc định.
- `[SRC]` `CircuitOpenException` chỉ được ném ở `ScraperCircuitBreaker.kt:76`; `DownloadRepository` và các scraper không dùng circuit breaker (`StoryRepository.kt:120-157,299-329` mới dùng) → nhánh `CircuitOpenException` trong `fetchChapterWithRetry` hiện là phòng thủ, không có đường thực thi (MINOR-B).
- `[SRC]` `StoryRepositoryTest.kt:196-231,489-502`: 3 test tích hợp hiện có vẫn thỏa với thiết kế mới (`first().current == 0` nhờ emit đầu lô; `last().current == 2`; url `unsupported.com` → `sourceFor` ném `ParseException`).
- `[SRC]` `DownloadRepositoryTest.kt:1-89`: chưa mock `android.util.Log` → Bước 4 bổ sung `mockkStatic` là cần thiết.

#### Mental Simulation Matrix (S1–S6 + Domain)

| Kịch bản | Kết quả | Bằng chứng | Chi tiết |
| -------- | ------- | ---------- | -------- |
| S1 Build | PASS | `[CMD]` `cmd.exe /d /c gradlew.bat compileDebugKotlin` Exit 0; `[SRC]` `DownloadRepository.kt:13-15` | Không đổi dependency. Cần thêm import `kotlinx.coroutines.async`, `awaitAll`, `coroutineScope`, `CancellationException`. Smart cast `outcome.error is A \|\| outcome.error is B` → `ScraperException?` hợp lệ với K2 (Kotlin 2.4.21). `ktlintCheck` baseline Exit 1 do lỗi ngoài phạm vi (đã ghi nhận trong plan). |
| S2 Lifecycle | PASS | `[SRC]` `DownloadService.kt:115,144-149,209-218`; `[ADV]` Timeline F | `async` là con của `coroutineScope` trong `flow`, không scope mới, không hardcode dispatcher → `runTest` điều khiển thời gian ảo. `ensureActive()` ở 3 vị trí vẫn bảo đảm hủy kể cả khi TTV bọc `CancellationException` thành `ParseException`. |
| S3 Database | PASS | `[SRC]` `ChapterDao.kt:72-90`; `[ADV]` Timeline F | Ghi tuần tự trên 1 coroutine sau `awaitAll()`, mỗi chương 1 `@Transaction`. Fail-fast ném sau khi đã ghi các chương thành công của lô → không mất dữ liệu đã tải. |
| S4 Cross-task state | PASS | `[SRC]` `IDownloadRepository`, `StoryRepositoryTest.kt:196-231,489-502` | Chữ ký giữ nguyên; test cũ vẫn thỏa. |
| S5 Security/Privacy | PASS | `[SRC]` `WikidichScraper.kt:297-305`; `[ADV]` Timeline G | Không log cookie; fail-fast chặn spam khi phiên hết hạn. TOCTOU phiên hết hạn giữa lô không làm lộ dữ liệu. |
| S6 External resilience | PASS (phần repository) | `[SRC]` `BaseScraper.kt:69-91`; `[ADV]` Timeline G, H | Không retry `LoginRequiredException`; dừng sau 3 lô lỗi liên tiếp; backoff trần 3s. Tối đa request vô ích khi nguồn chặn hoàn toàn: 3 lô × 4 chương × 2 attempt × 3 (retry nội bộ `fetchDocumentWithRetry`) = 72 request, thay vì ~7.200 ở Run 1. |
| Domain (thông báo cho người dùng khi phiên hết hạn) | **FAIL** | `[SRC]` `DownloadService.kt:184,212-215,326`; `.agents/rules/04-scraping.md:39`; `[ADV]` Timeline G | Người dùng chỉ thấy `"Đã tải 0 chương (1 lỗi)"`: không được yêu cầu đăng nhập lại, và số "0 chương" sai khi đã lưu hàng chục chương ở các lô trước → MAJOR-A. |

#### Adversarial Timelines

```text
Timeline F (Trục Vòng đời + Đồng thời — hủy trong lúc ghi DB của lô)
T1: Lô [Ch 9..12] awaitAll() xong; collector đã ghi Ch 9, Ch 10 (mỗi chương 1 @Transaction).
T2: Người dùng bấm Hủy → downloadJob.cancel().
T3: Trước khi ghi Ch 11, ensureActive() ném CancellationException; Ch 11, 12 (đã có trong RAM) bị bỏ, flow thoát; DownloadService.kt:209-211 ném lại, finally → stopSelfCleanly().
Kết cục: Ch 1..10 nguyên vẹn, không có ghi dở dang → AN TOÀN.
```

```text
Timeline G (Trục TOCTOU — phiên Wikidich hết hạn ở lô 11 khi đã lưu 40 chương)
T1: Lô 1..10 thành công, Room có 40 chương; lastProgress.current = 40.
T2: Lô 11: Ch 41 thành công, Ch 42 ném LoginRequiredException → fetchChapterWithRetry trả ngay, không retry; Ch 43, 44 cũng trả LoginRequired (đã chạy song song).
T3: Collector ghi Ch 41, emit current = 44, rồi throw fatalError → DownloadService.kt:212 catch (e: Exception) → Completed(storyId, title, 0, 1) + thông báo "Đã tải 0 chương (1 lỗi)".
Kết cục: Request dừng đúng (AN TOÀN về nguồn), nhưng người dùng thấy "0 chương" trong khi 41 chương đã lưu, và không có chỉ dẫn đăng nhập lại → vi phạm 04-scraping.md:39 → LỖI (MAJOR-A).
```

```text
Timeline H (Trục Thất bại giữa chừng — nguồn trả 429 liên tục cho 1.200 chương)
T1: Lô 1: 4 chương × 2 attempt (retry 250ms) đều lỗi → batchErrors = 4, consecutiveFailedBatches = 1, currentDelay = 500.
T2: Lô 2, 3 tương tự → consecutiveFailedBatches = 3 → log + break trước delay.
T3: Flow kết thúc bình thường; DownloadService.kt:203-208 → "Đã tải 12 chương (12 lỗi)".
Kết cục: Dừng sau 12 chương thay vì 1.200 → AN TOÀN cho nguồn. (Ghi chú: "Đã tải 12 chương" thực chất là 12 chương đã thử — ngữ nghĩa `downloaded` có sẵn từ trước, xem MINOR-C.)
```

```text
Timeline I (Trục Đồng thời — mạng về ngược thứ tự, TC-01)
T1: Ch 4 xong t=10, Ch 3 t=40, Ch 2 t=60, Ch 1 t=80; các nhánh async chỉ giữ ChapterFetchOutcome.
T2: awaitAll() trả tại t=80; sortedBy chapterNumber; ghi + emit 1 → 2 → 3 → 4 trên 1 coroutine.
T3: Lô cuối → không delay → testScheduler.currentTime == 80.
Kết cục: Thứ tự đúng, test phân biệt được với triển khai tuần tự (190) → AN TOÀN.
```

#### Commands Executed (trích từ evidence.md)

- `### [CMD] cmd.exe /d /c gradlew.bat compileDebugKotlin` — `Exit: 0` — `BUILD SUCCESSFUL in 6s`.
- `### [CMD] cmd.exe /d /c gradlew.bat testDebugUnitTest` — `Exit: 0` — `BUILD SUCCESSFUL in 1s` (`34 actionable tasks: 34 up-to-date`, baseline xanh).
- `### [CMD] cmd.exe /d /c gradlew.bat ktlintCheck` — `Exit: 1` — `SourceAuthManager.kt:51:151 Exceeded max line length (150)`, `SettingsViewModel.kt:100:21 Backing property is only allowed when a matching property or function exists` (lỗi có sẵn, ngoài phạm vi).
- `### [CMD] git status --short` — `Exit: 0` — `?? app/src/test/java/com/personal/apptruyen/data/repository/DownloadRepositoryTest.kt`.
- `### [CMD] node --test .agents/scripts/code-freeze-guard.test.js` — `Exit: 0` — `pass 31, fail 0`.
- UNVERIFIED (chạy sau khi triển khai): `.\gradlew.bat testDebugUnitTest --tests "com.personal.apptruyen.data.repository.DownloadRepositoryTest" --tests "com.personal.apptruyen.data.repository.StoryRepositoryTest"`.

#### Phân loại lỗi

- **[MAJOR-A] Phiên đăng nhập hết hạn không được báo cho người dùng đăng nhập lại; số chương đã tải bị báo sai thành 0 (còn lại từ MAJOR-2(a) của Run 1).**
  - Vi phạm `.agents/rules/04-scraping.md:39` ("Phiên hết hạn thì báo người dùng đăng nhập lại (tiếng Việt)"). Run 1 yêu cầu "nêu rõ cách hiển thị thông báo tiếng Việt yêu cầu đăng nhập lại"; đường `catch (e: Exception)` hiện có chỉ cho `"Đã tải 0 chương (1 lỗi)"`, không phải thông báo đăng nhập lại.
  - Sai dữ liệu hiển thị: `lastProgress` nằm trong `try` (`DownloadService.kt:184`) nên nhánh catch cứng `downloaded = 0` dù Room đã lưu các chương của lô trước (Timeline G). Assumption 4 hiện mô tả hành vi này như thể đạt yêu cầu.
  - **Yêu cầu sửa (thay đổi tối thiểu)**: thêm `DownloadService.kt` vào bảng Files và thêm 1 bước: (1) đưa `var lastProgress` ra ngoài `try`; (2) thêm `catch (e: ScraperException.LoginRequiredException)` trước `catch (e: Exception)`, đặt `Completed(storyId, storyTitle, lastProgress?.current ?: 0, ...)` và thông báo tiếng Việt kiểu `"Đã tải X chương. Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại để tải tiếp."`; (3) để có unit test theo `01-coding.md`, tách việc dựng nội dung thông báo thành hàm thuần (ví dụ `buildCompletionText(downloaded, errors, loginRequired: Boolean)` trong `download/`) và thêm test cho nhánh đăng nhập lại; bổ sung một dòng TC tương ứng và cập nhật Assumption 4, Cơ chế 2 trong Adversarial Timeline, Verification Plan thủ công (bước kiểm tra với cookie Wikidich hết hạn). Nếu Planner chọn cách khác (ví dụ không ném mà emit trạng thái), phải nêu rõ và có test tương đương.

- **[MINOR-A] TC-04 chưa mô phỏng đúng kịch bản hủy thật.** Test chỉ cho mock ném `CancellationException`; nên thêm (hoặc mở rộng) một case `job.cancel()` trong lúc nhánh async đang `delay` và mock bọc lỗi thành `ParseException` (giống `TangThuVienScraper.kt:355`) để chứng minh `ensureActive()` trước khi ghi DB chặn được ghi rác. Cũng nên ghi rõ cách assert (bắt `CancellationException` bằng `try/catch` trong `runTest` hoặc `assertThrows` + `runBlocking` như test hiện có), tránh nhầm với việc hủy chính test scope.
- **[MINOR-B] Nhánh `CircuitOpenException` là code phòng thủ không có đường thực thi** trong luồng tải (`DownloadRepository` không dùng `ScraperCircuitBreaker`). Giữ được, nhưng nên ghi chú là phòng thủ trong mục 1/R2 để không tạo kỳ vọng sai.
- **[MINOR-C] Ngữ nghĩa `downloaded`** đếm số chương đã thử (kể cả lỗi), nên thông báo "Đã tải 12 chương (12 lỗi)" ở Timeline H dễ gây hiểu nhầm. Hành vi có sẵn từ trước; không bắt buộc sửa trong plan này.
- **[MINOR-D] TC-05 chưa nêu hành vi của Ch 3, Ch 4 trong lô 1** (vẫn được gọi song song). Nên ghi rõ mock cho Ch 3, 4 (thành công hay lỗi) để assert `saveChapterContent` không bị mơ hồ.

#### Kết luận

Revision 2 đã khắc phục đầy đủ MAJOR-1, MAJOR-3, MAJOR-2(b)(c) và toàn bộ MINOR của Run 1; thiết kế repository (fetch song song → sort → ghi tuần tự, fail-fast, ngưỡng lô lỗi liên tiếp, backoff giữa lô, `ensureActive()`) đúng và Test Contract nay chứng minh được song song, giới hạn đồng thời và backoff bằng thời gian ảo. Còn 1 lỗi MAJOR-A: khi phiên hết hạn, người dùng không được báo đăng nhập lại và số chương đã tải bị báo sai thành 0, vi phạm `04-scraping.md:39`. Planner cần ra Revision 3 xử lý MAJOR-A (thay đổi nhỏ ở `DownloadService` + hàm dựng thông báo có test).

**Kết luận: [CHANGES_REQUESTED]**

### Audit Run 3

- **Reviewer**: Claude Code CLI (Claude Opus 5.5 — `claude-opus-5-5`)
- **Ngày**: 2026-10-14
- **Đối tượng**: Technical Plan Revision 3

#### Kiểm chứng lỗi cũ

| # | Lỗi từ Run 2 (kết luận CHANGES_REQUESTED) | Trạng thái | Bằng chứng trong Revision 3 |
| - | ----------------------------------------- | ---------- | --------------------------- |
| MAJOR-A | Phiên hết hạn không báo đăng nhập lại; số chương bị báo sai thành 0 | ✅ Đã khắc phục | `DownloadService.kt` đã có trong bảng Files; Bước 4 đưa `var lastProgress` ra ngoài `try`, thêm `catch (e: ScraperException.LoginRequiredException)` trước `catch (e: Exception)`, dùng `lastProgress?.current` ở cả 2 nhánh catch; tách hàm thuần `buildCompletionText(downloaded, errors, loginRequired)` với thông báo tiếng Việt `"Đã tải $downloaded chương. Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại để tải tiếp."`; thêm TC-09 + `DownloadServiceTest.kt` (3 nhánh); cập nhật Assumption 4, Cơ chế 2, Verification Plan thủ công bước 5. |
| MINOR-A | TC-04 chưa mô phỏng hủy thật khi scraper bọc lỗi | ✅ Đã khắc phục | TC-04b: `job.cancel()` tại t=50ms khi mock đang `delay(100)` và bọc thành `ParseException`, assert `saveChapterContent` 0 lần; TC-04a ghi rõ cách bắt bằng `try/catch` trong `runTest`. |
| MINOR-B | Nhánh `CircuitOpenException` là phòng thủ | ✅ Đã khắc phục | Mục 1, Assumption 4, R2 ghi rõ "phòng thủ". |
| MINOR-C | Ngữ nghĩa `downloaded` đếm cả chương lỗi | Giữ nguyên (chấp nhận) | Assumption 4 ghi rõ giữ ngữ nghĩa có sẵn. Xem thêm MINOR-3 dưới đây. |
| MINOR-D | TC-05 chưa nêu hành vi Ch 3, Ch 4 | ✅ Đã khắc phục | TC-05: Ch 1 thành công, Ch 2/3/4 đều ném `LoginRequiredException`; assert mỗi chương gọi mạng đúng 1 lần, `saveChapterContent` tổng 1 lần. |

#### Khảo sát source thực tế (bổ sung cho Run 3)

- `[SRC]` `DownloadService.kt:184` `var lastProgress` vẫn khai báo trong `try`; `:212-215` catch cứng `Completed(storyId, storyTitle, 0, 1)`; `:321-338` `sendCompletionNotification` private, 3 tham số → Bước 4 sửa đúng vị trí, thay đổi tối thiểu, không ảnh hưởng caller khác (grep `sendCompletionNotification` chỉ có 2 lời gọi nội bộ dòng 208, 215).
- `[SRC]` `DownloadService.kt:19` đã `import kotlinx.coroutines.*` → `CancellationException` trong catch hiện có vẫn hợp lệ; chỉ cần thêm import `ScraperException` như Bước 4 nêu.
- `[SRC]` `ScraperException.kt:26-30`: `ParseException(message, url, cause)` → lời gọi `ParseException("URL chương rỗng", "")` ở Bước 3 đúng thứ tự tham số; `:45-49` `LoginRequiredException(sourceId, message, cause)` → `LoginRequiredException("wikidich")` và mẫu TC-05 hợp lệ.
- `[SRC]` `ChapterContentResult.kt:7-10`: `data class` với `val content: String`, `val title: String?` → `outcome.result.content`/`outcome.result.title` smart cast được (property `val` của data class cùng module).
- `[SRC]` `StoryDetailViewModel.kt:309-318`: nhánh `Completed`/`Cancelled` chỉ reset `isDownloading`, không hiển thị text → kênh duy nhất báo người dùng là Notification hoàn tất; plan đặt thông báo đăng nhập lại đúng kênh đó.
- `[SRC]` `StoryRepositoryTest.kt:196-231,489-502`: 3 test tích hợp vẫn thỏa thiết kế mới (2 chương = 1 lô; `first().current == 0` nhờ emit đầu lô; chương lỗi `Exception("Error")` retry rồi vẫn `last().current == 2`; url `unsupported.com` → `sourceFor` ném `ParseException`).
- `[SRC]` `app/build.gradle.kts:189` `useJUnitPlatform()`; không thấy `unitTests.isReturnDefaultValues` → xem MINOR-1.

#### Mental Simulation Matrix (S1–S6 + Domain)

| Kịch bản | Kết quả | Bằng chứng | Chi tiết |
| -------- | ------- | ---------- | -------- |
| S1 Build | PASS | `[CMD]` `cmd.exe /d /c gradlew.bat compileDebugKotlin` Exit 0; `[SRC]` `DownloadService.kt:19`, `ScraperException.kt:26-49` | Không đổi dependency. Chữ ký constructor exception khớp. Smart cast `outcome.error is A \|\| outcome.error is B` sang `ScraperException?` hợp lệ với K2; `throw fatalError` trên biến `var` cục bộ không bị lambda ghi → smart cast được. Baseline `ktlintCheck` Exit 1 do 2 lỗi ngoài phạm vi (plan đã ghi nhận). |
| S2 Lifecycle | PASS | `[SRC]` `DownloadService.kt:115,144-149,209-218`; `[ADV]` Timeline J | `async` là con của `coroutineScope` trong `flow`; không scope mới, không hardcode dispatcher. Nhánh `catch LoginRequiredException` mới nằm sau `catch CancellationException` → hủy vẫn được ném lại; `finally { stopSelfCleanly() }` giữ nguyên → nhả WakeLock/foreground. |
| S3 Database | PASS | `[SRC]` `ChapterDao.kt:72-90`; `[ADV]` Timeline J, K | Ghi tuần tự trên 1 coroutine sau `awaitAll()`, mỗi chương 1 `@Transaction`; fail-fast ném sau khi đã ghi các chương thành công của lô → không mất dữ liệu. |
| S4 Cross-task state | PASS | `[SRC]` `IDownloadRepository.kt:20`, `StoryDetailViewModel.kt:309-318`, `StoryRepositoryTest.kt:196-231,489-502` | Chữ ký interface, `DownloadProgress`, `DownloadState` giữ nguyên; ViewModel không cần đổi; test cũ vẫn thỏa. |
| S5 Security/Privacy | PASS | `[SRC]` `WikidichScraper.kt:297-305`; `[ADV]` Timeline K | Không log cookie; `Timber.w(e, ...)` chỉ in exception có message tiếng Việt cố định. TOCTOU phiên hết hạn giữa lô dừng request ngay sau lô hiện tại. |
| S6 External resilience | PASS | `[SRC]` `BaseScraper.kt:69-91`; `[ADV]` Timeline H (Run 2), K | Không retry `LoginRequiredException`; dừng sau 3 lô lỗi liên tiếp; backoff trần 3s; tối đa 72 request vô ích khi nguồn chặn hoàn toàn. |
| Domain (thông báo khi phiên hết hạn, tiến độ, hủy) | PASS (kèm MINOR-3) | `[SRC]` `DownloadService.kt:184-215,321-338`; `.agents/rules/04-scraping.md:39`; `[ADV]` Timeline K | Người dùng nhận thông báo tiếng Việt yêu cầu đăng nhập lại kèm số chương đã xử lý, đáp ứng `04-scraping.md:39`. |

#### Adversarial Timelines

```text
Timeline J (Trục Vòng đời — người dùng bấm Hủy đúng lúc lô cuối ném LoginRequiredException)
T1: Lô 11 awaitAll() xong; collector ghi Ch 41, emit current = 44, chuẩn bị throw fatalError.
T2: Cùng lúc người dùng bấm Hủy → cancelCurrentDownload() đọc state Downloading(current = 44), gửi "Đã hủy tải (44 chương đã tải)", downloadJob.cancel().
T3a: Nếu cancel đến trước khi ghi Ch 41: ensureActive() ném CancellationException → catch CancellationException ném lại → finally stopSelfCleanly(); nhánh LoginRequired không chạy.
T3b: Nếu throw fatalError xảy ra trước khi coroutine quan sát cancel: nhánh catch LoginRequired chạy, ghi Completed + gửi thông báo đăng nhập lại cùng notification id storyTitle.hashCode() → ghi đè thông báo hủy.
Kết cục: Cả hai nhánh đều không mất dữ liệu, WakeLock được nhả trong finally; chỉ khác nội dung thông báo cuối (hành vi race có sẵn giữa cancel và completion, không do plan tạo ra) → AN TOÀN.
```

```text
Timeline K (Trục TOCTOU — phiên Wikidich hết hạn ở lô 11, đã lưu 40 chương)
T1: Lô 1..10 thành công; lastProgress (khai báo ngoài try) = (current 40, errors 0).
T2: Lô 11: Ch 41 OK; Ch 42..44 ném LoginRequiredException → fetchChapterWithRetry trả ngay, không retry; collector ghi Ch 41, emit current 41..44 (errors 3), throw fatalError.
T3: DownloadService catch LoginRequiredException → downloaded = 44, errors = 3 → Completed(…, 44, 3) + buildCompletionText(44, 3, loginRequired = true) → "Đã tải 44 chương. Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại để tải tiếp."; finally stopSelfCleanly(); lô 12+ không được gọi.
Kết cục: Không spam nguồn, có chỉ dẫn đăng nhập lại bằng tiếng Việt → AN TOÀN. (Số 44 gồm cả 3 chương lỗi — ngữ nghĩa có sẵn, xem MINOR-3.)
```

```text
Timeline L (Trục Đồng thời + Hủy — TC-04b kiểm chứng thực tế)
T1: t=0 async Ch 1 vào mock: delay(100) trong try/catch(Exception) bọc thành ParseException.
T2: t=50 job.cancel() → delay ném CancellationException → mock bọc thành ParseException → fetchChapterWithRetry rơi vào catch (e: Exception), attempt 1 < 2 → delay(retryDelayMs).
T3: delay trên coroutine đã hủy ném CancellationException ngay → thoát fetchChapterWithRetry, coroutineScope hủy, flow thoát; không tới pha ghi DB.
Kết cục: saveChapterContent 0 lần → TC-04b PASS. Lưu ý: điểm chặn thực tế ở attempt 1 là delay(retryDelayMs), không phải ensureActive() (ensureActive trước ghi DB chỉ là điểm chặn khi lỗi bọc rơi đúng attempt cuối) — xem MINOR-2.
```

#### Commands Executed (trích từ evidence.md)

- `### [CMD] cmd.exe /d /c gradlew.bat compileDebugKotlin` — `Exit: 0` — `BUILD SUCCESSFUL in 5s`.
- `### [CMD] cmd.exe /d /c gradlew.bat testDebugUnitTest` — `Exit: 0` — `BUILD SUCCESSFUL in 1s` (`34 actionable tasks: 34 up-to-date`, baseline xanh).
- `### [CMD] cmd.exe /d /c gradlew.bat ktlintCheck` — `Exit: 1` — `SourceAuthManager.kt:51:151 Exceeded max line length (150)`, `SettingsViewModel.kt:100:21 Backing property is only allowed when a matching property or function exists` (lỗi có sẵn, ngoài phạm vi, plan đã ghi nhận).
- `### [CMD] git status --short` — `Exit: 0` — `?? app/src/test/java/com/personal/apptruyen/data/repository/DownloadRepositoryTest.kt`; chưa có `app/src/test/java/com/personal/apptruyen/download/` (khớp nhãn `[NEW]` cho `DownloadServiceTest.kt`).
- `### [CMD] node --test .agents/scripts/code-freeze-guard.test.js` — `Exit: 0` — `pass 31, fail 0`.
- `### [CMD] node --test .agents/scripts/plan-approval.test.js` — `Exit: 0` — `pass 12, fail 0`.
- UNVERIFIED (implementer chạy sau khi triển khai): `.\gradlew.bat testDebugUnitTest --tests "com.personal.apptruyen.data.repository.DownloadRepositoryTest" --tests "com.personal.apptruyen.download.DownloadServiceTest" --tests "com.personal.apptruyen.data.repository.StoryRepositoryTest"`.

#### Phân loại lỗi

Không còn lỗi mức BLOCKER hoặc MAJOR.

- **[MINOR-1] `buildCompletionText` đặt trong `companion object` của `DownloadService`.** Gọi `DownloadService.buildCompletionText(...)` từ JVM unit test sẽ nạp lớp `DownloadService` (kế thừa `Hilt_DownloadService` → stub `android.app.Service`). Chỉ nạp lớp, không khởi tạo, nên thường không ném `Stub!`, nhưng chưa kiểm chứng được (UNVERIFIED, không có `unitTests.isReturnDefaultValues`). Nếu TC-09 lỗi khi nạp lớp, chuyển hàm thành top-level `internal fun` trong file mới `download/DownloadNotificationText.kt` (cùng package, không đổi hành vi), cập nhật bảng Files tương ứng.
- **[MINOR-2] Câu chữ TC-04b/Cơ chế 4.** Ở attempt 1, hủy được chặn bởi `delay(retryDelayMs)` chứ không phải `ensureActive()` (Timeline L). Assertion `saveChapterContent` 0 lần vẫn đúng; khi triển khai nên sửa comment test cho chính xác, không cần đổi plan.
- **[MINOR-3] Thông báo đăng nhập lại dùng `downloaded` gồm cả chương lỗi** ("Đã tải 44 chương" trong khi chỉ lưu 41 — Timeline K). Kế thừa ngữ nghĩa có sẵn (MINOR-C của Run 2) và nhất quán với nhánh thường. Có thể cân nhắc ở plan sau: hiển thị `downloaded - errors` cho số chương lưu thành công.
- **[MINOR-4] Race giữa Hủy và hoàn tất** (Timeline J) có thể làm thông báo đăng nhập lại ghi đè thông báo hủy (cùng notification id). Hành vi có sẵn, không mất dữ liệu; không bắt buộc sửa.
- **[MINOR-5] Thông báo phụ thuộc quyền `POST_NOTIFICATIONS`** (Android 13+): ViewModel không hiển thị text khi `Completed`, nên nếu người dùng tắt thông báo thì không thấy yêu cầu đăng nhập lại. Hạn chế có sẵn của kiến trúc tải; ghi chú để User biết khi test thủ công bước 5.

#### Kết luận

Revision 3 khắc phục đầy đủ MAJOR-A của Run 2 bằng thay đổi tối thiểu ở `DownloadService` (`lastProgress` ngoài `try`, nhánh catch `LoginRequiredException`, hàm thuần `buildCompletionText` có TC-09), và xử lý toàn bộ MINOR-A/B/D. Thiết kế repository (fetch song song bị chặn 4 → sort → ghi tuần tự, fail-fast, ngưỡng 3 lô lỗi liên tiếp, backoff giữa lô, ném lại `CancellationException` + `ensureActive()`) đúng với source thực tế; Test Contract TC-01..TC-09 chứng minh được song song, giới hạn đồng thời, thứ tự, backoff, hủy và fail-fast bằng thời gian ảo. S1–S6 và Domain đều PASS; chỉ còn các điểm MINOR không chặn triển khai.

**Kết luận: APPROVED**

[PLAN_APPROVED]
