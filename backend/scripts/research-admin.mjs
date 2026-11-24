import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile, appendFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { captureTimesFor, sortByCapture } from '../src/datasetProvenance.mjs';
import { buildNormalizedSenses, datasetVersionFor } from '../dataset/normalized.mjs';
import { generateMixedPack } from '../src/mixedGeneration.mjs';
import { validatePublishedMixedPack } from '../src/mixedQuestions.mjs';

const repo = fileURLToPath(new URL('../../', import.meta.url));
const env = process.env;
const getJson = async path => JSON.parse(await readFile(path, 'utf8'));
const hash = value => createHash('sha256').update(value).digest('hex');

export function verifyResearchBank({ catalog, files }) {
  if (catalog?.schemaVersion !== 2 || !Array.isArray(catalog.packs) || !catalog.packs.length)
    throw Error('invalid-bank-catalog');
  const packIds = new Set();
  let questions = 0;
  for (const descriptor of catalog.packs) {
    const { packId, sha256, questionCount } = descriptor;
    if (!/^[a-z0-9][a-z0-9-]{0,99}$/.test(packId) || packIds.has(packId) || !Object.hasOwn(files, packId))
      throw Error('invalid-bank-pack-id');
    packIds.add(packId);
    const bytes = files[packId];
    if (typeof bytes !== 'string' || hash(bytes) !== sha256) throw Error('bank-hash-mismatch');
    const pack = validatePublishedMixedPack(JSON.parse(bytes), { packId });
    if (pack.questions.length !== questionCount) throw Error('bank-question-count-mismatch');
    questions += questionCount;
  }
  if (Object.keys(files).length !== packIds.size) throw Error('unexpected-bank-pack');
  return questions;
}

function candidates(document) {
  return sortByCapture(document.entries.map(entry => ({ ...entry, ...captureTimesFor(entry) })));
}

