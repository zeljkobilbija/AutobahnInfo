package com.zeljkobilbija001.autobahninfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Jedna saobraćajna poruka sa verkehr.autobahn.de (upozorenje ili zatvaranje).
 * Tekst opisa je na nemačkom, onako kako ga daje Autobahn GmbH; vrsta se određuje iz njega.
 */
public final class Dogadjaj {

    /** Vrste, poređane po važnosti (manji broj = važnije). */
    public static final int ZATVORENO = 0, UDES = 1, OPASNOST = 2, KOLONA = 3,
            TRAKA = 4, SPORO = 5, PRILAZ = 6, OSTALO = 7;

    public final String id;
    public final int vrsta;
    public final String deonica;   // "Pforzheim-West – Karlsruhe" (može biti prazno)
    public final String smer;      // "Stuttgart → Karlsruhe"
    public final String opis;      // nemački tekst, redovi spojeni sa \n
    public final int kasnjenjeMin; // 0 = nepoznato
    public final long pocetak;     // ms, 0 = nepoznato
    public final double lat, lon;  // početak; NaN ako nema
    public final double lat2, lon2; // kraj (iz geometrije, u smeru vožnje); NaN ako nema

    private Dogadjaj(String id, int vrsta, String deonica, String smer, String opis,
                     int kasnjenjeMin, long pocetak, double lat, double lon, double lat2, double lon2) {
        this.id = id;
        this.vrsta = vrsta;
        this.deonica = deonica;
        this.smer = smer;
        this.opis = opis;
        this.kasnjenjeMin = kasnjenjeMin;
        this.pocetak = pocetak;
        this.lat = lat;
        this.lon = lon;
        this.lat2 = lat2;
        this.lon2 = lon2;
    }

    /** Da li zbog ove poruke vredi poslati obaveštenje (udes, kolona, zatvoren put, opasnost). */
    public boolean zaObavestenje() {
        switch (vrsta) {
            case ZATVORENO:
            case UDES:
            case OPASNOST:
                return true;
            case KOLONA:
                return kasnjenjeMin == 0 || kasnjenjeMin >= 10;
            default:
                return false;
        }
    }

    public boolean imaKoordinate() {
        return !Double.isNaN(lat) && !Double.isNaN(lon);
    }

    // ---------- Čitanje JSON-a ----------

    /** Lista iz odgovora servisa "warning" ili "closure"; najavljena (buduća) zatvaranja se preskaču. */
    static List<Dogadjaj> izJsona(String json, String kljuc) throws Exception {
        List<Dogadjaj> l = new ArrayList<>();
        JSONArray a = new JSONObject(json).optJSONArray(kljuc);
        if (a == null) return l;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null || o.optBoolean("future", false)) continue;
            Dogadjaj d = jedan(o);
            if (d != null) l.add(d);
        }
        return l;
    }

    private static Dogadjaj jedan(JSONObject o) {
        String id = o.optString("identifier", "");
        if (id.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        JSONArray opisi = o.optJSONArray("description");
        if (opisi != null) {
            boolean prazan = true;
            for (int i = 0; i < opisi.length(); i++) {
                String red = opisi.optString(i, "").trim();
                if (red.isEmpty()) {
                    prazan = true;
                    continue;
                }
                if (sb.length() > 0) sb.append(prazan ? "\n\n" : "\n");
                sb.append(red);
                prazan = false;
            }
        }
        String opis = sb.toString();

        int kasnjenje = 0;
        try {
            kasnjenje = Integer.parseInt(o.optString("delayTimeValue", "0").trim());
        } catch (NumberFormatException ignored) { }

        String prikaz = o.optString("display_type", "");
        int vrsta;
        if ("CLOSURE".equals(prikaz)) vrsta = ZATVORENO;
        else if ("CLOSURE_ENTRY_EXIT".equals(prikaz)) vrsta = PRILAZ;
        else vrsta = vrstaUpozorenja(opis.toLowerCase(Locale.GERMAN),
                    o.optString("abnormalTrafficType", ""), "true".equals(o.optString("isBlocked")), kasnjenje);

        String naslov = o.optString("title", "");
        int crta = naslov.indexOf('|');
        String deonica = crta >= 0 ? naslov.substring(crta + 1).trim() : "";
        if ("Verkehrsmeldung".equalsIgnoreCase(deonica)) deonica = "";
        deonica = deonica.replace(" - ", " – ");

        String smer = o.optString("subtitle", "").trim().replace("->", "→");

        double lat = Double.NaN, lon = Double.NaN;
        JSONObject k = o.optJSONObject("coordinate");
        if (k != null) {
            lat = k.optDouble("lat", Double.NaN);
            lon = k.optDouble("long", Double.NaN);
        }
        // kraj poruke: poslednja tačka linije (GeoJSON: [lon, lat])
        double lat2 = Double.NaN, lon2 = Double.NaN;
        JSONObject g = o.optJSONObject("geometry");
        JSONArray tacke = g != null ? g.optJSONArray("coordinates") : null;
        if (tacke != null && tacke.length() > 1) {
            JSONArray z = tacke.optJSONArray(tacke.length() - 1);
            if (z != null && z.length() >= 2) {
                lon2 = z.optDouble(0, Double.NaN);
                lat2 = z.optDouble(1, Double.NaN);
            }
        }

        return new Dogadjaj(id, vrsta, deonica, smer, opis, kasnjenje,
                vreme(o.optString("startTimestamp", "")), lat, lon, lat2, lon2);
    }

    private static int vrstaUpozorenja(String t, String tip, boolean blokirano, int kasnjenje) {
        if (t.contains("falschfahrer")) return OPASNOST;
        if (t.contains("unfall")) return UDES;
        if (blokirano || t.contains("vollsperrung") || t.contains("fahrbahn gesperrt")
                || t.contains("gesperrt in beide")) return ZATVORENO;
        if (t.contains("gegenstand") || t.contains("brand") || t.contains("glätte") || t.contains("glatteis")
                || t.contains("nebel") || t.contains("tiere") || t.contains("personen auf der fahrbahn")
                || t.contains("fahrbahnschäden") || t.contains("liegengeblieben") || t.contains("panne")
                || t.contains("gefahr")) return OPASNOST;
        if ("QUEUING_TRAFFIC".equals(tip) || t.contains("stau")) return KOLONA;
        if (t.contains("fahrstreifen gesperrt") || t.contains("fahrstreifen blockiert")) return TRAKA;
        if ("SLOW_TRAFFIC".equals(tip) || "HEAVY_TRAFFIC".equals(tip) || "UNSPECIFIED_ABNORMAL_TRAFFIC".equals(tip)
                || t.contains("stockend") || t.contains("durchschnittsgeschwindigkeit")) {
            return kasnjenje >= 15 ? KOLONA : SPORO;
        }
        return OSTALO;
    }

    private static long vreme(String iso) {
        if (iso.length() < 19) return 0;
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d = f.parse(iso.substring(0, 19));
            return d == null ? 0 : d.getTime();
        } catch (Exception e) {
            return 0;
        }
    }
}
