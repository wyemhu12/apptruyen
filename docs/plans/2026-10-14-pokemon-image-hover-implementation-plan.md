# Kế hoạch triển khai: Tra cứu hình ảnh & hệ Pokémon khi đọc truyện (Pokemon Image Hover / Lookup)

> **Trạng thái**: Completed
> **Revision**: 1
> **Ngày tạo**: 2026-10-14
> **Planner**: @planner (gemini-3.8-flash)

## Technical Plan

### 1. Mục Tiêu

Tích hợp tính năng **"Tra cứu hình ảnh & hệ Pokémon khi đọc truyện" (Pokemon Image Hover / Lookup)** vào màn hình đọc truyện (`ReaderScreen`), dựa trên dữ liệu và cơ chế của script [Pokemon Image Hover v1.0](https://greasyfork.org/zh-CN/scripts/569903-pokemon-image-hover), kết hợp mở rộng toàn diện tên Hán-Việt / tên convert phổ biến trong truyện đồng nhân Pokémon tiếng Việt:

1. **Tự động nhận diện và gạch chân tên Pokémon trong chương truyện (`ReaderScreen`)**:
   - Nhận diện tên Pokémon đa ngôn ngữ (Việt/Romaji trên cổng Pokémon VN, Hán-Việt chuẩn & biến thể convert đồng nhân, Tiếng Anh, Tiếng Trung, Tiếng Nhật) xuất hiện trong nội dung chương truyện.
   - Hiển thị tên Pokémon với màu nhấn nổi bật theo chủ đề đọc (Sáng / Tối / Sepia) kèm đường gạch chân chấm/nét đứt (`PathEffect.dashPathEffect`) dưới chân chữ thông qua custom drawing trong `ReaderPokemonSupport.kt` kết hợp `AnnotatedString` (tag `"POKEMON"`), **tuyệt đối không làm thay đổi chuỗi gốc `state.content` gửi sang `TtsManager`**.
   - Quá trình quét từ điển chạy bất đồng bộ trên `Dispatchers.Default` (ngoài main thread) cho từng đoạn văn bản (`paragraphs`), sử dụng cấu trúc **Trie khớp từ dài nhất trước (Longest-Match-First Trie)** không phân biệt hoa/thường (`lowercase(Locale.ROOT)`) kèm kiểm tra ranh giới từ thông minh (`isWordBoundary`) để không bao giờ bắt nhầm chuỗi con bên trong từ tiếng Việt/tiếng Anh thông thường (ví dụ: không bắt nhầm `"Mew"` bên trong `"Mewtwo"`, hoặc từ con nằm giữa từ ghép chữ cái).
2. **Tương tác chạm thông minh trên đoạn văn (`ReaderParagraphItem`)**:
   - **Chạm vào tên Pokémon được gạch chân (`onTap` trúng phạm vi annotation `"POKEMON"` và ngoài khoảng trắng rìa dòng)**: Mở hộp thoại `PokemonLookupDialog` hiển thị ảnh Pokémon sắc nét tải từ cổng chính thức `https://vn.portal-pokemon.com/play/resources/pokedex/img/pm/...` (qua Coil 3 `SubcomposeAsyncImage` tận dụng `ImageLoader` 50MB disk cache sẵn có tại `di/CoilModule.kt`), huy hiệu Hệ bằng tiếng Việt chuẩn (`Hệ Cỏ`, `Hệ Độc`, `Hệ Lửa`, `Hệ Bay`, `Hệ Rồng`, `Hệ Nước`, `Hệ Điện`, `Hệ Siêu Linh`, `Hệ Tiên`... với token màu sắc chuẩn trong `ui/theme/PokemonTypeColors.kt`), các chip chuyển đổi hình thái (`Mặc định`, `Mega`, `Mega X`, `Mega Y`, `Gigamax`, `Alola`, `Galar`, `Hisui`, `Paldea`...), và thanh chuyển đổi ngôn ngữ hiển thị tên (`Hán-Việt`, `Việt / Romaji`, `Trung`, `Anh`). Khi chạm mở Pokémon ở chế độ đang tự cuộn (`autoScrollEnabled`), tự động tạm dừng tự cuộn để người dùng xem thông tin.
   - **Chạm vào vùng văn bản bình thường (ngoài tên Pokémon)**: Vẫn gọi `onToggleBars()` để ẩn/hiện thanh công cụ trên/dưới như hiện tại.
   - **Nhấn giữ (`onLongPress`) bất kỳ đâu trên đoạn văn**: Vẫn phát phản hồi xúc giác (`HapticFeedbackType.LongPress`) và kích hoạt đọc TTS từ đoạn đó (`onSeekAndPlay(index)`).
   - **Chế độ Copy (`copyMode == true`)**: Giữ nguyên `SelectionContainer` + `Text` thuần để người dùng bôi đen và sao chép văn bản không bị cản trở bởi bộ bắt cử chỉ tra cứu.
3. **Chuẩn hóa cơ sở dữ liệu Pokédex đa ngôn ngữ & Bổ sung tối đa tên Hán-Việt (`app/src/main/assets/pokemon_pokedex.json`)**:
   - Chuyển đổi toàn bộ 1.025 loài Pokémon và 1.309 hình thái từ script gốc (`pokemonMap` nặng 1.47 MB do lặp lại mảng `forms` ở từng từ khóa) sang cấu trúc chuẩn hóa gồm danh sách `entries` (chứa `id`, `nameCn`, `nameVn`, `nameHv`, `nameEn`, `nameJp`, `aliases`, `forms`). Dung lượng file JSON trong `assets/` giảm từ **1.47 MB xuống ~240 KB** (~83%).
   - **Bổ sung đầy đủ tên Hán-Việt cho 100% 1.025 loài và 1.309 hình thái**: Kết hợp từ điển phiên âm Hán-Việt chuẩn xác cho toàn bộ kho chữ Hán trong tên Pokémon + bộ từ điển các tên dịch/convert kinh điển trong truyện đồng nhân Pokémon tiếng Việt (như *Bì Khả Khâu / Tỷ Ca Khâu*, *Tiểu Hỏa Long*, *Hỏa Khủng Long*, *Phun Hỏa Long*, *Diệu Oa Chủng / Diệu Oa Chủng Tử*, *Diệu Oa Thảo*, *Diệu Oa Hoa*, *Kiệt Ni Quy*, *Ca Mễ Quy*, *Thủy Tiễn Quy*, *Siêu Mộng*, *Mộng Huyễn / Mộng Ảo*, *Liệt Không Tọa*, *Cố Lạp Đốn*, *Cái Âu Ca*, *Khoái Long*, *Bạo Lý Long*, *Cảnh Điển / Canh Quỷ*, *Hồ Địa*, *Đại Nham Xà*, *Cự Kim Quái*, *Lộ Ca Lợi Áo / Lộ Ca Lý Áo*, *Sa Nại Đóa*, *Hắc Ám Nha*, *Mộc Thủ Cung*, *Hỏa Trĩ Kê*, *Thủy Nhược Ngư*, *Lạp Đế Á Tư*, *Lạp Đế Âu Tư*, *Đạt Khắc Giả Y*, *아 Nhĩ Trụ Tư / Á Nhĩ Trụ Tư*, *Giáp Hạ Nhẫn Oa*, *Tiên Tử Y Bố*...) và tự động sinh các biến thể hình thái Hán-Việt (*"Siêu Cấp ..."*, *"Mega ..."*, *"Siêu Cực Cự Hóa ..."*, *"... A La Lạp"*, *"... Già Lặc Nhĩ"*, *"... Tẩy Thúy"*, *"... Phạt Đế Á"*, *"Nguyên Thủy ..."*).
   - Lọc bỏ các từ đơn tiếng Việt có nguy cơ dương tính giả với từ thông dụng trong văn cảnh truyện (như tên tiếng Anh/Romaji trùng từ tiếng Việt hoặc quá ngắn < 2 ký tự Latin) qua tập `EXCLUDED_COMMON_ALIASES` trong `PokemonLookupHelper`.
4. **Cài đặt bật/tắt đồng bộ DataStore (Mặc định: BẬT `true`)**:
   - Thêm khóa `SettingsKeys.POKEMON_LOOKUP_ENABLED = booleanPreferencesKey("pokemon_lookup_enabled")` trong `SettingsDataStore.kt` với giá trị mặc định là **`true` (BẬT)** trong cả `ReaderViewModel.ReaderState`, `SettingsViewModel.SettingsState`, `ReaderSettingsPanel.kt` và `SettingsScreen.kt`.
   - Cho phép người dùng bật/tắt trực tiếp ngay trong bảng `ReaderSettingsPanel` (mục CÔNG CỤ: *"Từ điển Pokémon — Gạch chân tên Pokémon trong truyện, chạm để xem ảnh và hệ"*) cũng như trong màn hình `SettingsScreen` (mục Đọc truyện).

---

### 2. Pre-Work Reading

Đã đọc và khảo sát đầy đủ các tài liệu quy tắc, kiến trúc và mã nguồn theo Pre-Work Protocol (`.agents/rules/02-quality.md`):

- **Tài liệu quy tắc & kiến trúc**:
  - `AGENTS.md`, `.agents/rules/00-core.md`, `.agents/rules/01-coding.md`, `.agents/rules/02-quality.md`, `.agents/rules/03-ui.md`, `.agents/rules/05-plan-review.md`, `.agents/rules/06-docs.md`.
  - `.agents/agents/reviewer/evidence-bar.md`, `.agents/agents/reviewer/mental-simulation.md`.
  - `docs/architecture.md`, `docs/progress.md`, `docs/lessons-learned.md`, `docs/contributing.md`, `docs/plans/README.md`.
  - Kế hoạch gần nhất: `docs/plans/2026-10-14-parallel-chapter-download-implementation-plan.md`.
- **Mã nguồn & dữ liệu đã đọc**:
  - Mã nguồn script gốc: `C:\Users\wyemh\.gemini\antigravity\brain\ad35e288-1266-4c30-b880-6a084809d556\.system_generated\steps\3\content.md` (1.025 Pokémon, 1.309 forms, bảng `typeColors` và `typeTranslation` 18 hệ).
  - `app/build.gradle.kts` (dòng 1–202: Compose BOM `2026.05.01`, Coil `3.4.0`, `kotlinx-serialization-json:1.11.0`, `kotlinx-collections-immutable:0.3.8`).
  - `app/proguard-rules.pro` (dòng 59–77: quy tắc keep cho `com.personal.apptruyen.data.model.**` và `kotlinx.serialization`).
  - `app/src/main/java/com/personal/apptruyen/ui/reader/ReaderScreen.kt` (dòng 1–715: `ReaderScreen`, `ReaderContentArea`, `LazyColumn` render `paragraphs`, cử chỉ chạm/nhấn giữ).
  - `app/src/main/java/com/personal/apptruyen/ui/reader/ReaderViewModel.kt` (dòng 1–839: `ReaderIntent`, `ReaderState`, `loadDefaultSettings`, `loadChapter`).
  - `app/src/main/java/com/personal/apptruyen/ui/reader/ReaderSettingsPanel.kt` (dòng 1–625: các công tắc trong mục CÔNG CỤ).
  - `app/src/main/java/com/personal/apptruyen/data/local/SettingsDataStore.kt` (dòng 1–108: `SettingsKeys`).
  - `app/src/main/java/com/personal/apptruyen/ui/settings/SettingsViewModel.kt` (dòng 1–307: `SettingsState` + DataStore persistence).
  - `app/src/main/java/com/personal/apptruyen/ui/settings/SettingsScreen.kt` (dòng 1–250: mục Đọc truyện trong Cài Đặt).
  - `app/src/main/java/com/personal/apptruyen/ui/theme/Color.kt` (dòng 1–82) và `ReaderThemeColors.kt` (dòng 1–71).
  - `app/src/main/java/com/personal/apptruyen/di/CoilModule.kt` (dòng 1–41: `ImageLoader` với 50MB disk cache) và `AppModule.kt` (dòng 1–126).
  - `app/src/main/java/com/personal/apptruyen/ui/components/StoryCardPremium.kt` (dòng 95–125: cách dùng `SubcomposeAsyncImage` + `shimmerBrush()`).
  - `app/src/test/java/com/personal/apptruyen/ui/reader/ReaderViewModelTest.kt` (dòng 1–146) và `TextReplacementHelperTest.kt` (dòng 1–197).

---

### 3. Assumptions & Cross-Task Dependencies

1. **Không thay đổi Room Database Schema (`AppDatabase.kt` giữ nguyên `version = 9`)**:
   - Dữ liệu từ điển Pokémon là dữ liệu tĩnh bất biến đóng gói kèm APK tại `app/src/main/assets/pokemon_pokedex.json`, được nạp lazy một lần duy nhất vào bộ nhớ RAM thông qua `PokemonRepository` (`@Singleton`) khi tính năng được sử dụng.
   - Cài đặt bật/tắt lưu vào `DataStore<Preferences>` (`SettingsKeys.POKEMON_LOOKUP_ENABLED`, mặc định `true`). Do hệ thống `BackupRepository.exportBackup` (`BackupRepository.kt:68-71`) tự động thu thập toàn bộ các key trong `prefs.asMap()`, khóa mới sẽ tự động tương thích với tính năng Sao lưu / Khôi phục hiện có.
2. **Bảo toàn tuyệt đối kích thước file `ReaderScreen.kt` (tuân thủ `.agents/rules/01-coding.md` & `03-ui.md`)**:
   - Hiện tại `ReaderScreen.kt` đã dài 715 dòng (vượt ngưỡng khuyến nghị 600 dòng). Do đó, toàn bộ composable hiển thị đoạn văn có gạch chân tên Pokémon (`ReaderParagraphItem`) và hộp thoại tra cứu (`PokemonLookupDialog`) sẽ được tách sang file mới `app/src/main/java/com/personal/apptruyen/ui/reader/ReaderPokemonSupport.kt` (~320 dòng). Trong `ReaderScreen.kt` chỉ kết nối state/callback gọn nhẹ (~25 dòng thay đổi).
3. **Không ảnh hưởng tới tiến độ đọc TTS và `TtsTextPreprocessor`**:
   - `ReaderViewModel.loadChapter` vẫn truyền nguyên vẹn `content` thuần sang `ttsManager.setContent(content, title)` và `ttsManager.setContentForAutoAdvance(content, title)`.
   - Việc nhận diện tên Pokémon chỉ tạo danh sách `List<PokemonMatchRange>` để `ReaderParagraphItem` dựng `AnnotatedString` và vẽ đường gạch chân nét đứt ở tầng UI Compose.
4. **Quy tắc R8 / ProGuard cho model `@Serializable` mới**:
   - Các model `@Serializable` dùng để parse `pokemon_pokedex.json` (`PokemonCatalog`, `PokemonEntry`, `PokemonForm`) được đặt trong package `com.personal.apptruyen.data.model` (`PokemonModels.kt`). Nhờ các rule sẵn có tại `app/proguard-rules.pro:61,70-76` (`-keep class com.personal.apptruyen.data.model.** { *; }` và `-keep,includedescriptorclasses class com.personal.apptruyen.data.model.**$$serializer { *; }`), bản build release có bật R8 minify sẽ tự động giữ nguyên serializer mà không cần sửa `app/proguard-rules.pro`.

---

### 4. Verified Versions Table

> Không thêm hoặc nâng cấp bất kỳ dependency nào trong `app/build.gradle.kts`. Bảng dưới đây xác minh các thư viện liên quan trực tiếp đang sử dụng trong dự án:

| Thư viện | Version hiện tại | Version đề xuất | Latest đã xác minh | Nguồn URL | Tương thích? |
| -------- | ---------------- | --------------- | ------------------ | --------- | ------------ |
| `androidx.compose:compose-bom` (`ui`, `foundation`, `material3`) | `2026.05.01` | `2026.05.01` (giữ nguyên) | `2026.05.01` | [developer.android.com/jetpack/compose/bom](https://developer.android.com/jetpack/compose/bom) | ✅ `AnnotatedString`, `TextLayoutResult`, `detectTapGestures`, `drawBehind`, `PathEffect.dashPathEffect` ổn định trên `minSdk 26` |
| `io.coil-kt.coil3:coil-compose` & `coil-network-okhttp` | `3.4.0` | `3.4.0` (giữ nguyên) | `3.4.0` | [coil-kt.github.io/coil](https://coil-kt.github.io/coil/) | ✅ `SubcomposeAsyncImage` kết hợp `CoilModule` (50MB disk cache + 25% memory cache) tải ảnh PNG từ `vn.portal-pokemon.com` |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | `1.11.0` | `1.11.0` (giữ nguyên) | `1.11.0` | [github.com/Kotlin/kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) | ✅ Parse `pokemon_pokedex.json` (~240KB) trong ~15-25ms trên `Dispatchers.IO` |
| `androidx.datastore:datastore-preferences` | `1.2.1` | `1.2.1` (giữ nguyên) | `1.2.1` | [developer.android.com/jetpack/androidx/releases/datastore](https://developer.android.com/jetpack/androidx/releases/datastore) | ✅ `booleanPreferencesKey("pokemon_lookup_enabled")` lưu trữ bền vững cài đặt bật/tắt |
| `org.jetbrains.kotlinx:kotlinx-collections-immutable` | `0.3.8` | `0.3.8` (giữ nguyên) | `0.3.8` | [github.com/Kotlin/kotlinx.collections.immutable](https://github.com/Kotlin/kotlinx.collections.immutable) | ✅ `ImmutableList` cho `forms` và `paragraphPokemonMatches` đảm bảo Compose stability |

---

### 5. Files Cần Chỉnh Sửa / Tạo Mới

| Nhãn       | File | Tầng | Ghi chú (file nhạy cảm → lý do) |
| ---------- | ---- | ---- | ------------------------------- |
| `[NEW]`    | `app/src/main/assets/pokemon_pokedex.json` | `assets` | Cơ sở dữ liệu 1.025 loài Pokémon và 1.309 hình thái đã chuẩn hóa (~240KB), tích hợp đầy đủ tên Việt (Romaji), Hán-Việt chuẩn + biến thể đồng nhân, Trung, Anh, Nhật và URL ảnh chính thức. |
| `[NEW]`    | `app/src/main/java/com/personal/apptruyen/data/model/PokemonModels.kt` | `data/model` | Khai báo `@Serializable data class PokemonCatalog`, `PokemonEntry`, `PokemonForm`, `PokemonMatchRange`, `PokemonSelection` và `enum class PokemonNameLanguage`. |
| `[NEW]`    | `app/src/main/java/com/personal/apptruyen/util/PokemonLookupHelper.kt` | `util` | Thuật toán **Longest-Match-First Trie** thuần Kotlin (`PokemonMatcher`), chuẩn hóa chuỗi, kiểm tra ranh giới từ tiếng Việt/Latin (`isWordBoundary`), lọc từ dễ dương tính giả (`EXCLUDED_COMMON_ALIASES`), và chuyển đổi nhãn Hệ/Hình thái sang tiếng Việt. |
| `[NEW]`    | `app/src/main/java/com/personal/apptruyen/data/repository/IPokemonRepository.kt` | `data/repository` | Interface `IPokemonRepository` cung cấp `suspend fun getMatcher(): PokemonMatcher?` và `suspend fun findMatchesInParagraphs(paragraphs: List<String>): Map<Int, ImmutableList<PokemonMatchRange>>`. |
| `[NEW]`    | `app/src/main/java/com/personal/apptruyen/data/repository/PokemonRepository.kt` | `data/repository` | Implementation `@Singleton` nạp lazy + thread-safe (`Mutex`) file `assets/pokemon_pokedex.json` trên `Dispatchers.IO` và chạy matching trên `Dispatchers.Default`. |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/di/RepositoryModule.kt` | `di` | Thêm `@Binds @Singleton abstract fun bindPokemonRepository(impl: PokemonRepository): IPokemonRepository`. |
| `[NEW]`    | `app/src/main/java/com/personal/apptruyen/ui/theme/PokemonTypeColors.kt` | `ui/theme` | Định nghĩa bảng màu chuẩn cho 18 Hệ Pokémon (`PokemonTypeBadgeStyle`: màu nền huy hiệu + màu chữ tương phản cao) và màu gạch chân Pokémon theo 3 chủ đề `ReaderTheme` (tuân thủ `.agents/rules/03-ui.md`: không hardcode `Color(0xFF...)` trong Screen/Dialog). |
| `[NEW]`    | `app/src/main/java/com/personal/apptruyen/ui/reader/ReaderPokemonSupport.kt` | `ui/reader` | Chứa `ReaderParagraphItem` (render đoạn văn kèm gạch chân nét đứt tên Pokémon, xử lý chạm trúng tên Pokémon vs chạm vùng trống vs nhấn giữ TTS) và `PokemonLookupDialog` (hiển thị ảnh Coil 3, huy hiệu hệ tiếng Việt, nút chuyển đổi hình thái, nút chuyển đổi ngôn ngữ tên). |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/data/local/SettingsDataStore.kt` | `data/local` | Thêm `val POKEMON_LOOKUP_ENABLED = booleanPreferencesKey("pokemon_lookup_enabled")` (mặc định: `true`). |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/ui/reader/ReaderViewModel.kt` | `ui/reader` | Inject `IPokemonRepository`; thêm `pokemonLookupEnabled: Boolean = true`, `selectedPokemon: PokemonSelection? = null`, `paragraphPokemonMatches: ImmutableMap<Int, ImmutableList<PokemonMatchRange>> = persistentMapOf()` vào `ReaderState`; thêm intents `TogglePokemonLookup`, `SelectPokemon`, `DismissPokemonDialog`. |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/ui/reader/ReaderScreen.kt` | `ui/reader` | Kết nối `ReaderParagraphItem`, `PokemonLookupDialog` và truyền `pokemonLookupEnabled` + `onTogglePokemonLookup` xuống `ReaderSettingsPanel`. |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/ui/reader/ReaderSettingsPanel.kt` | `ui/reader` | Thêm công tắc `"Từ điển Pokémon"` (mô tả: `"Gạch chân tên Pokémon trong truyện, chạm để xem ảnh và hệ"`) trong mục `CÔNG CỤ`. |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/ui/settings/SettingsViewModel.kt` | `ui/settings` | Thêm `val pokemonLookupEnabled: Boolean = true` vào `SettingsState` và hàm `fun setPokemonLookupEnabled(enabled: Boolean)`. |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/ui/settings/SettingsScreen.kt` | `ui/settings` | Thêm công tắc `"Từ điển Pokémon"` trong mục `Đọc truyện` để đồng bộ với `ReaderSettingsPanel`. |
| `[NEW]`    | `app/src/test/java/com/personal/apptruyen/util/PokemonLookupHelperTest.kt` | `test` | **[NHẠY CẢM - Test File]**: Tạo mới bộ unit test kiểm chứng thuật toán Trie longest-match-first, kiểm tra ranh giới từ tiếng Việt/Latin/CJK, nhận diện tên Hán-Việt/Việt/Anh/Trung/Nhật, chọn đúng `initialFormIndex` khi chạm vào hình thái Mega/Gigamax/vùng miền, và dịch nhãn Hệ tiếng Việt. |
| `[MODIFY]` | `app/src/test/java/com/personal/apptruyen/ui/reader/ReaderViewModelTest.kt` | `test` | **[NHẠY CẢM - Test File]**: Cập nhật constructor `createViewModel()` thêm mock `IPokemonRepository` (Loại B - Test Harness) và **THÊM MỚI** các unit test cho `pokemonLookupEnabled` (mặc định `true`), `TogglePokemonLookup`, `SelectPokemon` / `DismissPokemonDialog`, và bảo toàn 100% nội dung gốc gửi sang `ttsManager`. Giữ nguyên toàn bộ test và assert hiện có. |
| `[MODIFY]` | `docs/architecture.md` | `docs` | Cập nhật cấu trúc thư mục, luồng dữ liệu tra cứu Pokémon và timestamp. |
| `[MODIFY]` | `docs/progress.md` | `docs` | Ghi nhận hoàn thành tính năng Tra cứu hình ảnh & hệ Pokémon khi đọc truyện. |
| `[MODIFY]` | `docs/changelog.md` | `docs` | Thêm mục tính năng mới dưới `## [Unreleased]`. |
| `[MODIFY]` | `docs/plans/README.md` | `docs` | Cập nhật bảng trạng thái kế hoạch. |

---

### 6. Các Bước Thực Hiện

- [ ] **Bước 1: Xây dựng và chuẩn hóa dữ liệu `app/src/main/assets/pokemon_pokedex.json` (1.025 loài + 1.309 hình thái + Tên Hán-Việt toàn diện)**
  - Trích xuất toàn bộ dữ liệu từ `pokemonMap` trong script gốc (`Pokemon Image Hover.user.js`), khử trùng lặp các nhóm hình thái theo loài cơ sở (1.025 entries, đánh số `id` từ `1..1025`), giữ nguyên 100% các trường gốc:
    - Tên Tiếng Trung (`nameCn`, ví dụ `"喷火龙"`), tên Việt/Romaji chuẩn từ cổng Pokémon VN (`nameVn`, ví dụ `"Lizardon"`), tên Tiếng Anh (`nameEn`, ví dụ `"Charizard"`), tên Tiếng Nhật (`nameJp`, ví dụ `"リザードン"`).
    - Danh sách hình thái `forms`: mỗi phần tử gồm `formCn` (`"超级喷火龙Ｘ"`), `formVn` (`"Lizardon Mega X"`), `formHv` (`"Siêu Cấp Phun Hỏa Long X"`), `imageUrl` (`"https://vn.portal-pokemon.com/play/resources/pokedex/img/pm/..."`), và `types` (mảng mã hệ chuẩn hóa sang tiếng Việt: `["Lửa", "Rồng"]`).
  - **Bổ sung trường `nameHv` (tên Hán-Việt chuẩn) và mảng `aliases` (các biến thể tên Hán-Việt / tên dịch đồng nhân)**:
    - Áp dụng từ điển phiên âm Hán-Việt chuẩn cho 100% tên tiếng Trung của 1.025 loài và 1.309 hình thái (đảm bảo xử lý chính xác các chữ đa âm trong ngữ cảnh Pokémon như `奇` → `Kỳ`, `龟` → `Quy`, `蛙` → `Oa`, `龙` → `Long`, `梦` → `Mộng`, `超` → `Siêu`, `鬼` → `Quỷ`, `钢` → `Cương`, `铁` → `Thiết`, `岩` → `Nham`, `暴` → `Bạo`, `鲤` → `Lý`, `皮` → `Bì`, `丘` → `Khâu`...).
    - Bổ sung bộ từ điển biến thể dịch/convert phổ biến trong cộng đồng đọc truyện đồng nhân Pokémon tại Việt Nam (Wikidich, Tàng Thư Viện, SsTruyện):
      - Gen 1: *Diệu Oa Chủng / Diệu Oa Chủng Tử*, *Diệu Oa Thảo*, *Diệu Oa Hoa*, *Tiểu Hỏa Long*, *Hỏa Khủng Long*, *Phun Hỏa Long*, *Kiệt Ni Quy*, *Ca Mễ Quy*, *Thủy Tiễn Quy*, *Lục Mao Trùng*, *Thiết Giáp Dũng*, *Ba Đại Điệp*, *Độc Giác Trùng*, *Thiết Xác Dũng*, *Đại Châm Phong*, *Ba Ba*, *Tỷ Tỷ Điểu / Bỉ Bỉ Điểu*, *Đại Tỷ Điểu / Đại Bỉ Điểu*, *Tiểu Lạp Đạt*, *Lạp Đạt*, *Liệt Tước*, *Đại Chủy Tước*, *A Bách Xà*, *A Bách Quái*, *Bì Khả Khâu / Tỷ Ca Khâu*, *Lôi Khâu*, *Xuyên Sơn Thử*, *Xuyên Sơn Vương*, *Ni Đa Lan*, *Ni Đa Na*, *Ni Đa Hậu*, *Ni Đa Lãng*, *Ni Đa Lực*, *Ni Đa Vương*, *Bì Bì*, *Bì Khả Tây*, *Lục Vĩ*, *Cửu Vĩ*, *Bàn Đinh*, *Bàn Khả Đinh*, *Siêu Âm Phúc*, *Đại Chủy Phúc*, *Hành Lộ Thảo*, *Xú Xú Hoa*, *Bá Vương Hoa*, *Phái Lạp Tư*, *Phái Lạp Đặc Tư*, *Mao Cầu*, *Ma Lỗ Nga*, *Địa Thử*, *Tam Địa Thử*, *Miêu Miêu*, *Miêu Lão Đại*, *Khả Đạt Áp*, *Ca Đạt Áp*, *Hầu Quái*, *Hỏa Bạo Hầu*, *Khả Đế Cẩu / tạp Đế Cẩu*, *Phong Tốc Cẩu*, *Văn Hương Kho nòng nọc / Văn Hương Đẩu*, *Văn Hương Quân*, *Văn Hương Vịnh Sĩ*, *Khải Tây*, *Dũng Cát Lạp*, *Hồ Địa*, *Uyển Lực*, *Hào Lực*, *Quái Lực*, *Lạt Nha*, *Khẩu Độc Hoa*, *Thực Đại Hoa*, *Mã Não Thủy Mẫu*, *Độc Thích Thủy Mẫu*, *Tiểu Quyền Thạch*, *Long Long Thạch*, *Long Long Nham*, *Tiểu Hỏa Mã*, *Liệt Diễm Mã*, *Ngốc Ngốc Thú*, *Ngốc Xác Thú / Ngốc Hà Thú*, *Tiểu Từ Quái*, *Tam Hợp Nhất Từ Quái*, *Đại Thông Áp*, *Đô Đô*, *Đô Đô Lợi*, *Tiểu Hải Sư*, *Bạch Hải Sư*, *Xú Nê*, *Xú Xú Nê*, *Đại Thiệt Bối*, *Thích Giáp Bối*, *Quỷ Tư*, *Quỷ Tư Thông*, *Cảnh Điển / Canh Quỷ*, *Đại Nham Xà*, *Thôi Miên Mạch*, *Dẫn Mộng Mạch Nhân*, *Đại Kiềm Giải*, *Cự Kiềm Giải*, *Lôi Điện Cầu*, *Ngoan Bì Lôi Đạn*, *Đản Đản*, *Gia Đản Thụ*, *Khả Lạp Khả Lạp*, *Cốt Lạp Cốt Lạp*, *Phi Thối Lang*, *Khoái Quyền Lang*, *Đại Thiệt Đầu*, *Ngõa Tư Đạn*, *Song Đạn Ngõa Tư*, *Độc Giác Tê Ngưu*, *Toàn Giác Tê Thú*, *Hạnh Phúc Đản / Cát Lợi Đản*, *Đằng Man Quái*, *Đại Mẫu Thú*, *Mặc Hải Mã*, *Hải Thứ Long*, *Giác Kim Ngư*, *Kim Ngư Vương*, *Hải Tinh Tinh*, *Bảo Thạch Hải Tinh*, *Ma Tường Nhân Ngẫu*, *Phi Thiên Đường Lang*, *Mê Thôi Tỷ*, *Điện Kích Thú*, *Áp Chủy Hỏa Thú*, *Khải Giáp / Đại Giáp*, *Khẳng Thái La*, *Lý Ngư Vương*, *Bạo Lý Long*, *Lạp Phổ Lạp Tư / Thừa Long*, *Bách Biến Quái*, *Y Bố*, *Thủy Y Bố*, *Lôi Y Bố*, *Hỏa Y Bố*, *Đa Biên Thú / 3D Long*, *Cúc Thạch Thú*, *Đa Thích Cúc Thạch Thú*, *Hóa Thạch Khôi*, *Liêm Đao Khôi*, *Hóa Thạch Dực Long*, *Ca Bỉ Thú*, *Cấp Đông Điểu*, *Thiểm Điện Điểu*, *Hỏa Diễm Điểu*, *Mê Nhĩ Long*, *Ha Khắc Long*, *Khoái Long*, *Siêu Mộng*, *Mộng Huyễn / Mộng Ảo*...
      - Gen 2–9 tiêu biểu: *Cúc Thảo Diệp*, *Hỏa Cầu Thử*, *Hỏa Bạo Thú*, *Tiểu Cự Ngạc*, *Đại lực Ngạc*, *Siêu Lực Ngạc*, *Xoa Tự Phúc*, *Điện Long*, *Đẹp Thạch Hoa / Mỹ Lệ Hoa*, *Mã Lực Lộ Lệ*, *Hồ Thuyết Thụ*, *Ngưu oa Quân*, *Thái Dương Y Bố*, *Nguyệt Lượng Y Bố*, *Hắc Ám Nha*, *Ngốc Ngốc Vương*, *Quả Thần Wobbuffet / Quả Nhiên Ông*, *Đại Thép Xà / Đại Cương Xà*, *Cự Kiềm Đường Lang*, *Hách Lạp Khắc La Tư*, *ữu Lạp*, *Quyển Hùng*, *Dung Nham Oa Ngưu*, *Trường Mao Trư*, *Thích Long Vương*, *Đốn Giáp*, *3D Long 2*, *Hạnh Phúc Đản*, *Lôi Công*, *Viêm Đế*, *Thủy Quân*, *Ấu Cơ Lạp Tư*, *Sa Cơ Lạp Tư*, *Ban Cơ Lạp Tư*, *Lộ Kỳ Á*, *Phượng Vương*, *Thời Ла Bỉ / Tuyết Lạp Bỉ*, *Mộc Thủ Cung*, *Sâm Lâm Thằn Lằn*, *Tích Dịch Vương*, *Hỏa Trĩ Kê*, *Lực Tràng Kê*, *Hỏa Diễm Kê*, *Thủy Nhược Ngư*, *Chiểu Nhược Ngư*, *Cự Trảo Ngư*, *Lạp Lỗ Lạp Tư*, *Kỳ Lỗ Lợi An*, *Sa Nại Đóa*, *Thỉnh Giả Vương*, *Thiết Khiết Quỷ*, *Bạo Phi Long*, *Thiết Á Đinh*, *Kim Châm Quái*, *Cự Kim Quái*, *Lôi Cát Lạc Khắc*, *Lôi Cát Ngải Tư*, *Lôi Cát Tư Kỳ Lỗ*, *Lạp Đế Á Tư*, *Lạp Đế Âu Tư*, *Cái Âu Ca*, *Cố Lạp Đốn*, *Liệt Không Tọa*, *Cơ Lạp Kỳ*, *Đế Âu Tây Tư*, *Thảo Miêu*, *Thổ Đài Quy*, *Tiểu Hỏa Diễm Hầu*, *Liệt Diễm Hầu*, *Ba Gia Mạn*, *Đế Vương Nã Ba*, *Mỗ Khắc Điểu*, *Lặc Khắc Miêu*, *Luân Cầm Miêu*, *Hàm Khôi Long*, *Chiến Chùy Long*, *Thuẫn Giáp Long*, *Hộ Thành Long*, *Viên Lục Sa*, *Tiêm Nha Lục Sa*, *Liệt Giảo Lục Sa*, *Lợi Âu Lộ*, *Lộ Ca Lợi Áo / Lộ Ca Lý Áo*, *Sa Hà Mã*, *Long Vương Bọ Cạp*, *Độc Đấu Oa*, *Tuyết Yêu Nữ*, *Lạc Thác Mẫu*, *Đế Nha Lư Ca*, *Phạt Lộ Kỳ Á*, *tịch Đa Lan Ân*, *Kỵ Lạp Đế Nạp*, *Khắc Lôi Sắc Lợi Á*, *Đạt Khắc Giả Y*, *Mã Nạp Phi*, *Tạ Mễ*, *Á Nhĩ Trụ Tư*, *Bỉ Khắc Đề Ni*, *Đằng Đằng Xà*, *Quân Chủ Xà*, *Noãn Noãn Trư*, *Viêm Vũ Vương*, *Thủy Thủy Đát*, *Đại Kiếm Quỷ*, *Lưu Manh Ngạc*, *Đạt Ma Phí Phí*, *Tác La Á Khắc*, *Băng Băng*, *Song Bội Băng Băng*, *Đơn Đầu Long*, *Song Đầu Long*, *Tam Đầu Ác Long*, *Đốt Trùng*, *Hỏa Thần Nga*, * Lai Hi Lạp Mẫu*, *Tiệp Khắc La Mẫu*, *Khâu Lôi Mẫu*, *Khải Lộ Địch Âu*, *Mỹ Lạc Gia Tháp*, *Cái Nặc Tái Khắc Đặc*, *Châm Lật Nhi*, *Hồ Ly Lửa / Hỏa Hồ Ly*, *Bọt Oa / Cô Cô Oa*, *Giáp Hạ Nhẫn Oa*, *Tiểu Tiến Tước*, *Liệt Tiến Ưng*, *Độc Kiếm Quái*, *Kiên Thuẫn Kiếm Quái*, *Tiên Tử Y Bố*, *Ngự Thừa Long / Niêm Mỹ Long*, *Ông Phúc*, *Triết Nhĩ Ni Á Tư*, *Y Bùi Nhĩ Tháp Nhĩ*, *Cơ Cách Nhĩ Đức*, *Đế An Tây*, *Hồ Ba*, *Ba Nhĩ Khắc Tây Ông*, *Mộc Kiêu*, *Quyết Xạ Kiêu*, *Hỏa Ban Miêu*, *Chí Viêm Hổ*, *Cầu Hải Sư*, *Tây Hải Sư*, *Nham Chó Con*, *Chạng Vạng Lang Nhân*, *Thực Độc Hoa*, *Mê Nghĩ Khâu*, *Trượng Vĩ Lân Giáp Long*, *Khả Phốc Minh Minh*, *Khả Phốc Điệp Điệp*, *Khả Phốc Mao Mao*, *Khả Phốc Kỳ Kỳ*, *Tác Nhĩ Ca Lôi Âu*, *Lộ Nại Á Lạp*, *Hư Ngô Y Đức*, *Bạo Cơ Văn*, *Phí Lạc Mỹ Lang*, *Điện Thúc Mộc*, *Thiết Hỏa Huy Dạ*, *Chỉ Ngự Kiếm*, *Ác Thực Vương*, *Nại Khắc Lạc Tư Mã*, *Mã cơ Nhã Na*, *Mã Hạ Đa*, *Tiệp Lạp Áo Lạp*, *Mỹ Lục Đản*, *Mỹ Lục Mai Tháp*, *Gõ Âm Hầu*, *Oanh Lôi Kim Cương Tinh*, *Viêm Thỏ Nhi*, *Thiểm Diễm Vương Bài*, *Lệ Nhãn Tích*, *Thiên Diện Tích Dịch*, *Cương Nha Nha*, *Tích Giáp Nha*, *Ba Cách Nha*, *Đa Long Mai Á*, *Đa Long Ba Lỗ Đặc*, *Thương Hưởng*, *Tàng Mã Nhiên Đặc*, *Vô Cực Thái Na*, *Hùng Đồ Đệ*, *Vũ Đạo Hùng Sư*, *Lôi Cát Ngải Lặc Kỳ*, *Lôi Cát Đạc Lạp Khoa*, *Lôi Quan Vương*, *Tân Diệp Miêu*, *Ma Huyễn Giả Diện Miêu*, *Ngốc Hỏa Ngạc*, *Cốt Thế Cự Thanh Ngạc*, *Nhuận Thủy Áp*, *Cuồng Hoan Lãng Vũ Áp*, *Tác Tài Linh*, *Tài Phú Hào*, *Cố Lặc Đốn*, *Mật Lặc Đốn*, *Ách Quỷ Bính*, *Thái Nhạc Ba Qua Tư*, *Đào Đãi Lang*...
      - Cùng các biến thể tên hình thái tiếng Việt/Hán-Việt: `"Siêu Cấp <Tên>"`, `"<Tên> Mega"`, `"Siêu Cực Cự Hóa <Tên>"`, `"<Tên> Gigamax"`, `"<Tên> A La Lạp"`, `"<Tên> Alola"`, `"<Tên> Già Lặc Nhĩ"`, `"<Tên> Galar"`, `"<Tên> Tẩy Thúy"`, `"<Tên> Hisui"`, `"<Tên> Phạt Đế Á"`, `"<Tên> Paldea"`, `"Nguyên Thủy <Tên>"`.

- [ ] **Bước 2: Tạo Domain Models trong `app/src/main/java/com/personal/apptruyen/data/model/PokemonModels.kt`**
  - Khai báo các data class `@Immutable` và `@Serializable`:
    ```kotlin
    package com.personal.apptruyen.data.model

    import androidx.compose.runtime.Immutable
    import kotlinx.collections.immutable.ImmutableList
    import kotlinx.collections.immutable.persistentListOf
    import kotlinx.serialization.Serializable

    @Serializable
    data class PokemonCatalog(
        val version: Int = 1,
        val entries: List<PokemonEntry> = emptyList(),
    )

    @Immutable
    @Serializable
    data class PokemonEntry(
        val id: Int,
        val nameCn: String,
        val nameVn: String,
        val nameHv: String,
        val nameEn: String = "",
        val nameJp: String = "",
        val aliases: List<String> = emptyList(),
        val forms: List<PokemonForm> = emptyList(),
    )

    @Immutable
    @Serializable
    data class PokemonForm(
        val formCn: String,
        val formVn: String,
        val formHv: String,
        val imageUrl: String,
        val types: List<String> = emptyList(),
    )

    @Immutable
    data class PokemonMatchRange(
        val start: Int,
        val end: Int, // exclusive
        val matchedText: String,
        val entry: PokemonEntry,
        val initialFormIndex: Int = 0,
    )

    @Immutable
    data class PokemonSelection(
        val entry: PokemonEntry,
        val matchedText: String,
        val initialFormIndex: Int = 0,
    )

    enum class PokemonNameLanguage(val label: String) {
        HAN_VIET("Hán-Việt"),
        VIET_ROMAJI("Việt / Romaji"),
        CHINESE("Trung"),
        ENGLISH("Anh"),
    }
    ```

- [ ] **Bước 3: Xây dựng bộ máy khớp tên `PokemonLookupHelper.kt` (`util/`)**
  - Tại `app/src/main/java/com/personal/apptruyen/util/PokemonLookupHelper.kt`:
    - Xây dựng class `PokemonMatcher` sử dụng cấu trúc **Trie theo ký tự đã chuẩn hóa `lowercase(Locale.ROOT)`** (hỗ trợ khớp chuỗi dài nhất trước — *Longest-Match-First*, tương đương hành vi `keywords.sort((a, b) => b.length - a.length)` trong script gốc nhưng chạy trong thời gian tuyến tính $O(N \cdot L_{max})$ theo độ dài văn bản thay vì quét hàng nghìn nhánh Regex):
      - Khi nạp mỗi `PokemonEntry`:
        - Đăng ký các từ khóa của loài cơ sở (`nameCn`, `nameVn`, `nameHv`, `nameEn`, `nameJp`, và từng phần tử trong `aliases`) với `initialFormIndex = 0`.
        - Đăng ký các từ khóa của từng hình thái `forms[idx]` (`formCn`, `formVn`, `formHv`, cùng các biến thể `"Mega ${entry.nameVn}"`, `"Mega ${entry.nameEn}"`, `"Siêu Cấp ${entry.nameHv}"`, `"${entry.nameHv} Mega"`) với `initialFormIndex = idx`.
      - **Bộ lọc chống dương tính giả (`EXCLUDED_COMMON_ALIASES` & quy tắc độ dài tối thiểu)**:
        - Bỏ qua các từ khóa rỗng; nếu từ khóa chỉ gồm ký tự Latin/tiếng Việt (không phải chữ Hán/Kana) thì yêu cầu độ dài tối thiểu `>= 3` ký tự và không nằm trong tập từ đơn tiếng Việt thông dụng (`EXCLUDED_COMMON_ALIASES`, ví dụ các từ đơn dễ trùng với danh từ/động từ tiếng Việt thường gặp nếu có).
      - **Kiểm tra ranh giới từ (`isWordBoundary(text: String, start: Int, end: Int): Boolean`)**:
        - Nếu ký tự đầu `text[start]` là chữ cái hoặc chữ số Unicode (không phải CJK/Kana): ký tự liền trước `text[start - 1]` (nếu `start > 0`) **không được** là chữ cái hoặc chữ số (`!Character.isLetterOrDigit(prevChar)`).
        - Nếu ký tự cuối `text[end - 1]` là chữ cái hoặc chữ số Unicode (không phải CJK/Kana): ký tự liền sau `text[end]` (nếu `end < text.length`) **không được** là chữ cái hoặc chữ số (`!Character.isLetterOrDigit(nextChar)`).
        - Quy tắc này đảm bảo: `"Pikachu"` trong `"Con Pikachu của Tiểu Trí!"` hoặc `"Pikachu, dùng Điện Thập Vạn Volt!"` được nhận diện chính xác; `"Mewtwo"` được khớp trọn vẹn thành `Mewtwo` (không bị cắt thành `Mew`); và từ tiếng Việt/Anh bất kỳ chứa chuỗi con tình cờ không bao giờ bị bắt nhầm.
      - Hàm `findMatches(text: String): List<PokemonMatchRange>` trả về danh sách các khoảng khớp không chồng lấn (`start`, `end`, `matchedText`, `entry`, `initialFormIndex`) theo thứ tự xuất hiện tăng dần.
    - Cung cấp các hàm tiện ích hiển thị tiếng Việt trong `PokemonLookupHelper`:
      - `formatTypeLabel(rawType: String): String`: chuẩn hóa tên hệ sang tiếng Việt có tiền tố `"Hệ ..."` (ví dụ `"Cỏ"` / `"草"` / `"hệ Cỏ"` → `"Hệ Cỏ"`, cover đầy đủ 18 hệ: *Cỏ, Độc, Lửa, Bay, Băng, Siêu Linh, Rồng, Thường, Giác Đấu, Tiên, Đất, Đá, Điện, Nước, Côn Trùng, Thép, Bóng Tối, Ma*).
      - `formatFormButtonLabel(entry: PokemonEntry, form: PokemonForm, index: Int, language: PokemonNameLanguage): String`: rút gọn tên nút chọn hình thái theo ngôn ngữ đang chọn (tương tự hàm `updateTooltipContent` trong script gốc: cắt bỏ tên cơ sở và từ `"dạng "` / `"的样子"`, nếu rỗng thì hiển thị `"Mặc định"`).
      - `formatDisplayName(entry: PokemonEntry, form: PokemonForm, language: PokemonNameLanguage): String`: trả về tên hiển thị chính trên tiêu đề Dialog theo ngôn ngữ đang chọn (`Hán-Việt`, `Việt / Romaji`, `Trung`, `Anh`) kèm phụ đề đa ngôn ngữ (`nameHv • nameVn • nameEn • nameCn`).

- [ ] **Bước 4: Tạo `IPokemonRepository` & `PokemonRepository` và đăng ký Hilt trong `RepositoryModule`**
  - Tại `app/src/main/java/com/personal/apptruyen/data/repository/IPokemonRepository.kt`:
    ```kotlin
    package com.personal.apptruyen.data.repository

    import com.personal.apptruyen.data.model.PokemonMatchRange
    import com.personal.apptruyen.util.PokemonMatcher
    import kotlinx.collections.immutable.ImmutableList
    import kotlinx.collections.immutable.ImmutableMap

    interface IPokemonRepository {
        suspend fun getMatcher(): PokemonMatcher?

        suspend fun findMatchesInParagraphs(
            paragraphs: List<String>,
        ): ImmutableMap<Int, ImmutableList<PokemonMatchRange>>
    }
    ```
  - Tại `app/src/main/java/com/personal/apptruyen/data/repository/PokemonRepository.kt`:
    - `@Singleton class PokemonRepository @Inject constructor(@ApplicationContext private val context: Context) : IPokemonRepository`
    - Sử dụng `Mutex` + biến `@Volatile private var cachedMatcher: PokemonMatcher? = null` để đảm bảo file `assets/pokemon_pokedex.json` chỉ được đọc và parse đúng 1 lần trên `Dispatchers.IO`. Nếu việc đọc/parse gặp lỗi bất ngờ, log qua `Timber.e(e, "Failed to load pokemon_pokedex.json")` và trả về `null` an toàn (không làm crash màn hình đọc truyện).
    - Trong `findMatchesInParagraphs(paragraphs: List<String>)`:
      - Nếu `paragraphs.isEmpty()` trả về `persistentMapOf()`.
      - Gọi `val matcher = getMatcher() ?: return persistentMapOf()`.
      - Chạy trên `withContext(Dispatchers.Default)`: duyệt từng đoạn văn `paragraphs.forEachIndexed`, kiểm tra `ensureActive()`, gọi `matcher.findMatches(paragraph)`, nếu có kết quả thì đưa vào map `index to matches.toImmutableList()`, cuối cùng trả về `result.toImmutableMap()`.
  - Tại `app/src/main/java/com/personal/apptruyen/di/RepositoryModule.kt`:
    - Bổ sung `@Binds @Singleton abstract fun bindPokemonRepository(impl: PokemonRepository): IPokemonRepository`.

- [ ] **Bước 5: Thêm Token Màu Hệ Pokémon và Màu Gạch Chân trong `ui/theme/PokemonTypeColors.kt`**
  - Tại `app/src/main/java/com/personal/apptruyen/ui/theme/PokemonTypeColors.kt` (tuân thủ nghiêm ngặt `.agents/rules/03-ui.md`: không đặt `Color(0xFF...)` trong Screen/Dialog):
    - Định nghĩa `@Immutable data class PokemonTypeBadgeStyle(val backgroundColor: Color, val contentColor: Color)`.
    - Ánh xạ đầy đủ 18 hệ Pokémon từ bảng màu của script gốc (`#b4f000` Hệ Cỏ, `#d28cd2` Hệ Độc, `#ff6900` Hệ Lửa, `#78dcff` Hệ Bay, `#14f5ff` Hệ Băng, `#f08cdc` Hệ Siêu Linh, `#5078dc` Hệ Rồng, `#dcdcdc` Hệ Thường, `#dc6900` Hệ Giác Đấu, `#ffafdc` Hệ Tiên, `#fac85a` Hệ Đất, `#b48c64` Hệ Đá, `#ffe100` Hệ Điện, `#14b9ff` Hệ Nước, `#46c846` Hệ Côn Trùng, `#aac8f0` Hệ Thép, `#787878` Hệ Bóng Tối, `#a08cff` Hệ Ma) kèm màu chữ tương phản rõ nét (chữ tối `Color(0xFF1A1A1A)` trên nền sáng như Hệ Cỏ / Hệ Điện / Hệ Băng / Hệ Thường / Hệ Đất / Hệ Tiên / Hệ Bay / Hệ Thép, và chữ trắng `Color.White` trên nền đậm như Hệ Lửa / Hệ Rồng / Hệ Nước / Hệ Độc / Hệ Giác Đấu / Hệ Đá / Hệ Côn Trùng / Hệ Bóng Tối / Hệ Ma).
    - Định nghĩa hàm `pokemonHighlightColor(theme: ReaderViewModel.ReaderTheme): Color` trả về màu chữ/gạch chân nổi bật có độ tương phản cao trên cả 3 nền đọc:
      - `LIGHT`: `Color(0xFFD63031)` (đỏ san hô đậm như script gốc `#d63031`).
      - `DARK`: `Color(0xFFFF7675)` (đỏ san hô sáng nổi bật trên nền tối `#1C1B1F`).
      - `SEPIA`: `Color(0xFFB33939)` (đỏ gạch ấm hài hòa và nổi bật trên nền giấy Sepia).

- [ ] **Bước 6: Tạo `ReaderPokemonSupport.kt` chứa `ReaderParagraphItem` và `PokemonLookupDialog`**
  - Tại `app/src/main/java/com/personal/apptruyen/ui/reader/ReaderPokemonSupport.kt`:
    1. **`ReaderParagraphItem` Composable**:
       - Nhận `paragraph: String`, `matches: ImmutableList<PokemonMatchRange>`, `pokemonLookupEnabled: Boolean`, `copyMode: Boolean`, `textStyle: TextStyle`, `readerTheme: ReaderViewModel.ReaderTheme`, `modifier: Modifier`, `onToggleBars: () -> Unit`, `onLongPressSeekAndPlay: () -> Unit`, `onPokemonClick: (PokemonSelection) -> Unit`.
       - Khi `copyMode == true`: giữ nguyên `SelectionContainer { Text(...) }` để chọn/copy văn bản bình thường.
       - Khi `copyMode == false && (!pokemonLookupEnabled || matches.isEmpty())`: dùng `Text` + `combinedClickable(onClick = onToggleBars, onLongClick = onLongPressSeekAndPlay)` như hiện tại (0 overhead cho các đoạn văn không có tên Pokémon).
       - Khi `copyMode == false && pokemonLookupEnabled && matches.isNotEmpty()`:
         - Dùng `remember(paragraph, matches, readerTheme)` dựng `AnnotatedString` tô màu `pokemonHighlightColor(readerTheme)` + `FontWeight.SemiBold` và gắn `addStringAnnotation(tag = "POKEMON", annotation = matchIndex.toString(), start = match.start, end = match.end)`.
         - Lưu `var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }`.
         - Vẽ đường gạch chân nét đứt (`PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)`) bên dưới từng cụm tên Pokémon thông qua `Modifier.drawBehind`: với mỗi `match`, duyệt qua các dòng từ `layout.getLineForOffset(match.start)` đến `layout.getLineForOffset((match.end - 1).coerceAtLeast(match.start))`, tính tọa độ ngang đầu/cuối trên từng dòng bằng `getHorizontalPosition` / `getLineLeft` / `getLineRight` và vẽ đường nét đứt tại `layout.getLineBottom(line) - 2.dp.toPx()`.
         - Xử lý cử chỉ chạm bằng `Modifier.pointerInput(paragraph, matches)`:
           ```kotlin
           detectTapGestures(
               onTap = { tapOffset ->
                   val layout = textLayoutResult
                   val hitMatch = layout?.let { findTappedPokemonMatch(it, tapOffset, matches) }
                   if (hitMatch != null) {
                       onPokemonClick(
                           PokemonSelection(
                               entry = hitMatch.entry,
                               matchedText = hitMatch.matchedText,
                               initialFormIndex = hitMatch.initialFormIndex,
                           ),
                       )
                   } else {
                       onToggleBars()
                   }
               },
               onLongPress = {
                   onLongPressSeekAndPlay()
               },
           )
           ```
         - Hàm `findTappedPokemonMatch(layout: TextLayoutResult, tapOffset: Offset, matches: List<PokemonMatchRange>): PokemonMatchRange?`: kiểm tra `tapOffset.y` nằm trong phạm vi dòng văn bản, `tapOffset.x` nằm giữa `layout.getLineLeft(line)..layout.getLineRight(line)` (tránh trường hợp người dùng chạm vào khoảng trắng cuối dòng bị `getOffsetForPosition` quy về ký tự cuối cùng của dòng), rồi tìm `match` thỏa mãn `charOffset in match.start until match.end`.
    2. **`PokemonLookupDialog` Composable**:
       - Hiển thị `Dialog(onDismissRequest = onDismiss)` với thiết kế thẻ bo tròn hiện đại (`RoundedCornerShape(24.dp)`), tương thích Dark/Light theme:
         - **Thanh đầu (Header)**:
           - Số thứ tự Pokédex (`#0001`..`#1025`), tiêu đề chính theo ngôn ngữ đang chọn (`Hán-Việt` mặc định nếu người dùng chạm vào tên Hán-Việt/Trung, hoặc `Việt / Romaji` nếu chạm vào tên Romaji/Anh), kèm dòng phụ đề hiển thị đồng thời các tên còn lại (`Diệu Oa Hoa • Fushigibana • Venusaur • 妙蛙花`).
           - Nút đóng `✕` (`contentDescription = "Đóng"`).
         - **Thanh chọn ngôn ngữ hiển thị tên**: 4 chip nhỏ gọn (`Hán-Việt`, `Việt / Romaji`, `Trung`, `Anh`) cho phép đổi nhanh ngôn ngữ tên Pokémon và nhãn nút hình thái ngay trong hộp thoại.
         - **Khung hiển thị hình ảnh Pokémon (200.dp)**:
           - Dùng `SubcomposeAsyncImage` của Coil 3 tải `currentForm.imageUrl` (`https://vn.portal-pokemon.com/play/resources/pokedex/img/pm/...`) với `ContentScale.Fit`.
           - Trạng thái **Đang tải (`Loading`)**: hiển thị `CircularProgressIndicator` + hiệu ứng `shimmerBrush()`.
           - Trạng thái **Lỗi mạng (`Error`)**: hiển thị thông báo tiếng Việt `"Không tải được ảnh Pokémon"` kèm nút `"Thử lại"` (tăng `retryHash` để kích hoạt Coil tải lại ảnh).
         - **Huy hiệu Hệ (Type Badges)**:
           - Hiển thị các huy hiệu hệ bằng tiếng Việt (`Hệ Cỏ`, `Hệ Độc`, `Hệ Lửa`, `Hệ Rồng`...) với màu sắc từ `PokemonTypeColors.getBadgeStyle(type)`. Khi đổi sang hình thái khác (ví dụ *Phun Hỏa Long* `Hệ Lửa, Hệ Bay` → *Siêu Cấp Phun Hỏa Long X* `Hệ Lửa, Hệ Rồng`), huy hiệu hệ tự động cập nhật theo hình thái đó.
         - **Bộ chuyển đổi Hình thái (`Forms`)**:
           - Nếu `entry.forms.size > 1`: hiển thị mục `"Hình thái (${entry.forms.size})"` với `FlowRow` (hoặc lưới chip cuộn ngang/dọc mượt mà) chứa các `FilterChip` cho từng hình thái (`Mặc định`, `Mega`, `Mega X`, `Mega Y`, `Gigamax`, `Alola`, `Galar`, `Hisui`, `Paldea`...), đánh dấu hình thái đang chọn và cập nhật ngay lập tức ảnh + hệ + tên khi người dùng chạm.

- [ ] **Bước 7: Cập nhật `SettingsDataStore.kt`, `ReaderViewModel.kt`, `ReaderScreen.kt`, `ReaderSettingsPanel.kt`, `SettingsViewModel.kt` và `SettingsScreen.kt`**
  - **Tại `SettingsDataStore.kt`**:
    - Thêm `/** Bật/tắt tra cứu hình ảnh & hệ Pokémon khi đọc truyện (mặc định: bật) */ val POKEMON_LOOKUP_ENABLED = booleanPreferencesKey("pokemon_lookup_enabled")`.
  - **Tại `ReaderViewModel.kt`**:
    - Inject `private val pokemonRepository: IPokemonRepository` vào constructor.
    - Bổ sung vào `ReaderIntent`:
      - `object TogglePokemonLookup : ReaderIntent`
      - `data class SelectPokemon(val selection: PokemonSelection) : ReaderIntent`
      - `object DismissPokemonDialog : ReaderIntent`
    - Bổ sung vào `ReaderState`:
      - `val pokemonLookupEnabled: Boolean = true`
      - `val paragraphPokemonMatches: ImmutableMap<Int, ImmutableList<PokemonMatchRange>> = persistentMapOf()`
      - `val selectedPokemon: PokemonSelection? = null`
    - Trong `loadDefaultSettings()`: đọc `val pokemonLookupEnabled = prefs[SettingsKeys.POKEMON_LOOKUP_ENABLED] ?: true` và gán vào `_state.value`; nếu chương hiện tại đã có nội dung thì gọi `refreshPokemonMatches(_state.value.content, pokemonLookupEnabled)`.
    - Trong `loadChapter(...)`: sau khi đã format và áp dụng `TextReplacementHelper.applyAllReplacements`, cập nhật `_state.value` với `content` mới (và `selectedPokemon = null`, `paragraphPokemonMatches = persistentMapOf()`) để giao diện hiển thị ngay chữ của chương mà không phải đợi quét từ điển; đồng thời khởi chạy `refreshPokemonMatches(content, _state.value.pokemonLookupEnabled)` trong `pokemonMatchJob` (hủy job cũ nếu chuyển chương nhanh) để điền `paragraphPokemonMatches` ngay khi quét xong trên background thread.
    - Trong `togglePokemonLookup()`: đảo giá trị `pokemonLookupEnabled`, lưu vào `dataStore.edit { it[SettingsKeys.POKEMON_LOOKUP_ENABLED] = newValue }`, và nếu `newValue == true` thì gọi `refreshPokemonMatches(_state.value.content, true)`, nếu `false` thì xóa `paragraphPokemonMatches = persistentMapOf()` và `selectedPokemon = null`.
    - Khi xử lý `ReaderIntent.SelectPokemon(selection)`: nếu `_state.value.autoScrollEnabled == true` thì tắt `autoScrollEnabled = false` (tạm dừng tự cuộn khi mở hộp thoại tra cứu) và gán `selectedPokemon = selection.selection`.
  - **Tại `ReaderScreen.kt`**:
    - Trong `ReaderScreen`: nếu `state.selectedPokemon != null`, hiển thị `PokemonLookupDialog(selection = state.selectedPokemon!!, onDismiss = { viewModel.onIntent(ReaderIntent.DismissPokemonDialog) })` (sử dụng local `state.selectedPokemon?.let { selection -> ... }`, tuyệt đối không dùng toán tử `!!`).
    - Trong `ReaderContentArea`: thay đoạn render `Text` trong `itemsIndexed(paragraphs)` bằng `ReaderParagraphItem(...)`, truyền `matches = state.paragraphPokemonMatches[index] ?: persistentListOf()`, `pokemonLookupEnabled = state.pokemonLookupEnabled`, và callback `onPokemonClick = { viewModel.onIntent(ReaderIntent.SelectPokemon(it)) }`.
    - Truyền `pokemonLookupEnabled = state.pokemonLookupEnabled` và `onTogglePokemonLookup = { viewModel.onIntent(ReaderIntent.TogglePokemonLookup) }` vào `ReaderSettingsPanel`.
  - **Tại `ReaderSettingsPanel.kt`**:
    - Thêm tham số `pokemonLookupEnabled: Boolean` và `onTogglePokemonLookup: () -> Unit`.
    - Trong mục `CÔNG CỤ`, thêm hàng `Switch`:
      - Tiêu đề: `"Từ điển Pokémon"`
      - Mô tả: `"Gạch chân tên Pokémon trong truyện, chạm để xem ảnh và hệ"`
  - **Tại `SettingsViewModel.kt` & `SettingsScreen.kt`**:
    - Thêm `val pokemonLookupEnabled: Boolean = true` vào `SettingsState`, đọc `prefs[SettingsKeys.POKEMON_LOOKUP_ENABLED] ?: true`, và thêm `fun setPokemonLookupEnabled(enabled: Boolean)`.
    - Trong `SettingsScreen.kt` (mục `"Đọc truyện"`), thêm công tắc `"Từ điển Pokémon"` (`"Gạch chân tên Pokémon trong truyện, chạm để xem ảnh và hệ"`) để người dùng có thể bật/tắt cả từ màn hình Cài đặt chính.

- [ ] **Bước 8: Viết Unit Tests (`PokemonLookupHelperTest.kt` & `ReaderViewModelTest.kt`) và Chạy Verification Gate**
  - Tạo mới `app/src/test/java/com/personal/apptruyen/util/PokemonLookupHelperTest.kt` kiểm thử đầy đủ các kịch bản trong Test Contract (TC-01..TC-07).
  - Cập nhật `app/src/test/java/com/personal/apptruyen/ui/reader/ReaderViewModelTest.kt` bổ sung các test cho `pokemonLookupEnabled`, `TogglePokemonLookup`, `SelectPokemon`/`DismissPokemonDialog` và tính bất biến của chuỗi gửi sang `TtsManager` (TC-08..TC-10).
  - Chạy đầy đủ lệnh kiểm증 Tier 2: `.\gradlew.bat ktlintCheck; .\gradlew.bat testDebugUnitTest; .\gradlew.bat assembleDebug`.
  - Cập nhật tài liệu theo `.agents/rules/06-docs.md`.

---

### 7. Test Contract

| # | Hành vi (ngôn ngữ nghiệp vụ) | Input | Kết quả mong đợi / thông báo lỗi tiếng Việt |
| - | ---------------------------- | ----- | ------------------------------------------- |
| TC-01 | Nhận diện tên Pokémon đa ngôn ngữ (Hán-Việt, Việt Romaji, Anh, Trung, Nhật) trong đoạn văn tiếng Việt | Đoạn văn: `"Tiểu Trí gọi Bì Khả Khâu, Lizardon, Charizard, 皮卡丘 và ピカチュウ ra sân."` | `PokemonMatcher.findMatches` trả về đúng 5 `PokemonMatchRange` theo thứ tự xuất hiện với `matchedText` lần lượt là `"Bì Khả Khâu"`, `"Lizardon"`, `"Charizard"`, `"皮卡丘"`, `"ピカチュウ"`, trỏ đúng tới entry của Pikachu (#25) và Lizardon (#6). |
| TC-02 | Ưu tiên khớp từ dài nhất trước (Longest-Match-First) cho chuỗi con và tên hình thái | Đoạn văn 1: `"Siêu Cấp Phun Hỏa Long X đối đầu Mewtwo và Diệu Oa Chủng Tử."` | Khớp trọn vẹn `"Siêu Cấp Phun Hỏa Long X"` (với `initialFormIndex = 1` ứng với hình thái Mega X, hệ `Lửa, Rồng`), `"Mewtwo"` (không bị cắt ngắn thành `"Mew"`), và `"Diệu Oa Chủng Tử"` (không bị dừng sớm ở `"Diệu Oa Chủng"`). |
| TC-03 | Kiểm tra ranh giới từ (`isWordBoundary`) chống bắt nhầm từ con bên trong từ tiếng Việt hoặc tiếng Anh khác | Đoạn văn chứa các từ có chuỗi con trùng ngẫu nhiên (ví dụ `"Mewtwo"` không bắt `"Mew"` rời, hoặc từ ghép liền chữ cái `"abcPikachuxyz"` không có khoảng trắng/dấu câu ngăn cách) | `"abcPikachuxyz"` trả về `emptyList()` (0 kết quả dương tính giả); trong khi `"(\"Pikachu!\")"` hoặc `"Pikachu, tiến lên!"` (ngăn cách bởi dấu câu/ngoặc/khoảng trắng) vẫn khớp chính xác `"Pikachu"`. |
| TC-04 | Chọn đúng hình thái khởi đầu (`initialFormIndex`) khi chạm vào tên hình thái Mega / Gigamax / Vùng miền | Người dùng chạm vào `"Lizardon Mega Y"` hoặc `"Siêu Cực Cự Hóa Phun Hỏa Long"` hoặc `"Tiểu Lạp Đạt A La Lạp"` | `PokemonMatchRange.initialFormIndex` trả về đúng chỉ số của hình thái tương ứng trong `entry.forms` (ví dụ Mega Y = `2`, Gigamax = `3`, Alola = `1`), giúp `PokemonLookupDialog` mở ra hiển thị ngay ảnh và hệ của hình thái đó. |
| TC-05 | Chuẩn hóa nhãn Hệ sang tiếng Việt và rút gọn nhãn nút Hình thái | `rawType = "草"`, `"Lửa"`, `"Siêu Linh"`; `forms` của Phun Hỏa Long (`["Phun Hỏa Long", "Siêu Cấp Phun Hỏa Long X", "Siêu Cấp Phun Hỏa Long Y", "Siêu Cực Cự Hóa Phun Hỏa Long"]`) | `formatTypeLabel` trả về `"Hệ Cỏ"`, `"Hệ Lửa"`, `"Hệ Siêu Linh"`; `formatFormButtonLabel` trả về `"Mặc định"` cho form 0 và `"Siêu Cấp X"` / `"Mega X"`, `"Siêu Cấp Y"` / `"Mega Y"`, `"Siêu Cực Cự Hóa"` / `"Gigamax"` cho các form tiếp theo. |
| TC-06 | Độ toàn vẹn của file dữ liệu thật `app/src/main/assets/pokemon_pokedex.json` | Đọc và parse trực tiếp file `app/src/main/assets/pokemon_pokedex.json` trong unit test JVM | Parse thành công ≥ 1.000 loài Pokémon (`entries.size >= 1000`), mọi entry đều có `nameCn`, `nameVn`, `nameHv` không rỗng, ≥ 1 form có `imageUrl` bắt đầu bằng `"https://vn.portal-pokemon.com/play/resources/pokedex/img/pm/"`, và tra cứu được các tên Hán-Việt tiêu biểu (*Bì Khả Khâu, Tiểu Hỏa Long, Phun Hỏa Long, Diệu Oa Chủng, Kiệt Ni Quy, Thủy Tiễn Quy, Siêu Mộng, Mộng Huyễn, Liệt Không Tọa, Cố Lạp Đốn, Cái Âu Ca, Khoái Long, Bạo Lý Long, Cảnh Điển, Hồ Địa, Đại Nham Xà, Cự Kim Quái, Lộ Ca Lợi Áo, Sa Nại Đóa*). |
| TC-07 | Trạng thái mặc định của tính năng là BẬT (`true`) và cho phép bật/tắt qua `ReaderIntent.TogglePokemonLookup` | Khởi tạo `ReaderViewModel` với `emptyPreferences()` (người dùng chưa từng chỉnh cài đặt) | `state.value.pokemonLookupEnabled == true`, `pokemonRepository.findMatchesInParagraphs` được gọi và `state.value.paragraphPokemonMatches` chứa kết quả khớp. Khi gửi `ReaderIntent.TogglePokemonLookup`, `pokemonLookupEnabled` chuyển sang `false`, `paragraphPokemonMatches` được dọn rỗng và `SettingsKeys.POKEMON_LOOKUP_ENABLED = false` được lưu vào DataStore. |
| TC-08 | Mở/đóng hộp thoại tra cứu Pokémon (`SelectPokemon` / `DismissPokemonDialog`) và tự dừng Auto-scroll | Đang bật `autoScrollEnabled = true`, gửi `ReaderIntent.SelectPokemon(selection)` rồi `ReaderIntent.DismissPokemonDialog` | Khi `SelectPokemon`: `state.value.selectedPokemon == selection` và `state.value.autoScrollEnabled == false`. Khi `DismissPokemonDialog`: `state.value.selectedPokemon == null`. |
| TC-09 | Bảo toàn 100% văn bản chương gửi sang TTS (`TtsManager`) | Tải chương có nội dung `"Tiểu Hỏa Long phun lửa."` với `pokemonLookupEnabled = true` | `ttsManager.setContent("Tiểu Hỏa Long phun lửa.", ...)` nhận chuỗi văn bản nguyên bản sạch sẽ, không chứa bất kỳ thẻ HTML hay ký tự đánh dấu lạ nào. |

---

### 8. Verification Plan

- **Unit Tests (JUnit 5 + MockK + `kotlinx-coroutines-test`)**:
  - `app/src/test/java/com/personal/apptruyen/util/PokemonLookupHelperTest.kt`:
    - Kiểm thử thuật toán `PokemonMatcher` (Trie longest-match-first, case-insensitive, word boundaries, `initialFormIndex`, chống dương tính giả).
    - Kiểm thử trực tiếp việc đọc và parse file thật `app/src/main/assets/pokemon_pokedex.json` từ đĩa để xác minh schema JSON hợp lệ 100%, không thiếu trường, và tra cứu chính xác hàng loạt tên Pokémon Hán-Việt / Việt / Anh / Trung / Nhật.
    - Kiểm thử `formatTypeLabel`, `formatFormButtonLabel`, `formatDisplayName`.
  - `app/src/test/java/com/personal/apptruyen/ui/reader/ReaderViewModelTest.kt`:
    - Kiểm thử mặc định `pokemonLookupEnabled == true`, luồng tạo `paragraphPokemonMatches`, `TogglePokemonLookup`, `SelectPokemon` (kèm tự tắt `autoScrollEnabled`), `DismissPokemonDialog`, và xác minh `ttsManager.setContent` nhận nguyên văn bản gốc.
- **Lệnh Verification (Tier 1 & Tier 2)**:
  ```powershell
  .\gradlew.bat compileDebugKotlin
  .\gradlew.bat ktlintCheck; .\gradlew.bat testDebugUnitTest; .\gradlew.bat assembleDebug
  ```
- **Các bước kiểm thử thủ công trên thiết bị (dành cho User sau khi triển khai)**:
  1. Mở một chương truyện đồng nhân Pokémon (hoặc bất kỳ chương truyện nào có tên Pokémon như *Bì Khả Khâu*, *Phun Hỏa Long*, *Pikachu*, *Lizardon*, *Siêu Mộng*, *Liệt Không Tọa*...):
     - Kiểm tra các tên Pokémon được tô màu nổi bật và có đường gạch chân nét đứt bên dưới.
  2. Chạm vào tên Pokémon được gạch chân:
     - Kiểm tra hộp thoại Từ điển Pokémon hiện lên với ảnh rõ nét từ `vn.portal-pokemon.com`, các huy hiệu hệ bằng tiếng Việt có màu sắc chuẩn (`Hệ Lửa`, `Hệ Bay`...), các chip chuyển đổi hình thái (`Mặc định`, `Mega X`, `Mega Y`, `Gigamax`...) và thanh chuyển đổi ngôn ngữ tên (`Hán-Việt`, `Việt / Romaji`, `Trung`, `Anh`).
  3. Chạm vào chữ bình thường (không phải tên Pokémon) trong cùng đoạn văn đó:
     - Kiểm tra thanh TopAppBar và TtsControlBar ẩn/hiện bình thường.
  4. Nhấn giữ vào đoạn văn có tên Pokémon:
     - Kiểm tra TTS bắt đầu đọc từ đoạn văn đó như bình thường.
  5. Mở **Cài đặt đọc** (biểu tượng góc trên phải trong `ReaderScreen`) → mục **CÔNG CỤ**:
     - Kiểm tra công tắc **"Từ điển Pokémon"** đang mặc định BẬT. Thử tắt công tắc → gạch chân biến mất ngay lập tức; bật lại → gạch chân xuất hiện lại.

---

### 9. Rủi Ro & Phòng Ngừa

| Rủi ro | Mức (Cao/TB/Thấp) | Technical control |
| ------ | ----------------- | ----------------- |
| **R1: Dương tính giả (False Positives)** khi tên Pokémon hoặc biến thể Hán-Việt/Anh trùng với từ đơn tiếng Việt thông dụng hoặc khớp nhầm vào giữa một từ khác | **Cao** | 1. Kiểm tra ranh giới từ nghiêm ngặt `isWordBoundary(text, start, end)` trong `PokemonLookupHelper.kt`: nếu biên của từ khóa là ký tự chữ/số Unicode (Latin/Tiếng Việt), ký tự liền kề ngoài biên bắt buộc không được là chữ/số (`!Character.isLetterOrDigit`).<br>2. Áp dụng danh sách loại trừ `EXCLUDED_COMMON_ALIASES` và chặn mọi từ khóa Latin đơn lẻ ngắn `< 3` ký tự khi xây dựng Trie.<br>3. Kiểm chứng bằng unit test `PokemonLookupHelperTest` (TC-02, TC-03, TC-06). |
| **R2: Giật lag UI (Main Thread Jank)** khi nạp file JSON từ điển hoặc quét chương truyện dài hàng trăm đoạn văn | **Trung bình** | 1. Chuẩn hóa dữ liệu JSON từ 1.47MB xuống ~240KB (`app/src/main/assets/pokemon_pokedex.json`).<br>2. `PokemonRepository` nạp và dựng `PokemonMatcher` (Trie) một lần duy nhất trên `Dispatchers.IO` với khóa `Mutex`.<br>3. Quá trình tìm tên Pokémon trong các đoạn văn chạy hoàn toàn trên `Dispatchers.Default` (`findMatchesInParagraphs`) sau khi `ReaderViewModel` đã hiển thị văn bản chương lên UI (`isLoading = false`), sử dụng thuật toán Trie tuyến tính $O(N \cdot L_{max})$ tốn `< 5ms` cho toàn bộ chương truyện. |
| **R3: Xung đột cử chỉ chạm (Gesture Conflict)** giữa chạm vào tên Pokémon, chạm vào đoạn văn để ẩn/hiện thanh công cụ (`onToggleBars`), nhấn giữ để đọc TTS (`onSeekAndPlay`), và bôi đen văn bản (`copyMode`) | **Trung bình** | 1. Khi `copyMode == true`: giữ nguyên `SelectionContainer` + `Text` thuần.<br>2. Khi `copyMode == false`: trong `ReaderParagraphItem`, dùng `pointerInput` + `detectTapGestures(onTap = ..., onLongPress = ...)` kết hợp `TextLayoutResult` và kiểm tra biên ngang của dòng (`tapOffset.x in getLineLeft(line)..getLineRight(line)`). Chỉ khi vị trí chạm thực sự rơi vào phạm vi ký tự của `PokemonMatchRange` mới mở `PokemonLookupDialog`; mọi vị trí chạm khác trong đoạn văn đều gọi `onToggleBars()`, và `onLongPress` luôn gọi `onSeekAndPlay(index)`. |
| **R4: Lỗi mạng hoặc ngoại lệ khi tải ảnh Pokémon / đọc file asset** | **Thấp** | 1. `PokemonRepository.getMatcher()` bọc trong `runCatching` (ném lại `CancellationException`), nếu lỗi thì log qua `Timber.e` và trả về `null` để chương truyện vẫn đọc bình thường.<br>2. `PokemonLookupDialog` dùng `SubcomposeAsyncImage` của Coil 3 (có cache đĩa 50MB trong `CoilModule`), hiển thị trạng thái lỗi tiếng Việt `"Không tải được ảnh Pokémon"` kèm nút `"Thử lại"` khi mất mạng. |

### 4.5.2 Adversarial Timelines (Phân tích đối kháng S2 / S3 / S4 / S5)

```
Cơ chế 1: Chuyển chương nhanh hoặc bật/tắt công tắc Từ điển Pokémon khi đang quét đoạn văn (Đồng thời & Vòng đời S2/S4)
T1: ReaderViewModel tải xong Chương 1, khởi chạy pokemonMatchJob (Coroutine A) trên Dispatchers.Default để quét Chương 1 | T2: Người dùng lập tức bấm chuyển sang Chương 2 hoặc tắt công tắc TogglePokemonLookup (Coroutine B) | T3: hàm refreshPokemonMatches() gọi pokemonMatchJob?.cancel() trước khi tạo job mới; trong PokemonRepository.findMatchesInParagraphs có kiểm tra ensureActive() và ném CancellationException
Kết cục: Coroutine A bị hủy ngay lập tức, không bao giờ ghi đè kết quả khớp cũ của Chương 1 lên nội dung của Chương 2 → AN TOÀN
```

```
Cơ chế 2: Khởi tạo đồng thời PokemonMatcher từ nhiều luồng (TOCTOU & Khởi tạo tài nguyên S2)
T1: Coroutine 1 gọi pokemonRepository.getMatcher() khi cachedMatcher == null | T2: Coroutine 2 cũng gọi getMatcher() cùng lúc trước khi file assets/pokemon_pokedex.json đọc xong | T3: PokemonRepository sử dụng Mutex().withLock { cachedMatcher ?: loadMatcherFromAssets().also { cachedMatcher = it } } (double-checked locking với Mutex)
Kết cục: File pokemon_pokedex.json chỉ được đọc và dựng Trie đúng 1 lần duy nhất trong suốt vòng đời ứng dụng → AN TOÀN
```

```
Cơ chế 3: Đồng bộ cài đặt POKEMON_LOOKUP_ENABLED giữa ReaderSettingsPanel và SettingsScreen qua DataStore (S3 DataStore)
T1: Người dùng bật/tắt "Từ điển Pokémon" trong ReaderSettingsPanel | T2: ReaderViewModel.togglePokemonLookup() cập nhật ngay _state.value và ghi dataStore.edit { it[SettingsKeys.POKEMON_LOOKUP_ENABLED] = newValue } | T3: SettingsViewModel lắng nghe dataStore.data Flow tự động nhận giá trị mới; lần mở ReaderScreen tiếp theo loadDefaultSettings() đọc đúng giá trị đã lưu
Kết cục: Trạng thái cài đặt nhất quán 100% giữa các màn hình và qua các lần khởi động lại app → AN TOÀN
```

```
Cơ chế 4: Biên giới bảo mật & quyền riêng tư khi tải ảnh Pokémon và đọc dữ liệu (S5 Security & Privacy)
T1: Người dùng mở PokemonLookupDialog | T2: Coil 3 tải ảnh tĩnh PNG qua HTTPS từ tên miền chính thức https://vn.portal-pokemon.com/play/resources/pokedex/img/pm/<sha1>.png | T3: Không gửi bất kỳ cookie nguồn truyện, token hay nội dung chương truyện nào ra ngoài; không sử dụng WebView hay thực thi JavaScript từ script gốc
Kết cục: Không rò rỉ dữ liệu người dùng hay cookie đăng nhập nguồn truyện → AN TOÀN
```

---

### 10. Cập Nhật Docs

Theo ma trận tại `.agents/rules/06-docs.md`, sau khi triển khai và verify xong sẽ cập nhật:

- `docs/architecture.md`: bổ sung `PokemonModels.kt`, `PokemonLookupHelper.kt`, `IPokemonRepository.kt`, `PokemonRepository.kt`, `PokemonTypeColors.kt`, `ReaderPokemonSupport.kt`, `assets/pokemon_pokedex.json`, luồng dữ liệu số 22 (Tra cứu Pokémon khi đọc truyện) và cập nhật timestamp.
- `docs/progress.md`: thêm tính năng **"Từ điển Pokémon khi đọc truyện (Pokemon Image Hover / Lookup)"** vào danh sách hoàn thành và bảng lịch sử thay đổi, cập nhật timestamp.
- `docs/changelog.md`: thêm mục dưới `## [Unreleased]` → `### Thêm mới`.
- `docs/plans/README.md` & `docs/plans/2026-10-14-pokemon-image-hover-implementation-plan.md`: cập nhật trạng thái sang `Completed`.

## Audit History

<!-- Reviewer chỉ append "### Audit Run N" ở dưới. Không sửa phần Technical Plan. -->

### Audit Run 1

[REVIEWER_MODEL: gemini-3.8-flash]
- **Ngày thẩm định**: 2026-10-14
- **Revision được review**: Revision 1
- **Kiểm chứng lỗi cũ**: N/A (Audit Run đầu tiên).

#### 1. Mental Simulation Verification Matrix

| Kịch bản | Status | Evidence Type | Chi tiết kiểm chứng độc lập |
| -------- | ------ | ------------- | --------------------------- |
| **S1: Cold-start & Build (Gradle / KSP / ktlint / R8)** | `PASS` | `[CMD]`, `[SRC]`, `[URL]` | 1. `[CMD]` Chạy `.\gradlew.bat compileDebugKotlin` đạt `BUILD SUCCESSFUL` (exit code `0`).<br>2. `[SRC]` `app/build.gradle.kts:24-27,106-160`: `minSdk = 26`, Compose BOM `2026.05.01`, Coil `3.4.0` (`coil-compose` + `coil-network-okhttp`), `kotlinx-serialization-json:1.11.0`, `kotlinx-collections-immutable:0.3.8`, `datastore-preferences:1.2.1` đã được khai báo sẵn, không thêm dependency mới.<br>3. `[SRC]` `app/proguard-rules.pro:61,70-76`: Đã có rule `-keep class com.personal.apptruyen.data.model.** { *; }` và `-keep,includedescriptorclasses class com.personal.apptruyen.data.model.**$$serializer { *; }`. Đặt `PokemonModels.kt` trong `com.personal.apptruyen.data.model` đảm bảo bản build release (`isMinifyEnabled = true` tại `app/build.gradle.kts:51`) giữ nguyên serializer mà không cần sửa `proguard-rules.pro`.<br>4. `[SRC]` `app/src/main/java/com/personal/apptruyen/di/RepositoryModule.kt:23-46`: Đăng ký `@Binds @Singleton abstract fun bindPokemonRepository(impl: PokemonRepository): IPokemonRepository` trong `SingletonComponent` khớp kiến trúc DI hiện hữu. |
| **S2: Lifecycle & Teardown (Coroutines, Service, TTS, Memory)** | `PASS` | `[SRC]`, `[ADV]` | 1. `[SRC]` `app/src/main/java/com/personal/apptruyen/ui/reader/ReaderViewModel.kt:402-456,487-494`: `ReaderViewModel` quản lý job bất đồng bộ qua `viewModelScope` (tương tự `prefetchJob`). Việc dùng `pokemonMatchJob?.cancel()` trước khi chạy `findMatchesInParagraphs` trên `Dispatchers.Default` kèm `ensureActive()` đảm bảo hủy ngay khi chuyển chương hoặc thoát màn hình.<br>2. `[SRC]` `app/src/main/java/com/personal/apptruyen/ui/reader/ReaderViewModel.kt:445-451`: `ttsManager.setContent(content, title)` và `ttsManager.setContentForAutoAdvance(content, title)` nhận trực tiếp biến `content: String` thuần trước khi quét từ điển Pokémon, không làm thay đổi dữ liệu gửi tới `TtsService`. |
| **S3: Database & Temporal Logic (Room + DataStore)** | `PASS` | `[SRC]`, `[ADV]` | 1. `[SRC]` `app/src/main/java/com/personal/apptruyen/data/local/AppDatabase.kt:14-24` & `app/src/main/java/com/personal/apptruyen/di/AppModule.kt:69-82`: Không thay đổi Room entity hay database version (`version = 9` giữ nguyên).<br>2. `[SRC]` `app/src/main/java/com/personal/apptruyen/data/local/SettingsDataStore.kt:36-82`: Khóa mới `POKEMON_LOOKUP_ENABLED = booleanPreferencesKey("pokemon_lookup_enabled")` mặc định `true`, không đụng tới khóa cũ.<br>3. `[SRC]` `app/src/main/java/com/personal/apptruyen/data/repository/BackupRepository.kt:68-71,204-206`: `exportBackup` thu thập mọi key trong `prefs.asMap()` và `importBackup` khôi phục mọi key có giá trị `"true"`/`"false"` qua `prefs[booleanPreferencesKey(key)] = value.toBoolean()`, nên khóa mới tương thích tự động hai chiều với tính năng Sao lưu / Khôi phục. |
| **S4: Cross-Task State Flow (MVI, Compose, Test Harness)** | `PASS` | `[SRC]`, `[CMD]` | 1. `[SRC]` `app/src/main/java/com/personal/apptruyen/ui/reader/ReaderViewModel.kt:34-108,111-122,156-185,213-245`: Luồng MVI `ReaderIntent` → `onIntent` → `_state` (`StateFlow<ReaderState>`) được giữ nguyên. Bổ sung `pokemonLookupEnabled`, `paragraphPokemonMatches`, `selectedPokemon` vào `ReaderState` và 3 intents mới tuân thủ đúng MVI.<br>2. `[SRC]` `app/src/main/java/com/personal/apptruyen/ui/settings/SettingsViewModel.kt:78-92,112-138` & `SettingsScreen.kt:191-215`: Đồng bộ trạng thái `pokemonLookupEnabled` giữa `SettingsScreen` và `ReaderSettingsPanel` thông qua `SettingsDataStore`.<br>3. `[SRC]` & `[CMD]` Kiểm tra toàn bộ `app/src/test/`: chỉ có duy nhất `app/src/test/java/com/personal/apptruyen/ui/reader/ReaderViewModelTest.kt:79-89` khởi tạo `ReaderViewModel`. Plan đã liệt kê cập nhật `createViewModel()` trong `ReaderViewModelTest.kt` và chạy baseline test pass (`exit code 0`). |
| **S5: Security & Privacy Boundary** | `PASS` | `[SRC]`, `[ADV]` | 1. `[SRC]` `app/src/main/AndroidManifest.xml:13-43`: Không thêm Activity/Service/Receiver mới, không thay đổi `exported`.<br>2. `[SRC]` `app/src/main/java/com/personal/apptruyen/di/CoilModule.kt:20-39`: `CoilModule` tạo `ImageLoader` độc lập (không gắn `WebViewCookieHandler` của `AppModule.provideOkHttpClient` tại `AppModule.kt:53-62`), chỉ tải ảnh tĩnh PNG qua HTTPS từ `https://vn.portal-pokemon.com/play/resources/pokedex/img/pm/...`, không gửi cookie nguồn truyện và không thực thi mã JavaScript. |
| **S6: External Resilience (Mạng, Asset IO, Image Loading)** | `PASS` | `[SRC]` | 1. `[SRC]` `app/src/main/java/com/personal/apptruyen/ui/components/StoryCardPremium.kt:96-130` & `CoilModule.kt:28-39`: Sử dụng `SubcomposeAsyncImage` kết hợp disk cache 50MB và memory cache 25%; khi mất mạng hiển thị thông báo tiếng Việt `"Không tải được ảnh Pokémon"` kèm nút `"Thử lại"`.<br>2. Trong `PokemonRepository.getMatcher()`, việc đọc `assets/pokemon_pokedex.json` bọc `runCatching` (ném lại `CancellationException`), nếu lỗi log qua `Timber.e` và trả về `null` để không ảnh hưởng việc đọc truyện. |
| **Domain: Open Inquiry (TTS, Hiệu năng Compose, UI Tiếng Việt, Kích thước file)** | `PASS` | `[SRC]` | 1. `[SRC]` `app/src/main/java/com/personal/apptruyen/ui/reader/ReaderScreen.kt:1-715`: File `ReaderScreen.kt` hiện đã dài 715 dòng (> 600 dòng tối đa theo `.agents/rules/01-coding.md`). Việc tách `ReaderParagraphItem` và `PokemonLookupDialog` sang file mới `ReaderPokemonSupport.kt` và tách màu hệ sang `ui/theme/PokemonTypeColors.kt` tuân thủ quy tắc kích thước file và quy tắc không hardcode `Color(0xFF...)` trong Screen/Dialog (`.agents/rules/03-ui.md:18-19`).<br>2. Toàn bộ chuỗi hiển thị trên UI (`"Từ điển Pokémon"`, `"Gạch chân tên Pokémon trong truyện, chạm để xem ảnh và hệ"`, `"Không tải được ảnh Pokémon"`, `"Thử lại"`, `"Đóng"`, `"Mặc định"`, `"Hình thái"`, `"Hệ Cỏ"`, `"Hệ Lửa"`...) đều 100% tiếng Việt. |

#### 2. Claim Verification

- **Claim 1 — `BackupRepository` tự động sao lưu và khôi phục khóa `booleanPreferencesKey("pokemon_lookup_enabled")`**:
  - Xác minh bằng `[SRC]` `app/src/main/java/com/personal/apptruyen/data/repository/BackupRepository.kt:68-71` (`prefs.asMap().forEach { (key, value) -> settingsMap[key.name] = value.toString() }`) và dòng `204-206` (`value == "true" || value == "false" -> prefs[booleanPreferencesKey(key)] = value.toBoolean()`). Khẳng định của plan hoàn toàn chính xác.
- **Claim 2 — Quy tắc ProGuard/R8 giữ nguyên serializer cho các class `@Serializable` trong `com.personal.apptruyen.data.model`**:
  - Xác minh bằng `[SRC]` `app/proguard-rules.pro:61,70-76`: Đã có `-keep class com.personal.apptruyen.data.model.** { *; }` và `-keep,includedescriptorclasses class com.personal.apptruyen.data.model.**$$serializer { *; }`.
- **Claim 3 — `Coil 3` (`3.4.0`), `Compose BOM` (`2026.05.01`), `kotlinx-serialization-json` (`1.11.0`) tương thích với `SubcomposeAsyncImage` và `AnnotatedString`**:
  - Xác minh bằng `[SRC]` `app/build.gradle.kts:106,129-130,159`, `[SRC]` `app/src/main/java/com/personal/apptruyen/ui/components/StoryCardPremium.kt:96-114` và `[URL]` tài liệu chính thức AndroidX Compose BOM (`https://developer.android.com/jetpack/compose/bom`), Coil 3 (`https://coil-kt.github.io/coil/`), Kotlinx Serialization (`https://github.com/Kotlin/kotlinx.serialization`).

#### 3. Adversarial Timelines (S2, S3, S4, S5)

```text
Cơ chế 1 (Trục Đồng thời & Vòng đời S2/S4): Đua tốc độ giữa loadDefaultSettings() và loadChapter() khi khởi tạo ReaderViewModel
T1: ReaderViewModel.init gọi đồng thời loadDefaultSettings() (Coroutine A) và loadChapter() (Coroutine B) (ReaderViewModel.kt:247-250) | T2: Coroutine B hoàn tất trước (ví dụ đọc chương từ cache), gọi refreshPokemonMatches(content, enabled = true) | T3: Ngay sau đó Coroutine A đọc xong DataStore (giả sử người dùng đã tắt pokemonLookupEnabled = false), cập nhật _state.value và gọi lại refreshPokemonMatches(content, enabled = false) -> hủy pokemonMatchJob của Coroutine B và xóa paragraphPokemonMatches = persistentMapOf()
Kết cục: Dù Coroutine A hay B xong trước, trạng thái cuối cùng luôn phản ánh đúng cài đặt trong DataStore và nội dung chương hiện tại → AN TOÀN
```

```text
Cơ chế 2 (Trục TOCTOU & Khởi tạo tài nguyên S2): Nhiều lời gọi đồng thời vào PokemonRepository.getMatcher()
T1: Coroutine 1 gọi getMatcher() khi cachedMatcher == null | T2: Coroutine 2 gọi getMatcher() trước khi Coroutine 1 đọc xong assets/pokemon_pokedex.json | T3: Mutex.withLock giữ Coroutine 2 đợi; sau khi Coroutine 1 gán cachedMatcher, Coroutine 2 đọc thấy cachedMatcher != null và tái sử dụng ngay
Kết cục: File JSON chỉ được đọc và parse đúng 1 lần duy nhất → AN TOÀN
```

```text
Cơ chế 3 (Trục Thất bại giữa chừng S6): Lỗi đọc/parse file asset hoặc mất mạng khi tải ảnh Pokémon
T1: Người dùng mở chương truyện hoặc chạm vào tên Pokémon khi thiết bị đang offline | T2: (a) Nếu asset lỗi, getMatcher() bắt ngoại lệ (trừ CancellationException), log Timber.e và trả null; (b) Khi mở Dialog offline, SubcomposeAsyncImage rơi vào trạng thái Error | T3: (a) Chương truyện vẫn hiển thị và đọc TTS bình thường; (b) Dialog hiển thị thông báo tiếng Việt "Không tải được ảnh Pokémon" và nút "Thử lại"
Kết cục: Không crash ứng dụng, có thông báo tiếng Việt và cơ chế thử lại rõ ràng → AN TOÀN
```

```text
Cơ chế 4 (Trục Bảo mật & Riêng tư S5): Tải ảnh từ vn.portal-pokemon.com
T1: Người dùng chạm vào tên Pokémon mở PokemonLookupDialog | T2: SubcomposeAsyncImage dùng ImageLoader của CoilModule tải URL ảnh PNG qua HTTPS | T3: ImageLoader không chia sẻ CookieJar nguồn truyện (WebViewCookieHandler chỉ gắn vào OkHttpClient của Scraper trong AppModule.kt:53-56)
Kết cục: Không gửi cookie đăng nhập nguồn truyện sang bên thứ ba → AN TOÀN
```

#### 4. Commands Executed

| Lệnh | Exit Code | Kết quả tóm tắt |
| ---- | --------- | --------------- |
| `.\gradlew.bat compileDebugKotlin` | `0` | `BUILD SUCCESSFUL in 5s` (8 actionable tasks: 8 up-to-date) |
| `node .agents/scripts/plan-approval.js status --plan docs/plans/2026-10-14-pokemon-image-hover-implementation-plan.md` | `0` | `approved: false`, status: `Ready for Review` |
| `.\gradlew.bat testDebugUnitTest --tests "com.personal.apptruyen.ui.reader.ReaderViewModelTest"` | `0` | `BUILD SUCCESSFUL in 8s` (Tất cả unit test hiện có trong `ReaderViewModelTest` đều pass) |
| `Select-String -Path app\src\test\java\com\personal\apptruyen\ui\reader\ReaderViewModelTest.kt -Pattern "ReaderViewModel"` | `0` | Xác nhận `createViewModel()` tại dòng 79–80 là điểm khởi tạo duy nhất trong test |

#### 5. Phát Hiện Mới (Fresh Findings & Regression Check)

Không phát hiện lỗi `[BLOCKER]` hay `[MAJOR]`. Ghi nhận 2 khuyến nghị kỹ thuật mức `[MINOR]` (`[Advisory Recommendations]`) để đảm bảo độ chính xác tuyệt đối khi triển khai.

##### Các Tấn Công Đã Thử (Attempted Attack Vectors)

1. **Kịch bản tấn công 1 — Lệch chỉ số đoạn văn (`paragraphIndex`) giữa `ReaderViewModel` và `ReaderScreen` khi đoạn văn dài quá 500 ký tự**:
   - *Giả thuyết tấn công*: Tại `[SRC]` `app/src/main/java/com/personal/apptruyen/ui/reader/ReaderScreen.kt:92-99`, danh sách `paragraphs` được tách bằng `TtsManager.splitIntoParagraphs(state.content)` (`[SRC]` `TtsManager.kt:238-268`), trong đó các đoạn dài hơn `maxLength = 500` sẽ được tách nhỏ tiếp theo câu (`SENTENCE_SPLIT_REGEX`). Nếu `ReaderViewModel.refreshPokemonMatches` tự tách đoạn bằng `content.split("\n\n")` thay vì gọi `TtsManager.splitIntoParagraphs(content)`, chỉ số `index` trong `paragraphPokemonMatches` sẽ bị lệch so với `itemsIndexed(paragraphs)` trên UI và gây lệch vị trí gạch chân hoặc `IndexOutOfBoundsException` khi dựng `AnnotatedString`.
   - *Lý do thiết kế đứng vững & Lưu ý thực thi*: `TtsManager.splitIntoParagraphs` là hàm `companion object` thuần tĩnh (`TtsManager.kt:238`). Khi `ReaderViewModel.refreshPokemonMatches` gọi trực tiếp `TtsManager.splitIntoParagraphs(content)` và `ReaderParagraphItem` kiểm tra bảo vệ `match.start >= 0 && match.end <= paragraph.length`, chỉ số đoạn văn và offset ký tự luôn khớp 1-1 tuyệt đối với `ReaderScreen`. (Được ghi nhận vào khuyến nghị `[MINOR-1]` bên dưới).

2. **Kịch bản tấn công 2 — Lệch độ dài chuỗi Unicode khi dùng `String.lowercase(Locale.ROOT)` trên toàn đoạn văn trước khi dò Trie**:
   - *Giả thuyết tấn công*: Một số ký tự Unicode đặc biệt (ví dụ `İ` U+0130 trong văn bản ngoại lai) khi gọi `.lowercase(Locale.ROOT)` trên cả chuỗi `String` sẽ nở thành 2 code units (`i\u0307`), làm thay đổi độ dài chuỗi đã chuẩn hóa so với chuỗi gốc `paragraph`. Nếu dùng offset trên chuỗi đã `.lowercase()` để gắn `addStyle(style, match.start, match.end)` lên `paragraph` gốc, vị trí gạch chân sau ký tự đó sẽ bị lệch hoặc vượt biên `paragraph.length`.
   - *Lý do thiết kế đứng vững & Lưu ý thực thi*: Trong thuật toán Trie `PokemonMatcher`, việc duyệt từng chỉ số `i` trực tiếp trên chuỗi gốc `text` và chuẩn hóa từng ký tự bằng `Character.toLowerCase(text[i])` giữ nguyên hoàn toàn hệ quy chiếu chỉ số `0 until text.length` của chuỗi gốc. (Được ghi nhận vào khuyến nghị `[MINOR-2]` bên dưới).

3. **Kịch bản tấn công 3 — Chạm vào khoảng trắng bên phải đoạn văn (ngoài chữ cuối dòng) kích hoạt nhầm hộp thoại Pokémon**:
   - *Giả thuyết tấn công*: Khi tên Pokémon nằm ở cuối một dòng ngắn (ví dụ dòng kết thúc bằng `"Pikachu."` ở giữa màn hình, phần bên phải là khoảng trắng), API `TextLayoutResult.getOffsetForPosition(tapOffset)` của Compose sẽ trả về offset của ký tự cuối cùng trên dòng đó. Nếu người dùng chạm vào vùng trống bên phải để ẩn/hiện thanh công cụ (`onToggleBars()`), app có thể mở nhầm `PokemonLookupDialog`.
   - *Lý do thiết kế đứng vững*: Bước 6 của kế hoạch (`ReaderPokemonSupport.kt`) đã thiết kế hàm `findTappedPokemonMatch` kiểm tra chặt điều kiện biên ngang `tapOffset.x in layout.getLineLeft(line)..layout.getLineRight(line)` và biên dọc `tapOffset.y` trước khi tra `charOffset in match.start until match.end`. Nhờ vậy, mọi cú chạm vào khoảng trắng bên ngoài văn bản thực tế đều trả về `null` và chuyển sang `onToggleBars()` đúng như mong đợi.

4. **Kịch bản tấn công 4 — Tương tác với nút Back hệ thống khi đang mở `PokemonLookupDialog` hoặc `ReaderSettingsPanel`**:
   - *Giả thuyết tấn công*: Tại `[SRC]` `app/src/main/java/com/personal/apptruyen/ui/reader/ReaderScreen.kt:222-227`, `ReaderScreen` có `BackHandler(enabled = state.showSettings || state.showChapterList)`. Nếu người dùng mở `PokemonLookupDialog` rồi bấm nút Back hệ thống, liệu có bị thoát khỏi `ReaderScreen` về màn hình chi tiết truyện hay không?
   - *Lý do thiết kế đứng vững*: Composable `Dialog(onDismissRequest = ...)` của Compose UI tạo cửa sổ Dialog riêng có `OnBackPressedDispatcher` ưu tiên cao hơn Activity/Screen bên dưới; khi nhấn Back, hệ thống tự động gọi `onDismissRequest` (`ReaderIntent.DismissPokemonDialog`) để đóng hộp thoại mà không thoát khỏi `ReaderScreen`.

#### 6. Danh Sách Lỗi & Khuyến Nghị (`[Advisory Recommendations]`)

- **`BLOCKER`**: `0`
- **`MAJOR`**: `0`
- **`MINOR` (`[Advisory Recommendations]` — không chặn phê duyệt)**:
  1. **`[MINOR-1]` Đồng bộ hàm tách đoạn văn và guard biên offset trong `ReaderPokemonSupport.kt`**: Trong `ReaderViewModel.refreshPokemonMatches`, sử dụng trực tiếp `TtsManager.splitIntoParagraphs(content)` (giống hệt `ReaderScreen.kt:97`) để tách đoạn trước khi gọi `pokemonRepository.findMatchesInParagraphs(paragraphs)`. Đồng thời trong `ReaderParagraphItem`, thêm điều kiện lọc an toàn `match.start >= 0 && match.end <= paragraph.length && match.start < match.end` khi dựng `AnnotatedString` và vẽ gạch chân.
  2. **`[MINOR-2]` Chuẩn hóa ký tự theo từng vị trí `Character.toLowerCase(ch)` trong `PokemonMatcher`**: Khi xây dựng và duyệt Trie trong `PokemonLookupHelper.kt`, dùng `Character.toLowerCase(char)` theo từng index trên chuỗi gốc thay vì `.lowercase(Locale.ROOT)` trên toàn bộ đoạn văn để đảm bảo `start`/`end` không bao giờ bị dịch chuyển bởi các ký tự Unicode đổi độ dài khi hạ chữ thường.

**Kết luận**: APPROVED · BLOCKER 0 · MAJOR 0

[PLAN_APPROVED]
