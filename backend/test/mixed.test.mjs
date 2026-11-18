import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync, mkdirSync, readFileSync, writeFileSync, readdirSync, existsSync, rmSync} from 'node:fs';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
import {mockPackage, validateQuestion} from '../src/game.mjs';
import {withProfilePictures} from '../src/profilePictures.mjs';
import {candidateIdFor} from '../src/datasetProvenance.mjs';
import {MIXED_PROMPT_VERSION, MIXED_CONTEXT_CATEGORIES, MIXED_EVALUATION_CHECKS,
  selectMixedSenses, createMixedPrompt, evaluateMixedResponse, validateMixedReferences, mixedReferencesFromSenses,
  validatePublishedMixedPack, isSafeBankId} from '../src/mixedQuestions.mjs';
import {createMixedProvider, safeMixedFailureCode} from '../src/mixedProvider.mjs';
import {generateMixedPack, createMixedPackId} from '../src/mixedGeneration.mjs';
import {publishMixedBank, writeMixedCatalog, writeImmutableMixedPack, mixedSha256} from '../src/mixedBank.mjs';
import {planPackTerms, sensesForTerms, countPublishedTerms} from '../src/mixedTermPlanner.mjs';
import {generatePackRound} from '../scripts/generate-mixed-pack-batch.mjs';

// Synthetic responses/provenance only. No test contacts a provider or labels fixture history as real.
function normalizedSense(term, normalizedDefinition) {
  const sourceCandidateId = candidateIdFor(term, normalizedDefinition);
  const quote = `Test-only reference text containing "${term}": ${normalizedDefinition}.`;
  return {senseId: `normalized-${sourceCandidateId}`, term, targetMeaning: normalizedDefinition, normalizedDefinition,
    normalizationModel: 'fixture/normalizer', sourceCandidateId,
    sources: [{url: `https://example.invalid/reference/${sourceCandidateId}`, title: 'Synthetic test reference', quote,
      collectedAt: new Date().toISOString(), collectionMethod: 'firecrawl',
      contentSha256: mixedSha256(`# Synthetic captured fixture\n\n${quote}`)}],
    reviewStatus: 'normalized-unreviewed'};
}
function senses(count = 12) {
  return Array.from({length: count}, (_, i) => normalizedSense(`term${i}`, `fixture meaning ${i}`));
}
function questionsFor({references, assignments}) {
  return assignments.map((assignment, i) => {
    const ref = references.find(ref => ref.senseId === assignment.senseId);
    const options = ['feeling very sleepy', 'planning a holiday', 'preparing a meal'];
    options.splice(i % 4, 0, ref.targetMeaning);
    // v9: five of ten terms live only in a comment written by somebody other than the post author.
    const replyTargeted = i % 2 === 1, commenter = `@fictional_reply_${i}`;
    const comments = replyTargeted
      ? [{handle: commenter, text: `Honestly, that was pure ${ref.term}.`, avatarId: 'guest_02'}]
      : [{handle: '@fictional_reply', text: 'The group chat went quiet for a minute.', avatarId: 'guest_02'}];
    return {
      id: assignment.questionId, senseId: ref.senseId, slang: ref.term, targetMeaning: ref.targetMeaning,
      difficulty: assignment.difficulty, contextId: 'group-chat', contextCategory: MIXED_CONTEXT_CATEGORIES[i % 9],
      post: {displayName: `Fictional Friend ${i}`, handle: `@fictional_${i}`, hashtag: '#GroupChat',
        content: replyTargeted ? 'The group chat went quiet after I shared my news.'
          : `My ${ref.term} moment after the group chat went quiet.`, avatarId: 'guest_01',
        comments},
      questionComment: `Wait, what does ${ref.term} mean here?`,
      questionTarget: replyTargeted ? 'reply' : 'post',
      targetHandle: replyTargeted ? commenter : `@fictional_${i}`,
      question: replyTargeted ? `What does ${commenter} mean by '${ref.term}'?`
        : `What does '${ref.term}' mean in this post?`,
      options, correctIndex: i % 4,
      explanation: `This fixture uses ${ref.term} for ${ref.targetMeaning}.`,
      replyTexts: options.map(option => `${ref.term} here just means ${option}.`)
    };
  });
}
function fixture(packId = 'mixed-test-pack', count = 12) {
  const selection = selectMixedSenses(senses(count), {packId, randomIndex: n => n - 1});
  const questions = questionsFor(selection);
  const pack = {schemaVersion: 2, packId, generationSource: 'llm', promptVersion: MIXED_PROMPT_VERSION,
    generatorModel: 'fixture/actual-model', generatedAt: new Date().toISOString(),
    referenceStatus: 'normalized-unreviewed', datasetVersion: 'fixture-dataset-v1', expertValidated: false,
    references: selection.references, questions};
  return {...selection, questions, pack, raw: JSON.stringify({questions})};
}
function promptInput(messages) {
  const match = /INPUT DATA:\n([\s\S]*?)\n\nFollow the ten assignments/.exec(messages[1].content);
  assert.ok(match, 'versioned prompt contains the exact JSON input block');
  return JSON.parse(match[1]);
}
function envelope(content) {
  return {id: 'fixture-response', model: 'fixture/actual-model', provider: 'Fixture Provider',
    created: Math.floor(Date.now() / 1000), usage: {prompt_tokens: 100, completion_tokens: 200, total_tokens: 300, cost: 0.001},
    choices: [{message: {content}, finish_reason: 'stop', native_finish_reason: 'stop'}]};
}
function fakeProvider({transform = value => value, observe = () => {}} = {}) {
  return createMixedProvider({apiKey: 'fixture-secret-never-persist', model: 'fixture/requested-model',
    fetchImpl: async (url, request) => {
      observe(url, request);
      const body = JSON.parse(request.body);
      const input = promptInput(body.messages);
      const content = JSON.stringify({questions: questionsFor(input)});
      return new Response(JSON.stringify(transform(envelope(content))), {status: 200});
    }});
}
function sandbox(t) {
  const root = mkdtempSync(join(tmpdir(), 'mixed-bank-test-'));
  const outputDir = join(root, 'bank'), experimentsDir = join(root, 'experiments');
  t.after(() => rmSync(root, {recursive: true, force: true}));
  return {root, outputDir, experimentsDir};
}
const jsonFile = (...parts) => JSON.parse(readFileSync(join(...parts), 'utf8'));
const check = (validation, id) => validation.checks.find(check => check.id === id);

// Selection and strict validation.
test('selection prefers ten distinct terms, records its plan, and never mutates the dataset', () => {
  const candidates = senses();
  candidates.push(normalizedSense(candidates[0].term, 'a distinct fixture definition for the same term'));
  const before = structuredClone(candidates);
  const selection = selectMixedSenses(candidates, {packId: 'mixed-select', randomIndex: n => n - 1});
  assert.equal(selection.senses.length, 10);
  assert.equal(new Set(selection.senses.map(s => s.term)).size, 10);
  assert.equal(selection.parameters.availableDistinctTerms, 12);
  assert.equal(selection.assignments.length, 10);
  assert.deepEqual(selection.assignments.map(a => a.difficulty), ['easy', 'easy', 'easy', 'easy', 'easy', 'medium', 'medium', 'medium', 'hard', 'hard']);
  assert.deepEqual(candidates, before);
});

test('selection uses all available unique terms when fewer than ten exist, but requires at least two', () => {
  for (const count of [2, 3, 9]) {
    const value = fixture('mixed-small', count);
    assert.equal(value.references.length, count);
    assert.equal(new Set(value.questions.map(q => q.slang)).size, count);
    assert.equal(evaluateMixedResponse(value.raw, value).valid, true);
  }
  assert.throws(() => selectMixedSenses(senses(1), {packId: 'mixed-single'}), /two-distinct-terms/);
  const duplicateTerm = senses(2);
  duplicateTerm[1] = normalizedSense(` ${duplicateTerm[0].term.toUpperCase()} `, duplicateTerm[1].normalizedDefinition);
  assert.throws(() => selectMixedSenses(duplicateTerm, {packId: 'mixed-single'}), /two-distinct-terms/);
});

