package geo.lambert;

/**
 * Coordonnées géographiques ellipsoïdales.
 *
 * @param longitude longitude λ en radians (positif vers l'Est)
 * @param latitude  latitude φ en radians (positif vers le Nord)
 * @param height    hauteur ellipsoïdale en mètres
 */
public record GeographicPoint(double longitude, double latitude, double height) {}