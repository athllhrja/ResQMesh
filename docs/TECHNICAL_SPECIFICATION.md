# Rancang Bangun ResQMesh: Sistem Komunikasi SOS Darurat Offline Berbasis Store-and-Forward Multi-Hop BLE untuk Situasi Bencana

## Spesifikasi Teknis & Arsitektur Protokol

Dokumen ini menjelaskan arsitektur teknis, spesifikasi format biner 27-byte, model status, serta mekanisme *Store-and-Forward Multi-Hop* pada protokol ResQMesh.

---

## 1. Arsitektur Komponen & Lifecycle Service

```text
[Aplikasi UI Jetpack Compose / ViewModel]
        │
        ▼
[MeshService - Foreground Service (connectedDevice)]
        │
        ▼
[MeshManager] ────► [StoreAndForwardQueue]
   │       │
   │       ├──► [BeaconScheduler] ────► [BleMeshTransport (Company ID 0xFFFF)]
   │       │                                  │
   ▼       ▼                                  ▼
[RelayEngine] ◄────────────────────── [BLE Airwave / Media Udara]
   │       │
   │       ├──► [DuplicateGuard (SeenDao & SeenFrameDao)]
   │       ├──► [FragmentAssembler]
   │       └──► [AckTracker]
   ▼
[Room Database - ResQMeshDatabase (v2)]
```

### 1.1 Android Foreground Service (`MeshService`)
- Untuk menjamin jaringan mesh tetap aktif saat aplikasi berada di latar belakang atau layar terkunci, `MeshService` dijalankan sebagai **Foreground Service** dengan `android:foregroundServiceType="connectedDevice"` (targetSdk 34).
- Menampilkan notifikasi persisten bertajuk *"ResQMesh Aktif"* dengan action button *"Hentikan mesh"*.

### 1.2 Mode Node (Warga vs Tim Penolong / Responder)
- Perangkat dapat berada dalam salah satu dari dua mode:
  1. **Mode Warga (Default)**: Meneruskan pesan, membawa SOS (`CARRYING`), dan menerima peringatan darurat.
  2. **Mode Tim Penolong / Responder**: Menyimpan SOS dan **menghasilkan paket ACK otomatis** begitu merakit SOS (`LOC` dan `DETAIL`) hingga selesai.
- Flag `NodeStatusFlags.RESPONDER_NODE = 0x20` disiarkan melalui Beacon BLE.

---

## 2. Format Biner 27-Byte & Protokol Frame

Seluruh komunikasi BLE Advertising menggunakan payload biner dikunci tepat pada **27 byte** (Manufacturer Specific Data, Company ID `0xFFFF` - Bluetooth SIG Test/Prototype ID).

### 2.1 Struct Frame BEACON (27 Byte)
| Offset | Ukuran | Field | Deskripsi |
|---|---|---|---|
| 0 | 1 byte | Version | Protocol Version (`0x01`) |
| 1 | 1 byte | Type | FrameType (`0x01` = BEACON) |
| 2–4 | 3 byte | Node ID | Identitas Node 24-bit pengirim |
| 5 | 1 byte | Status Flags | Bitmask (`0x01` Active, `0x10` Scanning, `0x20` Responder) |
| 6 | 1 byte | Battery Pct | Persentase baterai (`0–100`, `0xFF` jika tidak diketahui) |
| 7–8 | 2 byte | Pending Count | Jumlah pesan tertunda di outbox |
| 9 | 1 byte | Default TTL | TTL standar node (`5`) |
| 10 | 1 byte | GATT Peer Count | Jumlah peer terhubung GATT (`0`) |
| 11–14 | 4 byte | Node Seq | Sequence number node (monotonic) |
| 15–26 | 12 byte | Padding | Zero padding (`0x00`) |

