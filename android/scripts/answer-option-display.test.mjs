import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';
import assert from 'node:assert/strict';

const app = readFileSync(fileURLToPath(new URL('../app/src/main/java/id/slangverse/app/SlangverseApp.kt', import.meta.url)), 'utf8');

test('thread review keeps the same safe options and posted reply as the feed', () => {
  assert.match(app, /val displayedOptions = displayAnswerOptions\(thread\.options\)/);
  assert.match(app, /displayedOptions == null/);
  assert.match(app, /displayedOptions\.forEachIndexed/);
  assert.match(app, /StoryPlayerReply\(postedReply\(thread, answeredIndex\)\)/);
  assert.match(app, /PracticeHistoryDialog\(feedProgress,[\s\S]+?openConversation\(postId\)/);
  assert.match(app, /EditorialScreen\.Conversation -> \{[\s\S]+?FeedSocialPost\(post, feedProgress\.answers\[post\.thread\.id\]/);
});
