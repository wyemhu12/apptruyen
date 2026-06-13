# Hướng dẫn Scraping

> **Cập nhật lần cuối:** 2026-05-30 08:46

## TruyenCom (truyencom.com)

### URL Patterns

| Chức năng | URL | Ví dụ |
| --- | --- | --- |
| Trang chủ | `https://truyencom.com` | — |
| Tìm kiếm | `/tim-kiem?tukhoa={keyword}` | `/tim-kiem?tukhoa=pham+nhan+tu+tien` |
| Chi tiết truyện | `/{slug}.{id}/` | `/pham-nhan-tu-tien.14/` |
| Đọc chương | `/{slug}/chuong-{N}.html` | `/pham-nhan-tu-tien/chuong-1.html` |
| Truyện full | `/truyen-full/` | `/truyen-full/trang-2/` (phân trang) |
| Thể loại (full) | `/truyen-{genre}/full/` | `/truyen-tien-hiep/full/trang-3/` |
| **Thể loại sort API** | `/api/list/{catId}/{sort}/{page}/{limit}` | `/api/list/39/new/1/25` |
| Tác giả | `/tac-gia/{slug}/` | `/tac-gia/vong-ngu/` |
| Genre links | `a[href*=truyen-]` on homepage | 42 thể loại trong nav menu |

### CSS Selectors

#### Trang tìm kiếm

```css
Kết quả: h3.truyen-title a
         .list-truyen .truyen-title a
         .col-truyen-main .truyen-title a
```

#### Trang chi tiết

```css
Tiêu đề:    h1, .title, h3.title
Tác giả:    a[href*=tac-gia]
Thể loại:   .info-holder a[href*=truyen-]   /* SCOPED — tránh lấy nhầm nav menu */
Mô tả:      .desc-text, .desc, div[itemprop=description]
Ảnh bìa:    img.book, .book img, .info-holder img, div[itemprop=image] img
Chương:      a[href*=chuong-]
Phân trang:  a[href*=trang-] (Last » link → trang-N, 50 chương/trang)
```

#### Trang đọc chương

```css
Nội dung:   #chapter-c
             #chapter-content
             .chapter-c
             .chapter-content
             #content
             .truyen-content
```

> ⚠️ **Lưu ý**: Nội dung chương được load qua JavaScript.
> Đã implement `WebViewContentLoader` làm fallback khi Jsoup trả về rỗng.
> Flow: OkHttp+Jsoup (fast) → nếu < 50 chars → WebView headless (JS render).

### JSON API (Server-side Sort)

> ⚠️ **Quan trọng**: Trang thể loại full (`/truyen-{genre}/full/`) load nội dung 3 tab (Mới/Hot/Chọn lọc) qua **AJAX**, không phải HTML tĩnh. HTML tĩnh chỉ chứa danh sách HOT.

| Endpoint | Response |
| --- | --- |
| `/api/list/{categoryID}/new/{page}/25` | Truyện mới nhất (JSON) |
| `/api/list/{categoryID}/hot/{page}/25` | Truyện hot (JSON) |
| `/api/list/{categoryID}/selective/{page}/25` | Truyện chọn lọc (JSON) |

- `categoryID` extract từ `var categoryID=X;` trong inline script tag trên genre page
- Response: `{ "items": [{storyID, title, alias, author, chapters, ...}], "total": "N" }`
- `alias` = slug dùng để tạo URL: `/{alias}.{storyID}/`

---

## ~~MeTruyenChu (metruyenchu.com.vn)~~ — ĐÃ NGỪNG HOẠT ĐỘNG (2026-05-30)

> [!CAUTION]
> metruyenchu.com và metruyenchu.com.vn đã ngừng hoạt động.
> `MeTruyenChuScraper.kt` đã bị xóa khỏi codebase.
> Truyện đã tải trước đó vẫn đọc offline bình thường (sourceId="metruyenchu" giữ nguyên trong DB).

<details>
<summary>Thông tin cũ (lưu trữ)</summary>

### URL Patterns (cũ)

