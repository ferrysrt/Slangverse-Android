import {mkdir,writeFile} from 'node:fs/promises';
import {createSessionService} from '../src/sessionService.mjs';
import {createLlmProvider} from '../src/provider.mjs';
import {validatePackage} from '../src/game.mjs';
import {resolveSense,DATASET_VERSION} from '../src/catalog.mjs';
import {PROMPT_VERSION} from '../src/prompts.mjs';

const out=new URL('../reports/live-llm-smoke.json',import.meta.url);
const started=new Date().toISOString();
const senseId=process.env.SLANGVERSE_TEST_SENSE_ID||'no-cap-truth';
const model=process.env.LLM_MODEL||'openai/gpt-oss-120b';
const report={type:'slangverse-llm-generation-smoke',startedAt:started,
  status:'failed',model,senseId,datasetVersion:DATASET_VERSION,promptVersion:PROMPT_VERSION,
  provider:'openrouter',liveRequest:true,expertValidated:false,
  note:'This measures schema/format and latency only. Never conflate with semantic correctness or empirical difficulty.',
  apiKeyIncluded:false,rawQuestionsIncluded:false};
let exit=1;
try{
  const sense=resolveSense({senseId});
  if(!process.env.LLM_API_KEY)throw Error('missing-openrouter-secret');
  const provider=createLlmProvider({
    apiKey:process.env.LLM_API_KEY,model,
    baseUrl:'https://openrouter.ai/api/v1',
    timeoutMs:120000,maxTokens:8000,reasoningEffort:'low'
  });
  const service=createSessionService({provider:'openai-compatible',llmProvider:provider,retries:0});
  const result=await service({senseId});
  const session=result.session;
  const valid=result.session.source==='llm'&&
    session.questions.length===10&&validatePackage(session.questions).length===0;
  Object.assign(report,{
    status:valid?'passed':'fallback',
    sessionSource:session.source,providerCalls:result.providerCalls,
    latencyMs:result.generationLatencyMs,
    failureCategory:result.failureReason,
    validationErrorCodes:result.validationErrors||[],
    questionCount:session.source==='llm'?session.questions.length:null,
    distinctContextCount:session.source==='llm'?new Set(session.questions.map(q=>q.contextId)).size:null,
    difficultyCounts:session.source==='llm'?{
      easy:session.questions.filter(q=>q.difficulty==='easy').length,
      medium:session.questions.filter(q=>q.difficulty==='medium').length,
      hard:session.questions.filter(q=>q.difficulty==='hard').length
    }:null,
    referenceStatus:sense.reviewStatus,
    structuralChecksPassed:valid
  });
  if(valid)exit=0;
}catch(e){
  report.failureCategory=/^(missing-|bad-|unsafe-|invalid-)/.test(e?.message||'')?e.message:'smoke-run-error';
}
report.completedAt=new Date().toISOString();
await mkdir(new URL('../reports/',import.meta.url),{recursive:true});
await writeFile(out,JSON.stringify(report,null,2)+'\n',{mode:0o600});
console.log(JSON.stringify(report));
if(exit!==0)process.exitCode=1;
