# FOX Skat Light
Skat auf dem Light Phone III – offline gegen zwei Computergegner.

## Was es kann

- **Ein Spieler gegen zwei Computergegner** („Links“ und „Rechts“), nach den Regeln der
  Internationalen Skatordnung. Geber, Vorhand, Mittelhand und Hinterhand wechseln jede Runde.
- **Reizen:** Mittelhand sagt, Vorhand hört, danach Hinterhand. Unten tippst du `PASSE`, `JA` oder den
  nächsten Reizwert. Haben beide anderen gepasst, kannst du als Vorhand für 18 spielen.
- **Skat:** aufnehmen und zwei Karten drücken, oder Hand spielen.
- **Spiele:** Kreuz, Pik, Herz, Karo, Grand, Null und Null Ouvert. Bei Handspielen kannst du
  Schneider, Schwarz oder Ouvert ansagen. Beim Ansagen steht neben jedem Spiel sein Wert.
- **Ausspielen:** Karte antippen (sie wird hervorgehoben), noch einmal tippen spielt sie.
  Karten, die du nicht bedienen darfst, sind abgeblendet. Der letzte Stich bleibt stehen, bis
  der nächste beginnt.
- **Abrechnung:** Spitzen (mit/ohne), Hand, Schneider, Schwarz, Ansagen, Ouvert und Überreizen.
  Gewonnen zählt der Spielwert, verloren das Doppelte als Minus.
- **Liste** mit allen Spielen und den Summen für dich, Links und Rechts. Sie bleibt gespeichert.
- Hell/Dunkel umschaltbar.

Noch nicht dabei: Kontra/Re, Ramsch, Bockrunden, Seeger-Fabian-Wertung. Ein laufendes Spiel
geht verloren, wenn das Tool beendet wird; die Liste bleibt.

## Die Computergegner

Sie reizen nach Faustregeln (Trümpfe, Buben, Asse, Zehnen mit Ass), drücken blanke Zehnen und
kurze Farben und spielen nach bekannten Regeln: Trumpf ziehen als Alleinspieler, Asse vorweg,
„kurzer Weg, lange Farbe“, Schmieren beim Partner und Untendrunterbleiben im Null. Sie sehen nur,
was ein Mensch am Tisch auch sehen würde. In 5000 Testspielen der Computer gegeneinander
gewinnt der Alleinspieler etwa drei von vier Spielen.

## Datenschutz

| Was | Wohin | Wozu |
|---|---|---|
| nichts | – | – |

- Das Tool braucht keine Berechtigungen und baut keine Verbindung ins Internet auf.
- Die Liste liegt im privaten Speicher des Tools auf dem Telefon.

## APK bauen lassen

Bei jedem Push auf `main` baut **Actions → „APK bauen“** die App, führt die Tests aus und legt
unter **Releases** die Datei `FOX-Skat-Light.apk` ab. Für Pull Requests gibt es die APK als
Artefakt im jeweiligen Actions-Lauf.

Lokal geht es auch mit Android Studio oder:

```
./gradlew :tool:assembleDebug -DlightSdk.toolOnly=true
```

## Aufs Light Phone bringen

**Variante A – Tool Manager über WLAN (ohne Kabel)**

1. Im [Light-Dashboard](https://dashboard.thelightphone.com) den Entwicklermodus einschalten.
2. Telefon und Computer ins selbe WLAN, auf dem Telefon in den Einstellungen den Tool Manager
   starten und die angezeigte Adresse im Browser am Computer öffnen.
3. Unter `Developer` → `Developer Keys` die Datei
   [`docs/sideloading/light_debug_signing_key.txt`](docs/sideloading/light_debug_signing_key.txt)
   hochladen (falls noch nicht geschehen).
4. Unter `Upload Tools` die `FOX-Skat-Light.apk` hochladen. Danach erscheint „Skat“ in der Toolbox.

**Variante B – per Kabel mit adb:** `adb install FOX-Skat-Light.apk`

## Aufbau

Der Code liegt in [`tool/src/main/kotlin/io/github/foxskat`](tool/src/main/kotlin/io/github/foxskat):

| Datei | Inhalt |
|---|---|
| `core/Cards.kt` | Farben, Werte, Karten, Augen |
| `core/Rules.kt` | Spielarten, Trumpf, Bedienen, Stiche, Spitzen, Spielwert und Abrechnung |
| `core/Round.kt` | eine Runde vom Reizen bis zur Abrechnung, prüft jeden Zug |
| `core/Ai.kt` | Computergegner: Reizen, Drücken, Ansagen, Ausspielen |
| `core/ScoreSheet.kt` | Liste und Sortierung der Hand |
| `core/Texts.kt` | Texte für die Anzeige |
| `ui/…Screen.kt` | Oberfläche mit den Bausteinen der Light SDK |

`core/` kommt ohne Android aus; die Tests dazu liegen in `tool/src/test`.

Der Rest des Repos ist die [Light SDK](https://github.com/lightphone/light-sdk) (Stand Oktober 2026),
deren ursprüngliche README in `README.light-sdk.md` liegt.

## Für die offizielle Tool-Bibliothek

Das Tool arbeitet komplett lokal, ohne Konto, ohne Tracking und ohne Werbung, und hat ein klares
Ende (eine Runde). Damit passt es zu den [Tool Guidelines](TOOL_GUIDELINES.md).
