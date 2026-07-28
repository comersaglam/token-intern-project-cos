================================================================================
VERESİYE DİJİTAL TAKİP SİSTEMİ — MİMARİ VE REPO TASARIMI
================================================================================
Token stajı projesi için tasarım notları
Stack: Kotlin / Android (XML views) + Android Studio
--------------------------------------------------------------------------------


================================================================================
1. GENEL MİMARİ
================================================================================

Sistem üç ana parçadan oluşur. POS app ve müşteri app'i "client" (frontend)
tarafındadır; bunların KENDİNE AİT AYRI BİR BACKEND'İ YOKTUR. İkisi de tek bir
backend'e (senin "cloud" dediğin şey) API üzerinden konuşur.

Yani "pos backend" + "app backend" + "cloud" diye üç ayrı şey değildir; bunların
üçü aynı backend servisidir.


        ESNAF TARAFI                      MÜŞTERİ TARAFI
   +----------------------+         +------------------+
   |  app-pos             |         |  app-mobile      |
   |  (POS cihazı,        |         |  (login/kontrol/ |
   |   Kotlin/Android)    |         |   ödeme)         |
   +----------+-----------+         +--------+---------+
              |      HTTPS (REST/gRPC)         |
              +---------------+----------------+
                              v
                    +---------------------+
                    |   Backend API        |  <- senin "cloud"un
                    |   auth, veresiye     |
                    |   kayıtları, ödeme,  |
                    |   senkronizasyon     |
                    +----------+----------+
                               v
                         +----------+
                         | Database |  (backend'in sahibi olduğu tek DB)
                         +----------+


NET CEVAPLAR:

- "Frontend ve backend aynı anda mı yazılıyor?"
  Mobil/POS app'te "frontend/backend" ayrımı yoktur. App zaten kendi içinde
  UI + iş mantığı + API çağrılarını barındıran bütün bir client'tır. Ayrı bir
  "pos-backend" repo'suna gerek yoktur.

- "Cloud backend'ine ne kadar gerek var?"
  Tam olarak bir tane. Database yönetimi de request işleme de o backend'in işi.
  "Request yönlendirme" (gateway/routing) genelde ayrı bir repo değil, backend
  içindeki bir router / altyapı katmanıdır (küçük projede yeterli).


================================================================================
2. CİHAZ <-> BACKEND İLİŞKİSİ: OFFLINE-FIRST
================================================================================

Cihaz her zaman backend'e mi request atmalı? Bir spektrum var:

  a) THIN CLIENT (hep online)
     Her işlem = API çağrısı, cihazda mantık yok.
     POS İÇİN KÖTÜ: internet gidince dükkan çalışmaz, veresiye yazılamaz.

  b) OFFLINE-FIRST (local-first)  <-- SENİN İÇİN DOĞRUSU
     Cihazın kendi lokal DB'si var (Android'de Room/SQLite). İşlemler önce
     lokalde yapılır, cihaz OFFLINE bile çalışır, internet gelince arka planda
     backend'e sync eder. Backend "source of truth" olarak çakışmaları çözer
     ve yedek tutar.

  c) TAMAMEN LOKAL (cloud yok)
     Çoklu cihaz yok, yedek yok, insight yok. Uygun değil.

Endüstride ciddi POS sistemleri (Square, SumUp, iZettle, Toast) offline-first +
sync yapar.


   app-pos (cihaz)                     Backend (source of truth)
   +--------------------+              +----------------------+
   | UI                 |              |  API                 |
   | iş mantığı         |  -- sync --> |  reconciliation      |
   | LOKAL DB (Room)    | <-- sync --  |  merkezi DB          |
   |  -> offline çalışır|              |  -> tüm cihazların   |
   +--------------------+              |     tek gerçeği      |
                                       +----------------------+

"Cihazda çalışan kısım" ve "cloud'a gönderilen kısım" İKİSİ DE VARDIR — ama aynı
veriyi konuşurlar. Cihaz kendi başına iş yapabilir, backend hakemdir.


--------------------------------------------------------------------------------
EN KRİTİK TASARIM KARARI: APPEND-ONLY LEDGER
--------------------------------------------------------------------------------

Veresiye'yi "bakiye" (değişebilen tek bir sayı) olarak TUTMA. Sadece hareketleri
sakla (append-only ledger):

    Ahmet: +50 (ekmek borcu)  10:00
    Ahmet: +30 (süt borcu)    11:00
    Ahmet: -80 (ödeme)        15:00
    -> bakiye = hareketlerin toplamı, hiçbir yerde "0" diye yazmıyorsun

Kazandırdıkları:
  - SYNC KOLAYLAŞIR: iki cihaz aynı anda yazsa bile hareketler çakışmaz, sadece
    eklenir (last-write-wins dramasına girmezsin).
  - IDEMPOTENCY: her hareketin bir transaction_id'si olur; aynı ödeme retry'da
    iki kez işlenmez. Para işlerinde şart.
  - DENETLENEBİLİRLİK: kim, ne zaman, ne yazdı bellidir. Fintech'te (üstelik Koç
    bünyesinde) paha biçilmez.

Bu event-sourcing / ledger mantığı, finansal bir domain'de "junior çözüm" ile
"senior çözüm" arasındaki farktır.


================================================================================
3. CLIENT-SIDE (app-pos İÇİ) KATMANLI MİMARİ
================================================================================

Local DB + logic "UI'dan DB'ye yaz" kadar basit DEĞİL, ama tam teşekküllü bir
backend de DEĞİL — ortada bir yer. Android app'in İÇİNDE şu katmanlar olur:

    UI (XML layout + Activity/Fragment)
       |
    ViewModel  (StateFlow, ekran durumu)
       |
    Repository  <- KİLİT KATMAN: "veriyi lokalden mi backend'den mi alacağıma
       |            ben karar veririm" burada yaşar
       +-- Local data source  (Room DAO'lar, offline'ın kalbi)
       +-- Remote data source (Retrofit API client)
       |
    Sync engine  (arka planda WorkManager: kuyruk, retry, çakışma çözümü)

