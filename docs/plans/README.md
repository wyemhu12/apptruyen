# Kế Hoạch Triển Khai (`docs/plans/`)

Thư mục này lưu các kế hoạch triển khai (Implementation Plans) cho mọi task tính năng, refactor hoặc sửa lỗi phức tạp của **AppTruyen**. Plan do subagent `@planner` soạn và Lead Reviewer (Claude Code CLI, hoặc `@reviewer` khi CLI không dùng được) phê duyệt trước khi code. Quy trình chi tiết: [`.agents/rules/05-plan-review.md`](../../.agents/rules/05-plan-review.md).

Spec/thiết kế (nếu có) đặt ở [`docs/specs/`](../specs/).

## Quy Định Đặt Tên

- Định dạng: `YYYY-MM-DD-<task-name>-implementation-plan.md`
- Ví dụ: `2026-10-11-source-login-wikidich-implementation-plan.md`

## Code Freeze & Trạng Thái Phê Duyệt

- Code Freeze Guard (`.agents/scripts/code-freeze-guard.js`) quản lý trạng thái đóng băng code dựa trên tất cả các plan trong thư mục này (trừ `README.md`).
- Codebase được mở khóa khi và chỉ khi:
  1. KHÔNG có plan nào ở trạng thái blocking (`Draft`, `Ready for Review`, hoặc thiếu header/thẻ không hợp lệ).
  2. Có ít nhất một plan ở trạng thái mở khóa: header là `Approved` hoặc `In Progress`, và dòng không trống cuối cùng là `[PLAN_APPROVED]`.
- Plan kết thúc được chuyển sang `Completed`, plan hủy/thay thế chuyển sang `Superseded` (trạng thái đóng, không gây freeze).
- Kiểm tra trạng thái mở khóa độc lập qua lệnh:
  ```powershell
  node .agents/scripts/plan-approval.js status
  ```

## Bảng Trạng Thái

