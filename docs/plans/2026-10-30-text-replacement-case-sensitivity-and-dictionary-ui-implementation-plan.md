# Kế hoạch triển khai: Thay chữ phân biệt hoa thường & Thiết kế lại giao diện Từ điển thay chữ

> **Trạng thái**: Completed
> **Revision**: 2
> **Ngày tạo**: 2026-10-30
> **Planner**: @planner ([PLANNER_MODEL: gemini-4-argon])

## Technical Plan

### 1. Mục Tiêu (Goal) & Out of Scope (What We're NOT Doing)

#### Mục tiêu (Goal)
1. **Bổ sung tùy chọn phân biệt chữ hoa / chữ thường (`caseSensitive: Boolean`) cho từng mục thay chữ**:
   - Áp dụng đồng bộ cho cả **Thay chữ tự động toàn cục (Global)** trong `SettingsScreen` / `SettingsViewModel` và **Thay chữ riêng cho truyện (Per-story)** trong `ReaderSettingsPanel` / `ReaderViewModel` / `TextReplacementRepository`.
   - Khi `caseSensitive = true`: chỉ thay thế từ khớp chính xác chữ hoa/thường (`String.replace(from, to, ignoreCase = false)`).
   - Khi `caseSensitive = false`: thay thế không phân biệt chữ hoa/thường (`String.replace(from, to, ignoreCase = true)`).
   - **Bảo toàn 100% hành vi dữ liệu cũ & định dạng lưu trữ**:
     - Các bản ghi JSON cũ đã lưu trong `SettingsDataStore` (`CUSTOM_REPLACEMENTS`), cột `storyCustomReplacements` trong bảng `stories` (Room DB v9), và tệp sao lưu ZIP (`BackupRepository`) chưa có khóa `"caseSensitive"` (`!obj.has("caseSensitive")`) được nạp với `caseSensitive = true` (`LEGACY_JSON_DEFAULT_CASE_SENSITIVE = true`), giữ nguyên 100% hành vi thay chữ phân biệt hoa/thường trước đây của người dùng (tránh tự động biến chữ hoa đầu câu thành chữ thường).
     - Các mục **mới** tạo từ giao diện hộp thoại `"Thông tin"` có `Checkbox` `"Phân biệt hoa thường"` mặc định **tắt** (`caseSensitive = false`, đúng như Ảnh 2 của User) và khi lưu qua `serializeReplacementItems` luôn ghi tường minh `"caseSensitive": false/true` vào JSON.
2. **Thiết kế lại giao diện hiển thị & quản lý danh sách thay thế chữ ("Từ điển" & Dialog "Thông tin")**:
   - Thay thế cách liệt kê phẳng trực tiếp toàn bộ danh sách từ thay thế trên `SettingsScreen` và `ReaderSettingsPanel` (vốn làm màn hình bị kéo dài khi có nhiều mục) bằng thẻ tóm tắt gọn gàng (`TextReplacementSummarySection`) kết hợp hộp thoại toàn màn hình **`"Từ điển"`** (`TextReplacementDictionaryDialog`) và hộp thoại thêm/sửa **`"Thông tin"`** (`TextReplacementEditDialog`) dùng chung trong `ui/components/TextReplacementDictionaryDialog.kt`.
   - Hộp thoại **`"Từ điển"`** (theo mẫu Ảnh 1 của User):
     - Thanh tiêu đề có nút quay lại (`←`), tiêu đề `"Từ điển"`, và nút `+` thêm từ mới.
     - Thanh tìm kiếm bo tròn với icon kính lúp và placeholder `"Nhập từ khóa"` để lọc tức thì theo từ gốc hoặc từ thay thế.
     - Bảng danh sách dạng 4 cột có đường kẻ rõ ràng: **`Tìm`** | **`Thay thế`** | **`Hoa thường`** (Checkbox bật/tắt trực tiếp trên từng dòng) | **`Xóa`** (nút `✕`; đối với từ điển riêng của truyện có thêm nút chuyển sang từ điển chung).
     - Cuộn độc lập bằng `LazyColumn` có `key` ổn định; chạm vào dòng bất kỳ để mở hộp thoại **`"Thông tin"`** chỉnh sửa mục đó.
   - Hộp thoại **`"Thông tin"`** (theo mẫu Ảnh 2 của User):
     - Tiêu đề `"Thông tin"`.
     - Câu xem trước (preview) động làm nổi bật từ gốc và từ thay thế: `"Tất cả những từ: <Từ cần thay thế> sẽ được thay bằng <Từ thay thế> trong tất cả các bộ truyện"` (hoặc `"trong bộ truyện này"` đối với từ điển riêng của truyện).
     - Hai ô nhập `"Từ cần thay thế"` và `"Từ thay thế"`, hàng `"Phân biệt hoa thường"` kèm `Checkbox`, và 2 nút hành động `"Lưu lại"` / `"Đóng lại"`.
   - Giảm kích thước file `ReaderSettingsPanel.kt` (từ 633 dòng xuống ~545 dòng, dưới ngưỡng tối đa 600 dòng của `.agents/rules/01-coding.md`) và rút gọn `SettingsScreen.kt` (~85 dòng).

#### Out of Scope (Những gì KHÔNG làm)
- **Không** hỗ trợ biểu thức chính quy (Regex) do người dùng tự nhập để tránh lỗi cú pháp `PatternSyntaxException` hoặc ReDoS; giữ nguyên cơ chế thay thế văn bản thuần (`String.replace`).
- **Không** thay đổi cấu trúc bảng `stories` hay nâng version Room Database (`AppDatabase` giữ nguyên `version = 9`).
- **Không** thay đổi danh sách ký tự chấm mặc định (`DOTS_TO_REMOVE` gồm `·`, `•`, `⋅`) trong `TextReplacementHelper`.
- **Không** thêm tính năng đồng bộ từ điển trực tuyến lên đám mây (nút `"Online"` hoặc nút chia sẻ mạng xã hội trong ảnh mẫu của app bên thứ ba không thuộc kiến trúc offline-first của AppTruyen; tính năng chia sẻ từ riêng sang cài đặt chung của app vẫn được giữ nguyên).

---

### 2. Pre-Work Reading & Skills Loaded & Applied

#### Danh sách tài liệu và mã nguồn đã đọc (`[SRC]`)
- `AGENTS.md` (dòng 1–46), `.agents/rules/00-core.md` (dòng 1–85), `.agents/rules/01-coding.md` (dòng 1–118), `.agents/rules/02-quality.md` (dòng 1–75), `.agents/rules/03-ui.md` (dòng 1–47), `.agents/rules/05-plan-review.md` (dòng 1–90), `.agents/rules/06-docs.md` (dòng 1–50): Quy chuẩn kiến trúc Clean Architecture + MVVM/MVI, 100% UI tiếng Việt, ngưỡng kích thước file (`Screen`/`Composable` tối đa 600 dòng), và quy trình kiểm thử.
- `docs/architecture.md` (dòng 1–250), `docs/progress.md` (dòng 1–150), `docs/lessons-learned.md` (dòng 1–329), `docs/plans/README.md` (dòng 1–116):
  - `[SRC]` `docs/lessons-learned.md:270, 301`: Khi mở rộng hàm trên interface đang được MockK stub trong unit test (`ITextReplacementRepository.sendReplacementToGlobal`), bắt buộc giữ overload ít tham số để tương thích ngược với các stub MockK hiện có.
- `app/src/main/java/com/personal/apptruyen/util/TextReplacementHelper.kt` (dòng 1–111):
  - `[SRC]` `TextReplacementHelper.kt:32-53`: `applyReplacements` hiện nhận `List<Pair<String, String>>` và gọi `result.replace(from, to)` (mặc định `ignoreCase = false`, tức phân biệt hoa thường).
  - `[SRC]` `TextReplacementHelper.kt:59-79`: `applyAllReplacements` chạy `applyReplacements` (global) trước, sau đó chạy vòng lặp `storyCustomReplacements` (per-story).
  - `[SRC]` `TextReplacementHelper.kt:85-110`: `parseCustomReplacements` và `serializeCustomReplacements` dùng `org.json.JSONArray` và `org.json.JSONObject` với 2 trường `"from"` và `"to"`.
- `app/src/main/java/com/personal/apptruyen/data/repository/ITextReplacementRepository.kt` (dòng 1–32) & `TextReplacementRepository.kt` (dòng 1–85):
  - `[SRC]` `TextReplacementRepository.kt:35-66`: `addStoryReplacement` và `removeStoryReplacement` đọc `storyDao.getStoryReplacements(storyId)`, parse JSON, cập nhật list và ghi lại qua `storyDao.setStoryCustomReplacements(storyId, json)` mà chưa có khóa đồng bộ (`Mutex`). Chưa có hàm `updateStoryReplacement`.
  - `[SRC]` `TextReplacementRepository.kt:68-84`: `sendReplacementToGlobal(from, to)` đọc `dataStore.data.first()` bên ngoài `dataStore.edit`, kiểm tra trùng và ghi lại.
- `app/src/main/java/com/personal/apptruyen/data/local/StoryDao.kt` (dòng 74–99), `SettingsDataStore.kt` (dòng 53–132), `BackupData.kt` (dòng 18–35), `BackupRepository.kt` (dòng 220–260, 408–438, 557–611):
  - `[SRC]` `StoryDao.kt:82-99`: `storyCustomReplacements` lưu dạng `TEXT` (`String` JSON).
  - `[SRC]` `SettingsDataStore.kt:68, 124-126`: `SettingsKeys.CUSTOM_REPLACEMENTS` lưu dạng `stringPreferencesKey("custom_replacements")` và được sao lưu/khôi phục nguyên chuỗi JSON.
- `app/src/main/java/com/personal/apptruyen/ui/settings/SettingsViewModel.kt` (dòng 83–242) & `SettingsScreen.kt` (dòng 247–390):
  - `[SRC]` `SettingsViewModel.kt:220-241`: `addCustomReplacement` và `removeCustomReplacement` hiện đọc `state.value.customReplacements` rồi gọi `dataStore.edit`. Chưa có `updateCustomReplacement`.
  - `[SRC]` `SettingsScreen.kt:278-389` (tổng file 1.093 dòng): Render phẳng toàn bộ `state.customReplacements.forEachIndexed` ngay trong card cài đặt.
- `app/src/main/java/com/personal/apptruyen/ui/reader/ReaderViewModel.kt` (dòng 87–107, 210–213, 239–242, 274–285, 338–348, 465–536, 1009–1080), `ReaderScreen.kt` (dòng 403–438) & `ReaderSettingsPanel.kt` (dòng 1–633):
  - `[SRC]` `ReaderSettingsPanel.kt:496-606` (tổng file 633 dòng): Render phẳng toàn bộ `storyReplacements.forEachIndexed` trực tiếp trong `Column` có `verticalScroll`, làm phình dài panel cài đặt đọc.
- `app/src/main/java/com/personal/apptruyen/ui/detail/StoryDetailViewModel.kt` (dòng 55–145):
  - `[SRC]` `StoryDetailViewModel.kt:57, 95-99, 120, 128, 140`: Lưu `storyReplacements: ImmutableList<Pair<String, String>>` trong `DetailState` (không được `StoryDetailScreen` render) và gọi `textReplacementRepository.addStoryReplacement(targetId, from, to)`, `removeStoryReplacement(targetId, index)`, `sendReplacementToGlobal(from, to)`.
- `app/src/test/java/com/personal/apptruyen/util/TextReplacementHelperTest.kt` (dòng 1–196), `ui/reader/ReaderViewModelTest.kt` (dòng 1–756), `ui/settings/SettingsViewModelTest.kt` (dòng 1–264):
  - `[SRC]` `TextReplacementHelperTest.kt:42-195`: 16 bài test hiện hữu gọi `parseCustomReplacements`, `serializeCustomReplacements`, `applyReplacements`, `applyAllReplacements` với `List<Pair<String, String>>`.
  - `[SRC]` `ReaderViewModelTest.kt:527-561`: Test `SendReplacementToGlobal` stub `coEvery { textReplacementRepository.sendReplacementToGlobal("nàn", "nàng") } returnsMany listOf(true, false)`.

#### Bảng Skills Loaded & Applied

| Skill đã đọc (`view_file`) | Mục áp dụng | Quyết định kỹ thuật chịu ảnh hưởng trực tiếp |
| :--- | :--- | :--- |
| `.agents/skills/apptruyen-investigate/SKILL.md` | Mode 1 (`/how`) & Mode 4 (`/blast-radius`) | Thực hiện phân tích phạm vi ảnh hưởng toàn diện trên mọi nơi gọi `TextReplacementHelper`, `ITextReplacementRepository`, `SettingsViewModel`, `ReaderViewModel`, `StoryDetailViewModel`. Giữ nguyên các hàm `Pair<String, String>` hiện hữu trong `TextReplacementHelper` (đánh dấu `@Deprecated` trên `serializeCustomReplacements`) và bổ sung bộ hàm `TextReplacementItem` để không làm gãy bất kỳ bài test hay nơi gọi cũ nào. |
| `.agents/skills/apptruyen-simplify/SKILL.md` | Nguyên tắc 1 (Subtract Before You Add) & Ngưỡng kích thước file (`01-coding.md`) | Gỡ bỏ ~110 dòng code UI danh sách phẳng + `AlertDialog` bị lặp lại ở cả `SettingsScreen.kt` (dòng 278–389) và `ReaderSettingsPanel.kt` (dòng 496–606), gom về một shared component duy nhất `ui/components/TextReplacementDictionaryDialog.kt`, đưa `ReaderSettingsPanel.kt` từ 633 dòng xuống ~545 dòng (< 600 dòng). |

