import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

// Source-level guards for environments without the Android build toolchain.
// These do not replace Compose interaction or saved-state restoration tests.
const source = (name) => readFileSync(
  new URL(`../app/src/main/java/id/slangverse/app/${name}.kt`, import.meta.url), 'utf8');
const feed = source('SlangverseApp').match(/\bfun FeedSocialPost\(([\s\S]*?)\n\}/)?.[1];
const training = source('RemoteTrainingPanel');
const detective = source('ContextDetective');

function assertResetsExpanded(text, name, expectedCount) {
  const resets = [...text.matchAll(new RegExp(`\\b${name}\\s*=\\s*(true|false)\\b`, 'g'))];
  assert.equal(resets.length, expectedCount);
  assert.ok(resets.every((reset) => reset[1] === 'true'), 'Every reply reset must expand replies');
  assert.match(text, new RegExp(`${name} = !${name}`), 'Replies must remain collapsible');
}

test('feed opens replies by default when they contain the target slang', () => {
  assert.ok(feed, 'FeedSocialPost source not found');
  assert.match(feed, /initiallyExpanded: Boolean = post\.termTargetHandle != null/);
  assert.match(feed, /var expanded by rememberSaveable\(thread\.id\) \{ mutableStateOf\(initiallyExpanded\) \}/);
  assert.match(feed, /onClick = \{ expanded = !expanded \}/);
  assert.match(feed, /if \(expanded\) StoryReplies\(story\.replies, onProfile, post\.termTargetHandle\)/);
  assert.match(source('SocialSimulationUi'), /replies\.forEach \{ reply ->/);
});

test('feed answer choices still require opening the reply composer', () => {
  assert.match(feed, /var replying by rememberSaveable\(thread\.id\) \{ mutableStateOf\(false\) \}/);
  assert.match(feed, /if \(answeredIndex == null && !replying\)/);
  assert.match(feed, /EditorialPrimary\("Answer Kai"\) \{ replying = true \}/);
  assert.match(feed, /else if \(answeredIndex == null\) \{\s*Text\([^\n]+\n\s*displayedOptions\.forEachIndexed/);
  assert.match(feed, /enabled = selected in thread\.options\.indices\) \{ onAnswer\(selected\) \}/);
});

test('training starts and resets expanded, rendering every supported reply in order', () => {
  assert.match(training, /var expandedReplies by remember \{ mutableStateOf\(true\) \}/);
  assertResetsExpanded(training, 'expandedReplies', 3); // Load, new session, next conversation.
  assert.match(training, /current\.post\.comments\.take\(if \(expandedReplies\) current\.post\.comments\.size else 1\)\.forEachIndexed/);
  assert.match(training, /var questionOpened by remember \{ mutableStateOf\(false\) \}/);
  assert.match(training, /if \(!questionOpened && result == null\)/);
  assert.match(training, /Action\("REPLY WITH THE MEANING ↓"\) \{ questionOpened = true \}/);
  assert.doesNotMatch(training, /questionOpened = true(?! \})/);
});

test('detective starts and resets expanded without opening answer choices', () => {
  assert.match(detective, /var expanded by remember\(startCaseId\) \{ mutableStateOf\(true\) \}/);
  assertResetsExpanded(detective, 'expanded', 2); // Next case and replay.
  assert.match(detective, /item\.comments\.take\(if \(expanded\) item\.comments\.size else 1\)/);
  assert.match(detective, /var questionOpened by remember\(startCaseId\) \{ mutableStateOf\(false\) \}/);
  assert.match(detective, /if \(!questionOpened && step == DetectiveStep\.Investigate\)/);
  assert.match(detective, /CDButton\("REPLY WITH THE MEANING ↓", onClick = \{ questionOpened = true \}\)/);
  assert.doesNotMatch(detective, /questionOpened = true(?! \})/);
});
