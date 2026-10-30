# Hướng dẫn Scraping

> **Cập nhật lần cuối:** 2026-10-30 13:45

## Nguồn hoạt động hiện tại

1. **SsTruyện (`sstruyen.pics`)** — sourceId: `sstruyen`
2. **Wikidich (`wikicv.org`)** — sourceId: `wikidich`

---

## 1. Wikidich (`wikicv.org`)

Wikidich là nguồn truyện convert phong phú. Sử dụng cookie session `express.sid` để xác thực người dùng.

### URL Patterns

| Chức năng | URL | Ví dụ |
| --- | --- | --- |
| Trang chủ | `https://wikicv.org` | — |
| Tìm kiếm (kèm thể loại, Truyện nam, trạng thái & phân trang) | `/tim-kiem?q={keyword}&qs=1[&genre={24-hex}][&gender=5794f03dd7ced228f4419196][&status=5794f03dd7ced228f4419191][&start={start}]` | `/tim-kiem?q=h%E1%BB%87+th%E1%BB%91ng&qs=1&genre=5794f03dd7ced228f44191a4&gender=5794f03dd7ced228f4419196&status=5794f03dd7ced228f4419191&start=20` |
| Chi tiết truyện | `/truyen/{slug}` | `/truyen/de-ba` |
| Đọc chương | `/truyen/{slug}/{chapterSlug}` | `/truyen/de-ba/chuong-1` |
| Danh sách chương AJAX | `/book/index?bookId={id}&start={start}&size={pageSize}&signKey={key}&sign={sign}` | AJAX phân trang động theo `loadBookIndex(0, size, false)` (mặc định `501` trên `wikicv.org`, fallback `99`) |
| Thể loại (có/không lọc Truyện nam, sắp xếp Mới nhất) | `/tim-kiem?qs=2&{genre}[&gender=5794f03dd7ced228f4419196][&status=5794f03dd7ced228f4419191]&so=4&vo=1[&start={start}]` | `/tim-kiem?qs=2&genre=5794f03dd7ced228f44191a4&gender=5794f03dd7ced228f4419196&status=5794f03dd7ced228f4419191&so=4&vo=1` |
| Hoàn thành (khi bật Truyện nam `maleOnly = true` — mặc định) | `/tim-kiem?qs=2&gender=5794f03dd7ced228f4419196&status=5794f03dd7ced228f4419191&so=4&vo=1[&start={start}]` | Phân trang theo `start = (page - 1) * 20` |
| Hoàn thành (khi tắt Truyện nam `maleOnly = false` — nhánh nhanh) | `/bang-xep-hang?so=5[&start={start}]` (fallback `/tim-kiem?qs=2&status=5794f03dd7ced228f4419191&so=4&vo=1[&start={start}]`) | Phân trang theo `start = (page - 1) * 20` |

### 19 Thể loại Chính thống (`Loại hình` — `genre=<24-hex ObjectId>`) & Bộ lọc Truyện nam độc lập
- **19 thể loại chính thống thuộc mục `Loại hình` (`genre=`) trên `wikicv.org`**:
  - `tinh-cam` (`genre=5794f03dd7ced228f44191a2`), `lam-su-nghiep` (`genre=6785c24e23deea36d63efe8e`), `tien-hiep` (`genre=5794f03dd7ced228f44191a4`), `huyen-huyen` (`genre=57d17c94d7ced218fcd2734b`), `khoa-hoc-vien-tuong` (`genre=5794f03dd7ced228f44191a9`), `mat-the` (`genre=5794f03dd7ced228f44191ac`), `sinh-ton` (`genre=5fd54e6523deea0a1a87e9b9`), `tranh-ba` (`genre=5a1ce8f512fb556782b06f7e`), `vo-hiep` (`genre=5794f03dd7ced228f44191a3`), `trinh-tham` (`genre=5794f03dd7ced228f44191a7`), `kinh-di` (`genre=5794f03dd7ced228f44191a6`), `quan-truong` (`genre=5b10edd012fb550d90428316`), `kinh-thuong` (`genre=5794f03dd7ced228f44191d4`), `quan-su` (`genre=6785b91d23deea36d63efe7f`), `xay-dung` (`genre=5fd3abb623deea0a1a87e9b5`), `lam-ruong` (`genre=5794f03dd7ced228f44191c0`), `huyen-hoc` (`genre=58458dc2d7ced23af03679c5`), `phim-anh` (`genre=596bd83012fb557cb030fc5a`), `manga-anime` (`genre=5794f03dd7ced228f44191a8`).
  - Các nhãn phụ thuộc nhóm `tag=`, `age=`, `official=`, `ending=` và mục cũ ngừng sử dụng `Võng du` (`genre=5794f03dd7ced228f44191a5`, nay đã chuyển sang `tag=` `Trò chơi` / `Trò chơi xâm lấn`) đã được gỡ bỏ khỏi `getGenreList()` và `GENRE_QUERY_MAP`.
