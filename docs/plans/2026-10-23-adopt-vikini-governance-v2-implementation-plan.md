# Kế hoạch triển khai: Nâng cấp Hệ thống Rules, Subagents & Skills theo Vikini Governance 2.0

> **Trạng thái**: Completed
> **Revision**: 1
> **Ngày tạo**: 2026-10-23
> **Planner**: @planner ([PLANNER_MODEL: gemini-4-argon])

## Technical Plan

### 1. Mục Tiêu (Goal) & Out of Scope (What We're NOT Doing)

#### 1.1. Mục Tiêu (Goal)

Nâng cấp hệ thống quản trị đa tác tử (`.agents/` và `docs/plans/`) của dự án **AppTruyen** theo kiến trúc **Vikini Governance 2.0 (Clean Progressive Disclosure, Standardized Skills & Hardened Guard)** đã được kiểm chứng thực tế tại dự án `vikini` và đối chiếu với tài liệu chính thức Google Antigravity tháng 10/2026, đồng thời **bảo toàn 100% các cơ chế phòng thủ đặc thù vốn đã vượt trội của AppTruyen** (bộ mô phỏng chunk `plan-approval.js`, quy trình tiền thu thập bằng chứng `review-run.js`, và bộ phân tích từ vựng PowerShell 5.1 `tokenizeCommand` trong `code-freeze-guard.js`).

Kế hoạch giải quyết trọn vẹn 5 trụ cột:

1. **Chuẩn hóa Schema YAML Frontmatter (`model: pro`), Model Roster Tháng 10/2026, Khử Tự Đọc Thừa Bước 0 & Nâng Cấp Chuẩn Plan 10 Mục + `pstack` (`.agents/agents/`)**:
   - Trong `.agents/agents/planner/agent.md`, `.agents/agents/reviewer/agent.md`, và `.agents/agents/qa/agent.md`: đổi `model: gemini-4-argon` / `model: claude-4.6-opus` sang giá trị hợp lệ `model: pro` theo schema YAML frontmatter của Google Antigravity Subagents (`model: inherit | flash | pro`), đồng thời xóa trường `role:` không nằm trong đặc tả frontmatter.
   - Đồng bộ Model Roster tháng 10/2026: xóa bỏ hoàn toàn tham chiếu tới các model sắp ngừng hỗ trợ (`claude-4.6-opus`, `Gemini 3.1 Pro`) tại `.agents/agents/qa/agent.md:4,116`, `.agents/README.md:35`, và `.agents/rules/01-coding.md:93`; chuẩn hóa sang 4 mô hình chủ lực tháng 10/2026 gồm **`Gemini 4 Argon`** (`gemini-4-argon`), **`Claude Opus 5.5 (thinking)`** (`claude-opus-5-5`), **`Claude Sonnet 5.5 (thinking)`** (`claude-sonnet-5-5`), và **`Gemini 3.8 Flash`** (`gemini-3.8-flash`).
   - Giải thích rõ cơ chế runtime của `model: pro` trong `.agents/agents/qa/agent.md` và `.agents/README.md`: frontmatter `model: pro` chọn phân tầng `pro` của phiên hiện tại chứ không tự động chuyển đổi nhà cung cấp (provider) giữa Gemini và Claude; bắt buộc gắn nhãn định danh model thực tế đang chạy trong phiên (`[PLANNER_MODEL: ...]`, `[REVIEWER_MODEL: ...]`, `[QA_MODEL: ...]`).
   - Khử bước tự đọc lại chính file `agent.md` của mình ở Bước 0 trong `@planner` và `@reviewer` (vì thân Markdown bên dưới YAML frontmatter đã được nạp làm system prompt), nhưng **giữ nguyên** yêu cầu đọc `.agents/rules/02-quality.md`, các `SKILL.md` theo bảng Pre-Work Protocol và các tài liệu vệ tinh (`allowlist.md`, `evidence-bar.md`, `mental-simulation.md`).
   - Tích hợp **8 Nguyên Lý Kỹ Thuật Cốt Lõi (`pstack` chuyển hóa cho Android/Kotlin/Compose/Room/Scraper)** vào `.agents/agents/planner/agent.md`, mở rộng cấu trúc Plan 10 mục (bổ sung **Out of Scope**, bảng **Skills Loaded & Applied**, và **Caller-First Design Sketch**) trong `.agents/agents/planner/agent.md`, `docs/plans/README.md`, và **Plan Completeness Gate (7 hạng mục)** trong `.agents/agents/reviewer/agent.md` + `.agents/scripts/claude-reviewer-prompt.md`.

2. **Tối Ưu Context Engineering, Progressive Disclosure (`trigger: glob`), `Token Efficiency (tokenleak discipline)` & `Skill Precedence Hierarchy` (`.agents/rules/`)**:
   - Chuyển `.agents/rules/01-coding.md` từ `trigger: always_on` sang `trigger: glob` với `globs: "*.kt, *.kts, **/*.kt, **/*.kts"`.
   - Chuyển `.agents/rules/03-ui.md` từ `trigger: model_decision` sang `trigger: glob` với `globs: "*Screen.kt, **/ui/**/*.kt, **/res/**/*.xml"`.
   - Giữ `trigger: always_on` cho 4 file quy tắc điều phối cốt lõi (`00-core.md`, `02-quality.md`, `05-plan-review.md`, `06-docs.md`) với tổng dung lượng `< 18,000` ký tự (và mỗi file rule `<= 12,000` ký tự).
   - Bổ sung mục **`## Core Coding & Testing Invariants`** vào `.agents/rules/00-core.md` (`always_on`) để mọi bất biến cốt lõi (cấm `!!`, cấm `GlobalScope`/`runBlocking` ở main source set, cấm catch nuốt lỗi, bắt buộc rethrow `CancellationException`, bắt buộc unit test trong `app/src/test/`, UI 100% tiếng Việt, ngưỡng kích thước file) luôn thường trực ngay cả khi chưa mở file `.kt`.
   - Bổ sung mục **`## Token Efficiency (tokenleak discipline)`** vào `.agents/rules/00-core.md` (Locate before reading bằng `grep_search` + `StartLine`/`EndLine` cho file > 400 dòng, No duplicate reads, Bounded command output với `--tests` và `git log -n <N>`, Subagent delegation cho `research`).
   - Bổ sung mục **`## Skill Precedence Hierarchy (AppTruyen-First, Superpowers-Complementary)`** vào `.agents/rules/02-quality.md` để ngăn các skill của plugin `superpowers` (`using-git-worktrees`, `writing-plans`, `executing-plans`, `finishing-a-development-branch`) ghi đè luật cấm git mutating tự động hoặc quy trình phê duyệt plan của AppTruyen.

3. **Xây Dựng Bộ 4 Skills Nội Bộ Chuẩn Hóa Cho AppTruyen (`.agents/skills/apptruyen-*/SKILL.md`) & Đồng Bộ Workflows**:
   - Tạo mới 4 skills nội bộ chuyên biệt cho kiến trúc Android/Kotlin/Jetpack Compose/Room 3/Scraper/Media3 TTS của AppTruyen:
     1. `.agents/skills/apptruyen-debug/SKILL.md`: Hợp nhất quy trình **Part 1: Systematic Debugging Protocol (`/debug`, 6 bước)** và **Part 2: Post-Fix Protocol (`/post-fix`, 5 bước)** kèm Circuit Breaker 3 lần sửa thất bại.
     2. `.agents/skills/apptruyen-audit/SKILL.md`: Quy trình **8 bước Code Quality & Architecture Audit (`/audit`)** + Exit Criteria kiểm tra Clean Architecture + MVVM/MVI, Room 3 (`NULL AS content`, `withWriteTransaction`), Scraper (`BaseScraper`, `ScraperException`, `ScraperCircuitBreaker`), Compose stability (`ImmutableList`, `key`, `collectAsStateWithLifecycle`) và UI 100% tiếng Việt.
     3. `.agents/skills/apptruyen-investigate/SKILL.md`: Kỹ năng điều tra **Read-Only** gồm 4 chế độ: `/how` (trace luồng `Screen` → `ViewModel` → `Repository` → `Scraper` / `Room` / `TTS`), `/why` (git archaeology + tra cứu `docs/lessons-learned.md` & `docs/plans/`, tách bảng `Fact` vs `Inference`), `/recall` (khôi phục ngữ cảnh phiên qua `plan-approval.js status` + `docs/changelog.md` + `git status`), và `/blast-radius` (phân tích phạm vi ảnh hưởng trước khi sửa hàm/interface/model dùng chung trong `data/repository/`, `data/remote/`, `data/local/`, `data/model/`, `tts/`, `util/`).
     4. `.agents/skills/apptruyen-simplify/SKILL.md`: Kỹ năng tinh giản code bảo toàn hành vi (**Subtract Before You Add**, **Laziness Protocol**, **Strict Behavior Preservation**), giới hạn trong 4 thao tác an toàn kèm bằng chứng `grep_search` 0 refs và kiểm chứng Tier 1 / Tier 2.
   - Đồng bộ `.agents/workflows/{debug,post-fix,audit,update-docs}.md` trỏ về các skill tương ứng và sửa lỗi doc drift trong `.agents/workflows/debug.md:20` (đổi `curl.exe -sL <url>` vốn bị guard chặn thành `curl.exe -q -s <url>`).

4. **Gia Cố `code-freeze-guard.js`, `plan-approval.js`, `hooks.json`, `allowlist.md`, `claude-reviewer-*` & Đồng Bộ Host Nguồn Truyện**:
   - **Layer 2 `.md`-only guard cho `docs/**` và `UNFROZEN_FILES`**: Trong `.agents/scripts/code-freeze-guard.js`, kiểm tra mọi thao tác ghi vào `UNFROZEN_PREFIXES` (`docs/`) và `UNFROZEN_FILES` bắt buộc phải có đuôi `.md` (`!pk.endsWith('.md')` ➔ `ask`), chặn hoàn toàn việc tạo script/file thực thi (`.js`, `.mjs`, `.ps1`, `.bat`, `.sh`) bên trong `docs/` trong thời gian code freeze.
   - **Read Guard cho file secret và đường dẫn ngoài repo**: Trong `decide()` của `.agents/scripts/code-freeze-guard.js`, bổ sung nhánh kiểm tra cho các công cụ đọc file (`Read`, `read_file`, `view_file`): chặn (`ask`) nếu đọc đường dẫn ngoài repo (trừ Antigravity artifact hợp lệ) hoặc đọc file khớp `isSecretPath(relative)` / `.git/` / `.gemini/`; cho phép (`allow`) đối với các file thông thường trong repo.
   - **Hỗ trợ bí danh công cụ (`write_file`, `Write`, `edit_file`, `Edit`, `MultiEdit`) & `formatHookOutput`**: Mở rộng `.agents/hooks.json`, `extractInput` (`code-freeze-guard.js`), và `normalizeToolArgs` / `simulateWrite` / `detectApprovalTransition` (`plan-approval.js`) để nhận diện đầy đủ cả tên công cụ hiện hành của Antigravity lẫn các bí danh mới (`write_file`, `Write`, `edit_file`, `Edit`, `MultiEdit`), đồng thời export hàm `formatHookOutput(result, rawPayload)` chuẩn hóa đầu ra cho cả Antigravity hook lẫn Claude Code CLI `PreToolUse` hook.
   - **Đồng bộ `SOURCE_HOSTS` với 3 nguồn truyện đang hoạt động**: Bổ sung 4 host của SsTruyện mới và Wikidich (`sstruyen.pics`, `api.sstruyen.pics`, `file.sstruyen.pics`, `wikicv.org`) vào `SOURCE_HOSTS` trong `.agents/scripts/code-freeze-guard.js`, `.agents/scripts/claude-reviewer-settings.json`, `.agents/scripts/claude-reviewer-prompt.md` (đảm bảo 0 dấu nháy kép `"` theo test `H7`), và `.agents/agents/reviewer/allowlist.md`.
   - Mở rộng unit tests trong `.agents/scripts/code-freeze-guard.test.js` và `.agents/scripts/plan-approval.test.js` để kiểm chứng toàn bộ các chốt chặn mới và tự động kiểm tra ngưỡng ký tự / schema của `.agents/rules/*.md`, `.agents/agents/*/agent.md`, `.agents/skills/apptruyen-*/SKILL.md`.

