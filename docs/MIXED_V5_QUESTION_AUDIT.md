# Audit editorial 10 soal mixed-v5

## Ruang lingkup dan status

Audit ini meninjau file lokal `question-bank/packs/mixed-20261117t194601433z-8ca3b36c-8d92-4c8f-90d8-e61c571d6597.json`, pack `mixed-20261117t194601433z-8ca3b36c-8d92-4c8f-90d8-e61c571d6597`. ID lengkap setiap soal adalah ID pack tersebut diikuti `-q1` sampai `-q10`; nomor di bawah merujuk tepat pada sufiks itu. Opsi A–D adalah urutan pada JSON; indeks JSON mulai dari 0. Sasaran post/reply dan handle diambil dari data, bukan hasil pengujian UI.

Ini **penilaian editorial sementara**, bukan validasi oleh ahli bahasa, dokter, atau peserta; tidak ada pengujian pemahaman, kalibrasi kesulitan, pengujian perangkat, maupun pembuktian prevalensi slang. Referensi pack berstatus `normalized-unreviewed`, `expertValidated: false`; kesamaan dengan referensi bukan bukti kebenaran semantik. Kesesuaian dan kealamian di bawah adalah interpretasi, bukan ukuran objektif. Label easy/medium/hard adalah label desain, belum terkalibrasi.

### Temuan lintas-soal

- Pemeriksaan langsung JSON: 10/10 jawaban kunci identik dengan `targetMeaning`; 9/10 string kunci berakhir dengan karakter `.` dan 0/30 opsi salah demikian. Pengecualian q1 berakhir dengan tanda kutip penutup setelah titik. Ini **sinyal pada data mentah**, bukan bukti tampilan atau perilaku pemain; format tampilan perlu diperiksa terpisah. Panjang opsi juga tidak seragam, tetapi bukan aturan universal: jawaban q3 dan q5 jauh lebih panjang, sementara q8 dan q9 lebih pendek daripada semua pengecohnya.
- q3 menyalin frasa definisi ke post; q1 dan q10 juga memberi petunjuk makna melalui komentar pertanyaan. Banyak pengecoh bertema situasi tetapi tidak menjawab jenis pertanyaan yang sama. Petunjuk kontekstual wajar, tetapi menyalin definisi atau membuat satu opsi jauh lebih relevan dapat mengurangi tantangan.
- Prioritas editorial: **q7** untuk memeriksa sense dan konteks sumber; **q3** untuk kebocoran definisi dan kehati-hatian medis; lalu q1, q5, q10 dan pengecoh lain. Ini prioritas penilaian, bukan keputusan mencabut soal. Saran di bawah adalah rancangan manusia untuk versi masa depan, bukan output LLM atau perubahan yang sudah dibuat.
- Untuk opsi usulan, uji kembali agar satu jawaban paling defensible dalam konteks dan tidak ada dua jawaban tampak identik. Jangan memaksakan usulan yang bertentangan dengan kontrak jawaban verbatim: rujukan/versi baru dan proses penerbitan yang berprovenansi perlu diputuskan secara terpisah.

## Audit per soal

### q1 — `iykyk` · easy · post `@guest_01` · kunci B
- **Makna/konteks:** B masuk akal: kue perjalanan klub menjadi rujukan bersama; akronim berarti “if you know, you know”. Komentar tentang icing dan napkin membuat memori bersama cukup alami, meski cukup ditata seperti skenario latihan.
- **Petunjuk:** `questionComment` sudah menyebut “the little reference”; jawaban B sendiri satu-satunya yang menyebut inside joke/reference dan ekspansi singkatan. Jawaban berakhir kutip penutup, bukan karakter titik terakhir.
- **Pengecoh/ambigu:** A, “A secret code for ordering extra cookies”, paling dekat secara lokal karena kata *code* dan kue, tetapi tambahan pemesanan tidak didukung; C/D lebih berupa detail cerita. Tidak terlihat dua jawaban defensible; akronim dapat pula menyiratkan pengalaman bersama, bukan semata lelucon.
- **Usulan editorial:** ubah komentar pertanyaan pada versi baru menjadi “What does that last bit mean?” dan pertahankan petunjuk pengalaman tanpa menyebut “reference”. Pertimbangkan pengecoh sejenis seperti “A reminder to explain a shared memory to everyone” versus jawaban yang menandai pemahaman orang dalam; periksa agar tidak bertumpang tindih.

