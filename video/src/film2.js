import * as D from './design.js';
import {
  S2,
  PLATES,
  HOOK,
  STATS,
  STEPS,
  PASS,
  VERIFY,
  AGENT_PROMPT,
  AGENT_PLAN,
  DUR2,
} from './timeline2.js';

const { W, H, C } = D;
const SERIF = 'Georgia, "Times New Roman", serif';
const SANS = '"Helvetica Neue", Helvetica, Arial, sans-serif';
const MONO = '"Geist Mono", "JetBrains Mono", Menlo, monospace';

const cream = '#F7F1E4';
const ink = '#0A0B0D';
const blue = '#2A49FF';
const coral = '#FF5A3C';
const acid = '#E8FF4A';
const mint = '#5BF0B0';

let noise = [];
let vign = null;

export function init2() {
  for (let k = 0; k < 3; k++) {
    const n = document.createElement('canvas');
    n.width = 960;
    n.height = 540;
    const nx = n.getContext('2d');
    const data = nx.createImageData(960, 540);
    let seed = 9871 + k * 613;
    const rnd = () => {
      seed = (seed * 1664525 + 1013904223) >>> 0;
      return seed / 4294967296;
    };
    for (let i = 0; i < data.data.length; i += 4) {
      const v = 104 + Math.floor(rnd() * 48);
      data.data[i] = v;
      data.data[i + 1] = v;
      data.data[i + 2] = v;
      data.data[i + 3] = 255;
    }
    nx.putImageData(data, 0, 0);
    noise.push(n);
  }
  vign = document.createElement('canvas');
  vign.width = W;
  vign.height = H;
  const v = vign.getContext('2d');
  const g = v.createRadialGradient(W / 2, H * 0.5, H * 0.35, W / 2, H * 0.5, H * 0.95);
  g.addColorStop(0, 'rgba(0,0,0,0)');
  g.addColorStop(1, 'rgba(0,0,0,0.16)');
  v.fillStyle = g;
  v.fillRect(0, 0, W, H);
}

function grain(ctx, t, alpha = 0.055) {
  const idx = Math.floor(t * 18) % 3;
  ctx.save();
  ctx.globalCompositeOperation = 'overlay';
  ctx.globalAlpha = alpha;
  ctx.drawImage(noise[idx], 0, 0, W, H);
  ctx.restore();
}

function bars(ctx) {
  const cols = ['#F7F1E4', '#E8FF4A', '#5BF0B0', '#5BD6F0', '#FF5A3C', '#FF3D8B', '#2A49FF'];
  const w = W / cols.length;
  cols.forEach((c, i) => {
    ctx.fillStyle = c;
    ctx.fillRect(i * w, 0, w + 1, H);
  });
  ctx.fillStyle = 'rgba(10,11,13,0.96)';
  ctx.fillRect(0, H * 0.315, W, H * 0.37);
}

function plate(ctx, t, name, o = {}) {
  const p = PLATES[name] || PLATES.ink;
  const { arcY = 742, arcInk = null, bloom = 1 } = o;

  if (name === 'bars') {
    bars(ctx);
    return p;
  }

  const g = ctx.createLinearGradient(0, 0, 0, H);
  g.addColorStop(0, p.a);
  g.addColorStop(1, p.b);
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, W, H);

  // horizon arc
  const r = 1500;
  const cx = W / 2;
  const cy = arcY + r;
  ctx.save();
  ctx.beginPath();
  ctx.arc(cx, cy, r, Math.PI, Math.PI * 2);
  ctx.lineTo(W, H);
  ctx.lineTo(0, H);
  ctx.closePath();
  ctx.fillStyle = arcInk || (name === 'ink' ? '#050609' : 'rgba(8,8,10,0.16)');
  ctx.fill();
  ctx.restore();

  // rim glow
  ctx.save();
  ctx.beginPath();
  ctx.arc(cx, cy, r, Math.PI * 1.001, Math.PI * 1.999);
  ctx.strokeStyle = p.arc;
  ctx.lineWidth = 3;
  ctx.globalAlpha = 0.9;
  ctx.stroke();
  ctx.restore();

  const glow = ctx.createRadialGradient(cx, arcY, 0, cx, arcY, 640);
  glow.addColorStop(0, p.arc);
  glow.addColorStop(1, 'rgba(0,0,0,0)');
  ctx.save();
  ctx.globalAlpha = 0.22 * bloom;
  ctx.globalCompositeOperation = 'screen';
  ctx.fillStyle = glow;
  ctx.fillRect(0, arcY - 640, W, 900);
  ctx.restore();

  // scanlines
  ctx.save();
  ctx.globalAlpha = name === 'ink' ? 0.03 : 0.035;
  ctx.fillStyle = '#000';
  for (let y = 0; y < H; y += 4) ctx.fillRect(0, y, W, 1);
  ctx.restore();

  return p;
}