---

### 3. Assumptions & Cross-Task Dependencies

#### Các giả định kỹ thuật đã kiểm chứng
1. **Bảo toàn ngữ nghĩa dữ liệu JSON cũ & tách bạch mặc định UI mới (`[SRC]` `TextReplacementHelper.kt:48, 73, 85-110`, `SettingsDataStore.kt:68`, `StoryDao.kt:82-99`)**:
   - Trước khi có tính năng này, `TextReplacementHelper.kt:48, 73` gọi `result.replace(from, to)` với `ignoreCase = false` mặc định (tức **phân biệt hoa thường**).
   - Để không làm đổi ngầm hành vi thay chữ của người dùng hiện hữu (ví dụ quy tắc cũ `("nàn", "nàng")` không bị tự động đổi `"Nàn"` ở đầu câu thành `"nàng"`, hay `("lão", "ông")` không đổi tên riêng `"Lão"`), hàm `parseReplacementItems(json)` kiểm tra `if (obj.has("caseSensitive")) obj.optBoolean("caseSensitive", false) else LEGACY_JSON_DEFAULT_CASE_SENSITIVE` (với `LEGACY_JSON_DEFAULT_CASE_SENSITIVE = true`).
   - Ngược lại, khi người dùng thêm từ **mới** trên UI (`TextReplacementEditDialog`), `Checkbox` `"Phân biệt hoa thường"` mặc định **tắt** (`caseSensitive = false` theo đúng Ảnh 2 của User), và hàm `serializeReplacementItems` luôn ghi tường minh `"caseSensitive": false/true` vào JSON. Nhờ đó, dữ liệu cũ giữ nguyên 100% hành vi trước khi cập nhật, còn dữ liệu mới tạo từ UI mặc định không phân biệt hoa thường đúng như mong muốn của User.
2. **Tính nguyên tử (Atomicity) và chống lệch chỉ số (Anti-TOCTOU) khi thao tác nhanh trên bảng `"Từ điển"` (`[SRC]` `TextReplacementRepository.kt:35-84`, `SettingsViewModel.kt:220-241`)**:
   - Trong `@Singleton TextReplacementRepository`: bọc toàn bộ chu kỳ đọc–sửa–ghi per-story (`addStoryReplacement`, `updateStoryReplacement`, `removeStoryReplacement`) trong `storyReplacementMutex.withLock { ... }`, và đưa toàn bộ phần đọc + kiểm tra trùng + ghi của `sendReplacementToGlobal` vào bên trong `dataStore.edit { prefs -> ... }`.
   - Trong `SettingsViewModel`: đưa toàn bộ chu kỳ đọc–sửa–ghi của `addCustomReplacement`, `updateCustomReplacement`, `removeCustomReplacement` vào bên trong `dataStore.edit { prefs -> ... }`.
   - Để chống lỗi lệch `index` (TOCTOU) khi người dùng bấm nút xóa `✕` và bấm `Checkbox` `"Hoa thường"` liên tiếp trước khi UI kịp recompose, cả `update` và `remove` đều nhận tham số `expected: TextReplacementItem? = null` và chỉ thực hiện khi `index in list.indices && (expected == null || list[index] == expected)` bên trong vùng khóa nguyên tử.
3. **Giữ nguyên các bài test hiện hữu (`[SRC]` `TextReplacementHelperTest.kt:140-195`, `ReaderViewModelTest.kt:527-561`)**:
   - Trong `ITextReplacementRepository`, hàm 2 tham số `sendReplacementToGlobal(from: String, to: String): Boolean` ủy quyền sang hàm 3 tham số `sendReplacementToGlobal(from, to, caseSensitive = false)`. Trong `ReaderViewModel.sendReplacementToGlobal`, khi `caseSensitive == false` sẽ gọi `textReplacementRepository.sendReplacementToGlobal(from, to)` để khớp chính xác với stub MockK tại `ReaderViewModelTest.kt:532`.

#### Phân tích phạm vi ảnh hưởng (`/blast-radius`)

| Nơi gọi (Caller) | `file:line` | Cách sử dụng hiện tại | Tương thích? (`Yes` / `No` + Lý do) |
| :--- | :--- | :--- | :--- |
| `TextReplacementHelperTest` | `app/src/test/java/com/personal/apptruyen/util/TextReplacementHelperTest.kt:10-195` | Gọi `applyReplacements`, `applyAllReplacements`, `parseCustomReplacements`, `serializeCustomReplacements` với `List<Pair<String, String>>` | `Yes` — Giữ nguyên các hàm `Pair<String, String>` hiện hữu (thêm `@Suppress("DEPRECATION")` tại test cũ gọi `serializeCustomReplacements`); bổ sung thêm `@Test` mới cho `TextReplacementItem` & `caseSensitive`. |
| `TextReplacementRepository` | `app/src/main/java/com/personal/apptruyen/data/repository/TextReplacementRepository.kt:35-84` | Parse/serialize JSON cho Room và DataStore | `Yes` — Nâng cấp sang `parseReplacementItems` / `serializeReplacementItems`, bọc `storyReplacementMutex.withLock` & `dataStore.edit`, thêm `caseSensitive`, `expected`, và `updateStoryReplacement`. |
| `SettingsViewModel` | `app/src/main/java/com/personal/apptruyen/ui/settings/SettingsViewModel.kt:94, 144-148, 220-241` | Lưu `customReplacements` trong `SettingsState`, thêm/xóa mục trong `DataStore` | `No` (Internal UI state) — Đổi `SettingsState.customReplacements` sang `ImmutableList<TextReplacementItem>`, thêm `caseSensitive` vào `addCustomReplacement`, thêm `expected` vào `removeCustomReplacement` và thêm `updateCustomReplacement`. Cập nhật đồng bộ tại `SettingsScreen.kt`. |
| `SettingsScreen` | `app/src/main/java/com/personal/apptruyen/ui/settings/SettingsScreen.kt:278-389` | Hiển thị danh sách `state.customReplacements` và dialog thêm từ | `No` (UI Caller) — Cập nhật sang `TextReplacementSummarySection` mở `TextReplacementDictionaryDialog` & `TextReplacementEditDialog`. |
| `ReaderViewModel` | `app/src/main/java/com/personal/apptruyen/ui/reader/ReaderViewModel.kt:94-106, 212, 239-241, 338-348, 465-495, 1021-1079` | Quản lý `customReplacements` & `storyCustomReplacements`, xử lý nội dung chương và MVI intents | `No` (Internal UI state) — Đổi `ReaderState.storyReplacements` sang `ImmutableList<TextReplacementItem>`, mở rộng `AddStoryReplacement`, `RemoveStoryReplacement`, `SendReplacementToGlobal`, thêm `UpdateStoryReplacement`. Cập nhật đồng bộ tại `ReaderScreen.kt` và `ReaderSettingsPanel.kt`. |
| `ReaderViewModelTest` | `app/src/test/java/com/personal/apptruyen/ui/reader/ReaderViewModelTest.kt:370, 527-561` | Kiểm thử `CUSTOM_REPLACEMENTS` JSON cũ và `SendReplacementToGlobal("nàn", "nàng")` | `Yes` — Tham số mặc định `caseSensitive: Boolean = false` trên `ReaderIntent.SendReplacementToGlobal` và ủy quyền hàm 2 tham số giữ toàn bộ test hiện hữu pass 100%. |
| `ReaderScreen` & `ReaderSettingsPanel` | `ReaderScreen.kt:413, 429-433`, `ReaderSettingsPanel.kt:32, 47-49, 496-606` | Truyền `storyReplacements` và callbacks thêm/xóa/gửi sang cài đặt chung | `No` (UI Caller) — Cập nhật kiểu `List<TextReplacementItem>`, thêm callback `onUpdateStoryReplacement`, thay danh sách phẳng bằng `TextReplacementSummarySection`. |
| `StoryDetailViewModel` | `app/src/main/java/com/personal/apptruyen/ui/detail/StoryDetailViewModel.kt:57, 95-99, 120, 128, 140` | Lưu `storyReplacements: ImmutableList<Pair<String, String>>` (không có UI gọi tới), gọi `addStoryReplacement(targetId, from, to)`, `removeStoryReplacement(targetId, index)`, `sendReplacementToGlobal(from, to)` | `Yes` — Nhờ giữ `parseCustomReplacements` và tham số mặc định `caseSensitive = false` / `expected = null` trên `ITextReplacementRepository`, `StoryDetailViewModel` giữ nguyên không cần sửa (Minimal Diffs). |
| `BackupRepository` | `app/src/main/java/com/personal/apptruyen/data/repository/BackupRepository.kt:224-226, 433-434, 557-565` | Sao lưu/khôi phục nguyên chuỗi JSON của `storyCustomReplacements` và `CUSTOM_REPLACEMENTS` | `Yes` — Chuỗi JSON mới vẫn là `String` hợp lệ và tương thích 2 chiều. |

#### Danh sách `[NEEDS_PRODUCT_DECISION]`
- **`[NEEDS_PRODUCT_DECISION]` PD-1: Chế độ `caseSensitive` mặc định cho các mục thay chữ cũ đã lưu từ phiên bản trước (chưa có khóa `"caseSensitive"` trong chuỗi JSON)**:
  - **Phương án A (Default Assumption — Đã chọn trong Plan)**: Các bản ghi JSON cũ thiếu khóa `"caseSensitive"` (`!obj.has("caseSensitive")`) được nạp với `caseSensitive = true` để bảo toàn 100% hành vi thay chữ phân biệt hoa/thường hiện có của người dùng (tránh tự động đổi chữ hoa đầu câu như `"Nàn"` → `"nàng"` hoặc tên riêng `"Lão"` → `"ông"`). Trong khi đó, mọi mục **mới** tạo từ hộp thoại `"Thông tin"` trên UI mặc định bỏ chọn `Checkbox` (`caseSensitive = false` như Ảnh 2) và luôn ghi tường minh `"caseSensitive": false/true` vào JSON. Người dùng có thể bấm 1 chạm vào cột `"Hoa thường"` trong bảng `"Từ điển"` nếu muốn chuyển mục cũ sang không phân biệt hoa thường.
  - **Phương án B**: Chuyển toàn bộ các bản ghi JSON cũ thiếu khóa `"caseSensitive"` sang `caseSensitive = false` (không phân biệt hoa thường) ngay khi nâng cấp ứng dụng.

---

### 4. Verified Versions Table

Task này không thêm mới và không nâng cấp bất kỳ thư viện nào trong `app/build.gradle.kts`. Các thư viện liên quan đã được xác minh trực tuyến:

| Thư viện | Version hiện tại (`app/build.gradle.kts`) | Version đề xuất | Latest đã xác minh (2026) | Nguồn URL | Tương thích? |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Kotlin Standard Library (`CharSequence.replace(..., ignoreCase)`) | `2.4.21` | `2.4.21` (Giữ nguyên) | `2.4.21` | `https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.text/replace.html` | Có (`minSdk 26`, hỗ trợ sẵn `ignoreCase: Boolean`) |
| Jetpack Compose BOM (`material3`, `Dialog`, `Checkbox`, `LazyColumn`) | `2026.05.01` | `2026.05.01` (Giữ nguyên) | `2026.08.00` | `https://developer.android.com/jetpack/compose/bom/bom-mapping` | Có (Không đổi version theo nguyên tắc Minimal Diffs) |
| AndroidX DataStore Preferences | `1.2.1` | `1.2.1` (Giữ nguyên) | `1.2.1` | `https://developer.android.com/jetpack/androidx/releases/datastore` | Có |
| AndroidX Room 3 Runtime | `3.0.3` | `3.0.3` (Giữ nguyên) | `3.0.3` | `https://developer.android.com/jetpack/androidx/releases/room` | Có (Schema v9 giữ nguyên) |

---

### 5. Files Cần Chỉnh Sửa / Tạo Mới & Caller-First Design Sketch

#### Bảng phân loại files

