package com.zeljkobilbija001.autobahninfo;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Obaveštenja o stanju na putu: koji autoputevi se prate, povremena provera u pozadini
 * (WorkManager, oko 20 minuta) i prikaz notifikacije za nove udese, kolone i zatvaranja.
 */
public final class Obavestenja {

    private static final String PREFS = "obavestenja";
    private static final String PRACENI = "praceni";
    private static final String VIDJENO = "vidjeno_";
    private static final String STIGLO = "stiglo_";
    private static final String UKLJUCENO = "ukljuceno";
    private static final String POSLEDNJI = "poslednji_autoput";
    private static final String PRVI_PUT = "prvi_put_";
    private static final String POSAO = "provera_stanja";
    private static final String KANAL = "stanje_na_putu";
    public static final int MINUTA = 15;

    private Obavestenja() { }

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * Obaveštenja su jedan prekidač za sve autoputeve (odluka 18.11.2026). Stari spisak praćenih
     * autoputeva (PRACENI) se pri prvom čitanju prevodi: neprazan = uključeno, prvi sa spiska = poslednji.
     */
    public static boolean ukljucena(Context c) {
        SharedPreferences sp = p(c);
        if (!sp.contains(UKLJUCENO)) {
            Set<String> stari = sp.getStringSet(PRACENI, new HashSet<>());
            SharedPreferences.Editor e = sp.edit().putBoolean(UKLJUCENO, !stari.isEmpty()).remove(PRACENI);
            if (!stari.isEmpty() && !sp.contains(POSLEDNJI)) e.putString(POSLEDNJI, stari.iterator().next());
            e.apply();
            return !stari.isEmpty();
        }
        return sp.getBoolean(UKLJUCENO, false);
    }

    /** Autoputevi za proveru u pozadini: samo onaj na kom je vozač poslednji put bio. */
    public static Set<String> praceni(Context c) {
        Set<String> s = new HashSet<>();
        String a = p(c).getString(POSLEDNJI, null);
        if (ukljucena(c) && a != null) s.add(a);
        return s;
    }

    /**
     * Vožnja javlja da je vozač na autoputu {@code autoput}. Provera u pozadini posle toga prati njega;
     * pri promeni se njegove trenutne poruke prvi put samo beleže (bez obaveštenja za ceo autoput).
     */
    public static void naAutoputu(Context c, String autoput) {
        if (autoput.equals(p(c).getString(POSLEDNJI, null))) return;
        p(c).edit().putString(POSLEDNJI, autoput).putBoolean(PRVI_PUT + autoput, true).apply();
    }

    /**
     * Uključuje/isključuje obaveštenja za sve autoputeve. Pri uključivanju poruke otvorenog autoputa
     * se beleže kao viđene (korisnik ih upravo gleda) i on postaje praćeni, ako vozač nije na drugom.
     */
    public static void postavi(Context c, String autoput, boolean ukljuci, List<Dogadjaj> trenutno) {
        ukljucena(c); // prevod starog spiska, ako postoji
        SharedPreferences.Editor e = p(c).edit().putBoolean(UKLJUCENO, ukljuci);
        if (ukljuci) {
            if (VoznjaService.sadAutoput == null) e.putString(POSLEDNJI, autoput);
            if (trenutno != null) {
                Set<String> v = new HashSet<>(p(c).getStringSet(VIDJENO + autoput, new HashSet<>()));
                v.addAll(idZaObavestenje(trenutno));
                e.putStringSet(VIDJENO + autoput, v);
            }
        }
        e.apply();
        if (!ukljuci) {
            // već prikazana obaveštenja o stanju na putu se uklanjaju (stalno obaveštenje Vožnje ostaje)
            NotificationManagerCompat nm = NotificationManagerCompat.from(c);
            for (Autoput a : Autoputevi.SVE) nm.cancel(a.id.hashCode());
        }
        zakazi(c);
    }

