export const W = 1920;
export const H = 1080;

export const C = {
  bg: '#07080A',
  bg2: '#0A0C10',
  panel: '#0B0C0F',
  panel2: '#101216',
  ink: '#F4F2ED',
  ink2: 'rgba(244,242,237,0.64)',
  ink3: 'rgba(244,242,237,0.42)',
  ink4: 'rgba(244,242,237,0.20)',
  line: 'rgba(244,242,237,0.11)',
  line2: 'rgba(244,242,237,0.06)',
  accent: '#3D8BFF',
  accentInk: '#8FBAFF',
  good: '#74E2A3',
  warn: '#EBA55F',
  bad: '#E4786B',
  term: '#090A0D',
};

export const DISPLAY = '"Helvetica Neue", Helvetica, Arial, sans-serif';
export const MONO = '"Geist Mono", "JetBrains Mono", Menlo, monospace';

export const clamp = (v, a = 0, b = 1) => Math.min(b, Math.max(a, v));
export const lerp = (a, b, t) => a + (b - a) * t;
export const easeOut = (t) => 1 - Math.pow(1 - clamp(t), 3);
export const easeOutExpo = (t) => (t >= 1 ? 1 : 1 - Math.pow(2, -10 * clamp(t)));
export const easeInOut = (t) => {
  t = clamp(t);
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
};
export const prog = (t, start, dur) => clamp((t - start) / dur);
export const fade = (t, inAt, inDur, outAt = 1e9, outDur = 0.4) =>
  clamp((t - inAt) / inDur) * (1 - clamp((t - outAt) / outDur));

export function setFont(ctx, size, weight = 400, family = DISPLAY, tracking = 0) {
  ctx.font = `${weight} ${size}px ${family}`;
  ctx.letterSpacing = typeof tracking === 'number' ? `${tracking}px` : tracking;
}

export function txt(ctx, str, x, y, o = {}) {
  const {
    size = 32,
    weight = 400,
    family = DISPLAY,
    color = C.ink,
    align = 'left',
    baseline = 'alphabetic',
    tracking = 0,
    alpha = 1,
  } = o;
  if (alpha <= 0) return 0;
  ctx.save();
  ctx.globalAlpha *= alpha;
  setFont(ctx, size, weight, family, tracking);
  ctx.fillStyle = color;
  ctx.textAlign = align;
  ctx.textBaseline = baseline;
  ctx.fillText(str, x, y);
  const w = ctx.measureText(str).width;
  ctx.restore();
  return w;
}

export function measure(ctx, str, o = {}) {
  const { size = 32, weight = 400, family = DISPLAY, tracking = 0 } = o;
  setFont(ctx, size, weight, family, tracking);
  const w = ctx.measureText(str).width;
  return w;
}

export function clipRise(ctx, x, yTop, w, h, p, travel, fn) {
  if (p <= 0) return;
  ctx.save();
  ctx.beginPath();
  ctx.rect(x - 4, yTop, w + 8, h);
  ctx.clip();
  ctx.globalAlpha *= clamp(p * 2.4);
  ctx.translate(0, (1 - easeOutExpo(p)) * travel);
  fn();
  ctx.restore();
}

export function wrapLines(ctx, str, maxW, o = {}) {
  const words = str.split(' ');
  const lines = [];
  let cur = '';
  for (const word of words) {
    const next = cur ? cur + ' ' + word : word;
    if (measure(ctx, next, o) > maxW && cur) {
      lines.push(cur);
      cur = word;
    } else cur = next;
  }
  if (cur) lines.push(cur);
  return lines;
}

export function rr(ctx, x, y, w, h, r) {
  const rad = Math.min(r, w / 2, h / 2);
  ctx.beginPath();
  ctx.moveTo(x + rad, y);
  ctx.arcTo(x + w, y, x + w, y + h, rad);
  ctx.arcTo(x + w, y + h, x, y + h, rad);
  ctx.arcTo(x, y + h, x, y, rad);
  ctx.arcTo(x, y, x + w, y, rad);
  ctx.closePath();
}

export function hairline(ctx, x1, y1, x2, y2, color = C.line, width = 1) {
  ctx.save();
  ctx.strokeStyle = color;
  ctx.lineWidth = width;
  ctx.beginPath();
  ctx.moveTo(x1, y1);
  ctx.lineTo(x2, y2);
  ctx.stroke();
  ctx.restore();
}

let bgCanvas = null;
let noise = [];
let vignette = null;

