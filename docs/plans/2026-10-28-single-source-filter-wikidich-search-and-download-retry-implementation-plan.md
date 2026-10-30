# Sửa Lỗi Tìm Kiếm & 19 Thể Loại Wikidich, Bộ Lọc Đơn Nguồn Đồng Bộ 2 Chiều & Chống Nhảy Cóc Khi Tải Chương — Kế Hoạch Triển Khai

> **Trạng thái**: Completed
> **Revision**: 6
> **Ngày tạo**: 2026-10-28
> **Ngày cập nhật**: 2026-10-30
> **Planner**: @planner ([PLANNER_MODEL: gemini-4-argon])

---

## Technical Plan

### 1. Mục tiêu & Phạm vi

Giải quyết dứt điểm 3 nhóm vấn đề thực tế được người dùng phản ánh trong quá trình sử dụng hàng ngày, đồng thời khắc phục triệt để toàn bộ các phát hiện từ `Audit Run 1`, `Audit Run 2` và `Audit Run 3` (R1–R9, F2, F6, F8, N1, N3):

1. **Yêu cầu 1 — Sửa tìm kiếm theo tên/từ khóa, lọc thể loại và chuẩn hóa 19 thể loại chính thống trên Wikidich (`wikicv.org`)**:
   - **Tìm kiếm theo tên/từ khóa (`WikidichScraper.search`, `StorySource.search`, `IStoryRepository.searchStories`, `StoryRepository.searchStories`, `SearchViewModel`, `SearchScreen`)**:
     - Hiện tại `WikidichScraper.search(keyword, completedOnly)` (`WikidichScraper.kt:128-146`) chỉ gọi `$baseUrl/tim-kiem?q=$encoded&qs=1` (trang 1, thiếu các tham số lọc server-side `status=5794f03dd7ced228f4419191`, `gender=5794f03dd7ced228f4419196`, `genre=<objectId>`, và phân trang `start`).
     - Đồng thời, trong `SearchViewModel.applyLocalFilters()` (`SearchViewModel.kt:341-372`), biểu thức `matchesGenre` (`lines 348-354`) có nhánh `story.sourceId == "wikidich"` bỏ qua lọc thể loại ở client trong khi `WikidichScraper.search` lại không gửi `genre=` lên server — dẫn đến khi tìm từ khóa kèm thể loại trên Wikidich, bộ lọc thể loại bị phớt lờ hoàn toàn! Hơn nữa, `SearchViewModel.search()` (`SearchViewModel.kt:138-177`) luôn khóa cứng `canLoadMore = false` (`line 162`), còn `loadMoreFilterResults()` (`SearchViewModel.kt:441-485`) và `SearchScreen.kt:84` chỉ cho phép phân trang khi `state.isFilterActive == true` (tức `query.isBlank()`), khiến tìm kiếm có từ khóa trên Wikidich không thể tải trang 2+.
     - **Giải pháp**:
       - Truyền đầy đủ tham số lọc (`completedOnly`, `page`, `maleOnly`, `genreSlug`) xuống `StoryRepository.searchStories` và `StorySource.search` (overload 5 tham số có default implementation `if (page <= 1) search(keyword, completedOnly) else emptyList()`).
       - Trong `WikidichScraper.search(keyword, completedOnly, page, maleOnly, genreSlug)`: xây dựng URL server-side chuẩn đặt `q=$encoded&qs=1` ở đầu query string (`$baseUrl/tim-kiem?q=$encoded&qs=1$genreParam$genderParam$statusParam$startParam`); gọi `checkLoginRequired(doc)` (`WikidichScraper.kt:564-573`) ngay sau `fetchDocumentWithRetry(url)`; lọc hậu kiểm bằng `filterStories(stories, completedOnly, maleOnly)` (với tập đầy đủ 7 nhãn phi nam tần `NON_MALE_WIKIDICH_GENDERS`, bỏ `.ifEmpty { stories }` ở `line 141`); và gọi đúng hàm hiện có `enrichWithChapterCounts(filtered)` (`WikidichScraper.kt:503-562`).
       - Trong `SearchViewModel.search()` (`SearchViewModel.kt:138-177`): phân nhánh theo `currentState.activeSource == SourceIds.WIKIDICH`:
         - Khi `activeSource == SourceIds.WIKIDICH`: lưu `lastSubmittedQuery = query`, gọi `repository.searchStories(keyword = query, completedOnly = currentState.completedOnly, page = 1, maleOnly = currentState.maleOnly, genreSlug = currentState.selectedGenre?.slug)`, đặt `canLoadMore = rawResults.isNotEmpty() && _state.value.chapterFilter == ChapterCountFilter.NONE`, và hỗ trợ tải trang 2+ theo `lastSubmittedQuery` trong `SearchViewModel.loadMoreFilterResults()` (`SearchViewModel.kt:441-485`) cùng `SearchScreen.kt:84` (`(state.isFilterActive || state.hasSearched)`).
         - Khi `activeSource != SourceIds.WIKIDICH` (`tangthuvien` hoặc `sstruyen`): giữ nguyên lời gọi `repository.searchStories(query, completedOnly = false)` và `canLoadMore = false`, rồi lọc client-side qua `applyLocalFilters(rawResults)` (do khảo sát thực tế ngày `2026-10-30` cho thấy `https://sstruyen.pics/tim-kiem?keyword=tien&page=2` phớt lờ `page=2` và trang tìm kiếm từ khóa của TTV/SsTruyện không trả đủ metadata thể loại/trạng thái; giữ nguyên `TangThuVienScraper.kt` và `SsTruyenScraper.kt` không chỉnh sửa). Hiển thị ghi chú tiếng Việt rõ ràng trong `FilterBottomSheet` khi người dùng nhập từ khóa trên nguồn không hỗ trợ lọc `keyword + genre` server-side.
   - **Chuẩn hóa đúng 19 `Loại hình` của `wikicv.org` (`WikidichScraper.getGenreList`, `GENRE_QUERY_MAP`)**:
     - Giữ nguyên tham số `qs=2` kèm `&so=4&vo=1` trong `getStoriesByGenre()` (`WikidichScraper.kt:347-368`) và `getCompletedStories()` nhánh `/tim-kiem` (`WikidichScraper.kt:318-345`), vì trên `wikicv.org`, `qs=1` là tham số của form tìm kiếm nhanh có từ khóa (`search_logged_in.html:267-270`) và link trực tiếp từ trang chi tiết truyện (`story_detail.html:317-343`), còn duyệt danh sách lọc nâng cao có sắp xếp `&so=4&vo=1` dùng `qs=2` (`search_logged_in.html:276-411`).
     - Thay thế danh sách **35 mục** hiện tại trong `WikidichScraper.getGenreList()` (`WikidichScraper.kt:372-406`) và **42 khóa** pha trộn (`genre`, `tag`, `bg`, `age`, `official`, `ending`) trong `GENRE_QUERY_MAP` (`WikidichScraper.kt:83-124`) bằng **đúng 19 thể loại thuộc nhóm `Loại hình` (`genre=<24-hex ObjectId>`)** hiện hành trên `wikicv.org`, đối chiếu trực tiếp với HTML công khai (`HTTP 200`) trên `https://wikicv.org/truyen/<slug>` và các fixture thật có sẵn `app/src/test/resources/fixtures/wikidich/story_detail.html:316-338`, `search_logged_in.html:312-378`.
2. **Yêu cầu 2 — Quy tắc Đơn Nguồn Hoạt Động (Single Active Source) & Bộ chọn nguồn đồng bộ 2 chiều (`SettingsDataStore`, `StoryRepository`, `SettingsViewModel`, `SettingsScreen`, `SearchViewModel`, `SearchScreen`, `ExploreViewModel`, `ExploreScreen`)**:
   - **Chỉ 1 nguồn hoạt động tại một thời điểm**: Chuẩn hóa `SettingsKeys.ENABLED_SOURCES` qua `SourceIds.resolveSingleSource(rawCsv)` sao cho mọi trạng thái lưu trữ (`null`, rỗng, nhiều nguồn `"tangthuvien,sstruyen,wikidich"`, hoặc nguồn cũ đã gỡ) luôn phân giải về **đúng 1 `sourceId` hợp lệ duy nhất** thuộc `SourceIds.ALL_ACTIVE_SOURCES` (`"tangthuvien"`, `"sstruyen"`, `"wikidich"`).
   - **Tách biệt bộ lọc theo nguồn & Đồng bộ 2 chiều tức thời**:
     - Trên `SettingsScreen` (`SettingsScreen.kt:384-458`, vòng lặp nguồn `lines 394-409`): Khu vực "Quản lý nguồn" hiển thị 3 nguồn dạng đơn chọn (`RadioButton` chuẩn Material 3 — chọn 1 nguồn sẽ kích hoạt nguồn đó và tự động tắt 2 nguồn còn lại), dùng nhãn chuẩn `SourceIds.SOURCE_LABELS` (`SettingsDataStore.kt:29-33`: `"tangthuvien" to "TàngThưViện"`, `"sstruyen" to "SsTruyện"`, `"wikidich" to "Wikidich"`).
     - Trên `SearchScreen` (`FilterBottomSheet` và chip nguồn trên thanh bộ lọc) và `ExploreScreen`: Bổ sung bộ chọn **"Nguồn truyện"** dùng `SourceIds.SOURCE_LABELS` (`"TàngThưViện"`, `"SsTruyện"`, `"Wikidich"`).
     - Khi đổi nguồn ở bất kỳ màn hình nào (`SettingsScreen`, `SearchScreen`, hoặc `ExploreScreen`):
       1. Ghi ngay nguồn mới vào `SettingsDataStore` (`SettingsKeys.ENABLED_SOURCES = sourceId`), đồng bộ tức thời qua `Flow` (`collectLatest`) tới cả 3 màn hình.
       2. Tự động tải lại danh sách `genres` của riêng nguồn vừa chọn.
       3. Nếu `selectedGenre` hiện tại không tồn tại trong danh sách thể loại của nguồn mới (`newGenres.none { it.slug == selectedGenre.slug }`), tự động reset `selectedGenre = null` ("Tất cả"), tính lại `isFilterActive = hasAnyNonDefaultFilter(...)` và chạy lại truy vấn tìm kiếm/khám phá trên nguồn mới (hoặc gọi `clearFilter()` giữ nguyên `activeSource` đang chọn).
       4. Tùy chọn đặc thù của Wikidich (`"Truyện nam"` / `maleOnly`) trên cả `SearchScreen` và `ExploreScreen` (`ExploreScreen.kt:121-152`) chỉ hiển thị khi `state.activeSource == SourceIds.WIKIDICH`.
     - Việc đọc truyện, xem chi tiết truyện và tải chương của truyện đã lưu trong Tủ truyện (`StoryRepository.getStoryDetail`, `getChapterContent`, `DownloadRepository`) vẫn định tuyến theo URL của từng truyện (`sourceFor(url)`), không bị ảnh hưởng khi người dùng chuyển nguồn tìm kiếm/khám phá.
3. **Yêu cầu 3 — Chống nhảy cóc / bỏ sót chương khi tải truyện gặp rớt mạng tạm thời (`DownloadRepository` & `StoryDetailViewModel`)**:
   - Trong `DownloadRepository` (`DownloadRepository.kt:50, 127-285`):
     - Tăng độ bền khi retry chương lỗi trong `fetchChapterWithRetry` (`lines 127-161`) bằng exponential backoff (`val backoffDelay = retryDelayMs * (1L shl (attempt - 1))`) với `MAX_RETRY_ATTEMPTS = 3` (`line 50`, tức 1 lần thử đầu + tối đa 2 lần retry tại chỗ).
     - Tách nguyên văn khối lưu DB trong vòng lặp chính (`DownloadRepository.kt:206-228`) thành hàm private dùng chung `persistDownloadedChapter(storyId, chapter, result)`. Thu thập danh sách các chương thất bại (`failedChapters`) trong vòng lặp batch chính (`lines 230-244`). Sau khi kết thúc vòng lặp chính (nếu coroutine chưa bị hủy và `consecutiveFailedBatches < MAX_CONSECUTIVE_FAILED_BATCHES`), nếu `failedChapters.isNotEmpty()`, tự động thực thi **1 lượt tải bù (`retryPass`)** theo thứ tự `chapterNumber` tăng dần: tuân thủ rate-limit (`wikidichDelayProvider().coerceIn(WIKIDICH_MIN_DELAY_MS, WIKIDICH_MAX_DELAY_MS)` cho Wikidich hoặc `currentDelay` cho nguồn khác), kiểm tra lỗi nghiêm trọng (`LoginRequiredException`, `CircuitOpenException`, hoặc `isWikidich && isRateLimitHttpError(err)`), lưu chương tải bù thành công qua `persistDownloadedChapter(storyId, chapter, result)`, tăng `downloaded++`, giảm `errors = (errors - 1).coerceAtLeast(0)` và phát `DownloadProgress(downloaded, sortedChapters.size, chapter.title, errors)` (đúng constructor 4 tham số tại `StoryRepository.kt:826-834`).
   - Trong `StoryDetailViewModel` (`StoryDetailViewModel.kt:263-317`):
     - Đảm bảo `downloadChapters(count)` (`line 263`) và `downloadAll()` (`line 272`) đều lọc `!it.isDownloaded` **và** sắp xếp `.sortedBy { it.chapterNumber }` trước khi gọi `performDownload(undownloaded)` (`line 295`), đồng bộ với `downloadRange(fromIndex, toIndex)` (`lines 278-288` vốn đã có `.sortedBy { it.chapterNumber }` ở `line 285`).

- **Ngoài phạm vi (Out of Scope)**:
  - Không thay đổi Room database schema (`AppDatabase.kt:22` giữ nguyên `version = 9`).
  - Không thay đổi giao thức đăng nhập WebView hay `CookieVault`.

#### Verified Versions Table

| Thư viện / Công cụ | Phiên bản thực tế trong `build.gradle.kts` / `app/build.gradle.kts` | Ghi chú xác minh (2026) |
| :--- | :--- | :--- |
| Kotlin & Compose Plugin | `2.4.21` (`build.gradle.kts:4-5, 8`) | Tương thích chuẩn Coroutines & Compose Compiler |
| Android Gradle Plugin (AGP) | `9.4.1` (`build.gradle.kts:3`) | Cấu hình chuẩn AGP 9 |
| Jetpack Compose BOM | `2026.05.01` (`app/build.gradle.kts:106`) | Material 3 `FilterChip`, `RadioButton`, `ModalBottomSheet` ổn định |
| DataStore Preferences | `1.2.1` (`app/build.gradle.kts:147`) | `DataStore<Preferences>` + `emptyPreferences()` xử lý `IOException` |
| Room 3 | `3.0.3` (`build.gradle.kts:9`, `app/build.gradle.kts:138-139`) | Giữ nguyên `AppDatabase` `version = 9` (`AppDatabase.kt:22`) |
| Coroutines | `1.11.0` (`app/build.gradle.kts:153, 174`) | `collectLatest` + `StandardTestDispatcher` / `testScheduler` |
| OkHttp / MockWebServer3 / Jsoup | `5.3.2` / `5.3.2` / `1.22.2` (`app/build.gradle.kts:143-144, 175`) | Dùng `mockwebserver3` + Jsoup fixture cho scraper test |

#### Skills Loaded & Applied

- **`.agents/skills/apptruyen-investigate/SKILL.md`**: Áp dụng quy trình `/blast-radius` để rà soát toàn bộ callers và unit tests của `StorySource`, `WikidichScraper`, `IStoryRepository`, `StoryRepository`, `SettingsDataStore`, `SettingsViewModel`, `SearchViewModel`, `ExploreViewModel`, `DownloadRepository`, và `StoryDetailViewModel`.
- **`.agents/skills/apptruyen-debug/SKILL.md`**: Áp dụng quy trình truy vết nguyên nhân gốc (Root Cause Analysis) cho 3 lỗi: (1) `SearchViewModel.applyLocalFilters` (`line 350`) bỏ qua lọc `genre` cho `"wikidich"` trong khi `WikidichScraper.search` (`line 135`) không gửi `genre=` lên server và khóa phân trang `canLoadMore = false`; (2) trộn kết quả và danh sách thể loại của 3 nguồn khi bật đa nguồn; (3) bỏ qua chương lỗi không có lượt `retryPass` cuối trong `DownloadRepository` và thiếu `.sortedBy { it.chapterNumber }` trong `StoryDetailViewModel.downloadChapters`/`downloadAll`.
- **`.agents/rules/04-scraping.md` & `.agents/rules/03-ui.md`**: Tuân thủ chuẩn xây dựng URL scraper, bắt buộc gọi `checkLoginRequired(doc)`, chuẩn hóa Unicode NFC tiếng Việt, và UI 100% tiếng Việt.

---

### 2. Các File Cần Thay Đổi

| File | Hành động | Mô tả ngắn |
| :--- | :---: | :--- |
| `app/src/main/java/com/personal/apptruyen/data/local/SettingsDataStore.kt` | Sửa | Bổ sung `SourceIds.DEFAULT_SINGLE_SOURCE`, `SourceIds.ORDERED_SOURCES`, `SourceIds.resolveSingleSource(rawCsv)`; cập nhật `migrateWikidichEnabledSourcesIfNeeded` (đọc `prefs[ENABLED_SOURCES]` bên trong `edit`) và `restoreSettingFromBackup` để luôn chuẩn hóa về 1 nguồn hợp lệ duy nhất |
| `app/src/main/java/com/personal/apptruyen/data/remote/StorySource.kt` | Sửa | Bổ sung overload `search(keyword, completedOnly, page, maleOnly, genreSlug)` với default delegation `if (page <= 1) search(keyword, completedOnly) else emptyList()` |
| `app/src/main/java/com/personal/apptruyen/data/remote/WikidichScraper.kt` | Sửa | Chuẩn hóa đúng 19 thể loại `Loại hình` (`getGenreList` + `GENRE_QUERY_MAP`) và cập nhật `search(...)` gọi `checkLoginRequired(doc)` + `filterStories(...)` + `enrichWithChapterCounts(filtered)` (giữ nguyên `qs=2` ở `getStoriesByGenre`/`getCompletedStories`) |
| `app/src/main/java/com/personal/apptruyen/data/repository/IStoryRepository.kt` | Sửa | Mở rộng `searchStories(...)` với tham số mặc định `page = 1`, `maleOnly = true`, `genreSlug = null`; bổ sung `observeActiveSource(): Flow<String>` và `setActiveSource(sourceId: String)` |
| `app/src/main/java/com/personal/apptruyen/data/repository/StoryRepository.kt` | Sửa | Cập nhật `activeSources()` giữ nguyên `migrateWikidichEnabledSourcesIfNeeded()` + `try/catch` và trả về tập 1 phần tử; triển khai `observeActiveSource()` (kèm `.catch { IOException }`) và `setActiveSource(sourceId)`; truyền `page`/`maleOnly`/`genreSlug` trong `searchStories(...)` |
| `app/src/main/java/com/personal/apptruyen/data/repository/DownloadRepository.kt` | Sửa | Nâng `MAX_RETRY_ATTEMPTS = 3` kèm exponential backoff trong `fetchChapterWithRetry`; tách nguyên văn khối lưu DB `lines 206-228` thành `persistDownloadedChapter(storyId, chapter, result)`; bổ sung lượt tải bù `retryPass` cuối tuân thủ rate-limit Wikidich, kiểm tra lỗi nghiêm trọng và phát `DownloadProgress` 4 tham số |
| `app/src/main/java/com/personal/apptruyen/ui/detail/StoryDetailViewModel.kt` | Sửa | Thêm `.sortedBy { it.chapterNumber }` vào `downloadChapters(count)` và `downloadAll()` trước khi gọi `performDownload(undownloaded)` |
| `app/src/main/java/com/personal/apptruyen/ui/settings/SettingsViewModel.kt` | Sửa | Đổi mặc định `SettingsState.enabledSources` (`line 95`) sang `setOf(SourceIds.DEFAULT_SINGLE_SOURCE)`, chuẩn hóa `enabledSources` luôn chứa 1 nguồn duy nhất qua `SourceIds.resolveSingleSource`, bổ sung `selectSource(sourceId)` (có guard `sourceId in ALL_SOURCES` và ghi `IS_WIKIDICH_MIGRATED = true`) và cho `toggleSource` ủy quyền sang `selectSource` |
| `app/src/main/java/com/personal/apptruyen/ui/settings/SettingsScreen.kt` | Sửa | Cập nhật mục "Quản lý nguồn" (`lines 384-458`, vòng lặp nguồn `lines 394-409`) sang giao diện đơn chọn (`RadioButton` + chọn cả hàng) dùng `SourceIds.SOURCE_LABELS` |
| `app/src/main/java/com/personal/apptruyen/ui/search/SearchViewModel.kt` | Sửa | Quan sát `repository.observeActiveSource()` bằng `collectLatest`, giữ nguyên `val genres: StateFlow<ImmutableList<Genre>>`, bổ sung `activeSource` vào `SearchState`, giữ `activeSource` trong `clearFilter()`, `selectSource(sourceId)`, lưu `lastSubmittedQuery` và mở phân trang từ khóa trên `wikidich` trong `loadMoreFilterResults()` |
| `app/src/main/java/com/personal/apptruyen/ui/search/SearchScreen.kt` | Sửa | Cập nhật điều kiện `shouldLoadMore` (`line 84`) cho phép phân trang khi `state.isFilterActive \|\| state.hasSearched`; hiển thị bộ chọn "Nguồn truyện" trong `FilterBottomSheet` và chip nguồn trên thanh bộ lọc; chỉ hiện bộ lọc "Truyện nam" khi `state.activeSource == SourceIds.WIKIDICH` |
| `app/src/main/java/com/personal/apptruyen/ui/explore/ExploreViewModel.kt` | Sửa | Quan sát `repository.observeActiveSource()` bằng `collectLatest`, bổ sung `activeSource` vào `ExploreState`, `selectSource(sourceId)`, tự động tải lại `genres` và reset `selectedGenre` nếu không thuộc nguồn mới |
| `app/src/main/java/com/personal/apptruyen/ui/explore/ExploreScreen.kt` | Sửa | Hiển thị hàng chọn "Nguồn truyện" (`SourceIds.SOURCE_LABELS`) phía trên hàng chip thể loại; chỉ hiển thị chip `"Truyện nam"` và `VerticalDivider` (`lines 121-152`) khi `state.activeSource == SourceIds.WIKIDICH` |
| `app/src/test/java/com/personal/apptruyen/data/remote/WikidichScraperTest.kt` | Sửa | Cập nhật 3 test theo Type C (19 thể loại `Loại hình`, giữ `qs=2` ở `getStoriesByGenre`/`getCompletedStories`) + thêm test đối chiếu `GENRE_QUERY_MAP` với fixture thật `story_detail.html:329` và `search_logged_in.html:312-378`, test `search` 5 tham số ném `LoginRequiredException`, test URL server-side và lọc 7 nhãn `NON_MALE_WIKIDICH_GENDERS` |
| `app/src/test/java/com/personal/apptruyen/data/repository/StoryRepositoryTest.kt` | Sửa | Cập nhật 2 test `forwards maleOnly` theo Type C (đơn nguồn) + thêm test `observeActiveSource`, `setActiveSource` (dùng `MutableStateFlow<Preferences>`), và `searchStories` truyền `page`/`maleOnly`/`genreSlug` |
| `app/src/test/java/com/personal/apptruyen/data/repository/DownloadRepositoryTest.kt` | Sửa | Cập nhật mốc `testScheduler.currentTime` (`2_250L`) tại dòng `246` theo Type C + thêm test `retryPass` cứu chương lỗi tạm thời ở giữa danh sách và test `retryPass` trên Wikidich tuân thủ `wikidichDelayProvider` & ném ngay `HttpException(429)` |
| `app/src/test/java/com/personal/apptruyen/ui/settings/SettingsViewModelTest.kt` | Sửa | Cập nhật test `enabledSources filters out legacy source IDs and toggleSource protects last active source` theo Type C + thêm test `resolveSingleSource` và `selectSource` |
| `app/src/test/java/com/personal/apptruyen/ui/search/SearchViewModelTest.kt` | Sửa | Stub `observeActiveSource()` + `setActiveSource(any())` trong `setUp()`, thêm test đổi nguồn (`wikidich` → `sstruyen`) tự động reload `viewModel.genres.value` & reset `selectedGenre` không hợp lệ mà vẫn giữ `activeSource == "sstruyen"`, tìm kiếm từ khóa kèm thể loại trên `wikidich` và phân trang `loadMoreFilterResults` |
| `app/src/test/java/com/personal/apptruyen/ui/explore/ExploreViewModelTest.kt` | Sửa | Stub `observeActiveSource()` + `setActiveSource(any())` trong `setUp()`, thêm test đồng bộ `activeSource`, đổi nguồn tự động reload `genres` và reset `selectedGenre` không hợp lệ |
| `app/src/test/java/com/personal/apptruyen/ui/detail/StoryDetailViewModelTest.kt` | Sửa | Thêm test `downloadChapters` và `downloadAll` sắp xếp chương chưa tải theo `chapterNumber` tăng dần qua `DownloadService.startDownload` |
| `docs/architecture.md`, `docs/progress.md`, `docs/changelog.md`, `docs/scraping.md`, `docs/lessons-learned.md`, `docs/plans/README.md` | Sửa | Cập nhật tài liệu kiến trúc (kèm hành vi Circuit Breaker đơn nguồn), tiến độ, lịch sử thay đổi, đặc tả scraper Wikidich 19 thể loại và bài học kinh nghiệm |

---

### 3. Thiết kế chi tiết (Design & Architecture)

#### 3.1. Luồng dữ liệu & Tương tác giữa các Layer

```
SettingsDataStore (SettingsKeys.ENABLED_SOURCES: 1 sourceId duy nhất)
       ▲                                  │
       │ setActiveSource(sourceId)        │ observeActiveSource(): Flow<String>
       │                                  ▼
       ├─────────────────────── StoryRepository (activeSources() -> Set<StorySource> size = 1)
       │                         │        │        │
       ▼                         ▼        ▼        ▼
SettingsViewModel         SearchViewModel    ExploreViewModel
(enabledSources:          (activeSource,     (activeSource,
 setOf(activeSource))      genres StateFlow   genres của nguồn
                           của nguồn chọn,    đang chọn,
                           maleOnly chỉ       maleOnly chỉ cho
                           cho Wikidich)      Wikidich)
```

#### 3.2. Chuẩn hóa Đơn Nguồn Hoạt Động trong `SettingsDataStore.kt` `[SRC: SettingsDataStore.kt:21-35, 108-112, 162-181]`

- Trong `object SourceIds` (`SettingsDataStore.kt:21-35`):
  - Giữ các hằng số `TANGTHUVIEN = "tangthuvien"`, `SSTRUYEN = "sstruyen"`, `WIKIDICH = "wikidich"`, `ALL_ACTIVE_SOURCES`, và `SOURCE_LABELS`.
  - Bổ sung:
    ```kotlin
    const val DEFAULT_SINGLE_SOURCE = TANGTHUVIEN
    val ORDERED_SOURCES: List<String> = listOf(TANGTHUVIEN, SSTRUYEN, WIKIDICH)

    /**
     * Normalizes any stored CSV string into a single valid source ID.
     * Picks the first valid source present in [rawCsv], or falls back to [DEFAULT_SINGLE_SOURCE].
     */
    fun resolveSingleSource(rawCsv: String?): String {
        if (rawCsv.isNullOrBlank()) return DEFAULT_SINGLE_SOURCE
        return rawCsv
            .split(",")
            .map { it.trim() }
            .firstOrNull { it in ALL_ACTIVE_SOURCES }
            ?: DEFAULT_SINGLE_SOURCE
    }
    ```
- Cập nhật `SettingsDataStore.migrateWikidichEnabledSourcesIfNeeded()` (`SettingsDataStore.kt:162-181`) (giải quyết **F7, R9**):
  - Kể cả khi `prefs[SettingsKeys.IS_WIKIDICH_MIGRATED] == true`, nếu giá trị hiện tại ` null || contains(",") || trim() !in SourceIds.ALL_ACTIVE_SOURCES`, hàm vẫn thực hiện `dataStore.edit`. Bên trong khối `dataStore.edit { prefs -> ... }`, đọc trực tiếp `val rawInside = prefs[SettingsKeys.ENABLED_SOURCES]` từ `prefs` hiện hành (không dùng giá trị `currentRaw` đọc ngoài `edit`) rồi ghi `prefs[SettingsKeys.ENABLED_SOURCES] = SourceIds.resolveSingleSource(rawInside)` và `prefs[SettingsKeys.IS_WIKIDICH_MIGRATED] = true`, ngăn chặn hoàn toàn TOCTOU nếu `selectSource` chạy xen giữa.
- Cập nhật `SettingsKeys.restoreSettingFromBackup` (`SettingsDataStore.kt:108-110`):
  - Khi `key == ENABLED_SOURCES.name`, chuẩn hóa `prefs[ENABLED_SOURCES] = SourceIds.resolveSingleSource(rawValue)` để bản sao lưu cũ chứa nhiều nguồn cũng được khôi phục về đúng 1 nguồn hợp lệ.

#### 3.3. Thiết kế 19 Thể loại Chính thống & Tìm kiếm Server-Side trong `WikidichScraper.kt` `[SRC: WikidichScraper.kt:58-61, 83-146, 318-407, 503-573]`

