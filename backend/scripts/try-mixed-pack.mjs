import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {existsSync} from 'node:fs';
import {listNormalizedSenses, DATASET_VERSION} from '../src/catalog.mjs';
import {generateMixedPack, DEFAULT_MIXED_EXPERIMENTS_DIR} from '../src/mixedGeneration.mjs';
import {MIXED_PROMPT_VERSION} from '../src/mixedQuestions.mjs';
import {summarizeQuestions, loadQuestions} from './analyze-mixed-attempt.mjs';

/**
 * One paid generation attempt with NO publication: the question bank is never written, so an
 * experimental prompt version cannot reach a released Android client. Audit files land in
 * backend/experiments/<attemptId>/ exactly as a publishing run would produce them.
 *
 *   node --env-file=.env scripts/try-mixed-pack.mjs
 */
export async function tryMixedPack({experimentsDir = DEFAULT_MIXED_EXPERIMENTS_DIR, provider, providerOptions} = {}) {
  const result = await generateMixedPack({
    senses: listNormalizedSenses(), datasetVersion: DATASET_VERSION,
    experimentsDir, provider, providerOptions
  });
  const output = resolve(result.logDir, 'output.json');
  const analysis = existsSync(output) ? summarizeQuestions(loadQuestions(output)) : null;
  return {...result, analysis};
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  if (!process.env.LLM_API_KEY) {
    console.error('missing LLM_API_KEY: set it in the environment or run with --env-file=.env');
    process.exitCode = 2;
  } else {
    const result = await tryMixedPack({
      ...(process.env.BANK_EXPERIMENTS_DIR ? {experimentsDir: resolve(process.env.BANK_EXPERIMENTS_DIR)} : {})
    });
    console.log(JSON.stringify({
      event: 'mixed-try-pack', promptVersion: MIXED_PROMPT_VERSION, attemptId: result.attemptId,
      status: result.status, failureCode: result.failureCode, providerCalls: result.providerCalls,
      actualModel: result.actualModel, errors: result.validation?.errors ?? [], logDir: result.logDir,
      published: false
    }));
    if (result.analysis) {
      console.log('\nquestionComment review signals (heuristics, not validation):');
      for (const item of result.analysis.items) {
        const flags = [
          item.termOnDeclaredTarget ? '' : 'TERM-MISSING',
          item.verbatimCorrectOption ? '' : 'OPTION-PARAPHRASED',
          item.metaFeedRegister ? 'META-FEED' : '',
          item.sharedMeaningWords.length ? `shares:${item.sharedMeaningWords.join(',')}` : ''
        ].filter(Boolean).join(' ');
        console.log(`  ${String(item.slang).padEnd(12)} ${flags}`);
        console.log(`    "${item.questionComment}"`);
      }
      console.log('\n' + JSON.stringify(result.analysis.summary));
    }
    if (!result.ok) process.exitCode = 1;
  }
}
