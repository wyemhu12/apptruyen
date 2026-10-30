# Lọc Riêng "Truyện Nam" & Sắp Xếp "Mới Nhất" Nguồn Wikidich — Kế Hoạch Triển Khai

> **Trạng thái**: Completed
> **Revision**: 5
> **Ngày tạo**: 2026-10-27
> **Planner**: @planner ([PLANNER_MODEL: gemini-4-argon])

---

## Technical Plan

### 1. Mục Tiêu (Goal) & Out of Scope (What We're NOT Doing)

#### 1.1. Mục Tiêu (Goal)
Triển khai tính năng lọc riêng **"Truyện nam"** hoạt động độc lập và song song với bộ lọc thể loại trên nguồn Wikidich (`wikicv.org`), đồng thời chuẩn hóa URL lọc và sắp xếp theo **"Mới nhất"** (`so=4&vo=1`):

1. **Giữ nguyên lựa chọn 1 thể loại (single-select)** trong danh sách `Thể loại` chung của ứng dụng, không làm phức tạp hóa UI chọn thể loại.
2. **Tách riêng tag "Truyện nam" (`gender=5794f03dd7ced228f4419196` trên `wikicv.org`) thành 1 mục riêng biệt có thể Bật/Tắt độc lập**:
   - **Mặc định BẬT** (`maleOnly = true` là trạng thái mặc định, luôn lọc truyện nam trên Wikidich trừ khi người dùng chủ động tắt đi).
   - **Áp dụng đồng bộ cho cả màn Khám Phá (`ExploreScreen` / `ExploreViewModel`) và Tìm Kiếm (`SearchScreen` / `SearchViewModel`)**:
     - Trong `FilterBottomSheet` của `SearchScreen`: đặt riêng mục `Truyện nam` (độc lập với danh sách `Thể loại`), cho phép bật/tắt kết hợp song song với bất kỳ thể loại nào (hoặc khi không chọn thể loại nào). Cập nhật tham số `maleOnly`, local state `localMaleOnly`, điều kiện `hasAnyFilter` bổ sung `!localMaleOnly`, nút "Xóa tất cả bộ lọc" reset cả `localMaleOnly = true`, chữ ký `onApply: (Genre?, ChapterCountFilter, Boolean, Boolean) -> Unit`, và nút "Áp dụng bộ lọc" truyền đủ 4 tham số. Cập nhật đồng bộ 5 call sites của `applyAllFilters` trong `SearchScreen.kt`. **Bỏ giá trị mặc định của `maleOnly` trong `applyAllFilters`** để compiler bắt buộc toàn bộ call site phải truyền giá trị tường minh, không để lọt lỗi âm thầm.
     - Cập nhật 3 hàm gọi repository trong `SearchViewModel.kt` (`browseByGenre`, `browseByFilterOnly`, `loadMoreFilterResults`) luôn chuyển tiếp tham số `maleOnly` tương ứng (`_state.value.maleOnly` hoặc `current.maleOnly`), đảm bảo khi user bật/tắt "Truyện nam" trên Tìm Kiếm thì kết quả tải về từ Wikidich thay đổi chính xác.
     - Trên `ExploreScreen`: bố trí chip bật/tắt `Truyện nam` tách riêng khỏi hàng danh sách thể loại (mặc định BẬT), bọc trong cấu trúc `Column` bên trong `PullToRefreshBox` để không chồng lấn lên lưới truyện, hiển thị độc lập với `state.genres` (vẫn hiển thị khi đang tải, khi danh sách rỗng, hoặc khi tải lỗi), hoạt động song song cả khi duyệt tab `"Tất cả"` (`getCompletedStories`) lẫn khi chọn một thể loại cụ thể (`getStoriesByGenre`).
   - **Dọn dẹp danh sách thể loại Wikidich**: Gỡ bỏ hoàn toàn 4 tag `Truyện Nam`, `Ngôn Tình`, `Đam Mỹ`, `Bách Hợp` khỏi danh sách `getGenreList()` và `GENRE_QUERY_MAP` của `WikidichScraper` vì trên `wikicv.org` các tag này cùng chia sẻ tham số `gender=`, tránh xung đột tham số khi lọc song song.
3. **Chuẩn hóa URL lọc & Sắp xếp theo "Mới nhất" trên `WikidichScraper` (`wikicv.org`)**:
   - Toàn bộ URL đi qua endpoint `/tim-kiem?qs=2` lọc theo thể loại và duyệt truyện không theo thể loại trên `WikidichScraper` luôn gắn kèm `&so=4&vo=1` (Sắp xếp theo Mới cập nhật / Mới nhất), loại bỏ các URL `/tim-kiem` thiếu `so=4&vo=1` (vốn khiến `wikicv.org` mặc định sắp xếp theo Đọc nhiều nhất).
   - Loại bỏ các tham số URL di sản không chuẩn trong `WikidichScraper.getStoriesByGenre` (`qs=1`, `&tc=&tf=0&m=$month&y=$year&q=`).
   - Cơ chế định tuyến thông minh cho `getCompletedStories`: Khi `maleOnly = false`, sử dụng nhánh nhanh `/bang-xep-hang?so=5` (~1.3s, không gắn `so=4&vo=1` vì là endpoint bảng xếp hạng cố định) để không chạm hạn mức rate-limit của `/tim-kiem`. Nếu danh sách trả về rỗng, tự động fallback sang `/tim-kiem?qs=2&$COMPLETED_STATUS_PARAM&$NEWEST_SORT_PARAM$startParam`. Khi `maleOnly = true`, chuyển sang `/tim-kiem?qs=2&gender=5794f03dd7ced228f4419196&status=5794f03dd7ced228f4419191&so=4&vo=1` kèm 4 Technical Controls kiểm soát rủi ro throttle.
   - Mở rộng đồng bộ chữ ký: `getCompletedStories(page: Int = 1, maleOnly: Boolean)` và `getStoriesByGenre(genreSlug: String, page: Int = 1, minChapters: Int = 0, completedOnly: Boolean = true, maleOnly: Boolean)` qua interface `StorySource`, `IStoryRepository`, implementation `StoryRepository`, `SearchViewModel`, `ExploreViewModel`. **Bỏ default argument `maleOnly` ở cả hai interface `StorySource` và `IStoryRepository`** để compiler bắt buộc kiểm soát toàn bộ call site tại `StoryRepository` (lines 147, 218), `ExploreViewModel` và `SearchViewModel`. Default arguments chỉ được giữ cho `page = 1`, `minChapters = 0`, `completedOnly = true`; không đặt default argument ở các hàm override.

#### 1.2. Out of Scope (What We're NOT Doing)
- **KHÔNG thay đổi cấu trúc Database hoặc Room schema**: Không thêm bảng, không đổi entity `StoryEntity`, không nâng version `AppDatabase` (giữ nguyên version 9, không cần Room Migration).
- **KHÔNG thay đổi bộ lọc trên các nguồn truyện khác (`TangThuVien`, `SsTruyen`)**: Nguồn TangThuVien và SsTruyen không có tham số phân loại truyện nam riêng biệt trên web; các scraper này chỉ cập nhật chữ ký override nhận tham số `maleOnly: Boolean` (không can thiệp URL của TTV/SST).
- **Bộ lọc client-side `maleOnly` trong `applyLocalFilters` CHỈ áp dụng cho riêng nguồn Wikidich (`story.sourceId == "wikidich"`)**: Không lọc bỏ truyện của TangThuVien và SsTruyen (kết quả từ TTV/SST giữ nguyên, không bị ảnh hưởng bởi cờ `maleOnly`).
- **KHÔNG thay đổi danh sách thể loại thành multi-select**: Giữ nguyên cơ chế single-select (chỉ chọn 1 thể loại tại một thời điểm) cho danh sách thể loại chung.
- **KHÔNG thêm hoặc nâng cấp bất kỳ thư viện nào trong `app/build.gradle.kts`**.
- **KHÔNG làm xáo trộn các tính năng tìm kiếm từ khóa hiện có**: Keyword search trên Wikidich tiếp tục dùng form Chế độ 1 (`/tim-kiem?q=...&qs=1`), kết hợp lọc an toàn `applyLocalFilters` ở tầng client-side để loại bỏ các truyện nữ tần/đam mỹ/bách hợp của riêng nguồn Wikidich nếu người dùng đang bật `maleOnly = true`.

---

### 2. Pre-Work Reading & Skills Loaded & Applied

#### 2.1. Danh Sách Tài Liệu & Mã Nguồn Đã Đọc Trực Tiếp
- `AGENTS.md` (lines 1–44): Kiến trúc Clean Architecture + MVVM, UI 100% tiếng Việt, Dagger Hilt, Coroutines + Flow.
- `.agents/rules/00-core.md` (lines 1–68): Nguyên tắc Minimal Diffs, cấm mutating git, cấm `!!`, cấm `GlobalScope`.
- `.agents/rules/01-coding.md` (lines 1–119): Chuẩn xử lý lỗi `ScraperException`, Timber log, quy chuẩn unit test.
- `.agents/rules/02-quality.md` (lines 1–60): Pre-work protocol, Test Failure Triage, Quality Gates.
- `.agents/rules/03-ui.md` (lines 1–48): Material 3, UI 100% tiếng Việt, vùng chạm tối thiểu 48dp, FilterChip styling.
- `.agents/rules/04-scraping.md` (lines 1–50): Kiến trúc scraper kế thừa `BaseScraper`, khảo sát HTML thực tế trước khi sửa, chuẩn hóa URL, kiểm tra đăng nhập/session.
- `.agents/rules/05-plan-review.md` (lines 1–68): Giao thức duyệt kế hoạch, Code Freeze Guard.
- `docs/scraping.md` (lines 1–65): URL patterns của Wikidich, chữ ký AJAX, cookie session `express.sid`.
- `docs/lessons-learned.md` (lines 103–125): Bài học kinh nghiệm 2026-10-14 về rủi ro `/tim-kiem` bị throttle giữ kết nối ~12.03s khi có request liên tiếp; lý do `getCompletedStories()` từng được chuyển sang `/bang-xep-hang?so=5` để không chạm hạn mức.
- `app/src/test/resources/fixtures/wikidich/search_logged_in.html` (lines 267–271, 276–412, 473–502, 737–738): Khảo sát chi tiết fixture HTML thực tế: form tìm kiếm nhanh `#formQuickSearch` (`qs=1`), các thẻ tag `.tag-tab a` dẫn tới `/tim-kiem?qs=2&...` (ví dụ `tag=5794f03dd7ced228f44191ad&genre=5794f03dd7ced228f44191a4` cho Tiên hiệp; `tag=5794f03dd7ced228f44191b6` cho Hệ thống; `/tim-kiem?status=5794f03dd7ced228f4419191&qs=2` cho Hoàn thành); `<p class="book-gender">` chứa nhãn "Ngôn tình", "Đam mỹ"; và danh mục "Mới nhất" (`lines 737–738`) với link `/bang-xep-hang?so=4`.
- `app/src/main/java/com/personal/apptruyen/data/remote/WikidichScraper.kt` (lines 54, 76–124, 317–399): `SEARCH_PAGE_SIZE = 20`, `GENRE_QUERY_MAP`, `getCompletedStories`, `getStoriesByGenre`, `getGenreList`.
- `app/src/main/java/com/personal/apptruyen/data/remote/StorySource.kt` (lines 18–69): Interface chung `getCompletedStories(page, maleOnly)`, `getStoriesByGenre(genreSlug, page, minChapters, completedOnly, maleOnly)`.
- `app/src/main/java/com/personal/apptruyen/data/remote/TangThuVienScraper.kt` (lines 376–395) & `SsTruyenScraper.kt` (lines 713–730): Các hàm override.
- `app/src/main/java/com/personal/apptruyen/data/repository/IStoryRepository.kt` (lines 15–33): Interface repository.
- `app/src/main/java/com/personal/apptruyen/data/repository/StoryRepository.kt` (lines 115, 128–265): Timeout 25s cho Wikidich, gộp kết quả đa nguồn, cơ chế nuốt lỗi từng nguồn trả `emptyList()`.
- `app/src/main/java/com/personal/apptruyen/ui/search/SearchViewModel.kt` (lines 48–62, 157–345, 398–449, 450–475): `SearchState`, `applyFilter`, `applyAllFilters`, `toggleCompletedFilter`, `clearAllFilters`, `applyLocalFilters`, `browseByGenre` (:257-302), `browseByFilterOnly` (:308-344), `loadMoreFilterResults` (:398-449).
- `app/src/main/java/com/personal/apptruyen/ui/search/SearchScreen.kt` (lines 180–305, 585–640, 740–760): `activeFilterCount`, `hasActiveFilters`, 3 call sites của `applyAllFilters` trên `InputChip` (lines 253, 275, 298), `FilterBottomSheet` (call site line 595, định nghĩa line 610, nút áp dụng line 752).
- `app/src/main/java/com/personal/apptruyen/ui/explore/ExploreViewModel.kt` (lines 33–43, 75–190): `ExploreState`, `selectGenre`, `loadFirstPage`, `loadMore`, `pullToRefresh` (lines 158–190 gọi trực tiếp repo).
- `app/src/main/java/com/personal/apptruyen/ui/explore/ExploreScreen.kt` (lines 100–145): `PullToRefreshBox` `BoxScope`, cấu trúc `Column`, hàng chip thể loại nằm trong nhánh `else` và `if (state.genres.isNotEmpty())`.
- `app/src/test/java/com/personal/apptruyen/data/remote/WikidichScraperTest.kt` (lines 211–260): Unit test hiện có `getStoriesByGenre uses filter form qs=1 params...` cần viết lại theo chuẩn mới.

#### 2.2. Bảng Ánh Xạ Skills Đã Nạp & Quyết Định Kỹ Thuật

| Skill / Rule | File SKILL/Rule | Quyết định kỹ thuật chịu ảnh hưởng trực tiếp |
| :--- | :--- | :--- |
| `apptruyen-investigate` | `.agents/skills/apptruyen-investigate/SKILL.md` | Thực hiện `/blast-radius` chi tiết cho `StorySource`, `IStoryRepository`, `StoryRepository`, `WikidichScraper`, `TangThuVienScraper`, `SsTruyenScraper`, `SearchViewModel`, `ExploreViewModel`, `SearchScreen`, `ExploreScreen`, `WikidichScraperTest`, `StoryRepositoryTest`, `SearchViewModelTest`, `ExploreViewModelTest`, `LacaTruyenApiTest`. Phát hiện `pullToRefresh()` trong `ExploreViewModel`, 3 hàm gọi repo trong `SearchViewModel` và toàn bộ 5 call sites trong `SearchScreen` bị plan cũ bỏ sót. |
| Scraping Rule | `.agents/rules/04-scraping.md` | Đối chiếu fixture `search_logged_in.html:267-412` và bằng chứng phiên đăng nhập của User để chuẩn hóa hằng số `MALE_GENDER_PARAM = "gender=5794f03dd7ced228f4419196"`, `COMPLETED_STATUS_PARAM = "status=5794f03dd7ced228f4419191"`, `NEWEST_SORT_PARAM = "so=4&vo=1"`; loại bỏ query params cũ không chuẩn; thiết lập cơ chế định tuyến `/bang-xep-hang` vs `/tim-kiem`. |
| UI Rules | `.agents/rules/03-ui.md` | 100% UI tiếng Việt ("Truyện nam", "Cả truyện nữ/khác", "Bộ lọc", "Đối tượng độc giả"); FilterChip tuân thủ Material 3; vùng chạm tối thiểu 48dp; chip Truyện nam trên Khám Phá bọc trong `Column` độc lập với `state.genres` tránh đè lên lưới truyện. |
| Coding Standards | `.agents/rules/01-coding.md` | Minimal Diffs; không dùng `!!`, không dùng `GlobalScope`; bọc coroutine trong `withContext(Dispatchers.IO)`; rethrow `CancellationException`; tuân thủ cú pháp Kotlin cấm default argument ở các hàm override. |
| Quality Gates | `.agents/rules/02-quality.md` | Unit test đầy đủ các ca happy path và edge cases (bảo lưu `maleOnly` khi kéo làm mới, chuyển tiếp `maleOnly` ở cả 3 hàm browse/loadMore trong `SearchViewModel`, chuyển trạng thái bộ lọc tìm kiếm, lọc client-side chỉ với Wikidich, ca âm TTV/SST); khai báo rõ ràng phân loại Loại C cho test `WikidichScraperTest.kt:211-259`. |

---

### 3. Assumptions & Cross-Task Dependencies

#### 3.1. Các Giả Định Kỹ Thuật Đã Kiểm Chứng (`[SRC]`, `[FIXTURE]`, `[USER_EVIDENCE]`, `[CMD]`)

1. **Bằng chứng xác thực về `qs=2`, `gender=5794f03dd7ced228f4419196`, `so=4&vo=1` ("Mới nhất")**:
   - `[SRC] app/src/test/resources/fixtures/wikidich/search_logged_in.html:267-271,276-412,473-502,737-738`: Khảo sát mã nguồn HTML fixture thực tế:
     - Lines 267–271: `<form id="formQuickSearch" action="/tim-kiem">` với `<input id="txtSearch" name="q">` và `<input type="hidden" name="qs" value="1">` (form tìm kiếm nhanh từ khóa dùng `qs=1`).
     - Lines 276–412: Các thẻ `.tag-tab a` dẫn tới `/tim-kiem?qs=2&...` (ví dụ line 330: `tag=5794f03dd7ced228f44191ad&genre=5794f03dd7ced228f44191a4` cho Tiên hiệp; line 384: `/tim-kiem?qs=2&tag=5794f03dd7ced228f44191b6` cho Hệ thống; line 408: `/tim-kiem?status=5794f03dd7ced228f4419191&qs=2` cho Hoàn thành) chứng minh chế độ lọc nâng cao theo tag/thể loại/trạng thái trên Wikidich sử dụng `qs=2`.
     - Lines 473–474, 501–502: `<p class="book-gender">Ngôn tình</p>`, `<p class="book-gender">Đam mỹ</p>` và `<p class="book-publisher"><a href="/tim-kiem?status=5794f03dd7ced228f4419191">Hoàn thành</a></p>`.
     - Lines 737–738: `<div class="category">Mới nhất</div>` kèm `<a href="/bang-xep-hang?so=4">Xem tất cả</a>`.
   - Các ID định danh tham số được chứng minh bởi:
     - `[SRC] WikidichScraper.kt:87,105,110`: `"truyen-nam" to "gender=5794f03dd7ced228f4419196"`, `"he-thong" to "tag=5794f03dd7ced228f44191b6"`, `"dong-nhan" to "official=5794f03dd7ced228f4419194"`.
   - Tham số sắp xếp `so=4&vo=1` (Xếp theo: Mới nhất trên `/tim-kiem?qs=2`) được chứng minh bởi:
     - `[USER_EVIDENCE]` (2026-10-27): Bằng chứng từ phiên đăng nhập thực tế của User trên web `wikicv.org` (menu dropdown `Xếp theo` trên giao diện `/tim-kiem?qs=2` gồm 3 mục `Đọc nhiều nhất`, `Rating cao nhất`, `Mới nhất` với `Mới nhất` đang được chọn tương ứng `so=4&vo=1`) cùng 3 URL thực tế:
       1. `https://wikicv.org/tim-kiem?qs=2&gender=5794f03dd7ced228f4419196&tag=5794f03dd7ced228f44191b6&so=4&vo=1` (Nam sinh + Hệ thống + Mới nhất)
       2. `https://wikicv.org/tim-kiem?qs=2&gender=5794f03dd7ced228f4419196&tag=5794f03dd7ced228f44191b6` (Nam sinh + Hệ thống + mặc định Đọc nhiều nhất khi thiếu `so=4&vo=1`)
       3. `https://wikicv.org/tim-kiem?qs=2&gender=5794f03dd7ced228f4419196&official=5794f03dd7ced228f4419194&so=4&vo=1` (Nam sinh + Đồng nhân + Mới nhất)
   - **Giải thích về phản hồi ẩn danh**: Truy cập ẩn danh (chưa đăng nhập) vào các URL trên sẽ nhận trang HTTP 200 kèm thông báo "Đăng nhập để xem nội dung", không chứa HTML danh sách truyện. Ứng dụng AppTruyen xử lý trường hợp này qua `WikidichLoginHelper` và `LoginRequiredException` (`WikidichScraper.kt:556-565`), nên khi người dùng đã đăng nhập hoặc chạy test với fixture, cấu trúc URL trên hoạt động chính xác.

2. **Cơ chế định tuyến và kiểm soát rủi ro throttle ~12s của `/tim-kiem` (`[SRC] docs/lessons-learned.md:103-125`)**:
   - `[SRC] docs/lessons-learned.md:112`: Server `wikicv.org` giữ kết nối ~12.03s trên endpoint `/tim-kiem` khi có request liên tiếp (rate-limiting phòng thủ DoS của Cloudflare/server).
   - Endpoint `/bang-xep-hang?so=5` là danh sách tĩnh "Mới hoàn thành", thời gian phản hồi nhanh (~1.3s) và không bị áp dụng throttle rate-limit của `/tim-kiem`, nhưng endpoint này hoàn toàn không nhận tham số lọc giới tính `gender=` hay tag thể loại.
   - **Chiến lược định tuyến trong `WikidichScraper.getCompletedStories`**:
     - Khi `maleOnly == false`: Sử dụng endpoint nhanh `/bang-xep-hang?so=5$startParam` (kèm fallback sang `/tim-kiem?qs=2&$COMPLETED_STATUS_PARAM&$NEWEST_SORT_PARAM$startParam` nếu rỗng). Đây là nhánh tối ưu tốc độ, không kích hoạt throttle rate-limit khi danh sách có sẵn truyện.
     - Khi `maleOnly == true`: Bắt buộc sử dụng `/tim-kiem?qs=2&gender=5794f03dd7ced228f4419196&status=5794f03dd7ced228f4419191&so=4&vo=1` để đáp ứng đúng yêu cầu nghiệp vụ lọc riêng truyện nam. Rủi ro độ trễ ~12s được kiểm soát bằng 4 Technical Controls cụ thể (xem Mục 9), bao gồm in-memory cache tại `[SRC] BaseScraper.kt:57, 123-124, 374`.

3. **Kiểm chứng 4 tag giới tính cần gỡ bỏ**:
   - `[SRC] WikidichScraper.kt:84-87` & `lines 366-369`:
     - `"ngon-tinh" to "gender=5794f03dd7ced228f4419195"`
     - `"dam-my" to "gender=5794f03dd7ced228f4419197"`
     - `"bach-hop" to "gender=5794f03dd7ced228f4419198"`
     - `"truyen-nam" to "gender=5794f03dd7ced228f4419196"`
     Cả 4 tag này đều dùng chung namespace query `gender=`. Khi tách riêng `Truyện nam` thành tham số độc lập `gender=5794f03dd7ced228f4419196`, việc gỡ 4 tag này khỏi danh sách thể loại loại bỏ hoàn toàn khả năng xung đột 2 giá trị `gender=` khác nhau trong cùng 1 query string.

4. **Kiểm chứng cú pháp Kotlin về Default Arguments và Bỏ Default Argument ở `StorySource` & `IStoryRepository` (`[SRC] StorySource.kt`)**:
   - Kotlin quy định rõ: "An overriding function is not allowed to specify default values for its parameters".
   - Tất cả các lớp override (`WikidichScraper`, `TangThuVienScraper`, `SsTruyenScraper`, `StoryRepository`, `TestStorySource`) chỉ khai báo kiểu tham số `maleOnly: Boolean`, không gán giá trị mặc định `= true`.
   - **Quyết định thiết kế**: Để tận dụng trình biên dịch Kotlin phát hiện toàn bộ các call site gọi scraper và repo chưa chuyển tiếp `maleOnly`, ta **BỎ giá trị mặc định của `maleOnly` trong cả hai interface `StorySource` và `IStoryRepository`**:
     - `suspend fun getCompletedStories(page: Int = 1, maleOnly: Boolean): List<Story>`
     - `suspend fun getStoriesByGenre(genreSlug: String, page: Int = 1, minChapters: Int = 0, completedOnly: Boolean = true, maleOnly: Boolean): List<Story>`
     Nhờ vậy, compiler bắt buộc cả 2 call site trong `StoryRepository` (`lines 147, 218`), cả 6 call site trong `ExploreViewModel` (`lines 91, 93, 127, 129, 168, 170`) và cả 3 call site trong `SearchViewModel` (`browseByGenre:271`, `browseByFilterOnly:316`, `loadMoreFilterResults:414`) phải truyền tường minh `maleOnly`, loại bỏ rủi ro lỗi âm thầm.
   - Trong `StorySource.kt`, default arguments chỉ giữ cho `page: Int = 1`, `minChapters: Int = 0`, `completedOnly: Boolean = true`, còn `maleOnly: Boolean` bỏ default value để compiler kiểm soát toàn bộ caller.

