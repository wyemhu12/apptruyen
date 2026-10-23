import fs from 'node:fs';
import path from 'node:path';

export const CANONICAL_STATUSES = ['Draft', 'Ready for Review', 'Approved', 'In Progress', 'Completed', 'Superseded'];
export const OPEN = new Set(['draft', 'ready for review']);
export const UNLOCKING = new Set(['approved', 'in progress']);
export const CLOSED = new Set(['completed', 'superseded']);
export const PLAN_PATH_RE = /^docs\/plans\/\d{4}-\d{2}-\d{2}-[a-z0-9-]+-implementation-plan\.md$/i;
export const LOCK_FILE_REL = '.agents/.pending-plan-writes.json';

export function normalizeLineEndings(text) {
    if (typeof text !== 'string') return '';
    return text.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
}

export function stripBom(text) {
    if (typeof text !== 'string') return '';
    return text.startsWith('\uFEFF') ? text.slice(1) : text;
}

export function parsePlanHeader(rawContent) {
    const content = normalizeLineEndings(stripBom(rawContent));
    const lines = content.split('\n');

    let firstNonEmptyIdx = -1;
    for (let i = 0; i < lines.length; i++) {
        if (lines[i].trim() !== '') {
            firstNonEmptyIdx = i;
            break;
        }
    }

    if (firstNonEmptyIdx === -1 || !lines[firstNonEmptyIdx].startsWith('# ')) {
        return { status: null, statusLineIndex: null, h1LineIndex: null, headerBlock: null };
    }

    const h1LineIndex = firstNonEmptyIdx;

    let blockStart = -1;
    let emptyLinesBetween = 0;
    let idx = h1LineIndex + 1;

    while (idx < lines.length && lines[idx].trim() === '') {
        emptyLinesBetween++;
        idx++;
    }

    if (emptyLinesBetween > 1 || idx >= lines.length || !lines[idx].startsWith('>')) {
        return { status: null, statusLineIndex: null, h1LineIndex, headerBlock: null };
    }

    blockStart = idx;
    while (idx < lines.length && lines[idx].startsWith('>')) {
        idx++;
    }
    const blockEnd = idx - 1;

    const statusMatches = [];
    const statusRegex = /^>\s*\*?\*?\s*(?:Trạng thái|Status)\s*\*?\*?\s*:\s*(.*?)$/i;

    for (let i = blockStart; i <= blockEnd; i++) {
        const line = lines[i];
        const match = statusRegex.exec(line);
        if (match) {
            let rawStatus = match[1].trim();
            rawStatus = rawStatus.replace(/^[*_`]+|[*_`]+$/g, '').trim();
            statusMatches.push({ index: i, rawStatus });
        }
    }

    if (statusMatches.length !== 1) {
        return { status: null, statusLineIndex: null, h1LineIndex, headerBlock: [blockStart, blockEnd] };
    }

    const matched = statusMatches[0];
    const canonicalMatch = CANONICAL_STATUSES.find(
        (s) => s.toLowerCase() === matched.rawStatus.toLowerCase()
    );

    const finalStatus = canonicalMatch || matched.rawStatus;

    return {
        status: finalStatus,
        statusLineIndex: matched.index,
        h1LineIndex,
        headerBlock: [blockStart, blockEnd]
    };
}

export function lastNonBlankLine(rawContent) {
    const lines = normalizeLineEndings(rawContent).split('\n');
    for (let i = lines.length - 1; i >= 0; i--) {
        const trimmed = lines[i].trim();
        if (trimmed !== '') return trimmed;
    }
    return '';
}

export function extractTechnicalPlan(rawContent) {
    const content = normalizeLineEndings(rawContent);
    const techPlanHeader = '## Technical Plan';
    const auditHistoryHeader = '## Audit History';

    const techIdx = content.indexOf(techPlanHeader);
    const auditIdx = content.indexOf(auditHistoryHeader);

    if (techIdx === -1 || auditIdx === -1 || auditIdx < techIdx) {
        return null;
    }

    const start = techIdx + techPlanHeader.length;
    const rawSection = content.slice(start, auditIdx);
    return rawSection.split('\n').map((l) => l.trimEnd()).join('\n').trim();
}

export function isUnlocking(rawContent) {
    const header = parsePlanHeader(rawContent);
    if (!header.status || !UNLOCKING.has(header.status.toLowerCase())) {
        return false;
    }
    const last = lastNonBlankLine(rawContent);
    return last === '[PLAN_APPROVED]';
}

export function isClosed(rawContent) {
    const header = parsePlanHeader(rawContent);
    return Boolean(header.status && CLOSED.has(header.status.toLowerCase()));
}

