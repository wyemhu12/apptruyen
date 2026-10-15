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


## Coroutines / Lifecycle

_Chưa có entry._

## Build / Gradle / CI

_Chưa có entry._