function fit(ctx, str, size, family, weight, maxW, tracking = 0) {
  let s = size;
  while (s > 20 && D.measure(ctx, str, { size: s, family, weight, tracking }) > maxW) s -= 4;
  return s;
}

function revealWords(ctx, str, t, t0, o = {}) {
  const {
    size = 220,
    family = SERIF,
    weight = 400,
    color = ink,
    base = 620,
    stagger = 0.055,
    dur = 0.42,
    tracking = -2,
    maxW = 1680,
  } = o;
  let s = size;
  while (s > 20 && D.measure(ctx, str, { size: s, family, weight, tracking }) > maxW) s -= 4;
  const words = str.split(' ');
  const space = D.measure(ctx, ' ', { size: s, family, weight, tracking });
  const widths = words.map((w) => D.measure(ctx, w, { size: s, family, weight, tracking }));
  const total = widths.reduce((a, b) => a + b, 0) + space * (words.length - 1);
  let x = o.left != null ? o.left : W / 2 - total / 2;
  words.forEach((w, i) => {
    const p = D.prog(t, t0 + i * stagger, dur);
    if (p > 0) {
      const e = D.easeOutExpo(p);
      ctx.save();
      ctx.beginPath();
      ctx.rect(x - 10, base - s * 1.04, widths[i] + 20, s * 1.42);
      ctx.clip();
      ctx.globalAlpha = D.clamp(p * 2.6);
      ctx.translate(0, (1 - e) * s * 0.55);
      D.txt(ctx, w, x, base, { size: s, weight, family, color, tracking });
      ctx.restore();
    }
    x += widths[i] + space;
  });
}

function revealLetters(ctx, word, t, t0, o = {}) {
  const {
    size = 300,
    weight = 500,
    family = SANS,
    color = cream,
    base = 610,
    tracking = -0.04,
    stagger = 0.045,
    dur = 0.4,
  } = o;
  const track = tracking * size;
  const widths = [...word].map((ch) => D.measure(ctx, ch, { size, family, weight, tracking: track }));
  const total = widths.reduce((a, b) => a + b, 0);
  let x = W / 2 - total / 2;
  [...word].forEach((ch, i) => {
    const p = D.prog(t, t0 + i * stagger, dur);
    if (p > 0) {
      const e = D.easeOutExpo(p);
      ctx.save();
      ctx.beginPath();
      ctx.rect(x - 10, base - size * 1.02, widths[i] + 20, size * 1.34);
      ctx.clip();
      ctx.globalAlpha = D.clamp(p * 2.4);
      ctx.translate(0, (1 - e) * size * 0.7);
      D.txt(ctx, ch, x, base, { size, weight, family, color, tracking: track });
      ctx.restore();
    }
    x += widths[i];
  });
}

