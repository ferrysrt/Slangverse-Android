import test, {after} from 'node:test';
import assert from 'node:assert/strict';

process.env.SLANGVERSE_NO_LISTEN = '1';
const {default: server} = await import('../src/server.mjs');
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const root = `http://127.0.0.1:${server.address().port}`;
after(()=>new Promise(resolve=>server.close(resolve)));

async function json(method,path,body){
 const res=await fetch(root+path,{method,headers:{'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)});
 return {status:res.status,data:await res.json()};
}

test('health and curated trending mark mock data honestly',async()=>{
 const health=await json('GET','/api/health');assert.equal(health.data.ok,true);assert.equal(health.data.mode,'mock');assert.equal(health.data.llmConnected,false);assert.equal(health.data.providerConfigured,false);
 const trending=await json('GET','/api/trending');assert.equal(trending.data.source,'curated');assert.equal(trending.data.trending.length,3);
});
test('complete HTTP session creation, grading, progress and duplicate protection',async()=>{
 const {status,data}=await json('POST','/api/sessions',{});
 assert.equal(status,201);assert.equal(data.source,'mock');assert.equal(data.generationLatencyMs,null);
 assert.equal(data.questions.length,10);const first=data.questions[0];
 assert.ok(first.post.comments.length>=1);
 assert.equal('correctIndex' in first,false);assert.equal('targetMeaning' in first,false);
 assert.equal('explanation' in first,false);
 const progress0=await json('GET',`/api/sessions/${data.id}`);
 assert.equal(progress0.data.answeredCount,0);
 const grade=await json('POST',`/api/sessions/${data.id}/answers`,{questionId:first.id,selectedIndex:1});
 assert.equal(grade.data.correct,true);assert.equal(grade.data.totalXp,80);
 const progress1=await json('GET',`/api/sessions/${data.id}`);
 assert.equal(progress1.data.answeredCount,1);assert.equal(progress1.data.correctCount,1);
 const duplicate=await json('POST',`/api/sessions/${data.id}/answers`,{questionId:first.id,selectedIndex:1});
 assert.equal(duplicate.status,409);
 const missing=await json('GET','/api/sessions/00000000-0000-0000-0000-000000000000');
 assert.equal(missing.status,404);
});
test('validation errors are HTTP errors, not crashes',async()=>{
 const {data}=await json('POST','/api/sessions',{});
 const wrong=await json('POST',`/api/sessions/${data.id}/answers`,{questionId:data.questions[0].id,selectedIndex:7});
 assert.equal(wrong.status,400);assert.equal(wrong.data.error,'invalid-option');
 const missing=await json('POST',`/api/sessions/${data.id}/answers`,{questionId:'missing',selectedIndex:0});
 assert.equal(missing.status,404);
 const url=root+`/api/sessions/${data.id}/answers`;
 const malformed=await fetch(url,{method:'POST',headers:{'Content-Type':'application/json'},body:'{invalid'});
 assert.equal(malformed.status,400);
});
test('leaderboard never pretends simulated questions are ranked competitively',async()=>{
 const {data}=await json('GET','/api/leaderboard');assert.equal(data.competitive,false);
 assert.equal(data.source,'demo');assert.deepEqual(data.players,[]);
});

test('catalog lists provisional senses and preserves answer key secrecy',async()=>{
 const {status,data}=await json('GET','/api/catalog');
 assert.equal(status,200);assert.equal(data.candidateCount,200);
 assert.equal(data.expertValidated,false);
 assert.ok(data.senses.some(s=>s.senseId==='no-cap-truth'));
 assert.equal(data.senses.some(s=>'targetMeaning' in s),false);
 const candidates=await json('GET','/api/catalog/candidates?q=lowkey&limit=1');
 assert.equal(candidates.data.items.length,1);
 assert.equal(candidates.data.items[0].reviewStatus,'unreviewed');
});
test('create HTTP session for a selected sense without exposing answer keys',async()=>{
 const {status,data}=await json('POST','/api/sessions',{senseId:'lowkey-subtle'});
 assert.equal(status,201);assert.equal(data.senseId,'lowkey-subtle');
 assert.equal(data.referenceStatus,'provisional');assert.equal(data.questions.length,10);
 assert.equal(data.questions[0].slang,'lowkey');
 assert.equal('correctIndex' in data.questions[0],false);
 assert.equal('targetMeaning' in data.questions[0],false);
});
test('reject unsupported and mismatched sense selectors',async()=>{
 const invalid=await json('POST','/api/sessions',{senseId:'does-not-exist'});
 assert.equal(invalid.status,400);
 const mismatch=await json('POST','/api/sessions',{senseId:'cooked-trouble',term:'no cap'});
 assert.equal(mismatch.status,400);
});
