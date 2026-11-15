import {createLlmProvider} from './provider.mjs';
import {createMockSession,validatePackage,normalize} from './game.mjs';
import {resolveSense,DATASET_VERSION} from './catalog.mjs';
import {PROMPT_VERSION} from './prompts.mjs';
import {withProfilePictures} from './profilePictures.mjs';

/** Never silently substitute a different slang/sense after a provider failure. */
export function createSessionService({
  provider=process.env.LLM_PROVIDER||'mock',
  llmProvider,
  retries=Number(process.env.LLM_RETRIES??1),
  now=()=>Date.now()
}={}){
  const safeRetries=Number.isFinite(retries)?Math.max(0,Math.min(2,Math.floor(retries))):1;
  return async function generateSession(selection={}){
    const sense=resolveSense(selection);
    if(provider==='mock')return {session:createMockSession(now(),sense),generationLatencyMs:null,providerCalls:0,failureReason:null,promptVersion:null};
    if(provider!=='openai-compatible')throw Error('unsupported-llm-provider');
    const start=performance.now();let failureReason='unknown',calls=0,validationErrors=[];
    for(let attempt=0;attempt<=safeRetries;attempt++){
      calls++;
      try{
        const invoke=llmProvider||createLlmProvider();
        const json=await invoke({term:sense.term,meaning:sense.targetMeaning,senseId:sense.senseId});
        const items=Array.isArray(json)?json:json?.items;
        const errs=validatePackage(items);
        if(Array.isArray(items)&&items.some(q=>normalize(q?.slang)!==normalize(sense.term)||
           normalize(q?.targetMeaning)!==normalize(sense.targetMeaning)))errs.push('reference-mismatch');
        if(errs.length){failureReason='llm-schema-or-rules';validationErrors=[...new Set(errs)].slice(0,30);continue;}
        const session=createMockSession(now(),sense);
        session.source='llm';session.questions=withProfilePictures(items);
        session.catalogVersion=DATASET_VERSION;
        return {session,generationLatencyMs:Math.round(performance.now()-start),providerCalls:calls,failureReason:null,promptVersion:PROMPT_VERSION,validationErrors:[]};
      }catch(e){
        failureReason=(e instanceof Error&&e.message.startsWith('llm-'))?e.message:
           e?.message==='missing-provider-config'?'missing-provider-config':'provider-error';
      }
    }
    const session=createMockSession(now(),sense);
    session.source='fallback';
    return {session,generationLatencyMs:Math.round(performance.now()-start),providerCalls:calls,failureReason,promptVersion:PROMPT_VERSION,validationErrors};
  };
}
