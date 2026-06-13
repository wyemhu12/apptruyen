# Kiến trúc & Cấu trúc dự án

> **Cập nhật lần cuối:** 2026-06-13 14:50

## Tổng quan kiến trúc

```
┌─────────────────────────────────────────────────┐
│                   UI Layer                       │
│  (Jetpack Compose Screens + ViewModels)          │
│  HomeScreen, SearchScreen, StoryDetailScreen,    │
│  ReaderScreen, DownloadsScreen, SettingsScreen    │
│  SettingsViewModel (DataStore persistence)        │
├─────────────────────────────────────────────────┤
│                Repository Layer                   │
│  IStoryRepository → StoryRepository (core)          │
│  IDownloadRepository → DownloadRepository            │
│  ITextReplacementRepository → TextReplacementRepo    │
│  IReadingProgressRepository → ReadingProgressRepo    │
├──────────────────────┬──────────────────────────┤
│    Remote Layer      │      Local Layer          │
│  StorySource (IF)    │   Room Database v9        │
│  ├─ TruyenComScraper │   (Story/Chapter/         │
│  ├─ TangThuVienScrp. │    ReadingProgress/Stats)  │
│  ├─ SsTruyenScraper  │                            │
│  ├─ TruyenFullScrpr. │                            │
│  (OkHttp + Jsoup)    │                            │
├──────────────────────┴──────────────────────────┤
│              Cross-cutting Concerns               │
│   Hilt DI │ TtsService │ DownloadService          │
│   Compose Navigation │ ScraperModule              │
└─────────────────────────────────────────────────┘
```

## Cấu trúc thư mục