function drawNumber(ctx, str, cx, base, size, color, p) {
  const cellW = size * 0.62;
  const total = cellW * str.length;
  let x = cx - total / 2;
  [...str].forEach((ch, i) => {
    const d = parseInt(ch, 10);
    if (Number.isNaN(d)) {
      D.txt(ctx, ch, x + cellW / 2, base, {
        size,
        weight: 300,
        family: SANS,
        color,
        align: 'center',
        tracking: -size * 0.035,
      });
      x += cellW;
      return;
    }
    const e = D.easeOutExpo(D.clamp((p - i * 0.04) / 0.8));
    ctx.save();
    ctx.beginPath();
    ctx.rect(x - 2, base - size * 1.02, cellW + 4, size * 1.3);
    ctx.clip();
    ctx.textAlign = 'center';
    ctx.textBaseline = 'alphabetic';
    ctx.font = `300 ${size}px ${SANS}`;
    ctx.fillStyle = color;
    for (let k = 0; k < 5; k++) {
      const val = (d - 4 + k + 10) % 10;
      const yy = base + (k - 4) * size * 1.15 + (1 - e) * 4 * size * 1.15;
      if (yy > base + size * 0.4 || yy < base - size * 1.2) continue;
      ctx.fillText(String(val), x + cellW / 2, yy);
    }
    ctx.restore();
    x += cellW;
  });
}

function marquee(ctx, t, y, speed, alpha, size = 18) {
  const names = [
    'com.amazon.tv.acr',
    'com.amazon.device.metrics',
    'com.amazon.tahoe',
    'com.amazon.aca',
    'com.amazon.ceviche',
    'com.amazon.device.software.ota',
    'com.amazon.hybridadidservice',
    'com.amazon.privacypassservice',
    'com.amazon.hedwig',
    'com.amazon.tigris',
  ];
  const gap = 60;
  const widths = names.map((n) => D.measure(ctx, n, { size, family: MONO }));
  const total = widths.reduce((a, b) => a + b, 0) + gap * names.length;
  let x = -((t * speed) % total);
  ctx.save();
  ctx.globalAlpha = alpha;
  for (let pass = 0; pass < 2; pass++) {
    names.forEach((n, i) => {
      D.txt(ctx, n, x, y, { size, family: MONO, color: cream, tracking: 0 });
      x += widths[i] + gap;
    });
  }
  ctx.restore();
}

function enter(ctx, t, t0, dur = 0.2, fn, { scale = 0.1, rise = 20 } = {}) {
  const p = D.clamp((t - t0) / dur);
  if (p <= 0) return;
  const e = D.easeOutExpo(p);
  ctx.save();
  ctx.globalAlpha = D.clamp(p * 3.2);
  ctx.translate(W / 2, 600);
  const s = 1 + scale * (1 - e);
  ctx.scale(s, s);
  ctx.translate(-W / 2, -600 + (1 - e) * rise);
  fn();
  ctx.restore();
}

function serifWord(ctx, word, t, t0 = 0, size, color, maxW = 1680, base = 620) {
  const s = fit(ctx, word, size, SERIF, 400, maxW, -2);
  enter(ctx, t, t0, 0.22, () => {
    D.txt(ctx, word, W / 2, base, {
      size: s,
      weight: 400,
      family: SERIF,
      color,
      align: 'center',
      tracking: -0.02 * s,
    });
  });
}

function drawHook(ctx, card, t) {
  const p = plate(ctx, t, card.plate);
  const inkColor = card.ink || p.ink;
  if (card.plate === 'bars') {
    const wipe = D.easeOutExpo(D.prog(t, card.t, 0.14));
    ctx.fillStyle = '#0A0B0D';
    ctx.fillRect(0, 0, W, H);
    ctx.save();
    ctx.beginPath();
    ctx.rect(0, 0, W, H * wipe);
    ctx.clip();
    bars(ctx);
    ctx.restore();
    ctx.fillStyle = 'rgba(10,11,13,0.96)';
    ctx.fillRect(0, H * 0.315, W, H * 0.37);
    enter(ctx, t, card.t, 0.16, () => {
      D.txt(ctx, card.word, W / 2, 628, {
        size: fit(ctx, card.word, 300, SERIF, 400, 1600),
        family: SERIF,
        color: cream,
        align: 'center',
        tracking: -6,
      });
    }, { scale: 0.06, rise: 0 });
  } else {
    revealWords(ctx, card.word, t, card.t, {
      size: 286,
      color: inkColor,
      base: 632,
      tracking: -6,
      stagger: 0.07,
    });
  }
}

