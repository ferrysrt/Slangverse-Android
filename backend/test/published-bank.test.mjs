import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync,readFileSync,writeFileSync,rmSync,existsSync,readdirSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {exportLiveStaticPack} from '../scripts/export-static-llm-bank.mjs';
import {publishQuestionBank} from '../scripts/publish-question-bank.mjs';
import {validatePublishedPack,writePublishedCatalog,sha256,listLegacySenses,resolveLegacySense,legacyCatalog} from '../src/publishedBank.mjs';
import {createMockSession,normalize} from '../src/game.mjs';
import {candidateIdFor} from '../src/datasetProvenance.mjs';
import {PROMPT_VERSION} from '../src/prompts.mjs';
import {writeImmutableMixedPack,writeMixedCatalog} from '../src/mixedBank.mjs';
import {validatePublishedMixedPack,isSafeBankId,MIXED_PROMPT_VERSION,MIXED_CONTEXT_CATEGORIES} from '../src/mixedQuestions.mjs';

// Synthetic provider fixture only; production never relabels a mock as LLM.
// Authored mocks use explicit historical meanings, never active normalized senses.
const fixtureGenerate=async({senseId})=>{
 const sense=resolveLegacySense({senseId}),session=createMockSession();
 session.senseId=senseId;session.source='llm';
 session.questions=session.questions.map(q=>({...q,slang:sense.term,targetMeaning:sense.targetMeaning,
  questionComment:undefined,options:q.options.map((option,index)=>index===q.correctIndex?sense.targetMeaning:option)}));
 return {session,promptVersion:PROMPT_VERSION};
};
function sandbox(t){const dir=mkdtempSync(join(tmpdir(),'bank-test-'));t.after(()=>rmSync(dir,{recursive:true,force:true}));return dir;}
const readJson=file=>JSON.parse(readFileSync(file,'utf8'));

// Only checked-in artifacts may have been converted by a Windows Git checkout.
// Generated files and all production/network integrity checks hash exact bytes.
function artifactText(file,{checkout=false}={}){
 const text=readFileSync(file,'utf8');
 return checkout&&process.platform==='win32'?text.replace(/\r\n/g,'\n'):text;
}
function assertBank(directory,options={}){
 const catalog=readJson(join(directory,'catalog.json'));
 assert.ok([1,2].includes(catalog.schemaVersion));
 assert.equal(catalog.source,'github-static');
 assert.equal(catalog.generationSource,'llm');
 assert.equal(catalog.expertValidated,false);
 assert.ok(Array.isArray(catalog.senses));
 assert.equal(new Set(catalog.senses.map(s=>s.senseId)).size,catalog.senses.length);
 for(const entry of catalog.senses){
  const reference=resolveLegacySense({senseId:entry.senseId});
  assert.equal(entry.term,reference.term);
  assert.equal(entry.reviewStatus,reference.reviewStatus);
  assert.equal(entry.sourceRelationship,reference.sourceRelationship);
  assert.equal(entry.sourceCandidateId,reference.sourceCandidateId);
  const text=artifactText(join(directory,'packs',entry.senseId+'.json'),options);
  assert.equal(entry.sha256,sha256(text));
  validatePublishedPack(JSON.parse(text),entry.senseId);
 }
 if(catalog.schemaVersion===2){
  assert.ok(Array.isArray(catalog.packs));
  assert.equal(new Set(catalog.packs.map(p=>p.packId)).size,catalog.packs.length);
  for(const entry of catalog.packs){
   assert.equal(isSafeBankId(entry.packId),true);
   assert.equal(entry.questionCount,10);
   const text=artifactText(join(directory,'packs',entry.packId+'.json'),options);
   assert.equal(entry.sha256,sha256(text));
   validatePublishedMixedPack(JSON.parse(text),{packId:entry.packId});
  }
 }else assert.equal(catalog.packs?.length??0,0);
 return catalog;
}
function mixedFixture(){
 const packId='mixed-legacy-regression';
 const historical=['cooked-trouble','no-cap-truth'].map(senseId=>resolveLegacySense({senseId}));
 const references=historical.map(sense=>{
  const sourceCandidateId=candidateIdFor(sense.term,sense.targetMeaning);
  const quote=`Synthetic test reference containing the term "${sense.term}" only.`;
  return {senseId:'normalized-'+sourceCandidateId,term:sense.term,targetMeaning:sense.targetMeaning,
   normalizedDefinition:sense.targetMeaning,sourceCandidateId,
   referenceSources:[{url:'https://example.invalid/fixture',title:'Synthetic reference fixture',quote,
    collectedAt:new Date().toISOString(),collectionMethod:'firecrawl',
    contentSha256:sha256('# Synthetic fixture document\n\n'+quote)}]};
 });
 const sessions=historical.map(sense=>createMockSession(Date.now(),sense));
 return {schemaVersion:2,packId,generationSource:'llm',promptVersion:MIXED_PROMPT_VERSION,
  generatorModel:'fixture-model',generatedAt:new Date().toISOString(),referenceStatus:'normalized-unreviewed',
  datasetVersion:'fixture-normalized-dataset',expertValidated:false,references,
  questions:Array.from({length:10},(_,i)=>{
   const q=sessions[i%2].questions[i],reference=references[i%2];
   return {...q,id:`${packId}-q${i+1}`,senseId:reference.senseId,contextCategory:MIXED_CONTEXT_CATEGORIES[i%9],
    options:q.options.map((option,index)=>index===q.correctIndex?reference.targetMeaning:option)};
  })};
}
function addMixedPack(outputDir){
 const entry=writeImmutableMixedPack(mixedFixture(),{outputDir});
 return writeMixedCatalog({outputDir,datasetVersion:'fixture-normalized-dataset',candidateCount:2,additions:[entry]});
}

