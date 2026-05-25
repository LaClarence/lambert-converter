package geo.lambert;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Grille de transformation NTF ↔ RGF93 (formats NTv2 binaire {@code .gsb} et GeoTIFF GTG {@code .tif}).
 * <p>
 * Charge une grille de correction géodésique et fournit la transformation
 * bidirectionnelle NTF ↔ RGF93 par interpolation bilinéaire, conformément
 * aux notices IGN NT/G 88 et NT/G 111.
 * <p>
 * La grille de référence embarquée est <strong>fr_ign_ntf_r93.tif</strong>,
 * au format GeoTIFF GTG (DEFLATE + prédicteur virgule flottante), distribuée
 * par le réseau PROJ CDN sous licence ouverte. Elle couvre la France
 * métropolitaine (41°–52°N, 5,5°W–10°E) avec un pas de 0,1° et offre une
 * précision centimétrique.
 * <p>
 * Les deux formats sont supportés et détectés automatiquement :
 * <ul>
 *   <li>GeoTIFF GTG ({@code .tif}) — format moderne, bandes séparées, DEFLATE,
 *       prédicteur virgule flottante (code 3), décalages en secondes d'arc positifs
 *       vers l'Est sans négation ;</li>
 *   <li>NTv2 binaire ({@code .gsb}) — format historique IGN07_01, pour compatibilité.</li>
 * </ul>
 *
 * @see <a href="https://data.geopf.fr/annexes/ressources/documentation/geodesie/algorithmes/NTG_88.pdf">NTG_88</a>
 * @see <a href="https://data.geopf.fr/annexes/ressources/documentation/geodesie/algorithmes/NT111_V1_HARMEL_TransfoNTF-RGF93_FormatGrilleNTV2.pdf">NT/G 111</a>
 */
public final class Ntv2Grid {

    /** Limite sud de la grille (degrés, positif vers le Nord). */
    private final double sLat;
    /** Limite nord de la grille (degrés). */
    private final double nLat;
    /** Limite ouest de la grille (degrés, positif vers l'Est). */
    private final double wLon;
    /** Limite est de la grille (degrés, positif vers l'Est). */
    private final double eLon;
    /** Pas en latitude (degrés). */
    private final double latInc;
    /** Pas en longitude (degrés). */
    private final double lonInc;
    private final int nRows;
    private final int nCols;
    /** Correction en latitude par nœud (secondes d'arc, positif vers le Nord), ordre ligne-majeur S→N, O→E. */
    private final float[] latShifts;
    /** Correction en longitude par nœud (secondes d'arc, positif vers l'Est). */
    private final float[] lonShifts;

    // -------------------------------------------------------------------------
    // Constructeur
    // -------------------------------------------------------------------------

    private Ntv2Grid(double sLat, double nLat, double wLon, double eLon,
                     double latInc, double lonInc,
                     int nRows, int nCols,
                     float[] latShifts, float[] lonShifts) {
        this.sLat = sLat;
        this.nLat = nLat;
        this.wLon = wLon;
        this.eLon = eLon;
        this.latInc = latInc;
        this.lonInc = lonInc;
        this.nRows = nRows;
        this.nCols = nCols;
        this.latShifts = latShifts;
        this.lonShifts = lonShifts;
    }

    // -------------------------------------------------------------------------
    // Grille par défaut (chargement paresseux)
    // -------------------------------------------------------------------------

    /**
     * Holder thread-safe pour la grille embarquée {@code fr_ign_ntf_r93.tif}.
     * L'initialisation de la classe interne est garantie unique par la JVM
     * (initialisation-on-demand holder idiom).
     */
    private static final class DefaultHolder {
        static final Ntv2Grid INSTANCE = loadDefault();

