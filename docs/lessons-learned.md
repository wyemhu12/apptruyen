# Bài Học Kinh Nghiệm (Lessons Learned)

> Ghi lại sau MỖI lần sửa bug theo workflow [`.agents/workflows/post-fix.md`](../.agents/workflows/post-fix.md).
> Cùng một loại lỗi lặp lại từ 3 lần trở lên thì nâng cấp thành rule trong `.agents/rules/`.
> `@planner` đọc file này ở Bước 1 trước khi lập kế hoạch.

## Mẫu Entry

```markdown
### YYYY-MM-DD: [Mô tả ngắn]

- **Triệu chứng**: Lỗi biểu hiện thế nào
- **Nguyên nhân gốc**: Vì sao xảy ra
- **Cách sửa**: Đã thay đổi gì (file chính)
- **Quy tắc phòng ngừa**: Làm sao tránh lặp lại
```

## Governance / Agent

### 2026-10-12: Allowlist bằng regex không mô hình hóa được PowerShell argument mode và bypass đường dẫn

- **Triệu chứng**: Lệnh nguy hiểm (như `Remove-Item` kẹp trong subexpression `iex`, nháy thông minh Unicode `“ ”`, toán hạng `C:/Windows/win.ini NUL` của `git diff`, và cmdlet `Get-Content` đọc secret) lọt qua allowlist regex của hook.
- **Nguyên nhân gốc**:
  1. PowerShell 5.1 tự động diễn giải các ký tự nháy kép Unicode (`“ ” „`), nháy đơn (`‘ ’`), gạch ngang (`– —`) và khoảng trắng đặc biệt (NBSP) tương đương với ký tự ASCII, cho phép nhúng subexpression `( ... )` thực thi lệnh mà regex ASCII không nhận ra.
  2. `git diff` tự động chuyển sang chế độ `--no-index` khi có ít nhất một toán hạng nằm ngoài working tree, biến lệnh "read-only" thành công cụ đọc file bất kỳ trên hệ thống khi kết hợp với thiết bị Windows `NUL`.
  3. Parser URL WHATWG `new URL()` xử lý ký tự `\@` khác với `curl`, dẫn đến nguy cơ lệch parser (parser mismatch).
- **Cách sửa**:
  1. Token hóa chuỗi lệnh sau khi cấm toàn bộ ký tự ngoài ASCII in được (`[NON_ASCII_COMMAND]`) trên toàn bộ chuỗi thô.
  2. Whitelist cờ và kiểm tra mọi toán hạng đường dẫn phải nằm trọn trong working tree của repo, cấm thiết bị Windows (`NUL`, `CON`, `AUX`, `PRN`, `COM1-9`, `LPT1-9`) và đường dẫn vượt cấp `..`.
  3. Lọc `curl.exe -q` bằng regex bare URL neo chặt 2 đầu tới 8 host nguồn truyện chính thức, không dùng parser URL lỏng lẻo.
- **Quy tắc phòng ngừa**: Mọi bộ lọc an toàn cho shell phải dựa trên whitelist tokenized arguments và kiểm soát toán hạng đường dẫn thực tế, không dùng regex pattern matching trên chuỗi thô.

### 2026-10-12: Phê duyệt chỉ bằng thẻ văn bản trong file ghi được dẫn đến nguy cơ tự mở khóa

- **Triệu chứng**: Agent có thể tự ghi dòng `[PLAN_APPROVED]` vào thân file plan, hoặc chia nhỏ các chunk ghi đè để tự đưa plan vào trạng thái mở khóa mà không qua review.
- **Nguyên nhân gốc**: Phê duyệt chỉ kiểm tra sự tồn tại của chuỗi `[PLAN_APPROVED]` mà không có máy trạng thái ràng buộc header và không khóa nội dung sau khi duyệt.
- **Cách sửa**:
  1. Kết hợp đồng thời 2 điều kiện: Header ∈ {`Approved`, `In Progress`} VÀ dòng không trống cuối cùng là `[PLAN_APPROVED]`.
  2. Khóa toàn bộ nội dung plan khi đã ở trạng thái duyệt (`[APPROVED_PLAN_LOCKED]`); muốn đổi scope phải chuyển sang `Superseded` rồi lập plan mới.
  3. Kiểm soát các thao tác ghi qua `detectApprovalTransition`, mô phỏng mọi diễn giải ghi file (`simulateWrite`) và fail closed nếu có bất thường.
  4. Cơ chế pending writes lock chống TOCTOU khi có nhiều thao tác ghi đồng thời.
- **Quy tắc phòng ngừa**: Trạng thái bảo mật không bao giờ chỉ phụ thuộc vào một token văn bản đơn lẻ; phải có máy trạng thái chuyển tiếp chặt chẽ và khóa bất biến sau khi phê duyệt.

### 2026-10-12: Reviewer CLI tiềm ẩn nguy cơ phá vỡ sandbox qua Git Bash

- **Triệu chứng**: Khi dùng Claude Code CLI với `--allowedTools`, cờ `--dangerously-skip-permissions` có thể bỏ qua toàn bộ rào cản phân quyền; Git Bash trên Windows tự động chuyển đổi path `/c/...` thành `C:/...`, vô hiệu hóa các deny-list dạng chuỗi.
- **Nguyên nhân gốc**: Bash trên Windows qua MSYS có bề mặt thực thi phức tạp và không thể kiểm soát an toàn qua deny-list chuỗi.
- **Cách sửa**:
  1. Gỡ bỏ hoàn toàn tool `Bash` và `PowerShell` khỏi Claude Reviewer CLI (`--disallowedTools Bash PowerShell mcp__*`).
  2. Chuyển sang `--permission-mode dontAsk` kết hợp `--settings` kiểm soát chặt chẽ.
  3. Toàn bộ bằng chứng thực thi lệnh được script thu thập độc lập vào `evidence.md` trước lượt review bằng danh sách lệnh cố định `EVIDENCE_COMMANDS`. Reviewer chỉ việc đọc file bằng chứng này.
- **Quy tắc phòng ngừa**: Agent reviewer không cần quyền thực thi shell trực tiếp; kiến trúc "thu thập bằng chứng trước" (pre-collected evidence) an toàn và đáng tin cậy hơn nhiều so với việc cấp quyền shell cho AI.

### 2026-10-12: Nhận diện lỗi quota Claude CLI không khớp chuỗi thực tế

