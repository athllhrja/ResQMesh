# Technical Specification - ResQMesh

**Versi:** 2.0
**Status:** Draft implementasi - keputusan bertanda RANCANGAN belum final
**Sumber:** [PRD v3.0](ResQMesh_PRD_v3.md) — acuan spesifikasi target pengujian fisik (*field-ready baseline*). `ResQMesh_PRD_v2.md` dan `ResQMesh_PRD.md` adalah dokumen historis sebelumnya.
**Platform:** Android (minSdk 26 / targetSdk 34)
**Bahasa:** Kotlin, Jetpack Compose, BLE, Room

---

## 0. Ringkasan Keputusan Arsitektur

PRD v2 menetapkan *"Custom application-layer routing"* tanpa menentukan transport. Keputusan berikut mengunci implementasi.

**Fokus produk (PRD §4).** ResQMesh bukan aplikasi chat. Fungsi utamanya adalah keadaan darurat: lokasi + kondisi korban + penyebaran ke responder. Chat teks tetap ada sebagai fitur pendukung P2 dan harus selalu berada di bawah SOS dalam hierarki navigasi.

Label di tabel keputusan menunjukkan kematangan keputusan desain, bukan cakupan tes:

| Status | Arti |
|---|---|
| (tanpa label) | Keputusan desain yang dipilih untuk prototype. Lihat pemetaan PRD dan gap untuk status implementasinya; ketiadaan label bukan bukti bahwa semua perilakunya sudah diuji. |
| **RANCANGAN** | Arah sudah dipilih, tetapi implementasinya belum ada atau belum tervalidasi di perangkat. Jangan dipakai sebagai alasan untuk menutup gap. |

Saat ini, keputusan yang masih RANCANGAN adalah **D1** (transport BLE), **D2** (batas frame), dan **D9** (role node). Beberapa bagian keputusan lain memiliki implementasi dan cakupan tes JVM, tetapi tes tersebut hanya membuktikan perilaku yang secara langsung diuji. Tes yang menggunakan DAO palsu tidak membuktikan persistensi Room setelah aplikasi dimulai ulang; integrasi Android seperti lokasi dan pembukaan peta, serta komunikasi BLE pada perangkat, juga belum terverifikasi oleh tes JVM.

### 0.1 Keputusan arsitektur

| # | Keputusan | Alasan |
|---|---|---|
| D1 | **RANCANGAN** - Two-tier transport: BLE Advertising (flood) + BLE GATT (link) | GATT hanya bisa di-*central* ke satu peer pada satu waktu dan butuh role; advertising bersifat broadcast alami sehingga cocok untuk flooding multi-hop tanpa pairing. Dua jalur ini menutupi kekurangan masing-masing. **Belum ada kode BLE sama sekali**; yang berjalan sekarang `NoOpMeshTransport` plus `NoOpPeerLinkRegistry`. Lihat bagian 4, 5.4, 5.5, dan 6. |
| D2 | **RANCANGAN** - Wire format biner custom, bukan JSON | Budget payload advertising hanya 31 byte. JSON membuang 40–60% budget. Format biner fixed-field menghasilkan 9 byte payload per frame. Codec dan tes-nya sudah ada, tapi panjang frame belum pernah diukur di perangkat nyata. |
| D3 | **`NodeId` = 3 byte (6 hex), `MessageId` komposit** | 16,7 juta node dan 16,7 juta pesan per node. `MessageId = (originNodeId << 24) \| seq` → unik global tanpa koordinator, dan self-describing untuk Collision Detection. |
| D4 | **Duplicate detection persisten di Room dengan TTL retensi** | Kalau `seen-set` hanya in-memory, restart aplikasi menyebabkan pesan lama di-forward ulang dan memicu storm. Persistensi + retensi berbasis waktu membatasi ukuran DB. |
| D5 | **SOS dipecah jadi dua pesan terantai, bukan satu payload ter-fragmentasi** | Koordinat tampil dalam sekitar satu detik, catatan bebas menyusul. Satu payload gabungan baru bisa dibaca setelah fragmen terakhir tiba. Lihat A14. |
| D6 | **`LocationManager`, bukan FusedLocationProviderClient** | Perangkat rescue tidak selalu lolos peninjauan Play Store, dan Place API beresolusi tinggi tidak tersedia di sana. |
| D7 | **Intent peta eksternal, tanpa SDK peta** | Menyeret pengguna ke Play Store saat sedang darurat memastikan tidak ada yang bisa menampilkan peta. |
| D8 | **Flood tanpa bias hop untuk SOS** | Node yang hanya terjangkau lewat jalur panjang justru yang paling perlu diberi tahu. |
| D9 | **RANCANGAN** - Role node adalah bitmask lokal, bukan field wire pesan | PRD §15 menyebut USER/RELAY/RESPONDER/GATEWAY dan mengizinkan satu perangkat memegang lebih dari satu peran. Konsekuensi yang paling penting: §16 langkah 5 deciding "apakah node ini responder?" adalah keputusan **lokal**, bukan informasi yang ikut travelling di dalam SOS. Jadi menambah role tidak mengubah satu byte pun format wire. |
| D10 | **ACK tetap memakai `messageId` pesan asli** | Originator mengenali ACK-nya tanpa field tambahan. Konsekuensinya: ACK tidak membawa `responderId`, jadi originator tidak tahu responder mana yang mengonfirmasi. Untuk MVP ini cukup — lihat gap G5. |

### 0.2 Pemetaan PRD v2 → keputusan

| Area PRD | Keputusan | Status implementasi |
|---|---|---|
| §7 Scenario 4 — SOS + ACK | D1, D10 | Sebagian: ACK sudah bisa kembali ke origin, tetapi `responderId` belum ada (gap G5). |
| §8 SOS Packet | D2, D5 | Sebagian: `timestamp` dan `sender_name` belum ada di wire (gap G1, G2). |
| §9 Location Handling | D6 | Terpenuhi: snapshot lokasi, bukan tracking, sesuai PRD §5. |
| §11 F5 — SOS Relay | D1, D8, D9 | Belum: role belum ada, jadi §16 langkah 5 belum berjalan (gap G3). |
| §11 F9 — SOS Status | — | Belum: status CREATED belum ada, "received by N nodes" belum ada (gap G4). |
| §12 Screen 3 — SOS Active | — | Belum: layar ini belum ada (gap G6). |
| §12 Screen 4 — ACKNOWLEDGE button | — | Belum: ACK 100% otomatis, belum ada jalur manual (gap G7). |
| §12 Screen 5 — SOS History | D5 | Sebagian: satu insiden masih tampil sebagai 2 baris pesan (gap G8). |
| §14 BLE notes | D1 | Belum: `NoOpMeshTransport` masih dipakai. |
| §16 SOS Routing Logic | D9 | Belum: langkah 5 saat ini hanya `destination == selfId`, tanpa role check (gap G3). |
| §18 Success Metrics | D8 | Sebagian: M2 Location Integrity bisa diukur, M1 dan M3 butuh data yang belum ada. |

### 0.3 Deviasi dari PRD v2 yang perlu dicatat di laporan

| PRD | Tech Spec | Alasan |
|---|---|---|
| `NODE-A83F` (4 hex) | `NODE-A83F2C` (6 hex) | 4 hex = 65.536 kombinasi, rawan tabrakan. Contoh di PRD bersifat ilustratif. |
| "Message format: JSON / binary serialization" | Binary; JSON hanya di boundary UI dan log | lihat D2 |
| TTL diasumsikan langsung | TTL decrement hanya saat *forward*, bukan saat *receive* | lihat §7.6 |
| Duplicate: `IF exists THEN DROP` | Duplikat di-drop saat *meneruskan*, tidak saat *menyelesaikan re-assembly* | lihat §7.6 |
| §16 langkah 5: "CREATE ACK" saat frame masuk | ACK dikirim **setelah** pengguna menekan ACKNOWLEDGE | Memastikan responder punya waktu membaca dan memahami kondisi korban sebelum mengonfirmasi. Untuk demo KTI, ACK yang otomatis bisa menyesatkan — timing ACK adalah metrik yang diukur. |
| §11 F12 — Store-and-Forward | baru ditandai `PENDING_FORWARD`, belum ada scheduler retry | lihat §13. |

### 0.4 Gap yang belum terutup

| # | Gap | Area | Dampak pada PRD | Prioritas |
|---|---|---|---|---|
| G1 | `timestamp` tidak ada di wire | §8, §18 M3 | Pengirim dan penerima tidak bisa tahu kapan SOS dibuat; pengukuran latency M3 tidak sahih | P0 |
| G2 | `sender_name` tidak ada | §8, §12 Screen 2/4 | Responder tidak bisa melihat nama korban, hanya `NODE-xxxx` | P0 |
| G3 | `role` node belum ada; §16 langkah 5 belum berjalan | §11 F5, §16 | Node responder/gateway tidak tahu harus berhenti meneruskan | P0 |
| G4 | "Received by N nodes" tidak dihitung; status CREATED belum ada | §11 F9, §18 | Status SOS di layar hanya satu nilai, tidak ada hitungan node | P0 |
| G5 | ACK tidak membawa `responderId` | §15 SOSAck, §11 F10 | Pengirim tidak tahu responder mana yang mengonfirmasi | P1 |
| G6 | Layar "SOS Active" belum ada | §12 Screen 3 | Penggirim tidak punya tampilan status real-time | P0 |
| G7 | Tombol ACKNOWLEDGE belum ada | §12 Screen 4 | ACK selalu otomatis, tidak bisa dikonfirmasi manual | P1 |
| G8 | Riwayat SOS belum per-insiden | §12 Screen 5 | Satu insiden SOS = 2 baris (LOC + DETAIL) di riwayat | P1 |

G1 dan G2 bisa ditutup tanpa mengubah panjang `SOS_LOC` (tetap 16 byte = 2 frame). `fixAgeSeconds` diperkecil dari u16 ke u8 sehingga satu byte bebas, dan byte `reserved` yang lama dipakai bersama byte bebas itu untuk field u16 `sentAgeMinutes` (menit sejak pesan dikirim). Nilai u16 = 65.535 menit = 45 hari, jadi field ini wraparound, bukan penanda waktu absolut. Nama korban tidak masuk ke paket SOS sama sekali, melainkan disiarkan di 12 byte spare pada tail beacon. Rincian layout ada di A14.3.

---

## 1. Struktur Project

### 1.1 Pohon direktori

```text
ResQMesh/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── res/
│       │   │   ├── values/strings.xml
│       │   │   ├── values/themes.xml
│       │   │   ├── xml/backup_rules.xml
│       │   │   ├── xml/data_extraction_rules.xml
│       │   │   └── mipmap-anydpi-v26/ic_launcher.xml
│       │   └── java/com/resqmesh/
│       │       ├── ResQMeshApp.kt
│       │       ├── core/
│       │       │   ├── MeshConfig.kt
│       │       │   └── TimeProvider.kt
│       │       ├── di/
│       │       │   └── AppGraph.kt
│       │       ├── domain/
│       │       │   ├── model/
│       │       │   │   ├── NodeId.kt
│       │       │   │   ├── Node.kt
│       │       │   │   ├── NodeStatusFlags.kt
│       │       │   │   ├── MessageId.kt
│       │       │   │   ├── Message.kt
│       │       │   │   ├── MessageStatus.kt
│       │       │   │   ├── MessageDirection.kt
│       │       │   │   ├── MessageFlags.kt
│       │       │   │   ├── MeshFrame.kt
│       │       │   │   ├── BeaconFrame.kt
│       │       │   └── MeshState.kt
│       │       ├── MeshRepository.kt
│       │       └── usecase/
│       │           ├── SendMessageUseCase.kt
│       │           ├── BroadcastSosUseCase.kt
│       │           └── PruneUseCase.kt
│       │       ├── data/
│       │       │   ├── db/
│       │       │   │   ├── ResQMeshDatabase.kt
│       │       │   │   ├── Converters.kt
│       │       │   │   ├── node/NodeDao.kt
│       │       │   │   ├── node/NodeEntity.kt
│       │       │   │   ├── message/MessageDao.kt
│       │       │   │   ├── message/MessageEntity.kt
│       │       │   │   ├── message/MessageHopEntity.kt
│       │       │   │   ├── message/MessageHopDao.kt
│       │       │   │   ├── seen/SeenMessageEntity.kt
│       │       │   │   └── seen/SeenMessageDao.kt
│       │       │   ├── ble/
│       │       │   │   ├── BleCapabilities.kt
│       │       │   │   ├── BlePermissions.kt
│       │       │   │   ├── BleAdvertiser.kt
│       │       │   │   ├── BleScanner.kt
│       │       │   │   ├── BeaconScheduler.kt
│       │       │   │   ├── RelayQueue.kt
│       │       │   │   ├── GattServer.kt
│       │       │   │   ├── GattClient.kt
│       │       │   │   ├── GattPeerRegistry.kt
│       │       │   │   └── ResQMeshGattUuids.kt
│       │       │   ├── codec/
│       │       │   │   ├── ByteWriter.kt
│       │       │   │   ├── ByteReader.kt
│       │       │   │   ├── FrameCodec.kt
│       │       │   │   └── FrameParseException.kt
│       │       │   ├── prefs/NodeIdentityStore.kt
│       │       │   └── repo/DefaultMeshRepository.kt
│       │       ├── mesh/
│       │       │   ├── MeshManager.kt
│       │       │   ├── RelayEngine.kt
│       │       │   ├── ForwardingPolicy.kt
│       │       │   ├── DuplicateGuard.kt
│       │       │   ├── TtlPolicy.kt
│       │       │   ├── FragmentAssembler.kt
│       │       │   ├── AckTracker.kt
│       │       │   └── StoreAndForwardQueue.kt
│       │       ├── service/
│       │       │   ├── MeshService.kt
│       │       │   └── BootReceiver.kt
│       │       └── ui/
│       │           ├── MainActivity.kt
│       │           ├── ResQMeshNavHost.kt
│       │           ├── theme/Theme.kt
│       │           ├── home/HomeScreen.kt
│       │           ├── home/HomeViewModel.kt
│       │           ├── nodes/NodesScreen.kt
│       │           ├── nodes/NodesViewModel.kt
│       │           ├── chat/ChatScreen.kt
│       │           ├── chat/ChatViewModel.kt
│       │           ├── history/HistoryScreen.kt
│       │           ├── history/HistoryViewModel.kt
│       │           └── components/
│       │               ├── NodeRow.kt
│       │               ├── StatusDot.kt
│       │               └── MessageBubble.kt
│       ├── test/java/com/resqmesh/          ← unit test JVM, tanpa perangkat
│       └── androidTest/java/com/resqmesh/   ← instrumented test, butuh 2–3 HP
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/libs.versions.toml
└── gradle.properties
```

