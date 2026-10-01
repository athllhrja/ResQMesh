# PRD — ResQMesh

**Product Requirements Document**  
**Versi:** 1.0  
**Status:** Prototype / KTI  
**Platform:** Android  
**Target prototype:** 3–5 smartphone  
**Teknologi utama:** Kotlin, BLE, Room Database

---

## 1. Ringkasan Produk

**ResQMesh** adalah aplikasi komunikasi darurat berbasis **device-to-device communication** yang memungkinkan pengguna bertukar pesan ketika koneksi internet atau jaringan seluler tidak tersedia.

Berbeda dari aplikasi chat biasa, ResQMesh memungkinkan sebuah perangkat bertindak sebagai **relay node**.

Contoh:

```text
HP A ─── HP B ─── HP C
Sender   Relay    Receiver
```

Pesan:

```text
A → B → C
```

Bukan hanya:

```text
A ─────────────→ C
```

Perangkat yang berada di antara pengirim dan penerima dapat membantu meneruskan pesan.

---

## 2. Problem Statement

Dalam kondisi seperti:

- gempa bumi,
- banjir,
- longsor,
- pemadaman infrastruktur,
- kerusakan BTS,
- kondisi darurat di daerah terpencil,

komunikasi dapat terganggu ketika jaringan seluler atau internet tidak tersedia.

Masalah yang ingin diselesaikan ResQMesh:

> **Bagaimana pengguna tetap dapat mengirim pesan darurat ketika koneksi internet dan jaringan seluler tidak tersedia, selama masih terdapat perangkat lain yang dapat berfungsi sebagai relay?**

---

## 3. Tujuan Produk

### Tujuan utama

Membangun prototype yang membuktikan bahwa:

> **Smartphone dapat digunakan sebagai node komunikasi yang meneruskan pesan secara multi-hop tanpa bergantung pada internet.**

### Tujuan khusus

ResQMesh harus mampu:

1. Mengenali perangkat ResQMesh di sekitar.
2. Membuat identitas unik untuk setiap node.
3. Mengirim pesan ke node lain.
4. Meneruskan pesan melalui node perantara.
5. Mencegah pesan yang sama diteruskan berulang kali.
6. Membatasi jumlah hop menggunakan TTL.
7. Menyimpan pesan ketika penerima belum tersedia.
8. Menyediakan mode SOS untuk pesan darurat.
9. Menampilkan status node dan pesan.

---

## 4. Non-Goals

Untuk prototype pertama, ResQMesh **belum ditujukan untuk**:

- menggantikan jaringan seluler,
- melakukan panggilan suara,
- video call,
- mengirim file besar,
- komunikasi dengan internet,
- GPS tracking secara real-time,
- jaringan mesh dengan ratusan perangkat,
- enkripsi tingkat produksi.

Fokus KTI adalah:

> **Reliable emergency text communication through multi-hop device-to-device networking.**

---

## 5. Target User

### Primary User

Masyarakat yang berada dalam situasi darurat dan kehilangan akses internet/jaringan seluler.

Contoh:

> Korban bencana yang masih berada dalam jangkauan perangkat ResQMesh lain.

### Secondary User

Petugas atau relawan yang berada di lokasi bencana.

Contoh:

```text
Korban → Relawan → Posko
```

---

## 6. User Scenario Utama

### Scenario 1 — Direct Communication

A dan B berada berdekatan.

```text
A ───── B
```

A mengirim:

> "Saya berada di gedung sekolah."

B menerima pesan.

### Scenario 2 — Multi-Hop Communication

A tidak dapat berkomunikasi langsung dengan C, tetapi B berada di antara mereka.

```text
A ───── B ───── C
```

A:

> "Saya membutuhkan bantuan."

B:

> menerima → meneruskan

C:

> menerima pesan dari A.

### Scenario 3 — Emergency SOS

Pengguna menekan:

```text
🚨 SOS
```

