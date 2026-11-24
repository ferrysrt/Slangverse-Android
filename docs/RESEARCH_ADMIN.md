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

## Melihat dataset, bank, dan prompt

- **Dataset** membuka versi terbaru. Pilih versi lain dari daftar untuk melihat slang, definisi, tanggal publikasi jika tersedia, tautan sumber, dan kutipan. Setiap halaman memuat sampai 50 entri.
- **Bank soal** membuka bank peserta yang disimpan di Supabase. Pilih paket untuk melihat post, komentar, opsi, kunci, penjelasan, dan versi prompt yang tercatat di paket.
- **Prompt aktif** membuka instruksi sistem dan template dari commit `main` yang sama. Generator memakai versi di `backend/prompts/active.json`. Untuk prompt yang sudah diisi dengan input percobaan, buka `prompt.json` di riwayat run.

Pembaruan dashboard memerlukan migrasi `20261125100000_research_admin_viewers.sql` di proyek Supabase yang sama.

## Audit generasi dan penelitian

**Generate Soal** meminta satu percobaan LLM setelah konfirmasi. Jika workflow dijalankan ulang untuk permintaan yang sama, database mencegah pemanggilan kedua. Paket lulus tersimpan sebagai draf di bukti run. Hasil menyertakan prompt dan hash, keluaran, kunci validasi, dan model aktual bila dikembalikan provider. Hitungan soal mencerminkan paket yang diterima atau ditolak. Run tanpa keluaran yang dapat dinilai dilaporkan tersendiri.

**Tes rilis** dan **Build APK** dipasangkan berdasarkan SHA commit dan versi bank. Runner mengunduh bank dari Supabase, membandingkan SHA setiap paket, dan menjalankan ulang pengujian sebelum membuat APK dengan snapshot tersebut. Jika `main` berubah setelah reservasi, pekerjaan dibatalkan sebelum menjalankan generator atau membuat APK.

**Jawaban** menampilkan data dari Supabase, termasuk akurasi berdasarkan revisi soal. CSV dapat mengikutsertakan profil QA berawalan `qa_`. Pembatasan akses berlaku di database dan pada setiap permintaan admin.

## Sumber dataset dan perpindahan

**Actions > Refresh normalized slang dataset** menyimpan snapshot baru di Supabase setelah minimal sepuluh istilah berbeda lolos pemeriksaan kutipan. Jadwal Senin 10.00 WIB memakai satu pencarian Firecrawl. Parser membaca definisi eksplisit dari Markdown dan membuat CSV `slang,definition,date,source`. Bukti kutipan dan hash halaman masuk ke JSON. OpenRouter dipakai terpisah saat admin memilih **Generate Soal**.

Jalankan workflow manual dengan mode **replay** untuk memeriksa impor Supabase dari arsip tanpa memakai kredit provider. Waktu pengambilan tetap mengikuti arsip. Untuk pengujian lokal, isi `DATASET_EXPORT_DIR` dan jalankan `node backend/dataset/refresh-deterministic.mjs --replay path/to/sources.json`. Untuk pengambilan baru, pilih **live** dan gunakan `FIRECRAWL_API_KEY` di GitHub Actions.

Aplikasi peserta memakai bank Supabase yang dibekukan di APK. Aplikasi reguler yang sudah terpasang masih membaca URL GitHub, dan alat historis juga membaca snapshot lama. Perpindahan jalur itu memerlukan URL yang kompatibel dan uji pembaruan klien. Nomor versi dan revisi soal dipertahankan untuk pelacakan jawaban.


## Verifikasi 24 November 2026

- [Tes rilis](https://github.com/ferrysrt/Slangverse-Android/actions/runs/45392239192) dan [Build APK](https://github.com/ferrysrt/Slangverse-Android/actions/runs/45396245741) lulus untuk commit 96a9b786e dan bank 7e1e593cea34524f. Isi 9 paket, 90 soal, dan hash setiap paket diverifikasi. APK dipasang melalui ADB pada satu Xiaomi dengan tanda tangan yang sesuai. Aplikasi membuka layar, dan tab Quests serta Progress terdeteksi.
- [Percobaan Generate Soal](https://github.com/ferrysrt/Slangverse-Android/actions/runs/45396914552) memakai satu panggilan OpenRouter. Validator menolak paket berisi 10 soal karena rujukan dan makna kunci pada soal kelima, satu rujukan yang tidak digunakan, serta penulis target balasan pada soal pertama dan ketiga. Bukti tersimpan di Supabase dan dapat dibuka di dashboard. Bank peserta tetap memakai versi yang lulus tes.