- **Hậu kiểm đối tượng độc giả (`NON_MALE_WIKIDICH_GENDERS`)**:
  - Tham số đối tượng độc giả trên `wikicv.org` dùng khóa `gender=5794f03dd7ced228f4419196` (**Nam sinh / Truyện nam**).
  - Trong `WikidichScraper.filterStories` và `SearchViewModel.applyLocalFilters`, khi `maleOnly = true`, hệ thống chuẩn hóa Unicode NFC và loại bỏ đầy đủ 7 nhãn Tính hướng không phải Nam sinh: `"ngôn tình"`, `"đam mỹ"`, `"bách hợp"`, `"nữ tần"`, `"nữ tôn"`, `"không cp"`, `"đa nguyên"`.
- **Phân biệt `qs=1` và `qs=2`**:
  - `search(...)` dùng `/tim-kiem?q=$encoded&qs=1...` (chế độ tìm kiếm từ khóa kết hợp `genre=`, `gender=`, `status=`, `start=`).
  - `getStoriesByGenre(...)` và `getCompletedStories(...)` giữ nguyên `/tim-kiem?qs=2&...&so=4&vo=1` (chế độ lọc nâng cao sắp xếp theo Mới nhất).

### Chữ ký AJAX danh sách chương
- Trang chi tiết truyện nhúng script chứa `bookId`, `signKey`.
- Thuật toán ký:
  ```kotlin
  cut = (start + size) % signKey.length
  text = (signKey + start + size)
  fuzzy = text.substring(cut) + text.substring(0, cut)
  sign = SHA256(fuzzy).lowercase()
  ```
- Phân trang: Đọc selector `ul.pagination li a[data-action="loadBookIndex"][data-start=...]` để xác định trang kế tiếp; `totalPages` được tính từ `maxStart` (`(maxStart / 99) + 1`) kết hợp số trang hiển thị để không bị cắt ở 5 trang với truyện dài hàng nghìn chương.
- **Số chương trên trang chi tiết & trong danh sách tìm kiếm**:
  - Thẻ `.book-item` trên trang tìm kiếm không chứa số chương; `WikidichScraper.search()` làm giàu số chương cho tối đa 5 kết quả đầu (`ENRICH_MAX_ITEMS = 5`, `ENRICH_BATCH_SIZE = 2`, có `delay(crawlDelayMs)` giữa các lô và dừng sớm qua `AtomicBoolean` khi gặp HTTP `403`/`429`/`503` hoặc `LoginRequiredException`).
  - Trên trang chi tiết, `extractTotalChaptersFromDetail()` quét `.cover-info a[href*='/truyen/']` với `LATEST_CHAPTER_TEXT_REGEX` và `LATEST_CHAPTER_HREF_REGEX`. Nếu truyện đã hoàn thành không hiện dòng `"Mới nhất"` hoặc chương cuối là phiên ngoại (trả về `0`), `fetchTotalChaptersFromBookIndex()` tự động truy vấn `/book/index` tại `start = 0` và nhảy theo `data-start` lớn nhất (tối đa 2 bước nhảy) để đếm chính xác tổng số chương, đồng thời lưu vào `ajaxFragmentCache` (TTL 10 phút, tối đa 30 mục) để `getFirstPageChapters()` tái sử dụng ngay lập tức mà không tốn thêm request mạng.