#### 3.2. Quan Hệ Phụ Thuộc Chéo (Cross-Task Dependencies)
- Không có task nào đang chạy dở dang trong repo (`node .agents/scripts/plan-approval.js status` xác nhận toàn bộ plan trước đã `Completed` / `Superseded`).

#### 3.3. Quyết Định Sản Phẩm (Product Decisions)
- Cả 3 quyết định sản phẩm đã được User chốt dứt khoát:
  1. `maleOnly = true` là mặc định.
  2. Áp dụng đồng bộ cho cả `ExploreScreen` và `SearchScreen`.
  3. Gỡ bỏ 4 tag `Truyện Nam`, `Ngôn Tình`, `Đam Mỹ`, `Bách Hợp` khỏi danh sách tag Wikidich.
- Không phát sinh `[NEEDS_PRODUCT_DECISION]` mới.

---

### 4. Verified Versions Table

> Đã đối chiếu với `app/build.gradle.kts` và xác minh trực tuyến (ngày 2026-10-27). Không thay đổi dependency trong kế hoạch này.

| Thư viện / Plugin | Version trong Repo (`app/build.gradle.kts`) | Version đề xuất | Latest đã xác minh (2026-10) | Nguồn URL | Tương thích? |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Android Gradle Plugin (AGP) | `9.4.0` | `9.4.0` | `9.4.0` | `developer.android.com/build/releases/gradle-plugin` | Có |
| Kotlin | `2.4.21` | `2.4.21` | `2.4.21` | `kotlinlang.org/docs/releases.html` | Có |
| Compose BOM | `2026.05.01` | `2026.05.01` | `2026.05.01` | `developer.android.com/jetpack/compose/bom` | Có |
| Material 3 | `1.3.1` (qua BOM) | (giữ nguyên) | `1.3.1` | `developer.android.com/jetpack/androidx/releases/compose-material3` | Có (`FilterChip`, `ModalBottomSheet`) |
| OkHttp | `5.3.2` | `5.3.2` | `5.3.2` | `square.github.io/okhttp/changelog/` | Có |
| Jsoup | `1.22.2` | `1.22.2` | `1.22.2` | `jsoup.org/news/` | Có |
| Coroutines | `1.11.0` | `1.11.0` | `1.11.0` | `github.com/Kotlin/kotlinx.coroutines` | Có |
| kotlinx-collections-immutable | `0.3.8` | `0.3.8` | `0.3.8` | `github.com/Kotlin/kotlinx.collections.immutable` | Có |

---

### 5. Files Cần Chỉnh Sửa / Tạo Mới & Caller-First Design Sketch

#### 5.1. Bảng Danh Sách Files

| Nhãn | Đường dẫn File | Tầng Kiến Trúc | Lý do & Mô tả tóm tắt |
| :--- | :--- | :--- | :--- |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/data/remote/StorySource.kt` | `data/remote` | Mở rộng chữ ký `getCompletedStories(page: Int = 1, maleOnly: Boolean)` và `getStoriesByGenre(genreSlug: String, page: Int = 1, minChapters: Int = 0, completedOnly: Boolean = true, maleOnly: Boolean)`. **Bỏ default argument `maleOnly` ở interface này** để compiler bắt buộc kiểm soát caller `StoryRepository.kt:147, 218`. |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/data/remote/WikidichScraper.kt` | `data/remote` | Chuẩn hóa URL `qs=2&...&so=4&vo=1`, xóa 4 tag khỏi `GENRE_QUERY_MAP` & `getGenreList()`; định tuyến thông minh: nhánh nhanh `/bang-xep-hang?so=5$startParam` (kèm fallback `/tim-kiem?qs=2&$COMPLETED_STATUS_PARAM&$NEWEST_SORT_PARAM$startParam`) khi `!maleOnly` chống throttle ~12s, nhánh `/tim-kiem?qs=2...` khi `maleOnly`. Dùng hằng số `SEARCH_PAGE_SIZE = 20`. Không default ở override. |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/data/remote/TangThuVienScraper.kt` | `data/remote` | Cập nhật chữ ký override nhận `maleOnly: Boolean` và `completedOnly: Boolean` (không default ở override, giữ nguyên logic gọi mạng). |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/data/remote/SsTruyenScraper.kt` | `data/remote` | Cập nhật chữ ký override nhận `maleOnly: Boolean` và `completedOnly: Boolean` (không default ở override, giữ nguyên logic gọi mạng). |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/data/repository/IStoryRepository.kt` | `data/repository` | Cập nhật chữ ký interface `getCompletedStories(page: Int = 1, maleOnly: Boolean)` và `getStoriesByGenre(genreSlug: String, page: Int = 1, minChapters: Int = 0, completedOnly: Boolean = true, maleOnly: Boolean)`. **Bỏ default argument `maleOnly` ở interface này** để compiler bắt buộc kiểm tra toàn bộ các call site. |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/data/repository/StoryRepository.kt` | `data/repository` | Override nhận `maleOnly: Boolean` (không default), chuyển tiếp tham số `maleOnly` tới các `activeSources()` trong `getCompletedStories` và `getStoriesByGenre`. |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/ui/search/SearchViewModel.kt` | `ui/search` | Thêm `maleOnly: Boolean = true` vào `SearchState` (giữ nguyên `isFilterActive: Boolean = false`); xây dựng helper `hasAnyNonDefaultFilter`; cập nhật `applyFilter(genre, chapterFilter)`, `applyAllFilters(genre, chapterFilter, completedOnly, maleOnly)` (**bỏ default value của `maleOnly`**), `toggleCompletedFilter()`, `toggleMaleFilter()`, `clearAllFilters()` (giữ nguyên điều kiện `&& hadSearched`); **chuyển tiếp `maleOnly` ở cả 3 hàm gọi repo**: `browseByGenre` (:261-297), `browseByFilterOnly` (:302-339), `loadMoreFilterResults` (:398-441); giữ nguyên toàn bộ thân hàm gốc, guard và phân trang của 3 hàm này, chỉ truyền thêm `maleOnly`; cập nhật `applyLocalFilters()` chỉ lọc client-side cho riêng nguồn Wikidich bằng `minChapters` và `story.status`. |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/ui/search/SearchScreen.kt` | `ui/search` | Cập nhật `activeFilterCount` (:182-187) và `hasActiveFilters` (:230-233) thêm điều kiện `!state.maleOnly` để hiển thị badge và hàng chip khi tắt lọc nam; bổ sung section độc lập cho "Đối tượng độc giả" trong `FilterBottomSheet` (tham số `maleOnly`, local state `localMaleOnly`, `hasAnyFilter` có `!localMaleOnly`, nút "Xóa tất cả bộ lọc" reset cả `localMaleOnly = true`, chữ ký `onApply: (Genre?, ChapterCountFilter, Boolean, Boolean) -> Unit`, nút áp dụng dòng 752 truyền đủ 4 tham số); **cập nhật đồng bộ toàn bộ 5 call sites của `applyAllFilters`** trong `SearchScreen.kt` (lines 253, 275, 298, chip mới khi `!state.maleOnly`, và line 595 trong `FilterBottomSheet.onApply`). |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/ui/explore/ExploreViewModel.kt` | `ui/explore` | Thêm `maleOnly: Boolean = true` vào `ExploreState`; bổ sung hàm `toggleMaleOnly()`; cập nhật `loadFirstPage()`, `loadMore()`, và đặc biệt là `pullToRefresh()` bảo lưu đúng `maleOnly` (giữ nguyên cancel job, `canLoadMore = stories.isNotEmpty()`, xử lý exception chuẩn). |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/ui/explore/ExploreScreen.kt` | `ui/explore` | Bố cục bên trong `PullToRefreshBox` bằng `Column(modifier = Modifier.fillMaxSize())`, phía trên là `Row` chứa FilterChip "Truyện nam" độc lập với `state.genres`, phía dưới là `Box(modifier = Modifier.weight(1f).fillMaxWidth())` chứa khối `when` hiển thị danh sách thể loại và truyện, ngăn ngừa đè lên lưới truyện (`MINOR-08`). |
| `[MODIFY]` | `app/src/test/java/com/personal/apptruyen/data/remote/WikidichScraperTest.kt` | `test` *(File nhạy cảm)* | Viết lại test hiện có `...qs=1 params...` thành `qs=2` kèm `so=4&vo=1` (Loại C); thêm test MockWebServer kiểm tra URL `getCompletedStories` nhánh nhanh `/bang-xep-hang` vs nhánh `/tim-kiem`. |
| `[MODIFY]` | `app/src/test/java/com/personal/apptruyen/data/repository/StoryRepositoryTest.kt` | `test` *(File nhạy cảm)* | Bổ sung 2 unit test kiểm tra `StoryRepository` chuyển tiếp chính xác `completedOnly` và `maleOnly` tới các scraper nguồn trong `getCompletedStories` và `getStoriesByGenre`. |
| `[MODIFY]` | `app/src/test/java/com/personal/apptruyen/ui/search/SearchViewModelTest.kt` | `test` *(File nhạy cảm)* | Kiểm thử máy trạng thái `SearchViewModel`: kịch bản tắt nam + bật lại full không làm mất cờ `maleOnly`, chip xóa nhanh bảo lưu `maleOnly`, kiểm thử `browseByGenre`, `browseByFilterOnly`, `loadMoreFilterResults` chuyển tiếp chính xác `maleOnly`, `applyLocalFilters` chỉ lọc Wikidich và giữ nguyên truyện TTV/SST. |
| `[MODIFY]` | `app/src/test/java/com/personal/apptruyen/ui/explore/ExploreViewModelTest.kt` | `test` *(File nhạy cảm)* | Kiểm thử `toggleMaleOnly`, tải trang 1 và đặc biệt kiểm thử `pullToRefresh()` bảo lưu đúng `maleOnly`. |
| `[MODIFY]` | `app/src/test/java/com/personal/apptruyen/data/remote/LacaTruyenApiTest.kt` | `test` *(File nhạy cảm)* | Cập nhật chữ ký dummy `TestStorySource` nhận `maleOnly: Boolean` (không default ở override). |
| `[MODIFY]` | `app/src/test/java/com/personal/apptruyen/data/remote/SsTruyenScraperTest.kt` | `test` *(File nhạy cảm)* | Cập nhật 2 call site dòng 324, 351 thêm đối số `maleOnly = true` theo Loại B (Test Harness) sau khi bỏ default argument ở `StorySource`. |

*(Lý do chỉnh sửa file test: Tuân thủ nghiêm ngặt Test Integrity và Code Freeze Guard — cập nhật test harness và bổ sung test cases kiểm chứng Test Contract mới).*

---

#### 5.2. Caller-First Design Sketch

##### 1. Phía Caller UI (`ExploreScreen.kt` & `ExploreViewModel.kt`)
*Bố cục `ExploreScreen.kt` bên trong `PullToRefreshBox` dùng `Column(modifier = Modifier.fillMaxSize())`, phía trên là `Row` chip "Truyện nam", phía dưới là `Box(modifier = Modifier.weight(1f).fillMaxWidth())` chứa khối `when` để tránh xếp chồng lên lưới truyện (`MINOR-08`), hiển thị độc lập với `state.genres`:*

```kotlin
// Trong ExploreScreen.kt:
PullToRefreshBox(
    isRefreshing = state.isRefreshing,
    onRefresh = { viewModel.pullToRefresh() },
    modifier = Modifier.fillMaxSize(),
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Vị trí chip "Truyện nam": Đặt độc lập phía trên danh sách thể loại
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = state.maleOnly,
                onClick = { viewModel.toggleMaleOnly() },
                label = { Text("Truyện nam") },
                leadingIcon = if (state.maleOnly) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                shape = RoundedCornerShape(20.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }

        // Vùng nội dung chính chiếm toàn bộ chiều cao còn lại:
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading -> {
                    // Loading indicator...
                }
                state.error != null && state.stories.isEmpty() -> {
                    // Error view...
                }
                else -> {
                    // Hàng thể loại (LazyRow) và lưới truyện (StoryGrid)...
                }
            }
        }
    }
}
```

```kotlin
// Trong ExploreViewModel.kt:
data class ExploreState(
    val stories: ImmutableList<Story> = persistentListOf(),
    val genres: ImmutableList<Genre> = persistentListOf(),
    val selectedGenre: Genre? = null,
    val maleOnly: Boolean = true, // Mặc định BẬT
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val currentPage: Int = 1,
    val canLoadMore: Boolean = true,
)

fun toggleMaleOnly() {
    val newMaleOnly = !_state.value.maleOnly
    _state.update { it.copy(maleOnly = newMaleOnly) }
    loadFirstPage()
}

// Bắt buộc truyền tường minh maleOnly trong pullToRefresh (lines 158-190) để tránh lệch trạng thái:
fun pullToRefresh() {
    loadJob?.cancel()
    loadMoreJob?.cancel()
    loadJob =
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true, error = null) }
            try {
                val genre = _state.value.selectedGenre
                val currentMaleOnly = _state.value.maleOnly
                val stories =
                    if (genre != null) {
                        repository.getStoriesByGenre(
                            genreSlug = genre.slug,
                            page = 1,
                            completedOnly = true,
                            maleOnly = currentMaleOnly,
                        )
                    } else {
                        repository.getCompletedStories(
                            page = 1,
                            maleOnly = currentMaleOnly,
                        )
                    }
                _state.update {
                    it.copy(
                        stories = stories.toImmutableList(),
                        isRefreshing = false,
                        currentPage = 1,
                        canLoadMore = stories.isNotEmpty(),
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.update {
                    it.copy(
                        isRefreshing = false,
                        error = (e as? ScraperException)?.message ?: "Không thể làm mới dữ liệu. Vui lòng kiểm tra mạng và thử lại.",
                    )
                }
            }
        }
}
```

##### 2. Phía Caller UI (`SearchScreen.kt` & `SearchViewModel.kt`)
*Đặc tả máy trạng thái bộ lọc, helper kiểm tra filter, 3 hàm gọi repo chuyển tiếp `maleOnly`, và cập nhật toàn bộ 5 call sites của `applyAllFilters`:*

```kotlin
// Trong SearchViewModel.kt:
private val NON_MALE_WIKIDICH_GENDERS = setOf("ngôn tình", "đam mỹ", "bách hợp", "nữ tần")

data class SearchState(
    val query: String = "",
    val results: ImmutableList<Story> = persistentListOf(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val hasSearched: Boolean = false,
    // Filter state
    val selectedGenre: Genre? = null,
    val chapterFilter: ChapterCountFilter = ChapterCountFilter.NONE,
    val completedOnly: Boolean = true, // Mặc định chỉ hiện truyện Full
    val maleOnly: Boolean = true,      // Mặc định BẬT lọc truyện nam
    val isFilterActive: Boolean = false,
    val currentPage: Int = 1,
    val canLoadMore: Boolean = false,
)

// Helper kiểm tra filter có khác giá trị mặc định không (dùng chung cho applyAllFilters, toggleCompleted, toggleMale)
private fun hasAnyNonDefaultFilter(
    genre: Genre?,
    chapterFilter: ChapterCountFilter,
    completedOnly: Boolean,
    maleOnly: Boolean,
): Boolean {
    return genre != null ||
        chapterFilter != ChapterCountFilter.NONE ||
        !completedOnly ||
        !maleOnly
}

fun applyFilter(
    genre: Genre?,
    chapterFilter: ChapterCountFilter = ChapterCountFilter.NONE,
) {
    applyAllFilters(genre, chapterFilter, _state.value.completedOnly, _state.value.maleOnly)
}

// BỎ default argument của maleOnly để bắt buộc tất cả call site phải truyền tường minh (MAJOR-06)
fun applyAllFilters(
    genre: Genre?,
    chapterFilter: ChapterCountFilter,
    completedOnly: Boolean,
    maleOnly: Boolean,
) {
    val hasKeyword = _state.value.query.trim().isNotBlank()
    val hasAnyFilter = hasAnyNonDefaultFilter(genre, chapterFilter, completedOnly, maleOnly)

    _state.update {
        it.copy(
            selectedGenre = genre,
            chapterFilter = chapterFilter,
            completedOnly = completedOnly,
            maleOnly = maleOnly,
            isFilterActive = hasAnyFilter,
        )
    }

    if (hasKeyword) {
        search()
    } else if (genre != null) {
        browseByGenre(genre, chapterFilter)
    } else if (hasAnyFilter) {
        browseByFilterOnly(chapterFilter)
    } else if (_state.value.hasSearched) {
        clearFilter()
    }
}

fun toggleCompletedFilter() {
    val newValue = !_state.value.completedOnly
    val isFilterActive = hasAnyNonDefaultFilter(
        genre = _state.value.selectedGenre,
        chapterFilter = _state.value.chapterFilter,
        completedOnly = newValue,
        maleOnly = _state.value.maleOnly,
    )

    _state.update {
        it.copy(
            completedOnly = newValue,
            isFilterActive = isFilterActive,
        )
    }
    // Chỉ re-trigger khi hasSearched là true
    if (_state.value.hasSearched) {
        val hasKeyword = _state.value.query.trim().isNotBlank()
        val genre = _state.value.selectedGenre
        if (hasKeyword) {
            search()
        } else if (genre != null) {
            browseByGenre(genre, _state.value.chapterFilter)
        } else if (_state.value.isFilterActive) {
            browseByFilterOnly(_state.value.chapterFilter)
        } else {
            clearFilter()
        }
    }
}

fun toggleMaleFilter() {
    val newValue = !_state.value.maleOnly
    val isFilterActive = hasAnyNonDefaultFilter(
        genre = _state.value.selectedGenre,
        chapterFilter = _state.value.chapterFilter,
        completedOnly = _state.value.completedOnly,
        maleOnly = newValue,
    )

    _state.update {
        it.copy(
            maleOnly = newValue,
            isFilterActive = isFilterActive,
        )
    }
    // Chỉ re-trigger khi hasSearched là true
    if (_state.value.hasSearched) {
        val hasKeyword = _state.value.query.trim().isNotBlank()
        val genre = _state.value.selectedGenre
        if (hasKeyword) {
            search()
        } else if (genre != null) {
            browseByGenre(genre, _state.value.chapterFilter)
        } else if (_state.value.isFilterActive) {
            browseByFilterOnly(_state.value.chapterFilter)
        } else {
            clearFilter()
        }
    }
}

fun clearAllFilters() {
    val currentQuery = _state.value.query
    val hadSearched = _state.value.hasSearched
    _state.update {
        it.copy(
            selectedGenre = null,
            chapterFilter = ChapterCountFilter.NONE,
            completedOnly = true, // Reset về default (Full)
            maleOnly = true,      // Reset về default (Truyện nam)
            isFilterActive = false,
        )
    }
    // Giữ nguyên điều kiện hadSearched để không tự kích hoạt tìm kiếm khi user chưa từng tìm kiếm (MINOR-05)
    if (currentQuery.trim().isNotBlank() && hadSearched) {
        search()
    } else if (hadSearched) {
        clearFilter()
    }
}

// Chuyển tiếp maleOnly ở cả 3 hàm gọi repo trong SearchViewModel (MAJOR-07):
// 1. browseByGenre (lines 261-297):
private fun browseByGenre(
    genre: Genre,
    chapterFilter: ChapterCountFilter,
) {
    searchJob?.cancel()
    searchJob =
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, currentPage = 1, canLoadMore = false) }
            try {
                val results =
                    repository.getStoriesByGenre(
                        genre.slug,
                        page = 1,
                        minChapters = chapterFilter.minChapters,
                        completedOnly = _state.value.completedOnly,
                        maleOnly = _state.value.maleOnly,
                    )
                _state.update {
                    it.copy(
                        results = results.toImmutableList(),
                        isLoading = false,
                        hasSearched = true,
                        canLoadMore = results.isNotEmpty(),
                        error = null,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.update {
                    it.copy(
                        isLoading = false,
                        hasSearched = true,
                        error = (e as? ScraperException)?.message ?: "Không thể tải danh sách truyện. Vui lòng thử lại.",
                    )
                }
            }
        }
}

// 2. browseByFilterOnly (lines 302-339):
private fun browseByFilterOnly(chapterFilter: ChapterCountFilter) {
    searchJob?.cancel()
    searchJob =
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    hasSearched = true,
                    currentPage = 1,
                    canLoadMore = false,
                )
            }
            try {
                val rawStories = repository.getCompletedStories(page = 1, maleOnly = _state.value.maleOnly)
                val filtered = applyLocalFilters(rawStories)
                _state.update {
                    it.copy(
                        results = filtered.toImmutableList(),
                        isLoading = false,
                        currentPage = 1,
                        canLoadMore = rawStories.isNotEmpty() && chapterFilter == ChapterCountFilter.NONE,
                        error = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        hasSearched = true,
                        error = (e as? ScraperException)?.message ?: "Không thể tải danh sách truyện. Vui lòng thử lại.",
                    )
                }
            }
        }
}

// 3. loadMoreFilterResults (lines 398-441):
fun loadMoreFilterResults() {
    val current = _state.value
    if (current.isLoadingMore || !current.isFilterActive || !current.canLoadMore) return
    // Load more is only for server-side genre browsing (no keyword)
    if (current.query.trim().isNotBlank()) return

    val genre = current.selectedGenre ?: return
    val nextPage = current.currentPage + 1
    val minChapters = current.chapterFilter.minChapters

    searchJob?.cancel()
    searchJob =
        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            try {
                val moreResults =
                    repository.getStoriesByGenre(
                        genre.slug,
                        page = nextPage,
                        minChapters = minChapters,
                        completedOnly = current.completedOnly,
                        maleOnly = current.maleOnly,
                    )
                _state.update {
                    val merged = deduplicateStories(it.results + moreResults)
                    val hasNewItems = merged.size > it.results.size
                    it.copy(
                        results = merged.toImmutableList(),
                        isLoadingMore = false,
                        currentPage = if (hasNewItems) nextPage else it.currentPage,
                        canLoadMore = moreResults.isNotEmpty() && hasNewItems,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.update {
                    it.copy(
                        isLoadingMore = false,
                        canLoadMore = false,
                        error = (e as? ScraperException)?.message ?: "Không thể tải thêm truyện. Vui lòng thử lại.",
                    )
                }
            }
        }
}

// Lọc cục bộ: CHỈ áp dụng cho riêng nguồn Wikidich, giữ nguyên truyện của TTV và SST
private fun applyLocalFilters(stories: List<Story>): List<Story> {
    val genre = _state.value.selectedGenre
    val minChapters = _state.value.chapterFilter.minChapters
    val completedOnly = _state.value.completedOnly
    val maleOnly = _state.value.maleOnly

    return stories.filter { story ->
        val matchesGenre =
            genre == null ||
                story.genres.isEmpty() ||
                story.sourceId == "wikidich" ||
                story.genres.any {
                    it.trim().equals(genre.name.trim(), ignoreCase = true)
                }
        val matchesChapters = minChapters == 0 || story.totalChapters == 0 || story.totalChapters >= minChapters
        val matchesStatus =
            !completedOnly ||
                story.status.isBlank() ||
                story.status.contains("Hoàn thành", ignoreCase = true) ||
                story.status.contains("Full", ignoreCase = true)
        val matchesMale =
            !maleOnly ||
                story.sourceId != "wikidich" ||
                story.genres.none { g ->
                    g.trim().lowercase() in NON_MALE_WIKIDICH_GENDERS
                }
        matchesGenre && matchesChapters && matchesStatus && matchesMale
    }
}
```

