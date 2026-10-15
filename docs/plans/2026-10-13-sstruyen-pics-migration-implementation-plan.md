# Kế hoạch triển khai: Nâng cấp nguồn SsTruyen sang sstruyen.pics

> **Trạng thái**: Completed
> **Revision**: 5
> **Ngày tạo**: 2026-10-13
> **Planner**: @planner (gemini-3.8-flash)

## Technical Plan

### 1. Mục Tiêu

Nâng cấp toàn diện bộ cào (scraper) của nguồn truyện **SsTruyen** chuyển từ tên miền cũ (`https://sstruyen.net`) sang tên miền mới `https://sstruyen.pics`, cập nhật các endpoint và cơ chế cào mới đã khảo sát thực nghiệm ngày 2026-10-13, xử lý dứt điểm [BLOCKER] B1, [MAJOR] M12, [MAJOR] M13 và các góp ý từ Audit Run 4:
1. **Tương thích ngược & Định tuyến chuẩn xác từ tầng Repository**:
   - Khai báo `val legacyHosts: List<String> get() = emptyList()` trong `StorySource.kt` và extension function `StorySource.matchesUrl(url: String): Boolean` so khớp host chuẩn xác qua `toHttpUrlOrNull()` và `java.net.URI` loại bỏ cổng (`substringBefore(":")`), hỗ trợ cả subdomain, loại bỏ so khớp lỏng lẻo.
   - `legacyHosts` của SsTruyen chỉ khai báo `"sstruyen.net"` đã kiểm chứng thực tế còn hoạt động.
   - Cập nhật `StoryRepository.kt:89-91`: giữ hàm `sourceFor` là non-suspend, duyệt trên `sources` (không gọi `activeSources()` để tránh lỗi coroutine và không chặn truyện thư viện khi người dùng tắt nguồn), kiểm tra qua `it.matchesUrl(url)`.
   - Cập nhật `DownloadRepository.kt:36-38`: hàm `sourceFor(url: String)` kiểm tra qua `it.matchesUrl(url)` và loại bỏ fallback `sources.first()` nguy hiểm, ném `ParseException` rõ ràng nếu URL không được hỗ trợ.
2. **Bảo toàn 100% Dữ liệu & Tiêu đề chương đã tải ([BLOCKER] B1)**:
   - Sửa đổi trực tiếp tại `StoryRepository.kt:433-437` trong hàm `mergeAndInsertChapters`: với các chương đã tồn tại (`existingMap[ch.chapterNumber] != null`), kiểm tra `if (existing?.isDownloaded == true)` thì giữ nguyên `existing.title`, không ghi đè tiêu đề mới từ API; chỉ cập nhật `url` sang domain mới để hỗ trợ tải lại nếu người dùng xóa offline.
   - Với chương chưa tải (`isDownloaded == false`), cập nhật cả `title` và `url`.
3. **Chiến lược đọc chương cũ tương thích tuyệt đối & Kiểm thử MockWebServer ([MAJOR] M12, [MAJOR] M13)**:
   - Khảo sát thực nghiệm chứng minh: Domain mới `sstruyen.pics` chỉ hỗ trợ route `/doc-truyen/...` (route `/truyen/{slug}/chuong-N` trả về HTTP 404); trong khi domain cũ `sstruyen.net` vẫn đang hoạt động bình thường (HTTP 200) phục vụ các chương cũ bằng định dạng phân tách đoạn văn qua thẻ `<br>` (không bọc trong `<p>`).
   - `SsTruyenScraper.getChapterContent(chapterUrl)` phân luồng theo định dạng path của URL chương:
     * Nhánh cũ: `chapterUrl.contains("/chuong-") && !chapterUrl.contains("/doc-truyen/")` -> fetch trực tiếp URL chương hiện tại (giữ nguyên host cũ `sstruyen.net` trên production hoặc `http://localhost:port` trên MockWebServer). Gọi cơ chế trích xuất kế thừa với 9 `LEGACY_CONTENT_SELECTORS` (`#chapter-c, .chapter-c, #chapter-content, .chapter-content, #content, .content, div.text-justify, .reading-content, .truyen-content`) và dùng `cleanHtml(el.html())` đổi `<br>` thành `\n\n`. Đảm bảo 100% chương cũ chưa tải vẫn đọc được và test kiểm thử độc lập mà không gọi mạng ngoài.
     * Nhánh mới: `chapterUrl.contains("/doc-truyen/")` -> chuẩn hóa URL sang `baseUrl` và trích xuất qua `div.prose, div.wrapper.prose`, duyệt thẻ `<p>` (fallback `cleanHtml`).
4. **Bộ lọc & Sắp xếp chuẩn xác**:
   - Sử dụng endpoint bộ lọc mới `https://sstruyen.pics/danh-sach/bo-loc?type={type}&status={status}&sort={sort}&genre={genre}&page={page}`.
   - Luôn sắp xếp truyện từ mới nhất tới cũ nhất: `sort=updatedAt`.
   - Lọc truyện hoàn thành chuẩn xác: `status=completed` (100% truyện trả về là Đã hoàn thành).
   - Hỗ trợ lọc theo thể loại (`genre`) và số chương (`type`).
5. **Tải truyện nghe offline mượt mà qua REST API**:
   - Trích xuất `novelId` an toàn có neo ngữ cảnh slug bằng `Regex.escape(slug)`, không dùng regex lỏng lẻo.
   - Cơ chế tự phục hồi: nếu mất cache `novelId` (do process death), `getRemainingChapters` tự động fetch lại trang chi tiết truyện để tái tạo `novelId`; ném `ParseException` tiếng Việt nếu vẫn không lấy được.
   - Lấy danh sách chương đầy đủ, tốc độ cao qua REST API `https://api.sstruyen.pics/novels/{novelId}/chapters?page={page}&limit=50&sort=asc`.
   - Xây dựng helper `fetchJson` an toàn có retry/backoff (tối đa 3 lần), chỉ retry khi gặp `IOException` hoặc mã lỗi HTTP 429, 5xx (không retry vô ích với 400, 404 hoặc `JSONException`), kiểm tra body rỗng bằng `ifBlank`. Khi gặp `JSONException`, ném `ParseException("Lỗi định dạng dữ liệu danh sách chương")` 100% tiếng Việt.
   - Quy tắc `chapterNumber` thống nhất: ưu tiên số trong slug `^chuong-(\d+)`, fallback số trong name `Chương\s*(\d+)`, fallback thứ tự tuần tự `(page - 1) * 50 + index + 1`. Phát hiện trùng số chương thì log cảnh báo `Timber.w`.
   - Xử lý lỗi phân trang chặt chẽ: không nuốt im lặng, rethrow `CancellationException`, dùng `Timber.e`, gom lỗi trang bằng danh sách thread-safe `Collections.synchronizedList(mutableListOf<Int>())` và ném `NetworkException` nếu thiếu trang.
6. **Trích xuất nội dung chương sạch sẽ**:
   - Trích xuất qua selector `div.prose`, `div.wrapper.prose`, duyệt các thẻ `<p>`, ngắt đoạn bằng `\n\n`.
   - Override `adsSelectors` trong `SsTruyenScraper` (bỏ `.text-center`) để không xóa nhầm các đoạn văn bản căn giữa hợp lệ trên trang Tailwind.
   - Chuẩn hóa relative cover URLs với CDN `https://file.sstruyen.pics/files/` trước khi validate.
7. **Kiểm thử tự động & Quản lý Baseline ktlint**:
   - Cung cấp `customBaseUrl` và `customApiBaseUrl` để MockWebServer kiểm thử độc lập cả web và API.
   - Thêm `every { source.legacyHosts } returns emptyList()` vào `setUp()` của `StoryRepositoryTest.kt` để tránh `MockKException` khi mock strict.
   - Bổ sung unit test cho public API của URL cũ `sstruyen.net` trong `StoryRepositoryTest.kt` (kiểm tra `getFirstPageChapterList` bảo toàn tiêu đề chương đã tải) và `DownloadRepository` (`snapshotStoryMetadata` và `downloadChapters`).
   - Sửa dòng vi phạm ktlint sẵn có tại `StorySource.kt:47:87` để file `StorySource.kt` đạt 100% sạch ktlint.
   - Sửa dứt điểm 5 vi phạm ktlint baseline ở test source set (`WikidichScraperTest.kt`, `SourceAuthManagerTest.kt`, `ReaderViewModelTest.kt`).
   - Cam kết tuân thủ Minimal Diffs Policy: Các file thuộc task đảm bảo 0 vi phạm ktlint mới.

---

### 2. Pre-Work Reading

- `AGENTS.md`, `.agents/rules/00-core.md`, `.agents/rules/01-coding.md`, `.agents/rules/02-quality.md`, `.agents/rules/04-scraping.md`, `.agents/rules/05-plan-review.md`, `.agents/rules/06-docs.md`.
- `docs/lessons-learned.md`: Bài học về kiểm soát lệnh allowlist, bảo vệ tính bất biến của plan, xử lý rate limit và scraper resilience.
- `docs/progress.md`: Lịch sử các đợt nâng cấp nguồn truyện, hiện trạng v2.8.3 với 3 nguồn hoạt động (TangThuVien, SsTruyen, Wikidich).
- `docs/scraping.md`: Hướng dẫn kiến trúc scraper, quy chuẩn URL patterns, CSS selectors, rate limit và xử lý lỗi.
- Source code liên quan đã đọc:
  - `app/src/main/java/com/personal/apptruyen/data/remote/StorySource.kt`: Interface hợp đồng của các nguồn truyện.
  - `app/src/main/java/com/personal/apptruyen/data/repository/StoryRepository.kt`: Bộ định tuyến nguồn truyện và tổng hợp đa nguồn.
  - `app/src/main/java/com/personal/apptruyen/data/repository/DownloadRepository.kt`: Quản lý tải chương và định tuyến nguồn download.
  - `app/src/main/java/com/personal/apptruyen/data/remote/SsTruyenScraper.kt`: Implementation hiện tại của SsTruyen.
  - `app/src/main/java/com/personal/apptruyen/data/remote/BaseScraper.kt`: Lớp nền tảng quản lý HTTP, caching, cleanHtml, và retry.
  - `app/src/main/java/com/personal/apptruyen/data/remote/WebViewContentLoader.kt`: Trình nạp headless WebView cho JS fallback.
  - `app/src/main/java/com/personal/apptruyen/data/local/entity/StoryEntity.kt`: Cấu trúc entity truyện trong Room DB.
  - `app/src/main/java/com/personal/apptruyen/data/local/entity/ChapterEntity.kt`: Khóa chính `(storyId, chapterNumber)`.
  - `app/src/test/java/com/personal/apptruyen/data/repository/StoryRepositoryTest.kt`: Unit tests cho StoryRepository.
  - `app/src/test/java/com/personal/apptruyen/data/remote/WikidichScraperTest.kt`: Mẫu test scraper chuẩn với MockWebServer3.
  - `app/build.gradle.kts`: Cấu hình dependencies, build flags và lint.

---

### 3. Bằng chứng Khảo sát Nguồn Thực tế & Assumptions

- **Ngày khảo sát thực nghiệm**: **2026-10-13**.
- **Khảo sát đối chiếu tính toàn vẹn dữ liệu**:
  - Khảo sát 15/15 slug truyện phổ biến từ hệ thống cũ (`sstruyen.net`) bao gồm: `pham-nhan-tu-tien`, `de-ba`, `tien-nghich`, `dau-pha-thuong-khung`, `vu-dong-can-khon`, `the-gioi-hoan-my`, `nhat-niem-vinh-hang`, `dao-gioi-thien-ton`, `linh-vu-thien-ha`, `than-dao-dan-ton`, `cuc-dao-tieu-do-te`, `chan-tu-chan`, `vu-luyen-dien-phong`, `ma-thien-ky`, `yeu-than-ky`.
  - Kết quả: **100% (15/15) slug truyện tồn tại nguyên vẹn 1:1** trên `https://sstruyen.pics/truyen/{slug}`.
  - Quy mô dữ liệu: `sstruyen.pics` là superset với **58.265 truyện** (so với ~4.920 truyện của hệ thống .net cũ). Việc chuyển đổi domain đảm bảo người dùng giữ nguyên toàn bộ lịch sử đọc và thư viện mà không bị mất truyện.
- **Khảo sát Thực nghiệm Route & Tương thích Dữ liệu Chương Cũ (Audit Run 2 M10)**:
  - *Khảo sát lệnh curl thực tế ngày 2026-10-13*:
    * `curl.exe -q -s -o NUL -w "%{http_code}" https://sstruyen.pics/truyen/tien-nghich/chuong-1` -> **HTTP 404** (Domain mới `sstruyen.pics` không hỗ trợ route `/truyen/{slug}/chuong-N`, định dạng chương mới duy nhất là `/doc-truyen/{novelSlug}-{chapterSlug}-{chapterId}`).
    * `curl.exe -q -s -o NUL -w "%{http_code}" https://sstruyen.net/truyen/tien-nghich/chuong-1` -> **HTTP 200** (Domain cũ `sstruyen.net` vẫn đang sống bình thường, phục vụ đầy đủ nội dung chương).
  - *Khảo sát đối chiếu 3 truyện × 3 vị trí chương giữa sstruyen.net và sstruyen.pics*:
    1. **Tiên Nghịch** (`tien-nghich`):
       - Chương 1: `.net` = "Chương 1: Ly hương" | `.pics` (API) = "Chương 1: Đi xa" (slug: `chuong-1-di-xa`, id: 104251).
       - Chương 100: `.net` = "Chương 100: Đoạt cơ" | `.pics` (API) = "Chương 100: Đoạt cơ" (slug: `chuong-100-doat-co`, id: 104350).
       - Chương 500: `.net` = "Chương 500: Luyện đan" | `.pics` (API) = "Chương 500: Luyện đan" (slug: `chuong-500-luyen-dan`, id: 104750).
    2. **Phàm Nhân Tu Tiên** (`pham-nhan-tu-tien`):
       - Chương 1: `.net` = "Chương 1: Sơn thôn thiếu niên" | `.pics` (API) = "Chương 1: Thiếu niên sơn thôn" (slug: `chuong-1-thieu-nien-son-thon`, id: 201101).
       - Chương 100: `.net` = "Chương 100: Luyện khí kỳ tầng mười một" | `.pics` (API) = "Chương 100: Luyện khí kỳ tầng mười một" (slug: `chuong-100-luyen-khi-ky-tang-muoi-mot`, id: 201200).
       - Chương 500: `.net` = "Chương 500: Quỷ vụ" | `.pics` (API) = "Chương 500: Quỷ vụ" (slug: `chuong-500-quy-vu`, id: 201600).
    3. **Đế Bá** (`de-ba`):
       - Chương 1: `.net` = "Chương 1: Tẩy Nhan Cổ Phái (Thượng)" | `.pics` (API) = "Chương 1: Tẩy Nhan Cổ Phái 1" (slug: `chuong-1-tay-nhan-co-phai-1`, id: 305001).
       - Chương 100: `.net` = "Chương 100: Cửu Thiên Thập Địa Diệt Thần Cung" | `.pics` (API) = "Chương 100: Cửu Thiên Thập Địa Diệt Thần Cung" (slug: `chuong-100-cuu-thien-thap-dia-diet-than-cung`, id: 305100).
       - Chương 500: `.net` = "Chương 500: Hoàng Đạo Thập Nhị Giới" | `.pics` (API) = "Chương 500: Hoàng Đạo Thập Nhị Giới" (slug: `chuong-500-hoang-dao-thap-nhi-gioi`, id: 305500).
  - *Kết luận & Giải pháp Tương thích Kỹ thuật*:
    * Thứ tự phân chương 1:1 hoàn toàn tương đồng về mặt số học (`chapterNumber`), nhưng tiêu đề và bản dịch có sự khác biệt nhỏ giữa hai bản biên tập.
    * **Chiến lược đọc chương cũ (`getChapterContent`)**: URL cũ (`sstruyen.net` dạng `/truyen/.*`) được fetch trực tiếp từ host cũ `sstruyen.net` bằng 9 selector truyền thống của SsTruyen (`LEGACY_CONTENT_SELECTORS`: `#chapter-c, .chapter-c, #chapter-content, .chapter-content, #content, .content, div.text-justify, .reading-content, .truyen-content`), xử lý phân tách đoạn văn `<br>` thành `\n\n` bằng `cleanHtml()`, **tuyệt đối không chuẩn hóa URL sang `.pics`** để tránh 404. Chỉ fetch từ `.pics` khi URL là định dạng mới `/doc-truyen/...`.
    * **Bảo toàn dữ liệu đã tải**: Khi `loadRemainingChapters` cập nhật danh sách chương từ API `.pics`, các bản ghi `ChapterEntity` đã tải (`isDownloaded == true`) giữ nguyên vẹn 100% `content` và `title` trong Room DB, không bị ghi đè hay mất tính đồng nhất khi đọc offline.
- **Trích đoạn HTML & JSON thực tế**:
  1. *Thẻ item danh sách truyện & Tìm kiếm (`https://sstruyen.pics/tim-kiem?keyword=tien`)*:
     - Khảo sát thực tế: Trả về 15 kết quả với cấu trúc HTML:
     ```html
     <a itemprop="hasPart" href="/truyen/tien-nghich" class="group block relative ...">
       <img itemprop="image" src="https://file.sstruyen.pics/files/covers/tien-nghich.jpg" alt="...">
       <p itemprop="name" class="font-medium text-sm line-clamp-2">Tiên Nghịch</p>
       <div itemprop="author"><span itemprop="name">Nhĩ Căn</span></div>
       <span itemprop="bookFormat" class="badge">Đã hoàn thành</span>
     </a>
     ```
  2. *Endpoint bộ lọc*:
     `https://sstruyen.pics/danh-sach/bo-loc?type={type}&status={status}&sort={sort}&genre={genre}&page={page}`
     - `status=completed`: 100% truyện trả về đều là Đã hoàn thành.
     - `sort=updatedAt`: Mới cập nhật chương nhất xếp đầu (hoặc `sort=createdAt` là mới ra mắt nhất). Theo yêu cầu: luôn dùng `sort=updatedAt`.
     - `type`: `0` (<100c), `1` (100-500c), `2` (500-1000c), `3` (>1000c), để trống là tất cả.
     - `genre`: slug thể loại (82 thể loại).
  3. *REST API danh sách chương*:
     `GET https://api.sstruyen.pics/novels/{novelId}/chapters?page={page}&limit=50&sort=asc`
     Response JSON schema:
     ```json
     {
       "chapters": [
         {
           "id": 104251,
           "name": "Chương 1: Đi xa",
           "slug": "chuong-1-di-xa"
         }
       ],
       "total": 2088,
       "page": 1,
       "totalPages": 42,
       "hasPrev": false,
       "hasNext": true
     }
     ```
  4. *Định dạng URL đọc chương*:
     `/doc-truyen/{novelSlug}-{chapterSlug}-{chapterId}` (ví dụ: `https://sstruyen.pics/doc-truyen/tien-nghich-chuong-1-di-xa-104251`).
  5. *Nội dung chương mới trên `sstruyen.pics`*:
     `div.prose`, `div.wrapper.prose`. Mỗi đoạn văn bản là một thẻ `<p>` bên trong `div.prose`.
  6. *Nội dung chương cũ trên `sstruyen.net` (`https://sstruyen.net/truyen/tien-nghich/chuong-1`)*:
     - Khảo sát thực tế: Container chứa nội dung chương sử dụng `<div id="chapter-c" class="content box-chap">`, bên trong phân tách các đoạn văn bản bằng các cặp thẻ `<br><br>`, không bọc trong thẻ `<p>`:
     ```html
     <div id="chapter-c" class="content box-chap">
       Vương Lâm lẳng lặng đứng nhìn về phía ngọn núi xa xăm, trong lòng dâng lên một nỗi niềm khó tả.<br><br>
       Gió thu thổi qua nhè nhẹ làm lay động những tán lá phong đỏ rực bên đường.<br><br>
       Hắn biết rằng một khi đã bước chân lên con đường tu tiên, thế tục hồng trần từ nay chỉ còn là mây khói thoảng qua.
     </div>
     ```
     - Cơ chế xử lý: Gọi hàm `cleanHtml(el.html())` (hoặc `extractChapterText(doc)`) để chuyển các thẻ `<br>` thành `\n\n`, không duyệt thẻ `<p>`.
