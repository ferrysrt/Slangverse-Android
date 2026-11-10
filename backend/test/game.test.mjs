import test from 'node:test';
import assert from 'node:assert/strict';
import {mockPackage,validatePackage,validateQuestion,createMockSession,publicQuestion,grade,progress} from '../src/game.mjs';

test('10-question sample has 10 distinct contexts and a 5:3:2 nominal ratio',()=>{
  assert.equal(mockPackage.length,10);
  assert.equal(new Set(mockPackage.map(q=>q.contextId)).size,10);
  assert.deepEqual(validatePackage(mockPackage),[]);
});
test('keys, target meaning and explanation stay on the server',()=>{
  const q=publicQuestion(mockPackage[0]);
  for(const k of ['correctIndex','targetMeaning','explanation'])assert.equal(k in q,false,k);
  assert.equal(q.post.comments.length,3);
});
test('reject duplicate IDs, duplicate contexts and ratio mismatch',()=>{
  const pkg=structuredClone(mockPackage);pkg[1].id=pkg[0].id;pkg[1].contextId=pkg[0].contextId;
  pkg[9].difficulty='easy';const err=validatePackage(pkg);
  assert.ok(err.includes('duplicate-ids'));
  assert.ok(err.includes('duplicate-context-ids'));
  assert.ok(err.includes('difficulty-ratio'));
});
test('comments leaking any answer option are rejected',()=>{
  const q=structuredClone(mockPackage[0]);q.post.comments[0].text='You are in serious trouble!';
  assert.ok(validateQuestion(q).includes('direct-answer-leakage'));
});
test('invalid option shape and index are rejected',()=>{
  const q=structuredClone(mockPackage[0]);q.options=['fine','fine',null,'great'];q.correctIndex=7;
  assert.ok(validateQuestion(q).includes('options'));
  assert.ok(validateQuestion(q).includes('correct-index'));
});
test('server grades a wrong answer and records partial progress',()=>{
  const s=createMockSession();const answer=grade(s,'cooked-1',0);
  assert.equal(answer.correct,false);assert.equal(answer.correctIndex,1);assert.equal(answer.totalXp,15);
  assert.deepEqual(progress(s).answeredCount,1);
});
test('different questions can be graded exactly once each',()=>{
  const s=createMockSession();const right=grade(s,'cooked-1',1),wrong=grade(s,'cooked-2',0);
  assert.equal(right.correct,true);assert.equal(wrong.correct,false);
  assert.equal(grade(s,'cooked-1',1).status,409);
  assert.deepEqual(progress(s),{id:s.id,source:'mock',answeredCount:2,totalCount:10,correctCount:1,totalXp:95});
});
test('invalid question and invalid selection are rejected',()=>{
  const s=createMockSession();assert.equal(grade(s,'no-such-question',0).status,404);
  for(const selection of [-1,4,1.5,'1',null])assert.equal(grade(s,'cooked-1',selection).status,400);
});