```
app/src/main/java/com/personal/apptruyen/
├── di/                          # Hilt DI modules
│   ├── AppModule.kt             # Provides Room DB & DAOs, CoroutineScope
│   ├── RepositoryModule.kt      # @Binds IStoryRepository → StoryRepository
│   ├── ScraperModule.kt         # @Binds @IntoSet: TruyenCom + TangThuVien + SsTruyen + TruyenFull → Set<StorySource>
│   └── ApplicationScope.kt     # @Qualifier cho app-scoped CoroutineScope
├── data/
│   ├── model/                   # Domain models (không phụ thuộc Room)
│   │   ├── Story.kt
│   │   ├── Chapter.kt
│   │   ├── Genre.kt             # Thể loại truyện (name + slug + categoryId)
│   │   ├── BackupData.kt        # Serializable models cho backup/restore ZIP
│   │   └── ReadingProgress.kt
│   ├── local/                   # Room database layer
│   │   ├── entity/              # Room entities (DB tables)
│   │   │   ├── StoryEntity.kt
│   │   │   ├── ChapterEntity.kt
│   │   │   ├── ReadingProgressEntity.kt
│   │   │   ├── ReadingStatsEntity.kt  # Thống kê đọc theo ngày
│   │   │   ├── SearchHistoryEntity.kt # Lịch sử tìm kiếm
│   │   │   └── DownloadStats.kt       # Data class kết quả thống kê tải
│   │   ├── StoryDao.kt
│   │   ├── ChapterDao.kt
│   │   ├── ReadingProgressDao.kt
│   │   ├── ReadingStatsDao.kt         # DAO thống kê đọc
│   │   ├── SearchHistoryDao.kt        # DAO lịch sử tìm kiếm
│   │   ├── SettingsDataStore.kt    # DataStore singleton (shared)
│   │   └── AppDatabase.kt
│   ├── remote/                  # Web scraping (multi-source)
│   │   ├── StorySource.kt       # Interface chung cho mọi nguồn scrape
│   │   ├── BaseScraper.kt       # Abstract base — shared HTTP, cache, retry, HTML cleanup
│   │   ├── TruyenComScraper.kt  # Scrape truyencom.com (extends BaseScraper)
│   │   ├── TangThuVienScraper.kt # Scrape truyen.tangthuvien.vn (extends BaseScraper)
│   │   ├── SsTruyenScraper.kt    # Scrape sstruyen.net (extends BaseScraper)
│   │   ├── TruyenFullScraper.kt  # Scrape truyenfull.today (extends BaseScraper)
│   │   ├── ScraperException.kt  # Sealed error hierarchy (6 subtypes)
│   │   ├── ScraperCircuitBreaker.kt # Circuit breaker pattern (CLOSED/OPEN/HALF_OPEN)
│   │   └── WebViewContentLoader.kt # Headless WebView fallback
│   └── repository/
│       ├── IStoryRepository.kt      # Interface — story/chapter core
│       ├── StoryRepository.kt       # Core: search, browse, detail, chapters, refresh
│       ├── IDownloadRepository.kt   # Interface — download ops
│       ├── DownloadRepository.kt    # Download: save, delete, stats
│       ├── ITextReplacementRepository.kt  # Interface — text replacement
│       ├── TextReplacementRepository.kt   # Per-story + global text replacement
│       ├── IReadingProgressRepository.kt  # Interface — reading progress
│       ├── ReadingProgressRepository.kt   # Reading position, history
│       ├── DownloadProgress.kt      # (top-level data class in StoryRepository.kt)
│       ├── ReadingStatsRepository.kt # Reading time, chapters read, streak
│       ├── SearchHistoryRepository.kt # Recent searches CRUD
│       └── BackupRepository.kt    # Backup/restore: ZIP export/import via SAF
├── util/
│   ├── TextReplacementHelper.kt # Thay chữ: 24 default regex + custom JSON
│   ├── TextFormatHelper.kt      # Tự động format chương truyện (dấu câu, ngắt đoạn)
│   └── CrashHandler.kt         # UncaughtExceptionHandler → local crash log files
├── tts/
│   ├── TtsManager.kt            # Service proxy (API for UI)
│   ├── TtsService.kt            # Foreground Service + MediaSession (mediaPlayback, ID=1001)
│   └── TtsTextPreprocessor.kt   # Xử lý text trước khi gửi TTS
├── download/
│   └── DownloadService.kt       # Foreground Service cho tải truyện (dataSync, ID=3001, song song TTS)
├── ui/
│   ├── navigation/
│   │   └── AppNavigation.kt     # NavHost + Bottom Nav (animated)
│   ├── components/              # Shared UI components
│   │   ├── ShimmerEffect.kt     # Skeleton loading animations
│   │   ├── AnimatedEmptyState.kt # Animated empty state
│   │   └── StoryCardPremium.kt  # Premium list & grid cards
│   ├── home/                    # Trang chủ (premium tabs)
│   ├── explore/                 # Khám Phá (2-column grid)
│   │   ├── ExploreViewModel.kt  # Infinity scroll, pull-refresh
│   │   └── ExploreScreen.kt
│   ├── search/                  # Tìm kiếm + bộ lọc thể loại
│   ├── detail/                  # Chi tiết truyện (hero header)
│   │   └── DownloadNotificationHelper.kt # Tách notification logic ra khỏi VM
│   ├── reader/                  # Đọc chương + TTS controls
│   │   ├── ReaderScreen.kt      # Main reader composable (uses ReaderIntent MVI)
│   │   ├── ReaderViewModel.kt   # MVI: ReaderIntent → onIntent() → state
│   │   ├── TtsControlBar.kt     # TTS playback controls + speed/pitch
│   │   ├── ReaderSettingsPanel.kt # Font, theme, brightness, thay chữ, tự cuộn
│   │   ├── ChapterNavigation.kt # NextChapter + PrevChapter sections
│   │   └── ChapterListPanel.kt  # Slide-in chapter list panel
│   ├── downloads/               # Quản lý tải (stats card)
│   ├── settings/                # Cài đặt (grouped cards)
│   │   ├── SettingsScreen.kt
│   │   └── SettingsViewModel.kt # DataStore persistence
│   ├── splash/                  # Splash screen (loading)
│   │   └── SplashScreen.kt      # Animated splash → navigate Home
│   ├── stats/                   # Thống kê đọc
│   │   ├── ReadingStatsViewModel.kt # Weekly data + streak + today
│   │   └── ReadingStatsScreen.kt    # Bar chart + stat cards
│   └── theme/                   # Material 3 theme + Google Fonts
│       ├── Color.kt             # Color tokens
│       ├── Theme.kt             # AppTruyenTheme composable
│       ├── ThemeViewModel.kt    # Dark mode toggle
│       ├── Type.kt              # Typography (NotoSerif + BeVietnamPro)
│       └── ReaderThemeColors.kt # Centralized reader theme colors (3 themes)
├── AppTruyenApplication.kt      # Hilt Application
└── MainActivity.kt              # Single Activity + installSplashScreen()
```

## Database Schema

### stories