export async function checkedRpc(name, payload) {
  const origin = env.SLANGVERSE_SUPABASE_URL;
  const key = env.RESEARCH_WORKER_SUPABASE_KEY;
  if (!/^https:\/\/[a-z0-9]+\.supabase\.co$/.test(origin ?? '') || !key)
    throw Error('missing-worker-supabase-config');
  const response = await fetch(origin + '/rest/v1/rpc/' + name, {
    method: 'POST',
    headers: { apikey: key, 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
    redirect: 'error',
    signal: AbortSignal.timeout(25_000)
  });
  if (!response.ok) throw Error(name + '-http-' + response.status);
  return response.status === 204 ? null : response.json();
}

export async function importDataset(document) {
  const version = datasetVersionFor(candidates(document));
  await checkedRpc('research_worker_import', { p_dataset_version: version, p_document: document,
    p_bank_version: null, p_catalog: null, p_files: null, p_question_count: null });
  return version;
}

async function seed() {
  const document = await getJson(resolve(repo, 'backend/data/discovered_candidates.json'));
  const catalog = await getJson(resolve(repo, 'question-bank/catalog.json'));
  const files = Object.fromEntries(await Promise.all(catalog.packs.map(async ({ packId }) =>
    [packId, await readFile(resolve(repo, 'question-bank/packs', packId + '.json'), 'utf8')])));
  const questionCount = verifyResearchBank({ catalog, files });
  return { p_dataset_version: datasetVersionFor(candidates(document)), p_document: document,
    p_bank_version: catalog.bankVersion, p_catalog: catalog, p_files: files, p_question_count: questionCount };
}

function quoteSql(text) {
  return "'" + text.replaceAll("'", "''") + "'";
}

async function seedSql() {
  const values = await seed();
  const args = Object.values(values).map(value => typeof value === 'number' ? value :
    quoteSql(typeof value === 'string' ? value : JSON.stringify(value)));
  process.stdout.write("set standard_conforming_strings = on;\nselect public.research_worker_import("
    + args.join(',\n') + ");\n");
}

const request = () => ({ p_request_id: env.RESEARCH_REQUEST_ID, p_run_id: Number(env.GITHUB_RUN_ID) });
const operationFile = () => resolve(env.RUNNER_TEMP, 'research-job.json');

async function claim() {
  const job = await checkedRpc('research_worker_claim', { ...request(), p_commit_sha: env.GITHUB_SHA });
  if (!job || !['generate', 'check', 'build'].includes(job.operation)) throw Error('job-not-claimed');
  await writeFile(operationFile(), JSON.stringify(job));
  await appendFile(env.GITHUB_OUTPUT, 'operation=' + job.operation + '\n');
}

async function writeBank() {
  const job = await getJson(operationFile());
  const bank = await checkedRpc('research_worker_bank', { p_version: job.bank_version });
  const questions = verifyResearchBank(bank);
  if (bank.catalog.bankVersion !== job.bank_version) throw Error('bank-version-mismatch');
  const path = resolve(env.RUNNER_TEMP, 'research-bank');
  await mkdir(resolve(path, 'packs'), { recursive: true });
  await writeFile(resolve(path, 'catalog.json'), JSON.stringify(bank.catalog) + '\n');
  for (const [packId, bytes] of Object.entries(bank.files)) {
    await writeFile(resolve(path, 'packs', packId + '.json'), bytes);
  }
  await appendFile(env.GITHUB_OUTPUT, 'directory=' + path + '\n');
  await writeFile(resolve(env.RUNNER_TEMP, 'research-summary.json'), JSON.stringify({ questions }));
}

export function generationSummary(result, input, metadata, output) {
  const evaluated = Array.isArray(output?.questions) && metadata.actualModel &&
    !['length', 'content_filter', 'error'].includes(metadata.finishReason) && !metadata.errorCode;
  const questionCount = evaluated ? output.questions.length : null;
  return {
    promptVersion: input.promptVersion, promptSha256: input.promptSha256, model: result.actualModel,
    finishReason: metadata.finishReason ?? null, tokenUsage: metadata.usage ?? null,
    providerCalls: result.providerCalls, failureCode: result.failureCode,
    questionsPassed: questionCount === null ? null : result.ok ? questionCount : 0,
    questionsFailed: questionCount === null ? null : result.ok ? 0 : questionCount,
    errors: result.validation.errors, checks: result.validation.checks
  };
}

async function generate() {
  const job = await getJson(operationFile());
  if (job.operation !== 'generate') throw Error('not-a-generation-job');
  const document = await checkedRpc('research_worker_dataset', { p_version: job.dataset_version });
  const entries = candidates(document);
  if (datasetVersionFor(entries) !== job.dataset_version) throw Error('dataset-version-mismatch');
  const result = await generateMixedPack({ senses: buildNormalizedSenses(entries),
    datasetVersion: job.dataset_version, experimentsDir: resolve(env.RUNNER_TEMP, 'research-attempt') });
  const evidence = {};
  for (const name of ['input.json','prompt.json','request.json','metadata.json','output.txt','output.json',
    'response.txt','validation.json','result.json','pack.json']) {
    try { evidence[name] = await readFile(resolve(result.logDir, name), 'utf8'); }
    catch (error) { if (error.code !== 'ENOENT') throw error; }
  }
  const read = name => evidence[name] === undefined ? {} : JSON.parse(evidence[name]);
  const summary = generationSummary(result, read('input.json'), read('metadata.json'), read('output.json'));
  await writeFile(resolve(env.RUNNER_TEMP, 'research-summary.json'), JSON.stringify(summary));
  await writeFile(resolve(env.RUNNER_TEMP, 'research-evidence.json'), JSON.stringify(evidence));
  if (!result.ok) process.exitCode = 1;
}

async function finish() {
  const load = async (name, fallback) => {
    try { return await getJson(resolve(env.RUNNER_TEMP, name)); }
    catch (error) { if (error.code === 'ENOENT') return fallback; throw error; }
  };
  const success = env.RESEARCH_JOB_SUCCESS === 'true';
  const summary = await load('research-summary.json', {});
  if (!success && !summary.failureCode) summary.failureCode = 'workflow_step_failed';
  const evidence = await load('research-evidence.json', null);
  const recorded = await checkedRpc('research_worker_finish', {
    ...request(), p_success: success, p_summary: summary, p_evidence: evidence
  });
  if (!recorded) throw Error('result-was-not-recorded');
}

const commands = { 'seed-sql': seedSql, seed: async () => checkedRpc('research_worker_import', await seed()),
  claim, bank: writeBank, generate, finish };
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const run = commands[process.argv[2]];
  try {
    if (typeof run !== 'function' || !Object.hasOwn(commands, process.argv[2])) throw Error('unknown-admin-command');
    await run();
  } catch (error) {
    // Shell output must never contain captured source text, model output, credentials or response bodies.
    const message = /^[a-z][a-z0-9_-]{1,99}$/.test(error.message) ? error.message : 'research-worker-failed';
    console.error(message);
    process.exitCode = 1;
  }
}
