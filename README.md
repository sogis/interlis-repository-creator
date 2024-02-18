![Build Status](https://github.com/sogis/interlis-repository-creator/actions/workflows/main.yml/badge.svg)

# interlis-repository-creator
Gradle plugin for creating INTERLIS repositories

## ModelRepositoryCreator
Der ModelRepositoryCreator-Task erstellt aus einem Verzeichnis (inkl. Unterverzeichnisses) mit INTERLIS-Modelldateien eine _ilimodels.xml_-Datei, welche die Grundlage für eine INTERLIS-Modellablage dient.

Der Inhalt wird aus den INTERLIS-Modellen direkt ermittelt. Dementsprechend können nur die Attributewerte in der _ilimodels.xml_-Datei gespeichert werden, deren Information auch im Modell steckt. 

Die _ilimodels.xml_-Datei wird in den _modelsDir_-Ordner gespeichert. Die Pfadangaben der INTERLIS-Modelldateien in der _ilimodels.xml_-Datei sind relativ zu _modelsDir_.

```
task createIliModels(type: InterlisRepositoryCreator) {
    modelsDir = file("models/")
    dataFile = "ilimodels.xml"
}
```

Parameter | Beschreibung
----------|-------------------
modelsDir | File. Lokales Verzeichnis mit den INTERLIS-Modelldateien.
dataFile  | String. Name der _ilimodels.xml_-Datei. Wird im _modelsDir_-Verzeichnis gespeichert. Optional, default `ilimodels.xml`.
repoModelName | String. Name des Repository-Modells. Zur Auswahl stehen `IliRepository09` und `IliRepository20`. Optional, default `IliRepository20`.
modelRepos  | String. Modell-Respositories, die beim Kompilieren der INTERLIS-Modelldateien verwendet werden. Alle Ordner und Unterordner im `modelsDir`-Verzeichnis werden immer berücksichtigt. Weil diese Ordner vor den Standard-Repos verwendet werden, dauert es nun länger, weil immer z.B. Units gesucht wird. Um dies zu beschleunigen, kann man in einen _models-ext_-Ordner solche Basis-/Kern-Modelle kopieren. Optional, default `http://models.interlis.ch/;http://models.kgk-cgc.ch/;http://models.geo.admin.ch`
technicalContact | String. URI für technischen Kontakt. Wird nur verwendet, falls das Modell kein gleichlautendes Metaattribut aufweist. Optional, default wird im Bedarfsfall `mailto:agi@bd.so.ch` verwendet.
ilismeta | Boolean. Bei `true` wird für jedes INTERLIS-Modell die dazugehörige IlisMeta07-Datei (XTF) erzeugt.

## ConfigDataRepositoryCreator
Der ConfigDataRepositoryCreator-Task erstellt aus einem Verzeichnis (inkl. zwei Subverzeichnissen-Hierarchien) mit ini-Dateien eine ilidata.xml-Datei. Er prüft dabei _nicht_, ob die ini-Datei tatsächlich eine Config- oder Metaconfig-Datei für ilivalidator ist. Ebenso wenig wird die `http://codes.interlis.ch/model`-Kategorie geschrieben (Modell, für das die Config gültig ist). Die Information fehlt dazu in den ini-Datein (Eventuell eine Sidecar-Datei oder separater Info-Block in den ini-Dateien machen).

```
task createConfigDataXml(type: ConfigDataRepositoryCreator) {
    configDir = file('config')
    dataFile = 'ilidata.xml'
    owner = 'mailto:foo@bar.ch'
}
```

Parameter | Beschreibung
----------|-------------------
configDir | File. Lokales Verzeichnis mit den ini-Datein (in den Unterverzeichnissen).
dataFile  | String. Name der _ilidata.xml_-Datei. Default `ilidata.xml`.
owner | String. URI des Eigentümers. Default `mailto:agi@bd.so.ch`

## UsabILItyHubCreator
Der UsabILItyHubCreator-Task erstellt aus einem Verzeichnis mit lokalen ilihub-Repositories (_ilidata.xml_-Datei und Subverzeichnissen mit QML, ini, yaml, etc.) eine gemeinsame _ilidata.xml_-Datei. Es werden die Verzeichnisse innerhalb des angegebenen Verzeichnisses durchsucht. Diese _müssen_ eine _ilidata.xml_-Datei aufweisen. Weitere Subverzeichnisse werden nicht berücksichtigt. Die erstellte _ilidata.xml_-Datei wird mit _ilivalidator_ geprüft. Es wurde ein zusätzlicher Constraint eingeführt: Die Verzeichnisse müssen mit einem Amtskürzel beginnen. Die veränderte Modelldatei ist Bestandteil dieses Code-Repos und muss ggf. nachgeführt werden.

```
task createIliDataXml(type: UsabILItyHubCreator) {
    reposDir = file('ilihub')
    dataFile = 'ilidata.xml'
}
```

Parameter | Beschreibung
----------|-------------------
reposDir | File. Lokales Verzeichnis mit den lokalen ilihub-Repos.
dataFile  | String. Name der _ilidata.xml_-Datei. Wird im _reposDir_-Verzeichnis gespeichert. Optional, default `ilimodels.xml`.


## System Requirements
Java 17 or later.

## Bemerkungen

- `rm -rf .ilicache`: Es wird das Standardverzeichnis des ilicaches verwendet `user.home`. Aus diesem Grund kann es zu Konflikten kommen und das Verzeichnis muss vor dem Ausführen des Tasks gelöscht werden (händisch oder mit Gradle task).
- Die Metaattribute `technicalContact` und `furtherInformation` werden aus dem INTERLIS-Modell ausgelesen und als Attribute in der `ilimodels.xml`-Datei verwendet. Weil diese vom Typ `INTERLIS.URI` sein müssen, kommt es momentan zu Fehler beim Validieren der `ilimodels.xml`-Datei (wegen einiger unserer Modelle). Im Plugin-Code werden minimale Korrekturen vorgenommen oder es wird ein Standardwert gesetzt. Die Metaattribute in den entsprechenden Modellen müssen zu einem späteren Zeitpunkt korrigiert werden (SOGIS only).
- Beim Kompilieren der Modelle müssen allenfalls lokalen Verzeichnisse berücksichtigt werden, weil lokale Modelle wiederum lokale Modelle importieren. Dazu kann der Parameter `modelRepos` verwendet werden.
- Gradle-Plugin-Upload: Es wird eine alte Version des Plugins verwendet. Mit Version 1.0 (o.ä) gibt es einen API-Break.