package com.zeljkobilbija001.autobahninfo;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Stanje na putu za jedan autoput: upozorenja (kolone, udesi, opasnosti) i trenutna zatvaranja
 * sa javnog API-ja Autobahn GmbH (verkehr.autobahn.de, bez ključa).
 */
public final class StanjeNaPutu {

    public interface Odgovor {
        /** Poziva se na glavnoj niti; null ako podaci nisu stigli. */
        void stiglo(List<Dogadjaj> dogadjaji);
    }

    private static final String API = "https://verkehr.autobahn.de/o/autobahn/";
    private static final ExecutorService NIT = Executors.newSingleThreadExecutor();

    private StanjeNaPutu() { }

    public static void ucitaj(String autoput, Odgovor odgovor) {
        Handler glavna = new Handler(Looper.getMainLooper());
        NIT.execute(() -> {
            List<Dogadjaj> l;
            try {
                l = preuzmi(autoput);
            } catch (Exception e) {
                l = null;
            }
            List<Dogadjaj> konacno = l;
            glavna.post(() -> odgovor.stiglo(konacno));
        });
    }

    /** Preuzimanje na tekućoj niti (za proveru u pozadini); važnije poruke idu prve. */
    public static List<Dogadjaj> preuzmi(String autoput) throws Exception {
        List<Dogadjaj> l = new ArrayList<>();
        l.addAll(Dogadjaj.izJsona(get(API + autoput + "/services/warning"), "warning"));
        l.addAll(Dogadjaj.izJsona(get(API + autoput + "/services/closure"), "closure"));
        Collections.sort(l, (a, b) -> a.vrsta != b.vrsta ? Integer.compare(a.vrsta, b.vrsta)
                : Long.compare(b.pocetak, a.pocetak));
        return l;
    }

    private static String get(String adresa) throws Exception {
        HttpURLConnection v = (HttpURLConnection) new URL(adresa).openConnection();
        v.setConnectTimeout(10000);
        v.setReadTimeout(20000);
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
