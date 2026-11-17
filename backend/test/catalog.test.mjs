import test from 'node:test';
import assert from 'node:assert/strict';
import {publicCatalog,parseCandidateCsv,listCandidates,resolveSense,getCandidate} from '../src/catalog.mjs';
import {createMockSession,validatePackage,publicQuestion} from '../src/game.mjs';
import {createSessionService} from '../src/sessionService.mjs';

test('SlangFeed snapshot imports as candidates, never approved meanings',()=>{
 const catalog=publicCatalog();
 assert.equal(catalog.candidateCount,200);
 assert.equal(catalog.expertValidated,false);
 assert.equal(catalog.rejectedRowCount,0);
 assert.equal(listCandidates({q:'cooked'}).items[0].reviewStatus,'unreviewed');
 assert.equal(getCandidate('Cooked').definition.includes('exhaustion'),true);
 assert.equal(resolveSense({senseId:'cooked-trouble'}).targetMeaning,'in serious trouble');
 assert.equal(catalog.senses.some(s=>'targetMeaning' in s),false);
});
test('CSV parser rejects malformed rows and deduplicates',()=>{
 const r=parseCandidateCsv('slang,definition,origin_context\nA,one two three four five,x\nA,one two three four five,x\nbad,broken,x\n');
 assert.equal(r.entries.length,1);assert.equal(r.rejected.length,2);
});
test('unknown or invalid senses cannot run a session',async()=>{
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
test('Firecrawl senses retain source-backed provisional provenance through the catalog',()=>{
 for(const entry of publicCatalog().senses){
  const sense=resolveSense({senseId:entry.senseId});
  if(sense.sourceDataset!=='firecrawl')continue;
  assert.equal(resolveSense({term:entry.term}).term,entry.term);
  assert.equal(entry.playable,true);
  assert.equal(entry.reviewStatus,'provisional');
  assert.equal(entry.sourceRelationship,'source-backed-provisional');
  assert.equal(entry.sourceCandidateId,sense.sourceCandidateId);
  assert.ok(entry.sourceCandidateId);
  assert.ok(sense.targetMeaning);
  assert.ok(sense.normalizedDefinition);
  assert.ok(sense.normalizationModel);
  assert.ok(sense.referenceNote);
  assert.ok(sense.review.reviewedBy);
  assert.ok(Number.isFinite(Date.parse(sense.review.reviewedAt)));
  assert.ok(Array.isArray(sense.sources)&&sense.sources.length>0);
  for(const source of sense.sources){
   assert.ok(['http:','https:'].includes(new URL(source.url).protocol));
   assert.ok(source.title);
   assert.ok(source.quote);
   assert.ok(Number.isFinite(Date.parse(source.collectedAt)));
   assert.equal(source.collectionMethod,'firecrawl');
   assert.match(source.contentSha256,/^[a-f0-9]{64}$/);
  }
  assert.equal('targetMeaning' in entry,false);
 }
});
test('injected LLM receives correct selected target meaning and retains sense after fallback',async()=>{
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