### q2 — `cooked` · easy · reply `@guest_05` · kunci C
- **Makna/konteks:** C, lelah atau mental tidak fokus, cocok dengan jadwal padat dan catatan yang tampak kabur. Balasan empatik selanjutnya alami; “even my notes look blurry” sedikit eksplisit tetapi bukan salinan definisi.
- **Petunjuk:** komentar pertanyaan menanyakan keadaan setelah hari itu; konteks sendiri sangat mengarah ke kelelahan. Ini petunjuk yang relevan, bukan bukti salah.
- **Pengecoh/ambigu:** B, tidak dapat masuk kelas karena perubahan jadwal, paling dekat dengan topik jadwal, namun perubahan jadwal tidak dinyatakan; A dan D berlawanan dengan nada kelelahan. Tidak tampak dua jawaban defensible dalam pilihan sekarang. *Cooked* juga dapat berarti berada dalam masalah: pack lama `question-bank/packs/cooked-trouble.json`, q `cooked-1`, memakai proyek yang tertinggal dan kunci “in serious trouble”, bukan rasa lelah. Keduanya sense berbeda yang bergantung konteks; jangan menyamakan label pack lama dan baru atau menyatakan salah satu otomatis salah.
- **Usulan editorial:** bila ingin menguji perbedaan sense, buat versi baru dengan opsi “In serious trouble because of a mistake” dan konteks yang mempertahankan kelelahan tetapi tidak menyiratkan konsekuensi serius. Tinjau dengan penutur dan uji apakah salah satu sense masih terbaca ganda.

### q3 — `mewing` · easy · post `@guest_07` · kunci A
- **Makna/konteks:** A menggambarkan praktik posisi lidah, tetapi “face-shaping” bisa terbaca seolah manfaatnya terbukti. Post “showed me mewing ... tongue resting against the roof of the mouth” mengulang hampir persis inti opsi A; alurnya bisa terjadi sebagai penjelasan, namun terasa seperti definisi buku yang disisipkan dalam percakapan.
- **Petunjuk:** kebocoran langsung frasa `tongue resting against the roof of the mouth` dari post ke jawaban. Ini pengamatan teks, bukan hasil pengujian pemain.
- **Pengecoh/ambigu:** B, peregangan rahang, paling dekat dengan wajah dan gerakan; D, latihan napas di depan cermin, juga sedikit relevan. Keduanya tetap tidak cocok dengan deskripsi lidah; pilihan saat ini tidak menunjukkan dua jawaban defensible. Mewing dapat muncul sebagai meme/gerakan diam, bukan selalu sebagai uraian prosedur; soal ini menargetkan sense teknik.
- **Usulan editorial:** contoh post baru, “My cousin keeps mentioning mewing while posing for selfies. Anyone know what the trend is?”; jangan menyalin posisi lidah di konteks. Jawaban versi referensi baru dapat berbunyi “A tongue-posture trend promoted as a way to change facial appearance”, bukan janji hasil; misalnya pengecoh “A jaw-stretching routine before speaking”. Gunakan catatan bahwa klaim hasil tidak terbukti, bukan instruksi melakukan praktik. Lihat sumber AAO di bawah; perubahan definisi perlu versi/provenansi terpisah.

