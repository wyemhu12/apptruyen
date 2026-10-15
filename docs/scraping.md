# Hướng dẫn Scraping

> **Cập nhật lần cuối:** 2026-10-14 19:40

## Nguồn hoạt động hiện tại

1. **Tàng Thư Viện (`tangthuvien.org`)** — sourceId: `tangthuvien`
2. **SsTruyện (`sstruyen.pics`)** — sourceId: `sstruyen`
3. **Wikidich (`wikicv.org`)** — sourceId: `wikidich`

---

## 1. Wikidich (`wikicv.org`)

Wikidich là nguồn truyện convert phong phú. Sử dụng cookie session `express.sid` để xác thực người dùng.

### URL Patterns

| Chức năng | URL | Ví dụ |
| --- | --- | --- |
| Trang chủ | `https://wikicv.org` | — |
| Tìm kiếm | `/tim-kiem?qs=1&q={keyword}` | `/tim-kiem?qs=1&q=tien+hiep` |
| Chi tiết truyện | `/truyen/{slug}` | `/truyen/de-ba` |
| Đọc chương | `/truyen/{slug}/{chapterSlug}` | `/truyen/de-ba/chuong-1` |
| Danh sách chương AJAX | `/book/index?bookId={id}&start={start}&size=99&signKey={key}&sign={sign}` | AJAX phân trang 99 chương/lần |
| Thể loại | `/tim-kiem?qs=1&{genre/tag/age}&status={status}&start={start}` | `/tim-kiem?qs=1&tag=5794f03ed7ced228f44191de&status=5794f03dd7ced228f4419191` |
| Hoàn thành | `/tim-kiem?qs=1&status=5794f03dd7ced228f4419191&start={start}` | Phân trang theo `start = (page - 1) * 20` |

### Chữ ký AJAX danh sách chương
- Trang chi tiết truyện nhúng script chứa `bookId`, `signKey`.
- Thuật toán ký:
  ```kotlin
  cut = (start + size) % signKey.length
  text = (signKey + start + size)
  fuzzy = text.substring(cut) + text.substring(0, cut)
  sign = SHA256(fuzzy).lowercase()
  ```
- Phân trang: Đọc selector `ul.pagination li a[data-action="loadBookIndex"][data-start=...]` để xác định trang kế tiếp.
- **Số chương trong danh sách**: Thẻ `.book-item` trên trang tìm kiếm không chứa số chương; `WikidichScraper` trích xuất số chương từ link `"Mới nhất"` (`.cover-info a[href*='/truyen/']`) trên trang chi tiết qua `extractTotalChaptersFromDetail()` và làm giàu song song (`enrichWithChapterCounts()`, batch 5, timeout 7s).

### CSS Selectors
```css
Tìm kiếm:      .book-item (.info-col a.tooltipped, h5.book-title)
Chi tiết:
  Tiêu đề:     .cover-info h2, .cover-info h1, .book-info h2
  Tác giả:     .cover-info a[href*='/tac-gia/']
  Thể loại:    .cover-info a[href*='/tim-kiem?'], .book-desc a[href*='/tim-kiem?']
  Chương mới:  .cover-info a[href*='/truyen/'] (Mới nhất: Chương X)
  Ảnh bìa:     .cover-info img, .book-info img
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

## 2. Tàng Thư Viện (`tangthuvien.org`)

Trang web Tàng Thư Viện mới sử dụng React/Next.js (App Router). Dữ liệu được Server-Side Rendering nên vẫn có thể dùng Jsoup để parse.

**Cấu trúc URL:**
- Chi tiết truyện: `https://tangthuvien.org/{slug}`
- Đọc chương: `https://tangthuvien.org/{slug}/{số_thứ_tự_chương}`
- Tìm kiếm/Lọc: `https://tangthuvien.org/truyen?keyword={keyword}&status=COMPLETED&genre={genre}`

**Lưu ý khi Scraping:**
- Danh sách chương (chapter list) được fetch ngầm qua API của Next.js khi click UI.
- Giải pháp: Parse biến `totalChapters` từ thẻ báo số lượng chương (`<span>241<!-- --> Chương</span>`), sau đó tự động khởi tạo danh sách URL chương: `/{slug}/1`, `/{slug}/2` với tên mặc định "Chương 1", "Chương 2".
- Tên gốc của chương được parse khi người dùng bấm vào xem trang chi tiết chương (`h1, h2, div.font-semibold`). Title được extract và tự động cập nhật vào DB qua `getChapterContentWithTitle()`.
- Hỗ trợ fallback đổi URL cũ (`truyen.tangthuvien.vn/doc-truyen/{slug}`) thành URL mới trong `StoryDetail` và `ChapterContent`.
- **Chú ý prefix `#`**: Trang web TTV hiển thị title chương dạng `#8 Chương 8: Tên chương`. Regex `TITLE_HASH_PREFIX = ^#\d+\s+` được dùng để strip prefix này khỏi cả title và paragraph content.
- **Số chương trong danh sách**: Hàm `enrichWithChapterCounts()` fetch detail page song song (batch 5, timeout 7s, max 10 stories) để bổ sung.

---

## 3. SsTruyện (`sstruyen.pics`)

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

### ~~TruyenCom (truyencom.com)~~ — ĐÃ GỠ BỎ (2026-10-13)
- URL gốc: `https://truyencom.com`
- `TruyenComScraper.kt` đã bị xóa.

### ~~TruyenFull (truyenfull.today)~~ — ĐÃ GỠ BỎ (2026-10-13)
- URL gốc: `https://truyenfull.today`
- `TruyenFullScraper.kt` đã bị xóa.

### ~~MeTruyenChu (metruyenchu.com.vn)~~ — ĐÃ NGỪNG HOẠT ĐỘNG (2026-05-30)

---

## Rate Limiting (chung)

- Delay 200-500ms giữa mỗi request khi tải hàng loạt
- Retry 3 lần nếu request fail (exponential backoff: 1s, 2s, 4s)
- User-Agent giả lập Chrome Mobile
- Smart HTTP caching: ETag + Last-Modified → If-None-Match/If-Modified-Since → 304 handling

## WebViewContentLoader

- Allowed domains: `wikicv.org`, `wikicv.net`, `tangthuvien.org`, `sstruyen.vn`
- Timeout: 15s
- JS selectors: `#chapter-c`, `#chapter-content`, `.chapter-c`, `.chapter-content`, `#content`, `.truyen-content`, `#js-read__content`, `div.text-justify`
- Block images (`blockNetworkImage = true`) để tải nhanh hơn
- URL origin validation: chỉ cho phép domains trong `ALLOWED_DOMAINS`

## Xử lý lỗi

| Lỗi | Xử lý |
| --- | --- |
| `LoginRequiredException` | Nguồn yêu cầu đăng nhập (Wikidich), hiển thị banner tiếng Việt |
| `CircuitOpenException` | Nguồn tạm đóng do lỗi liên tiếp, phục hồi sau cooldown |
| Timeout | Retry 3 lần, tăng timeout |
| 404 | Bỏ qua chapter, log warning |
| HTML rỗng | Thử fallback selectors → WebView |
| Encoding | Force UTF-8 |
| JS-rendered | WebView headless fallback |
