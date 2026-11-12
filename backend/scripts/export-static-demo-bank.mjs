import {mkdirSync,writeFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {publicCatalog, resolveSense} from '../src/catalog.mjs';
import {createMockSession, validatePackage} from '../src/game.mjs';

/**
 * Export only hand-written demo packs to static JSON.
 * No LLM call; never label this export as live LLM generation or expert-reviewed content.
 * Files are designed for a separate public GitHub repository or GitHub Pages site.
 * Keys must be present for local demo grading and are therefore PUBLIC.
 */
export function buildDemoBank() {
  const catalog = publicCatalog();
  const index = {
    schemaVersion: 1,
    datasetVersion: catalog.datasetVersion,
    source: 'github-static',
    generationSource: 'mock',
    candidateCount: catalog.candidateCount,
    expertValidated: false,
    senses: catalog.senses.map(({senseId, term, reviewStatus}) => ({senseId, term, reviewStatus}))
  };
  const packs = {};
  for (const sense of index.senses) {
    const session = createMockSession(Date.UTC(2026, 10, 12), resolveSense({senseId:sense.senseId}));
    const errors = validatePackage(session.questions);
    if (errors.length) throw Error('Invalid curated demo pack: ' + errors.join(','));
    packs[sense.senseId] = {
      schemaVersion: 1,
      senseId: sense.senseId,
      generationSource: 'mock',
      referenceStatus: 'provisional',
      datasetVersion: index.datasetVersion,
      expertValidated: false,
      questions: session.questions.map(({targetMeaning,...question}) => structuredClone(question))
    };
  }
  return {index, packs};
}

export function exportDemoBank(outputDir) {
  const {index, packs} = buildDemoBank();
  mkdirSync(resolve(outputDir,'packs'), {recursive:true});
  writeFileSync(resolve(outputDir,'catalog.json'),JSON.stringify(index,null,2)+'\n');
  for (const [senseId, pack] of Object.entries(packs)) {
    writeFileSync(resolve(outputDir, 'packs', senseId+'.json'),JSON.stringify(pack,null,2)+'\n');
  }
  return {senses:Object.keys(packs),questions:Object.values(packs).reduce((n,p)=>n+p.questions.length,0)};
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const outputDir = process.argv[2] || resolve('public-question-bank');
  console.log(JSON.stringify(exportDemoBank(outputDir)));
}