- **Triệu chứng**: Khi Claude Code CLI hết quota phiên với thông báo `You've hit your session limit · resets 4:40am (Europe/Berlin)` và exit 1, script `run-claude.ps1` không nhận diện được lỗi quota, trả exit 1 (trùng với lỗi thiếu template) thay vì exit 42 để kích hoạt fallback `@reviewer`.
- **Nguyên nhân gốc**: Regex cũ tìm chuỗi `usage limit reached` hoặc `exceeded.*limit`, trong khi thông điệp thực tế của Anthropic dùng cụm `hit your session limit` và ký tự bullet `·`. Ngoài ra, việc quét regex trên cả output khi exit 0 dẫn đến dương tính giả nếu bài review bàn về rate limit của nguồn truyện.
- **Cách sửa**:
  1. Tách logic phân loại lỗi CLI vào module JS `review-run.js` (`classifyCliOutput`), dùng chung và test được bằng `node:test`.
  2. Bổ sung các pattern thực tế: `hit your (session|usage|weekly|daily) limit`, `limit · resets`, `API Error: 429|529`.
  3. Chỉ phân loại lỗi quota/auth khi `exitCode !== 0`. Khi exit 0, luôn trả kết quả `OK`. Mọi lỗi lạ khi exit ≠ 0 được phân loại là `UNKNOWN` (exit 45) để kích hoạt fallback an toàn.
- **Quy tắc phòng ngừa**: Phân tích lỗi CLI từ bên thứ ba phải dựa trên fixture chuỗi thực tế, kiểm tra mã thoát (exit code) trước khi quét nội dung văn bản, và luôn có phương án fallback an toàn cho các lỗi chưa biết.

## Scraper / Nguồn truyện

### 2026-10-13: Next.js App Router (RSC) không cung cấp đầy đủ dữ liệu trong HTML tĩnh

- **Triệu chứng**:
  1. Thẻ truyện trong trang tìm kiếm không hiển thị số chương vì text nằm trong `<span itemprop="dateModified">Chương 483</span>` với format chữ trước số sau, không khớp regex `(\d+)\s*chương`.
  2. Trang bộ lọc `/danh-sach/bo-loc` không trả về thẻ `<a href="/the-loai/...">` khi cào bằng Jsoup do client render qua JavaScript.
  3. Danh sách chương trên trang chi tiết truyện Next.js chỉ in 54 chương tĩnh (4 chương mới nhất và 50 chương đầu), không có link phân trang HTML, đồng thời không nhúng `novelId` vào DOM khiến việc phân trang qua API bị tê liệt (nhảy từ chương 50 sang 996..999).
- **Nguyên nhân gốc**: Các trang web hiện đại sử dụng React Server Components kết hợp Client Components thường chỉ render một phần khung HTML tĩnh tối thiểu (SSR) cho SEO, còn toàn bộ dữ liệu phong phú (danh sách thể loại, toàn bộ chương, ID thực thể) được tải ngầm qua các endpoint REST API nội bộ phía sau.
- **Cách sửa**:
  1. Bổ sung regex linh hoạt cả 2 chiều: `(?:Chương|Hồi|C)\s*(\d+)` và `(\d+)\s*chương`.
  2. Ưu tiên khai thác trực tiếp REST API của website (`/genres`, `/novels/{slug}`, `/novels/{novelId}/chapters`), luôn kết hợp cơ chế fallback an toàn sang HTML nếu API lỗi.
  3. Lấy trực tiếp `novelId` từ API `/novels/{slug}` và cache vào bộ nhớ thay vì dựa vào regex parse DOM HTML.
- **Quy tắc phòng ngừa**: Khi khảo sát website mới dùng Next.js/Nuxt, luôn ưu tiên kiểm tra Network Tab để tìm các REST/JSON API endpoint trước khi viết selector Jsoup HTML; không giả định rằng HTML tĩnh chứa trọn vẹn 100% dữ liệu.

### 2026-10-14: Container nội dung chương dùng `<br><br>` kết hợp footer thông báo lỗi bọc trong `<p>` làm mất toàn bộ nội dung chương

- **Triệu chứng**: Nhiều truyện trên `sstruyen.pics` (ví dụ `an-giau-o-hogwarts-lam-giao-su-chuong-1-9744041`) khi mở trên ứng dụng không hiện nội dung chương mà chỉ hiện đúng một câu: *"Nếu truyện không hiện chữ thiệu nội dung hoặc bắt nhập mật khẩu hay đăng nhập hãy báo lỗi để bên mình sửa lỗi"*.
- **Nguyên nhân gốc**:
  1. Trong `<div class="wrapper prose ...">`, nội dung chương của nhiều truyện được trình bày bằng các text node trực tiếp ngăn cách bởi `<br><br>` thay vì thẻ `<p>`.
  2. Tuy nhiên, ở cuối container `div.prose`, giao diện `sstruyen.pics` luôn chèn thêm khối `<div class="text-center mt-8"><p>===...<br><p>Nếu truyện không hiện chữ...</p></div>`.
  3. Logic cũ ưu tiên `proseEl.select("p")` và chỉ fallback sang `cleanHtml(proseEl.html())` khi `paragraphs.isEmpty()`. Vì khối footer chứa thẻ `<p>`, danh sách `paragraphs` không bao giờ rỗng, khiến scraper chỉ trích xuất dòng thông báo lỗi ở footer (dài 113 ký tự, vượt qua ngưỡng `>= 50`) và vứt bỏ 100% nội dung chương nằm ngoài thẻ `<p>`.
- **Cách sửa**:
  1. Trong `SsTruyenScraper.getChapterContent`: Loại bỏ trước `div.text-center` và `adsSelectors` khỏi `proseEl`, chuyển đổi đồng nhất cả `<br>` và `<p>` thành ký tự xuống dòng `\n`, đồng thời lọc bỏ các dòng phân cách `===...` và câu boilerplate `"Nếu truyện không hiện chữ"` trong `cleanContentText`.
  2. Trong `StoryRepository.getChapterContent`: Bổ sung kiểm tra `isCorruptedBoilerplate` để tự động tải lại và ghi đè các chương đã lỡ lưu nhầm dòng thông báo lỗi vào Room DB.
- **Quy tắc phòng ngừa**: Không bao giờ giả định container nội dung chương chỉ dùng độc nhất `<p>` hoặc `<br>`; khi trích xuất văn bản từ container bài viết, phải loại bỏ các khối footer/boilerplate trước và luôn xử lý đồng thời cả text node trực tiếp (`<br>`) lẫn thẻ `<p>`.

### 2026-10-14: Thiếu `withContext(Dispatchers.IO)` trong `WikidichScraper` gây `NetworkOnMainThreadException` và chọn nhầm thẻ `.book-publisher` đầu tiên

