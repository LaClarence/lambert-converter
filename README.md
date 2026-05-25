# lambert-converter
A library to convert Lambert Coordinates to GPS WGS84 coordinates based on the [IGN algorithms and methods](http://geodesie.ign.fr/contenu/fichiers/documentation/algorithmes/notice/NTG_71.pdf)

##  Build

pre-requisites:
* Java 26 installed
* Maven 3.9.15 installed
* Compile with `mvn clean package`
* Add the generated `target/lambert-java-<pom-version>.jar` file to your project

## References

The implementation follows the official IGN (Institut national de l'information géographique et forestière) geodetic algorithm notices:

| Document | Description |
|---|---|
| [NTG_71](https://geodesie.ign.fr/contenu/fichiers/documentation/algorithmes/notice/NTG_71.pdf) | Algorithmic notice — Lambert conformal conic projection (ALG0001–ALG0004, ALG0019, ALG0021, ALG0054) |
| [NTG_80](https://geodesie.ign.fr/contenu/fichiers/documentation/algorithmes/notice/NTG_80.pdf) | Algorithmic notice — geodetic system transformations (ALG0009, ALG0012) |
| [Transformations de coordonnées géodésiques](http://geodesie.ign.fr/contenu/fichiers/documentation/pedagogiques/TransformationsCoordonneesGeodesiques.pdf) | Pedagogical guide — coordinate transformations between geodetic systems |
| [Transformations entre systèmes géodésiques](http://geodesie.ign.fr/contenu/fichiers/documentation/pedagogiques/transfo.pdf) | Pedagogical guide — NTF ↔ WGS84 seven-parameter Helmert transformation |

## Usage example

```java
import geo.lambert.GeodeticConverter;
import geo.lambert.LambertZone;
import geo.lambert.Wgs84Point;

Wgs84Point pt = GeodeticConverter.convertToWGS84Deg(994272.661, 113467.422, LambertZone.LambertI);
System.out.println("Point latitude:" + pt.latitude() + " longitude:" + pt.longitude());
```
