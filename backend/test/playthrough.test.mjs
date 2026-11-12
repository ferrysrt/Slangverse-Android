import test, {after} from 'node:test';
import assert from 'node:assert/strict';
import {mockPackage} from '../src/game.mjs';
import {additionalMockPackage} from '../src/mockCatalog.mjs';
import {resolveSense} from '../src/catalog.mjs';

// Simulated PLAYER requests through the real HTTP server, not grade() directly.
// Mock data only. No OpenRouter secret or paid LLM request.
process.env.SLANGVERSE_NO_LISTEN='1';
delete process.env.SLANGVERSE_REQUIRE_TOKEN;
delete process.env.SLANGVERSE_CLIENT_TOKEN;
process.env.LLM_PROVIDER='mock';
const {default:server}=await import('../src/server.mjs');
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const root='http://127.0.0.1:'+server.address().port;
after(()=>new Promise(resolve=>server.close(resolve)));

async function request(method,path,body) {
  const response=await fetch(root+path,{
    method,headers:{'Content-Type':'application/json'},
    ...(body===undefined?{}:{body:JSON.stringify(body)})
  });
  return {status:response.status,body:await response.json()};
}

const senses=['cooked-trouble','no-cap-truth','lowkey-subtle'];
for(const senseId of senses){
  test('simulated full 10-question player session: '+senseId,async()=>{
    const selection=resolveSense({senseId});
    const reference=senseId==='cooked-trouble'?mockPackage:additionalMockPackage(selection);
    const created=await request('POST','/api/sessions',{senseId});
    assert.equal(created.status,201,'session should be created');
    const session=created.body;
    assert.equal(session.senseId,senseId);
    assert.equal(session.source,'mock');
    assert.equal(session.questions.length,10);
    assert.equal(session.referenceStatus,'provisional');
    const counts=session.questions.map(q=>q.difficulty).reduce((a,d)=>(a[d]++,a),{easy:0,medium:0,hard:0});
    assert.deepEqual(counts,{easy:5,medium:3,hard:2});
    assert.equal(new Set(session.questions.map(q=>q.contextId)).size,10);
    const initial=await request('GET','/api/sessions/'+session.id);
    assert.equal(initial.status,200);
    assert.equal(initial.body.answeredCount,0);

    // One intentionally wrong answer checks real miss handling and XP.
    const wrongAt=4;let expectedXp=0;
    for(const [i,q] of session.questions.entries()){
      assert.ok(q.post.content.length>0);
      assert.ok(q.post.comments.length>=1);
      assert.equal(q.options.length,4);
      assert.equal('correctIndex' in q,false,'do not leak key before grading');
      assert.equal('explanation' in q,false);
      const expectedIndex=reference[i].correctIndex;
      const answer=i===wrongAt?(expectedIndex+1)%4:expectedIndex;
      const expectedCorrect=i!==wrongAt;
      const graded=await request('POST','/api/sessions/'+session.id+'/answers',{
        questionId:q.id,selectedIndex:answer
      });
      assert.equal(graded.status,200);
      assert.equal(graded.body.correct,expectedCorrect,'incorrect grade at '+senseId+' item '+(i+1));
      assert.equal(graded.body.correctIndex,expectedIndex);
      assert.ok(graded.body.explanation.length>5);
      assert.equal(graded.body.answeredCount,i+1);
      const earned=expectedCorrect?80:15;
      expectedXp+=earned;
      assert.equal(graded.body.xpEarned,earned);
      assert.equal(graded.body.totalXp,expectedXp);
      const progress=await request('GET','/api/sessions/'+session.id);
      assert.equal(progress.body.answeredCount,i+1);
      assert.equal(progress.body.correctCount,i+1-(i>=wrongAt?1:0));

      if(i===0){
        const duplicate=await request('POST','/api/sessions/'+session.id+'/answers',{
          questionId:q.id,selectedIndex:answer
        });
        assert.equal(duplicate.status,409,'duplicate must not grant extra reward');
      }
    }
    const final=await request('GET','/api/sessions/'+session.id);
    assert.equal(final.body.totalCount,10);
    assert.equal(final.body.answeredCount,10);
    assert.equal(final.body.correctCount,9);
    assert.equal(final.body.totalXp,735);
    console.log(JSON.stringify({
      event:'simulated-player-complete',senseId,answered:10,correct:9,wrong:1,xp:735,source:session.source
    }));
  });
}
