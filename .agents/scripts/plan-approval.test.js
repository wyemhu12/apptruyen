import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {
    parsePlanHeader,
    lastNonBlankLine,
    extractTechnicalPlan,
    isUnlocking,
    isClosed,
    isBlocking,
    computeApprovalState,
    setHeaderStatus,
    normalizeToolArgs,
    resolveChunk,
    simulateWrite,
    detectApprovalTransition,
    summarizeFallback,
    checkPendingWrite,
    recordPendingWrite,
    clearPendingWrite,
    main
} from './plan-approval.js';

test('A1: Chuẩn hóa header và luồng mở khóa', () => {
    const raw = '# Kế hoạch\n\n> **Trạng thái**: Ready for Review\n> **Revision**: 1\n\n## Technical Plan\nNội dung\n\n## Audit History\n';
    const parsed = parsePlanHeader(raw);
    assert.equal(parsed.status, 'Ready for Review');
    assert.equal(isBlocking(raw).blocking, true);

    const withApproved = setHeaderStatus(raw, 'Approved') + '\n### Audit Run 1\n\n[PLAN_APPROVED]\n';
    assert.equal(isUnlocking(withApproved), true);
    assert.equal(isBlocking(withApproved).blocking, false);
});

test('A2: Trích xuất Technical Plan', () => {
    const raw = '# Kế hoạch\n\n> **Trạng thái**: Ready for Review\n\n## Technical Plan\nNội dung kỹ thuật\n\n## Audit History\n';
    assert.equal(extractTechnicalPlan(raw), 'Nội dung kỹ thuật');

    const edited = '# Kế hoạch\n\n> **Trạng thái**: Ready for Review\n\n## Technical Plan\nNội dung đã sửa\n\n## Audit History\n';
    assert.notEqual(extractTechnicalPlan(raw), extractTechnicalPlan(edited));
});

test('A4 & A8: Khóa nội dung plan đã duyệt và thẻ bị chia nhỏ', () => {
    const approvedPlan = '# Kế hoạch\n\n> **Trạng thái**: Approved\n\n## Technical Plan\nNội dung\n\n## Audit History\n\n[PLAN_APPROVED]\n';

    // Sửa thân plan đã duyệt -> APPROVED_PLAN_LOCKED
    const editBodyInput = normalizeToolArgs('replace_file_content', {
        TargetFile: 'docs/plans/2026-10-11-test-implementation-plan.md',
        TargetContent: 'Nội dung',
        ReplacementContent: 'Nội dung mới',
        StartLine: 6,
        EndLine: 7
    });
    const trans = detectApprovalTransition(editBodyInput, approvedPlan);
    assert.equal(trans?.code, 'APPROVED_PLAN_LOCKED');

    // Đổi header hợp lệ sang In Progress -> Cho phép
    const changeHeaderInput = normalizeToolArgs('replace_file_content', {
        TargetFile: 'docs/plans/2026-10-11-test-implementation-plan.md',
        TargetContent: '> **Trạng thái**: Approved',
        ReplacementContent: '> **Trạng thái**: In Progress',
        StartLine: 3,
        EndLine: 3
    });
    const transHeader = detectApprovalTransition(changeHeaderInput, approvedPlan);
    assert.equal(transHeader, null);
});

test('A5: Chuyển header trên plan blocking', () => {
    const blockingPlan = '# Kế hoạch\n\n> **Trạng thái**: Ready for Review\n\n## Technical Plan\nNội dung\n\n## Audit History\n';

    // Chuyển sang Approved -> APPROVAL_STATUS_CHANGE
    const inputApproved = normalizeToolArgs('replace_file_content', {
        TargetFile: 'docs/plans/test.md',
        TargetContent: 'Ready for Review',
        ReplacementContent: 'Approved'
    });
    assert.equal(detectApprovalTransition(inputApproved, blockingPlan)?.code, 'APPROVAL_STATUS_CHANGE');

    // Chuyển sang Draft -> Cho phép
    const inputDraft = normalizeToolArgs('replace_file_content', {
        TargetFile: 'docs/plans/test.md',
        TargetContent: 'Ready for Review',
        ReplacementContent: 'Draft'
    });
    assert.equal(detectApprovalTransition(inputDraft, blockingPlan), null);
});

