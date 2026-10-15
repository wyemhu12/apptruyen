# Kế hoạch triển khai: Đăng nhập nguồn truyện, tích hợp Wikidich & Gỡ bỏ TruyenFull, TruyenCom

> **Trạng thái**: Completed
> **Revision**: 6
> **Ngày tạo**: 2026-10-13
> **Planner**: @planner (gemini-3.8-flash)

## Technical Plan

### 1. Mục Tiêu (Goal)

1. **Tuân thủ ranh giới kiến trúc Clean Architecture [BLOCKER B1, m-l]**:
   - Xây dựng interface `ISourceAuthRepository` và implementation `SourceAuthRepository` tại tầng `data/repository/`, bind qua `@Binds` trong `di/RepositoryModule.kt`.
   - Đặt `SessionVerifyResult` và `SourceAuthConfig` trong `data/model/SourceAuth.kt` (tầng `data/model/`) để ViewModel không cần import từ `data/remote/auth` [m-l].
   - Các UI ViewModel (`SourceLoginViewModel`, `SettingsViewModel`, `SearchViewModel`, `ExploreViewModel`) và `StoryRepository` CHỈ giao tiếp với `ISourceAuthRepository`, TUYỆT ĐỐI không inject trực tiếp `SourceAuthManager` (tầng `data/remote/auth`).