1. **Bằng chứng HTML công khai từ server thật `https://wikicv.org/truyen/<slug>` (`HTTP 200`, không chứa `~`, khảo sát `2026-10-30` bằng `curl.exe -q -s "<url>" | Select-String "genre="`) & fixture thật có sẵn trong repo (`app/src/test/resources/fixtures/wikidich/story_detail.html:316-338`, `search_logged_in.html:267-414`) cho toàn bộ 19 `Loại hình` (giải quyết V1, R5, N1, F2, F8)**:
   - Trên trang chi tiết truyện công khai của `wikicv.org`, khối `<div class="book-desc"><p>Thể loại: <span style="color: #01bed6">` luôn render các thẻ `<a href="/tim-kiem?...">` phân cách bằng `" , "` theo đúng thứ tự schema phân loại của Wikidich: `official=` (Tính chất), `gender=` (Tính hướng), `age=` (Thời đại), `ending=` (Kết cục), **`genre=` (Loại hình)**, rồi đến `tag=` (Nhãn phụ):
     1. Fixture thật có sẵn trong repo `app/src/test/resources/fixtures/wikidich/story_detail.html:316-338` (xác nhận `Manga anime` → `genre=5794f03dd7ced228f44191a8`):
        ```html
        <p>Thể loại: <span style="color: #01bed6">
            <a href="/tim-kiem?qs=1&amp;official=5794f03dd7ced228f4419194">Diễn sinh</a>,
            <a href="/tim-kiem?gender=5794f03dd7ced228f4419195&amp;qs=1">Ngôn tình</a>,
            <a href="/tim-kiem?age=6748055223deea071cb36cd5&amp;qs=1">Giả tưởng lịch sử</a> ,
            <a href="/tim-kiem?qs=1&amp;ending=5794f03dd7ced228f441919f">HE</a> ,
            <a href="/tim-kiem?genre=5794f03dd7ced228f44191a8&amp;qs=1">Manga anime</a> ,
            <a href="/tim-kiem?qs=1&amp;tag=5794f03dd7ced228f44191af">Xuyên việt</a> ,
            <a href="/tim-kiem?qs=1&amp;tag=5794f03dd7ced228f44191b6">Hệ thống</a>
        ```
     2. Fixture thật có sẵn `app/src/test/resources/fixtures/wikidich/search_logged_in.html:312, 324, 330, 372, 378` (và trang live `https://wikicv.org/truyen/huyen-xa-thanh-tien-tu-tran-toc-lao-to-b-ZYzW5VS4CCOI2Vez`, `HTTP 200`): xác nhận 5 `Loại hình` `Huyền huyễn` (`genre=57d17c94d7ced218fcd2734b`), `Võ hiệp` (`genre=5794f03dd7ced228f44191a3`), `Tiên hiệp` (`genre=5794f03dd7ced228f44191a4`), `Trinh thám` (`genre=5794f03dd7ced228f44191a7`), `Kinh dị` (`genre=5794f03dd7ced228f44191a6`).
     3. Bằng chứng cho `Khoa học viễn tưởng` (`genre=5794f03dd7ced228f44191a9`), `Mạt thế` (`genre=5794f03dd7ced228f44191ac`), `Sinh tồn` (`genre=5fd54e6523deea0a1a87e9b9`) trên 3 trang công khai (`HTTP 200`, khảo sát `2026-10-30`, kết quả từng dòng `Select-String "genre="`):
        - `https://wikicv.org/truyen/ta-o-phe-tho-the-gioi-lam-di-hinh-chi-ma-auONUlS4CHBM2Y41` (`HTTP 200`, xác nhận `Khoa học viễn tưởng`, `Mạt thế`, `Sinh tồn`):
          ```html
          <a href="/tim-kiem?genre=5794f03dd7ced228f44191a9&amp;qs=1">Khoa học viễn tưởng</a>
          <a href="/tim-kiem?genre=5794f03dd7ced228f44191ac&amp;qs=1">Mạt thế</a>
          <a href="/tim-kiem?genre=5fd54e6523deea0a1a87e9b9&amp;qs=1">Sinh tồn</a>
          ```
        - `https://wikicv.org/truyen/khai-cuc-ban-di-lao-cha-cong-ty-khoa-hoc-aLkKg1S4CAJP8bAC` (`HTTP 200`, xác nhận `Khoa học viễn tưởng`, `Kinh thương`, `Làm sự nghiệp`):
          ```html
          <a href="/tim-kiem?genre=5794f03dd7ced228f44191a9&amp;qs=1">Khoa học viễn tưởng</a>
          <a href="/tim-kiem?genre=5794f03dd7ced228f44191d4&amp;qs=1">Kinh thương</a>
          <a href="/tim-kiem?genre=6785c24e23deea36d63efe8e&amp;qs=1">Làm sự nghiệp</a>
          ```
        - `https://wikicv.org/truyen/mat-the-da-vat-ta-mac-vao-nu-than-da-mat-aj6iIVS4CCixlhAF` (`HTTP 200`, xác nhận `Mạt thế`, `Sinh tồn`):
          ```html
          <a href="/tim-kiem?genre=5794f03dd7ced228f44191ac&amp;qs=1">Mạt thế</a>
          <a href="/tim-kiem?genre=5fd54e6523deea0a1a87e9b9&amp;qs=1">Sinh tồn</a>
          ```
     4. `https://wikicv.org/truyen/han-phi-chuyen-hinh-lao-dai-lam-ta-chup--auN_RmHe7H_y3fWy` (`HTTP 200`, khảo sát `2026-10-30`, xác nhận `Tình cảm`, `Phim ảnh`, `Làm sự nghiệp`):
        ```html
        <a href="/tim-kiem?genre=5794f03dd7ced228f44191a2&amp;qs=1">Tình cảm</a>
        <a href="/tim-kiem?genre=596bd83012fb557cb030fc5a&amp;qs=1">Phim ảnh</a>
        <a href="/tim-kiem?genre=6785c24e23deea36d63efe8e&amp;qs=1">Làm sự nghiệp</a>
        ```
     5. `https://wikicv.org/truyen/thien-ha-an-khang-XrDm9FS4CCFehJDd` (`HTTP 200`, khảo sát `2026-10-30`, xác nhận `Tranh bá`, `Quan trường`, `Quân sự`, `Làm sự nghiệp`):
        ```html
        <a href="/tim-kiem?genre=5a1ce8f512fb556782b06f7e&amp;qs=1">Tranh bá</a>
        <a href="/tim-kiem?genre=5b10edd012fb550d90428316&amp;qs=1">Quan trường</a>
        <a href="/tim-kiem?genre=6785b91d23deea36d63efe7f&amp;qs=1">Quân sự</a>
        <a href="/tim-kiem?genre=6785c24e23deea36d63efe8e&amp;qs=1">Làm sự nghiệp</a>
        ```
     6. `https://wikicv.org/truyen/trong-sinh-lam-tro-choi-ta-chi-lam-nguye-aqo2JPTaEHfVi5oA` (`HTTP 200`, khảo sát `2026-10-30`, xác nhận `Kinh thương`, `Làm sự nghiệp`):
        ```html
        <a href="/tim-kiem?genre=5794f03dd7ced228f44191d4&amp;qs=1">Kinh thương</a>
        <a href="/tim-kiem?genre=6785c24e23deea36d63efe8e&amp;qs=1">Làm sự nghiệp</a>
        ```
     7. `https://wikicv.org/truyen/ta-o-co-dai-trang-than-tien-nhat-tu-akQGDVS4CGStfdpr` và `https://wikicv.org/truyen/o-co-dai-khai-toan-khoa-benh-vien-alYD2vTaEEtVqO02` (`HTTP 200`, khảo sát `2026-10-30`, xác nhận `Tình cảm`, `Làm ruộng`, `Xây dựng`):
        ```html
        <a href="/tim-kiem?genre=5794f03dd7ced228f44191a2&amp;qs=1">Tình cảm</a>
        <a href="/tim-kiem?genre=5794f03dd7ced228f44191c0&amp;qs=1">Làm ruộng</a>
        <a href="/tim-kiem?genre=5fd3abb623deea0a1a87e9b5&amp;qs=1">Xây dựng</a>
        ```
     8. `https://wikicv.org/truyen/dan-tuc-tu-tuong-tay-huyet-than-bat-dau-Y37F61S4CEaeoXHC` (`HTTP 200`, khảo sát `2026-10-30`, xác nhận `Huyền huyễn`, `Huyền học`):
        ```html
        <a href="/tim-kiem?genre=57d17c94d7ced218fcd2734b&amp;qs=1">Huyền huyễn</a>
        <a href="/tim-kiem?genre=58458dc2d7ced23af03679c5&amp;qs=1">Huyền học</a>
        ```
   - **Giải trình chính xác cấu trúc MongoDB ObjectId (`24-hex` = 4-byte timestamp + 5-byte random + 3-byte counter), 3 ObjectId xung đột với `GENRE_QUERY_MAP` cũ (`WikidichScraper.kt:83-124`) và lý do loại `Võng du` (`genre=5794f03dd7ced228f44191a5`)**:
     - Tiền tố `5794f03d` là Unix timestamp ngày khởi tạo DB ban đầu của Wikidich (`2016-07-24`). Lúc khởi tạo năm 2016:
       - `...19f` (HE), `...1a0` (SE), `...1a1` (BE) là `ending=`.
       - `...1a2` (Tình cảm), `...1a3` (Võ hiệp), `...1a4` (Tiên hiệp), `...1a5` (Võng du - đã ngừng dùng), `...1a6` (Kinh dị), `...1a7` (Trinh thám), `...1a8` (Manga anime), `...1a9` (Khoa học viễn tưởng) là các mục `genre=` gốc (trước đây `WikidichScraper.kt:122` gán nhầm `"oe" to "ending=5794f03dd7ced228f44191a2"`).
       - `...1ac` (Mạt thế), `...1c0` (Làm ruộng), `...1d4` (Kinh thương) ban đầu năm 2016 được tạo trong bảng `tag` (nên thanh lối tắt tĩnh `<div class="tag-tabs">` ở header trang chủ `search_logged_in.html:306, 342` và `WikidichScraper.kt:94, 98` vẫn giữ link tĩnh cũ `tag=5794f03dd7ced228f44191ac&qs=2` cho `"mat-the"` ở dòng `94` và `tag=5794f03dd7ced228f44191c0&qs=2` cho `"lam-ruong"` ở dòng `98`). Về sau, khi Wikidich tái cấu trúc bảng lọc và bổ sung các `Loại hình` mới từ 2016–2025 (`57d17c94...` Huyền huyễn, `58458dc2...` Huyền học, `596bd830...` Phim ảnh, `5a1ce8f5...` Tranh bá, `5b10edd0...` Quan trường, `5fd3abb6...` Xây dựng, `5fd54e65...` Sinh tồn, `6785b91d...` Quân sự, `6785c24e...` Làm sự nghiệp), Wikidich đã chuyển `Mạt thế` (`...1ac`), `Làm ruộng` (`...1c0`), và `Kinh thương` (`...1d4`) sang nhóm **Loại hình (`genre=`)** nhưng **giữ nguyên ObjectId gốc**, như được chứng minh trực tiếp bởi HTML thật trên `wikicv.org/truyen/...` ở trên!
       - **Về `Võng du` (`genre=5794f03dd7ced228f44191a5`)**: Thanh `<div class="tag-tabs">` ở header trang chủ (`search_logged_in.html:274-414`) là danh sách lối tắt tĩnh từ năm 2016 (vẫn giữ `tag=...1ac` cho Mạt thế, `tag=...1c0` cho Làm ruộng, và `genre=...1a5` cho Võng du). Trên hệ thống phân loại hiện hành của `wikicv.org`, các truyện đề tài Du hí / Võng du đã được chuyển sang gắn nhãn `tag=58d92bd6d7ced220e05d73c9` (`Trò chơi`) hoặc `tag=69060c0023deea09ab6f0ec3` (`Trò chơi xâm lấn`) thay vì `genre=...1a5`, như chứng minh trực tiếp trên 2 trang truyện Du hí công khai (`HTTP 200`, khảo sát `2026-10-30`):
         - `https://wikicv.org/truyen/trong-sinh-lam-tro-choi-ta-chi-lam-nguye-aqo2JPTaEHfVi5oA` (`Hán Việt: Trọng sinh tố du hí: Ngã chỉ cảo nguyên sang`): gắn `genre=5794f03dd7ced228f44191d4` (`Kinh thương`), `genre=6785c24e23deea36d63efe8e` (`Làm sự nghiệp`) và `<a href="/tim-kiem?qs=1&amp;tag=58d92bd6d7ced220e05d73c9">Trò chơi</a>`.
         - `https://wikicv.org/truyen/tro-choi-buong-xuong-tho-san-tieu-thu-la-auOBP2He7AWuFBzZ` (`Hán Việt: Du hí hàng lâm: Liệp nhân tiểu tỷ hựu tra hựu dã`): gắn `genre=5794f03dd7ced228f44191ac` (`Mạt thế`) và `<a href="/tim-kiem?qs=1&amp;tag=69060c0023deea09ab6f0ec3">Trò chơi xâm lấn</a>`.
     - **Về `qs=1` vs `qs=2`**: Trên `wikicv.org`, `qs=1` là tham số của form tìm kiếm nhanh có từ khóa `<form id="formQuickSearch"><input name="qs" value="1">` (`search_logged_in.html:267-270`) và link trực tiếp từ trang chi tiết truyện (`story_detail.html:317-343`), trong khi duyệt danh sách lọc nâng cao có sắp xếp `&so=4&vo=1` (`getStoriesByGenre` tại `WikidichScraper.kt:359` và `getCompletedStories` tại `WikidichScraper.kt:325, 340`) dùng `qs=2` (`search_logged_in.html:276-411`). Do đó, giữ nguyên `qs=2` trong `getStoriesByGenre` và `getCompletedStories`, và dùng `?q=$encoded&qs=1...` trong `search(...)`.

2. **Danh sách đúng 19 `Loại hình` (`getGenreList()` tại `WikidichScraper.kt:370-407` và `GENRE_QUERY_MAP` tại `WikidichScraper.kt:83-124`)**:
   ```kotlin
   override suspend fun getGenreList(): List<Genre> =
       listOf(
           Genre("Tình cảm", "tinh-cam"),
           Genre("Làm sự nghiệp", "lam-su-nghiep"),
           Genre("Tiên hiệp", "tien-hiep"),
           Genre("Huyền huyễn", "huyen-huyen"),
           Genre("Khoa học viễn tưởng", "khoa-hoc-vien-tuong"),
           Genre("Mạt thế", "mat-the"),
           Genre("Sinh tồn", "sinh-ton"),
           Genre("Tranh bá", "tranh-ba"),
           Genre("Võ hiệp", "vo-hiep"),
           Genre("Trinh thám", "trinh-tham"),
           Genre("Kinh dị", "kinh-di"),
           Genre("Quan trường", "quan-truong"),
           Genre("Kinh thương", "kinh-thuong"),
           Genre("Quân sự", "quan-su"),
           Genre("Xây dựng", "xay-dung"),
           Genre("Làm ruộng", "lam-ruong"),
           Genre("Huyền học", "huyen-hoc"),
           Genre("Phim ảnh", "phim-anh"),
           Genre("Manga anime", "manga-anime"),
       )

   internal val GENRE_QUERY_MAP =
       mapOf(
           "tinh-cam" to "genre=5794f03dd7ced228f44191a2",
           "lam-su-nghiep" to "genre=6785c24e23deea36d63efe8e",
           "tien-hiep" to "genre=5794f03dd7ced228f44191a4",
           "huyen-huyen" to "genre=57d17c94d7ced218fcd2734b",
           "khoa-hoc-vien-tuong" to "genre=5794f03dd7ced228f44191a9",
           "mat-the" to "genre=5794f03dd7ced228f44191ac",
           "sinh-ton" to "genre=5fd54e6523deea0a1a87e9b9",
           "tranh-ba" to "genre=5a1ce8f512fb556782b06f7e",
           "vo-hiep" to "genre=5794f03dd7ced228f44191a3",
           "trinh-tham" to "genre=5794f03dd7ced228f44191a7",
           "kinh-di" to "genre=5794f03dd7ced228f44191a6",
           "quan-truong" to "genre=5b10edd012fb550d90428316",
           "kinh-thuong" to "genre=5794f03dd7ced228f44191d4",
           "quan-su" to "genre=6785b91d23deea36d63efe7f",
           "xay-dung" to "genre=5fd3abb623deea0a1a87e9b5",
           "lam-ruong" to "genre=5794f03dd7ced228f44191c0",
           "huyen-hoc" to "genre=58458dc2d7ced23af03679c5",
           "phim-anh" to "genre=596bd83012fb557cb030fc5a",
           "manga-anime" to "genre=5794f03dd7ced228f44191a8",
       )
   ```

3. **Hàm lọc hậu kiểm `filterStories(stories, completedOnly, maleOnly)` (giải quyết F1, W2)**:
   - Bổ sung `import java.text.Normalizer` và `import java.util.Locale` ở đầu file `WikidichScraper.kt` (`WikidichScraper.kt:3-24`).
   - Khai báo hằng số `NON_MALE_WIKIDICH_GENDERS` trong `companion object` của `WikidichScraper` (`WikidichScraper.kt:55-80`):
     ```kotlin
     internal val NON_MALE_WIKIDICH_GENDERS =
         setOf(
             "ngôn tình",
             "đam mỹ",
             "bách hợp",
             "nữ tần",
             "nữ tôn",
             "không cp",
             "đa nguyên",
         )
     ```
   - Thay thế khối lọc inline `if (completedOnly) { stories.filter { ... }.ifEmpty { stories } } else { stories }` tại `WikidichScraper.kt:139-144` (vốn có `.ifEmpty { stories }` làm lọt truyện đang ra khi trang không có truyện hoàn thành) bằng hàm `filterStories`:
     ```kotlin
     internal fun filterStories(
         stories: List<Story>,
         completedOnly: Boolean,
         maleOnly: Boolean,
     ): List<Story> {
         var result = stories
         if (completedOnly) {
             result =
                 result.filter { story ->
                     story.status.contains("Hoàn thành", ignoreCase = true) ||
                         story.status.contains("Full", ignoreCase = true)
                 }
         }
         if (maleOnly) {
             result =
                 result.filter { story ->
                     story.genres.none { rawGender ->
                         val normalized =
                             Normalizer
                                 .normalize(rawGender.trim().lowercase(Locale.ROOT), Normalizer.Form.NFC)
                         normalized in NON_MALE_WIKIDICH_GENDERS
                     }
                 }
         }
         return result
     }
     ```

4. **Cập nhật `WikidichScraper.search(...)` (`WikidichScraper.kt:128-146`) và giữ nguyên `qs=2` trong `getCompletedStories(...)` (`lines 318-345`) / `getStoriesByGenre(...)` (`lines 347-368`) (giải quyết F1, F5, N4, R4)**:
   - Trong `StorySource.kt` (`StorySource.kt:34-37`), giữ hàm 2 tham số và thêm overload 5 tham số có default delegation an toàn khi `page > 1`:
     ```kotlin
     suspend fun search(
         keyword: String,
         completedOnly: Boolean = false,
     ): List<Story>

     suspend fun search(
         keyword: String,
         completedOnly: Boolean,
         page: Int,
         maleOnly: Boolean = true,
         genreSlug: String? = null,
     ): List<Story> = if (page <= 1) search(keyword, completedOnly) else emptyList()
     ```
   - Trong `WikidichScraper.kt` (`lines 128-146`):
     - Hàm 2 tham số `override suspend fun search(keyword: String, completedOnly: Boolean)` ủy quyền sang `search(keyword = keyword, completedOnly = completedOnly, page = 1, maleOnly = false, genreSlug = null)` và đặt `q=$encoded&qs=1` ở đầu query string, nhờ đó khi gọi 2 tham số `search("hogwarts", false)`, URL sinh ra là `"/tim-kiem?q=hogwarts&qs=1"`, tương thích ngược 100% với `WikidichScraperTest.kt:262` và các test dùng fixture `search_logged_in.html`.
     - Bắt buộc gọi `checkLoginRequired(doc)` (`WikidichScraper.kt:564-573`) ngay sau `fetchDocumentWithRetry(url)` và gọi đúng hàm `enrichWithChapterCounts(filtered)` (`WikidichScraper.kt:503-562`):
     ```kotlin
     override suspend fun search(
         keyword: String,
         completedOnly: Boolean,
     ): List<Story> =
         search(
             keyword = keyword,
             completedOnly = completedOnly,
             page = 1,
             maleOnly = false,
             genreSlug = null,
         )

     override suspend fun search(
         keyword: String,
         completedOnly: Boolean,
         page: Int,
         maleOnly: Boolean,
         genreSlug: String?,
     ): List<Story> =
         withContext(Dispatchers.IO) {
             val trimmedKeyword = keyword.trim()
             if (trimmedKeyword.isBlank()) return@withContext emptyList()
             val filterParam =
                 if (!genreSlug.isNullOrBlank()) {
                     GENRE_QUERY_MAP[genreSlug] ?: return@withContext emptyList()
                 } else {
                     null
                 }
             val encoded = URLEncoder.encode(trimmedKeyword, "UTF-8")
             val genreParam = if (filterParam != null) "&$filterParam" else ""
             val genderParam = if (maleOnly) "&$MALE_GENDER_PARAM" else ""
             val statusParam = if (completedOnly) "&$COMPLETED_STATUS_PARAM" else ""
             val start = (page.coerceAtLeast(1) - 1) * SEARCH_PAGE_SIZE
             val startParam = if (start > 0) "&start=$start" else ""
             val url = "$baseUrl/tim-kiem?q=$encoded&qs=1$genreParam$genderParam$statusParam$startParam"
             val doc = fetchDocumentWithRetry(url)
             checkLoginRequired(doc)
             val stories = parseStoryList(doc)
             val filtered = filterStories(stories, completedOnly = completedOnly, maleOnly = maleOnly)
             enrichWithChapterCounts(filtered)
         }
     ```
   - Trong `WikidichScraper.getCompletedStories` (`WikidichScraper.kt:318-345`) và `getStoriesByGenre` (`WikidichScraper.kt:347-368`): giữ nguyên không đổi (`qs=2`, `checkLoginRequired(doc)` và logic server-side `genderParam` / `statusParam` / `NEWEST_SORT_PARAM` hiện có).

#### 3.4. Caller-First Design Sketch: `IStoryRepository`, `StoryRepository`, `SearchViewModel`, `ExploreViewModel` (giải quyết F4, F5, F6, F7, F11, N2, N4, N6, N7, R2, R3, R6)

```kotlin
// 1. IStoryRepository.kt [SRC: IStoryRepository.kt:15-24]
interface IStoryRepository {
    fun observeActiveSource(): Flow<String>
    suspend fun setActiveSource(sourceId: String)

    suspend fun searchStories(
        keyword: String,
        completedOnly: Boolean = false,
        page: Int = 1,
        maleOnly: Boolean = true,
        genreSlug: String? = null,
    ): List<Story>
    // ... existing methods unchanged
}

// 2. StoryRepository.kt [SRC: StoryRepository.kt:75-89, 129-197]
override fun observeActiveSource(): Flow<String> =
    dataStore.data
        .catch { e ->
            if (e is java.io.IOException) {
                Timber.w(e, "Failed to read DataStore in observeActiveSource, emitting emptyPreferences")
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw e
            }
        }
        .map { prefs -> SourceIds.resolveSingleSource(prefs[SettingsKeys.ENABLED_SOURCES]) }
        .distinctUntilChanged()

override suspend fun setActiveSource(sourceId: String) {
    if (sourceId !in SourceIds.ALL_ACTIVE_SOURCES) return
    dataStore.edit { prefs ->
        prefs[SettingsKeys.ENABLED_SOURCES] = sourceId
        prefs[SettingsKeys.IS_WIKIDICH_MIGRATED] = true
    }
}

private suspend fun activeSources(): Set<StorySource> {
    dataStore.migrateWikidichEnabledSourcesIfNeeded()
    val raw =
        try {
            dataStore.data.first()[SettingsKeys.ENABLED_SOURCES]
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.w(e, "Failed to read enabled sources from DataStore, falling back to default single source")
            null
        }
    val activeId = SourceIds.resolveSingleSource(raw)
    val matched = sources.filter { it.sourceId == activeId }.toSet()
    return matched.ifEmpty { sources.take(1).toSet() }
}

// 3. Call site in SearchViewModel.kt [SRC: SearchViewModel.kt:48-63, 90-91, 119-121, 253, 295, 441-490]
// Giữ nguyên _genres / genres là StateFlow<ImmutableList<Genre>> riêng (SearchViewModel.kt:90-91).
// Chỉ bổ sung val activeSource: String = SourceIds.DEFAULT_SINGLE_SOURCE vào SearchState (line 48-63).
private var lastObservedSource: String? = null
private var lastSubmittedQuery: String = ""

init {
    loadGenres()
    observeActiveSourceChanges()
}

private fun observeActiveSourceChanges() {
    viewModelScope.launch {
        repository.observeActiveSource().collectLatest { sourceId ->
            val previous = lastObservedSource
            lastObservedSource = sourceId
            _state.update { it.copy(activeSource = sourceId) }
            // Phát xạ đầu tiên (previous == null): init{} đã gọi loadGenres() (bên trong StoryRepository
            // đã đọc nguồn hiện tại từ DataStore qua activeSources()), nên chỉ gọi handleActiveSourceSwitched()
            // khi người dùng thực sự chuyển nguồn (previous != null && previous != sourceId).
            if (previous != null && previous != sourceId) {
                handleActiveSourceSwitched()
            }
        }
    }
}

private suspend fun handleActiveSourceSwitched() {
    val newGenres =
        try {
            repository.getGenreList().toImmutableList()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to reload genres after source switch")
            persistentListOf()
        }
    _genres.value = newGenres
    val current = _state.value
    val validGenre = current.selectedGenre?.takeIf { g -> newGenres.any { it.slug == g.slug } }
    val filterActive =
        hasAnyNonDefaultFilter(
            genre = validGenre,
            chapterFilter = current.chapterFilter,
            completedOnly = current.completedOnly,
            maleOnly = current.maleOnly,
            activeSource = current.activeSource,
        )
    _state.update {
        it.copy(
            selectedGenre = validGenre,
            isFilterActive = filterActive,
            error = null,
        )
    }
    val s = _state.value
    if (s.query.trim().isNotBlank() && s.hasSearched) {
        search()
    } else if (s.selectedGenre != null) {
        browseByGenre(s.selectedGenre, s.chapterFilter)
    } else if (s.isFilterActive) {
        browseByFilterOnly(s.chapterFilter)
    } else if (current.selectedGenre != null && validGenre == null) {
        clearFilter()
    }
}

private fun hasAnyNonDefaultFilter(
    genre: Genre?,
    chapterFilter: ChapterCountFilter,
    completedOnly: Boolean,
    maleOnly: Boolean,
    activeSource: String = _state.value.activeSource,
): Boolean =
    genre != null ||
        chapterFilter != ChapterCountFilter.NONE ||
        !completedOnly ||
        (activeSource == SourceIds.WIKIDICH && !maleOnly)

fun clearFilter() {
    searchJob?.cancel()
    lastSubmittedQuery = ""
    _state.value = SearchState(activeSource = _state.value.activeSource)
}
```

- **Phân nhánh theo `activeSource == SourceIds.WIKIDICH`, lưu `lastSubmittedQuery` và mở khóa phân trang từ khóa trong `SearchViewModel.search()`, `loadMoreFilterResults()` (`SearchViewModel.kt:138-177, 253-339, 441-490`) và `SearchScreen.kt:84` (giải quyết F4, F5, N4, R3, R6, V3, V4)**:
  - Trong `SearchViewModel.search()` (`SearchViewModel.kt:138-177`):
    - Lưu `lastSubmittedQuery = query` (sau khi `val query = currentState.query.trim()`).
    - Khi `currentState.activeSource == SourceIds.WIKIDICH`:
      - Gọi `val rawResults = repository.searchStories(keyword = query, completedOnly = currentState.completedOnly, page = 1, maleOnly = currentState.maleOnly, genreSlug = currentState.selectedGenre?.slug)`.
      - Lọc qua `val filtered = applyLocalFilters(rawResults)` (giữ `applyLocalFilters` cho cả hai nhánh để bộ lọc số chương `chapterFilter` được áp dụng nhất quán ở cả trang 1 lẫn trang 2+), cập nhật `canLoadMore = rawResults.isNotEmpty() && _state.value.chapterFilter == ChapterCountFilter.NONE`, `currentPage = 1`.
    - Khi `currentState.activeSource != SourceIds.WIKIDICH` (`tangthuvien` hoặc `sstruyen`):
      - Giữ nguyên lời gọi `val rawResults = repository.searchStories(query, completedOnly = false)` và `canLoadMore = false`, rồi lọc client-side qua `val filtered = applyLocalFilters(rawResults)`. Nhờ đó không làm thu hẹp kết quả tìm kiếm trên TTV/SsTruyện và tương thích ngược 100% với unit test hiện có (`SearchViewModelTest.kt:388`).
  - Trong `browseByGenre(...)` (`line 253`), `browseByFilterOnly(...)` (`line 295`) và `clearFilter()` (`line 487`):
    - Đặt `lastSubmittedQuery = ""` để đánh dấu chế độ duyệt bộ lọc không từ khóa. Riêng `clearFilter()` khởi tạo `SearchState(activeSource = _state.value.activeSource)` để không làm lệch `activeSource` về `"tangthuvien"`.
  - Trong `SearchViewModel.loadMoreFilterResults()` (`SearchViewModel.kt:441-485`) (và thêm hàm alias `fun loadMore() = loadMoreFilterResults()`):
    - Đổi điều kiện guard đầu hàm từ `if (current.isLoadingMore || !current.canLoadMore || !current.isFilterActive) return` thành:
      `if (current.isLoading || current.isLoadingMore || !current.canLoadMore || (!current.isFilterActive && !current.hasSearched)) return`
    - Bên trong `viewModelScope.launch`, phân nhánh theo `lastSubmittedQuery` (thay vì `current.query.trim()`, ngăn trộn kết quả nếu người dùng đang gõ dở chữ trong ô tìm kiếm mà chưa bấm tìm):
      1. `lastSubmittedQuery.isNotBlank()` (tìm kiếm có từ khóa trên Wikidich): gọi `val rawMore = repository.searchStories(keyword = lastSubmittedQuery, completedOnly = current.completedOnly, page = nextPage, maleOnly = current.maleOnly, genreSlug = current.selectedGenre?.slug)`, sau đó `applyLocalFilters(rawMore)`.
      2. `current.selectedGenre != null` (duyệt theo thể loại không có từ khóa): giữ nguyên lời gọi `repository.getStoriesByGenre(...)`.
      3. `current.isFilterActive` (duyệt truyện hoàn thành / truyện nam không có từ khóa): **bổ sung mới hành vi tải trang tiếp theo cho chế độ `browseByFilterOnly`** (khi `lastSubmittedQuery.isBlank() && current.selectedGenre == null && current.isFilterActive`, thay vì `return` sớm khi `selectedGenre == null` như tại `SearchViewModel.kt:447` hiện tại): gọi `val rawMore = repository.getCompletedStories(page = nextPage, maleOnly = current.maleOnly)` rồi lọc qua `applyLocalFilters(rawMore)` (giải quyết **W3**).
  - Trong `SearchScreen.kt` (`SearchScreen.kt:77-92`):
    - Sửa dòng `84` trong `shouldLoadMore`:
      ```kotlin
      lastVisibleItem >= totalItems - 3 &&
          !state.isLoading &&
          !state.isLoadingMore &&
          state.canLoadMore &&
          (state.isFilterActive || state.hasSearched)
      ```

#### 3.5. Thiết kế Chống Nhảy Cóc Chương Khi Tải (`DownloadRepository.kt` & `StoryDetailViewModel.kt`) `[SRC: DownloadRepository.kt:50-70, 127-285, ChapterDao.kt:93-100, StoryRepository.kt:826-834, StoryDetailViewModel.kt:263-317]` (giải quyết F3, F8, N3, R1)

1. **Trong `DownloadRepository.kt`**:
   - Nâng `MAX_RETRY_ATTEMPTS` (`DownloadRepository.kt:50`) từ `2` lên `3` (tức 1 lần tải đầu + tối đa 2 lần retry tại chỗ) và áp dụng exponential backoff tại dòng `156` của `fetchChapterWithRetry` (`DownloadRepository.kt:127-161`):
     ```kotlin
     val backoffDelay = retryDelayMs * (1L shl (attempt - 1))
     delay(backoffDelay)
     ```
     - Với nguồn thường (`BASE_BATCH_DELAY_MS = 250L`): chờ `250ms` (attempt 1 → 2), `500ms` (attempt 2 → 3).
     - Với Wikidich (`WIKIDICH_RETRY_DELAY_MS = 5_000L`): chờ `5000ms` (attempt 1 → 2), `10000ms` (attempt 2 → 3).
   - **Tách nguyên văn 100% khối lưu DB trong vòng lặp chính (`DownloadRepository.kt:206-228`) thành hàm dùng chung `persistDownloadedChapter` (với `titleToSave: String` không nullable khớp `ChapterDao.kt:93-100`)**:
     ```kotlin
     private suspend fun persistDownloadedChapter(
         storyId: String,
         chapter: Chapter,
         result: ChapterContentResult,
     ) {
         val isDummy = chapter.title.matches(DUMMY_TITLE_REGEX)
         val titleToSave =
             if (!result.title.isNullOrBlank() && (isDummy || chapter.title.isBlank())) {
                 result.title
             } else {
                 chapter.title
             }
         chapterDao.saveChapterContent(
             storyId = storyId,
             chapterNumber = chapter.chapterNumber,
             content = result.content,
             title = titleToSave,
             url = chapter.url,
         )

         if (!result.title.isNullOrBlank() && (isDummy || chapter.title.isBlank())) {
             chapterDao.updateChapterMetadata(
                 storyId = storyId,
                 chapterNumber = chapter.chapterNumber,
                 title = result.title,
                 url = chapter.url,
             )
         }
     }
     ```
   - **Bổ sung lượt tải bù `retryPass` cuối trong `downloadChapters` (`DownloadRepository.kt:163-285`)**:
     - Khai báo `val failedChapters = mutableListOf<Chapter>()` trước vòng lặp batch (`line 194`).
     - Trong vòng lặp xử lý kết quả batch (`lines 204-244`), khi `result != null` thì thay khối `lines 206-228` bằng lời gọi `persistDownloadedChapter(storyId, chapter, result)`; khi `result == null` (`lines 230-244`) thì thêm `failedChapters.add(chapter)` (cùng với `errors++` và `batchErrors++` như hiện tại).
     - Ngay sau khi vòng lặp `for ((batchIndex, batch) in batches.withIndex())` kết thúc (`line 284`), nếu `consecutiveFailedBatches < MAX_CONSECUTIVE_FAILED_BATCHES && failedChapters.isNotEmpty()`:
       ```kotlin
       if (consecutiveFailedBatches < MAX_CONSECUTIVE_FAILED_BATCHES && failedChapters.isNotEmpty()) {
           for (chapter in failedChapters.sortedBy { it.chapterNumber }) {
               currentCoroutineContext().ensureActive()
               val retryInterChapterDelay =
                   if (isWikidich) {
                       wikidichDelayProvider().coerceIn(WIKIDICH_MIN_DELAY_MS, WIKIDICH_MAX_DELAY_MS)
                   } else {
                       currentDelay
                   }
               if (retryInterChapterDelay > 0L) {
                   delay(retryInterChapterDelay)
               }
               val retryDelayMs = if (isWikidich) WIKIDICH_RETRY_DELAY_MS else currentDelay
               val outcome = fetchChapterWithRetry(source, chapter, retryDelayMs)
               currentCoroutineContext().ensureActive()
               val result = outcome.result
               if (result != null) {
                   persistDownloadedChapter(storyId, chapter, result)
                   downloaded++
                   errors = (errors - 1).coerceAtLeast(0)
                   emit(DownloadProgress(downloaded, sortedChapters.size, chapter.title, errors))
               } else {
                   val fatalRetryError =
                       outcome.error?.takeIf { err ->
                           err is ScraperException.LoginRequiredException ||
                               err is ScraperException.CircuitOpenException ||
                               (isWikidich && isRateLimitHttpError(err))
                       } as? ScraperException
                   if (fatalRetryError != null) {
                       throw fatalRetryError
                   }
               }
           }
       }
       ```
     - Lưu ý: Cả vòng lặp chính và `retryPass` đều gọi cùng hàm `persistDownloadedChapter(storyId, chapter, result)` (bảo toàn 100% hành vi của `DownloadRepositoryTest.kt:186-219, 517-538`), dùng đúng các lớp có thật trong `ScraperException.kt` (`LoginRequiredException`, `CircuitOpenException`, `HttpException` kiểm tra qua `isRateLimitHttpError(err)` tại `DownloadRepository.kt:68-69`) và đúng constructor 4 tham số `DownloadProgress(current, total, currentChapterTitle, errors)` tại `StoryRepository.kt:826-834`.
