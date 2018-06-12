package org.locationtech.proj4j.proj;

import org.locationtech.proj4j.ProjCoordinate;
import org.locationtech.proj4j.datum.Ellipsoid;

/**
 * 
 * @see https://github.com/OSGeo/proj.4/issues/316
 * @see https://github.com/OSGeo/proj.4/blob/master/src/proj_etmerc.c
 */
public class ExtendedTransverseMercatorProjection extends CylindricalProjection {
    
    private static final long serialVersionUID = 1L;
    
    double    Qn;    /* Merid. quad., scaled to the projection */ 
    double    Zb;    /* Radius vector in polar coord. systems  */ 
    double[]    cgb = new double[6]; /* Constants for Gauss -> Geo lat */ 
    double[]    cbg = new double[6]; /* Constants for Geo lat -> Gauss */ 
    double[]    utg = new double[6]; /* Constants for transv. merc. -> geo */ 
    double[]    gtu = new double[6]; /* Constants for geo -> transv. merc. */

    private static final int PROJ_ETMERC_ORDER = 6;
    private static final double HUGE_VAL = Double.POSITIVE_INFINITY;
    
    public ExtendedTransverseMercatorProjection() {
        ellipsoid = Ellipsoid.GRS80;
        projectionLatitude = Math.toRadians(0);
        projectionLongitude = Math.toRadians(0);
        minLongitude = Math.toRadians(-90);
        maxLongitude = Math.toRadians(90);
        initialize();
    }
    
    public ExtendedTransverseMercatorProjection(Ellipsoid ellipsoid, double lon_0, double lat_0, double k, double x_0, double y_0) {
        setEllipsoid(ellipsoid);
        projectionLongitude = lon_0;
        projectionLatitude = lat_0;
        scaleFactor = k;
        falseEasting = x_0;
        falseNorthing = y_0;
        initialize();
    }
    
    static double log1py(double x) {              /* Compute log(1+x) accurately */
        double y = 1 + x;
        double z = y - 1;
        /* Here's the explanation for this magic: y = 1 + z, exactly, and z
         * approx x, thus log(y)/z (which is nearly constant near z = 0) returns
         * a good approximation to the true log(1 + x)/x.  The multiplication x *
         * (log(y)/z) introduces little additional error. */
        return z == 0 ? x : x * Math.log(y) / z;
    }
    
    static double asinhy(double x) {              /* Compute asinh(x) accurately */
        double y = Math.abs(x);         /* Enforce odd parity */
        y = log1py(y * (1 + y/(Math.hypot(1.0, y) + 1)));
        return x < 0 ? -y : y;
    }
 
    static double gatg(double[] p1, int len_p1, double B) {
        double h = 0, h1, h2 = 0;

        double cos_2B = 2*Math.cos(2*B);
     
        int p1i;
        for (p1i = len_p1, h1 = p1[--p1i]; p1i > 0; h2 = h1, h1 = h) {
            h = -h2 + cos_2B*h1 + p1[--p1i];
        }
        
        return (B + h*Math.sin(2*B));
    }
    
    static double clenS(double[] a, int size, double arg_r, double arg_i, double[] R, double[] I) {
        double      hr, hr1, hr2, hi, hi1, hi2;

        /* arguments */
        int ai = size;
        double sin_arg_r  = Math.sin(arg_r);
        double cos_arg_r  = Math.cos(arg_r);
        double sinh_arg_i = Math.sinh(arg_i);
        double cosh_arg_i = Math.cosh(arg_i);
        double r          =  2*cos_arg_r*cosh_arg_i;
        double i          = -2*sin_arg_r*sinh_arg_i;

        /* summation loop */
        for (hi1 = hr1 = hi = 0, hr = a[--ai]; ai > 0;) {
            hr2 = hr1;
            hi2 = hi1;
            hr1 = hr;
            hi1 = hi;
            hr  = -hr2 + r*hr1 - i*hi1 + a[--ai];
            hi  = -hi2 + i*hr1 + r*hi1;
        }

        r   = sin_arg_r*cosh_arg_i;
        i   = cos_arg_r*sinh_arg_i;
        R[0]  = r*hr - i*hi;
        I[0]  = r*hi + i*hr;
        return R[0];
    }

    /* Real Clenshaw summation */
    static double clens(double[] a, int size, double arg_r) {
        double      hr, hr1, hr2;

        int ai = size;
        double cos_arg_r  = Math.cos(arg_r);
        double r          =  2*cos_arg_r;

        /* summation loop */
        for (hr1 = 0, hr = a[--ai]; ai > 0;) {
            hr2 = hr1;
            hr1 = hr;
            hr  = -hr2 + r*hr1 + a[--ai];
        }
        return Math.sin (arg_r)*hr;
    }
    
