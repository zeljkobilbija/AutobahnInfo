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
    private android.widget.TextView voznjaOpis;

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
        lista.setAdapter(new AutoputAdapter(Autoputevi.SVE, this::otvori));

        findViewById(R.id.dugmePodeli).setOnClickListener(v -> podeliAplikaciju());
        // provera stanja na praćenim autoputevima (ako ih ima) ostaje zakazana
        Obavestenja.zakazi(this);

        voznjaDugme = findViewById(R.id.voznjaDugme);
        voznjaOpis = findViewById(R.id.voznjaOpis);
        voznjaDugme.setOnClickListener(v -> {
            if (VoznjaService.radi) VoznjaService.zaustavi(this);
            else pokreniVoznju();
        });

        // Privatnost (GDPR) i baner: prozor za saglasnost se prikazuje samo kad je potreban
        dugmePrivatnosti = findViewById(R.id.dugmePrivatnosti);
        dugmePrivatnosti.setOnClickListener(v ->
                Reklame.prikaziPodesavanjaPrivatnosti(this, this::posleSaglasnosti));
        Reklame.prikupiSaglasnost(this, this::posleSaglasnosti);
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
            VoznjaService.pokreni(this);
            osveziVoznju();
        } else {
            dozvoleVoznje.launch(traziti.toArray(new String[0]));
        }
    }

    private void posleDozvola(Map<String, Boolean> rezultat) {
        if (ima(Manifest.permission.ACCESS_FINE_LOCATION)) {
            VoznjaService.pokreni(this);
            osveziVoznju();
        } else {
            Snackbar.make(findViewById(R.id.main), R.string.voznja_dozvola, Snackbar.LENGTH_LONG).show();
        }
    }

    private void osveziVoznju() {
        boolean radi = VoznjaService.radi;
        voznjaDugme.setText(radi ? R.string.voznja_zaustavi : R.string.voznja_pokreni);
        String opis = VoznjaService.opisStanja;
        voznjaOpis.setText(radi && !opis.isEmpty() ? opis : getString(R.string.voznja_opis));
    }

    @Override
    protected void onStart() {
        super.onStart();
        VoznjaService.postaviSlusaoca(this::osveziVoznju);
        osveziVoznju();
    }

    @Override
    protected void onStop() {
        VoznjaService.postaviSlusaoca(null);
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
                .withEndAction(() -> {
                    Intent i = AutoputActivity.namera(this, a.id)
                            .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    startActivity(i);
                    otvaranjeUToku = false;
                })
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
