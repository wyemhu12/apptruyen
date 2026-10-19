# Evidence Bar — Tiêu Chuẩn Bằng Chứng Cho @reviewer (AppTruyen)

> Tài liệu tham chiếu bắt buộc của `@reviewer` và Claude CLI reviewer. Đọc bằng `view_file` ở Bước 0 trước mọi review.

## Áp Dụng Cho MỌI Kết Luận PASS

> **Nguyên tắc cốt lõi**: Diễn giải lại nội dung plan KHÔNG phải evidence. Code hoặc plan "trông có vẻ đúng" (đủ từ khóa, đủ comment giải thích ý đồ) cũng KHÔNG phải evidence. PASS mà không có evidence hợp lệ thì tính là `UNVERIFIED`. Kịch bản S1–S5 nào bị `UNVERIFIED` đều dẫn đến `[CHANGES_REQUESTED]`.

Evidence hợp lệ CHỈ gồm 4 loại (ghi rõ loại trong ma trận):

- **`[CMD]`**: lệnh đã chạy (nằm trong ALLOWLIST), kèm exit code và trích đoạn output liên quan.
- **`[SRC]`**: `file:line` trong source dự án (`app/src/...`, `app/schemas/...`), file cấu hình/script gốc (`app/build.gradle.kts`, `.editorconfig`, `AndroidManifest.xml`, `.agents/scripts/*.js`) hoặc mã nguồn thư viện đã đọc bằng `view_file`, kèm tóm tắt hành vi đọc được. Không chấp nhận trích dẫn `docs/plans/` làm `[SRC]`.
- **`[ADV]`**: kịch bản đối kháng dạng timeline (xem 4.5.2) đã lần theo đến kết cục cụ thể.
- **`[URL]`**: tài liệu chính thức, release notes hoặc advisory năm 2026 (developer.android.com, kotlinlang.org, square.github.io/okhttp, jsoup.org...). Chỉ đủ cho version và API surface, KHÔNG đủ cho hành vi runtime tinh vi.

### 4.5.1 Library Claim Verification (Bắt buộc)

Với MỌI hành vi thư viện mà plan dựa vào để đảm bảo tính đúng, ví dụ:

- Room: hành vi `@Transaction`, `OnConflictStrategy`, `Flow` query re-emit, `Migration` và `fallbackToDestructiveMigrationFrom`
- OkHttp 5: timeout, retry-on-connection-failure, đóng `Response`/`ResponseBody`, interceptor order, `CookieJar`
- Jsoup: selector, `Element.text()` và `wholeText()`, xử lý charset
- Coroutines: cancellation, `SupervisorJob`, `stateIn(SharingStarted.WhileSubscribed)`, dispatcher injection
- Android `TextToSpeech`: `onInit`, `UtteranceProgressListener`, giới hạn độ dài `getMaxSpeechInputLength()`
- `CookieManager`/WebView, DataStore, Hilt scope

Yêu cầu:

- Trích dẫn `file:line` nơi dự án đang dùng API đó (`[SRC]`) và tài liệu chính thức (`[URL]`).
- Nếu hành vi kiểm được bằng unit test JVM hiện có thì chạy `--tests` để xác nhận (`[CMD]`).
- Mô tả của `@planner` về hành vi thư viện LUÔN bị coi là **chưa xác minh** cho đến khi có `[SRC]` hoặc `[CMD]`.

### 4.5.2 Adversarial Timeline (Bắt buộc cho S3, S5 và mọi cơ chế đồng thời / bất đồng bộ)

Áp dụng với MỖI cơ chế thuộc loại: ghi Room từ nhiều coroutine, check-then-act (chương đã tải hay chưa, cache), tải chương song song (DownloadService), TTS queue và đổi chương, cookie/phiên đăng nhập hết hạn, `ScraperCircuitBreaker`, gộp kết quả từ nhiều nguồn. Mỗi cơ chế cần ít nhất một timeline theo mẫu:

```
Cơ chế: <tên cơ chế>
T1: <actor A làm gì> | T2: <actor B / lỗi / mạng / Android OS làm gì> | T3: <hệ thống xử lý gì>
Kết cục: <trạng thái cuối cụ thể> → AN TOÀN / LỖI (<severity>)
```

BẮT BUỘC phủ tối thiểu 4 trục đối kháng (trục không áp dụng ghi `N/A` kèm lý do):

1. **Đồng thời**: 2 coroutine cùng ghi một bản ghi (ví dụ: tiến độ đọc từ Reader và từ TtsService; 2 worker tải cùng một chương).
2. **Khoảng trống check→use (TOCTOU)**: trạng thái đổi giữa lúc kiểm tra và lúc dùng (ví dụ: kiểm tra chương có offline xong thì file/bản ghi bị xóa; cookie hợp lệ lúc check nhưng hết hạn lúc request).
3. **Thất bại giữa chừng và thất bại của chính bước dọn dẹp**: mất mạng giữa lúc tải 500 chương; scraper ném exception trong `finally`; `TextToSpeech.shutdown()` bị gọi khi service đã chết.
4. **Vòng đời Android**: process bị kill khi đang chạy nền, đổi cấu hình (xoay màn hình, foldable), Doze/giới hạn foreground service, người dùng vuốt tắt app khi TTS đang phát.

Đánh giá theo **thời điểm thực thi**, không theo thời điểm khai báo.

### 4.5.3 Enforcement Claim Verification (Bắt buộc)

Khi plan khẳng định một chính sách "được enforce" bằng công cụ (ktlint rule trong `.editorconfig`, Android Lint, Gradle config, CI step, Code Freeze Guard):

- **Chứng minh wiring**: rule hoặc flag thực sự có hiệu lực, ví dụ `[SRC]` `.editorconfig:line` kèm `[CMD]` `.\gradlew.bat ktlintCheck` cho thấy task chạy trên source set mục tiêu.
- **Chứng minh bắt lỗi (Negative Probe)**: ktlint/Lint không nhận stdin nên reviewer KHÔNG tự tạo file vi phạm. Plan BẮT BUỘC có bước Verification trong đó người triển khai tạo đoạn code vi phạm tạm thời, chạy check, xác nhận exit code ≠ 0 kèm đúng rule id, rồi xóa. Thiếu bước này thì đánh `[MAJOR]`.
- "Chạy sạch trên codebase hiện tại" CHỈ chứng minh trạng thái hiện tại sạch, KHÔNG chứng minh rule hoạt động, nên tự nó KHÔNG đủ cho PASS.

### 4.5.4 Risk Table Audit (Bắt buộc)

Mọi rủi ro trong plan xếp hạng **Cao/Trung bình** PHẢI ánh xạ tới một technical control đã xác minh bằng `[CMD]`/`[SRC]` (hoặc một bước Verification có test/negative probe cho control sẽ được thêm). Ví dụ control: `ScraperCircuitBreaker`, timeout OkHttp, `Migration` kèm test, try/catch có thông báo tiếng Việt cho người dùng. Rủi ro Cao không có control thì đánh `[BLOCKER]`; rủi ro Trung bình không có control thì đánh `[MAJOR]`.
