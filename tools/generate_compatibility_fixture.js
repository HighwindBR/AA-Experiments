// Generate the compact compatibility baseline used by the 256 MiB regression gate.
const fs = require("fs");
const path = require("path");
const root = path.resolve(__dirname, "..");
const source = path.join(root, "outputs", "discovery", "inventory-17.8.663814.json");
const destination = path.join(root, "fixtures", "compatibility-17.8.663814.json");
const inventory = JSON.parse(fs.readFileSync(source, "utf8"));
const rows = inventory.identifiers.filter(identifier => identifier.editable).map(identifier => ({
  key: identifier.key,
  namespace: identifier.namespace,
  type: identifier.type,
  compiledDefault: identifier.compiledDefault,
  confidence: identifier.confidence,
  getters: (identifier.getters || []).map(index => inventory.methods[index]),
}));
fs.writeFileSync(destination, JSON.stringify(rows));
console.log(`wrote ${rows.length} mappings to ${destination}`);