Bu "logic" şunları içerir (basit değil):
  - Room şeması + DAO'lar (append-only ledger burada tutulur)
  - OUTBOX PATTERN: offline yazılan her hareket "gönderilmeyi bekleyen" kuyruğa
    girer, internet gelince sync olur
  - IDEMPOTENCY + RETRY: transaction_id ile aynı hareket iki kez gitmez
  - CONFLICT RESOLUTION: ledger olduğu için kolay, yine de "server bunu gördü mü"
    kontrolü var

ÖNEMLİ: Bu bir client-side offline-first mimarisidir, bir SUNUCU DEĞİLDİR.
HTTP dinlemez, başka cihazlara servis vermez, DB'nin tek sahibi backend'dir.
Bu yüzden AYRI BİR REPO'YA GEREK YOKTUR — katmanlar app-pos'un içinde MODÜL
olarak ayrılır (repo ile değil).


--------------------------------------------------------------------------------
3.1 "APP BACKEND'İ DİNLEMELİ Mİ?" — HAYIR, CLIENT DİNLEMEZ, ÇAĞIRIR
--------------------------------------------------------------------------------

"Dinlemek" (listening) = port açıp gelen bağlantıları beklemek = SUNUCU
davranışı. Backend :8080'de dinler. app-pos DİNLEMEZ, iletişimi hep O BAŞLATIR:

    app-pos  ---- "yeni hareket kaydet" ---->  Backend    (app başlatır)
    app-pos  <--- cevap --------------------   Backend

    Backend  --X-->  app-pos     (backend kendiliğinden app'e bağlanamaz:
                                  NAT arkası, değişken IP, cihaz uykuda)

Yani app'in içinde "gelen istekleri karşılayan router" YOKTUR; olması gereken
bir şey de değildir. Sunucudaki router'ın (POST /transactions -> şu fonksiyon)
app'te karşılığı yoktur, çünkü app'e kimse istek atmaz.

Backend bir şey söylemesi gerekirse üç yol var (hiçbiri "app dinliyor" değil):
  1. POLLING / PERİYODİK SYNC — app sorar: "son sync'ten beri yenilik var mı?"
  2. PUSH NOTIFICATION (FCM) — Android İŞLETİM SİSTEMİ Google ile bağlantı
     tutar, mesaj gelince app'i uyandırır. Sen sadece callback yazarsın, port
     açmazsın.
  3. WEBSOCKET — yine bağlantıyı APP başlatır, sonra açık tutar. Veresiye için
     muhtemelen gereksiz (pil + karmaşıklık maliyeti).

KATMANLAR BUNU NASIL BÖLÜYOR?

  core-network   Retrofit client, HTTP çağrıları, auth header, JSON.
                 GİDEN çağrıları yapar. Router değil, tam tersi: "istemci".
  core-data      Room DAO + Repository + SYNC ENGINE. "Ne zaman network'e
                 gideyim, ne zaman lokalden okuyayım" kararı. Outbox, retry.
  core-domain    Ledger kuralları, modeller. Ağdan TAMAMEN habersiz, HTTP bilmez.

"Router tarzı bir şey" diye aradığın rol = core-data'daki SYNC ENGINE. Ama işlevi
"gelen istekleri yönlendirmek" değil, "GİDEN işleri zamanlamak":

    Kullanıcı "Ahmet'e 50 TL yaz" dedi
            |
    core-domain: hareket geçerli mi? (ledger kuralları)
            |
    core-data:  -> Room'a yaz (anında, offline olsa bile OK)
                -> outbox kuyruğuna ekle
            |
    Sync engine (WorkManager, internet varken uyanır):
                -> kuyruktaki hareketleri al
                -> core-network üzerinden backend'e POST et
                -> başarılıysa kuyruktan sil
                -> başarısızsa retry (transaction_id sayesinde çift işlenmez)


--------------------------------------------------------------------------------
3.2 ARKA PLAN ÇALIŞMA — UI'SIZ BİLEŞEN, AYRI APP DEĞİL
--------------------------------------------------------------------------------

"App sürekli açık durmuyor, arka planda çalışan bir şey lazım" — DOĞRU. Ama bu
ayrı bir app/backend değil, AYNI APK'nın içindeki UI'sız bir bileşendir:

    app-pos (tek APK)
    +-- UI katmanı        -> Activity/Fragment, kullanıcı bakarken çalışır
    +-- arka plan katmanı -> WorkManager / Service, UI kapalıyken de çalışır
                             (aynı Room DB'ye, aynı Repository'ye erişir)

KÖRÜ KÖRÜNE POLLING ANDROID'DE ÇALIŞMAZ:
Modern Android (6.0+) arka planı agresif kısıtlar (Doze, App Standby, background
execution limits). "Her 30 saniyede istek at" loop'unu sistem öldürür/erteler.
WorkManager periyodik işlerde MİNİMUM PERİYOT 15 DAKİKADIR, OEM'ler (Samsung,
Xiaomi, Huawei) daha da geciktirebilir. Yani sürekli polling = pil düşmanı +
güvenilmez.

DOĞRU DESEN: ZAMANA DEĞİL, OLAYA BAĞLA

  TETİKLEYİCİ                NE ZAMAN            ARAÇ
  Kullanıcı işlem yaptı      anında (outbox)     WorkManager one-time
  İnternet geri geldi        otomatik            WorkManager NetworkType.CONNECTED
  App/ekran açıldı           foreground'a gelince ViewModel / lifecycle
  Backend'de yenilik var     anında              FCM push ("uyan, sync et")
  Emniyet ağı                15-30 dk'da bir     WorkManager periodic

KİLİT FİKİR: Backend'de yenilik olduğunu öğrenmek için sürekli sormana gerek yok
— backend FCM ile "yenilik var" diye DÜRTSÜN, sen sync'i çalıştır. Müşteri
app-mobile'dan ödeme yapınca kasadaki cihaza anlık haber vermenin doğru yolu bu.
Sürekli polling'e göre hem anlık hem çok daha ucuz.