| Nhãn | File | Tầng | Ghi chú (file nhạy cảm → lý do) |
| :--- | :--- | :--- | :--- |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/util/TextReplacementHelper.kt` | `util` | Thêm `TextReplacementItem(from, to, caseSensitive = false)` cùng các hàm parse (`!obj.has("caseSensitive")` → `true`) / serialize / apply hỗ trợ `ignoreCase = !item.caseSensitive`; đánh dấu `@Deprecated` trên `serializeCustomReplacements(List<Pair>)`. |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/data/repository/ITextReplacementRepository.kt` | `data/repository` | Mở rộng `addStoryReplacement`, `removeStoryReplacement` (`expected`), `sendReplacementToGlobal` (`caseSensitive`) và thêm `updateStoryReplacement`. |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/data/repository/TextReplacementRepository.kt` | `data/repository` | Triển khai lưu/đọc/cập nhật `TextReplacementItem` nguyên tử với `storyReplacementMutex.withLock` (Room) và `dataStore.edit` (DataStore), kiểm tra `expected` chống TOCTOU. |
| `[NEW]` | `app/src/main/java/com/personal/apptruyen/ui/components/TextReplacementDictionaryDialog.kt` | `ui/components` | Shared UI: `TextReplacementSummarySection`, `TextReplacementDictionaryDialog` (bảng "Từ điển" có tìm kiếm & checkbox `Hoa thường`), và `TextReplacementEditDialog` (hộp thoại "Thông tin" có preview động). |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/ui/settings/SettingsViewModel.kt` | `ui/settings` | Chuyển `customReplacements` sang `ImmutableList<TextReplacementItem>`, cập nhật nguyên tử trong `dataStore.edit` kèm kiểm tra `expected`, thêm `updateCustomReplacement`. |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/ui/settings/SettingsScreen.kt` | `ui/settings` | Thay danh sách phẳng bằng `TextReplacementSummarySection`, giảm ~85 dòng code. |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/ui/reader/ReaderViewModel.kt` | `ui/reader` | Chuyển `storyReplacements` sang `ImmutableList<TextReplacementItem>`, thêm `ReaderIntent.UpdateStoryReplacement`, đồng bộ `customReplacements` trong bộ nhớ cho cả nhánh thêm mới và cập nhật `caseSensitive` tại chỗ trong `sendReplacementToGlobal`. |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/ui/reader/ReaderScreen.kt` | `ui/reader` | Nối dây `onAddStoryReplacement`, `onUpdateStoryReplacement`, `onRemoveStoryReplacement`, `onSendToGlobal` vào `ReaderSettingsPanel`. |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/ui/reader/ReaderSettingsPanel.kt` | `ui/reader` | Thay danh sách phẳng (dòng 496–606) bằng `TextReplacementSummarySection`, giảm kích thước file từ 633 dòng xuống ~545 dòng (< 600 dòng). |
| `[MODIFY]` | `app/src/test/java/com/personal/apptruyen/util/TextReplacementHelperTest.kt` | `test` | **File nhạy cảm (`app/src/test/**`)**: Code Freeze Guard luôn hỏi User theo chính sách Test Integrity. Chỉ bổ sung `@Test` mới cho `caseSensitive` và JSON backward compatibility, giữ nguyên 100% 16 test cũ. |
| `[NEW]` | `app/src/test/java/com/personal/apptruyen/data/repository/TextReplacementRepositoryTest.kt` | `test` | **File nhạy cảm (`app/src/test/**`)**: Code Freeze Guard luôn hỏi User. Tạo mới bộ unit test cho `TextReplacementRepository` (add, update, remove, sendToGlobal, đồng thời & TOCTOU `expected`). |
| `[MODIFY]` | `app/src/test/java/com/personal/apptruyen/ui/settings/SettingsViewModelTest.kt` | `test` | **File nhạy cảm (`app/src/test/**`)**: Code Freeze Guard luôn hỏi User. Bổ sung `@Test` kiểm thử `addCustomReplacement`, `updateCustomReplacement`, `removeCustomReplacement` với `caseSensitive` và TOCTOU `expected`. |
| `[MODIFY]` | `app/src/test/java/com/personal/apptruyen/ui/reader/ReaderViewModelTest.kt` | `test` | **File nhạy cảm (`app/src/test/**`)**: Code Freeze Guard luôn hỏi User. Bổ sung `@Test` kiểm thử `AddStoryReplacement`, `UpdateStoryReplacement`, `SendReplacementToGlobal` (cả thêm mới và cập nhật `caseSensitive` tại chỗ) kèm `reprocessCurrentChapter`. |
| `[MODIFY]` | `docs/architecture.md`, `docs/progress.md`, `docs/changelog.md`, `docs/plans/README.md` | `docs` | Cập nhật tài liệu theo ma trận `.agents/rules/06-docs.md`. |

#### Caller-First Design Sketch

```kotlin
// ── 1. Model & Helper (com.personal.apptruyen.util.TextReplacementHelper) ──
data class TextReplacementItem(
    val from: String,
    val to: String,
    val caseSensitive: Boolean = false,
)

object TextReplacementHelper {
    const val LEGACY_JSON_DEFAULT_CASE_SENSITIVE = true

    fun applyReplacementItems(
        text: String,
        enabled: Boolean,
        customReplacements: List<TextReplacementItem> = emptyList(),
    ): String

    fun applyAllReplacementItems(
        text: String,
        globalEnabled: Boolean,
        globalCustomReplacements: List<TextReplacementItem>,
        storyEnabled: Boolean,
        storyCustomReplacements: List<TextReplacementItem>,
    ): String

    fun parseReplacementItems(json: String?): List<TextReplacementItem>
    fun serializeReplacementItems(list: List<TextReplacementItem>): String

    // Giữ nguyên các hàm Pair<String, String> để tương thích ngược 100% với test cũ
    fun applyReplacements(
        text: String,
        enabled: Boolean,
        customReplacements: List<Pair<String, String>> = emptyList(),
    ): String

    fun applyAllReplacements(
        text: String,
        globalEnabled: Boolean,
        globalCustomReplacements: List<Pair<String, String>>,
        storyEnabled: Boolean,
        storyCustomReplacements: List<Pair<String, String>>,
    ): String

    fun parseCustomReplacements(json: String?): List<Pair<String, String>>

    @Deprecated(
        message = "Use serializeReplacementItems to preserve caseSensitive flag",
        replaceWith = ReplaceWith("serializeReplacementItems(list.map { TextReplacementItem(it.first, it.second, true) })"),
    )
    fun serializeCustomReplacements(list: List<Pair<String, String>>): String
}

// ── 2. Repository Interface (com.personal.apptruyen.data.repository.ITextReplacementRepository) ──
interface ITextReplacementRepository {
    suspend fun getStoryReplacements(storyId: String): StoryReplacementData?
    suspend fun setStoryReplacementsEnabled(storyId: String, enabled: Boolean)
    suspend fun addStoryReplacement(
        storyId: String,
        from: String,
        to: String,
        caseSensitive: Boolean = false,
    )
    suspend fun updateStoryReplacement(
        storyId: String,
        index: Int,
        from: String,
        to: String,
        caseSensitive: Boolean,
        expected: TextReplacementItem? = null,
    )
    suspend fun removeStoryReplacement(
        storyId: String,
        index: Int,
        expected: TextReplacementItem? = null,
    )
    suspend fun sendReplacementToGlobal(
        from: String,
        to: String,
    ): Boolean = sendReplacementToGlobal(from, to, caseSensitive = false)

    suspend fun sendReplacementToGlobal(
        from: String,
        to: String,
        caseSensitive: Boolean,
    ): Boolean
}

// ── 3. ViewModel Call Sites (SettingsViewModel & ReaderViewModel) ──
// SettingsViewModel:
fun addCustomReplacement(from: String, to: String, caseSensitive: Boolean = false)
fun updateCustomReplacement(
    index: Int,
    from: String,
    to: String,
    caseSensitive: Boolean,
    expected: TextReplacementItem? = null,
)
fun removeCustomReplacement(
    index: Int,
    expected: TextReplacementItem? = null,
)

// ReaderIntent (trong ReaderViewModel.kt):
data class AddStoryReplacement(
    val from: String,
    val to: String,
    val caseSensitive: Boolean = false,
) : ReaderIntent

data class UpdateStoryReplacement(
    val index: Int,
    val from: String,
    val to: String,
    val caseSensitive: Boolean,
    val expected: TextReplacementItem? = null,
) : ReaderIntent

data class RemoveStoryReplacement(
    val index: Int,
    val expected: TextReplacementItem? = null,
) : ReaderIntent

data class SendReplacementToGlobal(
    val from: String,
    val to: String,
    val caseSensitive: Boolean = false,
) : ReaderIntent

// ── 4. Shared UI Component Call Sites (com.personal.apptruyen.ui.components.TextReplacementDictionaryDialog) ──
@Composable
fun TextReplacementSummarySection(
    items: ImmutableList<TextReplacementItem>,
    isGlobal: Boolean,
    onAdd: (from: String, to: String, caseSensitive: Boolean) -> Unit,
    onUpdate: (index: Int, expected: TextReplacementItem, from: String, to: String, caseSensitive: Boolean) -> Unit,
    onRemove: (index: Int, expected: TextReplacementItem) -> Unit,
    modifier: Modifier = Modifier,
    onSendToGlobal: ((from: String, to: String, caseSensitive: Boolean) -> Unit)? = null,
)
```

---

### 6. Các Bước Thực Hiện

- [ ] **Bước 1: Mở rộng `TextReplacementHelper` hỗ trợ `TextReplacementItem` và `caseSensitive`**
  - Định nghĩa `data class TextReplacementItem(val from: String, val to: String, val caseSensitive: Boolean = false)` và hằng số `const val LEGACY_JSON_DEFAULT_CASE_SENSITIVE = true` trong `util/TextReplacementHelper.kt`.
  - Triển khai `parseReplacementItems(json: String?)`: nếu `obj.has("caseSensitive")` thì đọc `obj.optBoolean("caseSensitive", false)`, ngược lại dùng `LEGACY_JSON_DEFAULT_CASE_SENSITIVE` (`true`) để bảo toàn 100% hành vi của dữ liệu JSON cũ; triển khai `serializeReplacementItems(list: List<TextReplacementItem>)` ghi đủ `"from"`, `"to"`, `"caseSensitive"`.
  - Triển khai `applyReplacementItems` và `applyAllReplacementItems` sử dụng `result.replace(item.from, item.to, ignoreCase = !item.caseSensitive)` khi `item.from.isNotBlank()`.
  - Giữ nguyên chữ ký các hàm `Pair<String, String>` hiện hữu để tương thích ngược, đánh dấu `@Deprecated` trên `serializeCustomReplacements(List<Pair<String, String>>)` để đảm bảo mọi đường ghi dữ liệu sản phẩm đều dùng `serializeReplacementItems`.
- [ ] **Bước 2: Cập nhật `ITextReplacementRepository` và `TextReplacementRepository` đảm bảo tính nguyên tử & chống TOCTOU**
  - Trên `ITextReplacementRepository`: bổ sung `caseSensitive: Boolean = false` vào `addStoryReplacement`, thêm `expected: TextReplacementItem? = null` vào `removeStoryReplacement`, thêm hàm `updateStoryReplacement(storyId, index, from, to, caseSensitive, expected = null)`, và cho hàm 2 tham số `sendReplacementToGlobal(from, to)` mặc định ủy quyền sang `sendReplacementToGlobal(from, to, caseSensitive = false)`.
  - Trong `TextReplacementRepository`:
    - Khai báo `private val storyReplacementMutex = kotlinx.coroutines.sync.Mutex()` để tuần tự hóa toàn bộ chu kỳ đọc–sửa–ghi (`getStoryReplacements` → sửa list → `setStoryCustomReplacements`) trong `addStoryReplacement`, `updateStoryReplacement`, và `removeStoryReplacement`.
    - Bỏ qua thao tác `addStoryReplacement` / `updateStoryReplacement` nếu `from.isBlank()`.
    - Trong `updateStoryReplacement` và `removeStoryReplacement`: kiểm tra `index in list.indices && (expected == null || list[index] == expected)` bên trong `storyReplacementMutex.withLock`; nếu lệch (do thao tác xóa trước đó vừa dịch chuyển danh sách) thì bỏ qua an toàn.
    - Trong `sendReplacementToGlobal`: bản 2 tham số gọi `sendReplacementToGlobal(from, to, caseSensitive = false)`. Bản 3 tham số thực hiện **toàn bộ** quá trình đọc `prefs[SettingsKeys.CUSTOM_REPLACEMENTS]`, parse bằng `parseReplacementItems`, kiểm tra trùng/cập nhật, và ghi lại bằng `serializeReplacementItems` **bên trong** `dataStore.edit { prefs -> ... }`:
      - Nếu đã có mục trùng cả `from`, `to`, và `caseSensitive` → giữ nguyên, trả về `false`.
      - Nếu có mục trùng `from` và `to` nhưng khác `caseSensitive` → cập nhật `caseSensitive` tại chỗ trong `prefs[SettingsKeys.CUSTOM_REPLACEMENTS]`, trả về `true`.
      - Nếu chưa có → thêm `TextReplacementItem(from, to, caseSensitive)` vào cuối danh sách, trả về `true`.
- [ ] **Bước 3: Cập nhật `SettingsViewModel` và `ReaderViewModel`**
  - Trong `SettingsViewModel`:
    - Đổi `SettingsState.customReplacements` sang `ImmutableList<TextReplacementItem>`.
    - Cập nhật `addCustomReplacement(from, to, caseSensitive = false)`, thêm `updateCustomReplacement(index, from, to, caseSensitive, expected = null)` và sửa `removeCustomReplacement(index, expected = null)` thực hiện đọc–kiểm tra `expected`–sửa–ghi nguyên tử bằng `parseReplacementItems` / `serializeReplacementItems` bên trong `dataStore.edit { prefs -> ... }`.
  - Trong `ReaderViewModel`:
    - Đổi `ReaderState.storyReplacements`, `customReplacements`, và `storyCustomReplacements` sang danh sách `TextReplacementItem`.
    - Mở rộng `ReaderIntent.AddStoryReplacement`, `ReaderIntent.RemoveStoryReplacement` (`expected`), `ReaderIntent.SendReplacementToGlobal` (`caseSensitive`), và thêm `ReaderIntent.UpdateStoryReplacement(index, from, to, caseSensitive, expected)`.
    - Cập nhật `processRawChapterContent` gọi `TextReplacementHelper.applyAllReplacementItems`.
    - Triển khai `updateStoryReplacement` gọi `textReplacementRepository.updateStoryReplacement(storyId, index, from, to, caseSensitive, expected)`, `reloadStoryReplacements()` và `reprocessCurrentChapter()`.
    - Trong `sendReplacementToGlobal(from, to, caseSensitive)`:
      - Khi `!caseSensitive` gọi `textReplacementRepository.sendReplacementToGlobal(from, to)` (tương thích stub MockK 2 tham số) và khi `caseSensitive` gọi `textReplacementRepository.sendReplacementToGlobal(from, to, true)`.
      - Khi kết quả trả về `true`: kiểm tra `val existingIdx = customReplacements.indexOfFirst { it.from == from && it.to == to }`. Nếu `existingIdx >= 0` (nhánh cập nhật `caseSensitive` tại chỗ), thay thế `customReplacements[existingIdx] = TextReplacementItem(from, to, caseSensitive)` trong bộ nhớ và phát `snackbarMessage = "Đã cập nhật chế độ hoa thường cho \"$from\" → \"$to\" trong cài đặt chung"`; nếu `existingIdx < 0` (nhánh thêm mới), nối thêm vào `customReplacements` và phát `snackbarMessage = "Đã thêm \"$from\" → \"$to\" vào cài đặt thay chữ chung"`. Luôn gọi `reprocessCurrentChapter()` ở cả hai nhánh.
