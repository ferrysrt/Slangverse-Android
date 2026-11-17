import test from 'node:test';
import assert from 'node:assert/strict';
import {DATASET_VERSION,resolveSense} from '../src/catalog.mjs';
import {createMockSession,difficulties,grade,validatePackage} from '../src/game.mjs';
import {withProfilePictures} from '../src/profilePictures.mjs';
import {PROMPT_VERSION} from '../src/prompts.mjs';
import {createSessionService} from '../src/sessionService.mjs';

const timestamp=Date.UTC(2026,10,17);
const sourcedSense=Object.freeze({
  senseId:'test-sourced-aura-presence',term:'aura',
  targetMeaning:'a distinctive personal presence',
  reviewStatus:'provisional',playable:true,
  sourceDataset:'firecrawl',sourceRelationship:'source-backed-provisional'
});
const selection={senseId:sourcedSense.senseId};
const senseResolver=input=>{
  assert.deepEqual(input,selection);
  return sourcedSense;
};

function sourcedQuestions(sense=sourcedSense){
  return withProfilePictures(difficulties.map((difficulty,index)=>({
    id:`${sense.senseId}-${index+1}`,slang:sense.term,targetMeaning:sense.targetMeaning,
    difficulty,contextId:`sourced-context-${index+1}`,
    post:{
      id:`sourced-post-${index+1}`,handle:`@sourced${index+1}`,displayName:`Source Fixture ${index+1}`,
      content:`Everyone mentioned ${sense.term} after the entrance at event ${index+1}.`,
      comments:[{handle:`@reply${index+1}`,text:'Did you see the clip from the entrance?'}]
    },
    questionComment:`What does ${sense.term} mean in this comment?`,
    question:`What does '${sense.term}' mean here?`,
    options:[sense.targetMeaning,'a schedule change','a technical error','an empty venue'],
    correctIndex:0,explanation:`Here, '${sense.term}' means '${sense.targetMeaning}'.`
  })));
}

for(const responseShape of ['items','array']){
  test(`valid sourced LLM ${responseShape} creates a session without a hand-authored mock`,async()=>{
    assert.throws(()=>createMockSession(timestamp,sourcedSense),/no-curated-demo-for-sense/);
    const items=sourcedQuestions(),original=structuredClone(items);
    assert.deepEqual(validatePackage(items),[]);
    let received,calls=0,resolutions=0;
    const generate=createSessionService({
      provider:'openai-compatible',retries:2,now:()=>timestamp,
      senseResolver:input=>{resolutions++;return senseResolver(input);},
      llmProvider:async input=>{
        calls++;received=input;
        return responseShape==='items'?{items}:items;
      }
    });
    const result=await generate(selection),session=result.session;
    assert.deepEqual(received,{
      term:sourcedSense.term,meaning:sourcedSense.targetMeaning,senseId:sourcedSense.senseId
    });
    assert.equal(resolutions,1);
    assert.equal(calls,1);
    assert.equal(result.providerCalls,1);
    assert.equal(result.failureReason,null);
    assert.equal(result.promptVersion,PROMPT_VERSION);
    assert.deepEqual(result.validationErrors,[]);
    assert.ok(Number.isFinite(result.generationLatencyMs));
    assert.equal(session.source,'llm');
    assert.equal(session.senseId,sourcedSense.senseId);
    assert.equal(session.catalogVersion,DATASET_VERSION);
    assert.equal(session.referenceStatus,'provisional');
    assert.equal(session.createdAt,new Date(timestamp).toISOString());
    assert.equal(session.expiresAt,timestamp+30*60*1000);
    assert.match(session.id,/^[0-9a-f-]{36}$/);
    assert.ok(session.answered instanceof Map);
    assert.equal(session.answered.size,0);
    assert.deepEqual(session.questions,items);
    assert.notEqual(session.questions,items);
    assert.deepEqual(items,original);
    const question=session.questions[0];
    assert.equal(grade(session,question.id,question.correctIndex).correct,true);
  });
}