    /** Pokreće ili gasi povremenu proveru, zavisno od toga da li se išta prati. */
    public static void zakazi(Context c) {
        WorkManager wm = WorkManager.getInstance(c.getApplicationContext());
        if (praceni(c).isEmpty()) {
            wm.cancelUniqueWork(POSAO);
            return;
        }
        PeriodicWorkRequest zahtev = new PeriodicWorkRequest.Builder(ProveraWorker.class, MINUTA, TimeUnit.MINUTES)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build();
        wm.enqueueUniquePeriodicWork(POSAO, ExistingPeriodicWorkPolicy.UPDATE, zahtev);
    }

    public static boolean smeDaObavesti(Context c) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(c,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
        return NotificationManagerCompat.from(c).areNotificationsEnabled();
    }

    /** Id poruke koja je poslednja stigla u obaveštenju za ovaj autoput, ili null. */
    public static String poslednjeStiglo(Context c, String autoputId) {
        String id = null;
        long max = Long.MIN_VALUE;
        for (String s : p(c).getStringSet(STIGLO + autoputId, new HashSet<>())) {
            int t = s.lastIndexOf('\t');
            if (t <= 0) continue;
            try {
                long ms = Long.parseLong(s.substring(t + 1));
                if (ms > max) { max = ms; id = s.substring(0, t); }
            } catch (NumberFormatException ignored) { }
        }
        return id;
    }

    private static Set<String> idZaObavestenje(List<Dogadjaj> l) {
        Set<String> s = new HashSet<>();
        for (Dogadjaj d : l) if (d.zaObavestenje()) s.add(d.id);
        return s;
    }

    /** Poziva ProveraWorker: javlja nove važne poruke sa celog praćenog autoputa. */
    static void obradi(Context c, String autoputId, List<Dogadjaj> trenutno) {
        if (p(c).getBoolean(PRVI_PUT + autoputId, false)) {
            // autoput je upravo postao praćeni: postojeće poruke se samo beleže, javljaju se tek nove
            Set<String> v = new HashSet<>(p(c).getStringSet(VIDJENO + autoputId, new HashSet<>()));
            v.addAll(idZaObavestenje(trenutno));
            p(c).edit().putStringSet(VIDJENO + autoputId, v).remove(PRVI_PUT + autoputId).apply();
            Dnevnik.upisi(c, "PRAĆENJE " + autoputId + ": prvi put, postojeće poruke samo zabeležene");
            return;
        }
        List<Dogadjaj> vazni = new java.util.ArrayList<>();
        for (Dogadjaj d : trenutno) if (d.zaObavestenje()) vazni.add(d);
        obradi(c, autoputId, trenutno, vazni, null);
    }

    /**
     * Javlja poruke iz {@code kandidati} koje još nisu javljene. Spisak viđenih je zajednički za praćenje
     * i režim vožnje (ista poruka se ne javlja dvaput), a čuvaju se samo poruke koje još postoje.
     *
     * @param udaljenosti km do poruke (režim vožnje) ili null
     */
    static synchronized void obradi(Context c, String autoputId, List<Dogadjaj> trenutno,
                                    List<Dogadjaj> kandidati, java.util.Map<String, Integer> udaljenosti) {
        // prekidač isključen: ne javlja se ništa, ni iz Vožnje (pri uključivanju se trenutne poruke beleže kao viđene)
        if (!ukljucena(c)) {
            Dnevnik.upisi(c, "OBAVEŠTENJA " + autoputId + ": isključena, ništa se ne javlja");
            return;
        }
        Set<String> vidjeno = new HashSet<>();
        // stare sačuvane oznake (pre stabilnih) svode se na stabilne, da se ništa ne javi ponovo
        for (String s : p(c).getStringSet(VIDJENO + autoputId, new HashSet<>())) vidjeno.add(Dogadjaj.stabilanId(s));
        Set<String> postoje = new HashSet<>();
        for (Dogadjaj d : trenutno) postoje.add(d.id);
        vidjeno.retainAll(postoje);
        List<Dogadjaj> novi = new java.util.ArrayList<>();
        for (Dogadjaj d : kandidati) if (vidjeno.add(d.id)) novi.add(d);
        // trenutak stizanja svake javljene poruke (id → ms), samo za poruke koje još postoje
        java.util.Map<String, Long> stiglo = new java.util.HashMap<>();
        for (String s : p(c).getStringSet(STIGLO + autoputId, new HashSet<>())) {
            int t = s.lastIndexOf('\t');
            if (t <= 0) continue;
            try {
                String id = s.substring(0, t);
                if (postoje.contains(id)) stiglo.put(id, Long.parseLong(s.substring(t + 1)));
            } catch (NumberFormatException ignored) { }
        }
        long sad = System.currentTimeMillis();
        for (int i = 0; i < novi.size(); i++) stiglo.put(novi.get(i).id, sad - i); // u istoj proveri: po redu liste
        Set<String> zapis = new HashSet<>();
        for (java.util.Map.Entry<String, Long> e : stiglo.entrySet()) zapis.add(e.getKey() + "\t" + e.getValue());
        p(c).edit().putStringSet(VIDJENO + autoputId, vidjeno).putStringSet(STIGLO + autoputId, zapis).apply();
        for (Dogadjaj d : novi) Dnevnik.upisi(c, "OBAVEŠTENJE " + autoputId + ": " + red(c, d, udaljenosti));
        if (!novi.isEmpty() && !smeDaObavesti(c)) Dnevnik.upisi(c, "   (obaveštenja nisu dozvoljena – nije prikazano)");
        if (novi.isEmpty()) return;
        // u obaveštenju sve javljene poruke koje još važe, poslednja stigla na vrhu (ne po važnosti)
        List<Dogadjaj> prikaz = new java.util.ArrayList<>();
        for (Dogadjaj d : kandidati) if (stiglo.containsKey(d.id)) prikaz.add(d);
        java.util.Collections.sort(prikaz, (a, b) -> Long.compare(stiglo.get(b.id), stiglo.get(a.id)));
        prikazi(c, autoputId, prikaz, udaljenosti);
    }