> **Single-module** disengaja. Untuk prototype 3–5 perangkat, multi-module menambah morbiditas build tanpa benefit. Pisahkan ke modul terpisah (`core-wire`, `core-db`) hanya bila produk berkembang.

### 1.2 Build config

`gradle/libs.versions.toml`:

```toml
[versions]
agp = "8.5.2"
kotlin = "1.9.24"
ksp = "1.9.24-1.0.20"
coreKtx = "1.13.1"
lifecycle = "2.8.4"
activityCompose = "1.9.1"
composeBom = "2024.06.00"
navigation = "2.7.7"
room = "2.6.1"
coroutines = "1.8.1"
junit = "4.13.2"
androidxTestJunit = "1.2.1"
espresso = "3.6.1"
mockk = "1.13.11"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-service = { module = "androidx.lifecycle:lifecycle-service", version.ref = "lifecycle" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { module = "androidx.compose.ui:ui" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3" }
androidx-compose-material-icons = { module = "androidx.compose.material:material-icons-extended" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
androidx-room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
androidx-room-testing = { module = "androidx.room:room-testing", version.ref = "room" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
junit = { module = "junit:junit", version.ref = "junit" }
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
androidx-test-junit = { module = "androidx.test.ext:junit", version.ref = "androidxTestJunit" }
androidx-test-espresso = { module = "androidx.test.espresso:espresso-core", version.ref = "espresso" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

`app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.resqmesh"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.resqmesh"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }

    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
}
```

`minSdk = 26` dipilih karena `BluetoothLeAdvertiser.startAdvertisingSet()` dan `AdvertiseCallback.onStartSuccess()` diperkenalkan di API 26. Menurunkan minSdk ke 24 memaksa fallback ke mode legacy satu-advertiser dan kehilangan callback status advertise.

---

## 2. Identity dan Wire Format

### 2.1 NodeId

```
NodeId  = 3 byte unsigned (0x000001 .. 0xFFFFFE)
Tampil  = "NODE-" + 6 hex uppercase   ->  NODE-A83F2C
Nilai 0x000000 (UNKNOWN) dan 0xFFFFFF (BROADCAST) punya makna khusus
```

Dibuat sekali saat first launch, disimpan permanen di SharedPreferences. `android:allowBackup="false"` (§4.2) mencegah NodeId ter-restore ke perangkat lain — dua perangkat dengan NodeId sama akan merusak routing secara senyap.

`domain/model/NodeId.kt`:

```kotlin
@JvmInline
value class NodeId(val value: Long) : Comparable<NodeId> {

    init { require(value in MIN_VALUE..MAX_VALUE) { "NodeId di luar rentang: $value" } }

    val hex: String get() = "%06X".format(value)
    val isBroadcast: Boolean get() = value == BROADCAST

    override fun toString(): String = "NODE-$hex"
    override fun compareTo(other: NodeId): Int = value.compareTo(other.value)

    companion object {
        const val UNKNOWN   = 0x000000L
        const val BROADCAST = 0xFFFFFFL
        const val MIN_VALUE = 0x000001L
        const val MAX_VALUE = 0xFFFFFE

        fun fromHex(s: String): NodeId =
            NodeId(s.removePrefix("NODE-").toLong(16) and 0xFFFFFF)

        fun random(random: Random = Random.Default): NodeId =
            NodeId((MIN_VALUE..MAX_VALUE).random(random))
    }
}
```

### 2.2 MessageId

```
MessageId = (originNodeId << 24) | seq24      -> 42 bit, muat di Long
Tampil     = originNodeId.hex + "-" + "%06X"  ->  A83F2C-000001
```

`seq24` adalah counter per-node, naik 1 untuk setiap pesan yang dibuat, disimpan bersama NodeId. Bentuk komposit ini memberi dua sifat sekaligus:

- **Unik global** tanpa koordinator sentral.
- **Prefix = asal pesan**, sehingga dua frame dengan `seq` sama tapi `origin` berbeda tidak salah dianggap duplikat.

```kotlin
@JvmInline
value class MessageId(val value: Long) {
    val origin: NodeId get() = NodeId((value ushr 24) and 0xFFFFFF)
    val seq: Int get() = (value and 0xFFFFFF).toInt()

    fun display(): String = "${origin.hex}-%06X".format(seq)

    companion object {
        private const val SEQ_MASK = 0xFFFFFF

        fun of(origin: NodeId, seq: Int): MessageId {
            require(seq in 0..SEQ_MASK) { "seq overflow: $seq" }
            return MessageId((origin.value shl 24) or seq.toLong())
        }

        fun fromWire(origin: NodeId, seq: Int): MessageId = of(origin, seq)
    }
}
```

> `seq` overflow setelah 16,7 juta pesan. Cukup untuk prototype. Bila terjadi, node meregenerasi identity (§2.1) dan menaikkan `nodeSeq` supaya sekuens peer ikut ter-reseed.

### 2.3 Batas frame advertising

Android **legacy advertising** membatasi 31 byte untuk seluruh AD structure, bukan 31 byte payload aplikasi.

```
AD structure "Manufacturer Specific Data":  [len:1][type:1][companyId:2][data:N]
Overhead = 4 byte  ->  budget data = 27 byte
```

Service UUID 128-bit sengaja tidak ditambahkan; biayanya 18 byte sehingga sisa payload hanya 9 byte. Sebagai gantinya, filter scan memakai `companyId`:

```kotlin
val filter = ScanFilter.Builder()
    .setManufacturerData(FrameCodec.COMPANY_ID, null, null)
    .build()
```

`COMPANY_ID` default `0xE000`. **Wajib diganti** dengan company ID yang terdaftar resmi di Bluetooth SIG sebelum demo di luar lingkungan kampus — ID yang tidak dialokasikan dapat bertabrakan dengan perangkat vendor lain di area yang sama.

### 2.4 Format frame

**BEACON — 27 byte**

```text
off  size  type   field
 0    1    u8     version          = 0x01
 1    1    u8     frameType        = 0x01 (BEACON)
 2    3    u24    nodeId
 5    1    u8     statusFlags      lihat NodeStatusFlags
 6    1    u8     batteryPct       0..100, 0xFF = unknown
 7    2    u16    pendingCount     pesan PENDING_FORWARD yang dipegang node
 9    1    u8     defaultTtl       TTL yang dipakai node untuk pesan baru
10    1    u8     gattPeerCount    jumlah peer GATT yang terhubung
11    4    u32    nodeSeq          naik tiap beacon
15   12    —     reserved
```

`nodeSeq` naik 4 pada re-advertise frame identik dari node yang sama (Android memanggil callback `onStartSuccess` ulang saat slot berubah). Ini membuat frame identik tetap bisa dibedakan untuk keperluan flood control di §6.4.

**MSG — 27 byte**

```text
off  size  type   field
 0    1    u8     version          = 0x01
 1    1    u8     frameType        = 0x02 (MSG)
 2    3    u24    msgSeq
 5    3    u24    originNodeId
 8    3    u24    destinationId    0xFFFFFF = BROADCAST
11    1    u8     ttl
12    1    u8     hopCount
13    1    u8     flags            lihat MessageFlags
14    1    u8     fragIndex
15    1    u8     fragCount
16    1    u8     totalPayloadLen  panjang payload utuh, 0..255
17    1    u8     thisPayloadLen   panjang payload di frame ini, 0..9
18    9    —     payload           UTF-8, maksimal 9 byte
```

Budget payload = **9 byte per frame**.

| Contoh payload | Panjang | Frame |
|---|---|---|
| `"SOS"` | 3 | 1 |
| `"BUTUH BANTUAN"` | 12 | 2 |
| `"Saya berada di gedung sekolah."` | 29 | 4 |
| `"Saya aman"` | 8 | 1 |

> Payload > 255 byte memerlukan `totalPayloadLen` widened ke u16 dengan mengorbankan 1 byte. Prototype belum memerlukan; pesan teks darurat jauh di bawah 255 byte.

### 2.5 Flags

```text
MessageFlags
  bit0  SOS             pesan dari tombol SOS
  bit1  FRAGMENTED      fragCount > 1
  bit2  ACK_REQUESTED   pengirim ingin ACK
  bit3  IS_ACK          frame ini adalah ACK
  bit4  REPLAY          frame hasil store-and-forward, bukan live flood
  bit5  REPLY_TO_HOP    jawaban atas hop spesifik
  bit6-7 reserved

NodeStatusFlags
  bit0  MESH_ACTIVE     mesh menyala
  bit1  HAS_PENDING     punya pesan PENDING_FORWARD
  bit2  SOS_RECEIVED    sudah pernah menerima SOS
  bit3  CHARGING
  bit4  SCANNING
```

Flag `REPLAY` penting untuk metrik: pesan yang tiba 30 detik setelah pengiriman tidak boleh dihitung sebagai delivery rate yang sehat (§10.3).

---

## 3. Database Room

### 3.1 Skema SQL

```sql
-- ============ nodes ============
CREATE TABLE IF NOT EXISTS `NodeEntity` (
    `nodeId`         INTEGER NOT NULL,
    `displayName`    TEXT    NOT NULL,
    `statusFlags`    INTEGER NOT NULL DEFAULT 0,
    `batteryPct`     INTEGER NOT NULL DEFAULT -1,
    `pendingCount`   INTEGER NOT NULL DEFAULT 0,
    `gattPeerCount`  INTEGER NOT NULL DEFAULT 0,
    `defaultTtl`     INTEGER NOT NULL DEFAULT 5,
    `nodeSeq`        INTEGER NOT NULL DEFAULT 0,
    `rssi`           INTEGER NOT NULL DEFAULT -127,
    `isSelf`         INTEGER NOT NULL DEFAULT 0,
    `firstSeenAt`    INTEGER NOT NULL,
    `lastSeenAt`     INTEGER NOT NULL,
    PRIMARY KEY(`nodeId`)
);
CREATE INDEX IF NOT EXISTS `index_NodeEntity_lastSeenAt` ON `NodeEntity`(`lastSeenAt`);
CREATE INDEX IF NOT EXISTS `index_NodeEntity_isSelf`     ON `NodeEntity`(`isSelf`);

-- ============ messages ============
CREATE TABLE IF NOT EXISTS `MessageEntity` (
    `messageKey`      INTEGER NOT NULL,   -- MessageId.value (u42)
    `messageIdHex`    TEXT    NOT NULL,
    `originNodeId`    INTEGER NOT NULL,
    `destinationId`   INTEGER NOT NULL,   -- 0xFFFFFF = BROADCAST
    `peerId`          INTEGER NOT NULL,   -- node tujuan langsung; = origin bila relay
    `payload`         TEXT    NOT NULL,
    `payloadBytes`    BLOB    NOT NULL,   -- salinan UTF-8 utuh untuk re-assembly
    `createdAt`       INTEGER NOT NULL,
    `initialTtl`      INTEGER NOT NULL,
    `ttl`             INTEGER NOT NULL,
    `hopCount`        INTEGER NOT NULL DEFAULT 0,
    `status`          TEXT    NOT NULL,
    `direction`       TEXT    NOT NULL,
    `isSos`           INTEGER NOT NULL DEFAULT 0,
    `isReplay`        INTEGER NOT NULL DEFAULT 0,
    `firstForwardAt`  INTEGER,
    `deliveredAt`     INTEGER,
    `isReassembly`    INTEGER NOT NULL DEFAULT 0,  -- sedang dikumpulkan, belum utuh
    `lastError`       TEXT,
    PRIMARY KEY(`messageKey`)
);
CREATE INDEX IF NOT EXISTS `index_MessageEntity_createdAt`  ON `MessageEntity`(`createdAt` DESC);
CREATE INDEX IF NOT EXISTS `index_MessageEntity_status`     ON `MessageEntity`(`status`);
CREATE INDEX IF NOT EXISTS `index_MessageEntity_peerId`     ON `MessageEntity`(`peerId`, `createdAt` DESC);
CREATE INDEX IF NOT EXISTS `index_MessageEntity_statusDest` ON `MessageEntity`(`status`, `destinationId`);

