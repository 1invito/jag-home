import * as D from './design.js';
import {
  S,
  PACKAGES,
  AGENT_LINES,
  VERIFY_ROWS,
  PASS_ROWS,
  TAPS,
} from './timeline.js';

const { W, H, C } = D;

const RAIL = [
  { n: '01', title: 'Developer mode', caption: 'Device & Software \u2192 About.\nTap the name 7\u00d7.' },
  { n: '02', title: 'The address', caption: 'Settings \u2192 Network.\nCopy the IP. That\u2019s all you need.' },
  { n: '03', title: 'The agent', caption: 'Clone the repo. Open your agent.\nPaste one sentence.' },
  { n: '04', title: 'The pass', caption: 'What the agent changes on the TV,\nand why each part is needed.' },
  { n: '05', title: 'Proof', caption: 'Nothing is called done\nuntil the TV answers for itself.' },
];

function rail(ctx, idx, t, local) {
  const r = RAIL[idx];
  const p = D.prog(local, 0.2, 0.8);
  ctx.save();
  D.clipRise(ctx, 100, 220, 470, 420, p, 22, () => {
    D.txt(ctx, r.n, 120, 258, { size: 15, weight: 500, family: D.MONO, color: C.accent, tracking: 3 });
    D.txt(ctx, '/ 05', 120 + 34, 258, { size: 15, weight: 400, family: D.MONO, color: C.ink4, tracking: 3 });
    D.txt(ctx, r.title, 120, 334, { size: 54, weight: 400, color: C.ink, tracking: -1.6 });
    const lines = r.caption.split('\n');
    lines.forEach((ln, i) => {
      D.txt(ctx, ln, 120, 396 + i * 32, {
        size: 19,
        weight: 400,
        family: D.MONO,
        color: 'rgba(244,242,237,0.50)',
      });
    });
  });
  ctx.restore();

  // five-step progress
  const y = 986;
  for (let i = 0; i < 5; i++) {
    const x = 120 + i * 58;
    ctx.fillStyle = i === idx ? C.accent : i < idx ? 'rgba(244,242,237,0.42)' : 'rgba(244,242,237,0.13)';
    ctx.fillRect(x, y, 42, 2);
  }
  D.txt(ctx, `${idx + 1} / 5`, 120 + 5 * 58 + 6, y + 6, {
    size: 13,
    family: D.MONO,
    color: C.ink4,
    tracking: 1.5,
  });
}

function detailRows(ctx, dw, rows, o = {}) {
  const { highlight = -1, x0 = 54, y0 = 150, gap = 96, labelSize = 17, valueSize = 27, alpha = 1 } = o;
  ctx.save();
  ctx.globalAlpha *= alpha;
  rows.forEach((row, i) => {
    const y = y0 + i * gap;
    if (i === highlight && o.highlightP > 0) {
      const p = o.highlightP;
      ctx.save();
      ctx.globalAlpha *= p;
      ctx.fillStyle = 'rgba(61,139,255,0.10)';
      ctx.fillRect(x0 - 22, y - 56, dw - 66, 92);
      ctx.fillStyle = C.accent;
      ctx.fillRect(x0 - 22, y - 56, 2, 92 * p);
      ctx.restore();
    }
    D.txt(ctx, row[0], x0, y - 26, { size: labelSize, family: D.MONO, color: C.ink3, tracking: 0.6 });
    D.txt(ctx, row[1], x0, y + 14, { size: valueSize, weight: 400, color: row[2] || C.ink });
  });
  ctx.restore();
}

// ---------------------------------------------------------------- 00 ACR