    @SuppressWarnings("MissingPermission")
    private static void prikazi(Context c, String autoputId, List<Dogadjaj> novi,
                                java.util.Map<String, Integer> udaljenosti) {
        if (!smeDaObavesti(c)) return;
        napraviKanal(c);
        Autoput a = Autoputevi.poId(autoputId);
        String oznaka = a != null ? a.oznaka() : autoputId;

        Intent otvori = StanjeActivity.namera(c, autoputId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, autoputId.hashCode(), otvori,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.InboxStyle stil = new NotificationCompat.InboxStyle();
        for (Dogadjaj d : novi) stil.addLine(red(c, d, udaljenosti));
        Dogadjaj prvi = novi.get(0);
        String naslov = novi.size() == 1
                ? oznaka + " · " + c.getString(DogadjajAdapter.naziv(prvi.vrsta))
                : c.getString(R.string.obav_naslov_vise, oznaka, novi.size());

        NotificationCompat.Builder b = new NotificationCompat.Builder(c, KANAL)
                .setSmallIcon(R.drawable.ic_obavestenje)
                .setColor(Izgled.boja(c, R.color.plava))
                .setContentTitle(naslov)
                .setContentText(red(c, prvi, udaljenosti))
                .setStyle(stil)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        NotificationManagerCompat.from(c).notify(autoputId.hashCode(), b.build());
    }

    /** "Unfall · za 12 km · Köln → Frankfurt · Idstein – Wiesbadener Kreuz · +11 min" */
    private static String red(Context c, Dogadjaj d, java.util.Map<String, Integer> udaljenosti) {
        StringBuilder sb = new StringBuilder(c.getString(DogadjajAdapter.naziv(d.vrsta)));
        Integer km = udaljenosti != null ? udaljenosti.get(d.id) : null;
        if (km != null) sb.append(" · ").append(c.getString(R.string.voznja_za_km, Math.max(0, km)));
        if (!d.smer.isEmpty()) sb.append(" · ").append(d.smer);
        if (!d.deonica.isEmpty()) sb.append(" · ").append(d.deonica);
        if (d.kasnjenjeMin > 0) sb.append(" · ").append(c.getString(R.string.stanje_kasnjenje, d.kasnjenjeMin));
        return sb.toString();
    }

    private static void napraviKanal(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(KANAL) != null) return;
        nm.createNotificationChannel(new NotificationChannel(KANAL,
                c.getString(R.string.obav_kanal), NotificationManager.IMPORTANCE_DEFAULT));
    }
}