| Column | Type | Mô tả |
|---|---|---|
| id (PK) | TEXT | slug.numericId (truyencom) hoặc slug (tangthuvien) |
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
| sourceId | TEXT | Nguồn truyện: "truyencom", "tangthuvien", "sstruyen", "truyenfull" (default: truyencom). Truyện cũ từ "metruyenchu" vẫn được giữ lại |
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
1b. **Lọc theo thể loại**: User → SearchVM → Repository → ∀ sources.getStoriesByGenre(slug, page, 0, completedOnly) → mỗi source xử lý server-side filter riêng (TC: /full/, TTV: fns=ht, SST: ?loc=hoan-thanh, TF: /hoan/) → merge → List<Story>
2. **Chi tiết**: User → DetailVM → Repository → Room cache → miss → sourceFor(url) → Web + Cache Room
3. **Đọc online**: ReaderVM → Repository → check Room cache → miss → sourceFor(url) → Web
4. **Đọc offline**: ReaderVM → Repository → check Room cache → hit → return cached content
5. **Chi tiết offline**: DetailVM → Repository → Room cache hit (offline-first, web fallback)
6. **Tải**: DetailVM → Repository.downloadChapters() → Flow<Progress> → sourceFor(url) + Room.save()
7. **TTS**: ReaderVM → TtsManager (proxy) → TtsService (ForegroundService) → TtsTextPreprocessor → TextToSpeech API
8. **TTS ngầm**: TtsService + WakeLock + MediaSession → tai nghe controls + notification
9. **TTS auto-advance**: TtsService đọc xong → ReaderVM detect → auto load chương tiếp + play
10. **Settings**: SettingsVM ↔ DataStore ← ReaderVM đọc khi khởi tạo (tốc độ, cỡ chữ, giao diện)
11. **Auto-navigate**: ReaderScreen detect scroll cuối → ReaderVM.loadNextChapterOnScroll() → load chapter + LazyListState reset (remember key=chapterNumber)
12. **Double-swipe**: NestedScrollConnection.onPostScroll → đếm overscroll (80dp/swipe) → 2 swipes → loadNext/PrevChapterOnScroll()
13. **Prev chapter scroll-to-end**: loadPrevChapterOnScroll() set scrollToEnd=true → ReaderScreen cuộn đến cuối chương trước
14. **Toggle bars**: Tap paragraph → toggleBarsVisibility() → AnimatedVisibility slide bars in/out, animated content padding
15. **Reading Stats**: ReaderVM.onCleared() → recordReadingSession(durationMs) → ReadingStatsDao.upsertStats()
16. **TTS Pitch/Voice**: SettingsVM → DataStore persist → ReaderVM loadDefaultSettings → TtsManager.setPitch/setVoice
20. **Auto-scroll**: ReaderSettingsPanel toggle → ReaderIntent.ToggleAutoScroll → LaunchedEffect loop animateScrollBy(speed*1.2f, 50ms) → pause on touch
21. **Backup/Restore**: SettingsScreen SAF → SettingsVM → BackupRepository.exportBackup(ZIP) / importBackup(ZIP) → metadata.json + chapters/{storyId}/{num}.txt
17. **Cancel Download**: UI cancel button → DetailVM.cancelDownload() → Job.cancel() → ensureActive() in repository
18. **Per-story Text Replacement**: StoryDetailScreen ⚙️ → DetailVM → Repository → StoryDao → Room DB (storyReplacementsEnabled + storyCustomReplacements JSON) → ReaderVM load khi init → TextReplacementHelper.applyAllReplacements(global trước, per-story sau)
19. **Gửi sang tổng**: StorySettingsBottomSheet 🌐 → DetailVM → Repository.sendReplacementToGlobal() → DataStore (global custom replacements)

## Dependencies chính

| Dependency | Version | Mục đích |
|---|---|---|
| Kotlin | 2.3.10 | Language + Compose compiler plugin |
| Compose BOM | 2025.12.00 | UI framework |
| Hilt | 2.59.2 | Dependency injection |
| Room | 2.8.4 | SQLite database |
| OkHttp | 5.3.2 | HTTP client |
| Jsoup | 1.22.2 | HTML parsing |
| Coil | 3.4.0 | Image loading (Kotlin Multiplatform) |
| Navigation Compose | 2.9.8 | Screen navigation |
| Lifecycle | 2.10.0 | ViewModel + compose lifecycle |
| DataStore | 1.2.1 | Preferences storage |
| Coroutines | 1.11.0 | Async operations |
| AndroidX Media | 1.7.1 | MediaSession + notification |
| SplashScreen | 1.2.0 | Splash screen compat (Android 6+) |
| kotlinx-serialization | 1.8.1 | JSON serialization (backup) |
| kotlinx-collections-immutable | 0.3.8 | Compose stability (ImmutableList) |
| profileinstaller | 1.4.1 | Baseline Profiles |
| ktlint (gradle plugin) | 14.2.0 | Code style enforcement (hard guardrail) |
| compileSdk | 36 | Android SDK compilation target |