```kotlin
// Trong SearchScreen.kt:
// 1. Cập nhật activeFilterCount (lines 182-187) và hasActiveFilters (lines 230-233) (MAJOR-09):
val activeFilterCount =
    listOfNotNull(
        state.selectedGenre,
        if (state.chapterFilter != ChapterCountFilter.NONE) true else null,
        if (!state.completedOnly) true else null, // Tắt Full = active filter
        if (!state.maleOnly) true else null,      // Tắt Truyện nam = active filter
    ).size

val hasActiveFilters =
    state.selectedGenre != null ||
        state.chapterFilter != ChapterCountFilter.NONE ||
        !state.completedOnly ||                   // Tắt Full = active filter
        !state.maleOnly                           // Tắt Truyện nam = active filter

// 2. Đặc tả FilterBottomSheet với tham số maleOnly, local state, nút áp dụng và nút xóa tất cả (MAJOR-06, MINOR-10):
@Composable
private fun FilterBottomSheet(
    genres: List<Genre>,
    selectedGenre: Genre?,
    chapterFilter: ChapterCountFilter,
    completedOnly: Boolean,
    maleOnly: Boolean, // Tham số mới
    onDismiss: () -> Unit,
    onApply: (Genre?, ChapterCountFilter, Boolean, Boolean) -> Unit, // Chữ ký 4 tham số
    onClearAll: () -> Unit, // Giữ nguyên tham số onClearAll
) {
    var localGenre by remember { mutableStateOf(selectedGenre) }
    var localChapterFilter by remember { mutableStateOf(chapterFilter) }
    var localCompleted by remember { mutableStateOf(completedOnly) }
    var localMaleOnly by remember { mutableStateOf(maleOnly) } // State cục bộ

    val hasAnyFilter = localGenre != null ||
        localChapterFilter != ChapterCountFilter.NONE ||
        !localCompleted ||
        !localMaleOnly

    // Section "Đối tượng độc giả":
    Text(
        text = "Đối tượng độc giả",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    Row(modifier = Modifier.padding(horizontal = 16.dp)) {
        FilterChip(
            selected = localMaleOnly,
            onClick = { localMaleOnly = !localMaleOnly },
            label = { Text("Truyện nam") },
            leadingIcon = if (localMaleOnly) {
                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
            } else null,
            shape = RoundedCornerShape(20.dp),
        )
    }

    // Nút "Áp dụng bộ lọc" (dòng 751-764):
    Button(
        onClick = { onApply(localGenre, localChapterFilter, localCompleted, localMaleOnly) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
    ) {
        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Áp dụng bộ lọc", style = MaterialTheme.typography.labelLarge)
    }

    // Nút "Xóa tất cả bộ lọc" (dòng 767-777):
    if (hasAnyFilter) {
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onClearAll,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(vertical = 14.dp),
        ) {
            Text("Xóa tất cả bộ lọc", style = MaterialTheme.typography.labelLarge)
        }
    }
}

// 3. Toàn bộ 5 Call sites của applyAllFilters trong SearchScreen.kt (MAJOR-06):
// Call site 1 (Dòng 253 - Xóa thể loại):
viewModel.applyAllFilters(genre = null, chapterFilter = state.chapterFilter, completedOnly = state.completedOnly, maleOnly = state.maleOnly)

// Call site 2 (Dòng 275 - Xóa số chương):
viewModel.applyAllFilters(genre = state.selectedGenre, chapterFilter = ChapterCountFilter.NONE, completedOnly = state.completedOnly, maleOnly = state.maleOnly)

// Call site 3 (Dòng 298 - Bật lại Full):
viewModel.applyAllFilters(genre = state.selectedGenre, chapterFilter = state.chapterFilter, completedOnly = true, maleOnly = state.maleOnly)

// Call site 4 (Chip xóa nhanh mới khi user tắt Truyện nam !state.maleOnly):
if (!state.maleOnly) {
    InputChip(
        selected = true,
        onClick = {
            viewModel.applyAllFilters(
                genre = state.selectedGenre,
                chapterFilter = state.chapterFilter,
                completedOnly = state.completedOnly,
                maleOnly = true,
            )
        },
        label = { Text("Cả truyện nữ/khác", style = MaterialTheme.typography.labelSmall) },
        trailingIcon = {
            Icon(Icons.Default.Clear, contentDescription = "Bật lại lọc truyện nam", modifier = Modifier.size(14.dp))
        },
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.height(28.dp),
    )
}

// Call site 5 (Dòng 590-602 - Trong SearchScreen.kt khi hiển thị FilterBottomSheet):
FilterBottomSheet(
    genres = state.genres,
    selectedGenre = state.selectedGenre,
    chapterFilter = state.chapterFilter,
    completedOnly = state.completedOnly,
    maleOnly = state.maleOnly,
    onDismiss = { showFilterSheet = false },
    onApply = { genre, chapterFilter, completedOnly, maleOnly ->
        showFilterSheet = false
        viewModel.applyAllFilters(genre, chapterFilter, completedOnly, maleOnly)
    },
    onClearAll = {
        showFilterSheet = false
        viewModel.clearAllFilters()
    },
)
```

##### 3. Phía Repository & Remote (`IStoryRepository.kt` & `StorySource.kt`)
*Bỏ default argument `maleOnly` ở interface `IStoryRepository` để compiler cảnh báo toàn bộ các call site, không để lọt lỗi âm thầm:*

```kotlin
// Trong IStoryRepository.kt:
suspend fun getCompletedStories(
    page: Int = 1,
    maleOnly: Boolean,
): List<Story>

suspend fun getStoriesByGenre(
    genreSlug: String,
    page: Int = 1,
    minChapters: Int = 0,
    completedOnly: Boolean = true,
    maleOnly: Boolean,
): List<Story>

// Trong StorySource.kt (Bỏ default argument maleOnly để compiler bắt buộc StoryRepository.kt:147, 218 phải truyền tường minh):
suspend fun getCompletedStories(
    page: Int = 1,
    maleOnly: Boolean,
): List<Story>

suspend fun getStoriesByGenre(
    genreSlug: String,
    page: Int = 1,
    minChapters: Int = 0,
    completedOnly: Boolean = true,
    maleOnly: Boolean,
): List<Story>
```

##### 4. Phía Scraper (`WikidichScraper.kt`)
*Định tuyến thông minh: nhánh nhanh `/bang-xep-hang?so=5$startParam` (kèm fallback `/tim-kiem?qs=2&$COMPLETED_STATUS_PARAM&$NEWEST_SORT_PARAM$startParam`) khi `!maleOnly` để chống throttle rate-limit; nhánh `/tim-kiem?qs=2...&so=4&vo=1` khi `maleOnly`. Phân trang chuẩn xác dùng `SEARCH_PAGE_SIZE = 20`:*

```kotlin
companion object {
    private const val SEARCH_PAGE_SIZE = 20
    private const val MALE_GENDER_PARAM = "gender=5794f03dd7ced228f4419196"
    private const val COMPLETED_STATUS_PARAM = "status=5794f03dd7ced228f4419191"
    private const val NEWEST_SORT_PARAM = "so=4&vo=1"
}

// Xây dựng URL getStoriesByGenre:
override suspend fun getStoriesByGenre(
    genreSlug: String,
    page: Int,
    minChapters: Int,
    completedOnly: Boolean,
    maleOnly: Boolean,
): List<Story> = withContext(Dispatchers.IO) {
    val filterParam = GENRE_QUERY_MAP[genreSlug] ?: return@withContext emptyList()
    val genderParam = if (maleOnly) "&$MALE_GENDER_PARAM" else ""
    val statusParam = if (completedOnly) "&$COMPLETED_STATUS_PARAM" else ""
    val startParam = if (page > 1) "&start=${(page - 1) * SEARCH_PAGE_SIZE}" else ""
    val url = "$baseUrl/tim-kiem?qs=2&$filterParam$genderParam$statusParam&$NEWEST_SORT_PARAM$startParam"
    val doc = fetchDocumentWithRetry(url)
    checkLoginRequired(doc)
    val stories = parseStoryList(doc)
    if (completedOnly) {
        stories.map { it.copy(status = "Hoàn thành") }
    } else {
        stories
    }
}

// Xây dựng URL getCompletedStories:
override suspend fun getCompletedStories(
    page: Int,
    maleOnly: Boolean,
): List<Story> = withContext(Dispatchers.IO) {
    val startParam = if (page > 1) "&start=${(page - 1) * SEARCH_PAGE_SIZE}" else ""
    if (!maleOnly) {
        // Nhánh nhanh chống throttle ~12s: endpoint /bang-xep-hang?so=5
        val url = "$baseUrl/bang-xep-hang?so=5$startParam"
        val doc = fetchDocumentWithRetry(url)
        checkLoginRequired(doc)
        val stories = parseStoryList(doc)
        if (stories.isNotEmpty()) {
            stories.map { it.copy(status = "Hoàn thành") }
        } else {
            val fallbackDoc = fetchDocumentWithRetry(
                "$baseUrl/tim-kiem?qs=2&$COMPLETED_STATUS_PARAM&$NEWEST_SORT_PARAM$startParam"
            )
            checkLoginRequired(fallbackDoc)
            parseStoryList(fallbackDoc).map { it.copy(status = "Hoàn thành") }
        }
    } else {
        // Khi lọc truyện nam: đi qua /tim-kiem?qs=2 với đầy đủ tham số
        val url = "$baseUrl/tim-kiem?qs=2&$MALE_GENDER_PARAM&$COMPLETED_STATUS_PARAM&$NEWEST_SORT_PARAM$startParam"
        val doc = fetchDocumentWithRetry(url)
        checkLoginRequired(doc)
        parseStoryList(doc).map { it.copy(status = "Hoàn thành") }
    }
}
```

---

### 6. Các Bước Thực Hiện

- [ ] **Bước 1: Mở rộng interface `StorySource` & `IStoryRepository`**
  - Khai báo chữ ký **không có default argument cho `maleOnly`** trong interface `StorySource`: `getCompletedStories(page: Int = 1, maleOnly: Boolean)` và `getStoriesByGenre(genreSlug: String, page: Int = 1, minChapters: Int = 0, completedOnly: Boolean = true, maleOnly: Boolean)`. Nhờ đó compiler bắt buộc `StoryRepository.kt:147, 218` phải truyền tham số `maleOnly` tường minh.
  - Khai báo chữ ký **không có default argument cho `maleOnly`** trong `IStoryRepository`: `getCompletedStories(page: Int = 1, maleOnly: Boolean)` và `getStoriesByGenre(genreSlug: String, page: Int = 1, minChapters: Int = 0, completedOnly: Boolean = true, maleOnly: Boolean)`. Nhờ đó compiler bắt buộc toàn bộ call site phải truyền tham số tường minh.
  - Cập nhật các hàm override trong `TangThuVienScraper`, `SsTruyenScraper`, `StoryRepository`, và `TestStorySource` trong `LacaTruyenApiTest.kt` (chỉ nhận `maleOnly: Boolean`, **không khai báo default value ở override** để tuân thủ cú pháp Kotlin).

- [ ] **Bước 2: Cải tiến & Chuẩn hóa URL, hằng số và loại bỏ 4 tag giới tính trong `WikidichScraper.kt`**
  - Khai báo hằng số `MALE_GENDER_PARAM = "gender=5794f03dd7ced228f4419196"`, `COMPLETED_STATUS_PARAM = "status=5794f03dd7ced228f4419191"`, `NEWEST_SORT_PARAM = "so=4&vo=1"`.
  - Gỡ bỏ hoàn toàn 4 tag `ngon-tinh`, `dam-my`, `bach-hop`, `truyen-nam` khỏi `GENRE_QUERY_MAP`.
  - Gỡ bỏ hoàn toàn 4 tag `Ngôn Tình`, `Đam Mỹ`, `Bách Hợp`, `Truyện Nam` khỏi `getGenreList()`.
  - Viết lại `getStoriesByGenre(genreSlug, page, minChapters, completedOnly, maleOnly)`: chuyển sang `qs=2`, kết hợp `&$filterParam$genderParam$statusParam&$NEWEST_SORT_PARAM$startParam` (với `startParam = if (page > 1) "&start=${(page - 1) * SEARCH_PAGE_SIZE}" else ""`), loại bỏ sạch các tham số cũ (`qs=1`, `&tc=&tf=0&m=...&y=...&q=`). Dùng đúng `fetchDocumentWithRetry`, `checkLoginRequired`, `parseStoryList`.
  - Viết lại `getCompletedStories(page, maleOnly)` với cơ chế định tuyến thông minh:
    - Nếu `!maleOnly`: Gọi nhánh nhanh `/bang-xep-hang?so=5$startParam` để không chạm hạn mức rate-limit throttle ~12s của `/tim-kiem`. Nếu danh sách parse rỗng, tự động fallback sang `$baseUrl/tim-kiem?qs=2&$COMPLETED_STATUS_PARAM&$NEWEST_SORT_PARAM$startParam`.
    - Nếu `maleOnly`: Gọi `/tim-kiem?qs=2&$MALE_GENDER_PARAM&$COMPLETED_STATUS_PARAM&$NEWEST_SORT_PARAM$startParam`.

- [ ] **Bước 3: Mở rộng `StoryRepository.kt`**
  - Trong `StoryRepository.getCompletedStories(page, maleOnly)`: chuyển tiếp tham số `maleOnly` tới các `activeSources()`.
  - Trong `StoryRepository.getStoriesByGenre(genreSlug, page, minChapters, completedOnly, maleOnly)`: chuyển tiếp tham số `maleOnly` tới các `activeSources()`.

- [ ] **Bước 4: Nâng cấp máy trạng thái bộ lọc & 3 hàm gọi repo trong `SearchViewModel.kt` và `SearchScreen.kt`**
  - Thêm trường `maleOnly: Boolean = true` vào `SearchState` (giữ nguyên trường `isFilterActive: Boolean = false`).
  - Viết helper `hasAnyNonDefaultFilter(genre, chapterFilter, completedOnly, maleOnly)` và tái sử dụng trực tiếp trong `applyAllFilters`, `toggleCompletedFilter`, `toggleMaleFilter`.
  - Cập nhật `applyFilter(genre, chapterFilter)` (chữ ký 2 tham số, ủy quyền sang `applyAllFilters`), `applyAllFilters(genre, chapterFilter, completedOnly, maleOnly)` (**bỏ default argument của `maleOnly`**), `toggleCompletedFilter()`, `toggleMaleFilter()`, `clearAllFilters()` (giữ nguyên điều kiện `&& hadSearched`).
  - **Cập nhật chuyển tiếp `maleOnly` ở cả 3 hàm gọi repo trong `SearchViewModel`**: `browseByGenre` (lines 261-297, `maleOnly = _state.value.maleOnly`), `browseByFilterOnly` (lines 302-339, `maleOnly = _state.value.maleOnly`), `loadMoreFilterResults` (lines 398-441, `maleOnly = current.maleOnly`); giữ nguyên toàn bộ thân hàm gốc, guard và phân trang của 3 hàm này.
  - Cập nhật `applyLocalFilters()`: Dùng `minChapters = _state.value.chapterFilter.minChapters`, `story.status.contains("Hoàn thành", ignoreCase = true)`, và chỉ lọc bỏ truyện nữ tần/đam mỹ/bách hợp (`NON_MALE_WIKIDICH_GENDERS`) đối với truyện có `story.sourceId == "wikidich"`, giữ nguyên truyện từ TangThuVien và SsTruyen.
  - Cập nhật `SearchScreen.kt`:
    - Cập nhật `activeFilterCount` (lines 182-187) và `hasActiveFilters` (lines 230-233) thêm điều kiện `!state.maleOnly` để hiển thị badge và hàng chip khi tắt lọc nam.
    - Thêm tham số `maleOnly`, local state `localMaleOnly`, điều kiện `hasAnyFilter` có `!localMaleOnly`, nút "Xóa tất cả bộ lọc" reset cả `localMaleOnly = true`, chữ ký `onApply` 4 tham số, nút "Áp dụng bộ lọc" (lines 751-764) truyền đủ 4 tham số trong `FilterBottomSheet`.
    - Cập nhật đồng bộ toàn bộ 5 call sites của `applyAllFilters` (3 InputChip cũ: 253, 275, 298; 1 InputChip mới "Cả truyện nữ/khác" khi `!state.maleOnly`; và 1 call site trong `SearchScreen.kt` khi hiển thị `FilterBottomSheet` tại dòng 590-602).

- [ ] **Bước 5: Nâng cấp `ExploreViewModel.kt` và `ExploreScreen.kt`**
  - Thêm `maleOnly: Boolean = true` vào `ExploreState`.
  - Thêm hàm `toggleMaleOnly()` vào `ExploreViewModel`.
  - Cập nhật `loadFirstPage()` và `loadMore()` truyền `maleOnly = _state.value.maleOnly`.
  - Cập nhật `pullToRefresh()` (`ExploreViewModel.kt:158-190`) giữ nguyên hủy job (`loadJob?.cancel()`, `loadMoreJob?.cancel()`), `canLoadMore = stories.isNotEmpty()`, xử lý exception chuẩn và truyền tường minh `maleOnly = _state.value.maleOnly`.
  - Trong `ExploreScreen.kt`: Bọc `PullToRefreshBox` bằng cấu trúc `Column(modifier = Modifier.fillMaxSize())`, phía trên là `Row` chứa FilterChip "Truyện nam" hiển thị độc lập với `state.genres`, phía dưới là `Box(modifier = Modifier.weight(1f).fillMaxWidth())` chứa khối `when` và lưới truyện, không để xếp chồng lên nhau (`MINOR-08`).

- [ ] **Bước 6: Cập nhật & Bổ sung Unit Tests**
  - `WikidichScraperTest.kt`: Viết lại test hiện có `getStoriesByGenre uses filter form qs=1 params...` (`lines 211-259`) thành `getStoriesByGenre uses filter form qs=2 params with newest sort and male filter` (Loại C - Approved Spec Change); thêm test MockWebServer kiểm tra URL `getCompletedStories` nhánh nhanh `/bang-xep-hang` vs nhánh `/tim-kiem` và ca fallback khi danh sách rỗng; kiểm tra danh sách thể loại không còn 4 tag giới tính cũ.
  - `SearchViewModelTest.kt`: Thêm test kịch bản tắt nam + bật lại full không làm mất cờ `maleOnly`; test `browseByGenre`, `browseByFilterOnly`, `loadMoreFilterResults` chuyển tiếp chính xác `maleOnly` tới repository; test `applyLocalFilters` chỉ lọc truyện Wikidich và giữ nguyên truyện TTV/SST.
  - `ExploreViewModelTest.kt`: Thêm test `toggleMaleOnly()` kích hoạt tải lại với `maleOnly = false`; test `pullToRefresh()` bảo lưu đúng `maleOnly`.
  - `StoryRepositoryTest.kt`: Bổ sung 2 unit test kiểm tra `StoryRepository` chuyển tiếp chính xác tham số `completedOnly` và `maleOnly` tới các scraper nguồn trong `getCompletedStories` và `getStoriesByGenre`.
  - `SsTruyenScraperTest.kt`: Cập nhật 2 call site dòng 324, 351 thêm đối số `maleOnly = true` theo Loại B (Test Harness).
  - Cập nhật các stub/call site test hiện có trong `ExploreViewModelTest.kt`, `SearchViewModelTest.kt`, `WikidichScraperTest.kt`, `SsTruyenScraperTest.kt`, `LacaTruyenApiTest.kt` theo **Loại B (Test Harness — thêm đối số `maleOnly = true` hoặc matcher sau khi bỏ default argument ở interface, không giảm số assert và không nới matcher)**.

- [ ] **Bước 7: Quality Gate & Cập nhật Documentation**
  - Thực thi kiểm tra chất lượng: `.\gradlew.bat compileDebugKotlin; .\gradlew.bat ktlintCheck; .\gradlew.bat testDebugUnitTest; .\gradlew.bat assembleDebug`.
  - Cập nhật tài liệu: `docs/scraping.md`, `docs/progress.md`, `docs/changelog.md`, `docs/architecture.md`, `docs/plans/README.md`.

---

### 7. Test Contract (Đặc Tả Hành Vi Nghiệp Vụ)

Bảng hợp đồng kiểm thử quy định chi tiết hành vi kỳ vọng cho tính năng:

| Mã TC | Ngữ Cảnh / Thao Tác | Đầu Vào (Input) | Kết Quả Kỳ Vọng (Output) | Xử Lý Thất Bại & Thông Báo |
| :--- | :--- | :--- | :--- | :--- |
| **TC-01** | Wikidich duyệt theo thể loại mặc định | `genreSlug = "he-thong"`, `page = 1`, `maleOnly = true`, `completedOnly = true` | Request gửi tới Wikidich có URL chứa: `/tim-kiem?qs=2&tag=5794f03dd7ced228f44191b6&gender=5794f03dd7ced228f4419196&status=5794f03dd7ced228f4419191&so=4&vo=1`. Không chứa `qs=1`, không chứa query param cũ. | Nếu kết nối lỗi, ném `ScraperException.NetworkError`. |
| **TC-02** | Wikidich duyệt theo thể loại khi tắt Truyện nam | `genreSlug = "he-thong"`, `page = 1`, `maleOnly = false`, `completedOnly = true` | URL chứa `/tim-kiem?qs=2&tag=5794f03dd7ced228f44191b6&status=5794f03dd7ced228f4419191&so=4&vo=1` (KHÔNG chứa tham số `gender=`). | Như trên. |
| **TC-03** | Wikidich duyệt tab Tất cả khi bật Truyện nam | `page = 1`, `maleOnly = true` qua `getCompletedStories` | Request gửi tới URL `/tim-kiem?qs=2&gender=5794f03dd7ced228f4419196&status=5794f03dd7ced228f4419191&so=4&vo=1`. | Timeout 25s; nếu lỗi ném `ScraperException.NetworkError`. |
| **TC-04** | Wikidich duyệt tab Tất cả khi tắt Truyện nam (Nhánh nhanh & Fallback) | `page = 1` hoặc `page = 2`, `maleOnly = false` qua `getCompletedStories` | **Nhánh chính**: Request gửi tới URL `/bang-xep-hang?so=5` (trang 1) hoặc `/bang-xep-hang?so=5&start=20` (trang 2). Phản hồi nhanh ~1.3s, không chạm endpoint `/tim-kiem`.<br>**Nhánh Fallback**: Nếu danh sách parse từ `/bang-xep-hang` rỗng, tự động gửi request thứ 2 tới `/tim-kiem?qs=2&$COMPLETED_STATUS_PARAM&$NEWEST_SORT_PARAM$startParam` để đảm bảo dữ liệu. | Nếu cả 2 request đều lỗi, ném `ScraperException.NetworkError`. |
| **TC-05** | Khảo sát danh sách thể loại Wikidich | Gọi `WikidichScraper.getGenreList()` và `GENRE_QUERY_MAP` | Danh sách trả về không chứa bất kỳ thể loại nào có slug: `truyen-nam`, `ngon-tinh`, `dam-my`, `bach-hop`. | Danh sách thể loại hợp lệ > 20 thể loại còn lại. |
| **TC-06** | Kéo làm mới trên ExploreScreen | `state.maleOnly = false`, người dùng kéo làm mới (`pullToRefresh`) | `pullToRefresh()` gọi repository với tham số `maleOnly = false` tường minh; dữ liệu tải về giữ nguyên trạng thái không lọc truyện nam, chip vẫn hiển thị tắt. | Báo lỗi tiếng Việt `(e as? ScraperException)?.message ?: "Không thể làm mới dữ liệu. Vui lòng kiểm tra mạng và thử lại."` nếu repo ném exception. |
| **TC-07** | Máy trạng thái SearchViewModel khi kết hợp bộ lọc | Bấm tắt `maleOnly = false` và tắt `completedOnly = false`, sau đó bấm bật lại `completedOnly = true` | `state.maleOnly` vẫn giữ nguyên giá trị `false`, `isFilterActive` vẫn là `true`, không bị reset về trạng thái ban đầu; cả 3 hàm `browseByGenre`, `browseByFilterOnly`, `loadMoreFilterResults` đều chuyển tiếp `maleOnly = false` tới repo. | State machine chuyển tiếp chính xác. |
| **TC-08** | Xóa nhanh bộ lọc qua `InputChip` trên SearchScreen | Bấm nút 'X' trên chip thể loại khi đang tắt `maleOnly = false` | `applyAllFilters` được gọi với `genre = null` và giữ nguyên `maleOnly = false`, không làm âm thầm bật lại lọc nam. | State machine bảo lưu chính xác các cờ còn lại. |
| **TC-09** | Xử lý lỗi 2 tầng (Repository nuốt lỗi nguồn lẻ vs ViewModel bắt exception) | Đa nguồn gặp sự cố mạng hoặc lỗi nội bộ | **Tầng Repository**: nuốt lỗi từng nguồn lẻ trả `emptyList()`; nếu tất cả các nguồn cùng gặp sự cố nội bộ, repo trả danh sách rỗng (`emptyList()`), UI hiển thị trạng thái danh sách rỗng.<br>**Tầng ViewModel**: nếu repository ném exception trực tiếp ra ngoài, ViewModel bắt và gán lỗi tiếng Việt: `"Không thể tải danh sách truyện. Vui lòng thử lại."` (`browseByGenre`/`browseByFilterOnly`) hoặc `"Không thể kết nối. Vui lòng kiểm tra mạng và thử lại."` (`search()` và `ExploreViewModel`). | Không crash ứng dụng, không hiện tiếng Anh thô. |
| **TC-10** | Lọc cục bộ `applyLocalFilters` tầng client-side | `maleOnly = true` trên kết quả tìm kiếm gộp đa nguồn | **Ca dương**: Truyện từ Wikidich có chứa nhãn "Ngôn tình" hoặc "Đam mỹ" bị lọc bỏ. **Ca âm**: Truyện Wikidich nam (hoặc genres rỗng) được giữ lại; **truyện từ TangThuVien và SsTruyen được giữ nguyên kể cả khi mang tag "Ngôn tình" hay "Nữ sinh"**. | Kết quả gộp chuẩn xác, không làm biến mất truyện của TTV/SST. |

