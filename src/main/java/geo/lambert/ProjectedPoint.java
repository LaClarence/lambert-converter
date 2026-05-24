package geo.lambert;

/**
 * Coordonnées en projection cartographique plane (Lambert conique conforme).
 *
 * @param x abscisse en mètres
 * @param y ordonnée en mètres
 */
public record ProjectedPoint(double x, double y) {}