import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {publishMixedBank} from '../src/mixedBank.mjs';
import {safeMixedFailureCode} from '../src/mixedProvider.mjs';

export {publishMixedBank};

// Module-relative defaults work even when the invoking shell ignores its requested cwd.
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const result = await publishMixedBank({
      maxPacks: Number(process.env.BANK_MAX_PACKS ?? 1),
      ...(process.env.OUTPUT_DIR ? {outputDir: resolve(process.env.OUTPUT_DIR)} : {}),
      ...(process.env.BANK_EXPERIMENTS_DIR ? {experimentsDir: resolve(process.env.BANK_EXPERIMENTS_DIR)} : {})
    });
    console.log(JSON.stringify({event: 'mixed-bank-publish', ...result}));
    if (result.failures.length) process.exitCode = 1;
  } catch (error) {
    // Never echo provider exception messages, response bodies, URLs or credentials.
    console.error(JSON.stringify({event: 'mixed-bank-publish-failed', code: safeMixedFailureCode(error)}));
    process.exitCode = 1;
  }
}
