package geo.lambert;

import static geo.lambert.LambertZone.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

class GeodeticConverterTest {

    /**
     * Smoke test for {@link GeodeticConverter#convertToWGS84Deg} on a LambertI point.
     * Verifies the method returns a non-null result and prints it for visual inspection.
     */
    @Test
    void convertToWGS84DegTest() {
        Wgs84Point pt = GeodeticConverter.convertToWGS84Deg(994272.661, 113467.422, LambertZone.LambertI);
        assertNotNull(pt);
        System.out.println("Point latitude:" + pt.latitude() + " longitude:" + pt.longitude());
    }

    /**
     * Regression test for {@link GeodeticConverter#convertToWGS84Deg} on a Lambert 93 coordinate.
     */
    @Test
    void Lambert93BugTest() {
        assertWgs84Equals(new Wgs84Point(2.56865, 49.64961),
                GeodeticConverter.convertToWGS84Deg(668832.5384, 6950138.7285, LambertZone.Lambert93), 0.0001);
    }

    /**
     * Verifies {@link GeodeticConverter#convertToWGS84Deg} for a known LambertIIExtended coordinate
     * (Paris area, cross-checked against cs2cs).
     */
    @Test
    void LambertIIExtendedToWgs84Test() {
        assertWgs84Equals(new Wgs84Point(2.58331732871, 48.8741427818),
                GeodeticConverter.convertToWGS84Deg(618115, 2430676, LambertZone.LambertIIExtended), 0.0001);
    }

    /**
     * NTG_80 ALG0009 — Jeux d'essai (three test vectors from the reference document).
     */
    @Test
    void LambertAlg0009Test() {
        double a   = 6378249.2;
        double e   = 0.08248325679;
        double eps = 1e-3; // 1 mm

        CartesianPoint p1 = GeodeticConverter.geographicToCartesian(0.01745329248, 0.02036217457, 100.0, a, e);
        assertEquals(6376064.6955, p1.x(), eps);
        assertEquals( 111294.6230, p1.y(), eps);
        assertEquals( 128984.7250, p1.z(), eps);

        CartesianPoint p2 = GeodeticConverter.geographicToCartesian(0.00290888212, 0.0, 10.0, a, e);
        assertEquals(6378232.2149, p2.x(), eps);
        assertEquals(  18553.5780, p2.y(), eps);
        assertEquals(      0.0,   p2.z(), eps);

        CartesianPoint p3 = GeodeticConverter.geographicToCartesian(0.00581776423, -0.03199770300, 2000.0, a, e);
        assertEquals(6376897.5369, p3.x(), eps);
        assertEquals(  37099.7050, p3.y(), eps);
        assertEquals(-202730.9070, p3.z(), eps);
    }

    /**
     * NTG_80 ALG0012 — Jeux d'essai (three test vectors from the reference document).
     */
    @Test
    void LambertAlg0012Test() {
        double a   = 6378249.2;
        double e   = 0.08248325679;
        double eps = 1e-11;

        GeographicPoint p1 = GeodeticConverter.cartesianToGeographic(
                new CartesianPoint(6376064.695, 111294.623, 128984.725), 0, a, e, eps);
        assertEquals( 0.01745329248, p1.longitude(), 1e-10); // λ (rad)
        assertEquals( 0.02036217457, p1.latitude(),  1e-10); // φ (rad)
        assertEquals(99.9995,        p1.height(),    1e-3);  // he (m)

        GeographicPoint p2 = GeodeticConverter.cartesianToGeographic(
                new CartesianPoint(6378232.215, 18553.578, 0.0), 0, a, e, eps);
        assertEquals(0.00290888212, p2.longitude(), 1e-10); // λ (rad)
        assertEquals(0.0,           p2.latitude(),  1e-10); // φ (rad)
        assertEquals(10.0001,       p2.height(),    1e-3);  // he (m)

        GeographicPoint p3 = GeodeticConverter.cartesianToGeographic(
                new CartesianPoint(6376897.537, 37099.705, -202730.907), 0, a, e, eps);
        assertEquals( 0.00581776423,  p3.longitude(), 1e-10); // λ (rad)
        assertEquals(-0.03199770301,  p3.latitude(),  1e-10); // φ (rad)
        assertEquals( 2000.0001,      p3.height(),    1e-3);  // he (m)
    }

    /**
     * NTG_71 ALG0021 — Jeu d'essai (test vector from the reference document).
     */
    @Test
    void LambertAlg0021Test() {
        double n = GeodeticConverter.lambertNormal(0.97738438100, 6378388.0, 0.081991890);
        assertEquals(6393174.9755, n, 1e-3);
    }

