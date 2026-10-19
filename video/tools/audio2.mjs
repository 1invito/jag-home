import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { DUR2, CUES2 } from '../src/timeline2.js';

const SR = 48000;
const N = Math.ceil(DUR2 * SR);
const TAU = Math.PI * 2;
const OUT = path.resolve(fileURLToPath(new URL('../out/score2.wav', import.meta.url)));
const BPM = 120;
const BEAT = 60 / BPM;
const BAR = BEAT * 4;

const bus = () => ({ L: new Float32Array(N), R: new Float32Array(N) });
const drums = bus();
const harm = bus();
const sfx = bus();
const send = bus();

const midi = (m) => 440 * Math.pow(2, (m - 69) / 12);
const clamp = (v, a, b) => Math.min(b, Math.max(a, v));
const idx = (t) => Math.round(t * SR);

let seed = 424242;
const rnd = () => {
  seed = (seed * 1664525 + 1013904223) >>> 0;
  return seed / 4294967296;
};
const noise = () => rnd() * 2 - 1;

function add(b, i, l, r, g = 1) {
  if (i < 0 || i >= N) return;
  b.L[i] += l * g;
  b.R[i] += r * g;
}

function polyblep(t, dt) {
  if (t < dt) {
    t /= dt;
    return t + t - t * t - 1;
  }
  if (t > 1 - dt) {
    t = (t - 1) / dt;
    return t * t + t + t + 1;
  }
  return 0;
}

function kick(t0, g = 1) {
  const i0 = idx(t0);
  const len = Math.ceil(0.34 * SR);
  let p = 0;
  for (let s = 0; s < len; s++) {
    const i = i0 + s;
    if (i >= N) break;
    const t = s / SR;
    const f = 132 * Math.exp(-t * 30) + 47;
    p += f / SR;
    if (p >= 1) p -= 1;
    const env = Math.exp(-t * 8.5);
    let v = Math.sin(p * TAU) * env;
    if (t < 0.008) v += noise() * (1 - t / 0.008) * 0.35;
    add(drums, i, v * g, v * g);
  }
}

function clap(t0, g = 1) {
  const i0 = idx(t0);
  const len = Math.ceil(0.22 * SR);
  let hp = 0;
  let prev = 0;
  for (let s = 0; s < len; s++) {
    const i = i0 + s;
    if (i >= N) break;
    const t = s / SR;
    const n = noise();
    hp = 0.72 * (hp + n - prev);
    prev = n;
    let env = Math.exp(-t * 30);
    if (t > 0.012 && t < 0.03) env = Math.sin(((t - 0.012) / 0.018) * Math.PI) * 0.7;
    else if (t >= 0.03) env *= 0.9;
    const v = hp * env * 0.5 + Math.sin(TAU * 190 * t) * Math.exp(-t * 40) * 0.15;
    add(drums, i, v * g, v * g);
  }
  addSend(t0, 0.05, 0.12);
}

function hat(t0, g = 1, open = false) {
  const i0 = idx(t0);
  const len = Math.ceil((open ? 0.09 : 0.028) * SR);
  let hp = 0;
  let prev = 0;
  for (let s = 0; s < len; s++) {
    const i = i0 + s;
    if (i >= N) break;
    const t = s / SR;
    const n = noise();
    hp = 0.9 * (hp + n - prev);
    prev = n;
    const env = Math.exp(-t * (open ? 42 : 120));
    const v = hp * env;
    add(drums, i, v * g * 0.85, v * g);
  }
}

function bass(t0, note, dur, g = 1) {
  const i0 = idx(t0);
  const len = Math.ceil((dur + 0.03) * SR);
  const f = midi(note);
  let p = 0;
  let lp = 0;
  for (let s = 0; s < len; s++) {
    const i = i0 + s;
    if (i >= N) break;
    const t = s / SR;
    p += f / SR;
    if (p >= 1) p -= 1;
    const saw = 2 * p - 1 - polyblep(p, f / SR);
    lp += (saw - lp) * 0.09;
    let env = 1;
    if (t < 0.006) env = t / 0.006;
    if (s > len - 0.03 * SR) env *= (len - s) / (0.03 * SR);
    add(harm, i, lp * env * g, lp * env * g);
  }
}

