# Hướng dẫn đóng góp (cho AI Agent & Developer)

> **Cập nhật lần cuối:** 2026-10-19

## Coding Standards

### Kotlin
- Sử dụng Kotlin 2.4+, JVM target 17
- Tuân thủ [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html) và `.editorconfig` (ktlint)
- Dùng `data class` cho models, `sealed interface`/`sealed class` cho states và events
- Sử dụng Coroutines + Flow cho async, **KHÔNG** dùng callbacks hay RxJava
- Log bằng `Timber` (`Timber.tag(...).d/w/e`), **KHÔNG** dùng `android.util.Log` trực tiếp (ngoại trừ `CrashHandler.kt`)

### Jetpack Compose
- Mỗi screen có 2 files: `*Screen.kt` (Composable) + `*ViewModel.kt` (Hilt ViewModel)
- Dùng `collectAsStateWithLifecycle()` trong Composable, `StateFlow` và `ImmutableList` trong ViewModel
- UI text phải 100% tiếng Việt
- Sử dụng Material 3 components

### Architecture
- **MVVM + Clean Architecture** (riêng `ReaderScreen` dùng MVI)
- Data flow: Screen → ViewModel → Repository → (Scraper | Room)
- ViewModel không phụ thuộc vào Android Context (trừ TTS)
- Repository là single source of truth

## Quy trình thêm tính năng mới

0. Lập kế hoạch: `@planner` soạn plan trong `docs/plans/`, Lead Reviewer (Claude CLI / `@reviewer`) duyệt đến khi có `[PLAN_APPROVED]` (xem `.agents/rules/05-plan-review.md`). Code Freeze Guard khóa `app/` cho đến lúc đó.
1. Cập nhật `docs/progress.md` — thêm task vào "Chưa làm"
2. Tạo/sửa data models nếu cần
3. Cập nhật Room entities + DAOs + migration nếu thay đổi schema
4. Thêm methods vào Repository (`I*Repository` + implementation)
5. Tạo ViewModel + Screen
6. Cập nhật `AppNavigation.kt` nếu thêm route mới
7. Cập nhật `docs/architecture.md` nếu thay đổi cấu trúc
8. Cập nhật `docs/changelog.md`
9. Đánh dấu task là "Hoàn thành" trong `docs/progress.md`, đổi plan sang `Trạng thái: Completed`

Chi tiết coding standards đầy đủ: `.agents/rules/01-coding.md`, quality gates: `.agents/rules/02-quality.md`.

## Quy tắc đặt tên

| Loại | Pattern | Ví dụ |
|---|---|---|
| Screen | `{Feature}Screen.kt` | `HomeScreen.kt` |
| ViewModel | `{Feature}ViewModel.kt` | `HomeViewModel.kt` |
| Entity | `{Name}Entity.kt` | `StoryEntity.kt` |
| DAO | `{Name}Dao.kt` | `StoryDao.kt` |
| Domain Model | `{Name}.kt` | `Story.kt` |
| Scraper | `{Source}Scraper.kt` | `TangThuVienScraper.kt`, `SsTruyenScraper.kt`, `WikidichScraper.kt` |
| Test | `{Class}Test.kt` | `StoryRepositoryTest.kt`, `TangThuVienScraperTest.kt` |

## Testing & Quality Gates

- Unit tests: `app/src/test/` — test Scraper (`MockWebServer` + HTML fixture, tuyệt đối không gọi mạng thật và không viết hàm mirror sao chép code vào test), Repository, TtsManager, ViewModel (JUnit 5 + MockK + `kotlinx-coroutines-test` + `mockwebserver3`)
- Instrumented tests: `app/src/androidTest/` — test Room, UI
- Các lệnh kiểm chứng (PowerShell):
  ```powershell
  .\gradlew.bat ktlintCheck
  .\gradlew.bat lintDebug
  .\gradlew.bat testDebugUnitTest
  .\gradlew.bat assembleDebug
  ```
- Bug fix: chạy workflow `/post-fix` và ghi bài học vào `docs/lessons-learned.md`
