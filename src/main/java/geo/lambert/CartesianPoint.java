package geo.lambert;

/**
 * Coordonnées cartésiennes tridimensionnelles géocentriques.
 *
 * @param x composante X en mètres
 * @param y composante Y en mètres
 * @param z composante Z en mètres
 */
public record CartesianPoint(double x, double y, double z) {

    /** Returns a new point offset by (dx, dy, dz). */
    public CartesianPoint translate(double dx, double dy, double dz) {
        return new CartesianPoint(x + dx, y + dy, z + dz);
    }
}