2. **Trong `StoryDetailViewModel.kt` (`StoryDetailViewModel.kt:263-295`)**:
   - Sửa `downloadChapters(count: Int)` (`lines 263-270`) và `downloadAll()` (`lines 272-276`) để luôn `.sortedBy { it.chapterNumber }` trước khi gọi `performDownload(undownloaded)` (`line 295`) (còn `downloadRange` ở `lines 278-288` đã có `.sortedBy { it.chapterNumber }` tại `line 285` nên giữ nguyên):
     ```kotlin
     fun downloadChapters(count: Int) {
         val undownloaded =
             _state.value.chapters
                 .filter { !it.isDownloaded }
                 .sortedBy { it.chapterNumber }
                 .take(count)
         if (undownloaded.isEmpty()) return
         performDownload(undownloaded)
     }

     fun downloadAll() {
         val undownloaded =
             _state.value.chapters
                 .filter { !it.isDownloaded }
                 .sortedBy { it.chapterNumber }
         if (undownloaded.isEmpty()) return
         performDownload(undownloaded)
     }
     ```

---

### 4. Kịch bản Nhiễu loạn & Đối kháng (Adversarial Timeline — 4 Trục)

#### Trục 1 — Xung đột đồng thời khi chuyển nguồn liên tiếp (Race Condition on Rapid Source Switching)
- **Timeline**:
  1. `t = 0ms`: Nguồn đang là `wikidich`. Người dùng mở `ExploreScreen`, bấm chuyển sang `tangthuvien`.
  2. `t = 10ms`: Coroutine trong `collectLatest` bắt đầu tải `getGenreList()` và `loadFirstPage()` của `tangthuvien` (tốn `400ms`).
  3. `t = 50ms`: Người dùng bấm chuyển tiếp sang `sstruyen` ngay lập tức.
- **Cơ chế bảo vệ**:
  - `observeActiveSourceChanges()` trong cả `SearchViewModel` và `ExploreViewModel` dùng `collectLatest` (giải quyết **F11**), đồng thời `loadFirstPage()` / `search()` hủy `loadJob?.cancel()` / `searchJob?.cancel()` trước khi chạy. Khi `sstruyen` phát ra ở `t = 50ms`, khối xử lý đang chạy của `tangthuvien` bị hủy ngay lập tức, `CancellationException` được ném lại (`throw e`) và không bao giờ ghi đè kết quả cũ lên giao diện `sstruyen`.

#### Trục 2 — Đầu vào Độc hại / Mâu thuẫn Trạng thái khi Đổi nguồn (`selectedGenre` không tồn tại ở nguồn mới)
- **Timeline**:
  1. Người dùng đang ở nguồn `Wikidich`, chọn thể loại `"Làm sự nghiệp"` (`slug = "lam-su-nghiep"`).
  2. Người dùng mở `FilterBottomSheet` trên `SearchScreen` (hoặc thanh nguồn trên `ExploreScreen`) và đổi nguồn sang `Tàng Thư Viện`.
  3. `Tàng Thư Viện` chỉ có 12 thể loại (`tien-hiep`, `huyen-huyen`, `do-thi`...), không có `"lam-su-nghiep"`.
- **Cơ chế bảo vệ**:
  - Ngay trong `handleActiveSourceSwitched()` của `SearchViewModel` và `ExploreViewModel`, sau khi tải `newGenres = repository.getGenreList()`, kiểm tra `val validGenre = currentGenre?.takeIf { g -> newGenres.any { it.slug == g.slug } }`. Vì `"lam-su-nghiep"` không thuộc `newGenres`, `selectedGenre` được tự động reset về `null` ("Tất cả") trước khi gọi lại truy vấn.

#### Trục 3 — Sự cố Mạng Chập chờn Giữa Chừng Khi Tải Chương (`DownloadRepository` Transient Network Drop)
- **Timeline**:
  1. Người dùng tải 20 chương (`Chương 1` → `Chương 20`, chia làm 5 batch × 4 chương).
  2. Ở Batch 2 (`Chương 5..8`), Wi-Fi chập chờn khiến `Chương 6` thất bại cả 3 lần thử trong `fetchChapterWithRetry` (`IOException`), trong khi `Chương 5, 7, 8` tải thành công.
  3. Batch 3, 4, 5 (`Chương 9..20`) mạng ổn định trở lại và tải thành công 100%.
- **Cơ chế bảo vệ**:
  - `Chương 6` được ghi nhận vào `failedChapters`. Sau khi Batch 5 hoàn tất, vì `consecutiveFailedBatches == 0 < MAX_CONSECUTIVE_FAILED_BATCHES`, `DownloadRepository` tự động chạy `retryPass` cho `Chương 6` (chờ đúng `retryInterChapterDelay` trước khi tải). Lúc này mạng đã ổn định, `Chương 6` tải thành công, lưu vào Room DB, tăng `downloaded = 20`, giảm `errors = 0`, và phát `DownloadProgress(20, 20, "Chương 6", 0)`.

#### Trục 4 — Hủy Coroutine & Lỗi Rate-Limit 429 Khi Đang Tải Bù Trên Wikidich (`Cancellation & Rate-Limit Abort`)
- **Timeline**:
  1. `DownloadRepository` đang chạy `retryPass` cho 3 chương lỗi của truyện Wikidich (`isWikidich == true`).
  2. Ở chương lỗi đầu tiên trong `retryPass`, server Wikidich trả về `ScraperException.HttpException(statusCode = 429)` (hoặc `LoginRequiredException`, hoặc người dùng bấm Hủy tải).
- **Cơ chế bảo vệ**:
  - Trước và sau `fetchChapterWithRetry`, `currentCoroutineContext().ensureActive()` kiểm tra hủy coroutine. Khi `outcome.error` là `LoginRequiredException`, `CircuitOpenException`, hoặc `isWikidich && isRateLimitHttpError(err)` (`statusCode in setOf(403, 429, 503)`), `retryPass` ném ngay `fatalRetryError` để dừng phiên tải tức khắc, tuyệt đối không gửi tiếp request cho 2 chương lỗi còn lại.

---

### 5. Các bước triển khai (Step-by-Step)

#### Bước 1: Chuẩn hóa Đơn Nguồn Hoạt Động trong `SettingsDataStore.kt` `[SRC: SettingsDataStore.kt:21-35, 108-112, 162-181]`
1. Trong `SourceIds`:
   - Thêm `const val DEFAULT_SINGLE_SOURCE = TANGTHUVIEN`.
   - Thêm `val ORDERED_SOURCES: List<String> = listOf(TANGTHUVIEN, SSTRUYEN, WIKIDICH)`.
   - Thêm hàm `fun resolveSingleSource(rawCsv: String?): String`.
2. Cập nhật `SettingsDataStore.migrateWikidichEnabledSourcesIfNeeded()` (`lines 162-181`, đọc trực tiếp `val rawInside = prefs[SettingsKeys.ENABLED_SOURCES]` bên trong `dataStore.edit { prefs -> ... }`) và `SettingsKeys.restoreSettingFromBackup()` (`lines 108-110`) để luôn chuẩn hóa giá trị lưu trong `SettingsKeys.ENABLED_SOURCES` về 1 nguồn hợp lệ duy nhất thông qua `SourceIds.resolveSingleSource(...)`.

#### Bước 2: Cập nhật `StorySource` & `WikidichScraper` `[SRC: StorySource.kt:34-37, WikidichScraper.kt:83-146, 318-407, 503-573]`
1. Trong `StorySource.kt`:
   - Bổ sung overload `suspend fun search(keyword: String, completedOnly: Boolean, page: Int, maleOnly: Boolean = true, genreSlug: String? = null): List<Story> = if (page <= 1) search(keyword, completedOnly) else emptyList()`.
2. Trong `WikidichScraper.kt`:
   - Bổ sung `import java.text.Normalizer` và `import java.util.Locale` ở đầu file (`lines 3-24`, giải quyết **W2**).
   - Thay thế `getGenreList()` (`lines 370-407`, hiện có 35 mục) và `GENRE_QUERY_MAP` (`lines 83-124`, hiện có 42 khóa) bằng đúng 19 thể loại thuộc mục `Loại hình` (`genre=<24-hex ObjectId>`) của `wikicv.org`.
   - Thêm `NON_MALE_WIKIDICH_GENDERS` (7 nhãn: `"ngôn tình"`, `"đam mỹ"`, `"bách hợp"`, `"nữ tần"`, `"nữ tôn"`, `"không cp"`, `"đa nguyên"`) và hàm `internal fun filterStories(stories: List<Story>, completedOnly: Boolean, maleOnly: Boolean): List<Story>`.
   - Triển khai `override suspend fun search(keyword, completedOnly, page, maleOnly, genreSlug)` xây dựng URL `$baseUrl/tim-kiem?q=$encoded&qs=1$genreParam$genderParam$statusParam$startParam`, gọi `checkLoginRequired(doc)`, lọc hậu kiểm qua `filterStories(...)`, và gọi `enrichWithChapterCounts(filtered)`.
   - Giữ nguyên `getCompletedStories` (`lines 318-345`) và `getStoriesByGenre` (`lines 347-368`) dùng `qs=2` kèm `&so=4&vo=1`.
   - Giữ nguyên `TangThuVienScraper.kt` và `SsTruyenScraper.kt` không chỉnh sửa.

#### Bước 3: Cập nhật `IStoryRepository` & `StoryRepository` `[SRC: IStoryRepository.kt:15-24, StoryRepository.kt:75-89, 129-197]`
1. Trong `IStoryRepository.kt`:
   - Thêm `fun observeActiveSource(): Flow<String>` và `suspend fun setActiveSource(sourceId: String)`.
   - Mở rộng `suspend fun searchStories(keyword: String, completedOnly: Boolean = false, page: Int = 1, maleOnly: Boolean = true, genreSlug: String? = null): List<Story>`.
2. Trong `StoryRepository.kt`:
   - Triển khai `observeActiveSource()` (có `.catch { if (it is IOException) emit(emptyPreferences()) else throw it }`) và `setActiveSource(sourceId)`.
   - Cập nhật `activeSources()` (`lines 75-89`) giữ nguyên `dataStore.migrateWikidichEnabledSourcesIfNeeded()` và khối `try/catch` (rethrow `CancellationException`), phân giải `val activeId = SourceIds.resolveSingleSource(raw)` và trả về tập 1 phần tử `sources.filter { it.sourceId == activeId }.toSet().ifEmpty { sources.take(1).toSet() }`.
   - Trong `searchStories(keyword, completedOnly, page, maleOnly, genreSlug)`:
     - Khi `page == 1 && maleOnly && genreSlug == null` và `source.sourceId != SourceIds.WIKIDICH`, bắt buộc gọi hàm 2 tham số `source.search(keyword, completedOnly)`; còn với `source.sourceId == SourceIds.WIKIDICH` hoặc khi `page > 1 || !maleOnly || !genreSlug.isNullOrBlank()` thì gọi hàm 5 tham số `source.search(keyword, completedOnly, page, maleOnly, genreSlug)` (quy tắc định tuyến này bắt buộc phải giữ vì `StoryRepositoryTest.kt:49` dùng `mockk<StorySource>()` không relaxed — MockK không tự chạy default method của interface trên mock chỉ stub 2 tham số, giải quyết **V5**).

#### Bước 4: Cập nhật `SettingsViewModel` & `SettingsScreen` `[SRC: SettingsViewModel.kt:95, 118-123, 224-239, SettingsScreen.kt:384-458]`
1. Trong `SettingsViewModel.kt`:
   - Đổi giá trị mặc định trong `SettingsState` (`SettingsViewModel.kt:95`) từ `val enabledSources: Set<String> = ALL_SOURCES` sang `val enabledSources: Set<String> = setOf(SourceIds.DEFAULT_SINGLE_SOURCE)`.
   - Trong `observeSettings()`, phân giải `val activeSource = SourceIds.resolveSingleSource(prefs[SettingsKeys.ENABLED_SOURCES])` và gán `enabledSources = setOf(activeSource)`.
   - Thêm `fun selectSource(sourceId: String)`: kiểm tra guard `if (sourceId !in ALL_SOURCES) return` trước khi gọi `dataStore.edit`, và ghi cả `prefs[SettingsKeys.ENABLED_SOURCES] = sourceId` lẫn `prefs[SettingsKeys.IS_WIKIDICH_MIGRATED] = true`.
   - Cho `fun toggleSource(sourceId: String)` gọi thẳng `selectSource(sourceId)`.
2. Trong `SettingsScreen.kt` (`lines 384-458`, vòng lặp nguồn `lines 394-409`):
   - Trong mục "Quản lý nguồn", thay `Switch` bằng `RadioButton` (và cho phép nhấn vào cả dòng nguồn để gọi `viewModel.selectSource(sourceId)`), dùng `SourceIds.SOURCE_LABELS` (`"tangthuvien" to "TàngThưViện"`, `"sstruyen" to "SsTruyện"`, `"wikidich" to "Wikidich"`) và cập nhật phụ đề hướng dẫn: `"Chọn 1 nguồn hoạt động cho Tìm kiếm và Khám phá (truyện trong Tủ truyện vẫn đọc/tải bình thường)"`.

#### Bước 5: Cập nhật `SearchViewModel`, `SearchScreen`, `ExploreViewModel`, `ExploreScreen` `[SRC: SearchViewModel.kt:48-63, 75-84, 90-91, 119-177, 253, 295, 315-372, 441-490, SearchScreen.kt:77-92, 190-270, 550-715, ExploreViewModel.kt:23-232, ExploreScreen.kt:85-160]`
1. Trong `SearchViewModel.kt`:
   - Thêm `val activeSource: String = SourceIds.DEFAULT_SINGLE_SOURCE` vào `SearchState` (`lines 48-63`); giữ nguyên `val genres: StateFlow<ImmutableList<Genre>>` (`lines 90-91`); thêm biến `private var lastSubmittedQuery: String = ""`.
   - Cập nhật `hasAnyNonDefaultFilter` (`lines 75-84`) nhận thêm `activeSource: String = _state.value.activeSource` và chỉ tính `!maleOnly` khi `activeSource == SourceIds.WIKIDICH` (giải quyết **V4**).
   - Cập nhật `NON_MALE_WIKIDICH_GENDERS` (`lines 99-105`) đủ 7 nhãn (`"ngôn tình"`, `"đam mỹ"`, `"bách hợp"`, `"nữ tần"`, `"nữ tôn"`, `"không cp"`, `"đa nguyên"`).
   - Quan sát `repository.observeActiveSource().collectLatest` trong `init`: ở phát xạ đầu tiên (`previous == null`), chỉ cập nhật `_state.update { it.copy(activeSource = sourceId) }` mà không gọi `handleActiveSourceSwitched()` (vì `init` đã gọi `loadGenres()`); chỉ khi `previous != null && previous != sourceId` mới gọi `handleActiveSourceSwitched()` để tải lại `_genres.value` của nguồn mới, kiểm tra `validGenre`, tính lại `isFilterActive = hasAnyNonDefaultFilter(..., activeSource = current.activeSource)`, và gọi `search()` / `browseByGenre(s.selectedGenre, s.chapterFilter)` / `browseByFilterOnly(s.chapterFilter)` / `clearFilter()`.
   - Sửa `clearFilter()` (`line 487`) để gán `lastSubmittedQuery = ""` và `_state.value = SearchState(activeSource = _state.value.activeSource)`.
   - Bổ sung `fun selectSource(sourceId: String)`.
   - Trong `search()` (`lines 138-177`):
     - Gán `lastSubmittedQuery = query`.
     - Khi `currentState.activeSource == SourceIds.WIKIDICH`: gọi `repository.searchStories(keyword = query, completedOnly = currentState.completedOnly, page = 1, maleOnly = currentState.maleOnly, genreSlug = currentState.selectedGenre?.slug)`, lọc qua `val filtered = applyLocalFilters(rawResults)`, đặt `canLoadMore = rawResults.isNotEmpty() && _state.value.chapterFilter == ChapterCountFilter.NONE`.
     - Khi `currentState.activeSource != SourceIds.WIKIDICH` (`tangthuvien` hoặc `sstruyen`): giữ nguyên lời gọi `repository.searchStories(query, completedOnly = false)` và `canLoadMore = false`, rồi lọc client-side qua `val filtered = applyLocalFilters(rawResults)` (giữ `applyLocalFilters` ở cả hai nhánh, giải quyết **V3**).
   - Trong `browseByGenre(...)` (`line 253`) và `browseByFilterOnly(...)` (`line 295`): gán `lastSubmittedQuery = ""`.
   - Trong `loadMoreFilterResults()` (`lines 441-485`) (và thêm `fun loadMore() = loadMoreFilterResults()`), hỗ trợ tải trang tiếp theo (`nextPage = current.currentPage + 1`) phân nhánh theo `lastSubmittedQuery.isNotBlank()` cho tìm kiếm từ khóa trên Wikidich, duyệt theo thể loại (`current.selectedGenre != null`), hoặc **bổ sung mới hành vi tải trang tiếp theo cho chế độ `browseByFilterOnly`** (`current.isFilterActive` khi `selectedGenre == null`, gọi `repository.getCompletedStories(page = nextPage, maleOnly = current.maleOnly)` + `applyLocalFilters(rawMore)`, giải quyết **W3**).
   - Giữ nguyên logic `matchesGenre` trong `applyLocalFilters()` (`lines 348-354`).
2. Trong `SearchScreen.kt`:
   - Sửa `shouldLoadMore` tại dòng `84` thành `(state.isFilterActive || state.hasSearched)`.
   - Trong `FilterBottomSheet`, thêm mục **"Nguồn truyện"** với 3 `FilterChip` lấy nhãn từ `SourceIds.SOURCE_LABELS` ở đầu bảng lọc; chỉ hiển thị mục **"Phân loại (Wikidich)"** (`Tất cả` / `Truyện nam`) khi `state.activeSource == SourceIds.WIKIDICH`; khi `state.activeSource != SourceIds.WIKIDICH && state.query.trim().isNotBlank()`, hiển thị dòng chú thích tiếng Việt dưới mục "Thể loại": `"Lưu ý: Nguồn này chỉ lọc theo thể loại khi không nhập từ khóa"`.
   - Trên thanh chip bộ lọc đang bật dưới ô tìm kiếm, hiển thị chip nguồn hiện tại (dùng `SourceIds.SOURCE_LABELS[state.activeSource]`, nhấn vào mở `FilterBottomSheet` để đổi nhanh) và chỉ hiển thị chip `"Truyện nam"` khi `state.activeSource == SourceIds.WIKIDICH`.
3. Trong `ExploreViewModel.kt` & `ExploreScreen.kt`:
   - Thêm `val activeSource: String = SourceIds.DEFAULT_SINGLE_SOURCE` vào `ExploreState`.
   - Quan sát `repository.observeActiveSource().collectLatest` trong `init`: ở phát xạ đầu tiên (`previous == null`), chỉ cập nhật `_state.update { it.copy(activeSource = sourceId) }` mà không gọi lại `loadGenres()` / `loadFirstPage()` (vì `init` đã gọi); chỉ khi `previous != null && previous != sourceId` mới tự động tải lại `genres` của nguồn mới, reset `selectedGenre = null` nếu thể loại cũ không thuộc nguồn mới, và gọi `loadFirstPage()`.
   - Thêm `fun selectSource(sourceId: String)`.
   - Trên `ExploreScreen.kt`: thêm hàng chip chọn nguồn (`SourceIds.ORDERED_SOURCES` + `SourceIds.SOURCE_LABELS`) ngay phía trên hàng chip thể loại; tại `ExploreScreen.kt:121-152`, bọc chip `"Truyện nam"` và `VerticalDivider` trong điều kiện `if (state.activeSource == SourceIds.WIKIDICH)` để ẩn tùy chọn đặc thù Wikidich khi đang chọn `TàngThưViện` hoặc `SsTruyện` (giải quyết **F10**).

#### Bước 6: Cập nhật `DownloadRepository` & `StoryDetailViewModel` `[SRC: DownloadRepository.kt:50, 127-285, StoryDetailViewModel.kt:263-295]`
1. Trong `DownloadRepository.kt`:
   - Đặt `MAX_RETRY_ATTEMPTS = 3` (`line 50`) và dùng exponential backoff `val backoffDelay = retryDelayMs * (1L shl (attempt - 1))` (`line 156`) trong `fetchChapterWithRetry`.
   - Tách nguyên văn khối lưu DB trong vòng lặp chính (`lines 206-228`) thành hàm private dùng chung `persistDownloadedChapter(storyId, chapter, result)`.
   - Thu thập các chương tải thất bại trong vòng lặp batch vào `failedChapters`. Sau khi kết thúc các batch (nếu `consecutiveFailedBatches < MAX_CONSECUTIVE_FAILED_BATCHES`), chạy 1 lượt `retryPass` cho `failedChapters.sortedBy { it.chapterNumber }` tuân thủ delay rate-limit, kiểm tra lỗi nghiêm trọng, lưu DB qua `persistDownloadedChapter(storyId, chapter, result)` + cập nhật `downloaded++`, `errors = (errors - 1).coerceAtLeast(0)` và emit `DownloadProgress(downloaded, sortedChapters.size, chapter.title, errors)`.
2. Trong `StoryDetailViewModel.kt`:
   - Thêm `.sortedBy { it.chapterNumber }` vào `downloadChapters(count)` (`line 266`) và `downloadAll()` (`line 274`) trước khi gọi `performDownload(undownloaded)` (đồng bộ với `downloadRange` tại `lines 278-288` đã có `.sortedBy` ở `line 285`).

---

### 6. Yêu cầu Kiểm thử (Test Requirements & Test Contract)

#### Phân loại Thay đổi Test Hiện có theo Giao thức Test Failure Triage (`.agents/rules/02-quality.md`)
- **Loại C (Approved Spec Change)**:
  1. `WikidichScraperTest.kt` (`lines 211-282, 339-346`, giải quyết **R4**):
     - Trong test `` `getStoriesByGenre uses filter form qs=2 params with newest sort and male filter` `` (`WikidichScraperTest.kt:211-264`):
       - Dòng `239-244`: đổi slug `"gioi-giai-tri"` (tag đã gỡ khỏi 19 `Loại hình`) sang `"lam-su-nghiep"` và đổi assert `tag=5794f03ed7ced228f44191de` sang `genre=6785c24e23deea36d63efe8e` (giữ nguyên toàn bộ các assert `qs=2`, `gender=...`, `status=...`, `so=4&vo=1`, `assertFalse(contains("qs=1"))`).
       - Dòng `251-254`: đổi slug `"hien-dai"` (`age=` đã gỡ) sang `"quan-su"` và đổi assert `age=5794f03dd7ced228f441919d` sang `genre=6785b91d23deea36d63efe7f` (giữ nguyên `qs=2`, `gender=...`, `so=4&vo=1`, `start=20`).
       - Dòng `260-263` (`scraper.search("hogwarts", false)`): giữ nguyên không sửa vì URL vẫn chứa `"/tim-kiem?q=hogwarts&qs=1"`.
     - Trong test `` `getStoriesByGenre without male filter omits gender param` `` (`WikidichScraperTest.kt:267-282`):
       - Dòng `272` và `278`: đổi slug `"he-thong"` (tag đã gỡ) sang `"xay-dung"` và đổi assert `tag=5794f03dd7ced228f44191b6` sang `genre=5fd3abb623deea0a1a87e9b5` (giữ nguyên `/tim-kiem?qs=2`, `status=...`, `so=4&vo=1`, `assertFalse(urlStr.contains("gender="))`).
     - Trong test `` `getGenreList does not contain removed gender tags` `` (`WikidichScraperTest.kt:339-346`):
       - Dòng `343`: đổi `assertTrue(genres.size > 20)` thành `assertEquals(19, genres.size)` (chính xác 19 thể loại `Loại hình` chính thống).
  2. `SettingsViewModelTest.kt` (`lines 136-170`):
     - Trong test `` `enabledSources filters out legacy source IDs and toggleSource protects last active source` `` (`line 136`), cập nhật kỳ vọng khi `ENABLED_SOURCES = "truyencom,truyenfull"` theo đặc tả Đơn Nguồn Hoạt Động (Yêu cầu 2): `resolveSingleSource` fallback về `DEFAULT_SINGLE_SOURCE` (`"tangthuvien"`), nên `viewModel.state.first().enabledSources` bằng `setOf("tangthuvien")` (`size == 1`, thay vì `ALL_SOURCES` 3 phần tử như thiết kế đa nguồn cũ).
  3. `StoryRepositoryTest.kt` (`lines 908-990`, giải quyết **V5**):
     - Cập nhật 2 test `` `getCompletedStories forwards maleOnly to every active source` `` (`line 908`) và `` `getStoriesByGenre forwards maleOnly to every active source` `` (`line 947`) theo đặc tả Đơn Nguồn Hoạt Động: trong mỗi test, stub `every { dataStore.data } returns flowOf(preferencesOf(SettingsKeys.ENABLED_SOURCES to "wikidich"))` để `activeSources()` chọn đúng `wikiSource`, xác nhận `wikiSource` được gọi với `maleOnly = false` và nguồn không được chọn (`source`) không bị gọi (`coVerify(exactly = 0)`), đồng thời dùng slug `"tien-hiep"`.
  4. `DownloadRepositoryTest.kt` (`lines 222-264`):
     - Trong test `` `downloadChapters retries failed chapter and doubles inter-batch delay on batch error` `` (`line 222`), cập nhật mốc `testScheduler.currentTime` ở dòng `246` từ `750L` sang **`2_250L`** theo công thức tính thời gian ảo chính xác (giải quyết **F6, F8**):
       - **Batch 1 (`c1..c4`, chạy song song, `retryDelayMs = 250ms`)**: `c2` lỗi lần 1, chờ `250ms`, thành công ở lần 2 (`250ms`). `c3` lỗi cả 3 lần thử (`MAX_RETRY_ATTEMPTS = 3`), chờ exponential backoff `250ms` (giữa lần 1 & 2) + `500ms` (giữa lần 2 & 3) = `750ms`. Thời gian Batch 1 = `max(0, 250, 750, 0) = 750ms`.
       - **Inter-batch delay sau Batch 1**: vì `batchErrors = 1` (`c3` lỗi), `currentDelay` nhân đôi từ `250ms` lên `500ms`, chờ `delay(500ms)` → tổng cộng `750 + 500 = 1_250ms`.
       - **Batch 2 (`c5..c8`)**: cả 4 chương thành công ngay ở lần 1 (`0ms`), `batchErrors = 0` nên `currentDelay` reset về `BASE_BATCH_DELAY_MS = 250ms`. Không có inter-batch delay sau batch cuối → vẫn là `1_250ms`.
       - **Lượt `retryPass` cho `failedChapters = [c3]`**: chờ `retryInterChapterDelay = currentDelay = 250ms` trước khi tải (`1_250 + 250 = 1_500ms`), sau đó `fetchChapterWithRetry(source, c3, 250ms)` thử 3 lần với backoff `250ms + 500ms = 750ms` → tổng thời gian ảo = `1_500 + 750 = 2_250L`.
       - Giữ nguyên toàn bộ các `assertEquals` về `downloaded`, `errors` và `coVerify` của `chapterDao`.
  5. `SearchViewModelTest.kt` (`lines 250-280` và `303-321`, giải quyết **W1**):
     - Trong 2 test kiểm tra bộ lọc `"Truyện nam"` (`maleOnly = false`) khi không chọn thể loại (`selectedGenre = null`) và `chapterFilter = ChapterCountFilter.NONE`:
       1. `` `toggleCompletedFilter does not reset maleOnly when maleOnly is false` `` (`SearchViewModelTest.kt:250-280`, đặt `activeSourceFlow.value = SourceIds.WIKIDICH` ngay trước dòng `255` khi khởi tạo `SearchViewModel`)
       2. `` `browseByFilterOnly forwards maleOnly to repository` `` (`SearchViewModelTest.kt:303-321`, đặt `activeSourceFlow.value = SourceIds.WIKIDICH` ngay trước dòng `308` khi khởi tạo `SearchViewModel`)
     - Lý do: theo đặc tả Yêu cầu 2 (và thiết kế `hasAnyNonDefaultFilter` ở V4), bộ lọc `"Truyện nam"` (`maleOnly`) là tính năng đặc thù của Wikidich nên chỉ hiển thị và được tính vào `hasAnyNonDefaultFilter` khi `activeSource == SourceIds.WIKIDICH` (trên nguồn mặc định `"tangthuvien"`, tổ hợp `genre = null, chapterFilter = NONE, completedOnly = true, maleOnly = false` có `isFilterActive = false`). Việc đặt `activeSourceFlow.value = SourceIds.WIKIDICH` trước khi tạo `SearchViewModel` trong 2 test này đảm bảo kiểm thử đúng ngữ cảnh nguồn Wikidich và giữ nguyên 100% toàn bộ các lệnh `assertFalse`, `assertTrue`, `assertEquals` và `coVerify`. Các test `maleOnly` khác trong `SearchViewModelTest.kt` (`lines 228-248` có `ChapterCountFilter.OVER_200`, `lines 282-301` và `323-347` có `genre != null`) vẫn có `isFilterActive = true` nên giữ nguyên không sửa.
- **Loại B (Test Harness Setup — giải quyết F6, R8 & W1)**:
  - Trong `SearchViewModelTest.setUp()` (`SearchViewModelTest.kt:40-46`) và `ExploreViewModelTest.setUp()`: bổ sung `private val activeSourceFlow = MutableStateFlow(SourceIds.DEFAULT_SINGLE_SOURCE)`, stub `every { repository.observeActiveSource() } returns activeSourceFlow` và `coEvery { repository.setActiveSource(any()) } answers { activeSourceFlow.value = firstArg() }` (riêng 2 test kiểm thử `maleOnly = false` không kèm thể loại/số chương tại `SearchViewModelTest.kt:250-280, 303-321` đặt `activeSourceFlow.value = SourceIds.WIKIDICH` như mục 5 Loại C ở trên).
  - Trong `StoryRepositoryTest.kt`: các test hiện có dùng mock `source` có `sourceId = "truyencom"` (`line 50`) tiếp tục chạy nguyên vẹn nhờ nhánh fallback `.ifEmpty { sources.take(1).toSet() }` trong `activeSources()`.

#### Bảng Test Contract

