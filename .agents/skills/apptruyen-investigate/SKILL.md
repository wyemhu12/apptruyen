---
name: apptruyen-investigate
description: "BẮT BUỘC dùng khi trả lời câu hỏi kiến trúc chỉ đọc (/how, /why), khôi phục ngữ cảnh phiên làm việc (/recall), hoặc phân tích phạm vi ảnh hưởng (/blast-radius) trước khi sửa các module dùng chung trong data/repository/, data/remote/, data/local/, data/model/, tts/, download/, hoặc util/."
---

# AppTruyen Investigate (Read-Only)

**Quy Tắc Cứng (Hard Rule)**: Kỹ năng này **chỉ đọc (strictly read-only)**. TUYỆT ĐỐI KHÔNG tạo, sửa hoặc xóa bất kỳ file nào, và KHÔNG chạy lệnh shell làm thay đổi trạng thái. Chỉ sử dụng `read_file`/`view_file`, `grep_search`, `list_dir`, `find_by_name`, và các lệnh kiểm tra chỉ đọc nằm trong `.agents/agents/reviewer/allowlist.md` (`git status|diff|log|show|blame`, `node .agents/scripts/plan-approval.js status` khớp với `READ_ONLY_COMMANDS` trong `.agents/scripts/code-freeze-guard.js`).

---

## Mode 1: `/how` — Truy Vết Luồng Thực Thi & Kiến Trúc (Runtime & Architecture Trace)

Sử dụng khi giải thích cách một tính năng, luồng dữ liệu hoặc phân hệ hoạt động đầu-cuối qua các tầng kiến trúc của AppTruyen (`ui/` → `data/repository/` → `data/remote/` | `data/local/` | `tts/` | `download/`).

### Quy Trình Thực Hiện

1. **Xác định điểm khởi đầu (Entry Point)**: Xác định sự kiện UI trên `*Screen.kt` (`ui/<feature>/`), route điều hướng trong `ui/navigation/AppNavigation.kt`, hoặc service nền (`tts/TtsService.kt`, `download/DownloadService.kt`).
2. **Truy vết tầng Điều phối (ViewModel / MVI)**: Lần theo luồng xử lý trong `*ViewModel.kt` (`StateFlow`, `Channel`, `UiEvent`/`Intent`).
3. **Truy vết tầng Dữ liệu & Hạ tầng (Repository / Scraper / Room / TTS)**: Lần theo lời gọi qua interface `I*Repository` vào implementation trong `data/repository/`, tiếp tục tới `BaseScraper` / nguồn truyện cụ thể (`TangThuVienScraper`, `SsTruyenScraper`, `WikidichScraper`), DAO/Room (`AppDatabase`, `*Dao.kt`), hoặc `SettingsDataStore`.
4. **Tùy chọn Subagent song song (Cho phân hệ lớn)**: Nếu luồng truy vết đi qua > 3 phân hệ, có thể khởi chạy 2–3 subagent `research` song song theo từng tầng rồi tổng hợp thành một mô hình thống nhất.

### Đầu Ra Bắt Buộc

- **Chuỗi lời gọi (Call Chain)**: Trình tự từng bước kèm dẫn chứng `file:line` chính xác (`[SRC]`).
- **Hình thái dữ liệu qua ranh giới (Boundary Data Shapes)**: Kiểu dữ liệu đầu vào/đầu ra ở mỗi ranh giới (`Entity` ↔ Domain `Model` ↔ `UiState`).
- **Quyền sở hữu trạng thái (State Ownership)**: Chỉ rõ trạng thái nào nằm trong Room (`AppDatabase`), DataStore (`SettingsDataStore`), bộ nhớ (`StateFlow` của ViewModel / Singleton Manager), hay hệ thống (`MediaSession`).
- **Điểm nhạy cảm (Sharp Edges)**: Timeout mạng, `ScraperCircuitBreaker`, `CancellationException`, khóa `Mutex`/coroutine `Job`, hoặc giới hạn bộ nhớ SQLite `CursorWindow`.

---

## Mode 2: `/why` — Truy Tìm Lịch Sử & Chủ Đích Thiết Kế (Historical & Intent Evidence)

Sử dụng khi điều tra lý do một đoạn code, ràng buộc kỹ thuật hoặc cách xử lý đặc biệt tồn tại trước khi đề xuất thay đổi hay gỡ bỏ.

### Quy Trình Thực Hiện

1. **Khảo cổ Git (Git Archaeology)**: Chạy các lệnh lịch sử chỉ đọc được phép trong allowlist:
   - `git log --oneline -n 20 -- <path>`
   - `git blame -L <start>,<end> <path>`
   - `git show <sha>`
2. **Đối chiếu Tài liệu Sống & Kế hoạch**: Tìm kiếm bằng `grep_search` trong `docs/plans/`, `docs/lessons-learned.md`, `docs/scraping.md`, và `docs/changelog.md` theo tên hàm/lớp hoặc đường dẫn file.