test('legacy references retain their historical meanings, provenance and immutable accessor boundary',()=>{
 const references=listLegacySenses();
 assert.deepEqual(references.map(s=>s.senseId),['cooked-trouble','no-cap-truth','lowkey-subtle',
  'glow-up-improvement','rizz-flirting','iykyk-inside-reference']);
 assert.ok(references.every(s=>s.reviewStatus==='provisional'&&s.playable));
 assert.equal(references[0].targetMeaning,'in serious trouble');
 assert.equal(references[0].sourceRelationship,'conflict');
 assert.equal(resolveLegacySense({senseId:'no-cap-truth'}).targetMeaning,'speaking truthfully without exaggeration');
 const before=legacyCatalog();
 references[0].targetMeaning='a changed definition';
 references.find(s=>s.sources).sources[0].quote='changed citation';
 assert.deepEqual(legacyCatalog(),before);
 assert.throws(()=>resolveLegacySense({senseId:'normalized-'+'0'.repeat(24)}),/unknown-legacy-sense/);
});

test('publishing a second historical sense preserves the first and hashes the exact public bytes',async t=>{
 const dir=sandbox(t);
 for(const senseId of ['cooked-trouble','no-cap-truth'])await exportLiveStaticPack({senseId,outputDir:dir,generate:fixtureGenerate});
 const catalog=assertBank(dir);
 assert.equal(catalog.status,'ready');
 assert.deepEqual(catalog.senses.map(s=>s.senseId),['cooked-trouble','no-cap-truth']);
 assert.equal(catalog.datasetVersion,legacyCatalog().datasetVersion);
});
test('mock/fallback output cannot be exported as a published LLM pack',async t=>{
 const dir=sandbox(t);
 await assert.rejects(exportLiveStaticPack({senseId:'cooked-trouble',outputDir:dir,generate:async()=>({session:createMockSession(),failureReason:'test'})}),/did not produce an LLM/);
 assert.equal(existsSync(join(dir,'packs','cooked-trouble.json')),false);
});
test('bounded legacy generation visits only historical references and skips already valid current packs',async t=>{
 const dir=sandbox(t),references=listLegacySenses(),visited=[];
 const exportPack=async opts=>{visited.push(opts.senseId);return exportLiveStaticPack({...opts,generate:fixtureGenerate});};
 let result=await publishQuestionBank({outputDir:dir,maxPacks:1,exportPack});
 assert.equal(result.attempted,1);assert.equal(result.available,1);
 result=await publishQuestionBank({outputDir:dir,maxPacks:1,exportPack});
 assert.equal(result.available,2);assert.equal(visited.length,2);
 result=await publishQuestionBank({outputDir:dir,maxPacks:3,exportPack});
 assert.equal(result.available,Math.min(5,references.length));
 assert.equal(visited.length,Math.min(5,references.length));
 while(result.available<references.length){
  const before=result.available;
  result=await publishQuestionBank({outputDir:dir,maxPacks:3,exportPack});
  assert.ok(result.available>before,'a bounded run must make progress across valid historical references');
 }
 assert.deepEqual(visited,references.map(s=>s.senseId));
 // A global active-dataset version is not a legacy reference freshness check.
 const file=join(dir,'packs','cooked-trouble.json'),pack=readJson(file);
 pack.datasetVersion='another-generation-dataset';writeFileSync(file,JSON.stringify(pack));
 result=await publishQuestionBank({outputDir:dir,maxPacks:3,exportPack});
 assert.equal(result.attempted,0);assert.equal(visited.length,references.length);
 assertBank(dir);
 await assert.rejects(publishQuestionBank({outputDir:dir,maxPacks:4,exportPack}),/1-to-3/);
});
test('generation failure preserves existing valid packs and is reported rather than disguised',async t=>{
 const dir=sandbox(t);
 await exportLiveStaticPack({senseId:'cooked-trouble',outputDir:dir,generate:fixtureGenerate});
 const before=readFileSync(join(dir,'packs','cooked-trouble.json'),'utf8');
 const result=await publishQuestionBank({outputDir:dir,maxPacks:1,exportPack:async()=>{throw Error('provider unavailable');}});
 assert.equal(result.available,1);assert.equal(result.published,0);assert.deepEqual(result.failures,['no-cap-truth']);
 assert.equal(readFileSync(join(dir,'packs','cooked-trouble.json'),'utf8'),before);
 assertBank(dir);
});

