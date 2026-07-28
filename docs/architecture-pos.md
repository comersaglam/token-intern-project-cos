# app-pos — Mimari & Tasarım Notları

> Bu belge iki şemanın yazılı karşılığıdır:
> - [architecture-pos.svg](architecture-pos.svg) — katmanlı mimari (sistem + app-pos içi)
> - [flow-pos.svg](flow-pos.svg) — veresiye ödeme akışı (Flow A: POS, Flow B: müşteri)
>
> Kaynak tasarım: [../veresiye-platform-tasarim.txt](../veresiye-platform-tasarim.txt)

---

## 1. Kısa özet

**Veresiye Dijital Takip** — esnafın müşteri borçlarını dijital tuttuğu bir sistem.
Üç parça + sözleşme: `app-pos` (esnaf), `app-mobile` (müşteri), `backend` (source
of truth), `shared-contracts` (OpenAPI). Bu belge **app-pos** odaklıdır.

Temel ilkeler:
- **Offline-first:** işlem önce lokal Room DB'ye yazılır, internet gelince sync olur.
- **Append-only ledger:** bakiye tek bir sayı olarak tutulmaz; sadece hareketler
  (DEBT/PAYMENT) saklanır, bakiye = hareketlerin toplamı.
- **Client çağırır, dinlemez:** app port açıp beklemez; iletişimi hep o başlatır.
  Backend haber vermek isterse FCM ile "dürter".
- **Para = kuruş (Long):** `amountMinor`. Float yok (kayan nokta para hatası yapar).

---

## 2. Katmanlı mimari (çok modüllü Gradle)

app-pos tek APK'dır ama içi 4 Gradle modülüne ayrılır. Modül ayrımının sebebi:
**katman sınırlarını derleyici zorlar.** Bir modülün `build.gradle.kts`'inde
bağımlılık yoksa, o modülün kodu diğerini `import` edemez → derleme hatası.

| Modül | Sorumluluk | İçindekiler | İçinde OLMAYAN |
|---|---|---|---|
| `:app` | UI + sunum | Activity, Fragment, **XML layout**, ViewModel (StateFlow) | iş kuralı, DB, network |
| `:core-domain` | işin kalbi (saf Kotlin) | modeller (`Transaction`), ledger kuralları | Android / Room / Retrofit importu |
| `:core-data` | veri + karar | Room DAO, Repository, Sync Engine (WorkManager), outbox | UI kodu |
| `:core-network`| dış dünya ağzı | Retrofit API, AuthInterceptor, DTO'lar | iş kuralı, DB |

### Bağımlılık yönü (derleyici zorlar)
```
:app          → :core-data
:core-data    → :core-domain, :core-network
:core-network → :core-domain
:core-domain  → (hiçbir şeye bağımlı değil)
```
**Kural:** dış katman içi bilir, iç katman dışı BİLMEZ. `:core-domain`'in Room
importu = derleme hatası. Bu, "mimari erozyonu" (big ball of mud) önler.

### Katmanların rolü (kavram sözlüğü)
- **ViewModel** — ekran durumunu `StateFlow` ile tutar. UI olayını alır,
  Repository'yi çağırır, sonucu state olarak yayar. UI bu state'i gözler.
- **Repository (KİLİT KATMAN)** — "veriyi lokalden mi backend'den mi alacağım"
  kararı burada. Offline-first mantığı: **önce Room'a yaz** (kullanıcı beklemesin),
  **sonra outbox'a at** (ağ işini arkaya bırak).
- **Room DAO** — Android'in SQLite sarmalayıcısı. Anotasyonlu Kotlin
  (`@Insert`, `@Query`) yazarsın, Room SQL'e çevirir + derleme zamanında kontrol
  eder. Append-only ledger burada (`insert` var, `update balance` yok).
- **Retrofit API** — HTTP client. Bir Kotlin `interface` yazarsın (`@POST`,
  `@GET`), Retrofit ağ kodunu üretir. Sadece **giden** çağrılar.
- **AuthInterceptor** — OkHttp interceptor'ı. Giden her isteğe
  `Authorization: Bearer <token>` header'ını otomatik ekler (her fonksiyona elle
  yazmazsın). 401 gelince token'ı sessizce yeniler.
- **Sync Engine (WorkManager)** — arka planda güvenilir iş. "İnternet gelince
  outbox'takileri POST et, başarısızsa retry." Sistem uykuda olsa bile çalışır.

