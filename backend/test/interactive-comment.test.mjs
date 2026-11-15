import test from 'node:test';
import assert from 'node:assert/strict';
import {mockPackage,validateQuestion,validatePackage,publicQuestion} from '../src/game.mjs';
import {additionalMockPackage} from '../src/mockCatalog.mjs';
import {resolveSense} from '../src/catalog.mjs';
import {createGenerationPrompt,PROMPT_VERSION} from '../src/prompts.mjs';

const packs=[
  mockPackage,
  additionalMockPackage(resolveSense({senseId:'no-cap-truth'})),
  additionalMockPackage(resolveSense({senseId:'lowkey-subtle'}))
];

test('V3.1 demo packs contain one distinct natural question comment per question without leaking keys',()=>{
  for(const pack of packs){
    assert.deepEqual(validatePackage(pack),[]);
    for(const q of pack){
      assert.match(q.questionComment,/\?/);
      assert.ok(q.questionComment.toLowerCase().includes(q.slang.toLowerCase()));
      assert.ok(q.post.comments.every(c=>c.text!==q.questionComment),'special reply must not replace hint list');
      const publicView=publicQuestion(q);
      assert.equal(publicView.questionComment,q.questionComment);
      assert.equal('correctIndex' in publicView,false);
      assert.equal('explanation' in publicView,false);
      assert.equal('targetMeaning' in publicView,false);
    }
  }
});

test('V3.1 generated special reply cannot leak the correct option or target meaning',()=>{
  const example=structuredClone(mockPackage[0]);
  example.questionComment="Wait, does cooked mean In serious trouble?";
  assert.ok(validateQuestion(example).includes('question-comment-leakage'));
  example.questionComment="Wait, does this mean cooked food is being prepared?";
  assert.deepEqual(validateQuestion(example),[]);
});

test('legacy question packs without questionComment still validate during transition',()=>{
  const legacy=structuredClone(mockPackage);
  for(const item of legacy)delete item.questionComment;
  assert.deepEqual(validatePackage(legacy),[]);
});

test('new LLM prompt explicitly requests special in-thread comment and avoids answer leakage',()=>{
  const p=createGenerationPrompt({term:'cooked',meaning:'in serious trouble',senseId:'cooked-trouble'});
  assert.ok(PROMPT_VERSION.includes('v3.1'));
  assert.match(p,/questionComment/);
  assert.match(p,/literal misunderstanding/);
  assert.match(p,/NOT mention the correct meaning/);
});
