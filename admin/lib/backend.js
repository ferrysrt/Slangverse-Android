import 'server-only';
import { cookies } from 'next/headers';

export const SESSION = 'slangverse-admin';
export const REPOSITORY = 'ferrysrt/Slangverse-Android';
export const GITHUB_RUN = 'https://github.com/' + REPOSITORY + '/actions/runs/';

export function configured() {
  return /^https:\/\/[a-z0-9]+\.supabase\.co$/.test(process.env.SLANGVERSE_SUPABASE_URL ?? '') &&
    /^sb_publishable_[A-Za-z0-9_-]+$/.test(process.env.SLANGVERSE_SUPABASE_PUBLISHABLE_KEY ?? '');
}

export function canDispatch() {
  return Boolean(process.env.RESEARCH_ADMIN_GITHUB_TOKEN);
}

export async function session() {
  return (await cookies()).get(SESSION)?.value;
}

export class ServiceError extends Error {
  constructor(status, code) {
    super('Service request failed');
    this.status = status;
    this.code = code;
  }
}

async function supabase(path, data, token) {
  if (!configured()) throw new ServiceError(503, 'setup_required');
  const headers = { apikey: process.env.SLANGVERSE_SUPABASE_PUBLISHABLE_KEY, 'Content-Type': 'application/json' };
  if (token) headers.Authorization = 'Bearer ' + token;
  return responseData(await fetch(process.env.SLANGVERSE_SUPABASE_URL + path, {
    method: 'POST', body: JSON.stringify(data), headers, cache: 'no-store', redirect: 'error',
    signal: AbortSignal.timeout(12_000)
  }));
}

async function responseData(response) {
  const data = response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok) throw new ServiceError(response.status, data?.code);
  return data;
}

export function signIn(email, password) {
  return supabase('/auth/v1/token?grant_type=password', { email, password });
}

export function rpc(token, name, data = {}) {
  if (!token) throw new ServiceError(401, 'no_session');
  return supabase('/rest/v1/rpc/research_admin_' + name, data, token);
}

async function github(path, data) {
  if (!canDispatch()) throw new ServiceError(503, 'workflow_setup_required');
  return responseData(await fetch('https://api.github.com/repos/' + REPOSITORY + path, {
    method: data ? 'POST' : 'GET', body: data ? JSON.stringify(data) : undefined, headers: {
      Authorization: 'Bearer ' + process.env.RESEARCH_ADMIN_GITHUB_TOKEN,
      Accept: 'application/vnd.github+json',
      'Content-Type': 'application/json',
      'X-GitHub-Api-Version': '2026-03-10'
    }, cache: 'no-store', redirect: 'error', signal: AbortSignal.timeout(12_000)
  }));
}

export async function readMainCommit() {
  const ref = await github('/git/ref/heads/main');
  if (!/^[a-f0-9]{40}$/.test(ref?.object?.sha ?? '')) throw new ServiceError(502, 'missing_main_commit');
  return ref.object.sha;
}

export async function readActivePrompt() {
  const commit = await readMainCommit();
  const read = async name => {
    const file = await github('/contents/backend/prompts/' + name + '?ref=' + commit);
    if (file?.encoding !== 'base64' || typeof file.content !== 'string')
      throw new ServiceError(502, 'missing_prompt_file');
    return Buffer.from(file.content, 'base64').toString('utf8').replace(/\r\n/g, '\n');
  };
  const { version, system } = JSON.parse(await read('active.json'));
  if (!/^mixed-v[1-9][0-9]*$/.test(version ?? '') || typeof system !== 'string' || !system.trim())
    throw new ServiceError(502, 'invalid_active_prompt');
  return { version, system, commit, template: await read(version + '.txt') };
}

export function dispatchDiscovery(options) {
  return github('/actions/workflows/slang-candidates.yml/dispatches', {
    ref: 'main', inputs: { mode: 'live', query: options.query, date_from: options.from,
      date_to: options.to, min_source_urls: String(options.minimum), domains: options.hosts.join(',') }
  });
}

export async function readDiscoveryRun(id) {
  const run = await github('/actions/runs/' + id);
  return run?.path?.split('@')[0] === '.github/workflows/slang-candidates.yml' ? run : null;
}

export function dispatch(id) {
  return github('/actions/workflows/research-admin.yml/dispatches', {
    ref: 'main', inputs: { request_id: id }
  });
}
