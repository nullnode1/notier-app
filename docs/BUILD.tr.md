# Notier geliştirici kılavuzu

Notier, seçtiğin Android klasöründeki UTF-8 Markdown notlarını açan ve düzenleyen bir Kotlin uygulamasıdır. Android 8.0 ve üzerini destekler. Hedef API 36'dır.

## Kullanım

1. Sol üstteki **klasör** simgesine dokun. Android dosya seçicisinde örneğin `Belgeler/Notier` klasörünü seç. Android 11 ve sonrasında doğrudan İndirilenler klasörü seçilemeyebilir. Bir alt klasör seçebilirsin.
2. Seçili klasördeki `.md` ve `.markdown` dosyaları son değişiklik tarihine göre sıralanır. Üstteki arama alanı dosya adlarını yazdıkça filtreler. **×** aramayı temizler. Sağ alttaki **+** yeni not açar.
3. Var olan bir not **Görünüm** ile açılır. Göz ikonu görünümü açar, kalem ham Markdown metnini düzenler, disket dosyayı kaydeder. Çerçeveli düğme etkin modu gösterir. Düğmelerin solundaki **Not başlığı** alanı, kaydederken `.md` dosyasının adını belirler.
4. Sağ üstteki **üç nokta → Farklı kaydet** ile ayrı bir `.md` dosyası oluşturabilirsin. Mevcut not üzerinde düzenleme yaparken bu seçenek kopyayı yazar ve orijinal notun düzenleyicisini açık bırakır.
5. Kaydedilmemiş değişiklikler, uygulama kapanırsa geri yüklenmek üzere cihazda taslak olarak tutulur.
6. Ana ekrandaki sıralama ikonuyla tarih veya başlık sırasını seçebilirsin. Bir notun menüsündeki **Markdown paylaş** ham Markdown metnini Android'in paylaşım ekranına gönderir.

Bir notun üzerine basılı tutarak doğrudan düzenleyebilir, yeniden adlandırabilir, sabitleyebilir veya silebilirsin. Sabitlenen notlar, seçtiğin sıralamaya göre listenin başında görünür. Silmeden önce onay ekranı açılır.

## Galeri

1. **Ayarlar → Galeri** bölümünde **Cihaz** ve **Google Drive** sekmelerini kullan. Cihaz sekmesinde Android’in dosya seçicisini aç. Drive sekmesinde Google hesabını bağla, istediğin klasöre gir ve **Bu klasörü kullan** seçeneğine dokun. İki seçim ayrı saklanır.
2. Görselleri dosya yöneticisinden veya Drive uygulamasından ilgili klasöre koy. Son değişiklikleri görmek için Notier’de **Galeriyi yenile** düğmesine dokun. Galeri seçilen klasörün doğrudan içindeki görselleri gösterir.
3. Editörün kaydırılabilir araç çubuğundaki görsel simgesinden **Galeriden görsel ekle** seçeneğini aç. Bir küçük resme dokunduğunda bağlantı imlece eklenir. Seçili metin görsel açıklaması olur. **URL ile görsel ekle** seçeneği çevrim içi görseller içindir.

Notier cihazdaki dosyaları `notier-gallery:/dosya-adı` ile seçili klasörde dosya adına göre bulur. Drive görselleri `notier-drive-image:/dosya-kimliği` ile seçili Drive klasöründen okunur. Drive önizlemesi için ilgili Google hesabına bağlı olman gerekir. Aynı notta her iki kaynağın görselleri kullanılabilir. Başka bir cihazda ilgili galeri klasörlerini yeniden seç. Notun dışa aktarımı Markdown metnini ve bağlantıları taşır. Standart Markdown okuyucularında HTTPS veya nota göre göreli görsel yolu kullanabilirsin.

## Yama notlarının önbelleği

Android uygulaması ve site, yama notlarını yayımlanan GitHub Releases açıklamalarından okur. İlk başarılı denetimden sonra önbellek 12 saat boyunca kullanılır. Bir sonraki denetimde GitHub değişiklik bildirmezse önceki yanıt korunur. Android’de **Güncellemeleri denetle** ile elle denetleyebilirsin. İnternet kesilirse daha önce alınan yama notlarını açabilirsin.

## Ayarlar

Ana ekrandaki **dişli** veya nottaki **üç nokta → Ayarlar** ile desteklenen dil ve alfabeler arasından seçim yapabilirsin. **Görünüm → Simge tarzı** altında Lucide çizgi, Minecraft tarzı piksel ve kalın çizgi seçenekleri bulunur. Sürüm bilgisi **Ayarlar → Hakkında** ekranında yer alır. Yazı, arayüz ve buton boyutları ayrı ayrı ayarlanabilir.