function stab(t0, notes, g = 1, dur = 0.22) {
  const i0 = idx(t0);
  const len = Math.ceil((dur + 0.25) * SR);
  const vs = [];
  notes.forEach((n) => {
    vs.push({ f: midi(n) * 0.997, p: rnd() });
    vs.push({ f: midi(n) * 1.003, p: rnd() });
  });
  let lpL = 0;
  let lpR = 0;
  for (let s = 0; s < len; s++) {
    const i = i0 + s;
    if (i >= N) break;
    const t = s / SR;
    let l = 0;
    let r = 0;
    for (const v of vs) {
      v.p += v.f / SR;
      if (v.p >= 1) v.p -= 1;
      const saw = 2 * v.p - 1 - polyblep(v.p, v.f / SR);
      l += saw;
      r += saw;
    }
    const cut = 0.22 + 0.28 * Math.exp(-t * 14);
    lpL += (l / vs.length - lpL) * cut;
    lpR += (r / vs.length - lpR) * cut;
    const env = Math.exp(-t * 9) * (1 - Math.exp(-t * 700));
    add(harm, i, lpL * env * g, lpR * env * g);
    add(send, i, lpL * env * g * 0.6, lpR * env * g * 0.6);
  }
}

function pluck(t0, note, g = 1, dur = 0.4, pan = 0) {
  const i0 = idx(t0);
  const len = Math.ceil(dur * SR);
  const f = midi(note);
  let p = 0;
  for (let s = 0; s < len; s++) {
    const i = i0 + s;
    if (i >= N) break;
    const t = s / SR;
    p += f / SR;
    if (p >= 1) p -= 1;
    const env = Math.exp(-t * 12) * (1 - Math.exp(-t * 900));
    const v = (Math.sin(p * TAU) * 0.8 + (2 * p - 1) * 0.1) * env;
    const l = pan <= 0 ? v : v * 0.6;
    const r = pan >= 0 ? v : v * 0.6;
    add(harm, i, l * g, r * g);
    add(send, i, v * g * 0.4, v * g * 0.4);
  }
}

function pad(notes, t0, dur, g = 1, bright = 1) {
  const i0 = idx(t0);
  const len = Math.ceil((dur + 0.6) * SR);
  const vs = [];
  notes.forEach((n) => {
    vs.push({ f: midi(n) * 0.998, side: 0, p: rnd() });
    vs.push({ f: midi(n) * 1.002, side: 1, p: rnd() });
  });
  let lpL = 0;
  let lpR = 0;
  for (let s = 0; s < len; s++) {
    const i = i0 + s;
    if (i >= N) break;
    const t = s / SR;
    let l = 0;
    let r = 0;
    for (const v of vs) {
      v.p += v.f / SR;
      if (v.p >= 1) v.p -= 1;
      const saw = 2 * v.p - 1 - polyblep(v.p, v.f / SR);
      if (v.side === 0) l += saw;
      else r += saw;
    }
    const cut = clamp(0.09 * bright, 0.02, 0.5);
    lpL += (l / notes.length - lpL) * cut;
    lpR += (r / notes.length - lpR) * cut;
    let env = 1;
    if (t < 0.08) env = t / 0.08;
    if (s > len - 0.5 * SR) env *= (len - s) / (0.5 * SR);
    add(harm, i, lpL * env * g, lpR * env * g);
    add(send, i, lpL * env * g * 0.5, lpR * env * g * 0.5);
  }
}

function sub(t0, dur, note, g = 1) {
  const i0 = idx(t0);
  const len = Math.ceil(dur * SR);
  const f = midi(note);
  let p = 0;
  for (let s = 0; s < len; s++) {
    const i = i0 + s;
    if (i >= N) break;
    const t = s / SR;
    p += f / SR;
    if (p >= 1) p -= 1;
    let env = 1;
    if (t < 0.01) env = t / 0.01;
    if (s > len - 0.12 * SR) env *= (len - s) / (0.12 * SR);
    add(harm, i, Math.sin(p * TAU) * env * g, Math.sin(p * TAU) * env * g);
  }
}

function addSend(t0, gl, gr) {
  const i0 = idx(t0);
  const len = Math.ceil(0.16 * SR);
  for (let s = 0; s < len; s++) {
    const i = i0 + s;
    if (i >= N) break;
    const env = Math.exp(-(s / SR) * 35);
    add(send, i, env * gl, env * gr);
  }
}

