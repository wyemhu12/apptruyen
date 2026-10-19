---
trigger: always_on
description: Pre-work protocol, verification tiers, test failure triage, post-fix và debugging protocol, governance, minimal diffs.
---

# Quality Gates (AppTruyen)

## Pre-Work Protocol (BẮT BUỘC)

Trước khi viết code, xác định domain của task và đọc tài liệu tương ứng:

| Domain                       | Đọc trước                                                                 | Workflow bắt buộc                   |
| ---------------------------- | ------------------------------------------------------------------------- | ----------------------------------- |
| Scraper / nguồn truyện mới   | `docs/scraping.md`, `.agents/rules/04-scraping.md`                        |                                     |
| Giải mã nội dung (LacaTruyen) | `docs/scraping_lacatruyen_decryption.md`                                 |                                     |
| Database / Room schema       | `docs/architecture.md` (Database Schema), `data/local/AppDatabase.kt`     |                                     |
| TTS / MediaSession           | `docs/architecture.md`, `tts/`                                            |                                     |
| UI / Compose                 | `.agents/rules/03-ui.md`                                                  |                                     |
| Tính năng mới                | `docs/architecture.md`, `docs/progress.md`, `docs/contributing.md`        | **.agents/rules/05-plan-review.md** |
| Lập kế hoạch task            | `docs/plans/README.md`, `.agents/rules/05-plan-review.md`                 | **.agents/rules/05-plan-review.md** |
| Sửa bug                      | `docs/lessons-learned.md`                                                 | **.agents/workflows/debug.md**      |
| Rà soát chất lượng code      | `.agents/rules/01-coding.md`                                              | **.agents/workflows/audit.md**      |
| Refactor / tách file         | `.agents/rules/01-coding.md` (Kích thước file)                            |                                     |
| Sau khi đổi code             | `.agents/rules/06-docs.md`                                                | **.agents/workflows/update-docs.md** |

<important>
Workflow hoặc rule được liệt kê trong bảng là quy trình bắt buộc, PHẢI đọc và làm theo từng bước.
Với tính năng mới hoặc task không tầm thường: code freeze áp dụng cho đến khi Lead Reviewer cấp `[PLAN_APPROVED]` (`.agents/rules/05-plan-review.md`).
Task tầm thường (sửa typo, đổi 1 chuỗi, chỉnh docs) không cần plan, nhưng hook vẫn hỏi User khi ghi vào `app/`.
</important>

## Post-Change Checklist (BẮT BUỘC)

| Tier  | Khi nào                | Lệnh                                                                                     | Mục đích                          |
| ----- | ---------------------- | ---------------------------------------------------------------------------------------- | --------------------------------- |
| **1** | Sau mỗi cụm sửa        | `.\gradlew.bat compileDebugKotlin`                                                       | Bắt lỗi biên dịch sớm             |
| **2** | Trước khi báo xong task | `.\gradlew.bat ktlintCheck; .\gradlew.bat testDebugUnitTest; .\gradlew.bat assembleDebug` | Quality gate đầy đủ               |
| **3** | Trước merge/release    | CI `.github/workflows/ci.yml` (ktlint → lint → test → assembleDebug)                     | Xác minh độc lập trên môi trường sạch |

<important>
Chạy Tier 1 sau mỗi cụm sửa, Tier 2 trước khi tuyên bố xong. KHÔNG bỏ Tier 2.
KHÔNG tự ý chạy `.\gradlew.bat assembleRelease` để tạo file APK release nếu User không yêu cầu rõ ràng.
Đổi logic thì thêm/cập nhật test TRƯỚC khi chạy Tier 2.
ktlint fail thì chạy `.\gradlew.bat ktlintFormat` (chỉ được dùng sau `[PLAN_APPROVED]`), rồi chạy lại `ktlintCheck`.
Tính năng UI/TTS: ghi rõ các bước User cần kiểm tra thủ công trên thiết bị.
</important>

Sau khi verify xong: cập nhật docs theo `.agents/rules/06-docs.md`.