### Đầu Ra Bắt Buộc

Trình bày kết quả tách bạch thành 2 cột:

| Sự thật xác thực (`[CMD]` / `[SRC]`)              | Suy luận kiến trúc (Architectural Interpretation) |
| :------------------------------------------------ | :------------------------------------------------ |
| Mã commit SHA, ngày, diff thực tế hoặc mục bài học | Lý do tác giả chọn đánh đổi kỹ thuật này          |

Kết thúc bằng phân loại bắt buộc:

- **Intentional Design**: Rào chắn bảo vệ hoặc bất biến nghiệp vụ bắt buộc phải giữ.
- **Regression / Accidental**: Lỗi phát sinh hoặc di sản cũ có thể sửa/dọn dẹp (sau khi có plan được duyệt).
- **Unknown**: Chưa đủ bằng chứng; cần cảnh báo rủi ro rõ ràng trước khi chạm vào.

---

## Mode 3: `/recall` — Khôi Phục Ngữ Cảnh Phiên Làm Việc (Context Recovery)

Sử dụng khi tiếp tục phiên làm việc, chuyển đổi tác vụ hoặc kiểm tra trạng thái hiện tại của dự án.

### Quy Trình Thực Hiện

1. Chạy `node .agents/scripts/plan-approval.js status` để kiểm tra trạng thái khóa/mở của toàn bộ các file kế hoạch trong `docs/plans/`.
2. Đọc mục trên cùng của `docs/changelog.md` và `docs/progress.md`.
3. Chạy `git status --short` và `git log --oneline -n 5`.

### Đầu Ra Bắt Buộc

1. **Đã hoàn thành (`Đã xong`)**: Các thay đổi đã được kiểm chứng thể hiện qua git status/log hoặc changelog.
2. **Đang thực hiện (`Đang dở`)**: Các file chưa commit hoặc các bước checklist chưa hoàn tất trong kế hoạch đang mở.
3. **Bước tiếp theo (`Bước tiếp theo`)**: Hành động cụ thể tiếp theo và trạng thái Code Freeze hiện tại (`approved: true/false`, `blockingPlans`).

---

## Mode 4: `/blast-radius` — Phân Tích Phạm Vi Ảnh Hưởng (Impact Analysis)

**BẮT BUỘC** thực hiện trước khi thay đổi chữ ký hàm, interface, domain model, entity hoặc hằng số dùng chung trong `data/repository/`, `data/remote/`, `data/local/`, `data/model/`, `di/`, `tts/`, `download/`, hoặc `util/`.

### Quy Trình Thực Hiện

1. Chạy `grep_search` trên toàn bộ `app/src/main/` và `app/src/test/` để tìm tất cả các vị trí import, khởi tạo và gọi tới biểu tượng (symbol) mục tiêu.
2. Xác định toàn bộ các file unit test (`*Test.kt` trong `app/src/test/`) và instrumented test (`app/src/androidTest/`) đang kiểm thử trực tiếp hoặc gián tiếp module đó.

### Đầu Ra Bắt Buộc

| Nơi gọi (Caller)                  | `file:line` | Cách sử dụng (Usage Pattern)     | Tương thích? (`Yes` / `No` + Lý do)            |
| :-------------------------------- | :---------- | :------------------------------- | :--------------------------------------------- |
| Screen / ViewModel / Repo / Test  | Dòng cụ thể | Cách truyền tham số & nhận kết quả | Thay đổi đề xuất có làm gãy nơi gọi này không? |

- **Danh sách Unit Tests bị ảnh hưởng**: Liệt kê mọi file `*Test.kt` cần chạy hoặc cập nhật theo Test Contract.
- **Quy tắc chuyển đổi (Migration Rule)**: Nếu bất kỳ nơi gọi nào bị đánh dấu `No`, kế hoạch triển khai (`docs/plans/...`) BẮT BUỘC phải liệt kê bước cập nhật tường minh cho nơi gọi đó.

---

## Hợp Đồng Đầu Ra & Các Hành Vi Bị Cấm

- **Hợp đồng đầu ra**: Mọi khẳng định phải đi kèm dẫn chứng `[SRC]` (`file:line`) hoặc `[CMD]`. Giao tiếp bằng tiếng Việt (giữ nguyên tên hàm/lớp/thuật ngữ kỹ thuật bằng tiếng Anh).
- **Hành vi bị cấm (BANNED)**:
  - Đoán mò hành vi runtime mà không đọc file nguồn thực tế.
  - Đọc lặp lại cùng một khoảng dòng của cùng một file nhiều lần trong một phiên (`00-core.md` §Token Efficiency).
  - Xả toàn bộ lịch sử git hoặc log kiểm thử không giới hạn mà không dùng `-n <N>` hoặc `--tests`.
