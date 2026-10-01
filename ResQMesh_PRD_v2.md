# PRD — ResQMesh

**Product Requirements Document**  
**Versi:** 2.0  
**Status:** Prototype / KTI  
**Platform:** Android  
**Target prototype:** 3–5 smartphone  
**Teknologi utama:** Kotlin, BLE, Room Database, Android Location Services

---

## 1. Ringkasan Produk

**ResQMesh** adalah sistem **offline emergency SOS mesh** yang memungkinkan pengguna mengirim sinyal darurat berisi **lokasi, identitas, waktu, dan informasi kondisi**, kemudian menyebarkannya melalui perangkat lain yang berperan sebagai **relay node**.

Konsep utamanya bukan sekadar mengirim pesan antar-pengguna, tetapi **mendistribusikan informasi SOS dari perangkat korban menuju perangkat lain yang masih dapat dijangkau**, hingga SOS dapat diterima oleh perangkat yang berperan sebagai responder/gateway/posko.

Contoh:

```text
📱 NODE-A
Korban
SOS + Lokasi
      │
      ▼
📱 NODE-B
Relay
      │
      ▼
📱 NODE-C
Relay
      │
      ▼
📱 NODE-D
Responder / Gateway
```

Sinyal SOS dapat berpindah secara multi-hop:

```text
NODE-A → NODE-B → NODE-C → NODE-D
```

Tanpa bergantung pada:

```text
❌ Internet
❌ Server cloud
❌ Jaringan seluler
```

Selama perangkat masih dapat berkomunikasi secara device-to-device dan informasi dapat diteruskan melalui node yang tersedia.

### Prinsip utama

> **One SOS, many relays, one source of truth.**

Satu SOS mempertahankan identitas pengirim dan `message_id` yang sama selama proses relay. Relay tidak membuat laporan baru; relay meneruskan paket SOS yang sudah ada.

---

## 2. Problem Statement

Dalam kondisi bencana seperti:

- gempa bumi,
- banjir,
- longsor,
- kerusakan infrastruktur,
- pemadaman jaringan,
- area dengan cakupan sinyal terbatas,
- atau kondisi darurat di lokasi terpencil,

korban dapat kehilangan akses terhadap komunikasi berbasis internet maupun jaringan seluler.

Masalah utama yang ingin diselesaikan ResQMesh:

> **Bagaimana seseorang tetap dapat mengirimkan informasi darurat dan koordinat lokasinya ketika jaringan internet dan jaringan seluler tidak tersedia, tetapi masih terdapat perangkat lain di sekitar yang dapat membantu meneruskan informasi tersebut?**

Masalah kedua adalah bahwa dalam kondisi darurat, pesan "saya butuh bantuan" saja mungkin belum cukup. Sistem perlu membawa informasi yang membantu responder mengenali:

```text
Siapa?
Dimana?
Kapan?
Kondisinya bagaimana?
Apakah SOS sudah diteruskan?
```

Karena itu, ResQMesh menggunakan **SOS packet terstruktur**.

---

## 3. Tujuan Produk

### Tujuan utama

Membangun prototype yang membuktikan bahwa:

> **Smartphone dapat digunakan sebagai node komunikasi darurat untuk menyebarkan SOS berisi koordinat lokasi dan informasi korban melalui beberapa perangkat secara multi-hop tanpa koneksi internet.**

### Tujuan khusus

ResQMesh harus mampu:

1. Membuat identitas unik setiap perangkat.
2. Menemukan node ResQMesh di sekitar.
3. Mengambil koordinat lokasi pengguna saat SOS dibuat.
4. Membentuk SOS packet berisi informasi penting.
5. Mengirim SOS ke perangkat di sekitar.
6. Meneruskan SOS melalui node perantara.
7. Mencegah duplikasi SOS menggunakan `message_id`.
8. Membatasi penyebaran menggunakan TTL.
9. Menampilkan jumlah hop dan status pengiriman.
10. Mengirim atau menyebarkan ACK ketika SOS mencapai responder/gateway.
11. Menyimpan riwayat SOS secara lokal.
12. Menyediakan store-and-forward sebagai pengembangan untuk kondisi node yang tidak selalu tersedia.

