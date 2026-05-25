package geo.lambert;


import static java.lang.Math.*;
import static geo.lambert.LambertZone.*;

/*
https://github.com/yageek/lambert-java
https://bintray.com/yageek/maven/lambert-java/view/files/net/yageek/lambert/lambert-java/1.1

Online samples :
http://geofree.fr/gf/coordinateConv.asp#listSys

--------------------------------------------------------------------------------------
Install cs2cs on Ubuntu :
http://www.sarasafavi.com/installing-gdalogr-on-ubuntu.html

--------------------------------------------------------------------------------------
http://cs2cs.mygeodata.eu/
Conversion From Lambert Zone II to WGS 84 :
$>cs2cs +proj=lcc +lat_1=46.8 +lat_0=46.8 +lon_0=0 +k_0=0.99987742 +x_0=600000 +y_0=2200000 +a=6378249.2 +b=6356515 +towgs84=-168,-60,320,0,0,0,0 +pm=paris +units=m +no_defs +to +proj=longlat +datum=WGS84 +no_defs -f "%.11f" <<EOF
> 618115 2430676
> EOF

2.58331732871	48.87414278182 43.05512374267

--------------------------------------------------------------------------------------
Conversion From WGS 84 To Lambert Zone II:
$>cs2cs +proj=longlat +datum=WGS84 +no_defs +to +proj=lcc +lat_1=46.8 +lat_0=46.8 +lon_0=0 +k_0=0.99987742 +x_0=600000 +y_0=2200000 +a=6378249.2 +b=6356515 +towgs84=-168,-60,320,0,0,0,0 +pm=paris +units=m +no_defs  -f "%.11f" <<EOF
2.58331732871 48.8741427818
EOF
618115.00035284588	2430676.00004872493 -43.05512374081
*/

/**
 * References:
 * @see <a href="https://data.geopf.fr/annexes/ressources/documentation/geodesie/algorithmes/NTG_71.pdf">NTG_71</a>
 * @see <a href="https://data.geopf.fr/annexes/ressources/documentation/geodesie/algorithmes/NTG_80.pdf">NTG_80</a>
 *
 */
public final class GeodeticConverter {

    private GeodeticConverter() {
        /* This utility class should not be instantiated */
    }

    /**
     * ALG0001 — Calcul de la latitude isométrique.
     * <p>
     * Calcule la latitude isométrique {@code L} sur un ellipsoïde de première
     * excentricité {@code e} au point de latitude {@code φ}.
     * <pre>
     *   L = ln( tan(π/4 + φ/2) · ( (1 − e·sin φ) / (1 + e·sin φ) )^(e/2) )
     * </pre>
     *
     * @param lat latitude φ en radians
     * @param e   première excentricité de l'ellipsoïde
     * @return    latitude isométrique L
     * @see <a href="https://data.geopf.fr/annexes/ressources/documentation/geodesie/algorithmes/NTG_71.pdf">NTG_71 ALG0001</a>
     */
    public static double latitudeISOFromLat(double lat, double e) {
        double elt11 = Math.PI / 4d;
        double elt12 = lat / 2d;
        double elt1 = tan(elt11 + elt12);

        double elt21 = e * sin(lat);
        double elt2 = pow((1 - elt21) / (1 + elt21), e / 2d);

        return log(elt1 * elt2);
    }


    /**
     * ALG0002 — Calcul de la latitude à partir de la latitude isométrique.
     * <p>
     * Calcule la latitude {@code φ} à partir de la latitude isométrique {@code L}
     * par itération jusqu'à convergence sous le seuil {@code ε}.
     * <pre>
     *   φ₀ = 2·arctan(exp(L)) − π/2
     *   φᵢ = 2·arctan( ((1 + e·sin φᵢ₋₁) / (1 − e·sin φᵢ₋₁))^(e/2) · exp(L) ) − π/2
     *   jusqu'à |φᵢ − φᵢ₋₁| &lt; ε
     * </pre>
     *
     * @param latISo latitude isométrique L
     * @param e      première excentricité de l'ellipsoïde
     * @param eps    tolérance de convergence ε
     * @return       latitude φ en radians
     * @see <a href="https://data.geopf.fr/annexes/ressources/documentation/geodesie/algorithmes/NTG_71.pdf">NTG_71 ALG0002</a>
     */
    static double latitudeFromLatitudeISO(double latISo, double e, double eps) {

        double phi0 = 2 * atan(exp(latISo)) - M_PI_2;
        double phiI = 2 * atan(pow((1 + e * sin(phi0)) / (1 - e * sin(phi0)), e / 2d) * exp(latISo)) - M_PI_2;
        double delta = abs(phiI - phi0);

        while (delta > eps) {
            phi0 = phiI;
            phiI = 2 * atan(pow((1 + e * sin(phi0)) / (1 - e * sin(phi0)), e / 2d) * exp(latISo)) - M_PI_2;
            delta = abs(phiI - phi0);
        }

        return phiI;
    }


