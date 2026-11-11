import { createGenerationPrompt } from './prompts.mjs';

/**
 * Generic OpenAI-compatible chat-completions adapter. API keys never reach Android.
 * Actual provider/model support for JSON response format varies and is NOT yet tested live.
 */
export function createLlmProvider({
  apiKey=process.env.LLM_API_KEY,
  model=process.env.LLM_MODEL,
  baseUrl=process.env.LLM_API_BASE_URL||'https://api.openai.com/v1',
  timeoutMs=Number(process.env.LLM_TIMEOUT_MS||20000),
  fetchImpl=fetch
}={}){
  if(!apiKey||!model)throw new Error('missing-provider-config');
  const u=new URL(baseUrl);
  if(u.protocol!=='https:'&&!(u.protocol==='http:'&&['127.0.0.1','localhost'].includes(u.hostname)))throw new Error('unsafe-provider-url');
  if(!Number.isFinite(timeoutMs)||timeoutMs<1000||timeoutMs>120000)throw new Error('bad-provider-timeout');
  const endpoint=u.toString().replace(/\/$/,'')+'/chat/completions';
  return async ({term,meaning,senseId})=>{
    let response;
    try{
      response=await fetchImpl(endpoint,{
        method:'POST',signal:AbortSignal.timeout(timeoutMs),
        headers:{'Content-Type':'application/json',Authorization:`Bearer ${apiKey}`},
        body:JSON.stringify({model,temperature:0.7,response_format:{type:'json_object'},messages:[
          {role:'system',content:'Generate structured fictional English slang context questions as strict JSON. Never include surrounding prose.'},
          {role:'user',content:createGenerationPrompt({term,meaning,senseId})}
        ]})
      });
    }catch(e){throw new Error(e?.name==='TimeoutError'?'llm-timeout':'llm-network');}
    if(!response.ok)throw new Error(`llm-http-${response.status}`);
    let outer;try{outer=await response.json()}catch{throw new Error('llm-bad-provider-json')}
    let content=outer?.choices?.[0]?.message?.content;
    if(typeof content!=='string')throw new Error('llm-missing-content');
    try{return JSON.parse(content)}catch{throw new Error('llm-invalid-generated-json')}
  };
}