        private static Ntv2Grid loadDefault() {
            try (var stream = Ntv2Grid.class.getResourceAsStream("fr_ign_ntf_r93.tif")) {
                if (stream == null) {
                    throw new IllegalStateException(
                            "Grille fr_ign_ntf_r93.tif introuvable dans le classpath. " +
                            "Vérifiez que le jar contient " +
                            "src/main/resources/geo/lambert/fr_ign_ntf_r93.tif.");
                }
                byte[] data = stream.readAllBytes();
                return parse(data);
            } catch (IOException e) {
                throw new IllegalStateException("Impossible de charger la grille embarquée.", e);
            }
        }
    }

    /**
     * Retourne la grille NTF → RGF93 embarquée ({@code fr_ign_ntf_r93.tif}).
     * <p>
     * La grille couvre la France métropolitaine (41°–52°N, 5,5°W–10°E) avec un
     * pas de 0,1° et offre une précision centimétrique sur l'ensemble du territoire.
     * <p>
     * Le chargement n'est effectué qu'au premier appel ; le résultat est ensuite
     * mis en cache pour la durée de vie de la JVM.
     *
     * @return instance unique de la grille embarquée
     * @throws IllegalStateException si la ressource est absente ou illisible
     */
    public static Ntv2Grid getDefault() {
        return DefaultHolder.INSTANCE;
    }

    // -------------------------------------------------------------------------
    // Factories
    // -------------------------------------------------------------------------

    /**
     * Charge une grille depuis un fichier. Les formats GeoTIFF GTG ({@code .tif})
     * et NTv2 binaire ({@code .gsb}) sont détectés automatiquement par les octets
     * magiques du fichier.
     *
     * @param path chemin vers le fichier de grille
     * @return grille prête à l'emploi
     * @throws IOException              en cas d'erreur de lecture
     * @throws IllegalArgumentException si le fichier ne respecte pas un format connu
     */
    public static Ntv2Grid load(Path path) throws IOException {
        return parse(Files.readAllBytes(path));
    }

    /**
     * Détecte le format (GeoTIFF ou NTv2 GSB) et délègue au parseur approprié.
     */
    private static Ntv2Grid parse(byte[] data) {
        if (data.length < 4) {
            throw new IllegalArgumentException(
                    "Données trop courtes pour être un fichier de grille valide.");
        }
        // TIFF magic bytes: II (little-endian) ou MM (big-endian)
        if ((data[0] == 'I' && data[1] == 'I') || (data[0] == 'M' && data[1] == 'M')) {
            return parseTiff(data);
        }
        return parseNtv2Gsb(data);
    }

    // -------------------------------------------------------------------------
    // Parseur GeoTIFF GTG
    // -------------------------------------------------------------------------

