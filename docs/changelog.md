# Changelog

Tất cả thay đổi đáng chú ý của dự án.

Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)

## [Unreleased]

## [v3.0.1] - 2026-10-15

### Fix bug

- **Khắc phục lỗi tự động ngắt dòng ở tên riêng và từ ghép có dấu gạch nối (`Spider-Man` → `Spider\n-Man`)**:
  - Gỡ bỏ quy tắc regex `(?<!\n)\s*([\-–—]\s*[\p{L}])` trong `TextFormatHelper.formatChapter()` từng chèn ký tự xuống dòng `\n` trước mọi dấu gạch nối giữa từ (làm đứt các tên như `Spider-Man`, `X-Men`, `Ho-Oh`, `Porygon-Z`, `Hán-Việt` và câu có dấu gạch ngang giải thích), đồng thời vẫn giữ nguyên các đoạn hội thoại bắt đầu bằng dấu gạch đầu dòng ở đầu đoạn văn.
  - Bổ sung bộ unit test `TextFormatHelperTest.kt` (4 tests).

## [v3.0.0] - 2026-10-14

### Thêm mới

- **Từ điển Pokémon khi đọc truyện (Pokemon Image Hover / Lookup)**:
  - Tích hợp danh mục chuẩn hóa `app/src/main/assets/pokemon_pokedex.json` gồm trọn vẹn 1.025 loài Pokémon và 1.309 hình thái (Mặc định, Siêu Cấp/Mega, Gigamax, Alola, Galar, Hisui, Paldea...), giữ toàn bộ tên Việt (Romaji trên cổng Pokémon VN), Anh, Trung, Nhật từ script gốc và **bổ sung toàn diện tên Hán-Việt** (*Bì Khả Khâu, Tiểu Hỏa Long, Phun Hỏa Long, Diệu Oa Chủng, Kiệt Ni Quy, Thủy Tiễn Quy, Siêu Mộng, Mộng Huyễn, Liệt Không Tọa, Cố Lạp Đốn, Cái Âu Ca, Khoái Long, Bạo Lý Long, Cảnh Điển, Hồ Địa, Đại Nham Xà, Cự Kim Quái, Lộ Ca Lợi Áo, Sa Nại Đóa...*) cùng hàng trăm biến thể dịch/convert đồng nhân tiếng Việt (*Siêu Cấp ..., Siêu Cực Cự Hóa ..., ... A La Lạp*).
  - Xây dựng bộ nhận diện `PokemonMatcher` (`PokemonLookupHelper.kt`, `PokemonRepository.kt`) dùng cấu trúc **Trie ưu tiên từ dài nhất (Longest-Match-First)** bất biến và thread-safe trên `Dispatchers.Default`, kiểm tra ranh giới từ (`isWordBoundary`) và danh sách loại trừ từ đơn dễ gây dương tính giả (`EXCLUDED_COMMON_ALIASES`), chỉ áp dụng ở tầng hiển thị UI mà không làm thay đổi văn bản gốc gửi sang `TtsManager`/`TtsService`.
  - Tạo `ReaderParagraphItem` và `PokemonLookupDialog` (`ReaderPokemonSupport.kt`, `PokemonTypeColors.kt`): tô màu nổi bật kèm đường gạch chân nét đứt (`drawBehind`) cho tên Pokémon trong đoạn văn; chạm vào tên Pokémon mở hộp thoại xem ảnh chính thức (qua Coil 3 `SubcomposeAsyncImage`), huy hiệu 18 hệ tiếng Việt chuẩn màu, chip chuyển đổi hình thái và thanh chuyển đổi ngôn ngữ (`Hán-Việt`, `Việt / Romaji`, `Trung`, `Anh`); chạm vào chữ thường vẫn ẩn/hiện thanh công cụ (`onToggleBars`), nhấn giữ vẫn phát TTS (`onSeekAndPlay`) và chế độ Copy (`SelectionContainer`) vẫn hoạt động nguyên vẹn.
  - Mặc định **BẬT (`true`)** qua khóa `SettingsKeys.POKEMON_LOOKUP_ENABLED`, đồng bộ công tắc `"Từ điển Pokémon"` trong cả `ReaderSettingsPanel` và `SettingsScreen`.
  - Bổ sung 9 unit tests trong `PokemonLookupHelperTest.kt` (TC-01..TC-06) và `ReaderViewModelTest.kt` (TC-07..TC-09).

### Fix bug

- **Khắc phục lỗi lọc theo thể loại và tìm kiếm trên nguồn Wikidich (`wikicv.org`)**:
  - Phân tách rõ 2 chế độ tìm kiếm của `wikicv.org`:
    * Chế độ 1 (`search` theo từ khóa): gọi `$baseUrl/tim-kiem?q=$encoded&qs=1` và lọc `completedOnly` ở tầng client (tránh truyền kèm tham số bộ lọc làm hỏng tìm kiếm từ khóa).
    * Chế độ 2 (`getStoriesByGenre` lọc theo tag/thể loại): gọi đúng cấu trúc form bộ lọc nâng cao `$baseUrl/tim-kiem?qs=1$statusParam&$filterParam&tc=&tf=0&m=$month&y=$year&q=$startParam` với đầy đủ tham số `status`, `tag`/`genre`/`age`/`gender`/`official`/`ending`, `tc=&tf=0&m=...&y=...&q=` và phân trang `&start=${(page - 1) * 20}`.
  - Chuyển `getCompletedStories` sang `$baseUrl/bang-xep-hang?so=5$startParam` ("Mới hoàn thành", phản hồi ~1.3s) để không chiếm hạn mức điều tiết (rate-limit) của endpoint `/tim-kiem`.
  - Trong `StoryRepository`: nâng `timeoutFor(wikidich)` lên `25_000L` (25s) và chỉ tải 1 trang/lượt (`sourcePagesFor`) để không bị cắt ngang ở giây thứ 10 khi `wikicv.org` áp dụng độ trễ điều tiết ~12.03s giữa các request `/tim-kiem` liên tiếp.
- **Khắc phục lỗi mất ảnh bìa và ảnh review trên nguồn SsTruyện (`sstruyen.pics`)**:
  - Bổ sung `extractImageSrc` ưu tiên đọc `data-src` / `data-lazy-src` / `data-original` và loại bỏ chuỗi placeholder 1×1px `data:image/png;base64,...` trong các thẻ `<img class="lazy">` (từng khiến 80% truyện từ mục thứ 7 trở đi trên trang tìm kiếm/bộ lọc bị ghép nhầm thành URL lỗi `https://file.sstruyen.pics/files/data:image/png;base64,...`).
  - Chuẩn hóa đường dẫn `/covers/...` có dấu gạch chéo đầu từ REST API về đúng CDN `https://file.sstruyen.pics/files/covers/...` và tự động fallback lấy `coverUrl` từ `api.sstruyen.pics/novels/{slug}` trong `getStoryDetail`.
  - Lắng nghe `repository.observeStory(story.id)` trong `StoryDetailViewModel` và làm sạch `coverImageUrl` hỏng trong cache Room (`StoryRepository.getStoryDetail`), kèm giao diện fallback chữ cái đầu trong `HeroSection` (`StoryDetailScreen`).

### Sửa đổi

- **Cập nhật hệ thống quản trị Agent (`.agents/`)**:
  - Bổ sung quy tắc bắt buộc trong `.agents/rules/00-core.md` và `.agents/rules/02-quality.md`: chỉ chạy `.\gradlew.bat assembleRelease` để xuất file APK release khi User yêu cầu rõ ràng.
  - Đồng bộ `Nguyên Tắc Thẩm Định Độc Lập`, `Luật Bằng Chứng Bổ Sung`, mục `Phát Hiện Mới` và `Các Tấn Công Đã Thử (Attempted Attack Vectors)` (tối thiểu 3 kịch bản phản biện) từ dự án Vikini sang `.agents/agents/reviewer/agent.md` và `.agents/scripts/claude-reviewer-prompt.md`.
- **Tối ưu tốc độ tải chương truyện song song theo lô (Parallel Batch Download with Ordered Persistence)**:
  - Chuyển `DownloadRepository.downloadChapters` từ tải tuần tự từng chương sang tải song song theo lô `DOWNLOAD_BATCH_SIZE = 4` chương/lô (`coroutineScope` + `async`/`awaitAll`).
  - Đảm bảo thứ tự chương tuyệt đối: chuẩn hóa đầu vào `chapters.sortedBy { it.chapterNumber }` và sắp xếp kết quả của mỗi lô theo `outcome.chapter.chapterNumber` tăng dần trước khi ghi tuần tự vào Room DB (`saveChapterContent`, `updateChapterMetadata`) và phát tiến độ `DownloadProgress`.
  - Chuyển thời gian chờ điều tiết từ sau mỗi chương (500ms/chương) sang giữa các lô (`BASE_BATCH_DELAY_MS = 250L`, nhân đôi lên tối đa `3000L` khi có lỗi trong lô), giảm ~8x thời gian chờ điều tiết.
  - Sửa lỗi nuốt `CancellationException` trong vòng lặp retry, bổ sung cơ chế dừng sớm (Fail-Fast) khi gặp `LoginRequiredException` hoặc khi 3 lô liên tiếp thất bại hoàn toàn (`MAX_CONSECUTIVE_FAILED_BATCHES = 3`), và phân mảnh truy vấn `getChaptersByNumbers` theo `SQLITE_MAX_BIND_ARGS = 900`.
  - Cập nhật `DownloadService` bảo toàn số chương đã xử lý (`lastProgress`) khi dừng giữa chừng và hiển thị thông báo tiếng Việt nhắc đăng nhập lại khi phiên nguồn truyện hết hạn (`buildCompletionText`).
  - Bổ sung 8 unit tests trong `DownloadRepositoryTest.kt` và 3 unit tests trong `DownloadServiceTest.kt`.
- **Nâng cấp kiến trúc lớn (Media3, Room 3, Kotlin 2.4, AGP 9.4)**:
  - **Hạ tầng âm thanh TTS (`androidx.media3:media3-session:1.11.1`)**:
    * Chuyển đổi `TtsService` từ `MediaSessionCompat` (deprecated) sang `MediaSessionService` kết hợp `TtsPlayer` (`SimpleBasePlayer`) và `DefaultMediaNotificationProvider`.
    * Tạo `TtsPlaybackStateMachine` thuần Kotlin quản lý trạng thái phát, timer timeout auto-advance 10s duy nhất, trạng thái `PausedDuringAutoAdvance`, cờ một lần `suppressNextPlay` (`onPlayRequested()`) và thuộc tính `isActiveOrBuffering` để chống race condition khi tạm dừng lúc tải chương mới mà vẫn bảo toàn 100% thao tác chuyển chương thủ công (Next/Prev, chọn chương, 2-chạm/3-chạm tai nghe).
    * Bảo mật kết nối `MediaSession.Callback.onConnect` và dual-binding `super.onBind(intent) ?: binder` với ép kiểu an toàn trong `TtsManager`.
  - **Room 3.0 KMP (`androidx.room3:3.0.3` + `androidx.sqlite:sqlite-bundled:2.7.1`)**:
    * Nâng cấp toàn bộ 5 Entity, 5 DAO và `AppDatabase` sang package `androidx.room3`, cấu hình plugin `id("androidx.room3")` với `room3 { schemaDirectory(...) }` và `.setDriver(BundledSQLiteDriver())`.
    * Chuyển đổi `MIGRATION_5_6`..`MIGRATION_8_9` sang `override suspend fun migrate(connection: SQLiteConnection)`.
    * Tách interface `DatabaseTransactionRunner` (`withWriteTransaction`) cho `DownloadRepository.deleteStory` đảm bảo xóa truyện nguyên tử và kiểm thử đơn vị sạch trên JUnit 5 JVM.
  - **Toolchain & Build System**:
    * Nâng cấp Kotlin lên `2.4.21` (đồng bộ Compose Compiler & Serialization plugins), Google KSP `2.3.12`, và `kotlinx-serialization-json:1.11.0`.
    * Nâng cấp Android Gradle Plugin (AGP) lên `9.4.0` và Gradle Wrapper lên `9.6.0`.
    * Bổ sung các bộ test mới: `TtsPlaybackStateTest.kt` (7 tests), `DownloadRepositoryTest.kt` (2 tests), và instrumented test `AppDatabaseMigrationTest.kt`.

## [v2.8.8] - 2026-10-13

### Fix bug

- **Khắc phục lỗi không tìm thấy truyện khi chỉ bật nguồn Wikidich (`wikicv.org`)**:
  - Bọc toàn bộ các phương thức mạng của `WikidichScraper` trong `withContext(Dispatchers.IO)`, khắc phục lỗi `NetworkOnMainThreadException` khi gọi từ `viewModelScope` khiến mọi truy vấn bị hủy trước khi gửi request.
  - Sửa selector trạng thái truyện trong `WikidichScraper.parseStoryList` thành `.book-publisher a[href*='status=']` (tránh lấy nhầm thẻ `.book-publisher` đầu tiên chứa tên người đăng khiến 100% truyện bị nhận nhầm là `"Đang ra"`).
  - Xóa cache bộ nhớ khi gặp trang yêu cầu đăng nhập (`checkLoginRequired`), tự động làm mới cache danh sách thể loại (`cachedGenreSourceIds`) khi bật/tắt nguồn trong Cài đặt, và mở rộng danh sách 24 thể loại Wikidich.
- **Khắc phục 4 lỗi vận hành nguồn SsTruyen (`sstruyen.pics`)**:
  - *Hiển thị nội dung chương dùng `<br><br>`*: Xử lý đồng nhất cả `<br>` và `<p>` trong `div.prose`, loại bỏ khối footer `div.text-center` chứa câu thông báo *"Nếu truyện không hiện chữ..."* từng khiến `proseEl.select("p")` bỏ sót toàn bộ nội dung chương nằm ngoài thẻ `<p>`. Đồng thời tự động phục hồi (re-fetch) nếu chương đã lưu offline trước đó bị dính nội dung thông báo lỗi này.
  - *Hiển thị số chương*: Bổ sung selector `span[itemprop=dateModified]` và regex `(?:Chương|Hồi|C)\s*(\d+)` giúp hiển thị badge số chương chính xác trên thẻ truyện `StoryCardPremium` khi tìm kiếm và duyệt danh mục.
  - *Bộ lọc thể loại*: Thay thế việc cào HTML SSR client-rendered bằng REST API chính thức `GET https://api.sstruyen.pics/genres`, tải trọn vẹn 93 thể loại tiếng Việt trong FilterBottomSheet, có fallback HTML.
  - *Phân trang danh sách chương*: Tích hợp API `GET https://api.sstruyen.pics/novels/{slug}` lấy trực tiếp `novelId` và `chapterCount`, phân trang đầy đủ `novels/{novelId}/chapters?page=...&limit=50&sort=asc`, chấm dứt hiện tượng nhảy cóc mất các chương ở giữa (từ chương 51 trở đi).

### Thêm mới

- **Nâng cấp nguồn SsTruyen sang `sstruyen.pics` (v2.8.4)**:
  - Chuyển đổi tên miền chính từ `https://sstruyen.net` sang `https://sstruyen.pics` (kho dữ liệu 58.265 truyện, 82 thể loại).
  - Tích hợp REST API danh sách chương `https://api.sstruyen.pics/novels/{novelId}/chapters?page={page}&limit=50&sort=asc` với cơ chế tự phục hồi cache `novelId` và gom lỗi trang thread-safe.
  - Cập nhật bộ lọc hoàn thành `status=completed` và thứ tự luôn mới nhất `sort=updatedAt` trên endpoint `/danh-sach/bo-loc`.
  - Cập nhật selector nội dung chương mới `div.prose`, `div.wrapper.prose` (xử lý đồng nhất cả `<p>` và `<br>`).
  - Hỗ trợ tương thích ngược tuyệt đối:
    * Khai báo `legacyHosts` trên `StorySource` và extension `matchesUrl` định tuyến host chính xác (hỗ trợ `sstruyen.net`).
    * Bảo toàn 100% tiêu đề (`title`) và nội dung chương đã tải offline trong Room DB khi cập nhật metadata (`mergeAndInsertChapters`).
    * Phân luồng đọc chương URL cũ `sstruyen.net` kế thừa 9 selectors và `cleanHtml` xử lý `<br>`.
  - Bổ sung bộ 15 unit tests với MockWebServer3 trong `SsTruyenScraperTest.kt` và 4 unit tests trong `StoryRepositoryTest.kt`.
- **Tích hợp nguồn truyện Wikidich (`wikicv.org`)**:
  - `WikidichScraper.kt` kế thừa `BaseScraper`, tự giải mã chữ ký động SHA-256 (`fuzzySign`) từ script trang web.
  - Phân trang AJAX danh sách chương (99 chương/trang) với điều kiện dừng chính xác theo thẻ DOM.
  - Hỗ trợ 46 thể loại truyện Wikidich và bộ lọc watermark bản quyền (`wikidich`, `nhúng`, `truyện được nhúng bởi`,...).
  - Badge nhận diện nguồn "WD" trên thẻ truyện `StoryCardPremium`.