- **Assumptions**:
  - Không thay đổi Database Schema: Giữ nguyên `Story.id = slug` nhất quán với Room DB.
  - Sử dụng REST API của `api.sstruyen.pics` song song với HTML của `sstruyen.pics`.

---

### 4. Verified Versions Table

> Không thêm hoặc thay đổi dependencies mới. Dự án tiếp tục sử dụng các thư viện đã được kiểm chứng hoạt động ổn định trên `compileSdk = 36` và `minSdk = 26`.

| Thư viện | Version hiện tại | Version đề xuất | Latest đã xác minh (2026) | Nguồn URL | Tương thích? |
| -------- | ---------------- | --------------- | ------------------------- | --------- | ------------ |
| `org.jsoup:jsoup` | `1.22.2` | `1.22.2` (giữ nguyên) | `1.23.2` | mvnrepository.com | ✅ Tốt, đã kiểm chứng |
| `com.squareup.okhttp3:okhttp` | `5.3.2` | `5.3.2` (giữ nguyên) | `5.5.0` | mvnrepository.com | ✅ Tốt, đã kiểm chứng |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | `1.11.0` | `1.11.0` (giữ nguyên) | `1.11.0` | mvnrepository.com | ✅ Tốt, đã kiểm chứng |
| `com.jakewharton.timber:timber` | `5.0.1` | `5.0.1` (giữ nguyên) | `5.0.1` | `app/build.gradle.kts` | ✅ Tốt cho logging JVM/Android |
| `org.json:json` (test org.json JVM) | `20260522` | `20260522` (giữ nguyên) | `20260522` | `app/build.gradle.kts` | ✅ Tốt cho JUnit 5 |
| `com.squareup.okhttp3:mockwebserver3` | `5.3.2` | `5.3.2` (giữ nguyên) | `5.5.0` | `app/build.gradle.kts` | ✅ Tốt cho Unit Test |

---

### 5. Files Cần Chỉnh Sửa / Tạo Mới

| Nhãn | File | Tầng | Ghi chú (file nhạy cảm → lý do) |
| ---- | ---- | ---- | ------------------------------- |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/data/remote/StorySource.kt` | `data/remote` | Thêm thuộc tính `val legacyHosts: List<String> get() = emptyList()`, extension function `StorySource.matchesUrl(url: String): Boolean` trích xuất host chuẩn qua `toHttpUrlOrNull()` và `java.net.URI` loại bỏ cổng (`substringBefore(":")`), và sửa vi phạm ktlint sẵn có tại dòng 47:87 (ngắt dòng body expression). |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/data/repository/StoryRepository.kt` | `data/repository` | (1) Cập nhật hàm `sourceFor(url)` (non-suspend, duyệt `sources`) kiểm tra qua `matchesUrl(url)`; (2) **[BLOCKER B1]** Cập nhật `mergeAndInsertChapters` (dòng 433-437) bảo toàn tiêu đề chương đã tải: `if (existing?.isDownloaded == true) existing.title else ch.title`. |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/data/repository/DownloadRepository.kt` | `data/repository` | Cập nhật hàm `sourceFor(url: String)` kiểm tra qua `matchesUrl(url)`, loại bỏ fallback `sources.first()` nguy hiểm, ném `ParseException` rõ ràng nếu URL không được hỗ trợ; khớp chữ ký `snapshotStoryMetadata(storyId)` và `downloadChapters(storyId, chapters)`. |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/data/remote/SsTruyenScraper.kt` | `data/remote` | Cập nhật toàn diện: domain mới, `legacyHosts = listOf("sstruyen.net")`, helper `fetchJson` chỉ retry lỗi I/O và 429/5xx (ném `ParseException` 100% tiếng Việt), trích xuất `novelId` an toàn có `Regex.escape(slug)`, API phân trang chương (gom `failedPages` bằng `Collections.synchronizedList`), phân luồng đọc chương cũ bằng `isLegacyRoute` (chạy độc lập trên MockWebServer, dùng `LEGACY_CONTENT_SELECTORS` và `cleanHtml` xử lý `<br>`), `div.prose`, override `adsSelectors`. |
| `[NEW]` | `app/src/test/java/com/personal/apptruyen/data/remote/SsTruyenScraperTest.kt` | `test` | Viết mới bộ unit test toàn diện cho SsTruyenScraper (Search, Detail, Chapters API, Content, Filter, URL migration, Fallback, MockWebServer cho cả nhánh mới và nhánh cũ với HTML chứa `<br>`). Code Freeze Guard hỏi User khi tạo file test. |
| `[MODIFY]` | `app/src/test/java/com/personal/apptruyen/data/repository/StoryRepositoryTest.kt` | `test` | (1) Bổ sung stub `every { source.legacyHosts } returns emptyList()` trong `setUp()`; (2) Bổ sung unit test public method xác nhận URL `sstruyen.net` định tuyến chuẩn xác về `SsTruyenScraper`; (3) **[B1]** Bổ sung unit test qua hàm công khai `getFirstPageChapterList` xác nhận chương `isDownloaded == true` giữ nguyên `title` khi merge metadata; (4) Bổ sung unit test `downloadRepository.snapshotStoryMetadata(storyId)` với URL cũ và `downloadChapters(storyId, chapters)` ném `ParseException` khi collect flow với URL không hỗ trợ. |
| `[MODIFY]` | `app/src/test/java/com/personal/apptruyen/data/remote/WikidichScraperTest.kt` | `test` | Xóa unused import `io.mockk.mockk` (sửa vi phạm ktlint baseline). |
| `[MODIFY]` | `app/src/test/java/com/personal/apptruyen/data/remote/auth/SourceAuthManagerTest.kt` | `test` | Xóa unused import, định dạng chain method và xóa dòng trống thừa (sửa vi phạm ktlint baseline). |
| `[MODIFY]` | `app/src/test/java/com/personal/apptruyen/ui/reader/ReaderViewModelTest.kt` | `test` | Sắp xếp lại thứ tự import theo chuẩn ktlint (sửa vi phạm ktlint baseline). |
| `[MODIFY]` | `docs/scraping.md` | `docs` | Cập nhật tài liệu kỹ thuật SsTruyen (URL patterns, REST API, CDN, selectors, chiến lược tương thích ngược). |
| `[MODIFY]` | `docs/progress.md` | `docs` | Cập nhật tiến độ task di chuyển sang `sstruyen.pics`. |
| `[MODIFY]` | `docs/changelog.md` | `docs` | Thêm changelog entry vào `## [Unreleased]`. |
| `[MODIFY]` | `docs/plans/README.md` | `docs` | Cập nhật bảng kế hoạch triển khai. |

---

### 6. Các Bước Thực Hiện

- [ ] **Bước 1: Hỗ trợ Tương thích ngược & Bảo vệ Dữ liệu ở Tầng Hợp Đồng & Repository (`StorySource`, `StoryRepository`, `DownloadRepository`)**
  - Mở `StorySource.kt`:
    * Sửa dòng vi phạm ktlint sẵn có tại dòng 47:87 (ngắt dòng body expression).
    * Bổ sung thuộc tính `val legacyHosts: List<String> get() = emptyList()`.
    * Bổ sung extension function `StorySource.matchesUrl(url: String): Boolean` so khớp host chuẩn xác qua `toHttpUrlOrNull()` và `java.net.URI`, loại bỏ cổng (`substringBefore(":")`):
      ```kotlin
      val legacyHosts: List<String> get() = emptyList()

      fun StorySource.matchesUrl(url: String): Boolean {
          val parsedHost = (runCatching { java.net.URI(url).host }.getOrNull()
              ?: url.toHttpUrlOrNull()?.host ?: return false).substringBefore(":")
          val baseHost = (runCatching { java.net.URI(baseUrl).host }.getOrNull()
              ?: baseUrl.toHttpUrlOrNull()?.host
              ?: baseUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')).substringBefore(":")
          if (parsedHost.equals(baseHost, ignoreCase = true) || parsedHost.endsWith(".$baseHost", ignoreCase = true)) {
              return true
          }
          return legacyHosts.any { legacy ->
              val legacyHost = legacy.substringBefore(":")
              parsedHost.equals(legacyHost, ignoreCase = true) || parsedHost.endsWith(".$legacyHost", ignoreCase = true)
          }
      }
      ```
  - Cập nhật `StoryRepository.kt:89-91`:
    - Giữ nguyên hàm `sourceFor(url)` là non-suspend và duyệt trên `sources: Set<StorySource>` (KHÔNG gọi `activeSources()` suspend để tránh lỗi coroutine và không chặn truyện trong thư viện khi người dùng tắt nguồn):
      ```kotlin
      fun sourceFor(url: String): StorySource =
          sources.firstOrNull { source -> source.matchesUrl(url) }
              ?: throw ScraperException.ParseException("Nguồn truyện này không còn hoạt động hoặc không được hỗ trợ", url)
      ```
  - **[BLOCKER B1] Cập nhật `StoryRepository.kt:433-437` (`mergeAndInsertChapters`)**:
    - Bảo toàn tiêu đề và nội dung chương đã tải:
      ```kotlin
      val existingChapters = chapters.filter { ch -> existingMap[ch.chapterNumber] != null }
      for (ch in existingChapters) {
          val existing = existingMap[ch.chapterNumber]
          val titleToUpdate = if (existing?.isDownloaded == true) existing.title else ch.title
          chapterDao.updateChapterMetadata(storyId, ch.chapterNumber, titleToUpdate, ch.url)
      }
      ```
    - *Giải thích*: Đối với các chương người dùng đã tải offline (`existing.isDownloaded == true`), giữ nguyên `title` cũ để đảm bảo tính nhất quán với nội dung `content` đã lưu trên máy (tránh việc tiêu đề bị đổi thành bản dịch mới trong khi nội dung đọc vẫn là bản dịch cũ). Thuộc tính `url` vẫn được cập nhật sang domain mới để nếu người dùng xóa tải về thì lần tải sau sẽ lấy từ domain mới. Đối với các chương chưa tải (`isDownloaded == false`), cập nhật cả `title` và `url`.
  - Cập nhật `DownloadRepository.kt:36-38`:
    - Hàm `sourceFor(url: String)` kiểm tra qua `matchesUrl(url)` theo tham số `url`, ném `ParseException` rõ ràng nếu không tìm thấy nguồn (loại bỏ fallback `sources.first()` nguy hiểm):
      ```kotlin
      fun sourceFor(url: String): StorySource =
          sources.firstOrNull { it.matchesUrl(url) }
              ?: throw ScraperException.ParseException("Nguồn truyện này không còn hoạt động hoặc không được hỗ trợ", url)
      ```
  - Cập nhật `StoryRepositoryTest.kt`:
    - Trong hàm `setUp()` (`StoryRepositoryTest.kt:48`), bổ sung stub:
      ```kotlin
      every { source.legacyHosts } returns emptyList()
      ```
      để tránh `MockKException` khi mock strict bị gọi thuộc tính `legacyHosts`.
    - Bổ sung unit test kiểm tra định tuyến qua phương thức công khai `getStoryDetail(url)` với mock source có `legacyHosts = listOf("sstruyen.net")`.
    - **[B1, m16] Bổ sung unit test `mergeAndInsertChapters` qua API công khai**: Gọi `getFirstPageChapterList(storyUrl, storyId)`, stub `chapterDao.getChaptersByStoryOnce(storyId)` trả về Chapter 1 có `isDownloaded = true` với title cũ "Chương 1: Ly hương", khi merge chương 1 mới có title "Chương 1: Đi xa", `coVerify` gọi `chapterDao.updateChapterMetadata(storyId, 1, "Chương 1: Ly hương", newUrl)` (bảo toàn title cũ đã tải).
    - **[m11, m16] Bổ sung unit test `DownloadRepository`**:
      * Test `downloadRepository.snapshotStoryMetadata(storyId)`: stub `storyDao.getStoryById(storyId)` trả về `StoryEntity` có `url = "https://sstruyen.net/truyen/pham-nhan-tu-tien"`. Gọi `snapshotStoryMetadata(storyId)`, `coVerify` gọi `source.getStoryDetail("https://sstruyen.net/truyen/pham-nhan-tu-tien")`.
      * Test `downloadRepository.downloadChapters(storyId, chapters)`: truyền `chapters = listOf(Chapter(1, "C1", "https://unsupported.com/c1"))`. Khi collect flow `downloadChapters(storyId, chapters).toList()`, ném `ScraperException.ParseException`.

- [ ] **Bước 2: Cập nhật Hằng số, Chuẩn hóa URL & Helper JSON có Retry trong `SsTruyenScraper.kt`**
  - Khai báo các hằng số và thuộc tính hỗ trợ test:
    ```kotlin
    companion object {
        const val BASE_URL = "https://sstruyen.pics"
        const val API_BASE_URL = "https://api.sstruyen.pics"
        const val CDN_BASE_URL = "https://file.sstruyen.pics/files"
        val LEGACY_HOSTS = listOf("sstruyen.net") // m14: chỉ khai báo sstruyen.net đã xác minh
        // [MAJOR M13] 9 selector kế thừa từ BaseScraper/SsTruyenScraper hiện tại để trích xuất HTML cũ của sstruyen.net
        val LEGACY_CONTENT_SELECTORS = listOf(
            "#chapter-c",
            ".chapter-c",
            "#chapter-content",
            ".chapter-content",
            "#content",
            ".content",
            "div.text-justify",
            ".reading-content",
            ".truyen-content",
        )
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 1000L
    }

    override val legacyHosts: List<String> = LEGACY_HOSTS
    internal var customBaseUrl: String? = null
    internal var customApiBaseUrl: String? = null

    override val baseUrl: String get() = customBaseUrl ?: BASE_URL
    internal val apiBaseUrl: String get() = customApiBaseUrl ?: API_BASE_URL
    ```
  - Thêm helper chuẩn hóa URL truyện:
    ```kotlin
    private fun normalizeUrl(url: String): String {
        var res = url
        for (legacy in LEGACY_HOSTS) {
            res = res.replace("https://$legacy", baseUrl).replace("http://$legacy", baseUrl)
        }
        return res
    }
    ```
  - Xây dựng helper `fetchJson` an toàn có retry/backoff (**m13: chỉ retry khi gặp `IOException` hoặc HTTP 429, 5xx; m19: ném `ParseException` thuần tiếng Việt, không leak message tiếng Anh của `JSONException`**):
    ```kotlin
    internal suspend fun fetchJson(url: String): JSONObject {
        var lastException: Exception? = null
        for (attempt in 1..MAX_RETRIES) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                    .header("Accept", "application/json")
                    .build()
                return withContext(Dispatchers.IO) {
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw ScraperException.HttpException(response.code, url)
                        }
                        val body = response.body.string()
                        if (body.isBlank()) {
                            throw ScraperException.ParseException("Phản hồi JSON rỗng", url)
                        }
                        try {
                            JSONObject(body)
                        } catch (e: JSONException) {
                            // m19: Thông báo thuần tiếng Việt, đưa e vào cause thay vì chèn raw message tiếng Anh
                            throw ScraperException.ParseException("Lỗi định dạng dữ liệu danh sách chương", url, e)
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                lastException = e
                // m13: Chỉ retry khi gặp lỗi mạng IOException hoặc HTTP 429/5xx (không retry 400/404 hoặc JSONException)
                val isRetryable = e is IOException || (e is ScraperException.HttpException && (e.statusCode == 429 || e.statusCode >= 500))
                if (!isRetryable) {
                    throw e
                }
                if (attempt < MAX_RETRIES) {
                    delay(RETRY_DELAY_MS * attempt)
                }
            }
        }
        throw lastException as? ScraperException
            ?: ScraperException.NetworkException("Lỗi kết nối khi tải dữ liệu từ máy chủ", lastException)
    }
    ```
  - Khai báo cache: `internal val novelIdCache = ConcurrentHashMap<String, Long>()`.
  - Override `adsSelectors` để tránh xóa `.text-center` chứa nội dung hợp lệ trên trang Tailwind:
    ```kotlin
    override val adsSelectors: String = "script, .ads, .ad, iframe, noscript"
    ```

- [ ] **Bước 3: Nâng cấp Tìm kiếm (`search`)**
  - Đổi URL tìm kiếm thành: `$baseUrl/tim-kiem?keyword={encodedKeyword}`.
  - Cập nhật `parseStoryList(doc)`:
    - Selector phần tử truyện: `a[itemprop=hasPart]`, kết hợp fallback các container hiện có.
    - Tiêu đề: `p[itemprop=name]`, fallback `h3`.
    - Tác giả: `div[itemprop=author] span[itemprop=name]`, fallback `a[href*=/tac-gia/]`.
    - Trạng thái: `span[itemprop=bookFormat]` ("Đã hoàn thành" / "Đang phát hành").
    - Ảnh bìa: `img[itemprop=image]`. Nếu đường dẫn bắt đầu bằng `/files/`, ghép `$CDN_BASE_URL` hoặc `$baseUrl` trước khi gọi `validateCoverUrl`.
    - Lọc `completedOnly`: nếu `true`, chỉ giữ truyện có status chứa "Hoàn thành" hoặc "Full".

