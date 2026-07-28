# Kotlin & Android — Terim Sözlüğü

Token stajı hazırlığı için Faz 0-8 boyunca görülen tüm kavramların sezgisel özeti. Her terimin yanında "neden var, ne zaman kullanılır, neyle karışır" notu var.

---

## FAZ 0 — Ortam ve Temel Kavramlar

| Terim | Sezgisel açıklama |
|---|---|
| **JVM** | Kotlin/Java kodunun derlendiği ara format (bytecode) ve bunu çalıştıran sanal makine. Kotlin'in Java kütüphaneleriyle %100 uyumlu olmasının sebebi bu — aynı JVM bytecode'una derleniyorlar. |
| **ART (Android Runtime)** | Android cihazının **gerçekte** kodu çalıştırdığı yer — JVM değil. Akış: Kotlin → JVM bytecode → (D8 ile) DEX bytecode → ART üzerinde çalışır. |
| **Android Studio** | IntelliJ IDEA + Android'e özel eklentiler (Emulator, Logcat, Layout Inspector, Profiler, APK Analyzer). |
| **Gradle** | Bağımlılık yönetimi + derleme + paketleme aracı — "pip + webpack"in birleşimi gibi düşün. `build.gradle.kts` dosyalarında bağımlılıklar (`implementation` = sadece bu modül görür, `api` = bağımlı modüller de görür) tanımlanır. |
| **Gradle Sync** | Build dosyası değişince "bağımlılıkları yeniden oku" komutu — `npm install`'a benzer ama IDE proje modelini de günceller. |
| **Kotlin DSL (.kts) vs Groovy (.gradle)** | Build script dili. Yeni projelerde `.kts` standart (tip güvenliği, autocomplete). |

---

## FAZ 1 — Kotlin Dili

