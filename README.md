# OctaTycoon

**Version:** `0.1-2026-DEV-1`  
**Authors:** Alvran dan RafyRafi  
**Module:** F0 foundation + F1–F8 first engine layer

OctaTycoon adalah port native Java dari sistem tycoon / idle-RPG Octanexus. Repositori ini memiliki fondasi F0, runtime pertama untuk progression/farming/mining, dan engine awal F1–F8. Banyak integrasi gameplay, konten, GUI, NPC, dan validasi server masih bertahap; lihat `docs/F1-F8-IMPLEMENTATION-STATUS.md`.

## Kompatibilitas dan build

- Target awal: Paper `1.21.11` API
- Java: JDK 21
- Build system: **Apache Maven** (`pom.xml`)
- SQLite JDBC di-shade ke dalam plugin
- Paper API menggunakan scope `provided` dan tidak dimasukkan ke JAR

Spesifikasi awal di `docs/ORIGINAL-SPEC.md` menyebut Gradle, tetapi build system proyek ini sudah dialihkan ke Maven sesuai permintaan. Penggantian build tool tidak mengubah fitur atau performa plugin ketika berjalan di server.

### Build di Windows

Pastikan JDK 21 dan Apache Maven sudah terpasang dan tersedia di `PATH`. Buka PowerShell di folder yang berisi `pom.xml`, lalu jalankan:

```powershell
mvn clean verify
```

Untuk membuat JAR tanpa menjalankan test:

```powershell
mvn clean package -DskipTests
```

Hasil plugin:

```text
target/OctaTycoon-0.1-2026-DEV-1.jar
```

### Build melalui GitHub

Workflow `.github/workflows/build.yml` menjalankan Maven otomatis saat ada push atau pull request ke `main`, `master`, atau `develop`. Untuk mengunduh JAR, buka repository → **Actions** → pilih run terbaru → unduh artifact `OctaTycoon-<commit-sha>`.

Panduan lengkap instalasi Maven, build lokal, upload source, dan download Artifact ada di `docs/GITHUB-MAVEN.md`.

Build pertama membutuhkan akses internet untuk mengambil Paper API, SQLite JDBC, JUnit, dan plugin Maven dari repository masing-masing.

## Fitur fondasi yang sudah disiapkan

### Infrastruktur
- Config `config.yml` dan bahasa `lang/id.yml` dengan parser warna hex `&#RRGGBB`.
- SQLite dengan WAL, satu antrean executor khusus database, dan operasi database asynchronous.
- Skema `players`, `player_stats`, `player_data`, `pending_rewards`, `audit_events`, `eco_daily`, serta `schema_version`.
- Cache pemain dengan dirty flag untuk stat dan data modul; flush berkala, saat quit, dan saat shutdown; GC cache offline.
- Cap stat per kategori dan cap global yang dapat diubah di konfigurasi.

### Reward dan ekonomi
- Satu pintu reward: `PayService.pay(UUID, RewardType, BigDecimal, source)`.
- Batas saldo default `9_000_000_000_000_000`.
- Multiplier provider dapat ditambahkan oleh modul lain.
- Hadiah offline disimpan dalam `pending_rewards`, dibatasi 100 entri per pemain.
- Pengiriman hadiah Token tertunda menggunakan transaksi database yang sekaligus menghapus antrean, untuk mencegah reward terbayar dua kali akibat crash di tengah proses.
- `RewardBackend` adalah extension point untuk Vault, ExcellentEconomy, dan jenis reward lain.
- Ringkasan faucet/sink harian dicatat di `eco_daily`.

**Catatan:** pada F0 ini, backend saldo native yang sudah aktif adalah Token. Adapter Vault dan ExcellentEconomy belum dihubungkan. Reward selain Token akan tetap tersimpan sebagai pending sampai adapter untuk jenis reward tersebut didaftarkan.

### Tick Bus dan performa
- Satu task pusat dengan interval default 5 detik.
- Prioritas P1/P2/P3, interval tiap job, dan API `slicePlayers(...)` untuk pemrosesan bertahap.
- TPS Guard: GREEN, YELLOW, ORANGE, RED. Penurunan level dilakukan satu tingkat setelah 60 detik TPS membaik; kenaikan langsung.
- Guard ORANGE membagi batch dua; Guard RED menghentikan job P2/P3.
- Profiler job: waktu terakhir, maksimum 60 detik, serta jumlah lonjakan di atas 5 ms dan 15 ms.
- Freeze manager untuk `hoe`, `pickaxe`, `gens`, dan `all`.
- Job batas item drop dijalankan per-slice dan hanya menghapus item yang tidak terikat.

### GUI, notifikasi, dan keamanan
- Chest GUI dengan `open(...)`, `set(...)`, action PDC, dan router `module:command:arg`.
- Click cooldown bertingkat; drag dan klik inventori bawah dibatalkan di GUI sistem.
- Mode notifikasi NORMAL, COMPACT, dan QUIET; error tetap lolos dalam mode senyap.
- Item terikat menggunakan PDC `octatycoon:item_id` dan `octatycoon:owner_uuid`.
- Proteksi transfer ke container (termasuk number-key/offhand swap), crafting, anvil/merchant/smithing/furnace, creative clone, drop, hopper, inventory pickup, armor stand, dan item frame. Item bound wajib memiliki owner UUID; data owner yang hilang ditolak (fail-closed).
- Item terikat yang ada di daftar drop saat pemain mati dipulihkan ke inventory setelah respawn.
- Generator yang kelak diberi PDC `octatycoon:gens_owner` dilindungi dari piston dan api.
- Audit ring buffer dan file `kuy-audit.log`.

