import test from 'node:test';
import assert from 'node:assert/strict';
import {createLlmProvider} from '../src/provider.mjs';
import {createSessionService} from '../src/sessionService.mjs';
import {mockPackage} from '../src/game.mjs';

test('valid mocked provider response can produce an LLM-labeled session',async()=>{
  const invoke=async()=>({items:structuredClone(mockPackage)});
  const generated=await createSessionService({provider:'openai-compatible',llmProvider:invoke,retries:1})();
  assert.equal(generated.session.source,'llm');assert.equal(generated.providerCalls,1);
  assert.equal(generated.session.questions.length,10);assert.equal(generated.promptVersion,'slangverse-v2.3-catalog');
});
test('schema violations fall back with explicit provenance',async()=>{
  const invoke=async()=>({items:[{bad:'pack'}]});
  const generated=await createSessionService({provider:'openai-compatible',llmProvider:invoke,retries:2})();
  assert.equal(generated.session.source,'fallback');assert.equal(generated.providerCalls,3);
  assert.equal(generated.failureReason,'llm-schema-or-rules');
});
test('provider failures and missing secrets fall back rather than leak keys',async()=>{
  let count=0;
  const invoke=async()=>{count++;throw new Error('llm-timeout')};
  const generated=await createSessionService({provider:'openai-compatible',llmProvider:invoke,retries:1})();
  assert.equal(count,2);assert.equal(generated.session.source,'fallback');assert.equal(generated.failureReason,'llm-timeout');
});
test('provider forbids unsafe remote HTTP and requires config',()=>{
 assert.throws(()=>createLlmProvider({apiKey:'secret',model:'example',baseUrl:'http://evil.example/v1'}),/unsafe-provider-url/);
 assert.throws(()=>createLlmProvider({apiKey:'',model:''}),/missing-provider-config/);
});
test('OpenAI-compatible adapter constructs request without logging secrets',async()=>{
 let url,request;
 const fetchImpl=async (u,options)=>{url=u;request=options;return {ok:true,json:async()=>({choices:[{message:{content:JSON.stringify({items:mockPackage})}}]})}};
 const provider=createLlmProvider({apiKey:'test-secret',model:'example-model',baseUrl:'https://example.org/v1',fetchImpl});
 const data=await provider({term:'cooked',meaning:'in serious trouble'});
 assert.equal(url,'https://example.org/v1/chat/completions');
 assert.equal(request.headers.Authorization,'Bearer test-secret');
 assert.equal(JSON.parse(request.body).response_format.type,'json_object');
 assert.equal(data.items.length,10);
 assert.equal(JSON.parse(request.body).max_tokens,7500);
});

test('OpenRouter adapter can request bounded low-effort generation without exposing key',async()=>{
 let request;
 const fetchImpl=async(_url,opts)=>{request=opts;return {ok:true,json:async()=>({choices:[{message:{content:JSON.stringify({items:mockPackage})}}]})}};
 const provider=createLlmProvider({apiKey:'example-only',model:'openai/gpt-oss-120b',
   baseUrl:'https://openrouter.ai/api/v1',maxTokens:8000,reasoningEffort:'low',fetchImpl});
 await provider({term:'cooked',meaning:'in serious trouble',senseId:'cooked-trouble'});
 const data=JSON.parse(request.body);
 assert.equal(data.max_tokens,8000);
 assert.deepEqual(data.reasoning,{effort:'low'});
 assert.equal(data.model,'openai/gpt-oss-120b');
 assert.equal(JSON.stringify(data).includes('example-only'),false);
});
test('failed structural validation reports safe categories and retains fallback',async()=>{
 const fn=createSessionService({provider:'openai-compatible',llmProvider:async()=>({items:[]}),retries:0});
 const result=await fn({senseId:'no-cap-truth'});
 assert.equal(result.session.source,'fallback');assert.equal(result.providerCalls,1);
 assert.deepEqual(result.validationErrors,['package-size']);
 assert.equal(result.session.senseId,'no-cap-truth');
});
