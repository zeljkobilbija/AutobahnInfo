package com.zeljkobilbija001.autobahninfo;

import java.util.Arrays;
import java.util.List;

/** Spisak autoputeva. Novi autoput: dodaj JSON u assets/autoputevi i upiši ime datoteke ovde. */
public final class Autoputevi {

    private Autoputevi() { }

    public static final List<Autoput> SVE = Arrays.asList(
            new Autoput("A1", "Heiligenhafen – Saarbrücken", null),
            new Autoput("A2", "Oberhausen – Berlin", null),
            new Autoput("A3", "Elten – Passau", null),
            new Autoput("A4", "Aachen – Görlitz", null),
            new Autoput("A5", "Hattenbach – Basel", null),
            new Autoput("A6", "Saarbrücken – Waidhaus", null),
            new Autoput("A7", "Flensburg – Füssen", null),
            new Autoput("A8", "Luxemburg – Salzburg", "autoputevi/a8.json"),
            new Autoput("A9", "Berlin – München", null),
            new Autoput("A10", "Berliner Ring", null)
    );

    public static Autoput poId(String id) {
        for (Autoput a : SVE) if (a.id.equals(id)) return a;
        return null;
    }
}