test('references bind the sense ID, candidate digest and exact normalized definition', () => {
  const original = fixture();
  for (const ref of original.references) {
    assert.equal(ref.senseId, `normalized-${ref.sourceCandidateId}`);
    assert.equal(ref.sourceCandidateId, candidateIdFor(ref.term, ref.normalizedDefinition));
    assert.equal(ref.targetMeaning, ref.normalizedDefinition);
  }
  const mutations = [
    ['reference-identity', ref => {ref.senseId = 'normalized-' + '0'.repeat(24);} ],
    ['reference-identity', ref => {ref.sourceCandidateId = '0'.repeat(24);} ],
    ['reference-identity', ref => {ref.sourceCandidateId = '0'.repeat(24); ref.senseId = `normalized-${ref.sourceCandidateId}`;} ],
    ['reference-identity', ref => {ref.term = 'a changed term';} ],
    ['reference-identity', ref => {ref.normalizedDefinition = ref.targetMeaning = 'a changed fixture definition';} ],
    ['reference-definition-mismatch', ref => {ref.targetMeaning = ref.normalizedDefinition.toUpperCase();} ],
    ['reference-definition-mismatch', ref => {ref.targetMeaning = ` ${ref.normalizedDefinition} `;} ]
  ];
  for (const [code, mutate] of mutations) {
    const value = fixture(); mutate(value.references[0]);
    const before = structuredClone(value.references);
    assert.ok(validateMixedReferences(value.references).includes(`0:${code}`), code);
    assert.equal(check(evaluateMixedResponse(value.raw, value), 'reference-integrity').passed, false, code);
    assert.throws(() => validatePublishedMixedPack(value.pack), /invalid-published-mixed-questions/);
    assert.deepEqual(value.references, before, 'validation must never repair reference records');
  }
});

test('every embedded source requires complete capture provenance and a quote containing its term', () => {
  const mutations = [
    source => {delete source.title;}, source => {source.title = '   ';}, source => {source.title = 12;},
    source => {delete source.collectedAt;}, source => {source.collectedAt = null;},
    source => {source.collectedAt = Date.now();}, source => {source.collectedAt = 'not-a-capture-time';},
    source => {delete source.collectionMethod;}, source => {source.collectionMethod = 'manual';},
    source => {source.collectionMethod = 'Firecrawl';}, source => {delete source.contentSha256;},
    source => {source.contentSha256 = 'a'.repeat(63);}, source => {source.contentSha256 = 'a'.repeat(65);},
    source => {source.contentSha256 = 'z'.repeat(64);}, source => {source.contentSha256 = 12;},
    source => {delete source.quote;}, source => {source.quote = 'No reference term occurs in this quote.';},
    source => {source.url = 'file:///not-a-source';}, source => {source.url = 'https://user:password@example.invalid';}
  ];
  for (const mutate of mutations) {
    const value = fixture(); mutate(value.references[0].referenceSources[0]);
    assert.ok(validateMixedReferences(value.references).includes('0:reference-source'));
    assert.throws(() => validatePublishedMixedPack(value.pack), /invalid-published-mixed-questions/);
  }
  const value = fixture();
  value.references[0].referenceSources.push({...value.references[0].referenceSources[0],
    title: `A title mentioning ${value.references[0].term}`, quote: 'An unrelated citation cannot be rescued by the valid source.'});
  assert.ok(validateMixedReferences(value.references).includes('0:reference-source'));
});

test('quote matching uses literal term boundaries, not substrings or regex metacharacters', () => {
  const cases = [
    ['cap', 'The term CAP appears in this source.', true],
    ['cap', 'The word capsule is not the requested term.', false],
    ['cap', 'A recap is not the requested term.', false],
    ['cap', 'The text cap2 is not the requested term.', false],
    ['cap', 'The text 2cap is not the requested term.', false],
    ['cap', 'The text _cap_ is not a standalone term.', false],
    ['cap', 'The text capé is not the requested term.', false],
    ['cap', 'The text cap\u0301 is not the requested term.', false],
    ['no cap', 'The words NO\tCAP appear in this source.', true],
    ['no cap', 'The words no\r\ncap appear in this source.', true],
    ['no cap', 'The words no caps are not the requested term.', false],
    ['no cap', 'The text nocap is not the requested term.', false],
    ["let's go", "The words let's go appear in this source.", true],
    ["let's go", 'The words lets go are not the requested term.', false],
    ['c++', 'The literal term c++ appears in this source.', true],
    ['c++', 'The text cccc is not the requested term.', false],
    ['a.b', 'The literal term a.b appears in this source.', true],
    ['a.b', 'The text axb is not the requested term.', false],
    ['café', 'The term CAFÉ appears in this source.', true],
    ['café', 'The text precafépost is not the requested term.', false]
  ];
  for (const [term, quote, expected] of cases) {
    const refs = mixedReferencesFromSenses([normalizedSense(term, 'a fixture definition for boundary tests'),
      normalizedSense('other reference', 'a second fixture definition')]);
    refs[0].referenceSources[0].quote = quote;
    refs[0].referenceSources[0].contentSha256 = mixedSha256(`# Synthetic captured fixture\n\n${quote}`);
    const before = structuredClone(refs);
    assert.equal(validateMixedReferences(refs).length === 0, expected, JSON.stringify({term, quote}));
    assert.deepEqual(refs, before);
  }
});

test('capture verification preserves original records and does not claim semantic review', () => {
  const value = fixture(), source = value.references[0].referenceSources[0];
  source.quote = `${value.references[0].term}\r\nThis quotation mentions the term without assessing any meaning.`;
  const capturedDocument = `# Synthetic captured document\r\n\r\n${source.quote}\r\n`;
  source.contentSha256 = mixedSha256(capturedDocument);
  const before = JSON.stringify(value.pack);
  assert.notEqual(source.contentSha256, mixedSha256(source.quote), 'the capture hash is not a hash of just its excerpt');
  assert.deepEqual(validateMixedReferences(value.references), []);
  validatePublishedMixedPack(value.pack, {expectedReferences: structuredClone(value.references)});
  assert.equal(value.pack.referenceStatus, 'normalized-unreviewed');
  assert.equal(value.pack.expertValidated, false);
  assert.equal(JSON.stringify(value.pack), before);
});

test('selection rejects invalid provenance, status, identities and unsafe pack IDs before a call', () => {
  for (const change of [s => {s.reviewStatus = 'provisional';}, s => {s.sources = [];},
    s => {s.senseId = 'cooked-trouble';}, s => {s.normalizationModel = '';},
    s => {s.sources[0].url = 'file:///private';}]) {
    const candidates = senses(); change(candidates[0]);
    assert.throws(() => selectMixedSenses(candidates, {packId: 'mixed-invalid'}), /invalid-normalized/);
  }
  for (const id of ['../escape', 'Uppercase', 'a'.repeat(81), 'bad--id', ''])
    assert.throws(() => selectMixedSenses(senses(), {packId: id}), /invalid-mixed-pack-id/);
  assert.equal(isSafeBankId(createMixedPackId()), true);
});

test('versioned disk prompt preserves exact inputs, includes safety constraints, and has no category quota', () => {
  const value = fixture();
  const messages = createMixedPrompt({packId: value.pack.packId, ...value});
  assert.deepEqual(promptInput(messages).references, value.references);
  assert.deepEqual(promptInput(messages).assignments, value.assignments);
  for (const category of MIXED_CONTEXT_CATEGORIES) assert.ok(messages[1].content.includes(category));
  assert.match(messages[1].content, /Repeating categories and contextId values is allowed/);
  assert.match(messages[1].content, /UNTRUSTED DATA/);
  assert.match(messages[1].content, /not claim expert validation/);
  assert.match(messages[1].content, /three|distractors|Distractors/);
  assert.equal(messages[1].content.includes('{{INPUT_JSON}}'), false);
});

test('mixed-v8 renders the targeted distractor and conversational comment requirements', () => {
  const value = fixture();
  const messages = createMixedPrompt({packId: value.pack.packId, ...value});
  const prompt = messages[1].content;
  assert.match(prompt, /All options must be grammatically similar/);
  assert.match(prompt, /Distractors must be relevant to the context and reflect plausible misunderstandings/);
  assert.match(prompt, /Never create a second defensible correct option/);
  assert.match(prompt, /Four distinct, similarly concise options: exactly one is the targetMeaning VERBATIM/);
  assert.match(prompt, /Copy the reference string character-for-character into BOTH targetMeaning and the one correct option/);
  // v8 tightening: uninflected term, no near-miss hints, and an explicit final self-check.
  assert.match(prompt, /Never inflect, pluralize, conjugate, hyphenate or derive it/);
  assert.match(prompt, /A wrong guess in questionComment is allowed; a near-miss that explains the correct sense is not/);
  assert.match(prompt, /Before returning, re-check every question and fix any item that fails/);
  assert.match(prompt, /Return ONE strict JSON object with a questions array of exactly 10 questions/);
  assert.deepEqual(promptInput(messages).references, value.references);
  assert.deepEqual(promptInput(messages).assignments, value.assignments);
  assert.equal(MIXED_PROMPT_VERSION, 'mixed-v10');
  assert.equal(value.pack.promptVersion, 'mixed-v10');
  // v9: five reply targets, a slang-free post for those, and the learner's own replies.
  assert.match(prompt, /Exactly five of the ten questions MUST use questionTarget "reply"/);
  assert.match(prompt, /post\.content MUST NOT contain the slang term/);
  assert.match(prompt, /replyTexts has exactly four strings, aligned with options in order/);
  assert.match(prompt, /Each one MUST contain the exact slang term at least once/);
});

