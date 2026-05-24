# lambert-converter
A library to convert Lambert Coordinates to GPS WGS84 coordinates based on the [IGN algorithms and methods](http://geodesie.ign.fr/contenu/fichiers/documentation/algorithmes/notice/NTG_71.pdf)

##  Build

pre-requisites:
* Java 26 installed
* Maven 3.9.15 installed
* Compile with `mvn clean package`
* Add the generated `target/lambert-java-<pom-version>.jar` file to your project

## Usage example

```java
import geo.lambert.GeodeticConverter;
import geo.lambert.LambertZone;
import geo.lambert.Wgs84Point;

Wgs84Point pt = GeodeticConverter.convertToWGS84Deg(994272.661, 113467.422, LambertZone.LambertI);
System.out.println("Point latitude:" + pt.latitude() + " longitude:" + pt.longitude());
```