-- ============ message_hops ============
CREATE TABLE IF NOT EXISTS `MessageHopEntity` (
    `id`          INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    `messageKey`  INTEGER NOT NULL,
    `nodeId`      INTEGER NOT NULL,
    `hopIndex`    INTEGER NOT NULL,
    `rssi`        INTEGER NOT NULL,
    `latencyMs`   INTEGER,
    `observedAt`  INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS `index_MessageHopEntity_messageKey`
    ON `MessageHopEntity`(`messageKey`, `hopIndex`);

-- ============ seen_messages ============
CREATE TABLE IF NOT EXISTS `SeenMessageEntity` (
    `messageKey`   INTEGER NOT NULL,   -- PK = MessageId komposit
    `originNodeId` INTEGER NOT NULL,
    `msgSeq`       INTEGER NOT NULL,
    `firstSeenAt`  INTEGER NOT NULL,
    `expiresAt`    INTEGER NOT NULL,   -- firstSeenAt + SEEN_RETENTION_MS
    `hopCount`     INTEGER NOT NULL,
    `forwardCount` INTEGER NOT NULL DEFAULT 0,  -- berapa kali kita meneruskannya
    PRIMARY KEY(`messageKey`)
);
CREATE INDEX IF NOT EXISTS `index_SeenMessageEntity_expiresAt` ON `SeenMessageEntity`(`expiresAt`);
CREATE INDEX IF NOT EXISTS `index_SeenMessageEntity_origin`    ON `SeenMessageEntity`(`originNodeId`, `msgSeq`);
```

### 3.2 Kenapa `seen_messages` dipisah dari `messages`

Kalau `seen_messages` diturunkan dari `MessageEntity`, maka pesan yang diteruskan tapi tidak pernah sampai tujuan (TTL habis) akan hilang dari dedupe. Node lain bisa meneruskannya ulang selama baris masih ada, menghasilkan duplikat. Tabel terpisah dengan `expiresAt` independen membuat masa hidup dedupe tidak terikat pada masa hidup history.

### 3.3 Entity Kotlin

`data/db/node/NodeEntity.kt`:

```kotlin
@Entity(
    tableName = "NodeEntity",
    indices = [Index(value = ["lastSeenAt"]), Index(value = ["isSelf"])],
)
data class NodeEntity(
    @PrimaryKey @ColumnInfo(name = "nodeId")     val nodeId: Long,
    @ColumnInfo(name = "displayName")            val displayName: String,
    @ColumnInfo(name = "statusFlags")            val statusFlags: Int,
    @ColumnInfo(name = "batteryPct")             val batteryPct: Int,
    @ColumnInfo(name = "pendingCount")           val pendingCount: Int,
    @ColumnInfo(name = "gattPeerCount")          val gattPeerCount: Int,
    @ColumnInfo(name = "defaultTtl")             val defaultTtl: Int,
    @ColumnInfo(name = "nodeSeq")                val nodeSeq: Long,
    @ColumnInfo(name = "rssi")                   val rssi: Int,
    @ColumnInfo(name = "isSelf")                 val isSelf: Boolean,
    @ColumnInfo(name = "firstSeenAt")            val firstSeenAt: Long,
    @ColumnInfo(name = "lastSeenAt")             val lastSeenAt: Long,
)
```

`data/db/message/MessageEntity.kt`:

```kotlin
@Entity(
    tableName = "MessageEntity",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["status"]),
        Index(value = ["peerId", "createdAt"]),
        Index(value = ["status", "destinationId"]),
    ],
)
data class MessageEntity(
    @PrimaryKey @ColumnInfo(name = "messageKey")    val messageKey: Long,
    @ColumnInfo(name = "messageIdHex")              val messageIdHex: String,
    @ColumnInfo(name = "originNodeId")              val originNodeId: Long,
    @ColumnInfo(name = "destinationId")             val destinationId: Long,
    @ColumnInfo(name = "peerId")                    val peerId: Long,
    @ColumnInfo(name = "payload")                   val payload: String,
    @ColumnInfo(name = "payloadBytes")              val payloadBytes: ByteArray,
    @ColumnInfo(name = "createdAt")                 val createdAt: Long,
    @ColumnInfo(name = "initialTtl")                val initialTtl: Int,
    @ColumnInfo(name = "ttl")                       val ttl: Int,
    @ColumnInfo(name = "hopCount")                  val hopCount: Int,
    @ColumnInfo(name = "status")                    val status: String,
    @ColumnInfo(name = "direction")                 val direction: String,
    @ColumnInfo(name = "isSos")                     val isSos: Boolean,
    @ColumnInfo(name = "isReplay")                  val isReplay: Boolean,
    @ColumnInfo(name = "firstForwardAt")            val firstForwardAt: Long?,
    @ColumnInfo(name = "deliveredAt")               val deliveredAt: Long?,
    @ColumnInfo(name = "isReassembly")              val isReassembly: Boolean,
    @ColumnInfo(name = "lastError")                 val lastError: String?,
) {
    // Room membandingkan List<MessageEntity> dari Flow setiap ada perubahan;
    // equals/hashCode harus konsisten dengan ByteArray agar perubahan status
    // tetap terdeteksi oleh collectLatest / distinctUntilChanged.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MessageEntity) return false
        return messageKey == other.messageKey &&
            payloadBytes.contentEquals(other.payloadBytes) &&
            status == other.status &&
            hopCount == other.hopCount &&
            ttl == other.ttl &&
            deliveredAt == other.deliveredAt &&
            isReassembly == other.isReassembly
    }

    override fun hashCode(): Int {
        var result = messageKey.hashCode()
        result = 31 * result + payloadBytes.contentHashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + hopCount
        result = 31 * result + ttl
        result = 31 * result + (deliveredAt?.hashCode() ?: 0)
        result = 31 * result + isReassembly.hashCode()
        return result
    }
}
```

`data/db/message/MessageHopEntity.kt`:

```kotlin
@Entity(
    tableName = "MessageHopEntity",
    indices = [Index(value = ["messageKey", "hopIndex"])],
)
data class MessageHopEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "messageKey") val messageKey: Long,
    @ColumnInfo(name = "nodeId")     val nodeId: Long,
    @ColumnInfo(name = "hopIndex")   val hopIndex: Int,
    @ColumnInfo(name = "rssi")       val rssi: Int,
    @ColumnInfo(name = "latencyMs")  val latencyMs: Long?,
    @ColumnInfo(name = "observedAt") val observedAt: Long,
)
```

`data/db/seen/SeenMessageEntity.kt`:

```kotlin
@Entity(
    tableName = "SeenMessageEntity",
    indices = [Index(value = ["expiresAt"]), Index(value = ["originNodeId", "msgSeq"])],
)
data class SeenMessageEntity(
    @PrimaryKey @ColumnInfo(name = "messageKey") val messageKey: Long,
    @ColumnInfo(name = "originNodeId")           val originNodeId: Long,
    @ColumnInfo(name = "msgSeq")                 val msgSeq: Int,
    @ColumnInfo(name = "firstSeenAt")            val firstSeenAt: Long,
    @ColumnInfo(name = "expiresAt")              val expiresAt: Long,
    @ColumnInfo(name = "hopCount")               val hopCount: Int,
    @ColumnInfo(name = "forwardCount")           val forwardCount: Int,
)
```

### 3.4 Enum domain

```kotlin
enum class MessageStatus(val wire: String) {
    PENDING_FORWARD("PENDING_FORWARD"),     // disimpan, menunggu node tujuan muncul
    AWAITING_FRAGMENTS("AWAITING_FRAGMENTS"),
    IN_TRANSIT("IN_TRANSIT"),
    DELIVERED("DELIVERED"),                 // sampai ke destination
    ACKED("ACKED"),                         // destination membalas ACK
    EXPIRED("EXPIRED"),                     // TTL habis
    FAILED("FAILED");

    val isTerminal: Boolean get() = this == ACKED || this == EXPIRED || this == FAILED
}

enum class MessageDirection(val wire: String) {
    OUTGOING("OUTGOING"),   // saya yang membuat
    INCOMING("INCOMING"),   // saya adalah destination
    RELAYED("RELAYED"),     // saya hanya meneruskan
}
```

### 3.5 DAO

`data/db/message/MessageDao.kt`:

```kotlin
@Dao
interface MessageDao {

    @Upsert
    suspend fun upsert(message: MessageEntity)

    @Query("SELECT * FROM MessageEntity WHERE messageKey = :key LIMIT 1")
    suspend fun findByKey(key: Long): MessageEntity?

    @Query("SELECT * FROM MessageEntity WHERE messageKey = :key LIMIT 1")
    fun observeByKey(key: Long): Flow<MessageEntity?>

    @Query("""
        SELECT * FROM MessageEntity
        WHERE isReassembly = 1 AND messageKey NOT IN (
            SELECT messageKey FROM MessageEntity WHERE isReassembly = 0
        )
        ORDER BY createdAt DESC
    """)
    suspend fun incompleteReassemblies(): List<MessageEntity>

    @Query("""
        SELECT * FROM MessageEntity
        WHERE status = 'PENDING_FORWARD'
        ORDER BY isSos DESC, createdAt ASC
        LIMIT :limit
    """)
    suspend fun pendingForwards(limit: Int = 8): List<MessageEntity>

    @Query("""
        SELECT COUNT(*) FROM MessageEntity
        WHERE status = 'PENDING_FORWARD'
    """)
    fun observePendingForwardCount(): Flow<Int>

    @Query("""
        UPDATE MessageEntity
        SET status = :status,
            ttl = :ttl,
            hopCount = :hopCount,
            firstForwardAt = COALESCE(firstForwardAt, :now)
        WHERE messageKey = :key
    """)
    suspend fun markForwarded(key: Long, status: String, ttl: Int, hopCount: Int, now: Long)

    @Query("""
        UPDATE MessageEntity
        SET status = 'DELIVERED', deliveredAt = :now
        WHERE messageKey = :key AND status IN ('PENDING_FORWARD','IN_TRANSIT')
    """)
    suspend fun markDelivered(key: Long, now: Long): Int

    @Query("UPDATE MessageEntity SET status = 'ACKED' WHERE messageKey = :key AND status != 'ACKED'")
    suspend fun markAcked(key: Long): Int

    @Query("UPDATE MessageEntity SET status = 'EXPIRED', ttl = 0 WHERE messageKey = :key")
    suspend fun markExpired(key: Long): Int

    @Query("DELETE FROM MessageEntity WHERE createdAt < :cutoff")
    suspend fun purgeOlderThan(cutoff: Long): Int

    @Query("""
        SELECT * FROM MessageEntity
        WHERE peerId = :peerId AND isReassembly = 0
        ORDER BY createdAt DESC LIMIT :limit
    """)
    fun observeConversation(peerId: Long, limit: Int = 100): Flow<List<MessageEntity>>

    @Query("SELECT * FROM MessageEntity WHERE isReassembly = 0 ORDER BY createdAt DESC LIMIT :limit")
    fun observeHistory(limit: Int = 200): Flow<List<MessageEntity>>
}
```

`data/db/seen/SeenMessageDao.kt`:

```kotlin
@Dao
interface SeenMessageDao {

    /** @return -1 bila sudah pernah dilihat (duplikat), rowId bila fresh. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun tryInsert(entry: SeenMessageEntity): Long

    @Query("SELECT * FROM SeenMessageEntity WHERE messageKey = :key LIMIT 1")
    suspend fun find(key: Long): SeenMessageEntity?

    @Query("UPDATE SeenMessageEntity SET hopCount = MIN(hopCount, :hop) WHERE messageKey = :key")
    suspend fun lowerHopIfBetter(key: Long, hop: Int)

    @Query("UPDATE SeenMessageEntity SET forwardCount = forwardCount + 1 WHERE messageKey = :key")
    suspend fun incrementForwardCount(key: Long)

    @Query("DELETE FROM SeenMessageEntity WHERE expiresAt < :now")
    suspend fun purgeExpired(now: Long): Int

    @Query("SELECT COUNT(*) FROM SeenMessageEntity")
    suspend fun count(): Int
}
```

`data/db/node/NodeDao.kt`:

```kotlin
@Dao
interface NodeDao {

    @Upsert
    suspend fun upsert(node: NodeEntity)

    @Query("SELECT * FROM NodeEntity WHERE isSelf = 1 LIMIT 1")
    suspend fun self(): NodeEntity?

    @Query("SELECT * FROM NodeEntity WHERE isSelf = 1 LIMIT 1")
    fun observeSelf(): Flow<NodeEntity?>

    @Query("SELECT * FROM NodeEntity WHERE isSelf = 0 ORDER BY lastSeenAt DESC")
    fun observeNeighbors(): Flow<List<NodeEntity>>

    @Query("SELECT * FROM NodeEntity WHERE nodeId = :id LIMIT 1")
    suspend fun find(id: Long): NodeEntity?

    @Query("UPDATE NodeEntity SET rssi = :rssi, lastSeenAt = :now WHERE nodeId = :id")
    suspend fun touchRssi(id: Long, rssi: Int, now: Long)

    @Query("""
        UPDATE NodeEntity
        SET statusFlags = :flags,
            batteryPct = :battery,
            pendingCount = :pending,
            gattPeerCount = :peers,
            defaultTtl = :ttl,
            nodeSeq = :seq
        WHERE nodeId = :id
    """)
    suspend fun applyBeacon(
        id: Long, flags: Int, battery: Int,
        pending: Int, peers: Int, ttl: Int, seq: Long,
    )

    @Query("DELETE FROM NodeEntity WHERE isSelf = 0 AND lastSeenAt < :cutoff")
    suspend fun pruneStale(cutoff: Long): Int
}
```

### 3.6 Database

`data/db/ResQMeshDatabase.kt`:

```kotlin
@Database(
    entities = [
        NodeEntity::class,
        MessageEntity::class,
        MessageHopEntity::class,
        SeenMessageEntity::class,
    ],
    version = 1,
    exportSchema = true,   // schema di-commit ke app/schemas untuk lampiran skripsi
)
abstract class ResQMeshDatabase : RoomDatabase() {
    abstract fun nodeDao(): NodeDao
    abstract fun messageDao(): MessageDao
    abstract fun messageHopDao(): MessageHopDao
    abstract fun seenMessageDao(): SeenMessageDao

    companion object {
        const val NAME = "resqmesh.db"

        fun build(context: Context): ResQMeshDatabase =
            Room.databaseBuilder(context, ResQMeshDatabase::class.java, NAME)
                .fallbackToDestructiveMigration()
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
```

> `fallbackToDestructiveMigration()` tidak dapat diterima di produksi, tetapi benar untuk prototype: tidak ada data pengguna yang tidak bisa dipulihkan. Tetap export schema ke `app/schemas/` dan commit — file itu yang dipakai untuk lampiran migration nanti.

---

## 4. Permission BLE Android

### 4.1 Matriks permission

| Permission | Berlaku sejak | Mengapa | Runtime request | Jika ditolak |
|---|---|---|---|---|
| `BLUETOOTH_SCAN` | API 31 | `BluetoothLeScanner.startScan` | Ya | Scan mati total |
| `BLUETOOTH_ADVERTISE` | API 31 | `BluetoothLeAdvertiser.startAdvertising` | Ya | Node tidak terlihat, tetapi tetap bisa scan dan relay |
| `BLUETOOTH_CONNECT` | API 31 | `connectGatt`, akses `BluetoothDevice` | Ya | GATT mati; adv-flood tetap jalan |
| `ACCESS_FINE_LOCATION` | API ≤ 30 | Wajib agar `ScanResult` terisi | Ya (hanya ≤ 30) | Scan mati total di Android 11 |
| `ACCESS_COARSE_LOCATION` | API ≤ 30 | Berpasangan dengan FINE | Ya (hanya ≤ 30) | — |
| `POST_NOTIFICATIONS` | API 33 | Notifikasi FGS dan notifikasi pesan masuk | Ya | Mesh tetap jalan, notifikasi hilang |
| `FOREGROUND_SERVICE` | API 28 | Menahan mesh saat app di background | — | — |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | API 34 | Jenis FGS untuk akses Bluetooth | — | — |
| `ACCESS_BACKGROUND_LOCATION` | API 29 | Scan saat app benar-benar ter-background | Ya, dialog terpisah | Mesh tetap jalan selama FGS aktif |

> **Android 12 (API 31)** adalah titik balik utama: tiga permission `BLUETOOTH_*` menggantikan model berbasis lokasi. Android 11 ke bawah menjadi jalur kode kedua yang hanya aktif di bawah API 31.
>
> **Android 14 (API 34)** menambah `FOREGROUND_SERVICE_CONNECTED_DEVICE`. `startForegroundService()` tanpa jenis FGS yang sesuai melempar `SecurityException`.

### 4.2 AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- ===== BLE: API 31+ ===== -->
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN"
        android:usesPermissionFlags="neverForLocation"
        tools:targetApi="s" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

    <!-- ===== BLE: legacy, API <= 30 ===== -->
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"
        android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"
        android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />

    <!-- ===== Foreground service ===== -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.VIBRATE" />

    <!-- ===== Hardware ===== -->
    <uses-feature android:name="android.hardware.bluetooth_le" android:required="true" />
    <uses-feature android:name="android.hardware.bluetooth" android:required="true" />

    <application
        android:name=".ResQMeshApp"
        android:allowBackup="false"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.ResQMesh">

        <activity
            android:name=".ui.MainActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:theme="@style/Theme.ResQMesh">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".service.MeshService"
            android:enabled="true"
            android:exported="false"
            android:foregroundServiceType="connectedDevice" />

        <receiver
            android:name=".service.BootReceiver"
            android:enabled="true"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
            </intent-filter>
        </receiver>
    </application>
</manifest>
```

#### Catatan kritis

1. **`neverForLocation`** — flag pada `BLUETOOTH_SCAN` memberi hak scan tanpa location permission di Android 12+, sekaligus menyatakan bahwa app tidak menurunkan lokasi dari hasil scan. Ini persis yang dibutuhkan ResQMesh, karena PRD §4 menyebut GPS tracking real-time sebagai non-goal. Konsekuensinya: aplikasi **tidak boleh** menurunkan lokasi dari `ScanResult` dan harus menghindari API lokasi.
2. **`android:allowBackup="false"`** — mencegah `NodeId` ter-restore ke perangkat lain. Dua perangkat dengan `NodeId` sama merusak routing secara senyap.
3. **`android:required="true"`** untuk `bluetooth_le` — Play Store menyaring perangkat tanpa BLE. Untuk KTI yang disideload, tidak kritis; tetap didokumentasikan.
4. **Urutan dialog permission** — Android hanya mengizinkan satu dialog runtime per grup. Minta `ACCESS_BACKGROUND_LOCATION` di dialog terpisah setelah user menyetujui lokasi (jalur Android 10+), atau request akan langsung `denied`.
5. **Throttle scan Android Oreo+** — app target API 26+ yang tidak memiliki foreground service aktif dibatasi 5× `startScan()` per 30 menit. Pola stop-then-start-scan yang salah akan memicu limit. `BleScanner` harus idempotent.

### 4.3 Helper permission

`data/ble/BlePermissions.kt`:

```kotlin
object BlePermissions {

    val RUNTIME_PERMISSIONS: Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    fun allGranted(context: Context): Boolean = RUNTIME_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Di API 31+ lokasi tidak lagi dibutuhkan karena BLUETOOTH_SCAN + neverForLocation. */
    fun requiresLegacyLocation(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S

    fun missingBackgroundLocation(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        ) != PackageManager.PERMISSION_GRANTED
}
```

`data/ble/BleCapabilities.kt`:

```kotlin
object BleCapabilities {

