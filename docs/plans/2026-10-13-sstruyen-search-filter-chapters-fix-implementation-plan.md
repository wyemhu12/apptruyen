# Kế hoạch triển khai: Sửa 3 lỗi nguồn SsTruyen (Số chương tìm kiếm, Bộ lọc thể loại, Phân trang danh sách chương)

> **Trạng thái**: Completed
> **Revision**: 1
> **Ngày tạo**: 2026-10-13
> **Planner**: @planner (gemini-3.8-flash)

## Technical Plan

### 1. Mục Tiêu

Khắc phục triệt để 3 lỗi vận hành cốt lõi của nguồn truyện **SsTruyen** (`sstruyen.pics`) đã được điều tra và kiểm chứng thực nghiệm 100% bởi Orchestrator và Planner vào ngày 2026-10-13:
1. **Lỗi 1 (Số chương khi tìm kiếm)**: Thẻ item truyện khi tìm kiếm hoặc duyệt danh sách không hiển thị badge "X chương" trên `StoryCardPremium` do regex cũ `(\d+)\s*chương` không khớp với HTML thực tế của Next.js SSR (`<span itemprop="dateModified" class="text-xs line-clamp-1">Chương 483</span>`). Dẫn đến `totalChapters = 0`, làm ẩn badge số chương và ảnh hưởng thuật toán deduplication (`smartDedup`).
2. **Lỗi 2 (Bộ lọc thể loại)**: Hàm `getGenreList()` chỉ lấy được ~11 thể loại (hoặc rỗng) do Next.js SSR trên trang web `https://sstruyen.pics/danh-sach/bo-loc` render client-side không in đầy đủ thẻ `<a href="/the-loai/...">`. Sửa bằng cách tích hợp REST API chính thức `GET https://api.sstruyen.pics/genres` trả về đầy đủ 93 thể loại chuẩn xác, có fallback an toàn sang HTML nếu API gặp sự cố.
3. **Lỗi 3 (Danh sách chương bị nhảy từ 50 sang 996..999)**: Do Next.js RSC trên trang chi tiết truyện không nhúng `novelId`, hàm `extractNovelId` trả về `null`. Khi rơi vào HTML fallback, trang HTML chỉ chứa 54 link chương (4 chương mới nhất ở đầu và 50 chương đầu tiên), đồng thời không có link phân trang HTML khiến `totalPages = 1`. Do đó, ứng dụng không kích hoạt `getRemainingChapters`, làm mất toàn bộ các chương ở giữa (từ chương 51 đến 995). Khắc phục bằng cách truy vấn endpoint REST API `GET https://api.sstruyen.pics/novels/{slug}` để lấy trực tiếp `novelId` và `chapterCount`, lưu vào `novelIdCache`, phân trang đầy đủ qua `GET https://api.sstruyen.pics/novels/{novelId}/chapters?page={page}&limit=50&sort=asc`, và loại bỏ container "Chương mới nhất" trong HTML fallback.

---

### 2. Pre-Work Reading

- `AGENTS.md`, `.agents/rules/00-core.md`, `.agents/rules/01-coding.md`, `.agents/rules/02-quality.md`, `.agents/rules/04-scraping.md`, `.agents/rules/05-plan-review.md`, `.agents/rules/06-docs.md`.
- `docs/lessons-learned.md`: Bài học về kiểm soát JSON parsing (JSON Object vs JSON Array), bọc và phân loại lỗi scraper (`ScraperException`), và xử lý resilient network requests.
- `docs/progress.md`: Lịch sử di chuyển domain sang `sstruyen.pics` (v2.8.3).
- `docs/scraping.md`: Đặc tả các endpoint và cấu trúc HTML/REST API của SsTruyen.
- Source code và test hiện có:
  - `app/src/main/java/com/personal/apptruyen/data/remote/SsTruyenScraper.kt`: Implementation bộ cào SsTruyen.
  - `app/src/test/java/com/personal/apptruyen/data/remote/SsTruyenScraperTest.kt`: Unit tests bộ cào SsTruyen với MockWebServer.
  - `app/src/main/java/com/personal/apptruyen/data/repository/StoryRepository.kt`: Tầng routing và gọi `getGenreList`, `smartDedup`.