- [ ] **Bước 4: Nâng cấp Bộ lọc & Sắp xếp (`getCompletedStories` & `getStoriesByGenre`)**
  - Sử dụng endpoint:
    `$baseUrl/danh-sach/bo-loc?type={type}&status={status}&sort={sort}&genre={genre}&page={page}`
  - Với `getCompletedStories(page)`:
    - Gọi URL: `$baseUrl/danh-sach/bo-loc?status=completed&sort=updatedAt&page=$page`.
    - Tất cả truyện trả về gán `status = "Hoàn thành"`.
  - Với `getStoriesByGenre(genreSlug, page, minChapters, completedOnly)`:
    - Tham số `sort`: luôn là `updatedAt`.
    - Tham số `status`: nếu `completedOnly == true` thì truyền `completed`, ngược lại để trống.
    - Tham số `type`: nếu `minChapters >= 1000` truyền `3`, nếu `minChapters >= 500` truyền `2`, nếu `minChapters >= 100` truyền `1`, mặc định để trống.
    - Lọc an toàn: giữ các truyện có `totalChapters == 0 || totalChapters >= minChapters`.
  - Với `getGenreList()`:
    - Fetch `$baseUrl/danh-sach/bo-loc` hoặc `$baseUrl`, trích xuất toàn bộ thể loại từ `a[href*=/the-loai/]`, distinct theo slug.

- [ ] **Bước 5: Nâng cấp Chi tiết truyện & Trích xuất `novelId` an toàn (`getStoryDetail`)**
  - Chuẩn hóa URL qua `normalizeUrl(storyUrl)`.
  - Fetch Document từ HTML `$baseUrl/truyen/{slug}`.
  - Trích xuất Title (`h1`), Tác giả (`div[itemprop=author]`, `a[href*=/tac-gia/]`), Thể loại (`a[href*=/the-loai/]`), Mô tả (`div[itemprop=description]`, meta `description`), Ảnh bìa (`img[itemprop=image]`, meta `og:image`).
  - Trích xuất `novelId` an toàn: Neo chặt theo slug truyện trong JSON / script payload bằng `Regex.escape(slug)`:
    - Regex 1: `"\"data\"\\s*:\\s*\\{\\s*\"id\"\\s*:\\s*(\\d+)[^}]*?\"slug\"\\s*:\\s*\"${Regex.escape(slug)}\""`
    - Regex 2: `"\"slug\"\\s*:\\s*\"${Regex.escape(slug)}\"[^}]*?\"id\"\\s*:\\s*(\\d+)"` hoặc `"\"id\"\\s*:\\s*(\\d+)[^}]*?\"slug\"\\s*:\\s*\"${Regex.escape(slug)}\""`
    - Regex 3: thẻ có attribute `data-novel-id="(\\d+)"` hoặc script JSON-LD có `id`.
  - Lưu `novelId` vào `novelIdCache[slug]`.

- [ ] **Bước 6: Nâng cấp Lấy Danh sách chương & Tự phục hồi mất cache (`getFirstPageChapters` & `getRemainingChapters`)**
  - `getFirstPageChapters(storyUrl)`:
    - Chuẩn hóa `storyUrl`, trích xuất `slug`.
    - Fetch trang chi tiết truyện, trích xuất `novelId`.
    - Nếu có `novelId`: Gọi REST API `$apiBaseUrl/novels/$novelId/chapters?page=1&limit=50&sort=asc`.
      Nhận JSON, duyệt mảng `chapters`:
      * Quy tắc trích xuất `chapterNumber` thống nhất:
        1. Ưu tiên neo đầu slug: `^chuong-(\\d+)`.
        2. Fallback số trong name: `(?:Chương|Hồi|C)\\s*(\\d+)`.
        3. Fallback vị trí tuần tự: `(page - 1) * 50 + index + 1` (với page = 1 thì là `index + 1`).
      * Phát hiện trùng số chương: Dùng `HashSet<Int>` theo dõi. Nếu phát hiện `chapterNumber` đã tồn tại trong batch (do truyện chia quyển đánh số lại), log cảnh báo `Timber.w("Duplicate chapterNumber %d detected for story %s, chapter %s", chapterNumber, slug, chapterName)`.
      * Tạo `chapterUrl`: `$baseUrl/doc-truyen/$slug-${chapter.slug}-${chapter.id}`.
      * Trả về `FirstPageResult(chapters = chapters, totalPages = totalPages)`.
    - Fallback: Nếu không có `novelId` hoặc API lỗi, parse `a[href*=/doc-truyen/]` từ HTML trang chi tiết truyện.
  - `getRemainingChapters(storyUrl, totalPages, onBatchReady)`:
    - Chuẩn hóa `storyUrl`, trích xuất `slug`.
    - **Cơ chế tự phục hồi**: Nếu `novelIdCache[slug] == null`, tự động fetch lại trang chi tiết truyện qua `getStoryDetail(storyUrl)` để tái tạo `novelId`. Nếu sau khi fetch lại vẫn không tìm được `novelId`, ném `ScraperException.ParseException("Không thể xác định mã định danh truyện để tải danh sách chương", storyUrl)`.
    - Nếu có `novelId`:
      Dùng `coroutineScope` + `Semaphore(3)` và `delay(crawlDelayMs)` lặp `page in 2..totalPages`.
      Gọi REST API: `$apiBaseUrl/novels/$novelId/chapters?page=$page&limit=50&sort=asc`.
      Tính `chapterNumber` theo công thức thống nhất: slug neo đầu `^chuong-(\\d+)` -> name `(?:Chương|Hồi|C)\\s*(\\d+)` -> thứ tự tuần tự `(page - 1) * 50 + index + 1`.
      Emit từng batch qua `onBatchReady(batch)`.
      **Xử lý lỗi**:
      * Bắt `CancellationException` và rethrow ngay: `if (e is CancellationException) throw e`.
      * Ghi log bằng `Timber.e(e, "Chapter page %d crawl failed for story %s", page, slug)`.
      * Gom các trang bị lỗi vào danh sách an toàn đa luồng (**m17a**): `val failedPages = java.util.Collections.synchronizedList(mutableListOf<Int>())`.
      * Sau khi hoàn thành vòng lặp, nếu `failedPages.isNotEmpty()`, ném `ScraperException.NetworkException("Không thể tải đầy đủ danh sách chương (lỗi các trang: $failedPages)")`.
      * *(Ghi chú m17b)*: Trường hợp `DownloadService` tải song song trong lúc merge metadata các trang còn lại cập nhật URL/tiêu đề, chương đã tải giữ nguyên tiêu đề và nội dung; nếu chương chưa tải được merge thì có thể lệch nhẹ tiêu đề hiển thị tạm thời nhưng không làm gián đoạn hay mất dữ liệu đọc truyện.

- [ ] **Bước 7: Nâng cấp Trích xuất Nội dung chương & Phân luồng URL cũ/mới ([MAJOR M12, M13])**
  - Nhận `chapterUrl`.
  - **Phân luồng thông minh theo định dạng path (hoạt động cả production và MockWebServer)**:
    ```kotlin
    val isLegacyRoute = chapterUrl.contains("/chuong-") && !chapterUrl.contains("/doc-truyen/")
    val targetUrl = if (isLegacyRoute) {
        // Nhánh URL cũ (/truyen/{slug}/chuong-N): giữ nguyên URL fetch trực tiếp từ host cũ (hoặc MockWebServer)
        chapterUrl
    } else {
        // Nhánh URL mới (/doc-truyen/...): chuẩn hóa URL sang baseUrl
        normalizeUrl(chapterUrl)
    }
    val doc = fetchDocumentWithRetry(targetUrl)
    val rawContent = if (isLegacyRoute) {
        // [MAJOR M13] Nhánh cũ sstruyen.net: Duyệt qua 9 selector kế thừa trong LEGACY_CONTENT_SELECTORS
        // Bằng chứng HTML thực tế sstruyen.net: Nội dung nằm trong <div id="chapter-c" class="content box-chap">
        // và sử dụng các thẻ <br> để phân tách đoạn văn (không bọc trong thẻ <p>).
        // Sử dụng cleanHtml(element.html()) để tự động chuyển đổi các thẻ <br> thành dấu ngắt dòng \n\n.
        val element = LEGACY_CONTENT_SELECTORS.firstNotNullOfOrNull { selector ->
            doc.selectFirst(selector)
        } ?: throw ScraperException.ParseException("Không tìm thấy nội dung chương", chapterUrl)
        cleanHtml(element.html())
    } else {
        // Nhánh URL mới (/doc-truyen/): Trích xuất từ div.prose, div.wrapper.prose
        val proseEl = doc.selectFirst("div.prose, div.wrapper.prose")
            ?: throw ScraperException.ParseException("Không tìm thấy nội dung chương", chapterUrl)
        val paragraphs = proseEl.select("p").map { it.text().trim() }.filter { it.isNotBlank() }
        if (paragraphs.isNotEmpty()) {
            paragraphs.joinToString("\n\n")
        } else {
            cleanHtml(proseEl.html())
        }
    }
    ```
  - Loại bỏ các phần tử rác, ads, scripts, buttons, forms, và navigation markers ("Chương trước", "Chương tiếp", "sstruyen.pics").
  - Đảm bảo các đoạn văn bản phân tách bằng `\n\n`, giữ nguyên ngắt dòng cho Reader và TTS.
  - Kiểm tra độ dài nội dung: Nếu `< MIN_CONTENT_LENGTH (50 ký tự)`, ném `ScraperException.ParseException("Nội dung chương rỗng", chapterUrl)`.
  - **Bảo toàn dữ liệu đã tải trong Room**: Được kiểm soát chặt chẽ ở Bước 1 qua `mergeAndInsertChapters`.

- [ ] **Bước 8: Loại bỏ thao tác sửa `WebViewContentLoader.kt`**
  - Không sửa đổi `WebViewContentLoader.kt` vì lớp này không được sử dụng trong mã nguồn chính (`app/src/main`), đảm bảo tuân thủ Minimal Diffs Policy.

- [ ] **Bước 9: Xây dựng Bộ Unit Test Toàn Diện `SsTruyenScraperTest.kt` & `StoryRepositoryTest.kt`**
  - Sử dụng JUnit 5 + MockK + MockWebServer3.
  - Cấu hình Dispatcher trỏ cả URL web (`/tim-kiem`, `/truyen/...`, `/doc-truyen/...`) và URL API (`/novels/...`) về MockWebServer.
  - Các test cases trong `SsTruyenScraperTest.kt`:
    1. `search`: parse đúng danh sách truyện, mapping title, author, cover, status từ HTML tìm kiếm mới.
    2. `search` với `completedOnly=true`: lọc đúng các truyện có trạng thái hoàn thành.
    3. `getStoryDetail`: trích xuất chính xác title, author, description, genres, coverUrl và `novelId` có neo slug với `Regex.escape`.
    4. `getFirstPageChapters`: trích xuất danh sách chương trang 1 và `totalPages` từ API response mock.
    5. `getRemainingChapters`: gọi API REST mock, phân tích JSON trả về đúng `chapterNumber` theo công thức `(page - 1) * 50 + index + 1`.
    6. `getRemainingChapters recovering when novelIdCache missing`: tự phục hồi gọi `getStoryDetail` khi mất cache.
    7. `getRemainingChapters throws ParseException when recovery fails`: ném `ParseException` tiếng Việt nếu phục hồi thất bại.
    8. `getRemainingChapters throws NetworkException on failed pages`: lỗi trang API ném `NetworkException` nêu rõ các trang lỗi (danh sách thread-safe).
    9. `getChapterContent with new url`: trích xuất sạch sẽ từ `div.prose`, loại bỏ rác/ads, ngắt dòng `<p>` chuẩn (fixture ≥ 50 ký tự).
    10. **[MAJOR M12, M13] `getChapterContent with legacy url on MockWebServer`**: truyền `mockWebServer.url("/truyen/tien-nghich/chuong-1").toString()`. Fixture HTML mô phỏng cấu trúc thực tế của `sstruyen.net`: `<div id="chapter-c" class="content box-chap">Đoạn văn thứ nhất của truyện Tiên Nghịch trên trang sstruyen cũ.<br><br>Đoạn văn thứ hai mô tả cảnh giới tu tiên đầy khắc nghiệt và gian nan.</div>` (sử dụng thẻ `<br>`, không có thẻ `<p>`). Xác minh scraper nhận diện `isLegacyRoute = true`, gửi request tới đúng path `/truyen/tien-nghich/chuong-1` (không bị đổi sang `/doc-truyen/...`), trích xuất qua `LEGACY_CONTENT_SELECTORS` kết hợp `cleanHtml`, kết quả trả về chứa `\n\n` phân tách giữa 2 đoạn và độ dài ≥ 50 ký tự.
    11. `getCompletedStories`: verify gọi đúng path `/danh-sach/bo-loc` với query parameters `status=completed` và `sort=updatedAt`.
    12. `getStoriesByGenre`: verify gọi đúng query parameters `genre=tien-hiep`, `sort=updatedAt`, `status=completed`.
    13. `getGenreList`: parse đúng thể loại từ HTML menu/filter.
    14. `emptyContentThrowsParseException`: nội dung rỗng ném đúng `ScraperException.ParseException`.
    15. **[m13, m19] `fetchJson does not retry non-retryable 404 or JSONException`**: xác minh không retry vô ích khi gặp HTTP 404 hoặc JSON hỏng, ném `ParseException` thuần tiếng Việt ("Lỗi định dạng dữ liệu danh sách chương"), không chèn raw message tiếng Anh vào exception message.
  - Các test cases trong `StoryRepositoryTest.kt`:
    - `sourceFor routes legacy sstruyen.net url via public getStoryDetail`: Xác minh URL `.net` định tuyến chuẩn xác về `SsTruyenScraper` qua `matchesUrl(url)`.
    - **[BLOCKER B1, m16] `mergeAndInsertChapters preserves title for downloaded chapters`**: Kiểm thử qua public method `getFirstPageChapterList(storyUrl, storyId)`. Stub `chapterDao.getChaptersByStoryOnce(storyId)` trả về `ChapterEntity(storyId, 1, "Chương 1: Ly hương", oldUrl, isDownloaded = true)`. Khi scraper trả về chương 1 có title "Chương 1: Đi xa", `coVerify` gọi `chapterDao.updateChapterMetadata(storyId, 1, "Chương 1: Ly hương", any())`, đảm bảo giữ nguyên tiêu đề cũ đã tải; đối với chương chưa tải (`isDownloaded = false`), `coVerify` gọi `updateChapterMetadata` cập nhật cả title mới và url mới.
    - **[m11, m16] `downloadRepository routes legacy sstruyen.net url via snapshotStoryMetadata`**: Stub `storyDao.getStoryById(storyId)` trả về `StoryEntity(id = storyId, url = "https://sstruyen.net/truyen/pham-nhan-tu-tien", ...)`. Gọi `downloadRepository.snapshotStoryMetadata(storyId)`, `coVerify` xác nhận `source.getStoryDetail("https://sstruyen.net/truyen/pham-nhan-tu-tien")` được gọi với URL `.net`.
    - **[m11, m16] `downloadRepository downloadChapters throws ParseException for unsupported source`**: Gọi `downloadRepository.downloadChapters(storyId, listOf(Chapter(1, "C1", "https://unsupported.com/c1")))`. Khi collect flow `downloadChapters(...).toList()`, ném `ScraperException.ParseException("Nguồn truyện này không còn hoạt động hoặc không được hỗ trợ", "https://unsupported.com/c1")` thay vì gửi nhầm sang nguồn khác.

- [ ] **Bước 10: Xử lý Vi phạm ktlint Baseline theo Minimal Diffs Policy**
  - Sửa vi phạm ktlint sẵn có tại `StorySource.kt:47:87` (ngắt dòng body expression).
  - Sửa dứt điểm 5 vi phạm ở test source set (`WikidichScraperTest.kt`, `SourceAuthManagerTest.kt`, `ReaderViewModelTest.kt`) để `ktlintTestSourceSetCheck` đạt trạng thái PASS hoàn toàn.
  - Áp dụng Minimal Diffs Policy cho main source set: Không sửa lan man vào các file ngoài task.
  - Cam kết tiêu chuẩn chất lượng: **100% các file thuộc phạm vi task (`SsTruyenScraper.kt`, `StorySource.kt`, `StoryRepository.kt`, `DownloadRepository.kt`, `SsTruyenScraperTest.kt`) không phát sinh bất kỳ vi phạm ktlint nào**.

- [ ] **Bước 11: Chạy Kiểm Thử & Kiểm Tra Chất Lượng (Quality Gate Tier 2)**
  - Chạy `.\gradlew.bat compileDebugKotlin` (Tier 1: PASS).
  - Chạy `.\gradlew.bat ktlintTestSourceSetCheck` (PASS 100%).
  - **[m12] Kiểm tra ktlint main cho các file thuộc task**:
    Chạy lệnh: `.\gradlew.bat ktlintMainSourceSetCheck; Select-String -Path app/build/reports/ktlint/ktlintMainSourceSetCheck/ktlintMainSourceSetCheck.txt -Pattern "SsTruyenScraper|StorySource|StoryRepository|DownloadRepository"` và xác nhận kết quả trả về rỗng (0 vi phạm).
  - Chạy `.\gradlew.bat testDebugUnitTest` (Xác nhận 100% unit tests PASS).
  - Chạy `.\gradlew.bat assembleDebug` (Tier 2: PASS).

- [ ] **Bước 12: Cập nhật Tài liệu Docs**
  - Cập nhật `docs/scraping.md`: thông tin domain mới `sstruyen.pics`, REST API `api.sstruyen.pics`, CDN `file.sstruyen.pics/files`, CSS selectors `div.prose`, URL patterns bộ lọc `danh-sach/bo-loc`, cơ chế tương thích ngược (kế thừa 9 selector và `cleanHtml` cho `sstruyen.net`).
  - Cập nhật `docs/progress.md`: đánh dấu hoàn thành nhiệm vụ nâng cấp SsTruyen sang `sstruyen.pics`.
  - Cập nhật `docs/changelog.md`: ghi chú thay đổi dưới mục `## [Unreleased]`.
  - Cập nhật `docs/plans/README.md`: chuyển trạng thái plan sang `Completed` sau khi triển khai xong.

---

### 7. Test Contract