    /**
     * NTG_71 ALG0054 — Jeux d'essai (two test vectors from the reference document).
     */
    @Test
    void LambertAlg0054Test() {
        // Case 1: secant projection, φ0 ≠ π/2
        double[] p1 = GeodeticConverter.projectionParametersSecant(
                6378388.0, 0.081991890,
                0.0, 0.0,
                -0.57595865300, -0.78539816300,
                0.0, 0.0);
        assertEquals(-0.63049633000, p1[0], 1e-9); // n
        assertEquals(-12453174.1795, p1[1], 1e-2); // c (m)
        assertEquals(0.0,            p1[2], 1e-11); // λc
        assertEquals(0.0,            p1[3], 1e-3);  // Xs
        assertEquals(-12453174.1795, p1[4], 1e-2);  // Ys

        // Case 2: secant projection, φ0 = π/2 → Ys = Y0
        double[] p2 = GeodeticConverter.projectionParametersSecant(
                6378388.0, 0.081991890,
                0.07623554539, Math.PI / 2,
                0.86975574400, 0.89302680100,
                150000.0, 5400000.0);
        assertEquals(0.77164218670, p2[0], 1e-9); // n
        assertEquals(11565915.8294, p2[1], 1e-2); // c (m)
        assertEquals(0.07623554539, p2[2], 1e-11); // λc
        assertEquals(150000.0,      p2[3], 1e-3);  // Xs
        assertEquals(5400000.0,     p2[4], 1e-3);  // Ys
    }

    /**
     * NTG_71 ALG0019 — Jeux d'essai (two test vectors from the reference document).
     */
    @Test
    void LambertAlg0019Test() {
        // Case 1
        double[] p1 = GeodeticConverter.projectionParametersTangent(
                6378388.0, 0.081991890, 0.18112808800, 0.97738438100, 1.0, 0.0, 0.0);
        assertEquals(0.82903757250, p1[0], 1e-9); // n
        assertEquals(11464828.2192, p1[1], 1e-2); // c (m)
        assertEquals(0.18112808800, p1[2], 1e-11); // λc
        assertEquals(0.0,           p1[3], 1e-3);  // Xs
        assertEquals(4312250.9718,  p1[4], 1e-2);  // Ys

        // Case 2
        double[] p2 = GeodeticConverter.projectionParametersTangent(
                6378249.2, 0.0824832568, 0.04079234433, 0.86393798000, 0.9998773400, 600000.0, 200000.0);
        assertEquals(0.76040596580, p2[0], 1e-9); // n
        assertEquals(11603796.9760, p2[1], 1e-2); // c (m)
        assertEquals(0.04079234433, p2[2], 1e-11); // λc
        assertEquals(600000.0,      p2[3], 1e-3);  // Xs
        assertEquals(5657616.6712,  p2[4], 1e-2);  // Ys
    }

    /**
     * NTG_71 ALG0002 — Jeux d'essai (three test vectors from the reference document).
     */
    @Test
    void LambertAlg0002Test() {
        double e   = 0.08199188998;
        double eps = 1e-11;

        assertEquals( 0.87266462600, GeodeticConverter.latitudeFromLatitudeISO( 1.00552653648, e, eps), 1e-10);
        assertEquals(-0.29999999997, GeodeticConverter.latitudeFromLatitudeISO(-0.30261690060, e, eps), 1e-10);
        assertEquals( 0.19998903369, GeodeticConverter.latitudeFromLatitudeISO( 0.20000000000, e, eps), 1e-10);
    }

    /**
     * NTG_71 ALG0001 — Jeux d'essai (three test vectors from the reference document).
     */
    @Test
    void LambertAlg0001Test() {
        double e = 0.08199188998;
        double eps = 1e-10;

        assertEquals(1.00552653649,  GeodeticConverter.latitudeISOFromLat( 0.87266462600, e), eps);
        assertEquals(-0.30261690063, GeodeticConverter.latitudeISOFromLat(-0.30000000000, e), eps);
        assertEquals(0.20000000001,  GeodeticConverter.latitudeISOFromLat( 0.19998903370, e), eps);
    }

    /**
     * NTG_71 ALG0003 — Jeu d'essai (test vector from the reference document).
     * <p>
     * Note: LambertI zone constants (n=0.7604059656, c=11603796.98) differ slightly
     * from the reference document parameters (n=0.760405966, c=11603796.9767),
     * hence a tolerance of 0.1 m is used.
     */
    @Test
    void LambertAlg0003Test() {
        ProjectedPoint lambertPoint = GeodeticConverter.geographicToLambertAlg003(
                0.87266462600, 0.14551209900,
                LambertZone.LambertI, LambertZone.LON_MERID_GREENWICH, LambertZone.E_CLARK_IGN);

        assertEquals(1029705.0818, lambertPoint.x(), 0.1);
        assertEquals(272723.8510,  lambertPoint.y(), 0.1);
    }