| Terim | Sezgisel açıklama |
|---|---|
| **Null safety (`?`, `!!`, `?:`)** | `String?` = null olabilir, `String` = olamaz (derleyici garanti eder). `?.` = güvenli erişim (null ise sonuç null). `?:` = Elvis, null ise varsayılan değer. `!!` = "riski ben üstleniyorum" — çökme riskini gizlemiyor, **görünür ve aranabilir** yapıyor. |
| **`data class`** | Sadece veri taşıyan yapı (C struct'a, Python `@dataclass`'a benzer). Otomatik `equals`/`hashCode`/`toString`/`copy()` üretir. API modelleri, DB satırları, UI state için kullanılır. |
| **`sealed class`** | Kapalı sınıf hiyerarşisi — her alt tip **farklı veri taşıyabilir** (enum'un aksine). `when` ile birleşince derleyici tüm ihtimallerin karşılandığını garanti eder (exhaustiveness). Network sonucu, UI state modellemede standart. |
| **`enum class`** | Sabit liste, her üye **aynı şekle** sahip olmak zorunda. Farklı veri taşıyan alt tipler gerekiyorsa `sealed class` kullanılır. |
| **Extension function** (`fun String.ilkHarfBuyuk()`) | Var olan bir sınıfa (kaynağına dokunmadan) yeni "metod" ekleme. Aslında statik bir fonksiyona syntax şekeri — Android SDK sınıflarına (View, Context) yardımcı fonksiyon eklemek için kullanılır. |
| **Scope functions** (`let`, `run`, `with`, `apply`, `also`) | Bir obje üzerinde blok çalıştırma. `let`/`also` → `it` ile erişim; `run`/`apply` → `this` ile erişim. `apply`/`also` → objenin kendisini döndürür (zincirlemeye devam); `let`/`run` → blok sonucunu döndürür. `?.let { }` = "null değilse şunu yap" standart kalıbı. |
| **`suspend fun`** | "Askıya alınabilir" fonksiyon — thread'i **bloklamadan** bekleyebilir. Sadece başka bir `suspend fun` içinden ya da coroutine scope içinden çağrılabilir. |
| **`launch`** | Yeni bir coroutine başlatır, sonucu **almaz** (`Job` döner). Sonucunu kullanmayacağın işler için. |
| **`async` / `await`** | Yeni bir coroutine başlatır, sonuç **üretir** (`Deferred<T>` döner). `.await()` ile sonucu alırsın. **Yaygın hata:** `async{}.await()` aynı satırda yazmak, işi yanlışlıkla sıralı (sequential) hale getirir — önce tüm `async`'leri başlat, sonra hepsini `await` et. |
| **`delay()` vs `Thread.sleep()`** | `delay` = suspend, thread'i **serbest bırakır** (UI donmaz). `Thread.sleep` = **bloklar**, aynı thread'deki her şeyi durdurur (UI donar, ANR riski). |
| **Dispatcher** (`Main`/`IO`/`Default`) | Coroutine'in hangi thread(ler)de çalışacağını belirler. `Main` = tek thread (UI). `IO`/`Default` = gerçek thread havuzu. `launch` **otomatik olarak** thread pool'a atmaz — bu tamamen Dispatcher'a bağlı. |
| **`withContext`** | Var olan bir coroutine'in **ortasında geçici** Dispatcher değişimi — blok bitince önceki Dispatcher'a otomatik döner. (Örn: network IO'da, UI güncellemesi Main'de.) |
| **Yapısal Eşzamanlılık (Structured Concurrency)** | Parent-child coroutine ilişkisi. Parent iptal olursa **tüm child'lar otomatik iptal olur** — `viewModelScope`'un `onCleared()`'da her şeyi tek seferde temizleyebilmesinin temeli. |
| **`Job` vs `SupervisorJob`** | Normal `coroutineScope`: bir child patlarsa **tüm kardeşler** ölür. `supervisorScope`: bir child patlarsa **sadece o** ölür, kardeşler etkilenmez. Bağımsız paralel işler için `supervisorScope`. |
| **`withTimeout` / `withTimeoutOrNull`** | "Çok uzun sürerse vazgeç." İlki exception fırlatır, ikincisi `null` döner. |
| **`CoroutineExceptionHandler`** | Yakalanmamış hataları merkezi yönetme. `launch`'ta otomatik devreye girer; `async`'te sadece `.await()` çağrıldığında hata görünür hale gelir — `await()` unutulursa hata **sessizce kaybolur**. |
| **`Flow`** | Zaman içinde birden fazla değer yayan **cold** (soğuk) akış — sadece `.collect { }` çağrıldığında çalışmaya başlar, her collector bağımsız bir çalıştırma tetikler. `map`/`filter`/`combine` ile zincirlenir. |
| **Race condition** | İki coroutine aynı paylaşılan veriye aynı anda dokunursa (`sayac++` gibi OKU-ARTIR-YAZ üç adımı çakışırsa) veri kaybı olur. `await`/`join` bunu **engellemez** — sadece "ne zaman bitti" garantisi verir. `Mutex.withLock { }` gerçek çözüm — bloğa aynı anda tek coroutine girebilir. |
| **Singleton / `object`** | Uygulama boyunca **tek instance**. Constructor'ı yok, `new` ile oluşturulamaz. Network client, DB bağlantısı, logger gibi paylaşılan kaynaklar için. |
| **`companion object`** | Bir sınıfa bağlı, instance gerektirmeyen üyeler (Java'nın `static`'i). Factory metodları, sabitler için kullanılır. |

---

## FAZ 2 — Android Uygulama Temelleri

| Terim | Sezgisel açıklama |
|---|---|
| **Activity** | Kullanıcının etkileşime girdiği tek bir ekran. Modern Compose projelerinde genelde **tek Activity** + içinde Navigation ile gezinilen çoklu "ekran" (Composable) yaklaşımı standart. |
| **Fragment** | Activity içine gömülebilen, kendi lifecycle'ı olan UI parçası. Tablet desteği için doğdu. Compose Navigation ile yeni projelerde kullanımı azaldı, legacy kod tabanlarında görülür. |
| **Lifecycle** | `onCreate → onStart → onResume → onPause → onStop → onDestroy`. Sistem, Activity'yi **senin kontrolün dışında** herhangi bir an durdurup öldürebilir (ekran döndürme, bellek azlığı). ViewModel'in var olma sebebi bu. |
| **Config change (ekran döndürme)** | Varsayılan olarak Activity **tamamen yok edilip yeniden yaratılır** — elindeki düz değişkenler sıfırlanır. ViewModel bundan etkilenmez. |
| **Intent** | Bileşenler arası "bir şey yap" mesajı. **Explicit**: hedef belli (`Intent(this, DetayActivity::class.java)`). **Implicit**: hedef belli değil, sistem karar verir (`ACTION_VIEW`, `ACTION_SEND`). |
| **Context** | "Ortama erişim kartı" — kaynaklar, sistem servisleri, dosya erişimi buradan. **Activity Context** (kısa ömürlü, UI'a bağlı) vs **Application Context** (uygulama ömürlü). Activity Context'i singleton'da saklamak = memory leak. |
| **AndroidManifest.xml** | Uygulamanın kimlik kartı — izinler (`<uses-permission>`, örn. `INTERNET`), giriş noktası (`MAIN`/`LAUNCHER` intent-filter), `android:exported` (API 31+ zorunlu). |

---

## FAZ 3 — UI Katmanı

| Terim | Sezgisel açıklama |
|---|---|
| **XML View sistemi** | İmperatif yaklaşım — vanilla JS/DOM manipülasyonuna benzer. `findViewById` ile View bulunur, elle güncellenir. State ile UI senkronu **tamamen senin sorumluluğun**. |
| **View Binding** | `findViewById`'a tip-güvenli alternatif — Gradle build sırasında otomatik üretilen `ActivityMainBinding` sınıfı. |
| **RecyclerView** | View sisteminde liste gösterimi — görünmeyen öğeleri bellekten atıp performans sağlayan adapter pattern. Compose'daki karşılığı `LazyColumn`. |
| **Jetpack Compose** | Deklaratif UI — React'e zihniyet olarak çok yakın. "State şu, UI böyle görünsün" dersin, state değişince UI **otomatik** güncellenir. |
| **`@Composable`** | Bir fonksiyonu Compose derleyici eklentisine işaretleyen annotation. Syntaktik olarak normal fonksiyondan tek farkı bu — asıl iş derleme zamanında eklenen gizli izleme koduyla oluyor. |
| **`remember` / `mutableStateOf` / `by`** | `mutableStateOf(x)` = gözlemlenebilir bir kutu (React `useState`'e karşılık). `remember { }` = "bu değeri hafızada tut, her recomposition'da yeniden yaratma." `by` = `.value` yazmayı gizleyen syntax kolaylığı. |
| **Recomposition** | Bir state değiştiğinde, **sadece o state'i okuyan** Composable'ların otomatik yeniden çalıştırılması. React'in re-render + Virtual DOM diff mantığına yakın, ama "hangi fonksiyon hangi state'i okudu" takibiyle daha ince taneli. |
| **`Modifier`** | Boyut/boşluk/tıklanabilirlik — CSS'in zincirleme fonksiyon hali. **Sıra önemli**: `.padding().background()` ile `.background().padding()` farklı görsel sonuç verir. |
| **State hoisting** | State'i Composable'ın kendi içinde değil, çağıran tarafta tutup parametre olarak geçirme (React'teki "controlled component"). Composable'ı test edilebilir/yeniden kullanılabilir yapar. |
| **`LaunchedEffect(key) { }`** | React `useEffect(fn, [key])` karşılığı. `key` değişmedikçe blok tekrar çalışmaz — recomposition'ın gereksiz yere network isteği tekrarlamasını önler. |
| **`@Preview`** | Emulator açmadan Composable'ı Android Studio içinde canlı görüntüleme. |
| **Material Design 3 (M3)** | Hazır tasarım sistemi (renk, tipografi, hazır component'ler: `Button`, `Card`, `TextField`). "Nasıl güncelleniyor" (Compose mekanizması) ile ilgisi yok, sadece "nasıl görünüyor" sorusuna cevap. |

---

## FAZ 4 — Jetpack Mimari Bileşenleri

| Terim | Sezgisel açıklama |
|---|---|
| **ViewModel** | State'in "gerçek evi." Config change'lerden etkilenmez (Activity yok olup yeniden yaratılsa bile ViewModel hayatta kalır). Ekran back stack'ten tamamen çıkana kadar yaşar. |
| **`viewModelScope`** | ViewModel'in ömrüyle senkron coroutine scope. ViewModel `onCleared()` olunca içindeki **tüm** coroutine'ler otomatik iptal olur (Faz 1'deki yapısal eşzamanlılık burada devrede). |
| **`StateFlow` / `MutableStateFlow`** | "Değiştiğinde haber veren kutu." `MutableStateFlow` = okunur+yazılır (ViewModel içinde `private`). `StateFlow` = sadece okunur (dışarıya, `private` olmayan alanla sunulur). Bu ayrım "state'i kim değiştirebilir" sınırını derleyici seviyesinde garantiler. |
| **`StateFlow` vs `LiveData`** | LiveData eski, Android'e özel, ana thread'e otomatik geçiş yapar. StateFlow yeni, coroutine tabanlı, Android-dışı Kotlin projelerinde de çalışır — artık standart. |
| **`collectAsState()`** | ViewModel'deki `StateFlow`'u Compose'a bağlar. İçeride **zaten `remember` + `LaunchedEffect` kullanır** — yeni bir mekanizma değil, bu ikilinin hazır paketlenmiş hali. |
| **Room** (`@Entity`, `@Dao`, `@Database`) | SQLite üzerine tip-güvenli ORM. `@Entity` = tablo tanımı (`data class`). `@Dao` = sorgu arayüzü (interface, implementasyonu Room derleme zamanında üretir). `Flow<List<T>>` dönen sorgular, tablo değişince otomatik günceller. |
| **DataStore** | Basit anahtar-değer saklama, coroutine/Flow tabanlı, asenkron. Eski **SharedPreferences**'ın (senkron, tip güvensiz) yerini aldı. |
| **Navigation Component** | Ekranlar arası geçiş — React Router'ın Compose karşılığı. `NavHost` = `<Routes>`, `composable("route")` = `<Route>`, `navController.navigate()` = `navigate()`. Route string'leri URL gibi düşünülebilir, `{id}` dinamik segment. |
| **WorkManager** | Uygulama kapansa/cihaz yeniden başlasa bile **garantili** çalışan arka plan işleri (Python'daki Celery/cron'a benzer, Android'in pil optimizasyonuna uyumlu). Koşullu çalıştırma (`Constraints`: sadece internet varken vb.) destekler. |

---

## FAZ 5 — Asenkron Programlama (Derinlemesine)

> Not: Bu fazın çoğu terimi Faz 1'de zaten listelendi (structured concurrency, SupervisorJob, withTimeout, CoroutineExceptionHandler, Flow, withContext) — bkz. yukarısı. Ek olarak:

| Terim | Sezgisel açıklama |
|---|---|
| **`coroutineScope { }` vs `supervisorScope { }`** | İlkinde bir child'ın hatası **tüm kardeşleri** iptal eder ve dışarı fırlatılır. İkincisinde sadece patlayan child etkilenir, kardeşler işine devam eder. |
| **`combine` (Flow operatörü)** | İki (ya da daha fazla) `Flow`'u birleştirir — herhangi biri yeni değer yayınladığında, en güncel ikisiyle yeni bir sonuç üretir. StateFlow'ları birbirine bağlarken kullanılır. |

---

## FAZ 6 — Networking

| Terim | Sezgisel açıklama |
|---|---|
| **OkHttp** | Ham HTTP istekleri atan düşük seviye kütüphane. Retrofit dahil hemen hemen her şeyin altında bu çalışır. Python `requests`'e benzer, ama JSON parse'ı elle yapman gerekir. |
| **Retrofit** | Annotation'lı (`@GET`, `@POST`, `@Path`, `@Query`, `@Body`), deklaratif REST client. Bir `interface` tanımlarsın (gövdesiz), Retrofit gerçek implementasyonu **runtime'da otomatik üretir** (`retrofit.create(...)`). FastAPI'nin tersine — orada sunucu route'u tanımlıyordun, burada client sözleşmesi tanımlıyorsun. |
| **kotlinx.serialization** | JSON ↔ Kotlin objesi dönüşümü, **derleme zamanında** kod üretir (`@Serializable`), reflection yok — en hızlı, null-safety'e tam uyumlu. Artık standart tercih. |
| **Moshi / Gson** | Eski/legacy JSON kütüphaneleri, **reflection tabanlı** (çalışma zamanında sınıfın içine bakarak keşif yapar) — daha yavaş, null safety'i Kotlin kadar iyi desteklemez. |
| **Interceptor** | Her isteğe otomatik müdahale noktası (auth header ekleme, logging) — Express/FastAPI middleware'ine birebir aynı fikir. `chain.proceed(...)` çağrılmazsa istek hiç gitmez. |
| **`Response<T>`** | Retrofit'in HTTP status code'una göre ince taneli hata yönetimi sağlayan sarmalayıcı tip (`isSuccessful`, `code()`, `body()`). |
| **Ktor Client** | JetBrains'in Kotlin-native network client'ı. Retrofit'in annotation+interface yaklaşımının aksine, doğrudan fonksiyon çağrısı tabanlı. Kotlin Multiplatform'da (Android+iOS+backend paylaşımı) tercih edilir; Android-only'de Retrofit hâlâ daha yaygın. |

---

## FAZ 7 — Mimari Pattern'ler

| Terim | Sezgisel açıklama |
|---|---|
| **Repository pattern** | ViewModel ile Room/Retrofit arasındaki köprü. "Veriyi nereden getireceğim" kararını (cache-first mi, network-first mi) izole eder — ViewModel bu kararın detayını bilmez. |
| **MVVM** | View (Compose) ↔ ViewModel (StateFlow tutan "beyin") ↔ Model (Repository+Room+Retrofit, tek kutu olarak düşünülür). Basit ekranlar için yeterli. |
| **MVI** | MVVM'nin daha katı hali — **tek** bir state objesi (`data class`), **tek** giriş noktası (`onIntent`), kullanıcı eylemleri `sealed class` ile kapalı liste olarak tanımlanır. Çok state'li/etkileşimli ekranlar (form, filtre) için. |
| **Clean Architecture** | MVVM'nin "Model" kutusunu ikiye açar: **Domain** (saf Kotlin, Android'e hiç bağımlı değil — UseCase + Repository interface) ve **Data** (Room/Retrofit'in gerçek implementasyonu). **Dependency Rule**: oklar sadece içe (Domain'e) doğru akar, Domain hiçbir katmanı tanımaz. |
| **UseCase** | Tek bir iş kuralını temsil eden sınıf — Repository'nin "nereden" sorusundan bağımsız, "nasıl yapılmalı" kuralını (validasyon, iş mantığı) izole eder. `operator fun invoke` ile `useCase(x)` şeklinde çağrılabilir hale getirilir. |
| **`operator fun invoke`** | Bir sınıf instance'ını fonksiyon gibi çağırabilmeni sağlayan Kotlin özelliği (`obj(x)` = `obj.invoke(x)`). |

---

## FAZ 8 — Dependency Injection

| Terim | Sezgisel açıklama |
|---|---|
| **DI'nin çözdüğü problem** | Bağımlılık zincirini (Retrofit→Repository→UseCase→ViewModel) elle her yerde kurmak yerine, framework'e "ben şuna ihtiyacım var" deyip otomatik aldırmak. |
| **Hilt** | Google'ın resmi DI çözümü — **derleme zamanında** kod üretir (annotation processing). Hatalar derleme zamanında yakalanır ama derleme süresi artar. |
| **`@HiltAndroidApp`** | Uygulamanın DI konteynerini başlatan işaret (`Application` sınıfına). |
| **`@Module` / `@InstallIn`** | "Bu sınıf Hilt'e bağımlılık tarifleri veriyor" + "bu tarifler ne kadar süre geçerli" (`SingletonComponent` = uygulama ömrü). |
| **`@Binds`** | Kendi yazdığın bir interface'i kendi implementasyonuna bağlama (`LedgerRepository` → `LedgerRepositoryImpl`). Az kod, sadece eşleştirme. |
| **`@Provides`** | 3. parti sınıfların (Retrofit, Room) kurulum tarifini yazma — gerçek bir fonksiyon gövdesi gerekir. |
| **`@Singleton`** | "Sadece bir kere üret, hep aynı instance'ı geri ver." |
| **`@Inject constructor`** | "Bu sınıfın bağımlılıklarını Hilt otomatik bulup geçirsin." |
| **`@HiltViewModel` / `hiltViewModel()`** | ViewModel'e otomatik bağımlılık enjeksiyonu — Compose'da hiç manuel kurulum yapmadan `hiltViewModel()` ile hazır ViewModel alınır. |
| **Koin** | Annotation'sız, **çalışma zamanında** çözümleme yapan alternatif DI. `module { single { ... } }` ile düz Kotlin DSL'i kullanılır — kod üretimi yok, derleme daha hızlı, ama hatalar runtime'da ortaya çıkar. |
| **`single` vs `factory` (Koin)** | `single` = tek instance (Hilt `@Singleton` karşılığı). `factory` = her istekte yeni instance. |

---

## Hızlı Çapraz Referans — Hangi Kavram Neye Benziyor

| Kotlin/Android | Senin bildiğin karşılığı |
|---|---|
| `data class` | Python `@dataclass`, C struct |
| Null safety (`?`, `?.`, `?:`) | TypeScript `strictNullChecks` |
| `suspend fun` / coroutine | Python `async def` / `asyncio` (ama `await` örtük) |
| `@Composable` + `remember`/`mutableStateOf` | React component + `useState` |
| Recomposition | React re-render + Virtual DOM diff |
| `LaunchedEffect(key)` | React `useEffect(fn, [key])` |
| State hoisting | React "controlled component" |
| `Modifier` zinciri | CSS (ama sıra görsel sonucu değiştirir) |
| Navigation Component | React Router |
| Room (`@Entity`/`@Dao`) | SQLAlchemy Model + Repository |
| Retrofit interface | (ters yönde) FastAPI route tanımı |
| Interceptor | Express/FastAPI middleware |
| `.use { }` (OkHttp) | Python `with open(...) as f:` context manager |
| DI (Hilt/Koin) | FastAPI `Depends()` sistemi |

---

*Faz 0'dan Faz 8'e kadar olan öğrenme sürecinin özeti. Sonraki fazlar: Test (JUnit/MockK/Compose UI Test), Build sistemi derinlemesine, Yayınlama & DevOps.*
