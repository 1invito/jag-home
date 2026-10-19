export const W2 = 1920;
export const H2 = 1080;
export const FPS2 = 30;
export const BPM2 = 120;
export const BEAT2 = 60 / BPM2;
export const BAR2 = BEAT2 * 4;
export const DUR2 = 29.0;

export const S2 = {
  hook: 0,
  dumbtv: 2.0,
  stats: 3.0,
  credo: 5.0,
  steps: 6.0,
  agent: 9.0,
  pass: 12.0,
  result: 15.0,
  verify: 18.0,
  honesty: 21.0,
  cta: 23.5,
  end: 25.5,
};

export const PLATES = {
  cobalt: { a: '#2A49FF', b: '#1027B8', ink: '#F7F1E4', arc: '#FF5A3C' },
  coral: { a: '#FF5A3C', b: '#DA2E12', ink: '#0A0B0D', arc: '#2A49FF' },
  acid: { a: '#E8FF4A', b: '#BFD900', ink: '#0A0B0D', arc: '#0A0B0D' },
  mint: { a: '#5BF0B0', b: '#12B47A', ink: '#0A0B0D', arc: '#2A49FF' },
  cream: { a: '#F7F1E4', b: '#E6D8C0', ink: '#0A0B0D', arc: '#FF5A3C' },
  ink: { a: '#0A0B0D', b: '#14161C', ink: '#F7F1E4', arc: '#2A49FF' },
};

export const HOOK = [
  { t: 0.0, word: 'YOUR TV', plate: 'bars', ink: '#0A0B0D' },
  { t: 0.5, word: 'KNOWS', plate: 'coral' },
  { t: 1.0, word: 'WHAT', plate: 'acid' },
  { t: 1.5, word: 'YOU WATCH.', plate: 'ink' },
];

export const STATS = [
  { t: 3.0, n: 65, unit: 'packages off', plate: 'cobalt' },
  { t: 3.5, n: 396, unit: 'hosts blocked', plate: 'coral' },
  { t: 4.0, n: 0, unit: 'permissions', plate: 'mint' },
  { t: 4.5, n: 10, unit: 'minutes', plate: 'acid' },
];

export const STEPS = [
  { t: 6.0, n: '01', title: 'Developer mode', detail: 'Device & Software \u2192 About', plate: 'cobalt' },
  { t: 7.0, n: '02', title: 'The address', detail: '192.168.1.42', plate: 'coral', mono: true },
  { t: 8.0, n: '03', title: 'The agent', detail: 'read AGENTS.md', plate: 'acid' },
];

export const PASS = [
  { t: 12.0, tag: 'PKG', n: '65', label: 'packages off', color: '#2A49FF' },
  { t: 12.6, tag: 'SET', n: '16', label: 'settings tightened', color: '#FF5A3C' },
  { t: 13.2, tag: 'OPS', n: '7', label: 'starved', color: '#5BF0B0' },
  { t: 13.8, tag: 'DNS', n: '396', label: 'hosts blocked', color: '#E8FF4A' },
  { t: 14.4, tag: 'UI', n: '41', label: 'KB launcher', color: '#F7F1E4' },
];

export const VERIFY = [
  { t: 18.0, host: 'softwareupdates.amazon.com', res: 'BLOCKED', ok: true },
  { t: 18.5, host: 'arcus-uswest.amazon.com', res: 'BLOCKED', ok: true },
  { t: 19.0, host: 'api.amazonalexa.com', res: 'BLOCKED', ok: true },
  { t: 19.5, host: 'www.youtube.com', res: 'STILL WORKS', ok: true },
];

export const AGENT_PROMPT = 'Read AGENTS.md. Set up my TV at 192.168.1.42.';
export const AGENT_PLAN = '65 packages  \u00b7  396 hosts  \u00b7  new home';

export const CUES2 = [
  { t: 0.0, k: 'static', g: 0.8 },
  { t: 0.0, k: 'impact', g: 0.5 },
  { t: 0.5, k: 'click', g: 0.5 },
  { t: 1.0, k: 'click', g: 0.5 },
  { t: 1.5, k: 'click', g: 0.6 },
  { t: 2.0, k: 'drop', g: 1.0 },
  { t: 3.0, k: 'tick', g: 0.5 },
  { t: 3.5, k: 'tick', g: 0.5 },
  { t: 4.0, k: 'tick', g: 0.5 },
  { t: 4.5, k: 'tick', g: 0.5 },
  { t: 5.0, k: 'click', g: 0.6 },
  { t: 5.5, k: 'click', g: 0.6 },
  { t: 6.0, k: 'whoosh', g: 0.5 },
  { t: 7.0, k: 'whoosh', g: 0.5 },
  { t: 8.0, k: 'whoosh', g: 0.5 },
  { t: 9.0, k: 'click', g: 0.6 },
  { t: 12.0, k: 'riser', g: 0.35, d: 1.0 },
  { t: 12.0, k: 'tick', g: 0.7 },
  { t: 12.6, k: 'tick', g: 0.6 },
  { t: 13.2, k: 'tick', g: 0.6 },
  { t: 13.8, k: 'tick', g: 0.7 },
  { t: 14.4, k: 'tick', g: 0.8 },
  { t: 15.0, k: 'drop', g: 0.8 },
  { t: 18.0, k: 'blip', g: 0.5 },
  { t: 18.5, k: 'blip', g: 0.5 },
  { t: 19.0, k: 'blip', g: 0.5 },
  { t: 19.5, k: 'blip', g: 0.5 },
  { t: 20.2, k: 'chime', g: 0.7 },
  { t: 21.0, k: 'impact', g: 0.5 },
  { t: 22.7, k: 'swell', g: 0.5 },
  { t: 23.5, k: 'riser', g: 0.4, d: 1.2 },
  { t: 24.5, k: 'whoosh', g: 0.5 },
  { t: 25.5, k: 'drop', g: 1.0 },
];