    /**
     * ALG0003 — Transformation de coordonnées λ, φ → X, Y Lambert.
     * <p>
     * Transforme des coordonnées géographiques en coordonnées en projection conique
     * conforme de Lambert.
     * <pre>
     *   L = ALG0001(φ, e)
     *   X = Xs + c · exp(−n·L) · sin(n · (λ − λc))
     *   Y = Ys − c · exp(−n·L) · cos(n · (λ − λc))
     * </pre>
     *
     * @param latitude    latitude φ en radians
     * @param longitude   longitude λ en radians (par rapport au méridien origine)
     * @param zone        zone Lambert (fournit n, c, Xs, Ys)
     * @param lonMeridian longitude du méridien origine λc en radians
     * @param e           première excentricité de l'ellipsoïde
     * @return            point Lambert (X, Y) en mètres
     * @see <a href="https://data.geopf.fr/annexes/ressources/documentation/geodesie/algorithmes/NTG_71.pdf">NTG_71 ALG0003</a>
     */
    public static ProjectedPoint geographicToLambertAlg003(double latitude, double longitude, LambertZone zone, double lonMeridian, double e) {

        double n = zone.n();
        double C = zone.c();
        double xs = zone.xs();
        double ys = zone.ys();

        double latIso = latitudeISOFromLat(latitude, e);

        double eLatIso = exp(-n * latIso);

        double nLon = n * (longitude - lonMeridian);

        double x = xs + C * eLatIso * sin(nLon);
        double y = ys - C * eLatIso * cos(nLon);

        return new ProjectedPoint(x, y);
    }

    /**
     * Transformation de coordonnées géographiques → Lambert (formulation directe).
     * <p>
     * Équivalent à ALG0003 mais calcule la latitude isométrique par la formule
     * développée (section 3.4 du document pédagogique IGN) sans passer par ALG0001.
     * <pre>
     *   L  = ½·ln((1+sin φ)/(1−sin φ)) − (e/2)·ln((1+e·sin φ)/(1−e·sin φ))
     *   R  = c · exp(−n·L)
     *   X  = Xs + R · sin(n·(λ − λc))
     *   Y  = Ys − R · cos(n·(λ − λc))
     * </pre>
     *
     * @param latitude    latitude φ en radians
     * @param longitude   longitude λ en radians par rapport au méridien origine
     * @param zone        zone Lambert (fournit n, c, Xs, Ys)
     * @param lonMeridian longitude du méridien origine λc en radians
     * @param e           première excentricité de l'ellipsoïde
     * @return            point Lambert (X, Y) en mètres
     * @see <a href="https://geodesie.ign.fr/algorithmes-geodesiques">IGN — Algorithmes géodésiques (TransformationsCoordonneesGeodesiques §3.4, document retiré)</a>
     */
    public static ProjectedPoint geographicToLambert(double latitude, double longitude, LambertZone zone, double lonMeridian, double e) {

        double n = zone.n();
        double C = zone.c();
        double xs = zone.xs();
        double ys = zone.ys();

        double sinLat = sin(latitude);
        double eSinLat = (e * sinLat);
        double elt1 = (1 + sinLat) / (1 - sinLat);
        double elt2 = (1 + eSinLat) / (1 - eSinLat);

        double latIso = (1 / 2d) * log(elt1) - (e / 2d) * log(elt2);

        double R = C * exp(-(n * latIso));

        double LAMBDA = n * (longitude - lonMeridian);

        double x = xs + (R * sin(LAMBDA));
        double y = ys - (R * cos(LAMBDA));

        return new ProjectedPoint(x, y);
    }

