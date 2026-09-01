<div align="center">

# Casino Engine

**Inti agregator game sumber terbuka untuk platform iGaming.**

Mesin mekanika game yang menggerakkan kasino di [1638.cloud](https://1638.cloud) —
dirilis di bawah lisensi Apache 2.0 sehingga operator mana pun bisa mengaudit, fork, atau menaruhnya langsung ke
stack yang sudah ada.

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-D4AF37.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF.svg)](https://kotlinlang.org)
[![JVM](https://img.shields.io/badge/JVM-21-0A2818.svg)](https://openjdk.org)
[![Built in Malta](https://img.shields.io/badge/Built%20in-Malta-D4AF37.svg)](https://1638.cloud)

[Website](https://1638.cloud) · [Live di moonbet.casino](https://moonbet.casino) · [Live di 2k.ua](https://2k.ua) · [Laporkan masalah](https://github.com/nekzabirov/IGaming-Game-Engine/issues/new/choose)

</div>

---

## Apa ini

Casino Engine adalah microservice Kotlin kelas produksi yang menangani
**sisi game dari sebuah platform iGaming**: integrasi provider/aggregator,
orkestrasi sesi, logika taruhan, mekanika freespin, dan siklus
putaran (round). Engine ini berjalan di produksi pada [moonbet.casino](https://moonbet.casino)
dan [2k.ua](https://2k.ua).

Ini adalah **satu-satunya modul sumber terbuka** dari platform [1638.cloud](https://1638.cloud) —
tujuh engine lainnya(PAM, Wallet, Payment, Risk, Engagement,
Intelligence, CMS) bersifat proprietary. Operator bisa memakai Casino Engine berdiri sendiri
lewat adaptor kustom, atau menjalankannya sebagai bagian dari platform white-label
1638.cloud yang lengkap.

> **Kenapa sumber terbuka??** Operator berhak melihat kode yang menangani
> aliran uang mereka. Integrasi aggregator harus bisa diaudit. Logika sesi game.
> tidak boleh menjadi kotak hitam. Setiap baris ada di GitHub.

---

## Untuk siapa ini

| Kamu... | Yang kamu dapat |
| --- | --- |
| **Operator kasino** yang menjalankan platform sendiri | Lapisan integrasi aggregator kelas produksi yang bisa langsung kamu pasang ke stack-mu. Tanpa vendor lock-in, tanpa bagi hasil, tanpa biaya lisensi. |
| **Engineer platform** yang mengevaluasi infrastruktur iGaming | Implementasi referensi mekanika sesi, bet, round, dan freespin dalam Kotlin/Ktor dengan arsitektur hexagonal. |
| **Startup** yang berencana meluncurkan kasino | Cara untuk memvalidasi model teknis sebelum berkomitmen. Saat kamu siap go-live, [1638.cloud](https://1638.cloud) mengurus lisensi, pembayaran, KYC, dan sisanya. |

---

## Fitur

- **Integrasi aggregator** — Pragmatic Play, OneGameHub, Pateplay, dengan
  pola yang terdokumentasi untuk menambah yang lain.

- **Siklus taruhan lengkap** — PLACE, SETTLE, ROLLBACK dengan jaminan
  idempotensi
- **Mekanika freespin** — pengambilan preset, pembuatan, pembatalan
- **Manajemen round** — pembuatan bet pertama, penggunaan ulang round multi-bet via
  external ID, siklus selesai
- **Event-driven** — publisher RabbitMQ untuk event sesi, spin, round, dan game
- **Arsitektur hexagonal** — pemisahan bersih antara domain, application,
  infrastructure
- **API gRPC** — protokol `game.v1` dengan 5 service dan JAR klien yang dipublikasikan
- **Adaptor pluggable** — port Wallet, PlayerLimit, File, Currency, Event
  yang kamu implementasikan untuk stack-mu
- **Teruji di produksi** — berjalan di moonbet.casino dan 2k.ua

---

## Mulai cepat

```bash
# 1. Klone
git clone https://github.com/nekzabirov/IGaming-Game-Engine.git
cd IGaming-Game-Engine

# 2. Jalankan infrastruktur
docker-compose up -d postgres rabbitmq redis minio minio-init

# 3. Konfigurasi
cp .env.example .env

# 4. Jalankan
./gradlew run                  # HTTP :8080, gRPC :5050
```

Panduan setup lengkap, panduan integrasi, dan dokumentasi operasional ada di [`docs/`](./docs) — lihat bagian [Dokumentasi](#documentation) di bawah.

---

## Tech stack

| Komponen | Pilihan |
| --- | --- |
| Bahasa | Kotlin 2.0.21 di JVM 21 |
| Server HTTP | Ktor 3.0 (CIO) |
| RPC | gRPC + Protobuf |
| Database | PostgreSQL via Exposed ORM |
| Messaging | RabbitMQ |
| Cache | Redis (Lettuce) |
| Object storage | Kompatibel S3 (MinIO lokal) |
| DI | Koin |
| Build | Gradle (Kotlin DSL) |

Arsitektur: hexagonal (ports & adapters) dengan CQRS.

---

## Dokumentasi

Dokumentasi teknis berada di [`docs/`](./docs):

- [Arsitektur](./docs/ARCHITECTURE.md) — desain sistem, lapisan, struktur sumber, alur event
- [Integrasi](./docs/INTEGRATIONS.md) — aggregator yang didukung dan cara menambah yang baru
- [Adaptor](./docs/ADAPTERS.md) — adaptor wajib (Wallet, PlayerLimit, File, Event, Currency) yang kamu implementasikan untuk produksi
- [API](./docs/API.md) — referensi API gRPC (`game.v1` package)
- [Konfigurasi](./docs/CONFIGURATION.md) — variabel lingkungan dan infrastruktur
- [Error](./docs/ERRORS.md) — hierarki exception domain dan pemetaan status gRPC

---

## Gambaran besar — platform 1638.cloud

Casino Engine adalah satu dari delapan engine yang membentuk platform iGaming
[1638.cloud](https://1638.cloud):

| # | Engine | Status |
| --- | --- | --- |
| 01 | **Casino Engine** — mekanika game, integrasi provider | **Sumber terbuka** (repo ini) |
| 02 | PAM Engine — akun pemain, KYC, siklus hidup | Proprietary |
| 03 | Wallet Engine — multi-mata uang, saldo real-time | Proprietary |
| 04 | Payment Engine — 76+ provider, fiat & kripto | Proprietary |
| 05 | Risk Engine — anti-penipuan, AML, skor perilaku | Proprietary |
| 06 | Engagement Engine — bonus, turnamen, loyalitas | Proprietary |
| 07 | Intelligence Engine — segmentasi, churn, LTV | Proprietary |
| 08 | CMS Engine — konten, theming, lokalisasi | Proprietary |

Operator bisa memakai seluruh stack sebagai deployment white-label atau turnkey
di bawah lisensi master [Anjouan](https://1638.cloud) kami, atau menaruh masing-masing
engine ke platform yang sudah ada. Casino Engine bekerja di kedua mode tersebut.

**Go-live sejak kontrak:** 7 hari. **Setup:** mulai €0 dengan Founders Circle.
Lihat [1638.cloud](https://1638.cloud) untuk syarat lengkap.

---

## Mau pakai ini di produksi?

Ada tiga jalur:

**1. Jalankan sendiri (gratis, Apache 2.0).**
Implementasikan adaptornya, hosting infrastrukturnya, integrasikan dengan sistem
wallet dan pemain milikmu. Kodenya milikmu. Kami tidak memungut biaya, tidak
mengaudit, tidak menghalangi.

**2. Pakai Casino Engine di platform 1638.cloud.**
Masuk ke managed stack kami — kami yang menjalankan infrastrukturnya, kamu tetap
punya fleksibilitas. Berguna saat kamu menginginkan engine sumber terbuka tetapi tanpa
beban urusan operasional.

**3. White-label penuh di 1638.cloud.**
Casino Engine plus tujuh engine lainnya, plus lisensi master Anjouan kami.
Go-live dalam 7 hari. Setup mulai €0. Hubungi
[customer@1638.cloud](mailto:customer@1638.cloud.

---

## Kontribusi

Issue, diskusi, dan pull request sangat diterima. Sebelum berkontribusi,
silakan baca [`CONTRIBUTING.md`](./CONTRIBUTING.md.

Kami sangat tertarik pada:
- Adaptor integrasi aggregator baru
- Laporan bug dari deployment produksi
- Benchmark performa dan laporan profiling
- Perbaikan dokumentasi

---

## Keamanan

Ini adalah infrastruktur keuangan. Jika kamu menemukan masalah keamanan, **jangan
buka issue publik** — baca [`SECURITY.md`](./SECURITY.md) untuk pengungkapan yang
bertanggung jawab.

---

## Lisensi

Apache 2.0 — lihat [`LICENSE`](./LICENSE.

Kamu boleh memakai Casino Engine secara komersial, memodifikasinya, mendistribusikannya, dan menjalankannya
di platform operator mana pun tanpa membayar royalti ke 1638.cloud. Satu-satunya

syarat adalah atribusi dan tidak memakai merek dagang 1638.cloud
tanpa izin.

---

## Tentang 1638.cloud

[1638.cloud](https://1638.cloud) adalah platform iGaming B2B yang dibangun di Malta.
Kami menyediakan infrastruktur kasino white-label dan turnkey di bawah
lisensi master Anjouan kami. Kami membuka sumber kode Casino Engine karena mekanika game.
tidak seharusnya menjadi kotak hitam — dan karena cara terbaik untuk mendapatkan
kepercayaan operator adalah membiarkan mereka membaca kodenya lebih dulu.

**Founder:** [Nekbakht Zabirov](https://github.com/nekzabirov)
**Email:** [customer@1638.cloud](mailto:customer@1638.cloud)
**Web:** [1638.cloud](https://1638.cloud)

<div align="center">

— Dibuat untuk operator yang bergerak lebih dulu —

</div>