**Görünüm → Tema** ile **Açık** veya **Koyu** seçebilirsin. Koyu temada düğmeler füme, yazılar yumuşak gri ve arka plan koyudur. Listeler, düzenleyici, açılır menüler ve Markdown önizlemesi de seçime uyar. Tema tercihi cihazda saklanır.

Ölçek kaydırıcıları yazıyı %75–175, arayüzü %75–140 ve butonları %75–145 arasında ayarlar. **Köşe yuvarlaklığı** %0'da köşeleri keskinleştirir. **Not düzeni** klasik liste, kompakt liste veya iki sütun seçeneklerini içerir. Yerel listeyi ve Drive listesini yenilemek için sıralama ile bulut arasındaki simgeye dokunabilir veya listenin başındayken aşağı çekebilirsin.

## Google Drive klasörleri

Ana ekrandaki **bulut** simgesinden Google Drive bölümüne geç. Sol üstteki **bulut → Google Drive’a bağlan** yoluyla hesabını seç ve erişim izni ver. **Drive’ım** içindeki klasörlere dokunarak gezebilirsin. Açık klasör, yeni Markdown notlarının kayıt konumudur ve Notier bu konumu hatırlar. Listenin hemen üstündeki gezinme şeridinden üst klasöre çıkabilir, Drive’ım sayfasını açabilir veya geçerli klasörde yeni bir klasör oluşturabilirsin.

Bir notun veya klasörün üzerine basılı tutunca hesabının izinlerine göre yeniden adlandırma, taşıma ve çöp kutusu seçenekleri görünür. Notier, listeyi Drive API üzerinden okur. Başka bir yerde yapılan değişiklikleri görmek için yenile. Yükleme göstergesi listenin altında yer alır. Drive klasörü kartı, klasörün doğrudan içindeki çöp kutusunda olmayan Markdown notlarının sayısını gösterir. Notier, .md ve .markdown uzantılarını sayar. Sayım arka planda tamamlanır. Yakın zamanda açılan klasörler oturum belleğinden gösterilir. Elle yenileme yeni bir sorgu yapar. Arama sonuçları boşsa arama mesajı gösterilir. Yazılabilir bir klasörde not ve alt klasör yoksa ilk not için bir düğme görünür.

**Drive hesabı → Drive oturumunu kapat** uygulamadaki oturumu temizler ve Google’dan Notier’e verilmiş Drive erişimini geri almayı ister. Sonraki girişte hesap seçici açılır. Notier, oturum süresi dolduğunda Google Play Hizmetleri üzerinden yeniden bağlanmayı dener. Google yeniden izin isterse bağlantı düğmesi görünür.

### Geliştiriciler için Google Cloud

Google, Android uygulamasının paket adını ve imzasını tanıyan bir OAuth istemcisi ister. Hazır APK ile kendi derlediğin APK farklı imzalanmışsa ikisi için ayrı istemciler gerekir. Kendi derlediğin APK’nın SHA-1 değerini `apksigner verify --print-certs build/Notier.apk` ile öğrenebilirsin.