    /**
     * Charge une grille au format GeoTIFF GTG.
     * <p>
     * Le format attendu est :
     * <ul>
     *   <li>Compression DEFLATE (code 8), format zlib ;</li>
     *   <li>Prédicteur virgule flottante (tag 317, valeur 3) ;</li>
     *   <li>Configuration planaire séparée (tag 284, valeur 2) — une bande par strip ;</li>
     *   <li>Bande 0 = décalages latitude (s d'arc, positif Nord) ;</li>
     *   <li>Bande 1 = décalages longitude (s d'arc, positif Est, sans négation).</li>
     * </ul>
     * Le pixel (0, 0) correspond au coin Nord-Ouest ; les lignes sont donc inversées
     * après décodage pour respecter la convention interne (ligne 0 = Sud).
     */
    private static Ntv2Grid parseTiff(byte[] data) {
        ByteOrder order = (data[0] == 'I') ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
        ByteBuffer buf  = ByteBuffer.wrap(data).order(order);

        // En-tête TIFF : 2 octets ordre, 2 octets magic (42), 4 octets offset IFD
        buf.position(4);
        int ifdOffset = buf.getInt();
        buf.position(ifdOffset);

        int numEntries = buf.getShort() & 0xFFFF;

        int    width          = -1;
        int    height         = -1;
        int    compression    = 1;   // 1 = aucune compression
        int    predictor      = 1;   // 1 = aucun prédicteur
        int[]  stripOffsets   = null;
        int[]  stripByteCounts= null;
        double[] pixelScale   = null;
        double[] tiepoint     = null;

        for (int e = 0; e < numEntries; e++) {
            int tag   = buf.getShort() & 0xFFFF;
            int type  = buf.getShort() & 0xFFFF;
            int count = buf.getInt();
            int vPos  = buf.position(); // champ de 4 octets valeur/offset

            switch (tag) {
                case 256   -> width           = tiffReadIntScalar(data, type, vPos, order);
                case 257   -> height          = tiffReadIntScalar(data, type, vPos, order);
                case 259   -> compression     = tiffReadIntScalar(data, type, vPos, order);
                case 317   -> predictor       = tiffReadIntScalar(data, type, vPos, order);
                case 273   -> stripOffsets    = tiffReadIntArray(data, type, count, vPos, order);
                case 279   -> stripByteCounts = tiffReadIntArray(data, type, count, vPos, order);
                case 33550 -> pixelScale      = tiffReadDoubleArray(data, count, vPos, order);
                case 33922 -> tiepoint        = tiffReadDoubleArray(data, count, vPos, order);
                default    -> { /* tag non requis — ignoré */ }
            }

            buf.position(vPos + 4); // avance après le champ valeur/offset
        }

        if (width < 0 || height < 0 || stripOffsets == null || stripByteCounts == null
                || pixelScale == null || tiepoint == null) {
            throw new IllegalArgumentException(
                    "Fichier GeoTIFF incomplet : un ou plusieurs tags requis sont manquants.");
        }
        if (stripOffsets.length < 2 || stripByteCounts.length < 2) {
            throw new IllegalArgumentException(
                    "GeoTIFF GTG : au moins 2 bandes (latitude / longitude) requises.");
        }

        // Géo-référencement depuis ModelPixelScaleTag et ModelTiepointTag
        // Tiepoint : [pixelI, pixelJ, pixelK, geoX, geoY, geoZ] × n
        // Pixel (0, 0) → coin Nord-Ouest = (westLon, northLat)
        double westLon  = tiepoint[3];    // longitude (positif Est)
        double northLat = tiepoint[4];    // latitude  (positif Nord)
        double lonInc   = pixelScale[0];  // degrés par pixel vers l'Est
        double latInc   = pixelScale[1];  // degrés par pixel vers le Nord (valeur positive)

        int nCols = width;
        int nRows = height;
        double eLon = westLon  + (nCols - 1) * lonInc;
        double sLat = northLat - (nRows - 1) * latInc;

        // Décompression et décodage des bandes 0 (lat) et 1 (lon)
        float[] latShifts = tiffDecompressStrip(data,
                stripOffsets[0], stripByteCounts[0], nCols, nRows, compression, predictor);
        float[] lonShifts = tiffDecompressStrip(data,
                stripOffsets[1], stripByteCounts[1], nCols, nRows, compression, predictor);

        // Inversion des lignes : GeoTIFF ligne 0 = Nord ; convention interne ligne 0 = Sud
        tiffFlipRows(latShifts, nCols, nRows);
        tiffFlipRows(lonShifts, nCols, nRows);

        // Les décalages longitude GTG sont déjà positifs vers l'Est (pas de négation,
        // contrairement au format NTv2 GSB où ils sont positifs vers l'Ouest).
        return new Ntv2Grid(sLat, northLat, westLon, eLon, latInc, lonInc, nRows, nCols,
                latShifts, lonShifts);
    }

    /**
     * Lit un scalaire entier SHORT (type 3) ou LONG (type 4) depuis le champ
     * valeur/offset d'une entrée IFD TIFF.
     */
    private static int tiffReadIntScalar(byte[] data, int type, int vPos, ByteOrder order) {
        ByteBuffer b = ByteBuffer.wrap(data, vPos, 4).order(order);
        return (type == 3) ? (b.getShort() & 0xFFFF) : b.getInt();
    }

