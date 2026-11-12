import {createHash, timingSafeEqual} from 'node:crypto';

/**
 * A private client passphrase for the game backend, never the OpenRouter API key.
 * The passphrase is typed into Android at runtime, not compiled into the APK.
 */
export function checkClientToken(header, configuredToken) {
  if (!configuredToken) return true; // local-only development compatibility
  if (typeof header !== 'string' || !header.startsWith('Bearer ')) return false;
  const candidate = header.slice(7);
  // Hash before comparing so different-length inputs take the same comparison path.
  const expectedHash = createHash('sha256').update(configuredToken).digest();
  const candidateHash = createHash('sha256').update(candidate).digest();
  return timingSafeEqual(expectedHash, candidateHash);
}

export function accessConfig(env = process.env) {
  const token = env.SLANGVERSE_CLIENT_TOKEN || '';
  const requireToken = env.SLANGVERSE_REQUIRE_TOKEN === 'true';
  if ((requireToken || token) && token.length < 24) {
    throw new Error('SLANGVERSE_CLIENT_TOKEN must have at least 24 characters when enabled');
  }
  const maxGenerations = Number(env.SLANGVERSE_MAX_LLM_SESSIONS || 20);
  if (!Number.isSafeInteger(maxGenerations) || maxGenerations < 1 || maxGenerations > 1000) {
    throw new Error('SLANGVERSE_MAX_LLM_SESSIONS must be an integer between 1 and 1000');
  }
  return {token, requireToken, maxGenerations};
}