    fun adapter(context: Context): BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    fun isBleSupported(context: Context): Boolean = adapter(context) != null

    fun isMultiAdSupported(context: Context): Boolean =
        adapter(context)?.isMultipleAdvertisementSupported == true

    fun supportsLowLatency(context: Context): Boolean =
        adapter(context)?.isLeExtendedAdvertisingSupported == true
}
```

### 4.4 Foreground Service

`service/MeshService.kt` (inti):

```kotlin
class MeshService : LifecycleService(), MeshManager.Observer {

    private val manager: MeshManager
        get() = (application as ResQMeshApp).graph.meshManager

    override fun onCreate() {
        super.onCreate()
        manager.addObserver(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForegroundCompat()
        // Scan dan advertise dijalankan di dalam FGS agar tidak terkena
        // background-scan throttle Oreo dan agar proses tidak dibunuh.
        manager.start()
        return START_STICKY
    }

    private fun startForegroundCompat() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(manager.state.value), type,
        )
    }

    override fun onStateChanged(state: MeshState) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(state))
    }

    override fun onDestroy() {
        manager.removeObserver(this)
        manager.stop()
        super.onDestroy()
    }
}
```

`startForeground()` harus dipanggil dalam ~5 detik setelah `startForegroundService()`, jika tidak → `ForegroundServiceDidNotStartInTimeException`. Karena itu dipanggil **sebelum** `manager.start()`.

Isi notifikasi (PRD §9 Screen 1):

```text
┌──────────────────────────────────────────┐
│ ResQMesh - NODE-A83F2C           [ SOS ] │
│ Mesh aktif - 3 node - 1 pesan            │
│                            [ Buka ]      │
└──────────────────────────────────────────┘
```

Notifikasi foreground service **tidak dapat di-dismiss**. Karena itu harus memuat action "Berhentikan mesh" yang menghentikan service secara eksplisit — pengguna butuh kendali atas kapan periode broadcast berlangsung.

---

## 5. Class Kotlin

### 5.1 Peta kelas

```text
                    ┌───────────────────────┐
                    │          UI           │
                    │  Screen + ViewModel   │
                    └───────────┬───────────┘
                                │ observes Flow
                    ┌───────────▼───────────┐
                    │   MeshRepository     │  (interface)
                    └───────────┬───────────┘
                                │
          ┌─────────────────────▼─────────────────────┐
          │                MeshManager                 │
          │  state, scheduler, orkestrasi policy       │
          └──┬─────────┬──────────┬──────────┬─────────┘
             │         │          │          │
  ┌──────────▼──┐ ┌────▼─────┐ ┌──▼───────┐ ┌▼────────────────┐
  │ RelayEngine │ │Duplicate │ │Fragment  │ │ BeaconScheduler  │
  │ keputusan   │ │  Guard   │ │Assembler │ │  (TX duty cycle) │
  │ forwarding  │ └──────────┘ └──────────┘ └┬────────────────┘
  └──────┬──────┘                              │
         │                          ┌────────▼─────────┐
         │                          │   RelayQueue     │
         │                          │ (antrian frame)  │
         │                          └──────────────────┘
  ┌──────▼──────────────────┐
  │ BleAdvertiser/Scanner   │
  │ GattServer/GattClient   │
  └──────┬──────────────────┘
         │
   ┌─────▼────────┐
   │  FrameCodec  │  (biner ⇄ domain)
   └──────────────┘
```

### 5.2 Kontrak inti

**`MeshRepository`** — satu-satunya yang dilihat ViewModel:

```kotlin
interface MeshRepository {
    fun observeSelf(): Flow<Node>
    fun observeNeighbors(): Flow<List<Node>>
    fun observeHistory(limit: Int = 200): Flow<List<Message>>
    fun observeConversation(peerId: NodeId, limit: Int = 100): Flow<List<Message>>
    fun observeMeshState(): Flow<MeshState>
    fun observePendingForwardCount(): Flow<Int>

    suspend fun sendTo(destination: NodeId, text: String, ttl: Int, isSos: Boolean = false): MessageId
    suspend fun broadcastSos(text: String, ttl: Int = MeshConfig.SOS_DEFAULT_TTL): MessageId
    suspend fun rebroadcastPending(): Int
    suspend fun prune(): Int
}
```

**`MeshManager`** — orchestrator tunggal, hidup di FGS:

```kotlin
class MeshManager(
    private val scope: CoroutineScope,
    private val identity: NodeId,
    private val nodeDao: NodeDao,
    private val messageDao: MessageDao,
    private val hopDao: MessageHopDao,
    private val seenDao: SeenMessageDao,
    private val gattPeerRegistry: GattPeerRegistry,
    private val assembler: FragmentAssembler,
    private val codec: FrameCodec,
    private val clock: TimeProvider,
) {
    val state: StateFlow<MeshState>

    fun start()
    fun stop()
    fun addObserver(observer: MeshManager.Observer)
    fun removeObserver(observer: MeshManager.Observer)

    suspend fun onAdvertisedFrame(frame: MeshFrame, rssi: Int)
    suspend fun onConnectedFrame(frame: MeshFrame, peer: NodeId, rssi: Int)
    suspend fun submitOutbound(destination: NodeId, text: String, ttl: Int, isSos: Boolean): MessageId
    fun connectedNeighbors(): Set<NodeId>
}
```

**`RelayEngine`** — mengimplementasikan routing logic PRD §13:

```kotlin
class RelayEngine(
    private val selfId: NodeId,
    private val duplicateGuard: DuplicateGuard,
    private val ttlPolicy: TtlPolicy,
    private val forwardingPolicy: ForwardingPolicy,
    private val assembler: FragmentAssembler,
    private val ackTracker: AckTracker,
) {
    suspend fun onFrame(frame: MeshFrame, source: Peer, rssi: Int, now: Long): RelayDecision
}

sealed interface RelayDecision {
    /** Bukan untuk kita dan tidak perlu di-flood. */
    data object Ignore : RelayDecision

    /** Kita adalah destination. */
    data class Consume(val message: Message) : RelayDecision

    /** Perlu di-re-advertise. */
    data class Relayed(val frames: List<MeshFrame>) : RelayDecision

    /** Simpan sampai destination muncul (store-and-forward). */
    data class ForwardLater(val message: Message) : RelayDecision

    /** Kirim ACK kembali ke origin. */
    data class Ack(val frame: MeshFrame) : RelayDecision

    data class Reject(val reason: RejectReason) : RelayDecision
}

enum class RejectReason {
    DUPLICATE, TTL_EXPIRED, FRAGMENT_INCOMPLETE, SELF_ORIGIN, MALFORMED, FLOOD_SUPPRESSED,
}
```

**`DuplicateGuard`**:

```kotlin
class DuplicateGuard(
    private val seenDao: SeenMessageDao,
    private val clock: TimeProvider,
) {
    /**
     * Atomik lewat INSERT dengan OnConflictStrategy.IGNORE.
     * Mengembalikan [DuplicateVerdict.Fresh] bila baru pertama kali,
     * [DuplicateVerdict.Repeated] bila sudah pernah. Tidak ada race TOCTOU
     * walau dua frame tiba bersamaan pada dua dispatcher berbeda.
     */
    suspend fun registerAndCheck(id: MessageId, hop: Int, ttl: Int): DuplicateVerdict
}

sealed interface DuplicateVerdict {
    data object Fresh : DuplicateVerdict
    data class Repeated(val previousHop: Int, val previousForwardCount: Int) : DuplicateVerdict
}
```

**`TtlPolicy`**:

```kotlin
class TtlPolicy {
    fun shouldForward(ttl: Int, hop: Int, initialTtl: Int): Boolean =
        ttl > 0 && hop < initialTtl

    fun nextTtl(ttl: Int): Int = (ttl - 1).coerceAtLeast(0)
    fun nextHop(hop: Int): Int = hop + 1
}
```

**`FragmentAssembler`**:

```kotlin
class FragmentAssembler(
    private val scope: CoroutineScope,
    private val clock: TimeProvider,
) {
    fun accept(frame: MeshFrame): AssemblyResult
    fun pendingCount(): Int
    fun evictExpired()
}

sealed interface AssemblyResult {
    data object Incomplete : AssemblyResult
    data class Complete(val payload: ByteArray) : AssemblyResult
    data class Stale(val id: MessageId) : AssemblyResult
    data class Malformed(val reason: String) : AssemblyResult
}
```

**`BeaconScheduler`** — TX duty cycle. Android hanya mengizinkan satu advertiser per proses, jadi semua transmisi diserialisasi lewat satu antrean:

```kotlin
class BeaconScheduler(
    private val scope: CoroutineScope,
    private val advertiser: BleAdvertiser,
    private val relayQueue: RelayQueue,
) {
    fun start()
    fun stop()
    fun enqueueUrgent(frame: MeshFrame)   // SOS: didahulukan, slot beacon dikorbankan
    fun enqueueNormal(frame: MeshFrame)   // flood biasa dan fragment
    fun updateBeaconPayload(build: () -> ByteArray)
}
```

### 5.3 Prioritas TX

Satu slot advertise pada satu waktu. Urutan prioritas:

```text
[0]  SOS frame        -> advertise segera, 150 ms, repeat 3x
[1]  ACK frame        -> 200 ms, repeat 2x
[2]  Relay / store-and-forward -> 300 ms, repeat 2x
[3]  Beacon           -> 300 ms, dijadwal ulang; di-skip bila antrean di atas tidak kosong
```

Jitter acak 0–200 ms disisipkan sebelum setiap advertise untuk mengurangi collision antar-node (§11 R1). Saat SOS aktif, interval beacon diturunkan ke 150 ms dan scan mode dinaikkan ke `SCAN_MODE_LOW_LATENCY`.

### 5.4 BleAdvertiser

```kotlin
class BleAdvertiser(
    private val context: Context,
    private val codec: FrameCodec,
) {
    private val advertiser: BluetoothLeAdvertiser?
        get() = BleCapabilities.adapter(context)?.bluetoothLeAdvertiser

    fun start(payload: ByteArray, onError: (Int) -> Unit): Boolean
    fun stop()

    companion object {
        const val MAX_ADVERTISING_BYTES = 27
    }
}
```

`updatePayload()` pada API < 26 memicu `stopAdvertising` lalu `startAdvertising`, yang menghasilkan gap 200–500 ms. `RelayQueue` menahan frame sampai callback `onStartSuccess` berikutnya agar tidak ada frame yang hilang di gap tersebut.

### 5.5 BleScanner

```kotlin
class BleScanner(
    private val context: Context,
    private val codec: FrameCodec,
    private val scope: CoroutineScope,
    private val onFrame: (MeshFrame, rssi: Int, timestamp: Long) -> Unit,
) {
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun start(): Result<Unit>
    fun stop()
    fun setSosActive(active: Boolean)   // menggeser SCAN_MODE
}
```

Pengaturan `ScanSettings`:

```kotlin
val settings = ScanSettings.Builder()
    .setScanMode(
        if (sosActive) ScanSettings.SCAN_MODE_LOW_LATENCY
        else ScanSettings.SCAN_MODE_BALANCED,
    )
    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
    .setReportDelay(0L)
    .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
    .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
    .build()
