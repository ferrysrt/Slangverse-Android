import {createLlmProvider} from './provider.mjs';
import {mockPackage,createMockSession,validatePackage,slang,normalize} from './game.mjs';
import {PROMPT_VERSION} from './prompts.mjs';

export function createSessionService({
  provider=process.env.LLM_PROVIDER||'mock',
  llmProvider,
  retries=Number(process.env.LLM_RETRIES??1),
  now=()=>Date.now()
}={}){
  const safeRetries=Number.isFinite(retries)?Math.max(0,Math.min(2,Math.floor(retries))):1;
  return async function generateSession(){
    if(provider==='mock')return {session:createMockSession(now()),generationLatencyMs:null,providerCalls:0,failureReason:null,promptVersion:null};
    if(provider!=='openai-compatible')throw new Error('unsupported-llm-provider');
    const start=performance.now();let failureReason='unknown',calls=0;
    for(let attempt=0;attempt<=safeRetries;attempt++){
      calls++;
      try{
        const invoke=llmProvider||createLlmProvider();
        const json=await invoke({term:slang.term,meaning:slang.targetMeaning});
        const items=Array.isArray(json)?json:json?.items;
        const errs=validatePackage(items);
        if(Array.isArray(items)&&items.some(q=>normalize(q?.slang)!==normalize(slang.term)||normalize(q?.targetMeaning)!==normalize(slang.targetMeaning)))errs.push('reference-mismatch');
        if(errs.length){failureReason='llm-schema-or-rules';continue;}
        const session=createMockSession(now());
        session.source='llm';session.questions=structuredClone(items);
        return {session,generationLatencyMs:Math.round(performance.now()-start),providerCalls:calls,failureReason:null,promptVersion:PROMPT_VERSION};
      }catch(e){failureReason=(e instanceof Error&&e.message.startsWith('llm-'))?e.message:e?.message==='missing-provider-config'?'missing-provider-config':'provider-error';}
    }
    const session=createMockSession(now());session.source='fallback';
    session.questions=mockPackage;
    return {session,generationLatencyMs:Math.round(performance.now()-start),providerCalls:calls,failureReason,promptVersion:PROMPT_VERSION};
  };
}
