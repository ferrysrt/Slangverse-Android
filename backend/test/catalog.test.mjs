import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {
 publicCatalog,parseCandidateCsv,listCandidates,resolveSense,getCandidate,listNormalizedSenses,DATASET_VERSION
} from '../src/catalog.mjs';
import {candidateIdFor,validateCandidate,captureTimesFor,sortByCapture} from '../src/datasetProvenance.mjs';
import {createMockSession,validatePackage,publicQuestion} from '../src/game.mjs';
import {createSessionService} from '../src/sessionService.mjs';

const dataset=JSON.parse(readFileSync(new URL('../data/discovered_candidates.json',import.meta.url),'utf8'));
const candidates=sortByCapture(dataset.entries.map(entry=>({...entry,...captureTimesFor(entry)})));
const eligible=candidates.filter(entry=>{
 try{validateCandidate(entry);return true;}catch{return false;}
});

test('active catalog and candidates use the own normalized dataset, not the historical snapshot',()=>{
 const catalog=publicCatalog();
 assert.equal(catalog.source,'firecrawl-normalized');
 assert.equal(catalog.candidateCount,dataset.entries.length);
 assert.equal(catalog.discoveredCandidateCount,dataset.entries.length);
 assert.equal(catalog.expertValidated,false);
 assert.equal(catalog.rejectedRowCount,candidates.length-eligible.length);
 assert.equal(catalog.order,'lastCollectedAt-desc');
 assert.match(catalog.warning,/not popularity/);
 assert.match(catalog.warning,/unreviewed and not expert validated/);
 assert.equal('sourceRepository' in catalog,false);
 const listing=listCandidates({limit:100});
 assert.equal(listing.totalMatches,candidates.length);
 assert.deepEqual(listing.items,candidates.slice(0,100));
 for(const q of [' LOWKEY ','cooked','not-in-dataset']){
  const expected=candidates.filter(entry=>entry.term.toLowerCase().includes(q.trim().toLowerCase()));
  const matches=listCandidates({q,limit:100});
  assert.equal(matches.totalMatches,expected.length);
  assert.deepEqual(matches.items,expected.slice(0,100));
 }
 assert.deepEqual(getCandidate('Cooked'),candidates.find(e=>e.term.toLowerCase()==='cooked')??null);
 assert.equal(catalog.senses.some(s=>'targetMeaning' in s),false);
 assert.equal(catalog.senses.some(s=>s.senseId==='cooked-trouble'),false);

});

test('listNormalizedSenses exports every eligible meaning and its original provenance',()=>{
 const senses=listNormalizedSenses();
 assert.equal(senses.length,eligible.length);
 assert.deepEqual(publicCatalog().senses.map(s=>s.senseId),senses.map(s=>s.senseId));
 assert.match(DATASET_VERSION,/^firecrawl-normalized-[a-f0-9]{24}$/);
 assert.equal(publicCatalog().datasetVersion,DATASET_VERSION);
 assert.equal(listCandidates().datasetVersion,DATASET_VERSION);
 for(const sense of senses){
  const candidate=dataset.entries.find(e=>e.candidateId===sense.sourceCandidateId);
  validateCandidate(candidate);
  assert.match(sense.senseId,/^normalized-[a-f0-9]{24}$/);
  assert.equal(sense.senseId,'normalized-'+candidateIdFor(sense.term,sense.normalizedDefinition));
  assert.equal(sense.targetMeaning,candidate.normalizedDefinition);
  assert.equal(sense.normalizedDefinition,candidate.normalizedDefinition);
  assert.equal(sense.normalizationModel,candidate.normalizationModel);
  assert.equal(sense.reviewStatus,'normalized-unreviewed');
  assert.equal(sense.playable,true);
  assert.equal('review' in sense,false);
  assert.deepEqual(sense.sources,candidate.sources);
  assert.equal(sense.firstSeenAt,candidate.firstSeenAt);
  assert.equal(sense.lastCollectedAt,candidate.lastCollectedAt);
  assert.deepEqual(resolveSense({senseId:sense.senseId}),sense);
  assert.equal(resolveSense({term:sense.term}).term,sense.term);
 }
 assert.ok(resolveSense({term:'cooked'}).senseId.startsWith('normalized-'));
});