- **Hạ tầng xác thực nguồn truyện (Source Authentication)**:
  - Kiến trúc phân lớp Clean Architecture: `ISourceAuthRepository` + `SourceAuthRepository` tại `data/repository/`, bind qua Hilt.
  - `SourceAuthManager` và `CookieStore`/`AndroidCookieStore` quản lý cookie host-only, probe kiểm tra phiên không mang cookie rò rỉ (`CookieJar.NO_COOKIES`).
  - Màn hình `SourceLoginScreen`: Đăng nhập web SSO qua diễn đàn Dịch Tiếng Hoa (`forum.dichtienghoa.com/dth/auth`) trong WebView với Referer `https://wikicv.org/`, kèm navigation guard chống chuyển hướng ngoài phạm vi.
  - Dialog `PasteCookieDialog`: Phương án dự phòng cho phép người dùng dán chuỗi cookie session (`express.sid`) copy từ trình duyệt máy tính.
  - Mục "Tài khoản nguồn truyện (Wikidich)" trong `SettingsScreen` quản lý trạng thái phiên, đăng nhập và đăng xuất an toàn.
  - Banner cảnh báo tiếng Việt trong `SearchScreen` và `ExploreScreen` khi nguồn yêu cầu đăng nhập (`LoginRequiredException`), cho phép bấm "Đăng nhập" trực tiếp hoặc "Đóng" mà không làm lỗi các nguồn khác trong luồng gộp kết quả.
  - Ngoại lệ `LoginRequiredException` trong cây lỗi `ScraperException`.
- **Bảo mật và sao lưu**: Bổ sung `data_extraction_rules.xml` và `backup_rules.xml` loại trừ thư mục dữ liệu WebView (`app_webview/`) khỏi Android Auto Backup.
- Hệ thống quản trị đa tác tử trong `.agents/`, adopt từ Project AURORA và Vikini rồi điều chỉnh cho Android/Kotlin:
  - Rules `00-core`, `01-coding`, `02-quality`, `03-ui`, `04-scraping` (thay rule song ngữ), `05-plan-review`, `06-docs`
  - Subagents `planner`, `reviewer` (kèm `allowlist`, `evidence-bar`, `mental-simulation` S1–S6 cho Room/Coroutines/TTS/scraper), `qa` (on-demand)
  - Dual-reviewer: Claude Code CLI (`scripts/run-claude.ps1`, exit 42 khi hết quota) + `@reviewer` dự phòng
  - Code Freeze Guard (`scripts/code-freeze-guard.js` + `hooks.json`): khóa `app/**` cho đến khi plan có `[PLAN_APPROVED]`; luôn hỏi khi sửa Room DB/schema, Manifest, cookie/auth, build/CI, test, keystore, `.agents/`; allowlist lệnh Gradle/git/curl read-only; Test Integrity Guard cho JUnit 5/MockK
  - Workflows `/debug`, `/post-fix`
- Khắc phục toàn bộ 19 phát hiện Audit của Claude Code CLI cho hệ thống governance `.agents/`:
  - Module `plan-approval.js`: máy trạng thái phê duyệt plan (Header + Thẻ `[PLAN_APPROVED]`), kiểm tra whitelist chuyển trạng thái, giải quyết chunks và mô phỏng ghi file, quản lý pending writes lock, CLI `status`.
  - Module `review-run.js`: thu thập snapshot và evidence cố định, render prompt độc lập theo thư mục lượt, phân loại lỗi CLI Claude Pro (AUTH trước QUOTA), xác minh append-only và tự động khôi phục snapshot khi lỗi/tampered.
  - Bộ 53 unit tests cho hệ thống governance: `code-freeze-guard.test.js` (31 tests), `plan-approval.test.js` (12 tests), `review-run.test.js` (10 tests).
- `docs/plans/README.md` (index + template plan), `docs/specs/`, `docs/lessons-learned.md`

### Sửa đổi

- Nâng cấp Code Freeze Guard và điều kiện mở khóa toàn cục:
  - Mở khóa khi không còn plan nào bị blocking VÀ có ít nhất một plan có header ∈ {`Approved`, `In Progress`} kèm dòng không trống cuối cùng là `[PLAN_APPROVED]`.
  - Khóa toàn bộ nội dung plan khi đã được duyệt (`[APPROVED_PLAN_LOCKED]`) để chống tự ý sửa đổi phạm vi task.
  - Khóa ghi đang treo chống TOCTOU khi có nhiều thao tác ghi đồng thời lên cùng một plan (`[CONCURRENT_PLAN_WRITE]`).
  - Phân loại quyết định hook: dùng `ask` thông thường (cho phép User xác nhận), chỉ dùng `force_ask` cho 2 lỗi nội bộ guard (`[GUARD_MODULE_LOAD_FAILED]`, `[PAYLOAD_UNREADABLE]`).
  - Token hóa lệnh PowerShell 5.1 và cấm toàn bộ ký tự ngoài ASCII in được (`[NON_ASCII_COMMAND]`) để ngăn chặn injection qua Unicode quotes/dashes/spaces.
  - Tự cho phép `curl.exe -q` chỉ tới 8 host nguồn truyện chính thức (D6), cấm redirect `-L` và các cờ ghi/đọc file; các lệnh mạng khác luôn hỏi User.
  - Giới hạn toán hạng Git và các cmdlet đọc file chỉ trong working tree của repo, chặn Windows device names (`NUL`, `CON`, `AUX`, `PRN`, `COM1-9`, `LPT1-9`) và đường dẫn vượt cấp `..`.
  - Sandbox cho Claude Reviewer CLI: gỡ bỏ tool `Bash` (`--disallowedTools Bash`), cung cấp bằng chứng lệnh qua `evidence.md`, hạn chế WebFetch tới 17 host chính thức và deny secrets.
- Gộp `.agent/` vào `.agents/`: `project-rules.md` chuyển vào `00-core`/`01-coding`, `update-docs.md` thành `06-docs.md`, workflow `/update-docs` chuyển sang `.agents/workflows/`
- Chuyển `docs/superpowers/plans|specs` sang `docs/plans/` và `docs/specs/`
- `AGENTS.md`, `docs/contributing.md`: trỏ sang hệ thống mới, thêm bước lập plan vào quy trình thêm tính năng
- **StoryRepository**: Bắt và cô lập `LoginRequiredException` trong tìm kiếm, khám phá và tải thể loại; chuyển sang gộp thể loại (union) thay vì giao (intersection) khi có nguồn cần đăng nhập; bảo toàn khả năng đọc offline cho truyện từ nguồn đã bị gỡ bỏ.
- **WebViewContentLoader**: Bổ sung `wikicv.org` và `wikicv.net` vào danh sách domain được phép tải nội dung.
- **SettingsDataStore**: Thêm `SourceIds` constants (`TANGTHUVIEN`, `SSTRUYEN`, `WIKIDICH`, `DEPRECATED_SOURCES`) và mapping nhãn tiếng Việt `SOURCE_LABELS`.

### Fix bug

- Khắc phục các lỗ hổng Code Freeze Guard:
  - Fix lỗ hổng tự phê duyệt và deadlock trạng thái (F01, F02, F11, N6, N7, N8, N9).
  - Fix các đường bypass lệnh qua subexpressions `iex`, pipe newlines, Unicode quotes/dashes, command flags, traversal, Windows devices `NUL` (F03, F04, F05, F06, F08, N1, N3, N4).
  - Fix lỗ hổng phân loại lỗi quota Claude CLI không nhận diện được chuỗi thật `You've hit your session limit` (M1).
  - Fix nguy cơ Claude CLI bypass quyền khi dùng `--dangerously-skip-permissions` bằng việc chuyển sang `--permission-mode dontAsk` và gỡ bỏ tool `Bash` (F09, N5).

### Xóa bỏ

- Thư mục `.agent/` và `docs/superpowers/`
- Gỡ bỏ 2 nguồn truyện trực tuyến: **TruyenFull** (`truyenfull.today`) và **TruyenCom** (`truyencom.com`) khỏi `ScraperModule.kt`, xóa `TruyenFullScraper.kt`, `TruyenComScraper.kt` và unit test tương ứng. Toàn bộ truyện và chương đã tải trước đó của 2 nguồn này trong Room Database vẫn được bảo toàn và đọc offline bình thường.

## [v2.8.7] - 2026-08-03

### Thêm mới
- **Bộ lọc Thể loại**: Khi bật đồng thời nhiều nguồn truyện, bộ lọc giờ đây sẽ tự động hiển thị danh sách thể loại là phần giao (intersection) của tất cả các nguồn đang bật, đảm bảo các thể loại hiển thị chắc chắn tồn tại trên mọi nguồn. Nếu chỉ bật một nguồn, sẽ hiển thị đầy đủ thể loại của riêng nguồn đó.
- **Tên chương TTV**: Thêm `ChapterContentResult` data class và `getChapterContentWithTitle()` trong `StorySource` interface để trả title thực khi scrape nội dung chương. Khi đọc/tải chương TTV, title được extract và tự động cập nhật vào DB — danh sách chương sẽ hiển thị tên đầy đủ thay vì chỉ "Chương X".

### Sửa đổi
- **Nâng cấp dependencies**: Gradle 9.4.1→9.5.1, AGP 9.2.0→9.3.0, Hilt 2.59.2→2.60.1. AGP 9.3.0 fix lỗi file handle leak trên Windows.

### Fix bug
- **Fix TTV `#số chương` hiển thị trong nội dung chương**: Loại bỏ prefix `#8 ` từ các paragraph và bỏ việc prepend titleEl vào đầu content — nội dung chương không còn dòng `#8 Chương 8: ...` thừa.
- **Fix TTV thiếu số chương trong danh sách**: Trang danh sách TTV (lọc, tìm kiếm, hoàn thành) không cung cấp số chương trong HTML. Thêm `enrichWithChapterCounts()` tự động fetch detail page song song (batch 5) để lấy `totalChapters` cho từng truyện.

## [v2.8.6] - 2026-07-21

### Fix bug

- Fix `TangThuVienScraper`: Sửa lỗi thiếu truyện trong kết quả tìm kiếm/lọc do selector quá khắt khe
- Fix `TangThuVienScraper.getGenreList()`: Sửa lỗi bộ lọc thể loại TTV chỉ hiện 7 tags (fallback) thay vì 80+ tags. Nguyên nhân: fetch từ trang chủ `/` (render client-side bằng Next.js, Jsoup không thấy genre links) → đổi sang fetch từ `/truyen` (SSR). Cập nhật fallback list đầy đủ.
- Fix `TextFormatHelper`: Bỏ tính năng tự động ngắt dòng ở dấu gạch ngang làm hỏng mạch đọc TTS


### Sửa đổi
- **Tàng Thư Viện**: Cập nhật scraper hỗ trợ giao diện React/Next.js mới (`tangthuvien.org`). Cập nhật logic lấy danh sách chương, tìm kiếm, lọc theo thể loại và tự động migrate URL truyện TTV cũ trong CSDL.


### Fix bug
- **Fix: Lỗi tự động thoát trang đăng nhập LacaTruyen**: Sửa lỗi `LoginWebViewScreen` nhận diện nhầm các cookie không liên quan (như của Cloudflare) thành cookie đăng nhập thành công, khiến app tự động quay về trang cài đặt trước khi user kịp đăng nhập.
- **Fix: Lỗi WebView trả về chữ loạn cho tài khoản đã đăng nhập**: Tinh chỉnh lại điều kiện trong `WebViewContentFetcher.kt` (`CHECK_READY_JS`) để kiểm tra chính xác các dấu hiệu chữ loạn (anagram) thông qua Regex `/[a-zà-ỹ][A-ZĂÂĐÊÔƠƯÀ-Ỹ]/`. Đảm bảo WebView đợi cho đến khi ReactJS giải mã hoàn tất trước khi bóc tách văn bản.
- **Fix: TTS đọc dấu ngoặc kép và dấu phân cách**: Thêm bộ lọc loại bỏ các dấu ngoặc kép (`"`, `“`, `”`) và các dòng phân cách dài (`---`, `___`, `+++`, `===`, `***`, `~~~`) trong `TtsTextPreprocessor.kt` để TTS không đọc lớn các ký tự này, giúp trải nghiệm nghe truyện mượt mà hơn (đặc biệt trong các đoạn hội thoại).
- **Fix: Chữ bị loạn ở nguồn LacaTruyen**: Sửa lỗi trả về sai fallback `previewHtml` (vốn đã bị mã hóa loạn chữ) khi luồng Native Decryption gặp thất bại trong `LacaTruyenScraper.kt`. Thay vì trả về `previewHtml`, giờ đây trả về `null` để ứng dụng chuyển sang phương án dự phòng bằng WebView, giúp hiển thị chữ chuẩn sau khi JS giải mã xong.
- **Fix: Tốc độ và cao độ TTS bị reset**: Tốc độ và cao độ đọc truyện điều chỉnh ở màn hình Reader không được lưu vào DataStore, dẫn đến bị reset về mặc định khi khởi động lại app. Khắc phục bằng cách ghi DataStore trong `ReaderViewModel` ngay khi user điều chỉnh tốc độ hoặc cao độ.

### Thêm mới
- **LacaTruyenScraper: Native Decryption** — Bổ sung thuật toán tự động giải mã nội dung chương của LacaTruyen (lacatruyen.buzz) trực tiếp bằng Kotlin. Bỏ qua sự phụ thuộc vào WebView, giúp load truyện nhanh chóng và khắc phục hoàn toàn lỗi nội dung truyện bị mã hóa hoặc hiển thị trắng.
- **Tài liệu: Reverse Engineering** — Thêm file `docs/scraping_lacatruyen_decryption.md` ghi lại chi tiết luồng giải mã nhiều lớp (Global Key, Dynamic Salt, Dynamic Key) của LacaTruyen làm tài liệu tham khảo.

## [v2.8.4] - 2026-06-15

### Thêm mới

- **Harness Engineering: Git version control** — Khởi tạo git repository cho project, cải thiện khả năng tracking history và rollback
- **Harness Engineering: ktlint** — Tích hợp ktlint v14.2.0 (jlleitschuh/ktlint-gradle plugin) làm hard guardrail cho code style enforcement
  - Cấu hình `.editorconfig` với rules phù hợp Android/Compose (disable wildcard-imports, function-naming cho @Composable)
  - Tất cả Kotlin source files đã pass ktlint check
- **Harness Engineering: Android Lint** — Cấu hình Android Lint block trong `build.gradle.kts` với HTML report output
- **Harness Engineering: Pre-commit hook** — Git pre-commit hook chạy ktlint check tự động trước mỗi commit
- **Harness Engineering: .gitignore** — File .gitignore toàn diện cho Android/Gradle/IDE/project-specific files
- **Harness Engineering: Timber logging** — Tích hợp Timber v5.0.1 cho structured logging. Migrate tất cả `android.util.Log` calls sang Timber (trừ CrashHandler do init order). DebugTree chỉ planted trong debug builds.
- **Harness Engineering: GitHub Actions CI** — Pipeline tự động: ktlint check → Android Lint → Unit Tests → Build Debug. Upload reports as artifacts.

### Sửa đổi

- **Thiết kế UI: Cập nhật App Icon (lần 3)** — Thay thế icon bằng bản mới từ folder `android/`:
  - Foreground: Book icon lớn hơn, căn giữa đẹp hơn (thay vì nhỏ + lệch góc)
  - Background: Nền tối có hiệu ứng sóng + sao lấp lánh (thay vì flat tối đơn giản)
  - Monochrome: Cập nhật tương ứng với foreground mới
  - Cập nhật tất cả 5 density: mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi + anydpi-v26

- **Thiết kế UI: Icon và Splash Screen (lần 2)** — Thay thế toàn bộ icon và splash screen bằng logo + hình nền mới do user cung cấp:
  - Xóa tất cả icon cũ (`ic_launcher`, `ic_launcher_foreground`, `ic_launcher_background`, `ic_launcher_monochrome`) ở mọi density
  - Xóa tất cả splash assets cũ (`splash_bg.png`, `ic_splash_logo.png`) ở mọi drawable density
  - Tạo mới app icon từ logo (neon book gradient tím-cyan) trên nền tối #0A0E17 cho 5 density: mdpi→xxxhdpi
  - Tạo mới adaptive icon (foreground transparent + background tối + monochrome trắng) cho 5 density
  - Tạo mới splash screen kết hợp logo centered trên background sóng vũ trụ cho drawable, drawable-xhdpi, drawable-xxxhdpi

- **Thiết kế UI: Icon và Splash Screen** — Làm mới giao diện khởi động theo phong cách cao cấp (premium dark mode):
  - Cập nhật icon ứng dụng (`ic_launcher`) thành biểu tượng cuốn sách/chữ T trừu tượng với dải màu gradient tím neon/cyan.
  - Thiết kế lại `SplashScreen.kt` với nền tối (obsidian), logo vector glowing gradient, và text màu xám xanh.
  - Thay đổi hệ màu shadow và glow effect để khớp với thiết kế hiện đại.

