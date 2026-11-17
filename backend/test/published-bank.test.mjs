import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync,readFileSync,rmSync,existsSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {exportLiveStaticPack} from '../scripts/export-static-llm-bank.mjs';
import {publishQuestionBank} from '../scripts/publish-question-bank.mjs';
import {validatePublishedPack,sha256} from '../src/publishedBank.mjs';
import {createMockSession} from '../src/game.mjs';
import {resolveSense,publicCatalog} from '../src/catalog.mjs';
import {PROMPT_VERSION} from '../src/prompts.mjs';
// Synthetic provider fixture only; production never relabels a mock as LLM.
const fixtureGenerate=async({senseId})=>{
 const sense=resolveSense({senseId}),session=createMockSession();
 session.senseId=senseId;session.source='llm';
 session.questions=session.questions.map(q=>({...q,slang:sense.term,targetMeaning:sense.targetMeaning,
  questionComment:undefined,options:q.options.map((option,index)=>index===q.correctIndex?sense.targetMeaning:option)}));
 return {session,promptVersion:PROMPT_VERSION};
};
function sandbox(t){const dir=mkdtempSync(join(tmpdir(),'bank-test-'));t.after(()=>rmSync(dir,{recursive:true,force:true}));return dir;}

test('publishing a second sense preserves the first and hashes the exact public bytes',async t=>{
 const dir=sandbox(t);
 for(const senseId of ['cooked-trouble','no-cap-truth'])await exportLiveStaticPack({senseId,outputDir:dir,generate:fixtureGenerate});
 const catalog=JSON.parse(readFileSync(join(dir,'catalog.json')));
 assert.equal(catalog.generationSource,'llm');assert.equal(catalog.status,'ready');
 assert.deepEqual(catalog.senses.map(s=>s.senseId),['cooked-trouble','no-cap-truth']);
 for(const sense of catalog.senses){
  const text=readFileSync(join(dir,'packs',sense.senseId+'.json'),'utf8');
  assert.equal(sense.sha256,sha256(text));validatePublishedPack(JSON.parse(text),sense.senseId);
 }
});
test('mock/fallback output cannot be exported as a published LLM pack',async t=>{
 const dir=sandbox(t);
 await assert.rejects(exportLiveStaticPack({senseId:'cooked-trouble',outputDir:dir,generate:async()=>({session:createMockSession(),failureReason:'test'})}),/did not produce an LLM/);
 assert.equal(existsSync(join(dir,'packs','cooked-trouble.json')),false);
});
test('bounded generation advances across curated senses and skips current packs',async t=>{
 const dir=sandbox(t);let calls=0;
 const exportPack=async opts=>{calls++;return exportLiveStaticPack({...opts,generate:fixtureGenerate});};
 let result=await publishQuestionBank({outputDir:dir,maxPacks:1,exportPack});
 assert.equal(result.attempted,1);assert.equal(result.available,1);
 result=await publishQuestionBank({outputDir:dir,maxPacks:1,exportPack});
 assert.equal(result.available,2);assert.equal(calls,2);
 result=await publishQuestionBank({outputDir:dir,maxPacks:3,exportPack});
 assert.equal(result.available,Math.min(5,publicCatalog().senses.length));
 assert.equal(calls,Math.min(5,publicCatalog().senses.length));
 while(result.available<publicCatalog().senses.length){
  result=await publishQuestionBank({outputDir:dir,maxPacks:3,exportPack});
 }
 result=await publishQuestionBank({outputDir:dir,maxPacks:3,exportPack});
 assert.equal(result.attempted,0);assert.equal(calls,publicCatalog().senses.length);
 await assert.rejects(publishQuestionBank({outputDir:dir,maxPacks:4,exportPack}),/1-to-3/);
});
test('generation failure preserves existing valid packs and is reported rather than disguised',async t=>{
 const dir=sandbox(t);
 await exportLiveStaticPack({senseId:'cooked-trouble',outputDir:dir,generate:fixtureGenerate});
 const before=readFileSync(join(dir,'packs','cooked-trouble.json'),'utf8');
 const result=await publishQuestionBank({outputDir:dir,maxPacks:1,exportPack:async()=>{throw Error('provider unavailable');}});
 assert.equal(result.available,1);assert.equal(result.published,0);assert.deepEqual(result.failures,['no-cap-truth']);
 assert.equal(readFileSync(join(dir,'packs','cooked-trouble.json'),'utf8'),before);
});

test('invalid existing packs are excluded while valid catalog entries survive',async t=>{
 const dir=sandbox(t);
 await exportLiveStaticPack({senseId:'cooked-trouble',outputDir:dir,generate:fixtureGenerate});
 const file=join(dir,'packs','cooked-trouble.json');
 const invalid=JSON.parse(readFileSync(file));invalid.generationSource='mock';
 const {writeFileSync}=await import('node:fs');writeFileSync(file,JSON.stringify(invalid));
 const result=await publishQuestionBank({outputDir:dir,maxPacks:1,exportPack:async()=>{throw Error('offline');}});
 assert.equal(result.available,0);assert.deepEqual(result.failures,['cooked-trouble']);
 assert.equal(JSON.parse(readFileSync(join(dir,'catalog.json'))).status,'awaiting-generation');
});
test('sourced exports retain exact reviewed source provenance and reject detached references',async t=>{
 const dir=sandbox(t);
 for(const sense of publicCatalog().senses.map(({senseId})=>resolveSense({senseId})).filter(s=>s.sourceCandidateId)){
  await exportLiveStaticPack({senseId:sense.senseId,outputDir:dir,generate:fixtureGenerate});
  const pack=JSON.parse(readFileSync(join(dir,'packs',sense.senseId+'.json')));
  assert.equal(pack.sourceCandidateId,sense.sourceCandidateId);
  assert.deepEqual(pack.referenceSources,sense.sources);
  validatePublishedPack(pack,sense.senseId);
  assert.throws(()=>validatePublishedPack({...pack,referenceSources:[]},sense.senseId),/provenance-mismatch/);
 }
});

test('checked-in published bank contains only valid reference-matched LLM packs with matching hashes',()=>{
 const catalog=JSON.parse(readFileSync(new URL('../../question-bank/catalog.json',import.meta.url)));
 assert.equal(catalog.generationSource,'llm');
 for(const sense of catalog.senses){
  const text=readFileSync(new URL(`../../question-bank/packs/${sense.senseId}.json`,import.meta.url),'utf8');
  assert.equal(sense.sha256,sha256(text));validatePublishedPack(JSON.parse(text),sense.senseId);
 }
});
