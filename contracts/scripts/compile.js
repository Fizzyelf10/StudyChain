// Compiles src/*.sol with solc-js and writes ABI + bytecode to build/<Name>.json
// Run: npm run compile
const fs = require("fs");
const path = require("path");
const solc = require("solc");

const root = path.join(__dirname, "..");
const srcDir = path.join(root, "src");
const outDir = path.join(root, "build");

const sources = {};
for (const file of fs.readdirSync(srcDir)) {
  if (file.endsWith(".sol")) {
    sources[file] = { content: fs.readFileSync(path.join(srcDir, file), "utf8") };
  }
}

const input = {
  language: "Solidity",
  sources,
  settings: {
    optimizer: { enabled: true, runs: 200 },
    // "paris" keeps the bytecode compatible with the local test chain and most networks
    evmVersion: "paris",
    outputSelection: { "*": { "*": ["abi", "evm.bytecode.object"] } },
  },
};

// Resolves imports such as "@openzeppelin/contracts/..." from node_modules
function findImports(importPath) {
  const candidates = [path.join(root, "node_modules", importPath), path.join(srcDir, importPath)];
  for (const candidate of candidates) {
    if (fs.existsSync(candidate)) return { contents: fs.readFileSync(candidate, "utf8") };
  }
  return { error: "File not found: " + importPath };
}

const output = JSON.parse(solc.compile(JSON.stringify(input), { import: findImports }));

let failed = false;
for (const err of output.errors || []) {
  console.log(err.formattedMessage);
  if (err.severity === "error") failed = true;
}
if (failed) process.exit(1);

fs.mkdirSync(outDir, { recursive: true });
for (const file of Object.keys(output.contracts)) {
  for (const [name, contract] of Object.entries(output.contracts[file])) {
    if (!(file in sources)) continue;
    fs.writeFileSync(
      path.join(outDir, name + ".json"),
      JSON.stringify({ abi: contract.abi, bytecode: "0x" + contract.evm.bytecode.object }, null, 2)
    );
    console.log("compiled", name);
  }
}