- **project-rules.md** — Thêm ktlint commands vào danh sách Gradle commands chuẩn, thêm lint rule vào quy tắc code
- **ScraperCircuitBreaker.kt** — Chuyển KDoc thành EOL comments trong constructor params để tránh xung đột ktlint rules
- **StoryEntity.kt** — Chuyển KDoc thành EOL comments trong data class params
- **TangThuVienScraper.kt** — Break long DESKTOP_UA constant xuống 2 dòng (max line length 150)
- **Toàn bộ Kotlin source** — Auto-format theo ktlint standards (trailing commas, line breaks, whitespace)

## [v2.8.3] - 2026-06-09

### Thêm mới

- **Tính năng Tự động format chương truyện**: Giúp chuẩn hóa văn bản của các chương có định dạng xấu. Bao gồm:
  - Tự động chuẩn hóa dấu chấm lửng (`...`) và loại bỏ chuỗi ký tự đặc biệt vô nghĩa.
  - Sửa lỗi khoảng trắng quanh dấu câu (xóa khoảng trắng thừa trước dấu phẩy/chấm, thêm khoảng trắng sau dấu câu).
  - Tách các câu thoại (nằm trong ngoặc kép hoặc gạch đầu dòng) ra dòng riêng.
  - Có thể bật/tắt trong màn hình Cài đặt đọc (ReaderSettingsPanel).

### Fix bug

- **Fix: Tính năng Tự động format ghi đè Thay chữ**: Đã đổi thứ tự xử lý để tính năng Tự động format chạy trước tính năng Thay chữ. Đồng thời, sửa lỗi thuật toán đếm câu để không cắt nhỏ các từ viết tắt như S.H.I.E.L.D, giúp người dùng tự do thay thế từ viết tắt.
- **Fix: Không ngắt dòng sau dấu !/? trong ngoặc kép**: Bỏ qua việc ngắt dòng sau các dấu này nếu nó nằm trong một câu thoại.

### Fix bug

- **Fix: Lỗi font/encoding tiếng Việt trong Reader**: 3 file (`ReaderScreen.kt`, `ChapterNavigation.kt`, `ChapterListPanel.kt`) bị save sai encoding (Latin-1 thay vì UTF-8) → text tiếng Việt hiển thị garbled (`Quay lá»‹i`, `Thá» lá»‹i`, `Đang tải nội dung...`). Fix: re-save tất cả 19 chuỗi tiếng Việt bị lỗi với đúng UTF-8 encoding
- **Fix: Tìm kiếm thiếu kết quả từ TruyenCom**: `applyLocalFilters()` lọc `completedOnly=true` loại bỏ toàn bộ kết quả TruyenCom vì search HTML không có status badge → `status=""` → bị filter. Fix: thêm `story.status.isBlank()` cho phép truyện chưa biết status lọt qua filter (chỉ TruyenCom search bị ảnh hưởng, TruyenFull đã fix gán `"Đang ra"` cho truyện không Full)

### Thêm mới

- **A1: Extract BaseScraper**: Refactor 4 scrapers để extend abstract `BaseScraper` class
  - Tạo `BaseScraper.kt` (294 dòng) chứa shared: fetchDocument, fetchDocumentWithRetry, cleanHtml, ensureAbsolute, LRU cache, extractChapterText, getChapterList, getChapterContent
  - `TruyenComScraper`: 775 → 585 dòng (-24%), giữ WebView fallback + JSON API
  - `TangThuVienScraper`: 496 → 418 dòng (-16%), giữ AJAX chapters + custom selectors
  - `SsTruyenScraper`: 597 → 454 dòng (-24%), dùng BaseScraper defaults
  - `TruyenFullScraper`: 567 → 420 dòng (-26%), override contentSelectors + adsSelectors
  - Tổng giảm: 2,435 → 2,171 dòng (gồm BaseScraper mới), fix 1 bug phải sửa 1 file thay vì 4
  - `WebViewContentLoader`: `TruyenComScraper.USER_AGENT` → `BaseScraper.DEFAULT_MOBILE_UA`

- **A3: Split StoryRepository**: Tách 772-dòng monolith thành 4 repositories
  - `StoryRepository` (core): 508 dòng — search, browse, detail, chapters, library, refresh
  - `DownloadRepository` (NEW): ~150 dòng — download, delete, stats (10 methods)
  - `TextReplacementRepository` (NEW): ~70 dòng — per-story + global replacement (5 methods)
  - `ReadingProgressRepository` (NEW): ~80 dòng — reading position, history (6 methods)
  - Tạo 4 interfaces: IDownloadRepository, ITextReplacementRepository, IReadingProgressRepository
  - Update RepositoryModule: 4 `@Binds` bindings
  - Update 6 consumers: DownloadsViewModel, HomeViewModel, StoryDetailViewModel, ReaderViewModel, DownloadService, ReadingStatsViewModel
  - Fix: DownloadService inject concrete → interface, ReadingStatsViewModel xóa unused injection
  - Update 5 test files phù hợp constructors mới

- **A4: MVI ReaderViewModel**: Thêm MVI pattern
  - `ReaderIntent` sealed interface: 20 intent types (navigation, settings, toggles, text replacement, scroll)
  - `onIntent()` dispatcher: single entry point routing tất cả user actions
  - ContentCache size limit: eviction khi cache > prefetchCount + 2
  - Update ReaderScreen: 28 direct calls → `onIntent(ReaderIntent.X)`

- **A5: Centralize Scraper Patterns**: Thêm shared helpers vào BaseScraper
  - `ChapterInfo.toChapter(storyId)`: eliminate 7 copy-paste `.map { Chapter(...) }`
  - `normalizeStatus(raw)`: centralize 4 scattered status normalization blocks
  - `validateCoverUrl(url)`: centralize ~6 inline cover URL checks
  - Update 4 scrapers dùng helpers mới

- **JUnit 5 Migration**: Migrate toàn bộ 14 test files (122 tests) từ JUnit 4 → JUnit 5 Jupiter
  - `junit:junit:4.13.2` → `org.junit.jupiter:junit-jupiter:5.11.4` + `junit-platform-launcher:1.11.4`
  - `@Before/@After` → `@BeforeEach/@AfterEach`
  - `Assert.*` → `Assertions.*` (param order: condition first, message last)
  - `@Test(expected=...)` → `assertThrows()`
  - `tasks.withType<Test> { useJUnitPlatform() }` trong build.gradle.kts

- **UI7: Haptic Feedback mở rộng**: Thêm rung nhẹ (haptic feedback) vào 3 màn hình:
  - **ReaderScreen**: `TextHandleMove` khi chuyển chương tiếp/trước (onPrevChapter/onNextChapter)
  - **HomeScreen**: `TextHandleMove` khi chuyển tab (Đang đọc ↔ Thống kê)
  - **DownloadsScreen**: `LongPress` khi xác nhận xóa truyện (cả "Xóa toàn bộ" và "Chỉ xóa tải")
- **UI9: Search Autocomplete**: Gợi ý tìm kiếm tự động khi gõ (từ lịch sử tìm kiếm)
  - `SearchViewModel.searchSuggestions`: StateFlow kết hợp query + recentSearches, lọc khi query ≥ 2 ký tự
  - `SearchScreen`: Dropdown gợi ý animated bên dưới search bar, click chọn gợi ý → điền + tìm kiếm
- **A7: IStoryRepository interface**: Extract interface cho StoryRepository (~40 methods)
  - Tất cả ViewModels inject `IStoryRepository` thay vì concrete class
  - `RepositoryModule.kt` (@Binds IStoryRepository → StoryRepository)
  - `DownloadProgress` chuyển thành top-level data class
- **UI8: Tablet Adaptive Layout**: WindowSizeClass support
  - Thêm `material3-window-size-class` dependency
  - `AppNavigation` nhận `WindowSizeClass`, dùng `NavigationRail` cho màn hình expanded/medium
  - Extract `AppNavContent` composable chung cho cả compact và expanded layout
- **UI3: M3 Expressive Shapes**: Nâng AppShapes lên Expressive style (small 12dp, medium 16dp, large 20dp, extraLarge 28dp)
- **A2: Unit Tests mới**: 6 tests cho SettingsViewModel, ReadingStatsViewModel, StoryDetailViewModel
- **LeakCanary**: Thêm debug dependency để detect memory leaks
- **F6: Auto-scroll Reader**: Tự cuộn trang khi đọc với tốc độ cấu hình 1-10x
  - `ReaderState`: +`autoScrollEnabled`, +`autoScrollSpeed` (mặc định 3)
  - `ReaderIntent`: +`ToggleAutoScroll`, +`SetAutoScrollSpeed`
  - `ReaderScreen`: LaunchedEffect auto-scroll (animateScrollBy 50ms tick), pause on tap, AnimatedVisibility indicator badge (top-end)
  - `ReaderSettingsPanel`: Section "TỰ CUỘN" với Switch toggle + Slider tốc độ
  - `SettingsDataStore`: +`AUTO_SCROLL_SPEED` IntPreferencesKey, persist qua DataStore

- **F4: Backup/Restore**: Sao lưu & khôi phục toàn bộ dữ liệu app
  - `BackupData.kt`: 6 @Serializable models (StoryBackup, ChapterBackup, ProgressBackup, StatsBackup, SearchBackup)
  - `BackupRepository.kt`: ZIP export/import qua `ZipOutputStream`/`ZipInputStream`
  - ZIP structure: `metadata.json` + `chapters/{storyId}/{number}.txt` (toàn bộ nội dung đã tải)
  - SAF (Storage Access Framework): `ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT` — không cần permission
  - `SettingsViewModel`: BackupState sealed class, export/import functions
  - `SettingsScreen`: Section "Sao lưu & Khôi phục" + progress indicator + confirm dialog
  - DAO bulk methods: `insertStories`, `getAllStoriesOnce`, `getAllProgressOnce`, `insertAllProgress`, etc.
  - Dependencies: `kotlinx-serialization-json:1.8.1`, `kotlin.plugin.serialization`

- **Compose Stability**: Migrate 7 state classes sang `ImmutableList` (kotlinx-collections-immutable:0.3.8)
  - SearchState, ExploreState, DetailState, ReaderState, StatsState, SettingsState, ReadingStatsState
  - Thêm `.toImmutableList()` tại tất cả emission sites

- **LazyColumn Keys**: Thêm explicit keys cho 3 chỗ thiếu
  - ExploreScreen genre LazyRow: `key = { it.slug }`
  - StoryDetailScreen chapters: `key = { _, ch -> ch.chapterNumber }`
  - ChapterListPanel chapters: `key = { _, ch -> ch.chapterNumber }`

- **Baseline Profiles**: `profileinstaller:1.4.1` + `baseline-prof.txt`

- **Crash Reporting**: Custom `CrashHandler` lưu crash logs local
  - `CrashHandler.kt`: UncaughtExceptionHandler → file (device info, stack trace, memory)
  - `AppTruyenApplication.onCreate()`: install handler
  - `SettingsScreen`: Section "Nhật ký lỗi" — hiển thị 5 logs gần nhất, xem chi tiết, sao chép, xóa
  - Giữ tối đa 10 files, tự xóa cũ nhất

### Cải tiến

- **Update 13 dependencies + Gradle**:
  - AGP 9.0.1 → 9.2.0, Gradle 9.2.1 → 9.4.1
  - `core-ktx:1.17.0` → `core:1.17.0` (artifact rename, core-ktx obsolete)
  - Compose BOM 2026.03.01 → 2026.05.01
  - Coroutines 1.10.2 → 1.11.0
  - Activity Compose 1.12.4 → 1.13.0
  - Navigation Compose 2.9.7 → 2.9.8
  - Jsoup 1.22.1 → 1.22.2
  - DataStore 1.2.0 → 1.2.1
  - MockK 1.13.9 → 1.14.11
  - org.json 20231013 → 20260522
  - AndroidX Test 1.2.1 → 1.3.0, Espresso 3.6.1 → 3.7.0
  - targetSdk 35 → 36 (Android 16)
- **Tạo `ReaderThemeColors.kt`**: Centralize reader theme colors (bg, text, panel, highlight, accent) cho 3 theme (Light/Dark/Sepia). Refactor 4 composable trong ReaderScreen để dùng shared colors thay vì duplicate `when(theme)` blocks
- **ChapterJumpSheet search toàn bộ chapters**: Đổi từ `state.displayedChapters` (lazy paged) sang `state.chapters` (full list) — user có thể tìm mọi chương
- **Animation delay cap**: DownloadsScreen animation delay cap ở 500ms (`minOf(index * 50, 500)`)
- **Fix deprecated icon**: `Icons.Filled.MenuBook` → `Icons.AutoMirrored.Filled.MenuBook` (ReaderScreen)
- **Tách ReaderScreen.kt** (1698→642 dòng): Extract 4 composable files mới:
  - `TtsControlBar.kt` — TTS controls với speed/pitch sliders
  - `ReaderSettingsPanel.kt` — Settings card (font, theme, brightness, thay chữ)
  - `ChapterNavigation.kt` — NextChapterSection + PrevChapterSection
  - `ChapterListPanel.kt` — Slide-in chapter list panel
- **Standardize scraper log tags**: Tất cả log tags giờ dùng format `{ClassName}` thống nhất
- **Fix bug TangThuVienScraper.getChapterContent()**: Thêm try-catch (trước đó exception throw thẳng)
- **Thêm unit tests**: 20 tests cho `TextReplacementHelper` (applyReplacements, parseJSON, serialize)
- **Thêm `org.json:json:20231013`** test dependency cho JVM unit tests
- **Tách StoryRepository** (858→756 dòng): Migrate 2 domain-specific repos:
  - `ReadingStatsRepository` — recordReadingSession, recordChapterRead, streak, observeAllStats
  - `SearchHistoryRepository` — getRecentSearches, saveSearch, deleteSearch, clearSearchHistory
- **Sealed error classes** (`ScraperException.kt`): 6 subtypes (NetworkException, HttpException, ParseException, CacheException, CircuitOpenException, UnknownException) + `toScraperException()` extension
- **Circuit breaker** (`ScraperCircuitBreaker.kt`): Auto-disable source sau 5 failures, recovery sau 60s, states CLOSED/OPEN/HALF_OPEN
- **Circuit breaker DI**: Thêm `provideScraperCircuitBreaker()` vào AppModule
- **Circuit breaker tích hợp**: StoryRepository filter sources theo circuit state, record success/failure cho `getCompletedStories`, `searchStories`
- **Scraper error migration**: Tất cả 4 scrapers (TruyenCom, TangThuVien, SsTruyen, TruyenFull) sử dụng ScraperException thay IOException trong fetchDocument/fetchDocumentWithRetry
- **Unit tests**: 14 tests cho ScraperCircuitBreaker (state transitions, execute, reset)
- **Test migration**: Fix test files cho repository constructor changes

### Fix bug

- **Fix B1: Background refresh ghi đè totalChapters=0**: Guard không ghi đè khi fresh totalChapters=0 mà cached > 0 (StoryRepository.kt)
- **Fix B3: TTV lastNumericStoryId race condition**: Thay `@Volatile var` bằng `ConcurrentHashMap<slug, numericId>` (TangThuVienScraper.kt)
- **Fix B5: Prefetch không cancel khi chuyển chương**: Thêm `prefetchJob` tracking + cancel (ReaderViewModel.kt)
- **Fix B7: extractStoryId logic thừa**: Simplify `path.contains(".") || path.isNotBlank()` → `takeIf { it.isNotBlank() }` (StoryRepository.kt)
- **Fix B8: CircuitBreaker race condition**: Thay mix AtomicInteger/@Volatile bằng `synchronized` blocks (ScraperCircuitBreaker.kt)
- **Fix B9: CookieJar thread safety**: `mutableMapOf` → `ConcurrentHashMap` (AppModule.kt)
- **Fix B10: sourceFor() fallback sai source**: `sources.first()` → throw `IllegalArgumentException` (StoryRepository.kt)
- **Fix B11: Scrapers trả error string thay vì throw**: 3 scrapers `getChapterContent()` return error string → đổi throw `ScraperException.ParseException`. Bỏ string matching trong download loop (TTV/SST/TF + StoryRepository)
- **Fix B12: ReadingStats race condition**: Read-modify-write → atomic `INSERT OR IGNORE` + `UPDATE SET x = x + delta` (ReadingStatsDao + ReadingStatsRepository)
- **Fix B13: TTS callback threading**: Wrap `onStart/onDone/onError` trong `Handler(MainLooper).post {}` (TtsService.kt)
- **Fix B14: WebView onPageFinished guard**: Thêm `isDestroyed` check đầu `onPageFinished` (WebViewContentLoader.kt)
- **Fix B15: SsTruyen false "Full" detection**: Scope `contains("Full")` check từ toàn page → `.tt-status` element (SsTruyenScraper.kt)
- **Fix B16: TTV getChapterList concurrent addAll**: Wrap `allChapters.addAll(batch)` trong `synchronized` (TangThuVienScraper.kt)
- **Fix B17: ChapterDao thiếu ORDER BY**: Thêm `ORDER BY chapterNumber ASC` cho `getChaptersByStoryOnce` (ChapterDao.kt)
- **Fix: 11 toán tử `!!` không an toàn → safe calls**: Thay tất cả `!!` (force unwrap) bằng safe alternatives (`?.let {}`, `?: ""`, `?: return`, `?: throw`) để tránh NPE:
  - `StoryRepository.kt:422` — `cached!!.content!!` → `cached?.content ?: throw Exception(...)`
  - `ReaderScreen.kt:466` — `state.error!!` → `state.error ?: ""`
  - `StoryDetailScreen.kt:110,125,177` — `state.error!!`, `state.story!!`, `state.downloadProgress!!` → safe calls
  - `SearchScreen.kt:336` — `state.error!!` → `state.error ?: ""`
  - `SearchViewModel.kt:142,169` — `genre!!` → `genre?.let { browseByGenre(it, ...) }`
  - `TtsManager.kt:63,70` — `service!!.state/skipEvent` → local `val svc = service ?: return@launch`
  - `TtsService.kt:471` — `audioFocusRequest!!` → `audioFocusRequest?.let { ... }`