    /**
     * Lit un tableau d'entiers SHORT (type 3) ou LONG (type 4).
     * Si la taille totale dépasse 4 octets, le champ contient un offset vers les données.
     */
    private static int[] tiffReadIntArray(byte[] data, int type, int count, int vPos,
                                          ByteOrder order) {
        int typeSize = (type == 3) ? 2 : 4;
        int[] result = new int[count];
        if ((long) count * typeSize <= 4) {
            // Valeurs inline dans le champ
            ByteBuffer b = ByteBuffer.wrap(data, vPos, 4).order(order);
            for (int i = 0; i < count; i++) {
                result[i] = (type == 3) ? (b.getShort() & 0xFFFF) : b.getInt();
            }
        } else {
            // Champ = offset vers les données
            int offset = ByteBuffer.wrap(data, vPos, 4).order(order).getInt();
            ByteBuffer b = ByteBuffer.wrap(data, offset, count * typeSize).order(order);
            for (int i = 0; i < count; i++) {
                result[i] = (type == 3) ? (b.getShort() & 0xFFFF) : b.getInt();
            }
        }
        return result;
    }

    /**
     * Lit un tableau de DOUBLE (type 12) depuis un offset externe dans l'IFD TIFF.
     */
    private static double[] tiffReadDoubleArray(byte[] data, int count, int vPos,
                                                ByteOrder order) {
        int offset = ByteBuffer.wrap(data, vPos, 4).order(order).getInt();
        ByteBuffer b = ByteBuffer.wrap(data, offset, count * 8).order(order);
        double[] result = new double[count];
        for (int i = 0; i < count; i++) result[i] = b.getDouble();
        return result;
    }

    /**
     * Décompresse et décode une bande GeoTIFF (DEFLATE + prédicteur virgule flottante).
     *
     * @param data       données brutes du fichier TIFF
     * @param offset     position du strip compressé dans {@code data}
     * @param byteCount  taille du strip compressé en octets
     * @param nCols      nombre de colonnes de la grille
     * @param nRows      nombre de lignes de la grille
     * @param compression code de compression TIFF (8 = Adobe DEFLATE)
     * @param predictor  code de prédicteur (1 = aucun, 3 = virgule flottante)
     * @return tableau de {@code nRows × nCols} valeurs {@code float} (ordre Nord→Sud)
     */
    private static float[] tiffDecompressStrip(byte[] data, int offset, int byteCount,
                                               int nCols, int nRows,
                                               int compression, int predictor) {
        // Étape 1 : décompression DEFLATE (format zlib, code 8 ou 32946)
        byte[] raw;
        if (compression == 8 || compression == 32946) {
            Inflater inf = new Inflater(false); // nowrap=false → format zlib avec en-tête
            inf.setInput(data, offset, byteCount);
            ByteArrayOutputStream baos = new ByteArrayOutputStream(nCols * nRows * 4);
            byte[] tmp = new byte[65536];
            try {
                while (!inf.finished()) {
                    int n = inf.inflate(tmp);
                    if (n > 0) baos.write(tmp, 0, n);
                    else if (inf.needsInput()) break; // ne devrait pas arriver avec entrée complète
                }
            } catch (DataFormatException e) {
                throw new IllegalArgumentException(
                        "Erreur de décompression DEFLATE dans le GeoTIFF : " + e.getMessage(), e);
            } finally {
                inf.end();
            }
            raw = baos.toByteArray();
        } else {
            // Aucune compression — copie directe
            raw = Arrays.copyOfRange(data, offset, offset + byteCount);
        }

        // Étape 2 : inversion du prédicteur virgule flottante (code 3)
        if (predictor == 3) {
            raw = tiffReverseFloatPredictor(raw, nCols, nRows);
        }

        // Étape 3 : interprétation des octets en float32 big-endian
        ByteBuffer fb = ByteBuffer.wrap(raw); // big-endian par défaut
        float[] floats = new float[nCols * nRows];
        for (int i = 0; i < floats.length; i++) floats[i] = fb.getFloat();
        return floats;
    }