test('template markers and replacement syntax in source text remain exact untrusted data', () => {
  const value = fixture();
  const source = value.references[0].referenceSources[0];
  source.quote = `Literal $& {{AVATAR_INSTRUCTIONS}} {{INPUT_JSON}} text about ${value.references[0].term}.`;
  source.contentSha256 = mixedSha256(`# Synthetic captured fixture\n\n${source.quote}`);
  const messages = createMixedPrompt({packId: value.pack.packId, ...value});
  assert.deepEqual(promptInput(messages).references, value.references);
  assert.ok(messages[1].content.includes('Allowed avatarId values:'));
});

test('nine explicit evaluation checks pass on a ten-term 5/3/2 pack with repeated contexts', () => {
  const value = fixture();
  const validation = evaluateMixedResponse(value.raw, value);
  assert.equal(validation.valid, true, JSON.stringify(validation));
  assert.deepEqual(validation.checks.slice(0, 9).map(c => c.id), MIXED_EVALUATION_CHECKS);
  assert.ok(validation.checks.every(c => c.passed));
  assert.equal(new Set(value.questions.map(q => q.contextId)).size, 1);
  assert.equal(new Set(value.questions.map(q => q.contextCategory)).size, 9);
  validatePublishedMixedPack(value.pack);
});

test('Markdown, surrounding prose, trailing JSON, BOM and malformed JSON fail without repair', () => {
  const value = fixture();
  for (const raw of [`\u0060\u0060\u0060json\n${value.raw}\n\u0060\u0060\u0060`, `Here it is: ${value.raw}`,
    `${value.raw}\nThanks!`, `${value.raw}{}`, `\ufeff${value.raw}`, value.raw.slice(0, -1), null]) {
    const validation = evaluateMixedResponse(raw, value);
    assert.equal(validation.valid, false);
    assert.equal(check(validation, 'json-parse').passed, false);
    assert.equal(check(validation, 'no-surrounding-text').passed, false);
  }
  assert.equal(evaluateMixedResponse(` \r\n${value.raw}\t`, value).valid, true);
  assert.equal(check(evaluateMixedResponse(JSON.stringify(value.questions), value), 'no-surrounding-text').passed, false);
});

test('each contract dimension has a distinct failing check', () => {
  const cases = [
    ['required-fields', q => {delete q[0].questionComment;}],
    ['required-fields', q => {delete q[0].post.hashtag;}],
    ['required-fields', q => {delete q[0].post.comments[0].avatarId;}],
    ['count-10', q => q.pop()],
    ['difficulty-5-3-2', q => {q[0].difficulty = 'hard';}],
    ['four-options', q => q[0].options.pop()],
    ['four-options', q => {q[0].options[1] = q[0].options[0];}],
    ['correct-index', q => {q[0].correctIndex = 4;}],
    ['correct-index', q => {q[0].correctIndex = '0';}],
    ['context-category', q => {q[0].contextCategory = 'campus';}],
    ['unique-ids', q => {q[1].id = q[0].id;}]
  ];
  for (const [id, mutate] of cases) {
    const value = fixture(); mutate(value.questions);
    const result = evaluateMixedResponse(JSON.stringify({questions: value.questions}), value);
    assert.equal(result.valid, false, id);
    assert.equal(check(result, id).passed, false, id);
  }
});

test('reference integrity rejects detached senses, changed meanings/terms, unused references and wrong assignment IDs', () => {
  for (const mutate of [q => {q[0].senseId = 'normalized-eeeeeeeeeeeeeeeeeeeeeeee';},
    q => {q[0].slang = 'different';}, q => {q[0].targetMeaning = 'different';},
    q => {q[0].options[q[0].correctIndex] = 'a different key';},
    q => {q[0].post.content = 'A post with no requested slang at all.';},
    q => {q[0].id = 'unassigned-id';}, q => {q[0] = {...q[1], id: q[0].id};}]) {
    const value = fixture(); mutate(value.questions);
    const validation = evaluateMixedResponse(JSON.stringify({questions: value.questions}), value);
    assert.equal(check(validation, 'reference-integrity').passed, false);
  }
  const value = fixture();
  const original = structuredClone(value.pack.references);
  value.pack.references[0].referenceSources[0].quote = 'Changed source quote.';
  assert.throws(() => validatePublishedMixedPack(value.pack, {expectedReferences: original}), /provenance-mismatch/);
  assert.ok(validateMixedReferences([]).length);
});

test('existing comment leakage rules remain enforced alongside social/media guardrails', () => {
  for (const mutate of [q => {q.post.comments[0].text = q.targetMeaning;},
    q => {q.questionComment = `Does ${q.slang} mean ${q.targetMeaning}?`;},
    q => {q.post.comments[0].text = q.options[2];}]) {
    const value = fixture(); mutate(value.questions[0]);
    const result = evaluateMixedResponse(JSON.stringify({questions: value.questions}), value);
    assert.equal(check(result, 'existing-question-rules').passed, false);
  }
  for (const mutate of [q => {q.post.content += ' https://unsafe.invalid';},
    q => {q.post.imageUrl = 'https://unsafe.invalid/picture.png';},
    q => {q.post.imagePath = '/private/portrait.png';},
    q => {q.post.comments[0].text = 'The correct answer is A';},
    q => {q.post.avatarId = 'player';}, q => {q.post.handle = '@not a handle';},
    q => {q.post.content += ' Reach me at person@example.invalid';}]) {
    const value = fixture(); mutate(value.questions[0]);
    const result = evaluateMixedResponse(JSON.stringify({questions: value.questions}), value);
    assert.equal(check(result, 'social-safety').passed, false);
  }
});

test('hostile field types fail evaluation instead of crashing the legacy validator', () => {
  for (const bad of [null, [], 42, {difficulty: {toString: null, valueOf: null}},
    {...fixture().questions[0], options: {some: 'not-callable'}},
    {...fixture().questions[0], post: {comments: [null]}}]) {
    const value = fixture(); value.questions[0] = bad;
    assert.equal(evaluateMixedResponse(JSON.stringify({questions: value.questions}), value).valid, false);
  }
});

// Provider transport and audit capture. All fetches here are in-process fakes.
test('provider makes one bounded strict-JSON low-reasoning request and preserves actual metadata', async () => {
  let calls = 0, requestBody;
  const provider = fakeProvider({observe: (url, request) => {
    calls++; assert.equal(url, 'https://openrouter.ai/api/v1/chat/completions');
    assert.equal(request.redirect, 'error');
    assert.equal(request.headers.Authorization, 'Bearer fixture-secret-never-persist');
    requestBody = JSON.parse(request.body);
  }});
  const value = fixture();
  const response = await provider({messages: createMixedPrompt({packId: value.pack.packId, ...value})});
  assert.equal(calls, 1);
  assert.equal(requestBody.temperature, 0.5);
  assert.equal(requestBody.max_tokens, 10000);
  assert.deepEqual(requestBody.response_format, {type: 'json_object'});
  assert.deepEqual(requestBody.reasoning, {effort: 'low'});
  assert.equal(provider.timeoutMs, 120000);
  assert.equal(response.metadata.actualModel, 'fixture/actual-model');
  assert.equal(response.metadata.requestedModel, 'fixture/requested-model');
  assert.equal(response.metadata.usage.total_tokens, 300);
  assert.equal(response.metadata.finishReason, 'stop');
  assert.equal(response.content, JSON.parse(response.rawResponse).choices[0].message.content);
});