    /**
     * ALG0004 — Transformation de coordonnées X, Y Lambert → λ, φ.
     * <p>
     * Transforme des coordonnées en projection conique conforme de Lambert en
     * coordonnées géographiques.
     * <pre>
     *   R     = √((X − Xs)² + (Y − Ys)²)
     *   γ     = arctan((X − Xs) / (Ys − Y))
     *   λ     = λc + γ / n
     *   L     = −(1/n) · ln(R / c)
     *   φ     = ALG0002(L, e, ε)
     * </pre>
     *
     * @param org         point projeté source (X, Y) en mètres
     * @param zone        zone Lambert (fournit n, c, Xs, Ys)
     * @param lonMeridian longitude du méridien origine λc en radians
     * @param e           première excentricité de l'ellipsoïde
     * @param eps         tolérance de convergence ε pour ALG0002
     * @return            point géographique (λ, φ, 0) — longitude et latitude en radians
     * @see <a href="https://data.geopf.fr/annexes/ressources/documentation/geodesie/algorithmes/NTG_71.pdf">NTG_71 ALG0004</a>
     */
    public static GeographicPoint lambertToGeographic(ProjectedPoint org, LambertZone zone, double lonMeridian, double e, double eps) {
        double n = zone.n();
        double C = zone.c();
        double xs = zone.xs();
        double ys = zone.ys();

        double x = org.x();
        double y = org.y();
        double R = sqrt((x - xs) * (x - xs) + (y - ys) * (y - ys));
        double gamma = atan((x - xs) / (ys - y));
        double lon = lonMeridian + gamma / n;
        double latIso = -1d / n * log(abs(R / C));
        double lat = latitudeFromLatitudeISO(latIso, e, eps);
        return new GeographicPoint(lon, lat, 0);
    }

    /**
     * ALG0021 — Calcul de la grande normale.
     * <p>
     * Calcule la grande normale {@code N} d'un ellipsoïde (a, e) en un point
     * de latitude {@code φ}.
     * <pre>
     *   N = a / √(1 − e²·sin²φ)
     * </pre>
     *
     * @param lat latitude φ en radians
     * @param a   demi-grand axe de l'ellipsoïde en mètres
     * @param e   première excentricité de l'ellipsoïde
     * @return    grande normale N en mètres
     * @see <a href="https://data.geopf.fr/annexes/ressources/documentation/geodesie/algorithmes/NTG_71.pdf">NTG_71 ALG0021</a>
     */
    static double lambertNormal(double lat, double a, double e) {

        return a / sqrt(1 - e * e * sin(lat) * sin(lat));
    }

    /**
     * ALG0019 — Paramètres de projection Lambert conique conforme dans le cas tangent.
     * <p>
     * Détermine les paramètres de calcul (n, c, λc, Xs, Ys) d'une projection Lambert
     * conique conforme tangente à partir des paramètres de définition usuels.
     * <pre>
     *   n  = sin(φ0)
     *   c  = k0 · N(φ0) · cotan(φ0) · exp(n · L(φ0, e))   [ALG0021 + ALG0001]
     *   Xs = X0
     *   Ys = Y0 + k0 · N(φ0) · cotan(φ0)
     *   λc = λ0
     * </pre>
     *
     * @param a       demi-grand axe de l'ellipsoïde en mètres
     * @param e       première excentricité de l'ellipsoïde
     * @param lambda0 longitude origine λ0 en radians par rapport au méridien origine
     * @param phi0    latitude origine φ0 en radians
     * @param k0      facteur d'échelle à l'origine
     * @param X0      abscisse en projection du point origine en mètres
     * @param Y0      ordonnée en projection du point origine en mètres
     * @return        {@code double[]} contenant {@code {n, c, lambdaC, Xs, Ys}}
     * @see <a href="https://data.geopf.fr/annexes/ressources/documentation/geodesie/algorithmes/NTG_71.pdf">NTG_71 ALG0019</a>
     */
    static double[] projectionParametersTangent(double a, double e, double lambda0, double phi0, double k0, double X0, double Y0) {
        double n  = sin(phi0);
        double N  = lambertNormal(phi0, a, e);
        double kNcotan = k0 * N * cos(phi0) / sin(phi0);
        double c  = kNcotan * exp(n * latitudeISOFromLat(phi0, e));
        double Xs = X0;
        double Ys = Y0 + kNcotan;
        return new double[]{ n, c, lambda0, Xs, Ys };
    }

