import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import {
    parsePlanHeader,
    extractTechnicalPlan,
    lastNonBlankLine,
    setHeaderStatus,
    normalizeLineEndings,
    stripBom
} from './plan-approval.js';

export const CLI_ERROR_PATTERNS = {
    AUTH: [
        /Failed to authenticate/i,
        /Invalid API key/i,
        /API Error:\s*401/i,
        /authentication_failed/i,
        /Please run \/login/i
    ],
    QUOTA: [
        /usage limit reached/i,
        /rate_limit_error/i,
        /API Error:\s*(429|529)/i,
        /hit your (session|usage|weekly|daily) limit/i,
        /limit\s*·\s*resets/i,
        /session limit/i,
        /usage limit/i,
        /rate limit|exceeded.*limit|\b429\b/i
    ]
};

export const EVIDENCE_COMMANDS = Object.freeze([
    { id: 'git-version', file: 'git', args: ['--version'], timeoutMs: 120_000, gradle: false },
    { id: 'node-version', file: 'node', args: ['--version'], timeoutMs: 120_000, gradle: false },
    { id: 'git-status', file: 'git', args: ['status', '--short'], timeoutMs: 120_000, gradle: false },
    { id: 'git-diff', file: 'git', args: ['diff', '--stat'], timeoutMs: 120_000, gradle: false },
    { id: 'git-log', file: 'git', args: ['log', '--oneline', '-n', '20'], timeoutMs: 120_000, gradle: false },
    { id: 'test-guard', file: 'node', args: ['--test', '.agents/scripts/code-freeze-guard.test.js'], timeoutMs: 120_000, gradle: false, checkFile: '.agents/scripts/code-freeze-guard.test.js' },
    { id: 'test-approval', file: 'node', args: ['--test', '.agents/scripts/plan-approval.test.js'], timeoutMs: 120_000, gradle: false, checkFile: '.agents/scripts/plan-approval.test.js' },
    { id: 'test-review', file: 'node', args: ['--test', '.agents/scripts/review-run.test.js'], timeoutMs: 120_000, gradle: false, checkFile: '.agents/scripts/review-run.test.js' },
    { id: 'gradle-compile', file: 'cmd.exe', args: ['/d', '/c', 'gradlew.bat', 'compileDebugKotlin'], timeoutMs: 1_200_000, gradle: true },
    { id: 'gradle-ktlint', file: 'cmd.exe', args: ['/d', '/c', 'gradlew.bat', 'ktlintCheck'], timeoutMs: 1_200_000, gradle: true },
    { id: 'gradle-test', file: 'cmd.exe', args: ['/d', '/c', 'gradlew.bat', 'testDebugUnitTest'], timeoutMs: 1_200_000, gradle: true }
]);

export function readTextAuto(buffer) {
    if (!Buffer.isBuffer(buffer)) {
        buffer = Buffer.from(buffer);
    }
    if (buffer.length >= 3 && buffer[0] === 0xEF && buffer[1] === 0xBB && buffer[2] === 0xBF) {
        return buffer.slice(3).toString('utf8');
    }
    if (buffer.length >= 2 && buffer[0] === 0xFF && buffer[1] === 0xFE) {
        return buffer.slice(2).toString('utf16le');
    }
    if (buffer.length >= 2 && buffer[0] === 0xFE && buffer[1] === 0xFF) {
        const swapped = Buffer.alloc(buffer.length - 2);
        for (let i = 2; i < buffer.length; i += 2) {
            swapped[i - 2] = buffer[i + 1] ?? 0;
            swapped[i - 1] = buffer[i];
        }
        return swapped.toString('utf16le');
    }
    return buffer.toString('utf8');
}

export function classifyCliOutput(output, exitCode) {
    if (exitCode === 0) {
        return 'OK';
    }

    const text = typeof output === 'string' ? output : readTextAuto(output);

    for (const pat of CLI_ERROR_PATTERNS.AUTH) {
        if (pat.test(text)) {
            return 'AUTH';
        }
    }

    for (const pat of CLI_ERROR_PATTERNS.QUOTA) {
        if (pat.test(text)) {
            return 'QUOTA';
        }
    }

    return 'UNKNOWN';
}

