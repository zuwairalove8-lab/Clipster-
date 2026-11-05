// Serves the capture page for the preview and for local checks.
// Unknown extension-less paths fall back to index.html so /c/<token> works
// whether or not the host does its own SPA fallback.

import http from "node:http";
import { readFile, stat } from "node:fs/promises";
import { extname, join, normalize, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(fileURLToPath(new URL("..", import.meta.url)), "landing");
const port = Number(process.env.PORT || 5173);

const types = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".ico": "image/x-icon",
};

async function fileFor(pathname) {
  const clean = normalize(decodeURIComponent(pathname)).replace(/^(\.\.[/\\])+/, "");
  const candidate = join(root, clean);
  if (!candidate.startsWith(root)) return null;
  try {
    const info = await stat(candidate);
    if (info.isFile()) return candidate;
    if (info.isDirectory()) {
      const index = join(candidate, "index.html");
      if ((await stat(index)).isFile()) return index;
    }
  } catch {
    /* fall through */
  }
  return null;
}

const server = http.createServer(async (req, res) => {
  if (req.method !== "GET" && req.method !== "HEAD") {
    res.writeHead(405, { allow: "GET, HEAD" }).end();
    return;
  }

  const pathname = new URL(req.url, "http://host").pathname;
  let file = await fileFor(pathname);
  let status = 200;

  if (!file && !extname(pathname)) {
    file = join(root, "index.html");
  }
  if (!file) {
    status = 404;
    file = join(root, "404.html");
  }

  try {
    const body = await readFile(file);
    res.writeHead(status, {
      "content-type": types[extname(file)] || "application/octet-stream",
      "cache-control": "no-store",
      "x-content-type-options": "nosniff",
      "referrer-policy": "no-referrer",
    });
    res.end(req.method === "HEAD" ? undefined : body);
  } catch {
    res.writeHead(500, { "content-type": "text/plain" }).end("error");
  }
});

server.listen(port, "0.0.0.0", () => {
  console.log("capture page on http://0.0.0.0:" + port);
});
