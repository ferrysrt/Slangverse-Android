import {mkdirSync, writeFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash, randomUUID} from 'node:crypto';
import {withProfilePictures} from './profilePictures.mjs';
import {createMixedProvider, safeMixedFailureCode} from './mixedProvider.mjs';
import {MIXED_PROMPT_VERSION, selectMixedSenses, createMixedPrompt, evaluateMixedResponse,
  validatePublishedMixedPack} from './mixedQuestions.mjs';

export const DEFAULT_MIXED_EXPERIMENTS_DIR = fileURLToPath(new URL('../experiments/', import.meta.url));
export function createMixedPackId() {
  return `mixed-${new Date().toISOString().replace(/[^a-z0-9]/gi, '').toLowerCase()}-${randomUUID()}`;
}
const json = (dir, name, value) => writeFileSync(resolve(dir, name), JSON.stringify(value, null, 2) + '\n');

/** One attempt, at most one invocation, no retry/fallback/JSON repair. Audit files precede publication. */
export async function generateMixedPack({
  senses, datasetVersion, packId, experimentsDir = DEFAULT_MIXED_EXPERIMENTS_DIR,
  provider, providerOptions, randomIndex
} = {}) {
  const attemptId = createMixedPackId();
  packId ??= attemptId;
  const startedAt = new Date().toISOString();
  mkdirSync(experimentsDir, {recursive: true});
  const logDir = resolve(experimentsDir, attemptId);
  mkdirSync(logDir);
  const input = {attemptId, packId, startedAt, datasetVersion: datasetVersion ?? null,
    promptVersion: MIXED_PROMPT_VERSION, promptSha256: null, selection: null};
  json(logDir, 'input.json', input);
  json(logDir, 'result.json', {attemptId, packId, startedAt, status: 'started', providerCalls: 0});
  let calls = 0, references = [], metadata = {}, raw = null, pack = null, failureCode = null;
  let validation = evaluateMixedResponse(null, {references});
  try {
    if (typeof datasetVersion !== 'string' || !datasetVersion.trim()) throw Error('invalid-mixed-dataset-version');
    const selection = selectMixedSenses(senses, {packId, randomIndex});
    references = selection.references;
    input.selection = selection;
    const messages = Object.freeze(createMixedPrompt({packId, ...selection}).map(message => Object.freeze(message)));
    // Hash the exact UTF-8 JSON message array sent to the provider, not a template/version label.
    input.promptSha256 = createHash('sha256').update(JSON.stringify(messages), 'utf8').digest('hex');
    json(logDir, 'input.json', input);
    json(logDir, 'prompt.json', messages);
    const invoke = provider ?? createMixedProvider(providerOptions);
    if (typeof invoke !== 'function') throw Error('missing-provider-config');
    // The built-in provider exposes exactly its non-secret request body parameters.
    json(logDir, 'request.json', {promptVersion: input.promptVersion, promptSha256: input.promptSha256,
      parameters: invoke.parameters ?? null, timeoutMs: invoke.timeoutMs ?? null, messages});
    calls++;
    json(logDir, 'result.json', {attemptId, packId, startedAt, status: 'in-flight', providerCalls: calls});
    const response = await invoke({messages});
    metadata = response?.metadata ?? {};
    if (typeof response?.rawResponse === 'string') writeFileSync(resolve(logDir, 'response.txt'), response.rawResponse);
    raw = response?.content;
    let parsedOutput;
    if (typeof raw === 'string') {
      writeFileSync(resolve(logDir, 'output.txt'), raw);
      try { parsedOutput = JSON.parse(raw); } catch { /* Preserve malformed output without repairing it. */ }
      if (parsedOutput !== undefined) json(logDir, 'output.json', parsedOutput);
    }
    json(logDir, 'metadata.json', metadata);
    validation = evaluateMixedResponse(raw, {references, assignments: selection.assignments});
    const providerValid = !response?.errorCode && typeof metadata.actualModel === 'string' &&
      metadata.actualModel.trim().length > 0 && !['length', 'content_filter', 'error'].includes(metadata.finishReason);
    validation.checks.push({id: 'provider-completion', passed: providerValid,
      errors: providerValid ? [] : ['provider-completion']});
    validation.valid &&= providerValid;
    if (!providerValid) validation.errors.push('provider-completion');
    if (!validation.valid) failureCode = response?.errorCode ? safeMixedFailureCode({code: response.errorCode}) : 'mixed-validation-failed';
    else {
      pack = {
        schemaVersion: 2, packId, generationSource: 'llm', promptVersion: MIXED_PROMPT_VERSION,
        generatorModel: metadata.actualModel, generatedAt: new Date().toISOString(),
        referenceStatus: 'normalized-unreviewed', datasetVersion, expertValidated: false,
        references, questions: withProfilePictures(parsedOutput.questions)
      };
      validatePublishedMixedPack(pack, {expectedReferences: references});
      json(logDir, 'pack.json', pack);
    }
  } catch (error) {
    failureCode = safeMixedFailureCode(error);
    pack = null;
    metadata = {...metadata,
      ...(Number.isInteger(error?.metadata?.httpStatus) ? {httpStatus: error.metadata.httpStatus} : {}),
      errorCode: failureCode};
    json(logDir, 'metadata.json', metadata);
    validation = evaluateMixedResponse(raw, {references});
    validation.valid = false;
    validation.checks.push({id: 'attempt-completed', passed: false, errors: [failureCode]});
    validation.errors.push(failureCode);
  }
  const completedAt = new Date().toISOString();
  json(logDir, 'validation.json', validation);
  const result = {attemptId, packId, startedAt, completedAt, status: pack ? 'valid' : 'failed',
    providerCalls: calls, failureCode, actualModel: metadata.actualModel ?? null};
  json(logDir, 'result.json', result);
  return {ok: Boolean(pack), ...result, logDir, validation, pack};
}
