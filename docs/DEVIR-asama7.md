# DEVİR — Aşama 7 (app-pos `:core-network` → `:core-data`/`:app` bağlama)

> **Geçici dosya.** Aşama 7 bitince SİL; kalıcı kayıt `docs/progress.md`'ye Tur 26 olarak yazılacak.
> Yeni oturumda ilk iş: bu dosyayı oku, sonra `docs/progress.md` + `docs/architecture-pos.md` §2/§5.

## Bağlam: neredeyiz

FAZ 4 (`:core-network`) yazılıyor. **Aşama 0–6 BİTTİ ve doğrulandı; Aşama 7 HENÜZ BAŞLAMADI.**
`:core-network` modülü tam olarak duruyor ama **hiçbir yerden çağrılmıyor** — `:core-data` ona
bağlı değil, `:app` Hilt kullanmıyor. Uygulama hâlâ tamamen offline çalışıyor ve bozulmadı.

Yol haritasındaki yeri: `docs/architecture-pos.md` §8 madde 4 (Backend fazının ilk yarısı).

## Kullanıcı tercihleri (bu turda alınan kararlar)

| Karar | Seçim | Not |
|---|---|---|
| Auth modeli | `POST /auth/refresh` contract'a EKLENDİ | Session'a `refresh_token` (nullable) |
| Timestamp | Şimdi ISO-8601'e geçildi | Aşama 0'da yapıldı, bitti |
| DI | **Hilt** (manuel provider değil) | Kullanıcı "şimdi geç" dedi |
| Repository yapısı | **OfflineFirstRepository** (Local+Remote+Sync besteleyen) | A değil B seçildi |

Genel çalışma tercihi: **endüstri standardını gerekçesiyle öner, kararı kullanıcıya bırak.**
Kod yorumları İngilizce, sohbet/doküman Türkçe.

---

## BİTEN İŞ (dokunma, çalışıyor)

### Aşama 0 — Timestamp ISO-8601 ✓
- `Daos.kt`: `CREATED_AT_SORT` substr hack'i **silindi** → düz `ORDER BY createdAt DESC`
- `AppDatabase.kt`: `version = 2` (destructive fallback açık → migration yazılmadı)
- `SeedCallback.kt` 13 timestamp, `RoomRepository.nowStamp()`, `OtpViewModel` → ISO üretiyor
- Yeni `app/util/TimeFormat.kt` — ISO → "dd.MM.yyyy HH:mm" **gösterim**; `TransactionAdapter` kullanıyor
- minSdk 24 yüzünden `java.time` DEĞİL, `SimpleDateFormat` + UTC (desugaring eklenmedi)
- **app-mobile'da AYNISI YAPILMADI** — ayrı tur, yoksa iki app kıyaslanamaz `created_at` yazar

### Aşama 1 — Modül + Hilt ✓
- `:core-network` (com.android.library), `settings.gradle.kts`'e eklendi
- **Hilt 2.60.1** — altındaki sürümler AGP 9'da "BaseExtension not found" ile patlıyor (memory'de kayıtlı)
- `NetworkConfig.kt` — `BuildConfig.API_BASE_URL` sarmalayıcısı; debug `http://10.0.2.2:4010/`

### Aşama 2 — DTO'lar ✓ (8 dosya, 29 Moshi adapter'ı)
`network/dto/`: Auth, User, Customer, Transaction, OrderBody, Approval, Error, Future
- **`@param:Json` kullanıldı** (düz `@Json` değil) — Kotlin 2.2'de 111 uyarı veriyordu
- Enum alanları **String**, domain enum'u DEĞİL (gerekçe aşağıda)

