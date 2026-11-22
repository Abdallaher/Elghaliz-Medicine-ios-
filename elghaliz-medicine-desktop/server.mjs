// Minimal static file server for the built Elghaliz Medicine web app.
// Also exposes POST /__log so the app can report client-side auth errors to a file.
import http from 'node:http';
import { readFile, stat, appendFile } from 'node:fs/promises';
import { extname, join } from 'node:path';

const PORT = Number(process.env.PORT || 8787);
const ROOT = join(process.cwd(), 'dist');
const LOG = join(process.cwd(), 'elghaliz-auth.log');

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.webp': 'image/webp',
  '.ttf': 'font/ttf',
  '.woff2': 'font/woff2',
  '.json': 'application/json; charset=utf-8',
  '.ico': 'image/x-icon',
};

const server = http.createServer((req, res) => {
  const url = req.url || '/';

  // Client error reporting endpoint
  if (req.method === 'POST' && url.startsWith('/__log')) {
    let body = '';
    req.on('data', (c) => {
      body += c;
      if (body.length > 8000) body = body.slice(0, 8000);
    });
    req.on('end', () => {
      appendFile(LOG, `${new Date().toISOString()} ${body}\n`).catch(() => {});
      res.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8' });
      res.end('ok');
    });
    return;
  }

  (async () => {
    try {
      let urlPath = decodeURIComponent(url.split('?')[0]);
      if (urlPath === '/' || urlPath === '') urlPath = '/index.html';
      let file = join(ROOT, urlPath);
      try {
        await stat(file);
      } catch {
        file = join(ROOT, 'index.html');
      }
      const data = await readFile(file);
      res.writeHead(200, { 'Content-Type': MIME[extname(file).toLowerCase()] || 'application/octet-stream' });
      res.end(data);
    } catch {
      res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
      res.end('Not found');
    }
  })();
});

server.listen(PORT, '127.0.0.1', () => {
  console.log(`Elghaliz Medicine server on http://localhost:${PORT}`);
});
