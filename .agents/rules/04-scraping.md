---
trigger: model_decision
description: Áp dụng khi tạo/sửa scraper hoặc nguồn truyện (data/remote) — BaseScraper, selector, rate limit, lỗi, đăng nhập/cookie, test bằng fixture.
---

# Scraping Rules (AppTruyen)

> Rule này thay thế `04-bilingual.md` của Vikini/Aurora (AppTruyen chỉ dùng tiếng Việt). Tài liệu chi tiết từng nguồn: `docs/scraping.md`.

## Kiến Trúc Scraper

- Mọi nguồn kế thừa `BaseScraper` (`data/remote/BaseScraper.kt`) và dùng lại `fetchDocumentWithRetry`, cache ETag, `userAgent`, `crawlDelayMs`, `contentSelectors`, `adsSelectors`. KHÔNG tự tạo OkHttpClient riêng. Client được inject qua Hilt (`di/ScraperModule.kt`).
- Đăng ký nguồn mới trong `data/remote/StorySource.kt` + `di/ScraperModule.kt`, và để Repository gộp kết quả. ViewModel không biết nguồn cụ thể.
- Tách **parser thuần** (nhận `Document`/`String`, trả model) khỏi lớp gọi mạng để test được bằng fixture.
- Nguồn render bằng JS hoặc cần cookie thì dùng `WebViewContentLoader` / `cookie/WebViewCookieHandler`, và ghi rõ lý do trong plan.

## Selector & URL

<important>
Trước khi viết hoặc sửa selector: phải xem HTML thực tế của nguồn (ưu tiên tool `read_url_content`, hoặc `curl.exe -q -s <url> | Select-String "<pattern>"` — lưu ý chỉ các host nguồn truyện chính thức mới được hook tự cho phép, URL khác sẽ hỏi User) và ghi ngày khảo sát vào plan.
Sau khi sửa: cập nhật `docs/scraping.md` (URL Patterns, CSS Selectors, edge cases).
</important>

- Selector, URL gốc và URL pattern đặt thành hằng số trong scraper, không rải rác.
- Có selector dự phòng cho nội dung chương (`contentSelectors` dạng danh sách ưu tiên).
- Chuẩn hóa URL tuyệt đối (`ensureAbsolute()`), xử lý domain đổi (nguồn hay đổi tên miền).

## Lỗi & Độ Bền

- Mọi lỗi đi qua sealed `ScraperException`: `NetworkException`, `HttpException`, `ParseException`, `CacheException`, `CircuitOpenException`, `UnknownException`. Repository/ViewModel chuyển chúng thành thông báo tiếng Việt.
- Selector trả rỗng hoặc trang là Cloudflare/đăng nhập thì ném `ParseException` (hoặc lỗi đăng nhập riêng). KHÔNG trả về chương rỗng như thể thành công, KHÔNG ghi đè chương đã lưu bằng nội dung rỗng.
- Tôn trọng `ScraperCircuitBreaker`: nguồn lỗi liên tục thì mở mạch, Repository fallback sang nguồn khác.
- Rate limit: giữ `crawlDelayMs` và exponential backoff có sẵn. Tải hàng loạt không được bắn request song song không giới hạn vào cùng một nguồn.

## Đăng Nhập / Cookie Nguồn

- Cookie/phiên chỉ nằm trong `CookieManager`/storage riêng. KHÔNG log, KHÔNG đưa vào crash log hay file backup.
- Code trong `data/remote/cookie/` và `data/remote/auth/` là vùng nhạy cảm: Code Freeze Guard luôn hỏi User khi sửa.
- Phiên hết hạn thì báo người dùng đăng nhập lại (tiếng Việt), không retry vô hạn.

## Làm Sạch Nội Dung

- Loại quảng cáo, script, watermark, link rác bằng `adsSelectors` và quy tắc riêng của nguồn. Giữ ngắt đoạn để Reader và TTS đọc đúng.
- Nguồn LacaTruyen và class `CryptoJS.kt` đã được gỡ bỏ khỏi codebase (tài liệu `docs/scraping_lacatruyen_decryption.md` chỉ giữ làm tư liệu kỹ thuật lịch sử). Nếu tích hợp nguồn có nội dung mã hóa trong tương lai, lỗi giải mã phải ném `ScraperException.ParseException` và không được làm crash app.

## Test

- Unit test parser bằng fixture HTML (chuỗi trong test hoặc file resource) hoặc `MockWebServer`. KHÔNG gọi mạng thật.
- Mỗi nguồn có test tối thiểu cho: danh sách tìm kiếm, chi tiết truyện, danh sách chương, nội dung chương, và trang lỗi/rỗng.
