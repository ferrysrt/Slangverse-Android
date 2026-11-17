import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

// Source guards, not a substitute for Kotlin parser/Compose tests.
const source = (name) => readFileSync(
  new URL(`../app/src/main/java/id/slangverse/app/${name}.kt`, import.meta.url), 'utf8');
const app = source('SlangverseApp');
const published = source('PublishedFeed');
const bank = source('GithubStaticBank');
const effect = app.split('LaunchedEffect(bankRoot, bankRefresh, offlineSample) {')[1]
  .split('// Last local demo case-file metrics')[0];
const success = effect.split('try {')[1].split('} catch (cancelled:')[0];
const failure = effect.split('} catch (error: Exception) {')[1].split('} finally {')[0];

test('Home has context tabs but no slang selection chips or selected-bank-sense state', () => {
  assert.doesNotMatch(app, /selectedBankSense|onSelect =/);
  assert.doesNotMatch(published, /FilterChip|selectedSense|onSelect/);
  assert.match(app, /listOf\("For you", "News"\) \+ FeedContext\.entries/);
  assert.match(source('SocialFeedGame'), /Personal\("Personal"/);
  assert.match(app, /it\.context == context/);
});

test('published load merges all validated sessions before replacing the feed once', () => {
  assert.match(success, /bank\.fetchCatalog\(requireLlm = true\)/);
  assert.match(success, /val sessions = bank\.fetchPublishedSessions\(catalog\)/);
  assert.match(success, /val posts = mergePublishedFeed\(sessions, savedOrder = savedOrder\)/);
  assert.equal((success.match(/feedProgress = feedProgress\.copy\(posts = posts\)/g) ?? []).length, 1);
  assert.doesNotMatch(app, /\.shuffled\(|\.random\(/);
  assert.match(published, /sessions\.flatMap \{ it\.toFeedPosts\(\) \}/);
  assert.equal((published.match(/posts\.shuffled\(random\)/g) ?? []).length, 1);
});

test('display order survives recreation and changes only on explicit reload or revised content', () => {
  for (const name of ['bankRefresh', 'loadedBankRoot', 'loadedBankRefresh', 'publishedOrder']) {
    assert.match(app, new RegExp(`var ${name} by rememberSaveable`));
  }
  assert.match(success, /loadedBankRoot == bankRoot && loadedBankRefresh == bankRefresh/);
  assert.match(success, /publishedOrder = posts\.map \{ it\.thread\.id \}/);
  assert.match(app, /onRefresh = \{ bankRefresh\+\+ \}/);
  assert.match(published, /savedOrder\.toSet\(\) == byId\.keys/);
  assert.match(published, /savedOrder\.map \{ byId\.getValue\(it\) \}/);
});

test('refresh failures retain published content and report errors without a sample fallback', () => {
  assert.doesNotMatch(failure, /feedProgress\s*=|offlineSample\s*=|SocialFeedGame/);
  assert.match(failure, /Showing previously loaded published questions in their existing order/);
  assert.match(failure, /No sample was loaded/);
  assert.match(effect, /if \(feedProgress\.posts\.any \{ !it\.published \}\) feedProgress = feedProgress\.copy\(posts = emptyList\(\)\)/);
  assert.match(published, /catch \(cancelled: CancellationException\) \{\s*throw cancelled/);
  assert.match(published, /requests\.isEmpty\(\).*throw ApiException/);
  assert.match(published, /generationSource != "llm".*throw ApiException/);
});

test('selection is mixed-first with a total limit of twelve and no error-driven legacy fallback', () => {
  assert.match(published, /if \(catalog\.packs\.isNotEmpty\(\)\) catalog\.packs\.take\(12\)/);
  assert.match(published, /else catalog\.senses\.take\(12\)/);
  assert.match(bank, /if \(request\.legacyTerm == null\) fetchMixedSession\(request\.id, request\.sha256\)/);
  assert.match(bank, /else fetchSession\(request\.id, requireLlm = true,\s*expectedTerm = request\.legacyTerm, expectedSha256 = request\.sha256\)/);
  assert.match(bank, /suspend fun fetchSession\(/); // Connected Training remains legacy-compatible.
  assert.match(source('RemoteTrainingPanel'), /fetchSession\(selectedSenseId\)/);
  assert.match(published, /session\.packId\.ifBlank \{ session\.senseId \}/);
  assert.match(published, /session\.contentVersion/);
});

test('feed and progress counters use the loaded feed size, not a single ten-question pack', () => {
  assert.match(app, /feedProgress\.completedCount\} \/ \$\{feedProgress\.posts\.size\} conversations decoded/);
  assert.match(app, /EditorialMetric\("Conversations decoded", "\$\{feedProgress\.completedCount\} \/ \$\{feedProgress\.posts\.size\}"\)/);
  assert.doesNotMatch(app, /all ten|current pack/);
});

test('mixed validation uses the exact nine-category contract and keeps legacy context uniqueness separate', () => {
  const categories = bank.match(/private val mixedContextCategories = setOf\(([\s\S]*?)\n\)/)?.[1];
  assert.ok(categories);
  assert.deepEqual([...categories.matchAll(/"([a-z-]+)"/g)].map((match) => match[1]),
    ['personal', 'education', 'work', 'prediction', 'sports', 'entertainment', 'opinion', 'friends', 'event-reaction']);
  assert.match(bank, /requireBankSchema\(json, 1, 2\)/);
  assert.match(bank, /requireBankSchema\(pack, if \(mixed\) 2 else 1\)/);
  assert.match(bank, /question\.slang != reference\.term \|\| question\.targetMeaning != reference\.targetMeaning/);
  assert.match(bank, /question\.options\[idx\] != reference\.targetMeaning/);
  assert.match(bank, /\} else \{\s*if \(questions\.map \{ it\.contextId \}\.toSet\(\)\.size != 10\)/);
});

test('test-only mixed fixture contains cross-referenced keys, nine categories, and repeated context IDs', () => {
  const fixture = JSON.parse(readFileSync(new URL('../app/src/test/resources/mixed-pack.json', import.meta.url), 'utf8'));
  assert.equal(fixture.schemaVersion, 2);
  assert.equal(fixture.questions.length, 10);
  assert.equal(new Set(fixture.questions.map((q) => q.id)).size, 10);
  assert.equal(new Set(fixture.questions.map((q) => q.slang)).size, 2);
  assert.equal(new Set(fixture.questions.map((q) => q.contextCategory)).size, 9);
  assert.ok(new Set(fixture.questions.map((q) => q.contextId)).size < 10);
  const distribution = {};
  for (const q of fixture.questions) {
    distribution[q.difficulty] = (distribution[q.difficulty] ?? 0) + 1;
    const reference = fixture.references.find((ref) => ref.senseId === q.senseId);
    assert.ok(reference);
    assert.equal(q.slang, reference.term);
    assert.equal(q.targetMeaning, reference.targetMeaning);
    assert.equal(q.options[q.correctIndex], reference.targetMeaning);
  }
  assert.deepEqual(distribution, { easy: 5, medium: 3, hard: 2 });
  const key = (value) => String(value ?? '').toLowerCase().replace(/[^a-z0-9 ]/g, ' ').replace(/\s+/g, ' ').trim();
  for (const q of fixture.questions) {
    assert.ok(['post', 'reply'].includes(q.questionTarget), 'every mixed question must declare a target');
    const surfaces = [
      {target: 'post', handle: q.post.handle, text: q.post.content},
      ...q.post.comments.map((c) => ({target: 'reply', handle: c.handle, text: c.text}))
    ];
    const words = key(q.slang).split(' ').filter(Boolean);
    const count = (text) => {
      const hay = key(text).split(' ').filter(Boolean);
      let total = 0;
      for (let i = 0; i + words.length <= hay.length; i++)
        if (hay.slice(i, i + words.length).join(' ') === key(q.slang)) total++;
      return total;
    };
    const hits = surfaces.filter((s) => count(s.text) > 0);
    assert.equal(surfaces.reduce((n, s) => n + count(s.text), 0), 1, 'the slang must appear exactly once');
    assert.equal(hits.length, 1, 'the slang must appear in exactly one surface');
    assert.equal(hits[0].target, q.questionTarget, 'the declared target must name the slang surface');
    assert.equal(hits[0].handle, q.targetHandle, 'the declared handle must match the slang surface');
    if (q.questionTarget === 'post') {
      assert.ok(q.question.toLowerCase().includes('post'), 'the question must name the post it asks about');
    } else {
      assert.ok(q.question.toLowerCase().includes(q.targetHandle.replace(/^@/, '')),
        'the question must name the reply it asks about');
    }
  }
});

test('published feed binds the term target handle for reply highlighting', () => {
  const feed = readFileSync(new URL('../app/src/main/java/id/slangverse/app/PublishedFeed.kt', import.meta.url), 'utf8');
  assert.match(feed, /termTargetHandle = if \(q\.questionTarget == "reply"\) q\.targetHandle else null/);
  const ui = readFileSync(new URL('../app/src/main/java/id/slangverse/app/SocialSimulationUi.kt', import.meta.url), 'utf8');
  assert.match(ui, /highlightHandle: String\? = null/);
  assert.match(ui, /reply\.handle == highlightHandle/);
  assert.match(ui, /THE SLANG IS IN THIS REPLY/);
});