| Test File | Test Case | Kịch bản & Input | Kỳ vọng Assert (`assertEquals` / `coVerify`) |
| :--- | :--- | :--- | :--- |
| `WikidichScraperTest.kt` | `getGenreList returns 19 canonical Loai hinh genres and GENRE_QUERY_MAP maps every slug to 24-hex genre ObjectId matching fixture links` | Gọi `getGenreList()`, đối chiếu `GENRE_QUERY_MAP`, đọc `fixtures/wikidich/story_detail.html:329` và `fixtures/wikidich/search_logged_in.html:312,324,330,372,378`, đồng thời gọi `getStoriesByGenre` với các slug đã gỡ (`"gioi-giai-tri"`, `"hien-dai"`, `"he-thong"`, `"oe"`) | `assertEquals(19, genres.size)`, khớp 1-1 với 19 khóa trong `GENRE_QUERY_MAP`, mọi giá trị khớp regex `^genre=[0-9a-f]{24}$`, link `a[href*="/tim-kiem?genre="]` trong `story_detail.html:329` (`Manga anime` → `genre=5794f03dd7ced228f44191a8`) và 5 link `Loại hình` trong `search_logged_in.html` (`Huyền huyễn`, `Võ hiệp`, `Tiên hiệp`, `Trinh thám`, `Kinh dị`) đều khớp với `GENRE_QUERY_MAP`; các slug đã gỡ trả về `emptyList()` mà không phát sinh HTTP request |
| `WikidichScraperTest.kt` | `search with 5 params throws LoginRequiredException when response contains login wall` | Enqueue `search_not_logged_in.html`, gọi `scraper.search("tiên", completedOnly = true, page = 1, maleOnly = true, genreSlug = "tien-hiep")` | `assertThrows<ScraperException.LoginRequiredException>` được ném ra từ `checkLoginRequired(doc)` |
| `WikidichScraperTest.kt` | `search builds server-side URL with qs 1 genre gender status keyword and start pagination` | Gọi `scraper.search("hệ thống", completedOnly = true, page = 2, maleOnly = true, genreSlug = "tien-hiep")` | Request path bằng `"/tim-kiem?q=h%E1%BB%87+th%E1%BB%91ng&qs=1&genre=5794f03dd7ced228f44191a4&gender=5794f03dd7ced228f4419196&status=5794f03dd7ced228f4419191&start=20"` |
| `WikidichScraperTest.kt` | `search filters out all 7 non-male Wikidich gender labels when maleOnly is true` | HTML kết quả có 8 truyện gồm `"Nam sinh"` và 7 nhãn `NON_MALE_WIKIDICH_GENDERS` (`Ngôn tình`, `Đam mỹ`, `Bách hợp`, `Nữ tần`, `Nữ tôn`, `Không CP`, `Đa nguyên`) | Khi `maleOnly = true` chỉ giữ lại 1 truyện `"Nam sinh"`; khi `maleOnly = false` trả đủ 8 truyện |
| `StoryRepositoryTest.kt` | `observeActiveSource and setActiveSource persist and emit single active source` | Dựng `val prefsFlow = MutableStateFlow<Preferences>(emptyPreferences())` với `every { dataStore.data } returns prefsFlow` và `coEvery { dataStore.updateData(any()) } coAnswers { val transform = firstArg<suspend (Preferences) -> Preferences>(); prefsFlow.value = transform(prefsFlow.value); prefsFlow.value }`, gọi `setActiveSource("wikidich")` rồi `setActiveSource("sstruyen")` | `observeActiveSource()` phát tuần tự `"tangthuvien"` → `"wikidich"` → `"sstruyen"`; `activeSources()` chỉ gọi đúng 1 scraper đang chọn |
| `SettingsViewModelTest.kt` | `resolveSingleSource normalizes null blank multi-source CSV legacy IDs and whitespace to single active source` | Kiểm tra `SourceIds.resolveSingleSource` với `null`, `""`, `"  "`, `"truyenfull,truyencom"`, `" sstruyen , wikidich "`, `"wikidich,tangthuvien"` | Trả về lần lượt `"tangthuvien"`, `"tangthuvien"`, `"tangthuvien"`, `"tangthuvien"`, `"sstruyen"`, `"wikidich"` |
| `SettingsViewModelTest.kt` | `selectSource switches exclusively to selected source and ignores invalid sourceId` | Gọi `viewModel.selectSource("wikidich")`, sau đó `viewModel.selectSource("invalid")` | `state.enabledSources` bằng `setOf("wikidich")` (`size == 1`) |
| `SearchViewModelTest.kt` | `selectSource updates activeSource reloads genres and resets incompatible selectedGenre` | Khi `activeSourceFlow.value = "wikidich"`, đang chọn `Genre("Làm sự nghiệp", "lam-su-nghiep")` và không có từ khóa (đi vào nhánh `clearFilter()`), gọi `viewModel.selectSource("sstruyen")` (khác `DEFAULT_SINGLE_SOURCE`) | `viewModel.state.value.activeSource == "sstruyen"`, `viewModel.genres.value` cập nhật theo SsTruyện, `viewModel.state.value.selectedGenre == null` |
| `SearchViewModelTest.kt` | `search with keyword and genre on wikidich forwards parameters to repository and loadMoreFilterResults fetches next page` | Khi `activeSourceFlow.value = "wikidich"`, tìm `"tiên"` kèm `selectedGenre = Genre("Tiên hiệp", "tien-hiep")` (`completedOnly = true` mặc định), sau đó gọi `loadMoreFilterResults()` | Trang 1 gọi `repository.searchStories("tiên", true, 1, true, "tien-hiep")`, `canLoadMore == true`; gọi `loadMoreFilterResults()` sẽ gọi `repository.searchStories("tiên", true, 2, true, "tien-hiep")` và nối kết quả |
| `SearchViewModelTest.kt` | `loadMoreFilterResults in browseByFilterOnly mode fetches next page of completedStories` | Khi đang ở chế độ `browseByFilterOnly` (`activeSource = "wikidich"`, `selectedGenre = null`, `maleOnly = false`, `canLoadMore = true`), gọi `loadMoreFilterResults()` | Gọi `repository.getCompletedStories(page = 2, maleOnly = false)` và nối kết quả trang 2 vào `state.value.results` (`size == 2`, `currentPage == 2`) |
| `ExploreViewModelTest.kt` | `selectSource syncs with repository reloads genres and resets incompatible selectedGenre` | Khi `activeSourceFlow.value = "wikidich"`, đang chọn thể loại riêng của `wikidich`, gọi `viewModel.selectSource("sstruyen")` | `state.activeSource == "sstruyen"`, `state.selectedGenre == null`, `repository.getCompletedStories` được gọi lại |
| `DownloadRepositoryTest.kt` | `downloadChapters retryPass recovers transiently failed middle chapter without skipping` | Tải 5 chương (1..5): Chương 2 lỗi ở cả 3 lần thử trong batch 1 nhưng thành công ở lượt `retryPass` cuối | `progress.last().current == 5`, `progress.last().errors == 0`, `coVerify(exactly = 1) { chapterDao.saveChapterContent("s1", 2, any(), any(), any(), any()) }` |
| `DownloadRepositoryTest.kt` | `downloadChapters retryPass on Wikidich waits wikidichDelayProvider and immediately throws HttpException on 429 without requesting remaining failed chapters` | Tải 2 chương Wikidich (`c1`, `c2`) đều lỗi `IOException` ở vòng chính; sang `retryPass`, `c1` trả về `ScraperException.HttpException(429, "Too Many Requests")` | `testScheduler` tiến thêm đúng khoảng `wikidichDelayProvider()` trước `c1` trong `retryPass`, ném `ScraperException.HttpException`, và `c2` không được gọi trong `retryPass` (`coVerify(exactly = 3) { wikiSource.getChapterContentWithTitle(c2.url) }`) |
| `StoryDetailViewModelTest.kt` | `downloadChapters and downloadAll sort undownloaded chapters ascending by chapterNumber` | Danh sách chương có Chương 5 (`isDownloaded = false`), Chương 1 (`isDownloaded = true`), Chương 2 (`isDownloaded = false`), Chương 3 (`isDownloaded = true`); dùng `mockkObject(DownloadService.Companion)` | Gọi `downloadChapters(1)` kích hoạt `verify(exactly = 1) { DownloadService.startDownload(any(), eq("story-1"), any(), match { it.contentEquals(intArrayOf(2)) }) }` (không nhảy qua Chương 2 để tải Chương 5 trước) |

---

### 7. Kế hoạch Xác minh (Verification Plan)

1. **Tier 1 (Sau mỗi cụm chỉnh sửa)**:
   - `.\gradlew.bat compileDebugKotlin`
2. **Chạy Unit Tests mục tiêu**:
   - `.\gradlew.bat testDebugUnitTest --tests "com.personal.apptruyen.data.remote.WikidichScraperTest" --tests "com.personal.apptruyen.data.repository.StoryRepositoryTest" --tests "com.personal.apptruyen.data.repository.DownloadRepositoryTest" --tests "com.personal.apptruyen.ui.settings.SettingsViewModelTest" --tests "com.personal.apptruyen.ui.search.SearchViewModelTest" --tests "com.personal.apptruyen.ui.explore.ExploreViewModelTest" --tests "com.personal.apptruyen.ui.detail.StoryDetailViewModelTest"`
3. **Tier 2 (Trước khi hoàn tất)**:
   - `.\gradlew.bat ktlintCheck; .\gradlew.bat testDebugUnitTest; .\gradlew.bat assembleDebug`
4. **Wiring Probes & Negative Probes**:
   - **Wiring Probe 1 (19 thể loại Wikidich)**: Kiểm tra `WikidichScraper.kt` có đúng 19 mục trong `getGenreList()` và 19 khóa khớp 1-1 trong `GENRE_QUERY_MAP`, toàn bộ 19 giá trị trong `GENRE_QUERY_MAP` đều khớp regex `^genre=[0-9a-f]{24}$`.
   - **Negative Probe 1 (Không còn tag/bg/age/official/ending trong `GENRE_QUERY_MAP`)**: `Select-String -Path "app/src/main/java/com/personal/apptruyen/data/remote/WikidichScraper.kt" -Pattern '"(tag|bg|age|official|ending)='` phải trả về `0` kết quả.
   - **Wiring Probe 2 (Đồng bộ nguồn 2 chiều)**: Kiểm tra cả `SettingsViewModel`, `SearchViewModel`, và `ExploreViewModel` đều đọc/ghi thông qua `SettingsKeys.ENABLED_SOURCES` / `observeActiveSource()` / `setActiveSource()`.

---

### 8. Rủi ro & Biện pháp giảm thiểu

| Rủi ro | Mức độ | Biện pháp kỹ thuật trong kế hoạch |
| :--- | :---: | :--- |
| Dữ liệu `SettingsKeys.ENABLED_SOURCES` cũ trên máy người dùng đang lưu chuỗi nhiều nguồn (`"tangthuvien,sstruyen,wikidich"`) hoặc nguồn đã xóa (`"truyenfull"`) | Trung bình | Hàm `SourceIds.resolveSingleSource(rawCsv)` và `migrateWikidichEnabledSourcesIfNeeded()` (đọc `prefs[ENABLED_SOURCES]` bên trong `edit`) tự động chuẩn hóa về nguồn hợp lệ đầu tiên trong chuỗi hoặc fallback về `DEFAULT_SINGLE_SOURCE` (`"tangthuvien"`), đảm bảo không bao giờ phát sinh trạng thái 0 nguồn hoặc >1 nguồn |
| Người dùng đổi nguồn sang `Tàng Thư Viện` hoặc `SsTruyện` nhưng vẫn muốn đọc/tải tiếp truyện `Wikidich` đã lưu trong Tủ truyện | Cao | `StoryRepository.getStoryDetail`, `getChapterList`, `getFirstPageChapterList`, `getChapterContent` và `DownloadRepository.downloadChapters` định tuyến scraper qua `sourceFor(url)` (tra cứu trên toàn bộ `sources`), hoàn toàn độc lập với `activeSources()` dùng cho Tìm kiếm/Khám phá |
| Khi chỉ có 1 nguồn hoạt động, nếu nguồn đó bị mở Circuit Breaker (`CircuitOpenException`), hệ thống không tự động lấy kết quả từ nguồn không được chọn (`StoryRepository.kt:135-139, 328-331`) | Thấp | Giữ nguyên `.ifEmpty { activeSources() }` để thử lại đúng nguồn người dùng đang chọn; nếu nguồn vẫn lỗi, hiển thị thông báo lỗi tiếng Việt kèm nút Thử lại và người dùng có thể chủ động đổi sang nguồn khác ngay trên chip nguồn của `SearchScreen` / `ExploreScreen` (giải quyết **F9**) |
| Lượt `retryPass` cuối trong `DownloadRepository` kéo dài vô ích nếu toàn bộ nguồn đang bị sập mạng hoặc bị chặn | Trung bình | Chỉ chạy `retryPass` khi vòng lặp batch chính không bị dừng bởi `consecutiveFailedBatches >= MAX_CONSECUTIVE_FAILED_BATCHES` (3 batch lỗi liên tiếp), tuân thủ delay rate-limit, và dừng ngay nếu gặp `LoginRequiredException`, `CircuitOpenException`, hoặc HTTP `403/429/503` trên Wikidich |
| Kích thước file `SearchScreen.kt` và `SettingsScreen.kt` đang lớn | Thấp | Giữ Minimal Diffs: thay thế trực tiếp khối `Switch` trong `SettingsScreen.kt` bằng `RadioButton` và thêm gọn hàng `FilterChip` chọn nguồn trong `FilterBottomSheet` của `SearchScreen.kt` |

---

### 9. Cập nhật Documentation (Theo `.agents/rules/06-docs.md`)

| File tài liệu | Nội dung cập nhật bắt buộc sau khi triển khai |
| :--- | :--- |
| `docs/scraping.md` | Cập nhật bảng URL tìm kiếm từ khóa của Wikidich (`/tim-kiem?q=...&qs=1&genre=...&gender=...&status=...&start=...`), danh sách 19 thể loại `Loại hình` chính thống (kèm giải thích 3 ObjectId `...1a2`, `...1ac`, `...1c0` và lý do loại `Võng du`) và 7 nhãn phân loại độc giả `NON_MALE_WIKIDICH_GENDERS` |
| `docs/architecture.md` | Cập nhật quy tắc Đơn Nguồn Hoạt Động (`observeActiveSource` / `setActiveSource`), hành vi Circuit Breaker khi chỉ có 1 nguồn hoạt động (không tự nhảy sang nguồn không chọn), và cơ chế `retryPass` trong `DownloadRepository` |
| `docs/progress.md` | Ghi nhận hoàn thành sửa lỗi tìm kiếm & 19 thể loại Wikidich, bộ lọc đơn nguồn đồng bộ 2 chiều và chống nhảy cóc chương khi tải |
| `docs/changelog.md` | Thêm mục dưới `## [Unreleased]` (`### Sửa đổi` & `### Fix bug`) cho 3 yêu cầu, bao gồm ghi chú rõ việc chuẩn hóa `WikidichScraper.getGenreList()` về đúng 19 thể loại `Loại hình` chính thống và gỡ bỏ `Võng du` (`vong-du`) cùng các nhãn `tag=`/`age=`/`official=`/`ending=` khỏi danh sách thể loại UI (giải quyết **V2**) |
| `docs/lessons-learned.md` | Ghi nhận bài học kinh nghiệm (Root Cause / Cách sửa / Quy tắc phòng ngừa) cho lỗi phớt lờ thể loại khi tìm kiếm từ khóa trên Wikidich và lỗi bỏ sót chương khi mạng chập chờn |
| `docs/plans/README.md` | Thêm plan `2026-10-28-single-source-filter-wikidich-search-and-download-retry-implementation-plan.md` vào bảng theo dõi |

---

### 10. Checklist tự kiểm tra của Planner

- [x] Tuân thủ Clean Architecture (Screen → ViewModel → Repository → Scraper/DAO)
- [x] UI 100% tiếng Việt
- [x] Không dùng công nghệ bị cấm (XML, Koin, RxJava, Retrofit cho scraping, SQLite thô)
- [x] Không dùng `!!`, `GlobalScope`, `runBlocking` ở main source, `Thread.sleep`
- [x] Mọi `catch` đều xử lý lỗi đúng chuẩn và rethrow `CancellationException`
- [x] Có kế hoạch Unit Test đầy đủ (happy path + error/edge case) và khai báo rõ Type C
- [x] Đã liệt kê các file docs cần cập nhật theo `06-docs.md`
- [x] Đã kiểm tra kích thước file ước tính nằm trong ngưỡng cho phép với Minimal Diffs

---

## Audit History

<!-- @reviewer appends audit runs below this line. Do not edit manually above this line once approved. -->

### Audit Run 1

- **Reviewer**: Claude Code CLI (Claude Opus 5.5, `claude-opus-5-5`)
- **Ngày**: 2026-10-28
- **Đối tượng**: Technical Plan Revision 1

#### Kiểm Chứng Lỗi Cũ

Không có Audit Run trước — đây là lượt thẩm định đầu tiên, không có lỗi cũ cần kiểm lại.

#### Phát Hiện Mới (Fresh Findings & Regression Check)

Ghi chú: working tree đang có nhiều file `M` chưa commit (`[CMD] git status --short`, Exit 0), nên mọi đối chiếu dưới đây dựa trên nội dung file hiện tại trên đĩa, không dựa trên HEAD.

**[MAJOR] F1 — `WikidichScraper.search(...)` mới làm mất kiểm tra đăng nhập và gọi hàm không tồn tại (Regression S5/S6)**
- `[SRC] app/src/main/java/com/personal/apptruyen/data/remote/WikidichScraper.kt:136-137`: `search` hiện tại gọi `checkLoginRequired(doc)` ngay sau `fetchDocumentWithRetry(url)`. Sketch §3.3 của plan (`val doc = fetchDocumentWithRetry(url)` → `parseStoryList(doc)`) bỏ dòng này.
- `[SRC] BaseScraper.kt:77,86`: `fetchDocumentWithRetry` không tự phát hiện trang "Đăng nhập để xem nội dung"; chỉ `checkLoginRequired` (`WikidichScraper.kt:564-573`) ném `LoginRequiredException`.
- Hệ quả: phiên Wikidich hết hạn → trang login được parse thành 0 kết quả, `StoryRepository.searchStories` không bao giờ đi vào nhánh `markLoginRequired` (`StoryRepository.kt:359-361`), người dùng chỉ thấy "không có kết quả" mà không có lời nhắc đăng nhập.
- Sketch còn gọi `shouldEnrichListingChapters()` và `enrichTopStoriesWithChapters(filtered)` — hai hàm này không tồn tại; hàm thật là `enrichWithChapterCounts(stories, maxItems)` (`WikidichScraper.kt:503-562`). Plan cũng nhắc hàm `filterCompleted(stories)` không tồn tại (logic `.ifEmpty { stories }` nằm inline ở `WikidichScraper.kt:139-144`).
- Yêu cầu: giữ `checkLoginRequired(doc)` trong `search` (và trong `getStoriesByGenre`/`getCompletedStories` sau khi đổi `qs=1`), dùng đúng `enrichWithChapterCounts`, và thêm test `search` trả về trang login → ném `LoginRequiredException`.

**[MAJOR] F2 — 19 ObjectId `Loại hình` chưa có bằng chứng HTML và xung đột với ID đang dùng trong code**
- `.agents/rules/04-scraping.md` (mục Selector & URL) bắt buộc xem HTML thực tế và ghi ngày khảo sát vào plan. Plan không có ngày khảo sát hay trích HTML nào cho danh sách 19 mục.
- `[URL]` WebFetch `https://wikicv.org/tim-kiem` (2026-10-28): trang trả về tường "Đăng nhập để xem nội dung", không lộ nhóm "Loại hình" → reviewer không xác minh được → `UNVERIFIED`.
- Xung đột nội tại với code hiện tại (ObjectId là duy nhất, không thể vừa là `ending` vừa là `genre`):
  - `5794f03dd7ced228f44191a2`: code hiện tại dùng làm `ending=` (OE) tại `WikidichScraper.kt:122`; plan dùng làm `genre=` cho "Tình cảm".
  - `5794f03dd7ced228f44191ac`: code hiện tại là `tag=` (mạt thế) tại `WikidichScraper.kt:94`; plan dùng `genre=`.
  - `5794f03dd7ced228f44191c0`: code hiện tại là `tag=` (điền văn/làm ruộng) tại `WikidichScraper.kt:97-98`; plan dùng `genre=`.
- Nếu tham số sai namespace, server sẽ bỏ qua hoặc trả rỗng, và test MockWebServer chỉ khẳng định chuỗi URL do chính plan đặt ra (mock vòng tròn), không phát hiện được.
- Yêu cầu: đính kèm trích đoạn HTML (đã đăng nhập) của khối "Loại hình" (các `input`/`a` có `genre=`) kèm ngày khảo sát, lưu thành fixture cho test `getGenreList`/`GENRE_QUERY_MAP`, và giải thích 3 ID xung đột ở trên.

**[MAJOR] F3 — Lượt `retryPass` trong `DownloadRepository` sai giả định về kiểu trả về, tham chiếu exception không tồn tại và phá nhịp chống rate-limit Wikidich**
- `[SRC] ScraperException.kt:13-63`: chỉ có `NetworkException`, `HttpException`, `ParseException`, `CacheException`, `CircuitOpenException`, `LoginRequiredException`, `UnknownException`. `CloudflareBlockedException` mà plan nhắc ở §1, §3.5, §8 không tồn tại.
- `[SRC] DownloadRepository.kt:127-161`: `fetchChapterWithRetry` trả `ChapterFetchOutcome(result, error)` và **nuốt** `LoginRequiredException`, `CircuitOpenException`, HTTP 403/429/503 của Wikidich vào `error` (dòng 146-153), không ném ra. Plan §3.5 viết `if (retryResult != null)` (sai kiểu) và "nếu phát sinh lỗi nghiêm trọng thì ném ra ngay" — điều này không tự xảy ra; vòng retryPass phải kiểm `outcome.error` giống khối `fatalError` ở `DownloadRepository.kt:234-251`. Plan cũng dùng biến `baseDelay` không được định nghĩa.
- `[SRC] DownloadRepository.kt:276-283`: giữa các batch Wikidich luôn chờ ngẫu nhiên 15–20 s (`wikidichDelayProvider`). Plan chỉ `delay(currentDelay)` (250–3000 ms) **một lần** trước retryPass rồi duyệt liên tiếp `failedChapters` không có delay giữa các chương → với Wikidich sẽ bắn liên tục nhiều request sau khi vừa gặp lỗi, vi phạm `04-scraping.md` (Rate limit) và dễ kích hoạt 429.
- `[SRC] StoryRepository.kt:826-834`: `DownloadProgress` chỉ có 4 tham số constructor, `processed` là thuộc tính tính toán. Plan emit `DownloadProgress(downloaded, sortedChapters.size, chapter.title, errors, processed)` (5 tham số) sẽ không biên dịch.
- Yêu cầu: viết lại §3.5 với (a) kiểm `outcome.error` để ném `fatalError` theo đúng tập điều kiện hiện có, (b) áp dụng `wikidichDelayProvider()` trước mỗi chương retry khi `isWikidich`, (c) chữ ký `DownloadProgress` đúng, (d) test Wikidich: retryPass gặp 429 thì dừng và ném `HttpException`, không gọi thêm chương.

**[MAJOR] F4 — Phân trang tìm kiếm theo từ khóa không thể chạy với code UI/VM hiện tại**
- `[SRC] SearchViewModel.kt:441-447`: không có hàm `loadMore()`; hàm thật là `loadMoreFilterResults()`, và nó `return` ngay khi `query` khác rỗng (dòng 445), khi `!isFilterActive` (dòng 443) hoặc khi `selectedGenre == null` (dòng 447).
- `[SRC] SearchScreen.kt:84-90`: trigger tải thêm yêu cầu `state.isFilterActive`; một lượt tìm từ khóa với bộ lọc mặc định có `isFilterActive == false` nên không bao giờ gọi tải thêm.
- Plan Bước 5 và Test Contract (`loadMore()` gọi trang 2) không nêu việc đổi tên/viết lại `loadMoreFilterResults`, không nêu sửa điều kiện ở `SearchScreen.kt:84`, không nêu nhánh "từ khóa + không thể loại" dùng `searchStories(page = nextPage)`.

**[MAJOR] F5 — Bỏ lọc thể loại client-side làm thể loại bị bỏ qua âm thầm trên SsTruyện; chẩn đoán gốc của plan không khớp code hiện tại**
- `[SRC] SearchViewModel.kt:348-354`: `matchesGenre` hiện đã cho qua mọi truyện Wikidich (`story.sourceId == "wikidich"`, dòng 351), nên khẳng định ở §1 "chọn thể loại kèm từ khóa luôn trả về 0 kết quả" không đúng với code hiện tại (trên Wikidich, thể loại đang bị **bỏ qua**, không phải trả 0).
- `[SRC] SsTruyenScraper.kt:257-279`: `search` chỉ có `keyword`. Plan Bước 2.3 chỉ thêm `&page=` cho SsTruyện, không thêm thể loại. Khi Bước 5 bỏ khối lọc genre trong `applyLocalFilters`, tìm "từ khóa + thể loại" trên SsTruyện sẽ trả về kết quả không lọc thể loại mà UI vẫn hiển thị chip thể loại đang bật.
- `[SRC] TangThuVienScraper.kt:165,397`: search dùng `/truyen?keyword=...`, browse dùng `genre=$genreSlug` (slug, không phải ID). Plan ghi `&genre=$genreId` mà không có bảng ánh xạ; tổ hợp `keyword + genre + page` trên tangthuvien.org chưa được khảo sát (WebFetch `https://tangthuvien.org/truyen?keyword=tien&genre=tien-hiep&page=2` trả HTTP 502 → `UNVERIFIED`).
- Yêu cầu: với nguồn không hỗ trợ lọc thể loại server-side, giữ lọc client-side theo nguồn (hoặc ẩn bộ chọn thể loại khi có từ khóa), và có khảo sát URL TTV/SsTruyện kèm ngày.

**[MAJOR] F6 — Test Contract và phân loại Type C chưa khớp code/test thật**
- `[SRC] SearchViewModel.kt:90-91`: `genres` nằm trong `_genres: MutableStateFlow` riêng, `SearchState` (dòng 48-63) không có trường `genres`/`activeSource`. Test Contract khẳng định `state.genres` — plan phải nêu rõ thêm trường hay dùng `viewModel.genres`.
- `[SRC] SearchViewModelTest.kt:40`, `ExploreViewModelTest.kt:35`: `repository = mockk(relaxed = true)`. Với relaxed mock, `observeActiveSource()` trả một `Flow` giả không phát phần tử nào. Plan không nêu `init` có giữ `loadGenres()`/`loadFirstPage()` (`ExploreViewModel.kt:59-62`, `SearchViewModel.kt:119-121`) hay chuyển hẳn vào collector. Nếu chuyển, các test hiện có kiểm genres/stories sau init sẽ hỏng mà không có trong danh sách Type C; nếu giữ, lần phát đầu tiên sẽ gọi `loadFirstPage()` lần hai (thêm một request vào Wikidich lúc mở màn hình). Plan phải chọn một phương án và bổ sung `every { repository.observeActiveSource() } returns flowOf(...)` vào `setUp`.
- `[SRC] SettingsViewModelTest.kt:136-170`: tên test thật là `enabledSources filters out legacy source IDs and toggleSource protects last active source` (một test, gồm cả assert `ALL_SOURCES` ở dòng 163), không khớp hai tên test plan liệt kê ở Type C mục 2.
- `[SRC] DownloadRepositoryTest.kt:246`: assert `750L`. Plan chỉ nói "cập nhật cho khớp" mà không nêu giá trị mới; theo thiết kế (c3 lỗi vĩnh viễn → retryPass chạy thêm 3 lần thử) giá trị sẽ thay đổi đáng kể. Cần ghi giá trị kỳ vọng mới và lý do, tránh việc chỉnh số cho test pass.
- Thiếu test cho `SourceIds.resolveSingleSource` (null, rỗng, CSV nhiều nguồn, chỉ ID legacy, khoảng trắng) — đây là logic mới trong `data/local/`.

**[MINOR] F7 — Migration DataStore không chạy lại cho người dùng đã migrate**
- `[SRC] SettingsDataStore.kt:163-164`: `migrateWikidichEnabledSourcesIfNeeded` return sớm khi `IS_WIKIDICH_MIGRATED == true`, nên giá trị `"tangthuvien,sstruyen,wikidich"` của người dùng cũ không bao giờ được ghi lại thành giá trị đơn như §3.2 mô tả. `[SRC] SettingsDataStore.kt:108-110`: restore backup cũng ghi CSV thô. Đọc qua `resolveSingleSource` vẫn cho ra 1 nguồn nên không gây lỗi chức năng, nhưng mô tả trong plan không đúng; ngoài ra sketch `activeSources()` bỏ lời gọi migrate và khối try/catch hiện có (`StoryRepository.kt:76-86`), còn `observeActiveSource()` không có `.catch` cho `IOException` của DataStore.

**[MINOR] F8 — Số dòng và tên hàm tham chiếu lệch so với source**
- `getGenreList` ở `WikidichScraper.kt:370-407` (plan ghi 64-93); `GENRE_QUERY_MAP` ở `WikidichScraper.kt:81-125` (plan ghi 125-154); `SearchViewModel.search()` ở dòng 138-177 (plan ghi 211-246); `applyLocalFilters` ở dòng 341-372 (plan ghi 422-450).
- `StoryDetailViewModel.kt:263-276` gọi `performDownload(...)`, plan viết `executeDownload(...)`. `downloadRange` đã có `.sortedBy` (`StoryDetailViewModel.kt:285`).
- `[SRC] AppDatabase.kt:22`: `version = 9`, plan ghi "giữ nguyên version 8".

**[MINOR] F9 — Đơn nguồn làm mất fallback giữa các nguồn khi circuit breaker mở**
- `[SRC] StoryRepository.kt:136-139, 329-331`: `.filter { circuit != OPEN }.ifEmpty { activeSources() }`. Với 1 nguồn, khi nguồn mở mạch thì không còn nguồn thay thế; `04-scraping.md` ghi "Repository fallback sang nguồn khác". Đây là thay đổi hành vi có chủ đích theo yêu cầu User nhưng cần nêu trong `docs/architecture.md` và hiển thị thông báo tiếng Việt gợi ý đổi nguồn.

**[MINOR] F10 — Hành vi `maleOnly` trên Explore khi nguồn không phải Wikidich chưa được mô tả**
- §1 Yêu cầu 2.4 nói "Truyện nam" chỉ hiển thị/áp dụng cho Wikidich, nhưng Bước 5.3 không nêu việc ẩn toggle trên `ExploreScreen` khi nguồn khác Wikidich.

**[MINOR] F11 — Đổi nguồn liên tiếp: sketch dùng `collect` tuần tự, không có `sourceSwitchJob`**
- §4 Trục 1 nói hủy job cũ khi có phát xạ mới, nhưng sketch §3.4 dùng `collect` và không khai báo `sourceSwitchJob`. `getGenreList()` đọc DataStore tại thời điểm gọi (`StoryRepository.kt:275` → `activeSources()`), nên trạng thái cuối vẫn hội tụ (xem timeline T-A), nhưng nên dùng `collectLatest` để tránh tải genres/danh sách thừa.

#### Mental Simulation Matrix (S1–S6 + Domain)

| Kịch bản | Kết quả | Bằng chứng | Chi tiết |
| :--- | :---: | :--- | :--- |
| S1 Cold-start & Build | FAIL | `[CMD]` `cmd.exe /d /c gradlew.bat compileDebugKotlin` Exit 0 (baseline); `[SRC]` `StoryRepository.kt:826-834`, `ScraperException.kt:13-63`, `WikidichScraper.kt:503` | Baseline biên dịch được. Không thêm dependency. Nhưng các snippet của plan tham chiếu ký hiệu không tồn tại (`CloudflareBlockedException`, `enrichTopStoriesWithChapters`, `shouldEnrichListingChapters`, `executeDownload`, `baseDelay`, `DownloadProgress` 5 tham số) — F1, F3, F8. |
| S2 Lifecycle & Teardown | PASS | `[SRC]` `DownloadRepository.kt:140,183,202` (`ensureActive`), `DownloadRepository.kt:144-145` (rethrow `CancellationException`); `ExploreViewModel.kt:88-89` (`loadJob?.cancel()`) | Vòng retryPass sẽ chạy trong cùng `flow {}` nên hủy từ `DownloadService` dừng được qua `ensureActive`/`delay`. Collector `observeActiveSource` chạy trong `viewModelScope`. |
| S3 Database & Temporal (Room + DataStore) | PASS | `[SRC]` `AppDatabase.kt:22` (v9, không đổi), `SettingsDataStore.kt:70` (key `enabled_sources` giữ nguyên tên), `SettingsDataStore.kt:108-110` | Không đổi schema Room. Key DataStore giữ nguyên, chỉ đổi ngữ nghĩa giá trị; `resolveSingleSource` đọc được cả CSV cũ và giá trị restore từ backup (xem timeline T-C). Sai lệch mô tả migration ghi ở F7 (MINOR). |
| S4 Cross-Task State Flow | FAIL | `[SRC]` `SearchViewModel.kt:48-63, 90-91, 441-447`; `SearchScreen.kt:84-90`; `SearchViewModelTest.kt:40` | `loadMore()` không tồn tại, trigger UI phụ thuộc `isFilterActive`, `SearchState` không có `genres`; ảnh hưởng tới mock relaxed chưa được xử lý — F4, F6. |
| S5 Security & Privacy Boundary | FAIL | `[SRC]` `WikidichScraper.kt:136-137, 564-573`; `BaseScraper.kt:77,86`; `StoryRepository.kt:359-361` | Trang đăng nhập của Wikidich sẽ bị parse thành danh sách rỗng trong `search` mới (bỏ `checkLoginRequired`) — F1. Không đụng tới cookie/`CookieVault`/Manifest. |
| S6 External Resilience | FAIL | `[SRC]` `DownloadRepository.kt:146-153, 276-283`; `[URL]` WebFetch wikicv.org/tim-kiem (tường đăng nhập), tangthuvien.org (HTTP 502) | retryPass bắn liên tiếp vào Wikidich (F3); ObjectId và tham số TTV chưa xác minh trên HTML thật (F2, F5). |
| Domain: Gộp nguồn / Tủ truyện | PASS | `[SRC]` `StoryRepository.kt:95-97, 590, 614, 625, 693`; `DownloadRepository.kt:71-73, 175-177` | Chi tiết truyện, danh sách chương, nội dung chương và tải offline định tuyến theo `sourceFor(url)` trên toàn bộ `sources`, độc lập với `activeSources()` → đổi nguồn không ảnh hưởng truyện đã lưu. |
| Domain: Tải offline – tiến độ | PASS | `[SRC]` `DownloadService.kt:293-304` | Service dùng `lastProgress` để tính `Completed`, nên lần emit sau retryPass (`downloaded` tăng, `errors` giảm) được phản ánh đúng trong thông báo hoàn tất. |
| Domain: UI tiếng Việt | PASS | `[SRC]` `SettingsScreen.kt:405-418` (vị trí thay thế) | Nhãn mới đề xuất ("Nguồn truyện", "Chọn 1 nguồn hoạt động…") là tiếng Việt; dùng `SourceIds.SOURCE_LABELS` (`SettingsDataStore.kt:29-34`). Lưu ý nhãn hiện tại là "TàngThưViện" (không dấu cách) khác "Tàng Thư Viện" trong plan — nên dùng chung một nguồn nhãn. |

#### Adversarial Timelines