test('catalog accessors do not expose mutable dataset state',()=>{
 const [sense]=listNormalizedSenses();
 const original=structuredClone(sense);
 sense.targetMeaning='changed';
 sense.sources[0].quote='changed';
 assert.deepEqual(resolveSense({senseId:original.senseId}),original);
 const candidate=getCandidate(original.term);
 candidate.sources.length=0;
 assert.ok(getCandidate(original.term).sources.length);
 const listing=listCandidates();
 listing.items[0].normalizedDefinition='changed';
 assert.notEqual(listCandidates().items[0].normalizedDefinition,'changed');
});

test('historical CSV parser stays a pure helper that rejects malformed and duplicate rows',()=>{
 const r=parseCandidateCsv('slang,definition,origin_context\nA,one two three four five,x\nA,one two three four five,x\nbad,broken,x\n');
 assert.equal(r.entries.length,1);assert.equal(r.rejected.length,2);
});
test('unknown or invalid senses cannot run a session',()=>{
 for(const x of [{senseId:'unknown'},{term:'invented'},{senseId:'no-cap-truth',term:'cooked'},{senseId:20},[]])
  assert.throws(()=>resolveSense(x),/sense|session/);
});
test('the three legacy hand-authored senses have 10 safe, unique-context mock questions',()=>{
 for(const senseId of ['cooked-trouble','no-cap-truth','lowkey-subtle']){
  const sense=resolveSense({senseId}),s=createMockSession(Date.now(),sense);
  assert.equal(s.senseId,senseId);
  assert.equal(s.questions.length,10);
  assert.deepEqual(validatePackage(s.questions),[]);
  assert.equal(s.questions.every(q=>q.slang===sense.term&&q.targetMeaning===sense.targetMeaning),true);
  const safe=publicQuestion(s.questions[0]);
  assert.equal('correctIndex' in safe,false);
  assert.equal('targetMeaning' in safe,false);
 }
});
test('all historical curated IDs and conflict notes remain available only for compatibility',()=>{
 const historical=JSON.parse(readFileSync(new URL('../data/curated_senses.json',import.meta.url),'utf8'));
 for(const sense of historical)assert.deepEqual(resolveSense({senseId:sense.senseId}),sense);
 assert.equal(resolveSense({senseId:'cooked-trouble'}).sourceRelationship,'conflict');
 assert.equal(resolveSense().senseId,'cooked-trouble');
});
test('normalized selections pass their full definition to generation without a historical mock',async()=>{
 const [sense]=listNormalizedSenses();
 let received;
 const fn=createSessionService({provider:'openai-compatible',retries:0,
  llmProvider:async input=>{received=input;return {items:[]}}});
 await assert.rejects(fn({senseId:sense.senseId}),{
  message:'missing-hand-authored-fallback-for-sense',status:503,senseId:sense.senseId
 });
 assert.deepEqual(received,{term:sense.term,meaning:sense.normalizedDefinition,senseId:sense.senseId});
});

test('injected LLM receives correct selected historical meaning and retains sense after fallback',async()=>{
 let received=null;
 const broken=async x=>{received=x;return {items:[]}};
 const fn=createSessionService({provider:'openai-compatible',llmProvider:broken,retries:0});
 const result=await fn({senseId:'no-cap-truth'});
 assert.equal(received.term,'no cap');
 assert.equal(received.meaning,'speaking truthfully without exaggeration');
 assert.equal(result.session.senseId,'no-cap-truth');
 assert.equal(result.session.source,'fallback');
 assert.equal(result.session.questions[0].slang,'no cap');
});