### CSS Selectors
```css
Tìm kiếm:      .book-item (.info-col a.tooltipped, h5.book-title)
  Ảnh bìa:     .cover-col img (qua extractCoverUrl)
Chi tiết:
  Tiêu đề:     .cover-info h2, .cover-info h1, .book-info h2
  Tác giả:     .cover-info a[href*='/tac-gia/']
  Thể loại:    .cover-info a[href*='/tim-kiem?'], .book-desc a[href*='/tim-kiem?']
  Chương mới:  .cover-info a[href*='/truyen/'] (Mới nhất: Chương X)
  Ảnh bìa:     .book-info .cover-wrapper img, .cover-info img, .book-info img (qua extractCoverUrl, tự động loại bỏ tham số ?o=1 trên /photo/ và lọc bỏ default-cover / default-avatar / data URI)
  Mô tả:       .book-desc-detail, .book-desc
Đọc chương:
  Nội dung:    #bookContentBody, .book-content, #chapter-content
```

### Lọc Watermark
Nội dung chương thường kèm watermark được lọc bằng regex:
`【❁.*\(Wikidich\).*❁】` và text `(Wikidich)`.

### Xác thực tài khoản (Source Authentication)
- Cookie session: `express.sid`.
- Đăng nhập: WebView hướng dẫn người dùng tới SSO forum Dịch Tiếng Hoa (`forum.dichtienghoa.com/dth/auth`), Referer: `https://wikicv.org/`.
- Fallback: Dán trực tiếp chuỗi cookie từ trình duyệt PC (`express.sid=...`).
- Quản lý phiên: `SourceAuthManager`, cookie lưu trữ host-only qua `CookieStore`.

---

## 2. SsTruyện (`sstruyen.pics`)

Nguồn truyện hỗ trợ đọc trực tuyến, tải offline tốc độ cao qua REST API, tự động tương thích ngược dữ liệu từ domain cũ (`sstruyen.net`).

### URL Patterns
- **Trang chủ**: `https://sstruyen.pics`
- **Tìm kiếm**: `https://sstruyen.pics/tim-kiem?keyword={keyword}`
- **Bộ lọc**: `https://sstruyen.pics/danh-sach/bo-loc?type={type}&status={status}&sort={sort}&genre={genre}&page={page}`
  - `status=completed`: Lọc truyện đã hoàn thành (100% truyện trả về chuẩn Đã hoàn thành).
  - `sort=updatedAt`: Luôn sắp xếp truyện mới cập nhật nhất lên đầu.
  - `type`: `0` (<100c), `1` (100-500c), `2` (500-1000c), `3` (>1000c).
- **API thể loại**: `GET https://api.sstruyen.pics/genres` (trả về JSON Array 93 thể loại sạch sẽ, có fallback HTML).
- **Chi tiết truyện**: `https://sstruyen.pics/truyen/{slug}`
- **API chi tiết truyện**: `GET https://api.sstruyen.pics/novels/{slug}` (trả về JSON chứa `id`, `chapterCount`, `coverUrl` để cache `novelId` và ảnh bìa dự phòng).
- **API danh sách chương**: `GET https://api.sstruyen.pics/novels/{novelId}/chapters?page={page}&limit=50&sort=asc`
- **Đọc chương mới**: `https://sstruyen.pics/doc-truyen/{novelSlug}-{chapterSlug}-{chapterId}`
- **Ảnh bìa (CDN)**: `https://file.sstruyen.pics/files/covers/{slug}.webp`

### CSS Selectors & Trích xuất
- **Danh sách item**: `a[itemprop=hasPart]`, fallback `div.home-truyendecu, div.each_truyen, div.item`
- **Ảnh bìa (`extractImageSrc`)**: Ưu tiên `data-src`, `data-lazy-src`, `data-original` trước `src` và loại bỏ placeholder lazy-load `data:image/png;base64,...` hoặc `/imgs/logo.webp`. Chuẩn hóa đường dẫn `/covers/...` về CDN `https://file.sstruyen.pics/files/covers/...`.
- **Tiêu đề truyện**: `h1`, `p[itemprop=name]`, `.title`
- **Tác giả**: `div[itemprop=author] span[itemprop=name]`, `a[href*=/tac-gia/]`
- **Số chương khi tìm kiếm/danh sách**: `span[itemprop=dateModified]`, trích xuất regex `(?:Chương|Hồi|C)\s*(\d+)` hoặc `(\d+)\s*chương`.
- **Trạng thái**: `span[itemprop=bookFormat]` ("Đã hoàn thành" / "Đang phát hành")
- **Thể loại**: REST API `api.sstruyen.pics/genres`, fallback `a[href*=/the-loai/]`
- **Mô tả**: `div[itemprop=description]`, `.desc-text`
- **Nội dung chương mới**: `div.prose`, `div.wrapper.prose` (xử lý đồng thời cả text node ngăn cách bằng `<br><br>` lẫn các thẻ `<p>`, loại bỏ khối footer `div.text-center` chứa câu thông báo lỗi mẫu), ngắt đoạn `\n\n`.
- **Tương thích ngược chương cũ (`sstruyen.net`)**: 9 selectors kế thừa (`#chapter-c, .chapter-c, #chapter-content, .chapter-content, #content, .content, div.text-justify, .reading-content, .truyen-content`) và `cleanHtml` xử lý `<br>`. Bảo toàn 100% tiêu đề và nội dung chương đã tải offline.