5. **Định Nghĩa Rõ 3 Quyết Định Sản Phẩm (`[NEEDS_PRODUCT_DECISION]`) Kèm Default Assumption**:
   - Trình bày rõ ràng 3 quyết định sản phẩm (`D1` về chiều thực thi Planner/Reviewer, `D2` về chế độ Fast-Track Mode trên `app/`, `D3` về việc giữ hay xóa `.agents/workflows/*.md`) tại Mục 3.3 với **Default Assumption** bảo toàn tính ổn định và an toàn cao nhất cho AppTruyen.

#### 1.2. Out of Scope (What We're NOT Doing)

- **KHÔNG** thay đổi bất kỳ file mã nguồn Kotlin/Android runtime nào trong `app/src/main/`, `app/src/test/`, `app/src/androidTest/`, hay cấu hình Gradle (`build.gradle.kts`, `app/build.gradle.kts`, `settings.gradle.kts`).
- **KHÔNG** hạ cấp hoặc xóa bỏ bất kỳ cơ chế bảo mật hiện có nào của AppTruyen trong `.agents/scripts/plan-approval.js` (`simulateWrite`, `resolveChunk`, `detectApprovalTransition`, `[APPROVED_PLAN_LOCKED]`, `.pending-plan-writes.json`), `.agents/scripts/review-run.js` (`prepare` thu thập `evidence.md`, chụp `before.md`, `verifyClaudeAppend`), hay `.agents/scripts/code-freeze-guard.js` (`tokenizeCommand`, `[NON_ASCII_COMMAND]`, `isSafeGitOperand`, `isSafeReadPath`, `isReadOnlyCurl`).
- **KHÔNG** cấp quyền `Bash` hay `PowerShell` cho Claude Code CLI Reviewer trong `.agents/scripts/run-claude.ps1` và `.agents/scripts/claude-reviewer-settings.json` (giữ nguyên kiến trúc pre-collected evidence an toàn tuyệt đối đã ghi trong `docs/lessons-learned.md:44-52`).
- **KHÔNG** xóa hoặc thay đổi 7 skills Android hiện có trong `.agents/skills/` (`adaptive`, `agp-9-upgrade`, `android-cli`, `android-intent-security`, `appfunctions`, `camerax`, `display-glasses-with-jetpack-compose-glimmer`, `edge-to-edge`, `engage-sdk-integration`, `migrate-xml-views-to-jetpack-compose`, `navigation-3`, `perfetto-sql`, `perfetto-trace-analysis`, `play-billing-library-version-upgrade`, `r8-analyzer`, `styles`, `testing-setup`, `verified-email`, `wear-compose-m3`).
- **KHÔNG** sửa đổi nội dung lịch sử của các file kế hoạch đã đóng (`Completed` / `Superseded`) trong `docs/plans/`.

---

### 2. Pre-Work Reading & Skills Loaded & Applied

#### 2.1. Danh Sách Tài Liệu & File Nguồn Đã Đọc (`Pre-Work Reading`)

| File / Tài liệu đã đọc | Phạm vi dòng | Phát hiện kỹ thuật chính (`[SRC]`) |
| :--- | :---: | :--- |
| `AGENTS.md` | 1–33 | Định nghĩa 5 quy tắc cốt lõi, bảng tài liệu bắt buộc đọc và tổng quan `.agents/` (dòng 27 tham chiếu `/update-docs`, `/debug`, `/post-fix` trong `.agents/workflows/`). |
| `.agents/README.md` | 1–105 | Dòng 35 còn ghi `@qa (agents/qa/agent.md, claude-4.6-opus / fallback gemini-4-argon)`; dòng 48–54 liệt kê 5/7 file rules là `always_on`. |
| `.agents/rules/00-core.md` | 1–108 | `4,008 chars`. Đang `always_on`; chưa có mục `Core Coding & Testing Invariants` và `Token Efficiency (tokenleak discipline)`. |
| `.agents/rules/01-coding.md` | 1–116 | `5,244 chars`. Đang `trigger: always_on` (nên chuyển sang `trigger: glob` cho `*.kt, *.kts`); dòng 93 còn ghi `(Claude Opus 4.6 / 5.5, Gemini 4 Argon, Gemini 3.1 Pro)`. |
| `.agents/rules/02-quality.md` | 1–109 | `4,904 chars`. Bảng Pre-Work Protocol (dòng 10–24) chưa ánh xạ tới bộ skills `.agents/skills/apptruyen-*/SKILL.md`; chưa có mục `Skill Precedence Hierarchy`. |
| `.agents/rules/03-ui.md` | 1–46 | `2,331 chars`. Đang `trigger: model_decision` (nên chuyển sang `trigger: glob` cho `*Screen.kt, **/ui/**/*.kt, **/res/**/*.xml`). |
| `.agents/rules/04-scraping.md` | 1–49 | `2,953 chars`. Chuẩn xác, đã ghi nhận gỡ bỏ `LacaTruyen` / `CryptoJS.kt` và giữ `trigger: model_decision`. |
| `.agents/rules/05-plan-review.md` | 1–107 | `6,829 chars`. Mô tả đầy đủ máy trạng thái `plan-approval.js` và Dual-Reviewer (`run-claude.ps1` + `@reviewer`). |
| `.agents/rules/06-docs.md` | 1–77 | `2,095 chars`. Ma trận cập nhật docs và template commit message. |
| `.agents/agents/planner/agent.md` | 1–110 | `5,951 chars`. Dòng 4–5 dùng `model: gemini-4-argon` và `role: Technical Planner`; chưa có 8 nguyên lý `pstack` và 3 mục mới (`Out of Scope`, `Skills Loaded & Applied`, `Caller-First Design Sketch`). |
| `.agents/agents/reviewer/agent.md` | 1–130 | `7,853 chars`. Dòng 4–5 dùng `model: gemini-4-argon` và `role:`; Bước 2 (dòng 55–66) mới có 6 mục Completeness Gate. |
| `.agents/agents/reviewer/{allowlist,evidence-bar,mental-simulation}.md` | Toàn bộ | `allowlist.md:30` chỉ liệt kê 8 host cũ (`truyencom.com`, `truyenfull.today`, `sstruyen.net`, `tangthuvien.org`, `lacatruyen.*`), thiếu `sstruyen.pics`, `api.sstruyen.pics`, `file.sstruyen.pics`, `wikicv.org`. |
| `.agents/agents/qa/agent.md` | 1–132 | `4,724 chars`. Dòng 4–5 dùng `model: claude-4.6-opus` và `role:`; dòng 116 ghi `Mặc định claude-4.6-opus`. |
| `.agents/workflows/{debug,post-fix,audit,update-docs}.md` | Toàn bộ | `debug.md:20` hướng dẫn `curl.exe -sL <url>` (thiếu `-q` và dùng `-L` nên bị `code-freeze-guard.js` chặn). |
| `.agents/hooks.json` | 1–29 | `PreToolUse` và `PostToolUse` matcher chưa có `write_file`, `edit_file`. |
| `.agents/scripts/code-freeze-guard.js` | 1–1026 | Dòng 92–101 `SOURCE_HOSTS` thiếu `sstruyen.pics`, `api.sstruyen.pics`, `file.sstruyen.pics`, `wikicv.org`; dòng 705 `extractInput` chỉ gọi `normalizeToolArgs` cho 3 tên công cụ cũ; dòng 837–839 cho phép ghi mọi đuôi file trong `docs/`; chưa có Read Guard cho `Read`/`read_file`/`view_file` và chưa có `formatHookOutput`. |
| `.agents/scripts/plan-approval.js` | 1–934 | Dòng 232–368 `normalizeToolArgs`, dòng 461 `simulateWrite`, dòng 594 `detectApprovalTransition` mới hỗ trợ `write_to_file`, `replace_file_content`, `multi_replace_file_content`, chưa hỗ trợ `write_file`, `Write`, `edit_file`, `Edit`, `MultiEdit`. |
| `.agents/scripts/review-run.js` & `run-claude.ps1` | Toàn bộ | Kiến trúc pre-collected `evidence.md` + `verifyClaudeAppend` + `before.md` hoạt động chặt chẽ, tất cả unit tests trong `review-run.test.js` pass. |
| `.agents/scripts/{claude-reviewer-prompt.md,claude-reviewer-settings.json}` | Toàn bộ | Dòng 10 của `claude-reviewer-prompt.md` (0 dấu nháy kép `"`) và `claude-reviewer-settings.json:8-15` cần bổ sung 4 host nguồn truyện mới để đồng bộ với `SOURCE_HOSTS` và test `H7b`. |
| `.agents/scripts/{code-freeze-guard,plan-approval,review-run}.test.js` | Toàn bộ | Hiện có `code-freeze-guard.test.js` (663 dòng), `plan-approval.test.js` (275 dòng), `review-run.test.js` (160 dòng). |
| `docs/{architecture,progress,scraping,lessons-learned}.md` & `docs/plans/README.md` | Toàn bộ | Xác nhận 3 nguồn truyện đang hoạt động (`tangthuvien.org`, `sstruyen.pics`, `wikicv.org`) và các bài học bảo mật hook/shell tại `docs/lessons-learned.md:20-62`. |
| `c:\Users\wyemh\vikini\.agents\**` & `c:\Users\wyemh\vikini\docs\plans\2026-10-19-remake-agents-governance-v2-implementation-plan.md` | Toàn bộ | Tài liệu tham chiếu chuẩn Vikini Governance 2.0 (Rules, Subagents, 4 Skills `vikini-debug`, `vikini-audit`, `vikini-investigate`, `vikini-simplify`, `code-freeze-guard.js`). |

#### 2.2. Bảng Skills Loaded & Applied

| Skill (`SKILL.md` đã đọc bằng `read_file`) | Lý do kích hoạt | Quyết định kỹ thuật chịu ảnh hưởng trực tiếp trong Kế hoạch |
| :--- | :--- | :--- |
| `C:\Users\wyemh\.gemini\antigravity\builtin\skills\agy-customizations\SKILL.md` | Xác minh cơ chế khám phá (`.agents/rules/`, `.agents/skills/<name>/SKILL.md`, `.agents/hooks.json`), thứ tự ưu tiên nạp và Progressive Disclosure của Google Antigravity. | Đặt 4 skills nội bộ mới tại `.agents/skills/apptruyen-{debug,audit,investigate,simplify}/SKILL.md` với YAML frontmatter `name` + `description` chuẩn; chuyển `01-coding.md` và `03-ui.md` sang `trigger: glob` để tận dụng Progressive Disclosure. |
| `c:\Users\wyemh\vikini\.agents\skills\vikini-investigate\SKILL.md` | Tham khảo cấu trúc 4 chế độ điều tra read-only (`/how`, `/why`, `/recall`, `/blast-radius`) để chuyển hóa sang kiến trúc Android/Kotlin của AppTruyen. | Thiết kế `.agents/skills/apptruyen-investigate/SKILL.md` bám sát luồng `Screen` → `ViewModel` → `Repository` → (`Scraper` \| `Room` \| `TTS`) và lệnh `node .agents/scripts/plan-approval.js status`. |
| `c:\Users\wyemh\vikini\.agents\skills\vikini-simplify\SKILL.md` | Áp dụng nguyên lý *Subtract Before You Add* khi tái cấu trúc `.agents/rules/` và thiết kế skill tinh giản code cho AppTruyen. | Thiết kế `.agents/skills/apptruyen-simplify/SKILL.md` cho Kotlin/Compose và tinh gọn các file `always_on` rules để tổng dung lượng luôn `< 18,000` ký tự. |
| `c:\Users\wyemh\vikini\.agents\skills\vikini-debug\SKILL.md` & `vikini-audit\SKILL.md` | Tham khảo cách hợp nhất `/debug` + `/post-fix` và chuẩn hóa `/audit` 8 bước kèm Exit Criteria. | Thiết kế `.agents/skills/apptruyen-debug/SKILL.md` và `.agents/skills/apptruyen-audit/SKILL.md` đặc thù cho AppTruyen (Room 3, BaseScraper, Compose Material 3, UI 100% tiếng Việt). |

---

### 3. Assumptions & Cross-Task Dependencies

#### 3.1. Giả Định Kỹ Thuật Đã Kiểm Chứng (Verified Assumptions)