    /**
     * Inverse le prédicteur virgule flottante TIFF (code 3).
     * <p>
     * Chaque ligne de {@code nCols} floats est codée en 4 pistes ({@code lanes})
     * de {@code nCols} octets chacune, ordonnées MSB → octet 2 → octet 1 → LSB,
     * avec différenciation horizontale (delta) par piste. Le décodage :
     * <ol>
     *   <li>annule la différenciation horizontale (somme cumulée) pour chaque piste ;</li>
     *   <li>ré-entrelace les 4 pistes pour reconstituer les floats en big-endian IEEE 754.</li>
     * </ol>
     *
     * @param raw   données décompressées : {@code nRows × nCols × 4} octets
     * @param nCols nombre de colonnes
     * @param nRows nombre de lignes
     * @return tableau d'octets représentant les floats en big-endian
     */
    private static byte[] tiffReverseFloatPredictor(byte[] raw, int nCols, int nRows) {
        byte[] result  = new byte[raw.length];
        int    rowSize = nCols * 4; // octets par ligne (4 pistes × nCols octets)

        for (int row = 0; row < nRows; row++) {
            int rowBase = row * rowSize;

            // --- 1) Annulation de la différenciation horizontale par piste ---
            // Les 4 pistes sont stockées séquentiellement dans la ligne :
            //   piste 0 [rowBase .. rowBase+nCols-1]            — MSB de chaque float
            //   piste 1 [rowBase+nCols .. rowBase+2*nCols-1]    — octet 2
            //   piste 2 [rowBase+2*nCols .. rowBase+3*nCols-1]  — octet 1
            //   piste 3 [rowBase+3*nCols .. rowBase+4*nCols-1]  — LSB
            byte[] undiffed = new byte[rowSize];
            System.arraycopy(raw, rowBase, undiffed, 0, rowSize);
            for (int lane = 0; lane < 4; lane++) {
                int laneBase = lane * nCols;
                byte prev = 0;
                for (int i = 0; i < nCols; i++) {
                    byte cur = (byte) (undiffed[laneBase + i] + prev);
                    undiffed[laneBase + i] = cur;
                    prev = cur;
                }
            }

            // --- 2) Ré-entrelacement : float i → [lane0[i], lane1[i], lane2[i], lane3[i]] ---
            // Résultat en big-endian IEEE 754 (MSB en premier).
            for (int i = 0; i < nCols; i++) {
                result[rowBase + i * 4    ] = undiffed[             i]; // MSB
                result[rowBase + i * 4 + 1] = undiffed[    nCols + i]; // octet 2
                result[rowBase + i * 4 + 2] = undiffed[2 * nCols + i]; // octet 1
                result[rowBase + i * 4 + 3] = undiffed[3 * nCols + i]; // LSB
            }
        }
        return result;
    }

    /**
     * Inverse l'ordre des lignes d'une grille plane.
     * Utilisé pour passer de l'ordre GeoTIFF (ligne 0 = Nord) à la convention
     * interne (ligne 0 = Sud), et réciproquement.
     */
    private static void tiffFlipRows(float[] arr, int nCols, int nRows) {
        float[] tmp = new float[nCols];
        for (int i = 0, j = nRows - 1; i < j; i++, j--) {
            System.arraycopy(arr, i * nCols, tmp, 0, nCols);
            System.arraycopy(arr, j * nCols, arr, i * nCols, nCols);
            System.arraycopy(tmp, 0, arr, j * nCols, nCols);
        }
    }

    // -------------------------------------------------------------------------
    // Parseur NTv2 binaire (format .gsb)
    // -------------------------------------------------------------------------