---

## Nguồn đã gỡ bỏ (Lưu trữ tương thích ngược)

> [!CAUTION]
> Các nguồn sau đã ngừng hỗ trợ cào trực tuyến. Các file scraper đã được xóa khỏi codebase.
> Toàn bộ truyện/chương đã tải trước đó trong Room Database vẫn được bảo toàn và đọc offline bình thường.

### ~~Tàng Thư Viện (tangthuvien.org / truyen.tangthuvien.vn)~~ — ĐÃ GỠ BỎ (2026-10-30)
- URL gốc: `https://truyen.tangthuvien.vn` / `https://tangthuvien.org`
- Lý do: Tàng Thư Viện chính thức thông báo ngừng hoạt động và cảnh báo tên miền `tangthuvien.org` là website giả mạo.
- `TangThuVienScraper.kt` và `TangThuVienScraperTest.kt` đã bị xóa; tên miền `tangthuvien.org` và `tangthuvien.vn` đã bị gỡ khỏi `WebViewContentLoader.ALLOWED_DOMAINS` và bị chặn cứng trong `CoilImageInterceptor.BLOCKED_IMAGE_HOSTS`.
- Toàn bộ truyện đã lưu trong Room DB (cả định dạng ID mới `"tangthuvien_<slug>"` lẫn ID cũ `"<slug>"`) vẫn đọc offline bình thường qua `StoryRepository.findCachedStory` (`deprecatedSourceIdForUrl`).

### ~~TruyenCom (truyencom.com)~~ — ĐÃ GỠ BỎ (2026-10-13)
- URL gốc: `https://truyencom.com`
- `TruyenComScraper.kt` đã bị xóa.

### ~~TruyenFull (truyenfull.today)~~ — ĐÃ GỠ BỎ (2026-10-13)
- URL gốc: `https://truyenfull.today`
- `TruyenFullScraper.kt` đã bị xóa.

### ~~LacaTruyen (lacatruyen.buzz)~~ — ĐÃ GỠ BỎ (2026-10-19)
- `CryptoJS.kt` đã bị xóa; tài liệu `docs/scraping_lacatruyen_decryption.md` được giữ làm tư liệu lịch sử.

### ~~MeTruyenChu (metruyenchu.com.vn)~~ — ĐÃ NGỪNG HOẠT ĐỘNG (2026-05-30)

---

## Rate Limiting, Cache TTL & DNS-over-HTTPS (DoH)

- **Nguồn thông thường (`sstruyen`)**:
  - Tải song song theo lô `DOWNLOAD_BATCH_SIZE = 4` chương/lô, thời gian chờ cơ sở `250ms` giữa các lô (nhân đôi lên tối đa `3000ms` khi có lỗi).
- **Nguồn Wikidich (`wikidich` / `wikicv.org`)**:
  - Do máy chủ Wikidich và Cloudflare WAF áp dụng giới hạn tốc độ nghiêm ngặt theo IP và session `express.sid`, `DownloadRepository` tự động chuyển sang tải tuần tự từng chương một (`WIKIDICH_BATCH_SIZE = 1`) với thời gian nghỉ ngẫu nhiên **`15–20 giây/chương`** (`WIKIDICH_MIN_DELAY_MS = 15_000L`, `WIKIDICH_MAX_DELAY_MS = 20_000L`).
  - Khi gặp lỗi mạng tạm thời, thời gian chờ trước khi thử lại là `5_000ms` (`WIKIDICH_RETRY_DELAY_MS`).
  - **Fail-Fast chống khóa IP**: Nếu gặp mã HTTP `403`, `429`, hoặc `503` (Cloudflare chặn/rate-limit), dừng ngay lập tức mà không retry và phát thông báo tiếng Việt trên `DownloadService`.
  - Giữa các trang AJAX danh sách chương (`getRemainingChapters`), tự động nghỉ `crawlDelayMs` (`300ms`).
