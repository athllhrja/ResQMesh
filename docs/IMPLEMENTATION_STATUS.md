# Rancang Bangun ResQMesh: Sistem Komunikasi SOS Darurat Offline Berbasis Store-and-Forward Multi-Hop BLE untuk Situasi Bencana

## Status Implementasi Fitur & Modul Protokol (F1 – F12)

Dokumen ini mencatat status implementasi terkini dari seluruh fitur protokol ResQMesh berdasarkan kode sumber nyata dan pengujian unit otomatis JVM (100+ unit test passed).

---

### Tabel Status Kebutuhan Fungsional (F1 – F12)

| Kode | Nama Fitur / Modul | Deskripsi Singkat | Status Implementasi |
|---|---|---|---|
| **F1** | Node Identity & Sender Allocation | Alokasi acak Node ID 24-bit per sesi instalasi dengan pencegahan konflik. | `VERIFIED-UNIT-TEST` |
| **F2** | BLE Neighbor Discovery & Beaconing | Pemindaian latar belakang dan siaran Beacon periodik (300 ms / 150 ms SOS). | `IMPLEMENTED-UNTESTED-ON-DEVICE` |
| **F3** | Single-Fix Location Provider | Pengambilan snapshot lokasi satu kali dari LocationManager saat SOS ditekan. | `IMPLEMENTED-UNTESTED-ON-DEVICE` |
| **F4** | Two-Tier SOS Chain (LOC & DETAIL) | Pemisahan SOS menjadi frame kilat koordinat (`SOS_LOC`) & rincian catatan (`SOS_DETAIL`). | `VERIFIED-UNIT-TEST` |
| **F5** | Location Coordinate Scaling | Kompresi biner koordinat presisi $1/10.000$ derajat desimal (~11 m di lintang). | `VERIFIED-UNIT-TEST` |
| **F6** | Duplicate Detection & Flood Control | Pencegahan badai paket dua level (Frame & Message) di `DuplicateGuard`. | `VERIFIED-UNIT-TEST` |
| **F7** | TTL Enforcement & Decrement | Batas lompatan maksimum (TTL awal 10) dan dekrementasi $TTL - 1$ per hop di `TtlPolicy`. | `VERIFIED-UNIT-TEST` |
| **F8** | Hop Count Incremental Tracking | Pelacakan inkremental $hopCount + 1$ untuk mencerminkan jarak topologi jaringan. | `VERIFIED-UNIT-TEST` |
| **F9** | Lifecycle Status Management | Pengelolaan fase MeshState (STOPPED, SCANNING, ACTIVE, ERROR) & Service Foreground. | `VERIFIED-UNIT-TEST` |
| **F10** | Broadcast SOS ACK & Responder Role | Mode node (Warga / Tim Penolong), pembuatan ACK responder, TTL ACK fixed (10), dan deduplikasi ACK. | `VERIFIED-UNIT-TEST` |
| **F11** | Local History & Incident Aggregation | Penyimpanan Room Database lokal, agregasi dua pesan per insiden, dan pencatatan hop. | `VERIFIED-UNIT-TEST` |
| **F12** | Store-and-Forward Buffer & Relay Carry | Retransmisi origin periodik dengan backoff, relay carrying (status `CARRYING`), dan Trickle suppression. | `VERIFIED-UNIT-TEST` |
| **GATT** | Direct Link Tier 2 (GATT P2P) | Koneksi langsung GATT antar peer berjarak dekat. | `NOT-IMPLEMENTED` |

---

## Batasan dan Model Ancaman (Limitations and Threat Model)

Prototipe ResQMesh dirancang khusus untuk situasi darurat bencana alam di mana ketersediaan (*availability*) dan kecepatan penyampaian sinyal darurat tanpa internet menjadi prioritas utama. Oleh karena itu, arsitektur ini memiliki batasan dan model ancaman berikut:

1. **SOS/ACK Bisa Dipalsukan (Tanpa Autentikasi & Anti-Spoofing)**:
   - Frame biner dikirimkan secara terbuka tanpa tanda tangan digital (*digital signature*) atau sertifikat terotentikasi.
   - Node jahat (*malicious actor*) dapat membuat frame SOS atau ACK palsu menggunakan Node ID mana pun.

2. **Lokasi Disiarkan Tanpa Enkripsi (Unencrypted Plaintext Broadcast)**:
   - Koordinat lokasi ($1/10.000$ derajat) dan teks catatan darurat disiarkan secara terbuka di udara BLE agar seluruh perangkat penolong sekitar dapat membacanya secara instan tanpa hambatan tukar kunci (*key exchange*).
   - *Eavesdropper* di jangkauan BLE dapat menyadap lokasi korban.

3. **Tidak Ada Verifikasi Identitas Pemilik HP (Unauthenticated Node Identity)**:
   - Node ID 24-bit dialokasikan secara acak tanpa terhubung ke sistem identitas terpusat (seperti NIK, OIDC, atau PKI).
