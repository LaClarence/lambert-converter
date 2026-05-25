package geo.lambert;

/**
 * Coordonnées géographiques en degrés décimaux dans le système RGF93v2b
 * (méridien de Greenwich, positif vers l'Est / le Nord).
 * <p>
 * RGF93 est le système de référence géodésique officiel français depuis 1993,
 * aligné avec ETRS89 à quelques centimètres près. Il est distinct de WGS84
 * (lequel est maintenu par le DoD américain) bien que très proches en pratique.
 *
 * @param longitude longitude en degrés décimaux (positif vers l'Est)
 * @param latitude  latitude en degrés décimaux (positif vers le Nord)
 */
public record Rgf93Point(double longitude, double latitude) {}
