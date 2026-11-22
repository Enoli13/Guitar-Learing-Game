# 🎸 Perde — Oyunlaştırılmış Gitar ve Müzik Teorisi

**Perde**, gitarı ve gitar için müzik teorisini **sıfırdan ustalığa** öğreten bir Android uygulamasıdır. Duolingo'nun oyunlaştırmasını, Anki'nin aralıklı tekrarını ve mikrofonla gerçek zamanlı çalma kontrolünü tek bir uygulamada birleştirir.

> "Metronomu 60'a al, çal, sonra 'yaptım' diye işaretle" yok. Çalma görevlerinde **mikrofon seni dinler**; metronomlu görevlerde yanlış ya da zamansız nota hata sayılır, bekleme modunda doğru notayı bulana kadar ilerlemezsin.

## Özellikler

### 🎤 Mikrofonla gerçek zamanlı kontrol
- **Nota tespiti:** YIN perde algoritması, vuruş sonrası analizle çalınan yeni notayı yakalar (legato/hammer-on/pull-off dahil).
- **Beklenen notayı dinleme:** Önceki teller çalmaya devam ederken (arpej, açık teller) tek perde algılayıcı şaşırabilir; bu yüzden her vuruştan sonra beklenen notanın harmoniklerinde yeni enerji belirip belirmediğine de bakılır. Oktav farkı ve pes akortlu şarkıları standart akortta çalmak kabul edilir. Bekleme modunda yanlış nota görevi bitirmez, tekrar denersin.
- **Vuruş zamanlaması:** Spektral akı (spectral flux) ile vuruş anı tespiti; metronom tıkları filtrelenir.
- **Akor tanıma:** Harmonik ağırlıklı kromagram şablonlarıyla (majör, minör, power, 7'li, sus, slash…).
- **Bend ölçer:** Teli bükerken ses yüksekliği canlı izlenir; hedefi tutturmalı ve aşmamalısın.
- **Akort aleti** ve telefonun ses gecikmesini ölçen **kalibrasyon**.
- Görev türleri: bekleme modunda nota çalma, metronomla zamanlı dizi (tab), akor basma, akor geçişleri, ritim/strum kalıpları, doğaçlama (gam dışı nota = başarısız), kulaktan çalma, porteden okuyup çalma.

### 📚 Müfredat: 26 ünite, 109 ders
Başlangıç (gitarın anatomisi, akort, açık teller) → müzik alfabesi ve klavye → tab ve ilk melodiler → ritim → ilk akorlar ve geçişler → porte okuma → power chord ve palm mute → aralıklar ve kulak → majör gam → diyatonik armoni, kapo, transpoze → ileri ritim (senkop, triole, shuffle, 6/8, 12/8) → minör pentatonik, blues, bend, legato, doğaçlama → barre akorlar → CAGED ve pentatoniğin 5 kutusu → parmakla çalma, Travis → 7'li/genişletilmiş akorlar, çevrimler → beşliler çemberi, kadanslar, modülasyon → 12-bar blues → modlar → armonik/melodik minör, simetrik gamlar → arpejler ve sweep → teknik ve hız (tempo merdiveni, tel atlama, ekonomik pena, tapping, armonikler) → ileri armoni (ikincil dominant, ödünç akor, tritone ikamesi, ses yürütme, akor-gam ilişkisi) → kulaktan çalma/transkripsiyon → alternatif akortlar, ton ve ifade → riff yazma, solo kurgusu ve **ustalık sınavı**.

Her ünite, onu zaten biliyorsan **sınavla atlanabilir**.

### 🧠 Aralıklı tekrar (SM-2)
Öğrendiğin her bilgi ve beceri bir kart olur. Doğru bildikçe kartlar seyrekleşir, unuttuğunda geri gelir. Her tekrar yeni rastgele bir soruyla gelir; **hız görevlerinde her başarılı tekrar temposu +5 BPM artırır** (tempo merdiveni).

### 🏆 Oyunlaştırma
XP ve seviyeler (Çırak → Efsane), günlük hedef halkası, seri (🔥) ve seri dondurucu, değişken ödüllü sandıklar, 22 rozet, pena (🎸) ile dükkân, ders yıldızları, günlük hatırlatma bildirimi. Tasarım; Octalysis, Hook modeli (tetikleyici → eylem → değişken ödül → yatırım) ve Tiny Habits (küçük başla, anında kutla) ilkelerine dayanır.

### 🎵 Şarkı Yolu
**Şarkı Yolu:** Yol'dan ayrı ilerleyen ikinci bir yol. Aynı 29 ünitenin konuları, ama her zikzak adımında konunun teorisi şarkının küçük bir bölümü (intro, riff, solo …) üzerinden anlatılır, soru yerine her görevde gitar çalınır (bölümün notaları, akorları, aralıkları, gamı şarkıdan çalınarak öğrenilir) ve bölüm önce yarı yarıya, sonra bütün olarak çalınır; her adım şarkının bir sonraki bölümünü öğretir. Ünite finalinde öğrenilen bütün bölümler birleştirilip tempoda çalınır, ardından 👑 ve ünite sandığı gelir.

- **Dosyadan:** Guitar Pro (.gp, .gp5, .gp4, .gp3) ve MusicXML (.musicxml, .mxl, .xml). Ritim, akort ve teknikler (bend, hammer-on, slide) dosyadan okunur; birden çok parça varsa hangisini çalacağını seçersin. Başka uygulamadan "Paylaş → Perde" de çalışır.
- **Metinden:** ASCII tab'ı yapıştır ya da elle yaz (tel etiketlerinden akort da okunur).

**Akor sözlüğü:** aynı anda basılan sesler akor ise (power chord, majör/minör, 7'li, sus, add9, çevrimler "C/E" ...) tab'daki perde sütunu silinir, yerine akorun adı yazılır. Akorlar pratikte de bütün olarak (kroma ile) kontrol edilir.
**Notaya çevir:** tek sesler sol anahtarlı porteye (gitar 8'i ile) ritim değerleriyle yazılır; akorlar portenin üstünde adıyla ve ritim çizgisiyle gösterilir. İstersen nota adları altına yazılır.

Her bölüm RitimKulak yöntemiyle öğretilir: **dinle → ritmi dokun → notaları bul (bekleme modu) → tempoda çal**. Alttaki kayan şeritte çalacağın her nota adıyla (ve tel/perdesiyle), akorlar adıyla gelir; tempoda çal adımında temiz geçince tempo 5 BPM artar.

## Kurulum
**[Perde.apk dosyasını indir](apk/Perde.apk)** (ya da [Releases](../../releases) sayfasından) ve telefonuna kur (Android 8.0+). İlk açılışta mikrofon ve bildirim izni istenir.

En iyi sonuç için: telefonu gitara 30–60 cm uzaklıkta tut, sessiz bir ortamda çal, zamanlama görevlerinden önce **Profil → Ayarlar → Gecikme ayarı** ile kalibrasyon yap. Kulaklık takarsan metronom mikrofona karışmaz.

## Derleme
Gradle gerekmez; yalnızca Android SDK (build-tools 36, platform 35) ve JDK 17+ yeterli:

```bash
ANDROID_HOME=~/Android/Sdk ./build.sh          # build/Perde.apk
ANDROID_HOME=~/Android/Sdk tools/test.sh       # müfredat + ses algılama testleri
```

`tools/Harness.java`, tüm müfredat görevlerinin üretilebildiğini, teori hesaplarını ve sentezlenmiş gitar sesiyle nota, vuruş ve akor algılamayı doğrular.

## Lisans
MIT
