import {existsSync,readFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {publicCatalog} from '../src/catalog.mjs';
import {PROMPT_VERSION} from '../src/prompts.mjs';
import {writePublishedCatalog,validatePublishedPack} from '../src/publishedBank.mjs';
import {exportLiveStaticPack} from './export-static-llm-bank.mjs';
export async function publishQuestionBank({outputDir=resolve('../question-bank'),maxPacks=3,force=false,exportPack=exportLiveStaticPack}={}){
 if(!Number.isInteger(maxPacks)||maxPacks<1||maxPacks>3)throw Error('max-packs-must-be-1-to-3');
 const catalog=publicCatalog();let attempted=0,published=0;const failures=[];
 for(const sense of catalog.senses){
  const file=resolve(outputDir,'packs',sense.senseId+'.json');let current=false;
  if(existsSync(file)){
   const pack=validatePublishedPack(JSON.parse(readFileSync(file,'utf8')),sense.senseId);
   current=pack.datasetVersion===catalog.datasetVersion&&pack.promptVersion===PROMPT_VERSION;
  }
  if(current&&!force)continue;
  if(attempted>=maxPacks)break;
  attempted++;
  try {await exportPack({senseId:sense.senseId,outputDir});published++;}
  catch {failures.push(sense.senseId);}
 }
 const index=writePublishedCatalog(outputDir);
 return {attempted,published,available:index.senses.length,failures,bankVersion:index.bankVersion};
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 if(!process.env.LLM_API_KEY)throw Error('Set the OPENROUTER_API_KEY repository secret; never enter it in the APK.');
 const result=await publishQuestionBank({maxPacks:Number(process.env.BANK_MAX_PACKS||3),force:process.env.BANK_FORCE==='true'});
 console.log(JSON.stringify(result));if(result.failures.length)process.exitCode=1;
}