### q4 — `delulu` · easy · reply `@guest_03` · kunci A
- **Makna/konteks:** A cocok dengan prediksi nama perpustakaan setelah satu puisi dan balasan yang menggoda. Hiperbola cukup koheren dan nada “I admire the confidence” menunjukkan godaan ringan, walau setup agak teatrikal.
- **Petunjuk:** “grand prediction” menyorot sifat berlebihan tanpa mengulang definisi. Kata *delusional* dalam kunci bisa terbaca klinis; di sini nuansanya bercanda tentang harapan tidak realistis, bukan diagnosis. Penggunaan lain bisa mengejek, tidak selalu endearing.
- **Pengecoh/ambigu:** D, nama bercanda untuk perayaan pembukaan perpustakaan, meminjam nada humor dan lokasi, tetapi tidak mendeskripsikan prediksi; B/C juga tidak cocok. Tidak tampak dua jawaban defensible.
- **Usulan editorial:** di materi baru pertahankan keterangan bercanda/tidak realistis dan hindari menyimpulkan semua penggunaan ramah; contoh pengecoh yang lebih sejenis: “Playfully cautious about a realistic goal”. Pastikan perbedaan realistis versus sangat tidak realistis tetap jelas.

### q5 — `bsfr` · easy · reply `@guest_06` · kunci C
- **Makna/konteks:** C sesuai ketidakpercayaan pada rencana piknik saat hujan; pertanyaan retoris tentang awan cukup wajar walau disusun rapi. Bark mendukung ekspansi dan fungsi ini; kapitalisasi bervariasi.
- **Petunjuk:** pertanyaan retoris sudah menunjukkan sikap ragu, sementara C satu-satunya ekspansi singkatan dan jauh lebih panjang. Konteks menunjukkan fungsi, tetapi tidak membuktikan ekspansi huruf tanpa pengetahuan sebelumnya.
- **Pengecoh/ambigu:** D, mengonfirmasi keakuratan prakiraan, paling dekat dengan cuaca dan kepastian tetapi bukan sikap pada rencana; A/B menambah fakta tidak ada. Tidak tampak dua jawaban defensible.
- **Usulan editorial:** susun seluruh pengecoh sebagai interpretasi pragmatis yang sebanding, misalnya “An abbreviation used to enthusiastically endorse a surprising plan”; hindari satu-satunya opsi yang memiliki ekspansi dan panjang mencolok. Bila menguji ekspansi akronim, uji tersendiri dengan pilihan ekspansi setara, bukan mencampurkannya dengan fungsi percakapan.

### q6 — `vibe check` · medium · post `@guest_08` · kunci B
- **Makna/konteks:** B masuk akal untuk menanyakan keadaan teman setelah latihan; bahasa post dan tawaran company/solo time terasa wajar meski agak suportif-terstruktur. Istilah dapat juga merujuk suasana kelompok atau situasi; B sengaja lebih sempit karena sumbernya tentang seseorang.
- **Petunjuk:** penanya menyebut “trying to find out from their friend”; kalimat setelah istilah memperjelas fungsi tanpa menduplikasi definisi. `#CheckingIn` juga memberi petunjuk.
- **Pengecoh/ambigu:** C, meminta penilaian publik performa grup, paling dekat dengan penilaian setelah latihan, tetapi tujuan dan publikasi tidak dinyatakan; A/D juga membahas rehearsal saja. B satu-satunya yang kuat, tidak ada alternatif defensible yang jelas.
- **Usulan editorial:** gunakan pengecoh sejenis seperti “An informal assessment of the group's performance” sambil menjaga konteks fokus pada keadaan pribadi, bukan kualitas latihan. Pertahankan nuansa konteks lain di penjelasan bila dipakai sebagai materi pembelajaran.

