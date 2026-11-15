import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {avatarIds,resolveAvatarId,withProfilePictures} from '../src/profilePictures.mjs';
import {mockPackage,publicQuestion,validatePackage,createMockSession,grade} from '../src/game.mjs';
import {createSessionService} from '../src/sessionService.mjs';
import {createGenerationPrompt} from '../src/prompts.mjs';
import {buildDemoBank} from '../scripts/export-static-demo-bank.mjs';

test('shared CC0 catalogue, bundled PNGs and backend-only registry agree',()=>{
 const catalog=JSON.parse(readFileSync(new URL('../../assets/profile-pictures/catalog.json',import.meta.url)));
 const deployed=JSON.parse(readFileSync(new URL('../data/profile-pictures.json',import.meta.url)));
 assert.equal(catalog.source.license,'CC0-1.0');
 assert.deepEqual(avatarIds,catalog.avatars.map(a=>a.id));
 assert.deepEqual(deployed.avatars,catalog.avatars.map(({id,handle})=>({id,handle})));
 assert.equal(new Set(avatarIds).size,22);
 for(const avatar of catalog.avatars){
  const png=readFileSync(new URL(`../../assets/profile-pictures/${avatar.png}`,import.meta.url));
  assert.equal(png.subarray(0,8).toString('hex'),'89504e470d0a1a0a');
  assert.equal(png.readUInt32BE(16),256);assert.equal(png.readUInt32BE(20),256);
  assert.equal(createHash('sha256').update(png).digest('hex'),avatar.sha256);
 }
});

test('known characters stay fixed and invalid or absent avatar IDs have stable Android-compatible fallbacks',()=>{
 assert.equal(resolveAvatarId('guest_04',' @MIRAMOMENTS '),'mira');
 assert.equal(resolveAvatarId('guest_03','@newperson'),'guest_03');
 assert.equal(resolveAvatarId(undefined,'@someone-new'),'guest_03');
 assert.equal(resolveAvatarId(undefined,'@visitor'),'guest_08');
 for(const invalid of [undefined,'','../../secret','https://example.com/a.png',{},'<svg/>']){
  assert.equal(resolveAvatarId(invalid,'@visitor'),'guest_08');
 }
 assert.equal(resolveAvatarId(undefined,''),'default');
});

test('normalization preserves questions and assigns one avatar per handle without mutating provider data',()=>{
 const original=structuredClone(mockPackage);
 original[0].post.comments[0].handle='@recurring';
 original[1].post.comments[0].handle='@recurring';
 original[1].post.comments[0].avatarId='guest_07';
 original[2].post.comments[0].handle='@recurring';
 original[2].post.comments[0].avatarId='guest_02';
 const normalized=withProfilePictures(original);
 assert.deepEqual(validatePackage(normalized),[]);
 assert.equal(original[0].post.avatarId,undefined);
 assert.deepEqual(normalized.slice(0,3).map(q=>q.post.comments[0].avatarId),['guest_07','guest_07','guest_07']);
 for(let i=0;i<normalized.length;i++){
  const stripped=structuredClone(normalized[i]);delete stripped.post.avatarId;
  stripped.post.comments.forEach(c=>delete c.avatarId);
  const before=structuredClone(original[i]);delete before.post.avatarId;
  before.post.comments.forEach(c=>delete c.avatarId);
  assert.deepEqual(stripped,before);
 }
});

test('mock API views and static exports carry avatar IDs without changing scoring or revealing answer keys',()=>{
 const session=createMockSession();
 const view=publicQuestion(session.questions[0]);
 assert.equal(view.post.avatarId,'campus');
 assert.ok(view.post.comments.every(c=>avatarIds.includes(c.avatarId)));
 for(const key of ['correctIndex','targetMeaning','explanation'])assert.equal(key in view,false);
 assert.equal(grade(session,session.questions[0].id,session.questions[0].correctIndex).xpEarned,80);
 for(const pack of Object.values(buildDemoBank().packs)){
  assert.ok(pack.questions.every(q=>avatarIds.includes(q.post.avatarId)&&q.post.comments.every(c=>avatarIds.includes(c.avatarId))));
 }
});

test('LLM prompt offers actual local IDs and malformed cosmetic metadata needs no paid retry',async()=>{
 const prompt=createGenerationPrompt();
 assert.match(prompt,/avatarId/);assert.match(prompt,/Never return an image URL/);
 for(const id of avatarIds)assert.ok(prompt.includes(id));
 const items=structuredClone(mockPackage);
 items[0].post.avatarId='https://example.com/avatar.png';
 const result=await createSessionService({provider:'openai-compatible',retries:0,llmProvider:async()=>({items})})();
 assert.equal(result.session.source,'llm');assert.equal(result.providerCalls,1);
 assert.equal(result.session.questions[0].post.avatarId,'campus');
 assert.deepEqual(validatePackage(result.session.questions),[]);
});
