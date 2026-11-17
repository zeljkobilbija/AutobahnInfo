/*
 * Zvanična kilometraža (brojevi sa plavih tabli pored autoputa) za podatke aplikacije.
 *
 *   node alati/kilometraza.js <milestone.json>
 *
 * Ulaz: tačke highway=milestone iz germany-latest.osm.pbf ({la, lo, d, r}), keš alati/osm/deu.json (linije autoputeva).
 * Svaka tabla se dodeli autoputu čiji je kolovoz do 40 m od nje (ili po svom ref-u), projektuje se na liniju
 * deonice u assets/autoputevi/*.json (kroz izlaze i odmorišta) i dobija "naš" kilometar. Za svaku deonicu
 * se upisuje "kal": [[naš km, km sa table, smer ±1], …] – samo table koje se slažu sa susedima (do 4 km):
 * zvanični km = km table + smer · (naš km − naš km table). Tako se hvataju i obrnut smer i resetovanje kilometraže.
 */
'use strict';
const fs = require('fs'), path = require('path');

const ASSETS = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'autoputevi');
const kes = require(path.join(__dirname, 'osm', 'deu.json'));
const table = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'))
  .map(t => ({ ...t, km: parseFloat(String(t.d).replace(',', '.')) }))
  .filter(t => isFinite(t.km));

const norm = r => r.replace(/\s+/g, '').toUpperCase();
const KX = la => 111.32 * Math.cos(la * Math.PI / 180), KY = 110.57;

// ---- 1. Segmenti kolovoza po oznaci autoputa, u mreži ćelija ~1 km ----
const mreza = new Map();
const celija = (la, lo) => Math.floor(la * 100) + '_' + Math.floor(lo * 70);
for (const w of Object.values(kes.mw)) {
  if (w.tr) continue;
  const refs = (w.ref || '').split(';').map(norm).filter(r => /^A\d+$/.test(r));
  if (!refs.length) continue;
  for (let i = 1; i < w.n.length; i++) {
    const a = kes.k[w.n[i - 1]], b = kes.k[w.n[i]];
    if (!a || !b) continue;
    const s = { a, b, refs };
    const c = celija((a[0] + b[0]) / 2, (a[1] + b[1]) / 2);
    if (!mreza.has(c)) mreza.set(c, []);
    mreza.get(c).push(s);
  }
}

/** Udaljenost (km) tačke od segmenta. */
function dSeg(la, lo, a, b) {
  const kx = KX(la);
  const ax = (a[1] - lo) * kx, ay = (a[0] - la) * KY, bx = (b[1] - lo) * kx, by = (b[0] - la) * KY;
  const dx = bx - ax, dy = by - ay, d2 = dx * dx + dy * dy;
  const t = d2 ? Math.max(0, Math.min(1, -(ax * dx + ay * dy) / d2)) : 0;
  return Math.hypot(ax + t * dx, ay + t * dy);
}

// ---- 2. Tabla → autoput(evi) ----
const poAutoputu = new Map();
let dodeljeno = 0;
for (const t of table) {
  let refs = null;
  if (/^A\s*\d+$/i.test(t.r || '')) refs = [norm(t.r)];
  else {
    const a = Math.floor(t.la * 100), b = Math.floor(t.lo * 70);
    let naj = 0.04, nr = null;
    for (let i = -1; i <= 1; i++) for (let j = -1; j <= 1; j++)
      for (const s of mreza.get((a + i) + '_' + (b + j)) || []) {
        const d = dSeg(t.la, t.lo, s.a, s.b);
        if (d < naj) { naj = d; nr = s.refs; }
      }
    refs = nr;
  }
  if (!refs) continue;
  dodeljeno++;
  for (const r of refs) { if (!poAutoputu.has(r)) poAutoputu.set(r, []); poAutoputu.get(r).push(t); }
}
console.log(`table: ${table.length}, uz autoput: ${dodeljeno}`);

/**
 * Ceo autoput u jednom komadu: sve deonice se spajaju u jednu listu, redom kojim idu.
 * Deonica okrenuta suprotno od prethodne se okreće (km, strana, plus/minus). Kilometri se nastavljaju
 * (dodaje se vazdušna razdaljina preko prekida). Gde je autoput stvarno prekinut (razmak > 5 km),
 * upisuje se "prekidi": [[km kraja, km nastavka]] – aplikacija tu ne spaja liniju autoputa.
 * Ponovno pokretanje ne menja već spojeno.
 */
