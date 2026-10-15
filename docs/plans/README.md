# Kế Hoạch Triển Khai (`docs/plans/`)

Thư mục này lưu các kế hoạch triển khai (Implementation Plans) cho mọi task tính năng, refactor hoặc sửa lỗi phức tạp của **AppTruyen**. Plan do subagent `@planner` soạn và Lead Reviewer (Claude Code CLI, hoặc `@reviewer` khi CLI không dùng được) phê duyệt trước khi code. Quy trình chi tiết: [`.agents/rules/05-plan-review.md`](../../.agents/rules/05-plan-review.md).

Spec/thiết kế (nếu có) đặt ở [`docs/specs/`](../specs/).

## Quy Định Đặt Tên

- Định dạng: `YYYY-MM-DD-<task-name>-implementation-plan.md`
- Ví dụ: `2026-10-11-source-login-wikidich-implementation-plan.md`

## Code Freeze & Trạng Thái Phê Duyệt

- Code Freeze Guard (`.agents/scripts/code-freeze-guard.js`) quản lý trạng thái đóng băng code dựa trên tất cả các plan trong thư mục này (trừ `README.md`).
- Codebase được mở khóa khi và chỉ khi:
  1. KHÔNG có plan nào ở trạng thái blocking (`Draft`, `Ready for Review`, hoặc thiếu header/thẻ không hợp lệ).
  2. Có ít nhất một plan ở trạng thái mở khóa: header là `Approved` hoặc `In Progress`, và dòng không trống cuối cùng là `[PLAN_APPROVED]`.
- Plan kết thúc được chuyển sang `Completed`, plan hủy/thay thế chuyển sang `Superseded` (trạng thái đóng, không gây freeze).
- Kiểm tra trạng thái mở khóa độc lập qua lệnh:
  ```powershell
  node .agents/scripts/plan-approval.js status
  ```

## Bảng Trạng Thái

| Ngày       | Kế hoạch                                                                                                   | Trạng thái   | Tóm tắt                                                                                     |
| :--------- | :--------------------------------------------------------------------------------------------------------- | :----------: | :------------------------------------------------------------------------------------------ |
| 2026-10-11 | [Khắc phục audit governance .agents/](./2026-10-11-governance-audit-remediation-implementation-plan.md)     | `Completed`  | Khắc phục toàn bộ 19 audit findings của Claude CLI và các lỗ hổng bảo mật/vòng đời.         |
| 2026-10-11 | [Đăng nhập nguồn truyện & WikidichScraper](./2026-10-11-source-login-wikidich-implementation-plan.md)      | `Superseded` | Đóng theo quyết định D3; tính năng đăng nhập Wikidich sẽ được lập plan mới sau.              |
| 2026-10-13 | [Đăng nhập nguồn, Wikidich & Gỡ TruyenFull, TruyenCom](./2026-10-13-source-auth-wikidich-remove-truyenfull-truyencom-implementation-plan.md) | `Completed`  | Tích hợp Wikidich, hạ tầng Source Auth (WebView + Paste Cookie), gỡ TruyenFull & TruyenCom. |
| 2026-10-13 | [Nâng cấp nguồn SsTruyen sang sstruyen.pics](./2026-10-13-sstruyen-pics-migration-implementation-plan.md) | `Completed`  | Chuyển tên miền sang sstruyen.pics (58.265 truyện), REST API danh sách chương, bộ lọc completed, sort=updatedAt, Tailwind prose, tương thích ngược 100% dữ liệu đã tải. |
| 2026-10-13 | [Sửa 3 lỗi nguồn SsTruyen (Số chương, Bộ lọc, Danh sách chương)](./2026-10-13-sstruyen-search-filter-chapters-fix-implementation-plan.md) | `Completed`  | Bổ sung dateModified span hiển thị số chương tìm kiếm, nạp trọn vẹn 93 thể loại qua REST API `/genres`, nạp `novelId` qua `/novels/{slug}` tải đầy đủ 100% chương không bị đứt đoạn. |
| 2026-10-13 | [Nâng cấp kiến trúc lớn (Media3, Room 3, Kotlin 2.4, AGP 9.4)](./2026-10-13-major-architecture-upgrades-implementation-plan.md) | `Completed`  | Nâng cấp toàn diện: Media3 Session (1.11.1), Room 3.0 KMP (3.0.3), Kotlin 2.4.21, AGP 9.4.0 và Gradle 9.6.0. |
| 2026-10-14 | [Tối ưu tốc độ tải chương truyện song song theo lô](./2026-10-14-parallel-chapter-download-implementation-plan.md) | `Completed` | Tải song song 4 chương/lô (bounded concurrency), tự động sắp xếp theo `chapterNumber` tăng dần trước khi lưu Room DB và phát tiến độ. |
| 2026-10-14 | [Tra cứu hình ảnh & hệ Pokémon khi đọc truyện](./2026-10-14-pokemon-image-hover-implementation-plan.md) | `Completed` | Nhận diện tên Pokémon đa ngôn ngữ (Việt/Romaji, Hán-Việt + đồng nhân, Anh, Trung, Nhật) bằng Trie longest-match-first, gạch chân nét đứt trên `ReaderScreen`, chạm mở hộp thoại xem ảnh/hệ/hình thái/ngôn ngữ, mặc định BẬT. |