1. **Schema YAML Frontmatter & Hành Vi Runtime Của `model: pro` Trên Antigravity Subagents**:
   - **Giả định**: Các file định nghĩa subagent trong `.agents/agents/<name>/agent.md` sử dụng YAML frontmatter với trường `model` nhận một trong 3 giá trị `inherit | flash | pro` (`[URL]` `https://antigravity.google/docs/subagents.md`). Phần thân Markdown bên dưới YAML frontmatter được Antigravity nạp trực tiếp làm system prompt của subagent khi khởi chạy, nên việc yêu cầu subagent tự gọi `view_file` đọc lại chính file `agent.md` của mình ở Bước 0 là dư thừa. Tuy nhiên, việc đọc `.agents/rules/02-quality.md`, các `SKILL.md` và 3 file vệ tinh (`allowlist.md`, `evidence-bar.md`, `mental-simulation.md`) ở Bước 0 vẫn bắt buộc để đảm bảo phòng thủ chiều sâu.
   - **Dẫn chứng**: `[SRC]` `.agents/agents/planner/agent.md:1-35`, `[SRC]` `.agents/agents/reviewer/agent.md:1-48`, `[SRC]` `.agents/agents/qa/agent.md:1-16`.

2. **Tương Tác Giữa `plan-approval.js`, `review-run.js` Và `code-freeze-guard.js` Khi Claude Code CLI Reviewer Chạy**:
   - **Giả định**: Trong AppTruyen, `.agents/scripts/run-claude.ps1:138-145` gọi `claude.exe` với `--permission-mode dontAsk`, `--tools "Read,Grep,Glob,Edit,WebFetch,WebSearch"`, `--settings ".agents/scripts/claude-reviewer-settings.json"`, `--allowedTools "Edit($relPlan) Read($promptPath) Read($evidencePath)"`, và `--disallowedTools "Write" "NotebookEdit" "Bash" "PowerShell" "mcp__*"`. Sau khi `claude.exe` kết thúc, `review-run.js finalize` (`[SRC]` `.agents/scripts/review-run.js:90-151, 378-396`) kiểm tra `verifyClaudeAppend(beforeContent, afterContent)` để đảm bảo `## Technical Plan` và Header không bị sửa đổi, rồi chính `review-run.js` mới gọi `setHeaderStatus(cur, 'Approved')`.
   - **Hệ quả thiết kế quan trọng**: Không gắn `code-freeze-guard.js` chặn tool `Edit` bên trong `claude-reviewer-settings.json` (vì `code-freeze-guard.js:820-832` yêu cầu `stepIdx` của Antigravity và chặn `APPROVAL_TAG_CHANGE` bằng `ask`, vốn sẽ biến thành `deny` trong chế độ `dontAsk` và làm hỏng khả năng ghi `[PLAN_APPROVED]` của Claude Reviewer CLI). Thay vào đó, giữ nguyên sandbox `--allowedTools "Edit($relPlan)"` + `--disallowedTools Bash PowerShell Write` + `verifyClaudeAppend` của `review-run.js`, đồng thời bổ sung `formatHookOutput(result, rawPayload)` trong `code-freeze-guard.js` để hỗ trợ chuẩn schema `hookSpecificOutput` khi cần dùng với định dạng sự kiện `PreToolUse`.
   - **Dẫn chứng**: `[SRC]` `.agents/scripts/code-freeze-guard.js:803-835`, `[SRC]` `.agents/scripts/plan-approval.js:654-660`, `[SRC]` `.agents/scripts/review-run.js:90-151`, `[SRC]` `.agents/scripts/run-claude.ps1:135-177`.

3. **Đồng Bộ `SOURCE_HOSTS` Với Kiểm Thử `H7` Và `H7b` Trong `code-freeze-guard.test.js`**:
   - **Giả định**: Bài test `H7` (`[SRC]` `.agents/scripts/code-freeze-guard.test.js:531-536`) bắt buộc `.agents/scripts/claude-reviewer-prompt.md` có **0 ký tự dấu nháy kép (`"`)**. Bài test `H7b` (`[SRC]` `.agents/scripts/code-freeze-guard.test.js:538-579`) kiểm tra danh sách `WebFetch(domain:...)` trong `.agents/scripts/claude-reviewer-settings.json` phải khớp chính xác 1-1 với `[...SOURCE_HOSTS, 'developer.android.com', 'kotlinlang.org', 'square.github.io', 'jsoup.org', 'code.claude.com', 'git-scm.com', 'nodejs.org', 'learn.microsoft.com', 'curl.se']`. Bài test `H8` (`[SRC]` `.agents/scripts/code-freeze-guard.test.js:581-605`) chạy trực tiếp mọi dòng lệnh mẫu dưới `# hook-allow-examples` và `# hook-ask-examples` trong `.agents/agents/reviewer/allowlist.md` qua `decide({ command: line }, frozen)`.
   - **Dẫn chứng**: `[SRC]` `.agents/scripts/code-freeze-guard.test.js:531-605`.

#### 3.2. Quan Hệ Phụ Thuộc Chéo (Cross-Task Dependencies)

- Tất cả 12 kế hoạch trước đó trong `docs/plans/` (từ `2026-10-11` đến `2026-10-23-fix-reader-scroll-and-wikidich-chapter-count-implementation-plan.md`) đều đã ở trạng thái đóng (`Completed` hoặc `Superseded`). Không có kế hoạch nào đang ở trạng thái mở hay xung đột khóa ghi.
- Các file trong `.agents/**` là file quản trị (`isGovernanceFile(relative) === true` tại `.agents/scripts/code-freeze-guard.js:252-262`), nên theo thiết kế bảo mật của AppTruyen, ngay cả sau khi kế hoạch có `[PLAN_APPROVED]`, Code Freeze Guard vẫn luôn hiển thị hộp thoại `ask` để User xác nhận khi ghi vào `.agents/**`.

#### 3.3. Các Quyết Định Sản Phẩm (`[NEEDS_PRODUCT_DECISION]`)

1. **`[NEEDS_PRODUCT_DECISION]` D1 — Chiều Thực Thi Giữa Planner Và Reviewer**:
   - **Câu hỏi**: User muốn giữ nguyên chiều phân công của AppTruyen hay đảo chiều giống Vikini?
     - **Phương án A (Default Assumption — Khuyến nghị)**: **Giữ nguyên chiều hiện tại của AppTruyen**: Subagent `@planner` (`gemini-4-argon` / `model: pro`) soạn kế hoạch → **Primary Reviewer là Claude Code CLI** (`.agents/scripts/run-claude.ps1` + `review-run.js` tự động thu thập `evidence.md`, kiểm tra `verifyClaudeAppend` chống tampering và tự động cập nhật `> **Trạng thái**: Approved` không cần hộp thoại hỏi User) → **Fallback Reviewer là Subagent `@reviewer`** (`gemini-4-argon` khi Claude CLI hết quota/lỗi).
     - **Phương án B (Giống Vikini)**: Đảo chiều để Primary Planner chạy qua Claude Code CLI và Primary Reviewer chạy qua Subagent `@reviewer`. (Lưu ý: Trên AppTruyen, vì `plan-approval.js` chặn mọi thao tác ghi thẻ `[PLAN_APPROVED]` từ tool của Antigravity bằng `ask`, nếu dùng `@reviewer` làm Primary Reviewer thì mỗi lần duyệt plan đều cần User bấm xác nhận tay 1 lần).
   - **Default Assumption trong kế hoạch này**: Thực hiện theo **Phương án A**.

2. **`[NEEDS_PRODUCT_DECISION]` D2 — Chế Độ Fast-Track Mode (`<= 40` Dòng Trên File UI Composable Thuần)**:
   - **Câu hỏi**: User có muốn bật chế độ Fast-Track Mode (cho phép sửa trực tiếp `<= 40` dòng bằng `replace_file_content`/`edit_file` trên file `*Screen.kt` / `ui/theme/*.kt` khi chưa có `[PLAN_APPROVED]`) không?
     - **Phương án A (Default Assumption — Khuyến nghị)**: **Không tự động bypass Code Freeze cho `app/**`**; giữ nguyên quy định tại `.agents/rules/02-quality.md:29` (*"Task tầm thường (sửa typo, đổi 1 chuỗi, chỉnh docs) không cần plan, nhưng hook vẫn hỏi User khi ghi vào `app/`"*). Điều này đảm bảo không agent nào có thể tự ý sửa code Android trong `app/` khi chưa có plan hoặc chưa được User bấm cho phép.
     - **Phương án B**: Bật `isFastTrackEligible` trong `code-freeze-guard.js` cho các chỉnh sửa `<= 40` dòng trên `app/src/main/java/com/personal/apptruyen/ui/**/*.kt` (loại trừ `*ViewModel.kt`, `*State.kt`, `*Intent.kt`, `*Test.kt`).
   - **Default Assumption trong kế hoạch này**: Thực hiện theo **Phương án A**.

3. **`[NEEDS_PRODUCT_DECISION]` D3 — Giữ Hay Xóa Thư Mục `.agents/workflows/` Sau Khi Tạo `.agents/skills/apptruyen-*/SKILL.md`**:
   - **Câu hỏi**: Sau khi tạo bộ 4 skills `.agents/skills/apptruyen-{debug,audit,investigate,simplify}/SKILL.md`, User muốn giữ lại các file `.agents/workflows/{debug,post-fix,audit,update-docs}.md` hay xóa thư mục `.agents/workflows/`?
     - **Phương án A (Default Assumption — Khuyến nghị)**: **Giữ lại `.agents/workflows/{debug,post-fix,audit,update-docs}.md`** và cập nhật chúng làm cầu nối đồng bộ trỏ sang `.agents/skills/apptruyen-*/SKILL.md`. Cách này giúp bảo toàn 100% các slash command `/debug`, `/post-fix`, `/audit`, `/update-docs` được liệt kê trong `AGENTS.md:27` đồng thời kích hoạt đầy đủ hệ thống Progressive Disclosure Skills.
     - **Phương án B**: Xóa thư mục `.agents/workflows/` và cập nhật `AGENTS.md:27` sang `.agents/skills/apptruyen-*/`.
   - **Default Assumption trong kế hoạch này**: Thực hiện theo **Phương án A**.

---

### 4. Verified Versions Table

> Không thêm hoặc nâng cấp bất kỳ thư viện nào trong `app/build.gradle.kts` hay `package.json`. Bảng dưới đây xác minh môi trường thực thi Node.js, Gradle/Kotlin hiện tại của dự án và đặc tả công cụ AI tháng 10/2026:

| Thư viện / Nền tảng | Version hiện tại | Version đề xuất | Latest đã xác minh (2026-10) | Nguồn URL | Tương thích? |
| :--- | :--- | :--- | :--- | :--- | :---: |
| Google Antigravity Customizations (Subagents, Rules, Skills, Hooks) | `2026.10` (`v2.24.x`) | Giữ nguyên | `model: inherit \| flash \| pro`; `trigger: always_on \| glob \| model_decision` | `https://antigravity.google/docs/subagents.md`, `https://antigravity.google/docs/rules.md` | ✅ |
| Claude Code CLI Hooks & Permissions (`PreToolUse`) | `2.x` (2026) | Giữ nguyên | `hookSpecificOutput` (`permissionDecision: allow \| deny \| ask \| defer`), `blockReadsOutsideWorkingDirectories` | `https://code.claude.com/docs/en/hooks`, `https://code.claude.com/docs/en/permissions` | ✅ |
| Node.js Test Runner (`node:test`, `node:assert/strict`) | Node `v22.x` / `v24.x` | Giữ nguyên | Built-in `node --test` (ES Modules) | `https://nodejs.org/api/test.html` | ✅ |
| Kotlin / AGP / Room 3 / Compose BOM (`app/build.gradle.kts`) | Kotlin `2.4.21`, AGP `9.4.0`, Room `3.0.3`, BOM `2026.05.01` | Không thay đổi | Kotlin `2.4.21`, AGP `9.4.0`, Room `3.0.3`, BOM `2026.05.01` | `https://developer.android.com/jetpack/androidx/releases/room`, `https://kotlinlang.org` | ✅ |

---

### 5. Files Cần Chỉnh Sửa / Tạo Mới & Caller-First Design Sketch