---

## 4. Fokus Produk

Pada versi baru ini, ResQMesh **bukan aplikasi chat umum**.

Fokus produk:

```text
                 RESQMESH

            🚨 EMERGENCY SOS
                    │
        ┌───────────┴───────────┐
        │                       │
     LOCATION              VICTIM INFO
        │                       │
        └───────────┬───────────┘
                    │
                 SOS PACKET
                    │
                    ▼
              MULTI-HOP RELAY
                    │
                    ▼
             RESPONDER/GATEWAY
```

Fungsi chat teks umum dapat tetap digunakan sebagai fitur pendukung, tetapi **bukan fokus utama MVP**.

---

## 5. Non-Goals

Untuk prototype KTI, ResQMesh belum ditujukan untuk:

- menggantikan jaringan seluler;
- menggantikan sistem komunikasi SAR profesional;
- melakukan panggilan suara;
- melakukan video call;
- mengirim file besar;
- menyediakan internet;
- melakukan GPS tracking kontinu seperti aplikasi pelacak;
- menjamin komunikasi pada jarak yang tidak didukung teknologi device-to-device;
- membangun jaringan dengan ratusan atau ribuan smartphone pada tahap awal;
- menyediakan enkripsi dan keamanan tingkat produksi.

### Batasan lokasi

ResQMesh pada MVP menggunakan **koordinat lokasi pada saat SOS dibuat**.

Artinya:

```text
User berada di lokasi X
        ↓
Tekan SOS
        ↓
GPS/Location Services mengambil koordinat
        ↓
Koordinat dimasukkan ke SOS
        ↓
SOS diteruskan
```

ResQMesh bukan sistem live tracking pada MVP.

---

## 6. Target User

### Primary User — Korban / Pengguna Darurat

Pengguna yang membutuhkan pertolongan dan kehilangan akses ke internet atau jaringan seluler.

Contoh:

```text
Korban bencana
    ↓
Tekan SOS
    ↓
Lokasi + informasi kondisi
    ↓
Disebarkan melalui node terdekat
```

### Secondary User — Relay Node

Perangkat pengguna lain yang berada dalam jangkauan dan bersedia membantu meneruskan SOS.

Relay tidak harus mengetahui seluruh isi percakapan. Fungsinya adalah:

```text
RECEIVE → VALIDATE → SAVE → FORWARD
```

### Tertiary User — Responder / Gateway

Perangkat yang digunakan oleh:

- relawan,
- petugas lapangan,
- posko,
- atau perangkat yang ditunjuk sebagai penerima SOS.

Perangkat ini menjadi tujuan penting dari penyebaran SOS.

---

## 7. Konsep Operasi Utama

### Scenario 1 — Direct SOS

Korban dan responder berada cukup dekat.

```text
📱 A ───────── 📱 D

Korban          Responder
```

A membuat SOS:

```text
SOS-001
Lokasi: -7.xxxxxx, 112.xxxxxx
Status: BUTUH BANTUAN
```

D menerima langsung.

---

### Scenario 2 — Multi-Hop SOS

Korban tidak dapat menjangkau responder secara langsung.

```text
📱 A ─── 📱 B ─── 📱 C ─── 📱 D

Korban   Relay    Relay    Responder
```

Alurnya:

```text
A → B → C → D
```

B dan C tidak mengubah sumber SOS.

Mereka hanya meneruskan:

```text
SOS-001
Sender = NODE-A
```

---

### Scenario 3 — SOS Dissemination

Jika tujuan belum diketahui secara langsung, SOS dapat disebarkan ke node sekitar.

```text
             B
            ↗
A ──────────┼────────── C
Korban      │
            ↘
             D
```

Satu SOS dapat mencapai beberapa node.

`message_id` dan TTL mencegah paket yang sama menyebar tanpa kontrol.

---

### Scenario 4 — SOS + ACK

Setelah SOS mencapai perangkat responder:

```text
NODE-A → NODE-B → NODE-C → NODE-D
```

NODE-D dapat mengirim ACK:

```text
ACK-SOS-001
```

ACK diteruskan kembali:

```text
NODE-D → NODE-C → NODE-B → NODE-A
```

Pengirim dapat melihat:

```text
🚨 SOS RECEIVED

Status:
Diterima responder

Hop:
3

ACK:
Received
```

ACK menjadi indikator bahwa SOS bukan hanya keluar dari perangkat pengirim, tetapi telah mencapai responder.

---

### Scenario 5 — Node Failure

Contoh jaringan:

```text
          B
        /   \\
A ─────       ───── D
        \\   /
          C
```

Jika jalur B gagal:

```text
A → C → D
```

masih dapat digunakan.

Hal ini menjadi dasar pengujian **resilience**.

---

## 8. SOS Packet

### Struktur dasar

Setiap SOS memiliki struktur data seperti:

```json
{
  "message_id": "SOS-00125",
  "message_type": "SOS",
  "sender_id": "NODE-A83F",
  "sender_name": "Andi",
  "status": "BUTUH BANTUAN",
  "latitude": -7.445321,
  "longitude": 112.718542,
  "location_accuracy_m": 12,
  "timestamp": "2026-10-01T18:20:00",
  "battery_percent": 34,
  "ttl": 8,
  "hop_count": 0
}
```

### Informasi wajib

| Field | Fungsi |
|---|---|
| `message_id` | Identitas unik SOS |
| `message_type` | Menandai paket sebagai SOS |
| `sender_id` | Identitas perangkat pengirim |
| `latitude` | Koordinat lintang |
| `longitude` | Koordinat bujur |
| `timestamp` | Waktu SOS dibuat |
| `ttl` | Batas jumlah relay |
| `hop_count` | Jumlah hop yang telah dilalui |

### Informasi tambahan

| Field | Fungsi |
|---|---|
| `sender_name` | Nama pengguna |
| `status` | Kondisi darurat |
| `location_accuracy_m` | Perkiraan akurasi koordinat |
| `battery_percent` | Informasi baterai pengirim |
| `additional_info` | Pesan tambahan |
| `people_count` | Jumlah orang yang membutuhkan bantuan |

Informasi tambahan dapat diperluas tanpa mengubah konsep dasar relay.

---

## 9. Location Handling

### Tujuan

ResQMesh mengambil lokasi pengguna ketika SOS dibuat.

Alur:

```text
User menekan SOS
        ↓
Location Manager
        ↓
Ambil lokasi terbaru
        ↓
Dapatkan:
Latitude
Longitude
Accuracy
Timestamp
        ↓
Masukkan ke SOS Packet
```

### Contoh tampilan

```text
Current Location

Latitude:
-7.445321

Longitude:
112.718542

Accuracy:
12 m

[USE THIS LOCATION]
```

### Catatan desain

Koordinat perlu disimpan sebagai bagian dari SOS packet sehingga relay tidak perlu memiliki lokasi korban secara lokal untuk meneruskannya.

Informasi lokasi merupakan **snapshot lokasi ketika SOS dibuat**, bukan tracking real-time pada MVP.

---

## 10. Fitur Utama MVP

| Fitur | Prioritas | Status Konsep |
|---|---|---|
| Node Identity | P0 | Wajib |
| Device Discovery | P0 | Wajib |
| Location Capture | P0 | Wajib |
| SOS Creation | P0 | Wajib |
| SOS Packet | P0 | Wajib |
| Multi-Hop Relay | P0 | Wajib |
| Broadcast/Dissemination | P0 | Wajib |
| Duplicate Detection | P0 | Wajib |
| TTL | P0 | Wajib |
| Hop Count | P0 | Wajib |
| SOS Status | P0 | Wajib |
| ACK | P1 | Penting |
| Local SOS History | P1 | Penting |
| Store-and-Forward | P1 | Pengembangan |
| Battery information | P1 | Pengembangan |
| Chat umum | P2 | Bukan fokus MVP |
| Live location tracking | P2 | Di luar MVP |

**P0 = harus ada untuk demonstrasi inti.**

---

## 11. Feature Requirements

### F1 — Node Identity

Setiap perangkat mempunyai identitas unik.

Contoh:

```text
NODE-A83F
NODE-B19C
NODE-C72D
NODE-D11A
```

