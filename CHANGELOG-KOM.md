# Kom-BYDMate changelog

What changed in each Kom-BYDMate release, in English and Armenian. Changes inherited from upstream BYDMate are listed in [CHANGELOG.md](CHANGELOG.md).

Kom-BYDMate-ի յուրաքանչյուր տարբերակում կատարված փոփոխությունները՝ անգլերեն և հայերեն։ Upstream BYDMate-ից եկած փոփոխությունները՝ [CHANGELOG.md](CHANGELOG.md)-ում։

## 3.19.3-kom.17 — 2026-10-05

### English

**Range**
- Climbs and descents no longer distort the range: the altitude change of every kilometre (GPS) is taken out, so the app learns what driving costs on a flat road. On a hilly day the old estimate was off by a quarter.
- The climate counts the moment it runs: switching the AC on lowers the range at once, by its real power (the compressor's own sensor plus the fan) and your average speed, more in town and less on the highway; switching it off brings the range back.
- Short trips and the last few kilometres only nudge the estimate, and a temperature's history counts by how much of it there is, so two short warm-up trips no longer halve the range.
- The consumption counter is calibrated against the battery's own energy over long stretches.
- The range never exceeds the car's rated range at the current charge (Settings, 605 km by default).
- The learned range history starts over once after this update (it had hills in it); until it relearns, the range starts from the car's lifetime average.

**Dashboard**
- GPS signal and altitude in the top-right corner of the dashboards; on a Song Plus only the satellites, since its head unit shows the altitude itself.
- Swipe down on the title row to put the app away.
- A traffic officer with a whistle steps onto the road after 30 s over the speed limit; three taps switch him off, Settings switches him back on.

**Driver's display**
- Yandex Navigator restarts only once, straight into its window on the driver's display: no world map, and the route line stays.
- A scale change while the map is on the driver's display applies at once.
- Song Plus (DiLink 4): sending the map to the driver's display works again.

**Cars and updates**
- Built-in BYD Song Plus picture, chosen automatically when the car's Bluetooth name shows a Song Plus.
- In-app updates: Kom-BYDMate checks its own releases and offers the new version.

### Հայերեն

**Պաշար**
- Վերելքներն ու վայրէջքները այլևս չեն աղավաղում պաշարը․ յուրաքանչյուր կիլոմետրի բարձրության փոփոխությունը (GPS) հանվում է, և app-ը սովորում է հարթ ճանապարհի ծախսը։ Լեռնային օրը հին հաշվարկը սխալվում էր մոտ քառորդով։
- Կլիման հաշվվում է հենց միանալու պահից․ AC-ն միացնելիս պաշարը միանգամից նվազում է՝ ըստ իրական հզորության (կոմպրեսորի սեփական տվիչ և օդափոխիչ) և ձեր միջին արագության, քաղաքում՝ ավելի շատ, մայրուղում՝ ավելի քիչ․ անջատելիս պաշարը վերադառնում է։
- Կարճ ուղևորություններն ու վերջին մի քանի կիլոմետրը միայն թեթևակի են ազդում, իսկ ջերմաստիճանի պատմությունը կշիռ ունի ըստ իր ծավալի․ երկու կարճ տաքացման ուղևորությունն այլևս պաշարը կիսով չեն կրճատում։
- Ծախսի հաշվիչը երկար հատվածներում ճշգրտվում է մարտկոցի սեփական էներգիայով։
- Պաշարը երբեք չի գերազանցում մեքենայի անվանական պաշարը ընթացիկ լիցքի դեպքում (Settings, լռելյայն 605 կմ)։
- Այս թարմացումից հետո պաշարի սովորած պատմությունը մեկ անգամ զրոյացվում է (այն պարունակում էր վերելքներ)․ մինչև նորից սովորելը պաշարը սկսում է մեքենայի ամբողջ կյանքի միջին ծախսից։

**Dashboard**
- GPS ազդանշանը և բարձրությունը dashboard-ների վերևի աջ անկյունում․ Song Plus-ում՝ միայն արբանյակները, քանի որ բարձրությունն արդեն ցույց է տալիս մեքենայի վերևի տողը։
- Վերնագրի տողի վրա ներքև սահեցնելով app-ը թաքցվում է։
- Արագության սահմանը 30 վայրկյան գերազանցելիս ճանապարհին հայտնվում է սուլիչով ոստիկան․ երեք հպումով անջատվում է, Settings-ից նորից միացվում։

**Վարորդի էկրան**
- Յանդեքս Նավիգատորը վերաբացվում է միայն մեկ անգամ՝ անմիջապես վարորդի էկրանի իր պատուհանում․ առանց աշխարհի քարտեզի, և երթուղու գիծը մնում է։
- Մասշտաբի փոփոխությունը, երբ քարտեզը վարորդի էկրանին է, կիրառվում է անմիջապես։
- Song Plus (DiLink 4)․ քարտեզը վարորդի էկրան ուղարկելը նորից աշխատում է։

**Մեքենաներ և թարմացումներ**
- Ներկառուցված BYD Song Plus-ի նկար, որն ընտրվում է ավտոմատ, երբ մեքենայի Bluetooth անունը Song Plus է։
- Թարմացումներ app-ի ներսից․ Kom-BYDMate-ը ստուգում է իր սեփական թողարկումները և առաջարկում նոր տարբերակը։

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
