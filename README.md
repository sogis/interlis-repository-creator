![Build Status](https://github.com/sogis/interlis-repository-creator/actions/workflows/main.yml/badge.svg)

# interlis-repository-creator
Gradle plugin for creating INTERLIS repositories

Der InterlisRepositoryCreator-Task erstellt aus einem Verzeichnis (inkl. Unterverzeichnisses) mit INTERLIS-Modelldateien eine `ilimodels.xml`-Datei, welche die Grundlage für eine INTERLIS-Modellablage dient.

Der Inhalt wird aus den INTERLIS-Modellen direkt ermittelt. Dementsprechend können nur die Attributewerte in der `ilimodels.xml`-Datei gespeichert werden, deren Information auch im Modell steckt. 

Die `ilimodels.xml`-Datei wird in den `modelsDir`-Ordner gespeichert. Die Pfadangaben der INTERLIS-Modelldateien in der `ilimodels.xml`-Datei sind relativ zu `modelsDir`.

```
task createIliModels(type: InterlisRepositoryCreator) {
    modelsDir = file("models/")
    dataFile = "ilimodels.xml"
}
```

Parameter | Beschreibung
----------|-------------------
modelsDir | File. Lokales Verzeichnis mit den INTERLIS-Modelldateien.
dataFile  | String. Name der `ilimodels.xml`-Datei. Wird im `modelsDir`-Verzeichnis gespeichert. Optional, default `ilimodels.xml`.
repoModelName | String. Name des Repository-Modells. Zur Auswahl stehen `IliRepository09` und `IliRepository20`. Optional, default `IliRepository09`.
modelRepos  | String. Modell-Respositories, die beim Kompilieren der INTERLIS-Modelldateien verwendet werden. Optional, default `http://models.interlis.ch/;http://models.kgk-cgc.ch/;http://models.geo.admin.ch`
technicalContact | String. URI für technischen Kontakt. Wird nur verwendet, falls das Modell kein gleichlautendes Metaattribut aufweist. Optional, default wird im Bedarfsfall `mailto:agi@bd.so.ch` verwendet.
ilimeta | Boolean. Bei `true` wird für jedes INTERLIS-Modell das dazugehörig IlisMeta07-Datei (XTF) erzeugt.

## System Requirements
Java 17 or later.

## Bemerkungen

- `rm -rf .ilicache`: Es wird das Standardverzeichnis des ilicaches verwendet `user.home`. Aus diesem Grund kann es zu Konflikten kommen und das Verzeichnis muss vor dem Ausführen des Tasks gelöscht werden (händisch oder mit Gradle task).
- Die Metaattribute `technicalContact` und `furtherInformation` werden aus dem INTERLIS-Modell ausgelesen und als Attribute in der `ilimodels.xml`-Datei verwendet. Weil diese vom Typ `INTERLIS.URI` sein müssen, kommt es momentan zu Fehler beim Validieren der `ilimodels.xml`-Datei (wegen einiger unserer Modelle). Im Plugin-Code werden minimale Korrekturen vorgenommen oder es wird ein Standardwert gesetzt. Die Metaattribute in den entsprechenden Modellen müssen zu einem späteren Zeitpunkt korrigiert werden (SOGIS only).
- Beim Kompilieren der Modelle müssen allenfalls lokalen Verzeichnisse berücksichtigt werden, weil lokale Modelle wiederum lokale Modelle importieren. Dazu kann der Parameter `modelRepos` verwendet werden.
- Gradle-Plugin-Upload: Es wird eine alte Version des Plugins verwendet. Mit Version 1.0 (o.ä) gibt es einen API-Break.