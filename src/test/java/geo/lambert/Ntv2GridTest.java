package geo.lambert;

import org.junit.jupiter.api.Test;

import static geo.lambert.LambertZone.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de la grille NTv2 et de la chaîne Lambert NTF ↔ RGF93.
 *
 * <h2>Tests autonomes (toujours exécutés)</h2>
 * <ul>
 *   <li>Interpolation bilinéaire sur grille synthétique 3×3.</li>
 *   <li>Aller-retour NTF→RGF93→NTF (round-trip) sur grille constante.</li>
 *   <li>Chaîne complète Lambert NTF → RGF93 puis RGF93 → Lambert NTF.</li>
 *   <li>Validation des cas limites (point hors grille, Lambert93 rejeté).</li>
 * </ul>
 *
 * <h2>Test d'intégration avec une grille externe (optionnel)</h2>
 * Activé uniquement si la propriété système {@code gr3df97a.path} pointe vers
 * un fichier de grille NTF→RGF93 au format NTv2 ({@code .gsb}) ou GeoTIFF GTG ({@code .tif}),
 * par exemple {@code fr_ign_ntf_r93.tif} (PROJ CDN) ou {@code GR3DF97A.gsb} (IGN) :
 * <pre>
 *   mvn test -Dgr3df97a.path=/chemin/vers/fr_ign_ntf_r93.tif
 * </pre>
 */
class Ntv2GridTest {

    // =========================================================================
    // Grilles synthétiques partagées
    // =========================================================================

    /**
     * Grille 3×3 couvrant [44°–46°N, 1°–3°E] avec pas de 1°.
     * Correction latitude  = 1,0" partout.
     * Correction longitude = 2,0" partout.
     */
    private static Ntv2Grid uniformGrid() {
        int n = 9;
        float[] lat = new float[n];
        float[] lon = new float[n];
        for (int i = 0; i < n; i++) { lat[i] = 1.0f; lon[i] = 2.0f; }
        return Ntv2Grid.of(44.0, 46.0, 1.0, 3.0, 1.0, 1.0, lat, lon);
    }

    /**
     * Grille uniforme large couvrant [42°–52°N, -6°–10°E] avec pas de 1°,
     * utilisée pour les tests chaîne complète (Paris ≈ 48,87°N, 2,58°E).
     * Correction latitude  = 1,0" partout.
     * Correction longitude = 2,0" partout.
     */
    private static Ntv2Grid uniformGridFrance() {
        // 11 lignes (42–52°N) × 17 colonnes (-6–10°E) = 187 nœuds
        int nRows = 11, nCols = 17, n = nRows * nCols;
        float[] lat = new float[n];
        float[] lon = new float[n];
        for (int i = 0; i < n; i++) { lat[i] = 1.0f; lon[i] = 2.0f; }
        return Ntv2Grid.of(42.0, 52.0, -6.0, 10.0, 1.0, 1.0, lat, lon);
    }

    /**
     * Grille 2×2 couvrant [44°–45°N, 1°–2°E].
     * Valeurs aux nœuds (ordre S→N, O→E) :
     * <pre>
     *   NW (lat=45, lon=1) : dLat=2", dLon=4"
     *   NE (lat=45, lon=2) : dLat=4", dLon=2"
     *   SW (lat=44, lon=1) : dLat=0", dLon=0"
     *   SE (lat=44, lon=2) : dLat=2", dLon=4"
     * </pre>
     * Stockage ligne-majeur S→N, col O→E : [SW, SE, NW, NE].
     */
    private static Ntv2Grid bilinearGrid() {
        return Ntv2Grid.of(
                44.0, 45.0, 1.0, 2.0, 1.0, 1.0,
                new float[]{ 0f, 2f, 2f, 4f }, // latShifts : SW, SE, NW, NE
                new float[]{ 0f, 4f, 4f, 2f }  // lonShifts : SW, SE, NW, NE
        );
    }

    // =========================================================================
    // Tests d'interpolation bilinéaire
    // =========================================================================

    @Test
    void interpolation_uniformGrid_anyPoint_returnsConstantShift() {
        Ntv2Grid g = uniformGrid();
        // Coin
        double[] d = g.interpolate(44.0, 1.0);
        assertEquals(1.0, d[0], 1e-9, "dLat coin SW");
        assertEquals(2.0, d[1], 1e-9, "dLon coin SW");
        // Centre
        d = g.interpolate(45.0, 2.0);
        assertEquals(1.0, d[0], 1e-9, "dLat centre");
        assertEquals(2.0, d[1], 1e-9, "dLon centre");
        // Point quelconque
        d = g.interpolate(44.7, 1.3);
        assertEquals(1.0, d[0], 1e-9, "dLat intérieur");
        assertEquals(2.0, d[1], 1e-9, "dLon intérieur");
    }