- [ ] **Bước 4: Xây dựng shared UI component `ui/components/TextReplacementDictionaryDialog.kt`**
  - Tạo `TextReplacementSummarySection`: hiển thị số lượng từ thay thế hiện có (kèm tối đa 3 thẻ xem trước ngắn gọn nếu danh sách khác rỗng), nút `"Từ điển thay chữ (${items.size})"` mở `TextReplacementDictionaryDialog` và nút `"Thêm từ thay thế"` mở nhanh `TextReplacementEditDialog`.
  - Tạo `TextReplacementDictionaryDialog` (theo mẫu Ảnh 1):
    - `Dialog` toàn màn hình (`usePlatformDefaultWidth = false`) với `Surface` nền theo `MaterialTheme.colorScheme.background`.
    - Thanh tiêu đề gồm nút Quay lại (`←`), tiêu đề `"Từ điển"`, và nút `+` thêm từ mới.
    - Thanh tìm kiếm bo tròn (`RoundedCornerShape(28.dp)`) với icon `Icons.Default.Search`, placeholder `"Nhập từ khóa"` và nút xóa nhanh từ khóa, lọc không phân biệt hoa thường theo `from` hoặc `to` nhưng giữ nguyên `originalIndex` (`items.withIndex().filter { ... }`).
    - Bảng danh sách có hàng tiêu đề cố định (`Tìm` | `Thay thế` | `Hoa thường` | `Xóa`, kèm cột `Chung` khi `onSendToGlobal != null`) và `LazyColumn` sử dụng `key = { (originalIndex, item) -> "$originalIndex:${item.from}:${item.to}:${item.caseSensitive}" }` (đảm bảo duy nhất tuyệt đối nhờ `originalIndex` ngay cả khi có 2 mục trùng `from`/`to`):
      - Chạm vào ô `Tìm` / `Thay thế` mở `TextReplacementEditDialog` để sửa mục tại `originalIndex` (truyền kèm `expected = item`).
      - Ô `Hoa thường` chứa `Checkbox` bật/tắt trực tiếp `onUpdate(originalIndex, item, item.from, item.to, newChecked)`.
      - Ô `Xóa` chứa nút `✕` (`Icons.Default.Close`, `contentDescription = "Xóa"`) gọi `onRemove(originalIndex, item)`.
  - Tạo `TextReplacementEditDialog` (theo mẫu Ảnh 2):
    - Tiêu đề `"Thông tin"`.
    - Dòng preview động bằng `buildAnnotatedString`: `"Tất cả những từ: <Từ cần thay thế> sẽ được thay bằng <Từ thay thế> trong tất cả các bộ truyện"` (nếu `isGlobal = true`) hoặc `"trong bộ truyện này"` (nếu `isGlobal = false`), tô đậm và đổi màu nổi bật cho 2 cụm từ.
    - Hai ô nhập `"Từ cần thay thế"` và `"Từ thay thế"`, hàng `"Phân biệt hoa thường"` kèm `Checkbox` (mặc định `false` khi thêm mới), và 2 nút `"Lưu lại"` / `"Đóng lại"`.
- [ ] **Bước 5: Tích hợp vào `SettingsScreen`, `ReaderSettingsPanel`, `ReaderScreen` và kiểm tra biên dịch Tier 1**
  - Trong `SettingsScreen.kt`: thay khối danh sách phẳng (dòng 278–389) bằng `TextReplacementSummarySection(items = state.customReplacements, isGlobal = true, ...)`.
  - Trong `ReaderSettingsPanel.kt` và `ReaderScreen.kt`: thay khối danh sách phẳng (dòng 496–606) bằng `TextReplacementSummarySection(items = storyReplacements, isGlobal = false, ...)`, đưa `ReaderSettingsPanel.kt` xuống dưới 600 dòng.
  - Chạy `.\gradlew.bat compileDebugKotlin` xác nhận biên dịch sạch.
- [ ] **Bước 6: Viết Unit Tests, chạy Quality Gate Tier 2 và cập nhật tài liệu**
  - Bổ sung unit tests trong `TextReplacementHelperTest.kt`, `TextReplacementRepositoryTest.kt`, `SettingsViewModelTest.kt`, và `ReaderViewModelTest.kt` theo đúng Test Contract (TC-1 → TC-10).
  - Chạy `.\gradlew.bat ktlintCheck; .\gradlew.bat testDebugUnitTest; .\gradlew.bat assembleDebug`.
  - Cập nhật `docs/architecture.md`, `docs/progress.md`, `docs/changelog.md`, `docs/plans/README.md`.

---

### 7. Test Contract

| # | Hành vi (ngôn ngữ nghiệp vụ) | Input | Kết quả mong đợi / thông báo lỗi tiếng Việt |
| :-: | :--- | :--- | :--- |
| **TC-1** | Thay chữ khi **TẮT** phân biệt hoa thường (`caseSensitive = false`, mặc định cho mục mới trên UI) | Văn bản `"Mây tía bay qua, mây tía rực rỡ, MÂY TÍA trên trời"`, quy tắc `TextReplacementItem(from = "Mây tía", to = "Tử Vân", caseSensitive = false)` | Thay thế tất cả các biến thể hoa/thường: `"Tử Vân bay qua, Tử Vân rực rỡ, Tử Vân trên trời"`. |
| **TC-2** | Thay chữ khi **BẬT** phân biệt hoa thường (`caseSensitive = true`) | Văn bản `"Mây tía bay qua, mây tía rực rỡ, MÂY TÍA trên trời"`, quy tắc `TextReplacementItem(from = "Mây tía", to = "Tử Vân", caseSensitive = true)` | Chỉ thay thế từ khớp chính xác chữ hoa/thường: `"Tử Vân bay qua, mây tía rực rỡ, MÂY TÍA trên trời"`. |
| **TC-3** | Kết hợp 2 lớp thay thế Global (chạy trước) + Per-story (chạy sau) với `caseSensitive` độc lập | Văn bản `"tu liên cùng Tu Liên"`, Global: `("tu liên", "Tu Tiên", caseSensitive = false)`, Story: `("Tu Tiên", "Đạo Tổ", caseSensitive = true)` | Global đổi cả 2 cụm thành `"Tu Tiên cùng Tu Tiên"`, sau đó Story khớp chính xác `"Tu Tiên"` đổi thành `"Đạo Tổ cùng Đạo Tổ"`. |
| **TC-4** | Bảo toàn hành vi JSON cũ thiếu khóa `"caseSensitive"` (`caseSensitive = true`) & khứ hồi JSON mới có khóa `"caseSensitive"` | (a) JSON cũ `[{"from":"nàn","to":"nàng"}]` áp dụng lên `"Nàn nhìn nàn"`; (b) JSON mới `[{"from":"abc","to":"xyz","caseSensitive":false},{"from":"Mây tía","to":"Tử Vân","caseSensitive":true}]` | (a) `parseReplacementItems` nạp `TextReplacementItem("nàn", "nàng", caseSensitive = true)` và áp dụng ra `"Nàn nhìn nàng"` (giữ nguyên chữ hoa đầu câu `"Nàn"`); (b) khứ hồi `serializeReplacementItems` → `parseReplacementItems` bảo toàn 100% `false` và `true`. |
| **TC-5** | Bỏ qua từ gốc rỗng hoặc chỉ chứa khoảng trắng (`from.isBlank()`) | `TextReplacementItem(from = "   ", to = "Tử Vân", caseSensitive = false)` trên `"Mây tía"` | Văn bản giữ nguyên `"Mây tía"`; `addStoryReplacement` / `addCustomReplacement` không lưu mục có `from.isBlank()`. |
| **TC-6** | Thêm, sửa (`updateStoryReplacement`), xóa, gửi sang cài đặt chung và đồng thời (`Mutex`) trong `TextReplacementRepository` | (a) Thêm 2 mục rồi chạy đồng thời 2 coroutine `updateStoryReplacement` trên index 0 và index 1; (b) gọi `sendReplacementToGlobal("Mây tía", "Tử Vân", false)` rồi gọi lại với `caseSensitive = true` | (a) Cả 2 mục tại index 0 và index 1 đều được bảo toàn cập nhật nhờ `storyReplacementMutex`; (b) Lần 1 thêm mới trả `true`, lần 2 cùng `caseSensitive = false` trả `false`, lần 3 đổi sang `caseSensitive = true` cập nhật tại chỗ và trả `true`. |
| **TC-7** | Quản lý từ điển toàn cục trong `SettingsViewModel` & chống ghi đè nhầm khi lệch `expected` (TOCTOU) | Danh sách có `[A, B, C]`. Xóa `A` tại index 0 (`removeCustomReplacement(0, expected = A)`), sau đó gọi `updateCustomReplacement(1, ..., expected = B)` (index 1 cũ của `B`, nay đã là `C`) | `A` bị xóa, danh sách còn `[B, C]`; lệnh `update` với `index = 1, expected = B` thấy `list[1] == C != B` nên bỏ qua an toàn, không ghi đè mất `C`. |
| **TC-8** | Cập nhật mục thay chữ riêng trong `ReaderViewModel` (`UpdateStoryReplacement`) tự động xử lý lại chương đang đọc (`reprocessCurrentChapter`) | Chương đang mở có nội dung `"mây tía và Mây tía"`, ban đầu có quy tắc `("Mây tía", "Tử Vân", caseSensitive = false)` → gửi `ReaderIntent.UpdateStoryReplacement(0, "Mây tía", "Tử Vân", caseSensitive = true)` | Khi `caseSensitive = false`, nội dung chương là `"Tử Vân và Tử Vân"`; ngay sau khi gửi `UpdateStoryReplacement(..., caseSensitive = true)`, nội dung chương tự động cập nhật thành `"mây tía và Tử Vân"` từ `contentCache` mà không gọi lại mạng. |
| **TC-9** | `ReaderViewModel.sendReplacementToGlobal` cập nhật `caseSensitive` tại chỗ trong bộ nhớ khi từ đã có trong cài đặt chung nhưng khác cờ `caseSensitive` | Chương có `"mây tía và Mây tía"`, global đang có `("Mây tía", "Tử Vân", caseSensitive = true)` (chương hiển thị `"mây tía và Tử Vân"`). Gửi `SendReplacementToGlobal("Mây tía", "Tử Vân", caseSensitive = false)` | `customReplacements` trong bộ nhớ cập nhật mục đó sang `caseSensitive = false`, chương xử lý lại ngay thành `"Tử Vân và Tử Vân"`, và phát `snackbarMessage = "Đã cập nhật chế độ hoa thường cho \"Mây tía\" → \"Tử Vân\" trong cài đặt chung"`. |

---

### 8. Verification Plan

#### Unit Tests cần thêm / cập nhật
1. `app/src/test/java/com/personal/apptruyen/util/TextReplacementHelperTest.kt`:
   - Thêm các test cho `TC-1`, `TC-2`, `TC-3`, `TC-4` (cả JSON cũ không khóa `"caseSensitive"` mặc định `true` trên `"Nàn nhìn nàn"` và JSON mới có khóa `"caseSensitive"`), `TC-5`.
2. `app/src/test/java/com/personal/apptruyen/data/repository/TextReplacementRepositoryTest.kt` (`[NEW]`):
   - Kiểm thử `addStoryReplacement`, `updateStoryReplacement` (bao gồm biên `index`, `from.isBlank()`, chạy song song 2 coroutine kiểm chứng `storyReplacementMutex`, và chặn TOCTOU khi `expected` không khớp), `removeStoryReplacement`, và `sendReplacementToGlobal` (thêm mới, trùng hoàn toàn trả về `false`, trùng `from`/`to` nhưng đổi `caseSensitive` cập nhật tại chỗ và trả về `true`, bảo toàn `caseSensitive` của các mục khác) (`TC-6`).
3. `app/src/test/java/com/personal/apptruyen/ui/settings/SettingsViewModelTest.kt`:
   - Kiểm thử `addCustomReplacement`, `updateCustomReplacement`, và `removeCustomReplacement` cập nhật `state.customReplacements`, `SettingsKeys.CUSTOM_REPLACEMENTS`, và chặn TOCTOU khi `expected` lệch (`TC-7`).
4. `app/src/test/java/com/personal/apptruyen/ui/reader/ReaderViewModelTest.kt`:
   - Kiểm thử `AddStoryReplacement`, `UpdateStoryReplacement`, và `SendReplacementToGlobal` (cả thêm mới và cập nhật `caseSensitive` tại chỗ) kèm xác minh `reprocessCurrentChapter()` cập nhật ngay `state.content` (`TC-8`, `TC-9`).

#### Lệnh thực thi kiểm chứng (Quality Gate Tier 2)
```powershell
.\gradlew.bat testDebugUnitTest --tests "com.personal.apptruyen.util.TextReplacementHelperTest" --tests "com.personal.apptruyen.data.repository.TextReplacementRepositoryTest" --tests "com.personal.apptruyen.ui.settings.SettingsViewModelTest" --tests "com.personal.apptruyen.ui.reader.ReaderViewModelTest" --tests "com.personal.apptruyen.ui.detail.StoryDetailViewModelTest"
.\gradlew.bat ktlintCheck; .\gradlew.bat testDebugUnitTest; .\gradlew.bat assembleDebug
```

