package geo.lambert;

/**
 * Coordonnées géographiques WGS84 en degrés décimaux.
 *
 * @param longitude longitude en degrés décimaux (positif vers l'Est)
 * @param latitude  latitude en degrés décimaux (positif vers le Nord)
 */
public record Wgs84Point(double longitude, double latitude) {}