```

> `MATCH_NUM_MAX_ADVERTISEMENT` wajib dipakai bersama duty cycling. Default `MATCH_NUM_FIRST_ADVERTISEMENT` (= 1) membuat scanner berhenti melapor setelah advertisement pertama tiap perangkat — flooding tidak akan pernah bekerja.
>
> `setReportDelay(0L)` sedikit memboroskan daya, karena batching scan dihapus sejak Android 7. Menunda laporan fatal untuk pesan darurat; trade-off ini diambil secara sadar.

### 5.6 FrameCodec

```kotlin
class FrameCodec {
    fun encodeBeacon(frame: BeaconFrame): ByteArray
    fun encodeMessage(frame: MeshFrame): ByteArray   // sudah ter-fragmentasi
    fun decode(bytes: ByteArray): MeshFrame
}
```

Encoding multi-byte memakai **big-endian** (network order) agar `xxd` pada dump skripsi konsisten dengan diagram:

`data/codec/ByteWriter.kt`:

```kotlin
class ByteWriter(private val capacity: Int = 32) {
    private val buf = ByteArray(capacity)
    private var pos = 0

    fun u8(v: Int)  { buf[pos++] = (v and 0xFF).toByte() }

    fun u16(v: Int) { u8(v shr 8); u8(v) }

    fun u24(v: Long) {
        u8(((v shr 16) and 0xFF).toInt())
        u8(((v shr 8) and 0xFF).toInt())
        u8((v and 0xFF).toInt())
    }

    fun u32(v: Long) {
        u8(((v shr 24) and 0xFF).toInt())
        u8(((v shr 16) and 0xFF).toInt())
        u8(((v shr 8) and 0xFF).toInt())
        u8((v and 0xFF).toInt())
    }

    fun bytes(src: ByteArray) { src.copyInto(buf, pos); pos += src.size }
    fun skip(n: Int) { pos += n }
    fun toByteArray(): ByteArray = buf.copyOf(pos)
}
```

`data/codec/ByteReader.kt`:

```kotlin
class ByteReader(private val buf: ByteArray) {
    private var pos = 0

    fun u8(): Int = (buf[pos++].toInt() and 0xFF)

    fun u16(): Int = (u8() shl 8) or u8()

    fun u24(): Long = (u8().toLong() shl 16) or (u8().toLong() shl 8) or u8().toLong()

    fun u32(): Long =
        (u8().toLong() shl 24) or (u8().toLong() shl 16) or
        (u8().toLong() shl 8) or u8().toLong()

    fun bytes(n: Int): ByteArray =
        buf.copyOfRange(pos, pos + n).also { pos += n }
}
```

`data/codec/FrameCodec.kt`:

```kotlin
class FrameCodec {

    fun encodeBeacon(frame: BeaconFrame): ByteArray {
        val w = ByteWriter(27)
        w.u8(MeshConfig.PROTOCOL_VERSION)
        w.u8(FrameType.BEACON.code)
        w.u24(frame.nodeId.value)
        w.u8(frame.statusFlags)
        w.u8(frame.batteryPct)
        w.u16(frame.pendingCount)
        w.u8(frame.defaultTtl)
        w.u8(frame.gattPeerCount)
        w.u32(frame.nodeSeq)
        w.skip(12)   // reserved
        return w.toByteArray()
    }

    fun encodeMessage(frame: MeshFrame): ByteArray {
        val w = ByteWriter(27)
        w.u8(MeshConfig.PROTOCOL_VERSION)
        w.u8(FrameType.MSG.code)
        w.u24(frame.messageId.seq)
        w.u24(frame.messageId.origin.value)
        w.u24(frame.destination.value)
        w.u8(frame.ttl)
        w.u8(frame.hopCount)
        w.u8(frame.flags.code)
        w.u8(frame.fragIndex)
        w.u8(frame.fragCount)
        w.u8(frame.totalPayloadLen)
        w.u8(frame.payloadChunk.size)
        w.bytes(frame.payloadChunk)
        return w.toByteArray()
    }

    fun decode(bytes: ByteArray): MeshFrame {
        require(bytes.size >= 2) { "frame terlalu pendek: ${bytes.size}" }
        val r = ByteReader(bytes)
        val version = r.u8()
        if (version != MeshConfig.PROTOCOL_VERSION) {
            throw FrameParseException("Versi protokol tidak didukung: $version")
        }
        return when (FrameType.fromCode(r.u8())) {
            FrameType.BEACON -> decodeBeacon(r)
            FrameType.MSG    -> decodeMessage(r)
            else -> throw FrameParseException("Tipe frame tidak dikenal")
        }
    }
}
```

### 5.7 RelayQueue dan StoreAndForwardQueue

`RelayQueue` adalah antrian TX **frame** (fragment pesan, beacon, ACK) dengan backpressure per-PDU:

```kotlin
class RelayQueue(private val capacity: Int = MeshConfig.MAX_RELAY_QUEUE) {
    private val _urgent = MutableStateFlow<MeshFrame?>(null)
    private val _normal = ArrayDeque<MeshFrame>()

    /** SOS memakai latest-wins agar 30 tekanan tidak menumpuk 30 frame identik. */
    fun pushUrgent(frame: MeshFrame) { _urgent.value = frame }

    fun pushNormal(frame: MeshFrame) {
        if (_normal.size < capacity) _normal.addLast(frame)
    }

    fun poll(): MeshFrame? =
        _urgent.value?.also { _urgent.value = null } ?: _normal.removeFirstOrNull()

    fun size(): Int = (_urgent.value?.let { 1 } ?: 0) + _normal.size
}
```

`StoreAndForwardQueue` berbeda: bekerja di level **pesan utuh**, bukan frame, dan hanya berisi pesan berstatus `PENDING_FORWARD` yang destination-nya belum pernah terlihat.

### 5.8 MeshState

```kotlin
data class MeshState(
    val phase: Phase,
    val selfId: NodeId,
    val isScanning: Boolean,
    val isAdvertising: Boolean,
    val neighborCount: Int,
    val gattPeerCount: Int,
    val pendingForwardCount: Int,
    val isSosActive: Boolean,
    val lastError: String? = null,
) {
    enum class Phase { STOPPED, IDLE, SCANNING, ACTIVE, ERROR }
}
```

### 5.9 Config

`core/MeshConfig.kt`:

```kotlin
object MeshConfig {
    // ---- Identity ----
    const val DEFAULT_TTL = 5
    const val SOS_DEFAULT_TTL = 10
    const val MAX_TTL = 12

    // ---- Wire ----
    const val PROTOCOL_VERSION = 0x01
    const val COMPANY_ID = 0xE000          // ganti dengan ID resmi Bluetooth SIG
    const val MAX_ADVERTISING_BYTES = 27
    const val PAYLOAD_PER_FRAME = 9
    const val MAX_PAYLOAD_BYTES = 255

    // ---- Scheduler ----
    const val BEACON_INTERVAL_MS = 300L
    const val BEACON_INTERVAL_SOS_MS = 150L
    const val RELAY_INTERVAL_MS = 300L
    const val ACK_INTERVAL_MS = 200L
    const val SCHEDULER_JITTER_MS = 200L
    const val REPEAT_COUNT_NORMAL = 2
    const val REPEAT_COUNT_URGENT = 3

    // ---- Retention ----
    const val SEEN_RETENTION_MS = 30 * 60 * 1000L               // 30 menit
    const val ASSEMBLY_TIMEOUT_MS = 10_000L
    const val HISTORY_RETENTION_MS = 7 * 24 * 60 * 60 * 1000L  // 7 hari
    const val NODE_STALE_MS = 90_000L                           // 90 detik
    const val PRUNE_INTERVAL_MS = 5 * 60_000L

    // ---- GATT ----
    const val MAX_GATT_PEERS = 3
    const val GATT_CONNECT_BACKOFF_MS = 2_000L
    const val GATT_CHUNK_SIZE = 180      // konservatif di bawah MTU 247

    // ---- Flood control ----
    const val NORMAL_FORWARD_LIMIT = 2      // re-advertise maks 2x tanpa progres
const val SOS_FLOOD_FORWARD_LIMIT = 1   // SOS: tepat sekali, tanpa bias hop
    const val MAX_RELAY_QUEUE = 32
}
```

> `SEEN_RETENTION_MS` harus lebih besar dari `MAX_TTL × interval per hop`. Dengan TTL 12 dan hop ~700 ms, jarak terjauh sebelum pesan mati adalah ~8,4 detik. 30 menit memberi margin 200× — cukup untuk fragmen yang datang terlambat tanpa membuat `seen_messages` membengkak.

---

## 6. GATT Layer (Tier 2)

### 6.1 UUID

```kotlin
object ResQMeshGattUuids {
    // base UUID: 0000xxxx-0000-1000-8000-00805f9b34fb
    val SERVICE        = UUID.fromString("f0a1c000-1a2b-4c3d-8e9f-0a1b2c3d4e5f")
    val CHAR_MSG_OUT   = UUID.fromString("f0a1c001-1a2b-4c3d-8e9f-0a1b2c3d4e5f") // peripheral -> central
    val CHAR_MSG_IN    = UUID.fromString("f0a1c002-1a2b-4c3d-8e9f-0a1b2c3d4e5f") // central -> peripheral
    val CHAR_NODE_INFO = UUID.fromString("f0a1c003-1a2b-4c3d-8e9f-0a1b2c3d4e5f") // read
    val CHAR_CTRL      = UUID.fromString("f0a1c004-1a2b-4c3d-8e9f-0a1b2c3d4e5f") // write
}
```

### 6.2 Peran dan batasan

Android mengizinkan satu `BluetoothGattServer` per aplikasi. Konsekuensi:

- `GattServer` adalah **peripheral**. `GattClient` adalah **central** dan tidak boleh menjalankan service GATT-nya sendiri.
- Jelly Bean: `connectGatt` bersifat serial. Direkomendasikan maksimum 3 koneksi GATT aktif per perangkat, dengan exponential backoff plus jitter saat connect gagal. Android secara hardware mendukung sampai 7, tetapi banyak chipset consumer gagal di atas 4–5.

### 6.3 Mengapa GATT tetap dipakai jika adv-flood cukup

|Advertising | GATT |
|---|---|
| 9 byte per frame (fragmentasi berat) | hingga 244 byte per write (MTU 247) |
| Rendezvous-free, tetapi collision-prone | Reliable lewat characteristic write + confirm |
| Hanya berisi frame yang di-advertise saat ini | Full-duplex kapan saja |
| RSSI saja | RSSI + latency ping lewat `CHAR_CTRL` |
| Tidak ada ack | Ack native dari `onCharacteristicWrite` |

Untuk KTI, keandalan menentukan apakah demo A → B → C terlihat meyakinkan. Rekomendasi: **route lewat GATT bila peer ada di `GattPeerRegistry`, selain itu adv-flood.**

### 6.4 Flood control

Tanpa kontrol, pesan yang gagal sampai tujuan akan di-advertise ulang selamanya selama TTL. `SeenMessageEntity.forwardCount` membatasi jumlah re-advertise:

```kotlin
val entry = seenDao.find(messageKey) ?: return   // bukan milik kita
if (entry.forwardCount >= MeshConfig.NORMAL_FORWARD_LIMIT) {
    messageDao.markExpired(messageKey)
    return
}
seenDao.incrementForwardCount(messageKey)
```

`NORMAL_FORWARD_LIMIT = 2` berarti satu percobaan inisial plus satu percobaan ulang untuk pesan biasa. Baris di `seen_messages` tetap ada sampai retensi habis, sehingga percobaan berikutnya dari node lain juga ditolak.

---

## 7. Alur Komunikasi A → B → C

### 7.1 Topologi dan asumsi

```text
       [Node A]  ------------  [Node B]  ------------  [Node C]
      NODE-A83F2C             NODE-B19C77             NODE-C72D4A
        origin                  relay                 destination

  Jarak A-B = 8 m, B-C = 10 m.
  A dan C TIDAK saling melihat radio (jarak / dinding).
  Internet: OFF.  Wi-Fi: OFF.  Mode pesawat: ON.
```

### 7.2 Fase 0 — Persiapan

```text
A                                     B                                     C
|                                     |                                     |
| ResQMeshApp.onCreate()             |                                     |
| |- NodeIdentityStore.load()         |                                     |
| |  -> NodeId NODE-A83F2C            |  -> NODE-B19C77      -> NODE-C72D4A
| |- Room.databaseBuilder()           |                                     |
| |- NodeDao.upsert(self, isSelf=1)   |                                     |
|                                     |                                     |
| Permission runtime diminta, granted |                                     |
|                                     |                                     |
| MeshService.startForeground()      |                                     |
| MeshManager.start()                |                                     |
| |- BleScanner.start()               |                                     |
| |   filter companyId 0xE000         |                                     |
| |   SCAN_MODE_BALANCED              |                                     |
| |- GattServer.start(SERVICE)        |                                     |
| |- BeaconScheduler.start()          |                                     |
|   slot 0: BEACON A83F2C seq=1      |---- advert ---------> (B scan)      |
```

Pada fase ini B dan C saling melihat radio. Yang **belum** terjadi: A tidak melihat C, dan belum ada koneksi GATT.

### 7.3 Fase 1 — A menyusun pesan (T+0 ms)

```text
User di A, Screen 3 - Chat
  |- pilih destination = NODE-C72D4A   (dari Nearby, lastSeenAt = 40 detik lalu)
  |- ketik "BUTUH BANTUAN"
  `- tekan SEND
```

