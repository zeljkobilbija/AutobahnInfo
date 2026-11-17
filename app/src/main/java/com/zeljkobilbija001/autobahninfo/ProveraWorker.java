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
                Obavestenja.obradi(c, id, StanjeNaPutu.preuzmi(id));
            } catch (Exception ignored) {
                // nema mreže ili servis ne radi: pokušava se pri sledećoj proveri
            }
        }
        return Result.success();
    }
}
