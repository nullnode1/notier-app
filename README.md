<p align="center"><img src="docs/images/notier-logo.png" width="80" alt="Siyah zemin üzerinde beyaz N biçimindeki Notier logosu"></p>

<h1 align="center">Notier</h1>

<p align="center">Android için sade bir Markdown not defteri. Klasörünü seç, notlarını yaz ve önizlemede oku.</p>

<p align="center"><b>Türkçe</b> · <a href="README.en.md">English</a> · <a href="README.ru.md">Русский</a></p>

![Notier arayüz çizimi. Solda açık temalı not listesi, sağda koyu temalı Markdown editörü ve net piksel simgeler.](docs/images/notier-overview.svg)

**Kotlin · Android 8 ve üzeri · GNU GPL v3**

## Notier ile neler yapabilirsin?

- **Klasörle çalış.** Cihazdan bir klasör seç, notları ada ya da tarihe göre sırala ve ara. Klasik liste, kompakt liste ve iki sütunlu ızgara arasında geçiş yap.
- **Markdown yaz ve oku.** Ham metin ile önizleme arasında geçiş yap. Başlık, görev listesi, bağlantı ve kod için editör kısayollarını kullan. Önizlemede kod bloklarını tek dokunuşla kopyala.
- **Drive’daki klasörlerini gez.** İzin verdiğinde mevcut klasörleri açabilir, Markdown notu oluşturabilir, taşıyabilir veya çöp kutusuna gönderebilirsin. Klasör kartları Markdown notlarını sayar.
- **Görünümü özelleştir.** Açık ve koyu temayı, 13 yazı tipini, Lucide, net piksel ve kalın çizgi simgelerini dene. Metin, arayüz ve düğme boyutları ile köşe yuvarlaklığını ayrı ayrı ayarla. Arayüz 42 dil içerir.

## Android’e kurulum

Bu deponun **Releases** bölümündeki imzalı APK’yı Android cihazında aç. İlk kullanımda sol üstteki klasör düğmesine dokun ve notlarını saklayacağın klasörü seç. Drive isteğe bağlıdır. Bulut ekranından Google hesabını seçip erişim verebilirsin.

## Termux’ta derleme

Projeyi Termux ana dizinine çıkardıktan sonra aşağıdaki komutları çalıştır. Kaynak klasörünün adı farklıysa `cd` satırını ona göre düzenle.

```bash
pkg install openjdk-21 aapt2 d8 apksigner python curl unzip
cd ~/Notier
bash derle.sh
```

Derlenen APK `build/Notier.apk` konumuna yazılır. Kendi imzanla derlediğin APK’daki Drive bağlantısı için [geliştirici kılavuzundaki OAuth adımlarını](docs/BUILD.tr.md#geliştiriciler-için-google-cloud) izle.

## Gizlilik ve lisans

- [Gizlilik politikası](docs/PRIVACY.tr.md) cihazda ve Drive’da hangi bilgilerin kullanıldığını, saklandığını ve erişimin nasıl geri alınacağını açıklar.
- Notier’in kaynak kodu [GNU Genel Kamu Lisansı, sürüm 3](LICENSE) ile yayımlanır. Google Play Hizmetleri için sınırlı bağlantı izni ve üçüncü taraf lisans bilgileri [NOTICE](NOTICE) dosyasındadır.