### Aşama 3 — Mapper'lar ✓ (`network/mapper/`)
- `EnumMapping.kt` — bilinmeyen değer politikası: `type` → satırı DÜŞÜR, `claim_status` → UNCLAIMED
- `TransactionMapper.toCreateDto()` **`seller_id` göndermiyor** (sunucu token'dan alır)
- `OrderBodyMapper` — `limit`(domain) / `item_limit`(wire) / `itemLimit`(entity) üç ad tek değer
- `ApprovalMapper` sadece istek yönü (app-pos'ta approval domain tipi YOK, o app-mobile'da)

### Aşama 4 — Hata sınırı ✓
`ApiResult` (Success/ApiError/NetworkError/UnexpectedError) + `isRetryable()` + `SafeCall.apiCall()`
- **`isRetryable()` outbox'ın retry kuralıdır:** NetworkError→evet, 4xx→hayır, 5xx→evet
- Hata gövdesi **tek kez** parse edilir (`errorBody().string()` tek kullanımlık)

### Aşama 5 — 7 Retrofit API'si ✓ (`network/api/`)
Auth, User, Customer, Ledger, Buyer, Approval, Sync. Hepsi `suspend`, DTO döner (`Response<T>` değil).
`BuyerApi` app-pos'ta kullanılmıyor ama yazıldı → app-mobile düz kopyalayacak.

### Aşama 6 — Auth ✓
`network/auth/`: `TokenStore` (interface), `DataStoreTokenStore`, `AuthInterceptor`, `TokenAuthenticator`
- **İki OkHttp client:** `@AuthClient` (token'sız → AuthApi) + `@ApiClient` (tam yığın).
  Refresh döngüsü **yapısal olarak** imkânsız. `Provider<AuthApi>` ile kurulum döngüsü kırıldı.
- `TokenStore` senkron okumaları RAM cache'ten (`prime()` tek disk okuması) → login gate ANR etmez
- **Docs'tan bilinçli sapma:** düz DataStore kullanıldı, EncryptedSharedPreferences DEĞİL (deprecated).
  `TokenStore` interface olduğu için Keystore'lu impl sonradan drop-in. `architecture-pos.md` §5'e not düşülecek.
- `shared-contracts/openapi.yaml`: `POST /auth/refresh` + `Session.refresh_token` **eklendi**,
  Redocly lint temiz (56 warning, hepsi stil), **Prism'de canlı doğrulandı**

### Testler: 22 test, hepsi geçiyor, 0 skip
| Dosya | Adet | Ne kanıtlıyor |
|---|---|---|
| `TransactionMapperTest` | 6 | snake_case anahtarlar, `seller_id` gitmiyor, bilinmeyen type düşüyor |
| `ApiResultTest` | 6 | retry sınıflandırması |
| `LedgerApiTest` | 5 | **MockWebServer** — gerçek URL/header/JSON |
| `AuthFlowTest` | 5 | **MockWebServer** — bearer header, 401→refresh→retry, sonsuz döngü yok |

Çalıştır: `./gradlew :core-network:testDebugUnitTest`

---

## YAPILACAK: Aşama 7 — parçalı plan

### Kod okumasından çıkan ÜÇ SÜRPRIZ (plan bunlara göre düzeltildi)

**1. `isSessionValid()` İKİ yerde okunuyor:**
- `MainActivity.kt:64` → startDestination (nav graph'tan ÖNCE, senkron, main thread)
- `MainActivity.kt:118` → **CREDIT handoff dalı** (`onNewIntent` yoluyla `onCreate`'siz de gelir)

Hilt inject'i `super.onCreate()`'te biter, `onNewIntent` hep ondan sonra → sıralama güvenli.
Ama handoff yolu cihazda AYRICA test edilmeli.

**2. İki VM constructor arg alıyor, İKİSİ FARKLI:**

| VM | Arg nereden | Hilt'te |
|---|---|---|
| `CustomerDetailViewModel(customerId)` | nav arg (safeargs) | `SavedStateHandle` ✅ |
| `ConfirmViewModel(customerId)` | **`saleViewModel.selectedCustomer.value`** (başka VM'in runtime state'i) | `SavedStateHandle` **ÇÖZEMEZ** ❌ |

→ **`ConfirmViewModel` Hilt'e ALINMIYOR**, mevcut factory'siyle kalıyor. Hilt'li ve Hilt'siz VM
aynı projede sorunsuz yaşar; en küçük diff.

**3. `SaleViewModel` repo'ya hiç dokunmuyor** (import bile yok) → Hilt'e gerek yok.

**Sonuç: planın "9 VM" dediği yerde gerçek sayı 7.**

### Adımlar (her biri ayrı derlenir; riskliler tek başına)

| # | İş | Risk | Doğrulama |
|---|---|---|---|
| **7a** | `core-data/build.gradle.kts` → `implementation(project(":core-network"))` + hilt plugin; `RoomRepository.kt` → `local/RoomLocalDataSource.kt` (session bloğu HENÜZ çıkmaz) | Düşük | `:core-data:assembleDebug` + `:app:compileDebugKotlin` |
| **7b** | `core-data/remote/RemoteDataSource.kt` — API'leri sarar, `ApiResult` döner (kimse çağırmıyor) | Düşük | `:core-data:assembleDebug` |
| **7c** | `OfflineFirstRepository : Repository` — session RAM'den `TokenStore`'a. `isSessionValid()`/`currentSellerId()` **senkron KALIR**. `observeCurrentUser()` `tokenStore.observeSession()` ile combine | **YÜKSEK** | **unit test** (session boş→false, primed→true, logout→null emit). `:app` bu adımda HİÇ değişmez |
| **7d** | Hilt'i `:app`'e sok ama SADECE `App.kt` (`@HiltAndroidApp` + `prime()`) ve `MainActivity` (`@AndroidEntryPoint` + `@Inject repo`). Diğer 7 VM hâlâ `RepositoryProvider.instance` | **YÜKSEK** | **CİHAZ** (login gate + handoff) |
| **7e** | 7 VM'i Hilt'e: önce `ProfileViewModel` tek başına → derle → sonra kalan 6. Fragment'lara `@AndroidEntryPoint` | Orta | her grup sonrası derleme |
| **7f** | `core-data/di/DataModule.kt` (Room + Repository `@Provides`); `RepositoryProvider.kt` **SİL** | Düşük | derleme + cihaz |
| **7g** | `app/src/main/res/xml/network_security_config.xml` (sadece 10.0.2.2 + localhost) + manifest `android:networkSecurityConfig`. **`usesCleartextTraffic="true"` KOYMA** (release'e sızar) | Düşük | derleme |

**7c/7d ayrımının sebebi:** 7c'de `:app` değişmediği için eski `RepositoryProvider` ayakta kalır ve
login gate unit testle doğrulanabilir. 7d'de sadece 2 dosya değişir → gate bozulursa suçlu 19 değil 2.

---

## Cihaz testi (7d ve 7f sonrası)

**Cihaz:** Xiaomi 23049PCD8G (marble), kablosuz debugging. Üç paket kurulu:
`com.example.app_pos`, `com.example.app_mobile`, `com.example.mock_pos`.

**MIUI kuralı:** `adb shell pm clear` ÇALIŞMAZ (CLEAR_APP_USER_DATA izni yok).
DB sıfırlamak için: `adb uninstall com.example.app_pos` sonra yeniden kur.
(`adb uninstall` argümansız çağrılırsa "uninstall requires an argument" der — paket adı şart.)

**Build:** `export JAVA_HOME=".../Android Studio.app/Contents/jbr/Contents/Home"` şart
(Bash tool zshrc yüklemiyor). `:app:assembleDebug` sandbox'ta jlink hatası verir — **kullanıcının
`posbuild`'i ile kurulur**, kod bugı değil.

**Grup A — bozulmaması gerekenler:**
1. Launcher → login gate
2. Giriş (`05554443322`) → dashboard, flash yok
3. mock-pos'tan VERESİYE → app-pos → onay → OTP → mock-pos'a döner
4. Çıkış → login, dashboard geçmişten silinir
5. Müşteri detayı → geri oku listeye döner

**Grup B — YENİ davranış (Aşama 7'nin asıl kazancı):**
6. Oturum açıkken uygulamayı kapat-aç → **login SORMUYOR** (eskiden RAM'di, gidiyordu)

**Aşama 0 kontrolü (DB v2, ilk kurulumda):** bakiyeler — Ahmet 40,00 / Ayşe 165,00 / Mehmet 0 /
Fatma 25,50 / Hasan 210,00. Sıralama: yeni kayıt geçmişte EN ÜSTTE (ISO sort).

---

## Aşama 7'den SONRA sırada ne var

- **Aşama 8:** outbox yazımı (`addTransaction` içinde `db.withTransaction` ile ledger+outbox atomik),
  `OutboxDao.insert`'e `onConflict = IGNORE` (şu an default ABORT → retry'da patlar),
  `SyncEngine.drainOutbox()`. **WorkManager BU TURDA YOK** (ayrı tur; `WorkerFactory` sorunu).
- **Dokümanlar:** `progress.md` Tur 26, `api-endpoints.md` açık karar #3 kapat + `/auth/refresh`,
  `architecture-pos.md` §5 DataStore sapma notu.
- **app-mobile:** `:core-network` kopyası + Aşama 0 timestamp geçişi (ayrı tur).

## Backend'in onurlandırması gerekenler (contract'tan çıkan)

1. `Idempotency-Key == transaction_id`; aynı key+aynı gövde → 200 orijinal kayıt; aynı key+**farklı**
   gövde → **409**, sessiz overwrite DEĞİL
2. Bakiye türetilir, saklanmaz — `core-domain/Ledger.kt::balanceOf` ile birebir aynı
3. Append-only: UPDATE/DELETE yok, düzeltme = ters işaretli yeni satır
4. `seller_id` gövdeden asla güvenilmez, token'dan
5. Yeni enum değeri = **breaking change** (client bilinmeyen `type`'ı düşürüyor)
6. Telefon E.164 normalizasyonu sunucuda, `storedPhone` ile aynı
