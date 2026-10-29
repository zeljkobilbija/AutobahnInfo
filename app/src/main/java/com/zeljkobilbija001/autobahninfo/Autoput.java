package com.zeljkobilbija001.autobahninfo;

/** Jedan autoput na početnom ekranu (A1, A2…). */
public final class Autoput {
    public final String id;       // "A8"
    public final String ruta;     // "Luxemburg – Salzburg"
    public final String datoteka; // "autoputevi/a8.json" ili null ako podaci još ne postoje

    public Autoput(String id, String ruta, String datoteka) {
        this.id = id;
        this.ruta = ruta;
        this.datoteka = datoteka;
    }

    /** Oznaka kao na tabli: "A 8". */
    public String oznaka() {
        return id.replaceFirst("^A", "A ");
    }

    public boolean dostupan() {
        return datoteka != null;
    }
}