- **Triệu chứng**: Khi chỉ bật nguồn Wikidich, cả màn hình Khám Phá và Tìm Kiếm đều không trả về bất kỳ truyện nào, đồng thời banner `"Wikidich yêu cầu đăng nhập"` cũng không hiển thị khi chưa đăng nhập.
- **Nguyên nhân gốc**:
  1. Trong `WikidichScraper.kt`, toàn bộ các hàm `suspend` gọi thẳng `BaseScraper.fetchDocument(url)` (vốn gọi đồng bộ `OkHttpClient.execute()`) mà không bọc trong `withContext(Dispatchers.IO)`. Khi gọi từ `viewModelScope` (`Dispatchers.Main`) trên Android, OkHttp lập tức ném `android.os.NetworkOnMainThreadException` trước khi gửi HTTP request. `StoryRepository` bắt `Exception` chung, trả về `emptyList()` và xóa cờ `loginRequired`.
  2. Trong mỗi thẻ `.book-item` của Wikidich có 2 thẻ `<p class="book-publisher">`: thẻ đầu tiên (`a[href^='/user/']`) là tên người đăng, thẻ thứ hai (`a[href*='status=']`) mới là trạng thái (`Hoàn thành` / `Còn tiếp`). Lệnh `item.selectFirst(".book-publisher a")` luôn lấy tên người đăng, khiến 100% truyện bị gán `status = "Đang ra"` và bị bộ lọc `completedOnly` loại bỏ sạch.
  3. `BaseScraper.cache` lưu trang HTML `"Đăng nhập để xem nội dung"` trước khi `checkLoginRequired` ném ngoại lệ, và `StoryRepository.cachedGenres` không làm mới khi người dùng đổi nguồn truyện trong Cài đặt.
- **Cách sửa**:
  1. Bọc toàn bộ các phương thức mạng của `WikidichScraper` trong `withContext(Dispatchers.IO)`, gọi `cache.clear()` trong `checkLoginRequired`, và sửa selector trạng thái thành `.book-publisher a[href*='status=']`.
  2. Trong `StoryRepository`: gắn `cachedGenreSourceIds` để tự động làm mới danh sách thể loại khi đổi nguồn và chỉ gọi `clearLoginRequired` khi kết quả trả về không rỗng.
- **Quy tắc phòng ngừa**: Mọi phương thức `suspend` trong subclass của `BaseScraper` gọi `fetchDocument` bắt buộc phải bọc trong `withContext(Dispatchers.IO)` (vì `runTest` trong unit test JVM không kích hoạt `NetworkOnMainThreadException` của Android).

### 2026-10-14: Hai chế độ tìm kiếm tách biệt trên `wikicv.org`, giới hạn tốc độ 12 giây của `/tim-kiem` và ảnh lazy-load `data:image/png;base64` trên SsTruyện

- **Triệu chứng**:
  1. Trên nguồn Wikidich (`wikicv.org`), tìm kiếm từ khóa hoạt động nhưng lọc theo thể loại (Giới Giải Trí, Khoa Huyễn, Tiên Hiệp...) luôn báo *"Không tìm thấy truyện nào trong thể loại..."* (logcat ghi nhận request mất ~12 giây và bị cắt tại mốc timeout 10 giây).
  2. Trên nguồn SsTruyện (`sstruyen.pics`), 6 truyện đầu tiên của mỗi trang danh sách có ảnh bìa, nhưng toàn bộ 24+ truyện còn lại mất ảnh bìa hoàn toàn và khi bấm vào trang chi tiết cũng không hiện ảnh bìa/ảnh review.
- **Nguyên nhân gốc**:
  1. Trên `wikicv.org/tim-kiem` có 2 chế độ form tìm kiếm tách biệt:
     - **Chế độ 1 (Tìm theo từ khóa — `#formQuickSearch`)**: chỉ nhận `?q=<keyword>&qs=1` (`https://wikicv.org/tim-kiem?q=hogwarts&qs=1`). Nếu truyền `q=<keyword>` vào form lọc nâng cao phía trên, server không tìm theo từ khóa.
     - **Chế độ 2 (Tìm theo bộ lọc tag/thể loại/tình trạng/độ dài)**: form gửi đầy đủ `?qs=1&status=5794f03dd7ced228f4419191&<tag/genre/age/gender>&tc=&tf=0&m=<tháng>&y=<năm>&q=`.
     - Ngoài ra, endpoint `/tim-kiem` trên `wikicv.org` có cơ chế điều tiết (rate-limit throttle) giữ kết nối đúng **~12.03 giây** khi có các request liên tiếp (ví dụ vừa mở tab Khám Phá gọi `completedStories` xong chuyển sang lọc thể loại, hoặc gọi `enrichWithChapterCounts` 5 trang chi tiết). Vì `StoryRepository` đặt `perSourceTimeoutMs = 10_000L` (10.0 giây), mọi request bị giữ 12.03 giây đều bị hủy ở giây thứ 10.0 trước khi nhận phản hồi `200 OK`.
  2. Trên `sstruyen.pics`, các thẻ `<img class="lazy">` từ vị trí thứ 7 trở đi gán `src="data:image/png;base64,..."` (không rỗng!) và đặt URL thật trong `data-src="https://file.sstruyen.pics/files/covers/..."`. Biểu thức `img.attr("abs:src").ifEmpty { img.attr("src") }.ifEmpty { img.attr("data-src") }` dừng lại ở `src`, sau đó `normalizeCoverUrl` ghép tiền tố CDN thành `https://file.sstruyen.pics/files/data:image/png;base64,...`, làm vô hiệu hóa điều kiện `!url.startsWith("data:")` trong `validateCoverUrl` và lưu URL hỏng vào Room DB.
- **Cách sửa**:
  1. Trong `WikidichScraper.kt` và `StoryRepository.kt`:
     - Tách bạch 2 chế độ: `search()` gọi `$baseUrl/tim-kiem?q=$encoded&qs=1`; `getStoriesByGenre()` gọi đúng cấu trúc form lọc `$baseUrl/tim-kiem?qs=1$statusParam&$filterParam&tc=&tf=0&m=$month&y=$year&q=$startParam`; `getCompletedStories()` chuyển sang `$baseUrl/bang-xep-hang?so=5$startParam` ("Mới hoàn thành", phản hồi ~1.3s và không chạm hạn mức của `/tim-kiem`).
     - Trong `StoryRepository`: nâng `timeoutFor(wikidich)` lên `25_000L` (25s) và chỉ tải 1 trang/lượt (`sourcePagesFor`) để không bao giờ bị cắt ngang ở giây thứ 10 khi server `wikicv.org` điều tiết 12.03s.
  2. Trong `SsTruyenScraper.kt`, `StoryRepository.kt` và `StoryDetailViewModel.kt`: Tạo `extractImageSrc` ưu tiên `data-src` và loại bỏ mọi chuỗi bắt đầu bằng `data:` / chứa `base64` trước khi ghép CDN; chuẩn hóa `/covers/...` về CDN; làm sạch `coverImageUrl` hỏng trong cache Room và lắng nghe `observeStory(story.id)` trên màn hình chi tiết.