function drawDumbtv(ctx, t) {
  const p = plate(ctx, t, 'ink', { arcY: 800 });
  const p2 = D.prog(t, 0, 0.16);
  const inverted = p2 < 1;
  if (inverted) {
    ctx.fillStyle = `rgba(247,241,228,${1 - p2})`;
    ctx.fillRect(0, 0, W, H);
  }
  revealLetters(ctx, 'dumbtv', t, -0.02, {
    size: 300,
    weight: 500,
    color: inverted ? ink : p.ink,
    base: 610,
    tracking: -0.05,
    stagger: 0.016,
    dur: 0.16,
  });

  const u = D.prog(t, 0.3, 0.2);
  ctx.fillStyle = blue;
  ctx.fillRect(W / 2 - 90 * D.easeOut(u), 658, 180 * D.easeOut(u), 3);

  const q = D.prog(t, 0.42, 0.18);
  D.txt(ctx, 'NO ADS   NO TRACKING   NO ROOT', W / 2, 716, {
    size: 20,
    family: MONO,
    color: p.ink,
    align: 'center',
    alpha: D.clamp(q) * 0.8,
    tracking: 2,
  });
}

function drawStat(ctx, card, t) {
  const p = plate(ctx, t, card.plate, { arcY: 800 });
  const countP = D.easeOut(D.prog(t, card.t, 0.22));
  const n = Math.round(card.n * countP);
  const ns = String(n);
  const size = fit(ctx, '000', 400, SANS, 300, 1300, -10);
  ctx.save();
  ctx.globalAlpha = D.clamp(countP * 3);
  drawNumber(ctx, ns, W / 2, 610, size, p.ink, countP);
  ctx.restore();
  const up = D.prog(t, card.t + 0.16, 0.18);
  D.txt(ctx, card.unit, W / 2, 706 + (1 - D.easeOutExpo(up)) * 16, {
    size: 34,
    family: MONO,
    color: p.ink,
    align: 'center',
    tracking: 1,
    alpha: up * 0.78,
  });
}

function drawCredo(ctx, card, t) {
  const p = plate(ctx, t, card.plate, { arcY: 780 });
  revealWords(ctx, card.word, t, 0, {
    size: card.size || 210,
    color: p.ink,
    base: 620,
    tracking: -4,
    stagger: 0.06,
  });
}

function drawStep(ctx, card, t) {
  const p = plate(ctx, t, card.plate, { arcY: 760 });
  const pulse = D.prog(t, 0, 0.05);
  D.txt(ctx, card.n, 1720, 470, {
    size: 400,
    weight: 300,
    family: SANS,
    color: p.ink,
    align: 'right',
    tracking: -16,
    alpha: 0.07,
  });

  enter(ctx, t, card.t, 0.18, () => {
    D.txt(ctx, card.n, 180, 330, {
      size: 104,
      weight: 300,
      family: SANS,
      color: p.ink,
      tracking: -4,
    });
    D.txt(ctx, card.title, 180, 470, {
      size: 88,
      weight: 400,
      family: SERIF,
      color: p.ink,
      tracking: -2,
    });
    D.txt(ctx, card.detail, 184, 560, {
      size: card.mono ? 68 : 32,
      family: MONO,
      color: p.ink,
      tracking: card.mono ? 0 : 1,
      alpha: 0.86,
    });
  }, { scale: 0.035, rise: 16 });

  if (card.n === '01') {
    D.txt(ctx, 'TAP THE NAME 7\u00d7', 180, 640, {
      size: 22,
      family: MONO,
      color: p.ink,
      tracking: 2,
      alpha: 0.8,
    });
    for (let i = 0; i < 7; i++) {
      const at = 6.12 + i * 0.11;
      if (t < at) continue;
      const q = D.easeOut(D.prog(t, at, 0.08));
      ctx.beginPath();
      ctx.arc(192 + i * 46, 684, 13 * q, 0, Math.PI * 2);
      ctx.fillStyle = p.ink;
      ctx.fill();
    }
  }
}