Requirement:

- ID dibuat dan disimpan secara lokal.
- ID digunakan pada setiap paket.
- ID tidak boleh berubah selama sesi prototype.

---

### F2 — Device Discovery

Aplikasi mencari perangkat ResQMesh di sekitar menggunakan BLE.

Contoh:

```text
Nearby ResQMesh Nodes

🟢 NODE-B19C
Signal: Strong

🟢 NODE-C72D
Signal: Medium

🟡 NODE-D11A
Signal: Weak
```

Requirement:

- scan node sekitar;
- mengenali Node ID;
- mengetahui node yang tersedia;
- memperbarui status node.

---

### F3 — Location Capture

Saat SOS dibuat:

1. Aplikasi meminta/mengecek akses lokasi.
2. Aplikasi memperoleh koordinat.
3. Aplikasi memperoleh tingkat akurasi jika tersedia.
4. Koordinat dimasukkan ke SOS packet.
5. Paket siap disebarkan.

---

### F4 — SOS Creation

User memilih:

```text
🚨 SEND SOS
```

Aplikasi dapat menampilkan konfirmasi:

```text
SEND SOS?

Location:
-7.445321, 112.718542

Status:
BUTUH BANTUAN

[ CANCEL ] [ SEND SOS ]
```

Setelah dikirim, aplikasi membuat:

```text
SOS-00125
```

---

### F5 — SOS Relay

Setiap node yang menerima SOS menjalankan:

```text
RECEIVE
   ↓
CHECK MESSAGE ID
   ↓
ALREADY KNOWN?
 ┌─┴─────────┐
YES          NO
 │            │
DROP       SAVE
              │
              ▼
        IS RESPONDER?
          │       │
         YES      NO
          │        │
       HANDLE    TTL > 0?
                    │
                ┌───┴───┐
               YES      NO
                │        │
             FORWARD    DROP
```

---

### F6 — Duplicate Detection

Jika suatu node sudah pernah menerima:

```text
SOS-00125
```

maka penerimaan berikutnya tidak diproses ulang.

Contoh:

```text
B ───→ C
 \\    ↗
  → D
```

Jika C menerima SOS-00125 dari B dan D:

```text
First:
PROCESS + STORE

Second:
IGNORE
```

Hal ini mencegah pesan berulang dalam jaringan yang memiliki banyak jalur.

---

### F7 — TTL

TTL menentukan batas penyebaran SOS.

Contoh:

```text
TTL = 8
```

Relay:

```text
A → B
8 → 7

B → C
7 → 6

C → D
6 → 5
```

Jika:

```text
TTL = 0
```

node tidak meneruskan paket tersebut.

---

### F8 — Hop Count

Setiap relay meningkatkan:

```text
hop_count += 1
```

Contoh:

```text
A → B → C → D

A = 0
B = 1
C = 2
D = 3
```

Informasi hop count ditampilkan untuk kebutuhan demonstrasi dan pengukuran KTI.

---

### F9 — SOS Status

Pengirim dapat melihat status SOS:

```text
SOS-00125

✓ Created
✓ Relayed
✓ Received by 2 nodes
✓ ACK Received

Hop Count:
3
```

Status minimal:

```text
CREATED
RELAYED
DELIVERED
EXPIRED
```

Implementasi dapat menggunakan enum internal yang konsisten.

---

### F10 — ACK

Responder dapat membuat acknowledgement:

```text
ACK-SOS-00125
```

ACK mengikuti jalur relay menuju pengirim selama jalur tersedia.

Tujuan:

> Memberikan indikator bahwa SOS telah mencapai perangkat responder.

ACK bukan pengganti konfirmasi dari petugas lapangan; pada prototype ACK berarti paket telah diterima oleh perangkat tujuan/responder yang dikonfigurasi.

---

### F11 — Local SOS History

Riwayat disimpan pada perangkat.

Contoh:

```text
SOS History

🚨 SOS-00125
18:20
Delivered
3 hops

🚨 SOS-00120
17:52
Expired
5 hops
```

---

### F12 — Store-and-Forward

Fitur P1.

Jika node tujuan belum tersedia:

```text
A ─── B       D
```

B dapat menyimpan SOS secara lokal:

```text
SOS-00125
Status: PENDING
```

Ketika node/responder muncul:

```text
A ─── B ─── C ─── D
```

B atau node lain dapat meneruskan SOS.

Store-and-forward diposisikan sebagai pengembangan setelah relay dasar berhasil.

---

## 12. UI Structure

### Screen 1 — Home

Fokus utama adalah SOS.

```text
┌────────────────────────────┐
│          ResQMesh          │
│      OFFLINE SOS MESH      │
├────────────────────────────┤
│ Node ID                    │
│ NODE-A83F                  │
│                            │
│ Nearby Nodes: 3            │
│ Network: Available         │
│                            │
│       ┌────────────┐       │
│       │   🚨 SOS   │       │
│       └────────────┘       │
│                            │
│ [Nearby Nodes]             │
│ [SOS History]              │
└────────────────────────────┘
```

---

### Screen 2 — Create SOS

```text
┌────────────────────────────┐
│       CREATE SOS            │
├────────────────────────────┤
│ Name                       │
│ Andi                       │
│                            │
│ Status                     │
│ BUTUH BANTUAN              │
│                            │
│ People                     │
│ 1                          │
│                            │
│ Additional Information     │
│ Terjebak di lantai 2       │
│                            │
│ Location                   │
│ -7.445321                  │
│ 112.718542                 │
│ Accuracy: 12 m             │
│                            │
│       [ SEND SOS ]         │
└────────────────────────────┘
```

---

### Screen 3 — SOS Active

```text
┌────────────────────────────┐
│        🚨 SOS ACTIVE       │
├────────────────────────────┤
│ SOS ID                     │
│ SOS-00125                  │
│                            │
│ Sender                     │
│ NODE-A83F                  │
│                            │
│ Location                   │
│ -7.445321, 112.718542      │
│                            │
│ Status                     │
│ RELAYING                   │
│                            │
│ Hops                       │
│ 2                          │
│                            │
│ Nearby Nodes               │
│ 3                          │
│                            │
│ ACK                         │
│ Waiting...                 │
└────────────────────────────┘
```

---

### Screen 4 — Received SOS

Responder melihat:

```text
┌────────────────────────────┐
│      🚨 EMERGENCY SOS      │
├────────────────────────────┤
│ From                       │
│ Andi                       │
│ NODE-A83F                  │
│                            │
│ Status                     │
│ BUTUH BANTUAN              │
│                            │
│ Location                   │
│ -7.445321                  │
│ 112.718542                 │
│ Accuracy: 12 m             │
│                            │
│ People                     │
│ 3                          │
│                            │
│ Info                       │
│ Terjebak di lantai 2       │
│                            │
│ Hops: 3                    │
│                            │
│ [ ACKNOWLEDGE ]            │
└────────────────────────────┘
```

---

### Screen 5 — SOS History

```text
SOS History

🚨 SOS-00125
Andi
Delivered
3 hops

🚨 SOS-00120
Unknown
Pending

🚨 SOS-00112
Delivered
2 hops
```

---

## 13. Arsitektur Sistem

Arsitektur baru menambahkan komponen lokasi dan SOS:

```text
┌─────────────────────────────────┐
│             UI Layer            │
│ Home / Create SOS / Active SOS  │
│ Received SOS / History          │
└───────────────┬─────────────────┘
                │
┌───────────────▼─────────────────┐
│        Emergency Manager        │
│ SOS creation                    │
│ SOS status                      │
│ ACK handling                    │
└───────────────┬─────────────────┘
                │
       ┌────────┼─────────┐
       ▼        ▼         ▼
┌──────────┐ ┌─────────┐ ┌────────────┐
│ Location │ │  Mesh   │ │   Local    │
│ Manager  │ │ Manager │ │  Storage   │
└──────────┘ └────┬────┘ └─────┬──────┘
                  │             │
           ┌──────┴──────┐      │
           ▼             ▼      │
     ┌──────────┐ ┌────────────┐
     │ BLE      │ │ Message    │
     │ Manager  │ │ Router     │
     └──────────┘ └─────┬──────┘
                         │
                    ┌────▼────┐
                    │ Room DB │
                    └─────────┘
```

