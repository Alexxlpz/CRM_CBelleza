// Capturas de Cuquora con Brave/Chrome sin ventana (protocolo DevTools, sin dependencias).
// Uso:
//   node shot.mjs --base http://localhost:8091 --path /worker/clients/6 --name ficha \
//        [--user carlos@cbelleza.com] [--widths 1920,1440,1100,960,768,390] [--out DIR] [--run "JS tras cargar"] [--wait ms]
// Sin --user hace las capturas sin sesión. Con --run la captura es solo de la pantalla visible
// (útil para un modal abierto); sin él, de la página entera (hasta 4000 px de alto).
import { spawn } from 'node:child_process';
import { existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const args = Object.fromEntries(process.argv.slice(2).reduce((acc, v, i, all) =>
    v.startsWith('--') ? [...acc, [v.slice(2), all[i + 1]]] : acc, []));
const base = args.base || 'http://localhost:8091';
const widths = (args.widths || '1920,1440,1100,960,768,390').split(',').map(Number);
const out = args.out || '.';
const browserPath = ['/usr/bin/brave', '/opt/google/chrome/chrome', '/usr/bin/chromium', '/usr/bin/google-chrome',
    'C:/Program Files/BraveSoftware/Brave-Browser/Application/brave.exe', 'C:/Program Files/Google/Chrome/Application/chrome.exe']
    .find(p => existsSync(p));
mkdirSync(out, { recursive: true });

async function login(user) {
    const page = await fetch(base + '/login');
    const cookie = page.headers.getSetCookie().map(c => c.split(';')[0]).join('; ');
    const csrf = (await page.text()).match(/name="_csrf"[^>]*value="([^"]+)"/)?.[1];
    const res = await fetch(base + '/login', {
        method: 'POST', redirect: 'manual',
        headers: { cookie, 'content-type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ identifier: user, password: args.password || 'Cuquora2026!', _csrf: csrf })
    });
    const session = res.headers.getSetCookie().find(c => c.startsWith('JSESSIONID='));
    if (!session || /login/.test(String(res.headers.get('location')))) throw new Error('No se pudo iniciar sesión como ' + user);
    return session.split(';')[0].split('=')[1];
}

const port = 9300 + Math.floor(Math.random() * 500);
const browser = spawn(browserPath, ['--headless=new', `--remote-debugging-port=${port}`, '--no-first-run',
    '--hide-scrollbars', `--user-data-dir=${join(tmpdir(), 'cuquora-shots-' + port)}`, 'about:blank'], { stdio: 'ignore' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
let ws;
for (let i = 0; i < 50 && !ws; i++) {
    await sleep(200);
    try { ws = (await (await fetch(`http://127.0.0.1:${port}/json`)).json()).find(t => t.type === 'page')?.webSocketDebuggerUrl; } catch { /* aún arrancando */ }
}
const sock = new WebSocket(ws);
await new Promise(r => { sock.onopen = r; });
let seq = 0;
const pending = new Map();
sock.onmessage = m => { const d = JSON.parse(m.data); pending.get(d.id)?.(d); pending.delete(d.id); };
const send = (method, params = {}) => new Promise(r => { pending.set(++seq, r); sock.send(JSON.stringify({ id: seq, method, params })); });

try {
    await send('Page.enable');
    if (args.user) await send('Network.setCookie', { name: 'JSESSIONID', value: await login(args.user), url: base });
    for (const width of widths) {
        const mobile = width < 600;
        await send('Emulation.setDeviceMetricsOverride', { width, height: mobile ? 844 : 900, deviceScaleFactor: 1, mobile });
        const nav = await send('Page.navigate', { url: base + args.path });
        if (nav.error || nav.result?.errorText) console.error('Navegación fallida:', JSON.stringify(nav.error || nav.result));
        await sleep(Number(args.wait) || 2000);
        // El aviso de cookies tapa el pie: se oculta para la captura
        await send('Runtime.evaluate', { expression: "document.getElementById('cookieBanner')?.setAttribute('hidden','')" });
        if (args.run) {
            const r = await send('Runtime.evaluate', { expression: args.run, awaitPromise: true });
            if (r.result?.exceptionDetails) console.error('Error en --run:', r.result.exceptionDetails.exception?.description);
            await sleep(500);
        }
        const { result } = await send('Page.getLayoutMetrics');
        const height = Math.min(Math.ceil(result.cssContentSize.height), 4000);
        const shot = await send('Page.captureScreenshot', args.run ? { format: 'png' }
            : { format: 'png', captureBeyondViewport: true, clip: { x: 0, y: 0, width, height, scale: 1 } });
        const file = `${out}/${args.name || 'captura'}-${width}.png`;
        writeFileSync(file, Buffer.from(shot.result.data, 'base64'));
        console.log(file);
    }
} finally {
    sock.close();
    browser.kill();
}
