# Kiến trúc & Cấu trúc dự án

> **Cập nhật lần cuối:** 2026-10-23 20:25

## Tổng quan kiến trúc

```
┌─────────────────────────────────────────────────┐
│                   UI Layer                       │
│  (Jetpack Compose Screens + ViewModels)          │
│  HomeScreen, SearchScreen, StoryDetailScreen,    │
│  ReaderScreen (+ ReaderPokemonSupport Dialog),   │
│  DownloadsScreen, SettingsScreen,                │
│  SourceLoginScreen (WebView Auth + Paste Cookie) │
│  SettingsViewModel (DataStore persistence)        │
├─────────────────────────────────────────────────┤
│                Repository Layer                   │
│  IStoryRepository → StoryRepository (core)          │
│  ISourceAuthRepository → SourceAuthRepository     │
│  IDownloadRepository → DownloadRepository            │
│  ITextReplacementRepository → TextReplacementRepo    │
│  IReadingProgressRepository → ReadingProgressRepo    │
│  IPokemonRepository → PokemonRepository              │
├──────────────────────┬──────────────────────────┤
│    Remote Layer      │      Local Layer          │
│  StorySource (IF)    │   Room 3.0 Database v9    │
│  ├─ TangThuVienScrp. │   (Story/Chapter/         │
│  ├─ SsTruyenScraper  │    ReadingProgress/Stats  │
│  ├─ WikidichScraper  │    + BundledSQLiteDriver) │
│  ├─ ResilientDohDns  │   Assets:                 │
│  ├─ auth/ & cookie/  │   └─ pokemon_pokedex.json │
│  (OkHttp + Jsoup)    │                           │
├──────────────────────┴──────────────────────────┤
│              Cross-cutting Concerns               │
│   Hilt DI │ TtsService (Media3) │ DownloadService │
│   Compose Navigation │ ScraperModule              │
└─────────────────────────────────────────────────┘
```

## Cấu trúc thư mục