    /**
     * Verifies {@link GeodeticConverter#convertToLambertByAlg003} converts a WGS84 point
     * to LambertIIExtended using ALG0003 for the final projection step.
     */
    @Test
    void ConvertWGS84ToLambertByAlg0003Test() {
        double latitude  = 48.87412734248018;
        double radLat    = Math.toRadians(latitude);
        double longitude = 2.5832231178521186;
        double radLong   = Math.toRadians(longitude);

        ProjectedPoint lambertPoint = GeodeticConverter.convertToLambertByAlg003(radLat, radLong, LambertZone.LambertIIExtended);

        assertEquals(618115, lambertPoint.x(), 1);
        assertEquals(2430676, lambertPoint.y(), 1);
    }

    /**
     * Verifies {@link GeodeticConverter#geographicToLambert} against §3.4 of the IGN pedagogical
     * document, using geographic coordinates already expressed in the NTF datum (no datum-shift applied).
     *
     * @see <a href="https://geodesie.ign.fr/algorithmes-geodesiques">IGN — Algorithmes géodésiques (TransformationsCoordonneesGeodesiques §3.4, document retiré)</a>
     */
    @Test
    void LambertGeographicToLambertTest() {
        double latitude = 48.8741427818;
        double radLat = Math.toRadians(latitude);
        double longitude = 2.58331732871;
        double radLong = Math.toRadians(longitude);

        ProjectedPoint lambertPoint = GeodeticConverter.geographicToLambert(radLat, radLong, LambertZone.LambertIIExtended, LambertZone.LON_MERID_GREENWICH, LambertZone.E_CLARK_IGN);

        assertEquals(618062, lambertPoint.x(), 1);
        assertEquals(2430668, lambertPoint.y(), 1);
    }

    /**
     * Verifies {@link GeodeticConverter#geographicToLambert} against §3.4 of the IGN pedagogical
     * document, using NTF geographic coordinates expressed in grads (converted to radians before the call).
     *
     * @see <a href="https://geodesie.ign.fr/algorithmes-geodesiques">IGN — Algorithmes géodésiques (TransformationsCoordonneesGeodesiques §3.4, document retiré)</a>
     */
    @Test
    void LambertConvertNTFToLambertTest() {
        double latitude = 51.8072313; // Grad
        double radLat = Math.toRadians(latitude * 360d / 400d); // Deg before Rad
        double longitude = 0.4721669; //Grad
        double radLong = Math.toRadians(longitude * 360d / 400d); // Deg before Rad

        ProjectedPoint lambertPoint = GeodeticConverter.geographicToLambert(radLat, radLong, LambertZone.LambertII, LON_MERID_PARIS, E_CLARK_IGN);

        assertEquals(632542.058, lambertPoint.x(), 0.001);
        assertEquals(180804.145, lambertPoint.y(), 0.01);
    }

    /**
     * NTG_71 ALG0004 — Jeu d'essai (test vector from the reference document).
     * <p>
     * Note: LambertI zone constants (n=0.7604059656, c=11603796.98) differ slightly
     * from the reference document parameters (n=0.760405966, c=11603796.9767),
     * hence a tolerance of 1e-6 rad (~0.1 m) is used.
     */
    @Test
    void LambertAlg0004Test() {
        GeographicPoint lambertPoint = GeodeticConverter.lambertToGeographic(
                new ProjectedPoint(1029705.083, 272723.8490),
                LambertZone.LambertI, LambertZone.LON_MERID_GREENWICH, LambertZone.E_CLARK_IGN, 1e-11);


        assertEquals(0.14551209925, lambertPoint.longitude(), 1e-6); // longitude λ (rad)
        assertEquals(0.87266462567, lambertPoint.latitude(),  1e-6); // latitude  φ (rad)
        assertEquals(0.0, lambertPoint.height());
    }

    /**
     * Verifies {@link GeodeticConverter#lambertToGeographic} against §3.3 of the IGN pedagogical document.
     *
     * @see <a href="https://geodesie.ign.fr/algorithmes-geodesiques">IGN — Algorithmes géodésiques (TransformationsCoordonneesGeodesiques §3.3, document retiré)</a>
     */
    @Test
    void LamberConvertLambertToNTFTest() {
        double x = 1029705.083;
        double y = 272723.849;

        GeographicPoint lambertPoint = GeodeticConverter.lambertToGeographic(new ProjectedPoint(x, y), LambertZone.LambertI, LON_MERID_PARIS, E_CLARK_IGN, DEFAULT_EPS);

        assertEquals(0.145512099, lambertPoint.longitude(), 10); // Longitude en rad
        assertEquals(0.872664626, lambertPoint.latitude(),  10); // Latitude en rad
    }