2. **Tích hợp nguồn truyện mới: Wikidich (`wikicv.org`) [MAJOR M2, M10, M2', N1, N2, X4, X5, m-h, m-m]**:
   - Triển khai `WikidichScraper` kế thừa `BaseScraper`, thực hiện đầy đủ hợp đồng `StorySource`:
     - Chi tiết truyện (`getStoryDetail`): parse title, tác giả, thể loại, mô tả, ảnh bìa, trạng thái từ `.cover-info`, `.book-desc`. Khi tạo `Story` gán tường minh `sourceId = "wikidich"` [m-m].
     - Danh sách chương (`getFirstPageChapters`, `getRemainingChapters`, `getChapterList`) [N1, X4, m-h]:
       - Trích xuất `bookId`, `signKey` và tham số cắt chuỗi từ inline script trang truyện (`var bookId = "..."`, `var signKey = "..."`, trích xuất số nguyên `cut` từ regex `text\.substring\((\d+)\)` trong hàm `fuzzySign`). Nếu không tìm thấy hoặc `cut !in 0..text.length`, ném `ScraperException.ParseException` [m-h].
       - Tính toán chữ ký: `sign = signFunc(fuzzySign(signKey + start + size, cut))` với `start = index`, `size = 99`, `signFunc` = SHA-256 hex 64 ký tự thường (`MessageDigest.getInstance("SHA-256")`).
       - Gọi AJAX endpoint: `GET https://wikicv.org/book/index?bookId={id}&start={start}&size=99&signKey={key}&sign={sign}`.
       - Phân trang & Điều kiện dừng rõ ràng [X4]:
         - **Trang 1 (`start = 0, size = 99`)**: Trích xuất các thẻ `li.chapter-name a[href^="/truyen/"]`. Nếu trang 1 rỗng (không có bất kỳ link chương nào), ném `ScraperException.ParseException("Không trích xuất được danh sách chương từ máy chủ Wikidich")`.
         - **Các trang tiếp theo**: Kiểm tra xem fragment hiện tại có thẻ liên kết phân trang `ul.pagination li a[data-action="loadBookIndex"][data-start="${currentStart + size}"]` hay không.
           - Nếu có: tiếp tục tải trang kế với `start = currentStart + size`.
           - Nếu không có: **ĐÃ HẾT TRANG**! Dừng vòng lặp phân trang bình thường và trả về toàn bộ danh sách chương đã thu thập. TUYỆT ĐỐI KHÔNG ném ParseException khi hết trang [X4].
           - Đặt giới hạn an toàn `maxPages = 200` (~20.000 chương) để ngăn lặp vô tận.
     - Nội dung chương (`getChapterContent`): trích xuất các đoạn văn trong `#bookContentBody`, loại bỏ quảng cáo và lọc sạch watermark `【❁.*\(Wikidich\).*❁】` để TTS không đọc nhầm.
     - Tìm kiếm (`search`) & Duyệt thể loại / hoàn thành [N2, X5]:
       - Selector danh sách truyện: Trích xuất từ khối `.book-item` (đối chiếu khảo sát thực tế `wikicv_home.html:458-481`): Tiêu đề & URL (`.info-col a[href^="/truyen/"]`, `h5.book-title`), Tác giả (`.book-author a`), Ảnh bìa (`.cover-col img`), Thể loại/Trạng thái (`.book-publisher a`, `.book-gender`).
       - Tìm kiếm (`search`): truy vấn `https://wikicv.org/tim-kiem?qs=1&q={keyword}`. Khi HTML chứa marker `Đăng nhập để xem nội dung` và không có danh sách truyện → ném `ScraperException.LoginRequiredException("wikidich")`.
       - Duyệt hoàn thành (`getCompletedStories`): truy vấn URL hoàn thành thật `https://wikicv.org/tim-kiem?status=5794f03dd7ced228f4419191&qs=1` (id `5794f03dd7ced228f4419191` là Hoàn thành, trích từ `wikicv_home.html:502`) [N2].
       - Duyệt thể loại (`getStoriesByGenre`) đồng nhất 100% với HTML thật (`wikicv_home.html` khối `div.tag-tab`) [X5]:
         - Bảng ánh xạ slug thể loại sang query string chuẩn:
           - `"tien-hiep"` -> `"qs=2&tag=5794f03dd7ced228f44191ad&genre=5794f03dd7ced228f44191a4"`
           - `"huyen-huyen"` -> `"qs=2&genre=57d17c94d7ced218fcd2734b"`
           - `"vo-hiep"` -> `"qs=2&genre=5794f03dd7ced228f44191a3"`
           - `"xuyen-khong"` / `"xuyen-viet"` -> `"qs=1&tag=5794f03dd7ced228f44191af"`
           - `"trong-sinh"` -> `"qs=2&tag=5794f03dd7ced228f44191b4"`
           - `"ngon-tinh"` -> `"gender=5794f03dd7ced228f4419195&qs=1"`
           - `"do-thi"` / `"hien-dai"` -> `"qs=2&age=5794f03dd7ced228f441919d"`
           - `"vong-du"` -> `"qs=2&genre=5794f03dd7ced228f44191a5"`
           - `"mat-the"` -> `"qs=2&tag=5794f03dd7ced228f44191ac"`
           - `"khoa-huyen"` -> `"qs=2&age=5794f03dd7ced228f441919e&tag=5794f03dd7ced228f44191c6"`
         - Ghép `completedOnly`: nối thêm `&status=5794f03dd7ced228f4419191`.
         - URL đầy đủ: `https://wikicv.org/tim-kiem?{queryString}{completedQuery}&p={page}`.
         - Nếu slug lạ không nằm trong bảng hỗ trợ của Wikidich, scraper trả về `emptyList()` ngay mà không gọi mạng [X5].
       - Danh sách thể loại (`getGenreList`): trả về danh sách thể loại chuẩn hóa (slug tiếng Việt). Trong `StoryRepository`, dùng phép hợp (union) danh sách thể loại tiếng Việt chuẩn hóa giữa các nguồn để UI không bao giờ bị rỗng thể loại.
   - Bổ sung unit test `WikidichScraperTest` đáp ứng quy tắc `04-scraping.md` (bao gồm: search happy path với `.book-item`, detail, chapter list dừng phân trang bình thường, chapter content loại bỏ ads/watermark, search error/login required, slug lạ trả `emptyList()`).
3. **Xây dựng hạ tầng đăng nhập nguồn truyện (Source Authentication) [MAJOR M1, M3, M6, M8, M1', M6', N3, N4, X2, Y1, m-c, m-f, m-g, m-o, m-r]**:
   - Tách interface `CookieStore` trừu tượng hóa `android.webkit.CookieManager` để `SourceAuthManager` và `SourceAuthRepository` có thể unit test 100% trên JVM mà không phụ thuộc Android runtime.
   - Cơ chế phát hiện đăng nhập chính xác (S5-1):
     - Chỉ quét domain chính của nguồn (`https://wikicv.org`), LOẠI BỎ domain diễn đàn `forum.dichtienghoa.com` khỏi danh sách kiểm tra đăng nhập Wikidich.
     - Xác nhận đăng nhập hợp lệ bằng 2 lớp bảo vệ: (1) cookie `express.sid` tồn tại và không rỗng trên `https://wikicv.org`; (2) URL WebView đã chuyển hướng về `wikicv.org` sau `/auth/dth/callback` VÀ probe `verifySessionOnline` xác nhận phiên hợp lệ.
     - Probe xác minh phiên (`verifySessionOnline`) [M1', X2, m-r]: CHỈ gọi duy nhất `https://wikicv.org/tim-kiem?qs=1&q=tien`. Đóng `Response` bằng `response.use { ... }` [m-r]. Không chứa marker `"Đăng nhập để xem nội dung"` -> `true`; có marker -> `false`; lỗi mạng/timeout -> `false` + thông báo lỗi kết nối tiếng Việt, KHÔNG hạ authState hay logout user khi lỗi mạng.
     - Cơ chế dán cookie PC an toàn bằng Client cô lập & Xóa cookie cũ sạch sẽ [N3, X2, Y1, m-o]:
       - Hợp đồng trả về `sealed interface SessionVerifyResult` (đặt trong `data/model/SourceAuth.kt` [m-l]):
         - `data object Success : SessionVerifyResult`
         - `data class InvalidCookie(val message: String = "Chuỗi cookie không hợp lệ, thiếu token hoặc phiên không hoạt động") : SessionVerifyResult`
         - `data class NetworkError(val message: String = "Không thể kết nối đến máy chủ để xác minh phiên, vui lòng thử lại") : SessionVerifyResult`
       - Quy trình dán cookie an toàn (chống đụng độ cookie cũ / an toàn TOCTOU) [X2, Y1]:
         - Request probe dán cookie KHÔNG dùng client có CookieJar chung của app. Tạo client cô lập không có CookieJar:
           `val probeClient = okHttpClient.newBuilder().cookieJar(CookieJar.NO_COOKIES).build()`
         - Gửi probe request GET tới `https://wikicv.org/tim-kiem?qs=1&q=tien` kèm header `Cookie: express.sid={token}` và đóng response bằng `response.use { }` [m-r].
         - Nhờ `CookieJar.NO_COOKIES`, header `Cookie` tạm không bị `BridgeInterceptor` ghi đè bằng cookie cũ, và `Set-Cookie` của server không bị ghi vào `CookieManager` [X2].
         - Nếu probe trả về `SessionVerifyResult.Success`:
           1. **Xóa sạch mọi bản cookie `express.sid` cũ** [Y1]: Gọi xóa cookie (set `express.sid=; Path=/; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT` cho cả host-only `https://wikicv.org` và domain `.wikicv.org`) để triệt tiêu cookie GUEST cũ gây xung đột session.
           2. **Ghi MỘT cookie duy nhất host-only** [Y1]: Ghi cookie chuẩn host-only cho `https://wikicv.org`:
              `cookieStore.setCookie("https://wikicv.org", "express.sid=$token; Path=/; Secure; Max-Age=2592000")`, gọi `flush()`.
           3. Cập nhật `_authState.value = true` và `clearLoginRequired("wikidich")`.
         - Có marker -> Hỏng/hết hạn: KHÔNG ghi vào `CookieStore` (giữ nguyên cookie cũ), trả về `InvalidCookie`.
         - Lỗi mạng / timeout -> KHÔNG ghi vào `CookieStore`, giữ nguyên cookie cũ và `authState`, trả về `NetworkError`.
   - Đồng bộ trạng thái phiên & đăng xuất sạch sẽ:
     - `logout(sourceId)`: xóa sạch cookie bằng cách set cookie hết hạn `Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT` cho cả `wikicv.org` và domain gốc `.wikicv.org`, gọi `flush()`.
     - `markLoginRequired(sourceId)`: hạ `_authState.value = false` và cập nhật `loginRequiredSources.update { it + sourceId }`.
     - `clearLoginRequired(sourceId)`: xóa khỏi set khi đăng nhập hoặc khi request thành công qua `update { it - sourceId }` [m-a].
   - Bảo mật & Vòng đời WebView [M6', N4, m-c, m-f, m-g]:
     - Xóa hoàn toàn debug log raw cookie ở `WebViewCookieHandler.kt` và xóa các hàm rò rỉ cookie cũ.
     - Cấu hình loại trừ cookie WebView khỏi Auto Backup [N4]: cấu hình `domain="root"` và loại trừ toàn bộ thư mục `app_webview/` trong cả `data_extraction_rules.xml` (API 31+) và `backup_rules.xml` (API <= 30); khai báo trong `AndroidManifest.xml`. Thêm bước verify lint.
     - `SourceLoginScreen`: cài đặt `shouldOverrideUrlLoading` chỉ cho phép host `wikicv.org`, `wikicv.net`, `forum.dichtienghoa.com`; mở host lạ bằng trình duyệt ngoài (`Intent.ACTION_VIEW`); TUYỆT ĐỐI không dùng `addJavascriptInterface`.
     - Quản lý vòng đời [m-c, m-g]: `AndroidView(factory = { ... }, onRelease = { it.stopLoading(); it.destroy() })`; dùng `rememberSaveable` lưu `Bundle` trạng thái WebView qua `webView.saveState(bundle)` và `webView.restoreState(bundle)` để giữ URL trang đăng nhập hiện tại khi xoay màn hình (kỳ vọng Manual Test: giữ đúng URL trang đăng nhập hiện tại, không reload lại trang trắng [m-g]). Bỏ `setAcceptThirdPartyCookies` không cần thiết [m-f].
4. **Xử lý `LoginRequiredException` xuyên suốt [MAJOR M4, m-a, m-r]**:
   - Trong `StoryRepository.kt`:
     - Sửa khối `catch` cấp trang trong `getCompletedStories` và `getStoriesByGenre` để KHÔNG nuốt `LoginRequiredException` và `CancellationException`.
     - Ở catch bao ngoài của từng source: rethrow `CancellationException` nếu `e is CancellationException` [m-r].
     - Giữ nguyên logic hiện tại của `getStoriesByGenre` (`StoryRepository.kt:191-194`), KHÔNG tự ý thêm `circuitBreaker.recordFailure` vào luồng lọc thể loại [m-r].
     - Bắt `LoginRequiredException` ở catch ngoài, gọi `authRepository.markLoginRequired(source.sourceId)`, không ghi nhận failure vào `circuitBreaker`. Khi nguồn trả kết quả thành công, gọi `authRepository.clearLoginRequired(source.sourceId)` [m-a].
   - Hiển thị banner nhắc đăng nhập tiếng Việt ở cả `SearchScreen` và `ExploreScreen`.
5. **Gỡ bỏ nguồn truyện TruyenFull & TruyenCom và Bảo toàn tương thích ngược [MAJOR M7, M10, M7', N5, X3, m-b, m-e, m-n, m-p, m-r]**:
   - Gỡ khỏi `di/ScraperModule.kt`, xóa 2 file scraper và test của TruyenCom.
   - Cập nhật `WebViewContentLoader.kt`: thay allowlist sang `wikicv.org`, `wikicv.net`.
   - Chuẩn hóa DataStore `ENABLED_SOURCES` nguyên tử & Ranh giới kiến trúc [N5, X3, m-n, m-p, m-r]:
     - Đặt các hằng số nguồn truyện `SourceIds` trong `data/local/SettingsDataStore.kt`:
       `ALL_ACTIVE_SOURCES = setOf("tangthuvien", "sstruyen", "wikidich")`, `DEPRECATED_SOURCES = setOf("truyencom", "truyenfull")`. `SettingsViewModel` tham chiếu từ đây, TUYỆT ĐỐI không để `data/local` import ngược lên `ui/settings` [X3].
     - Trong `SettingsViewModel`, giữ alias companion object: `val ALL_SOURCES = SourceIds.ALL_ACTIVE_SOURCES` để tránh gãy các lời gọi hiện có từ UI (`SettingsScreen.kt:366,381`) [m-p].
     - Sử dụng `DataStore<Preferences>` sẵn có được provide trong `AppModule.kt`: thêm key `SettingsKeys.IS_WIKIDICH_MIGRATED = booleanPreferencesKey("is_wikidich_migrated")`.
     - Extension function `DataStore<Preferences>.migrateWikidichEnabledSourcesIfNeeded()`: đọc cờ trước, nếu đã migrate (`true`) thì return ngay không gọi `edit` [m-n]. Nếu chưa migrate, chạy trong `edit { }`: loại bỏ các nguồn deprecate, thêm `"wikidich"`, set cờ `true` [X3].
     - Điểm kích hoạt migration: gọi trong `StoryRepository.activeSources()` BÊN NGOÀI khối `try/catch` để không nuốt lỗi migration hoặc cancellation [m-r] ĐỒNG THỜI gọi trong `SettingsViewModel.init`.
     - Sau khi migrate: hàm `normalizeEnabledSources` giữ nguyên lựa chọn tắt nguồn của người dùng (tuyệt đối không tự ý bật lại `wikidich`).
   - Tương thích ngược Room: giữ nguyên dữ liệu đã lưu trong DB v9, `getChapterContent` ưu tiên trả dữ liệu từ Room offline; khi mở truyện chưa tải của nguồn đã gỡ, ném thông báo tiếng Việt chuẩn xác: `"Nguồn truyện này không còn hoạt động hoặc không được hỗ trợ"`.
   - `ReaderViewModel`: đặt `tryConstructChapterUrl` là `internal` và bổ sung unit test trong file test mới `ReaderViewModelTest.kt` [m-e, m-k].
   - Cập nhật `StoryCardPremium.kt` hỗ trợ badge cho cả nguồn cũ và mới.
6. **Tuân thủ Quality Gate [MAJOR M9, BLOCKER X1, m-i, m-q]**:
   - TUYỆT ĐỐI BỎ lệnh `git checkout -- TangThuVienScraper.kt` trong mọi bước. File này có thay đổi chưa commit của người dùng, không được hoàn tác [BLOCKER X1].
   - KHÔNG chạy `ktlintFormat` toàn module. Thay vào đó, format thủ công (manual format) các vi phạm ktlint trong phạm vi của task (`StoryRepository.kt:220,238,241`, `ScraperModule.kt:36`, `AppNavigation.kt:86,332`, `ReaderViewModel.kt:428`, `SettingsScreen.kt:46,394`, `SettingsViewModel.kt:103,129`) và viết code mới tuân thủ ktlint ngay từ đầu [BLOCKER X1].
   - Tiêu chí nghiệm thu ktlint [m-q]: Output của `.\gradlew.bat ktlintCheck` không còn bất kỳ dòng vi phạm nào thuộc các file trong bảng Files của plan (các vi phạm còn lại chỉ ở `TangThuVienScraper.kt` và `StorySource.kt:47:87` ngoài bảng Files). Giữ nguyên logic `StorySource.kt` [m-i].

---

### 2. Pre-Work Reading

Đã kiểm tra và nghiên cứu các tài liệu và mã nguồn:
- `AGENTS.md`, `.agents/rules/00-core.md`, `.agents/rules/01-coding.md`, `.agents/rules/02-quality.md`, `.agents/rules/04-scraping.md`, `.agents/rules/05-plan-review.md`, `.agents/rules/06-docs.md`.
- `docs/lessons-learned.md`: các bài học về xử lý lỗi, circuit breaker, an toàn shell, và kiểm soát trạng thái phê duyệt.
- `docs/progress.md`: lịch sử thêm/xóa nguồn (MeTruyenChu bị xóa ngày 2026-05-30, TruyenFull được thêm ngày 2026-05-30).
- `docs/scraping.md`: thông số CSS selectors, URL patterns và cấu trúc các nguồn truyện hiện tại.
- `docs/architecture.md`: cấu trúc Clean Architecture + MVVM, Room Database v9, luồng tương tác giữa UI, Repository, Remote và Local.
- File plan và kết quả `Audit Run 1`, `Audit Run 2`, `Audit Run 3` của Lead Reviewer (Claude Code CLI).
- Khảo sát mã nguồn thực tế:
  - `di/ScraperModule.kt`, `di/AppModule.kt`, `di/RepositoryModule.kt`
  - `data/remote/StorySource.kt`, `data/remote/BaseScraper.kt`, `data/remote/ScraperException.kt`, `data/remote/ScraperCircuitBreaker.kt`, `data/remote/WebViewContentLoader.kt`, `data/remote/cookie/WebViewCookieHandler.kt`
  - `data/local/SettingsDataStore.kt`: khảo sát `SettingsKeys`, injection `DataStore<Preferences>`.
  - `data/repository/IStoryRepository.kt`, `data/repository/StoryRepository.kt`: khảo sát hàm `activeSources()`.
  - `ui/search/SearchViewModel.kt`, `ui/search/SearchScreen.kt`, `ui/explore/ExploreViewModel.kt`, `ui/explore/ExploreScreen.kt`
  - `ui/settings/SettingsViewModel.kt`, `ui/settings/SettingsScreen.kt`, `ui/navigation/AppNavigation.kt`, `ui/components/StoryCardPremium.kt`, `ui/reader/ReaderViewModel.kt`
  - `app/src/test/java/com/personal/apptruyen/ui/explore/ExploreViewModelTest.kt`: khảo sát 5 lời gọi constructor tại dòng 47, 63, 78, 94, 106.
- Khảo sát HTML thực tế từ `build/` (cập nhật ngày 2026-10-13):
  - `build/real_book_index.html` (34,955 bytes) & `build/real_book_index_p2.html` (35,193 bytes): khảo sát 2026-10-13 từ truyện thật trên `wikicv.org`; danh sách chương `li.chapter-name a[href^="/truyen/"]`; phân trang `.pagination li a[data-action="loadBookIndex"]` chứa `data-start` và `data-size`.
  - `build/get-book.js`: script `loadBookIndex(f, e, d)` với chữ ký SHA-256 thuần JS `signFunc(fuzzySign(signKey + f + e))`.
  - `wikicv_home.html`: khảo sát 2026-10-13; khối truyện `.book-item` (`lines 458-481`), trạng thái Hoàn thành `status=5794f03dd7ced228f4419191` (`line 502`).
  - `wikicv_story.html`: khảo sát 2026-10-11 → 2026-10-13; trích xuất `bookId` (`line 1936`), `signKey` (`line 1940`), `fuzzySign` (`line 1938`): regex `text\.substring\((\d+)\)`, `volume-list` (`line 1146`), `.book-desc-detail` (`line 1966`), `.cover-info h2` (`line 293`).
  - `wikicv_chap.html`: khảo sát 2026-10-11 → 2026-10-13; `#bookContentBody` (`line 409`), watermark `【❁ Nguyên Hà (Wikidich) ❁】` (`line 409`).
  - `wk_search.html`: khảo sát 2026-10-11 → 2026-10-13; marker chưa đăng nhập `Đăng nhập để xem nội dung` (`line 264`).
  - `wk_auth.html`: 302 redirect từ `https://wikicv.org/auth/dth` sang `https://forum.dichtienghoa.com/dth/auth?code=...`.
  - `dth_login.html`: trang đăng nhập NodeBB diễn đàn Dịch Tiếng Hoa.

---

### 3. Assumptions & Cross-Task Dependencies

1. **Ranh giới kiến trúc Clean Architecture [B1]**:
   - Tầng UI (`ui/**`) tuyệt đối không import hay inject bất kỳ class nào từ `data/remote/**`.
   - Toàn bộ nghiệp vụ xác thực nguồn được bọc qua interface `ISourceAuthRepository` tại `data/repository/`.
2. **Tương thích ngược Room**: Database Room v9 (`AppDatabase.kt`) giữ nguyên schema, bảng `stories` có `sourceId`. Truyện đã tải có `sourceId = "truyencom"` hoặc `"truyenfull"` được `StoryDao` và `ChapterDao` đọc offline trực tiếp, không phụ thuộc scraper.
3. **Cơ chế tải chương Wikidich [M2, M2', N1]**:
   - Danh sách chương của Wikidich không nằm trong HTML tĩnh mà được gọi qua AJAX endpoint:
     `GET https://wikicv.org/book/index?bookId={id}&start={start}&size={size}&signKey={key}&sign={sign}`
   - Tham số: `sign = signFunc(fuzzySign(signKey + start + size, cut))`.
   - `fuzzySign(text, cut)`: trích xuất số nguyên `cut` từ regex `text\.substring\((\d+)\)` trong hàm `fuzzySign` của HTML trang truyện (`wikicv_story.html`). Nếu không tìm thấy, ném `ScraperException.ParseException("Không tìm thấy tham số cắt chuỗi fuzzySign")`.
   - `signFunc(text)`: SHA-256 hex 64 ký tự thường bằng `MessageDigest.getInstance("SHA-256")`.
   - Phân trang: fragment trả về chứa pagination HTML. Đọc các link chương `li.chapter-name a[href^="/truyen/"]` và các thẻ `a[data-action="loadBookIndex"]` để lấy danh sách đầy đủ.
4. **Xác thực WebView & Bảo vệ Cookie [M1', M6', N4, m-c, m-f, m-g]**:
   - Chỉ tải các host được cấp phép: `wikicv.org`, `wikicv.net`, `forum.dichtienghoa.com`.
   - Lưu trữ và khôi phục trạng thái WebView qua `saveState`/`restoreState` bằng `rememberSaveable` để giữ URL trang đăng nhập hiện tại khi xoay màn hình [m-c, m-g].
   - Không kích hoạt `setAcceptThirdPartyCookies` không cần thiết [m-f].
   - Loại trừ toàn bộ thư mục `app_webview/` khỏi Google Cloud Auto Backup và Device Transfer qua `domain="root"` trong `data_extraction_rules.xml` và `backup_rules.xml` [N4].

---

### 4. Verified Versions Table

| Thư viện / Thành phần | Version hiện tại | Version đề xuất | Latest đã xác minh | Nguồn URL | Tương thích? |
|---|---|---|---|---|---|
| Compose BOM | `2026.05.01` | Giữ nguyên (`2026.05.01`) | `2026.09.00` (xác minh 2026-10-13) | [developer.android.com/jetpack/compose/bom/bom-mapping](https://developer.android.com/jetpack/compose/bom/bom-mapping) | Tương thích tốt |
| Android CookieManager | SDK Native (API 36) | SDK Native | API 36 Native (xác minh 2026-10-13) | [developer.android.com/reference/android/webkit/CookieManager](https://developer.android.com/reference/android/webkit/CookieManager) | Native chuẩn Android |
| OkHttp | `5.3.2` | Giữ nguyên (`5.3.2`) | `5.3.2` (xác minh 2026-10-13) | [square.github.io/okhttp](https://square.github.io/okhttp/) | Hỗ trợ CookieJar |
| Jsoup | `1.22.2` | Giữ nguyên (`1.22.2`) | `1.23.2` (xác minh 2026-10-13) | [jsoup.org](https://jsoup.org/) | Tương thích tốt |
| Room | `2.8.4` | Giữ nguyên (`2.8.4`) | `2.8.4` (xác minh 2026-10-13) | [developer.android.com/jetpack/androidx/releases/room](https://developer.android.com/jetpack/androidx/releases/room) | Không đổi schema |
| Hilt | `2.60.1` | Giữ nguyên (`2.60.1`) | `2.60.1` (xác minh 2026-10-13) | [dagger.dev/hilt](https://dagger.dev/hilt/) | Tương thích tốt |
| Navigation Compose | `2.9.8` | Giữ nguyên (`2.9.8`) | `2.9.8` (xác minh 2026-10-13) | [developer.android.com/jetpack/androidx/releases/navigation](https://developer.android.com/jetpack/androidx/releases/navigation) | Hỗ trợ nav arguments |
| Kotlin / Coroutines | `2.3.10` / `1.11.0` | Giữ nguyên | `2.3.10` / `1.11.0` (xác minh 2026-10-13) | [kotlinlang.org](https://kotlinlang.org/) | Tương thích tốt |

> *Ghi chú*: Task này không thêm mới hay nâng cấp dependency bên ngoài trong `app/build.gradle.kts`.

---

### 5. Files Cần Chỉnh Sửa / Tạo Mới

| Nhãn | File | Tầng | Ghi chú (Lý do bảo vệ / nhạy cảm) |
|---|---|---|---|
| `[NEW]` | `app/src/main/java/com/personal/apptruyen/data/model/SourceAuth.kt` | `data/model` | **[m-l]** Chứa `sealed interface SessionVerifyResult` (`Success`, `InvalidCookie`, `NetworkError`) và `SourceAuthConfig` tại tầng domain/model |
| `[NEW]` | `app/src/main/java/com/personal/apptruyen/data/remote/auth/CookieStore.kt` | `data/remote/auth` | Interface trừu tượng hóa CookieManager phục vụ testable clean architecture |
| `[NEW]` | `app/src/main/java/com/personal/apptruyen/data/remote/auth/AndroidCookieStore.kt` | `data/remote/auth` | Implementation mặc định bọc `android.webkit.CookieManager` |
| `[NEW]` | `app/src/main/java/com/personal/apptruyen/data/remote/auth/CookieStringParser.kt` | `data/remote/auth` | Helper parse chuỗi cookie dán từ clipboard / dialog |
| `[NEW]` | `app/src/main/java/com/personal/apptruyen/data/remote/auth/SourceAuthManager.kt` | `data/remote/auth` | **[X2]** Quản lý phiên và cookie ở tầng Remote; sử dụng client probe cô lập `CookieJar.NO_COOKIES` trước khi ghi CookieStore |
| `[NEW]` | `app/src/main/java/com/personal/apptruyen/data/repository/ISourceAuthRepository.kt` | `data/repository` | **[BLOCKER B1, N3]** Interface Repository cung cấp trạng thái auth và trả về `SessionVerifyResult` |
| `[NEW]` | `app/src/main/java/com/personal/apptruyen/data/repository/SourceAuthRepository.kt` | `data/repository` | **[BLOCKER B1, N3]** Implementation Repository điều phối giữa ViewModel và SourceAuthManager |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/data/remote/ScraperException.kt` | `data/remote` | Thêm subclass `LoginRequiredException(val sourceId: String, ...)` |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/data/remote/cookie/WebViewCookieHandler.kt` | `data/remote/cookie` | **[M6]** Xóa log raw cookie (line 70), xóa `getCookieString()` và code LacaTruyen cũ (nhạy cảm: CookieJar) |
| `[NEW]` | `app/src/main/java/com/personal/apptruyen/data/remote/WikidichScraper.kt` | `data/remote` | **[M2, M2', N1, N2, X4, X5, m-h, m-m]** Scraper nguồn Wikidich (`wikicv.org`), phân trang AJAX điều kiện dừng hết trang, selector `.book-item`, bảng thể loại và lọc watermark |
| `[DELETE]` | `app/src/main/java/com/personal/apptruyen/data/remote/TruyenComScraper.kt` | `data/remote` | Xóa scraper TruyenCom theo yêu cầu |
| `[DELETE]` | `app/src/main/java/com/personal/apptruyen/data/remote/TruyenFullScraper.kt` | `data/remote` | Xóa scraper TruyenFull theo yêu cầu |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/data/remote/WebViewContentLoader.kt` | `data/remote` | Cập nhật `ALLOWED_DOMAINS`: bỏ TruyenCom/TruyenFull, thêm `wikicv.org`, `wikicv.net` |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/di/ScraperModule.kt` | `di` | Xóa binding TruyenCom & TruyenFull; thêm binding `WikidichScraper` |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/di/AppModule.kt` | `di` | Provide `CookieStore` và `SourceAuthManager` |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/di/RepositoryModule.kt` | `di` | **[B1]** Bind `ISourceAuthRepository` → `SourceAuthRepository` |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/data/repository/StoryRepository.kt` | `data/repository` | **[M4, M10, M2', N5, X3, m-a]** Rethrow exception ở catch cấp trang; gọi `dataStore.migrateWikidichEnabledSourcesIfNeeded()` trong `activeSources()`; sửa gộp genre union |
| `[NEW]` | `app/src/main/java/com/personal/apptruyen/ui/login/SourceLoginViewModel.kt` | `ui/login` | **[B1, N3, m-l]** Inject `ISourceAuthRepository`; xử lý `SessionVerifyResult` phân biệt cookie hỏng vs mất mạng |
| `[NEW]` | `app/src/main/java/com/personal/apptruyen/ui/login/SourceLoginScreen.kt` | `ui/login` | **[M6, M8, m-c, m-f, m-g]** WebView lifecycle an toàn (`onRelease`, `saveState`/`restoreState` giữ URL khi xoay), chặn domain lạ trong `shouldOverrideUrlLoading` |
| `[NEW]` | `app/src/main/java/com/personal/apptruyen/ui/login/PasteCookieDialog.kt` | `ui/login` | Dialog dán chuỗi cookie session từ PC |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/ui/navigation/AppNavigation.kt` | `ui/navigation` | Thêm route `source_login/{sourceId}` vào NavHost |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/ui/settings/SettingsViewModel.kt` | `ui/settings` | **[B1, M7, M7', N5, X3]** Inject `ISourceAuthRepository` và `DataStore<Preferences>`; gọi `migrateWikidichEnabledSourcesIfNeeded()` trong `init`; tham chiếu `SourceIds` |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/ui/settings/SettingsScreen.kt` | `ui/settings` | Thêm section "Tài khoản nguồn truyện"; cập nhật text đa nguồn ở header |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/ui/search/SearchViewModel.kt` | `ui/search` | **[B1]** Inject `ISourceAuthRepository`; theo dõi `loginRequiredSources` |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/ui/search/SearchScreen.kt` | `ui/search` | Hiển thị banner tiếng Việt khi nguồn yêu cầu đăng nhập, nút "Đăng nhập ngay" |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/ui/explore/ExploreViewModel.kt` | `ui/explore` | **[M4, M5']** Inject `ISourceAuthRepository`; lắng nghe `loginRequiredSources` và cập nhật state thông báo |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/ui/explore/ExploreScreen.kt` | `ui/explore` | **[M4]** Hiển thị banner cảnh báo nhẹ khi nguồn duyệt yêu cầu login |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/ui/reader/ReaderViewModel.kt` | `ui/reader` | **[m-e]** Đặt `tryConstructChapterUrl` thành `internal`, trả về `null` cho link dạng hash như Wikidich |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/ui/components/StoryCardPremium.kt` | `ui/components` | Bổ sung badge "WKD", "SST"; giữ nguyên badge "TC", "TF" cho truyện đã tải |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/data/local/SettingsDataStore.kt` | `data/local` | **[M7', N5, X3, m-n]** Thêm `SettingsKeys.IS_WIKIDICH_MIGRATED`, `object SourceIds`, extension `DataStore<Preferences>.migrateWikidichEnabledSourcesIfNeeded()` |
| `[MODIFY]` | `app/src/main/AndroidManifest.xml` | `manifest` | **[M6', N4]** Thêm `dataExtractionRules` và `fullBackupContent` trỏ tới cấu hình bảo vệ cookie |
| `[NEW]` | `app/src/main/res/xml/data_extraction_rules.xml` | `res/xml` | **[M6', N4]** Loại trừ `app_webview/` (`domain="root"`) khỏi Google Drive Cloud Backup (API 31+) |
| `[NEW]` | `app/src/main/res/xml/backup_rules.xml` | `res/xml` | **[M6', N4]** Loại trừ `app_webview/` (`domain="root"`) khỏi Full Backup (API <= 30) |
| `[NEW]` | `app/src/test/java/com/personal/apptruyen/data/remote/auth/CookieStringParserTest.kt` | `test` | Unit test parse các định dạng chuỗi cookie |
| `[NEW]` | `app/src/test/java/com/personal/apptruyen/data/remote/auth/SourceAuthManagerTest.kt` | `test` | **[M5, M1', N3, X2]** Unit test kiểm tra probe cô lập MockWebServer, 3 nhánh `SessionVerifyResult` và bảo toàn cookie cũ |
| `[NEW]` | `app/src/test/java/com/personal/apptruyen/data/repository/SourceAuthRepositoryTest.kt` | `test` | **[M5, N3]** Unit test cho Repository xác thực nguồn |
| `[NEW]` | `app/src/test/java/com/personal/apptruyen/data/remote/WikidichScraperTest.kt` | `test` | **[M2, M5, M2', N1, N2, X4, X5]** Unit test phân trang điều kiện dừng, bảng thể loại, selector `.book-item` |
| `[DELETE]` | `app/src/test/java/com/personal/apptruyen/data/remote/TruyenComScraperTest.kt` | `test` | Xóa test file của TruyenCom theo nguồn bị xóa |
| `[MODIFY]` | `app/src/test/java/com/personal/apptruyen/data/repository/StoryRepositoryTest.kt` | `test` | **[M5, M2', N5, X3, m-a]** Cập nhật test case xử lý `LoginRequiredException`, clearLoginRequired khi thành công, migration DataStore và đọc offline |
| `[MODIFY]` | `app/src/test/java/com/personal/apptruyen/ui/search/SearchViewModelTest.kt` | `test` | **[M5]** Cập nhật constructor và test state `loginRequiredSources` |
| `[MODIFY]` | `app/src/test/java/com/personal/apptruyen/ui/explore/ExploreViewModelTest.kt` | `test` | **[M5']** Cập nhật 5 lời gọi constructor (dòng 47, 63, 78, 94, 106) truyền mock `ISourceAuthRepository`, test banner loginRequired |
| `[MODIFY]` | `app/src/test/java/com/personal/apptruyen/ui/settings/SettingsViewModelTest.kt` | `test` | **[M5, M7, M7', N5, X3]** Cập nhật constructor và test migration `IS_WIKIDICH_MIGRATED` nguyên tử trên `DataStore<Preferences>` |
| `[NEW]` | `app/src/test/java/com/personal/apptruyen/ui/login/SourceLoginViewModelTest.kt` | `test` | **[M5, N3]** Unit test cho SourceLoginViewModel với 3 nhánh `SessionVerifyResult` |
| `[NEW]` | `app/src/test/java/com/personal/apptruyen/ui/reader/ReaderViewModelTest.kt` | `test` | **[m-e, m-k]** File test mới kiểm tra hàm `tryConstructChapterUrl` với các mẫu URL tuần tự và link hash |
| `[NEW]` | `app/src/test/resources/wikidich/story_detail.html` | `test/resources` | Fixture chi tiết truyện Wikidich |
| `[NEW]` | `app/src/test/resources/wikidich/chapter_content.html` | `test/resources` | Fixture nội dung chương Wikidich |
| `[NEW]` | `app/src/test/resources/wikidich/search_logged_out.html` | `test/resources` | Fixture kết quả tìm kiếm khi chưa đăng nhập |
| `[NEW]` | `app/src/test/resources/wikidich/chapter_index_ajax.html` | `test/resources` | **[N1]** Fixture fragment HTML trang 1 danh sách chương AJAX `/book/index` (trích từ `build/real_book_index.html` khảo sát 2026-10-13) |
| `[NEW]` | `app/src/test/resources/wikidich/chapter_index_ajax_p2.html` | `test/resources` | **[N1]** Fixture fragment HTML trang 2 danh sách chương AJAX `/book/index` (trích từ `build/real_book_index_p2.html` khảo sát 2026-10-13) |
| `[NEW]` | `app/src/test/resources/wikidich/search_logged_in.html` | `test/resources` | **[m-j]** Fixture kết quả tìm kiếm khi đã đăng nhập chứa các khối `.book-item` (dẫn xuất từ `wikicv_home.html`) |
| `[MODIFY]` | `docs/scraping.md` | `docs` | Thêm tài liệu cào Wikidich; đánh dấu TruyenCom & TruyenFull đã ngừng hoạt động |
| `[MODIFY]` | `docs/architecture.md` | `docs` | Cập nhật cấu trúc Repository/Remote Layer và danh sách nguồn truyện |
| `[MODIFY]` | `docs/progress.md` | `docs` | Ghi nhận tính năng hoàn thành và thay đổi nguồn |
| `[MODIFY]` | `docs/changelog.md` | `docs` | Ghi log thay đổi v2.9.0 trong `[Unreleased]` |

---

### 6. Các Bước Thực Hiện

- [ ] **Bước 1: Tạo `LoginRequiredException`, `SourceAuth.kt` (`SessionVerifyResult`, `SourceAuthConfig`), `CookieStringParser` & Unit Test [N3, m-l]**
  - Thêm subclass `LoginRequiredException(val sourceId: String, message: String = "Nguồn yêu cầu đăng nhập tài khoản để xem nội dung", cause: Throwable? = null)` vào `app/src/main/java/com/personal/apptruyen/data/remote/ScraperException.kt`.
  - Tạo `app/src/main/java/com/personal/apptruyen/data/model/SourceAuth.kt` tại tầng `data/model` [m-l]:
    ```kotlin
    sealed interface SessionVerifyResult {
        data object Success : SessionVerifyResult
        data class InvalidCookie(val message: String = "Chuỗi cookie không hợp lệ, thiếu token hoặc phiên không hoạt động") : SessionVerifyResult
        data class NetworkError(val message: String = "Không thể kết nối đến máy chủ để xác minh phiên, vui lòng thử lại") : SessionVerifyResult
    }

    data class SourceAuthConfig(
        val sourceId: String,
        val displayName: String,
        val baseUrl: String,
        val loginUrl: String,
        val loginReferer: String,
        val domains: List<String>,
        val sessionCookieNames: List<String>,
        val primaryCookieName: String,
        val loggedOutMarker: String
    )
    ```
  - Tạo `app/src/main/java/com/personal/apptruyen/data/remote/auth/CookieStringParser.kt`: hàm `parse(rawInput: String, defaultCookieName: String = "express.sid"): List<Pair<String, String>>`. Hỗ trợ:
    1. Chuỗi header tiêu chuẩn: `name1=val1; name2=val2`.
    2. Chuỗi có tiền tố: `Cookie: name1=val1; ...`.
    3. Chuỗi chỉ chứa token thô (không có dấu `=`): gán với tên `defaultCookieName`.
  - Tạo test `app/src/test/java/com/personal/apptruyen/data/remote/auth/CookieStringParserTest.kt` kiểm tra các trường hợp chuẩn và biên (chuỗi rỗng, nhiều dấu chấm phẩy, token URL-encoded).

- [ ] **Bước 2: Xây dựng `CookieStore`, `AndroidCookieStore` và `SourceAuthManager` với Client Probe Cô Lập [M1, M3, M1', N3, X2, m1, m4, m7]**
  - Tạo interface `CookieStore` trong `data/remote/auth/CookieStore.kt` (`getCookie(url: String): String?`, `setCookie(url: String, value: String)`, `flush()`, `removeCookies(url: String, names: List<String>)`).
  - Tạo `AndroidCookieStore` implement `CookieStore` bọc `android.webkit.CookieManager`.
  - Cấu hình Wikidich trong `SourceAuthManager`:
    - `sourceId = "wikidich"`, `displayName = "Wikidich"`, `baseUrl = "https://wikicv.org"`
    - `loginUrl = "https://wikicv.org/auth/dth"`, `loginReferer = "https://wikicv.org/"`
    - `domains = listOf("https://wikicv.org", "https://wikicv.net")` (chuẩn hóa thành URL đầy đủ [m4], LOẠI BỎ `forum.dichtienghoa.com` [M1]).
    - `sessionCookieNames = listOf("express.sid")`, `primaryCookieName = "express.sid"`
    - `loggedOutMarker = "Đăng nhập để xem nội dung"`
  - Tạo `SourceAuthManager(cookieStore: CookieStore, client: OkHttpClient)` [m7: lazy init]:
    - `getAuthState(sourceId)`: StateFlow<Boolean>.
    - `loginRequiredSources`: StateFlow<Set<String>> quản lý qua `MutableStateFlow.update { }` [m1].
    - `isLoggedIn(sourceId)`: kiểm tra cookie `express.sid` trên `https://wikicv.org` có giá trị hợp lệ (> 10 ký tự, không rỗng).
    - Client probe cô lập [X2, m-r(d)]:
      ```kotlin
      private val probeClient: OkHttpClient = client.newBuilder()
          .cookieJar(CookieJar.NO_COOKIES)
          .build()
      ```
    - `verifySessionOnline(sourceId): Boolean` [M1']: CHỈ gọi duy nhất `https://wikicv.org/tim-kiem?qs=1&q=tien`.
      - Dùng client thông thường (có CookieJar) để gửi cookie phiên đã lưu trong CookieStore.
      - Phản hồi 200 OK và không chứa `loggedOutMarker` -> trả về `true`.
      - Phản hồi chứa `loggedOutMarker` -> trả về `false` (phiên thực sự hết hạn hoặc chưa đăng nhập).
      - Ngoại lệ kết nối (`IOException`, Timeout, HTTP 5xx) -> trả về `false`, TUYỆT ĐỐI KHÔNG hạ `_authState` / không đánh dấu hết hạn phiên / không logout người dùng vì lỗi mạng.
    - `importCookies(sourceId, rawInput): SessionVerifyResult` [N3, X2, Y1, m-r(d)]:
      - Parse rawInput lấy token `express.sid`. Nếu không có -> trả về `SessionVerifyResult.InvalidCookie("Chuỗi cookie không hợp lệ hoặc thiếu token express.sid")`.
      - Probe cô lập an toàn [X2, m-r(d)]: Gửi request GET tới `https://wikicv.org/tim-kiem?qs=1&q=tien` kèm header tạm `Cookie: express.sid=${token}` bằng `probeClient` (`CookieJar.NO_COOKIES`). Thực thi trong `probeClient.newCall(request).execute().use { response -> ... }` để đảm bảo giải phóng tài nguyên mạng [m-r(d)].
        - Header `Cookie` tạm không bị `BridgeInterceptor` ghi đè bằng cookie cũ, và phản hồi `Set-Cookie` của server không bị ghi vào `CookieManager` [X2].
        - Nếu 200 OK và HTML không chứa `loggedOutMarker`: Hợp lệ! [MAJOR Y1]:
          1. Xóa sạch mọi cookie `express.sid` cũ trong `cookieStore` (gọi `cookieStore.removeCookies("https://wikicv.org", listOf("express.sid"))` và xóa cả trên domain `.wikicv.org` bằng cách set `Max-Age=0`) để loại bỏ hoàn toàn cookie GUEST host-only cũ do server từng cấp khi duyệt web trước đó [Y1].
          2. Ghi MỘT cookie duy nhất host-only cho `https://wikicv.org`: `cookieStore.setCookie("https://wikicv.org", "express.sid=$token; Path=/; Secure; Max-Age=2592000")` (KHÔNG chỉ định thuộc tính `Domain` để tránh tạo thêm bản domain cookie gây xung đột gửi 2 cookie lên server) [Y1].
          3. Gọi `cookieStore.flush()`, cập nhật `_authState.value = true`, gọi `clearLoginRequired(sourceId)`, trả về `SessionVerifyResult.Success`.
        - Nếu HTML chứa `loggedOutMarker`: Hết hạn/sai token! KHÔNG ghi vào `cookieStore` (bảo toàn phiên cũ), trả về `SessionVerifyResult.InvalidCookie()`.
        - Nếu gặp lỗi kết nối/timeout (`IOException`): KHÔNG ghi vào `cookieStore`, giữ nguyên `authState`, trả về `SessionVerifyResult.NetworkError()`.
    - `logout(sourceId)`: gọi `cookieStore.removeCookies` gán `Max-Age=0` cho cả `wikicv.org` và domain gốc `.wikicv.org`, gọi `flush()`, cập nhật `_authState.value = false` [M3].
    - `markLoginRequired(sourceId)`: hạ `_authState.value = false` và cập nhật `_loginRequiredSources.update { it + sourceId }` [M3, m1].
    - `clearLoginRequired(sourceId)`: cập nhật `_loginRequiredSources.update { it - sourceId }` [M3, m1, m-a].
  - Viết `SourceAuthManagerTest.kt` kiểm chứng:
    - Mock `CookieStore` và MockWebServer: CookieStore đang có `express.sid=OLD`, probe dán token rác nhận Set-Cookie `GUEST` + marker -> kết quả `InvalidCookie` và CookieStore vẫn giữ nguyên `OLD` [X2].
    - Kiểm tra header gửi đi của probe: assert `server.takeRequest().headers["Cookie"] == "express.sid=<pasted>"` [m-o].
    - Kiểm tra nhánh thành công: assert xóa sạch cookie cũ trước khi ghi cookie mới host-only, xác nhận sau import chỉ còn duy nhất 1 cookie host-only [Y1].
    - Kiểm tra đủ 3 nhánh của `SessionVerifyResult`, xác nhận không phá phiên cũ khi dán cookie lỗi hoặc mất mạng [N3].

- [ ] **Bước 3: Xây dựng `ISourceAuthRepository` và `SourceAuthRepository` [BLOCKER B1, N3]**
  - Tạo `data/repository/ISourceAuthRepository.kt`:
    - `fun getAuthState(sourceId: String): StateFlow<Boolean>`
    - `val loginRequiredSources: StateFlow<Set<String>>`
    - `fun isLoginRequired(sourceId: String): Boolean`
    - `fun markLoginRequired(sourceId: String)`
    - `fun clearLoginRequired(sourceId: String)`
    - `suspend fun importCookies(sourceId: String, rawInput: String): SessionVerifyResult` [N3]
    - `suspend fun onLoginDetected(sourceId: String)`
    - `suspend fun logout(sourceId: String)`
    - `fun getSourceConfig(sourceId: String): SourceAuthConfig?`
  - Tạo `data/repository/SourceAuthRepository.kt` inject `SourceAuthManager`, ủy quyền các thao tác và chuyển dispatcher `Dispatchers.IO` khi cần.
  - Cập nhật `di/RepositoryModule.kt`: thêm `@Binds abstract fun bindSourceAuthRepository(impl: SourceAuthRepository): ISourceAuthRepository`.
  - Viết unit test `SourceAuthRepositoryTest.kt`.

- [ ] **Bước 4: Tái cấu trúc `WebViewCookieHandler` & Loại bỏ Log Rò Rỉ Cookie [M6]**
  - Trong `app/src/main/java/com/personal/apptruyen/data/remote/cookie/WebViewCookieHandler.kt`:
    - XÓA DÒNG LOG RÒ RỈ: `android.util.Log.d("WebViewCookieHandler", "Cookies for $d: $cookieString")` (line 70) [M6].
    - XÓA hàm `getCookieString()` và toàn bộ logic hardcode LacaTruyen cũ.
    - Giữ class như một `CookieJar` chuẩn đồng bộ giữa OkHttp và `CookieManager`.
  - Cập nhật `di/AppModule.kt`: cung cấp `CookieStore` và `WebViewCookieHandler`.

- [ ] **Bước 5: Triển khai `WikidichScraper`, AJAX Chapter Loading & Unit Tests [M2, M2', m2, m5, N1, N2, X4, X5, m-h, m-j, m-m]**
  - Tạo fixtures trong `app/src/test/resources/wikidich/`:
    - `story_detail.html`: từ `build/wikicv_story.html`.
    - `chapter_content.html`: từ `build/wikicv_chap.html`.
    - `search_logged_out.html`: từ `build/wk_search.html`.
    - `chapter_index_ajax.html`: fixture fragment HTML trang 1 (`start=0, size=99`) trả về từ endpoint `/book/index` (từ `build/real_book_index.html`, chứa `li.chapter-name a[href^="/truyen/"]` và `ul.pagination li a[data-action="loadBookIndex"]`) [N1].
    - `chapter_index_ajax_p2.html`: fixture fragment HTML trang 2 (`start=99, size=99`) trả về từ endpoint `/book/index` (từ `build/real_book_index_p2.html`) [N1].
    - `search_logged_in.html`: fixture HTML trang tìm kiếm khi đã đăng nhập chứa danh sách truyện dạng `.book-item` (dẫn xuất từ `build/wikicv_home.html:458-481`) [N2, m-j].
  - Tạo `app/src/main/java/com/personal/apptruyen/data/remote/WikidichScraper.kt` kế thừa `BaseScraper(client)`:
    - `sourceId = "wikidich"`, `sourceName = "Wikidich"`, `baseUrl = "https://wikicv.org"`.
    - Hỗ trợ nhận diện URL cho cả `wikicv.org` và `wikicv.net` [m5].
    - Khi tạo đối tượng `Story` (trong detail hoặc search/genre), gán tường minh `sourceId = "wikidich"` [m-m].
    - Thuật toán chữ ký AJAX và giải mã xoay chuỗi [M2, M2', m-h]:
      ```kotlin
      fun extractFuzzyCut(html: String): Int {
          val regex = """text\.substring\((\d+)\)""".toRegex()
          val match = regex.find(html) ?: throw ScraperException.ParseException(
              "Không tìm thấy tham số cắt chuỗi fuzzySign trong trang truyện Wikidich"
          )
          return match.groupValues[1].toInt()
      }

      fun fuzzySign(text: String, cut: Int): String {
          if (cut !in 0..text.length) {
              throw ScraperException.ParseException("Vị trí cắt $cut vượt quá độ dài chuỗi ${text.length}")
          }
          return text.substring(cut) + text.substring(0, cut)
      }

      fun sha256Hex(input: String): String {
          val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
          return bytes.joinToString("") { "%02x".format(it) }
      }

      fun makeBookIndexSign(signKey: String, start: Int, size: Int, cut: Int): String {
          val raw = signKey + start + size
          return sha256Hex(fuzzySign(raw, cut))
      }
      ```
    - `getFirstPageChapters` / `getRemainingChapters` / `getChapterList` & Điều kiện dừng phân trang [N1, X4]:
      - Fetch trang chi tiết truyện để lấy `bookId`, `signKey` và tham số `cut` qua Regex từ inline scripts.
      - Với kích thước trang chuẩn `size = 99`: gọi GET `${baseUrl}/book/index?bookId=${bookId}&start=${start}&size=99&signKey=${signKey}&sign=${sign}`.
      - Phân tích fragment HTML bằng Jsoup:
        - Danh sách chương: trích xuất các thẻ `li.chapter-name a[href^="/truyen/"]` lấy tiêu đề chương và URL đầy đủ (kèm hash).
        - **Trang 1 (`start = 0, size = 99`)**: Nếu phản hồi rỗng hoặc không có bất kỳ thẻ link chương nào hợp lệ (`li.chapter-name a`), ném `ScraperException.ParseException("Không trích xuất được danh sách chương từ máy chủ Wikidich")` [N1, X4].
        - **Các trang tiếp theo**: Kiểm tra xem fragment hiện tại có thẻ liên kết trang kế tiếp `ul.pagination li a[data-action="loadBookIndex"][data-start="${currentStart + size}"]` hay không [X4]:
          - NẾU CÓ: Tiếp tục tải trang kế với `start = currentStart + size`.
          - NẾU KHÔNG CÓ: **ĐÃ HẾT TRANG**! Dừng vòng lặp phân trang bình thường và trả về toàn bộ danh sách chương đã thu thập. TUYỆT ĐỐI KHÔNG ném ParseException khi hết trang [X4].
          - Giới hạn an toàn `maxPages = 200` (~20.000 chương) để ngăn lặp vô tận.
    - `parseChapterContentFromDoc`:
      - Trích xuất paragraphs trong `#bookContentBody`.
      - Xóa các selectors rác: `script, .ads, .ad, iframe, noscript, .text-center, .add-name-tip`.
      - Lọc bỏ watermark cuối chương bằng regex: `【❁.*\(Wikidich\).*❁】` [m2].
    - `search` & Selectors danh sách truyện [N2]:
      - Truy vấn `https://wikicv.org/tim-kiem?qs=1&q={keyword}`.
      - Khối truyện chuẩn: `.book-item` (khảo sát thực tế `wikicv_home.html:458-481`):
        - Tiêu đề & URL truyện: `.info-col a[href^="/truyen/"]`, `h5.book-title`.
        - Tác giả: `.book-author a`.
        - Ảnh bìa: `.cover-col img` (lấy `src` hoặc `data-src`).
        - Trạng thái / Thể loại: `.book-publisher a`, `.book-gender`.
      - Khi HTML chứa marker `Đăng nhập để xem nội dung` và không có bất kỳ `.book-item` nào → ném `ScraperException.LoginRequiredException("wikidich")`.
    - `getStoriesByGenre` & `getGenreList` [M2', N2, X5]:
      - Bảng ánh xạ slug thể loại sang query string chuẩn trích từ khối `div.tag-tab` của `wikicv_home.html` [X5]:
        - `"tien-hiep"` -> `"qs=2&tag=5794f03dd7ced228f44191ad&genre=5794f03dd7ced228f44191a4"`
        - `"huyen-huyen"` -> `"qs=2&genre=57d17c94d7ced218fcd2734b"`
        - `"vo-hiep"` -> `"qs=2&genre=5794f03dd7ced228f44191a3"`
        - `"xuyen-khong"`, `"xuyen-viet"` -> `"qs=1&tag=5794f03dd7ced228f44191af"`
        - `"trong-sinh"` -> `"qs=2&tag=5794f03dd7ced228f44191b4"`
        - `"ngon-tinh"` -> `"gender=5794f03dd7ced228f4419195&qs=1"`
        - `"do-thi"`, `"hien-dai"` -> `"qs=2&age=5794f03dd7ced228f441919d"`
        - `"vong-du"` -> `"qs=2&genre=5794f03dd7ced228f44191a5"`
        - `"mat-the"` -> `"qs=2&tag=5794f03dd7ced228f44191ac"`
        - `"khoa-huyen"` -> `"qs=2&age=5794f03dd7ced228f441919e&tag=5794f03dd7ced228f44191c6"`
      - Ghép `completedOnly`: nếu `completedOnly == true`, nối thêm `&status=5794f03dd7ced228f4419191`.
      - URL đầy đủ: `https://wikicv.org/tim-kiem?{queryString}{completedQuery}&p={page}`.
      - Nếu slug lạ không nằm trong bảng hỗ trợ của Wikidich: trả về `emptyList()` ngay mà không gọi mạng [X5].
    - `getCompletedStories` [M2', N2]:
      - Truy vấn URL hoàn thành thực tế: `https://wikicv.org/tim-kiem?status=5794f03dd7ced228f4419191&qs=1` (ID `5794f03dd7ced228f4419191` là nhãn "Hoàn thành" khảo sát từ `wikicv_home.html:502`).
  - Viết `WikidichScraperTest.kt` kiểm tra đủ các khía cạnh theo `04-scraping.md`:
    1. Search happy path với fixture `search_logged_in.html` (parse khối `.book-item`, assert `sourceId == "wikidich"` [m-m]).
    2. Chi tiết truyện với `story_detail.html` (assert `sourceId == "wikidich"` [m-m]).
    3. AJAX chapter list: trích xuất cut, tạo chữ ký SHA-256, parse fragment `chapter_index_ajax.html` và phân trang `chapter_index_ajax_p2.html`.
    4. Phân trang trang cuối (không có thẻ trang kế): dừng bình thường, không ném ngoại lệ [X4].
    5. Chapter content làm sạch ads và lọc watermark `【❁.*❁】`.
    6. Search khi chưa đăng nhập với `search_logged_out.html` ném `LoginRequiredException`.
    7. Thể loại lạ: trả về `emptyList()` an toàn, không gọi mạng [X5].

- [ ] **Bước 6: Xây dựng UI Đăng nhập nguồn (`SourceLoginViewModel`, `SourceLoginScreen`, `PasteCookieDialog`) & Navigation [B1, M6, M8, M1', m-c, m-f, m-g, m6, N3, m-l]**
  - Tạo `app/src/main/java/com/personal/apptruyen/ui/login/SourceLoginViewModel.kt`:
    - Inject `SavedStateHandle` và `ISourceAuthRepository` (tuân thủ Clean Architecture [B1]).
    - Import `SessionVerifyResult` từ `com.personal.apptruyen.data.model.SessionVerifyResult` [m-l].
    - Expose `isLoggedIn: StateFlow<Boolean>`, `showPasteDialog: StateFlow<Boolean>`, `errorMessage: StateFlow<String?>`, `isVerifying: StateFlow<Boolean>`.
    - `onPageFinished(url: String)`: khi URL quay về `wikicv.org` sau đăng nhập, gọi `authRepository.onLoginDetected(sourceId)`.
    - `importCookieString(rawCookie: String)`: gọi `authRepository.importCookies(sourceId, rawCookie)` và bắt các nhánh của `SessionVerifyResult` [N3]:
      - `SessionVerifyResult.Success`: đóng dialog, cập nhật UI thông báo đăng nhập thành công.
      - `SessionVerifyResult.InvalidCookie`: báo lỗi tiếng Việt: `"Chuỗi cookie không hợp lệ, thiếu token hoặc phiên không hoạt động"`.
      - `SessionVerifyResult.NetworkError`: báo lỗi tiếng Việt: `"Không thể kết nối đến máy chủ để xác minh phiên, vui lòng thử lại"`.
    - `logout()`: gọi `authRepository.logout(sourceId)`.
  - Tạo `PasteCookieDialog.kt`: Material 3 dialog dán cookie với hướng dẫn tiếng Việt rõ ràng.
  - Tạo `SourceLoginScreen.kt`:
    - Xử lý guard: nếu `sourceId` không hợp lệ/không có config, hiện thông báo `"Nguồn truyện không hỗ trợ đăng nhập"` kèm nút Back [m6].
    - Quản lý vòng đời & trạng thái WebView [M8, m-c, m-g]:
      - Dùng `rememberSaveable` lưu `Bundle` trạng thái WebView:
        ```kotlin
        val webViewBundle = rememberSaveable { Bundle() }
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    if (!webViewBundle.isEmpty) {
                        restoreState(webViewBundle)
                    } else {
                        loadUrl(config.loginUrl)
                    }
                }
            },
            update = { webView ->
                webView.saveState(webViewBundle)
            },
            onRelease = { webView ->
                webView.saveState(webViewBundle)
                webView.stopLoading()
                webView.destroy()
            }
        )
        ```
      - Lưu ý [m-g]: `saveState`/`restoreState` bảo toàn URL hiện tại và lịch sử duyệt trang khi xoay màn hình (không bị reload lại trang chủ hoặc mất luồng redirect), không kỳ vọng khôi phục text đang gõ dở trên web form do giới hạn của WebView Android engine.
      - KHÔNG gọi `setAcceptThirdPartyCookies(webView, true)` không cần thiết [m-f].
    - Bảo mật điều hướng [M6]:
      - Override `shouldOverrideUrlLoading`: chỉ cho phép các URL có host thuộc `wikicv.org`, `wikicv.net`, `forum.dichtienghoa.com`. Nếu là domain khác (quảng cáo, link ngoài) thì mở qua `Intent(Intent.ACTION_VIEW)`.
      - TUYỆT ĐỐI không gọi `addJavascriptInterface`.
  - Viết `SourceLoginViewModelTest.kt`.
  - Đăng ký route `source_login/{sourceId}` trong `AppNavigation.kt`.

- [ ] **Bước 7: Xử lý `LoginRequiredException` trong `StoryRepository`, `SearchViewModel`, `ExploreViewModel` [M4, M10, M2', m-a, m5, N5, X3]**
  - Trong `app/src/main/java/com/personal/apptruyen/data/repository/StoryRepository.kt`:
    - Inject `ISourceAuthRepository` và `DataStore<Preferences>`.
    - Trong `activeSources()`: kiểm tra và gọi `dataStore.migrateWikidichEnabledSourcesIfNeeded()` ở bên ngoài khối `try/catch (_: Exception)` để không nuốt lỗi migration hoặc cancellation [N5, X3, m-r(c)], trước khi lọc các nguồn kích hoạt, đảm bảo người dùng cũ ngay khi mở ứng dụng truy vấn truyện đã có nguồn Wikidich hoạt động mà không bắt buộc phải mở màn hình Cài đặt.
    - Trong `getCompletedStories`: tại catch cấp từng trang (line 124), rethrow nếu `e is CancellationException || e is ScraperException.LoginRequiredException` [M4].
    - Trong `getStoriesByGenre`: tại catch cấp từng trang (line 181), rethrow nếu `e is CancellationException || e is ScraperException.LoginRequiredException` [M4]. Giữ nguyên logic hiện tại của `StoryRepository.kt:191-194` (KHÔNG thêm `circuitBreaker.recordFailure(source.sourceId)` vào catch của `getStoriesByGenre` vì hàm này không dùng circuit breaker) [m-r(b)].
    - Tại catch bao ngoài của từng source:
      ```kotlin
      catch (e: Exception) {
          if (e is CancellationException) throw e // [m-r(a)]
          if (e is ScraperException.LoginRequiredException) {
              authRepository.markLoginRequired(source.sourceId)
              // Không gọi circuitBreaker.recordFailure
          } else {
              circuitBreaker.recordFailure(source.sourceId)
          }
          emptyList()
      }
      ```
    - Khi nguồn truyện trả về kết quả thành công: gọi `authRepository.clearLoginRequired(source.sourceId)` (cùng chỗ với `circuitBreaker.recordSuccess(source.sourceId)`) [m-a].
    - Trong `sourceFor(storyUrl)`:
      - Khớp cả `wikicv.org` và `wikicv.net` [m5].
      - Nếu không tìm thấy nguồn: ném `ScraperException.ParseException("Nguồn truyện này không còn hoạt động hoặc không được hỗ trợ")` [M10].
    - Trong `getChapterContent`: khi bắt lỗi chương chưa tải của nguồn đã gỡ, hiện đúng thông báo `"Nguồn truyện này không còn hoạt động hoặc không được hỗ trợ"` [M10].
    - Trong `getGenreList`: dùng phép hợp (union) danh sách thể loại chuẩn hóa tiếng Việt từ các nguồn hoạt động, loại bỏ hoàn toàn phép giao (`intersect`) để UI không bao giờ bị mất thể loại [M2'].
  - Trong `SearchViewModel.kt`:
    - Inject `ISourceAuthRepository` [B1].
    - Lắng nghe `authRepository.loginRequiredSources`, cập nhật `SearchState.loginRequiredSources`.
  - Trong `SearchScreen.kt`:
    - Hiển thị banner cảnh báo tiếng Việt phía trên kết quả khi `state.loginRequiredSources` chứa `"wikidich"` kèm nút `"Đăng nhập ngay"`.
  - Trong `ExploreViewModel.kt` & `ExploreScreen.kt`:
    - Inject `ISourceAuthRepository` [B1, M5']. Lắng nghe `loginRequiredSources` và cập nhật state thông báo banner [M4].
  - Cập nhật `StoryRepositoryTest.kt`, `SearchViewModelTest.kt` và `ExploreViewModelTest.kt` (cập nhật 5 lời gọi constructor tại dòng 47, 63, 78, 94, 106 truyền mock `ISourceAuthRepository`, bổ sung test banner khi loginRequiredSources chứa `"wikidich"`) [M5'].

- [ ] **Bước 8: Tích hợp Quản lý tài khoản nguồn & Chuẩn hóa DataStore Đúng Ngữ Nghĩa [B1, M7, M7', N5, X3, m-n]**
  - Trong `data/local/SettingsDataStore.kt`:
    - Bổ sung preference key: `val IS_WIKIDICH_MIGRATED = booleanPreferencesKey("is_wikidich_migrated")` trong `SettingsKeys`.
    - Định nghĩa `object SourceIds` tại tầng data/local [X3]:
      ```kotlin
      object SourceIds {
          val ALL_ACTIVE_SOURCES = setOf("tangthuvien", "sstruyen", "wikidich")
          val DEPRECATED_SOURCES = setOf("truyencom", "truyenfull")
      }
      ```
    - Thêm extension function migration nguyên tử trên `DataStore<Preferences>` [X3, m-n]:
      ```kotlin
      suspend fun DataStore<Preferences>.migrateWikidichEnabledSourcesIfNeeded() {
          val isMigrated = data.first()[SettingsKeys.IS_WIKIDICH_MIGRATED] ?: false
          if (isMigrated) return

          edit { preferences ->
              if (preferences[SettingsKeys.IS_WIKIDICH_MIGRATED] == true) return@edit
              val currentSources = preferences[SettingsKeys.ENABLED_SOURCES]
                  ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toMutableSet()
                  ?: SourceIds.ALL_ACTIVE_SOURCES.toMutableSet()
              currentSources.removeAll(SourceIds.DEPRECATED_SOURCES)
              currentSources.add("wikidich")
              preferences[SettingsKeys.ENABLED_SOURCES] = currentSources.joinToString(",")
              preferences[SettingsKeys.IS_WIKIDICH_MIGRATED] = true
          }
      }
      ```
  - Trong `SettingsViewModel.kt`:
    - Inject `ISourceAuthRepository` và `DataStore<Preferences>` [B1, X3].
    - Trong hàm `init`: gọi `dataStore.migrateWikidichEnabledSourcesIfNeeded()` [N5, X3].
    - Tham chiếu hằng số `SourceIds.ALL_ACTIVE_SOURCES` và `SourceIds.DEPRECATED_SOURCES` từ `data/local/SettingsDataStore.kt` (TUYỆT ĐỐI không để `data/local` import ngược `ui/settings`) [X3].
    - Trong `companion object`: giữ alias `val ALL_SOURCES = SourceIds.ALL_ACTIVE_SOURCES` để tương thích ngược với các vị trí đang gọi `SettingsViewModel.ALL_SOURCES` (như trong `SettingsScreen.kt:366, 381`) [m-p].
    - `normalizeEnabledSources`:
      ```kotlin
      fun normalizeEnabledSources(storedSources: Set<String>?): Set<String> {
          if (storedSources.isNullOrEmpty()) return SourceIds.ALL_ACTIVE_SOURCES
          val valid = storedSources.filter { it in SourceIds.ALL_ACTIVE_SOURCES }.toSet()
          return valid.ifEmpty { SourceIds.ALL_ACTIVE_SOURCES }
      }
      ```
    - `SOURCE_LABELS`:
      ```kotlin
      val SOURCE_LABELS = mapOf(
          "tangthuvien" to "TàngThưViện",
          "sstruyen" to "SsTruyện",
          "wikidich" to "Wikidich",
      )
      ```
  - Trong `SettingsScreen.kt`:
    - Thêm section **"Tài khoản nguồn truyện"** hiển thị Wikidich kèm trạng thái "Đã đăng nhập" / "Chưa đăng nhập" và nút "Đăng nhập" / "Đăng xuất".
    - Cập nhật header text: `"Đa nguồn: TàngThưViện • SsTruyện • Wikidich"`.
  - Cập nhật `SettingsDataStore.kt:46` KDoc.
  - Cập nhật `SettingsViewModelTest.kt` kiểm tra migration nguyên tử trên `DataStore<Preferences>` chỉ chạy một lần và không tự bật lại Wikidich nếu người dùng đã chủ động tắt [M7', N5, X3].

- [ ] **Bước 9: Gỡ bỏ TruyenCom, TruyenFull, Cấu hình Backup Rules & Bảo toàn dữ liệu offline [M10, M6', m-e, m-k, N4]**
  - Gỡ bindings `bindTruyenCom` và `bindTruyenFull` khỏi `di/ScraperModule.kt`.
  - Thêm binding `bindWikidich(impl: WikidichScraper): StorySource`.
  - Xóa `TruyenComScraper.kt`, `TruyenFullScraper.kt` và `TruyenComScraperTest.kt`.
  - Cập nhật `WebViewContentLoader.kt`: `ALLOWED_DOMAINS = listOf("tangthuvien.vn", "tangthuvien.org", "sstruyen.net", "wikicv.org", "wikicv.net")`.
  - Cập nhật `StoryCardPremium.kt`: ánh xạ `"wikidich" -> "WKD"`, `"sstruyen" -> "SST"`, `"tangthuvien" -> "TTV"`, và giữ `"truyencom" -> "TC"`, `"truyenfull" -> "TF"`.
  - Cập nhật `ReaderViewModel.kt`: trong `tryConstructChapterUrl`, đổi visibility thành `internal`, trả về `null` nếu URL không khớp pattern số tuần tự đơn giản (bảo vệ link hash Wikidich) [m-e]. Tạo mới file unit test `ReaderViewModelTest.kt` [m-k].
  - Cấu hình loại trừ cookie WebView khỏi Auto Backup [M6', N4]:
    - Tạo `app/src/main/res/xml/data_extraction_rules.xml` (API 31+):
      ```xml
      <?xml version="1.0" encoding="utf-8"?>
      <data-extraction-rules>
          <cloud-backup>
              <exclude domain="root" path="app_webview/" />
          </cloud-backup>
          <device-transfer>
              <exclude domain="root" path="app_webview/" />
          </device-transfer>
      </data-extraction-rules>
      ```
    - Tạo `app/src/main/res/xml/backup_rules.xml` (API < 31):
      ```xml
      <?xml version="1.0" encoding="utf-8"?>
      <full-backup-content>
          <exclude domain="root" path="app_webview/" />
      </full-backup-content>
      ```
    - Cập nhật `app/src/main/AndroidManifest.xml`: thêm `android:dataExtractionRules="@xml/data_extraction_rules"` và `android:fullBackupContent="@xml/backup_rules"` vào thẻ `<application>`.
    - Kiểm tra lint XML cấu hình backup bằng lệnh `.\gradlew.bat lintDebug` để đảm bảo không có cảnh báo/lỗi cú pháp resource [N4].

- [ ] **Bước 10: Quality Gate & Cập nhật Documentation [M9, BLOCKER X1, m-i]**
  - **Format thủ công (manual format)** các vi phạm ktlint trong phạm vi của task [BLOCKER X1]:
    - Sửa các dòng vi phạm đã biết: `StoryRepository.kt:220,238,241`, `ScraperModule.kt:36`, `AppNavigation.kt:86,332`, `ReaderViewModel.kt:428`, `SettingsScreen.kt:46,394`, `SettingsViewModel.kt:103,129`.
    - Đảm bảo tất cả các file tạo mới tuân thủ 100% quy tắc ktlint (`max_line_length = 150`, indent 4, LF).
    - **TUYỆT ĐỐI KHÔNG chạy `ktlintFormat` toàn module** để không làm xáo trộn các file khác.
    - **TUYỆT ĐỐI KHÔNG chạy `git checkout -- TangThuVienScraper.kt`** để bảo vệ dữ liệu chưa commit của người dùng [BLOCKER X1].
  - Chạy kiểm tra chất lượng đầy đủ:
    `.\gradlew.bat ktlintCheck; .\gradlew.bat testDebugUnitTest; .\gradlew.bat assembleDebug`
    *Tiêu chí chấp nhận [M9, X1, m-q]*: Output `.\gradlew.bat ktlintCheck` không còn dòng vi phạm nào thuộc các file trong bảng Files của plan (các vi phạm còn lại chỉ ở `TangThuVienScraper.kt` và `StorySource.kt:47:87` nằm ngoài bảng Files). 100% unit tests pass.
  - Cập nhật tài liệu:
    - `docs/scraping.md`: thêm mục Wikidich (URL patterns, AJAX `/book/index`, selectors, cookies SSO); chuyển TruyenCom & TruyenFull sang mục lưu trữ đã ngừng hoạt động.
    - `docs/architecture.md`: cập nhật Remote Layer, Repository Layer (`ISourceAuthRepository`), UI Layer, danh sách 3 nguồn.
    - `docs/progress.md`: cập nhật các tính năng hoàn thành.
    - `docs/changelog.md`: thêm entries trong `## [Unreleased]`.

---

### 7. Test Contract

| # | Hành vi nghiệp vụ | Điều kiện đầu vào (Input) | Kết quả mong đợi / Thông báo tiếng Việt | Boundary / Failure mode |
|---|---|---|---|---|
| 1 | Parse cookie thô từ clipboard | Nhập chuỗi chỉ có token: `s%3Aabc123xyz` | `CookieStringParser.parse` trả về `[("express.sid", "s%3Aabc123xyz")]` | Chuỗi rỗng / khoảng trắng trả về danh sách rỗng |
| 2 | Parse chuỗi cookie đầy đủ | Chuỗi header: `Cookie: express.sid=token1; cf_clearance=cf2` | Trả về 2 cặp: `("express.sid", "token1")` và `("cf_clearance", "cf2")` | Ký tự hoa thường tiền tố `cookie:` / `Cookie:` đều được loại bỏ |
| 3 | Nhập cookie qua dialog với `SessionVerifyResult`, Client Probe Cô Lập và Xóa Cookie Cũ [M1', N3, X2, Y1, m-o] | Dán cookie chứa `express.sid=token` vào dialog, kiểm tra probe request bằng `probeClient` (`CookieJar.NO_COOKIES`) | (1) Thành công: probe request gửi đúng header `Cookie: express.sid=<pasted>` [m-o]; trước khi ghi cookie mới, xóa sạch mọi cookie `express.sid` cũ (cả host-only và domain `.wikicv.org`), sau đó ghi duy nhất 1 cookie host-only cho `https://wikicv.org`, CookieStore chỉ còn đúng 1 cookie mới host-only [Y1]; trả về `SessionVerifyResult.Success`, `authState` chuyển `true`, dialog đóng.<br>(2) Cookie hỏng/hết hạn: trả về `SessionVerifyResult.InvalidCookie`, hiện thông báo: "Chuỗi cookie không hợp lệ, thiếu token hoặc phiên không hoạt động", KHÔNG ghi CookieStore, bảo toàn cookie cũ nếu có.<br>(3) Mất mạng/timeout: trả về `SessionVerifyResult.NetworkError`, hiện thông báo: "Không thể kết nối đến máy chủ để xác minh phiên, vui lòng thử lại", KHÔNG ghi đè và KHÔNG hạ `authState`.<br>(4) Test MockWebServer: CookieStore đang có `OLD`, dán rác nhận Set-Cookie `GUEST` + marker -> kết quả `InvalidCookie` và CookieStore vẫn giữ nguyên `OLD` [X2]. | Nhờ `CookieJar.NO_COOKIES`, header `Cookie` tạm không bị ghi đè bằng cookie cũ và `Set-Cookie` của server không bị ghi vào `CookieManager`. Xóa cookie cũ trước khi ghi cookie mới host-only ngăn chặn triệt để xung đột gửi 2 cookie lên server [Y1]. |
| 4 | Tìm kiếm Wikidich khi chưa đăng nhập | Gửi query tìm kiếm tới Wikidich, HTML trả về có `Đăng nhập để xem nội dung` và không có `.book-item` | Ném `ScraperException.LoginRequiredException("wikidich")` | Không ném ngoại lệ chung `Exception`; không kích hoạt circuit breaker |
| 5 | Gộp tìm kiếm đa nguồn khi 1 nguồn yêu cầu login | Tìm kiếm "tiên hiệp" trên toàn bộ nguồn khi Wikidich chưa đăng nhập | Kết quả từ Tàng Thư Viện và SsTruyện vẫn hiển thị đầy đủ; SearchScreen hiển thị banner: "Nguồn Wikidich yêu cầu đăng nhập tài khoản để tìm kiếm / lọc nội dung. [Đăng nhập ngay]" | Không làm crash hoặc rỗng kết quả tìm kiếm của các nguồn còn lại; khi nguồn chạy thành công gọi `clearLoginRequired` [m-a] |
| 6 | Đăng nhập Wikidich qua WebView thành công | User đăng nhập thành công trên DTH forum, WebView redirect về `wikicv.org`, có `express.sid` và probe `verifySessionOnline` sạch marker | `authState` chuyển `true`, `loginRequiredSources` xóa `"wikidich"`, banner xanh xuất hiện trên `SourceLoginScreen` | Nếu user chưa nhập thông tin mà chỉ ở trang login: KHÔNG báo đăng nhập thành công giả |
| 7 | Đọc offline truyện của TruyenCom / TruyenFull đã tải | Mở chương truyện của TruyenCom (`sourceId = "truyencom"`) đã lưu trong Room DB | Nội dung chương hiển thị ngay từ `chapterDao`, không ném lỗi dù scraper đã bị xóa | Nếu truyện chưa tải và cố tải online: hiển thị thông báo "Nguồn truyện này không còn hoạt động hoặc không được hỗ trợ" |
| 8 | Quản lý nguồn trong Cài đặt | Mở Cài đặt mục "Tài khoản nguồn truyện" | Hiển thị nguồn Wikidich kèm trạng thái "Đã đăng nhập" (xanh) hoặc "Chưa đăng nhập" (xám). Bấm "Đăng nhập" điều hướng tới `source_login/wikidich` | Bấm "Đăng xuất" xóa sạch cookie (cả domain gốc `.wikicv.org`) và cập nhật trạng thái về "Chưa đăng nhập" |
| 9 | Migration DataStore nguyên tử và bảo lưu lựa chọn tắt nguồn [M7', N5, X3, m-n] | Người dùng cũ mở app hoặc vào Cài đặt (`isWikidichMigrated = false`) | Đọc cờ trước, nếu đã migrate (`true`) thì return ngay không gọi `edit` [m-n]. Nếu chưa migrate, chạy trong `dataStore.edit`: loại bỏ `truyencom`, `truyenfull`, thêm `wikidich` và set `isWikidichMigrated = true`. Kích hoạt tại `StoryRepository.activeSources()` và `SettingsViewModel.init` [X3] | Nếu user đã chủ động tắt `wikidich` sau khi migrate: `normalizeEnabledSources` giữ nguyên không tự ý bật lại; không để kẹt id nguồn chết |
| 10 | Duyệt thể loại khi Wikidich chưa đăng nhập | User lọc truyện theo thể loại ở `SearchScreen` hoặc `ExploreScreen` | Các nguồn TTV, SST vẫn trả về danh sách; Wikidich ném `LoginRequiredException` được rethrow đúng ở catch trang và bắt ở catch ngoài, kích hoạt banner nhắc đăng nhập | Không bị nuốt ngoại lệ ở catch con; thuật toán gộp genre union không làm mất thể loại [M2'] |
| 11 | Suy đoán URL chương kế tiếp trong Reader [m-e, m-k] | Gọi `tryConstructChapterUrl` với URL chương tuần tự vs URL hash Wikidich | URL dạng tuần tự số: trả về URL kế tiếp; URL dạng hash của Wikidich: trả về `null` | Không tạo ra URL sai định dạng gây crash mạng |
| 12 | Hiển thị banner cảnh báo trên ExploreScreen [M5'] | Lắng nghe `loginRequiredSources` khi có nguồn yêu cầu đăng nhập | `ExploreScreen` hiển thị banner nhắc nhở tương tự `SearchScreen`, bấm nút dẫn tới trang đăng nhập | 5 test cases khởi tạo `ExploreViewModel` đều pass với mock repository |
| 13 | Tải và phân trang danh sách chương AJAX Wikidich [N1, X4] | Gọi `getFirstPageChapters` và `getRemainingChapters` với fragment `/book/index` | (1) Trang 1 (`start=0, size=99`): nếu không có thẻ link chương hợp lệ nào -> ném `ScraperException.ParseException("Không trích xuất được danh sách chương từ máy chủ Wikidich")`.<br>(2) Các trang tiếp theo: nếu fragment không còn thẻ liên kết trang kế `data-action="loadBookIndex"` -> ĐÃ HẾT TRANG: kết thúc phân trang bình thường và trả về toàn bộ danh sách chương đã thu thập [X4]. | TUYỆT ĐỐI KHÔNG ném ParseException khi đến trang cuối cùng; giới hạn an toàn `maxPages = 200` ngăn lặp vô tận |
| 14 | Trích xuất truyện và lọc thể loại Wikidich [N2, X5, m-m] | Parser duyệt các khối truyện `.book-item` hoặc truy vấn thể loại / hoàn thành | Trích xuất đủ title, tác giả, cover, URL, gán tường minh `sourceId = "wikidich"` [m-m]; truy vấn hoàn thành qua `status=5794f03dd7ced228f4419191&qs=1`; thể loại hợp lệ theo bảng ánh xạ 10 slug phổ biến [X5] | Thể loại lạ/không hỗ trợ: trả về `emptyList()` ngay mà không gọi mạng [X5] |

---

### 8. Verification Plan

1. **Unit Test Classes**:
   - `app/src/test/java/com/personal/apptruyen/data/remote/auth/CookieStringParserTest.kt`: kiểm tra parse chuỗi cookie đa định dạng.
   - `app/src/test/java/com/personal/apptruyen/data/remote/auth/SourceAuthManagerTest.kt`:
     - Kiểm tra nhận diện đăng nhập chính xác, loại trừ forum domain, logout xóa cookie đúng hạn.
     - Kiểm tra probe dán cookie bằng client cô lập `probeClient` (`CookieJar.NO_COOKIES`), kiểm tra assert `server.takeRequest().headers["Cookie"] == "express.sid=<pasted>"` [X2, m-o].
     - MockWebServer: CookieStore đang có `express.sid=OLD`, probe dán token rác nhận Set-Cookie `GUEST` + marker -> kết quả `InvalidCookie` và CookieStore vẫn giữ nguyên `OLD` [X2].
     - Kiểm tra nhánh thành công: assert xóa sạch cookie cũ trước khi ghi cookie mới host-only, xác nhận sau import CookieStore chỉ còn duy nhất 1 cookie host-only [Y1].
     - Kiểm tra online verify probe `tim-kiem?qs=1&q=tien` trả về `SessionVerifyResult.Success`.
     - Kiểm tra lỗi mạng/timeout trả về `SessionVerifyResult.NetworkError`, không xóa cookie và không hạ authState [M1', N3].
   - `app/src/test/java/com/personal/apptruyen/data/repository/SourceAuthRepositoryTest.kt`: kiểm tra flow auth state, `loginRequiredSources`, gọi manager tương ứng.
   - `app/src/test/java/com/personal/apptruyen/data/remote/WikidichScraperTest.kt`: kiểm tra các ca kiểm thử theo `04-scraping.md` [N1, N2, X4, X5, m-m]:
     1. `parseStoryDetailFromDoc`: trích xuất title, tác giả, thể loại, mô tả, ảnh bìa từ `story_detail.html`, assert `sourceId == "wikidich"` [m-m].
     2. `parseChapterContentFromDoc`: trích xuất đoạn văn từ `chapter_content.html`, lọc sạch ads và watermark `【❁.*❁】`.
     3. `parseChapterIndexAjax`: trích xuất danh sách chương từ fixture fragment AJAX `/book/index` (với thuật toán trích xuất cut từ regex và chữ ký SHA-256) [M2'].
     4. `parseChapterIndexAjax_pagination`: trích xuất phân trang trang 2 từ `chapter_index_ajax_p2.html` [N1].
     5. `parseChapterIndexAjax_lastPage_completesNormally`: fragment trang cuối (không có liên kết trang kế) dừng phân trang bình thường, trả về danh sách, không ném ngoại lệ [X4].
     6. `parseChapterIndexAjax_empty_throwsException`: xác nhận ném `ParseException` khi fragment trang 1 không có chương [N1, X4].
     7. `parseSearchResultsFromDoc`: trích xuất danh sách truyện từ khối `.book-item` trong `search_logged_in.html`, assert `sourceId == "wikidich"` [N2, m-m].
     8. `parseSearchResults_loggedOut`: phát hiện marker `Đăng nhập để xem nội dung` và ném `LoginRequiredException`.
     9. `getCompletedStories`: kiểm tra query URL trạng thái hoàn thành [N2].
     10. `getStoriesByGenre_unknownGenre`: thể loại lạ trả về `emptyList()` ngay mà không gọi mạng [X5].
   - `app/src/test/java/com/personal/apptruyen/data/repository/StoryRepositoryTest.kt`:
     - Test `activeSources()`: kiểm tra tự động kích hoạt migration nguyên tử DataStore [N5, X3].
     - Test `searchStories` và `getStoriesByGenre` khi một nguồn ném `LoginRequiredException`: xác nhận rethrow không bị nuốt, không kích hoạt circuit breaker, cập nhật `loginRequiredSources`, và gọi `clearLoginRequired` khi thành công [m-a].
     - Test `getChapterContent` với truyện offline của nguồn cũ: đọc thành công từ `chapterDao`; với chương chưa tải: ném `"Nguồn truyện này không còn hoạt động hoặc không được hỗ trợ"`.
     - Test `getGenreList`: xác nhận thuật toán gộp genre union không làm rỗng thể loại [M2'].
   - `app/src/test/java/com/personal/apptruyen/ui/search/SearchViewModelTest.kt`: kiểm tra cập nhật `loginRequiredSources` và kích hoạt banner.
   - `app/src/test/java/com/personal/apptruyen/ui/explore/ExploreViewModelTest.kt`: cập nhật 5 constructor calls (lines 47, 63, 78, 94, 106) truyền mock `ISourceAuthRepository`, kiểm tra banner loginRequired [M5'].
   - `app/src/test/java/com/personal/apptruyen/ui/settings/SettingsViewModelTest.kt`: kiểm tra `ALL_ACTIVE_SOURCES` mới, migration nguyên tử cờ `IS_WIKIDICH_MIGRATED` trên `DataStore<Preferences>`, và không tự bật lại nếu user đã tắt [M7', N5, X3, m-n].
   - `app/src/test/java/com/personal/apptruyen/ui/reader/ReaderViewModelTest.kt`: file test mới kiểm tra hàm `tryConstructChapterUrl` với link tuần tự và link hash Wikidich [m-e, m-k].
   - `app/src/test/java/com/personal/apptruyen/ui/login/SourceLoginViewModelTest.kt`: kiểm tra import cookie tương ứng 3 nhánh `SessionVerifyResult`, xử lý lỗi kết nối tiếng Việt, logout [M1', N3, m-l].

2. **Lệnh thực thi kiểm chứng tự động (Quality Gate)**:
   ```powershell
   # 1. Format thủ công các file vi phạm trong phạm vi sửa đổi của plan [BLOCKER X1]:
   # Sửa tay các dòng vi phạm: StoryRepository.kt:220,238,241; ScraperModule.kt:36; AppNavigation.kt:86,332;
   # ReaderViewModel.kt:428; SettingsScreen.kt:46,394; SettingsViewModel.kt:103,129.
   # TUYỆT ĐỐI KHÔNG chạy ktlintFormat toàn module!
   # TUYỆT ĐỐI KHÔNG chạy git checkout -- TangThuVienScraper.kt!

   # 2. Kiểm tra lint tài nguyên XML backup [N4]:
   .\gradlew.bat lintDebug

   # 3. Quality Gate đầy đủ:
   .\gradlew.bat ktlintCheck; .\gradlew.bat testDebugUnitTest; .\gradlew.bat assembleDebug
   ```
   *Tiêu chí chấp nhận [M9, BLOCKER X1, m-q]*: Output `.\gradlew.bat ktlintCheck` không còn dòng vi phạm nào thuộc các file trong bảng Files của plan (các vi phạm còn lại chỉ ở `TangThuVienScraper.kt` và `StorySource.kt:47:87` nằm ngoài bảng Files). 100% unit tests pass.

3. **Kiểm thử thủ công trên thiết bị (Manual Testing)**:
   - **Tìm kiếm khi đã đăng nhập [m-j]**: Đăng nhập tài khoản Wikidich trên thiết bị thật, tìm kiếm từ khóa truyện -> Kết quả hiển thị đầy đủ danh sách truyện từ Wikidich kèm các nguồn khác, không còn marker yêu cầu đăng nhập.
   - **Dán cookie khi đã có cookie khách [Y1]**: Truy cập Wikidich khi chưa đăng nhập (để WebView/CookieManager nhận cookie `express.sid` dạng khách host-only), sau đó vào Cài đặt -> Đăng nhập Wikidich -> Chọn Dán cookie và dán token đã đăng nhập hợp lệ -> Kiểm tra hệ thống xóa sạch cookie khách cũ, lưu duy nhất cookie đăng nhập host-only, tìm kiếm hoạt động ngay lập tức mà không bị xung đột gửi kèm cookie khách cũ.
   - **Xoay màn hình WebView [m-c, m-g]**: Mở `SourceLoginScreen`, mở trang đăng nhập, xoay màn hình điện thoại → WebView giữ nguyên URL hiện tại và lịch sử duyệt trang thông qua `saveState`/`restoreState`, không bị reload lại trang chủ hay mất luồng redirect.
   - **Chặn domain ngoài**: Trong WebView đăng nhập, nếu có link ra ngoài domain Wikidich/DTH → Mở bằng trình duyệt ngoài của hệ thống, không render trong WebView.
   - **Banner nhắc đăng nhập**: Vào Search/Explore khi chưa đăng nhập Wikidich → Banner nhắc đăng nhập hiển thị rõ ràng, bấm "Đăng nhập ngay" dẫn đúng tới màn hình đăng nhập.
   - **Đọc truyện đã tải của nguồn cũ**: Mở truyện TruyenCom/TruyenFull đã tải trong mục Đã tải → Đọc bình thường không lỗi.
   - **Kiểm tra tắt nguồn Wikidich [M7', N5, X3]**: Vào Cài đặt tắt Wikidich, khởi động lại app → Wikidich vẫn giữ nguyên trạng thái tắt, không bị tự động bật lại; người dùng cũ mở app lần đầu được tự động kích hoạt Wikidich ngay cả khi chưa vào Cài đặt.
   - **Bảo mật Auto Backup [N4]**: Kiểm tra `app_webview/` được loại trừ trong `data_extraction_rules.xml` và `backup_rules.xml` với `domain="root"`.

---

### 9. Rủi Ro & Phòng Ngừa

| Rủi ro | Mức độ | Technical Control cụ thể trong mã nguồn |
|---|:---:|---|
| Thay đổi chưa commit của người dùng bị xóa mất hoặc format lan sang file ngoài phạm vi [BLOCKER X1] | **Cao** | TUYỆT ĐỐI BỎ lệnh `git checkout -- TangThuVienScraper.kt`. KHÔNG chạy `ktlintFormat` toàn module. Chỉ format thủ công các file vi phạm trong phạm vi của plan; tiêu chí nghiệm thu là 0 vi phạm trên các file trong bảng Files. |
| Client probe dùng chung CookieJar làm rò rỉ cookie hoặc ghi đè cookie cũ khi dán token hỏng [X2] | **Cao** | Sử dụng `okHttpClient.newBuilder().cookieJar(CookieJar.NO_COOKIES).build()` cho probe dán cookie. Chỉ ghi CookieStore khi probe `Success`. MockWebServer test xác nhận cookie cũ được bảo toàn 100%. |
| Trùng lặp cookie `express.sid` (cookie khách host-only cũ và cookie đăng nhập mới) khiến server chọn nhầm cookie khách [MAJOR Y1] | **Cao** | Trong `importCookies`, khi probe `Success`: xóa sạch mọi cookie `express.sid` cũ (cho cả `https://wikicv.org` và domain `.wikicv.org`), sau đó ghi duy nhất 1 cookie host-only cho `https://wikicv.org` (không dùng thuộc tính Domain) rồi gọi `flush()`. |
| Nhận diện đăng nhập nhầm khi user mới tải trang login do `express.sid` cấp cho khách [M1] | **Cao** | Loại bỏ `forum.dichtienghoa.com` khỏi domain kiểm tra; chỉ xác nhận đăng nhập khi URL đã quay về `wikicv.org` VÀ online verify không chứa marker `Đăng nhập để xem nội dung`. |
| Xác minh phiên thất bại do mất mạng khiến người dùng bị logout ngoài ý muốn [M1', N3] | **Cao** | Trong `verifySessionOnline`: sử dụng `SessionVerifyResult.NetworkError` khi gặp ngoại lệ kết nối/timeout; TUYỆT ĐỐI không hạ `_authState` hay xóa cookie. Dán cookie hỏng trả về `InvalidCookie` và không ghi đè CookieStore [N3]. |
| Vi phạm ranh giới kiến trúc Clean Architecture khi UI gọi trực tiếp Remote hoặc import sai tầng [B1, m-l] | **Cao** | Tạo `ISourceAuthRepository` tại `data/repository/`. Đặt `SessionVerifyResult` và `SourceAuthConfig` trong `data/model/SourceAuth.kt` [m-l]. Hằng số `SourceIds` đặt tại `data/local/SettingsDataStore.kt` [X3]. ViewModel chỉ inject Repository và model. |
| Nuốt `LoginRequiredException` ở các luồng thể loại/hoàn thành [M4] | **Cao** | Sửa catch cấp trang trong `StoryRepository` (line 124, 181): tường minh rethrow `CancellationException` và `LoginRequiredException`. |
| Phân trang AJAX bị crash hoặc ném ngoại lệ khi đến trang cuối [X4] | **Trung bình** | Chỉ ném `ParseException` khi trang 1 rỗng; các trang tiếp theo nếu không tìm thấy liên kết phân trang kế tiếp thì dừng vòng lặp bình thường và trả về kết quả đã thu thập. Giới hạn `maxPages = 200` chống lặp vô hạn. |
| Sai selector danh sách truyện hoặc slug thể loại gây rỗng kết quả [N2, X5] | **Trung bình** | Áp dụng selector `.book-item` đã kiểm chứng trên HTML thật; URL hoàn thành `status=5794f03dd7ced228f4419191&qs=1`; bảng ánh xạ 10 slug thể loại chuẩn từ `wikicv_home.html`, fallback `emptyList()` ngay nếu slug lạ [X5]. |
| Người dùng cũ không được bật Wikidich hoặc bị tự động bật lại sau khi đã chủ động tắt [M7', N5, X3, m-n] | **Trung bình** | Sử dụng cờ migration `IS_WIKIDICH_MIGRATED` trong khối `dataStore.edit` nguyên tử (đọc cờ trước để bypass [m-n]). Kích hoạt migration tại `StoryRepository.activeSources()` (chạy ngay khi mở app) và `SettingsViewModel.init`. Hàm `normalizeEnabledSources` tôn trọng triệt để lựa chọn tắt của người dùng. |
| Rò rỉ cookie session qua log hoặc Google Cloud Auto Backup [M6, M6', N4] | **Trung bình** | Xóa toàn bộ log raw cookie trong `WebViewCookieHandler`. Cấu hình `data_extraction_rules.xml` và `backup_rules.xml` với `domain="root"` loại trừ toàn bộ `app_webview/`. |
| Rò rỉ WebView gây crash OutOfMemory hoặc reload trắng khi xoay màn hình [M8, m-c, m-g] | **Trung bình** | Sử dụng `AndroidView(onRelease = { it.stopLoading(); it.destroy() })` và `rememberSaveable` giữ URL và navigation history của WebView qua `saveState`/`restoreState`. |

---

### 10. Cập Nhật Documentation

Theo ma trận `.agents/rules/06-docs.md`, sau khi triển khai sẽ cập nhật:
- `docs/architecture.md`: Cập nhật cấu trúc Clean Architecture với `ISourceAuthRepository`, `SourceAuthManager`, `SourceAuth.kt`, `WikidichScraper`; danh sách 3 nguồn hoạt động; cập nhật timestamp.
- `docs/scraping.md`: Thêm tài liệu kỹ thuật Wikidich (`wikicv.org`), CSS selectors khối `.book-item`, cơ chế phân trang AJAX `/book/index` và điều kiện dừng, chữ ký SHA-256 fuzzyCut, bảng ánh xạ thể loại, cơ chế SSO DTH; lưu trữ thông tin TruyenCom & TruyenFull vào mục đã ngừng hoạt động.
- `docs/progress.md`: Thêm mục hoàn thành cho nguồn Wikidich, hạ tầng đăng nhập nguồn, và gỡ bỏ 2 nguồn cũ.
- `docs/changelog.md`: Thêm mục `### Thêm mới`, `### Sửa đổi`, `### Xóa bỏ` tương ứng cho phiên bản v2.9.0 trong `## [Unreleased]`.

---

## Audit History

<!-- Reviewer chỉ append "### Audit Run N" ở dưới. Không sửa phần Technical Plan. -->

### Audit Run 1

- **Reviewer**: Claude Code CLI (Claude Opus 5.5, `claude-opus-5-5`)
- **Ngày**: 2026-10-13
- **Đối tượng**: Technical Plan Revision 1

#### Kiểm chứng lỗi cũ

| # | Lỗi từ Run trước | Trạng thái |
|---|---|---|
| — | Đây là lượt audit đầu tiên, chưa có Run trước | N/A |

#### Mental Simulation Matrix (S1–S6 + Domain)

| Kịch bản | Kết quả | Bằng chứng | Chi tiết |
|---|---|---|---|
| S1 Cold-start & Build | PASS (baseline) / FAIL (quality gate) | `[CMD]` compileDebugKotlin Exit 0; `[CMD]` ktlintCheck Exit 1; `[SRC]` `di/ScraperModule.kt:21-35`, `di/AppModule.kt:36-53` | Baseline compile sạch, không thêm dependency, API WebView (`setAcceptThirdPartyCookies`, `loadUrl(url, headers)`) đều < minSdk 26. Hilt: `SourceAuthManager` `@Singleton @Inject` hợp lệ. NHƯNG ktlint baseline đã FAIL trên chính các file plan sẽ sửa (`StoryRepository.kt:220,238,241`, `ScraperModule.kt:36`, `AppNavigation.kt:86,332`, `SettingsScreen.kt:46,394`, `SettingsViewModel.kt:103,129`) → Quality Gate Bước 9 không thể đạt nếu plan không quy định xử lý. Ngoài ra `SearchViewModelTest.kt:60…148` và `SettingsViewModelTest.kt:46,75` gọi constructor theo vị trí → sẽ không biên dịch khi thêm tham số `SourceAuthManager`, nhưng 2 file này không nằm trong bảng Files. |
| S2 Lifecycle & Teardown | FAIL | `[SRC]` plan Bước 5; `[ADV]` timeline L1 | `AndroidView { WebView }` không có `onRelease`/`DisposableEffect` gọi `stopLoading()`/`destroy()`; không nêu cách giữ trạng thái khi xoay màn hình (WebView tái tạo → `loadUrl(loginUrl)` lại, mất form/redirect đang dở). |
| S3 Database & DataStore | PASS (Room) / FAIL (DataStore) | `[SRC]` `data/local/entity/StoryEntity.kt:24`, `data/local/AppDatabase.kt:57`; `[SRC]` `StoryRepository.kt:64-76`, `SettingsViewModel.kt:119-124,208-218` | Room không đổi schema, `sourceId` đã có → an toàn. Nhưng `ENABLED_SOURCES` của người dùng cũ đang lưu `"truyencom,tangthuvien,sstruyen,truyenfull"`: `activeSources()` lọc theo id đã lưu → `wikidich` KHÔNG BAO GIỜ được bật cho user cũ cho đến khi tự vào Cài đặt bật tay; id chết `truyencom/truyenfull` còn kẹt trong set và tính vào điều kiện "không cho tắt nguồn cuối cùng" (`current.size > 1`). Plan không có bước chuẩn hóa/migration key này. |
| S4 Cross-Task State Flow | FAIL | `[SRC]` grep `import com.personal.apptruyen.data.remote` trong `ui/` → 0 kết quả; `[SRC]` `StoryRepository.kt:165-198` | (a) Plan cho `SearchViewModel`, `SettingsViewModel`, `SourceLoginViewModel` inject thẳng `SourceAuthManager` (thuộc `data/remote/auth`) → lần đầu tiên tầng UI phụ thuộc trực tiếp `data/remote`, phá luồng Screen → ViewModel → Repository. (b) `getStoriesByGenre` có `catch (_: Exception) { emptyList() }` ở cấp từng trang (`StoryRepository.kt:181`) → `LoginRequiredException` bị nuốt trước khi tới catch ngoài; plan Bước 6 không yêu cầu sửa catch trong → banner không bao giờ xuất hiện ở luồng lọc thể loại. Tương tự `getCompletedStories:124`. |
| S5 Security & Privacy | FAIL | `[SRC]` `cookie/WebViewCookieHandler.kt:70,96`; `[SRC]` `AndroidManifest.xml:13`; `[SRC]` `build/dth_login.html:33,50` (NodeBB); `[ADV]` timeline S5-1, S5-2 | (a) `WebViewCookieHandler.kt:70` hiện đang log nguyên chuỗi cookie và `getCookieString()` trả raw cookie; plan Bước 3 chỉ nói "thay thế" `isLoggedIn`, không yêu cầu xóa log/API này, trong khi Risk Table khẳng định handler "chỉ log boolean". (b) `allowBackup="true"` và không có `dataExtractionRules`/`fullBackupContent` → CSDL cookie của WebView (chứa `express.sid`) có thể đi vào Auto Backup/device-transfer; plan thêm dữ liệu phiên nhạy cảm nhưng không đánh giá lại. (c) WebView bật JS nhưng không giới hạn host điều hướng (`shouldOverrideUrlLoading`). (d) Logic `isLoggedIn` = "có cookie `express.sid`" là sai: xem timeline S5-1. |
| S6 External Resilience | FAIL | `[SRC]` `build/wikicv_story.html:1146,1941`; `build/wk_search.html:264`; `[SRC]` `ReaderViewModel.kt:624-637,802` | (a) Danh sách chương Wikidich được nạp bằng JS (`<div class="volume-list"></div>` + `loadBookIndex(0, 99, false)`) → HTML tĩnh không có chương; plan không đặc tả `getFirstPageChapters`/`getRemainingChapters`/`getChapterList` cho Wikidich (endpoint, tham số, phân trang) → scraper không đọc được truyện. (b) Selector `.book-item` không xuất hiện trong bất kỳ fixture nào (grep 0 kết quả); chưa có fixture tìm kiếm khi đã đăng nhập → parser kết quả tìm kiếm không có bằng chứng. (c) Marker `data-action="login"` xuất hiện trên MỌI trang khi chưa đăng nhập (`wikicv_story.html:110…619`, `wikicv_chap.html:157…455`) nên không đủ đặc hiệu; marker đúng là text `Đăng nhập để xem nội dung` (`wk_search.html:264`). (d) Header `Referer` và luồng 302 của `wikicv.org/auth/dth` → UNVERIFIED (host `wikicv.org` không có trong allowlist curl; chỉ có `build/wk_auth.html:4` cho thấy redirect tới `forum.dichtienghoa.com/dth/auth?code=…`). |
| Domain | FAIL | `[SRC]` `StoryRepository.kt:211-259`; `build/wikicv_story.html:316-338`; `build/wikicv_chap.html:409` | (a) `getGenreList` lấy GIAO các slug thể loại giữa mọi nguồn; thể loại Wikidich là `/tim-kiem?tag=<ObjectId>` → nếu Wikidich trả danh sách khác rỗng, phép giao với TTV/SST sẽ rỗng → mất toàn bộ thể loại ở Explore/Search. Plan không đặc tả `getGenreList`/`getStoriesByGenre`/`getCompletedStories` của Wikidich. (b) Watermark `【❁ Nguyên Hà (Wikidich) ❁】` ở cuối `#bookContentBody` sẽ bị TTS đọc; plan không có quy tắc làm sạch. (c) `tryConstructChapterUrl` (`ReaderViewModel.kt:802`) thay `/chuong-1-…-arNEh8QsREQ0Hzh7` thành `/chuong-2` → URL sai trên Wikidich (cần hash). |

#### Adversarial Timelines

```
Cơ chế: Phát hiện đăng nhập (SourceAuthManager.isLoggedIn) — S5-1
T1: User mở SourceLoginScreen, WebView GET https://wikicv.org/auth/dth | T2: Server Express tạo session để giữ state OAuth (code=… trong build/wk_auth.html:4) và Set-Cookie express.sid cho wikicv.org; trang DTH (NodeBB, build/dth_login.html:33) cũng cấp express.sid cho khách | T3: onPageFinished → isLoggedIn quét domains (gồm cả forum.dichtienghoa.com) thấy express.sid → onLoginDetected → banner "Đã đăng nhập thành công"
Kết cục: Báo đăng nhập thành công khi user CHƯA nhập mật khẩu; Settings hiện "Đã đăng nhập"; tìm kiếm vẫn ném LoginRequired → LỖI (MAJOR). (Set-Cookie thực tế là UNVERIFIED, đề xuất User chạy: curl.exe -q -sI --max-time 10 https://wikicv.org/auth/dth)
```

```
Cơ chế: Cookie hết hạn giữa chừng (TOCTOU) — S5-2
T1: Settings đọc getAuthState("wikidich") = true (cookie còn trong CookieManager) | T2: Server hủy session (hết hạn/đăng xuất nơi khác), cookie local vẫn còn | T3: searchStories → trang "Đăng nhập để xem nội dung" → LoginRequiredException → markLoginRequired
Kết cục: Search hiện banner yêu cầu đăng nhập trong khi Settings vẫn hiện "Đã đăng nhập" và nút "Đăng xuất"; plan không quy định markLoginRequired hạ authState → trạng thái mâu thuẫn → LỖI (MAJOR, gộp vào M3)
```

```
Cơ chế: Gộp đa nguồn + LoginRequiredException ở luồng thể loại — S4/Domain
T1: SearchViewModel gọi repository.getStoriesByGenre (StoryRepository.kt:155) | T2: WikidichScraper.getStoriesByGenre ném LoginRequiredException ở trang p | T3: catch (_: Exception) tại StoryRepository.kt:181 trả emptyList; catch ngoài không bao giờ thấy exception
Kết cục: Không gọi markLoginRequired, không có banner; user thấy kết quả thiếu nguồn mà không biết lý do → LỖI (MAJOR)
```

```
Cơ chế: Ghi đồng thời loginRequiredSources (trục Đồng thời)
T1: async search Wikidich ném LoginRequired → markLoginRequired | T2: cùng lúc async getStoriesByGenre khác (SearchViewModel.kt:263 và 358) cũng markLoginRequired; SourceLoginViewModel.onLoginDetected chạy clear | T3: nếu cài đặt bằng `_flow.value = _flow.value + id` (read-modify-write) có thể ghi đè lẫn nhau
Kết cục: AN TOÀN chỉ khi bắt buộc dùng MutableStateFlow.update {}; plan chưa ghi rõ → MINOR (m1)
```

```
Cơ chế: Vòng đời WebView đăng nhập (trục Vòng đời Android) — L1
T1: User đang ở form đăng nhập DTH | T2: Xoay màn hình / đổi cấu hình | T3: AndroidView factory chạy lại, loadUrl(config.loginUrl) mới; WebView cũ không được destroy
Kết cục: Mất form, tạo code OAuth mới, rò rỉ WebView cũ → LỖI (MAJOR, S2)
```

```
Cơ chế: Đọc offline truyện nguồn đã gỡ (trục Thất bại giữa chừng)
T1: User mở chương truyencom chưa tải | T2: sourceFor() ném IllegalArgumentException (StoryRepository.kt:82-84) | T3: catch tại StoryRepository.kt:522 ném "Chương này chưa được tải…"
Kết cục: Không crash (AN TOÀN về dữ liệu), nhưng thông báo khác Test Contract #7; chương đã tải trả ngay tại StoryRepository.kt:498-501 → AN TOÀN. Plan cần nêu cụ thể chỗ sửa thông báo (MAJOR M6 gộp).
```

Trục TOCTOU kiểm chương offline: N/A cho phần thay đổi này (logic `getChapterContent` không bị plan sửa ngoài thông báo).

#### Commands Executed

Trích từ `evidence.md` (reviewer không chạy lệnh):

- `### [CMD] cmd.exe /d /c gradlew.bat compileDebugKotlin` — `Exit: 0` — "BUILD SUCCESSFUL in 31s" (cảnh báo `!!` tại `BackupRepository.kt:94`, deprecated tại `SettingsScreen.kt:36,193,958`).
- `### [CMD] cmd.exe /d /c gradlew.bat ktlintCheck` — `Exit: 1` — "Task :app:ktlintMainSourceSetCheck FAILED"; vi phạm gồm `StoryRepository.kt:220:40`, `ScraperModule.kt:36:1`, `AppNavigation.kt:86:1`, `SettingsScreen.kt:46:1`, `SettingsViewModel.kt:103:27`…
- `### [CMD] cmd.exe /d /c gradlew.bat testDebugUnitTest` — `Exit: 0` — "BUILD SUCCESSFUL in 19s".
- `### [CMD] git status --short` — `Exit: 0` — working tree có nhiều thay đổi chưa commit (bao gồm các file plan sẽ sửa).
- `### [CMD] node --test .agents/scripts/code-freeze-guard.test.js` — `Exit: 0` — "pass 31, fail 0".

UNVERIFIED (đề xuất User tự chạy): `curl.exe -q -sI --max-time 10 https://wikicv.org/auth/dth` (host ngoài allowlist) để xác nhận Set-Cookie `express.sid` trước khi đăng nhập; `.\gradlew.bat testDebugUnitTest --tests "com.personal.apptruyen.data.repository.StoryRepositoryTest"` sau khi đổi constructor.

#### Phân loại lỗi

**[BLOCKER]**

- **B1 – Vi phạm ranh giới kiến trúc**: `SearchViewModel`, `SettingsViewModel`, `SourceLoginViewModel` inject trực tiếp `SourceAuthManager` (`data/remote/auth`). Hiện không có file nào trong `ui/` import `data.remote` (grep 0 kết quả). Yêu cầu: thêm `ISourceAuthRepository` + `SourceAuthRepository` trong `data/repository/` (bind trong `di/RepositoryModule` hoặc module tương ứng), ViewModel chỉ phụ thuộc interface này; cập nhật bảng Files.

**[MAJOR]**

- **M1 – Logic phát hiện đăng nhập sai (S5-1)**: chỉ dựa vào sự tồn tại của `express.sid` và quét cả `forum.dichtienghoa.com` (NodeBB cũng dùng `express.sid`). Yêu cầu: loại domain forum khỏi phép kiểm tra; xác nhận đăng nhập bằng tín hiệu thật (ví dụ URL `onPageFinished` đã quay về `wikicv.org` sau `/auth/dth/callback` VÀ fetch một trang wikicv không còn `loggedOutMarker`, hoặc probe trang tìm kiếm không còn "Đăng nhập để xem nội dung"); `SourceAuthConfig.loggedOutMarker` phải được dùng thực sự; nêu test cho hàm thuần quyết định trạng thái.
- **M2 – Wikidich scraper chưa đặc tả đủ hợp đồng `StorySource`**: danh sách chương nạp bằng JS (`wikicv_story.html:1146,1941`) chưa có chiến lược (endpoint AJAX/ chữ ký/ phân trang, hoặc WebView); thiếu `getGenreList`, `getCompletedStories`, `getStoriesByGenre` (URL, selector, hành vi khi cần login); selector `.book-item` không có bằng chứng; thiếu ngày khảo sát HTML theo `04-scraping.md`; thiếu fixture tìm kiếm đã đăng nhập và fixture danh sách chương. Đồng thời phải xử lý rủi ro `getGenreList` lấy giao slug làm rỗng thể loại (ví dụ Wikidich trả `emptyList()` cho genre hoặc loại khỏi phép giao) và có test cho điều đó.
- **M3 – Đồng bộ trạng thái phiên**: `markLoginRequired` phải hạ `authState` về `false` (hoặc trạng thái "phiên hết hạn"); xác định nơi gọi `clearLoginRequired` (khi Wikidich trả kết quả thành công); `logout` phải xóa cookie bằng cách ghi cookie hết hạn đúng `Domain`/`Path` (`express.sid=; Max-Age=0; Path=/` cho `wikicv.org` và `.wikicv.org`) — `setCookie(domain, "")` như `WebViewCookieHandler.kt:103` không xóa được cookie; `importCookies` không ghi trùng cả host-only lẫn `.wikicv.org`.
- **M4 – `LoginRequiredException` bị nuốt ở luồng thể loại/hoàn thành**: phải sửa catch cấp trang tại `StoryRepository.kt:124` và `:181` (rethrow `LoginRequiredException` và `CancellationException`), thêm test `getStoriesByGenre` khi một nguồn ném `LoginRequiredException`. Banner cũng cần có ở Explore nếu Explore gọi nguồn Wikidich (`ExploreViewModel.kt:76,111,148`), hoặc ghi rõ lý do không làm.
- **M5 – Thiếu Test Contract/unit test cho logic mới và test hiện có sẽ gãy**: bổ sung vào bảng Files và Verification: `SearchViewModelTest.kt`, `SettingsViewModelTest.kt` (constructor đổi), test cho `SourceAuthRepository`/`SourceAuthManager` (tách phần thuần khỏi `CookieManager` bằng interface `CookieStore` để test JVM), `SourceLoginViewModelTest` (import thành công/thất bại → errorMessage tiếng Việt, logout), test `SearchViewModel` cập nhật `loginRequiredSources`. `WikidichScraperTest` phải đủ tối thiểu theo `04-scraping.md`: tìm kiếm (happy path), chi tiết, danh sách chương, nội dung chương, trang lỗi/rỗng.
- **M6 – Bảo mật/phiên đăng nhập (S5)**: (a) Bước 3 phải xóa log chuỗi cookie (`WebViewCookieHandler.kt:70`) và hàm `getCookieString()`/logic LacaTruyen chết; (b) đánh giá `allowBackup="true"`: thêm `dataExtractionRules`/`fullBackupContent` loại thư mục `app_webview` (hoặc lý do bằng văn bản); (c) `shouldOverrideUrlLoading` chỉ cho phép host `wikicv.org`, `wikicv.net`, `forum.dichtienghoa.com` (+ host OAuth bên thứ ba nếu DTH dùng, cần khảo sát), host khác mở bằng trình duyệt ngoài; xác nhận không dùng `addJavascriptInterface`.
- **M7 – DataStore `ENABLED_SOURCES`**: thêm bước chuẩn hóa: loại id không còn tồn tại và tự thêm `wikidich` cho user cũ (hoặc nêu rõ hành vi mặc định khác), có test cho hàm chuẩn hóa; cập nhật KDoc `SettingsDataStore.kt:46`.
- **M8 – Vòng đời WebView (S2)**: `AndroidView(onRelease = { it.stopLoading(); it.destroy() })` hoặc `DisposableEffect`; chỉ `loadUrl` lần đầu (giữ cờ trong ViewModel / `rememberSaveable`), xử lý xoay màn hình.
- **M9 – Quality Gate không khả thi**: ktlint baseline đang Exit 1 trên chính các file plan sửa. Plan phải ghi rõ: sửa vi phạm ktlint trong các file chạm tới (tối thiểu) và tiêu chí chấp nhận cụ thể (ví dụ `ktlintCheck` không còn vi phạm ở các file trong bảng Files), thay vì kỳ vọng toàn bộ gate pass.
- **M10 – Thông báo nguồn đã gỡ (Test Contract #7)**: chỉ rõ thay đổi trong `StoryRepository` (`sourceFor` ném lỗi riêng, ví dụ `ScraperException.ParseException("Nguồn truyện này không còn hoạt động hoặc không được hỗ trợ")`, và `getChapterContent`/`getStoryDetail`/`getFirstPageChapterList` hiển thị đúng chuỗi này), kèm test; hiện `StoryRepository.kt:84` có message lộ URL và `:437,:528` ném chuỗi khác.

**[MINOR]**

- m1: Bắt buộc cập nhật `loginRequiredSources`/`authState` bằng `MutableStateFlow.update {}`.
- m2: Làm sạch watermark `【❁ … (Wikidich) ❁】` trong nội dung chương (TTS sẽ đọc).
- m3: `tryConstructChapterUrl` (`ReaderViewModel.kt:802`) sinh URL sai cho Wikidich; nên trả `null` cho nguồn có hash trong URL.
- m4: `SourceAuthConfig.domains` trộn URL và host trần; `CookieManager.getCookie` cần URL đầy đủ — chuẩn hóa thành danh sách URL.
- m5: `sourceFor` khớp theo `baseUrl` (`wikicv.org`) sẽ không nhận link `wikicv.net`; cân nhắc khớp theo danh sách host.
- m6: Route `source_login/{sourceId}` cần xử lý `sourceId` không có cấu hình (thông báo tiếng Việt + quay lại).
- m7: `CookieManager.getInstance()` trong `SourceAuthManager` nên khởi tạo lazy để tránh khởi tạo WebView engine sớm khi graph Hilt dựng `StoryRepository`.
- m8: Bảng Verified Versions ghi "latest" Compose BOM/Jsoup không kèm bằng chứng `[URL]` ngày tra cứu; không ảnh hưởng vì không nâng version.

#### Kết luận

Plan có hướng đúng (giữ nguyên schema Room, có phương án dán cookie dự phòng, không thêm dependency), nhưng còn 1 BLOCKER về ranh giới kiến trúc và 10 MAJOR (phát hiện đăng nhập sai, scraper Wikidich chưa đặc tả danh sách chương/thể loại, exception bị nuốt ở luồng thể loại, thiếu test và test hiện có sẽ gãy, bảo mật cookie/backup/WebView, DataStore nguồn bật, vòng đời WebView, quality gate ktlint). Planner cần ra Revision 2 xử lý các mục trên.

Kết luận: [CHANGES_REQUESTED]

### Audit Run 2

- **Reviewer**: Claude Code CLI (Claude Opus 5.5, `claude-opus-5-5`)
- **Ngày**: 2026-10-13
- **Đối tượng**: Technical Plan Revision 2

#### Kiểm chứng lỗi cũ

| # | Lỗi từ Run 1 | Trạng thái | Bằng chứng / Ghi chú |
|---|---|---|---|
| B1 | ViewModel inject thẳng `SourceAuthManager` | ĐÃ KHẮC PHỤC | `ISourceAuthRepository` + `SourceAuthRepository` trong `data/repository/`, `@Binds` trong `di/RepositoryModule.kt` (file có thật, `RepositoryModule.kt:21-40` đã dùng đúng pattern `@Binds @Singleton`). Mọi ViewModel chỉ inject interface. |
| M1 | Phát hiện đăng nhập sai | CHƯA ĐẠT | Đã bỏ domain forum và thêm `verifySessionOnline`, nhưng trang probe chọn sai: xem M1' bên dưới. |
| M2 | Scraper Wikidich chưa đặc tả đủ | MỘT PHẦN | Đã có endpoint AJAX, regex `bookId/signKey`, ngày khảo sát, watermark, fixture. Nhưng thuật toán `sign` không có bằng chứng và tự mâu thuẫn; selector tìm kiếm vẫn không có HTML thật; URL `getStoriesByGenre`/`getCompletedStories`/danh sách `getGenreList` vẫn chưa đặc tả. Xem M2'. |
| M3 | Đồng bộ trạng thái phiên | ĐÃ KHẮC PHỤC (phần lớn) | `markLoginRequired` hạ `authState`; `logout` ghi `Max-Age=0` cho host và `.wikicv.org`. Nơi gọi `clearLoginRequired` khi request Wikidich thành công vẫn chưa nằm trong Bước 7 (chuyển thành m-a). |
| M4 | `LoginRequiredException` bị nuốt ở catch trang | ĐÃ KHẮC PHỤC | Bước 7 rethrow tại `StoryRepository.kt:124` và `:181` (đã đối chiếu đúng dòng), catch ngoài không gọi `recordFailure`; banner có ở cả Explore. |
| M5 | Thiếu test / test hiện có gãy | MỘT PHẦN | Đã thêm `SearchViewModelTest`, `SettingsViewModelTest`, `SourceAuth*Test`, `SourceLoginViewModelTest`, `WikidichScraperTest`. Nhưng `ExploreViewModelTest.kt` bị sót (xem M5'). |
| M6 | Bảo mật cookie / backup / WebView | MỘT PHẦN | (a) xóa log + `getCookieString()` – OK; (c) `shouldOverrideUrlLoading` + không `addJavascriptInterface` – OK. (b) Auto Backup chỉ ghi "nếu cần thiết", không có file nào trong bảng Files → xem M6'. |
| M7 | DataStore `ENABLED_SOURCES` | CHƯA ĐẠT | Có hàm `normalizeEnabledSources` + test, nhưng hàm luôn `add("wikidich")` và không nói gọi ở đâu/ghi lại DataStore → xem M7'. |
| M8 | Vòng đời WebView | ĐÃ KHẮC PHỤC | `AndroidView(onRelease = { stopLoading(); destroy() })` + `rememberSaveable` cờ load lần đầu. (Xem m-c về việc form vẫn mất khi xoay.) |
| M9 | Quality Gate ktlint | ĐÃ KHẮC PHỤC (có lưu ý) | Có tiêu chí chấp nhận theo bảng Files. Lưu ý phạm vi `ktlintFormat` ở m-d. |
| M10 | Thông báo nguồn đã gỡ | ĐÃ KHẮC PHỤC (phần lớn) | `sourceFor` ném `ParseException` tiếng Việt, test cho `getChapterContent`. Lưu ý: catch tại `StoryRepository.kt:522-534` hiện đè mọi lỗi bằng "Chương này chưa được tải…", implementer phải phân nhánh – plan đã ghi ý đồ ở Bước 7, chấp nhận. |
| m1 | `MutableStateFlow.update {}` | ĐÃ KHẮC PHỤC | Bước 2. |
| m2 | Watermark | ĐÃ KHẮC PHỤC | Regex `【❁.*\(Wikidich\).*❁】`, test ở WikidichScraperTest #2. |
| m3 | `tryConstructChapterUrl` | ĐÃ KHẮC PHỤC (ý đồ) | Bước 9; chưa có test (xem m-e). |
| m4, m5, m6, m7 | Domains URL, `wikicv.net`, guard route, lazy CookieManager | ĐÃ KHẮC PHỤC | Bước 2, 6, 7. |
| m8 | Versions không có URL | ĐÃ KHẮC PHỤC | Bảng có URL + ngày tra cứu. |

#### Mental Simulation Matrix (S1–S6 + Domain)

| Kịch bản | Kết quả | Bằng chứng | Chi tiết |
|---|---|---|---|
| S1 Cold-start & Build | FAIL | `[CMD]` compileDebugKotlin Exit 0; `[SRC]` `ExploreViewModelTest.kt:47,63,78,94,106`; `[SRC]` `ExploreViewModel.kt:21-25` | Baseline biên dịch sạch, không thêm dependency, Hilt binding đầy đủ (`RepositoryModule`, `ScraperModule`, `AppModule`). Nhưng plan sửa `ExploreViewModel` để lắng nghe `loginRequiredSources` (Bước 7, mục B1 trong Goal liệt kê `ExploreViewModel` inject `ISourceAuthRepository`) trong khi `ExploreViewModelTest.kt` gọi `ExploreViewModel(repository)` theo vị trí ở 5 chỗ và KHÔNG có trong bảng Files → `compileDebugUnitTestKotlin` sẽ gãy. |
| S2 Lifecycle & Teardown | PASS | `[SRC]` plan Bước 6; `[ADV]` timeline L2 | `onRelease` hủy WebView, `rememberSaveable` chặn `loadUrl` lặp. Repository dùng `Dispatchers.IO`. Không có `GlobalScope`. |
| S3 Database & DataStore | FAIL (DataStore) | `[SRC]` `SettingsViewModel.kt:119-124,208-218`; `[SRC]` `StoryRepository.kt:64-76`; `[ADV]` timeline D1, D2 | Room không đổi schema → an toàn. DataStore: `normalizeEnabledSources` luôn thêm `wikidich` ⇒ nếu áp dụng mỗi lần đọc `state` thì user KHÔNG THỂ tắt Wikidich (D1); nếu chỉ áp dụng trong ViewModel mà không ghi lại DataStore thì `StoryRepository.activeSources()` (đọc key thô) không bật Wikidich cho user cũ (D2). Plan không nói hàm được gọi ở đâu và có persist hay không. |
| S4 Cross-Task State Flow | PASS (có lưu ý) | `[SRC]` `StoryRepository.kt:104-207`; `RepositoryModule.kt:21-40` | Luồng Screen → VM → `ISourceAuthRepository` → `SourceAuthManager` đúng ranh giới. Rethrow ở catch trang hợp lệ: các `async` trang là con của scope `withTimeoutOrNull` (không phải supervisor) nên exception lan lên scope đó và bị catch ngoài (`:135`, `:191`) bắt → không làm hỏng các nguồn khác trong `supervisorScope`. Lưu ý chỗ gọi `clearLoginRequired` (m-a). |
| S5 Security & Privacy | FAIL | `[SRC]` `AndroidManifest.xml:13` (`allowBackup="true"`, không `dataExtractionRules`); `[SRC]` grep marker `Đăng nhập để xem nội dung` chỉ có trong `build/wk_search.html:264`, KHÔNG có trong `build/wikicv_home.html`; `[ADV]` S5-1', S5-2 | (a) Auto Backup: Risk Table dòng "Rò rỉ cookie session… Auto Backup" (Trung bình) khẳng định control "Cấu hình loại trừ thư mục cookie WebView khỏi backup" nhưng không có bước/không có file `AndroidManifest.xml` hay `res/xml/*backup_rules*.xml` trong bảng Files → theo 4.5.4 là rủi ro Trung bình không có control → MAJOR. (b) `verifySessionOnline` probe "`/chuong-moi` hoặc trang chủ": trang chủ ở trạng thái chưa đăng nhập KHÔNG chứa marker → probe luôn "thành công" → phát hiện đăng nhập/nhập cookie giả vẫn PASS (S5-1'). `/chuong-moi` chưa được khảo sát. Đã xóa log cookie, có `shouldOverrideUrlLoading`, không `addJavascriptInterface` → OK. |
| S6 External Resilience | FAIL | `[SRC]` `build/wikicv_story.html:1934-1941`; `[SRC]` `build/wikicv_story.html:285` (`.book-info` là khối chi tiết truyện, không phải kết quả tìm kiếm); `[SRC]` không có file `get-book-0.2.6.min.js` trong `build/` | (a) Chữ ký AJAX: Mục 3 ghi `sign = callSignFunc(fuzzySign(...))` nhưng code Bước 5 chỉ có `fuzzySign` (thiếu `callSignFunc` – nhiều khả năng là hàm băm trong `get-book-0.2.6.min.js`); script này không được khảo sát (không có trong `build/`), nhánh `raw.length / 2` là tự bịa; trang thật gọi `loadBookIndex(0, 99, false)` còn plan dùng `size=100` → ngữ nghĩa `start/size` chưa xác minh. Risk Table khẳng định "đối chiếu trực tiếp từ mã nguồn script get-book.js" nhưng không có bằng chứng → danh sách chương (chức năng cốt lõi) có nguy cơ 100% hỏng. (b) Selector tìm kiếm `.book-info` / `.flexbox .book-info` chỉ xuất hiện ở trang CHI TIẾT truyện (`wikicv_story.html:283-285`); fixture `search_logged_in.html` sẽ do planner tự dựng → test vòng tròn (fixture được nặn theo parser). (c) Endpoint `/book/index` trả HTML fragment hay JSON: UNVERIFIED (host `wikicv.org` ngoài allowlist curl). |
| Domain | FAIL (một phần) | `[SRC]` `StoryRepository.kt:211-259`; plan Bước 5, 7 | (a) `getGenreList` Wikidich "trả về danh sách thể loại đã khảo sát với slug chuẩn hóa" nhưng không liệt kê slug ↔ URL (`/tim-kiem?tag=<ObjectId>` theo Run 1) và `getStoriesByGenre`/`getCompletedStories` Wikidich vẫn không có URL pattern. Fallback "lấy nguồn có nhiều thể loại nhất" khi giao rỗng sẽ truyền slug của nguồn đó cho mọi nguồn khác (`StoryRepository.kt:180` dùng chung `genreSlug`) → các nguồn còn lại ném 404/ParseException → rỗng. Cần đặc tả ánh xạ slug. (b) Watermark, dedup, offline nguồn cũ → OK. |

#### Adversarial Timelines

```
Cơ chế: Xác minh phiên online (verifySessionOnline) — S5-1'
T1: User dán chuỗi cookie rác "abcdefghijklmn" (>10 ký tự) | T2: importCookies ghi express.sid, gọi verifySessionOnline → GET trang chủ wikicv.org | T3: HTML trang chủ khi chưa đăng nhập không chứa "Đăng nhập để xem nội dung" (chỉ trang /tim-kiem có, build/wk_search.html:264; build/wikicv_home.html không có)
Kết cục: importCookies trả true, Settings "Đã đăng nhập", search vẫn ném LoginRequiredException → LỖI (MAJOR). Tương tự cho onLoginDetected sau khi user chỉ mở /auth/dth rồi quay lại.
```

```
Cơ chế: Cookie hết hạn giữa chừng (TOCTOU) — S5-2
T1: Settings hiển thị "Đã đăng nhập" | T2: Server hủy session | T3: search Wikidich → LoginRequiredException → catch ngoài → authRepository.markLoginRequired → _authState=false, set += "wikidich"
Kết cục: Settings chuyển "Chưa đăng nhập", banner hiện ở Search/Explore → AN TOÀN (M3 đã xử lý).
```

```
Cơ chế: Chuẩn hóa ENABLED_SOURCES — D1 (áp dụng khi đọc)
T1: User vào Cài đặt tắt Wikidich → toggleSource ghi "tangthuvien,sstruyen" | T2: dataStore re-emit → state map gọi normalizeEnabledSources → valid.add("wikidich") | T3: UI hiện Wikidich vẫn bật
Kết cục: Không thể tắt Wikidich; công tắc "nảy" lại → LỖI (MAJOR)
```

```
Cơ chế: Chuẩn hóa ENABLED_SOURCES — D2 (chỉ áp dụng trong ViewModel, không persist)
T1: User cũ có "truyencom,tangthuvien,sstruyen,truyenfull" | T2: Không mở Cài đặt | T3: StoryRepository.activeSources() (StoryRepository.kt:64-76) đọc key thô → lọc theo sources → {tangthuvien, sstruyen}
Kết cục: Wikidich không bao giờ chạy cho user cũ dù UI Cài đặt hiện "bật" → LỖI (MAJOR). Cần: chuẩn hóa một lần + ghi lại DataStore (cờ migration), hoặc dùng chung hàm thuần ở cả Repository và ViewModel nhưng KHÔNG ép thêm wikidich khi user đã chủ động tắt.
```

```
Cơ chế: Ghi đồng thời loginRequiredSources (trục Đồng thời)
T1: async search Wikidich markLoginRequired | T2: async getStoriesByGenre markLoginRequired; SourceLoginViewModel gọi clearLoginRequired | T3: _loginRequiredSources.update { … } (CAS)
Kết cục: Không mất cập nhật → AN TOÀN.
```

```
Cơ chế: Thất bại giữa chừng ở luồng thể loại khi 1 trang ném LoginRequired
T1: Wikidich trang p=1 ném LoginRequiredException (rethrow tại catch trang) | T2: các async trang p=2,3 cùng scope withTimeoutOrNull bị hủy | T3: catch ngoài bắt, markLoginRequired, emptyList; nguồn TTV/SST trong supervisorScope không bị ảnh hưởng
Kết cục: AN TOÀN; CancellationException của trang anh em không lọt ra ngoài vì scope đã fail với LoginRequiredException.
```

```
Cơ chế: Vòng đời WebView (trục Vòng đời Android) — L2
T1: User đang nhập form DTH | T2: Xoay màn hình → Activity tái tạo → onRelease destroy WebView cũ, factory tạo WebView mới; rememberSaveable isLoaded=true nên không loadUrl | T3: WebView mới trống (không có state trang)
Kết cục: Không rò rỉ (AN TOÀN về bộ nhớ) nhưng màn hình trắng, kỳ vọng Manual Test "không mất dữ liệu đang nhập" sẽ FAIL → MINOR (m-c): cần saveState/restoreState hoặc load lại URL hiện tại.
```

Trục TOCTOU kiểm chương offline: N/A (logic offline-first `StoryRepository.kt:498-501` không đổi).

#### Commands Executed

Trích từ `evidence.md` (reviewer không chạy lệnh):

- `### [CMD] cmd.exe /d /c gradlew.bat compileDebugKotlin` — `Exit: 0` — "BUILD SUCCESSFUL in 7s".
- `### [CMD] cmd.exe /d /c gradlew.bat ktlintCheck` — `Exit: 1` — "Task :app:ktlintMainSourceSetCheck FAILED"; vi phạm ngoài phạm vi plan ở `TangThuVienScraper.kt` (3:1 … 526:1) và trong phạm vi ở `StoryRepository.kt:220:40,238:1,241:1`, `ScraperModule.kt:36:1`, `AppNavigation.kt:86:1,332:1`, `ReaderViewModel.kt:428:33`, `SettingsScreen.kt:46:1,394:1`, `SettingsViewModel.kt:103:27,129:1`.
- `### [CMD] cmd.exe /d /c gradlew.bat testDebugUnitTest` — `Exit: 0` — "BUILD SUCCESSFUL in 1s".
- `### [CMD] git status --short` — `Exit: 0` — các file plan sửa đã có thay đổi chưa commit (`StoryRepository.kt`, `SettingsViewModel.kt`, `AppModule.kt`, `ScraperModule.kt`…); `data/remote/cookie/` chưa track.
- `### [CMD] node --test .agents/scripts/code-freeze-guard.test.js` — `Exit: 0` — "pass 31, fail 0".

UNVERIFIED (đề xuất User tự chạy / cung cấp HTML): tải `https://wikicv.org/static/js/wiki/get-book-0.2.6.min.js` để xác định `callSignFunc` và tham số `start/size`; một lần gọi `/book/index` thật (lưu response vào `build/`); HTML `/tim-kiem?qs=1&q=...` khi ĐÃ đăng nhập; HTML `/chuong-moi` khi chưa đăng nhập. (Host `wikicv.org` không có trong allowlist curl/WebFetch của reviewer.)

#### Phân loại lỗi

**[BLOCKER]**

- Không còn.

**[MAJOR]**

- **M1' – Probe xác minh phiên không đặc hiệu (S5-1')**: `verifySessionOnline` phải gọi trang ĐÃ được chứng minh có marker khi chưa đăng nhập — hiện chỉ `/tim-kiem?qs=1&q=<từ khóa>` (`build/wk_search.html:264`). Bỏ "hoặc trang chủ"; nếu dùng `/chuong-moi` thì phải có HTML khảo sát kèm ngày. Bổ sung test: HTML không marker + có danh sách → `true`; HTML có marker → `false`; lỗi mạng → `false` + thông báo tiếng Việt, không mark đăng nhập.
- **M2' – Chữ ký AJAX `/book/index` và selector tìm kiếm chưa có bằng chứng (S6)**: (a) khảo sát `get-book-0.2.6.min.js` (`wikicv_story.html:1934`), ghi đúng công thức `sign` (bao gồm `callSignFunc` nếu có), ngữ nghĩa `start/size` (trang thật dùng `0, 99`), định dạng response (HTML/JSON) và lưu một response thật làm fixture `chapter_index_ajax.html`; bỏ nhánh `raw.length / 2` tự đặt (hoặc ném `ParseException` khi `signKey` bất thường); sửa mâu thuẫn giữa Mục 3 và Bước 5. (b) Fixture `search_logged_in.html` phải là HTML thật đã đăng nhập (ghi ngày khảo sát), selector kết quả tìm kiếm phải trích từ đó — `.book-info` hiện chỉ là khối chi tiết truyện. (c) Đặc tả URL `getStoriesByGenre`/`getCompletedStories` của Wikidich và bảng slug thể loại ↔ tham số URL; mô tả lại fallback `getGenreList` sao cho slug truyền xuống từng nguồn vẫn hợp lệ (ví dụ chỉ giao giữa các nguồn có slug tương thích, hoặc mỗi nguồn tự bỏ qua slug lạ trả `emptyList()` thay vì lỗi), kèm test.
- **M5' – `ExploreViewModelTest.kt` sẽ không biên dịch**: thêm vào bảng Files + Bước 7: cập nhật 5 lời gọi constructor (`ExploreViewModelTest.kt:47,63,78,94,106`) và thêm test banner khi `loginRequiredSources` chứa `"wikidich"`.
- **M6' – Auto Backup chưa có control (Risk Table 4.5.4)**: thêm `[MODIFY] app/src/main/AndroidManifest.xml` + `[NEW] app/src/main/res/xml/data_extraction_rules.xml` (API 31+) và `backup_rules.xml` (`fullBackupContent`, API ≤ 30) loại trừ thư mục WebView (`app_webview`, domain `root`) — hoặc ghi lý do bằng văn bản vì sao chấp nhận rủi ro, và sửa dòng Risk Table cho khớp.
- **M7' – `normalizeEnabledSources` phá quyền tắt nguồn và không đến được Repository (D1, D2)**: chỉ thêm `wikidich` MỘT LẦN cho user cũ (cờ migration trong DataStore, ví dụ key mới có default) và ghi lại `ENABLED_SOURCES`; sau đó tôn trọng lựa chọn tắt của user. Nêu rõ điểm gọi (khởi động app/Repository) để `StoryRepository.activeSources()` thấy giá trị đã chuẩn hóa; test: user cũ → có wikidich; user tắt wikidich → giữ tắt; set chỉ chứa id chết → `ALL_SOURCES`.

**[MINOR]**

- m-a: Bước 7 nên ghi rõ gọi `authRepository.clearLoginRequired(source.sourceId)` khi nguồn trả kết quả thành công (cạnh `recordSuccess`), không chỉ ở Goal.
- m-b: Goal 5 viết "tự động bổ sung `wikicv`" trong khi `sourceId` là `"wikidich"` ở mọi chỗ khác — thống nhất `wikidich`.
- m-c: `rememberSaveable` + `onRelease` chặn rò rỉ nhưng WebView mới sau xoay sẽ trống; dùng `webView.saveState/restoreState` (Bundle trong `rememberSaveable`) hoặc load lại URL hiện tại, và chỉnh kỳ vọng Manual Test.
- m-d: `.\gradlew.bat ktlintFormat` sẽ format cả `TangThuVienScraper.kt` (ngoài phạm vi, ~60 vi phạm) → diff lan. Ghi rõ chấp nhận hoặc giới hạn phạm vi.
- m-e: `tryConstructChapterUrl` là logic ViewModel → nên có test (hàm thuần/`internal`) theo `01-coding.md`.
- m-f: `setAcceptThirdPartyCookies(webView, true)` không cần cho redirect top-level (cookie first-party) và mở rộng theo dõi bởi script quảng cáo (`wikicv_story.html:53`); cân nhắc bỏ hoặc nêu bằng chứng cần thiết. Nếu DTH dùng OAuth Google (`apis.google.com`, `wikicv_story.html:1985`), allowlist `shouldOverrideUrlLoading` có thể chặn luồng — kiểm thử thủ công.

#### Kết luận

Revision 2 khắc phục BLOCKER B1 và phần lớn M3, M4, M8, M9, M10 cùng toàn bộ MINOR của Run 1. Còn 5 MAJOR: probe xác minh phiên không đặc hiệu (M1'), chữ ký AJAX/selector tìm kiếm/URL thể loại Wikidich chưa có bằng chứng (M2'), `ExploreViewModelTest` sẽ gãy (M5'), Auto Backup chưa có control (M6'), chuẩn hóa `ENABLED_SOURCES` sai ngữ nghĩa (M7'). Planner cần ra Revision 3; M2' cần User cung cấp HTML/JS khảo sát từ `wikicv.org` vì host này ngoài allowlist của reviewer.

Kết luận: [CHANGES_REQUESTED]

### Audit Run 3

- **Reviewer**: Claude Code CLI (Claude Opus 5.5, `claude-opus-5-5`)
- **Ngày**: 2026-10-13
- **Đối tượng**: Technical Plan Revision 3

#### Kiểm chứng lỗi cũ

| # | Lỗi từ Run 2 | Trạng thái | Bằng chứng / Ghi chú |
|---|---|---|---|
| M1' | Probe xác minh phiên không đặc hiệu | ĐÃ KHẮC PHỤC (phần probe) | Chỉ dùng `/tim-kiem?qs=1&q=tien`, marker có thật ở `build/wk_search.html:264`; bỏ "hoặc trang chủ"; có test lỗi mạng. Tuy nhiên kênh báo lỗi mạng ra UI chưa khả thi, xem N3. |
| M2'(a) | Công thức chữ ký AJAX | ĐÃ KHẮC PHỤC (công thức) / CHƯA ĐẠT (fixture + phân trang) | `[SRC]` `build/get-book.js` (dòng 1, minified): `loadBookIndex=function(f,e,d){var c="/book/index";var b=signFunc(fuzzySign(signKey+f+e));$.ajax({type:"GET",url:c,data:{bookId:bookId,start:f,size:e,signKey:signKey,sign:b},success:function(g){$(".volume-list").html(g)…` và `var signFunc=function a(W){function V(d,c){return d>>>c\|d<<32-c}…` (SHA-256 thuần JS) → công thức, tham số và định dạng HTML khớp plan. `[SRC]` `build/wikicv_story.html:1937-1941`: `fuzzySign` = `text.substring(87) + text.substring(0, 87)`, `loadBookIndex(0, 99, false)` → regex trích `cut` đúng. NHƯNG `build/real_book_index.html` và `build/book_index_sample.html` đều RỖNG (0 byte) → fixture `chapter_index_ajax.html` vẫn do planner tự dựng; cách lấy trang tiếp theo (`actions.loadBookIndex` đọc `data-start`/`data-size` từ chính fragment trả về) không được đặc tả. Xem N1. |
| M2'(b) | Selector tìm kiếm không có bằng chứng | CHƯA ĐẠT | Plan vẫn dùng `.book-info` / `.flexbox .book-info`, chỉ xuất hiện ở khối chi tiết truyện (`build/wikicv_story.html:283-285`). Khối danh sách thật trên trang chủ là `.book-item` (`build/wikicv_home.html:458-481`: `.info-col a[href^=/truyen/]`, `h5.book-title`, `p.book-author`). Vẫn không có HTML tìm kiếm đã đăng nhập. Xem N2. |
| M2'(c) | URL thể loại/hoàn thành + bảng slug | CHƯA ĐẠT (sai dữ liệu) | `getCompletedStories` dùng `ending=5794f03dd7ced228f441919f` nhưng `build/wikicv_story.html:325` cho thấy id này là nhãn **"HE"** (happy ending), không phải trạng thái hoàn thành. Trạng thái "Hoàn thành" thật là `status=5794f03dd7ced228f4419191` (`build/wikicv_home.html:502`). Bảng ánh xạ slug tiếng Việt ↔ ObjectId vẫn không có, trong khi Repository truyền `genreSlug` dạng slug (`StoryRepository.kt:180`) và phân loại Wikidich trải trên nhiều tham số (`genre`, `tag`, `gender`, `official`, `age`, `ending` — `wikicv_story.html:317-338`). Xem N2. |
| M5' | `ExploreViewModelTest` gãy | ĐÃ KHẮC PHỤC | Bảng Files + Bước 7 cập nhật 5 lời gọi constructor, thêm test banner. |
| M6' | Auto Backup chưa có control | CHƯA ĐẠT (control sai) | Đã thêm file và Manifest, nhưng XML không có hiệu lực: xem N4. |
| M7' | Chuẩn hóa `ENABLED_SOURCES` | MỘT PHẦN | Có cờ `IS_WIKIDICH_MIGRATED`, không tự bật lại → đúng ngữ nghĩa. Nhưng điểm gọi vẫn chỉ trong `SettingsViewModel` (Run 2 đã yêu cầu nêu rõ khởi động app/Repository), API dùng trong code mẫu không tồn tại. Xem N5. |
| m-a, m-b, m-e, m-f | `clearLoginRequired`, thống nhất `wikidich`, test `tryConstructChapterUrl`, bỏ third-party cookies | ĐÃ KHẮC PHỤC | Bước 7, Goal 5, Bước 9, Bước 6. |
| m-c | WebView trắng khi xoay | MỘT PHẦN | Có `saveState`/`restoreState`, nhưng kỳ vọng "giữ nguyên dữ liệu đang nhập" không đúng với API (xem m-g). |
| m-d | `ktlintFormat` lan file | CHƯA ĐẠT | Bước 10 và Verification §2 vẫn chạy `.\gradlew.bat ktlintFormat` toàn module (sẽ format `TangThuVienScraper.kt` — ~60 vi phạm trong evidence) trong khi câu chữ nói "chỉ file trong phạm vi". Mâu thuẫn, giữ MINOR. |

#### Mental Simulation Matrix (S1–S6 + Domain)

| Kịch bản | Kết quả | Bằng chứng | Chi tiết |
|---|---|---|---|
| S1 Cold-start & Build | FAIL | `[CMD]` compileDebugKotlin Exit 0; `[SRC]` `data/local/SettingsDataStore.kt:14-57`; `[SRC]` `ui/settings/SettingsViewModel.kt:68-73` | Baseline biên dịch sạch, không thêm dependency, Hilt binding mới có chỗ đăng ký. Nhưng code mẫu Bước 8 gọi `dataStore.isWikidichMigrated`, `dataStore.enabledSources`, `dataStore.setEnabledSources()`, `dataStore.setWikidichMigrated()` và "Inject `SettingsDataStore`" — `SettingsDataStore.kt` chỉ là `object SettingsKeys` + extension `Context.settingsDataStore`, KHÔNG có class injectable hay các API trên; `SettingsViewModel` đang inject `DataStore<Preferences>`. Plan không đặt file này ở dạng tạo class mới → code mẫu không biên dịch nếu làm đúng chữ. |
| S2 Lifecycle & Teardown | PASS (có lưu ý) | `[SRC]` plan Bước 6; `[ADV]` L3 | `onRelease` gọi `stopLoading()`/`destroy()`, không `GlobalScope`, Repository dùng `Dispatchers.IO`. Lưu ý m-g về `saveState`. |
| S3 Database & DataStore | FAIL (DataStore) | `[SRC]` `StoryRepository.kt:64-76`; `[SRC]` `SettingsViewModel.kt:102-127,208-218`; `[ADV]` D2', D3 | Room không đổi schema → an toàn. DataStore: migration chỉ nằm trong `SettingsViewModel` → user cũ không mở Cài đặt thì `activeSources()` vẫn đọc key thô `truyencom,tangthuvien,sstruyen,truyenfull` → không bao giờ chạy Wikidich (D2'). Migration đọc rồi ghi bằng 2 lần edit riêng, không nguyên tử (D3). |
| S4 Cross-Task State Flow | FAIL | `[SRC]` plan Bước 3 (`suspend fun importCookies(...): Boolean`), Bước 2 (`verifySessionOnline` trả `false` cho cả phiên hỏng lẫn lỗi mạng), Test Contract #3; `[ADV]` A1 | Kiến trúc Screen → VM → `ISourceAuthRepository` → `SourceAuthManager` đúng ranh giới. Nhưng hợp đồng interface trả `Boolean` nên `SourceLoginViewModel` không phân biệt được "cookie hỏng" và "mất mạng" → Test Contract #3 (2 thông báo khác nhau) không thể hiện thực/test được; "emit/ghi nhận thông báo" trong Manager không có kênh nào trong `ISourceAuthRepository`. |
| S5 Security & Privacy | FAIL | `[URL]` developer.android.com/identity/data/autobackup (tra 2026-10-13); `[SRC]` `AndroidManifest.xml:13` (`allowBackup="true"`); `[SRC]` `cookie/WebViewCookieHandler.kt:24-43` (CookieJar ghi/đọc `CookieManager`); `[ADV]` S5-3, A1 | (a) Theo tài liệu chính thức, thuộc tính `domain` của `<include>/<exclude>` là **bắt buộc**, và `database` ánh xạ tới `getDatabasePath()` (thư mục `databases/`), `root` mới là thư mục dữ liệu riêng của app. `data_extraction_rules.xml` trong plan thiếu `domain`; `backup_rules.xml` dùng `domain="database" path="app_webview/Cookies"` → trỏ tới `databases/app_webview/Cookies` (không tồn tại) → cookie `express.sid` vẫn đi vào Cloud Backup/device-transfer. Risk Table (Trung bình) có control không hiệu lực → MAJOR. (b) Ghi cookie trước rồi mới verify trong `importCookies`; khi verify `false` không có rollback → cookie rác đè phiên hợp lệ (A1). (c) Đã xóa log cookie, có `shouldOverrideUrlLoading`, không `addJavascriptInterface` → OK. |
| S6 External Resilience | FAIL | `[SRC]` `build/get-book.js:1`; `build/real_book_index.html` (0 byte); `build/wikicv_story.html:285,302,325`; `build/wikicv_home.html:458,502` | Công thức chữ ký đã có bằng chứng. Còn: (a) không có response `/book/index` thật, cơ chế phân trang (`data-start`/`data-size` trong fragment) chưa đặc tả → `getRemainingChapters` không xác định được điểm dừng; (b) selector tìm kiếm `.book-info` sai khối; (c) `getCompletedStories` sẽ trả truyện có nhãn HE thay vì truyện hoàn thành. |
| Domain | FAIL | `[SRC]` `StoryRepository.kt:155-198,211-259`; `wikicv_story.html:317-338` | Bỏ phép giao sang union là hợp lý (nguồn TTV/SST nhận slug lạ sẽ lỗi 404/rỗng, bị catch trang nuốt; không gọi `recordFailure` ở luồng thể loại → không mở mạch oan). Nhưng Wikidich `getGenreList` "slug tiếng Việt chuẩn hóa" không có bảng ánh xạ sang ObjectId và loại tham số (`genre` vs `tag`), nên `getStoriesByGenre("xuyen-khong")` không có cách dựng URL đúng. Watermark, offline nguồn cũ, `tryConstructChapterUrl` → OK. |

#### Adversarial Timelines

```
Cơ chế: Migration ENABLED_SOURCES chỉ chạy trong SettingsViewModel — D2'
T1: User cũ (ENABLED_SOURCES = "truyencom,tangthuvien,sstruyen,truyenfull") cập nhật app | T2: Mở Search/Explore, không vào Cài đặt | T3: StoryRepository.activeSources() (StoryRepository.kt:64-76) đọc key thô, lọc theo Set<StorySource> → {tangthuvien, sstruyen}
Kết cục: Wikidich không bao giờ được truy vấn cho tới khi user mở màn Cài đặt; cờ migration chưa set → LỖI (MAJOR). Cần gọi migration ở điểm khởi động (Application/MainActivity qua repository, hoặc ngay trong activeSources()).
```

```
Cơ chế: Migration không nguyên tử (trục Đồng thời) — D3
T1: SettingsViewModel khởi chạy migrateEnabledSourcesIfNeeded(), đọc enabledSources = {tangthuvien, sstruyen} | T2: User bấm tắt sstruyen ngay lúc đó → toggleSource ghi "tangthuvien" (SettingsViewModel.kt:210-217) | T3: migration ghi {tangthuvien, sstruyen, wikidich} rồi set cờ
Kết cục: Lựa chọn tắt sstruyen bị ghi đè → LỖI (thấp, gộp MAJOR N5). Cần làm toàn bộ check-flag + sửa set + set cờ trong MỘT dataStore.edit { }.
```

```
Cơ chế: Dán cookie khi đang đăng nhập hợp lệ — A1 (TOCTOU + thất bại giữa chừng)
T1: User đã đăng nhập (express.sid hợp lệ) | T2: Dán nhầm chuỗi rác → importCookies ghi express.sid=rác + flush → verifySessionOnline gặp marker → false | T3: Không rollback; Boolean false → VM báo "Chuỗi cookie không hợp lệ…"
Kết cục: Phiên cũ bị phá, authState có thể vẫn true (plan không quy định hạ) → lần tìm kiếm sau ném LoginRequired → LỖI (MAJOR). Biến thể mạng: T2 timeout → false → VM KHÔNG biết là lỗi mạng → hiện sai thông báo, trái Test Contract #3.
```

```
Cơ chế: Phân trang danh sách chương Wikidich — C1 (trục Thất bại giữa chừng)
T1: getFirstPageChapters gọi /book/index start=0 size=99 | T2: Truyện có 1.200 chương; fragment chứa nút trang tiếp với data-start/data-size (get-book.js: actions.loadBookIndex đọc d.data("start")) | T3: Plan không đặc tả cách đọc tổng số/trang kế, fixture tự dựng không có phần phân trang
Kết cục: Hoặc chỉ có 99 chương đầu, hoặc lặp vô hạn/đoán start sai → LỖI (MAJOR).
```

```
Cơ chế: Cookie backup — S5-3 (trục Vòng đời Android)
T1: User đăng nhập Wikidich, express.sid lưu trong CookieManager | T2: Android chạy Auto Backup / chuyển thiết bị; exclude domain="database" trỏ databases/app_webview/Cookies (không tồn tại), data_extraction_rules thiếu domain | T3: Thư mục app_webview vẫn được sao lưu
Kết cục: Phiên đăng nhập rời thiết bị qua Google Drive → LỖI (MAJOR, Risk Table control không hiệu lực).
```

```
Cơ chế: Ghi đồng thời loginRequiredSources (trục Đồng thời)
T1: async search Wikidich markLoginRequired | T2: async getStoriesByGenre markLoginRequired; SourceLoginViewModel clearLoginRequired | T3: MutableStateFlow.update { } (CAS)
Kết cục: Không mất cập nhật → AN TOÀN.
```

```
Cơ chế: Vòng đời WebView khi xoay màn hình — L3
T1: User đang nhập form DTH | T2: Xoay → onSaveInstanceState → rememberSaveable lưu Bundle; sau đó onRelease saveState vào cùng Bundle | T3: Activity mới restoreState → nạp lại URL lịch sử
Kết cục: Không rò rỉ → AN TOÀN về bộ nhớ; WebView.saveState không lưu dữ liệu form đã nhập, và `update` chỉ chạy khi recompose → kỳ vọng Manual Test "giữ nguyên dữ liệu đang nhập" không đạt → MINOR (m-g).
```

Trục TOCTOU kiểm chương offline: N/A (logic offline-first `StoryRepository.kt:498-501` không đổi).

#### Commands Executed

Trích từ `evidence.md` (reviewer không chạy lệnh):

- `### [CMD] cmd.exe /d /c gradlew.bat compileDebugKotlin` — `Exit: 0` — "BUILD SUCCESSFUL in 7s".
- `### [CMD] cmd.exe /d /c gradlew.bat ktlintCheck` — `Exit: 1` — "Task :app:ktlintMainSourceSetCheck FAILED"; vi phạm ngoài phạm vi ở `TangThuVienScraper.kt` (3:1 … 526:1), `StorySource.kt:47:87`; trong phạm vi ở `StoryRepository.kt:220:40,238:1,241:1`, `ScraperModule.kt:36:1`, `AppNavigation.kt:86:1,332:1`, `ReaderViewModel.kt:428:33`, `SettingsScreen.kt:46:1,394:1`, `SettingsViewModel.kt:103:27,129:1`.
- `### [CMD] cmd.exe /d /c gradlew.bat testDebugUnitTest` — `Exit: 0` — "BUILD SUCCESSFUL in 1s".
- `### [CMD] git status --short` — `Exit: 0` — các file plan sửa đã có thay đổi chưa commit; `data/remote/cookie/` chưa track.
- `### [CMD] node --test .agents/scripts/code-freeze-guard.test.js` — `Exit: 0` — "pass 31, fail 0".
- `### [CMD] node --test .agents/scripts/plan-approval.test.js` — `Exit: 0` — "pass 12, fail 0".
- `### [CMD] node --test .agents/scripts/review-run.test.js` — `Exit: 0` — "pass 10, fail 0".

UNVERIFIED (host `wikicv.org` ngoài allowlist của reviewer, đề xuất User cung cấp): một response thật của `GET https://wikicv.org/book/index?bookId=…&start=0&size=99&signKey=…&sign=…` (lưu vào `build/real_book_index.html` — file hiện 0 byte) và một response trang thứ hai; HTML `/tim-kiem?qs=1&q=<từ khóa>` khi ĐÃ đăng nhập; vị trí file cookie WebView trên thiết bị thật (`app_webview/Cookies` hay `app_webview/Default/Cookies`, kiểm bằng `adb shell run-as com.personal.apptruyen ls -R app_webview` — lệnh ngoài allowlist, User tự chạy).

#### Phân loại lỗi

**[BLOCKER]**

- Không có.

**[MAJOR]**

- **N1 – Danh sách chương Wikidich: thiếu response thật và đặc tả phân trang (S6, tiếp M2'(a))**: công thức chữ ký đã đúng (`build/get-book.js`, `wikicv_story.html:1937-1941`), nhưng `build/real_book_index.html` rỗng nên fixture `chapter_index_ajax.html` vẫn tự dựng (test vòng tròn). Yêu cầu: lưu response thật (trang đầu + một trang sau) làm fixture, ghi ngày khảo sát; đặc tả cách `getRemainingChapters` xác định trang kế/điểm dừng (đọc `data-start`/`data-size` hoặc tổng số chương) và điều kiện ném `ParseException` khi fragment rỗng/không có `a[href*=/chuong-]`; test cho phân trang.
- **N2 – Dữ liệu tìm kiếm/duyệt Wikidich sai hoặc không có bằng chứng (S6/Domain, tiếp M2'(b)(c))**: (a) `getCompletedStories` phải dùng `status=5794f03dd7ced228f4419191` ("Hoàn thành", `wikicv_home.html:502`), không phải `ending=…919f` ("HE", `wikicv_story.html:325`); (b) selector kết quả tìm kiếm phải trích từ HTML thật — `.book-info` là khối chi tiết; khối danh sách đã thấy là `.book-item` (`wikicv_home.html:458-481`), cần HTML `/tim-kiem` đã đăng nhập để xác nhận và làm fixture `search_logged_in.html`; (c) liệt kê bảng ánh xạ slug thể loại ↔ (tên tham số, ObjectId) cho Wikidich và hành vi khi slug không có trong bảng (`emptyList()`), kèm test.
- **N3 – Hợp đồng `importCookies`/xác minh phiên không đủ để hiện thực Test Contract #3 (S4/S5)**: đổi kiểu trả về thành kết quả có phân loại (ví dụ `sealed interface SessionVerifyResult { Valid; Invalid; NetworkError }`) xuyên suốt `SourceAuthManager` → `ISourceAuthRepository` → `SourceLoginViewModel`; quy định rollback cookie cũ (hoặc chỉ ghi sau khi verify bằng request kèm header `Cookie` tạm) khi kết quả là `Invalid`/`NetworkError`; quy định `authState` sau mỗi nhánh; test cho cả 3 nhánh và nhánh rollback.
- **N4 – Cấu hình loại trừ cookie khỏi backup không có hiệu lực (S5, tiếp M6')**: thêm `domain="root"` cho mọi `<exclude>` trong `data_extraction_rules.xml`; trong `backup_rules.xml` đổi `domain="database"` thành `domain="root"`; nên loại trừ cả thư mục `app_webview/` (đường dẫn file cookie thay đổi theo phiên bản WebView) thay vì từng file. Bổ sung bước Verification kiểm chứng được (ví dụ `adb shell bmgr backupnow` + kiểm danh sách file, hoặc ít nhất Android Lint không báo lỗi XML backup) và sửa dòng Risk Table cho khớp.
- **N5 – Migration `ENABLED_SOURCES` không đến được Repository và code mẫu dùng API không tồn tại (S1/S3, tiếp M7')**: (a) `SettingsDataStore.kt` chỉ có `object SettingsKeys`; plan phải dùng `DataStore<Preferences>` sẵn có (hoặc ghi rõ `[NEW]` class bọc kèm DI); (b) đặt migration ở điểm luôn chạy trước khi Repository đọc nguồn (ví dụ trong `StoryRepository.activeSources()` lần đầu hoặc khởi động app qua repository), không chỉ trong `SettingsViewModel`; (c) gộp đọc cờ + sửa set + set cờ trong MỘT `dataStore.edit { }`; test: user cũ không mở Cài đặt vẫn có `wikidich` trong `activeSources()`.

**[MINOR]**

- m-d (giữ nguyên): Bước 10/Verification §2 vẫn gọi `.\gradlew.bat ktlintFormat` toàn module; ghi rõ cách giới hạn (ví dụ chỉ sửa tay các vi phạm trong file thuộc phạm vi, hoặc chấp nhận diff `TangThuVienScraper.kt` bằng văn bản).
- m-g: `WebView.saveState` không lưu dữ liệu form đã nhập, và `update` chỉ chạy khi recompose; sửa kỳ vọng Manual Test (giữ trang/URL hiện tại, không hứa giữ chữ đã gõ) hoặc dùng cách khác.
- m-h: `fuzzySign` dùng `require(...)` ném `IllegalArgumentException` (không phải `ScraperException`); nên ném `ScraperException.ParseException` để tuân thủ `04-scraping.md`.
- m-i: Thêm `StorySource.kt` vào phạm vi ktlint nếu plan chạm tới (evidence báo `StorySource.kt:47:87`), hoặc ghi rõ ngoài phạm vi.

#### Kết luận

Revision 3 khắc phục M1' (probe), M5', các MINOR m-a, m-b, m-e, m-f, và đã có bằng chứng `[SRC]` cho công thức chữ ký `/book/index` (SHA-256 + `fuzzySign` cắt tại vị trí trích bằng regex). Còn 5 MAJOR: danh sách chương thiếu response thật/phân trang (N1), URL hoàn thành sai (đang lọc "HE") và selector/ánh xạ thể loại chưa có bằng chứng (N2), hợp đồng `importCookies` không phân biệt lỗi mạng và không rollback (N3), XML loại trừ backup sai `domain` nên không bảo vệ cookie (N4), migration nguồn bật không chạy trước Repository và dùng API DataStore không tồn tại (N5). Planner cần ra Revision 4; N1 và N2(b) cần User cung cấp response `/book/index` và HTML tìm kiếm đã đăng nhập từ `wikicv.org`.

Kết luận: [CHANGES_REQUESTED]

### Audit Run 4

- **Reviewer**: Claude Code CLI (Claude Opus 5.5, `claude-opus-5-5`)
- **Ngày**: 2026-10-13
- **Đối tượng**: Technical Plan Revision 4

#### Kiểm chứng lỗi cũ

| # | Lỗi từ Run 3 | Trạng thái | Bằng chứng / Ghi chú |
|---|---|---|---|
| N1 | Danh sách chương thiếu response thật + phân trang | MỘT PHẦN | `[SRC]` `build/real_book_index.html` và `build/real_book_index_p2.html` nay có nội dung thật: `li.chapter-name > a.truncate[href^="/truyen/…/chuong-1-…-asXu1cQsRAPG6zke"]` (dòng 39-42), `ul.pagination li a[data-start][data-size="99"][data-action="loadBookIndex"]` (dòng 4-30), trang 2 bắt đầu từ "Chương 100" và `li.active` ở `data-start="99"` (p2 dòng 12-13, 42). Selector + fixture ĐẠT. Nhưng điều kiện dừng phân trang tự mâu thuẫn với điều kiện ném lỗi → xem X4. |
| N2(a) | `getCompletedStories` lọc nhầm "HE" | ĐÃ KHẮC PHỤC | `status=5794f03dd7ced228f4419191` khớp nhãn "Hoàn thành" ở `build/wikicv_home.html:502`. |
| N2(b) | Selector tìm kiếm không có bằng chứng | MỘT PHẦN | Đổi sang `.book-item` (`build/wikicv_home.html:458-481`: `.cover-col img`, `.info-col a[href^=/truyen/]`, `h5.book-title`, `p.book-author a`, `p.book-gender`, `p.book-publisher a`) – đúng khối danh sách của site. Fixture `search_logged_in.html` vẫn dựng từ trang chủ, không phải `/tim-kiem` đã đăng nhập (`build/wk_search.html` không có `.book-item`) → hạ xuống MINOR (m-j). |
| N2(c) | Bảng slug thể loại | MỘT PHẦN | Goal 2 ghi đúng theo HTML thật (`tien-hiep` → `qs=2&tag=…91ad&genre=…91a4`, `huyen-huyen` → `qs=2&genre=57d17c94…`, `vo-hiep` → `qs=2&genre=…91a3` khớp `build/wikicv_home.html:312,324,330`), nhưng Bước 5 lại ghi khác → xem X5. |
| N3 | Hợp đồng `importCookies` / rollback | MỘT PHẦN | `SessionVerifyResult` 3 nhánh xuyên suốt Manager → Repository → ViewModel, probe trước khi ghi → hợp đồng ĐẠT. Nhưng cơ chế "header `Cookie` tạm" không có hiệu lực trên `OkHttpClient` dùng chung có `CookieJar` → xem X2. |
| N4 | XML loại trừ backup sai `domain` | ĐÃ KHẮC PHỤC | `domain="root" path="app_webview/"` cho `cloud-backup`, `device-transfer` và `full-backup-content`; Manifest khai báo cả hai; có bước `lintDebug`. `app/src/main/res/xml/` hiện chưa tồn tại (Glob 0 kết quả) → nhãn `[NEW]` đúng. |
| N5 | Migration `ENABLED_SOURCES` | MỘT PHẦN | (b) điểm gọi trong `StoryRepository.activeSources()` + `SettingsViewModel.init` – ĐẠT; (c) một khối `dataStore.edit { }` – ĐẠT. (a) vẫn "Inject `SettingsDataStore`" – class không tồn tại → xem X3. |
| m-d | `ktlintFormat` lan file | CHƯA ĐẠT → NÂNG BLOCKER | Revision 4 thêm `git checkout -- …/TangThuVienScraper.kt` → xem X1. |
| m-g | Kỳ vọng xoay màn hình | ĐÃ KHẮC PHỤC | Bước 6 ghi rõ chỉ giữ URL/lịch sử, không hứa giữ chữ đang gõ. |
| m-h | `fuzzySign` ném `IllegalArgumentException` | ĐÃ KHẮC PHỤC | Ném `ScraperException.ParseException` (Bước 5). |
| m-i | `StorySource.kt` ngoài phạm vi ktlint | ĐÃ KHẮC PHỤC | Goal 6 ghi rõ giữ nguyên logic `StorySource.kt`. |

#### Mental Simulation Matrix (S1–S6 + Domain)

| Kịch bản | Kết quả | Bằng chứng | Chi tiết |
|---|---|---|---|
| S1 Cold-start & Build | FAIL | `[CMD]` compileDebugKotlin Exit 0; `[CMD]` ktlintCheck Exit 1; `[SRC]` `data/local/SettingsDataStore.kt:1-57`; `[SRC]` `ui/settings/SettingsViewModel.kt:68-73,92-98`; `[SRC]` Glob `app/src/test/.../ui/**` | Baseline biên dịch sạch, không thêm dependency, binding Hilt mới có chỗ đăng ký (`RepositoryModule.kt`, `ScraperModule.kt`, `AppModule.kt`). Lỗi: (a) Bước 7/8 "Inject `SettingsDataStore`" và gọi `settingsDataStore.migrateEnabledSourcesIfNeeded()`, nhưng `SettingsDataStore.kt` chỉ có `val Context.settingsDataStore` + `object SettingsKeys`; code mẫu dùng biến `dataStore` và `ALL_SOURCES`/`DEPRECATED_SOURCES` vốn nằm trong companion của `SettingsViewModel` (tầng `ui/`) → hoặc không biên dịch, hoặc `data/local` phải import `ui/settings` (ngược chiều kiến trúc) — X3. (b) `ReaderViewModelTest.kt` KHÔNG tồn tại (chỉ có test `detail`, `downloads`, `explore`, `home`, `navigation`, `search`, `stats`, `settings`) nhưng được gắn `[MODIFY]` → m-k. |
| S2 Lifecycle & Teardown | PASS | `[SRC]` plan Bước 6 (`AndroidView(factory, update, onRelease { saveState; stopLoading; destroy })`); `[ADV]` L4 | WebView được hủy, trạng thái URL giữ qua `rememberSaveable` Bundle; ViewModel dùng `viewModelScope`, Repository `Dispatchers.IO`, không `GlobalScope`/`runBlocking`. |
| S3 Database & DataStore | PASS (logic) / FAIL (hiện thực – gộp X3) | `[SRC]` `StoryRepository.kt:64-76`; `[SRC]` `SettingsDataStore.kt:47`; `[ADV]` D4, D5 | Room giữ nguyên v9, `sourceId` cũ đọc offline được. Migration đọc cờ + sửa set + set cờ trong một `edit {}` → nguyên tử (D4 an toàn); gọi trong `activeSources()` nên user cũ không mở Cài đặt vẫn có `wikidich` (D5 an toàn). Key mới có default `false`, không đổi tên key cũ. Phần hiện thực API thuộc X3. |
| S4 Cross-Task State Flow | PASS (có lưu ý) | `[SRC]` `di/RepositoryModule.kt`; `[SRC]` `StoryRepository.kt:104-207`; `[SRC]` `ExploreViewModelTest.kt`, `SearchViewModelTest.kt`, `SettingsViewModelTest.kt` tồn tại | Luồng Screen → VM → `ISourceAuthRepository` → `SourceAuthManager` đúng ranh giới; `SessionVerifyResult` truyền đến VM để hiện đúng 2 thông báo tiếng Việt. Rethrow `LoginRequiredException` ở catch trang (`:124`, `:181`) lan lên scope `withTimeoutOrNull` rồi bị catch ngoài (`:135`, `:191`), không ảnh hưởng nguồn khác trong `supervisorScope`. Lưu ý: `SourceAuthRepository` nằm trong `data/repository` nhưng `ISourceAuthRepository` trả `SessionVerifyResult`/`SourceAuthConfig` thuộc `data/remote/auth` → VM vẫn import kiểu từ `data/remote` (m-l). |
| S5 Security & Privacy | FAIL | `[SRC]` `di/AppModule.kt:44-47` (`OkHttpClient.Builder().cookieJar(cookieHandler)`); `[SRC]` `cookie/WebViewCookieHandler.kt:24-43` (`saveFromResponse` ghi mọi Set-Cookie vào `CookieManager`, `loadForRequest` trả cookie đã lưu); `[ADV]` S5-4, S5-5 | (a) Probe dán cookie dùng `client` được Hilt inject (client dùng chung có `CookieJar`). `BridgeInterceptor` của OkHttp gọi `cookieJar.loadForRequest(url)` và nếu khác rỗng thì `requestBuilder.header("Cookie", …)` — **thay thế** header `Cookie` do người gọi đặt. Đồng thời mọi `Set-Cookie` của response probe đi qua `saveFromResponse` vào `CookieManager`. Do đó "KHÔNG ghi vào CookieStore trước khi verify" và "bảo toàn cookie cũ" không đúng ở thời điểm thực thi (S5-4, S5-5) → X2. (Hành vi `BridgeInterceptor` là `[SRC]` mã nguồn OkHttp `okhttp3.internal.http.BridgeInterceptor`, reviewer không tải được qua allowlist → plan phải chốt bằng test `MockWebServer` kiểm `takeRequest().headers["Cookie"]`.) (b) Auto Backup, xóa log cookie, `shouldOverrideUrlLoading`, không `addJavascriptInterface` → ĐẠT. |
| S6 External Resilience | FAIL | `[SRC]` `build/real_book_index.html:4-42`, `build/real_book_index_p2.html:12-13,42`; `[SRC]` `build/get-book.js:1`; `[SRC]` `build/wikicv_home.html:312-334`; `[ADV]` C2 | Chữ ký, endpoint, selector chương, fixture thật → ĐẠT. Lỗi: phân trang "lặp `start += 99` cho đến khi fragment không còn chương" xung đột với "fragment không có chương → ném `ParseException`" (C2) → X4. Pagination chỉ hiện cửa sổ 5 trang (`data-start` 0…396), nên "đọc tất cả `data-start`" ở trang đầu cũng không cho tổng số trang. `getStoriesByGenre` mâu thuẫn tham số giữa Goal và Bước 5 → X5. |
| Domain | PASS (có lưu ý) | `[SRC]` `StoryRepository.kt:211-259`; `[SRC]` `StoryCardPremium.kt:243,418`; `[SRC]` `data/model/Story.kt:16`, `StoryEntity.kt:24`, `BackupData.kt:31` | Union genre thay intersect → không mất thể loại; slug lạ ở Wikidich trả `emptyList()`. Watermark `【❁…(Wikidich)…❁】` bị lọc trước TTS. Badge TC/TF giữ cho truyện cũ. Lưu ý: `Story.sourceId`, `StoryEntity.sourceId`, `BackupData.sourceId` mặc định `"truyencom"` → parser Wikidich phải gán `sourceId = "wikidich"` tường minh và test phải assert (m-m). |

#### Adversarial Timelines

```
Cơ chế: Dán cookie khi đang có phiên hợp lệ — S5-4 (TOCTOU, header tạm bị CookieJar ghi đè)
T1: User đã đăng nhập, CookieManager giữ express.sid=VALID | T2: User dán nhầm "express.sid=RAC" → importCookies gửi GET /tim-kiem?qs=1&q=tien kèm header Cookie: express.sid=RAC qua client dùng chung (AppModule.kt:44-47) | T3: BridgeInterceptor gọi WebViewCookieHandler.loadForRequest → trả express.sid=VALID → header("Cookie", "express.sid=VALID") thay thế header tạm; server trả trang không có marker
Kết cục: importCookies trả Success rồi ghi express.sid=RAC vào CookieStore (Max-Age 30 ngày) → phá phiên hợp lệ, Settings hiện "Đã đăng nhập", lần tìm kiếm sau ném LoginRequired → LỖI (MAJOR X2).
```

```
Cơ chế: Set-Cookie của response probe rò vào CookieManager — S5-5 (trục Thất bại giữa chừng)
T1: User đã đăng nhập (express.sid=VALID) | T2: Dán token hết hạn; server Express không nhận session, cấp express.sid=GUEST qua Set-Cookie | T3: WebViewCookieHandler.saveFromResponse (WebViewCookieHandler.kt:24-33) ghi express.sid=GUEST + flush; importCookies thấy marker → InvalidCookie và "không ghi CookieStore"
Kết cục: Cookie hợp lệ cũ bị thay bằng guest dù plan cam kết "giữ nguyên cookie cũ" → LỖI (MAJOR X2). Hành vi Set-Cookie của server với sid không hợp lệ là UNVERIFIED; dù vậy plan không được dựa vào giả định server không cấp cookie.
```

```
Cơ chế: Phân trang danh sách chương Wikidich — C2 (điểm dừng)
T1: Truyện 450 chương; getRemainingChapters tải start=99, 198, 297, 396 thành công | T2: Lặp start=495 → server trả fragment không có li.chapter-name (hoặc trả lại trang cuối – UNVERIFIED) | T3: Theo Bước 5 "phản hồi rỗng → ném ParseException"
Kết cục: Mọi truyện đều kết thúc bằng ParseException ở trang vượt cuối → toàn bộ danh sách chương bị coi là lỗi (hoặc, nếu server trả lại trang cuối, lặp vô hạn) → LỖI (MAJOR X4). Cần: trang kế tồn tại khi và chỉ khi fragment hiện tại có a[data-action=loadBookIndex][data-start=start+size]; ParseException chỉ cho trang đầu rỗng hoặc trang đã được pagination công bố mà rỗng.
```

```
Cơ chế: ktlintFormat + git checkout file ngoài phạm vi — K1 (trục Thất bại của bước dọn dẹp)
T1: Working tree có TangThuVienScraper.kt đang sửa chưa commit (evidence `git status --short`: " M …/TangThuVienScraper.kt"; `git diff --stat`: 694 dòng) | T2: Implementer chạy .\gradlew.bat ktlintFormat (Bước 10) → file bị format | T3: Chạy git checkout -- …/TangThuVienScraper.kt để "khôi phục"
Kết cục: Khôi phục về HEAD chứ không về trạng thái trước format → mất toàn bộ 694 dòng thay đổi chưa commit của User; đồng thời vi phạm 00-core.md "KHÔNG tự … checkout" → LỖI (BLOCKER X1).
```

```
Cơ chế: Migration ENABLED_SOURCES gọi đồng thời — D4 (trục Đồng thời)
T1: StoryRepository.activeSources() (Search) gọi migrate | T2: SettingsViewModel.init cùng lúc gọi migrate; user bấm tắt sstruyen | T3: DataStore tuần tự hóa các edit {}; edit thứ hai đọc cờ = true nên bỏ qua; toggleSource ghi sau cùng
Kết cục: Chỉ migrate 1 lần, lựa chọn của user không bị ghi đè → AN TOÀN.
```

```
Cơ chế: User cũ không mở Cài đặt — D5 (trục Vòng đời)
T1: Cập nhật app, ENABLED_SOURCES = "truyencom,tangthuvien,sstruyen,truyenfull" | T2: Mở Search ngay | T3: activeSources() chạy migrate trước khi đọc key → {tangthuvien, sstruyen, wikidich}
Kết cục: Wikidich được truy vấn ngay → AN TOÀN (khi X3 được hiện thực đúng).
```

```
Cơ chế: Ghi đồng thời loginRequiredSources (trục Đồng thời)
T1: async search Wikidich markLoginRequired | T2: async getStoriesByGenre markLoginRequired; SourceLoginViewModel clearLoginRequired | T3: MutableStateFlow.update { } (CAS)
Kết cục: Không mất cập nhật → AN TOÀN.
```

```
Cơ chế: WebView đăng nhập khi xoay màn hình — L4 (trục Vòng đời Android)
T1: User đang ở trang đăng nhập DTH | T2: Xoay → onRelease saveState vào Bundle (rememberSaveable), stopLoading, destroy | T3: factory mới restoreState(Bundle) → về đúng URL
Kết cục: Không rò rỉ WebView, giữ URL; chữ đang gõ có thể mất (đã ghi rõ trong plan) → AN TOÀN.
```

Trục TOCTOU kiểm chương offline: N/A (logic offline-first `StoryRepository.kt:492-534` chỉ đổi thông báo lỗi).

#### Commands Executed

Trích từ `evidence.md` (reviewer không chạy lệnh):

- `### [CMD] cmd.exe /d /c gradlew.bat compileDebugKotlin` — `Exit: 0` — "BUILD SUCCESSFUL in 8s".
- `### [CMD] cmd.exe /d /c gradlew.bat ktlintCheck` — `Exit: 1` — "Task :app:ktlintMainSourceSetCheck FAILED"; ngoài phạm vi: `StorySource.kt:47:87`, `TangThuVienScraper.kt` (3:1 … 526:1); trong phạm vi: `StoryRepository.kt:220:40,238:1,241:1`, `ScraperModule.kt:36:1`, `AppNavigation.kt:86:1,332:1`, `ReaderViewModel.kt:428:33`, `SettingsScreen.kt:46:1,394:1`, `SettingsViewModel.kt:103:27,129:1`.
- `### [CMD] cmd.exe /d /c gradlew.bat testDebugUnitTest` — `Exit: 0` — "BUILD SUCCESSFUL in 2s".
- `### [CMD] git status --short` — `Exit: 0` — " M app/src/main/java/com/personal/apptruyen/data/remote/TangThuVienScraper.kt" (cùng nhiều file khác chưa commit).
- `### [CMD] git diff --stat` — `Exit: 0` — ".../apptruyen/data/remote/TangThuVienScraper.kt    | 694 ++++++++++----------".
- `### [CMD] node --test .agents/scripts/code-freeze-guard.test.js` — `Exit: 0` — "pass 31, fail 0".
- `### [CMD] node --test .agents/scripts/plan-approval.test.js` — `Exit: 0` — "pass 12, fail 0".
- `### [CMD] node --test .agents/scripts/review-run.test.js` — `Exit: 0` — "pass 10, fail 0".

UNVERIFIED (đề xuất): response của `/book/index` khi `start` vượt số chương (User tự lấy bằng script `build/fetch_book_index.js` đã có, lưu vào `build/`); HTML `/tim-kiem?qs=1&q=<từ khóa>` khi ĐÃ đăng nhập; sau khi hiện thực X2 chạy `.\gradlew.bat testDebugUnitTest --tests "com.personal.apptruyen.data.remote.auth.SourceAuthManagerTest"`.

#### Phân loại lỗi

**[BLOCKER]**

- **X1 – Bước 10 / Verification §2 làm mất thay đổi chưa commit**: `git checkout -- app/src/main/java/com/personal/apptruyen/data/remote/TangThuVienScraper.kt` khôi phục về HEAD, trong khi file đang có 694 dòng sửa chưa commit (evidence `git status --short`, `git diff --stat`) → mất dữ liệu của User; đồng thời vi phạm `.agents/rules/00-core.md` (không tự checkout). Yêu cầu: bỏ hẳn lệnh `git checkout`; KHÔNG chạy `ktlintFormat` toàn module — sửa tay các vi phạm trong phạm vi đã liệt kê ở evidence (`StoryRepository.kt:220,238,241`, `ScraperModule.kt:36`, `AppNavigation.kt:86,332`, `ReaderViewModel.kt:428`, `SettingsScreen.kt:46,394`, `SettingsViewModel.kt:103,129`) cùng file mới; tiêu chí chấp nhận: output `ktlintCheck` không còn dòng nào thuộc file trong bảng Files (vi phạm còn lại chỉ ở `TangThuVienScraper.kt`, `StorySource.kt`).

**[MAJOR]**

- **X2 – Probe dán cookie không cô lập khỏi `CookieJar` (S5, tiếp N3)**: `SourceAuthManager` phải dùng client riêng cho probe, ví dụ `client.newBuilder().cookieJar(CookieJar.NO_COOKIES).build()` (dùng chung connection pool), để header `Cookie: express.sid={token}` được gửi nguyên vẹn và `Set-Cookie` của response không ghi vào `CookieManager`; chỉ sau `Success` mới ghi qua `CookieStore`. Test bắt buộc (MockWebServer): CookieStore giữ `express.sid=OLD` → `takeRequest().headers["Cookie"] == "express.sid=<pasted>"`; response có `Set-Cookie: express.sid=GUEST` + marker → `InvalidCookie` và CookieStore vẫn `OLD`. Cập nhật Bước 2, Test Contract #3 và Risk Table.
- **X3 – Migration DataStore dùng API/kiểu không tồn tại và kéo hằng số tầng UI xuống `data/local` (S1, tiếp N5(a))**: không có class `SettingsDataStore` để inject. Chốt một cách: top-level `suspend fun DataStore<Preferences>.migrateEnabledSourcesIfNeeded()` trong `SettingsDataStore.kt`, gọi qua `dataStore` đã inject sẵn ở `StoryRepository.kt:43` và `SettingsViewModel.kt:71`; chuyển `ALL_SOURCES`/`DEPRECATED_SOURCES` (id nguồn) sang tầng data (ví dụ `object SourceIds` trong `data/local` hoặc `data/model`) để `SettingsViewModel` tham chiếu ngược lên, không để `data/local` import `ui/settings`. Sửa đồng bộ Goal 5, Bước 7, Bước 8, bảng Files; test migration trên `DataStore` thật (`PreferenceDataStoreFactory.create` với file tạm) hoặc fake.
- **X4 – Điểm dừng phân trang danh sách chương tự mâu thuẫn (S6, tiếp N1)**: thay "lặp `start += 99` đến khi rỗng (hoặc duyệt pagination)" bằng một thuật toán duy nhất: tải trang kế khi và chỉ khi fragment hiện tại có `a[data-action=loadBookIndex][data-start=start+size]`; dừng khi không có; giới hạn vòng lặp an toàn (ví dụ số trang tối đa) chống lặp vô hạn; `ParseException` chỉ khi trang đầu không có `li.chapter-name a` hoặc trang được pagination công bố mà rỗng. Test: fixture trang cuối (không có liên kết trang kế — có thể cắt từ fixture thật và ghi rõ là biến thể) → dừng không ném lỗi; dùng `MockWebServer` kiểm số request.
- **X5 – Ánh xạ thể loại Wikidich mâu thuẫn giữa Goal và Bước 5 (Domain/S6, tiếp N2(c))**: Goal 2 dùng chuỗi query thật (`qs=2&tag=…91ad&genre=…91a4` cho Tiên hiệp, khớp `build/wikicv_home.html:330`), còn Bước 5 ghi `tien-hiep -> 5794f03dd7ced228f44191a2` (ObjectId không xuất hiện trong bất kỳ HTML khảo sát nào) và URL `tim-kiem?genre={genreId}&qs=1`. Yêu cầu: Bước 5 dùng đúng dạng "slug → query string" của Goal, nêu nguồn bảng (`build/wikicv_home.html` khối `div.tag-tab`, ghi ngày khảo sát), đặc tả cách ghép `completedOnly` (`&status=5794f03dd7ced228f4419191`) và tham số trang; test: một slug có `tag`+`genre`, một slug chỉ `genre`, slug lạ → `emptyList()` (không gọi mạng).

**[MINOR]**

- m-j: Fixture `search_logged_in.html` dựng từ trang chủ (`.book-item` ở `wikicv_home.html:458-481`), chưa phải trang `/tim-kiem` đã đăng nhập; ghi rõ là fixture dẫn xuất và bổ sung Manual Test tìm kiếm khi đã đăng nhập.
- m-k: `app/src/test/java/com/personal/apptruyen/ui/reader/ReaderViewModelTest.kt` chưa tồn tại → đổi nhãn `[MODIFY]` thành `[NEW]` (hoặc đặt test hàm thuần ở file khác).
- m-l: `ISourceAuthRepository` trả `SessionVerifyResult`/`SourceAuthConfig` khai báo trong `data/remote/auth` → ViewModel vẫn import kiểu từ `data/remote`. Nên đặt 2 kiểu này ở `data/model` (hoặc `data/repository`).
- m-m: `Story.sourceId`/`StoryEntity.sourceId`/`BackupData.sourceId` mặc định `"truyencom"`; parser Wikidich phải gán `sourceId = "wikidich"` tường minh và test assert giá trị này.
- m-n: `migrateEnabledSourcesIfNeeded()` chạy mỗi lần `activeSources()`; DataStore bỏ qua ghi khi dữ liệu không đổi nhưng vẫn qua hàng đợi `edit` — nên đọc cờ trước (`data.first()`) hoặc nhớ cờ trong bộ nhớ để tránh tuần tự hóa mọi truy vấn.

#### Kết luận

Revision 4 khắc phục N2(a), N4, m-g, m-h, m-i; danh sách chương nay có fixture `/book/index` thật (trang 1 và 2) với selector đã kiểm chứng; hợp đồng `SessionVerifyResult` và migration nguyên tử gọi từ `activeSources()` đúng hướng. Còn 1 BLOCKER: lệnh `git checkout` trong Quality Gate sẽ xóa 694 dòng thay đổi chưa commit ở `TangThuVienScraper.kt` (X1). Còn 4 MAJOR: probe dán cookie bị `CookieJar` dùng chung ghi đè header và ghi Set-Cookie (X2), migration dùng class `SettingsDataStore` không tồn tại và kéo hằng số UI xuống data (X3), điểm dừng phân trang mâu thuẫn với điều kiện ném lỗi (X4), bảng thể loại mâu thuẫn giữa Goal và Bước 5 (X5). Planner cần ra Revision 5.

Kết luận: [CHANGES_REQUESTED]

### Audit Run 5

- **Reviewer**: Claude Code CLI (Claude Opus 5.5, `claude-opus-5-5`)
- **Ngày**: 2026-10-13
- **Đối tượng**: Technical Plan Revision 5

#### Kiểm chứng lỗi cũ

| # | Lỗi từ Run 4 | Trạng thái | Bằng chứng / Ghi chú |
|---|---|---|---|
| X1 | `git checkout` + `ktlintFormat` làm mất thay đổi chưa commit | ĐÃ KHẮC PHỤC | Goal 6, Bước 10 và Verification §2 bỏ hẳn `git checkout` và `ktlintFormat`, chỉ sửa tay đúng các dòng có trong evidence (`StoryRepository.kt:220,238,241`, `ScraperModule.kt:36`, `AppNavigation.kt:86,332`, `ReaderViewModel.kt:428`, `SettingsScreen.kt:46,394`, `SettingsViewModel.kt:103,129` — khớp `### [CMD] cmd.exe /d /c gradlew.bat ktlintCheck`). Tiêu chí "0 vi phạm trong các file thuộc bảng Files" khả thi. Câu "vi phạm còn lại chỉ ở `TangThuVienScraper.kt`" bỏ sót `StorySource.kt:47:87` → m-q. |
| X2 | Probe dán cookie không cô lập khỏi `CookieJar` | ĐÃ KHẮC PHỤC (cơ chế) | `probeClient = client.newBuilder().cookieJar(CookieJar.NO_COOKIES).build()`; chỉ ghi `CookieStore` khi `Success`. Client dùng chung hiện có `cookieJar(cookieHandler)` (`di/AppModule.kt:44-47`), `WebViewCookieHandler.saveFromResponse` ghi mọi Set-Cookie vào `CookieManager` (`cookie/WebViewCookieHandler.kt:24-33`) → cô lập bằng `NO_COOKIES` đúng chỗ. Test chưa assert header gửi đi → m-o. Còn một khe khác ở bước GHI sau `Success` → Y1. |
| X3 | Migration dùng class không tồn tại + hằng số tầng UI | ĐÃ KHẮC PHỤC | Extension `DataStore<Preferences>.migrateWikidichEnabledSourcesIfNeeded()` + `object SourceIds` trong `data/local/SettingsDataStore.kt` (file hiện chỉ có `settingsDataStore` + `SettingsKeys`, `SettingsDataStore.kt:12-57`); `DataStore<Preferences>` đã được provide (`AppModule.kt:104-108`) và inject sẵn ở `StoryRepository.kt:43`. Không còn `data/local` → `ui/settings`. `SettingsScreen.kt:366,381` vẫn tham chiếu `SettingsViewModel.ALL_SOURCES` → m-p. |
| X4 | Điểm dừng phân trang mâu thuẫn | ĐÃ KHẮC PHỤC | Một thuật toán duy nhất ở Goal 2, Bước 5, Test Contract #13: trang kế khi và chỉ khi có `ul.pagination li a[data-action="loadBookIndex"][data-start=start+size]`; `ParseException` chỉ khi trang 1 rỗng; `maxPages = 200`. Đối chiếu `build/real_book_index_p2.html:4-30`: trang `data-start=99` đang active, có link `data-start="198"` → selector chọn trang kế hoạt động trên HTML thật. |
| X5 | Bảng thể loại mâu thuẫn Goal/Bước 5 | ĐÃ KHẮC PHỤC | Bước 5 nay trùng Goal 2. Đối chiếu `build/wikicv_home.html`: `:330` Tiên hiệp `qs=2&tag=…91ad&genre=…91a4`, `:312` `genre=57d17c94…`, `:324` `genre=…91a3`, `:282` Trọng sinh `tag=…91b4`, `:294` Hiện đại `age=…919d`, `:300` `age=…919e&tag=…91c6`, `:306` Mạt thế `tag=…91ac`, `:336` Võng du `genre=…91a5`; `ngon-tinh` (`gender=…9195&qs=1`) và `xuyen-khong` (`qs=1&tag=…91af`) lấy từ `build/wikicv_story.html:318,333`. Ghép `completedOnly` bằng `&status=…9191` khớp `wikicv_home.html:408`. Slug lạ → `emptyList()` không gọi mạng. |
| m-j | Fixture tìm kiếm dẫn xuất | ĐÃ KHẮC PHỤC | Ghi rõ "dẫn xuất từ `wikicv_home.html`" + Manual Test tìm kiếm khi đã đăng nhập. |
| m-k | `ReaderViewModelTest.kt` nhãn sai | ĐÃ KHẮC PHỤC | Nhãn `[NEW]`; Glob `app/src/test/.../ui/**/*Test.kt` xác nhận file chưa tồn tại. |
| m-l | Kiểu trả về nằm ở `data/remote/auth` | ĐÃ KHẮC PHỤC | `SessionVerifyResult`, `SourceAuthConfig` ở `data/model/SourceAuth.kt`. |
| m-m | `sourceId` mặc định `"truyencom"` | ĐÃ KHẮC PHỤC | Gán tường minh `sourceId = "wikidich"` + assert trong test #1, #7. |
| m-n | Migration đi qua hàng đợi `edit` mỗi lần | ĐÃ KHẮC PHỤC | Đọc cờ bằng `data.first()` trước, kiểm lại cờ bên trong `edit {}`. |

#### Mental Simulation Matrix (S1–S6 + Domain)

| Kịch bản | Kết quả | Bằng chứng | Chi tiết |
|---|---|---|---|
| S1 Cold-start & Build | PASS | `[CMD]` compileDebugKotlin Exit 0; `[CMD]` ktlintCheck Exit 1 (chỉ vi phạm đã liệt kê); `[SRC]` `ScraperException.kt:7-49`, `AppModule.kt:36-53,104-108`, `SettingsDataStore.kt:1-57` | Không thêm dependency. `LoginRequiredException` thêm được vào sealed class sẵn có. Mọi class mới đều có nhãn `[NEW]` và chỗ đăng ký Hilt (`AppModule` cho `CookieStore`/`SourceAuthManager`, `RepositoryModule` cho `ISourceAuthRepository`, `ScraperModule` cho `WikidichScraper`). API dùng (`MessageDigest`, `CookieManager`, `WebView.saveState/restoreState`, `AndroidView(onRelease)`) đều chạy được với minSdk 26. Các test bị đổi constructor (`SearchViewModelTest`, `ExploreViewModelTest`, `SettingsViewModelTest`, `StoryRepositoryTest`) đều nằm trong bảng Files. Quality gate có tiêu chí khả thi. |
| S2 Lifecycle & Teardown | PASS | `[SRC]` plan Bước 6; `[ADV]` L5 | `onRelease` gọi `saveState` → `stopLoading` → `destroy`; `restoreState` từ Bundle `rememberSaveable`. ViewModel dùng `viewModelScope`, Repository dùng `Dispatchers.IO`, không `GlobalScope`/`runBlocking`. Lưu ý nhỏ: plan không ghi đóng `Response` của probe bằng `use {}` (m-r). |
| S3 Database & DataStore | PASS | `[SRC]` `StoryRepository.kt:64-76`; `[SRC]` `SettingsDataStore.kt:47`; `[ADV]` D4, D5 (Run 4, vẫn đúng với Revision 5) | Room giữ v9, không đổi entity. Migration: đọc cờ, sửa set, set cờ trong một `edit {}`; gọi ở `activeSources()` trước khi đọc `ENABLED_SOURCES` → user cũ có `wikidich` ngay; key mới mặc định `false`, không đổi tên key cũ; sau migration tôn trọng lựa chọn tắt. Lưu ý: `activeSources()` đang có `catch (_: Exception) { null }` (`StoryRepository.kt:72`). Nếu đặt migration trong khối `try` đó thì lỗi migration sẽ bị nuốt và `CancellationException` cũng bị nuốt; implementer nên gọi migration ngoài khối đó hoặc rethrow (gộp m-r). |
| S4 Cross-Task State Flow | PASS | `[SRC]` `StoryRepository.kt:104-207,269-298`; `[SRC]` grep `ALL_SOURCES` → `SettingsScreen.kt:366,381`, `SettingsViewModel.kt:86,92,124` | Luồng Screen → VM → `ISourceAuthRepository` → `SourceAuthManager` đúng ranh giới; kiểu dùng chung nằm ở `data/model`. `SessionVerifyResult` 3 nhánh tới VM → 2 thông báo tiếng Việt riêng. Rethrow `LoginRequiredException` ở catch trang (`:124`, `:181`) lan lên scope `withTimeoutOrNull` rồi bị catch ngoài (`:135`, `:191`, search `:296`), không ảnh hưởng nguồn khác trong `supervisorScope`. |
| S5 Security & Privacy | FAIL | `[SRC]` `cookie/WebViewCookieHandler.kt:24-43`; `[SRC]` `AppModule.kt:44-47`; `[SRC]` plan Bước 2 (dòng "ghi cookie vào `cookieStore` (`Path=/; Secure; Max-Age=2592000; Domain=.wikicv.org`)"); `[SRC]` `build/dth_h.txt:45` (chỉ có Set-Cookie của forum, không có của `wikicv.org`); `[ADV]` S5-6 | Đã ĐẠT: probe cô lập, không ghi khi `InvalidCookie`/`NetworkError`, xóa log cookie, `shouldOverrideUrlLoading` giới hạn host, không `addJavascriptInterface`, loại trừ `app_webview/` khỏi backup. CHƯA ĐẠT: sau `Success`, plan ghi một cookie MỚI có `Domain=.wikicv.org` nhưng không xóa cookie `express.sid` cũ (host-only `wikicv.org`) do WebView hoặc client dùng chung đã lưu trước đó (`saveFromResponse` ghi mọi Set-Cookie vào `CookieManager`). Hai cookie cùng tên khác domain cùng tồn tại → request sau gửi cả hai (S5-6). Thuộc tính `Domain` của `express.sid` trên `wikicv.org` là UNVERIFIED. Yêu cầu này đã nêu ở Run 1 M3 ("`importCookies` không ghi trùng cả host-only lẫn `.wikicv.org`") nhưng chưa có trong Technical Plan → Y1. |
| S6 External Resilience | PASS | `[SRC]` `build/real_book_index.html:4-42`, `build/real_book_index_p2.html:4-42`; `build/get-book.js:1`; `build/wikicv_home.html:276-408,458-502`; `build/wk_search.html:264` | Chữ ký, endpoint, selector chương, điểm dừng phân trang, URL hoàn thành, bảng thể loại đều có bằng chứng HTML thật kèm ngày khảo sát. Ném `ScraperException` (Parse/LoginRequired), không trả chương rỗng như thành công, `LoginRequired` không mở circuit breaker. Probe lỗi mạng → `NetworkError`, không logout. |
| Domain | PASS | `[SRC]` `StoryRepository.kt:211-259`; plan Bước 5, 7, 9 | Union thể loại, slug lạ trả `emptyList()`, watermark `【❁…(Wikidich)…❁】` bị lọc trước TTS, badge TC/TF giữ cho truyện cũ, đọc offline nguồn đã gỡ, thông báo tiếng Việt cho chương chưa tải, `tryConstructChapterUrl` trả `null` cho URL hash. UI text trong plan đều tiếng Việt. |

#### Adversarial Timelines

```
Cơ chế: Ghi cookie sau khi probe Success khi đã có express.sid cũ — S5-6 (TOCTOU + trục Thất bại giữa chừng)
T1: User chưa đăng nhập mở Search; WikidichScraper.search gọi wikicv.org qua client dùng chung (AppModule.kt:44-47); server cấp express.sid=GUEST (host-only, nếu server không đặt Domain); WebViewCookieHandler.saveFromResponse ghi vào CookieManager (WebViewCookieHandler.kt:24-33) | T2: User dán express.sid=NEW hợp lệ; probeClient (NO_COOKIES) xác nhận không có marker → Success; importCookies setCookie("express.sid=NEW; …; Domain=.wikicv.org") — key (name, domain, path) khác cookie GUEST host-only nên KHÔNG ghi đè | T3: Request tiếp theo: CookieManager.getCookie("https://wikicv.org") trả cả "express.sid=GUEST; express.sid=NEW" (cùng Path=/, thường cookie tạo trước đứng trước); loadForRequest (WebViewCookieHandler.kt:35-43) gửi cả hai; express-session thường lấy giá trị đầu
Kết cục: Settings hiện "Đã đăng nhập" (authState=true) nhưng tìm kiếm ném LoginRequiredException → markLoginRequired → authState=false; dán lại vẫn lặp lại. Luồng dự phòng "dán cookie PC" không dùng được trong đúng tình huống nó cần → LỖI (MAJOR Y1). Thuộc tính Domain thật và thứ tự gửi cookie là UNVERIFIED; plan không được dựa vào giả định thuận lợi.
```

```
Cơ chế: Dán cookie hỏng khi đang có phiên hợp lệ — S5-4' (kiểm lại X2)
T1: CookieManager giữ express.sid=VALID | T2: User dán express.sid=RAC → probeClient (CookieJar.NO_COOKIES) gửi header Cookie: express.sid=RAC nguyên vẹn; server trả marker + Set-Cookie GUEST, NO_COOKIES bỏ qua Set-Cookie | T3: InvalidCookie, không ghi CookieStore
Kết cục: express.sid=VALID giữ nguyên, authState không đổi → AN TOÀN.
```

```
Cơ chế: Probe gặp lỗi mạng — N3' (trục Thất bại giữa chừng)
T1: User dán cookie | T2: Timeout/IOException ở probeClient | T3: NetworkError, không ghi CookieStore, không hạ authState; VM hiện "Không thể kết nối đến máy chủ để xác minh phiên, vui lòng thử lại"
Kết cục: AN TOÀN.
```

```
Cơ chế: Phân trang danh sách chương — C3 (kiểm lại X4)
T1: getRemainingChapters tải start=99 (real_book_index_p2.html), thấy a[data-start="198"] | T2: … tải start=396 (trang 5 của truyện 5 trang), fragment không có a[data-start="495"] | T3: Dừng vòng lặp, trả danh sách đã gom
Kết cục: Không ném lỗi ở trang cuối, không lặp vô hạn (maxPages=200) → AN TOÀN. Cách cửa sổ pagination trượt với truyện > 5 trang là UNVERIFIED, nhưng selector theo start+size không phụ thuộc kích thước cửa sổ.
```

```
Cơ chế: Ghi đồng thời loginRequiredSources và migration (trục Đồng thời)
T1: async search Wikidich markLoginRequired; activeSources() gọi migrate | T2: async getStoriesByGenre markLoginRequired; SettingsViewModel.init gọi migrate; SourceLoginViewModel clearLoginRequired | T3: MutableStateFlow.update {} (CAS); DataStore tuần tự hóa edit {}, edit thứ hai thấy cờ = true
Kết cục: Không mất cập nhật, migrate đúng 1 lần → AN TOÀN.
```

```
Cơ chế: WebView đăng nhập khi xoay màn hình — L5 (trục Vòng đời Android)
T1: User ở trang đăng nhập DTH | T2: Xoay → onRelease saveState vào Bundle rememberSaveable, stopLoading, destroy | T3: factory mới thấy Bundle khác rỗng → restoreState, không loadUrl lại
Kết cục: Không rò rỉ, giữ URL/lịch sử (không hứa giữ chữ đang gõ) → AN TOÀN.
```

Trục TOCTOU kiểm chương offline: N/A (logic offline-first `StoryRepository.kt:492-534` chỉ đổi thông báo lỗi).

#### Commands Executed

Trích từ `evidence.md` (reviewer không chạy lệnh):

- `### [CMD] cmd.exe /d /c gradlew.bat compileDebugKotlin` — `Exit: 0` — "BUILD SUCCESSFUL in 9s".
- `### [CMD] cmd.exe /d /c gradlew.bat ktlintCheck` — `Exit: 1` — "Task :app:ktlintMainSourceSetCheck FAILED"; ngoài phạm vi: `StorySource.kt:47:87`, `TangThuVienScraper.kt` (3:1 … 526:1); trong phạm vi: `StoryRepository.kt:220:40,238:1,241:1`, `ScraperModule.kt:36:1`, `AppNavigation.kt:86:1,332:1`, `ReaderViewModel.kt:428:33`, `SettingsScreen.kt:46:1,394:1`, `SettingsViewModel.kt:103:27,129:1`.
- `### [CMD] cmd.exe /d /c gradlew.bat testDebugUnitTest` — `Exit: 0` — "BUILD SUCCESSFUL in 2s".
- `### [CMD] git status --short` — `Exit: 0` — " M app/src/main/java/com/personal/apptruyen/data/remote/TangThuVienScraper.kt" (cùng nhiều file khác chưa commit).
- `### [CMD] git diff --stat` — `Exit: 0` — ".../apptruyen/data/remote/TangThuVienScraper.kt    | 694 ++++++++++----------".
- `### [CMD] node --test .agents/scripts/code-freeze-guard.test.js` — `Exit: 0` — "pass 31, fail 0".
- `### [CMD] node --test .agents/scripts/plan-approval.test.js` — `Exit: 0` — "pass 12, fail 0".
- `### [CMD] node --test .agents/scripts/review-run.test.js` — `Exit: 0` — "pass 10, fail 0".

UNVERIFIED (đề xuất User tự cung cấp, host `wikicv.org` ngoài allowlist): header `Set-Cookie` của `https://wikicv.org/` khi chưa đăng nhập (có `Domain=` hay host-only) để chốt Y1. `[URL]` tài liệu `CookieJar.NO_COOKIES` trên square.github.io (2 URL thử đều 404). Plan bù bằng test MockWebServer (xem m-o). Sau khi hiện thực: `.\gradlew.bat testDebugUnitTest --tests "com.personal.apptruyen.data.remote.auth.SourceAuthManagerTest"`.

#### Phân loại lỗi

**[BLOCKER]**

- Không có.

**[MAJOR]**

- **Y1 – Ghi cookie sau khi dán tạo bản trùng `express.sid` (S5-6, phần chưa làm của Run 1 M3)**: trong nhánh `Success` của `importCookies`, trước khi ghi phải xóa mọi biến thể `express.sid` hiện có (host-only `https://wikicv.org` và `.wikicv.org`, dùng lại đúng thủ tục `Max-Age=0` của `logout`), rồi ghi MỘT cookie duy nhất. Cách ghi cần khớp phạm vi server dùng. Nếu chưa xác minh được thì ghi host-only (không có `Domain=`) cho `https://wikicv.org`, vì đây là dạng express-session mặc định. Bổ sung vào Bước 2 + Test Contract #3: fake `CookieStore` đang có `express.sid=GUEST` (host-only) → `importCookies` thành công → assert `removeCookies` được gọi cho cả hai biến thể TRƯỚC `setCookie`, và `getCookie("https://wikicv.org")` chỉ còn đúng một `express.sid=NEW`. Thêm Manual Test: đang có cookie khách, dán cookie PC → tìm kiếm Wikidich trả kết quả, không hiện banner.

**[MINOR]**

- m-o: Test X2 nên assert `server.takeRequest().headers["Cookie"] == "express.sid=<pasted>"` với client gốc có `CookieJar` trả `OLD`. Đây là cách duy nhất kiểm chứng hành vi `CookieJar.NO_COOKIES`/`BridgeInterceptor` mà plan dựa vào (4.5.1).
- m-p: `SettingsScreen.kt:366,381` đang dùng `SettingsViewModel.ALL_SOURCES`; Bước 8 cần ghi rõ đổi sang `SourceIds.ALL_ACTIVE_SOURCES` (hoặc giữ alias trong companion) để không gãy biên dịch.
- m-q: Tiêu chí ktlint ghi "vi phạm còn lại chỉ ở `TangThuVienScraper.kt`", nhưng evidence còn `StorySource.kt:47:87` (ngoài bảng Files). Sửa câu chữ cho khớp.
- m-r: Đoạn mẫu catch ngoài ở Bước 7 cần (a) rethrow `CancellationException` theo `01-coding.md`; (b) không THÊM `recordFailure` vào `getStoriesByGenre` (hiện `StoryRepository.kt:191-194` không gọi), tránh đổi hành vi circuit breaker ngoài phạm vi; (c) đặt lời gọi migration ngoài `catch (_: Exception)` của `activeSources()` (`:72`); (d) đóng `Response` của probe bằng `use {}`.

#### Kết luận

Revision 5 khắc phục toàn bộ BLOCKER X1 và các MAJOR X2, X3, X4, X5 của Run 4, cùng các MINOR m-j, m-k, m-l, m-m, m-n. Bằng chứng HTML thật đã đủ cho chữ ký, phân trang, URL hoàn thành và bảng thể loại. Còn 1 MAJOR: nhánh `Success` của `importCookies` ghi thêm `express.sid` với `Domain=.wikicv.org` mà không xóa cookie cũ, nên luồng dán cookie có thể vô hiệu khi đã có cookie khách (Y1). Sửa nhỏ và khoanh vùng: chỉ Bước 2 và Test Contract #3. Planner cần ra Revision 6.

Kết luận: [CHANGES_REQUESTED]

### Audit Run 6

- **Reviewer**: Claude Code CLI (Claude Opus 5.5, `claude-opus-5-5`)
- **Ngày**: 2026-10-13
- **Đối tượng**: Technical Plan Revision 6

#### Kiểm chứng lỗi cũ

| # | Lỗi từ Run 5 | Trạng thái | Bằng chứng / Ghi chú |
|---|---|---|---|
| Y1 | Ghi cookie sau khi dán tạo bản trùng `express.sid` | ĐÃ KHẮC PHỤC | Goal 3 và Bước 2 (nhánh `Success`): (1) xóa mọi `express.sid` cũ cho cả host-only `https://wikicv.org` và domain `.wikicv.org` bằng `Max-Age=0; Expires=Thu, 01 Jan 1970…` với `Path=/`; (2) ghi MỘT cookie host-only `express.sid=$token; Path=/; Secure; Max-Age=2592000`, không có `Domain`; (3) `flush()` rồi mới đổi `authState`. Test Contract #3(1) và Verification §1 assert xóa trước khi ghi và chỉ còn đúng 1 cookie. Có Manual Test "Dán cookie khi đã có cookie khách". Thứ tự này khớp đề xuất Run 5. |
| m-o | Assert header `Cookie` của probe | ĐÃ KHẮC PHỤC | Bước 2 và Verification §1: `server.takeRequest().headers["Cookie"] == "express.sid=<pasted>"`. Test #3(4) giữ kịch bản `OLD` + `Set-Cookie GUEST` + marker → `InvalidCookie`, CookieStore vẫn `OLD`. |
| m-p | `SettingsScreen.kt:366,381` dùng `SettingsViewModel.ALL_SOURCES` | ĐÃ KHẮC PHỤC | Goal 5 và Bước 8 giữ alias `val ALL_SOURCES = SourceIds.ALL_ACTIVE_SOURCES` trong companion. Grep xác nhận hai vị trí gọi vẫn còn (`SettingsScreen.kt:366,381`), nên alias giữ biên dịch được. |
| m-q | Câu chữ tiêu chí ktlint | ĐÃ KHẮC PHỤC | Goal 6, Bước 10 và Verification §2 nay ghi "vi phạm còn lại chỉ ở `TangThuVienScraper.kt` và `StorySource.kt:47:87`", khớp evidence. |
| m-r(a) | Rethrow `CancellationException` ở catch ngoài | ĐÃ KHẮC PHỤC | Có trong đoạn mẫu Bước 7. |
| m-r(b) | Không thêm `recordFailure` vào `getStoriesByGenre` | ĐÃ KHẮC PHỤC (câu chữ) | Bước 7 ghi rõ giữ nguyên `StoryRepository.kt:191-194`. Đoạn mẫu chung vẫn có nhánh `else recordFailure`, dễ bị chép sang hàm genre → m-v. |
| m-r(c) | Migration ngoài `catch (_: Exception)` của `activeSources()` | ĐÃ KHẮC PHỤC | Bước 7 đặt lời gọi ngoài khối `try` (`StoryRepository.kt:66-74`). |
| m-r(d) | Đóng `Response` probe bằng `use {}` | ĐÃ KHẮC PHỤC | `probeClient.newCall(request).execute().use { … }` ở Bước 2, `response.use { }` ở Goal 3. |

#### Mental Simulation Matrix (S1–S6 + Domain)

| Kịch bản | Kết quả | Bằng chứng | Chi tiết |
|---|---|---|---|
| S1 Cold-start & Build | PASS | `[CMD]` compileDebugKotlin Exit 0; `[CMD]` ktlintCheck Exit 1 (đúng danh sách vi phạm plan đã liệt kê); `[SRC]` `di/RepositoryModule.kt:27-39`, `di/AppModule.kt:38-47,108`, `di/ScraperModule.kt:23-35`; grep `getCookieString\|WebViewCookieHandler` | Không thêm dependency. Mọi class mới có nhãn `[NEW]` và có chỗ đăng ký Hilt. Đồ thị DI không có vòng: `StoryRepository` → `ISourceAuthRepository` → `SourceAuthManager` → `OkHttpClient`/`CookieStore`. Xóa `isLoggedIn`/`getCookieString` khỏi `WebViewCookieHandler` an toàn: grep trên `app/src` không có nơi gọi nào ngoài chính file đó và `AppModule` (chỉ dùng constructor). `SettingsScreen.kt:366,381` được alias giữ. Các test có constructor bị đổi đều nằm trong bảng Files. |
| S2 Lifecycle & Teardown | PASS | `[SRC]` plan Bước 2 (`execute().use {}`), Bước 6 (`AndroidView(onRelease { saveState; stopLoading; destroy })`); `[ADV]` L6 | Response probe được đóng. WebView được hủy, trạng thái giữ qua Bundle `rememberSaveable`. ViewModel dùng `viewModelScope`, Repository dùng `Dispatchers.IO`, không `GlobalScope`/`runBlocking`. |
| S3 Database & DataStore | PASS | `[SRC]` `StoryRepository.kt:64-76`; `[SRC]` `AppModule.kt:108`; `[SRC]` plan Bước 8 (extension `migrateWikidichEnabledSourcesIfNeeded`); `[ADV]` D6 | Room giữ v9, không đổi entity. Migration đọc cờ trước, kiểm lại cờ trong `edit {}`, sửa set và đặt cờ trong cùng một `edit` → nguyên tử, chỉ chạy 1 lần. Key mới mặc định `false`, không đổi tên key cũ. Gọi migration ngoài `try/catch` nên lỗi và cancellation không bị nuốt. |
| S4 Cross-Task State Flow | PASS | `[SRC]` `StoryRepository.kt:104-207`; `[SRC]` grep `ALL_SOURCES`; `[SRC]` `SettingsViewModel.kt:71,86,92,124` | Luồng Screen → VM → `ISourceAuthRepository` → `SourceAuthManager` đúng ranh giới; kiểu dùng chung nằm ở `data/model/SourceAuth.kt`; `SourceIds` ở `data/local`, `ui/settings` tham chiếu xuống. Rethrow `LoginRequiredException` ở catch trang (`:124`, `:181`) lan lên `withTimeoutOrNull` rồi được catch ngoài (`:135`, `:191`) bắt, không ảnh hưởng nguồn khác trong `supervisorScope`. |
| S5 Security & Privacy | PASS | `[SRC]` `cookie/WebViewCookieHandler.kt:24-43` (`saveFromResponse` ghi mọi Set-Cookie, `loadForRequest` đọc từ `CookieManager`), `:70` (log raw cookie sẽ bị xóa); `[SRC]` `AppModule.kt:44-47` (client dùng chung có `cookieJar`); `[SRC]` plan Bước 2, Bước 9; `[ADV]` S5-6', S5-4', N3' | Probe dán cookie dùng `probeClient` `CookieJar.NO_COOKIES`, nên header tạm được gửi nguyên vẹn và Set-Cookie không vào `CookieManager`. Có test MockWebServer kiểm header. Chỉ ghi khi `Success`; trước khi ghi xóa cả host-only lẫn `.wikicv.org`, sau đó ghi một bản host-only (Y1). Xóa log cookie. `shouldOverrideUrlLoading` giới hạn host, không `addJavascriptInterface`. `app_webview/` bị loại khỏi cloud-backup/device-transfer/full-backup. Hành vi `CookieJar.NO_COOKIES` với `BridgeInterceptor` vẫn chưa có `[URL]` (square.github.io trả 404 ở Run 5), nhưng đã được chốt bằng test assert header → chấp nhận theo 4.5.1. |
| S6 External Resilience | PASS | `[SRC]` `build/real_book_index.html`, `build/real_book_index_p2.html`, `build/get-book.js`, `build/wikicv_home.html`, `build/wk_search.html` (đã đối chiếu ở Run 4–5, Revision 6 không đổi phần này) | Chữ ký SHA-256 + `fuzzySign`, endpoint `/book/index`, điểm dừng phân trang theo `data-start=start+size`, `maxPages = 200`, URL hoàn thành, bảng 10 slug thể loại, slug lạ → `emptyList()`. Lỗi đi qua `ScraperException` (Parse/LoginRequired); `LoginRequired` không mở circuit breaker; lỗi mạng của probe → `NetworkError`, không logout. |
| Domain | PASS | `[SRC]` `StoryRepository.kt:211-259`; plan Bước 5, 7, 9 | Union thể loại, watermark `【❁…(Wikidich)…❁】` bị lọc trước TTS, badge TC/TF giữ cho truyện cũ, đọc offline nguồn đã gỡ kèm thông báo tiếng Việt cho chương chưa tải, `tryConstructChapterUrl` trả `null` với URL hash, `sourceId = "wikidich"` gán tường minh. Toàn bộ text UI mới trong plan đều tiếng Việt. |

#### Adversarial Timelines

```
Cơ chế: Dán cookie PC khi CookieManager đã có express.sid khách — S5-6' (kiểm lại Y1; trục TOCTOU + Thất bại giữa chừng)
T1: User chưa đăng nhập mở Search; client dùng chung nhận Set-Cookie express.sid=GUEST (host-only hoặc .wikicv.org — UNVERIFIED) → WebViewCookieHandler.saveFromResponse ghi vào CookieManager | T2: User dán express.sid=NEW; probeClient (NO_COOKIES) gửi đúng "Cookie: express.sid=NEW", không có marker → Success | T3: importCookies xóa express.sid ở host-only wikicv.org VÀ ở Domain=.wikicv.org (Path=/, Max-Age=0), rồi setCookie host-only NEW, flush, authState=true, clearLoginRequired
Kết cục: CookieManager.getCookie("https://wikicv.org") chỉ còn express.sid=NEW, bất kể GUEST được cấp theo dạng nào → request sau đăng nhập đúng → AN TOÀN. Nếu server cấp GUEST với Path khác "/" thì lệnh xóa với Path=/ sẽ không trúng; express-session mặc định Path=/ nên rủi ro thấp, đã có Manual Test để phát hiện.
```

```
Cơ chế: Mất mạng giữa bước xóa và bước ghi cookie (trục Thất bại của bước dọn dẹp)
T1: Probe Success | T2: Xóa express.sid cũ thành công | T3: setCookie/flush là thao tác cục bộ trên CookieManager, không có I/O mạng; process bị kill giữa hai lệnh
Kết cục: Trường hợp xấu nhất là mất cookie cũ chưa kịp ghi cookie mới → user ở trạng thái chưa đăng nhập, lần tìm kiếm sau hiện banner nhắc đăng nhập (không mất dữ liệu đọc, không lộ cookie) → AN TOÀN (chấp nhận được).
```

```
Cơ chế: Dán cookie hỏng khi đang có phiên hợp lệ — S5-4' (kiểm lại X2)
T1: CookieManager giữ express.sid=VALID | T2: Dán express.sid=RAC; probeClient gửi nguyên header RAC; server trả marker + Set-Cookie GUEST; NO_COOKIES bỏ qua Set-Cookie | T3: InvalidCookie; nhánh xóa/ghi không chạy
Kết cục: VALID giữ nguyên, authState không đổi → AN TOÀN.
```

```
Cơ chế: Probe gặp lỗi mạng — N3' (trục Thất bại giữa chừng)
T1: User dán cookie | T2: IOException/timeout trong execute().use {} | T3: NetworkError, không ghi CookieStore, không hạ authState; VM hiện "Không thể kết nối đến máy chủ để xác minh phiên, vui lòng thử lại"
Kết cục: AN TOÀN.
```

```
Cơ chế: Migration + loginRequiredSources đồng thời — D6 (trục Đồng thời)
T1: activeSources() (Search) gọi migrate; async Wikidich markLoginRequired | T2: SettingsViewModel.init gọi migrate; SourceLoginViewModel clearLoginRequired | T3: DataStore tuần tự hóa edit {}, edit thứ hai thấy cờ = true thì bỏ qua; MutableStateFlow.update {} là CAS
Kết cục: Migrate đúng 1 lần, không mất cập nhật set → AN TOÀN.
```

```
Cơ chế: WebView đăng nhập khi xoay màn hình — L6 (trục Vòng đời Android)
T1: User ở trang đăng nhập DTH | T2: Xoay → onRelease saveState vào Bundle rememberSaveable, stopLoading, destroy | T3: factory mới thấy Bundle khác rỗng → restoreState, không loadUrl lại
Kết cục: Không rò rỉ WebView, giữ URL/lịch sử (không hứa giữ chữ đang gõ) → AN TOÀN.
```

Trục TOCTOU kiểm chương offline: N/A (logic offline-first trong `StoryRepository` chỉ đổi thông báo lỗi, không đổi luồng đọc).

#### Commands Executed

Trích từ `evidence.md` (reviewer không chạy lệnh):

- `### [CMD] cmd.exe /d /c gradlew.bat compileDebugKotlin` — `Exit: 0` — "BUILD SUCCESSFUL in 8s".
- `### [CMD] cmd.exe /d /c gradlew.bat ktlintCheck` — `Exit: 1` — "Task :app:ktlintMainSourceSetCheck FAILED"; ngoài phạm vi: `StorySource.kt:47:87`, `TangThuVienScraper.kt` (3:1 … 526:1); trong phạm vi: `StoryRepository.kt:220:40,238:1,241:1`, `ScraperModule.kt:36:1`, `AppNavigation.kt:86:1,332:1`, `ReaderViewModel.kt:428:33`, `SettingsScreen.kt:46:1,394:1`, `SettingsViewModel.kt:103:27,129:1` — khớp đúng danh sách sửa tay ở Bước 10.
- `### [CMD] cmd.exe /d /c gradlew.bat testDebugUnitTest` — `Exit: 0` — "BUILD SUCCESSFUL in 2s".
- `### [CMD] git status --short` — `Exit: 0` — " M app/src/main/java/com/personal/apptruyen/data/remote/TangThuVienScraper.kt" (cùng nhiều file khác chưa commit; plan không còn lệnh nào hoàn tác file này).
- `### [CMD] git diff --stat` — `Exit: 0` — ".../apptruyen/data/remote/TangThuVienScraper.kt    | 694 ++++++++++----------".
- `### [CMD] node --test .agents/scripts/code-freeze-guard.test.js` — `Exit: 0` — "pass 31, fail 0".
- `### [CMD] node --test .agents/scripts/plan-approval.test.js` — `Exit: 0` — "pass 12, fail 0".
- `### [CMD] node --test .agents/scripts/review-run.test.js` — `Exit: 0` — "pass 10, fail 0".

UNVERIFIED (không chặn plan, đã có test/Manual Test bù): thuộc tính `Domain`/`Path` thật của Set-Cookie `express.sid` trên `wikicv.org`; HTML `/tim-kiem` khi đã đăng nhập. Lệnh đề xuất sau khi hiện thực: `.\gradlew.bat testDebugUnitTest --tests "com.personal.apptruyen.data.remote.auth.SourceAuthManagerTest"` và `.\gradlew.bat testDebugUnitTest --tests "com.personal.apptruyen.data.remote.WikidichScraperTest"`.

#### Phân loại lỗi

**[BLOCKER]**

- Không có.

**[MAJOR]**

- Không có.

**[MINOR]**

- m-s: `isLoggedIn` (Bước 2) chỉ kiểm tra `express.sid` dài hơn 10 ký tự. Nhưng express-session cũng cấp `express.sid` cho khách (giả định nền của Y1). Nếu `authState` khởi tạo từ `isLoggedIn`, Cài đặt sẽ hiện "Đã đăng nhập" với khách, cho tới khi một lần tìm kiếm gọi `markLoginRequired`. Khi hiện thực, nên để giá trị khởi tạo là `false` (hoặc trạng thái "chưa xác minh"), và chỉ đặt `true` sau `importCookies` Success, sau `onLoginDetected` có probe sạch, hoặc sau một request Wikidich thành công.
- m-t: `verifySessionOnline` trả `Boolean` và gộp "lỗi mạng" với "có marker" thành `false`, nên `onLoginDetected` không phân biệt được hai trường hợp. Nên trả `SessionVerifyResult` để màn đăng nhập hiện đúng thông báo lỗi kết nối, giống luồng dán cookie.
- m-u: `domains` của config gồm cả `https://wikicv.net`, nhưng `logout` và bước xóa ở `importCookies` chỉ nhắm `wikicv.org`/`.wikicv.org`. Nên xóa cả `wikicv.net`, hoặc bỏ `wikicv.net` khỏi danh sách nếu không có cookie ở đó.
- m-v: Đoạn mẫu catch ngoài ở Bước 7 có nhánh `else circuitBreaker.recordFailure(...)`, áp dụng chung cho mọi hàm. Khi chép vào `getStoriesByGenre` cần bỏ nhánh này, đúng như câu chữ m-r(b) đã ghi.
- m-w: Test ở Bước 2 dùng fake `CookieStore`, nên chỉ kiểm được thứ tự gọi xóa/ghi, chưa kiểm được ngữ nghĩa host-only và domain của `android.webkit.CookieManager`. Manual Test "Dán cookie khi đã có cookie khách" là control thật cho Y1, cần chạy trước khi đóng plan.

#### Kết luận

Revision 6 khắc phục MAJOR Y1. Nhánh `Success` của `importCookies` nay xóa cả hai biến thể `express.sid` trước khi ghi một bản host-only duy nhất, có test thứ tự và Manual Test. Revision này cũng khắc phục toàn bộ MINOR còn lại của Run 5 (m-o, m-p, m-q, m-r). Kịch bản S1–S6 và Domain đều PASS với bằng chứng `[CMD]`/`[SRC]`/`[ADV]`. Quality gate không còn thao tác có thể làm mất thay đổi chưa commit. Chỉ còn 5 MINOR (m-s … m-w), không chặn triển khai; implementer nên xử lý trong lúc code.

Kết luận: APPROVED

[PLAN_APPROVED]
