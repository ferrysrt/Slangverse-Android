import {randomUUID} from 'node:crypto';
import {mkdir, writeFile, rename} from 'node:fs/promises';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';

export const AUDIT_LIMITS = Object.freeze({
  documents: 10, markdownCharsPerDocument: 400000, totalMarkdownChars: 2000000,
  firecrawlResponseBytes: 24 * 1024 * 1024, normalizationResponseBytes: 2 * 1024 * 1024
});
const directoryPath = value => value instanceof URL ? fileURLToPath(value) : String(value);
const knownErrors = new Set([
  'missing-firecrawl-key', 'missing-normalizer-config', 'invalid-existing-candidates',
  'invalid-firecrawl-v2-response', 'no-scraped-documents', 'insufficient-scraped-documents',
  'normalizer-empty-output', 'normalizer-call-budget-exceeded',
  'normalizer-output-truncated', 'normalizer-incomplete-output',
  'normalizer-json-invalid', 'normalizer-shape-invalid', 'no-evidence-backed-candidates',
  'dataset-audit-write-failed', 'dataset-output-write-failed', 'dataset-refresh-failed'
]);

// Never persist or rethrow arbitrary exception messages, response headers or error bodies.
export function safeFailureCode(error) {
  const code = error?.message;
  return knownErrors.has(code) || /^(firecrawl|openrouter)-(http-[1-5]\d\d|network-error|timeout|response-json-invalid|response-read-error|response-too-large)$/.test(code)
    ? code : 'dataset-refresh-failed';
}

export async function auditText(auditDir, filename, text) {
  if (!auditDir) return;
  try {
    await mkdir(directoryPath(auditDir), {recursive: true});
    await writeFile(join(directoryPath(auditDir), filename), text, 'utf8');
  } catch { throw Error('dataset-audit-write-failed'); }
}

export function auditJson(auditDir, filename, value) {
  return auditText(auditDir, filename, JSON.stringify(value, null, 2) + '\n');
}

export async function createDatasetRun(auditRoot, input) {
  const startedAt = new Date().toISOString();
  const runId = startedAt.replace(/[:.]/g, '-') + '-' + randomUUID();
  const auditDir = join(directoryPath(auditRoot), runId);
  try {
    await mkdir(directoryPath(auditRoot), {recursive: true});
    await mkdir(auditDir);
  } catch { throw Error('dataset-audit-write-failed'); }
  await auditJson(auditDir, 'input.json', {schemaVersion: 1, runId, startedAt, ...input});
  return {auditDir, runId, startedAt};
}

export async function auditDocuments(auditDir, docs) {
  let remaining = AUDIT_LIMITS.totalMarkdownChars;
  const documents = docs.slice(0, AUDIT_LIMITS.documents).map(doc => {
    const original = String(doc.markdown ?? '');
    const markdown = original.slice(0, Math.min(AUDIT_LIMITS.markdownCharsPerDocument, remaining));
    remaining -= markdown.length;
    return {...doc, markdown, originalMarkdownChars: original.length,
      markdownTruncated: markdown.length !== original.length};
  });
  await auditJson(auditDir, 'sources.json', {limits: AUDIT_LIMITS, documents});
}

async function readBoundedText(response, maxBytes, stage) {
  if (!response.body?.getReader) {
    const text = await response.text();
    if (Buffer.byteLength(text, 'utf8') > maxBytes) throw Error(stage + '-response-too-large');
    return text;
  }
  const reader = response.body.getReader(), chunks = [];
  let bytes = 0;
  try {
    while (true) {
      const {done, value} = await reader.read();
      if (done) break;
      bytes += value.byteLength;
      if (bytes > maxBytes) {
        await reader.cancel();
        throw Error(stage + '-response-too-large');
      }
      chunks.push(Buffer.from(value));
    }
  } finally { reader.releaseLock(); }
  return Buffer.concat(chunks).toString('utf8');
}

export async function auditedPost({fetchImpl, url, key, payload, stage, timeoutMs = 50000, auditDir, label}) {
  const prefix = (stage === 'openrouter' ? 'normalization' : 'firecrawl') + (label ? '-' + label : '');
  const started = Date.now();
  const metadata = {startedAt: new Date(started).toISOString(), finishedAt: null,
    status: 'running', httpStatus: null, errorCode: null};
  await auditJson(auditDir, prefix + '-request.json', {url, payload});
  await auditJson(auditDir, prefix + '-http.json', metadata);
  console.log('[dataset] ' + stage + ' request started');
  try {
    let response;
    try {
      response = await fetchImpl(url, {
        method: 'POST', signal: AbortSignal.timeout(timeoutMs),
        headers: {Authorization: 'Bearer ' + key, 'Content-Type': 'application/json'},
        body: JSON.stringify(payload)
      });
    } catch (error) {
      throw Error(stage + '-' + (['TimeoutError', 'AbortError'].includes(error?.name) ? 'timeout' : 'network-error'));
    }
    metadata.httpStatus = response.status;
    console.log('[dataset] ' + stage + ' HTTP ' + response.status + ' in ' + (Date.now() - started) + 'ms');
    // In particular, never read or persist a non-2xx response body.
    if (!response.ok) throw Error(stage + '-http-' + response.status);
    let text;
    try {
      if (typeof response.text === 'function') {
        text = await readBoundedText(response, stage === 'firecrawl'
          ? AUDIT_LIMITS.firecrawlResponseBytes : AUDIT_LIMITS.normalizationResponseBytes, stage);
      } else if (typeof response.json === 'function') {
        // Test/injected transports may only expose parsed JSON; keep the exact
        // serialized form as the raw evidence instead of losing the response.
        text = JSON.stringify(await response.json());
      } else {
        throw Error(stage + '-response-read-error');
      }
    } catch (error) {
      if (error?.message === stage + '-response-too-large' || error?.message === stage + '-response-read-error') throw error;
      throw Error(stage + '-' + (['TimeoutError', 'AbortError'].includes(error?.name) ? 'timeout' : 'response-read-error'));
    }
    // Only successful normalization responses are archived raw. Firecrawl source
    // captures are archived separately, bounded to ten documents with explicit truncation.
    if (stage === 'openrouter') await auditText(auditDir, prefix + '-response.txt', text);
    let parsed;
    try { parsed = JSON.parse(text); }
    catch { throw Error(stage + '-response-json-invalid'); }
    if (auditDir && stage === 'openrouter') {
      try {
        await rename(join(directoryPath(auditDir), prefix + '-response.txt'),
          join(directoryPath(auditDir), prefix + '-response.json'));
      } catch { throw Error('dataset-audit-write-failed'); }
    }
    metadata.status = 'received';
    return parsed;
  } catch (error) {
    metadata.status = 'failed';
    metadata.errorCode = safeFailureCode(error);
    throw Error(metadata.errorCode);
  } finally {
    metadata.finishedAt = new Date().toISOString();
    await auditJson(auditDir, prefix + '-http.json', metadata);
  }
}
