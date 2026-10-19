import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { S, CUES, TAPS, AGENT_LINES, VERIFY_ROWS, DURATION, MUSIC } from '../src/timeline.js';

const SR = 48000;
const N = Math.ceil(DURATION * SR);
const TAU = Math.PI * 2;
const OUT = path.resolve(fileURLToPath(new URL(`../out/${process.env.STEM ? 'stem-' + process.env.STEM : 'score'}.wav`, import.meta.url)));

const buf = () => ({ L: new Float32Array(N), R: new Float32Array(N) });
const drums = buf();
const harm = buf();
const sfx = buf();
const send = buf();

const midi = (m) => 440 * Math.pow(2, (m - 69) / 12);
const clamp = (v, a, b) => Math.min(b, Math.max(a, v));
const idx = (t) => Math.round(t * SR);

let seed = 20261017;
const rnd = () => {
  seed = (seed * 1664525 + 1013904223) >>> 0;
  return seed / 4294967296;
};
const noise = () => rnd() * 2 - 1;

function add(bus, i, l, r, gain = 1) {
  if (i < 0 || i >= N) return;
  bus.L[i] += l * gain;
  bus.R[i] += r * gain;
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

function pad(notes, t0, dur, gain, bright = 1) {
  const i0 = idx(t0);
  const len = Math.ceil((dur + 2.6) * SR);
  const atk = Math.min(2.2, dur * 0.35) * SR;
  const rel = 2.4 * SR;
  const voices = [];
  notes.forEach((n) => {
    const f = midi(n);
    voices.push({ f: f * 0.9986, side: 0, p: rnd() });
    voices.push({ f: f * 1.0014, side: 1, p: rnd() });
  });

  let lpL = 0;
  let lpR = 0;
  const cut = clamp(0.085 * bright, 0.02, 0.16);
  for (let s = 0; s < len; s++) {
    const i = i0 + s;
    if (i >= N) break;
    let env = 1;
    if (s < atk) env = s / atk;
    if (s > len - rel) env = Math.max(0, (len - s) / rel);
    env *= env;
    let l = 0;
    let r = 0;
    for (const v of voices) {
      v.p += v.f / SR;
      if (v.p >= 1) v.p -= 1;
      const saw = 2 * v.p - 1 - polyblep(v.p, v.f / SR);
      if (v.side === 0) l += saw;
      else r += saw;
    }
    l = (l / Math.max(1, notes.length)) * 0.8;
    r = (r / Math.max(1, notes.length)) * 0.8;
    lpL += (l - lpL) * cut;
    lpR += (r - lpR) * cut;
    const g = gain * env;
    add(harm, i, lpL, lpR, g);
    add(send, i, lpL, lpR, g * 0.55);
  }
}

function sub(t0, dur, f, gain) {
  const i0 = idx(t0);
  const len = Math.ceil(dur * SR);
  const atk = 0.02 * SR;
  const rel = 0.35 * SR;
  let p = 0;
  for (let s = 0; s < len; s++) {
    const i = i0 + s;
    if (i >= N) break;
    p += f / SR;
    if (p >= 1) p -= 1;
    let env = 1;
    if (s < atk) env = s / atk;
    if (s > len - rel) env = (len - s) / rel;
    const v = (Math.sin(p * TAU) + Math.sin(p * 2 * TAU) * 0.22) * env;
    add(harm, i, v * gain, v * gain);
  }
}

function drone(t0, t1) {
  const i0 = idx(t0);
  const i1 = idx(t1);
  const f1 = midi(33);
  const f2 = midi(40);
  let p1 = 0;
  let p2 = 0;
  let lp = 0;
  for (let i = i0; i < i1; i++) {
    const t = (i - i0) / SR;
    const span = (i1 - i0) / SR;
    const env = clamp(t / 9, 0, 1) * clamp((span - t) / 3, 0, 1);
    p1 += f1 / SR;
    p2 += f2 / SR;
    if (p1 >= 1) p1 -= 1;
    if (p2 >= 1) p2 -= 1;
    const raw = Math.sin(p1 * TAU) * 0.6 + (2 * p2 - 1) * 0.05;
    lp += (raw - lp) * 0.008;
    add(harm, i, lp * env * 0.26, lp * env * 0.26);
    add(send, i, lp * env * 0.06, lp * env * 0.06);
  }
}

function kick(t0, gain) {
  const i0 = idx(t0);
  const len = Math.ceil(0.42 * SR);
  let p = 0;
  for (let s = 0; s < len; s++) {
    const i = i0 + s;
    if (i >= N) break;
    const t = s / SR;
    const f = 118 * Math.exp(-t * 26) + 46;
    p += f / SR;
    if (p >= 1) p -= 1;
    const env = Math.exp(-t * 9.5);
    const v = Math.sin(p * TAU) * env;
    add(drums, i, v * gain, v * gain);
    if (t < 0.012) {
      const c = noise() * (1 - t / 0.012) * 0.25;
      add(drums, i, c * gain, c * gain);
    }
  }
  addSend(t0, 0.06, 0.06);
}

function hat(t0, gain, open = false) {
  const i0 = idx(t0);
  const len = Math.ceil((open ? 0.14 : 0.045) * SR);
  let hp = 0;
  let prev = 0;
  for (let s = 0; s < len; s++) {
    const i = i0 + s;
    if (i >= N) break;
    const t = s / SR;
    const n = noise();
    hp = 0.86 * (hp + n - prev);
    prev = n;
    const env = Math.exp(-t * (open ? 26 : 90));
    const v = hp * env;
    add(drums, i, v * gain * 0.9, v * gain);
  }
}

function rim(t0, gain) {
  const i0 = idx(t0);
  const len = Math.ceil(0.09 * SR);
  let p = 0;
  for (let s = 0; s < len; s++) {
    const i = i0 + s;
    if (i >= N) break;
    const t = s / SR;
    p += 1600 / SR;
    if (p >= 1) p -= 1;
    const env = Math.exp(-t * 55);
    const v = (Math.sin(p * TAU) * 0.6 + noise() * 0.4) * env;
    add(drums, i, v * gain, v * gain);
  }
  addSend(t0, 0.05, 0.12);
}

function pluck(t0, note, gain, dur = 1.6) {
  const i0 = idx(t0);
  const len = Math.ceil(dur * SR);
  const f = midi(note);
  let p = 0;
  let p2 = 0;
  for (let s = 0; s < len; s++) {
    const i = i0 + s;
    if (i >= N) break;
    const t = s / SR;
    p += f / SR;
    p2 += (f * 2.005) / SR;
    if (p >= 1) p -= 1;
    if (p2 >= 1) p2 -= 1;
    const env = Math.exp(-t * 3.2) * (1 - Math.exp(-t * 400));
    const v = (Math.sin(p * TAU) * 0.8 + Math.sin(p2 * TAU) * 0.12) * env;
    add(harm, i, v * gain * 0.75, v * gain);
    add(send, i, v * gain * 0.5, v * gain * 0.5);
  }
}

function bell(t0, note, gain, dur = 3.2) {
  const i0 = idx(t0);
  const len = Math.ceil(dur * SR);
  const f = midi(note);
  let p = 0;
  let q = 0;
  for (let s = 0; s < len; s++) {
    const i = i0 + s;
    if (i >= N) break;
    const t = s / SR;
    p += f / SR;
    q += (f * 2.76) / SR;
    if (p >= 1) p -= 1;
    if (q >= 1) q -= 1;
    const env = Math.exp(-t * 1.12) * (1 - Math.exp(-t * 220));
    const v = (Math.sin(p * TAU) + Math.sin(q * TAU) * 0.16) * env;
    add(harm, i, v * gain * 0.6, v * gain * 0.6);
    add(send, i, v * gain * 0.9, v * gain * 0.9);
  }
}

function addSend(t0, gainL, gainR) {
  const i0 = idx(t0);
  const len = Math.ceil(0.2 * SR);
  for (let s = 0; s < len; s++) {
    const i = i0 + s;
    if (i >= N) break;
    const env = Math.exp(-(s / SR) * 30);
    add(send, i, env * gainL, env * gainR);
  }
}

function impact(t, gain = 1) {
  const i0 = idx(t);
  const len = Math.ceil(2.4 * SR);
  let p = 0;
  for (let s = 0; s < len; s++) {
    const i = i0 + s;
    if (i >= N) break;
    const tt = s / SR;
    const f = 92 * Math.exp(-tt * 3.2) + 32;
    p += f / SR;
    if (p >= 1) p -= 1;
    const env = Math.exp(-tt * 2.4);
    const v = Math.sin(p * TAU) * env;
    add(sfx, i, v * gain * 0.55, v * gain * 0.55);
    add(send, i, v * gain * 0.2, v * gain * 0.2);
    if (tt < 0.09) {
      const e = 1 - tt / 0.09;
      const c = noise() * e * e * 0.18 * gain;
      add(sfx, i, c, c);
    }
  }
}

function whoosh(t, dur, gain = 0.5) {
  const i0 = idx(t);
  const len = Math.ceil(dur * SR);
  let low = 0;
  let band = 0;
  for (let s = 0; s < len; s++) {
    const i = i0 + s;
    if (i >= N) break;
    const u = s / len;
    const env = Math.sin(Math.PI * Math.pow(u, 0.7)) ** 2;
    const f = 320 + 2600 * u * u;
    const F = 2 * Math.sin((Math.PI * f) / SR);
    const n = noise();
    low += F * band;
    const high = n - low - 0.55 * band;
    band += F * high;
    const v = band * 0.45 * env;
    const pan = 0.5 + 0.5 * Math.sin(u * Math.PI);
    add(sfx, i, v * gain * pan, v * gain * (1 - pan));
  }
}

function riser(t, dur, gain = 0.3) {
  const i0 = idx(t);
  const len = Math.ceil(dur * SR);
  let hp = 0;
  let prev = 0;
  for (let s = 0; s < len; s++) {
    const i = i0 + s;
    if (i >= N) break;
    const u = s / len;
    const n = noise();
    hp = 0.55 * (hp + n - prev);
    prev = n;
    const env = u * u;
    const v = hp * env * (0.4 + u * 0.6);
    add(sfx, i, v * gain, v * gain);
  }
}

function tick(t, gain = 0.25, freq = 1400, decay = 50) {
  const i0 = idx(t);
  const len = Math.ceil(0.03 * SR);
  let p = 0;
  for (let s = 0; s < len; s++) {
    const i = i0 + s;
    if (i >= N) break;
    const tt = s / SR;
    p += freq / SR;
    if (p >= 1) p -= 1;
    const env = Math.exp(-tt * decay);
    const v = (Math.sin(p * TAU) * 0.6 + noise() * 0.25) * env;
    add(sfx, i, v * gain, v * gain);
  }
}

function thock(t, gain = 0.35) {
  const i0 = idx(t);
  const len = Math.ceil(0.12 * SR);
  let p = 0;
  for (let s = 0; s < len; s++) {
    const i = i0 + s;
    if (i >= N) break;
    const tt = s / SR;
    p += (180 - tt * 500) / SR;
    if (p >= 1) p -= 1;
    if (p < 0) p += 1;
    const env = Math.exp(-tt * 38);
    const v = Math.sin(p * TAU) * env;
    add(sfx, i, v * gain, v * gain);
  }
}

function shimmer(t0, gain = 0.2) {
  const i0 = idx(t0);
  const len = Math.ceil(6 * SR);
  const fs = [2093, 2637, 3136];
  fs.forEach((f, k) => {
    let p = rnd();
    for (let s = 0; s < len; s++) {
      const i = i0 + s;
      if (i >= N) break;
      const tt = s / SR;
      p += f / SR;
      if (p >= 1) p -= 1;
      const env = Math.exp(-tt * (0.8 - k * 0.15)) * (1 - Math.exp(-tt * 6));
      const trem = 0.7 + 0.3 * Math.sin(TAU * 0.7 * tt + k);
      const v = Math.sin(p * TAU) * env * trem;
      add(sfx, i, v * gain * (0.5 - k * 0.12), v * gain * (0.5 + k * 0.12));
    }
  });
  addSend(t0, 0.2, 0.2);
}

// ---------------------------------------------------------------- arrangement

const CHORDS = {
  Am: [45, 57, 60, 64],
  F: [41, 53, 57, 60],
  C: [48, 55, 60, 64],
  G: [43, 55, 59, 62],
  Fmaj7: [41, 57, 60, 64],
  Am9: [45, 60, 64, 71],
};

const progression = [
  [S.title, 'Am'],
  [41.4, 'F'],
  [45.7, 'C'],
  [50.0, 'G'],
  [54.3, 'Am'],
  [58.6, 'F'],
  [62.9, 'G'],
  [67.1, 'Am'],
  [71.4, 'F'],
  [75.7, 'C'],
  [80.0, 'G'],
  [84.3, 'Am'],
  [88.6, 'F'],
  [S.result, 'Fmaj7'],
  [108.6, 'C'],
  [113.4, 'G'],
  [118.2, 'Am'],
  [S.honesty, 'F'],
  [S.outro, 'F'],
  [140.2, 'C'],
  [145.8, 'G'],
  [149.2, 'Am9'],
];

drone(0, 30.0);

for (let i = 0; i < progression.length; i++) {
  const [t, name] = progression[i];
  const next = progression[i + 1] ? progression[i + 1][0] : DURATION;
  const dur = Math.min(next - t + 2.4, 10.5);
  const notes = CHORDS[name];
  const low = t > 120 ? 0.75 : 1;
  pad(notes, t, Math.max(2.6, dur), 0.56 * low, t > 100 ? 0.82 : 1.15);
  sub(t, Math.min(dur, 7.0), midi(notes[0] - 12), 0.19 * low);
}

// heartbeat under the cold open and the honesty beat
for (let t = 1.0; t < 37.0; t += 5.714) {
  sub(t, 0.9, midi(33), 0.10);
  sub(t + 0.36, 0.55, midi(33), 0.05);
}
for (let t = 124.6; t < 133.4; t += 5.714) sub(t, 1.2, midi(33), 0.08);

// drums through the build
for (let t = 37.1; t < 101.8; t += 1.4285) {
  const bar = Math.floor((t - 37.1) / 5.714);
  kick(t, bar < 2 ? 0.15 : 0.19);
}

for (let t = 48.55; t < 101.8; t += 0.714) {
  const accent = Math.abs(((t - 48.55) / 0.714) % 2) < 0.1;
  hat(t, accent ? 0.045 : 0.028, accent && Math.floor(t) % 7 === 0);
}

for (let t = 72.6; t < 101.8; t += 1.4285) rim(t, 0.05);

// bass pulse
const roots = { Am: 45, F: 41, C: 48, G: 43, Fmaj7: 41, Am9: 45 };
for (let t = 48.55; t < 101.8; t += 0.714) {
  const chord = progression.filter(([pt]) => pt <= t).slice(-1)[0][1];
  const root = roots[chord];
  const up = Math.round((t - 48.55) / 0.714) % 8 === 6;
  pluck(t, up ? root + 12 : root - 12, 0.055, 0.5);
}

// sparse motifs
bell(27.2, 76, 0.10, 3.4);
bell(30.2, 72, 0.07, 3.0);
[103.6, 105.4, 107.4, 110.2, 112.4].forEach((t, i) => {
  bell(t, [65, 69, 72, 76, 72][i], 0.11, 3.6);
});
[135.6, 139.0, 143.4, 147.4, 150.2].forEach((t, i) => {
  bell(t, [53, 60, 67, 72, 69][i], 0.09, 4.0);
});

// ---------------------------------------------------------------- sound design

for (const c of CUES) {
  if (c.k === 'impact') impact(c.t, c.g);
  else if (c.k === 'whoosh') whoosh(c.t, 0.55, c.g);
  else if (c.k === 'tick') tick(c.t, 0.18 * c.g, 1500, 60);
  else if (c.k === 'rise') riser(c.t, c.d || 4, 0.16);
  else if (c.k === 'open') {
    impact(c.t, c.g);
    whoosh(c.t, 0.9, 0.35);
  } else if (c.k === 'swell') {
    sub(c.t, 2.4, midi(33), 0.06 * c.g);
  } else if (c.k === 'shimmer') shimmer(c.t, 0.16 * c.g);
  else if (c.k === 'end') {
    impact(c.t, c.g);
    sub(c.t, 6.0, midi(29), 0.1);
  }
}

TAPS.forEach((t, i) => {
  thock(t, 0.22);
  tick(t, 0.05, 2200 + i * 40, 80);
});

for (const line of AGENT_LINES) {
  if (!line.cps) continue;
  const abs = S.agent + line.at;
  const chars = line.text.length;
  const n = Math.min(10, Math.max(3, Math.round(chars / 5)));
  for (let k = 0; k < n; k++) {
    tick(abs + (chars / line.cps) * (k / n), 0.035, 900 + (k % 3) * 120, 90);
  }
}

VERIFY_ROWS.forEach((row, i) => {
  if (row.big) {
    tick(S.verify + row.at, 0.09, 880, 24);
    tick(S.verify + row.at + 0.07, 0.06, 1320, 30);
  } else {
    tick(S.verify + row.at, 0.045, 1500 + i * 45, 70);
  }
});

// typing in the verify command
for (let k = 0; k < 12; k++) tick(S.verify + 0.3 + k * 0.05, 0.02, 1100, 90);

// ---------------------------------------------------------------- ducks and fx

// duck the harmonic bus on every kick (subtle, 180 ms)
const duck = new Float32Array(N).fill(1);
for (let t = 37.1; t < 101.8; t += 1.4285) {
  const i0 = idx(t);
  const len = Math.ceil(0.22 * SR);
  for (let s = 0; s < len; s++) {
    const i = i0 + s;
    if (i >= N) break;
    const u = s / len;
    duck[i] = Math.min(duck[i], 1 - 0.35 * Math.exp(-u * 5));
  }
}

// reverb: 4 combs + 2 allpass, stereo
function reverb(input, spread) {
  const out = buf();
  const combs = [1557, 1617, 1491, 1422];
  const fb = 0.78;
  const damp = 0.28;
  for (let ch = 0; ch < 2; ch++) {
    const src = ch === 0 ? input.L : input.R;
    const dst = ch === 0 ? out.L : out.R;
    const lines = combs.map((d) => ({ d: d + (ch ? spread : 0), buf: new Float32Array(d + 16), i: 0, lp: 0 }));
    for (let n = 0; n < N; n++) {
      let acc = 0;
      for (const l of lines) {
        const y = l.buf[l.i];
        l.lp += (y - l.lp) * (1 - damp);
        l.buf[l.i] = src[n] + l.lp * fb;
        l.i = (l.i + 1) % l.d;
        acc += y;
      }
      dst[n] = acc * 0.25;
    }
    const ap = [[225, 0.5], [556, 0.5]];
    for (const [d, g] of ap) {
      const b = new Float32Array(d + 1);
      let p = 0;
      for (let n = 0; n < N; n++) {
        const bufv = b[p];
        const x = dst[n] + bufv * g;
        b[p] = x;
        dst[n] = bufv - x * g;
        p = (p + 1) % (d + 1);
      }
    }
  }
  return out;
}

const wet = reverb(send, 23);

// ---------------------------------------------------------------- master

const master = buf();
let peak = 0;
for (let i = 0; i < N; i++) {
  const t = i / SR;
  let fade = 1;
  if (t < 1.2) fade = t / 1.2;
  if (t > DURATION - 2.4) fade = Math.max(0, (DURATION - t) / 2.4);
  const l = (drums.L[i] + harm.L[i] * duck[i] + sfx.L[i] + wet.L[i] * 0.55) * fade;
  const r = (drums.R[i] + harm.R[i] * duck[i] + sfx.R[i] + wet.R[i] * 0.55) * fade;
  if (process.env.STEM === 'drums') { master.L[i] = drums.L[i]; master.R[i] = drums.R[i]; }
  else if (process.env.STEM === 'harm') { master.L[i] = harm.L[i]; master.R[i] = harm.R[i]; }
  else if (process.env.STEM === 'sfx') { master.L[i] = sfx.L[i]; master.R[i] = sfx.R[i]; }
  else {
    master.L[i] = Math.tanh(l * 1.05) * 0.92;
    master.R[i] = Math.tanh(r * 1.05) * 0.92;
  }
  peak = Math.max(peak, Math.abs(master.L[i]), Math.abs(master.R[i]));
}

const norm = peak > 0 ? 0.72 / peak : 1;
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
console.log(`wrote ${OUT}  ${DURATION}s  peak-norm ${norm.toFixed(2)}`);
