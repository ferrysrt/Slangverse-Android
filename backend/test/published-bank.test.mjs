import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync,readFileSync,rmSync,existsSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {exportLiveStaticPack} from '../scripts/export-static-llm-bank.mjs';
import {publishQuestionBank} from '../scripts/publish-question-bank.mjs';
import {validatePublishedPack,sha256} from '../src/publishedBank.mjs';
import {createMockSession} from '../src/game.mjs';
import {resolveSense} from '../src/catalog.mjs';
import {PROMPT_VERSION} from '../src/prompts.mjs';
const fixtureGenerate=async({senseId})=>({session:{...createMockSession(Date.now(),resolveSense({senseId})),source:'llm'},promptVersion:PROMPT_VERSION});
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
 assert.equal(result.available,3);assert.equal(calls,3);
 result=await publishQuestionBank({outputDir:dir,maxPacks:3,exportPack});
 assert.equal(result.attempted,0);assert.equal(calls,3);
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
