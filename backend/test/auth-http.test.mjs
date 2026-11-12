import test, {after} from 'node:test';
import assert from 'node:assert/strict';

const token='Slangverse-strong-private-test-token-12345';
process.env.SLANGVERSE_NO_LISTEN='1';
process.env.SLANGVERSE_REQUIRE_TOKEN='true';
process.env.SLANGVERSE_CLIENT_TOKEN=token;
process.env.SLANGVERSE_MAX_LLM_SESSIONS='1';
process.env.LLM_PROVIDER='openai-compatible';
process.env.LLM_RETRIES='0';
delete process.env.LLM_API_KEY; // No external calls or usage charges in this test.

const {default:server}=await import('../src/server.mjs');
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const root=`http://127.0.0.1:${server.address().port}`;
after(()=>new Promise(resolve=>server.close(resolve)));
async function call(path,{method='GET',auth,body}={}){
  const response=await fetch(root+path,{
    method,
    headers:{'Content-Type':'application/json',...(auth?{Authorization:auth}:{})},
    ...(body?{body:JSON.stringify(body)}:{})
  });
  return {status:response.status,body:await response.json()};
}
test('health is public while gameplay requires the configured token',async()=>{
  assert.equal((await call('/api/health')).status,200);
  assert.equal((await call('/api/catalog')).status,401);
  assert.equal((await call('/api/catalog',{auth:'Bearer wrong'})).status,401);
  const catalog=await call('/api/catalog',{auth:`Bearer ${token}`});
  assert.equal(catalog.status,200);
  assert.equal(catalog.body.expertValidated,false);
});
test('authorized single generation falls back safely and second call is limited',async()=>{
  const opts={method:'POST',auth:`Bearer ${token}`,body:{senseId:'no-cap-truth'}};
  assert.equal((await call('/api/sessions',{method:'POST',body:{}})).status,401);
  const first=await call('/api/sessions',opts);
  assert.equal(first.status,201);
  assert.equal(first.body.source,'fallback');
  assert.equal(first.body.questions.length,10);
  const second=await call('/api/sessions',opts);
  assert.equal(second.status,429);
  assert.equal(second.body.error,'generation-limit-reached');
});
