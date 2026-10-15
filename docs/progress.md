# Tiến độ phát triển

> **Cập nhật lần cuối:** 2026-10-15 21:18

## Phiên bản hiện tại: v3.0.1

## MVP Status

### ✅ Hoàn thành

- [x] **Project setup** — Gradle, dependencies, AndroidManifest (2026-02-11)
- [x] **UI: Cài đặt** — TTS speed, font, theme (2026-02-11)
- [x] **Navigation** — Bottom nav + all routes (2026-02-11)
- [x] **Feature: Auto Format** — Tự động định dạng văn bản chương truyện (2026-06-09)
- [x] **Data models** — Story, Chapter, ReadingProgress (2026-02-11)
- [x] **Room database** — 3 entities, 3 DAOs, AppDatabase (2026-02-11)
- [x] **Web scraper** — TangThuVienScraper + SsTruyenScraper + WikidichScraper (search, detail, chapter list, content) (2026-02-11 → 2026-10-13)
- [x] **Nâng cấp nguồn SsTruyen (`sstruyen.pics`)** — Chuyển từ domain cũ `sstruyen.net` sang `sstruyen.pics` (58.265 truyện, 93 thể loại), REST API danh sách chương, bộ lọc completed, sort=updatedAt, Tailwind prose, tương thích ngược 100% dữ liệu đã tải (2026-10-13)
- [x] **Khắc phục 3 lỗi vận hành nguồn SsTruyen (`sstruyen.pics`)** — Hiển thị số chương khi tìm kiếm (`dateModified`), nạp trọn vẹn 93 thể loại qua REST API `/genres`, nạp `novelId` qua `/novels/{slug}` tải đầy đủ 100% chương không bị đứt đoạn (2026-10-13)
- [x] **Tích hợp nguồn Wikidich (`wikicv.org`)** — Giải mã chữ ký fuzzySign SHA-256, phân trang AJAX 99 chương/trang, 46 thể loại, lọc watermark (2026-10-13)
- [x] **Hạ tầng xác thực nguồn truyện (Source Auth)** — Đăng nhập WebView SSO (Dịch Tiếng Hoa) + dán cookie session `express.sid`, CookieStore an toàn, banner nhắc đăng nhập Search/Explore, Settings UI (2026-10-13)
- [x] **Gỡ bỏ nguồn trực tuyến TruyenFull & TruyenCom** — Gỡ cào trực tuyến, bảo toàn toàn bộ dữ liệu đọc offline trong Room DB (2026-10-13)
- [x] **TTS: Thêm mức tốc độ** — Slider 0.5x–2.0x bước 0.05 (2026-02-11)
- [x] **TTS: Background playback** — Foreground Service + WakeLock (2026-02-11)
- [x] **TTS: Headphone controls** — MediaSession (2026-02-11)
- [x] **Offline reading** — Offline-first pattern, Room cache trước rồi web fallback (2026-02-11)
- [x] **Settings persistence** — SettingsViewModel + DataStore (2026-02-11)
- [x] **TTS: Auto-advance** — Tự chuyển chương tiếp khi đọc hết (2026-02-11)
- [x] **TTS: Auto-play** — Tự play khi chuyển chương (2026-02-11)
- [x] **TTS: Chapter nav buttons** — Nút skip = chuyển chương (2026-02-11)
- [x] **CSS selector tuning** — Verified với live HTML, reorder priority (2026-02-11)
- [ ] **Offline TTS voice check** — Dialog hướng dẫn cài giọng Việt
- [x] **Error handling** — Retry 3x + exponential backoff, HTTP status check (2026-02-11)
- [x] **Unit tests** — Scraper tests (13), repository tests (11), TTS tests (10) (2026-02-11)
- [x] **Code review & bug fixes** — 6 bugs + 6 optimizations (2026-02-11)
- [x] **WebView fallback** — JS-rendered chapter content via headless WebView (2026-02-11)

### 🔮 Nâng cấp (sau MVP)

