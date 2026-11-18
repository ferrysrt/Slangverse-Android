import {readFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {listNormalizedSenses, DATASET_VERSION} from '../src/catalog.mjs';
import {generateMixedPack, DEFAULT_MIXED_EXPERIMENTS_DIR} from '../src/mixedGeneration.mjs';
import {planPackTerms, sensesForTerms, countPublishedTerms} from '../src/mixedTermPlanner.mjs';
import {publishRecordedMixedPack} from './publish-recorded-mixed-pack.mjs';
import {DEFAULT_MIXED_BANK_DIR, MIXED_CATALOG_RETENTION} from '../src/mixedBank.mjs';

const readJson = file => JSON.parse(readFileSync(file, 'utf8'));

/** Only the packs the catalog indexes count as published content. */
export function publishedPacks(outputDir = DEFAULT_MIXED_BANK_DIR) {
  const catalog = readJson(resolve(outputDir, 'catalog.json'));
  return (catalog.packs ?? []).map(entry => readJson(resolve(outputDir, 'packs', entry.packId + '.json')));
}

function attemptCost(logDir) {
  try {
    const cost = readJson(resolve(logDir, 'metadata.json'))?.usage?.cost;
    return Number.isFinite(cost) ? cost : 0;
  } catch { return 0; }
}

/**
 * One pack: plan its terms, then spend attempts until one response passes every gate.
 * A failed attempt is audited but never published, and the next attempt gets the same
 * term set so the bank stays balanced.
 */
export async function generatePackRound({senses, usedCounts, outputDir = DEFAULT_MIXED_BANK_DIR,
  experimentsDir = DEFAULT_MIXED_EXPERIMENTS_DIR, maxAttempts = 4, retention = MIXED_CATALOG_RETENTION,
  provider, providerOptions} = {}) {
  if (!Number.isInteger(maxAttempts) || maxAttempts < 1) throw Error('invalid-max-attempts');
  const terms = planPackTerms(senses, {usedCounts});
  const scoped = sensesForTerms(senses, terms);
  const attempts = [];
  let lastFailure = null;
  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    const result = await generateMixedPack({
      senses: scoped, datasetVersion: DATASET_VERSION, experimentsDir, provider, providerOptions
    });
    attempts.push({attemptId: result.attemptId, status: result.status, failureCode: result.failureCode,
      cost: attemptCost(result.logDir), errors: result.validation?.errors ?? []});
    if (!result.ok) { lastFailure = result; continue; }
    const {entry} = publishRecordedMixedPack(result.logDir, {outputDir, retention});
    return {ok: true, terms, attempts, entry, packId: result.packId};
  }
  return {ok: false, terms, attempts, failureCode: lastFailure?.failureCode ?? 'generation-failed'};
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  if (!process.env.LLM_API_KEY) {
    console.error('missing LLM_API_KEY: set it in the environment or run with --env-file=.env');
    process.exitCode = 2;
  } else {
    const intArg = (name, fallback) => {
      const found = process.argv.find(value => value.startsWith(`--${name}=`));
      if (!found) return fallback;
      const parsed = Number(found.split('=')[1]);
      if (!Number.isInteger(parsed) || parsed < 1) throw Error(`invalid-${name}`);
      return parsed;
    };
    const packs = intArg('packs', 1);
    const maxAttempts = intArg('max-attempts', 4);
    // A ten-question JSON pack needs more than the live server's short request budget.
    const timeoutMs = intArg('timeout-ms', 120000);
    if (timeoutMs > 120000) throw Error('invalid-timeout-ms');
    const providerOptions = {timeoutMs};
    const senses = listNormalizedSenses();
    const rounds = [];
    let failed = null;
    for (let round = 0; round < packs; round++) {
      const usedCounts = countPublishedTerms(publishedPacks());
      const result = await generatePackRound({senses, usedCounts, maxAttempts, providerOptions});
      rounds.push(result.ok
        ? {event: 'mixed-pack-round', pack: result.entry.packId, terms: result.terms,
          attempts: result.attempts.map(a => ({status: a.status, failureCode: a.failureCode, cost: a.cost}))}
        : {event: 'mixed-pack-round-failed', failureCode: result.failureCode, terms: result.terms,
          attempts: result.attempts.map(a => ({status: a.status, failureCode: a.failureCode, cost: a.cost, errors: a.errors}))});
      if (!result.ok) { failed = result; break; }
    }
    const catalog = readJson(resolve(DEFAULT_MIXED_BANK_DIR, 'catalog.json'));
    const summary = {
      event: 'mixed-pack-batch', packs, maxAttempts,
      published: catalog.packs.length, questions: catalog.packs.length * 10,
      attempts: rounds.flatMap(round => round.attempts ?? []).length,
      spend: rounds.flatMap(round => round.attempts ?? []).reduce((total, attempt) => total + attempt.cost, 0),
      bankVersion: catalog.bankVersion
    };
    for (const round of rounds) console.log(JSON.stringify(round));
    console.log(JSON.stringify(summary));
    if (failed) process.exitCode = 1;
  }
}
