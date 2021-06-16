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
modelsDir | Verzeichnis mit den INTERLIS-Modelldateien.
dataFile  | Name der `ilimodels.xml`-Datei. Wird im `modelsDir`-Verzeichnis gespeichert.
technicalContact | URI für technischen Kontakt. Wird nur verwendet, falls das Modell kein gleichlautendes Metaattribut aufweist. Optional, standardmässig wird im Bedarfsfall `mailto:agi@bd.so.ch` verwendet.

# Bemerkungen

- Die Metaattribute `technicalContact` und `furtherInformation` werden aus dem INTERLIS-Modell ausgelesen und als Attribute in der `ilimodels.xml`-Datei verwendet. Weil diese vom Typ `INTERLIS.URI` sein müssen, kommt es momentan zu Fehler beim Validieren der `ilimodels.xml`-Datei (wegen einiger unserer Modelle). Im Plugin-Code werden minimale Korrekturen vorgenommen oder es wird ein Standardwert gesetzt. Die Metaattribute in den entsprechenden Modellen müssen z.B. bei der Umwandlung nach UTF-8 korrigiert werden.
- Beim Kompilieren der Modelle müssen lokalen Verzeichnisse berücksichtigt werden, weil lokale Modelle wiederum lokale Modelle importieren.