```
app/src/main/
├── assets/
│   └── pokemon_pokedex.json     # Từ điển 1.025 loài Pokémon (1.309 hình thái, tên Việt/Hán-Việt/Anh/Trung/Nhật + đồng nhân)
└── java/com/personal/apptruyen/
├── di/                          # Hilt DI modules
│   ├── AppModule.kt             # Provides Room 3 DB (BundledSQLiteDriver), DatabaseTransactionRunner, DAOs, CoroutineScope, CookieStore, OkHttpClient (ResilientDohDns)
│   ├── RepositoryModule.kt      # @Binds IStoryRepository, ISourceAuthRepository, IPokemonRepository
│   ├── ScraperModule.kt         # @Binds @IntoSet: TangThuVien + SsTruyen (sstruyen.pics) + Wikidich → Set<StorySource>
│   └── ApplicationScope.kt     # @Qualifier cho app-scoped CoroutineScope
├── data/
│   ├── model/                   # Domain models (không phụ thuộc Room)
│   │   ├── Story.kt
│   │   ├── Chapter.kt
│   │   ├── ChapterContentResult.kt  # Content + title trả từ scraper
│   │   ├── Genre.kt             # Thể loại truyện (name + slug + categoryId)
│   │   ├── BackupData.kt        # Serializable models cho backup/restore ZIP
│   │   ├── ReadingProgress.kt
│   │   ├── SourceAuth.kt        # SourceAuthConfig, SessionVerifyResult
│   │   └── PokemonModels.kt     # PokemonCatalog, PokemonEntry, PokemonForm, PokemonMatchRange, PokemonSelection
│   ├── local/                   # Room 3.0 database layer (androidx.room3)
│   │   ├── entity/              # Room entities (DB tables)
│   │   │   ├── StoryEntity.kt
│   │   │   ├── ChapterEntity.kt
│   │   │   ├── ReadingProgressEntity.kt
│   │   │   ├── ReadingStatsEntity.kt  # Thống kê đọc theo ngày
│   │   │   ├── SearchHistoryEntity.kt # Lịch sử tìm kiếm
│   │   │   └── DownloadStats.kt       # Data class kết quả thống kê tải
│   │   ├── StoryDao.kt                # Story CRUD + updateTotalChaptersIfGreater (đồng bộ đơn điệu tổng số chương)
│   │   ├── ChapterDao.kt              # Chiếu nhẹ (NULL AS content) cho truy vấn danh sách, phân trang cho backup
│   │   ├── ReadingProgressDao.kt
│   │   ├── ReadingStatsDao.kt         # DAO thống kê đọc
│   │   ├── SearchHistoryDao.kt        # DAO lịch sử tìm kiếm
│   │   ├── DatabaseTransactionRunner.kt # Seam thực thi withWriteTransaction nguyên tử
│   │   ├── SettingsDataStore.kt    # DataStore singleton (shared, SourceIds, getIntSafe, healCorruptedIntPreferences)
│   │   └── AppDatabase.kt          # Room 3 DB v9 + SQLiteConnection migrations
│   ├── remote/                  # Web scraping (multi-source) & auth
│   │   ├── StorySource.kt       # Interface chung cho mọi nguồn scrape
│   │   ├── BaseScraper.kt       # Abstract base — shared HTTP, cache TTL 10m (CACHE_TTL_MS), selective retry, HTTPS cover upgrade
│   │   ├── TangThuVienScraper.kt # Scrape tangthuvien.org (extends BaseScraper, customBaseUrl)
│   │   ├── SsTruyenScraper.kt    # Scrape sstruyen.pics (extends BaseScraper)
│   │   ├── WikidichScraper.kt    # Scrape wikicv.org (fuzzySign + AJAX pagination + ajaxFragmentCache + fetchTotalChaptersFromBookIndex)
│   │   ├── ResilientDohDns.kt    # DNS-over-HTTPS (Cloudflare 1.1.1.1/1.0.0.1 + Google 8.8.8.8 + System DNS fallback) vượt chặn DNS WiFi công ty
│   │   ├── ScraperException.kt  # Sealed error hierarchy (7 subtypes, LoginRequiredException, 100% thông báo tiếng Việt)
│   │   ├── ScraperCircuitBreaker.kt # Circuit breaker pattern (CLOSED/OPEN/HALF_OPEN)
│   │   ├── WebViewContentLoader.kt # Headless WebView fallback + strict isAllowedDomain + mainHandler cleanup
│   │   ├── auth/
│   │   │   └── SourceAuthManager.kt # Quản lý session cookie, verify, probe NO_COOKIES
│   │   └── cookie/
│   │       ├── CookieStore.kt       # Interface quản lý cookie
│   │       ├── AndroidCookieStore.kt # CookieManager wrapper an toàn (injectable provider + removeCookies host/wildcard)
│   │       └── WebViewCookieHandler.kt # Cookie sync & validation
│   └── repository/
│       ├── IStoryRepository.kt      # Interface — story/chapter core
│       ├── StoryRepository.kt       # Core: search, browse, detail, chapters, refresh (atomic merge, resolveSafeTotalChapters 3-way, upgradeToHttps)
│       ├── ISourceAuthRepository.kt # Interface — xác thực nguồn truyện
│       ├── SourceAuthRepository.kt  # Triển khai quản lý auth session, cookie
│       ├── IDownloadRepository.kt   # Interface — download ops
│       ├── DownloadRepository.kt    # Download: save, delete (atomic via DatabaseTransactionRunner), stats
│       ├── ITextReplacementRepository.kt  # Interface — text replacement
│       ├── TextReplacementRepository.kt   # Per-story + global text replacement
│       ├── IReadingProgressRepository.kt  # Interface — reading progress
│       ├── ReadingProgressRepository.kt   # Reading position, history
│       ├── IPokemonRepository.kt          # Interface — tra cứu tên Pokémon trong đoạn văn
│       ├── PokemonRepository.kt           # Lazy-load pokemon_pokedex.json + Mutex + Trie matcher trên Dispatchers.Default
│       ├── DownloadProgress.kt      # (top-level data class in StoryRepository.kt, processed = current + errors)
│       ├── ReadingStatsRepository.kt # Reading time, chapters read, streak (associateBy date + isActiveDay)
│       ├── SearchHistoryRepository.kt # Recent searches CRUD
│       └── BackupRepository.kt    # Backup/restore: streaming ZIP export & 2-phase import via SAF + Zip Bomb guard + withWriteTransaction
├── util/
│   ├── TextReplacementHelper.kt # Thay chữ: 24 default regex + custom JSON
│   ├── TextFormatHelper.kt      # Tự động format chương truyện (dấu câu, ngắt đoạn)
│   ├── PokemonLookupHelper.kt   # Trie Longest-Match-First nhận diện tên Pokémon + từ điển Hán-Việt/đồng nhân
│   └── CrashHandler.kt         # UncaughtExceptionHandler (catch Throwable + finally defaultHandler) → local crash log files
├── tts/
│   ├── TtsManager.kt            # Service proxy (on-demand startService + pendingPlay/pendingSeekAndPlayIndex queue)
│   ├── TtsService.kt            # Media3 MediaSessionService + DefaultMediaNotificationProvider + pendingSpeed
│   ├── TtsPlayer.kt             # Media3 SimpleBasePlayer (8 commands + timeline MediaItem)
│   ├── TtsPlaybackStateMachine.kt # State machine thuần Kotlin (auto-advance 10s timer, PausedDuringAutoAdvance, suppressNextPlay)
│   └── TtsTextPreprocessor.kt   # Xử lý text trước khi gửi TTS
├── download/
│   └── DownloadService.kt       # Foreground Service cho tải truyện (dataSync, ID=3001, song song TTS)
├── ui/
│   ├── navigation/
│   │   ├── AppNavigation.kt     # NavHost + Bottom Nav (animated, SourceLogin route, consumeWindowInsets)
│   │   └── NavigationUtils.kt   # Mã hóa/giải mã URL & route params an toàn (Base64 URL-safe + sentinel "_" + tiền tố "b64_")
│   ├── components/              # Shared UI components
│   │   ├── ShimmerEffect.kt     # Skeleton loading animations (MaterialTheme.colorScheme)
│   │   ├── AnimatedEmptyState.kt # Animated empty state
│   │   └── StoryCardPremium.kt  # Premium list & grid cards (badges WD, TTV, SST, TC, TF)
│   ├── auth/                    # Đăng nhập nguồn truyện
│   │   ├── SourceLoginScreen.kt # WebView login flow + strict host/scheme navigation guard
│   │   ├── SourceLoginViewModel.kt # StateFlow + verify session
│   │   └── PasteCookieDialog.kt # Fallback dán cookie express.sid
│   ├── home/                    # Trang chủ (premium tabs, dynamic 7-day chart, hasReadingHistory)
│   ├── explore/                 # Khám Phá (Adaptive grid minSize=150.dp, login banner, chống lặp loadMore)
│   │   ├── ExploreViewModel.kt  # Infinity scroll, pull-refresh, loginRequiredSources
│   │   └── ExploreScreen.kt
│   ├── search/                  # Tìm kiếm + bộ lọc thể loại/số chương/trạng thái (login banner, imePadding)
│   │   ├── SearchViewModel.kt   # loginRequiredSources StateFlow, browseByFilterOnly, chống lặp loadMore
│   │   └── SearchScreen.kt
│   ├── detail/                  # Chi tiết truyện (hero header)
│   │   └── DownloadNotificationHelper.kt # Tách notification logic ra khỏi VM
│   ├── reader/                  # Đọc chương + TTS controls
│   │   ├── ReaderScreen.kt      # Main reader composable (uses ReaderIntent MVI, LocalActivity)
│   │   ├── ReaderViewModel.kt   # MVI: ReaderIntent → onIntent() → state
│   │   ├── ReaderPokemonSupport.kt # ReaderParagraphItem (AnnotatedString + drawBehind gạch chân nét đứt) & PokemonLookupDialog
│   │   ├── TtsControlBar.kt     # TTS playback controls + speed/pitch
│   │   ├── ReaderSettingsPanel.kt # Font, theme, brightness, thay chữ, từ điển Pokémon, tự cuộn
│   │   ├── ChapterNavigation.kt # NextChapter + PrevChapter sections
│   │   └── ChapterListPanel.kt  # Slide-in chapter list panel (safeDrawingPadding + search filter)
│   ├── downloads/               # Quản lý tải (stats card + errorMessage Snackbar)
│   ├── settings/                # Cài đặt (grouped cards, tài khoản nguồn truyện, bảo vệ nguồn cuối cùng)
│   │   ├── SettingsScreen.kt
│   │   └── SettingsViewModel.kt # DataStore persistence, reactive getAuthState, intersect(ALL_SOURCES)
│   ├── splash/                  # Splash screen (loading, delay 800ms)
│   │   └── SplashScreen.kt      # Animated splash → navigate Home
│   ├── stats/                   # Thống kê đọc
│   │   ├── ReadingStatsViewModel.kt # Weekly data + streak + today
│   │   └── ReadingStatsScreen.kt    # Bar chart + stat cards ("Chuỗi ngày")
│   └── theme/                   # Material 3 theme + Google Fonts
│       ├── Color.kt             # Color tokens
│       ├── Theme.kt             # AppTruyenTheme composable
│       ├── ThemeViewModel.kt    # Dark mode toggle
│       ├── Type.kt              # Typography (NotoSerif + BeVietnamPro)
│       ├── ReaderThemeColors.kt # Centralized reader theme colors (3 themes)
│       └── PokemonTypeColors.kt # Token màu huy hiệu 18 hệ Pokémon & màu highlight theo 3 theme đọc truyện
├── AppTruyenApplication.kt      # Hilt Application
└── MainActivity.kt              # Single Activity + installSplashScreen()
```

