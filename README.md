# Togg Robot 🚗

Android uygulaması: `https://configurator.togg.com.tr/` sitesine her dakika girer,
**T10F V2 RWD Uzun Menzil**'i seçmeye çalışır ve fiyata göre bildirim gönderir.

## Ne yapar?

1. **Basit alarm:** Versiyon seçilebiliyorsa ve fiyatı **2.600.000 TL**'den düşükse bildirim.
2. **Tam alarm:** Ardından süreci ilerletir, **Kış Paketi**, **Akıllı Destek Paketi**,
   **Kula** dış renk, **Siyah vegan deri** koltuk ve **Panoramik cam tavan** seçer.
   Hepsi hatasız seçilebildiyse ve son fiyat **2.500.000 TL**'den düşükse alarm sesiyle bildirim.

Bot asla "Sipariş / Ödeme / Satın al / Rezervasyon" butonlarına basmaz.

## Kurulum

1. GitHub'da bu deponun **Releases** bölümünden en yeni `ToggRobot.apk`'yı telefondan indir.
2. Kur (gerekirse "bilinmeyen kaynaklardan yüklemeye izin ver").
3. Uygulamayı aç → bildirim iznini ver → **Pil kısıtlamasını kaldır** → **Şimdi test et** ile
   botun siteyi doğru gezdiğini izle → **Başlat**.

## Site değişirse

Bot elemanları **yazılarına göre** bulur. Bir adım bulunamazsa uygulamadaki
"Anahtar kelimeler" bölümünden kelimeleri düzeltebilirsin (virgül = VE, `/` = VEYA).
"Sayfa metnini kopyala" ile çıktıyı alıp geliştiriciye gönderebilirsin.