```text
ChatViewModel.send()
  |
  `-> MeshManager.submitOutbound(destination = NodeId(C72D4A),
                                  text = "BUTUH BANTUAN", ttl = 5, isSos = false)
        |
        |- 1. id = MessageId.of(selfId, nextSeq++)   ->  A83F2C-000001
        |
        |- 2. payload = "BUTUH BANTUAN".toByteArray(UTF_8)   -> 12 byte
        |
        |- 3. messageDao.upsert(MessageEntity(
        |       messageKey   = id.value,
        |       originNodeId = A83F2C,
        |       destinationId= C72D4A,
        |       peerId       = C72D4A,
        |       payload      = "BUTUH BANTUAN",
        |       payloadBytes = payload,
        |       createdAt    = now,
        |       initialTtl   = 5, ttl = 5, hopCount = 0,
        |       status       = PENDING_FORWARD,
        |       direction    = OUTGOING,
        |       isSos        = false,
        |   ))
        |
        |- 4. seenDao.tryInsert(SeenMessageEntity(id, A83F2C, 1, now, now + 30 mnt, hop = 0))
        |      -> return rowId (bukan -1)  => Fresh
        |
        |- 5. Fragmentasi: 12 byte / 9 = 2 frame
        |      frag 0: bytes[0..8]  "BUTUH BAN"
        |      frag 1: bytes[9..11] "TUAN"
        |
        `- 6. BeaconScheduler.enqueueNormal(frag 0)
           BeaconScheduler.enqueueNormal(frag 1)
```

Checkpoint: baris `MessageEntity` dan `SeenMessageEntity` tertulis **sebelum** frame pertama di-advertise. Kalau proses mati di tengah, Store-and-Forward bisa mengambil alih.

### 7.4 Fase 2 — Broadcast hop-0

```text
BeaconScheduler tick @ T+20 ms  (jitter acak 0-200 ms)
  |- frame = MeshFrame(
  |     messageId  = A83F2C-000001,
  |     origin     = A83F2C,
  |     destination= C72D4A,
  |     ttl        = 5,          <- BELUM dikurangi
  |     hopCount   = 0,          <- A = hop 0
  |     flags      = FRAGMENTED,
  |     fragIndex  = 0, fragCount = 2,
  |     totalLen   = 12, chunk = "BUTUH BAN"
  |  )
  |- advertiser.startAdvertising(codec.encodeMessage(frame))
  `- onStartSuccess  -> slot aktif 300 ms
```

```text
... T+450 ms  -> frame frag 1
... T+900 ms  -> frame BEACON A83F2C seq=5
```

> TTL **tidak** dikurangi oleh A pada frame pertama. `hopCount = 0` menandai A sebagai pengirim asli.

### 7.5 Fase 3 — B menerima fragmen pertama

```text
BleScanner.onScanResult (rssi = -67 dBm)
  |- manufacturerData[0xE000]  -> 27 byte
  |- codec.decode(bytes) -> MeshFrame(frag 0, ttl=5, hop=0, dest=C72D4A)
  |
  `-> MeshManager.onAdvertisedFrame(frame, rssi = -67)
        |
        |- hopDao.insert(MessageHopEntity(
        |      messageKey = A83F2C-000001,
        |      nodeId     = B19C77, hopIndex = 1,
        |      rssi       = -67,
        |      latencyMs  = now - createdAt(A),
        |  ))
        |
        `-> RelayEngine.onFrame(frame, source = Peer.Advertised, rssi = -67, now)
             |
             |- STEP 1  selfId(B19C77) == frame.origin(A83F2C)?  -> tidak
             |
             |- STEP 2  DuplicateGuard.registerAndCheck(A83F2C-000001, hop=0, ttl=5)
             |           -> seenDao.tryInsert(...)  -> return rowId  => Fresh
             |
             |- STEP 3  messageDao.upsert(MessageEntity(
             |      messageKey  = A83F2C-000001,
             |      origin      = A83F2C,
             |      destination = C72D4A,
             |      peerId      = A83F2C,        <- B tidak pernah jadi peer chat
             |      direction   = RELAYED,
             |      ttl = 5, hopCount = 0,
             |      status      = AWAITING_FRAGMENTS,
             |      isReassembly = true,
             |  ))
             |
             |- STEP 4  assembler.accept(frag 0)
             |      pending[A83F2C-000001] = { 0 dari 2 }   -> Incomplete
             |
             `- return RelayDecision.Ignore   <- belum utuh, jangan re-advertise
```

### 7.6 Fase 4 — B meng-reassemble dan meneruskan

```text
Frame frag 1 tiba @ T+680 ms

  STEP 2'  DuplicateGuard.registerAndCheck(A83F2C-000001, hop=0, ttl=5)
            -> seenDao.tryInsert(...) -> return -1  => Repeated(forwardCount = 0)
            => Frame DUPLIKAT, tetapi re-assembly TETAP diproses
               karena fragIndex 1 belum pernah lengkap

  assembler.accept(frag 1)
    pending = { 0 ok, 1 ok }  ->  Complete(payload = "BUTUH BANTUAN")

  finalize(messageId, payload, frame):
    |- if (destination == selfId)  -> Consume              (bukan kasus di sini)
    |- if (destination == origin)  -> Reject(SELF_ORIGIN)  (bukan kasus)
    |
    |- ForwardingPolicy.decide(direction = RELAYED, hasGattLinkTo(C72D4A))
    |     gattPeerRegistry.contains(C72D4A)?  -> belum pada fase ini
    |     -> Route.TIER1_ADVERTISING
    |
    |- TtlPolicy.shouldForward(ttl = 5, hop = 0, initialTtl = 5)  -> true
    |
    |- messageDao.markForwarded(
    |      key      = A83F2C-000001,
    |      status   = IN_TRANSIT,
    |      ttl      = TtlPolicy.nextTtl(5)   = 4,     <- TTL decremented
    |      hopCount = TtlPolicy.nextHop(0)   = 1,     <- B = hop 1
    |  )
    |
    |- seenDao.incrementForwardCount(A83F2C-000001)  -> 1
    |
    |- Re-fragmentasi 12 byte / 9 = 2 frame (payload identik, header berubah)
    |
    `- BeaconScheduler.enqueueNormal(frag 0)
       BeaconScheduler.enqueueNormal(frag 1)
       return RelayDecision.Relayed(frames)
```

#### Dua aturan yang berbeda dari PRD

Aturan ini adalah tempat paling rawan salah implementasi, karena PRD menyatakannya secara ringkas.

**1. TTL decrement hanya saat forward, bukan saat receive.**
PRD §F7 menulis `A → B: TTL 5 → 4` tanpa menyatakan kapan pengurangan terjadi. Jika dikurangi saat *receive*, setiap hop mengurangi 2 (sekali saat A mengirim, sekali saat B menerima) dan jangkauan efektif menyusut separuh.

**2. Duplikat di-drop saat meneruskan, tidak saat menyelesaikan re-assembly.**
PRD §F6 menulis `IF message_id exists THEN DROP`. Diterapkan mentah, frame fragmen kedua akan dibuang dan pesan tidak pernah menjadi utuh. Aturan yang benar:

```text
duplikat  DAN  fragment sudah lengkap  ->  Reject(DUPLICATE), jangan advertise lagi
duplikat  DAN  fragment belum lengkap  ->  tetap proses FragmentAssembler
```

Pemeriksa `prev.forwardCount == 0` pada `DuplicateVerdict.Repeated` membedakan kedua kasus tersebut.

### 7.7 Fase 5 — B broadcast hop-1 (T+750 ms)

```text
BeaconScheduler tick @ T+750 ms
  frame = MeshFrame(
     messageId  = A83F2C-000001,
     origin     = A83F2C,      <- tetap A, bukan B
     destination= C72D4A,
     ttl        = 4,           <- 5 -> 4
     hopCount   = 1,           <- B
     flags      = FRAGMENTED,
     fragIndex  = 0, fragCount = 2, totalLen = 12, chunk = "BUTUH BAN"
  )
```

`origin = A` itu penting: C harus tahu siapa pengirim asli, dan A harus dapat mengenali balasan.

### 7.8 Fase 6 — C menerima dan meng-konsume (T+820 ms)

```text
C: BleScanner.onScanResult (rssi = -71 dBm)
  `-> RelayEngine.onFrame(...)
       |- STEP 1  selfId(C72D4A) == destination(C72D4A)?  -> YA
       |- STEP 2  DuplicateGuard.registerAndCheck            -> Fresh
       |- finalize -> Complete("BUTUH BANTUAN")
       |
       |- messageDao.upsert(MessageEntity(
       |      direction   = INCOMING,
       |      peerId      = A83F2C,
       |      status      = DELIVERED,
       |      deliveredAt = now,
       |      hopCount    = 1,          <- jumlah hop yang benar-benar dilalui
       |      isReassembly= false,
       |  ))
       |
       |- hopDao.insert(nodeId=C72D4A, hopIndex=2, rssi=-71,
       |                latencyMs = now - createdAt(A))
       |
       `- return RelayDecision.Ack(ackFrame(messageId, from=C72D4A, to=BROADCAST))
```

`RelayDecision.Ack` **bukan** `Relayed`. C tidak boleh meng-forward pesan yang tujuannya adalah dia sendiri. Aturan inilah yang mencegah pola C → B → A → B → C.

### 7.9 Fase 7 — ACK kembali

```text
C broadcast ACK frame:
   messageId  = A83F2C-000001
   origin     = C72D4A        <- pada frame ACK, "origin" = pengirim ACK
   destination= BROADCAST
   flags      = IS_ACK
   ttl = 5, hopCount = 0

B menerima ACK @ T+1.10 s
  |- AckTracker.onAck(A83F2C-000001, from = C72D4A)
  |     messageDao.markDelivered(key, now)  -> status = DELIVERED
  |- messageDao.markForwarded(hopCount = 2)
  `- return RelayDecision.Ack(ackFrame.copy(destination = A83F2C))
      -> B re-advertise ACK ke arah A

A menerima ACK @ T+1.40 s
  |- RelayEngine: destination == selfId, frameType == ACK
  |- AckTracker.onAck(A83F2C-000001, from = C72D4A)
  |     messageDao.markAcked(key)  -> status = ACKED
  |- hopDao.insert(nodeId=A83F2C, hopIndex=0, rssi=-58, latencyMs=0)
  `- ChatViewModel observes Flow<MessageEntity> -> bubble berubah
      "BUTUH BANTUAN"  -  Delivered  -  2 hops  -  1.4 dtk
```

ACK dikirim sebagai **broadcast, bukan unicast terbalik**. Topologi mesh asimetris: jalur A → B → C tidak menjamin jalur C → B → A tersedia. Broadcast dengan TTL 5 memastikan ACK sampai ke siapa pun yang menyimpan copy pesan.

### 7.10 Ringkasan timing nominal

```text
t = 0 ms      A menyimpan pesan ke Room
t = +20 ms    A advertise frag 0                (jitter 0-200 ms)
t = +450 ms   A advertise frag 1
t = +670 ms   B menerima frag 0                (rssi -67)
t = +830 ms   B menerima frag 1
t = +850 ms   B assemble, ttl 5->4, hop 0->1
t = +870 ms   B advertise frag 0
t = +1230 ms  B advertise frag 1
t = +1240 ms  C menerima frag 0                (rssi -71)
t = +1420 ms  C menerima frag 1
t = +1425 ms  C assemble -> DELIVERED, hopCount = 1
t = +1430 ms  C broadcast ACK
t = +1650 ms  B menerima ACK -> DELIVERED
t = +1700 ms  B broadcast ACK
t = +1950 ms  A menerima ACK -> ACKED
------------------------------------------------------------
end-to-end latency : ~1.9 detik   (2 hop, SCAN_MODE_BALANCED)
                    ~0.7 detik   (2 hop, SCAN_MODE_LOW_LATENCY saat SOS)
```

Jitter dan repeat count membuat angka aktual selalu lebih besar dari tabel ini. Angka yang dipakai di laporan adalah hasil pengukuran nyata, bukan nilai teoretis ini.

### 7.11 Skenario kegagalan

**F1 — B mati saat relay**

```text
A --- X B          C
```

Pesan di A tetap `PENDING_FORWARD` selama TTL. A melihat node lain (misalnya D) yang belum ada di `seen_messages`, sehingga A masih bisa mencoba ulang sejauh `NORMAL_FORWARD_LIMIT`. Butuh D cukup dekat dengan C. Target M3 terpenuhi bila jalur alternatif tersedia.

**F2 — C menerima frame duplikat dari B dan D**

```text
        B
       / \
      A   D
       \ /
        C