### 2.2 Struct Frame PESAN / MSG (27 Byte)
| Offset | Ukuran | Field | Deskripsi |
|---|---|---|---|
| 0 | 1 byte | Version | Protocol Version (`0x01`) |
| 1 | 1 byte | Type | FrameType (`0x02` = MSG) |
| 2–4 | 3 byte | Message Seq | Sequence number pesan 24-bit |
| 5–7 | 3 byte | Origin Node ID | Node ID pengirim asli 24-bit |
| 8–10 | 3 byte | Destination ID | Node ID tujuan (`0xFFFFFF` = BROADCAST) |
| 11 | 1 byte | TTL | Time-to-Live (awalnya 10 untuk SOS) |
| 12 | 1 byte | Hop Count | Jumlah lompatan yang telah dilalui |
| 13 | 1 byte | Flags | Bitmask (`SOS`, `SOS_PAYLOAD`, `SOS_LOC`, `IS_ACK`, `REPLAY`) |
| 14 | 1 byte | Frag Index | Indeks fragmen (`0 .. fragCount-1`) |
| 15 | 1 byte | Frag Count | Total jumlah fragmen |
| 16 | 1 byte | Total Payload Len | Total panjang payload asli dalam byte |
| 17 | 1 byte | Chunk Len | Panjang payload chunk pada frame ini ($\le 9$ byte) |
| 18–26 | 9 byte | Payload Chunk | Potongan data biner payload |

---

## 3. Mekanisme Store-and-Forward & Retransmisi SOS

### 3.1 Retransmisi Origin (Origin Re-Advertisement)
- Node pengirim SOS meretransmisi paket SOS miliknya secara periodik dengan **backoff eksponensial** (dimulai dari interval 3 detik hingga maksimum 30 detik) selama jendela waktu retransmisi (15 menit).
- Retransmisi origin berhenti otomatis ketika:
  1. Paket `ACK` dari responder diterima di Origin.
  2. Paket kedaluwarsa secara waktu.
  3. Pengguna menekan tombol **"Batalkan SOS"** di UI aplikasi.

### 3.2 Relay Carrying & Trickle Suppression
- Simpul perantara (Relay Node) menyimpan pesan SOS yang pernah diteruskan dalam Room DB dengan status `CARRYING`.
- **Pemicu Re-broadcast Relay**:
  1. Terdeteksi tetangga baru lewat Beacon BLE (`onNewPeerDiscovered()`).
  2. Secara periodik pada *duty cycle* rendah (tiap 60 detik).
- **Trickle Suppression**:
  - Sebelum memancarkan ulang, relay menunda transmisi dengan acak *jitter* ($200–1.000$ ms).
  - Jika selama jeda *jitter* terdengar $\ge k$ ($k = 2$) salinan duplikat dari tetangga lain, retransmisi dipadamkan (*suppressed*).
  - Jumlah pengulangan carry dibatasi maksimum $3\times$ per pesan.

### 3.3 Propagasi ACK & TTL ACK Fixed
- Begitu Responder selesai merakit `SOS_LOC` & `SOS_DETAIL`, Responder membentuk frame `ACK` dengan `ttl = MeshConfig.ACK_TTL` ($10$) dan `hopCount = 0`.
- Frame `ACK` merambat balik secara multi-hop (hingga 6+ hop) kembali ke Origin.
- `DuplicateGuard` melakukan deduplikasi khusus ACK (`ACK_FRAG_INDEX = 0x1000`) agar ACK tidak diteruskan berulang kali oleh relay yang sama.

---

## 4. Batasan dan Model Ancaman (Limitations and Threat Model)

1. **SOS/ACK Bisa Dipalsukan (Tanpa Autentikasi & Anti-Spoofing)**:
   - Frame dikirim tanpa enkripsi kriptografi atau tanda tangan digital (*digital signature*).
   - Node jahat dapat memalsukan frame SOS/ACK untuk membingungkan jaringan.

2. **Lokasi Disiarkan Tanpa Enkripsi (Unencrypted Plaintext Broadcast)**:
   - Koordinat GPS dan teks catatan disiarkan dalam format terbuka di udara BLE agar seluruh perangkat penolong dapat membacanya secara langsung tanpa hambatan tukar kunci.

3. **Tidak Ada Verifikasi Identitas Pemilik HP (Unauthenticated Node Identity)**:
   - Node ID 24-bit dialokasikan secara acak tanpa terikat pada identitas fisik atau sertifikat PKI/NTP.