    /**
     * Charge une grille au format NTv2 binaire ({@code .gsb}).
     * <p>
     * Seul le premier sous-grille est lu (la grille France métropolitaine
     * n'en comporte qu'un). Les ordres d'octets little-endian et big-endian
     * sont tous deux supportés (détection automatique par le champ NUM_OREC).
     * <p>
     * <b>Convention NTv2 :</b> les longitudes sont stockées en secondes d'arc
     * <em>positives vers l'Ouest</em>. Cette méthode les convertit immédiatement
     * en positives vers l'Est lors de la lecture.
     *
     * @throws IllegalArgumentException si les données ne respectent pas le format NTv2
     */
    private static Ntv2Grid parseNtv2Gsb(byte[] data) {
        if (data.length < 176) {
            throw new IllegalArgumentException("Données trop courtes pour être un NTv2 valide.");
        }

        // Détection de l'ordre des octets : le champ NUM_OREC (bytes 8-11) vaut 11.
        ByteBuffer probe = ByteBuffer.wrap(data, 8, 4).order(ByteOrder.LITTLE_ENDIAN);
        ByteOrder order  = (probe.getInt() == 11) ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
        ByteBuffer buf   = ByteBuffer.wrap(data).order(order);

        // --- En-tête général (11 enregistrements × 16 octets) ---
        for (int i = 0; i < 11; i++) {
            buf.position(buf.position() + 16);
        }

        // --- En-tête du premier sous-grille (11 enregistrements × 16 octets) ---
        double sLatSec = 0, nLatSec = 0, wLonSecPW = 0, eLonSecPW = 0;
        double latIncSec = 0, lonIncSec = 0;
        int gsCount = 0;

        for (int i = 0; i < 11; i++) {
            String tag = ntv2ReadTag(buf);
            switch (tag) {
                case "S_LAT"    -> sLatSec    = buf.getDouble();
                case "N_LAT"    -> nLatSec    = buf.getDouble();
                case "W_LONG"   -> wLonSecPW  = buf.getDouble(); // positif vers l'Ouest
                case "E_LONG"   -> eLonSecPW  = buf.getDouble(); // positif vers l'Ouest
                case "LAT_INC"  -> latIncSec  = buf.getDouble();
                case "LONG_INC" -> lonIncSec  = buf.getDouble();
                case "GS_COUNT" -> { gsCount  = buf.getInt(); buf.getInt(); } // int32 + remplissage
                default         -> buf.position(buf.position() + 8);
            }
        }

        if (gsCount <= 0) {
            throw new IllegalArgumentException("GS_COUNT invalide dans le sous-grille NTv2.");
        }

        // Conversion secondes positif-Ouest → degrés positif-Est
        double sLat   =  sLatSec   / 3600.0;
        double nLat   =  nLatSec   / 3600.0;
        double wLon   = -wLonSecPW / 3600.0;
        double eLon   = -eLonSecPW / 3600.0;
        double latInc =  latIncSec / 3600.0;
        double lonInc =  lonIncSec / 3600.0;

        int nRows = (int) Math.round((nLat - sLat) / latInc) + 1;
        int nCols = (int) Math.round((eLon - wLon) / lonInc) + 1;

        if (nRows * nCols != gsCount) {
            throw new IllegalArgumentException(
                    String.format("Incohérence GS_COUNT : attendu %d (%d lignes × %d cols), lu %d",
                            nRows * nCols, nRows, nCols, gsCount));
        }

        // --- Données de la grille : gsCount × 4 floats (16 octets par nœud) ---
        // [0] correction latitude   (secondes, positif Nord)
        // [1] correction longitude  (secondes, positif Ouest → négation)
        // [2] précision latitude    (ignorée)
        // [3] précision longitude   (ignorée)
        float[] latShifts = new float[gsCount];
        float[] lonShifts = new float[gsCount];

        for (int i = 0; i < gsCount; i++) {
            latShifts[i] =  buf.getFloat();
            lonShifts[i] = -buf.getFloat(); // positif-Ouest → positif-Est
            buf.getFloat();                 // précision latitude  — ignorée
            buf.getFloat();                 // précision longitude — ignorée
        }

        return new Ntv2Grid(sLat, nLat, wLon, eLon, latInc, lonInc, nRows, nCols,
                latShifts, lonShifts);
    }

