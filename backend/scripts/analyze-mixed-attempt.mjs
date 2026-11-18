import {readFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {normalize} from '../src/game.mjs';

/**
 * Editorial signals for questionComment quality. These are heuristics for human review, NOT validation:
 * passing them does not make a comment natural, and failing them does not make a pack unpublishable.
 */
const META_FEED = /\b(?:the|this|that)\s+(?:post|reply|commenter|writer|message)\b|\breaders?\b|\bdescribed\s+here\b/i;
const EXAM_OPENER = /^\s*(?:what|which|how\s+should)\b/i;
const STOP = new Set(['the', 'and', 'for', 'that', 'this', 'with', 'from', 'someone', 'something',
  'about', 'into', 'your', 'their', 'they', 'them', 'than', 'then', 'when', 'what', 'which', 'while']);

const words = value => normalize(value).split(' ').filter(word => word.length > 3 && !STOP.has(word));

export function termCount(text, term) {
  const key = normalize(term);
  if (!key) return 0;
  const target = key.split(' ').filter(Boolean);
  const hay = normalize(text).split(' ').filter(Boolean);
  let total = 0;
  for (let i = 0; i + target.length <= hay.length; i++)
    if (hay.slice(i, i + target.length).join(' ') === key) total++;
  return total;
}

function surfaceFlags(question) {
  const post = question.post ?? {};
  const comments = Array.isArray(post.comments) ? post.comments : [];
  const postHits = termCount(post.content ?? '', question.slang);
  const replyHits = comments.map(comment => termCount(comment.text ?? '', question.slang));
  const declared = question.questionTarget === 'post'
    ? {handle: post.handle, hits: postHits}
    : {handle: question.targetHandle,
      hits: comments.filter(comment => comment.handle === question.targetHandle)
        .reduce((total, comment) => total + termCount(comment.text ?? '', question.slang), 0)};
  return {postHits, replyHits, declaredHits: declared.hits};
}

export function analyzeQuestion(question) {
  const comment = typeof question.questionComment === 'string' ? question.questionComment : '';
  const meaning = typeof question.targetMeaning === 'string' ? question.targetMeaning : '';
  const correct = Array.isArray(question.options) ? question.options[question.correctIndex] : undefined;
  const surfaces = surfaceFlags(question);
  const shared = words(meaning).filter(word => words(comment).includes(word));
  return {
    id: question.id ?? null,
    slang: question.slang ?? null,
    questionTarget: question.questionTarget ?? null,
    questionComment: comment,
    termOnDeclaredTarget: surfaces.declaredHits > 0,
    termOccurrences: {post: surfaces.postHits, replies: surfaces.replyHits},
    termQuotedInComment: termCount(comment, question.slang) > 0,
    handleInComment: typeof question.targetHandle === 'string' && question.targetHandle.length > 1 &&
      comment.toLowerCase().includes(question.targetHandle.toLowerCase()),
    metaFeedRegister: META_FEED.test(comment),
    examOpener: EXAM_OPENER.test(comment),
    endsWithQuestion: comment.trim().endsWith('?'),
    commentLength: comment.length,
    verbatimCorrectOption: correct === question.targetMeaning,
    // Informational only: shared content words between the comment and the reference meaning.
    sharedMeaningWords: shared
  };
}

export function summarizeQuestions(questions) {
  const items = questions.map(analyzeQuestion);
  const count = predicate => items.filter(predicate).length;
  return {
    items,
    summary: {
      questions: items.length,
      conversationalComment: count(item => !item.metaFeedRegister),
      metaFeedRegister: count(item => item.metaFeedRegister),
      examOpener: count(item => item.examOpener),
      termQuotedInComment: count(item => item.termQuotedInComment),
      handleInComment: count(item => item.handleInComment),
      endsWithQuestion: count(item => item.endsWithQuestion),
      termOnDeclaredTarget: count(item => item.termOnDeclaredTarget),
      verbatimCorrectOption: count(item => item.verbatimCorrectOption),
      sharedMeaningWords: count(item => item.sharedMeaningWords.length > 0)
    }
  };
}

export function loadQuestions(target) {
  const raw = JSON.parse(readFileSync(target, 'utf8'));
  if (Array.isArray(raw?.questions)) return raw.questions;
  throw Error('input-must-contain-questions-array');
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const target = process.argv[2];
  if (!target) {
    console.error('usage: node scripts/analyze-mixed-attempt.mjs <attempt-dir|pack.json>');
    process.exitCode = 2;
  } else {
    const file = target.endsWith('.json') ? target : resolve(target, 'output.json');
    const {items, summary} = summarizeQuestions(loadQuestions(resolve(file)));
    if (process.argv.includes('--json')) console.log(JSON.stringify({summary, items}, null, 2));
    else {
      for (const item of items) {
        const flags = [
          item.termOnDeclaredTarget ? '' : 'TERM-MISSING',
          item.verbatimCorrectOption ? '' : 'OPTION-PARAPHRASED',
          item.metaFeedRegister ? 'META-FEED' : '',
          item.termQuotedInComment ? 'quotes-term' : '',
          item.sharedMeaningWords.length ? `shares:${item.sharedMeaningWords.join(',')}` : ''
        ].filter(Boolean).join(' ');
        console.log(`${item.termOnDeclaredTarget ? '-' : '!'} ${String(item.slang).padEnd(12)} ${flags}`);
        console.log(`    "${item.questionComment}"`);
      }
      console.log('\n' + JSON.stringify(summary, null, 2));
    }
  }
}
