package geo.lambert;

public enum LambertZone {

    LambertI(0), LambertII(1), LambertIII(2), LambertIV(3), LambertIIExtended(4), Lambert93(5);

    private final int zone;

    private static final double[] LAMBERT_N  = {0.7604059656d, 0.7289686274d, 0.6959127966d, 0.6712679322d, 0.7289686274d, 0.7256077650d};
    private static final double[] LAMBERT_C  = {11603796.98d, 11745793.39d, 11947992.52d, 12136281.99d, 11745793.39d, 11754255.426d};
    private static final double[] LAMBERT_XS = {600000.0d, 600000.0d, 600000.0d, 234.358d, 600000.0d, 700000.0d};
    private static final double[] LAMBERT_YS = {5657616.674d, 6199695.768d, 6791905.085d, 7239161.542d, 8199695.768d, 12655612.050d};

    public static final double M_PI_2             = Math.PI / 2.0d;
    public static final double DEFAULT_EPS        = 1e-10d;
    public static final double E_CLARK_IGN        = 0.08248325676d;
    public static final double E_WGS84            = 0.08181919106d;
    public static final double A_CLARK_IGN        = 6378249.2d;
    public static final double A_WGS84            = 6378137.0d;
    public static final double LON_MERID_PARIS     = 0d;
    public static final double LON_MERID_GREENWICH = 0.04079234433d;
    public static final double LON_MERID_IERS      = 3.0d * Math.PI / 180.0d;

    /** Helmert translation parameters — NTF geocentric → WGS84 geocentric (metres). */
    public static final double TX_NTF_WGS84 = -168.0d;
    public static final double TY_NTF_WGS84 =  -60.0d;
    public static final double TZ_NTF_WGS84 =  320.0d;

    LambertZone(int zone) {
        this.zone = zone;
    }

    public double n()  { return LAMBERT_N[zone]; }
    public double c()  { return LAMBERT_C[zone]; }
    public double xs() { return LAMBERT_XS[zone]; }
    public double ys() { return LAMBERT_YS[zone]; }
}