---

### 8. Verification Plan

#### 8.1. Kiểm Thử Tự Động (Unit Tests)

| Test Class | Tên Test Case Cụ Thể | Mục Đích Kiểm Thử | Phân Loại Triage |
| :--- | :--- | :--- | :--- |
| `WikidichScraperTest` | `getStoriesByGenre uses filter form qs=2 params with newest sort and male filter` | Xác nhận URL thể loại dùng `qs=2`, `gender=...`, `so=4&vo=1`. Đổi tên và cập nhật từ test cũ `...qs=1 params...`. | **Loại C (Approved Spec Change)** |
| `WikidichScraperTest` | `getStoriesByGenre without male filter omits gender param` | Xác nhận URL thể loại khi `maleOnly = false` không chứa `gender=`. | **Loại A/New** |
| `WikidichScraperTest` | `getCompletedStories with maleOnly calls tim kiem with newest sort` | Xác nhận duyệt Tất cả khi `maleOnly = true` gọi `/tim-kiem?qs=2...&so=4&vo=1`. | **Loại A/New** |
| `WikidichScraperTest` | `getCompletedStories without maleOnly calls bang xep hang fast path` | Xác nhận duyệt Tất cả khi `maleOnly = false` gọi `/bang-xep-hang?so=5`. | **Loại A/New** |
| `WikidichScraperTest` | `getCompletedStories fallback to tim kiem when bang xep hang is empty` | Xác nhận duyệt Tất cả khi `maleOnly = false` và bảng xếp hạng rỗng thì fallback sang `/tim-kiem`. | **Loại A/New** |
| `WikidichScraperTest` | `getGenreList does not contain removed gender tags` | Xác nhận 4 tag giới tính cũ đã bị gỡ sạch khỏi danh sách thể loại. | **Loại A/New** |
| `ExploreViewModelTest` | `toggleMaleOnly updates state and reloads first page with maleOnly false` | Xác nhận chuyển trạng thái chip Truyện nam trên Khám Phá. | **Loại A/New** |
| `ExploreViewModelTest` | `pullToRefresh preserves maleOnly filter state` | Xác nhận kéo làm mới không làm reset `maleOnly`. | **Loại A/New** |
| `SearchViewModelTest` | `toggleMaleFilter updates state and triggers search or browse` | Xác nhận bật/tắt Truyện nam trên Tìm Kiếm. | **Loại A/New** |
| `SearchViewModelTest` | `toggleCompletedFilter does not reset maleOnly when maleOnly is false` | Xác nhận máy trạng thái không reset âm thầm `maleOnly`. | **Loại A/New** |
| `SearchViewModelTest` | `browseByGenre forwards maleOnly to repository` | Xác nhận `browseByGenre` chuyển tiếp chính xác cờ `maleOnly` tới repo. | **Loại A/New** |
| `SearchViewModelTest` | `browseByFilterOnly forwards maleOnly to repository` | Xác nhận `browseByFilterOnly` chuyển tiếp chính xác cờ `maleOnly` tới repo. | **Loại A/New** |
| `SearchViewModelTest` | `loadMoreFilterResults forwards maleOnly to repository` | Xác nhận cuộn tải trang 2 chuyển tiếp chính xác cờ `maleOnly` tới repo. | **Loại A/New** |
| `SearchViewModelTest` | `applyLocalFilters filters wikidich non-male stories and preserves other sources` | Xác nhận lọc cục bộ chỉ áp dụng cho Wikidich, bảo vệ truyện của TTV/SST. | **Loại A/New** |
| `StoryRepositoryTest` | `getCompletedStories forwards maleOnly to every active source` | Xác nhận `StoryRepository` chuyển tiếp đối số `maleOnly` tới tất cả `activeSources()` trong `getCompletedStories`. | **Loại A/New** |
| `StoryRepositoryTest` | `getStoriesByGenre forwards maleOnly to every active source` | Xác nhận `StoryRepository` chuyển tiếp đối số `maleOnly` tới tất cả `activeSources()` trong `getStoriesByGenre`. | **Loại A/New** |
| `SsTruyenScraperTest` | `getCompletedStories and getStoriesByGenre accept maleOnly param` | Cập nhật 2 call site dòng 324, 351 thêm `maleOnly = true` sau khi bỏ default ở `StorySource`. | **Loại B (Test Harness)** |

> **Quy chuẩn cập nhật Stub / Mock hiện có theo Loại B (Test Harness)**: Các stub và call site hiện có trong `ExploreViewModelTest.kt` (`:48, 63, 64, 79, 95, 107, 128, 130, 145, 146, 160`), `SearchViewModelTest.kt` (`:183, 196, 211, 212`), `WikidichScraperTest.kt` (`:187, 195`), `SsTruyenScraperTest.kt` (`:324, 351`) và `LacaTruyenApiTest.kt` được cập nhật thêm đối số `maleOnly = true` hoặc matcher sau khi bỏ default argument ở interface `StorySource` và `IStoryRepository`. Việc cập nhật này tuân thủ nghiêm ngặt Test Failure Triage Loại B: **không giảm số lượng assert, không nới matcher** (`assertEquals` giữ nguyên, không đổi thành `assertNotNull`/`assertTrue`).

#### 8.2. Lệnh Chạy Quality Gate (Terminal Windows PowerShell 5.1)
```powershell
# Bước 1: Biên dịch kiểm tra cú pháp Kotlin
.\gradlew.bat compileDebugKotlin

# Bước 2: Kiểm tra định dạng mã nguồn ktlint
.\gradlew.bat ktlintCheck

# Bước 3: Chạy unit tests mục tiêu
.\gradlew.bat testDebugUnitTest --tests "com.personal.apptruyen.data.remote.WikidichScraperTest"
.\gradlew.bat testDebugUnitTest --tests "com.personal.apptruyen.data.repository.StoryRepositoryTest"
.\gradlew.bat testDebugUnitTest --tests "com.personal.apptruyen.ui.search.SearchViewModelTest"
.\gradlew.bat testDebugUnitTest --tests "com.personal.apptruyen.ui.explore.ExploreViewModelTest"

# Bước 4: Quality gate đầy đủ (ktlint + toàn bộ test + assemble debug)
.\gradlew.bat ktlintCheck; .\gradlew.bat testDebugUnitTest; .\gradlew.bat assembleDebug
```

#### 8.3. Wiring Probes & Negative Probes
- **Wiring Probe (SearchViewModel state flow)**: Đặt mock repository kiểm tra đối số `maleOnly`:
  ```kotlin
  viewModel.toggleMaleFilter() // maleOnly = false
  viewModel.toggleCompletedFilter() // completedOnly = false
  viewModel.toggleCompletedFilter() // completedOnly = true
  coVerify { repository.getCompletedStories(page = 1, maleOnly = false) }
  ```
- **Negative Probe (ktlint)**: Tạo một biến tạm vi phạm quy tắc ktlint (ví dụ khoảng trắng thừa hoặc import thừa trong `WikidichScraper.kt`), chạy `.\gradlew.bat ktlintCheck`, xác nhận lệnh thất bại với mã lỗi khác 0 và chỉ rõ file vi phạm, sau đó hoàn tác (revert) sạch sẽ đoạn vi phạm trước khi kết luận.

---

### 9. Rủi Ro & Phòng Ngừa

| Rủi Ro | Mức Độ | Biện Pháp Phòng Ngừa Kỹ Thuật (Technical Control) | Dẫn Chứng / Kiểm Chứng |
| :--- | :---: | :--- | :--- |
| **Server Wikidich throttle giữ kết nối ~12s trên `/tim-kiem` khi gọi `getCompletedStories`** (`docs/lessons-learned.md:112`). | **Cao** | **Thiết lập 4 Technical Controls đồng bộ**: <br>1. **Định tuyến thông minh**: Khi `maleOnly = false`, scraper sử dụng nhánh nhanh `/bang-xep-hang?so=5$startParam` (~1.3s) không bị throttle.<br>2. **Timeout an toàn & Không dồn request**: `timeoutFor(wikidich) = 25s` (`StoryRepository.kt:115`) đủ dài để không bị timeout; chỉ tải 1 trang mỗi lần duyệt.<br>3. **Client-side Job Cancellation**: `loadFirstPage()` và `applyAllFilters()` đều hủy coroutine Job cũ (`loadJob?.cancel()`, `searchJob?.cancel()`) trước khi tải mới; bấm chip nhanh liên tiếp chỉ xử lý request cuối cùng.<br>4. **In-memory HTTP Cache 10 phút**: Tận dụng cơ chế cache trong bộ nhớ của `BaseScraper.kt:57, 123-124, 374` (`CACHE_TTL_MS = 10 * 60 * 1000L`) cho các truy vấn GET cùng URL trong phiên duyệt truyện. | `[SRC] docs/lessons-learned.md:112-121`; `[SRC] StoryRepository.kt:115`; `[SRC] BaseScraper.kt:57, 123-124, 374`; kiểm chứng qua test `getCompletedStories without maleOnly calls bang xep hang fast path` và Mental Simulation S6. |
| **Server Wikidich trả về kết quả sắp xếp "Đọc nhiều nhất" thay vì "Mới nhất"** do thiếu `so=4&vo=1`. | **Cao** | Hằng số `NEWEST_SORT_PARAM = "so=4&vo=1"` bắt buộc gắn cứng vào chuỗi query builder của mọi URL qua endpoint `/tim-kiem?qs=2` (trong cả `getStoriesByGenre` và `getCompletedStories`). Nhánh nhanh `/bang-xep-hang?so=5` là bảng xếp hạng riêng biệt. | Technical Control trong `WikidichScraper.kt`; kiểm chứng qua `RecordedRequest.requestUrl` trong unit test MockWebServer (TC-01, TC-03). |
| **Lọc nhầm truyện của TangThuVien và SsTruyen ở tầng client-side** khi bật `maleOnly = true`. | **Cao** | Trong `SearchViewModel.applyLocalFilters`, bắt buộc đặt điều kiện kiểm tra nguồn: `story.sourceId != "wikidich"` bỏ qua lọc. Chỉ những truyện có `story.sourceId == "wikidich"` mới bị kiểm tra tập `NON_MALE_WIKIDICH_GENDERS`. Truyện của TTV và SST được giữ lại. | Technical Control trong `SearchViewModel.kt`; kiểm chứng qua unit test `SearchViewModelTest` (TC-10). |
| **Treo luồng chính (Main Thread) khi parse hoặc fetch mạng trên Android**. | **Cao** | Toàn bộ hàm suspend của `WikidichScraper` bắt buộc được bọc trong `withContext(Dispatchers.IO)`. | `[SRC] WikidichScraper.kt` tuân thủ bài học kinh nghiệm 2026-10-14 (`Dispatchers.IO`). |
| **Lệch trạng thái bộ lọc trên SearchScreen hoặc mất cờ `maleOnly` khi kéo làm mới** (`ExploreViewModel.pullToRefresh`). | **Trung bình** | 1. `pullToRefresh()` bắt buộc truyền tường minh `maleOnly = _state.value.maleOnly`.<br>2. `SearchViewModel` sử dụng helper `hasAnyNonDefaultFilter` và định tuyến chuyển trạng thái nhất quán, không rơi vào `clearFilter()` khi còn cờ `!maleOnly`.<br>3. Cập nhật đủ 5 call sites của `applyAllFilters` trong `SearchScreen.kt`. | Logic đồng nhất trong `SearchViewModel.kt`, `SearchScreen.kt`, `ExploreViewModel.kt`; kiểm chứng qua `SearchViewModelTest` (TC-07, TC-08) và `ExploreViewModelTest` (TC-06). |
| **Trùng lặp ID item trong LazyColumn / LazyGrid khi phân trang**. | **Thấp** | Hàm `deduplicateStories` bằng compound key `"${story.sourceId}:${story.id}"` đã có sẵn trong cả `SearchViewModel` và `ExploreViewModel` ngăn ngừa crash Compose do trùng key. | `[SRC] SearchViewModel.kt:466-472` và `ExploreViewModel.kt:185-195`. |

---

### 10. Cập Nhật Documentation

Theo ma trận quy định trong `.agents/rules/06-docs.md`:

| File Tài Liệu | Nội Dung Cập Nhật Cụ Thể |
| :--- | :--- |
| `docs/scraping.md` | Cập nhật mục **Wikidich (`wikicv.org`)**: chuẩn hóa cấu trúc URL `qs=2` kèm `&gender=5794f03dd7ced228f4419196` và `&so=4&vo=1`; cơ chế định tuyến nhánh nhanh `/bang-xep-hang?so=5$startParam` vs nhánh lọc nam `/tim-kiem?qs=2...`; ghi rõ việc loại bỏ các query param cũ (`qs=1`, `tc`, `tf=0`, `m`, `y`) và việc tách riêng bộ lọc Truyện nam. |
| `docs/architecture.md` | Cập nhật mục **Data Flow & UI State**: mô tả luồng tham số `maleOnly` từ `ExploreViewModel` / `SearchViewModel` qua `IStoryRepository` tới `WikidichScraper`; cập nhật sơ đồ định tuyến bộ lọc. |
| `docs/progress.md` | Thêm mục hoàn thành: Tính năng lọc độc lập Truyện nam và sắp xếp Mới nhất nguồn Wikidich. Cập nhật timestamp. |
| `docs/changelog.md` | Thêm entry dưới `## [Unreleased]`: `### Thêm mới` (Bộ lọc riêng Truyện nam trên Khám Phá và Tìm Kiếm), `### Sửa đổi` (Chuẩn hóa URL Wikidich sang `qs=2&...&so=4&vo=1`, gỡ 4 tag giới tính khỏi danh sách thể loại). |
| `docs/plans/README.md` | Bổ sung entry kế hoạch vào bảng trạng thái với trạng thái ban đầu `Ready for Review`. |

---

## Audit History

> Phần này do Reviewer ghi nhận kết quả đánh giá (Claude Code CLI hoặc subagent `@reviewer`). Planner không chỉnh sửa phần này.

### Audit Run 1

- **Reviewer**: Claude Code CLI (claude-opus-5-5)
- **Ngày**: 2026-10-27
- **Đối tượng**: Technical Plan Revision 1

#### Kiểm Chứng Lỗi Cũ

Không có Audit Run trước, không có lỗi cũ cần kiểm lại.

#### Đối Chiếu Các File `[MODIFY]` Với Code Thật

| File | Khớp / Lệch |
| :--- | :--- |
| `StorySource.kt` | Khớp: `getCompletedStories(page: Int = 1)` tại `StorySource.kt:51`, `getStoriesByGenre(..., completedOnly: Boolean = true)` tại `:54-59`. |
| `WikidichScraper.kt` | Khớp: 4 tag giới tính tại `:84-87` và `:366-369`; `getCompletedStories` dùng `/bang-xep-hang?so=5` tại `:320` kèm fallback `/tim-kiem?status=...&qs=2` tại `:327`; `getStoriesByGenre` dùng `qs=1...&tc=&tf=0&m=&y=&q=` tại `:347`. Lệch: plan ghi `[SRC] WikidichScraper.kt:320` làm bằng chứng cho việc "`/bang-xep-hang` không hỗ trợ `gender=`" và "thiếu `so=4&vo=1` thì mặc định Đọc nhiều nhất" — dòng 320 chỉ là chuỗi URL, không chứa thông tin này. |
| `TangThuVienScraper.kt` / `SsTruyenScraper.kt` | Khớp: override tại `TangThuVienScraper.kt:376,385` và `SsTruyenScraper.kt:713,724`. |
| `IStoryRepository.kt` / `StoryRepository.kt` | Khớp: `IStoryRepository.kt:18-25`; `StoryRepository.kt:128-185` gọi `source.getCompletedStories(p)` tại `:147`, `:194-264` gọi `source.getStoriesByGenre(genreSlug, p, 0, completedOnly)` tại `:218`. |
| `SearchViewModel.kt` | Khớp một phần. Plan bỏ sót `toggleCompletedFilter` (`:368-396`) và các call site `applyAllFilters` trong `SearchScreen.kt:253,275,298` (xem MAJOR-04). |
| `SearchScreen.kt` | Khớp: `activeFilterCount` `:182-186`, `hasActiveFilters` `:230-233`, `FilterBottomSheet` `:614-622`, `onApply` 3 tham số `:616`. |
| `ExploreViewModel.kt` | Lệch: plan ghi cập nhật `loadFirstPage()`, `loadMore()`, `refresh()` nhưng `refresh()` (`:154-156`) chỉ gọi `loadFirstPage()`; hàm thực sự gọi repository riêng là `pullToRefresh()` (`:158-190`) bị bỏ sót (xem MAJOR-03). |
| `ExploreScreen.kt` | Khớp: hàng chip thể loại tại `:127-151`, nằm trong nhánh `else` của `when` (`:111-126`) và trong `if (state.genres.isNotEmpty())` (`:129`). |
| `WikidichScraperTest.kt` | Lệch: test hiện có `getStoriesByGenre uses filter form qs=1 params...` (`:211-259`) assert `qs=1` (`:241,249`) và `tc=&tf=0` (`:244`) — sẽ đỏ sau khi đổi URL, plan không ghi rõ việc viết lại test này (MINOR-02). |
| `StoryRepositoryTest.kt`, `SearchViewModelTest.kt`, `ExploreViewModelTest.kt`, `LacaTruyenApiTest.kt` | Tồn tại. `LacaTruyenApiTest.kt:95-102` có `TestStorySource` cần cập nhật chữ ký — khớp với plan. |

#### Phát Hiện Mới (Fresh Findings & Regression Check)

**[MAJOR-01] Đưa `getCompletedStories` của Wikidich quay lại endpoint `/tim-kiem` bị throttle ~12s, ngược với bài học 2026-10-14 mà plan khai đã đọc.**
- `[SRC]` `docs/lessons-learned.md:112`: `/tim-kiem` giữ kết nối ~12.03s khi có request liên tiếp. `:116`: `getCompletedStories()` được chuyển sang `/bang-xep-hang?so=5` chính vì lý do "không chạm hạn mức của `/tim-kiem`". `:121` (Quy tắc phòng ngừa 2): "tách các luồng duyệt trang chủ (`getCompletedStories`) ra khỏi endpoint tìm kiếm".
- Plan (mục 5.2 bước 4, Bước 2) chuyển `getCompletedStories` sang `/tim-kiem?qs=2...`. Hệ quả: mở Khám Phá (`ExploreViewModel.kt:58-61` → `loadFirstPage` → `getCompletedStories`) rồi chọn thể loại hoặc bấm chip "Truyện nam" (`toggleMaleOnly` → `loadFirstPage`) đều là 2 request liên tiếp vào `/tim-kiem`, mỗi lần bật/tắt có thể chờ ~12s. `[SRC]` `StoryRepository.kt:115` timeout Wikidich 25s nên không bị cắt, nhưng độ trễ UX tăng rõ.
- Bảng Rủi ro (mục 9) không có dòng nào cho rủi ro này. Yêu cầu: (a) bổ sung rủi ro + control (ví dụ đo `curl.exe -w "%{time_total}"` 2 request liên tiếp trên `/tim-kiem?qs=2...` và ghi kết quả; hoặc giữ `/bang-xep-hang` cho nhánh `maleOnly=false` nếu endpoint đó đáp ứng; hoặc debounce chip), (b) nêu rõ trong plan vì sao chấp nhận quay lại `/tim-kiem` bất chấp quy tắc phòng ngừa đã ghi.

**[MAJOR-02] Ngữ nghĩa `so=4&vo=1` ("Mới nhất") và nhận định "thiếu tham số thì mặc định Đọc nhiều nhất" chưa được kiểm chứng.**
- Bằng chứng `[URL]` trong plan (mục 3.1.1) chỉ nêu "trang trả về 200 OK với cấu trúc layout chuẩn". `[URL]` reviewer: WebFetch `https://wikicv.org/tim-kiem?qs=2&gender=5794f03dd7ced228f4419196&tag=5794f03dd7ced228f44191b6&so=4&vo=1` (2026-10-27) trả về trang chặn "Đăng nhập để xem nội dung", không có `.book-item` và không có form `so`/`vo` — nên 200 OK ẩn danh không chứng minh được bộ lọc hay thứ tự sắp xếp.
- `[URL]` WebFetch `https://wikicv.org/` (2026-10-27): trang chủ có link `/bang-xep-hang?so=4` và `/bang-xep-hang?so=5`, tức `so=` mang nghĩa xếp hạng khác nhau tùy endpoint; ngữ nghĩa `so=4` trên `/tim-kiem` cần bằng chứng riêng. (Trang chủ cũng dùng `qs=2` cho các link lọc, ví dụ `/tim-kiem?qs=2&age=5794f03dd7ced228f441919b` — điểm này ủng hộ việc chuyển sang `qs=2`.)
- `[SRC]` `WikidichScraper.kt:320` được plan trích làm bằng chứng nhưng không chứa thông tin về `so=4`, `vo=1` hay việc `/bang-xep-hang` không nhận `gender=`.
- Theo `.agents/rules/04-scraping.md:19-22` phải khảo sát HTML thực tế. Yêu cầu: ghi lại (từ HTML đã đăng nhập, ví dụ lưu thành fixture `app/src/test/resources/fixtures/wikidich/`) các `<option>` của `select[name=so]` và ý nghĩa `vo`, kèm ngày khảo sát; nếu không lấy được thì đánh dấu `[NEEDS_PRODUCT_DECISION]`/giả định rõ ràng thay vì trình bày như đã xác minh.