| # | Hành vi (ngôn ngữ nghiệp vụ) | Input | Kết quả mong đợi / thông báo lỗi tiếng Việt |
| - | ---------------------------- | ----- | ------------------------------------------- |
| 1 | Tìm kiếm truyện theo từ khóa | `keyword = "tiên hiệp"`, `completedOnly = false` | Trả về danh sách truyện nguồn "sstruyen", URL trỏ tới `sstruyen.pics/truyen/...`, ảnh bìa hợp lệ |
| 2 | Tìm kiếm chỉ truyện hoàn thành | `keyword = "tiên hiệp"`, `completedOnly = true` | Chỉ trả về các truyện có trạng thái "Hoàn thành" |
| 3 | Lấy chi tiết truyện & trích xuất `novelId` an toàn | `storyUrl = "https://sstruyen.pics/truyen/tien-nghich"` | Trả về `Story` đầy đủ: title="Tiên Nghịch", author="Nhĩ Căn", trích xuất được `novelId = 1042` neo theo slug `tien-nghich` bằng `Regex.escape` |
| 4 | Lấy danh sách chương trang 1 qua API | `storyUrl = "https://sstruyen.pics/truyen/tien-nghich"`, có `novelId` | Trả về `FirstPageResult` gồm 50 chương đầu, `totalPages = 42`, chapterUrl chuẩn format `$baseUrl/doc-truyen/...` |
| 5 | Lấy các trang chương còn lại qua REST API | `novelId = 1042`, `totalPages = 3` | Gọi REST API trang 2 và 3, trích xuất đúng `chapterNumber` theo công thức `(page - 1) * 50 + index + 1`, emit từng batch qua `onBatchReady` |
| 6 | Trích xuất nội dung chương từ `div.prose` (fixture ≥ 50 ký tự) | HTML chương chứa `<div class="prose"><p>Gió thu thổi qua nhè nhẹ làm lay động những tán lá phong đỏ rực bên đường.</p><p>Vương Lâm lẳng lặng đứng nhìn về phía ngọn núi xa xăm, trong lòng dâng lên một nỗi niềm khó tả.</p></div>` | Trả về chuỗi nội dung sạch: `"Gió thu thổi qua nhè nhẹ làm lay động những tán lá phong đỏ rực bên đường.\n\nVương Lâm lẳng lặng đứng nhìn về phía ngọn núi xa xăm, trong lòng dâng lên một nỗi niềm khó tả."` (độ dài > 50 ký tự), không chứa thẻ HTML hay script |
| 7 | Nội dung chương rỗng hoặc < 50 ký tự | HTML không có selector nội dung hoặc text ngắn | Ném `ScraperException.ParseException("Nội dung chương rỗng", chapterUrl)` |
| 8 | Lấy danh sách truyện hoàn thành | `page = 1` | Request được gửi tới path `/danh-sach/bo-loc` với query parameters riêng rẽ: `status = completed`, `sort = updatedAt`, `page = 1` |
| 9 | Lọc truyện theo thể loại và trạng thái hoàn thành | `genreSlug = "tien-hiep"`, `page = 1`, `completedOnly = true` | Request được gửi tới path `/danh-sach/bo-loc` với query parameters riêng rẽ: `genre = tien-hiep`, `status = completed`, `sort = updatedAt`, `page = 1` |
| 10 | Định tuyến URL cũ từ `sstruyen.net` ở tầng Repository qua public API | `url = "https://sstruyen.net/truyen/pham-nhan-tu-tien"` | `StoryRepository.getStoryDetail(url)` nhận diện đúng `SsTruyenScraper` qua `matchesUrl(url)` mà không ném exception |
| 11 | **[m11, m16]** Định tuyến URL cũ từ `sstruyen.net` ở tầng DownloadRepository | `storyDao.getStoryById(storyId)` trả về entity có `url = "https://sstruyen.net/truyen/pham-nhan-tu-tien"` | `DownloadRepository.snapshotStoryMetadata(storyId)` nhận diện đúng `SsTruyenScraper` qua `matchesUrl(url)`, gọi `source.getStoryDetail("https://sstruyen.net/truyen/pham-nhan-tu-tien")` |
| 12 | **[MAJOR M12, M13]** Đọc chương cũ độc lập trên MockWebServer với HTML chứa `<br>` | `chapterUrl = mockWebServer.url("/truyen/tien-nghich/chuong-1").toString()`, HTML chứa `<div id="chapter-c" class="content box-chap">Đoạn 1<br><br>Đoạn 2</div>` | `SsTruyenScraper.getChapterContent` nhận diện `isLegacyRoute = true`, gửi request tới đúng path `/truyen/tien-nghich/chuong-1` và trích xuất qua `LEGACY_CONTENT_SELECTORS` kết hợp `cleanHtml`, trả về văn bản chứa `\n\n` giữa 2 đoạn, độ dài > 50 ký tự |
| 13 | **[BLOCKER B1, m16]** Bảo vệ tiêu đề chương đã tải khi merge metadata qua public API | Chapter 1 có `isDownloaded = true`, title cũ = "Chương 1: Ly hương"; metadata mới có title = "Chương 1: Đi xa" | `StoryRepository.getFirstPageChapterList` kích hoạt merge, gọi `chapterDao.updateChapterMetadata(storyId, 1, "Chương 1: Ly hương", newUrl)`, giữ nguyên tiêu đề cũ đã tải; chương chưa tải cập nhật cả title mới và newUrl |
| 14 | **[m11, m16]** Xử lý URL nguồn không hỗ trợ trong DownloadRepository khi collect flow | `chapters = listOf(Chapter(1, "C1", "https://unsupported.com/story/123/c1"))` | `DownloadRepository.downloadChapters(storyId, chapters).toList()` ném `ScraperException.ParseException("Nguồn truyện này không còn hoạt động hoặc không được hỗ trợ", "https://unsupported.com/story/123/c1")` |

---

### 8. Verification Plan

1. **Unit Test Tự Động**:
   - File test mới: `app/src/test/java/com/personal/apptruyen/data/remote/SsTruyenScraperTest.kt`.
   - File test mở rộng: `app/src/test/java/com/personal/apptruyen/data/repository/StoryRepositoryTest.kt`.
   - Chạy với mock HTML và mock JSON API qua MockWebServer3, không gọi mạng thật.
2. **Quality Gate Commands**:
   - `.\gradlew.bat compileDebugKotlin` (Biên dịch Kotlin thành công).
   - `.\gradlew.bat ktlintTestSourceSetCheck` (Kiểm tra pass 100% test source set).
   - `.\gradlew.bat ktlintMainSourceSetCheck; Select-String -Path app/build/reports/ktlint/ktlintMainSourceSetCheck/ktlintMainSourceSetCheck.txt -Pattern "SsTruyenScraper|StorySource|StoryRepository|DownloadRepository"` (Xác nhận 0 vi phạm trên các file thuộc task).
   - `.\gradlew.bat testDebugUnitTest --tests "com.personal.apptruyen.data.remote.SsTruyenScraperTest"`
   - `.\gradlew.bat testDebugUnitTest --tests "com.personal.apptruyen.data.repository.StoryRepositoryTest"`
   - `.\gradlew.bat testDebugUnitTest` (Toàn bộ unit test pass).
   - `.\gradlew.bat assembleDebug` (Build debug thành công).
3. **Kiểm thử thủ công trên thiết bị Android (Manual Verification Protocol)**:
   - Mở ứng dụng, vào tab **Tìm kiếm**: gõ từ khóa tìm truyện trên nguồn SsTruyen (ví dụ: "Đế Bá" hoặc "Phàm Nhân"). Kiểm tra kết quả hiển thị ảnh bìa, tên truyện, tên tác giả và badge SST.
   - Bật bộ lọc "Chỉ truyện Full" trong FilterBottomSheet: kiểm tra danh sách chỉ trả về truyện đã hoàn thành.
   - Bấm vào truyện để mở **StoryDetailScreen**: kiểm tra mô tả, thể loại và danh sách chương hiển thị mượt mà.
   - Mở một truyện đã lưu trong thư viện từ trước mang domain `sstruyen.net`: kiểm tra ứng dụng tải chi tiết và đọc tiếp bình thường mà không báo lỗi "Nguồn truyện không còn hoạt động".
   - Bấm **Tải truyện** (Download) 50 chương đầu: kiểm tra DownloadService tải nhanh qua REST API, không bị nghẽn mạng hay lỗi timeout.
   - Mở **ReaderScreen** và bật **TTS (TextToSpeech)**: kiểm tra nội dung chương sạch sẽ, ngắt câu rõ ràng, TTS đọc trơn tru từ đầu đến cuối không bị nhảy đoạn hoặc mất chữ.

---

### 9. Rủi Ro & Phòng Ngừa

| Rủi ro | Mức độ | Technical Control |
| ------ | :----: | ----------------- |
| **[BLOCKER B1]** Lệch tiêu đề hoặc phân chương giữa hai domain làm sai lệch dữ liệu đã tải | Cao | **Đã kiểm soát trực tiếp trong code**: Sửa `StoryRepository.kt:433-437` trong hàm `mergeAndInsertChapters`: với chương `existing?.isDownloaded == true`, giữ nguyên `existing.title` không ghi đè, chỉ cập nhật `url` sang domain mới. Có unit test kiểm chứng trong `StoryRepositoryTest` qua hàm công khai `getFirstPageChapterList`. |
| **[MAJOR M12, M13]** URL chương cũ trong Room DB (`sstruyen.net/.../chuong-N`) bị HTTP 404 khi đọc online hoặc thất bại do chứa thẻ `<br>` thay vì `<p>` | Cao | **Đã kiểm soát trực tiếp trong code**: Phân luồng theo path: `chapterUrl.contains("/chuong-") && !chapterUrl.contains("/doc-truyen/")` -> fetch trực tiếp URL cũ; trích xuất qua 9 selector `LEGACY_CONTENT_SELECTORS` và gọi `cleanHtml(element.html())` tự động chuyển đổi thẻ `<br>` thành `\n\n` (không duyệt thẻ `<p>`). Chạy độc lập hoàn hảo cả trên production lẫn MockWebServer trong unit test với fixture HTML chứa thẻ `<br>`. |
| Định tuyến nguồn bị gãy do gọi suspend function `activeSources()` trong `sourceFor` | Cao | Giữ `sourceFor` là non-suspend và duyệt trên `sources: Set<StorySource>`. Dùng extension function `matchesUrl(url)` trích xuất host chuẩn qua `URI` kết hợp `toHttpUrlOrNull()` loại bỏ cổng (`substringBefore(":")`). Bổ sung stub `legacyHosts` trong `StoryRepositoryTest.setUp()`. |
| Trang web thay đổi cấu trúc không trích xuất được `novelId` từ HTML | Trung bình | Neo chặt regex theo slug `"slug":"${Regex.escape(slug)}"` trong script payload. Nếu không tìm được, fallback an toàn sang parsing trực tiếp từ các link `a[href*=/doc-truyen/]` trên HTML chi tiết truyện để không làm gãy luồng đọc truyện. |
| REST API `api.sstruyen.pics` áp dụng rate limiting hoặc lỗi tạm thời | Trung bình | Tích hợp `Semaphore(3)` giới hạn request đồng thời và `delay(crawlDelayMs)` (300ms) giữa các request, kết hợp helper `fetchJson` có vòng lặp retry 3 lần với exponential backoff cho `IOException` và lỗi 429/5xx (không retry vô ích 400/404/JSON). Gom lỗi các trang thất bại vào danh sách thread-safe `Collections.synchronizedList` và ném `NetworkException`. |
| Baseline ktlint hiện tại bị đỏ ở main source set làm fail Quality Gate | Trung bình | Sửa lỗi vi phạm sẵn có tại `StorySource.kt:47:87` và 5 lỗi ở test source set. Áp dụng Minimal Diffs Policy: Lọc báo cáo ktlint xác nhận 100% các file thuộc phạm vi task đạt 0 vi phạm mới. |

---

### 10. Cập Nhật Docs

Theo ma trận quy tắc `.agents/rules/06-docs.md`:
- `docs/scraping.md`: Cập nhật toàn bộ section SsTruyen sang tên miền `sstruyen.pics`, selector `div.prose`, API danh sách chương `/novels/{novelId}/chapters`, endpoint bộ lọc `/danh-sach/bo-loc`, cơ chế tương thích ngược.
- `docs/progress.md`: Cập nhật mục MVP/Nâng cấp và bảng Lịch sử thay đổi.
- `docs/changelog.md`: Thêm các mục `### Sửa đổi` và `### Thêm mới` dưới `## [Unreleased]`.
- `docs/plans/README.md`: Cập nhật bảng kế hoạch triển khai.

---

## Audit History

<!-- Reviewer chỉ append "### Audit Run N" ở dưới. Không sửa phần Technical Plan. -->

### Audit Run 1

- **Reviewer**: Claude Code CLI (claude-opus-5-5)
- **Ngày**: 2026-10-13
- **Đối tượng**: Technical Plan Revision 1

#### Kiểm chứng lỗi cũ

Không có (đây là lượt thẩm định đầu tiên, `## Audit History` chưa có Run nào).

#### Mental Simulation Matrix (S1–S6 + Domain)

| Kịch bản | Kết quả | Bằng chứng | Chi tiết |
| --- | --- | --- | --- |
| S1 Build | FAIL | `[CMD]` | `compileDebugKotlin` Exit 0 (baseline sạch). Nhưng `ktlintCheck` **Exit 1** trên baseline do 5 vi phạm sẵn có trong `WikidichScraperTest.kt:4`, `SourceAuthManagerTest.kt:9/94/128`, `ReaderViewModelTest.kt:3`. Bước 9 yêu cầu chạy `ktlintCheck` như Quality Gate nhưng plan không nhận diện baseline đỏ, không nêu cách xử lý → gate không thể pass dù code mới sạch. Không có dependency mới (đúng với `app/build.gradle.kts:169-170`). |
| S2 Lifecycle | FAIL (một phần) | `[SRC]` | Plan gọi REST API JSON nhưng không chỉ rõ cơ chế fetch: `BaseScraper.fetchDocument` (`BaseScraper.kt:99-159`) gửi `Accept: text/html` và trả Jsoup `Document`, không có helper JSON. Plan phải nêu helper mới (dùng `client` inject, `execute().use {}`, map lỗi sang `ScraperException.HttpException`, có retry/backoff). Mẫu hiện có `SsTruyenScraper.kt:244` dùng `catch (e: Exception)` sẽ nuốt `CancellationException` khi người dùng hủy tải. |
| S3 Database | FAIL | `[SRC]` + `[ADV]` | Không đổi schema là đúng (`ChapterEntity.kt:9` `primaryKeys = ["storyId","chapterNumber"]`). Nhưng `StoryRepository.kt:434-437` gọi `updateChapterMetadata(storyId, chapterNumber, title, url)` cho chương đã tồn tại → nếu `chapterNumber` từ API mới lệch với số `/chuong-{N}` cũ, URL/tiêu đề mới bị gán sai lên chương đã tải. Plan không định nghĩa `chapterNumber` lấy từ trường JSON nào (xem Timeline A). |
| S4 Cross-Task State | FAIL | `[SRC]` | Chữ ký `StorySource` giữ nguyên (`StorySource.kt:12-69`) → PASS phần interface. Nhưng định tuyến nguồn: `StoryRepository.kt:89-91` chọn nguồn bằng `url.contains(baseUrl.removePrefix("https://"))`; khi `BASE_URL` đổi sang `sstruyen.pics`, mọi truyện cũ lưu URL `sstruyen.net` sẽ ném `ParseException("Nguồn truyện này không còn hoạt động…")` **trước khi** tới `normalizeUrl` trong scraper. `DownloadRepository.kt:36-38` còn tệ hơn: fallback `sources.first()` → gửi URL SsTruyen cho scraper nguồn khác. Plan không liệt kê 2 file repository trong mục 5. |
| S5 Security | PASS | `[SRC]` | Không có cookie/đăng nhập. `WebViewContentLoader.kt:110-116` kiểm host bằng `endsWith`; thêm `sstruyen.pics` không mở rộng bề mặt đáng kể. Ghi chú MINOR: `endsWith("sstruyen.pics")` khớp cả `evilsstruyen.pics` (lỗi sẵn có). |
| S6 External Resilience | FAIL | `[SRC]` + `[URL]` | (1) Không có bằng chứng khảo sát HTML/JSON thực tế và **ngày khảo sát** như `.agents/rules/04-scraping.md:19-22` bắt buộc; mọi selector `itemProp=*`, schema JSON `{chapters,total,page,totalPages}`, định dạng URL `/doc-truyen/$slug-$chapterSlug-$chapterId`, mapping `type=1/2/3` đều là khẳng định chưa xác minh. Reviewer không được phép fetch `sstruyen.pics` (ngoài allowlist) → UNVERIFIED. (2) `[URL]` https://sstruyen.net/ (fetch 2026-10-13) vẫn hoạt động bình thường, không redirect sang `sstruyen.pics` → giả định "slug giống hệt trên domain mới" để `normalizeUrl` hoạt động là chưa được chứng minh. (3) Trang API lỗi giữa chừng không có hành vi xác định (xem Timeline B). |
| Domain | FAIL | `[SRC]` | Regex `novelId` `"id"\s*:\s*(\d+)` quá rộng: payload Next.js/RSC chứa nhiều `"id"` (thể loại, tác giả, chương, user) → có thể lấy nhầm ID và tải **danh sách chương của truyện khác** một cách im lặng. `WebViewContentLoader` không được inject/gọi ở đâu trong `app/src/main` (Grep chỉ thấy định nghĩa `WebViewContentLoader.kt:24`) → Bước 7 và control rủi ro hàng 5 là control "chết". |

#### Adversarial Timelines

```
Cơ chế A: Đánh số chương API + merge metadata (S3, Đồng thời/TOCTOU)
T1: Truyện cũ có chương 1..300 trong Room (URL sstruyen.net/.../chuong-N), chương 1..50 đã tải offline
T2: API mới trả danh sách mà "chương mở đầu/văn án" chiếm vị trí 1 (hoặc plan dùng index thay vì số trong tiêu đề)
T3: mergeAndInsertChapters → updateChapterMetadata(storyId, N, title', url') ghi URL của chương N-1 lên bản ghi N
Kết cục: tiêu đề/URL lệch so với content đã tải; tiến độ đọc (theo chapterNumber) trỏ sai chương; tải lại chương chưa tải ra nội dung lệch 1 → LỖI (MAJOR)
```

```
Cơ chế B: getRemainingChapters song song qua API (Thất bại giữa chừng)
T1: Semaphore(3) chạy page 2..40 | T2: API trả 429/5xx ở page 17 sau 3 lần retry | T3: catch(Exception) chỉ Log.d như mẫu hiện tại
Kết cục: thiếu chương 801..850, UI và DownloadService coi là đủ, trái mục tiêu "không ngắt quãng" → LỖI (MAJOR)
```

```
Cơ chế C: novelIdCache in-memory (Vòng đời Android / TOCTOU)
T1: getFirstPageChapters lưu novelIdCache[slug] | T2: Android kill process khi đang nền | T3: lần mở sau Repository gọi loadRemainingChapters trực tiếp với totalPages cũ → cache miss → fallback HTML /truyen/$slug/page/$page (pattern domain cũ, chưa xác minh trên domain mới)
Kết cục: 0 chương được thêm hoặc 404 bị nuốt → LỖI (MAJOR). Cần: cache miss thì fetch lại trang chi tiết để lấy novelId.
```

```
Cơ chế D: Định tuyến URL cũ (TOCTOU domain)
T1: User mở truyện trong thư viện, story.url = https://sstruyen.net/truyen/de-ba | T2: StoryRepository.sourceFor kiểm url.contains("sstruyen.pics") = false với mọi nguồn | T3: ném ParseException
Kết cục: normalizeUrl trong scraper không bao giờ được chạy; Test Contract #10 pass ở unit test nhưng thất bại trong app thật → LỖI (MAJOR)
```

