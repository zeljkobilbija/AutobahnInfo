package com.zeljkobilbija001.autobahninfo;

/** Neprekinuti deo autoputa. Kilometri se računaju od njegovog početka. */
public final class Deonica {
    public final String od, doMesta;
    /** Kuda se ide kada kilometri rastu (+) i kada opadaju (-). */
    public final String plus, minus;

    public Deonica(String od, String doMesta, String plus, String minus) {
        this.od = od;
        this.doMesta = doMesta;
        this.plus = plus;
        this.minus = minus;
    }

    public String naziv() {
        return od + " – " + doMesta;
    }

    /**
     * Kilometarske table (alati/kilometraza.js): naš km, km sa table i smer (+1 table rastu sa našim km, -1 opadaju).
     * Poređane po našem km; prazno ako za deonicu nema tabli.
     */
    double[] kalNas = new double[0], kalTabla = new double[0];
    int[] kalSmer = new int[0];

    /** Mesta gde je autoput stvarno prekinut: parovi [km kraja, km nastavka]; tu se linija ne spaja. */
    double[] prekidOd = new double[0], prekidDo = new double[0];

    /** Da li je između dva kilometra prekid autoputa. */
    public boolean prekidIzmedju(double a, double b) {
        double od = Math.min(a, b), d = Math.max(a, b);
        for (int i = 0; i < prekidOd.length; i++) {
            if (prekidOd[i] >= od - 0.05 && prekidDo[i] <= d + 0.05) return true;
        }
        return false;
    }

    /**
     * Kilometar kao na tablama pored autoputa: od najbliže table na istom neprekinutom delu autoputa
     * (kilometraža teče ravnomerno, a naši kilometri su mereni po putu). NaN ako na tom delu nema tabli.
     */
    public double kmTabla(double km) {
        int n = kalNas.length;
        if (n == 0) return Double.NaN;
        int i = java.util.Arrays.binarySearch(kalNas, km);
        if (i < 0) i = -i - 1;
        int naj = -1;
        // prva tabla iza i prva ispred, ako između njih i ovog km nema prekida autoputa
        if (i < n && !prekidIzmedju(km, kalNas[i])) naj = i;
        if (i > 0 && !prekidIzmedju(km, kalNas[i - 1])
                && (naj < 0 || km - kalNas[i - 1] < kalNas[naj] - km)) naj = i - 1;
        if (naj < 0) return Double.NaN;
        double z = kalTabla[naj] + kalSmer[naj] * (km - kalNas[naj]);
        if (z < -3) return Double.NaN;   // pre nule kilometraže: ne zna se
        return Math.max(0, z);
    }

    /** Kilometar za prikaz: sa table ako se zna, inače od početka deonice. */
    public double kmZaPrikaz(double km) {
        double z = kmTabla(km);
        return Double.isNaN(z) ? km : z;
    }
}