test('provider rejects unsafe configuration and budgets above the hard caps', () => {
  const config = {apiKey: 'fixture-key', model: 'fixture/model'};
  for (const baseUrl of ['http://remote.invalid/v1', 'https://user:password@example.invalid', 'https://example.invalid/?key=secret'])
    assert.throws(() => createMixedProvider({...config, baseUrl}), /unsafe-provider-url/);
  for (const timeoutMs of [0, 120001, NaN]) assert.throws(() => createMixedProvider({...config, timeoutMs}), /bad-provider-timeout/);
  for (const maxTokens of [511, 12001, 1.5]) assert.throws(() => createMixedProvider({...config, maxTokens}), /bad-provider-max-tokens/);
  assert.throws(() => createMixedProvider({...config, apiKey: ''}), /missing-provider-config/);
  assert.equal(safeMixedFailureCode(Error('secret provider failure')), 'mixed-attempt-failed');
});

test('successful generation logs exact input/output, raw response, actual model, usage and all checks', async t => {
  const {experimentsDir} = sandbox(t);
  const started = Date.now(); let calls = 0, sentMessages;
  const result = await generateMixedPack({senses: senses(), datasetVersion: 'fixture-v1', experimentsDir,
    provider: fakeProvider({observe: (_url, request) => {calls++; sentMessages = JSON.parse(request.body).messages;}})});
  assert.equal(result.ok, true); assert.equal(calls, 1); assert.equal(result.providerCalls, 1);
  assert.equal(result.pack.generatorModel, 'fixture/actual-model');
  assert.equal(result.pack.referenceStatus, 'normalized-unreviewed');
  assert.ok(Date.parse(result.pack.generatedAt) >= started && Date.parse(result.pack.generatedAt) <= Date.now());
  assert.ok(Date.parse(result.completedAt) >= Date.parse(result.startedAt));
  const input = jsonFile(result.logDir, 'input.json');
  assert.equal(input.datasetVersion, 'fixture-v1');
  assert.equal(input.selection.parameters.candidateCount, 12);
  assert.equal(input.selection.senses[0].normalizationModel, 'fixture/normalizer');
  const prompt = jsonFile(result.logDir, 'prompt.json');
  assert.deepEqual(promptInput(prompt).references, result.pack.references);
  const request = jsonFile(result.logDir, 'request.json');
  assert.deepEqual(request.messages, prompt);
  assert.deepEqual(sentMessages, prompt);
  assert.equal(input.promptVersion, MIXED_PROMPT_VERSION);
  assert.equal(request.promptVersion, MIXED_PROMPT_VERSION);
  assert.match(input.promptSha256, /^[a-f0-9]{64}$/);
  assert.equal(input.promptSha256, mixedSha256(JSON.stringify(sentMessages)));
  assert.equal(request.promptSha256, input.promptSha256);
  assert.equal(request.parameters.model, 'fixture/requested-model');
  const raw = readFileSync(join(result.logDir, 'output.txt'), 'utf8');
  const outer = jsonFile(result.logDir, 'response.txt');
  assert.equal(raw, outer.choices[0].message.content);
  assert.deepEqual(jsonFile(result.logDir, 'output.json'), JSON.parse(raw));
  assert.deepEqual(result.pack.questions, withProfilePictures(JSON.parse(raw).questions));
  assert.equal(jsonFile(result.logDir, 'metadata.json').actualModel, 'fixture/actual-model');
  assert.equal(jsonFile(result.logDir, 'metadata.json').usage.cost, 0.001);
  assert.equal(jsonFile(result.logDir, 'validation.json').valid, true);
  assert.equal(jsonFile(result.logDir, 'result.json').status, 'valid');
  for (const file of readdirSync(result.logDir))
    assert.equal(readFileSync(join(result.logDir, file), 'utf8').includes('fixture-secret-never-persist'), false);
});

test('prompt digests track immutable rendered content even when prompt version and pack ID stay the same', async t => {
  const {experimentsDir} = sandbox(t), candidates = senses(2), invoke = fakeProvider();
  const provider = async ({messages}) => {
    assert.equal(Object.isFrozen(messages), true);
    assert.ok(messages.every(Object.isFrozen));
    return invoke({messages});
  };
  const options = {senses: candidates, datasetVersion: 'fixture-v1', packId: 'mixed-stable-prompt-id',
    experimentsDir, randomIndex: n => n - 1, provider};
  const first = await generateMixedPack(options);
  const changed = [normalizedSense(candidates[0].term, 'a changed fixture definition'), candidates[1]];
  const second = await generateMixedPack({...options, senses: changed});
  assert.equal(first.ok, true); assert.equal(second.ok, true);
  const firstInput = jsonFile(first.logDir, 'input.json'), secondInput = jsonFile(second.logDir, 'input.json');
  assert.equal(firstInput.promptVersion, secondInput.promptVersion);
  assert.equal(firstInput.packId, secondInput.packId);
  assert.notEqual(firstInput.promptSha256, secondInput.promptSha256);
  for (const result of [first, second]) {
    const input = jsonFile(result.logDir, 'input.json'), request = jsonFile(result.logDir, 'request.json');
    assert.equal(input.promptSha256, mixedSha256(JSON.stringify(jsonFile(result.logDir, 'prompt.json'))));
    assert.equal(request.promptSha256, input.promptSha256);
    assert.equal(request.promptVersion, input.promptVersion);
  }
});

test('an in-flight paid invocation is durably counted before transport starts', async t => {
  const {experimentsDir} = sandbox(t);
  const result = await generateMixedPack({senses: senses(), datasetVersion: 'fixture-v1', experimentsDir,
    provider: fakeProvider({observe: () => {
      const [attemptId] = readdirSync(experimentsDir);
      const progress = jsonFile(experimentsDir, attemptId, 'result.json');
      assert.equal(progress.status, 'in-flight');
      assert.equal(progress.providerCalls, 1);
      const input = jsonFile(experimentsDir, attemptId, 'input.json');
      const request = jsonFile(experimentsDir, attemptId, 'request.json');
      assert.equal(request.promptVersion, input.promptVersion);
      assert.equal(request.promptSha256, input.promptSha256);
      assert.equal(request.promptSha256, mixedSha256(JSON.stringify(request.messages)));
    }})});
  assert.equal(result.ok, true);
});

test('malformed generated JSON including Markdown is captured as failure, not retried or repaired', async t => {
  const {experimentsDir} = sandbox(t);
  const contents = ['not JSON', '```json\n{"questions":[]}\n```', '{"questions":',
    '{"questions":[]} trailing text', 'Here: {"questions":[]}', '\ufeff{"questions":[]}', '{}{}'];
  for (const content of contents) {
    let calls = 0;
    const result = await generateMixedPack({senses: senses(), datasetVersion: 'fixture-v1', experimentsDir,
      provider: fakeProvider({observe: () => calls++, transform: data => ({...data, choices: [{message: {content}, finish_reason: 'stop'}]})})});
    assert.equal(result.ok, false); assert.equal(result.pack, null); assert.equal(calls, 1);
    assert.equal(readFileSync(join(result.logDir, 'output.txt'), 'utf8'), content);
    assert.equal(jsonFile(result.logDir, 'validation.json').valid, false);
    assert.equal(check(result.validation, 'json-parse').passed, false);
    assert.equal(existsSync(join(result.logDir, 'output.json')), false);
    assert.equal(existsSync(join(result.logDir, 'pack.json')), false);
  }
  assert.equal(readdirSync(experimentsDir).length, contents.length);
});

test('JSON outputs remain captured unchanged when structural validation fails', async t => {
  const {experimentsDir} = sandbox(t);
  const contents = ['{"questions":[]}', '{"questions":[{"id":"incomplete","options":["only one"]}]}',
    '{"items":[]}', '[]', 'null', 'false', '0', ' \r\n{"questions":[],"extra":{"note":"unchanged 😅"}}\t'];
  for (const content of contents) {
    let calls = 0;
    const result = await generateMixedPack({senses: senses(), datasetVersion: 'fixture-v1', experimentsDir,
      provider: fakeProvider({observe: () => calls++, transform: data => ({...data,
        choices: [{message: {content}, finish_reason: 'stop'}]})})});
    assert.equal(result.ok, false); assert.equal(result.pack, null); assert.equal(calls, 1);
    assert.equal(result.failureCode, 'mixed-validation-failed');
    assert.equal(readFileSync(join(result.logDir, 'output.txt'), 'utf8'), content);
    assert.equal(jsonFile(result.logDir, 'response.txt').choices[0].message.content, content);
    assert.deepEqual(jsonFile(result.logDir, 'output.json'), JSON.parse(content));
    assert.equal(check(result.validation, 'json-parse').passed, true);
    assert.equal(jsonFile(result.logDir, 'validation.json').valid, false);
    assert.equal(jsonFile(result.logDir, 'result.json').status, 'failed');
    assert.equal(existsSync(join(result.logDir, 'pack.json')), false);
  }
  assert.equal(readdirSync(experimentsDir).length, contents.length);
});

