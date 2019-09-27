[![Build Status](https://travis-ci.org/sogis/interlis-repository-creator.svg?branch=master)](https://travis-ci.org/sogis/interlis-repository-creator)

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