export function isBlocking(rawContent) {
    if (isUnlocking(rawContent) || isClosed(rawContent)) {
        return { blocking: false, reason: null };
    }
    const header = parsePlanHeader(rawContent);
    if (!header.status) {
        return { blocking: true, reason: '[HEADER_MISSING]' };
    }
    if (UNLOCKING.has(header.status.toLowerCase())) {
        return { blocking: true, reason: '[TAG_NOT_LAST]' };
    }
    return { blocking: true, reason: '[STATUS_NOT_UNLOCKING]' };
}

export function computeApprovalState({ plans, markerApproved = false }) {
    if (markerApproved) {
        return { approved: true, reason: 'Marker approved', activePlan: null, blockingPlans: [] };
    }

    const blockingPlans = [];
    const unlockingPlans = [];

    for (const p of plans) {
        const b = isBlocking(p.content);
        if (b.blocking) {
            blockingPlans.push({ name: p.name, reason: b.reason });
        }
        if (isUnlocking(p.content)) {
            unlockingPlans.push(p.name);
        }
    }

    const approved = blockingPlans.length === 0 && unlockingPlans.length > 0;
    let reason = 'Approved';
    if (blockingPlans.length > 0) {
        reason = `Blocking plans present: ${blockingPlans.map((b) => `${b.name} (${b.reason})`).join(', ')}`;
    } else if (unlockingPlans.length === 0) {
        reason = 'No unlocking plan';
    }

    return {
        approved,
        reason,
        activePlan: unlockingPlans[0] ?? null,
        blockingPlans
    };
}

export function loadApprovalStateFromDisk(repoRoot) {
    const markerFile = path.join(repoRoot, '.agents', '.plan-approved');
    let markerApproved = false;
    if (fs.existsSync(markerFile)) {
        const content = fs.readFileSync(markerFile, 'utf8').trim();
        if (content === 'true') {
            markerApproved = true;
        }
    }

    const plansDir = path.join(repoRoot, 'docs', 'plans');
    const plans = [];

    if (fs.existsSync(plansDir)) {
        const files = fs.readdirSync(plansDir);
        for (const file of files) {
            if (file.toLowerCase().endsWith('.md') && file.toLowerCase() !== 'readme.md') {
                const fullPath = path.join(plansDir, file);
                try {
                    const content = fs.readFileSync(fullPath, 'utf8');
                    plans.push({ name: `docs/plans/${file.toLowerCase()}`, content });
                } catch {
                    plans.push({ name: `docs/plans/${file.toLowerCase()}`, content: '' });
                }
            }
        }
    }

    return computeApprovalState({ plans, markerApproved });
}

export function setHeaderStatus(rawContent, newStatus) {
    const content = normalizeLineEndings(stripBom(rawContent));
    const header = parsePlanHeader(content);
    const lines = content.split('\n');

    if (header.headerBlock && header.statusLineIndex !== null) {
        lines[header.statusLineIndex] = `> **Trạng thái**: ${newStatus}`;
        return lines.join('\n');
    }

    if (header.headerBlock) {
        lines.splice(header.headerBlock[0], 0, `> **Trạng thái**: ${newStatus}`);
        return lines.join('\n');
    }

    if (header.h1LineIndex !== null) {
        lines.splice(header.h1LineIndex + 1, 0, '', `> **Trạng thái**: ${newStatus}`);
        return lines.join('\n');
    }

    return `# Kế hoạch triển khai\n\n> **Trạng thái**: ${newStatus}\n\n${content}`;
}

export const FULL_WRITE_TOOLS = new Set(['write_to_file', 'write_file', 'Write']);
export const SINGLE_EDIT_TOOLS = new Set(['replace_file_content', 'edit_file', 'Edit']);
export const MULTI_EDIT_TOOLS = new Set(['multi_replace_file_content', 'MultiEdit']);

