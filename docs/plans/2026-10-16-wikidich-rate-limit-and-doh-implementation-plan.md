# Kế hoạch triển khai: Điều tiết tốc độ tải chương Wikidich (Anti-Cloudflare) & DNS-over-HTTPS (ResilientDohDns)

> **Trạng thái**: Completed
> **Revision**: 1
> **Ngày tạo**: 2026-10-16
> **Planner**: @planner (gemini-3.8-flash)

## Technical Plan

### 1. Mục Tiêu

Triển khai kết hợp 2 cải tiến độ bền mạng và chống chặn cho ứng dụng:

1. **Mục tiêu 1 (A1) — Điều tiết tốc độ tải chương riêng cho nguồn Wikidich (`wikidich` / `wikicv.org`)**:
   - Máy chủ `wikicv.org` áp dụng cơ chế chống cào dữ liệu rất gắt (throttle giữ kết nối ~12 giây và Cloudflare chặn/rate-limit bằng HTTP `403`, `429`, `503` khi phát hiện các luồng tải song song hoặc request dồn dập).
   - Thiết lập chính sách tải riêng cho nguồn `wikidich` trong `DownloadRepository`:
     - **Tải tuần tự từng chương một (`WIKIDICH_BATCH_SIZE = 1`)** thay vì 4 chương/lô.
     - **Nghỉ ngẫu nhiên `15–20 giây/chương` (`WIKIDICH_MIN_DELAY_MS = 15_000L`, `WIKIDICH_MAX_DELAY_MS = 20_000L`)** giữa các chương liên tiếp thông qua `wikidichDelayProvider` (có thể inject hàm cố định trong unit test để kiểm thử thời gian ảo deterministic).
     - **Dừng sớm (Fail-Fast, không retry) khi gặp mã HTTP `403`, `429`, `503`**: Trong `fetchChapterWithRetry`, nếu `source.sourceId == WIKIDICH_SOURCE_ID` và lỗi là `ScraperException.HttpException` với `statusCode in RATE_LIMIT_HTTP_CODES` (`setOf(403, 429, 503)`), trả về lỗi ngay ở lần thử đầu tiên mà không retry. Trong `downloadChapters`, gán `fatalError` và ném ra khỏi `Flow` ngay sau khi hoàn tất lô hiện tại để dừng toàn bộ các chương kế tiếp, tránh đốt thêm hạn mức IP/phiên trên Cloudflare.
     - Nếu gặp lỗi mạng tạm thời khác trên Wikidich (như `SocketTimeoutException`), thời gian chờ trước lần thử thứ 2 là `WIKIDICH_RETRY_DELAY_MS = 5_000L`.
     - **Giữ nguyên 100% tốc độ tải song song 4 chương/lô (`DOWNLOAD_BATCH_SIZE = 4`, `BASE_BATCH_DELAY_MS = 250L`) và cơ chế `MAX_CONSECUTIVE_FAILED_BATCHES = 3` cho các nguồn khác (`sstruyen`, `tangthuvien`)**.
   - Bổ sung `delay(crawlDelayMs)` (`300ms`) giữa các trang phân trang AJAX danh sách chương trong `WikidichScraper.getRemainingChapters()`.
   - Gia hạn `WakeLock` (`renewWakeLock()`, `setReferenceCounted(false)`) trên mỗi tiến độ chương trong `DownloadService.kt` để không hết hạn `WakeLock` khi tải các bộ truyện dài của Wikidich, đồng thời hiển thị thông báo tiếng Việt rõ ràng khi dừng do giới hạn tốc độ: `"Đã tải $downloaded chương. Nguồn truyện đang giới hạn tốc độ truy cập (Cloudflare), vui lòng thử lại sau ít phút."`.

2. **Mục tiêu 2 (A2) — Vượt chặn DNS trên WiFi công ty bằng DNS-over-HTTPS (`ResilientDohDns`)**:
   - Nhiều mạng WiFi công ty hoặc ISP chặn truy vấn DNS truyền thống (UDP/TCP cổng 53) tới các tên miền nguồn truyện (`wikicv.org`, `sstruyen.pics`, `api.sstruyen.pics`, `file.sstruyen.pics`, `tangthuvien.org`) bằng cách trả về `NXDOMAIN` hoặc IP sinkhole (`0.0.0.0`, `127.0.0.1`, IP nội bộ).
   - Xây dựng `ResilientDohDns` cài đặt giao diện `okhttp3.Dns` (không cần thêm dependency mới vào `app/build.gradle.kts`, tận dụng `okhttp3.OkHttpClient` và `org.json.JSONObject` có sẵn):
     - Truy vấn DNS-over-HTTPS (JSON API RFC 8427 `application/dns-json`) trực tiếp qua địa chỉ IP literal của **Cloudflare (`https://1.1.1.1/dns-query`, `https://1.0.0.1/dns-query`)** và **Google (`https://8.8.8.8/resolve`)**, không phụ thuộc vào DNS hệ thống để phân giải tên miền máy chủ DoH.
     - Bảo toàn `hostname` gốc trên đối tượng `InetAddress` thông qua `InetAddress.getByAddress(hostname, rawBytes)` để tính năng TLS SNI (Server Name Indication) và xác minh chứng chỉ HTTPS của OkHttp hoạt động chính xác 100%.
     - Lọc bỏ các địa chỉ IP sinkhole/bogon (`0.0.0.0`, loopback `127.0.0.1`, link-local, site-local `10.x.x.x`/`192.168.x.x`, multicast) trong phản hồi DNS.
     - Cache kết quả phân giải hợp lệ trong `ConcurrentHashMap` với TTL `10 phút` (`DEFAULT_CACHE_TTL_MS = 600_000L`), hỗ trợ dùng lại bản ghi cache cũ (stale-on-error) nếu mạng chập chờn tạm thời.
     - Tự động bỏ qua DoH cho `"localhost"`, `"127.0.0.1"`, `"::1"`, `"[::1]"` và mọi chuỗi địa chỉ IP literal (trả về ngay `systemDns.lookup(hostname)`) nhằm bảo toàn 100% tốc độ và tính cô lập của `MockWebServer` trong toàn bộ unit test.
     - Tự động fallback về `systemDns.lookup(hostname)` nếu cả 3 endpoint DoH đều không thể kết nối (ví dụ mạng nội bộ chặn cổng 443 tới `1.1.1.1`/`8.8.8.8`).
   - Gắn `.dns(ResilientDohDns())` vào `OkHttpClient` dùng chung trong `di/AppModule.kt`.

---

### 2. Pre-Work Reading

Đã đọc và đối chiếu đầy đủ các tài liệu quy tắc, kiến trúc và mã nguồn thực tế theo Pre-Work Protocol (`.agents/rules/02-quality.md`):

- **Tài liệu quy tắc & kiến trúc**:
  - `AGENTS.md`, `.agents/rules/00-core.md`, `.agents/rules/01-coding.md`, `.agents/rules/02-quality.md`, `.agents/rules/04-scraping.md`, `.agents/rules/05-plan-review.md`, `.agents/rules/06-docs.md`.
  - `.agents/agents/reviewer/mental-simulation.md`, `.agents/agents/reviewer/evidence-bar.md`.
  - `docs/architecture.md`, `docs/progress.md`, `docs/lessons-learned.md` (đặc biệt bài học ngày `2026-10-14` về cơ chế throttle 12 giây của `wikicv.org`), `docs/scraping.md`, `docs/plans/README.md`, `docs/plans/2026-10-14-parallel-chapter-download-implementation-plan.md`.
- **File source & test đã khảo sát**:
  - `app/build.gradle.kts` (dòng 1–202: `minSdk = 26`, `okhttp:5.3.2`, `mockwebserver3:5.3.2`, `org.json:json:20260522`, `kotlinx-coroutines-test:1.11.0`).
  - `app/src/main/java/com/personal/apptruyen/data/repository/DownloadRepository.kt` (dòng 1–309: `fetchChapterWithRetry`, `downloadChapters`, `companion object`).
  - `app/src/main/java/com/personal/apptruyen/download/DownloadService.kt` (dòng 1–401: `buildCompletionText`, `startDownload`, `sendCompletionNotification`, `acquireWakeLock`, `releaseWakeLock`).
  - `app/src/main/java/com/personal/apptruyen/data/remote/WikidichScraper.kt` (dòng 1–618: `sourceId = "wikidich"`, `getRemainingChapters` tại dòng 225–260, `getChapterContent` tại dòng 262–292).
  - `app/src/main/java/com/personal/apptruyen/data/remote/BaseScraper.kt` (dòng 1–347: `crawlDelayMs = 300L` tại dòng 33, `fetchDocument` ném `ScraperException.HttpException` tại dòng 140 & 150).
  - `app/src/main/java/com/personal/apptruyen/data/remote/ScraperException.kt` (dòng 1–82: `HttpException(val statusCode: Int, val url: String)`, `NetworkException`, `LoginRequiredException`, `CircuitOpenException`, `toScraperException`).
  - `app/src/main/java/com/personal/apptruyen/data/remote/StorySource.kt` (dòng 1–100: `matchesUrl`).
  - `app/src/main/java/com/personal/apptruyen/data/remote/auth/SourceAuthManager.kt` (dòng 34–38: `probeClient = okHttpClient.newBuilder().cookieJar(CookieJar.NO_COOKIES).build()` kế thừa cấu hình `Dns` từ `okHttpClient`).
  - `app/src/main/java/com/personal/apptruyen/di/AppModule.kt` (dòng 51–63: `provideOkHttpClient`).
  - `app/src/test/java/com/personal/apptruyen/data/repository/DownloadRepositoryTest.kt` (dòng 1–380).
  - `app/src/test/java/com/personal/apptruyen/download/DownloadServiceTest.kt` (dòng 1–47).
  - `app/src/test/java/com/personal/apptruyen/data/remote/WikidichScraperTest.kt` (dòng 1–261).