function staticBurst(t0, dur, g = 1) {
  const i0 = idx(t0);
  const len = Math.ceil(dur * SR);
  let hp = 0;
  let prev = 0;
  for (let s = 0; s < len; s++) {
    const i = i0 + s;
    if (i >= N) break;
    const t = s / SR;
    const n = noise();
    hp = 0.5 * (hp + n - prev);
    prev = n;
    const env = Math.min(1, t * 60) * Math.min(1, (dur - t) * 12);
    add(sfx, i, hp * env * g, hp * env * g);
  }
}

function click(t0, g = 1, f = 1600) {
  const i0 = idx(t0);
  const len = Math.ceil(0.03 * SR);
  let p = 0;
  for (let s = 0; s < len; s++) {
    const i = i0 + s;
    if (i >= N) break;
    const t = s / SR;
    p += f / SR;
    if (p >= 1) p -= 1;
    const env = Math.exp(-t * 140);
    const v = (Math.sin(p * TAU) * 0.6 + noise() * 0.4) * env;
    add(sfx, i, v * g, v * g);
  }
}

function whoosh(t0, dur, g = 1) {
  const i0 = idx(t0);
  const len = Math.ceil(dur * SR);
  let low = 0;
  let band = 0;
  for (let s = 0; s < len; s++) {
    const i = i0 + s;
    if (i >= N) break;
    const u = s / len;
    const f = 250 + 4200 * u * u;
    const F = 2 * Math.sin((Math.PI * f) / SR);
    const n = noise();
    low += F * band;
    const high = n - low - 0.5 * band;
    band += F * high;
    const env = Math.sin(Math.PI * Math.pow(u, 0.6)) ** 2;
    add(sfx, i, band * env * g * 0.6, band * env * g * 0.6);
  }
}

function riser(t0, dur, g = 1) {
  const i0 = idx(t0);
  const len = Math.ceil(dur * SR);
  let hp = 0;
  let prev = 0;
  let p = 0;
  for (let s = 0; s < len; s++) {
    const i = i0 + s;
    if (i >= N) break;
    const u = s / len;
    const n = noise();
    hp = 0.6 * (hp + n - prev);
    prev = n;
    p += (120 + 900 * u * u) / SR;
    if (p >= 1) p -= 1;
    const tone = Math.sin(p * TAU) * 0.25;
    const env = u * u;
    add(sfx, i, (hp + tone) * env * g, (hp + tone) * env * g);
  }
}

function impact(t0, g = 1) {
  const i0 = idx(t0);
  const len = Math.ceil(1.2 * SR);
  let p = 0;
  for (let s = 0; s < len; s++) {
    const i = i0 + s;
    if (i >= N) break;
    const t = s / SR;
    const f = 110 * Math.exp(-t * 6) + 34;
    p += f / SR;
    if (p >= 1) p -= 1;
    const env = Math.exp(-t * 3.2);
    add(sfx, i, Math.sin(p * TAU) * env * g * 0.7, Math.sin(p * TAU) * env * g * 0.7);
    if (t < 0.05) {
      const c = noise() * (1 - t / 0.05) * g * 0.3;
      add(sfx, i, c, c);
    }
  }
  addSend(t0, 0.2, 0.2);
}

function blip(t0, g = 1, f = 1480) {
  const i0 = idx(t0);
  const len = Math.ceil(0.09 * SR);
  let p = 0;
  for (let s = 0; s < len; s++) {
    const i = i0 + s;
    if (i >= N) break;
    const t = s / SR;
    p += f / SR;
    if (p >= 1) p -= 1;
    const env = Math.exp(-t * 34);
    add(sfx, i, Math.sin(p * TAU) * env * g, Math.sin(p * TAU) * env * g);
  }
}

function chime(t0, g = 1) {
  const notes = [72, 76, 79, 84];
  notes.forEach((n, k) => {
    const i0 = idx(t0 + k * 0.035);
    const len = Math.ceil(1.4 * SR);
    let p = 0;
    const f = midi(n);
    for (let s = 0; s < len; s++) {
      const i = i0 + s;
      if (i >= N) break;
      const t = s / SR;
      p += f / SR;
      if (p >= 1) p -= 1;
      const env = Math.exp(-t * 2.6) * (1 - Math.exp(-t * 300));
      const v = Math.sin(p * TAU) * env * 0.18;
      add(sfx, i, v * g, v * g);
    }
  });
  addSend(t0, 0.15, 0.15);
}