### q7 — `scuba` · medium · reply `@guest_04` · kunci A
- **Makna/konteks:** A sesuai arti bau yang dilaporkan sumber tercatat, dan tas olahraga, orang mundur, serta komentar “outdoors” mengarah ke bau. Namun “said everything” tidak mengidentifikasi gerak atau niat; bau diinferensikan, bukan dinyatakan. “The little scuba move from the back row” secara gramatikal mungkin, tetapi kealamian pemakaian tulisan dan frekuensinya belum terbukti.
- **Petunjuk:** tas terlupakan dan reaksi tim menyiratkan bau; penjelasan memperlakukannya sebagai pasti. Istilah bisa juga menunjuk nama tarian untuk merayakan sesuatu, sehingga jangan mengasumsikan setiap pelaksanaan gerak tersebut berarti bau. Dalam pilihan aktual tidak ada opsi tarian perayaan; B menyangkut menyelam, bukan arti tarian alternatif. Jadi ada **ambiguitas sense di dunia nyata**, tetapi belum terbukti ada dua opsi defensible yang tersedia.
- **Pengecoh/ambigu:** B, sinyal siap menyelam, adalah salah tafsir literal yang paling jelas untuk istilah; C adalah gerakan yang sesuai ruang ramai namun tidak didukung. Tanpa video, bukti naturalness komentar terbatas.
- **Usulan editorial:** tunda keputusan memakai sense bau sebagai generalisasi sampai ada contoh penggunaan langsung yang dapat diverifikasi. Jika sense tetap dipakai dalam versi baru, tulis pertanyaan sebagai interpretasi gestur *dalam adegan ini*, sertakan petunjuk yang membedakan reaksi terhadap bau dari perayaan tanpa sekadar menyalin definisi; berikan pilihan sejenis termasuk “a celebratory dance after good news”, lalu uji apakah konteks membedakannya. Jangan ubah pack historis secara diam-diam.

### q8 — `rizz` · medium · post `@guest_06` · kunci C
- **Makna/konteks:** C masuk akal sebagai pesona sosial; percakapan dengan penyair tidak dengan sendirinya membuktikan flirting, sehingga jangan menjelaskan kejadian ini sebagai pasti romantis. Kata “or natural charisma” pada kunci memungkinkan pembacaan nonromantis. Cerita dan “That was smoother than my last hello” cukup wajar.
- **Petunjuk:** “personal quality ... praised” mengarahkan ke sifat orang; konteks tertawa dan undangan membantu menyimpulkan pesona. C justru paling pendek.
- **Pengecoh/ambigu:** B, percaya diri mengingat nama di acara, sama-sama kualitas sosial dan paling dekat, namun cerita tidak menyatakan ia mengingat nama; A/D adalah kebiasaan buku, bukan kualitas yang dipuji. Tidak tampak dua jawaban defensible di opsi aktual.
- **Usulan editorial:** ubah penjelasan versi baru menjadi “her easy social charm” tanpa menyiratkan hubungan romantis; jika ingin menguji flirting secara khusus, tambahkan konteks niat romantis yang jelas. Buat pengecoh sifat sosial yang tidak identik, misalnya “Skill at organizing a public reading”.

### q9 — `aura` · hard · reply `@guest_02` · kunci C
- **Makna/konteks:** C cocok sebagai kesan/energi yang dirasakan dari kehadiran Nico. Hubungan antara membantu menata kursi dan suasana ramah dapat dipahami, tetapi dialognya agak formal; ini dekat makna umum *aura*, tidak khusus membuktikan nuansa internet seperti *aura points* atau status keren.
- **Petunjuk:** “aspect of Nico” dan “makes a whole room feel more welcoming” mengarahkan pada kesan pribadi. Jawaban C jauh lebih pendek dari semua opsi lain.
- **Pengecoh/ambigu:** B, suasana tenang dari pencahayaan, dekat secara semantik dengan atmosfer tetapi sumbernya bukan Nico; A, reputasi menyelesaikan tugas cepat, juga sifat orang namun kecepatan/reputasi tidak dibuktikan. Satu jawaban kuat di opsi aktual, walau ‘aura’ dapat bernuansa reputasi atau kesan tergantung komunitas.
- **Usulan editorial:** apabila sasaran slang online adalah reputasi/coolness, bangun konteks spesifik untuk sense tersebut dan rujukan berversi; jika sasaran tetap kesan pribadi, jangan mengklaim mencakup seluruh sense. Seimbangkan panjang opsi, misalnya pengecoh “A person's formal position at the event”.