function drawAgent(ctx, t) {
  const p = plate(ctx, t, 'ink', { arcY: 900, bloom: 0.6 });
  D.txt(ctx, 'THE AGENT', 180, 190, {
    size: 18,
    family: MONO,
    color: '#8FBAFF',
    tracking: 5,
  });
  D.txt(ctx, '03', 1720, 470, {
    size: 400,
    weight: 300,
    family: SANS,
    color: cream,
    align: 'right',
    tracking: -16,
    alpha: 0.05,
  });

  const typing = D.clamp((t - 0.05) / 0.9);
  const chars = Math.floor(AGENT_PROMPT.length * typing);
  const shown = AGENT_PROMPT.slice(0, chars);
  ctx.fillStyle = blue;
  ctx.fillRect(180, 318, 6, 50);
  D.txt(ctx, shown, 212, 360, {
    size: 44,
    family: MONO,
    color: p.ink,
    tracking: -0.5,
  });
  if (Math.floor(t * 3) % 2 === 0) {
    const w = D.measure(ctx, shown, { size: 44, family: MONO, tracking: -0.5 });
    ctx.fillStyle = blue;
    ctx.fillRect(216 + w, 322, 16, 46);
  }

  const planP = D.prog(t, 1.5, 0.2);
  D.txt(ctx, AGENT_PLAN, 212, 470, {
    size: 27,
    family: MONO,
    color: p.ink,
    alpha: planP * 0.62,
  });

  const doneP = D.prog(t, 2.3, 0.14);
  if (doneP > 0) {
    ctx.beginPath();
    ctx.arc(190, 590, 7, 0, Math.PI * 2);
    ctx.fillStyle = mint;
    ctx.fill();
    D.txt(ctx, 'done in 10 minutes.', 216, 602, {
      size: 27,
      family: MONO,
      color: mint,
      alpha: doneP,
    });
  }
}

function drawPass(ctx, card, t) {
  const p = PLATES[card.plate || 'ink'];
  plate(ctx, t, card.plate || 'ink', { arcY: 860, bloom: 0.8 });
  const inkColor = card.inkColor || p.ink;
  marquee(ctx, t, 300, 260, 0.10, 18);
  const e = D.easeOutExpo(D.prog(t, 0, 0.18));
  ctx.save();
  ctx.beginPath();
  ctx.rect(0, 330, W, 330);
  ctx.clip();
  ctx.globalAlpha = D.clamp(D.prog(t, 0, 0.08) * 2);
  ctx.translate(0, (1 - e) * 70);
  drawNumber(ctx, card.n, W / 2, 600, 360, inkColor, D.prog(t, 0, 0.28));
  ctx.restore();
  D.txt(ctx, card.label, W / 2, 690 + (1 - D.easeOutExpo(D.prog(t, 0.06, 0.2))) * 14, {
    size: 34,
    family: MONO,
    color: inkColor,
    align: 'center',
    tracking: 1,
    alpha: D.prog(t, 0.06, 0.2) * 0.8,
  });
  D.txt(ctx, card.tag, 180, 208, {
    size: 22,
    family: MONO,
    color: inkColor,
    tracking: 5,
    alpha: 0.95,
  });
  ctx.fillStyle = inkColor;
  ctx.globalAlpha = 0.3;
  ctx.fillRect(180, 230, 42, 1);
  ctx.globalAlpha = 1;
  ctx.restore();
}