// ---------------------------------------------------------------- arrangement

const CH = {
  Am9: [45, 57, 60, 64, 67],
  Fmaj7: [41, 53, 57, 60],
  Cmaj7: [48, 52, 55, 59],
  G6: [43, 55, 59, 62],
};
const ROOT = { Am9: 33, Fmaj7: 29, Cmaj7: 36, G6: 31 };
const PROG = ['Am9', 'Am9', 'Fmaj7', 'Cmaj7', 'G6', 'Am9', 'Fmaj7', 'Cmaj7', 'G6', 'Am9', 'Fmaj7', 'G6', 'Am9', 'Fmaj7'];

for (let bar = 0; bar < 14; bar++) {
  const t = bar * BAR;
  const ch = PROG[bar];
  const notes = CH[ch];
  const root = ROOT[ch];

  sub(t, BAR, root, bar < 2 ? 0.07 : 0.12);
  stab(t + 1.75, notes.slice(1, 4), bar < 2 ? 0.2 : 0.55);
  stab(t + 3.25, notes.slice(1, 4), bar < 2 ? 0.14 : 0.4);

  if (bar >= 2 && bar < 9) pad(notes, t, BAR, 0.2);
  if (bar >= 9) pad(notes, t, BAR, 0.24);
  if (bar >= 1 && bar < 13) pad(notes.map((n) => n + 12), t, BAR, 0.1, 3.2);

  if (bar >= 1) {
    for (let b = 0; b < 4; b++) {
      const bt = t + b * BEAT;
      if (bar === 7 && b >= 2) continue;
      kick(bt, b === 0 ? 0.42 : 0.36);
      if (b === 1 || b === 3) clap(bt, 0.55);
    }
    for (let s = 0; s < 16; s++) {
      const st = t + s * 0.125;
      if (bar === 7 && s >= 8) continue;
      const open = s % 4 === 2;
      hat(st, s % 2 === 0 ? 0.4 : 0.2, open);
    }
    const pattern = [0, 0, 12, 0, 0, 7, 0, 12, 0, 0, 12, 0, 0, 7, 12, 0];
    for (let s = 0; s < 16; s++) {
      if (bar === 7 && s >= 8) continue;
      const note = root + 12 + pattern[s];
      bass(t + s * 0.125, note, 0.1, s % 4 === 0 ? 0.24 : 0.17);
    }
  }
}

// intro riser + snare roll into the drop
riser(0.0, 2.0, 0.28);
for (let i = 0; i < 8; i++) {
  const tt = 1.0 + i * 0.125;
  staticBurst(tt, 0.035, 0.18 + i * 0.03);
}
// drop
stab(2.0, CH.Am9.slice(1), 0.22, 0.5);
sub(2.0, 1.0, 33, 0.22);

// breakdown before the result card
stab(15.0, CH.Cmaj7.slice(1), 0.1, 0.6);

// resolve into the end card
stab(25.5, [57, 60, 64, 67, 71], 0.28, 1.0);
sub(25.5, 2.2, 29, 0.24);
pad([41, 57, 60, 64, 67, 71], 25.5, 2.4, 0.1);
chime(25.7, 0.5);

// arp from bar 3, sparkles
for (let bar = 3; bar < 12; bar++) {
  const ch = PROG[bar];
  const notes = CH[ch];
  if (bar === 7) continue;
  for (let s = 0; s < 8; s++) {
    const tt = bar * BAR + s * 0.25;
    const note = notes[(s + bar) % notes.length] + 24;
    pluck(tt, note, s % 2 === 0 ? 0.28 : 0.2, 0.3, s % 2 ? 0.6 : -0.6);
  }
}

// lead motif in the peak
const lead = [
  [8.0, 72, 0.22],
  [8.5, 76, 0.22],
  [9.0, 79, 0.42],
  [10.0, 76, 0.22],
  [10.5, 72, 0.42],
  [12.0, 74, 0.22],
  [12.5, 77, 0.22],
  [13.0, 81, 0.5],
  [23.7, 76, 0.22],
  [24.0, 79, 0.22],
  [24.4, 84, 0.55],
];
for (const [t, n, d] of lead) pluck(t, n, 0.2, d, 0);

// ---------------------------------------------------------------- cues