test('invalid existing packs are excluded while valid historical catalog entries survive',async t=>{
 const dir=sandbox(t);
 await exportLiveStaticPack({senseId:'cooked-trouble',outputDir:dir,generate:fixtureGenerate});
 const file=join(dir,'packs','cooked-trouble.json');
 const invalid=readJson(file);invalid.generationSource='mock';writeFileSync(file,JSON.stringify(invalid));
 const result=await publishQuestionBank({outputDir:dir,maxPacks:1,exportPack:async()=>{throw Error('offline');}});
 assert.equal(result.available,0);assert.deepEqual(result.failures,['cooked-trouble']);
 assert.equal(readJson(join(dir,'catalog.json')).status,'awaiting-generation');
});
test('sourced legacy exports retain exact reviewed source provenance and reject every detached citation field',async t=>{
 const dir=sandbox(t),references=listLegacySenses().filter(s=>s.sourceCandidateId);
 assert.ok(references.length>0,'the provenance checks must actually exercise sourced references');
 for(const sense of references){
  await exportLiveStaticPack({senseId:sense.senseId,outputDir:dir,generate:fixtureGenerate});
  const pack=readJson(join(dir,'packs',sense.senseId+'.json'));
  assert.equal(pack.sourceCandidateId,sense.sourceCandidateId);
  assert.equal(pack.normalizedDefinition,sense.normalizedDefinition);
  assert.deepEqual(pack.referenceSources,sense.sources);
  validatePublishedPack(pack,sense.senseId);
  for(const mutate of [p=>{p.sourceCandidateId='detached';},p=>{p.normalizedDefinition='changed definition';},
    p=>{p.referenceSources=[];},...['url','title','quote','collectedAt','collectionMethod','contentSha256']
     .map(key=>p=>{p.referenceSources[0][key]='changed';})]){
   const detached=structuredClone(pack);mutate(detached);
   assert.throws(()=>validatePublishedPack(detached,sense.senseId),/provenance-mismatch/);
  }
 }
 assertBank(dir);
});

test('legacy validation rejects wrong meanings, slang, keys and shapes rather than trusting the generated key',async t=>{
 const dir=sandbox(t);
 await exportLiveStaticPack({senseId:'cooked-trouble',outputDir:dir,generate:fixtureGenerate});
 const pack=readJson(join(dir,'packs','cooked-trouble.json'));
 for(const mutate of [p=>{p.questions[0].options[p.questions[0].correctIndex]='a different normalized meaning';},
   p=>{p.questions[0].targetMeaning='a different normalized meaning';},p=>{p.questions[0].slang='different';},
   p=>{p.questions[0].correctIndex=4;},p=>{p.questions[0].options={some:'not-callable'};},
   p=>{p.questions.pop();},p=>{p.questions[0].difficulty='hard';}]){
  const invalid=structuredClone(pack);mutate(invalid);
  assert.throws(()=>validatePublishedPack(invalid,'cooked-trouble'),/invalid-published-pack/);
 }
 assert.throws(()=>validatePublishedPack(pack,'normalized-'+'0'.repeat(24)),/unknown-legacy-sense/);
 // A structurally complete pack with a wrong reference key is not counted as published.
 const result=await publishQuestionBank({outputDir:dir,maxPacks:1,force:true,exportPack:async()=>{
  const invalid=structuredClone(pack);invalid.questions[0].options[invalid.questions[0].correctIndex]='a different meaning';
  writeFileSync(join(dir,'packs','cooked-trouble.json'),JSON.stringify(invalid));
 }});
 assert.equal(result.published,0);assert.deepEqual(result.failures,['cooked-trouble']);
 assert.equal(result.available,0);
});

