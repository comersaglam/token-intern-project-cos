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

**Doğrulandı:** app-pos gerçek cihazda (Xiaomi, wireless debugging) çalıştı.
Not: MIUI'de "USB üzerinden yükle" ayarı açılmadan kurulum
INSTALL_FAILED_USER_RESTRICTED verir (kablosuz bağlıyken bile).
Ayrıca AndroidX sürüm dersi: core-ktx 1.19.0 compileSdk 37 istiyordu ->
1.15.0'a düşürüldü (lifecycle 2.11.0 -> 2.8.7 de aynı sebeple).

---

### 2026-07-23 — Tur 3: Yol haritası güncellendi (tasarım txt)

- `veresiye-platform-tasarim.txt` **bölüm 6** yeniden yazıldı: "contract-first"
  yerine **9 fazlı yol haritası** (FAZ 0 temel ... FAZ 8 ileri özellikler).
- **FAZ 7 = Regülasyon & uyumluluk** — ileri özelliklerden ÖNCE. Gerekçe:
  bankalarla iletişimde olan bir üründe KVKK/ödeme regülasyonu ekstra özellik
  değil, canlıya çıkmanın ön koşulu. (KVKK, tokenizasyon/PCI-DSS, audit log,
  veri ikametgahı, faiz-vade için BDDK lisans sorusu.)
- Yeni **bölüm 7 "ALINAN KARARLAR"**: açık kalan kararlar kapatıldı — modül
  yapısı, para tipi, ledger, claimStatus, auth (esnaf/müşteri), POS donanımı
  (açık karar -> normal telefon), geliştirme yöntemi.
- Sıra kararı: **UI+ViewModel (sahte veri) -> contract -> Room -> ağ.** Contract
  backend'e bağlanılacağı an için doğru kural; ekranların neye ihtiyacı olduğu
  bilinmeden yazılan şema sonradan değişir.

---

### 2026-07-23 — Tur 4: FAZ 1 — app-pos UI + MVVM iskeleti (sahte veri)

Üç ekran, hepsi ViewModel + StateFlow ile, veri kaynağı sahte.

**Model + veri (`:app` içinde, FAZ 2'de `:core-domain`'e taşınacak):**
- `model/Customer.kt` — `Customer` + `ClaimStatus` (UNCLAIMED/CLAIMED)
- `model/Transaction.kt` — `Transaction` + `TransactionType` (DEBT/PAYMENT)
- `data/FakeRepository.kt` — 5 müşteri, 10 hareket. **Bakiye saklanmıyor,
  hareketlerden hesaplanıyor** (append-only kuralı sahte veride de geçerli).
- `util/MoneyFormat.kt` — `5000L.toTlString()` -> "50,00 TL"

**Ekran 1 — `ui/payment/`** (launcher activity; MainActivity silindi)
- POS tuş takımı: rakamlar sağdan kayar (`amountMinor * 10 + digit`), ondalık
  nokta yok -> hiç float/parse yok, model ile aynı Long.
- Kart/Yemek Kartı/Nakit -> mock Toast; VERESİYE -> Ekran 2.

**Ekran 2 — `ui/customers/`**
- RecyclerView + `ListAdapter` (DiffUtil ile otomatik fark hesabı)
- Üstte toplam alacak, arama (`doAfterTextChanged`), filtre çipleri (Tümü/Borçlular)
- Borcu olan kırmızı, borcu bitmiş gri.

**Ekran 3 — `ui/detail/`**
- Müşterinin ledger geçmişi + güncel bakiye (yine hareketlerden hesaplanıyor)
- Filtre çipleri: Tümü / Borçlar / Ödemeler. DEBT "+" kırmızı, PAYMENT "-" yeşil.

**Gradle:** `lifecycle-viewmodel-ktx`, `activity-ktx`, `recyclerview` eklendi.

**Öğrenilenler:** `by viewModels()` (ekran döndürmede state korunur);
`StateFlow` + `repeatOnLifecycle(STARTED)` (arka planda toplama durur, sızıntı
yok); `ListAdapter`/`DiffUtil`; constructor parametreli ViewModel için
`ViewModelProvider.Factory`; `companion object`'te `createIntent()` (extra
anahtarları ekrana özel kalır); `styles.xml` ile 12 butonun tek yerden stili;
`tools:` namespace'i sadece preview'da görünür.

---

### 2026-07-23 — Tur 5: Activity'ler -> tek Activity + Fragment + Navigation

