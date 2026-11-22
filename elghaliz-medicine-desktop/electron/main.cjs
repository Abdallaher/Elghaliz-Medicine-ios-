// Electron main process — serves the built web app over an internal loopback HTTP
// server and loads it over http:// (avoids file:// ES-module restrictions entirely).
// Also writes a startup diagnostic log so runtime failures can be inspected.
const { app, BrowserWindow } = require('electron');
const path = require('node:path');
const fs = require('node:fs');
const http = require('node:http');
const os = require('node:os');

const logDir = path.join(process.env.LOCALAPPDATA || os.tmpdir(), 'ElghalizMedicine');
const logFile = path.join(logDir, 'startup.log');
function log(msg) {
  try {
    fs.mkdirSync(logDir, { recursive: true });
    fs.appendFileSync(logFile, `[${new Date().toISOString()}] ${msg}\n`);
  } catch {
    /* ignore */
  }
}

log(`PROCESS START argv=${JSON.stringify(process.argv)}`);
process.on('uncaughtException', (e) => log('uncaughtException: ' + ((e && e.stack) || e)));
process.on('unhandledRejection', (e) => log('unhandledRejection: ' + ((e && e.stack) || e)));

app.setName('Elghaliz Medicine');
app.on('render-process-gone', (_e, _wc, d) => log('render-process-gone: ' + JSON.stringify(d)));
app.on('child-process-gone', (_e, d) => log('child-process-gone: ' + JSON.stringify(d)));

const isSmoke = process.argv.includes('--smoke-test');
const smokeOut = process.env.ELG_SMOKE_OUT || '';
function writeSmoke(t) {
  if (!smokeOut) return;
  try {
    fs.writeFileSync(smokeOut, t);
  } catch {
    /* ignore */
  }
}

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.webp': 'image/webp',
  '.ttf': 'font/ttf',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.json': 'application/json; charset=utf-8',
  '.ico': 'image/x-icon',
};

function startStaticServer() {
  const root = path.join(__dirname, '..', 'dist');
  const server = http.createServer((req, res) => {
    try {
      let urlPath = decodeURIComponent((req.url || '/').split('?')[0]);
      if (urlPath === '/' || urlPath === '') urlPath = '/index.html';
      let file = path.join(root, urlPath);
      let exists = false;
      try {
        exists = fs.existsSync(file) && fs.statSync(file).isFile();
      } catch {
        exists = false;
      }
      if (!exists) file = path.join(root, 'index.html');
      const ext = path.extname(file).toLowerCase();
      const data = fs.readFileSync(file);
      res.writeHead(200, { 'Content-Type': MIME[ext] || 'application/octet-stream' });
      res.end(data);
    } catch {
      res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
      res.end('Not found');
    }
  });
  return new Promise((resolve) => {
    server.listen(0, '127.0.0.1', () => resolve(server.address().port));
  });
}

async function createWindow() {
  log('createWindow start');
  const port = await startStaticServer();
  log('static server on port ' + port);

  const win = new BrowserWindow({
    width: 1366,
    height: 900,
    minWidth: 960,
    minHeight: 640,
    title: 'Elghaliz Medicine',
    backgroundColor: '#0f3d2e',
    autoHideMenuBar: true,
    show: !isSmoke,
    icon: path.join(__dirname, 'icon.png'),
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
    },
  });
  win.setMenuBarVisibility(false);

  win.webContents.on('did-finish-load', () => {
    log('did-finish-load OK');
    if (isSmoke) {
      writeSmoke('OK');
      app.quit();
    }
  });
  win.webContents.on('did-fail-load', (_e, code, desc) => {
    log(`did-fail-load ${code} ${desc}`);
    if (isSmoke) {
      writeSmoke(`FAIL ${code} ${desc}`);
      app.exit(1);
    }
  });

  log('loading http://127.0.0.1:' + port + '/index.html');
  win.loadURL(`http://127.0.0.1:${port}/index.html`);

  if (isSmoke) {
    setTimeout(() => {
      writeSmoke('TIMEOUT');
      app.exit(1);
    }, 15000);
  }
}

app.whenReady().then(() => {
  log('app ready');
  createWindow().catch((e) => log('createWindow failed: ' + ((e && e.stack) || e)));
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
