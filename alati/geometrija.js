/*
 * Tačna linija kolovoza svakog autoputa za aplikaciju (da li je vozač NA autoputu ili na putu pored njega).
 *
 *   node alati/geometrija.js
 *
 * Ulaz: keš alati/osm/deu.json (putevi highway=motorway sa ref-om). Svaki put se uprošćava
 * (Douglas–Peucker, 15 m) i upisuje u app/src/main/assets/geometrija.bin (big-endian, za DataInputStream):
 *   int brojAutoputeva; za svaki: UTF oznaka ("A8"), int brojPuteva; za svaki put: int n, pa n × (int lat·1e5, int lon·1e5)
 */
'use strict';
const fs = require('fs'), path = require('path');

const ASSETS = path.join(__dirname, '..', 'app', 'src', 'main', 'assets');
const kes = require(path.join(__dirname, 'osm', 'deu.json'));
const KY = 110.57, KX = la => 111.32 * Math.cos(la * Math.PI / 180);
const TOL = 0.015;

const ids = new Set(fs.readdirSync(path.join(ASSETS, 'autoputevi')).filter(f => f.endsWith('.json'))
  .map(f => JSON.parse(fs.readFileSync(path.join(ASSETS, 'autoputevi', f), 'utf8')).id));

function uprosti(p) {
  if (p.length < 3) return p;
  const a = p[0], b = p[p.length - 1], kx = KX(a[0]);
  let mi = -1, md = 0;
  for (let i = 1; i < p.length - 1; i++) {
    const q = p[i];
    const ax = (a[1] - q[1]) * kx, ay = (a[0] - q[0]) * KY, bx = (b[1] - q[1]) * kx, by = (b[0] - q[0]) * KY;
    const dx = bx - ax, dy = by - ay, d2 = dx * dx + dy * dy;
    const t = d2 ? Math.max(0, Math.min(1, -(ax * dx + ay * dy) / d2)) : 0;
    const d = Math.hypot(ax + t * dx, ay + t * dy);
    if (d > md) { md = d; mi = i; }
  }
  if (md <= TOL) return [a, b];
  return uprosti(p.slice(0, mi + 1)).slice(0, -1).concat(uprosti(p.slice(mi)));
}

const po = new Map();
for (const w of Object.values(kes.mw)) {
  if (w.tr) continue;
  const refs = (w.ref || '').split(';').map(r => r.replace(/\s+/g, '').toUpperCase()).filter(r => ids.has(r));
  if (!refs.length) continue;
  const p = w.n.map(x => kes.k[x]).filter(Boolean);
  if (p.length < 2) continue;
  const u = uprosti(p);
  for (const r of refs) { if (!po.has(r)) po.set(r, []); po.get(r).push(u); }
}

const delovi = [];
const int = v => { const b = Buffer.alloc(4); b.writeInt32BE(v); return b; };
const utf = s => { const t = Buffer.from(s, 'utf8'), b = Buffer.alloc(2); b.writeUInt16BE(t.length); return Buffer.concat([b, t]); };
delovi.push(int(po.size));
let tacaka = 0;
for (const [id, putevi] of [...po.entries()].sort()) {
  delovi.push(utf(id), int(putevi.length));
  for (const p of putevi) {
    const b = Buffer.alloc(4 + p.length * 8);
    b.writeInt32BE(p.length, 0);
    p.forEach((q, i) => { b.writeInt32BE(Math.round(q[0] * 1e5), 4 + i * 8); b.writeInt32BE(Math.round(q[1] * 1e5), 8 + i * 8); });
    delovi.push(b);
    tacaka += p.length;
  }
}
const izlaz = Buffer.concat(delovi);
fs.writeFileSync(path.join(ASSETS, 'geometrija.bin'), izlaz);
console.log(`autoputeva: ${po.size}/${ids.size}, tačaka: ${tacaka}, ${(izlaz.length / 1024).toFixed(0)} KB`);
const bez = [...ids].filter(i => !po.has(i));
if (bez.length) console.log('bez geometrije:', bez.join(', '));