POS CİHAZI ÖZEL DURUM (senin lehine):
POS cihazları normal telefon değil — genelde sürekli prizde (pil kaygısı yok),
tek amaçlı / kiosk-dedicated modda, ekran sürekli açık. Bu şartlarda FOREGROUND
SERVICE kullanılabilir: kalıcı bildirimle çalışan, sistemin öldürmediği servis.
Böylece gerçekten sürekli ayakta bir sync bileşenin olur. MDM ile pil
optimizasyonundan muaf tutulabilir.
=> app-mobile'da BUNU YAPMA (normal telefon, pil önemli, kullanıcı rahatsız
   olur): orada FCM + app açılınca sync doğru yaklaşımdır.
   İki client, iki farklı strateji, AYNI core-data katmanı.

YÖN MESELESİ (akışı basitleştirir):
  - CİHAZDAN ÇIKAN veri (veresiye yazma): polling gerekmez. Kullanıcı yazar ->
    Room -> outbox -> internet varken gönderilir. Tetikleyici kullanıcının
    kendisi.
  - CİHAZA GELEN veri (müşteri ödemesi, başka kasadan giriş): asıl "haberdar
    olma" ihtiyacı burada. Çözüm: FCM + açılışta sync.

Sonuç: ihtiyacın "sürekli sorgulayan arka plan app'i" değil, OLAYLARA TEPKİ
VEREN BİR SYNC BİLEŞENİ.

AÇIK KARAR: Token'daki POS cihazı dedicated/kiosk donanım mı, yoksa normal
Android tablet gibi mi kullanılacak? -> Foreground service mi WorkManager+FCM mi
sorusunu bu belirliyor.


================================================================================
4. REPO / GITHUB TASARIMI: TEK MONOREPO
================================================================================

Branch'ler bu iş için DEĞİLDİR. Branch = aynı kod tabanının farklı zaman/
versiyonları (feature, fix, release). Üç ayrı parçayı branch'lere koymak yanlış
kullanımdır.

Doğru soru: 3 ayrı repo mu, 1 monorepo mu? -> Senin durumunda TEK MONOREPO.

    veresiye-platform/           <- TEK repo
    +-- app-pos/                 (Android, Kotlin, XML)
    |   +-- app/                 UI (XML layout + Activity/Fragment)
    |   +-- core-data/           Room + Repository + Sync
    |   +-- core-domain/         iş kuralları, modeller (ledger mantığı)
    |   +-- core-network/        Retrofit API client
    |
    +-- app-mobile/              (Android, müşteri tarafı — aynı katmanlı yapı,
    |                             daha ince)
    |
    +-- backend/                 (senin "cloud"un)
    |   +-- api/                 endpoint'ler
    |   +-- domain/              veresiye/ödeme kuralları
    |   +-- db/                  migration'lar, şema
    |   +-- (opsiyonel) analytics/  faz 2 ML buraya
    |
    +-- shared-contracts/        (OpenAPI — app'ler ve backend aynı modeli
    |                             konuşsun)
    +-- docs/                    (mimari, README)
    +-- README.md                <- her şeyin haritası


NEDEN MONOREPO (senin için):
  - Tek yerde durur, 50 repo arasında kaybolmaz.
  - Bağlantı besbelli — klasör yapısı mimariyi anlatır.
  - Atomik değişiklik — API şemasını değiştirince backend + iki app'i TEK PR'da
    güncellersin.
  - Tek portföy parçası — mülakat/mezuniyet için bütünlüklü tek şey.

3 AYRI REPO ne zaman mantıklı? Ayrı takımlar, ayrı release döngüleri, ayrı erişim
izinleri gerektiğinde. Senin senaryonda bunların hiçbiri yok.


--------------------------------------------------------------------------------
BRANCH vs KLASÖR — ZAMAN EKSENİ vs MEKAN EKSENİ
--------------------------------------------------------------------------------

  KLASÖRLER = "hangi parça"   (POS mu, backend mi) -> kalıcı, hep yan yana
  BRANCH'LER = "hangi iş"     (sesli giriş özelliği, şu bug fix) -> geçici,
                               sonra main'e merge

                        Branch'lerde tutmak      Monorepo (klasörler)
  Üç parça aynı anda      Hayır (tek branch)      Evet (hepsi yan yana)
   diskte mi?
  İkisine birlikte        Hayır                   Evet
   bakabilir misin?
  Tek PR'da backend+app   İmkansız                Evet
   değiştirir misin?
  Git'in doğasına uygun?  Hayır (amaç dışı)       Evet

Branch kullanımı (doğru):
    main branch (kararlı, çalışan hal)
    veresiye-platform/  <- app-pos + backend + app-mobile HEPSİ burada

    feature/voice-input branch
    veresiye-platform/  <- yine hepsi burada, ama app-pos'a sesli giriş
                           eklenmiş hali. Bitince main'e MERGE.


--------------------------------------------------------------------------------
MONOREPO'YU PROFESYONEL GÖSTEREN ŞEYLER
--------------------------------------------------------------------------------

1. KÖK README bir "harita" olsun: her alt klasör ne, nasıl bağlanıyor
   (ASCII diyagram), her biri nasıl çalıştırılıyor. İlk 30 saniyede sistem
   anlaşılmalı.

2. COMMIT PREFIX'leri kullan:
       feat(pos): offline ledger sync eklendi
       fix(backend): idempotency retry hatası
       chore(contracts): OpenAPI ödeme modeli güncellendi

3. HER ALT KLASÖR BAĞIMSIZ ÇALIŞSIN: kendi README + kendi çalıştırma komutu.
   Monorepo "her şey birbirine yapışık" demek değil; sınırlar net kalır.

UYARI: Android + backend farklı dil/build sistemleri. Gerçek bir "birleşik build"
(Bazel gibi) kurmaya kalkma — overkill. Monorepo sadece "aynı Git repo altında
yan yana klasörler" demek. Her klasör kendi dünyasında derlenir.


--------------------------------------------------------------------------------
"BAĞIMSIZ KLASÖRLER" DURUMU — NORMAL, AMA TUTKAL EKLE
--------------------------------------------------------------------------------

