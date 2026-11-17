package com.zeljkobilbija001.autobahninfo;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Određuje na kom autoputu, deonici i kilometru je neka tačka: autoput se aproksimira linijom
 * kroz izlaze i odmorišta (poređane po kilometru), a tačka se projektuje na najbliži odsečak.
 * Sve radi na telefonu, iz podataka u assets-u.
 */
public final class Polozaj {

    /** Rezultat: autoput, deonica, kilometar i udaljenost od linije autoputa (km). */
    public static final class Tacka {
        public final String autoput;
        public final int deonica;
        public final double km, udaljenost;

        Tacka(String autoput, int deonica, double km, double udaljenost) {
            this.autoput = autoput;
            this.deonica = deonica;
            this.km = km;
            this.udaljenost = udaljenost;
        }
    }

    /** Jedna deonica jednog autoputa kao izlomljena linija. */
    private static final class Linija {
        final String autoput;
        final int deonica;
        final double[] km, lat, lon;
        final double minLat, maxLat, minLon, maxLon;

        Linija(String autoput, int deonica, List<Stavka> tacke) {
            this.autoput = autoput;
            this.deonica = deonica;
            int n = tacke.size();
            km = new double[n];
            lat = new double[n];
            lon = new double[n];
            double a = 90, b = -90, c = 180, d = -180;
            for (int i = 0; i < n; i++) {
                Stavka s = tacke.get(i);
                km[i] = s.km;
                lat[i] = s.lat;
                lon[i] = s.lon;
                a = Math.min(a, s.lat);
                b = Math.max(b, s.lat);
                c = Math.min(c, s.lon);
                d = Math.max(d, s.lon);
            }
            // okvir proširen za ~5 km, da se brzo preskoče daleki autoputevi
            minLat = a - 0.05;
            maxLat = b + 0.05;
            minLon = c - 0.08;
            maxLon = d + 0.08;
        }
    }

    private static List<Linija> linije;

    private Polozaj() { }

    private static synchronized List<Linija> linije(Context c) {
        if (linije != null) return linije;
        List<Linija> l = new ArrayList<>();
        for (Autoput a : Autoputevi.SVE) {
            Podaci p = Podaci.za(c, a);
            if (p == null) continue;
            for (int d = 0; d < p.deonice.size(); d++) {
                List<Stavka> tacke = new ArrayList<>();
                for (Stavka s : p.stavke) if (s.deonica == d && s.imaKoordinate()) tacke.add(s);
                if (tacke.size() < 2) continue;
                Collections.sort(tacke, (x, y) -> Double.compare(x.km, y.km));
                l.add(new Linija(a.id, d, tacke));
            }
        }
        linije = l;
        return l;
    }

    /**
     * Najbliži autoput tački (ili samo zadati autoput, ako {@code samoAutoput} nije null).
     * Vraća null ako u blizini (~5 km) nema nijednog.
     */
    public static Tacka nadji(Context c, double lat, double lon, String samoAutoput) {
        double kx = 111.32 * Math.cos(Math.toRadians(lat)), ky = 110.57;
        Tacka najbolja = null;
        for (Linija li : linije(c)) {
            if (samoAutoput != null && !samoAutoput.equals(li.autoput)) continue;
            if (lat < li.minLat || lat > li.maxLat || lon < li.minLon || lon > li.maxLon) continue;
            for (int i = 0; i + 1 < li.km.length; i++) {
                // ravan sistem u km, tačka je u koordinatnom početku
                double ax = (li.lon[i] - lon) * kx, ay = (li.lat[i] - lat) * ky;
                double bx = (li.lon[i + 1] - lon) * kx, by = (li.lat[i + 1] - lat) * ky;
                double dx = bx - ax, dy = by - ay, d2 = dx * dx + dy * dy;
                double t = d2 == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / d2));
                double px = ax + t * dx, py = ay + t * dy;
                double u = Math.sqrt(px * px + py * py);
                if (najbolja == null || u < najbolja.udaljenost) {
                    double km = li.km[i] + t * (li.km[i + 1] - li.km[i]);
                    najbolja = new Tacka(li.autoput, li.deonica, km, u);
                }
            }
        }
        return najbolja;
    }
}
