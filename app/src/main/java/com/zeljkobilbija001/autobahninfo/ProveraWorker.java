package com.zeljkobilbija001.autobahninfo;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Povremena provera stanja na praćenim autoputevima (pokreće je WorkManager). */
public class ProveraWorker extends Worker {

    public ProveraWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context c = getApplicationContext();
        for (String id : Obavestenja.praceni(c)) {
            if (isStopped()) break;
            try {
                java.util.List<Dogadjaj> l = StanjeNaPutu.preuzmi(id);
                int vaznih = 0;
                for (Dogadjaj d : l) if (d.zaObavestenje()) vaznih++;
                Dnevnik.upisi(c, "PRAĆENJE " + id + ": " + l.size() + " poruka, važnih " + vaznih);
                Obavestenja.obradi(c, id, l);
            } catch (Exception e) {
                Dnevnik.upisi(c, "PRAĆENJE " + id + ": greška " + e.getMessage());
                // nema mreže ili servis ne radi: pokušava se pri sledećoj proveri
            }
        }
        return Result.success();
    }
}
