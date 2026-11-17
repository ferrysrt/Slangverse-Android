import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync, mkdirSync, readFileSync, writeFileSync, rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {reportExperiments} from '../scripts/report-experiments.mjs';
import {MIXED_EVALUATION_CHECKS} from '../src/mixedQuestions.mjs';

function sandbox(t) {
  const directory = mkdtempSync(join(tmpdir(), 'experiment-report-'));
  t.after(() => rmSync(directory, {recursive: true, force: true}));
  return directory;
}
function record(root, name, status, passed) {
  const directory = join(root, name);
  mkdirSync(directory);
  const save = (file, value) => writeFileSync(join(directory, file + '.json'), JSON.stringify(value));
  save('input', {promptVersion: 'fixture-v1', datasetVersion: 'fixture-dataset'});
  save('result', {status, providerCalls: 1, startedAt: '2026-01-01T00:00:00Z', completedAt: '2026-01-01T00:00:02Z'});
  if (passed !== null) save('validation', {checks: MIXED_EVALUATION_CHECKS.map(id => ({id, passed})), errors: passed ? [] : ['fixture-failure']});
  return directory;
}

test('report keeps successful, failed, pre-call and incomplete attempts with explicit denominators', t => {
  const directory = sandbox(t);
  record(directory, 'mixed-fixture-success', 'valid', true);
  const failed = record(directory, 'mixed-fixture-failure', 'failed', false);
  writeFileSync(join(failed, 'output.txt'), 'not json');
  const original = readFileSync(join(failed, 'output.txt'), 'utf8');
  record(directory, 'mixed-fixture-inflight', 'in-flight', null);
  const missing = join(directory, 'mixed-fixture-missing');
  mkdirSync(missing);
  writeFileSync(join(missing, 'input.json'), '{broken');
  const summary = reportExperiments({directory});
  assert.equal(summary.attemptCount, 4);
  assert.equal(summary.byPrompt['fixture-v1'].attempts, 3);
  assert.equal(summary.byPrompt['fixture-v1'].terminal, 2);
  assert.equal(summary.byPrompt['fixture-v1'].valid, 1);
  assert.equal(summary.byPrompt['fixture-v1'].failed, 1);
  assert.equal(summary.byPrompt['fixture-v1'].incomplete, 1);
  assert.equal(summary.byPrompt['fixture-v1'].allNinePassed, 1);
  assert.deepEqual(summary.byPrompt['fixture-v1'].checks['json-parse'], {evaluated: 2, passed: 1});
  assert.ok(summary.attempts.find(a => a.attemptId.endsWith('missing')).auditProblems.includes('unreadable-input'));
  assert.equal(summary.attempts.find(a => a.status === 'valid').durationMs, 2000);
  assert.equal(readFileSync(join(failed, 'output.txt'), 'utf8'), original);
  assert.ok(readFileSync(join(directory, 'attempts.csv'), 'utf8').includes('mixed-fixture-failure'));
  assert.deepEqual(reportExperiments({directory}), summary);
});

test('empty report records zero observations instead of synthetic successes', t => {
  const directory = sandbox(t);
  const summary = reportExperiments({directory});
  assert.equal(summary.attemptCount, 0);
  assert.deepEqual(summary.byPrompt, {});
  assert.deepEqual(summary.attempts, []);
});

test('terminal records with missing validation remain visible and unevaluated', t => {
  const directory = sandbox(t);
  record(directory, 'mixed-fixture-no-validation', 'failed', null);
  const summary = reportExperiments({directory});
  assert.equal(summary.byPrompt['fixture-v1'].failed, 1);
  assert.deepEqual(summary.byPrompt['fixture-v1'].checks['json-parse'], {evaluated: 0, passed: 0});
  assert.equal(summary.attempts[0].allNinePassed, false);
  assert.ok(summary.attempts[0].auditProblems.includes('missing-validation'));
});