#### 5.1. Danh Sách Files Cần Chỉnh Sửa / Tạo Mới

> **Lưu ý về Code Freeze Guard**: Toàn bộ các file trong `.agents/**` thuộc nhóm `isGovernanceFile(relative)` hoặc `isTestFile(relative)`, nên Code Freeze Guard luôn yêu cầu User xác nhận (`ask`) khi ghi. Các file trong `docs/**` (trừ thay đổi trạng thái nhạy cảm của plan) được tự động cho phép (`allow`).

| Nhãn | File | Tầng | Ghi chú (file nhạy cảm → lý do) |
| :---: | :--- | :---: | :--- |
| `[NEW]` | `.agents/skills/apptruyen-debug/SKILL.md` | Governance / Skill | File trong `.agents/**` → Hook hỏi xác nhận. Hợp nhất quy trình `/debug` (6 bước) và `/post-fix` (5 bước) cho AppTruyen. |
| `[NEW]` | `.agents/skills/apptruyen-audit/SKILL.md` | Governance / Skill | File trong `.agents/**` → Hook hỏi xác nhận. Quy trình `/audit` 8 bước + Exit Criteria cho Clean Architecture, MVVM/MVI, Room 3, Scraper, Compose, UI tiếng Việt. |
| `[NEW]` | `.agents/skills/apptruyen-investigate/SKILL.md` | Governance / Skill | File trong `.agents/**` → Hook hỏi xác nhận. Kỹ năng điều tra Read-Only 4 chế độ (`/how`, `/why`, `/recall`, `/blast-radius`). |
| `[NEW]` | `.agents/skills/apptruyen-simplify/SKILL.md` | Governance / Skill | File trong `.agents/**` → Hook hỏi xác nhận. Kỹ năng tinh giản code bảo toàn hành vi (4 thao tác được phép + quy trình 6 bước). |
| `[MODIFY]` | `.agents/agents/planner/agent.md` | Governance / Agent | File trong `.agents/**` → Hook hỏi xác nhận. Đổi `model: pro`, xóa `role:`, khử tự đọc thừa ở Bước 1, thêm 8 nguyên lý `pstack` và chuẩn Plan 10 mục. |
| `[MODIFY]` | `.agents/agents/reviewer/agent.md` | Governance / Agent | File trong `.agents/**` → Hook hỏi xác nhận. Đổi `model: pro`, xóa `role:`, cập nhật Bước 2 Plan Completeness Gate (7 hạng mục) và ghi chú bí danh công cụ. |
| `[MODIFY]` | `.agents/agents/qa/agent.md` | Governance / Agent | File trong `.agents/**` → Hook hỏi xác nhận. Đổi `model: pro`, xóa `role:`, thay `claude-4.6-opus` bằng cơ chế Runtime Model tháng 10/2026 (`claude-opus-5-5` / `gemini-4-argon`). |
| `[MODIFY]` | `.agents/agents/reviewer/allowlist.md` | Governance / Agent | File trong `.agents/**` → Hook hỏi xác nhận. Bổ sung 4 host nguồn truyện (`sstruyen.pics`, `api.sstruyen.pics`, `file.sstruyen.pics`, `wikicv.org`) vào Nhóm C và ví dụ `# hook-allow-examples`. |
| `[MODIFY]` | `.agents/rules/00-core.md` | Governance / Rule | File trong `.agents/**` → Hook hỏi xác nhận. Thêm `## Core Coding & Testing Invariants`, `## Token Efficiency (tokenleak discipline)`, chuẩn hóa `## Git & Version Control`. |
| `[MODIFY]` | `.agents/rules/01-coding.md` | Governance / Rule | File trong `.agents/**` → Hook hỏi xác nhận. Chuyển sang `trigger: glob` (`globs: "*.kt, *.kts, **/*.kt, **/*.kts"`), cập nhật danh sách model tháng 10/2026 tại dòng 93. |
| `[MODIFY]` | `.agents/rules/02-quality.md` | Governance / Rule | File trong `.agents/**` → Hook hỏi xác nhận. Cập nhật bảng Pre-Work Protocol tích hợp 4 skills `apptruyen-*`, thêm `## Skill Precedence Hierarchy`, cập nhật giới hạn ký tự. |
| `[MODIFY]` | `.agents/rules/03-ui.md` | Governance / Rule | File trong `.agents/**` → Hook hỏi xác nhận. Chuyển sang `trigger: glob` (`globs: "*Screen.kt, **/ui/**/*.kt, **/res/**/*.xml"`). |
| `[MODIFY]` | `.agents/rules/05-plan-review.md` | Governance / Rule | File trong `.agents/**` → Hook hỏi xác nhận. Cập nhật mô tả Layer 2 `.md`-only guard, Read Guard cho secret, bí danh công cụ, và tinh gọn văn bản để giữ tổng `always_on < 18,000` ký tự. |
| `[MODIFY]` | `.agents/workflows/debug.md` | Governance / Workflow | File trong `.agents/**` → Hook hỏi xác nhận. Sửa `curl.exe -sL <url>` ở dòng 20 thành `curl.exe -q -s <url>` và liên kết với `.agents/skills/apptruyen-debug/SKILL.md`. |
| `[MODIFY]` | `.agents/workflows/post-fix.md` | Governance / Workflow | File trong `.agents/**` → Hook hỏi xác nhận. Liên kết đồng bộ với `.agents/skills/apptruyen-debug/SKILL.md` (Part 2). |
| `[MODIFY]` | `.agents/workflows/audit.md` | Governance / Workflow | File trong `.agents/**` → Hook hỏi xác nhận. Liên kết đồng bộ với `.agents/skills/apptruyen-audit/SKILL.md`. |
| `[MODIFY]` | `.agents/README.md` | Governance / Docs | File trong `.agents/**` → Hook hỏi xác nhận. Cập nhật bảng Rules (`glob` cho `01-coding.md` và `03-ui.md`), danh sách 4 skills `apptruyen-*`, xóa tham chiếu `claude-4.6-opus` ở dòng 35, cập nhật mô tả Code Freeze Guard. |
| `[MODIFY]` | `.agents/hooks.json` | Governance / Hook | File trong `.agents/**` → Hook hỏi xác nhận. Bổ sung `write_file` và `edit_file` vào matcher của `PreToolUse` và `PostToolUse`. |
| `[MODIFY]` | `.agents/scripts/plan-approval.js` | Governance / Script | File trong `.agents/**` → Hook hỏi xác nhận. Mở rộng `normalizeToolArgs`, `simulateWrite`, `detectApprovalTransition` hỗ trợ `write_file`, `Write`, `edit_file`, `Edit`, `MultiEdit`. |
| `[MODIFY]` | `.agents/scripts/code-freeze-guard.js` | Governance / Script | File trong `.agents/**` → Hook hỏi xác nhận. Bổ sung 4 host vào `SOURCE_HOSTS`, Read Guard cho `Read`/`read_file`/`view_file`, Layer 2 `.md`-only guard cho `docs/**` & `UNFROZEN_FILES`, mở rộng `extractInput`, export `formatHookOutput`. |
| `[MODIFY]` | `.agents/scripts/claude-reviewer-settings.json` | Governance / Script | File trong `.agents/**` → Hook hỏi xác nhận. Bổ sung 4 quyền `WebFetch(domain:sstruyen.pics)`, `WebFetch(domain:api.sstruyen.pics)`, `WebFetch(domain:file.sstruyen.pics)`, `WebFetch(domain:wikicv.org)`. |
| `[MODIFY]` | `.agents/scripts/claude-reviewer-prompt.md` | Governance / Script | File trong `.agents/**` → Hook hỏi xác nhận. Bổ sung 4 domain nguồn truyện mới ở dòng 10 và cập nhật kiểm tra cấu trúc plan ở mục 4 (giữ 0 dấu nháy kép `"`). |
| `[MODIFY]` | `.agents/scripts/plan-approval.test.js` | Governance / Test | File test trong `.agents/**` → Hook hỏi xác nhận. Bổ sung test `A30` kiểm chứng `normalizeToolArgs`, `simulateWrite`, `detectApprovalTransition` với `write_file`, `Write`, `edit_file`, `Edit`, `MultiEdit`. |
| `[MODIFY]` | `.agents/scripts/code-freeze-guard.test.js` | Governance / Test | File test trong `.agents/**` → Hook hỏi xác nhận. Cập nhật `H5` (matcher mới trong `hooks.json`) & `H7b` (21 hosts), bổ sung test `P6` (`.md`-only guard), `P7` (Read Guard), `L6b` (host mới), `H1c` (`formatHookOutput` + tool aliases), `H11` (Governance Limits & Schema). |
| `[MODIFY]` | `docs/plans/README.md` | Documentation | Cập nhật Template 10 mục và thêm dòng trạng thái của kế hoạch này vào Bảng Trạng Thái. |
| `[MODIFY]` | `docs/changelog.md` | Documentation | Ghi nhận đợt nâng cấp hệ thống quản trị `.agents/` theo Vikini Governance 2.0 dưới mục `## [Unreleased]`. |

#### 5.2. Caller-First Design Sketch (Chữ Ký & Hợp Đồng Giao Tiếp)

1. **Mở rộng `normalizeToolArgs`, `simulateWrite`, `detectApprovalTransition` trong `.agents/scripts/plan-approval.js`**:
   ```js
   export const FULL_WRITE_TOOLS = new Set(['write_to_file', 'write_file', 'Write']);
   export const SINGLE_EDIT_TOOLS = new Set(['replace_file_content', 'edit_file', 'Edit']);
   export const MULTI_EDIT_TOOLS = new Set(['multi_replace_file_content', 'MultiEdit']);

   // Caller-side usage in code-freeze-guard.js & plan-approval.test.js:
   const normWrite = normalizeToolArgs('write_file', { Path: 'docs/plans/2026-10-23-x-implementation-plan.md', Content: '# Plan...', stepIdx: 1 });
   const normEdit = normalizeToolArgs('edit_file', { Path: 'docs/plans/2026-10-23-x-implementation-plan.md', Target: 'old', Replacement: 'new', stepIdx: 2 });
   const normClaudeEdit = normalizeToolArgs('Edit', { file_path: 'docs/plans/2026-10-23-x-implementation-plan.md', old_string: 'old', new_string: 'new', replace_all: false });
   const normMultiEdit = normalizeToolArgs('MultiEdit', { file_path: 'docs/plans/2026-10-23-x-implementation-plan.md', edits: [{ old_string: 'a', new_string: 'b' }] });
   ```

2. **Bổ sung Read Guard, Layer 2 `.md`-Only Guard và `formatHookOutput` trong `.agents/scripts/code-freeze-guard.js`**:
   ```js
   export const SOURCE_HOSTS = Object.freeze([
       'truyencom.com',
       'truyenfull.today',
       'sstruyen.net',
       'sstruyen.pics',
       'api.sstruyen.pics',
       'file.sstruyen.pics',
       'tangthuvien.org',
       'wikicv.org',
       'lacatruyen.buzz',
       'www.lacatruyen.buzz',
       'lacatruyen.world',
       'www.lacatruyen.world'
   ]);

   const READ_TOOLS = new Set(['Read', 'read_file', 'view_file']);

   // Inside decide(input, options):
   if (input.targetFile) {
       const relative = toRepoRelative(input.targetFile);

       if (input.toolName && READ_TOOLS.has(input.toolName)) {
           if (relative === null) {
               if (isAntigravityArtifact(input.targetFile)) return { decision: 'allow' };
               return ask(`⚠️ Security Guard: read targets a path outside the repository: ${input.targetFile}`);
           }
           if (relative.includes('.gemini/') || relative.startsWith('.git/') || isSecretPath(relative)) {
               return ask(`⚠️ Security Guard: reading secret or prohibited path is blocked: ${relative}`);
           }
           return { decision: 'allow' };
       }
       // ... existing gates ...
       if (UNFROZEN_PREFIXES.some((p) => pk.startsWith(p)) || UNFROZEN_FILES.includes(pk)) {
           if (!pk.endsWith('.md')) {
               return ask(`⚠️ Code Freeze Guard: only .md files are allowed inside unfrozen documentation paths (${relative}).`);
           }
           return { decision: 'allow' };
       }
   }

   export function formatHookOutput(result, rawPayload) {
       const root = /** @type {Record<string, any>} */ (rawPayload ?? {});
       if (root.hook_event_name === 'PreToolUse') {
           if (result.decision === 'allow') {
               return null;
           }
           const permissionDecision = root.permission_mode === 'dontAsk' ? 'deny' : 'ask';
           return {
               hookSpecificOutput: {
                   hookEventName: 'PreToolUse',
                   permissionDecision,
                   ...(result.reason ? { permissionDecisionReason: result.reason } : {})
               }
           };
       }
       return result;
   }
   ```

