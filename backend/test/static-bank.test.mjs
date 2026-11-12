import test from 'node:test';
import assert from 'node:assert/strict';
import {buildDemoBank} from '../scripts/export-static-demo-bank.mjs';
import {validatePackage} from '../src/game.mjs';

test('static GET bank contains 3 distinct provisional demo packs', () => {
  const {index,packs}=buildDemoBank();
  assert.equal(index.schemaVersion,1);
  assert.equal(index.generationSource,'mock');
  assert.equal(index.expertValidated,false);
  assert.deepEqual(Object.keys(packs).sort(),['cooked-trouble','lowkey-subtle','no-cap-truth']);
  for(const [senseId, pack] of Object.entries(packs)) {
    assert.equal(pack.schemaVersion,1);
    assert.equal(pack.senseId,senseId);
    assert.equal(pack.generationSource,'mock');
    assert.equal(pack.expertValidated,false);
    assert.equal(pack.questions.length,10);
    assert.equal(new Set(pack.questions.map(q=>q.contextId)).size,10);
    assert.equal(validatePackage(pack.questions.map(q=>({
      ...q,targetMeaning:q.options[q.correctIndex]
    }))).length,0);
    for (const q of pack.questions) {
      assert.equal('targetMeaning' in q,false);
      assert.ok(Number.isInteger(q.correctIndex));
      assert.ok(q.explanation.length > 0);
    }
  }
});