> **Kotlin notu — `suspend`:** Ağ/DB çağrıları `suspend fun`'dır (coroutine).
> Uzun sürebilir ama ana thread'i (UI) kilitlemez → ekran donmaz.
> **`data class`:** `equals`/`hashCode`/`toString`/`copy` otomatik gelir; sadece
> veri taşıyan kutu. **`enum class`:** sabit seçenek kümesi; geçersiz değer
> derleme zamanında imkânsız.

---

## 3. Sistem iletişimi (kim kime çağrı atar)

```
app-pos  ──enroll · POST /transactions · GET balance──►  Backend
app-mobile ──telefon+OTP · GET balance · POST payment──►  Backend
Backend  ──FCM push "uyan/yeni borç"──►  app-pos / app-mobile   (dinletmez, dürter)
```
- **Düz oklar:** app'in başlattığı HTTPS/REST çağrıları (giden).
- **FCM (kesikli):** backend app'e kendiliğinden bağlanamaz (NAT, değişken IP,
  uyku). Sadece "yenilik var, sync et" diye dürter; app sync'i kendi başlatır.
- Backend **tek DB'nin sahibi** ve source of truth; çakışmaları çözer (ledger
  append-only olduğu için çakışma zaten minimum).

---

## 4. Veri modelleri (çekirdek 3'lü — bu turda)

Üç ayrı temsil, çünkü her birinin **değişme sebebi farklı** (aralarında `mapper`
fonksiyonlarıyla çevrilir). `shared-contracts/openapi.yaml` alan adlarıyla hizalı.

```kotlin
// :core-domain — saf, temiz iş modeli
enum class TransactionType { DEBT, PAYMENT }

data class Transaction(
    val transactionId: String,   // UUID — idempotency
    val customerId: String,
    val amountMinor: Long,       // kuruş; 50 TL = 5000
    val type: TransactionType,   // DEBT (+) / PAYMENT (-)
    val createdAt: String        // ISO-8601
)
```
```kotlin
// :core-network — GİDEN JSON şekli (snake_case). Retrofit/Moshi @Json ile eşlenir.
data class TransactionDto(
    val transaction_id: String,
    val customer_id: String,
    val amount_minor: Long,
    val type: String,            // "DEBT" / "PAYMENT"
    val created_at: String
)
// :core-network — GELEN cevap (müşteri bakiyesi)
data class BalanceDto(
    val customer_id: String,
    val balance_minor: Long,     // hareketlerin toplamı (backend hesaplar)
    val as_of: String
)
```
> Ayrıca `:core-data`'da Room için `TransactionEntity` (`@Entity`, `@PrimaryKey`)
> olacak. Yani: `Transaction` (domain) ↔ `TransactionEntity` (Room) ↔
> `TransactionDto` (JSON). Bu turda sadece domain + DTO taslaklanır.

---

## 5. Auth kararları

### app-pos (esnaf) — cihaz kaydı + token yenileme
- Cihaz **dükkanda sabit**, sürekli prizde, tek kullanıcı. Her açılışta şifre =
  gereksiz friction.
- İlk kurulumda **bir kez** giriş → backend uzun ömürlü **refresh token** verir →
  sonra her açılış **sessiz access token**. Esnaf bir daha şifre görmez.
- Square/SumUp/iZettle deseni.

### app-mobile (müşteri) — telefon + OTP
- Müşteri yaşlı/aceleci olabilir → friction düşman.
- **Telefon numarası + OTP (SMS kod).** Şifre yok, hatırlanacak bir şey yok.
  **Kayıt = giriş** (aynı akış, ayrı register ekranı yok). Sonra kullanıcı adı girer.

### Ortak — AuthInterceptor
- Token her isteğe `:core-network`'teki `AuthInterceptor` ile eklenir.
- Token'lar şifreli saklanır (EncryptedSharedPreferences / DataStore + Keystore).

---

## 6. Akışlar (özet — detay flow-pos.svg)