| Chức năng | URL | Ví dụ |
| --- | --- | --- |
| Trang chủ | `https://metruyenchu.com.vn` | — |
| Tìm kiếm | `/tim-kiem?tukhoa={keyword}` | `/tim-kiem?tukhoa=nghich+thien` |
| Chi tiết truyện | `/{slug}` | `/nghich-thien-ta-than` |
| Đọc chương | `/{slug}/chuong-{N}-{hashId}` | `/nghich-thien-ta-than/chuong-1-5IgkcQhl5YDW` |
| Truyện full | `/danh-sach/truyen-full` | `/danh-sach/truyen-full?page=2` |
| Thể loại | `/the-loai/{slug}` | `/the-loai/tien-hiep` |

</details>

---

## TruyenFull (truyenfull.today)

> Cùng engine 8cache với TruyenCom. 46 thể loại. Server-side rendered — không cần WebView fallback.

### URL Patterns

| Chức năng | URL | Ví dụ |
| --- | --- | --- |
| Trang chủ | `https://truyenfull.today` | — |
| Tìm kiếm | `/tim-kiem/?tukhoa={keyword}` | `/tim-kiem/?tukhoa=pham+nhan` |
| Chi tiết truyện | `/{slug}/` | `/pham-nhan-tu-tien/` |
| Đọc chương | `/{slug}/chuong-{N}/` | `/pham-nhan-tu-tien/chuong-1/` |
| Phân trang chapter | `/{slug}/trang-{N}/#list-chapter` | `/pham-nhan-tu-tien/trang-2/#list-chapter` (50 chương/trang) |
| Thể loại hoàn thành | `/the-loai/{genre}/hoan/` | `/the-loai/tien-hiep/hoan/` |
| Phân trang thể loại | `/the-loai/{genre}/hoan/trang-{N}/` | `/the-loai/tien-hiep/hoan/trang-2/` |
| Truyện full | `/danh-sach/truyen-full/` | `/danh-sach/truyen-full/` |

### CSS Selectors

#### Danh sách truyện (search / browse)

```css
Kết quả: .row[itemscope] h3.truyen-title a
```

#### Trang chi tiết

```css
Tiêu đề:    h3.title[itemprop=name]
Tác giả:    a[itemprop=author]
Thể loại:   a[itemprop=genre]
Ảnh bìa:    .book img[itemprop=image]
Mô tả:      .desc-text[itemprop=description]
```

#### Danh sách chương

```css
Chương:      #list-chapter .list-chapter li a[href*=chuong-]
Phân trang:  ul.pagination.pagination-sm a[href*=trang-]
```

#### Trang đọc chương

```css
Nội dung:   #chapter-c[itemprop=articleBody]
```

> ✅ **Lưu ý**: Nội dung chương server-side rendered, không cần WebView fallback.

---

## Rate Limiting (chung)

- Delay 200-500ms giữa mỗi request khi tải hàng loạt
- Retry 3 lần nếu request fail (exponential backoff: 1s, 2s, 4s)
- User-Agent giả lập Chrome Mobile
- Smart HTTP caching: ETag + Last-Modified → If-None-Match/If-Modified-Since → 304 handling

## WebViewContentLoader

- Allowed domains: `truyencom.com`, `tangthuvien.vn`, `sstruyen.net`, `truyenfull.today`
- Timeout: 15s
- JS selectors: `#chapter-c`, `#chapter-content`, `.chapter-c`, `.chapter-content`, `#content`, `.truyen-content`, `#js-read__content`, `div.text-justify`
- Block images (`blockNetworkImage = true`) để tải nhanh hơn
- URL origin validation: chỉ cho phép domains trong `ALLOWED_DOMAINS`

## Xử lý lỗi

| Lỗi | Xử lý |
| --- | --- |
| Timeout | Retry 3 lần, tăng timeout |
| 404 | Bỏ qua chapter, log warning |
| HTML rỗng | Thử fallback selectors → WebView |
| Encoding | Force UTF-8 |
| JS-rendered | WebView headless fallback |