export function initLayers() {
  bgCanvas = document.createElement('canvas');
  bgCanvas.width = W;
  bgCanvas.height = H;
  const b = bgCanvas.getContext('2d');
  b.fillStyle = C.bg;
  b.fillRect(0, 0, W, H);
  let g = b.createRadialGradient(W * 0.5, H * 0.34, 60, W * 0.5, H * 0.42, 1160);
  g.addColorStop(0, 'rgba(48,62,86,0.20)');
  g.addColorStop(0.55, 'rgba(20,26,38,0.10)');
  g.addColorStop(1, 'rgba(0,0,0,0)');
  b.fillStyle = g;
  b.fillRect(0, 0, W, H);

  vignette = document.createElement('canvas');
  vignette.width = W;
  vignette.height = H;
  const v = vignette.getContext('2d');
  const rg = v.createRadialGradient(W / 2, H / 2, H * 0.28, W / 2, H / 2, H * 0.92);
  rg.addColorStop(0, 'rgba(0,0,0,0)');
  rg.addColorStop(1, 'rgba(0,0,0,0.62)');
  v.fillStyle = rg;
  v.fillRect(0, 0, W, H);

  for (let k = 0; k < 3; k++) {
    const n = document.createElement('canvas');
    n.width = 960;
    n.height = 540;
    const nx = n.getContext('2d');
    const data = nx.createImageData(960, 540);
    let seed = 1337 + k * 977;
    const rnd = () => {
      seed = (seed * 1664525 + 1013904223) >>> 0;
      return seed / 4294967296;
    };
    for (let i = 0; i < data.data.length; i += 4) {
      const val = 108 + Math.floor(rnd() * 40);
      data.data[i] = val;
      data.data[i + 1] = val;
      data.data[i + 2] = val;
      data.data[i + 3] = 255;
    }
    nx.putImageData(data, 0, 0);
    noise.push(n);
  }
}

export function bg(ctx, t, o = {}) {
  const { glow = 1, grain = 0.10, vign = 1 } = o;
  ctx.drawImage(bgCanvas, 0, 0);
  if (glow !== 1) {
    ctx.save();
    ctx.globalAlpha = glow;
    ctx.drawImage(bgCanvas, 0, 0);
    ctx.restore();
  }
  if (vign) {
    ctx.save();
    ctx.globalAlpha = vign;
    ctx.drawImage(vignette, 0, 0);
    ctx.restore();
  }
  if (grain) {
    const idx = Math.floor(t * 15) % 3;
    ctx.save();
    ctx.globalCompositeOperation = 'overlay';
    ctx.globalAlpha = grain;
    const dx = (Math.floor(t * 30) % 2) * 2;
    ctx.drawImage(noise[idx], -dx, 0, W + 4, H);
    ctx.restore();
  }
}

export function glowAt(ctx, x, y, r, color, alpha = 0.5) {
  const g = ctx.createRadialGradient(x, y, 0, x, y, r);
  g.addColorStop(0, color);
  g.addColorStop(1, 'rgba(0,0,0,0)');
  ctx.save();
  ctx.globalAlpha = alpha;
  ctx.fillStyle = g;
  ctx.fillRect(x - r, y - r, r * 2, r * 2);
  ctx.restore();
}

export function shadowUnder(ctx, x, y, w, h, strength = 0.8) {
  const g = ctx.createRadialGradient(x + w / 2, y + h / 2, h * 0.1, x + w / 2, y + h / 2, h * 0.9);
  g.addColorStop(0, `rgba(0,0,0,${0.55 * strength})`);
  g.addColorStop(1, 'rgba(0,0,0,0)');
  ctx.save();
  ctx.fillStyle = g;
  ctx.fillRect(x - w * 0.1, y - h * 0.1, w * 1.2, h * 1.2);
  ctx.restore();
}

export function drawCover(ctx, img, x, y, w, h) {
  const ir = img.width / img.height;
  const r = w / h;
  let sw = img.width;
  let sh = img.height;
  let sx = 0;
  let sy = 0;
  if (ir > r) {
    sw = img.height * r;
    sx = (img.width - sw) / 2;
  } else {
    sh = img.width / r;
    sy = (img.height - sh) / 2;
  }
  ctx.drawImage(img, sx, sy, sw, sh, x, y, w, h);
}

export function panel(ctx, x, y, w, h, o = {}) {
  const { fill = C.panel, border = C.line, radius = 14, shadow = 0 } = o;
  if (shadow) shadowUnder(ctx, x, y, w, h, shadow);
  ctx.save();
  rr(ctx, x, y, w, h, radius);
  ctx.fillStyle = fill;
  ctx.fill();
  ctx.strokeStyle = border;
  ctx.lineWidth = 1;
  ctx.stroke();
  ctx.restore();
}