- **Quy tắc phòng ngừa**:
  1. Khi lấy URL ảnh từ thẻ `<img>` bằng Jsoup, luôn kiểm tra `data-src` / `data-lazy-src` trước `src`, và phải kiểm tra loại trừ `data:` / `base64` TRƯỚC khi nối base URL/CDN.
  2. Khi tích hợp nguồn có cơ chế throttle phía server (như `wikicv.org` giữ request 12s trên `/tim-kiem`), phải đo `time_total` của các request liên tiếp bằng `curl.exe -w`, tách các luồng duyệt trang chủ (`getCompletedStories`) ra khỏi endpoint tìm kiếm, và đặt timeout của Repository cao hơn thời gian throttle của server.

## Room / Dữ liệu

### 2026-10-14: Chữ ký API thực tế của Room 3.0.3 (`androidx.room3`) cho Migration, Constructor và `withWriteTransaction`

- **Triệu chứng**: Khi nâng cấp từ `androidx.room:2.8.4` lên `androidx.room3:3.0.3`:
  1. Override `onMigrate(connection: SQLiteConnection)` báo lỗi compiler `'onMigrate' overrides nothing`; class `androidx.room3.migration.Migration` yêu cầu implement `override suspend fun migrate(connection: SQLiteConnection)`.
  2. Hằng số `RoomWarnings.NO_DATABASE_CONSTRUCTOR` được đánh dấu `@ExperimentalRoomApi` (gây lỗi `OPT_IN_USAGE_ERROR`), trong khi `@Suppress("NO_DATABASE_CONSTRUCTOR")` bằng chuỗi literal hoạt động sạch sẽ mà không cần opt-in.
  3. Hàm mở rộng `RoomDatabase.withWriteTransaction` nhận tham số `suspend TransactionScope<R>.() -> R` thay vì `suspend () -> R`, nên khi bọc qua `DatabaseTransactionRunner` cần gọi `db.withWriteTransaction { block() }`.
- **Nguyên nhân gốc**: Tài liệu tổng quan KMP và bytecode thực tế của `androidx.room3:room3-runtime:3.0.3` có khác biệt nhỏ về tên phương thức (`migrate` thay vì `onMigrate`) và receiver scope của hàm giao dịch.
- **Cách sửa**:
  1. Trong `AppDatabase.kt`: Dùng `@Suppress("NO_DATABASE_CONSTRUCTOR")` kết hợp truyền factory `{ AppDatabase_Impl() }` vào `Room.databaseBuilder` và implement `override suspend fun migrate(connection: SQLiteConnection)`.
  2. Trong `AppModule.kt`: Bọc `db.withWriteTransaction { block() }` bên trong `DatabaseTransactionRunner`.
- **Quy tắc phòng ngừa**: Khi nâng cấp thư viện major version (như Room 3 KMP), luôn chạy cổng biên dịch `compileDebugKotlin` độc lập ngay sau phase DB để xác minh chữ ký bytecode thực tế trước khi viết tiếp các tầng khác.

### 2026-10-15: Lệch chuẩn `Story.id` giữa các Scraper và `StoryRepository` làm hỏng cache Offline của Wikidich và gây ghi đè dữ liệu khi trùng `slug`

- **Triệu chứng**:
  1. Truyện tải từ nguồn Wikidich không thể mở trang chi tiết (`StoryDetailScreen`) khi mất kết nối mạng dù đã tải đầy đủ chương vào Room DB.
  2. Khi mở cùng một bộ truyện có chung `slug` (ví dụ `pham-nhan-tu-tien`) ở cả Tàng Thư Viện và SsTruyện, bản ghi ở nguồn mở sau sẽ `@Upsert` ghi đè `sourceId`, `url` của truyện trong bảng `stories` và ghi đè toàn bộ URL chương của nguồn trước trong bảng `chapters`.
- **Nguyên nhân gốc**:
  1. `WikidichScraper` đặt `Story.id = "${sourceId}_$slug"` (`"wikidich_..."`), trong khi `TangThuVienScraper` và `SsTruyenScraper` đặt `Story.id = slug` trần (không có tiền tố nguồn), còn `StoryRepository.getStoryDetail(storyUrl)` chỉ dùng `extractStoryId(storyUrl)` cắt chuỗi sau dấu `/` cuối cùng để gọi `storyDao.getStoryById(storyId)`. Do đó với mọi truyện Wikidich, `storyDao.getStoryById(rawSlug)` luôn trả về `null`.
  2. Vì `stories.id` là khóa chính duy nhất của bảng `stories` và khóa ngoại `storyId` của bảng `chapters`/`reading_progress`, việc dùng `slug` trần không kèm `sourceId` khiến hai nguồn khác nhau có chung `slug` va chạm khóa chính trực tiếp.
- **Cách sửa**:
  1. Chuẩn hóa `Story.id` và `Chapter.storyId` thành `"${sourceId}_$slug"` đồng nhất trên cả `TangThuVienScraper`, `SsTruyenScraper` và `WikidichScraper`.
  2. Thêm `storyDao.getStoryByUrl(url)` và hàm `StoryRepository.findCachedStory(storyUrl)` tra cứu theo thứ tự: (a) khớp chính xác theo `url`, (b) khớp theo ID chuẩn `"${matchedSource.sourceId}_$rawSlug"`, và (c) fallback sang ID legacy `rawSlug` **chỉ khi** `legacyCached.sourceId == matchedSource.sourceId` (hoặc khi nguồn cũ đã gỡ như `truyencom`/`truyenfull`).
  3. Bảo toàn khóa chính `id = cached.id` / `existing.id` khi cập nhật metadata trong `StoryRepository.getStoryDetail`, `StoryRepository.refreshLibraryStories`, `StoryRepository.mergeAndInsertChapters` và `DownloadRepository.snapshotStoryMetadata` để không làm mất liên kết chương đã tải của dữ liệu cũ.
- **Quy tắc phòng ngừa**: Trong kiến trúc gộp nhiều nguồn truyện (multi-source aggregator), khóa chính của thực thể (`Story.id`, `Chapter.storyId`) bắt buộc phải mang tiền tố định danh nguồn (`"${sourceId}_$slug"`), và mọi truy vấn đọc cache từ URL phải đối chiếu `sourceId` hoặc `url` thay vì chỉ cắt `slug` từ đường dẫn.