test('HTTP, network and timeout failures log safe codes only and do not read HTTP error bodies', async t => {
  const {experimentsDir} = sandbox(t);
  for (const [code, fetchImpl] of [
    ['llm-http-401', async () => ({ok: false, status: 401, text: () => {throw Error('never read fixture-secret');}})],
    ['llm-network', async () => {throw Error('fixture-secret-never-persist in URL');}],
    ['llm-timeout', async () => {throw Object.assign(Error('fixture-secret-never-persist'), {name: 'TimeoutError'});}]
  ]) {
    const provider = createMixedProvider({apiKey: 'fixture-secret-never-persist', model: 'fixture/model', fetchImpl});
    const result = await generateMixedPack({senses: senses(), datasetVersion: 'fixture-v1', experimentsDir, provider});
    assert.equal(result.ok, false); assert.equal(result.failureCode, code); assert.equal(result.providerCalls, 1);
    assert.equal(existsSync(join(result.logDir, 'response.txt')), false);
    assert.equal(jsonFile(result.logDir, 'validation.json').valid, false);
    for (const file of readdirSync(result.logDir))
      assert.equal(readFileSync(join(result.logDir, file), 'utf8').includes('fixture-secret'), false);
  }
});

test('provider envelope failures, missing actual models and truncated completions cannot be published', async t => {
  const {experimentsDir} = sandbox(t);
  for (const transform of [data => ({...data, model: undefined}), data => ({...data, choices: []}),
    data => ({...data, choices: [{...data.choices[0], finish_reason: 'length'}]})]) {
    const result = await generateMixedPack({senses: senses(), datasetVersion: 'fixture-v1', experimentsDir,
      provider: fakeProvider({transform})});
    assert.equal(result.ok, false);
    assert.equal(check(result.validation, 'provider-completion').passed, false);
    assert.equal(existsSync(join(result.logDir, 'response.txt')), true);
  }
  const provider = createMixedProvider({apiKey: 'fixture-key', model: 'fixture/model',
    fetchImpl: async () => new Response('malformed outer envelope', {status: 200})});
  const result = await generateMixedPack({senses: senses(), datasetVersion: 'fixture-v1', experimentsDir, provider});
  assert.equal(result.failureCode, 'llm-bad-provider-json');
  assert.equal(readFileSync(join(result.logDir, 'response.txt'), 'utf8'), 'malformed outer envelope');
});

test('incomplete or misbound reference inputs are captured before any paid invocation', async t => {
  const {experimentsDir} = sandbox(t); let calls = 0;
  for (const mutate of [sense => {sense.sources[0].title = '';}, sense => {sense.sources[0].collectedAt = null;},
    sense => {sense.sourceCandidateId = '0'.repeat(24); sense.senseId = `normalized-${sense.sourceCandidateId}`;}]) {
    const candidates = senses(); mutate(candidates[0]);
    const result = await generateMixedPack({senses: candidates, datasetVersion: 'fixture-v1', experimentsDir,
      provider: async () => {calls++; throw Error('must not invoke');}});
    assert.equal(result.ok, false); assert.equal(result.providerCalls, 0);
    assert.equal(result.failureCode, 'invalid-normalized-sense');
    assert.equal(jsonFile(result.logDir, 'validation.json').valid, false);
    assert.equal(existsSync(join(result.logDir, 'response.txt')), false);
  }
  assert.equal(calls, 0);
});

test('selection and missing-config failures are audited without invoking a provider', async t => {
  const {experimentsDir} = sandbox(t); let calls = 0;
  const result = await generateMixedPack({senses: senses(1), datasetVersion: 'fixture-v1', experimentsDir,
    provider: async () => {calls++; throw Error('must not call');}});
  assert.equal(result.ok, false); assert.equal(calls, 0); assert.equal(result.providerCalls, 0);
  assert.equal(result.failureCode, 'mixed-needs-two-distinct-terms');
  assert.equal(jsonFile(result.logDir, 'result.json').status, 'failed');
  const missing = await generateMixedPack({senses: senses(), datasetVersion: 'fixture-v1', experimentsDir,
    providerOptions: {apiKey: '', model: 'fixture/model'}});
  assert.equal(missing.failureCode, 'missing-provider-config');
  assert.equal(missing.providerCalls, 0);
  assert.equal(existsSync(join(missing.logDir, 'prompt.json')), true);
});

// Immutable publication and backward-compatible catalog retention.
function seedLegacy(outputDir) {
  mkdirSync(join(outputDir, 'packs'), {recursive: true});
  const pack = {schemaVersion: 1, senseId: 'legacy-cooked', generationSource: 'llm',
    questions: withProfilePictures(mockPackage).map(({targetMeaning, ...q}) => q)};
  const bytes = JSON.stringify(pack, null, 2) + '\n';
  writeFileSync(join(outputDir, 'packs', 'legacy-cooked.json'), bytes);
  const entry = {senseId: 'legacy-cooked', term: 'cooked', playable: true, reviewStatus: 'provisional', sha256: mixedSha256(bytes)};
  writeFileSync(join(outputDir, 'catalog.json'), JSON.stringify({schemaVersion: 1, datasetVersion: 'fixture-legacy-v1',
    source: 'github-static', generationSource: 'llm', senses: [entry], candidateCount: 1, expertValidated: false}));
  return {entry, bytes};
}

test('publisher defaults to one paid-call budget and preserves valid legacy bytes and entries', async t => {
  const {outputDir, experimentsDir} = sandbox(t); const legacy = seedLegacy(outputDir); let calls = 0;
  const result = await publishMixedBank({outputDir, experimentsDir, senses: senses(), datasetVersion: 'fixture-v1',
    provider: fakeProvider({observe: () => calls++})});
  assert.equal(calls, 1); assert.equal(result.attempted, 1); assert.equal(result.published, 1);
  const catalog = jsonFile(outputDir, 'catalog.json');
  assert.equal(catalog.schemaVersion, 2); assert.equal(catalog.source, 'github-static');
  assert.equal(catalog.generationSource, 'llm'); assert.equal(catalog.expertValidated, false);
  assert.equal(catalog.candidateCount, 12); assert.equal(catalog.status, 'ready');
  assert.deepEqual(catalog.senses, [legacy.entry]);
  assert.equal(readFileSync(join(outputDir, 'packs', 'legacy-cooked.json'), 'utf8'), legacy.bytes);
  const [entry] = catalog.packs;
  const bytes = readFileSync(join(outputDir, 'packs', entry.packId + '.json'));
  assert.equal(entry.sha256, mixedSha256(bytes)); assert.equal(entry.questionCount, 10);
  validatePublishedMixedPack(JSON.parse(bytes), {packId: entry.packId});
  assert.equal(readdirSync(outputDir).some(name => name.endsWith('.tmp')), false);
});

test('publisher consumes the dataset agent interface with no paid call and no legacy publicCatalog dependency', async t => {
  const {outputDir, experimentsDir} = sandbox(t);
  const {listNormalizedSenses, DATASET_VERSION} = await import('../src/catalog.mjs');
  let calls = 0;
  const result = await publishMixedBank({outputDir, experimentsDir,
    provider: fakeProvider({observe: () => calls++})});
  assert.equal(calls, 1); assert.equal(result.published, 1);
  const catalog = jsonFile(outputDir, 'catalog.json');
  assert.equal(catalog.datasetVersion, DATASET_VERSION);
  assert.equal(catalog.candidateCount, listNormalizedSenses().length);
  const pack = jsonFile(outputDir, 'packs', catalog.packs[0].packId + '.json');
  validatePublishedMixedPack(pack);
  assert.equal(pack.references.length, Math.min(10, new Set(listNormalizedSenses().map(s => s.term.toLowerCase())).size));
});