---

### 3. Assumptions & Cross-Task Dependencies

1. **Kiến trúc & Tương thích**: Giữ nguyên kiến trúc Clean Architecture + BaseScraper. Không thay đổi chữ ký các hàm trong interface `StorySource.kt`.
2. **Kế thừa Task trước**: Task `2026-10-13-sstruyen-pics-migration-implementation-plan.md` đã hoàn tất thiết lập domain `sstruyen.pics`, REST API client OkHttp với retry/backoff, và tương thích ngược với URL cũ `sstruyen.net`. Task này tập trung sửa lỗi logic trích xuất dữ liệu và bổ sung endpoint REST API đã được khảo sát thực tế.
3. **Database**: Không thay đổi schema Room Database (giữ nguyên schema v9 hiện tại). Không yêu cầu migration.
4. **Định dạng dữ liệu API**:
   - `GET https://api.sstruyen.pics/genres` trả về một JSON Array gồm 93 phần tử: `[{"name":"Bách Hợp","slug":"bach-hop"},...]`. Bắt buộc dùng `JSONArray` parser, không dùng `JSONObject` để tránh `JSONException`.
   - `GET https://api.sstruyen.pics/novels/{slug}` trả về một JSON Object chứa `"id": <Long>`, `"name": "..."`, `"chapterCount": <Int>`, `"status": "..."`.
   - `GET https://api.sstruyen.pics/novels/{novelId}/chapters?page={page}&limit=50&sort=asc` trả về JSON Object chứa `"chapters": [...]`, `"total": <Int>`, `"totalPages": <Int>`.

---

### 4. Verified Versions Table