```
Cơ chế T-A: Đổi nguồn liên tiếp (SearchViewModel/ExploreViewModel ↔ DataStore)
T1: Người dùng chọn sstruyen trên ExploreScreen → setActiveSource("sstruyen") ghi DataStore
T2: 30 ms sau chọn wikidich trên SearchScreen → setActiveSource("wikidich"); DataStore có thể gộp phát xạ (conflate)
T3: Collector của Explore nhận "sstruyen", gọi getGenreList() → activeSources() đọc DataStore lúc này = "wikidich" → trả genres Wikidich, gán cho state đang ghi activeSource="sstruyen"; sau đó nhận "wikidich" và tải lại (cache hit, StoryRepository.kt:279-281)
Kết cục: trạng thái cuối hội tụ về wikidich, có 1 lượt tải thừa; loadFirstPage cũ bị hủy bởi loadJob?.cancel() (ExploreViewModel.kt:88) → AN TOÀN (MINOR F11, khuyến nghị collectLatest)
```

```
Cơ chế T-B: retryPass Wikidich sau lỗi 429 (DownloadRepository)
T1: Tải 10 chương Wikidich, batch size 1, chờ 15–20 s giữa batch (DownloadRepository.kt:276-280)
T2: Chương 3 và 7 lỗi NetworkException; chương cuối tải xong → không có delay sau batch cuối
T3: Theo plan: delay(currentDelay ≤ 3000 ms) rồi fetch chương 3 → gặp 429 → fetchChapterWithRetry trả outcome.error (không ném, dòng 151-152) → plan không kiểm error → ngay lập tức fetch chương 7
Kết cục: 2 request sát nhau sau 429, không có backoff 15–20 s, không ném fatal → LỖI (MAJOR F3)
```

```
Cơ chế T-C: Dữ liệu ENABLED_SOURCES cũ / restore backup (TOCTOU DataStore)
T1: Người dùng cũ có IS_WIKIDICH_MIGRATED=true, ENABLED_SOURCES="tangthuvien,sstruyen,wikidich"
T2: Mở app bản mới → migrate return sớm (SettingsDataStore.kt:163-164), giá trị CSV giữ nguyên
T3: activeSources()/observeActiveSource()/SettingsViewModel đều đọc qua resolveSingleSource → "tangthuvien"; người dùng restore backup cũ (SettingsDataStore.kt:108-110) ghi lại CSV → vẫn resolve "tangthuvien"
Kết cục: luôn đúng 1 nguồn hiển thị; giá trị lưu trữ chỉ được chuẩn hóa khi người dùng chọn nguồn → AN TOÀN (MINOR F7 về mô tả plan)
```

```
Cơ chế T-D: Phiên Wikidich hết hạn giữa lúc tìm kiếm (S5 TOCTOU)
T1: authRepository coi Wikidich đã đăng nhập; người dùng tìm "hệ thống"
T2: Cookie hết hạn phía server; wikicv.org trả trang chứa "Đăng nhập để xem nội dung"
T3: search mới theo plan: fetchDocumentWithRetry → parseStoryList (không có .book-item) → [] → StoryRepository gọi clearLoginRequired (StoryRepository.kt:345-347)
Kết cục: UI báo "không có kết quả", lời nhắc đăng nhập bị xóa thay vì được bật → LỖI (MAJOR F1)
```

```
Cơ chế T-E: Hủy tải khi đang ở retryPass (vòng đời)
T1: retryPass đang delay(backoff) trước lần thử thứ 2 của chương 6
T2: Người dùng bấm "Hủy tải" → downloadJob.cancel()
T3: delay ném CancellationException; fetchChapterWithRetry rethrow (DownloadRepository.kt:144-145); flow kết thúc
Kết cục: không emit thêm, không ghi DB → AN TOÀN
```

#### Commands Executed

Trích từ `evidence.md` (reviewer không tự chạy lệnh):
- `### [CMD] git status --short` — `Exit: 0` — 27 file `M` và 5 file `??`, gồm các file `[MODIFY]` của plan (`WikidichScraper.kt`, `StoryRepository.kt`, `SearchViewModel.kt`, …) đang có thay đổi chưa commit.
- `### [CMD] git diff --stat` — `Exit: 0` — `27 files changed, 1191 insertions(+), 314 deletions(-)`.
- `### [CMD] cmd.exe /d /c gradlew.bat compileDebugKotlin` — `Exit: 0` — `BUILD SUCCESSFUL in 1s`.
- `### [CMD] cmd.exe /d /c gradlew.bat ktlintCheck` — `Exit: 0` — `BUILD SUCCESSFUL in 1s`.
- `### [CMD] cmd.exe /d /c gradlew.bat testDebugUnitTest` — `Exit: 0` — `BUILD SUCCESSFUL in 1m 46s`.
- `### [CMD] node --test .agents/scripts/code-freeze-guard.test.js` — `Exit: 0` — `pass 36, fail 0`.

UNVERIFIED (đề xuất User/Planner chạy hoặc bổ sung):
- `curl.exe -q -s https://wikicv.org/tim-kiem` khi có phiên đăng nhập (qua WebView/cookie) để trích khối "Loại hình" — reviewer không có cookie nên không thực hiện được.
- `curl.exe -q -s https://tangthuvien.org/truyen?keyword=tien&genre=tien-hiep&page=2` để xác minh tổ hợp tham số tìm kiếm TTV (WebFetch trả 502).

#### Kết luận

Còn 6 lỗi `[MAJOR]` (F1–F6) và các kịch bản S1, S4, S5, S6 ở trạng thái FAIL. Planner cần ra Revision 2 xử lý F1–F6 (kèm bằng chứng HTML có ngày khảo sát cho F2/F5) và cân nhắc các mục `[MINOR]` F7–F11.

[CHANGES_REQUESTED]

### Audit Run 2

- **Reviewer**: Claude Code CLI (Claude Opus 5.5, `claude-opus-5-5`)
- **Ngày**: 2026-10-30
- **Đối tượng**: Technical Plan Revision 2

Ghi chú: working tree vẫn có nhiều file `M` chưa commit (`[CMD] git status --short`, Exit 0), nên mọi `[SRC]` dưới đây là nội dung file hiện tại trên đĩa. Kết luận CHANGES_REQUESTED của Audit Run 1 chỉ dùng làm danh sách cần kiểm lại.

#### Kiểm Chứng Lỗi Cũ

| Mã | Trạng thái | Bằng chứng trên code thật | Ghi chú |
| :--- | :---: | :--- | :--- |
| F1 | ĐÃ SỬA | `[SRC] WikidichScraper.kt:136-137` (`fetchDocumentWithRetry` → `checkLoginRequired`), `WikidichScraper.kt:503-506` (`enrichWithChapterCounts(stories, maxItems = ENRICH_MAX_ITEMS)` có default), `WikidichScraper.kt:564-573` | Sketch §3.3 mục 4 giữ `checkLoginRequired(doc)` và gọi đúng `enrichWithChapterCounts(filtered)`; `.ifEmpty { stories }` thật ở `WikidichScraper.kt:141` được thay bằng `filterStories`. Có test `LoginRequiredException` cho bản 5 tham số trong Test Contract. |
| F2 | SỬA MỘT PHẦN | `[SRC] search_logged_in.html:306` (`tag=5794f03dd7ced228f44191ac`), `search_logged_in.html:342` (`tag=5794f03dd7ced228f44191c0`), `story_detail.html:321` (`age=6748055223deea071cb36cd5`), `story_detail.html:317-333`; `[URL]` WebFetch `https://wikicv.org/tim-kiem` (2026-10-30) vẫn trả tường "Đăng nhập để xem nội dung" | Plan đã thêm trích đoạn HTML và ngày khảo sát, nhưng trích đoạn mâu thuẫn với HTML thật đã có trong repo (xem N1). Chưa thể dùng làm bằng chứng. |
| F3 | SỬA MỘT PHẦN | `[SRC] DownloadRepository.kt:146-153` (outcome nuốt lỗi), `DownloadRepository.kt:234-251` (khối `fatalError`), `DownloadRepository.kt:276-283` (delay Wikidich), `StoryRepository.kt:826-834` (`DownloadProgress` 4 tham số) | Đã sửa: kiểm `outcome.error`, `wikidichDelayProvider()` trước mỗi chương, `DownloadProgress` 4 tham số, bỏ `CloudflareBlockedException`/`baseDelay`. Còn lại: khối ghi DB của `retryPass` dùng ký hiệu không tồn tại và lệch luồng ghi của vòng chính (xem N2, N3). |
| F4 | ĐÃ SỬA | `[SRC] SearchViewModel.kt:441-447` (guard cũ), `SearchScreen.kt:84` (`state.isFilterActive`) | §3.4 nêu rõ guard mới, nhánh từ khóa dùng `searchStories(page = nextPage)`, sửa `SearchScreen.kt:84`. Vị trí và tên hàm khớp code thật. |
| F5 | SỬA MỘT PHẦN | `[SRC] SearchViewModel.kt:348-354` (giữ `matchesGenre`), `SsTruyenScraper.kt:262-263`, `TangThuVienScraper.kt:163-165`; `[URL]` WebFetch `https://sstruyen.pics/tim-kiem?keyword=tien&page=2` (2026-10-30): trang hiển thị "Trang / 124", không có trang 2 đang chọn, phân trang sinh động, không thấy href `page=`; `[URL]` WebFetch `https://tangthuvien.org/truyen?keyword=tien&page=2` → HTTP 502 | Lọc thể loại client-side đã được giữ. Tham số `&page=$page` cho cả TTV và SsTruyện vẫn không có khảo sát HTML kèm ngày như `04-scraping.md` yêu cầu; kết quả WebFetch SsTruyện gợi ý tham số `page` bị bỏ qua → `UNVERIFIED`. |
| F6 | SỬA MỘT PHẦN | `[SRC] SearchViewModel.kt:90-91`, `DownloadRepositoryTest.kt:222-264`, `SettingsViewModelTest.kt:136-170`, `SearchViewModelTest.kt:388`, `ExploreViewModel.kt:59-62` | Đã sửa: dùng `viewModel.genres`, tên test Settings đúng, test `resolveSingleSource`. Mốc `2_250L` đã tự tính lại và khớp (batch 1 = 250+500 = 750; inter-batch 500 → 1250; retryPass 250 + 750 → 2250). Còn thiếu: `SearchViewModelTest.kt:388` sẽ hỏng do N4 mà không có trong Type C; Explore chưa nêu cách tránh gọi `loadFirstPage()` lần hai ở phát xạ đầu (N6). |
| F7 | ĐÃ SỬA | `[SRC] SettingsDataStore.kt:162-181` (return sớm ở 163-164), `SettingsDataStore.kt:108-110`, `StoryRepository.kt:75-89` | §3.2 xử lý cả trường hợp đã migrate, chuẩn hóa khi restore; sketch `activeSources()` giữ migrate + `try/catch`; `observeActiveSource()` có `.catch { IOException }`. |
| F8 | SỬA MỘT PHẦN | `[SRC] WikidichScraper.kt:94, 98, 372-406, 81-125`; `SettingsScreen.kt:394-409`; `DownloadRepositoryTest.kt:246`; `StoryDetailViewModel.kt:278-288, 295` | Đã sửa `performDownload`, `version = 9`, dải dòng `getGenreList`/`GENRE_QUERY_MAP`. Lệch mới: plan nói "27 mục" (thật: 35 mục trong `getGenreList`, 43 khóa trong map); `mat-the`/`lam-ruong` ở dòng 94/98 (plan ghi 98/100); vòng nguồn của Settings bắt đầu ở 394 (plan ghi 405-445); assert `750L` ở dòng 246 (plan ghi 254); `downloadRange` ở 278-288 (plan ghi 281-293). |
| F9 | ĐÃ SỬA | `[SRC] StoryRepository.kt:135-139, 328-331` | §8 và §9 (`docs/architecture.md`) ghi rõ hành vi circuit breaker khi chỉ có 1 nguồn và gợi ý đổi nguồn bằng chip. |
| F10 | ĐÃ SỬA | `[SRC] ExploreScreen.kt:113-152` (chip "Truyện nam" dòng 124, `VerticalDivider` dòng 146) | Bước 5.3 bọc chip và divider trong `if (state.activeSource == SourceIds.WIKIDICH)`. |
| F11 | ĐÃ SỬA | `[SRC] ExploreViewModel.kt:87-89` (`loadJob?.cancel()`), `SearchViewModel.kt:142` (`searchJob?.cancel()`) | Sketch dùng `collectLatest`. |

#### Phát Hiện Mới (Fresh Findings & Regression Check)

**[MAJOR] N1 — Trích đoạn HTML "Loại hình" (fixture mới) mâu thuẫn với HTML wikicv.org thật đã có trong repo; test fixture là vòng tròn**
- `[SRC] search_logged_in.html:306, 342`: chính giao diện wikicv.org dùng `tag=5794f03dd7ced228f44191ac` (Mạt thế) và `tag=5794f03dd7ced228f44191c0` (Làm ruộng/Điền văn). ObjectId là duy nhất; plan cho rằng cùng ID này nằm ở `genre=` trong bảng lọc nâng cao mà không có HTML thật chứng minh.
- Lập luận "dãy liên tiếp" ở §3.3 mục 1.1 nói `...1a2` → `...1a9` là nhóm Loại hình, nhưng chính danh sách 19 mục lại gồm `...1ac`, `...1c0`, `...1d4` — nằm trong dải tag (`...1ad`, `...1af`, `...1b0`, `...1b6`, `...1c1`, `...1cc`, `...1d7` đều là `tag=` theo `search_logged_in.html:276-402` và `story_detail.html:333-338`). Mâu thuẫn nội tại.
- `[SRC] story_detail.html:321`: tham số Thời đại thật là `age=`, còn trích đoạn plan mô tả hàng `bg` (Thời đại). Cấu trúc `div.filter-box > div.filter-row[data-group] > a.filter-item[data-field][data-value]` không xuất hiện ở bất kỳ fixture thật nào. Có dấu hiệu trích đoạn được dựng lại, không phải sao chép từ trang.
- Test `getGenreList and GENRE_QUERY_MAP match ... fixture` so code với một file do chính Planner tạo → không phát hiện được ID sai namespace (cùng vấn đề mock vòng tròn đã nêu ở F2).
- Đổi `qs=2` → `qs=1` ở `getCompletedStories`/`getStoriesByGenre` (`WikidichScraper.kt:334, 340, 359`) không có bằng chứng ý nghĩa của `qs`; site dùng `qs=2` ở tag-tab trang chủ (`search_logged_in.html:276-408`) và `qs=1` ở trang chi tiết (`story_detail.html:300-423`).
- Yêu cầu: lưu nguyên văn HTML khối lọc `/tim-kiem` (đã đăng nhập, ví dụ qua WebView "Lưu trang" hoặc `curl.exe -q -s` kèm cookie do User tự chạy), không biên tập lại; giải thích lại 3–4 ID trong dải tag; nếu không lấy được HTML thật thì giữ `tag=` cho Mạt thế/Làm ruộng như code hiện tại.

**[MAJOR] N2 — Sketch tham chiếu các ký hiệu không tồn tại (S1 Build)**
- `isDummyTitle(...)` và `Chapter.toEntity()` trong `retryPass` (§3.5): `[SRC]` Grep không có `fun isDummyTitle` hay `Chapter.toEntity` trong `app/src`; vòng chính dùng `DUMMY_TITLE_REGEX` (`DownloadRepository.kt:61, 206`); `toEntity()` duy nhất là `private fun Story.toEntity()` ở `StoryRepository.kt:752`.
- `triggerFilterBrowse()` trong `handleActiveSourceSwitched()` (§3.4): không tồn tại; các hàm thật là `browseByGenre` (`SearchViewModel.kt:253`) và `browseByFilterOnly` (`SearchViewModel.kt:295`).
- Bước 3.2 "Chỉ gộp kết quả local (`storyDao.searchLocal(keyword)`) ở trang đầu": `[SRC]` không có DAO nào có hàm `search*` hay câu `LIKE` trong `data/local/`; `StoryRepository.searchStories` (`StoryRepository.kt:323-372`) hiện không gộp kết quả local. Bước này mô tả một hành vi không có trong code và không có Test Contract.

**[MAJOR] N3 — Luồng ghi DB của `retryPass` lệch với vòng chính; Test Contract `coVerify` sẽ không khớp**
- `[SRC] DownloadRepository.kt:206-228`: vòng chính luôn gọi `chapterDao.saveChapterContent(storyId, chapterNumber, content, title = titleToSave, url = chapter.url)`; `saveChapterContent` là UPSERT (`ChapterDao.kt:92-120`) nên không cần nhánh `getChapter`/`insertChapters`. Sketch `retryPass` tự tạo nhánh khác: gọi `saveChapterContent` 3 tham số (title mặc định `"Chương $chapterNumber"`, url `""`, `ChapterDao.kt:97-99`) và nhánh `insertChapters` (REPLACE, `ChapterDao.kt:69-70`) không đặt `downloadedTimestamp`. Hai đường ghi khác nhau cho cùng một việc dễ sinh sai lệch metadata.
- Test Contract `coVerify(exactly = 1) { chapterDao.saveChapterContent("s1", 2, any()) }`: Kotlin điền giá trị mặc định `timestamp = System.currentTimeMillis()` lúc verify → không khớp lời gọi thật. Test hiện có dùng 6 matcher (`DownloadRepositoryTest.kt:69, 263, 281`).
- Yêu cầu: tách khối "lưu một kết quả thành công" của vòng chính (`DownloadRepository.kt:205-229`) thành hàm private dùng chung cho cả vòng chính và `retryPass`; sửa `coVerify` theo 6 tham số.

**[MAJOR] N4 — Đổi `search()` sang `completedOnly = _state.value.completedOnly` làm thu hẹp kết quả tìm kiếm từ khóa trên cả 3 nguồn; test hiện có sẽ hỏng ngoài Type C**
- `[SRC] SearchViewModel.kt:153-155`: code cố ý truyền `completedOnly = false` vì HTML tìm kiếm của đa số nguồn không có trạng thái. `[SRC] SearchViewModel.kt:58`: mặc định `completedOnly = true`.
- Hệ quả theo §3.4: TTV thêm `&status=COMPLETED` (`TangThuVienScraper.kt:164`); SsTruyện lọc cứng theo `status` (`SsTruyenScraper.kt:268-276`) → truyện không có nhãn trạng thái bị loại; Wikidich thêm `status=` server-side và `filterStories` bỏ `.ifEmpty`. Plan không nêu và không giải thích thay đổi hành vi này.
- `[SRC] SearchViewModelTest.kt:388`: stub `repository.searchStories("test", false)` sẽ không khớp lời gọi `completedOnly = true` → mock relaxed trả rỗng → assert dòng 407 hỏng. Test này không có trong Type C.
- Mâu thuẫn nội tại: Test Contract dòng `search with keyword and genre…` kỳ vọng `searchStories("tiên", false, 1, true, "tien-hiep")` trong khi state mặc định có `completedOnly = true`.
- Yêu cầu: chọn rõ một hướng (giữ `false` cho nguồn không có trạng thái, hoặc chỉ truyền `completedOnly` thật cho Wikidich) và cập nhật Type C/Test Contract tương ứng.

**[MINOR] N5 — `SettingsViewModel.selectSource` không kiểm tra `sourceId` hợp lệ nhưng Test Contract kỳ vọng bỏ qua `"invalid"`**
- Bước 4.1 ghi thẳng `prefs[ENABLED_SOURCES] = sourceId`. Sau `selectSource("invalid")`, `resolveSingleSource("invalid")` trả `"tangthuvien"`, trái với kỳ vọng `setOf("wikidich")`. Cần guard `if (sourceId !in SourceIds.ALL_ACTIVE_SOURCES) return` như `setActiveSource` ở §3.4. Ngoài ra `SettingsState.enabledSources` mặc định vẫn là `ALL_SOURCES` (`SettingsViewModel.kt:95`) → trước phát xạ đầu UI có thể hiển thị 3 RadioButton cùng chọn; nên đổi mặc định sang `setOf(SourceIds.DEFAULT_SINGLE_SOURCE)`.

**[MINOR] N6 — Explore: phát xạ đầu của `observeActiveSource()` có thể gọi `loadFirstPage()` lần hai lên Wikidich**
- `[SRC] ExploreViewModel.kt:59-62`: `init` đã gọi `loadGenres()` + `loadFirstPage()`. Bước 5.3 chỉ nói "khi nguồn thay đổi… gọi `loadFirstPage()`", không nêu guard phát xạ đầu như sketch Search (`previous == null`). Nếu nguồn lưu là `wikidich`, request đầu bị `loadJob?.cancel()` hủy giữa chừng rồi gửi request thứ hai — tốn request trên nguồn có rate-limit. Cần nêu rõ cùng mẫu guard với Search.

**[MINOR] N7 — `handleActiveSourceSwitched` reset `selectedGenre` nhưng không tính lại `isFilterActive`**
- `[SRC] SearchViewModel.kt:75-84, 207-215`: `isFilterActive` được tính từ `genre`. Khi thể loại là bộ lọc duy nhất bị reset về `null`, `isFilterActive` vẫn `true` → nhánh `s.isFilterActive` chạy duyệt truyện hoàn thành thay vì về trạng thái trống. Nên gọi `hasAnyNonDefaultFilter(...)` khi reset.

**[MINOR] N8 — Negative Probe 1 bỏ sót `age=` và `official=`**
- `[SRC] WikidichScraper.kt:89-95, 111-112`: map hiện có `age=` và `official=`; regex `'"(tag|bg|ending)='` không bắt hai tiền tố này. Nên dùng probe dương: mọi giá trị trong `GENRE_QUERY_MAP` khớp `^genre=[0-9a-f]{24}$`.

#### Mental Simulation Matrix (S1–S6 + Domain)

| Kịch bản | Kết quả | Bằng chứng | Chi tiết |
| :--- | :---: | :--- | :--- |
| S1 Cold-start & Build | FAIL | `[CMD]` `cmd.exe /d /c gradlew.bat compileDebugKotlin` Exit 0 (baseline); `[SRC]` `DownloadRepository.kt:61, 206`, `StoryRepository.kt:752`, `SearchViewModel.kt:253, 295` | Baseline biên dịch được, không thêm dependency. Sketch dùng `isDummyTitle`, `Chapter.toEntity()`, `triggerFilterBrowse()`, `storyDao.searchLocal` — không tồn tại (N2). |
| S2 Lifecycle & Teardown | PASS | `[SRC]` `DownloadRepository.kt:140, 144-145, 183, 202`; `ExploreViewModel.kt:88-89`; `SearchViewModel.kt:142` | `retryPass` chạy trong cùng `flow {}`, có `ensureActive()` trước/sau fetch, `delay` hủy được. Collector `collectLatest` nằm trong `viewModelScope`. |
| S3 Database & Temporal (Room + DataStore) | PASS | `[SRC]` `AppDatabase.kt:22` (v9), `SettingsDataStore.kt:70, 108-110, 162-181`; `StoryRepository.kt:75-89` | Không đổi schema Room, key `enabled_sources` giữ nguyên; `resolveSingleSource` đọc được CSV cũ, giá trị restore và ID legacy. Xem timeline T-A. |
| S4 Cross-Task State Flow | FAIL | `[SRC]` `SearchViewModel.kt:58, 75-84, 153-155`; `SearchViewModelTest.kt:388`; `ExploreViewModel.kt:59-62` | `completedOnly` đổi ngữ nghĩa tìm kiếm từ khóa và làm hỏng test ngoài Type C (N4); `isFilterActive` không cập nhật khi reset thể loại (N7). |
| S5 Security & Privacy Boundary | PASS | `[SRC]` `WikidichScraper.kt:136-137, 564-573`; `DownloadRepository.kt:146-147, 234-251`; `StoryRepository.kt:359-361` | `search` 5 tham số giữ `checkLoginRequired`; `retryPass` ném `LoginRequiredException` → `markLoginRequired` ở tầng trên. Không đụng `CookieVault`/Manifest. Xem timeline T-B. |
| S6 External Resilience | FAIL | `[SRC]` `search_logged_in.html:306, 342`, `story_detail.html:321`; `[URL]` WebFetch wikicv.org/tim-kiem (tường đăng nhập), sstruyen.pics `&page=2` (không có trang 2), tangthuvien.org (502) | ID Loại hình mâu thuẫn HTML thật (N1); tham số phân trang TTV/SsTruyện chưa xác minh (F5). |
| Domain: Gộp nguồn / Tủ truyện | PASS | `[SRC]` `StoryRepository.kt:95-97`; `DownloadRepository.kt:71-73, 175-177` | Chi tiết/chương/tải offline vẫn định tuyến qua `sourceFor(url)` trên toàn bộ `sources`, độc lập với `activeSources()`. |
| Domain: Tải offline – tiến độ | PASS | `[SRC]` `StoryRepository.kt:826-834` | `processed = current + errors`; `retryPass` tăng `downloaded` và giảm `errors` nên `processed` không vượt `total`. |
| Domain: UI tiếng Việt | PASS | `[SRC]` `SettingsDataStore.kt:29-34` | Nhãn mới là tiếng Việt. Lưu ý `SOURCE_LABELS` hiện là `"TàngThưViện"` (không dấu cách) trong khi plan mô tả "Tàng Thư Viện"; nên thống nhất. |

#### Adversarial Timelines

```
Cơ chế T-A (S3): DataStore ENABLED_SOURCES cũ + migrate + chọn nguồn từ Settings khi chưa migrate
T1: Bản cài mới, IS_WIKIDICH_MIGRATED chưa có, ENABLED_SOURCES = null
T2: Người dùng mở Settings trước Search/Explore, chọn "sstruyen" → SettingsViewModel.selectSource ghi "sstruyen" (không đặt IS_WIKIDICH_MIGRATED)
T3: Mở Search → activeSources() → migrate chạy nhánh chưa migrate. Code cũ (SettingsDataStore.kt:176-179) sẽ ghi "sstruyen,wikidich"; theo §3.2 ghi resolveSingleSource("sstruyen") = "sstruyen"
Kết cục: cả hai cách đều resolve "sstruyen" → AN TOÀN, với điều kiện §3.2 thay hẳn logic add("wikidich"). Khuyến nghị selectSource cũng ghi IS_WIKIDICH_MIGRATED = true như setActiveSource.
```

```
Cơ chế T-B (S5): Phiên Wikidich hết hạn giữa retryPass
T1: Vòng chính tải 5 chương Wikidich, chương 2 và 4 lỗi NetworkException
T2: retryPass: delay(wikidichDelayProvider) rồi fetch chương 2 → trang login → scraper ném LoginRequiredException → fetchChapterWithRetry trả outcome.error (DownloadRepository.kt:146-147)
T3: Sketch kiểm outcome.error là LoginRequiredException → throw ngay; chương 4 không được gọi
Kết cục: phiên tải dừng, DownloadService nhận lỗi đăng nhập → AN TOÀN
```

```
Cơ chế T-C (đồng thời): Đổi nguồn ở Search khi đang tải trang 2 tìm kiếm từ khóa
T1: Search đang ở trang 1 "tiên" trên wikidich; loadMoreFilterResults() khởi chạy searchJob trang 2
T2: Người dùng chọn tangthuvien → setActiveSource → collectLatest nhận "tangthuvien" → handleActiveSourceSwitched → search() → searchJob?.cancel() (SearchViewModel.kt:142) hủy job trang 2
T3: search() trang 1 trên TTV ghi currentPage = 1, results mới
Kết cục: không trộn kết quả trang 2 Wikidich vào TTV → AN TOÀN (phụ thuộc việc loadMoreFilterResults vẫn gán vào searchJob như SearchViewModel.kt:451-452)
```

```
Cơ chế T-D (đồng thời): Phát xạ đầu observeActiveSource trên Explore với nguồn lưu = wikidich
T1: init → loadFirstPage() gửi /tim-kiem?... lên wikicv.org (ExploreViewModel.kt:59-62)
T2: collectLatest nhận "wikidich" lần đầu; nếu không có guard previous == null → loadFirstPage() lại → loadJob?.cancel() hủy request T1 giữa chừng
T3: Request thứ hai được gửi trong vài ms
Kết cục: 2 request liên tiếp vào nguồn có rate-limit → LỖI NHẸ (MINOR N6)
```

#### Commands Executed

Trích từ `evidence.md` (reviewer không tự chạy lệnh):
- `### [CMD] git status --short` — `Exit: 0` — 27 file `M` và 5 file `??`, gồm các file `[MODIFY]` của plan.
- `### [CMD] git diff --stat` — `Exit: 0` — `27 files changed, 1191 insertions(+), 314 deletions(-)`.
- `### [CMD] cmd.exe /d /c gradlew.bat compileDebugKotlin` — `Exit: 0` — `BUILD SUCCESSFUL in 9s`.
- `### [CMD] cmd.exe /d /c gradlew.bat ktlintCheck` — `Exit: 0` — `BUILD SUCCESSFUL in 878ms`.
- `### [CMD] cmd.exe /d /c gradlew.bat testDebugUnitTest` — `Exit: 0` — `BUILD SUCCESSFUL in 1s` (`34 actionable tasks: 34 up-to-date`).
- `### [CMD] node --test .agents/scripts/code-freeze-guard.test.js` — `Exit: 0` — `pass 36, fail 0`.
- `### [CMD] node --test .agents/scripts/plan-approval.test.js` — `Exit: 0` — `pass 13, fail 0`.
- `### [CMD] node --test .agents/scripts/review-run.test.js` — `Exit: 0` — `pass 10, fail 0`.

UNVERIFIED (đề xuất User/Planner chạy hoặc bổ sung):
- `curl.exe -q -s https://wikicv.org/tim-kiem` với phiên đăng nhập (User tự chạy, không đưa cookie vào plan) để lấy nguyên văn khối lọc "Loại hình".
- `curl.exe -q -s "https://sstruyen.pics/tim-kiem?keyword=tien&page=2"` và `curl.exe -q -s "https://tangthuvien.org/truyen?keyword=tien&page=2"` để xác minh tham số phân trang tìm kiếm.

#### Kết luận

Đã sửa: F1, F4, F7, F9, F10, F11. Sửa một phần: F2, F3, F5, F6, F8. Phát hiện mới: 4 lỗi `[MAJOR]` (N1–N4) và 4 lỗi `[MINOR]` (N5–N8). Kịch bản S1, S4, S6 FAIL. Planner cần ra Revision 3: lấy HTML thật cho Loại hình (hoặc giữ `tag=` hiện tại), thay các ký hiệu không tồn tại, dùng chung hàm lưu chương giữa vòng chính và `retryPass`, chốt lại ngữ nghĩa `completedOnly` khi tìm kiếm từ khóa kèm cập nhật Type C, và khảo sát tham số phân trang TTV/SsTruyện.

[CHANGES_REQUESTED]

### Audit Run 3

- **Reviewer**: Claude Code CLI (Claude Opus 5.5, `claude-opus-5-5`)
- **Ngày**: 2026-10-30
- **Đối tượng**: Technical Plan Revision 3

Ghi chú: working tree vẫn có nhiều file `M` chưa commit (`[CMD] git status --short`, Exit 0), nên mọi `[SRC]` dưới đây là nội dung file hiện tại trên đĩa. Kết luận CHANGES_REQUESTED của Audit Run 1 và 2 chỉ được dùng làm danh sách cần kiểm lại; mọi trạng thái dưới đây được kiểm lại trên code thật.

#### Kiểm Chứng Lỗi Cũ