## Test Failure Triage Protocol

Khi test fail, BẮT BUỘC phân loại nguyên nhân:

- **Loại A (Code Bug)**: code ứng dụng sai so với đặc tả → **SỬA CODE**, không đụng vào test.
- **Loại B (Test Harness)**: mock, fixture HTML, dispatcher hoặc import lỗi thời do refactor, đặc tả không đổi → **được sửa harness/mock**, nhưng KHÔNG giảm số assert và KHÔNG nới matcher.
- **Loại C (Approved Spec Change)**: đặc tả đổi theo Test Contract trong plan đã có `[PLAN_APPROVED]` → **được cập nhật test** cho khớp Test Contract mới.

<important>
NGHIÊM CẤM HẠ THẤP TIÊU CHUẨN TEST:
- Xóa `assert*`/`verify {}`/`coVerify {}`, hoặc comment-out test.
- Nới matcher (`assertEquals` → `assertNotNull`/`assertTrue`, khớp chính xác → `any()`).
- Thêm `@Disabled`, `@Ignore`, `assumeTrue`, hoặc bọc assert trong `runCatching`/`try` khi không có căn cứ Loại C.
Test Integrity Guard (hook) sẽ phát hiện, và `@qa` đánh lỗi `[BLOCKER]`.
</important>

## Phân Định: Audit Workflow / Debug Workflow / `@qa`

- `.agents/workflows/audit.md`: rà soát chất lượng code định kỳ cho một file hoặc cụm tính năng (kiến trúc, coding standards, UI tiếng Việt, stale patterns, verify).
- `.agents/workflows/debug.md`: bug không hiển nhiên hoặc tái diễn. Không sửa khi chưa xác nhận root cause.
- `@qa`: chỉ chạy khi User gọi. Nghiệm thu một task cụ thể theo plan (7 bước), có Verification Gate và Test Integrity Audit.

## Governance-Only Changes

Khi tạo/sửa tài liệu quản trị (`.agents/rules/`, `.agents/agents/`, `.agents/workflows/`, `docs/plans/`):

- Có YAML frontmatter đúng (`trigger`/`description` cho rule; `name`/`description`/`model`/`tools` cho agent).
- Theo chuẩn GitHub Flavored Markdown, đường dẫn viết tương đối từ repo root.
- Giới hạn: rule ≤ 12.000 ký tự/file, agent ≤ 10.000 ký tự/file.
- Sửa `code-freeze-guard.js` thì phải chạy `node --test .agents/scripts/code-freeze-guard.test.js`.

## Sau Khi Sửa Bug (Post-Fix Protocol, BẮT BUỘC)

Làm đủ các bước theo `.agents/workflows/post-fix.md`:

1. **Verify**: chạy Tier 2.
2. **Ghi bài học** vào `docs/lessons-learned.md`: Triệu chứng / Nguyên nhân gốc / Cách sửa / Quy tắc phòng ngừa.
3. **Nâng cấp thành rule**: cùng một loại lỗi xuất hiện ≥ 3 lần thì trích thành rule trong `.agents/rules/`.
4. **Changelog**: thêm vào `docs/changelog.md` mục `### Fix bug`.
5. **Xác nhận**: kết thúc bằng câu `Đã ghi bài học vào docs/lessons-learned.md: [mô tả 1 dòng]`.

## Debugging Protocol

<important>
Bug không hiển nhiên thì PHẢI làm theo `.agents/workflows/debug.md`. KHÔNG đề xuất sửa khi chưa xác nhận root cause.
Sửa 3 lần vẫn fail thì DỪNG và báo: "CẢNH BÁO KIẾN TRÚC: 3 lần sửa thất bại. Đề xuất thảo luận refactor."
</important>

## Minimal Diffs Policy

- Chỉ sửa những gì cần. Không tạo nhiễu do format lại.
- Theo style của file đang sửa. Không format lại vùng code không đổi logic. `ktlintFormat` chỉ được áp cho file mình chạm vào; nếu nó sửa file khác thì báo User.
