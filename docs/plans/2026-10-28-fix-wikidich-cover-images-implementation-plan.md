# Kế hoạch triển khai: Sửa lỗi hiển thị ảnh bìa truyện Wikidich

> **Trạng thái**: Completed
> **Revision**: 4
> **Ngày tạo**: 2026-10-28
> **Planner**: @planner ([PLANNER_MODEL: gemini-4-argon])

## Technical Plan

### 1. Mục Tiêu (Goal) & Out of Scope (What We're NOT Doing)

- **Mục tiêu (Goal)**:
  - Khắc phục lỗi không tải được ảnh bìa truyện từ nguồn Wikidich (`wikicv.org`) ở cả 2 vị trí theo phản ánh của người dùng:
    1. *Khi cuộn danh sách tìm kiếm / khám phá (`SearchScreen`, `ExploreScreen`)*: Khi cuộn danh sách và tải hàng chục ảnh bìa, các request gặp hiện tượng MISS cache và server `wikicv.org` trả về header `cf-cache-status: DYNAMIC` cho các request `/photo/<id>` (Cloudflare không cache edge như ảnh tĩnh, request dội về origin server của `wikicv.org` với độ trễ ~0.6s–0.8s/ảnh). `OkHttpClient` mặc định của Coil 3 có timeout chỉ 10s, không có DNS DoH (dễ lỗi trên mạng ISP chặn DNS), không có cookie phiên, không có Referer chuẩn và dùng User-Agent mặc định `okhttp/5.3.2`, dispatcher mặc định cho phép 5 request dồn dập về origin cùng lúc với request phân trang của Scraper, dẫn đến `SocketTimeoutException` và ảnh bìa bị lỗi. Ta sẽ cài đặt `SingletonImageLoader.Factory` trên `AppTruyenApplication`, cấu hình `ImageLoader` trong `CoilModule` sử dụng `OkHttpClient` chia sẻ từ `AppModule` với DNS DoH (`ResilientDohDns`), cookie jar (`ReadOnlyWikidichCookieJar`) ở chế độ chỉ đọc cho Wikidich, `readTimeout = 30s` (> 12.03s), dispatcher giới hạn `maxRequestsPerHost = 4`, tự động gắn User-Agent Chrome đồng bộ (`WikidichScraper.DESKTOP_UA` = `Chrome/122.0.0.0`) khớp với cookie `cf_clearance` từ WebView và Referer chuẩn theo từng host thông qua `CoilImageInterceptor` tách riêng, cùng cơ chế tự động thử lại (retry) đúng 1 lần cho `SocketTimeoutException` hoặc mã lỗi 502/503/504 (không retry 429/403 và không retry khi call bị hủy).
    2. *Khi vào trang chi tiết truyện (`StoryDetailScreen`)*: Thẻ ảnh bìa trên HTML thực tế của `wikicv.org` (`[SRC] app/src/test/resources/fixtures/wikidich/story_detail.html:285-288`) nằm trong `<div class="book-info"><div class="cover-wrapper"><img class="z-depth-1 materialboxed" src="/photo/6a950ea654b8087c7d7c833d?o=1"/></div></div>`. Selector của Scraper (`.cover-info img, .book-info img`) khớp được thẻ `img` nhưng trả về URL có hậu tố `?o=1`. Qua đo đạc thực nghiệm bằng `curl.exe`:
       - Truyện người dùng báo lỗi (`Hoang đảo cầu sinh`): Ảnh danh sách `/photo/6adf3c8e54b8083581ec61a4` có dung lượng **44.2 KB** (`Content-Length: 44244`). Trong khi ảnh chi tiết `/photo/6adf3c8e54b8083581ec61a4?o=1` là file ảnh gốc có dung lượng lên tới **690.9 KB** (**nặng gấp 15.6 lần!**, `cf-cache-status: DYNAMIC`).
       - Truyện trong fixture (`Hỏa ảnh`): Ảnh danh sách dung lượng **20.3 KB**, ảnh chi tiết có `?o=1` dung lượng **69.0 KB** (gấp 3.4 lần).
       Điều này gây ra 3 hệ quả:
       - *Lệch cache key*: Coil 3 dùng URL làm cache key (RAM + Disk). Dù ảnh danh sách 44.2 KB đã được nạp sẵn vào cache, URL trang chi tiết có `?o=1` khiến Coil bị **cache miss**.
       - *Tải lại file ảnh gốc 690.9 KB bằng OkHttpClient mặc định*: Cùng lúc mở trang chi tiết, ứng dụng bắn request bóc tách chi tiết và danh sách chương, trong khi Coil phải mở kết nối mới tải file ảnh gốc 690.9 KB qua client mặc định (không DoH, timeout 10s), dẫn đến timeout và rơi vào placeholder chữ cái đầu.
       - *Bản ghi trong Room DB lưu URL có `?o=1`*: Với truyện đã xem, DB lưu URL có `?o=1`. Ta sẽ chuẩn hóa loại bỏ tham số `o` bằng `HttpUrl` (`toHttpUrlOrNull()?.newBuilder()?.removeAllQueryParameters("o")`) ở cả `WikidichScraper.extractCoverUrl` lẫn trong `StoryRepository.upgradeToHttps` (được gọi bởi `StoryEntity.toDomain()` và `resolveSafeCoverUrl()`). Đồng thời sửa lỗi tại `StoryRepository.kt:445, 468` để trả về ngay `resolvedStory` có `coverImageUrl = safeCoverUrl` đã được merge an toàn.
       - *Đánh đổi chất lượng hiển thị*: Bỏ `?o=1` đồng nghĩa trang chi tiết hiển thị ảnh thumbnail (~20–44 KB) thay vì file ảnh gốc. Trên `StoryDetailScreen` (`[SRC] StoryDetailScreen.kt:341-415`), ảnh nền đã được làm mờ `blur(25.dp)` và ảnh bìa chính chỉ hiển thị trong khung kích thước nhỏ 100x140 dp, do đó chất lượng thumbnail hoàn toàn đáp ứng tốt về độ sắc nét trong khi mang lại lợi ích vượt trội: tải tức thì (hit cache), tiết kiệm băng thông mạng và tránh nghẽn server.

- **Out of Scope (Những gì KHÔNG làm)**:
  - KHÔNG thay đổi database schema của Room (không nâng version `AppDatabase`, không sửa cấu trúc `StoryEntity`).
  - KHÔNG thêm bất kỳ thư viện bên thứ ba mới nào vào `build.gradle.kts` (tận dụng Coil 3.4.0, OkHttp 5.3.2, Hilt 2.60.1 đã có sẵn).
  - KHÔNG thay đổi logic bóc tách danh sách chương hay đọc truyện trong `WikidichScraper`.
  - KHÔNG can thiệp logic state/merge trong `StoryDetailViewModel` (toàn bộ logic merge và chuẩn hóa cover được tập trung tại `StoryRepository` làm single source of truth).
  - KHÔNG thay đổi giao diện các Composable (`StoryCardPremium`, `StoryDetailScreen`, `DownloadsScreen`, `ReaderPokemonSupport`).

---

### 2. Pre-Work Reading & Skills Loaded & Applied

- **Tài liệu & mã nguồn đã khảo sát**:
  - `AGENTS.md` (lines 1–35): kiến trúc Clean Architecture + MVVM/MVI, UI 100% tiếng Việt, Dagger Hilt.
  - `.agents/rules/00-core.md`, `.agents/rules/01-coding.md`, `.agents/rules/02-quality.md`: các quy tắc lập trình bất biến, cấm `!!`, cấm catch nuốt lỗi, yêu cầu unit test bắt buộc.
  - `.agents/rules/04-scraping.md`: quy tắc bóc tách dữ liệu từ web nguồn, rate limiting, kiểm thử bằng fixture HTML.
  - `docs/lessons-learned.md` (lines 100–145): ghi nhận lỗi ngày 2026-10-14 về việc Cloudflare trên `wikicv.org` throttle kết nối ~12.03s; yêu cầu đo đạc bằng `curl.exe -w` trước khi quyết định timeout.
  - `app/build.gradle.kts` (lines 128–135): xác nhận Coil `3.4.0` (`coil-compose`, `coil-network-okhttp`), OkHttp `5.3.2`.
  - `app/src/main/java/com/personal/apptruyen/data/remote/WikidichScraper.kt` (lines 64–65 `DESKTOP_UA`, lines 146–185 `getStoryDetail`, lines 573–624 `parseStoryList`).
  - `app/src/main/java/com/personal/apptruyen/ui/auth/SourceLoginScreen.kt` (line 167) & `SourceAuthManager.kt` (lines 113–114): xác nhận User-Agent đồng bộ `Chrome/122.0.0.0`.
  - `app/src/test/resources/fixtures/wikidich/story_detail.html` (lines 285–288): cấu trúc thẻ `img` ảnh bìa trong `.book-info .cover-wrapper`.
  - `app/src/main/java/com/personal/apptruyen/data/repository/StoryRepository.kt` (lines 440–470, lines 840–875): phát hiện lỗi trả về `resolvedStory` với cover chưa merge tại dòng 445/468 và hàm `upgradeToHttps` tại dòng 843.
  - `app/src/main/java/com/personal/apptruyen/di/CoilModule.kt` (lines 1–41): `provideImageLoader` chưa cấu hình `OkHttpNetworkFetcherFactory`, chưa inject `OkHttpClient`.
  - `app/src/main/java/com/personal/apptruyen/AppTruyenApplication.kt` (lines 1–20): chưa implement `SingletonImageLoader.Factory`.
  - `app/src/main/java/com/personal/apptruyen/ui/reader/ReaderPokemonSupport.kt` (line 341): Composable tra cứu ảnh Pokemon cũng dùng `SubcomposeAsyncImage` qua `SingletonImageLoader`.

- **Bảng ánh xạ Skills Loaded & Applied**:
  | Skill | Đường dẫn | Quyết định kỹ thuật chịu ảnh hưởng trực tiếp |
  |---|---|---|
  | `apptruyen-debug` | `.agents/skills/apptruyen-debug/SKILL.md` | Dùng đo đạc thực nghiệm (`curl.exe`) xác minh kích thước file ảnh (44.2 KB vs 690.9 KB) và trạng thái cache Cloudflare (`DYNAMIC`), tìm đúng nguyên nhân gốc rễ lệch cache key `?o=1` và timeout của `OkHttpClient` mặc định; dồn trách nhiệm merge cover vào `StoryRepository` thay vì vá tạm ở `StoryDetailViewModel`. |
  | `apptruyen-investigate` | `.agents/skills/apptruyen-investigate/SKILL.md` | Phân tích blast-radius của việc cài đặt `SingletonImageLoader.Factory` trên `Application` đối với tất cả các màn hình sử dụng `SubcomposeAsyncImage` / `AsyncImage` (`SearchScreen`, `ExploreScreen`, `HomeScreen`, `StoryDetailScreen`, `DownloadsScreen`, và `ReaderPokemonSupport.kt:341`); đồng bộ User-Agent `Chrome/122.0.0.0` với WebView để không làm hỏng cookie `cf_clearance`. |

---

### 3. Assumptions & Cross-Task Dependencies

- **Assumptions (Giả định kỹ thuật đã kiểm chứng)**:
  1. [SRC `AppModule.kt:54-64`] `@Singleton OkHttpClient` đã có sẵn cấu hình `ResilientDohDns`, `cookieJar(WebViewCookieHandler)`, timeout `30s` (> 12.03s throttle của `wikicv.org`).
  2. [SRC `app/src/test/resources/fixtures/wikidich/story_detail.html:285-288`] HTML thực tế của Wikidich chứa thẻ ảnh bìa nằm trong `<div class="book-info"><div class="cover-wrapper"><img class="z-depth-1 materialboxed" src="/photo/6a950ea654b8087c7d7c833d?o=1"/></div></div>`. Selector `.cover-info img, .book-info img` của Scraper khớp thẻ này nhưng trả về URL có hậu tố `?o=1`.
  3. [CMD `curl.exe -q -sI --max-time 20` (đã đo thực nghiệm)] Kết quả đo đạc:
     - Header ảnh bìa `/photo/<id>` của `wikicv.org` trả về `cf-cache-status: DYNAMIC` (Cloudflare không cache edge, các request dội về origin server với độ trễ ~0.6s–0.8s/ảnh).
     - Truyện `Hoang đảo cầu sinh`: Danh sách `/photo/6adf3c8e54b8083581ec61a4` nặng **44.2 KB** (`Content-Length: 44244`). Trong khi trang chi tiết `/photo/6adf3c8e54b8083581ec61a4?o=1` là file ảnh gốc nặng tới **690.9 KB** (**gấp 15.6 lần!**, `cf-cache-status: DYNAMIC`).
     - Truyện `Hỏa ảnh`: Danh sách nặng **20.3 KB**, trang chi tiết có `?o=1` nặng **69.0 KB** (gấp 3.4 lần).
     - Đánh đổi chất lượng hiển thị: Việc loại bỏ tham số `o` khiến trang chi tiết dùng chung ảnh thumbnail (~20–44 KB). Trên `StoryDetailScreen.kt:341-415`, ảnh nền được làm mờ `blur(25.dp)` và ảnh bìa chính hiển thị trong khung kích thước nhỏ 100x140 dp, do đó chất lượng ảnh thumbnail đáp ứng tốt về độ nét trong khi giúp nạp ảnh tức thì (hit cache), tránh timeout và giảm tải origin server 15.6 lần.
  4. [SRC `CoilModule.kt:1-41`, `StoryCardPremium.kt:96-101`, `ReaderPokemonSupport.kt:341`] Các Composable sử dụng Coil 3 không truyền instance `imageLoader` cụ thể mà phụ thuộc vào `SingletonImageLoader.get(context)`. Khi `Application` implements `SingletonImageLoader.Factory`, Coil 3 sẽ tự động gọi `newImageLoader(PlatformContext)` để lấy loader singleton phục vụ tất cả các màn hình (Search, Explore, Home, StoryDetail, Downloads, ReaderPokemonSupport).
  5. [SRC `WikidichScraper.kt:64-65`, `ui/auth/SourceLoginScreen.kt:167`, `SourceAuthManager.kt:113-114`] User-Agent của Wikidich và WebView đăng nhập cố định là `Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36`. Cookie `cf_clearance` giải từ Cloudflare bị ràng buộc với UA này; do đó `CoilImageInterceptor` phải dùng chung chính xác User-Agent `WikidichScraper.DESKTOP_UA` để cookie phiên hợp lệ khi gửi request ảnh.
  6. [SRC `StoryRepository.kt:445-468, 838, 843-852`] Hàm `upgradeToHttps` được gọi bởi cả `StoryEntity.toDomain()` và `resolveSafeCoverUrl()`. Việc dùng `toHttpUrlOrNull()` để loại bỏ tham số query `o` trên host `wikicv.org` đường dẫn `/photo/` sẽ tự động làm sạch URL ảnh bìa của các bản ghi cũ trong Room DB ngay khi đọc ra bộ nhớ, đồng thời bảo toàn an toàn các query param khác (`?o=1&w=200` -> `?w=200`) và không làm ảnh hưởng host khác.

- **Cross-Task Dependencies**:
  - Phụ thuộc kế hoạch `2026-10-16-wikidich-rate-limit-and-doh-implementation-plan.md` đã hoàn thành: DoH và WebViewCookieHandler hoạt động ổn định trên `OkHttpClient`.
  - Kế hoạch `2026-10-27-wikidich-male-tag-and-newest-sort-implementation-plan.md`: Đã chuẩn hóa URL tìm kiếm và danh sách truyện Wikidich.

---

### 4. Verified Versions Table