test('mock provider rejects a sourced sense with an explicit unavailable-fallback error',async()=>{
  let providerCalls=0;
  const generate=createSessionService({
    provider:'mock',senseResolver,now:()=>timestamp,
    llmProvider:async()=>{providerCalls++;return {items:sourcedQuestions()};}
  });
  await assert.rejects(generate(selection),{
    message:'missing-hand-authored-fallback-for-sense',status:503,senseId:sourcedSense.senseId
  });
  assert.equal(providerCalls,0);
});

const failures=[
  ['provider timeout',async()=>{throw Error('llm-timeout');}],
  ['missing provider configuration',async()=>{throw Error('missing-provider-config');}],
  ['unexpected provider error',async()=>{throw Error('private-provider-detail');}],
  ['invalid package',async()=>({items:[]})],
  ['wrong slang',async()=>({items:sourcedQuestions({...sourcedSense,term:'other slang'})})],
  ['wrong meaning',async()=>({items:sourcedQuestions({...sourcedSense,targetMeaning:'a different reference meaning'})})]
];
for(const [name,invoke] of failures){
  test(`sourced sense ${name} exhausts retries without fabricating a fallback`,async()=>{
    let calls=0;
    const generate=createSessionService({
      provider:'openai-compatible',senseResolver,retries:1,now:()=>timestamp,
      llmProvider:async input=>{calls++;return invoke(input);}
    });
    await assert.rejects(generate(selection),{
      message:'missing-hand-authored-fallback-for-sense',status:503,senseId:sourcedSense.senseId
    });
    assert.equal(calls,2);
  });
}

test('a sourced sense can recover from a failed attempt without a mock',async()=>{
  const items=sourcedQuestions();let calls=0;
  const generate=createSessionService({
    provider:'openai-compatible',senseResolver,retries:1,now:()=>timestamp,
    llmProvider:async()=>{calls++;return {items:calls===1?[]:items};}
  });
  const result=await generate(selection);
  assert.equal(result.providerCalls,2);
  assert.equal(result.failureReason,null);
  assert.deepEqual(result.validationErrors,[]);
  assert.equal(result.session.source,'llm');
  assert.equal(result.session.senseId,sourcedSense.senseId);
  assert.deepEqual(result.session.questions,items);
});

for(const senseId of ['cooked-trouble','no-cap-truth','lowkey-subtle']){
  test(`legacy ${senseId} retains its mock and same-sense provider fallbacks`,async()=>{
    const sense=resolveSense({senseId}),expected=createMockSession(timestamp,sense);
    const mocked=await createSessionService({provider:'mock',now:()=>timestamp})({senseId});
    assert.equal(mocked.session.source,'mock');
    assert.equal(mocked.session.senseId,senseId);
    assert.equal(mocked.session.catalogVersion,DATASET_VERSION);
    assert.equal(mocked.session.referenceStatus,'provisional');
    assert.equal(mocked.generationLatencyMs,null);
    assert.equal(mocked.providerCalls,0);
    assert.equal(mocked.failureReason,null);
    assert.equal(mocked.promptVersion,null);
    assert.deepEqual(mocked.session.questions,expected.questions);
    for(const [failureReason,invoke,validationErrors] of [
      ['llm-schema-or-rules',async()=>({items:[]}),['package-size']],
      ['llm-timeout',async()=>{throw Error('llm-timeout');},[]]
    ]){
      const result=await createSessionService({
        provider:'openai-compatible',llmProvider:invoke,retries:1,now:()=>timestamp
      })({senseId});
      assert.equal(result.session.source,'fallback');
      assert.equal(result.session.senseId,senseId);
      assert.equal(result.session.catalogVersion,DATASET_VERSION);
      assert.equal(result.session.referenceStatus,'provisional');
      assert.equal(result.providerCalls,2);
      assert.equal(result.failureReason,failureReason);
      assert.equal(result.promptVersion,PROMPT_VERSION);
      assert.deepEqual(result.validationErrors,validationErrors);
      assert.deepEqual(result.session.questions,expected.questions);
      assert.deepEqual(validatePackage(result.session.questions),[]);
    }
  });
}
