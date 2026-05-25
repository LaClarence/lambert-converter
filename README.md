# lambert-converter

Bibliothèque Java pour convertir des coordonnées Lambert (NTF ou RGF93) vers WGS84 / RGF93 et inversement.

## Build

Pré-requis :

* Java 26
* Maven 3.9+

```bash
mvn clean package
```

Ajouter le fichier généré `target/lambert-converter-<version>.jar` à votre projet.

---

## Conversions disponibles

| Depuis | Vers | Méthode | Précision |
|---|---|---|---|
| Lambert NTF (I–IVe, IIE) | WGS84 géographique (radians) | `convertToWGS84(ProjectedPoint, zone)` | ±1–3 m |
| Lambert NTF (I–IVe, IIE) | WGS84 géographique (degrés) | `convertToWGS84Deg(x, y, zone)` | ±1–3 m |
| WGS84 géographique (degrés) | Lambert NTF / Lambert93 | `convertToLambert(lat, lon, zone)` | ±1–3 m |
| WGS84 géographique (degrés) | Lambert NTF / Lambert93 | `convertToLambertByAlg003(lat, lon, zone)` | ±1–3 m |
| Lambert NTF (I–IVe, IIE) | RGF93 géographique (degrés) | `convertToRGF93(x, y, zone, grid)` | < 1 cm |
| RGF93 géographique (degrés) | Lambert NTF (I–IVe, IIE) | `convertToLambertNtf(Rgf93Point, zone, grid)` | < 1 cm |

### Zones Lambert supportées

| Constante | Système | Méridien origine | Usage |
|---|---|---|---|
| `LambertI` | NTF | Paris | Nord de la France |
| `LambertII` | NTF | Paris | Centre de la France |
| `LambertIII` | NTF | Paris | Sud de la France |
| `LambertIV` | NTF | Paris | Corse |
| `LambertIIExtended` | NTF | Paris | France entière (cadastre) |
| `Lambert93` | RGF93 | Greenwich (IERS) | Système légal actuel |

### Types de coordonnées

| Type | Champs | Unité | Système |
|---|---|---|---|
| `ProjectedPoint` | `x()`, `y()` | mètres | Lambert (NTF ou RGF93) |
| `GeographicPoint` | `longitude()`, `latitude()` | radians | intermédiaire interne |
| `Wgs84Point` | `longitude()`, `latitude()` | degrés décimaux | WGS84 (voie Helmert, ±1–3 m) |
| `Rgf93Point` | `longitude()`, `latitude()` | degrés décimaux | RGF93 (voie grille NTv2, < 1 cm) |

> `Wgs84Point` et `Rgf93Point` ont les mêmes champs mais des systèmes géodésiques distincts :
> WGS84 et RGF93 diffèrent de ~50 cm en France. Les garder séparés évite de les mélanger
> par inadvertance — le compilateur signale toute confusion entre les deux.

---

## Exemples d'utilisation

### Lambert NTF → WGS84 (Helmert 7 paramètres, ±1–3 m)

```java
import geo.lambert.GeodeticConverter;
import geo.lambert.LambertZone;
import geo.lambert.Wgs84Point;

// Lambert IIE → WGS84 en degrés décimaux
Wgs84Point pt = GeodeticConverter.convertToWGS84Deg(618115.0, 2430676.0, LambertZone.LambertIIExtended);
System.out.println("lat=" + pt.latitude() + " lon=" + pt.longitude());
// → lat≈48.874  lon≈2.583
```

### WGS84 → Lambert NTF

```java
import geo.lambert.GeodeticConverter;
import geo.lambert.LambertZone;
import geo.lambert.ProjectedPoint;

// WGS84 (degrés) → Lambert IIE
ProjectedPoint p = GeodeticConverter.convertToLambert(48.8741, 2.5833, LambertZone.LambertIIExtended);
System.out.println("x=" + p.x() + " y=" + p.y());
// → x≈618115  y≈2430676
```

### Lambert NTF → RGF93 par grille (précision centimétrique)

La grille de correction `fr_ign_ntf_r93.tif` est embarquée dans le JAR.
Utiliser `Ntv2Grid.getDefault()` pour la charger (singleton, chargement paresseux).

```java
import geo.lambert.GeodeticConverter;
import geo.lambert.LambertZone;
import geo.lambert.Ntv2Grid;
import geo.lambert.Rgf93Point;

// Lambert IIE → RGF93 géographique
Rgf93Point rgf93 = GeodeticConverter.convertToRGF93(
        618115.0, 2430676.0,
        LambertZone.LambertIIExtended,
        Ntv2Grid.getDefault());
System.out.println("lat=" + rgf93.latitude() + " lon=" + rgf93.longitude());
// → lat≈48.8741  lon≈2.5833  (précision < 1 cm)
```

### RGF93 → Lambert NTF par grille

```java
import geo.lambert.GeodeticConverter;
import geo.lambert.LambertZone;
import geo.lambert.Ntv2Grid;
import geo.lambert.ProjectedPoint;
import geo.lambert.Rgf93Point;

Rgf93Point origine = new Rgf93Point(2.5833, 48.8741); // lon, lat
ProjectedPoint lambert = GeodeticConverter.convertToLambertNtf(
        origine,
        LambertZone.LambertIIExtended,
        Ntv2Grid.getDefault());
System.out.println("x=" + lambert.x() + " y=" + lambert.y());
// → x≈618115  y≈2430676
```

---

## Précision

| Méthode | Algorithme | Précision |
|---|---|---|
| `convertToWGS84` / `convertToWGS84Deg` | Helmert 7 paramètres (NTG_80 ALG0009/ALG0012) | ±1–3 m |
| `convertToLambert` / `convertToLambertByAlg003` | Projection conique conforme (NTG_71 ALG0003) | ±1–3 m |
| `convertToRGF93` | Grille NTv2 GeoTIFF bilinéaire (NTG_88 / NT111) | < 1 cm |
| `convertToLambertNtf` | Grille NTv2 GeoTIFF inverse itérative | < 1 cm |

La grille embarquée (`fr_ign_ntf_r93.tif`) couvre la France métropolitaine (41°–52°N, 5,5°W–10°E) avec un pas de 0,1°.

---

## Références

L'implémentation suit les notices algorithmiques officielles de l'IGN :

| Document | Description |
|---|---|
| [NTG_71](https://data.geopf.fr/annexes/ressources/documentation/geodesie/algorithmes/NTG_71.pdf) | Notice algorithmique — projection conique conforme de Lambert (ALG0001–ALG0004, ALG0019, ALG0021, ALG0054) |
| [NTG_80](https://data.geopf.fr/annexes/ressources/documentation/geodesie/algorithmes/NTG_80.pdf) | Notice algorithmique — transformations entre systèmes géodésiques (ALG0009, ALG0012) |
| [NTG_88](https://data.geopf.fr/annexes/ressources/documentation/geodesie/algorithmes/NTG_88.pdf) | Notice algorithmique — transformation NTF ↔ RGF93 par grille NTv2 |
| [NT/G 111](https://data.geopf.fr/annexes/ressources/documentation/geodesie/algorithmes/NT111_V1_HARMEL_TransfoNTF-RGF93_FormatGrilleNTV2.pdf) | Format de la grille NTv2 NTF → RGF93 |
| Transformations de coordonnées géodésiques | Guide pédagogique — transformations entre systèmes géodésiques *(document retiré du site IGN)* |
| Transformations entre systèmes géodésiques | Guide pédagogique — transformation Helmert NTF ↔ WGS84 *(document retiré du site IGN)* |
