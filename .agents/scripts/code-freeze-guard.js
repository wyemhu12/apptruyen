// @ts-check
/**
 * Code Freeze Guard — PreToolUse and PostToolUse hook.
 * Adapted for AppTruyen (Kotlin + Jetpack Compose, Windows PowerShell 5.1).
 */

import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

let defaultApprovalApi = null;
try {
    defaultApprovalApi = await import('./plan-approval.js');
} catch {
    defaultApprovalApi = null;
}

/** Repo root, derived from this file's location. */
export const REPO_ROOT = path.resolve(fileURLToPath(import.meta.url), '..', '..', '..');

/** Root directory for Antigravity brain artifacts. */
export const ARTIFACT_ROOT = path.resolve(os.homedir(), '.gemini/antigravity/brain');

/** Directories that remain writable while code freeze is in effect. */
const UNFROZEN_PREFIXES = ['docs/'];

/** Root-level planning artifacts that remain writable during the freeze. */
const UNFROZEN_FILES = ['implementation_plan.md', 'task.md', 'walkthrough.md'];

/** Safe extensions permitted in scratch subdirectories within Antigravity brain. */
const SAFE_SCRATCH_EXTENSIONS = ['.md', '.json', '.kt', '.txt', '.csv', '.sql'];

/** Dangerous executable extensions explicitly rejected. */
const DANGEROUS_EXTENSIONS = ['.exe', '.dll', '.bat', '.cmd', '.ps1', '.sh', '.vbs', '.jar', '.apk'];

/** Gradle tasks that only compile / verify (outputs stay inside build/). */
const GRADLE_VERIFY_TASKS = [
    'testDebugUnitTest',
    'ktlintCheck',
    'compileDebugKotlin',
    'compileDebugUnitTestKotlin',
    'lint',
    'lintDebug',
    'assembleDebug',
    'dependencies',
    'dependencyInsight',
    'tasks'
];

/** Gradle tasks that mutate sources or produce release artifacts — allowed only after [PLAN_APPROVED]. */
const GRADLE_POST_APPROVAL_TASKS = ['ktlintFormat', 'clean', 'assembleRelease', 'installDebug'];

/** Gradle flags that never write to the source tree. */
const GRADLE_SAFE_FLAG =
    /^(?:--info|--stacktrace|--no-daemon|--offline|--console=plain|--rerun-tasks|--continue|--tests|--configuration|--dependency|-q|--quiet|--warning-mode=\w+)$/;

/**
 * Builds a matcher for `.\gradlew.bat <tasks...> [flags]` limited to the given task set.
 *
 * @param {string[]} tasks
 * @returns {(tokens: { text: string, quoted: boolean }[]) => boolean}
 */
function gradleTokenMatcher(tasks) {
    return (tokens) => {
        if (!tokens || tokens.length < 2) return false;
        const exe = tokens[0].text.replace(/^[.\\]+/, '');
        if (exe !== 'gradlew' && exe !== 'gradlew.bat') return false;

        let sawTask = false;
        for (let i = 1; i < tokens.length; i++) {
            const token = tokens[i].text;
            if (token.startsWith('-')) {
                if (!GRADLE_SAFE_FLAG.test(token)) return false;
                if (token === '--tests' || token === '--configuration' || token === '--dependency') {
                    i++;
                    if (i >= tokens.length || tokens[i].text.startsWith('-')) return false;
                }
                continue;
            }
            const taskName = token.replace(/^(?::?[\w-]+)*:/, '');
            if (!tasks.includes(taskName)) return false;
            sawTask = true;
        }
        return sawTask;
    };
}

export const isGradleVerify = gradleTokenMatcher(GRADLE_VERIFY_TASKS);
export const isGradlePostApproval = gradleTokenMatcher(GRADLE_POST_APPROVAL_TASKS);

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

export const CURL_URL_RE = new RegExp(
    `^https://(${SOURCE_HOSTS.map((h) => h.replace(/\./g, '\\.')).join('|')})(?:/[A-Za-z0-9._~/?=%-]*)?$`
);

export const NETWORK_COMMANDS = new Set([
    'curl',
    'wget',
    'invoke-webrequest',
    'iwr',
    'invoke-restmethod',
    'irm',
    'start-bitstransfer'
]);

const SAFE_SUFFIXES = [
    /\s*\|\s*Select-String\s+(?:-Pattern\s+)?"[\x20\x21\x23\x25-\x5F\x61-\x7E]*"(?:\s+-(?:Context\s+\d+|SimpleMatch|CaseSensitive))*\s*$/,
    /\s*\|\s*Select-Object\s+-(?:First|Last)\s+\d+\s*$/,
    /\s*\|\s*Tee-Object\s+-FilePath\s+build[\\/][\w.-]+\.txt\s*$/,
    /\s+2>&1\s*$/
];