test('later runs add immutable packs and retain previous packs using embedded references after dataset changes', async t => {
  const {outputDir, experimentsDir} = sandbox(t);
  await publishMixedBank({outputDir, experimentsDir, senses: senses(), datasetVersion: 'fixture-v1', provider: fakeProvider()});
  const first = jsonFile(outputDir, 'catalog.json').packs[0];
  const before = readFileSync(join(outputDir, 'packs', first.packId + '.json'), 'utf8');
  const changed = senses(2).map((sense, i) => normalizedSense(sense.term, `changed fixture meaning ${i}`));
  const result = await publishMixedBank({outputDir, experimentsDir, senses: changed, datasetVersion: 'fixture-v2', provider: fakeProvider()});
  assert.equal(result.available, 2);
  assert.equal(jsonFile(outputDir, 'catalog.json').datasetVersion, 'fixture-v2');
  assert.ok(jsonFile(outputDir, 'catalog.json').packs.some(entry => entry.packId === first.packId));
  assert.equal(readFileSync(join(outputDir, 'packs', first.packId + '.json'), 'utf8'), before);
  assert.throws(() => writeImmutableMixedPack(JSON.parse(before), {outputDir}), /EEXIST/);
  assert.equal(readFileSync(join(outputDir, 'packs', first.packId + '.json'), 'utf8'), before);
});

test('failed generation never publishes invalid output and retains previously valid packs and legacy entries', async t => {
  const {outputDir, experimentsDir} = sandbox(t); const legacy = seedLegacy(outputDir);
  await publishMixedBank({outputDir, experimentsDir, senses: senses(), datasetVersion: 'fixture-v1', provider: fakeProvider()});
  const before = readFileSync(join(outputDir, 'catalog.json'), 'utf8');
  const result = await publishMixedBank({outputDir, experimentsDir, senses: senses(), datasetVersion: 'fixture-v1',
    provider: fakeProvider({transform: data => ({...data, choices: [{message: {content: '```json\n{}\n```'}}]})})});
  assert.equal(result.published, 0); assert.equal(result.available, 1); assert.equal(result.failures.length, 1);
  assert.equal(readFileSync(join(outputDir, 'catalog.json'), 'utf8'), before);
  assert.equal(readFileSync(join(outputDir, 'packs', 'legacy-cooked.json'), 'utf8'), legacy.bytes);
  assert.equal(readdirSync(join(outputDir, 'packs')).length, 2);
});

test('publisher stops at the first valid pack within the attempt budget', async t => {
  const {outputDir, experimentsDir} = sandbox(t);
  let call = 0;
  const provider = fakeProvider({transform: envelope => {
    call++;
    return call % 3 === 1 ? envelope : {...envelope, choices: [{message: {content: '{}'}}]};
  }});
  const first = await publishMixedBank({outputDir, experimentsDir, maxPacks: 3, senses: senses(),
    datasetVersion: 'fixture-v1', provider});
  assert.equal(call, 1); assert.equal(first.attempted, 1); assert.equal(first.published, 1);
  assert.equal(first.stoppedEarly, true);
  assert.equal(jsonFile(outputDir, 'catalog.json').packs.length, 1);

  const second = await publishMixedBank({outputDir, experimentsDir, maxPacks: 3, senses: senses(),
    datasetVersion: 'fixture-v2', provider, stopOnFirstValid: false});
  assert.equal(call, 4); assert.equal(second.attempted, 3); assert.equal(second.published, 1);
  assert.equal(second.stoppedEarly, false);
  assert.equal(jsonFile(outputDir, 'catalog.json').packs.length, 2);
});

test('explicit max three attempts are bounded without retrying failed completions', async t => {
  const {outputDir, experimentsDir} = sandbox(t); let calls = 0;
  const result = await publishMixedBank({outputDir, experimentsDir, maxPacks: 3, senses: senses(), datasetVersion: 'fixture-v1',
    provider: fakeProvider({observe: () => calls++, transform: data => ({...data, choices: [{message: {content: '{}'}}]})})});
  assert.equal(calls, 3); assert.equal(result.attempted, 3); assert.equal(result.providerCalls, 3);
  assert.equal(result.failures.length, 3); assert.equal(result.published, 0);
  assert.equal(jsonFile(outputDir, 'catalog.json').status, 'awaiting-generation');
  for (const maxPacks of [0, 4, 1.5, NaN])
    await assert.rejects(publishMixedBank({outputDir, experimentsDir, maxPacks, senses: senses(), datasetVersion: 'fixture-v1'}), /1-to-3/);
});

test('catalog retention is bounded to twelve while all old pack files remain immutable', t => {
  const {outputDir} = sandbox(t); const additions = [];
  for (let i = 0; i < 14; i++) additions.push(writeImmutableMixedPack(fixture(`mixed-retention-${i}`).pack, {outputDir}));
  const catalog = writeMixedCatalog({outputDir, datasetVersion: 'fixture-v1', candidateCount: 12, additions});
  assert.equal(catalog.packs.length, 12); assert.equal(readdirSync(join(outputDir, 'packs')).length, 14);
  const again = writeMixedCatalog({outputDir, datasetVersion: 'fixture-v1', candidateCount: 12});
  assert.deepEqual(again, catalog); assert.equal(again.bankVersion, catalog.bankVersion);
  assert.throws(() => writeMixedCatalog({outputDir, datasetVersion: 'fixture-v1', candidateCount: 12, retention: 13}), /retention/);
});

test('runtime integrity rejects newline-converted pack bytes unless their exact hashes match', t => {
  const {outputDir} = sandbox(t); const legacy = seedLegacy(outputDir);
  const entry = writeImmutableMixedPack(fixture('mixed-exact-bytes').pack, {outputDir});
  const catalog = writeMixedCatalog({outputDir, datasetVersion: 'fixture-v1', candidateCount: 12, additions: [entry]});
  const catalogPath = join(outputDir, 'catalog.json'), before = readFileSync(catalogPath);
  const legacyPath = join(outputDir, 'packs', 'legacy-cooked.json');
  const mixedPath = join(outputDir, 'packs', entry.packId + '.json');
  const legacyCrlf = legacy.bytes.replace(/\n/g, '\r\n');
  const mixedCrlf = readFileSync(mixedPath, 'utf8').replace(/\n/g, '\r\n');
  writeFileSync(legacyPath, legacyCrlf); writeFileSync(mixedPath, mixedCrlf);
  assert.notEqual(mixedSha256(mixedCrlf), entry.sha256);
  assert.throws(() => writeMixedCatalog({outputDir, datasetVersion: 'fixture-v1', candidateCount: 12,
    additions: [entry]}), /bank-hash-mismatch/);
  assert.deepEqual(readFileSync(catalogPath), before);
  const next = writeMixedCatalog({outputDir, datasetVersion: 'fixture-v1', candidateCount: 12});
  assert.deepEqual(next.packs, []); assert.deepEqual(next.senses, []);
  const exactLegacy = {...legacy.entry, sha256: mixedSha256(legacyCrlf)};
  const exactMixed = {...entry, sha256: mixedSha256(mixedCrlf)};
  writeFileSync(catalogPath, JSON.stringify({...catalog, senses: [exactLegacy], packs: [exactMixed]}));
  const verified = writeMixedCatalog({outputDir, datasetVersion: 'fixture-v1', candidateCount: 12});
  assert.deepEqual(verified.senses, [exactLegacy]); assert.deepEqual(verified.packs, [exactMixed]);
  assert.equal(readFileSync(legacyPath, 'utf8'), legacyCrlf);
  assert.equal(readFileSync(mixedPath, 'utf8'), mixedCrlf);
});

test('retention rejects incomplete embedded provenance even when the catalog hash matches the changed bytes', t => {
  const {outputDir} = sandbox(t), pack = fixture('mixed-invalid-provenance').pack;
  const entry = writeImmutableMixedPack(pack, {outputDir});
  const catalog = writeMixedCatalog({outputDir, datasetVersion: 'fixture-v1', candidateCount: 12, additions: [entry]});
  pack.references[0].referenceSources[0].title = '';
  const text = JSON.stringify(pack), packPath = join(outputDir, 'packs', pack.packId + '.json');
  writeFileSync(packPath, text);
  catalog.packs[0].sha256 = mixedSha256(text);
  writeFileSync(join(outputDir, 'catalog.json'), JSON.stringify(catalog));
  const next = writeMixedCatalog({outputDir, datasetVersion: 'fixture-v1', candidateCount: 12});
  assert.deepEqual(next.packs, []);
  assert.equal(readFileSync(packPath, 'utf8'), text);
});