    /**
     * ALG0054 — Paramètres de projection Lambert conique conforme dans le cas sécant.
     * <p>
     * Calcule les constantes (n, c, λc, Xs, Ys) d'une projection Lambert conique
     * conforme sécante à partir des paramètres de définition usuels.
     * <pre>
     *   n  = ln(N(φ2)·cos φ2 / N(φ1)·cos φ1) / (L(φ1,e) − L(φ2,e))   [ALG0021 + ALG0001]
     *   c  = N(φ1)·cos φ1 / n · exp(n · L(φ1,e))
     *   λc = λ0
     *   Xs = X0
     *   Ys = Y0                          si φ0 = π/2
     *   Ys = Y0 + c·exp(−n·L(φ0,e))     sinon
     * </pre>
     *
     * @param a       demi-grand axe de l'ellipsoïde en mètres
     * @param e       première excentricité de l'ellipsoïde
     * @param lambda0 longitude origine λ0 en radians par rapport au méridien origine
     * @param phi0    latitude origine φ0 en radians
     * @param phi1    latitude du 1er parallèle automécoïque φ1 en radians
     * @param phi2    latitude du 2ème parallèle automécoïque φ2 en radians
     * @param X0      abscisse en projection du point origine en mètres
     * @param Y0      ordonnée en projection du point origine en mètres
     * @return        {@code double[]} contenant {@code {n, c, lambdaC, Xs, Ys}}
     * @see <a href="https://data.geopf.fr/annexes/ressources/documentation/geodesie/algorithmes/NTG_71.pdf">NTG_71 ALG0054</a>
     */
    static double[] projectionParametersSecant(double a, double e, double lambda0, double phi0, double phi1, double phi2, double X0, double Y0) {
        double N1 = lambertNormal(phi1, a, e);
        double N2 = lambertNormal(phi2, a, e);
        double L1 = latitudeISOFromLat(phi1, e);
        double L2 = latitudeISOFromLat(phi2, e);
        double n  = log(N2 * cos(phi2) / (N1 * cos(phi1))) / (L1 - L2);
        double c  = N1 * cos(phi1) / n * exp(n * L1);
        double Xs = X0;
        double Ys = (abs(phi0 - M_PI_2) < 1e-12) ? Y0 : Y0 + c * exp(-n * latitudeISOFromLat(phi0, e));
        return new double[]{ n, c, lambda0, Xs, Ys };
    }

    /**
     * ALG0009 — Transformation de coordonnées géographiques → cartésiennes.
     * <p>
     * Transforme des coordonnées géographiques ellipsoïdales en coordonnées
     * cartésiennes tridimensionnelles.
     * <pre>
     *   N = ALG0021(φ, a, e)
     *   X = (N + he) · cos φ · cos λ
     *   Y = (N + he) · cos φ · sin λ
     *   Z = (N · (1 − e²) + he) · sin φ
     * </pre>
     *
     * @param lon longitude λ en radians par rapport au méridien origine
     * @param lat latitude φ en radians
     * @param he  hauteur au-dessus de l'ellipsoïde en mètres
     * @param a   demi-grand axe de l'ellipsoïde en mètres
     * @param e   première excentricité de l'ellipsoïde
     * @return    point cartésien (X, Y, Z) en mètres
     * @see <a href="https://data.geopf.fr/annexes/ressources/documentation/geodesie/algorithmes/NTG_80.pdf">NTG_80 ALG0009</a>
     */
    static CartesianPoint geographicToCartesian(double lon, double lat, double he, double a, double e) {
        double N = lambertNormal(lat, a, e);
        double common = (N + he) * cos(lat);

        return new CartesianPoint(
                common * cos(lon),
                common * sin(lon),
                (N * (1 - e * e) + he) * sin(lat));

    }