#### Wiring Probes & Negative Probes (`.agents/agents/reviewer/evidence-bar.md` §4.5.3)
- **Wiring Probe (Kiểm chứng kết nối thực tế)**:
  - Xác minh `ReaderViewModel.processRawChapterContent` gọi `TextReplacementHelper.applyAllReplacementItems` và `sendReplacementToGlobal` cập nhật `customReplacements` tại chỗ bằng bài test `TC-8` và `TC-9` trong `ReaderViewModelTest`.
- **Negative Probe (Kiểm chứng chặn đầu vào không hợp lệ, chỉ số ngoài biên & TOCTOU `remove` → `update`)**:
  - Trong `TextReplacementRepositoryTest` và `SettingsViewModelTest`:
    1. Gọi `addStoryReplacement` / `addCustomReplacement` với `from = "   "` (rỗng/khoảng trắng) và `update`/`remove` với `index = -1` và `index = 99`.
    2. Mô phỏng chuỗi TOCTOU: danh sách `[A, B, C]` → xóa `A` tại index 0 → gọi `update(index = 1, ..., expected = B)` (index 1 lúc này là `C`) và `remove(index = 0, expected = A)` → xác nhận `C` và `B` không bị ghi đè hay xóa nhầm.

#### Kiểm thử thủ công trên thiết bị
1. Vào **Cài đặt → Thay chữ tự động → Từ điển thay chữ**:
   - Bấm `+`, nhập `"Mây tía"` và `"Tử Vân"`, quan sát dòng preview động `"Tất cả những từ: Mây tía sẽ được thay bằng Tử Vân trong tất cả các bộ truyện"`, bấm `"Lưu lại"`.
   - Bật/tắt trực tiếp ô `Checkbox` ở cột `"Hoa thường"` trên bảng `"Từ điển"`.
   - Nhập từ khóa vào ô `"Nhập từ khóa"` để lọc danh sách, chạm vào dòng kết quả để sửa qua hộp thoại `"Thông tin"`, và bấm `✕` ở cột `"Xóa"` để xóa.
2. Mở một chương truyện bất kỳ → **Cài đặt đọc → Thay chữ riêng cho truyện**:
   - Mở `"Từ điển thay chữ"`, thêm từ mới (kiểm tra dòng preview hiển thị `"trong bộ truyện này"`), bật/tắt `"Hoa thường"`, đóng bảng cài đặt và quan sát nội dung chương cập nhật tức thì.

---

### 9. Rủi Ro & Phòng Ngừa

| Rủi ro | Mức (Cao/TB/Thấp) | Technical control (`[SRC]` / Negative Probe) |
| :--- | :---: | :--- |
| **R1**: Khi tìm kiếm/lọc từ khóa hoặc khi bấm Xóa (`✕`) và Checkbox `"Hoa thường"` liên tiếp trước khi UI recompose, chỉ số `index` bị dịch chuyển gây sửa/xóa nhầm mục khác (TOCTOU). | **Cao** | (1) Trong `TextReplacementDictionaryDialog.kt`, lọc trên `items.withIndex()` để giữ `originalIndex`. (2) Truyền `expected: TextReplacementItem` vào `onUpdate` / `onRemove` và kiểm tra `list.getOrNull(index) == expected` bên trong vùng khóa nguyên tử (`dataStore.edit` / `storyReplacementMutex.withLock`). Kiểm chứng bằng Negative Probe `TC-7`. |
| **R2**: Người dùng bấm liên tiếp nhiều `Checkbox` `"Hoa thường"` trên bảng `"Từ điển"` (cả Global trong `SettingsViewModel` và Per-story trong `TextReplacementRepository`) hoặc gọi `sendReplacementToGlobal` đồng thời gây mất cập nhật. | **Cao** | (1) Trong `SettingsViewModel.kt`, đọc–sửa–ghi nguyên tử trong `dataStore.edit`. (2) Trong `@Singleton TextReplacementRepository.kt`, bọc toàn bộ thao tác per-story bằng `storyReplacementMutex.withLock` và đưa toàn bộ `sendReplacementToGlobal` vào trong `dataStore.edit`. Kiểm chứng bằng `TC-6` & `TC-7`. |
| **R3**: Dữ liệu JSON cũ trong `SettingsDataStore` (`CUSTOM_REPLACEMENTS`), cột `stories.storyCustomReplacements` hoặc file Backup ZIP chưa có khóa `"caseSensitive"` bị đổi ngầm ngữ nghĩa từ phân biệt hoa/thường sang không phân biệt hoa/thường. | **Trung bình** | Trong `TextReplacementHelper.parseReplacementItems`, kiểm tra `if (obj.has("caseSensitive")) obj.optBoolean("caseSensitive", false) else true` (`LEGACY_JSON_DEFAULT_CASE_SENSITIVE = true`), giữ nguyên 100% hành vi của dữ liệu cũ; mục mới tạo trên UI mặc định `false` và luôn ghi tường minh khóa `"caseSensitive"`. Kiểm chứng bằng `TC-4`. |
| **R4**: Nhánh cập nhật `caseSensitive` tại chỗ trong `sendReplacementToGlobal` không đồng bộ `ReaderViewModel.customReplacements` trong bộ nhớ, hoặc làm gãy stub MockK 2 tham số trong `ReaderViewModelTest.kt:532`. | **Trung bình** | Trong `ReaderViewModel.sendReplacementToGlobal`, cập nhật phần tử tại `existingIdx` trong `customReplacements`, phát thông báo Snackbar riêng và gọi `reprocessCurrentChapter()`. Giữ ủy quyền 2 tham số → 3 tham số trên `ITextReplacementRepository`. Kiểm chứng bằng `TC-9`. |
| **R5**: Kích thước file `ReaderSettingsPanel.kt` (hiện 633 dòng) và `SettingsScreen.kt` (hiện 1.093 dòng) vượt ngưỡng khuyến nghị; `LazyColumn` crash nếu trùng key. | **Thấp** | Tách UI từ điển sang `ui/components/TextReplacementDictionaryDialog.kt` (đưa `ReaderSettingsPanel.kt` xuống ~545 dòng < 600 dòng), và dùng `key = { (originalIndex, item) -> "$originalIndex:${item.from}:${item.to}:${item.caseSensitive}" }` luôn duy nhất. |

#### Adversarial Timeline (`.agents/agents/reviewer/evidence-bar.md` §4.5.2)

```text
Cơ chế 1 (Đồng thời - Axis 1a & 1b): Bật/tắt liên tiếp Checkbox "Hoa thường" trên bảng "Từ điển" (Global DataStore & Per-story Room)
T1: User tích Checkbox dòng 0 (onUpdate(0, expected = A, ..., true)) -> Coroutine 1 vào dataStore.edit (Global) hoặc storyReplacementMutex.withLock (Per-story)
T2: Ngay lập tức User tích Checkbox dòng 1 (onUpdate(1, expected = B, ..., true)) trước khi StateFlow của UI kịp emit
T3: Mutex nội bộ của DataStore / storyReplacementMutex tuần tự hóa Coroutine 2 chạy sau khi Coroutine 1 đã ghi xong JSON_v1; Coroutine 2 đọc JSON_v1, kiểm tra list[1] == B khớp và ghi JSON_v2
Kết cục: Cả dòng 0 và dòng 1 đều có caseSensitive = true trong lưu trữ và UI state -> AN TOÀN

Cơ chế 2 (TOCTOU - Axis 2a & 2b): Lọc từ khóa & Bấm Xóa xen kẽ Checkbox trước khi UI recompose
T1: Danh sách gốc là [A, B, C]. User bấm nút Xóa (✕) ở dòng A (index = 0, expected = A) -> remove(0, expected = A) vào hàng đợi
T2: Trước khi danh sách kịp recompose, User bấm Checkbox "Hoa thường" ở dòng B đang hiển thị tại index 1 -> update(1, expected = B, ..., true) vào hàng đợi
T3: remove(0, expected = A) chạy trong vùng nguyên tử: list.getOrNull(0) == A -> xóa A, danh sách còn [B, C]
T4: update(1, expected = B, ...) chạy trong vùng nguyên tử: list.getOrNull(1) hiện là C != B -> bị từ chối an toàn, không ghi đè C
Kết cục: Mục C được bảo toàn nguyên vẹn, không bị ghi đè bởi B -> AN TOÀN

Cơ chế 3 (Dữ liệu cũ & Khôi phục sao lưu - Axis 3): Khôi phục bản sao lưu ZIP cũ hoặc đọc DB từ phiên bản trước
T1: BackupRepository / Room cung cấp chuỗi JSON cũ [{"from":"nàn","to":"nàng"}] (không có khóa "caseSensitive")
T2: ReaderViewModel nạp chương truyện có câu "Nàn nhìn nàn" và gọi TextReplacementHelper.parseReplacementItems(json)
T3: !obj.has("caseSensitive") trả về LEGACY_JSON_DEFAULT_CASE_SENSITIVE = true; chương truyện được thay thành "Nàn nhìn nàng" (giữ nguyên chữ hoa đầu câu)
Kết cục: Bảo toàn 100% hành vi thay chữ của dữ liệu cũ và không lỗi parse -> AN TOÀN

Cơ chế 4 (Đồng bộ bộ nhớ & Vòng đời Android - Axis 4): Chuyển mục per-story sang global với cờ caseSensitive mới khi đang đọc ở giữa chương (scrollPosition = 28)
T1: User đang đọc chương 1 ở đoạn 28 (currentScrollPosition = 28); Global đang có ("Mây tía", "Tử Vân", caseSensitive = true)
T2: User gửi mục per-story ("Mây tía", "Tử Vân", caseSensitive = false) sang cài đặt chung
T3: TextReplacementRepository cập nhật mục trong DataStore sang caseSensitive = false (trong dataStore.edit) và trả về true; ReaderViewModel cập nhật customReplacements[existingIdx] trong bộ nhớ sang false, hiện Snackbar "Đã cập nhật chế độ hoa thường..." và gọi reprocessCurrentChapter() giữ nguyên initialScrollIndex = 28
Kết cục: Bộ nhớ ReaderViewModel, DataStore và nội dung chương đang đọc đồng bộ tức thì mà không nhảy vị trí cuộn -> AN TOÀN
```

---

### 10. Cập Nhật Docs

Theo ma trận trong `.agents/rules/06-docs.md`, sau khi triển khai code cần cập nhật:
- `docs/architecture.md`:
  - Cập nhật sơ đồ cấu trúc thư mục mục `ui/components/` (thêm `TextReplacementDictionaryDialog.kt`) và mô tả `util/TextReplacementHelper.kt` (`TextReplacementItem` hỗ trợ `caseSensitive`).
  - Cập nhật timestamp `"Cập nhật lần cuối"`.
- `docs/progress.md`:
  - Thêm mục hoàn thành tính năng phân biệt hoa/thường khi thay chữ và giao diện Từ điển thay chữ (`TextReplacementDictionaryDialog` + `TextReplacementEditDialog`), cập nhật bảng lịch sử thay đổi và timestamp.
- `docs/changelog.md`:
  - Thêm entry dưới `## [Unreleased]` (`### Thêm mới` & `### Sửa đổi`) mô tả tùy chọn phân biệt hoa thường (`caseSensitive`) và giao diện quản lý `"Từ điển"` + hộp thoại `"Thông tin"`.
- `docs/plans/README.md`:
  - Thêm kế hoạch `2026-10-30-text-replacement-case-sensitivity-and-dictionary-ui-implementation-plan.md` vào Bảng Trạng Thái và đổi trạng thái plan sang `Completed` khi hoàn tất.

## Audit History

<!-- Reviewer chỉ append "### Audit Run N" ở dưới. Không sửa phần Technical Plan. -->

### Audit Run 1

- **Reviewer**: Claude Code CLI (Claude Opus 5.5 — `claude-opus-5-5`)
- **Ngày**: 2026-10-30
- **Đối tượng**: Technical Plan Revision 1

#### Kiểm Chứng Lỗi Cũ

Không có Audit Run trước — đây là lượt thẩm định đầu tiên, không có mã lỗi cũ cần kiểm lại.

#### Đối chiếu file `[MODIFY]` với code thật

| File | Giả định của plan | Kết quả đối chiếu |
| :--- | :--- | :--- |
| `util/TextReplacementHelper.kt` | `applyReplacements` gọi `result.replace(from, to)`; parse/serialize 2 khóa `from`/`to` | Khớp — `[SRC]` `TextReplacementHelper.kt:46-48, 71-74, 85-110`. |
| `data/repository/ITextReplacementRepository.kt` | 5 hàm, `sendReplacementToGlobal(from, to)` | Khớp — `[SRC]` `ITextReplacementRepository.kt:10-31`. |
| `data/repository/TextReplacementRepository.kt` | add/remove đọc–sửa–ghi qua `storyDao`; `sendReplacementToGlobal` đọc `data.first()` rồi `edit` | Khớp — `[SRC]` `TextReplacementRepository.kt:35-49, 51-66, 68-84`. Lưu ý: cả 3 hàm đều **không nguyên tử** (xem F2). |
| `ui/settings/SettingsViewModel.kt` | add/remove đọc `state.value` rồi `edit` | Khớp — `[SRC]` `SettingsViewModel.kt:220-241`. |
| `ui/settings/SettingsScreen.kt` | Danh sách phẳng 278–389 | Khớp — `[SRC]` `SettingsScreen.kt:278-389`. |
| `ui/reader/ReaderViewModel.kt` | `customReplacements`/`storyCustomReplacements` dạng `Pair`, `processRawChapterContent`, `reprocessCurrentChapter`, `sendReplacementToGlobal` | Khớp — `[SRC]` `ReaderViewModel.kt:239-241, 277-285, 465-536, 1021-1079`. |
| `ui/reader/ReaderScreen.kt` | Nối dây callback 413, 429–433 | Khớp — `[SRC]` `ReaderScreen.kt:412-433`. |
| `ui/reader/ReaderSettingsPanel.kt` | 633 dòng, danh sách phẳng 496–606 | Khớp — `[SRC]` `ReaderSettingsPanel.kt:496-606`, dòng cuối 633. Bỏ ~111 dòng, thêm ~10 dòng → ~530 dòng, dưới ngưỡng 600. |
| `ui/components/` | Thư mục tồn tại, file `[NEW]` chưa có | Khớp — Glob trả về `AnimatedEmptyState.kt`, `StoryCardPremium.kt`, `ShimmerEffect.kt`; chưa có `TextReplacementDictionaryDialog.kt`. |
| `test/.../ReaderViewModelTest.kt` | Stub `sendReplacementToGlobal("nàn", "nàng")` không relaxed | Khớp — `[SRC]` `ReaderViewModelTest.kt:532`. Test `:370` dùng JSON cũ `Bì Khả Khâu` vẫn pass ở cả hai chế độ hoa/thường. |