### q10 — `girl dinner` · hard · post `@guest_05` · kunci D
- **Makna/konteks:** D sesuai piring campuran snack yang dijadikan makan malam; gaya “apparently” dan isi piring cukup alami, meski daftar lengkap sangat memandu. Ungkapan tidak berarti hanya perempuan boleh makan demikian, dan tidak mensyaratkan diet atau porsi tertentu.
- **Petunjuk:** `questionComment` sudah mengatakan “improvised plate”; post menyebut “Dinner” dan daftar snack, hampir merakit definisi jawaban. Label hard tidak membuktikan soal ini sulit.
- **Pengecoh/ambigu:** B, snack untuk tamu sebelum makan besar, paling dekat secara bahan tetapi bertentangan dengan post yang menyatakan piring itu adalah dinner; A/C menambahkan tujuan yang tak dinyatakan. Dalam opsi saat ini D paling defensible; makanan yang sama dapat menjadi appetizer dalam konteks lain, bukan di konteks ini.
- **Usulan editorial:** ganti komentar pada versi baru menjadi “What does the caption mean here?”; gunakan konteks yang tetap menunjukkan makan tetapi kurang mendefinisikan campuran secara lengkap. Misalnya pengecoh “A selection of snacks served before a separate dinner”; uji apakah hubungan sebelum makan versus menjadi makan tetap jelas tanpa mengandalkan stereotip gender.

## Penelusuran sumber dan keterbatasan

### Scuba: URL yang tercatat, penelusuran, dan sense lain

