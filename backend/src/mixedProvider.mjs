const safeCodes = new Set([
  'missing-provider-config', 'unsafe-provider-url', 'bad-provider-timeout', 'bad-provider-max-tokens',
  'llm-timeout', 'llm-network', 'llm-bad-provider-json', 'llm-missing-content', 'llm-missing-model',
  'invalid-mixed-pack-id', 'invalid-normalized-dataset', 'invalid-normalized-sense',
  'invalid-normalized-selection', 'mixed-needs-two-distinct-terms', 'invalid-selection-random-index',
  'invalid-mixed-prompt-input', 'invalid-mixed-dataset-version', 'invalid-existing-bank-catalog',
  'invalid-mixed-catalog-metadata', 'invalid-mixed-retention', 'normalized-catalog-interface-unavailable',
  'max-packs-must-be-1-to-3', 'mixed-validation-failed'
]);

export function safeMixedFailureCode(error) {
  const code = error?.code || error?.message;
  return typeof code === 'string' && (safeCodes.has(code) || /^llm-http-[1-5][0-9]{2}$/.test(code))
    ? code : 'mixed-attempt-failed';
}

function providerError(code, metadata = {}) {
  return Object.assign(Error(code), {code, metadata});
}

/** Return raw content and real provider metadata; never parse or repair generated JSON here. */
export function createMixedProvider({
  apiKey = process.env.LLM_API_KEY,
  model = process.env.LLM_MODEL || 'openai/gpt-6-luna',
  baseUrl = process.env.LLM_API_BASE_URL || 'https://openrouter.ai/api/v1',
  timeoutMs = Number(process.env.LLM_TIMEOUT_MS || 120000),
  maxTokens = Number(process.env.LLM_MAX_TOKENS || 10000),
  fetchImpl = globalThis.fetch
} = {}) {
  if (typeof apiKey !== 'string' || !apiKey.trim() || typeof model !== 'string' || !/^[a-z0-9._:/-]{1,200}$/i.test(model))
    throw providerError('missing-provider-config');
  let url;
  try { url = new URL(baseUrl); } catch { throw providerError('unsafe-provider-url'); }
  if ((url.protocol !== 'https:' && !(url.protocol === 'http:' && ['127.0.0.1', 'localhost'].includes(url.hostname))) ||
      url.username || url.password || url.search || url.hash) throw providerError('unsafe-provider-url');
  if (!Number.isInteger(timeoutMs) || timeoutMs < 1000 || timeoutMs > 120000) throw providerError('bad-provider-timeout');
  if (!Number.isInteger(maxTokens) || maxTokens < 512 || maxTokens > 12000) throw providerError('bad-provider-max-tokens');
  const endpoint = url.toString().replace(/\/$/, '') + '/chat/completions';
  const parameters = Object.freeze({model, temperature: 0.5, max_tokens: maxTokens,
    response_format: Object.freeze({type: 'json_object'}), reasoning: Object.freeze({effort: 'low'})});
  const provider = async ({messages}) => {
    const startedAt = new Date().toISOString();
    let response, rawResponse;
    try {
      response = await fetchImpl(endpoint, {
        method: 'POST', signal: AbortSignal.timeout(timeoutMs), redirect: 'error',
        headers: {'Content-Type': 'application/json', Authorization: `Bearer ${apiKey}`},
        body: JSON.stringify({...parameters, messages})
      });
      // Do not capture error bodies, headers, exceptions, URLs or request credentials.
      if (!response.ok) throw providerError(`llm-http-${response.status}`, {httpStatus: response.status});
      rawResponse = await response.text();
    } catch (error) {
      if (/^llm-http-[1-5][0-9]{2}$/.test(error?.code || '')) throw error;
      throw providerError(['TimeoutError', 'AbortError'].includes(error?.name) ? 'llm-timeout' : 'llm-network');
    }
    const metadata = {requestedModel: model, startedAt, completedAt: new Date().toISOString(), httpStatus: response.status};
    let envelope;
    try { envelope = JSON.parse(rawResponse); }
    catch { return {rawResponse, content: null, metadata, errorCode: 'llm-bad-provider-json'}; }
    const choice = envelope?.choices?.[0];
    Object.assign(metadata, {
      actualModel: typeof envelope?.model === 'string' ? envelope.model : null,
      responseId: typeof envelope?.id === 'string' ? envelope.id : null,
      provider: typeof envelope?.provider === 'string' ? envelope.provider : null,
      providerCreated: typeof envelope?.created === 'number' ? envelope.created : null,
      usage: envelope?.usage ?? null,
      finishReason: choice?.finish_reason ?? null,
      nativeFinishReason: choice?.native_finish_reason ?? null
    });
    const content = typeof choice?.message?.content === 'string' ? choice.message.content : null;
    const errorCode = content === null ? 'llm-missing-content' :
      !metadata.actualModel?.trim() ? 'llm-missing-model' : null;
    return {rawResponse, content, metadata, errorCode};
  };
  provider.parameters = parameters;
  provider.timeoutMs = timeoutMs;
  return provider;
}