#### Phát Hiện Mới (Fresh Findings & Regression Check)

**F1 — `[MAJOR]` Đổi ngầm ngữ nghĩa của mọi quy tắc đã lưu (phân biệt hoa thường → không phân biệt), trái với khẳng định "tương thích ngược".**
- `[SRC]` `TextReplacementHelper.kt:48` và `:73`: hành vi hiện tại là `result.replace(from, to)` với `ignoreCase = false` mặc định, tức **mọi quy tắc hiện có đều đang phân biệt hoa thường**. Plan tự trích dòng này ở mục 2 (dòng 46).
- Mục 3 Giả định 1 và Bước 1 cho `optBoolean("caseSensitive", false)` → mọi bản ghi JSON cũ (DataStore `CUSTOM_REPLACEMENTS`, cột `stories.storyCustomReplacements`, file backup ZIP) sẽ chuyển sang `ignoreCase = true` ngay sau khi cập nhật app.
- Hậu quả cụ thể: quy tắc cũ `("nàn", "nàng")` sẽ đổi luôn `"Nàn"` ở đầu câu thành `"nàng"` (mất chữ hoa); quy tắc `("lão", "ông")` (`StoryDetailViewModelTest.kt:330`) sẽ đổi luôn `"Lão"` trong tên riêng. Dữ liệu JSON không lỗi parse, nhưng cách thay chữ của người dùng bị đổi mà họ không biết. Plan không có Test Contract nào khóa hành vi với dữ liệu cũ ở mức nội dung chương.
- Yêu cầu: (a) map bản ghi **thiếu khóa** `caseSensitive` thành `true` (giữ đúng hành vi cũ), còn mục **mới** tạo từ UI mặc định `false`; hoặc (b) ghi rõ `[NEEDS_PRODUCT_DECISION]` để User chọn đổi hành vi và cập nhật changelog/ghi chú phát hành. Bổ sung TC cho lựa chọn đó (JSON cũ + văn bản có chữ hoa đầu câu), và sửa lại khẳng định "tương thích ngược 100%" ở Goal/Assumption/R3/Timeline Cơ chế 3.

**F2 — `[MAJOR]` Đọc–sửa–ghi từ điển riêng của truyện (Room) và `sendReplacementToGlobal` (DataStore) không nguyên tử → mất cập nhật khi bật/tắt Checkbox "Hoa thường" liên tiếp.**
- `[SRC]` `TextReplacementRepository.kt:40-48`: `getStoryReplacements` → sửa list trong bộ nhớ → `setStoryCustomReplacements`, gồm hai lệnh `suspend` DAO riêng (`StoryDao.kt:82-89`), không có `@Transaction` hay `Mutex`.
- `[SRC]` `ReaderViewModel.kt:1025-1036`: mỗi intent chạy một `viewModelScope.launch` riêng, nên hai coroutine xen nhau ở điểm suspend của DAO.
- `[SRC]` `TextReplacementRepository.kt:72-82`: `dataStore.data.first()` và `dataStore.edit { ... }` là hai bước tách rời, nên có thể ghi đè thay đổi `SettingsViewModel` vừa ghi trong `edit`.
- Plan chỉ xử lý race cho `SettingsViewModel` (R2, Timeline Cơ chế 1), còn bảng Checkbox ở `ReaderSettingsPanel` dùng `UpdateStoryReplacement` → `updateStoryReplacement`, đúng đường đi không nguyên tử ở trên. UI mới khiến việc bấm liên tiếp xảy ra thường xuyên hơn hẳn so với nút Thêm/Xóa cũ.
- Yêu cầu: tuần tự hóa toàn bộ thao tác đọc–sửa–ghi per-story trong `TextReplacementRepository` (ví dụ `Mutex` trong `@Singleton`, hoặc phương thức DAO `@Transaction`), và chuyển phần đọc + kiểm tra trùng của `sendReplacementToGlobal` vào trong lambda `dataStore.edit { prefs -> ... }`. Bổ sung Adversarial Timeline và test đồng thời (2 lần `updateStoryReplacement` chạy song song trên index khác nhau → cả hai đều được giữ).

**F3 — `[MAJOR]` Nhánh "cập nhật `caseSensitive` tại chỗ" của `sendReplacementToGlobal` làm lệch state trong bộ nhớ của `ReaderViewModel`, và hướng ủy quyền giữa hai overload chưa rõ ràng.**
- Plan Bước 2: nếu trùng `from`/`to` nhưng khác `caseSensitive` thì cập nhật tại chỗ và trả `true`.
- `[SRC]` `ReaderViewModel.kt:1045-1048`: khi `added == true`, code chỉ thêm vào `customReplacements` nếu `none { from && to }`. Với nhánh cập nhật tại chỗ, cặp `from`/`to` đã có nên **không** cập nhật cờ trong bộ nhớ → `reprocessCurrentChapter()` (`:1052`) xử lý lại chương bằng cờ cũ cho tới khi mở lại Reader. Snackbar `"Đã thêm ..."` (`:1050`) cũng mô tả sai thao tác.
- Đường đi cụ thể: global có `("Mây tía","Tử Vân", true)`, người dùng gửi mục per-story `("Mây tía","Tử Vân", false)` → ViewModel gọi overload 2 tham số (theo R4) → repo lật cờ trong DataStore thành `false`, trả `true` → bộ nhớ vẫn giữ `true`.
- Bản phác thảo interface (dòng 203–207) cho overload 3 tham số mặc định gọi `sendReplacementToGlobal(from, to)`, tức **bỏ** `caseSensitive`; plan cũng không nói overload 2 tham số trong `TextReplacementRepository` sẽ làm gì. Nếu giữ nguyên thân hàm hiện tại (`parseCustomReplacements`/`serializeCustomReplacements`, `TextReplacementRepository.kt:74-81`) thì mỗi lần gửi sang cài đặt chung sẽ **xóa khóa `caseSensitive` của toàn bộ mục global** (mất dữ liệu cờ).
- Yêu cầu: ghi rõ overload 2 tham số = `sendReplacementToGlobal(from, to, false)` dùng `parseReplacementItems`/`serializeReplacementItems`; đổi chiều default trong interface (2 → 3 tham số), hoặc bỏ default; trong ViewModel thay thế/thêm mục theo `from`/`to` với cờ mới; tách snackbar tiếng Việt riêng cho nhánh cập nhật (vd: `"Đã cập nhật chế độ hoa thường cho ..."`); thêm test cho nhánh này ở cả `TextReplacementRepositoryTest` và `ReaderViewModelTest`.

**F4 — `[MAJOR]` Định danh mục bằng `index` sinh TOCTOU khi Xoá và Checkbox/Sửa xen nhau, nên sửa nhầm mục khác.**
- Timeline Cơ chế 1 chỉ xét 2 lần `update` liên tiếp. Trường hợp nguy hiểm: T1 bấm `✕` dòng 0 → `remove(0)` vào hàng đợi; T2, trước khi danh sách recompose, bấm Checkbox ở dòng đang hiển thị index 1 → `update(1, fromB, toB, true)`; T3 `remove` chạy xong, danh sách dịch lên, index 1 giờ là mục C; T4 `update(1, fromB, toB, ...)` **ghi đè mục C bằng nội dung của B** (mất C, nhân đôi B). `dataStore.edit` tuần tự hóa nhưng không chặn được lỗi này, vì index được chốt ở UI.
- Áp dụng cho cả `SettingsViewModel` (`SettingsViewModel.kt:232-238`, `removeAt(index)`) và per-story (`TextReplacementRepository.kt:61-62`).
- Yêu cầu: `update`/`remove` nhận thêm mục mong đợi (`expected: TextReplacementItem`) và chỉ thao tác khi `list.getOrNull(index) == expected` bên trong vùng nguyên tử (bỏ qua nếu lệch); bổ sung Negative Probe cho chuỗi remove → update.

**F5 — `[MINOR]` Bảng blast-radius thiếu `StoryDetailViewModel`.**
- `[SRC]` `StoryDetailViewModel.kt:57, 95-99, 120, 140`: parse `Pair` từ cùng cột Room, gọi `addStoryReplacement(targetId, from, to)` và `sendReplacementToGlobal(from, to)`; test `StoryDetailViewModelTest.kt:334` có `coVerify { textRepo.addStoryReplacement("s1", "lão", "ông") }`.
- Vẫn biên dịch được nhờ giữ API `Pair` và tham số mặc định (Kotlin gọi qua `DefaultImpls`, nên MockK khớp với `caseSensitive = false`). Grep `ui/` không thấy màn hình nào dùng `storyReplacements` của `DetailState`, nên tác động người dùng thấp. Cần thêm dòng vào bảng blast-radius và ghi rõ quyết định (giữ nguyên hay chuyển sang `TextReplacementItem`).

**F6 — `[MINOR]` `key` của `LazyColumn` chưa được chỉ định trong khi danh sách cho phép trùng `from`.**
- `[SRC]` `TextReplacementRepository.kt:46` và `SettingsViewModel.kt:226` không chống trùng. Nếu triển khai `key = { it.value.from }` thì hai mục trùng `from` sẽ crash `IllegalArgumentException: Key ... was already used`. Cần ghi rõ `key = { it.index }` (originalIndex) hoặc khóa ghép `index` + nội dung.

**F7 — `[MINOR]` Giữ `serializeCustomReplacements(List<Pair>)` ở dạng public dễ làm mất cờ `caseSensitive` nếu sau này còn đường ghi nào dùng nó.** Đề nghị đánh dấu `@Deprecated` (hoặc chỉ dùng trong test) và ghi rõ trong plan rằng mọi đường **ghi** dữ liệu lưu trữ phải dùng `serializeReplacementItems`.

**F8 — `[MINOR]` Chính tả nhãn.** Plan dùng `"Xoá"`, còn UI hiện tại dùng `"Xóa"` (`[SRC]` `ReaderSettingsPanel.kt:531`, `SettingsScreen.kt:315`). Nên thống nhất một kiểu viết.

#### Mental Simulation Matrix (S1–S6 + Domain)

| Kịch bản | Kết quả | Bằng chứng | Chi tiết |
| :--- | :---: | :--- | :--- |
| S1 Cold-start & Build | PASS | `[CMD]` `cmd.exe /d /c gradlew.bat compileDebugKotlin` Exit 0; `[SRC]` `ui/components/` | Baseline biên dịch sạch; không thêm dependency; `String.replace(..., ignoreCase)` có trong stdlib từ lâu, không phụ thuộc `minSdk`; không cần Hilt binding mới (`TextReplacementRepository` đã là `@Singleton`). |
| S2 Lifecycle & Teardown | PASS | `[SRC]` `ReaderViewModel.kt:506-507`, `ReaderScreen.kt:400-403` | Dùng `viewModelScope`, `reprocessJob?.cancel()` có sẵn. Dialog toàn màn hình nằm trong `ReaderSettingsPanel` dưới `AnimatedVisibility`; panel đóng thì dialog đóng theo, không rò tài nguyên. |
| S3 Database & Temporal | FAIL | `[SRC]` `TextReplacementRepository.kt:40-48, 72-82`; `StoryDao.kt:82-89`; `TextReplacementHelper.kt:48` | Room v9 và khóa DataStore giữ nguyên (đúng). Nhưng đọc–sửa–ghi per-story/global không nguyên tử (F2), và dữ liệu cũ bị đổi ngữ nghĩa (F1). |
| S4 Cross-Task State Flow | FAIL | `[SRC]` `ReaderViewModel.kt:1044-1052`; `StoryDetailViewModel.kt:95-140` | State bộ nhớ của Reader lệch với DataStore ở nhánh cập nhật tại chỗ (F3); thiếu `StoryDetailViewModel` trong blast-radius (F5); TOCTOU trên index (F4). |
| S5 Security & Privacy | PASS | `[SRC]` `SettingsDataStore.kt:68, 124`; `ITextReplacementRepository.kt:9-31` | Không đụng cookie/phiên, Manifest, WebView. Từ điển thay chữ vốn đã nằm trong backup, không phải dữ liệu nhạy cảm. Không dùng Regex do người dùng nhập nên không có ReDoS. |
| S6 External Resilience | N/A | — | Không đụng scraper/mạng/TTS engine. `reprocessCurrentChapter` dùng `contentCache` (`ReaderViewModel.kt:499`), không gọi lại mạng. |
| Domain: Làm sạch nội dung | FAIL | `[SRC]` `TextReplacementHelper.kt:46-48, 71-74` | F1: quy tắc cũ đổi hành vi. `ignoreCase = true` của Kotlin so từng ký tự qua `uppercaseChar/lowercaseChar` nên khớp được `Â/â`, `Í/í` dựng sẵn (TC-1 hợp lệ), nhưng sẽ thay chữ hoa đầu câu bằng `to` viết thường. |
| Domain: UI tiếng Việt | PASS | Plan mục 1, Bước 4 | Mọi nhãn mới đều tiếng Việt; chỉ có F8 (chính tả). |
| Domain: Hiệu năng Compose | UNVERIFIED | Plan Bước 4 | `LazyColumn` + `ImmutableList` đúng hướng; `key` chưa chốt (F6). |