- **Fix: Deprecated `Locale("vi", "VN")` constructor** (TtsService.kt:142) → `Locale.Builder().setLanguage("vi").setRegion("VN").build()`
- **Fix: SplashScreen VectorDrawable crash tiềm ẩn**: Tách `ic_launcher_foreground.xml` thành `ic_splash_logo.xml` riêng biệt để tránh Adaptive Icon phân giải nhầm trên ROM tùy biến
- **Fix: Filter "Chỉ truyện Full" không lọc hết truyện đang ra**: 2 nguyên nhân:
  1. `TruyenFullScraper.parseStoryList()` gán `status=""` cho truyện không có `label-full` badge → `applyLocalFilters()` cho qua vì `status.isBlank()`. Fix: đổi thành `"Đang ra"` (giống SsTruyen)
  2. `SearchViewModel.applyLocalFilters()` có điều kiện `story.status.isBlank()` cho phép truyện unknown status lọt qua filter Full. Fix: bỏ điều kiện này — chỉ truyện xác nhận "Hoàn thành"/"Full" mới pass filter


## [v2.6] - 2026-05-30

### Fix bug

- **Fix: Toggle "Chỉ truyện Full" hoàn toàn không hoạt động**: `completedOnly` parameter được pass qua 3 layer (UI → ViewModel → Repository) nhưng không bao giờ thực sự lọc:
  - Keyword search: hardcode `false`, `applyLocalFilters()` bỏ qua status
  - Genre browse: Repository nhận nhưng không forward, interface `StorySource.getStoriesByGenre()` thiếu parameter, tất cả 4 scrapers hardcode completed-only URLs
  - Fix: Thêm `completedOnly: Boolean = true` vào `StorySource.getStoriesByGenre()`. Mỗi scraper xử lý server-side filter theo cách riêng:
    - TruyenCom: `/truyen-{slug}/` (tất cả) vs `/truyen-{slug}/full/` (hoàn thành)
    - TTV: bỏ/thêm `&fns=ht` query param
    - SsTruyen: bỏ/thêm `?loc=hoan-thanh` query param
    - TruyenFull: `/the-loai/{slug}/` (tất cả) vs `/the-loai/{slug}/hoan/` (hoàn thành)
  - `StoryRepository.getStoriesByGenre()` forward `completedOnly` cho từng scraper
  - `SearchViewModel.applyLocalFilters()` thêm kiểm tra status cho keyword search (giữ story có status rỗng)
- **Fix: Genre dedup mất `categoryId`**: `getGenreList()` dùng `distinctBy { it.slug }` giữ bản đầu tiên ngẫu nhiên → có thể mất `categoryId` từ TruyenCom. Fix: dùng `groupBy` + `maxByOrNull { it.categoryId }` ưu tiên Genre có metadata
- **Fix: Genre name matching thiếu normalize**: `applyLocalFilters()` so sánh genre name không trim → whitespace gây mismatch. Fix: thêm `.trim()` trước so sánh
- **Fix: SettingsScreen hiện "MêTruyệnChữ" (đã xóa)**: Text nguồn cũ không cập nhật sau khi xoá MeTruyenChu và thêm TruyenFull. Fix: đổi thành "TruyenCom • TàngThưViện • SsTruyện • TruyenFull"
- **Fix: ExploreViewModel pullToRefresh race condition**: `pullToRefresh()` không cancel `loadMoreJob` → data cũ có thể append vào kết quả mới. Fix: thêm `loadMoreJob?.cancel()` + clear error
- **Fix: SearchViewModel toggleCompletedFilter bỏ qua genre browse**: Toggle completedOnly khi browse genre không keyword → không trigger lại server-side filter. Fix: thêm `browseByGenre()` khi genre != null
- **Fix: SearchViewModel isFilterActive logic thừa**: `hasAnyFilter || hasGenre` — `hasGenre` đã nằm trong `hasAnyFilter`. Fix: bỏ `|| hasGenre`
- **Fix: StoryRepositoryTest mock search sai param**: `search(any())` không match 2-param method. Fix: đổi thành `search(any(), any())`

### Thêm mới

- **TruyenFullScraper.kt** (NEW): Scraper cho truyenfull.today — cùng engine 8cache với TruyenCom
  - Base URL: `https://truyenfull.today`
  - 46 thể loại, hỗ trợ filter `/hoan/` native (server-side)
  - Server-side rendered — không cần WebView fallback
  - URL patterns: tìm kiếm (`/tim-kiem/?tukhoa=`), chi tiết (`/{slug}/`), đọc chương (`/{slug}/chuong-{N}/`), phân trang chapter (`/{slug}/trang-{N}/#list-chapter`, 50 chương/trang), thể loại hoàn thành (`/the-loai/{genre}/hoan/`), truyện full (`/danh-sach/truyen-full/`)
  - Implement đầy đủ `StorySource` interface (search, detail, firstPage/remaining chapters, content, genres, completed stories)
- **ScraperModule**: Thêm `bindTruyenFull` binding (`@Binds @IntoSet`)
- Tổng 4 nguồn hoạt động: TruyenCom, TàngThưViện, SsTruyện, TruyenFull
- **Snapshot metadata khi tải truyện**: Khi bấm "Tải truyện", tự động fetch + lưu đầy đủ metadata (title, author, genres, description, cover, status) vào Room DB trước khi tải chương. Đảm bảo info truyện vẫn đầy đủ khi nguồn gốc ngừng hoạt động (StoryRepository.snapshotStoryMetadata → DownloadService)

### Xóa bỏ

- **MeTruyenChuScraper.kt**: Xóa toàn bộ scraper cho metruyenchu.com/metruyenchu.com.vn (trang đã ngừng hoạt động)
- **ScraperModule**: Bỏ `bindMeTruyenChu` binding
- **SettingsViewModel**: Bỏ "metruyenchu" khỏi ALL_SOURCES và SOURCE_LABELS
- **WebViewContentLoader**: Bỏ domain metruyenchu.com.vn khỏi ALLOWED_DOMAINS, xóa CSS selectors #vungdoc/.vung-doc
- **SettingsDataStore**: Cập nhật comment CSV sources

> Truyện đã tải từ MêTruyệnChữ vẫn đọc offline bình thường (sourceId="metruyenchu" giữ nguyên trong DB)

## [v2.5] - 2026-05-05

### Thêm mới

- **Per-story Text Replacement**: Thay chữ tự động riêng cho từng truyện, hoạt động song song với thay chữ tổng (cài đặt app)
  - **StoryEntity**: +2 cột `storyReplacementsEnabled` (BOOL), `storyCustomReplacements` (TEXT JSON)
  - **DB Migration v8→v9**: `ALTER TABLE stories ADD COLUMN storyReplacementsEnabled / storyCustomReplacements`
  - **StoryDao**: +3 methods (`setStoryReplacementsEnabled`, `setStoryCustomReplacements`, `getStoryReplacements`) + `StoryReplacementData` partial query class
  - **StoryRepository**: +5 methods (get/set enabled, add/remove replacement, `sendReplacementToGlobal`)
  - **TextReplacementHelper**: +`applyAllReplacements()` kết hợp global (chạy trước) + per-story (chạy sau)
  - **ReaderViewModel**: Load per-story replacements khi init, +methods (toggleStoryReplacements, addStoryReplacement, removeStoryReplacement, sendReplacementToGlobal, reloadStoryReplacements)
  - **ReaderSettingsPanel**: Section "THAY CHỮ RIÊNG CHO TRUYỆN" với toggle, danh sách từ, nút 🌐 "Gửi sang cài đặt tổng", nút 🗑️ xóa, nút "Thêm từ thay thế", dialog nhập, info note hướng dẫn
  - **Preserve data**: 3 chỗ `insertStory()` giữ `storyReplacementsEnabled`/`storyCustomReplacements` khi background refresh
  - **Snackbar feedback**: "Đã gửi sang cài đặt tổng" hoặc "Từ này đã có trong cài đặt tổng" (auto-dismiss 3s)

### Sửa đổi

- **BackHandler**: Nút Back hệ thống đóng panel Cài đặt đọc / Danh sách chương thay vì quay về trang bìa truyện
- **UI consolidation**: Chuyển toàn bộ per-story replacement UI từ StoryDetailScreen (BottomSheet) vào ReaderSettingsPanel — 1 nơi duy nhất quản lý

### Xóa bỏ

- **StoryDetailScreen**: Xóa nút ⚙️ Settings trên TopAppBar + StorySettingsBottomSheet composable (~220 dòng)

## [v2.4] - 2026-04-29

### Fix bug

- **Fix CRITICAL: Search chỉ hiện 3 kết quả thay vì hàng chục**: Nguyên nhân kép:
  1. **TruyenCom search KHÔNG CÓ status badge** trong HTML (rows chỉ có title + author + "N chương") → code parse `row.text()` phát hiện sai "Full" từ genre URLs (`/truyen-tien-hiep/full/`) → false positive. Fix: bỏ status parse cho TC search, set `status=""` (unknown), bỏ `completedOnly` filter ở tầng TC scraper
  2. **MeTruyenChu chỉ fetch 1/4+ trang**: Trang search có `<i class="status status-full">` badge (ĐÚNG), parse ĐÚNG, nhưng chỉ fetch trang 1 (~20 kết quả, 2 Full). Fix: thêm multi-page search (fetch trang 2, 3 qua `.phan-trang .btn-page` pagination), tăng từ ~20 → ~60 kết quả
  3. **SearchViewModel** luôn pass `completedOnly=true` khi keyword search → loại hết kết quả từ nguồn không có status data. Fix: keyword search luôn pass `completedOnly=false`

- **Fix CRITICAL: Nút "Tiếp tục đọc" nhảy sai chương (chương 45 thay vì 128)**: Nguyên nhân kép:
  1. **`saveScrollPosition()` dùng `viewModelScope.launch`** — khi user bấm Back, `onCleared()` cancel `viewModelScope` TRƯỚC KHI `DisposableEffect.onDispose()` kịp gọi save → progress cuối cùng bị mất. Fix: đổi sang `appScope.launch` (pattern đã tồn tại cho `recordReadingSession`)
  2. **`StoryDetailViewModel` fetch progress 1 lần** bằng suspend (`getReadingProgress`) → khi user quay lại từ Reader, ViewModel tồn tại trong back stack → progress cũ hiển thị. Fix: observe progress bằng `observeReadingProgress()` Flow (tương tự `observeChapters` đã có)
  3. Thêm final save progress trong `onCleared()` bằng `appScope.launch` — backup

- **Fix: Lỗi chính tả "MeTruyệnChủ"**: Đổi thành "MêTruyệnChữ" trong SettingsViewModel (SOURCE_LABELS) và SettingsScreen (thông tin app)

- **Fix: SsTruyen description sai (hiện SEO text thay vì mô tả truyện)**: Scraper dùng `og:description` ("Đọc truyện X của tác giả Y thuộc thể loại Z... Full với tổng số 116 chương...") thay vì mô tả thật. Fix: lấy `.desc-text` content (bỏ header "GIỚI THIỆU"), fallback `og:description`

- **Fix: SsTruyen cover image không hiện (hiện logo site)**: Cover nằm trong CSS `background:url()` của `.wallpaper` div, KHÔNG có `<img>` tag → selector cũ (.each_truyen img, img.book...) không match. Fix: parse `background:url()` từ `.wallpaper[style]`, fallback `og:image` meta tag. Cũng thêm `.hoan-thanh-mau` selector cho status badge (ngoài `.hoan-thanh`)

### Thêm mới

- **Tải truyện chạy nền (Foreground Service)**: Tải tiếp tục khi tắt màn hình hoặc chuyển app
  - `DownloadService.kt` (NEW): Foreground Service với `foregroundServiceType="dataSync"`, WakeLock 2h, notification progress bar real-time, nút Hủy trên notification
  - Song song với `TtsService` (khác notification ID 3001 vs 1001, khác channel, khác foregroundServiceType)
  - Static `StateFlow<DownloadState>` cho ViewModel observe progress từ bên ngoài
  - `StoryDetailViewModel`: Khởi chạy DownloadService thay vì `viewModelScope.launch`, observe progress qua StateFlow
  - `ChapterDao.getChaptersByNumbers()`: Query chapters bằng list chapterNumber cho Service
  - `StoryRepository.getChaptersByNumbers()`: Map entity → domain model
  - AndroidManifest: thêm `FOREGROUND_SERVICE_DATA_SYNC` permission + `DownloadService` declaration



### Fix bug

- **Fix: Trang chi tiết hiện hàng chục tag không liên quan**: Selector `a[href*=truyen-]` (TruyenCom) / `a[href*=/the-loai/]` (MeTruyenChu, SsTruyen) tìm thể loại trên TOÀN BỘ trang HTML thay vì chỉ trong vùng info truyện → lấy nhầm 42+ link từ navigation menu, sidebar, footer. Fix: scope selector vào container chứa info truyện:
  - TruyenCom: `doc.select("a[href*=truyen-]")` → `.info-holder a[href*=truyen-]`
  - MeTruyenChu: `doc.select("a[href*=/the-loai/]")` → `.book-info-text a[href*=/the-loai/]`
  - SsTruyen: `doc.select("a[href*=/the-loai/]")` → `.info a[href*=/the-loai/]` (fallback whole doc)

- **Fix: MeTruyenChu nội dung chương lặp tựa truyện + tên chương**: `#vungdoc` chứa header (h1 tựa truyện + h2 tên chương + navigation links) bao quanh `div.truyen` (nội dung thật). Khi đọc thì không sao nhưng TTS đọc lặp tên truyện/chương gây khó chịu. Fix 3 lớp:
  - **Lớp 1** (WebView JS): Thêm selector `#vungdoc .truyen` ưu tiên đầu + clone DOM → strip h1-h4, .chapter_wrap, navigation links trước khi lấy innerText
  - **Lớp 2** (Jsoup): Thêm selector `#vungdoc .truyen` + remove heading/nav elements trước khi cleanHtml
  - **Lớp 3** (Defense-in-depth): `cleanRepeatedHeaders()` regex strip các dòng "Chương N..." và navigation text ở đầu nội dung

- **Fix Gradle `transformDebugUnitTestClassesWithAsm` InvalidUserCodeException**: Lỗi `jarsOutputDir` property query trước task complete do `applicationVariants.all` (legacy API bị xóa trong AGP 9.0) + `android.newDsl=false`. Fix: migrate sang `androidComponents { finalizeDsl }` + `base.archivesName` cho APK renaming, xóa `android.newDsl=false` khỏi `gradle.properties`
- **Fix test compilation: `HomeViewModelTest`**: Reference `downloadedStories` không tồn tại (property đã đổi tên thành `recentlyRead` trong refactor trước). Fix: đổi test sang dùng `viewModel.recentlyRead`
- **Fix test compilation: `StoryRepositoryTest`**: Thiếu param `dataStore: DataStore<Preferences>` sau khi thêm source management vào `StoryRepository`. Fix: thêm mock `DataStore` với `emptyPreferences()` vào test setup
- **Fix test runtime: `SearchViewModelTest`**: Mock `searchStories("test")` chỉ match 1-arg nhưng ViewModel gọi 2-arg `searchStories(query, completedOnly)`. Fix: đổi mock sang `searchStories("test", any())`

## [2.3.1] - 2026-04-12

### Fix bug

- **Fix CRITICAL: MET chapter list thiếu chương (chỉ hiển thị 1-32 + cuối)**: MET render chapter list bằng JavaScript AJAX, Jsoup chỉ thấy ~30 chương tĩnh. Fix: dùng API `/get/listchap/{bid}?page={N}`, extract `bid` từ `<input name="bid">`. Parse pagination từ `onclick="page(bid,N)"`. Xử lý chapters không có số trong URL (`chuong--{hash}`) bằng sequential numbering.

## [2.3] - 2026-04-12

### Fix bug

