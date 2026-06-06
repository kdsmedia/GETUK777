// Generates src/index.ts re-exporting every generated *_pb.ts module.
// Within a single proto package every symbol name is unique, so a flat
// `export *` barrel never collides. Runs after `buf generate`.
import { readdirSync, statSync, writeFileSync } from "node:fs";
import { join, relative } from "node:path";

const genDir = "src/gen";
const files = [];

function walk(dir) {
  for (const entry of readdirSync(dir)) {
    const p = join(dir, entry);
    if (statSync(p).isDirectory()) walk(p);
    else if (entry.endsWith("_pb.ts")) files.push(p);
  }
}

walk(genDir);
files.sort();

const lines = files.map((f) => {
  const spec = "./" + relative("src", f).replace(/\.ts$/, ".js");
  return `export * from "${spec}";`;
});

writeFileSync("src/index.ts", lines.join("\n") + "\n");
console.log(`barrel: ${files.length} modules -> src/index.ts`);
