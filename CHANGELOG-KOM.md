# Kom-BYDMate changelog

What changed in each Kom-BYDMate release, in English and Armenian. Changes inherited from upstream BYDMate are listed in [CHANGELOG.md](CHANGELOG.md).

Kom-BYDMate-ի յուրաքանչյուր տարբերակում կատարված փոփոխությունները՝ անգլերեն և հայերեն։ Upstream BYDMate-ից եկած փոփոխությունները՝ [CHANGELOG.md](CHANGELOG.md)-ում։

## 3.19.3-kom.16 — 2026-10-02

### English

**My car** (Settings → Application → My car)
- Choose which car is drawn on the cluster and dashboard road, and its paint. The BYD Sealion 06 EV comes with its six official colours (Glaze Silver, Misty Smoke, Warm Sun White, Snow White, Cloud Sea Grey, Sea Cyan) plus the original look. Lights, logo and glass keep their colour.
- **Capture my car**: the app opens DiLink's Vehicle app with the 3D car and shows a 📷 button. Turn the car to see it from behind, wait 5–6 seconds for the white hotspots to fade and tap 📷. A short editor frames the car, removes the background automatically (day and night themes), optionally keeps the ground shadow under the bumper, marks the licence plate and names the model.
- **Share / Import**: a car is saved as `Download/kom_car_<name>.kcar.zip`. Send it to the author (GitHub issue) to add your model for everyone, or import a file someone shared with you. See [docs/car-packs.md](docs/car-packs.md).

### Հայերեն

**Իմ մեքենան** (Settings → Application → Իմ մեքենան)
- Ընտրեք, թե որ մեքենան է երևում cluster-ի և dashboard-ի ճանապարհին, և դրա գույնը։ BYD Sealion 06 EV-ն ունի իր վեց պաշտոնական գույները (Փայլուն արծաթագույն, Ծխագույն, Տաք սպիտակ, Ձյունաճերմակ, Ամպամած մոխրագույն, Ծովային փիրուզագույն) և օրիգինալ տեսքը։ Լույսերը, լոգոն և ապակին պահպանում են իրենց գույնը։
- **Վերցնել իմ մեքենայի նկարը**․ app-ը բացում է DiLink-ի «Մեքենա» app-ը 3D մոդելով և ցույց է տալիս 📷 կոճակ։ Պտտեք մեքենան հետևի տեսքով, սպասեք 5–6 վայրկյան, մինչև սպիտակ ակտիվ կետերը անհետանան, և սեղմեք 📷։ Կարճ խմբագրիչով շրջանակում եք մեքենան, ֆոնը հեռանում է ավտոմատ (ցերեկային և գիշերային թեմաներ), ցանկության դեպքում պահում եք ստվերը բամպերի տակ, նշում համարանիշը և անվանում մոդելը։
- **Կիսվել / Ներմուծել**․ մեքենան պահվում է `Download/kom_car_<անուն>.kcar.zip` ֆայլով։ Ուղարկեք այն հեղինակին (GitHub issue), որ ձեր մոդելը հասանելի դառնա բոլորին, կամ ներմուծեք ուրիշի ուղարկած ֆայլը։ Մանրամասները՝ [docs/car-packs.md](docs/car-packs.md)։

## 3.19.3-kom.15 — 2026-10-02

### English
- The "Loading map" screen on the cluster shows its whole text on one line.
- The five cluster style buttons (Classic, Lagoon, Tide, Arch, Road) fit on one row.

### Հայերեն
- Cluster-ի «Քարտեզը բեռնվում է…» էկրանը ամբողջ տեքստը ցույց է տալիս մեկ տողով։
- Cluster-ի ոճերի հինգ կոճակները (Դասական, Լիճ, Մակընթացություն, Կամար, Ճանապարհ) տեղավորվում են մեկ տողում։

## 3.19.3-kom.14 — 2026-10-02

First public release. / Առաջին հանրային տարբերակ։

### English

**Range**
- The range estimate learns only from real driving: stops of 15 minutes or more and anything that happens on a charger are left out, so hours of parking with the climate on no longer shrink the predicted range.
- It remembers consumption for each 5 °C of outside temperature (a cold morning starts from last winter's numbers) and adapts to the current trip within 5–20 km.
- Remaining energy comes from the battery management system, cross-checked against the charge level. The range tile shows the consumption used and the "right now" consumption.

**Navigator on the cluster**
- Yandex Navigator moves to and from the driver's display with a single restart: no world map, no lost GPS, behind a short "Loading map" screen.
- The cluster no longer goes black while the navigator moves there, and frees up immediately when you bring the navigator back.

**Cluster and dashboard**
- New **Road** theme: the Lagoon layout without the lake; the road edges glow from green to red as the battery drains.
- Lagoon/Tide and Arch: the alternating range / charge number is smaller and sits nearer the road's end. The speed-limit sign on the gauges is 15% smaller.
- The overspeed beep now plays on the car's voice audio channel (it was silent before) and has a **Test** button in settings.

### Հայերեն

**Պաշար**
- Պաշարի հաշվարկը սովորում է միայն իրական վարելուց․ 15 րոպե և ավելի կանգառները և լիցքավորման ընթացքում կատարվածը չեն հաշվվում, այնպես որ կլիմայով ժամերով կանգնելն այլևս չի կրճատում կանխատեսված պաշարը։
- Հիշում է ծախսը արտաքին ջերմաստիճանի յուրաքանչյուր 5 °C-ի համար (ցուրտ առավոտը սկսվում է անցյալ ձմռան թվերից) և 5–20 կմ-ի ընթացքում հարմարվում է ընթացիկ ուղևորությանը։
- Մնացած էներգիան վերցվում է մարտկոցի կառավարման համակարգից՝ լիցքի մակարդակի հետ ստուգված։ Պաշարի սալիկը ցույց է տալիս օգտագործվող և «հիմա» ծախսը։

**Նավիգատորը cluster-ի վրա**
- Yandex Navigator-ը վարորդի էկրան և հետ է տեղափոխվում մեկ վերաբեռնմամբ՝ առանց աշխարհի քարտեզի և GPS-ի կորստի, կարճ «Քարտեզը բեռնվում է…» էկրանի հետևում։
- Տեղափոխելիս cluster-ն այլևս չի սևանում, իսկ հետ բերելիս անմիջապես ազատվում է։

**Cluster և dashboard**
- Նոր **Ճանապարհ** ոճ՝ Լճի դասավորությունը առանց լճի․ ճանապարհի եզրագծերը լիցքի նվազմանը զուգահեռ փոխվում են կանաչից կարմիր։
- Լիճ/Մակընթացություն և Կամար․ հերթափոխվող պաշարի/լիցքի թիվը ավելի փոքր է և ավելի մոտ ճանապարհի ծայրին։ Արագաչափի վրայի սահմանափակման նշանը 15%-ով փոքր է։
- Արագության գերազանցման ազդանշանն այժմ հնչում է մեքենայի «Voice» աուդիո ալիքով (նախկինում չէր լսվում), իսկ կարգավորումներում կա **Փորձել** կոճակ։