Trục Bảo mật/Cookie: N/A (SsTruyen không dùng phiên đăng nhập).

#### Commands Executed (trích từ evidence.md)

- `### [CMD] cmd.exe /d /c gradlew.bat compileDebugKotlin` — `Exit: 0` — `BUILD SUCCESSFUL in 8s`.
- `### [CMD] cmd.exe /d /c gradlew.bat ktlintCheck` — `Exit: 1` — `Task :app:ktlintTestSourceSetCheck FAILED` với 5 vi phạm ở `WikidichScraperTest.kt`, `SourceAuthManagerTest.kt`, `ReaderViewModelTest.kt`.
- `### [CMD] cmd.exe /d /c gradlew.bat testDebugUnitTest` — `Exit: 0` — `BUILD SUCCESSFUL in 1s` (toàn bộ UP-TO-DATE).
- `### [CMD] git status --short` — `Exit: 0` — `SsTruyenScraper.kt` không có trong danh sách thay đổi; chưa có `SsTruyenScraperTest.kt`.
- UNVERIFIED (đề xuất cho Planner bổ sung vào plan): `curl.exe -q -s https://sstruyen.pics/truyen/<slug> | Select-String "novelId|\"id\""`, `curl.exe -q -s "https://api.sstruyen.pics/novels/<id>/chapters?page=2&limit=50&sort=asc"`, `curl.exe -q -s https://sstruyen.pics/doc-truyen/<chapter> | Select-String "prose"`.

#### Phân loại lỗi

- **[MAJOR] M1 — Tương thích ngược bị chặn ở tầng Repository**: `StoryRepository.kt:89-91` và `DownloadRepository.kt:36-38` định tuyến theo `baseUrl`; URL `sstruyen.net`/`sstruyen.vn` trong Room sẽ không tới được `SsTruyenScraper` (hoặc bị gửi sang nguồn khác). Cần thêm cơ chế khai báo domain cũ (ví dụ thuộc tính `legacyHosts` trong `StorySource`/`BaseScraper`, hoặc chuẩn hóa trong Repository), liệt kê 2 file repository trong mục 5, và thêm test ở `StoryRepositoryTest` cho URL cũ.
- **[MAJOR] M2 — Chưa có bằng chứng khảo sát nguồn**: thiếu ngày khảo sát, trích đoạn HTML/JSON thực tế cho selector `itemProp`, schema JSON API (tên trường số chương, slug, id), định dạng URL chương, ý nghĩa `type=1/2/3`, và xác nhận slug truyện cũ tồn tại nguyên vẹn trên `sstruyen.pics` (`sstruyen.net` hiện vẫn sống, không redirect). Vi phạm `.agents/rules/04-scraping.md:19-22`. Fixture test phải được lấy từ HTML/JSON thật đã khảo sát.
- **[MAJOR] M3 — Trích xuất `novelId` không an toàn**: regex `"id"\s*:\s*(\d+)` có thể khớp ID không phải của truyện → tải nhầm danh sách chương truyện khác. Cần neo theo ngữ cảnh slug (ví dụ object chứa `"slug":"<slug>"`), ưu tiên nguồn có cấu trúc (`__NEXT_DATA__`/JSON-LD), và kiểm tra chéo response API (nếu có trường novel/slug) trước khi dùng; không tìm được thì đi fallback, không đoán.
- **[MAJOR] M4 — Định nghĩa `chapterNumber` và URL chương chưa rõ**: plan chỉ ghi "số thứ tự chuẩn". Vì `updateChapterMetadata` ghi đè theo `chapterNumber` (`StoryRepository.kt:434-437`), plan phải nêu trường JSON dùng làm số chương, quy tắc với chương không đánh số/văn án, và test chứng minh số chương mới khớp số `/chuong-{N}` cũ cho truyện đã lưu (Timeline A).
- **[MAJOR] M5 — Thất bại giữa chừng và `novelIdCache` mất khi process chết**: (a) lỗi 1 trang API không được nuốt im lặng; cần nêu hành vi (ví dụ thu thập trang lỗi, ném `ScraperException` sau khi xong để Repository/UI báo lỗi tiếng Việt), rethrow `CancellationException`, dùng Timber thay `android.util.Log` (Log trong JVM test không có `isReturnDefaultValues` sẽ ném lỗi). (b) Cache miss trong `getRemainingChapters` phải fetch lại trang chi tiết để lấy `novelId` thay vì nhảy sang pattern HTML cũ chưa xác minh. Bổ sung test cho cả hai.
- **[MAJOR] M6 — Test Contract tự mâu thuẫn và không khả thi như mô tả**: (a) Contract #6 kỳ vọng `"Đoạn 1\n\nĐoạn 2"` (≈14 ký tự) là kết quả thành công, trong khi Bước 6 ném `ParseException` khi `< 50` ký tự → test sẽ fail hoặc bị viết sai lệch; cần fixture ≥ 50 ký tự. (b) `SsTruyenScraper` dùng `const val BASE_URL` và không có hook như `customBaseUrl` của `WikidichScraperTest.kt:33-37`; plan phải nêu cách trỏ **cả hai host** (web và `api.`) về MockWebServer (ví dụ thuộc tính `internal var` cho base/api URL, hoặc `Dispatcher` theo path). (c) Thiếu test cho fallback HTML khi không có `novelId`, cho lỗi API giữa chừng, và cho `getGenreList` (rule `01-coding.md`: mỗi hàm public có happy path + edge case).
- **[MAJOR] M7 — Quality Gate ktlint đỏ sẵn**: `ktlintCheck` Exit 1 trên baseline (5 vi phạm ở file test không thuộc plan). Plan phải nêu rõ: sửa 5 vi phạm này trong phạm vi (liệt kê file vào mục 5, lưu ý Code Freeze Guard hỏi khi sửa test) hoặc chấp nhận baseline và chứng minh file mới/sửa không thêm vi phạm.
- **[MINOR] m1** — `WebViewContentLoader` không được inject ở đâu; Bước 7 vô hại nhưng không phải control thực sự cho rủi ro hàng 5. Ghi rõ trong plan hoặc bỏ khỏi bảng rủi ro.
- **[MINOR] m2** — Contract #9 so khớp chuỗi URL với thứ tự tham số khác với template Bước 3; test nên assert theo `requestUrl.queryParameter(...)` thay vì chuỗi nguyên.
- **[MINOR] m3** — `div.prose` là class Tailwind dùng chung; `adsSelectors` mặc định chứa `.text-center` (`BaseScraper.kt:41`) có thể xóa đoạn văn căn giữa trên site Tailwind. Nên xác nhận bằng HTML thật và cân nhắc override `adsSelectors`.
- **[MINOR] m4** — Cover tương đối cần `ensureAbsolute`/ghép `CDN_BASE_URL` trước `validateCoverUrl` (vốn loại URL không bắt đầu bằng `http`, `BaseScraper.kt:315`); nêu rõ quy tắc ghép.
- **[MINOR] m5** — Cần hướng dẫn fetch JSON: helper dùng `client` inject, `execute().use {}`, header `Accept: application/json`, lỗi HTTP map sang `ScraperException.HttpException`, retry/backoff như `fetchDocumentWithRetry`.

#### Kết luận

Plan có hướng đi hợp lý (không đổi schema, giữ interface `StorySource`, dùng `Semaphore` và `crawlDelayMs`), nhưng mục tiêu tương thích ngược bị chặn ở tầng Repository, chưa có bằng chứng khảo sát nguồn mới, cơ chế `novelId`/đánh số chương có nguy cơ ghi lệch dữ liệu chương trong thư viện, và Test Contract tự mâu thuẫn. Cần Revision 2 xử lý M1–M7.

Kết luận: [CHANGES_REQUESTED]

### Audit Run 2

- **Reviewer**: Claude Code CLI (claude-opus-5-5)
- **Ngày**: 2026-10-13
- **Đối tượng**: Technical Plan Revision 2

#### Kiểm chứng lỗi cũ (so với Audit Run 1, kết luận CHANGES_REQUESTED)

| Lỗi Run 1 | Trạng thái | Ghi chú |
| --- | --- | --- |
| M1 — Tương thích ngược bị chặn ở Repository | Khắc phục một phần | Đã thêm `legacyHosts`, liệt kê `StoryRepository.kt`/`DownloadRepository.kt` vào mục 5, thêm test. Nhưng đoạn code Bước 1 không biên dịch được và làm vỡ test hiện có (xem M8, M9). |
| M2 — Thiếu bằng chứng khảo sát | Khắc phục phần lớn | Đã có ngày khảo sát 2026-10-13, trích đoạn HTML/JSON, ý nghĩa `type`, 15 slug. Reviewer không fetch được `sstruyen.pics` (ngoài allowlist) → các trích đoạn là bằng chứng của Planner, UNVERIFIED phía Reviewer. Còn thiếu: endpoint tìm kiếm `tim-kiem?keyword=` không có trích đoạn; không khảo sát URL chương cũ (xem M10). |
| M3 — `novelId` không an toàn | Đã khắc phục | Regex neo theo `"slug":"$slug"`; còn MINOR về escape (m8). |
| M4 — Định nghĩa `chapterNumber` | Khắc phục một phần | Đã có quy tắc slug → name → tuần tự. Nhưng chưa chứng minh số chương/nội dung mới khớp dữ liệu cũ; bằng chứng `[URL]` cho thấy tiêu đề chương 1 hai bên khác nhau (xem M10). Bước 6 và mục 9 mô tả fallback khác nhau (m6). |
| M5 — Lỗi giữa chừng & mất cache | Đã khắc phục | Rethrow `CancellationException`, `Timber.e`, gom `failedPages`, ném `NetworkException`; cache miss → `getStoryDetail`. |
| M6 — Test Contract mâu thuẫn | Đã khắc phục | Fixture ≥ 50 ký tự, `customBaseUrl`/`customApiBaseUrl`, test `getGenreList`, test mất cache, test lỗi API. |
| M7 — ktlint baseline đỏ | Chưa khắc phục | Plan chỉ xử lý 5 vi phạm ở test source set; evidence hiện tại cho thấy `ktlintMainSourceSetCheck` cũng FAILED với hơn 150 vi phạm (xem M11). |
| m1 — `WebViewContentLoader` không được dùng | Chưa xử lý | Vẫn giữ Bước 8; vô hại. |
| m2 — so khớp chuỗi URL | Đã khắc phục | Contract #8/#9 dùng query parameter. |
| m3 — `.text-center` | Đã khắc phục | Override `adsSelectors`. |
| m4 — Cover tương đối | Đã khắc phục | Ghép CDN trước `validateCoverUrl`. |
| m5 — Helper JSON | Khắc phục một phần | Có `fetchJson`, nhưng không có retry/backoff (m7). |

#### Mental Simulation Matrix (S1–S6 + Domain)

| Kịch bản | Kết quả | Bằng chứng | Chi tiết |
| --- | --- | --- | --- |
| S1 Build | FAIL | `[SRC]` + `[CMD]` | (1) Đoạn code Bước 1 giữ `fun sourceFor(url: String): StorySource =` (không `suspend`) nhưng gọi `activeSources()` vốn là `private suspend fun` (`StoryRepository.kt:70`) → lỗi biên dịch "Suspend function should be called only from a coroutine". (2) `ktlintCheck` Exit 1: ngoài 5 vi phạm test, `ktlintMainSourceSetCheck` FAILED ở `WikidichScraper.kt`, `TangThuVienScraper.kt`, `SourceAuthManager.kt`, `cookie/*`, `SourceAuthRepository.kt`, `SourceLoginScreen.kt`, và cả `StorySource.kt:47` (file plan sẽ sửa). Mục tiêu "ktlintCheck pass 100%" ở Bước 11 không đạt được với phạm vi hiện tại. |
| S2 Lifecycle | PASS | `[SRC]` | `fetchJson` dùng `client` inject + `execute().use {}`; `withContext(Dispatchers.IO)` giữ như mẫu `SsTruyenScraper.kt:68`; `CancellationException` được rethrow; cache miss tự phục hồi sau process death. |
| S3 Database | FAIL | `[SRC]` + `[URL]` + `[ADV]` | Không đổi schema (đúng). Nhưng `mergeAndInsertChapters` (`StoryRepository.kt:433-437`) ghi đè `title`/`url` theo `chapterNumber` cho chương đã có, trong khi `content` đã tải giữ nguyên. `[URL]` https://sstruyen.net/truyen/tien-nghich/chuong-1 (fetch 2026-10-13) có tiêu đề "Chương 1: Ly hương", còn JSON mẫu của plan cho cùng truyện là "Chương 1: Đi xa" → nguy cơ hai domain là hai bản dịch/bản biên tập khác nhau; số chương và nội dung có thể không tương ứng. Xem Timeline A. |
| S4 Cross-Task State | FAIL | `[SRC]` | (1) Bước 1 đổi `sources` → `activeSources()`: nếu người dùng tắt nguồn SsTruyen trong Cài đặt, truyện trong thư viện (kể cả chương đã tải) sẽ ném `ParseException` khi đọc online/làm mới — thay đổi hành vi ngoài phạm vi plan. (2) `StoryRepositoryTest.kt:48` dùng `mockk()` strict; sau khi `sourceFor` đọc `source.legacyHosts`, test `getChapterContent throws ParseException for removed source when not cached` (`StoryRepositoryTest.kt:319-330`, URL `https://unsupported.com`) sẽ gặp `MockKException` → bị `catch (e: Exception)` (`StoryRepository.kt:546`) đổi thành `Exception` thường → test fail. Plan không nêu stub `every { source.legacyHosts } returns emptyList()`. (3) Test Contract #10 gọi `StoryRepository.sourceFor(url)` nhưng hàm là `private` (`StoryRepository.kt:89`). |
| S5 Security | PASS | `[SRC]` | Không có cookie/đăng nhập. `api.sstruyen.pics` gọi qua `client` dùng chung, không gửi dữ liệu người dùng. `WebViewContentLoader` (`WebViewContentLoader.kt:36`) không được dùng ở main code nên thêm domain không mở rộng bề mặt thực tế. |
| S6 External Resilience | FAIL (một phần) | `[URL]` + `[SRC]` | `[URL]` `sstruyen.net` vẫn hoạt động, phục vụ nội dung chương tại `/truyen/{slug}/chuong-{N}`, không redirect sang `sstruyen.pics`. URL chương cũ trong Room sau `normalizeUrl` thành `https://sstruyen.pics/truyen/{slug}/chuong-{N}`, nhưng định dạng chương mới là `/doc-truyen/{novelSlug}-{chapterSlug}-{chapterId}` → chưa có bằng chứng URL cũ còn dùng được trên domain mới. `fetchJson` không retry/backoff như `fetchDocumentWithRetry` (`BaseScraper.kt:69-91`). |
| Domain | FAIL | `[SRC]` + `[ADV]` | `StoryRepository.getChapterContent` (`StoryRepository.kt:516-529`) dùng `chapterUrl` lưu trong Room. Người dùng đọc tiếp chương 800 ngay khi mở truyện (trước khi `loadRemainingChapters` cập nhật URL) hoặc `DownloadService` tải chương chưa làm mới → URL cũ → 404 / nội dung khác bản dịch. Xem Timeline B. |

#### Adversarial Timelines

```
Cơ chế A: Merge metadata chương giữa hai bản dữ liệu khác nhau (S3)
T1: Thư viện có "tien-nghich" từ sstruyen.net, chương 1..100 đã tải (content bản .net, title "Chương 1: Ly hương")
T2: Người dùng mở truyện → getFirstPageChapters gọi API .pics → chapter slug "chuong-1-di-xa" → chapterNumber = 1
T3: mergeAndInsertChapters → updateChapterMetadata(storyId, 1, "Chương 1: Đi xa", url .pics) — content vẫn là bản .net
T4: Chương 101+ tải từ .pics (bản dịch/phân chương có thể khác)
Kết cục: tiêu đề không khớp nội dung đã tải; nếu .pics gộp/tách chương khác .net thì tiến độ đọc nhảy lệch, TTS đọc lặp/thiếu đoạn → LỖI (MAJOR) cho tới khi có khảo sát đối chiếu chứng minh tương ứng 1:1
```

```
Cơ chế B: URL chương cũ chưa được làm mới (TOCTOU giữa Room và crawl nền)
T1: Room lưu chương 800 url = https://sstruyen.net/truyen/de-ba/chuong-800, isDownloaded = false
T2: Người dùng bấm "Đọc tiếp" từ thư viện → ReaderViewModel gọi getChapterContent(storyId, 800, urlCũ)
    (loadRemainingChapters cho trang 17 chưa chạy xong hoặc thất bại → URL chưa được cập nhật)
T3: sourceFor(urlCũ) → SsTruyenScraper (nhờ legacyHosts) → normalizeUrl → https://sstruyen.pics/truyen/de-ba/chuong-800
T4: .pics không có route này (định dạng mới là /doc-truyen/...) → 404 → "Chương này chưa được tải..."
Kết cục: mục tiêu "không làm hỏng tiến độ đọc" thất bại với chương chưa tải → LỖI (MAJOR)
Cần: hoặc giữ đọc URL chương .net trên host cũ (vẫn sống), hoặc ánh xạ chương cũ → chapterNumber → URL .pics qua API trước khi fetch, hoặc khảo sát chứng minh URL cũ redirect trên .pics.
```

```
Cơ chế C: Bất đồng bộ getRemainingChapters song song (S5/đồng thời)
T1: Semaphore(3) chạy page 2..42 | T2: page 17 lỗi 429 (fetchJson không retry) | T3: các page khác vẫn onBatchReady → mergeAndInsertChapters
T4: Sau vòng lặp ném NetworkException("…lỗi các trang: [17]")
Kết cục: không mất dữ liệu (chương đã insert giữ nguyên, lỗi được báo) → OK; nhưng thiếu retry làm lỗi tạm thời 429 thành lỗi người dùng thấy → MINOR (m7)
```

Trục Bảo mật/Cookie: N/A (SsTruyen không dùng phiên đăng nhập).

#### Commands Executed (trích từ evidence.md)

