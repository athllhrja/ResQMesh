# ResQMesh — instruksi untuk agen

Proyek: aplikasi Android (Kotlin, Jetpack Compose, Room, package com.resqmesh).
SOS darurat offline lewat BLE advertising (frame 27 byte), store-and-forward multi-hop.
Judul lomba: "Rancang Bangun ResQMesh: Sistem Komunikasi SOS Darurat Offline
Berbasis Store-and-Forward Multi-Hop BLE untuk Situasi Bencana".

## Aturan kerja
- Baca file yang relevan sebelum mengubah. Jangan menebak isi file.
- Satu tugas per percakapan. Jangan menyentuh file di luar cakupan tugas;
  kalau perlu, sebutkan alasannya lebih dulu.
- Setelah perubahan: build, lalu jalankan unit test (:app:testDebugUnitTest).
  Perbaiki yang gagal sebelum lanjut. Jangan menghapus tes lama; jika sebuah tes
  salah karena perilaku lama, ubah dan jelaskan alasannya.
- Konstanta baru masuk core/MeshConfig.kt. String UI masuk strings.xml (Bahasa Indonesia).
- Jangan menulis "terverifikasi di perangkat" kecuali kamu benar-benar menjalankannya
  di perangkat terhubung dan menunjukkan log-nya.
- Untuk fakta API Android (izin, tipe foreground service, perilaku BLE), rujuk
  dokumentasi developer.android.com dan sebut halamannya. Kalau tidak yakin, tulis
  "perlu diverifikasi". Jangan mengarang.
- Kalau instruksi tugas bertentangan dengan kode nyata, berhenti dan laporkan.
- Jika dokumen (README, PRD, spesifikasi) bertentangan dengan kode, anggap kode yang
  benar dan laporkan ketidakcocokannya. Jangan menyimpulkan sebuah fitur "sudah
  selesai" hanya dari status di dokumen.
- Akhiri tiap tugas dengan laporan: file yang diubah, tes yang ditambah, hasil tes,
  keputusan desain beserta alasan, dan daftar "belum terverifikasi di perangkat".