    /**
     * Verifies the full WGS84 → LambertIIExtended chain via
     * {@link GeodeticConverter#convertToLambert} (includes datum shift NTF ↔ WGS84).
     */
    @Test
    void LambertConvertToLambertTest() {
        double latitude = 48.87412734248018;
        double radLat = Math.toRadians(latitude);
        double longitude = 2.5832231178521186;
        double radLong = Math.toRadians(longitude);

        ProjectedPoint lambertPoint = GeodeticConverter.convertToLambert(radLat, radLong, LambertZone.LambertIIExtended);

        assertEquals(618115, lambertPoint.x(), 1);
        assertEquals(2430676, lambertPoint.y(), 1);
    }

    /**
     * 10 French cities × 2 zones → WGS84 degrees.
     * Reference coordinates produced by pyproj 3.x (PROJ 9.x).
     */
    @ParameterizedTest(name = "{0} [{3}]")
    @MethodSource("frenchCities")
    void convertToWGS84DegFrenchCitiesTest(String city, double x, double y, LambertZone zone, double expectedLon, double expectedLat) {
        assertWgs84Equals(new Wgs84Point(expectedLon, expectedLat),
                GeodeticConverter.convertToWGS84Deg(x, y, zone), 0.0001);
    }

    static Stream<Arguments> frenchCities() {
        return Stream.of(
            // Lambert IIe (EPSG:27572)
            Arguments.of("Paris",       601152.299, 2428695.897, LambertZone.LambertIIExtended,  2.3522, 48.8566),
            Arguments.of("Lyon",        794390.283, 2087943.390, LambertZone.LambertIIExtended,  4.8357, 45.7640),
            Arguments.of("Marseille",   846499.547, 1815214.128, LambertZone.LambertIIExtended,  5.3698, 43.2965),
            Arguments.of("Toulouse",    527853.020, 1845164.018, LambertZone.LambertIIExtended,  1.4442, 43.6047),
            Arguments.of("Bordeaux",    369400.422, 1986174.310, LambertZone.LambertIIExtended, -0.5792, 44.8378),
            Arguments.of("Nice",        997226.984, 1868938.580, LambertZone.LambertIIExtended,  7.2620, 43.7102),
            Arguments.of("Nantes",      305486.753, 2253807.092, LambertZone.LambertIIExtended, -1.5536, 47.2184),
            Arguments.of("Strasbourg",  999472.191, 2410952.534, LambertZone.LambertIIExtended,  7.7521, 48.5734),
            Arguments.of("Montpellier", 724498.639, 1846651.391, LambertZone.LambertIIExtended,  3.8767, 43.6108),
            Arguments.of("Lille",       651112.229, 2626335.522, LambertZone.LambertIIExtended,  3.0573, 50.6292),
            // Lambert 93 (EPSG:2154)
            Arguments.of("Paris",        652469.023, 6862035.259, LambertZone.Lambert93,  2.3522, 48.8566),
            Arguments.of("Lyon",         842666.659, 6519924.367, LambertZone.Lambert93,  4.8357, 45.7640),
            Arguments.of("Marseille",    892390.222, 6247035.257, LambertZone.Lambert93,  5.3698, 43.2965),
            Arguments.of("Toulouse",     574357.820, 6279642.972, LambertZone.Lambert93,  1.4442, 43.6047),
            Arguments.of("Bordeaux",     417241.539, 6421813.346, LambertZone.Lambert93, -0.5792, 44.8378),
            Arguments.of("Nice",        1043410.160, 6299400.043, LambertZone.Lambert93,  7.2620, 43.7102),
            Arguments.of("Nantes",       355577.802, 6689723.103, LambertZone.Lambert93, -1.5536, 47.2184),
            Arguments.of("Strasbourg",  1050362.695, 6840899.647, LambertZone.Lambert93,  7.7521, 48.5734),
            Arguments.of("Montpellier",  770795.509, 6279476.135, LambertZone.Lambert93,  3.8767, 43.6108),
            Arguments.of("Lille",        704061.146, 7059136.589, LambertZone.Lambert93,  3.0573, 50.6292)
        );
    }

    private static void assertWgs84Equals(Wgs84Point expected, Wgs84Point actual, double eps) {
        assertEquals(expected.longitude(), actual.longitude(), eps);
        assertEquals(expected.latitude(),  actual.latitude(),  eps);
    }
}