## TTS / MediaSession

### 2026-10-14: Phân biệt trạng thái Pause lúc đang tải chương kế tiếp (`PausedDuringAutoAdvance`) với Pause khi chuyển chương thủ công

- **Triệu chứng**:
  1. Nếu chặn lệnh `play()` kế tiếp dựa trên điều kiện rộng `state != BufferingAutoAdvance` khi `setContentForAutoAdvance()` được gọi, mọi thao tác chuyển chương thủ công (`nextChapter()`, `prevChapter()` gọi `pause()` trước; `navigateToChapter()` gọi `stop()` trước) đều bị nuốt mất lệnh `play()`.
  2. Ngược lại, nếu chỉ kiểm tra ở `setContentForAutoAdvance()` mà không chặn ở đầu hàm `TtsService.play()`, lời gọi `ttsManager.play()` liền kề tại `ReaderViewModel.kt:455` sẽ kích hoạt phát lại ngay sau khi user vừa bấm Pause.
  3. Trong lúc `BufferingAutoAdvance`, `TtsState.isPlaying == false`, nên nút 1 chạm tai nghe và listener mất AudioFocus nếu kiểm tra `_state.value.isPlaying` sẽ không gọi `pause()`.
- **Nguyên nhân gốc**: Luồng auto-advance và luồng chuyển chương thủ công dùng chung `loadChapter(autoPlay = true)` (`setContentForAutoAdvance` + `play()`), đồng thời tầng UI (`TtsState.isPlaying = false`) và tầng MediaSession (`STATE_BUFFERING`) có trạng thái khác nhau trong cửa sổ 1–3s tải chương.
- **Cách sửa**:
  1. Bổ sung trạng thái `PausedDuringAutoAdvance` và cờ một lần `suppressNextPlay` trong `TtsPlaybackStateMachine`: chỉ vào `PausedDuringAutoAdvance` khi `pause()` xảy ra lúc đang `BufferingAutoAdvance`; khi `onAutoAdvanceContentLoaded()` chạy từ `PausedDuringAutoAdvance` thì bật `suppressNextPlay = true` để `onPlayRequested()` ở đầu `TtsService.play()` nuốt đúng một lệnh `play()` từ `ReaderViewModel.kt:455`.
  2. Đổi điều kiện kiểm tra ở 1 chạm tai nghe và AudioFocus loss sang `stateMachine.isActiveOrBuffering`.
- **Quy tắc phòng ngừa**: Mọi cơ chế chặn race condition bất đồng bộ phải gắn chặt với trạng thái nguồn cụ thể (`PausedDuringAutoAdvance`) và được kiểm chứng trên đúng chuỗi lời gọi thực tế từ ViewModel tới Service.

## Compose / UI

### 2026-10-15: Regex ngắt dòng trước dấu gạch nối trong `TextFormatHelper` làm đứt từ ghép và tên riêng (`Spider-Man` → `Spider\n-Man`)

- **Triệu chứng**: Khi bật tính năng Tự động format chương truyện (`autoFormatEnabled = true`), các từ ghép hoặc tên riêng có dấu gạch nối như `Spider-Man`, `X-Men`, `Ho-Oh`, `Porygon-Z`, `Hán-Việt` bị tự động ngắt dòng ngay trước dấu gạch nối (`Spider\n-Man`).
- **Nguyên nhân gốc**: Bước 7 trong `TextFormatHelper.formatChapter` dùng biểu thức `Regex("(?<!\n)\\s*([\\-–—]\\s*[\\p{L}])")` thay bằng `"\n$1"`. Vì `\s*` khớp cả trường hợp 0 khoảng trắng trước và sau dấu `-`, mọi dấu gạch nối nằm giữa hai chữ cái đều bị chèn ký tự xuống dòng `\n` phía trước. Ở lần sửa trước (2026-07-21), Bước 8 và Bước 9 được đổi từ `split("\n")` sang `split(Regex("\n\\s*\n"))` để tránh tách đoạn TTS, nhưng biểu thức thay thế ở Bước 7 vẫn còn nguyên nên ký tự `\n` đơn lẻ vẫn tồn tại trong đoạn văn hiển thị trên UI.
- **Cách sửa**: Gỡ bỏ quy tắc chèn `\n` trước dấu gạch nối `[\-–—]` trong `TextFormatHelper.kt` (các đoạn thoại bắt đầu bằng gạch đầu dòng từ HTML vốn đã nằm ở đoạn riêng và vẫn được bảo toàn ở bước kiểm tra đầu đoạn), đồng thời bổ sung `TextFormatHelperTest.kt`.
- **Quy tắc phòng ngừa**: Khi xử lý định dạng văn bản tự động bằng regex, không bao giờ dùng lượng từ `\s*` (0 hoặc nhiều khoảng trắng) để nhận diện dấu gạch đầu dòng; mọi thay đổi trên `TextFormatHelper` phải có unit test kiểm tra tên riêng có dấu gạch nối (`Spider-Man`, `Ho-Oh`) và từ viết tắt (`S.H.I.E.L.D`).

### 2026-10-19: Đặt `DisposableEffect` độ sáng bên trong `AnimatedVisibility`, mất `scrollPosition` do thứ tự `snapshotFlow`, và bẫy `(?<!\p{N})` bên trong lookahead `(?=...)` của `TextFormatHelper`

- **Triệu chứng**:
  1. Kéo thanh chỉnh độ sáng trong `ReaderSettingsPanel` rồi đóng bảng cài đặt làm màn hình lập tức quay về độ sáng hệ thống (`-1f`).
  2. Mở lại chương đang đọc dở luôn bị cuộn về đầu chương (`scrollPosition = 0`), và khi thoát màn hình đọc, `onCleared()` ghi đè `scrollPosition = 0` làm mất tiến độ cuộn vừa lưu.
  3. Khi mở chương đầu tiên, đôi khi quy tắc thay chữ tùy chỉnh hoặc `prefetchCount` chưa kịp áp dụng; chuyển chương liên tiếp nhanh gây nhảy ngược về chương cũ.
  4. Trong `TextFormatHelper`, biểu thức `\\.(?=\\p{L}|(?<!\\p{N})\\p{N})` vẫn chèn khoảng trắng vào giữa `3.14` (`3. 14`) và `12:30` (`12: 30`).
