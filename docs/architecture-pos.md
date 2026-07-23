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

**Flow A — POS (esnaf veresiye yazar):**
1. Tutar gir → 2. Ödeme yöntemi (VERESİYE) → 3. Müşteri tanımla:
   **credential (QR/NFC — TBD)** → CLAIMED, veya **elle isim** → UNCLAIMED →
   4. **Onay ekranı** (para işi, şart) → 5. domain doğrular → 6. Room'a yaz +
   outbox (offline OK) → 7. Sync Engine POST (retry, idempotency).
2. Esnaf alacaklarını ledger'dan görür (müşteri bazlı / genel toplam).

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
