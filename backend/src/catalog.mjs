import {readFileSync} from 'node:fs';
import {captureTimesFor, sortByCapture, validateSourcedSense} from './datasetProvenance.mjs';
import {buildNormalizedSenses, datasetVersionFor, DATASET_SOURCE, DATASET_NOTE} from '../dataset/normalized.mjs';

// This ID also selects the historical hand-authored cooked mock in game.mjs.
export const DEFAULT_SENSE_ID='cooked-trouble';
const dataset=JSON.parse(readFileSync(new URL('../data/discovered_candidates.json',import.meta.url),'utf8'));
if(!Array.isArray(dataset.entries))throw Error('invalid-normalized-dataset');
const discovered=sortByCapture(dataset.entries.map(entry=>({...entry,...captureTimesFor(entry)})));
export const DATASET_VERSION=datasetVersionFor(discovered);
const normalized=buildNormalizedSenses(discovered);
const active=new Map(normalized.map(sense=>[sense.senseId,sense]));
let historical;

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

// Historical authored mocks and old packs remain resolvable, but never populate
// the active catalog or require the old candidate snapshot to be present.
function historicalSenses(){
  if(!historical){
    const senses=JSON.parse(readFileSync(new URL('../data/curated_senses.json',import.meta.url),'utf8'));
    historical=new Map(senses.map(sense=>[sense.senseId,sense]));
  }
  return historical;
}

export function listNormalizedSenses(){return structuredClone(normalized);}

export function publicCatalog(){
  return {source:DATASET_SOURCE,datasetVersion:DATASET_VERSION,
    candidateCount:discovered.length,discoveredCandidateCount:discovered.length,
    provenanceTable:'backend/data/discovered_candidates.csv',
    rejectedRowCount:discovered.length-normalized.length,expertValidated:false,
    reviewStatus:'normalized-unreviewed',order:'lastCollectedAt-desc',warning:DATASET_NOTE,
    senses:normalized.map(({senseId,term,reviewStatus,sourceRelationship,sourceCandidateId,firstSeenAt,lastCollectedAt})=>
      ({senseId,term,reviewStatus,sourceRelationship,sourceCandidateId,firstSeenAt,lastCollectedAt,playable:true}))};
}
export function listCandidates({q='',limit=25}={}){
  const query=String(q).trim().toLowerCase().slice(0,80);
  const n=Math.min(100,Math.max(1,Number.parseInt(String(limit),10)||25));
  const matches=discovered.filter(e=>!query||e.term.toLowerCase().includes(query));
  return {source:DATASET_SOURCE,datasetVersion:DATASET_VERSION,reviewStatus:'normalized-unreviewed',
    order:'lastCollectedAt-desc',totalMatches:matches.length,items:structuredClone(matches.slice(0,n))};
}
export function resolveSense(input={}){
  if(!input||typeof input!=='object'||Array.isArray(input))throw Object.assign(Error('invalid-session-options'),{status:400});
  const {senseId,term}=input;
  if((senseId!==undefined&&(typeof senseId!=='string'||senseId.length>80))||
     (term!==undefined&&(typeof term!=='string'||term.length>80)))
    throw Object.assign(Error('invalid-sense-selector'),{status:400});
  const requestedId=senseId?.trim()||null;
  const requestedTerm=term?.trim().toLowerCase()||null;
  let selected=requestedId?active.get(requestedId):
    requestedTerm?normalized.find(s=>s.term.toLowerCase()===requestedTerm):null;
  if(!selected){
    selected=requestedId?historicalSenses().get(requestedId):
      requestedTerm?[...historicalSenses().values()].find(s=>s.term.toLowerCase()===requestedTerm):
      historicalSenses().get(DEFAULT_SENSE_ID);
    if(selected?.sourceDataset==='firecrawl')validateSourcedSense(selected,discovered);
  }
  if(!selected||!selected.playable||(requestedTerm&&selected.term.toLowerCase()!==requestedTerm))
    throw Object.assign(Error('sense-not-curated-or-not-supported'),{status:400});
  return structuredClone(selected);
}
export function getCandidate(term){
  const entry=discovered.find(e=>e.term.toLowerCase()===String(term).trim().toLowerCase());
  return entry?structuredClone(entry):null;
}