- **Fix CRITICAL: MeTruyenChu cover image không hiện**: Scraper thiếu selector `.book-info-pic img` cho ảnh bìa + chưa hỗ trợ `data-src` (lazy-loading). Fix: thêm `.book-info-pic img` ưu tiên đầu, thêm fallback `data-src` attribute
- **Fix CRITICAL: MeTruyenChu chapter content không load**: Website render nội dung chương bằng JavaScript → Jsoup trả về rỗng. Fix: thêm selector `#vungdoc` / `.vung-doc` + WebView headless fallback khi Jsoup content quá ngắn
- **Fix CRITICAL: App crash khi quay lại từ Reader**: `ReaderViewModel.onCleared()` gọi `viewModelScope.launch` sau khi scope đã cancelled. Fix: inject `@ApplicationScope` CoroutineScope, dùng `appScope.launch` thay `viewModelScope.launch` trong `onCleared()`
- **Fix: WebViewContentLoader chặn domain MeTruyenChu**: `shouldOverrideUrlLoading` chỉ cho phép `truyencom.com` → block tất cả domain khác. Fix: thêm `ALLOWED_DOMAINS` list (truyencom.com, tangthuvien.vn, metruyenchu.com.vn, sstruyen.net)
- **Fix: WebView thiếu selector cho MET**: EXTRACT_JS không có `#vungdoc` / `.vung-doc`. Fix: thêm vào đầu danh sách selectors
- **Fix: CHAPTER_NUMBER_REGEX quá chặt**: Pattern `chuong-(\d+)-` yêu cầu dấu `-` sau số → không match chapter URLs không có hash ID. Fix: đổi thành `chuong-(\d+)` match cả 2 dạng
- **Fix: ReaderVM chapter URL regex break MET URLs**: Pattern `/chuong-\d+` match sai trong URL `/chuong-1-5IgkcQhl5YDW` (chỉ replace `chuong-1`, để lại hash). Fix: cập nhật regex `/chuong-\d+(-[A-Za-z0-9_!-]+)?` handle cả hash pattern
- **Fix: MeTruyenChu extractSlug thiếu normalize**: Chapter links chứa suffix `-truyen-chu` trong slug nhưng story URL không có → storyId mismatch giữa story detail và chapter list. Fix: `removeSuffix(TRUYEN_CHU_SUFFIX)` trong `extractSlug()`

### Sửa đổi

- `MeTruyenChuScraper`: Inject `WebViewContentLoader` cho JS-rendered content fallback
- `ReaderViewModel`: Inject `@ApplicationScope` CoroutineScope cho lifecycle-safe recording
- `WebViewContentLoader`: Centralize allowed domains list, support multi-source

### Thêm mới

- **Xóa truyện khỏi Đang Đọc**: Icon thùng rác bên cạnh mỗi story card (giống trang Đã Tải). Dialog xác nhận trước khi xóa. Snackbar thông báo sau khi xóa. Chỉ xóa reading progress — truyện đã tải không bị ảnh hưởng
  - `HomeViewModel.removeFromReadingList()`: Xóa reading progress qua Repository
  - `StoryRepository.deleteReadingProgress()`: Public method gọi `progressDao.deleteProgress()`
  - `HomeScreen.ReadingListContent`: Icon `DeleteOutline` + `AlertDialog` confirm + `SnackbarHost`
- **Tab "Thống kê" thay thế "Đã tải"**: Tab "Đã tải" trên Home bị thừa (có screen riêng ở bottom nav). Thay bằng tab "Thống kê" hiện inline:
  - Summary cards: số truyện, tổng chương đã đọc, streak ngày liên tiếp
  - Hôm nay: số chương + thời gian đọc
  - Biểu đồ cột 7 ngày gần đây
  - `HomeViewModel.ReadingStatsState`: Data class cho stats
  - `HomeViewModel.loadReadingStats()`: Tải stats từ `ReadingStatsDao`

### Fix bug (Batch 2)

- **Fix CRITICAL: App crash khi scroll infinite**: `LazyColumn/LazyVerticalGrid` dùng `story.id` (slug) làm key → trùng khi cùng truyện xuất hiện từ nhiều nguồn hoặc các trang khác nhau. Fix: đổi key sang `"${sourceId}:${id}"` compound key tại ExploreScreen, SearchScreen, HomeScreen
- **Fix CRITICAL: App crash khi back từ Search filter**: Duplicate key còn xảy ra khi `loadMoreFilterResults()` gộp kết quả mới mà không dedup. Fix: thêm `deduplicateStories()` vào `ExploreViewModel` và `SearchViewModel`
- **Fix: Genre/Tag load chậm**: `getGenreList()` gọi tất cả sources không có timeout → 1 source chậm block tất cả. Fix: thêm `withTimeoutOrNull(15s)` per source
- **Tối ưu: Batch loading 3x**: `getCompletedStories()` & `getStoriesByGenre()` trước đây load 1 page/source → sau dedup chỉ còn 4-10 truyện. Fix: load 3 pages cùng lúc từ mỗi source mỗi lần "load more" (4 sources × 20/page × 3 pages = ~240 → dedup ~40-60 unique). Timeout giảm từ 15s → 10s
- **Fix: TTV trả kết quả sai khi genre không được hỗ trợ**: `genreSlugToIdMap` chỉ có 12 thể loại gốc → "Hệ Thống" trả về ctg=0 → kết quả chung. Fix: guard clause trả empty list khi slug không có trong map
- **Tối ưu: TC getStoriesByGenre bỏ double-fetch**: Trước: fetch HTML để lấy categoryId, rồi fetch lại API. Sau: fetch HTML 1 lần, extract catId + parse stories từ cùng Document. Refactor `parseStoryListPage` thành 2 overload (URL + Document)
- **Fix: Genre browse loại nhầm truyện**: SST/MET genre page KHÔNG hiển thị status badge → `parseStoryList` trả `status="Đang ra"` sai → `completedOnly` filter loại hết. Fix: bỏ client-side `completedOnly` filter (TC/TTV đã lọc server-side)
- **Fix: minChapters filter loại nhầm truyện**: Genre page không hiển số chương → `totalChapters=0` → bị lọc. Fix: chuyển minChapters filter từ scraper sang repo level, chỉ lọc story có `totalChapters > 0`
- **Tối ưu: SST dùng ?loc=hoan-thanh server-side filter**: Genre browse URL thêm `?loc=hoan-thanh` để SST chỉ trả truyện hoàn thành, force `status="Hoàn thành"`

## [2.2] - 2026-04-12

### Thêm mới

- **Thêm 2 nguồn truyện mới**: Mở rộng multi-source từ 2 → 4 nguồn
  - `MeTruyenChuScraper.kt` (NEW): Scraper cho metruyenchu.com.vn. Chapter URL có hash ID cố định (`/{slug}/chuong-{N}-{hashId}`), 35+ thể loại (`/the-loai/{slug}`), truyện full (`/danh-sach/truyen-full`), search (`/tim-kiem?tukhoa=`). LRU cache, retry + exponential backoff
  - `SsTruyenScraper.kt` (NEW): Scraper cho sstruyen.net. Chapter URL sạch (`/truyen/{slug}/chuong-{N}`), pagination rõ ràng (`/truyen/{slug}/page/{N}`), 30+ thể loại, truyện hoàn thành (`/truyen-hoan-thanh`), OG description parsing
  - `ScraperModule.kt`: Thêm 2 `@Binds @IntoSet` binding mới (`bindMeTruyenChu`, `bindSsTruyen`)
  - Cả 2 scraper implement đầy đủ `StorySource` interface (search, detail, firstPage/remaining chapters, content, genres, completed stories)
  - Không cần thay đổi Repository, ViewModel, hay UI — nhờ kiến trúc `Set<StorySource>`

### Sửa đổi

- **Smart Filter**: Fix 5 xung đột trong hệ thống lọc, thống nhất logic giữa Search & Explore
  - `completedOnly` mặc định = `true` (chỉ hiện truyện Full). User muốn xem đang ra phải tự tắt
  - `StoryRepository.getStoriesByGenre()`: thêm param `completedOnly`, lọc client-side cho nguồn không hỗ trợ server filter (MeTruyenChu, SsTruyen)
  - `StoryRepository.getCompletedStories()`: thêm safety filter cho nguồn không hardcode status
  - `SearchViewModel`: bỏ double-filter `completedOnly` (server + client), pass `completedOnly` xuống `browseByGenre()` & `loadMoreFilterResults()`
  - `ExploreViewModel`: pass `completedOnly=true` cho tất cả `getStoriesByGenre()` calls
  - `SearchScreen`: đổi label "Full + Mới nhất" → "Chỉ truyện Full", chip "Cả đang ra" khi user tắt lọc Full
  - `clearAllFilters()` reset về default `completedOnly=true` thay vì `false`

### Fix bug

- **MeTruyenChu search URL sai**: `/tim-kiem?tukhoa=` → `/search?q=` (URL cũ redirect về homepage, không trả kết quả)
- **SsTruyen search URL sai**: `/tim-kiem?tukhoa=` → `/search?s=`
- **Tốc độ tìm kiếm chậm (45s-1m)**: Thêm `withTimeoutOrNull(15s)` per source trong Repository → max chờ 15s thay vì 90s (3 retry × 30s timeout)
- **Không hiện số chương từ nguồn mới**: Fix `parseStoryList()` - tìm text "Số chương : N" trong container item thay vì parent text
- **Không hiện ảnh bìa từ nguồn mới**: Thêm selector `a.cover img, .cover img, img[data-src]` + hỗ trợ lazy-loading `data-src`
- **parseStoryList() fallback**: Thêm 2-tier parsing (div.item → h3 a fallback) cho cả 2 scraper mới
- **MeTruyenChu FULL badge**: Phát hiện `<i class="status-full">` (CSS pseudo-element) thay vì text "Full" — áp dụng cho search list, fallback parser, và story detail
- **Cập nhật dependencies**: Hilt 2.59.1→2.59.2, KSP 2.3.5→2.3.6, Compose BOM 2025.12.00→2026.03.01, Coil 3.3.0→3.4.0
- **AGP 9.1.0 rollback**: AGP 9.1.0 có regression `processDebugNavigationResources`, giữ 9.0.1
- **BUG-4: Duplicate Story ID đa nguồn**: `distinctBy { it.id }` trong Repository gây trùng ID khi cùng slug xuất hiện từ MeTruyenChu, SsTruyen, TangThuVien → mất kết quả. Fix: `distinctBy { "${it.sourceId}:${it.id}" }` — compound key giữ toàn bộ kết quả từ mọi nguồn
- **BUG-3: Filter completed nhận nhầm truyện**: `status.isBlank()` trong `getCompletedStories()` và `getStoriesByGenre()` cho phép truyện chưa parse được status (blank) lọt qua filter "Hoàn thành". Fix: bỏ `|| it.status.isBlank()` — các scraper đã gán default "Đang ra"
- **BUG-1: Hardcoded nguồn đơn trong Settings**: "Nguồn: TruyenCom.com" → liệt kê đầy đủ 4 nguồn
- **BUG-6: Nút "Xóa tất cả bộ lọc" luôn hiện**: `hasAnyFilter` trong FilterBottomSheet dùng `localCompleted` (default true) → luôn true. Fix: đổi thành `!localCompleted` chỉ tính khi user tắt filter Full
- **BUG-7: TTS error snackbar treo vĩnh viễn**: `errorMessage` không bao giờ được clear → snackbar hiện mãi. Fix: thêm auto-dismiss 5s + nút "Đóng" + AnimatedVisibility
- **UI-1: Smart dedup đa nguồn**: Thay `distinctBy { compound key }` bằng `smartDedup()` — gộp truyện cùng slug từ nhiều nguồn, chỉ giữ bản có nhiều chương nhất. Giảm trùng kết quả search/browse

### Thêm mới (Phase 2)

- **FEAT-1: Quản lý nguồn truyện**: Section "Quản lý nguồn" trong Settings với 4 Switch toggle. Tắt nguồn không dùng để tăng tốc tìm kiếm. `activeSources()` helper trong Repository lọc theo DataStore preferences. Không cho tắt nguồn cuối cùng
- **FEAT-4: Tải trước chương**: Auto-prefetch N chương tiếp theo (0/3/5/10) vào `ConcurrentHashMap` in-memory. Đóng app mất cache. Không xung đột với download offline (Room). Chỉ cần có mạng, không yêu cầu WiFi
- **FEAT-5: Chọn font chữ**: 4 font: Noto Serif (serif, mặc định), Roboto (sans-serif), Be Vietnam Pro (UI font), System Default. FilterChip picker trong Reader Settings. Lưu vào DataStore
- **UI-3: Tổ chức lại Reader Settings**: Chia thành 2 section "HIỂN THỊ" (font, cỡ chữ, khoảng cách dòng, độ sáng, giao diện) và "CÔNG CỤ" (thay chữ, copy mode) với divider + label section
- **UI-9: Placeholder mô tả trống**: Khi `story.description` blank, hiện "Chưa có mô tả cho truyện này." italic thay vì ẩn section hoàn toàn

## [2.1] - 2026-03-28

### Sửa đổi

- **Tái cấu trúc SearchScreen**: Gộp tất cả bộ lọc (số chương, trạng thái Full, thể loại) vào 1 FilterBottomSheet duy nhất. Header giảm từ ~316dp xuống ~108-144dp, hiển thị 5-6 truyện thay vì 3
  - Xóa: Row chips số chương, row "Full + Mới nhất", nút "Tìm kiếm" riêng khỏi main screen
  - Thêm: FilterBottomSheet gộp 3 sections (Số chương / Trạng thái / Thể loại) với local state, nút "Áp dụng bộ lọc" + "Xóa tất cả bộ lọc"
  - Thêm: Active filter chips row compact dưới search bar (horizontal scroll, mỗi chip có nút × dismiss)
  - Thêm: Badge count trên nút filter hiển thị số bộ lọc đang bật
  - `SearchViewModel`: Thêm `applyAllFilters()` áp dụng cả 3 bộ lọc cùng lúc, `clearAllFilters()` reset filters giữ query

### Fix bug

- **Fix**: TTS dừng lâu ở dấu chấm giữa từ (Dr.Bach, S.H.I.E.L.D., Mr. Bean) — TTS engine hiểu nhầm dấu chấm là kết thúc câu. Thêm `TtsTextPreprocessor.kt` xử lý text trước khi gửi TTS: bỏ dấu chấm acronym (S.H.I.E.L.D. → SHIELD), thay viết tắt phổ biến (Dr. → Doctor), thay dấu chấm giữa từ bằng khoảng trắng. UI hiển thị không bị ảnh hưởng


## [2.0.3] - 2026-03-01

### Fix bug

- **Fix**: Chuyển chương tiếp nhảy cuối chương thay vì đầu chương — `rememberLazyListState()` giữ scroll position cũ xuyên suốt mọi chương, gây race condition với `LaunchedEffect` scroll-to-top. Fix: dùng `remember(state.currentChapterNumber) { LazyListState() }` tạo state mới mỗi chương, đơn giản hóa scroll logic chỉ handle `scrollToEnd` cho chương trước, key `saveScrollPosition` theo chương
- **Fix**: Chế độ Copy không hoạt động khi TTS đang bật — `SelectionContainer` bị conflict với `animateColorAsState` recomposition liên tục từ TTS highlight. Fix: `toggleCopyMode()` auto-pause TTS khi bật copy mode

## [2.0.2] - 2026-02-26

### Fix bug

- **Fix CRITICAL**: TTV (Tàng Thư Viện) chỉ hiện chương 1-75 + vài chương cuối, thiếu toàn bộ chương ở giữa — TTV phân trang chương qua AJAX (75 chương/trang) nhưng scraper giả định tất cả chương có sẵn trong HTML ban đầu. Fix: implement pagination qua AJAX endpoint `/doc-truyen/page/{storyId}?page={pageIndex}&limit=75&web=1`, extract numeric `story_id_hidden`, crawl parallel với Semaphore(3)

## [2.0.1] - 2026-02-25

### Fix bug

- **Fix CRITICAL**: Dữ liệu truyện đã đọc/tải bị mất sau update — thiếu `MIGRATION_5_6` khiến user update từ DB v5 (v1.5–v1.7) lên v8 (v2.0) bị Room xóa sạch DB. Fix: thêm `MIGRATION_5_6` tạo bảng `reading_stats`, bỏ v5 khỏi `fallbackToDestructiveMigrationFrom`
- **Fix**: Search keyword + toggle "Full + Mới nhất" chỉ hiện truyện Full không liên quan keyword — `TruyenComScraper.search()` không dùng `completedOnly`, `TangThuVienScraper.search()` dùng endpoint `/tong-hop` (browse, bỏ qua keyword). Fix: cả 2 scraper dùng search endpoint + lọc client-side truyện "Hoàn thành"

## [2.0] - 2026-02-24

### Thêm mới

- **Filter "Full + Mới nhất"**: Chip filter riêng cho truyện đã hoàn thành, sắp xếp mới nhất
  - TTV dùng server-side filter `/tong-hop?term=...&fns=ht` (endpoint tối ưu)
  - TC dùng client-side filter qua `applyLocalFilters()`
  - `Story.kt` thêm field `status` ("Hoàn thành", "Đang ra", "Ngừng viết")
  - Scrapers parse status từ HTML (TTV: `.author` text, TC: row text "Full/Hoàn")
- **Inline filter chips**: Số chương (Tất cả/200+/300+/500+/900+) + "Full + Mới nhất" trên SearchScreen
- **Results summary**: Hiển thị "• Full" khi filter hoàn thành đang bật
- **DB migration v7→v8**: `ALTER TABLE stories ADD COLUMN status`
- **ChapterDao UPSERT**: `saveChapterContent` dùng INSERT IGNORE + UPDATE thay vì chỉ UPDATE

### Sửa đổi