**[MAJOR-03] `ExploreViewModel.pullToRefresh()` bị bỏ sót → bật/tắt "Truyện nam" lệch với dữ liệu sau khi kéo làm mới.**
- `[SRC]` `ExploreViewModel.kt:158-190`: `pullToRefresh` gọi trực tiếp `repository.getStoriesByGenre(genre.slug, page = 1, completedOnly = true)` (`:168`) và `repository.getCompletedStories(page = 1)` (`:170`). `[SRC]` `ExploreScreen.kt:105`: `onRefresh = { viewModel.pullToRefresh() }`.
- Sau khi thêm default `maleOnly = true`, các lời gọi này vẫn biên dịch nhưng luôn gửi `maleOnly = true`. Kịch bản: user tắt chip (`maleOnly=false`) → kéo làm mới → danh sách quay về chỉ truyện nam trong khi chip vẫn hiển thị tắt.
- Plan liệt kê `refresh()` (bảng 5.1) nhưng `refresh()` (`:154-156`) chỉ gọi `loadFirstPage()`. Yêu cầu: thêm `pullToRefresh` vào Bước 5 và Test Contract/Verification (test `pullToRefresh forwards maleOnly`). Gợi ý: cân nhắc bỏ giá trị mặc định `maleOnly`/`completedOnly` ở `IStoryRepository` để compiler buộc mọi call site truyền giá trị tường minh.

**[MAJOR-04] Máy trạng thái bộ lọc của `SearchViewModel` chưa được đặc tả cho `maleOnly`, có đường đi làm reset `maleOnly` âm thầm.**
- `[SRC]` `SearchViewModel.kt:214-215` và `:230`: `hasAnyFilter` và nhánh gọi `browseByFilterOnly` chỉ xét `hasChapterFilter || completedIsNonDefault`. Nếu không cập nhật, `applyAllFilters(null, NONE, true, maleOnly=false)` không có keyword sẽ rơi vào nhánh `clearFilter()` (`:232-233`) → `SearchState()` (`:445`) → `maleOnly` bị đặt lại `true`, lựa chọn của user mất.
- `[SRC]` `SearchViewModel.kt:374-375,390-393`: `toggleCompletedFilter` tính `isFilterActive` không xét `maleOnly`. Kịch bản: user tắt "Truyện nam" và tắt "Full" → bật lại "Full" → `isFilterActive=false` → `clearFilter()` → `maleOnly` bị reset, kết quả bị xóa. Plan không liệt kê `toggleCompletedFilter` trong Bước 4.
- `toggleMaleFilter()` chỉ được mô tả qua tên test "toggles maleOnly and triggers browseByFilterOnly" (mục 8.1). Nếu đang có keyword hoặc genre, gọi `browseByFilterOnly` sẽ bỏ qua genre/keyword (`browseByFilterOnly` gọi `getCompletedStories`, `:316`). Cần đặc tả định tuyến giống `toggleCompletedFilter` (keyword → `search()`, genre → `browseByGenre`, còn lại → `browseByFilterOnly`/`clearFilter`).
- `[SRC]` `SearchScreen.kt:253,275,298`: các `InputChip` xóa nhanh gọi `viewModel.applyAllFilters(..., completedOnly = ...)` với 3 tham số; plan không nêu cách chuyển tiếp `state.maleOnly` tại các call site này (nếu `applyAllFilters` có default `maleOnly = true` thì xóa chip thể loại sẽ âm thầm bật lại lọc nam).
- `applyFilter` (`:157-195`) có trong bảng 5.1 nhưng Bước 4 không mô tả thay đổi.
- Yêu cầu: bảng chuyển trạng thái cho `maleOnly` qua `applyFilter`, `applyAllFilters`, `toggleCompletedFilter`, `toggleMaleFilter`, `clearAllFilters`, `clearFilter`, cùng test cho kịch bản "tắt nam + bật lại Full không reset maleOnly".

**[MAJOR-05] Lọc client-side `maleOnly` trong `applyLocalFilters` chưa có đặc tả và ảnh hưởng nguồn TTV/SST, mâu thuẫn với Out of Scope.**
- Plan mục 1.2 (dòng "KHÔNG thay đổi bộ lọc trên các nguồn truyện khác" và "KHÔNG làm xáo trộn tìm kiếm từ khóa") mâu thuẫn với Bước 4/TC-10: `applyLocalFilters` (`SearchViewModel.kt:344-366`) chạy trên kết quả gộp mọi nguồn trong `search()` (`:134`) và `browseByFilterOnly()` (`:317`). Với `maleOnly = true` mặc định, tìm từ khóa một truyện Ngôn Tình trên TTV/SST sẽ bị ẩn theo mặc định — thay đổi hành vi hiện tại cho user.
- Tập nhãn bị loại chưa được định nghĩa ("tag nữ tần/đam mỹ/bách hợp"). `[SRC]` fixture `app/src/test/resources/fixtures/wikidich/search_logged_in.html:473,501`: `.book-gender` của Wikidich có giá trị `Ngôn tình`, `Đam mỹ` (được `parseStoryList` đưa vào `genres`, `WikidichScraper.kt:582-588`). TTV/SST dùng tên thể loại riêng. Cần danh sách chuỗi chính xác, quy tắc so khớp (không phân biệt hoa thường, trim), và quy định nguồn nào bị áp dụng.
- Bất nhất giữa 2 màn: Khám Phá không qua `applyLocalFilters` nên TTV/SST không bị lọc, còn Tìm Kiếm (`browseByFilterOnly`) thì bị lọc, dù cùng một công tắc "Truyện nam".
- Ca biên: keyword + chọn thể loại "Ngôn Tình" (từ danh sách gộp của TTV/SST) + `maleOnly=true` → toàn bộ kết quả bị loại, không có thông báo giải thích.
- Yêu cầu: chốt phạm vi (chỉ áp dụng cho `sourceId == "wikidich"` hay mọi nguồn), cập nhật mục 1.2 cho thống nhất, mở rộng TC-10 với ca âm (truyện nam giữ lại, truyện `genres` rỗng giữ lại, truyện TTV/SST theo quyết định đã chốt).

**[MINOR-01] Override trong Kotlin không được khai báo giá trị mặc định.** Bảng 5.1 ghi `TangThuVienScraper`/`SsTruyenScraper` "nhận `maleOnly: Boolean = true` và `completedOnly: Boolean = true`". Kotlin báo lỗi "An overriding function is not allowed to specify default values for its parameters". Code hiện tại đúng mẫu không default ở override (`TangThuVienScraper.kt:385-390`, `StoryRepository.kt:194-199`). Sửa lại mô tả: default chỉ nằm ở interface `StorySource`/`IStoryRepository`.

**[MINOR-02] Test hiện có bị thay đổi assertion chưa được khai báo.** `WikidichScraperTest.kt:211-259` assert `qs=1` và `tc=&tf=0`; plan cần ghi rõ test này được viết lại/đổi tên (và lý do), theo Test Integrity (`.agents/rules/05-plan-review.md:21`).

**[MINOR-03] TC-09 không khớp code.** `StoryRepository.kt:242-251` nuốt lỗi từng nguồn và trả `emptyList()`, nên lỗi mạng thường không tới ViewModel; thông báo lỗi của Khám Phá là `"Không thể kết nối. Vui lòng kiểm tra mạng và thử lại."` (`ExploreViewModel.kt:108`), khác chuỗi trong TC-09. Cần chỉnh TC-09 theo hành vi thật (empty state) hoặc theo từng màn.

**[MINOR-04] Vị trí chip "Truyện nam" trên Khám Phá.** Hàng chip nằm trong nhánh `else` (`ExploreScreen.kt:126-129`) và trong `if (state.genres.isNotEmpty())`: chip sẽ biến mất khi đang tải, khi lỗi và khi tải thể loại thất bại. Plan nên nêu rõ chip "Truyện nam" hiển thị độc lập với `state.genres` và ở trạng thái lỗi/rỗng (để user có thể tắt lọc khi kết quả rỗng).

#### Mental Simulation Matrix (S1–S6 + Domain)

| Kịch bản | Kết quả | Loại bằng chứng | Chi tiết |
| :--- | :--- | :--- | :--- |
| S1 Cold-start & Build | PASS (kèm MINOR-01) | `[CMD]`, `[SRC]` | Baseline `compileDebugKotlin` và `ktlintCheck` Exit 0 (evidence.md). Không thêm dependency. Negative probe ktlint có trong mục 8.3. Default ở override (MINOR-01) sẽ bị compiler chặn ngay. |
| S2 Lifecycle & Teardown | PASS | `[SRC]` | `loadFirstPage` hủy `loadJob`/`loadMoreJob` (`ExploreViewModel.kt:82-83`); `searchJob?.cancel()` (`SearchViewModel.kt:265,303,408`). `toggleMaleOnly` đi qua `loadFirstPage` nên bấm nhanh liên tiếp chỉ giữ job cuối. Test dùng `MockWebServer` có `close()` (`WikidichScraperTest.kt:39-42`). |
| S3 Database & Temporal | N/A | `[SRC]` | Không đổi schema: `AppDatabase.kt:22` `version = 9`. `maleOnly` chỉ ở UI state, không ghi DataStore — mất khi process bị kill và quay về mặc định `true`, phù hợp quyết định sản phẩm. |
| S4 Cross-Task State Flow | FAIL | `[SRC]` | MAJOR-03 (`pullToRefresh`), MAJOR-04 (state machine Search, call site `SearchScreen.kt:253,275,298`). |
| S5 Security & Privacy | PASS | `[SRC]`, `[ADV]` | Không đụng `cookie/`, `auth/`, Manifest. Tham số URL lấy từ hằng số và `GENRE_QUERY_MAP` (`WikidichScraper.kt:340`), không nhận input tự do. Trang đăng nhập được chặn bằng `checkLoginRequired` (`:556-565`) trước khi parse. |
| S6 External Resilience | FAIL | `[SRC]`, `[URL]` | MAJOR-01 (throttle `/tim-kiem`), MAJOR-02 (ngữ nghĩa `so=4&vo=1` chưa xác minh). |
| Domain (gộp đa nguồn / UI tiếng Việt) | FAIL | `[SRC]` | MAJOR-05 (lọc client-side lan sang TTV/SST, bất nhất giữa 2 màn). Chuỗi UI mới đều là tiếng Việt ("Truyện nam", "Cả truyện nữ/khác", "Bật lại lọc truyện nam"). |

#### Adversarial Timelines

```
Cơ chế: Bật/tắt "Truyện nam" + kéo làm mới trên Khám Phá (S4)
T1: User bấm chip → toggleMaleOnly() → maleOnly=false, loadFirstPage(maleOnly=false) | T2: User kéo làm mới → pullToRefresh() gọi getCompletedStories(page = 1) với default maleOnly=true (ExploreViewModel.kt:170) | T3: state.stories bị thay bằng danh sách chỉ truyện nam, chip vẫn tắt
Kết cục: UI và dữ liệu lệch nhau → LỖI (MAJOR-03)
```

```
Cơ chế: Kết hợp tắt "Truyện nam" và "Full" trên Tìm Kiếm (S4)
T1: applyAllFilters(null, NONE, completedOnly=false, maleOnly=false) → browseByFilterOnly | T2: User bấm toggleCompletedFilter() → completedOnly=true, isFilterActive tính theo SearchViewModel.kt:374-375 = false | T3: nhánh else → clearFilter() (:393) → SearchState() → maleOnly=true, results rỗng
Kết cục: lựa chọn "tắt Truyện nam" mất âm thầm → LỖI (MAJOR-04)
```

```
Cơ chế: Throttle /tim-kiem khi duyệt liên tiếp (S6, đồng thời theo thời gian)
T1: Mở Khám Phá → getCompletedStories → /tim-kiem?qs=2&gender=...&status=...&so=4&vo=1 | T2: Trong vài giây user chọn "Hệ Thống" → /tim-kiem?qs=2&gender=...&tag=... ; server giữ ~12s (lessons-learned.md:112) | T3: Repository chờ tối đa 25s (StoryRepository.kt:115), UI hiển thị shimmer ~12s; mỗi lần bật/tắt chip lặp lại
Kết cục: không mất dữ liệu nhưng độ trễ tăng mạnh, chưa có control → LỖI (MAJOR-01)
```

```
Cơ chế: Phiên Wikidich hết hạn giữa lúc bật/tắt chip (S5, TOCTOU)
T1: User bấm chip, request /tim-kiem?qs=2... được gửi | T2: Cookie express.sid hết hạn, server trả trang "Đăng nhập để xem nội dung" | T3: checkLoginRequired (WikidichScraper.kt:556-565) xóa cache, ném LoginRequiredException → StoryRepository.kt:244-246 markLoginRequired, các nguồn khác vẫn trả kết quả, banner đăng nhập hiện (ExploreScreen.kt:154-155)
Kết cục: không parse nhầm trang đăng nhập thành danh sách → AN TOÀN
```

Trục đối kháng: (1) Đồng thời — bấm chip nhanh liên tiếp: job trước bị hủy, AN TOÀN. (2) TOCTOU — timeline phiên hết hạn ở trên, AN TOÀN. (3) Thất bại giữa chừng — lỗi một nguồn bị nuốt thành `emptyList()` (`StoryRepository.kt:242-251`), xem MINOR-03. (4) Vòng đời Android — xoay màn hình giữ ViewModel; process bị kill reset `maleOnly=true` (mặc định chấp nhận được).

#### Commands Executed

Trích từ `evidence.md` (reviewer không có shell):

- `### [CMD] git status --short` — `Exit: 0` — `M app/build.gradle.kts`, `M build.gradle.kts`, `M docs/architecture.md`, `M docs/changelog.md`, `M docs/progress.md`, `?? docs/plans/2026-10-27-wikidich-male-tag-and-newest-sort-implementation-plan.md`. Ghi chú: working tree đã có thay đổi chưa commit ở `app/build.gradle.kts`/`build.gradle.kts` ngoài phạm vi plan — người triển khai cần tách bạch khi commit.
- `### [CMD] cmd.exe /d /c gradlew.bat compileDebugKotlin` — `Exit: 0` — `BUILD SUCCESSFUL in 821ms`.
- `### [CMD] cmd.exe /d /c gradlew.bat ktlintCheck` — `Exit: 0` — `> Task :app:ktlintMainSourceSetCheck UP-TO-DATE`, `> Task :app:ktlintTestSourceSetCheck UP-TO-DATE`, `BUILD SUCCESSFUL in 722ms`.
- `### [CMD] cmd.exe /d /c gradlew.bat testDebugUnitTest` — `Exit: 0` — `> Task :app:testDebugUnitTest UP-TO-DATE`, `BUILD SUCCESSFUL in 990ms`.
- `### [CMD] node --test .agents/scripts/code-freeze-guard.test.js` — `Exit: 0` — `ℹ pass 36`, `ℹ fail 0`.
- `### [CMD] node --test .agents/scripts/plan-approval.test.js` — `Exit: 0` — `ℹ pass 13`, `ℹ fail 0`.
- UNVERIFIED (lệnh đề xuất cho Planner): `curl.exe -q -s -o NUL -w "%{time_total}" "https://wikicv.org/tim-kiem?qs=2&status=5794f03dd7ced228f4419191&so=4&vo=1"` chạy 2 lần liên tiếp để đo throttle (MAJOR-01).

#### Kết luận

Plan có hướng đi hợp lý (tách `maleOnly` khỏi danh sách thể loại, gom tham số URL thành hằng số, URL trong TC-01…TC-05 nhất quán với sketch mục 5.2), nhưng còn 5 lỗi `[MAJOR]` và các kịch bản S4, S6 bị FAIL. Planner cần xử lý MAJOR-01…MAJOR-05 và các MINOR trong Revision 2.

**Kết luận**: [CHANGES_REQUESTED]

### Audit Run 2

- **Reviewer**: Claude Code CLI (claude-opus-5-5)
- **Ngày**: 2026-10-27
- **Đối tượng**: Technical Plan Revision 3

Audit Run 1 kết luận CHANGES_REQUESTED. Danh sách lỗi của Run 1 chỉ được dùng làm danh sách cần kiểm lại; mọi trạng thái dưới đây được xác định lại trên code thật.

#### Kiểm Chứng Lỗi Cũ

| Mã lỗi | Trạng thái | Bằng chứng trên code thật / Ghi chú |
| :--- | :--- | :--- |
| MAJOR-01 (throttle `/tim-kiem`) | ĐÃ SỬA (rủi ro được ghi nhận, số đo còn UNVERIFIED) | Plan giữ `/bang-xep-hang?so=5$startParam` cho nhánh `!maleOnly`, khớp URL hiện tại `[SRC] WikidichScraper.kt:319-320` và fallback `:327`. Mục 9 đã thêm dòng rủi ro + 4 control. Control 4 (cache): `[SRC] BaseScraper.kt:57` (map `cache`), `:123-124` (đọc cache theo `CACHE_TTL_MS`), `:374` (`CACHE_TTL_MS = 10 * 60 * 1000L`) — plan trích `BaseScraper.kt:64`, lệch dòng (xem MINOR-07). Control 2: `[SRC] StoryRepository.kt:115,121-122` timeout 25s và Wikidich chỉ tải 1 trang thật mỗi logical page. Lưu ý: vì `maleOnly = true` là mặc định, lần mở Khám Phá đầu tiên giờ đi vào `/tim-kiem` (trước đây là `/bang-xep-hang`); rủi ro này đã được chấp nhận theo quyết định sản phẩm mục 3.3. Lệnh đo vẫn chưa có trong evidence.md → UNVERIFIED (không chặn, thuộc S6). |
| MAJOR-02 (ngữ nghĩa `so=4&vo=1`) | SỬA MỘT PHẦN | Plan bổ sung `[USER_EVIDENCE]` gồm 3 URL người dùng cung cấp từ phiên đã đăng nhập; đây là nguồn đầu vào sản phẩm chấp nhận được. Fixture `[SRC] search_logged_in.html:737-738` chỉ cho thấy `so=4` = "Mới nhất" trên `/bang-xep-hang`, chưa phải trên `/tim-kiem` và không nói gì về `vo=1`. Ảnh chụp `media_1793126178610_8bd36114.png` không có trong repo (Glob không tìm thấy). Plan vẫn trích `[SRC] WikidichScraper.kt:87,105,110` làm bằng chứng cho `so=4&vo=1`, nhưng các dòng này chỉ chứa `gender=`/`tag=`/`official=`, không có `so`/`vo`. Phần còn thiếu chuyển thành MINOR-07 (gắn nhãn bằng chứng sai), không chặn plan. |
| MAJOR-03 (`ExploreViewModel.pullToRefresh`) | ĐÃ SỬA | `[SRC] ExploreViewModel.kt:158-190`: `pullToRefresh` gọi repo tại `:168,:170`; sketch mục 5.2 giữ nguyên `loadJob?.cancel()`/`loadMoreJob?.cancel()` (`:159-160`), `canLoadMore = stories.isNotEmpty()` (`:177`), chuỗi lỗi `:185`, và thêm `maleOnly = currentMaleOnly`. Bước 5, TC-06 và test `pullToRefresh preserves maleOnly filter state` đã có. Lỗi cùng loại vẫn còn ở `SearchViewModel` → xem MAJOR-07. |
| MAJOR-04 (máy trạng thái Search) | SỬA MỘT PHẦN | Đã sửa: `toggleCompletedFilter` tính `isFilterActive` có xét `!maleOnly` (so với `[SRC] SearchViewModel.kt:374-375` hiện tại); `applyAllFilters` có nhánh `hasAnyFilter` gồm `maleIsNonDefault` (so với `:215,:230`); 3 call site `InputChip` tại `[SRC] SearchScreen.kt:253,275,298` được truyền `maleOnly = state.maleOnly`. Còn thiếu: (1) call site thứ 4 có sẵn `[SRC] SearchScreen.kt:593-595` cùng chữ ký `FilterBottomSheet.onApply` `:616`, `:752` (xem MAJOR-06); (2) các hàm gọi repository trong `SearchViewModel` không chuyển tiếp `maleOnly` (xem MAJOR-07). |
| MAJOR-05 (lọc client-side lan sang TTV/SST) | ĐÃ SỬA | Sketch `applyLocalFilters` thêm điều kiện `story.sourceId != "wikidich"` → bỏ qua lọc. Mục 1.2 đã cập nhật cho thống nhất. Tập `NON_MALE_WIKIDICH_GENDERS` khớp nhãn thật của fixture: `[SRC] search_logged_in.html:473` "Ngôn tình", `:501` "Đam mỹ", `:831` "Bách hợp"; nhãn đi vào `Story.genres` qua `[SRC] WikidichScraper.kt:582-588`. TC-10 có ca dương/ca âm cho TTV/SST. |
| MINOR-01 (default ở override) | ĐÃ SỬA | Plan mục 3.1.4 và Bước 1 ghi rõ không default ở override, khớp mẫu hiện tại `[SRC] TangThuVienScraper.kt:385`, `StoryRepository.kt:194-199`, `LacaTruyenApiTest.kt:97-102`. |
| MINOR-02 (test cũ đổi assertion) | ĐÃ SỬA | Plan khai báo viết lại test `WikidichScraperTest.kt:211` (`getStoriesByGenre uses filter form qs=1 params...`) theo Loại C tại mục 8.1. Tên test khớp code thật `[SRC] WikidichScraperTest.kt:211`. |
| MINOR-03 (TC-09 lệch code) | SỬA MỘT PHẦN | TC-09 đã nêu repo nuốt lỗi từng nguồn, nhưng vẫn ghi "nếu toàn bộ các nguồn cùng lỗi ... UI hiển thị `Không thể kết nối...`". Theo `[SRC] StoryRepository.kt:168-178,242-251`, mọi nguồn lỗi đều trả `emptyList()` nên repo trả danh sách rỗng, UI hiển thị trạng thái rỗng chứ không hiện chuỗi lỗi; chuỗi lỗi chỉ xuất hiện khi repo ném exception (`ExploreViewModel.kt:108`, `SearchViewModel.kt:151`). Không chặn. |
| MINOR-04 (chip Khám Phá phụ thuộc `state.genres`) | ĐÃ SỬA (có vấn đề bố cục mới) | Plan đặt chip ngoài `when` (`[SRC] ExploreScreen.kt:111-126`) và ngoài `if (state.genres.isNotEmpty())` (`:129`). Vấn đề bố cục phát sinh → MINOR-08. |

#### Đối Chiếu Các File `[MODIFY]` Với Code Thật

| File | Khớp / Lệch |
| :--- | :--- |
| `StorySource.kt` / `IStoryRepository.kt` | Khớp: `getCompletedStories(page: Int = 1)` (`StorySource.kt:51`, `IStoryRepository.kt:18`), `getStoriesByGenre` (`StorySource.kt:54`, `IStoryRepository.kt:20`). |
| `WikidichScraper.kt` | Khớp: `SEARCH_PAGE_SIZE = 20` (`:54`), `GENRE_QUERY_MAP` gồm 4 tag `gender=` (`:84-87`), `getCompletedStories` (`:317-331`), `getStoriesByGenre` với `qs=1...&tc=&tf=0&m=&y=&q=` (`:333-356`), `getGenreList` gồm 4 tag (`:366-369`), `checkLoginRequired` (`:556-565`). Sketch dùng đúng `fetchDocumentWithRetry`, `checkLoginRequired`, `parseStoryList`. |
| `TangThuVienScraper.kt` / `SsTruyenScraper.kt` | Khớp: override tại `TangThuVienScraper.kt:376,385`, `SsTruyenScraper.kt:713,724`. |
| `StoryRepository.kt` | Khớp: `source.getCompletedStories(p)` (`:147`), `source.getStoriesByGenre(genreSlug, p, 0, completedOnly)` (`:218`). Không có cache tầng repo theo tham số nên thêm `maleOnly` không gây trộn kết quả cache. |
| `SearchViewModel.kt` | Lệch: plan không liệt kê `browseByGenre` (`:261-297`), `browseByFilterOnly` (`:302-339`), `loadMoreFilterResults` (`:398-441`) — cả 3 gọi repository (MAJOR-07). Sketch `clearAllFilters` bỏ điều kiện `&& hadSearched` có trong code hiện tại `:251` (MINOR-05). |
| `SearchScreen.kt` | Lệch: plan nói "4 call sites" nhưng code đã có 4 call site `applyAllFilters` (`:253,:275,:298,:595`) và plan thêm call site thứ 5 (chip mới). `FilterBottomSheet` (`:610-618`) có `onApply: (Genre?, ChapterCountFilter, Boolean) -> Unit`, local state `:620-622`, `hasAnyFilter` `:625-628`, nút áp dụng `:752` — plan không đặc tả cách truyền `maleOnly` qua các điểm này (MAJOR-06). `activeFilterCount` `:182-187`, `hasActiveFilters` `:230-233` khớp mô tả. |
| `ExploreViewModel.kt` | Khớp: `ExploreState` `:33-43`, `loadFirstPage` `:81-113`, `loadMore` `:115-152`, `pullToRefresh` `:158-190`. |
| `ExploreScreen.kt` | Khớp vị trí `PullToRefreshBox` `:103-110`, `when` `:111`, hàng chip `:127-151`. Lệch bố cục: xem MINOR-08. |
| `WikidichScraperTest.kt` | Khớp: test cần viết lại tại `:211`, gọi `getStoriesByGenre` tại `:239,:248`. |
| `SearchViewModelTest.kt` / `ExploreViewModelTest.kt` | Khớp: stub `getCompletedStories(1)` (`ExploreViewModelTest.kt:48` …), `getStoriesByGenre(genre.slug, 1, 0, true)` (`SearchViewModelTest.kt:211`). Các stub này dựa vào default `maleOnly = true` của interface; nếu bỏ default theo khuyến nghị ở MAJOR-07 thì phải cập nhật — plan đã liệt kê 2 file là `[MODIFY]`. |
| `LacaTruyenApiTest.kt` | Khớp: `TestStorySource` `:95-102`. |