    @Test
    void interpolation_bilinearGrid_centerPoint() {
        // Au centre (t=0.5, u=0.5) la valeur est la moyenne des 4 coins.
        Ntv2Grid g = bilinearGrid();
        double[] d = g.interpolate(44.5, 1.5);
        double expectedLat = (0 + 2 + 2 + 4) / 4.0; // 2.0"
        double expectedLon = (0 + 4 + 4 + 2) / 4.0; // 2.5"
        assertEquals(expectedLat, d[0], 1e-6, "dLat au centre");
        assertEquals(expectedLon, d[1], 1e-6, "dLon au centre");
    }

    @Test
    void interpolation_bilinearGrid_southEdge() {
        // Bord sud (u=0) : interpolation linéaire E–O entre SW et SE.
        Ntv2Grid g = bilinearGrid();
        // t=0.5, u=0 → (SW+SE)/2
        double[] d = g.interpolate(44.0, 1.5);
        assertEquals((0f + 2f) / 2.0, d[0], 1e-6, "dLat bord sud");
        assertEquals((0f + 4f) / 2.0, d[1], 1e-6, "dLon bord sud");
    }

    @Test
    void interpolation_bilinearGrid_westEdge() {
        // Bord ouest (t=0) : interpolation linéaire S–N entre SW et NW.
        Ntv2Grid g = bilinearGrid();
        // t=0, u=0.5 → (SW+NW)/2
        double[] d = g.interpolate(44.5, 1.0);
        assertEquals((0f + 2f) / 2.0, d[0], 1e-6, "dLat bord ouest");
        assertEquals((0f + 4f) / 2.0, d[1], 1e-6, "dLon bord ouest");
    }

    @Test
    void interpolation_outOfBounds_throwsException() {
        Ntv2Grid g = uniformGrid();
        assertThrows(IllegalArgumentException.class,
                () -> g.interpolate(40.0, 2.0), "Hors sud");
        assertThrows(IllegalArgumentException.class,
                () -> g.interpolate(45.0, 0.5), "Hors ouest");
    }

    // =========================================================================
    // Tests de couverture
    // =========================================================================

    @Test
    void covers_insideGrid_returnsTrue() {
        Ntv2Grid g = uniformGrid();
        assertTrue(g.covers(45.0, 2.0));
        assertTrue(g.covers(44.0, 1.0)); // coin
        assertTrue(g.covers(46.0, 3.0)); // coin opposé
    }

    @Test
    void covers_outsideGrid_returnsFalse() {
        Ntv2Grid g = uniformGrid();
        assertFalse(g.covers(43.9, 2.0));
        assertFalse(g.covers(45.0, 3.1));
    }

    // =========================================================================
    // Tests NTF ↔ RGF93
    // =========================================================================

    @Test
    void ntfToRgf93_uniformShift_correctlyApplied() {
        // grille : dLat=1", dLon=2"
        Ntv2Grid g = uniformGrid();
        Rgf93Point r = g.ntfToRgf93(45.0, 2.0);
        assertEquals(45.0 + 1.0 / 3600.0, r.latitude(),  1e-10, "latitude RGF93");
        assertEquals( 2.0 + 2.0 / 3600.0, r.longitude(), 1e-10, "longitude RGF93");
    }

    @Test
    void rgf93ToNtf_uniformShift_roundTrip() {
        Ntv2Grid g = uniformGrid();
        double latNtf = 45.0, lonNtf = 2.0;
        Rgf93Point rgf93 = g.ntfToRgf93(latNtf, lonNtf);
        double[] back = g.rgf93ToNtf(rgf93.latitude(), rgf93.longitude());
        assertEquals(latNtf, back[0], 1e-9, "round-trip latitude");
        assertEquals(lonNtf, back[1], 1e-9, "round-trip longitude");
    }

    // =========================================================================
    // Tests chaîne complète GeodeticConverter
    // =========================================================================

    @Test
    void convertToRGF93_lambert2Extended_applyShift() {
        // Grille uniforme couvrant la France : les coordonnées RGF93 doivent
        // différer des coordonnées NTF des corrections de la grille (+1"/+2").
        Ntv2Grid g = uniformGridFrance();
        // Point Paris approx. : Lambert IIE (618115, 2430676)
        ProjectedPoint paris = new ProjectedPoint(618115, 2430676);
        Rgf93Point rgf93 = GeodeticConverter.convertToRGF93(paris, LambertIIExtended, g);
        // La grille uniforme ajoute +1"/3600° en lat et +2"/3600° en lon
        assertNotNull(rgf93);
        // Vérification de cohérence : latitude entre 48° et 49°N
        assertTrue(rgf93.latitude()  > 48.0 && rgf93.latitude()  < 49.0, "latitude plausible");
        assertTrue(rgf93.longitude() >  2.0 && rgf93.longitude() <  3.0, "longitude plausible");
    }