---

### 3. Assumptions & Cross-Task Dependencies

1. **Giữ nguyên 100% chữ ký `IDownloadRepository`, `DownloadProgress`, `DownloadState` (Minimal Diffs)**:
   - Không thay đổi giao diện `IDownloadRepository` hay `DownloadService.DownloadState`.
   - Hàm `DownloadService.buildCompletionText(downloaded: Int, errors: Int, loginRequired: Boolean = false, rateLimited: Boolean = false): String` chỉ thêm tham số có giá trị mặc định `rateLimited: Boolean = false`, tương thích ngược 100% với các lời gọi và test hiện có.
2. **Cách thức hoạt động của Fail-Fast HTTP `403`/`429`/`503` trên Wikidich mà không ảnh hưởng nguồn khác**:
   - Trong `WikidichScraper.getChapterContent` (`WikidichScraper.kt:264`), scraper gọi trực tiếp `fetchDocument(chapterUrl)` (không qua `fetchDocumentWithRetry`), nên khi máy chủ `wikicv.org` trả HTTP `403`, `429` hoặc `503`, `fetchDocument` lập tức ném `ScraperException.HttpException(response.code, absUrl)` ngay ở request đầu tiên.
   - Tại `DownloadRepository.fetchChapterWithRetry`, khi `source.sourceId == WIKIDICH_SOURCE_ID` và `isRateLimitHttpError(e)` là `true`, hàm trả về ngay `ChapterFetchOutcome(chapter = chapter, error = e)` mà không retry lần thứ 2.
   - Tại `DownloadRepository.downloadChapters`, điều kiện dừng sớm `isWikidich && isRateLimitHttpError(outcome.error)` chỉ kích hoạt khi `source.sourceId == "wikidich"`. Nhờ đó, các nguồn khác (`sstruyen`, `tangthuvien`) vẫn giữ nguyên hành vi retry 2 lần và dừng sau `MAX_CONSECUTIVE_FAILED_BATCHES = 3` lô lỗi liên tiếp (bảo toàn 100% test `downloadChapters aborts early after MAX_CONSECUTIVE_FAILED_BATCHES completely failed batches` trong `DownloadRepositoryTest.kt:340-355`).
3. **An toàn TLS/SNI và chống đệ quy vô hạn trong `ResilientDohDns`**:
   - `ResilientDohDns` sử dụng một `bootstrapClient: OkHttpClient` độc lập được cấu hình `.dns(Dns.SYSTEM)` và gọi tới các URL dùng địa chỉ IP literal (`https://1.1.1.1/dns-query`, `https://1.0.0.1/dns-query`, `https://8.8.8.8/resolve`).
   - Do URL của DoH là địa chỉ IP literal và `bootstrapClient` dùng `Dns.SYSTEM`, tuyệt đối không bao giờ xảy ra vòng lặp đệ quy `ResilientDohDns -> bootstrapClient -> ResilientDohDns`.
   - Chứng chỉ TLS của `1.1.1.1`, `1.0.0.1` và `8.8.8.8` đều khai báo IP tương ứng trong trường Subject Alternative Name (SAN), nên `OkHttpClient` xác thực TLS chuẩn xác trên Android (`minSdk = 26`) mà không cần can thiệp `X509TrustManager`.
   - Khi tạo `InetAddress` từ chuỗi IP trả về trong JSON (`Answer[].data`), `ResilientDohDns` dùng `InetAddress.getByAddress(hostname, rawBytes)` thay vì `InetAddress.getByName(ipStr)` để giữ nguyên `hostname` gốc (ví dụ `"wikicv.org"`) trên đối tượng `InetAddress`, đảm bảo OkHttp gửi đúng TLS SNI `"wikicv.org"` khi bắt tay TLS với Cloudflare.

---

### 4. Verified Versions Table

> Không thêm/đổi bất kỳ dependency nào trong `app/build.gradle.kts`. Bảng dưới đây xác minh các thư viện và giao thức chuẩn đang sử dụng:

| Thư viện / API | Version hiện tại | Version đề xuất | Latest đã xác minh | Nguồn URL | Tương thích? |
| -------------- | ---------------- | --------------- | ------------------ | --------- | ------------ |
| `com.squareup.okhttp3:okhttp` (`okhttp3.Dns`) | `5.3.2` | `5.3.2` (giữ nguyên) | `5.3.2` | [square.github.io/okhttp](https://square.github.io/okhttp/) | ✅ Giao diện `okhttp3.Dns.lookup(hostname: String): List<InetAddress>` và `OkHttpClient.Builder.dns(...)` ổn định trên JVM 17 / Android `minSdk 26` |
| Cloudflare & Google DoH JSON API (`application/dns-json`) | Chuẩn RFC 8427 | Chuẩn RFC 8427 | 2026 | [developers.cloudflare.com/1.1.1.1/encryption/dns-over-https/make-api-requests/dns-json/](https://developers.cloudflare.com/1.1.1.1/encryption/dns-over-https/make-api-requests/dns-json/) | ✅ Cả `https://1.1.1.1/dns-query` (`Accept: application/dns-json`) và `https://8.8.8.8/resolve` đều trả về JSON `{ "Status": 0, "Answer": [{ "type": 1, "data": "..." }] }` |
| `org.json:json` (JVM Test) & Android `org.json.JSONObject` | `20260522` (test) / SDK 26+ | Giữ nguyên | `20260522` | `app/build.gradle.kts:176` | ✅ Có sẵn trong Android framework (`minSdk 26`) và trong JVM unit test, không cần thêm dependency mới |
| `kotlinx-coroutines-android` / `kotlinx-coroutines-test` | `1.11.0` | `1.11.0` (giữ nguyên) | `1.11.0` | [kotlinlang.org/api/kotlinx.coroutines](https://kotlinlang.org/api/kotlinx.coroutines/) | ✅ `TestCoroutineScheduler` trong `runTest` kiểm soát hoàn toàn `delay(15_000L..20_000L)` mà không làm chậm thời gian chạy unit test thực tế |

---

### 5. Files Cần Chỉnh Sửa / Tạo Mới

| Nhãn       | File | Tầng | Ghi chú (file nhạy cảm → lý do) |
| ---------- | ---- | ---- | ------------------------------- |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/data/repository/DownloadRepository.kt` | `data/repository` | Bổ sung hằng số điều tiết riêng cho Wikidich (`WIKIDICH_SOURCE_ID = "wikidich"`, `WIKIDICH_BATCH_SIZE = 1`, `WIKIDICH_MIN_DELAY_MS = 15_000L`, `WIKIDICH_MAX_DELAY_MS = 20_000L`, `WIKIDICH_RETRY_DELAY_MS = 5_000L`, `RATE_LIMIT_HTTP_CODES = setOf(403, 429, 503)`), thuộc tính `internal var wikidichDelayProvider`, hàm `internal fun isRateLimitHttpError`, nhánh fail-fast không retry trong `fetchChapterWithRetry` và điều tiết tuần tự 15–20s/chương trong `downloadChapters` khi `source.sourceId == "wikidich"`. |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/data/remote/WikidichScraper.kt` | `data/remote` | Thêm `kotlinx.coroutines.delay(crawlDelayMs)` (`300ms`) trước khi sang trang kế tiếp trong vòng lặp `getRemainingChapters()` (`WikidichScraper.kt:238-259`). |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/download/DownloadService.kt` | `download` | Bổ sung `WAKELOCK_TIMEOUT_MS`, `setReferenceCounted(false)` + `renewWakeLock()` mỗi khi nhận tiến độ chương mới; cập nhật `buildCompletionText` và `sendCompletionNotification` hỗ trợ `rateLimited: Boolean = false`; bắt riêng `catch (e: ScraperException.HttpException)` trong `startDownload` để báo thông báo tiếng Việt khi gặp mã `403`/`429`/`503`. |
| `[NEW]` | `app/src/main/java/com/personal/apptruyen/data/remote/ResilientDohDns.kt` | `data/remote` | Cài đặt `okhttp3.Dns` kết hợp DoH JSON (`1.1.1.1`, `1.0.0.1`, `8.8.8.8`), bộ lọc IP sinkhole/bogon, cache TTL 10 phút (`ConcurrentHashMap`), bypass localhost/IP literal cho `MockWebServer`, và fallback `Dns.SYSTEM`. |
| `[MODIFY]` | `app/src/main/java/com/personal/apptruyen/di/AppModule.kt` | `di` | Gắn `.dns(ResilientDohDns())` vào `provideOkHttpClient` (`AppModule.kt:53-63`). |
| `[MODIFY]` | `app/src/test/java/com/personal/apptruyen/data/repository/DownloadRepositoryTest.kt` | `test` | **[NHẠY CẢM - Test File]**: Giữ nguyên 100% 10 unit test hiện có; chỉ **THÊM MỚI** 4 unit test kiểm chứng riêng cho nguồn `wikidich`: (1) tải tuần tự `batchSize == 1` và nghỉ `15_000L..20_000L` giữa các chương, (2) fail-fast không retry khi gặp `HttpException(403/429/503)` trên Wikidich và dừng ngay các chương sau, (3) retry sau `5_000L` khi gặp lỗi mạng tạm thời (`NetworkException`) trên Wikidich, (4) xác minh `wikidichDelayProvider` mặc định sinh giá trị trong đoạn `15_000L..20_000L`. |
| `[MODIFY]` | `app/src/test/java/com/personal/apptruyen/download/DownloadServiceTest.kt` | `test` | **[NHẠY CẢM - Test File]**: Giữ nguyên 100% 3 unit test hiện có; chỉ **THÊM MỚI** 1 unit test kiểm chứng `buildCompletionText` khi `rateLimited = true` trả về đúng thông báo tiếng Việt. |
| `[MODIFY]` | `app/src/test/java/com/personal/apptruyen/data/remote/WikidichScraperTest.kt` | `test` | **[NHẠY CẢM - Test File]**: Giữ nguyên 100% các unit test hiện có; chỉ **THÊM MỚI** 1 unit test kiểm chứng `getRemainingChapters` có nhiều trang phân trang thực hiện nghỉ `crawlDelayMs` (`300ms`) giữa các trang AJAX. |
| `[NEW]` | `app/src/test/java/com/personal/apptruyen/data/remote/ResilientDohDnsTest.kt` | `test` | **[NHẠY CẢM - Test File]**: Tạo mới bộ unit test dùng `MockWebServer` kiểm chứng `ResilientDohDns`: (1) bypass localhost/IP literal sang `systemDns`, (2) phân giải thành công qua DoH JSON và bảo toàn `hostname` trên `InetAddress`, (3) lọc bỏ IP sinkhole (`0.0.0.0`, `127.0.0.1`, `192.168.1.1`) và chuyển sang endpoint DoH kế tiếp hoặc lấy IP public hợp lệ, (4) trả kết quả từ cache trong thời hạn TTL và làm mới khi hết TTL, (5) fallback sang `systemDns` khi tất cả DoH endpoints lỗi, và (6) ném `UnknownHostException` khi cả DoH và `systemDns` đều thất bại. |
| `[MODIFY]` | `docs/architecture.md` | `docs` | Cập nhật mô tả `ResilientDohDns` trong Remote Layer, luồng tải chương riêng cho Wikidich và timestamp. |
| `[MODIFY]` | `docs/scraping.md` | `docs` | Cập nhật mục Rate Limiting & DNS-over-HTTPS cho nguồn Wikidich và các nguồn truyện. |
| `[MODIFY]` | `docs/progress.md` | `docs` | Ghi nhận hoàn thành điều tiết tải Wikidich & tích hợp DNS-over-HTTPS. |
| `[MODIFY]` | `docs/changelog.md` | `docs` | Thêm mục cải tiến dưới `## [Unreleased]`. |
| `[MODIFY]` | `docs/plans/README.md` | `docs` | Thêm plan vào Bảng Trạng Thái và cập nhật sang `Completed` khi hoàn tất. |

---

### 6. Các Bước Thực Hiện

- [ ] **Bước 1: Triển khai `ResilientDohDns.kt` và tích hợp vào `di/AppModule.kt` (Mục tiêu A2)**
  - Tạo mới file `app/src/main/java/com/personal/apptruyen/data/remote/ResilientDohDns.kt`:
    - Khai báo class:
      ```kotlin
      class ResilientDohDns(
          private val systemDns: Dns = Dns.SYSTEM,
          private val dohEndpoints: List<String> = DEFAULT_DOH_ENDPOINTS,
          private val bootstrapClient: OkHttpClient = buildBootstrapClient(),
          private val cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS,
          private val clock: () -> Long = System::currentTimeMillis,
          private val bypassLocalhostCheckForTest: Boolean = false,
      ) : Dns
      ```
    - Khai báo hằng số trong `companion object`:
      - `val DEFAULT_DOH_ENDPOINTS = listOf("https://1.1.1.1/dns-query", "https://1.0.0.1/dns-query", "https://8.8.8.8/resolve")`
      - `const val DEFAULT_CACHE_TTL_MS = 10 * 60 * 1000L` (`10 phút`)
      - `private const val MAX_CACHE_ENTRIES = 128`
      - `private const val DOH_TIMEOUT_SECONDS = 3L`
      - `private const val DNS_TYPE_A = 1`
      - `private const val DNS_TYPE_AAAA = 28`
      - `private val IPV4_LITERAL_REGEX = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")`
    - Trong `override fun lookup(hostname: String): List<InetAddress>`:
      1. Chuẩn hóa `val normalizedHost = hostname.trim().lowercase()`. Nếu rỗng ném `UnknownHostException("Hostname is blank")`.
      2. Nếu `!bypassLocalhostCheckForTest && isLocalOrIpLiteral(normalizedHost)`: trả về ngay `systemDns.lookup(hostname)`.
      3. Kiểm tra cache `ConcurrentHashMap<String, CacheEntry>`: nếu còn hạn (`clock() < cached.expiresAtMs`) và `cached.addresses.isNotEmpty()` thì trả về ngay `cached.addresses`.
      4. Duyệt tuần tự qua `dohEndpoints`:
         - Tạo URL `endpoint.toHttpUrl().newBuilder().addQueryParameter("name", normalizedHost).addQueryParameter("type", "A").build()`.
         - Gửi request GET với header `Accept: application/dns-json` qua `bootstrapClient.newCall(request).execute().use { response -> ... }`.
         - Parse JSON bằng `parseDohJsonResponse(hostname, body)`: kiểm tra `Status == 0`, đọc mảng `Answer`, lấy các phần tử có `type == DNS_TYPE_A` hoặc `type == DNS_TYPE_AAAA`, chuyển chuỗi IP thành mảng byte (`parseIpLiteralToBytes`) rồi tạo `InetAddress.getByAddress(hostname, rawBytes)`, và chỉ giữ lại địa chỉ thỏa `isValidPublicAddress(addr)` (`!addr.isAnyLocalAddress && !addr.isLoopbackAddress && !addr.isLinkLocalAddress && !addr.isSiteLocalAddress && !addr.isMulticastAddress`).
         - Nếu danh sách địa chỉ khác rỗng: lưu vào `cache[normalizedHost]` với `expiresAtMs = clock() + cacheTtlMs` và trả về ngay.
      5. Nếu mọi `dohEndpoints` đều thất bại hoặc không trả về IP hợp lệ:
         - Gọi `systemDns.lookup(hostname)` trong khối `try/catch`:
           - Lọc `val publicAddresses = sysAddresses.filter { isValidPublicAddress(it) }`. Nếu khác rỗng thì lưu cache và trả về `publicAddresses`; ngược lại nếu `sysAddresses.isNotEmpty()` thì trả về `sysAddresses`.
         - Nếu `systemDns.lookup(hostname)` ném `UnknownHostException`: nếu có bản ghi `cache[normalizedHost]` cũ (stale cache) thì trả về `cached.addresses`, ngược lại ném lại `UnknownHostException`.
  - Tại `app/src/main/java/com/personal/apptruyen/di/AppModule.kt`:
    - Thêm `import com.personal.apptruyen.data.remote.ResilientDohDns` và gắn `.dns(ResilientDohDns())` vào `provideOkHttpClient`.

- [ ] **Bước 2: Bổ sung chính sách điều tiết tốc độ và Fail-Fast riêng cho Wikidich trong `DownloadRepository.kt` (Mục tiêu A1)**
  - Tại `app/src/main/java/com/personal/apptruyen/data/repository/DownloadRepository.kt`:
    - Trong `companion object` (`DownloadRepository.kt:48-56`), thêm các hằng số:
      - `const val WIKIDICH_SOURCE_ID = "wikidich"`
      - `const val WIKIDICH_BATCH_SIZE = 1`
      - `const val WIKIDICH_MIN_DELAY_MS = 15_000L`
      - `const val WIKIDICH_MAX_DELAY_MS = 20_000L`
      - `const val WIKIDICH_RETRY_DELAY_MS = 5_000L`
      - `val RATE_LIMIT_HTTP_CODES = setOf(403, 429, 503)`
    - Thêm thuộc tính hỗ trợ kiểm thử deterministic trong `DownloadRepository`:
      ```kotlin
      internal var wikidichDelayProvider: () -> Long = {
          kotlin.random.Random.nextLong(WIKIDICH_MIN_DELAY_MS, WIKIDICH_MAX_DELAY_MS + 1)
      }
      ```
    - Thêm hàm kiểm tra lỗi giới hạn tốc độ HTTP:
      ```kotlin
      internal fun isRateLimitHttpError(error: Throwable?): Boolean =
          error is ScraperException.HttpException && error.statusCode in RATE_LIMIT_HTTP_CODES
      ```
    - Trong `fetchChapterWithRetry` (`DownloadRepository.kt:114-145`):
      - Trong khối `catch (e: Exception)`: nếu `source.sourceId == WIKIDICH_SOURCE_ID && isRateLimitHttpError(e)`, trả về ngay `ChapterFetchOutcome(chapter = chapter, error = e)` (không retry).
    - Trong `downloadChapters` (`DownloadRepository.kt:147-249`):
      - Sau `val source = sourceFor(firstValidUrl)`, xác định:
        - `val isWikidich = source.sourceId == WIKIDICH_SOURCE_ID`
        - `val batchSize = if (isWikidich) WIKIDICH_BATCH_SIZE else DOWNLOAD_BATCH_SIZE`
        - `val batches = sortedChapters.chunked(batchSize)`
      - Khi gọi `fetchChapterWithRetry`, truyền `val retryDelayMs = if (isWikidich) WIKIDICH_RETRY_DELAY_MS else currentDelay`.
      - Khi xử lý `outcome.error` (`DownloadRepository.kt:204-212`):
        ```kotlin
        if (
            fatalError == null &&
            (
                outcome.error is ScraperException.LoginRequiredException ||
                    outcome.error is ScraperException.CircuitOpenException ||
                    (isWikidich && isRateLimitHttpError(outcome.error))
            )
        ) {
            fatalError = outcome.error as? ScraperException
        }
        ```
      - Ở bước nghỉ giữa các lô (`if (batchIndex < batches.lastIndex)` tại `DownloadRepository.kt:245-247`):
        - Nếu `isWikidich`: gọi `delay(wikidichDelayProvider().coerceIn(WIKIDICH_MIN_DELAY_MS, WIKIDICH_MAX_DELAY_MS))`.
        - Ngược lại: giữ nguyên `delay(currentDelay)`.

- [ ] **Bước 3: Thêm `delay(crawlDelayMs)` trong `WikidichScraper.getRemainingChapters()`**
  - Tại `app/src/main/java/com/personal/apptruyen/data/remote/WikidichScraper.kt` (dòng 11 và dòng 253–259):
    - Thêm `import kotlinx.coroutines.delay`.
    - Trong `getRemainingChapters`, sau khi kiểm tra `if (!hasNextPage) { break }` và trước khi sang vòng lặp trang AJAX kế tiếp (`currentStart = nextStart`), gọi `delay(crawlDelayMs)` (`300ms`).

- [ ] **Bước 4: Gia hạn `WakeLock` và cập nhật thông báo tiếng Việt khi bị giới hạn tốc độ trong `DownloadService.kt`**
  - Tại `app/src/main/java/com/personal/apptruyen/download/DownloadService.kt`:
    - Trong `companion object` (`DownloadService.kt:50-77`):
      - Thêm `private const val WAKELOCK_TIMEOUT_MS = 2 * 60 * 60 * 1000L`.
      - Cập nhật `internal fun buildCompletionText`:
        ```kotlin
        internal fun buildCompletionText(
            downloaded: Int,
            errors: Int,
            loginRequired: Boolean = false,
            rateLimited: Boolean = false,
        ): String =
            when {
                loginRequired ->
                    "Đã tải $downloaded chương. Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại để tải tiếp."
                rateLimited ->
                    "Đã tải $downloaded chương. Nguồn truyện đang giới hạn tốc độ truy cập (Cloudflare), vui lòng thử lại sau ít phút."
                errors > 0 -> "Đã tải $downloaded chương ($errors lỗi)"
                else -> "Đã tải $downloaded chương thành công"
            }
        ```
    - Trong `startDownload` (`DownloadService.kt:198-237`):
      - Bên trong `downloadRepository.downloadChapters(storyId, allChapters).collect { progress -> ... }`: gọi `renewWakeLock()` mỗi khi nhận tiến độ chương mới.
      - Thêm nhánh `catch (e: ScraperException.HttpException)` trước `catch (e: Exception)`:
        ```kotlin
        } catch (e: ScraperException.HttpException) {
            Timber.w(e, "Download stopped: HTTP ${e.statusCode}")
            val downloaded = lastProgress?.current ?: 0
            val errors = (lastProgress?.errors ?: 0).coerceAtLeast(1)
            val isRateLimited = e.statusCode in com.personal.apptruyen.data.repository.DownloadRepository.RATE_LIMIT_HTTP_CODES
            _downloadState.value = DownloadState.Completed(storyId, storyTitle, downloaded, errors)
            sendCompletionNotification(storyTitle, downloaded, errors, rateLimited = isRateLimited)
        }
        ```
    - Trong `sendCompletionNotification` (`DownloadService.kt:342-360`): thêm tham số `rateLimited: Boolean = false` và truyền vào `buildCompletionText(downloaded, errors, loginRequired, rateLimited)`.
    - Trong `acquireWakeLock()` (`DownloadService.kt:381-392`): gọi `setReferenceCounted(false)` trước `acquire(WAKELOCK_TIMEOUT_MS)`, và thêm hàm `private fun renewWakeLock() { wakeLock?.acquire(WAKELOCK_TIMEOUT_MS) }`.

- [ ] **Bước 5: Bổ sung Unit Tests trong `DownloadRepositoryTest.kt`, `DownloadServiceTest.kt`, `WikidichScraperTest.kt` và `ResilientDohDnsTest.kt`**
  - Tại `app/src/test/java/com/personal/apptruyen/data/repository/DownloadRepositoryTest.kt`:
    - Giữ nguyên toàn bộ 10 unit test hiện có.
    - Thêm 4 unit test mới cho nguồn `wikidich`:
      1. `downloadChapters for wikidich downloads sequentially with batch size 1 and waits 15 to 20 seconds between chapters` (TC-01).
      2. `downloadChapters for wikidich fails fast without retry on HTTP 403 429 or 503 after saving prior chapters` (TC-02).
      3. `downloadChapters for wikidich retries temporary NetworkException with 5000ms retry delay` (TC-03).
      4. `default wikidichDelayProvider generates random delay within 15000ms to 20000ms inclusive` (TC-04).
  - Tại `app/src/test/java/com/personal/apptruyen/download/DownloadServiceTest.kt`:
    - Giữ nguyên 3 unit test hiện có.
    - Thêm 1 unit test mới: `buildCompletionText returns Vietnamese Cloudflare rate limit message when rateLimited is true` (TC-05).
  - Tại `app/src/test/java/com/personal/apptruyen/data/remote/WikidichScraperTest.kt`:
    - Giữ nguyên toàn bộ các unit test hiện có.
    - Thêm 1 unit test mới: `getRemainingChapters delays crawlDelayMs between multiple AJAX pages` (TC-06).
  - Tạo mới `app/src/test/java/com/personal/apptruyen/data/remote/ResilientDohDnsTest.kt` (TC-07..TC-11):
    - Kiểm thử bằng `MockWebServer` (với `bypassLocalhostCheckForTest = true` khi test luồng DoH trên `MockWebServer`, và `bypassLocalhostCheckForTest = false` mặc định khi test bypass localhost):
      1. `lookup delegates localhost and IP literals directly to systemDns without calling DoH` (TC-07).
      2. `lookup resolves public domain via DoH JSON and preserves hostname on returned InetAddress` (TC-08).
      3. `lookup filters out sinkhole and private IPs from DoH response and falls back to next DoH endpoint` (TC-09).
      4. `lookup caches resolved addresses within TTL and re-queries after TTL expires` (TC-10).
      5. `lookup falls back to systemDns when all DoH endpoints fail and throws UnknownHostException when both fail` (TC-11).

- [ ] **Bước 6: Chạy Verification Gate (Tier 2) & Cập nhật Documentation**
  - Chạy `.\gradlew.bat compileDebugKotlin`, `.\gradlew.bat ktlintCheck`, `.\gradlew.bat testDebugUnitTest`, `.\gradlew.bat assembleDebug`.
  - Cập nhật `docs/architecture.md`, `docs/scraping.md`, `docs/progress.md`, `docs/changelog.md`, `docs/plans/README.md` và chuyển trạng thái plan sang `Completed`.

---

### 7. Test Contract

| # | Hành vi (ngôn ngữ nghiệp vụ) | Input | Kết quả mong đợi / thông báo lỗi tiếng Việt |
| - | ---------------------------- | ----- | ------------------------------------------- |
| TC-01 | Tải chương nguồn Wikidich tuần tự từng chương một (`WIKIDICH_BATCH_SIZE = 1`) kèm nghỉ điều tiết `15–20 giây/chương`, không ảnh hưởng tốc độ 4 chương/lô của nguồn khác | `source.sourceId = "wikidich"`, `source.baseUrl = "https://wikicv.org"`, 3 chương (`c1`, `c2`, `c3`), mỗi chương mất `50ms` tải mạng, `wikidichDelayProvider = { 17_000L }` | Số request chạy đồng thời tối đa `maxInFlight == 1`; lưu tuần tự đủ 3 chương vào Room DB; tổng thời gian ảo `testScheduler.currentTime == 34_150L` (`3 * 50ms + 2 * 17_000ms`, không chờ sau chương cuối). |
| TC-02 | Dừng sớm (Fail-Fast) ngay lập tức và không retry khi nguồn Wikidich trả mã HTTP `403`, `429` hoặc `503` (Cloudflare chặn/rate-limit) | `source.sourceId = "wikidich"`, 3 chương (`c1`, `c2`, `c3`): `c1` thành công, `c2` ném `ScraperException.HttpException(429, "https://wikicv.org/c2")` | `c1` được lưu vào Room DB; `c2` chỉ gọi mạng đúng 1 lần (`coVerify(exactly = 1)`, không retry); `downloadChapters` ném `ScraperException.HttpException(429)` ngay sau `c2`; `c3` tuyệt đối không được gọi mạng (`coVerify(exactly = 0)`). |
| TC-03 | Thử lại (retry) lỗi mạng tạm thời (`NetworkException`) trên nguồn Wikidich với khoảng nghỉ `WIKIDICH_RETRY_DELAY_MS = 5_000L` | `source.sourceId = "wikidich"`, 1 chương (`c1`): lần gọi 1 ném `ScraperException.NetworkException("Timeout")`, lần gọi 2 trả về `"Nội dung 1"` | `c1` được gọi mạng 2 lần, cách nhau đúng `5_000L` trên `testScheduler.currentTime`; lưu `"Nội dung 1"` vào Room DB thành công (`errors == 0`). |
| TC-04 | Sinh khoảng nghỉ ngẫu nhiên chuẩn trong đoạn `[15_000L, 20_000L]` cho Wikidich | Gọi `downloadRepository.wikidichDelayProvider()` 100 lần | 100% giá trị trả về nằm trong khoảng `15_000L..20_000L` (bao gồm cả 2 biên). |
| TC-05 | Hiển thị thông báo tiếng Việt trên `DownloadService` khi dừng tải do giới hạn tốc độ (Cloudflare HTTP `403`/`429`/`503`) | `DownloadService.buildCompletionText(downloaded = 18, errors = 1, loginRequired = false, rateLimited = true)` | Trả về đúng chuỗi tiếng Việt: `"Đã tải 18 chương. Nguồn truyện đang giới hạn tốc độ truy cập (Cloudflare), vui lòng thử lại sau ít phút."`. |
| TC-06 | Điều tiết `delay(crawlDelayMs)` (`300ms`) giữa các trang AJAX khi tải danh sách chương dài trên `WikidichScraper.getRemainingChapters` | Trang chi tiết + 2 trang AJAX (`start=99` có link `data-start="198"`, `start=198` là trang cuối) | Thu thập đủ chương của cả 2 trang AJAX và thời gian ảo `testScheduler.currentTime >= 300L`. |
| TC-07 | Bỏ qua DoH cho `localhost` và địa chỉ IP literal (`127.0.0.1`, `::1`) để bảo toàn tốc độ `MockWebServer` | `ResilientDohDns` mặc định (`bypassLocalhostCheckForTest = false`), gọi `lookup("localhost")` và `lookup("127.0.0.1")` | Ủy quyền trực tiếp cho `systemDns.lookup(...)`, `MockWebServer` đóng vai trò DoH nhận `0` request. |
| TC-08 | Phân giải tên miền nguồn truyện (`wikicv.org`) qua DoH JSON (`application/dns-json`) và bảo toàn `hostname` cho TLS SNI | `MockWebServer` trả về JSON `{"Status":0,"Answer":[{"name":"wikicv.org","type":1,"TTL":300,"data":"104.21.48.210"}]}` | Trả về `List<InetAddress>` có `hostAddress == "104.21.48.210"` và `hostName == "wikicv.org"` (không gọi reverse DNS). |
| TC-09 | Lọc bỏ IP sinkhole/bogon (`0.0.0.0`, `127.0.0.1`, `192.168.1.1`) khi DNS bị đầu độc và tự động chuyển sang endpoint DoH kế tiếp | Endpoint DoH 1 trả về `0.0.0.0` và `192.168.1.1`; Endpoint DoH 2 trả về `172.67.180.99` | Loại bỏ `0.0.0.0` và `192.168.1.1`, tự động gọi Endpoint DoH 2 và trả về `172.67.180.99`. |
| TC-10 | Cache kết quả phân giải DoH trong thời hạn TTL (`10 phút`) và làm mới khi hết hạn | Gọi `lookup("sstruyen.pics")` tại `t = 0ms`, `t = 5 phút`, và `t = 11 phút` | Tại `t = 5 phút` trả kết quả từ RAM (`requestCount == 1`); tại `t = 11 phút` gọi lại DoH (`requestCount == 2`). |
| TC-11 | Fallback về `systemDns` khi tất cả DoH endpoints lỗi, hoặc ném `UnknownHostException` khi cả hai cùng lỗi | Tất cả DoH endpoints trả HTTP 500; (a) `systemDns` trả `104.21.10.20`, (b) `systemDns` ném `UnknownHostException` | (a) Trả về `104.21.10.20` từ `systemDns`; (b) Ném `UnknownHostException`. |

---

### 8. Verification Plan

- **Unit Tests**:
  - Các file test:
    - `app/src/test/java/com/personal/apptruyen/data/repository/DownloadRepositoryTest.kt`
    - `app/src/test/java/com/personal/apptruyen/download/DownloadServiceTest.kt`
    - `app/src/test/java/com/personal/apptruyen/data/remote/WikidichScraperTest.kt`
    - `app/src/test/java/com/personal/apptruyen/data/remote/ResilientDohDnsTest.kt`
  - Lệnh chạy riêng các test class liên quan:
    ```powershell
    .\gradlew.bat testDebugUnitTest --tests "com.personal.apptruyen.data.repository.DownloadRepositoryTest" --tests "com.personal.apptruyen.download.DownloadServiceTest" --tests "com.personal.apptruyen.data.remote.WikidichScraperTest" --tests "com.personal.apptruyen.data.remote.ResilientDohDnsTest"
    ```
- **Tier 2 Quality Gate (Bắt buộc trước khi báo cáo hoàn tất)**:
  ```powershell
  .\gradlew.bat ktlintCheck; .\gradlew.bat testDebugUnitTest; .\gradlew.bat assembleDebug
  ```
- **Kiểm thử thủ công trên thiết bị (dành cho User)**:
  1. Kết nối vào mạng WiFi công ty đang chặn DNS: mở ứng dụng, kiểm tra tab Khám Phá / Tìm Kiếm / Chi Tiết Truyện trên cả 3 nguồn (`Wikidich`, `SsTruyện`, `Tàng Thư Viện`) tải dữ liệu bình thường nhờ `ResilientDohDns`.
  2. Mở một truyện trên nguồn `Wikidich` (`wikicv.org`), bấm tải 5–10 chương: quan sát thanh tiến độ tải tuần tự từng chương một, khoảng cách giữa mỗi chương từ 15–20 giây, không bị Cloudflare chặn.
  3. Mở một truyện trên nguồn `SsTruyện` hoặc `Tàng Thư Viện`, bấm tải 20 chương: quan sát tiến độ vẫn nhảy nhanh theo từng lô 4 chương/lần.
- **Negative Probe**: Không thêm rule ktlint/lint/hook mới nên không yêu cầu negative probe.

---

### 9. Rủi Ro & Phòng Ngừa

| Rủi ro | Mức (Cao/TB/Thấp) | Technical control |
| ------ | ----------------- | ----------------- |
| **R1: `ResilientDohDns` tạo vòng lặp đệ quy vô hạn nếu dùng chung `OkHttpClient` hoặc làm mất TLS SNI khi tạo `InetAddress` từ chuỗi IP** | **Cao** | (1) `ResilientDohDns` dùng `bootstrapClient` riêng với `.dns(Dns.SYSTEM)` và gọi trực tiếp tới địa chỉ IP literal (`https://1.1.1.1/dns-query`, `https://1.0.0.1/dns-query`, `https://8.8.8.8/resolve`); (2) Tạo `InetAddress` bằng `InetAddress.getByAddress(hostname, rawBytes)` để giữ nguyên `hostname` gốc cho TLS SNI (kiểm chứng ở TC-08). |
| **R2: `ResilientDohDns` làm chậm hoặc phá vỡ tính cô lập của `MockWebServer` trong bộ unit test hiện có** | **Cao** | Kiểm tra `isLocalOrIpLiteral(normalizedHost)` ở ngay đầu `lookup(hostname)`: mọi truy vấn tới `localhost`, `127.0.0.1`, `::1` hoặc IP literal đều trả về ngay qua `systemDns.lookup(hostname)` mà không gọi DoH (kiểm chứng ở TC-07 và toàn bộ `testDebugUnitTest`). |
| **R3: Tải nhiều chương Wikidich với nhịp 15–20s/chương vượt quá thời hạn 2 giờ ban đầu của `WakeLock` hoặc vẫn retry khi đã bị Cloudflare trả 403/429/503** | **Cao** | (1) Trong `DownloadService`, đặt `setReferenceCounted(false)` và gọi `renewWakeLock()` mỗi khi nhận tiến độ chương mới trong `collect`; (2) Trong `DownloadRepository`, khi `source.sourceId == "wikidich"` và gặp `HttpException(403/429/503)`, không retry trong `fetchChapterWithRetry` và ném `fatalError` dừng ngay sau chương hiện tại, kèm thông báo tiếng Việt trên `DownloadService` (kiểm chứng ở TC-02, TC-05). |
| **R4: Mạng nội bộ công ty chặn luôn kết nối HTTPS tới IP `1.1.1.1` và `8.8.8.8`** | **Trung bình** | Đặt timeout ngắn (`3 giây` connect/read) trên `bootstrapClient`; nếu tất cả DoH endpoints đều lỗi thì tự động fallback sang `systemDns.lookup(hostname)` (kiểm chứng ở TC-11). |

#### Phân Tích Đối Kháng (Adversarial Timeline — S3, S5, S6)

```text
Cơ chế 1 (Trục Đồng thời & S6 External Resilience): Tải chương Wikidich gặp HTTP 429/403 từ Cloudflare giữa chừng
T1: Người dùng bấm tải 50 chương truyện Wikidich. DownloadRepository nhận diện source.sourceId == "wikidich", đặt batchSize = 1. Chương 1 tải thành công, lưu vào Room DB, emit tiến độ 1/50 và nghỉ ngẫu nhiên 17 giây.
T2: Khi tải Chương 2, máy chủ wikicv.org / Cloudflare trả về HTTP 429 (Too Many Requests). BaseScraper.fetchDocument ném ScraperException.HttpException(429, url).
T3: fetchChapterWithRetry kiểm tra source.sourceId == "wikidich" && isRateLimitHttpError(e) == true -> trả về ngay ChapterFetchOutcome(error = HttpException(429)) mà KHÔNG retry lần 2. Vòng lặp downloadChapters ghi nhận fatalError = HttpException(429), emit tiến độ và throw fatalError ngay lập tức (không gọi mạng cho Chương 3..50). DownloadService bắt catch (e: ScraperException.HttpException), hiển thị thông báo tiếng Việt "Đã tải 2 chương. Nguồn truyện đang giới hạn tốc độ truy cập (Cloudflare), vui lòng thử lại sau ít phút." và giải phóng WakeLock trong finally.
Kết cục: Chương 1 được bảo toàn trong Room DB, không spam thêm bất kỳ request nào vào wikicv.org sau khi gặp 429, người dùng nhận thông báo tiếng Việt rõ ràng → AN TOÀN.

Cơ chế 2 (Trục TOCTOU & S5/S6 DNS Poisoning): Mạng WiFi công ty chặn/đầu độc DNS khi người dùng mở truyện
T1: Người dùng mở truyện trên WiFi công ty (DNS hệ thống trả NXDOMAIN hoặc IP sinkhole 0.0.0.0 / 192.168.1.1 cho wikicv.org).
T2: OkHttpClient gọi ResilientDohDns.lookup("wikicv.org"). Cache chưa có bản ghi nên ResilientDohDns gọi HTTPS GET tới https://1.1.1.1/dns-query?name=wikicv.org&type=A qua bootstrapClient.
T3: Phản hồi JSON trả về IP public của wikicv.org. ResilientDohDns kiểm tra isValidPublicAddress (loại bỏ mọi IP loopback/private/sinkhole), gắn hostname "wikicv.org" vào InetAddress.getByAddress("wikicv.org", bytes), lưu vào ConcurrentHashMap với TTL 10 phút và trả về cho OkHttpClient thực hiện bắt tay TLS với SNI "wikicv.org".
Kết cục: Vượt qua hoàn toàn chặn DNS của WiFi công ty, bảo toàn xác thực chứng chỉ TLS/HTTPS và cookie phiên → AN TOÀN.

Cơ chế 3 (Trục Thất bại giữa chừng): Firewall chặn cả 1.1.1.1 / 8.8.8.8 hoặc mất mạng khi đang tải
T1: Thiết bị nằm trong mạng chặn kết nối trực tiếp tới IP 1.1.1.1, 1.0.0.1 và 8.8.8.8.
T2: ResilientDohDns thử lần lượt các endpoint DoH, mỗi endpoint thất bại nhanh trong tối đa 3 giây, sau đó tự động chuyển sang gọi systemDns.lookup(hostname) (hoặc trả về stale cache nếu đã phân giải thành công trước đó).
T3: Nếu mất mạng hoàn toàn và không có stale cache, UnknownHostException được ném ra, BaseScraper chuyển đổi thành ScraperException.NetworkException với thông báo tiếng Việt.
Kết cục: Không treo ứng dụng, luôn có đường lùi về DNS hệ thống và xử lý lỗi mạng chuẩn xác → AN TOÀN.

Cơ chế 4 (Trục Vòng đời Android): Tải bộ truyện Wikidich 500 chương kéo dài > 2 giờ dưới nền
T1: DownloadService bắt đầu tải 500 chương Wikidich (500 * 17.5s ≈ 2.4 giờ), gọi acquireWakeLock() với setReferenceCounted(false) và timeout 2 giờ.
T2: Cứ mỗi 15–20 giây khi tải xong 1 chương, collect nhận DownloadProgress mới và gọi renewWakeLock() (wakeLock?.acquire(2 giờ)), gia hạn lại bộ đếm 2 giờ của PowerManager.
T3: Khi tải xong, bị hủy hoặc dừng do lỗi, khối finally gọi stopSelfCleanly() -> releaseWakeLock() giải phóng ngay lập tức WakeLock.
Kết cục: CPU không bị Doze tắt giữa chừng khi đang tải bộ truyện dài và không bao giờ rò rỉ WakeLock sau khi dịch vụ kết thúc → AN TOÀN.
```

---

### 10. Cập Nhật Docs

Theo ma trận tại `.agents/rules/06-docs.md`, sau khi hoàn tất code và pass Tier 2 Quality Gate sẽ cập nhật:

- `docs/architecture.md`: bổ sung `ResilientDohDns.kt` vào cấu trúc thư mục `data/remote/`, cập nhật mục Luồng dữ liệu số 6 (tải chương tuần tự 15–20s/chương cho Wikidich và song song 4 chương/lô cho nguồn khác), và cập nhật timestamp.
- `docs/scraping.md`: bổ sung mô tả cơ chế Rate Limiting riêng cho Wikidich (`1 chương/lần`, `15–20s/chương`, fail-fast `403`/`429`/`503`) và cơ chế vượt chặn DNS qua `ResilientDohDns`.
- `docs/progress.md`: thêm mục hoàn thành điều tiết tải chương Wikidich & vượt chặn DNS bằng DoH vào danh sách tính năng và bảng lịch sử thay đổi.
- `docs/changelog.md`: thêm mục dưới `## [Unreleased]` (`### Thêm mới` cho `ResilientDohDns` và `### Sửa đổi` cho điều tiết tải chương Wikidich & `DownloadService`).
- `docs/plans/README.md`: thêm dòng kế hoạch `2026-10-16-wikidich-rate-limit-and-doh-implementation-plan.md` và cập nhật trạng thái `Completed` sau khi hoàn tất.

## Audit History

<!-- Reviewer chỉ append "### Audit Run N" ở dưới. Không sửa phần Technical Plan. -->

### Audit Run 1

[REVIEWER_MODEL: gemini-3.8-flash]
- **Ngày thẩm định**: 2026-10-16
- **Revision được review**: Revision 1
- **Phương thức**: Fallback Lead Architecture Reviewer (`@reviewer`, 7-step independent audit)

#### 1. Kiểm Chứng Lỗi Cũ
- Đây là lượt thẩm định đầu tiên (Revision 1), không có lỗi tồn đọng từ lượt trước.

#### 2. Plan Completeness Gate
- **Verified Versions Table**: Đầy đủ tại Mục 4 (`okhttp:5.3.2`, Cloudflare/Google DoH `application/dns-json`, `org.json:json:20260522`, `kotlinx-coroutines-test:1.11.0`).
- **Danh sách file `[NEW]`/`[MODIFY]` & Checklist ≥ 5 bước**: Đầy đủ tại Mục 5 (14 file, gắn nhãn `[NHẠY CẢM - Test File]` cho 4 file test) và Mục 6 (6 bước triển khai).
- **Test Contract**: Đầy đủ tại Mục 7 (11 test cases từ TC-01 đến TC-11).
- **Verification Plan**: Đầy đủ tại Mục 8 (lệnh Gradle chạy riêng 4 test class, lệnh Tier 2 Quality Gate, kịch bản kiểm thử thủ công).
- **Assumptions & Cross-Task Dependencies**: Đầy đủ tại Mục 3.
- **Bảng rủi ro & Cập nhật Docs**: Đầy đủ tại Mục 9 (R1–R4 + 4 Adversarial Timelines) và Mục 10 (`architecture.md`, `scraping.md`, `progress.md`, `changelog.md`, `docs/plans/README.md`).

#### 3. Mental Simulation Verification Matrix

| Kịch bản | Status | Evidence Type | Chi tiết kiểm chứng độc lập |
| :--- | :---: | :---: | :--- |
| **S1: Cold-start & Build** | `PASS` | `[CMD]`, `[SRC]` | `[CMD]` `.\gradlew.bat compileDebugKotlin` pass (exit code `0`). `[SRC]` `app/build.gradle.kts:26,143,174-176` xác nhận `minSdk = 26`, `okhttp:5.3.2`, `mockwebserver3:5.3.2`, `org.json:json:20260522`. Không thêm dependency mới. Các API `okhttp3.Dns`, `InetAddress.getByAddress(String, ByteArray)`, `PowerManager.WakeLock.setReferenceCounted(Boolean)` đều có từ API 1 (`<= minSdk 26`). `[SRC]` `app/src/main/java/com/personal/apptruyen/di/AppModule.kt:53-62` xác nhận `provideOkHttpClient` là nơi duy nhất cung cấp `OkHttpClient` `@Singleton`. |
| **S2: Lifecycle & Teardown** | `PASS` | `[SRC]`, `[ADV]` | `[SRC]` `app/src/main/java/com/personal/apptruyen/download/DownloadService.kt:128,158-162,237-239,381-399`: `serviceScope` dùng `SupervisorJob() + Dispatchers.IO` và hủy trong `onDestroy()`. Việc thêm `setReferenceCounted(false)` trước `acquire(WAKELOCK_TIMEOUT_MS)` và gọi `renewWakeLock()` trong `collect` đảm bảo `releaseWakeLock()` trong `finally -> stopSelfCleanly()` giải phóng hoàn toàn `WakeLock` chỉ với 1 lệnh `release()`. Trong `ResilientDohDns`, `bootstrapClient.newCall(request).execute().use { ... }` đóng `Response` tự động. |
| **S3: Database & Temporal Logic** | `PASS` | `[SRC]`, `[ADV]` | `[SRC]` `app/src/main/java/com/personal/apptruyen/data/local/AppDatabase.kt:14-24` (`version = 9`) và `app/src/main/java/com/personal/apptruyen/data/repository/DownloadRepository.kt:186-221`: Không thay đổi schema Room. Trong `downloadChapters`, các chương tải thành công trước khi gặp `fatalError` được lưu tuần tự qua `chapterDao.saveChapterContent` trước khi `throw fatalError`. Trong `ResilientDohDns`, TTL cache tính qua hàm `clock: () -> Long = System::currentTimeMillis` cho phép kiểm thử thời gian ảo mà không phụ thuộc đồng hồ hệ thống. |
| **S4: Cross-Task State Flow** | `PASS` | `[SRC]`, `[CMD]` | `[SRC]` `app/src/main/java/com/personal/apptruyen/data/repository/IDownloadRepository.kt:12-43` giữ nguyên chữ ký interface. `[SRC]` `app/src/main/java/com/personal/apptruyen/download/DownloadService.kt:66-76`: `buildCompletionText` thêm tham số có giá trị mặc định `rateLimited: Boolean = false`, bảo toàn tương thích ngược với `DownloadServiceTest.kt:8-46`. `[SRC]` `app/src/main/java/com/personal/apptruyen/data/remote/auth/SourceAuthManager.kt:34-38`: `probeClient = okHttpClient.newBuilder().cookieJar(CookieJar.NO_COOKIES).build()` tự động kế thừa `ResilientDohDns` từ `okHttpClient`. |
| **S5: Security & Privacy Boundary** | `PASS` | `[SRC]`, `[URL]`, `[ADV]` | `[SRC]` `app/src/main/java/com/personal/apptruyen/di/AppModule.kt:53-62`: `ResilientDohDns` dùng `bootstrapClient` độc lập không gắn `WebViewCookieHandler`, đảm bảo cookie phiên (`express.sid`) không bao giờ gửi tới máy chủ DoH (`1.1.1.1`/`8.8.8.8`). Sử dụng `InetAddress.getByAddress(hostname, rawBytes)` giữ nguyên `hostname` gốc cho TLS SNI và xác minh chứng chỉ X.509. Bộ lọc `isValidPublicAddress` loại bỏ IP sinkhole/loopback/site-local (`0.0.0.0`, `127.0.0.1`, `10.x.x.x`, `192.168.x.x`). |
| **S6: External Resilience** | `PASS` | `[SRC]`, `[CMD]`, `[ADV]` | `[SRC]` `app/src/main/java/com/personal/apptruyen/data/remote/WikidichScraper.kt:29,225-260,262-292` và `BaseScraper.kt:32,149-151`: `WikidichScraper` có `sourceId = "wikidich"`, gọi thẳng `fetchDocument(chapterUrl)` ném `ScraperException.HttpException(code, url)` ngay ở lần đầu khi gặp `403`/`429`/`503`. `DownloadRepository` tải tuần tự `batchSize = 1`, nghỉ `15–20s/chương`, fail-fast không retry khi gặp `403`/`429`/`503`, và giữ nguyên `DOWNLOAD_BATCH_SIZE = 4` cho các nguồn khác. |
| **Domain: Open Inquiry** | `PASS` | `[SRC]` | Mọi chuỗi thông báo mới trong `DownloadService.kt` (`"Đã tải $downloaded chương. Nguồn truyện đang giới hạn tốc độ truy cập (Cloudflare), vui lòng thử lại sau ít phút."`) đều 100% tiếng Việt, tuân thủ `.agents/rules/00-core.md` và `01-coding.md`. |

#### 4. Claim Verification (Library & Codebase Claims)
- **Claim 1 (`okhttp3.Dns` & `InetAddress.getByAddress(hostname, rawBytes)` bảo toàn TLS SNI)**:
  - `[URL]` Tài liệu OkHttp `okhttp3.Dns` (`https://square.github.io/okhttp/5.x/okhttp/okhttp3/-dns/index.html`) và Java `InetAddress.getByAddress(String, byte[])`: Khi khởi tạo `InetAddress` bằng `InetAddress.getByAddress(hostname, rawBytes)`, trường `hostName` của `InetAddress` lưu trực tiếp `hostname` mà không thực hiện reverse DNS lookup; tầng TLS của OkHttp sử dụng `InetAddress` kèm hostname của `Request` để gửi đúng SNI và đối chiếu SAN trên chứng chỉ.
  - `[SRC]` `app/src/main/java/com/personal/apptruyen/di/AppModule.kt:53-62`: `OkHttpClient.Builder()` hỗ trợ `.dns(...)` trực tiếp.
- **Claim 2 (Cloudflare `1.1.1.1/dns-query` và Google `8.8.8.8/resolve` JSON schema)**:
  - `[URL]` Tài liệu Cloudflare DoH JSON (`https://developers.cloudflare.com/1.1.1.1/encryption/dns-over-https/make-api-requests/dns-json/`) và Google Public DNS JSON (`https://developers.google.com/speed/public-dns/docs/doh/json`): Với header `Accept: application/dns-json` và tham số `?name=<domain>&type=A`, cả hai trả về JSON có `"Status": 0` (NOERROR) và mảng `"Answer"` gồm các object `{"name": "...", "type": 1, "TTL": ..., "data": "<IPv4>"}`.
- **Claim 3 (`WikidichScraper.getChapterContent` ném trực tiếp `ScraperException.HttpException` không qua retry nội bộ)**:
  - `[SRC]` `app/src/main/java/com/personal/apptruyen/data/remote/WikidichScraper.kt:262-265`: `getChapterContent` override `BaseScraper.getChapterContent` và gọi `fetchDocument(chapterUrl)` (không gọi `fetchDocumentWithRetry`).
  - `[SRC]` `app/src/main/java/com/personal/apptruyen/data/remote/BaseScraper.kt:149-151`: Khi `!response.isSuccessful`, `fetchDocument` ném ngay `ScraperException.HttpException(response.code, absUrl)`.
- **Claim 4 (Tương thích ngược với 10 unit test hiện có trong `DownloadRepositoryTest`)**:
  - `[SRC]` `app/src/test/java/com/personal/apptruyen/data/repository/DownloadRepositoryTest.kt:63-64,340-355`: Mock `source = mockk(relaxed = true)` có `source.sourceId == ""` (khác `"wikidich"`), nên toàn bộ 10 test hiện tại (bao gồm test `MAX_CONSECUTIVE_FAILED_BATCHES` dùng `HttpException(429)` cho `https://sstruyen.pics`) đều đi vào nhánh mặc định `isWikidich == false`.
  - `[CMD]` Đã chạy `.\gradlew.bat testDebugUnitTest --tests "com.personal.apptruyen.data.repository.DownloadRepositoryTest" --tests "com.personal.apptruyen.download.DownloadServiceTest" --tests "com.personal.apptruyen.data.remote.WikidichScraperTest"` pass 100% (exit code `0`).

#### 5. Adversarial Timelines (S3, S5 & Đồng thời)

```text
Cơ chế 1 (Trục Đồng thời — Nhiều luồng OkHttp gọi ResilientDohDns.lookup cùng lúc):
T1: Người dùng mở màn hình Khám Phá, 3 scraper cùng gửi request song song qua OkHttpClient dùng chung.
T2: Nhiều worker thread của OkHttp gọi đồng thời ResilientDohDns.lookup("wikicv.org") và lookup("sstruyen.pics").
T3: ResilientDohDns đọc/ghi trên ConcurrentHashMap<String, CacheEntry>; đối tượng InetAddress và List<InetAddress> lưu trong CacheEntry là bất biến (immutable); bootstrapClient thread-safe.
Kết cục: Không xảy ra race condition hay ConcurrentModificationException → AN TOÀN.

Cơ chế 2 (Trục TOCTOU — DNS cache hết hạn đúng lúc mất mạng tạm thời):
T1: Bản ghi cache của "wikicv.org" trong ResilientDohDns vừa quá hạn TTL 10 phút (clock() >= cached.expiresAtMs) khi người dùng đang tải chương thứ 40.
T2: WiFi chập chờn trong 2 giây khiến cả 3 endpoint DoH (1.1.1.1, 1.0.0.1, 8.8.8.8) và systemDns.lookup("wikicv.org") đều ném IOException / UnknownHostException.
T3: Khối catch UnknownHostException của systemDns kiểm tra thấy cache["wikicv.org"] (stale entry) vẫn còn địa chỉ IP public hợp lệ đã phân giải trước đó nên trả về cached.addresses.
Kết cục: Tải chương tiếp tục mượt mà qua IP đã biết của wikicv.org mà không làm đứt tiến trình tải → AN TOÀN.

Cơ chế 3 (Trục Thất bại giữa chừng — Cloudflare trả HTTP 403/429 ở chương K của Wikidich):
T1: Chương 1..K-1 của truyện Wikidich đã tải thành công và lưu vào Room DB qua chapterDao.saveChapterContent.
T2: Ở chương K, wikicv.org trả HTTP 429; BaseScraper.fetchDocument ném ScraperException.HttpException(429, url).
T3: fetchChapterWithRetry kiểm tra source.sourceId == "wikidich" && isRateLimitHttpError(e) -> trả về ngay không retry; downloadChapters gán fatalError = HttpException(429), emit tiến độ và throw fatalError; DownloadService bắt catch (e: ScraperException.HttpException), hiển thị thông báo tiếng Việt và gọi stopSelfCleanly() giải phóng WakeLock trong finally.
Kết cục: Chương 1..K-1 được bảo toàn nguyên vẹn trong Room DB, chương K+1..N không bị gọi mạng, WakeLock được giải phóng sạch → AN TOÀN.

Cơ chế 4 (Trục Vòng đời Android — Gia hạn WakeLock nhiều lần khi tải bộ truyện dài):
T1: DownloadService bắt đầu tải 300 chương Wikidich, gọi acquireWakeLock() với setReferenceCounted(false) và acquire(2 giờ).
T2: Sau mỗi chương (15–20 giây), collect gọi renewWakeLock() -> wakeLock?.acquire(2 giờ) tổng cộng 300 lần.
T3: Khi hoàn tất hoặc người dùng bấm Hủy, stopSelfCleanly() gọi releaseWakeLock() -> if (it.isHeld) it.release(). Vì setReferenceCounted(false), đúng 1 lần gọi release() nhả hoàn toàn WakeLock.
Kết cục: Không bị hết hạn WakeLock giữa chừng và không rò rỉ WakeLock do lệch bộ đếm tham chiếu → AN TOÀN.
```

#### 6. Commands Executed

| Lệnh đã chạy | Exit Code | Kết quả |
| :--- | :---: | :--- |
| `.\gradlew.bat compileDebugKotlin; .\gradlew.bat testDebugUnitTest --tests "com.personal.apptruyen.data.repository.DownloadRepositoryTest" --tests "com.personal.apptruyen.download.DownloadServiceTest" --tests "com.personal.apptruyen.data.remote.WikidichScraperTest"` | `0` | `BUILD SUCCESSFUL` — Biên dịch sạch và toàn bộ unit test hiện có của 3 class mục tiêu đều pass. |
| `node .agents/scripts/plan-approval.js status --plan docs/plans/2026-10-16-wikidich-rate-limit-and-doh-implementation-plan.md` | `0` | Trạng thái `Ready for Review`, cấu trúc file plan hợp lệ. |

#### 7. Phát Hiện Mới (Fresh Findings & Regression Check)

##### Các Tấn Công Đã Thử (Attempted Attack Vectors)
1. **Kịch bản phản biện 1 — Vòng lặp đệ quy DNS (`ResilientDohDns` → `OkHttpClient` → `ResilientDohDns`) hoặc rò rỉ Cookie sang máy chủ DoH**:
   - *Giả thuyết tấn công*: Nếu `ResilientDohDns` dùng lại `OkHttpClient` chính trong `AppModule` hoặc gọi DoH bằng tên miền (`https://cloudflare-dns.com/dns-query`), mỗi lần phân giải DNS sẽ kích hoạt lại chính `ResilientDohDns.lookup()`, gây `StackOverflowError`, đồng thời `WebViewCookieHandler` có thể đính kèm cookie người dùng.
   - *Kiểm chứng phòng thủ*: Thiết kế ở Bước 1 tách riêng `bootstrapClient` dùng `.dns(Dns.SYSTEM)` (không gắn `CookieJar`) và chỉ gọi tới các URL chứa địa chỉ IP literal (`https://1.1.1.1/dns-query`, `https://1.0.0.1/dns-query`, `https://8.8.8.8/resolve`). Thêm vào đó, đầu hàm `lookup` kiểm tra `isLocalOrIpLiteral(normalizedHost)` để trả về ngay qua `systemDns`. Vòng lặp đệ quy và rò rỉ cookie bị triệt tiêu hoàn toàn (`[SRC]` `AppModule.kt:53-62`).
2. **Kịch bản phản biện 2 — Rò rỉ `PowerManager.WakeLock` do gọi `acquire(timeout)` lặp lại trên `WakeLock` mặc định (reference-counted)**:
   - *Giả thuyết tấn công*: Trên Android, `PowerManager.newWakeLock(...)` mặc định có `isReferenceCounted = true`. Nếu `renewWakeLock()` gọi `wakeLock?.acquire(WAKELOCK_TIMEOUT_MS)` sau mỗi chương mà không tắt reference counting, bộ đếm tăng lên `N + 1`, khiến 1 lệnh `it.release()` trong `releaseWakeLock()` (`DownloadService.kt:394-399`) không thực sự nhả `WakeLock`.
   - *Kiểm chứng phòng thủ*: Bước 4 của plan đã chỉ định rõ việc gọi `setReferenceCounted(false)` trong `acquireWakeLock()` trước khi `acquire(WAKELOCK_TIMEOUT_MS)`. Với `setReferenceCounted(false)`, mọi lời gọi `acquire(timeout)` kế tiếp chỉ cập nhật lại thời điểm hết hạn mà không tăng bộ đếm, và 1 lời gọi `release()` duy nhất trong `releaseWakeLock()` giải phóng hoàn toàn `WakeLock` (`[SRC]` `DownloadService.kt:381-399`).
3. **Kịch bản phản biện 3 — Hỏng các unit test hiện có dùng `MockWebServer` hoặc `DownloadRepositoryTest` khi thêm Fail-Fast HTTP `429`**:
   - *Giả thuyết tấn công*: (a) Trong `DownloadRepositoryTest.kt:340-355`, test `downloadChapters aborts early after MAX_CONSECUTIVE_FAILED_BATCHES completely failed batches` giả lập lỗi bằng `ScraperException.HttpException(429, "https://sstruyen.pics")`. Nếu nhánh fail-fast `403`/`429`/`503` áp dụng cho mọi nguồn, test này sẽ ném exception ở ngay lô 1 thay vì chạy đủ 3 lô (12 chương). (b) Các test scraper dùng `MockWebServer` gọi tới `localhost` sẽ bị chậm nếu đi qua DoH.
   - *Kiểm chứng phòng thủ*: (a) Điều kiện fail-fast trong `fetchChapterWithRetry` và `downloadChapters` được khóa chặt vào `source.sourceId == WIKIDICH_SOURCE_ID` (`"wikidich"`), nên nguồn `sstruyen` và mock mặc định trong `DownloadRepositoryTest` giữ nguyên hành vi 100% (`[SRC]` `DownloadRepositoryTest.kt:63-64,340-355`). (b) `ResilientDohDns` bypass ngay `localhost`, `127.0.0.1`, `::1` và mọi IP literal về `systemDns.lookup(hostname)` trước khi chạm tới DoH (được kiểm chứng bởi TC-07).

#### 8. Danh Sách Lỗi Theo Severity
- **`[BLOCKER]`**: `0`
- **`[MAJOR]`**: `0`
- **`[MINOR]` (Advisory Recommendations — không chặn phê duyệt)**:
  1. `[MINOR]` Tại Mục 1 (dòng 28) và Mục 4 (dòng 86), plan gọi định dạng `application/dns-json` của Cloudflare/Google là "RFC 8427". Trên thực tế, `application/dns-json` (`Status`, `Answer[].type`, `Answer[].data`) là Google/Cloudflare DoH JSON schema, còn RFC 8427 là đặc tả JSON khác cho bản tin DNS. Cấu trúc JSON mà plan parse (`Status == 0`, `Answer`, `type == 1 | 28`, `data`) hoàn toàn chính xác với tài liệu chính thức của `1.1.1.1/dns-query` và `8.8.8.8/resolve`; khi viết KDoc trong `ResilientDohDns.kt` chỉ cần ghi rõ "Cloudflare/Google DoH JSON API (`application/dns-json`)".

**Kết luận**: APPROVED · BLOCKER 0 · MAJOR 0

[PLAN_APPROVED]

