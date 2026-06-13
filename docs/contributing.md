# Hướng dẫn đóng góp (cho AI Agent & Developer)

> **Cập nhật lần cuối:** 2026-02-11

## Coding Standards

### Kotlin
- Sử dụng Kotlin 1.9+, JVM target 17
- Tuân thủ [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Dùng `data class` cho models, `sealed class` cho states
- Sử dụng Coroutines + Flow cho async, **KHÔNG** dùng callbacks

### Jetpack Compose
- Mỗi screen có 2 files: `*Screen.kt` (Composable) + `*ViewModel.kt` (Hilt ViewModel)
- Dùng `collectAsState()` trong Composable, `StateFlow` trong ViewModel
- UI text phải 100% tiếng Việt
- Sử dụng Material 3 components

### Architecture
- **MVVM + Clean Architecture**
- Data flow: Screen → ViewModel → Repository → (Scraper | Room)
- ViewModel không phụ thuộc vào Android Context (trừ TTS)
- Repository là single source of truth

## Quy trình thêm tính năng mới

1. Cập nhật `docs/progress.md` — thêm task vào "Chưa làm"
2. Tạo/sửa data models nếu cần
3. Cập nhật Room entities + DAOs + migration nếu thay đổi schema
4. Thêm methods vào Repository
5. Tạo ViewModel + Screen
6. Cập nhật `AppNavigation.kt` nếu thêm route mới
7. Cập nhật `docs/architecture.md` nếu thay đổi cấu trúc
8. Cập nhật `docs/changelog.md`
9. Đánh dấu task là "Hoàn thành" trong `docs/progress.md`

## Quy tắc đặt tên

| Loại | Pattern | Ví dụ |
|---|---|---|
| Screen | `{Feature}Screen.kt` | `HomeScreen.kt` |
| ViewModel | `{Feature}ViewModel.kt` | `HomeViewModel.kt` |
| Entity | `{Name}Entity.kt` | `StoryEntity.kt` |
| DAO | `{Name}Dao.kt` | `StoryDao.kt` |
| Domain Model | `{Name}.kt` | `Story.kt` |

## Testing

- Unit tests: `app/src/test/` — test Scraper, Repository, TtsManager
- Instrumented tests: `app/src/androidTest/` — test Room, UI
- Test commands:
  ```bash
  ./gradlew testDebugUnitTest
  ./gradlew connectedDebugAndroidTest
  ```