#### Phát Hiện Mới (Fresh Findings & Regression Check)

**[MAJOR-06] Mục "Đối tượng độc giả" trong `FilterBottomSheet` không có đường dữ liệu tới ViewModel; lựa chọn trong sheet sẽ bị bỏ qua âm thầm.**
- `[SRC] SearchScreen.kt:616`: `onApply: (Genre?, ChapterCountFilter, Boolean) -> Unit` (3 tham số). `:620-622`: chỉ có `localGenre`, `localChapterFilter`, `localCompleted`. `:752`: `onApply(localGenre, localChapterFilter, localCompleted)`. `:593-595`: `viewModel.applyAllFilters(genre, chapterFilter, completedOnly)`.
- Plan (Bước 4, bảng 5.1) chỉ nói "Bổ sung section độc lập cho Đối tượng độc giả trong `FilterBottomSheet`" và "4 call sites trên `InputChip`", không nêu: tham số `maleOnly` mới cho `FilterBottomSheet`, `var localMale by remember { mutableStateOf(maleOnly) }`, đổi chữ ký `onApply` thành 4 tham số, sửa call site `:595`, và thêm `!localMale` vào `hasAnyFilter` `:625-628`.
- Vì sketch đặt default `maleOnly: Boolean = _state.value.maleOnly` cho `applyAllFilters`, call site `:595` vẫn biên dịch được khi không sửa, và luôn gửi giá trị `maleOnly` cũ. Kịch bản: user mở sheet, tắt "Truyện nam", bấm "Áp dụng bộ lọc" → `maleOnly` không đổi; compiler, ktlint và test ViewModel đều không phát hiện.
- Yêu cầu: bổ sung sketch cho `FilterBottomSheet` (tham số, local state, `hasAnyFilter`, chữ ký `onApply`, call site `:593-595`), sửa số lượng call site cho đúng, và bỏ default của tham số `maleOnly` trong `applyAllFilters` để compiler buộc mọi call site truyền giá trị tường minh.

**[MAJOR-07] `SearchViewModel` không chuyển tiếp `maleOnly` tới repository ở 3 hàm; bật/tắt "Truyện nam" trên Tìm Kiếm không đổi dữ liệu Wikidich.**
- `[SRC] SearchViewModel.kt:271-276` (`browseByGenre`): `repository.getStoriesByGenre(genre.slug, page = 1, minChapters = ..., completedOnly = _state.value.completedOnly)`.
- `[SRC] SearchViewModel.kt:316` (`browseByFilterOnly`): `repository.getCompletedStories(page = 1)`.
- `[SRC] SearchViewModel.kt:414-419` (`loadMoreFilterResults`): `repository.getStoriesByGenre(genre.slug, page = nextPage, minChapters = minChapters, completedOnly = current.completedOnly)`.
- Plan Bước 4 và bảng 5.1 không liệt kê 3 hàm này. Sau khi thêm default `maleOnly = true` vào `IStoryRepository`, cả 3 lời gọi vẫn biên dịch và luôn gửi `maleOnly = true`. Hệ quả: `toggleMaleFilter()` (không keyword) → `browseByFilterOnly` → `getCompletedStories(page = 1)` → Wikidich vẫn đi nhánh `/tim-kiem?...gender=...`; `applyLocalFilters` với `!maleOnly` không thêm lại được truyện đã bị server loại. Tương tự khi có thể loại (`browseByGenre`) và khi cuộn tải trang 2 (`loadMoreFilterResults`).
- Wiring probe của chính plan (mục 8.3) `coVerify { repository.getCompletedStories(page = 1, maleOnly = false) }` sẽ đỏ với thiết kế hiện tại — mâu thuẫn nội tại. Đây là cùng loại lỗi với MAJOR-03 của Run 1, chỉ được sửa ở `ExploreViewModel`.
- Yêu cầu: thêm 3 hàm vào Bước 4 với `maleOnly = _state.value.maleOnly` (và `current.maleOnly` trong `loadMoreFilterResults`); thêm test `browseByGenre/loadMoreFilterResults forward maleOnly`. Khuyến nghị lại: bỏ default `maleOnly` (và cân nhắc `completedOnly`) ở `IStoryRepository` để compiler liệt kê mọi call site (6 call site trong `ExploreViewModel.kt:91,93,127,129,168,170` và 3 trong `SearchViewModel.kt`).

**[MINOR-05] Sketch `clearAllFilters` đổi hành vi, mâu thuẫn với mô tả "chỉ re-trigger khi `hasSearched`".** Code hiện tại `[SRC] SearchViewModel.kt:251`: `if (currentQuery.trim().isNotBlank() && hadSearched) search()`. Sketch bỏ `&& hadSearched`, nên khi user mới gõ chữ (chưa tìm) rồi bấm "Xóa tất cả" sẽ kích hoạt tìm kiếm ngoài ý muốn. Giữ nguyên điều kiện cũ.

**[MINOR-06] Mâu thuẫn nội tại về `so=4&vo=1` và TC-04.** Mục 1.1.3 gạch đầu dòng 1 ("toàn bộ URL ... duyệt truyện không theo thể loại ... luôn gắn kèm `&so=4&vo=1`, tuyệt đối loại bỏ các URL thiếu") và dòng rủi ro thứ 2 mục 9 ("Không có bất kỳ nhánh nào tạo URL thiếu `so=4&vo=1`") trái với nhánh nhanh `/bang-xep-hang?so=5` ở chính mục 1.1.3 gạch 3 và sketch mục 5.2. TC-04 ghi "tuyệt đối không chạm endpoint `/tim-kiem`" nhưng sketch có fallback sang `/tim-kiem` khi `/bang-xep-hang` rỗng; TC-04 cần nêu điều kiện phản hồi không rỗng và thêm 1 ca cho fallback.

**[MINOR-07] Trích dẫn bằng chứng lệch.** (a) `[SRC] WikidichScraper.kt:87,105,110` được dùng làm bằng chứng cho `so=4&vo=1` nhưng không chứa tham số này. (b) `BaseScraper.kt:64` cho `CACHE_TTL_MS`; thực tế tại `:374` (map `cache` tại `:57`). (c) Helper `hasAnyNonDefaultFilter` được khai báo trong sketch nhưng không được dùng ở `applyAllFilters`/`toggleCompletedFilter`/`toggleMaleFilter` (mỗi hàm tự tính lại). (d) Các cụm "chính xác 100%", "tối ưu tuyệt đối", "ngăn ngừa 100%" không có bằng chứng tương ứng.

**[MINOR-08] Chip "Truyện nam" trên Khám Phá có thể chồng lên lưới truyện.** `[SRC] ExploreScreen.kt:103-111`: nội dung của `PullToRefreshBox` là `BoxScope`; `when` là con trực tiếp. Đặt `Row` chứa chip làm anh em với `when` (như sketch) thì hai khối xếp chồng tại cùng vị trí. Cần bọc `Row` + `when` trong một `Column` (và cho phần `when` `Modifier.weight(1f)`), hoặc nêu rõ vị trí trong sketch.

#### Mental Simulation Matrix (S1–S6 + Domain)

| Kịch bản | Kết quả | Loại bằng chứng | Chi tiết |
| :--- | :--- | :--- | :--- |
| S1 Cold-start & Build | PASS | `[CMD]`, `[SRC]` | Baseline `compileDebugKotlin` và `ktlintCheck` Exit 0 (evidence.md). Không thêm dependency. Override không default (MINOR-01 đã sửa). Lưu ý: chính các default argument làm MAJOR-06/07 lọt qua compiler. |
| S2 Lifecycle & Teardown | PASS | `[SRC]` | `loadFirstPage`/`pullToRefresh` hủy `loadJob`/`loadMoreJob` (`ExploreViewModel.kt:82-83,159-160`); `toggleMaleOnly` đi qua `loadFirstPage`. `searchJob?.cancel()` tại `SearchViewModel.kt:120,265,303,408` nên bấm chip liên tiếp chỉ giữ job cuối. |
| S3 Database & Temporal | N/A | `[SRC]` | Không đổi schema: `AppDatabase.kt:22` `version = 9`. `maleOnly` chỉ nằm trong UI state, không ghi Room/DataStore. |
| S4 Cross-Task State Flow | FAIL | `[SRC]` | MAJOR-06 (`SearchScreen.kt:593-595,616,752`), MAJOR-07 (`SearchViewModel.kt:271-276,316,414-419`). |
| S5 Security & Privacy | PASS | `[SRC]` | Không đụng cookie/auth/Manifest. Tham số URL chỉ lấy từ hằng số và `GENRE_QUERY_MAP` (`WikidichScraper.kt:340`), slug lạ trả `emptyList()` trước khi gọi mạng. Trang chặn đăng nhập bị bắt bởi `checkLoginRequired` (`:556-565`) ở mọi nhánh của sketch, kể cả fallback. |
| S6 External Resilience | UNVERIFIED (không chặn) | `[SRC]` | Control định tuyến và timeout có thật (`WikidichScraper.kt:320`, `StoryRepository.kt:115`, `BaseScraper.kt:123-124,374`). Độ trễ thực tế của `/tim-kiem?qs=2&gender=...&status=...&so=4&vo=1` khi gọi liên tiếp chưa được đo; ngữ nghĩa `vo=1` chỉ dựa vào URL người dùng cung cấp. |
| Domain (gộp đa nguồn / UI tiếng Việt) | PASS | `[SRC]` | Lọc client-side chỉ áp cho `sourceId == "wikidich"`; nhãn khớp fixture (`search_logged_in.html:473,501,831`). Repo gọi `getStoriesByGenre` cho mọi nguồn với cùng slug (`StoryRepository.kt:218`); slug `ngon-tinh`/`dam-my` (nếu còn trong danh sách gộp từ TTV/SST) sẽ khiến Wikidich trả rỗng tại `WikidichScraper.kt:340`, không gây lỗi. Chuỗi UI mới đều tiếng Việt. |

#### Adversarial Timelines

```
Cơ chế: Tắt "Truyện nam" trong FilterBottomSheet (S4)
T1: User mở sheet, bấm tắt chip "Truyện nam" (giả sử plan chỉ thêm UI theo mô tả hiện tại) | T2: Bấm "Áp dụng bộ lọc" → onApply(localGenre, localChapterFilter, localCompleted) (SearchScreen.kt:752) → applyAllFilters(genre, chapterFilter, completedOnly) (:595), maleOnly lấy default = _state.value.maleOnly = true | T3: State giữ maleOnly=true, chip trong sheet lần sau mở lại vẫn BẬT
Kết cục: thao tác của user bị bỏ qua âm thầm → LỖI (MAJOR-06)
```

```
Cơ chế: Tắt "Truyện nam" qua toggleMaleFilter khi đang duyệt không keyword (S4, bất đồng bộ)
T1: toggleMaleFilter() → maleOnly=false, isFilterActive=true → browseByFilterOnly() hủy searchJob cũ (SearchViewModel.kt:303) | T2: Job mới gọi repository.getCompletedStories(page = 1) (:316) → default maleOnly=true → Wikidich /tim-kiem?qs=2&gender=...&status=...&so=4&vo=1 | T3: applyLocalFilters với maleOnly=false không lọc thêm, kết quả vẫn chỉ có truyện nam; chip hiển thị TẮT
Kết cục: UI và dữ liệu lệch → LỖI (MAJOR-07)
```

```
Cơ chế: Cuộn tải trang 2 khi đã tắt "Truyện nam" + chọn thể loại (S4)
T1: browseByGenre (đã sửa) trả trang 1 gồm cả truyện nữ | T2: loadMoreFilterResults() (:398) gọi getStoriesByGenre(..., completedOnly = current.completedOnly) (:414-419), maleOnly default true | T3: trang 2 chỉ có truyện nam, ghép vào danh sách
Kết cục: danh sách trộn 2 chế độ lọc → LỖI (MAJOR-07)
```

```
Cơ chế: Process bị kill khi maleOnly=false (S3, temporal)
T1: User tắt "Truyện nam" trên Khám Phá | T2: App vào nền, hệ thống kill process | T3: Mở lại → ExploreState() mới với maleOnly=true (không lưu SavedStateHandle/DataStore), init gọi loadFirstPage (ExploreViewModel.kt:58-61) với maleOnly=true, chip hiển thị BẬT
Kết cục: UI và dữ liệu nhất quán với mặc định sản phẩm, không mất dữ liệu → AN TOÀN
```

```
Cơ chế: Phiên Wikidich hết hạn giữa lúc bật/tắt chip (S5, TOCTOU)
T1: toggleMaleOnly → /tim-kiem?qs=2&gender=... gửi đi | T2: Cookie hết hạn, server trả trang "Đăng nhập để xem nội dung" | T3: checkLoginRequired (WikidichScraper.kt:556-565) xóa cache, ném LoginRequiredException → StoryRepository.kt:170-172 markLoginRequired, nguồn khác vẫn trả kết quả; nhánh fallback của sketch cũng gọi checkLoginRequired trước parse
Kết cục: không parse nhầm trang đăng nhập → AN TOÀN
```

Trục đối kháng: (1) Đồng thời — bấm chip nhanh liên tiếp: job cũ bị hủy (S2), AN TOÀN. (2) TOCTOU — phiên hết hạn, AN TOÀN. (3) Thất bại giữa chừng — một nguồn lỗi bị nuốt thành `emptyList()` (`StoryRepository.kt:168-178`), xem MINOR-03. (4) Cache 10 phút — URL nhánh nam và nhánh không nam khác nhau nên khóa cache (`BaseScraper.kt:123`) không trộn kết quả hai chế độ, AN TOÀN.

#### Commands Executed

Trích từ `evidence.md` (reviewer không có shell):

- `### [CMD] git status --short` — `Exit: 0` — `M app/build.gradle.kts`, `M build.gradle.kts`, `M docs/architecture.md`, `M docs/changelog.md`, `M docs/progress.md`, `?? docs/plans/2026-10-27-wikidich-male-tag-and-newest-sort-implementation-plan.md`.
- `### [CMD] git diff --stat` — `Exit: 0` — `app/build.gradle.kts | 4 ++--`, `build.gradle.kts | 2 +-`, `5 files changed, 16 insertions(+), 7 deletions(-)`. Ghi chú: thay đổi chưa commit ở 2 file build nằm ngoài phạm vi plan (mục 1.2 cam kết không đụng `app/build.gradle.kts`); người triển khai cần tách bạch khi commit.
- `### [CMD] cmd.exe /d /c gradlew.bat compileDebugKotlin` — `Exit: 0` — `BUILD SUCCESSFUL in 940ms`.
- `### [CMD] cmd.exe /d /c gradlew.bat ktlintCheck` — `Exit: 0` — `> Task :app:ktlintMainSourceSetCheck UP-TO-DATE`, `BUILD SUCCESSFUL in 641ms`.
- `### [CMD] cmd.exe /d /c gradlew.bat testDebugUnitTest` — `Exit: 0` — `> Task :app:testDebugUnitTest UP-TO-DATE`, `BUILD SUCCESSFUL in 954ms`.
- `### [CMD] node --test .agents/scripts/code-freeze-guard.test.js` — `Exit: 0` — `ℹ pass 36`, `ℹ fail 0`.
- `### [CMD] node --test .agents/scripts/plan-approval.test.js` — `Exit: 0` — `ℹ pass 13`, `ℹ fail 0`.
- `### [CMD] node --test .agents/scripts/review-run.test.js` — `Exit: 0` — `ℹ pass 10`, `ℹ fail 0`.
- UNVERIFIED (lệnh đề xuất cho Planner/User, cần cookie phiên đăng nhập): `curl.exe -q -s -o NUL -w "%{time_total}" "https://wikicv.org/tim-kiem?qs=2&gender=5794f03dd7ced228f4419196&status=5794f03dd7ced228f4419191&so=4&vo=1"` chạy 2 lần liên tiếp để đo throttle (S6).

#### Kết luận

Revision 3 đã xử lý MAJOR-01, MAJOR-03, MAJOR-05 và phần lớn MAJOR-04 của Run 1. Còn 2 lỗi `[MAJOR]` mới cùng loại "call site không chuyển tiếp `maleOnly` nhưng vẫn biên dịch nhờ default argument": MAJOR-06 (`FilterBottomSheet.onApply`, `SearchScreen.kt:593-595,616,752`) và MAJOR-07 (`SearchViewModel.browseByGenre`/`browseByFilterOnly`/`loadMoreFilterResults`). S4 FAIL. Planner cần bổ sung 2 điểm này (khuyến nghị bỏ default `maleOnly` ở `IStoryRepository` và `applyAllFilters` để compiler bắt mọi call site) và xử lý các MINOR-05…MINOR-08 trong Revision 4.

**Kết luận**: [CHANGES_REQUESTED]

### Audit Run 3

- **Reviewer**: Claude Code CLI (claude-opus-5-5)
- **Ngày**: 2026-10-27
- **Đối tượng**: Technical Plan Revision 4

Audit Run 1 và Audit Run 2 đều kết luận CHANGES_REQUESTED. Danh sách lỗi của hai lượt đó chỉ dùng làm danh sách cần kiểm lại; mọi trạng thái dưới đây được xác định lại trên code thật.

#### Kiểm Chứng Lỗi Cũ

| Mã lỗi | Trạng thái | Bằng chứng trên code thật / Ghi chú |
| :--- | :--- | :--- |
| MAJOR-01 (throttle `/tim-kiem`) | ĐÃ SỬA | Nhánh `!maleOnly` giữ `/bang-xep-hang?so=5$startParam`, khớp code hiện tại `[SRC] WikidichScraper.kt:319-320`, fallback `:327`. Control timeout `[SRC] StoryRepository.kt:115`, Wikidich 1 trang thật mỗi logical page `:121-122`. Control cache `[SRC] BaseScraper.kt:57` (map `cache`), `:124` (so `CACHE_TTL_MS`), `:374` (`CACHE_TTL_MS = 10 * 60 * 1000L`) — trích dẫn plan nay khớp. Số đo độ trễ thực vẫn chưa có (S6). |
| MAJOR-02 (ngữ nghĩa `so=4&vo=1`) | ĐÃ SỬA (dựa trên `[USER_EVIDENCE]`) | Mục 3.1.1 nay chỉ dùng `WikidichScraper.kt:87,105,110` để chứng minh ID `gender=`/`tag=`/`official=` — khớp `[SRC] WikidichScraper.kt:87,105,110`. Ngữ nghĩa `so=4&vo=1` chỉ dựa vào 3 URL người dùng cung cấp từ phiên đã đăng nhập; reviewer không tự xác minh được (trang ẩn danh bị chặn đăng nhập, xác nhận ở Run 1). Plan ghi rõ điều này, không trình bày như đã tự kiểm chứng. |
| MAJOR-03 (`ExploreViewModel.pullToRefresh`) | ĐÃ SỬA | Sketch khớp `[SRC] ExploreViewModel.kt:158-190` (cancel `:159-160`, `canLoadMore` `:177`, chuỗi lỗi `:185`). Bỏ default `maleOnly` ở `IStoryRepository` (`[SRC] IStoryRepository.kt:18,20-25` hiện có default) khiến 6 call site `ExploreViewModel.kt:91,93,127,129,168,170` không biên dịch nếu thiếu `maleOnly`. |
| MAJOR-04 (máy trạng thái Search) | SỬA MỘT PHẦN | Đã sửa trong ViewModel: `hasAnyNonDefaultFilter` dùng ở `applyAllFilters`/`toggleCompletedFilter`/`toggleMaleFilter`, thay cho `[SRC] SearchViewModel.kt:214-215,230` và `:374-375`. Còn thiếu ở tầng UI: `hasActiveFilters` (`SearchScreen.kt:230-233`) và `activeFilterCount` (`:182-187`) không xét `!maleOnly` → xem MAJOR-09. |
| MAJOR-05 (lọc client-side lan sang TTV/SST) | ĐÃ SỬA | Sketch `applyLocalFilters` thêm `story.sourceId != "wikidich"` cho `matchesMale`, khớp mẫu điều kiện nguồn đang có `[SRC] SearchViewModel.kt:353`. |
| MAJOR-06 (`FilterBottomSheet` không truyền `maleOnly`) | ĐÃ SỬA | Sketch bổ sung tham số, `localMaleOnly`, `hasAnyFilter`, chữ ký `onApply` 4 tham số, nút `:752`, call site `:593-595` — đối chiếu `[SRC] SearchScreen.kt:593-595,610-628,752`. `applyAllFilters` bỏ default `maleOnly` nên call site thiếu sẽ không biên dịch. Sai lệch nhỏ của sketch so với code: xem MINOR-10. |
| MAJOR-07 (3 hàm Search không chuyển tiếp `maleOnly`) | SỬA MỘT PHẦN | Phần chuyển tiếp đã có và được compiler ép buộc (bỏ default ở `IStoryRepository`). Nhưng sketch của 3 hàm viết lại logic hiện có thay vì chỉ thêm tham số, gây regression → xem MAJOR-08. |
| MINOR-01 (default ở override) | ĐÃ SỬA | Khớp `[SRC] TangThuVienScraper.kt:385`, `StoryRepository.kt:194-199`, `LacaTruyenApiTest.kt:95-102`. |
| MINOR-02 (test cũ đổi assertion) | ĐÃ SỬA | Test `[SRC] WikidichScraperTest.kt:211` khai báo Loại C ở mục 8.1. |
| MINOR-03 (TC-09 lệch code) | ĐÃ SỬA | TC-09 nay ghi mọi nguồn lỗi → `emptyList()`, khớp `[SRC] StoryRepository.kt:168-178,242-251`; chuỗi lỗi theo màn khớp `ExploreViewModel.kt:108`, `SearchViewModel.kt:151,292,334`. |
| MINOR-04 (chip Khám Phá phụ thuộc `state.genres`) | ĐÃ SỬA | Chip nằm ngoài `when` (`[SRC] ExploreScreen.kt:111`) và ngoài `if (state.genres.isNotEmpty())` (`:129`). |
| MINOR-05 (`clearAllFilters` bỏ `&& hadSearched`) | ĐÃ SỬA | Sketch giữ điều kiện như `[SRC] SearchViewModel.kt:251`. |
| MINOR-06 (mâu thuẫn `so=4&vo=1` / TC-04) | ĐÃ SỬA | Mục 1.1.3 và dòng rủi ro 2 mục 9 nay giới hạn ở URL `/tim-kiem?qs=2`; TC-04 có nhánh chính và nhánh fallback. |
| MINOR-07 (trích dẫn lệch, từ ngữ tuyệt đối) | SỬA MỘT PHẦN | (a), (b), (c) đã sửa. (d) còn: "100% call site" (dòng 21), "loại bỏ triệt để" (dòng 26, 27, 117), "bảo vệ nguyên vẹn 100%" (dòng 558), "tuân thủ 100% cú pháp Kotlin" (dòng 799), "giữ lại 100%" (TC-10). Chuyển thành MINOR-11. |
| MINOR-08 (chip chồng lên lưới) | ĐÃ SỬA | Sketch bọc `Row` + `Box(Modifier.weight(1f))` trong `Column` bên trong `PullToRefreshBox` (`[SRC] ExploreScreen.kt:103-110`). |