test('A6 & A7: Vòng đời đóng plan và Completed giữ freeze', () => {
    const inProgress = '# Kế hoạch\n\n> **Trạng thái**: In Progress\n\n## Technical Plan\nNội dung\n\n## Audit History\n\n[PLAN_APPROVED]\n';
    assert.equal(isUnlocking(inProgress), true);

    const completed = setHeaderStatus(inProgress, 'Completed');
    assert.equal(isClosed(completed), true);
    assert.equal(isUnlocking(completed), false);
    assert.equal(isBlocking(completed).blocking, false);

    const state = computeApprovalState({
        plans: [{ name: 'docs/plans/a.md', content: completed }]
    });
    assert.equal(state.approved, false);
});

test('A9, A10, A11: Header và thẻ dị thường', () => {
    // A9: Trạng thái lạ
    const strange = '# Kế hoạch\n\n> **Trạng thái**: Rejected\n\n[PLAN_APPROVED]';
    assert.equal(isBlocking(strange).blocking, true);

    // A10: Dòng cuối phủ định
    const negation = '# Kế hoạch\n\n> **Trạng thái**: Approved\n\nChưa được cấp [PLAN_APPROVED]';
    assert.equal(isUnlocking(negation), false);
    assert.equal(isBlocking(negation).blocking, true);

    // A11: Header nằm ngoài blockquote đầu tiên
    const fakeHeader = 'Văn bản mở đầu\n# Kế hoạch\n\n> **Trạng thái**: Approved\n\n[PLAN_APPROVED]';
    assert.equal(parsePlanHeader(fakeHeader).status, null);
    assert.equal(isBlocking(fakeHeader).blocking, true);
});

test('A13: Plan legacy không có Technical Plan', () => {
    const legacy = '# Kế hoạch Wikidich\n\n> **For agentic workers:**\n> Nội dung hướng dẫn\n\nChi tiết công việc\n';
    assert.equal(isBlocking(legacy).blocking, true);
    assert.equal(isBlocking(legacy).reason, '[HEADER_MISSING]');

    const superseded = '# Kế hoạch Wikidich\n\n> **Trạng thái**: Superseded\n> **For agentic workers:**\n> Nội dung hướng dẫn\n\nChi tiết công việc\n';
    assert.equal(isClosed(superseded), true);
    assert.equal(isBlocking(superseded).blocking, false);
});

test('A18 & A19: Fallback reviewer append và Orchestrator duyệt', () => {
    const readyPlan = '# Kế hoạch\n\n> **Trạng thái**: Ready for Review\n> **Revision**: 1\n\n## Technical Plan\nNội dung\n\n## Audit History\n';

    // Reviewer append Audit Run
    const appendInput = normalizeToolArgs('replace_file_content', {
        TargetFile: 'docs/plans/test.md',
        TargetContent: '## Audit History\n',
        ReplacementContent: '## Audit History\n\n### Audit Run 1\n\n[REVIEWER_MODEL: Claude 3.7]\n\n**Kết luận**: APPROVED · BLOCKER 0 · MAJOR 0\n'
    });
    assert.equal(detectApprovalTransition(appendInput, readyPlan), null);

    const withRun = readyPlan + '\n### Audit Run 1\n\n[REVIEWER_MODEL: Claude 3.7]\n\n**Kết luận**: APPROVED · BLOCKER 0 · MAJOR 0\n';
    const summary = summarizeFallback(withRun);
    assert.ok(summary.includes('Claude 3.7'));
    assert.ok(!summary.includes('[FALLBACK_NOT_APPROVED]'));

    // Orchestrator multi_replace chạm header và dòng cuối -> MULTI_CHUNK_SENSITIVE
    const orchInput = normalizeToolArgs('multi_replace_file_content', {
        TargetFile: 'docs/plans/test.md',
        ReplacementChunks: [
            {
                TargetContent: '> **Trạng thái**: Ready for Review',
                ReplacementContent: '> **Trạng thái**: Approved',
                StartLine: 3,
                EndLine: 3
            },
            {
                TargetContent: '**Kết luận**: APPROVED · BLOCKER 0 · MAJOR 0\n',
                ReplacementContent: '**Kết luận**: APPROVED · BLOCKER 0 · MAJOR 0\n\n[PLAN_APPROVED]\n',
                StartLine: 12,
                EndLine: 14
            }
        ]
    });
    const res = detectApprovalTransition(orchInput, withRun);
    assert.equal(res?.code, 'MULTI_CHUNK_SENSITIVE');
});