| Ngày       | Kế hoạch                                                                                                   | Trạng thái   | Tóm tắt                                                                                     |
| :--------- | :--------------------------------------------------------------------------------------------------------- | :----------: | :------------------------------------------------------------------------------------------ |
| 2026-10-11 | [Khắc phục audit governance .agents/](./2026-10-11-governance-audit-remediation-implementation-plan.md)     | `Completed`  | Khắc phục toàn bộ 19 audit findings của Claude CLI và các lỗ hổng bảo mật/vòng đời.         |
| 2026-10-11 | [Đăng nhập nguồn truyện & WikidichScraper](./2026-10-11-source-login-wikidich-implementation-plan.md)      | `Superseded` | Đóng theo quyết định D3; tính năng đăng nhập Wikidich sẽ được lập plan mới sau.              |
| 2026-10-13 | [Đăng nhập nguồn, Wikidich & Gỡ TruyenFull, TruyenCom](./2026-10-13-source-auth-wikidich-remove-truyenfull-truyencom-implementation-plan.md) | `Completed`  | Tích hợp Wikidich, hạ tầng Source Auth (WebView + Paste Cookie), gỡ TruyenFull & TruyenCom. |
| 2026-10-13 | [Nâng cấp nguồn SsTruyen sang sstruyen.pics](./2026-10-13-sstruyen-pics-migration-implementation-plan.md) | `Completed`  | Chuyển tên miền sang sstruyen.pics (58.265 truyện), REST API danh sách chương, bộ lọc completed, sort=updatedAt, Tailwind prose, tương thích ngược 100% dữ liệu đã tải. |
| 2026-10-13 | [Sửa 3 lỗi nguồn SsTruyen (Số chương, Bộ lọc, Danh sách chương)](./2026-10-13-sstruyen-search-filter-chapters-fix-implementation-plan.md) | `Completed`  | Bổ sung dateModified span hiển thị số chương tìm kiếm, nạp trọn vẹn 93 thể loại qua REST API `/genres`, nạp `novelId` qua `/novels/{slug}` tải đầy đủ 100% chương không bị đứt đoạn. |
| 2026-10-13 | [Nâng cấp kiến trúc lớn (Media3, Room 3, Kotlin 2.4, AGP 9.4)](./2026-10-13-major-architecture-upgrades-implementation-plan.md) | `Completed`  | Nâng cấp toàn diện: Media3 Session (1.11.1), Room 3.0 KMP (3.0.3), Kotlin 2.4.21, AGP 9.4.0 và Gradle 9.6.0. |
| 2026-10-14 | [Tối ưu tốc độ tải chương truyện song song theo lô](./2026-10-14-parallel-chapter-download-implementation-plan.md) | `Completed` | Tải song song 4 chương/lô (bounded concurrency), tự động sắp xếp theo `chapterNumber` tăng dần trước khi lưu Room DB và phát tiến độ. |
| 2026-10-14 | [Tra cứu hình ảnh & hệ Pokémon khi đọc truyện](./2026-10-14-pokemon-image-hover-implementation-plan.md) | `Completed` | Nhận diện tên Pokémon đa ngôn ngữ (Việt/Romaji, Hán-Việt + đồng nhân, Anh, Trung, Nhật) bằng Trie longest-match-first, gạch chân nét đứt trên `ReaderScreen`, chạm mở hộp thoại xem ảnh/hệ/hình thái/ngôn ngữ, mặc định BẬT. |
| 2026-10-16 | [Điều tiết tốc độ tải Wikidich & Vượt chặn DNS WiFi công ty bằng DoH](./2026-10-16-wikidich-rate-limit-and-doh-implementation-plan.md) | `Completed` | Tải tuần tự 1 chương/lần kèm delay ngẫu nhiên 15–20s/chương và Fail-Fast trên HTTP 403/429/503 cho Wikidich; tích hợp `ResilientDohDns` (Cloudflare + Google DoH + System DNS fallback) vào `OkHttpClient`. |
| 2026-10-16 | [Khắc phục toàn diện Audit Phase 1 (Data Integrity, Backup/Restore, Services, TTS & Coroutines)](./2026-10-16-audit-remediation-phase1-implementation-plan.md) | `Completed` | Streaming ZIP backup/restore + Zip Bomb guard + `withWriteTransaction`, sửa `SettingsKeys.restoreSettingFromBackup`, sửa `getReadingStreak`, chiếu nhẹ `ChapterDao`, bảo vệ vòng đời & `onTimeout` `DownloadService`, đồng bộ `TtsService`, rethrow `CancellationException`. |
| 2026-10-16 | [Khắc phục toàn diện Audit Phase 2 (Reader, StoryDetail & TextFormatHelper)](./2026-10-16-audit-remediation-phase2-implementation-plan.md) | `Completed` | Khôi phục & bảo toàn `scrollPosition`, giữ độ sáng ở cấp gốc `ReaderScreen`, chặn crash `DownloadRangeDialog`, đồng bộ `settingsLoadedDeferred` + hủy `loadChapterJob` + giữ `contentCache` thô trên `Dispatchers.Default`, thống kê đọc theo `ON_RESUME`/`ON_PAUSE`, cố định `contentPadding`, `SelectionContainer` toàn danh sách, tìm kiếm chương `ChapterListPanel` & bảo vệ số thập phân/giờ/URL trong `TextFormatHelper`. |
| 2026-10-19 | [Khắc phục toàn diện Audit Phase 3 (Core Screens, Navigation, WebView, TTS & System)](./2026-10-19-audit-remediation-phase3-implementation-plan.md) | `Completed` | Biểu đồ 7 ngày động & giữ `FilterChip` trên `HomeScreen`, bảo vệ công tắc nguồn cuối cùng & đồng bộ đăng nhập Wikidich trên `SettingsScreen`, chặn vòng lặp `loadMore` vô tận, lọc độc lập số chương/trạng thái trên `SearchScreen`, mã hóa Base64 URL-safe `NavigationUtils`, kiểm tra tên miền chặt `isAllowedDomain` & dọn `WebView` trong `finally`, retry `WikidichScraper`, bảo vệ `CrashHandler`, đồng bộ `collectAsStateWithLifecycle`, `WindowInsets`, `GridCells.Adaptive` & `TtsManager`/`TtsService`. |
| 2026-10-19 | [Khắc phục Báo Cáo Kiểm Toán — Giai Đoạn 4 (Kiểm Thử, Build/Lint/ProGuard, Cookie/Cache & Tài Liệu)](./2026-10-19-audit-remediation-phase4-implementation-plan.md) | `Completed` | Bổ sung `TangThuVienScraperTest` (`MockWebServer`), xóa Mirror Tests (`TtsManagerTest`, `NavigationUtilsTest`, `StoryDetailViewModelTest`), chuẩn hóa `CookieTest`/`LacaTruyenApiTest`/`TestJsoup` & xóa `CryptoJS.kt`, bật `abortOnError = true` cho Android Lint + tinh chỉnh `proguard-rules.pro`, hoàn thiện `AndroidCookieStore.removeCookies` & `BaseScraper` Cache TTL 10 phút, khóa Cleartext HTTP, chuyển `android.util.Log` sang `Timber`, và đồng bộ tài liệu dự án. |
| 2026-10-23 | [Sửa lỗi nhảy cuối chương khi vuốt sang chương tiếp theo & hiển thị số chương Wikidich](./2026-10-23-fix-reader-scroll-and-wikidich-chapter-count-implementation-plan.md) | `Completed` | Chống kích hoạt `DisposableEffect.onDispose` khi đổi chương trên `ReaderScreen`, gắn `chapterNumber` vào `SaveScrollPosition` & thêm `restoreSavedScroll` trong `ReaderViewModel`; bổ sung `fetchTotalChaptersFromBookIndex` + `ajaxFragmentCache` + bật `enrichWithChapterCounts` trong `WikidichScraper.search()`, đồng bộ `updateTotalChaptersIfGreater` trong `StoryRepository` và hiển thị `effectiveTotalChapters` trên `StoryDetailScreen`/`HomeScreen`. |
| 2026-10-23 | [Nâng cấp Hệ thống Rules, Subagents & Skills theo Vikini Governance 2.0](./2026-10-23-adopt-vikini-governance-v2-implementation-plan.md) | `Completed` | Chuẩn hóa YAML frontmatter subagents (`model: pro`), Model Roster tháng 10/2026, Progressive Disclosure (`trigger: glob` cho `01-coding.md` & `03-ui.md`, tổng `always_on < 18.000` ký tự), tạo bộ 4 skills nội bộ (`apptruyen-debug`, `apptruyen-audit`, `apptruyen-investigate`, `apptruyen-simplify`) thay thế `.agents/workflows/`, gia cố `code-freeze-guard.js` (Layer 2 `.md`-only guard, Read Guard, bí danh công cụ, `formatHookOutput`) và đồng bộ 4 host nguồn truyện (`sstruyen.pics`, `api.sstruyen.pics`, `file.sstruyen.pics`, `wikicv.org`). |

