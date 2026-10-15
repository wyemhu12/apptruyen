import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {
    classifyCliOutput,
    verifyClaudeAppend,
    decideRunOutcome,
    makeRunDir,
    readTextAuto,
    collectEvidence,
    EVIDENCE_COMMANDS
} from './review-run.js';

test('R1: Chuỗi lỗi hết quota thực tế', () => {
    const raw = "You've hit your session limit · resets 4:40am (Europe/Berlin)";
    assert.equal(classifyCliOutput(raw, 1), 'QUOTA');

    const outcome = decideRunOutcome({
        snapshotExists: true,
        claudeExit: 1,
        classification: 'QUOTA',
        verify: null
    });
    assert.equal(outcome.restore, true);
    assert.equal(outcome.exitCode, 42);
    assert.equal(outcome.tag, '[CLAUDE_PRO_QUOTA_EXCEEDED]');
});

test('R2: Nhắc quota trong nội dung review khi exit code = 0', () => {
    const raw = "You've hit your session limit · resets 4:40am (Europe/Berlin)";
    assert.equal(classifyCliOutput(raw, 0), 'OK');
});

test('R3: Các mẫu lỗi quota khác', () => {
    assert.equal(classifyCliOutput('usage limit reached', 1), 'QUOTA');
    assert.equal(classifyCliOutput('API Error: 429', 1), 'QUOTA');
    assert.equal(classifyCliOutput('rate_limit_error', 1), 'QUOTA');
});

test('R4: Lỗi xác thực / auth', () => {
    assert.equal(classifyCliOutput('Failed to authenticate', 1), 'AUTH');
    assert.equal(classifyCliOutput('Invalid API key', 1), 'AUTH');
    assert.equal(classifyCliOutput('API Error: 401', 1), 'AUTH');

    const outcome = decideRunOutcome({
        snapshotExists: true,
        claudeExit: 1,
        classification: 'AUTH',
        verify: null
    });
    assert.equal(outcome.exitCode, 41);
    assert.equal(outcome.tag, '[CLAUDE_NOT_AUTHENTICATED]');
});

test('R5: Lỗi CLI không phân loại được', () => {
    assert.equal(classifyCliOutput('Segmentation fault', 1), 'UNKNOWN');
    const outcome = decideRunOutcome({
        snapshotExists: true,
        claudeExit: 1,
        classification: 'UNKNOWN',
        verify: null
    });
    assert.equal(outcome.exitCode, 45);
    assert.equal(outcome.restore, true);
});

test('R6 & R9: verifyClaudeAppend cho kết quả APPROVED và TAMPERED', () => {
    const before = '# Kế hoạch\n\n> **Trạng thái**: Ready for Review\n\n## Technical Plan\nNội dung\n\n## Audit History\n';
    const afterApproved = before + '\n### Audit Run 1\n\nĐánh giá đạt chuẩn\n\n[PLAN_APPROVED]\n';

    const resApproved = verifyClaudeAppend(before, afterApproved);
    assert.equal(resApproved.result, 'APPROVED');
    assert.equal(resApproved.auditRun, 1);

    const outcomeApproved = decideRunOutcome({
        snapshotExists: true,
        claudeExit: 0,
        classification: 'OK',
        verify: resApproved
    });
    assert.equal(outcomeApproved.exitCode, 0);
    assert.equal(outcomeApproved.setApproved, true);

    // Tampered: sửa Technical Plan
    const afterTampered = '# Kế hoạch\n\n> **Trạng thái**: Ready for Review\n\n## Technical Plan\nNội dung đã sửa\n\n## Audit History\n\n### Audit Run 1\n\n[PLAN_APPROVED]\n';
    const resTampered = verifyClaudeAppend(before, afterTampered);
    assert.equal(resTampered.result, 'TAMPERED');

    const outcomeTampered = decideRunOutcome({
        snapshotExists: true,
        claudeExit: 0,
        classification: 'OK',
        verify: resTampered
    });
    assert.equal(outcomeTampered.exitCode, 43);
    assert.equal(outcomeTampered.restore, true);
});

test('R8: INCONCLUSIVE khi không có Audit Run mới', () => {
    const before = '# Kế hoạch\n\n> **Trạng thái**: Ready for Review\n\n## Technical Plan\nNội dung\n\n## Audit History\n';
    const afterSame = before + '   \n';
    const res = verifyClaudeAppend(before, afterSame);
    assert.equal(res.result, 'INCONCLUSIVE');

    const outcome = decideRunOutcome({
        snapshotExists: true,
        claudeExit: 0,
        classification: 'OK',
        verify: res
    });
    assert.equal(outcome.exitCode, 44);
    assert.equal(outcome.restore, true);
});

test('R10: makeRunDir sinh thư mục riêng theo timestamp', () => {
    const d1 = makeRunDir('d:/AppTruyen', 'docs/plans/test.md', new Date('2026-10-12T10:00:00Z'));
    const d2 = makeRunDir('d:/AppTruyen', 'docs/plans/test.md', new Date('2026-10-12T10:00:01Z'));
    assert.notEqual(d1, d2);
    assert.ok(d1.includes('build'));
});

test('R14: readTextAuto xử lý đúng các loại encoding và BOM', () => {
    const utf8Bom = Buffer.concat([Buffer.from([0xEF, 0xBB, 0xBF]), Buffer.from('hello utf8')]);
    assert.equal(readTextAuto(utf8Bom), 'hello utf8');

    const utf16leBom = Buffer.concat([Buffer.from([0xFF, 0xFE]), Buffer.from('hello utf16', 'utf16le')]);
    assert.equal(readTextAuto(utf16leBom), 'hello utf16');

    const plainUtf8 = Buffer.from('plain utf8');
    assert.equal(readTextAuto(plainUtf8), 'plain utf8');
});

test('E1 - E5: Danh sách lệnh evidence cố định và không dừng chuỗi khi lỗi', () => {
    const calls = [];
    const fakeExec = (file, args) => {
        calls.push([file, ...args]);
        if (file === 'git' && args[0] === 'status') {
            const err = new Error('simulated error');
            err.status = 1;
            err.stdout = 'simulated stdout';
            err.stderr = 'simulated stderr';
            throw err;
        }
        return 'success output';
    };

    const evidence = collectEvidence({
        repoRoot: 'd:/AppTruyen',
        withGradle: false,
        exec: fakeExec,
        exists: () => true
    });

    assert.ok(evidence.includes('git --version'));
    assert.ok(evidence.includes('Exit: 1'));
    assert.ok(evidence.includes('simulated stdout'));
    assert.ok(calls.length >= 7);
});