    private static String ntv2ReadTag(ByteBuffer buf) {
        byte[] b = new byte[8];
        buf.get(b);
        return new String(b, StandardCharsets.US_ASCII).trim();
    }

    // -------------------------------------------------------------------------
    // Fabrique pour tests
    // -------------------------------------------------------------------------

    /**
     * Fabrique une grille à partir de tableaux pré-calculés (usage tests / embarqué).
     *
     * @param sLat      limite sud (degrés, positif Nord)
     * @param nLat      limite nord (degrés)
     * @param wLon      limite ouest (degrés, positif Est)
     * @param eLon      limite est  (degrés, positif Est)
     * @param latInc    pas en latitude  (degrés)
     * @param lonInc    pas en longitude (degrés)
     * @param latShifts corrections latitude  par nœud (secondes, positif Nord), ligne-majeur S→N, O→E
     * @param lonShifts corrections longitude par nœud (secondes, positif Est)
     * @return grille prête à l'emploi
     */
    public static Ntv2Grid of(double sLat, double nLat, double wLon, double eLon,
                               double latInc, double lonInc,
                               float[] latShifts, float[] lonShifts) {
        int nRows    = (int) Math.round((nLat - sLat) / latInc) + 1;
        int nCols    = (int) Math.round((eLon - wLon) / lonInc) + 1;
        int expected = nRows * nCols;
        if (latShifts.length != expected || lonShifts.length != expected) {
            throw new IllegalArgumentException(
                    String.format("Taille tableaux incorrecte : %d×%d=%d nœuds attendus, " +
                                  "latShifts=%d, lonShifts=%d",
                                  nRows, nCols, expected, latShifts.length, lonShifts.length));
        }
        return new Ntv2Grid(sLat, nLat, wLon, eLon, latInc, lonInc, nRows, nCols,
                latShifts.clone(), lonShifts.clone());
    }

    // -------------------------------------------------------------------------
    // API publique
    // -------------------------------------------------------------------------

    /**
     * Indique si le point ({@code latDeg}, {@code lonDeg}) est couvert par la grille.
     *
     * @param latDeg latitude en degrés (positif Nord)
     * @param lonDeg longitude en degrés (positif Est, méridien de Greenwich)
     * @return {@code true} si le point est dans les limites de la grille
     */
    public boolean covers(double latDeg, double lonDeg) {
        return latDeg >= sLat && latDeg <= nLat
            && lonDeg >= wLon && lonDeg <= eLon;
    }

    /**
     * Transforme un point NTF géographique en RGF93 géographique par interpolation
     * bilinéaire dans la grille.
     *
     * @param latDeg latitude NTF en degrés décimaux (positif Nord)
     * @param lonDeg longitude NTF en degrés décimaux (positif Est, méridien Greenwich)
     * @return {@link Rgf93Point} en degrés décimaux
     * @throws IllegalArgumentException si le point est hors couverture de la grille
     */
    public Rgf93Point ntfToRgf93(double latDeg, double lonDeg) {
        double[] d = interpolate(latDeg, lonDeg);
        return new Rgf93Point(lonDeg + d[1] / 3600.0, latDeg + d[0] / 3600.0);
    }