- `StorySource.search()` nhận `completedOnly: Boolean` parameter
- `StoryRepository.searchStories()` pass `completedOnly` tới từng source
- `SearchViewModel`: Gộp `searchWithFilters()` vào `search()` (xóa code trùng lặp)
- `TangThuVienScraper.getStoryDetail()`: Pass `status` vào Story constructor (trước đó bị bỏ)
- `TangThuVienScraper.cleanHtml()`: Dùng cached regex thay vì tạo inline
- `TruyenComScraper.search()`: Chapter count regex chuyển sang companion cached

### Thêm mới (Multi-Source)

- **Multi-Source Scraping**: Gộp kết quả từ `truyencom.com` + `truyen.tangthuvien.vn`. Tất cả nguồn chỉ trả về truyện đã hoàn thành + mới nhất
  - `StorySource.kt` (NEW): Interface chung cho mọi nguồn scrape (search, detail, chapters, content, browse, genres)
  - `TangThuVienScraper.kt` (NEW): Scraper cho truyen.tangthuvien.vn (search, detail, chapter list, content, completed stories, genre browsing)
  - `ScraperModule.kt` (NEW): Hilt module dùng `@Binds @IntoSet` inject cả 2 scrapers vào `Set<StorySource>`
  - `TruyenComScraper.kt`: Refactor implement `StorySource` interface, thêm `sourceId` cho tất cả Story objects
  - `StoryRepository.kt`: Nhận `Set<StorySource>`, aggregate search/browse/genre kết quả từ tất cả sources qua `supervisorScope` parallel. `sourceFor(url)` route detail/chapter/download requests đến đúng source
  - `Story.kt` + `StoryEntity.kt`: Thêm field `sourceId` ("truyencom" | "tangthuvien")
  - DB migration v6→v7: `ALTER TABLE stories ADD COLUMN sourceId`

### Xoá bỏ

- **SortOption enum**: Xoá `SortOption(NEWEST/HOT/SELECTIVE)` — app luôn hiển thị truyện mới nhất + hoàn thành
- **Sort section trong FilterBottomSheet**: Bộ lọc chỉ còn Thể loại + Số chương
- **`categoryId` parameter**: `getStoriesByGenre()` tự resolve categoryId nội bộ từng source

### Sửa đổi (Multi-Source)

- `SearchViewModel`: Xoá `SortOption`, `updateSort()`, đơn giản hóa `applyFilter()` chỉ nhận genre + chapterFilter
- `ExploreViewModel`: Xoá tham số `categoryId` trong `getStoriesByGenre()` calls
- `SearchScreen`: Xoá sort chip display + sort section trong FilterBottomSheet
- `AppModule`: Register `MIGRATION_6_7` trong database builder
- `StoryRepositoryTest`: Mock `StorySource` thay `TruyenComScraper`, thêm `ReadingStatsDao` mock

## [1.8.2] - 2026-02-23

### Thêm mới

- **TTS Pitch Control**: Slider cao độ giọng đọc 0.5x–2.0x trong TtsControlBar. Persist qua DataStore `TTS_PITCH` key
- **TTS Voice Selection**: Enumerate Vietnamese voices khi TTS init. `setVoice()` trong TtsService + TtsManager proxy. Persist `TTS_VOICE` key
- **Cancel Download**: Nút hủy (✕) trên DownloadProgressCard. `cancelDownload()` trong StoryDetailViewModel. Notification "Đã hủy tải"
- **Download Optimization**: `currentCoroutineContext().ensureActive()` trong download loop hỗ trợ cooperative cancellation
- **Reading Statistics**: Màn hình thống kê đọc (bar chart 7 ngày, streak, stat cards). ReadingStatsEntity + DAO + ViewModel + Screen. DB v5→6
- **Lọc theo số chương**: FilterBottomSheet thêm section "Số chương" (Tất cả/200+/300+/500+/900+). Client-side filter trên JSON API, fetch 10 pages. Kết hợp genre + sort

### Fix bug

- **Tìm kiếm không hiện số chương**: `search()` dùng row-based parsing extract `totalChapters` + `author` từ HTML rows (giống `parseStoryListPage`)

- **Fix**: Chuyển chương tiếp đôi khi nhảy cuối chương — `LaunchedEffect` gọi `scrollToItem(0)` khi LazyColumn chưa render (đang hiện loading spinner). Fix: đợi LazyColumn có ≥3 items trước khi scroll. Thêm reset `scrollToEnd = false` tường minh khi bắt đầu load chương mới
- **Fix**: Brightness không phục hồi khi rời ReaderScreen — `window.screenBrightness` bị kẹt ở mức reader. Fix: thêm `DisposableEffect` reset về `-1f` (hệ thống mặc định) khi rời settings panel
- **Fix**: LRU cache `ConcurrentModificationException` ngẫu nhiên — `LinkedHashMap(accessOrder=true)` khiến `get()` reorder entries (structural modification) khi nhiều coroutine truy cập đồng thời. Fix: đổi `accessOrder=false` (insertion-order)
- **Fix**: `seekAndPlay()` thiếu WakeLock/AudioFocus/SilentPlayer — thiết bị có thể ngủ giữa TTS và mất Bluetooth headphone control. Fix: bổ sung `acquireWakeLock()`, `requestAudioFocus()`, `startSilentPlayer()`, set `isPlaying=true`
- **Fix**: `scrollPosition` không bao giờ được lưu — `saveScrollPosition()` tồn tại nhưng không gọi từ UI. Fix: thêm `LaunchedEffect` auto-save với debounce 2s + `DisposableEffect` save khi rời screen

## [1.8] - 2026-02-20

### Fix bug

- **Fix CRITICAL**: Truyện đã tải bị mất sau khi mở lại app — `StoryDao.insertStory` dùng `@Insert(REPLACE)` = DELETE + INSERT. `ChapterEntity` có `ForeignKey(onDelete=CASCADE)`. Mỗi khi background refresh story → SQLite DELETE story cũ → CASCADE xóa toàn bộ chapters đã tải → INSERT story mới không chapters. Fix: đổi `@Insert(REPLACE)` → `@Upsert` (UPDATE in-place, không trigger CASCADE). Thêm `ChapterDao.insertChaptersIgnore()` (IGNORE strategy) + `updateChapterMetadata()` cho merge an toàn. Rewrite `StoryRepository.mergeAndInsertChapters()` — chỉ INSERT chapters mới với IGNORE, update metadata chapters cũ mà không đụng content

### Thêm mới

- **Font Be Vietnam Pro**: Thêm font `.ttf` (Regular, Medium, SemiBold, Bold) vào `res/font/`, cấu hình `BeVietnamPro` FontFamily trong `Type.kt`, áp dụng cho toàn bộ `MaterialTheme.typography`
- **HomeScreen HorizontalPager**: Thay `AnimatedContent` bằng `HorizontalPager` + `PremiumTabRow` liên kết `pagerState`. Vuốt ngang qua lại giữa "Đang đọc" và "Đã tải" mượt mà
- **ReaderScreen Paged Mode**: Thêm toggle "Chế độ trang" (Paged Mode) trong `ReaderSettingsPanel`. Khi bật, nội dung hiển thị qua `HorizontalPager` từng đoạn/trang, có nút chuyển chương overlay
- **ReaderScreen Line Spacing**: Thêm tuỳ chỉnh khoảng cách dòng (1.2x, 1.5x, 1.8x) trong `ReaderSettingsPanel`, áp dụng vào `lineHeight` text style
- **FAB auto-hide**: Ẩn FAB khi cuộn xuống, hiện lại khi cuộn lên — `NestedScrollConnection` scroll direction tracking ở `StoryDetailScreen`
- **StoryCardPremium CircularProgress**: Thay `LinearProgressIndicator` dưới cùng bằng `CircularProgressIndicator` nhỏ overlay góc dưới-phải ảnh bìa
- **Glassmorphism translucent overlays**: `TtsControlBar` và `ReaderSettingsPanel` dùng containerColor alpha 0.85f/0.92f tạo hiệu ứng kính mờ premium
- **Screen Transitions**: Thêm `enterTransition`/`exitTransition` cho NavHost. Tab screens (Home/Explore/Downloads/Settings) fade crossfade 250ms. Sub-screens (Search/Detail/Reader) slide từ phải + fade 350ms. Pop transitions slide ra phải + fade
- **HomeScreen Pull-to-Refresh**: Wrap content trong `PullToRefreshBox`, gọi `viewModel.refresh()` để cập nhật thư viện
- **Blur Hero Header**: StoryDetailScreen `HeroSection` background cover image dùng `Modifier.blur(25.dp)` tạo depth effect premium kiểu Apple Books
- **Fix version Settings**: "Phiên bản 1.0" hardcoded → `"Phiên bản ${BuildConfig.VERSION_NAME}"` dynamic
- **Edge-to-edge cleanup**: Xoá `window.statusBarColor`/`window.navigationBarColor` trong `Theme.kt` (đã có `enableEdgeToEdge()` trong `MainActivity`)
- **Reader Brightness Control**: Slider độ sáng 1%-100% trong `ReaderSettingsPanel`, dùng `Activity.window.attributes.screenBrightness` điều chỉnh riêng cho reader
- **Explore Genre Filter Chips**: `LazyRow` với `FilterChip` cho 42 thể loại trên `ExploreScreen`. `ExploreViewModel` thêm `loadGenres()`, `selectGenre()`, lọc stories qua `getStoriesByGenre()` API
- **Cleanup dead imports**: Xoá `HorizontalPager`/`rememberPagerState` imports từ `ReaderScreen.kt` (paged mode đã xoá)

### Xoá bỏ

- **ReaderScreen Paged Mode**: Xoá HorizontalPager paged mode (không phù hợp UX đọc truyện dài)

### Sửa đổi

- Điều chỉnh màu highlight TTS cho chế độ Sepia (`#D0B286` deepened) và Dark (`#3A3000` dark gold) để tăng contrast
- `ReaderViewModel`: Thêm `lineSpacing` vào `ReaderState` + DataStore persistence (xoá `pagedMode`)
- `StoryCardGrid`: Thêm params `showProgress`, `progress` mặc định

- **Thay chữ tự động (Text Replacement)**: Xóa ký tự `·` (middle dot), `•` (bullet), `⋅` (dot operator) khỏi nội dung chương. User có thể thêm/xóa từ tùy chỉnh trong Settings → "Thay chữ tự động". Toggle bật/tắt đồng bộ giữa Reader settings và Settings tổng
  - `SettingsDataStore.kt`: Thêm `TEXT_REPLACEMENT_ENABLED`, `CUSTOM_REPLACEMENTS` keys
  - `TextReplacementHelper.kt` (NEW): Xóa 3 loại dấu chấm filter, JSON serialize/deserialize cho custom list
  - `ReaderViewModel.kt`: Thêm `textReplacementEnabled` vào state, apply tại `loadChapter()`, `toggleTextReplacement()` reload chapter
  - `SettingsViewModel.kt`: Thêm CRUD cho custom replacements, toggle bật/tắt
  - `SettingsScreen.kt`: Section "Thay chữ tự động" với switch + quản lý danh sách custom (thêm/xóa dialog)
  - `ReaderScreen.kt`: Switch "Thay chữ tự động" trong `ReaderSettingsPanel`
- **Chế độ Copy (Copy Mode)**: Tắt long-press TTS để cho phép chọn và sao chép văn bản. Toggle trong Reader settings, mặc định tắt, không persist
  - `ReaderViewModel.kt`: Thêm `copyMode` vào state, `toggleCopyMode()`
  - `ReaderScreen.kt`: Conditional `SelectionContainer` vs `combinedClickable`, switch trong `ReaderSettingsPanel`

### Fix bug (v1.8)

- **Fix**: Scroll nhảy cuối chương sau 30-40 chương — `scrollToEnd` flag bị tồn dư từ `loadPrevChapterOnScroll`. Thêm param `scrollToEnd` cho `loadChapter()`, mặc định `false`, chỉ `true` khi quay về chương trước
- **Fix**: TTS crash `startForeground() not allowed` khi auto-advance qua tai nghe — Thêm `setContentForAutoAdvance()` giữ foreground service sống giữa các chương, wrap `startForeground()` trong try-catch cho Android 12+
- **Fix**: Không bấm qua chương tiếp/trước bằng tai nghe — `nextChapter()`/`prevChapter()` gọi `ttsManager.stop()` → `stopForeground()` giết foreground service. Giờ dùng `pause()` khi TTS đang active để giữ foreground sống
- **Fix**: Không đọc được truyện đã tải khi offline — `getChapterContent()` giờ try-catch scraper call, fallback cached content, hiện message rõ khi chương chưa tải
- **Fix**: Double-tap tai nghe không skip chương — TWS earbuds gửi `KEYCODE_MEDIA_PLAY_PAUSE` thay vì `KEYCODE_MEDIA_NEXT`. Thêm custom double-tap detection (400ms debounce): 1 tap=play/pause, 2 taps=next, 3 taps=prev
- **Fix**: Bluetooth tai nghe hoàn toàn không điều khiển được — TTS audio đến từ `com.google.android.tts` package (process riêng), Android route media buttons cho package đó thay vì app. Thêm "Silent MediaPlayer Hack": phát file WAV im lặng qua `MediaPlayer` của app → Android associate media playback với app package → media buttons route đến `MediaSession`. Kèm `MediaButtonReceiver`, `AudioFocusRequest`, và `MediaSession` flags
- **Fix**: Tap notification mở lại app như khởi động mới — Thêm `ACTION_MAIN + CATEGORY_LAUNCHER + FLAG_ACTIVITY_NEW_TASK` trên notification intent + `launchMode="singleTask"` trong AndroidManifest
- **Thêm**: Runtime `POST_NOTIFICATIONS` permission dialog cho Android 13+

## [1.5] — 2026-02-17

### Release

- **Release chính thức v1.5** — APK `apptruyenv15.apk` (4.27MB)
  - `versionCode`: 20, `versionName`: "1.5"
  - APK naming convention: `applicationVariants.all` tự đặt tên `apptruyenvXX.apk`
  - Tạo lại keystore JKS (`release.jks`) với thống nhất store/key password
  - Thêm QUY TẮC RELEASE vào `project-rules.md`
  - R8 minify + shrink resources → 4.27MB

## [1.3.7] — 2026-02-17

### Thêm mới

- **Progressive Chapter Loading**: Truyện 3000+ chương hiển thị ngay ~1.5s thay vì 12s+
  - `TruyenComScraper`: Thêm `getFirstPageChapters()` + `getRemainingChapters()` (parallel Semaphore(3))
  - `StoryRepository`: Thêm `getFirstPageChapterList()` + `loadRemainingChapters()` + `mergeAndInsertChapters()` helper
  - `StoryDetailViewModel`: Phase 1 hiển thị ngay, Phase 2 background crawl, `observeChapters` auto-update UI
- **Nút Danh sách chương trên Bottom Bar** — Nút `FormatListNumbered` ở bên phải speed badge trong `TtsControlBar`. Mở panel danh sách chương trượt từ phải (`AnimatedVisibility` + `slideInHorizontally`). Chương đang đọc auto-scroll vào giữa, tô đậm, nền highlight. Tap chương để nhảy đọc, tap scrim để đóng. Tương thích 3 themes (Light/Dark/Sepia)
  - `ReaderViewModel`: Thêm `showChapterList`, `chapterList` vào `ReaderState`, `toggleChapterList()`, `navigateToChapter(chapter)`
  - `ReaderScreen`: Thêm `onShowChapterList` callback vào `TtsControlBar`, tạo `ChapterListPanel` composable

## [1.3.6] — 2026-02-17

### Performance Optimization — 6 fixes (4 Critical, 2 Medium)

- **Critical #1**: `StoryRepository.refreshLibraryStories()` — sequential for-loop → `coroutineScope` + `Semaphore(3)` parallel refresh (~3x faster)
- **Critical #2**: `ExploreViewModel` — tách `loadMoreJob` riêng khỏi `loadJob`, tránh loadMore cancel loadFirstPage race condition
- **Critical #3**: `StoryDetailViewModel.loadStoryDetail()` — sequential network calls → `async` parallel (chapter list + reading progress concurrent)
- **Critical #4**: `HomeScreen` staggered animation — `index * 50` delay unbounded → `(index % 8) * 50` capped 400ms max
- **Medium #7**: `DownloadsViewModel` N+1 query → aggregate `ChapterDao.getDownloadStats()` single GROUP BY query + `DownloadStats` projection class
- **Medium #8**: `StoryEntity.isInLibrary` — thêm `@Index` cho `getLibraryStories()` query performance
- **Medium #5**: `ReaderScreen` — `animateColorAsState` per paragraph → chỉ animate paragraph active TTS, non-active dùng static `Color.Transparent`
- **DB version**: 4 → 5, `fallbackToDestructiveMigrationFrom(1, 2, 3, 4)`
- **Tests**: Fix `DownloadsViewModelTest` mock cho `getDownloadStats()`, fix `HomeViewModelTest` mock `Log.d`

## [1.3.5] — 2026-02-17

### Code Review #4 — Fix 11 issues (P1-P4, M1-M4, M8, L1-L2)