```

C menerima `MSG-001` dari B (T+1.2 s) dan dari D (T+1.45 s). `seenDao.tryInsert` pada frame pertama mengembalikan rowId; pada frame kedua mengembalikan `-1` -> `Reject(DUPLICATE)`. C menampilkan **satu** bubble. Target M4 terpenuhi.

**F3 — TTL habis**

```text
A -> B -> C -> D -> E -> F -> G    (ttl = 5, hop = 0..5)
```

Di E: `hop = 4`, `ttl = 1` -> `shouldForward = true`, re-advertise dengan `ttl = 0`. Di F: `ttl = 0` -> `Reject(TTL_EXPIRED)`, tidak disimpan ke history, tetapi tetap masuk `seen_messages` sampai retensi habis agar tidak memicu flood kedua. Target M5 terpenuhi.

**F4 — C muncul belakangan (Store-and-Forward, P1)**

```text
A --- B           C        (t = 0 .. 30 detik)
```

Saat pesan tiba di B, C **tidak ada** di `NodeDao`. `ForwardingPolicy` melihat destination tidak dikenal -> `RelayDecision.ForwardLater`. B menyimpan dengan `status = PENDING_FORWARD` dan mendaftarkan `destinationId` di watchlist in-memory.

Ketika beacon `C72D4A` pertama terlihat (`NodeDao.applyBeacon` lalu insert), `StoreAndForwardQueue.onNodeDiscovered(C72D4A)` mengambil semua `PENDING_FORWARD` dengan `destinationId = C72D4A`:

```text
1. messageDao.markForwarded(status = IN_TRANSIT, ttl = max(0, ttl-1), hop = hop+1)
2. seenDao.incrementForwardCount(...)
3. re-fragment, BeaconScheduler.enqueueUrgent(...)   dengan flag REPLAY
4. C menerima -> markDelivered()
```

Batas yang harus ditulis di laporan: store-and-forward hanya berlaku untuk **satu hop**. Bila B tidak melihat C, pesan tidak mencari rute lain. Ini P1 dan boleh disederhanakan.

**F5 — Broadcast / SOS**

```text
A --- B --- C
```

`destination = BROADCAST (0xFFFFFF)`. Di B: `destination == selfId`? Tidak. `destination == origin`? Tidak. `shouldForward`? Ya. Di C: pemeriksaan sama, dan C juga broadcast. Hasil: pesan SOS menjangkau semua node dalam TTL — perilaku yang memang dimaksudkan untuk broadcast.

### 7.12 Mengapa tanpa path selection

Tidak ada AODV/DSR di level ini. Alasan:

1. **Arahkan dari A ke C** membutuhkan routing table. Membangun routing table butuh broadcast discovery — persis yang sudah di-*flood* oleh pesan itu sendiri. Overhead ronde pertama sama dengan flooding pesan itu sendiri.
2. **3–5 node, topologi Line.** Flooding hanya menduplikasi `degree-1` per hop, maksimum 4 untuk topologi star. Murah.
3. **Non-goal eksplisit** (PRD §4): "jaringan mesh dengan ratusan perangkat". Pada 100 node, flooding O(N) per pesan menjadi masalah — dan itu akan menjadi bagian *Limitations and Future Work*.

`ForwardingPolicy` sudah diisolasi sebagai titik substitusi, sehingga adopsi IEEE 802.11s HWMP atau GPSR (greedy geographic forwarding) nanti tidak menyentuh bagian lain.

### 7.13 Tabel keputusan routing

```
| Kondisi, berurutan                                          | Aksi                    |
|-------------------------------------------------------------|-------------------------|
| frame.version != 1 atau decode gagal                        | Reject(MALFORMED)       |
| frame.origin == selfId                                      | Reject(SELF_ORIGIN)     |
| frame.flags & IS_ACK, destination == selfId                 | AckTracker.onAck -> Ignore |
| duplikat && fragment belum lengkap                         | proses assembler        |
| duplikat && fragment sudah lengkap                          | Reject(DUPLICATE)       |
| forwardCount >= NORMAL_FORWARD_LIMIT && belum delivered        | Reject(FLOOD_SUPPRESSED)|
| destination == selfId                                       | Consume + Ack           |
| ttl == 0 || hop >= initialTtl                               | Reject(TTL_EXPIRED)     |
| destination tidak pernah terlihat                           | ForwardLater            |
| ttl > 0                                                     | Relayed                 |
| selain itu                                                  | Ignore                  |
```

---

## 8. Repository

`data/repo/DefaultMeshRepository.kt`:

```kotlin
class DefaultMeshRepository(
    private val manager: MeshManager,
    private val nodeDao: NodeDao,
    private val messageDao: MessageDao,
    private val seenDao: SeenMessageDao,
    private val storeAndForward: StoreAndForwardQueue,
    private val clock: TimeProvider,
) : MeshRepository {

    override fun observeSelf(): Flow<Node> =
        nodeDao.observeSelf().map { it?.toDomain() ?: error("Node self tidak ada di DB") }

    override fun observeNeighbors(): Flow<List<Node>> =
        nodeDao.observeNeighbors().map { list -> list.map { it.toDomain() } }

    override fun observeHistory(limit: Int): Flow<List<Message>> =
        messageDao.observeHistory(limit).map { list -> list.map { it.toDomain() } }

    override fun observeConversation(peerId: NodeId, limit: Int): Flow<List<Message>> =
        messageDao.observeConversation(peerId.value, limit)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun sendTo(
        destination: NodeId, text: String, ttl: Int, isSos: Boolean,
    ): MessageId = manager.submitOutbound(destination, text, ttl, isSos)

    override suspend fun broadcastSos(text: String, ttl: Int): MessageId =
        manager.submitOutbound(NodeId.fromHex("FFFFFF"), text, ttl, isSos = true)

    override suspend fun rebroadcastPending(): Int {
        val pending = messageDao.pendingForwards(limit = 16)
        var n = 0
        for (message in pending) {
            val entry = seenDao.find(message.messageKey)
            if (entry != null && entry.forwardCount >= MeshConfig.NORMAL_FORWARD_LIMIT) {
                messageDao.markExpired(message.messageKey)
                continue
            }
            storeAndForward.replay(message)
            n++
        }
        return n
    }

    override suspend fun prune(): Int {
        val now = clock.now()
        val messages = messageDao.purgeOlderThan(now - MeshConfig.HISTORY_RETENTION_MS)
        val seen     = seenDao.purgeExpired(now)
        val nodes    = nodeDao.pruneStale(now - MeshConfig.NODE_STALE_MS)
        return messages + seen + nodes
    }
}
```

`DEFAULT_TTL = 5` ada di `MeshConfig` dan dapat di-override per-pesan dari UI. `SOS_DEFAULT_TTL = 10`. Home screen menyediakan pemilih TTL untuk pengujian T5.

---

## 9. Pemetaan PRD v2 ke Tech Spec

Mengindeks ulang bagian A0.2 dari level fitur ke level bab PRD v2. Kolom Status memakai tiga nilai: **Terpenuhi**, **Sebagian**, **Belum**.

| PRD v2 | Fitur | Implementasi | File utama | Status |
|---|---|---|---|---|
| F1 | Node Identity | `NodeId` 3 byte, SharedPreferences, `isSelf` | `domain/model/NodeId.kt` | Terpenuhi |
| F2 | Device Discovery | konseptual saja; `NoOpMeshTransport` yang aktif | `mesh/MeshManager.kt` (`NoOpMeshTransport`) | Belum |
| F3 | Location Capture | `LocationSource` (LocationManager, tanpa Play Services) | `data/location/LocationSource.kt` | Sebagian |
| F4 | SOS Creation | `SosComposerDialog` -> `submitSos` | `ui/components/SosComposerDialog.kt` | Sebagian |
| F5 | SOS Relay | `RelayEngine` + flood policy | `mesh/RelayEngine.kt` | Sebagian |
| F6 | Duplicate Detection | `DuplicateGuard` + `SeenMessageDao.tryInsert` | `mesh/DuplicateGuard.kt` | Terpenuhi |
| F7 | TTL | `TtlPolicy` saat forward | `mesh/TtlPolicy.kt` | Terpenuhi |
| F8 | Hop Count | bertambah di `RelayEngine.finalize` | `mesh/RelayEngine.kt` | Terpenuhi |
| F9 | SOS Status | `MessageStatus` per pesan | `domain/model/MessageStatus.kt` | Belum |
| F10 | ACK | `RelayEngine.buildAck` + `AckTracker` | `mesh/AckTracker.kt` | Sebagian |
| F11 | Local SOS History | `observeSosIncidents` + `toSosIncidents` | `ui/alerts/AlertsScreen.kt` | Sebagian |
| F12 | Store-and-Forward | status `PENDING_FORWARD`, belum ada scheduler retry | `data/repo/DefaultMeshRepository.kt` | Belum |
| §4 | Fokus SOS, chat diturunkan | Nav SOS-first, chat butuh 2 tap | `ui/ResQMeshNavHost.kt` | Terpenuhi |
| §5 | Non-goals: tanpa live tracking | `LocationSource` hanya `getLastKnownLocation` | `data/location/LocationSource.kt` | Terpenuhi |
| §15 | Data Model (Node.role) | belum ada | - | Belum |
| §15 | Data Model (SOSMessage) | subset field, `timestamp` dan `sender_name` lokal saja | `domain/model/SosIncident.kt` | Sebagian |
| §15 | Data Model (SOSAck) | tanpa `responderId` | `mesh/AckTracker.kt` | Belum |
| §16 | Routing Logic | langkah 1-4 dan 6-7 berjalan; langkah 5 hanya address match | `mesh/RelayEngine.kt` | Sebagian |
| §17 | Strategi Penyebaran | broadcast multi-node + TTL + dedupe | `mesh/BeaconScheduler.kt` | Terpenuhi |
| §18 | Success Metrics | `MessageHopEntity` ada, belum ada query metrik | A10.3 | Belum |
| §19 | Skenario Pengujian T1-T8 | unit test JVM | `test/` | Sebagian |
| §20 | Roadmap Sprint 1-7 | lihat A12 | - | Sebagian |
| §22 | Packet Tracer | di luar repo Android | - | Terpisah |
| §24 | Prinsip Desain | SOS First, Source Preserved, Duplicate Safe, TTL Controlled | - | Terpenuhi |

---

## 10. Testing

### 10.1 Unit test (JVM, tanpa perangkat)

```text
app/src/test/java/com/resqmesh/
|- codec/FrameCodecTest.kt         round-trip semua tipe frame, truncasi, versi salah
|- codec/ByteCodecTest.kt          batas u8/u16/u24/u32 dan overflow
|- mesh/TtlPolicyTest.kt           matriks (ttl, hop, initialTtl) x expected
|- mesh/DuplicateGuardTest.kt      Room in-memory, insert ganda -> Repeated
|- mesh/FragmentAssemblerTest.kt   out-of-order, frag hilang, timeout, frag duplikat
|- mesh/RelayEngineTest.kt         7 skenario: direct/multi-hop/ttl/dupe/self/broadcast/flood
|- db/MessageDaoTest.kt            transisi status, purge, query index
`- model/MessageIdTest.kt          komposisi, overflow seq, hex round-trip
```

`RelayEngineTest` yang paling bernilai untuk laporan — ini bukti bahwa §13 routing logic benar **sebelum** ada perangkat.

```kotlin
@Test
fun `multi hop A B C relay dengan ttl turun dan hop naik`() = runTest {
    val engine = buildEngine(self = NodeId(0xB19C77))
    val frame = frame(
        origin = NodeId(0xA83F2C), destination = NodeId(0xC72D4A),
        ttl = 5, hop = 0, payload = "BUTUH BANTUAN".toByteArray(),
    )

    val decision = engine.onFrame(frame, Peer.Advertised, -67, now = 0L)

    assertIs<RelayDecision.Relayed>(decision)
    assertEquals(4, decision.frames.first().ttl)         // 5 -> 4
    assertEquals(1, decision.frames.first().hopCount)    // 0 -> 1
    assertEquals(NodeId(0xA83F2C), decision.frames.first().origin)  // tetap A
}

@Test
fun `destination tidak meneruskan`() = runTest {
    val engine = buildEngine(self = NodeId(0xC72D4A))
    val frame = frame(destination = NodeId(0xC72D4A), ttl = 5, hop = 0, payload = "X".toByteArray())
    val decision = engine.onFrame(frame, Peer.Advertised, -71, now = 0L)
    assertIs<RelayDecision.Ack>(decision)
}

@Test
fun `ttl nol ditolak dan tidak disimpan`() = runTest {
    val engine = buildEngine(self = NodeId(0xD11AFF))
    val frame = frame(destination = NodeId(0xA83F2C), ttl = 0, hop = 5, payload = "X".toByteArray())
    assertEquals(
        RelayDecision.Reject(RejectReason.TTL_EXPIRED),
        engine.onFrame(frame, Peer.Advertised, -80, now = 0L),
    )
}

@Test
fun `fragmen kedua duplikat tetap menyelesaikan re-assembly`() = runTest {
    val engine = buildEngine(self = NodeId(0xB19C77))
    val head = frame(destination = NodeId(0xC72D4A), ttl = 5, hop = 0,
                     fragIndex = 0, fragCount = 2, payload = "BUTUH BAN".toByteArray())
    val tail = frame(destination = NodeId(0xC72D4A), ttl = 5, hop = 0,
                     fragIndex = 1, fragCount = 2, payload = "TUAN".toByteArray())

    engine.onFrame(head, Peer.Advertised, -67, now = 0L)
    val second = engine.onFrame(tail, Peer.Advertised, -67, now = 100L)

    assertIs<RelayDecision.Relayed>(second)
    assertEquals("BUTUH BANTUAN", String(assemble(second.frames)))
}

@Test
fun `broadcast tidak pernah di-stop sebelum ttl habis`() = runTest {
    val engine = buildEngine(self = NodeId(0xC72D4A))
    val frame = frame(destination = NodeId.fromHex("FFFFFF"), ttl = 3, hop = 1, payload = "SOS".toByteArray())
    assertIs<RelayDecision.Relayed>(engine.onFrame(frame, Peer.Advertised, -71, now = 0L))
}
```

### 10.2 Pengujian manual dengan perangkat

Test otomatis multi-perangkat membutuhkan 3 HP fisik plus orkestrasi ADB. Untuk KTI, hybrid lebih pragmatis: **unit test untuk logika, skrip manual yang direkam untuk pengukuran.**

| Test | Topologi | Cara ukur | Otomatis? |
|---|---|---|---|
| T1 | A -> B | 50x kirim, hitung `status = 'ACKED'` | manual + query SQL |
| T2 | A -> B -> C | 20x kirim, `hopCount = 1`, `deliveredAt` terisi | manual |
| T3 | A -> B -> C -> D | 10x kirim, `hopCount = 2` | manual |
| T4 | A -> B -> C, matikan B | craft pesan TTL tinggi, lihat apakah D deliver | manual |
| T5 | A -> B -> A | 1x kirim, pastikan `forwardCount <= 2` | unit + manual |

### 10.3 Query metrik

