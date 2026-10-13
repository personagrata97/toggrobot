# Togg Robot 🚗

Android uygulaması. Togg konfigüratörünün (`configurator.togg.com.tr`) **kendi veri servisini**
düzenli aralıklarla okur. İstediğin konfigürasyon seçilebilir hale gelip fiyatı sınırının altına
düşünce alarm sesiyle bildirim gönderir.

## Nasıl çalışır?

Konfigüratör sayfası tüm verisini tek bir istekle çekiyor:
`bff.dfs.togg.cloud/.../getSmartDeviceProducts/v1?collectionHandle=t10f-configurator`.
Bu yanıtta her versiyonun seçenekleri (renk, koltuk, paketler, donanım) ve **gerçekten seçilebilen
konfigürasyonlar** (anahtar teslim fiyat, ÖTV oranı, teslim ayı) bulunuyor. Uygulama sayfayı açıp
butonlara tıklamıyor, sadece bu listeyi okuyor:

- Kontrol başına tek ve küçük bir istek (~50 KB, sıkıştırılmış hali daha da küçük). Resim, reklam ya da takip kodu indirmiyor.
- İki kontrol arasında telefon tamamen uyuyabiliyor; alarm anında yaklaşık 1 saniyeliğine uyanıyor.
- Arka planda sürekli çalışan bir servis ya da gizli tarayıcı yok.
- Site 403/429 gibi "yavaşla" yanıtları verirse kontrol aralığı kendiliğinden uzar (en fazla 15 dk).

## Robotlar

Birden fazla robot tanımlanabilir (ör. biri sana, biri arkadaşına). Her robotta şunlar ayarlanır:

- Model (T10F / T10X) ve versiyon (ör. *T10F V2 RWD Uzun Menzil*)
- Her seçenek için kabul edilen değerler, sitenin gerçek listesinden işaretlenir:
  - Renk: Kula veya Seyhan → ikisinden biri yeter
  - Meridian: **Yok** → zorla seçili gelirse alarm çalmaz
  - Siyah Renkte Tavan ve Yan Aynalar (çift renk): **Yok**
- Anahtar teslim fiyat sınırı
- İsteğe bağlı ek alarm: seçenekler tutmasa da versiyon belli bir fiyatın altına inerse haber verir

## Kurulum

1. Bu deponun **Releases** bölümünden en yeni `ToggRobot.apk` dosyasını telefona indirip kur.
2. Uygulamayı aç. İzin pencereleri sırayla kendiliğinden gelir (bildirim, pil); hepsine "İzin ver" de.
3. Robot kendiliğinden çalışmaya başlar. "Sitede şu an seçilebilenler" bölümünde gerçek veriyi görebilirsin.
