# Rancang Bangun ResQMesh: Sistem Komunikasi SOS Darurat Offline Berbasis Store-and-Forward Multi-Hop BLE untuk Situasi Bencana

Proyek penelitian rekayasa perangkat lunak dan jaringan komunikasi darurat (*Delay-Tolerant Mobile Ad-Hoc Network / DTN-MANET*) untuk menyebarkan sinyal darurat (SOS) berisi koordinat lokasi dan informasi keadaan secara *device-to-device* (*peer-to-peer*) melalui perangkat seluler di sekitar tanpa memerlukan infrastruktur seluler atau internet.

---

## 1. Status Implementasi & Kesiapan Eksperimen Lapangan

- **Platform & Teknologi:** Android (Kotlin, Jetpack Compose, Room Database, Android BLE API / `BluetoothLeScanner` & `BluetoothLeAdvertiser`).
- **Penyelesaian Logika Protokol (F1 – F12):** Seluruh fitur inti (Alokasi Node ID 24-bit, Device Discovery BLE, Pengambilan Snapshot Lokasi, Pembuatan SOS Dua-Rantai, Multi-Hop Relay, Duplicate Detection, TTL Enforcement, Hop Count Tracking, Lifecycle Status, Broadcast SOS ACK & Responder Role, Local History, dan Store-and-Forward Buffer) telah selesai diimplementasikan dan diverifikasi melalui **100+ unit test pengujian otomatis JVM**.
- **Integrasi Transport Hardware Native:** Lapisan transport telah terhubung secara penuh ke driver perangkat keras native melalui `BleMeshTransport` (Company ID `0xFFFF`, struktur payload biner 27-byte), mengoperasikan Foreground Service (`connectedDevice`) demi menjamin jaringan mesh tetap berjalan di latar belakang.

---

## 2. Metrik Evaluasi Penelitian (KTI Metrics)

Dalam rangka pengujian prototipe untuk Karya Tulis Ilmiah (KTI), variabel unjuk kerja (*performance evaluation metrics*) utama yang diamati meliputi:

1. **Packet Delivery Ratio (PDR):** Rasio keberhasilan paket SOS diterima oleh node responder atau perantara terhadap total paket yang disiarkan dalam berbagai skenario topologi.
2. **Round-Trip Time (RTT):** Waktu tunda total (*delay*) sejak tombol SOS ditekan di perangkat asal (*origin*) hingga paket darurat diterima dan dikonfirmasi (*DELIVERED*) melalui ACK oleh responder pada jam tunggal HP Origin.
3. **Hop Count Verification:** Keakuratan inkremental hop dalam mencerminkan jarak topologi jaringan multi-hop secara riil.
4. **Location Integrity:** Keutuhan dan presisi data koordinat (*snapshot latitude, longitude, accuracy*) selama proses estafet data melintasi simpul perantara ($1/10.000$ derajat desimal / ~11 m di lintang).
5. **Resilience & Fault Tolerance:** Ketahanan jaringan (*resilience*) terhadap kegagalan node (*node failure*) atau pergerakan node di luar jangkauan menggunakan mekanisme *Store-and-Forward* & *Relay Carry*.

---

## 3. Konsep Arsitektur & Metodologi

ResQMesh dirancang dengan pendekatan *Application-Layer Flooding Protocol* dengan optimasi anggaran payload iklan BLE (*Bluetooth Low Energy Advertising*) yang sangat ketat (maksimal 27 byte payload biner per frame):

```text
Ponsel Korban (Origin) → Simpul Perantara (Relay Node) → Simpul Responder (Gateway/Responder)
```

- **Struktur Paket Dua-Rantai (*Two-Tier SOS Chain*):** Mengatasi batasan ukuran paket BLE dengan memecah informasi darurat menjadi dua pesan berantai (`SOS_LOC` 16-byte untuk koordinat kilat dan `SOS_DETAIL` 7-byte header + teks catatan darurat).
- **Manajemen Router & Relay:** Setiap simpul perantara mempertahankan identitas sumber (*origin sender*) dan `message_id` asli, menerapkan dekrementasi TTL, inkrementasi hop, serta pencegahan duplikasi atomik (`DuplicateGuard`).

---

## 4. Batasan dan Model Ancaman (Limitations and Threat Model)

1. **SOS/ACK Bisa Dipalsukan (Tanpa Autentikasi & Anti-Spoofing)**: Frame dikirimkan tanpa enkripsi kriptografi atau tanda tangan digital (*digital signature*). Perangkat jahat dapat memalsukan frame SOS/ACK milik Node ID lain.
2. **Lokasi Disiarkan Tanpa Enkripsi (Unencrypted Plaintext Broadcast)**: Koordinat GPS ($1/10.000$ derajat) dan teks catatan disiarkan secara terbuka di udara BLE agar seluruh penolong dapat membacanya secara instan tanpa hambatan tukar kunci.
3. **Tidak Ada Verifikasi Identitas Pemilik HP (Unauthenticated Node Identity)**: Node ID 24-bit dialokasikan secara acak tanpa terhubung ke sistem identitas fisik terpusat.

---

## 5. Panduan Menjalankan & Pengujian

1. **Prasyarat Sistem:**
   - Android Studio (Electric Eel / Iguana atau versi terbaru).
   - JDK 17.
   - Android SDK Platform 34 (minimum SDK 26 / Android 8.0).
   - Minimal 2 hingga 3 perangkat fisik Android dengan dukungan Bluetooth Low Energy (BLE) untuk pengujian multi-hop.
2. **Sinkronisasi & Build:**
   - Buka direktori proyek di Android Studio dan lakukan *Gradle Sync*.
   - Jalankan perintah build APK Debug melalui terminal/Gradle: `.\gradlew.bat :app:assembleDebug`.
3. **Pengujian Unit & Simulasi:**
   - Jalankan rangkaian unit test otomatis JVM: `.\gradlew.bat :app:testDebugUnitTest`.

---

## 6. Dokumentasi Referensi Penelitian

Spesifikasi teknis, rincian protokol biner, dan pemetaan kebutuhan dikelola secara ketat melalui dokumen acuan resmi:
- [`docs/IMPLEMENTATION_STATUS.md`](docs/IMPLEMENTATION_STATUS.md) — Acuan status implementasi fitur F1–F12 dan kesiapan eksperimen.
- [`docs/TECHNICAL_SPECIFICATION.md`](docs/TECHNICAL_SPECIFICATION.md) — Spesifikasi arsitektur teknis mendalam dan pemetaan desain biner 27-byte.
- [`docs/archive/`](docs/archive/) — Arsip historis PRD versi awal.
