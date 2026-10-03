# ResQMesh: Protokol Routing Mesh P2P Berbasis BLE untuk Komunikasi Darurat Tanpa Internet

Proyek penelitian rekayasa perangkat lunak dan jaringan komunikasi darurat (*Delay-Tolerant Mobile Ad-Hoc Network / DTN-MANET*) untuk menyebarkan sinyal darurat (SOS) berisi koordinat lokasi dan informasi keadaan secara *device-to-device* (*peer-to-peer*) melalui perangkat seluler di sekitar tanpa memerlukan infrastruktur seluler atau internet.

---

## 1. Status Implementasi & Kesiapan Eksperimen Lapangan (Field-Ready Baseline)

- **Platform & Teknologi:** Android (Kotlin, Jetpack Compose, Room Database, Android BLE API / `BluetoothLeScanner` & `BluetoothLeAdvertiser`).
- **Penyelesaian Logika Protokol (F1 – F12):** Seluruh fitur inti (Alokasi Node ID 24-bit, Device Discovery BLE, Pengambilan Snapshot Lokasi, Pembuatan SOS Dua-Rantai, Multi-Hop Relay, Duplicate Detection, TTL Enforcement, Hop Count Tracking, Lifecycle Status, ACK Propagation, Local History, dan Store-and-Forward Buffer) telah selesai diimplementasikan dan diverifikasi melalui **85+ unit test pengujian otomatis JVM**.
- **Integrasi Transport Hardware Native:** Lapisan transport telah terhubung secara penuh ke driver perangkat keras native melalui `BleMeshTransport` (Company ID `0xE000`, struktur payload biner 27-byte), menggantikan `NoOpMeshTransport` demi beralih dari fase simulasi in-memory menuju fase eksperimen validasi perangkat fisik (*field testing*).

---

## 2. Metrik Evaluasi Penelitian (KTI Metrics)

Dalam rangka pengujian prototipe untuk Karya Tulis Ilmiah (KTI), variabel unjuk kerja (*performance evaluation metrics*) utama yang diamati meliputi:

1. **Packet Delivery Ratio (PDR):** Rasio keberhasilan paket SOS diterima oleh node responder atau perantara terhadap total paket yang disiarkan dalam berbagai skenario topologi.
2. **End-to-End Latency:** Waktu tunda total (*delay*) sejak tombol SOS ditekan di perangkat asal (*origin*) hingga paket darurat diterima dan dikonfirmasi (*DELIVERED*) melalui ACK oleh responder.
3. **Hop Count Verification:** Keakuratan inkremental hop (*F8*) dalam mencerminkan jarak topologi jaringan multi-hop secara riil.
4. **Location Integrity:** Keutuhan dan presisi data koordinat (*snapshot latitude, longitude, accuracy*) selama proses estafet data melintasi simpul perantara (*F3* & *F5*).
5. **Resilience & Fault Tolerance:** Ketahanan jaringan (*resilience*) terhadap kegagalan node (*node failure*) atau pergerakan node di luar jangkauan menggunakan mekanisme *Store-and-Forward* (*F12*).

---

## 3. Konsep Arsitektur & Metodologi

ResQMesh dirancang dengan pendekatan *Application-Layer Flooding Protocol* dengan optimasi anggaran payload iklan BLE (*Bluetooth Low Energy Advertising*) yang sangat ketat (maksimal 27 byte payload biner per frame):

```text
Ponsel Korban (Origin) → Simpul Perantara (Relay Node) → Simpul Responder (Gateway/Responder)
```

- **Struktur Paket Dua-Rantai (*Two-Tier SOS Chain*):** Mengatasi batasan ukuran paket BLE dengan memecah informasi darurat menjadi dua pesan berantai (`SOS_LOC` 16-byte untuk koordinat kilat dan `SOS_DETAIL` 7-byte header + teks catatan darurat).
- **Manajemen Router & Relay:** Setiap simpul perantara mempertahankan identitas sumber (*origin sender*) dan `message_id` asli, menerapkan dekrementasi TTL (*F7*), inkrementasi hop (*F8*), serta pencegahan duplikasi atomik (*F6*).

---

## 4. Panduan Menjalankan & Pengujian

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

## 5. Dokumentasi Referensi Penelitian

Spesifikasi teknis, rincian protokol biner, dan pemetaan kebutuhan dikelola secara ketat melalui dokumen acuan resmi:
- [`ResQMesh_PRD_v3.md`](ResQMesh_PRD_v3.md) — Acuan spesifikasi target pengujian fisik (*field-ready baseline*).
- [`ResQMesh_PRD_v2.md`](ResQMesh_PRD_v2.md) — Dokumen transisi kebutuhan produk.
- [`ResQMesh_PRD.md`](ResQMesh_PRD.md) — Spesifikasi PRD versi awal (*historis*).
- [`ResQMesh_Technical_Specification.md`](ResQMesh_Technical_Specification.md) — Spesifikasi arsitektur teknis mendalam dan pemetaan desain biner.
