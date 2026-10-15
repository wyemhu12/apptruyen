---
trigger: always_on
description: Chuẩn Kotlin/Compose, xử lý lỗi, yêu cầu test, cấu trúc file, đặt tên, kích thước file và trạng thái cưỡng chế.
---

# Coding Standards (AppTruyen)

## Trạng Thái Cưỡng Chế (Enforcement Status)

| Cơ chế                         | Quy tắc                                                                                                               | Công cụ thực tế                                                                                                                       |
| :----------------------------- | :-------------------------------------------------------------------------------------------------------------------- | :------------------------------------------------------------------------------------------------------------------------------------ |
| **Máy chặn** (Hard Gate)       | Biên dịch Kotlin; ktlint (`max_line_length = 150`, indent 4, LF); unit tests pass; Android Lint                       | `.\gradlew.bat compileDebugKotlin`, `ktlintCheck` (`.editorconfig`), `testDebugUnitTest`, CI `.github/workflows/ci.yml` (ktlint → lint → test → assembleDebug) |
| **Máy hỏi** (Interactive Hook) | Chặn sửa code khi chưa có `[PLAN_APPROVED]`; khóa file nhạy cảm; chặn lệnh mutating/shell escape                      | Hook `PreToolUse` `.agents/scripts/code-freeze-guard.js` qua `.agents/hooks.json`                                                     |
| **Kỷ luật Agent & Reviewer**   | Cấm `!!` mới, cấm `GlobalScope`, không catch nuốt lỗi, test cho logic mới, UI tiếng Việt, kích thước file, minimal diffs | Lead Reviewer (Claude CLI / `@reviewer`) thẩm định plan; `@qa` nghiệm thu khi được gọi                                                  |

> ktlint đang tắt một số rule trong `.editorconfig` (`no-wildcard-imports`, `package-name`, `function-naming`, `comment-wrapping`, `kdoc-wrapping`...). KHÔNG tự bật/tắt thêm rule nếu không có plan.

## Kotlin

- Dùng `data class` cho model, `sealed interface/class` cho UI state, event và kết quả.
- Ưu tiên `val`, immutable collections. UI state dùng `ImmutableList` (kotlinx-collections-immutable) để Compose ổn định.
- Coroutines + Flow cho mọi tác vụ bất đồng bộ. Inject dispatcher khi cần test được.

<important>
CẤM trong code mới:
- `!!` (dùng `?.`, `?:`, `requireNotNull(x) { "..." }`, hoặc smart cast).
- `GlobalScope`, `runBlocking` ở main source set (chỉ dùng được trong test).
- Parse HTML hoặc IO/DB trên main thread (phải dùng `Dispatchers.IO`/`Default` hoặc suspend DAO).
- `Thread.sleep` (dùng `delay`).
</important>

## Xử Lý Lỗi

- Lỗi từ nguồn truyện đi qua `ScraperException` (`data/remote/ScraperException.kt`). Không ném `Exception` chung chung từ scraper.
- Log bằng Timber (`Timber.e(e, "...")`). KHÔNG log cookie, token, nội dung phiên đăng nhập.
- Không bao giờ hiện raw exception message (tiếng Anh) cho người dùng.

<important>
CẤM: catch block chỉ log rồi im lặng.
BẮT BUỘC: mọi hành động do người dùng kích hoạt (tải chương, xóa truyện, backup/restore, đăng nhập nguồn, phát TTS) khi thất bại phải cập nhật UI state lỗi để hiện thông báo tiếng Việt (Snackbar/Dialog/Text).
Ngoại lệ: `CancellationException` phải được ném lại, không được nuốt.
</important>

```kotlin
// Mẫu bắt buộc trong ViewModel
viewModelScope.launch {
    runCatching { repository.downloadChapters(storyId) }
        .onFailure { e ->
            if (e is CancellationException) throw e
            Timber.e(e, "downloadChapters failed")
            _uiState.update { it.copy(errorMessage = "Không tải được chương, vui lòng thử lại") }
        }
}
```

