import { rpc, session } from '../../../lib/backend';

const fields = ['participant_id','username','event_id','pack_id','pack_sha256','question_id','sense_id','context_category',
  'selected_index','is_correct','xp_awarded','mode','client_answered_at','received_at'];

function csvCell(value) {
  let text = String(value ?? '');
  if (/^[\s]*[=+@-]/.test(text)) text = "'" + text;
  return '"' + text.replaceAll('"', '""') + '"';
}

export async function GET(request) {
  const token = await session();
  if (!token) return new Response('Masuk sebagai admin.', { status: 401 });
  const includeQa = new URL(request.url).searchParams.get('qa') === '1';
  const lines = [fields.join(',')];
  const page = 500;
  try {
    for (let offset = 0; ; offset += page) {
      const rows = await rpc(token, 'export', { p_limit: page, p_offset: offset, p_include_qa: includeQa });
      if (offset >= 10_000 && rows.length) return new Response('Ekspor melebihi 10.000 baris. Gunakan SQL Editor.', { status: 413 });
      for (const row of rows) lines.push(fields.map(field => csvCell(row[field])).join(','));
      if (rows.length < page) break;
    }
  } catch (error) {
    return new Response('Ekspor tidak tersedia.', { status: error.status === 403 ? 403 : 502 });
  }
  return new Response('\ufeff' + lines.join('\r\n') + '\r\n', { headers: {
    'Content-Type': 'text/csv; charset=utf-8', 'Cache-Control': 'private, no-store',
    'Content-Disposition': 'attachment; filename="slangverse-answers.csv"'
  }});
}