const SHELL_ESCAPE_HATCHES = /[;|&`]|\$\(|>>?|\btee\b|Out-File|Set-Content|Add-Content/i;
const MUTATING_FLAGS = /--fix\b|--write\b|--force\b|--output\b|(?:^|\s)-i\b/;

const SECRET_REFERENCE =
    /keystore|\.jks\b|local\.properties|(?:^|[\s\\/])\.env\b|printenv|\benv:|Get-ChildItem\s+env|\.ssh\b|\.claude\b|git-credentials|\.gradle[\\/]gradle\.properties|credentials|hkcu:|hklm:|\.android\b|adbkey|\.config[\\/]gh\b/i;

export const TEST_INFRA_PATTERNS = [
    /^gradle\//,
    /(?:^|\/)[^/]+\.gradle(?:\.kts)?$/,
    /^gradlew(?:\.bat)?$/,
    /^gradle\.properties$/,
    /^buildsrc\//,
    /^\.github\//,
    /(?:^|\/)lint(?:-baseline)?\.xml$/,
    /^\.git(?:attributes|ignore|modules)$/,
    /^\.editorconfig$/,
    /(?:^|\/)(?:proguard|consumer)-rules\.pro$/
];

export const SECRET_PATH_PATTERNS = [
    /(?:^|\/)local\.properties$/,
    /(?:^|\/)keystore[^/]*$/,
    /\.(?:jks|keystore|key|pem|p12|pfx|der)$/,
    /(?:^|\/)id_(?:rsa|ed25519|ecdsa|dsa)[^/]*$/,
    /(?:^|\/)adbkey[^/]*$/,
    /(?:^|\/)\.env[^/]*$/,
    /(?:^|\/)google-services\.json$/,
    /(?:^|\/)\.git\//,
    /(?:^|\/)\.gradle\/gradle\.properties$/
];

const WINDOWS_SEPARATOR = String.fromCharCode(92);

export function toPosix(value) {
    return value.split(WINDOWS_SEPARATOR).join('/');
}

export function toRepoRelative(targetFile) {
    if (typeof targetFile !== 'string') return null;
    let posix = toPosix(targetFile);

    if (posix.startsWith('//') || posix.startsWith('\\\\')) return null;

    const colonIdx = posix.indexOf(':');
    if (colonIdx !== -1 && colonIdx !== 1) {
        return null;
    }

    const segments = posix.split('/');
    for (const seg of segments) {
        if (seg === '.' || seg === '..') continue;
        if (seg.endsWith('.') || seg.endsWith(' ')) {
            return null;
        }
    }

    const absolute = path.resolve(REPO_ROOT, posix);
    let realAbsolute = absolute;
    if (fs.existsSync(absolute)) {
        try {
            realAbsolute = fs.realpathSync.native(absolute);
        } catch {
            realAbsolute = absolute;
        }
    }

    const relative = toPosix(path.relative(REPO_ROOT, realAbsolute));
    if (relative === '' || relative.startsWith('../') || path.isAbsolute(relative)) {
        return null;
    }
    return relative;
}

export function pathKey(rel) {
    return rel ? rel.toLowerCase() : '';
}

export function isAntigravityArtifact(targetFile) {
    if (typeof targetFile !== 'string') return false;
    if (!path.isAbsolute(targetFile)) return false;
    if (toRepoRelative(targetFile) !== null) return false;

    const targetAbsolute = path.resolve(targetFile);
    const rel = toPosix(path.relative(ARTIFACT_ROOT, targetAbsolute));
    if (rel === '' || rel.startsWith('../') || path.isAbsolute(rel)) return false;

    const ext = path.extname(targetAbsolute).toLowerCase();
    if (DANGEROUS_EXTENSIONS.includes(ext)) return false;
    if (ext === '.md' || ext === '.json') return true;

    const parts = rel.split('/');
    return parts.includes('scratch') && SAFE_SCRATCH_EXTENSIONS.includes(ext);
}

export function isTestFile(relative) {
    if (!relative) return false;
    const pk = pathKey(relative);
    return (
        /(?:^|\/)src\/(?:test|androidtest|sharedtest|test[a-z0-9]+)\//.test(pk) ||
        /Test\.(?:kt|java)$/.test(relative) ||
        /\.test\.(?:js|ts)$/.test(relative)
    );
}

export function sensitiveCategory(relative) {
    if (!relative) return null;
    const pk = pathKey(relative);
    if (/(?:^|\/)data\/local\/appdatabase\.kt$/.test(pk) || /^app\/schemas\//.test(pk)) {
        return 'Room database schema / migrations';
    }
    if (/(?:^|\/)androidmanifest\.xml$/.test(pk)) {
        return 'AndroidManifest (exported components, permissions, cleartext)';
    }
    if (/\/res\/xml\/(?:network_security_config|backup_rules|data_extraction_rules)[\w-]*\.xml$/.test(pk)) {
        return 'network security / backup configuration';
    }
    if (/(?:^|\/)data\/remote\/(?:cookie|auth)\//.test(pk)) {
        return 'cookie / session authentication code';
    }
    return null;
}

export function isSecretPath(relative) {
    if (!relative) return false;
    const pk = pathKey(relative);
    return SECRET_PATH_PATTERNS.some((pat) => pat.test(pk));
}

export function isGovernanceFile(relative) {
    if (!relative) return false;
    const pk = pathKey(relative);
    return (
        /^\.agents\//.test(pk) ||
        pk === 'agents.md' ||
        /^claude(?:\.local)?\.md$/.test(pk) ||
        /^\.claude\//.test(pk) ||
        pk === '.mcp.json'
    );
}

export function assessTestIntegrity(oldContent, newContent) {
    if (oldContent === undefined || newContent === undefined) {
        return ['unavailable (payload carried partial or missing content)'];
    }
    const count = (str, regex) => (str.match(regex) ?? []).length;
    const delta = (regex) => count(newContent, regex) - count(oldContent, regex);
    const signals = [];

    const assertions =
        /\bassert(?:[A-Z]\w*)?\s*(?:<[^>\n]*>)?\s*[({]|\b(?:coVerify|verify)(?:Order|Sequence|All)?\s*(?:\([^)\n]*\))?\s*\{|\bexpect\s*\(/g;
    const assertionDelta = delta(assertions);
    if (assertionDelta < 0) {
        const diff = Math.abs(assertionDelta);
        signals.push(`${diff} fewer assertion/verify call${diff === 1 ? '' : 's'}`);
    }

    const disabled = [];
    if (delta(/@Disabled\b/g) > 0) disabled.push('@Disabled');
    if (delta(/@Ignore\b/g) > 0) disabled.push('@Ignore');
    if (delta(/@(?:EnabledIf|DisabledIf|EnabledOn|DisabledOn)\w*/g) > 0) disabled.push('@EnabledIf/@DisabledIf');
    if (delta(/\bassume(?:True|False|That)\s*\(/g) > 0) disabled.push('assume*()');
    if (delta(/\.(?:skip|only|todo)\b/g) > 0) disabled.push('.skip/.only/.todo');
    if (disabled.length > 0) signals.push(`adds ${disabled.join('/')}`);

    const strict = /\b(?:assertEquals|assertArrayEquals|assertIterableEquals|assertSame|assertLinesMatch|assertThrows|strictEqual|deepStrictEqual)\s*(?:<[^>\n]*>)?\s*[({]/g;
    const loose = /\b(?:assertNotNull|assertTrue|assertFalse|assertDoesNotThrow|ok)\s*\(|\bany\(\)/g;
    if (delta(strict) < 0 && delta(loose) > 0) signals.push('relaxes matcher strength');

    if (delta(/\btry\s*\{|\brunCatching\s*\{/g) > 0) signals.push('adds try/catch or runCatching block');

    return signals;
}

export function stripSafeSuffixes(command) {
    let current = command;
    let changed = true;
    while (changed) {
        changed = false;
        for (const suffix of SAFE_SUFFIXES) {
            const next = current.replace(suffix, '');
            if (next !== current) {
                current = next.trim();
                changed = true;
            }
        }
    }
    return current;
}

export function tokenizeCommand(command) {
    const rawTokens = [];
    let i = 0;
    const len = command.length;

    while (i < len) {
        while (i < len && (command[i] === ' ' || command[i] === '\t')) {
            i++;
        }
        if (i >= len) break;

        if (command[i] === '"') {
            const start = i;
            i++;
            let closed = false;
            while (i < len) {
                if (command[i] === '"') {
                    closed = true;
                    i++;
                    break;
                }
                if (command[i] === '$' || command[i] === '`') {
                    return null;
                }
                i++;
            }
            if (!closed) return null;
            if (i < len && command[i] !== ' ' && command[i] !== '\t') {
                return null;
            }
            const inner = command.slice(start + 1, i - 1);
            if (!/^[\x20-\x7E]*$/.test(inner)) return null;
            rawTokens.push({ text: inner, quoted: true });
        } else {
            const start = i;
            while (i < len && command[i] !== ' ' && command[i] !== '\t') {
                if (command[i] === '"') {
                    return null;
                }
                i++;
            }
            const token = command.slice(start, i);
            if (token.startsWith('https://')) {
                if (!/^[A-Za-z0-9_./\\:=+?%~-]+$/.test(token)) return null;
            } else {
                if (!/^[A-Za-z0-9_./\\:=+-]+$/.test(token)) return null;
            }
            rawTokens.push({ text: token, quoted: false });
        }
    }

    return rawTokens;
}