Kullanıcının sorusu üzerine yapıldı ("her sayfa ayrı Activity yerine Fragment
daha optimize olmaz mı?") — haklıydı, modern Android standardı tek Activity.

**Yeni yapı:**
- `MainActivity` tek Activity; `activity_main.xml` sadece bir
  `FragmentContainerView` (NavHostFragment) içerir.
- `res/navigation/nav_graph.xml` — ekranlar ve geçişler. **Nested graph**
  (`saleFlow`) kullanıldı: satış akışının ekranları bir grup altında.
- Üç Activity -> Fragment: `PaymentFragment`, `CustomerListFragment`,
  `CustomerDetailFragment`. Layout'lar `activity_*` -> `fragment_*`.
- `ui/sale/SaleViewModel` — akışın **paylaşılan state**'i (tutar).
  `by navGraphViewModels(R.id.saleFlow)` ile erişilir: akıştaki tüm fragment'lar
  aynı örneği görür, akıştan çıkınca ViewModel ölür -> yeni satış sıfırdan başlar.
  Intent extra ile tutar taşıma kalktı.
- Ekranlar arası geçiş: `findNavController().navigate(...)`, argümanlar
  **Safe Args** ile tip güvenli (`CustomerListFragmentDirections`, `by navArgs()`).
- Tema `NoActionBar` -> `Theme.Material3.DayNight`; ekran başlığı ve geri oku
  `setupActionBarWithNavController` ile nav_graph label'larından geliyor.

**Gradle:** `fragment-ktx`, `navigation-fragment-ktx`, `navigation-ui-ktx` +
`androidx.navigation.safeargs.kotlin` plugin'i (root + app).

**Öğrenilenler:**
- Fragment'ın view'ı fragment'tan **önce ölür** -> `_binding` nullable, view
  `onDestroyView`'da temizlenir (yoksa bellek sızıntısı).
- `viewLifecycleOwner.lifecycleScope` kullanılır, `lifecycleScope` değil — flow
  toplama view'ın ömrüne bağlanır.
- Nested graph = hem ekran grubu hem **ViewModel scope**. İleride login akışı /
  bottom navigation gelince kardeş nested grafikler olarak eklenir.
- `viewModelScope` ayrı bir şey: ViewModel içindeki coroutine scope'u (FAZ 3'te
  Room/Retrofit çağrılarında kullanılacak).

**Sıradaki:** Uçtan uca test (tutar gir -> VERESİYE -> liste -> müşteri detayı,
geri oku çalışıyor mu). Sonra FAZ 2: `:core-domain` modülü +
`shared-contracts/openapi.yaml`.

---

### 2026-07-27 — Tur 6: Ödeme ayrımı (mock-pos) + app-pos iki giriş noktalı

Plan değişikliği: ödeme (keypad + Kart/Yemek Kartı/Nakit) app-pos'un parçası
DEĞİL — Token'ın POS ödeme app'ine ait. Yeni `mock-pos/` (ayrı Gradle projesi/APK)
onu taklit eder; VERESİYE -> app-pos'u intent ile açar.

**mock-pos (yeni):** ödeme ekranı (keypad + 4 yöntem), `PaymentViewModel` (keypad
state), `MoneyFormat` (app-pos'tan kopya — ayrı APK'lar import edemez), ViewBinding.
VERESİYE -> `Intent("com.example.app_pos.action.CREDIT")` + `setPackage` +
`amount_minor` (Long kuruş) extra. Manifest'e `<queries>` (Android 11+ package
visibility — olmadan hedef app sessizce bulunamaz).

**app-pos:** ödeme ekranı SİLİNDİ (PaymentFragment, keypad layout/style/strings).
`SaleViewModel` keypad'i çıktı, `setAmount` geldi (tutar intent'ten). nav_graph
start = **dashboard**; saleFlow customerSelect'ten başlar, tutarı `amountMinor`
nav-arg'ından alır. `MainActivity`: intent CREDIT ise saleFlow'a tutarla geç +
handoff modu; değilse dashboard. Manifest'e CREDIT intent-filter (MAIN/LAUNCHER
korundu -> **iki giriş noktası**: bağımsız launcher + veresiye handoff).

**Öğrenilenler / dersler:**
- App-to-app handoff standart deseni: tek Activity + iki intent-filter + onCreate'te
  intent'e göre başlangıç ekranı seçme. Custom action + extra; client'lar kod
  paylaşmaz, sabitler kopyalanır (ileride shared-contracts).