| Mã | Trạng thái | Bằng chứng trên code thật | Ghi chú |
| :--- | :---: | :--- | :--- |
| F1 | ĐÃ SỬA | `[SRC] WikidichScraper.kt:136-137` (`fetchDocumentWithRetry` → `checkLoginRequired`), `WikidichScraper.kt:503-506` (`enrichWithChapterCounts(stories, maxItems = ENRICH_MAX_ITEMS)`), `WikidichScraper.kt:564-573` | Sketch §3.3 mục 4 giữ `checkLoginRequired(doc)` và gọi `enrichWithChapterCounts(filtered)` (tham số `maxItems` có default nên biên dịch được). `SEARCH_PAGE_SIZE`, `MALE_GENDER_PARAM`, `COMPLETED_STATUS_PARAM` tồn tại ở `WikidichScraper.kt:56-58`. |
| F2 | SỬA MỘT PHẦN | `[URL]` WebFetch `https://wikicv.org/truyen/mat-the-da-vat-ta-mac-vao-nu-than-da-mat-aj6iIVS4CCixlhAF` (2026-10-30, không có tường đăng nhập): `href="/tim-kiem?genre=5794f03dd7ced228f44191ac&qs=1"` "Mạt thế", `href="/tim-kiem?genre=5fd54e6523deea0a1a87e9b9&qs=1"` "Sinh tồn"; hai URL còn lại trả HTTP 500. `[SRC] story_detail.html:316-338`; `search_logged_in.html:312-378` | Bằng chứng live xác nhận `...1ac` là `genre=` (giải thích được xung đột với `tag=` ở trang chủ). Nhưng trích đoạn fixture trong plan không khớp file thật và danh sách 19 mục thiếu Võng du — xem N1 và R5. |
| F3 | ĐÃ SỬA | `[SRC] DownloadRepository.kt:146-153` (outcome nuốt lỗi), `DownloadRepository.kt:234-243` (tập điều kiện `fatalError`), `DownloadRepository.kt:276-283` (delay Wikidich), `StoryRepository.kt:826-834` (`DownloadProgress` 4 tham số), `DownloadRepository.kt:68-69` (`isRateLimitHttpError`) | Sketch `retryPass` kiểm `outcome.error` với đúng tập điều kiện của vòng chính, delay `wikidichDelayProvider()` trước mỗi chương, emit 4 tham số. Phần ghi DB tách riêng thành N3/R1. |
| F4 | ĐÃ SỬA | `[SRC] SearchViewModel.kt:441-447`, `SearchScreen.kt:84` | Guard mới và điều kiện `shouldLoadMore` khớp vị trí thật. Có hệ quả phụ ở R6. |
| F5 | ĐÃ SỬA | `[SRC] SearchViewModel.kt:153-155` (giữ `completedOnly = false`), `SearchViewModel.kt:348-354` (giữ `matchesGenre`) | Rev 3 giữ nguyên `TangThuVienScraper.kt`/`SsTruyenScraper.kt`, không thêm `page` cho hai nguồn này, `canLoadMore = false` cho nhánh không phải Wikidich, có chú thích tiếng Việt khi không hỗ trợ `keyword + genre`. Khớp với kết quả WebFetch SsTruyện ở Run 2. |
| F6 | SỬA MỘT PHẦN | `[SRC] SearchViewModelTest.kt:40`, `ExploreViewModelTest.kt` (mock `relaxed`), `WikidichScraperTest.kt:211-345` | Đã sửa: N4 không còn làm hỏng `SearchViewModelTest.kt:388`. Còn thiếu: (a) plan vẫn không nêu stub `every { repository.observeActiveSource() } returns …` trong `setUp` của Search/Explore — với mock relaxed, Flow giả không phát phần tử nào nên `activeSource` luôn là mặc định `"tangthuvien"`; hai test Contract `search with keyword and genre on wikidich…` và `selectSource … resets incompatible selectedGenre` không thể đạt nếu không có `MutableStateFlow` giả lập; (b) Type C của `WikidichScraperTest` sai tên test và thiếu nhiều test sẽ hỏng (R4). |
| F7 | ĐÃ SỬA | `[SRC] SettingsDataStore.kt:162-181`, `SettingsDataStore.kt:108-110`, `StoryRepository.kt:75-89` | Vẫn đúng như mô tả. Có điểm TOCTOU nhỏ ở R9. |
| F8 | SỬA MỘT PHẦN | `[SRC] WikidichScraper.kt:83-124` (42 dòng mapping), `WikidichScraper.kt:372-406` (35 `Genre`), `WikidichScraperTest.kt:211, 267` | Các dải dòng nêu ở Run 2 đã được sửa. Lệch còn lại: `GENRE_QUERY_MAP` có 42 khóa (plan ghi 43); tên hai test Type C của `WikidichScraperTest` không tồn tại (R4); trích đoạn `story_detail.html:316-331` không khớp nội dung file (R5). |
| F9 | ĐÃ SỬA | `[SRC] StoryRepository.kt:135-139, 329-331` | §8 và §9 vẫn ghi rõ. |
| F10 | ĐÃ SỬA | `[SRC] ExploreViewModel.kt:33-44` (`ExploreState` có `maleOnly`) | Bước 5.3 bọc chip "Truyện nam" theo `activeSource`. |
| F11 | ĐÃ SỬA | `[SRC] ExploreViewModel.kt:87-89`, `SearchViewModel.kt:142` | Sketch dùng `collectLatest`. |
| N1 | SỬA MỘT PHẦN | `[URL]` WebFetch trang "mat-the-da-vat…" (2026-10-30) như F2; `[SRC] story_detail.html:329` (chỉ có `genre=5794f03dd7ced228f44191a8` "Manga anime"); `search_logged_in.html:312, 324, 330, 336, 372, 378` | Xác minh được trên HTML thật: `...1ac`, `5fd54e65…`, `...1a8`, `57d17c94…`, `...1a3`, `...1a4`, `...1a6`, `...1a7`. Không có bằng chứng HTML nào trong plan cho `5a1ce8f5…` (Tranh bá), `6785b91d…` (Quân sự), `58458dc2…` (Huyền học), `596bd830…` (Phim ảnh); các ID còn lại chỉ dựa vào hai URL reviewer không tải được (HTTP 500) → `UNVERIFIED`. Đổi `qs=2` → `qs=1` vẫn chưa có bằng chứng cho `/tim-kiem` có `so=4&vo=1` (trang lọc bị tường đăng nhập). |
| N2 | ĐÃ SỬA | Grep `isDummyTitle|toEntity|triggerFilterBrowse|searchLocal|insertChapters` trên plan chỉ còn khớp trong Audit History | Bốn ký hiệu không tồn tại đã bị loại khỏi Technical Plan. Tuy nhiên Rev 3 tạo ra lỗi chữ ký mới (R1, R2). |
| N3 | SỬA MỘT PHẦN | `[SRC] DownloadRepository.kt:205-228`, `ChapterDao.kt:93-100` | Đã tách hàm dùng chung `persistDownloadedChapter` và đổi `coVerify` sang 6 matcher. Nhưng thân hàm mới không biên dịch và lệch logic vòng chính (R1). |
| N4 | ĐÃ SỬA | `[SRC] SearchViewModel.kt:58, 153-155`; `SearchViewModelTest.kt:388` | Nhánh không phải Wikidich giữ `searchStories(query, completedOnly = false)` (giá trị default `page = 1, maleOnly = true, genreSlug = null` khớp stub hiện có). Test Contract Wikidich dùng `completedOnly = true` khớp `SearchState` mặc định. |
| N5 | ĐÃ SỬA | `[SRC] SettingsViewModel.kt:95, 245-269` | Bước 4.1 có guard `sourceId !in ALL_SOURCES`, ghi `IS_WIKIDICH_MIGRATED = true`, đổi mặc định `enabledSources`. |
| N6 | ĐÃ SỬA | `[SRC] ExploreViewModel.kt:59-62` | Bước 5.3 dùng cùng guard `previous == null` như Search. |
| N7 | ĐÃ SỬA (có regression) | `[SRC] SearchViewModel.kt:75-84` | Sketch tính lại `isFilterActive = hasAnyNonDefaultFilter(...)`. Nhánh cuối `clearFilter()` của chính sketch này gây R3. |
| N8 | ĐÃ SỬA | `[SRC] WikidichScraper.kt:89-95, 111-112, 120-122` | Negative Probe 1 đã có `age` và `official`; có thêm probe dương `^genre=[0-9a-f]{24}$`. |

#### Phát Hiện Mới (Fresh Findings & Regression Check)

**[MAJOR] R1 — `persistDownloadedChapter` không biên dịch và lệch logic tiêu đề của vòng chính (S1, regression N3)**
- `[SRC] ChapterDao.kt:93-100`: `saveChapterContent(..., timestamp: Long = …, title: String = "Chương $chapterNumber", url: String = "")` — `title` là `String` không nullable. Sketch §3.5 truyền `title = titleToSave` với `titleToSave: String?` (giá trị `null` khi tiêu đề hiện tại không phải dạng giả) → lỗi kiểu khi biên dịch.
- `[SRC] DownloadRepository.kt:206-212, 221`: vòng chính chọn `result.title` khi `(isDummy || chapter.title.isBlank())` và ngược lại giữ `chapter.title`. Sketch bỏ nhánh `chapter.title.isBlank()` và ở nhánh còn lại muốn truyền `null` thay vì `chapter.title`. Nếu người triển khai "sửa cho biên dịch" bằng cách bỏ tham số, DAO sẽ dùng default `"Chương N"` (`ChapterDao.kt:98, 101`), ghi đè tiêu đề thật như `"Chương 2: Đã có tên"` trong `DownloadRepositoryTest.kt:193`.
- Mục tiêu của N3 là tách nguyên văn khối `DownloadRepository.kt:206-228` thành hàm dùng chung; sketch hiện tại là một bản viết lại khác.
- Yêu cầu: copy đúng logic `isDummy`/`titleToSave` của `DownloadRepository.kt:206-228` (giữ `title = titleToSave` là `String`, giữ điều kiện `chapter.title.isBlank()`), và để test `DownloadRepositoryTest.kt:186-219, 517-538` chạy qua hàm chung.

**[MAJOR] R2 — `handleActiveSourceSwitched()` gọi `browseByGenre`/`browseByFilterOnly` sai số tham số (S1)**
- `[SRC] SearchViewModel.kt:253-256`: `private fun browseByGenre(genre: Genre, chapterFilter: ChapterCountFilter)` — không có default cho `chapterFilter`. Sketch §3.4 gọi `browseByGenre(s.selectedGenre)`.
- `[SRC] SearchViewModel.kt:295`: `private fun browseByFilterOnly(chapterFilter: ChapterCountFilter)`. Sketch gọi `browseByFilterOnly()`.
- Yêu cầu: truyền `s.chapterFilter` ở cả hai lời gọi (như `SearchViewModel.kt:400, 402`).

**[MAJOR] R3 — `clearFilter()` đặt lại `activeSource` về `"tangthuvien"` và không bao giờ được đồng bộ lại (S4, regression của N7)**
- `[SRC] SearchViewModel.kt:487-490`: `clearFilter()` gán `_state.value = SearchState()`. Khi plan thêm `activeSource: String = SourceIds.DEFAULT_SINGLE_SOURCE` vào `SearchState`, mọi lần gọi `clearFilter()` sẽ đặt `activeSource = "tangthuvien"` bất kể DataStore.
- `observeActiveSource()` trong sketch §3.4 có `.distinctUntilChanged()`, nên DataStore không phát lại giá trị `"wikidich"`/`"sstruyen"` → state lệch vĩnh viễn cho tới lần đổi nguồn kế tiếp.
- `clearFilter()` được gọi ở `SearchViewModel.kt:224, 246, 404, 436` và ở nhánh cuối của chính `handleActiveSourceSwitched()` (sketch §3.4, nhánh `current.selectedGenre != null && validGenre == null`). Hệ quả: chip nguồn hiển thị sai, mục "Truyện nam" bị ẩn, và `search()` đi nhánh không phải Wikidich (bỏ `genreSlug`, khóa phân trang) dù repository vẫn tìm trên Wikidich.
- Test Contract `selectSource updates activeSource reloads genres and resets incompatible selectedGenre` đổi sang `tangthuvien` — trùng giá trị mặc định nên che mất lỗi này (xem timeline T-B).
- Yêu cầu: `clearFilter()` giữ `activeSource` (`SearchState(activeSource = _state.value.activeSource)`), và Test Contract đổi nguồn phải dùng nguồn khác `DEFAULT_SINGLE_SOURCE` (ví dụ `wikidich → sstruyen`).

**[MAJOR] R4 — Type C của `WikidichScraperTest` dùng tên test không tồn tại và bỏ sót ít nhất 5 assert sẽ hỏng (vi phạm Test Failure Triage)**
- Plan §6 Type C mục 1 nêu `` `getStoriesByGenre includes male gender param by default and omits when false` `` (211-244) và `` `getStoriesByGenre supports new background and tag slugs and returns empty for unknown slug` `` (247-282). `[SRC] WikidichScraperTest.kt:211, 267`: tên thật là `` `getStoriesByGenre uses filter form qs=2 params with newest sort and male filter` `` và `` `getStoriesByGenre without male filter omits gender param` ``.
- Các assert sẽ hỏng theo thiết kế Rev 3 mà không được phân loại:
  - `WikidichScraperTest.kt:239-246`: dùng slug `gioi-giai-tri` (bị loại → `emptyList()` không gửi request, `requestedPaths.first()` ném `NoSuchElementException`), assert `qs=2`, `assertFalse(contains("qs=1"))`; `:251-254` dùng `hien-dai`/`age=` (bị loại).
  - `WikidichScraperTest.kt:262`: `contains("/tim-kiem?q=hogwarts&qs=1")` — URL mới là `/tim-kiem?qs=1&q=hogwarts`.
  - `WikidichScraperTest.kt:272-278`: slug `he-thong` + `tag=…1b6` (bị loại).
  - `WikidichScraperTest.kt:296`: `contains("/tim-kiem?qs=2")` trong `getCompletedStories`.
  - `WikidichScraperTest.kt:334`: `contains("/tim-kiem?qs=2&status=…&so=4&vo=1")`.
  - `WikidichScraperTest.kt:343`: `assertTrue(genres.size > 20)` — danh sách mới có 19 mục.
- Yêu cầu: liệt kê đúng tên và dòng của từng test/assert bị đổi theo Type C, nêu giá trị mới.

**[MAJOR] R5 — Trích đoạn fixture `story_detail.html:316-331` trong plan không khớp file thật; danh sách "đúng 19 Loại hình" bỏ sót Võng du (`genre=`)**
- Plan §3.3 mục 1.4 và Test Contract dòng 1 khẳng định fixture chứa `Tình cảm → …1a2`, `Tiên hiệp → …1a4`, `Huyền huyễn → 57d17c94…`, `Xây dựng → 5fd3abb6…`. `[SRC] story_detail.html:316-338`: các link thật là `official=…194` (Diễn sinh), `gender=…195` (Ngôn tình), `age=6748055…` (Giả tưởng lịch sử), `ending=…19f` (HE), `genre=…1a8` (Manga anime), `tag=…1af` (Xuyên việt), `tag=…1b6` (Hệ thống). Grep `genre=` trên `fixtures/wikidich` chỉ trả `story_detail.html:329` và 6 tag-tab ở `search_logged_in.html`. Test Contract "đối chiếu với fixture thật" như mô tả sẽ không chạy được trên file này.
- `[SRC] search_logged_in.html:336-338`: `href="/tim-kiem?qs=2&genre=5794f03dd7ced228f44191a5"` "Võng du" — đây là `genre=` trên chính giao diện wikicv.org, và code hiện tại đang ánh xạ `"vong-du" to "genre=5794f03dd7ced228f44191a5"` (`WikidichScraper.kt:93`). Danh sách 19 mục của plan bỏ Võng du, nên khẳng định "đúng 19 thể loại thuộc nhóm Loại hình" mâu thuẫn với HTML thật và gây mất một thể loại đang dùng được.
- Yêu cầu: dùng đúng nội dung fixture (hoặc lưu fixture mới từ các trang `/truyen/<slug>` công khai đã khảo sát, không biên tập), thêm Võng du (hoặc giải thích bằng HTML vì sao loại), và ghi bằng chứng cho 4 ID chưa có trích dẫn (Tranh bá, Quân sự, Huyền học, Phim ảnh).

**[MINOR] R6 — `loadMoreFilterResults()` phân nhánh theo `current.query` đang gõ, không theo chế độ của lượt tải trước**
- `[SRC] SearchViewModel.kt:134-136`: `onQueryChange` cập nhật `query` theo từng phím gõ, không chạy lại tìm kiếm. `[SRC] SearchViewModel.kt:445`: guard cũ `if (query.isNotBlank()) return` ngăn trộn kết quả.
- Với nhánh mới §3.4: đang duyệt thể loại Wikidich (không từ khóa, `canLoadMore = true`) → người dùng gõ chữ vào ô tìm kiếm nhưng chưa bấm tìm → cuộn xuống → nhánh 1 gọi `searchStories(keyword, page = 2)` và nối kết quả từ khóa trang 2 vào danh sách thể loại. Chiều ngược lại (xóa ô từ khóa sau khi tìm) nối kết quả `getCompletedStories`/`getStoriesByGenre` vào kết quả tìm kiếm.
- Khuyến nghị: lưu `lastSubmittedQuery` (hoặc enum chế độ) khi `search()`/`browseBy*()` chạy và phân nhánh theo giá trị đó.

**[MINOR] R7 — Test Contract `StoryDetailViewModelTest` kiểm sai điểm gọi**
- `[SRC] StoryDetailViewModel.kt:295-309`: `performDownload` gọi `DownloadService.startDownload(context, storyId, storyTitle, chapterNumbers: IntArray)`, không gọi `storyRepository.downloadChapters`. `[SRC] StoryDetailViewModelTest.kt:176-179, 229`: test hiện có dùng `mockkObject(DownloadService.Companion)` và verify `startDownload`. Contract nên verify `chapterNumbers` (`intArrayOf(2)`) qua `startDownload`.

**[MINOR] R8 — Test `observeActiveSource and setActiveSource…` cần DataStore thật hoặc fake**
- `[SRC] StoryRepositoryTest.kt:63-65`: `dataStore = mockk(relaxed = true)` với `data = flowOf(emptyPreferences())`. `setActiveSource` ghi qua `edit` trên mock sẽ không làm `data` phát giá trị mới, nên kỳ vọng `"tangthuvien" → "wikidich" → "sstruyen"` không đạt nếu không nêu cách dựng DataStore (ví dụ `PreferenceDataStoreFactory.create` với thư mục tạm, hoặc fake dùng `MutableStateFlow<Preferences>`).
- Lưu ý thêm: `StoryRepositoryTest.kt:50` dùng `sourceId = "truyencom"`; các test `searchStories` (dòng 248, 397) chỉ còn chạy nhờ nhánh `.ifEmpty { sources.take(1).toSet() }`. Nên ghi rõ phụ thuộc này hoặc đổi sang `sourceId` hợp lệ.

**[MINOR] R9 — Migration chuẩn hóa nên đọc lại giá trị bên trong `edit {}`**
- `[SRC] SettingsDataStore.kt:163-167`: code hiện có kiểm lại cờ bên trong `edit`. §3.2 mô tả điều kiện theo `currentRaw` nhưng không nói đọc ở đâu. Nếu đọc từ `data.first()` rồi ghi `resolveSingleSource(currentRaw)` trong `edit`, một lần `selectSource("wikidich")` xen giữa sẽ bị ghi đè về `"tangthuvien"` (timeline T-A). Yêu cầu: tính `resolveSingleSource(prefs[ENABLED_SOURCES])` từ `prefs` trong khối `edit`.

#### Mental Simulation Matrix (S1–S6 + Domain)

| Kịch bản | Kết quả | Bằng chứng | Chi tiết |
| :--- | :---: | :--- | :--- |
| S1 Cold-start & Build | FAIL | `[CMD]` `cmd.exe /d /c gradlew.bat compileDebugKotlin` Exit 0 (baseline, `BUILD SUCCESSFUL in 4s`); `[SRC]` `ChapterDao.kt:93-100`, `SearchViewModel.kt:253-256, 295` | Baseline biên dịch được, không thêm dependency. Sketch truyền `String?` vào `title: String` (R1) và gọi `browseByGenre`/`browseByFilterOnly` sai số tham số (R2). |
| S2 Lifecycle & Teardown | PASS | `[SRC]` `DownloadRepository.kt:140, 144-145, 183, 202`; `ExploreViewModel.kt:87-89`; `SearchViewModel.kt:142, 451-452` | `retryPass` chạy trong cùng `flow {}`, có `ensureActive()` trước/sau fetch, `delay` hủy được, `fetchChapterWithRetry` rethrow `CancellationException`. Collector `collectLatest` trong `viewModelScope`. Xem timeline T-D. |
| S3 Database & Temporal (Room + DataStore) | PASS | `[SRC]` `AppDatabase.kt:22` (`version = 9`), `SettingsDataStore.kt:70, 108-110, 162-181`; `StoryRepository.kt:75-89` | Không đổi schema Room, key `enabled_sources` giữ nguyên tên; `resolveSingleSource` đọc được CSV cũ, giá trị restore và ID legacy. Hai writer (`SettingsViewModel.selectSource` và `StoryRepository.setActiveSource`) ghi cùng key qua `edit` tuần tự. TOCTOU nhỏ ở migration ghi ở R9 (MINOR). |
| S4 Cross-Task State Flow | FAIL | `[SRC]` `SearchViewModel.kt:134-136, 445, 487-490`; `SearchViewModelTest.kt:40` | `clearFilter()` làm `activeSource` lệch DataStore vĩnh viễn (R3); Test Contract Search/Explore thiếu stub `observeActiveSource()` (F6). |
| S5 Security & Privacy Boundary | PASS | `[SRC]` `WikidichScraper.kt:136-137, 564-573`; `StoryRepository.kt:357-361`; `DownloadRepository.kt:146-147, 234-243` | `search` 5 tham số giữ `checkLoginRequired`; tìm kiếm trang 2 và `retryPass` gặp trang đăng nhập đều dẫn tới `markLoginRequired`/ném `LoginRequiredException`. Không đụng `CookieVault`/Manifest/backup cookie. Xem timeline T-C. |
| S6 External Resilience | FAIL | `[URL]` WebFetch wikicv.org `/truyen/mat-the-da-vat…` (2 ID xác minh), 2 URL còn lại HTTP 500; `[SRC]` `story_detail.html:316-338`, `search_logged_in.html:336-338` | Trích đoạn fixture không khớp, Võng du bị loại dù là `genre=`, 4 ID không có bằng chứng (R5, N1). Retry/backoff và rate-limit Wikidich đúng (`DownloadRepository.kt:57-60, 276-283`). |
| Domain: Gộp nguồn / Tủ truyện | PASS | `[SRC]` `StoryRepository.kt:95-97`; `DownloadRepository.kt:71-73, 175-177` | Chi tiết/chương/tải offline vẫn định tuyến qua `sourceFor(url)` trên toàn bộ `sources`, độc lập với `activeSources()`. |
| Domain: Tải offline – tiến độ | PASS | `[SRC]` `StoryRepository.kt:826-834`; `DownloadRepositoryTest.kt:222-264` | Mốc `2_250L` tự tính lại: batch 1 = max(250, 250+500) = 750; inter-batch `currentDelay` 500 → 1_250; batch 2 thành công, không delay sau batch cuối; `retryPass` delay 250 + backoff 250+500 → 2_250. `emissions.last()` vẫn là emit cuối của batch 2 (c3 thất bại không emit) nên `current = 7, errors = 1` giữ nguyên. |
| Domain: UI tiếng Việt | PASS | `[SRC]` `SettingsDataStore.kt:29-34` | Nhãn dùng `SourceIds.SOURCE_LABELS`; chuỗi mới ("Nguồn truyện", "Lưu ý: Nguồn này chỉ lọc theo thể loại khi không nhập từ khóa", phụ đề Settings) là tiếng Việt. |

#### Adversarial Timelines

```
Cơ chế T-A (S3, TOCTOU): migration chuẩn hóa ENABLED_SOURCES song song với chọn nguồn ở Settings
T1: Bản cập nhật mới, ENABLED_SOURCES = "tangthuvien,sstruyen,wikidich", IS_WIKIDICH_MIGRATED = true. SearchViewModel.init → getGenreList() → activeSources() → migrate đọc currentRaw qua data.first()
T2: Cùng lúc người dùng ở Settings chọn "wikidich" → selectSource ghi ENABLED_SOURCES = "wikidich"
T3: migrate tiếp tục vào edit {} và ghi resolveSingleSource(currentRaw cũ) = "tangthuvien"
Kết cục: lựa chọn của người dùng bị ghi đè nếu currentRaw đọc ngoài edit; nếu tính từ prefs trong edit thì "wikidich" (không có dấu phẩy, hợp lệ) được giữ → LỖI NHẸ (MINOR R9), an toàn khi sửa theo yêu cầu
```

```
Cơ chế T-B (S4): Đổi nguồn wikidich → sstruyen khi thể loại riêng của Wikidich đang chọn, chưa nhập từ khóa
T1: SearchState: activeSource = "wikidich", selectedGenre = "lam-su-nghiep", query = "", completedOnly/maleOnly mặc định, chapterFilter = NONE
T2: selectSource("sstruyen") → DataStore → collectLatest nhận "sstruyen" → _state.activeSource = "sstruyen" → handleActiveSourceSwitched()
T3: validGenre = null, isFilterActive = false, không có từ khóa → nhánh cuối clearFilter() → _state.value = SearchState() → activeSource = "tangthuvien"
T4: distinctUntilChanged chặn phát lại "sstruyen"; FilterBottomSheet hiển thị TàngThưViện, trong khi activeSources() vẫn trả SsTruyện
Kết cục: UI và repository dùng hai nguồn khác nhau cho tới lần đổi nguồn sau → LỖI (MAJOR R3). Test Contract đổi sang "tangthuvien" không phát hiện được vì trùng giá trị mặc định
```

```
Cơ chế T-C (S5, TOCTOU): phiên Wikidich hết hạn giữa trang 1 và trang 2 của tìm kiếm từ khóa
T1: search() trang 1 "tiên" + genre tien-hiep trên wikidich thành công, canLoadMore = true
T2: Cookie hết hạn phía server
T3: loadMoreFilterResults() → searchStories(page = 2) → WikidichScraper.search → checkLoginRequired ném LoginRequiredException (WikidichScraper.kt:564-573) → StoryRepository.markLoginRequired và trả rỗng (StoryRepository.kt:357-361)
T4: merged không có mục mới → canLoadMore = false; loginRequiredSources phát "wikidich" → banner đăng nhập
Kết cục: không parse trang đăng nhập thành kết quả, không vòng lặp tải thêm → AN TOÀN
```

```
Cơ chế T-D (đồng thời / vòng đời): Hủy tải khi retryPass Wikidich đang chờ delay 15–20 s
T1: Vòng chính tải 5 chương Wikidich, chương 2 lỗi NetworkException sau 3 lần thử
T2: retryPass: delay(wikidichDelayProvider()) trước chương 2; người dùng bấm "Hủy tải" → downloadJob.cancel()
T3: delay ném CancellationException; flow kết thúc, không gọi persistDownloadedChapter
Kết cục: không có request thừa, không ghi DB → AN TOÀN
```

```
Cơ chế T-E (đồng thời): retryPass gặp 429 ở chương lỗi đầu tiên
T1: Wikidich, failedChapters = [c1, c2] (mỗi batch 1 chương, consecutiveFailedBatches = 2 < 3 nên retryPass chạy)
T2: retryPass c1 → HttpException(429) → fetchChapterWithRetry trả outcome.error ngay (DownloadRepository.kt:151-152)
T3: Sketch kiểm isWikidich && isRateLimitHttpError → throw; c2 không được gọi thêm
Kết cục: tổng số lần gọi c2 = 3 (chỉ trong vòng chính), khớp Test Contract coVerify(exactly = 3) → AN TOÀN
```

#### Commands Executed

Trích từ `evidence.md` (reviewer không tự chạy lệnh):
- `### [CMD] git status --short` — `Exit: 0` — 27 file `M` và 5 file `??`, gồm các file `[MODIFY]` của plan.
- `### [CMD] git diff --stat` — `Exit: 0` — `27 files changed, 1191 insertions(+), 314 deletions(-)`.
- `### [CMD] git log --oneline -n 20` — `Exit: 0` — HEAD `d6024c3`.
- `### [CMD] cmd.exe /d /c gradlew.bat compileDebugKotlin` — `Exit: 0` — `BUILD SUCCESSFUL in 4s`.
- `### [CMD] cmd.exe /d /c gradlew.bat ktlintCheck` — `Exit: 0` — `BUILD SUCCESSFUL in 692ms`.
- `### [CMD] cmd.exe /d /c gradlew.bat testDebugUnitTest` — `Exit: 0` — `BUILD SUCCESSFUL in 2s` (`34 actionable tasks: 34 up-to-date`).
- `### [CMD] node --test .agents/scripts/code-freeze-guard.test.js` — `Exit: 0` — `pass 36, fail 0`.
- `### [CMD] node --test .agents/scripts/plan-approval.test.js` — `Exit: 0` — `pass 13, fail 0`.
- `### [CMD] node --test .agents/scripts/review-run.test.js` — `Exit: 0` — `pass 10, fail 0`.

UNVERIFIED (đề xuất User/Planner chạy hoặc bổ sung):
- `curl.exe -q -s "https://wikicv.org/truyen/nien-dai-tu-nong-thon-den-tu-hop-vien-aI80RlS4CG9Q~mXh"` và `curl.exe -q -s "https://wikicv.org/truyen/trong-sinh-cuu-cuu-do-ta-thanh-van-lo-auB2kDu26yhcP9CV"` — WebFetch của reviewer nhận HTTP 500; cần lưu nguyên văn làm fixture.
- Trang `/truyen/<slug>` công khai có chứa `genre=` cho Tranh bá, Quân sự, Huyền học, Phim ảnh.
- `.\gradlew.bat testDebugUnitTest --tests "com.personal.apptruyen.data.remote.WikidichScraperTest"` sau khi triển khai, để xác nhận danh sách Type C ở R4.

#### Kết luận

Đã sửa: F1, F3, F4, F5, F7, F9, F10, F11, N2, N4, N5, N6, N7 (kèm regression R3), N8. Sửa một phần: F2, F6, F8, N1, N3. Phát hiện mới: 5 lỗi `[MAJOR]` (R1–R5) và 4 lỗi `[MINOR]` (R6–R9). Kịch bản S1, S4, S6 FAIL. Planner cần ra Revision 4: tách nguyên văn khối lưu chương của vòng chính vào `persistDownloadedChapter`, sửa chữ ký `browseByGenre`/`browseByFilterOnly`, giữ `activeSource` khi `clearFilter()` và đổi Test Contract sang nguồn khác mặc định, liệt kê đúng Type C của `WikidichScraperTest`, sửa trích đoạn fixture cho khớp file thật, xử lý Võng du và bổ sung bằng chứng cho 4 ID còn thiếu, thêm stub `observeActiveSource()` cho test Search/Explore.

[CHANGES_REQUESTED]

### Audit Run 4

- **Reviewer**: Claude Code CLI (Claude Opus 5.5, `claude-opus-5-5`)
- **Ngày**: 2026-10-30
- **Đối tượng**: Technical Plan Revision 4

Ghi chú: working tree vẫn có 27 file `M` và 5 file `??` (`[CMD] git status --short`, Exit 0), nên mọi `[SRC]` dưới đây là nội dung file hiện tại trên đĩa. Kết luận CHANGES_REQUESTED của Audit Run 1–3 chỉ được dùng làm danh sách cần kiểm lại; mọi trạng thái dưới đây được kiểm lại trên code thật hoặc trang `wikicv.org` công khai.

#### Kiểm Chứng Lỗi Cũ