3. **Ma Trận Bảo Toàn Quy Chuẩn (Normative Rule Preservation Matrix) Khi Tinh Gọn `.agents/rules/`**:

   | Quy chuẩn bắt buộc | Vị trí cũ | Vị trí mới sau khi bật Progressive Disclosure | Trạng thái nạp |
   | :--- | :--- | :--- | :--- |
   | Giao tiếp tiếng Việt, Tech Stack bất biến, Ranh giới kiến trúc, Terminal PowerShell 5.1, Git, Release | `00-core.md` | `00-core.md` | `always_on` |
   | Cấm `!!`, cấm `GlobalScope`/`runBlocking`, cấm catch nuốt lỗi, bắt buộc rethrow `CancellationException`, bắt buộc unit test, ngưỡng dòng file | `01-coding.md` | Tóm tắt tại `00-core.md` (`## Core Coding & Testing Invariants`) + đầy đủ tại `01-coding.md` | `always_on` (tóm tắt) + `glob: *.kt, *.kts` (chi tiết) |
   | Token Efficiency (`grep_search` trước, `StartLine`/`EndLine`, không đọc lặp, giới hạn output) | *(Mới từ Vikini)* | `00-core.md` (`## Token Efficiency (tokenleak discipline)`) | `always_on` |
   | Pre-Work Protocol, Tier 1/2/3, Test Triage A/B/C, Post-Fix, Debug, Audit, Skill Precedence | `02-quality.md` | `02-quality.md` | `always_on` |
   | UI 100% tiếng Việt, Material 3 token, State/Event/MVI, `ImmutableList` + `key`, Adaptive/Edge-to-Edge | `03-ui.md` | Tóm tắt UI 100% tiếng Việt tại `00-core.md` + đầy đủ tại `03-ui.md` | `always_on` (tóm tắt) + `glob: *Screen.kt, **/ui/**/*.kt, **/res/**/*.xml` |
   | BaseScraper, selector, `ScraperException`, `ScraperCircuitBreaker`, cookie/auth, fixture test | `04-scraping.md` | `04-scraping.md` | `model_decision` |
   | Code Freeze, Zero-Self-Execution, Dual-Reviewer, Severity Rubric, Máy trạng thái Plan, Pre-Flight Gate | `05-plan-review.md` | `05-plan-review.md` | `always_on` |
   | Ma trận cập nhật docs, template commit message | `06-docs.md` | `06-docs.md` | `always_on` |

---

### 6. Các Bước Thực Hiện

- [ ] **Bước 1 — Tạo mới bộ 4 Skills nội bộ chuẩn hóa cho AppTruyen (`.agents/skills/apptruyen-*/SKILL.md`) và đồng bộ `.agents/workflows/`**:
  1. Tạo `.agents/skills/apptruyen-debug/SKILL.md`: hợp nhất quy trình **Part 1: Systematic Debugging Protocol (`/debug`, 6 bước)** và **Part 2: Post-Fix Protocol (`/post-fix`, 5 bước)** cho Android/Kotlin/Compose/Room/Scraper/TTS; dùng lệnh probe chuẩn allowlist `curl.exe -q -s <url> | Select-String "<selector>"`.
  2. Tạo `.agents/skills/apptruyen-audit/SKILL.md`: quy trình **8 bước Code Quality & Architecture Audit (`/audit`)** + Exit Criteria.
  3. Tạo `.agents/skills/apptruyen-investigate/SKILL.md`: quy trình điều tra **Read-Only** 4 chế độ (`/how`, `/why`, `/recall`, `/blast-radius`) chuyên biệt cho các tầng `ui/`, `data/repository/`, `data/remote/`, `data/local/`, `tts/`, `download/`, `util/` của AppTruyen.
  4. Tạo `.agents/skills/apptruyen-simplify/SKILL.md`: quy trình tinh giản code bảo toàn hành vi (4 danh mục thao tác được phép, quy tắc cấm và 6 bước thực hiện kèm bảng báo cáo trước/sau).
  5. Cập nhật `.agents/workflows/debug.md` (sửa `curl.exe -sL <url>` ở dòng 20 thành `curl.exe -q -s <url>` và trỏ về `.agents/skills/apptruyen-debug/SKILL.md`), `.agents/workflows/post-fix.md`, và `.agents/workflows/audit.md` để đồng bộ với 4 skills mới.

- [ ] **Bước 2 — Chuẩn hóa YAML Frontmatter (`model: pro`), Model Roster Tháng 10/2026, Khử Tự Đọc Thừa Bước 0 & Nâng Cấp Chuẩn Plan 10 Mục + `pstack` trong `.agents/agents/`**:
  1. Cập nhật `.agents/agents/planner/agent.md`: đổi `model: gemini-4-argon` thành `model: pro`, xóa dòng `role: Technical Planner`; khử lệnh tự đọc lại `planner/agent.md` ở Bước 1 nhưng giữ nguyên việc đọc `00-core.md`, `01-coding.md`, `02-quality.md`, các `SKILL.md` theo bảng Pre-Work Protocol và tài liệu dự án; bổ sung mục `## 8 Nguyên Lý Kỹ Thuật Cốt Lõi (pstack cho Android/Kotlin)`; cập nhật 10 mục bắt buộc của `## Technical Plan` (thêm `Out of Scope`, `Skills Loaded & Applied`, `Caller-First Design Sketch`); giữ tổng dung lượng `<= 10,000` ký tự.
  2. Cập nhật `.agents/agents/reviewer/agent.md`: đổi `model: gemini-4-argon` thành `model: pro`, xóa dòng `role: Fallback Lead Architecture Reviewer`; cập nhật Bước 2 **Plan Completeness Gate (7 hạng mục)** đồng bộ với cấu trúc Plan mới; ghi chú sự tương thích với bí danh công cụ `edit_file`/`read_file`; giữ tổng dung lượng `<= 10,000` ký tự.
  3. Cập nhật `.agents/agents/qa/agent.md`: đổi `model: claude-4.6-opus` thành `model: pro`, xóa dòng `role: Lead QA, Bug Hunter & Code Auditor`; thay thế mục `## Runtime Fallback Model` (đang tham chiếu `claude-4.6-opus`) bằng `## Cơ Chế Runtime Model & Fallback` giải thích rõ cơ chế `model: pro`, khuyến nghị chọn `Claude Opus 5.5 (thinking)` (`claude-opus-5-5`) hoặc `Gemini 4 Argon` (`gemini-4-argon`), và bắt buộc ghi `[QA_MODEL: <active-runtime-model>]` ở đầu báo cáo; giữ tổng dung lượng `<= 10,000` ký tự.

- [ ] **Bước 3 — Tối ưu Progressive Disclosure (`trigger: glob`), Ngân Sách Context (`always_on < 18,000` ký tự), `Token Efficiency` & `Skill Precedence Hierarchy` trong `.agents/rules/`**:
  1. Cập nhật `.agents/rules/00-core.md` (`trigger: always_on`): bổ sung mục `## Core Coding & Testing Invariants` và `## Token Efficiency (tokenleak discipline)`, làm rõ phân tách **BANNED Mutating Git Commands** vs **ALLOWED Read-Only Git Inspection** trong `## Git & Version Control`.
  2. Cập nhật `.agents/rules/01-coding.md`: chuyển frontmatter từ `trigger: always_on` sang `trigger: glob` với `globs: "*.kt, *.kts, **/*.kt, **/*.kts"`; cập nhật dòng 93 từ `(Claude Opus 4.6 / 5.5, Gemini 4 Argon, Gemini 3.1 Pro)` thành `(Gemini 4 Argon, Claude Opus 5.5, Claude Sonnet 5.5, Gemini 3.8 Flash)`.
  3. Cập nhật `.agents/rules/02-quality.md` (`trigger: always_on`): cập nhật bảng Pre-Work Protocol tích hợp 4 skills `.agents/skills/apptruyen-{debug,audit,investigate,simplify}/SKILL.md`; bổ sung mục `## Skill Precedence Hierarchy (AppTruyen-First, Superpowers-Complementary)`; ghi rõ giới hạn `always_on < 18,000` ký tự trong mục `Governance-Only Changes`.
  4. Cập nhật `.agents/rules/03-ui.md`: chuyển frontmatter từ `trigger: model_decision` sang `trigger: glob` với `globs: "*Screen.kt, **/ui/**/*.kt, **/res/**/*.xml"`.
  5. Cập nhật `.agents/rules/05-plan-review.md` (`trigger: always_on`): bổ sung mô tả Layer 2 `.md`-only guard cho `docs/**`, Read Guard cho file secret, và bí danh công cụ mới, đồng thời biên tập gọn câu chữ để tổng 4 file `always_on` (`00` + `02` + `05` + `06`) đạt `< 18,000` ký tự.

- [ ] **Bước 4 — Gia cố `.agents/scripts/plan-approval.js`, `.agents/scripts/code-freeze-guard.js`, `.agents/hooks.json`, `.agents/agents/reviewer/allowlist.md` & `.agents/scripts/claude-reviewer-*`**:
  1. Trong `.agents/scripts/plan-approval.js`: thêm `FULL_WRITE_TOOLS`, `SINGLE_EDIT_TOOLS`, `MULTI_EDIT_TOOLS` và cập nhật `normalizeToolArgs`, `simulateWrite`, `detectApprovalTransition` để hỗ trợ đầy đủ `write_file`, `Write`, `edit_file`, `Edit` (`old_string`/`new_string`/`replace_all`), và `MultiEdit` (`edits[]`).
  2. Trong `.agents/scripts/code-freeze-guard.js`:
     - Bổ sung `'sstruyen.pics'`, `'api.sstruyen.pics'`, `'file.sstruyen.pics'`, `'wikicv.org'` vào `SOURCE_HOSTS`.
     - Mở rộng `extractInput(payload, approvalApi)` hỗ trợ đầy đủ `write_file`, `Write`, `edit_file`, `Edit`, `MultiEdit`, `args.Path`, `args.Content`, `args.Target`, `args.Replacement`, `args.old_string`, `args.new_string`, `args.edits`.
     - Trong `decide(input, options)`: (a) thêm Read Guard ở đầu nhánh `if (input.targetFile)` cho `Read`, `read_file`, `view_file` (chặn đọc ngoài repo và chặn đọc `isSecretPath(relative)` / `.git/` / `.gemini/`); (b) thêm Layer 2 `.md`-only guard tại nhánh `UNFROZEN_PREFIXES` / `UNFROZEN_FILES` (`if (!pk.endsWith('.md')) return ask(...)`).
     - Bổ sung và export hàm `formatHookOutput(result, rawPayload)` và sử dụng trong `main()`.
  3. Trong `.agents/hooks.json`: cập nhật `PreToolUse[0].matcher` thành `"write_to_file|write_file|replace_file_content|edit_file|multi_replace_file_content|run_command"` và `PostToolUse[0].matcher` thành `"write_to_file|write_file|replace_file_content|edit_file|multi_replace_file_content"`.
  4. Trong `.agents/agents/reviewer/allowlist.md`, `.agents/scripts/claude-reviewer-settings.json`, và `.agents/scripts/claude-reviewer-prompt.md`: bổ sung 4 host nguồn truyện mới (`sstruyen.pics`, `api.sstruyen.pics`, `file.sstruyen.pics`, `wikicv.org`), cập nhật mục kiểm tra cấu trúc plan trong `claude-reviewer-prompt.md` (đảm bảo 0 dấu nháy kép `"`).