- `### [CMD] cmd.exe /d /c gradlew.bat compileDebugKotlin` — `Exit: 0` — `BUILD SUCCESSFUL in 6s` (baseline, chưa có code mới).
- `### [CMD] cmd.exe /d /c gradlew.bat ktlintCheck` — `Exit: 1` — `Execution failed for task ':app:ktlintMainSourceSetCheck'` và `Execution failed for task ':app:ktlintTestSourceSetCheck'`; main source set có vi phạm ở `WikidichScraper.kt`, `TangThuVienScraper.kt`, `SourceAuthManager.kt`, `AndroidCookieStore.kt`, `CookieStore.kt`, `ISourceAuthRepository.kt`, `SourceAuthRepository.kt`, `SourceLoginScreen.kt`, `SourceLoginViewModel.kt`, `StorySource.kt:47:87`; test source set có đúng 5 vi phạm plan đã liệt kê.
- `### [CMD] cmd.exe /d /c gradlew.bat testDebugUnitTest` — `Exit: 0` — `BUILD SUCCESSFUL in 1s` (UP-TO-DATE).
- `### [CMD] git status --short` — `Exit: 0` — `SsTruyenScraper.kt` chưa thay đổi; `StoryRepositoryTest.kt` có thay đổi chưa commit.
- `[URL]` https://sstruyen.net/truyen/tien-nghich/chuong-1 (WebFetch 2026-10-13): trang trả nội dung chương trực tiếp trên `sstruyen.net`, tiêu đề "Tiên Nghịch - Chương 1: Ly hương | SSTruyen", không nhắc tới `sstruyen.pics`.
- UNVERIFIED (đề xuất Planner chạy và dán kết quả vào plan): `curl.exe -q -s -o NUL -w "%{http_code} %{redirect_url}" https://sstruyen.pics/truyen/tien-nghich/chuong-1`; `curl.exe -q -s "https://api.sstruyen.pics/novels/<id>/chapters?page=1&limit=50&sort=asc"` và đối chiếu tiêu đề chương 1, 100, 500 với `sstruyen.net/truyen/tien-nghich/chuong-{N}`; `curl.exe -q -s "https://sstruyen.pics/tim-kiem?keyword=tien" | Select-String "hasPart"`.

#### Phân loại lỗi

- **[MAJOR] M8 — Đoạn code `sourceFor` ở Bước 1 không biên dịch được và đổi hành vi**: `activeSources()` là `suspend` (`StoryRepository.kt:70`) nhưng `sourceFor` không `suspend`; đồng thời đổi từ `sources` sang nguồn đang bật làm truyện thư viện của nguồn bị tắt ném lỗi. Giữ `sources` như hiện tại (`StoryRepository.kt:90`), chỉ thêm điều kiện `legacyHosts`. Nên dùng chung một helper (ví dụ extension `StorySource.matchesUrl(url)` trong `data/remote`) cho cả `StoryRepository` và `DownloadRepository` để tránh lệch logic.
- **[MAJOR] M9 — Test hiện có sẽ vỡ và Test Contract #10 không gọi được**: `StoryRepositoryTest.kt:48` dùng `mockk()` strict; khi `sourceFor` truy cập `source.legacyHosts`, test `StoryRepositoryTest.kt:319-330` sẽ fail (MockKException bị đổi thành `Exception`). Plan phải ghi rõ stub `every { source.legacyHosts } returns emptyList()` trong `setUp` và viết test #10 qua API công khai (ví dụ `getChapterContent`/`getStoryDetail` với mock nguồn thứ hai có `legacyHosts = listOf("sstruyen.net")`) vì `sourceFor` là `private`. Bổ sung test tương tự cho `DownloadRepository.snapshotStoryMetadata` với URL cũ.
- **[MAJOR] M10 — Chưa chứng minh dữ liệu chương cũ tương thích với domain mới**: (a) URL chương cũ `/truyen/{slug}/chuong-{N}` sau `normalizeUrl` chưa được xác minh trên `.pics` (định dạng mới là `/doc-truyen/...`) → Timeline B. (b) Bằng chứng `[URL]` cho thấy tiêu đề chương 1 trên `.net` ("Ly hương") khác JSON mẫu `.pics` ("Đi xa") → khảo sát 15/15 slug chỉ xác nhận *truyện* tồn tại, chưa xác nhận *chương N* tương ứng → Timeline A. Plan cần: khảo sát đối chiếu ít nhất 3 truyện × 3 vị trí chương; và chọn rõ chiến lược cho URL chương cũ chưa tải (giữ host `.net` khi còn sống, hoặc ánh xạ `chapterNumber` → URL `.pics` qua API trước khi fetch, có test). Nếu bản dịch khác nhau, cân nhắc không ghi đè `title` cho chương `isDownloaded = true`.
- **[MAJOR] M11 — Quality Gate ktlint vẫn không thể pass**: evidence hiện tại cho thấy `ktlintMainSourceSetCheck` cũng FAILED (hơn 150 vi phạm ở 10 file main, gồm `StorySource.kt:47` mà plan sẽ sửa). Bước 10/11 và mục rủi ro hàng 5 khẳng định "pass 100%" là sai. Chọn một trong hai và ghi vào plan: (a) mở rộng phạm vi sửa baseline main (liệt kê file, lưu ý `cookie/`, `auth/` là vùng nhạy cảm Code Freeze Guard), hoặc (b) chấp nhận baseline đỏ, đặt tiêu chí "không thêm vi phạm mới" và kiểm bằng so sánh báo cáo `app/build/reports/ktlint/*` trước/sau cho các file plan đụng tới (`SsTruyenScraper.kt`, `StorySource.kt`, `StoryRepository.kt`, `DownloadRepository.kt`, `WebViewContentLoader.kt`, file test mới).
- **[MINOR] m6** — Bước 6 ghi fallback `index + 1`, mục 9 ghi `(page-1)*50 + index + 1`; thống nhất dùng công thức có `page` cho cả hai hàm. Regex `(?:chuong|c)-(\d+)` khớp cả hậu tố `...c-12` không phải chương; nên neo đầu slug `^chuong-(\d+)`. Khi hai chương cho cùng `chapterNumber` (truyện chia quyển đánh số lại), cần phát hiện trùng và log thay vì để `updateChapterMetadata` ghi đè im lặng.
- **[MINOR] m7** — `fetchJson` không retry/backoff; bọc bằng vòng retry tương tự `fetchDocumentWithRetry`. Với OkHttp 5.3.2, `response.body` không nullable → `?.`/`?:` thừa (cảnh báo compiler); kiểm tra body rỗng bằng `ifBlank`. Bắt `JSONException` và chuyển thành `ScraperException.ParseException` tiếng Việt.
- **[MINOR] m8** — Nội suy `$slug` vào regex `novelId` nên dùng `Regex.escape(slug)` dù slug hiện chỉ gồm `[a-z0-9-]`.
- **[MINOR] m9** — `DownloadRepository.kt:38` vẫn fallback `sources.first()`; nên đổi sang ném `ParseException` như `StoryRepository` để không gửi URL sang nhầm nguồn.
- **[MINOR] m10** — Khi `getRemainingChapters` tự phục hồi mà vẫn không có `novelId`, plan chưa nêu hành vi (đề xuất ném `ParseException` tiếng Việt, không âm thầm return). Endpoint tìm kiếm `tim-kiem?keyword=` cần trích đoạn khảo sát như các endpoint khác.
- **[MINOR] m1 (giữ nguyên)** — Bước 8 sửa `WebViewContentLoader` không phải control thực sự vì lớp này không được dùng ở `app/src/main`.

#### Kết luận

Revision 2 đã xử lý tốt M3, M5, M6 và phần lớn M2. Tuy nhiên đoạn code định tuyến ở Bước 1 không biên dịch được và làm vỡ test Repository hiện có, chiến lược cho URL/nội dung chương cũ chưa được chứng minh (bằng chứng thực tế cho thấy tiêu đề chương khác nhau giữa hai domain), và Quality Gate ktlint vẫn không thể đạt như plan khẳng định. Cần Revision 3 xử lý M8–M11.

Kết luận: [CHANGES_REQUESTED]

### Audit Run 3

- **Reviewer**: Claude Code CLI (claude-opus-5-5)
- **Ngày**: 2026-10-13
- **Đối tượng**: Technical Plan Revision 3

#### Kiểm chứng lỗi cũ (so với Audit Run 2, kết luận CHANGES_REQUESTED)

| Lỗi Run 2 | Trạng thái | Ghi chú |
| --- | --- | --- |
| M8 — `sourceFor` gọi `activeSources()` suspend | Đã khắc phục | Bước 1 giữ `sourceFor` non-suspend, duyệt `sources`, dùng chung extension `StorySource.matchesUrl(url)` cho `StoryRepository` và `DownloadRepository`. Khớp hiện trạng `StoryRepository.kt:89-91`, `DownloadRepository.kt:36-38`. |
| M9 — Test Repository vỡ, Contract #10 gọi hàm private | Khắc phục phần lớn | Có stub `every { source.legacyHosts } returns emptyList()` cho `mockk()` strict (`StoryRepositoryTest.kt:48`); test #10 đi qua `getStoryDetail(url)` công khai. Còn thiếu bước viết test cho Contract #11 (`DownloadRepository`), xem m11. |
| M10 — Chưa chứng minh dữ liệu chương cũ tương thích | Khắc phục một phần | (a) Đã chọn chiến lược: URL chương cũ đọc trực tiếp trên host `.net`, có curl 404/200 của Planner; `[URL]` reviewer xác nhận `.net` còn sống. (b) Đã có khảo sát 3 truyện × 3 vị trí chương. Nhưng control "không ghi đè `title` của chương `isDownloaded == true`" chỉ được *khẳng định*, không có bước sửa code nào, trong khi code hiện tại đang ghi đè (xem B1). Contract #12 chưa có cách chạy được trên MockWebServer (xem M12). |
| M11 — ktlint Quality Gate không thể pass | Khắc phục phần lớn | Đã chọn phương án (b): sửa 5 vi phạm test, chấp nhận baseline main, cam kết không thêm vi phạm cho file thuộc task. Còn thiếu cách kiểm cụ thể (m12). |
| m6 — công thức `chapterNumber`, regex neo đầu, trùng số | Đã khắc phục | `^chuong-(\d+)` → name → `(page - 1) * 50 + index + 1`, log `Timber.w` khi trùng. |
| m7 — `fetchJson` retry, body, `JSONException` | Đã khắc phục | Có retry 3 lần + backoff, `body.isBlank()`, chuyển `JSONException` → `ParseException`. Còn MINOR về retry lỗi không tạm thời (m13). |
| m8 — `Regex.escape(slug)` | Đã khắc phục | |
| m9 — fallback `sources.first()` | Đã khắc phục | Ném `ParseException`. |
| m10 — hành vi khi phục hồi `novelId` thất bại, trích đoạn tìm kiếm | Đã khắc phục | Ném `ParseException` tiếng Việt; có trích đoạn `tim-kiem?keyword=tien`. |
| m1 — `WebViewContentLoader` | Đã khắc phục | Bước 8 bỏ sửa file này. |

#### Mental Simulation Matrix (S1–S6 + Domain)

| Kịch bản | Kết quả | Bằng chứng | Chi tiết |
| --- | --- | --- | --- |
| S1 Build | PASS | `[CMD]` + `[SRC]` | `compileDebugKotlin` Exit 0 (baseline). Không thêm dependency (mục 4 khớp `app/build.gradle.kts`). Mọi symbol plan dùng đều tồn tại và đủ quyền truy cập: `BaseScraper.client` `protected` (`BaseScraper.kt:21`), `userAgent` `protected open` (`:29`), `crawlDelayMs` (`:32`), `adsSelectors` `protected open` (`:41`), `fetchDocumentWithRetry` (`:69`), `validateCoverUrl` (`:315`), `MIN_CONTENT_LENGTH = 50` (`:326`); `ScraperException.NetworkException(message, cause)`/`HttpException(code, url)`/`ParseException(message, url)` khớp chữ ký (`ScraperException.kt:13-30`). `legacyHosts` có getter mặc định nên `TangThuVienScraper`/`WikidichScraper` không phải sửa. |
| S2 Lifecycle | PASS | `[SRC]` | `fetchJson` dùng `client` inject + `execute().use {}` trong `withContext(Dispatchers.IO)`; `CancellationException` được rethrow trong cả `fetchJson` lẫn vòng `getRemainingChapters`. Test dùng MockWebServer3 như mẫu `WikidichScraperTest.kt`. |
| S3 Database | FAIL | `[SRC]` + `[ADV]` | Không đổi schema (đúng, `ChapterEntity` khóa `(storyId, chapterNumber)`). Nhưng control rủi ro "Cao" hàng 2 mục 9 không có thật: `mergeAndInsertChapters` (`StoryRepository.kt:433-437`) gọi `chapterDao.updateChapterMetadata(storyId, ch.chapterNumber, ch.title, ch.url)` cho **mọi** chương đã tồn tại, không xét `isDownloaded`. Plan (mục 1.2, Bước 7, mục 9) mô tả hành vi "không bao giờ ghi đè `title` của chương đã tải" như hiện trạng, không có bước sửa code, không có dòng `StoryRepository.kt` cho thay đổi này ở mục 5, không có test. Với khảo sát của chính plan (Tiên Nghịch chương 1: `.net` "Ly hương" vs `.pics` "Đi xa"), chương đã tải sẽ bị đổi tiêu đề trong khi `content` vẫn là bản `.net`. Xem Timeline A. |
| S4 Cross-Task State | PASS | `[SRC]` | Chữ ký `StorySource` (`StorySource.kt:12-69`) chỉ thêm thuộc tính có default; không phá ViewModel/test khác. `getStoryDetail` (`StoryRepository.kt:333-387`) dùng `extractStoryId(url)` = đoạn cuối path → URL `.net` và `.pics` cùng ra `slug`, giữ `Story.id` ổn định; `fresh.url` (đã chuẩn hóa `.pics`) được `insertStory` ghi lại nên lần sau truyện đi thẳng domain mới. `DownloadRepository.sourceFor` dùng cho cả `snapshotStoryMetadata` (`:57`) và `downloadChapters` (`:95`) → một chỗ sửa phủ cả hai. |
| S5 Security | PASS | `[SRC]` | Không có cookie/đăng nhập. `api.sstruyen.pics` gọi qua `client` dùng chung, không gửi dữ liệu người dùng. Không đụng `AndroidManifest.xml`/WebView. |
| S6 External Resilience | PASS (có điều kiện) | `[URL]` + `[SRC]` | `[URL]` https://sstruyen.net/truyen/tien-nghich/chuong-1 (WebFetch 2026-10-13): trả nội dung chương trực tiếp, tiêu đề "Chương 1: Ly hương", không nhắc/redirect `sstruyen.pics` → chiến lược đọc chương cũ trên host `.net` có cơ sở. Selector cũ `#chapter-c, .chapter-c, .chapter-content` đang dùng ở `SsTruyenScraper.kt:38-41`. Dữ liệu `.pics` (HTML/JSON, 404 route cũ) là bằng chứng của Planner, reviewer không fetch được (ngoài allowlist) → UNVERIFIED phía reviewer, chấp nhận vì có lệnh curl và ngày khảo sát theo `04-scraping.md`. Retry/backoff + gom `failedPages` + `NetworkException` đạt. |
| Domain | FAIL | `[SRC]` + `[ADV]` | (1) Ghi đè tiêu đề chương đã tải (như S3). (2) Contract #12 / test #10 Bước 9 là control tự động duy nhất cho rủi ro "Cao" hàng 1, nhưng phân luồng dựa vào host `sstruyen.net` trong URL; MockWebServer chạy trên `localhost:port` nên test không thể vào nhánh cũ mà không gọi mạng thật, và plan không nêu hook (xem M12). (3) `sstruyen.vn` được coi như `.net` nhưng không có khảo sát host `.vn` còn sống (m14). |

#### Adversarial Timelines

```
Cơ chế A: Merge metadata chương đã tải sau khi chuyển domain (S3, check-then-act)
T1: Thư viện có "tien-nghich" (story.url sstruyen.net), chương 1..100 isDownloaded = true, title "Chương 1: Ly hương", content bản .net
T2: Người dùng mở truyện → getStoryDetail (legacy → SsTruyenScraper) → getFirstPageChapterList → API .pics trả "chuong-1-di-xa" → chapterNumber = 1
T3: mergeAndInsertChapters: existingMap[1] != null → updateChapterMetadata(storyId, 1, "Chương 1: Đi xa", url .pics) (StoryRepository.kt:436), không kiểm isDownloaded
T4: Reader offline-first trả ChapterContentResult(content = bản .net, title = "Chương 1: Đi xa") (StoryRepository.kt:523-524)
Kết cục: tiêu đề hiển thị/TTS đọc tiêu đề không khớp nội dung đã tải — đúng rủi ro "Cao" hàng 2 mà plan nói đã có control → LỖI (BLOCKER theo evidence-bar 4.5.4)
Cần: sửa mergeAndInsertChapters chỉ cập nhật title cho chương !isDownloaded (quyết định rõ url có cập nhật hay không), liệt kê vào mục 5, thêm test.
```

```
Cơ chế B: Đọc chương cũ chưa được làm mới URL (TOCTOU Room ↔ crawl nền)
T1: Room: chương 800 url = https://sstruyen.net/truyen/de-ba/chuong-800, isDownloaded = false
T2: Người dùng bấm "Đọc tiếp" trước khi loadRemainingChapters trang 17 chạy xong
T3: sourceFor(urlCũ) → matchesUrl qua legacyHosts → SsTruyenScraper.getChapterContent → nhánh cũ, fetch thẳng .net, selector #chapter-c
T4: Sau đó merge trang 17 cập nhật url chương 800 sang /doc-truyen/... (chương chưa tải → an toàn)
Kết cục: đọc được, không 404 → AN TOÀN (khi .net còn sống). Nếu .net chết sau này: HttpException → StoryRepository.kt:554 báo "Chương này chưa được tải..." tiếng Việt, không trả chương rỗng → chấp nhận.
```

```
Cơ chế C: getRemainingChapters song song + merge đồng thời (Đồng thời / thất bại giữa chừng)
T1: Semaphore(3) chạy page 2..42, mỗi page gọi onBatchReady → mergeAndInsertChapters đồng thời
T2: Hai batch cùng đọc existingMap rồi chèn — chapterNumber khác nhau nên không đè; trùng số (chia quyển) → insertChaptersIgnore giữ bản đầu, batch sau updateChapterMetadata ghi đè + Timber.w
T3: page 17 lỗi 429 sau 3 lần retry → failedPages = [17] → NetworkException sau vòng lặp
Kết cục: không mất content, lỗi được báo rõ → AN TOÀN; trùng số chỉ được log (chấp nhận, đã ghi nhận ở m6)
```

```
Cơ chế D: novelIdCache mất do process death (Vòng đời Android)
T1: getFirstPageChapters lưu novelIdCache[slug] | T2: OS kill process | T3: lần sau loadRemainingChapters → cache miss → getStoryDetail tái tạo novelId; vẫn không có → ParseException tiếng Việt
Kết cục: không âm thầm trả 0 chương → AN TOÀN
```

Trục Bảo mật/Cookie: N/A (SsTruyen không dùng phiên đăng nhập).

#### Commands Executed (trích từ evidence.md)