export function verifyClaudeAppend(beforeRaw, afterRaw) {
    const before = normalizeLineEndings(stripBom(beforeRaw));
    const after = normalizeLineEndings(stripBom(afterRaw));

    const beforeTech = extractTechnicalPlan(before);
    const afterTech = extractTechnicalPlan(after);

    if (!beforeTech || !afterTech || beforeTech !== afterTech) {
        return { result: 'TAMPERED', reason: 'Technical Plan bị sửa đổi hoặc thiếu' };
    }

    const beforeHeader = parsePlanHeader(before);
    const afterHeader = parsePlanHeader(after);

    if (beforeHeader.status !== afterHeader.status) {
        return { result: 'TAMPERED', reason: 'Header block bị sửa đổi' };
    }

    const beforeTrim = before.trimEnd();
    if (!after.startsWith(beforeTrim)) {
        return { result: 'TAMPERED', reason: 'Nội dung cũ không phải là tiền tố bất biến của nội dung mới' };
    }

    const lastBefore = lastNonBlankLine(before);
    if (lastBefore === '[PLAN_APPROVED]') {
        return { result: 'TAMPERED', reason: 'Before đã chứa thẻ phê duyệt' };
    }

    const appendPart = after.slice(beforeTrim.length);
    const auditRunHeadingRegex = /^###\s*Audit Run\s*(\d+)/gm;
    const matches = [...appendPart.matchAll(auditRunHeadingRegex)];

    if (matches.length === 0) {
        return { result: 'INCONCLUSIVE', reason: 'Không tìm thấy Audit Run mới trong phần append' };
    }

    if (matches.length > 1) {
        return { result: 'TAMPERED', reason: 'Có nhiều hơn một Audit Run mới trong phần append' };
    }

    const newAuditRunNum = Number(matches[0][1]);

    const tagMatches = [...appendPart.matchAll(/\[PLAN_APPROVED\]/g)];
    const afterLastLine = lastNonBlankLine(after);
    const tagAtEnd = (afterLastLine === '[PLAN_APPROVED]');

    if (tagMatches.length > 1 || (tagMatches.length === 1 && !tagAtEnd)) {
        return { result: 'TAMPERED', reason: 'Thẻ phê duyệt xuất hiện sai vị trí hoặc bị lặp lại' };
    }

    const hasChangesRequested = /\[CHANGES_REQUESTED\]/.test(appendPart);

    if (hasChangesRequested && tagAtEnd) {
        return { result: 'TAMPERED', reason: 'Append chứa đồng thời [CHANGES_REQUESTED] và thẻ [PLAN_APPROVED]' };
    }

    if (hasChangesRequested || !tagAtEnd) {
        return { result: 'CHANGES_REQUESTED', auditRun: newAuditRunNum };
    }

    return { result: 'APPROVED', auditRun: newAuditRunNum };
}

export function decideRunOutcome({ snapshotExists, claudeExit, classification, verify }) {
    if (!snapshotExists) {
        return { restore: false, setApproved: false, exitCode: 46, tag: '[SNAPSHOT_MISSING]' };
    }

    if (claudeExit !== 0) {
        if (classification === 'QUOTA') {
            return { restore: true, setApproved: false, exitCode: 42, tag: '[CLAUDE_PRO_QUOTA_EXCEEDED]' };
        }
        if (classification === 'AUTH') {
            return { restore: true, setApproved: false, exitCode: 41, tag: '[CLAUDE_NOT_AUTHENTICATED]' };
        }
        return { restore: true, setApproved: false, exitCode: 45, tag: '[CLAUDE_CLI_ERROR]' };
    }

    if (!verify) {
        return { restore: true, setApproved: false, exitCode: 44, tag: '[REVIEW_INCONCLUSIVE]' };
    }

    if (verify.result === 'APPROVED') {
        return { restore: false, setApproved: true, exitCode: 0, tag: '[REVIEW_RESULT] APPROVED' };
    }

    if (verify.result === 'CHANGES_REQUESTED') {
        return { restore: false, setApproved: false, exitCode: 0, tag: '[REVIEW_RESULT] CHANGES_REQUESTED' };
    }

    if (verify.result === 'TAMPERED') {
        return { restore: true, setApproved: false, exitCode: 43, tag: '[APPROVAL_ATTESTATION_FAILED]' };
    }

    return { restore: true, setApproved: false, exitCode: 44, tag: '[REVIEW_INCONCLUSIVE]' };
}