- **Build ortamı dersi:** VSCode'un getirdiği JRE'de `jlink` yok -> `JdkImageTransform`
  patlıyor. Çözüm: `JAVA_HOME`'u Android Studio JBR'sine ayarla + transform cache'i
  temizle. Terminal build: `export JAVA_HOME=".../Android Studio.app/Contents/jbr/
  Contents/Home"` + `-Dorg.gradle.java.installations.auto-detect=false`.
- mock-pos AndroidX sürümleri app-pos ile hizalandı (yine core-ktx 1.19->1.15 vb.
  compileSdk 37 sorunu; Tur 2 dersinin tekrarı).

### 2026-07-27 — Tur 7: FAZ 1 kapatma — onay ekranı + ledger yazımı  [FAZ 1 BİTTİ]

Flow A'nın yarım kalan halkası tamamlandı: müşteri seçilince artık gerçekten
ledger'a yazılıyor.

**FakeRepository observable oldu:** ledger `MutableStateFlow<List<Transaction>>`;
`addTransaction` (append-only), `observeCustomers/observeTransactions/
observeTotalReceivableMinor` Flow döner. Yazınca liste/detay/toplam CANLI güncellenir.
Room DAO Flow'ları aynı davranacağı için ViewModel'ler Faz 3'te değişmeyecek.

**ViewModel'ler Flow'a bağlandı:** `CustomersViewModel`/`CustomerDetailViewModel`/
`CustomerSelectViewModel` artık `combine(repoFlow, query/filter)` + `stateIn
(viewModelScope, WhileSubscribed)`. Bir kez snapshot yerine reaktif. Detay bakiyesi
tek `Long` yerine `StateFlow<Long>`, fragment collect ediyor.