function isSafeDeviceName(segment) {
    return /^(?:nul|con|prn|aux|com[1-9]|lpt[1-9])(?:\..*)?$/i.test(segment);
}

function isSafeGitOperand(raw) {
    if (typeof raw !== 'string') return false;
    if (raw.includes(':')) return false;
    if (raw === '/dev/null') return false;
    if (raw.startsWith('\\\\') || raw.startsWith('//')) return false;

    const segments = toPosix(raw).split('/');
    for (const seg of segments) {
        if (isSafeDeviceName(seg)) return false;
    }

    const resolved = path.resolve(REPO_ROOT, toPosix(raw));
    let realResolved = resolved;
    if (fs.existsSync(resolved)) {
        try {
            realResolved = fs.realpathSync.native(resolved);
        } catch {}
    }

    const rel = toPosix(path.relative(REPO_ROOT, realResolved));
    if (rel === '..' || rel.startsWith('../') || path.isAbsolute(rel)) {
        return false;
    }

    if (isSecretPath(pathKey(rel))) {
        return false;
    }

    return true;
}

const GIT_SAFE_FLAGS =
    /^--(?:stat|shortstat|numstat|oneline|name-only|name-status|cached|staged|patch|no-color|graph|decorate|all|short|branch|porcelain(?:=v[12])?|(?:format|pretty)=(?:oneline|short|medium|full|fuller|reference|raw)|max-count=\d+|since=\S+|until=\S+|author=\S+|grep=\S+|follow)$|^-p$|^-w$|^-U\d+$|^-n\d*$|^-\d+$|^--$/;