- **Nguyên nhân gốc**:
  1. State `brightness` và `DisposableEffect(Unit) { onDispose { lp.screenBrightness = -1f } }` được khai báo bên trong khối `AnimatedVisibility(visible = state.showSettings)`. Ngay khi đóng bảng cài đặt (`showSettings = false`), Composable con rời khỏi Composition tree và kích hoạt `onDispose`, xóa sạch độ sáng tùy chỉnh.
  2. `ReaderViewModel.loadChapter` và `onCleared()` luôn ghi cứng `scrollPosition = 0`, còn trên `ReaderScreen`, `snapshotFlow { listState.firstVisibleItemIndex }` chạy độc lập với việc khôi phục vị trí cuộn.
  3. Trong `ReaderViewModel.init`, `loadDefaultSettings()` và `loadChapter()` chạy trên 2 coroutine song song không đồng bộ hóa, và `loadChapter()` không hủy `Job` của lượt tải chương trước đó.
  4. Khi dùng negative lookbehind `(?<!\p{N})\p{N}` **bên trong** positive lookahead `(?=...)` đứng sau dấu `.` (hoặc `,`, `:`), con trỏ regex tại thời điểm kiểm tra lookbehind đang đứng ngay sau dấu `.`, nên ký tự liền trước nó chính là dấu `.` (không phải chữ số `\p{N}`), khiến `(?<!\p{N})` luôn đúng!
- **Cách sửa**:
  1. Đưa `brightness` (`rememberSaveable`), `LaunchedEffect` và `DisposableEffect` khôi phục `screenBrightness = -1f` ra cấp gốc của `ReaderScreen`.
  2. Duy trì `currentScrollPosition` và `initialScrollIndex` trong `ReaderViewModel`, khôi phục từ `ReadingProgress` khi cùng chương, và gộp `listState.scrollToItem(initialScrollIndex)` chạy đồng bộ trước `snapshotFlow` trong cùng một `LaunchedEffect(state.currentChapterNumber, state.isLoading)`.
  3. Dùng `CompletableDeferred<Unit>` (`settingsLoadedDeferred`) trong `try/finally` của `loadDefaultSettings()` để `loadChapter()` đợi cài đặt nạp xong, hủy `loadChapterJob?.cancel()` khi chuyển chương, và giữ `contentCache` thô để `reprocessCurrentChapter()` chạy trên `Dispatchers.Default`.
  4. Trong `TextFormatHelper`, sửa lookbehind bên trong lookahead thành `(?<!\\p{N}[,:])\\p{N}` và `(?<!\\p{N}\\.)\\p{N}` (kiểm tra lùi 2 ký tự bao gồm cả dấu câu), kết hợp mask `URL_OR_DOMAIN_REGEX`.
- **Quy tắc phòng ngừa**:
  1. Không bao giờ đặt `DisposableEffect` quản lý tài nguyên cấp Activity/Window (như độ sáng màn hình, giữ sáng màn hình, hướng màn hình) bên trong một panel/dialog con có vòng đời ẩn/hiện (`AnimatedVisibility`); phải đặt ở Composable gốc của Screen.
  2. Khi viết lookbehind `(?<!...)` nằm bên trong lookahead `(?=...)` sau một ký tự K, biểu thức lookbehind phải tính cả ký tự K (`(?<!\p{N}K)`), vì vị trí neo của lookahead nằm ở phía sau K.

### 2026-10-19: Vòng lặp `loadMore()` vô tận khi trùng kết quả, lỗi `KotlinNothingValueException` khi collect `StateFlow` mock, và bẫy kiểm tra tên miền `endsWith` không có dấu chấm

- **Triệu chứng**:
  1. Trên `ExploreScreen` và `SearchScreen`, khi cuộn đến trang cuối cùng mà nguồn trả về danh sách trùng với trang trước hoặc bị lỗi mạng, `LaunchedEffect(shouldLoadMore)` kích hoạt `loadMore()` liên tục trong vòng lặp vô hạn.
  2. Trên `HomeScreen`, biểu đồ 7 ngày gán nhãn tĩnh `T2..CN` khiến cột hôm nay luôn bị gắn nhãn `CN` (sai thứ ở 6/7 ngày); chọn bộ lọc `"Chưa xong"` khi đã đọc hết các truyện làm biến mất cả hàng `FilterChip`.
  3. Trên `SettingsScreen`, biểu thức `enabled = isEnabled || state.enabledSources.size < ALL_SOURCES.size` luôn bằng `true` khi công tắc đang bật (`isEnabled == true`), cho phép tắt nốt nguồn cuối cùng.
  4. Kiểm tra tên miền WebView bằng `host.endsWith(domain)` hoặc `host.contains(domain)` cho phép tên miền giả mạo kiểu `evilwikicv.org` vượt qua bộ lọc.
  5. Trong Unit Test, khi ViewModel gọi `authRepository.getAuthState(id).collect { ... }` trên một mock `mockk(relaxed = true)` chưa stub `getAuthState`, MockK ném `kotlin.KotlinNothingValueException`.
- **Nguyên nhân gốc**:
  1. `canLoadMore` chỉ kiểm tra `results.isNotEmpty()` của trang vừa tải mà không kiểm tra `merged.size > currentStories.size` sau khi loại trùng (`deduplicateStories`), và không đặt `canLoadMore = false` trong khối `catch`.
  2. `HomeScreen` dùng danh sách nhãn thứ cố định thay vì tính động từ `Calendar.DAY_OF_WEEK` của từng ngày trong cửa sổ 7 ngày trượt, và gộp điều kiện hiển thị `FilterChip` chung với `recentlyRead.isNotEmpty()`.
  3. Biểu thức De Morgan cho công tắc nguồn bị viết ngược (`isEnabled || ...` thay vì `!isEnabled || state.enabledSources.size > 1`).
  4. `String.endsWith("wikicv.org")` khớp với mọi chuỗi kết thúc bằng `"wikicv.org"` bất kể ký tự đứng trước có phải là dấu chấm phân tách subdomain hay không.
  5. Phương thức `StateFlow.collect(...)` trong `kotlinx.coroutines.flow` có kiểu trả về là `Nothing`. Khi gọi `.collect` trên một đối tượng `StateFlow` do MockK tạo tự động (`relaxed = true`), MockK cố gắng tạo giá trị trả về cho kiểu `Nothing` và ném `KotlinNothingValueException`.
- **Cách sửa**:
  1. Chỉ giữ `canLoadMore = results.isNotEmpty() && merged.size > currentStories.size` và đặt `canLoadMore = false` khi `loadMore` gặp lỗi.
  2. Tính `DayChapterStat(label, chapters, isToday)` động trong `HomeViewModel` và tách `hasReadingHistory: StateFlow<Boolean>` khỏi `recentlyRead`.
  3. Sửa điều kiện `Switch` thành `enabled = !isEnabled || state.enabledSources.size > 1` và chuẩn hóa `.intersect(ALL_SOURCES).ifEmpty { ALL_SOURCES }`.
  4. Dùng `WebViewContentLoader.isAllowedDomain(host, allowedDomains)` kiểm tra `normalizedHost == domain || normalizedHost.endsWith(".$domain")`, đồng thời dọn dẹp `WebView` trong khối `finally` của `withTimeoutOrNull`.
  5. Trong `@BeforeEach` của unit test có mock interface trả về `StateFlow`/`SharedFlow`, luôn stub rõ `every { mock.getStateFlow(...) } returns MutableStateFlow(...)`.