- **P1**: Tách notification logic ra `DownloadNotificationHelper.kt` — `StoryDetailViewModel` không còn inject `@ApplicationContext` trực tiếp
- **P2**: Fix Flow collect leak trong `TtsManager` khi service reconnect — track Job, cancel on disconnect/reconnect/shutdown
- **P3**: Cache 4 Regex patterns trong `TruyenComScraper.cleanHtml()` companion object — tránh recompilation mỗi lần gọi
- **P4**: WebView XSS validation — `shouldOverrideUrlLoading()` chặn redirect sang domain ngoài `truyencom.com`
- **M1**: Gom 3 LRU caches (`cachedBodies`, `cachedEtags`, `cachedLastModified`) thành `CacheEntry` data class + 1 map
- **M2**: Cache `CHAPTER_URL_REGEX` trong `ReaderViewModel` companion object
- **M3**: Reuse `TruyenComScraper.USER_AGENT` constant trong `WebViewContentLoader` thay vì hardcode
- **M4**: `ThemeViewModel.appThemeMode` trả `StateFlow<AppThemeMode>` thay `StateFlow<Int>`. `MainActivity` dùng enum pattern matching
- **M8**: Cancel `loadJob` trước khi launch mới trong `ExploreViewModel.loadMore()` tránh race condition
- **L1**: Xóa biến `splashScreen` không dùng trong `MainActivity`
- **L2**: Dùng `NavigationUtils.decodeUrl()` nhất quán trong `ReaderViewModel` thay Base64 thủ công
- Cache 2 Regex trong `TtsManager.splitIntoParagraphs()` companion
- **M5**: Tách `StoryDetailScreen.kt` (732 lines) → 6 composables riêng: `HeroSection`, `ActionButtonsSection`, `DownloadProgressCard`, `ChapterListItem`, `ChapterJumpSheet`, `DownloadRangeDialog`
- **M6**: Tách `ReaderScreen.kt` main body → 2 composables riêng: `ReaderContentArea`, `ReaderTopBar`

### Fix bug

- **Fix**: Theme Sepia — text/icon không thấy rõ trên nền da. Thêm `contentColor` tường minh cho TopAppBar (`titleContentColor`/`navigationIconContentColor`/`actionIconContentColor`), TtsControlBar Surface, speed badge, và slider labels. Tất cả dùng `SepiaText` (`#3E2723`) thay vì Material defaults

## [1.3.4] — 2026-02-16

### Code Review #3 — Fix 8 issues

- **Fix**: `NavigationUtils.decodeUrl()` crash trên malformed Base64 → `try-catch` + fallback raw string
- **Fix**: `ShimmerEffect` luôn dùng Light colors → auto-detect `isSystemInDarkTheme()`, dùng `ShimmerBaseDark`/`ShimmerHighlightDark`
- **Fix**: `SettingsViewModel` magic Int themes → `ReaderThemeOption` + `AppThemeMode` enums type-safe
- **Fix**: `SettingsScreen` cập nhật dùng enum types, thêm `ThemePreview`/`ModeInfo` data classes
- **Fix**: `SettingsScreen` sai nguồn "TruyenFull.vn" → "TruyenCom.com"
- **Fix**: `DownloadsViewModel` N² flow emission — `combine().collect` → `combine().first()` snapshot
- **Fix**: `StoryRepository` genre cache không expire → thêm 1h TTL (`cachedGenresTimestamp`)
- **Fix**: `ReaderScreen` `scrollToEnd` race condition — `snapshotFlow { totalItemsCount }.first { it > 0 }` đợi content render
- **Fix**: `StoryRepository.downloadChapters()` fixed 500ms delay → adaptive delay (500ms–3s) + retry 1 lần/chapter

### Code Review #2 — Fix 13 issues

- **Fix #6**: `SearchViewModel.categoryId` cache sai khi đổi genre → reset khi `selectedGenre` thay đổi
- **Fix #8**: `StoryDetailViewModel` observer leak khi `retry()` → track `observerJob`, cancel trước re-launch
- **Fix #2**: `WebViewContentLoader` race condition `postDelayed` trên destroyed WebView → `isDestroyed` guard
- **Fix #5**: `StoryRepository.getChapterList()` N+1 query → batch fetch + `Map` lookup O(1)
- **Fix #3**: `TtsService` thiếu `@AndroidEntryPoint` → thêm Hilt annotation
- **Fix #1**: `TtsManager` lifecycle mismatch → `@Singleton` + inject `@ApplicationScope` CoroutineScope. `ReaderViewModel.onCleared()` gọi `stop()` thay `shutdown()`
- **Fix #9**: Duplicate HTML-to-text logic → extract `cleanHtml()` private function trong `TruyenComScraper`
- **Fix #10**: Hardcoded magic numbers (`50`, `200`) → constants (`MIN_CONTENT_LENGTH`, `CRAWL_DELAY_MS`)
- **Fix #4**: Silent `catch (_: Exception)` → `Log.d` trong `StoryRepository`, `HomeViewModel`, `TruyenComScraper`
- **Fix #12**: Thiếu index `reading_progress.lastReadTimestamp` → thêm `@Index`
- **Fix #13**: `StoryEntity.addedTimestamp` default `System.currentTimeMillis()` → `0`. `StoryDao.addToLibrary()` set timestamp
- **Fix #7**: `DownloadsViewModel` N-flow combine → thêm comment giải thích, giữ pattern hiện tại vì collectLatest đã handle
- **DB version**: 3 → 4, `fallbackToDestructiveMigrationFrom(1, 2, 3)`
- **ChapterDao**: Thêm `getChaptersByStoryOnce()` suspend query cho batch lookup

### Code Review — Sửa đổi

- **Production Keystore** — Tạo `release.jks`, `keystore.properties`, signing config trong `build.gradle.kts`
- **Hilt DI** — `TtsManager`, `DataStore<Preferences>` inject qua Hilt. `ReaderViewModel`, `SettingsViewModel`, `ThemeViewModel` extends `ViewModel` thay `AndroidViewModel`
- **Scraper IO safety** — `getStoriesByGenreApi()` wrap `withContext(Dispatchers.IO)`
- **Refresh thật** — `HomeViewModel.refresh()` gọi `repository.refreshLibraryStories()` thay delay giả
- **Cache giảm** — HTML cache 50 → 15 entries
- **Search history qua Repository** — `SearchViewModel` delegate qua `StoryRepository` thay truy cập `SearchHistoryDao` trực tiếp
- **API error logging** — Thêm `Log.w` cho parse errors trong scraper
- **NavigationUtils** — Centralize URL encode/decode, `AppNavigation` dùng utility thay inline Base64
- **Rename `deleteStory` → `deleteDownloads`** — Tránh nhầm lẫn (chỉ xóa downloads, không xóa story)
- **Room schema export** — `exportSchema = true` cho migration tracking
- **Dead code** — Xóa `prevParagraph` không dùng trong `ReaderViewModel`

### Thêm mới (Tests)

- `HomeViewModelTest.kt` — 4 tests (flows, refresh, sort, error handling)
- `DownloadsViewModelTest.kt` — 4 tests (display, search filter, delete, state)
- `SearchViewModelTest.kt` — 7 tests (search, history, genres, filter)
- `ExploreViewModelTest.kt` — 5 tests (pagination, refresh, error)
- `NavigationUtilsTest.kt` — 6 tests (encode/decode, special chars, Vietnamese)

### Thêm mới

- **Ẩn/hiện thanh bars khi chạm** — Chạm (tap) vào nội dung chương để ẩn TopAppBar và TtsControlBar, tạo thêm không gian đọc. Chạm lại để hiện. Chuyển từ `Scaffold` sang `Box` layout với `AnimatedVisibility` (slide in/out). Content padding animated theo trạng thái bars. Tận dụng `combinedClickable` sẵn có trên mỗi paragraph — phân biệt tap vs scroll tự động. Khi mở settings panel, bars tự hiện lại
- **Nút Lọc Truyện centered** — Khi vào SearchScreen chưa tìm/lọc, hiện nút "Lọc Truyện" ở giữa với dòng chú thích. Sau khi có kết quả, nút chuyển thành icon filter ở top-right. `FilterPromptSection` composable mới

### Sửa đổi

- **Giảm khoảng trống top bar** — Chuyển `LargeTopAppBar` → `TopAppBar` ở HomeScreen, ExploreScreen, SettingsScreen. Giảm typography từ `headlineLarge` → `titleLarge`, bỏ `exitUntilCollapsedScrollBehavior` ở ExploreScreen
- **Hiện ảnh bìa tất cả truyện** — Auto-generate coverImageUrl trong `parseStoryLink` và `getStoriesByGenreApi` dùng CDN pattern `covers/{id/1000}/{id}-{slug}_cover_large.jpg`. Helper function `buildCoverUrl` dùng chung

## [1.3.2] — 2026-02-14

### Thêm mới

- **Double-swipe chuyển chương** — Khi ở cuối chương (không cuộn được nữa), kéo lên 2 lần → sang chương tiếp. Khi ở đầu chương, kéo xuống 2 lần → về chương trước. Dùng `NestedScrollConnection.onPostScroll` phát hiện overscroll thực sự, mỗi lần vượt 80dp tính 1 swipe. Hiện banner đếm ngược ("Kéo lên 2 lần..." → "...1 lần nữa..."). TTS không đọc hướng dẫn
- **Về cuối chương trước** — Khi chuyển về chương trước (swipe down), cuộn đến cuối chương thay vì đầu chương. Thêm `scrollToEnd` flag trong `ReaderState`
- **Auto-navigate chương tiếp** — Khi đọc hết chương, hiện banner "Cuộn xuống để đọc chương tiếp" + tự động load chương mới khi scroll đến cuối + cuộn trang lên đầu chương mới. Chương cuối hiện badge "Đã hết truyện". `NextChapterSection` + `PrevChapterSection` composable themed cho cả 3 giao diện (Sáng/Tối/Sepia)

### Fix bug

- Fix Gradle `transformDebugUnitTestClassesWithAsm: InvalidUserCodeException` — AGP 9.0 `jarsOutputDir` property conflict. Thêm `android.newDsl=false` trong `gradle.properties`
- Fix `TruyenComScraperTest`: OkHttp 5.x MockWebServer package đổi `okhttp3.mockwebserver` → `mockwebserver3`, `shutdown()` → `close()`, `MockResponse().setBody()` → `MockResponse.Builder().body().code().build()`
- Fix `StoryRepositoryTest`: Thiếu 2 constructor params (`database: AppDatabase`, `appScope: CoroutineScope`) và mock `android.util.Log` cho unit test environment

### Thêm mới (10 UX/UI Improvements)

- **B7: Dark Mode toàn app** — 3 chế độ: Hệ thống / Sáng / Tối, ThemeViewModel + SettingsDataStore, FilterChip trong SettingsScreen
- **C1: Image Caching + Placeholder** — Hilt CoilModule (50MB disk + 25% memory), SubcomposeAsyncImage shimmer placeholder + error fallback icon
- **B6: Skeleton Loading Detail** — ShimmerStoryDetail composable thay thế CircularProgressIndicator trên StoryDetailScreen
- **C2: Lazy Chapter List (100/lần)** — ChapterDao paged query, StoryDetailViewModel infinity scroll, loading indicator
- **A2: Reading Progress Bar** — HomeViewModel progressMap, StoryCardPremium hiện bar + subtitle "Chương X/Y"
- **A3: Sort/Filter Home** — HomeSortOption enum (Gần đây / A→Z / Chưa xong), FilterChip row trên tab Đang đọc
- **B2: Pull-to-Refresh HomeScreen** — PullToRefreshBox wrapper trên tab Đang đọc
- **B5: Đánh dấu chương đã đọc** — CheckCircle icon + alpha 0.5 cho chương đã đọc trên StoryDetailScreen
- **B8: Badge chương chưa đọc** — SuggestionChip "Còn X chương chưa đọc" trên StoryDetailScreen
- **B9: Bottom Sheet Chapter Jump** — FAB + ModalBottomSheet với search field, nhảy đến chương bất kỳ

### Fix bug (Code Audit #5)

- Fix `TruyenComScraper`: LRU cache (`cachedBodies`, `cachedEtags`, `cachedLastModified`) dùng `LinkedHashMap` trần không thread-safe — wrap bằng `Collections.synchronizedMap()`, xóa `cacheLock` thủ công
- Fix `TtsService.acquireWakeLock()`: Không release WakeLock cũ trước khi acquire mới → orphan WakeLock khi play liên tục. Thêm `releaseWakeLock()` ở đầu
- Fix `ExploreViewModel.loadMore()`: Stale state — capture `_state.value` ở đầu function, gọi nhanh 2 lần sẽ mất data lần 1. Chuyển sang `_state.update{}`
- Fix `SearchViewModel.loadMoreFilterResults()`: Cùng bug stale state — chuyển sang `_state.update{}`
- Fix `SearchViewModel.search()`: Lưu history kể cả khi 0 kết quả — chỉ lưu khi `results.isNotEmpty()`
- Thêm comment `getStoriesByGenreApi()`: Clarify synchronous OkHttp call luôn chạy trong `withContext(Dispatchers.IO)`

### Thêm mới (Tìm kiếm offline)

- **Tìm kiếm truyện đã tải** — Thanh tìm kiếm animated trong DownloadsScreen lọc truyện theo tên (client-side, case-insensitive), icon toggle search/close trên TopAppBar, subtitle hiện số kết quả, empty state khi không tìm thấy

### Fix bug (Chapter pagination)

- Fix `TruyenComScraper`: Chỉ lấy ~55 chương thay vì toàn bộ — website phân trang 50 chương/trang nhưng scraper chỉ fetch trang 1. Thêm `getTotalChapterPages()` detect "Last »" link, `getChapterList()` crawl tất cả trang, `getStoryDetail()` lấy totalChapters chính xác từ trang cuối

### Sửa đổi (Release Preparation)

- Bật R8 `isMinifyEnabled = true` + `isShrinkResources = true` cho release build — APK giảm từ 16.1MB → 4.28MB (giảm 73%)
- Cập nhật `proguard-rules.pro` toàn diện: Kotlin, Room, Hilt, OkHttp 5, Jsoup (+ re2j dontwarn), Coil 3, DataStore, MediaSession, Compose

### Fix bug (Gradle & UI)