- [x] Cache thông minh (ETag/Last-Modified) (2026-02-13)
- [x] Ảnh bìa truyện (cover image) (2026-02-13)
- [x] **Tab Khám Phá** — Truyện hoàn thành, infinity scroll, pull-to-refresh (2026-02-13)
- [x] **Lọc truyện Search** — ModalBottomSheet, 42 thể loại, sắp xếp server-side via JSON API, infinity scroll (2026-02-13)
- [x] **Server-side sort** — JSON API `/api/list/{catId}/{sortType}/{page}/25`, on-demand categoryId extraction + cache (2026-02-13)
- [x] **App Icon** — Vector drawable quyển sách mở + sóng audio, adaptive icon (2026-02-13)
- [x] **Splash Screen** — AndroidX SplashScreen API + Compose animated splash (2026-02-13)
- [x] **UX/UI Redesign** — Google Fonts, premium cards, shimmer loading, hero header, animated nav (2026-02-14)
- [x] **Full Dependency Upgrade** — Kotlin 2.3.10, BOM 2025.12.00, Coil 3, OkHttp 5, Room 2.8.4, và 10+ thư viện khác (2026-02-14)
- [x] **Tìm kiếm truyện đã tải** — Thanh search animated trong DownloadsScreen, lọc client-side theo tên (2026-02-14)
- [x] **B7: Dark Mode toàn app** — 3 chế độ (Hệ thống/Sáng/Tối), ThemeViewModel, SettingsDataStore (2026-02-14)
- [x] **C1: Image Caching** — Hilt CoilModule 50MB disk cache, shimmer placeholder, error fallback (2026-02-14)
- [x] **B6: Skeleton Loading** — ShimmerStoryDetail cho StoryDetailScreen (2026-02-14)
- [x] **C2: Lazy Chapter List** — Load 100 chương/lần, infinity scroll (2026-02-14)
- [x] **A2: Reading Progress Bar** — Progress bar + subtitle trên StoryCardPremium (2026-02-14)
- [x] **A3: Sort/Filter Home** — 3 chế độ sắp xếp: Gần đây / A→Z / Chưa xong (2026-02-14)
- [x] **B2: Pull-to-Refresh** — PullToRefreshBox trên HomeScreen (2026-02-14)
- [x] **B5: Đánh dấu chương đã đọc** — CheckCircle icon + alpha giảm (2026-02-14)
- [x] **B8: Unread Badge** — Chip "Còn X chương chưa đọc" (2026-02-14)
- [x] **B9: Chapter Jump Bottom Sheet** — FAB + search + quick nav (2026-02-14)
- [x] **Auto-navigate chương tiếp** — Scroll hết chương → tự load chương tiếp + cuộn lên đầu (2026-02-14)
- [x] **Double-swipe chuyển chương** — Overscroll 2 lần ở cuối/đầu chương → chuyển chương tiếp/trước (2026-02-14)
- [x] **Về cuối chương trước** — Khi quay về chương trước, cuộn đến cuối chương (2026-02-14)
- [x] **Fix Gradle build error** — `transformDebugUnitTestClassesWithAsm` fix via `android.newDsl=false` (2026-02-14)
- [x] **Ẩn/hiện bars khi chạm** — Tap vào nội dung chương toggle TopAppBar + TtsControlBar, animated slide (2026-02-16)
- [x] **Nút Lọc Truyện centered** — Nút ở giữa SearchScreen khi chưa tìm/lọc, chuyển icon top-right khi có kết quả (2026-02-16)
- [x] **Auto-generate cover images** — Cover URL từ CDN pattern thay vì scrape, hiện ảnh bìa ở Explore và Search (2026-02-16)
- [x] **Performance optimization** — 6 fixes: parallel library refresh, separated loadMoreJob, async detail load, capped animation, aggregate DAO, isInLibrary index (2026-02-17)
- [x] **Nút Danh sách chương** — Panel trượt từ phải, highlight chương đang đọc, auto-scroll, tương thích 3 themes (2026-02-17)
- [x] **Thay chữ tự động** — 24 regex patterns mặc định + user tự thêm từ, toggle bật/tắt, đồng bộ Reader ↔ Settings (2026-02-18)
- [x] **Chế độ Copy** — Tắt long-press TTS → cho phép select/copy text, trong Reader settings (2026-02-18)
- [x] **Font Be Vietnam Pro** — Font UI sans-serif 4 weights (Regular/Medium/SemiBold/Bold), áp dụng cho toàn bộ MaterialTheme.typography (2026-02-20)
- [x] **HomeScreen HorizontalPager** — Vuốt ngang qua lại giữa Đang đọc/Đã tải, PremiumTabRow liên kết pagerState (2026-02-20)
- [x] ~~**ReaderScreen Paged Mode**~~ — Đã xoá (không phù hợp UX) (2026-02-20)
- [x] **ReaderScreen Line Spacing** — Tuỳ chỉnh khoảng cách dòng 1.2x/1.5x/1.8x (2026-02-20)
- [x] **FAB auto-hide** — NestedScrollConnection scroll direction tracking ở StoryDetailScreen (2026-02-20)
- [x] **StoryCardPremium CircularProgress** — Circular indicator overlay góc dưới-phải ảnh bìa (2026-02-20)
- [x] **Glassmorphism overlays** — TtsControlBar + ReaderSettingsPanel translucent alpha 0.85f/0.92f (2026-02-20)
- [x] **Screen Transitions** — NavHost enter/exit animations: fade cho tabs, slide+fade cho sub-screens (2026-02-20)
- [x] **Fix version Settings** — Hardcoded "1.0" → BuildConfig.VERSION_NAME (2026-02-20)
- [x] **Edge-to-edge cleanup** — Xoá opaque status/nav bar colors, enableEdgeToEdge() đã có (2026-02-20)
- [x] **HomeScreen Pull-to-Refresh** — PullToRefreshBox wrap HorizontalPager (2026-02-20)
- [x] **Blur Hero Header** — StoryDetailScreen HeroSection blur(25dp) background cover (2026-02-20)
- [x] **Reader Brightness Control** — Slider độ sáng trong ReaderSettingsPanel, dùng Activity.window.screenBrightness (2026-02-20)
- [x] **Explore Genre Filter** — LazyRow FilterChip 42 thể loại, chọn genre lọc stories từ API (2026-02-20)
- [x] **Cleanup** — Xoá imports thừa HorizontalPager/rememberPagerState từ ReaderScreen (2026-02-20)
- [x] **TTS Pitch Control** — Slider cao độ 0.5x–2.0x trong TtsControlBar + persist DataStore (2026-02-20)
- [x] **TTS Voice Selection** — Enumerate Vietnamese voices, set voice qua TtsService/TtsManager (2026-02-20)
- [x] **Cancel Download** — Nút hủy tải trên DownloadProgressCard, Job.cancel() + ensureActive() (2026-02-20)
- [x] **Reading Statistics** — ReadingStatsEntity + DAO + StatsScreen (bar chart, streak, stat cards) (2026-02-20)
- [x] **Download Optimization** — ensureActive() trong download loop hỗ trợ cooperative cancellation (2026-02-20)
- [x] **Lọc theo số chương** — FilterBottomSheet "Số chương" (Tất cả/200+/300+/500+/900+), client-side filter 10 pages, kết hợp genre + sort (2026-02-23)
- [x] **Tái cấu trúc SearchScreen** — Gộp tất cả bộ lọc vào 1 FilterBottomSheet, giảm header ~42% → hiển thị 5-6 truyện thay vì 3, badge count, active filter chips compact (2026-03-28)
- [x] **Audit & Bug Fix đợt 1** — Fix 5 bugs: duplicate story ID đa nguồn, filter completed nhận nhầm truyện, hardcoded nguồn Settings, FilterBottomSheet logic sai, TTS error snackbar treo vĩnh viễn (2026-04-12)
- [x] **Audit Phase 2** — Smart dedup, FEAT-1 quản lý nguồn, FEAT-4 tải trước chương, FEAT-5 chọn font chữ, UI-3 reader settings redesign, UI-9 placeholder mô tả (2026-04-12)
- [x] **Per-story Text Replacement** — Thay chữ riêng từng truyện, DB v8→v9, BackHandler, snackbar feedback, UI trong ReaderSettingsPanel (2026-05-05)
- [x] **UI7: Haptic Feedback mở rộng** — Thêm haptic vào ReaderScreen (chuyển chương), HomeScreen (chuyển tab), DownloadsScreen (xác nhận xóa) (2026-06-06)
- [x] **UI9: Search Autocomplete** — Gợi ý tìm kiếm từ lịch sử khi gõ ≥ 2 ký tự, dropdown animated trong SearchScreen (2026-06-06)
- [x] **A1: Extract BaseScraper** — Tách shared HTTP/cache/retry/HTML cleanup thành BaseScraper abstract class, 4 scrapers kế thừa (2026-06-06)
- [x] **A3: Split StoryRepository** — Tách 772 dòng → StoryRepository (core) + DownloadRepository + TextReplacementRepository + ReadingProgressRepository với interfaces (2026-06-06)
- [x] **A4: MVI ReaderViewModel** — Thêm ReaderIntent sealed interface + onIntent() dispatcher, contentCache size limit (2026-06-06)
- [x] **A5: Centralize Scraper Patterns** — toChapter(), normalizeStatus(), validateCoverUrl() helpers trong BaseScraper (2026-06-06)
- [x] **F4: Backup/Restore** — Sao lưu/khôi phục toàn bộ dữ liệu app ra file ZIP qua SAF, không cần permission (2026-06-07)
- [x] **F6: Auto-scroll Reader** — Tự cuộn trang khi đọc, tốc độ 1-10x, toggle + slider trong ReaderSettingsPanel, indicator overlay, pause khi chạm (2026-06-07)
- [x] **Compose Stability** — Migrate 7 state classes sang `ImmutableList` (kotlinx-collections-immutable:0.3.8), `.toImmutableList()` tại emission sites (2026-06-07)
- [x] **LazyColumn Keys** — Explicit keys cho ExploreScreen, StoryDetailScreen, ChapterListPanel (2026-06-07)
- [x] **Baseline Profiles** — profileinstaller:1.4.1 + baseline-prof.txt cho AOT compilation (2026-06-07)
- [x] **Crash Reporting** — CrashHandler local lưu crash logs vào file, hiển thị trong Settings, sao chép/xóa (2026-06-07)
- [x] **Major Architecture Upgrades** — Media3 Session 1.11.1 (`TtsPlayer`, `TtsPlaybackStateMachine`, `MediaSessionService`), Room 3.0.3 KMP (`androidx.room3` + `BundledSQLiteDriver 2.7.1` + `DatabaseTransactionRunner`), Kotlin 2.4.21 + KSP 2.3.12 + kotlinx-serialization 1.11.0, AGP 9.4.0 + Gradle 9.6.0 (2026-10-14)
- [x] **Parallel Batch Download (Tải song song theo lô)** — Tải đồng thời 4 chương/lô (`DOWNLOAD_BATCH_SIZE = 4`), tự động sắp xếp theo `chapterNumber` tăng dần trước khi lưu Room DB và phát tiến độ, giảm 8x thời gian chờ điều tiết giữa các lô (`250ms`/lô), dừng sớm an toàn khi hết hạn phiên đăng nhập (kèm thông báo tiếng Việt nhắc đăng nhập lại) hoặc khi 3 lô liên tiếp lỗi hoàn toàn (2026-10-14)
- [x] **Từ điển Pokémon khi đọc truyện (Pokemon Image Hover / Lookup)** — Nhận diện tên Pokémon đa ngôn ngữ (Việt/Romaji, Hán-Việt + biến thể đồng nhân/convert, Anh, Trung, Nhật; 1.025 loài, 1.309 hình thái) bằng Trie Longest-Match-First trên `Dispatchers.Default`, gạch chân nét đứt màu nổi bật trên `ReaderScreen` (không ảnh hưởng chuỗi gốc TTS), chạm để mở hộp thoại xem ảnh chính thức (Coil 3), huy hiệu 18 hệ tiếng Việt, chuyển đổi hình thái (Mặc định, Siêu Cấp/Mega, Gigamax, Alola, Galar, Hisui, Paldea...) và ngôn ngữ hiển thị, mặc định BẬT (2026-10-14)