## Yêu Cầu Test

<important>
Tạo mới hoặc sửa logic trong `data/repository/`, `data/remote/` (scraper, parser, giải mã), `tts/` (tiền xử lý), `util/` hoặc ViewModel thì BẮT BUỘC thêm/cập nhật unit test trong `app/src/test/java/com/personal/apptruyen/<cùng package>/`.
</important>

- Framework: JUnit 5 (`org.junit.jupiter`), MockK, `kotlinx-coroutines-test` (`runTest`), `mockwebserver3` cho HTTP.
- Scraper: test bằng HTML fixture hoặc `MockWebServer`. KHÔNG gọi mạng thật trong unit test.
- Mỗi hàm public mới: ít nhất 1 happy path và 1 error/edge case.
- Room/UI: instrumented test trong `app/src/androidTest/` khi logic phức tạp. Composable chỉ render thì không bắt buộc test.
- Đổi schema Room: phải có test migration hoặc lý do bằng văn bản trong plan.

## Cấu Trúc File Mới

**Screen mới:**

1. `ui/{feature}/{Feature}ViewModel.kt`: `StateFlow` + `@HiltViewModel`
2. `ui/{feature}/{Feature}Screen.kt`: Composable
3. Thêm route trong `ui/navigation/AppNavigation.kt`
4. Cập nhật docs (`.agents/rules/06-docs.md`)

**Data source mới:**

1. Model trong `data/model/`
2. Entity trong `data/local/entity/`
3. DAO trong `data/local/`
4. Methods trong `data/repository/` (interface `I*Repository` + implementation)
5. Cập nhật `di/` (AppModule / RepositoryModule / ScraperModule) nếu cần
6. Cập nhật docs

## Đặt Tên

| Loại         | Pattern                 | Ví dụ                    |
| ------------ | ----------------------- | ------------------------ |
| Screen       | `{Feature}Screen.kt`    | `HomeScreen.kt`          |
| ViewModel    | `{Feature}ViewModel.kt` | `HomeViewModel.kt`       |
| Entity       | `{Name}Entity.kt`       | `StoryEntity.kt`         |
| DAO          | `{Name}Dao.kt`          | `StoryDao.kt`            |
| Domain Model | `{Name}.kt`             | `Story.kt`               |
| Scraper      | `{Source}Scraper.kt`    | `TruyenFullScraper.kt`   |
| Test         | `{Class}Test.kt`        | `StoryRepositoryTest.kt` |

Hàm/biến `camelCase`, hằng số `UPPER_SNAKE_CASE`, Composable `PascalCase`.

## Kích Thước File & Tính Mô-đun

Thước đo chính là **Single Responsibility**. Số dòng chỉ là heuristic.

| Loại file                    | Mục tiêu | Tối đa | Khi nào tách                                         |
| ---------------------------- | -------- | ------ | ---------------------------------------------------- |
| Screen / Composable          | 200–400  | 600    | > 3 khối UI độc lập → tách sub-composable            |
| ViewModel                    | 150–300  | 450    | > 1 nhóm state độc lập → tách use-case/helper        |
| Scraper                      | 200–400  | 600    | Tách parser thuần (testable) khỏi lớp gọi mạng       |
| Repository / logic `util/`   | 200–300  | 450    | > 1 domain                                           |
| Hàm                          | < 50     | 100    | Không mô tả được mục đích trong 1 câu                |

Miễn trừ: file test, file hằng số/selector, file theme. File hiện có đã vượt ngưỡng thì không tự tách nếu không có plan.

## Hardcoded Literals

- URL gốc, CSS selector, User-Agent, timeout: đặt thành hằng số trong scraper tương ứng hoặc `companion object`, KHÔNG rải rác trong hàm.
- Màu/kích thước UI: dùng `MaterialTheme` và token trong `ui/theme/` (xem `03-ui.md`).