function isGitReadOnly(tokens) {
    if (!tokens || tokens.length < 2) return false;
    if (tokens[0].text.toLowerCase() !== 'git') return false;
    const sub = tokens[1].text.toLowerCase();
    if (!['status', 'diff', 'log', 'show', 'blame'].includes(sub)) return false;

    let sawDoubleDash = false;
    for (let i = 2; i < tokens.length; i++) {
        const token = tokens[i].text;
        if (!sawDoubleDash && token.startsWith('-')) {
            if (token === '--') {
                sawDoubleDash = true;
                continue;
            }
            if (token === '-n' && i + 1 < tokens.length && /^\d+$/.test(tokens[i + 1].text)) {
                i++;
                continue;
            }
            if (!GIT_SAFE_FLAGS.test(token)) {
                return false;
            }
        } else {
            if (!isSafeGitOperand(token)) {
                return false;
            }
        }
    }
    return true;
}

function isSafeReadPath(raw) {
    if (typeof raw !== 'string') return false;
    if (/[*?[\]~$]/.test(raw)) return false;
    if (/^[A-Za-z]{2,}:/.test(raw)) return false;
    if (raw.startsWith('\\\\') || raw.startsWith('//') || raw.startsWith('\\\\?\\')) return false;

    const segments = toPosix(raw).split('/');
    for (const seg of segments) {
        if (isSafeDeviceName(seg)) return false;
    }

    const resolved = path.resolve(REPO_ROOT, toPosix(raw));
    let realResolved = resolved;
    if (fs.existsSync(resolved)) {
        try {
            realResolved = fs.realpathSync.native(resolved);
        } catch {}
    }

    const rel = toPosix(path.relative(REPO_ROOT, realResolved));
    if (rel === '..' || rel.startsWith('../') || path.isAbsolute(rel)) {
        return false;
    }

    if (isSecretPath(pathKey(rel))) {
        return false;
    }

    return true;
}

function isReadCmdlet(tokens) {
    if (!tokens || tokens.length === 0) return false;
    const cmd = tokens[0].text.toLowerCase();
    const readCmdlets = ['get-content', 'get-childitem', 'select-string', 'test-path', 'get-item', 'resolve-path'];
    if (!readCmdlets.includes(cmd)) return false;

    for (let i = 1; i < tokens.length; i++) {
        const t = tokens[i];
        if (t.quoted) continue;
        const text = t.text;
        if (text.startsWith('-')) {
            const flag = text.toLowerCase();
            if (['path', 'literalpath'].some((p) => flag === `-${p}`)) {
                i++;
                if (i >= tokens.length || !isSafeReadPath(tokens[i].text)) return false;
                continue;
            }
            if (['totalcount', 'tail', 'depth', 'context'].some((p) => flag === `-${p}`)) {
                i++;
                if (i >= tokens.length || !/^\d+$/.test(tokens[i].text)) return false;
                continue;
            }
            if (flag === '-filter') {
                i++;
                if (i >= tokens.length || /[/\\]/.test(tokens[i].text)) return false;
                continue;
            }
            if (flag === '-pattern') {
                i++;
                if (i >= tokens.length) return false;
                continue;
            }
            if (['raw', 'recurse', 'file', 'directory', 'name', 'simplematch', 'casesensitive'].some((p) => flag === `-${p}`)) {
                continue;
            }
            if (flag === '-encoding' && i + 1 < tokens.length && tokens[i + 1].text.toLowerCase() === 'utf8') {
                i++;
                continue;
            }
            return false;
        } else {
            if (!isSafeReadPath(text)) {
                return false;
            }
        }
    }
    return true;
}