- Fix `SearchScreen.FilterBottomSheet`: Không scroll được khi nhiều thể loại → phần "Sắp Xếp" bị ẩn. Thêm `verticalScroll` cho Column
- Fix `settings.gradle.kts`: Toplevel `plugins {}` block không tương thích Gradle 9.2.1 → sửa thứ tự blocks + xóa stale Gradle script cache
- Fix AGP 9.0 compatibility: Xóa deprecated properties trong `gradle.properties`, xóa `kotlin-android` plugin (built-in), upgrade Hilt `2.57.1` → `2.59.1`
- Fix Gradle `NoSuchMethodError` trên `Settings_gradle`: Root cause `GRADLE_USER_HOME` trỏ qua Scoop junction symlink → trigger bug Gradle 9.x Kotlin DSL compilation (#36483). Set `GRADLE_USER_HOME` → `$HOME\.gradle`, upgrade Gradle `9.2.1` → `9.3.1`, xóa `foojay-resolver-convention` plugin không cần thiết

### Thêm mới (UX/UI Redesign)

- **UX/UI Redesign** — Nâng cấp toàn diện giao diện ứng dụng:
  - **Design System**: Google Fonts (Noto Serif + Inter), gradient colors, tonal surfaces, shimmer colors
  - **Shared Components**: `ShimmerEffect.kt` (skeleton loading), `AnimatedEmptyState.kt` (pulsing icon + stagger text), `StoryCardPremium.kt` (list + grid premium cards with press animation)
  - **HomeScreen**: LargeTopAppBar greeting, pill-shaped TabRow, StoryCardPremium, staggered enter animations
  - **ExploreScreen**: 2-column grid layout, StoryCardGrid, collapsing LargeTopAppBar, shimmer loading skeleton
  - **SearchScreen**: Rounded search bar, shimmer loading, StoryCardPremium results, filter bottom sheet with rounded chips
  - **StoryDetailScreen**: Full-width hero section with gradient scrim, cover image card, genre SuggestionChips, numbered chapter list
  - **ReaderScreen**: Noto Serif reading font, theme-aware top bar, redesigned TTS bar with prominent play button and progress
  - **DownloadsScreen**: LargeTopAppBar, storage summary card (stats), cover images, staggered animations
  - **SettingsScreen**: Sectioned cards with icons, gradient app info card, visual theme previews
  - **AppNavigation**: Filled/outlined toggle icons, rounded top corner nav bar, animated show/hide
- Dependency: `androidx.compose.animation:animation` cho staggered animations
- Font files: `noto_serif_regular.ttf`, `noto_serif_bold.ttf`, `inter_regular.ttf`, `inter_medium.ttf`, `inter_semibold.ttf`

- `drawable/ic_launcher_foreground.xml` — Vector icon quyển sách mở + sóng audio
- `drawable/ic_launcher_background.xml` — Gradient amber background cho icon
- `splash_theme.xml` — Theme splash screen (amber background + animated icon)
- `SplashScreen.kt` — Compose animated splash (scale+fade icon, staggered text, pulse glow, auto-navigate 2.5s)
- Dependency: `androidx.core:core-splashscreen:1.0.1`
- `MainActivity`: `installSplashScreen()` trước `setContent`
- `AppNavigation`: Route "splash" → startDestination, navigate Home sau animation
- `di/ApplicationScope.kt` — @Qualifier annotation cho app-scoped CoroutineScope
- `drawable/ic_notification.xml` — Monochrome book icon cho notifications
- `drawable/ic_skip_previous.xml`, `ic_play_arrow.xml`, `ic_pause.xml`, `ic_skip_next.xml` — Media control icons

### Fix bug (Code Audit #4)

- Fix `StoryRepository`: CoroutineScope leak — background refresh dùng `CoroutineScope(Dispatchers.IO)` không lifecycle → inject `@ApplicationScope` CoroutineScope
- Fix `TruyenComScraper`: `cachedEtags` và `cachedLastModified` dùng `LinkedHashMap` thường, tăng mãi không evict → đổi sang LRU LinkedHashMap
- Fix `TruyenComScraper`: `categoryIdCache` dùng `mutableMapOf` không thread-safe → `ConcurrentHashMap`
- Fix `TtsService` + `StoryDetailViewModel`: Notification dùng `R.mipmap.ic_launcher` → hiện ô vuông trắng trên Android 8+. Đổi sang `R.drawable.ic_notification` + proper action icons
- Fix `TtsService`: WakeLock acquired trên `onCreate` kể cả khi chưa play → chỉ acquire khi `play()`, release khi `stop()`
- Fix `TruyenComScraper.getStoriesByGenreApi()`: Response body không được close → wrap trong `response.use{}`
- Fix `StoryDetailScreen.DownloadRangeDialog`: Thiếu validation from <= to → auto-swap khi range ngược

### Sửa đổi (Code Smells & Improvements)

- Refactor `TtsManager`: Xóa duplicate `TtsState` data class — dùng `TtsService.TtsState` trực tiếp, loại bỏ mapping thừa
- Refactor `StoryDetailViewModel`: Xóa dead `Build.VERSION.SDK_INT >= O` check (minSdk đã là 26)
- Refactor `ReaderViewModel`: Decouple khỏi Navigation layer — dùng `Base64.decode` trực tiếp thay vì gọi `Screen.Reader.decodeUrl()`
- Opt `AppModule`: OkHttp connection pool tuning (5 connections, 30s keep-alive) + write timeout 15s
- Opt `StoryCardPremium`: Coil `crossfade(300)` cho ảnh bìa load mượt hơn
- Opt `ReaderScreen`: Thêm nút "Thử lại" khi tải chương lỗi (bên cạnh "Quay lại")
- Opt `ReaderViewModel`: Thêm `retryLoadChapter()` cho retry logic
- Refactor `TtsManager`: Implement `Closeable` interface — `close()` gọi `shutdown()` đảm bảo resource cleanup
- Refactor `StoryDetailViewModel`: Inject `NotificationManager` + `@ApplicationContext` thay vì `Application`
- Refactor `AppModule`: Thêm Hilt provider cho `NotificationManager`, đổi `fallbackToDestructiveMigration()` → `fallbackToDestructiveMigrationFrom(1, 2)`
- Refactor `StoryRepository`: Inject `AppDatabase` + dùng `withTransaction` cho `deleteStory()` atomic
- Upgrade `build.gradle.kts`: Compose BOM `2024.02.00` → `2024.09.03`, lifecycle `2.8.5`, activity-compose `1.9.2`, navigation `2.8.0`, coil `2.7.0`, hilt-navigation `1.2.0`
- Thêm `ExploreScreen`: Pull-to-refresh (kéo xuống để làm mới) với `PullToRefreshBox`
- Thêm `ExploreViewModel`: `isRefreshing` state + `pullToRefresh()` method
- **Full Dependency Upgrade** — Nâng cấp toàn bộ dependencies:
  - Kotlin `1.9.22` → `2.3.10` + Compose compiler plugin
  - Compose BOM `2024.09.03` → `2025.12.00`
  - Hilt `2.50` → `2.57.1`
  - Room `2.6.1` → `2.8.4`
  - OkHttp `4.12.0` → `5.3.2`
  - Coil `2.7.0` → `3.3.0` (package rename `coil` → `coil3`, thêm `coil-network-okhttp`)
  - Navigation `2.8.0` → `2.9.7`
  - Lifecycle `2.8.5` → `2.10.0`
  - Activity Compose `1.9.2` → `1.12.4`
  - Core KTX `1.13.1` → `1.17.0`
  - Jsoup `1.17.2` → `1.22.1`, DataStore `1.0.0` → `1.2.0`, Coroutines `1.7.3` → `1.10.2`
  - Splashscreen `1.0.1` → `1.2.0`, Media `1.7.0` → `1.7.1`
  - KSP `1.0.17` → `2.3.5`, compileSdk `35` → `36`
  - `kotlinOptions` → `kotlin.compilerOptions` (Kotlin 2.x migration)
  - Coil 3: `AsyncImage model` dùng URL string trực tiếp thay vì `ImageRequest.Builder`

### Thêm mới (Explore & Filter)

- `Genre.kt` — Data model cho thể loại truyện (name + slug + categoryId)
- `TruyenComScraper`: `getCompletedStories(page)` — scrape truyện full từ `/truyen-full/`
- `TruyenComScraper`: `getStoriesByGenre(genreSlug, page, sortType, categoryId)` — truyện full theo thể loại, hỗ trợ server-side sort via JSON API
- `TruyenComScraper`: `getStoriesByGenreApi(categoryId, sortType, page)` — gọi JSON API `/api/list/{catId}/{sort}/{page}/25`
- `TruyenComScraper`: `getCategoryId(genreSlug)` — extract `var categoryID=X;` từ genre page, cached in-memory
- `TruyenComScraper`: `getGenreList()` — parse 42 thể loại từ homepage navigation
- `TruyenComScraper`: `parseStoryListPage()` — shared helper cho listing pages
- `StoryRepository`: `getCompletedStories()`, `getStoriesByGenre()`, `getGenreList()`, `getCategoryId()` (in-memory cache)
- `ExploreViewModel.kt` — ViewModel tab Khám Phá, infinity scroll, pull-to-refresh
- `ExploreScreen.kt` — UI Khám Phá: LazyColumn + infinity scroll, StoryCard với cover image
- `SearchViewModel`: `FilterState`, `SortOption(NEWEST/HOT/SELECTIVE)`, `applyFilter()`, `loadMoreFilterResults()`, `clearFilter()`
- `SearchScreen`: ModalBottomSheet bộ lọc (thể loại FlowRow chips + sắp xếp), badge filter icon, infinity scroll filter results
- `AppNavigation`: Tab "Khám Phá" (4 tabs: Trang chủ, Khám Phá, Đã tải, Cài đặt)

### Sửa đổi

- `StoryDetailScreen`: Nút "Xem thêm" / "Thu gọn" cho phần giới thiệu truyện — `animateContentSize()` animation, chỉ hiển thị khi text bị overflow
- `SearchViewModel.SortOption`: client-side sort → server-side sort enum (NEWEST→"new", HOT→"hot", SELECTIVE→"selective")
- `SearchViewModel.SearchState`: thêm `categoryId` cached cho API calls

### Fix bug (Sort API)

- Fix sort "Mới nhất" trả về kết quả HOT — HTML tĩnh chỉ chứa danh sách HOT, tab content loaded qua AJAX. Chuyển sang gọi JSON API trực tiếp

### Fix bug (Core)

- Fix `WebViewContentLoader`: Deferred không complete khi timeout gây memory leak — thêm `deferred.complete(null)` trước destroy
- Fix `TtsService.play()`: Race condition state — set `isPlaying=true` ngay lập tức trước khi gọi `speakCurrent()`
- Fix `StoryRepository.getStoryDetail()`: Offline-first không bao giờ refresh từ web — thêm fire-and-forget background refresh
- Fix `StoryDetailViewModel`: Notification ID cứng 2001 cho tất cả truyện — dùng `storyId.hashCode()`
- Fix `DownloadsScreen`: Dialog xóa chỉ xóa nội dung tải, không xóa story khỏi library — thêm option "Xóa hoàn toàn"
- Fix `TruyenComScraper`: Cache eviction random (ConcurrentHashMap) → LRU cache (LinkedHashMap + synchronized)
- Fix `ReaderViewModel`: Settings font/theme không persist khi thay đổi trong reader → thêm DataStore persistence
- Fix `AppNavigation`: URL double-encode (%2F → %252F) — chuyển sang Base64 URL-safe encoding
- Fix `TtsManager.bindService()`: Thêm guard ngăn double-bind khi tạo nhiều instances
- Remove `ReaderViewModel.currentStoryId` dead code (khai báo nhưng không bao giờ dùng)

### Sửa đổi (Core)

- `StoryRepository`: `e.printStackTrace()` → `Log.e()` cho production logging
- `TruyenComScraper`: Cache thread-safety dùng `synchronized(cacheLock)` thay vì ConcurrentHashMap
- Fix `StoryDetailViewModel`: `NOTIFICATION_ID` trùng TtsService (1001→2001) — notification download ghi đè TTS
- Fix `StoryRepository.getStoryDetail()`: preserve `isInLibrary`/`addedTimestamp` khi re-insert story
- Fix `WebViewContentLoader`: double `WebView.destroy()` crash — thêm `AtomicBoolean` guard
- Fix `TruyenComScraper`: cache maps `HashMap` → `ConcurrentHashMap` cho thread-safety
- Fix `TruyenComScraper`: `cachedBodies` phình vô hạn → giới hạn 50 entries + eviction
- Fix `ReaderViewModel`: `nextChapter`/`prevChapter` chỉ auto-play khi TTS đang active
- Fix `ReaderScreen`: contentDescription skip buttons sai (`Đoạn` → `Chương`)

### Thêm mới (Search & Download)

- `SearchHistoryEntity` + `SearchHistoryDao` — Lưu lịch sử tìm kiếm vào Room
- `SearchScreen`: hiển lịch sử tìm kiếm, click auto-search, xóa/xóa tất cả
- `StoryDetailViewModel.downloadRange(from, to)` — Tải chương theo khoảng
- `StoryDetailScreen`: Dialog chọn "Từ chương X → Đến chương Y" với smart defaults
- Download completion notification với NotificationChannel
- Hiển số chương lỗi trong download progress card
- `TruyenComScraper`: scrape cover image URL (`img.book, .book img`, etc.)
- `Story` + `StoryEntity`: thêm `coverImageUrl`
- `StoryDetailScreen`: hiển ảnh bìa với Coil AsyncImage
- `TruyenComScraper.fetchDocument`: smart HTTP caching (ETag, Last-Modified, 304 handling)
- Dependency mới: `io.coil-kt:coil-compose:2.5.0`

### Thêm mới (WebView & Retry)

- `WebViewContentLoader.kt` — Headless WebView cho chapter content JS-rendered
- `TruyenComScraper`: retry 3x + exponential backoff (1s, 2s, 4s)
- `TruyenComScraper`: HTTP status code checking (throw on non-2xx)
- `StoryRepository.DownloadProgress`: thêm `errors` counter

### Sửa đổi (Scraper)

- `TruyenComScraper`: reorder CSS selectors (`#chapter-c` ưu tiên)
- `TruyenComScraper.getChapterContent()`: OkHttp first → WebView fallback
- `StoryRepository.downloadChapters()`: track và báo lỗi failed chapters

### Fix bug (Initial)

- Fix `TruyenComScraper`: response body leak — `response.use{}` (BUG #1)
- Fix `TtsManager`: CoroutineScope leak — cancel job in `shutdown()` (BUG #2)
- Fix `StoryDetailViewModel`: Flow `collect` blocking — dùng `launchIn()` (BUG #3)
- Fix `StoryDetailViewModel`: duplicate download logic — extract `performDownload()` (BUG #4)
- Fix `TtsService`: `speakCurrent()` no init check — guard + pending queue (BUG #5)
- Fix `AppModule`: Room no migration — `fallbackToDestructiveMigration()` (BUG #6)

### Sửa đổi (Optimization)

- Opt `DownloadsViewModel`: N+1 query → `combine()` flows (OPT #4)
- Opt `SearchViewModel`: missing debounce → Job cancellation (OPT #3)
- Opt `TruyenComScraper`: inject `OkHttpClient` via Hilt (OPT #9)
- Opt `TruyenComScraper`: cache Regex patterns in companion (OPT #10)
- Opt `StoryDetailScreen`: deprecated `progress` Float → lambda (OPT #6)
- Opt `ReaderViewModel`: remove unused `wasPlaying` field (OPT #5)

### Thêm mới (Tests)

- `TruyenComScraperTest.kt` — 13 unit tests (regex, URL, content)
- `StoryRepositoryTest.kt` — 11 unit tests (cache, search, download, progress)
- `TtsManagerTest.kt` — 10 unit tests (paragraph splitting)
- Dependencies: `mockk:1.13.9`, `coroutines-test:1.7.3`, `mockwebserver:4.12.0`

### Fix bug (Build)

- Fix `settings.gradle.kts`: `dependencyResolution` → `dependencyResolutionManagement`
- Fix `StoryDetailScreen.kt`: `LinearProgressIndicator` progress lambda → Float
- Fix `ReaderScreen.kt`: Thêm `@OptIn(ExperimentalMaterial3Api::class)` cho `FilterChip`
- Fix Compose BOM `2024.01.00` → `2024.02.00` (resolve `KeyframesSpec.at()` NoSuchMethodError)

### Thêm mới (Project setup)

- `gradlew.bat` + `gradlew` + `gradle-wrapper.jar` (Gradle wrapper)
- `local.properties` (Android SDK path)
- Launcher icons (`mipmap-anydpi-v26/ic_launcher.xml`)
- `docs/` folder: architecture, progress, changelog, scraping, contributing
- `AGENTS.md` + `.agent/rules/` (AI agent documentation rules)
- TTS: Tô màu đoạn đang đọc (highlight paragraph)
- TTS: Nhấn giữ đoạn text để đọc từ đó (long-press seek)
- TTS: Slider tốc độ 0.5x–2.0x (bước 0.05, bao gồm 1.3/1.35/1.4)
- TTS: `TtsService` — Foreground Service chạy ngầm, notification controls
- TTS: `MediaSession` — điều khiển tai nghe (play/pause/next/prev)
- TTS: WakeLock — tiếp tục đọc khi tắt màn hình
- Dependency: `androidx.media:media:1.7.0`

### Fix bug (Offline & Settings)

- Offline reading: truyện đã tải giờ đọc được khi không có mạng
- Downloads: không bị mất sau khi bật lại mạng
- Settings: lưu tốc độ TTS, cỡ chữ, giao diện vào DataStore
- Settings crash: fix duplicate DataStore delegate → tạo `SettingsDataStore.kt` singleton
- Default speed: fix race condition `setSpeed()` gọi trước service bind → `pendingSpeed`

### Thêm mới (TTS auto-advance)

- TTS: Tự chuyển chương tiếp khi đọc xong (`observeTtsForAutoAdvance`)
- TTS: Tự play khi bấm chương trước/sau hoặc auto-advance
- TTS: Nút skip (◀▶) trong TTS bar giờ chuyển chương (bỏ thanh bottom riêng)
- `SettingsViewModel` — ViewModel + DataStore cho trang Cài đặt
- `SettingsDataStore.kt` — singleton DataStore dùng chung
- `ChapterDao.getChaptersByStoryOnce()` — suspend query cho offline fallback

### Cần làm

- Tune CSS selectors cho scraper
- Unit tests
- TTS: Tô màu đoạn đang đọc
- TTS: Nhấn giữ để đọc từ đoạn đó
- TTS: Background playback + headphone controls

---

## [1.0.0-alpha] — 2026-02-11

### Thêm mới

- **Project structure**: Kotlin + Jetpack Compose + Material 3 + Hilt + Room
- **Data layer**:
  - Domain models: `Story`, `Chapter`, `ReadingProgress`
  - Room entities + DAOs + `AppDatabase`
  - `TruyenComScraper` (OkHttp + Jsoup) cho truyencom.com
  - `StoryRepository` (caching, download flow, reading progress)
- **TTS**: `TtsManager` với giọng Việt Nam, play/pause/stop, speed 0.5x–2.0x, paragraph navigation
- **UI screens** (100% tiếng Việt):
  - Trang chủ — tabs Đang đọc / Đã tải
  - Tìm kiếm — input, kết quả, loading states
  - Chi tiết truyện — info, download buttons, chapter list
  - Đọc chương — content, TTS controls, font/theme settings (sáng/tối/sepia)
  - Đã tải — list, dung lượng ước tính, xóa
  - Cài đặt — TTS speed, font size, theme
- **Navigation**: Bottom nav (Trang chủ / Đã tải / Cài đặt)
- **DI**: Hilt `AppModule`
- **Theme**: Warm amber/gold palette, light/dark/sepia

### Ghi chú kỹ thuật

- Scraper CSS selectors cần tune khi test với HTML thực tế
- Chapter content có thể cần WebView fallback nếu JS-rendered
- minSdk 26 (Android 8.0), targetSdk 34