### Governance & tài liệu (ngoài source)

```
.agents/                         # Hệ thống đa tác tử (adopt từ AURORA/Vikini) — xem .agents/README.md
├── rules/                       # 00-core, 01-coding, 02-quality, 03-ui, 04-scraping, 05-plan-review, 06-docs
├── agents/                      # planner/, reviewer/ (+ allowlist, evidence-bar, mental-simulation), qa/
├── workflows/                   # update-docs, debug, post-fix
├── scripts/                     # code-freeze-guard.js (+ test node:test), run-claude.ps1, claude-reviewer-prompt.md
├── hooks.json                   # PreToolUse → Code Freeze Guard
└── skills/                      # Android skills chính thức
docs/
├── plans/                       # Implementation plans (Technical Plan + Audit History), README = index + template
├── specs/                       # Spec/thiết kế
├── lessons-learned.md           # Bài học sau mỗi bug fix
└── architecture.md, progress.md, changelog.md, scraping.md, contributing.md, ...
```

## Database Schema

### stories

| Column | Type | Mô tả |
|---|---|---|
| id (PK) | TEXT | `${sourceId}_$slug` (hoặc slug.numericId / slug trần cho dữ liệu cũ) |
| slug | TEXT | URL slug |
| title | TEXT | Tên truyện |
| author | TEXT | Tác giả |
| genres | TEXT | Thể loại (comma-separated) |
| description | TEXT | Mô tả |
| url | TEXT | Full URL |
| totalChapters | INT | Tổng số chương |
| coverImageUrl | TEXT | URL ảnh bìa |
| isInLibrary | BOOL | Đã thêm vào thư viện (**indexed**) |
| addedTimestamp | LONG | Thời gian thêm (0 = chưa add, set bởi addToLibrary()) |
| sourceId | TEXT | Nguồn truyện: "tangthuvien", "sstruyen", "wikidich" (tương thích dữ liệu offline cũ từ "truyencom", "truyenfull", "metruyenchu") |
| status | TEXT | Trạng thái: "Hoàn thành", "Đang ra", etc. |
| storyReplacementsEnabled | BOOL | Bật/tắt thay chữ riêng cho truyện (default: false) |
| storyCustomReplacements | TEXT | Danh sách thay chữ riêng, JSON format giống global (default: "") |

