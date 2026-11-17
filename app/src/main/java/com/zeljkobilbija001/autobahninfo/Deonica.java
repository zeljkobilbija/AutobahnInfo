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

    /** Najdalje od table do koje se kilometraža računa od nje. */
    private static final double DOMET_KM = 40;

    /** Kilometar kao na tablama pored autoputa (od najbliže table), ili NaN ako table nema u blizini. */
    public double kmTabla(double km) {
        int n = kalNas.length;
        if (n == 0) return Double.NaN;
        int i = java.util.Arrays.binarySearch(kalNas, km);
        if (i < 0) i = -i - 1;
        int naj = -1;
        if (i < n) naj = i;
        if (i > 0 && (naj < 0 || km - kalNas[i - 1] < kalNas[naj] - km)) naj = i - 1;
        if (naj < 0 || Math.abs(km - kalNas[naj]) > DOMET_KM) return Double.NaN;
        double z = kalTabla[naj] + kalSmer[naj] * (km - kalNas[naj]);
        return z < 0 ? Double.NaN : z;
    }

    /** Kilometar za prikaz: sa table ako se zna, inače od početka deonice. */
    public double kmZaPrikaz(double km) {
        double z = kmTabla(km);
        return Double.isNaN(z) ? km : z;
    }
}
