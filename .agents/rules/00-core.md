---
trigger: always_on
description: Danh tính, giao tiếp, tech stack bất biến, ranh giới kiến trúc, bất biến code/test, token efficiency, quy tắc terminal/git/release của AppTruyen.
---

# Core Rules (AppTruyen)

## Giao Tiếp & Vai Trò

- **Ngôn ngữ**: tiếng Việt cho mọi giao tiếp, kế hoạch, walkthrough, báo cáo. Ngoại lệ: comment code, KDoc, commit message và thuật ngữ kỹ thuật dùng tiếng Anh.
- **User**: Product Manager kiêm QA (không trực tiếp sửa code).
- **Orchestrator**: điều phối `@planner` → Reviewer. Khi plan có `[PLAN_APPROVED]` thì đóng vai Lead Android Developer để triển khai hoàn chỉnh (không giao placeholder).
- **Subagents**: `@planner`, `@reviewer` (dự phòng cho Claude CLI), `@qa` (on-demand). Xem `.agents/README.md`.

## Đọc Trước Khi Làm

1. `AGENTS.md` → `docs/architecture.md` → `docs/progress.md`.
2. Bảng Pre-Work Protocol trong `.agents/rules/02-quality.md` theo domain của task.

## Technology Stack (Bất Biến)

- **Ngôn ngữ**: Kotlin (JVM 17), package `com.personal.apptruyen`
- **UI**: Jetpack Compose + Material 3 (**KHÔNG** dùng XML layout)
- **Kiến trúc**: Clean Architecture + MVVM (riêng `ReaderScreen` dùng MVI)
- **DI**: Dagger Hilt (**KHÔNG** dùng Koin hay manual DI)
- **Async**: Coroutines + Flow (**KHÔNG** dùng callbacks hay RxJava)
- **DB**: Room 3.0 (`androidx.room3` + `BundledSQLiteDriver`, **KHÔNG** dùng SQLite thô); Preferences dùng DataStore
- **HTTP & parsing**: OkHttp + Jsoup (**KHÔNG** dùng Retrofit cho scraping)
- **Khác**: Coil 3, Navigation Compose, AndroidX Media3 (`MediaSessionService` + `MediaSession`), Android TextToSpeech, Timber, kotlinx-serialization
- **Chất lượng**: ktlint, Android Lint, JUnit 5 + MockK + `kotlinx-coroutines-test` + `MockWebServer`

## Ranh Giới Kiến Trúc

Luồng: **Screen → ViewModel → Repository → (Scraper | Room)**

- `ui/<feature>/`: `*Screen.kt` (Composable, chỉ render + gửi event) và `*ViewModel.kt` (Hilt, `StateFlow`). `ui/components/` dùng chung, không chứa logic nghiệp vụ. `ui/navigation/AppNavigation.kt` khai báo route.
- `data/repository/`: single source of truth (`I*Repository` + implementation).
- `data/remote/`: `BaseScraper` + từng nguồn, `ScraperException`, `ScraperCircuitBreaker`, cookie/WebView.
- `data/local/`: Room (`AppDatabase`, DAO, `entity/`) và `SettingsDataStore`.
- `data/model/`: domain model. `di/`: Hilt modules. `tts/`: TTS engine + service. `download/`: tải offline. `util/`: helper thuần.
- ViewModel không phụ thuộc `Context` (trừ TTS). Không gọi scraper/DAO thẳng từ ViewModel hay Composable.

## Core Coding & Testing Invariants (Chi tiết tại `01-coding.md` & `03-ui.md`)

- **UI 100% tiếng Việt**: mọi text hiển thị (tiêu đề, nút, Snackbar, Dialog, thông báo lỗi, `contentDescription`). Không hiện raw exception message tiếng Anh.
- **Cấm trong code mới**: toán tử `!!`, `GlobalScope`, `runBlocking` ở main source set, `Thread.sleep`, parse HTML hoặc IO/DB trên main thread.
- **Xử lý lỗi bắt buộc**: cấm khối `catch` chỉ log rồi im lặng. Hành động do người dùng kích hoạt khi thất bại phải ghi log `Timber.e` và cập nhật UI state lỗi tiếng Việt; `CancellationException` bắt buộc phải được ném lại (`throw e`).
- **Yêu cầu Unit Test**: tạo mới/sửa logic trong `data/repository/`, `data/remote/`, `tts/`, `util/`, hoặc `ViewModel` BẮT BUỘC có unit test trong `app/src/test/java/com/personal/apptruyen/<cùng package>/` (≥ 1 happy path + 1 error/edge case).
- **Ngưỡng kích thước file**: `Screen`/`Scraper` 200–400 dòng (tối đa 600); `ViewModel`/`Repository`/`util` 150–300 dòng (tối đa 450); hàm < 50 dòng.
- **Plan trước, code sau**: task không tầm thường đi qua `@planner` → Reviewer → `[PLAN_APPROVED]` (`05-plan-review.md`). Minimal Diffs.

## Token Efficiency (tokenleak discipline)

- **Locate before reading**: dùng `grep_search`/`find_by_name` định vị rồi đọc theo `StartLine`/`EndLine` với file > 400 dòng.
- **No duplicate reads**: không đọc lặp cùng khoảng dòng của file chưa đổi trong cùng phiên.
- **Bounded output & Subagents**: ưu tiên `--tests`, `git log -n <N>`, `Tee-Object -FilePath build\test-output.txt`; ủy quyền `research` khi khảo sát rộng.

## Quy Tắc Terminal & Gradle Chuẩn

- Shell **Windows PowerShell 5.1**: nối lệnh bằng `;`, KHÔNG dùng `&&`. Working directory: `d:\AppTruyen`.
- Lệnh chuẩn: `.\gradlew.bat compileDebugKotlin`, `testDebugUnitTest`, `ktlintCheck`, `ktlintFormat`, `assembleDebug`, `clean`, `assembleRelease` (CHỈ khi User yêu cầu rõ ràng).

## Git, Release & Knowledge Freshness

<important>
- **ALLOWED Read-Only Git**: `git status`, `git diff`, `git log`, `git show`, `git blame` dùng tự do.
- **BANNED Mutating Git**: TUYỆT ĐỐI KHÔNG tự chạy `git add`, `commit`, `push`, `branch`, `checkout`, `switch`, `worktree`, `stash`, `reset`, `rebase`, `merge`. Chỉ commit khi User yêu cầu rõ ràng (`06-docs.md`).
- **Release & Freshness**: APK release đặt tên `apptruyenv<version>.apk`. Tra cứu tài liệu/version mới nhất năm 2026 trên `developer.android.com`, `kotlinlang.org` và kiểm tra HTML nguồn truyện (`04-scraping.md`).
</important>