test('hash-corrupt, structurally invalid, unknown and traversal entries cannot enter a catalog', t => {
  const {outputDir} = sandbox(t); const legacy = seedLegacy(outputDir);
  const entry = writeImmutableMixedPack(fixture().pack, {outputDir});
  writeMixedCatalog({outputDir, datasetVersion: 'fixture-v1', candidateCount: 12, additions: [entry]});
  writeFileSync(join(outputDir, 'packs', entry.packId + '.json'), '{}');
  const before = jsonFile(outputDir, 'catalog.json');
  before.packs.push({packId: '../escape', sha256: 'a'.repeat(64), questionCount: 10});
  before.senses.push({senseId: '../escape', term: 'bad', sha256: 'b'.repeat(64)});
  writeFileSync(join(outputDir, 'catalog.json'), JSON.stringify(before));
  const catalog = writeMixedCatalog({outputDir, datasetVersion: 'fixture-v1', candidateCount: 12});
  assert.deepEqual(catalog.packs, []); assert.deepEqual(catalog.senses, [legacy.entry]);
  assert.equal(readFileSync(join(outputDir, 'packs', entry.packId + '.json'), 'utf8'), '{}');
  const invalid = fixture('mixed-invalid-pack').pack; invalid.questions[0].contextCategory = 'technology';
  assert.throws(() => writeImmutableMixedPack(invalid, {outputDir}), /invalid-published-mixed/);
  assert.equal(existsSync(join(outputDir, 'packs', 'mixed-invalid-pack.json')), false);
});

test('an invalid addition or corrupt catalog leaves the existing catalog bytes untouched', async t => {
  const {outputDir, experimentsDir} = sandbox(t); seedLegacy(outputDir);
  const before = readFileSync(join(outputDir, 'catalog.json'), 'utf8');
  assert.throws(() => writeMixedCatalog({outputDir, datasetVersion: 'fixture-v1', candidateCount: 12,
    additions: [{packId: 'missing', sha256: 'a'.repeat(64), questionCount: 10}]}));
  assert.equal(readFileSync(join(outputDir, 'catalog.json'), 'utf8'), before);
  writeFileSync(join(outputDir, 'catalog.json'), '{invalid'); let calls = 0;
  await assert.rejects(publishMixedBank({outputDir, experimentsDir, senses: senses(), datasetVersion: 'fixture-v1',
    provider: fakeProvider({observe: () => calls++})}), /invalid-existing-bank-catalog/);
  assert.equal(calls, 0); assert.equal(readFileSync(join(outputDir, 'catalog.json'), 'utf8'), '{invalid');
});

test('prompt versions are archived and the active template binds the term to one surface', () => {
  const read = version => readFileSync(new URL(`../prompts/${version}.txt`, import.meta.url), 'utf8');
  const v1 = read('mixed-v1'), v2 = read('mixed-v2'), v3 = read('mixed-v3'), v4 = read('mixed-v4');
  const v5 = read('mixed-v5'), v6 = read('mixed-v6'), v7 = read('mixed-v7'), v8 = read('mixed-v8');
  const v9 = read('mixed-v9');
  const v10 = read('mixed-v10');
  assert.equal(MIXED_PROMPT_VERSION, 'mixed-v10');
  // Archive rule: the template that produced a recorded attempt is never edited in place.
  assert.ok(v7.includes('The term may also occur any number of times in the post, other replies and questionComment'));
  assert.ok(v7.includes('Example for cooked: "What do you mean by cooked? Are you cooking food or something?"'));
  assert.ok(v8.includes('Never inflect, pluralize, conjugate, hyphenate or derive it'));
  assert.ok(v9.includes('replyTexts has exactly four strings, aligned with options in order'));
  assert.ok(v10.includes('never the @guest_01 pattern'));
  assert.ok(v6.includes('The exact slang term MUST appear EXACTLY ONCE'));
  assert.ok(v1.includes('A natural short post using the exact slang term'));
  assert.ok(v2.includes('questionComment may mention the term but cannot substitute'));
  assert.ok(v3.includes('It MUST contain the exact slang term verbatim, in addition to its presence in post.content'));
  assert.ok(v4.includes('Copy the reference string character-for-character into BOTH `targetMeaning` and the one correct option'));
  assert.ok(v5.includes('The exact slang term MUST appear EXACTLY ONCE'));
  assert.ok(v5.includes('Set questionTarget to "post" when the term is in post.content'));
  const value = fixture();
  const messages = createMixedPrompt({packId: value.pack.packId, ...value});
  assert.equal(messages[1].content.includes('questionTarget'), true);
  assert.equal(messages[1].content.includes('{{INPUT_JSON}}'), false);
});

// The term belongs to exactly one social surface: post.content or one reply.
function replyTargeted() {
  const value = fixture();
  value.questions = value.questions.map(q => {
    const copy = structuredClone(q);
    copy.post.content = 'An ordinary post that never uses the slang itself.';
    copy.post.comments = [{handle: '@fictional_reply', text: `Honestly, that was pure ${q.slang}.`, avatarId: 'guest_02'}];
    copy.questionComment = 'Wait, what is that supposed to mean here?';
    copy.questionTarget = 'reply';
    copy.targetHandle = '@fictional_reply';
    copy.question = `What does '${q.slang}' mean in @fictional_reply's reply?`;
    return copy;
  });
  return {...value, raw: JSON.stringify({questions: value.questions})};
}
// These cases pin the v7/v8 repeat-and-bind contract; v9 adds its own checks below.
const evaluate = (value, promptVersion = 'mixed-v8') => evaluateMixedResponse(JSON.stringify({questions: value.questions}), {references: value.references, promptVersion});

test('a reply-targeted term is valid and binds the question to that reply', () => {
  const result = evaluate(replyTargeted());
  assert.equal(result.valid, true, JSON.stringify(result.errors));
});

test('the term must occur exactly once and the declared target must name that surface', () => {
  const both = replyTargeted();
  both.questions[0].post.content = `An ordinary post that still says ${both.questions[0].slang} once.`;
  assert.ok(evaluate(both, 'mixed-v6').errors.some(e => e.includes('term-presence')));
  assert.equal(evaluate(both).valid, true);

  const none = replyTargeted();
  none.questions[0].post.comments[0].text = 'Honestly, that was a strange thing to say.';
  assert.ok(evaluate(none).errors.some(e => e.includes('term-target-mismatch')));

  const wrongTarget = replyTargeted();
  wrongTarget.questions[0].questionTarget = 'post';
  wrongTarget.questions[0].targetHandle = wrongTarget.questions[0].post.handle;
  assert.ok(evaluate(wrongTarget).errors.some(e => e.includes('term-target-mismatch')));

  const wrongHandle = replyTargeted();
  wrongHandle.questions[0].targetHandle = '@someone_else';
  assert.ok(evaluate(wrongHandle).errors.some(e => e.includes('term-target-handle-mismatch')));

  const unbound = replyTargeted();
  unbound.questions[0].question = `What does '${unbound.questions[0].slang}' mean here?`;
  assert.ok(evaluate(unbound).errors.some(e => e.includes('term-question-binding')));

  const badShape = replyTargeted();
  badShape.questions[0].questionTarget = 'thread';
  assert.ok(evaluate(badShape).errors.some(e => e.includes('term-target-shape')));
});

test('repeat-term versions target a unique named surface, permit repeats and still block disclosures', () => {
  const value = replyTargeted();
  const q = value.questions[0];
  q.post.content = `${q.slang} and ${q.slang} showed up in the post.`;
  q.post.comments.push({handle: '@other', text: `I heard ${q.slang} too.`, avatarId: 'guest_03'});
  q.questionComment = `Wait, what does ${q.slang} mean here?`;
  assert.equal(evaluate(value).valid, true, JSON.stringify(evaluate(value).errors));
  const pack = {...value.pack, questions: value.questions, promptVersion: 'mixed-v7'};
  assert.doesNotThrow(() => validatePublishedMixedPack(pack));
  assert.throws(() => validatePublishedMixedPack({...pack, promptVersion: 'mixed-v6'}), /invalid-published-mixed-questions/);
  assert.ok(validateQuestion(q).includes('term-presence'));
  assert.equal(validateQuestion(q, {promptVersion: 'mixed-v7'}).length, 0);
  assert.equal(validateQuestion(q, {promptVersion: 'mixed-v8'}).length, 0);
  assert.doesNotThrow(() => validatePublishedMixedPack({...pack, promptVersion: 'mixed-v8'}));
  q.post.comments.push({handle: '@FICTIONAL_REPLY', text: 'Another reply.', avatarId: 'guest_03'});
  assert.ok(evaluate(value).errors.some(e => e.includes('term-target-handle-ambiguous')));
  q.post.comments.pop();
  q.targetHandle = '@other';
  q.post.comments[1].text = 'No slang in this reply.';
  q.question = `What does it mean in @other's reply?`;
  assert.ok(evaluate(value).errors.some(e => e.includes('term-target-mismatch')));
  q.targetHandle = '@fictional_reply';
  q.question = `What does it mean in @fictional_reply's reply?`;
  q.questionComment = `Does ${q.slang} mean ${q.targetMeaning}?`;
  assert.ok(evaluate(value).errors.some(e => e.includes('question-comment-leakage')));
  q.questionComment = `Is ${q.slang} ${q.options.find(o => o !== q.targetMeaning)}?`;
  assert.ok(evaluate(value).errors.some(e => e.includes('question-comment-leakage')));
  q.questionComment = `Is the answer B for ${q.slang}?`;
  assert.ok(evaluate(value).errors.some(e => e.includes('answer-index-leakage')));
  delete q.targetHandle;
  assert.ok(evaluate(value).errors.some(e => e.includes('term-target-handle')));
});

