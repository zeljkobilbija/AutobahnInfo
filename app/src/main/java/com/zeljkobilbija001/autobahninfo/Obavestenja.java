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
    private static final String POSAO = "provera_stanja";
    private static final String KANAL = "stanje_na_putu";
    public static final int MINUTA = 15;

    private Obavestenja() { }

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static Set<String> praceni(Context c) {
        return new HashSet<>(p(c).getStringSet(PRACENI, new HashSet<>()));
    }

    public static boolean prati(Context c, String autoput) {
        return praceni(c).contains(autoput);
    }

    /**
     * Uključuje/isključuje praćenje. Pri uključivanju trenutne poruke se beleže kao viđene
     * (korisnik ih upravo gleda), pa obaveštenja stižu samo za nove.
     */
    public static void postavi(Context c, String autoput, boolean ukljuci, List<Dogadjaj> trenutno) {
        Set<String> s = praceni(c);
        if (ukljuci) s.add(autoput);
        else s.remove(autoput);
        SharedPreferences.Editor e = p(c).edit().putStringSet(PRACENI, s);
        if (ukljuci && trenutno != null) {
            Set<String> v = new HashSet<>(p(c).getStringSet(VIDJENO + autoput, new HashSet<>()));
            v.addAll(idZaObavestenje(trenutno));
            e.putStringSet(VIDJENO + autoput, v);
        }
        e.apply();
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

    private static Set<String> idZaObavestenje(List<Dogadjaj> l) {
        Set<String> s = new HashSet<>();
        for (Dogadjaj d : l) if (d.zaObavestenje()) s.add(d.id);
        return s;
    }

    /** Poziva ProveraWorker: javlja nove važne poruke sa celog praćenog autoputa. */
    static void obradi(Context c, String autoputId, List<Dogadjaj> trenutno) {
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
        Set<String> vidjeno = new HashSet<>(p(c).getStringSet(VIDJENO + autoputId, new HashSet<>()));
        Set<String> postoje = new HashSet<>();
        for (Dogadjaj d : trenutno) postoje.add(d.id);
        vidjeno.retainAll(postoje);
        List<Dogadjaj> novi = new java.util.ArrayList<>();
        for (Dogadjaj d : kandidati) if (vidjeno.add(d.id)) novi.add(d);
        p(c).edit().putStringSet(VIDJENO + autoputId, vidjeno).apply();
        for (Dogadjaj d : novi) Dnevnik.upisi(c, "OBAVEŠTENJE " + autoputId + ": " + red(c, d, udaljenosti));
        if (!novi.isEmpty() && !smeDaObavesti(c)) Dnevnik.upisi(c, "   (obaveštenja nisu dozvoljena – nije prikazano)");
        if (!novi.isEmpty()) prikazi(c, autoputId, novi, udaljenosti);
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