## Command F0

Semua command admin menggunakan permission `octatycoon.admin.core`.

| Command | Fungsi |
|---|---|
| `/kuy` | Bantuan command core |
| `/kuyguard [bersihkan]` | Alias darurat untuk melihat/reset TPS Guard |
| `/testadmin` | GUI pengujian core admin |
| `/kuy status` | TPS Guard, online, job, cache, dan profiler |
| `/kuy guard <auto\|green\|yellow\|orange\|red>` | Otomatis atau paksa level Guard |
| `/kuy freeze <hoe\|pickaxe\|gens\|all>` | Bekukan modul |
| `/kuy unfreeze <hoe\|pickaxe\|gens\|all>` | Aktifkan kembali modul |
| `/kuy audit [jumlah\|pemain]` | Lihat audit terbaru atau filter nama |
| `/kuy player <pemain>` | Lihat UUID dan saldo Token pemain online |
| `/kuy eco [hari]` | Ringkasan faucet/sink |
| `/kuy dryrun <event\|reward>` | Stub simulasi tanpa mengubah data |
| `/kuy backup` | Flush cache dan checkpoint WAL SQLite |

## Instalasi dan uji manual

1. Build plugin dengan Java 21.
2. Salin JAR hasil build ke `plugins/` pada server Paper 1.21.11.
3. Jalankan server sekali untuk menghasilkan `plugins/OctaTycoon/config.yml`, `lang/id.yml`, dan folder database.
4. Pastikan log menampilkan `OctaTycoon ... is ready`.
5. Jalankan `/kuy status`, `/kuy guard orange`, `/kuy guard auto`, `/kuy freeze gens`, `/kuy unfreeze gens`, dan `/kuy backup`.
6. Uji item PDC dari modul atau test harness: coba pindahkan ke chest, ender chest, shulker, hopper, anvil, crafting, drop, dan armor stand.
7. Uji reward Token melalui modul yang memanggil `PayService`, lalu cek saldo melalui database/command admin.
8. Uji hadiah offline: panggil `PayService.pay` untuk UUID yang sedang offline, lalu login dan pastikan hadiah hanya dikirim sekali.

## Command gameplay yang aktif pada snapshot ini

| Command | Permission | Fungsi |
|---|---|---|
| `/progress` | `octatycoon.cmd.progress` | Melihat level gabungan, XP, prestige dan mastery |
| `/mastery` | `octatycoon.cmd.mastery` | Melihat jumlah poin mastery; alokasi GUI belum aktif |

Farming runtime menggunakan Hoe pada tanaman matang. Mining runtime memerlukan Pickaxe, level sesuai band, dan izin ore (`octatycoon.mine.<ore>` atau flag data pemain). OP dapat melewati izin untuk pengujian. Angka dan status tiap modul ada di `docs/F1-F8-IMPLEMENTATION-STATUS.md`.

## F1–F8: status implementasi pengembangan

Update ini menambahkan engine awal untuk semua fase dan menghubungkan runtime pertama F1–F3:

| Fase | Yang sudah ada di source | Status integrasi |
|---|---|---|
| F1 Progress | Combined level/XP persisted, XP curve dari config, prestige cap config, mastery calculation, rank bonus helper, `/progress`, `/mastery` | Progression aktif; rankup, prestige gates/rewards, mastery allocation, top/season/contract/menu belum |
| F2 Farming | Crop gates, harvest Hoe, crop reset, XP/Token, Wheat Market/Barn logic, `data/crops.yml` | Panen vanilla aktif; AoE/enchant/story/quest/Barn GUI belum |
| F3 Mining | Ore mapping, level/permit gate, configurable ore band, 50% XP penalty above band, heat tracker | Reward ore aktif; regen/vein/geode/real Rush/enchants/boss belum |
| F4 Gens | Timestamp production engine, tier/stars/overclock/durability/storage calculation, 11 sample definitions | Belum dihubungkan ke block placement/database/GUI/auto-sell |
| F5 Fishing | Fish DTO, weight helper, minigame evaluation, bait-success rule, sample fish table | Belum mengganti vanilla fishing; belum 500 ikan/rod/quest/UI |
| F6 Boss/Event | Boss contribution/reward splitter, weighted event registry + Guard gates | BossBar/spawn scheduler/turnamen/seluruh konten belum |
| F7 Meta | Battle Pass formula, class page gates, guild role/capacity rules, pity counter | Repository/GUI/command/story/lootbox/ring/booster/shop/donate belum |
| F8 Polish | CSV normalizer importer, unit test sources, GitHub Actions workflow, Maven GitHub guide | Raw Skript parser, PlaceholderAPI, 100-player benchmark, Bedrock/Folia tests belum |

Untuk daftar requirement yang lebih rinci dan cara tes manual, baca `docs/F1-F8-IMPLEMENTATION-STATUS.md`. Panduan upload source ke GitHub dan mengambil JAR hasil Actions ada di `docs/GITHUB-MAVEN.md`.

**Peringatan:** nilai rate gens dan band level ore di file `data/` adalah contoh awal, bukan angka final yang disalin dari Skript lama. Jangan aktifkan fitur ekonomi bernilai tinggi di server produksi sebelum semua harga, drop, multiplier dan reward divalidasi. Adapter Vault/ExcellentEconomy, backend MySQL, hook PlaceholderAPI dan integrasi plugin eksternal juga belum selesai. Spesifikasi awal tetap disertakan di `docs/ORIGINAL-SPEC.md`.