- **DNS-over-HTTPS (`ResilientDohDns`)**:
  - Gắn trực tiếp vào `OkHttpClient` (`AppModule`) để vượt qua tường lửa DNS nội bộ (Pi-hole, FortiGate, Cisco Umbrella...) trên mạng WiFi công ty thường chặn các tên miền giải trí (`wikicv.org`, `sstruyen.pics`).
  - Phân giải tên miền qua HTTPS cổng 443 bằng JSON DoH của Cloudflare (`https://1.1.1.1/dns-query`, `https://1.0.0.1/dns-query`) và Google (`https://8.8.8.8/resolve`), lọc bỏ IP sinkhole/bogon (`0.0.0.0`, `127.0.0.1`, LAN...), bảo toàn `hostname` cho TLS SNI (`InetAddress.getByAddress(hostname, rawBytes)`), cache TTL 10 phút và tự động fallback về `Dns.SYSTEM`.
- **Smart HTTP caching & Cache TTL (`BaseScraper`)**:
  - `CacheEntry` lưu `timestamp = nowMs()` bắt buộc và áp dụng `CACHE_TTL_MS = 10 * 60 * 1000L` (10 phút) kết hợp giới hạn `MAX_CACHE_SIZE = 100` và ETag / `Last-Modified` (`If-None-Match` / `If-Modified-Since` → `304 Not Modified`).
- **Chuẩn hóa HTTPS (`usesCleartextTraffic="false"`)**:
  - Mọi URL ảnh bìa và URL truyện `http://` (trừ `http://localhost` và `http://127.0.0.1` phục vụ `MockWebServer`) đều được tự động nâng cấp lên `https://` tại `BaseScraper.validateCoverUrl`, `WikidichScraper.fixUrl`, `StoryRepository.resolveSafeCoverUrl` và `StoryEntity.toDomain()`.

## WebViewContentLoader

- Allowed domains: `wikicv.org`, `wikicv.net`, `sstruyen.pics`, `api.sstruyen.pics`, `file.sstruyen.pics`, `sstruyen.vn`, `sstruyen.net` (đã loại bỏ `tangthuvien.org` và `tangthuvien.vn`)
- Host validation: `WebViewContentLoader.isAllowedDomain(host, allowedDomains)` kiểm tra khớp chính xác `normalizedHost == domain` hoặc subdomain có dấu chấm `normalizedHost.endsWith(".$domain")` (chặn tên miền giả mạo kiểu `evilwikicv.org`)
- Lifecycle & Cancellation: tự động `stopLoading()` và `destroy()` trên Main thread (`mainHandler`) cả khi timeout lẫn khi coroutine bị hủy (`finally` của `withTimeoutOrNull`)
- Timeout: 15s
- JS selectors: `#chapter-c`, `#chapter-content`, `.chapter-c`, `.chapter-content`, `#content`, `.truyen-content`, `#js-read__content`, `div.text-justify`
- Block images (`blockNetworkImage = true`) để tải nhanh hơn

## Xử lý lỗi

| Lỗi | Xử lý |
| --- | --- |
| `LoginRequiredException` | Nguồn yêu cầu đăng nhập (Wikidich), hiển thị banner tiếng Việt |
| `HttpException(403/429/503)` | Với Wikidich: dừng tải ngay lập tức (Fail-Fast, không retry) để tránh bị khóa IP dài hạn, báo tiếng Việt trên thông báo tải |
| `CircuitOpenException` | Nguồn tạm đóng do lỗi liên tiếp, phục hồi sau cooldown |
| Timeout / 5xx / 429 | Retry tối đa 3 lần qua `BaseScraper.fetchDocumentWithRetry` (`isRetryableScraperException`), không retry lỗi 4xx vĩnh viễn (`404`, `403`, `401`) hoặc `ParseException` |
| 404 | Bỏ qua chapter, log warning |
| HTML rỗng | Thử fallback selectors → WebView |
| Encoding | Force UTF-8 |
| JS-rendered | WebView headless fallback |