- [ ] **Bước 5 — Cập nhật Unit Tests (`.agents/scripts/plan-approval.test.js` & `.agents/scripts/code-freeze-guard.test.js`) và chạy toàn bộ bộ kiểm chứng**:
  1. Trong `.agents/scripts/plan-approval.test.js`: thêm test `A30` kiểm chứng `normalizeToolArgs`, `simulateWrite` và `detectApprovalTransition` với các bí danh công cụ `write_file`, `Write`, `edit_file`, `Edit`, `MultiEdit` (bao gồm cả chặn `[APPROVED_PLAN_LOCKED]` và `[MULTI_CHUNK_SENSITIVE]`).
  2. Trong `.agents/scripts/code-freeze-guard.test.js`:
     - Cập nhật `H5` khớp với matcher mới của `.agents/hooks.json` và giữ nguyên kiểm tra `H7b` (với 21 hosts = 12 `SOURCE_HOSTS` + 9 doc hosts).
     - Thêm test `P6` (Layer 2 `.md`-only guard trên `docs/**` và `UNFROZEN_FILES`).
     - Thêm test `P7` (Read Guard chặn đọc secret `local.properties`, `keystore.properties`, `.env`, `.git/config`, đường dẫn ngoài repo, và cho phép đọc file thường trong repo / Antigravity artifact).
     - Thêm test `L6b` (`curl.exe -q -s` tới `sstruyen.pics`, `api.sstruyen.pics`, `file.sstruyen.pics`, `wikicv.org`).
     - Thêm test `H1c` (`extractInput` cho `write_file`/`edit_file`/`Edit`/`MultiEdit` và `formatHookOutput` cho payload Claude CLI `PreToolUse`).
     - Thêm test `H11` (Governance Limits & Schema Verification: kiểm tra tự động mọi file `.agents/rules/*.md` `<= 12,000` chars, tổng `always_on < 18,000` chars, mọi file `.agents/agents/*/agent.md` `<= 10,000` chars có `model: pro` và không có `role:` hay `claude-4.6-opus`, và sự tồn tại hợp lệ của 4 file `.agents/skills/apptruyen-*/SKILL.md`).
  3. Chạy đầy đủ bộ test Node.js (`code-freeze-guard.test.js`, `plan-approval.test.js`, `review-run.test.js`) và bộ kiểm chứng Gradle Tier 2 (`ktlintCheck`, `testDebugUnitTest`, `assembleDebug`).

- [ ] **Bước 6 — Cập nhật tài liệu dự án (`.agents/README.md`, `docs/plans/README.md`, `docs/changelog.md`) và đóng trạng thái kế hoạch**:
  1. Cập nhật `.agents/README.md` đồng bộ với bảng Rules (`glob` cho `01` và `03`), bộ 4 skills `apptruyen-*`, Model Roster tháng 10/2026 và các lớp bảo vệ mới của Code Freeze Guard.
  2. Cập nhật `docs/plans/README.md` (Template 10 mục + Bảng Trạng Thái) và `docs/changelog.md` (mục `## [Unreleased]`).
  3. Sau khi hoàn tất toàn bộ kiểm chứng, chuyển dòng trạng thái ở Header của file kế hoạch này sang `> **Trạng thái**: Completed` bằng 1 lệnh `replace_file_content` 1 chunk để khóa lại repo.

---

### 7. Test Contract

| # | Hành vi (ngôn ngữ nghiệp vụ) | Input | Kết quả mong đợi |
| :-: | :--- | :--- | :--- |
| **TC-01** | **Layer 2 `.md`-Only Guard trên `docs/**` và `UNFROZEN_FILES`**: Chặn tạo/ghi file không phải `.md` bên trong `docs/` khi đang code freeze | `decide({ toolName: 'write_to_file', targetFile: 'docs/exploit.js' }, { planApproved: false })` hoặc `targetFile: 'docs/plans/run.ps1'`, `'docs/notes/a.mjs'` | Trả về `{ decision: 'ask', reason: /only \.md files are allowed inside unfrozen documentation paths/ }`. Ngược lại `targetFile: 'docs/architecture.md'` và `'docs/plans/README.md'` trả về `{ decision: 'allow' }`. |
| **TC-02** | **Read Guard cho công cụ đọc file (`Read`, `read_file`, `view_file`)**: Chặn đọc file secret hoặc đường dẫn ngoài repo, cho phép đọc file thường | `decide({ toolName: 'read_file', targetFile: 'local.properties' })`, `targetFile: 'keystore.properties'`, `targetFile: '.env.local'`, `targetFile: 'C:/Windows/win.ini'` | Trả về `{ decision: 'ask', reason: /Security Guard/ }`. Khi đọc `docs/architecture.md`, `app/build.gradle.kts`, hoặc `~/.gemini/antigravity/brain/abc/plan.md` thì trả về `{ decision: 'allow' }`. |
| **TC-03** | **Bí danh công cụ (`write_file`, `Write`, `edit_file`, `Edit`, `MultiEdit`) trong `plan-approval.js` & `code-freeze-guard.js`** | Gọi `edit_file` / `Edit` / `MultiEdit` sửa thân plan đã duyệt (`> **Trạng thái**: Approved` + `[PLAN_APPROVED]`) hoặc sửa file test giảm `assertEquals` | Trên plan đã duyệt: `detectApprovalTransition` trả về `code: 'APPROVED_PLAN_LOCKED'`, `decide()` trả về `decision: 'ask'`. Trên file test: `assessTestIntegrity` phát hiện `fewer assertion/verify call` và trả về `decision: 'ask'`. |
| **TC-04** | **Định dạng đầu ra `formatHookOutput(result, rawPayload)` cho Antigravity và Claude CLI `PreToolUse`** | (a) Payload Antigravity `{ toolCall: { name: 'run_command', args: { CommandLine: 'git status' } } }`; (b) Payload Claude CLI `{ hook_event_name: 'PreToolUse', permission_mode: 'dontAsk', tool_name: 'Read', tool_input: { file_path: 'local.properties' } }`; (c) Payload Claude CLI `allow` | (a) Trả về `{ decision: 'allow' }`; (b) Trả về `{ hookSpecificOutput: { hookEventName: 'PreToolUse', permissionDecision: 'deny', permissionDecisionReason: ... } }` (không có trường `decision` cấp gốc); (c) Trả về `null`. |
| **TC-05** | **Allowlist `curl.exe -q -s` tới 4 host nguồn truyện mới (`sstruyen.pics`, `api.sstruyen.pics`, `file.sstruyen.pics`, `wikicv.org`)** | `decide({ command: 'curl.exe -q -s https://sstruyen.pics/truyen/abc' })`, `https://api.sstruyen.pics/genres`, `https://file.sstruyen.pics/files/covers/a.webp`, `https://wikicv.org/truyen/de-ba` | Tất cả trả về `{ decision: 'allow' }`. Đồng thời `claude-reviewer-settings.json`, `claude-reviewer-prompt.md` (0 dấu `"`), và `allowlist.md` đồng bộ 100% (`H7`, `H7b`, `H8` pass). |
| **TC-06** | **Ngân sách Context, Schema Subagent & Sự tồn tại của 4 Skills `apptruyen-*` (`H11`)** | Quét toàn bộ `.agents/rules/*.md`, `.agents/agents/*/agent.md`, và `.agents/skills/apptruyen-*/SKILL.md` | Mọi file rule `<= 12,000` ký tự; tổng các file `trigger: always_on` `< 18,000` ký tự; `01-coding.md` và `03-ui.md` có `trigger: glob`; cả 3 subagent có `model: pro`, không có `role:` trong frontmatter, không chứa `claude-4.6-opus`, độ dài `<= 10,000` ký tự; 4 file `SKILL.md` tồn tại và hợp lệ. |

---

### 8. Verification Plan

#### 8.1. Unit Tests & Governance Tests (Node.js)

- File test cập nhật:
  - `.agents/scripts/code-freeze-guard.test.js` (bổ sung `P6`, `P7`, `L6b`, `H1c`, `H11`; cập nhật `H5`, `H7b`)
  - `.agents/scripts/plan-approval.test.js` (bổ sung `A30`)
  - `.agents/scripts/review-run.test.js` (regression check)
- Lệnh chạy kiểm chứng quản trị (PowerShell 5.1, nối bằng `;`):
  ```powershell
  node --test .agents/scripts/code-freeze-guard.test.js; node --test .agents/scripts/plan-approval.test.js; node --test .agents/scripts/review-run.test.js; node .agents/scripts/plan-approval.js status
  ```

#### 8.2. Quality Gate Đầy Đủ Của Dự Án (Tier 2 Gradle)

```powershell
.\gradlew.bat ktlintCheck; .\gradlew.bat testDebugUnitTest; .\gradlew.bat assembleDebug
```

#### 8.3. Wiring Probes & Negative Probes (Theo `evidence-bar.md` §4.5.3)

1. **Negative Probe 1 — Chặn ghi file thực thi `.js`/`.ps1` vào `docs/**` khi đang Code Freeze (`P6`)**:
   - Trong `code-freeze-guard.test.js`, gọi `decide({ toolName: 'write_file', targetFile: path.join(REPO_ROOT, 'docs/plans/evil.js'), newContent: 'console.log(1)' }, { planApproved: false })` ➔ xác nhận `decision === 'ask'` và `reason` khớp `/only \.md files are allowed/`.
2. **Negative Probe 2 — Chặn đọc file secret qua `read_file` / `Read` (`P7`)**:
   - Gọi `decide({ toolName: 'read_file', targetFile: path.join(REPO_ROOT, 'local.properties') })` và `decide({ toolName: 'Read', targetFile: path.join(REPO_ROOT, 'keystore.properties') })` ➔ xác nhận `decision === 'ask'`.
3. **Negative Probe 3 — Chặn sửa thân plan đã duyệt qua bí danh công cụ `edit_file` / `Edit` / `MultiEdit` (`A30`)**:
   - Gọi `detectApprovalTransition(normalizeToolArgs('edit_file', { Path: 'docs/plans/test.md', Target: 'Nội dung', Replacement: 'Hack' }), approvedPlan)` ➔ xác nhận trả về `{ code: 'APPROVED_PLAN_LOCKED' }`.
4. **Wiring Probe 4 — Kiểm tra đồng bộ `hooks.json` + CLI spawn (`H2–H5`, `H11`)**:
   - Spawn trực tiếp `node scripts/code-freeze-guard.js --event pre` với payload `write_file` nhắm vào `docs/evil.js` và `read_file` nhắm vào `local.properties` ➔ xác nhận tiến trình trả về exit code `0` và JSON `{"decision":"ask",...}`.

#### 8.4. Kiểm Thử Thủ Công Trên Thiết Bị

- Không áp dụng kiểm thử giao diện thủ công trên thiết bị Android vì kế hoạch này là **Governance-Only Change** (chỉ cập nhật `.agents/` và `docs/`, không chạm bất kỳ file nào trong `app/`).

---

### 9. Rủi Ro & Phòng Ngừa

| Rủi ro | Mức (Cao/TB/Thấp) | Technical control |
| :--- | :---: | :--- |
| **R1**: Gắn `code-freeze-guard.js` làm hook chặn `Edit` cho Claude Code CLI Reviewer sẽ khiến Claude CLI bị từ chối khi ghi `[PLAN_APPROVED]` vào cuối file kế hoạch (do payload của Claude CLI không có `stepIdx` của Antigravity và `detectApprovalTransition` trả về `ask` cho `APPROVAL_TAG_CHANGE`). | **Cao** | Giữ nguyên kiến trúc sandbox của `.agents/scripts/run-claude.ps1:136-145` (`--allowedTools "Edit($relPlan) Read($promptPath) Read($evidencePath)"`, `--disallowedTools Write NotebookEdit Bash PowerShell mcp__*`) kết hợp hậu kiểm bất biến `verifyClaudeAppend` trong `.agents/scripts/review-run.js:90-151`. Không gắn hook chặn `Edit($relPlan)` vào `claude-reviewer-settings.json`. Kiểm chứng qua `review-run.test.js` và `H7b`. |
| **R2**: Khi thêm 4 host nguồn truyện mới (`sstruyen.pics`, `api.sstruyen.pics`, `file.sstruyen.pics`, `wikicv.org`) vào `SOURCE_HOSTS`, nếu quên cập nhật đồng bộ `claude-reviewer-settings.json` hoặc vô tình đưa dấu nháy kép `"` vào `claude-reviewer-prompt.md` sẽ làm gãy test `H7` và `H7b`. | **Trung bình** | Cập nhật đồng bộ cả 4 vị trí (`code-freeze-guard.js`, `claude-reviewer-settings.json`, `claude-reviewer-prompt.md`, `allowlist.md`) và kiểm chứng trực tiếp bằng `node --test .agents/scripts/code-freeze-guard.test.js` (`H7`, `H7b`, `H8`). |
| **R3**: Khi chuyển `01-coding.md` và `03-ui.md` sang `trigger: glob`, agent có thể quên các bất biến cốt lõi (cấm `!!`, cấm `GlobalScope`, bắt buộc unit test, UI 100% tiếng Việt) ở giai đoạn thảo luận/lập kế hoạch trước khi mở file `.kt`. | **Trung bình** | Bổ sung mục `## Core Coding & Testing Invariants` vào `.agents/rules/00-core.md` (`trigger: always_on`) theo Ma Trận Bảo Toàn Quy Chuẩn tại Mục 5.2, đồng thời giữ yêu cầu đọc `01-coding.md` ở Bước 1 của `@planner` và `@reviewer`. |
| **R4**: Tổng dung lượng các file `always_on` vượt ngưỡng `< 18,000` ký tự hoặc file agent vượt ngưỡng `<= 10,000` ký tự sau khi thêm nội dung mới. | **Thấp** | Bổ sung bài test tự động `H11` trong `.agents/scripts/code-freeze-guard.test.js` đo chính xác số ký tự của từng file `.agents/rules/*.md` và `.agents/agents/*/agent.md`, fail ngay nếu vượt ngưỡng. |