    /**
     * ALG0012 — Transformation de coordonnées cartésiennes → géographiques.
     * <p>
     * Transforme des coordonnées cartésiennes tridimensionnelles en coordonnées
     * géographiques ellipsoïdales par la méthode de Heiskanen-Moritz-Boucher.
     * <pre>
     *   λ  = meridien + arctan(Y / X)
     *   φ₀ = arctan(Z / (√(X²+Y²) · (1 − a·e² / √(X²+Y²+Z²))))
     *   φᵢ = arctan(Z / (√(X²+Y²) · (1 − a·e²·cos(φᵢ₋₁) / (√(X²+Y²) · √(1−e²·sin²(φᵢ₋₁))))))
     *         itéré jusqu'à |φᵢ − φᵢ₋₁| &lt; ε
     *   he = √(X²+Y²) / cos(φ) − a / √(1 − e²·sin²(φ))
     * </pre>
     *
     * @param org      point cartésien source {@link CartesianPoint} (X, Y, Z) en mètres
     * @param meridien longitude du méridien origine en radians
     * @param a        demi-grand axe de l'ellipsoïde en mètres
     * @param e        première excentricité de l'ellipsoïde
     * @param eps      tolérance de convergence ε en radians
     * @return         point géographique (λ, φ, he) — longitude et latitude en radians, hauteur en mètres
     * @see <a href="https://data.geopf.fr/annexes/ressources/documentation/geodesie/algorithmes/NTG_80.pdf">NTG_80 ALG0012</a>
     */
    static GeographicPoint cartesianToGeographic(CartesianPoint org, double meridien, double a, double e, double eps) {
        double x = org.x(), y = org.y(), z = org.z();

        double lon = meridien + atan(y / x);

        double module = sqrt(x * x + y * y);

        double phi0 = atan(z / (module * (1 - (a * e * e) / sqrt(x * x + y * y + z * z))));
        double phiI = atan(z / module / (1 - a * e * e * cos(phi0) / (module * sqrt(1 - e * e * sin(phi0) * sin(phi0)))));
        double delta = abs(phiI - phi0);
        while (delta > eps) {
            phi0 = phiI;
            phiI = atan(z / module / (1 - a * e * e * cos(phi0) / (module * sqrt(1 - e * e * sin(phi0) * sin(phi0)))));
            delta = abs(phiI - phi0);

        }

        double he = module / cos(phiI) - a / sqrt(1 - e * e * sin(phiI) * sin(phiI));

        return new GeographicPoint(lon, phiI, he);
    }

    /**
     * Convertit un point Lambert vers WGS84 (coordonnées géographiques en radians).
     * <p>
     * Pour Lambert 93 : inverse Lambert direct (ALG0004) avec les paramètres WGS84.<br>
     * Pour les autres zones NTF : Lambert NTF → géographique NTF (ALG0004) →
     * cartésien NTF (ALG0009) → translation (−168, −60, +320) → cartésien WGS84 →
     * géographique WGS84 (ALG0012).
     *
     * @param org  point source en coordonnées Lambert (X, Y) en mètres
     * @param zone zone Lambert source (LambertI … LambertIIExtended, Lambert93)
     * @return     point géographique WGS84 (λ, φ, he) — longitude et latitude en radians, hauteur en mètres
     * @see <a href="https://geodesie.ign.fr/algorithmes-geodesiques">IGN — Algorithmes géodésiques (transfo.pdf, document retiré)</a>
     */
    public static GeographicPoint convertToWGS84(ProjectedPoint org, LambertZone zone) {

        if (zone == Lambert93) {
            return lambertToGeographic(org, Lambert93, LON_MERID_IERS, E_WGS84, DEFAULT_EPS);
        } else {
            GeographicPoint pt1 = lambertToGeographic(org, zone, LON_MERID_PARIS, E_CLARK_IGN, DEFAULT_EPS);

            CartesianPoint pt2 = geographicToCartesian(pt1.longitude(), pt1.latitude(), pt1.height(), A_CLARK_IGN, E_CLARK_IGN)
                    .translate(TX_NTF_WGS84, TY_NTF_WGS84, TZ_NTF_WGS84);

            return cartesianToGeographic(pt2, LON_MERID_GREENWICH, A_WGS84, E_WGS84, DEFAULT_EPS);
        }
    }

