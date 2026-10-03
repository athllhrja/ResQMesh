# PRD Implementation Progress — ResQMesh (Transition to PRD v3)

Dokumen ini melacak status implementasi, verifikasi kode, dan kesiapan pengujian prototipe ResQMesh berdasarkan evolusi dari **PRD v2.0** menuju spesifikasi target **PRD v3.0 (Field-Ready Physical Testing)** serta dokumen acuan teknis (*Technical Specification*).

---

## 1. Ringkasan Status Fitur (F1 – F12)

| Feature ID | Feature Name | Priority | Status | Implementasi & Catatan Teknis (PRD v3 Baseline) |
|---|---|:---:|:---:|---|
| **F1** | Node Identity | P0 | **COMPLETED** | Alokasi identitas 24-bit (`NodeId`, 3-byte / 6-digit hex uppercase), disimpan persisten di `SharedPreferences` (`resqmesh_identity`), dicegah duplikasinya via `allowBackup="false"`, siap untuk enkoding frame BLE. |
| **F2** | Device Discovery | P0 | **INTEGRATED** | Implementasi ganda BLE: `BluetoothLeScanner` dan `BluetoothLeAdvertiser` native dengan Company ID `0xE000`. Memancarkan dan mem-parsing frame beacon 27-byte secara berkala. Riwayat tetangga tersimpan di Room DB (`NodeDao`). |
| **F3** | Location Capture | P0 | **VERIFIED** | Pengambilan snapshot koordinat offline satu kali (*one-time snapshot*, bukan *live tracking*) via `LocationSource` / `LocationManager`. Menangani izin runtime lokasi, fallback akurasi, dan validasi kesegaran data koordinat. |
| **F4** | SOS Creation | P0 | **VERIFIED** | Alur pembuatan sinyal darurat melalui dialog Compose, menyatukan Node ID, koordinat lokasi, debouncing pencegah pemicuan ganda, dan pembagian paket berantai (`SOS_LOC` & `SOS_DETAIL`). |
| **F5** | SOS Relay | P0 | **INTEGRATED** | Kontroler multi-hop forwarding (`SosRelayController` / `RelayEngine`). Menjaga original `message_id` dan origin sender tetap utuh (*source-preserved*), melakukan dekrementasi TTL, penambahan hop count, dan *split-horizon echo suppression*. |
| **F6** | Duplicate Detection | P0 | **VERIFIED** | Pencegahan banjir pesan (*flood suppression*) secara atomik via `DuplicateGuard` dan Room DB (`SeenMessageDao`). Menolak perulangan *forwarding* pesan yang sama dan mendukung pembersihan retensi berbasis waktu (`expiresAt`). |
| **F7** | TTL (Hop Limit) | P0 | **VERIFIED** | Penegakan batas sebaran melalui `TtlPolicy`: TTL awal ditentukan saat pembuatan, didekrementasi 1 (`ttl - 1`) hanya ketika diteruskan (*forward*), dan paket langsung di-drop jika `ttl <= 0`. |
| **F8** | Hop Count Tracking | P0 | **VERIFIED** | Pelacakan jarak jaringan hop: bernilai 0 di pengirim asal (*origin*), bertambah 1 (`hopCount + 1`) pada setiap simpul perantara (*relay node*) untuk metrik evaluasi. |
| **F9** | SOS Status Lifecycle | P0 | **VERIFIED** | Pelacakan siklus hidup status SOS (`CREATED`, `RELAYED`, `DELIVERED`, `EXPIRED`) yang disimpan persisten dan reaktif pada Room DB (`MessageDao`). |
| **F10** | ACK (Delivery Confirmation) | P1 | **INTEGRATED** | Penyebaran konfirmasi penerimaan elektronik via `AckController` / `AckTracker`. Mengirim balik frame ACK dengan referensi `message_id` darurat asli dan memperbarui status pengirim lokal menjadi `DELIVERED`. |
| **F11** | Local SOS History | P1 | **VERIFIED** | Riwayat lokal persisten via Room DB (`Flow<List<SosIncident>>`). Menggabungkan dua frame (`SOS_LOC` + `SOS_DETAIL`) menjadi satu insiden utuh di UI, dengan pemisahan query pesan sendiri (*self*) dan pesan relay (*peer*). |
| **F12** | Store-and-Forward | P1 | **INTEGRATED** | Buffer outbox antrean pesan tunda (`StoreAndForwardQueue`) untuk kondisi node tujuan/tetangga belum terdeteksi. Otomatis memicu siaran ulang begitu simpul baru terdeteksi oleh BLE scanner. |