    @Test
    void convertToLambertNtf_roundTrip_subMeter() {
        // Aller-retour Lambert → RGF93 → Lambert doit rester < 0,01 m
        // avec une grille uniforme (correction constante).
        Ntv2Grid g = uniformGridFrance();
        ProjectedPoint original = new ProjectedPoint(618115, 2430676);
        Rgf93Point rgf93 = GeodeticConverter.convertToRGF93(original, LambertIIExtended, g);
        ProjectedPoint back = GeodeticConverter.convertToLambertNtf(rgf93, LambertIIExtended, g);
        assertEquals(original.x(), back.x(), 0.01, "X round-trip < 1 cm");
        assertEquals(original.y(), back.y(), 0.01, "Y round-trip < 1 cm");
    }

    @Test
    void convertToRGF93_lambert93_throwsUnsupported() {
        Ntv2Grid g = uniformGrid();
        assertThrows(UnsupportedOperationException.class,
                () -> GeodeticConverter.convertToRGF93(new ProjectedPoint(0, 0), Lambert93, g));
    }

    @Test
    void convertToLambertNtf_lambert93_throwsUnsupported() {
        Ntv2Grid g = uniformGrid();
        assertThrows(UnsupportedOperationException.class,
                () -> GeodeticConverter.convertToLambertNtf(new Rgf93Point(2.0, 45.0), Lambert93, g));
    }

    // =========================================================================
    // Tests grille embarquée (fr_ign_ntf_r93.tif depuis le classpath)
    // =========================================================================

    @Test
    void getDefault_loadsEmbeddedGrid() {
        Ntv2Grid g = Ntv2Grid.getDefault();
        // Métadonnées attendues pour fr_ign_ntf_r93.tif (156×111, pas 0,1°)
        assertEquals(41.0, g.southLatitude(),  1e-6, "limite sud");
        assertEquals(52.0, g.northLatitude(),  1e-6, "limite nord");
        assertTrue(g.westLongitude() < -5.0,          "limite ouest < -5°E");
        assertTrue(g.eastLongitude() >  9.0,          "limite est  > 9°E");
        assertEquals(111, g.rowCount(),               "111 lignes (0,1° × 110°)");
        assertEquals(156, g.columnCount(),            "156 colonnes (0,1° × 155°)");
    }

    @Test
    void getDefault_returnsSameInstance() {
        // Chargement paresseux : le même objet doit être retourné à chaque appel.
        assertSame(Ntv2Grid.getDefault(), Ntv2Grid.getDefault(), "singleton");
    }

    @Test
    void getDefault_parisCovered() {
        assertTrue(Ntv2Grid.getDefault().covers(48.8741, 2.5833), "Paris couvert");
    }

    @Test
    void getDefault_paris_lambertIIE_toRgf93_plausibleCoordinates() {
        // Point de référence : Lambert IIE (618115, 2430676) ≈ Paris, tour Eiffel aprox.
        // RGF93 doit être très proche du WGS84 géographique (~48,874°N, ~2,583°E).
        Rgf93Point r = GeodeticConverter.convertToRGF93(618115, 2430676, LambertIIExtended,
                Ntv2Grid.getDefault());
        assertEquals(48.874, r.latitude(),  5e-3, "latitude RGF93 Paris (±0,5°)");
        assertEquals( 2.583, r.longitude(), 5e-3, "longitude RGF93 Paris (±0,5°)");
    }

    @Test
    void getDefault_paris_roundTrip_subMillimeter() {
        // Aller-retour Lambert IIE → RGF93 → Lambert IIE avec la grille réelle.
        // La précision de la grille étant centimétrique, le round-trip doit rester < 1 mm.
        Ntv2Grid g = Ntv2Grid.getDefault();
        double x0 = 618115.0, y0 = 2430676.0;
        Rgf93Point rgf93 = GeodeticConverter.convertToRGF93(x0, y0, LambertIIExtended, g);
        var back = GeodeticConverter.convertToLambertNtf(rgf93, LambertIIExtended, g);
        assertEquals(x0, back.x(), 0.001, "X round-trip < 1 mm");
        assertEquals(y0, back.y(), 0.001, "Y round-trip < 1 mm");
    }
}