---

### 10. Cập Nhật Docs

- `.agents/README.md`: Cập nhật bảng Rules (`trigger: glob` cho `01-coding.md` và `03-ui.md`), danh sách 4 skills nội bộ `.agents/skills/apptruyen-{debug,audit,investigate,simplify}/SKILL.md`, xóa tham chiếu `claude-4.6-opus` ở dòng 35, cập nhật mô tả Code Freeze Guard.
- `docs/plans/README.md`: Cập nhật mục `## Template` theo chuẩn 10 mục mới (gồm `Out of Scope`, `Skills Loaded & Applied`, `Caller-First Design Sketch`) và thêm dòng kế hoạch `2026-10-23-adopt-vikini-governance-v2-implementation-plan.md` vào `## Bảng Trạng Thái`.
- `docs/changelog.md`: Thêm mục mới dưới `## [Unreleased]` → `### Nâng cấp Quản trị AI Agent (.agents/)` ghi nhận việc nâng cấp hệ thống Rules, Subagents, Skills và Code Freeze Guard theo Vikini Governance 2.0.

## Audit History

<!-- Reviewer chỉ append "### Audit Run N" ở dưới. Không sửa phần Technical Plan. -->

### Audit Run 1

[REVIEWER_MODEL: gemini-4-argon]

- **Ngày**: 2026-10-23
- **Đối tượng thẩm định**: Revision 1 (Fallback Lead Architecture Reviewer kích hoạt do Claude Code CLI trả mã `42` hết hạn mức phiên).

#### 1. Kiểm Chứng Lỗi Cũ

Đây là lượt thẩm định đầu tiên (`Audit Run 1`) cho Revision 1 nên không có danh sách lỗi tồn đọng từ lượt trước.

#### 2. Phát Hiện Mới (Fresh Findings & Regression Check)

Không phát hiện lỗi mức `[BLOCKER]` hoặc `[MAJOR]`. Ghi nhận 2 khuyến nghị mức `[MINOR]` (`[Advisory Recommendations]`) để lưu ý khi triển khai Bước 4:

- **`[MINOR-01]` — Đọc `oldContent` từ đĩa cho `assessTestIntegrity` khi dùng `write_file` / `Write` và chuẩn hóa `args.AbsolutePath` (`code-freeze-guard.js:697,715,757`)**:
  - **Đối chiếu mã nguồn (`[SRC]`)**: Tại `.agents/scripts/code-freeze-guard.js:715` và `757`, điều kiện nạp `oldContent` từ file hiện có trên đĩa để tính `assessTestIntegrity(input.oldContent, input.newContent)` đang kiểm tra cứng `if (toolName === 'write_to_file' && oldContent === undefined && targetFile)`. Ngoài ra tại dòng `697`, `rawTarget` đang lấy `args.TargetFile ?? args.file_path ?? args.path`.
  - **Khuyến nghị (`[Advisory Recommendations]`)**: Khi mở rộng `extractInput` ở Bước 4.2, đổi điều kiện ở dòng `715` và `757` sang `FULL_WRITE_TOOLS.has(toolName)` (bao gồm `write_to_file`, `write_file`, `Write`) để trường hợp ghi đè file test bằng `write_file` vẫn phát hiện được việc giảm số lượng `assert*`/`verify`. Đồng thời bổ sung `args.Path ?? args.AbsolutePath` vào chuỗi trích xuất `rawTarget` để bao phủ cả công cụ `read_file` và `view_file`.
- **`[MINOR-02]` — Chuẩn hóa cờ `Append` của `write_file` và `replace_all` của `Edit`/`MultiEdit` trong `normalizeToolArgs` (`plan-approval.js:245-365`)**:
  - **Đối chiếu mã nguồn (`[SRC]`)**: Công cụ `write_file` có tham số tùy chọn `Append: boolean`, trong khi `simulateWrite` tại `.agents/scripts/plan-approval.js:461-463` mô phỏng nhóm `FULL_WRITE_TOOLS` là ghi đè toàn bộ nội dung file. Bên cạnh đó, `edit_file` mặc định thay thế mọi vị trí khớp của `Target`, còn `Edit` / `MultiEdit` có cờ `replace_all: boolean` (mặc định `false`).
  - **Khuyến nghị (`[Advisory Recommendations]`)**: Trong `normalizeToolArgs` (`.agents/scripts/plan-approval.js`), nếu `write_file` truyền `Append: true` khi nhắm vào file plan thì trả về lỗi `error: '[APPEND_NOT_SUPPORTED]'` (fail-closed) để kết quả mô phỏng không bị lệch so với thao tác ghi đĩa thực tế; đồng thời ánh xạ `replace_all` của `Edit`/`MultiEdit` sang `allowMultiple`.

#### 3. Mental Simulation Verification Matrix

| Kịch bản | Status | Evidence Type | Chi tiết kiểm chứng |
| :--- | :---: | :---: | :--- |
| **S1: Cold-start & Build (Gradle / KSP / ktlint)** | `PASS` | `[CMD]`, `[SRC]`, `[URL]` | Chạy `.\gradlew.bat compileDebugKotlin` đạt `BUILD SUCCESSFUL in 24s` (exit `0`). Chạy 3 bộ unit test Node.js (`code-freeze-guard.test.js` 31/31, `plan-approval.test.js` 12/12, `review-run.test.js` 10/10) đều đạt exit `0`. Đối chiếu `[SRC]` `app/build.gradle.kts:22-30,106,133,138,143-144` và `build.gradle.kts:3-10` khớp bảng phiên bản (AGP `9.4.0`, Kotlin `2.4.21`, Room `3.0.3`, Compose BOM `2026.05.01`, `minSdk = 26`). Kế hoạch chỉ sửa `.agents/` và `docs/`, không chạm mã nguồn `app/`. |
| **S2: Lifecycle & Teardown** | `PASS` | `[SRC]`, `[CMD]` | Kế hoạch không thay đổi `TtsService`, `DownloadService`, `TtsManager`, `WebViewContentLoader` hay `ViewModel`. Trong tầng quản trị Node.js, `[SRC]` `.agents/scripts/code-freeze-guard.js:1009-1015` xử lý sự kiện `PostToolUse` bằng cách gọi `approvalApi.clearPendingWrite(stepIdx, REPO_ROOT)` trong khối `try/catch` và kết thúc tiến trình với `{}`. Các bài test tạo thư mục tạm (`code-freeze-guard.test.js:608-661`, `plan-approval.test.js:252-274`) đều dọn dẹp trong khối `finally`. |
| **S3: Database & Temporal Logic** | `PASS` | `[SRC]`, `[ADV]` | Không thay đổi schema Room (`AppDatabase` giữ nguyên version 9) hay `SettingsDataStore`. Đối với trạng thái khóa ghi tạm thời `.agents/.pending-plan-writes.json`, `[SRC]` `.agents/scripts/plan-approval.js:792-828` áp dụng TTL `120_000` ms (`now - entry.at < 120_000`) và ghi nguyên tử qua file tạm `${lockFile}.tmp.${process.pid}.${Date.now()}` trước khi `fs.renameSync`. Đã kiểm chứng qua Adversarial Timeline bên dưới. |
| **S4: Cross-Task State Flow (Soi Code Thực Tế)** | `PASS` | `[SRC]`, `[CMD]` | Đã đọc trực tiếp toàn bộ 22 file `[MODIFY]` bằng `read_file`. Kiểm tra hợp đồng giao tiếp giữa `.agents/scripts/code-freeze-guard.js:12-17,705-735,798-835`, `.agents/scripts/plan-approval.js:232-368,454-768`, và `.agents/scripts/review-run.js:4-11,90-151`. Các hàm export mà `review-run.js` sử dụng (`parsePlanHeader`, `extractTechnicalPlan`, `lastNonBlankLine`, `setHeaderStatus`, `normalizeLineEndings`, `stripBom`) giữ nguyên chữ ký. Kết quả `node .agents/scripts/plan-approval.js status` xác nhận 13 plan cũ đều ở trạng thái đóng (`Completed`/`Superseded`), không gây xung đột khóa. |
| **S5: Security & Privacy Boundary** | `PASS` | `[SRC]`, `[ADV]`, `[URL]` | Kiểm chứng 4 lớp bảo vệ mới trên `[SRC]` `.agents/scripts/code-freeze-guard.js`: (1) Layer 2 `.md`-only guard tại dòng `837-839` chặn ghi `.js`/`.ps1`/`.bat` vào `docs/`; (2) Read Guard tại `decide()` chặn đọc `isSecretPath(relative)` (`local.properties`, `keystore*`, `*.jks`, `.env*`, `.git/`) và đường dẫn ngoài repo; (3) Đồng bộ bí danh `write_file`/`edit_file`/`Edit`/`MultiEdit` ngăn vượt khóa `APPROVED_PLAN_LOCKED` và `assessTestIntegrity`; (4) Giữ nguyên sandbox `--disallowedTools Bash PowerShell Write` trong `run-claude.ps1` và `claude-reviewer-settings.json:26-44`. |
| **S6: External Resilience** | `PASS` | `[SRC]`, `[CMD]` | Đối chiếu `[SRC]` `app/src/main/java/com/personal/apptruyen/data/remote/SsTruyenScraper.kt:30` (`sstruyen.pics`) và `WikidichScraper.kt:36` (`https://wikicv.org`): việc bổ sung 4 host `sstruyen.pics`, `api.sstruyen.pics`, `file.sstruyen.pics`, `wikicv.org` vào `SOURCE_HOSTS` (`code-freeze-guard.js:92-101`), `claude-reviewer-settings.json:8-25`, `claude-reviewer-prompt.md:10`, và `allowlist.md:30` giúp đồng bộ danh sách nguồn truyện đang hoạt động với bài kiểm thử `H7`, `H7b`, `H8`. |
| **Domain: Open Inquiry (AppTruyen)** | `PASS` | `[SRC]` | Đối chiếu `[SRC]` `.agents/rules/00-core.md`, `01-coding.md`, `02-quality.md`, `03-ui.md`, `04-scraping.md`, `05-plan-review.md`, `06-docs.md`: Ma trận bảo toàn quy chuẩn tại Mục 5.2 giữ đầy đủ các quy tắc nghiệp vụ của AppTruyen (UI tiếng Việt, Clean Architecture + MVVM/MVI, Room 3, `BaseScraper` + `ScraperException` + `ScraperCircuitBreaker`, và ngưỡng kích thước file) trong `00-core.md` (`always_on`) khi chuyển `01-coding.md` và `03-ui.md` sang `trigger: glob`. |

#### 4. Claim Verification