function spojiDeonice(d) {
  if (d.sekcije.length < 2) return;
  const tac = si => d.stavke.filter(x => x.si === si && x.la != null);
  const dist = (a, b) => Math.hypot((a.la - b.la) * KY, (a.lo - b.lo) * KX(a.la));
  const krajevi = si => { const a = tac(si); if (!a.length) return null;
    return [a.reduce((m, x) => x.km < m.km ? x : m), a.reduce((m, x) => x.km > m.km ? x : m)]; };
  const duzina = si => Math.max(0, ...d.stavke.filter(x => x.si === si).map(x => x.km));
  const prekidi = [];
  let pomak = 0, prethodniKraj = null;
  const nova = { ...d.sekcije[0] };
  for (let si = 0; si < d.sekcije.length; si++) {
    const s = d.sekcije[si], L = duzina(si);
    let kr = krajevi(si), okreni = false;
    // okreće se ako mu je kraj bliži kraju prethodne nego početak
    if (prethodniKraj && kr && dist(prethodniKraj, kr[1]) < dist(prethodniKraj, kr[0])) okreni = true;
    let razmak = 0;
    if (si > 0) {
      const pocetak = kr ? (okreni ? kr[1] : kr[0]) : null;
      razmak = prethodniKraj && pocetak ? dist(prethodniKraj, pocetak) : 0;
      if (razmak > 5) prekidi.push([+pomak.toFixed(1), +(pomak + razmak).toFixed(1)]);
      pomak += razmak;
      nova.do = s.do;
      nova.plus = okreni ? s.minus : s.plus;
      spojeno.push(`${d.id}: + ${s.od} – ${s.do}${okreni ? ' (okrenuta)' : ''}, razmak ${razmak.toFixed(1)} km`);
    }
    for (const x of d.stavke) {
      if (x.si !== si) continue;
      const k = okreni ? L - x.km : x.km;
      x.km = +(pomak + k).toFixed(1);
      if (okreni) x.s = (x.s || '+') === '+' ? '-' : '+';
      x.si = 0;
    }
    if (kr) prethodniKraj = okreni ? kr[0] : kr[1];
    pomak += L;
  }
  delete nova.kal;
  if (prekidi.length) nova.prekidi = prekidi;
  d.sekcije = [nova];
  d.stavke.sort((x, y) => x.km - y.km);
}
const spojeno = [];

// ---- 3. Po deonici: projekcija na liniju kroz izlaze/odmorišta i provera sa susedima ----

let ukupnoStavki = 0, pokriveno = 0;
for (const f of fs.readdirSync(ASSETS).filter(f => f.endsWith('.json'))) {
  const p = path.join(ASSETS, f);
  const d = JSON.parse(fs.readFileSync(p, 'utf8'));
  spojiDeonice(d);
  const tabl = poAutoputu.get(norm(d.id)) || [];
  d.sekcije.forEach((sek, si) => {
    delete sek.kal;
    const tacke = d.stavke.filter(x => x.si === si && x.la != null).sort((x, y) => x.km - y.km);
    if (tacke.length < 2) return;
    const parovi = [];
    for (const t of tabl) {
      let naj = 1e9, km = 0;
      const kx = KX(t.la);
      for (let i = 1; i < tacke.length; i++) {
        const A = tacke[i - 1], B = tacke[i];
        if ((sek.prekidi || []).some(q => q[0] >= A.km - 0.05 && q[1] <= B.km + 0.05)) continue;
        const ax = (A.lo - t.lo) * kx, ay = (A.la - t.la) * KY, bx = (B.lo - t.lo) * kx, by = (B.la - t.la) * KY;
        const dx = bx - ax, dy = by - ay, d2 = dx * dx + dy * dy;
        const u = d2 ? Math.max(0, Math.min(1, -(ax * dx + ay * dy) / d2)) : 0;
        const dd = Math.hypot(ax + u * dx, ay + u * dy);
        if (dd < naj) { naj = dd; km = A.km + u * (B.km - A.km); }
      }
      // linija kroz izlaze je gruba: tabla mora biti blizu nje, ali ne i na samom kolovozu
      if (naj <= 2.5) parovi.push([km, t.km]);
    }
    parovi.sort((x, y) => x[0] - y[0]);
    // Smer table: glasaju susedne table na DRUGIM mestima (0,3–6 km dalje) čiji nagib (Δ km table / Δ naš km)
    // je blizu ±1; tabla važi ako se bar jedna takva slaže sa njom (±0,5 km). Table sa obe strane kolovoza
    // na istom mestu ne glasaju (nemaju nagib).
    const kal = [];
    for (const [n, z] of parovi) {
      let plus = 0, minus = 0;
      for (const [qn, qz] of parovi) {
        const dn = qn - n;
        if (Math.abs(dn) < 0.3 || Math.abs(dn) > 15) continue;
        const nagib = (qz - z) / dn;
        if (Math.abs(nagib - 1) <= 0.2 || Math.abs((qz - z) - dn) <= 0.5) plus++;
        else if (Math.abs(nagib + 1) <= 0.2 || Math.abs((qz - z) + dn) <= 0.5) minus++;
      }
      if (plus === 0 && minus === 0) continue;
      if (plus === minus) continue;
      kal.push([+n.toFixed(2), +z.toFixed(1), plus > minus ? 1 : -1]);
    }
    // bliske duplikate (oba kolovoza) svesti na jednu
    const cisto = kal.filter((q, i) => i === 0 || Math.abs(q[0] - kal[i - 1][0]) > 0.15 || q[1] !== kal[i - 1][1]);
    if (cisto.length) sek.kal = cisto;
    for (const x of d.stavke.filter(x => x.si === si)) {
      ukupnoStavki++;
      if (cisto.some(q => Math.abs(q[0] - x.km) <= 5)) pokriveno++;
    }
  });
  fs.writeFileSync(p, JSON.stringify(d));
}
console.log('spojene deonice:\n  ' + spojeno.join('\n  '));
console.log(`mesta sa zvaničnim km (tabla do 5 km): ${pokriveno} / ${ukupnoStavki} (${(100 * pokriveno / ukupnoStavki).toFixed(0)} %)`);
