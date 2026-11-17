import {existsSync, readdirSync, readFileSync, writeFileSync, mkdirSync} from 'node:fs';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {MIXED_EVALUATION_CHECKS} from '../src/mixedQuestions.mjs';
import {DEFAULT_MIXED_EXPERIMENTS_DIR} from '../src/mixedGeneration.mjs';

function readRecord(directory, name, problems) {
  const path = resolve(directory, name + '.json');
  if (!existsSync(path)) return null;
  try { return JSON.parse(readFileSync(path, 'utf8')); }
  catch { problems.push('unreadable-' + name); return null; }
}
const csvCell = value => {
  const text = String(value ?? '');
  return /[",\r\n]/.test(text) ? '"' + text.replace(/"/g, '""') + '"' : text;
};

/** Audit aggregation only: never invokes an LLM or repairs/rewrites attempt evidence. */
export function reportExperiments({directory = DEFAULT_MIXED_EXPERIMENTS_DIR} = {}) {
  mkdirSync(directory, {recursive: true});
  const attempts = readdirSync(directory, {withFileTypes: true})
    .filter(entry => entry.isDirectory() && /^mixed-[a-z0-9-]+$/.test(entry.name))
    .map(entry => {
      const path = resolve(directory, entry.name), problems = [];
      const result = readRecord(path, 'result', problems);
      const input = readRecord(path, 'input', problems);
      const metadata = readRecord(path, 'metadata', problems);
      const validation = readRecord(path, 'validation', problems);
      const terminal = ['valid', 'failed'].includes(result?.status);
      if (!result) problems.push('missing-result');
      if (!input) problems.push('missing-input');
      if (terminal && !validation) problems.push('missing-validation');
      const duration = Date.parse(result?.completedAt) - Date.parse(result?.startedAt);
      const checks = Object.fromEntries(MIXED_EVALUATION_CHECKS.map(id => {
        const check = validation?.checks?.find(item => item.id === id);
        return [id, typeof check?.passed === 'boolean' ? check.passed : null];
      }));
      return {
        attemptId: entry.name, packId: result?.packId ?? input?.packId ?? null,
        promptVersion: input?.promptVersion ?? 'unknown', promptSha256: input?.promptSha256 ?? null,
        datasetVersion: input?.datasetVersion ?? null, status: result?.status ?? 'incomplete', terminal,
        providerCalls: Number.isInteger(result?.providerCalls) ? result.providerCalls : null,
        startedAt: result?.startedAt ?? input?.startedAt ?? null, completedAt: result?.completedAt ?? null,
        durationMs: Number.isFinite(duration) && duration >= 0 ? duration : null,
        actualModel: metadata?.actualModel ?? result?.actualModel ?? null,
        finishReason: metadata?.finishReason ?? null, usage: metadata?.usage ?? null,
        failureCode: result?.failureCode ?? null, validationErrors: validation?.errors ?? [],
        selectedDistinctTerms: input?.selection?.parameters?.selectedDistinctTerms ?? null,
        checks, allNinePassed: MIXED_EVALUATION_CHECKS.every(id => checks[id] === true),
        auditProblems: problems
      };
    }).sort((a, b) => String(a.startedAt ?? a.attemptId).localeCompare(String(b.startedAt ?? b.attemptId)));
  const byPrompt = {};
  for (const attempt of attempts) {
    const group = byPrompt[attempt.promptVersion] ??= {
      attempts: 0, terminal: 0, valid: 0, failed: 0, incomplete: 0, providerCalls: 0,
      allNinePassed: 0, checks: Object.fromEntries(MIXED_EVALUATION_CHECKS.map(id => [id, {evaluated: 0, passed: 0}]))
    };
    group.attempts++;
    group.providerCalls += attempt.providerCalls ?? 0;
    if (!attempt.terminal) { group.incomplete++; continue; }
    group.terminal++;
    group[attempt.status]++;
    if (attempt.allNinePassed) group.allNinePassed++;
    for (const id of MIXED_EVALUATION_CHECKS) if (attempt.checks[id] !== null) {
      group.checks[id].evaluated++;
      if (attempt.checks[id]) group.checks[id].passed++;
    }
  }
  const summary = {schemaVersion: 1,
    note: 'Observed attempt records only. Valid means generation validation passed, not expert approval or confirmed Git publication. Incomplete records are listed, never discarded. See per-check denominators.',
    attemptCount: attempts.length, byPrompt, attempts};
  const fields = ['attempt_id', 'prompt_version', 'dataset_version', 'status', 'provider_calls', 'actual_model',
    'started_at', 'completed_at', 'duration_ms', 'selected_distinct_terms', 'all_nine_passed',
    ...MIXED_EVALUATION_CHECKS, 'failure_code', 'validation_errors', 'audit_problems'];
  const rows = attempts.map(a => [a.attemptId, a.promptVersion, a.datasetVersion, a.status, a.providerCalls,
    a.actualModel, a.startedAt, a.completedAt, a.durationMs, a.selectedDistinctTerms, a.allNinePassed,
    ...MIXED_EVALUATION_CHECKS.map(id => a.checks[id]), a.failureCode, a.validationErrors.join(';'), a.auditProblems.join(';')]);
  writeFileSync(resolve(directory, 'summary.json'), JSON.stringify(summary, null, 2) + '\n');
  writeFileSync(resolve(directory, 'attempts.csv'), [fields, ...rows].map(row => row.map(csvCell).join(',')).join('\n') + '\n');
  return summary;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const summary = reportExperiments({directory: process.env.BANK_EXPERIMENTS_DIR || DEFAULT_MIXED_EXPERIMENTS_DIR});
  console.log(JSON.stringify({event: 'experiment-report', attemptCount: summary.attemptCount, byPrompt: summary.byPrompt}));
}