test('legacy catalog hashes exact output bytes without LF normalization in production',async t=>{
 const dir=sandbox(t);
 await exportLiveStaticPack({senseId:'cooked-trouble',outputDir:dir,generate:fixtureGenerate});
 const file=join(dir,'packs','cooked-trouble.json'),lf=readFileSync(file,'utf8');
 const crlf=lf.replace(/\n/g,'\r\n');writeFileSync(file,crlf);
 const catalog=writePublishedCatalog(dir);
 assert.equal(catalog.senses[0].sha256,sha256(Buffer.from(crlf)));
 assert.notEqual(catalog.senses[0].sha256,sha256(lf));
 assertBank(dir);
});

test('legacy publisher and catalog writer reject schema 2 before calls or changes to mixed and legacy files',async t=>{
 const dir=sandbox(t);
 await exportLiveStaticPack({senseId:'cooked-trouble',outputDir:dir,generate:fixtureGenerate});
 addMixedPack(dir);assertBank(dir);
 const catalogFile=join(dir,'catalog.json'),before=readFileSync(catalogFile);
 const files=readdirSync(join(dir,'packs')).map(name=>[name,readFileSync(join(dir,'packs',name))]);
 let calls=0;
 await assert.rejects(publishQuestionBank({outputDir:dir,force:true,exportPack:async()=>{calls++;}}),/cannot-overwrite-schema-2/);
 assert.throws(()=>writePublishedCatalog(dir),/cannot-overwrite-schema-2/);
 assert.equal(calls,0);assert.deepEqual(readFileSync(catalogFile),before);
 for(const [name,bytes] of files)assert.deepEqual(readFileSync(join(dir,'packs',name)),bytes);
 assert.equal(readdirSync(dir).some(name=>name.endsWith('.tmp')),false);
});

test('legacy publisher fails closed on corrupt or unknown catalogs before invoking generation',async t=>{
 const dir=sandbox(t);let calls=0;
 for(const text of ['{malformed',JSON.stringify({schemaVersion:3,senses:[]})]){
  writeFileSync(join(dir,'catalog.json'),text);
  await assert.rejects(publishQuestionBank({outputDir:dir,exportPack:async()=>{calls++;}}),/existing-bank/);
  assert.equal(readFileSync(join(dir,'catalog.json'),'utf8'),text);
 }
 assert.equal(calls,0);
});

test('published-bank checks cover both legacy entries and schema-2 mixed packs, rejecting invalid counts, hashes and references',async t=>{
 const dir=sandbox(t);
 await exportLiveStaticPack({senseId:'cooked-trouble',outputDir:dir,generate:fixtureGenerate});
 const catalog=addMixedPack(dir);assertBank(dir);
 const catalogFile=join(dir,'catalog.json'),entry=catalog.packs[0];
 const invalidCount=structuredClone(catalog);invalidCount.packs[0].questionCount=9;
 writeFileSync(catalogFile,JSON.stringify(invalidCount));assert.throws(()=>assertBank(dir));
 const invalidHash=structuredClone(catalog);invalidHash.packs[0].sha256='0'.repeat(64);
 writeFileSync(catalogFile,JSON.stringify(invalidHash));assert.throws(()=>assertBank(dir));
 const packFile=join(dir,'packs',entry.packId+'.json'),pack=readJson(packFile);
 pack.questions[0].senseId='normalized-'+'f'.repeat(24);
 const bytes=JSON.stringify(pack);writeFileSync(packFile,bytes);
 catalog.packs[0].sha256=sha256(bytes);writeFileSync(catalogFile,JSON.stringify(catalog));
 assert.throws(()=>assertBank(dir),/invalid-published-mixed/);
});

test('checked-in published bank validates schema-1 historical references and schema-2 mixed packs when present',()=>{
 const directory=fileURLToPath(new URL('../../question-bank/',import.meta.url));
 const catalog=assertBank(directory,{checkout:true});
 assert.ok(catalog.senses.length+(catalog.packs?.length??0)>0);
 for(const entry of catalog.senses){
  const pack=JSON.parse(artifactText(join(directory,'packs',entry.senseId+'.json'),{checkout:true}));
  const reference=resolveLegacySense({senseId:entry.senseId});
  assert.ok(pack.questions.every(q=>normalize(q.options[q.correctIndex])===normalize(reference.targetMeaning)));
 }
});
