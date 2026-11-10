import test from 'node:test';
import assert from 'node:assert/strict';
import {createLlmProvider} from '../src/provider.mjs';
import {createSessionService} from '../src/sessionService.mjs';
import {mockPackage} from '../src/game.mjs';

test('valid mocked provider response can produce an LLM-labeled session',async()=>{
  const invoke=async()=>({items:structuredClone(mockPackage)});
  const generated=await createSessionService({provider:'openai-compatible',llmProvider:invoke,retries:1})();
  assert.equal(generated.session.source,'llm');assert.equal(generated.providerCalls,1);
  assert.equal(generated.session.questions.length,10);assert.equal(generated.promptVersion,'slangverse-v2.2');
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
});