test('A20: Hồi sinh plan đã đóng', () => {
    const completed = '# Kế hoạch\n\n> **Trạng thái**: Completed\n\n## Technical Plan\nNội dung\n\n## Audit History\n\n[PLAN_APPROVED]\n';
    const reopenInput = normalizeToolArgs('replace_file_content', {
        TargetFile: 'docs/plans/test.md',
        TargetContent: '> **Trạng thái**: Completed',
        ReplacementContent: '> **Trạng thái**: In Progress'
    });
    assert.equal(detectApprovalTransition(reopenInput, completed)?.code, 'PLAN_REOPEN');
});

test('A25 & A26: Chống tự mở khóa và hạ H1 giả mạo', () => {
    const completedPlan = '# Kế hoạch\n\n> **Trạng thái**: Completed\n\n## Technical Plan\nNội dung\n\n## Audit History\n\n[PLAN_APPROVED]\n';

    // T6-a bước 1: chèn H1 giả trước Technical Plan
    const step1Input = normalizeToolArgs('replace_file_content', {
        TargetFile: 'docs/plans/test.md',
        TargetContent: '## Technical Plan',
        ReplacementContent: '# X\n> **Trạng thái**: Approved\n\n## Technical Plan'
    });
    // Bước 1 được phép vì H1 thật vẫn ở dòng 1
    const step1Res = detectApprovalTransition(step1Input, completedPlan);
    assert.equal(step1Res, null);

    const step1Content = completedPlan.replace('## Technical Plan', '# X\n> **Trạng thái**: Approved\n\n## Technical Plan');

    // T6-a bước 2: hạ H1 thật xuống -> Bị chặn bởi bất biến PLAN_REOPEN
    const step2Input = normalizeToolArgs('multi_replace_file_content', {
        TargetFile: 'docs/plans/test.md',
        ReplacementChunks: [
            {
                TargetContent: '# Kế hoạch',
                ReplacementContent: 'Kế hoạch',
                StartLine: 1,
                EndLine: 1
            },
            {
                TargetContent: 'Nội dung',
                ReplacementContent: 'Nội dung cập nhật',
                StartLine: 7,
                EndLine: 9
            }
        ]
    });
    const step2Res = detectApprovalTransition(step2Input, step1Content);
    assert.ok(step2Res !== null);
    assert.ok(['MULTI_CHUNK_SENSITIVE', 'PLAN_REOPEN'].includes(step2Res?.code));
});

test('A27 & A28: Chuẩn hóa payload thật và giới hạn StartLine/EndLine', () => {
    const content = 'line 1\nTarget\nline 3\nTarget\nline 5\n';

    // Ambiguous target
    const ambiguousInput = normalizeToolArgs('replace_file_content', {
        TargetFile: 'docs/plans/test.md',
        TargetContent: 'Target',
        ReplacementContent: 'Replaced',
        AllowMultiple: false,
        StartLine: 1,
        EndLine: 5
    });
    const simAmbiguous = simulateWrite(ambiguousInput, content);
    assert.equal(simAmbiguous.ok, false);
    assert.equal(simAmbiguous.code, 'SIMULATION_FAILED');

    // Payload chuỗi JSON cho ReplacementChunks
    const jsonPayload = normalizeToolArgs('multi_replace_file_content', {
        TargetFile: 'docs/plans/test.md',
        ReplacementChunks: JSON.stringify([
            {
                TargetContent: 'line 1',
                ReplacementContent: 'line 1 edited',
                StartLine: 1,
                EndLine: 1
            }
        ])
    });
    assert.equal(jsonPayload.chunks?.length, 1);
    const simJson = simulateWrite(jsonPayload, content);
    assert.equal(simJson.ok, true);
});

