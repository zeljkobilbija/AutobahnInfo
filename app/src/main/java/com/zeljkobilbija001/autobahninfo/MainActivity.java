package com.zeljkobilbija001.autobahninfo;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    /** Kartica koja se okrenula pri otvaranju; pri povratku se vraća nazad. */
    private View okrenutaKartica;
    private boolean otvaranjeUToku;
    private ImageButton dugmePrivatnosti;
    private MaterialButton voznjaDugme;
    private View voznjaTrazi;
    private android.widget.TextView voznjaOpis;
    private AutoputAdapter adapterAutoputeva;
    /** Autoput čiji je štit narandžast (Vožnja: vozač je na njemu). */
    private String obojeniAutoput;

    /** Dozvole za režim vožnje: precizna lokacija (obavezno) i obaveštenja (Android 13+). */
    private final ActivityResultLauncher<String[]> dozvoleVoznje = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), this::posleDozvola);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Splash mora pre super.onCreate()
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        RecyclerView lista = findViewById(R.id.listaAutoputeva);
        lista.setLayoutManager(new GridLayoutManager(this, 2));
        adapterAutoputeva = new AutoputAdapter(Autoputevi.SVE, this::otvori);
        lista.setAdapter(adapterAutoputeva);

        findViewById(R.id.dugmePodeli).setOnClickListener(v -> podeliAplikaciju());
        // provera stanja na praćenim autoputevima (ako ih ima) ostaje zakazana
        Obavestenja.otvorena(this, savedInstanceState == null); // i posle potpunog zatvaranja: aktuelne vesti nazad

        voznjaDugme = findViewById(R.id.voznjaDugme);
        voznjaTrazi = findViewById(R.id.voznjaTrazi);
        voznjaOpis = findViewById(R.id.voznjaOpis);
        // proba (samo debug verzija): dodir na karticu "Vožnja" otvara dnevnik
        if (Dnevnik.ukljucen(this)) {
            findViewById(R.id.voznja).setOnClickListener(v ->
                    startActivity(new Intent(this, DnevnikActivity.class)));
        }
        voznjaDugme.setOnClickListener(v -> {
            if (VoznjaService.radi) VoznjaService.zaustavi(this);
            else pokreniVoznju();
        });

        // Privatnost (GDPR) i baner: prozor za saglasnost se prikazuje samo kad je potreban
        dugmePrivatnosti = findViewById(R.id.dugmePrivatnosti);
        dugmePrivatnosti.setOnClickListener(v ->
                Reklame.prikaziPodesavanjaPrivatnosti(this, this::posleSaglasnosti));
        Reklame.prikupiSaglasnost(this, this::posleSaglasnosti);

        // Kupovina „Bez reklama“: dugme dok nije kupljeno; promena stanja (kupovina,
        // vraćanje na novom telefonu) ponovo iscrta ekran, pa baner nestane ili se pojavi
        View dugmeBezReklama = findViewById(R.id.dugmeBezReklama);
        dugmeBezReklama.setVisibility(Kupovina.bezReklama(this) ? View.GONE : View.VISIBLE);
        dugmeBezReklama.setOnClickListener(v -> Kupovina.kupi(this, () -> runOnUiThread(() -> {
            Toast.makeText(this, R.string.kupovina_hvala, Toast.LENGTH_LONG).show();
            recreate();
        })));
        Kupovina.proveri(this, () -> runOnUiThread(this::recreate));
    }

    private void posleSaglasnosti() {
        Reklame.prikaziBaner(this, findViewById(R.id.reklamaKontejner));
        dugmePrivatnosti.setVisibility(Reklame.trebaDugmePrivatnosti(this) ? View.VISIBLE : View.GONE);
    }

    // ---------- Režim "Vožnja" ----------

    private boolean ima(String dozvola) {
        return ContextCompat.checkSelfPermission(this, dozvola) == PackageManager.PERMISSION_GRANTED;
    }

    private void pokreniVoznju() {
        List<String> traziti = new ArrayList<>();
        if (!ima(Manifest.permission.ACCESS_FINE_LOCATION)) {
            traziti.add(Manifest.permission.ACCESS_FINE_LOCATION);
            traziti.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33 && !ima(Manifest.permission.POST_NOTIFICATIONS)) {
            traziti.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (traziti.isEmpty()) {
            pokreniAkoLokacija();
        } else {
            dozvoleVoznje.launch(traziti.toArray(new String[0]));
        }
    }

    private void posleDozvola(Map<String, Boolean> rezultat) {
        if (ima(Manifest.permission.ACCESS_FINE_LOCATION)) {
            pokreniAkoLokacija();
        } else {
            // jasan prozor umesto sitne poruke na dnu: bez dozvole Vožnja ne može da radi
            prozorLokacije(R.string.voznja_bez_dozvole_poruka,
                    new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.fromParts("package", getPackageName(), null)));
        }
    }

    /** Dozvola postoji; Vožnja se pokreće samo ako je lokacija na telefonu uključena. */
    private void pokreniAkoLokacija() {
        android.location.LocationManager lm = getSystemService(android.location.LocationManager.class);
        if (lm != null && !androidx.core.location.LocationManagerCompat.isLocationEnabled(lm)) {
            prozorLokacije(R.string.voznja_gps_iskljucen_poruka,
                    new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            return;
        }
        VoznjaService.pokreni(this);
        osveziVoznju();
    }

    /** Prozor "Vožnja ne može da se pokrene" sa dugmetom koje otvara podešavanja. */
    private void prozorLokacije(int poruka, Intent podesavanja) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.voznja_ne_moze_naslov)
                .setMessage(poruka)
                .setPositiveButton(R.string.stanje_podesavanja, (d, w) -> {
                    try {
                        startActivity(podesavanja);
                    } catch (Exception ignored) { }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Da li je Vožnja već jednom sama otvorila ekran autoputa (od poslednjeg uključenja). */
    private static boolean voznjaOtvorila;

    private void osveziVoznju() {
        boolean radi = VoznjaService.radi;
        voznjaDugme.setText(radi ? R.string.voznja_zaustavi : R.string.voznja_pokreni);
        String opis = VoznjaService.opisStanja;
        String tekst = radi && !opis.isEmpty() ? opis : getString(R.string.voznja_opis);
        if (Dnevnik.ukljucen(this)) tekst += "\nDNEVNIK › (dodir na karticu)";
        voznjaOpis.setText(tekst);
        voznjaOpis.setTextColor(radi && VoznjaService.upozorenje ? Izgled.boja(this, R.color.ovde)
                : Izgled.boja(this, R.color.plava_svetla));
        // kružić dok Vožnja traži autoput ili smer
        voznjaTrazi.setVisibility(radi && VoznjaService.trazi ? View.VISIBLE : View.GONE);
        String sad = VoznjaService.sadAutoput;
        // Vožnja je našla autoput i mesto na njemu: jednom po uključenju sama otvara taj autoput
        // (ekran autoputa ide do reda "Vi ste ovde"); povratak na početni ekran posle toga ne vraća nazad
        if (!radi) voznjaOtvorila = false;
        else if (sad != null && !voznjaOtvorila && !isFinishing()) {
            voznjaOtvorila = true;
            startActivity(AutoputActivity.namera(this, sad));
        }
        if (!java.util.Objects.equals(sad, obojeniAutoput)) {
            obojeniAutoput = sad;
            adapterAutoputeva.notifyDataSetChanged();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        VoznjaService.postaviSlusaoca(this, this::osveziVoznju);
        osveziVoznju();
    }

    @Override
    protected void onStop() {
        VoznjaService.skiniSlusaoca(this);
        super.onStop();
    }

    /** Standardni Android meni za deljenje: kratka poruka i link ka aplikaciji na Play prodavnici. */
    private void podeliAplikaciju() {
        String link = "https://play.google.com/store/apps/details?id=" + getPackageName();
        Intent slanje = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
                .putExtra(Intent.EXTRA_TEXT, getString(R.string.podeli_poruka, link));
        startActivity(Intent.createChooser(slanje, getString(R.string.podeli_naslov)));
    }

    /** Kartica se okrene oko vertikalne ose, pa se otvara ekran autoputa koji se "dookrene". */
    private void otvori(Autoput a, View kartica) {
        if (!a.dostupan()) {
            Toast.makeText(this, getString(R.string.uskoro_poruka, a.oznaka()), Toast.LENGTH_SHORT).show();
            return;
        }
        if (otvaranjeUToku) return;
        otvaranjeUToku = true;
        okrenutaKartica = kartica;
        kartica.setCameraDistance(9000 * getResources().getDisplayMetrics().density);
        kartica.animate()
                .rotationY(90f)
                .scaleX(1.08f)
                .scaleY(1.08f)
                .setDuration(280)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> Reklame.posleOglasa(this, () -> {
                    // svako treće otvaranje (najviše jednom u 3 min, ne u Vožnji) prvo interstitial, pa autoput
                    Intent i = AutoputActivity.namera(this, a.id)
                            .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    startActivity(i);
                    otvaranjeUToku = false;
                }))
                .start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Povratak sa ekrana autoputa: kartica se okrene nazad na svoje mesto
        View k = okrenutaKartica;
        okrenutaKartica = null;
        if (k != null) {
            k.setRotationY(-90f);
            k.animate()
                    .rotationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(380)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }
}
