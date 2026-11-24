import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp, mkdir, copyFile, readFile, writeFile, rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {pathToFileURL} from 'node:url';
import {candidateIdFor, mergeCandidates, candidateTableCsv, captureTimesFor} from '../src/datasetProvenance.mjs';
import {buildNormalizedSenses, datasetVersionFor} from '../dataset/normalized.mjs';
import {migrateDataset, migrateDatasetFiles} from '../dataset/migrate.mjs';

const oldTime='2026-01-01T00:00:00.000Z';
const middleTime='2026-02-01T00:00:00.000Z';
const newTime='2026-03-01T00:00:00.000Z';
const definition='a fictional meaning used only in dataset fixtures';
const source=(collectedAt=oldTime)=>({
 url:'https://example.org/fixture',title:'Dataset fixture',
 quote:'Fixture slang means a fictional meaning used only in tests.',
 collectedAt,collectionMethod:'firecrawl',contentSha256:'a'.repeat(64)
});
const candidate=(patch={})=>{
 const entry={term:'fixture slang',definition,normalizedDefinition:definition,
  normalizationModel:'fixture/model',sourceEvidenceStatus:'literal-excerpt-unreviewed',
  reviewStatus:'unreviewed',sources:[source()],...patch};
 return {candidateId:candidateIdFor(entry.term,entry.normalizedDefinition),...entry};
};

async function temporaryDirectory(t){
 const dir=await mkdtemp(join(tmpdir(),'slang-normalized-'));
 t.after(()=>rm(dir,{recursive:true,force:true}));
 return dir;
}

test('provenance-valid normalized definitions are auto-eligible without a manual review record',()=>{
 const entry=candidate();
 const [sense]=buildNormalizedSenses([entry]);
 assert.equal(sense.senseId,'normalized-'+entry.candidateId);
 assert.equal(sense.targetMeaning,entry.normalizedDefinition);
 assert.equal(sense.sourceCandidateId,entry.candidateId);
 assert.equal(sense.normalizationModel,'fixture/model');
 assert.equal(sense.reviewStatus,'normalized-unreviewed');
 assert.equal(sense.playable,true);
 assert.deepEqual(sense.sources,entry.sources);
 assert.equal(sense.firstSeenAt,oldTime);
 assert.equal(sense.lastCollectedAt,oldTime);
 assert.equal('review' in sense,false);
 sense.sources[0].quote='mutated';
 assert.notEqual(entry.sources[0].quote,'mutated');
});

test('invalid identity or incomplete provenance never becomes playable automatically',()=>{
 const entry=candidate();
 for(const patch of [
  {candidateId:'forged'},{term:''},{normalizedDefinition:''},{normalizationModel:''},
  {normalizationModel:'  '},{normalizationModel:{}},{sources:[]},
  {sourceEvidenceStatus:'literal-term-appearance-only'},{sourceEvidenceStatus:'legacy-unverified'}
 ])assert.deepEqual(buildNormalizedSenses([{...entry,...patch}]),[]);
 for(const patch of [
  {url:'javascript:alert(1)'},{url:'bad-url'},{title:''},{quote:''},{collectedAt:null},
  {collectedAt:'unknown'},{collectionMethod:'model-claimed'},{contentSha256:'fake'}
 ])assert.deepEqual(buildNormalizedSenses([{...entry,sources:[{...source(),...patch}]}]),[]);
 assert.deepEqual(buildNormalizedSenses([{...entry,sources:[null]}]),[]);
 assert.deepEqual(buildNormalizedSenses([{...entry,sources:[source(),{...source(),collectedAt:null}]}]),[]);
});

