import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
import { once } from 'node:events';

const ROOT = path.resolve(fileURLToPath(new URL('..', import.meta.url)));
const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.png': 'image/png',
  '.json': 'application/json',
  '.woff2': 'font/woff2',
};

function serve(port) {
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://localhost');
    let p = path.join(ROOT, decodeURIComponent(url.pathname));
    if (url.pathname === '/') p = path.join(ROOT, 'index.html');
    if (!p.startsWith(ROOT) || !fs.existsSync(p) || fs.statSync(p).isDirectory()) {
      res.writeHead(404);
      res.end('not found');
      return;
    }
    res.writeHead(200, { 'Content-Type': MIME[path.extname(p)] || 'application/octet-stream' });
    fs.createReadStream(p).pipe(res);
  });
  return new Promise((resolve) => server.listen(port, '127.0.0.1', () => resolve(server)));
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function connect(port) {
  let page;
  for (let i = 0; i < 80; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${port}/json`)).json();
      page = list.find((t) => t.type === 'page');
      if (page) break;
    } catch {}
    await sleep(150);
  }
  if (!page) throw new Error('chrome debug endpoint never appeared');
  const ws = new WebSocket(page.webSocketDebuggerUrl);
  await once(ws, 'open');
  let id = 0;
  const pending = new Map();
  ws.onmessage = (e) => {
    const msg = JSON.parse(e.data);
    if (msg.id && pending.has(msg.id)) {
      const { resolve, reject } = pending.get(msg.id);
      pending.delete(msg.id);
      if (msg.error) reject(new Error(JSON.stringify(msg.error)));
      else resolve(msg.result);
    }
  };
  const send = (method, params = {}) =>
    new Promise((resolve, reject) => {
      const mid = ++id;
      pending.set(mid, { resolve, reject });
      ws.send(JSON.stringify({ id: mid, method, params }));
    });
  return { ws, send };
}

async function main() {
  const args = Object.fromEntries(
    process.argv.slice(2).reduce((acc, cur, i, arr) => {
      if (cur.startsWith('--')) acc.push([cur.slice(2), arr[i + 1] && !arr[i + 1].startsWith('--') ? arr[i + 1] : true]);
      return acc;
    }, [])
  );

  const port = Number(args.port || 8781);
  const debugPort = Number(args.debug || 9460);
  const fps = Number(args.fps || 30);
  const from = Number(args.from || 0);
  const to = Number(args.to || 10);
  const out = args.out || 'out/segment.mp4';
  const stills = args.stills ? String(args.stills).split(',').map(Number) : null;
  const serveOnly = args.serve === true;
  const audit = args.audit ? String(args.audit).split(',').map(Number) : null;

  const server = await serve(port);
  if (serveOnly) {
    console.log(`serving http://127.0.0.1:${port}`);
    return;
  }

  const chrome = spawn(
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    [
      '--headless=new',
      `--remote-debugging-port=${debugPort}`,
      `--user-data-dir=/tmp/dtv-chrome-${debugPort}`,
      '--window-size=1920,1080',
      '--force-device-scale-factor=1',
      '--hide-scrollbars',
      '--no-first-run',
      '--no-default-browser-check',
      '--disable-background-timer-throttling',
      '--disable-renderer-backgrounding',
      '--disable-backgrounding-occluded-windows',
      `http://127.0.0.1:${port}/${args.page || 'index.html'}`,
    ],
    { stdio: 'ignore' }
  );

  try {
    const { send } = await connect(debugPort);
    await send('Page.enable');
    await send('Runtime.enable');
    await send('Emulation.setDeviceMetricsOverride', {
      width: 1920,
      height: 1080,
      deviceScaleFactor: 1,
      mobile: false,
    });

    for (let i = 0; i < 200; i++) {
      const r = await send('Runtime.evaluate', { expression: 'window.__ready === true', returnByValue: true });
      if (r.result.value === true) break;
      await sleep(100);
    }

    const shoot = async () => {
      const r = await send('Page.captureScreenshot', {
        format: 'png',
        fromSurface: true,
        captureBeyondViewport: false,
        optimizeForSpeed: true,
      });
      return Buffer.from(r.data, 'base64');
    };

    if (audit) {
      const all = [];
      for (const t of audit) {
        const r = await send('Runtime.evaluate', {
          expression: `JSON.stringify(window.__audit(${t}))`,
          returnByValue: true,
        });
        const bad = JSON.parse(r.result.value);
        all.push(...bad);
      }
      console.log(JSON.stringify(all, null, 1));
      return;
    }

    if (stills) {
      fs.mkdirSync(path.dirname(out), { recursive: true });
      for (const t of stills) {
        await send('Runtime.evaluate', { expression: `window.__frame(${t})` });
        await sleep(60);
        const buf = await shoot();
        const name = `${out.replace(/\.mp4$/, '')}-${String(t).replace('.', '_')}.png`;
        fs.writeFileSync(name, buf);
        console.log('still', name);
      }
      return;
    }

    fs.mkdirSync(path.dirname(out), { recursive: true });
    const ff = spawn('ffmpeg', [
      '-y',
      '-f', 'image2pipe',
      '-framerate', String(fps),
      '-i', '-',
      '-an',
      '-c:v', 'libx264',
      '-preset', 'slow',
      '-crf', '15',
      '-pix_fmt', 'yuv420p',
      '-profile:v', 'high',
      '-color_primaries', 'bt709',
      '-color_trc', 'bt709',
      '-colorspace', 'bt709',
      '-movflags', '+faststart',
      out,
    ], { stdio: ['pipe', 'ignore', 'pipe'] });
    let ffErr = '';
    ff.stderr.on('data', (d) => { ffErr += d.toString(); if (ffErr.length > 4000) ffErr = ffErr.slice(-2000); });

    const startFrame = Math.round(from * fps);
    const endFrame = Math.round(to * fps);
    const t0 = Date.now();

    for (let f = startFrame; f < endFrame; f++) {
      const t = f / fps;
      await send('Runtime.evaluate', { expression: `window.__frame(${t})` });
      const buf = await shoot();
      if (!ff.stdin.write(buf)) await once(ff.stdin, 'drain');
      if ((f - startFrame) % 150 === 0) {
        const done = f - startFrame;
        const secs = (Date.now() - t0) / 1000;
        process.stdout.write(`\r${out}  ${done}/${endFrame - startFrame}  ${(done / secs).toFixed(1)} fps   `);
      }
    }
    ff.stdin.end();
    const [code] = await once(ff, 'close');
    if (code !== 0) throw new Error('ffmpeg failed: ' + ffErr);
    console.log(`\ndone ${out} in ${((Date.now() - t0) / 1000).toFixed(1)}s`);
  } finally {
    chrome.kill('SIGKILL');
    server.close();
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