---

## Template

BẮT BUỘC tuân thủ định dạng header chặt: H1 là dòng không trống đầu tiên của file, theo liền sau là khối blockquote header:

```markdown
# Kế hoạch triển khai: <Tên task>

> **Trạng thái**: Ready for Review
> **Revision**: 1
> **Ngày tạo**: YYYY-MM-DD
> **Planner**: @planner (<model>)

## Technical Plan

### 1. Mục Tiêu

### 2. Pre-Work Reading

- `AGENTS.md`, `.agents/rules/00-core.md`, ...
- File source đã đọc: ...

### 3. Assumptions & Cross-Task Dependencies

### 4. Verified Versions Table

| Thư viện | Version hiện tại | Version đề xuất | Latest đã xác minh | Nguồn URL | Tương thích? |
| -------- | ---------------- | --------------- | ------------------ | --------- | ------------ |

### 5. Files Cần Chỉnh Sửa / Tạo Mới

| Nhãn       | File | Tầng | Ghi chú (file nhạy cảm → lý do) |
| ---------- | ---- | ---- | ------------------------------- |
| `[NEW]`    |      |      |                                 |
| `[MODIFY]` |      |      |                                 |

### 6. Các Bước Thực Hiện

- [ ] Bước 1: ...
- [ ] Bước 2: ...
- [ ] Bước 3: ...
- [ ] Bước 4: ...
- [ ] Bước 5: ...

### 7. Test Contract

| # | Hành vi (ngôn ngữ nghiệp vụ) | Input | Kết quả mong đợi / thông báo lỗi tiếng Việt |
| - | ---------------------------- | ----- | ------------------------------------------- |

### 8. Verification Plan

- Unit test: `app/src/test/java/com/personal/apptruyen/...Test.kt`
- Lệnh: `.\gradlew.bat ktlintCheck; .\gradlew.bat testDebugUnitTest; .\gradlew.bat assembleDebug`
- Kiểm thử thủ công trên thiết bị: ...
- Negative probe (nếu thêm rule enforcement): ...

### 9. Rủi Ro & Phòng Ngừa

| Rủi ro | Mức (Cao/TB/Thấp) | Technical control |
| ------ | ----------------- | ----------------- |

### 10. Cập Nhật Docs

- `docs/changelog.md`: ...

## Audit History

<!-- Reviewer chỉ append "### Audit Run N" ở dưới. Không sửa phần Technical Plan. -->
```
