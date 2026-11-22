import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const DIST_DIR = path.resolve(__dirname, 'dist');
const INDEX_HTML = path.join(DIST_DIR, 'index.html');

const HOST = '0.0.0.0';
const PORT = Number(process.env.PORT) || 8080;

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.mjs': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.webmanifest': 'application/manifest+json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.webp': 'image/webp',
  '.ico': 'image/x-icon',
  '.ttf': 'font/ttf',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.txt': 'text/plain; charset=utf-8',
  '.xml': 'application/xml; charset=utf-8',
};

function getCacheControl(relPath) {
  const base = path.basename(relPath);
  if (
    base === 'index.html' ||
    base === 'sw.js' ||
    base === 'registerSW.js' ||
    base.startsWith('workbox-')
  ) {
    return 'no-cache, no-store, must-revalidate';
  }
  if (base === 'manifest.json' || base === 'manifest.webmanifest') {
    return 'public, max-age=3600';
  }
  if (relPath.startsWith('assets/') || relPath.startsWith('fonts/')) {
    return 'public, max-age=31536000, immutable';
  }
  return 'public, max-age=3600';
}

function getContentType(filePath) {
  const base = path.basename(filePath);
  if (base === 'manifest.json' || base === 'manifest.webmanifest') {
    return 'application/manifest+json; charset=utf-8';
  }
  const ext = path.extname(filePath).toLowerCase();
  return MIME_TYPES[ext] || 'application/octet-stream';
}

function sendFile(req, res, filePath, relPath) {
  fs.stat(filePath, (statErr, stat) => {
    if (statErr || !stat.isFile()) {
      res.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' });
      res.end('dist/index.html not found. Run "npm run build" first.');
      return;
    }

    const headers = {
      'Content-Type': getContentType(filePath),
      'Content-Length': stat.size,
      'Cache-Control': getCacheControl(relPath),
      'X-Content-Type-Options': 'nosniff',
    };

    if (path.basename(filePath) === 'sw.js') {
      headers['Service-Worker-Allowed'] = '/';
    }

    res.writeHead(200, headers);
    if (req.method === 'HEAD') {
      res.end();
      return;
    }

    const stream = fs.createReadStream(filePath);
    stream.on('error', () => {
      if (!res.headersSent) {
        res.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' });
      }
      res.end('Internal Server Error');
    });
    stream.pipe(res);
  });
}

const server = http.createServer((req, res) => {
  if (req.method !== 'GET' && req.method !== 'HEAD') {
    res.writeHead(405, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('Method Not Allowed');
    return;
  }

  let pathname = '/';
  try {
    const parsed = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);
    pathname = decodeURIComponent(parsed.pathname);
  } catch {
    pathname = '/';
  }

  // Normalize and prevent directory traversal outside dist/
  const cleanRel = pathname.replace(/^\/+/, '');
  const candidatePath = path.resolve(DIST_DIR, cleanRel);
  const insideDist =
    candidatePath === DIST_DIR || candidatePath.startsWith(DIST_DIR + path.sep);

  if (insideDist && cleanRel.length > 0 && fs.existsSync(candidatePath)) {
    const stat = fs.statSync(candidatePath);
    if (stat.isFile()) {
      sendFile(req, res, candidatePath, cleanRel);
      return;
    }
  }

  // SPA Fallback: serve dist/index.html for "/" and all client-side routes ("*")
  sendFile(req, res, INDEX_HTML, 'index.html');
});

server.listen(PORT, HOST, () => {
  console.log(`Elghaliz Medicine PWA production server listening on http://${HOST}:${PORT}`);
});
