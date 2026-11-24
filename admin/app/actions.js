'use server';

import { cookies } from 'next/headers';
import { redirect } from 'next/navigation';
import { canDispatch, dispatch, dispatchDiscovery, readMainCommit, rpc, session, SESSION, signIn } from '../lib/backend';
import { readDiscoveryOptions } from '../lib/discovery-options.mjs';

export async function login(data) {
  const email = String(data.get('email') || '').trim();
  const password = String(data.get('password') || '');
  if (!email || !password || email.length > 320 || password.length > 1024) redirect('/?notice=login');
  try {
    const auth = await signIn(email, password);
    await rpc(auth.access_token, 'overview');
    (await cookies()).set(SESSION, auth.access_token, {
      httpOnly: true, secure: process.env.NODE_ENV === 'production',
      sameSite: 'lax', path: '/', maxAge: Math.min(auth.expires_in || 3600, 3600)
    });
  } catch {
    redirect('/?notice=login');
  }
  redirect('/');
}

export async function logout() {
  (await cookies()).delete(SESSION);
  redirect('/');
}

export async function startJob(data) {
  const token = await session();
  if (!token) redirect('/?notice=session');
  const operation = String(data.get('operation') ?? '');
  const version = String(data.get('version') ?? '');
  const requestId = String(data.get('request') ?? '');
  if (!canDispatch() || !['generate','check','build'].includes(operation) || !/^[a-zA-Z0-9-]{1,100}$/.test(version)
    || !/^[a-f0-9-]{36}$/.test(requestId) || (operation === 'generate' && data.get('confirm') !== 'yes')) {
    redirect('/?notice=request');
  }
  try {
    // Verify the allowlist before calling GitHub and re-check it in the reservation RPC.
    await rpc(token, 'overview');
    await rpc(token, 'reserve', {
      p_request_id: requestId, p_operation: operation, p_commit_sha: await readMainCommit(),
      p_dataset_version: operation === 'generate' ? version : null,
      p_bank_version: operation === 'generate' ? null : version
    });
  } catch (error) {
    redirect('/?notice=' + (error.code === '23505' ? 'duplicate' : operation === 'build' && error.code === '22023' ? 'gate' : 'request'));
  }
  try {
    await dispatch(requestId);
  } catch {
    await rpc(token, 'dispatch_failed', { p_request_id: requestId }).catch(() => null);
    redirect('/?notice=dispatch');
  }
  redirect('/?job=' + requestId);
}

export async function resetAnswers(data) {
  const token = await session();
  if (!token) redirect('/?notice=session');
  const id = data.get('request');
  const expected = data.get('period');
  const uuid = /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/;
  if (data.get('confirm') !== 'RESET' || !uuid.test(id ?? '') || (expected !== '' && !uuid.test(expected ?? ''))) {
    redirect('/?notice=answerResetConfirm#answers');
  }
  let reset;
  try {
    reset = await rpc(token, 'reset_answers', { p_request_id: id, p_expected_period: expected || null });
  } catch {
    redirect('/?notice=answerResetFailed#answers');
  }
  redirect('/?notice=' + (reset === true ? 'answerResetSuccess' : 'answerResetStale') + '#answers');
}

export async function startDiscovery(data) {
  const token = await session();
  if (!token) redirect('/?notice=session');
  if (!canDispatch() || data.get('confirm') !== 'yes') redirect('/?notice=discoveryInput#firecrawl');
  let options;
  try {
    const input = name => String(data.get(name) ?? '');
    options = readDiscoveryOptions({
      query: input('query'), from: input('date_from'), to: input('date_to'),
      minUrls: input('min_source_urls'), domains: input('domains')
    });
  } catch {
    redirect('/?notice=discoveryInput#firecrawl');
  }
  let run;
  try {
    await rpc(token, 'overview');
    run = await dispatchDiscovery(options);
  } catch {
    redirect('/?notice=discoveryDispatch#firecrawl');
  }
  const id = run?.workflow_run_id;
  redirect(Number.isSafeInteger(id) && id > 0 ? '/?crawl=' + id + '#firecrawl' : '/?notice=discoverySent#firecrawl');
}