> **Ödeme ayrımı:** Tutar girişi + ödeme yöntemi (keypad, Kart/Yemek Kartı/Nakit)
> app-pos'a AİT DEĞİLDİR — Token'ın POS ödeme app'ine aittir. Repo'da bunu
> `mock-pos/` (ayrı Gradle projesi/APK) taklit eder. mock-pos'ta [VERESİYE]'ye
> basılınca app-pos bir **custom action intent** ile açılır
> (`com.example.app_pos.action.CREDIT` + `amount_minor` extra, Long kuruş). İki app
> birbirini import etmez; köprü sabitleri iki tarafta kopyalanır.
>
> app-pos **iki giriş noktalıdır:** (a) mock-pos'tan veresiye handoff'u ile
> doğrudan müşteri-seçme ekranından başlar, iş bitince `finish` ile mock-pos'a
> döner; (b) kendi launcher ikonuyla **bağımsız** açılır ve dashboard'dan başlar
> (esnaf ödeme olmadan müşteri/log görüntüler). Hangisinden başlanacağını
> `MainActivity` gelen intent'e bakarak seçer.
>
> **Ayrı task:** mock-pos, app-pos'u `FLAG_ACTIVITY_NEW_TASK` ile açar — app-pos
> KENDİ task'ında (ayrı recent-apps kartı) çalışır, çağıranın yaşam döngüsüne
> bağımlı olmaz. mock-pos gerçek POS ödeme app'inin taklidi olduğundan app-pos'un
> ona bağımlı olmaması hem doğru hem test için ayrılabilir.
>
> **Kimlik = telefon:** her müşteri bir numarayla takip edilir (claim akışının
> temeli); aynı numara iki kez olamaz, isim serbest. `claimStatus` ayrı eksen:
> yalnızca "app var (CLAIMED) / yok (UNCLAIMED)". Yeni müşteri: müşteri-seçme
> ekranında isim yaz → **telefon ekranı** (numara, benzersiz) → onay.
>
> **OTP onayı (her yazma/ödeme için):** satıcı keyfî borç yazamasın diye her
> DEBT/PAYMENT müşteri onayından geçer. `OtpService.requestOtp/verifyOtp` — şimdilik
> MOCK (backend yok, her kod geçer); imzalar sabit, backend gelince (FAZ 4/5) içleri
> değişir. app'li müşteride onay app-push, app'siz'de SMS OTP (kodda ayrık, ikisi
> mock). **Yazma yalnızca OTP başarılı olunca** olur (tek nokta: OtpViewModel).
>
> **Ödeme (PAYMENT) akışı:** müşteri detayında **[Ödeme Al]** → saleFlow → **keypad**
> (tutar) → onay → OTP → PAYMENT hareketi. Veresiye ile aynı onay+OTP+yazma
> pipeline'ını paylaşır (`SaleViewModel.txType`).
>
> **saleFlow giriş mimarisi (nav_graph):** saleFlow bir nested graph'tır; Navigation
> kuralı gereği dışarıdan yalnızca `startDestination`'ına girilebilir. `startDestination
> = keypadFragment` — keypad ORTAK GİRİŞ KAPISI. İki akış kapıda `amountMinor`'a göre
> ayrışır (`KeypadFragment.routeByEntry`): **DEBT** (mock-pos, `amountMinor>0`) keypad'i
> atlayıp müşteri-seçmeye geçer (`popUpTo` ile keypad geçmişten silinir); **PAYMENT**
> (`amountMinor==0`, müşteri belli) keypad'de kalıp tutarı aldırır. Bu, "iç node'a
> doğrudan navigate → crash" tuzağını çözen yapıdır.

**Flow A — POS (veresiye DEBT / ödeme PAYMENT, ortak pipeline):**
1. Her ikisi de saleFlow'un giriş kapısı **keypadFragment**'tan girer:
   **DEBT** — (ödeme app'inde) tutar → VERESİYE → app-pos (intent, `amountMinor>0`) →
   keypad tutarı görüp müşteri-seçmeye forward eder (keypad atlanır).
   **PAYMENT** — müşteri detayı → [Ödeme Al] (`amountMinor==0`, müşteri belli) → keypad
   açık kalır, tutar girilir.
2. Müşteri: listeden seç (numara hazır) **veya** yeni → **telefon ekranı** (benzersiz
   numara). *(QR/NFC credential devri hâlâ TBD, FAZ 8.)*
3. **Onay ekranı** (müşteri, tutar, mevcut + işlem sonrası bakiye — DEBT +, PAYMENT −).
4. **[Onaya Gönder]** → **OTP ekranı** (müşteri onayı; mock true) →
5. onay başarılı → append-only ledger'a **DEBT/PAYMENT hareketi** (UUID = idempotency;
   yeni müşteriyse önce kayıt oluşturulur) → bitiş **akış türüne bağlı**: DEBT +
   handoff ise `finish` (mock-pos'a dön), aksi halde (PAYMENT veya bağımsız) dashboard.
   *(Faz 1'de yazım FakeRepository'ye; Faz 3'te Room + outbox, Faz 4'te Sync POST.)*
6. Esnaf alacaklarını ledger'dan görür; **satışta yanlış geri = "iptal edilsin mi?"**
   onayı (girilen bilgi kazara kaybolmasın). Müşteri detayında **geri oku** listeye döner.

> **Reaktivite (Faz 1):** `FakeRepository` observable'dır — ledger bir
> `MutableStateFlow`, okumalar (`observeCustomers`/`observeTransactions`/
> `observeTotalReceivableMinor`) Flow döner. Veresiye yazılınca müşteri listesi,
> detay ve toplam alacak **canlı** güncellenir. Room DAO Flow'ları aynı davranacağı
> için ViewModel'ler Faz 3'te değişmeyecek.

**Flow B — Müşteri (app-mobile):**
1. QR/NFC okut → 2. app var mı? yoksa Play Store → ilk kurulum (telefon+OTP+isim)
   → 3. "Toplam borcum" sayfası → 4. ÖDE (şimdilik kart) / 5. Ayarlar
   (isim, kart, borç-ödeme listesi). Gelen QR "otomatik ekle" onayı ile bağlanır.

**Gün sonu:** POS lokal ledger toplamı == backend toplamı? → **reconciliation
raporu** (eşitlik kontrolü).

---

## 7. TODO — ileride (SQL field'larını önden tasarlamak için)

Bu turda **yapılmaz**, ama şema/DB tasarımını erken planlamak için not:

- **Customer modeli + claim akışı**
  ```kotlin
  enum class ClaimStatus { UNCLAIMED, CLAIMED }
  data class Customer(
      val customerId: String,
      val displayName: String,     // esnafın girdiği isim
      val phone: String?,          // claim edilince dolar
      val claimStatus: ClaimStatus,
      // authedFlag: POS'tan giden bilgide tracking için
  )
  ```
  Esnaf app'siz müşteriyi sadece isimle açar (UNCLAIMED). Müşteri sonra aynı
  telefonla girince backend eski borcu hesaba **CLAIM** eder.
- **Sync detayı:** `SyncBatch` (toplu gönderim), çakışma çözümü, `idempotency-key`
  header, `OutboxEntry` (lokal kuyruk tablosu).
- **Backend hesap logic'i** (enflasyona karşı, mikrokredi açıları için — field'lar
  önden): günlük artış/enflasyon oranı, faiz, geri ödeme vadesi. Faz 2, KVKK gate.
- **Ödeme yöntemi kayıtları:** kart token'ları; ileride satıcı-onaylı **offline
  ödeme** ("müşteri ödedim der → esnafa onay çıkar").
- **Credential yöntemi kararı:** QR mı NFC mi. İkisi de "müşteri app'inden
  customerId + auth devri" soyutlamasına oturur (emülatörde QR test daha kolay).
- **Auth implementasyonu:** OTP akışı, refresh token, EncryptedSharedPreferences.
- **Voice input** (esnaf için, confirmation şart) ve **insight/ML** (faz 2).
- **Satıcı hesabı / app-mobile birleşimi (TBD):** app-mobile yalnız müşteriyi değil
  satıcıyı da kapsayacak. Müşteri basit login (telefon+OTP) ile kaydolduktan sonra
  profil ekranındaki "satıcı hesabı girişi"nden satıcı credential'larını girer,
  POS'tan onay alır, aynı bilgileri DB'den çekip satıcı olarak devam eder.
  app-pos'un login-gerektiren müşteri-log görme/filtreleme özellikleri app-mobile'ın
  satıcı tarafında da bulunur (app-pos bağımsız açılışıyla aynı yetenek, iki
  client'ta paylaşılır). Detay zamanı gelince tasarlanacak.

---

## 8. Sonraki adımlar (uygulama sırası)

1. `shared-contracts/openapi.yaml` — ledger çekirdeği (transaction, balance, sync,
   yer tutucu `GET /insights`).
2. **app-pos: Compose → XML dönüşümü** + Gradle modüllerini (`:core-domain` vb.)
   oluştur.
3. app-pos UI katmanı (XML layout + Activity/Fragment) — statik/mock.
4. app-pos MVVM (ViewModel + StateFlow) + `:core-domain` modelleri.
5. app-pos `:core-data` (Room + Repository + outbox) + `:core-network` (Retrofit/mock).
6. → app-mobile → backend.
