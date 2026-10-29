package com.zeljkobilbija001.autobahninfo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Redovi liste autoputa, a na kraju napomena o izvoru podataka. */
public class StavkaAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int RED = 0, IZVOR = 1;
    private final List<Stavka> stavke = new ArrayList<>();

    @SuppressLint("NotifyDataSetChanged")
    public void postavi(List<Stavka> nove) {
        stavke.clear();
        stavke.addAll(nove);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return stavke.isEmpty() ? 0 : stavke.size() + 1;
    }

    @Override
    public int getItemViewType(int poz) {
        return poz < stavke.size() ? RED : IZVOR;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup roditelj, int tip) {
        LayoutInflater inf = LayoutInflater.from(roditelj.getContext());
        if (tip == IZVOR) {
            return new RecyclerView.ViewHolder(inf.inflate(R.layout.item_izvor, roditelj, false)) { };
        }
        return new Drzac(inf.inflate(R.layout.item_stavka, roditelj, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int poz) {
        if (h instanceof Drzac) ((Drzac) h).povezi(stavke.get(poz));
    }

    static class Drzac extends RecyclerView.ViewHolder {
        final TextView km, znakTekst, naziv, opis;
        final ImageView znakIkona;
        final View znak;
        final ChipGroup oznake;

        Drzac(View v) {
            super(v);
            km = v.findViewById(R.id.km);
            znak = v.findViewById(R.id.znak);
            znakTekst = v.findViewById(R.id.znakTekst);
            znakIkona = v.findViewById(R.id.znakIkona);
            naziv = v.findViewById(R.id.naziv);
            opis = v.findViewById(R.id.opis);
            oznake = v.findViewById(R.id.oznake);
        }

        void povezi(Stavka s) {
            Context c = itemView.getContext();
            km.setText(String.format(Locale.getDefault(), "%.1f", s.km));

            // Znak na šini: broj izlaza, P, pumpa ili pribor za jelo
            boolean pumpa = !s.jeIzlaz() && s.tip != Stavka.PARKING && !s.gorivo.isEmpty();
            boolean hrana = !s.jeIzlaz() && s.tip != Stavka.PARKING && s.gorivo.isEmpty();
            int bela = 0xFFFFFFFF;
            int plava = Izgled.boja(c, R.color.plava);
            if (s.tip == Stavka.IZLAZ) {
                znak.setBackground(Izgled.oblik(c, plava, 7, 0, 0));
                tekstZnaka(s.ref, bela);
            } else if (s.tip == Stavka.CVORISTE) {
                znak.setBackground(Izgled.oblik(c, bela, 7, plava, 2.5f));
                tekstZnaka(s.ref, plava);
            } else if (s.tip == Stavka.PARKING) {
                znak.setBackground(Izgled.oblik(c, plava, 7, 0, 0));
                tekstZnaka("P", bela);
                znakTekst.setTextSize(19);
            } else if (pumpa) {
                znak.setBackground(Izgled.oblik(c, Izgled.boja(c, R.color.pumpa), 16, 0, 0));
                ikonaZnaka(R.drawable.ic_pumpa, Izgled.boja(c, R.color.pumpa_tekst));
            } else if (hrana) {
                int crvena = Izgled.boja(c, R.color.hrana);
                znak.setBackground(Izgled.oblik(c, Izgled.boja(c, R.color.povrsina), 16, crvena, 2));
                ikonaZnaka(R.drawable.ic_hrana, crvena);
            }

            String ime = s.naziv.isEmpty() ? c.getString(R.string.tip_izlaz, s.ref) : s.naziv;
            naziv.setText(ime);
            switch (s.tip) {
                case Stavka.IZLAZ: opis.setText(c.getString(R.string.tip_izlaz, s.ref)); break;
                case Stavka.CVORISTE: opis.setText(c.getString(R.string.tip_cvoriste, s.ref)); break;
                case Stavka.ODMORISTE: opis.setText(R.string.tip_odmoriste); break;
                case Stavka.AUTOHOF: opis.setText(R.string.tip_autohof); break;
                default: opis.setText(R.string.tip_parking);
            }

            // Oznake: gorivo, hrana, WC, tuš, punjači…
            oznake.removeAllViews();
            for (String g : s.gorivo) cip(c, g, R.color.cip_gorivo, R.color.cip_gorivo_tekst);
            for (String h : s.hrana) cip(c, h, R.color.cip_hrana, R.color.cip_hrana_tekst);
            if (s.restoran && s.hrana.isEmpty())
                cip(c, c.getString(R.string.cip_restoran), R.color.cip_hrana, R.color.cip_hrana_tekst);
            if (s.wc) cip(c, c.getString(R.string.cip_wc), R.color.cip_obicno, R.color.cip_obicno_tekst);
            if (s.tus) cip(c, c.getString(R.string.cip_tus), R.color.cip_obicno, R.color.cip_obicno_tekst);
            if (!s.punjaci.isEmpty()) {
                int n = s.punjaci.size();
                String spisak = android.text.TextUtils.join(", ", s.punjaci.subList(0, Math.min(3, n)))
                        + (n > 3 ? " +" + (n - 3) : "");
                cip(c, c.getString(R.string.cip_punjac, spisak), R.color.cip_struja, R.color.cip_struja_tekst);
            }
            if (s.hotel) cip(c, c.getString(R.string.cip_hotel), R.color.cip_obicno, R.color.cip_obicno_tekst);
            if (s.prodavnica) cip(c, c.getString(R.string.cip_prodavnica), R.color.cip_obicno, R.color.cip_obicno_tekst);
            if (!s.kamioni.isEmpty())
                cip(c, c.getString(R.string.cip_kamioni, s.kamioni), R.color.cip_obicno, R.color.cip_obicno_tekst);
            if ("24/7".equals(s.radnoVreme))
                cip(c, c.getString(R.string.cip_0_24), R.color.cip_obicno, R.color.cip_obicno_tekst);
            oznake.setVisibility(oznake.getChildCount() > 0 ? View.VISIBLE : View.GONE);
        }

        private void tekstZnaka(String t, int boja) {
            znakIkona.setVisibility(View.GONE);
            znakTekst.setVisibility(View.VISIBLE);
            znakTekst.setText(t);
            znakTekst.setTextColor(boja);
            znakTekst.setTextSize(t.length() > 3 ? 13 : 16);
        }

        private void ikonaZnaka(int ikona, int boja) {
            znakTekst.setVisibility(View.GONE);
            znakIkona.setVisibility(View.VISIBLE);
            znakIkona.setImageResource(ikona);
            znakIkona.setImageTintList(Izgled.jedna(boja));
        }

        private void cip(Context c, String tekst, @ColorRes int pozadina, @ColorRes int boja) {
            TextView t = new TextView(c);
            t.setText(tekst);
            t.setTextSize(12.5f);
            t.setTextColor(Izgled.boja(c, boja));
            t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
            t.setBackground(Izgled.oblik(c, Izgled.boja(c, pozadina), 7, 0, 0));
            int h = Izgled.dp(c, 8), v = Izgled.dp(c, 3);
            t.setPadding(h, v, h, v);
            oznake.addView(t);
        }
    }
}
