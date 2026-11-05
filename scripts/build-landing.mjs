// Copies the static capture page into dist/ for hosting.
// No bundler: the page is plain HTML/CSS/JS and ships exactly as written.

import { cpSync, existsSync, rmSync, mkdirSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

// Resolved from this file, not from cwd, and without import.meta.dirname,
// which only exists on Node 20.11+ — the hosting builder may run older.
const root = resolve(fileURLToPath(new URL("..", import.meta.url)));
const src = resolve(root, "landing");
const out = resolve(root, "dist");

if (!existsSync(src)) {
  console.error("landing/ is missing");
  process.exit(1);
}

rmSync(out, { recursive: true, force: true });
mkdirSync(out, { recursive: true });

// index.html, 404.html, css/, js/
cpSync(src, out, { recursive: true });

for (const required of ["index.html", "404.html", "css/app.css", "js/app.js"]) {
  if (!existsSync(resolve(out, required))) {
    console.error("build output is missing " + required);
    process.exit(1);
  }
}

console.log("built dist/ from landing/");