#### Adversarial Timelines

```text
S3-A (F2 — per-story lost update):
T1: User tích Checkbox dòng 0 → Intent UpdateStoryReplacement(0, ..., true) → coroutine A: storyDao.getStoryReplacements() → JSON_v0 → suspend
T2: User tích Checkbox dòng 1 → coroutine B: storyDao.getStoryReplacements() → JSON_v0 (A chưa ghi)
T3: A ghi JSON_v1 (dòng 0 = true)
T4: B ghi JSON_v1' = JSON_v0 + (dòng 1 = true) → ghi đè, dòng 0 quay về false
Kết cục: mất cập nhật dòng 0, UI hiển thị sai sau reloadStoryReplacements → KHÔNG AN TOÀN

S3-B (F2 — global sendReplacementToGlobal vs SettingsViewModel):
T1: Reader gửi sang cài đặt chung → dataStore.data.first() → list_v0
T2: (màn Settings còn sống trong back stack/process) SettingsViewModel.updateCustomReplacement chạy dataStore.edit → list_v1
T3: Reader dataStore.edit { it[CUSTOM_REPLACEMENTS] = list_v0 + item } → ghi đè list_v1
Kết cục: mất thay đổi của Settings → KHÔNG AN TOÀN

S4/S5-TOCTOU (F4 — index dịch chuyển):
T1: Danh sách [A, B, C]; bấm ✕ dòng A → remove(0) vào hàng đợi
T2: Trước recompose, tích "Hoa thường" ở dòng B hiển thị index 1 → update(1, B.from, B.to, true)
T3: remove(0) xong → [B, C]
T4: update(1, ...) ghi B' vào vị trí của C → [B, B']
Kết cục: mất C, nhân đôi B → KHÔNG AN TOÀN

S4-B (F3 — lệch bộ nhớ):
T1: Global = [("Mây tía","Tử Vân", true)], ReaderViewModel.customReplacements giống vậy
T2: User gửi per-story ("Mây tía","Tử Vân", false) → repo cập nhật tại chỗ thành false, trả true
T3: ReaderViewModel.kt:1046 none{...} = false → không đổi bộ nhớ; snackbar "Đã thêm"; reprocess với cờ true cũ
Kết cục: chương hiển thị khác dữ liệu đã lưu cho tới khi mở lại Reader → KHÔNG AN TOÀN
```

#### Commands Executed (trích từ `evidence.md`)

- `### [CMD] cmd.exe /d /c gradlew.bat compileDebugKotlin` — `Exit: 0` — `BUILD SUCCESSFUL in 4s` (`:app:compileDebugKotlin UP-TO-DATE`).
- `### [CMD] cmd.exe /d /c gradlew.bat ktlintCheck` — `Exit: 0` — `BUILD SUCCESSFUL in 857ms`.
- `### [CMD] cmd.exe /d /c gradlew.bat testDebugUnitTest` — `Exit: 0` — `BUILD SUCCESSFUL in 1s` (`:app:testDebugUnitTest UP-TO-DATE`; đây chỉ là baseline, chưa có test mới của plan).
- `### [CMD] git status --short` — `Exit: 0` — file plan ở trạng thái `??` (untracked); working tree có nhiều thay đổi chưa commit ngoài phạm vi plan.
- UNVERIFIED (chưa có trong evidence, đề xuất chạy sau khi triển khai): `.\gradlew.bat testDebugUnitTest --tests "com.personal.apptruyen.data.repository.TextReplacementRepositoryTest"` và `.\gradlew.bat assembleDebug`.

#### Các Tấn Công Đã Thử (Attempted Attack Vectors)

Các hướng đã thử nhưng không làm vỡ thiết kế (ngoài các lỗi F1–F8 ở trên):
1. **Gãy stub MockK do tham số mặc định mới trên `addStoryReplacement`**: `StoryDetailViewModelTest.kt:334` và `ReaderViewModel.kt:1026` gọi bản 3 tham số; Kotlin biên dịch qua `ITextReplacementRepository.DefaultImpls`, gọi hàm 4 tham số với `false`, nên `coVerify` vẫn khớp. Không vỡ.
2. **TC-8 phải gọi lại mạng**: `reprocessCurrentChapter` đọc `contentCache[currentNum]` (`ReaderViewModel.kt:499`), và `loadChapter` giữ entry trong cache (`:586-591`). Không vỡ.
3. **Vượt ngưỡng 600 dòng**: bỏ `ReaderSettingsPanel.kt:496-606` (~111 dòng) khỏi file 633 dòng → ~530 dòng. Không vỡ.

#### Kết luận

Còn 4 lỗi `[MAJOR]` (F1–F4); S3 và S4 FAIL. Planner cần ra Revision 2 xử lý F1–F4 (F5–F8 nên xử lý cùng lúc).

[CHANGES_REQUESTED]

### Audit Run 2

- **Reviewer**: Lead Code & Architecture Reviewer Subagent ([REVIEWER_MODEL: gemini-4-argon])
- **Ngày**: 2026-10-30
- **Đối tượng**: Technical Plan Revision 2

#### Kiểm Chứng Lỗi Cũ

| Mã lỗi | Mức độ ban đầu | Trạng thái | Bằng chứng kiểm chứng trên code thật (`[SRC]`/`[CMD]`) & Đánh giá |
| :--- | :---: | :---: | :--- |
| **F1** | `[MAJOR]` | **ĐÃ SỬA** | `[SRC]` `TextReplacementHelper.kt:48, 73`; Plan Revision 2 Goal (dòng 18–19), Assumptions (dòng 80–83), Bước 1 (dòng 294–295), và `TC-4` (dòng 352). Hàm `parseReplacementItems` nạp `if (obj.has("caseSensitive")) obj.optBoolean("caseSensitive", false) else LEGACY_JSON_DEFAULT_CASE_SENSITIVE` với `LEGACY_JSON_DEFAULT_CASE_SENSITIVE = true`, bảo toàn 100% hành vi phân biệt hoa/thường của dữ liệu JSON cũ; các mục mới tạo trên UI có Checkbox mặc định tắt (`false`) và ghi tường minh `"caseSensitive": false/true`. Khẳng định tương thích ngược và Test Contract đã chuẩn xác. |
| **F2** | `[MAJOR]` | **ĐÃ SỬA** | `[SRC]` `TextReplacementRepository.kt:18-24, 35-84`; Plan Revision 2 Assumptions (dòng 85), Bước 2 (dòng 301, 304), `TC-6` (dòng 354), `R2` (dòng 402) và Timeline Cơ chế 1. Toàn bộ thao tác per-story (`addStoryReplacement`, `updateStoryReplacement`, `removeStoryReplacement`) được tuần tự hóa nguyên tử qua `storyReplacementMutex.withLock` trong `@Singleton TextReplacementRepository`. Toàn bộ quá trình đọc `CUSTOM_REPLACEMENTS`, kiểm tra trùng và ghi lại của `sendReplacementToGlobal` được đưa trọn vẹn vào trong lambda `dataStore.edit { prefs -> ... }`, loại bỏ hoàn toàn race condition. |
| **F3** | `[MAJOR]` | **ĐÃ SỬA** | `[SRC]` `ReaderViewModel.kt:1040-1059`, `ReaderViewModelTest.kt:532`; Plan Revision 2 Design Sketch (dòng 223–233), Bước 2 (dòng 304–307), Bước 3 (dòng 317–319), và `TC-9` (dòng 357). Interface `ITextReplacementRepository` khai báo overload 2 tham số `sendReplacementToGlobal(from, to)` ủy quyền sang bản 3 tham số với `caseSensitive = false`. Trong `ReaderViewModel.sendReplacementToGlobal`, khi `!caseSensitive` gọi bản 2 tham số khớp chính xác stub MockK tại `ReaderViewModelTest.kt:532`. Khi repo trả về `true` ở nhánh cập nhật tại chỗ (`existingIdx >= 0`), ViewModel cập nhật `customReplacements[existingIdx]` trong bộ nhớ, phát Snackbar `"Đã cập nhật chế độ hoa thường cho..."` và gọi `reprocessCurrentChapter()` cập nhật nội dung chương ngay lập tức. |
| **F4** | `[MAJOR]` | **ĐÃ SỬA** | `[SRC]` `SettingsViewModel.kt:232-238`, `TextReplacementRepository.kt:61-62`; Plan Revision 2 Assumptions (dòng 87), Design Sketch (dòng 210–222, 238–268), Bước 2 (dòng 303), Bước 4 (dòng 327–329), `TC-7` (dòng 355), `R1` (dòng 401), Negative Probe 2 (dòng 384–385) và Timeline Cơ chế 2. Các hàm `update` và `remove` trên repository, ViewModel và event interface đều nhận tham số `expected: TextReplacementItem? = null` và kiểm tra `index in list.indices && (expected == null || list[index] == expected)` bên trong vùng khóa nguyên tử (`Mutex`/`dataStore.edit`), triệt tiêu rủi ro ghi đè hoặc xóa nhầm khi chỉ số bị dịch chuyển. |
| **F5** | `[MINOR]` | **ĐÃ SỬA** | `[SRC]` `StoryDetailViewModel.kt:57, 95-99, 120, 140`; Plan Revision 2 lines 62–63, 89, 102, 375. Đã bổ sung `StoryDetailViewModel` vào danh sách đọc, bảng blast-radius và danh sách lớp kiểm thử trong lệnh Gradle. |
| **F6** | `[MINOR]` | **ĐÃ SỬA** | `[SRC]` Plan Revision 2 line 326. `key` của `LazyColumn` đã được chỉ định bằng khóa ghép kèm `originalIndex`: `key = { (originalIndex, item) -> "$originalIndex:${item.from}:${item.to}:${item.caseSensitive}" }`, ngăn ngừa hoàn toàn va chạm key khi có các mục trùng `from`. |
| **F7** | `[MINOR]` | **ĐÃ SỬA** | `[SRC]` Plan Revision 2 lines 131, 193–198, 297. Đã đánh dấu `@Deprecated` kèm `ReplaceWith` trên `serializeCustomReplacements(List<Pair<String, String>>)`. |
| **F8** | `[MINOR]` | **ĐÃ SỬA** | `[SRC]` Plan Revision 2 lines 25, 329. Đã đồng bộ chính tả thành `"Xóa"` khớp với toàn bộ UI hiện hữu. |

#### Phát Hiện Mới (Fresh Findings & Regression Check)

- **F9 — `[MINOR]` Mâu thuẫn đánh số giữa Checklist Bước 6 và bảng Test Contract**:
  - Tại Checklist Bước 6 (dòng 339), plan ghi: `"theo đúng Test Contract (TC-1 → TC-10)"`. Tuy nhiên, bảng Test Contract (dòng 345–358) thực tế có 9 kịch bản kiểm thử từ `TC-1` đến `TC-9`.
  - Phân tích: Cả 9 test case nghiệp vụ đã bao phủ trọn vẹn toàn bộ các đường thực thi (từ `caseSensitive` bật/tắt, kết hợp global + story, tương thích dữ liệu JSON cũ, bỏ qua input rỗng, thao tác đồng thời Mutex, chống TOCTOU `expected`, đến tự động xử lý lại nội dung chương và cập nhật bộ nhớ). Đây là lỗi gõ nhầm số tham chiếu trong checklist, không ảnh hưởng đến thiết kế kỹ thuật hay tính toàn vẹn của mã nguồn.
  - Phân loại theo Severity Rubric: `[MINOR]` (Style & tài liệu, không chặn plan; phê duyệt kèm `[Advisory Recommendations]`).
  - Khuyến nghị: Khi tiến hành cài đặt code tại Bước 6, điều chỉnh chuỗi tham chiếu thành `TC-1 → TC-9`.

#### Mental Simulation Verification Matrix (S1–S6 + Domain)