| Mã | Trạng thái | Bằng chứng trên code thật | Ghi chú |
| :--- | :---: | :--- | :--- |
| F1 | ĐÃ SỬA | `[SRC] WikidichScraper.kt:136-137, 503-506, 564-573` | Sketch §3.3 mục 4 giữ `checkLoginRequired(doc)` ngay sau `fetchDocumentWithRetry(url)`; `enrichWithChapterCounts(filtered)` dùng default `maxItems = ENRICH_MAX_ITEMS`. |
| F2 | SỬA MỘT PHẦN | `[URL]` WebFetch 2026-10-30 các trang `/truyen/<slug>` plan trích dẫn (chi tiết ở V1); `[SRC] search_logged_in.html:312, 324, 330, 372, 378`; `story_detail.html:329` | 18/19 ObjectId đã khớp HTML thật. Còn `Khoa học viễn tưởng` (`...1a9`) không có bằng chứng — xem V1. |
| F3 | ĐÃ SỬA | `[SRC] DownloadRepository.kt:68-69, 146-153, 234-243, 276-283`; `StoryRepository.kt:826-834` | `retryPass` kiểm `outcome.error` đúng tập điều kiện của vòng chính, delay `wikidichDelayProvider()` trước mỗi chương Wikidich, emit `DownloadProgress` 4 tham số. |
| F4 | ĐÃ SỬA | `[SRC] SearchViewModel.kt:441-447`; `SearchScreen.kt:84` | Guard mới, nhánh từ khóa và điều kiện `shouldLoadMore` khớp vị trí thật. |
| F5 | ĐÃ SỬA | `[SRC] SearchViewModel.kt:153-155, 348-354` | Nhánh không phải Wikidich giữ `searchStories(query, completedOnly = false)`, `canLoadMore = false`, giữ `matchesGenre`; không sửa `TangThuVienScraper.kt`/`SsTruyenScraper.kt`. |
| F6 | ĐÃ SỬA | `[SRC] SearchViewModelTest.kt:40`, `ExploreViewModelTest.kt:35` (mock `relaxed`); `SettingsViewModelTest.kt:138-148` (mẫu `MutableStateFlow<Preferences>` + `updateData` `coAnswers` đã có sẵn) | §6 Loại B thêm `activeSourceFlow = MutableStateFlow(...)`, stub `observeActiveSource()` và `setActiveSource(any())` trong `setUp()`; Test Contract đổi nguồn dùng `wikidich → sstruyen`. |
| F7 | ĐÃ SỬA | `[SRC] SettingsDataStore.kt:108-110, 162-181`; `StoryRepository.kt:75-89` | Không đổi so với Run 3. |
| F8 | ĐÃ SỬA | `[SRC] WikidichScraper.kt:81-125` (42 mapping), `WikidichScraper.kt:370-407` (35 `Genre`), `SettingsScreen.kt:394-409`, `DownloadRepositoryTest.kt:246`, `StoryDetailViewModel.kt:263-288` | Số lượng mục/khóa và dải dòng plan nêu khớp code. Còn lệch nhỏ không ảnh hưởng triển khai (ví dụ `getGenreList` thật ở 370-407, plan ghi 372-406). |
| F9 | ĐÃ SỬA | `[SRC] StoryRepository.kt:135-139, 328-331` | §8, §9 giữ mô tả. |
| F10 | ĐÃ SỬA | `[SRC] ExploreViewModel.kt:33-44` | Bước 5.3 bọc chip "Truyện nam" theo `activeSource`. |
| F11 | ĐÃ SỬA | `[SRC] ExploreViewModel.kt:87-89`; `SearchViewModel.kt:142` | `collectLatest` + hủy job cũ. |
| N1 | SỬA MỘT PHẦN | `[URL]` như F2 | Bằng chứng HTML thật đã có cho 18 ID; trích đoạn "nguyên văn" của trang `mat-the-da-vat…` không khớp trang thật (V1). |
| N2 | ĐÃ SỬA | Grep `isDummyTitle|toEntity|triggerFilterBrowse|searchLocal` chỉ khớp trong Audit History | — |
| N3 | ĐÃ SỬA | `[SRC] DownloadRepository.kt:206-228`; `ChapterDao.kt:93-99` (`title: String`); `ChapterContentResult.kt:9` (`title: String?`) | `persistDownloadedChapter` trong §3.5 chép đúng khối 206-228: `titleToSave` là `String` (smart cast qua `!result.title.isNullOrBlank()` giống vòng chính đang biên dịch), giữ điều kiện `chapter.title.isBlank()`; `coVerify` 6 matcher. |
| N4 | ĐÃ SỬA | `[SRC] SearchViewModel.kt:58, 153-155`; `SearchViewModelTest.kt:388` | Không đổi so với Run 3. |
| N5–N8 | ĐÃ SỬA | `[SRC] SettingsViewModel.kt:95, 245-269`; `ExploreViewModel.kt:59-62`; `SearchViewModel.kt:75-84`; `WikidichScraper.kt:89-95, 111-112, 120-122` | Không đổi so với Run 3. |
| R1 | ĐÃ SỬA | `[SRC] DownloadRepository.kt:206-228`; `ChapterDao.kt:93-99` | Như N3. |
| R2 | ĐÃ SỬA | `[SRC] SearchViewModel.kt:253-256, 295` | Sketch gọi `browseByGenre(s.selectedGenre, s.chapterFilter)` trong nhánh `s.selectedGenre != null` (`s` là `val` cục bộ, `selectedGenre` là `val` của data class cùng module nên smart cast hợp lệ) và `browseByFilterOnly(s.chapterFilter)`. |
| R3 | ĐÃ SỬA | `[SRC] SearchViewModel.kt:487-490` (điểm duy nhất gán `SearchState()`) | `clearFilter()` dùng `SearchState(activeSource = _state.value.activeSource)`; Test Contract đổi sang `sstruyen` (khác `DEFAULT_SINGLE_SOURCE`). Xem timeline T-B. |
| R4 | ĐÃ SỬA | `[SRC] WikidichScraperTest.kt:211, 239-246, 251-256, 260-263, 267-282, 296, 334, 339-346` | Tên test và dòng trong Type C khớp file thật. Vì Rev 4 giữ `qs=2` cho `getStoriesByGenre`/`getCompletedStories` và giữ `q=…&qs=1` cho `search`, các assert ở 241, 246, 262, 277, 296, 334 không còn hỏng. Slug mới `lam-su-nghiep`, `quan-su`, `xay-dung` có trong map 19 mục; `removedSlugs` (dòng 342) không giao với danh sách mới. |
| R5 | SỬA MỘT PHẦN | `[SRC] story_detail.html:316-338`; `search_logged_in.html:336-338`; `[URL]` WebFetch `https://wikicv.org/truyen/trong-sinh-lam-tro-choi-ta-chi-lam-nguye-aqo2JPTaEHfVi5oA` và `…/tro-choi-buong-xuong-tho-san-tieu-thu-la-auOBP2He7AWuFBzZ` | Trích đoạn `story_detail.html` trong §3.3 mục 1.1 đã khớp file (chỉ gộp khoảng trắng). Bốn ID Tranh bá/Quân sự/Huyền học/Phim ảnh đã xác minh. Võng du có giải thích bằng HTML (hai truyện trò chơi gắn `tag=58d92bd6…c9` "Trò chơi" và `tag=69060c00…c3` "Trò chơi xâm lấn", không có `genre=…1a5`) — chấp nhận như quyết định sản phẩm, ghi chú ở V2. Còn thiếu bằng chứng cho `…1a9` (V1). |
| R6 | ĐÃ SỬA | `[SRC] SearchViewModel.kt:134-136, 445` | `lastSubmittedQuery` được gán ở `search()`, xóa ở `browseByGenre`/`browseByFilterOnly`/`clearFilter`; `loadMoreFilterResults()` phân nhánh theo biến này. |
| R7 | ĐÃ SỬA | `[SRC] StoryDetailViewModel.kt:295-309` | Test Contract verify `DownloadService.startDownload(..., match { it.contentEquals(intArrayOf(2)) })`. |
| R8 | ĐÃ SỬA | `[SRC] StoryRepositoryTest.kt:63-65`; `SettingsViewModelTest.kt:138-148` | Test Contract dựng `MutableStateFlow<Preferences>` + `coEvery { dataStore.updateData(any()) } coAnswers {…}` — cùng mẫu đã chạy được trong `SettingsViewModelTest`. |
| R9 | ĐÃ SỬA | `[SRC] SettingsDataStore.kt:163-167` | §3.2 đọc `rawInside = prefs[ENABLED_SOURCES]` bên trong `edit {}`. Xem timeline T-A. |

#### Phát Hiện Mới (Fresh Findings & Regression Check)

**[MAJOR] V1 — Trích đoạn HTML "nguyên văn" cho `Khoa học viễn tưởng` (`genre=5794f03dd7ced228f44191a9`) không tồn tại trên trang thật; ID này chưa có bằng chứng**
- §3.3 mục 1.3 khẳng định trang `https://wikicv.org/truyen/mat-the-da-vat-ta-mac-vao-nu-than-da-mat-aj6iIVS4CCixlhAF` chứa `<a href="/tim-kiem?genre=5794f03dd7ced228f44191a9&amp;qs=1">Khoa học viễn tưởng</a> - <a …>Mạt thế</a> - <a …>Sinh tồn</a>`.
- `[URL]` WebFetch trang này hai lần (2026-10-30). Dòng "Thể loại:" thật là: `Nguyên sang (official=…193), Nam sinh (gender=…196), Hiện đại (age=…19d) , HE (ending=…19f) , Mạt thế (genre=…1ac) , Sinh tồn (genre=5fd54e65…) , Hệ thống (tag=…1b6) , Thị giác nam chủ (tag=5bbd346e…)`. Chuỗi "Khoa học viễn tưởng" không xuất hiện trên trang. Audit Run 3 cũng chỉ ghi nhận Mạt thế và Sinh tồn từ trang này.
- `[SRC]` Grep `44191a9|Khoa học viễn tưởng` trên `app/src/test/resources/fixtures/wikidich/` → 0 kết quả.
- Dấu phân cách thật giữa các link là `" , "`, không phải `" - "` như mọi trích đoạn mục 1.3–1.8. Nội dung các ID ở mục 1.4–1.8 vẫn khớp (đã WebFetch: Tình cảm/Phim ảnh/Làm sự nghiệp; Tranh bá/Quan trường/Quân sự/Làm sự nghiệp; Kinh thương/Làm sự nghiệp; Tình cảm/Làm ruộng/Xây dựng; Huyền huyễn/Huyền học), nhưng các khối được trình bày là nguyên văn thực chất đã được biên tập lại.
- Hệ quả: `"khoa-hoc-vien-tuong" to "genre=5794f03dd7ced228f44191a9"` được đưa vào map, `docs/scraping.md` và test chỉ dựa trên suy luận "dãy ID liên tiếp năm 2016" — đúng kiểu lập luận đã bị bác ở N1 (ví dụ `…1ac`, `…1c0`, `…1d4` nằm trong dải tag nhưng lại là `genre=`). Nếu sai namespace, thể loại này trả rỗng hoặc sai kết quả mà test MockWebServer không phát hiện được. Vi phạm `.agents/rules/04-scraping.md:20` (phải xem HTML thật trước khi viết URL/selector).
- Yêu cầu (sửa nhỏ): (a) thay trích đoạn mục 1.3 bằng nội dung đúng của trang; (b) bổ sung một trang `/truyen/<slug>` công khai có `genre=5794f03dd7ced228f44191a9` kèm nhãn và ngày khảo sát, hoặc tạm loại `Khoa học viễn tưởng` khỏi danh sách (còn 18 mục, cập nhật `assertEquals(…)`, Wiring Probe 1, docs) cho tới khi có bằng chứng; (c) bỏ chữ "nguyên văn" hoặc chép đúng dấu phân cách.

**[MINOR] V2 — Bỏ Võng du dựa trên suy luận từ 2 mẫu**
- `[SRC] search_logged_in.html:336-338`: thanh `tag-tab` của chính wikicv.org vẫn trỏ `genre=5794f03dd7ced228f44191a5` "Võng du"; `WikidichScraper.kt:93` đang dùng ID này. Việc hai truyện trò chơi không gắn `genre=…1a5` không chứng minh ID đã ngừng hoạt động. Không chặn plan (giải thích có HTML, là quyết định phạm vi), nhưng nên ghi vào `docs/changelog.md` mục "Sửa đổi" rằng thể loại Võng du bị gỡ để người dùng đang dùng nó biết.

**[MINOR] V3 — Nhánh Wikidich trang 1 của `search()` không nói rõ có còn `applyLocalFilters(rawResults)` hay không**
- `[SRC] SearchViewModel.kt:155-156`: code hiện tại luôn `applyLocalFilters(rawResults)`. §3.4 và Bước 5.1 chỉ mô tả lời gọi repository và `canLoadMore` cho nhánh Wikidich, trong khi `loadMoreFilterResults()` nhánh 1 ghi rõ `applyLocalFilters(rawMore)`. Nếu người triển khai bỏ bước này ở trang 1, bộ lọc số chương (`chapterFilter`) bị bỏ qua ở trang 1 nhưng áp dụng ở trang 2. Nên ghi rõ "giữ `applyLocalFilters(rawResults)` cho cả hai nhánh".

**[MINOR] V4 — `maleOnly = false` bị ẩn trên nguồn khác Wikidich vẫn làm `isFilterActive = true`**
- `[SRC] SearchViewModel.kt:75-84`: `hasAnyNonDefaultFilter` tính `!maleOnly`. Người dùng tắt "Truyện nam" trên Wikidich rồi đổi sang TTV: `handleActiveSourceSwitched()` tính `isFilterActive = true`, đi nhánh `browseByFilterOnly`, trong khi chip/mục "Truyện nam" đã bị ẩn nên người dùng không thấy lý do bộ lọc đang bật. Gợi ý: bỏ qua `maleOnly` trong `hasAnyNonDefaultFilter` khi `activeSource != SourceIds.WIKIDICH`.

**[MINOR] V5 — Type C `StoryRepositoryTest` chưa nêu cách stub `ENABLED_SOURCES = "wikidich"`; mô tả "default delegation giữ mock 2 tham số" chưa chính xác**
- `[SRC] StoryRepositoryTest.kt:65`: `setUp` trả `flowOf(emptyPreferences())`; với `resolveSingleSource(null) = "tangthuvien"` và `setOf(source /*truyencom*/, wikiSource)` (dòng 930, 969), `activeSources()` rơi vào `sources.take(1)` = `source`, nên `wikiSource` không được gọi. Hai test ở dòng 908/947 cần stub `every { dataStore.data } returns flowOf(preferencesOf(SettingsKeys.ENABLED_SOURCES to "wikidich"))`; plan nên ghi rõ.
- `[SRC] StoryRepositoryTest.kt:49`: `source = mockk()` (không relaxed). MockK không chạy default method của interface, nên nếu repository gọi overload 5 tham số trên mock này sẽ ném `MockKException`. Điều giữ test `searchStories aggregates from sources` (dòng 255-257) chạy được là quy tắc định tuyến ở Bước 3.2 (gọi bản 2 tham số khi `page == 1 && maleOnly && genreSlug == null` với nguồn không phải Wikidich), không phải default delegation. Nên sửa câu giải thích để người triển khai không đổi quy tắc định tuyến.

#### Mental Simulation Matrix (S1–S6 + Domain)

| Kịch bản | Kết quả | Bằng chứng | Chi tiết |
| :--- | :---: | :--- | :--- |
| S1 Cold-start & Build | PASS | `[CMD]` `cmd.exe /d /c gradlew.bat compileDebugKotlin` Exit 0 (`BUILD SUCCESSFUL in 3s`); `[SRC]` `ChapterDao.kt:93-99`, `ChapterContentResult.kt:9`, `StoryRepository.kt:826-832`, `SearchViewModel.kt:253-256, 295`, `StorySource.kt:19-22`, `DownloadRepository.kt:17-19` | Không thêm dependency. Các ký hiệu sketch dùng đều tồn tại (`currentCoroutineContext`, `ensureActive` đã import; `DUMMY_TITLE_REGEX`, `isRateLimitHttpError`, `WIKIDICH_*`). Overload `search(keyword, completedOnly, page, …)` không mơ hồ với bản 2 tham số vì `page` không có default; override trong `WikidichScraper` không khai báo lại default. Fake `StorySource` duy nhất ngoài scraper (`LacaTruyenApiTest.kt:100`) không cần đổi nhờ default method. |
| S2 Lifecycle & Teardown | PASS | `[SRC]` `DownloadRepository.kt:140, 144-145, 183, 202`; `ExploreViewModel.kt:87-89`; `SearchViewModel.kt:142, 451-452` | `retryPass` chạy trong cùng `flow {}`, `ensureActive()` trước/sau fetch, `delay` hủy được. Collector `collectLatest` nằm trong `viewModelScope`. Xem timeline T-D. |
| S3 Database & Temporal (Room + DataStore) | PASS | `[SRC]` `AppDatabase.kt:22` (`version = 9`), `SettingsDataStore.kt:70, 108-110, 162-181` | Không đổi schema Room; key `enabled_sources` giữ tên; migration tính lại từ `prefs` trong `edit {}`; restore backup chuẩn hóa qua `resolveSingleSource`. Xem timeline T-A. |
| S4 Cross-Task State Flow | PASS | `[SRC]` `SearchViewModel.kt:48-63, 134-136, 441-490`; `SearchViewModelTest.kt:208-226, 324-347`; `ExploreViewModelTest.kt:207-208`; `SettingsViewModelTest.kt:136-170` | `activeSource` không còn bị `clearFilter()` đặt lại; `lastSubmittedQuery` tách chế độ tải thêm; các test duyệt thể loại hiện có đi nhánh 2 của `loadMoreFilterResults()` như cũ; Explore guard `previous == null` giữ `coVerify(exactly = 1)` ở dòng 207. Các điểm V3, V4 là MINOR. |
| S5 Security & Privacy Boundary | PASS | `[SRC]` `WikidichScraper.kt:136-137, 564-573`; `StoryRepository.kt:357-361`; `DownloadRepository.kt:146-147, 234-243` | `search` 5 tham số giữ `checkLoginRequired`; tìm kiếm trang 2 và `retryPass` gặp trang đăng nhập đều dẫn tới `markLoginRequired`/ném `LoginRequiredException`. Không đụng `CookieVault`/Manifest/backup cookie. Xem timeline T-C. |
| S6 External Resilience | FAIL | `[URL]` WebFetch 7 trang `wikicv.org/truyen/…` (2026-10-30); `[SRC]` `DownloadRepository.kt:57-60, 276-283`; `parseStoryList` `WikidichScraper.kt:598-607` + `search_logged_in.html:474, 502` | Rate-limit/backoff Wikidich đúng; `filterStories(completedOnly)` dựa vào `.book-publisher a[href*='status=']` có thật trong HTML tìm kiếm nên bỏ `.ifEmpty` không làm rỗng kết quả hợp lệ. FAIL do `…1a9` chưa có bằng chứng (V1). |
| Domain: Gộp nguồn / Tủ truyện | PASS | `[SRC]` `StoryRepository.kt:95-97`; `DownloadRepository.kt:71-73, 175-177` | Chi tiết/chương/tải offline định tuyến qua `sourceFor(url)` trên toàn bộ `sources`, độc lập với `activeSources()`. |
| Domain: Tải offline – tiến độ | PASS | `[SRC]` `DownloadRepositoryTest.kt:222-264, 344-360, 424-455, 458-481` | Mốc `2_250L` tự tính lại khớp (750 + 500 + 250 + 750). Test 344 vẫn dừng sau 3 batch lỗi nên không vào `retryPass`; test 424 ném 429 trước `retryPass`; test 458 vẫn `elapsed = 5_000` vì backoff lần 1 = `retryDelayMs * 1`. |
| Domain: UI tiếng Việt | PASS | `[SRC]` `SettingsDataStore.kt:29-34` | Chuỗi mới ("Nguồn truyện", "Lưu ý: Nguồn này chỉ lọc theo thể loại khi không nhập từ khóa", phụ đề Settings) là tiếng Việt, nhãn nguồn lấy từ `SOURCE_LABELS`. |

#### Adversarial Timelines

```
Cơ chế T-A (S3, TOCTOU): migration chuẩn hóa ENABLED_SOURCES xen với selectSource ở Settings
T1: ENABLED_SOURCES = "tangthuvien,sstruyen,wikidich", IS_WIKIDICH_MIGRATED = true. SearchViewModel.init → getGenreList() → activeSources() → migrate đọc data.first(): có dấu phẩy → cần edit
T2: Người dùng chọn "wikidich" ở Settings → selectSource: edit ghi ENABLED_SOURCES = "wikidich" (DataStore tuần tự hóa các edit)
T3: edit của migrate chạy sau, đọc rawInside = prefs[ENABLED_SOURCES] = "wikidich" → resolveSingleSource = "wikidich" → ghi lại cùng giá trị
Kết cục: lựa chọn của người dùng được giữ → AN TOÀN
```

```
Cơ chế T-B (S4): đổi nguồn wikidich → sstruyen khi đang chọn thể loại riêng Wikidich, chưa nhập từ khóa
T1: SearchState activeSource = "wikidich", selectedGenre = lam-su-nghiep, query = "", các bộ lọc khác mặc định
T2: selectSource("sstruyen") → setActiveSource → collectLatest nhận "sstruyen" (previous = "wikidich") → handleActiveSourceSwitched()
T3: newGenres của SsTruyện không có lam-su-nghiep → validGenre = null, isFilterActive = false → nhánh cuối clearFilter() → SearchState(activeSource = "sstruyen")
Kết cục: UI và activeSources() cùng là sstruyen, selectedGenre = null → AN TOÀN (khớp Test Contract)
```

```
Cơ chế T-C (S5, TOCTOU): phiên Wikidich hết hạn giữa trang 1 và trang 2 tìm kiếm từ khóa
T1: search() "tiên" + tien-hiep trên wikidich thành công, lastSubmittedQuery = "tiên", canLoadMore = true
T2: Cookie hết hạn phía server
T3: loadMoreFilterResults() nhánh 1 → searchStories(page = 2) → WikidichScraper.search → checkLoginRequired ném LoginRequiredException → StoryRepository.markLoginRequired, trả rỗng (StoryRepository.kt:357-361)
Kết cục: không parse trang đăng nhập thành kết quả, canLoadMore = false, banner đăng nhập hiện → AN TOÀN
```

```
Cơ chế T-D (đồng thời / vòng đời): hủy tải khi retryPass Wikidich đang chờ delay 15–20 s
T1: Vòng chính tải 5 chương Wikidich, chương 2 lỗi NetworkException sau 3 lần thử (5 s + 10 s backoff)
T2: retryPass: delay(wikidichDelayProvider()) trước chương 2; người dùng bấm "Hủy tải"
T3: delay ném CancellationException; flow kết thúc, không gọi persistDownloadedChapter
Kết cục: không có request thừa, không ghi DB → AN TOÀN
```

```
Cơ chế T-E (đồng thời): đổi nguồn ở Search khi đang tải trang 2 của tìm kiếm Wikidich
T1: loadMoreFilterResults() khởi chạy searchJob trang 2 (SearchViewModel.kt:451-452)
T2: Người dùng chọn tangthuvien → collectLatest → handleActiveSourceSwitched → query không rỗng, hasSearched → search() → searchJob?.cancel() hủy trang 2
T3: search() nhánh không phải Wikidich: lastSubmittedQuery = query, canLoadMore = false, kết quả TTV
Kết cục: không trộn trang 2 Wikidich vào kết quả TTV, không tải thêm trên TTV → AN TOÀN
```

#### Commands Executed

Trích từ `evidence.md` (reviewer không tự chạy lệnh):
- `### [CMD] git status --short` — `Exit: 0` — 27 file `M` và 5 file `??`, gồm các file `[MODIFY]` của plan.
- `### [CMD] git diff --stat` — `Exit: 0` — `27 files changed, 1191 insertions(+), 314 deletions(-)`.
- `### [CMD] git log --oneline -n 20` — `Exit: 0` — HEAD `d6024c3`.
- `### [CMD] cmd.exe /d /c gradlew.bat compileDebugKotlin` — `Exit: 0` — `BUILD SUCCESSFUL in 3s`.
- `### [CMD] cmd.exe /d /c gradlew.bat ktlintCheck` — `Exit: 0` — `BUILD SUCCESSFUL in 709ms`.
- `### [CMD] cmd.exe /d /c gradlew.bat testDebugUnitTest` — `Exit: 0` — `BUILD SUCCESSFUL in 2s` (`34 actionable tasks: 34 up-to-date`).
- `### [CMD] node --test .agents/scripts/code-freeze-guard.test.js` — `Exit: 0` — `pass 36, fail 0`.
- `### [CMD] node --test .agents/scripts/plan-approval.test.js` — `Exit: 0` — `pass 13, fail 0`.
- `### [CMD] node --test .agents/scripts/review-run.test.js` — `Exit: 0` — `pass 10, fail 0`.

UNVERIFIED (đề xuất User/Planner chạy hoặc bổ sung):
- Một trang `https://wikicv.org/truyen/<slug>` công khai có `genre=5794f03dd7ced228f44191a9` (kiểm bằng `curl.exe -q -s "<url>" | Select-String "44191a9"`).
- `.\gradlew.bat testDebugUnitTest --tests "com.personal.apptruyen.data.remote.WikidichScraperTest" --tests "com.personal.apptruyen.data.repository.DownloadRepositoryTest"` sau khi triển khai, để xác nhận danh sách Type C.

#### Kết luận

Đã sửa: F1, F3–F11, N2–N8, R1–R4, R6–R9. Sửa một phần: F2, N1, R5 (cùng một gốc: ID `Khoa học viễn tưởng` chưa có bằng chứng và trích đoạn trang `mat-the-da-vat…` không khớp trang thật). Phát hiện mới: 1 lỗi `[MAJOR]` (V1) và 4 lỗi `[MINOR]` (V2–V5). S1–S5 PASS, S6 FAIL do V1. Planner cần ra Revision 5 chỉ với phần sửa V1 (sửa trích đoạn mục 1.3, bổ sung bằng chứng cho `…1a9` hoặc tạm loại thể loại này), và nên xử lý kèm V3, V5 vì chi phí thấp.

[CHANGES_REQUESTED]

### Audit Run 5

- **Reviewer**: Claude Code CLI (Claude Opus 5.5, `claude-opus-5-5`)
- **Ngày**: 2026-10-30
- **Đối tượng**: Technical Plan Revision 5

Ghi chú: working tree vẫn có 27 file `M` và 5 file `??` (`[CMD] git status --short`, Exit 0). Grep `observeActiveSource|setActiveSource|resolveSingleSource|lastSubmittedQuery|retryPass|persistDownloadedChapter` trên `app/src` → 0 kết quả, tức chưa có phần nào của plan được triển khai; mọi `[SRC]` dưới đây là code hiện tại trên đĩa. Kết luận CHANGES_REQUESTED của Audit Run 1–4 chỉ dùng làm danh sách cần kiểm lại.

#### Kiểm Chứng Lỗi Cũ

| Mã | Trạng thái | Bằng chứng trên code thật / nguồn thật | Ghi chú |
| :--- | :---: | :--- | :--- |
| F1 | ĐÃ SỬA | `[SRC] WikidichScraper.kt:136-137, 503, 564` | Sketch §3.3 mục 4 giữ `checkLoginRequired(doc)` sau `fetchDocumentWithRetry(url)`, gọi `enrichWithChapterCounts(filtered)` (hàm thật tại dòng 503, có tham số `maxItems` mặc định). |
| F2 | ĐÃ SỬA | `[URL]` WebFetch 2026-10-30 `https://wikicv.org/truyen/ta-o-phe-tho-the-gioi-lam-di-hinh-chi-ma-auONUlS4CHBM2Y41` và `https://wikicv.org/truyen/khai-cuc-ban-di-lao-cha-cong-ty-khoa-hoc-aLkKg1S4CAJP8bAC` | Cả hai trang có `/tim-kiem?genre=5794f03dd7ced228f44191a9&qs=1` nhãn "Khoa học viễn tưởng". Trang 1 còn có `genre=…1ac` (Mạt thế), `genre=5fd54e65…` (Sinh tồn); trang 2 có `genre=…1d4` (Kinh thương), `genre=6785c24e…` (Làm sự nghiệp) — khớp trích đoạn §3.3 mục 1.3. Đủ bằng chứng cho 19 ID. |
| F3 | ĐÃ SỬA | `[SRC] DownloadRepository.kt:68-69, 127-161, 234-243, 276-283`; `StoryRepository.kt:826-834` | Không đổi so với Run 4. |
| F4 | ĐÃ SỬA | `[SRC] SearchViewModel.kt:441-447`; `SearchScreen.kt:84` | Guard và điều kiện `shouldLoadMore` plan nêu khớp vị trí thật. |
| F5 | ĐÃ SỬA | `[SRC] SearchViewModel.kt:153-156, 348-354` | Nhánh không phải Wikidich giữ `searchStories(query, completedOnly = false)` và `matchesGenre`. |
| F6 | ĐÃ SỬA | `[SRC] SearchViewModelTest.kt:40`; `SettingsViewModelTest.kt:136-170`; `DownloadRepositoryTest.kt:246` | Không đổi so với Run 4. |
| F7 | ĐÃ SỬA | `[SRC] SettingsDataStore.kt:108-110, 162-181`; `StoryRepository.kt:75-89` | — |
| F8 | ĐÃ SỬA | `[SRC] WikidichScraper.kt:81-125, 370-407`; `StoryDetailViewModel.kt:263-288` | — |
| F9–F11 | ĐÃ SỬA | `[SRC] StoryRepository.kt:135-139, 328-331`; `ExploreViewModel.kt:33-44, 87-89` | — |
| N1 | ĐÃ SỬA | `[URL]` như F2 | Trích đoạn mục 1.3 cho trang `mat-the-da-vat…` chỉ còn Mạt thế/Sinh tồn (khớp Run 4); ID `…1a9` lấy từ hai trang khác đã xác minh. |
| N2–N8 | ĐÃ SỬA | `[SRC] SettingsViewModel.kt:95, 245-269`; `ExploreViewModel.kt:59-62`; `SearchViewModel.kt:75-84` | — |
| R1–R4, R6–R9 | ĐÃ SỬA | `[SRC] DownloadRepository.kt:206-228`; `SearchViewModel.kt:253, 295, 487-490`; `WikidichScraperTest.kt:239-254, 272-278, 339-346`; `SettingsDataStore.kt:163-167` | Type C của `WikidichScraperTest` khớp đúng các dòng 239, 244, 251, 254, 272, 278, 343. |
| R5 | ĐÃ SỬA | `[SRC] story_detail.html:316-338`; `[URL]` như F2 | Phần còn thiếu ở Run 4 (`…1a9`) đã có bằng chứng. |
| V1 | ĐÃ SỬA | `[URL]` như F2 | Trích đoạn được trình bày theo từng link, không còn dấu phân cách `" - "` sai. |
| V2 | ĐÃ SỬA | (plan §9, `docs/changelog.md`) | Changelog ghi gỡ `Võng du`. Là mục tài liệu, chỉ kiểm được khi triển khai. |
| V3 | ĐÃ SỬA | `[SRC] SearchViewModel.kt:155-156` | §3.4 và Bước 5.1 ghi rõ giữ `applyLocalFilters(rawResults)` ở cả hai nhánh. |
| V4 | ĐÃ SỬA (thiết kế) | `[SRC] SearchViewModel.kt:75-84` | `hasAnyNonDefaultFilter` nhận `activeSource` và chỉ tính `!maleOnly` khi là Wikidich. Bản sửa này gây regression test — xem W1. |
| V5 | ĐÃ SỬA | `[SRC] StoryRepositoryTest.kt:49, 255-257, 399-402` | Bước 3.2 giải thích quy tắc định tuyến 2 tham số/5 tham số đúng với mock không relaxed. |

#### Phát Hiện Mới (Fresh Findings & Regression Check)

**[MAJOR] W1 — Bản sửa V4 làm hỏng 2 test hiện có của `SearchViewModelTest` không có trong danh sách Type C (vi phạm Test Failure Triage)**
- §6 Loại B đặt `activeSourceFlow = MutableStateFlow(SourceIds.DEFAULT_SINGLE_SOURCE)` = `"tangthuvien"`, và `SearchState.activeSource` mặc định cũng là `"tangthuvien"`. Với `hasAnyNonDefaultFilter(..., activeSource = _state.value.activeSource)` mới, `maleOnly = false` không còn làm bộ lọc bật trên TTV.
- `[SRC] SearchViewModelTest.kt:303-321` (`browseByFilterOnly forwards maleOnly to repository`): `applyAllFilters(null, NONE, completedOnly = true, maleOnly = false)` → `hasAnyFilter = false`; `SearchViewModel.kt:217-225`: không có từ khóa, `genre == null`, `hasAnyFilter == false`, `hasSearched == false` → không gọi gì. `assertEquals(1, results.size)` (dòng 319) và `coVerify { getCompletedStories(1, false) }` (dòng 320) sẽ hỏng.
- `[SRC] SearchViewModelTest.kt:250-280` (`toggleCompletedFilter does not reset maleOnly when maleOnly is false`): sau `toggleCompletedFilter()`, `isFilterActive` tính lại = `false` (genre null, NONE, completedOnly true, maleOnly bị bỏ qua) → `SearchViewModel.kt:396-405` đi nhánh `clearFilter()` → `SearchState(activeSource = …)` đặt `maleOnly = true`, `hasSearched = false`. Các assert dòng 276 (`assertFalse(maleOnly)`), 277 (`assertTrue(isFilterActive)`), 278 (`assertTrue(hasSearched)`) hỏng; `coVerify` dòng 279 cũng hỏng vì `clearMocks(repository, answers = false)` ở dòng 270 đã xóa lần gọi trước.
- `[SRC] SearchViewModelTest.kt:228-248` (`toggleMaleFilter…`) không hỏng vì `OVER_200` vẫn giữ `isFilterActive = true`; `SearchViewModelTest.kt:282-301, 323-347` không hỏng vì có `genre`.
- Hai test hỏng mô tả hành vi "Truyện nam" — tính năng chỉ có trên Wikidich sau thay đổi này. Yêu cầu: thêm vào §6 (Type B/C) việc đặt `activeSourceFlow.value = SourceIds.WIKIDICH` trước khi tạo `SearchViewModel` trong 2 test này (giữ nguyên các assert), kèm lý do. Nên rà lại toàn bộ test trong `SearchViewModelTest` gọi `applyAllFilters`/`toggleMaleFilter` với `maleOnly = false` khi viết danh sách.

**[MINOR] W2 — Sketch `filterStories` trong `WikidichScraper` dùng `Normalizer` và `Locale` chưa được import**
- `[SRC] WikidichScraper.kt:3-24`: không có `import java.text.Normalizer` hay `import java.util.Locale`. Sketch §3.3 mục 3 dùng tên ngắn. Lỗi biên dịch dễ phát hiện ở Tier 1, nhưng nên ghi rõ import (hoặc dùng tên đầy đủ như `SearchViewModel.kt:366-367`).

**[MINOR] W3 — Nhánh 3 của `loadMoreFilterResults()` là hành vi mới, không phải "giữ nguyên"**
- `[SRC] SearchViewModel.kt:447`: hàm hiện tại `return` khi `selectedGenre == null`, nên chưa có lời gọi `getCompletedStories(page = nextPage)` nào trong `loadMoreFilterResults()`. Trong khi đó `browseByFilterOnly` (dòng 320) đặt `canLoadMore = true`. Plan §3.4 mục 3 viết "giữ nguyên lời gọi `repository.getCompletedStories(page = nextPage, …)`" — thực chất là thêm tính năng tải thêm cho chế độ chỉ lọc. Hành vi hợp lý, nhưng cần ghi đúng là hành vi mới và thêm 1 dòng Test Contract (trang 2 của `browseByFilterOnly` gọi `getCompletedStories(2, maleOnly)` và nối kết quả).

#### Mental Simulation Matrix (S1–S6 + Domain)