function isAllowedNodeCommand(tokens) {
    if (!tokens || tokens.length < 2) return false;
    if (tokens[0].text.toLowerCase() !== 'node') return false;

    const sub = tokens[1].text.toLowerCase();
    if (sub === '--test') {
        if (tokens.length !== 3) return false;
        const target = toPosix(tokens[2].text).toLowerCase();
        return (
            target === '.agents/scripts/code-freeze-guard.test.js' ||
            target === '.agents/scripts/plan-approval.test.js' ||
            target === '.agents/scripts/review-run.test.js'
        );
    }

    const scriptPath = toPosix(tokens[1].text).toLowerCase();
    if (scriptPath === '.agents/scripts/plan-approval.js') {
        if (tokens.length >= 3 && tokens[2].text.toLowerCase() === 'status') {
            if (tokens.length === 3) return true;
            if (tokens.length === 5 && tokens[3].text.toLowerCase() === '--plan') {
                const planRel = toPosix(tokens[4].text).toLowerCase();
                return /^docs\/plans\/\d{4}-\d{2}-\d{2}-[a-z0-9-]+-implementation-plan\.md$/.test(planRel);
            }
        }
    }

    return false;
}

function isReviewerScriptCall(tokens) {
    if (!tokens || tokens.length < 2) return false;
    const exe = tokens[0].text.toLowerCase().replace(/\.exe$/, '');
    if (exe !== 'powershell') return false;

    let idx = 1;
    if (tokens[idx].text.toLowerCase() === '-executionpolicy' && idx + 1 < tokens.length) {
        if (tokens[idx + 1].text.toLowerCase() !== 'bypass') return false;
        idx += 2;
    }

    if (idx >= tokens.length || tokens[idx].text.toLowerCase() !== '-file') return false;
    idx++;

    if (idx >= tokens.length) return false;
    const script = toPosix(tokens[idx].text).toLowerCase();
    if (script !== '.agents/scripts/run-claude.ps1') return false;
    idx++;

    if (idx === tokens.length) return true;
    if (idx === tokens.length - 1 && tokens[idx].text.toLowerCase() === '-checkonly') return true;

    let sawPlan = false;
    let sawModel = false;
    let sawNoGradle = false;

    while (idx < tokens.length) {
        const flag = tokens[idx].text.toLowerCase();
        if (flag === '-planfile' && !sawPlan) {
            sawPlan = true;
            idx++;
            if (idx >= tokens.length) return false;
            const p = toPosix(tokens[idx].text).toLowerCase();
            if (!/^docs\/plans\/\d{4}-\d{2}-\d{2}-[a-z0-9-]+-implementation-plan\.md$/.test(p)) return false;
            idx++;
        } else if (flag === '-model' && !sawModel) {
            sawModel = true;
            idx++;
            if (idx >= tokens.length || !/^[A-Za-z0-9.-]+$/.test(tokens[idx].text)) return false;
            idx++;
        } else if (flag === '-nogradle' && !sawNoGradle) {
            sawNoGradle = true;
            idx++;
        } else {
            return false;
        }
    }

    return sawPlan;
}

function isReadOnlyCurl(tokens) {
    if (!tokens || tokens.length < 3) return false;
    if (tokens[0].text.toLowerCase() !== 'curl.exe') return false;
    if (tokens[1].text !== '-q') return false;

    let sawUrl = false;

    for (let i = 2; i < tokens.length; i++) {
        const token = tokens[i];
        if (token.quoted) {
            return false;
        }
        const text = token.text;

        if (/^-[sSI]+$/.test(text) || text === '--compressed') {
            continue;
        }
        if (text === '--max-time' || text === '-m') {
            i++;
            if (i >= tokens.length || !/^\d{1,3}$/.test(tokens[i].text) || tokens[i].quoted) {
                return false;
            }
            continue;
        }
        if (text === '-A' || text === '--user-agent') {
            i++;
            if (i >= tokens.length || !tokens[i].quoted || !/^[A-Za-z0-9 ./():,_+-]{1,200}$/.test(tokens[i].text)) {
                return false;
            }
            continue;
        }
        if (CURL_URL_RE.test(text)) {
            if (sawUrl) return false;
            sawUrl = true;
            continue;
        }

        return false;
    }

    return sawUrl;
}