test('incremental multi-sense merging retains oldest captures and derives honest newest-first recency',()=>{
 const first=candidate({firstSeenAt:'2099-01-01',lastCollectedAt:'2099-01-01'});
 const different=candidate({definition:'another fictional meaning for the same test expression',
  normalizedDefinition:'another fictional meaning for the same test expression',sources:[source(middleTime)]});
 const incoming=candidate({sources:[source(newTime),source(oldTime)]});
 const before=structuredClone(first);
 const merged=mergeCandidates([first,different],[incoming]);
 assert.equal(merged.length,2);
 assert.deepEqual(first,before);
 assert.equal(merged[0].candidateId,first.candidateId);
 assert.equal(merged[0].firstSeenAt,oldTime);
 assert.equal(merged[0].lastCollectedAt,newTime);
 assert.deepEqual(merged[0].sources,[source(oldTime),source(newTime)]);
 assert.equal(merged[1].candidateId,different.candidateId);
 assert.notEqual(merged[0].candidateId,merged[1].candidateId);
 const senses=buildNormalizedSenses(merged);
 assert.equal(senses.length,2);
 assert.equal(senses[0].term,senses[1].term);
 assert.notEqual(senses[0].senseId,senses[1].senseId);
 assert.deepEqual(mergeCandidates(merged,[]),merged);
 // Capture ordering is global, not grouped by the candidate's latest capture.
 const rows=candidateTableCsv(merged).trim().split('\n').map(row=>row.split(','));
 assert.deepEqual(rows.slice(1).map(row=>row[6]),[newTime,middleTime,oldTime]);
 assert.equal(rows[1][1],senses[0].senseId);
 assert.equal(rows[3][1],senses[0].senseId);
 assert.equal(rows[1][12],oldTime);
 assert.equal(rows[1][13],newTime);
 assert.equal(rows[1][15],'a'.repeat(64));
});

test('offline migration preserves weaker historical evidence and never invents capture times',()=>{
 const legacy={term:'old slang',definition,sourceUrl:'https://example.org/legacy',
  sourceTitle:'Legacy fixture',evidenceSnippet:'Old slang was mentioned in this historical fixture.',
  sourceEvidenceStatus:'literal-term-appearance-only'};
 const original={version:1,collectedAt:'2099-01-01',entries:[legacy,candidate()]};
 const before=structuredClone(original);
 const migrated=migrateDataset(original);
 assert.deepEqual(original,before);
 assert.equal(migrated.version,3);
 assert.equal(migrated.stage,'normalized_dataset');
 assert.doesNotMatch(migrated.note,/review each source|never auto-promote/i);
 assert.deepEqual(migrateDataset(migrated),migrated);
 const unknown=migrated.entries.find(e=>e.term==='old slang');
 assert.equal(unknown.firstSeenAt,null);
 assert.equal(unknown.lastCollectedAt,null);
 assert.equal(unknown.sources[0].collectedAt,null);
 assert.equal(unknown.sources[0].contentSha256,null);
 assert.equal(unknown.normalizationModel,null);
 assert.equal(unknown.sourceEvidenceStatus,legacy.sourceEvidenceStatus);
 assert.equal(unknown.reviewStatus,'unreviewed');
 assert.equal(migrated.entries.at(-1),unknown);
 assert.equal(buildNormalizedSenses(migrated.entries).length,1);
 const row=candidateTableCsv(migrated.entries).trim().split('\n').at(-1).split(',');
 assert.equal(row[1],'');
 assert.equal(row[6],'');
 assert.equal(row[12],'');
 assert.equal(row[13],'');
 assert.deepEqual(captureTimesFor({sources:[source('bad-time'),source(null)]}),
  {firstSeenAt:null,lastCollectedAt:null});
});

