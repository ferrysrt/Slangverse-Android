# Dashboard admin Slangverse

Dashboard privat memantau jawaban, membandingkan run generasi berdasarkan prompt, model, dan dataset, serta meminta tes dan build APK peserta. Supabase menyimpan data dan bukti run. GitHub Actions menjalankan pekerjaan. Vercel menyajikan antarmuka.

## Penyiapan satu kali

1. Di Supabase yang sudah melayani aplikasi peserta, pasang migrasi `20261124180000_research_admin.sql` dan seed dari `node backend/scripts/research-admin.mjs seed-sql`. Alternatifnya jalankan bundel SQL admin yang memuat keduanya. Seed mengimpor snapshot dataset dan bank yang sudah ada, termasuk 90 soal. Riwayat jawaban tetap memakai tabel peserta yang sudah aktif.
2. Buat akun email khusus admin di **Authentication → Users**. Ambil UUID akun yang sudah terkonfirmasi dan tambahkan ke daftar admin dengan SQL berikut. Gunakan UUID akun admin sendiri.

```sql
insert into slangverse_private.research_admins (user_id)
values ('00000000-0000-0000-0000-000000000000');
```

3. Impor repositori ke proyek pribadi Vercel dan pilih **Root Directory** `admin`. Tambahkan variabel lingkungan `SLANGVERSE_SUPABASE_URL`, `SLANGVERSE_SUPABASE_PUBLISHABLE_KEY`, dan `RESEARCH_ADMIN_GITHUB_TOKEN`. Token GitHub hanya untuk repositori ini dengan **Actions** baca/tulis dan **Contents** baca. Simpan token sebagai rahasia Vercel.
4. Di **GitHub → Settings → Secrets and variables → Actions**, set variabel `SLANGVERSE_SUPABASE_URL` dan `SLANGVERSE_SUPABASE_PUBLISHABLE_KEY`. Set secret `RESEARCH_WORKER_SUPABASE_KEY` dengan kunci server Supabase yang dapat menjalankan RPC worker. Secret `OPENROUTER_API_KEY` dipakai oleh workflow generasi yang sama seperti sebelumnya. Gunakan nilai rahasia langsung di dashboard pemilik proyek.
5. Akses URL Vercel, masuk sebagai admin, lalu cek daftar dataset dan bank. Jalankan tes bank sebelum memilih **Build APK**. Pilih build dari run GitHub Actions dan distribusikan satu APK yang sama ke peserta dalam satu gelombang. CI menggunakan tanda tangan debug runner. Untuk mengganti versi pada instalasi yang sudah ada, gunakan kunci penandatanganan yang sama.

## Audit generasi dan penelitian

**Generate Soal** meminta satu percobaan LLM setelah konfirmasi. Jika workflow dijalankan ulang untuk permintaan yang sama, database mencegah pemanggilan kedua. Paket lulus tersimpan sebagai draf di bukti run. Hasil menyertakan prompt dan hash, keluaran, kunci validasi, dan model aktual bila dikembalikan provider. Hitungan soal mencerminkan paket yang diterima atau ditolak. Run tanpa keluaran yang dapat dinilai dilaporkan tersendiri.

**Tes rilis** dan **Build APK** dipasangkan berdasarkan SHA commit dan versi bank. Runner mengunduh bank dari Supabase, membandingkan SHA setiap paket, dan menjalankan ulang pengujian sebelum membuat APK dengan snapshot tersebut. Jika `main` berubah setelah reservasi, pekerjaan dibatalkan sebelum menjalankan generator atau membuat APK.

**Jawaban** menampilkan data dari Supabase, termasuk akurasi berdasarkan revisi soal. CSV dapat mengikutsertakan profil QA berawalan `qa_`. Pembatasan akses berlaku di database dan pada setiap permintaan admin.

## Sumber dataset dan perpindahan

Untuk menguji ekstraksi langsung, jalankan `node backend/dataset/refresh-deterministic.mjs` dengan `FIRECRAWL_API_KEY` dan `DATASET_EXPORT_DIR`. Operasi ini memakai kredit Firecrawl. Parser menerima entri glosarium seperti `**Cooked**: In a difficult situation with few options.` CSV berisi `slang,definition,date,source`. Bukti kutipan dan hash halaman masuk ke JSON. Tambahkan `--import` dan konfigurasi worker Supabase untuk menyimpan versi baru.

Snapshot awal diwarisi dari pipeline historis. Sebelum memindahkan sumber utama, verifikasi cakupan sumber parser baru, impor data, uji build dari Supabase dan cek aplikasi lama yang masih membaca URL GitHub. Setelah itu migrasikan workflow terjadwal dan hapus salinan data aktif dari branch. Nomor versi dan revisi soal perlu dipertahankan untuk pelacakan jawaban.