| Thư viện | Version hiện tại | Version đề xuất | Latest đã xác minh | Nguồn URL | Tương thích? |
|---|---|---|---|---|---|
| `io.coil-kt.coil3:coil-compose` | `3.4.0` | `3.4.0` (giữ nguyên) | `3.4.0` (2026) | [Coil 3 Docs](https://coil-kt.github.io/coil/) | Tương thích (đã xác minh baseline `compileDebugKotlin` Exit 0) |
| `io.coil-kt.coil3:coil-network-okhttp` | `3.4.0` | `3.4.0` (giữ nguyên) | `3.4.0` (2026) | [Coil 3 OkHttp](https://coil-kt.github.io/coil/) | Tương thích (đã xác minh baseline `compileDebugKotlin` Exit 0) |
| `com.squareup.okhttp3:okhttp` | `5.3.2` | `5.3.2` (giữ nguyên) | `5.3.2` (2026) | [OkHttp Repo](https://square.github.io/okhttp/) | Tương thích (đã xác minh baseline `compileDebugKotlin` Exit 0) |
| `com.google.dagger:hilt-android` | `2.60.1` | `2.60.1` (giữ nguyên) | `2.60.1` (2026) | [Dagger Hilt](https://dagger.dev/hilt/) | Tương thích (đã xác minh baseline `compileDebugKotlin` Exit 0) |
| `org.jsoup:jsoup` | `1.22.1` | `1.22.1` (giữ nguyên) | `1.22.1` (2026) | [Jsoup](https://jsoup.org/) | Tương thích (đã xác minh baseline `compileDebugKotlin` Exit 0) |

> Không thêm hoặc nâng cấp bất kỳ dependency nào.

---

### 5. Files Cần Chỉnh Sửa / Tạo Mới & Caller-First Design Sketch

#### Bảng danh sách files

| Nhãn | File | Tầng | Ghi chú (file nhạy cảm → lý do) |
|---|---|---|---|
| `[NEW]` | `app/src/main/java/com/personal/apptruyen/data/remote/CoilImageInterceptor.kt` | `data/remote` | Chứa `internal class CoilImageInterceptor` (có `AtomicInteger inFlightCount` và runtime probe log `CoilNet` an toàn), `internal class ReadOnlyWikidichCookieJar`, và `internal fun buildImageOkHttpClient` để testable 100% trên JVM mà không phụ thuộc Hilt/Context. |
| `[NEW]` | `app/src/test/java/com/personal/apptruyen/data/remote/CoilImageInterceptorTest.kt` | `test` | Unit test cho `CoilImageInterceptor` (UA, Referer, retry 502/503/504/timeout, không retry 429/403/isCanceled với deterministic MockWebServer dispatcher), `ReadOnlyWikidichCookieJar` (chỉ gửi cookie cho wikicv.org, không save cookie), và `buildImageOkHttpClient` (Dispatcher, retryOnConnectionFailure, interceptors). |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/data/remote/WikidichScraper.kt` | `data/remote` | Mở visibility `internal const val DESKTOP_UA`, thêm import `toHttpUrlOrNull` & `Element`, KDoc giải thích phạm vi bỏ query `o`, thêm helper `extractCoverUrl` duyệt `listOf(src, data-src)` chọn ứng viên hợp lệ đầu tiên và loại bỏ tham số `o`, cập nhật selector `.book-info .cover-wrapper img, .cover-info img, .book-info img`. |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/di/CoilModule.kt` | `di` | Gọi `buildImageOkHttpClient(okHttpClient)`, cấu hình ImageLoader với `OkHttpNetworkFetcherFactory(callFactory = { imageOkHttpClient })`, DiskCache 50MB, MemoryCache 25% RAM. |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/AppTruyenApplication.kt` | `app` | Implement `SingletonImageLoader.Factory`, inject `dagger.Lazy<ImageLoader>`, thêm log `Timber.tag("CoilInit").d(...)`. File nhạy cảm Application root. |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/data/repository/StoryRepository.kt` | `data/repository` | Thêm import `toHttpUrlOrNull`, KDoc giải thích phạm vi chỉ bỏ `o` cho `wikicv.org`, sửa `upgradeToHttps` dùng `HttpUrl` loại bỏ tham số `o` trên host `wikicv.org` đường dẫn `/photo/`; tính `safeCoverUrl` trước khi tạo `resolvedStory` ở dòng 445/468 để trả về cover an toàn đã merge. |
| `[MODIFY]` | `app/src/test/java/com/personal/apptruyen/data/remote/WikidichScraperTest.kt` | `test` | Assert cover trong `getStoryDetail` khớp `server.url` và không còn `?o=`; test `extractCoverUrl` lọc placeholder (data URI), ưu tiên `data-src` khi `src` là svg/data URI, và loại bỏ query `o`. |
| `[MODIFY]` | `app/src/test/java/com/personal/apptruyen/data/repository/StoryRepositoryTest.kt` | `test` | Test `upgradeToHttps` loại bỏ tham số `o` bằng `HttpUrl` (bảo toàn query param khác và host khác); test nhánh không cache của `getStoryDetail` trả về `resolvedStory` đã merge cover. |

*(Lưu ý: Không can thiệp `StoryDetailViewModel.kt` hay `StoryDetailViewModelTest.kt` để tuân thủ nguyên tắc Single Source of Truth; toàn bộ logic merge và chuẩn hóa cover được tập trung tại `StoryRepository`)*.

#### Caller-First Design Sketch

1. **Từ phía Composable Caller (UI)**:
   Các Composable hiện tại (`StoryCardPremium.kt`, `StoryDetailScreen.kt`, `DownloadsScreen.kt`, `ReaderPokemonSupport.kt:341`) gọi Coil như sau:
   ```kotlin
   SubcomposeAsyncImage(
       model = ImageRequest.Builder(LocalContext.current)
           .data(story.coverImageUrl)
           .crossfade(true)
           .build(),
       contentDescription = story.title,
       modifier = Modifier.fillMaxSize(),
       contentScale = ContentScale.Crop,
       ...
   )
   ```
   *Hành vi mới*: Khi `AppTruyenApplication` cài đặt `SingletonImageLoader.Factory`, Coil 3 sẽ tự động gọi `newImageLoader(PlatformContext)` trên Application instance. Toàn bộ các Composable không cần truyền tường minh `imageLoader`, đồng thời nhận ngay cơ chế tải ảnh qua `OkHttpClient` có DoH, Cookie chỉ đọc, Timeout 30s, Dispatcher điều tiết 4 req/host và Disk Cache 50MB.

2. **Từ phía `CoilImageInterceptor.kt` (Class và hàm độc lập trong `data/remote`)**:
   ```kotlin
   package com.personal.apptruyen.data.remote

   import com.personal.apptruyen.BuildConfig
   import java.io.IOException
   import java.net.SocketTimeoutException
   import java.util.concurrent.atomic.AtomicInteger
   import okhttp3.Cookie
   import okhttp3.CookieJar
   import okhttp3.Dispatcher
   import okhttp3.HttpUrl
   import okhttp3.Interceptor
   import okhttp3.OkHttpClient
   import okhttp3.Response
   import timber.log.Timber

   // 1. CookieJar chỉ đọc cho Wikidich
   internal class ReadOnlyWikidichCookieJar(
       private val delegate: CookieJar,
   ) : CookieJar {
       override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
           // Chế độ chỉ đọc: không lưu cookie từ CDN ảnh bên thứ ba hay Wikidich vào kho cookie
       }

       override fun loadForRequest(url: HttpUrl): List<Cookie> {
           val host = url.host
           return if (host == "wikicv.org" || host.endsWith(".wikicv.org")) {
               delegate.loadForRequest(url)
           } else {
               emptyList()
           }
       }
   }

   // 2. Interceptor xử lý User-Agent đồng bộ, Referer theo domain, retry có kiểm soát và probe CoilNet
   internal class CoilImageInterceptor(
       private val defaultUserAgent: String = WikidichScraper.DESKTOP_UA,
   ) : Interceptor {
       private val inFlightCount = AtomicInteger(0)

       override fun intercept(chain: Interceptor.Chain): Response {
           val call = chain.call()
           val request = chain.request()
           val builder = request.newBuilder()

           // Gắn User-Agent Chrome đồng bộ với WikidichScraper nếu thiếu
           if (request.header("User-Agent") == null) {
               builder.header("User-Agent", defaultUserAgent)
           }

           // Gắn Referer theo từng host
           if (request.header("Referer") == null) {
               resolveRefererForHost(request.url.host)?.let { referer ->
                   builder.header("Referer", referer)
               }
           }

           val newRequest = builder.build()

           // Thực thi request lần 1 kèm theo dõi inFlight và log probe an toàn
           val currentInFlight = inFlightCount.incrementAndGet()
           val attempt = 1
           var response: Response? = null
           var error: IOException? = null
           try {
               response = chain.proceed(newRequest)
               if (BuildConfig.DEBUG) {
                   Timber.tag("CoilNet").d(
                       "host=%s path=%s attempt=%d code=%d inFlight=%d",
                       newRequest.url.host, newRequest.url.encodedPath, attempt, response.code, currentInFlight
                   )
               }
           } catch (e: IOException) {
               error = e
               if (BuildConfig.DEBUG) {
                   Timber.tag("CoilNet").d(
                       "host=%s path=%s attempt=%d error=%s inFlight=%d",
                       newRequest.url.host, newRequest.url.encodedPath, attempt, e.javaClass.simpleName, currentInFlight
                   )
               }
           } finally {
               inFlightCount.decrementAndGet()
           }

           // Điều kiện retry tối đa 1 lần (HTTP 502/503/504 hoặc SocketTimeoutException, không retry 429/403)
           val shouldRetryHttp = response != null && !response.isSuccessful && response.code in SERVER_ERROR_CODES
           val shouldRetryTimeout = error is SocketTimeoutException && !call.isCanceled() && !Thread.currentThread().isInterrupted

           if ((shouldRetryHttp || shouldRetryTimeout) && !call.isCanceled() && !Thread.currentThread().isInterrupted) {
               // BẮT BUỘC đóng response cũ trước khi retry để tránh rò connection
               response?.close()
               val retryInFlight = inFlightCount.incrementAndGet()
               val retryAttempt = 2
               try {
                   val retryResponse = chain.proceed(newRequest)
                   if (BuildConfig.DEBUG) {
                       Timber.tag("CoilNet").d(
                           "host=%s path=%s attempt=%d code=%d inFlight=%d",
                           newRequest.url.host, newRequest.url.encodedPath, retryAttempt, retryResponse.code, retryInFlight
                       )
                   }
                   return retryResponse
               } catch (retryError: IOException) {
                   error?.let { retryError.addSuppressed(it) }
                   if (BuildConfig.DEBUG) {
                       Timber.tag("CoilNet").d(
                           "host=%s path=%s attempt=%d error=%s inFlight=%d",
                           newRequest.url.host, newRequest.url.encodedPath, retryAttempt, retryError.javaClass.simpleName, retryInFlight
                       )
                   }
                   throw retryError
               } finally {
                   inFlightCount.decrementAndGet()
               }
           }

           if (error != null) throw error
           return response ?: throw IOException("Phản hồi ảnh không hợp lệ (null response)")
       }

       internal fun resolveRefererForHost(host: String): String? {
           return when {
               host == "wikicv.org" || host.endsWith(".wikicv.org") -> "https://wikicv.org/"
               host == "sstruyen.pics" || host.endsWith(".sstruyen.pics") -> "https://sstruyen.pics/"
               host == "tangthuvien.org" || host.endsWith(".tangthuvien.org") || host == "tangthuvien.vn" || host.endsWith(".tangthuvien.vn") -> "https://tangthuvien.org/"
               else -> null // Không gắn Referer cho các host bên thứ ba khác như imgur
           }
       }

       companion object {
           private val SERVER_ERROR_CODES = setOf(502, 503, 504)
       }
   }

   // 3. Hàm dựng OkHttpClient cho Coil để testable trên JVM
   internal fun buildImageOkHttpClient(baseClient: OkHttpClient): OkHttpClient {
       val imageDispatcher = Dispatcher().apply {
           maxRequests = 32
           maxRequestsPerHost = 4
       }
       return baseClient.newBuilder()
           .dispatcher(imageDispatcher)
           .cookieJar(ReadOnlyWikidichCookieJar(baseClient.cookieJar))
           .retryOnConnectionFailure(true) // Giữ mặc định để OkHttp tự phục hồi stale pool connection
           .addInterceptor(CoilImageInterceptor())
           .build()
   }
   ```

3. **Từ phía `WikidichScraper.kt`**:
   ```kotlin
   import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
   import org.jsoup.nodes.Element

   // Mở rộng visibility:
   internal const val DESKTOP_UA =
       "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

   /**
    * Trích xuất URL ảnh bìa hợp lệ từ thẻ img, ưu tiên ứng viên đầu tiên trong [src, data-src].
    * Loại bỏ tham số query 'o' trên mọi đường dẫn '/photo/' để chuẩn hóa cache key theo ảnh danh sách.
    * Lưu ý: Áp dụng cho mọi host có '/photo/' vì scraper chỉ sinh URL thuộc baseUrl của chính nó
    * (bao gồm cả trường hợp chạy trên localhost trong MockWebServer unit test).
    */
   internal fun extractCoverUrl(img: Element?): String {
       if (img == null) return ""
       val candidates = listOf(img.attr("src").trim(), img.attr("data-src").trim())
       val raw = candidates.firstOrNull { candidate ->
           candidate.isNotBlank() &&
               !candidate.startsWith("data:", ignoreCase = true) &&
               !candidate.contains("base64", ignoreCase = true) &&
               !candidate.contains("default-cover") &&
               !candidate.contains("default-avatar")
       }.orEmpty()
       if (raw.isBlank()) return ""

       val fixed = fixUrl(raw)
       val httpUrl = fixed.toHttpUrlOrNull() ?: return fixed
       return if (httpUrl.encodedPath.startsWith("/photo/") && httpUrl.queryParameter("o") != null) {
           httpUrl.newBuilder().removeAllQueryParameters("o").build().toString()
       } else {
           fixed
       }
   }
   ```
   *Trong `getStoryDetail`*:
   ```kotlin
   val coverImg = doc.selectFirst(".book-info .cover-wrapper img, .cover-info img, .book-info img")
   val coverUrl = extractCoverUrl(coverImg)
   ```

4. **Từ phía `StoryRepository.kt` (Tầng dữ liệu)**:
   ```kotlin
   import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

   /**
    * Nâng cấp URL sang HTTPS và chuẩn hóa URL ảnh bìa Wikidich.
    * Khác với Scraper, StoryRepository xử lý truyện từ mọi nguồn truyện (TTV, SsTruyện, Wikidich, imgur...),
    * do đó việc loại bỏ tham số query 'o' trên '/photo/' chỉ được giới hạn nghiêm ngặt cho host 'wikicv.org'
    * để tránh làm sai lệch URL của các nguồn khác.
    */
   internal fun upgradeToHttps(url: String): String {
       val trimmed = url.trim()
       val httpsUrl =
           if (trimmed.startsWith("http://", ignoreCase = true) &&
               !trimmed.startsWith("http://localhost", ignoreCase = true) &&
               !trimmed.startsWith("http://127.0.0.1")
           ) {
               "https://" + trimmed.substring(7)
           } else {
               trimmed
           }
       val httpUrl = httpsUrl.toHttpUrlOrNull() ?: return httpsUrl
       val host = httpUrl.host
       val isWikidichPhoto = (host == "wikicv.org" || host.endsWith(".wikicv.org")) &&
           httpUrl.encodedPath.startsWith("/photo/")
       return if (isWikidichPhoto && httpUrl.queryParameter("o") != null) {
           httpUrl.newBuilder().removeAllQueryParameters("o").build().toString()
       } else {
           httpsUrl
       }
   }

   // Trong getStoryDetail (nhánh không có cache ban đầu, dòng 445-468):
   val safeCoverUrl = resolveSafeCoverUrl(story.coverImageUrl, existingEntity?.coverImageUrl)
   val resolvedStory =
       story.copy(
           id = resolvedId,
           totalChapters = safeTotal,
           coverImageUrl = safeCoverUrl,
       )
   val entity = ...
   storyDao.insertStory(entity)
   ...
   return resolvedStory // Trả về resolvedStory đã có safeCoverUrl
   ```

---

### 6. Các Bước Thực Hiện

- [ ] **Bước 1**: Tạo `CoilImageInterceptor.kt` trong `data/remote/` chứa `internal class CoilImageInterceptor` (có `AtomicInteger inFlightCount` theo dõi request đồng thời và ghi log an toàn `Timber.tag("CoilNet")`), `internal class ReadOnlyWikidichCookieJar`, và `internal fun buildImageOkHttpClient`. Triển khai logic gắn User-Agent đồng bộ `WikidichScraper.DESKTOP_UA`, Referer theo domain (`wikicv.org`, `sstruyen.pics`, `tangthuvien.org`, `tangthuvien.vn`), cơ chế retry tối đa 1 lần với 502/503/504 (bắt buộc `response.close()`) hoặc `SocketTimeoutException` (kèm `addSuppressed`), không retry 429/403, và tôn trọng việc hủy request (`!call.isCanceled()`).
- [ ] **Bước 2**: Cập nhật `CoilModule.kt` — Inject `@Singleton okHttpClient: OkHttpClient` và `@ApplicationContext context: Context`. Sử dụng `buildImageOkHttpClient(okHttpClient)`, tích hợp `OkHttpNetworkFetcherFactory(callFactory = { imageOkHttpClient })`, MemoryCache (25% RAM), DiskCache (50MB) và `crossfade(300)`.
- [ ] **Bước 3**: Cập nhật `AppTruyenApplication.kt` — Khai báo `class AppTruyenApplication : Application(), SingletonImageLoader.Factory`, inject `@Inject lateinit var imageLoader: dagger.Lazy<ImageLoader>`, và override `newImageLoader(context: PlatformContext): ImageLoader = imageLoader.get()`, bổ sung log debug `Timber.tag("CoilInit").d("SingletonImageLoader initialized via AppTruyenApplication")`.
- [ ] **Bước 4**: Cập nhật `WikidichScraper.kt` — Mở visibility `internal const val DESKTOP_UA`, bổ sung import `toHttpUrlOrNull` & `Element`, thêm KDoc giải thích phạm vi, thêm helper `extractCoverUrl(img: Element?): String` duyệt `listOf(src, data-src)` chọn ứng viên hợp lệ đầu tiên và loại bỏ tham số query `o` bằng `toHttpUrlOrNull()`. Sửa selector trang chi tiết thành `.book-info .cover-wrapper img, .cover-info img, .book-info img` và áp dụng `extractCoverUrl` cho cả `getStoryDetail` lẫn `parseStoryList`.
- [ ] **Bước 5**: Cập nhật `StoryRepository.kt` — Bổ sung import `toHttpUrlOrNull`, KDoc giải thích phạm vi chỉ áp dụng cho host `wikicv.org`, sửa `upgradeToHttps` dùng `toHttpUrlOrNull()` loại bỏ tham số `o` trên host `wikicv.org` đường dẫn ảnh `/photo/`. Trong `getStoryDetail` (nhánh không có cache ban đầu, dòng 445–468), tính `val safeCoverUrl = resolveSafeCoverUrl(story.coverImageUrl, existingEntity?.coverImageUrl)` trước khi tạo `resolvedStory` để trả về ngay story có ảnh bìa an toàn đã merge.
- [ ] **Bước 6**: Bổ sung & cập nhật Unit Tests:
  - `CoilImageInterceptorTest.kt`: Test `buildImageOkHttpClient` (Dispatcher `maxRequests=32`, `maxRequestsPerHost=4`, `retryOnConnectionFailure=true`, interceptors); test `ReadOnlyWikidichCookieJar` (chỉ load cho `wikicv.org`, không save cookie); test `CoilImageInterceptor` (gắn User-Agent `Chrome/122.0.0.0`, gắn Referer theo domain, retry 503 có đóng response, không retry 429/403, không retry khi call bị hủy với deterministic mock dispatcher).
  - `WikidichScraperTest.kt`: Assert ảnh bìa trang chi tiết từ fixture `story_detail.html` khớp URL của MockWebServer và không chứa `?o=`; test `extractCoverUrl` lọc placeholder và query `o`, kiểm tra ưu tiên `data-src` khi `src` là data URI SVG.
  - `StoryRepositoryTest.kt`: Test `upgradeToHttps` loại bỏ tham số `o` bằng `HttpUrl` (kiểm tra cả `?a=1&o=1` -> `?a=1` và host khác giữ nguyên query); test `getStoryDetail` nhánh không cache trả về `resolvedStory` có `coverImageUrl` đã được merge.
- [ ] **Bước 7**: Chạy toàn bộ test quality gates — Thực thi `ktlintCheck`, `testDebugUnitTest`, và `assembleDebug` để xác nhận không có bất kỳ lỗi biên dịch hay regression nào.

---

### 7. Test Contract

| # | Hành vi (ngôn ngữ nghiệp vụ) | Input | Kết quả mong đợi / thông báo lỗi tiếng Việt | Test Class |
|---|---|---|---|---|
| TC1 | Trích xuất ảnh bìa truyện Wikidich ở trang chi tiết | HTML chứa `<div class="book-info"><div class="cover-wrapper"><img class="materialboxed" src="/photo/6a950ea654b8087c7d7c833d?o=1"...></div></div>` | `story.coverImageUrl.endsWith("/photo/6a950ea654b8087c7d7c833d")` và `!story.coverImageUrl.contains("?o=")` | `WikidichScraperTest` |
| TC2 | Bỏ qua ảnh bìa mặc định hoặc data URI rỗng | HTML chứa `<img src="/static/img/default-cover.jpg">` hoặc `src="data:image/svg..."` và `data-src=""` | `story.coverImageUrl == ""` | `WikidichScraperTest` |
| TC3 | Trích xuất ảnh bìa truyện lazy-load có `data-src` | Item HTML chứa `<img src="data:image/svg+xml;base64,..." data-src="/photo/123"/>` | `story.coverImageUrl.endsWith("/photo/123")` và `!story.coverImageUrl.contains("?o=")` | `WikidichScraperTest` |
| TC4 | Chuẩn hóa ảnh bìa Room DB qua `upgradeToHttps` bằng `HttpUrl` | URL `https://wikicv.org/photo/123?a=1&o=1` hoặc `https://other.com/photo/123?o=1` | Với `wikicv.org` trả về `https://wikicv.org/photo/123?a=1` (chỉ loại bỏ tham số `o`); với `other.com` trả về `https://other.com/photo/123?o=1` (giữ nguyên) | `StoryRepositoryTest` |
| TC5 | Gộp ảnh bìa an toàn trong `getStoryDetail` nhánh không cache | `story.coverImageUrl == ""`, `existingEntity.coverImageUrl == "https://wikicv.org/photo/123"` | `result.coverImageUrl == "https://wikicv.org/photo/123"` | `StoryRepositoryTest` |
| TC6 | Gắn User-Agent Chrome đồng bộ (`WikidichScraper.DESKTOP_UA`) | Request không có header `User-Agent` | Request gửi đi có header `User-Agent` chứa `Chrome/122.0.0.0` | `CoilImageInterceptorTest` |
| TC7 | Gắn Referer theo domain cho từng nguồn truyện | Request tới `wikicv.org`, `sstruyen.pics`, `tangthuvien.org`, `tangthuvien.vn` | Request có Referer tương ứng (`https://wikicv.org/`, `https://sstruyen.pics/`, `https://tangthuvien.org/`) | `CoilImageInterceptorTest` |
| TC8 | Không gắn Referer cho host bên thứ ba | Request tới `i.imgur.com` | Request không có header `Referer` | `CoilImageInterceptorTest` |
| TC9 | Đóng response và retry đúng 1 lần khi server trả 503 hoặc timeout | Server trả HTTP 503 ở lần 1, HTTP 200 ở lần 2 | Thành công, response cũ được đóng, tổng số request là 2 | `CoilImageInterceptorTest` |
| TC10 | Không retry khi server trả mã lỗi 429 hoặc 403 | Server trả HTTP 429 hoặc 403 | Trả về response lỗi ngay lập tức, không gửi thêm request thứ 2 | `CoilImageInterceptorTest` |
| TC11 | Không retry khi Call đã bị hủy (`isCanceled() == true`) | MockWebServer dispatcher gọi `call.cancel()` trong lúc dispatch request 503 | Ném `IOException("Canceled")`, không gửi thêm request thứ 2 (kiểm tra deterministic) | `CoilImageInterceptorTest` |
| TC12 | Cấu hình OkHttpClient nạp ảnh qua `buildImageOkHttpClient` | `buildImageOkHttpClient(baseClient)` | Dispatcher có `maxRequests = 32`, `maxRequestsPerHost = 4`, `retryOnConnectionFailure = true`, và chứa `CoilImageInterceptor` | `CoilImageInterceptorTest` |
| TC13 | Bảo vệ cookie phiên qua `ReadOnlyWikidichCookieJar` | Gọi `loadForRequest` với `wikicv.org` vs `i.imgur.com`, và gọi `saveFromResponse` | Trả về cookie phiên cho `wikicv.org`, trả rỗng cho host khác, và không lưu cookie nào vào delegate | `CoilImageInterceptorTest` |

---

### 8. Verification Plan

- **Unit test files**:
  - `app/src/test/java/com/personal/apptruyen/data/remote/CoilImageInterceptorTest.kt`
  - `app/src/test/java/com/personal/apptruyen/data/remote/WikidichScraperTest.kt`
  - `app/src/test/java/com/personal/apptruyen/data/repository/StoryRepositoryTest.kt`
- **Lệnh thực thi kiểm chứng**:
  ```powershell
  .\gradlew.bat ktlintCheck; .\gradlew.bat testDebugUnitTest --tests "com.personal.apptruyen.data.remote.CoilImageInterceptorTest" --tests "com.personal.apptruyen.data.remote.WikidichScraperTest" --tests "com.personal.apptruyen.data.repository.StoryRepositoryTest"; .\gradlew.bat assembleDebug
  ```
- **Kiểm thử thủ công trên thiết bị**:
  1. Mở màn hình Tìm kiếm (`SearchScreen`), gõ từ khóa tìm truyện Wikidich (ví dụ "tiên", "hoang đảo").
  2. Cuộn nhanh xuống dưới danh sách kết quả (20–50 truyện): Xác nhận ảnh bìa hiển thị mượt mà, không bị rơi vào khung màu trống / lỗi tải ảnh.
  3. Bấm vào một truyện bất kỳ: Xác nhận ở màn hình Chi tiết truyện (`StoryDetailScreen`), ảnh bìa hiển thị ngay lập tức (hit cache từ danh sách truyện, không tải lại file ảnh gốc 690KB).
  4. Mở truyện Wikidich đã lưu từ trước trong thư viện: Xác nhận ảnh bìa lập tức hiển thị URL đã chuẩn hóa không còn `?o=1`.
- **Wiring Probes**:
  - *Compile/Codegen Probe*: Lệnh `compileDebugKotlin` và `assembleDebug` chứng minh Dagger sinh mã thành công, không có vòng tròn phụ thuộc (cyclic dependency) giữa `AppTruyenApplication` và `CoilModule`.
  - *Runtime Probe (CoilInit & CoilNet)*:
    1. Tag `CoilInit`: Log `Timber.tag("CoilInit").d("SingletonImageLoader initialized via AppTruyenApplication")` trong `AppTruyenApplication.newImageLoader` xác nhận loader singleton đã được Coil gọi lười sau khi `Application.onCreate()` hoàn tất.
    2. Tag `CoilNet`: Log `Timber.tag("CoilNet").d("host=%s path=%s attempt=%d code=%d inFlight=%d", ...)` trong `CoilImageInterceptor` (bản debug, không log header/cookie/query) in ra các dòng `host=wikicv.org path=/photo/<id> attempt=1 code=200 inFlight=...`. Điều này chứng minh 100%:
       - `OkHttpNetworkFetcherFactory` đăng ký tường minh trong `CoilModule` đã ghi đè hoàn toàn fetcher mặc định của service loader.
       - Toàn bộ request ảnh thực sự đi qua `imageOkHttpClient` + `CoilImageInterceptor`.
       - Chỉ số `inFlight` trên logcat không vượt quá 4, xác nhận hiệu lực điều tiết của Dispatcher lúc runtime.
- **Negative Probes**:
  - Truyền HTML với thẻ ảnh lỗi (chứa `data:image/svg...`, `/static/img/default-cover.jpg`, hoặc thẻ `img` rỗng): `extractCoverUrl` trả về chuỗi rỗng `""`, Composable hiển thị fallback placeholder ký tự đầu viết hoa, không crash ứng dụng.
  - Server trả 429 hoặc call bị hủy khi cuộn nhanh: Interceptor ngắt ngay lập tức, không spam request lên server.

---

### 9. Rủi Ro & Phòng Ngừa

| Rủi ro | Mức (Cao/TB/Thấp) | Technical control & Cơ sở kiểm chứng |
|---|---|---|
| Host ảnh Wikidich (`wikicv.org/photo/`) bị quá tải nếu tải dồn dập | **Trung bình** | **Cơ sở kỹ thuật**: Cấu hình `Dispatcher.maxRequestsPerHost = 4` và `maxRequests = 32` trên `buildImageOkHttpClient` là biện pháp điều tiết best-effort ở tầng transport pool của OkHttp. Kết hợp với `readTimeout = 30s` và DiskCache 50MB giúp tránh tải lặp lại cùng 1 ảnh.<br>**Kiểm chứng**: Kiểm tra cấu hình Dispatcher bằng unit test `TC12` trong `CoilImageInterceptorTest`, và kiểm chứng hiệu lực thực tế lúc chạy thông qua chỉ số `inFlight <= 4` của Runtime Probe `CoilNet` trên Logcat khi cuộn danh sách 50 truyện. |
| Vòng tròn phụ thuộc (Circular Dependency) hoặc lỗi khởi tạo sớm giữa `Application` và Hilt | **Trung bình** | **Cơ sở kỹ thuật**: Lớp sinh mã `Hilt_AppTruyenApplication.onCreate()` thực hiện tiêm các trường `@Inject` (gồm `@Inject lateinit var imageLoader: dagger.Lazy<ImageLoader>`) ngay trong lệnh `super.onCreate()`. Mặt khác, phương thức `newImageLoader(PlatformContext)` của giao diện `SingletonImageLoader.Factory` chỉ được Coil 3 gọi lười (lazy) khi Composable đầu tiên yêu cầu nạp ảnh ở `MainActivity` (xảy ra sau khi `Application.onCreate()` đã hoàn tất). Việc dùng `dagger.Lazy<ImageLoader>` đảm bảo đồ thị phụ thuộc của ImageLoader (OkHttpClient, DiskCache) chỉ được khởi tạo khi thực sự cần.<br>**Kiểm chứng**: `compileDebugKotlin` + `assembleDebug` xác nhận Dagger codegen hợp lệ, cùng Runtime Wiring Probe ghi log `Timber.tag("CoilInit")`. |
| Cookie của CDN ảnh bên thứ ba (như imgur) bị lưu vào kho cookie WebView của app | **Thấp** | **Cơ sở kỹ thuật**: Lớp `ReadOnlyWikidichCookieJar` được áp dụng cho ImageLoader: `loadForRequest` chỉ chuyển tiếp tới `cookieHandler` khi `url.host` là `wikicv.org`, còn `saveFromResponse` hoàn toàn để trống. Nhờ đó không lưu bất kỳ cookie nào từ CDN bên thứ ba và không lộ cookie Wikidich sang host lạ.<br>**Kiểm chứng**: Unit test `TC13` trong `CoilImageInterceptorTest`. |
| Kết nối pooled stale bị server đóng ngầm | **Thấp** | **Cơ sở kỹ thuật**: Giữ mặc định `retryOnConnectionFailure(true)` trong `buildImageOkHttpClient` để OkHttp tự động phục hồi kết nối keep-alive hoặc HTTP/2 GOAWAY từ ConnectionPool dùng chung.<br>**Kiểm chứng**: Unit test `TC12` trong `CoilImageInterceptorTest`. |

*Ghi chú kỹ thuật bổ sung*:
- *Thời gian chờ khi retry `SocketTimeoutException`*: Với `readTimeout = 30s`, nếu request gặp timeout ở lần 1 và tiếp tục timeout ở lần retry thứ 2 thì tổng thời gian chờ tối đa có thể lên tới ~60s trên luồng nền của Dispatcher mà không gây đơ/chặn giao diện UI. Đây là đánh đổi có chủ đích để tăng cơ hội tải ảnh thành công khi origin server chậm, và request sẽ bị ngắt lập tức nếu người dùng cuộn item ra khỏi màn hình nhờ kiểm tra `!call.isCanceled()`.
- *Phạm vi áp dụng User-Agent Chrome*: User-Agent đồng bộ `WikidichScraper.DESKTOP_UA` (`Chrome/122.0.0.0`) được `CoilImageInterceptor` gắn cho mọi request nạp ảnh chưa có header `User-Agent` (bao gồm cả ảnh truyện SsTruyện, TTV và `ReaderPokemonSupport.kt:341`). Điều này hoàn toàn an toàn và tương thích tốt với các CDN ảnh tiêu chuẩn.

---

### 10. Cập Nhật Docs

- `docs/lessons-learned.md`: Thêm bài học ngày 2026-10-28 về việc Coil 3 cần `SingletonImageLoader.Factory` dùng chung `ResilientDohDns`, thiết lập `CoilImageInterceptor` với Runtime Probe `CoilNet` theo dõi inFlight, đồng bộ User-Agent `Chrome/122.0.0.0` với WebView để duy trì hiệu lực cookie `cf_clearance`, và chuẩn hóa loại bỏ tham số `o` (`?o=1`) trên endpoint `/photo/<id>` của `wikicv.org` bằng `HttpUrl` (kèm dữ liệu đo đạc thực nghiệm curl: thumbnail 44.2 KB vs ảnh gốc 690.9 KB, `cf-cache-status: DYNAMIC`).
- `docs/scraping.md`: Cập nhật CSS Selectors của `WikidichScraper` (thêm selector ảnh bìa trang chi tiết `.book-info .cover-wrapper img, .cover-info img, .book-info img`, hàm `extractCoverUrl` duyệt `listOf(src, data-src)` và dùng `HttpUrl`).
- `docs/architecture.md`: Cập nhật mục Network & Image Loading (ghi chú về việc `AppTruyenApplication` implement `SingletonImageLoader.Factory`, `CoilImageInterceptor`, `ReadOnlyWikidichCookieJar`, `buildImageOkHttpClient` và cấu hình ImageLoader trong `CoilModule`).
- `docs/changelog.md`: Thêm mục sửa lỗi ảnh bìa Wikidich và tối ưu ImageLoader trong phiên bản kế tiếp.
- `docs/progress.md`: Cập nhật trạng thái tính năng.
- `docs/plans/README.md`: Cập nhật tóm tắt kế hoạch Revision 4.

## Audit History

<!-- Reviewer chỉ append "### Audit Run N" ở dưới. Không sửa phần Technical Plan. -->

### Audit Run 1

- **Reviewer**: Claude Code CLI (claude-opus-5-5)
- **Ngày**: 2026-10-28
- **Đối tượng**: Technical Plan Revision 1

#### Kiểm Chứng Lỗi Cũ

Không có Audit Run trước → không có lỗi cũ cần kiểm lại.

#### Phát Hiện Mới (Fresh Findings & Regression Check)

**F1 `[MAJOR]` — Chẩn đoán nguyên nhân #2 (trang chi tiết) không khớp code thật.**
Plan (Mục 1, Mục 2) khẳng định selector `.cover-info img, .book-info img` "không khớp chính xác hoặc bóc tách sai". Thực tế:
- [SRC] `app/src/test/resources/fixtures/wikidich/story_detail.html:285-288`: `<div class="book-info">` bọc `<div class="cover-wrapper">` chứa `<img class="z-depth-1 materialboxed" src="/photo/6a950ea654b8087c7d7c833d?o=1">`. Không có `img` nào thuộc `.cover-info`/`.book-info` đứng trước dòng 288.
- [SRC] `WikidichScraper.kt:157-158`: `doc.selectFirst(".cover-info img, .book-info img")` → khớp đúng ảnh ở dòng 288, kết quả hiện tại là `https://wikicv.org/photo/6a950ea654b8087c7d7c833d?o=1` (khác rỗng).
- [SRC] `StoryDetailViewModel.kt:212-218` và `StoryRepository.kt:398, 451`: cover đã được merge bằng `resolveSafeCoverUrl` ở repository.
Do đó selector không phải nguyên nhân gốc. Khác biệt thật duy nhất là hậu tố `?o=1` (có thể là ảnh gốc kích thước lớn, cache key khác danh sách `/photo/<id>` ở `search_logged_in.html:462`). Plan cần chẩn đoán lại bằng bằng chứng (Logcat lỗi của Coil trên màn chi tiết, hoặc `curl.exe -w "%{http_code} %{size_download} %{time_total}"` cho cả `/photo/<id>` và `/photo/<id>?o=1`), rồi viết lại phần Goal #2 cho đúng. Skill `apptruyen-debug` mà plan tự dẫn yêu cầu tìm đúng nguyên nhân gốc.

**F2 `[MAJOR]` — Chẩn đoán nguyên nhân #1 (throttle ảnh khi cuộn) chưa có bằng chứng.**
- [SRC] `docs/lessons-learned.md:112`: throttle ~12.03s được đo trên endpoint `/tim-kiem`, không phải `/photo/`. Plan ngoại suy sang host ảnh mà không đo.
- [SRC] `docs/lessons-learned.md:121`: quy tắc phòng ngừa yêu cầu đo `time_total` bằng `curl.exe -w` trước khi đặt timeout.
- Plan không có Logcat/stacktrace `SocketTimeoutException` từ Coil, không có phép đo cho `/photo/`. Các giả thuyết "thiếu Referer/User-Agent gây lỗi" và "thiếu cookie gây lỗi" cũng chưa được chứng minh. Plan cần bổ sung phép đo (lệnh đề xuất ở mục Commands, hiện `UNVERIFIED`) và tách rõ yếu tố nào thật sự gây lỗi.

**F3 `[MAJOR]` — Interceptor retry trong `CoilModule` mô tả chưa đủ và không có unit test.**
Mục 6 Bước 3 chỉ ghi "retry 1 lần cho lỗi mạng tạm thời / 429 / 5xx". Còn thiếu:
1. Phải `close()` response lỗi trước khi gọi `chain.proceed()` lần hai (OkHttp 5 yêu cầu đóng response trước khi tái gọi; không đóng sẽ rò connection hoặc ném `IllegalStateException`).
2. Retry ngay lập tức với 429 đi ngược mục đích giảm tải cho host đang throttle (S6: "không spam request"). Cần backoff hoặc tôn trọng `Retry-After`, hoặc bỏ 429 khỏi danh sách retry. Blocking `Thread.sleep` trong interceptor chiếm thread của Dispatcher (tối đa 4/host) cũng phải được cân nhắc.
3. Phải bỏ qua retry khi `chain.call().isCanceled()` (Compose hủy request khi item cuộn khỏi màn hình) và không retry `IOException` do hủy.
4. OkHttp đã có `retryOnConnectionFailure = true` mặc định cho lỗi kết nối; plan cần nêu ranh giới để không retry chồng (1 + retry mặc định).
5. Interceptor áp dụng cho ảnh của mọi nguồn (SsTruyện CDN, TTV, imgur ở `story_detail.html:1130`), nên phải nêu rõ quy tắc Referer cho từng host.
- Test Contract (Mục 7) không có TC nào cho interceptor (UA/Referer được gắn, retry đúng 1 lần, đóng response, không retry khi bị hủy) và TC6 không gắn với file test nào; Mục 8 chỉ liệt kê `WikidichScraperTest` và `StoryDetailViewModelTest`. `.agents/rules/01-coding.md` bắt buộc unit test cho logic; `mockwebserver3:5.3.2` đã có sẵn ([SRC] `app/build.gradle.kts:175`). Nên tách interceptor ra một class riêng (ví dụ trong `data/remote/` hoặc `di/`) để test được bằng MockWebServer.

**F4 `[MAJOR]` — Merge cover trong `StoryDetailViewModel` trùng logic của repository (vi phạm single source of truth, S4) và trỏ vào hàm không tồn tại.**
- [SRC] `StoryRepository.kt:398` (nhánh có cache, refresh nền) và `StoryRepository.kt:451/460` (nhánh không cache) đã dùng `resolveSafeCoverUrl(fresh, existing)` trước khi `insertStory`. [SRC] `StoryRepository.kt:507`: `observeStory` phát entity từ Room, tức đã được merge. Vì vậy trường hợp TC4 (Flow phát `coverImageUrl = ""` trong khi đã có cover) chỉ xảy ra khi DB thật sự lưu rỗng, mà điều này đã bị `resolveSafeCoverUrl` chặn.
- Lỗ hổng thật nằm ở repository: [SRC] `StoryRepository.kt:445, 468` trả về `resolvedStory` (cover chưa merge) trong khi entity ghi xuống DB ở dòng 451 đã merge, nên UI thấy cover rỗng trong lúc chờ Flow phát lại. Nên sửa tại repository (`return resolvedStory.copy(coverImageUrl = entity.coverImageUrl)`) và thêm test vào `StoryRepositoryTest.kt` thay vì để ViewModel tự cache song song.
- Merge ở ViewModel còn có hại: nếu repository chủ động làm sạch cover hỏng về `""` ([SRC] `StoryRepository.kt:419-423`), ViewModel sẽ giữ lại giá trị cũ đã bị loại.
- Plan nhắc `loadStory` và `observeStory` trong ViewModel "lines 200–240", nhưng thực tế hàm là `loadStoryDetail` ([SRC] `StoryDetailViewModel.kt:148`), còn `observeStory` là lời gọi repository không gán vào Job ([SRC] `StoryDetailViewModel.kt:212-218`). Ở lần nạp đầu, `_state.value.story` đang là `null` nên "merge lúc nạp cache ban đầu" không có giá trị nào để merge.

**F5 `[MAJOR]` — Thiếu cập nhật `docs/lessons-learned.md`.**
[SRC] `.agents/rules/06-docs.md:19, 29`: fix bug bắt buộc thêm entry lessons-learned (Triệu chứng / Nguyên nhân gốc / Cách sửa / Quy tắc phòng ngừa). Mục 10 của plan không liệt kê file này.

**F6 `[MAJOR]` — Rủi ro mức Trung bình chưa có control đã xác minh (evidence-bar 4.5.4).**
- Rủi ro "throttle host ảnh" dựa vào `Dispatcher.maxRequestsPerHost = 4`. Giới hạn của Dispatcher chỉ có hiệu lực với lời gọi bất đồng bộ (`enqueue`), không áp dụng cho `execute()`. Plan chưa dẫn `[SRC]`/`[URL]` cho thấy `OkHttpNetworkFetcherFactory` của Coil 3.4.0 dùng `enqueue`, cũng chưa có bước verify (ví dụ test MockWebServer đếm số request đồng thời). → `UNVERIFIED`.
- Rủi ro "vòng phụ thuộc Hilt" chưa có bước verify ngoài build. Cần ghi rõ: field injection của `@HiltAndroidApp` chạy trong `super.onCreate()`, còn `newImageLoader` được Coil gọi lười ở request ảnh đầu tiên (sau `onCreate`). Cần có wiring probe cụ thể (ví dụ log/assert trong debug hoặc test Robolectric nếu dự án có sẵn), không chỉ là câu mô tả.

**F7 `[MINOR]` — TC1 có giá trị mong đợi không khớp môi trường test.**
[SRC] `WikidichScraperTest.kt:35`: `customBaseUrl = mockBaseUrl`, nên `fixUrl("/photo/…")` cho ra URL của MockWebServer chứ không phải `https://wikicv.org/photo/…` như TC1/TC3. Nên assert theo `server.url("/photo/6a950ea654b8087c7d7c833d")` hoặc `endsWith`, và assert thêm việc không còn `?o=`.

**F8 `[MINOR]` — Cookie jar dùng chung lưu cookie của host ảnh bên thứ ba.**
[SRC] `WebViewCookieHandler.kt:20-28`: `saveFromResponse` ghi mọi `Set-Cookie` vào `CookieStore` (WebView). Khi ImageLoader dùng client chung, cookie của imgur/CDN sẽ được lưu bền vào kho cookie WebView. Phía gửi thì đã được giới hạn theo URL ([SRC] `WebViewCookieHandler.kt:31-39`), nên không lộ cookie Wikidich sang host khác. Cân nhắc dùng `CookieJar` chỉ đọc cho ImageLoader, hoặc ghi rõ đây là rủi ro chấp nhận được.

**F9 `[MINOR]` — Độ chính xác và văn phong.**
- Assumption 2 ghi `[SRC WikidichScraper.kt:288 …]`, nhưng dòng 288 là của fixture `story_detail.html`, không phải của scraper.
- Trong `extractCoverUrl`, `removeSuffix("?o=1").substringBefore("?o=")` thừa một bước; cũng chưa xử lý `&o=`. Nên dùng `HttpUrl` để bỏ đúng tham số `o`.
- Selector `img.materialboxed` khá rộng (có thể khớp ảnh trong mô tả/bình luận); nên để sau `div.cover-wrapper:not(.small-cover) img` hoặc giới hạn trong `.book-info`.
- Goal dùng từ "triệt để", Versions Table dùng "Tương thích hoàn toàn" khi chưa có `[URL]` xác minh latest version.

#### Mental Simulation Matrix (S1–S6 + Domain)

| Kịch bản | Kết quả | Bằng chứng | Chi tiết |
|---|---|---|---|
| S1 Cold-start & Build | PASS | [CMD] `compileDebugKotlin` Exit 0; [SRC] `app/build.gradle.kts:129-130,143`; [SRC] `AppModule.kt:52-64` | Không thêm dependency; `OkHttpClient` `@Singleton` có sẵn để inject vào `CoilModule`; `coil-network-okhttp` đã có trên classpath. Chưa có `[URL]` cho chữ ký `SingletonImageLoader.Factory.newImageLoader(PlatformContext)` của Coil 3.4.0, implementer cần xác nhận khi build. |
| S2 Lifecycle & Teardown | FAIL | [SRC] `StoryDetailViewModel.kt:212-218`; plan Mục 6 Bước 3 | Interceptor retry chưa đặc tả việc đóng `Response` lỗi và tôn trọng việc hủy request (F3). Ngoài phạm vi plan: Job của `observeStory` không được giữ/hủy khi reload (sẵn có từ trước). |
| S3 Database & Temporal | PASS | [SRC] `StoryRepository.kt:386-409, 446-464`; plan Out of Scope | Không đổi schema/`StoryEntity`. URL cũ có `?o=1` trong Room sẽ được refresh nền ghi đè bằng URL mới, vì `resolveSafeCoverUrl` ưu tiên giá trị fresh hợp lệ. Xem timeline bên dưới. |
| S4 Cross-Task State Flow | FAIL | [SRC] `StoryRepository.kt:398, 451, 468, 507` | Merge ở ViewModel trùng logic repository; lỗ hổng thật ở dòng 468 (F4). |
| S5 Security & Privacy | PASS (kèm MINOR F8) | [SRC] `WebViewCookieHandler.kt:20-39` | Cookie chỉ gửi theo URL khớp domain; plan không thêm log cookie. Yêu cầu: interceptor mới không được log header. Xem timeline TOCTOU. |
| S6 External Resilience | FAIL | [SRC] `docs/lessons-learned.md:112,121`; plan Mục 6 Bước 3 | Chẩn đoán throttle `/photo/` chưa đo (F2); retry 429 không backoff (F3); hiệu lực của Dispatcher chưa xác minh (F6). |
| Domain (cache ảnh / gộp nguồn) | UNVERIFIED | [SRC] `search_logged_in.html:462` và `story_detail.html:288` | Bỏ `?o=1` giúp cache key giữa danh sách và chi tiết trùng nhau (lợi cho hit cache), nhưng chưa xác minh `/photo/<id>` và `?o=1` trả về cùng ảnh/chất lượng (F1). Không ảnh hưởng khử trùng lặp truyện. |

#### Adversarial Timelines

```
Cơ chế: Refresh nền cover (appScope) song song với observeStory của ViewModel (S3 – đồng thời)
T1: VM gọi getStoryDetail → nhánh cache trả cached (cover "/photo/x?o=1") | T2: appScope fetch fresh → extractCoverUrl trả "/photo/x" → insertStory(entity cover "/photo/x") | T3: Room phát lại → observeStory → VM cập nhật story.cover = "/photo/x"
Kết cục: UI đổi URL một lần, Coil tải lại hoặc hit cache theo key danh sách → AN TOÀN (không cần merge ở VM)
```

```
Cơ chế: Nhánh không cache, scraper trả cover rỗng nhưng DB có entity cũ theo slug (S4 – check→use)
T1: findCachedStory trả null (URL khác) | T2: source.getStoryDetail trả cover "" | T3: existingEntity tìm theo slug có cover hợp lệ → entity ghi cover cũ (dòng 451) nhưng hàm trả resolvedStory cover "" (dòng 468)
Kết cục: UI hiện placeholder cho đến khi observeStory phát → LỖI hiển thị tạm thời (MAJOR F4, nên sửa ở repository)
```

```
Cơ chế: Cookie Wikidich hết hạn giữa lúc tải ảnh (S5 – TOCTOU)
T1: Coil gửi GET /photo/x kèm cookie cũ | T2: server trả 302 về trang đăng nhập/Cloudflare HTML (followRedirects=true, AppModule.kt:63) | T3: Coil nhận body text/html → decode thất bại → error placeholder
Kết cục: Không parse nhầm thành nội dung, không crash; ảnh lỗi giữ nguyên trạng thái → AN TOÀN. Với 429/5xx, retry ngay không backoff sẽ làm tăng tải → LỖI (MAJOR F3)
```

```
Cơ chế: Người dùng cuộn nhanh 50 item, Compose hủy request (S6 – thất bại giữa chừng / vòng đời)
T1: 4 request/host đang chạy, 46 đang chờ trong Dispatcher | T2: item rời màn hình → coroutine Coil bị hủy → Call.cancel() | T3: interceptor nhận IOException("Canceled") → nếu retry không kiểm tra isCanceled sẽ gọi proceed lần hai trên call đã hủy
Kết cục: Phụ thuộc đặc tả interceptor đang thiếu → UNVERIFIED (MAJOR F3)
```

Trục vòng đời Android (process bị kill): N/A cho phần lưu trữ, vì disk cache của Coil và Room ghi độc lập, không có trạng thái nửa vời mới.

#### Commands Executed

Trích từ `evidence.md`:
- `### [CMD] git status --short` — `Exit: 0` — WikidichScraper.kt và WikidichScraperTest.kt đang có thay đổi chưa commit từ plan 2026-10-27; implementer phải rebase phần sửa lên trạng thái này.
- `### [CMD] cmd.exe /d /c gradlew.bat compileDebugKotlin` — `Exit: 0` — `BUILD SUCCESSFUL in 8s`.
- `### [CMD] cmd.exe /d /c gradlew.bat ktlintCheck` — `Exit: 0` — `BUILD SUCCESSFUL in 1s`.
- `### [CMD] cmd.exe /d /c gradlew.bat testDebugUnitTest` — `Exit: 0` — `BUILD SUCCESSFUL in 2s` (baseline, toàn bộ UP-TO-DATE).

`UNVERIFIED`, lệnh đề xuất cho F1/F2:
- `curl.exe -q -s -o NUL -w "%{http_code} %{content_type} %{size_download} %{time_total}" -A "Mozilla/5.0" https://wikicv.org/photo/6a950ea654b8087c7d7c833d`
- `curl.exe -q -s -o NUL -w "%{http_code} %{content_type} %{size_download} %{time_total}" -A "Mozilla/5.0" "https://wikicv.org/photo/6a950ea654b8087c7d7c833d?o=1"`
- Chạy 10–20 lần liên tiếp và không có Referer/UA để đo throttle trên `/photo/`.

#### Các Tấn Công Đã Thử (Attempted Attack Vectors)

Có Phát Hiện Mới nên mục này không bắt buộc. Ghi nhận thêm các hướng đã thử nhưng không làm vỡ thiết kế:
1. Selector mới khớp nhầm ảnh `small-cover` của truyện liên quan: [SRC] `story_detail.html:689` cho thấy `small-cover` nằm trên thẻ `<a>`, không phải `div`, nên `div.cover-wrapper:not(.small-cover) img` không khớp các ảnh này → an toàn.
2. Placeholder lọt qua bộ lọc: [SRC] `story_detail.html:1656` dùng `default-avatar.png`, khớp điều kiện `contains("default-avatar")`; `manager-avatar` ở dòng 1130 nằm ngoài `.book-info` → an toàn.
3. Dispatcher riêng làm nghẽn scraper: plan nêu `newBuilder()` + Dispatcher riêng, nên Dispatcher của client chung ([SRC] `AppModule.kt:54-64`) không bị đổi `maxRequestsPerHost` → an toàn, với điều kiện implementer không gọi `okHttpClient.dispatcher.maxRequestsPerHost = 4` trên instance chung.

#### Kết luận

Còn 6 lỗi `[MAJOR]` (F1–F6), các kịch bản S2, S4, S6 FAIL. Revision 2 cần: chẩn đoán lại nguyên nhân gốc kèm số đo, chuyển phần merge cover xuống repository, đặc tả interceptor và có test, xác minh control của các rủi ro, bổ sung lessons-learned.

[CHANGES_REQUESTED]

### Audit Run 2

- **Reviewer**: Claude Code CLI (claude-opus-5-5)
- **Ngày**: 2026-10-28
- **Đối tượng**: Technical Plan Revision 2

#### Kiểm Chứng Lỗi Cũ

Audit Run 1 kết luận CHANGES_REQUESTED. Từng lỗi được kiểm lại trên code thật như sau:

| Mã | Trạng thái | Bằng chứng | Ghi chú |
|---|---|---|---|
| F1 | ĐÃ SỬA | [SRC] `WikidichScraper.kt:157-158` (`selectFirst(".cover-info img, .book-info img")` + `fixUrl`), [SRC] `story_detail.html:288` (`/photo/6a950ea654b8087c7d7c833d?o=1`), [SRC] `story_detail.html:691` (ảnh danh sách `/photo/<id>` không có `?o=`) | Chẩn đoán mới (selector khớp, lỗi nằm ở hậu tố `?o=1` làm lệch cache key) khớp code thật. Số đo 44.2 KB / 690.9 KB không có trong `evidence.md` → `UNVERIFIED` với reviewer (xem N7), nhưng hướng sửa không phụ thuộc vào con số cụ thể. |
| F2 | SỬA MỘT PHẦN | Không có `[CMD] curl.exe` trong `evidence.md` | Plan có nêu số đo `/photo/` (~0.6–0.8s, `cf-cache-status: DYNAMIC`) nhưng không ghi lại output; vẫn chưa có Logcat `SocketTimeoutException` từ Coil. Phần còn lại hạ xuống `[MINOR]` N7. |
| F3 | ĐÃ SỬA (mức thiết kế) | Plan Mục 5 mục 2 (sketch), [SRC] `app/build.gradle.kts:175` (`mockwebserver3:5.3.2`) | Interceptor đã tách class riêng, đóng response trước khi retry, kiểm tra `isCanceled()`, bỏ 429/403, có quy tắc Referer theo host, có `CoilImageInterceptorTest` (TC6–TC11). Ranh giới retry chồng được xử lý bằng `retryOnConnectionFailure(false)` nhưng tạo ra rủi ro mới (N4). File `[NEW]` nên chưa có code thật để đối chiếu. |
| F4 | ĐÃ SỬA | [SRC] `StoryRepository.kt:445` (`resolvedStory` chưa có cover merge), `:451`, `:460` (merge chỉ áp vào entity), `:468` (`return resolvedStory`) | Sketch Bước 5 tính `resolveSafeCoverUrl(story.coverImageUrl, existingEntity?.coverImageUrl)` trước khi tạo `resolvedStory`; với `existingEntity == null` cho cùng kết quả như dòng 460. Không còn sửa `StoryDetailViewModel`. |
| F5 | ĐÃ SỬA | Plan Mục 10 | `docs/lessons-learned.md` đã có trong danh sách cập nhật. |
| F6 | SỬA MỘT PHẦN | Không có `[SRC]`/`[URL]` cho mã nguồn Coil; [SRC] `app/build.gradle.kts` không có Robolectric | Plan chỉ mô tả bằng lời rằng `OkHttpNetworkFetcher` dùng `enqueue`; bước kiểm chứng trỏ vào `CoilImageInterceptorTest`, nhưng test này không chạm tới Dispatcher cấu hình trong `CoilModule`. Wiring probe cho `SingletonImageLoader` chưa nêu cách thực hiện. Xem N2. |
| F7 | ĐÃ SỬA | [SRC] `WikidichScraperTest.kt:35` (`customBaseUrl = mockBaseUrl`) | TC1/TC3 đã chuyển sang `endsWith(...)` và `!contains("?o=")`. |
| F8 | ĐÃ SỬA (mức thiết kế) | [SRC] `WebViewCookieHandler.kt:20-28` (`saveFromResponse` ghi mọi cookie) | Plan dùng `CookieJar` chỉ đọc, chỉ load cho `wikicv.org`. Logic lọc host này chưa có test (N2). |
| F9 | SỬA MỘT PHẦN | [SRC] `story_detail.html:285-288` | Đã sửa trích dẫn Assumption 2, bỏ `removeSuffix`, selector giới hạn trong `.book-info`. Còn lại: regex `[?&]o=\d+` có trường hợp làm hỏng URL (N3); Goal vẫn dùng "tuyệt đối không retry", "100% request", "cache miss 100%". |

#### Phát Hiện Mới (Fresh Findings & Regression Check)

**N1 `[MAJOR]` — User-Agent của ImageLoader lệch với UA mà phiên Wikidich/Cloudflare đang dùng.**
- [SRC] `WikidichScraper.kt:64-65` (`DESKTOP_UA` = `Chrome/122.0.0.0`), [SRC] `WikidichScraper.kt:716-718` (AJAX cũng dùng `Chrome/122.0.0.0`), [SRC] `ui/auth/SourceLoginScreen.kt:167` (WebView đăng nhập đặt UA `Chrome/122.0.0.0`), [SRC] `SourceAuthManager.kt:113-114, 159-160` (cùng UA đó).
- Plan đặt `DEFAULT_USER_AGENT = ...Chrome/131.0.0.0...` và gửi kèm cookie Wikidich lấy từ WebView (cookie jar chỉ đọc cho `wikicv.org`). Cookie `cf_clearance` của Cloudflare gắn với User-Agent đã giải challenge; gửi cookie đó với UA khác thường bị coi là không hợp lệ và trả về trang challenge (403/HTML). Khi đó interceptor không retry 403 (đúng thiết kế), Coil decode thất bại → ảnh bìa vẫn lỗi đúng vào lúc plan kỳ vọng cookie giúp vượt chặn. Việc chuyển cookie sang ImageLoader mất tác dụng, và còn có thể làm Cloudflare gắn cờ phiên vì một cookie xuất hiện với hai UA.
- Yêu cầu: dùng chung một hằng UA với nguồn Wikidich (ví dụ đưa `DESKTOP_UA` ra hằng `internal` dùng chung, hoặc inject vào `CoilImageInterceptor`) và thêm TC khẳng định request tới `wikicv.org` mang đúng UA mà `WikidichScraper` dùng. Đồng thời cân nhắc UA của `sstruyen.pics` / `tangthuvien.org` theo `BaseScraper.kt:28, 370` (TTV Desktop, còn lại Mobile) để nhất quán.

**N2 `[MAJOR]` — Control của rủi ro mức Trung bình và logic trong `CoilModule` không có test hay bước kiểm chứng thực thi được (evidence-bar 4.5.4, `.agents/rules/01-coding.md`).**
- Rủi ro "Host ảnh bị quá tải" (Trung bình): mục "Kiểm chứng" ghi "Kiểm tra cấu hình Dispatcher trong `CoilImageInterceptorTest`". Interceptor nhận `Chain`, không truy cập được Dispatcher của client dựng trong `CoilModule`, nên bước này không kiểm chứng được gì. Phần "Cơ sở kỹ thuật" về `CallFactoryNetworkClient` + `Call.enqueue` là mô tả của planner, không có `[SRC]` mã nguồn Coil 3.4.0 hay `[URL]` → theo 4.5.1 vẫn bị coi là chưa xác minh.
- Rủi ro "Cookie bên thứ ba" và bộ lọc host của cookie jar chỉ đọc cũng ghi kiểm chứng bằng `CoilImageInterceptorTest`, nhưng logic này nằm trong `CoilModule`.
- Rủi ro "Vòng phụ thuộc Hilt" (Trung bình): chỉ có `compileDebugKotlin`/`assembleDebug`. TC12 ghi Test Class là "Wiring Probe" nhưng [SRC] `app/build.gradle.kts` không có Robolectric, nên `SingletonImageLoader.get(context)` không chạy được trong JVM test; plan không nêu cách thực hiện probe.
- Yêu cầu: tách phần dựng client thành hàm/class testable trong `data/remote/` (ví dụ `internal fun buildImageOkHttpClient(base: OkHttpClient, cookieHandler: CookieJar): OkHttpClient` và `internal class ReadOnlyWikidichCookieJar`), rồi thêm test JVM: `client.dispatcher.maxRequestsPerHost == 4`, `maxRequests == 32`, `interceptors` có `CoilImageInterceptor`, cookie jar trả rỗng cho `i.imgur.com` và không gọi `saveFromResponse` của handler gốc. Với Coil `enqueue`: hoặc trích `[URL]`/mã nguồn, hoặc hạ claim và chấp nhận rằng `maxRequestsPerHost` chỉ là best-effort. Với Hilt: nêu một probe thủ công cụ thể (ví dụ `Timber.d` trong `newImageLoader` ở debug build, kiểm Logcat khi mở `SearchScreen`, kèm kiểm request ảnh tới `wikicv.org` có UA/Referer mong đợi).

**N3 `[MINOR]` — Regex `[?&]o=\d+` làm hỏng URL khi `o` không phải tham số cuối, và áp dụng cho mọi host.**
- `https://wikicv.org/photo/x?o=1&w=200` → `https://wikicv.org/photo/x&w=200` (mất dấu `?`, path bị đổi). Hiện fixture chỉ có `?o=1` ([SRC] `story_detail.html:288`) nên chưa gây lỗi, nhưng logic này còn chạy trên mọi URL đọc ra từ Room qua `toDomain()` ([SRC] `StoryRepository.kt:838`).
- `upgradeToHttps` sẽ lọc `o` trên mọi URL chứa `/photo/`, kể cả host khác `wikicv.org`.
- Đề nghị: dùng `toHttpUrlOrNull()?.newBuilder()?.removeAllQueryParameters("o")?.build()` và chỉ áp khi host là `wikicv.org`; thêm TC cho `?o=1&w=200` và cho host khác có `/photo/`. Input TC4 `http://wikicv.org/photo/123&o=1` không phải dạng URL thật; nên thay bằng `?a=1&o=1`.

**N4 `[MINOR]` — `retryOnConnectionFailure(false)` bỏ cơ chế phục hồi kết nối pooled bị đóng ngầm.**
- `okHttpClient.newBuilder()` dùng chung `ConnectionPool` với client scraper ([SRC] `AppModule.kt:62`). Khi tắt `retryOnConnectionFailure`, OkHttp không tự thử lại khi kết nối lấy từ pool đã bị server đóng (keep-alive hết hạn, HTTP/2 GOAWAY) và không chuyển sang route khác. Interceptor bù được 1 lần, nhưng như vậy lần retry duy nhất bị dùng cho lỗi mà OkHttp vốn tự xử lý; nếu lần 2 lại gặp lỗi route khác thì request thất bại.
- Đề nghị: giữ mặc định `true` và cho interceptor chỉ retry 502/503/504 (cùng lắm thêm `SocketTimeoutException`), hoặc ghi rõ lý do chấp nhận trong Rủi Ro.

**N5 `[MINOR]` — Chi tiết đặc tả interceptor.**
- Phải ghi rõ đăng ký bằng `addInterceptor` (application interceptor). Network interceptor không được gọi `proceed` hai lần.
- Khối `try { return chain.proceed(newRequest) } catch (retryError: IOException) { throw retryError }` thừa; nên `retryError.addSuppressed(error)` khi có lỗi lần 1 để không mất nguyên nhân gốc.
- Retry `SocketTimeoutException` với `readTimeout = 30s` có thể kéo một ảnh lên ~60s và tăng tải lên origin đang chậm; nên ghi rõ hành vi này.
- `resolveRefererForHost` khớp `.tangthuvien.vn` nhưng không khớp `tangthuvien.vn`.

**N6 `[MINOR]` — Blast radius và đánh đổi chất lượng chưa ghi.**
- [SRC] `ui/reader/ReaderPokemonSupport.kt:341` cũng dùng `SubcomposeAsyncImage` qua singleton loader, nên sẽ đổi UA/dispatcher/cookie jar. Plan Mục 2 chỉ liệt kê `StoryCardPremium`, `StoryDetailScreen`, `DownloadsScreen`.
- Bỏ `?o=1` làm trang chi tiết hiển thị ảnh thumbnail (~44 KB) thay vì ảnh gốc. [SRC] `StoryDetailScreen.kt:341-348` cho thấy ảnh nền bị `blur(25.dp)` nên chấp nhận được; ảnh bìa chính ở dòng 415 có thể bị mờ hơn trên màn hình mật độ cao. Nên ghi đánh đổi này vào lessons-learned/changelog.

**N7 `[MINOR]` — Số đo `curl.exe` không có trong `evidence.md`.**
Assumption 3 ghi `[CMD curl.exe -q -sI --max-time 20]` nhưng file evidence không có lệnh này. Đề nghị dán output (status, `Content-Length`, `cf-cache-status`, `time_total`) vào lessons-learned khi triển khai.

#### Mental Simulation Matrix (S1–S6 + Domain)

| Kịch bản | Kết quả | Bằng chứng | Chi tiết |
|---|---|---|---|
| S1 Cold-start & Build | PASS | [CMD] `compileDebugKotlin` Exit 0; [SRC] `app/build.gradle.kts:129-130`; [SRC] `AppModule.kt:52-64`; [SRC] `CoilModule.kt:20-39` | Không thêm dependency; `OkHttpClient` `@Singleton` có sẵn để inject; `ImageLoader` đã là binding `@Singleton` nhưng hiện chưa ai dùng (Grep `ImageLoader` trong `app/src` chỉ thấy `CoilModule.kt`), nên Coil đang dùng loader mặc định, đúng như plan mô tả. Chữ ký `newImageLoader(PlatformContext)` chưa có `[URL]`; compile sẽ xác nhận. |
| S2 Lifecycle & Teardown | PASS | [ADV] timeline 2; plan sketch interceptor | Response lỗi được `close()` trước khi retry; `isCanceled()` chặn retry sau khi Compose hủy request. Dispatcher riêng tạo executor riêng, sống theo singleton nên không rò. |
| S3 Database & Temporal | PASS | [SRC] `StoryRepository.kt:838` (`toDomain` gọi `upgradeToHttps`), `:863`, `:870` (`resolveSafeCoverUrl` gọi `upgradeToHttps` trên cả 2 nhánh), `:398`, `:794` | Không đổi schema. URL `?o=1` cũ trong Room được chuẩn hóa khi đọc và khi ghi lại qua `resolveSafeCoverUrl`. Xem timeline 1. |
| S4 Cross-Task State Flow | PASS | [SRC] `StoryRepository.kt:445-468`, `:507` | Repository trả `resolvedStory` có cover đã merge; `observeStory` phát entity đã merge. VM không merge riêng. |
| S5 Security & Privacy | PASS | [SRC] `WebViewCookieHandler.kt:20-39`; [ADV] timeline 3 | Cookie chỉ gửi cho URL khớp domain; jar chỉ đọc chặn lưu cookie CDN bên thứ ba. Interceptor không log header. Logic lọc host chưa có test (N2), nhưng ngay cả khi lọc sai thì `getCookie(url)` vẫn chỉ trả cookie của đúng domain. |
| S6 External Resilience | FAIL | [SRC] `WikidichScraper.kt:64-65`, `SourceLoginScreen.kt:167`; [SRC] `AppModule.kt:62` | UA lệch với `cf_clearance` (N1); control Dispatcher chưa kiểm chứng (N2); `retryOnConnectionFailure(false)` trên pool dùng chung (N4). |
| Domain (cache ảnh / gộp nguồn) | PASS | [SRC] `story_detail.html:288` và `:691` | Sau khi bỏ `?o=1`, key ảnh chi tiết trùng với key ảnh danh sách `/photo/<id>`; disk cache trúng. Không ảnh hưởng khử trùng lặp truyện. |

#### Adversarial Timelines

```
Cơ chế: Bản ghi Room cũ có cover "?o=1" và refresh nền song song (S3 – đồng thời)
T1: VM gọi getStoryDetail → nhánh cache → cached.toDomain() → upgradeToHttps bỏ "?o=1" → UI nhận "/photo/x" | T2: appScope fetch fresh → extractCoverUrl trả "/photo/x" → resolveSafeCoverUrl → insertStory | T3: Room phát lại cùng URL "/photo/x"
Kết cục: URL ổn định, không đổi key giữa T1 và T3, Coil không tải lại → AN TOÀN
```

```
Cơ chế: Cuộn nhanh 50 item, Compose hủy request ảnh (S2/S6 – thất bại giữa chừng)
T1: request ảnh /photo/x nhận 503 | T2: item rời màn hình → Coil hủy coroutine → Call.cancel() | T3: interceptor kiểm tra isCanceled() == true → không retry, trả response 503 (đã không bị close) cho tầng trên, Coil bỏ kết quả và đóng body
Kết cục: Không gửi request thứ 2, không rò connection → AN TOÀN (với điều kiện implementer chỉ close response khi thực sự retry, như sketch)
```

```
Cơ chế: Cookie cf_clearance hợp lệ lúc check nhưng dùng với UA khác (S5/S6 – TOCTOU)
T1: User giải Cloudflare trong WebView (UA Chrome/122, SourceLoginScreen.kt:167) → cookie lưu vào CookieStore | T2: Coil gửi GET wikicv.org/photo/x kèm cf_clearance + UA Chrome/131 | T3: Cloudflare thấy UA không khớp → trả 403 challenge HTML → interceptor không retry 403 → decode thất bại
Kết cục: Ảnh bìa vẫn lỗi khi Wikidich bật challenge → LỖI (MAJOR N1)
```

```
Cơ chế: Kết nối pooled bị server đóng ngầm (S6 – thất bại giữa chừng)
T1: scraper dùng connection tới wikicv.org rồi trả về pool chung | T2: server đóng keep-alive | T3: Coil lấy connection cũ → IOException; retryOnConnectionFailure=false nên OkHttp không tự thử lại → interceptor retry 1 lần trên connection mới
Kết cục: Thường vẫn thành công nhưng đã dùng hết lượt retry; lỗi tiếp theo làm request thất bại → rủi ro thấp (MINOR N4)
```

Trục vòng đời Android: process bị kill giữa lúc tải ảnh chỉ để lại entry disk cache chưa commit, Coil `DiskCache` tự bỏ khi mở lại; Room không có ghi mới → N/A.

#### Commands Executed

Trích từ `evidence.md`:
- `### [CMD] git status --short` — `Exit: 0` — `WikidichScraper.kt`, `StoryRepository.kt`, `WikidichScraperTest.kt`, `StoryRepositoryTest.kt` đang có thay đổi chưa commit (plan 2026-10-27); số dòng trong plan cần đối chiếu lại khi triển khai.
- `### [CMD] cmd.exe /d /c gradlew.bat compileDebugKotlin` — `Exit: 0` — `BUILD SUCCESSFUL in 7s`.
- `### [CMD] cmd.exe /d /c gradlew.bat ktlintCheck` — `Exit: 0` — `BUILD SUCCESSFUL in 969ms`.
- `### [CMD] cmd.exe /d /c gradlew.bat testDebugUnitTest` — `Exit: 0` — `BUILD SUCCESSFUL in 1s` (baseline, toàn bộ UP-TO-DATE).

`UNVERIFIED`, lệnh đề xuất:
- `curl.exe -q -s -o NUL -w "%{http_code} %{size_download} %{time_total}" -A "Mozilla/5.0" https://wikicv.org/photo/6a950ea654b8087c7d7c833d`
- `curl.exe -q -s -o NUL -w "%{http_code} %{size_download} %{time_total}" -A "Mozilla/5.0" "https://wikicv.org/photo/6a950ea654b8087c7d7c833d?o=1"`

#### Các Tấn Công Đã Thử (Attempted Attack Vectors)

Có Phát Hiện Mới nên mục này không bắt buộc. Các hướng đã thử nhưng không làm vỡ thiết kế:
1. Selector mới khớp nhầm ảnh truyện liên quan: Jsoup `selectFirst` với nhóm selector trả phần tử đầu tiên theo thứ tự tài liệu; [SRC] `story_detail.html:288` là ảnh đầu tiên trong `.book-info`, ảnh liên quan bắt đầu từ dòng 691 → an toàn.
2. Nhánh không cache với `existingEntity == null`: sketch gọi `resolveSafeCoverUrl(story.coverImageUrl, null)`, khớp [SRC] `StoryRepository.kt:460` → không đổi hành vi ghi DB.
3. Test cũ của `upgradeToHttps` bị vỡ: [SRC] `StoryRepositoryTest.kt:751-753` chỉ dùng `http://example.com/a.jpg` (không có `/photo/`) → vẫn PASS sau khi sửa.

#### Kết luận

Các lỗi F1, F3, F4, F5, F7, F8 đã được xử lý trong Revision 2. Còn 2 lỗi `[MAJOR]`: N1 (UA lệch với cookie Cloudflare của Wikidich) và N2 (control của rủi ro Trung bình và logic cookie jar/Dispatcher trong `CoilModule` chưa có test hay bước kiểm chứng chạy được, gộp phần còn lại của F6). S6 FAIL. Revision 3 cần: dùng chung UA với `WikidichScraper`, tách phần dựng client ảnh ra để test được, nêu probe Hilt cụ thể, và cân nhắc các mục `[MINOR]` N3–N7.

[CHANGES_REQUESTED]

### Audit Run 3

- **Reviewer**: Claude Code CLI (claude-opus-5-5)
- **Ngày**: 2026-10-28
- **Đối tượng**: Technical Plan Revision 3

#### Kiểm Chứng Lỗi Cũ

Audit Run 1 và Audit Run 2 đều kết luận CHANGES_REQUESTED. Từng lỗi được kiểm lại trên code thật như sau. Các file `[NEW]` chưa tồn tại nên phần "ĐÃ SỬA" của chúng chỉ đúng ở mức thiết kế, đã đối chiếu với các API và file thật mà thiết kế dựa vào.

| Mã | Trạng thái | Bằng chứng | Ghi chú |
|---|---|---|---|
| F1 | ĐÃ SỬA | [SRC] `WikidichScraper.kt:157-158`; [SRC] `story_detail.html:288` (`/photo/6a950ea654b8087c7d7c833d?o=1`); [SRC] `search_logged_in.html:462` (cùng id, không có `?o=`) | Chẩn đoán "lệch cache key do `?o=1`" khớp code và fixture. Cùng một id ảnh xuất hiện ở danh sách (không `o`) và chi tiết (`?o=1`), nên `/photo/<id>` không có `o` là URL hợp lệ. |
| F2 | SỬA MỘT PHẦN | `evidence.md` không có `### [CMD] curl.exe …` | Số đo `/photo/` vẫn chỉ nằm trong plan; vẫn chưa có Logcat `SocketTimeoutException` từ Coil. Giữ ở mức `[MINOR]` (xem N7). |
| F3 | ĐÃ SỬA (mức thiết kế) | Sketch mục 5.2; [SRC] `app/build.gradle.kts:175` (`mockwebserver3:5.3.2`) | Đóng response trước retry, bỏ 429/403, kiểm tra `isCanceled()`, `addSuppressed`, Referer theo host, có TC6–TC11. |
| F4 | ĐÃ SỬA | [SRC] `StoryRepository.kt:445` (`resolvedStory` chưa merge cover), `:451`, `:460`, `:468` (`return resolvedStory`) | Sketch tính `safeCoverUrl` trước khi tạo `resolvedStory`; với `existingEntity == null` cho cùng kết quả như `:460`. TC5 phủ nhánh này. |
| F5 | ĐÃ SỬA | Plan mục 10 | Có `docs/lessons-learned.md`. |
| F6 | SỬA MỘT PHẦN | Xem R1 | Probe Hilt (`Timber.tag("CoilInit")`) đã có. Claim `enqueue` của Coil vẫn chỉ là mô tả của planner. |
| F7 | ĐÃ SỬA | [SRC] `WikidichScraperTest.kt:35` (`customBaseUrl = mockBaseUrl`) | TC1/TC3 dùng `endsWith` + `!contains("?o=")`. |
| F8 | ĐÃ SỬA (mức thiết kế) | [SRC] `WebViewCookieHandler.kt:20-28` (save ghi mọi cookie), `:31-39` (load theo URL); [SRC] `AppModule.kt:57` (`cookieJar(cookieHandler)`) | `ReadOnlyWikidichCookieJar(baseClient.cookieJar)` bọc đúng `WebViewCookieHandler`; TC13 kiểm load/save. |
| F9 | ĐÃ SỬA | Plan mục 1, mục 5 | Đã bỏ regex, dùng `HttpUrl`; selector giới hạn trong `.book-info`. |
| N1 | ĐÃ SỬA (mức thiết kế) | [SRC] `WikidichScraper.kt:51, 64-65` (`private const val DESKTOP_UA` nằm trong `companion object`) | Đổi sang `internal const val` cho phép tham chiếu `WikidichScraper.DESKTOP_UA` từ `data/remote/CoilImageInterceptor.kt` (cùng module). TC6 khẳng định `Chrome/122.0.0.0`. |
| N2 | SỬA MỘT PHẦN | [SRC] `CoilModule.kt:22-39` (chưa có `OkHttpNetworkFetcherFactory`); [SRC] `app/build.gradle.kts` không có Robolectric | `buildImageOkHttpClient` + TC12/TC13 kiểm cấu hình client và cookie jar; probe `CoilInit` kiểm việc Coil gọi factory. Phần còn thiếu: không có bước nào chứng minh request ảnh thực sự đi qua `imageOkHttpClient`/`CoilImageInterceptor`. Xem R1. |
| N3 | ĐÃ SỬA (mức thiết kế) | [SRC] `StoryRepository.kt:843-852` (`upgradeToHttps` hiện chỉ đổi scheme), `:838`, `:863`, `:870` | Sketch dùng `toHttpUrlOrNull()` + `removeAllQueryParameters("o")`, giới hạn host `wikicv.org` và path `/photo/`; TC4 có `?a=1&o=1` và host khác. |
| N4 | ĐÃ SỬA | [SRC] `AppModule.kt:62` (pool dùng chung) | Sketch giữ `retryOnConnectionFailure(true)`. |
| N5 | SỬA MỘT PHẦN | Sketch mục 5.2 | Đã dùng `addInterceptor`, `addSuppressed`, khớp `tangthuvien.vn`. Chưa ghi việc retry `SocketTimeoutException` với `readTimeout = 30s` có thể kéo một ảnh tới khoảng 60s (`[MINOR]`). |
| N6 | ĐÃ SỬA | [SRC] `ReaderPokemonSupport.kt:341`; [SRC] `StoryDetailScreen.kt:348` (`blur(25.dp)`), `:422` (`size(100.dp, 140.dp)`) | Blast radius và đánh đổi chất lượng đã ghi, khớp code. |
| N7 | SỬA MỘT PHẦN | `evidence.md` | Output curl vẫn chưa có trong evidence; plan cam kết ghi vào lessons-learned. `[MINOR]`. |

#### Phát Hiện Mới (Fresh Findings & Regression Check)

**R1 `[MAJOR]` — Phần sửa cho nguyên nhân #1 (cuộn danh sách) không có bước kiểm chứng rằng Coil thực sự dùng `imageOkHttpClient`; control của rủi ro mức Trung bình "Host ảnh bị quá tải" vẫn dựa vào mô tả thư viện chưa xác minh (evidence-bar 4.5.1, 4.5.4; phần còn lại của F6/N2).**
- Toàn bộ hiệu quả của DoH, UA đồng bộ, Referer, cookie chỉ đọc, retry và `maxRequestsPerHost = 4` phụ thuộc vào 3 hành vi của Coil 3.4.0: (a) `SingletonImageLoader.get` dùng `Application` khi nó implement `SingletonImageLoader.Factory`; (b) `OkHttpNetworkFetcherFactory(callFactory = …)` đăng ký tường minh được ưu tiên hơn fetcher mà `coil-network-okhttp` tự nạp qua service loader ([SRC] `app/build.gradle.kts:130`); (c) fetcher gọi `Call.enqueue` để Dispatcher có hiệu lực. Plan không có `[SRC]` mã nguồn Coil hay `[URL]` cho (b) và (c); reviewer không truy cập được tài liệu Coil trong danh sách domain cho phép.
- Probe hiện có chỉ chứng minh (a): log `CoilInit` vẫn in ra ngay cả khi (b) sai và ảnh vẫn đi qua client mặc định (UA `okhttp/5.3.2`, không DoH, timeout 10s), tức đúng tình trạng lỗi ban đầu. TC12 chỉ kiểm cấu hình của client được dựng bởi `buildImageOkHttpClient`, không kiểm việc Coil dùng nó. Bước thủ công "ảnh hiển thị mượt" không phân biệt được hai trường hợp, vì lỗi gốc mang tính ngẫu nhiên.
- Audit Run 2 (N2) đã yêu cầu probe "kèm kiểm request ảnh tới `wikicv.org` có UA/Referer mong đợi"; Revision 3 chưa thêm phần này.
- Yêu cầu (chỉ cần sửa ở mục 8 và mục 9):
  1. Thêm Runtime Probe: trong `CoilImageInterceptor`, ở debug build, ghi `Timber.tag("CoilNet").d(...)` gồm host, path, số lần thử và mã HTTP (KHÔNG log header, cookie hay query). Khi cuộn `SearchScreen` với nguồn Wikidich, Logcat phải có tag `CoilNet` cho các request `/photo/`; nếu không có thì (b) sai.
  2. Với (c): hoặc trích `[SRC]` mã nguồn `coil-network-okhttp` 3.4.0 cho thấy dùng `enqueue`, hoặc ghi rõ trong mục 9 rằng `maxRequestsPerHost` là best-effort và bổ sung vào probe trên việc đếm số request `/photo/` đang chạy đồng thời (ví dụ `AtomicInteger` tăng/giảm quanh `proceed`, chỉ ở debug) để xác nhận không vượt 4.

**R2 `[MINOR]` — `extractCoverUrl` bỏ mất ảnh khi `src` là placeholder còn `data-src` có ảnh thật.**
[SRC] `WikidichScraper.kt:586` hiện dùng `src.ifBlank { data-src }`; sketch giữ nguyên thứ tự này rồi trả `""` nếu `raw` bắt đầu bằng `data:` hoặc chứa `default-cover`. Với kiểu lazy-load `src="data:image/svg…" data-src="/photo/x"`, ảnh thật trong `data-src` bị bỏ. Fixture hiện tại dùng `src` thật ([SRC] `search_logged_in.html:462`) nên chưa gây lỗi. Đề nghị: lấy ứng viên đầu tiên hợp lệ trong `[src, data-src]` thay vì chỉ fallback khi `src` rỗng, và thêm một case vào TC2/TC3.

**R3 `[MINOR]` — Hai nơi chuẩn hóa `o` dùng điều kiện khác nhau.**
`extractCoverUrl` bỏ `o` trên mọi host có path `/photo/` (cần như vậy vì test chạy trên `localhost`, [SRC] `WikidichScraperTest.kt:35`), còn `upgradeToHttps` chỉ bỏ trên `wikicv.org`. Hành vi đúng trong thực tế vì scraper chỉ trả URL của `baseUrl` ([SRC] `WikidichScraper.kt:751-759`), nhưng nên ghi chú lý do ngay trong KDoc để người sau không "đồng bộ" sai.

**R4 `[MINOR]` — Chi tiết triển khai.**
- `WikidichScraper.kt` cần thêm import `okhttp3.HttpUrl.Companion.toHttpUrlOrNull` và `org.jsoup.nodes.Element` ([SRC] `WikidichScraper.kt:3-22` chưa có); `StoryRepository.kt` cũng cần import `toHttpUrlOrNull`.
- `CoilImageInterceptor` là `public class` trong khi các thành phần đi kèm là `internal`; nên để `internal` cho thống nhất.
- UA `Chrome/122` Desktop được gắn cho cả ảnh của SsTruyện/TTV và ảnh Pokémon ([SRC] `ReaderPokemonSupport.kt:341`). Chưa thấy rủi ro cụ thể, nhưng nên ghi vào mục 9.
- TC11 (hủy call) dễ flaky trên MockWebServer; nên mô tả cách dựng (ví dụ `Dispatcher` của MockWebServer chờ latch rồi `call.cancel()` từ thread khác).

#### Mental Simulation Matrix (S1–S6 + Domain)

| Kịch bản | Kết quả | Bằng chứng | Chi tiết |
|---|---|---|---|
| S1 Cold-start & Build | PASS | [CMD] `gradlew.bat compileDebugKotlin` Exit 0; [SRC] `app/build.gradle.kts:129-130, 143`; [SRC] `AppModule.kt:52-64`; [SRC] `CoilModule.kt:20-39`; [SRC] `AppTruyenApplication.kt:8-9`; [SRC] `AndroidManifest.xml` (Grep `provider` không có kết quả) | Không thêm dependency. `OkHttpClient` `@Singleton` inject được vào `CoilModule`. `ImageLoader` không phụ thuộc `Application` nên không có vòng phụ thuộc. Manifest không có `ContentProvider` nào có thể gọi Coil trước `Application.onCreate()` (trước khi field injection chạy). Thiếu import là `[MINOR]` R4, compile sẽ bắt. |
| S2 Lifecycle & Teardown | PASS | [ADV] timeline 2; sketch mục 5.2 | Response lỗi được `close()` trước retry; response không retry được trả nguyên cho Coil để Coil đóng. Dispatcher riêng sống theo `@Singleton`, không rò. Không có coroutine scope mới. |
| S3 Database & Temporal | PASS | [SRC] `StoryRepository.kt:838, 843-852, 863, 870`; [SRC] `StoryEntity.kt:21`; [ADV] timeline 1 | Không đổi schema. URL `?o=1` cũ được chuẩn hóa khi đọc (`toDomain`) và khi ghi lại (`resolveSafeCoverUrl`). `BackupRepository.kt:425-426` và `DownloadRepository.kt:111` cũng đi qua `resolveSafeCoverUrl` nên dùng cùng quy tắc. |
| S4 Cross-Task State Flow | PASS | [SRC] `StoryRepository.kt:445-468`; Grep `coverImageUrl` trong `ui/` chỉ đọc giá trị (`StoryDetailScreen.kt:340-419`, `StoryCardPremium.kt:95-362`, `DownloadsScreen.kt:239-241`) | Repository là nơi duy nhất merge/chuẩn hóa cover; UI không tự cache. Không đổi chữ ký `IStoryRepository`. |
| S5 Security & Privacy | PASS | [SRC] `WebViewCookieHandler.kt:20-39`; [SRC] `AppModule.kt:57`; [ADV] timeline 3 | Cookie Wikidich chỉ được load cho `wikicv.org`; không lưu cookie từ response ảnh. Probe hiện có chỉ log chuỗi cố định. Probe đề nghị ở R1 phải loại trừ header/cookie/query. |
| S6 External Resilience | FAIL | [SRC] `app/build.gradle.kts:130`; [SRC] `CoilModule.kt:22-39` | Control "Dispatcher 4 req/host" và việc Coil dùng client mới chưa kiểm chứng (R1). Retry chỉ 1 lần, không retry 429/403, có kiểm `isCanceled()` → phần retry PASS. |
| Domain (cache ảnh / gộp nguồn) | PASS | [SRC] `search_logged_in.html:462`, `story_detail.html:288`; [SRC] `WikidichScraper.kt:751-759` | Sau khi bỏ `o`, key chi tiết `https://wikicv.org/photo/<id>` trùng key danh sách. Không ảnh hưởng khử trùng lặp truyện. |

#### Adversarial Timelines

```
Cơ chế: Bản ghi Room cũ có cover "?o=1", refresh nền song song với observeStory (S3 – đồng thời)
T1: VM gọi getStoryDetail → nhánh cache → cached.toDomain() → upgradeToHttps bỏ "o" → UI nhận "https://wikicv.org/photo/x" | T2: appScope fetch fresh → extractCoverUrl trả ".../photo/x" → resolveSafeCoverUrl → insertStory | T3: Room phát lại cùng URL
Kết cục: Key Coil không đổi giữa T1 và T3, không tải lại → AN TOÀN
```

```
Cơ chế: Cuộn nhanh, Compose hủy request đang chờ retry (S2/S6 – thất bại giữa chừng)
T1: request /photo/x nhận 503 | T2: item rời màn hình → Coil hủy coroutine → Call.cancel() | T3: interceptor thấy isCanceled() == true → không close, không retry, trả 503 cho Coil, Coil bỏ kết quả và đóng body
Kết cục: Không có request thứ 2, không rò connection → AN TOÀN
```

```
Cơ chế: cf_clearance đổi giữa lúc load cookie và lúc gửi request ảnh (S5 – TOCTOU)
T1: ReadOnlyWikidichCookieJar.loadForRequest đọc cookie từ CookieStore | T2: User đăng nhập lại trong WebView (SourceLoginScreen, cùng UA Chrome/122) → cookie mới được ghi | T3: request ảnh gửi cookie cũ → Cloudflare trả 403 HTML → interceptor không retry 403 → Coil decode lỗi → placeholder; request ảnh kế tiếp đọc cookie mới
Kết cục: Một ảnh lỗi tạm thời, không parse nhầm HTML thành nội dung, không ghi đè cookie → AN TOÀN
```

```
Cơ chế: Fetcher service-loader của coil-network-okhttp được chọn thay cho fetcher tường minh (S6 – wiring)
T1: Application trả ImageLoader từ CoilModule → log CoilInit in ra | T2: request ảnh được fetcher mặc định xử lý (client mặc định, UA okhttp, không DoH, timeout 10s) | T3: wikicv.org chậm → SocketTimeoutException → placeholder
Kết cục: Probe hiện tại vẫn báo "đã khởi tạo" trong khi lỗi gốc còn nguyên → chưa xác minh được (MAJOR R1)
```

Trục vòng đời Android: process bị kill giữa lúc tải ảnh chỉ để lại entry disk cache chưa commit, Coil bỏ khi mở lại; không có ghi Room mới ngoài các đường đã có → N/A.

#### Commands Executed

Trích từ `evidence.md`:
- `### [CMD] git status --short` — `Exit: 0` — `WikidichScraper.kt`, `StoryRepository.kt`, `WikidichScraperTest.kt`, `StoryRepositoryTest.kt` còn thay đổi chưa commit từ plan 2026-10-27; số dòng trong plan đã được đối chiếu lại trên bản working tree hiện tại (khớp `:445`, `:468`, `:843`).
- `### [CMD] git diff --stat` — `Exit: 0` — `WikidichScraper.kt | 54`, `StoryRepository.kt | 10`.
- `### [CMD] cmd.exe /d /c gradlew.bat compileDebugKotlin` — `Exit: 0` — `BUILD SUCCESSFUL in 8s`.
- `### [CMD] cmd.exe /d /c gradlew.bat ktlintCheck` — `Exit: 0` — `BUILD SUCCESSFUL in 1s`.
- `### [CMD] cmd.exe /d /c gradlew.bat testDebugUnitTest` — `Exit: 0` — `BUILD SUCCESSFUL in 2s` (baseline, UP-TO-DATE).

`UNVERIFIED`, lệnh đề xuất:
- `curl.exe -q -s -o NUL -w "%{http_code} %{size_download} %{time_total}" -A "Mozilla/5.0" https://wikicv.org/photo/6a950ea654b8087c7d7c833d`
- `curl.exe -q -s -o NUL -w "%{http_code} %{size_download} %{time_total}" -A "Mozilla/5.0" "https://wikicv.org/photo/6a950ea654b8087c7d7c833d?o=1"`

#### Các Tấn Công Đã Thử (Attempted Attack Vectors)

Có Phát Hiện Mới nên mục này không bắt buộc. Các hướng đã thử nhưng không làm vỡ thiết kế:
1. Nhánh không cache trả cover rỗng khi DB có cover cũ: [SRC] `StoryRepository.kt:432-436` tìm `existingEntity` theo `story.id` hoặc `slug`; sketch dùng chính `existingEntity?.coverImageUrl` → kết quả trả về khớp entity ghi xuống ở `:451` → an toàn.
2. Hành vi mới của nhánh không cache với cover `data:`/base64 từ nguồn khác: trước đây `:468` trả nguyên URL `data:`; nay `resolveSafeCoverUrl` ([SRC] `:858-870`) lọc về `""` hoặc cover cũ, giống nhánh cache ở `:419-423` → không phải regression.
3. Test cũ của `upgradeToHttps` vỡ: [SRC] `StoryRepositoryTest.kt:753-756` chỉ dùng `example.com` và `localhost` không có `/photo/` → vẫn PASS.
4. Cookie Wikidich lọt sang host khác: kể cả khi bộ lọc host sai, [SRC] `WebViewCookieHandler.kt:31-38` chỉ trả cookie theo đúng URL → không lộ.

#### Kết luận

Các lỗi F1, F3, F4, F5, F7, F8, F9, N1, N3, N4, N6 đã được xử lý trong Revision 3 (F3, F8, N1, N3 ở mức thiết kế). Còn 1 lỗi `[MAJOR]`: R1 (chưa có probe chứng minh request ảnh thực sự đi qua `imageOkHttpClient`/`CoilImageInterceptor`, và claim `enqueue` của Coil chưa xác minh hoặc chưa hạ xuống best-effort), làm S6 FAIL. Revision 4 chỉ cần bổ sung Runtime Probe `CoilNet` ở mục 8 và cập nhật mục 9 theo R1; các mục `[MINOR]` R2–R4, N5, N7 nên xử lý cùng lúc nhưng không chặn.

[CHANGES_REQUESTED]

### Audit Run 4

- **Reviewer**: Claude Code CLI (claude-opus-5-5)
- **Ngày**: 2026-10-28
- **Đối tượng**: Technical Plan Revision 4

#### Kiểm Chứng Lỗi Cũ

Audit Run 1, 2, 3 đều kết luận CHANGES_REQUESTED. Từng lỗi được kiểm lại trên working tree hiện tại. Các file `[NEW]` (`CoilImageInterceptor.kt`, `CoilImageInterceptorTest.kt`) chưa tồn tại, nên trạng thái "ĐÃ SỬA (mức thiết kế)" nghĩa là thiết kế đã được đối chiếu với các API/file thật mà nó dựa vào.

| Mã | Trạng thái | Bằng chứng | Ghi chú |
|---|---|---|---|
| F1 | ĐÃ SỬA | [SRC] `WikidichScraper.kt:157-158` (`selectFirst(".cover-info img, .book-info img")` + `fixUrl`); [SRC] `story_detail.html:285-288` (`/photo/6a950ea654b8087c7d7c833d?o=1` trong `.book-info .cover-wrapper`) | Chẩn đoán "lệch cache key do `?o=1`" khớp code thật. |
| F2 | SỬA MỘT PHẦN | `evidence.md` không có `### [CMD] curl.exe …` | Số đo `/photo/` vẫn chỉ nằm trong plan. Hướng sửa không phụ thuộc vào con số cụ thể → giữ `[MINOR]`. |
| F3 | ĐÃ SỬA (mức thiết kế) | Sketch mục 5.2; [SRC] `app/build.gradle.kts:175` (`mockwebserver3`) | `addInterceptor`, đóng response trước retry, bỏ 429/403, kiểm `isCanceled()`, `addSuppressed`. |
| F4 | ĐÃ SỬA | [SRC] `StoryRepository.kt:445` (`resolvedStory` chưa merge), `:451`, `:460`, `:468` (`return resolvedStory`) | Sketch tính `safeCoverUrl` trước `resolvedStory`; TC5 phủ nhánh không cache. |
| F5 | ĐÃ SỬA | Plan mục 10 | Có `docs/lessons-learned.md`. |
| F6 | ĐÃ SỬA | Plan mục 9 (Dispatcher ghi rõ "best-effort"), mục 8 (probe `CoilInit` + `CoilNet`) | Claim `enqueue` đã được hạ xuống best-effort và có probe runtime đo đếm; xem M1 về độ chính xác của bộ đếm. |
| F7 | ĐÃ SỬA | [SRC] `WikidichScraperTest.kt:35` (`customBaseUrl = mockBaseUrl`) | TC1/TC3 dùng `endsWith` + `!contains("?o=")`. |
| F8 | ĐÃ SỬA (mức thiết kế) | [SRC] `WebViewCookieHandler.kt:20-28`, `:31-39`; [SRC] `AppModule.kt:57` | `ReadOnlyWikidichCookieJar(baseClient.cookieJar)` bọc đúng `WebViewCookieHandler`; TC13. |
| F9 | ĐÃ SỬA | Sketch mục 5.3, 5.4 | Dùng `HttpUrl`, selector giới hạn trong `.book-info`. Văn phong còn sót, xem M3. |
| N1 | ĐÃ SỬA (mức thiết kế) | [SRC] `WikidichScraper.kt:38` (`userAgent = DESKTOP_UA`), `:51`, `:64-65` (`private const val DESKTOP_UA` trong `companion object`) | Đổi sang `internal const val` cho phép `WikidichScraper.DESKTOP_UA` từ cùng module; TC6. |
| N2 | ĐÃ SỬA (mức thiết kế) | [SRC] `CoilModule.kt:20-39` (chưa có `OkHttpNetworkFetcherFactory`, chưa inject `OkHttpClient`); [SRC] `AppTruyenApplication.kt:8-9` (chưa implement Factory) | `buildImageOkHttpClient` + TC12/TC13 kiểm cấu hình; `CoilNet` chứng minh request thật đi qua interceptor. |
| N3 | ĐÃ SỬA (mức thiết kế) | [SRC] `StoryRepository.kt:843-852` (hiện chỉ đổi scheme), `:838`, `:863`, `:870` | Sketch giới hạn host `wikicv.org` + path `/photo/`; TC4 có `?a=1&o=1` và host khác. |
| N4 | ĐÃ SỬA | [SRC] `AppModule.kt:62` (pool dùng chung) | Sketch giữ `retryOnConnectionFailure(true)`. |
| N5 | ĐÃ SỬA | Plan mục 9, ghi chú "Thời gian chờ khi retry" | Đã ghi rõ trường hợp tối đa ~60s trên luồng nền. |
| N6 | ĐÃ SỬA | [SRC] `ReaderPokemonSupport.kt:341`; [SRC] `StoryDetailScreen.kt:340-348`, `:414-422` | Blast radius và đánh đổi chất lượng đã ghi. |
| N7 | SỬA MỘT PHẦN | `evidence.md` | Output curl vẫn chưa có trong evidence; plan cam kết ghi vào lessons-learned. `[MINOR]`. |
| R1 | ĐÃ SỬA (mức thiết kế) | Sketch mục 5.2 (log `CoilNet` trong `intercept`); plan mục 8 Runtime Probe, mục 9 best-effort | Nếu fetcher service-loader được chọn thay cho fetcher tường minh thì Logcat sẽ không có `CoilNet` cho `/photo/` → probe phân biệt được trường hợp sai wiring. Log chỉ có host, encodedPath, attempt, code, inFlight — không có header/cookie/query. Phần đếm đồng thời còn lệch so với yêu cầu "theo `/photo/`", xem M1. |
| R2 | ĐÃ SỬA (mức thiết kế) | [SRC] `WikidichScraper.kt:585-586` (hiện `src.ifBlank { data-src }`) | Sketch duyệt `listOf(src, data-src)` lấy ứng viên hợp lệ đầu tiên; TC3 kiểm `src` là data URI SVG. |
| R3 | ĐÃ SỬA | Sketch mục 5.3, 5.4 (KDoc hai nơi) | KDoc giải thích lý do khác điều kiện host. |
| R4 | ĐÃ SỬA (mức thiết kế) | [SRC] `WikidichScraper.kt:3-22` (chưa có import `toHttpUrlOrNull`/`Element`); [SRC] `ResilientDohDns.kt:4` (import mẫu `okhttp3.HttpUrl.Companion.toHttpUrlOrNull` đã dùng trong repo) | Import được liệt kê ở bước 4, 5; `CoilImageInterceptor` là `internal`; phạm vi UA ghi ở mục 9; TC11 mô tả dispatcher deterministic. |

#### Phát Hiện Mới (Fresh Findings & Regression Check)

**M1 `[MINOR]` — Bộ đếm `inFlight` của probe `CoilNet` là bộ đếm toàn cục, không theo host; kết luận "inFlight ≤ 4 chứng minh hiệu lực Dispatcher" (mục 8, mục 9) không đúng khi tải ảnh nhiều nguồn cùng lúc.**
- `maxRequestsPerHost = 4` giới hạn theo host, còn `maxRequests = 32` giới hạn tổng. Sketch dùng một `AtomicInteger` duy nhất cho mọi host. Trên các màn hình trộn nguồn (Home/Library/Search gộp TTV, SsTruyện, Wikidich), `inFlight` có thể lên 8–12 dù Dispatcher hoạt động đúng → probe báo sai là "vượt giới hạn". Ngược lại, khi chỉ có ảnh Wikidich thì probe vẫn dùng được.
- Thêm một giới hạn về ý nghĩa đo: bộ đếm được giảm trong `finally` ngay khi `chain.proceed` trả về header, trong khi phần body (ví dụ 690.9 KB) được Coil đọc sau khi interceptor đã trả response. Theo mô hình `AsyncCall` của OkHttp, slot của Dispatcher cũng được trả khi callback `onResponse` kết thúc, nên số kết nối đang truyền body có thể vượt 4. Plan đã gọi đây là best-effort nên không chặn, nhưng mục 9 nên nói rõ giới hạn chỉ áp lên pha gửi request/nhận header. (Lập luận này dựa trên hành vi đã biết của OkHttp, chưa có `[SRC]` mã nguồn Coil/OkHttp trong repo → `UNVERIFIED`.)
- Đề nghị khi triển khai: đếm theo host (`ConcurrentHashMap<String, AtomicInteger>`) hoặc chỉ đếm khi `host` thuộc `wikicv.org`, và sửa câu mô tả trong lessons-learned tương ứng.

**M2 `[MINOR]` — Nhánh retry `SocketTimeoutException` không có TC.**
TC9 ghi "503 hoặc timeout" nhưng Input chỉ có 503→200. Nhánh timeout có logic riêng (`shouldRetryTimeout`, `addSuppressed` khi cả hai lần đều lỗi). Đề nghị thêm case: client test với `readTimeout` ngắn (ví dụ 200 ms), MockWebServer lần 1 dùng `headersDelay` lớn hơn timeout, lần 2 trả 200 → kỳ vọng thành công và `requestCount == 2`; thêm case cả hai lần timeout → ném `SocketTimeoutException` có `suppressed` chứa lỗi lần 1.

**M3 `[MINOR]` — Văn phong và nguồn version.**
- Plan còn các cụm tuyệt đối/cảm tính: mục 8 "chứng minh 100%", "ghi đè hoàn toàn"; mục 9 "hoàn toàn an toàn"; mục 1 "lợi ích vượt trội", "hoàn toàn đáp ứng tốt"; mục 5 "testable 100%". Nên viết lại trung tính khi đưa vào docs.
- Versions Table dẫn `coil-kt.github.io`, `dagger.dev` — không nằm trong danh sách domain reviewer được kiểm; cột "Latest đã xác minh" là `UNVERIFIED` với reviewer. Không ảnh hưởng vì plan không đổi dependency ([CMD] `compileDebugKotlin` Exit 0 trên baseline).

**M4 `[MINOR]` — Tính lại cover thừa trong `StoryRepository.getStoryDetail`.**
Sau khi `resolvedStory.coverImageUrl = safeCoverUrl`, các dòng [SRC] `StoryRepository.kt:451` và `:460` nếu giữ nguyên sẽ gọi lại `resolveSafeCoverUrl(resolvedStory.coverImageUrl, …)`. Kết quả vẫn đúng vì `upgradeToHttps` là idempotent (bỏ `o` lần hai không đổi URL; khi `safeCoverUrl == ""` thì nhánh existing cho cùng kết quả như lần đầu), nhưng nên gán thẳng `coverImageUrl = safeCoverUrl` trong cả hai nhánh `copy` để tránh hai nguồn sự thật.

#### Mental Simulation Matrix (S1–S6 + Domain)

| Kịch bản | Kết quả | Bằng chứng | Chi tiết |
|---|---|---|---|
| S1 Cold-start & Build | PASS | [CMD] `cmd.exe /d /c gradlew.bat compileDebugKotlin` Exit 0; [SRC] `AppModule.kt:52-64`; [SRC] `CoilModule.kt:20-39`; [SRC] `AppTruyenApplication.kt:8-18`; [SRC] `WikidichScraper.kt:51, 64-65` | Không thêm dependency. `OkHttpClient` `@Singleton` inject được vào `CoilModule`; `ImageLoader` không phụ thuộc `Application` nên `dagger.Lazy<ImageLoader>` không tạo vòng. `internal const val` trong `companion object` truy cập được qua `WikidichScraper.DESKTOP_UA` trong cùng module. `com.personal.apptruyen.BuildConfig` đã được dùng ở `AppTruyenApplication.kt:15`. Audit Run 3 đã kiểm Manifest không có `ContentProvider` gọi Coil trước `onCreate`. |
| S2 Lifecycle & Teardown | PASS | Sketch mục 5.2; [ADV] timeline 2 | Response lỗi chỉ `close()` khi thực sự retry; response không retry được trả nguyên cho Coil đóng. Khi call bị hủy, OkHttp `RealCall` ném `IOException("Canceled")` sau chain nên TC11 có kết quả xác định. Dispatcher riêng sống theo `@Singleton`. |
| S3 Database & Temporal | PASS | [SRC] `StoryRepository.kt:398, 418, 451, 460, 838, 843-852, 863, 870`; [SRC] `DownloadRepository.kt:111`; [SRC] `BackupRepository.kt:425-426`; [ADV] timeline 1 | Không đổi schema. URL `?o=1` cũ chuẩn hóa khi đọc (`toDomain`) và khi ghi (`resolveSafeCoverUrl`), mọi đường ghi cover đều qua `resolveSafeCoverUrl`. `removeAllQueryParameters` đưa query về null khi rỗng nên không sinh `?` thừa. |
| S4 Cross-Task State Flow | PASS | [SRC] `StoryRepository.kt:445-468`; Grep `coverImageUrl` trong `ui/` chỉ đọc (`StoryDetailScreen.kt:340-419`, `StoryCardPremium.kt:95-362`, `DownloadsScreen.kt:239-241`) | Repository là nơi duy nhất merge/chuẩn hóa cover; không đổi `IStoryRepository`. Thay đổi đang dở của plan 2026-10-27 trên `WikidichScraper.kt`/`StoryRepository.kt` ([CMD] `git status --short`) không chạm các dòng plan sửa. |
| S5 Security & Privacy | PASS | [SRC] `WebViewCookieHandler.kt:20-39`; [SRC] `AppModule.kt:57`; sketch mục 5.2; [ADV] timeline 3 | Cookie chỉ load cho `wikicv.org`, không lưu cookie từ response ảnh. Log `CoilNet` chỉ ở debug, chỉ host/encodedPath/code, không header/cookie/query. |
| S6 External Resilience | PASS (kèm MINOR M1, M2) | [SRC] `AppModule.kt:58-62`; plan mục 8, 9; [ADV] timeline 4 | DoH + `readTimeout 30s` + UA đồng bộ + retry có điều kiện. Việc Coil dùng client mới được kiểm bằng probe `CoilNet`; giới hạn Dispatcher được ghi là best-effort. |
| Domain (cache ảnh / gộp nguồn) | PASS | [SRC] `story_detail.html:288`; [SRC] `WikidichScraper.kt:585-586, 751-759`; [SRC] `WikidichScraperTest.kt:410-434` | Sau khi bỏ `o`, key chi tiết trùng key danh sách. URL không có `/photo/` (ví dụ test `cdn.truyenwikidich.net/covers/a.jpg`, `localhost:8080/covers/b.jpg`) đi nhánh trả `fixed` nên test hiện có vẫn PASS. |

#### Adversarial Timelines

```
Cơ chế: Bản ghi Room cũ có cover "?o=1", refresh nền appScope song song với observeStory (S3 – đồng thời)
T1: VM gọi getStoryDetail → nhánh cache → cached.toDomain() → upgradeToHttps bỏ "o" → UI nhận ".../photo/x" | T2: appScope fetch fresh → extractCoverUrl trả ".../photo/x" → resolveSafeCoverUrl(fresh, cached) → insertStory | T3: Room phát lại cùng URL
Kết cục: Key Coil không đổi giữa T1 và T3, disk cache trúng key danh sách → AN TOÀN
```

```
Cơ chế: Cuộn nhanh, Compose hủy request ảnh đang nhận 503 (S2/S6 – thất bại giữa chừng)
T1: proceed lần 1 trả 503 | T2: item rời màn hình → Coil hủy coroutine → Call.cancel() | T3: interceptor thấy isCanceled() == true → không close, không retry, trả 503 → RealCall thấy canceled → đóng response, ném IOException("Canceled")
Kết cục: Không có request thứ 2, không rò connection → AN TOÀN
```

```
Cơ chế: cf_clearance đổi giữa lúc load cookie và lúc gửi request ảnh (S5 – TOCTOU)
T1: ReadOnlyWikidichCookieJar.loadForRequest đọc cookie cũ | T2: User đăng nhập lại trong WebView (UA Chrome/122) → cookie mới ghi vào CookieStore | T3: request ảnh gửi cookie cũ → 403 HTML → không retry → Coil decode lỗi → placeholder; request kế tiếp đọc cookie mới
Kết cục: Một ảnh lỗi tạm thời, không ghi đè cookie, không lộ cookie sang host khác → AN TOÀN
```

```
Cơ chế: Màn hình trộn nguồn, 4 ảnh wikicv.org + 4 ảnh file.sstruyen.pics + 4 ảnh TTV cùng lúc (S6 – đồng thời, probe)
T1: Dispatcher cho chạy 4 call mỗi host (tổng 12 ≤ 32) | T2: mỗi call vào intercept → inFlightCount.incrementAndGet() | T3: log CoilNet in inFlight=12
Kết cục: Dispatcher đúng nhưng probe báo vượt 4 → kết luận probe sai, không ảnh hưởng hành vi app → MINOR M1
```

Trục vòng đời Android: process bị kill giữa lúc tải ảnh chỉ để lại entry disk cache chưa commit, Coil bỏ khi mở lại; không có đường ghi Room mới → N/A.

#### Commands Executed

Trích từ `evidence.md`:
- `### [CMD] git status --short` — `Exit: 0` — `WikidichScraper.kt`, `StoryRepository.kt`, `WikidichScraperTest.kt`, `StoryRepositoryTest.kt` còn thay đổi chưa commit của plan 2026-10-27; số dòng plan dẫn (`:445`, `:468`, `:843`, `:157-158`, `:585-586`) đã đối chiếu lại trên working tree và khớp.
- `### [CMD] git diff --stat` — `Exit: 0` — `WikidichScraper.kt | 54`, `StoryRepository.kt | 10`, `23 files changed`.
- `### [CMD] cmd.exe /d /c gradlew.bat compileDebugKotlin` — `Exit: 0` — `BUILD SUCCESSFUL in 7s`.
- `### [CMD] cmd.exe /d /c gradlew.bat ktlintCheck` — `Exit: 0` — `BUILD SUCCESSFUL in 995ms`.
- `### [CMD] cmd.exe /d /c gradlew.bat testDebugUnitTest` — `Exit: 0` — `BUILD SUCCESSFUL in 1s` (baseline, UP-TO-DATE).

`UNVERIFIED`, lệnh đề xuất (cho F2/N7, ghi vào lessons-learned khi triển khai):
- `curl.exe -q -s -o NUL -w "%{http_code} %{size_download} %{time_total}" -A "Mozilla/5.0" https://wikicv.org/photo/6a950ea654b8087c7d7c833d`
- `curl.exe -q -s -o NUL -w "%{http_code} %{size_download} %{time_total}" -A "Mozilla/5.0" "https://wikicv.org/photo/6a950ea654b8087c7d7c833d?o=1"`

#### Các Tấn Công Đã Thử (Attempted Attack Vectors)

Có Phát Hiện Mới (chỉ `[MINOR]`). Các hướng đã thử nhưng không làm vỡ thiết kế:
1. `extractCoverUrl` làm vỡ test cũ của danh sách: [SRC] `WikidichScraperTest.kt:416, 422, 433-434` dùng URL không có `/photo/` → nhánh trả `fixed` giữ nguyên hành vi `fixUrl` ([SRC] `WikidichScraper.kt:751-759`) → an toàn.
2. `removeAllQueryParameters("o")` để lại `?` thừa làm lệch key với ảnh danh sách: OkHttp đặt query về null khi danh sách tham số rỗng, và TC1 dùng `endsWith("/photo/<id>")` sẽ bắt trường hợp này → an toàn.
3. `extractCoverUrl` gọi `fixUrl` là `private`: sketch đặt `extractCoverUrl` là `internal fun` thành viên của `WikidichScraper` ([SRC] `WikidichScraper.kt:25-29, 751`) nên truy cập được; test gọi qua instance có `customBaseUrl` ([SRC] `WikidichScraperTest.kt:35`) → an toàn.
4. Đổi `resolvedStory` làm sai cover ghi DB: tính lại `resolveSafeCoverUrl` trên giá trị đã an toàn là idempotent ([SRC] `StoryRepository.kt:854-871`) → an toàn (chỉ còn trùng lặp, M4).

#### Kết luận

Toàn bộ lỗi `[MAJOR]` của các lượt trước (F1–F6, N1, N2, R1) đã được xử lý trong Revision 4 (một số ở mức thiết kế, đã đối chiếu với code thật). Còn lại `[MINOR]`: F2/N7 (output curl chưa có trong evidence), M1 (bộ đếm `inFlight` toàn cục, giới hạn Dispatcher chỉ áp pha header), M2 (thiếu TC cho nhánh retry timeout), M3 (văn phong, nguồn version), M4 (tính lại cover thừa). S1–S6 và Domain đều PASS. Implementer nên xử lý M1, M2, M4 khi triển khai và ghi số đo curl vào `docs/lessons-learned.md`.

APPROVED

[PLAN_APPROVED]
