# Notier Gizlilik Politikası

[English](PRIVACY.md) · [Türkçe](PRIVACY.tr.md) · [Русский](PRIVACY.ru.md)

Son güncelleme 17 Kasım 2026.

## Cihazındaki notlar

Notier, Android için bir Markdown editörüdür. Android’in dosya seçicisinde seçtiğin klasördeki notları açar. Klasörü veya tek tek dosyaları yeniden açabilmen için Android erişim izinlerini saklayabilir. Notier, ayarları, sabitlenen notların kayıtlarını ve varsa kaydedilmemiş taslağı cihazındaki özel uygulama alanında tutar. Yerel dosyaları kullanmak için Google hesabı gerekmez.

## Görsel galeri

Cihaz galerisi için bir klasör seçtiğinde Notier, görselleri listelemek ve önizlemek için Android’in verdiği okuma iznini saklar. Drive galerisi seçildiğinde klasör ve hesap ilişkisinin kimlikleri cihazda saklanır. Klasör listesi, küçük resimler ve önizlemede kullanılan görseller Google Drive’dan cihazına okunur. Notta görselin dosya adı veya Drive dosya kimliği bulunur.

## Google Drive

Drive kullanımı isteğe bağlıdır. Bağlanırsan hesap seçimini ve yetkilendirmeyi Google yönetir. Notier, mevcut klasörlere girmek, Markdown notlarını bulup okumak, kaydetmek ve istediğin taşıma veya çöp kutusuna gönderme gibi işlemleri yapmak için `https://www.googleapis.com/auth/drive` kapsamını kullanır. Liste oluştururken ve notları sayarken dosya veya klasör adı, türü, kimliği, değişiklik tarihi ve izin verilen işlemler gibi üst veriler okunur. Notu açtığında Markdown içeriği okunur.

Notier, oturumu geri yüklemek için seçilen Google hesabının adını ve son Drive klasörünün kimliğini cihazdaki özel uygulama alanında saklayabilir. Uygulamanın kullandığı erişim belirteci ve kısa süreli liste önbelleği bellekte tutulur. Cihazdaki yetkilendirmeyi Google Play Hizmetleri yönetir. Android dosya seçicisiyle eklediğin dosyalar için Android’in verdiği erişim izinleri ve dosya kayıtları saklanabilir.

## Verilerin kullanımı ve paylaşımı

Drive istekleri cihazından doğrudan Google’a gider. Uygulama, Notier’e ait bir sunucu, reklam SDK’sı veya kullanım analizi servisi kullanmaz. Notier, Drive notlarını geliştiriciye göndermez. Android üzerinden bir notu paylaşmayı veya dışa aktarmayı seçersen notu seçtiğin hedef alır. Markdown önizlemesi, notta bağlantısı bulunan çevrim içi görsel, ses veya videoyu ilgili servisten yükleyebilir. Bu servisler yükleme sırasında IP adresini ve olağan istek bilgilerini alabilir.

Notier, Google API’lerinden alınan bilgilerin kullanımı ve aktarımında, Sınırlı Kullanım koşulları dahil [Google API Hizmetleri Kullanıcı Verileri Politikası](https://developers.google.com/terms/api-services-user-data-policy) hükümlerine uyar.

## Erişimi kaldırma ve verileri silme

Notier’de **Google Drive → Drive hesabı → Drive oturumunu kapat** yoluyla doğrudan Drive oturumunu temizleyebilir ve Google’dan uygulama erişimini geri almasını isteyebilirsin. Erişimi [Google Hesabı bağlantılarından](https://myaccount.google.com/connections) da kontrol edebilir veya kaldırabilirsin. Çöp kutusuna taşımadığın veya silmediğin dosyalar Drive hesabında kalır. Android seçicisiyle eklediğin bir dosya için **Listeden kaldır**, kaydı kaldırır ve saklanan izni bırakır.

Android ayarlarından Notier uygulama verilerini temizlediğinde yerel ayarlar, saklanan kayıtlar ve kaydedilmemiş taslak silinir. Cihaz klasörlerine veya Drive’a dosya olarak kaydettiğin notları ilgili konumdan yönetebilirsin.

## Güncelleme denetimi

Notier’i açtığında, güncellemeleri denetlediğinde veya güncelleme bildirimlerini açtığında Notier, GitHub API’sinden herkese açık sürüm bilgilerini ister. GitHub bu isteklerde IP adresini ve olağan bağlantı bilgilerini alabilir. Güncelleme istekleri not, dosya veya Google hesabı bilgisi içermez. Arka plan denetimlerini Notier’in güncelleme ayarlarından kapatabilirsin. Notier, son başarılı denetimin zamanını ve herkese açık sürüm bilgilerini ve yama notlarını cihazında saklar. Böylece sonucu çevrimdışı okuyabilirsin.

## İletişim

Gizlilikle ilgili sorular için Notier deposunun **Issues** bölümünü kullanabilirsin. Sorunu iletirken not içeriğini, hesap bilgilerini veya erişim belirteçlerini paylaşma.