    /**
     * Convertit un point WGS84 vers Lambert NTF (coordonnées en mètres).
     * <p>
     * Chaîne : géographique WGS84 → cartésien WGS84 (ALG0009) → translation
     * (+168, +60, −320) → cartésien NTF → géographique NTF (ALG0012) → Lambert NTF.
     * Lambert 93 n'est pas supporté.
     *
     * @param latitude  latitude φ WGS84 en radians
     * @param longitude longitude λ WGS84 en radians par rapport au méridien de Greenwich
     * @param zone      zone Lambert cible (LambertI … LambertIIExtended)
     * @return          point Lambert NTF (X, Y) en mètres
     * @throws UnsupportedOperationException si {@code zone} est Lambert93
     * @see <a href="https://geodesie.ign.fr/algorithmes-geodesiques">IGN — Algorithmes géodésiques (transfo.pdf, document retiré)</a>
     */
    public static ProjectedPoint convertToLambert(double latitude, double longitude, LambertZone zone) throws UnsupportedOperationException {

        if (zone == Lambert93) {
            throw new UnsupportedOperationException();
        } else {
            CartesianPoint pt1 = geographicToCartesian(longitude - LON_MERID_GREENWICH, latitude, 0, A_WGS84, E_WGS84)
                    .translate(-TX_NTF_WGS84, -TY_NTF_WGS84, -TZ_NTF_WGS84);

            GeographicPoint pt2 = cartesianToGeographic(pt1, LON_MERID_PARIS, A_WGS84, E_WGS84, DEFAULT_EPS);

            return geographicToLambert(pt2.latitude(), pt2.longitude(), zone, LON_MERID_PARIS, E_WGS84);
        }
    }

    /**
     * Convertit un point WGS84 vers Lambert NTF en utilisant ALG0003 pour la projection finale.
     * <p>
     * Identique à {@link #convertToLambert} mais utilise {@link #geographicToLambertAlg003}
     * au lieu de {@link #geographicToLambert} pour la dernière étape.
     * Lambert 93 n'est pas supporté.
     *
     * @param latitude  latitude φ WGS84 en radians
     * @param longitude longitude λ WGS84 en radians par rapport au méridien de Greenwich
     * @param zone      zone Lambert cible (LambertI … LambertIIExtended)
     * @return          point Lambert NTF (X, Y) en mètres
     * @throws UnsupportedOperationException si {@code zone} est Lambert93
     */
    public static ProjectedPoint convertToLambertByAlg003(double latitude, double longitude, LambertZone zone) throws UnsupportedOperationException {

        if (zone == Lambert93) {
            throw new UnsupportedOperationException();
        } else {
            CartesianPoint pt1 = geographicToCartesian(longitude - LON_MERID_GREENWICH, latitude, 0, A_WGS84, E_WGS84)
                    .translate(-TX_NTF_WGS84, -TY_NTF_WGS84, -TZ_NTF_WGS84);

            GeographicPoint pt2 = cartesianToGeographic(pt1, LON_MERID_PARIS, A_WGS84, E_WGS84, DEFAULT_EPS);

            return geographicToLambertAlg003(pt2.latitude(), pt2.longitude(), zone, LON_MERID_PARIS, E_WGS84);
        }
    }

    /**
     * Convertit un point Lambert vers WGS84 (coordonnées géographiques en radians).
     * Surcharge de commodité acceptant les coordonnées X, Y sous forme scalaire.
     *
     * @param x    abscisse Lambert en mètres
     * @param y    ordonnée Lambert en mètres
     * @param zone zone Lambert source (LambertI … LambertIIExtended, Lambert93)
     * @return     point géographique WGS84 (λ, φ, he) — longitude et latitude en radians, hauteur en mètres
     */
    public static GeographicPoint convertToWGS84(double x, double y, LambertZone zone) {

        return convertToWGS84(new ProjectedPoint(x, y), zone);
    }

    /**
     * Convertit un point Lambert vers WGS84 en degrés décimaux.
     *
     * @param x    abscisse Lambert en mètres
     * @param y    ordonnée Lambert en mètres
     * @param zone zone Lambert source (LambertI … LambertIIExtended, Lambert93)
     * @return     {@link Wgs84Point} avec longitude et latitude en degrés décimaux
     */
    public static Wgs84Point convertToWGS84Deg(double x, double y, LambertZone zone) {
        GeographicPoint rad = convertToWGS84(new ProjectedPoint(x, y), zone);
        return new Wgs84Point(toDegrees(rad.longitude()), toDegrees(rad.latitude()));
    }

}


