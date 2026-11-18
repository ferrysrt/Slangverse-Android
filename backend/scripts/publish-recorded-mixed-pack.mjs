import {readFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {writeImmutableMixedPack, writeMixedCatalog, DEFAULT_MIXED_BANK_DIR, MIXED_CATALOG_RETENTION}
  from '../src/mixedBank.mjs';

/**
 * Publish one ALREADY RECORDED attempt: no provider call, no new generation, no spend.
 * The recorded pack is validated and written byte-for-byte from the attempt directory, so the
 * published file is the same artifact a paid run produced and reviewed.
 *
 * `--retention=N` drops older mixed packs from the catalogue index (their files stay on disk).
 *
 *   node scripts/publish-recorded-mixed-pack.mjs experiments/<attemptId> --retention=1
 */
export function publishRecordedMixedPack(attemptDir, {outputDir = DEFAULT_MIXED_BANK_DIR,
  candidateCount, retention = MIXED_CATALOG_RETENTION} = {}) {
  const pack = JSON.parse(readFileSync(resolve(attemptDir, 'pack.json'), 'utf8'));
  const input = JSON.parse(readFileSync(resolve(attemptDir, 'input.json'), 'utf8'));
  const previous = JSON.parse(readFileSync(resolve(outputDir, 'catalog.json'), 'utf8'));
  const entry = writeImmutableMixedPack(pack, {outputDir});
  const catalog = writeMixedCatalog({
    outputDir, datasetVersion: input.datasetVersion,
    candidateCount: candidateCount ?? previous.candidateCount,
    additions: [entry], retention
  });
  return {entry, catalog};
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const attemptDir = process.argv[2];
  const retentionArg = process.argv.find(value => value.startsWith('--retention='));
  if (!attemptDir) {
    console.error('usage: node scripts/publish-recorded-mixed-pack.mjs <attempt-dir> [--retention=N]');
    process.exitCode = 2;
  } else {
    const {entry, catalog} = publishRecordedMixedPack(resolve(attemptDir), {
      ...(retentionArg ? {retention: Number(retentionArg.split('=')[1])} : {})
    });
    console.log(JSON.stringify({
      event: 'mixed-recorded-pack-published', packId: entry.packId, sha256: entry.sha256,
      questionCount: entry.questionCount, indexedPacks: catalog.packs.length,
      legacySenses: catalog.senses.length, bankVersion: catalog.bankVersion,
      datasetVersion: catalog.datasetVersion
    }));
  }
}
