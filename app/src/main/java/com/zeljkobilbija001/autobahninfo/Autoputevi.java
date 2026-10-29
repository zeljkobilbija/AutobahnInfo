package com.zeljkobilbija001.autobahninfo;

import java.util.Arrays;
import java.util.List;

/** Spisak autoputeva. Novi autoput: dodaj JSON u assets/autoputevi i upiši ime datoteke ovde. */
public final class Autoputevi {

    private Autoputevi() { }

    public static final List<Autoput> SVE = Arrays.asList(
            new Autoput("A1", "Heiligenhafen – Saarbrücken", "autoputevi/a1.json"),
            new Autoput("A2", "Oberhausen – Berlin", "autoputevi/a2.json"),
            new Autoput("A3", "Elten – Passau", "autoputevi/a3.json"),
            new Autoput("A4", "Aachen – Görlitz", "autoputevi/a4.json"),
            new Autoput("A5", "Hattenbach – Basel", "autoputevi/a5.json"),
            new Autoput("A6", "Saarbrücken – Waidhaus", "autoputevi/a6.json"),
            new Autoput("A7", "Flensburg – Füssen", "autoputevi/a7.json"),
            new Autoput("A8", "Luxemburg – Salzburg", "autoputevi/a8.json"),
            new Autoput("A9", "Berlin – München", null),
            new Autoput("A10", "Berliner Ring", null),
            new Autoput("A14", "Wismar – Nossen", "autoputevi/a14.json"),
            new Autoput("A19", "Rostock – Wittstock", "autoputevi/a19.json"),
            new Autoput("A24", "Hamburg – Berlin", "autoputevi/a24.json"),
            new Autoput("A31", "Emden – Bottrop", "autoputevi/a31.json"),
            new Autoput("A38", "Göttingen – Leipzig", "autoputevi/a38.json"),
            new Autoput("A40", "Straelen – Dortmund", "autoputevi/a40.json"),
            new Autoput("A42", "Kamp-Lintfort – Dortmund", "autoputevi/a42.json"),
            new Autoput("A44", "Aachen – Kassel", null),
            new Autoput("A45", "Dortmund – Aschaffenburg", "autoputevi/a45.json"),
            new Autoput("A61", "Venlo – Hockenheim", "autoputevi/a61.json"),
            new Autoput("A81", "Würzburg – Gottmadingen", "autoputevi/a81.json"),
            new Autoput("A93", "Hof – Kiefersfelden", null),
            new Autoput("A96", "Lindau – München", null)
    );

    public static Autoput poId(String id) {
        for (Autoput a : SVE) if (a.id.equals(id)) return a;
        return null;
    }
}
