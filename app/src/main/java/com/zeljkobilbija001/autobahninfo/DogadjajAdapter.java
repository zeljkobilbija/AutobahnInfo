package com.zeljkobilbija001.autobahninfo;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Lista saobraćajnih poruka; dodir otvara/zatvara pun opis. */
public class DogadjajAdapter extends RecyclerView.Adapter<DogadjajAdapter.Red> {

    public interface NaMapi {
        void otvori(Dogadjaj d);
    }

    private final List<Dogadjaj> stavke = new ArrayList<>();
    private final Set<String> otvoreni = new HashSet<>();
    private final NaMapi naMapi;

    public DogadjajAdapter(NaMapi naMapi) {
        this.naMapi = naMapi;
    }

    @SuppressWarnings("NotifyDataSetChanged")
    public void postavi(List<Dogadjaj> l) {
        stavke.clear();
        stavke.addAll(l);
        notifyDataSetChanged();
    }

    @StringRes
    public static int naziv(int vrsta) {
        switch (vrsta) {
            case Dogadjaj.ZATVORENO: return R.string.vrsta_zatvoreno;
            case Dogadjaj.UDES: return R.string.vrsta_udes;
            case Dogadjaj.OPASNOST: return R.string.vrsta_opasnost;
            case Dogadjaj.KOLONA: return R.string.vrsta_kolona;
            case Dogadjaj.TRAKA: return R.string.vrsta_traka;
            case Dogadjaj.SPORO: return R.string.vrsta_sporo;
            case Dogadjaj.PRILAZ: return R.string.vrsta_prilaz;
            default: return R.string.vrsta_ostalo;
        }
    }

    /** {pozadina, tekst} oznake vrste. */
    private static int[] boje(int vrsta) {
        switch (vrsta) {
            case Dogadjaj.ZATVORENO:
            case Dogadjaj.UDES: return new int[]{0xFFC62828, 0xFFFFFFFF};
            case Dogadjaj.OPASNOST:
            case Dogadjaj.KOLONA: return new int[]{0xFFE65100, 0xFFFFFFFF};
            case Dogadjaj.TRAKA:
            case Dogadjaj.SPORO: return new int[]{0xFFF9C11B, 0xFF2A1F00};
            default: return new int[]{0xFF6B7588, 0xFFFFFFFF};
        }
    }

    @NonNull
    @Override
    public Red onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Red(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dogadjaj, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Red h, int position) {
        Dogadjaj d = stavke.get(position);
        Context c = h.itemView.getContext();

        int[] b = boje(d.vrsta);
        h.vrsta.setText(naziv(d.vrsta));
        h.vrsta.setTextColor(b[1]);
        h.vrsta.setBackground(Izgled.oblik(c, b[0], 6, 0, 0));

        h.kasnjenje.setVisibility(d.kasnjenjeMin > 0 ? View.VISIBLE : View.GONE);
        h.kasnjenje.setText(c.getString(R.string.stanje_kasnjenje, d.kasnjenjeMin));

        h.deonica.setText(d.deonica.isEmpty() ? d.smer : d.deonica);
        h.smer.setText(d.smer);
        h.smer.setVisibility(d.deonica.isEmpty() || d.smer.isEmpty() ? View.GONE : View.VISIBLE);

        if (d.pocetak > 0) {
            h.vreme.setVisibility(View.VISIBLE);
            // danas: samo vreme; ranije: i datum
            int f = DateUtils.FORMAT_SHOW_TIME;
            if (!DateUtils.isToday(d.pocetak)) f |= DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH;
            h.vreme.setText(c.getString(R.string.stanje_od, DateUtils.formatDateTime(c, d.pocetak, f)));
        } else {
            h.vreme.setVisibility(View.GONE);
        }

        boolean otvoren = otvoreni.contains(d.id);
        h.opis.setText(d.opis);
        h.opis.setVisibility(otvoren && !d.opis.isEmpty() ? View.VISIBLE : View.GONE);
        h.karta.setVisibility(otvoren && d.imaKoordinate() ? View.VISIBLE : View.GONE);
        h.karta.setOnClickListener(v -> naMapi.otvori(d));

        h.itemView.setOnClickListener(v -> {
            if (!otvoreni.remove(d.id)) otvoreni.add(d.id);
            int i = h.getBindingAdapterPosition();
            if (i != RecyclerView.NO_POSITION) notifyItemChanged(i);
        });
    }

    @Override
    public int getItemCount() {
        return stavke.size();
    }

    static class Red extends RecyclerView.ViewHolder {
        final TextView vrsta, kasnjenje, deonica, smer, vreme, opis, karta;

        Red(View v) {
            super(v);
            vrsta = v.findViewById(R.id.vrsta);
            kasnjenje = v.findViewById(R.id.kasnjenje);
            deonica = v.findViewById(R.id.deonica);
            smer = v.findViewById(R.id.smer);
            vreme = v.findViewById(R.id.vreme);
            opis = v.findViewById(R.id.opis);
            karta = v.findViewById(R.id.karta);
        }
    }
}