test('A29: Khóa ghi đang treo (Pending writes lock)', () => {
    const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'lock-test-'));

    try {
        const pathKey = 'docs/plans/test.md';

        // Ban đầu chưa có lock
        assert.equal(checkPendingWrite(pathKey, tmpDir).pending, false);

        // Ghi lock
        recordPendingWrite(pathKey, 10, tmpDir);
        const check1 = checkPendingWrite(pathKey, tmpDir);
        assert.equal(check1.pending, true);
        assert.equal(check1.stepIdx, 10);

        // Khác pathKey thì không bị lock
        assert.equal(checkPendingWrite('docs/plans/other.md', tmpDir).pending, false);

        // Giải phóng lock theo stepIdx
        clearPendingWrite(10, tmpDir);
        assert.equal(checkPendingWrite(pathKey, tmpDir).pending, false);
    } finally {
        fs.rmSync(tmpDir, { recursive: true, force: true });
    }
});

test('A30: Hỗ trợ bí danh công cụ write_file, Write, edit_file, Edit, MultiEdit và chặn Append', () => {
    const approvedPlan = [
        '# Kế hoạch triển khai: Test',
        '',
        '> **Trạng thái**: Approved',
        '> **Revision**: 1',
        '',
        '## Technical Plan',
        'Nội dung kỹ thuật.',
        '',
        '## Audit History',
        '### Audit Run 1',
        '**Kết luận**: APPROVED · BLOCKER 0 · MAJOR 0',
        '',
        '[PLAN_APPROVED]'
    ].join('\n');

    // 1. write_file với Append: true bị từ chối fail-closed
    const appendNorm = normalizeToolArgs('write_file', {
        Path: 'docs/plans/test.md',
        Content: 'extra',
        Append: true
    });
    assert.equal(appendNorm.error, '[APPEND_NOT_SUPPORTED]');

    // 2. write_file / Write trên plan đã duyệt bị chặn bởi APPROVED_PLAN_LOCKED
    const writeFileNorm = normalizeToolArgs('write_file', {
        Path: 'docs/plans/test.md',
        Content: approvedPlan
    });
    assert.equal(detectApprovalTransition(writeFileNorm, approvedPlan)?.code, 'APPROVED_PLAN_LOCKED');

    // 3. edit_file / Edit sửa thân plan đã duyệt bị chặn bởi APPROVED_PLAN_LOCKED
    const editFileNorm = normalizeToolArgs('edit_file', {
        Path: 'docs/plans/test.md',
        Target: 'Nội dung kỹ thuật.',
        Replacement: 'Sửa trộm.'
    });
    assert.equal(detectApprovalTransition(editFileNorm, approvedPlan)?.code, 'APPROVED_PLAN_LOCKED');

    const claudeEditNorm = normalizeToolArgs('Edit', {
        file_path: 'docs/plans/test.md',
        old_string: 'Nội dung kỹ thuật.',
        new_string: 'Sửa trộm.',
        replace_all: false
    });
    assert.equal(detectApprovalTransition(claudeEditNorm, approvedPlan)?.code, 'APPROVED_PLAN_LOCKED');

    // 4. edit_file 1 chunk đổi dòng trạng thái Approved -> In Progress được cho phép (null)
    const headerTransition = normalizeToolArgs('edit_file', {
        Path: 'docs/plans/test.md',
        Target: '> **Trạng thái**: Approved',
        Replacement: '> **Trạng thái**: In Progress'
    });
    assert.equal(detectApprovalTransition(headerTransition, approvedPlan), null);

    // 5. MultiEdit chạm vùng nhạy cảm trên plan Ready for Review bị chặn bởi MULTI_CHUNK_SENSITIVE
    const openPlan = approvedPlan
        .replace('> **Trạng thái**: Approved', '> **Trạng thái**: Ready for Review')
        .replace('\n\n[PLAN_APPROVED]', '');
    const multiEditNorm = normalizeToolArgs('MultiEdit', {
        file_path: 'docs/plans/test.md',
        edits: [
            { old_string: '> **Trạng thái**: Ready for Review', new_string: '> **Trạng thái**: Approved' },
            { old_string: 'Nội dung kỹ thuật.', new_string: 'Nội dung khác.' }
        ]
    });
    assert.equal(detectApprovalTransition(multiEditNorm, openPlan)?.code, 'MULTI_CHUNK_SENSITIVE');
});