export function chip(ctx, label, x, y, o = {}) {
  const { color = C.accent, fill = 'rgba(61,139,255,0.12)', size = 14, pad = 10, border = null } = o;
  setFont(ctx, size, 500, MONO, 1.5);
  const w = ctx.measureText(label).width + pad * 2;
  const h = size + 12;
  ctx.save();
  rr(ctx, x, y - h / 2, w, h, h / 2);
  ctx.fillStyle = fill;
  ctx.fill();
  if (border) {
    ctx.strokeStyle = border;
    ctx.stroke();
  }
  ctx.restore();
  txt(ctx, label, x + pad, y + size * 0.36, { size, weight: 500, family: MONO, color, tracking: 1.5 });
  return w;
}

export function terminal(ctx, opts) {
  const {
    x,
    y,
    w,
    h,
    lines,
    t,
    title = 'dumbtv',
    fontSize = 21,
    lineGap = 1.62,
    pad = 34,
    alpha = 1,
    bar = true,
    cursor = true,
  } = opts;

  ctx.save();
  ctx.globalAlpha = alpha;

  shadowUnder(ctx, x, y, w, h, 1.1);
  panel(ctx, x, y, w, h, { fill: C.term, border: 'rgba(244,242,237,0.13)', radius: 16 });

  const barH = bar ? 46 : 0;
  if (bar) {
    ctx.save();
    rr(ctx, x, y, w, h, 16);
    ctx.clip();
    ctx.fillStyle = 'rgba(255,255,255,0.025)';
    ctx.fillRect(x, y, w, barH);
    ctx.fillStyle = 'rgba(244,242,237,0.10)';
    ctx.fillRect(x, y + barH - 1, w, 1);
    ctx.restore();
    const dots = ['rgba(244,242,237,0.20)', 'rgba(244,242,237,0.13)', 'rgba(244,242,237,0.09)'];
    dots.forEach((c, i) => {
      ctx.beginPath();
      ctx.arc(x + 26 + i * 20, y + barH / 2, 5, 0, Math.PI * 2);
      ctx.fillStyle = c;
      ctx.fill();
    });
    txt(ctx, title, x + w / 2, y + barH / 2 + 5, {
      size: 13,
      family: MONO,
      color: C.ink3,
      align: 'center',
      tracking: 1.2,
    });
    txt(ctx, 'zsh', x + w - 26, y + barH / 2 + 5, {
      size: 13,
      family: MONO,
      color: 'rgba(244,242,237,0.25)',
      align: 'right',
    });
  }

  const left = x + pad;
  const lh = fontSize * lineGap;
  let cy = y + barH + pad + fontSize * 0.2;

  const visible = [];
  for (const ln of lines) {
    if (t < ln.at) continue;
    let str = ln.text;
    let typing = false;
    if (ln.cps) {
      const n = Math.floor((t - ln.at) * ln.cps);
      if (n < str.length) {
        str = str.slice(0, Math.max(0, n));
        typing = true;
      }
    }
    if (ln.kind === 'agentbar') {
      visible.push({ ...ln, str, typing, kind: 'agentbar', at: ln.at });
      continue;
    }
    visible.push({ ...ln, str, typing });
  }

  let overflow = 0;
  const totalH = visible.reduce((acc, ln) => acc + (ln.kind === 'agentbar' ? lh * 1.25 : lh), 0);
  const avail = h - barH - pad * 2;
  if (totalH > avail) overflow = totalH - avail;

  ctx.save();
  ctx.beginPath();
  ctx.rect(x + 1, y + barH + 1, w - 2, h - barH - 2);
  ctx.clip();

  let cursorY = null;
  let cursorX = null;

  for (let i = 0; i < visible.length; i++) {
    const ln = visible[i];
    const yy = cy - overflow;

    if (ln.kind === 'agentbar') {
      setFont(ctx, fontSize * 0.66, 500, MONO, 1.4);
      const label = ln.str.toUpperCase();
      const cw = ctx.measureText(label).width + 26;
      rr(ctx, left, yy - fontSize * 0.62, cw, fontSize * 1.22, 4);
      ctx.fillStyle = 'rgba(61,139,255,0.14)';
      ctx.fill();
      txt(ctx, label, left + 13, yy + fontSize * 0.02, {
        size: fontSize * 0.66,
        weight: 500,
        family: MONO,
        color: C.accentInk,
        tracking: 1.4,
      });
      cy += lh * 1.25;
      continue;
    }

    let prefix = '';
    let color = C.ink2;
    let weight = 400;
    if (ln.kind === 'cmd') {
      prefix = '$';
      color = C.ink;
    } else if (ln.kind === 'user') {
      prefix = '>';
      color = C.ink;
    } else if (ln.kind === 'dim') {
      prefix = '';
      color = 'rgba(244,242,237,0.30)';
    } else if (ln.kind === 'ok') {
      prefix = '';
      color = C.good;
      weight = 500;
    } else {
      prefix = '\u203a';
      color = C.ink2;
    }
    if (prefix) {
      txt(ctx, prefix, left, yy, {
        size: fontSize,
        weight: 500,
        family: MONO,
        color: ln.kind === 'user' ? C.accent : C.ink3,
      });
    }
    const textX = left + (prefix ? fontSize * 1.05 : 0);
    const wText = txt(ctx, ln.str, textX, yy, {
      size: fontSize,
      weight,
      family: MONO,
      color,
    });
    if (ln.typing || i === visible.length - 1) {
      cursorY = yy;
      cursorX = textX + wText + 3;
    }
    cy += lh;
  }

  if (cursor && cursorY !== null && Math.floor(t * 2.2) % 2 === 0) {
    ctx.fillStyle = C.accent;
    ctx.globalAlpha = 0.9;
    ctx.fillRect(cursorX, cursorY - fontSize * 0.78, fontSize * 0.52, fontSize * 0.95);
  }

  ctx.restore();
  ctx.restore();
}

