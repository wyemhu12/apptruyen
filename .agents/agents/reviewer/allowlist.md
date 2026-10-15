# ALLOWLIST Lệnh `run_command` Cho @reviewer (AppTruyen)

> Tài liệu tham chiếu bắt buộc của `@reviewer`, `@qa` và Claude CLI reviewer. Đọc bằng `view_file` ở Bước 0 trước khi chạy bất kỳ lệnh nào. Code Freeze Guard (`.agents/scripts/code-freeze-guard.js`) cưỡng chế cùng tập lệnh này ở tầng hook.

## Nhóm Lệnh Được Phép

`run_command` dùng để **chứng minh** kết luận, KHÔNG dùng để sửa code hay dựng môi trường. Lệnh nào đã chạy đều phải được liệt kê trong mục `Commands Executed`. Shell là **PowerShell 5.1**: nối lệnh bằng `;`, KHÔNG dùng `&&`.

**Nhóm A — Verification Suite (Gradle, chỉ ghi vào `build/`)**

- `.\gradlew.bat compileDebugKotlin`: kiểm tra biên dịch (tương đương type-check)
- `.\gradlew.bat compileDebugUnitTestKotlin`
- `.\gradlew.bat testDebugUnitTest`, `.\gradlew.bat testDebugUnitTest --tests "<package.Class>"` (có thể thêm `--info`, `--stacktrace`)
- `.\gradlew.bat ktlintCheck`: KHÔNG được chạy `ktlintFormat`
- `.\gradlew.bat lintDebug`, `.\gradlew.bat assembleDebug`
- Đọc output dài bằng hậu tố chuẩn của dự án:
  `... 2>&1 | Tee-Object -FilePath build\<tên>.txt` hoặc `... 2>&1 | Select-String "BUILD|FAIL|tests completed"`

**Nhóm B — Wiring & Dependency Probes (không ghi file)**

- `.\gradlew.bat app:dependencies --configuration debugRuntimeClasspath`
- `.\gradlew.bat dependencyInsight --dependency <group:artifact> --configuration debugRuntimeClasspath`
- `.\gradlew.bat tasks`

**Nhóm C — Source & Runtime Probes (read-only)**

- `curl.exe -q -s <url>` hoặc `curl.exe -q -sI --max-time N <url>`: tải HTML hoặc header của nguồn truyện để đối chiếu CSS selector, mã HTTP.
  * Tham số đầu tiên BẮT BUỘC là `-q` (bỏ qua `.curlrc`).
  * Chỉ chấp nhận các cờ read-only: `-s`, `-S`, `-I`, `--compressed`, `--max-time N` (hoặc `-m N`), `-A "<UA>"` (hoặc `--user-agent "<UA>"`). User-Agent không chứa dấu `;`.
  * Đúng MỘT URL bare `https://` thuộc danh sách host nguồn: `truyencom.com`, `truyenfull.today`, `sstruyen.net`, `tangthuvien.org`, `lacatruyen.buzz`, `www.lacatruyen.buzz`, `lacatruyen.world`, `www.lacatruyen.world`.
  * CẤM `-L` (redirect), `-o/-O/--output`, `-d/--data`, `-X`, `-F`, `-c/-b` (cookie), `-u`, `-x` (proxy), `-K/--config`.
  * Mọi lệnh mạng khác (`curl` alias PowerShell, `wget`, `Invoke-WebRequest`, `iwr`, `irm`...) đều bị hook hỏi User xác nhận.
- `Get-Content`, `Get-ChildItem`, `Select-String -Path ... -Pattern "..."`, `Test-Path`, `Get-Item`, `Resolve-Path`: đọc file trong repo (trừ file secret). Đường dẫn chỉ nằm trong working tree, không chứa wildcard/provider/thiết bị Windows (`NUL`, `CON`, `AUX`, `PRN`, `COM1-9`, `LPT1-9`).

**Nhóm D — Git Read-Only**

- `git status`, `git diff`, `git log`, `git show`, `git blame`
- Cờ an toàn: `--stat`, `--shortstat`, `--oneline`, `--name-only`, `--cached`, `-p`, `--graph`, v.v.
- Mọi toán hạng đường dẫn/ref phải nằm trong repo làm việc, không chứa `:`, không trỏ ra ngoài working tree, không dùng `NUL` hay `/dev/null`.

**Nhóm E — Governance & Test Runner (Node.js)**

- `node --test .agents/scripts/code-freeze-guard.test.js`
- `node --test .agents/scripts/plan-approval.test.js`
- `node --test .agents/scripts/review-run.test.js`
- `node .agents/scripts/plan-approval.js status` (hoặc kèm `--plan docs/plans/...`)

## Ví Dụ Đồng Bộ (Dùng Cho Hook Test H8)

```text
# hook-allow-examples
curl.exe -q -s https://truyenfull.today/
curl.exe -q -sI --max-time 10 https://www.lacatruyen.buzz/
git status
git diff --stat
node --test .agents/scripts/code-freeze-guard.test.js
node .agents/scripts/plan-approval.js status
Get-Content docs\architecture.md
```

```text
# hook-ask-examples
curl -s https://truyenfull.today/
curl.exe -sL https://truyenfull.today/
curl.exe -q -s https://evil.example/
git diff C:/Windows/win.ini NUL
Get-Content local.properties
npm install
Remove-Item -Recurse build
```

## CẤM TUYỆT ĐỐI

- Lệnh có side-effect lên source, DB, thiết bị, dependencies: `.\gradlew.bat ktlintFormat|clean|installDebug|assembleRelease|publish`, `adb install|shell pm clear`, `npm install`, mọi lệnh `git` ghi (`add`, `commit`, `checkout`, `switch`, `reset`, `stash`, `push`...), `Remove-Item`, `Move-Item`, `Copy-Item`, `New-Item`, `Set-Content`, `Add-Content`, `Out-File`.
- Tham số Gradle ghi đè cấu hình: `--init-script`, `-P`/`-D` thay đổi build, `--write-locks`, `--refresh-dependencies`.
- Chuyển hướng output ra file ngoài hậu tố `Tee-Object -FilePath build\*.txt` (`>`, `>>`, `tee` tùy ý).
- Đọc hoặc in secrets: `keystore.properties`, `keystore/`, `*.jks`, `local.properties`, `.env*`, `Get-ChildItem env:`.
- Mọi lệnh nằm ngoài 5 nhóm trên. Nếu cần lệnh khác để xác minh thì ghi `UNVERIFIED` kèm lệnh đề xuất cho User tự chạy, KHÔNG tự chạy.