Aplikasi membuat pesan:

```text
SOS
Node: NODE-A
Timestamp: 16:42
Message: BUTUH BANTUAN
```

Pesan kemudian diteruskan ke node lain.

---

## 7. MVP

MVP ResQMesh memiliki fitur berikut:

| Fitur | Prioritas |
|---|---|
| Node ID | P0 |
| Device Discovery | P0 |
| Direct Messaging | P0 |
| Multi-Hop Forwarding | P0 |
| Duplicate Detection | P0 |
| TTL | P0 |
| SOS | P0 |
| Message History | P1 |
| Store-and-Forward | P1 |
| Battery monitoring | P2 |
| Location | P2 |

**P0 = wajib untuk prototype.**

---

## 8. Feature Requirements

### F1 — Node Identity

Setiap perangkat memiliki ID unik.

Contoh:

```text
NODE-A83F
NODE-B19C
NODE-C72D
```

Saat aplikasi dibuka:

```text
My Node

NODE-A83F

Status
ONLINE
```

#### Requirement

- ID harus unik.
- ID disimpan secara lokal.
- ID digunakan dalam setiap pesan.

---

### F2 — Device Discovery

Aplikasi mencari perangkat ResQMesh di sekitar.

Contoh:

```text
Nearby Nodes

🟢 NODE-B19C
   Signal: Strong

🟢 NODE-C72D
   Signal: Medium

⚪ NODE-D83A
   Signal: Weak
```

Discovery menggunakan BLE.

#### Requirement

Aplikasi harus dapat:

- melakukan scanning,
- menemukan perangkat,
- mengetahui Node ID,
- mengetahui status koneksi,
- menampilkan perangkat yang tersedia.

---

### F3 — Direct Messaging

Pengguna dapat memilih node:

```text
Nearby Nodes

NODE-B19C

[Send Message]
```

Kemudian:

```text
┌──────────────────────────┐
│ Message                  │
│                          │
│ Saya aman                │
│                          │
│       [SEND]             │
└──────────────────────────┘
```

---

### F4 — Multi-Hop Forwarding

Ini adalah **core feature ResQMesh**.

Misalnya:

```text
NODE-A
   │
   ▼
NODE-B
   │
   ▼
NODE-C
```

A mengirim:

```text
MSG-001
```

B menerima.

B memeriksa:

```text
Apakah destination B?
```

Tidak.

Kemudian:

```text
Apakah B sudah pernah menerima MSG-001?
```

Belum.

Maka:

```text
SAVE
  ↓
FORWARD
```

Kemudian C menerima.

---

### F5 — Message Structure

Setiap pesan minimal mempunyai:

```text
Message ID
Sender ID
Destination ID
Payload
Timestamp
TTL
Hop Count
Status
```

Contoh:

```text
MSG-001

Sender:
NODE-A83F

Destination:
NODE-C72D

Message:
BUTUH BANTUAN

TTL:
5

Hop:
2

Status:
DELIVERED
```

---

### F6 — Duplicate Detection

Aplikasi harus mencegah pesan yang sama diproses berkali-kali.

Contoh:

```text
        B
       / \
      /   \
     A     D
      \   /
       \ /
        C
```

C mungkin mendapatkan:

```text
MSG-001 dari B
MSG-001 dari D
```

C hanya boleh memproses `MSG-001` satu kali.

#### Logic

```text
IF message_id exists
    DROP
ELSE
    SAVE
    PROCESS
```

---

### F7 — TTL

Setiap pesan mempunyai batas hop.

Contoh:

```text
TTL = 5
```

Setiap diteruskan:

```text
A → B
TTL 5 → 4

B → C
TTL 4 → 3
```

Jika:

```text
TTL = 0
```

pesan tidak diteruskan lagi.

Tujuannya mencegah pesan berputar tanpa batas.

---

### F8 — SOS

Ini fitur utama untuk demonstrasi KTI.