export function settingsPanel(ctx, opts) {
  const {
    x,
    y,
    w,
    h,
    active = 2,
    focus = true,
    rows = ['About', 'Storage', 'Network', 'Install Update'],
    right = () => {},
    navTitle = 'Device & Software',
    tapPulse = 0,
    tapIndex = -1,
  } = opts;

  panel(ctx, x, y, w, h, { fill: '#0D0E11', border: 'rgba(244,242,237,0.10)', radius: 10, shadow: 0.9 });

  const navW = w * 0.30;
  const listW = w * 0.32;

  ctx.save();
  ctx.beginPath();
  ctx.rect(x, y, w, h);
  ctx.clip();

  // nav
  const g = ctx.createLinearGradient(x, y, x + navW, y);
  g.addColorStop(0, 'rgba(255,255,255,0.045)');
  g.addColorStop(1, 'rgba(255,255,255,0.015)');
  ctx.fillStyle = g;
  ctx.fillRect(x, y, navW, h);

  const ix = x + navW / 2;
  const iy = y + h * 0.38;
  ctx.strokeStyle = 'rgba(244,242,237,0.72)';
  ctx.lineWidth = 3.5;
  ctx.lineJoin = 'round';
  ctx.beginPath();
  ctx.roundRect(ix - 48, iy - 34, 96, 62, 5);
  ctx.stroke();
  ctx.beginPath();
  ctx.moveTo(ix - 12, iy + 28);
  ctx.lineTo(ix + 12, iy + 28);
  ctx.stroke();
  ctx.beginPath();
  ctx.moveTo(ix - 22, iy + 40);
  ctx.lineTo(ix + 22, iy + 40);
  ctx.stroke();
  txt(ctx, navTitle, ix, y + h * 0.38 + 118, {
    size: 25,
    weight: 300,
    color: 'rgba(244,242,237,0.86)',
    align: 'center',
  });

  ctx.fillStyle = 'rgba(0,0,0,0.18)';
  ctx.fillRect(x + navW, y, 1, h);
  ctx.fillRect(x + navW + listW, y, 1, h);

  // list
  const rowH = Math.min(92, h / (rows.length + 1.6));
  const listTop = y + h * 0.30;
  for (let i = 0; i < rows.length; i++) {
    const ry = listTop + i * rowH;
    const isActive = i === active;
    if (isActive && focus) {
      ctx.fillStyle = 'rgba(236,235,230,0.92)';
      ctx.fillRect(x + navW + 18, ry - rowH * 0.62, listW - 36, rowH * 0.96);
    }
    txt(ctx, rows[i], x + navW + 40, ry, {
      size: 22,
      weight: isActive ? 500 : 400,
      color: isActive && focus ? '#0A0B0D' : 'rgba(244,242,237,0.80)',
    });
  }

  const dx = x + navW + listW;
  const dw = w - navW - listW;
  ctx.save();
  ctx.translate(dx, y);
  right(dw, h, { tapPulse, tapIndex });
  ctx.restore();

  ctx.restore();
}

export function tapRipple(ctx, cx, cy, p, color = 'rgba(244,242,237,0.55)') {
  if (p <= 0 || p >= 1) return;
  const r = 10 + easeOut(p) * 42;
  ctx.save();
  ctx.beginPath();
  ctx.arc(cx, cy, r, 0, Math.PI * 2);
  ctx.strokeStyle = color;
  ctx.globalAlpha = (1 - p) * 0.8;
  ctx.lineWidth = 2;
  ctx.stroke();
  ctx.restore();
}

export function bigNumber(ctx, value, x, y, o = {}) {
  const { size = 120, color = C.ink, align = 'left', weight = 300, tracking = -4 } = o;
  return txt(ctx, String(value), x, y, {
    size,
    weight,
    family: DISPLAY,
    color,
    align,
    tracking,
  });
}
