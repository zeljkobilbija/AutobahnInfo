package com.zeljkobilbija001.autobahninfo;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cene goriva u zemlji: nedeljni nacionalni prosek (EU Weekly Oil Bulletin, preko fuel-prices.eu, CC BY 4.0).
 * Preuzeti fajl se čuva 6 sati; bez interneta se koristi poslednji sačuvani.
 */
public final class CeneGoriva {

    public static final int FR_NEDELJA = 0;

    public static final class Cena {
        public final String naziv;
        public final int nazivRes;
        public final double eura;
        public final String jedinica;

        Cena(String naziv, int nazivRes, double eura, String jedinica) {
            this.naziv = naziv;
            this.nazivRes = nazivRes;
            this.eura = eura;
            this.jedinica = jedinica;
        }

        public String naziv(Context c) {
            return naziv.isEmpty() ? c.getString(nazivRes) : naziv;
        }
    }

    public static final class Blok {
        public final int vrsta;
        public final String period;
        public final List<Cena> cene;

        Blok(int vrsta, String period, List<Cena> cene) {
            this.vrsta = vrsta;
            this.period = period;
            this.cene = cene;
        }
    }

    public interface Odgovor {
        /** Poziva se na glavnoj niti; prazna lista ako cene nisu dostupne. */
        void stiglo(List<Blok> blokovi);
    }

    private static final String FR_URL = "https://www.fuel-prices.eu/Germany/?format=json";
    private static final String L = "€/L";
    private static final long VAZI_MS = 6 * 60 * 60 * 1000L;
    private static final String PREFS = "cene_goriva";
    private static final ExecutorService NIT = Executors.newSingleThreadExecutor();

    private CeneGoriva() { }

    public static void ucitaj(Context ctx, Odgovor odgovor) {
        Context c = ctx.getApplicationContext();
        Handler glavna = new Handler(Looper.getMainLooper());
        NIT.execute(() -> {
            SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            List<Blok> l = new ArrayList<>();
            Blok b = francuska(tekst(p, "GE", FR_URL));
            if (b != null && !b.cene.isEmpty()) l.add(b);
            glavna.post(() -> odgovor.stiglo(l));
        });
    }

    /** Sveže sačuvan tekst, inače preuzimanje; bez interneta poslednji sačuvani (ili null). */
    private static String tekst(SharedPreferences p, String kljuc, String url) {
        String stari = p.getString(kljuc, null);
        if (stari != null && System.currentTimeMillis() - p.getLong(kljuc + "_t", 0) < VAZI_MS) return stari;
        try {
            String novi = get(url);
            p.edit().putString(kljuc, novi).putLong(kljuc + "_t", System.currentTimeMillis()).apply();
            return novi;
        } catch (Exception e) {
            return stari;
        }
    }

    private static Blok francuska(String json) {
        if (json == null) return null;
        try {
            JSONObject fp = new JSONObject(json).getJSONObject("fp");
            JSONArray a = fp.getJSONArray("prices");
            List<Cena> cene = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                double v = o.optDouble("mean", Double.NaN);
                if (Double.isNaN(v)) continue;
                String kod = o.optString("fuel_code");
                String naziv;
                switch (kod) {
                    case "E5": naziv = "Super E5"; break;
                    case "E10": naziv = "Super E10"; break;
                    case "B7": naziv = "Diesel"; break;
                    case "LPG": naziv = "Autogas (LPG)"; break;
                    default: naziv = o.optString("fuel", kod);
                }
                cene.add(new Cena(naziv, 0, v, L));
            }
            String upd = fp.optString("updated", "");
            Date d = upd.length() >= 10 ? new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(upd.substring(0, 10)) : null;
            return new Blok(FR_NEDELJA, d == null ? "?" : DateFormat.getDateInstance(DateFormat.MEDIUM).format(d), cene);
        } catch (Exception e) {
            return null;
        }
    }

    private static String get(String adresa) throws Exception {
        HttpURLConnection v = (HttpURLConnection) new URL(adresa).openConnection();
        v.setConnectTimeout(10000);
        v.setReadTimeout(15000);
        v.setRequestProperty("User-Agent", "AutobahnInfo-Android");
        v.setRequestProperty("Accept", "application/json");
        try {
            if (v.getResponseCode() != 200) throw new Exception("HTTP " + v.getResponseCode());
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(v.getInputStream(), StandardCharsets.UTF_8))) {
                String red;
                while ((red = r.readLine()) != null) sb.append(red).append('\n');
            }
            return sb.toString();
        } finally {
            v.disconnect();
        }
    }
}