```sql
-- M1 Delivery rate
SELECT
    COUNT(*) AS total,
    SUM(CASE WHEN status IN ('DELIVERED','ACKED') THEN 1 ELSE 0 END) AS delivered
FROM MessageEntity
WHERE direction = 'OUTGOING'
  AND isReplay = 0
  AND createdAt BETWEEN :t0 AND :t1;

-- M2 Rata-rata hop count pesan unicast yang sukses
SELECT AVG(hopCount) AS avg_hops
FROM MessageEntity
WHERE direction = 'OUTGOING'
  AND destinationId != 16777215
  AND status IN ('DELIVERED','ACKED');

-- M2 End-to-end latency (ms)
SELECT AVG(deliveredAt - createdAt) AS avg_latency_ms
FROM MessageEntity
WHERE status IN ('DELIVERED','ACKED')
  AND deliveredAt IS NOT NULL;

-- M4 Deteksi duplikasi (harus kosong)
SELECT messageKey, COUNT(*) AS n
FROM MessageEntity
WHERE isReassembly = 0
GROUP BY messageKey
HAVING n > 1;

-- M5 TTL dihormati (harus 0)
SELECT COUNT(*) FROM SeenMessageEntity WHERE hopCount > 12;

-- Rekonstruksi path satu pesan
SELECT mh.hopIndex, nh.displayName, mh.rssi, mh.latencyMs
FROM MessageHopEntity mh
JOIN NodeEntity nh ON nh.nodeId = mh.nodeId
WHERE mh.messageKey = :key
ORDER BY mh.hopIndex;
```

Filter `isReplay = 0` pada query M1 itu wajib. Tanpa filter itu, pesan store-and-forward yang tiba 30 detik kemudian ikut dihitung dan membuat delivery rate tampak lebih baik dari kenyataan.

Ekspor hasil query sebagai tabel di laporan — ini yang mengubah "semoga berhasil" menjadi angka.

---

## 11. Risiko Teknis dan Mitigasi

| # | Risiko | Dampak | Mitigasi |
|---|---|---|---|
| R1 | **Collision advertising.** 5 device advertising bersamaan menyebabkan packet loss tinggi | Delivery rate bisa < 60% | Jitter acak, repeat 2–3x per frame, duty cycling, dan prioritaskan GATT bila peer punya link. Diterima karena delivery rate adalah metrik **yang diukur**, bukan diasumsikan. |
| R2 | **Android membatasi satu advertiser per aplikasi** | Broadcast rate terbatas | Serial queue (`BeaconScheduler`) dengan fragmen mengalir berurutan. |
| R3 | **Throttle scan di background (Oreo+)** | Scan mati setelah 5x / 30 menit | `BleScanner` idempotent, `start()` mengecek `isScanning` lebih dulu. Scan dijalankan di dalam FGS. |
| R4 | **Baterai habis saat scan/SOS** | Gagal di momen paling kritis | `SCAN_MODE_BALANCED` default, `LOW_LATENCY` hanya saat SOS aktif. Baterai sudah masuk skema DB. |
| R5 | **GATT tidak simetris.** A terhubung B, tapi B tidak terhubung A pada saat bersamaan | Pesan besar dan ACK bisa gagal | GATT adalah optimasi *best-effort*; adv-flood selalu tersedia. ACK selalu broadcast. |
| R6 | **NodeId bentrok** bila app di-backup ke dua perangkat | Routing rusak total | `allowBackup="false"`. Validasi `count(isSelf) == 1` saat startup. |
| R7 | **Fragmen hilang** karena packet loss | Pesan menggantung di `AWAITING_FRAGMENTS` | `ASSEMBLY_TIMEOUT_MS = 10s` untuk evict, dan pengirim meng-advertise ulang frame yang hilang sampai `NORMAL_FORWARD_LIMIT`. |
| R8 | **Android 14 memperketat FGS** | Crash saat start service | `foregroundServiceType="connectedDevice"`, permission `FOREGROUND_SERVICE_CONNECTED_DEVICE`, dan runtime permission dicek sebelum `startForegroundService`. |
| R9 | **OEM agresif mematikan background** (Xiaomi, Oppo) | Mesh mati di luar demo | FGS + `START_STICKY` + `BootReceiver`, plus dokumentasi whitelist battery optimization untuk penguji. Ini masalah UX, bukan protokol. |
| R10 | **Scope creep ke protokol produksi** (6LoWPAN, IPv6, NDN) | Proyek melebar | PRD §4 sudah menyebut enkripsi produksi dan mesh skala besar sebagai non-goal. Tetap di BLE mesh. |

---

## 12. Definition of Done per Sprint

| Sprint | Selesai bila |
|---|---|
| 1 — Basic App | Proyek build, Node ID tampil di Home, permission flow jalan |
| 2 — BLE | `BleScanner` melihat beacon peer, `NodeDao` terisi, layar Nearby listing node dengan RSSI |
| 3 — Messaging | Direct message A -> B terkirim, diterima, muncul di history kedua perangkat |
| 4 — Mesh | A -> B -> C lolos dengan `hopCount = 1`; T5 tidak loop |
| 5 — Emergency | SOS menyala dalam 5 detik, broadcast sampai 2 hop, store-and-forward Opsional |
| 6 — Research | Tabel delivery rate, latency, hop count, duplikasi, dan node failure terisi dari query §10.3 |

---

## 13. Appendeks — Referensi Nilai

```
COMPANY_ID          = 0xE000   -> ganti dengan ID terdaftar Bluetooth SIG
PROTOCOL_VERSION    = 0x01
BEACON frame        = 27 byte
MSG frame           = 27 byte, payload maks 9 byte
MAX payload         = 255 byte  (totalPayloadLen u8)
DEFAULT_TTL         = 5
SOS_DEFAULT_TTL     = 10
MAX_TTL             = 12
SEEN_RETENTION_MS   = 1_800_000     (30 menit)
ASSEMBLY_TIMEOUT_MS = 10_000
NODE_STALE_MS       = 90_000
NORMAL_FORWARD_LIMIT    = 2
SOS_FLOOD_FORWARD_LIMIT = 1
MAX_GATT_PEERS      = 3
BROADCAST NodeId    = 0xFFFFFF
COORD_SCALE         = 10_000
SOS_LOC_BLOB_BYTES  = 16
SOS_DETAIL_HEADER_BYTES = 7
SOS_TEXT_MAX_BYTES  = 180
LOCATION_MAX_AGE_SEC = 120
SOS_NEARBY_METERS   = 150.0
```

---

## 14. Protokol SOS Darurat

Bab ini menjawab PRD v2 bagian 8 (SOS Packet) dan 12 (layar SOS). PRD v2 menetapkan
SOS terstruktur berisi lokasi dan kondisi korban, bukan teks bebas. Versi pertama PRD
hanya mengirim teks `I NEED HELP`, yang tidak memberi penolong informasi apa pun untuk
bertindak. Bab ini menambah payload terstruktur berisi titik koordinat, kondisi korban, dan
catatan.

### 14.1 Keputusan

| # | Keputusan | Alasan |
|---|---|---|
| D5 | **SOS dipecah jadi dua pesan terantai**, bukan satu payload ter-fragmentasi | Kalau koordinat dan catatan digabung dalam satu payload 187 byte, penerima baru bisa menampilkan apa pun setelah fragmen ke-21 tiba. Dengan pemisahan, lokasi tampil setelah fragmen LOC kedua, sekitar 0,6–1,0 detik. |
| D6 | **`LocationManager`, bukan FusedLocationProviderClient** | Perangkat tim rescue tidak selalu lolos peninjauan Play Store, dan Place API beresolusi tinggi tidak tersedia di sana. |
| D7 | **Intent peta eksternal** (`geo:`), tanpa SDK peta | Menyeret pengguna ke Play Store saat sedang darurat memastikan tidak ada yang bisa menampilkan peta. |
| D8 | **Flood tanpa bias hop, batas satu penerusan per node** | Chat memakai bias "maju ke hop terkecil saja" supaya hemat duty cycle. Untuk SOS, node yang hanya bisa dicapai lewat jalur panjang justru yang paling butuh diberi tahu, jadi bias hop dimatikan. |

### 14.2 Flag baru

Bit yang tersisa pada field `flags` u8:

| Bit | Nama | Arti |
|---|---|---|
| `0x40` | `SOS_PAYLOAD` | Payload adalah blob terstruktur, bukan teks UTF-8. |
| `0x80` | `SOS_LOC` | Fragmen koordinat. Pada payload SOS yang memiliki `SOS_PAYLOAD`, jika bit ini tidak disetel maka payload merupakan `SOS_DETAIL`. |

Tanpa `SOS_PAYLOAD`, payload ditafsirkan sebagai teks UTF-8 seperti sebelumnya,
sehingga node versi lama tetap bisa membaca pesan chat biasa.

### 14.3 Blob SOS_LOC (16 byte, dua frame 9 + 7)

```
offset  size  field        keterangan
0       1     sosKind      enum SosKind, lihat A14.6
1       1     caps         bit0 ada accuracy, bit1 ada fixAge, bit2 ada battery
2       3     latE4        s24, latitude * 10000
5       3     lonE4        s24, longitude * 10000
8       2     accuracyM    u16, meter
10      1     fixAgeSec    u8, detik, 0xFF bila tidak diketahui
11      2     sentAgeMin   u16, menit sejak dikirim, wraparound 45 hari
13      1     batteryPct   u8, 0..100, 0xFF bila tidak diketahui
14      1     victimCount  u8, 0 berarti pengirim sendiri
15      1     hazards      u8, bitmask SosHazard
```

Longitude maksimum 180 * 10000 = 1.800.000, masih di dalam rentang s24
(-8.388.608..8.388.607). Presisi 1/10000 derajat sekitar 11 meter pada
lintang, 1,1 meter pada khatulistiwa.

Panjang 16 byte dipilih supaya persis pecah menjadi dua frame. Kalau 15 byte,
pecah jadi 9 + 6 dan jumlah frame tetap dua; kalau 18 byte, jadi 9 + 9 dan
membuang satu frame precious untuk satu byte.

### 14.4 Blob SOS_DETAIL (7 byte header + teks)

```
offset  size  field       keterangan
0       3     refSeq      seq pesan SOS_LOC yang dirujuk
3       3     refOrigin   NodeId pengirim SOS_LOC
6       1     refSosKind  ikut dikirim agar klasifikasi tidak ikut menunggu
7       ...   text        UTF-8, dipotong pada SOS_TEXT_MAX_BYTES
```

Header 7 byte berarti teks mulai pada fragmen pertama. Dengan
`SOS_TEXT_MAX_BYTES = 180`, payload total 187 byte, atau 21 frame.

### 14.5 Urutan kirim dan penggabungan

`incidentId` adalah `MessageId` pesan SOS_LOC. Baris `SOS_DETAIL` menyimpan
`incidentKey = incidentId`, sedangkan baris SOS_LOC memakai `messageKey` sendiri.
`Mappers.toSosIncidents()` mengelompokkan berdasarkan `incidentKey`, jadi:

- LOC lalu DETAIL: satu kartu lengkap.
- DETAIL lalu LOC: kartu muncul tanpa pin, lalu pin menyusul.
- Hanya LOC: kartu muncul dengan koordinat dan status "keterangan menyusul".

`refSosKind` ada justru supaya kasus ketiga tidak perlu menunggu: jenis
darurat tetap diketahui walau SOS_LOC belum tiba.

### 14.6 Enum dan bitmask

```
SosKind:   UNKNOWN=0 MEDICAL=1 FIRE=2 ACCIDENT=3 FLOOD=4 SECURITY=5 TRAPPED=6 OTHER=7

SosHazard: CONSCIOUS=0x01 BLEEDING=0x02 TRAPPED=0x04
           FIRE=0x08 HAZARD_AREA=0x10 NEEDS_EVAC=0x20
```

`UNKNOWN` bernilai 0 supaya versi baru tetap bisa dibaca versi lama. Nilai
enum hanya boleh ditambah di akhir.

### 14.7 Flood policy

| Jenis | Batas forward | Bias hop | TTL awal |
|---|---|---|---|
| Chat | `NORMAL_FORWARD_LIMIT = 2` | Ya, maju ke hop terkecil saja | `DEFAULT_TTL = 5` |
| SOS | `SOS_FLOOD_FORWARD_LIMIT = 1` | Tidak | `SOS_DEFAULT_TTL = 10` |

Batas SOS ada supaya advertising tidak membanjir, dan bias hop dimatikan karena
menyingkirkan jalur panjang akan membuat pesan hilang tepat di node yang paling
jauh. TTL 10 memberi jangkauan sampai 10 hop, cukup untuk menjangkau seluruh
rantai antar RW dan RT.

### 14.8 Perbaikan yang menyertai

Tiga bug ditemukan saat mengimplementasikan bab ini:

1. **`RelayQueue` kehilangan fragmen.** Penyimpanan urgent memakai latest-wins
   satu frame, sehingga fragmen ke-2 SOS_LOC menimpa fragmen ke-1 dan
   koordinat tidak pernah sampai. Diganti antrean dengan dedupe per
   `(messageId, fragIndex)`.
2. **ACK tidak pernah sampai ke pengirim.** `buildAck` memakai
   `destination = BROADCAST`, sementara node perantara membuang semua ACK
   broadcast. Pengirim tidak pernah tahu pesannya sampai. Kondisi discard
   `destination.isBroadcast` pada cabang ACK dihapus.
3. **`MeshManager.replay` menghilangkan flag struktural.** Flag
   `SOS_PAYLOAD`/`SOS_LOC` hilang saat replay, sehingga node penerima membaca
   blob koordinat sebagai teks UTF-8. Flag struktural sekarang ikut di-replay.

### 14.9 Lokasi

`LocationSource` memakai `getLastKnownLocation` pada provider GPS lalu jaringan,
dan membuang fix yang umurnya lebih dari `LOCATION_MAX_AGE_SEC`. Fix basi
ditolak karena lebih berbahaya daripada tidak ada pin: penolong akan datang ke
tempat yang salah dengan yakin.

Izin `ACCESS_FINE_LOCATION` sengaja **tidak** diberi `maxSdkVersion="30"`.
Batas tersebut membuat koordinat mustahil dikirim di Android 12 ke atas,
padahal justru perangkat rescue modern yang paling membutuhkan pin.

`BLUETOOTH_SCAN` tetap `neverForLocation` karena lokasi tidak pernah diturunkan
dari hasil scan BLE. Pemisahan ini membuat mesh tetap berjalan meski operator
menolak izin lokasi: SOS masih menyiarkan jenis dan catatan, hanya tanpa pin.
