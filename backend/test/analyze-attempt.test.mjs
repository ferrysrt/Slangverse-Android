import {test} from 'node:test';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
import {analyzeQuestion, summarizeQuestions, loadQuestions} from '../scripts/analyze-mixed-attempt.mjs';

const question = overrides => ({
  id: 'q1', slang: 'cooked', targetMeaning: 'Exhausted or mentally out of it.',
  questionTarget: 'post', targetHandle: '@author',
  questionComment: 'Wait, what do you mean by cooked? Are you cooking food or something?',
  question: 'What does cooked mean in the post?',
  options: ['A literal meal.', 'Exhausted or mentally out of it.', 'A cooking skill.', 'A kitchen tool.'],
  correctIndex: 1,
  post: {handle: '@author', content: 'Three deadlines today and I am cooked.', comments: [
    {handle: '@friend', text: 'same honestly', avatarId: 'guest_02'}
  ]},
  ...overrides
});

test('the analyzer flags exam register, missing terms and paraphrased options', () => {
  const conversational = analyzeQuestion(question());
  assert.equal(conversational.metaFeedRegister, false);
  assert.equal(conversational.termOnDeclaredTarget, true);
  assert.equal(conversational.verbatimCorrectOption, true);
  assert.equal(conversational.termQuotedInComment, true);
  assert.equal(conversational.endsWithQuestion, true);

  const exam = analyzeQuestion(question({questionComment: 'Which personal quality is being praised in the post?'}));
  assert.equal(exam.metaFeedRegister, true);
  assert.equal(exam.examOpener, true);
  assert.equal(exam.termQuotedInComment, false);

  // Inflected forms are not the contract term; that is a validation failure, not a style signal.
  const inflected = analyzeQuestion(question({
    slang: 'larp', post: {handle: '@author', content: 'Stop larping about it.', comments: []}
  }));
  assert.equal(inflected.termOnDeclaredTarget, false);

  const paraphrased = analyzeQuestion(question({
    options: ['A literal meal.', 'Really worn out.', 'A cooking skill.', 'A kitchen tool.']
  }));
  assert.equal(paraphrased.verbatimCorrectOption, false);
});

test('the shipped v5 pack reproduces the recorded editorial signals', () => {
  const pack = loadQuestions(fileURLToPath(new URL(
    '../../question-bank/packs/mixed-20261117t194601433z-8ca3b36c-8d92-4c8f-90d8-e61c571d6597.json', import.meta.url)));
  const {summary} = summarizeQuestions(pack);
  assert.equal(summary.questions, 10);
  // Editorial observation: every v5 question comment addressed the feed as an object.
  assert.equal(summary.metaFeedRegister, 10);
  assert.equal(summary.termQuotedInComment, 0);
  assert.equal(summary.verbatimCorrectOption, 10);
});