test('legacy questions without questionTarget keep only the exactly-once rule', () => {
  // Legacy packs are checked by validateQuestion, not by the mixed contract.
  const plain = fixture().questions.map(q => { const copy = structuredClone(q); delete copy.questionTarget; return copy; });
  assert.equal(plain.some(q => validateQuestion(q).some(e => e.startsWith('term-'))), false);
  for (const q of plain) q.post.content = 'A post that never says the slang.';
  assert.ok(plain.some(q => validateQuestion(q).includes('term-presence')));
});

test('mixed-v9 enforces the 5/5 surface mix, slang-free posts and four learner replies', () => {
  const value = fixture();
  const v9 = questions => evaluateMixedResponse(JSON.stringify({questions}),
    {references: value.references, assignments: value.assignments, promptVersion: 'mixed-v9'});
  assert.equal(v9(value.questions).valid, true, JSON.stringify(v9(value.questions).errors));
  const mix = v9(value.questions).checks.find(check => check.id === 'reply-target-mix');
  assert.equal(mix.passed, true);
  assert.equal(value.questions.filter(q => q.questionTarget === 'reply').length, 5);

  // Every term back in its post: the target mix is a generation contract, not a replay rule.
  const postOnly = structuredClone(value.questions).map(q => ({...q, questionTarget: 'post', targetHandle: q.post.handle,
    post: {...q.post, content: `My ${q.slang} moment after the group chat went quiet.`},
    question: `What does '${q.slang}' mean in this post?`}));
  assert.ok(v9(postOnly).errors.includes('reply-target-count:0'));
  assert.equal(evaluateMixedResponse(JSON.stringify({questions: postOnly}),
    {references: value.references, promptVersion: 'mixed-v9'}).valid, true);

  const mutate = change => {
    const questions = structuredClone(value.questions);
    change(questions);
    return evaluateMixedResponse(JSON.stringify({questions}),
      {references: value.references, promptVersion: 'mixed-v9'}).errors;
  };
  assert.ok(mutate(q => { q[1].post.content = `The post itself now says ${q[1].slang} once.`; })
    .some(e => e.includes('reply-target-post-has-term')));
  assert.ok(mutate(q => { q[3].post.comments.push({handle: '@stranger', text: `Plus ${q[3].slang}.`, avatarId: 'guest_03'}); })
    .some(e => e.includes('reply-target-comment')));
  assert.ok(mutate(q => { q[5].post.comments[0].handle = q[5].post.handle; q[5].targetHandle = q[5].post.handle; })
    .some(e => e.includes('reply-target-author')));
  assert.ok(mutate(q => { delete q[0].replyTexts; }).some(e => e.includes('reply-texts-shape')));
  assert.ok(mutate(q => { q[0].replyTexts[1] = q[0].replyTexts[0]; }).some(e => e.includes('reply-texts-duplicate')));
  assert.ok(mutate(q => { q[0].replyTexts[0] = q[0].options[0]; }).some(e => e.includes('reply-texts-copy-option')));
  assert.ok(mutate(q => { q[0].replyTexts[0] = 'Not naming the word here at all, just a snack plate.'; })
    .some(e => e.includes('reply-texts-term')));
  assert.ok(mutate(q => { q[0].replyTexts[0] = `${q[0].slang} ${'x'.repeat(260)}`; }).some(e => e.includes('reply-texts-long')));
  // v8 packs without the new field keep validating exactly as recorded.
  assert.equal(evaluateMixedResponse(value.raw, {references: value.references, promptVersion: 'mixed-v8'}).valid, true);
});

// Growth plan: unused terms first, then least-used terms, one term per question.
test('the next pack covers unused slang first, then the least-used terms', () => {
  const pool = senses(20);
  const first = planPackTerms(pool, {usedCounts: new Map()});
  assert.equal(first.length, 10);
  assert.equal(new Set(first).size, 10);
  const usedOnce = new Map(first.map(term => [term, 1]));
  const second = planPackTerms(pool, {usedCounts: usedOnce});
  assert.equal(new Set([...first, ...second]).size, 20);
  const balanced = new Map([...first, ...second].map(term => [term, 1]));
  const third = planPackTerms(pool, {usedCounts: balanced});
  assert.equal(third.length, 10);
  assert.ok(third.every(term => balanced.get(term) === 1), 'least-used terms repeat before a third use');
  const scoped = sensesForTerms(pool, third);
  assert.ok(scoped.length >= 10);
  assert.ok(scoped.every(sense => third.includes(sense.term)));
  assert.throws(() => planPackTerms(senses(5), {usedCounts: new Map()}), /not-enough-distinct-terms/);
  assert.throws(() => planPackTerms([], {}), /invalid-normalized-dataset/);
  assert.throws(() => planPackTerms(senses(12), {usedCounts: []}), /invalid-term-usage/);
});

test('published-term counts are derived from the questions a catalog already indexes', () => {
  const counts = countPublishedTerms([fixture().pack]);
  assert.equal(counts.size, 10);
  assert.ok([...counts.values()].every(value => value === 1));
  assert.throws(() => countPublishedTerms('not-a-list'), /invalid-published-pack-list/);
});

test('a batch round spends attempts on the planned terms and publishes only a valid pack', async t => {
  const {outputDir, experimentsDir} = sandbox(t);
  writeMixedCatalog({outputDir, datasetVersion: 'fixture-dataset-v1', candidateCount: 20});
  const pool = senses(20);
  const result = await generatePackRound({senses: pool, usedCounts: new Map(), outputDir, experimentsDir,
    provider: fakeProvider(), maxAttempts: 2});
  assert.equal(result.ok, true, JSON.stringify(result.attempts));
  assert.equal(result.attempts.length, 1);
  assert.deepEqual(result.terms.slice().sort(), planPackTerms(pool, {usedCounts: new Map()}).slice().sort());
  const catalog = jsonFile(outputDir, 'catalog.json');
  assert.equal(catalog.packs.length, 1);
  const pack = jsonFile(outputDir, 'packs', catalog.packs[0].packId + '.json');
  assert.equal(pack.questions.length, 10);
  assert.deepEqual([...new Set(pack.questions.map(q => q.slang))].sort(), result.terms.slice().sort());
  assert.deepEqual([...countPublishedTerms([pack]).keys()].sort(), result.terms.slice().sort());
});

test('a failed batch round audits every attempt and publishes nothing', async t => {
  const {outputDir, experimentsDir} = sandbox(t);
  writeMixedCatalog({outputDir, datasetVersion: 'fixture-dataset-v1', candidateCount: 20});
  const provider = createMixedProvider({apiKey: 'fixture-secret-never-persist', model: 'fixture/requested-model',
    fetchImpl: async () => new Response(JSON.stringify(envelope('{"questions":[]}')), {status: 200})});
  const result = await generatePackRound({senses: senses(20), usedCounts: new Map(), outputDir, experimentsDir,
    provider, maxAttempts: 3});
  assert.equal(result.ok, false);
  assert.equal(result.attempts.length, 3);
  assert.ok(result.attempts.every(attempt => attempt.status === 'failed'));
  assert.equal(jsonFile(outputDir, 'catalog.json').packs.length, 0);
  assert.deepEqual(existsSync(join(outputDir, 'packs')) ? readdirSync(join(outputDir, 'packs')) : [], []);
});