for (const c of CUES2) {
  if (c.k === 'static') staticBurst(c.t, 0.3, 0.5 * c.g);
  else if (c.k === 'impact') impact(c.t, c.g);
  else if (c.k === 'click') click(c.t, 0.12 * c.g, 1800);
  else if (c.k === 'tick') click(c.t, 0.1 * c.g, 1200 + c.t * 20);
  else if (c.k === 'whoosh') whoosh(c.t, 0.4, c.g);
  else if (c.k === 'riser') riser(c.t, c.d || 1, 0.22 * c.g);
  else if (c.k === 'drop') {
    impact(c.t, c.g);
    sub(c.t, 0.8, 29, 0.18 * c.g);
  } else if (c.k === 'blip') blip(c.t, 0.09 * c.g, 1300 + c.t * 30);
  else if (c.k === 'chime') chime(c.t, 0.5 * c.g);
  else if (c.k === 'swell') sub(c.t, 1.4, 33, 0.06 * c.g);
  else if (c.k === 'end') impact(c.t, c.g);
}

// type ticks for the prompt
for (let i = 0; i < 30; i++) click(9.1 + i * 0.04, 0.03, 1700);

// ---------------------------------------------------------------- mix

const duck = new Float32Array(N).fill(1);
for (let t = 0; t < 25.5; t += BEAT) {
  const i0 = idx(t);
  const len = Math.ceil(0.2 * SR);
  for (let s = 0; s < len; s++) {
    const i = i0 + s;
    if (i >= N) break;
    duck[i] = Math.min(duck[i], 1 - 0.55 * Math.exp(-(s / len) * 5));
  }
}

function reverb(input) {
  const out = bus();
  const combs = [1557, 1617, 1491, 1422];
  for (let ch = 0; ch < 2; ch++) {
    const src = ch === 0 ? input.L : input.R;
    const dst = ch === 0 ? out.L : out.R;
    const lines = combs.map((d) => ({ d: d + (ch ? 23 : 0), buf: new Float32Array(d + 16), i: 0, lp: 0 }));
    for (let n = 0; n < N; n++) {
      let acc = 0;
      for (const l of lines) {
        const y = l.buf[l.i];
        l.lp += (y - l.lp) * 0.7;
        l.buf[l.i] = src[n] + l.lp * 0.76;
        l.i = (l.i + 1) % l.d;
        acc += y;
      }
      dst[n] = acc * 0.25;
    }
  }
  return out;
}

const wet = reverb(send);
const master = bus();
let peak = 0;
for (let i = 0; i < N; i++) {
  const t = i / SR;
  let fade = 1;
  if (t > DUR2 - 0.8) fade = Math.max(0, (DUR2 - t) / 0.8);
  const l = (drums.L[i] + harm.L[i] * duck[i] + sfx.L[i] + wet.L[i] * 0.5) * fade;
  const r = (drums.R[i] + harm.R[i] * duck[i] + sfx.R[i] + wet.R[i] * 0.5) * fade;
  master.L[i] = Math.tanh(l * 1.2) * 0.94;
  master.R[i] = Math.tanh(r * 1.2) * 0.94;
  peak = Math.max(peak, Math.abs(master.L[i]), Math.abs(master.R[i]));
}

const norm = peak > 0 ? 0.78 / peak : 1;
const wav = Buffer.alloc(44 + N * 4);
wav.write('RIFF', 0);
wav.writeUInt32LE(36 + N * 4, 4);
wav.write('WAVE', 8);
wav.write('fmt ', 12);
wav.writeUInt32LE(16, 16);
wav.writeUInt16LE(1, 20);
wav.writeUInt16LE(2, 22);
wav.writeUInt32LE(SR, 24);
wav.writeUInt32LE(SR * 4, 28);
wav.writeUInt16LE(4, 32);
wav.writeUInt16LE(16, 34);
wav.write('data', 36);
wav.writeUInt32LE(N * 4, 40);
let o = 44;
for (let i = 0; i < N; i++) {
  wav.writeInt16LE(Math.round(clamp(master.L[i] * norm, -1, 1) * 32767), o);
  wav.writeInt16LE(Math.round(clamp(master.R[i] * norm, -1, 1) * 32767), o + 2);
  o += 4;
}
fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, wav);
console.log(`wrote ${OUT} ${DUR2}s`);