Üç klasörün KOD OLARAK bağımsız olması İYİ ve olması gerekendir (biri diğerini
import etmez). Ama tamamen ilgisiz görünmemeli:

  - Kök README (sistemin haritası) ekle.
  - shared-contracts (API şeması) — detay için bkz. bölüm 4.1.

KONTROL — iç içe .git var mı? (olmamalı, kökte tek .git olmalı)
    # repo kökünde:
    find . -name ".git" -maxdepth 2
Sadece ./.git çıkmalı. ./app-pos/.git gibi iç içe .git çıkarsa düzeltilmeli.


--------------------------------------------------------------------------------
4.1 shared-contracts NE İŞE YARAR?
--------------------------------------------------------------------------------

Amaç: app-pos, app-mobile ve backend'in AYNI DİLİ konuştuğuna emin olmak. Kod
paylaşmıyorlar (biri diğerini import etmiyor) ama aynı JSON'u konuşmak
zorundalar. O anlaşmanın yazılı hali burada durur.

PROBLEM (contract olmayan dünya):
  Backend yazdı:   { "customerId": "...", "amount": 5000,  "type": "DEBT" }
  App gönderdi:    { "customer_id": "...", "amount": 50.0, "kind": "debt" }
  -> 3 hata: alan adı, kuruş vs lira, enum adı. Hiçbiri derleme zamanında
     yakalanmaz; ÇALIŞMA ZAMANINDA, üstelik PARA HATASI olarak patlar.

ÇÖZÜM: tek bir yazılı sözleşme

    shared-contracts/
    +-- openapi.yaml     <- tek gerçek: endpoint'ler, alan adları, tipler

    Transaction:
      properties:
        transaction_id: { type: string, format: uuid }   # idempotency için
        customer_id:    { type: string }
        amount_minor:   { type: integer }   # KURUŞ. float yok.
        type:           { enum: [DEBT, PAYMENT] }
        created_at:     { type: string, format: date-time }

