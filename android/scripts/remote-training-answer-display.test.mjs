import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';
import assert from 'node:assert/strict';

const panel = readFileSync(fileURLToPath(new URL('../app/src/main/java/id/slangverse/app/RemoteTrainingPanel.kt', import.meta.url)), 'utf8');

test('connected training blocks ambiguous choices and uses display labels in choices and feedback', () => {
  assert.match(panel, /val displayedOptions = displayAnswerOptions\(current\.options\)/);
  assert.match(panel, /if \(displayedOptions == null\)\s*\{[\s\S]*?ambiguous answer choices[\s\S]*?\}\s*else if \(!questionOpened/);
  assert.match(panel, /displayedOptions\.forEachIndexed \{ index, option ->/);
  assert.match(panel, /meaning = displayedOptions\.getOrElse\(result!!\.correctIndex\)/);
  assert.doesNotMatch(panel, /current\.options\.(?:forEachIndexed|getOrElse)/);
});

test('both sources validate the whole session before assigning state', () => {
  assert.match(panel, /val candidate = validateTrainingSession\(downloaded\.session\)[\s\S]*?staticAnswers = downloaded\.answers[\s\S]*?session = candidate/);
  assert.match(panel, /val candidate = validateTrainingSession\(SlangverseApi\(serverUrl, clientToken\)\.createSession\(selectedSenseId\)\)[\s\S]*?staticAnswers = emptyMap\(\)[\s\S]*?session = candidate/);
});

test('both scoring paths submit the selected original index', () => {
  assert.match(panel, /selectedIndex = index/);
  assert.match(panel, /gradeStaticAnswer\(staticAnswers, current\.id, selectedIndex, currentIndex, totalXp\)/);
  assert.match(panel, /submitAnswer\(activeSession\.id, current\.id, selectedIndex\)/);
});