| Kịch bản | Kết quả | Bằng chứng | Chi tiết |
| :--- | :---: | :--- | :--- |
| S1 Cold-start & Build | PASS | `[CMD]` `cmd.exe /d /c gradlew.bat compileDebugKotlin` Exit 0 (`BUILD SUCCESSFUL in 4s`); `[SRC]` `WikidichScraper.kt:56-58` (`SEARCH_PAGE_SIZE`, `MALE_GENDER_PARAM`, `COMPLETED_STATUS_PARAM` có thật), `StorySource.kt` overload không mơ hồ, `DownloadRepository.kt:61, 64-69` | Ký hiệu sketch dùng đều tồn tại; thiếu import ở W2 (MINOR). Gọi `repository.searchStories(query, completedOnly = false)` sau khi thêm tham số mặc định vẫn khớp stub `searchStories(any(), any())` của `SearchViewModelTest.kt:60, 81, 388` (MockK điền giá trị mặc định 1/true/null cho cả hai phía). |
| S2 Lifecycle & Teardown | PASS | `[SRC]` `DownloadRepository.kt:140, 144-145, 183, 202`; `ExploreViewModel.kt:87-89`; `SearchViewModel.kt:142, 451-452` | `retryPass` chạy trong cùng `flow {}`, `delay`/`ensureActive` hủy được; `collectLatest` trong `viewModelScope`; `search()`/`loadFirstPage()` hủy job cũ. Test hủy giữa chừng `DownloadRepositoryTest.kt:285-307` vẫn đúng vì `delay(backoff)` ném `CancellationException`. Xem T-B. |
| S3 Database & Temporal (Room + DataStore) | PASS | `[SRC]` `AppDatabase.kt:22`; `SettingsDataStore.kt:70, 108-110, 162-181`; `StoryRepository.kt:274-281` | Không đổi schema. Migration đọc `prefs` trong `edit {}`. Cache thể loại khóa theo `cachedGenreSourceIds == activeIds` (dòng 280) nên đổi nguồn không trả nhầm thể loại cũ. Xem T-A. |
| S4 Cross-Task State Flow | PASS | `[SRC]` `SearchViewModel.kt:75-84, 217-225, 396-405, 441-490`; `ExploreViewModel.kt:59-62` | Luồng runtime đúng: ẩn "Truyện nam" trên TTV/SsTruyện và không tính nó vào `isFilterActive`. Regression nằm ở bộ test (W1, MAJOR theo Test Failure Triage), không ở hành vi app. |
| S5 Security & Privacy Boundary | PASS | `[SRC]` `WikidichScraper.kt:136-137, 564-573`; `StoryRepository.kt:357-361`; `DownloadRepository.kt:146-147` | `search` 5 tham số giữ `checkLoginRequired`; trang đăng nhập dẫn tới `markLoginRequired`. Không đụng `CookieVault`/Manifest. Xem T-C. |
| S6 External Resilience | PASS | `[URL]` 2 trang wikicv.org (F2); `[SRC]` `DownloadRepository.kt:57-60, 276-283`; `WikidichScraper.kt:590-604` | 19 ID có bằng chứng HTML. `filterStories(maleOnly)` dựa trên `.book-gender` (dòng 590-596) — đúng nguồn nhãn giới tính. Rate-limit `retryPass` Wikidich tôn trọng 15–20 s. |
| Domain: Gộp nguồn / Tủ truyện | PASS | `[SRC]` `StoryRepository.kt:95-97`; `DownloadRepository.kt:71-73, 175-177` | Đọc/tải định tuyến qua `sourceFor(url)` trên toàn bộ `sources`. |
| Domain: Tải offline – tiến độ | PASS | `[SRC]` `DownloadRepositoryTest.kt:222-264, 310-341, 344-360, 386-417, 458-478` | Mốc `2_250L` tự tính lại khớp. Test 310 ném `LoginRequiredException` ở vòng chính trước `retryPass`; test 344 dừng sau 3 batch lỗi; test 386 không có chương lỗi. |
| Domain: UI tiếng Việt | PASS | `[SRC]` `SettingsDataStore.kt:29-34` | Chuỗi mới đều tiếng Việt, nhãn nguồn lấy từ `SOURCE_LABELS`. |

#### Adversarial Timelines

```
Cơ chế T-A (S3, TOCTOU): người dùng mới cài app, Explore và Search cùng khởi tạo
T1: ExploreViewModel.init gọi loadGenres() và loadFirstPage() song song → hai lần activeSources() → hai lần migrate
T2: Lần migrate 1: ENABLED_SOURCES = null → edit ghi resolveSingleSource(null) = "tangthuvien", IS_WIKIDICH_MIGRATED = true
T3: Lần migrate 2: edit (tuần tự hóa) đọc rawInside = "tangthuvien" → ghi lại cùng giá trị
T4: observeActiveSource: resolve(null) = "tangthuvien" rồi resolve("tangthuvien") = "tangthuvien" → distinctUntilChanged chặn phát xạ trùng
Kết cục: không có lần chuyển nguồn giả, không gọi handleActiveSourceSwitched() → AN TOÀN
```

```
Cơ chế T-B (S2/S4, đồng thời): đổi nguồn ở Settings khi Search vẫn nằm trong back stack
T1: SearchViewModel đang ở wikidich, đã tìm "tiên" (hasSearched = true)
T2: Người dùng mở Settings, chọn sstruyen → selectSource ghi ENABLED_SOURCES = "sstruyen"
T3: Collector của SearchViewModel (viewModelScope còn sống) nhận "sstruyen" → handleActiveSourceSwitched → getGenreList (cache khóa theo activeIds nên tải mới) → search() nhánh không phải Wikidich, searchJob cũ bị hủy
T4: Người dùng quay lại Search: kết quả đã là SsTruyện, activeSource = "sstruyen", canLoadMore = false
Kết cục: không trộn kết quả Wikidich với SsTruyện → AN TOÀN
```

```
Cơ chế T-C (S5, TOCTOU): phiên Wikidich hết hạn giữa trang 1 và trang 2 tìm kiếm từ khóa
T1: search() "tiên" + tien-hiep thành công, lastSubmittedQuery = "tiên", canLoadMore = true
T2: Cookie hết hạn phía server
T3: loadMoreFilterResults nhánh 1 → searchStories(page = 2) → WikidichScraper.search → checkLoginRequired ném LoginRequiredException → StoryRepository.kt:359-361 markLoginRequired, trả rỗng
Kết cục: không parse trang đăng nhập thành kết quả, canLoadMore = false, banner đăng nhập hiện → AN TOÀN
```

```
Cơ chế T-D (S5/S6, rate-limit): retryPass Wikidich gặp 429
T1: Vòng chính: c1, c2 (batch size 1) đều lỗi NetworkException sau 3 lần thử; consecutiveFailedBatches = 2 < 3
T2: retryPass: delay(wikidichDelayProvider()) → fetchChapterWithRetry(c1) nhận HttpException(429) → trả ngay outcome.error (DownloadRepository.kt:151-152)
T3: fatalRetryError khớp isWikidich && isRateLimitHttpError → throw
Kết cục: c2 không bị gọi thêm trong retryPass → AN TOÀN (khớp Test Contract)
```

#### Commands Executed

Trích từ `evidence.md` (reviewer không tự chạy lệnh):
- `### [CMD] git status --short` — `Exit: 0` — 27 file `M`, 5 file `??`.
- `### [CMD] git diff --stat` — `Exit: 0` — `27 files changed, 1191 insertions(+), 314 deletions(-)`.
- `### [CMD] git log --oneline -n 20` — `Exit: 0` — HEAD `d6024c3`.
- `### [CMD] cmd.exe /d /c gradlew.bat compileDebugKotlin` — `Exit: 0` — `BUILD SUCCESSFUL in 4s`.
- `### [CMD] cmd.exe /d /c gradlew.bat ktlintCheck` — `Exit: 0` — `BUILD SUCCESSFUL in 837ms`.
- `### [CMD] cmd.exe /d /c gradlew.bat testDebugUnitTest` — `Exit: 0` — `BUILD SUCCESSFUL in 2s` (`34 actionable tasks: 34 up-to-date`, baseline trước khi triển khai).
- `### [CMD] node --test .agents/scripts/code-freeze-guard.test.js` — `Exit: 0` — `pass 36, fail 0`.
- `### [CMD] node --test .agents/scripts/plan-approval.test.js` — `Exit: 0` — `pass 13, fail 0`.
- `### [CMD] node --test .agents/scripts/review-run.test.js` — `Exit: 0` — `pass 10, fail 0`.

UNVERIFIED (đề xuất chạy sau khi triển khai):
- `.\gradlew.bat testDebugUnitTest --tests "com.personal.apptruyen.ui.search.SearchViewModelTest"` — để xác nhận W1 đã được xử lý.

#### Các Tấn Công Đã Thử (Attempted Attack Vectors)

1. Cache thể loại trả nhầm danh sách của nguồn cũ sau khi đổi nguồn → không thành: `[SRC] StoryRepository.kt:277-281` chỉ dùng cache khi `cachedGenreSourceIds == activeIds`.
2. Tham số mặc định mới của `IStoryRepository.searchStories` làm stub MockK 2 tham số không khớp → không thành: lời gọi và stub cùng đi qua giá trị mặc định `page = 1, maleOnly = true, genreSlug = null` (`SearchViewModelTest.kt:60, 81, 388`; `StoryRepositoryTest.kt:255-257`).
3. Bỏ `.ifEmpty { stories }` khi `completedOnly` làm rỗng kết quả hợp lệ → không thành: `parseStoryList` đọc trạng thái từ `.book-publisher a[href*='status=']` (`WikidichScraper.kt:598-604`); không có test 2 tham số nào gọi `search(…, true)` (Grep `\.search\(` trong `WikidichScraperTest.kt` chỉ có `false`).
4. Tăng `MAX_RETRY_ATTEMPTS` làm hỏng test hủy tải bọc `CancellationException` → không thành: `DownloadRepositoryTest.kt:285-307`, `delay(backoff)` sau khi job bị hủy ném `CancellationException`.

#### Kết luận

Đã sửa: toàn bộ lỗi Run 1–4 (F1–F11, N1–N8, R1–R9, V1–V5); bằng chứng cho `Khoa học viễn tưởng` (`…1a9`) đã kiểm trên 2 trang wikicv.org thật. Phát hiện mới: 1 lỗi `[MAJOR]` (W1 — bản sửa V4 làm hỏng 2 test `SearchViewModelTest` ngoài danh sách Type C) và 2 lỗi `[MINOR]` (W2 import, W3 mô tả nhánh 3 của `loadMoreFilterResults`). S1–S6 PASS. Planner cần ra Revision 6 bổ sung W1 vào §6 (đặt `activeSourceFlow.value = SourceIds.WIKIDICH` trong 2 test dòng 250 và 303, giữ nguyên các assert); nên xử lý kèm W2, W3 vì chi phí thấp.

[CHANGES_REQUESTED]

### Audit Run 6

- **Reviewer**: Claude Code CLI (Claude Opus 5.5, `claude-opus-5-5`)
- **Ngày**: 2026-10-30
- **Đối tượng**: Technical Plan Revision 6

Ghi chú: working tree vẫn có 27 file `M` và 5 file `??` (`[CMD] git status --short`, Exit 0); plan chưa được triển khai (`SearchViewModel.kt` vẫn chưa có `activeSource`/`lastSubmittedQuery`, `DownloadRepository.kt:50` vẫn là `MAX_RETRY_ATTEMPTS = 2`). Mọi `[SRC]` dưới đây là code hiện tại trên đĩa. Kết luận CHANGES_REQUESTED của Audit Run 1–5 chỉ được dùng làm danh sách cần kiểm lại.

#### Kiểm Chứng Lỗi Cũ

| Mã | Trạng thái | Bằng chứng trên code thật | Ghi chú |
| :--- | :---: | :--- | :--- |
| F1 | ĐÃ SỬA | `[SRC] WikidichScraper.kt:136-137` (`fetchDocumentWithRetry` → `checkLoginRequired`), `WikidichScraper.kt:503-506` (`enrichWithChapterCounts(stories, maxItems = ENRICH_MAX_ITEMS)`), `WikidichScraper.kt:564-573` | Sketch §3.3 mục 4 giữ `checkLoginRequired(doc)` ngay sau fetch và gọi đúng hàm enrich có thật. |
| F2, N1, R5, V1 | ĐÃ SỬA | `[SRC] story_detail.html:316-338`, `search_logged_in.html:312-378`; `[URL]` đã kiểm ở Run 4–5 cho 19 ID | Không có thay đổi mới ở §3.3 mục 1 so với Rev 5; trích đoạn trang `mat-the-da-vat…` chỉ còn Mạt thế/Sinh tồn. |
| F3 | ĐÃ SỬA | `[SRC] DownloadRepository.kt:68-69, 146-153, 234-243, 276-283`; `StoryRepository.kt:826-834` | `retryPass` kiểm `outcome.error` với đúng tập điều kiện `fatalError` của vòng chính, delay `wikidichDelayProvider()` trước mỗi chương Wikidich, emit `DownloadProgress` 4 tham số. |
| F4, R6 | ĐÃ SỬA | `[SRC] SearchViewModel.kt:134-136, 441-447`; `SearchScreen.kt:84` | Guard mới, `lastSubmittedQuery` và điều kiện `shouldLoadMore` khớp vị trí thật. |
| F5, N4 | ĐÃ SỬA | `[SRC] SearchViewModel.kt:153-156, 348-354`; `SearchViewModelTest.kt:388` | Nhánh không phải Wikidich giữ `searchStories(query, completedOnly = false)`, `canLoadMore = false`, `matchesGenre`. |
| F6, R8 | ĐÃ SỬA | `[SRC] SearchViewModelTest.kt:24, 40`; `ExploreViewModelTest.kt:20, 35`; `SettingsViewModelTest.kt:138-148` | Loại B stub `observeActiveSource()`/`setActiveSource(any())` bằng `MutableStateFlow`; cả hai test VM dùng `UnconfinedTestDispatcher` nên phát xạ đầu chạy ngay trong `init`. Chi tiết thu thập phát xạ cho `StoryRepositoryTest` ở X1 (MINOR). |
| F7, R9 | ĐÃ SỬA | `[SRC] SettingsDataStore.kt:108-110, 162-181`; `StoryRepository.kt:75-89` | Migration đọc `prefs[ENABLED_SOURCES]` trong `edit {}`; restore backup chuẩn hóa qua `resolveSingleSource`. |
| F8 | ĐÃ SỬA | `[SRC] WikidichScraper.kt:81-125, 370-407`; `StoryDetailViewModel.kt:263-288` | — |
| F9, F10, F11 | ĐÃ SỬA | `[SRC] StoryRepository.kt:135-139, 328-331`; `ExploreViewModel.kt:33-44, 87-89` | — |
| N2, N3, R1 | ĐÃ SỬA | `[SRC] DownloadRepository.kt:206-228`; `ChapterDao.kt:93-100` | `persistDownloadedChapter` chép đúng khối 206-228 (`titleToSave: String`, giữ `chapter.title.isBlank()`, smart cast `result.title` như vòng chính). |
| N5–N8 | ĐÃ SỬA | `[SRC] SettingsViewModel.kt:95, 245-269`; `ExploreViewModel.kt:59-62`; `SearchViewModel.kt:75-84` | — |
| R2 | ĐÃ SỬA | `[SRC] SearchViewModel.kt:253-256, 295` | Sketch gọi `browseByGenre(s.selectedGenre, s.chapterFilter)` và `browseByFilterOnly(s.chapterFilter)`. |
| R3 | ĐÃ SỬA | `[SRC] SearchViewModel.kt:487-490` | `clearFilter()` giữ `activeSource`. |
| R4 | ĐÃ SỬA | `[SRC] WikidichScraperTest.kt:211-282, 339-346` | Type C khớp tên test và dòng thật; `qs=2` giữ nguyên nên assert 241, 246, 262, 277, 296, 334 không đổi. |
| R7 | ĐÃ SỬA | `[SRC] StoryDetailViewModel.kt:295-309`; `StoryDetailViewModelTest.kt:176-179, 229` | Contract verify `DownloadService.startDownload(..., intArrayOf(2))` qua `mockkObject(DownloadService.Companion)` đã có sẵn. Với code hiện tại (`StoryDetailViewModel.kt:264-267`, không sort) `downloadChapters(1)` sẽ chọn Chương 5 → test phát hiện được lỗi. |
| V2–V5 | ĐÃ SỬA | `[SRC] SearchViewModel.kt:75-84, 155-156`; `StoryRepositoryTest.kt:49, 65` | Không đổi so với Run 5. |
| W1 | ĐÃ SỬA | `[SRC] SearchViewModelTest.kt:24` (`UnconfinedTestDispatcher`), `SearchViewModelTest.kt:250-280, 303-321`; `SearchViewModel.kt:205, 217-225, 374-407` | §6 Loại C mục 5 đặt `activeSourceFlow.value = SourceIds.WIKIDICH` trước khi tạo VM ở đúng 2 test. Mô phỏng: phát xạ đầu `"wikidich"` được collector nhận đồng bộ trong `init` (Unconfined) → `activeSource = "wikidich"` trước `applyAllFilters`. Test 303: `hasAnyNonDefaultFilter(null, NONE, true, false, "wikidich") = true` → `browseByFilterOnly` → `getCompletedStories(1, false)` → assert 319-320 đạt. Test 250: sau `clearMocks(repository, answers = false)` (giữ stub `observeActiveSource`), `toggleCompletedFilter()` tính `isFilterActive = true` (do `!maleOnly` trên Wikidich) → nhánh `browseByFilterOnly` (dòng 401-402) → assert 275-279 đạt. Rà các test còn lại có `maleOnly = false`: 228-248 có `OVER_200`, 282-301 và 323-347 có `genre` → không đổi, khớp ghi chú của plan. |
| W2 | ĐÃ SỬA | `[SRC] WikidichScraper.kt:3-24` (chưa có `java.text.Normalizer`, `java.util.Locale`) | §3.3 mục 3 và Bước 2 ghi rõ bổ sung hai import. |
| W3 | ĐÃ SỬA | `[SRC] SearchViewModel.kt:447` (`selectedGenre ?: return`), `SearchViewModel.kt:320` (`browseByFilterOnly` đặt `canLoadMore = true`) | §3.4 mục 3 ghi đúng là hành vi mới; Test Contract có dòng `loadMoreFilterResults in browseByFilterOnly mode…` gọi `getCompletedStories(page = 2, maleOnly = false)`. |

#### Phát Hiện Mới (Fresh Findings & Regression Check)

**[MINOR] X1 — Test Contract `observeActiveSource and setActiveSource…` chưa nêu cách thu phát xạ từ `StateFlow` (dễ bị conflate)**
- `[SRC] StoryRepositoryTest.kt` không dùng `UnconfinedTestDispatcher`/`backgroundScope` (Grep `Dispatcher|backgroundScope` → 0 kết quả liên quan), tức `runTest` mặc định `StandardTestDispatcher`. Nguồn phát là `MutableStateFlow<Preferences>` → `map` → `distinctUntilChanged`; nếu test gọi `setActiveSource("wikidich")` rồi `setActiveSource("sstruyen")` liền nhau mà collector chưa được chạy, `StateFlow` sẽ gộp và collector chỉ thấy `"tangthuvien"` → `"sstruyen"`.
- Khuyến nghị khi viết test: thu bằng `backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.observeActiveSource().toList(emitted) }` hoặc gọi `runCurrent()` sau mỗi `setActiveSource`. Không ảnh hưởng hành vi app.

**[MINOR] X2 — `maleOnly = false` trên Explore được giữ âm thầm khi chuyển sang nguồn khác Wikidich**
- `[SRC] ExploreViewModel.kt:82-85, 93, 105-108`: `maleOnly` là state riêng, luôn được truyền xuống `getCompletedStories`/`getStoriesByGenre`. `[SRC] TangThuVienScraper.kt:378, 393`, `SsTruyenScraper.kt:715, 732`: tham số `maleOnly` chỉ được khai báo, không được dùng → không sai kết quả trên TTV/SsTruyện. Khi người dùng quay lại Wikidich, chip "Truyện nam" hiện lại đúng giá trị cũ (`false`), nên hành vi nhất quán. Chỉ ghi nhận để `docs/architecture.md` mô tả rõ "Truyện nam là trạng thái riêng của Wikidich, được giữ qua lần đổi nguồn".

#### Mental Simulation Matrix (S1–S6 + Domain)

| Kịch bản | Kết quả | Bằng chứng | Chi tiết |
| :--- | :---: | :--- | :--- |
| S1 Cold-start & Build | PASS | `[CMD]` `cmd.exe /d /c gradlew.bat compileDebugKotlin` Exit 0 (`BUILD SUCCESSFUL in 3s`); `[SRC]` `StorySource.kt:19-22`, `WikidichScraper.kt:3-24, 56-58, 503-506`, `DownloadRepository.kt:17-19, 61, 68-69`, `ChapterDao.kt:93-100`, `SearchViewModel.kt:253-256, 295` | Không thêm dependency. Mọi ký hiệu sketch dùng đều có thật hoặc được plan khai báo (import `Normalizer`/`Locale`). Overload `search(keyword, completedOnly, page, …)` không mơ hồ với bản 2 tham số vì `page` không có default. `GENRE_QUERY_MAP` đổi từ `val` sang `internal val` an toàn: chỉ được dùng ở `WikidichScraper.kt:355` và `WikidichScraperTest.kt:345` (cùng module). |
| S2 Lifecycle & Teardown | PASS | `[SRC]` `DownloadRepository.kt:140, 144-145, 183, 202`; `ExploreViewModel.kt:87-89`; `SearchViewModel.kt:142, 451-452`; `DownloadRepositoryTest.kt:285-307` | `retryPass` chạy trong cùng `flow {}`, `delay`/`ensureActive` hủy được; `collectLatest` nằm trong `viewModelScope`. Xem T-B. |
| S3 Database & Temporal (Room + DataStore) | PASS | `[SRC]` `AppDatabase.kt:22` (`version = 9`); `SettingsDataStore.kt:70, 108-110, 162-181`; `StoryRepository.kt:274-281` | Không đổi schema Room; key `enabled_sources` giữ tên. Migration tính lại từ `prefs` trong `edit {}`. Cache thể loại khóa theo `cachedGenreSourceIds == activeIds` nên đổi nguồn không trả nhầm thể loại cũ. Xem T-A. |
| S4 Cross-Task State Flow | PASS | `[SRC]` `SearchViewModel.kt:75-84, 205-225, 374-407, 441-490`; `SearchViewModelTest.kt:24, 179-347`; `ExploreViewModelTest.kt:189-209`; `SettingsViewModelTest.kt:136-170` | W1 đã được phân loại đúng; các test `maleOnly` còn lại không đổi. `ExploreViewModelTest.kt:207` (`coVerify(exactly = 1)`) giữ được nhờ guard `previous == null`. `SettingsViewModelTest.kt:151-163` khớp Type C mục 2 (mặc định và resolve đều là `"tangthuvien"`). |
| S5 Security & Privacy Boundary | PASS | `[SRC]` `WikidichScraper.kt:136-137, 564-573`; `StoryRepository.kt:357-361`; `DownloadRepository.kt:146-147, 234-243` | `search` 5 tham số giữ `checkLoginRequired`; trang 2 và `retryPass` gặp trang đăng nhập đều dẫn tới `markLoginRequired`/ném `LoginRequiredException`. Không đụng `CookieVault`/Manifest. Xem T-C. |
| S6 External Resilience | PASS | `[SRC]` `DownloadRepository.kt:57-60, 276-283`; `WikidichScraper.kt:503-562` | `retryPass` Wikidich chờ 15–20 s mỗi chương và dừng ngay ở 403/429/503. Trang 2 tìm kiếm Wikidich tái dùng `enrichWithChapterCounts` (tối đa 5 request chi tiết, tự dừng khi gặp 403/429/503 tại dòng 543-546) — cùng ngân sách như trang 1 hiện tại. |
| Domain: Gộp nguồn / Tủ truyện | PASS | `[SRC]` `StoryRepository.kt:95-97`; `DownloadRepository.kt:71-73, 175-177` | Chi tiết/chương/tải offline định tuyến qua `sourceFor(url)` trên toàn bộ `sources`, độc lập với `activeSources()`. |
| Domain: Tải offline – tiến độ | PASS | `[SRC]` `DownloadRepositoryTest.kt:222-264, 344-360, 386-421, 424-455, 458-481, 484-514` | Mốc `2_250L` tự tính lại khớp (batch 1 = 750; inter-batch 500; `retryPass` 250 + 250 + 500). `c3` thất bại lại trong `retryPass` không emit → `emissions.last()` vẫn `current = 7, errors = 1`. Test 344 dừng sau 3 batch lỗi nên không vào `retryPass`; test 386/484 không có chương lỗi; test 458 `elapsed = 5_000` vì backoff lần 1 = `retryDelayMs * 1`. |
| Domain: UI tiếng Việt | PASS | `[SRC]` `SettingsDataStore.kt:29-34` | Chuỗi mới đều tiếng Việt; nhãn nguồn lấy từ `SOURCE_LABELS`. |

#### Adversarial Timelines

```
Cơ chế T-A (S3, TOCTOU): migration chuẩn hóa ENABLED_SOURCES xen với selectSource ở Settings
T1: ENABLED_SOURCES = "tangthuvien,sstruyen,wikidich", IS_WIKIDICH_MIGRATED = true; ExploreViewModel.init → loadGenres()/loadFirstPage() → activeSources() → migrate thấy có dấu phẩy → vào edit
T2: Người dùng chọn "wikidich" ở Settings → selectSource: edit ghi ENABLED_SOURCES = "wikidich" (DataStore tuần tự hóa edit)
T3: edit của migrate chạy sau, đọc rawInside = "wikidich" → resolveSingleSource = "wikidich" → ghi lại cùng giá trị
Kết cục: lựa chọn của người dùng được giữ; observeActiveSource phát "tangthuvien" → "wikidich" một lần → AN TOÀN
```

```
Cơ chế T-B (S2/S5, đồng thời): hủy tải khi retryPass Wikidich đang chờ delay 15–20 s
T1: Vòng chính tải 5 chương Wikidich, chương 2 lỗi NetworkException sau 3 lần thử (backoff 5 s + 10 s)
T2: retryPass: delay(wikidichDelayProvider()) trước chương 2; người dùng bấm "Hủy tải"
T3: delay ném CancellationException; flow kết thúc, không gọi persistDownloadedChapter
Kết cục: không có request thừa, không ghi DB → AN TOÀN
```

```
Cơ chế T-C (S5, TOCTOU): phiên Wikidich hết hạn giữa trang 1 và trang 2 tìm kiếm từ khóa
T1: search() "tiên" + tien-hiep trên wikidich thành công, lastSubmittedQuery = "tiên", canLoadMore = true
T2: Cookie hết hạn phía server
T3: loadMoreFilterResults() nhánh 1 → searchStories(page = 2) → WikidichScraper.search → checkLoginRequired ném LoginRequiredException → StoryRepository.kt:359-361 markLoginRequired, trả rỗng
Kết cục: không parse trang đăng nhập thành kết quả, canLoadMore = false, banner đăng nhập hiện → AN TOÀN
```

```
Cơ chế T-D (S4, đồng thời): đổi nguồn wikidich → sstruyen trong lúc loadMore nhánh 3 (browseByFilterOnly) đang chạy
T1: activeSource = "wikidich", maleOnly = false, selectedGenre = null; loadMoreFilterResults() gán searchJob gọi getCompletedStories(page = 2, false)
T2: Người dùng chọn sstruyen → collectLatest → handleActiveSourceSwitched → isFilterActive tính lại với activeSource = "sstruyen" (maleOnly bị bỏ qua) = false; không từ khóa, selectedGenre đã null từ trước → không vào nhánh clearFilter (điều kiện current.selectedGenre != null sai)
T3: Kết quả trang 2 Wikidich vẫn có thể được nối vào results vì searchJob không bị hủy ở nhánh này
Kết cục: searchJob trang 2 nối kết quả Wikidich vào chính danh sách Wikidich cũ (không trộn hai nguồn), nhưng không có lượt tải mới cho sstruyen; UI hiển thị activeSource = "sstruyen" với danh sách Wikidich cũ cho tới khi người dùng thao tác → LỖI NHẸ hiển thị (MINOR X3)
```

Ghi chú T-D: đây là trạng thái hiển thị cũ, không trộn dữ liệu giữa hai nguồn trong cùng một lượt tải và không gửi request sai nguồn (`activeSources()` đọc DataStore tại thời điểm gọi). Khi triển khai, nên để nhánh cuối của `handleActiveSourceSwitched()` gọi `clearFilter()` cả khi `s.hasSearched && !s.isFilterActive` (không chỉ khi thể loại bị reset), để danh sách Wikidich không còn hiển thị dưới chip "SsTruyện". Ghi nhận là `[MINOR]` X3.

**[MINOR] X3 — `handleActiveSourceSwitched()` không làm mới danh sách khi bộ lọc chỉ gồm `maleOnly = false` trên Wikidich**
- `[SRC] SearchViewModel.kt:295-336` (`browseByFilterOnly` đặt `hasSearched = true`), `SearchViewModel.kt:441-485` (`searchJob` của tải thêm). Sketch §3.4 chỉ gọi `clearFilter()` khi `current.selectedGenre != null && validGenre == null`; tổ hợp `query = ""`, `selectedGenre = null`, chỉ `maleOnly = false` trên Wikidich → đổi sang TTV/SsTruyện: `isFilterActive` mới = `false` (V4), không nhánh nào chạy → kết quả Wikidich cũ vẫn hiển thị. Sửa nhỏ khi triển khai: thêm `else if (s.hasSearched) clearFilter()` ở cuối chuỗi điều kiện (cùng mẫu với `SearchViewModel.kt:223-225`). Không chặn plan vì không sai dữ liệu và không gửi request sai nguồn.

#### Commands Executed

Trích từ `evidence.md` (reviewer không tự chạy lệnh):
- `### [CMD] git status --short` — `Exit: 0` — 27 file `M`, 5 file `??`.
- `### [CMD] git diff --stat` — `Exit: 0` — `27 files changed, 1191 insertions(+), 314 deletions(-)`.
- `### [CMD] git log --oneline -n 20` — `Exit: 0` — HEAD `d6024c3`.
- `### [CMD] cmd.exe /d /c gradlew.bat compileDebugKotlin` — `Exit: 0` — `BUILD SUCCESSFUL in 3s`.
- `### [CMD] cmd.exe /d /c gradlew.bat ktlintCheck` — `Exit: 0` — `BUILD SUCCESSFUL in 768ms`.
- `### [CMD] cmd.exe /d /c gradlew.bat testDebugUnitTest` — `Exit: 0` — `BUILD SUCCESSFUL in 2s` (`34 actionable tasks: 34 up-to-date`, baseline trước triển khai).
- `### [CMD] node --test .agents/scripts/code-freeze-guard.test.js` — `Exit: 0` — `pass 36, fail 0`.
- `### [CMD] node --test .agents/scripts/plan-approval.test.js` — `Exit: 0` — `pass 13, fail 0`.
- `### [CMD] node --test .agents/scripts/review-run.test.js` — `Exit: 0` — `pass 10, fail 0`.

UNVERIFIED (đề xuất chạy sau khi triển khai):
- `.\gradlew.bat testDebugUnitTest --tests "com.personal.apptruyen.ui.search.SearchViewModelTest" --tests "com.personal.apptruyen.data.repository.DownloadRepositoryTest" --tests "com.personal.apptruyen.data.repository.StoryRepositoryTest"` — xác nhận W1, mốc `2_250L` và X1.

#### Các Tấn Công Đã Thử (Attempted Attack Vectors)

1. Tăng `MAX_RETRY_ATTEMPTS` lên 3 làm lệch số lần gọi trong các test đếm `coVerify(exactly = …)` → không thành: các test đếm lời gọi (`DownloadRepositoryTest.kt:280, 334, 450-453`) đều dừng ở lần đầu do `CancellationException`, `LoginRequiredException` hoặc 429 Wikidich (`DownloadRepository.kt:144-153`), không đi vào vòng retry.
2. `retryPass` làm thay đổi `emissions.last()` của test 222 → không thành: `c3` thất bại lại trong `retryPass` không emit (sketch chỉ emit khi `result != null`), nên giá trị cuối vẫn là emit của batch 2 (`DownloadRepository.kt:246`).
3. Default argument mới của `IStoryRepository.searchStories` làm stub MockK 2 tham số không khớp → không thành: lời gọi và stub cùng đi qua `DefaultImpls` với `page = 1, maleOnly = true, genreSlug = null` (`SearchViewModelTest.kt:60, 81, 388`).
4. Chọn `"tangthuvien"`/`"sstruyen"` vẫn truyền `maleOnly = false` xuống scraper làm sai kết quả → không thành: `TangThuVienScraper.kt:378, 393` và `SsTruyenScraper.kt:715, 732` không dùng tham số này.

#### Kết luận

Đã sửa: toàn bộ lỗi Run 1–5 (F1–F11, N1–N8, R1–R9, V1–V5, W1–W3), kiểm lại trên `SearchViewModel.kt`, `SearchViewModelTest.kt`, `DownloadRepository.kt`, `DownloadRepositoryTest.kt`, `WikidichScraper.kt`, `SettingsDataStore.kt`, `StoryDetailViewModel.kt`. Phát hiện mới chỉ gồm 3 lỗi `[MINOR]` (X1 cách thu phát xạ trong test, X2 ghi chú tài liệu về `maleOnly` trên Explore, X3 thêm nhánh `clearFilter()` cuối trong `handleActiveSourceSwitched()`), có thể xử lý trong lúc triển khai. S1–S6 PASS. Kết luận: APPROVED.

[PLAN_APPROVED]
