import {mkdirSync, writeFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {resolveSense, publicCatalog} from '../src/catalog.mjs';
import {createSessionService} from '../src/sessionService.mjs';
import {validatePackage} from '../src/game.mjs';
import {writePublishedCatalog, assertLegacyPublicationAllowed} from '../src/publishedBank.mjs';

/**
 * Generate ONE real LLM pack in a manually triggered GitHub Actions run.
 * Never store a fabricated 'llm' claim when the provider fell back to mock data.
 * The output contains answer keys for public, local-demo scoring.
 * No provider keys or unverified raw completions are written to files or logs.
 */
export async function exportLiveStaticPack({
  senseId = process.env.SLANG_SENSE_ID,
  outputDir = process.env.OUTPUT_DIR || 'public-question-bank',
  generate = createSessionService({
    provider: 'openai-compatible',
    retries: 0
  })
}={}) {
  const target = resolve(outputDir);
  assertLegacyPublicationAllowed(target);
  const selected = resolveSense({senseId});
  const result = await generate({senseId:selected.senseId});
  if (result.session?.source !== 'llm') {
    throw Error('Live generation did not produce an LLM result: '+String(result.failureReason||'unknown'));
  }
  const errs = validatePackage(result.session.questions);
  if (errs.length) throw Error('Generated pack failed structural validation: '+errs.join(','));
  const catalog = publicCatalog();
  assertLegacyPublicationAllowed(target);
  mkdirSync(resolve(target,'packs'),{recursive:true});
  const pack = {
    schemaVersion: 1,
    senseId: selected.senseId,
    generationSource: 'llm',
    generatorModel: process.env.LLM_MODEL || 'openai/gpt-6-luna',
    promptVersion: result.promptVersion,
    generatedAt: new Date().toISOString(),
    referenceStatus:'provisional',
    datasetVersion:catalog.datasetVersion,
    expertValidated:false,
    ...(selected.sourceCandidateId ? {sourceCandidateId:selected.sourceCandidateId,
      normalizedDefinition:selected.normalizedDefinition, referenceSources:selected.sources} : {}),
    questions: result.session.questions.map(({targetMeaning,...q})=>structuredClone(q))
  };
  writeFileSync(resolve(target,'packs',selected.senseId+'.json'),JSON.stringify(pack,null,2)+'\n');
  writePublishedCatalog(target);
  return {senseId:selected.senseId,source:'llm',questions:pack.questions.length,contexts:new Set(pack.questions.map(q=>q.contextId)).size};
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  exportLiveStaticPack().then(metrics=>{
    console.log(JSON.stringify({event:'llm-static-pack-export',...metrics}));
  }).catch(e=>{
    console.error(JSON.stringify({event:'llm-static-pack-failed',category:e?.message||'error'}));
    process.exitCode=1;
  });
}
