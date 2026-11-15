import {existsSync,mkdirSync,readFileSync,writeFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {createHash} from 'node:crypto';
import {publicCatalog,resolveSense} from './catalog.mjs';
import {validatePackage,normalize} from './game.mjs';
export const sha256=text=>createHash('sha256').update(text).digest('hex');
export function validatePublishedPack(pack,senseId){
 const sense=resolveSense({senseId});
 if(pack?.schemaVersion!==1||pack.senseId!==senseId||pack.generationSource!=='llm')throw Error('not-a-published-llm-pack');
 const questions=pack.questions;
 const errors=validatePackage(questions?.map(q=>({...q,targetMeaning:q.options?.[q.correctIndex]})));
 if(errors.length||questions.some(q=>normalize(q.slang)!==normalize(sense.term)||normalize(q.options[q.correctIndex])!==normalize(sense.targetMeaning)))throw Error('invalid-published-pack');
 return pack;
}
export function writePublishedCatalog(outputDir){
 const catalog=publicCatalog();const senses=[];
 for(const sense of catalog.senses){
  const file=resolve(outputDir,'packs',sense.senseId+'.json');
  if(!existsSync(file))continue;
  const text=readFileSync(file,'utf8');
  validatePublishedPack(JSON.parse(text),sense.senseId);
  senses.push({...sense,sha256:sha256(text)});
 }
 const index={schemaVersion:1,datasetVersion:catalog.datasetVersion,source:'github-static',generationSource:'llm',
  status:senses.length?'ready':'awaiting-generation',candidateCount:catalog.candidateCount,expertValidated:false,
  bankVersion:sha256(JSON.stringify(senses)).slice(0,16),senses};
 mkdirSync(outputDir,{recursive:true});
 writeFileSync(resolve(outputDir,'catalog.json'),JSON.stringify(index,null,2)+'\n');
 return index;
}
