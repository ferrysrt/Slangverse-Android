export const dynamic = 'force-dynamic';

const headers = { 'Cache-Control': 'no-store' };

export async function GET() {
  const raw = process.env.SLANGVERSE_PARTICIPANT_UPDATE;
  if (!raw) return Response.json({ available: false }, { headers });
  try {
    const { versionCode, versionName, downloadPage } = JSON.parse(raw);
    const url = new URL(downloadPage);
    if (!Number.isSafeInteger(versionCode) || versionCode <= 0 || versionCode > 2100000000 ||
      typeof versionName !== 'string' || !/^[0-9][A-Za-z0-9.+-]{0,49}$/.test(versionName) ||
      url.protocol !== 'https:' || url.username || url.password)
      throw Error('invalid-app-update');
    return Response.json({ available: true, packageName: 'id.slangverse.app.staging', versionCode,
      versionName, downloadPage: url.href }, { headers });
  } catch {
    return Response.json({ available: false }, { status: 503, headers });
  }
}