### chapters

| Column | Type | Mô tả |
|---|---|---|
| storyId (PK) | TEXT | FK → stories.id |
| chapterNumber (PK) | INT | Số chương |
| title | TEXT | Tên chương |
| url | TEXT | Full URL |
| content | TEXT? | Nội dung (null = chưa tải) |
| isDownloaded | BOOL | Đã tải offline |
| downloadedTimestamp | LONG? | Thời gian tải |

### reading_progress

| Column | Type | Mô tả |
|---|---|---|
| storyId (PK) | TEXT | FK → stories.id |
| lastChapterNumber | INT | Chương đọc cuối |
| scrollPosition | INT | Vị trí scroll |
| lastReadTimestamp | LONG | Thời gian đọc (indexed) |

### reading_stats

| Column | Type | Mô tả |
|---|---|---|
| date (PK) | TEXT | Ngày "yyyy-MM-dd" |
| totalReadingTimeMs | LONG | Tổng ms đọc trong ngày |
| chaptersRead | INT | Số chương đọc |
| storiesRead | INT | Số truyện khác nhau |

## Luồng dữ liệu

1. **Tìm kiếm**: User → SearchVM → Repository → ∀ sources parallel → merge → List<Story>
1b. **Lọc theo thể loại**: User → SearchVM → Repository → ∀ sources.getStoriesByGenre(slug, page, 0, completedOnly) → mỗi source xử lý server-side filter riêng (TTV: status=COMPLETED, SST: status=completed, WD: status=5794f03dd7ced228f4419191) → merge → List<Story>
2. **Chi tiết**: User → DetailVM → Repository → Room cache → miss → sourceFor(url) → Web + Cache Room
3. **Đọc online**: ReaderVM → Repository → check Room cache → miss → sourceFor(url) → Web
4. **Đọc offline**: ReaderVM → Repository → check Room cache → hit → return cached content
5. **Chi tiết offline**: DetailVM → Repository → Room cache hit (offline-first, web fallback)
6. **Tải**: DetailVM → DownloadService (ForegroundService) → DownloadRepository.downloadChapters() → chia lô 4 chương (`DOWNLOAD_BATCH_SIZE = 4`) → tải song song mỗi lô (`coroutineScope` + `async`/`awaitAll`) → sắp xếp theo `chapterNumber` tăng dần (`sortedBy`) → tuần tự lưu Room DB (`saveChapterContent` + `updateChapterMetadata`) + emit `Flow<DownloadProgress>` (kèm adaptive delay giữa các lô và fail-fast khi hết hạn phiên đăng nhập / 3 lô lỗi liên tiếp)
7. **TTS**: ReaderVM → TtsManager (proxy) → TtsService (ForegroundService) → TtsTextPreprocessor → TextToSpeech API
8. **TTS ngầm**: TtsService + WakeLock + MediaSession → tai nghe controls + notification
9. **TTS auto-advance**: TtsService đọc xong → ReaderVM detect → auto load chương tiếp + play
10. **Settings**: SettingsVM ↔ DataStore ← ReaderVM đọc khi khởi tạo (tốc độ, cỡ chữ, giao diện)
11. **Auto-navigate & Scroll Restoration**: ReaderScreen `LaunchedEffect(currentChapterNumber, isLoading)` khôi phục `listState.scrollToItem(initialScrollIndex)` từ `ReadingProgress.scrollPosition` (nếu mở lại đúng chương đang đọc dở) trước khi lắng nghe `snapshotFlow { firstVisibleItemIndex }` (debounce 1s) lưu vị trí cuộn qua `ReaderIntent.SaveScrollPosition`; cuộn hết chương → `ReaderVM.loadNextChapterOnScroll()`
12. **Double-swipe**: NestedScrollConnection.onPostScroll → đếm overscroll (80dp/swipe) → 2 swipes → loadNext/PrevChapterOnScroll()
13. **Prev chapter scroll-to-end**: loadPrevChapterOnScroll() set scrollToEnd=true → ReaderScreen cuộn đến cuối chương trước
14. **Toggle bars & Brightness**: Tap paragraph → toggleBarsVisibility() → AnimatedVisibility slide overlay bars in/out (cố định `contentPadding` `64.dp`/`140.dp` chống nhảy chữ); độ sáng màn hình (`rememberSaveable` + `DisposableEffect`) quản lý ở cấp gốc `ReaderScreen`
15. **Reading Stats**: `LifecycleEventObserver` (`ON_RESUME`/`ON_PAUSE`) trên `ReaderScreen` + `ReaderVM.onCleared()` → `flushReadingSession()` ghi `recordReadingSession(durationMs)` (loại trừ thời gian tắt màn hình/background) và `recordChapterReadOnTransition()` trên cả 6 luồng chuyển chương
16. **TTS Pitch/Voice**: SettingsVM → DataStore persist → ReaderVM loadDefaultSettings (`CompletableDeferred` đồng bộ trước `loadChapter` đầu tiên) → TtsManager.setPitch/setVoice
20. **Auto-scroll**: ReaderSettingsPanel toggle → ReaderIntent.ToggleAutoScroll → LaunchedEffect loop animateScrollBy(speed*1.2f, 50ms) → tự động tắt khi chạm đáy chương (`!listState.canScrollForward`) hoặc pause on touch
21. **Backup/Restore**: SettingsScreen SAF → SettingsVM → BackupRepository.exportBackup(ZIP) / importBackup(ZIP) → metadata.json + chapters/{storyId}/{num}.txt
17. **Cancel Download**: UI cancel button → DetailVM.cancelDownload() → Job.cancel() → ensureActive() in repository
18. **Per-story Text Replacement & Raw Cache**: StoryDetailScreen / ReaderSettingsPanel → DetailVM / ReaderVM → StoryDao → Room DB (`storyReplacementsEnabled` + `storyCustomReplacements` JSON) → `ReaderVM` giữ `contentCache` văn bản thô và chạy `processRawChapterContent` (`TextFormatHelper.formatChapter` + `TextReplacementHelper.applyAllReplacements` + `splitIntoParagraphs`) trên `Dispatchers.Default`; bật/tắt format hoặc thay chữ gọi `reprocessCurrentChapter()` tức thì mà không tải lại mạng
19. **Gửi sang tổng**: ReaderSettingsPanel 🌐 → `ReaderVM.sendReplacementToGlobal()` → DataStore (global custom replacements) + cập nhật `customReplacements` trong bộ nhớ + `reprocessCurrentChapter()` + `Snackbar` tiếng Việt
22. **Từ điển Pokémon khi đọc truyện**: `ReaderVM.refreshPokemonMatches(paragraphs, enabled)` → `PokemonRepository.findMatchesInParagraphs()` (lazy-load `assets/pokemon_pokedex.json` + Trie Longest-Match-First trên `Dispatchers.Default`, không đụng chuỗi gốc gửi sang TTS) → `paragraphPokemonMatches` → `ReaderParagraphItem` (render `AnnotatedString` + gạch chân nét đứt `drawBehind`) → chạm vào span tên Pokémon gửi `ReaderIntent.SelectPokemon` → mở `PokemonLookupDialog` (cuộn dọc `verticalScroll`, ảnh chính thức qua Coil 3, huy hiệu 18 hệ tiếng Việt, chuyển đổi hình thái & ngôn ngữ Hán-Việt / Việt / Trung / Anh)