| Thư viện | Version hiện tại | Version đề xuất | Latest đã xác minh (2026) | Nguồn URL | Tương thích? |
|---|---|---|---|---|---|
| `com.squareup.okhttp3:okhttp` | 5.3.2 | Giữ nguyên (5.3.2) | 5.5.0 (2026-08) | [mvnrepository.com](https://mvnrepository.com/artifact/com.squareup.okhttp3/okhttp) | Tương thích 100% |
| `org.jsoup:jsoup` | 1.22.2 | Giữ nguyên (1.22.2) | 1.23.2 (2026-08) | [jsoup.org](https://jsoup.org) | Tương thích 100% |
| `org.json` (Android Platform) | SDK 35/36 | Giữ nguyên | Android built-in | [developer.android.com](https://developer.android.com/reference/org/json/package-summary) | Tương thích 100% |

> *Ghi chú*: Task này là bugfix thuần túy trong lớp cào dữ liệu `SsTruyenScraper.kt`, không thêm, xóa hay nâng cấp bất kỳ dependency nào trong `app/build.gradle.kts`.

---

### 5. Files Cần Chỉnh Sửa / Tạo Mới

| Nhãn | File | Tầng | Ghi chú (file nhạy cảm → lý do) |
|---|---|---|---|
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/data/remote/SsTruyenScraper.kt` | `data/remote` | Sửa logic trích xuất số chương search, gọi API genres, tích hợp endpoint novel metadata và hoàn thiện phân trang |
| `[MODIFY]` | `app/src/test/java/com/personal/apptruyen/data/remote/SsTruyenScraperTest.kt` | `test` | Cập nhật và bổ sung unit tests với MockWebServer cho 3 tính năng sửa lỗi |
| `[MODIFY]` | `docs/scraping.md` | `docs` | Bổ sung tài liệu endpoint `api/genres` và `api/novels/{slug}` của SsTruyen |
| `[MODIFY]` | `docs/progress.md` | `docs` | Cập nhật tiến độ hoàn thành task sửa lỗi SsTruyen |
| `[MODIFY]` | `docs/changelog.md` | `docs` | Ghi nhận các mục sửa lỗi trong phiên bản hiện tại |

---

### 6. Các Bước Thực Hiện

- [ ] **Bước 1: Bổ sung helper `fetchJsonArray` và regex nhận diện số chương trong `SsTruyenScraper.kt`**
  - Thêm hằng số `CHAPTER_PREFIX_REGEX = Regex("(?:Chương|Hồi|C)\\s*(\\d+)", RegexOption.IGNORE_CASE)`.
  - Viết hàm `internal suspend fun fetchJsonArray(url: String): JSONArray` với cơ chế retry tối đa 3 lần, exponential backoff (1s, 2s), kiểm tra mã lỗi HTTP, parse bằng `JSONArray(body)`, ném `ScraperException.ParseException("Lỗi định dạng dữ liệu danh sách thể loại", url, e)` 100% tiếng Việt nếu body không phải JSON Array.
- [ ] **Bước 2: Sửa lỗi 1 — Trích xuất số chương khi tìm kiếm & danh sách truyện (`parseStoryList`)**
  - Trong `parseStoryList(doc: Document)`:
    * Với khối `a[itemProp=hasPart]`: Trích xuất text từ `span[itemProp=dateModified], span[itemprop=dateModified]`. Áp dụng `CHAPTER_PREFIX_REGEX` trước, sau đó fallback sang `CHAPTER_COUNT_REGEX` trên `item.text()`, fallback tiếp sang `CHAPTER_PREFIX_REGEX` trên `item.text()`. Đảm bảo khớp cả dạng "Chương 483" lẫn "483 chương".
    * Với các khối fallback (container `div.home-truyendecu`, `div.each_truyen` và `a[href*=/truyen/]`): Bổ sung selector `span[itemProp=dateModified], span[itemprop=dateModified]` và regex `CHAPTER_PREFIX_REGEX` để đồng bộ toàn diện mọi layout của SsTruyen.
- [ ] **Bước 3: Sửa lỗi 2 — Lấy danh sách thể loại đầy đủ 93 tags (`getGenreList`)**
  - Trong `getGenreList()`:
    * Gọi API `GET $apiBaseUrl/genres` qua `fetchJsonArray`.
    * Duyệt qua mảng JSON: lấy `name = optString("name").trim()`, `slug = optString("slug").trim()`.
    * Lọc bỏ các mục rỗng, `name == "Thể loại"`, và `slug == "truyen-sac"`.
    * Trả về danh sách `Genre(name, slug)` được loại trùng qua `distinctBy { it.slug }`.
    * Nếu gọi API gặp lỗi (`HttpException`, `IOException`, `ParseException`): Bắt ngoại lệ, log `Timber.w`, và fallback sang phương thức cào HTML cũ từ `$baseUrl/danh-sach/bo-loc` (đảm bảo không bao giờ crash).
- [ ] **Bước 4: Sửa lỗi 3 — Lấy `novelId` qua API và khắc phục phân trang chương (`getStoryDetail`, `getFirstPageChapters`, `getRemainingChapters`, `parseChapterLinks`)**
  - Thêm hàm helper `internal suspend fun fetchNovelIdFromApi(slug: String): Long?` gọi `GET $apiBaseUrl/novels/$slug`. Nếu thành công, trích xuất `"id"` và lưu vào `novelIdCache[slug]`.
  - Cập nhật `getStoryDetail(storyUrl)`:
    * Gọi `fetchNovelIdFromApi(slug)` hoặc tích hợp dữ liệu từ `GET $apiBaseUrl/novels/$slug` để lấy `novelId` và `chapterCount`.
    * Nếu có `chapterCount` từ API, cập nhật `totalChapters = maxOf(totalChapters, chapterCountFromApi)`.
    * Đảm bảo `novelIdCache[slug]` luôn được ghi nhận `novelId` chuẩn xác.
  - Cập nhật `getFirstPageChapters(storyUrl)`:
    * Kiểm tra `novelId = novelIdCache[slug]`. Nếu null, gọi `fetchNovelIdFromApi(slug)`.
    * Nếu vẫn null mới fallback sang fetch HTML detail và gọi `extractNovelId(d, slug)`.
    * Khi có `novelId`: Gọi `GET $apiBaseUrl/novels/$novelId/chapters?page=1&limit=50&sort=asc`.
    * Tính `totalPages`: ưu tiên `json.optInt("totalPages", 0)`, nếu = 0 thì tính `ceil(total / 50.0).toInt()`.
    * Chỉ fallback sang HTML khi API ném ngoại lệ hoặc trả về danh sách chương rỗng.
  - Cập nhật `getRemainingChapters(storyUrl, totalPages, onBatchReady)`:
    * Nếu `novelIdCache[slug]` null, ưu tiên gọi `fetchNovelIdFromApi(slug)` để phục hồi `novelId` nhanh chóng mà không cần parse lại toàn bộ HTML detail page.
    * Đảm bảo tải tuần tự/song song các trang từ 2 đến `totalPages` qua API.
  - Cập nhật `parseChapterLinks(doc, slug)` trong HTML fallback:
    * Loại bỏ các phần tử container chương mới nhất (`.chuong-moi, .new-chapters, .latest-chapters, .box-new-chap`) trước khi duyệt link chương để tránh việc trang 1 bị dính 4 chương cuối (chương 996-999).
- [ ] **Bước 5: Cập nhật & bổ sung Unit Tests trong `SsTruyenScraperTest.kt`**
  - Test 1: `search extracts chapter count with dateModified span` (kiểm tra `StoryCardPremium` badge số chương hoạt động, `totalChapters == 483`).
  - Test 2: `getGenreList fetches full genres from REST API` (kiểm tra parse đúng JSON Array 93 thể loại, lọc đúng `truyen-sac`).
  - Test 3: `getGenreList falls back to HTML when API fails` (kiểm tra tính tự phục hồi khi API lỗi 500 hoặc rỗng).
  - Test 4: `getFirstPageChapters fetches novelId from API and computes totalPages correctly` (kiểm tra gọi `novels/{slug}` lấy id, tính `totalPages` chuẩn xác).
  - Test 5: `parseChapterLinks excludes latest chapters container in HTML fallback` (kiểm tra không bị nhảy cóc chương trong HTML fallback).
- [ ] **Bước 6: Chạy Quality Gate Tier 2 và Cập nhật Documentation**
  - Chạy kiểm tra: `.\gradlew.bat ktlintCheck; .\gradlew.bat testDebugUnitTest; .\gradlew.bat assembleDebug`.
  - Cập nhật `docs/scraping.md`, `docs/progress.md`, `docs/changelog.md` theo quy tắc `06-docs.md`.

---

### 7. Test Contract

| # | Hành vi (ngôn ngữ nghiệp vụ) | Input | Kết quả mong đợi / Thông báo lỗi tiếng Việt |
|---|---|---|---|
| TC-01 | Tìm kiếm truyện trên SsTruyen hiển thị đúng số chương | HTML item chứa `<span itemprop="dateModified">Chương 483</span>` | `story.totalChapters == 483`, badge "483 chương" hiển thị trên UI, không bị gán = 0. |
| TC-02 | Lấy danh sách thể loại SsTruyen qua REST API | Request `GET /genres` trả về JSON Array 93 thể loại | Trả về danh sách đầy đủ 93 thể loại tiếng Việt, loại trừ "truyen-sac", không trùng lặp slug. |
| TC-03 | Phục hồi thể loại qua HTML khi API genres lỗi | Request `GET /genres` trả về HTTP 500 / 404 | Không crash, tự động fallback sang cào HTML `$baseUrl/danh-sach/bo-loc`, trả về danh sách thể loại từ HTML. |
| TC-04 | Lấy danh sách chương đầy đủ không bị nhảy cóc (50 → 996) | Truyện có 999 chương, `novelIdCache` ban đầu rỗng | Gọi API `novels/{slug}` lấy `novelId`, gọi API `chapters?page=1` trả về 50 chương đầu và `totalPages = 20`. Toàn bộ 999 chương được nạp đầy đủ qua `getRemainingChapters`. |
| TC-05 | Tự phục hồi `novelId` trong `getRemainingChapters` sau process death | `novelIdCache` bị xóa sạch | `getRemainingChapters` gọi `fetchNovelIdFromApi(slug)` lấy lại `novelId` thành công và tiếp tục tải các trang còn lại. |
| TC-06 | Báo lỗi tiếng Việt khi không thể lấy danh sách chương | Cả API lẫn HTML fallback đều không lấy được `novelId` | Ném `ScraperException.ParseException` với thông điệp: "Không thể xác định mã định danh truyện để tải danh sách chương". |

---

### 8. Verification Plan

#### 8.1. Unit Tests Tự Động
- File test: `app/src/test/java/com/personal/apptruyen/data/remote/SsTruyenScraperTest.kt`.
- Sử dụng `MockWebServer` để mock các phản hồi:
  1. HTML tìm kiếm có `<span itemprop="dateModified">Chương 483</span>`.
  2. JSON Array thể loại từ endpoint `/genres`.
  3. JSON Object thông tin truyện từ `/novels/{slug}` (chứa `id` và `chapterCount`).
  4. JSON Object danh sách chương phân trang từ `/novels/{id}/chapters`.
  5. HTML fallback có container `.chuong-moi` chứa link chương mới nhất để kiểm tra tính năng loại trừ.

#### 8.2. Lệnh Gradle Bắt Buộc (Tier 2 Quality Gate)
```powershell
.\gradlew.bat ktlintCheck; .\gradlew.bat testDebugUnitTest; .\gradlew.bat assembleDebug
```

#### 8.3. Kiểm Thử Thủ Công Trên Thiết Bị (User Verification Steps)
1. Mở màn hình **Tìm kiếm**: Tìm từ khóa `"linh"` hoặc `"tien"` với nguồn **SsTruyen** -> Quan sát thẻ truyện kết quả hiển thị badge số chương (ví dụ "4707 chương" hoặc "483 chương"), không còn bị ẩn badge.
2. Mở **Bộ lọc** (FilterBottomSheet): Chọn nguồn SsTruyen -> Kiểm tra danh sách thể loại hiển thị đầy đủ ~93 tags (có Bách Hợp, Cổ Đại, Đồng Nhân, Khoa Huyễn, Võng Du...), không còn chỉ hiện 11 tags.
3. Mở một truyện dài (> 100 chương, ví dụ *Linh Vũ Thiên Hạ* hoặc *Tiên Nghịch*) của SsTruyen:
   - Mở màn hình danh sách chương -> Cuộn danh sách qua chương 50 -> Xác nhận chương 51, 52, 53... hiển thị liên tục, không bị nhảy cóc sang chương 996 hay 4700.
   - Thử tải offline 100 chương đầu -> Quá trình tải hoàn tất trơn tru không báo lỗi thiếu trang.

---

### 9. Rủi Ro & Phòng Ngừa

| Rủi ro | Mức độ | Technical Control trong Code |
|---|---|---|
| API `/genres` trả về JSON Array trong khi code cũ chỉ có `fetchJson` parse `JSONObject` | **Cao** | Xây dựng helper riêng `fetchJsonArray(url: String): JSONArray`, parse an toàn với `JSONArray(body)`, ném `ParseException` rõ ràng nếu sai cấu trúc. |
| Server SsTruyen chặn hoặc lỗi mạng tạm thời ở endpoint REST API | **Trung bình** | Bọc toàn bộ các lệnh gọi REST API (`/genres`, `/novels/{slug}`) trong khối `try-catch`, tự động fallback sang cơ chế cào HTML truyền thống (`fetchDocumentWithRetry`) khi API thất bại. |
| Cache `novelIdCache` bị mất khi Activity bị hủy (process death) | **Trung bình** | Trong `getRemainingChapters`, nếu `novelIdCache[slug]` là null, tự động gọi lại `fetchNovelIdFromApi(slug)` để khôi phục `novelId` trước khi bắt đầu tải danh sách chương. |
| Tên thể loại hoặc chương có ký tự đặc biệt / khoảng trắng thừa | **Thấp** | Áp dụng `.trim()`, `.ifBlank`, loại trừ các tag rác ("Thể loại", "truyen-sac") và dùng `.distinctBy { it.slug }`. |

---

### 10. Cập Nhật Docs

Theo ma trận quy tắc `.agents/rules/06-docs.md`:
- `docs/scraping.md`: Cập nhật mục SsTruyen: bổ sung endpoint `https://api.sstruyen.pics/genres` (93 thể loại) và `https://api.sstruyen.pics/novels/{slug}` (lấy `novelId` và metadata), cập nhật selector số chương `span[itemprop=dateModified]`.
- `docs/progress.md`: Cập nhật bảng hoàn thành: ghi nhận sửa dứt điểm 3 lỗi nguồn SsTruyen (hiển thị số chương tìm kiếm, 93 thể loại bộ lọc, sửa lỗi nhảy cóc danh sách chương 50->996).
- `docs/changelog.md`: Thêm mục dưới `## [Unreleased]` -> `### Fix bug`:
  * Sửa lỗi nguồn SsTruyen: không hiển thị số chương khi tìm kiếm.
  * Sửa lỗi nguồn SsTruyen: bộ lọc chỉ hiện 11 thể loại thay vì 93 thể loại.
  * Sửa lỗi nguồn SsTruyen: danh sách chương bị gián đoạn từ chương 50 sang các chương cuối do thiếu novelId API.

## Audit History

<!-- Reviewer chỉ append "### Audit Run N" ở dưới. Không sửa phần Technical Plan. -->

### Audit Run 1

[REVIEWER_MODEL: gemini-3.8-flash]
- **Ngày review**: 2026-10-13
- **Plan Revision**: 1
- **Kiểm chứng lỗi cũ**: Lượt review đầu tiên (Audit Run 1), không có lỗi cũ từ run trước.

#### 1. Mental Simulation Verification Matrix

| Kịch bản | Trạng thái | Loại bằng chứng | Chi tiết thẩm định |
|---|---|---|---|
| **S1: Cold-start & Build** | `PASS` | `[CMD]` + `[SRC]` | `compileDebugKotlin` pass (0 errors, 752ms). Không đổi `app/build.gradle.kts`, không thêm dependency mới. Tương thích hoàn toàn Android `minSdk 26`. |
| **S2: Lifecycle & Teardown** | `PASS` | `[SRC]` | OkHttp `Response` đóng bằng `use {}`, coroutine hủy ném lại `CancellationException`, `MockWebServer` đóng sạch sẽ trong `@AfterEach`. Không dùng `GlobalScope`. |
| **S3: Database & Temporal Logic** | `PASS` | `[SRC]` + `[ADV]` | Room Database giữ nguyên schema v9. `novelIdCache` dùng `ConcurrentHashMap`, an toàn truy cập đồng thời. Khôi phục `novelId` sau process death qua API an toàn. |
| **S4: Cross-Task State Flow** | `PASS` | `[SRC]` | Giữ nguyên chữ ký `StorySource`. `smartDedup` trong `StoryRepository` không còn loại bỏ truyện SsTruyen vì `totalChapters = 0`. UI `StoryCardPremium` badge "X chương" hiển thị chuẩn. |
| **S5: Security & Privacy Boundary** | `PASS` | `[SRC]` | Nguồn SsTruyen không dùng cookie/token người dùng. Không log thông tin nhạy cảm. URL parameter encode UTF-8 an toàn. Host `api.sstruyen.pics` thuộc miền chính thức. |
| **S6: External Resilience** | `PASS` | `[SRC]` + `[ADV]` | Cơ chế tự phục hồi: fallback từ API sang HTML khi API lỗi/timeout. Thông báo lỗi bằng tiếng Việt (`ScraperException`). Tách container chương mới nhất để tránh nhảy cóc chương. |
| **Domain: Scraping & REST API** | `PASS` | `[SRC]` + `[CMD]` | Trích xuất đủ 93 thể loại qua `JSONArray` parser từ `/genres`. Regex bắt cả "Chương 483" lẫn "483 chương". `SsTruyenScraperTest` hiện có pass 100% (exit 0). |

#### 2. Claim Verification

- `[SRC]` `app/src/main/java/com/personal/apptruyen/data/repository/StoryRepository.kt:97-102`: Thuật toán `smartDedup` gom nhóm theo slug và chọn `maxByOrNull { it.totalChapters }`. Việc `totalChapters = 0` trước đây khiến SsTruyen luôn bị lép vế khi gộp nguồn.
- `[SRC]` `app/src/main/java/com/personal/apptruyen/ui/components/StoryCardPremium.kt:273-285`: Badge số chương `if (story.totalChapters > 0)` bị ẩn hoàn toàn khi `totalChapters == 0`.
- `[SRC]` `app/src/main/java/com/personal/apptruyen/data/remote/SsTruyenScraper.kt:137-141`: Hàm `fetchJson` hiện tại gọi `JSONObject(body)`. Endpoint `GET /genres` trả về một JSON Array, do đó bắt buộc phải có `fetchJsonArray` dùng `JSONArray(body)` để ngăn ngừa `JSONException`.
- `[URL]` [developer.android.com/reference/org/json/package-summary](https://developer.android.com/reference/org/json/package-summary): `org.json.JSONArray` và `org.json.JSONObject` là thư viện nền tảng của Android SDK.

#### 3. Adversarial Timelines

- **Timeline 1: Truy cập đồng thời trên `novelIdCache`**
  * T1: Coroutine A gọi `getStoryDetail("tien-nghich")` và ghi `novelIdCache["tien-nghich"] = 104251`.
  * T2: Coroutine B đồng thời gọi `getFirstPageChapters("tien-nghich")`.
  * T3: `ConcurrentHashMap` đảm bảo thread-safe. Nếu B đọc trước khi A ghi, B gọi `fetchNovelIdFromApi("tien-nghich")` độc lập. Cả hai đều nhận được `104251` hợp lệ.
  * Kết cục: AN TOÀN.
- **Timeline 2: Phục hồi sau Process Death**
  * T1: Người dùng đang xem truyện, ứng dụng bị Android OS thu hồi RAM trong nền.
  * T2: Người dùng mở lại truyện hoặc tiếp tục tải offline, `novelIdCache` bị rỗng, hệ thống gọi `getRemainingChapters("tien-nghich", 20)`.
  * T3: `getRemainingChapters` phát hiện `novelId == null`, lập tức kích hoạt `fetchNovelIdFromApi("tien-nghich")` để khôi phục `novelId` trực tiếp từ REST API mà không bị chặn bởi HTML RSC.
  * Kết cục: AN TOÀN.
- **Timeline 3: Endpoint REST API gặp sự cố (HTTP 500 hoặc Timeout)**
  * T1: Gọi `getGenreList()` hoặc `getFirstPageChapters()`.
  * T2: Máy chủ `api.sstruyen.pics` phản hồi lỗi 500 hoặc mất kết nối mạng.
  * T3: Scraper bắt ngoại lệ, ghi log `Timber.w`, và tự động fallback sang cào HTML `$baseUrl/danh-sach/bo-loc` hoặc HTML detail page mà không làm crash ứng dụng.
  * Kết cục: AN TOÀN.

#### 4. Commands Executed

1. `.\gradlew.bat compileDebugKotlin` — Exit code: 0 (752ms).
2. `.\gradlew.bat testDebugUnitTest --tests "com.personal.apptruyen.data.remote.SsTruyenScraperTest"` — Exit code: 0 (11 tests pass).
3. `node .agents/scripts/plan-approval.js status --plan docs/plans/2026-10-13-sstruyen-search-filter-chapters-fix-implementation-plan.md` — Exit code: 0.

#### 5. Danh Sách Lỗi & Đề Xuất

- **`[BLOCKER]`**: 0
- **`[MAJOR]`**: 0
- **`[MINOR]` (Advisory Recommendations)**: 2
  * *Advisory 1*: Trong `getFirstPageChapters`, khi tính toán `totalPages` dự phòng từ `total / 50.0`, nên dùng `maxOf(1, ceil(total / 50.0).toInt())` để phòng ngừa trường hợp `total = 0` dẫn đến `totalPages = 0`.
  * *Advisory 2*: Tại dòng 90 của `SsTruyenScraper.kt` đã có sẵn `CHAPTER_NUMBER_FROM_NAME_REGEX = Regex("(?:Chương|Hồi|C)\\s*(\\d+)", RegexOption.IGNORE_CASE)`. Khi triển khai có thể tái sử dụng trực tiếp hằng số này để tránh khai báo thêm regex trùng lặp.

**Kết luận**: APPROVED · BLOCKER 0 · MAJOR 0

[PLAN_APPROVED]