1. Pack mencatat URL perantara persis berikut (bukan URL artikel yang stabil):
   `https://www.google.com/goto?url=CAESoAEBxCJF16l_f0O1WPesf33e-OcaQZbn_LRbGM1M1bo-KsavVGzu0rfES-Cr8j3GAzrDpuvNHXSClTB282t4v9spKhgpBPWEm3TJMhKDGozravyu1ITLYdWYStCN-My5XYZnTnbhsep7iCpL2l4zZ4uP72vU82oxq5OSaJvxnu6Cuqw_LobfdllPmqJcFrNn8SYVsdjtbZYoVtusl8-AcZpO&gsessionid=QpeQQHQeV8u2pjbOziW921TU_ahJ9DkINJlbssTrPhs1UADjcHcTew`.
   Ekstraksi URL tersebut mengembalikan halaman [New York Post, 24 September 2026](https://nypost.com/2026/09/24/lifestyle/the-most-confusing-gen-alpha-slang-of-2026-revealed-report/), judulnya cocok dengan metadata pack. Artikel menyatakan: “To Gen Alphas, ‘scuba’ is a dance indicating that someone or something stinks” dan menjelaskan menutup hidung sambil mengibaskan udara. Kutipan pack mendukung bahwa normalisasi mengikuti artikel; redirect berparameter sesi berisiko tidak permanen. Artikel jurnalistik ini bukan studi linguistik atau bukti prevalensi; penulisannya menggeneralisasi kelompok usia.
2. [Quartz, 26 Oktober 2026](https://qz.com/gen-alpha-slang-terms-decoded-for-parents-2026) mengulangi sense bau, **secara eksplisit merujuk New York Post**, dan mengkritik tidak adanya jumlah kueri, peringkat, rentang waktu dan wilayah pada roundup itu. Karena turunan, ini bukan konfirmasi independen tentang sense bau. Quartz juga mengutip sumber lain untuk pemakaian selebrasi.
3. [Distractify, “What Does ‘Scuba’ Mean on TikTok”](https://www.distractify.com/p/what-does-scuba-mean-tiktok) secara terpisah menggambarkan gerak menutup hidung/melambaikan tangan sebagai *Scuba Dance*/*Scuba Juke*, “often used as a celebration or to express excitement”, dengan contoh dan embed media sosial. Ini mendukung adanya **konteks alternatif**, bukan konfirmasi independen bahwa gerak tersebut berarti bau. Artikel hiburan dan embed bukan korpus representatif; video asalnya tidak ditonton langsung dalam audit ini. Jangan menyimpulkan kedua penggunaan identik dalam setiap komunitas atau bahwa salah satunya dominan.
4. [My Quick Trippers](https://www.myquicktrippers.com/blog/scuba-meaning-gen-alpha-slang) juga memberi sense bau, tetapi secara eksplisit mengandalkan New York Post; bukan bukti independen. Klaim luas seperti “every kid” tidak dapat dijadikan ukuran frekuensi.

**Kesimpulan:** ada dukungan sumber tercatat untuk sense bau, tetapi belum ditemukan dan diverifikasi bukti penggunaan langsung yang independen bagi sense bau dalam penelusuran ini. Statusnya perlu verifikasi lebih lanjut, bukan terbukti salah atau umum dipakai. Sense selebrasi membuat konteks penentu penting.

### Mewing: deskripsi bukan bukti khasiat

- [American Association of Orthodontists — What is Mewing, and Does it Work?](https://aaoinfo.org/whats-trending/is-mewing-bad-for-you/) menjelaskan menempatkan lidah di langit-langit mulut untuk **mencoba** memperbaiki tampilan rahang; klaim membentuk rahang/memperbaiki gigitan tidak didukung bukti ilmiah menurut halaman itu. AAO memperingatkan tekanan atau posisi tidak tepat dapat menimbulkan masalah dan menyarankan konsultasi profesional untuk masalah rahang/gigi. Ini artikel informasi organisasi profesi, **bukan** systematic review atau konsultasi individual; ia mendukung kehati-hatian, bukan pernyataan bahwa setiap posisi lidah normal berbahaya atau bahwa efektivitas mustahil pada semua keadaan.
- [UPrinting — Top 60 Gen Z Slang Words](https://www.uprinting.com/blog/top-gen-z-slang-words/) adalah halaman sumber tercatat untuk q1–q4, q6, q8–q10; penjelasan slang-nya adalah daftar pemasaran, bukan bukti medis atau validasi penggunaan setiap kalimat yang dibuat model. URL pack memiliki parameter pelacakan; ekstraksi mengarah ke URL langsung ini. Cuplikan `mewing` yang tersimpan dalam pack terpotong; jangan menganggapnya kutipan medis lengkap.
- [Bark — Teen Slang & Text Codes](https://www.bark.us/blog/teen-text-speak-codes-every-parent-should-know/) adalah halaman sumber q5: menyebut BSFR berarti “Be So For Real” dan menyatakan ketidakpercayaan/“Are you serious?”. Ini panduan perusahaan parental control, bukan kajian representatif atau persetujuan ahli atas soal. URL pack berparameter mengarah ke halaman langsung ini.

## Tindak lanjut yang diusulkan, bukan dilakukan

1. Tinjau q7 dengan contoh sumber penggunaan langsung yang sah dan konteksnya; dokumentasikan hubungan antar-sumber agar artikel turunan tidak dihitung sebagai konfirmasi independen.
2. Review q3 dengan bahasa yang menyatakan klaim sebagai klaim, tanpa anjuran melakukan praktik; hilangkan salinan definisi pada konteks versi baru.
3. Uji pengecoh, kejelasan target post/reply, format opsi dan kesulitan dengan peserta/penutur yang relevan; catat metode dan hasil sebelum menyebutnya terkalibrasi. Cek tampilan nyata secara terpisah dari statistik JSON.
4. Pertahankan pack dan arsip published apa adanya. Jika diputuskan mengganti atau menonaktifkan item, rancang versi, asal-usul referensi, dan publikasi secara eksplisit setelah persetujuan; saran di dokumen ini bukan perubahan bank atau hasil generasi baru.