    public ProjCoordinate project(double lplam, double lpphi, ProjCoordinate xy) {
        double sin_Cn, cos_Cn, cos_Ce, sin_Ce;
        double[] dCn = new double[1];
        double[] dCe = new double[1];
        double Cn = lpphi, Ce = lplam;

        /* ell. LAT, LNG -> Gaussian LAT, LNG */
        Cn  = gatg (cbg, PROJ_ETMERC_ORDER, Cn);
        /* Gaussian LAT, LNG -> compl. sph. LAT */
        sin_Cn = Math.sin (Cn);
        cos_Cn = Math.cos (Cn);
        sin_Ce = Math.sin (Ce);
        cos_Ce = Math.cos (Ce);

        Cn     = Math.atan2 (sin_Cn, cos_Ce*cos_Cn);
        Ce     = Math.atan2 (sin_Ce*cos_Cn,  Math.hypot (sin_Cn, cos_Cn*cos_Ce));

        /* compl. sph. N, E -> ell. norm. N, E */
        Ce  = asinhy ( Math.tan (Ce) );     /* Replaces: Ce  = log(tan(FORTPI + Ce*0.5)); */
        Cn += clenS (gtu, PROJ_ETMERC_ORDER, 2*Cn, 2*Ce, dCn, dCe);
        Ce += dCe[0];
        if (Math.abs (Ce) <= 2.623395162778) {
            xy.y  = Qn * Cn + Zb;  /* Northing */
            xy.x  = Qn * Ce;          /* Easting  */
        } else
            xy.x = xy.y = HUGE_VAL;
        return xy;
    }

    public ProjCoordinate projectInverse(double x, double y, ProjCoordinate out) {
        double sin_Cn, cos_Cn, cos_Ce, sin_Ce;
        double[] dCn = new double[1];
        double[] dCe = new double[1];
        double Cn = y, Ce = x;

        /* normalize N, E */
        Cn = (Cn - Zb)/Qn;
        Ce = Ce/Qn;

        if (Math.abs(Ce) <= 2.623395162778) { /* 150 degrees */
            /* norm. N, E -> compl. sph. LAT, LNG */
            Cn += clenS(utg, PROJ_ETMERC_ORDER, 2*Cn, 2*Ce, dCn, dCe);
            Ce += dCe[0];
            Ce = Math.atan (Math.sinh (Ce)); /* Replaces: Ce = 2*(atan(exp(Ce)) - FORTPI); */
            /* compl. sph. LAT -> Gaussian LAT, LNG */
            sin_Cn = Math.sin (Cn);
            cos_Cn = Math.cos (Cn);
            sin_Ce = Math.sin (Ce);
            cos_Ce = Math.cos (Ce);
            Ce     = Math.atan2 (sin_Ce, cos_Ce*cos_Cn);
            Cn     = Math.atan2 (sin_Cn*cos_Ce,  Math.hypot (sin_Ce, cos_Ce*cos_Cn));
            /* Gaussian LAT, LNG -> ell. LAT, LNG */
            out.y = gatg (cgb,  PROJ_ETMERC_ORDER, Cn);
            out.x = Ce;
        }
        
        return out;
    }
    
    public void setUTMZone(int zone) {
        zone--;
        projectionLongitude = (zone + .5) * Math.PI / 30. - Math.PI;
        projectionLatitude = 0.0;
        scaleFactor = 0.9996;
        falseEasting = 500000;
        falseNorthing = isSouth ? 10000000.0 : 0.0;
        initialize();
    }
    
