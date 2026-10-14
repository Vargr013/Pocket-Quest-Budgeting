import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const root = new URL('../', import.meta.url);
const source = new URL('config/firebase/google-services.demo.json', root);
const target = new URL('app/src/debug/google-services.json', root);
const demo = readFileSync(source, 'utf8');

// I generated only debug configuration and refused to replace a selected project (Google, 2026m).
if (existsSync(new URL('app/google-services.json', root))) {
  throw new Error('A real/common client configuration exists. Do not mix it with demo debug configuration.');
}
if (existsSync(target) && readFileSync(target, 'utf8') !== demo) {
  throw new Error('Debug client configuration differs. Keep it or remove it explicitly before selecting demo mode.');
}
mkdirSync(new URL('app/src/debug/', root), { recursive: true });
writeFileSync(target, demo);
console.log(`Prepared synthetic debug configuration: ${fileURLToPath(target)}`);
console.log('No live Firebase project or credentials are used. Release configuration is still required.');
