import { randomUUID } from 'node:crypto';
import { configured, canDispatch, GITHUB_RUN, rpc, session } from '../lib/backend';
import { login, logout, startJob } from './actions';

const operations = { generate: 'Generasi', check: 'Pengujian', build: 'Build APK' };
const statusNames = { queued: 'Antre', running: 'Berjalan', success: 'Lulus', failed: 'Gagal' };
const notices = {
  login: 'Akun admin belum dapat masuk. Periksa email, sandi, dan akses proyek.',
  session: 'Sesi admin berakhir. Masuk kembali.',
  gate: 'Jalankan pengujian untuk commit main dan versi bank ini sebelum membuat APK.',
  duplicate: 'Permintaan ini sudah dikirim. Lihat riwayat pekerjaan.',
  request: 'Permintaan belum bisa dijalankan. Periksa versi dan konfigurasi.',
  dispatch: 'GitHub Actions belum dapat menerima permintaan. Periksa konfigurasi proyek.'
};

const number = value => new Intl.NumberFormat('id-ID').format(value ?? 0);
const date = value => value && Number.isFinite(Date.parse(value)) ?
  new Intl.DateTimeFormat('id-ID', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Jakarta' }).format(new Date(value)) : '—';
const percent = (part, total) => total ? Math.round(part * 100 / total) + '%' : '—';
const uuid = value => typeof value === 'string' && /^[a-f0-9-]{36}$/.test(value);
const versionId = value => typeof value === 'string' && /^[a-zA-Z0-9-]{1,100}$/.test(value);

function Header({ signedIn }) {
  return <header className="header"><div className="wrap headline">
    <a className="brand" href="/"><span className="mark" aria-hidden="true">S</span>Slangverse</a>
    {signedIn && <form action={logout}><button className="quiet" type="submit">Keluar</button></form>}
  </div></header>;
}

function Login({ notice, setup, existing }) {
  return <><Header signedIn={existing} /><main className="wrap login">
    <section className="login-card">
      <p className="eyebrow">Slangverse · Penelitian</p>
      <h1>Masuk ke dashboard</h1>
      {notice && <p className="message" role="status">{notice}</p>}
      {setup && <p className="message" role="status">{setup}</p>}
      <form className="stack" action={login}>
        <label>Email admin<input name="email" type="email" autoComplete="username" required /></label>
        <label>Kata sandi<input name="password" type="password" autoComplete="current-password" required /></label>
        <button className="primary" disabled={Boolean(setup && !existing)} type="submit">Masuk</button>
      </form>
    </section>
  </main></>;
}

function Metric({ label, value, detail }) {
  return <article className="metric"><p>{label}</p><strong>{value}</strong><small>{detail}</small></article>;
}

function WorkForm({ list, mode, label, title, description, enabled, versionLabel }) {
  return <form action={startJob} className="card stack">
    <div><h3>{title}</h3><p className="muted">{description}</p></div>
    <input type="hidden" name="operation" value={mode} />
    <input type="hidden" name="request" value={randomUUID()} />
    <label>{versionLabel}<select name="version" disabled={!list.length} required>
      {list.length ? list.map(item => <option key={item.version} value={item.version}>{item.version}</option>) :
        <option value="">Versi belum tersedia</option>}
    </select></label>
    {mode === 'generate' && <label className="confirm"><input type="checkbox" name="confirm" value="yes" required />
      Saya setuju memakai satu panggilan LLM berbayar.</label>}
    <button className={mode === 'generate' ? 'primary' : 'secondary'} disabled={!enabled || !list.length}>{label}</button>
  </form>;
}

function Ledger({ rows, bank }) {
  return <div className="table-scroll"><table>
    <thead><tr><th>Versi</th><th>{bank ? 'Paket / soal' : 'Entri'}</th><th>Diimpor (WIB)</th></tr></thead>
    <tbody>{rows.map(row => <tr key={row.version}>
      <td><a href={'/?kind=' + (bank ? 'bank' : 'dataset') + '&version=' + row.version + '#versions'}>{row.version}</a></td>
      <td>{bank ? number(row.packs) + ' / ' + number(row.question_count) : number(row.entries)}</td>
      <td>{date(row.created_at)}</td>
    </tr>)}</tbody>
  </table>{!rows.length && <p className="empty">Belum ada versi yang diimpor.</p>}</div>;
}

function JobDetail({ job }) {
  return <article className="card detail" id="detail">
    <h3>{operations[job.operation]} <span className={'badge ' + job.status}>{statusNames[job.status]}</span></h3>
    <dl><div><dt>Commit</dt><dd>{job.commit_sha}</dd></div>
      <div><dt>Versi data</dt><dd>{job.dataset_version || job.bank_version}</dd></div>
      {job.run_id && <div><dt>GitHub Actions</dt><dd><a href={GITHUB_RUN + job.run_id}>Buka run{job.operation === 'build' ? ' dan artefak APK' : ''}</a></dd></div>}
    </dl>
    {job.summary?.errors?.length > 0 && <div><h4>Alasan validasi</h4><ul>{job.summary.errors.map((error, index) => <li key={index}>{error}</li>)}</ul></div>}
    {job.evidence && <div className="evidence">
      {Object.entries(job.evidence).map(([name, value]) => <details key={name}>
        <summary>{name}</summary><pre>{value}</pre>
      </details>)}
    </div>}
  </article>;
}

export default async function Page({ searchParams }) {
  const params = await searchParams;
  const notice = typeof params.notice === 'string' && Object.hasOwn(notices, params.notice) ? notices[params.notice] : null;
  const token = await session();
  if (!configured()) return <Login notice={notice} setup="Konfigurasi Supabase belum tersedia." />;
  if (!token) return <Login notice={notice} />;
  const wantedJob = uuid(params.job) ? rpc(token, 'job', { p_request_id: params.job }).catch(() => null) : null;
  const wantedContent = versionId(params.version) && ['dataset','bank'].includes(params.kind)
    ? rpc(token, 'content', { p_kind: params.kind, p_version: params.version }).catch(() => null) : null;
  let data;
  try {
    data = await rpc(token, 'overview');
  } catch (error) {
    return <Login existing notice={notice} setup={error.status === 401 || error.status === 403 ?
      'Sesi tidak memiliki akses admin.' : 'Data belum dapat dibuka. Periksa migrasi Supabase.'} />;
  }
  const [job, content] = await Promise.all([wantedJob, wantedContent]);
  const enabled = canDispatch();
  return <><Header signedIn />
    <main className="wrap dashboard">
      <div className="hero"><div><p className="eyebrow">Slangverse · Penelitian</p><h1>Dashboard admin</h1></div>
        <a className="quiet" href={job ? '/?job=' + job.request_id + '#detail' : '/'}>Perbarui data</a>
      </div>
      <nav aria-label="Bagian dashboard">
        <a href="#actions">Pekerjaan</a><a href="#runs">Riwayat</a><a href="#versions">Dataset & bank</a><a href="#answers">Jawaban</a>
      </nav>
      {notice && <p className="message" role="status">{notice}</p>}
      <section className="metrics" aria-label="Ringkasan penelitian">
        <Metric label="Generasi" value={number(data.generation.runs)} detail={number(data.generation.passedRuns) + ' lulus · ' + number(data.generation.failedRuns) + ' gagal'} />
        <Metric label="Soal dinilai" value={number(data.generation.questionsPassed + data.generation.questionsFailed)}
          detail={number(data.generation.questionsPassed) + ' lulus paket · ' + number(data.generation.questionsFailed) + ' ditolak bersama paket'} />
        <Metric label="Peserta" value={number(data.participants)} detail="Profil penelitian" />
        <Metric label="Akurasi jawaban" value={percent(data.answers.correct, data.answers.total)} detail={number(data.answers.total) + ' jawaban terkirim'} />
      </section>
      <section id="actions"><h2>Jalankan pekerjaan</h2>
        {!enabled && <p className="message">Sambungan GitHub Actions belum dikonfigurasi.</p>}
        <div className="actions">
          <WorkForm mode="generate" title="Generasi soal" description="Satu percobaan membuat paket draf dengan hasil validasi dan bukti prompt."
            label="Generate Soal" versionLabel="Versi dataset" list={data.datasets} enabled={enabled} />
          <WorkForm mode="check" title="Tes rilis" description="Periksa backend, Android, dan hash bank pada commit main."
            label="Jalankan tes" versionLabel="Versi bank" list={data.banks} enabled={enabled} />
          <WorkForm mode="build" title="APK peserta" description="Build memakai bank yang sama dengan hasil tes yang sudah lulus."
            label="Build APK" versionLabel="Versi bank" list={data.banks} enabled={enabled} />
        </div>
      </section>
      <section id="runs"><h2>Riwayat pekerjaan dan prompt</h2>
        <p className="muted">Soal dihitung jika keluaran dapat dinilai. Paket yang lulus tersedia sebagai draf di bukti run.</p>
        <div className="card table-scroll"><table>
          <thead><tr><th>Jenis</th><th>Status</th><th>Prompt / model</th><th>Waktu (WIB)</th><th>Detail</th></tr></thead>
          <tbody>{data.jobs.map(row => <tr key={row.request_id}><td>{operations[row.operation]}</td>
            <td><span className={'badge ' + row.status}>{statusNames[row.status]}</span></td>
            <td>{row.summary?.promptVersion || '—'}{row.summary?.model && <small className="sub">{row.summary.model}</small>}</td>
            <td>{date(row.requested_at)}</td><td><a href={'/?job=' + row.request_id + '#detail'}>Buka</a></td></tr>)}</tbody>
        </table>{!data.jobs.length && <p className="empty">Belum ada pekerjaan dashboard.</p>}</div>
        {job && <JobDetail job={job} />}
        <div className="card detail table-scroll"><h3>Perbandingan versi prompt</h3>
          <p className="muted">Cocokkan dataset dan model saat membandingkan hasil. <a href="https://github.com/ferrysrt/Slangverse-Android/blob/main/docs/PROMPT_ENGINEERING.md">Dokumentasi perubahan prompt</a></p>
          <table><thead><tr><th>Prompt</th><th>Dataset</th><th>Model</th><th>Run</th><th>Lulus / gagal</th></tr></thead>
            <tbody>{data.promptStats.map(row => <tr key={JSON.stringify([row.version,row.dataset_version,row.model])}>
              <td>{row.version}</td><td>{row.dataset_version}</td><td>{row.model || '—'}</td>
              <td>{row.runs}</td><td>{row.passed} / {row.failed}</td></tr>)}</tbody></table>
          {!data.promptStats.length && <p className="empty">Ringkasan tampil setelah generasi menyimpan bukti prompt.</p>}
        </div>
      </section>
      <section id="versions"><h2>Dataset dan bank soal</h2><div className="columns">
        <div className="card"><h3>Versi dataset</h3><Ledger rows={data.datasets} /></div>
        <div className="card"><h3>Versi bank</h3><Ledger rows={data.banks} bank /></div>
      </div>
      {content && <div className="card detail table-scroll"><h3>{content.version}</h3>
        {content.entries && <table><thead><tr><th>Slang</th><th>Definisi</th><th>Tanggal</th><th>Sumber</th></tr></thead>
          <tbody>{content.entries.map((entry,index) => <tr key={index}><td>{entry.slang}</td><td>{entry.definition}</td>
            <td>{entry.date || '—'}</td><td>{/^https?:\/\//.test(entry.source || '') ?
              <a rel="noreferrer" href={entry.source}>Buka</a> : '—'}</td></tr>)}</tbody></table>}
        {content.packs && <table><thead><tr><th>Paket</th><th>Soal</th><th>SHA-256</th></tr></thead>
          <tbody>{content.packs.map(pack => <tr key={pack.packId}><td>{pack.packId}</td><td>{pack.questionCount}</td>
            <td><code>{pack.sha256}</code></td></tr>)}</tbody></table>}
      </div>}
      </section>
      <section id="answers">
        <div className="section-head"><h2>Jawaban peserta</h2><div className="links">
          <a className="secondary" href="/api/export">Ekspor CSV</a>
          <a className="quiet" href="/api/export?qa=1">CSV termasuk QA</a>
        </div></div>
        <div className="columns">
          <article className="card table-scroll"><h3>Jawaban terbaru</h3><table>
            <thead><tr><th>Peserta</th><th>Konteks</th><th>Hasil</th><th>Waktu (WIB)</th></tr></thead>
            <tbody>{data.recentAnswers.map((row, i) => <tr key={i}><td>{row.username}<small className="sub">{row.participant_id.slice(0,8)}</small></td>
              <td>{row.context_category}</td><td>{row.is_correct ? 'Benar' : 'Salah'}</td><td>{date(row.received_at)}</td></tr>)}</tbody>
          </table>{!data.recentAnswers.length && <p className="empty">Belum ada jawaban peserta.</p>}</article>
          <article className="card table-scroll"><h3>Akurasi per soal</h3><table>
            <thead><tr><th>Soal / revisi</th><th>Konteks</th><th>Akurasi</th></tr></thead>
            <tbody>{data.questions.map(row => <tr key={row.pack_id + '/' + row.pack_sha256 + '/' + row.question_id}>
              <td>{row.question_id}<small className="sub">{row.pack_sha256.slice(0,10)}</small></td>
              <td>{row.context_category}</td><td>{percent(row.correct,row.total)}<small className="sub">{row.correct}/{row.total}</small></td>
            </tr>)}</tbody>
          </table>{!data.questions.length && <p className="empty">Akurasi tampil setelah jawaban masuk.</p>}</article>
        </div>
      </section>
    </main></>;
}
