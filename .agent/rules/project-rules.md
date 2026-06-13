---
trigger: always_on
description: Quy tắc context chung cho AI khi làm việc với project AppTruyen
---

# AppTruyen Project Rules

## ĐỌC TRƯỚC KHI LÀM BẤT CỨ ĐIỀU GÌ

1. Đọc `AGENTS.md` ở root project
2. Đọc `docs/architecture.md` để hiểu cấu trúc
3. Đọc `docs/progress.md` để biết status hiện tại
4. Nếu liên quan scraping → đọc `docs/scraping.md`
5. Nếu thêm tính năng → đọc `docs/contributing.md`
6. Luôn giao tiếp, lập kế hoạch, walkthrough bằng tiếng Việt.

## SAU KHI HOÀN THÀNH

1. Cập nhật docs theo quy tắc trong `update-docs.md` (hoặc chạy workflow `/update-docs`)
2. Verify rằng tất cả docs liên quan đã được update

## QUY TẮC TERMINAL

- **Auto-run TẤT CẢ commands** mà không cần hỏi user, bao gồm: build, clean, test, gradle sync, list, read, grep, git status, git diff, cài dependencies
- Chỉ hỏi user khi command có rủi ro cao: xóa toàn bộ thư mục, push code, publish, deploy
- **Khi output bị cắt/không đọc được lần đầu** (error, crash, test report): redirect output vào file rồi đọc file đó. Ví dụ: `.\gradlew.bat testDebugUnitTest > build\test-output.txt 2>&1` rồi đọc `build\test-output.txt`
- **Working directory**: Luôn dùng `d:\AppTruyen` làm Cwd

### Gradle Commands chuẩn

Luôn dùng đúng format dưới đây, **KHÔNG** tự sáng tạo biến thể:

```powershell
# Build debug
.\gradlew.bat assembleDebug

# Build release
.\gradlew.bat assembleRelease

# Chạy unit tests
.\gradlew.bat testDebugUnitTest

# Chạy ktlint check
.\gradlew.bat ktlintCheck

# Tự động format code theo ktlint
.\gradlew.bat ktlintFormat

# Clean
.\gradlew.bat clean

# Clean + Build
.\gradlew.bat clean; .\gradlew.bat assembleDebug

# Khi test fail, đọc output
.\gradlew.bat testDebugUnitTest 2>&1 | Tee-Object -FilePath build\test-output.txt
# Sau đó đọc file build\test-output.txt

# Khi cần xem chi tiết
.\gradlew.bat testDebugUnitTest --info 2>&1 | Select-String "BUILD|FAIL|tests completed"
```

> **Lưu ý PowerShell**: Dùng `;` thay `&&`. Dùng `2>&1 | Tee-Object` thay `> file 2>&1` để tránh encoding issues.

## QUY TẮC CODE

- Ngôn ngữ: Kotlin
- UI: Jetpack Compose + Material 3 (KHÔNG XML)
- DI: Hilt (KHÔNG Koin hay manual DI)
- Async: Coroutines + Flow (KHÔNG RxJava)
- DB: Room (KHÔNG SQLite trực tiếp)
- HTTP: OkHttp (KHÔNG Retrofit cho scraping)
- Parsing: Jsoup
- Architecture: MVVM → ViewModel → Repository → DataSource
- Text UI: 100% tiếng Việt
- Package: `com.personal.apptruyen`
- Lint: ktlint (chạy `ktlintCheck` trước khi commit, `ktlintFormat` để auto-fix)
- PowerShell doesn't support &&

## QUY TẮC RELEASE

- APK release luôn đặt tên: `apptruyen` + `v` + phiên bản (bỏ dấu chấm) + `.apk`
- Ví dụ: v1.5 → `apptruyenv15.apk`, v2.0 → `apptruyenv20.apk`
- Cấu hình sẵn trong `build.gradle.kts` (`applicationVariants.all`)

## CẤU TRÚC FILE MỚI

Khi tạo screen mới:

1. `ui/{feature}/{Feature}ViewModel.kt` — StateFlow + Hilt
2. `ui/{feature}/{Feature}Screen.kt` — Composable
3. Thêm route trong `ui/navigation/AppNavigation.kt`
4. Cập nhật docs (chạy /update-docs)

Khi tạo data source mới:

1. Model trong `data/model/`
2. Entity trong `data/local/entity/`
3. DAO trong `data/local/`
4. Methods trong `data/repository/StoryRepository.kt`
5. Cập nhật `di/AppModule.kt` nếu cần
6. Cập nhật docs (chạy @update-docs)