### Komponen utama

**Emergency Manager**

Mengatur pembuatan, status, dan pengolahan SOS.

**Location Manager**

Mengambil koordinat dan akurasi lokasi saat SOS dibuat.

**Mesh Manager**

Mengatur node, koneksi, dan relay.

**Message Router**

Menentukan apakah paket perlu diproses, diteruskan, atau dihentikan.

**BLE Manager**

Menangani komunikasi device-to-device pada prototype.

**Room DB**

Menyimpan node, SOS packet, status, dan riwayat secara lokal.

---

## 14. Teknologi

| Komponen | Teknologi |
|---|---|
| Platform | Android |
| Bahasa | Kotlin |
| IDE | Android Studio |
| Device communication | BLE |
| Location | Android Location Services |
| Database | Room |
| UI | Jetpack Compose |
| Architecture | MVVM |
| Routing | Custom application-layer routing |
| Message format | JSON / binary serialization |

### Catatan implementasi

BLE pada prototype digunakan sebagai **media komunikasi antarperangkat**, sedangkan logika multi-hop dilakukan pada **application layer**.

ResQMesh tidak mengandalkan mekanisme BLE Mesh standar. Setiap smartphone yang berpartisipasi menjalankan aplikasi ResQMesh dan secara aktif melakukan fungsi relay sesuai logika aplikasi.

---

## 15. Data Model

### Node

```text
Node
├── nodeId
├── name
├── lastSeen
├── signalStrength
├── status
└── role
```

Role dapat berupa:

```text
USER
RELAY
RESPONDER
GATEWAY
```

Untuk prototype, satu perangkat dapat menjalankan lebih dari satu peran.

---

### SOSMessage

```text
SOSMessage
├── messageId
├── messageType
├── senderId
├── senderName
├── status
├── latitude
├── longitude
├── locationAccuracy
├── timestamp
├── ttl
├── hopCount
├── peopleCount
├── batteryPercent
├── additionalInfo
└── deliveryStatus
```

---

### SOS Ack

```text
SOSAck
├── ackId
├── sosMessageId
├── responderId
├── timestamp
└── hopCount
```

---

## 16. SOS Routing Logic

Pseudocode:

```text
ON RECEIVE SOS:

1. Read message_id

2. Check local database

3. IF message_id already exists:
       DROP

4. ELSE:
       SAVE SOS

5. IF current node is responder/gateway:
       MARK AS DELIVERED
       CREATE ACK
       STOP forwarding

6. ELSE IF TTL > 0:
       TTL = TTL - 1
       hop_count = hop_count + 1
       FORWARD to eligible nearby nodes

7. ELSE:
       MARK AS EXPIRED
       STOP
```

### Prinsip forwarding

Node tidak perlu meneruskan SOS secara sembarang tanpa batas.

Forwarding harus mempertimbangkan:

```text
- TTL
- duplicate detection
- node availability
- status koneksi
```

Algoritma pemilihan relay dapat disempurnakan pada tahap pengembangan berikutnya.

---

## 17. Strategi Penyebaran SOS

Untuk MVP, SOS diperlakukan sebagai **priority emergency packet**.

Model dasar:

```text
Korban
  ↓
Node sekitar
  ↓
Node sekitar
  ↓
Responder/Gateway
```

Paket dapat diteruskan ke lebih dari satu node untuk meningkatkan peluang mencapai responder, tetapi harus dikontrol oleh:

```text
message_id
TTL
duplicate detection
```

### Tujuan

Mencapai kompromi antara:

```text
Delivery Probability
        vs
Network Overhead
```

Hal tersebut dapat menjadi bagian dari analisis eksperimen KTI.

---

## 18. Success Metrics

### M1 — SOS Delivery Rate

Mengukur persentase SOS yang berhasil mencapai responder.

```text
Delivery Rate =
SOS diterima responder
---------------------
SOS yang dibuat
× 100%
```

---

### M2 — Location Integrity

Mengukur apakah informasi koordinat yang diterima responder sesuai dengan koordinat yang dibuat oleh pengirim.