**Onay ekranı (yeni):** `ui/sale/ConfirmFragment` + `ConfirmViewModel` +
`fragment_confirm.xml`. Müşteri, tutar, mevcut + işlem sonrası bakiye gösterir;
[Onayla ve Yaz] -> `addTransaction(DEBT, UUID transactionId)`. Bakiye repo'dan
TAZE okunur (SaleViewModel'deki stale kopya değil). Onay LOKAL — müşteri bildirimi
FAZ 8 (client dinlemez, çağırır). saleFlow: customerSelect -> **confirm** -> yazım
-> handoff'ta `finish`, bağımsız modda dashboard'a.

**Öğrenilenler:**
- `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5s), initial)` — cold
  Flow'u hot StateFlow'a çevirir; aboneli değilken durur (pil), 5sn tolerans ekran
  dönmesinde yeniden başlatmaz.
- `combine` ile arama/filtre reaktif kaynağın üstüne bindirilir (ayrı MutableStateFlow
  snapshot'ı yerine).
- minSdk 24: `java.time.LocalDateTime` API 26 — desugaring yoksa `SimpleDateFormat`
  kullan (MoneyFormat'ın Locale desenine uyumlu).

**Sıradaki:** Uçtan uca test (mock-pos tutar -> VERESİYE -> müşteri seç -> onay ->
yaz -> mock-pos'a dön; app-pos bağımsız aç -> detayda yeni hareket + artan bakiye).
Sonra **FAZ 2**: User modeli (isBuyer/isSeller flag), `:core-domain` modülü,
mock DB formatı, `shared-contracts/openapi.yaml`. Sonra auth (OTP mock), en son
app-mobile UI (model hazırken).

### 2026-07-27 — Tur 8: Handoff düzeltmeleri + yeni müşteri (UNCLAIMED) oluşturma

Fiziksel cihaz testinde çıkan üç iş. (Test sırasında bir yanlış anlama netleşti:
handoff DOĞRU çalışıyordu — VERESİYE app-pos'u açıyor; recent-apps'te tek "mock-pos"
kartı görünmesi Android'in TASK davranışıydı, veri hep app-pos'tan geliyordu.)

**1) Keypad sıfırlama (mock-pos):** `MainActivity.onResume()` -> `viewModel.onClear()`.
Veresiye yazıp dönünce tutar artık "0,00 TL" (önceki satış kalmıyor).

**2) Ayrı task (mock-pos):** `onCreditSelected` intent'ine `FLAG_ACTIVITY_NEW_TASK`.
app-pos KENDİ task'ında açılır -> recent-apps'te iki ayrı kart. Gerekçe: mock-pos
Token'ın gerçek POS ödeme app'inin TAKLİDİ; app-pos onun yaşam döngüsüne bağımlı
olmamalı (hem mimari doğruluk hem debug'da ayrılabilirlik). İki app kod paylaşmaz;
tek sözleşme action string + `amount_minor` extra.

**3) Yeni müşteri oluşturma (UNCLAIMED):** Müşteri seç ekranında, aranan isimde
tam eşleşme YOKSA inline buton ("'<isim>' adıyla yeni müşteri ekle") çıkar; tıkla
-> `ClaimStatus.UNCLAIMED` Customer oluşur (app'siz müşteri; "not-registered" flag'i
zaten tasarımda vardı) -> onay ekranına geçer. Tam eşleşmede buton gizli (aynı isme
izin yok -> esnaf listeden seçer).
- `FakeRepository`: `customers` da MutableStateFlow (observable) oldu; `addCustomer`
  (UNCLAIMED, UUID id) + `customerNameExists` (case-insensitive). observeCustomers/
  observeTotalReceivable artık iki Flow'u `combine` ediyor.
- `CustomerSelectViewModel`: `canCreate: StateFlow<Boolean>` (query dolu + isim yok)
  + `createCustomer()` (id döner / çakışmada null).
- `CustomerSelectFragment` + `fragment_customer_select.xml`: inline TonalButton.

**Öğrenilenler:**
- **Task modeli:** `startActivity` varsayılan olarak çağıranın task'ına ekler;
  başka app'i AYRI recents kartı/yaşam döngüsüyle açmak için `FLAG_ACTIVITY_NEW_TASK`.
- **Customer vs User:** yeni müşteri `Customer.claimStatus=UNCLAIMED` ile açılır,
  Faz 2'nin User/buyer-seller modelini beklemez (farklı kavramlar).
- Kaynak observable'sa (`MutableStateFlow`), yazma metodu yeni liste emit eder ->
  tüm ekranlar canlı; iki kaynağı `combine` ile birleştir.

**Sıradaki:** Fiziksel cihazda doğrula (keypad reset, iki recents kartı, yeni
UNCLAIMED müşteri akışı). Sonra **FAZ 2** (User modeli + core-domain + openapi).

### 2026-07-28 — Tur 9: Telefon-kimlikli akış + OTP onay pipeline + ödeme + nav fix

Büyük mimari karar: sistem **telefon numarası** ekseninde çalışır, ve **her
veresiye/ödeme OTP onayından** geçer (satıcı keyfî borç yazamasın). Backend yok →
OTP mock (`verifyOtp` hep true), ama gerçek ekran + fonksiyon imzaları var;
backend gelince (FAZ 4/5) sadece içleri değişir.

**Kimlik = telefon:** seed müşterilere numara eklendi (hepsi dolu). `addCustomer`
artık `(name, phone)`; `customerNameExists` → `customerPhoneExists` (rakam-normalize,
aynı numara iki kez olamaz; isim serbest). `findCustomerByPhone/ById` eklendi.
UNCLAIMED artık sadece "app yok" demek (numara her kayıtta var).

**OtpService (mock, backend-ready):** `requestOtp(phone, hasApp)` + `verifyOtp(phone,
code, hasApp)`, ikisi suspend, şimdilik true. hasApp dalı (app-push vs SMS) kodda
ayrık, ikisi de mock. Belirgin TODO(FAZ 4/5).

**saleFlow ortak DEBT/PAYMENT akışı:** `SaleViewModel.txType` taşınır. Keypad
mantığı (onDigit/onClear/onBackspace, mock-pos'tan) SaleViewModel'e geri geldi.
Yeni ekranlar: KeypadFragment (PAYMENT girişi), PhoneFragment (yeni müşteri
numara), OtpFragment (kod). Confirm artık YAZMAZ → OTP'ye devreder; **yazma tek
yerde** (OtpViewModel.verifyAndWrite: yeni müşteriyse addCustomer, sonra
addTransaction, UUID idempotency). DEBT: mock-pos → customerSelect. PAYMENT: müşteri
detayı → global action → keypad. Keypad app-pos'a geri (styles.xml + key strings
yeniden; silinmişti).

**İki nav bug'ı:**
- 7a (detaydan listeye dönüş): kök neden — detay İÇ nav host'ta, action-bar up sadece
  DIŞ controller'ı biliyordu. Fix: iç host `defaultNavHost=true` (sistem geri) +
  MainActivity.onSupportNavigateUp önce iç controller'ı dener (DashboardFragment
  `innerNavControllerOrNull` expose eder).
- 7b (satışta yanlış geri = kayıp): `installCancelGuard()` (ortak extension,
  OnBackPressedCallback + AlertDialog "iptal edilsin mi?") her sale ekranında.

**Öğrenilenler:**
- Nested NavHost'ta `defaultNavHost=true` iç back stack'i sistem geri tuşuna bağlar;
  action-bar up ayrı köprü ister (onSupportNavigateUp iç controller'ı önce dener).
- Global action + safeargs: `NavGraphDirections.actionGlobalPay(...)`; iç graph'tan
  dış graph'a geçiş `Navigation.findNavController(activity, R.id.navHostFragment)`.
- Yazmayı tek noktada (OTP sonrası) tutmak: onay ekranı sadece özet, gate OTP.
- Mimari gerilim (NOT): OTP zorunlu ↔ offline-first. Şimdilik mock bypass; backend
  gelince offline'da onay politikası (PENDING durumu?) yeniden konuşulacak (FAZ 4/5).

**Sıradaki:** Cihazda uçtan uca doğrula (yeni müşteri+numara+OTP, kayıtlı, çakışma,
ödeme akışı, iki nav bug). Sonra **FAZ 2** (User buyer/seller modeli + core-domain
+ openapi), sonra auth, en son app-mobile UI.

### 2026-07-28 — Tur 10: Tur 9 hata düzeltmeleri (crash, handoff, geri, numara)

Cihaz testinde çıkan 4 hata düzeltildi. Kök nedenler + çözümler:

**1a) "Ödeme Al" → mock-pos'a gitti:** `isCreditHandoff` bayrağı bir kez true olunca
sıfırlanmıyordu + `launchMode` tanımsız → mock-pos'un NEW_TASK'ıyla açılan app-pos
hayalet task olarak kalıp eski (handoff=true) instance geri geliyordu, ödeme akışı
finish() ile yanlışlıkla mock-pos'a dönüyordu.
- Fix: Manifest `MainActivity launchMode=singleTask` (tek instance) + `onNewIntent`
  override + bayrağı **her intent'te** yeniden değerlendir (`isCreditHandoff =
  intent.action == ACTION_CREDIT`; launcher gelince false).

**1b) Ödeme keypad girişi crash (sürekli durma):** `action_global_pay` iç graph'tan
dış keypadFragment'a girerken zorunlu (default'suz) argümanlar eşleşmiyordu →
IllegalArgument → crash-restart döngüsü.
- Fix: keypad arg'larına `android:defaultValue=""` (crash yerine boş güvenlik ağı) +
  action'a `launchSingleTop`.

**2) Detaydan geri dönülemiyor:** İki nested `defaultNavHost=true` çakışıyordu (dış
host sistem geri tuşunu önce yakalıyor).
- Fix: iç host'tan `defaultNavHost` kaldırıldı; DashboardFragment kendi
  `OnBackPressedCallback`'ini kurar — iç back-stack'te detay varsa
  (`previousBackStackEntry != null`) geri onu pop eder (detay→liste), yoksa callback
  disabled (dış host çalışır). Action-bar up köprüsü (onSupportNavigateUp) korundu.

**3+4) Numara görünmüyor / Ahmet1-Ahmet2 ayrımı:** Detay + liste satırında telefon
alanı yoktu. Seçim zaten customerId (UUID) ile doğruydu; numara görününce satıcı
ayırt edebiliyor.
- Fix: `fragment_customer_detail.xml`'e `detailPhone`; CustomerDetailFragment
  `findCustomerById(...).phone` (lazy, tek okuma, pay butonuyla paylaşılır).
  `CustomerAdapter` statü satırına numarayı ekler ("Uygulaması yok · +90 555…").

**Öğrenilenler:**
- `launchMode=singleTask` + `onNewIntent`: app-to-app handoff yapan Activity'de
  tek-instance garantisi; her açılış intent'i onNewIntent'e gelir, state oradan
  tazelenir. Bayrağı intent'e bağlamak (bir kez set edip bırakmak yerine) stale
  state'i önler.
- Nav argümanı default'suz = zorunlu; verilmezse crash. Deep hedefe (nested graph
  içi) girişte default güvenlik ağı işe yarar.
- İki nested defaultNavHost = geri tuşu çakışması; iç host'u elle OnBackPressedCallback
  ile yönet.

**Sıradaki:** Cihazda 4 hatayı da doğrula (app-pos'u yeniden kur!). Sonra FAZ 2.

### 2026-07-28 — Tur 11: Ödeme akışı crash — gerçek kök neden (nav mimarisi)

Tur 10'daki 1a/1b tam çözmemişti; cihazda crash sürüyordu. Logcat (crash buffer)
ile kesin trace alındı, kök neden bulundu ve nav_graph mimarisi düzeltildi.

**Debug yöntemi (kalıcı):** `adb logcat -b crash` = crash buffer (Java stack trace).
`level:error` filtresi sistem-seviyesi crash'i KAÇIRIYOR — crash için hep `-b crash`.
Kurulu kod güncel mi? `dumpsys package ... | grep lastUpdateTime`. Kısayollar
`~/.zshrc`'ye eklendi: **posbuild** (app-pos derle+kur), **mockbuild**, **poscrash**.
Önemli ders: AS "Run" bazen "up-to-date" deyip eski APK'yı bırakıyor; kesinlik için
`adb install -r` (posbuild bunu yapar) + lastUpdateTime kontrolü.

**Gerçek kök neden:** `keypadFragment` `saleFlow` nested graph'ının İÇİNDEydi ama
`startDestination` değildi (`customerSelectFragment`'tı). Navigation kuralı: bir
nested graph'a DIŞARIDAN sadece `startDestination`'ına girilebilir; iç bir node'a
doğrudan `navigate` → IllegalArgumentException ("cannot be found from current
destination"). Global action da, Bundle ile doğrudan navigate de aynı duvara
çarpıyordu — hepsi "içeri doğrudan gir" demeye çalışıyordu.

**Çözüm — keypad'i saleFlow'un start'ı yap, iki akışı kapıda ayır:**
- `nav_graph.xml`: `saleFlow app:startDestination=@id/keypadFragment`. Keypad ortak
  giriş kapısı. `action_global_pay` artık `saleFlow`'a (grafiğe) gider, keypad'e değil.
- `KeypadFragment.routeByEntry()`: `amountMinor > 0` (mock-pos'tan gelen DEBT) →
  tutarı yükle + `action_keypad_to_customerSelect` (popUpTo keypad inclusive, keypad
  geçmişten silinsin) → müşteri seçmeye geçer, keypad atlanır. `amountMinor == 0`
  (detaydan gelen PAYMENT) → keypad'de kal, müşteri zaten belli, tutar burada girilir.
- `CustomerSelectFragment`: artık amountMinor argümanı yok (keypad'e taşındı),
  `setupAmount`/navArgs kaldırıldı; amount/txType keypad'de set edilir.

**Handoff'u akış türüne bağla (Tur 10-1a'yı tamamlar):** `finishCreditHandoff(isHandoffFlow)`
— sadece DEBT + isCreditHandoff'ta finish (mock-pos'a dön). PAYMENT hep dashboard'a
döner, ASLA mock-pos'a gitmez/crash etmez. Çağrı yerleri (OtpFragment, SaleFlowCancel)
txType'a göre isHandoffFlow geçer.

**Detay geri oku (Tur 10-2'yi tamamlar):** iç host action-bar'a bağlı olmadığından
geri oku HİÇ görünmüyordu. DashboardFragment iç destination değişiminde
`supportActionBar.setDisplayHomeAsUpEnabled(canGoUp)` — detayda ok görünür, listede
gizli. Hem üst ok hem sistem geri tuşu detay→liste yapar. ✓ cihazda doğrulandı.

**Öğrenilenler (nav — intuitive):** nav_graph = metro haritası; `<navigation>` = kapalı
istasyon grubu; `app:startDestination` = grubun TEK giriş kapısı; dışarıdan sadece
kapıya girilir, iç node'a değil. `app:` = kütüphane (Navigation) attribute'u,
`android:` = çekirdek. `popUpTo`+`popUpToInclusive` = geçmişten ekran sil (atlanan
ekrana geri dönülmesin). "Girilemez" kuralını kütüphane runtime'da uygular (crash
mesajıyla belli eder), kodda yazmaz.

**Durum:** app-pos akışları cihazda ÇALIŞIYOR — veresiye (mock-pos→app-pos→onay→OTP→
mock-pos'a dön), ödeme (detay→Ödeme Al→keypad→onay→OTP→dashboard), yeni müşteri+numara,
detaydan geri. **Sıradaki: FAZ 2** (User buyer/seller modeli + core-domain + openapi),
sonra auth (OTP'yi gerçek backend'e bağlama dahil), en son app-mobile UI.

### 2026-07-28 — Tur 12: FAZ 2 ilk adım — `:core-domain` saf-Kotlin modülü  [FAZ 2 başladı]

Çok modüllü mimarinin ilk taşı. Hedef yapı `:app → :core-data → {:core-domain,
:core-network}`; bu tur `:core-domain`'i kurup saf domain modellerini oraya taşıdık.
Küçük, öğretici adım — davranış hiç değişmedi ("modül ekledik, kod aynen derlendi").

**Kapsam kararı (bilinçli dar tutuldu — overengineering yok):**
- TAŞINDI → `:core-domain`: `model/Customer.kt` (ClaimStatus + Customer),
  `model/Transaction.kt` (TransactionType + Transaction). Saf enum + immutable data
  class, sıfır dış bağımlılık.
- KALDI → `:app`: `FakeRepository` (Flow/observable/seed = data katmanı; FAZ 3'te
  `:core-data`'ya), `OtpService`, `util/MoneyFormat` + `util/PhoneFormat` (UI-yakını
  formatlama). `balanceOf` (DEBT +, PAYMENT −) şimdilik FakeRepository'de private —
  tek çağıranı var, erken soyutlama YAGNI; FAZ 3'te Room dönüşümüyle domain'e çıkar.

**Yapılanlar:**
- Yeni `core-domain/build.gradle.kts`: `plugins { id("org.jetbrains.kotlin.jvm") }`
  (VERSİYONSUZ — aşağıdaki ders), `kotlin { jvmToolchain(11) }` (:app Java 11 ile
  uyumlu bytecode), dependencies BOŞ (saflık). `repositories {}` YOK
  (`FAIL_ON_PROJECT_REPOS` — modül repo tanımlarsa build patlar). `android {}` YOK.
- `settings.gradle.kts`: `include(":core-domain")`.
- `app/build.gradle.kts`: `implementation(project(":core-domain"))`.
- **Paket adı KORUNDU** (`com.example.app_pos.model`) — sadece fiziksel yer değişti.
  → `:app`'teki hiçbir `import` satırı değişmedi (ViewModel/adapter/FakeRepository
  olduğu gibi derlendi). Minimum risk, net diff. Dosyalar `mv` ile taşındı
  (`git mv` untracked dosyada çalışmaz — henüz commit'lenmemişlerdi).

**Öğrenilenler (kritik AGP 9 dersi):**
- İlk denemede `alias(libs.plugins.kotlin.jvm)` (version.ref="kotlin") çakıştı:
  *"plugin is already on the classpath with an unknown version"*. Kök neden: AGP 9
  Kotlin Gradle Plugin'i TÜM build classpath'ine (sürümsüz) koyuyor — sadece Android
  modüllerini değil. Ayrı bir versiyonlu istem sürüm çakışması sayılıyor. Çözüm:
  `:core-domain`'de plugin'i VERSİYONSUZ uygula (`id("org.jetbrains.kotlin.jvm")`);
  classpath'te hazır olanı kullanır. Kullanılmayan catalog alias'ı geri alındı.
- **Modül saflığı derleme çıktısından okunur:** `:core-domain:build` sadece
  `compileKotlin`/`jar` çalıştırdı, hiç Android task'ı yok → gerçekten saf JVM.

**Doğrulama:** `./gradlew :core-domain:build` ✓ (izole, Android'siz), `:app:assembleDebug`
✓ (taşınan modeller `:core-domain`'den çözüldü; tek uyarı: OtpViewModel'deki eski
`Locale` deprecated — bu turla ilgisiz). **Cihaz testi BEKLİYOR** — build sırasında
telefon bağlı değildi (`adb: no devices`). Cihaz bağlanınca `posbuild` + akışları elle
doğrula (davranış aynı olmalı).

**Sıradaki:** (1) Cihazda doğrula. (2) `docs/architecture-pos.md`'ye Customer≠User /
tek app-mobile iki rol modelini ekle (Tur bu session'da netleşti: müşteri telefon+OTP
hızlı giriş → oto-kayıt → ödeme geçmişi + profil; "Satıcı ol" → registration + POS
eşleme; esnafın eklediği UNCLAIMED kayıt henüz User değil, claim ile bağlanır).
(3) User modeli (`:core-domain`, isBuyer/isSeller) + mock DB düzeni. (4) openapi.yaml.

### 2026-07-29 — Tur 13: User modeli + mock DB (full-app-ready) + yol haritası yeniden sıralandı

FAZ 2'nin asıl içeriği. İki kavramsal karar netleşti ve **faz sırası değişti**.

**YOL HARİTASI YENİDEN SIRALANDI (kullanıcı kararı):** app-mobile öne alındı.
Yeni sıra: **User modeli (bu tur) → app-mobile UI (mock üstünde) → shared-contracts
→ Room → backend.** Gerekçe: iki client mock'la çalışınca backend contract'ı gerçek
ekran ihtiyacına göre yazılır (tasarımın "ihtiyaç bilinmeden yazılan şema değişir"
ilkesi) + gösterilebilir somut demo. app-mobile `:core-domain`'i KOPYALAYACAK (ayrı
Gradle projesi; mock-pos deseni), gerçek paylaşım backend fazında.

**CUSTOMER ≠ USER (tasarımın kalbi — karıştırma):**
- Customer = SATICININ DEFTER KAYDI (app-pos'un bildiği). UNCLAIMED = arkasında hesap
  OLMAYAN isim+telefon.
- User = APP-MOBILE HESABI (telefon+OTP giriş). Tek model, rol iki BOOL: isBuyer
  (herkes böyle başlar) + isSeller ("Satıcı ol" ile). İki rol aynı anda aktif olabilir.
- Köprü = CLAIM: User telefonuyla girince o numaralı UNCLAIMED Customer CLAIMED olur,
  `Customer.claimedByUserId` ile bağlanır. İlişki VERİDE (telefon eşleşmesine güvenme).

**Yapılanlar:**
- `:core-domain` yeni `User.kt`: User (userId + phone non-null + isBuyer/isSeller +
  email? + sellerInfo? + createdAt) + `SellerInfo` (shopName, shopPhone?). SellerInfo
  AYRI class → "isSeller=true ⇔ sellerInfo!=null" kuralını DERLEYİCİ korur.
- `Customer.kt`: `claimedByUserId: String?` eklendi (CLAIMED ⇔ !=null). Yanlış yorum
  düzeltildi ("null=UNCLAIMED" → telefon pratikte hep dolu).
- `FakeRepository`: `RawCustomer`+seed'e claimedByUserId (c1→u1, c3→u3, diğerleri null);
  Customer construct eden **3 nokta** güncellendi (unutulan = derleme hatası, iyi koruma).
  `_users` seed: `u_owner` (esnaf, isBuyer+isSeller, SellerInfo "Ahmet Bakkal" —
  Customer'ı YOK, "tek User iki rol" kanıtı) + u1/u3 (CLAIMED customer'ların hesabı).
- Metodlar (backend-ready, OtpService deseni): **çalışır** — findUserByPhone,
  observeCurrentUser (mock: owner), registerUser (oto-kayıt: yoksa oluştur/varsa dön),
  setSeller (isSeller=true + SellerInfo). **imza+TODO** — claimCustomerForUser
  (app-mobile turunda). observeUser/observeMyTransactions: sadece dokümante (cross-merchant
  backend ile gelir).
- Dokümanlar senkronlandı (iki dosya User'da ayrışmıştı): `docs/architecture-pos.md`
  §4'e Customer≠User + User/SellerInfo kod bloğu + "gerçek DB ne zaman" (Room=FAZ 3 cihazda
  / Docker sunucu-DB=backend fazı), §7 Customer bloğu + §8 yeni sıra; `veresiye-platform-
  tasarim.md` ALINAN KARARLAR'a CUSTOMER!=USER + FAZ sırası güncellemesi + FAZ 8 satıcı notu.

**Öğrenilenler:**
- **Data class'a alan eklemek = tüm construct noktalarını güncelle** (Customer 3 yerde
  kuruluyordu). Unutulan biri derleme hatası verir — sessiz bug değil, iyi koruma.
- **Invariant'ı tiple koru:** "satıcıysa bilgi dolu" / "claimed'se user id dolu" —
  nullable ALT-NESNE (SellerInfo?) veya nullable FK (claimedByUserId?) ile ifade edilince
  imkânsız durumlar derlemede yakalanır (düz nullable alanlar kaçırırdı).
- **Locale:** yeni kodda `Locale.forLanguageTag("tr-TR")` (deprecated değil); kod
  tabanının eski yerleri `Locale("tr","TR")` kullanıyor (ileride topluca güncellenebilir).

**Doğrulama:** `:core-domain:build` ✓ (User/SellerInfo saf JVM), `:app:assembleDebug` ✓
uyarısız. Mevcut veresiye/ödeme akışları değişmedi (sadece FakeRepository iç satırları +
Customer alanı). **Cihaz testi:** telefon bağlıysa `posbuild` + akışları dolaş (aynı
davranış). Build sırasında cihaz bağlı değildi.

**Sıradaki:** app-mobile UI (mock üstünde) — telefon+OTP giriş (oto-kayıt) → ödeme
geçmişi + profil → "Satıcı ol". POS asset'lerinden türer, `:core-domain`'i kopyalar.
