---
name: apptruyen-simplify
description: "BẮT BUỘC dùng khi được yêu cầu tinh giản code, dọn dẹp dead code, hoặc giảm độ phức tạp (/simplify) mà không làm thay đổi hành vi runtime của AppTruyen. Chỉ xóa phần thừa và làm phẳng luồng điều khiển."
---

# AppTruyen Simplify (Behavior-Preserving Code Reduction)

Sử dụng kỹ năng này khi tinh giản code hiện có, gỡ bỏ dead code, hoặc làm phẳng luồng điều khiển (control flow) trong khi vẫn giữ nguyên 100% hành vi runtime của **AppTruyen**.

---

## 3 Nguyên Tắc Cốt Lõi

1. **Trừ Trước Khi Cộng (Subtract Before You Add)**: Ưu tiên xóa code không dùng, state dư thừa và các lớp trung gian chỉ gọi 1 lần hơn là tạo thêm helper hay abstraction mới.
2. **Giao Thức Tối Giản (Laziness Protocol)**: Giữa các giải pháp đúng và đạt chuẩn của AppTruyen, luôn chọn giải pháp có ít dòng code nhất và tải nhận thức (cognitive load) thấp nhất. Không thêm cấu hình phòng hờ hay lớp bọc (wrapper) suy đoán.
3. **Bảo Toàn Hành Vi Tuyệt Đối (Strict Behavior Preservation)**: Đầu vào, đầu ra, side effect, mã lỗi `ScraperException`, thông báo tiếng Việt trên UI, trạng thái `StateFlow` và giao diện hiển thị phải giữ nguyên không đổi.

---

## 4 Nhóm Thao Tác Được Phép (Allowed Operations)

1. **Xóa Dead Code (Dead Code Removal)**:
   - Xóa hàm/biến/hằng số `private` hoặc `internal` không sử dụng, import thừa, nhánh điều kiện không thể chạm tới.
   - Bắt buộc có bằng chứng `grep_search` chứng minh `0` tham chiếu trên toàn bộ `app/src/main/`, `app/src/test/`, và `app/src/androidTest/`.
2. **Inline Hàm Bọc Chỉ Có 1 Nơi Gọi (Inline Single-Caller Wrappers)**:
   - Inline các hàm trung gian chỉ chuyển tiếp tham số (pass-through) hoặc biến tạm chỉ có đúng 1 nơi gọi và không mang lại ý nghĩa nghiệp vụ rõ ràng.
3. **Loại Bỏ State & Tham Số Dư Thừa (Redundant State & Parameter Elimination)**:
   - Thay thế các trường dữ liệu dẫn xuất bị lưu trùng lặp trong `UiState` hoặc Composable bằng thuộc tính tính toán trực tiếp (`val isEmpty get() = ...` hoặc `remember(items) { ... }`).
   - Gỡ bỏ các tham số hàm/Composable không còn được sử dụng sau khi đã kiểm tra mọi nơi gọi.
4. **Làm Phẳng Luồng Điều Khiển (Control Flow Flattening)**:
   - Chuyển các khối `if/else` hoặc `when` lồng nhau sâu thành guard clauses (`if (...) return`, `?: return`) hoặc biểu thức Kotlin ngắn gọn.
   - Giữ độ dài mỗi hàm dưới 50 dòng theo `.agents/rules/01-coding.md`.

---

## Các Hành Vi Nghiêm Cấm (Forbidden Actions)

- **KHÔNG thêm tính năng hay đổi đặc tả**: Không thay đổi quy tắc nghiệp vụ, selector nguồn truyện, timeout, hay cấu trúc bảng Room.
- **KHÔNG tự ý đổi chữ ký hàm public/interface dùng chung**: Không sửa chữ ký hàm trong `data/repository/`, `data/remote/`, `data/local/`, `data/model/`, `tts/`, `util/` nếu chưa chạy `/blast-radius` từ `.agents/skills/apptruyen-investigate/SKILL.md`.
- **KHÔNG làm lệch văn bản UI tiếng Việt hoặc Theme Token**: Không thay đổi chuỗi hiển thị tiếng Việt, token màu/chữ `MaterialTheme`, hay hành vi cuộn/chuyển chương.
- **KHÔNG hạ thấp tiêu chuẩn Test**: Tuyệt đối không xóa `assert*`/`verify`/`coVerify`, không nới matcher, không tắt test (`.agents/rules/02-quality.md` §Test Failure Triage Protocol).
- **KHÔNG gây nhiễu định dạng (No Formatting Noise)**: Không format lại các dòng code không liên quan đến phần tinh giản (`.agents/rules/02-quality.md` §Minimal Diffs Policy).

---

## Quy Trình 6 Bước Thực Hiện

1. **Xác lập Baseline (Baseline Verification)**:
   - Chạy `.\gradlew.bat ktlintCheck; .\gradlew.bat testDebugUnitTest` và ghi nhận số lượng bài test pass trước khi chạm vào code.
2. **Chứng minh 0 Tham Chiếu (Zero-Reference Proof)**:
   - Với mọi biểu tượng dự kiến xóa, chạy `grep_search` trên toàn bộ workspace để xác nhận `0` nơi sử dụng.
3. **Thực hiện Chỉnh sửa Tối giản (Apply Minimal Edits)**:
   - Thực hiện từng thay đổi nhỏ, tập trung bằng `edit_file` (`replace_file_content`).
4. **Kiểm tra Nhanh Tier 1 (Tier 1 Sanity Check)**:
   - Chạy `.\gradlew.bat compileDebugKotlin` ngay sau mỗi cụm chỉnh sửa.
5. **Kiểm chứng Đầy đủ Tier 2 (Tier 2 Full Verification)**:
   - Chạy `.\gradlew.bat ktlintCheck; .\gradlew.bat testDebugUnitTest; .\gradlew.bat assembleDebug` sau khi hoàn tất. Tổng số bài test pass phải `>=` baseline.
6. **Báo cáo Bằng chứng Trước / Sau (Before/After Evidence Report)**:
   - Xuất bảng tổng kết bằng tiếng Việt:

| File | Thao tác (Allowed Category) | Số dòng trước → sau | Bằng chứng (`[CMD]` / `[SRC]`) |
| :--- | :--- | :---: | :--- |
| `app/src/main/...` | Dead code / Inline / Flatten | `140 → 112` | `grep_search` 0 refs + Tier 2 `BUILD SUCCESSFUL` |

---

## Tuân Thủ Quản Trị & Code Freeze

- Mọi thao tác tinh giản code trong `app/**` đều là sửa đổi mã nguồn và chịu sự điều chỉnh của `.agents/rules/05-plan-review.md` (bắt buộc có kế hoạch đạt `[PLAN_APPROVED]` đối với các đợt dọn dẹp hoặc refactor).
