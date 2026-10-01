Warning: truncated output (original token count: 1254)
Total output lines: 79

# ResQMesh

Prototype aplikasi Android untuk menyebarkan SOS berisi lokasi dan informasi keadaan melalui perangkat sekitar, dengan tujuan mendukung komunikasi saat internet atau jaringan seluler tidak tersedia.

## Status implementasi sekarang

- Aplikasi Android menggunakan Kotlin, Jetpack Compose, dan Room.
- Fondasi model SOS, encoding paket, penyimpanan lokal, logika relay, TTL, deteksi duplikasi, fragmentasi, dan ACK sudah ada di source.
- Aplikasi **belum dapat mengirim SOS antarponsel melalui BLE**. `AppGraph` saat ini menggunakan `NoOpMeshTransport`, transport kosong untuk pengembangan dan pengujian logika tanpa perangkat.
- Gradle Wrapper 8.7 disertakan untuk menjalankan build dengan versi yang sesuai dengan Android Gradle Plugin 8.5.2 proyek.
- Karena itu, status jaringan atau paket yang muncul di UI belum membuktikan komunikasi perangkat sungguhan.

## Kebutuhan untuk membuka proyek

- Android Studio dengan dukungan proyek Android/Kotlin.
- JDK 17 untuk konfigurasi Java/Kotlin proyek.
- Android SDK Platform 34 dan Android SDK Build-Tools.
- Untuk pengujian komunikasi di masa mendatang: minimal dua ponsel Android dengan BLE; pengujian multi-hop memerlukan setidaknya tiga ponsel.

## Cara menjalankan

1. Pasang kebutuhan di atas dan buka folder proyek ini di Android Studio.
2. Jalankan Gradle Sync di Android Studio.
3. Untuk membangun APK debug dari Windows, jalankan `.\gradlew.bat :app:assembleDebug` dari folder proyek. APK keluaran berada di `app/build/outputs/apk/debug/`.
4. Untuk menjalankan tes unit JVM, jalankan `.\gradlew.bat :app:testDebugUnitTest`.
5. Setelah build berhasil, pilih konfigurasi `app` dan perangkat Android atau emulator di Android Studio, lalu tekan **Run**.
6. Untuk uji BLE antarponsel, implementasikan transport BLE sungguhan terlebih dahulu dan hubungkan ke `AppGraph`; transport saat ini masih `NoOpMeshTransport`.

Build dan tes belum diverifikasi pada checkout ini.

## Konsep produk

ResQMesh mengeksplorasi pengiriman informasi darurat dari satu ponsel ke ponsel lain. Perangkat perantara dapat meneruskan SOS yang sama sampai mencapai responder atau gateway.

```text
Ponsel korban → ponsel relay → ponsel responder
```

Setiap relay mempertahankan identitas sumber dan `message_id` SOS. Relay memvalidasi dan menyimpan pesan, lalu meneruskannya jika aturan duplikasi dan TTL mengizinkan.

SOS dapat berisi jenis keadaan darur…54 tokens truncated…

## Tujuan dan batasan prototype

Tujuan prototype adalah menguji apakah perangkat Android dapat menyebarkan SOS secara device-to-device tanpa internet, termasuk melalui beberapa relay.

ResQMesh bukan pengganti jaringan seluler, sistem komunikasi SAR profesional, atau konfirmasi bahwa bantuan fisik telah dikirim. ACK hanya menandakan paket diterima oleh perangkat responder yang ditentukan. Keberhasilan komunikasi dapat dipengaruhi oleh Bluetooth, izin Android, jarak, kondisi perangkat, dan apakah aplikasi sedang berjalan.

Di luar cakupan MVP: panggilan suara/video, transfer berkas besar, internet/cloud, live location tracking, serta jaringan skala besar.

## Teknologi dan struktur

- **Platform dan bahasa:** Android, Kotlin.
- **UI:** Jetpack Compose.
- **Penyimpanan:** Room Database.
- **Lokasi:** Android Location Services.
- **Transport yang direncanakan:** BLE device-to-device; implementasi aktual belum tersedia.
- **Arsitektur source:** lapisan UI, domain, data, dan logika mesh berada di modul `app`.

## Rencana pengujian

Lakukan pengujian bertahap setelah transport BLE tersedia:

1. **Dua ponsel, pengiriman langsung:** A mengirim SOS ke B tanpa internet.
2. **Tiga ponsel, relay:** A mengirim melalui B ke C.
3. **Aturan relay:** verifikasi identitas dan isi SOS tetap, paket duplikat tidak membuat insiden baru, TTL membatasi penerusan, dan hop count tercatat benar.
4. **Responder dan ACK:** verifikasi penerimaan responder dan status ACK jika jalur balik tersedia.
5. **Kondisi gagal:** Bluetooth mati, izin ditolak, lokasi tidak tersedia, perangkat di luar jangkauan, dan relay tidak aktif.

Untuk setiap percobaan, catat topologi, kondisi koneksi, keberhasilan penerimaan, kecocokan isi/lokasi, latency, hop count, ACK, duplikasi, dan error.

Cisco Packet Tracer dapat dipakai sebagai simulasi pendukung untuk topologi, jalur alternatif, dan kegagalan link/node. Packet Tracer tidak menguji aplikasi Android, BLE, lokasi, atau implementasi relay ResQMesh; hasil simulasi harus dipisahkan dari hasil pengujian ponsel nyata.

## Dokumentasi

- [`ResQMesh_PRD_v2.md`](ResQMesh_PRD_v2.md) — acuan kebutuhan produk saat ini.
- [`ResQMesh_PRD.md`](ResQMesh_PRD.md) — PRD versi awal.
- [`ResQMesh_Technical_Specification.md`](ResQMesh_Technical_Specification.md) — spesifikasi teknis draft, termasuk keputusan desain, status implementasi, dan gap yang masih terbuka.

