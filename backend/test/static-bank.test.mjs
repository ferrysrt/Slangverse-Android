import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync,mkdirSync,readFileSync,writeFileSync,readdirSync,existsSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {buildDemoBank,exportDemoBank} from '../scripts/export-static-demo-bank.mjs';
import {validatePackage,createMockSession,normalize} from '../src/game.mjs';
import {resolveLegacySense,legacyCatalog,listLegacySenses} from '../src/publishedBank.mjs';

function sandbox(t){const dir=mkdtempSync(join(tmpdir(),'demo-bank-test-'));t.after(()=>rmSync(dir,{recursive:true,force:true}));return dir;}

test('static GET bank contains 3 distinct provisional demo packs', () => {
  const {index,packs}=buildDemoBank();
  assert.equal(index.schemaVersion,1);
  assert.equal(index.generationSource,'mock');
  assert.equal(index.expertValidated,false);
  assert.equal(index.datasetVersion,legacyCatalog().datasetVersion);
  assert.equal(index.candidateCount,listLegacySenses().length);
  assert.deepEqual(Object.keys(packs).sort(),['cooked-trouble','lowkey-subtle','no-cap-truth']);
  for(const [senseId, pack] of Object.entries(packs)) {
    assert.equal(pack.schemaVersion,1);
    assert.equal(pack.senseId,senseId);
    assert.equal(pack.generationSource,'mock');
    assert.equal(pack.expertValidated,false);
    assert.equal(pack.referenceStatus,'provisional');
    assert.equal(pack.datasetVersion,index.datasetVersion);
    assert.equal(pack.questions.length,10);
    assert.equal(new Set(pack.questions.map(q=>q.contextId)).size,10);
    assert.equal(validatePackage(pack.questions.map(q=>({
      ...q,targetMeaning:q.options[q.correctIndex]
    }))).length,0);
    const reference=resolveLegacySense({senseId});
    for (const q of pack.questions) {
      assert.equal(q.slang,reference.term);
      assert.equal(normalize(q.options[q.correctIndex]),normalize(reference.targetMeaning));
      assert.equal('targetMeaning' in q,false);
      assert.ok(Number.isInteger(q.correctIndex));
      assert.ok(q.explanation.length > 0);
    }
  }
});

test('demo questions are the exact authored historical packages, not normalized same-term meanings',()=>{
 const {index,packs}=buildDemoBank();
 assert.deepEqual(index.senses.map(s=>s.senseId),['cooked-trouble','no-cap-truth','lowkey-subtle']);
 for(const entry of index.senses){
  const reference=resolveLegacySense({senseId:entry.senseId});
  const expected=createMockSession(Date.now(),reference).questions.map(({targetMeaning,...q})=>q);
  assert.deepEqual(packs[entry.senseId].questions,expected);
  assert.equal(entry.reviewStatus,'provisional');
 }
 assert.equal(resolveLegacySense({senseId:'cooked-trouble'}).sourceRelationship,'conflict');
 assert.equal(resolveLegacySense({senseId:'cooked-trouble'}).targetMeaning,'in serious trouble');
});

test('demo file export writes all three historical packs with honest mock metadata and answer keys',t=>{
 const dir=sandbox(t),expected=buildDemoBank();
 assert.deepEqual(exportDemoBank(dir),{senses:Object.keys(expected.packs),questions:30});
 assert.deepEqual(JSON.parse(readFileSync(join(dir,'catalog.json'),'utf8')),expected.index);
 assert.equal(readdirSync(join(dir,'packs')).length,3);
 for(const [senseId,pack] of Object.entries(expected.packs))
  assert.deepEqual(JSON.parse(readFileSync(join(dir,'packs',senseId+'.json'),'utf8')),pack);
});

test('demo export rejects schema 2 without modifying any mixed or historical files',t=>{
 const dir=sandbox(t);
 mkdirSync(join(dir,'packs'));
 const files=[['catalog.json',JSON.stringify({schemaVersion:2,source:'github-static',generationSource:'llm',
  senses:[],packs:[{packId:'mixed-existing',questionCount:10,sha256:'a'.repeat(64)}]})],
  ['packs/mixed-existing.json','existing mixed bytes'],['packs/cooked-trouble.json','existing historical bytes']];
 for(const [name,bytes] of files)writeFileSync(join(dir,name),bytes);
 assert.throws(()=>exportDemoBank(dir),/cannot-overwrite-schema-2/);
 for(const [name,bytes] of files)assert.equal(readFileSync(join(dir,name),'utf8'),bytes);
 assert.equal(readdirSync(join(dir,'packs')).length,2);
});

test('demo export fails closed on corrupt catalog bytes rather than overwriting them',t=>{
 const dir=sandbox(t);
 writeFileSync(join(dir,'catalog.json'),'{invalid');
 assert.throws(()=>exportDemoBank(dir),/invalid-existing-bank-catalog/);
 assert.equal(readFileSync(join(dir,'catalog.json'),'utf8'),'{invalid');
 assert.equal(existsSync(join(dir,'packs')),false);
});