function drawResult(ctx, t, img) {
  const enterP = D.easeOutExpo(D.prog(t, 0, 0.26));
  const push = (1 + 0.045 * D.easeOut(D.clamp(t / 3))) * (1.22 - 0.22 * enterP);
  ctx.save();
  ctx.translate(W / 2, H / 2);
  ctx.scale(push, push);
  ctx.translate(-W / 2, -H / 2);
  D.drawCover(ctx, img, 0, 0, W, H);
  ctx.restore();
  if (t < 0.08) {
    ctx.fillStyle = `rgba(247,241,228,${1 - t / 0.08})`;
    ctx.fillRect(0, 0, W, H);
  }

  const shade = ctx.createLinearGradient(0, H - 330, 0, H);
  shade.addColorStop(0, 'rgba(0,0,0,0)');
  shade.addColorStop(0.45, 'rgba(0,0,0,0.55)');
  shade.addColorStop(1, 'rgba(0,0,0,0.86)');
  ctx.fillStyle = shade;
  ctx.fillRect(0, H - 330, W, 330);

  const captions = [
    { t: 0.0, out: 0.95, text: 'This is the home screen.' },
    { t: 1.0, out: 1.95, text: '41 KB. Zero permissions. No account.' },
    { t: 2.0, out: 3.0, text: 'HOME lands here in 0.2 seconds.' },
  ];
  for (const c of captions) {
    const a = D.fade(t, c.t, 0.18, c.out, 0.12);
    if (a > 0) {
      ctx.save();
      ctx.globalAlpha = a;
      ctx.translate(0, (1 - D.easeOut(D.prog(t, c.t, 0.2))) * 14);
      revealWords(ctx, c.text, t, c.t, {
        size: 48,
        family: SANS,
        weight: 400,
        color: cream,
        base: 972,
        tracking: -0.4,
        stagger: 0.03,
        maxW: 1680,
        left: 120,
      });
      ctx.restore();
    }
  }
  ctx.fillStyle = blue;
  ctx.fillRect(120, 996, 96, 3);
}

function drawVerify(ctx, t) {
  const p = plate(ctx, t, 'ink', { arcY: 980, bloom: 0.5 });
  D.txt(ctx, 'PROOF', 150, 200, {
    size: 18,
    family: MONO,
    color: '#8FBAFF',
    tracking: 5,
  });

  VERIFY.forEach((row, i) => {
    const q = D.prog(t, row.t - 18.0, 0.12);
    if (q <= 0) return;
    const y = 320 + i * 86;
    ctx.save();
    ctx.beginPath();
    ctx.rect(0, y - 62, W, 80);
    ctx.clip();
    ctx.globalAlpha = D.easeOut(q);
    ctx.translate((1 - D.easeOutExpo(q)) * -46, 0);
    D.txt(ctx, row.host, 150, y, {
      size: 38,
      family: MONO,
      color: cream,
      tracking: -0.4,
    });
    ctx.fillStyle = 'rgba(247,241,228,0.10)';
    ctx.fillRect(150, y - 44, 1620, 1);
    D.txt(ctx, row.res, 1770, y, {
      size: 28,
      family: MONO,
      color: mint,
      align: 'right',
      tracking: 1,
    });
    ctx.restore();
  });

  const allP = D.prog(t, 2.2, 0.16);
  if (allP > 0) {
    ctx.save();
    ctx.globalAlpha = D.easeOut(allP);
    ctx.fillStyle = mint;
    ctx.fillRect(150, 640, 120 * D.easeOut(allP), 3);
    D.txt(ctx, 'all checks passed.', 150, 820, {
      size: 96,
      weight: 400,
      family: SERIF,
      color: cream,
      tracking: -1.5,
    });
    D.txt(ctx, '65 disabled  \u00b7  396 hosts  \u00b7  HOME on dumbtv', 150, 884, {
      size: 30,
      family: MONO,
      color: cream,
      alpha: 0.8,
      tracking: 0.8,
    });
    ctx.restore();
  }
}

function drawHonesty(ctx, t, card) {
  const p = plate(ctx, t, card.plate, { arcY: 800 });
  serifWord(ctx, card.word, t, 0, card.size || 108, p.ink, 1680, 610);
  if (card.cmd) {
    enter(ctx, t, 0.12, 0.2, () => {
      D.txt(ctx, card.cmd, W / 2, 706, {
        size: 34,
        family: MONO,
        color: p.ink,
        align: 'center',
        alpha: 0.9,
        tracking: 0.5,
      });
    });
  }
}