Home screen:

```text
┌─────────────────────────┐
│       ResQMesh          │
│                         │
│  🟢 Network Available   │
│                         │
│     ┌─────────────┐     │
│     │     SOS     │     │
│     └─────────────┘     │
│                         │
│ Nearby Nodes: 3         │
└─────────────────────────┘
```

Ketika SOS ditekan:

```text
┌─────────────────────────┐
│ SEND EMERGENCY?         │
│                         │
│ "I NEED HELP"           │
│                         │
│ [CANCEL]    [SEND SOS]  │
└─────────────────────────┘
```

Pesan disebarkan melalui node yang tersedia.

---

### F9 — Message History

Pengguna dapat melihat pesan:

```text
Messages

🚨 SOS
NODE-A83F
16:42
Delivered
2 hops

Saya membutuhkan bantuan.

────────────────

NODE-B19C
16:45
Received
1 hop

Saya berada di sekolah.
```

---

### F10 — Store and Forward

Fitur ini **P1**, sehingga dapat dibuat setelah multi-hop berhasil.

Contoh:

```text
A ─── B       C
```

A mengirim ke C.

C belum tersedia.

B menyimpan:

```text
MSG-001
STATUS = PENDING
```

Kemudian C muncul:

```text
A ─── B ─── C
```

B mengirim:

```text
MSG-001 → C
```

C menerima.

Status berubah:

```text
DELIVERED
```

---

## 9. UI Structure

Aplikasi memiliki 4 halaman utama.

### Screen 1 — Home

```text
ResQMesh

Node:
NODE-A83F

Status:
🟢 Active

Nearby:
3 Nodes

[ 🚨 SOS ]

[Nearby Nodes]
[Messages]
```

### Screen 2 — Nearby Nodes

```text
Nearby Nodes

NODE-B19C
Connected

NODE-C72D
Available

NODE-D11A
Available
```

### Screen 3 — Chat

```text
NODE-B19C

You:
Saya aman.

NODE-B19C:
Pesan diterima.

[ Type message... ]

[SEND]
```

### Screen 4 — Messages

```text
Message History

SOS
Delivered
2 hops

Saya aman
Delivered
1 hop

Need help
Pending
```

---

## 10. Arsitektur Sistem

```text
┌───────────────────────────────┐
│           UI Layer            │
│ Home / Nodes / Chat / History │
└───────────────┬───────────────┘
                │
┌───────────────▼───────────────┐
│         Mesh Manager          │
│ Node management               │
│ Connection management         │
└───────────────┬───────────────┘
                │
       ┌────────┴────────┐
       ▼                 ▼
┌─────────────┐   ┌─────────────┐
│ BLE Manager │   │MessageRouter│
└─────────────┘   └──────┬──────┘
                         │
                   ┌─────▼─────┐
                   │  Room DB  │
                   └───────────┘
```

---

## 11. Teknologi

| Komponen | Teknologi |
|---|---|
| Platform | Android |
| Bahasa | Kotlin |
| IDE | Android Studio |
| Device communication | BLE |
| Database | Room |
| UI | Jetpack Compose |
| Architecture | MVVM |
| Routing | Custom application-layer routing |
| Message format | JSON / binary serialization |

Untuk prototype KTI, kombinasi **Kotlin + Jetpack Compose + BLE + Room** sudah cukup.

---

## 12. Data Model

### Node

```text
Node
├── nodeId
├── name
├── lastSeen
├── signalStrength
└── status
```

### Message

```text
Message
├── messageId
├── senderId
├── destinationId
├── payload
├── timestamp
├── ttl
├── hopCount
└── status
```

---

## 13. Message Routing Logic