#### Đối Chiếu Các File `[MODIFY]` Với Code Thật

| File | Khớp / Lệch |
| :--- | :--- |
| `StorySource.kt` | Khớp: `getCompletedStories(page: Int = 1)` `:51`, `getStoriesByGenre(..., completedOnly = true)` `:54-59`. Plan giữ default `maleOnly = true` ở đây → xem MAJOR-10. |
| `IStoryRepository.kt` | Khớp: `:18`, `:20-25`. Bỏ default `maleOnly` khả thi; mọi call site main code chỉ nằm ở `ExploreViewModel.kt` và `SearchViewModel.kt` (Grep `getCompletedStories|getStoriesByGenre` trên `app/src`). |
| `WikidichScraper.kt` | Khớp: `SEARCH_PAGE_SIZE` `:54`, `GENRE_QUERY_MAP` `:76-124` (4 tag `gender=` tại `:84-87`), `getCompletedStories` `:317-331`, `getStoriesByGenre` `:333-356`, `getGenreList` `:358-399` (4 tag tại `:366-369`). |
| `TangThuVienScraper.kt` / `SsTruyenScraper.kt` | Khớp: `:376,385` và `:713,724`. |
| `StoryRepository.kt` | Khớp: gọi `source.getCompletedStories(p)` `:147`, `source.getStoriesByGenre(genreSlug, p, 0, completedOnly)` `:218` (positional). |
| `SearchViewModel.kt` | Lệch: sketch `browseByGenre`/`browseByFilterOnly`/`loadMoreFilterResults` khác đáng kể với `:261-297`, `:302-339`, `:398-441` (MAJOR-08). `applyFilter` `:157-195` bị thay bằng ủy quyền sang `applyAllFilters` — hành vi thay đổi nhưng chỉ được gọi trong test (`SearchViewModelTest.kt:188,217`). |
| `SearchScreen.kt` | Lệch: `hasActiveFilters` `:230-233`, `activeFilterCount` `:182-187` không được plan cập nhật (MAJOR-09). `FilterBottomSheet` là `private fun` (`:610`) có `onClearAll` (`:617`), không có nút "Đặt lại" (MINOR-10). |
| `ExploreViewModel.kt` / `ExploreScreen.kt` | Khớp: `ExploreState` `:33-43`, `loadFirstPage` `:81-113`, `loadMore` `:115-152`, `pullToRefresh` `:158-190`; nhánh loading `ExploreScreen.kt:112` là `state.isLoading` (sketch ghi `state.isLoading && state.stories.isEmpty()` — MINOR-12). |
| `WikidichScraperTest.kt` | Khớp: `:182-190` (`getCompletedStories(1)` enqueue 1 response không phụ thuộc path → vẫn qua khi đi nhánh `/tim-kiem`), `:193-198`, `:211-259`. |
| `SearchViewModelTest.kt` / `ExploreViewModelTest.kt` | Lệch: stub dùng chữ ký cũ tại `ExploreViewModelTest.kt:48,63,64,79,95,107,128,130,145,146,160` và `SearchViewModelTest.kt:183,196,211,212` sẽ không biên dịch sau khi bỏ default (MINOR-09). Test hiện có `SearchViewModelTest.kt:207-226` mâu thuẫn với sketch `loadMoreFilterResults` (MAJOR-08). |
| `StoryRepositoryTest.kt` / `LacaTruyenApiTest.kt` | `StoryRepositoryTest.kt` hiện không gọi 2 hàm này (Grep). `LacaTruyenApiTest.kt:95-102` cần đổi chữ ký — khớp plan. |

#### Phát Hiện Mới (Fresh Findings & Regression Check)

**[MAJOR-08] Sketch `SearchViewModel` viết lại 3 hàm gọi repository, làm mất các guard hiện có; mâu thuẫn với Bước 4 và làm đỏ test hiện có.**
- Bước 4 chỉ yêu cầu "chuyển tiếp `maleOnly`" ở 3 hàm, nhưng sketch mục 5.2 (dòng 451-556) thay luôn thân hàm. Người triển khai làm theo sketch sẽ đưa vào các thay đổi sau, không được khai báo và không có trong Test Contract:
  1. `loadMoreFilterResults`: `[SRC] SearchViewModel.kt:400` có guard `!current.isFilterActive`, `:402` bỏ qua khi có keyword, `:404` `selectedGenre ?: return` (chỉ phân trang khi duyệt theo thể loại). Sketch bỏ cả 3 guard và thêm nhánh `getCompletedStories(nextPage)` khi không có thể loại — mở rộng phân trang cho chế độ chỉ lọc, ngoài phạm vi plan.
  2. `[SRC] SearchViewModel.kt:421-427`: dừng phân trang khi trang mới chỉ toàn bản trùng (`hasNewItems`), giữ `currentPage` khi không có item mới. Sketch đổi thành `canLoadMore = stories.isNotEmpty()` và `currentPage = nextPage` vô điều kiện.
  3. `[SRC] SearchViewModel.kt:432-437`: khi lỗi đặt `canLoadMore = false` và hiện lỗi tiếng Việt. Sketch chỉ đặt `isLoadingMore = false`.
  4. `browseByGenre`/`browseByFilterOnly`: code hiện tại dùng `(e as? ScraperException)?.message ?: ...` (`:292`, `:334`); sketch bỏ `ScraperException.message`. `browseByGenre` hiện không qua `applyLocalFilters` (`:277-284`); sketch thêm vào. `browseByFilterOnly` hiện đặt `canLoadMore = rawStories.isNotEmpty() && chapterFilter == ChapterCountFilter.NONE` (`:323`); sketch bỏ điều kiện số chương.
- Bằng chứng regression: test hiện có `[SRC] SearchViewModelTest.kt:207-226` (`loadMoreFilterResults stops canLoadMore when duplicate results returned or exception thrown`) stub trang 2 trả lại đúng `sampleStory` và assert `canLoadMore == false` (`:225`). Với sketch, `stories.isNotEmpty()` = true → test đỏ. Plan không phân loại test này (A/B/C).
- Hệ quả runtime: `[SRC] SearchScreen.kt:76-91` — `shouldLoadMore` phụ thuộc `!state.isLoadingMore && state.canLoadMore`; mỗi lần `isLoadingMore` đổi true→false thì key `LaunchedEffect` đổi false→true và gọi lại `loadMoreFilterResults()`. Nếu trang mới chỉ toàn bản trùng, hoặc `applyLocalFilters` lọc hết (ví dụ trang Wikidich toàn truyện bị loại), danh sách không dài thêm nên vị trí cuộn vẫn ở cuối → chuỗi request liên tiếp trang 3, 4, … cho tới khi một trang rỗng. Với Wikidich đây là chuỗi request vào `/tim-kiem` — đúng kiểu bị throttle ~12s theo `[SRC] docs/lessons-learned.md:112`.
- Yêu cầu: sketch 3 hàm chỉ thêm `maleOnly = ...` vào lời gọi repository và giữ nguyên phần còn lại (guard `:400-404`, `hasNewItems` `:421-427`, xử lý lỗi `:432-437`, `:292`, `:334`, `canLoadMore` `:323`); nếu muốn đổi hành vi nào thì nêu riêng, thêm vào Test Contract và phân loại test `SearchViewModelTest.kt:207-226`.

**[MAJOR-09] Tắt "Truyện nam" trên Tìm Kiếm không hiện chip xóa nhanh và badge; call site thứ 4 của `applyAllFilters` không bao giờ hiển thị khi chỉ có `maleOnly = false`.**
- `[SRC] SearchScreen.kt:230-233`: `hasActiveFilters = selectedGenre != null || chapterFilter != NONE || !completedOnly`; `:235-236` toàn bộ hàng `InputChip` nằm trong `AnimatedVisibility(visible = hasActiveFilters)`. Chip mới "Cả truyện nữ/khác" (sketch dòng 659-678) đặt trong hàng này.
- `[SRC] SearchScreen.kt:182-187`: `activeFilterCount` chỉ đếm thể loại, số chương, `!completedOnly`; badge và màu nút lọc (`:191`, `:208`, `:219`) dựa vào giá trị này.
- Plan (Bước 4, bảng 5.1, sketch) không nêu việc thêm `!state.maleOnly` vào 2 biểu thức trên. Kịch bản: user mở sheet, tắt "Truyện nam", bấm "Áp dụng bộ lọc" → `isFilterActive = true`, kết quả đổi, nhưng không có chip nào hiện ra và badge = 0. Chip "Cả truyện nữ/khác" chỉ hiện khi đồng thời có bộ lọc khác. TC-08 và mục 9 ("Cập nhật đủ 5 call sites") dựa vào chip này.
- Yêu cầu: thêm `!state.maleOnly` vào `hasActiveFilters` và một phần tử vào `activeFilterCount`; bổ sung vào Bước 4 và mục 8 (ít nhất kiểm thủ công hoặc test UI nếu có).

**[MAJOR-10] Tầng `StoryRepository → StorySource` vẫn có thể bỏ sót `maleOnly` âm thầm và không có test nào bắt.**
- Plan giữ default `maleOnly: Boolean = true` ở `StorySource` (mục 3.1.4, 5.2.3). `[SRC] StoryRepository.kt:147` gọi `source.getCompletedStories(p)` và `:218` gọi `source.getStoriesByGenre(genreSlug, p, 0, completedOnly)` theo vị trí; nếu không sửa, cả hai vẫn biên dịch và luôn gửi `maleOnly = true` tới Wikidich.
- Test ViewModel mock `IStoryRepository` nên không chạm tới đoạn này. Mục 8.1 không có test nào cho `StoryRepositoryTest`; Bước 6 chỉ ghi "cập nhật mock/stub khớp với chữ ký mới", trong khi bảng 5.1 (dòng 166) ghi "kiểm thử chuyển tiếp chính xác `completedOnly` và `maleOnly`" — mâu thuẫn nội tại. `StoryRepositoryTest.kt` hiện không gọi 2 hàm này (Grep), nên không có gì để "cập nhật".
- Đây là cùng loại lỗi với MAJOR-03/06/07 ở tầng còn lại. Yêu cầu một trong hai: (a) bỏ default `maleOnly` ở `StorySource` (cập nhật call site test `WikidichScraperTest.kt:187,195,239,248`, `SsTruyenScraperTest.kt:324,351`), hoặc (b) thêm test có tên cụ thể vào mục 8.1, ví dụ `getCompletedStories forwards maleOnly to every active source` và `getStoriesByGenre forwards maleOnly to every active source`, dùng fake `StorySource` ghi lại tham số.

**[MINOR-09] Stub test cũ không biên dịch sau khi bỏ default ở `IStoryRepository`.** `[SRC] ExploreViewModelTest.kt:48,63,64,79,95,107,128,130,145,146,160` và `SearchViewModelTest.kt:183,196,211,212` dùng `getCompletedStories(n)` / `getStoriesByGenre(slug, n, 0, true)`. Bước 6 chỉ nêu "thêm test" cho 2 file này. Nên ghi rõ cập nhật các stub (ví dụ thêm `maleOnly = true` hoặc `any()`), phân loại Loại B (chỉ đổi chữ ký, không đổi assertion).

**[MINOR-10] Sketch `FilterBottomSheet` lệch code thật.** (a) Plan mô tả "Nút Đặt lại (lines 630-645)" nhưng code không có nút này; `:630-645` là `ModalBottomSheet`/`Column`. Nút thật là "Xóa tất cả bộ lọc" `[SRC] SearchScreen.kt:767-776` → `onClearAll` (`:617`) → `viewModel.clearAllFilters()` (`:597-600`); sketch bỏ tham số `onClearAll`. Hành vi reset `maleOnly = true` vẫn đúng nhờ `clearAllFilters` của sketch. (b) Sketch gọi `onDismiss()` sau `onApply` trong nút, trong khi call site `:594` đã đặt `showFilterSheet = false`. (c) Sketch khai báo `fun FilterBottomSheet`, code là `private fun` (`:610`).

**[MINOR-11] Từ ngữ tuyệt đối còn sót** (phần còn lại của MINOR-07d): dòng 21, 26, 27, 117, 558, 799, TC-10.

**[MINOR-12] Nhánh loading trong sketch `ExploreScreen` đổi điều kiện.** Code `[SRC] ExploreScreen.kt:112` là `state.isLoading ->`; sketch ghi `state.isLoading && state.stories.isEmpty()`. `loadFirstPage` luôn xóa `stories` trước khi tải (`ExploreViewModel.kt:87`) nên kết quả tương đương; nên giữ nguyên để diff tối thiểu.

#### Mental Simulation Matrix (S1–S6 + Domain)

| Kịch bản | Kết quả | Loại bằng chứng | Chi tiết |
| :--- | :--- | :--- | :--- |
| S1 Cold-start & Build | PASS (kèm MINOR-09) | `[CMD]`, `[SRC]` | Baseline `compileDebugKotlin`, `ktlintCheck`, `testDebugUnitTest` Exit 0 (evidence.md). Không thêm dependency. Bỏ default ở `IStoryRepository`/`applyAllFilters` làm compiler báo mọi call site thiếu — gồm cả stub test (MINOR-09), lỗi sẽ lộ ngay khi build. |
| S2 Lifecycle & Teardown | PASS | `[SRC]` | `loadFirstPage`/`pullToRefresh` hủy `loadJob`/`loadMoreJob` (`ExploreViewModel.kt:82-83,159-160`); `searchJob?.cancel()` (`SearchViewModel.kt:120,265,303,408`). `toggleMaleOnly` đi qua `loadFirstPage`. |
| S3 Database & Temporal | N/A | `[SRC]` | Không đổi Room/DataStore. `maleOnly` chỉ nằm trong UI state; process bị kill → mặc định `true` (timeline bên dưới). |
| S4 Cross-Task State Flow | FAIL | `[SRC]` | MAJOR-08 (regression `loadMoreFilterResults`, test `SearchViewModelTest.kt:207-226`), MAJOR-09 (`SearchScreen.kt:182-187,230-236`), MAJOR-10 (`StoryRepository.kt:147,218`). |
| S5 Security & Privacy | PASS | `[SRC]` | Không đụng cookie/auth/Manifest. Tham số URL chỉ từ hằng số và `GENRE_QUERY_MAP`; slug lạ trả `emptyList()` trước khi gọi mạng (`WikidichScraper.kt:340`, test `WikidichScraperTest.kt:193-198`). Mọi nhánh sketch gọi `checkLoginRequired` trước `parseStoryList`. |
| S6 External Resilience | FAIL | `[SRC]` | MAJOR-08: chuỗi tải trang liên tiếp vào `/tim-kiem` khi trang trùng/lọc hết (`SearchScreen.kt:76-91`, `docs/lessons-learned.md:112`). Độ trễ thực tế của `/tim-kiem?qs=2&gender=...` vẫn chưa đo. |
| Domain (gộp đa nguồn / UI tiếng Việt) | PASS | `[SRC]` | Lọc client-side chỉ áp cho `sourceId == "wikidich"`. Slug `ngon-tinh`/`dam-my` nếu còn trong danh sách gộp từ TTV/SST sẽ làm Wikidich trả rỗng (`WikidichScraper.kt:340`), nguồn khác vẫn trả. Chuỗi UI mới đều tiếng Việt. |

#### Adversarial Timelines

```
Cơ chế: Cuộn tải thêm khi trang mới toàn bản trùng (S4/S6, bất đồng bộ) — theo sketch loadMoreFilterResults
T1: browseByGenre trả trang 1, canLoadMore=true; user cuộn tới cuối → shouldLoadMore=true (SearchScreen.kt:83) → loadMoreFilterResults(page 2)
T2: Trang 2 chỉ chứa bản trùng (hoặc applyLocalFilters lọc hết) → sketch đặt currentPage=2, canLoadMore=stories.isNotEmpty()=true, isLoadingMore=false; danh sách không dài thêm
T3: isLoadingMore true→false làm shouldLoadMore false→true → LaunchedEffect (SearchScreen.kt:87-91) chạy lại → page 3, 4, ... mỗi lần một request /tim-kiem tới Wikidich
Kết cục: chuỗi request tới khi gặp trang rỗng, chạm throttle ~12s; code hiện tại dừng nhờ hasNewItems (SearchViewModel.kt:421-427) → LỖI (MAJOR-08)
```

```
Cơ chế: Tắt "Truyện nam" chỉ qua FilterBottomSheet (S4)
T1: Không có bộ lọc khác; user mở sheet, tắt "Truyện nam", bấm "Áp dụng bộ lọc" → applyAllFilters(null, NONE, true, false) → isFilterActive=true → browseByFilterOnly
T2: SearchScreen tính hasActiveFilters = false (SearchScreen.kt:230-233), activeFilterCount = 0 (:182-187)
T3: Hàng InputChip ẩn (:235-236), chip "Cả truyện nữ/khác" không hiện, badge không hiện
Kết cục: user không thấy bộ lọc đang khác mặc định và không có lối tắt bật lại → LỖI (MAJOR-09)
```

```
Cơ chế: Repository quên chuyển tiếp maleOnly (S4)
T1: Người triển khai sửa chữ ký override StoryRepository.getCompletedStories(page, maleOnly) nhưng giữ dòng source.getCompletedStories(p) (StoryRepository.kt:147)
T2: Build Exit 0 nhờ default maleOnly=true ở StorySource; test ViewModel (mock IStoryRepository) xanh
T3: User tắt chip trên Khám Phá → Wikidich vẫn đi nhánh /tim-kiem?...gender=...
Kết cục: không có lớp kiểm soát nào bắt được → LỖI (MAJOR-10)
```

```
Cơ chế: Process bị kill khi maleOnly=false (S3, temporal)
T1: User tắt "Truyện nam" trên Khám Phá | T2: App vào nền, hệ thống kill process | T3: Mở lại → ExploreState() mới, maleOnly=true; init gọi loadFirstPage (ExploreViewModel.kt:58-61) với maleOnly=true, chip BẬT
Kết cục: UI và dữ liệu nhất quán với mặc định sản phẩm, không ghi DB → AN TOÀN
```

```
Cơ chế: Phiên Wikidich hết hạn giữa lúc bật/tắt chip (S5, TOCTOU)
T1: toggleMaleOnly → /tim-kiem?qs=2&gender=... gửi đi | T2: Cookie hết hạn, server trả trang "Đăng nhập để xem nội dung" | T3: checkLoginRequired (WikidichScraper.kt:322,328,349) ném LoginRequiredException → StoryRepository.kt:151,170-172 markLoginRequired; nguồn khác vẫn trả kết quả; banner hiện (ExploreScreen.kt:154-155)
Kết cục: không parse nhầm trang đăng nhập → AN TOÀN
```

```
Cơ chế: Bấm chip "Truyện nam" liên tiếp trên Khám Phá (S2, đồng thời)
T1: toggleMaleOnly → loadFirstPage job A (maleOnly=false) | T2: Bấm lại trong lúc A chờ /bang-xep-hang → loadFirstPage hủy A (ExploreViewModel.kt:82) và chạy job B (maleOnly=true) | T3: Chỉ B cập nhật state
Kết cục: không có kết quả của job cũ ghi đè → AN TOÀN
```

#### Commands Executed

Trích từ `evidence.md` (reviewer không có shell):

- `### [CMD] git status --short` — `Exit: 0` — `M app/build.gradle.kts`, `M build.gradle.kts`, `M docs/architecture.md`, `M docs/changelog.md`, `M docs/progress.md`, `?? docs/plans/2026-10-27-wikidich-male-tag-and-newest-sort-implementation-plan.md`.
- `### [CMD] git diff --stat` — `Exit: 0` — `app/build.gradle.kts | 4 ++--`, `build.gradle.kts | 2 +-`, `5 files changed, 16 insertions(+), 7 deletions(-)`. Thay đổi chưa commit ở 2 file build vẫn nằm ngoài phạm vi plan (mục 1.2); cần tách bạch khi commit.
- `### [CMD] cmd.exe /d /c gradlew.bat compileDebugKotlin` — `Exit: 0` — `BUILD SUCCESSFUL in 971ms`.
- `### [CMD] cmd.exe /d /c gradlew.bat ktlintCheck` — `Exit: 0` — `> Task :app:ktlintMainSourceSetCheck UP-TO-DATE`, `BUILD SUCCESSFUL in 699ms`.
- `### [CMD] cmd.exe /d /c gradlew.bat testDebugUnitTest` — `Exit: 0` — `> Task :app:testDebugUnitTest UP-TO-DATE`, `BUILD SUCCESSFUL in 950ms`.
- `### [CMD] node --test .agents/scripts/code-freeze-guard.test.js` — `Exit: 0` — `ℹ pass 36`, `ℹ fail 0`.
- `### [CMD] node --test .agents/scripts/plan-approval.test.js` — `Exit: 0` — `ℹ pass 13`, `ℹ fail 0`.
- `### [CMD] node --test .agents/scripts/review-run.test.js` — `Exit: 0` — `ℹ pass 10`, `ℹ fail 0`.
- UNVERIFIED (lệnh đề xuất, cần cookie phiên đăng nhập): `curl.exe -q -s -o NUL -w "%{time_total}" "https://wikicv.org/tim-kiem?qs=2&gender=5794f03dd7ced228f4419196&status=5794f03dd7ced228f4419191&so=4&vo=1"` chạy 2 lần liên tiếp để đo throttle (S6).

#### Kết luận

Revision 4 đã xử lý MAJOR-03, MAJOR-05, MAJOR-06 và các MINOR-01…MINOR-06, MINOR-08; việc bỏ default `maleOnly` ở `IStoryRepository` và `applyAllFilters` giúp compiler bắt lỗi chuyển tiếp ở tầng ViewModel/UI. Còn 3 lỗi `[MAJOR]`: MAJOR-08 (sketch viết lại `loadMoreFilterResults`/`browseByGenre`/`browseByFilterOnly`, làm mất guard `hasNewItems` và đỏ test `SearchViewModelTest.kt:207-226`), MAJOR-09 (`hasActiveFilters`/`activeFilterCount` trong `SearchScreen.kt` không xét `maleOnly`), MAJOR-10 (tầng `StoryRepository → StorySource` không có lớp kiểm soát cho `maleOnly`). S4 và S6 FAIL. Planner cần sửa 3 điểm này và các MINOR-09…MINOR-12 trong Revision 5.

**Kết luận**: [CHANGES_REQUESTED]

### Audit Run 4

- **Reviewer**: Claude Code CLI (claude-opus-5-5)
- **Ngày**: 2026-10-28
- **Đối tượng**: Technical Plan Revision 5

Audit Run 1, 2, 3 đều kết luận CHANGES_REQUESTED. Danh sách lỗi của các lượt đó chỉ dùng làm danh sách cần kiểm lại; trạng thái dưới đây được xác định lại trên code thật.

#### Kiểm Chứng Lỗi Cũ