function drawCta(ctx, t, phase) {
  if (phase === 0) {
    const p = plate(ctx, t, 'cream', { arcY: 780 });
    serifWord(ctx, 'Point your agent at the repo.', t, 0, 96, p.ink, 1660, 590);
  } else if (phase === 1) {
    const p = plate(ctx, t, 'ink', { arcY: 900, bloom: 0.6 });
    const l1 = 'Read AGENTS.md.';
    const l2 = 'Set up my TV at 192.168.1.42.';
    const p1 = D.prog(t, 0, 0.12);
    D.txt(ctx, l1, W / 2, 470, {
      size: 58,
      family: MONO,
      color: cream,
      align: 'center',
      alpha: D.easeOut(p1),
      tracking: -1,
    });
    D.txt(ctx, l2, W / 2, 580, {
      size: 58,
      family: MONO,
      color: cream,
      align: 'center',
      alpha: D.easeOut(D.prog(t, 0.1, 0.12)),
      tracking: -1,
    });
    D.txt(ctx, 'one sentence \u00b7 any shell-capable agent', W / 2, 676, {
      size: 26,
      family: MONO,
      color: '#8FBAFF',
      align: 'center',
      alpha: Math.min(1, D.prog(t, 0.2, 0.12) * 1.4),
      tracking: 1.5,
    });
  } else {
    plate(ctx, t, 'cobalt', { arcY: 870 });
    const p = D.prog(t, 0, 0.16);
    ctx.save();
    ctx.globalAlpha = D.easeOut(p);
    ctx.translate(0, (1 - D.easeOutExpo(p)) * 20);
    D.txt(ctx, 'github.com/humblemuzzu/dumbtv', W / 2, 590, {
      size: fit(ctx, 'github.com/humblemuzzu/dumbtv', 76, MONO, 400, 1700, -1),
      family: MONO,
      color: cream,
      align: 'center',
      tracking: -1,
    });
    ctx.restore();
  }
}

function drawEnd(ctx, t) {
  const p = plate(ctx, t, 'ink', { arcY: 880, bloom: 1.1 });

  const e = D.easeOutExpo(D.prog(t, 0, 0.22));
  revealLetters(ctx, 'dumbtv', t, 0.0, {
    size: 190,
    weight: 500,
    color: cream,
    base: 500,
    tracking: -0.05,
    stagger: 0.03,
    dur: 0.3,
  });
  ctx.fillStyle = blue;
  ctx.fillRect(W / 2 - 160 * e, 534, 320 * e, 3);

  D.txt(ctx, 'no ads  \u00b7  no tracking  \u00b7  no root', W / 2, 606, {
    size: 26,
    family: MONO,
    color: cream,
    align: 'center',
    alpha: 0.7 * D.prog(t, 0.5, 0.2),
    tracking: 1.5,
  });
  const urlP = D.easeOut(D.prog(t, 0.58, 0.3));
  D.txt(ctx, 'github.com/humblemuzzu/dumbtv', W / 2, 704, {
    size: 54,
    family: MONO,
    color: cream,
    align: 'center',
    alpha: Math.min(1, urlP * 1.6),
    tracking: D.lerp(10, -0.5, urlP),
  });
  const uw = D.measure(ctx, 'github.com/humblemuzzu/dumbtv', { size: 54, family: MONO, tracking: -0.5 });
  ctx.fillStyle = blue;
  ctx.globalAlpha = D.prog(t, 0.72, 0.3);
  ctx.fillRect(W / 2 - (uw / 2) * D.easeOut(D.prog(t, 0.72, 0.3)), 726, uw * D.easeOut(D.prog(t, 0.72, 0.3)), 3);
  ctx.globalAlpha = 1;
}

