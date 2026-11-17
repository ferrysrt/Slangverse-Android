import {readFileSync} from 'node:fs';
import {validateSourcedSense} from './datasetProvenance.mjs';

export const DATASET_VERSION='slangfeed-2026-10-05';
export const DEFAULT_SENSE_ID='cooked-trouble';
export const SNAPSHOT_SOURCE='ferrysrt/slangfeed@main:data/slang_dataset.csv';
const csv=readFileSync(new URL('../data/slangfeed_snapshot.csv',import.meta.url),'utf8');
const senses=JSON.parse(readFileSync(new URL('../data/curated_senses.json',import.meta.url),'utf8'));
const discovered=JSON.parse(readFileSync(new URL('../data/discovered_candidates.json',import.meta.url),'utf8')).entries;

export function parseCandidateCsv(input){
  const lines=input.replace(/^\uFEFF/,'').trim().split(/\r?\n/);
  if(lines.shift()?.trim()!=='slang,definition,origin_context')throw Error('invalid-candidate-header');
  const entries=[],seen=new Set(),rejected=[];
  for(const [i,line] of lines.entries()){
    if(!line.trim())continue;
    const parts=line.split(',');
    if(parts.length!==3){rejected.push({line:i+2,reason:'column-count'});continue;}
    const [term,definition,originContext]=parts.map(s=>s.trim());
    const key=term.toLowerCase();
    if(!term||!definition||definition.split(/\s+/).length<5||term.length>64||definition.length>1500||seen.has(key)){
      rejected.push({line:i+2,reason:'empty-short-oversized-or-duplicate'});continue;
    }
    seen.add(key);entries.push({term,definition,originContext,reviewStatus:'unreviewed'});
  }
  return {entries,rejected};
}
const parsed=parseCandidateCsv(csv);
const byTerm=new Map(parsed.entries.map(e=>[e.term.toLowerCase(),e]));
const curated=new Map();
for(const sense of senses){
  if(!sense.playable||sense.reviewStatus!=='provisional')throw Error('invalid-curation-state');
  if(curated.has(sense.senseId))throw Error('curated-sense-catalog-mismatch');
  if(sense.sourceDataset==='firecrawl')validateSourcedSense(sense,discovered);
  else if(!byTerm.has(sense.sourceDatasetTerm.toLowerCase()))throw Error('curated-sense-catalog-mismatch');
  curated.set(sense.senseId,Object.freeze(sense));
}

export function publicCatalog(){
  return {source:discovered.length?'slangfeed-snapshot+firecrawl':'slangfeed-snapshot',datasetVersion:DATASET_VERSION,
    sourceRepository:SNAPSHOT_SOURCE,candidateCount:parsed.entries.length,
    discoveredCandidateCount:discovered.length,
    provenanceTable:'backend/data/discovered_candidates.csv',
    rejectedRowCount:parsed.rejected.length,expertValidated:false,
    warning:'Candidate definitions are NOT verified reference meanings. Curated senses are provisional and not expert-certified.',
    senses:[...curated.values()].map(({senseId,term,reviewStatus,sourceRelationship,sourceCandidateId})=>
      ({senseId,term,reviewStatus,sourceRelationship,...(sourceCandidateId?{sourceCandidateId}:{}),playable:true}))};
}
export function listCandidates({q='',limit=25}={}){
  const query=String(q).trim().toLowerCase().slice(0,80);
  const n=Math.min(100,Math.max(1,Number.parseInt(String(limit),10)||25));
  const matches=parsed.entries.filter(e=>!query||e.term.toLowerCase().includes(query));
  return {source:SNAPSHOT_SOURCE,datasetVersion:DATASET_VERSION,reviewStatus:'unreviewed',
    totalMatches:matches.length,items:matches.slice(0,n)};
}
export function resolveSense(input={}){
  if(!input||typeof input!=='object'||Array.isArray(input))throw Object.assign(Error('invalid-session-options'),{status:400});
  const {senseId,term}=input;
  if((senseId!==undefined&&(typeof senseId!=='string'||senseId.length>80))||
     (term!==undefined&&(typeof term!=='string'||term.length>80)))
    throw Object.assign(Error('invalid-sense-selector'),{status:400});
  const requestedId=senseId?.trim()||null;
  const requestedTerm=term?.trim().toLowerCase()||null;
  const selected=requestedId?curated.get(requestedId):
    requestedTerm?[...curated.values()].find(s=>s.term.toLowerCase()===requestedTerm):
    curated.get(DEFAULT_SENSE_ID);
  if(!selected||(requestedTerm&&selected.term.toLowerCase()!==requestedTerm))
    throw Object.assign(Error('sense-not-curated-or-not-supported'),{status:400});
  return selected;
}
export function getCandidate(term){return byTerm.get(String(term).toLowerCase())||null;}
