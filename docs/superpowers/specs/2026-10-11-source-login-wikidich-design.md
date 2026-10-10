# Thiết kế: Đăng nhập nguồn truyện (WebView + dán cookie) & WikidichScraper

> Ngày: 2026-10-11 · Trạng thái: Chờ duyệt · Phương án: **C** (WebView login + fallback dán cookie)
> Phạm vi giai đoạn 1: **Wikidich**. LacaTruyen để giai đoạn 2 (dùng lại cùng hạ tầng).

## 1. Mục tiêu

- User đăng nhập Wikidich **một lần** trong app (WebView) → app tự dùng phiên đó cho mọi request.
- Nếu không đăng nhập được trong WebView (vd. dùng Google), user dán cookie copy từ trình duyệt PC.
- Thêm `WikidichScraper` vào hệ thống gộp nguồn (search / browse / đọc / tải).

**Ngoài phạm vi:** LacaTruyen (giai đoạn 2), tự động lưu mật khẩu, đồng bộ nhiều tài khoản.

## 2. Kết quả khảo sát kỹ thuật (đã kiểm chứng 2026-10-11)

### 2.1 Wikidich

| Hạng mục | Kết quả | Cách kiểm chứng |
|---|---|---|
| Domain hiện tại | `https://wikicv.org` (`wikisach.net`/`truyenwikidich.net` → `wikicv.net` → `wikicv.org`) | `curl -I` chuỗi redirect |
| Cloudflare challenge | Không (HTTP 200 với curl + UA mobile) | `curl` |
| Trang truyện `/truyen/{slug}-{id}` | Đọc được **không cần login** | `curl` |
| Nội dung chương | HTML tĩnh, `<p>` trong body, **không cần login**, không mã hoá (đã thử chương 1 và 170) | `curl` |
| Tìm kiếm `/tim-kiem?...`, lọc, `/bang-xep-hang?...` | HTTP 200 nhưng **rỗng kết quả** khi chưa login | `curl` + user xác nhận |
| Dấu hiệu chưa login | HTML có `data-action="login"` | `curl` |
| Cơ chế login | `actions.login` trong `/static/js/wiki/base-0.0.8.min.js` mở **popup** `window.open("/auth/dth")` → 302 tới SSO `forum.dichtienghoa.com/dth/auth?code=…` → quay về wikicv | Đọc JS + `curl` |
| `/auth/dth` | **Bắt buộc header `Referer: https://wikicv.org/`** (không có → HTTP 500) | `curl` 2 lần mỗi trường hợp |
| SSO forum | NodeBB; hỗ trợ **user/mật khẩu** và **Google** (`/auth/google`) | `curl /login` |
| Cookie phiên | `express.sid` (logout JS gọi `Cookies.remove("express.sid")`) | Đọc JS |

### 2.2 Ràng buộc nền tảng Android

| Chủ đề | Kết luận | Hệ quả thiết kế |
|---|---|---|
| Google OAuth trong WebView | Google chặn từ 09/2021 (`403 disallowed_useragent`), không có workaround hợp lệ | Login Google **không thể** làm trong WebView → cần fallback dán cookie |
| Custom Tabs | Không chia sẻ cookie với app | Không dùng CCT để lấy cookie |
| `CookieManager.getCookie()` | Trả về cả cookie HttpOnly cho code native | Bridge WebView→OkHttp hoạt động với `express.sid` |
| Session cookie (không Expires) | WebView thường vẫn lưu xuống đĩa; phải gọi `flush()` sau khi thay đổi | Gọi `flush()` sau login/dán/logout; nếu mất phiên sau restart → app phát hiện và báo đăng nhập lại |
| `window.open` trong WebView | Mặc định bị bỏ qua trừ khi bật `setSupportMultipleWindows(true)` + `onCreateWindow` | **Tránh popup**: tải thẳng `/auth/dth` kèm Referer bằng `loadUrl(url, headers)` |
| WebView trong Compose | Accompanist WebView đã deprecated → dùng `AndroidView { WebView }` | Không thêm dependency mới |
| `cf_clearance` | Gắn với UA/IP/TLS → không portable | Wikicv hiện không có challenge; vẫn dùng **UA của WebView** cho request Wikidich để giảm rủi ro |

## 3. Kiến trúc