function sceneAcr(ctx, t) {
  ctx.fillStyle = '#05060A';
  ctx.fillRect(0, 0, W, H);

  D.glowAt(ctx, W / 2, H / 2, 640, 'rgba(56,74,108,0.22)', 0.9 * D.fade(t, 0, 2));
  D.glowAt(ctx, W / 2, 220, 340, 'rgba(61,139,255,0.13)', D.fade(t, 2.4, 1.6, 12.8, 1.4));

  // standby light
  const led = D.fade(t, 0.5, 0.8, 2.2, 0.8);
  if (led > 0) {
    const pulse = 0.65 + 0.35 * Math.sin(t * 2.2);
    ctx.save();
    ctx.globalAlpha = led * pulse;
    ctx.fillStyle = '#EDE7DA';
    ctx.beginPath();
    ctx.arc(W / 2, H / 2, 3.2, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
    D.glowAt(ctx, W / 2, H / 2, 90, 'rgba(237,231,218,0.20)', led * 0.8);
  }

  // rings
  const ringA = D.fade(t, 2.6, 2.2, 13.0, 1.2);
  if (ringA > 0) {
    for (let i = 0; i < 3; i++) {
      const ph = ((t - 2.6) / 4.6 + i / 3) % 1;
      ctx.save();
      ctx.globalAlpha = ringA * 0.10 * (1 - ph);
      ctx.strokeStyle = C.accent;
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.arc(W / 2, 470, 180 + ph * 420, 0, Math.PI * 2);
      ctx.stroke();
      ctx.restore();
    }
  }

  const p1 = D.prog(t, 2.7, 0.9);
  D.clipRise(ctx, 0, 360, W, 220, p1, 26, () => {
    D.txt(ctx, 'PACKAGE', W / 2, 404, {
      size: 13,
      weight: 500,
      family: D.MONO,
      color: 'rgba(244,242,237,0.34)',
      align: 'center',
      tracking: 5.5,
    });
    D.txt(ctx, 'com.amazon.tv.acr', W / 2, 486, {
      size: 58,
      weight: 400,
      family: D.MONO,
      color: C.ink,
      align: 'center',
      tracking: 1,
    });
  });
  const lineP = D.prog(t, 3.2, 0.9);
  if (lineP > 0) {
    const half = 330 * D.easeOut(lineP);
    D.hairline(ctx, W / 2 - half, 522, W / 2 + half, 522, 'rgba(244,242,237,0.20)');
  }
  const p2 = D.prog(t, 4.0, 0.8);
  D.clipRise(ctx, 0, 540, W, 60, p2, 14, () => {
    D.txt(ctx, 'AUTOMATIC CONTENT RECOGNITION', W / 2, 578, {
      size: 14,
      weight: 400,
      family: D.MONO,
      color: 'rgba(143,186,255,0.75)',
      align: 'center',
      tracking: 3.6,
    });
  });

  const lines = [
    { at: 5.7, text: 'It fingerprints what\u2019s on your screen.', o: {} },
    { at: 8.6, text: 'Including HDMI.', o: {} },
  ];
  let y = 700;
  for (const l of lines) {
    const p = D.prog(t, l.at, 0.8);
    D.clipRise(ctx, 0, y - 62, W, 92, p, 26, () => {
      D.txt(ctx, l.text, W / 2, y, {
        size: 46,
        weight: 300,
        color: C.ink,
        align: 'center',
        tracking: -0.8,
      });
    });
    y += 76;
  }

  const p3 = D.prog(t, 11.4, 0.8);
  D.clipRise(ctx, 0, 828, W, 60, p3, 16, () => {
    D.txt(ctx, 'Amazon won\u2019t let you uninstall it.', W / 2, 862, {
      size: 19,
      weight: 400,
      family: D.MONO,
      color: 'rgba(228,120,107,0.88)',
      align: 'center',
      tracking: 0.2,
    });
  });

  const out = 1 - D.prog(t, 13.15, 0.7);
  if (out < 1) {
    ctx.fillStyle = `rgba(5,6,10,${1 - out})`;
    ctx.fillRect(0, 0, W, H);
  }
}

// ------------------------------------------------------------- 01 WALL

function sceneWall(ctx, t) {
  const names = [...new Set(PACKAGES)].map((n) => (n.length > 36 ? n.slice(0, 35) + '\u2026' : n));
  const cols = 3;
  const colW = 340;
  const x0 = 780;
  const y0 = 24;
  const rowH = 29;

  const scanStart = 0.9;
  const scanEnd = 8.3;
  const scanP = D.clamp((t - scanStart) / (scanEnd - scanStart));
  const scanX = D.lerp(-120, W + 120, scanP);
  const drift = -18 * D.clamp(t / 11.4);

  let disabled = 0;
  ctx.save();
  ctx.translate(0, drift);

  for (let i = 0; i < names.length; i++) {
    const col = i % cols;
    const row = Math.floor(i / cols);
    const x = x0 + col * colW;
    const y = y0 + row * rowH + 24;
    const isTarget = i < 65;
    const passed = t > 0.5 && isTarget && x + 130 < scanX;
    if (passed) disabled++;

    const near = Math.abs(x + 150 - scanX) < 160;
    let alpha = isTarget ? 0.30 : 0.14;
    let color = C.ink2;
    if (passed) {
      alpha = 0.17;
      color = C.ink3;
    }
    if (near && isTarget) {
      alpha = 0.9;
      color = C.accentInk;
    }
    D.txt(ctx, names[i], x, y, {
      size: 15,
      weight: 400,
      family: D.MONO,
      color,
      alpha,
    });
  }
  ctx.restore();

  // top and bottom falloff for the wall
  const vg = ctx.createLinearGradient(0, 0, 0, H);
  vg.addColorStop(0, 'rgba(7,8,10,0.95)');
  vg.addColorStop(0.09, 'rgba(7,8,10,0)');
  vg.addColorStop(0.9, 'rgba(7,8,10,0)');
  vg.addColorStop(1, 'rgba(7,8,10,1)');
  ctx.fillStyle = vg;
  ctx.fillRect(0, 0, W, H);

  // scan beam
  if (t > scanStart && t < scanEnd + 0.2) {
    const g = ctx.createLinearGradient(scanX - 90, 0, scanX + 10, 0);
    g.addColorStop(0, 'rgba(61,139,255,0)');
    g.addColorStop(0.75, 'rgba(61,139,255,0.10)');
    g.addColorStop(1, 'rgba(140,190,255,0.32)');
    ctx.fillStyle = g;
    ctx.fillRect(scanX - 90, 0, 100, H);
    ctx.fillStyle = 'rgba(180,214,255,0.75)';
    ctx.fillRect(scanX, 0, 1.5, H);
  }

  // left readout
  D.glowAt(ctx, 360, 520, 620, 'rgba(50,68,98,0.16)', 1);

  const p = D.prog(t, 0.35, 0.9);
  D.clipRise(ctx, 100, 240, 620, 340, p, 24, () => {
    D.txt(ctx, 'INSTALLED', 120, 268, {
      size: 14,
      weight: 500,
      family: D.MONO,
      color: 'rgba(244,242,237,0.34)',
      tracking: 4.5,
    });
    D.txt(ctx, '238', 120, 420, { size: 148, weight: 200, color: C.ink, tracking: -8 });
    D.txt(ctx, 'packages on this TV', 124, 468, { size: 19, family: D.MONO, color: C.ink3 });
  });

  const count = t < scanStart ? 0 : disabled;
  const cp = D.prog(t, scanStart, 0.4);
  ctx.save();
  ctx.globalAlpha = cp;
  D.txt(ctx, 'DISABLED', 120, 620, {
    size: 14,
    weight: 500,
    family: D.MONO,
    color: 'rgba(143,186,255,0.72)',
    tracking: 4.5,
  });
  D.txt(ctx, String(count), 120, 748, { size: 118, weight: 200, color: C.accent, tracking: -6 });
  D.txt(ctx, '/ 65', 120 + D.measure(ctx, String(count), { size: 118, weight: 200, tracking: -6 }) + 18, 748, {
    size: 24,
    family: D.MONO,
    color: C.ink4,
  });
  ctx.restore();

  const pc = D.prog(t, 6.7, 0.9);
  D.clipRise(ctx, 100, 820, 620, 150, pc, 18, () => {
    D.txt(ctx, 'They exist to watch,', 120, 862, { size: 31, weight: 300, color: C.ink, tracking: -0.4 });
    D.txt(ctx, 'track, sell, or update.', 120, 900, { size: 31, weight: 300, color: C.ink, tracking: -0.4 });
  });

  const out = 1 - D.prog(t, 11.0, 0.55);
  if (out < 1) {
    ctx.fillStyle = `rgba(7,8,10,${1 - out})`;
    ctx.fillRect(0, 0, W, H);
  }
}

// ------------------------------------------------------------ 02 TITLE

function sceneTitle(ctx, t) {
  D.glowAt(ctx, W / 2, 470, 820, 'rgba(61,139,255,0.13)', D.fade(t, 0, 1.6, 10.4, 1.4));
  D.glowAt(ctx, W / 2, 560, 400, 'rgba(255,255,255,0.05)', D.fade(t, 0, 2));

  const tagP = D.prog(t, 0.35, 0.7);
  D.clipRise(ctx, 0, 340, W, 42, tagP, 10, () => {
    D.txt(ctx, 'OPEN SOURCE \u00b7 FIRE TV \u00b7 NO ROOT', W / 2, 372, {
      size: 13,
      weight: 500,
      family: D.MONO,
      color: 'rgba(244,242,237,0.40)',
      align: 'center',
      tracking: 5,
    });
  });

  const wp = D.prog(t, 0.55, 1.0);
  if (wp > 0) {
    const track = D.lerp(8, -9, D.easeOut(wp));
    ctx.save();
    ctx.beginPath();
    ctx.rect(0, 402, W, 262);
    ctx.clip();
    ctx.globalAlpha *= D.clamp(wp * 2.2);
    ctx.translate(0, (1 - D.easeOutExpo(wp)) * 86);
    D.txt(ctx, 'dumbtv', W / 2 - 5, 600, {
      size: 232,
      weight: 500,
      color: C.ink,
      align: 'center',
      tracking: track,
    });
    ctx.restore();
  }

  const rule = D.prog(t, 1.55, 0.7);
  if (rule > 0) {
    const half = 90 * D.easeOut(rule);
    D.hairline(ctx, W / 2 - half, 646, W / 2 + half, 646, 'rgba(61,139,255,0.85)', 2);
  }

  const tp = D.prog(t, 1.95, 0.8);
  D.clipRise(ctx, 0, 672, W, 60, tp, 16, () => {
    D.txt(ctx, 'A television that behaves.', W / 2, 714, {
      size: 35,
      weight: 300,
      color: C.ink2,
      align: 'center',
      tracking: -0.4,
    });
  });

  const facts = ['65 packages off', '396 hosts blocked', 'no root'];
  const sizes = facts.map((f) =>
    D.measure(ctx, f, { size: 17, family: D.MONO, tracking: 1.2 })
  );
  const sep = 44;
  const total = sizes.reduce((a, b) => a + b, 0) + sep * (facts.length - 1);
  let fx = W / 2 - total / 2;
  const fp = D.prog(t, 4.3, 0.9);
  facts.forEach((f, i) => {
    const a = D.clamp(fp * 1.6 - i * 0.18);
    D.txt(ctx, f, fx, 806, {
      size: 17,
      family: D.MONO,
      color: C.ink3,
      tracking: 1.2,
      alpha: a,
    });
    fx += sizes[i];
    if (i < facts.length - 1) {
      ctx.save();
      ctx.globalAlpha = a * 0.5;
      ctx.fillStyle = C.ink4;
      ctx.beginPath();
      ctx.arc(fx + sep / 2, 800, 2.2, 0, Math.PI * 2);
      ctx.fill();
      ctx.restore();
      fx += sep;
    }
  });

  const pp = D.prog(t, 6.6, 0.9);
  D.clipRise(ctx, 0, 862, W, 70, pp, 14, () => {
    D.txt(ctx, 'Point an agent at the repo. It does the rest.', W / 2, 902, {
      size: 27,
      weight: 300,
      color: C.ink2,
      align: 'center',
      tracking: 0,
    });
  });

  const up = D.prog(t, 8.2, 0.8);
  D.txt(ctx, 'github.com/humblemuzzu/dumbtv', W / 2, 972, {
    size: 14,
    family: D.MONO,
    color: C.ink4,
    align: 'center',
    tracking: 1.8,
    alpha: up,
  });

  const out = 1 - D.prog(t, 10.2, 0.9);
  if (out < 1) {
    ctx.fillStyle = `rgba(7,8,10,${1 - out})`;
    ctx.fillRect(0, 0, W, H);
  }
}

// --------------------------------------------------------- 03 DEVMODE

function sceneDevmode(ctx, t) {
  rail(ctx, 0, t, t);

  const px = 650;
  const py = 200;
  const pw = 1150;
  const ph = 640;

  const p = D.prog(t, 0.45, 0.9);
  D.clipRise(ctx, px - 20, py - 20, pw + 40, ph + 40, p, 22, () => {
    D.settingsPanel(ctx, {
      x: px,
      y: py,
      w: pw,
      h: ph,
      active: 0,
      rows: ['About', 'Storage', 'Network', 'Install Update'],
      tapPulse: 0,
      right: (dw) => {
        const taps = TAPS.filter((tt) => t + S.devmode >= tt).length;
        const last = TAPS.filter((tt) => t + S.devmode >= tt).slice(-1)[0];
        const pulse = last ? 1 - D.prog(t + S.devmode, last, 0.5) : 0;

        D.txt(ctx, 'About', 54, 86, {
          size: 13,
          weight: 500,
          family: D.MONO,
          color: 'rgba(244,242,237,0.34)',
          tracking: 4,
        });

        detailRows(
          ctx,
          dw,
          [
            ['Device Name', 'Living Room TV'],
            ['Fire OS', '7.7.1.7'],
            ['Storage', '5.2 GB free'],
          ],
          { highlight: 0, highlightP: taps > 0 ? 1 : 0, x0: 54, y0: 160, gap: 96 }
        );

        if (taps > 0 && pulse > 0) {
          D.tapRipple(ctx, dw * 0.78, 160 - 12, 1 - pulse, 'rgba(143,186,255,0.9)');
        }


        const up = D.prog(t, 7.0, 0.5);
        if (up > 0) {
          ctx.save();
          ctx.globalAlpha = up;
          D.rr(ctx, 54, 544, 330, 54, 6);
          ctx.fillStyle = 'rgba(116,226,163,0.10)';
          ctx.fill();
          ctx.strokeStyle = 'rgba(116,226,163,0.35)';
          ctx.stroke();
          ctx.restore();
          D.txt(ctx, 'DEVELOPER OPTIONS UNLOCKED', 72, 577, {
            size: 12,
            weight: 500,
            family: D.MONO,
            color: 'rgba(116,226,163,0.95)',
            tracking: 1.8,
            alpha: up,
          });
        }
      },
    });
  });

  const taps = TAPS.filter((tt) => t + S.devmode >= tt).length;
  const tp = D.prog(t, 2.4, 0.6);
  if (tp > 0) {
    ctx.save();
    ctx.globalAlpha = tp;
    D.txt(ctx, 'TAPS', 120, 486, {
      size: 12,
      weight: 500,
      family: D.MONO,
      color: 'rgba(244,242,237,0.38)',
      tracking: 3.4,
    });
    for (let i = 0; i < 7; i++) {
      const on = i < taps;
      ctx.beginPath();
      ctx.arc(120 + i * 16 + 3, 516, 3.8, 0, Math.PI * 2);
      ctx.fillStyle = on ? C.accent : 'rgba(244,242,237,0.16)';
      ctx.fill();
    }
    D.txt(ctx, `${taps} / 7`, 120 + 7 * 16 + 10, 521, {
      size: 13,
      family: D.MONO,
      color: taps === 7 ? C.good : 'rgba(244,242,237,0.45)',
      tracking: 1.2,
    });
    ctx.restore();
  }

  const noteP = D.prog(t, 7.6, 0.7);
  D.txt(ctx, 'Fire TV sticks: My Fire TV \u2192 About.', 650, 900, {
    size: 15,
    family: D.MONO,
    color: 'rgba(244,242,237,0.45)',
    alpha: noteP,
  });
}

// --------------------------------------------------------- 04 ADDRESS

function sceneAddress(ctx, t) {
  rail(ctx, 1, t, t);

  const px = 650;
  const py = 200;
  const pw = 1150;
  const ph = 600;

  const p = D.prog(t, 0.35, 0.9);
  D.clipRise(ctx, px - 20, py - 20, pw + 40, ph + 40, p, 22, () => {
    D.settingsPanel(ctx, {
      x: px,
      y: py,
      w: pw,
      h: ph,
      active: 2,
      rows: ['About', 'Storage', 'Network', 'Install Update'],
      right: (dw) => {
        const ipP = D.prog(t, 1.4, 0.7);
        D.txt(ctx, 'Network', 54, 86, {
          size: 13,
          weight: 500,
          family: D.MONO,
          color: 'rgba(244,242,237,0.34)',
          tracking: 4,
        });
        detailRows(
          ctx,
          dw,
          [
            ['SSID', 'home-wifi'],
            ['IP Address', '192.168.1.42', C.accentInk],
            ['Gateway', '192.168.1.1'],
            ['DNS', '192.168.1.1'],
          ],
          { highlight: 1, highlightP: ipP, x0: 54, y0: 160, gap: 96 }
        );

        const copied = t > 3.1;
        const chipP = D.prog(t, 2.9, 0.4);
        if (chipP > 0) {
          const label = copied ? 'COPIED' : 'COPY';
          ctx.save();
          ctx.globalAlpha = chipP;
          D.rr(ctx, dw - 152, 200, 104, 34, 17);
          ctx.fillStyle = copied ? 'rgba(116,226,163,0.12)' : 'rgba(244,242,237,0.07)';
          ctx.fill();
          ctx.strokeStyle = copied ? 'rgba(116,226,163,0.45)' : 'rgba(244,242,237,0.15)';
          ctx.stroke();
          ctx.restore();
          D.txt(ctx, label, dw - 100, 223, {
            size: 11,
            weight: 500,
            family: D.MONO,
            color: copied ? C.good : C.ink3,
            align: 'center',
            tracking: 2,
            alpha: chipP,
          });
        }
      },
    });
  });

  const p2 = D.prog(t, 4.6, 0.8);
  D.clipRise(ctx, 640, 838, 1160, 120, p2, 16, () => {
    D.txt(ctx, 'Now give it to your agent. That\u2019s the whole setup.', 650, 878, {
      size: 23,
      weight: 300,
      color: C.ink2,
      tracking: 0,
    });
    D.txt(ctx, 'No root. No USB cable. No factory reset.', 650, 914, {
      size: 15,
      family: D.MONO,
      color: C.ink4,
      tracking: 0.4,
    });
  });
}

// ----------------------------------------------------------- 05 AGENT

function sceneAgent(ctx, t) {
  rail(ctx, 2, t, t);

  const x = 600;
  const y = 176;
  const w = 1200;
  const h = 620;

  const p = D.prog(t, 0.25, 0.8);
  const push = 1 + 0.012 * D.easeOut(D.prog(t, 0, 16));

  ctx.save();
  ctx.translate(x + w / 2, y + h / 2);
  ctx.scale(push, push);
  ctx.translate(-(x + w / 2), -(y + h / 2));
  D.clipRise(ctx, x - 20, y - 20, w + 40, h + 40, p, 20, () => {
    D.terminal(ctx, {
      x,
      y,
      w,
      h,
      lines: AGENT_LINES,
      t,
      title: 'dumbtv \u2014 agent',
      fontSize: 24,
      pad: 36,
    });
  });
  ctx.restore();

  const noteP = D.prog(t, 14.2, 0.8);
  D.txt(ctx, 'Claude Code \u00b7 Codex \u00b7 pi \u2014 any agent that can run a shell.', 600, 866, {
    size: 16,
    family: D.MONO,
    color: 'rgba(244,242,237,0.42)',
    alpha: noteP,
    tracking: 0.3,
  });
}

// ------------------------------------------------------------ 06 PASS

function scenePass(ctx, t) {
  rail(ctx, 3, t, t);

  const x = 620;
  const top = 200;
  const rowH = 148;
  const w = 1180;

  ctx.save();
  for (let i = 0; i < 5; i++) {
    const r = PASS_ROWS[i];
    const p = D.prog(t, r.at, 0.7);
    if (p <= 0) continue;
    const yy = top + i * rowH;

    ctx.save();
    ctx.globalAlpha = D.easeOut(p);
    ctx.translate(0, (1 - D.easeOutExpo(p)) * 18);

    // tag
    ctx.save();
    D.rr(ctx, x, yy - 26, 56, 30, 4);
    ctx.strokeStyle = 'rgba(244,242,237,0.18)';
    ctx.stroke();
    ctx.restore();
    D.txt(ctx, r.tag, x + 28, yy - 5, {
      size: 11,
      weight: 500,
      family: D.MONO,
      color: C.ink3,
      align: 'center',
      tracking: 1.6,
    });

    D.txt(ctx, r.name, x + 84, yy + 2, {
      size: 40,
      weight: 300,
      color: C.ink,
      tracking: -0.6,
    });

    const dx = x + 470;
    D.txt(ctx, r.note, dx, yy - 2, {
      size: 17,
      family: D.MONO,
      color: C.ink3,
    });

    const numP = D.easeOut(D.prog(t, r.at + 0.15, 1.3));
    const n = Math.round(r.n * numP);
    D.txt(ctx, String(n), x + w, yy + 8, {
      size: 64,
      weight: 200,
      family: D.DISPLAY,
      color: i === 3 ? C.accentInk : C.ink,
      align: 'right',
      tracking: -2.5,
    });
    D.txt(ctx, r.unit, x + w - 4, yy + 56, {
      size: 13,
      family: D.MONO,
      color: 'rgba(244,242,237,0.30)',
      align: 'right',
      tracking: 0.6,
    });

    const barP = D.easeOut(D.prog(t, r.at + 0.1, 1.4));
    ctx.fillStyle = 'rgba(244,242,237,0.07)';
    ctx.fillRect(x, yy + 72, w, 1);
    ctx.fillStyle = i === 3 ? C.accent : 'rgba(244,242,237,0.45)';
    ctx.fillRect(x, yy + 72, w * barP, 1);

    ctx.restore();
  }
  ctx.restore();

  const endP = D.prog(t, 12.6, 0.9);
  D.clipRise(ctx, 620, 900, 1180, 80, endP, 14, () => {
    D.txt(ctx, 'Five levers. None of them need root.', 620, 948, {
      size: 24,
      weight: 300,
      color: C.ink2,
    });
  });
}

// ---------------------------------------------------------- 07 VERIFY

function sceneVerify(ctx, t) {
  rail(ctx, 4, t, t);

  const x = 520;
  const y = 160;
  const w = 1280;
  const h = 730;

  const p = D.prog(t, 0.2, 0.8);
  ctx.save();
  D.clipRise(ctx, x - 20, y - 20, w + 40, h + 40, p, 18, () => {
    D.terminal(ctx, {
      x,
      y,
      w,
      h,
      t,
      title: 'dumbtv \u2014 verify',
      bar: true,
      fontSize: 21,
      pad: 36,
      cursor: false,
      lines: [{ at: 0.3, kind: 'cmd', text: './scripts/verify.sh', cps: 34 }],
    });

    const lx = x + 36;
    const top = y + 190;
    D.txt(ctx, 'host', lx + 84, top, {
      size: 13,
      weight: 500,
      family: D.MONO,
      color: 'rgba(244,242,237,0.30)',
      tracking: 2.6,
    });
    D.txt(ctx, 'result', lx + 660, top, {
      size: 13,
      weight: 500,
      family: D.MONO,
      color: 'rgba(244,242,237,0.30)',
      tracking: 2.6,
    });

    D.hairline(ctx, lx, top + 16, lx + w - 72, top + 16, 'rgba(244,242,237,0.12)');

    VERIFY_ROWS.forEach((row, i) => {
      const rp = D.prog(t, row.at, 0.4);
      if (rp <= 0) return;
      const yy = top + 70 + i * 46;
      ctx.save();
      ctx.globalAlpha = D.easeOut(rp);
      ctx.translate(0, (1 - D.easeOut(rp)) * 6);
      if (row.big) {
        ctx.beginPath();
        ctx.arc(lx + 6, yy - 7, 4, 0, Math.PI * 2);
        ctx.fillStyle = C.good;
        ctx.fill();
        D.txt(ctx, row.a, lx + 26, yy, {
          size: 25,
          weight: 500,
          family: D.MONO,
          color: C.good,
        });
      } else {
        D.txt(ctx, row.s, lx, yy, {
          size: 17,
          weight: 400,
          family: D.MONO,
          color: C.good,
        });
        D.txt(ctx, row.a, lx + 84, yy, {
          size: 19,
          family: D.MONO,
          color: C.ink2,
        });
        D.txt(ctx, row.b, lx + 660, yy, {
          size: 19,
          family: D.MONO,
          color: C.ink3,
        });
      }
      ctx.restore();
    });

  });
  ctx.restore();

  const noteP = D.prog(t, 9.4, 0.8);
  D.txt(ctx, 'The agent runs this itself, then fixes what failed.', 520, 938, {
    size: 16,
    family: D.MONO,
    color: 'rgba(244,242,237,0.44)',
    alpha: noteP,
  });
}

// ---------------------------------------------------------- 08 RESULT

function sceneResult(ctx, t, images) {
  const img = images.home;
  const push = 1 + 0.038 * D.easeOut(D.clamp(t / 20));

  const baseW = 1400;
  const baseH = 788;
  const cx = W / 2;
  const cy = 500;

  D.glowAt(ctx, cx, cy, 900, 'rgba(76,104,150,0.16)', D.fade(t, 0, 1.2, 19.2, 0.8));

  ctx.save();
  ctx.translate(cx, cy);
  ctx.scale(push, push);
  ctx.translate(-cx, -cy);

  const sx = cx - baseW / 2;
  const sy = cy - baseH / 2;

  D.shadowUnder(ctx, sx, sy, baseW, baseH, 1.2);
  ctx.fillStyle = '#000';
  ctx.fillRect(sx - 5, sy - 5, baseW + 10, baseH + 10);
  D.drawCover(ctx, img, sx, sy, baseW, baseH);
  ctx.strokeStyle = 'rgba(244,242,237,0.16)';
  ctx.lineWidth = 1;
  ctx.strokeRect(sx - 5.5, sy - 5.5, baseW + 11, baseH + 11);
  ctx.restore();

  const phrases = [
    { at: 0.5, out: 4.7, text: 'This is what\u2019s left.' },
    { at: 4.9, out: 9.2, text: 'Two apps. Four inputs. A clock.' },
    { at: 9.4, out: 13.6, text: '41 KB. Zero permissions. No account.' },
    { at: 13.8, out: 19.6, text: 'HOME lands here in 0.2 seconds.' },
  ];
  for (const ph of phrases) {
    const a = D.fade(t, ph.at, 0.45, ph.out, 0.35);
    if (a > 0) {
      ctx.save();
      ctx.globalAlpha = a;
      ctx.translate(0, (1 - D.easeOut(D.prog(t, ph.at, 0.45))) * 10);
      D.txt(ctx, ph.text, cx, 964, {
        size: 40,
        weight: 300,
        color: C.ink,
        align: 'center',
        tracking: -0.4,
      });
      ctx.restore();
    }
  }

  const lbl = D.fade(t, 0.8, 0.8, 19.0, 0.8);
  D.txt(ctx, 'tv.dumbtv.home', cx, 74, {
    size: 12,
    family: D.MONO,
    color: 'rgba(244,242,237,0.30)',
    align: 'center',
    tracking: 3.2,
    alpha: lbl,
  });

  const out = 1 - D.prog(t, 19.4, 0.7);
  if (out < 1) {
    ctx.fillStyle = `rgba(7,8,10,${1 - out})`;
    ctx.fillRect(0, 0, W, H);
  }
}

// --------------------------------------------------------- 09 HONESTY

function sceneHonesty(ctx, t) {
  D.glowAt(ctx, W / 2, 460, 700, 'rgba(46,60,84,0.16)', D.fade(t, 0, 2));

  const p1 = D.prog(t, 0.5, 0.9);
  D.clipRise(ctx, 0, 388, W, 130, p1, 24, () => {
    D.txt(ctx, 'It will not replace Fire OS.', W / 2, 476, {
      size: 66,
      weight: 300,
      color: C.ink,
      align: 'center',
      tracking: -2,
    });
  });

  const p2 = D.prog(t, 2.6, 0.9);
  D.clipRise(ctx, 0, 560, W, 80, p2, 14, () => {
    D.txt(ctx, 'bootloader signed  \u00b7  /system verified  \u00b7  no root for this build', W / 2, 606, {
      size: 17,
      family: D.MONO,
      color: C.ink3,
      align: 'center',
      tracking: 0.6,
    });
  });

  const p3 = D.prog(t, 5.5, 0.9);
  D.clipRise(ctx, 0, 660, W, 80, p3, 16, () => {
    D.txt(ctx, 'That\u2019s the hardware. Not the repo.', W / 2, 716, {
      size: 38,
      weight: 300,
      color: C.ink2,
      align: 'center',
      tracking: -0.6,
    });
  });

  const p4 = D.prog(t, 8.2, 0.9);
  D.clipRise(ctx, 0, 792, W, 130, p4, 14, () => {
    D.txt(ctx, 'What it does change, it can undo.', W / 2, 848, {
      size: 28,
      weight: 300,
      color: C.ink,
      align: 'center',
    });
    D.txt(ctx, './scripts/restore.sh', W / 2, 908, {
      size: 20,
      family: D.MONO,
      color: 'rgba(143,186,255,0.85)',
      align: 'center',
      tracking: 0.5,
    });
  });

  const out = 1 - D.prog(t, 11.2, 0.8);
  if (out < 1) {
    ctx.fillStyle = `rgba(7,8,10,${1 - out})`;
    ctx.fillRect(0, 0, W, H);
  }
}

// ----------------------------------------------------------- 10 OUTRO

function sceneOutro(ctx, t) {
  const titleA = D.fade(t, 0.35, 0.8, 10.4, 0.7);
  const titleY = 300;

  if (titleA > 0) {
    ctx.save();
    ctx.globalAlpha = titleA;
    ctx.translate(0, (1 - D.easeOut(D.prog(t, 0.35, 0.8))) * 14);
    D.txt(ctx, 'Point your agent at the repo.', 140, titleY, {
      size: 56,
      weight: 300,
      color: C.ink,
      tracking: -1.4,
    });
    ctx.restore();
  }

  const cardP = D.prog(t, 1.5, 0.8);
  if (cardP > 0) {
    const a = cardP * D.clamp(1 - D.prog(t, 10.4, 0.7));
    ctx.save();
    ctx.globalAlpha = a;
    ctx.translate(0, (1 - D.easeOutExpo(cardP)) * 18);
    D.panel(ctx, 140, 360, 1060, 216, { fill: '#0A0B0E', border: 'rgba(244,242,237,0.13)', radius: 12 });
    ctx.fillStyle = C.accent;
    ctx.fillRect(140, 360, 3, 216);
    D.txt(ctx, 'Read AGENTS.md.', 190, 440, {
      size: 41,
      family: D.MONO,
      color: C.ink,
    });
    D.txt(ctx, 'Set up my TV at 192.168.1.42.', 190, 502, {
      size: 41,
      family: D.MONO,
      color: C.ink,
    });
    ctx.restore();

    const a2 = D.prog(t, 4.6, 0.7) * (1 - D.prog(t, 10.4, 0.7));
    D.txt(ctx, 'Claude Code \u00b7 Codex \u00b7 pi \u2014 any agent that can run a shell.', 140, 642, {
      size: 18,
      family: D.MONO,
      color: 'rgba(244,242,237,0.46)',
      alpha: a2,
    });

    const urlP = D.prog(t, 6.8, 0.8) * (1 - D.prog(t, 10.6, 0.6));
    if (urlP > 0) {
      D.txt(ctx, 'github.com/humblemuzzu/dumbtv', 140, 776, {
        size: 50,
        family: D.MONO,
        color: C.ink,
        alpha: urlP,
        tracking: -0.6,
      });
      const uw = D.measure(ctx, 'github.com/humblemuzzu/dumbtv', {
        size: 50,
        family: D.MONO,
        tracking: -0.6,
      });
      ctx.fillStyle = C.accent;
      ctx.globalAlpha = urlP * D.easeOut(D.prog(t, 7.3, 0.8));
      ctx.fillRect(140, 802, uw, 2);
      ctx.globalAlpha = 1;
    }
  }

  const endP = D.prog(t, 11.0, 1.0);
  if (endP > 0) {
    const a = D.easeOut(endP);
    ctx.save();
    ctx.globalAlpha = a;
    const dy = (1 - a) * 12;
    D.txt(ctx, 'dumbtv', W / 2 - 3, 468 + dy, {
      size: 128,
      weight: 500,
      color: C.ink,
      align: 'center',
      tracking: -5.2,
    });
    D.hairline(ctx, W / 2 - 44, 508 + dy, W / 2 + 44, 508 + dy, 'rgba(61,139,255,0.85)', 2);
    D.txt(ctx, 'no ads  \u00b7  no tracking  \u00b7  no root', W / 2, 570 + dy, {
      size: 22,
      family: D.MONO,
      color: C.ink2,
      align: 'center',
      tracking: 1.2,
    });
    D.txt(ctx, 'github.com/humblemuzzu/dumbtv', W / 2, 642 + dy, {
      size: 17,
      family: D.MONO,
      color: C.ink3,
      align: 'center',
      tracking: 1.2,
    });
    D.txt(ctx, 'MIT  \u00b7  41 KB launcher  \u00b7  Fire OS 7 and 8', W / 2, 700 + dy, {
      size: 14,
      family: D.MONO,
      color: 'rgba(244,242,237,0.30)',
      align: 'center',
      tracking: 1.4,
    });
    ctx.restore();
  }

  const out = 1 - D.prog(t, 17.4, 1.1);
  if (out < 1) {
    ctx.fillStyle = `rgba(7,8,10,${1 - out})`;
    ctx.fillRect(0, 0, W, H);
  }
}

// ------------------------------------------------------------- MASTER

const SCENES = {
  acr: sceneAcr,
  wall: sceneWall,
  title: sceneTitle,
  devmode: sceneDevmode,
  address: sceneAddress,
  agent: sceneAgent,
  pass: scenePass,
  verify: sceneVerify,
  result: sceneResult,
  honesty: sceneHonesty,
  outro: sceneOutro,
};

const ORDER = [
  ['acr', S.acr, S.wall],
  ['wall', S.wall, S.title],
  ['title', S.title, S.devmode],
  ['devmode', S.devmode, S.address],
  ['address', S.address, S.agent],
  ['agent', S.agent, S.pass],
  ['pass', S.pass, S.verify],
  ['verify', S.verify, S.result],
  ['result', S.result, S.honesty],
  ['honesty', S.honesty, S.outro],
  ['outro', S.outro, 1e9],
];

export function drawFrame(ctx, t, images) {
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.globalAlpha = 1;
  ctx.globalCompositeOperation = 'source-over';
  D.bg(ctx, t);

  const scene = ORDER.find(([, a, b]) => t >= a && t < b);
  if (scene) {
    const [name, start] = scene;
    SCENES[name](ctx, t - start, images);
  }

  const blackouts = [
    [14.05, 14.3],
    [25.1, 25.7],
  ];
  for (const [a, b] of blackouts) {
    if (t >= a && t <= b) {
      const k = 1 - Math.abs((t - (a + b) / 2) / ((b - a) / 2));
      ctx.fillStyle = `rgba(7,8,10,${0.9 * k})`;
      ctx.fillRect(0, 0, W, H);
    }
  }

  if (t < 0.7) {
    ctx.fillStyle = `rgba(0,0,0,${1 - t / 0.7})`;
    ctx.fillRect(0, 0, W, H);
  }
  if (t > 151.6) {
    ctx.fillStyle = `rgba(0,0,0,${D.clamp((t - 151.6) / 1.4)})`;
    ctx.fillRect(0, 0, W, H);
  }
}
