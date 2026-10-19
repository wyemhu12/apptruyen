# Giải mã nội dung truyện trên LacaTruyen (lacatruyen.buzz) — Tư liệu lịch sử

> [!NOTE]
> Nguồn truyện LacaTruyen (`lacatruyen.buzz`) và class giải mã `CryptoJS.kt` đã được gỡ bỏ khỏi codebase trong **Audit Remediation Phase 4 (2026-10-19)**. Tài liệu này được lưu giữ làm tư liệu kỹ thuật lịch sử.

Tài liệu này ghi chú lại quá trình reverse engineer và phân tích thuật toán mã hóa AES nhiều lớp (multi-layered AES encryption) của LacaTruyen. Thuật toán này được phát hiện nhằm ngăn chặn các scraper và chỉ cho phép frontend React (Next.js) hiển thị chữ hợp lệ trên trình duyệt.

## Vấn đề
- API của LacaTruyen (thông qua đối tượng `__NEXT_DATA__` trong trang HTML) trả về nội dung và danh sách chương bị mã hóa hoàn toàn (ở dạng chuỗi Base64: `U2FsdGVkX1...`).
- Nếu WebView thông thường (như trong `WebViewContentFetcher`) trích xuất `.chapter-content` quá sớm hoặc khi JS chưa chạy xong thì chỉ lấy được đoạn mã hóa này.
- Bằng cách phân tích JS chunk của Next.js (file `_app-...js` và `[slug]-...js`), ta có thể bóc tách toàn bộ công thức giải mã và tự động thực thi bằng Kotlin.

## Quy trình giải mã Native 5 Bước

### 1. Global Key (Chìa khóa Toàn cục)
LacaTruyen dùng chung một chìa khóa gốc được tạo ra từ một chuỗi bí mật (hardcoded trong file JS chunk gốc):
- **Chuỗi gốc:** `"T5Hr41U5jKTTrtUOXdYZnyx3wjZEKUoxv16Clwwu4D5zIbd0-q9sdfh"`
- **Thuật toán sinh khóa:**
  1. Tách chuỗi bởi dấu `-` làm hai phần: `t` (q9sdfh) và `n` (T5Hr...).
  2. Map mảng `t` thành mã ASCII, sau đó `reduce` với công thức: `(e, t) => (e * t) % 255` khởi tạo từ 1. Gọi kết quả là `i`.
  3. Lặp qua các ký tự của `n`. Với mỗi ký tự, lấy mã ASCII `c`.
  4. Tạo độ lệch `r = (i + t_index * c) % 26`.
  5. Xoay vòng bảng chữ cái (chỉ áp dụng cho chữ cái in hoa [65-90] và in thường [97-122]):
     - Hoa: `(c - 65 + r) % 26 + 65`
     - Thường: `(c - 97 + r) % 26 + 97`
- **Kết quả (Global Key):** `"T5Vv41K5zJBHhtODBnOTdrl3shZVEPqzb16Hzfuh4J5tWrb0"`

### 2. Dữ liệu từ `__NEXT_DATA__`
Mỗi URL trang truyện chứa một thẻ `<script id="__NEXT_DATA__">`. Trong đó phần `props.pageProps` có chứa các thông số:
- `ri`: Số thứ tự của key động (bị mã hóa AES bằng Global Key).
- `rs`: Dictionary (từ `0` đến `99`) chứa các salt string (bị mã hóa AES bằng Global Key).
- `comment_count`: Có thể là số lượng comment hoặc một mảng rỗng `[]`.
- `chapter.key_encrypt`: Chìa khóa riêng của chương truyện này (bị mã hóa AES bằng Dynamic Key).
- `chapter.content`: Nội dung HTML của chương (bị mã hóa AES bằng Final Key).
- `previewHtml`: Nội dung xem thử (unencrypted) nhưng chỉ là một đoạn ngắn, không chứa đủ chữ của chương truyện.

### 3. Khôi phục `rs_val`
- Lấy `ri` giải mã bằng Global Key (theo AES) để ra `ri_decrypted` (ví dụ `"33"` hoặc `"454"`).
- Tìm giá trị bị mã hóa trong `rs[ri_decrypted]`.
  - **Lưu ý:** Nếu `rs[ri_decrypted]` không tồn tại (ví dụ `ri` là `454` mà `rs` chỉ từ 0-99), thì giá trị truy xuất là `undefined` (theo tiêu chuẩn Javascript, hoặc trả về lỗi).
- Giải mã giá trị này bằng Global Key. Kết quả gọi là `rs_val` (ví dụ `"w0qpb4x6h"`). Trong trường hợp truy xuất `rs[454]`, `rs_val` sẽ được coi là `"undefined"`.

### 4. Khôi phục Final Key
- Key động (`dynamic_key`) được tạo bằng cách ghép: `rs_val + comment_count`.
  - **Lưu ý:** Trong Javascript, nếu `comment_count` là `[]` (mảng rỗng), thì `"w0qpb4x6h" + []` ra `"w0qpb4x6h"`. Nếu `rs_val` là "undefined" và `comment_count` là `[]`, kết quả ghép ra chuỗi `"undefined"`.
- Dùng `dynamic_key` để giải mã biến `chapter.key_encrypt`.
- Kết quả thu được gọi là `final_key` (thường chính nó cũng có dạng `U2FsdGVkX1...`).

### 5. Khôi phục Nội dung (Plaintext Content)
- Cuối cùng, dùng `final_key` để giải mã biến `chapter.content`.
- Chuỗi trả về chính là HTML chuẩn, có chứa các thẻ `<p>` hiển thị toàn bộ nội dung chương truyện rõ ràng bằng tiếng Việt.

## Triển khai trên Android (Kotlin — Đã gỡ bỏ)
- Do tính chất mã hóa tự thiết kế của AES (Salted__ header format của CryptoJS), việc ánh xạ và giả lập lại hàm `getGlobalKey()` và chuỗi giải mã liên hoàn trên Kotlin từng được triển khai qua class `CryptoJS.kt` (đã được xóa trong Phase 4 khi dọn dẹp dead code của nguồn cũ LacaTruyen).
- Triển khai Native cho phép Bypass hoàn toàn WebView, tăng tốc độ render (do JSON lấy về có thể decode ngay trong vài milisecond so với 5-10s chờ WebView).
