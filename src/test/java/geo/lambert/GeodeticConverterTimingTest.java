package geo.lambert;

import static geo.lambert.LambertZone.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Indicative timing measurements for each public static method of {@link GeodeticConverter}.
 * <p>
 * Each test warms up the JIT with {@value #WARMUP} calls, then measures {@value #ITERATIONS}
 * calls and prints the average time in microseconds. Results are also written to a
 * timestamped CSV file ({@code timing_YYYY_MM_dd.csv}) at the project root so that the
 * file survives {@code mvn clean}.
 * <p>
 * Note: these are not rigorous benchmarks — use JMH for accurate microbenchmarking.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GeodeticConverterTimingTest {

    private static final int WARMUP     = 1_000;
    private static final int ITERATIONS = 10_000;
    private static final DateTimeFormatter DATE_FMT      = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss");
    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final List<String[]> results = new ArrayList<>();

    // Shared inputs (pre-computed to exclude setup cost from measurements)
    private static final double LAT_RAD   = Math.toRadians(48.87412734248018);
    private static final double LON_RAD   = Math.toRadians(2.5832231178521186);
    private static final ProjectedPoint PROJECTED = new ProjectedPoint(618115, 2430676);
    private static final ProjectedPoint LAMBERT_I = new ProjectedPoint(1029705.083, 272723.849);

    @BeforeAll
    static void printHeader() {
        System.out.printf("%-40s  %12s%n", "Method", "µs / call");
        System.out.println("-".repeat(56));
    }

    @AfterAll
    static void writeCsv() throws IOException {
        Path csvPath = Path.of("timing_" + LocalDateTime.now().format(DATE_FMT) + ".csv");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(csvPath))) {
            pw.println("timestamp,method,iterations,us_per_call");
            for (String[] row : results) pw.println(String.join(",", row));
        }
        System.out.println("\nTiming results written to " + csvPath.toAbsolutePath());
    }

    /** ALG0001 — {@link GeodeticConverter#latitudeISOFromLat}. */
    @Test @Order(1)
    void latitudeISOFromLat() {
        measure("latitudeISOFromLat",
                () -> GeodeticConverter.latitudeISOFromLat(LAT_RAD, E_CLARK_IGN));
    }

    /** ALG0003 — {@link GeodeticConverter#geographicToLambertAlg003}. */
    @Test @Order(2)
    void geographicToLambertAlg003() {
        measure("geographicToLambertAlg003",
                () -> GeodeticConverter.geographicToLambertAlg003(LAT_RAD, LON_RAD, LambertIIExtended, LON_MERID_GREENWICH, E_CLARK_IGN));
    }

    /** §3.4 direct formula — {@link GeodeticConverter#geographicToLambert}. */
    @Test @Order(3)
    void geographicToLambert() {
        measure("geographicToLambert",
                () -> GeodeticConverter.geographicToLambert(LAT_RAD, LON_RAD, LambertIIExtended, LON_MERID_GREENWICH, E_CLARK_IGN));
    }

    /** ALG0004 — {@link GeodeticConverter#lambertToGeographic}. */
    @Test @Order(4)
    void lambertToGeographic() {
        measure("lambertToGeographic",
                () -> GeodeticConverter.lambertToGeographic(LAMBERT_I, LambertZone.LambertI, LON_MERID_GREENWICH, E_CLARK_IGN, DEFAULT_EPS));
    }

    /** Full NTF chain — {@link GeodeticConverter#convertToWGS84(ProjectedPoint, LambertZone)}. */
    @Test @Order(5)
    void convertToWGS84_projected() {
        measure("convertToWGS84(ProjectedPoint, zone)",
                () -> GeodeticConverter.convertToWGS84(PROJECTED, LambertIIExtended));
    }

    /** Convenience overload — {@link GeodeticConverter#convertToWGS84(double, double, LambertZone)}. */
    @Test @Order(6)
    void convertToWGS84_xy() {
        measure("convertToWGS84(x, y, zone)",
                () -> GeodeticConverter.convertToWGS84(618115, 2430676, LambertIIExtended));
    }

    /** Degrees output — {@link GeodeticConverter#convertToWGS84Deg}. */
    @Test @Order(7)
    void convertToWGS84Deg() {
        measure("convertToWGS84Deg",
                () -> GeodeticConverter.convertToWGS84Deg(618115, 2430676, LambertIIExtended));
    }

    /** Full WGS84→NTF chain — {@link GeodeticConverter#convertToLambert}. */
    @Test @Order(8)
    void convertToLambert() {
        measure("convertToLambert",
                () -> GeodeticConverter.convertToLambert(LAT_RAD, LON_RAD, LambertIIExtended));
    }

    /** Full WGS84→NTF chain via ALG0003 — {@link GeodeticConverter#convertToLambertByAlg003}. */
    @Test @Order(9)
    void convertToLambertByAlg003() {
        measure("convertToLambertByAlg003",
                () -> GeodeticConverter.convertToLambertByAlg003(LAT_RAD, LON_RAD, LambertIIExtended));
    }

    private static void measure(String name, Runnable action) {
        for (int i = 0; i < WARMUP; i++) action.run();
        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) action.run();
        long totalNanos = System.nanoTime() - start;
        double microsPerCall = totalNanos / 1000.0 / ITERATIONS;
        System.out.printf("%-40s  %12.3f%n", name, microsPerCall);
        results.add(new String[]{
                LocalDateTime.now().format(TIMESTAMP_FMT),
                name,
                String.valueOf(ITERATIONS),
                String.format(Locale.ROOT, "%.3f", microsPerCall)
        });
        assertTrue(totalNanos > 0);
    }
}
