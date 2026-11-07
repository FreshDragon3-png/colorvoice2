# Color Voice (Minecraft 26.2, Fabric)

Sag per Simple Voice Chat eine Farbe (z. B. "rot") und alle Blöcke im Umkreis von 100 Blöcken
werden in zufällige Blöcke dieser Farbe verwandelt (rote Wolle, roter Beton, Netherrack, ...).

## 1. Jar kostenlos bauen (GitHub Actions)

1. Kostenlosen Account auf github.com anlegen, neues Repository erstellen.
2. Den Inhalt dieses Ordners hochladen ("Add file" -> "Upload files"; auch der Ordner `.github` muss mit).
3. Reiter **Actions** öffnen -> Workflow "Build" läuft automatisch (ca. 3-5 Minuten).
4. Fertigen Lauf anklicken -> unten bei **Artifacts** `colorvoice-jar` herunterladen -> ZIP entpacken -> `colorvoice-1.0.0.jar`.
5. Schlägt der Build fehl: Fehlermeldung aus dem Log kopieren und mir schicken.

## 2. Installieren

Im `mods`-Ordner (Server oder Client mit LAN-Welt) müssen liegen:
- `colorvoice-1.0.0.jar`
- Fabric API (26.2)
- **Simple Voice Chat 2.6.21+26.2 (Fabric)** (auf dem Server UND bei allen Spielern)

Hinweis: Simple Voice Chat läuft nur auf einem Server oder in einer für LAN geöffneten Welt, nicht im reinen Einzelspieler.

## 3. Sprachmodell (nötig, ca. 40-50 MB, kostenlos)

Die Erkennung läuft offline mit Vosk. Von https://alphacephei.com/vosk/models ein **kleines** Modell laden:
- Deutsch: `vosk-model-small-de-0.15`
- Englisch: `vosk-model-small-en-us-0.15`

ZIP entpacken und den **Inhalt** (Ordner `am`, `conf`, `graph`, ...) nach
`config/colorvoice/model/` legen. Danach in `config/colorvoice.json` die `language` (`de` oder `en`) passend setzen.

## 4. Benutzen

- Mit Simple Voice Chat sprechen: "rot", "blau", "grün", "gelb", "orange", "lila", "rosa", "schwarz", "weiß", "grau", "braun", "türkis", "hellblau", "hellgrün", "magenta".
- Zum Testen ohne Mikrofon: `/colorvoice rot`

## Einstellungen (`config/colorvoice.json`)

| Name | Bedeutung |
|---|---|
| radius | Radius in Blöcken (Standard 100) |
| language | `de` oder `en` (muss zum Modell passen) |
| msBudgetPerTick | Millisekunden pro Tick fürs Verwandeln (höher = schneller, mehr Lag) |
| skipBlockEntities | Kisten, Öfen usw. nicht ersetzen |
| skipFluids | Wasser/Lava nicht ersetzen |
| cooldownSeconds | Pause zwischen zwei Verwandlungen |
| enableCommand | Testbefehl `/colorvoice` (auf öffentlichen Servern ausschalten!) |

## Bekannte Grenzen

- Nur **geladene** Chunks werden verwandelt (Server-Sichtweite begrenzt den effektiven Radius).
- Eine Kugel mit Radius 100 sind ~4 Millionen Blöcke: Es dauert einige Sekunden bis Minuten und kann nicht rückgängig gemacht werden. **Vorher ein Backup der Welt machen.**