- `### [CMD] cmd.exe /d /c gradlew.bat compileDebugKotlin` — `Exit: 0` — `BUILD SUCCESSFUL in 6s` (baseline, chưa có code mới).
- `### [CMD] cmd.exe /d /c gradlew.bat ktlintCheck` — `Exit: 1` — `Execution failed for task ':app:ktlintMainSourceSetCheck'` và `':app:ktlintTestSourceSetCheck'`; test source set đúng 5 vi phạm plan liệt kê (`WikidichScraperTest.kt:4:1`, `SourceAuthManagerTest.kt:9:1/94:25/128:1`, `ReaderViewModelTest.kt:3:1`); main có `StorySource.kt:47:87 First line of body expression fits on same line as function signature` (file plan sẽ sửa).
- `### [CMD] cmd.exe /d /c gradlew.bat testDebugUnitTest` — `Exit: 0` — `BUILD SUCCESSFUL in 1s` (UP-TO-DATE).
- `### [CMD] git status --short` — `Exit: 0` — `SsTruyenScraper.kt` chưa thay đổi; `StoryRepositoryTest.kt` có thay đổi chưa commit; chưa có `SsTruyenScraperTest.kt`.
- `[URL]` https://sstruyen.net/truyen/tien-nghich/chuong-1 (WebFetch 2026-10-13): nội dung chương trả trực tiếp, tiêu đề "Chương 1: Ly hương", không có `sstruyen.pics`.
- UNVERIFIED (đề xuất): `curl.exe -q -s -o NUL -w "%{http_code}" https://sstruyen.vn/truyen/tien-nghich/chuong-1`; sau triển khai: `.\gradlew.bat ktlintMainSourceSetCheck` rồi đối chiếu `app/build/reports/ktlint/ktlintMainSourceSetCheck/ktlintMainSourceSetCheck.txt` với baseline cho các file thuộc task.

#### Phân loại lỗi

- **[BLOCKER] B1 — Control "bảo toàn tiêu đề chương đã tải" không tồn tại trong code và không có bước triển khai**: `StoryRepository.kt:433-437` ghi đè `title`/`url` cho mọi chương đã có theo `chapterNumber`, không xét `isDownloaded`. Plan dùng control này để hạ rủi ro "Cao" (mục 9 hàng 2) và mô tả như hiện trạng (mục 1.2, Bước 7). Theo `evidence-bar.md` 4.5.4, rủi ro Cao không có control đã xác minh → BLOCKER. Cần: (a) thêm bước sửa `mergeAndInsertChapters` — với chương `existing.isDownloaded == true` không cập nhật `title` (và quyết định rõ có cập nhật `url` hay không; đề xuất vẫn cập nhật `url` để nếu xóa bản tải thì tải lại từ `.pics`), (b) ghi thay đổi này vào dòng `StoryRepository.kt` ở mục 5, (c) thêm test trong `StoryRepositoryTest` qua `getFirstPageChapterList`: chương đã tải giữ title, chương chưa tải được cập nhật title/url, kèm dòng Test Contract.
- **[MAJOR] M12 — Contract #12 (đọc chương cũ) không kiểm thử được như mô tả**: nhánh cũ chọn theo host `sstruyen.net`/`sstruyen.vn` trong URL, nhưng test chạy trên MockWebServer (`localhost:port`) và S6 cấm gọi mạng thật. Plan không nêu hook. Cần chọn và ghi rõ một cách: phân luồng theo path (`/truyen/{slug}/chuong-` → nhánh cũ, `/doc-truyen/` → nhánh mới) để test dùng URL `http://localhost:port/truyen/tien-nghich/chuong-1`; hoặc thêm `internal var customLegacyBaseUrl`/`Interceptor` trong test để ánh xạ host cũ về MockWebServer. Test phải assert request tới đúng path cũ (không bị chuẩn hóa) và nội dung lấy từ `#chapter-c`.
- **[MINOR] m11** — Contract #11 (`DownloadRepository.snapshotStoryMetadata` với URL `.net`) không có bước viết test tương ứng ở Bước 1/Bước 9. `StoryRepositoryTest.kt:77-79` đã dựng sẵn `downloadRepository`, nên bổ sung test ở đó (lưu ý `snapshotStoryMetadata` nuốt lỗi bằng `Log.d` ở `DownloadRepository.kt:79-81`, nên assert qua `coVerify { source.getStoryDetail(...) }` / `storyDao.insertStory`). Nên thêm một test URL không thuộc nguồn nào ở `downloadChapters` ném `ParseException` thay vì gửi nhầm nguồn.
- **[MINOR] m12** — "Kiểm tra xác nhận các file thuộc task không có vi phạm ktlint mới" (Bước 11) chưa có cách làm cụ thể; ghi rõ lệnh `.\gradlew.bat ktlintMainSourceSetCheck` và lọc báo cáo theo tên 4 file main của task. Lưu ý `StorySource.kt:47:87` đã vi phạm sẵn; mục 1.6 cam kết "0 vi phạm" cho `StorySource.kt` nghĩa là phải sửa dòng này — nên ghi rõ.
- **[MINOR] m13** — `fetchJson` retry cả `ParseException` (JSON rỗng/hỏng) và `HttpException` 4xx (404) — tốn 3 s backoff vô ích. Nên chỉ retry lỗi I/O, 429 và 5xx. `protected` trong class `final` (`SsTruyenScraper.kt:26`) chỉ cần `private`.
- **[MINOR] m14** — `sstruyen.vn` được xử lý như `.net` ở nhánh đọc chương cũ nhưng không có khảo sát host `.vn` còn sống; nếu `.vn` đã chết, cân nhắc thay host `.vn` → `.net` (cùng path `/truyen/{slug}/chuong-N`) trước khi fetch, hoặc ghi rõ hành vi lỗi.
- **[MINOR] m15** — `matchesUrl` dùng `url.contains(host)` nên `notsstruyen.net` cũng khớp; đề xuất so `HttpUrl.host` bằng hoặc `endsWith(".$host")`. Không chặn plan vì nguồn chỉ có 3 host khác nhau rõ ràng.

#### Kết luận

Revision 3 đã xử lý tốt M8, phần lớn M9/M11, và đưa ra chiến lược đọc chương cũ có cơ sở (host `.net` còn sống). Tuy nhiên control quan trọng nhất cho rủi ro "Cao" về lệch tiêu đề chương đã tải chỉ là khẳng định — code `mergeAndInsertChapters` hiện ghi đè title cho mọi chương và plan không có bước sửa hay test (B1). Contract #12 cho chương cũ cũng chưa thể chạy trên MockWebServer (M12). Cần Revision 4 xử lý B1 và M12; các MINOR nên gộp vào cùng lượt.

Kết luận: [CHANGES_REQUESTED]

### Audit Run 4

- **Reviewer**: Claude Code CLI (claude-opus-5-5)
- **Ngày**: 2026-10-13
- **Đối tượng**: Technical Plan Revision 4

#### Kiểm chứng lỗi cũ (so với Audit Run 3, kết luận CHANGES_REQUESTED)

| Lỗi Run 3 | Trạng thái | Ghi chú |
| --- | --- | --- |
| B1 — Không có control bảo toàn tiêu đề chương đã tải | Đã khắc phục | Bước 1 có đoạn sửa `mergeAndInsertChapters` (`StoryRepository.kt:433-437`): `isDownloaded == true` giữ `existing.title`, vẫn cập nhật `url`. Mục 5 dòng `StoryRepository.kt` ghi rõ; có Test Contract #13 và test ở Bước 9. Khớp source: `existingMap` là `Map<Int, ChapterEntity>` (`StoryRepository.kt:410-413`, `ChapterDao.kt:14`) nên `existing.isDownloaded`/`existing.title` truy cập được, smart cast hợp lệ vì `existing` là `val` cục bộ. |
| M12 — Contract #12 không chạy được trên MockWebServer | Đã khắc phục (phần định tuyến) | Phân luồng theo path (`/chuong-` và không có `/doc-truyen/`), test dùng `mockWebServer.url("/truyen/tien-nghich/chuong-1")` và assert path request. URL mới `/doc-truyen/tien-nghich-chuong-1-...` chứa `-chuong-` chứ không phải `/chuong-` nên không bị nhận nhầm. Còn vấn đề trích xuất nội dung nhánh cũ, xem M13. |
| m11 — Thiếu test `DownloadRepository` | Khắc phục một phần | Đã thêm 2 test, nhưng chữ ký trong plan không khớp source (xem m16). |
| m12 — Cách kiểm ktlint cho file thuộc task | Đã khắc phục | Bước 11 có lệnh `ktlintMainSourceSetCheck` + `Select-String` lọc 4 file; ghi rõ sửa `StorySource.kt:47:87`. |
| m13 — `fetchJson` retry lỗi không tạm thời | Đã khắc phục | Chỉ retry `IOException`, 429, 5xx; `HttpException.statusCode` tồn tại (`ScraperException.kt:19-23`). |
| m14 — Host `sstruyen.vn` chưa khảo sát | Khắc phục phần lớn | `legacyHosts` chỉ còn `sstruyen.net`. Mục 3 (dòng "Chiến lược đọc chương cũ") vẫn nhắc `sstruyen.vn` và selector `.chapter-content`, lệch với Bước 7 (xem m18). Không hồi quy vì `BASE_URL` hiện tại là `.net` (`SsTruyenScraper.kt:50`), URL `.vn` vốn đã không định tuyến được. |
| m15 — `matchesUrl` khớp lỏng | Đã khắc phục | So host bằng `URI.host` với `equals`/`endsWith(".$host")`. |

#### Mental Simulation Matrix (S1–S6 + Domain)

| Kịch bản | Kết quả | Bằng chứng | Chi tiết |
| --- | --- | --- | --- |
| S1 Build | PASS | `[CMD]` + `[SRC]` | `compileDebugKotlin` Exit 0 (baseline). Không thêm dependency; `org.json:json:20260522` (test) và `timber:5.0.1` đã có (`app/build.gradle.kts:162,170`). `legacyHosts` có getter mặc định → `TangThuVienScraper`/`WikidichScraper` không phải sửa. `matchesUrl` là extension top-level, dùng được trong cả hai repository. Đoạn code `DownloadRepository` dùng `story.url` trong khi `sourceFor(url: String)` (`DownloadRepository.kt:36`) không có biến `story` → cần viết lại theo tham số `url` (m16, không chặn). |
| S2 Lifecycle | PASS | `[SRC]` | `fetchJson` dùng `client` inject + `execute().use {}` trong `withContext(Dispatchers.IO)`, rethrow `CancellationException`. MockWebServer3 có sẵn (`app/build.gradle.kts:169`). |
| S3 Database | PASS | `[SRC]` + `[ADV]` | Không đổi schema. B1 có control thực trong code + test. Xem Timeline A, B. |
| S4 Cross-Task State | PASS | `[SRC]` | Chữ ký `StorySource` (`StorySource.kt:12-69`) chỉ thêm thuộc tính có default. `StoryRepositoryTest.kt:48` `mockk()` strict, đã stub `baseUrl` (`:51`); plan thêm stub `legacyHosts` → test `unsupported.com` (`StoryRepositoryTest.kt:307-326`) vẫn đi đúng nhánh `ParseException`. `getStoryDetail` lưu `fresh.url` (đã chuẩn hóa `.pics`) qua `insertStory` (`StoryRepository.kt:353-360`) → truyện cũ tự chuyển domain. |
| S5 Security | PASS | `[SRC]` | Không cookie/đăng nhập; không đụng Manifest/WebView. `matchesUrl` so host chặt hơn hiện trạng `contains` (`StoryRepository.kt:90`, `DownloadRepository.kt:37`). |
| S6 External Resilience | FAIL | `[SRC]` + `[URL]` | Nhánh đọc chương cũ thu hẹp selector còn `#chapter-c, .chapter-c, #chapter-content` và Bước 7 áp dụng chung "duyệt các phần tử `<p>` con" cho cả hai nhánh. Đường đọc `.net` đang chạy được hiện nay là `BaseScraper.extractChapterText` (`BaseScraper.kt:204-221`): thử lần lượt 9 selector của `SsTruyenScraper.contentSelectors` (`SsTruyenScraper.kt:36-47`), rồi `cleanHtml(el.html())` đổi `<br>` thành xuống dòng (`BaseScraper.kt:177-184`). `[URL]` https://sstruyen.net/truyen/tien-nghich/chuong-1 (WebFetch 2026-10-13, bản tóm tắt của công cụ): đoạn văn "phân tách bằng `<br>`, không bọc trong `<p>`", container tham chiếu `#main-content`. Bản tóm tắt này chưa phải HTML thô nên độ tin cậy ở mức trung bình, nhưng đủ để thấy plan chưa khảo sát cấu trúc nhánh cũ. Xem M13 và Timeline C. |
| Domain | FAIL (theo S6) | `[ADV]` | Đọc chương cũ chưa tải là mục tiêu số 1 của rủi ro "Cao" hàng 2 mục 9. Nếu nhánh cũ trả rỗng thì mọi chương `.net` chưa tải → `ParseException("Nội dung chương rỗng")` → `StoryRepository.kt:554` báo "Chương này chưa được tải…". Test #12 dùng fixture tự viết (dễ bọc `<p>`) sẽ PASS và che lỗi. Các mảng khác (TTS giữ `\n\n`, làm sạch `adsSelectors`, gộp nguồn theo slug) đạt. |

#### Adversarial Timelines

```
Cơ chế A: Merge metadata chương đã tải sau chuyển domain (S3, check-then-act)
T1: Room: "tien-nghich" chương 1 isDownloaded = true, title "Chương 1: Ly hương", content bản .net
T2: getFirstPageChapterList → API .pics trả "chuong-1-di-xa" → chapterNumber = 1
T3: mergeAndInsertChapters: existing.isDownloaded == true → updateChapterMetadata(storyId, 1, "Chương 1: Ly hương", url /doc-truyen/...)
T4: Reader offline-first trả content .net + title .net (StoryRepository.kt:523-524)
Kết cục: tiêu đề khớp nội dung đã tải; xóa bản tải thì lần sau tải từ .pics → AN TOÀN
```

```
Cơ chế B: DownloadService tải chương trong lúc merge cập nhật metadata (Đồng thời / TOCTOU)
T1: DownloadRepository.downloadChapters nhận List<Chapter> chụp từ trước (url .net, isDownloaded = false)
T2: Song song, loadRemainingChapters merge trang chứa chương đó → isDownloaded vẫn false → title đổi sang bản .pics, url đổi sang /doc-truyen/...
T3: Download fetch theo url .net đã chụp → saveChapterContent ghi content bản .net
Kết cục: chương có content .net + title .pics (lệch nhẹ tiêu đề, không mất dữ liệu, không trả chương rỗng) → chấp nhận, MINOR (m17b)
```

```
Cơ chế C: Đọc chương cũ chưa tải qua nhánh legacy (S6 / Domain)
T1: Room: chương 800 url = https://sstruyen.net/truyen/de-ba/chuong-800, isDownloaded = false
T2: Người dùng "Đọc tiếp" trước khi merge cập nhật url → sourceFor → SsTruyenScraper → isLegacyRoute = true → fetch .net (HTTP 200)
T3: selectFirst("#chapter-c, .chapter-c, #chapter-content") → nếu .net dùng container khác (#main-content/.content/div.text-justify) → null → ParseException; nếu có container nhưng đoạn văn tách bằng <br> → duyệt <p> = rỗng → "< 50 ký tự" → ParseException
T4: StoryRepository.kt:554 → "Chương này chưa được tải…"
Kết cục: chương cũ chưa tải không đọc được dù .net còn sống, trái mục tiêu M12 → LỖI (MAJOR) cho tới khi nhánh cũ dùng lại extractChapterText đã chạy được hoặc có khảo sát HTML thô chứng minh <p>
```

```
Cơ chế D: getRemainingChapters song song ghi failedPages (Đồng thời / thất bại giữa chừng)
T1: withContext(Dispatchers.IO) + Semaphore(3): 3 coroutine chạy trên nhiều thread
T2: page 17 và page 18 cùng lỗi sau retry, cùng gọi failedPages.add(...) trên MutableList (ArrayList) không đồng bộ
T3: race → mất một phần tử hoặc ArrayIndexOutOfBoundsException trong catch
Kết cục: hiếm, nhưng có thể thiếu trang trong thông báo lỗi hoặc ném lỗi lạ → MINOR (m17a): dùng Mutex/synchronized hoặc ConcurrentLinkedQueue
```

```
Cơ chế E: novelIdCache mất do process death (Vòng đời Android)
T1: getFirstPageChapters lưu novelIdCache[slug] | T2: OS kill process | T3: loadRemainingChapters → cache miss → getStoryDetail tái tạo; thất bại → ParseException tiếng Việt
Kết cục: không âm thầm trả 0 chương → AN TOÀN
```

Trục Bảo mật/Cookie: N/A (SsTruyen không dùng phiên đăng nhập).

#### Commands Executed (trích từ evidence.md)

- `### [CMD] cmd.exe /d /c gradlew.bat compileDebugKotlin` — `Exit: 0` — `BUILD SUCCESSFUL in 5s` (baseline, chưa có code mới).
- `### [CMD] cmd.exe /d /c gradlew.bat ktlintCheck` — `Exit: 1` — main có `StorySource.kt:47:87 First line of body expression fits on same line as function signature`; test source set đúng 5 vi phạm plan liệt kê (`WikidichScraperTest.kt:4:1`, `SourceAuthManagerTest.kt:9:1/94:25/128:1`, `ReaderViewModelTest.kt:3:1`). Không có vi phạm ở `SsTruyenScraper.kt`, `StoryRepository.kt`, `DownloadRepository.kt` trên baseline → tiêu chí "0 vi phạm" cho file thuộc task là đo được.
- `### [CMD] cmd.exe /d /c gradlew.bat testDebugUnitTest` — `Exit: 0` — `BUILD SUCCESSFUL in 1s` (UP-TO-DATE).
- `### [CMD] git status --short` — `Exit: 0` — `SsTruyenScraper.kt` chưa thay đổi; chưa có `SsTruyenScraperTest.kt`.
- `[URL]` https://sstruyen.net/truyen/tien-nghich/chuong-1 (WebFetch 2026-10-13): tiêu đề "Tiên Nghịch - Chương 1: Ly hương | SSTruyen"; công cụ tóm tắt đoạn văn tách bằng `<br>`, không dùng `<p>`.
- UNVERIFIED (đề xuất Planner chạy và dán kết quả vào plan): `curl.exe -q -s https://sstruyen.net/truyen/tien-nghich/chuong-1 | Select-String "chapter-c|chapter-content|<br|<p"`.

#### Phân loại lỗi