```
SettingsScreen ─► SourceAccountsSection ─► SourceLoginScreen (WebView)  ┐
                                       └─► PasteCookieDialog             ├─► SourceAuthManager ─► CookieManager (WebView)
SearchScreen (banner "Cần đăng nhập") ─► SourceLoginScreen               ┘          ▲
                                                                                    │ loadForRequest
WikidichScraper (BaseScraper) ─► OkHttpClient(cookieJar = WebViewCookieHandler) ────┘
```

### 3.1 Thành phần

| File | Trách nhiệm |
|---|---|
| `data/remote/auth/SourceAuthConfig.kt` (mới) | Data class cấu hình từng nguồn: `sourceId`, `displayName`, `baseUrl`, `loginUrl`, `loginReferer`, `sessionCookieNames`, `loggedOutMarker`. Hằng `WIKIDICH` |
| `data/remote/auth/SourceAuthManager.kt` (mới, `@Singleton`) | `isLoggedIn(sourceId)`, `loginState(sourceId): StateFlow`, `importCookies(sourceId, raw)`, `logout(sourceId)`, `markSessionExpired(sourceId)`. Bao `CookieManager`, gọi `flush()` |
| `data/remote/auth/CookieStringParser.kt` (mới, thuần Kotlin) | Parse chuỗi cookie user dán (`a=b; c=d`, hoặc chỉ giá trị `express.sid`, hoặc dòng header `Cookie: …`) → `List<Pair<name,value>>`. Có unit test |
| `data/remote/cookie/WebViewCookieHandler.kt` (sửa) | Bỏ `isLoggedIn` hard-code LacaTruyen (chuyển sang `SourceAuthManager`). Giữ vai trò `CookieJar` |
| `data/remote/WikidichScraper.kt` (mới, extends `BaseScraper`) | Implement `StorySource`. `userAgent` = UA mặc định WebView. Search/browse ném `LoginRequiredException` khi gặp `loggedOutMarker` và không có kết quả |
| `data/remote/ScraperException.kt` (sửa) | Thêm `LoginRequiredException(sourceId)` |
| `ui/login/SourceLoginScreen.kt` + `SourceLoginViewModel.kt` (mới) | WebView `AndroidView`; tải `loginUrl` với header Referer; theo dõi `onPageFinished` → kiểm tra cookie → hiện "Đã đăng nhập" + nút Xong. Nút "Dán cookie thay thế" |
| `ui/login/PasteCookieDialog.kt` (mới) | Ô nhập cookie + hướng dẫn lấy cookie trên PC (DevTools → Application → Cookies → `express.sid`) |
| `ui/settings/…` (sửa) | Mục "Tài khoản nguồn truyện": trạng thái, Đăng nhập, Dán cookie, Đăng xuất |
| `ui/search/…` (sửa) | Banner "Wikidich cần đăng nhập để tìm kiếm" + nút "Đăng nhập" khi nhận `LoginRequiredException` |
| `ui/navigation/AppNavigation.kt` (sửa) | Route `source_login/{sourceId}` |
| `di/ScraperModule.kt` (sửa) | `@Binds @IntoSet` `WikidichScraper` |

### 3.2 Luồng đăng nhập WebView

1. Mở `SourceLoginScreen("wikidich")`.
2. Xoá cookie cũ của `wikicv.org` (tương tự `actions.login` của site) để tránh phiên hỏng.
3. `webView.loadUrl("https://wikicv.org/auth/dth", mapOf("Referer" to "https://wikicv.org/"))`.
4. User đăng nhập NodeBB bằng **user/mật khẩu** (nếu bấm Google → Google trả lỗi; màn hình hiển thị gợi ý dùng "Dán cookie").
5. Mỗi `onPageFinished` trên host `wikicv.org`: nếu có cookie `express.sid` **và** HTML trang không chứa `data-action="login"` (kiểm tra qua `evaluateJavascript`) → trạng thái `LoggedIn`, `flush()`.
6. User bấm "Xong" → quay lại màn hình trước.

WebView settings: `javaScriptEnabled = true`, `domStorageEnabled = true`, `CookieManager.setAcceptThirdPartyCookies(webView, true)` (SSO khác domain), `setSupportMultipleWindows(false)` (nếu trang vẫn gọi `window.open`, URL sẽ mở ngay trong WebView hiện tại).

### 3.3 Luồng dán cookie