export function normalizeToolArgs(toolName, rawArgs) {
    if (!rawArgs || typeof rawArgs !== 'object') {
        return { toolName, error: '[INVALID_ARGS_PAYLOAD]' };
    }

    const args = {};
    for (const [k, v] of Object.entries(rawArgs)) {
        args[k.toLowerCase()] = v;
    }

    const targetFile = args.targetfile ?? args.path ?? args.file_path ?? args.absolutepath;
    const stepIdx = rawArgs.stepIdx ?? rawArgs.step_idx ?? rawArgs.toolCall?.stepIdx;

    if (FULL_WRITE_TOOLS.has(toolName)) {
        if (args.append === true || args.append === 'true') {
            return { toolName, targetFile, stepIdx, error: '[APPEND_NOT_SUPPORTED]' };
        }
        const codeContent = args.codecontent ?? args.content;
        if (typeof codeContent !== 'string') {
            return { toolName, targetFile, stepIdx, error: '[CODE_CONTENT_NOT_STRING]' };
        }
        return { toolName, targetFile, stepIdx, codeContent };
    }

    if (SINGLE_EDIT_TOOLS.has(toolName)) {
        const targetContent = args.targetcontent ?? args.target ?? args.old_string;
        const replacementContent = args.replacementcontent ?? args.replacement ?? args.new_string ?? '';
        let allowMultiple = toolName === 'edit_file';

        const rawAllowMultiple = args.allowmultiple ?? args.replace_all;
        if (rawAllowMultiple !== undefined) {
            if (rawAllowMultiple === true || rawAllowMultiple === 'true') {
                allowMultiple = true;
            } else if (rawAllowMultiple === false || rawAllowMultiple === 'false') {
                allowMultiple = false;
            } else {
                return { toolName, targetFile, stepIdx, error: '[INVALID_ALLOW_MULTIPLE]' };
            }
        }

        let startLine = 1;
        let endLine = Infinity;

        const hasStart = args.startline !== undefined;
        const hasEnd = args.endline !== undefined;

        if (hasStart || hasEnd) {
            if (!hasStart || !hasEnd) {
                return { toolName, targetFile, stepIdx, error: '[INVALID_LINE_RANGE]' };
            }
            const s = Number(args.startline);
            const e = Number(args.endline);
            if (!Number.isInteger(s) || !Number.isInteger(e) || s < 1 || s > e) {
                return { toolName, targetFile, stepIdx, error: '[INVALID_LINE_RANGE]' };
            }
            startLine = s;
            endLine = e;
        }

        if (typeof targetContent !== 'string' || targetContent === '') {
            return { toolName, targetFile, stepIdx, error: '[EMPTY_TARGET_CONTENT]' };
        }

        return {
            toolName,
            targetFile,
            stepIdx,
            chunks: [{ targetContent, replacementContent, allowMultiple, startLine, endLine }]
        };
    }

    if (MULTI_EDIT_TOOLS.has(toolName)) {
        let chunksRaw = args.replacementchunks ?? args.edits;
        if (typeof chunksRaw === 'string') {
            try {
                chunksRaw = JSON.parse(chunksRaw);
            } catch {
                return { toolName, targetFile, stepIdx, error: '[INVALID_JSON_CHUNKS]' };
            }
        }

        if (!Array.isArray(chunksRaw) || chunksRaw.length === 0) {
            return { toolName, targetFile, stepIdx, error: '[EMPTY_OR_INVALID_CHUNKS]' };
        }

        const chunks = [];
        for (const item of chunksRaw) {
            if (!item || typeof item !== 'object') {
                return { toolName, targetFile, stepIdx, error: '[CHUNK_NOT_OBJECT]' };
            }

            const c = {};
            for (const [k, v] of Object.entries(item)) {
                c[k.toLowerCase()] = v;
            }

            const targetContent = c.targetcontent ?? c.target ?? c.old_string;
            const replacementContent = c.replacementcontent ?? c.replacement ?? c.new_string ?? '';
            let allowMultiple = false;

            const rawAllowMultiple = c.allowmultiple ?? c.replace_all;
            if (rawAllowMultiple !== undefined) {
                if (rawAllowMultiple === true || rawAllowMultiple === 'true') {
                    allowMultiple = true;
                } else if (rawAllowMultiple === false || rawAllowMultiple === 'false') {
                    allowMultiple = false;
                } else {
                    return { toolName, targetFile, stepIdx, error: '[INVALID_ALLOW_MULTIPLE]' };
                }
            }

            let startLine = 1;
            let endLine = Infinity;

            const hasStart = c.startline !== undefined;
            const hasEnd = c.endline !== undefined;

            if (hasStart || hasEnd) {
                if (!hasStart || !hasEnd) {
                    return { toolName, targetFile, stepIdx, error: '[INVALID_LINE_RANGE]' };
                }
                const s = Number(c.startline);
                const e = Number(c.endline);
                if (!Number.isInteger(s) || !Number.isInteger(e) || s < 1 || s > e) {
                    return { toolName, targetFile, stepIdx, error: '[INVALID_LINE_RANGE]' };
                }
                startLine = s;
                endLine = e;
            }

            if (typeof targetContent !== 'string' || targetContent === '') {
                return { toolName, targetFile, stepIdx, error: '[EMPTY_TARGET_CONTENT]' };
            }

            chunks.push({ targetContent, replacementContent, allowMultiple, startLine, endLine });
        }

        return { toolName, targetFile, stepIdx, chunks };
    }

    return { toolName, targetFile, stepIdx, error: '[UNSUPPORTED_TOOL]' };
}