export function isPlanContentApproved(content) {
    const lines = content.split(/\r?\n/);
    const header = lines.slice(0, 30).join('\n');
    const isPending =
        /(?:\*\*|\b)(?:Status|Trạng thái)(?:\*\*)?\s*:\s*(?:\*\*)?\s*`?(?:Draft|In Review|Ready for Review|Changes Requested|Chờ Duyệt)/i.test(
            header
        );
    if (isPending) return false;
    const nonBlank = lines.map((line) => line.trim()).filter(Boolean);
    if (nonBlank.length === 0) return false;
    return nonBlank[nonBlank.length - 1].endsWith('[PLAN_APPROVED]');
}

export function isPlanApproved(repoRoot = REPO_ROOT) {
    const markerPath = path.resolve(repoRoot, '.agents/.plan-approved');
    if (fs.existsSync(markerPath)) {
        try {
            const markerContent = fs.readFileSync(markerPath, 'utf8').trim();
            if (markerContent === 'true') return true;
        } catch {}
    }

    const plansDir = path.resolve(repoRoot, 'docs/plans');
    if (!fs.existsSync(plansDir)) return false;
    try {
        const entries = fs
            .readdirSync(plansDir)
            .filter((file) => file.endsWith('.md') && file.toLowerCase() !== 'readme.md')
            .map((file) => path.join(plansDir, file));
        if (entries.length === 0) return false;
        entries.sort((a, b) => fs.statSync(b).mtimeMs - fs.statSync(a).mtimeMs);
        return isPlanContentApproved(fs.readFileSync(entries[0], 'utf8'));
    } catch {
        return false;
    }
}

export function unwrapJsonString(value) {
    if (typeof value !== 'string' || value.length < 2 || !value.startsWith('"') || !value.endsWith('"')) {
        return value;
    }
    try {
        const decoded = JSON.parse(value);
        return typeof decoded === 'string' ? decoded : value;
    } catch {
        return value;
    }
}

const FULL_WRITE_TOOLS = new Set(['write_to_file', 'write_file', 'Write']);
const WRITE_EDIT_TOOLS = new Set([
    'write_to_file',
    'write_file',
    'Write',
    'replace_file_content',
    'edit_file',
    'Edit',
    'multi_replace_file_content',
    'MultiEdit'
]);
const READ_TOOLS = new Set(['Read', 'read_file', 'view_file']);

export function extractInput(payload, approvalApi = null) {
    const root = /** @type {Record<string, any>} */ (payload ?? {});
    const args = root.toolCall?.args ?? root.tool_input ?? {};
    const toolName =
        typeof root.toolCall?.name === 'string'
            ? root.toolCall.name
            : typeof root.tool_name === 'string'
                ? root.tool_name
                : undefined;

    const rawTarget = unwrapJsonString(
        args.TargetFile ?? args.Path ?? args.AbsolutePath ?? args.file_path ?? args.path
    );
    const targetFile = typeof rawTarget === 'string' ? rawTarget : undefined;

    const rawCommand = unwrapJsonString(args.CommandLine ?? args.Command ?? args.command);
    const command = typeof rawCommand === 'string' ? rawCommand : undefined;

    const stepIdx = root.stepIdx ?? root.step_idx ?? root.toolCall?.stepIdx;

    if (approvalApi && toolName && WRITE_EDIT_TOOLS.has(toolName)) {
        const norm = approvalApi.normalizeToolArgs(toolName, { ...args, stepIdx });
        let newContent = norm.codeContent;
        let oldContent;

        if (norm.chunks && norm.chunks.length > 0) {
            oldContent = norm.chunks.map((c) => c.targetContent).join('\n');
            newContent = norm.chunks.map((c) => c.replacementContent).join('\n');
        }

        if (FULL_WRITE_TOOLS.has(toolName) && oldContent === undefined && targetFile) {
            try {
                const absolute = path.resolve(REPO_ROOT, toPosix(targetFile));
                if (fs.existsSync(absolute) && fs.statSync(absolute).isFile()) {
                    oldContent = fs.readFileSync(absolute, 'utf8');
                }
            } catch {}
        }

        return {
            toolName,
            targetFile,
            command,
            stepIdx,
            chunks: norm.chunks,
            codeContent: norm.codeContent,
            error: norm.error,
            newContent,
            oldContent
        };
    }

    let newContent;
    let oldContent;
    const rawChunks = Array.isArray(args.ReplacementChunks)
        ? args.ReplacementChunks
        : Array.isArray(args.edits)
            ? args.edits
            : null;
    if (rawChunks && rawChunks.length > 0) {
        const oldParts = [];
        const newParts = [];
        for (const chunk of rawChunks) {
            if (chunk && typeof chunk === 'object') {
                const cOld = chunk.TargetContent ?? chunk.Target ?? chunk.old_string;
                const cNew = chunk.ReplacementContent ?? chunk.Replacement ?? chunk.new_string;
                if (typeof cOld === 'string') oldParts.push(cOld);
                if (typeof cNew === 'string') newParts.push(cNew);
            }
        }
        if (oldParts.length > 0) oldContent = oldParts.join('\n');
        if (newParts.length > 0) newContent = newParts.join('\n');
    } else {
        const rawNew =
            args.CodeContent ??
            args.Content ??
            args.ReplacementContent ??
            args.Replacement ??
            args.new_string ??
            args.content ??
            args.new_content;
        newContent = typeof rawNew === 'string' ? rawNew : undefined;
        const rawOld =
            args.TargetContent ??
            args.Target ??
            args.old_string ??
            args.old_content ??
            args.target_content;
        oldContent = typeof rawOld === 'string' ? rawOld : undefined;
    }

    if (FULL_WRITE_TOOLS.has(toolName ?? '') && oldContent === undefined && targetFile) {
        try {
            const absolute = path.resolve(REPO_ROOT, toPosix(targetFile));
            if (fs.existsSync(absolute) && fs.statSync(absolute).isFile()) {
                oldContent = fs.readFileSync(absolute, 'utf8');
            }
        } catch {}
    }

    return { toolName, targetFile, command, stepIdx, newContent, oldContent };
}

export function decide(input, options = {}) {
    const ask = (reason) => ({ decision: 'ask', reason });
    const approvalApi = options.approvalApi ?? defaultApprovalApi;
    const planApproved = () => {
        if (options.planApproved !== undefined) return options.planApproved;
        if (approvalApi) {
            return approvalApi.loadApprovalStateFromDisk(REPO_ROOT).approved;
        }
        return isPlanApproved();
    };

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

        if (relative === null) {
            if (isAntigravityArtifact(input.targetFile)) return { decision: 'allow' };
            return ask(`Write targets a path outside the repository: ${input.targetFile}`);
        }

        const pk = pathKey(relative);

        if (relative.includes('.gemini/') || relative.startsWith('.git/') || isSecretPath(relative)) {
            return ask(`⚠️ Security Guard: write targets a prohibited/secret path: ${relative}`);
        }

        if (isGovernanceFile(relative)) {
            return ask(`⚠️ Governance Guard: write targets governance file: ${relative}`);
        }

        if (pk.startsWith('docs/plans/') && pk.endsWith('.md') && pk !== 'docs/plans/readme.md') {
            if (!approvalApi) {
                return ask('[APPROVAL_API_MISSING] plan-approval module chưa sẵn sàng');
            }

            const lockCheck = approvalApi.checkPendingWrite(pk, REPO_ROOT);
            if (lockCheck.corrupted) {
                return ask('[CORRUPTED_LOCK_FILE] File trạng thái khóa ghi bị hỏng, fail closed');
            }
            if (lockCheck.pending) {
                return {
                    decision: 'deny',
                    reason: `[CONCURRENT_PLAN_WRITE] Đang có thao tác ghi khác chưa hoàn tất trên cùng plan (bước stepIdx: ${lockCheck.stepIdx}); các lời gọi ghi đồng thời lên cùng plan bị từ chối để chống TOCTOU. Vui lòng thử lại tuần tự ở lượt sau.`
                };
            }

            const fullPlan = path.resolve(REPO_ROOT, relative);
            let diskContent = '';
            if (fs.existsSync(fullPlan)) {
                diskContent = fs.readFileSync(fullPlan, 'utf8');
            }

            const trans = approvalApi.detectApprovalTransition(input, diskContent);
            if (trans !== null) {
                const summary = approvalApi.summarizeFallback(diskContent);
                if (input.stepIdx === undefined || input.stepIdx === null) {
                    return ask(`[STEP_IDX_MISSING] Thiếu stepIdx khi ghi plan | [${trans.code}] ${trans.reason} | ${summary}`);
                }
                approvalApi.recordPendingWrite(pk, input.stepIdx, REPO_ROOT);
                return ask(`[${trans.code}] ${trans.reason} | ${summary}`);
            }

            if (input.stepIdx === undefined || input.stepIdx === null) {
                return ask('[STEP_IDX_MISSING] Thiếu stepIdx khi ghi plan');
            }
            approvalApi.recordPendingWrite(pk, input.stepIdx, REPO_ROOT);
            return { decision: 'allow' };
        }

        if (UNFROZEN_PREFIXES.some((p) => pk.startsWith(p)) || UNFROZEN_FILES.includes(pk)) {
            if (!pk.endsWith('.md')) {
                return ask(`⚠️ Code Freeze Guard: only .md files are allowed inside unfrozen documentation paths (${relative}).`);
            }
            return { decision: 'allow' };
        }

        if (TEST_INFRA_PATTERNS.some((pat) => pat.test(pk))) {
            return ask(
                [
                    '⚠️ Test Infrastructure Guard: this edit changes build/CI/lint configuration.',
                    `Path: ${relative}`,
                    'Before approving, ask the agent whether it adds a dependency, narrows which tests run, relaxes ktlint/lint or weakens CI, and why.'
                ].join('\n')
            );
        }

        if (isTestFile(relative)) {
            let signals;
            try {
                signals = assessTestIntegrity(input.oldContent, input.newContent);
            } catch {
                signals = ['unavailable (payload carried no content)'];
            }
            return ask(
                [
                    '⚠️ Test Integrity Guard: this edit modifies a TEST file.',
                    `Path: ${relative}`,
                    `Signals: ${signals.length > 0 ? signals.join('; ') : 'none detected'}`
                ].join('\n')
            );
        }

        const core = sensitiveCategory(relative);
        if (core !== null) {
            return ask(`⚠️ Sensitive Core Guard: write targets ${core}.\nPath: ${relative}`);
        }

        if (planApproved()) {
            return { decision: 'allow' };
        }

        return ask(`⚠️ Code Freeze Guard: write blocked pending plan approval.\nPath: ${relative}`);
    }

    if (input.command) {
        const rawCommand = input.command;

        if (/[\r\n\u0085\u2028\u2029\u0000]/.test(rawCommand)) {
            return ask(`⚠️ Code Freeze Guard: command contains newline characters.\nCommand: ${rawCommand}`);
        }

        const nonAsciiMatch = /[^\t\x20-\x7E]/.exec(rawCommand);
        if (nonAsciiMatch) {
            const hex = nonAsciiMatch[0].charCodeAt(0).toString(16).toUpperCase().padStart(4, '0');
            return ask(
                `[NON_ASCII_COMMAND] Lệnh chứa ký tự ngoài ASCII (U+${hex} tại vị trí ${nonAsciiMatch.index}); PowerShell coi một số ký tự Unicode là dấu nháy/gạch/khoảng trắng nên hook không phân tích an toàn được.`
            );
        }

        const command = rawCommand.trim();

        if (SECRET_REFERENCE.test(command)) {
            return ask(`⚠️ Security Guard: command references signing material or secrets.\nCommand: ${command}`);
        }

        const segments = command.split(';').map((s) => s.trim()).filter(Boolean);
        if (segments.length > 1) {
            for (const seg of segments) {
                const dec = decide({ command: seg }, options);
                if (dec.decision !== 'allow') {
                    return dec;
                }
            }
            return { decision: 'allow' };
        }

        const firstWord = command.trimStart().split(/[ \t]+/)[0].toLowerCase();
        const baseCmd = firstWord.replace(/\.exe$/, '');

        if (baseCmd === 'curl') {
            if (firstWord === 'curl.exe') {
                const base = stripSafeSuffixes(command);
                const tokens = tokenizeCommand(base);
                if (tokens && isReadOnlyCurl(tokens)) {
                    return { decision: 'allow' };
                }
                return ask(`[CURL_NOT_ALLOWED] Chỉ tự cho phép curl.exe -q … tới host nguồn truyện; dạng này cần User xác nhận.`);
            }
            return ask(`[NETWORK_COMMAND] Lệnh mạng cần User xác nhận; dùng curl.exe -q -s <url nguồn> hoặc tool read_url_content.`);
        }

        if (NETWORK_COMMANDS.has(baseCmd)) {
            return ask(`[NETWORK_COMMAND] Lệnh mạng cần User xác nhận; dùng curl.exe -q -s <url nguồn> hoặc tool read_url_content.`);
        }

        const base = stripSafeSuffixes(command);
        if (SHELL_ESCAPE_HATCHES.test(base) || MUTATING_FLAGS.test(base)) {
            return ask(`⚠️ Code Freeze Guard: command contains redirection, chaining or a mutating flag.\nCommand: ${command}`);
        }

        const tokens = tokenizeCommand(base);
        if (!tokens) {
            return ask(`⚠️ Code Freeze Guard: command could not be safely tokenized.\nCommand: ${command}`);
        }

        if (isGradleVerify(tokens)) {
            return { decision: 'allow' };
        }
        if (isGitReadOnly(tokens)) {
            return { decision: 'allow' };
        }
        if (isAllowedNodeCommand(tokens)) {
            return { decision: 'allow' };
        }
        if (isReadCmdlet(tokens)) {
            return { decision: 'allow' };
        }
        if (isReviewerScriptCall(tokens)) {
            return { decision: 'allow' };
        }
        if (isGradlePostApproval(tokens) && planApproved()) {
            return { decision: 'allow' };
        }

        return ask(`⚠️ Code Freeze Guard: command is outside the read-only ALLOWLIST.\nCommand: ${command}`);
    }

    return ask('⚠️ Code Freeze Guard: tool call carries no recognisable target — failing closed.');
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

async function main() {
    const isPostEvent = process.argv.includes('--event') && process.argv[process.argv.indexOf('--event') + 1] === 'post';

    let approvalApi = null;
    try {
        approvalApi = await import(new URL('./plan-approval.js', import.meta.url));
    } catch (err) {
        if (isPostEvent) {
            process.stdout.write('{}');
            process.exit(0);
        }
        process.stdout.write(
            JSON.stringify({
                decision: 'force_ask',
                reason: `[GUARD_MODULE_LOAD_FAILED] Code Freeze Guard không nạp được plan-approval.js: ${err.message}`
            })
        );
        process.exit(0);
    }

    let raw = '';
    process.stdin.setEncoding('utf8');
    process.stdin.on('data', (chunk) => {
        raw += chunk;
    });

    process.stdin.on('end', () => {
        let payload;
        try {
            payload = JSON.parse(raw.replace(/^\uFEFF/, '').trim());
        } catch {
            if (isPostEvent) {
                process.stdout.write('{}');
                process.exit(0);
            }
            process.stdout.write(
                JSON.stringify({
                    decision: 'force_ask',
                    reason: '[PAYLOAD_UNREADABLE] Code Freeze Guard: payload could not be parsed — failing closed.'
                })
            );
            process.exit(0);
        }

        if (isPostEvent) {
            try {
                const stepIdx = payload.stepIdx ?? payload.step_idx ?? payload.toolCall?.stepIdx;
                approvalApi.clearPendingWrite(stepIdx, REPO_ROOT);
            } catch {}
            process.stdout.write('{}');
            process.exit(0);
        }

        const input = extractInput(payload, approvalApi);
        const result = decide(input, { approvalApi });
        const formatted = formatHookOutput(result, payload);
        if (formatted !== null) {
            process.stdout.write(JSON.stringify(formatted));
        }
    });
}

if (process.argv[1] && path.resolve(process.argv[1]).endsWith('code-freeze-guard.js')) {
    main();
}
