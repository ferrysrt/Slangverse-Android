import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';
import assert from 'node:assert/strict';

const app = readFileSync(fileURLToPath(new URL('../app/src/main/java/id/slangverse/app/SlangverseApp.kt', import.meta.url)), 'utf8');

test('feed hides unsafe choices and reuses displayed labels for answers and progress', () => {
  assert.match(app, /val displayedOptions = displayAnswerOptions\(thread\.options\)/);
  assert.match(app, /displayedOptions == null/);
  assert.match(app, /displayedOptions\.forEachIndexed/);
  assert.match(app, /StoryPlayerReply\(displayedOptions\[answeredIndex\]\)/);
  assert.match(app, /displayAnswerOptions\(post\.thread\.options\)/);
  assert.doesNotMatch(app, /Your reply: \$\{post\.thread\.options\[result\.selectedIndex\]\}/);
});