    /**
     * Transforme un point RGF93 géographique en NTF géographique par inversion
     * itérative de la grille (convergence en ≤ 3 itérations pour la France métro.).
     *
     * @param latDeg latitude RGF93 en degrés décimaux (positif Nord)
     * @param lonDeg longitude RGF93 en degrés décimaux (positif Est, méridien Greenwich)
     * @return tableau {@code [latNtf, lonNtf]} en degrés décimaux
     * @throws IllegalArgumentException si le point est hors couverture de la grille
     */
    public double[] rgf93ToNtf(double latDeg, double lonDeg) {
        // Amorçage : NTF ≈ RGF93 (décalage < 3 m en France)
        double latNtf = latDeg;
        double lonNtf = lonDeg;
        for (int i = 0; i < 10; i++) {
            if (!covers(latNtf, lonNtf)) break;
            double[] d = interpolate(latNtf, lonNtf);
            double latNew = latDeg - d[0] / 3600.0;
            double lonNew = lonDeg - d[1] / 3600.0;
            if (Math.abs(latNew - latNtf) < 1e-12 && Math.abs(lonNew - lonNtf) < 1e-12) {
                latNtf = latNew;
                lonNtf = lonNew;
                break;
            }
            latNtf = latNew;
            lonNtf = lonNew;
        }
        return new double[]{ latNtf, lonNtf };
    }

    // -------------------------------------------------------------------------
    // Implémentation interne
    // -------------------------------------------------------------------------

    /**
     * Interpolation bilinéaire des corrections au point ({@code latDeg}, {@code lonDeg}).
     *
     * @return {@code [dLat, dLon]} — corrections en secondes d'arc (positif Nord / Est)
     * @throws IllegalArgumentException si le point est hors grille
     */
    double[] interpolate(double latDeg, double lonDeg) {
        if (!covers(latDeg, lonDeg)) {
            throw new IllegalArgumentException(
                    String.format("Point (lat=%.6f°, lon=%.6f°) hors grille " +
                                  "[%.2f°–%.2f°N, %.2f°–%.2f°E]",
                                  latDeg, lonDeg, sLat, nLat, wLon, eLon));
        }

        double colF = (lonDeg - wLon) / lonInc;
        double rowF = (latDeg - sLat) / latInc;

        int col0 = (int) colF;
        int row0 = (int) rowF;
        // Blocage sur la frontière Est/Nord pour éviter un dépassement de tableau
        if (col0 >= nCols - 1) col0 = nCols - 2;
        if (row0 >= nRows - 1) row0 = nRows - 2;

        double t = colF - col0; // fraction vers l'Est   [0, 1]
        double u = rowF - row0; // fraction vers le Nord [0, 1]

        // Indices des 4 nœuds voisins (ligne-majeur : index = ligne × nCols + col)
        int sw = row0       * nCols + col0;     // Sud-Ouest
        int se = row0       * nCols + col0 + 1; // Sud-Est
        int nw = (row0 + 1) * nCols + col0;     // Nord-Ouest
        int ne = (row0 + 1) * nCols + col0 + 1; // Nord-Est

        double dLat = (1 - t) * (1 - u) * latShifts[sw]
                    +      t  * (1 - u) * latShifts[se]
                    + (1 - t) *      u  * latShifts[nw]
                    +      t  *      u  * latShifts[ne];

        double dLon = (1 - t) * (1 - u) * lonShifts[sw]
                    +      t  * (1 - u) * lonShifts[se]
                    + (1 - t) *      u  * lonShifts[nw]
                    +      t  *      u  * lonShifts[ne];

        return new double[]{ dLat, dLon };
    }

    // -------------------------------------------------------------------------
    // Accesseurs
    // -------------------------------------------------------------------------

    /** Limite sud de la grille (degrés). */
    public double southLatitude()  { return sLat; }
    /** Limite nord de la grille (degrés). */
    public double northLatitude()  { return nLat; }
    /** Limite ouest de la grille (degrés, positif Est). */
    public double westLongitude()  { return wLon; }
    /** Limite est de la grille (degrés, positif Est). */
    public double eastLongitude()  { return eLon; }
    /** Nombre de lignes. */
    public int rowCount()          { return nRows; }
    /** Nombre de colonnes. */
    public int columnCount()       { return nCols; }
}