function findLineRanges(content, indices, length) {
    const lineStarts = [0];
    for (let i = 0; i < content.length; i++) {
        if (content[i] === '\n') {
            lineStarts.push(i + 1);
        }
    }

    function getLine(charIdx) {
        let low = 0;
        let high = lineStarts.length - 1;
        while (low <= high) {
            const mid = Math.floor((low + high) / 2);
            if (lineStarts[mid] <= charIdx) {
                if (mid === lineStarts.length - 1 || lineStarts[mid + 1] > charIdx) {
                    return mid + 1;
                }
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return 1;
    }

    return indices.map((idx) => {
        const startLine = getLine(idx);
        const endLine = getLine(idx + length - (length > 0 ? 1 : 0));
        return { charIndex: idx, startLine, endLine };
    });
}

export function resolveChunk(chunk, rawContent) {
    const content = normalizeLineEndings(rawContent);
    const target = normalizeLineEndings(chunk.targetContent);

    if (target === '') return { error: '[EMPTY_TARGET_CONTENT]' };

    const occIndices = [];
    let pos = 0;
    while ((pos = content.indexOf(target, pos)) !== -1) {
        occIndices.push(pos);
        pos += 1;
        if (occIndices.length > 50) {
            return { error: '[TOO_MANY_OCCURRENCES]' };
        }
    }

    if (occIndices.length === 0) {
        return { error: '[TARGET_NOT_FOUND]' };
    }

    const occRanges = findLineRanges(content, occIndices, target.length);
    const totalLines = content.split('\n').length;

    if (chunk.endLine > totalLines && chunk.endLine !== Infinity) {
        return { error: '[ENDLINE_EXCEEDS_FILE]' };
    }

    const inRange = [];
    for (const r of occRanges) {
        if (r.startLine >= chunk.startLine && r.endLine <= chunk.endLine) {
            inRange.push(r);
        } else if (chunk.allowMultiple) {
            const overlaps = (r.startLine <= chunk.endLine && r.endLine >= chunk.startLine);
            if (overlaps) {
                return { error: '[CHUNK_CROSSES_LINE_BOUNDS]' };
            }
        }
    }

    if (chunk.allowMultiple) {
        if (inRange.length === 0) {
            return { error: '[TARGET_NOT_FOUND_IN_RANGE]' };
        }
    } else {
        if (inRange.length !== 1) {
            return { error: '[AMBIGUOUS_OR_NOT_FOUND]' };
        }
    }

    return { inRange, occ: occRanges };
}

export function simulateWrite(input, rawDiskContent) {
    if (input.error) {
        return { ok: false, code: 'SIMULATION_FAILED', reason: input.error };
    }

    const diskContent = normalizeLineEndings(rawDiskContent);

    if (FULL_WRITE_TOOLS.has(input.toolName)) {
        return { ok: true, afters: [normalizeLineEndings(input.codeContent ?? '')] };
    }

    if (!input.chunks || input.chunks.length === 0) {
        return { ok: false, code: 'SIMULATION_FAILED', reason: '[NO_CHUNKS]' };
    }

    if (input.chunks.length === 1) {
        const chunk = input.chunks[0];
        const res = resolveChunk(chunk, diskContent);
        if (res.error) {
            return { ok: false, code: 'SIMULATION_FAILED', reason: res.error };
        }

        const targetLen = normalizeLineEndings(chunk.targetContent).length;
        const replacement = normalizeLineEndings(chunk.replacementContent);

        function applyReplacements(indices) {
            const sorted = [...indices].sort((a, b) => b.charIndex - a.charIndex);
            let s = diskContent;
            for (const r of sorted) {
                s = s.slice(0, r.charIndex) + replacement + s.slice(r.charIndex + targetLen);
            }
            return s;
        }

        const afters = [];
        afters.push(applyReplacements(res.inRange));

        if (res.occ.length > res.inRange.length) {
            if (!chunk.allowMultiple) {
                for (const r of res.occ) {
                    afters.push(applyReplacements([r]));
                }
            } else {
                afters.push(applyReplacements(res.occ));
            }
        }

        return { ok: true, afters: Array.from(new Set(afters)) };
    }

    const resolvedChunks = [];
    for (const chunk of input.chunks) {
        const res = resolveChunk(chunk, diskContent);
        if (res.error) {
            return { ok: false, code: 'SIMULATION_FAILED', reason: res.error };
        }
        if (res.occ.length !== res.inRange.length) {
            return { ok: false, code: 'SIMULATION_FAILED', reason: '[MULTI_CHUNK_OCCURRENCE_OUTSIDE_RANGE]' };
        }
        resolvedChunks.push({ chunk, inRange: res.inRange });
    }

    const allIntervals = [];
    for (const { chunk, inRange } of resolvedChunks) {
        const len = normalizeLineEndings(chunk.targetContent).length;
        for (const r of inRange) {
            allIntervals.push({
                start: r.charIndex,
                end: r.charIndex + len,
                replacement: normalizeLineEndings(chunk.replacementContent)
            });
        }
    }

    allIntervals.sort((a, b) => a.start - b.start);
    for (let i = 0; i < allIntervals.length - 1; i++) {
        if (allIntervals[i].end > allIntervals[i + 1].start) {
            return { ok: false, code: 'SIMULATION_FAILED', reason: '[CHUNKS_OVERLAP]' };
        }
    }

    let result1 = diskContent;
    const sortedRev = [...allIntervals].sort((a, b) => b.start - a.start);
    for (const item of sortedRev) {
        result1 = result1.slice(0, item.start) + item.replacement + result1.slice(item.end);
    }

    let result2 = diskContent;
    for (const { chunk } of resolvedChunks) {
        const stepRes = resolveChunk(chunk, result2);
        if (stepRes.error || stepRes.inRange.length === 0) {
            return { ok: false, code: 'SIMULATION_FAILED', reason: '[SEQUENTIAL_APPLICATION_FAILED]' };
        }
        const targetLen = normalizeLineEndings(chunk.targetContent).length;
        const rep = normalizeLineEndings(chunk.replacementContent);
        const sortedStep = [...stepRes.inRange].sort((a, b) => b.charIndex - a.charIndex);
        for (const r of sortedStep) {
            result2 = result2.slice(0, r.charIndex) + rep + result2.slice(r.charIndex + targetLen);
        }
    }

    if (result1 !== result2) {
        return { ok: false, code: 'SIMULATION_FAILED', reason: '[MULTI_CHUNK_ORDER_MISMATCH]' };
    }

    return { ok: true, afters: [result1] };
}

export function detectApprovalTransition(input, rawDiskContent) {
    const diskContent = normalizeLineEndings(rawDiskContent);
    const beforeHeader = parsePlanHeader(diskContent);
    const beforeStatus = beforeHeader.status;
    const beforeUnlocking = isUnlocking(diskContent);
    const beforeClosed = isClosed(diskContent);

    const lines = diskContent.split('\n');
    const sensitiveLines = new Set();

    if (beforeHeader.h1LineIndex !== null) {
        sensitiveLines.add(beforeHeader.h1LineIndex + 1);
    }
    if (beforeHeader.headerBlock) {
        for (let l = beforeHeader.headerBlock[0] + 1; l <= beforeHeader.headerBlock[1] + 1; l++) {
            sensitiveLines.add(l);
        }
    }
    for (let i = 0; i < lines.length; i++) {
        if (lines[i].trim() === '[PLAN_APPROVED]') {
            sensitiveLines.add(i + 1);
        }
    }
    for (let i = lines.length - 1; i >= 0; i--) {
        if (lines[i].trim() !== '') {
            sensitiveLines.add(i + 1);
            break;
        }
    }

    if (beforeStatus && UNLOCKING.has(beforeStatus.toLowerCase())) {
        const allowedTransitions = new Set(['in progress', 'completed', 'superseded']);
        if (FULL_WRITE_TOOLS.has(input.toolName)) {
            return { code: 'APPROVED_PLAN_LOCKED', reason: '[APPROVED_PLAN_LOCKED] write_to_file trên plan đã duyệt bị cấm' };
        }
        if (!input.chunks || input.chunks.length !== 1) {
            return { code: 'APPROVED_PLAN_LOCKED', reason: '[APPROVED_PLAN_LOCKED] Plan đã duyệt chỉ cho phép 1 chunk đổi header' };
        }
        const sim = simulateWrite(input, diskContent);
        if (!sim.ok || sim.afters.length !== 1) {
            return { code: 'APPROVED_PLAN_LOCKED', reason: '[APPROVED_PLAN_LOCKED] Mô phỏng đổi header không rõ ràng' };
        }
        const chunk = input.chunks[0];
        const res = resolveChunk(chunk, diskContent);
        if (res.error || res.inRange.length !== 1) {
            return { code: 'APPROVED_PLAN_LOCKED', reason: '[APPROVED_PLAN_LOCKED] Chunk không nằm trọn trong dòng header' };
        }
        const r = res.inRange[0];
        if (r.startLine !== (beforeHeader.statusLineIndex + 1) || r.endLine !== (beforeHeader.statusLineIndex + 1)) {
            return { code: 'APPROVED_PLAN_LOCKED', reason: '[APPROVED_PLAN_LOCKED] Chunk không nằm trọn trong dòng header' };
        }

        const afterContent = sim.afters[0];
        const afterHeader = parsePlanHeader(afterContent);
        if (!afterHeader.status || !allowedTransitions.has(afterHeader.status.toLowerCase())) {
            return { code: 'APPROVED_PLAN_LOCKED', reason: '[APPROVED_PLAN_LOCKED] Chuyển trạng thái header không hợp lệ' };
        }

        const beforeWithoutHeaderLine = lines.filter((_, idx) => idx !== beforeHeader.statusLineIndex).join('\n');
        const afterLines = afterContent.split('\n');
        const afterWithoutHeaderLine = afterLines.filter((_, idx) => idx !== afterHeader.statusLineIndex).join('\n');

        if (beforeWithoutHeaderLine !== afterWithoutHeaderLine) {
            return { code: 'APPROVED_PLAN_LOCKED', reason: '[APPROVED_PLAN_LOCKED] Sửa nội dung ngoài dòng header trên plan đã duyệt' };
        }
    }

    if (input.chunks && input.chunks.length > 1) {
        for (const chunk of input.chunks) {
            const rep = chunk.replacementContent || '';
            if (/>\s*\*?\*?\s*(?:Trạng thái|Status)\s*\*?\*?:/i.test(rep) || /^# /m.test(rep) || /\[PLAN_APPROVED\]/.test(rep)) {
                return { code: 'MULTI_CHUNK_SENSITIVE', reason: '[MULTI_CHUNK_SENSITIVE] Chunk chứa dòng header/H1/thẻ' };
            }
            const res = resolveChunk(chunk, diskContent);
            if (res.error) {
                return { code: 'SIMULATION_FAILED', reason: res.error };
            }
            for (const r of res.inRange) {
                for (let l = r.startLine; l <= r.endLine; l++) {
                    if (sensitiveLines.has(l)) {
                        return { code: 'MULTI_CHUNK_SENSITIVE', reason: '[MULTI_CHUNK_SENSITIVE] Chunk chạm vùng nhạy cảm' };
                    }
                }
            }
        }
    }

    const sim = simulateWrite(input, diskContent);
    if (!sim.ok) {
        return { code: sim.code, reason: sim.reason };
    }

    const beforeLastTag = (lastNonBlankLine(diskContent) === '[PLAN_APPROVED]');

    for (const after of sim.afters) {
        const afterLastTag = (lastNonBlankLine(after) === '[PLAN_APPROVED]');
        if (beforeLastTag !== afterLastTag) {
            return { code: 'APPROVAL_TAG_CHANGE', reason: '[APPROVAL_TAG_CHANGE] Dòng thẻ ở cuối file bị thay đổi' };
        }

        const afterHeader = parsePlanHeader(after);
        const afterStatus = afterHeader.status;

        const statusUnchanged = (beforeStatus === afterStatus) ||
            (beforeStatus && afterStatus && beforeStatus.toLowerCase() === afterStatus.toLowerCase());

        if (!statusUnchanged) {
            const beforeIsOpen = beforeStatus === null || OPEN.has(beforeStatus.toLowerCase());
            const afterIsOpen = afterStatus !== null && OPEN.has(afterStatus.toLowerCase());

            const beforeIsApproved = beforeStatus && beforeStatus.toLowerCase() === 'approved';
            const afterIsInProgress = afterStatus && afterStatus.toLowerCase() === 'in progress';
            const beforeIsUnlocking = beforeStatus && UNLOCKING.has(beforeStatus.toLowerCase());
            const afterIsClosed = afterStatus && CLOSED.has(afterStatus.toLowerCase());

            if (beforeIsOpen && afterIsOpen) {
                // Draft <-> Ready for Review: allowed
            } else if (beforeIsApproved && afterIsInProgress) {
                // Approved -> In Progress: allowed (đã kiểm soát bởi khóa nội dung ở trên)
            } else if (beforeIsUnlocking && afterIsClosed) {
                // Approved/In Progress -> Completed/Superseded: allowed
            } else if (beforeStatus && CLOSED.has(beforeStatus.toLowerCase())) {
                return { code: 'PLAN_REOPEN', reason: '[PLAN_REOPEN] Mở lại plan đã đóng' };
            } else if (afterStatus && UNLOCKING.has(afterStatus.toLowerCase())) {
                return { code: 'APPROVAL_STATUS_CHANGE', reason: `[APPROVAL_STATUS_CHANGE] Đổi trạng thái sang ${afterStatus}` };
            } else if (afterStatus && CLOSED.has(afterStatus.toLowerCase()) && !beforeClosed) {
                return { code: 'CLOSE_BLOCKING_PLAN', reason: `[CLOSE_BLOCKING_PLAN] Đóng plan đang giữ freeze sang ${afterStatus}` };
            } else {
                return { code: 'STATUS_CHANGE', reason: `[STATUS_CHANGE] Đổi trạng thái từ ${beforeStatus} sang ${afterStatus}` };
            }
        }

        const afterUnlocking = isUnlocking(after);
        const afterClosed = isClosed(after);

        if (afterUnlocking && !beforeUnlocking) {
            return { code: 'APPROVAL_STATUS_CHANGE', reason: '[APPROVAL_STATUS_CHANGE] Bất biến: plan tự chuyển thành unlocking' };
        }
        if (beforeClosed && !afterClosed) {
            return { code: 'PLAN_REOPEN', reason: '[PLAN_REOPEN] Bất biến: plan đã đóng bị chuyển khỏi closed' };
        }
    }

    return null;
}

export function summarizeFallback(rawContent) {
    const content = normalizeLineEndings(rawContent);
    const header = parsePlanHeader(content);
    const lines = content.split('\n');

    let revision = 'unknown';
    const revMatch = />\s*\*?\*?Revision\s*\*?\*?\s*:\s*(\d+)/i.exec(content);
    if (revMatch) revision = revMatch[1];

    let lastAuditRunIdx = -1;
    let auditRunNumber = 0;
    const auditRunRegex = /^###\s*Audit Run\s*(\d+)/im;

    for (let i = lines.length - 1; i >= 0; i--) {
        const m = auditRunRegex.exec(lines[i]);
        if (m) {
            lastAuditRunIdx = i;
            auditRunNumber = Number(m[1]);
            break;
        }
    }

    let reviewerModel = null;
    let conclusionLine = null;
    let hasChangesRequested = false;

    if (lastAuditRunIdx !== -1) {
        for (let i = lastAuditRunIdx; i < lines.length; i++) {
            const line = lines[i];
            const modelMatch = /\[REVIEWER_MODEL:\s*([^\]]+)\]/i.exec(line);
            if (modelMatch) reviewerModel = modelMatch[1].trim();

            if (/\*\*Kết luận\*\*\s*:\s*(.*?)$/i.test(line)) {
                conclusionLine = line.trim();
            }
            if (/\[CHANGES_REQUESTED\]/i.test(line)) {
                hasChangesRequested = true;
            }
        }
    }

    const summaryParts = [
        `Plan header: ${header.status ?? 'null'}, Revision: ${revision}`,
        `Audit Run: ${auditRunNumber}, Model: ${reviewerModel ?? 'N/A'}`,
        `Conclusion: ${conclusionLine ?? 'N/A'}`
    ];

    const isValidFallback = (
        lastAuditRunIdx !== -1 &&
        reviewerModel !== null &&
        conclusionLine !== null &&
        /APPROVED\s*·\s*BLOCKER\s*0\s*·\s*MAJOR\s*0/i.test(conclusionLine) &&
        !hasChangesRequested
    );

    if (!isValidFallback) {
        summaryParts.push('[FALLBACK_NOT_APPROVED] Audit Run cuối không thỏa mãn điều kiện phê duyệt');
    }

    return summaryParts.join(' | ');
}

export function getLockFilePath(repoRoot) {
    return path.join(repoRoot, LOCK_FILE_REL);
}

export function checkPendingWrite(pathKey, repoRoot) {
    const lockFile = getLockFilePath(repoRoot);
    if (!fs.existsSync(lockFile)) {
        return { pending: false };
    }

    let data;
    try {
        const raw = fs.readFileSync(lockFile, 'utf8');
        data = JSON.parse(raw);
    } catch {
        return { pending: false, corrupted: true };
    }

    if (!data || !Array.isArray(data.entries)) {
        return { pending: false, corrupted: true };
    }

    const now = Date.now();
    for (const entry of data.entries) {
        if (entry.pathKey === pathKey && (now - entry.at < 120_000)) {
            return { pending: true, stepIdx: entry.stepIdx, at: entry.at, conversationId: entry.conversationId };
        }
    }

    return { pending: false };
}

export function recordPendingWrite(pathKey, stepIdx, repoRoot, conversationId = null) {
    const lockFile = getLockFilePath(repoRoot);
    const lockDir = path.dirname(lockFile);
    if (!fs.existsSync(lockDir)) {
        fs.mkdirSync(lockDir, { recursive: true });
    }

    let entries = [];
    if (fs.existsSync(lockFile)) {
        try {
            const raw = fs.readFileSync(lockFile, 'utf8');
            const data = JSON.parse(raw);
            if (Array.isArray(data.entries)) {
                entries = data.entries;
            }
        } catch {
            // overwrite corrupted
        }
    }

    const now = Date.now();
    entries = entries.filter((e) => (now - e.at < 120_000));
    entries.push({ pathKey, stepIdx, at: now, conversationId });

    const tmpFile = `${lockFile}.tmp.${process.pid}.${Date.now()}`;
    fs.writeFileSync(tmpFile, JSON.stringify({ entries }, null, 2), 'utf8');
    fs.renameSync(tmpFile, lockFile);
}

export function clearPendingWrite(stepIdx, repoRoot, conversationId = null) {
    const lockFile = getLockFilePath(repoRoot);
    if (!fs.existsSync(lockFile)) return;

    let entries = [];
    try {
        const raw = fs.readFileSync(lockFile, 'utf8');
        const data = JSON.parse(raw);
        if (Array.isArray(data.entries)) {
            entries = data.entries;
        }
    } catch {
        try { fs.unlinkSync(lockFile); } catch {}
        return;
    }

    const now = Date.now();
    entries = entries.filter((e) => {
        if (now - e.at >= 120_000) return false;
        if (stepIdx !== null && stepIdx !== undefined && e.stepIdx === stepIdx) {
            if (conversationId && e.conversationId && e.conversationId !== conversationId) {
                return true;
            }
            return false;
        }
        return true;
    });

    const tmpFile = `${lockFile}.tmp.${process.pid}.${Date.now()}`;
    fs.writeFileSync(tmpFile, JSON.stringify({ entries }, null, 2), 'utf8');
    fs.renameSync(tmpFile, lockFile);
}

export function main(argv = process.argv.slice(2)) {
    const subCommand = argv[0];
    if (subCommand === 'status') {
        let planFilter = null;
        for (let i = 1; i < argv.length; i++) {
            if (argv[i] === '--plan' && i + 1 < argv.length) {
                planFilter = argv[i + 1].replace(/\\/g, '/');
                i++;
            }
        }

        if (planFilter) {
            if (!PLAN_PATH_RE.test(planFilter)) {
                process.stderr.write(`[PLAN_PATH_INVALID] Đường dẫn plan không hợp lệ: ${planFilter}\n`);
                process.exitCode = 1;
                return;
            }
        }

        const repoRoot = path.resolve(path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')), '../..');
        const state = loadApprovalStateFromDisk(repoRoot);

        const plansDir = path.join(repoRoot, 'docs', 'plans');
        const plansInfo = [];

        if (fs.existsSync(plansDir)) {
            const files = fs.readdirSync(plansDir);
            for (const f of files) {
                if (f.toLowerCase().endsWith('.md') && f.toLowerCase() !== 'readme.md') {
                    const relName = `docs/plans/${f.toLowerCase()}`;
                    if (planFilter && relName !== planFilter.toLowerCase()) continue;
                    try {
                        const content = fs.readFileSync(path.join(plansDir, f), 'utf8');
                        const h = parsePlanHeader(content);
                        const tagLast = (lastNonBlankLine(content) === '[PLAN_APPROVED]');
                        plansInfo.push({
                            name: relName,
                            status: h.status,
                            tagLast,
                            unlocking: isUnlocking(content),
                            closed: isClosed(content)
                        });
                    } catch {
                        plansInfo.push({
                            name: relName,
                            status: null,
                            tagLast: false,
                            unlocking: false,
                            closed: false
                        });
                    }
                }
            }
        }

        process.stdout.write(JSON.stringify({
            approved: state.approved,
            blockingPlans: state.blockingPlans,
            plans: plansInfo
        }, null, 2) + '\n');
        process.exitCode = 0;
        return;
    }

    process.stderr.write(`[BAD_ARGS] Lệnh không hợp lệ: ${subCommand || ''}\n`);
    process.exitCode = 1;
}

if (process.argv[1] && import.meta.url.endsWith(process.argv[1].replace(/\\/g, '/'))) {
    main();
}