```text
Receive Message
       │
       ▼
Is Message ID already known?
       │
   ┌───┴───┐
  YES      NO
   │        │
   ▼        ▼
 DROP    Save message
            │
            ▼
       Is destination?
         │
      ┌──┴──┐
     YES    NO
      │      │
      ▼      ▼
    DONE   TTL > 0?
             │
          ┌──┴──┐
         YES    NO
          │      │
          ▼      ▼
       Forward   DROP
```

---

## 14. Success Metrics

### M1 — Direct Delivery

Target:

> ≥95% pesan berhasil diterima dalam kondisi pengujian normal.

Formula:

```text
Delivery Rate =
(jumlah pesan berhasil diterima / jumlah pesan dikirim) × 100%
```

### M2 — Multi-Hop

Contoh:

```text
A → B → C
```

Target:

> Pesan berhasil diterima oleh C tanpa internet.

### M3 — Node Failure

Jika salah satu jalur/node tidak tersedia, sistem masih dapat mengirim pesan melalui node lain apabila tersedia jalur alternatif.

### M4 — Duplicate

Satu `message_id` tidak boleh menghasilkan pesan yang sama berkali-kali pada node tujuan.

### M5 — TTL

Pesan tidak boleh diteruskan ketika TTL mencapai 0.

---

## 15. Skenario Pengujian Utama

| Test | Topologi | Tujuan |
|---|---|---|
| T1 | A → B | Direct message |
| T2 | A → B → C | Multi-hop |
| T3 | A → B → C → D | Extended multi-hop |
| T4 | A → B → C + node failure | Resilience |
| T5 | A → B → A | Duplicate/TTL |

### T2 — Multi-Hop Test

```text
A ─── B ─── C

Internet: OFF

A mengirim:
"TEST RESQMESH"

Expected:
C menerima pesan
```

---

## 16. Roadmap Development

### Sprint 1 — Basic App

- Android project
- Home screen
- Node ID
- Basic UI

### Sprint 2 — BLE

- BLE scanning
- Device discovery
- Connection
- Node list

### Sprint 3 — Messaging

- Send
- Receive
- Message ID
- Message history

### Sprint 4 — Mesh

- Forwarding
- TTL
- Duplicate detection
- Hop count

### Sprint 5 — Emergency

- SOS
- Broadcast
- Store-and-forward

### Sprint 6 — Research

- Testing
- Latency measurement
- Delivery rate
- Hop count
- Failure testing

---

## 17. MVP Final

MVP ResQMesh difokuskan pada:

```text
              RESQMESH MVP

                   │
       ┌───────────┴───────────┐
       │                       │
  Device Discovery        Messaging
       │                       │
       │                ┌──────┴──────┐
       │                │             │
       │             Direct       Multi-hop
       │                              │
       │                         TTL + ID
       │                              │
       └──────────────┬───────────────┘
                      │
                     SOS
```

Target demonstrasi:

```text
📱 A ─── 📱 B ─── 📱 C

Internet: OFF

A:
"BUTUH BANTUAN"

       ↓

B:
RELAY

       ↓

C:
🚨 "BUTUH BANTUAN"
```

Keberhasilan prototype ditunjukkan melalui pengukuran:

- delivery rate,
- latency,
- hop count,
- duplicate message,
- dan respons terhadap kegagalan node.

---

## 18. Hubungan dengan Simulasi Cisco Packet Tracer

Cisco Packet Tracer digunakan sebagai tahap simulasi konsep jaringan:

```text
R1 → R2 → R4
 \          /
  → R3 →──
```

Simulasi membuktikan:

- routing,
- redundancy,
- node failure,
- dan jalur alternatif.

Prototype Android kemudian mengimplementasikan konsep tersebut pada perangkat nyata:

```text
Phone A → Phone B → Phone C
```

Dengan:

- device discovery,
- multi-hop messaging,
- forwarding,
- duplicate detection,
- TTL,
- dan SOS.

Dengan demikian:

> **Cisco Packet Tracer = simulasi arsitektur jaringan.**  
> **Android ResQMesh = prototype implementasi device-to-device communication.**