---

## Lịch sử thay đổi (chi tiết xem [changelog.md](changelog.md))

| Ngày | Thay đổi |
|---|---|
| 2026-02-11 | Khởi tạo project, implement toàn bộ MVP code |
| 2026-02-11 | Fix build: settings.gradle, Compose BOM, launcher icons |
| 2026-02-11 | Docs system: architecture, progress, changelog, agent rules |
| 2026-02-11 | TTS: highlight, long-press, speed slider, background, headphone |
| 2026-02-11 | Fix: offline reading, settings persistence (DataStore), crash fix |
| 2026-02-11 | TTS: auto-advance, auto-play, chapter nav buttons |
| 2026-02-11 | Code review: 6 bugs fixed + 6 optimizations + 34 unit tests |
| 2026-02-11 | Phase 2: WebView fallback, retry/backoff, CSS selector tuning |
| 2026-02-13 | Priority 1: Search history, Download range, Download notification |
| 2026-02-13 | Priority 3: Cover image + Smart HTTP caching (ETag/Last-Modified) |
| 2026-02-13 | Code review #3: 10 bugs fixed + 2 code smells (WebViewContentLoader, TTS race, offline refresh, notification ID, delete dialog, LRU cache, settings persist, URL Base64 encode, TtsManager guard, dead code, Log.e) |
| 2026-02-13 | Tab Khám Phá + Lọc truyện: Genre model, 3 scraper methods, ExploreScreen infinity scroll, SearchScreen ModalBottomSheet filter, 4-tab bottom nav |
| 2026-02-13 | Server-side sort: JSON API thay HTML parsing, Genre.categoryId, getCategoryId() with cache, SortOption enum (NEWEST/HOT/SELECTIVE) |
| 2026-02-13 | App Icon (sách + audio) + Splash Screen (AndroidX + Compose animated) |
| 2026-02-14 | UX/UI Redesign: Design system (Color, Type, Theme + Google Fonts), 3 shared components (ShimmerEffect, AnimatedEmptyState, StoryCardPremium), 8 screens redesigned (Home, Explore, Search, Detail, Reader, Downloads, Settings, AppNavigation) |
| 2026-02-14 | Code audit: 7 bugs fixed (CoroutineScope leak, LRU cache eviction, ConcurrentHashMap, notification icons, WakeLock timing, response body leak, download range validation). New: ApplicationScope.kt, 5 notification drawable icons |
| 2026-02-14 | Code smells & improvements: Unified TtsState, removed dead Build.VERSION check, decoupled ViewModel from Navigation, OkHttp pool tuning, Coil crossfade, retry button in ReaderScreen |
| 2026-02-14 | Round 2: TtsManager Closeable, StoryDetailViewModel inject NotificationManager, safe DB migration, withTransaction for deleteStory |
| 2026-02-14 | Compose BOM upgrade (2024.02 → 2024.09.03) + pull-to-refresh cho ExploreScreen |
| 2026-02-14 | Full dependency upgrade: Kotlin 2.3.10, BOM 2025.12.00, Coil 3.3.0, OkHttp 5.3.2, Room 2.8.4, Hilt 2.57.1, Navigation 2.9.7, compileSdk 36, KSP 2.3.5, Compose plugin, compilerOptions DSL |
| 2026-02-14 | Release preparation: R8 minify + shrink resources → APK 16.1MB → 4.28MB, ProGuard rules toàn diện |
| 2026-02-14 | Fix: FilterBottomSheet scroll (verticalScroll), settings.gradle.kts Gradle 9.2.1 compatibility |
| 2026-02-14 | Thêm tìm kiếm truyện đã tải: search bar animated + client-side filtering trong DownloadsScreen |
| 2026-02-14 | Fix: Chapter pagination — scraper crawl toàn bộ trang phân trang chương thay vì chỉ trang 1 (~55 → đúng số chương) |
| 2026-02-14 | 10 UX/UI improvements: Dark mode, image cache, skeleton loading, lazy chapters, progress bar, sort/filter, pull-to-refresh, mark read, unread badge, chapter jump FAB |
| 2026-02-14 | Auto-navigate chương tiếp khi scroll hết + fix Gradle transformDebugUnitTestClassesWithAsm |
| 2026-02-14 | Double-swipe chuyển chương: NestedScrollConnection overscroll, PrevChapterSection, scrollToEnd cho chương trước. Release v1.3.2 |
| 2026-02-16 | Ẩn/hiện bars khi chạm: Scaffold → Box layout, AnimatedVisibility bars, animated padding, tap toggle trên combinedClickable |
| 2026-02-16 | UI layout fix: LargeTopAppBar → TopAppBar (Home, Explore, Settings). Fix cover images ExploreScreen: extract coverImageUrl trong parseStoryListPage |
| 2026-02-16 | Nút Lọc Truyện centered + auto-generate coverImageUrl từ CDN pattern `covers/{id/1000}/{id}-{slug}_cover_large.jpg` trong parseStoryLink |
| 2026-02-16 | Fix build: `android.newDsl=false` cho Hilt/AGP 9 + MockWebServer 3 import migration + StoryRepositoryTest constructor update |
| 2026-02-16 | Code review #2: Fix 13 issues — categoryId cache, observer leak, WebView race, N+1 query, TtsService Hilt, TtsManager @Singleton, cleanHtml(), magic constants, Log.d logging, DB index, addedTimestamp, DownloadsVM comment. DB v3→v4 |
| 2026-02-16 | Code review #3: Fix 8 issues — NavigationUtils crash-safe decode, shimmer dark theme, Settings Int→enum, DownloadsVM N² flow→first(), source name fix, genre cache 1h TTL, scrollToEnd snapshotFlow, download adaptive delay+retry |
| 2026-02-17 | Code review #4: Fix 11 issues — P1 DownloadNotificationHelper tách ViewModel, P2 Flow collect leak TtsManager, P3 cache Regex cleanHtml, P4 WebView XSS validation, M1 unified CacheEntry, M2-M4 enum+cached Regex, M8 cancel loadJob, L1-L2 cleanup |
| 2026-02-17 | Code review #4 batch 2: M5 tách StoryDetailScreen → 6 composables, M6 tách ReaderScreen → 2 composables (ReaderContentArea, ReaderTopBar). Skip M7/L3-L5 (justified) |
| 2026-02-17 | Fix Sepia theme: text/icon không thấy rõ — thêm contentColor tường minh cho TopAppBar, TtsControlBar, speed badge, slider labels |
| 2026-02-17 | Performance optimization: 6 fixes (parallel refresh Semaphore(3), separated loadMoreJob, async chapter+progress load, capped animation delay, aggregate DAO query, isInLibrary index). DB v4→v5 |
| 2026-02-17 | Nút Danh sách chương: Panel chương trượt từ phải, nút FormatListNumbered trên TtsControlBar, highlight chương đang đọc, auto-scroll, scrim overlay, navigateToChapter() |
| 2026-02-17 | **Release v1.5**: version bump (versionCode=20), APK naming `apptruyenv15.apk`, tạo lại keystore JKS, cấu hình `applicationVariants.all`, thêm QUY TẮC RELEASE vào project-rules |
| 2026-02-18 | Fix 4 bugs sau release: scroll nhảy cuối chương (scrollToEnd flag), TTS startForeground crash Android 12+ (setContentForAutoAdvance), offline reading fail (getChapterContent try-catch), headphone skip chapter (pause thay stop) |
| 2026-02-18 | Thay chữ tự động (xóa ký tự ·•⋅ + custom rules) + Chế độ Copy (tắt long-press TTS cho phép select text) |
| 2026-02-19 | Fix headphone multi-tap (PLAY_PAUSE + HEADSETHOOK detection), notification intent restart (ACTION_MAIN + CATEGORY_LAUNCHER + singleTask). **Release v1.7** (versionCode 22) |
| 2026-02-20 | **Fix CRITICAL offline storage**: `StoryDao.insertStory(REPLACE)` + `ForeignKey(CASCADE)` xóa toàn bộ chapters đã tải khi background refresh. Fix: `@Upsert` + `insertChaptersIgnore` + safe merge logic |
| 2026-02-20 | **UX/UI Premium Upgrade**: Font Be Vietnam Pro (4 weights), HomeScreen HorizontalPager + PremiumTabRow, ~~ReaderScreen Paged Mode~~ (đã xoá), Line Spacing (1.2x-1.8x), FAB auto-hide, CircularProgress, Glassmorphism |
| 2026-02-20 | **UX/UI Batch 2**: Screen transitions (fade/slide+fade), fix version Settings, edge-to-edge cleanup, HomeScreen PullToRefreshBox, StoryDetail blur hero header |
| 2026-02-20 | **UX/UI Batch 3**: Reader brightness slider, Explore genre filter chips (42 thể loại), xoá imports thừa paged mode |
| 2026-02-20 | **Release v1.8** (versionCode 23): Screen transitions, fix version Settings, edge-to-edge, HomeScreen PullToRefresh, blur hero header, brightness control, genre filter chips, fix ExploreScreen bugs |
| 2026-02-20 | **Fix**: Chuyển chương tiếp nhảy cuối chương — LaunchedEffect scroll race condition, reset scrollToEnd flag |
| 2026-02-20 | **Fix 4 bugs** (quét toàn diện): brightness không phục hồi (DisposableEffect), LRU cache CME (accessOrder=false), seekAndPlay thiếu WakeLock/AudioFocus, scrollPosition không lưu (auto-save debounce) |
| 2026-02-20 | **5 Features mới**: TTS Pitch Control (slider + persist), TTS Voice Selection (enumerate Vietnamese voices), Cancel Download (nút hủy + ensureActive), Download Optimization (cooperative cancellation), Reading Statistics (Entity/DAO/Screen bar chart+streak). DB v5→6 |
| 2026-02-23 | **Lọc theo số chương**: ChapterCountFilter enum, FilterBottomSheet section "Số chương" (5 chips), TruyenComScraper multi-page fetch (10 pages) + client-side filter, active filter chip display |
| 2026-02-23 | **Fix**: Tìm kiếm không hiện số chương — search() dùng row-based parsing extract totalChapters + author từ HTML rows |
| 2026-02-23 | **Release v1.8.2** (versionCode 25): Lọc theo số chương, fix hiển thị số chương khi tìm kiếm |
| 2026-02-25 | **Fix CRITICAL**: Mất dữ liệu sau update — thêm MIGRATION_5_6 (reading_stats), bỏ v5 khỏi destructive fallback |
| 2026-02-25 | **Fix**: Search keyword + Full filter — TruyenComScraper.search() lọc client-side truyện Hoàn thành khi completedOnly=true |
| 2026-02-26 | **Fix CRITICAL**: TTV chapter pagination — implement AJAX `/doc-truyen/page/{id}?page={n}&limit=75&web=1` thay vì chỉ đọc HTML tĩnh (thiếu chương 76+) |
| 2026-02-28 | **Fix**: Chuyển chương tiếp nhảy cuối chương — reset `LazyListState` theo `currentChapterNumber`, đơn giản hóa scroll logic |
| 2026-03-01 | **Fix**: Chế độ Copy không hoạt động khi TTS đang bật — auto-pause TTS khi bật copy mode |
| 2026-03-08 | **Fix**: TTS dừng lâu ở dấu chấm giữa từ — TtsTextPreprocessor xử lý acronym/abbreviation/mid-word dots trước khi gửi TTS engine |
| 2026-03-28 | **Tái cấu trúc SearchScreen**: Gộp bộ lọc vào FilterBottomSheet, giảm header, badge count, active filter chips. **Release v2.1** (versionCode 30) |
| 2026-04-12 | **Thêm 2 nguồn truyện mới**: MeTruyenChuScraper (metruyenchu.com.vn) + SsTruyenScraper (sstruyen.net). Cập nhật ScraperModule (4 @IntoSet bindings). Tổng 4 nguồn |
| 2026-04-12 | **Smart Filter**: Fix 5 xung đột bộ lọc, `completedOnly` default=true, pass xuyên suốt VM→Repo→Scraper, bỏ double-filter, thống nhất Search & Explore |
| 2026-04-12 | **Audit Bug Fix**: 5 bugs — BUG-4 duplicate story ID (compound key), BUG-3 filter completed (bỏ isBlank), BUG-1 hardcoded source text, BUG-6 FilterBottomSheet hasAnyFilter, BUG-7 TTS snackbar auto-dismiss |
| 2026-04-12 | **Audit Phase 2**: Smart dedup (giữ nguồn nhiều chương nhất), quản lý nguồn (bật/tắt), tải trước chương (in-memory), chọn font (4 loại), reader settings redesign, placeholder mô tả |
| 2026-04-12 | **Fix CRITICAL MET 3 bugs**: (1) Cover image thiếu selector `.book-info-pic img` + `data-src`, (2) Chapter content JS-rendered → thêm `#vungdoc` selector + WebView fallback, (3) App crash `viewModelScope.launch` trong `onCleared()` → dùng `@ApplicationScope`. Thêm: WebView ALLOWED_DOMAINS, fix chapter URL regex cho hash pattern, normalize `-truyen-chu` slug suffix |
| 2026-04-12 | **Xóa truyện khỏi Đang Đọc**: Icon thùng rác (thay swipe-to-delete), dialog xác nhận, snackbar thông báo. Chỉ xóa reading progress — không ảnh hưởng dữ liệu đã tải |
| 2026-04-12 | **Fix CRITICAL 2 crash bugs**: (1) Duplicate key crash khi scroll infinite — đổi key sang compound `sourceId:id` tại ExploreScreen/SearchScreen/HomeScreen + thêm `deduplicateStories()` ở VM. (2) Genre load chậm — thêm `withTimeoutOrNull(15s)` per source trong `getGenreList()` |
| 2026-04-12 | **UI: Tab "Thống kê" thay "Đã tải"**: Bỏ tab "Đã tải" thừa trên Home (có screen riêng). Thêm tab "Thống kê" inline: summary cards (truyện/chương/streak), hôm nay (chương + thời gian), biểu đồ cột 7 ngày |
| 2026-04-29 | **Fix CRITICAL Search**: TC bỏ false-positive status parse, MeTruyenChu multi-page (fetch trang 2,3), SearchVM pass completedOnly=false. **Fix CRITICAL Progress**: saveScrollPosition → appScope, DetailVM observe progress Flow, final save trong onCleared |
| 2026-04-29 | **Fix chính tả** "MeTruyệnChủ" → "MêTruyệnChữ". **Fix SsTruyen** description dùng .desc-text thay og:description, cover dùng .wallpaper CSS background + og:image. **NEW DownloadService** foreground service cho download chạy nền (dataSync, WakeLock, song song TTS) |
| 2026-05-05 | **Per-story Text Replacement**: Thay chữ tự động riêng cho từng truyện. DB v8→9 (+storyReplacementsEnabled, +storyCustomReplacements). StoryDao/Repository +methods. TextReplacementHelper +applyAllReplacements() (global trước, per-story sau). ReaderSettingsPanel +section thay chữ riêng (toggle/thêm/xóa/gửi tổng). BackHandler đóng panel thay vì quay lại. Xóa StorySettingsBottomSheet khỏi StoryDetailScreen. Snackbar feedback khi gửi sang tổng. **Release v2.5** (versionCode 36) |
| 2026-05-30 | **Xóa nguồn MeTruyenChu**: metruyenchu.com/metruyenchu.com.vn đã ngừng hoạt động. Xóa MeTruyenChuScraper.kt, bỏ khỏi ScraperModule/SettingsViewModel/WebViewContentLoader. Truyện đã tải vẫn giữ nguyên trong DB. Còn 3 nguồn: TruyenCom, TàngThưViện, SsTruyện |
| 2026-05-30 | **Thêm nguồn TruyenFull** (truyenfull.today): TruyenFullScraper.kt mới, cùng engine 8cache với TruyenCom, 46 thể loại, hỗ trợ filter /hoan/ native, server-side rendered (không cần WebView). Tổng 4 nguồn: TruyenCom, TàngThưViện, SsTruyện, TruyenFull |
| 2026-05-30 | **Fix tương thích filtering đa nguồn**: (1) Toggle "Chỉ truyện Full" hoàn toàn không hoạt động — thêm `completedOnly` vào interface `StorySource.getStoriesByGenre()`, sửa cả 4 scrapers với URL pattern riêng (TruyenCom /full/, TTV fns=ht, SST ?loc=hoan-thanh, TruyenFull /hoan/), forward tham số từ Repository. (2) Genre dedup ưu tiên categoryId. (3) Normalize genre name matching. (4) `applyLocalFilters` lọc status cho keyword search |
| 2026-05-30 | **Rà soát toàn diện**: Phát hiện 17 vấn đề (0 CRITICAL, 4 HIGH, 7 MEDIUM, 6 LOW). Đã sửa 5: (1) SettingsScreen text nguồn cũ "MêTruyệnChữ" → "TruyenFull" (2) ExploreVM pullToRefresh race condition (3) SearchVM toggleCompletedFilter bỏ qua genre browse (4) SearchVM isFilterActive logic thừa (5) StoryRepositoryTest mock search sai param |
| 2026-05-30 | **Release v2.6**: Cập nhật version lên v2.6, versionCode 37. Build release APK (`apptruyenv26.apk`) |
| 2026-06-06 | **UI7: Haptic Feedback mở rộng**: ReaderScreen (chuyển chương TextHandleMove), HomeScreen (chuyển tab TextHandleMove), DownloadsScreen (xóa truyện LongPress). **UI9: Search Autocomplete**: SearchViewModel.searchSuggestions + SearchScreen dropdown gợi ý |
| 2026-06-07 | **F4: Backup/Restore**: BackupData.kt (6 @Serializable models), BackupRepository.kt (ZIP export/import), SettingsViewModel backup state, SettingsScreen SAF launchers + confirm dialog. Thêm bulk DAO methods cho 4 DAOs. ProGuard rules cho kotlinx-serialization |
| 2026-06-07 | **F6: Auto-scroll Reader**: Tự cuộn trang khi đọc, tốc độ 1-10x configurable, toggle/slider trong ReaderSettingsPanel, indicator overlay, pause on touch, DataStore persistence |
| 2026-06-07 | **Performance**: Compose Stability (7 VMs → ImmutableList), LazyColumn keys (3 fixes), Baseline Profiles (profileinstaller + baseline-prof.txt), 3 new deps (kotlinx-collections-immutable, kotlinx-serialization-json, profileinstaller) |
| 2026-06-07 | **Release v2.7** (versionCode 38): Backup/Restore (SAF), T? cu?n trang (Auto-scroll), Haptic feedback, Search Autocomplete, Compose Performance improvements |
| 2026-07-20 | **Cập nhật LacaTruyen**: Cài đặt Native Decryption logic bỏ qua WebView |
| 2026-06-09 | **Release v2.8.3** (versionCode 39): T�nh nang T? d?ng format chuong truy?n |
| 2026-07-21 | **Cập nhật nguồn Tàng Thư Viện mới**: Hỗ trợ trang tangthuvien.org (React/Next.js). Tính năng: tạo danh sách chương giả từ tổng số chương, migrate URL cũ, filter search/genre đầy đủ. |
| 2026-07-21 | **Sửa lỗi lọc và TextFormatHelper**: Cập nhật selector để tìm toàn bộ truyện và bỏ rule tự động ngắt dòng bằng dấu gạch ngang (Tàng Thư Viện) |
| 2026-10-11 | **Governance**: Adopt hệ thống rules + planner/reviewer/qa + Code Freeze Guard từ AURORA/Vikini vào `.agents/` (gộp `.agent/`), thêm `docs/plans/`, `docs/specs/`, `docs/lessons-learned.md` |
| 2026-10-12 | **Governance Audit Remediation**: Khắc phục 19 audit findings (F01–F19) và bypass bảo mật. Bổ sung plan-approval.js, review-run.js, bộ 53 unit tests pass 100%, sandbox Claude CLI không Bash, pending writes lock chống TOCTOU. |
| 2026-10-13 | **Nguồn Wikidich, Hạ tầng Source Auth, Gỡ TruyenFull & TruyenCom**: Tích hợp WikidichScraper (`wikicv.org`) kèm chữ ký fuzzySign SHA-256 + AJAX phân trang; xây dựng hạ tầng Source Authentication (WebView login forum SSO + dialog dán cookie session `express.sid`, CookieStore, banner nhắc đăng nhập); gỡ bỏ nguồn trực tuyến TruyenCom & TruyenFull nhưng bảo toàn toàn bộ dữ liệu đọc offline trong Room DB. |
| 2026-10-13 | **Nâng cấp nguồn SsTruyen (`sstruyen.pics`)**: Chuyển từ domain cũ `sstruyen.net` sang `sstruyen.pics` (kho 58.265 truyện, 82 thể loại); tích hợp REST API danh sách chương (`api.sstruyen.pics`), tự phục hồi cache `novelId`, lọc hoàn thành `status=completed`, sắp xếp mới nhất `sort=updatedAt`, nội dung Tailwind `div.prose`; tương thích ngược 100% URL chương cũ `sstruyen.net` (kế thừa 9 selector và xử lý `<br>`), bảo toàn tiêu đề và nội dung chương đã tải offline trong Room DB. |
| 2026-10-14 | **Nâng cấp kiến trúc lớn (Media3, Room 3, Kotlin 2.4, AGP 9.4)**: Chuyển TTS sang `androidx.media3:media3-session:1.11.1` (`MediaSessionService`, `TtsPlayer` kế thừa `SimpleBasePlayer`, `TtsPlaybackStateMachine` bảo toàn trạng thái auto-advance); nâng cấp DB lên `androidx.room3:3.0.3` + `sqlite-bundled:2.7.1` + `DatabaseTransactionRunner`; nâng cấp Kotlin `2.4.21`, KSP `2.3.12`, `kotlinx-serialization-json:1.11.0`, AGP `9.4.0` và Gradle `9.6.0`. |
| 2026-10-14 | **Tối ưu tốc độ tải chương song song theo lô**: `DownloadRepository.downloadChapters` tải đồng thời 4 chương/lô (`DOWNLOAD_BATCH_SIZE = 4`), sắp xếp kết quả theo `chapterNumber` tăng dần trước khi lưu tuần tự vào Room DB và phát `DownloadProgress`, giảm delay điều tiết xuống 250ms giữa các lô, fail-fast khi gặp `LoginRequiredException` (báo tiếng Việt nhắc đăng nhập lại trên `DownloadService`) hoặc sau 3 lô liên tiếp lỗi hoàn toàn. Bổ sung 11 unit tests mới (`DownloadRepositoryTest`, `DownloadServiceTest`). |
| 2026-10-14 | **Từ điển Pokémon khi đọc truyện (Pokemon Image Hover / Lookup)**: Tích hợp `pokemon_pokedex.json` (1.025 loài, 1.309 hình thái, tên Việt/Romaji, Hán-Việt + đồng nhân/convert, Anh, Trung, Nhật), bộ nhận diện Trie Longest-Match-First (`PokemonLookupHelper`, `PokemonRepository`), gạch chân nét đứt trên `ReaderScreen` (`ReaderPokemonSupport`) và hộp thoại `PokemonLookupDialog` xem ảnh chính thức (Coil 3), huy hiệu 18 hệ tiếng Việt (`PokemonTypeColors`), chuyển đổi hình thái & ngôn ngữ, mặc định BẬT trong `SettingsDataStore` (`POKEMON_LOOKUP_ENABLED = true`). Bổ sung 9 unit tests (`PokemonLookupHelperTest`, `ReaderViewModelTest`). |
| 2026-10-14 | **Release v3.0.0** (versionCode 48): Nâng cấp kiến trúc lớn (Media3 1.11.1, Room 3.0.3, Kotlin 2.4.21, AGP 9.4.0), Tải chương song song theo lô (4 chương/lô), Từ điển Pokémon khi đọc truyện (1.025 loài, 1.309 hình thái, tên Hán-Việt + đồng nhân), sửa lỗi tìm kiếm/bộ lọc Wikidich và ảnh bìa SsTruyện (`apptruyenv300.apk`). |
| 2026-10-15 | **Release v3.0.1** (versionCode 49): Khắc phục lỗi tự động ngắt dòng ở từ ghép/tên riêng có dấu gạch nối (`Spider-Man` → `Spider\n-Man`) trong `TextFormatHelper.formatChapter`, bổ sung `TextFormatHelperTest` (`apptruyenv301.apk`). |

