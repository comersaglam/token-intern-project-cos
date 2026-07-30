# Notlar

## PhoneFormat.toStored idempotent değildi (Tur 16 — ÇÖZÜLDÜ)

Login ve register sistemini mock DB ile bağlarken bir bug alınmıştı: `PhoneFormat.toStored`
idempotent değildi — E.164 çıktısını ("+905554443322") tekrar girince null veriyordu
(11-hane-0-ile-başlar kontrolüne takılıyor). Bu üç ayrı yerde soruna yol açtı:
1. `login` E.164 numarayı tekrar toStored'dan geçirip null alıyor → session set edilmiyor
   (ama LoginVM yine SUCCESS diyordu → boş dashboard/profil, telefon görünmüyor).
2. `pendingPhoneDisplay` aynı çift-dönüşüm → register onay dialogu açılmıyor.
3. (dolaylı) registerUser'da fallback ile rakam-only saklanıyordu.

**ÇÖZÜLDÜ:** `toStored` idempotent yapıldı (zaten "+90…" olanı olduğu gibi döndürür);
çağrı yerlerinde çift-dönüşüm kaldırıldı; `login` findUserByPhone (digit-normalize) kullanır;
LoginVM `login()` dönüşünü kontrol eder (false→ERROR). Ders: format dönüştüren util'ler
idempotent olmalı.

## Session hardcoded u_owner döndürüyordu (Tur 16 — ÇÖZÜLDÜ)

`observeCurrentUser` / `currentSellerId` her zaman "u_owner" döndürüyordu (tek-user mock
kısayolu). Register ile çok-user gelince kırıldı — yeni numarayla login olsan bile eski
u_owner'ın bilgileri görünüyordu. **ÇÖZÜLDÜ:** `Session`'a `userId` eklendi; login o user'ın
id'sini saklar; observeCurrentUser + currentSellerId session.userId'ye bağlandı. Artık kim
login'se onun profili/ledger'ı görünür (backend JWT subject'ine köprü).