export function makeRunDir(repoRoot, planRel, now = new Date()) {
    const base = path.basename(planRel, path.extname(planRel));
    const pad = (n, len = 2) => String(n).padStart(len, '0');
    const ts = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}${pad(now.getMilliseconds(), 3)}`;
    return path.join(repoRoot, 'build', 'claude-review', `${base}-${ts}`);
}

export function findStaleRuns(repoRoot, planRel) {
    const base = path.basename(planRel, path.extname(planRel));
    const parentDir = path.join(repoRoot, 'build', 'claude-review');
    if (!fs.existsSync(parentDir)) return [];

    const dirs = fs.readdirSync(parentDir);
    const stale = [];

    for (const d of dirs) {
        if (d.startsWith(`${base}-`)) {
            const fullDir = path.join(parentDir, d);
            try {
                if (fs.statSync(fullDir).isDirectory()) {
                    const finalizedJson = path.join(fullDir, 'finalized.json');
                    if (!fs.existsSync(finalizedJson)) {
                        stale.push(fullDir);
                    }
                }
            } catch {}
        }
    }

    return stale;
}

export function collectEvidence({ repoRoot, withGradle = true, exec = execFileSync, exists = fs.existsSync }) {
    const sections = [];
    const cmds = EVIDENCE_COMMANDS.filter((c) => withGradle || !c.gradle);

    for (const cmd of cmds) {
        const cmdStr = `${cmd.file} ${cmd.args.join(' ')}`;
        if (cmd.checkFile && !exists(path.join(repoRoot, cmd.checkFile))) {
            sections.push(`### [CMD] ${cmdStr}\nExit: SKIPPED (file không tồn tại)\n`);
            continue;
        }

        let exitCode = 0;
        let output = '';

        try {
            const res = exec(cmd.file, cmd.args, {
                cwd: repoRoot,
                shell: false,
                encoding: 'utf8',
                timeout: cmd.timeoutMs,
                maxBuffer: 32 * 1024 * 1024,
                windowsHide: true
            });
            output = String(res ?? '');
        } catch (err) {
            exitCode = err.status ?? (err.code ? `ERROR ${err.code}` : 1);
            output = `${err.stdout ?? ''}\n${err.stderr ?? ''}`;
        }

        const lines = output.replace(/\r\n/g, '\n').split('\n');
        const tailLines = lines.slice(-200).join('\n').trim();

        sections.push(`### [CMD] ${cmdStr}\nExit: ${exitCode}\n\`\`\`\n${tailLines}\n\`\`\`\n`);
    }

    return sections.join('\n');
}