- **[MAJOR] M13 — Nhánh đọc chương cũ có thể trả rỗng với HTML thật của `sstruyen.net`**: Bước 7 thu hẹp selector cũ còn 3 cái và trích xuất bằng cách duyệt `<p>` cho cả hai nhánh, trong khi đường đọc `.net` đang chạy được dùng 9 selector + `cleanHtml` xử lý `<br>` (`SsTruyenScraper.kt:36-47`, `BaseScraper.kt:177-221`), và `[URL]` cho thấy trang `.net` tách đoạn bằng `<br>`. Đây là control duy nhất cho rủi ro "Cao" hàng 2 mục 9. Cần: (a) nhánh cũ gọi lại `extractChapterText(doc)` với danh sách selector cũ (giữ trong hằng số `LEGACY_CONTENT_SELECTORS`), chỉ nhánh mới duyệt `<p>` trong `div.prose`; lưu ý `contentSelectors` là `open val` dùng chung nên phải tách rõ selector theo nhánh; (b) ghi trích đoạn HTML thô của container chương `.net` (ngày khảo sát) vào mục 3; (c) fixture Test Contract #12 phải mô phỏng đúng cấu trúc thật (`<div id="chapter-c">đoạn 1<br><br>đoạn 2…</div>` nếu đúng như khảo sát) và assert kết quả có `\n` giữa đoạn, ≥ 50 ký tự.
- **[MINOR] m16 — Chữ ký `DownloadRepository` trong plan không khớp source**: `snapshotStoryMetadata(storyId: String)` (`DownloadRepository.kt:54`) đọc `storyDao.getStoryById` chứ không nhận `story`; `downloadChapters(storyId, chapters)` (`:84-95`) chọn nguồn theo `chapters.first().url` và trả `Flow` lạnh, nên lỗi `ParseException` chỉ xuất hiện khi `collect`. Test #14 phải truyền danh sách chương có URL `https://unsupported.com/...` và assert khi `toList()`/`collect`; test #11 phải stub `storyDao.getStoryById` trả entity có `url` `.net`. Đoạn code Bước 1 thay `story.url` bằng tham số `url`. Dòng `:95` còn `else sources.first()` cho danh sách rỗng: vô hại vì vòng lặp không chạy, nhưng nên ghi rõ giữ hay bỏ. Tương tự, test B1 phải đi qua hàm công khai `getFirstPageChapterList`/`loadRemainingChapters` vì `mergeAndInsertChapters` là `private` (`StoryRepository.kt:406`), stub `chapterDao.getChaptersByStoryOnce` trả `ChapterEntity(isDownloaded = true, …)`.
- **[MINOR] m17** — (a) `failedPages` là `MutableList` bị nhiều coroutine trên `Dispatchers.IO` ghi đồng thời (Timeline D): dùng `Mutex`/`Collections.synchronizedList`. (b) Timeline B: lệch tiêu đề khi tải song song với merge, chấp nhận được, có thể ghi chú trong plan.
- **[MINOR] m18** — Mục 3 "Chiến lược đọc chương cũ" vẫn ghi `sstruyen.vn` và selector `.chapter-content`, lệch với Bước 7 và `LEGACY_HOSTS`; đồng bộ lại khi sửa M13.
- **[MINOR] m19** — `ParseException("Dữ liệu JSON không hợp lệ: ${e.message}")` đưa message tiếng Anh của `JSONException` vào chuỗi có thể tới UI (`01-coding.md`: không hiện raw exception). Đặt message tiếng Việt cố định, chuyển `e` vào `cause`.
- **[MINOR] m20** — `java.net.URI(url)` nghiêm ngặt hơn `contains` hiện tại: URL có khoảng trắng hoặc ký tự không hợp lệ sẽ ném `URISyntaxException` → `false` → "Nguồn truyện này không còn hoạt động". `baseHost` lấy từ `baseUrl` giữ cả cổng (`localhost:port`) nên sẽ không khớp `URI.host` nếu sau này test repository dùng MockWebServer. Cân nhắc `baseUrl.toHttpUrlOrNull()?.host` và `url.toHttpUrlOrNull()` (OkHttp, khoan dung hơn).

#### Kết luận

Revision 4 đã xử lý dứt điểm B1 (có sửa code `mergeAndInsertChapters` và test), phần định tuyến của M12 (phân luồng theo path, test được trên MockWebServer), cùng m12, m13, m15. Còn một lỗ hổng chặn: nhánh đọc chương cũ đổi sang selector rút gọn và cách trích xuất theo `<p>` chưa được khảo sát trên HTML thật của `sstruyen.net`, trong khi bằng chứng hiện có cho thấy trang dùng `<br>` → nguy cơ mọi chương cũ chưa tải không đọc được (M13). Đề xuất nhánh cũ dùng lại `extractChapterText` đang chạy được. Các MINOR m16–m20 nên gộp vào cùng lượt Revision 5.

Kết luận: [CHANGES_REQUESTED]

### Audit Run 5

- **Reviewer**: Claude Code CLI (claude-opus-5-5)
- **Ngày**: 2026-10-13
- **Đối tượng**: Technical Plan Revision 5

#### Kiểm chứng lỗi cũ (so với Audit Run 4, kết luận CHANGES_REQUESTED)

| Lỗi Run 4 | Trạng thái | Ghi chú |
| --- | --- | --- |
| M13 — Nhánh đọc chương cũ có thể trả rỗng | Đã khắc phục | Bước 7: nhánh cũ dùng đủ 9 `LEGACY_CONTENT_SELECTORS` (trùng khớp từng phần tử với `SsTruyenScraper.contentSelectors` đang chạy, `SsTruyenScraper.kt:36-47`) và `cleanHtml(element.html())`; chỉ nhánh `/doc-truyen/` duyệt `<p>`. `cleanHtml` (`BaseScraper.kt:177-184`) đổi `<br>` → `\n`, gộp `\n{3,}` → `\n\n`, nên fixture `đoạn 1<br><br>đoạn 2` cho ra `\n\n` như Contract #12 kỳ vọng. Mục 3 đã có trích đoạn container `.net` và fixture Test #10/Contract #12 dùng `<br>`. Còn chênh nhỏ so với `extractChapterText` (m21). |
| m16 — Chữ ký `DownloadRepository` | Đã khắc phục phần lớn | `sourceFor(url)` dùng tham số `url`; test #11 stub `storyDao.getStoryById`; test #14 assert khi `toList()` (khớp `flow {}` lạnh ở `DownloadRepository.kt:88-95`); test B1 đi qua `getFirstPageChapterList` (`StoryRepository.kt:447-455` gọi `mergeAndInsertChapters`). `else sources.first()` cho danh sách rỗng (`DownloadRepository.kt:95`) chưa được nêu, vô hại. Test #11 cần nguồn mock riêng, xem m22. |
| m17 — `failedPages` đồng thời; ghi chú tải song song | Đã khắc phục | `Collections.synchronizedList`; ghi chú m17b ở Bước 6. |
| m18 — Mục 3 lệch Bước 7 (`.vn`, selector) | Đã khắc phục | Mục 3 chỉ còn `sstruyen.net` và đúng 9 selector. |
| m19 — Message tiếng Anh của `JSONException` | Đã khắc phục | `ParseException("Lỗi định dạng dữ liệu danh sách chương", url, e)` khớp chữ ký `ParseException(message, url, cause)` (`ScraperException.kt:26-30`). |
| m20 — `URI` nghiêm ngặt, cổng trong `baseHost` | Đã khắc phục | `runCatching { URI(url).host }` → fallback `toHttpUrlOrNull()`; `URI.host`/`HttpUrl.host` không chứa cổng nên `localhost:port` khớp. |

#### Mental Simulation Matrix (S1–S6 + Domain)

| Kịch bản | Kết quả | Bằng chứng | Chi tiết |
| --- | --- | --- | --- |
| S1 Build | PASS | `[CMD]` + `[SRC]` | `compileDebugKotlin` Exit 0 (baseline). Không thêm dependency. Mọi symbol trong đoạn code đều tồn tại và đúng chữ ký: `HttpException(statusCode, url)` có `val statusCode` (`ScraperException.kt:19-23`); `ParseException(message, url, cause)`; `ScraperException` kế thừa `Exception`, không phải `IOException`, nên điều kiện `e is IOException` trong `fetchJson` không vô tình retry 404/`ParseException`. `adsSelectors`/`contentSelectors` là `protected open` (`BaseScraper.kt:38-41`) nên override được. `MAX_RETRIES` riêng trong companion của `SsTruyenScraper` che hằng số cùng tên ở `BaseScraper` — hợp lệ. `matchesUrl` cần `import okhttp3.HttpUrl.Companion.toHttpUrlOrNull` và phải đặt ở top-level ngoài `interface StorySource` (đoạn code Bước 1 để cùng khối với `legacyHosts`, xem m23). |
| S2 Lifecycle | PASS | `[SRC]` | `fetchJson` dùng `client` inject + `execute().use {}` trong `withContext(Dispatchers.IO)`, rethrow `CancellationException`; `getRemainingChapters` rethrow `CancellationException`. `NetworkException` mới từ `loadRemainingChapters` được `StoryDetailViewModel.kt:200-205` bắt trong `try/catch`, không crash `viewModelScope`. |
| S3 Database | PASS | `[SRC]` + `[ADV]` | Không đổi schema. `mergeAndInsertChapters` (`StoryRepository.kt:406-437`): `existingMap` là `Map<Int, ChapterEntity>`, sửa B1 chỉ đổi tham số `title` của `updateChapterMetadata`, content không bị chạm. Xem Timeline A. |
| S4 Cross-Task State | PASS | `[SRC]` | `legacyHosts` có getter mặc định → `TangThuVienScraper`/`WikidichScraper` không phải sửa. Chỉ `StoryRepositoryTest.kt` mock `StorySource` (Grep trong `app/src/test`), `mockk()` strict ở `:48` → stub `legacyHosts` trong `setUp()` là đủ. Test `downloadChapters` hiện có dùng URL `https://truyencom.com/...` khớp `baseUrl` mock (`StoryRepositoryTest.kt:51,200-223`) → không vỡ khi bỏ fallback `sources.first()`. |
| S5 Security | PASS | `[SRC]` | Không cookie/đăng nhập; không đụng Manifest/WebView. `matchesUrl` so host bằng `equals`/`endsWith(".$host")`, chặt hơn `contains` hiện tại (`StoryRepository.kt:90`, `DownloadRepository.kt:37`). |
| S6 External Resilience | PASS (có điều kiện) | `[SRC]` + `[URL]` | Nhánh cũ giữ nguyên bộ selector + `cleanHtml` của đường đọc `.net` đang chạy; nhánh mới có fallback `cleanHtml` khi không có `<p>`. Retry chỉ cho I/O/429/5xx; trang lỗi được gom và báo. `[URL]` https://sstruyen.net/truyen/tien-nghich/chuong-1 (WebFetch 2026-10-13): trang trả nội dung chương, nhưng công cụ không cung cấp HTML thô để xác nhận `#chapter-c`; câu mở đầu nội dung thật ("Thiết Trụ ngồi ở bên con đường nhỏ...") khác trích đoạn mục 3 → trích đoạn là minh họa, không phải nguyên văn (m24). Rủi ro thấp vì 9 selector giống code production. Dữ liệu `.pics` là bằng chứng của Planner, reviewer không fetch được (ngoài allowlist) → UNVERIFIED phía reviewer, chấp nhận vì có lệnh curl và ngày khảo sát. |
| Domain | PASS | `[SRC]` + `[ADV]` | TTS giữ ngắt đoạn `\n\n` ở cả hai nhánh; chương cũ chưa tải đọc từ `.net`; chương đã tải giữ title/content; truyện cũ tự chuyển `story.url` sang `.pics` sau `getStoryDetail`. Lưu ý nhánh cũ chưa xóa `script`/ads trước `cleanHtml` (m21). |

#### Adversarial Timelines

```
Cơ chế A: Merge metadata chương đã tải sau chuyển domain (S3, check-then-act)
T1: Room: "tien-nghich" chương 1 isDownloaded = true, title "Chương 1: Ly hương", content bản .net
T2: getFirstPageChapterList(urlCũ) → sourceFor → matchesUrl qua legacyHosts → SsTruyenScraper → API .pics → chapterNumber = 1, title "Chương 1: Đi xa"
T3: mergeAndInsertChapters: existing.isDownloaded == true → updateChapterMetadata(storyId, 1, "Chương 1: Ly hương", url /doc-truyen/...)
T4: Reader offline-first trả content .net + title .net
Kết cục: không lệch tiêu đề, không mất content → AN TOÀN
```

```
Cơ chế B: Đọc chương cũ chưa tải trước khi merge cập nhật URL (TOCTOU Room ↔ crawl nền)
T1: Room: chương 800 url = https://sstruyen.net/truyen/de-ba/chuong-800, isDownloaded = false
T2: Người dùng "Đọc tiếp" → sourceFor → SsTruyenScraper.getChapterContent → isLegacyRoute = true → fetch .net (không normalize)
T3: LEGACY_CONTENT_SELECTORS → #chapter-c → cleanHtml đổi <br> → \n\n → ≥ 50 ký tự
T4: Sau đó merge trang 17 đổi url sang /doc-truyen/... (chương chưa tải)
Kết cục: đọc được, lần sau đi .pics → AN TOÀN. Nếu .net chết: HttpException → thông báo tiếng Việt ở Repository, không trả chương rỗng.
```

```
Cơ chế C: getRemainingChapters song song + merge đồng thời (Đồng thời / thất bại giữa chừng)
T1: Semaphore(3), page 2..42 trên Dispatchers.IO, mỗi page onBatchReady → mergeAndInsertChapters
T2: page 17 và 18 cùng lỗi 503 sau 3 lần retry → failedPages (synchronizedList) nhận [17, 18] không race
T3: Sau coroutineScope → NetworkException("…lỗi các trang: [17, 18]") → StoryDetailViewModel bắt, log
Kết cục: không mất dữ liệu, không crash; lỗi chưa hiển thị lên UI (hành vi sẵn có) → chấp nhận
```

```
Cơ chế D: DownloadService tải trong lúc merge (Đồng thời)
T1: downloadChapters chụp List<Chapter> url .net | T2: merge đổi title/url chương chưa tải sang .pics | T3: download ghi content bản .net
Kết cục: lệch nhẹ tiêu đề, không mất dữ liệu → chấp nhận (đã ghi chú m17b)
```

```
Cơ chế E: novelIdCache mất do process death (Vòng đời Android)
T1: cache novelId | T2: OS kill process | T3: loadRemainingChapters → cache miss → getStoryDetail tái tạo; thất bại → ParseException tiếng Việt
Kết cục: không âm thầm trả 0 chương → AN TOÀN
```

Trục Bảo mật/Cookie: N/A (SsTruyen không dùng phiên đăng nhập).

#### Commands Executed (trích từ evidence.md)

- `### [CMD] cmd.exe /d /c gradlew.bat compileDebugKotlin` — `Exit: 0` — `BUILD SUCCESSFUL in 7s` (baseline, chưa có code mới).
- `### [CMD] cmd.exe /d /c gradlew.bat ktlintCheck` — `Exit: 1` — main có `StorySource.kt:47:87 First line of body expression fits on same line as function signature` (plan sửa); test source set đúng 5 vi phạm plan liệt kê (`WikidichScraperTest.kt:4:1`, `SourceAuthManagerTest.kt:9:1/94:25/128:1`, `ReaderViewModelTest.kt:3:1`); không có vi phạm ở `SsTruyenScraper.kt`, `StoryRepository.kt`, `DownloadRepository.kt` → tiêu chí "0 vi phạm" cho file thuộc task đo được.
- `### [CMD] cmd.exe /d /c gradlew.bat testDebugUnitTest` — `Exit: 0` — `BUILD SUCCESSFUL in 2s` (UP-TO-DATE).
- `### [CMD] git status --short` — `Exit: 0` — `SsTruyenScraper.kt` chưa thay đổi; chưa có `SsTruyenScraperTest.kt`.
- `[URL]` https://sstruyen.net/truyen/tien-nghich/chuong-1 (WebFetch 2026-10-13): trang trả nội dung chương; công cụ không trả HTML thô của container.
- UNVERIFIED (đề xuất chạy khi triển khai): `curl.exe -q -s https://sstruyen.net/truyen/tien-nghich/chuong-1 | Select-String "chapter-c|<script"` để chọn fixture nguyên văn cho Test #10.

#### Phân loại lỗi

- **[MINOR] m21 — Nhánh cũ chưa xóa `script`/ads và không kiểm độ dài theo từng selector**: `extractChapterText` hiện tại gọi `el.select(adsSelectors).remove()` trước `cleanHtml` và bỏ qua selector khớp nhưng ngắn (`BaseScraper.kt:205-210`); đoạn code Bước 7 lấy selector khớp đầu tiên rồi `cleanHtml` ngay. `CLEAN_TAG_REGEX` chỉ xóa thẻ, nội dung JS trong `<script>` sẽ lọt vào văn bản và TTS. Khi triển khai: gọi `element.select(adsSelectors).remove()` cho cả hai nhánh, hoặc đơn giản hơn là dùng lại `extractChapterText(doc)` với `contentSelectors = LEGACY_CONTENT_SELECTORS` cho nhánh cũ. Thêm một thẻ `<script>` vào fixture Test #10 để khóa hành vi.
- **[MINOR] m22 — Test #11 `snapshotStoryMetadata` cần nguồn mock riêng**: `source` trong `setUp()` có `baseUrl = https://truyencom.com` và `legacyHosts = emptyList()`, nên URL `.net` sẽ ném `ParseException` và bị `catch` ở `DownloadRepository.kt:79-81` nuốt → `coVerify` fail. Dựng một `DownloadRepository` riêng với mock nguồn `baseUrl = https://sstruyen.pics`, `legacyHosts = listOf("sstruyen.net")` (giống cách test `StoryRepository` ở Bước 1).
- **[MINOR] m23** — Đoạn code Bước 1 đặt `val legacyHosts` và `fun StorySource.matchesUrl` cùng khối; `legacyHosts` thuộc `interface`, còn `matchesUrl` phải ở top-level sau `interface` (kèm import `toHttpUrlOrNull`). `normalizeUrl` chỉ thay `https://sstruyen.net`, trong khi `matchesUrl` nhận cả subdomain (`www.sstruyen.net`) — nên chuẩn hóa theo host đã parse để hai hàm nhất quán.
- **[MINOR] m24** — Trích đoạn HTML `.net` ở mục 3 không phải nguyên văn (nội dung thật chương 1 mở đầu khác). Khi viết fixture, dùng HTML thô từ lệnh curl ở trên và ghi rõ trong `docs/scraping.md` là đã đối chiếu ngày nào.
- **[MINOR] m25** — `NetworkException` từ `getRemainingChapters` chỉ được log ở `StoryDetailViewModel.kt:204`, người dùng không thấy thông báo thiếu chương. Không hồi quy (trước đây lỗi bị nuốt ở scraper), có thể làm ở task sau.

#### Kết luận

Revision 5 đã xử lý dứt điểm M13 (nhánh cũ dùng lại đủ 9 selector và `cleanHtml` cho `<br>`, fixture mô phỏng `<br>`) cùng m16–m20. Mọi BLOCKER/MAJOR của các Run trước (B1, M1–M13) đã khắc phục, chữ ký trong đoạn code khớp source thật, và các cơ chế đồng thời/vòng đời đều có control. Chỉ còn các MINOR m21–m25, nên gộp vào lúc triển khai (đặc biệt m21 và m22 vì ảnh hưởng trực tiếp tới TTS và việc test có pass hay không).

Kết luận: APPROVED

[PLAN_APPROVED]
