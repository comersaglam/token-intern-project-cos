# Progress — Veresiye Platform

Bu dosya her adımda güncellenir: ne yaptık, neden, sıradaki ne. Amaç: unutmamak.

Sıra: **app-pos + shared-contracts → app-mobile → backend.**
Yöntem: kısa parçalar, her adımda açıklama + onay, XML views, Clean Architecture,
overengineering yok. Emülatör: medium-size, Google Play imajı (iki cihaz için de).

---

## Genel kararlar (kesinleşti)

- **Repo:** tek monorepo. `app-pos/`, `app-mobile/`, `backend/`, `shared-contracts/`, `docs/`.
- **UI:** XML views (Compose değil).
- **Modül yapısı (app-pos):** çok modüllü Gradle — `:app`, `:core-domain`,
  `:core-data`, `:core-network`. Katman sınırlarını derleyici zorlar.
- **Para tipi:** `amountMinor: Long` (kuruş). Float yok.
- **Ledger:** append-only. Bakiye = hareketlerin toplamı.
- **Sync:** offline-first (Room + outbox + WorkManager). POS = normal telefon
  varsayımı (sürekli prizde ama şimdilik foreground service yok) → WorkManager +
  açılışta sync; FCM ileride.
- **Auth:** esnaf = cihaz kaydı + token yenileme; müşteri = telefon + OTP.
- **Müşteri modeli:** `Customer` + `claimStatus` (UNCLAIMED/CLAIMED) — app'siz
  müşteri desteği (TODO, detay docs/architecture-pos.md §7).

---

## Adım günlüğü

### 2026-07-22 — Tur 1: app-pos mimari tasarımı & folder structure (dokümantasyon)

**Yapılanlar (kod yok, sadece docs):**
- [docs/architecture-pos.svg](docs/architecture-pos.svg) — katmanlı mimari:
  sistem görünümü (app-pos ↔ Backend ↔ app-mobile, "client çağırır dinlemez",
  auth kararları) + app-pos içi katmanlar (modül etiketleriyle) + veri modelleri
  paneli + bağımlılık yönü.
- [docs/flow-pos.svg](docs/flow-pos.svg) — veresiye ödeme akışı: Flow A (POS,
  esnaf) + Flow B (müşteri, app-mobile), aralarında QR/NFC devir, backend +
  gün sonu reconciliation, hesap logic TODO.
- [docs/architecture-pos.md](docs/architecture-pos.md) — şemaların yazılı
  karşılığı: katman rolleri, kavram sözlüğü (ViewModel/Repository/DAO/Retrofit/
  Interceptor/WorkManager), auth kararları, çekirdek 3'lü data class, TODO listesi.
- Mevcut durum tespiti: app-pos & app-mobile Android Studio **Compose**
  template'iyle kurulu (Gradle 9.4.1, AGP 9.2.1, Kotlin 2.2.10, minSdk 24,
  targetSdk 36, tek `:app` modülü). `backend/` ve `shared-contracts/` boş.

### 2026-07-22 — Tur 2: Compose → XML dönüşümü (app-pos + app-mobile)

Her iki proje de Android Studio Compose template'iyle gelmişti; XML views'a
çevirdik. İkisine de aynı 6 değişiklik uygulandı (namespace/tema adı farklı):

- `gradle/libs.versions.toml` — Compose (bom, activity-compose, material3, ui,
  compose-plugin) çıkarıldı; XML için `appcompat`, `material`, `constraintlayout`
  eklendi. Plugin `kotlin-compose` → `kotlin-android`.
- `build.gradle.kts` (root) — `kotlin.compose` plugin'i → `kotlin.android`.
- `app/build.gradle.kts` — `buildFeatures { compose }` → `viewBinding = true`;
  `kotlinOptions { jvmTarget = "11" }` eklendi (Compose plugin'i bunu içeride
  hallediyordu); bağımlılıklar XML setine değişti.
- `MainActivity.kt` — `ComponentActivity` + `setContent { Compose }` →
  `AppCompatActivity` + `setContentView(binding.root)` (ViewBinding).
- `res/layout/activity_main.xml` — **yeni**: ConstraintLayout + ortada TextView
  (`@string/pos_hello` / `@string/mobile_hello`).
- `res/values/themes.xml` — `android:Theme.Material.*` → `Theme.Material3.DayNight.
  NoActionBar` (AppCompatActivity uyumlu). `strings.xml`'e hello string eklendi.
- Silindi: `ui/theme/` (Color.kt, Theme.kt, Type.kt — Compose teması).
- Test dosyaları (ExampleUnitTest / ExampleInstrumentedTest) Compose'suz, dokunulmadı.
- Doğrulama: `grep compose` → temiz. Gerçek build kullanıcının terminalinde
  (`./gradlew :app:assembleDebug` + emülatörde run).

**Öğrenilenler (kavram):** ViewBinding = XML'deki id'li view'lar için üretilen
tip-güvenli sınıf (findViewById yerine). AppCompatActivity XML dünyasının Activity
temeli, AppCompat/Material tabanlı tema ister.

### 2026-07-22 — Tur 2 düzeltmeleri: build hatası + SVG temizliği

**Build hatası çözümü (önemli AGP 9 dersi):**
- Hata: `Cannot add extension with name 'kotlin'`. Sebep: **AGP 9.x Kotlin'i
  built-in getiriyor** — `com.android.application` uygulanınca `kotlin` extension'ı
  zaten kayıtlı oluyor. Ayrıca `org.jetbrains.kotlin.android` eklemek çakışma yaratır.
- Çözüm: `kotlin-android` plugin'ini her yerden kaldırdık (root + app build.gradle.kts
  + libs.versions.toml). AGP 9'da XML projesi için `plugins { alias(android.application) }`
  yeterli; ayrı Kotlin plugin'i YOK. `kotlinOptions { jvmTarget }` bloğu da kaldırıldı
  (built-in Kotlin ile gerekmiyor; compileOptions VERSION_11 yeterli). İkisine de uygulandı.
- Not: Bu haliyle yapı AGP 9'un XML template'iyle birebir aynı davranışta.

**SVG düzeltmeleri:** dikey/binen yazılar yatay yapıldı, belirsiz oklar net
kenarlara bağlandı (architecture-pos.svg: ok etiketleri; flow-pos.svg: var/kurulum/
FCM/sync/devir okları).

**Sıradaki:** İki projeyi de emülatörde çalıştırıp "Hello XML" ekranını gör
(doğrulama: `./gradlew clean :app:assembleDebug`). Sonra shared-contracts/openapi.yaml
(ledger çekirdeği), ardından app-pos Gradle modülleri (:core-domain vb.).