const ORDER = [
  ...HOOK.map((c, i) => [c.t, i + 1 < HOOK.length ? HOOK[i + 1].t : S2.dumbtv, 'hook', c]),
  [S2.dumbtv, S2.stats, 'dumbtv'],
  ...STATS.map((c, i) => [c.t, i + 1 < STATS.length ? STATS[i + 1].t : S2.credo, 'stat', c]),
  [S2.credo, 5.5, 'credo', { t: 0, word: 'no root.', plate: 'ink', size: 220 }],
  [5.5, S2.steps, 'credo', { t: 0, word: 'reversible.', plate: 'cream', size: 210 }],
  ...STEPS.map((c, i) => [c.t, i + 1 < STEPS.length ? STEPS[i + 1].t : S2.agent, 'step', c]),
  [S2.agent, S2.pass, 'agent'],
  ...PASS.map((c, i) => [c.t, i + 1 < PASS.length ? PASS[i + 1].t : S2.result, 'pass', c]),
  [S2.result, S2.verify, 'result'],
  [S2.verify, S2.honesty, 'verify'],
  [21.0, 22.0, 'honesty', { word: 'It won\u2019t replace Fire OS.', plate: 'cream', size: 86 }],
  [22.0, 22.7, 'honesty', { word: 'That\u2019s the hardware.', plate: 'ink', size: 96 }],
  [22.7, 23.5, 'honesty', { word: 'Everything else, it can undo.', plate: 'coral', size: 70, cmd: './scripts/restore.sh' }],
  [23.5, 24.5, 'cta', 0],
  [24.5, 25.5, 'cta', 1],
  [25.5, 99, 'end'],
];

const PASS_PLATES = ['cobalt', 'coral', 'mint', 'ink', 'cream'];
PASS.forEach((p, i) => { p.plate = PASS_PLATES[i]; });

export function drawFrame2(ctx, t, images) {
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.globalAlpha = 1;
  ctx.globalCompositeOperation = 'source-over';

  const shot = ORDER.find(([a, b]) => t >= a && t < b) || ORDER[ORDER.length - 1];
  const [start, , type, data] = shot;
  const local = t - start;

  if (type === 'hook') drawHook(ctx, data, t);
  else if (type === 'dumbtv') drawDumbtv(ctx, local);
  else if (type === 'stat') drawStat(ctx, data, t);
  else if (type === 'credo') drawCredo(ctx, data, local);
  else if (type === 'step') drawStep(ctx, data, t);
  else if (type === 'agent') drawAgent(ctx, local);
  else if (type === 'pass') drawPass(ctx, data, local);
  else if (type === 'result') drawResult(ctx, local, images.home);
  else if (type === 'verify') drawVerify(ctx, local);
  else if (type === 'honesty') drawHonesty(ctx, local, data);
  else if (type === 'cta') drawCta(ctx, local, data);
  else if (type === 'end') drawEnd(ctx, local);

  if (type === 'end' && local < 0.05) {
    ctx.fillStyle = `rgba(247,241,228,${1 - local / 0.05})`;
    ctx.fillRect(0, 0, W, H);
  }

  // glitch slices on the first two frames of a cut
  const glitchy = type === 'dumbtv' || (type === 'hook' && data && data.word === 'YOUR TV') || (type === 'pass' && data && data.n === '65');
  if (glitchy && local < 0.07) {
    ctx.save();
    ctx.globalAlpha = 0.9;
    const slices = [
      [H * 0.18, H * 0.05, 26],
      [H * 0.56, H * 0.03, -18],
      [H * 0.82, H * 0.04, 12],
    ];
    for (const [sy, sh, dx] of slices) {
      ctx.drawImage(ctx.canvas, 0, sy, W, sh, dx, sy, W, sh);
    }
    ctx.restore();
  }

  grain(ctx, t, type === 'result' ? 0.04 : 0.05);
  ctx.drawImage(vign, 0, 0);

  if (t > DUR2 - 0.8) {
    ctx.fillStyle = `rgba(6,6,8,${D.clamp((t - (DUR2 - 0.8)) / 0.8)})`;
    ctx.fillRect(0, 0, W, H);
  }
  if (t < 0.12) {
    ctx.fillStyle = `rgba(0,0,0,${1 - t / 0.12})`;
    ctx.fillRect(0, 0, W, H);
  }
}
