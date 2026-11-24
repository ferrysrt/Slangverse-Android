import {createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
import {validatePublishedMixedPack} from '../src/mixedQuestions.mjs';

const quote = value => `'${value.replaceAll("'", "''")}'`;
const safePack = /^[a-z0-9](?:[a-z0-9-]{0,98}[a-z0-9])?$/;
const sha256 = /^[0-9a-f]{64}$/;

export async function exportAccountBankSql(bankRoot = new URL('../../question-bank/', import.meta.url)) {
  const catalog = JSON.parse(await readFile(new URL('catalog.json', bankRoot), 'utf8'));
  if (catalog.schemaVersion !== 2 || !Array.isArray(catalog.packs) || catalog.packs.length === 0) {
    throw Error('account-sync-needs-mixed-catalog');
  }
  const packIds = new Set();
  const rows = [];
  for (const descriptor of catalog.packs) {
    const {packId, sha256: expectedHash} = descriptor;
    if (typeof packId !== 'string' || !safePack.test(packId) || packIds.has(packId)
        || typeof expectedHash !== 'string' || !sha256.test(expectedHash)) throw Error('invalid-account-bank-descriptor');
    packIds.add(packId);
    const bytes = await readFile(new URL(`packs/${packId}.json`, bankRoot));
    if (createHash('sha256').update(bytes).digest('hex') !== expectedHash) throw Error('account-bank-hash-mismatch');
    const pack = validatePublishedMixedPack(JSON.parse(bytes), {packId});
    for (const question of pack.questions) {
      const fields = [packId, expectedHash, question.id, question.senseId, question.contextCategory].map(quote);
      rows.push(`    (${[...fields, question.correctIndex].join(', ')})`);
    }
  }

  return `begin;
set local standard_conforming_strings = on;
insert into slangverse_private.bank_questions (pack_id, pack_sha256, question_id, sense_id, context_category, correct_index)
values
${rows.join(',\n')}
on conflict (pack_id, pack_sha256, question_id) do nothing;
commit;
`;
}

if (process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === import.meta.url) {
  process.stdout.write(await exportAccountBankSql());
}