ASIL KAZANÇ — bundan OTOMATİK ÜRETİM (sadece doküman değil):
  - Kotlin data class + Retrofit interface OTOMATİK üretilir (OpenAPI Generator).
    Elle model yazmazsın -> yazım hatası yapamazsın. Şema değişirse kod yeniden
    üretilir, uyumsuzluk DERLEME HATASI olarak çıkar (çalışma zamanında değil).
  - MOCK SERVER bedava gelir — Prism'i openapi.yaml'a doğrultursun, backend hiç
    yazılmamışken app'i gerçek API'ye konuşuyormuş gibi geliştirirsin.
  - Backend tarafı da doğrulanır (response'lar şemaya uyuyor mu testi).

BAĞIMLILIK YÖNÜ (monorepo'daki "tutkal"):

            shared-contracts/openapi.yaml
            (kimseye bağımlı değil)
               ^            ^            ^
          app-pos      app-mobile     backend
          (üretir)      (üretir)      (uyar)

Üçü de şemaya bakar, birbirlerine bakmaz. Monorepo faydası: şema + backend + iki
app TEK PR'da güncellenir. Ayrı repo olsa üç PR'ı senkron tutman gerekirdi.

NE ZAMAN GEREKSİZ? Tek kişi, tek app, 5 endpoint -> overkill olabilir. AMA senin
durumunda İKİ AYRI CLIENT aynı backend'e konuşacak (uyumsuzluk riski 2 katı) ve
domain para/ledger (kayma toleransı sıfır). Bu iki sebep haklı çıkarıyor.

BAŞLANGIÇ İÇİN MİNİMUM: openapi.yaml'a sadece ledger çekirdeği — transaction
oluştur, müşteri bakiyesi çek, sync endpoint'i. Yer tutucu olarak GET /insights
eklenirse faz 2 baştan planlanmış görünür.


================================================================================
5. EK ÖZELLİK FİKİRLERİ
================================================================================

--------------------------------------------------------------------------------
VOICE INPUT / VOICE AGENT — iyi oturuyor
--------------------------------------------------------------------------------
Esnafın elleri meşgul; "Ahmet'e 50 lira ekmek yazdım" deyip kaydın otomatik
girilmesi POS'ta ciddi friction azaltır.

Uyarılar:
  - Türkçe ASR + gürültülü dükkan ortamı = doğruluk düşer, test et.
  - PARA SÖZ KONUSU: mutlaka confirmation UX ("Ahmet, 50 TL, onaylıyor musun?").
    Sesli girişin yanlış anlaması sessizce borç yazmamalı.
  - İsim eşleştirme (hangi Ahmet?) NLU tarafında ayrı problem — isim + son işlem
    context'iyle çözülür.

--------------------------------------------------------------------------------
MICRO CREDIT SYSTEM - based on user behavior
--------------------------------------------------------------------------------
Kullanıcıların alışkanlıklarına, opsiyonel olarak kredi kartı harcama ve bakiyeleri
(bankadan alınabilecek bilgilere göre) göz önünde bulundurularak onlara haftalık 
vs oranında vadeli, bikaç yüz veya bikaç bin tl tarzı aslında veresiyeyi satıcı değil
bankaya yapıyormuş tarzında kredi kartlarındaki esnek hesaptan daha cömert ancak kredi
almak gibi uzun vade veya hesap işlem yükü olmayan, ödeme esnasında kullanılabilecek
düşük faizli ve vadeli şekilde işleyen. kredi kartının asgarisine dahil olmasından 
daha cömert olan şekilde, bir bankanın müşteri çekmek için kullanabileceği tarzda bir
uygulama.
Detayları TBD olarak duruyor şu anda

--------------------------------------------------------------------------------
ML / DATA SCIENCE INSIGHT — yön doğru, ama FAZ 2
--------------------------------------------------------------------------------
MVP ölçeğinde gerçek ML için yeterli veri olmaz; başta basit analytics/heuristik
>> ML. Asıl değerli olan: elindeki veri enformel KREDİ verisidir (veresiye =
kayıt dışı borç).

Kurulabilecek güçlü ürün açıları:
  - KREDİ/ÖDEME RİSK SKORU: "Bu müşteriye veresiye vereyim mi?" sinyali. Küçük
    esnaf için mini kredi bürosu gibi. (Fintech'in kalbi.)
  - NAKİT AKIŞI TAHMİNİ: "bu hafta ~X TL tahsilat bekleniyor".
  - BÖLGESEL BENCHMARK: "veresiye vaden mahalle ortalamasından uzun".

İki gerçek engel:
  - KVKK: Token'da bir GATE olacak, süs değil. "Anonymize edip ML'e sokarım"
    kulağa basit gelir ama doğru anonimizasyon zordur (re-identification riski,
    özellikle bölgesel + küçük veri kümesinde). Cross-merchant benchmark ayrı bir
    rıza/hukuk boyutu getirir. Ekibe erken sor.
  - VERİ HACMİ: bölgesel insight için çok dükkanın verisi gerekir, MVP'de olmaz.

Pratik: insight'ı önce TEK DÜKKAN İÇİ basit istatistikle başlat (müşteri risk
listesi, tahsilat takvimi). ML + bölgesel karşılaştırmayı FAZ 2 yap. Bu fintech'e
"veri governance'ı biliyorum" mesajı verir — cool bir modelden çok puan getirir.


================================================================================
6. GELİŞTİRME YOL HARİTASI — FAZLAR
================================================================================

Tüm projenin yaklaşık planı. Her faz kendi içinde küçük adımlara bölünür; her
adımda açıklama + onay + progress.md güncellemesi.

--------------------------------------------------------------------------------
FAZ 0 — TEMEL  [TAMAMLANDI]
--------------------------------------------------------------------------------
  - Monorepo yapısı (app-pos, app-mobile, backend, shared-contracts, docs)
  - Mimari + akış şemaları: docs/architecture-pos.svg, docs/flow-pos.svg,
    docs/architecture-pos.md
  - app-pos & app-mobile: Compose template -> XML views dönüşümü
    (AGP 9'da Kotlin built-in; ayrı Kotlin plugin YOK, viewBinding açık)
  - Cihazda çalışan ilk XML ekranı doğrulandı

--------------------------------------------------------------------------------
FAZ 1 — app-pos UI + MVVM İSKELETİ (SAHTE VERİ)   [TAMAMLANDI]
--------------------------------------------------------------------------------
Üç çekirdek ekran, her biri ViewModel + StateFlow ile, veri kaynağı sahte:

    Ekran 1: Tutar + ödeme yöntemi -> AYRI APP'E TAŞINDI (mock-pos; bkz. bölüm 7
       "ÖDEME AYRIMI"). VERESİYE -> intent ile app-pos açılır (tutar dolu).
    Ekran 2: Müşteri seçme (arama) -> müşteriye tıkla -> Ekran 2b
    Ekran 2b: ONAY EKRANI (müşteri, tutar, mevcut + işlem sonrası bakiye) ->
       [Onayla ve Yaz] -> append-only ledger'a DEBT hareketi yazılır (UUID
       transactionId, idempotency). Onay LOKAL'dir (esnafın yanlış girişini önler);
       müşteriye mobil bildirim FAZ 8 (client dinlemez, çağırır).
    Ekran 3: Müşteri listesi (RecyclerView) + toplam alacak + filtre/arama
    Ekran 4: Müşteri detayı — borç/ödeme geçmişi (ledger hareketleri)

FakeRepository artık OBSERVABLE (ledger MutableStateFlow; observeCustomers/
observeTransactions/observeTotalReceivable Flow döner). Veresiye yazılınca liste,
detay ve toplam alacak CANLI güncellenir. Room'a geçince DAO Flow'ları aynı
davranacağı için ViewModel'ler değişmeyecek (offline-first "sıfır refactor" ödülü).

NEDEN UI ÖNCE, CONTRACT SONRA? Tasarımın orijinal "contract-first" tavsiyesinden
bilinçli sapma: contract, BACKEND'E BAĞLANILACAĞI AN için doğru kuraldır.
Ekranların hangi alanlara ihtiyaç duyduğu bilinmeden yazılan şema sonradan
değişir. Önce UI ihtiyacı görülür, sonra contract ona göre yazılır (FAZ 2).

NEDEN SAF PREVIEW DEĞİL, VIEWMODEL İLE BİRLİKTE? Ekranı sahte veriyle besleyen
StateFlow'lu ViewModel baştan yazılırsa, gerçek veri gelince ekran ve ViewModel
DEĞİŞMEZ (sadece sahte repository yerine gerçek Repository konur) -> sıfır
refactor. MVVM ayrımı da baştan doğru oturur (mantık Activity'ye sızmaz).

********************************************************************************
SIRALAMA GÜNCELLEMESİ (Tur 13): app-mobile ÖNE ALINDI.
Aşağıdaki faz NUMARALARI korunuyor (referanslar kırılmasın) ama UYGULAMA SIRASI
değişti. Yeni sıra:
  1) FAZ 2 (domain + User modeli)  <-- bu turda YAPILDI (aşağıda "DURUM")
  2) FAZ 6 (app-mobile UI, mock üstünde) — contract ve backend'den ÖNCE
  3) shared-contracts/openapi.yaml — iki client'ın gerçek ekran ihtiyacı görülünce
  4) FAZ 3 (Room), 5) FAZ 4/5 (ağ+backend), 6) FAZ 7 (regülasyon)
NEDEN: iki client mock'la çalışınca contract gerçek ihtiyaca göre yazılır +
gösterilebilir somut demo çıkar. app-mobile, :core-domain'i KOPYALAR (ayrı Gradle
projesi; mock-pos'un MoneyFormat kopyaladığı desen); gerçek paylaşım backend fazında.
********************************************************************************