## Dependencies chính

| Dependency | Version | Mục đích |
|---|---|---|
| AGP / Gradle Wrapper | 9.4.0 / 9.6.0 | Android Gradle Plugin & Build System |
| Kotlin | 2.4.21 | Language + Compose compiler + Serialization plugins |
| Google KSP | 2.3.12 | Symbol processing (Room 3 + Hilt) |
| Compose BOM | 2026.05.01 | UI framework |
| Hilt | 2.60.1 | Dependency injection |
| Room 3 (`androidx.room3`) | 3.0.3 | KMP SQLite database (coroutine-first, KSP mandatory) |
| SQLite Bundled (`sqlite-bundled`) | 2.7.1 | Bundled SQLite driver cho Room 3 |
| OkHttp | 5.3.2 | HTTP client |
| Jsoup | 1.22.2 | HTML parsing |
| Coil | 3.4.0 | Image loading (Kotlin Multiplatform) |
| Navigation Compose | 2.9.8 | Screen navigation |
| Lifecycle | 2.10.0 | ViewModel + compose lifecycle |
| DataStore | 1.2.1 | Preferences storage |
| Coroutines | 1.11.0 | Async operations |
| AndroidX Media3 (`media3-session`) | 1.11.1 | MediaSessionService + SimpleBasePlayer + DefaultMediaNotificationProvider |
| SplashScreen | 1.2.0 | Splash screen compat (Android 6+) |
| kotlinx-serialization | 1.11.0 | JSON serialization (backup) |
| kotlinx-collections-immutable | 0.3.8 | Compose stability (ImmutableList) |
| profileinstaller | 1.4.1 | Baseline Profiles |
| ktlint (gradle plugin) | 14.2.0 | Code style enforcement (hard guardrail) |
| Timber | 5.0.1 | Structured logging (observability) |
| compileSdk / targetSdk | 36 | Android SDK compilation target |