---

## 2. Pemetaan Penutupan Gap Arsitektur (Tech Spec → PRD v3)

Menjelaskan penyesuaian teknis kritis dari fase simulasi (PRD v2) menuju kesiapan perangkat fisik (PRD v3):

1. **Aktivasi Native BLE Transport (Menutup Gap D1):**
   - Transisi dari `NoOpMeshTransport` (stub pengujian lokal) ke `BleMeshTransport` native di `AppGraph.kt`.
   - Integrasi langsung dengan modul radio hardware Android (`BluetoothLeScanner` & `BluetoothLeAdvertiser`).

2. **Dua-Rantai Struktur Paket SOS (Keputusan D5 & Bab 14 Tech Spec):**
   - Mengatasi batas kapasitas payload iklan BLE legacy (maksimal 27 byte payload dari struktur 31 byte AD):
     - **Frame 1 (`SOS_LOC` - 16 byte):** Membawa koordinat s24 lat/lon, tingkat akurasi, usia fix, indikator baterai, estimasi jumlah korban, dan bendera hazard darurat.
     - **Frame 2 (`SOS_DETAIL` - 7 byte header + teks):** Membawa referensi sequence dan origin pengirim serta catatan tambahan UTF-8.
   - Agregasi tampilan di sisi UI ditangani oleh mapper `toSosIncidents()`.

3. **In-App Simulation vs Physical Device Readiness:**
   - Logika protokol telah divalidasi oleh 85+ unit test JVM dan skenario pengujian simulasi integrasi in-memory multi-hop (Node A -> Node B -> Node C).
   - Modul perizinan Android runtime telah disesuaikan (mendukung Android 12+ API 31 `BLUETOOTH_SCAN` dengan flag `neverForLocation` serta izin `ACCESS_FINE_LOCATION` untuk penangkapan snapshot GPS).

---

## 3. Matriks Skenario Uji Coba Lapangan (Physical Testing Matrix)

Rencana verifikasi langsung menggunakan perangkat fisik Android tanpa jaringan internet/seluler:

| Kode Uji | Skenario | Target / Topologi | Kriteria Keberhasilan PRD v3 | Status |
|:---:|---|---|---|:---:|
| **T1** | Direct Transmission | HP A -> HP B | Pesan SOS diterima langsung; Hop Count = 0 / 1; status `DELIVERED`. | Siap Diuji |
| **T2** | Single-Relay Multi-Hop | HP A -> HP B -> HP C | HP B meneruskan paket; HP C menerima koordinat utuh milik HP A; Hop = 1 / 2. | Siap Diuji |
| **T3** | Extended Multi-Hop | HP A -> HP B -> HP C -> HP D | Rantai estafet 3 relay berhasil menjangkau responder terjauh. | Siap Diuji |
| **T5** | Duplicate Suppression | HP A -> HP B / HP C -> HP D | Paket identik dari dua jalur hanya menghasilkan 1 insiden di HP D; duplikat di-drop. | Siap Diuji |
| **T6** | TTL Expiration | Skenario paket dengan TTL habis | Paket tidak lagi diteruskan saat nilai TTL mencapai 0. | Siap Diuji |
| **T7** | Reverse ACK Flow | Respon balik dari Responder ke Korban | Status SOS di HP asal otomatis beralih menjadi `DELIVERED` setelah ACK diterima. | Siap Diuji |