test('dataset version depends on own content rather than JSON formatting or candidate order',()=>{
 const entries=migrateDataset({entries:[candidate(),candidate({term:'other fixture'})]}).entries;
 const version=datasetVersionFor(entries);
 assert.match(version,/^firecrawl-normalized-[a-f0-9]{24}$/);
 assert.equal(datasetVersionFor(JSON.parse(JSON.stringify(entries,null,2).replace(/\n/g,'\r\n'))),version);
 assert.equal(datasetVersionFor([...entries].reverse()),version);
 const reordered=entries.map(entry=>Object.fromEntries(Object.entries(entry).reverse()));
 assert.equal(datasetVersionFor(reordered),version);
 for(const patch of [{normalizationModel:'different/model'},{sources:[source(newTime)]},
  {normalizedDefinition:'a different normalized meaning changes the own dataset content'}]){
  assert.notEqual(datasetVersionFor([{...entries[0],...patch},entries[1]]),version);
 }
});

test('offline file migration writes matching JSON and CSV and is idempotent',async t=>{
 const dir=await temporaryDirectory(t);
 const outputUrl=join(dir,'discovered_candidates.json');
 const csvOutputUrl=join(dir,'discovered_candidates.csv');
 await writeFile(outputUrl,JSON.stringify({version:2,entries:[candidate()]}));
 const migrated=await migrateDatasetFiles({outputUrl,csvOutputUrl});
 const saved=await readFile(outputUrl,'utf8');
 const csv=await readFile(csvOutputUrl,'utf8');
 assert.deepEqual(JSON.parse(saved),migrated);
 assert.equal(csv,candidateTableCsv(migrated.entries));
 await migrateDatasetFiles({outputUrl,csvOutputUrl});
 assert.equal(await readFile(outputUrl,'utf8'),saved);
 assert.equal(await readFile(csvOutputUrl,'utf8'),csv);
});

test('active catalog loads without snapshot, curated_senses, or sense_reviews files',async t=>{
 const dir=await temporaryDirectory(t);
 for(const folder of ['src','dataset','data'])await mkdir(join(dir,folder));
 for(const file of ['src/catalog.mjs','src/datasetProvenance.mjs','dataset/normalized.mjs']){
  await copyFile(new URL('../'+file,import.meta.url),join(dir,file));
 }
 const entries=migrateDataset({entries:[candidate(),candidate({
  normalizedDefinition:'a second fictional meaning for the same test expression',sources:[source(newTime)]
 })]}).entries;
 await writeFile(join(dir,'data/discovered_candidates.json'),JSON.stringify({version:3,stage:'normalized_dataset',entries}));
 const catalog=await import(pathToFileURL(join(dir,'src/catalog.mjs')).href);
 assert.equal(catalog.publicCatalog().senses.length,2);
 assert.equal(catalog.listNormalizedSenses().length,2);
 assert.equal(catalog.listCandidates().totalMatches,2);
 assert.deepEqual(catalog.listCandidates().items,entries);
 assert.equal(catalog.resolveSense({term:'fixture slang'}).lastCollectedAt,newTime);
 for(const sense of catalog.listNormalizedSenses()){
  assert.deepEqual(catalog.resolveSense({senseId:sense.senseId}),sense);
 }
 assert.equal(catalog.DATASET_VERSION,datasetVersionFor(entries));
});

test('weekly glossary refresh keeps Git read-only and replay needs no provider',async()=>{
 const workflow=await readFile(new URL('../../.github/workflows/slang-candidates.yml',import.meta.url),'utf8');
 assert.match(workflow,/cron: '0 3 \* \* 1'/);
 assert.match(workflow,/permissions:\s+contents: read/);
 assert.match(workflow,/default: replay/);
 assert.match(workflow,/FIRECRAWL_API_KEY: \$\{\{ inputs.mode != 'replay' && secrets.FIRECRAWL_API_KEY \|\| '' \}\}/);
 assert.match(workflow,/RESEARCH_WORKER_SUPABASE_KEY/);
 assert.match(workflow,/node dataset\/refresh-deterministic\.mjs --import --replay/);
 assert.match(workflow,/node dataset\/refresh-deterministic\.mjs --import\r?\n/);
 assert.doesNotMatch(workflow,/OPENROUTER|git (add|push)|dataset:refresh/);
});