| Mã lỗi | Trạng thái | Bằng chứng trên code thật / Ghi chú |
| :--- | :--- | :--- |
| MAJOR-01 (throttle `/tim-kiem`) | SỬA MỘT PHẦN (hạ xuống MINOR-14) | Nhánh `!maleOnly` giữ `/bang-xep-hang?so=5$startParam` và fallback như `[SRC] WikidichScraper.kt:319-330`. Timeout Wikidich 25s `[SRC] StoryRepository.kt:115`, 1 trang/lượt `:121-122`. Cache 10 phút áp dụng cho Wikidich vì Wikidich không override `useEtagCaching` (mặc định `false`, `[SRC] BaseScraper.kt:35`), nhánh cache `:121-129`. Phần còn lại: nhánh mặc định (`maleOnly = true`) đi `/tim-kiem` ở mọi lần mở Khám Phá, và Control 3 không giảm tải phía server — xem MINOR-14. |
| MAJOR-02 (ngữ nghĩa `so=4&vo=1`) | ĐÃ SỬA (dựa trên `[USER_EVIDENCE]`) | ID `gender=`/`tag=`/`official=` khớp `[SRC] WikidichScraper.kt:87,105,110`. Ngữ nghĩa `so=4&vo=1` chỉ dựa vào URL người dùng cung cấp từ phiên đã đăng nhập; plan ghi rõ nguồn, không trình bày như reviewer đã tự kiểm chứng. |
| MAJOR-03 (`pullToRefresh`) | ĐÃ SỬA | Sketch khớp `[SRC] ExploreViewModel.kt:158-190` (cancel `:159-160`, `canLoadMore` `:177`, chuỗi lỗi `:185`), chỉ thêm `maleOnly`. |
| MAJOR-04 (máy trạng thái Search) | ĐÃ SỬA | ViewModel: `hasAnyNonDefaultFilter` thay `[SRC] SearchViewModel.kt:214-215,230` và `:374-375`. UI: sketch thêm `!state.maleOnly` vào `activeFilterCount`/`hasActiveFilters`, khớp vị trí `[SRC] SearchScreen.kt:182-187,230-233`. |
| MAJOR-05 (lọc client-side lan sang TTV/SST) | ĐÃ SỬA | `matchesMale` có `story.sourceId != "wikidich"`, cùng mẫu `[SRC] SearchViewModel.kt:353`. Nhãn so khớp lấy từ `.book-gender` `[SRC] WikidichScraper.kt:582-588`. |
| MAJOR-06 (`FilterBottomSheet`) | ĐÃ SỬA | Tham số, local state, `onApply` 4 tham số, nút áp dụng — đối chiếu `[SRC] SearchScreen.kt:593-595,610-628,752`. Lệch nhỏ ở call site: xem MINOR-13. |
| MAJOR-07 (3 hàm Search không chuyển tiếp) | ĐÃ SỬA | Sketch `browseByGenre`/`browseByFilterOnly`/`loadMoreFilterResults` (plan dòng 454-578) nay chỉ khác code thật ở đối số `maleOnly`. |
| MAJOR-08 (sketch viết lại 3 hàm) | ĐÃ SỬA | Đối chiếu từng dòng: guard `[SRC] SearchViewModel.kt:400,402,404`, `hasNewItems` `:421-427`, xử lý lỗi `:430-438`, `ScraperException.message` `:292,334`, `canLoadMore` có điều kiện số chương `:323` — sketch giữ nguyên toàn bộ. Test `[SRC] SearchViewModelTest.kt:207-226` sẽ vẫn qua sau khi đổi stub theo Loại B. |
| MAJOR-09 (badge/chip khi chỉ tắt nam) | ĐÃ SỬA | Sketch plan dòng 615-627 thêm `!state.maleOnly` vào hai biểu thức thật `[SRC] SearchScreen.kt:182-187` và `:230-233`; hàng chip `:235-236` sẽ hiện. |
| MAJOR-10 (`StoryRepository → StorySource`) | ĐÃ SỬA | Plan bỏ default `maleOnly` ở `StorySource` (hiện `[SRC] StorySource.kt:51,54-59`), nên `[SRC] StoryRepository.kt:147,218` không biên dịch nếu thiếu đối số. Grep `getCompletedStories|getStoriesByGenre` trên `app/src/main`: ngoài `StoryRepository` không còn caller nào của `StorySource`. Thêm 2 test có tên ở mục 8.1; `StoryRepositoryTest` đã có mẫu dựng `StoryRepository` với `mockk<StorySource>()` (`[SRC] StoryRepositoryTest.kt:149-156`). |
| MINOR-01…MINOR-08 | ĐÃ SỬA | Đã kiểm lại ở Run 3; Revision 5 không làm lệch lại (override không default: `[SRC] TangThuVienScraper.kt:385-390`, `SsTruyenScraper.kt:724-729`, `LacaTruyenApiTest.kt:95-102`). |
| MINOR-09 (stub test cũ) | ĐÃ SỬA | Danh sách Loại B ở mục 8.1 khớp Grep: `[SRC] ExploreViewModelTest.kt:48,63,64,79,95,107,128,130,145,146,160`, `SearchViewModelTest.kt:183,196,211,212`, `WikidichScraperTest.kt:187,195` (`:239,248` thuộc test Loại C), `SsTruyenScraperTest.kt:324,351`. |
| MINOR-10 (sketch `FilterBottomSheet`) | ĐÃ SỬA | Sketch nay là `private fun`, giữ `onClearAll`, không gọi `onDismiss()` sau `onApply` — khớp `[SRC] SearchScreen.kt:610,617,752,770`. |
| MINOR-11 (từ ngữ tuyệt đối) | ĐÃ SỬA | Grep các từ cấm trong Technical Plan: chỉ còn "100%" khi trích quy tắc "UI 100% tiếng Việt" (dòng 44, 48, 71). |
| MINOR-12 (nhánh loading) | ĐÃ SỬA | Sketch dòng 214 dùng `state.isLoading ->`, khớp `[SRC] ExploreScreen.kt:112`. |

#### Đối Chiếu Các File `[MODIFY]` Với Code Thật

| File | Khớp / Lệch |
| :--- | :--- |
| `StorySource.kt` | Khớp `:51`, `:54-59`. |
| `IStoryRepository.kt` | Khớp `:18`, `:20-25`. |
| `WikidichScraper.kt` | Khớp: `SEARCH_PAGE_SIZE` `:54`, `GENRE_QUERY_MAP` `:76-124` (4 tag `:84-87`), `getCompletedStories` `:317-331`, `getStoriesByGenre` `:333-356` (`qs=1...tc=&tf=0&m=&y=&q=` tại `:347`), `getGenreList` `:358-399` (4 tag `:366-369`; còn 36 thể loại sau khi gỡ, khớp TC-05 "> 20"). |
| `TangThuVienScraper.kt` / `SsTruyenScraper.kt` | Khớp `:376,385` / `:713,724`. `completedOnly` đã có sẵn ở override `getStoriesByGenre`; chỉ cần thêm `maleOnly`. |
| `StoryRepository.kt` | Khớp `:128`, `:147`, `:194-199`, `:218`. |
| `SearchViewModel.kt` | Khớp (xem MAJOR-08). `applyFilter` `:157-195` chỉ được gọi trong test (`SearchViewModelTest.kt:188,217`); Grep `app/src/main` không có caller của `applyFilter`, `toggleCompletedFilter` (MINOR-15). |
| `SearchScreen.kt` | Khớp `:182-187`, `:230-233`, `:253,275,298`, `:587-601`, `:610-628`, `:752`, `:767-776`. Lệch: call site sketch dùng `genres = state.genres` (MINOR-13). |
| `ExploreViewModel.kt` | Khớp `:33-43`, `:81-113`, `:115-152`, `:158-190`. |
| `ExploreScreen.kt` | Khớp `:103-112`, `:127-151`. Lệch: sketch `PullToRefreshBox` bỏ `.padding(padding)` (MINOR-13). |
| `WikidichScraperTest.kt` | Khớp `:182-198`, `:211-259` (dispatcher theo path `:227-237` dùng lại được cho test nhánh `/bang-xep-hang` vs `/tim-kiem`). |
| `SearchViewModelTest.kt` / `ExploreViewModelTest.kt` / `StoryRepositoryTest.kt` / `LacaTruyenApiTest.kt` / `SsTruyenScraperTest.kt` | Khớp (xem MINOR-09, MAJOR-10). `repository` trong `SearchViewModelTest` là `mockk(relaxed = true)` (`:40`). |

#### Phát Hiện Mới (Fresh Findings & Regression Check)

**[MINOR-13] Sketch lệch code thật ở 2 chỗ, compiler/kiểm thủ công sẽ lộ nhưng nên sửa để người triển khai không chép sai.**
- (a) Call site 5 (plan dòng 727-728) ghi `genres = state.genres`. `[SRC] SearchViewModel.kt:48-62`: `SearchState` không có trường `genres`; danh sách thể loại là `StateFlow` riêng (`:68-69`), call site thật dùng `genres = genres` (`[SRC] SearchScreen.kt:588`). Chép nguyên sketch sẽ lỗi biên dịch.
- (b) Sketch `ExploreScreen` (plan dòng 183-187) đặt `modifier = Modifier.fillMaxSize()` cho `PullToRefreshBox`; code thật là `Modifier.fillMaxSize().padding(padding)` (`[SRC] ExploreScreen.kt:106-109`). Thiếu `padding` thì chip "Truyện nam" nằm dưới `TopAppBar`. Lỗi này không bị compiler bắt.
- (c) Bảng 5.1 / Bước 4 ghi nút "Xóa tất cả bộ lọc" "reset cả `localMaleOnly = true`", còn sketch giữ `onClick = onClearAll` (đóng sheet và gọi `clearAllFilters()`, nơi `maleOnly` được reset). Kết quả tương đương; nên thống nhất mô tả.

**[MINOR-14] Rủi ro throttle còn lại ở nhánh mặc định, và Control 3 trong mục 9 mô tả quá mức tác dụng.**
- Vì `maleOnly = true` là mặc định, mỗi lần mở Khám Phá (`[SRC] ExploreViewModel.kt:58-61`) đều gọi `/tim-kiem?qs=2&gender=...&status=...`; chọn thể loại ngay sau đó là request `/tim-kiem` thứ hai liên tiếp — đúng kịch bản `[SRC] docs/lessons-learned.md:112`. Nhánh nhanh `/bang-xep-hang` chỉ dùng khi user chủ động tắt lọc. Mục 1.1.3 nói định tuyến "để không chạm hạn mức", nhưng điều này không đúng với luồng mặc định. Timeout 25s (`StoryRepository.kt:115`) vẫn lớn hơn 12.03s nên không mất dữ liệu; hệ quả là độ trễ.
- Control 3 ("bấm chip nhanh liên tiếp chỉ xử lý request cuối cùng"): `[SRC] BaseScraper.kt:158` dùng `client.newCall(...).execute()` (blocking). Hủy coroutine không hủy HTTP call đang chạy, nên request cũ vẫn tới server và vẫn tính vào throttle. Control này chỉ bảo vệ tính nhất quán của UI state, không giảm số request tới `/tim-kiem`.
- Quy tắc phòng ngừa `[SRC] docs/lessons-learned.md:121` yêu cầu đo `time_total` bằng `curl.exe -w`; phép đo vẫn UNVERIFIED. Đề nghị khi triển khai: sửa câu chữ Control 3, ghi nhận sự chấp nhận rủi ro ở nhánh mặc định vào `docs/scraping.md`, và bổ sung một mục ngắn vào `docs/lessons-learned.md` nếu số đo thực tế cho thấy độ trễ đáng kể.

**[MINOR-15] `toggleMaleFilter()` không có caller trong UI.** Plan thêm `toggleMaleFilter()` và test `toggleMaleFilter updates state and triggers search or browse`, nhưng mọi thao tác bật/tắt "Truyện nam" trên Tìm Kiếm đều đi qua `applyAllFilters` (sheet và chip xóa nhanh). Code hiện tại có cùng mẫu: `toggleCompletedFilter` không được gọi ở `app/src/main` (Grep). Không chặn plan; có thể bỏ hàm này để diff nhỏ hơn, hoặc giữ để đối xứng với `toggleCompletedFilter`.

**[MINOR-16] Wiring Probe ở mục 8.3 sẽ thất bại như đang viết.** Trên ViewModel mới tạo, `hasSearched = false` (`[SRC] SearchViewModel.kt:54`). Theo sketch, `toggleMaleFilter` và `toggleCompletedFilter` chỉ gọi repository khi `hasSearched` là true (plan dòng 386, 417; code hiện tại `:379`). Chuỗi `toggleMaleFilter(); toggleCompletedFilter(); toggleCompletedFilter()` không gọi repository lần nào, nên `coVerify { repository.getCompletedStories(page = 1, maleOnly = false) }` đỏ. Cần thêm bước đặt `hasSearched = true` trước (ví dụ `applyAllFilters(null, NONE, true, false)`), hoặc dùng probe qua `applyAllFilters`. Các test có tên ở mục 8.1 không bị ảnh hưởng.

**[MINOR-17] So khớp nhãn tiếng Việt phụ thuộc dạng chuẩn Unicode.** `NON_MALE_WIKIDICH_GENDERS` so khớp `g.trim().lowercase()` với chuỗi literal (dạng NFC trong mã nguồn). Nếu `wikicv.org` trả nhãn ở dạng NFD (dấu tổ hợp), `"ngôn tình"` sẽ không khớp và truyện không bị lọc. Fixture `search_logged_in.html:473,501` là bằng chứng duy nhất hiện có. Đề nghị chuẩn hóa `java.text.Normalizer.normalize(g, NFC)` trước khi so khớp, hoặc thêm test với chuỗi NFD. Hậu quả nếu sót chỉ là lọc thiếu ở tìm kiếm từ khóa, không mất dữ liệu.

#### Mental Simulation Matrix (S1–S6 + Domain)

| Kịch bản | Kết quả | Loại bằng chứng | Chi tiết |
| :--- | :--- | :--- | :--- |
| S1 Cold-start & Build | PASS | `[CMD]`, `[SRC]` | Baseline `compileDebugKotlin`, `ktlintCheck`, `testDebugUnitTest` đều `Exit: 0` (evidence.md). Không thêm dependency. Bỏ default `maleOnly` ở `StorySource`, `IStoryRepository`, `applyAllFilters` khiến mọi call site thiếu đối số lộ ra lúc biên dịch; danh sách stub test cần đổi đã khớp Grep (MINOR-09). MINOR-13(a) cũng sẽ lộ khi biên dịch. |
| S2 Lifecycle & Teardown | PASS | `[SRC]` | `loadFirstPage`/`pullToRefresh` hủy `loadJob`/`loadMoreJob` (`ExploreViewModel.kt:82-83,159-160`); `searchJob?.cancel()` (`SearchViewModel.kt:120,265,303,408`). `toggleMaleOnly` đi qua `loadFirstPage`. Không thêm scope/listener mới. |
| S3 Database & Temporal | N/A | `[SRC]` | Không đổi Room/DataStore (mục 1.2). `maleOnly` chỉ nằm trong UI state; process bị kill → về mặc định `true` (timeline bên dưới). |
| S4 Cross-Task State Flow | PASS | `[SRC]` | Ba hàm Search giữ nguyên guard (`SearchViewModel.kt:400-404,421-427`); `hasActiveFilters`/`activeFilterCount` xét `maleOnly` (`SearchScreen.kt:182-187,230-233`); `StoryRepository.kt:147,218` buộc truyền `maleOnly`. Các lối reset: `clearFilter()` → `SearchState()` (`:445`) chỉ chạy khi mọi bộ lọc ở mặc định nhờ `hasAnyNonDefaultFilter`. |
| S5 Security & Privacy | PASS | `[SRC]` | Không đụng cookie/auth/Manifest. Tham số URL lấy từ hằng số và `GENRE_QUERY_MAP`; slug lạ trả `emptyList()` trước khi gọi mạng (`WikidichScraper.kt:340`, test `WikidichScraperTest.kt:193-198`). Mọi nhánh sketch gọi `checkLoginRequired` trước `parseStoryList`. |
| S6 External Resilience | UNVERIFIED | `[SRC]` | Timeout 25s > 12.03s (`StoryRepository.kt:115`, `lessons-learned.md:112`); request blocking không bị hủy khi hủy coroutine (`BaseScraper.kt:158`) — MINOR-14. Độ trễ thực của `/tim-kiem?qs=2&gender=...` chưa đo (cần cookie đăng nhập). |
| Domain (gộp đa nguồn / UI tiếng Việt) | PASS | `[SRC]` | Lọc client-side chỉ áp cho `sourceId == "wikidich"`; nhãn lấy từ `.book-gender` (`WikidichScraper.kt:582-588`). Slug `ngon-tinh`/`dam-my` nếu còn trong danh sách gộp từ TTV/SST sẽ làm Wikidich trả rỗng (`:340`), nguồn khác vẫn trả. Chuỗi UI mới đều tiếng Việt. |

#### Adversarial Timelines

```
Cơ chế: Mở Khám Phá rồi chọn thể loại ngay (S6, request liên tiếp vào /tim-kiem ở luồng mặc định)
T1: init → loadFirstPage → getCompletedStories(1, maleOnly=true) → /tim-kiem?qs=2&gender=...&status=...&so=4&vo=1
T2: User chọn "Hệ Thống" → loadFirstPage hủy job T1 (ExploreViewModel.kt:82) nhưng HTTP call T1 vẫn chạy (BaseScraper.kt:158 execute blocking)
T3: getStoriesByGenre("he-thong", maleOnly=true) → /tim-kiem lần 2 → server giữ ~12.03s; timeout Wikidich 25s (StoryRepository.kt:115) → vẫn nhận kết quả
Kết cục: không mất dữ liệu, độ trễ ~12s cho Wikidich; TTV/SST trả trước (song song) → CHẤP NHẬN ĐƯỢC, ghi nhận MINOR-14
```

```
Cơ chế: Tắt "Truyện nam" chỉ qua FilterBottomSheet (S4)
T1: Không có bộ lọc khác; user tắt "Truyện nam" trong sheet, bấm "Áp dụng bộ lọc" → applyAllFilters(null, NONE, true, false) → hasAnyNonDefaultFilter = true → browseByFilterOnly → getCompletedStories(1, false)
T2: SearchScreen: hasActiveFilters = true, activeFilterCount = 1 (sketch dòng 615-627 trên SearchScreen.kt:182-187,230-233)
T3: Chip "Cả truyện nữ/khác" hiện; bấm → applyAllFilters(null, NONE, true, true) → mọi cờ về mặc định, hasSearched = true → clearFilter()
Kết cục: badge, chip và dữ liệu nhất quán → AN TOÀN
```

```
Cơ chế: Tắt nam + tắt Full, rồi bật lại Full qua chip (S4)
T1: applyAllFilters(null, NONE, false, false) → browseByFilterOnly
T2: Bấm chip "Cả đang ra" → applyAllFilters(null, NONE, completedOnly=true, maleOnly=state.maleOnly=false)
T3: hasAnyNonDefaultFilter = true (do !maleOnly) → browseByFilterOnly, không rơi vào clearFilter() (SearchViewModel.kt:445)
Kết cục: maleOnly giữ false → AN TOÀN
```

```
Cơ chế: Cuộn tải thêm sau khi lọc theo thể loại với maleOnly=false (S4/S6, bất đồng bộ)
T1: browseByGenre → canLoadMore = results.isNotEmpty() | T2: loadMoreFilterResults(page 2, maleOnly=current.maleOnly) | T3: trang 2 toàn bản trùng → hasNewItems=false → canLoadMore=false (SearchViewModel.kt:421-427 giữ nguyên)
Kết cục: chuỗi tải dừng, không dồn request /tim-kiem → AN TOÀN
```

```
Cơ chế: Process bị kill khi maleOnly=false (S3, temporal)
T1: User tắt "Truyện nam" | T2: Hệ thống kill process | T3: Mở lại → ExploreState() mới, maleOnly=true; init gọi loadFirstPage (ExploreViewModel.kt:58-61), chip BẬT
Kết cục: UI và dữ liệu khớp mặc định sản phẩm, không ghi DB → AN TOÀN
```

```
Cơ chế: Phiên Wikidich hết hạn giữa lúc bật/tắt chip (S5, TOCTOU)
T1: toggleMaleOnly → /tim-kiem?qs=2&gender=... | T2: Cookie hết hạn, server trả trang "Đăng nhập để xem nội dung" | T3: checkLoginRequired ném LoginRequiredException → StoryRepository.kt:151,170-172 markLoginRequired; nguồn khác vẫn trả kết quả
Kết cục: không parse nhầm trang đăng nhập → AN TOÀN
```

#### Commands Executed

Trích từ `evidence.md` (reviewer không có shell):

- `### [CMD] git status --short` — `Exit: 0` — `M app/build.gradle.kts`, `M build.gradle.kts`, `M docs/architecture.md`, `M docs/changelog.md`, `M docs/progress.md`, `?? docs/plans/2026-10-27-wikidich-male-tag-and-newest-sort-implementation-plan.md`. Thay đổi chưa commit ở 2 file build và 3 file docs nằm ngoài plan này; cần tách bạch khi commit.
- `### [CMD] git diff --stat` — `Exit: 0` — `5 files changed, 16 insertions(+), 7 deletions(-)`.
- `### [CMD] cmd.exe /d /c gradlew.bat compileDebugKotlin` — `Exit: 0` — `BUILD SUCCESSFUL in 10s`.
- `### [CMD] cmd.exe /d /c gradlew.bat ktlintCheck` — `Exit: 0` — `> Task :app:ktlintCheck UP-TO-DATE`, `BUILD SUCCESSFUL in 1s`.
- `### [CMD] cmd.exe /d /c gradlew.bat testDebugUnitTest` — `Exit: 0` — `> Task :app:testDebugUnitTest UP-TO-DATE`, `BUILD SUCCESSFUL in 2s`.
- `### [CMD] node --test .agents/scripts/code-freeze-guard.test.js` — `Exit: 0` — `ℹ pass 36`, `ℹ fail 0`.
- `### [CMD] node --test .agents/scripts/plan-approval.test.js` — `Exit: 0` — `ℹ pass 13`, `ℹ fail 0`.
- `### [CMD] node --test .agents/scripts/review-run.test.js` — `Exit: 0` — `ℹ pass 10`, `ℹ fail 0`.
- UNVERIFIED (lệnh đề xuất, cần cookie phiên đăng nhập): `curl.exe -q -s -o NUL -w "%{time_total}" "https://wikicv.org/tim-kiem?qs=2&gender=5794f03dd7ced228f4419196&status=5794f03dd7ced228f4419191&so=4&vo=1"` chạy 2 lần liên tiếp (S6, MINOR-14).

#### Các Tấn Công Đã Thử (Attempted Attack Vectors)

1. **Còn caller nào của `StorySource.getCompletedStories/getStoriesByGenre` ngoài `StoryRepository` mà không bị compiler bắt?** Grep trên `app/src/main` chỉ thấy `StoryRepository.kt:147,218`; các implementation là `WikidichScraper.kt:317,333`, `TangThuVienScraper.kt:376,385`, `SsTruyenScraper.kt:713,724` và `LacaTruyenApiTest.kt:95,97`. Không vỡ.
2. **Xung đột hai tham số `gender=` trong một URL.** Sau khi gỡ 4 slug `:84-87`, không còn giá trị nào trong `GENRE_QUERY_MAP` (`WikidichScraper.kt:76-124`) chứa `gender=`; `genre=` (ví dụ `tien-hiep`) là khóa khác. Không vỡ.
3. **Test hiện có `loadMoreFilterResults ... duplicate` đỏ sau thay đổi.** Stub `SearchViewModelTest.kt:211-212` đổi sang 5 đối số theo Loại B; sketch giữ `hasNewItems` (`SearchViewModel.kt:421-427`) nên `assertFalse(canLoadMore)` (`:225`) vẫn đúng. Không vỡ.
4. **Cache 10 phút trả kết quả sai khi đổi `maleOnly`.** Khóa cache là URL tuyệt đối (`BaseScraper.kt:118,123`); hai nhánh `maleOnly` sinh URL khác nhau nên không dùng lẫn cache. Không vỡ.

#### Kết luận

Revision 5 đã xử lý MAJOR-08, MAJOR-09, MAJOR-10 và MINOR-09…MINOR-12; đối chiếu trên code thật không còn lỗi `[BLOCKER]` hay `[MAJOR]`. S1, S2, S4, S5 PASS; S3 N/A; S6 UNVERIFIED (không thuộc nhóm chặn S1–S5). Còn các `[MINOR]` 13–17, đề nghị người triển khai xử lý trong lúc code: dùng `genres = genres` và giữ `.padding(padding)` (MINOR-13), sửa câu chữ Control 3 và ghi nhận rủi ro throttle ở nhánh mặc định (MINOR-14), sửa Wiring Probe (MINOR-16), cân nhắc chuẩn hóa NFC (MINOR-17).

**Kết luận**: APPROVED

[PLAN_APPROVED]
