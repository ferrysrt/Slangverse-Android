import { randomUUID } from 'node:crypto';

/**
 * Research-aware DEMO dataset. Every question is hand-authored; no LLM call occurs.
 * Ten distinct scenario IDs simulate the 5:3:2 package contract for one target meaning.
 * Difficulty labels are design metadata only, not empirically calibrated item difficulty.
 */
export const slang = Object.freeze({term: 'cooked', targetMeaning: 'in serious trouble'});
export const difficulties = ['easy','easy','easy','easy','easy','medium','medium','medium','hard','hard'];
const scenarios = [
  {id:'campus', handle:'@campusfeed', displayName:'Campus Confessions', hashtag:'#CampusChaos',
    text:"Deadline tonight, laptop crashed, and my project isn't saved. I'm cooked. 😭",
    comments:["bro the lecturer's gonna grill you tomorrow 😭", 'please tell me auto-save was ON 💀', "that deadline isn't gonna wait for anyone..."]},
  {id:'gaming', handle:'@gaminglobby', displayName:'Gaming Lobby', hashtag:'#GamingMoments',
    text:"We were up 4–0 and still lost 4–5. We're cooked. 😭",
    comments:['from 4–0 to 4–5 is actually diabolical 😭', "we're never living down that comeback 💀", 'wait how did that match collapse so badly?']},
  {id:'office', handle:'@workchat', displayName:'Office After Hours', hashtag:'#WorkLife',
    text:"I sent the wrong budget file to the client an hour before the meeting. I'm cooked.",
    comments:['the follow-up email is gonna be awkward', 'did anyone approve that attachment?', "hope your manager hasn't opened it yet"]},
  {id:'travel', handle:'@railstories', displayName:'Last Train Stories', hashtag:'#TravelTales',
    text:"The last train left and my hotel is two towns away. I'm cooked.",
    comments:['there is no late bus either 😭', 'maybe someone at the station can help?', 'night taxis there cost a fortune']},
  {id:'sports', handle:'@finalwhistle', displayName:'Final Whistle', hashtag:'#SportsTalk',
    text:"Our goalkeeper got injured and we used all our subs. We're cooked.",
    comments:['who is even going in goal now?', 'ten minutes left is gonna feel like an hour', 'coach looks stressed']},
  {id:'technology', handle:'@devdiary', displayName:'Dev Diary', hashtag:'#TechFails',
    text:"Production server is down and the backup restore just failed. We're cooked.",
    comments:['customers are already reporting errors', 'is there a rollback plan?', 'please tell me somebody has a snapshot']},
  {id:'finance', handle:'@budgettalk', displayName:'Budget Diaries', hashtag:'#Adulting',
    text:"Rent is due tomorrow and I accidentally paid the wrong account. I'm cooked.",
    comments:['have you called the bank yet?', 'tomorrow is way too soon', 'hope the transfer can be reversed']},
  {id:'event', handle:'@stagecrew', displayName:'Stage Crew Life', hashtag:'#EventChaos',
    text:"The opening act starts in five minutes but the venue has no power. We're cooked.",
    comments:['audience is already lining up', 'is the backup generator working?', 'someone call the technician']},
  {id:'exam', handle:'@studentlife', displayName:'Student Life', hashtag:'#ExamWeek',
    text:"I just noticed my exam ID is missing and the doors close in ten minutes. I'm cooked.",
    comments:['maybe the office can print a temporary pass?', 'that clock is moving too fast', "don't forget to ask the proctor"]},
  {id:'application', handle:'@careersteps', displayName:'Career Steps', hashtag:'#CareerTalk',
    text:"I finished my scholarship application, but the portal locked right before I hit submit. I'm cooked.",
    comments:['email admissions with a timestamp', 'screenshots might help you make a case', 'the deadline really was tonight?']}
];
export const mockPosts = scenarios.map(({id,handle,displayName,hashtag,text,comments})=>({
  id,handle,displayName,hashtag,content:text,
  comments:comments.map((comment,i)=>({handle:`@${id}reply${i+1}`,text:comment}))
}));
const optionSets = [
  ['Feeling exhausted','In serious trouble','Very excited','Feeling hungry'],
  ['Enjoying a victory','Feeling sleepy','Planning a meal','In serious trouble'],
  ['In serious trouble','Feeling relaxed','Taking a break','Being praised'],
  ['Feeling hungry','Feeling confident','In serious trouble','Feeling nostalgic'],
  ['Wishing for a meal','In serious trouble','Feeling energetic','Being congratulated'],
  ['Having a good time','Winning something','Feeling bored','In serious trouble'],
  ['In serious trouble','Being excited','Getting promoted','Feeling sleepy'],
  ['Going to dinner','Feeling confident','In serious trouble','Taking a day off'],
  ['In serious trouble','Feeling proud','Relaxing after work','Becoming famous'],
  ['Feeling welcome','Getting praised','Making dinner','In serious trouble']
];
const correctIndexes = [1,3,0,2,1,3,0,2,0,3];
export const mockPackage = difficulties.map((difficulty,i)=>({
  id:`cooked-${i+1}`,slang:'cooked',targetMeaning:slang.targetMeaning,
  difficulty,contextId:mockPosts[i].id,post:mockPosts[i],
  question:"What does 'cooked' mean in this post?",options:optionSets[i],
  correctIndex:correctIndexes[i],explanation:'Here, cooked describes someone being in a serious or difficult situation.'
}));
export const normalize = value => typeof value==='string' ? value.toLowerCase().replace(/[^a-z0-9 ]/g,' ').replace(/\s+/g,' ').trim() : '';
export function validateQuestion(q){
  if(!q||typeof q!=='object'||Array.isArray(q))return ['not-object'];
  const errors=[];
  if(typeof q.id!=='string'||!q.id||typeof q.slang!=='string'||!q.slang)errors.push('question-identity');
  if(typeof q.contextId!=='string'||!q.contextId)errors.push('context-id');
  if(!q.post||typeof q.post!=='object'||typeof q.post.content!=='string'||!q.post.content.trim()||typeof q.post.displayName!=='string'||typeof q.post.handle!=='string')errors.push('post-shape');
  if(typeof q.question!=='string'||!q.question.trim())errors.push('question-text');
  if(!Array.isArray(q.options)||q.options.length!==4||q.options.some(o=>typeof o!=='string'||!normalize(o))||new Set(q.options.map(normalize)).size!==4)errors.push('options');
  if(!Number.isInteger(q.correctIndex)||q.correctIndex<0||q.correctIndex>3)errors.push('correct-index');
  // Lexical reference-key check only. This is NOT a semantic quality assessment.
  if(Array.isArray(q.options) && Number.isInteger(q.correctIndex) && q.correctIndex>=0 && q.correctIndex<4 &&
     normalize(q.options[q.correctIndex]) !== normalize(q.targetMeaning))errors.push('key-meaning-mismatch');
  if(!['easy','medium','hard'].includes(q.difficulty))errors.push('difficulty');
  if(typeof q.targetMeaning!=='string'||!normalize(q.targetMeaning))errors.push('target-meaning');
  if(typeof q.explanation!=='string'||!q.explanation.trim())errors.push('explanation');
  if(!Array.isArray(q.post?.comments)||q.post.comments.length<1||q.post.comments.length>5){errors.push('comments-count');}
  else for(const c of q.post.comments){
    if(!c||typeof c.handle!=='string'||!c.handle.trim()||typeof c.text!=='string'||!c.text.trim()) {errors.push('comment-shape');continue;}
    const text=normalize(c.text),meaning=normalize(q.targetMeaning);
    if((meaning&&text.includes(meaning))||q.options?.some(o=>{const opt=normalize(o);return opt&&text.includes(opt);}))errors.push('direct-answer-leakage');
  }
  return [...new Set(errors)];
}
export function validatePackage(pkg,{requireDistinctContexts=true}={}){
  if(!Array.isArray(pkg)||pkg.length!==10)return ['package-size'];
  const errors=[],counts={easy:0,medium:0,hard:0};
  for(const [index,q] of pkg.entries()){
    errors.push(...validateQuestion(q).map(e=>`${index}:${e}`));
    if(q?.difficulty in counts)counts[q.difficulty]++;
  }
  if(counts.easy!==5||counts.medium!==3||counts.hard!==2)errors.push('difficulty-ratio');
  if(new Set(pkg.map(q=>q?.id)).size!==pkg.length)errors.push('duplicate-ids');
  if(requireDistinctContexts&&new Set(pkg.map(q=>q?.contextId)).size!==pkg.length)errors.push('duplicate-context-ids');
  if(new Set(pkg.map(q=>q?.slang)).size!==1)errors.push('mixed-slang-package');
  return errors;
}
export function publicQuestion(q){
  const {correctIndex,targetMeaning,explanation,...safe}=q;
  return structuredClone(safe);
}
export function createMockSession(now=Date.now()){
  const errs=validatePackage(mockPackage);
  if(errs.length)throw new Error(`Invalid curated mock pack: ${errs.join(',')}`);
  return {id:randomUUID(),source:'mock',createdAt:new Date(now).toISOString(),expiresAt:now+30*60*1000,answered:new Map(),questions:mockPackage};
}
export function grade(session,questionId,selectedIndex){
  const q=session.questions.find(q=>q.id===questionId);
  if(!q)return {status:404,error:'question-not-found'};
  if(!Number.isInteger(selectedIndex)||selectedIndex<0||selectedIndex>3)return {status:400,error:'invalid-option'};
  if(session.answered.has(questionId))return {status:409,error:'already-answered'};
  const correct=selectedIndex===q.correctIndex,xpEarned=correct?80:15;
  session.answered.set(questionId,{selectedIndex,correct,xpEarned});
  return {status:200,correct,correctIndex:q.correctIndex,explanation:q.explanation,xpEarned,
    answeredCount:session.answered.size,totalCount:session.questions.length,
    totalXp:[...session.answered.values()].reduce((n,a)=>n+a.xpEarned,0)};
}
export function progress(session){const answers=[...session.answered.values()];return {id:session.id,source:session.source,answeredCount:answers.length,totalCount:session.questions.length,correctCount:answers.filter(x=>x.correct).length,totalXp:answers.reduce((s,x)=>s+x.xpEarned,0)};}
