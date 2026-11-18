/**
 * Optional SSRF-hardened Image Retrieval Relay for Smart Page Library.
 * Use only if you want a controlled server-side allowlisted relay endpoint
 * configured in the app's Settings -> Optional Relay Endpoint URL.
 *
 * Run: ALLOWED_DOMAINS="example.com,cdn.example.org" PORT=8787 node server.mjs
 */

import http from 'node:http';
import dns from 'node:dns/promises';
import net from 'node:net';

const PORT = Number(process.env.PORT || 8787);
const MAX_BYTES = 20 * 1024 * 1024; // 20 MB
const REQUEST_TIMEOUT_MS = 12_000;
const ALLOWED_DOMAINS = (process.env.ALLOWED_DOMAINS || '')
  .split(',')
  .map((s) => s.trim().toLowerCase())
  .filter(Boolean);

function isPrivateOrReservedIp(ip) {
  if (!net.isIP(ip)) return true;
  if (net.isIPv4(ip)) {
    const [a, b] = ip.split('.').map(Number);
    if (a === 0 || a === 10 || a === 127) return true;
    if (a === 169 && b === 254) return true;
    if (a === 172 && b >= 16 && b <= 31) return true;
    if (a === 192 && b === 168) return true;
    if (a === 100 && b >= 64 && b <= 127) return true;
    if (a >= 224) return true;
    return false;
  }
  const lower = ip.toLowerCase();
  return (
    lower === '::' ||
    lower === '::1' ||
    lower.startsWith('fc') ||
    lower.startsWith('fd') ||
    lower.startsWith('fe80')
  );
}

function isDomainAllowed(hostname) {
  if (ALLOWED_DOMAINS.length === 0) return false;
  const host = hostname.toLowerCase();
  return ALLOWED_DOMAINS.some((d) => host === d || host.endsWith(`.${d}`));
}

const server = http.createServer(async (req, res) => {
  try {
    const reqUrl = new URL(req.url || '/', `http://localhost:${PORT}`);
    if (req.method !== 'GET' || reqUrl.pathname !== '/') {
      res.writeHead(404, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'Not found' }));
      return;
    }

    const targetParam = reqUrl.searchParams.get('url');
    if (!targetParam) {
      res.writeHead(400, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'Missing url query parameter' }));
      return;
    }

    let target;
    try {
      target = new URL(targetParam);
    } catch {
      res.writeHead(400, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'Invalid URL' }));
      return;
    }

    if (target.protocol !== 'http:' && target.protocol !== 'https:') {
      res.writeHead(400, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'Only HTTP/HTTPS supported' }));
      return;
    }

    if (!isDomainAllowed(target.hostname)) {
      res.writeHead(403, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'Domain not in ALLOWED_DOMAINS allowlist' }));
      return;
    }

    // DNS-rebinding & SSRF check
    const resolved = await dns.lookup(target.hostname, { all: true });
    if (!resolved.length || resolved.some((r) => isPrivateOrReservedIp(r.address))) {
      res.writeHead(403, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'Target resolves to private or reserved IP' }));
      return;
    }

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

    const upstream = await fetch(target.toString(), {
      method: 'GET',
      redirect: 'manual',
      signal: controller.signal,
      headers: { Accept: 'image/jpeg,image/*;q=0.8' },
    }).finally(() => clearTimeout(timer));

    if (!upstream.ok) {
      res.writeHead(upstream.status, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: `Upstream HTTP ${upstream.status}` }));
      return;
    }

    const contentType = (upstream.headers.get('content-type') || '').toLowerCase();
    if (contentType.includes('text/html')) {
      res.writeHead(422, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'Upstream returned HTML instead of JPEG' }));
      return;
    }

    const arrayBuf = await upstream.arrayBuffer();
    if (arrayBuf.byteLength > MAX_BYTES) {
      res.writeHead(413, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'Response exceeds max image size' }));
      return;
    }

    const buf = Buffer.from(arrayBuf);
    if (buf.length < 3 || buf[0] !== 0xff || buf[1] !== 0xd8 || buf[2] !== 0xff) {
      res.writeHead(422, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'Invalid JPEG magic bytes' }));
      return;
    }

    res.writeHead(200, {
      'Content-Type': 'image/jpeg',
      'Content-Length': String(buf.length),
      'Cache-Control': 'no-store',
    });
    res.end(buf);
  } catch (err) {
    res.writeHead(502, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: err instanceof Error ? err.message : 'Relay failure' }));
  }
});

server.listen(PORT, () => {
  console.log(`Smart Page Library relay listening on port ${PORT}`);
});