Yang dibandingkan:

```text
Original:
Latitude
Longitude

vs

Received:
Latitude
Longitude
```

Koordinat seharusnya tetap menjadi data milik pengirim dan tidak berubah selama proses relay.

---

### M3 — End-to-End Latency

Mengukur waktu:

```text
SOS created
      ↓
SOS received by responder
```

---

### M4 — Hop Count

Mencatat jumlah relay:

```text
A → B
1 hop

A → B → C
2 hops

A → B → C → D
3 hops
```

---

### M5 — Duplicate Rate

Target:

> Tidak ada duplikasi SOS yang ditampilkan sebagai event baru pada node tujuan untuk `message_id` yang sama.

---

### M6 — TTL Enforcement

Target:

> Tidak ada forwarding setelah TTL mencapai 0.

---

### M7 — Resilience

Menguji apakah SOS masih dapat sampai ketika salah satu relay gagal, selama masih tersedia jalur alternatif.

---

## 19. Skenario Pengujian Utama

| Test | Topologi | Tujuan |
|---|---|---|
| T1 | A → D | Direct SOS |
| T2 | A → B → D | 1 relay |
| T3 | A → B → C → D | Multi-hop |
| T4 | A → B/C → D | Alternate path |
| T5 | A → B → C + duplicate | Duplicate detection |
| T6 | A → B → C dengan TTL kecil | TTL enforcement |
| T7 | A → B → C → D + ACK | Acknowledgement |
| T8 | Node failure | Resilience |

### T1 — Direct SOS

```text
📱 A ───────── 📱 D

Internet: OFF

A:
SEND SOS

D:
RECEIVE SOS
```

---

### T3 — Multi-Hop

```text
📱 A ─── 📱 B ─── 📱 C ─── 📱 D

A:
🚨 SOS
Location:
-7.445321, 112.718542

Expected:
D receives exact SOS data
```

---

### T5 — Duplicate

```text
       B
      / \\
     /   \\
    A     C
     \\   /
      \\ /
       D
```

D dapat menerima SOS yang sama melalui lebih dari satu jalur.

Expected:

```text
SOS-00125
→ tampil sebagai satu SOS
→ tidak menjadi dua SOS berbeda
```

---

### T6 — TTL

```text
TTL = 2

A → B
TTL 2 → 1

B → C
TTL 1 → 0

C → D
STOP
```

Expected:

```text
D tidak menerima SOS melalui forwarding tersebut.
```

---

### T7 — ACK

```text
A → B → C → D

D:
ACK

D → C → B → A
```

Expected:

```text
A:
ACK RECEIVED
```

---

### T8 — Node Failure

```text
        B
      /   \\
A ───       ─── D
      \\   /
        C
```

Matikan B:

```text
A → C → D
```

Expected:

```text
SOS tetap dapat diterima jika jalur C tersedia.
```

---

## 20. Roadmap Development

### Sprint 1 — Basic Application

- Android project
- Home screen
- Node ID
- SOS UI dasar

### Sprint 2 — Location

- Location permission
- Location capture
- Latitude/longitude
- Accuracy
- Location preview

### Sprint 3 — BLE

- BLE scanning
- Device discovery
- Connection
- Nearby nodes

### Sprint 4 — SOS Messaging

- SOS packet
- Send/receive
- Local storage
- Message ID

### Sprint 5 — Mesh Relay

- Forwarding
- TTL
- Duplicate detection
- Hop count
- Dissemination

### Sprint 6 — Responder & ACK

- Responder mode
- Received SOS screen
- ACK
- Delivery status

### Sprint 7 — Research

- Delivery rate
- Latency
- Hop count
- Location integrity
- Duplicate testing
- Node failure
- Comparison of relay conditions

---

## 21. MVP Final

MVP ResQMesh difokuskan pada satu alur utama:

```text
                RESQMESH
                    │
              USER TEKAN SOS
                    │
        ┌───────────┴───────────┐
        │                       │
      LOCATION              VICTIM INFO
        │                       │
        └───────────┬───────────┘
                    │
                SOS PACKET
                    │
                    ▼
              DEVICE DISCOVERY
                    │
                    ▼
              MULTI-HOP RELAY
                    │
          ┌─────────┴─────────┐
          │                   │
       TTL / ID             HOP COUNT
          │                   │
          └─────────┬─────────┘
                    ▼
             RESPONDER/GATEWAY
                    │
                    ▼
                   ACK
                    │
                    ▼
                 KORBAN
```

### Target demonstrasi KTI

```text
Internet: OFF

📱 A                 📱 D
Korban              Responder
   │
   │ SOS
   ▼
📱 B
Relay
   │
   ▼
📱 C
Relay
   │
   ▼
📱 D

SOS:
"BUTUH BANTUAN"

Location:
-7.445321, 112.718542

Result:
✓ SOS received
✓ Location preserved
✓ 3 hops
✓ ACK returned
```

---

## 22. Hubungan dengan Cisco Packet Tracer

Cisco Packet Tracer tetap digunakan sebagai **simulasi konsep ketahanan jaringan**, bukan sebagai simulasi smartphone BLE.

Topologi:

```text
             R2
            /  \\
           /    \\
        R1        R4
           \\    /
            \\  /
             R3
```

PC:

```text
PC1 ── R1                 R4 ── PC4
```

Dua jalur utama:

```text
PC1 → R1 → R2 → R4 → PC4
PC1 → R1 → R3 → R4 → PC4
```

### Packet Tracer digunakan untuk menguji

- routing,
- redundancy,
- alternate path,
- node/link failure,
- dan reconvergence.

### Prototype Android digunakan untuk menguji

```text
Phone A → Phone B → Phone C → Phone D
```

dengan:

- device discovery,
- SOS packet,
- location data,
- multi-hop relay,
- TTL,
- duplicate detection,
- hop count,
- ACK,
- dan node failure.

Dengan demikian:

> **Cisco Packet Tracer = simulasi ketahanan dan jalur jaringan pada network layer.**

> **Android ResQMesh = prototype penyebaran SOS melalui device-to-device relay pada perangkat nyata.**

Keduanya mendukung ide yang sama dari sisi berbeda: **informasi tetap dapat menemukan jalur alternatif ketika jalur komunikasi utama tidak tersedia.**

---

## 23. Arah Penelitian KTI

Perubahan konsep ini memungkinkan fokus penelitian yang lebih spesifik:

> **Pengembangan sistem penyebaran SOS berbasis device-to-device multi-hop untuk mengirim informasi lokasi dan kondisi pengguna tanpa bergantung pada jaringan seluler atau internet.**

Contoh pertanyaan penelitian:

1. Apakah SOS dapat diteruskan secara multi-hop tanpa internet?
2. Bagaimana jumlah relay memengaruhi latency dan delivery rate?
3. Apakah duplicate detection dan TTL dapat mencegah penyebaran paket yang tidak terkendali?
4. Apakah jalur alternatif meningkatkan keberhasilan pengiriman ketika salah satu node gagal?
5. Apakah informasi koordinat tetap utuh setelah melalui beberapa relay?

### Variabel yang dapat diamati

```text
Independent:
- jumlah node
- jumlah hop
- kondisi node
- TTL
- jumlah jalur

Dependent:
- delivery rate
- latency
- hop count
- duplicate rate
- packet expiry
- keberhasilan ACK
```

---

## 24. Prinsip Desain Utama

ResQMesh harus selalu mengikuti prinsip:

### 1. SOS First

Fungsi utama adalah keadaan darurat, bukan chat.

### 2. Location Included

SOS membawa koordinat lokasi pengirim.

### 3. Source Preserved

Relay tidak mengubah identitas sumber SOS.

### 4. Duplicate Safe

Satu `message_id` diperlakukan sebagai satu SOS.

### 5. TTL Controlled

SOS memiliki batas penyebaran.

### 6. Offline by Design

MVP tidak membutuhkan internet untuk proses SOS relay.

### 7. Local Data

Informasi SOS dapat disimpan secara lokal pada perangkat.

### 8. Observable

Sistem harus menampilkan metrik seperti:

```text
Status
Hop count
Timestamp
Delivery
ACK
```