- **Quy tắc phòng ngừa**:
  1. Mọi logic phân trang vô tận (`canLoadMore`) bắt buộc phải kiểm tra số lượng phần tử thực sự tăng lên sau khi gộp/loại trùng (`merged.size > previous.size`), không chỉ kiểm tra trang mạng trả về khác rỗng.
  2. Kiểm tra tên miền (host allowlist) bắt buộc phải so khớp `host == domain || host.endsWith(".$domain")`, tuyệt đối không dùng `contains()` hay `endsWith(domain)` thiếu dấu chấm.
  3. Không bao giờ để MockK tự sinh `relaxed` mock cho kiểu trả về `StateFlow`/`SharedFlow` nếu code sản phẩm gọi `.collect` trực tiếp trên đó; luôn stub trả về `MutableStateFlow` thật.

## Coroutines / Lifecycle

### 2026-10-16: Khắc phục 6 nhóm lỗi Critical/High về Sao lưu Streaming, Room `ON DELETE CASCADE`, Chiếu nhẹ `CursorWindow`, `DownloadService` Token/Timeout, TTS `onDone` Race và `CancellationException`

- **Triệu chứng**:
  1. Xuất/nhập bản sao lưu thư viện lớn có nguy cơ `OutOfMemoryError`; khôi phục bản sao lưu làm sập `ReaderViewModel` / `SettingsViewModel` với `ClassCastException: java.lang.Float cannot be cast to java.lang.Integer` tại khóa `auto_scroll_speed`, đồng thời làm mất các chương đã tải trên máy nếu bản sao lưu cũ hơn.
  2. Chuỗi ngày đọc liên tiếp (`getReadingStreak`) trả về `0` vào buổi sáng khi hôm nay chưa đọc dù hôm qua vẫn đọc liên tục.
  3. Mở truyện có hàng nghìn chương đã tải gây giật lag hoặc lỗi `CursorWindow` 2 MB; tải lại danh sách chương thực hiện hàng nghìn lệnh `UPDATE` đơn lẻ.
  4. Hủy tải rồi bấm tải truyện mới ngay lập tức trên `DownloadService` làm lượt tải mới tự tắt; chạy nền quá hạn `dataSync` trên Android 15/16 có nguy cơ crash `ForegroundServiceDidNotStopInTimeException`.
  5. Bấm Pause hoặc chuyển đoạn trên `TtsService` đôi khi vẫn tự nhảy sang đoạn kế tiếp do callback `onDone` của câu vừa bị `tts.stop()` gửi trễ.
  6. Hủy coroutine khi chuyển màn hình bị `Exception.toScraperException()` và `ScraperCircuitBreaker.execute()` bắt nhầm, làm tăng bộ đếm lỗi của Circuit Breaker và hiện thông báo lỗi giả.
- **Nguyên nhân gốc**:
  1. `BackupRepository` tích trữ toàn bộ nội dung chương vào `contentMap` trong RAM; `importBackup` đoán kiểu cài đặt bằng `key.contains("speed")` (gán `Float` cho `AUTO_SCROLL_SPEED` vốn là `Int`) và dùng `storyDao.insertStories` (`OnConflictStrategy.REPLACE` / `@Upsert`) kích hoạt `FOREIGN KEY ... ON DELETE CASCADE` xóa sạch các dòng `chapters` và `reading_progress` hiện có trên máy trước khi chèn lại.
  2. `ReadingStatsRepository.getReadingStreak()` dùng chỉ số vòng lặp `i` của danh sách `recent` làm số ngày lùi của `Calendar` sau khi `continue` bỏ qua ngày hôm nay.
  3. `ChapterDao.getChaptersByStory` / `getChaptersByStoryOnce` dùng `SELECT * FROM chapters`, kéo toàn bộ cột `content` dài hàng chục MB vào bộ nhớ mỗi khi Room phát Flow.
  4. Khối `finally` của `downloadJob` cũ trong `DownloadService` gọi `stopSelfCleanly()` vô điều kiện mà không đối chiếu token của lượt tải đang hoạt động.
  5. `UtteranceProgressListener.onDone(utteranceId)` không đối chiếu `utteranceId == "paragraph_$currentIndex"` và `_state.value.isPlaying`.
  6. `Exception.toScraperException()` và các khối `catch (e: Exception)` bắt cả `CancellationException` (subclass của `IllegalStateException`).
- **Cách sửa**:
  1. Chuyển `BackupRepository` sang streaming ZIP (`CountingNonClosingOutputStream`, `BoundedNonClosingInputStream`, spool `tempZipFile`, Pha 1 kiểm chứng ngoài DB transaction, Pha 2a dùng `insertStoryIgnore` + `updateStory` trong `withWriteTransaction`, Pha 2b ghi chương theo lô `<= 50` chương & `<= 8 MB` qua `restoreChapterContentIfEmpty`); khôi phục cài đặt theo bảng khóa tường minh `SettingsKeys.restoreSettingFromBackup` kèm `healCorruptedIntPreferences()` và `getIntSafe()`.
  2. Tính `getReadingStreak()` bằng `recent.associateBy { it.date }` và `isActiveDay()` (`totalReadingTimeMs > 0L || chaptersRead > 0`), đồng thời giữ overload không tham số `getReadingStreak()` để tương thích với MockK.
  3. Dùng chiếu nhẹ `NULL AS content` cho cả 5 truy vấn danh sách chương trong `ChapterDao`; bọc `mergeAndInsertChapters` trong `withWriteTransaction` và chỉ gọi `updateChapterMetadata` khi có thay đổi.
  4. Quản lý `activeDownloadToken` và `lastStartId` trong `DownloadService`, dùng `stopSelfResult(stopId)` khi kết thúc bình thường và `stopSelf()` trong `onTimeout` / lỗi `startForeground`.
  5. Thêm `TtsPlaybackStateMachine.shouldProcessUtteranceDone`, giải phóng tài nguyên trong `onError`, và ủy quyền `nextParagraph`/`prevParagraph` sang `seekAndPlay`.
  6. Rethrow `if (e is CancellationException) throw e` ở đầu `toScraperException()`, `ScraperCircuitBreaker.execute()`, các Scraper, Repository và ViewModel.
