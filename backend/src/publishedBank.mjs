import {existsSync,mkdirSync,readFileSync,writeFileSync,renameSync,unlinkSync} from 'node:fs';
import {resolve} from 'node:path';
import {createHash,randomUUID} from 'node:crypto';
import {validateSourcedSense} from './datasetProvenance.mjs';
import {validatePackage,normalize} from './game.mjs';
export const sha256=text=>createHash('sha256').update(text).digest('hex');
const safeSenseId=value=>typeof value==='string'&&value.length<=80&&/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(value);
let legacySenses;

// Historical meanings are explicit references, never the active normalized catalog.
export function listLegacySenses(){
 if(!legacySenses){
  const senses=JSON.parse(readFileSync(new URL('../data/curated_senses.json',import.meta.url),'utf8'));
  if(!Array.isArray(senses))throw Error('invalid-legacy-reference-catalog');
  const seen=new Set();
  const candidates=senses.some(sense=>sense?.sourceCandidateId)?
   JSON.parse(readFileSync(new URL('../data/discovered_candidates.json',import.meta.url),'utf8')).entries:[];
  for(const sense of senses){
   if(!safeSenseId(sense?.senseId)||seen.has(sense.senseId)||!sense.playable||sense.reviewStatus!=='provisional'||
      typeof sense.term!=='string'||!normalize(sense.term)||typeof sense.targetMeaning!=='string'||!normalize(sense.targetMeaning))
    throw Error('invalid-legacy-reference-catalog');
   if(sense.sourceDataset==='firecrawl'||sense.sourceCandidateId)validateSourcedSense(sense,candidates);
   seen.add(sense.senseId);
  }
  legacySenses=senses;
 }
 return structuredClone(legacySenses);
}
export function resolveLegacySense({senseId}={}){
 const sense=listLegacySenses().find(sense=>sense.senseId===senseId);
 if(!sense)throw Error('unknown-legacy-sense');
 return sense;
}
export function legacyCatalog(){
 const references=listLegacySenses();
 return {datasetVersion:'legacy-curated-'+sha256(JSON.stringify(references)).slice(0,24),
  candidateCount:references.length,discoveredCandidateCount:references.filter(sense=>sense.sourceCandidateId).length,
  expertValidated:false,
  senses:references.map(({senseId,term,reviewStatus,sourceRelationship,sourceCandidateId})=>
   ({senseId,term,reviewStatus,sourceRelationship,...(sourceCandidateId?{sourceCandidateId}:{}),playable:true}))};
}

// Fail before generation or writes; a legacy tool must never downgrade a mixed bank.
export function assertLegacyPublicationAllowed(outputDir){
 const file=resolve(outputDir,'catalog.json');
 if(!existsSync(file))return;
 let catalog;try{catalog=JSON.parse(readFileSync(file,'utf8'));}catch{throw Error('invalid-existing-bank-catalog');}
 if(catalog?.schemaVersion===2)throw Error('legacy-publication-cannot-overwrite-schema-2');
 if(catalog?.schemaVersion!==1)throw Error('unsupported-existing-bank-schema');
}

export function validatePublishedPack(pack,senseId){
 const sense=resolveLegacySense({senseId});
 if(pack?.schemaVersion!==1||pack.senseId!==senseId||pack.generationSource!=='llm')throw Error('not-a-published-llm-pack');
 if(sense.sourceCandidateId&&(pack.sourceCandidateId!==sense.sourceCandidateId||
    pack.normalizedDefinition!==sense.normalizedDefinition||JSON.stringify(pack.referenceSources)!==JSON.stringify(sense.sources)))
   throw Error('published-reference-provenance-mismatch');
 const questions=pack.questions;
 let errors;
 try{
  errors=validatePackage(questions?.map(q=>({...q,targetMeaning:sense.targetMeaning})));
 }catch{throw Error('invalid-published-pack');}
 if(errors.length||questions.some(q=>normalize(q.slang)!==normalize(sense.term)||
    (q.targetMeaning!==undefined&&normalize(q.targetMeaning)!==normalize(sense.targetMeaning))))throw Error('invalid-published-pack');
 return pack;
}
export function writePublishedCatalog(outputDir){
 assertLegacyPublicationAllowed(outputDir);
 const catalog=legacyCatalog();const senses=[];
 for(const sense of catalog.senses){
  const file=resolve(outputDir,'packs',sense.senseId+'.json');
  if(!existsSync(file))continue;
  const bytes=readFileSync(file);
  try {validatePublishedPack(JSON.parse(bytes.toString('utf8')),sense.senseId);} catch {continue;}
  senses.push({...sense,sha256:sha256(bytes)});
 }
 const index={schemaVersion:1,datasetVersion:catalog.datasetVersion,source:'github-static',generationSource:'llm',
  status:senses.length?'ready':'awaiting-generation',candidateCount:catalog.candidateCount,
  discoveredCandidateCount:catalog.discoveredCandidateCount,expertValidated:false,
  bankVersion:sha256(JSON.stringify({datasetVersion:catalog.datasetVersion,senses})).slice(0,16),senses};
 mkdirSync(outputDir,{recursive:true});
 const temporary=resolve(outputDir,'.legacy-catalog-'+randomUUID()+'.tmp');
 try{
  writeFileSync(temporary,JSON.stringify(index,null,2)+'\n',{flag:'wx'});
  assertLegacyPublicationAllowed(outputDir);
  renameSync(temporary,resolve(outputDir,'catalog.json'));
 }finally{if(existsSync(temporary))unlinkSync(temporary);}
 return index;
}
