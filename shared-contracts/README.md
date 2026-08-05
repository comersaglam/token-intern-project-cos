# shared-contracts

Veresiye platformunun **tek sözleşmesi**: app-pos, app-mobile ve backend aynı JSON'u
konuştuğuna bu dosyayla emin olur (kod paylaşmazlar; sadece bu şemaya uyarlar). Ayrıntılı
gerekçe: [../docs/veresiye-platform-tasarim.md](../docs/veresiye-platform-tasarim.md) §4.1.

- **`openapi.yaml`** — endpoint'ler + şemalar + seed-hizalı `example`'lar (tek gerçek).
- Endpoint body'lerinin okunur tasarımı: [../docs/api-endpoints.md](../docs/api-endpoints.md)
- Tablo şeması: [../docs/db-schema.md](../docs/db-schema.md)

## Prism ile mock server (backend'siz geliştirme)

Backend daha yazılmamışken app'ler bu mock'a gerçek API'ymiş gibi konuşabilir.

```bash
# Static/example mode (VARSAYILAN) — yaml'daki example'ları döner (gerçekçi, sabit, seed-hizalı).
npx @stoplight/prism-cli mock openapi.yaml

# Dinamik mode — şemadan RASTGELE üretir (example'ları YOKSAYAR). Sadece şema-uyumu testine yarar.
npx @stoplight/prism-cli mock -d openapi.yaml
```

> **DİKKAT (ters sezgi):** `-d` (`--dynamic`) rastgele üretir; **example'ları görmek için `-d`
> KOYMA** (default zaten static). İlk kez kurulumda `npx` Prism'i indirir (~30 sn); sonraki
> çalıştırmalar hızlı (~4 sn).

Ayağa kalkınca `http://127.0.0.1:4010` dinler. Örnek istekler (seed-hizalı yanıt döner):

```bash
curl -H "Authorization: Bearer x" http://127.0.0.1:4010/customers
#   -> c1..c5 (Ahmet 40,00 / Ayşe 165,00 / Mehmet 0 / Fatma 25,50 / Hasan 210,00)

curl -H "Authorization: Bearer x" http://127.0.0.1:4010/me/debts
#   -> [Ayşe Market 100,00 TL, Ahmet Bakkal 40,00 TL]

curl -H "Authorization: Bearer x" "http://127.0.0.1:4010/balances?customer_id=c1"
#   -> { balance_minor: 4000, ... }

curl -X POST http://127.0.0.1:4010/transactions \
  -H "Authorization: Bearer x" -H "Idempotency-Key: 11111111-1111-1111-1111-111111111111" \
  -H "Content-Type: application/json" \
  -d '{"transaction_id":"11111111-1111-1111-1111-111111111111","customer_id":"c1","amount_minor":5000,"type":"DEBT","description":"Veresiye"}'
#   -> 201 Transaction (Prism şemaya uygun yanıt döndürür)
```

Belirli bir örneği istemek (birden çok `examples` olursa): `Prefer: example=<ad>` header'ı.

## Lint (şema doğruluğu)

```bash
npx @redocly/cli lint openapi.yaml     # 0 error beklenir; warning'ler stil (operationId, license…)
```

## Seed (example'ların dayandığı tutarlı veri)

Example'lar iki client'ın `FakeRepository` seed'lerini kapsayan **tek birleşik** sete göre:
- **Kullanıcılar:** `u_owner` (Ahmet Bakkal, hem satıcı hem alıcı), `u_market` (Ayşe Market, satıcı),
  `u1` (Ahmet Yılmaz, alıcı), `u3` (Mehmet Kaya, alıcı).
- **Müşteriler:** `u_owner` defterinde c1..c5; `u1` iki dükkanda claim'li (c1 @ Ahmet Bakkal,
  m1 @ Ayşe Market); `o1` = u_owner'ın Ayşe Market'teki alıcı kaydı.
- **Bakiyeler:** u1 → Ahmet Bakkal 40,00 + Ayşe Market 100,00; u_owner defteri Fatma 25,50 / Hasan 210,00.

Backend gerçek seed'i bu sete yaklaşacak. Değerler `FakeRepository` KODUNDAN alındı (docs'tan değil).

## İleride

- **Kod üretimi (OpenAPI Generator):** Kotlin data class + Retrofit interface `openapi.yaml`'dan
  üretilebilir → elle model yazma hatası biter. FAZ 4 (`:core-network`) ile.
- **`future` tag'li endpoint'ler** (sync, settle, insights, credit-offers, devices) yer tutucudur;
  gerçek kullanım ileri fazlarda (bkz. api-endpoints.md Bölüm B).