---

## Template

BẮT BUỘC tuân thủ định dạng header chặt: H1 là dòng không trống đầu tiên của file, theo liền sau là khối blockquote header:

```markdown
# Kế hoạch triển khai: <Tên task>

> **Trạng thái**: Ready for Review
> **Revision**: 1
> **Ngày tạo**: YYYY-MM-DD
> **Planner**: @planner ([PLANNER_MODEL: <model>])

## Technical Plan

### 1. Mục Tiêu (Goal) & Out of Scope (What We're NOT Doing)

### 2. Pre-Work Reading & Skills Loaded & Applied

- `AGENTS.md`, `.agents/rules/00-core.md`, `.agents/rules/02-quality.md`, ...
- Bảng `SKILL.md` đã đọc và áp dụng: ...

### 3. Assumptions & Cross-Task Dependencies

### 4. Verified Versions Table

| Thư viện | Version hiện tại | Version đề xuất | Latest đã xác minh | Nguồn URL | Tương thích? |
| -------- | ---------------- | --------------- | ------------------ | --------- | ------------ |

### 5. Files Cần Chỉnh Sửa / Tạo Mới & Caller-First Design Sketch

| Nhãn       | File | Tầng | Ghi chú (file nhạy cảm → lý do) |
| ---------- | ---- | ---- | ------------------------------- |
| `[NEW]`    |      |      |                                 |
| `[MODIFY]` |      |      |                                 |

### 6. Các Bước Thực Hiện

- [ ] Bước 1: ...
- [ ] Bước 2: ...
- [ ] Bước 3: ...
- [ ] Bước 4: ...
- [ ] Bước 5: ...

### 7. Test Contract

| # | Hành vi (ngôn ngữ nghiệp vụ) | Input | Kết quả mong đợi / thông báo lỗi tiếng Việt |
| - | ---------------------------- | ----- | ------------------------------------------- |

### 8. Verification Plan

- Unit test: `app/src/test/java/com/personal/apptruyen/...Test.kt`
- Lệnh: `.\gradlew.bat ktlintCheck; .\gradlew.bat testDebugUnitTest; .\gradlew.bat assembleDebug`
- Kiểm thử thủ công trên thiết bị: ...
- Wiring Probe & Negative Probe (nếu thêm rule/guard/validation): ...

### 9. Rủi Ro & Phòng Ngừa

| Rủi ro | Mức (Cao/TB/Thấp) | Technical control |
| ------ | ----------------- | ----------------- |

### 10. Cập Nhật Docs

- `docs/changelog.md`: ...

## Audit History

<!-- Reviewer chỉ append "### Audit Run N" ở dưới. Không sửa phần Technical Plan. -->
```