| Kịch bản | Kết quả | Evidence Type | Chi tiết kiểm chứng |
| :--- | :---: | :---: | :--- |
| **S1: Cold-start & Build** | PASS | `[CMD]` `[SRC]` | `.\gradlew.bat compileDebugKotlin` (Exit 0, UP-TO-DATE trong 4s); `.\gradlew.bat ktlintCheck` (Exit 0, 7 tasks executed/up-to-date trong 8s); `[SRC]` `app/build.gradle.kts:87`; không bổ sung dependency bên ngoài; `String.replace(..., ignoreCase)` thuộc Kotlin standard library `kotlin.text`, tương thích hoàn toàn với `minSdk 26`; không cần bổ sung Hilt module mới vì `TextReplacementRepository` đã được đánh dấu `@Singleton` và `@Inject constructor`. |
| **S2: Lifecycle & Teardown** | PASS | `[SRC]` | `[SRC]` `ReaderViewModel.kt:497-536`, `ReaderScreen.kt:400-438`; Quá trình xử lý lại chương (`reprocessCurrentChapter`) hủy job cũ qua `reprocessJob?.cancel()` và chạy trong `viewModelScope`; Shared UI Dialog (`TextReplacementDictionaryDialog`) gắn với vòng đời Compose và được giải phóng sạch sẽ khi người dùng đóng hộp thoại hoặc thoát màn hình. |
| **S3: Database & Temporal** | PASS | `[SRC]` `[ADV]` | `[SRC]` `TextReplacementRepository.kt:18-24`, `SettingsDataStore.kt:68`, `StoryDao.kt:82-99`; `[ADV]` Timeline Cơ chế 1; `AppDatabase` giữ nguyên `version = 9`, cấu trúc bảng `stories` không đổi (trường `storyCustomReplacements TEXT` lưu trữ chuỗi JSON tương thích xuôi ngược); `storyReplacementMutex.withLock` trong `@Singleton TextReplacementRepository` và `dataStore.edit` tuần tự hóa các truy cập ghi, bảo đảm tính toàn vẹn dữ liệu. |
| **S4: Cross-Task State Flow** | PASS | `[SRC]` `[ADV]` | `[SRC]` `ReaderViewModel.kt:1040-1059`, `ReaderViewModelTest.kt:532`, `StoryDetailViewModel.kt:120-140`; `[ADV]` Timeline Cơ chế 2 & Cơ chế 4; Chữ ký mở rộng trên `ITextReplacementRepository` tương thích ngược 100% nhờ tham số mặc định và overload 2 tham số; các unit test hiện hữu của `StoryDetailViewModelTest` và `ReaderViewModelTest` không bị gãy; `ReaderViewModel` đồng bộ `customReplacements` trong bộ nhớ và phát tín hiệu xử lý lại văn bản chương đang hiển thị tức thời. |
| **S5: Security & Privacy** | PASS | `[SRC]` | `[SRC]` `SettingsDataStore.kt:68`, `ITextReplacementRepository.kt`; Dữ liệu từ điển chỉ lưu trữ trên thiết bị người dùng; không lưu trữ hoặc rò rỉ cookie/token/phiên đăng nhập; không sử dụng Regex do người dùng nhập thô, triệt tiêu nguy cơ lỗi cú pháp regex hoặc tấn công ReDoS. |
| **S6: External Resilience** | N/A | — | Tính năng xử lý thay chữ hoạt động hoàn toàn offline trên thiết bị; không gọi mạng, không phụ thuộc scraper bên thứ ba; `reprocessCurrentChapter` tái sử dụng dữ liệu từ `contentCache`. |
| **Domain: Làm sạch nội dung** | PASS | `[SRC]` `[ADV]` | `[SRC]` `TextReplacementHelper.kt:40-79`; `[ADV]` Timeline Cơ chế 3; Thứ tự xử lý chuẩn mực: khử ký tự chấm đặc biệt (`DOTS_TO_REMOVE`) -> thay chữ toàn cục (Global) -> thay chữ riêng của truyện (Per-story). Khi `caseSensitive = false`, `String.replace(..., ignoreCase = true)` xử lý chuẩn xác các ký tự tiếng Việt có dấu (`Â/â`, `Í/í`...). Dữ liệu JSON cũ bảo toàn chữ hoa đầu câu (`LEGACY_JSON_DEFAULT_CASE_SENSITIVE = true`). |
| **Domain: UI tiếng Việt 100%** | PASS | `[SRC]` | `[SRC]` Plan Revision 2 lines 20–31, 320–333; 100% các thành phần giao diện (tiêu đề `"Từ điển"`, thanh tìm kiếm bo tròn với placeholder `"Nhập từ khóa"`, các cột tiêu đề bảng `"Tìm"`, `"Thay thế"`, `"Hoa thường"`, `"Xóa"`, hộp thoại `"Thông tin"`, câu preview động, và các thông báo Snackbar) đều sử dụng tiếng Việt tự nhiên, không lộ thuật ngữ tiếng Anh hay raw exception. |
| **Domain: Hiệu năng Compose** | PASS | `[SRC]` | `[SRC]` `SettingsScreen.kt:278-389`, `ReaderSettingsPanel.kt:496-606`; Loại bỏ hoàn toàn khối danh sách phẳng dài trong `Column(verticalScroll)` gây giật lag và kéo dài màn hình, thay thế bằng `TextReplacementSummarySection` gọn gàng mở Dialog `LazyColumn` cuộn độc lập; sử dụng khóa ổn định chống va chạm `key = { (originalIndex, item) -> "$originalIndex:${item.from}:${item.to}:${item.caseSensitive}" }`; rút gọn `ReaderSettingsPanel.kt` từ 634 dòng xuống ~538 dòng (< 600 dòng). |

#### Claim Verification

- **Room & DataStore Storage**: `AppDatabase` giữ nguyên `version = 9`, `StoryDao.kt:82-99` thao tác trên trường `storyCustomReplacements TEXT` (`[SRC]` `StoryDao.kt:82-99`). `SettingsKeys.CUSTOM_REPLACEMENTS` lưu dạng `stringPreferencesKey` (`[SRC]` `SettingsDataStore.kt:68`).
- **Kotlin Standard Library Replacement**: `kotlin.text.replace(oldValue: String, newValue: String, ignoreCase: Boolean = false): String` là API tích hợp sẵn trong Kotlin stdlib, hỗ trợ đầy đủ `ignoreCase` trên mọi nền tảng Android SDK (`[URL]` `https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.text/replace.html`).
- **Jetpack Compose BOM Compatibility**: BOM `2026.05.01` (`material3`, `foundation`) cung cấp đầy đủ các composable `Dialog`, `Surface`, `LazyColumn`, `Checkbox`, `OutlinedTextField` (`[SRC]` `app/build.gradle.kts:87`).

#### Adversarial Timelines

```text
Cơ chế 1 (Đồng thời - Trục 1: Room Mutex & DataStore Atomic Edit):
T1: Người dùng chạm Checkbox dòng 0 -> onUpdate(0, expected = A, ..., true) -> coroutine 1 chạy trong storyReplacementMutex.withLock (Per-story) hoặc dataStore.edit (Global).
T2: Cùng lúc, người dùng chạm Checkbox dòng 1 -> onUpdate(1, expected = B, ..., true) trước khi UI nhận StateFlow mới.
T3: Coroutine 2 xếp hàng chờ Mutex của TextReplacementRepository / Mutex nội bộ của DataStore; coroutine 1 ghi xong JSON_v1; coroutine 2 bắt đầu thực thi trên JSON_v1 mới nhất, kiểm tra list[1] == B và ghi tiếp JSON_v2.
Kết cục: Cả hai mục đều cập nhật chính xác cờ caseSensitive, không bị mất cập nhật -> AN TOÀN.

Cơ chế 2 (TOCTOU - Trục 2: Xóa mục xen kẽ Cập nhật Checkbox):
T1: Danh sách gồm [A, B, C]. Người dùng bấm nút Xóa (✕) ở dòng A (index = 0, expected = A) -> đưa remove(0, expected = A) vào hàng đợi.
T2: Trước khi Compose kịp recompose giao diện, người dùng chạm Checkbox dòng B đang hiển thị ở index 1 -> đưa update(1, expected = B, ..., true) vào hàng đợi.
T3: Thao tác remove(0, expected = A) chạy trước trong vùng khóa nguyên tử: list[0] == A khớp -> xóa A, danh sách còn [B, C].
T4: Thao tác update(1, expected = B, ...) chạy tiếp theo: tại index 1 lúc này là mục C; kiểm tra list.getOrNull(1) == C != B không khớp với expected -> lệnh bị từ chối an toàn, không ghi đè mất mục C.
Kết cục: Dữ liệu được bảo toàn nguyên vẹn, không xảy ra hiện tượng ghi đè nhầm bản ghi lân cận -> AN TOÀN.

Cơ chế 3 (Bảo toàn dữ liệu cũ & Khôi phục sao lưu - Trục 3):
T1: Người dùng khôi phục tệp ZIP sao lưu cũ hoặc đọc dữ liệu từ Room DB v9 phiên bản trước: chuỗi JSON là [{"from":"nàn","to":"nàng"}], hoàn toàn không có khóa "caseSensitive".
T2: ReaderViewModel hoặc SettingsViewModel nạp dữ liệu và gọi TextReplacementHelper.parseReplacementItems(json).
T3: Điều kiện !obj.has("caseSensitive") kích hoạt, áp dụng hằng số LEGACY_JSON_DEFAULT_CASE_SENSITIVE = true -> nạp thành TextReplacementItem("nàn", "nàng", caseSensitive = true).
T4: Khi hiển thị câu "Nàn nhìn nàn", quy tắc thay thế phân biệt hoa/thường giữ nguyên chữ hoa đầu câu -> hiển thị đúng "Nàn nhìn nàng".
Kết cục: Bảo tồn 100% ngữ nghĩa và hành vi thay chữ của dữ liệu người dùng cũ mà không làm hỏng trải nghiệm -> AN TOÀN.

Cơ chế 4 (Đồng bộ bộ nhớ & Vòng đời đọc truyện - Trục 4):
T1: Người dùng đang đọc chương 5 ở đoạn thứ 35 (currentScrollPosition = 35). Cài đặt chung đang có ("Mây tía", "Tử Vân", caseSensitive = true).
T2: Người dùng mở bảng cài đặt đọc và gửi mục per-story ("Mây tía", "Tử Vân", caseSensitive = false) sang cài đặt chung.
T3: TextReplacementRepository cập nhật mục trong DataStore thành caseSensitive = false và trả về true; ReaderViewModel cập nhật phần tử tại existingIdx trong customReplacements của bộ nhớ RAM thành false, phát Snackbar tiếng Việt thông báo cập nhật thành công và kích hoạt reprocessCurrentChapter().
T4: Chương truyện được định dạng và thay chữ lại tức thì từ contentCache với initialScrollIndex = 35 mà không bị nhảy vị trí cuộn hay tải lại mạng.
Kết cục: Bộ nhớ RAM, DataStore và giao diện đọc đồng bộ tức thời -> AN TOÀN.
```

#### Commands Executed

1. `git status` — Exit code: 0 — Kiểm tra trạng thái working tree của dự án.
2. `.\gradlew.bat compileDebugKotlin` — Exit code: 0 — Xác nhận biên dịch Kotlin sạch sẽ (`BUILD SUCCESSFUL in 4s`).
3. `.\gradlew.bat ktlintCheck` — Exit code: 0 — Xác nhận không có vi phạm định dạng mã nguồn (`BUILD SUCCESSFUL in 8s`).
4. `node .agents/scripts/plan-approval.js status` — Exit code: 0 — Kiểm tra máy trạng thái phê duyệt của các kế hoạch triển khai.

#### Các Tấn Công Đã Thử (Attempted Attack Vectors)

1. **Kiểm tra nguy cơ `IndexOutOfBoundsException` khi danh sách rỗng hoặc chỉ số ngoài biên**:
   - Thử nghiệm: Xét kịch bản UI gửi `updateCustomReplacement(-1, ...)` hoặc `removeCustomReplacement(99, ...)`.
   - Kết quả: Trong code đề xuất của `TextReplacementRepository` và `SettingsViewModel`, điều kiện guard được viết dưới dạng: `if (index in list.indices && (expected == null || list[index] == expected))`. Nhờ cơ chế short-circuit evaluation của toán tử `&&` trong Kotlin, khi `index !in list.indices` thì vế sau hoàn toàn không được đánh giá. Không có khả năng ném ngoại lệ ngoài biên (`[SRC]` `SettingsViewModel.kt:235`, `TextReplacementRepository.kt:61`).
2. **Kiểm tra nguy cơ Deadlock giữa `storyReplacementMutex` và `dataStore.edit`**:
   - Thử nghiệm: Xét trường hợp coroutine A đang giữ `storyReplacementMutex` và cố gắng chiếm khóa của DataStore, trong khi coroutine B đang trong `dataStore.edit` và cố gắng chiếm `storyReplacementMutex`.
   - Kết quả: `storyReplacementMutex` trong `TextReplacementRepository` chỉ bọc các thao tác trên `storyDao` (Room). Thao tác `sendReplacementToGlobal` chỉ gọi `dataStore.edit` độc lập và hoàn toàn không chiếm giữ hay yêu cầu `storyReplacementMutex`. Hai cơ chế khóa độc lập hoàn toàn về mặt tài nguyên, không có hiện tượng lồng khóa hay đảo thứ tự khóa (lock inversion), triệt tiêu nguy cơ Deadlock (`[SRC]` `TextReplacementRepository.kt:35-84`).
3. **Kiểm tra tính tương thích của MockK stub trên interface `ITextReplacementRepository`**:
   - Thử nghiệm: Kiểm tra xem việc thêm tham số mặc định `caseSensitive: Boolean = false` vào `addStoryReplacement` có làm gãy lệnh xác minh `coVerify { textRepo.addStoryReplacement("s1", "lão", "ông") }` trong `StoryDetailViewModelTest.kt:334` hay không.
   - Kết quả: Trong Kotlin bytecode, interface method có default argument sẽ sinh ra hàm static trong `ITextReplacementRepository$DefaultImpls`. Cả caller trong `StoryDetailViewModel.kt:120` và verification call trong `StoryDetailViewModelTest.kt:334` đều gọi bản 3 tham số và được compiler chuyển tiếp qua `DefaultImpls` với giá trị mặc định `caseSensitive = false`. MockK proxy relaxed ghi nhận và đối chiếu chính xác lời gọi này (`[SRC]` `StoryDetailViewModelTest.kt:301, 334`).

#### Kết luận

**Kết luận**: APPROVED · BLOCKER 0 · MAJOR 0

[PLAN_APPROVED]