--------------------------------------------------------------------------------
FAZ 2 — DOMAIN + USER MODELİ   <-- bu turda YAPILDI (contract app-mobile'dan sonraya kaydı)
--------------------------------------------------------------------------------
DURUM (Tur 12-13): :core-domain modülü kuruldu; Customer/Transaction taşındı;
User + SellerInfo eklendi; Customer'a claimedByUserId; FakeRepository'ye User seed +
backend-ready metodlar (findUserByPhone/observeCurrentUser/registerUser/setSeller
çalışır, claimCustomerForUser imza+TODO). Cihazda derleniyor, mevcut akışlar değişmedi.

User modeli: TEK User + isBuyer/isSeller bool flag'ler (iki rol aynı anda aktif
olabilir) + SellerInfo (satıcı bilgisi ayrı class -> derleyici korur) + nullable
alanlar (email). Ayrıntı: yukarıda "CUSTOMER != USER" kararı + docs/architecture-pos.md §4.

  - :core-domain modülü: Transaction, Customer, User, TransactionType, ClaimStatus
    (saf Kotlin; Android/Room/Retrofit importu YOK). ✓
  - shared-contracts/openapi.yaml: ARTIK app-mobile'dan SONRA (yukarı bkz.). FAZ 1+
    app-mobile'da ortaya çıkan gerçek alan ihtiyacına göre yazılır. Minimum: transaction
    oluştur, müşteri bakiyesi, sync, user/auth; yer tutucu GET /insights.
  - KVKK burada bir TASARIM KISITI olarak devreye girer (bkz. FAZ 7): hangi veri
    neden tutuluyor, UNCLAIMED kayıt hangi hukuki sebeple? Alanlar buna göre.

--------------------------------------------------------------------------------
FAZ 3 — LOKAL KALICILIK (ROOM)
--------------------------------------------------------------------------------
  - :core-data modülü: Room şeması + DAO'lar, append-only ledger tabloları
  - Repository (kilit katman): "lokalden mi backend'den mi" kararı
  - Sahte repository yerine gerçek Room; UI ve ViewModel değişmez (MVVM ödülü)

--------------------------------------------------------------------------------
FAZ 4 — AĞ + SYNC
--------------------------------------------------------------------------------
  - :core-network: Retrofit client, DTO'lar, AuthInterceptor (Bearer token)
  - Outbox pattern + WorkManager sync engine, idempotency (transaction_id), retry
  - Mock server (Prism ile openapi.yaml'dan) ile backend'siz uçtan uca test

--------------------------------------------------------------------------------
FAZ 5 — BACKEND
--------------------------------------------------------------------------------
  - API endpoint'leri (contract'a uyar), DB şeması + migration'lar
  - Ledger yazma/okuma, idempotency kontrolü, reconciliation (gün sonu eşitlik)

--------------------------------------------------------------------------------
FAZ 6 — app-mobile (MÜŞTERİ)
--------------------------------------------------------------------------------
  - Telefon + OTP (kayıt = giriş, düşük friction), kullanıcı adı
  - "Toplam borcum" sayfası, ödeme (kart), ayarlar, borç/ödeme listesi
  - app-pos ile AYNI core katmanları, farklı sync stratejisi (pil önemli)

--------------------------------------------------------------------------------
FAZ 7 — REGÜLASYON & UYUMLULUK
--------------------------------------------------------------------------------
ÖNEMLİ: Bu bir "ekstra özellik" DEĞİLDİR. Bankalarla/ödeme kuruluşlarıyla
iletişimde olan bir uygulama için CANLIYA ÇIKMANIN ÖN KOŞULUDUR — ürünü
tamamlayan zorunlu katman. Bu yüzden ileri özelliklerden ÖNCE gelir.

  - KVKK: aydınlatma metni, açık rıza akışı, veri saklama/silme süreleri,
    veri minimizasyonu. UNCLAIMED kayıt (esnafın app'siz müşteriyi kaydetmesi)
    başlı başına bir rıza/hukuki sebep sorusudur — erken düşünülmeli.
  - ÖDEME REGÜLASYONU: kart verisi HİÇ SAKLANMAZ -> tokenizasyon; kart bilgisi
    lisanslı ödeme kuruluşuna gider, bizde sadece token durur (PCI-DSS
    kapsamından kaçınma stratejisi).
  - DENETİM İZİ (audit log): ledger zaten append-only; ayrıca kim-ne-zaman-ne
    yaptı kaydı ve değiştirilemezlik garantisi.
  - VERİ İKAMETGAHI: verilerin Türkiye'de tutulması (KVKK yurt dışı aktarım).
  - LİSANS SORUSU: faiz / vade / mikrokredi hesabı yapılacaksa bu FİNANSAL
    HİZMET sayılır -> BDDK lisans boyutu. Ürün kapsamını belirler, Token ekibine
    ERKEN sorulmalı.

--------------------------------------------------------------------------------
FAZ 8 — İLERİ ÖZELLİKLER
--------------------------------------------------------------------------------
  - QR / NFC credential devri (müşteri tanımlama), yöntem kararı
  - FCM push ("yenilik var, sync et" / "yeni borç")
  - Claim akışı: UNCLAIMED müşteri -> telefonla giriş -> eski borcu devral
  - Voice input (confirmation şart), insight / ML (KVKK gate — bkz. bölüm 5)
  - SATICI HESABI / app-mobile BİRLEŞİMİ: artık User.isBuyer/isSeller ile MODELLENDİ
    (TBD değil — bkz. "CUSTOMER != USER" kararı). app-mobile tek app; telefon+OTP ile
    giriş (oto-kayıt, isBuyer=true), profildeki "Satıcı ol" -> isSeller=true + SellerInfo
    (registration + POS eşleme). Satıcı olunca app-pos'un müşteri-log görme/filtreleme
    yetenekleri app-mobile'ın satıcı tarafında da açılır (aynı yetenek iki client'ta
    paylaşılır = app-pos "bağımsız açılış" yeteneği). UI uygulaması app-mobile turunda.


================================================================================
ÖZET — TEK CÜMLELER
================================================================================
- 3 parça: app-pos, app-mobile, backend (+ shared-contracts).
- Client'ların ayrı backend'i yok; hepsi tek backend'e konuşur.
- Backend = source of truth + tek DB'nin sahibi.
- Client DİNLEMEZ, ÇAĞIRIR. App'in içinde router yok; core-network (giden çağrı)
  + core-data'daki sync engine (ne zaman gideceğine karar veren akıl) var.