export function main(argv = process.argv.slice(2)) {
    const action = argv[0];
    const repoRoot = path.resolve(path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')), '../..');

    if (action === 'prepare') {
        let planRel = null;
        let withGradle = true;

        for (let i = 1; i < argv.length; i++) {
            if (argv[i] === '--plan' && i + 1 < argv.length) {
                planRel = argv[i + 1].replace(/\\/g, '/');
                i++;
            } else if (argv[i] === '--no-gradle') {
                withGradle = false;
            } else {
                process.stderr.write(`[BAD_ARGS] Tham số không hợp lệ cho prepare: ${argv[i]}\n`);
                process.exit(1);
            }
        }

        if (!planRel) {
            process.stderr.write('[BAD_ARGS] Thiếu --plan\n');
            process.exit(1);
        }

        const fullPlanPath = path.join(repoRoot, planRel);
        if (!fs.existsSync(fullPlanPath)) {
            process.stderr.write(`[PLAN_NOT_FOUND] Không tìm thấy file: ${planRel}\n`);
            process.exit(1);
        }

        const content = fs.readFileSync(fullPlanPath, 'utf8');
        const header = parsePlanHeader(content);

        if (header.status && (header.status.toLowerCase() === 'completed' || header.status.toLowerCase() === 'superseded')) {
            process.stderr.write(`[PLAN_CLOSED] Kế hoạch đã đóng (${header.status}), không thể thẩm định\n`);
            process.exit(1);
        }

        if (!header.status || header.status.toLowerCase() !== 'ready for review') {
            process.stderr.write(`[PLAN_NOT_READY] Kế hoạch không ở trạng thái Ready for Review (${header.status})\n`);
            process.exit(1);
        }

        const staleRuns = findStaleRuns(repoRoot, planRel);
        if (staleRuns.length > 0) {
            process.stderr.write(`[STALE_RUN] Phát hiện lượt review trước chưa dọn dẹp: ${staleRuns[0]}\n`);
            process.exit(47);
        }

        const lastLine = lastNonBlankLine(content);
        if (lastLine === '[PLAN_APPROVED]') {
            process.stderr.write('[STALE_APPROVAL_TAG] Dòng cuối đã là [PLAN_APPROVED] khi chưa duyệt\n');
            process.exit(47);
        }

        const runDir = makeRunDir(repoRoot, planRel);
        fs.mkdirSync(runDir, { recursive: true });

        try {
            fs.writeFileSync(path.join(runDir, 'before.md'), content, 'utf8');

            const templatePath = path.join(repoRoot, '.agents', 'scripts', 'claude-reviewer-prompt.md');
            let promptText = '';
            if (fs.existsSync(templatePath)) {
                promptText = fs.readFileSync(templatePath, 'utf8').replace('{{PLAN_FILE}}', planRel);
            }
            fs.writeFileSync(path.join(runDir, 'prompt.md'), promptText, 'utf8');

            const evidenceText = collectEvidence({ repoRoot, withGradle });
            fs.writeFileSync(path.join(runDir, 'evidence.md'), evidenceText, 'utf8');

            process.stdout.write(`${runDir}\n`);
            process.exit(0);
        } catch (err) {
            fs.writeFileSync(path.join(runDir, 'finalized.json'), JSON.stringify({ outcome: 'PREPARE_FAILED', error: String(err) }, null, 2), 'utf8');
            process.stderr.write(`[PREPARE_FAILED] Lỗi khởi tạo lượt review: ${err.message}\n`);
            process.exit(1);
        }
    }

    if (action === 'finalize') {
        let planRel = null;
        let runDir = null;
        let cliExit = 0;
        let model = '';

        for (let i = 1; i < argv.length; i++) {
            if (argv[i] === '--plan' && i + 1 < argv.length) {
                planRel = argv[i + 1].replace(/\\/g, '/');
                i++;
            } else if (argv[i] === '--run-dir' && i + 1 < argv.length) {
                runDir = argv[i + 1];
                i++;
            } else if (argv[i] === '--exit' && i + 1 < argv.length) {
                cliExit = Number(argv[i + 1]);
                i++;
            } else if (argv[i] === '--model' && i + 1 < argv.length) {
                model = argv[i + 1];
                i++;
            }
        }

        if (!planRel || !runDir) {
            process.stderr.write('[BAD_ARGS] finalize yêu cầu --plan và --run-dir\n');
            process.exit(1);
        }

        const snapshotPath = path.join(runDir, 'before.md');
        const snapshotExists = fs.existsSync(snapshotPath);
        const fullPlanPath = path.join(repoRoot, planRel);

        let cliOutput = '';
        const outPath = path.join(runDir, 'claude-output.txt');
        if (fs.existsSync(outPath)) {
            cliOutput = readTextAuto(fs.readFileSync(outPath));
        }

        const classification = classifyCliOutput(cliOutput, cliExit);
        let verify = null;

        if (cliExit === 0 && snapshotExists && fs.existsSync(fullPlanPath)) {
            const beforeContent = fs.readFileSync(snapshotPath, 'utf8');
            const afterContent = fs.readFileSync(fullPlanPath, 'utf8');
            verify = verifyClaudeAppend(beforeContent, afterContent);
        }

        const outcome = decideRunOutcome({ snapshotExists, claudeExit: cliExit, classification, verify });

        if (outcome.restore && snapshotExists && fs.existsSync(fullPlanPath)) {
            fs.writeFileSync(fullPlanPath, fs.readFileSync(snapshotPath, 'utf8'), 'utf8');
        }

        if (outcome.setApproved && fs.existsSync(fullPlanPath)) {
            const cur = fs.readFileSync(fullPlanPath, 'utf8');
            const updated = setHeaderStatus(cur, 'Approved');
            const tmpPath = `${fullPlanPath}.tmp.${Date.now()}`;
            fs.writeFileSync(tmpPath, updated, 'utf8');
            fs.renameSync(tmpPath, fullPlanPath);
        }

        fs.writeFileSync(path.join(runDir, 'finalized.json'), JSON.stringify({
            outcome: outcome.tag,
            exitCode: outcome.exitCode,
            model,
            at: Date.now()
        }, null, 2), 'utf8');

        process.stdout.write(`${outcome.tag}\n`);
        process.exit(outcome.exitCode);
    }

    if (action === 'restore') {
        let planRel = null;
        let runDir = null;

        for (let i = 1; i < argv.length; i++) {
            if (argv[i] === '--plan' && i + 1 < argv.length) {
                planRel = argv[i + 1].replace(/\\/g, '/');
                i++;
            } else if (argv[i] === '--run-dir' && i + 1 < argv.length) {
                runDir = argv[i + 1];
                i++;
            }
        }

        if (!planRel || !runDir) {
            process.stderr.write('[BAD_ARGS] restore yêu cầu --plan và --run-dir\n');
            process.exit(1);
        }

        const snapshotPath = path.join(runDir, 'before.md');
        const fullPlanPath = path.join(repoRoot, planRel);

        if (fs.existsSync(snapshotPath) && fs.existsSync(fullPlanPath)) {
            fs.writeFileSync(fullPlanPath, fs.readFileSync(snapshotPath, 'utf8'), 'utf8');
        }

        fs.writeFileSync(path.join(runDir, 'finalized.json'), JSON.stringify({
            outcome: 'RESTORED',
            exitCode: 45,
            at: Date.now()
        }, null, 2), 'utf8');

        process.stdout.write('[RESTORED]\n');
        process.exit(0);
    }

    process.stderr.write(`[BAD_ARGS] Lệnh không hợp lệ: ${action || ''}\n`);
    process.exit(1);
}

if (process.argv[1] && import.meta.url.endsWith(process.argv[1].replace(/\\/g, '/'))) {
    main();
}