1. User dán chuỗi vào `PasteCookieDialog`.
2. `CookieStringParser` chuẩn hoá. Nếu chuỗi không có `=` → hiểu là giá trị của cookie phiên đầu tiên (`express.sid`).
3. `SourceAuthManager.importCookies` gọi `CookieManager.setCookie("https://wikicv.org", "name=value; Path=/; Secure; Max-Age=2592000")` cho từng cookie, rồi `flush()`.
4. Xác minh: gọi `GET /tim-kiem?qs=1&q=a` qua OkHttp → nếu không còn `data-action="login"` → thành công; ngược lại báo "Cookie không hợp lệ hoặc đã hết hạn".

### 3.4 Phát hiện hết phiên khi đang dùng

- `WikidichScraper` khi parse trang tìm kiếm/lọc/BXH: nếu không có item **và** có `data-action="login"` → `authManager.markSessionExpired("wikidich")` + ném `LoginRequiredException`.
- **Không cache** response trong trường hợp này (BaseScraper có in-memory cache khi `useEtagCaching=false` — phải bỏ qua cache cho URL tìm kiếm của Wikidich hoặc xoá entry khi phát hiện chưa login).

### 3.5 Tích hợp vào gộp nguồn

- Đọc truyện/chương/tải: luôn hoạt động (không cần login).
- Tìm kiếm / browse gộp: nếu Wikidich ném `LoginRequiredException` → `StoryRepository` **bỏ qua nguồn đó** (không làm hỏng kết quả nguồn khác) và phát sự kiện để UI hiện banner. Không đếm vào circuit breaker.
- `sourceId = "wikidich"`, `sourceName = "Wikidich"`, `baseUrl = "https://wikicv.org"`.

## 4. Xử lý lỗi

| Tình huống | Hành vi |
|---|---|
| Chưa login, user tìm kiếm | Banner + nút "Đăng nhập Wikidich"; kết quả nguồn khác vẫn hiện |
| Bấm Google trong WebView | Google báo `disallowed_useragent` → app hiện hướng dẫn chuyển sang "Dán cookie" |
| Cookie dán sai/hết hạn | Thông báo lỗi, không lưu trạng thái `LoggedIn` |
| Wikidich đổi domain lần nữa | Cookie cũ không dùng được (cookie gắn domain) → user đăng nhập lại; đổi `baseUrl` trong `SourceAuthConfig`. Ghi chú trong `docs/scraping.md` |
| Network lỗi | Dùng retry sẵn có của `BaseScraper` |

## 5. Kiểm thử

- **Unit (JUnit5, JVM):**
  - `CookieStringParserTest`: các dạng input (`a=b; c=d`, chỉ giá trị, có tiền tố `Cookie:`, khoảng trắng, chuỗi rỗng).
  - `WikidichScraperParserTest`: parse từ fixture HTML trong `app/src/test/resources/wikidich/` — trang truyện, chương, tìm kiếm khi **chưa** login (→ `LoginRequiredException`), tìm kiếm khi **đã** login (fixture lấy sau khi user đăng nhập — xem Rủi ro).
  - `MockWebServer`: verify request Wikidich gửi kèm Referer/UA đúng, và không cache khi chưa login.
- **Thủ công trên máy thật:** login user/mật khẩu → tìm kiếm có kết quả → kill app → mở lại vẫn còn phiên; dán cookie từ PC → tìm kiếm có kết quả; đăng xuất → banner xuất hiện.

## 6. Rủi ro & câu hỏi mở

1. **Chưa có HTML trang tìm kiếm khi đã login** → selectors cho kết quả tìm kiếm/BXH phải lấy sau khi đăng nhập. Trong plan có một bước: user đăng nhập (hoặc cung cấp cookie) để chụp fixture.
2. Tham số lọc (`status=…`, `gender=…`, `so=…`, `qs=…`) là ObjectId — cần map thể loại/trạng thái "hoàn thành" từ trang `/tim-kiem` khi đã login.
3. Session `express.sid` có thể là session cookie; nếu WebView không giữ qua restart thì app sẽ phát hiện và báo đăng nhập lại (chấp nhận được, đo thực tế khi test).
4. Gợi ý cho user Google: không đặt được mật khẩu NodeBB cho tài khoản SSO từ phía user → dán cookie là đường chính.

## 7. Tài liệu cần cập nhật khi triển khai

`docs/architecture.md` (file mới, route mới), `docs/scraping.md` (mục Wikidich: URL, selectors, auth), `docs/progress.md`, `docs/changelog.md`.