- Arka plan işi ayrı app değil, aynı APK'da UI'sız bileşen (WorkManager/Service).
- Körü körüne polling yapma; olaya bağla (kullanıcı işlemi, ağ geldi, app açıldı,
  FCM push, 15-30 dk emniyet ağı). POS dedicated ise foreground service uygun.
- app-pos offline-first çalışır (Room + outbox + sync), backend hakem.
- Veresiye'yi bakiye değil append-only LEDGER olarak tut.
- Hepsi TEK monorepo'da yan yana klasör (branch değil).
- Branch = "hangi iş", klasör = "hangi parça".
- shared-contracts = üçünün aynı JSON'u konuşma garantisi + kod/mock üretimi.
- Başlangıç: UI+ViewModel (sahte veri) -> contract -> Room -> ağ (bkz. bölüm 6).
- Voice input iyi (confirmation şart). ML faz 2 (KVKK gate).
- Regülasyon ekstra özellik değil, canlıya çıkmanın ön koşulu (FAZ 7).
================================================================================


================================================================================
7. ALINAN KARARLAR
================================================================================
Bu belge boyunca açık bırakılan sorular ve tartışma sonucu verilen kararlar.
(Kararlar tartışılarak alındı; gerekçeleri docs/architecture-pos.md'de detaylı.)

--------------------------------------------------------------------------------
MİMARİ
--------------------------------------------------------------------------------
- ÖDEME AYRIMI: ÖDEME app-pos'un PARÇASI DEĞİLDİR.
  Token'ın POS cihazlarında zaten ayrı bir ödeme uygulaması var. Ödeme ekranı
  (keypad + tutar + Kart/Yemek Kartı/Nakit) ONA aittir; veresiye bizim AYRI
  app'imizdir. Repo'da bunu 'mock-pos' klasörü (ayrı Gradle projesi, ayrı APK,
  applicationId com.example.mock_pos) TAKLİT eder — POS'un gerçek ödeme app'inin
  yerine geçer.
    AKIŞ: mock-pos'ta tutar girilir -> [VERESİYE] -> app-pos bir INTENT ile açılır.
    KÖPRÜ: custom action 'com.example.app_pos.action.CREDIT' + extra 'amount_minor'
           (Long, kuruş). app-pos MainActivity'de eşleşen intent-filter + onCreate
           intent okuması var. İki app birbirini import ETMEZ (ayrı APK); köprü
           sabitleri iki tarafta KOPYALANIR (ileride shared-contracts'a alınabilir).
    İKİ GİRİŞ NOKTASI: app-pos hem (a) mock-pos'tan veresiye handoff'u ile (doğrudan
           müşteri seçme ekranından başlar, iş bitince finish -> mock-pos'a döner),
           hem de (b) KENDİ LAUNCHER İKONUYLA BAĞIMSIZ açılır (dashboard'dan başlar).
           (b) şart çünkü esnaf ödeme olmadan da müşteri/log görüntüleyebilmeli.
           Hangisinden başlanacağını MainActivity gelen intent'e bakarak seçer
           (Android'de app-to-app handoff'un standart deseni).
    NOT (finish tetikleyicisi): ledger yazımı henüz yok; handoff'ta 'iş bitti'
           şimdilik müşteri seçimine bağlı. Onay/ledger adımı gelince finish oraya
           taşınacak. İleride setResult ile başarı/iptal mock-pos'a döndürülebilir.

- KİMLİK = TELEFON NUMARASI + OTP ONAYI (Tur 9).
  Her müşteri bir telefon numarasıyla takip edilir (claim akışının temeli); aynı
  numara iki kez olamaz, isim serbest. claimStatus artık SADECE "app var/yok"
  eksenidir (numara her kayıtta dolu). Yeni müşteri: isim + numara (benzersiz).
  HER veresiye (DEBT) ve ödeme (PAYMENT) MÜŞTERİ OTP ONAYINDAN geçer — satıcı
  keyfî borç yazamasın. OtpService.requestOtp/verifyOtp şimdilik MOCK (backend
  yok, her kod geçer); imza sabit, backend gelince (FAZ 4/5) içi değişir. Yazma
  yalnızca onay başarılıysa (tek nokta). Ödeme akışı müşteri detayındaki [Ödeme
  Al] → keypad → onay → OTP; veresiye ile aynı pipeline (SaleViewModel.txType).
  AÇIK GERİLİM (backend'de çözülecek): OTP zorunlu ↔ offline-first. OTP internet
  ister; tasarım "offline yazabilmeli" diyordu. Şimdilik mock bypass; backend
  gelince offline'da onay politikası (ör. PENDING durumu) yeniden kararlaştırılacak.

- MODÜL YAPISI: ÇOK MODÜLLÜ GRADLE.
    :app          -> UI (Activity/Fragment + XML layout + ViewModel)
    :core-domain  -> saf Kotlin; modeller + ledger kuralları (bağımlılığı YOK)
    :core-data    -> Room + Repository + Sync engine + outbox
    :core-network -> Retrofit + DTO + AuthInterceptor
  Bağımlılık yönü: :app -> :core-data -> {:core-domain, :core-network};
                   :core-network -> :core-domain; :core-domain -> (hiçbir şey)
  NEDEN: Sınırları DERLEYİCİ zorlar. Tek modülde katman ayrımı sadece disipline
  bağlıdır; domain'e yanlışlıkla @Entity eklemek derlenir ve mimari erozyonu
  (big ball of mud) başlar. Çok modülde bu DERLEME HATASI olur. Para/ledger
  domain'inde bu gerçek bir kazanç.
  NOT: FAZ 1'de kod henüz :app içinde; modüllere ayırma FAZ 2-4'te yapılır
  (var olan kodu taşımak, boş modüllere dosya dağıtmaktan öğreticidir).

- UI TEKNOLOJİSİ: XML VIEWS + ViewBinding (Compose değil).
  ViewBinding = XML'deki id'li view'lar için otomatik üretilen tip-güvenli sınıf
  (findViewById yerine). AGP 9'da Kotlin BUILT-IN gelir -> ayrı kotlin-android
  plugin'i EKLENMEZ (eklenirse "Cannot add extension with name 'kotlin'" hatası).

--------------------------------------------------------------------------------
VERİ
--------------------------------------------------------------------------------
- PARA TİPİ: amountMinor: Long — KURUŞ cinsinden. 50 TL = 5000.
  Float/Double YOK (kayan nokta para hatası yapar). BigDecimal de değil
  (Room/JSON serileştirmede ekstra converter yükü, bu ölçekte gereksiz).

- LEDGER: append-only. Bakiye hiçbir yerde tek sayı olarak TUTULMAZ;
  bakiye = hareketlerin toplamı. Her hareketin transaction_id'si (UUID) var
  -> idempotency (retry'da çift işlenmez).

- MÜŞTERİ MODELİ: claimStatus (UNCLAIMED / CLAIMED).
  Gerçek dünyada müşterilerin çoğu app kullanmaz. Esnaf sadece İSİM girerek
  müşteri açabilir (UNCLAIMED). Müşteri sonra aynı telefon numarasıyla giriş
  yapınca backend eski borç geçmişini o hesaba CLAIM eder. Sistemin gerçek
  dünyada çalışmasını sağlayan şey budur.

- CUSTOMER != USER (İKİ AYRI KAVRAM, Tur 13'te netleşti — KARIŞTIRMA):
  * Customer = SATICININ DEFTER KAYDI (app-pos'un bildiği). UNCLAIMED = arkasında
    hesap OLMAYAN, esnafın yazdığı isim+telefon. Hesap değil.
  * User = APP-MOBILE HESABI (telefon+OTP ile giriş). TEK model, rol iki BOOL:
    isBuyer (herkes böyle başlar) + isSeller ("Satıcı ol" ile açılır). İki rol
    AYNI ANDA aktif olabilir. Satıcı bilgisi ayrı data class SellerInfo (shopName,
    shopPhone) -> "isSeller=true ise sellerInfo dolu" kuralını DERLEYİCİ korur.
  * KÖPRÜ = CLAIM: User telefonuyla girince, o numaralı UNCLAIMED Customer CLAIMED
    olur, Customer.claimedByUserId ile o User'a bağlanır (eski borç devralınır).
    İlişki VERİDE tutulur (telefon eşleşmesine güvenilmez) -> Room'da FK, backend'de
    join. Model :core-domain'de mevcut (User.kt); claim MANTIĞI app-mobile turunda.
  * GERÇEK DB NE ZAMAN (iki katman, karışıyor): lokal kalıcılık = FAZ 3 (Room,
    CİHAZDA — telefonun içinde SQLite, sunucu değil). Docker'lı sunucu-DB + gerçek
    endpoint'ler = backend fazı; ondan önce openapi'dan mock server (Prism) ile prova.

- ÜÇ AYRI TEMSİL: Transaction (domain) / TransactionEntity (Room) /
  TransactionDto (JSON). Değişme sebepleri farklı olduğu için ayrılır;
  aralarında mapper fonksiyonlarıyla çevrilir.

--------------------------------------------------------------------------------
AUTH  (bölüm 2/3'te açık bırakılmıştı)
--------------------------------------------------------------------------------
- app-pos (ESNAF): CİHAZ KAYDI + TOKEN YENİLEME.
  İlk kurulumda bir kez giriş -> backend uzun ömürlü REFRESH TOKEN verir ->
  sonra her açılışta sessiz ACCESS TOKEN. Esnaf bir daha şifre görmez.
  (Square/SumUp/iZettle deseni. Cihaz dükkanda sabit, tek kullanıcı.)

- app-mobile (MÜŞTERİ): TELEFON + OTP (SMS kod).
  Şifre yok, hatırlanacak bir şey yok. KAYIT = GİRİŞ (aynı akış, ayrı register
  ekranı yok). Sonra kullanıcı adı girilir.
  NEDEN: müşteri yaşlı/aceleci olabilir; friction düşman. Şifreli register
  kötü, sosyal login Google hesabı şartı getirir.

- ORTAK: Token her isteğe :core-network'teki AuthInterceptor (OkHttp) ile
  otomatik eklenir; her Retrofit fonksiyonuna elle yazılmaz. Token'lar şifreli
  saklanır (EncryptedSharedPreferences / DataStore + Keystore).

--------------------------------------------------------------------------------
POS DONANIMI  (bölüm 3.2'de "AÇIK KARAR" olarak işaretlenmişti -> KAPANDI)
--------------------------------------------------------------------------------
- KARAR: NORMAL ANDROID TELEFON varsayımı. (Senior eng onayı: medium-size
  Android telefon emülatörü yeterli.) Cihazın sürekli prizde olacağı biliniyor
  ama şimdilik FOREGROUND SERVICE KULLANILMIYOR.
- Sync stratejisi: WorkManager (network-triggered) + app açılışında sync;
  FCM push ileride (FAZ 8). app-mobile ile AYNI core-data katmanı.
- Foreground service / kiosk modu ileride gerekirse eklenir (dedicated donanıma
  geçilirse). Şimdi eklemek emülatörde test zorluğu + erken karmaşıklık getirir.

--------------------------------------------------------------------------------
GELİŞTİRME YÖNTEMİ
--------------------------------------------------------------------------------
- Sıra: UI+ViewModel (sahte veri) -> contract -> Room -> ağ -> backend ->
  app-mobile -> regülasyon -> ileri özellikler. (Detay: bölüm 6)
- Kısa adımlar, her adımda açıklama + onay, progress.md güncellemesi.
- Overengineering yapma; ihtiyaç doğmadan soyutlama ekleme.
================================================================================




--------------------------------------------------------------------------------
Ek özellik updateleri
--------------------------------------------------------------------------------

- Alış satış zamanını ve parasını tutuyoruz ya. başka bir db tablosunda da web fetch ile o saatteki dolar euro ve altın dönüşümlerini de tutabiliriz ileride. Böylece microcredit veya enflasyona satıcının yenilmemesi tarzı konuları implemente etmekte geriye dönük bilgimiz her zaman db de olur ve webfetch ile zaman kaybetmeyiz bu hesaplarda.