- **Quy tắc phòng ngừa**:
  1. Không bao giờ dùng `SELECT *` cho các truy vấn danh sách trên bảng chứa cột văn bản lớn (`TEXT` chương truyện); luôn dùng chiếu nhẹ `NULL AS content`.
  2. Trên bảng cha có `FOREIGN KEY ... ON DELETE CASCADE`, tuyệt đối không dùng `INSERT OR REPLACE` khi mục đích là hợp nhất (merge) dữ liệu sao lưu; phải dùng `INSERT OR IGNORE` kết hợp `UPDATE`.
  3. Mọi khối `catch (e: Exception)` hoặc hàm chuyển đổi ngoại lệ trong `suspend fun` bắt buộc phải kiểm tra `if (e is CancellationException) throw e` ở dòng đầu tiên.
  4. Khi thêm tham số mặc định (như `nowMillis: Long = System.currentTimeMillis()`) vào hàm `suspend` đang được MockK stub trong unit test, luôn giữ một hàm overload không tham số gọi sang hàm có tham số để tránh lệch giá trị mặc định tại thời điểm stubbing so với thời điểm gọi.

## Build / Gradle / CI

### 2026-10-19: Khắc phục Mirror Tests, Cổng Android Lint `abortOnError`, ProGuard `@Serializable`, Cleartext HTTP, Cache TTL `BaseScraper` và Xóa Cookie Đa Miền

- **Triệu chứng**:
  1. Một số Unit Test (`TtsManagerTest`, `NavigationUtilsTest`) định nghĩa lại hàm `private fun` bên trong file test thay vì gọi hàm thực tế của production code; `TangThuVienScraper` chưa có unit test `MockWebServer`; `TestJsoup.java` và `LacaTruyenApiTest.kt` gọi mạng thật hoặc chỉ in `println` mà không có assertion.
  2. `app/build.gradle.kts` đặt `lint { abortOnError = false }`, khiến các lỗi Android Lint (`ContextCastToActivity` trên `ReaderScreen.kt`, `NonObservableLocale` trên `SettingsScreen.kt`) không chặn build; trên Windows, `local.properties` chứa đường dẫn SDK có dấu `\` bị Lint bắt lỗi `PropertyEscape`.
  3. `AndroidManifest.xml` bật `android:usesCleartextTraffic="true"` trên toàn ứng dụng; một số dữ liệu truyện cũ trong Room DB hoặc HTML trả về URL ảnh bìa/chương `http://` sẽ bị chặn ngầm khi khóa cleartext nếu không tự động nâng cấp lên `https://`.
  4. `BaseScraper.responseCache` lưu kết quả HTML trong RAM suốt phiên chạy mà không có thời hạn (TTL), khiến danh sách chương mới hoặc trạng thái truyện sau khi đăng nhập bị cũ.
  5. `AndroidCookieStore.removeCookies` với `cookieNames.isEmpty()` chỉ ghi đè `name=` mà không kèm `Domain=.$domain`, nên các cookie phiên làm việc đặt ở cấp miền cha (như `.wikicv.org`) không bị xóa sạch khỏi `CookieManager`.
- **Nguyên nhân gốc**:
  1. Khi viết test ban đầu cho các hàm `internal`/`private`, người viết copy logic sang file test (Mirror Test) thay vì mở phạm vi `internal` trên hàm production; `TangThuVienScraper` hardcode hằng số `BASE_URL` nên khó trỏ vào `MockWebServer`.
  2. `abortOnError = false` được tắt tạm thời khi khởi tạo project nhưng chưa được bật lại kèm cấu hình `app/lint.xml` cho `PropertyEscape` trên Windows.
  3. Chưa có cơ chế tự động chuẩn hóa `http://` → `https://` ở tầng Scraper (`validateCoverUrl`, `fixUrl`) và Repository (`upgradeToHttps`).
  4. `CacheEntry` trong `BaseScraper` chưa lưu `timestamp: Long` để kiểm tra hạn sử dụng.
  5. `CookieManager.setCookie` của Android WebView phân biệt cookie host-only và domain cookie có thuộc tính `Domain=.<host>`.
- **Cách sửa**:
  1. Thêm `customBaseUrl: String? = null` vào `TangThuVienScraper` và tạo `TangThuVienScraperTest` (5 `@Test` `MockWebServer`); xóa sạch các hàm mirror trong `TtsManagerTest` và `NavigationUtilsTest` để gọi trực tiếp hàm `internal` của production code; chuyển `TestJsoup` và `LacaTruyenApiTest` (`BaseScraperCacheTest`) sang offline fixture / `MockWebServer` với assertions đầy đủ; xóa file mã chết `CryptoJS.kt`.
  2. Tạo `app/lint.xml` bỏ qua `PropertyEscape` cho `local.properties`, bật `abortOnError = true` trong `app/build.gradle.kts`, dùng `LocalActivity.current` trong `ReaderScreen.kt` và `Locale.ROOT` trong `SettingsScreen.kt`; thu hẹp rule ProGuard Jsoup và giữ `@Serializable class com.personal.apptruyen.data.model.**`.
  3. Đặt `android:usesCleartextTraffic="false"` trong `AndroidManifest.xml` và tự động nâng cấp `http://` → `https://` tại `BaseScraper.validateCoverUrl`, `WikidichScraper.fixUrl` và `StoryRepository.upgradeToHttps`.
  4. Thêm `CACHE_TTL_MS = 10 * 60 * 1000L` và `nowMs()` vào `BaseScraper.kt` để tự động loại bỏ bản ghi cache quá 10 phút.
  5. Trong `AndroidCookieStore.removeCookies`, chuẩn hóa `normalizedUrl`, xóa cả bản ghi host-only lẫn `Domain=.$domain` khi `cookieNames.isEmpty()`, và luôn gọi `cookieManager.flush()`.
- **Quy tắc phòng ngừa**:
  1. Tuyệt đối cấm viết "Mirror Test" (sao chép hàm từ `src/main` sang `src/test`); mọi unit test phải gọi trực tiếp hàm/lớp thực tế của production code và không được gọi mạng thật.
  2. Luôn duy trì `lint { abortOnError = true }` trong `app/build.gradle.kts` và kiểm chứng bằng `.\gradlew.bat lintDebug` trước khi hoàn tất.
  3. Khi giữ `android:usesCleartextTraffic="false"`, mọi điểm tiếp nhận URL từ HTML/DB (ảnh bìa, URL truyện, URL chương) phải có bước nâng cấp `http://` → `https://` cho các nguồn hỗ trợ HTTPS.