    public void initialize() {
        super.initialize();
        
        double f, n, np, Z;

        if (es <= 0) {
            //return pj_default_destructor(P, PJD_ERR_ELLIPSOID_USE_REQUIRED);
            return;
        }

        /* flattening */
        f = es / (1 + Math.sqrt (1 -  es)); /* Replaces: f = 1 - sqrt(1-P->es); */

        /* third flattening */
        np = n = f/(2 - f);

        /* COEF. OF TRIG SERIES GEO <-> GAUSS */
        /* cgb := Gaussian -> Geodetic, KW p190 - 191 (61) - (62) */
        /* cbg := Geodetic -> Gaussian, KW p186 - 187 (51) - (52) */
        /* PROJ_ETMERC_ORDER = 6th degree : Engsager and Poder: ICC2007 */

        cgb[0] = n*( 2 + n*(-2/3.0  + n*(-2      + n*(116/45.0 + n*(26/45.0 +
                    n*(-2854/675.0 ))))));
        cbg[0] = n*(-2 + n*( 2/3.0  + n*( 4/3.0  + n*(-82/45.0 + n*(32/45.0 +
                    n*( 4642/4725.0))))));
        np     *= n;
        cgb[1] = np*(7/3.0 + n*( -8/5.0  + n*(-227/45.0 + n*(2704/315.0 +
                    n*( 2323/945.0)))));
        cbg[1] = np*(5/3.0 + n*(-16/15.0 + n*( -13/9.0  + n*( 904/315.0 +
                    n*(-1522/945.0)))));
        np     *= n;
        /* n^5 coeff corrected from 1262/105 . -1262/105 */
        cgb[2] = np*( 56/15.0  + n*(-136/35.0 + n*(-1262/105.0 +
                    n*( 73814/2835.0))));
        cbg[2] = np*(-26/15.0  + n*(  34/21.0 + n*(    8/5.0   +
                    n*(-12686/2835.0))));
        np     *= n;
        /* n^5 coeff corrected from 322/35 -> 332/35 */
        cgb[3] = np*(4279/630.0 + n*(-332/35.0 + n*(-399572/14175.0)));
        cbg[3] = np*(1237/630.0 + n*( -12/5.0  + n*( -24832/14175.0)));
        np     *= n;
        cgb[4] = np*(4174/315.0 + n*(-144838/6237.0 ));
        cbg[4] = np*(-734/315.0 + n*( 109598/31185.0));
        np     *= n;
        cgb[5] = np*(601676/22275.0 );
        cbg[5] = np*(444337/155925.0);

        /* Constants of the projections */
        /* Transverse Mercator (UTM, ITM, etc) */
        np = n*n;
        /* Norm. mer. quad, K&W p.50 (96), p.19 (38b), p.5 (2) */
        Qn = scaleFactor/(1 + n) * (1 + np*(1/4.0 + np*(1/64.0 + np/256.0)));
        /* coef of trig series */
        /* utg := ell. N, E -> sph. N, E,  KW p194 (65) */
        /* gtu := sph. N, E -> ell. N, E,  KW p196 (69) */
        utg[0] = n*(-0.5  + n*( 2/3.0 + n*(-37/96.0 + n*( 1/360.0 +
                    n*(  81/512.0 + n*(-96199/604800.0))))));
        gtu[0] = n*( 0.5  + n*(-2/3.0 + n*(  5/16.0 + n*(41/180.0 +
                    n*(-127/288.0 + n*(  7891/37800.0 ))))));
        utg[1] = np*(-1/48.0 + n*(-1/15.0 + n*(437/1440.0 + n*(-46/105.0 +
                    n*( 1118711/3870720.0)))));
        gtu[1] = np*(13/48.0 + n*(-3/5.0  + n*(557/1440.0 + n*(281/630.0 +
                    n*(-1983433/1935360.0)))));
        np      *= n;
        utg[2] = np*(-17/480.0 + n*(  37/840.0 + n*(  209/4480.0  +
                    n*( -5569/90720.0 ))));
        gtu[2] = np*( 61/240.0 + n*(-103/140.0 + n*(15061/26880.0 +
                    n*(167603/181440.0))));
        np      *= n;
        utg[3] = np*(-4397/161280.0 + n*(  11/504.0 + n*( 830251/7257600.0)));
        gtu[3] = np*(49561/161280.0 + n*(-179/168.0 + n*(6601661/7257600.0)));
        np     *= n;
        utg[4] = np*(-4583/161280.0 + n*(  108847/3991680.0));
        gtu[4] = np*(34729/80640.0  + n*(-3418889/1995840.0));
        np     *= n;
        utg[5] = np*(-20648693/638668800.0);
        gtu[5] = np*(212378941/319334400.0);

        /* Gaussian latitude value of the origin latitude */
        Z = gatg (cbg, PROJ_ETMERC_ORDER, projectionLatitude);

        /* Origin northing minus true northing at the origin latitude */
        /* i.e. true northing = N - P->Zb                         */
        Zb  = - Qn*(Z + clens(gtu, PROJ_ETMERC_ORDER, 2*Z));
    }
    
    public boolean hasInverse() {
        return true;
    }
    
    public boolean isRectilinear() {
        return false;
    }
    
    public Object clone() {
        ExtendedTransverseMercatorProjection p = (ExtendedTransverseMercatorProjection) super.clone();
        if (cgb != null) {
            p.cgb = (double[]) cgb.clone();
        }
        if (cbg != null) {
            p.cbg = (double[]) cbg.clone();
        }
        if (utg != null) {
            p.utg = (double[]) utg.clone();
        }
        if (gtu != null) {
            p.gtu = (double[]) gtu.clone();
        }
        return p;
    }

    public String toString() {
        return "Extended Transverse Mercator";
    }

}