1. [Google Cloud Console](https://console.cloud.google.com/) üzerinden bir proje aç ve **Google Drive API**'yi etkinleştir.
2. **Google Auth Platform** altında erişim ekranını yapılandır. Kişisel deneme için **External → Testing** kullanıyorsan Google hesabını **Test users** listesine ekle.
3. OAuth kapsamlarına `https://www.googleapis.com/auth/drive` ekle. Bu kapsam **Drive’ım** içindeki mevcut klasörleri ve dosyaları yönetmek için kullanılır. Geniş kitleye dağıtımda Google’ın kısıtlı kapsam doğrulaması geçerlidir.
4. **Clients → Create client → Android** ile istemci oluştur. Hazır APK için aşağıdaki paket adını ve SHA-1 değerini kullan. APK içine istemci sırrı girmen gerekmez.
5. Notier’de **Google Drive’a bağlan** ile Google izin ekranını aç.

Hazır Notier v1.0.2 APK için kayıt değerleri

| Alan | Değer |
| --- | --- |
| Paket adı | `app.notier.standalone` |
| SHA-1 | `03:0F:CD:82:0A:3D:10:CD:56:3D:62:14:4E:F1:B1:A0:AD:21:53:EA` |

### Drive erişimini herkese açma

1. Mevcut Android OAuth istemcisinin bulunduğu projede **Google Auth Platform → Audience** ekranını aç. Kullanıcı türü **External** olsun. **Publish app** ile yayın durumunu **In production** yap.
2. **Branding** altında Notier adını, destek e-postasını, doğrulayabildiğin bir alan adındaki ana sayfayı ve gizlilik politikasının adresini gir. Marka doğrulamasını tamamlayıp markayı yayımla.
3. **Data Access** bölümünde Notier'in istediği `https://www.googleapis.com/auth/drive` kapsamını bildir. **Verification Center** üzerinden kısıtlı kapsam incelemesine başvur. Bu kapsam, kullanıcıdan her dosya için ayrı seçim istemeden Drive’ım içindeki mevcut klasörleri ve Markdown notlarını yönetmek için kullanılır. Google, hesabı seçme ve izin verme akışıyla klasör gezinmesini, not yazmayı ve oturumu kapatmayı gösteren bir video isteyebilir.
4. İnceleme tamamlandıktan sonra test kullanıcıları listesinde bulunmayan bir Google hesabıyla bağlanmayı dene. Android OAuth istemcisi APK'nın paket adını ve imza parmak izini tanıyorsa kullanıcının kendi Cloud projesini kurması gerekmez.

Gizlilik politikası, Notier’in Google Drive kapsamını neden istediğini ve hangi veriyi işlediğini açıklamalı. Notier Drive’a cihazdan bağlanır ve notlar için Notier’e ait bir sunucu kullanmaz. Drive oturumunda seçilen hesabın adı ve son klasör kimliği cihazda saklanır. Erişim belirteci oturum belleğinde tutulur. Android yazı taslakları cihazda saklanır. Drive erişimi uygulamadan veya kullanıcının Google Hesabı ayarlarından geri alınabilir. Yayın ve inceleme adımları için [Google’ın kapsam doğrulama belgesini](https://developers.google.com/identity/protocols/oauth2/production-readiness/restricted-scope-verification) kullan.

Daha önce Android dosya seçicisiyle eklenen Drive dosyaları, doğrudan bağlantı kapalıyken erişilebilir. **Drive hesabı** menüsündeki **Dosya seçici** seçeneğiyle bunları ekleyebilirsin.

Yazı tipi seçenekleri Geist, Android, Pixelify Sans, Atkinson Hyperlegible Next, Instrument Serif, IBM Plex Mono, DM Sans, Bricolage Grotesque, Source Sans 3, Playfair Display, Space Mono, Serif ve Monospace. Tercihin cihazda saklanır ve arayüze, düzenleyiciye ve önizlemeye uygulanır. Pixelify Sans, Minecraft tarzı bir piksel yazı tipidir. Önizlemedeki kod blokları sabit genişlikli yazı tipini kullanır.

## Markdown ve medya

Başlıklar, bağlantılar, kod blokları, tablolar, görev listeleri ve üstü çizili yazı desteklenir. HTTPS kullanan resimleri standart Markdown ile veya tek başına bir paragraf olarak ekleyebilirsin.

Düzenleme ekranındaki kısayol çubuğunu yana kaydırabilirsin. Kalın **B**, eğik *I*, bağlantı ikonu, **H1–H3**, liste, alıntı, kod ve görsel düğmeleri seçili metne veya imlecin bulunduğu yere Markdown ekler. Çubuğun sonundaki ok düğmeleri son düzenlemeleri geri alır veya yineler.

Bir kod bloğunun sağ üstündeki kopyalama ikonu, bloğun içeriğini Android panosuna alır.

~~~markdown
# Gezi notu

![İnternetteki fotoğraf](https://ornek.site/fotograf.webp)

![Aynı klasördeki fotoğraf](gorseller/fotograf.jpg)

https://ornek.site/video.mp4

https://www.youtube.com/watch?v=VIDEO_KIMLIGI
~~~

Seçili klasördeki bir notun Markdown resimleri, aynı klasördeki veya alt klasörlerdeki yerel dosyalara başvurabilir. Yaygın medya uzantısı taşıyan doğrudan HTTPS bağlantıları ve YouTube veya Vimeo video bağlantıları, tek başına bir paragrafta yer aldığında uygulama içinde açılır. Video ve ses oynatma biçimi Android System WebView'a bağlıdır. Gömülmeye kapalı videolar için oynatıcının altında açma bağlantısı bulunur. Başlıksız veya uzantısız görsel URL'lerini `![açıklama](URL)` biçiminde kullanabilirsin. Yerel not ve görsel okumak için internet gerekmez.

## Termux'ta derleme

ZIP dosyasını İndirilenler'e kaydet. İlk derlemede Maven Central ve Google Maven depolarından Kotlin, Markdown ve Drive yetkilendirme bağımlılıkları indirilir. Betik mevcut Android API 36 platformunu kullanır veya gereken `android.jar` dosyasını Google'ın SDK arşivinden çıkarır.

```bash
pkg install openjdk-21 aapt2 d8 apksigner python curl unzip
termux-setup-storage
cd ~
unzip -o ~/storage/downloads/Notier-GitHub-v1.1.0.zip
cd Notier
bash derle.sh
cp build/Notier.apk ~/storage/downloads/Notier.apk
```

APK'yi İndirilenler'den açarak yükleyebilirsin. Betik, Termux'un yerel AAPT2 aracını ve Kotlin derleyicisini kullanır. `d8` paketindeki R8 aracı mevcutsa APK küçültülür. Özel bir SDK yolu için örnek komut `NOTIER_ANDROID_JAR=/yol/android.jar bash derle.sh`.

Derleme anahtarı `~/.notier-signing/debug.p12` altında tutulur. Paket kimliği ve imzalama anahtarı aynı olan sürümler birbirinin üzerine kurulabilir.

Hazır Notier v1.0.2 APK, hazır v1.0.1 sürümünün üzerine kurulabilir. Android sürüm kodu 12’dir. Aynı paket ve imza kullanıldığı için mevcut Google Cloud Android OAuth kaydı da geçerlidir. Termux'ta normal `bash derle.sh` komutu `app.notier` paketini kullanır. Hazır APK'nın paket kimliğiyle derlemek için `NOTIER_SIDE_BY_SIDE=1 bash derle.sh` komutunu kullan. Kurulu sürümü güncellemek için imzalama anahtarının da eşleşmesi gerekir.

## Kaynak kodu

- `MainActivity.kt` Android ekranları, arama, gezinme ve dosya seçici.
- `DriveApi.kt` Google Drive klasörleri, önizleme görselleri ve Markdown dosya işlemleri.
- `Gallery.kt` cihaz galerisinin izinli klasörünü ve küçük resimlerini okuma.
- `Notes.kt` seçilen klasördeki notları okuma, sıralama ve yazma.
- `Markdown.kt` Markdown, resim ve video önizlemesi.
- `NoteFont.kt` düzenleyici ve önizleme yazı tipi seçenekleri.
- `MarkdownShortcut.kt` seçili metne Markdown uygulayan editör kısayolları.
- `DraftStore.kt` cihazda saklanan taslak.
- `derle.sh` Kotlin → DEX → imzalı APK derleme akışı.

Arayüzde Lucide vektör ikonları ve Geist yazı tipi, Markdown ayrıştırmada CommonMark Java ve Autolink kullanılır. Yazı tipi lisansları ve diğer lisanslar `app/src/main/assets` klasöründedir.

## GitHub Pages ve v1.1.0 yayını

`Notier-GitHub-v1.1.0.zip` içindeki `Notier/` klasörünün içeriğini mevcut `nullnode1/notier-app` deposunun köküne aktar. Site dosyaları `docs/` altında. APK’yı sürüm dosyası olarak GitHub Releases bölümüne ekle. GitHub Pages yayın kaynağı `main` ve `/docs`.

### Termux üzerinden İndirilenler klasöründen yayınlama

```bash
pkg install git gh unzip
termux-setup-storage
gh auth login
gh auth setup-git
git clone https://github.com/nullnode1/notier-app.git ~/notier-yayin
cd ~/notier-yayin

notier_paket="$(mktemp -d)"
unzip ~/storage/downloads/Notier-GitHub-v1.1.0.zip -d "$notier_paket"
cp -a "$notier_paket/Notier/." .
git status --short
git diff --check
git add .gitignore LICENSE NOTICE README.md README.en.md README.ru.md derle.sh app docs tools
git diff --cached --stat
git commit -m "Notier 1.1.0: klasör tabanlı galeri"
git push origin main

gh release create v1.1.0 ~/storage/downloads/Notier-v1.1.0.apk \
  --target main --title "Notier v1.1.0" --notes-file docs/updates/v1.1.0.md
```

Yerelde zaten güncel bir klon varsa `git clone` yerine o depoyu açıp temiz çalışma ağacında `git pull --ff-only` ile başla. Aynı etiketle mevcut bir taslak Release varsa açıklamasını ve APK’yı GitHub arayüzünden güncelle. Mevcut kurulumların üzerine yayın yaparken hazır APK’yı kullan veya önceki sürümle aynı paket kimliği ve imzalama anahtarıyla derle. Notier, APK içeren kararlı sürümü GitHub’dan okur. Yama notu kaynağı [v1.1.0](updates/v1.1.0.md) ve [İngilizce v1.1.0](updates/v1.1.0.en.md).
