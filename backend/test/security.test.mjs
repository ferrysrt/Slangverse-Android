import test from 'node:test';
import assert from 'node:assert/strict';
import {accessConfig, checkClientToken} from '../src/security.mjs';

const secret='Slangverse-private-client-passphrase-2026';
test('local backend works with no token configured',()=>{
  assert.equal(checkClientToken(undefined,''),true);
  assert.equal(accessConfig({}).maxGenerations,20);
});
test('client token uses bearer header, rejecting missing, wrong or malformed credentials',()=>{
  assert.equal(checkClientToken(`Bearer ${secret}`,secret),true);
  for(const value of [undefined,'',secret,'Bearer bad','Basic '+secret,'Bearer '+secret+'x']){
    assert.equal(checkClientToken(value,secret),false);
  }
});
test('cloud settings fail closed for missing or weak secret',()=>{
  assert.throws(()=>accessConfig({SLANGVERSE_REQUIRE_TOKEN:'true'}),/24 characters/);
  assert.throws(()=>accessConfig({SLANGVERSE_CLIENT_TOKEN:'short'}),/24 characters/);
  assert.equal(accessConfig({SLANGVERSE_REQUIRE_TOKEN:'true',SLANGVERSE_CLIENT_TOKEN:secret}).token,secret);
});
test('generation safety fuse validates limits',()=>{
  assert.equal(accessConfig({SLANGVERSE_MAX_LLM_SESSIONS:'3'}).maxGenerations,3);
  for(const value of ['0','-1','1.5','1001','NaN']){
    assert.throws(()=>accessConfig({SLANGVERSE_MAX_LLM_SESSIONS:value}),/integer/);
  }
});