- **Xác minh đối chiếu từng file `[MODIFY]` với codebase thật (`[SRC]`)**:
  - `.agents/agents/planner/agent.md:1-17`, `.agents/agents/reviewer/agent.md:1-17`, `.agents/agents/qa/agent.md:1-16`: Cả 3 file hiện đang khai báo `model: gemini-4-argon` hoặc `model: claude-4.6-opus` kèm trường `role:`. Kế hoạch chuyển sang `model: pro` và bỏ `role:` khớp với đặc tả Subagents và tài liệu nội bộ `C:\Users\wyemh\.gemini\antigravity\builtin\skills\agy-customizations\SKILL.md:19-30`.
  - `.agents/rules/01-coding.md:1-4,102`: Hiện tại dùng `trigger: always_on` và dòng `102` (kế hoạch ghi dòng 93 do lệch số dòng trống) còn liệt kê `(Claude Opus 4.6 / 5.5, Gemini 4 Argon, Gemini 3.1 Pro)`.
  - `.agents/rules/00-core.md` (`4,008` ký tự), `02-quality.md` (`4,904` ký tự), `05-plan-review.md` (`6,829` ký tự), `06-docs.md` (`2,095` ký tự): Tổng 4 file `always_on` hiện tại là `17,836` ký tự, khớp với nhận định tại Rủi ro `R4` và Bước 3.5 của kế hoạch về việc cần biên tập gọn `05-plan-review.md` kết hợp kiểm tra tự động qua test `H11` (`< 18,000` ký tự, thấp hơn giới hạn cứng `24,000` bytes/file trong `agy-customizations/docs/rules.md:31-36`).
  - `.agents/workflows/debug.md:20`: Hiện đang viết `curl.exe -sL <url> | Select-String "<selector>"` (thiếu cờ `-q` ở vị trí đầu và dùng `-L`), vốn bị `.agents/scripts/code-freeze-guard.js:598,609` chặn (`test C5(b),(c)` tại `code-freeze-guard.test.js:223-228`). Việc sửa thành `curl.exe -q -s <url>` khớp với `allowlist.md:27-31`.
  - `.agents/scripts/code-freeze-guard.js:92-101,705,837-839` & `.agents/scripts/plan-approval.js:232-368,461,594`: Xác nhận `SOURCE_HOSTS` hiện chỉ có 8 host; `extractInput` và `normalizeToolArgs` hiện chỉ xử lý 3 tên công cụ cũ (`write_to_file`, `replace_file_content`, `multi_replace_file_content`); nhánh `UNFROZEN_PREFIXES` (`docs/`) tại dòng `837-839` hiện trả về `{ decision: 'allow' }` mà chưa kiểm tra phần mở rộng `.md`.
- **Xác minh tài liệu trực tuyến (`[URL]`)**:
  - Đặc tả Claude Code `PreToolUse` hook output (`[URL]` `https://code.claude.com/docs/en/hooks`): Khi trả quyết định từ hook `PreToolUse`, JSON xuất ra `stdout` dùng cấu trúc `{ hookSpecificOutput: { hookEventName: 'PreToolUse', permissionDecision: 'allow' | 'deny' | 'ask' | 'defer', permissionDecisionReason: '...' } }`. Hàm `formatHookOutput(result, rawPayload)` tại Mục 5.2 của kế hoạch tuân thủ đúng cấu trúc này và giữ nguyên định dạng `{ decision, reason }` cho Antigravity (`agy-customizations/docs/hooks.md:188-205`).
  - Đặc tả Node.js Test Runner (`[URL]` `https://nodejs.org/api/test.html`): `node:test` và `node:assert/strict` được tích hợp sẵn trong runtime Node.js và thực thi qua `node --test`, khớp với các bài test hiện hữu trong `.agents/scripts/*.test.js`.

#### 5. Adversarial Timelines

```text
Cơ chế 1 (S3 / Đồng thời & TOCTOU): Hai lời gọi công cụ cùng ghi vào một file kế hoạch trong docs/plans/ bằng bí danh mới (edit_file và replace_file_content)
T1: Agent phát lệnh 1 (edit_file, stepIdx = 10) nhắm vào docs/plans/2026-10-23-x-implementation-plan.md -> PreToolUse gọi decide() -> checkPendingWrite(pk) trả về pending: false -> recordPendingWrite(pk, 10) ghi nguyên tử vào .agents/.pending-plan-writes.json.
T2: Trước khi PostToolUse của stepIdx = 10 chạy, lệnh 2 (replace_file_content, stepIdx = 11) cùng nhắm vào file plan đó -> PreToolUse gọi decide() -> checkPendingWrite(pk) phát hiện bản ghi của stepIdx = 10 còn hiệu lực (< 120_000 ms).
T3: decide() lập tức trả về { decision: 'deny', reason: '[CONCURRENT_PLAN_WRITE] ...' } cho stepIdx = 11; sau đó PostToolUse của stepIdx = 10 gọi clearPendingWrite(10, REPO_ROOT) giải phóng khóa.
Kết cục: Không xảy ra ghi đè TOCTOU giữa hai bí danh công cụ khác nhau -> AN TOÀN.
```

```text
Cơ chế 2 (S5 / Tấn công vượt rào Code Freeze qua docs/ và bí danh công cụ):
T1: Khi repo đang ở trạng thái Code Freeze (planApproved === false), tác nhân gọi write_file tạo file thực thi docs/plans/helper.mjs hoặc docs/exploit.ps1 rồi định chạy qua run_command.
T2: PreToolUse chặn ngay ở bước ghi file: toRepoRelative trả về 'docs/plans/helper.mjs', khớp UNFROZEN_PREFIXES ('docs/'), nhưng kiểm tra Layer 2 (!pk.endsWith('.md')) kích hoạt.
T3: decide() trả về { decision: 'ask', reason: '⚠️ Code Freeze Guard: only .md files are allowed inside unfrozen documentation paths...' }, ngăn tạo script thực thi trong docs/.
Kết cục: Kịch bản thả file thực thi vào thư mục docs/ trong thời gian đóng băng bị chặn tại hook -> AN TOÀN.
```

```text
Cơ chế 3 (S5 / Thất bại giữa chừng & Vòng đời tiến trình khi Claude Code CLI Reviewer chạy):
T1: Orchestrator chạy run-claude.ps1 -> review-run.js prepare chụp snapshot before.md và thu thập evidence.md.
T2: Tiến trình claude.exe chạy với --permission-mode dontAsk và dùng tool Edit để ghi nhận xét kèm dòng thẻ phê duyệt vào cuối file plan; hoặc claude.exe gặp lỗi hết hạn mức (exit 42).
T3: Do kế hoạch KHÔNG gắn code-freeze-guard.js chặn Edit($relPlan) trong claude-reviewer-settings.json (Rủi ro R1), claude.exe không bị từ chối bởi lỗi thiếu stepIdx của Antigravity; khi claude.exe kết thúc, review-run.js finalize đối chiếu verifyClaudeAppend(beforeContent, afterContent) và tự khôi phục từ before.md nếu có sửa đổi trái phép hoặc lỗi quota.
Kết cục: Bảo toàn tính nguyên vẹn của file plan trong cả luồng thành công lẫn luồng lỗi/ngắt giữa chừng -> AN TOÀN.
```

#### 6. Commands Executed

1. `node --test .agents/scripts/code-freeze-guard.test.js; node --test .agents/scripts/plan-approval.test.js; node --test .agents/scripts/review-run.test.js; node .agents/scripts/plan-approval.js status` — **Exit code: `0`** (`code-freeze-guard.test.js`: 31/31 pass; `plan-approval.test.js`: 12/12 pass; `review-run.test.js`: 10/10 pass; `plan-approval.js status`: `approved: false`, `blockingPlans`: `2026-10-23-adopt-vikini-governance-v2-implementation-plan.md`).
2. `.\gradlew.bat compileDebugKotlin; git status --short` — **Exit code: `0`** (`BUILD SUCCESSFUL in 24s`, `8 actionable tasks: 2 executed, 6 up-to-date`).

#### 7. Các Tấn Công Đã Thử (Attempted Attack Vectors)

1. **Vector 1 — Thử làm gãy luồng cấp quyền của Claude Code CLI Reviewer trong chế độ `--permission-mode dontAsk`**:
   - **Giả thuyết tấn công**: Nếu gắn `code-freeze-guard.js` vào `claude-reviewer-settings.json` cho công cụ `Edit`, khi Claude CLI Reviewer thêm thẻ phê duyệt vào cuối file plan, `detectApprovalTransition` (`.agents/scripts/plan-approval.js:658-660`) trả về `APPROVAL_TAG_CHANGE` và `code-freeze-guard.js:823-827` trả về `decision: 'ask'` (hoặc `[STEP_IDX_MISSING]` do payload Claude CLI không có `stepIdx`). Khi qua `formatHookOutput` với `permission_mode === 'dontAsk'`, `ask` bị chuyển thành `permissionDecision: 'deny'`, làm tê liệt kênh Primary Reviewer.
   - **Kiểm chứng trên thiết kế & code thật (`[SRC]`)**: Kế hoạch đã nhận diện đúng điểm này tại Mục 3.1.2 (dòng 109–112) và Rủi ro `R1` (dòng 386): giữ nguyên kiến trúc sandbox của `.agents/scripts/run-claude.ps1:136-145` kết hợp hậu kiểm `verifyClaudeAppend` trong `.agents/scripts/review-run.js:90-151`, không gắn hook chặn `Edit($relPlan)` vào `claude-reviewer-settings.json`. Thiết kế không bị vỡ.
2. **Vector 2 — Thử dùng công cụ đọc file (`read_file` / `view_file`) trong `decide()` khi đang Code Freeze**:
   - **Giả thuyết tấn công**: Trong `code-freeze-guard.js:780-877` hiện tại, mọi payload có `input.targetFile` đều đi qua các cổng kiểm tra ghi file (`isGovernanceFile`, `isTestFile`, `planApproved()`). Nếu thêm `read_file` vào `decide()` mà đặt nhánh kiểm tra `READ_TOOLS` sau `isGovernanceFile` hoặc sau `isTestFile`, mọi thao tác đọc `.agents/**` hoặc `app/src/test/**` sẽ bị hỏi xác nhận (`ask`). Ngược lại, nếu đặt `READ_TOOLS` trước `isSecretPath(relative)`, tác nhân có thể đọc trộm `local.properties` hoặc `keystore.properties`.
   - **Kiểm chứng trên thiết kế & code thật (`[SRC]`)**: Trong Mục 5.2 (dòng 231–240) của kế hoạch, nhánh `if (input.toolName && READ_TOOLS.has(input.toolName))` được đặt ngay đầu khối `if (input.targetFile)`, kiểm tra `relative === null` (chỉ cho phép `isAntigravityArtifact`) và chặn `relative.includes('.gemini/') || relative.startsWith('.git/') || isSecretPath(relative)` trước khi trả về `{ decision: 'allow' }`. Nhờ đó, thao tác đọc file nguồn và `.agents/**` được cho phép, trong khi thao tác đọc `local.properties`/`keystore*`/`.git/` vẫn bị chặn bởi `isSecretPath`.
3. **Vector 3 — Thử làm gãy bài kiểm thử đồng bộ `H7` và `H7b` khi thêm 4 host nguồn truyện mới**:
   - **Giả thuyết tấn công**: Trong `.agents/scripts/code-freeze-guard.test.js:531-579`, bài test `H7` đếm số ký tự `"` trong `claude-reviewer-prompt.md` phải bằng `0`, còn `H7b` so khớp mảng `WebFetch(domain:...)` trong `claude-reviewer-settings.json` với `[...SOURCE_HOSTS, ...9 doc hosts]`. Nếu thêm host vào `SOURCE_HOSTS` mà lệch thứ tự/tập hợp ở `claude-reviewer-settings.json` hoặc dùng dấu `"` trong `claude-reviewer-prompt.md`, bộ test sẽ fail.
   - **Kiểm chứng trên thiết kế & code thật (`[SRC]`, `[CMD]`)**: Kế hoạch tại Bước 4.2, 4.4 và Bước 5.2 cập nhật đồng bộ cả 4 nơi (`code-freeze-guard.js`, `claude-reviewer-settings.json`, `claude-reviewer-prompt.md` không dùng dấu `"`, và `allowlist.md`), nâng tổng số domain `WebFetch` từ 17 lên 21 (`12 SOURCE_HOSTS + 9 doc hosts`).

**Kết luận**: APPROVED · BLOCKER 0 · MAJOR 0

[PLAN_APPROVED]
