import { createHash, randomUUID } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { join, resolve } from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
const project = 'pocket-quest-c7127';
const database = `projects/${project}/databases/(default)`;
const sha = text => createHash('sha256').update(text).digest('hex');

// Firestore integer strings stay exact, including amounts above JavaScript's safe integer limit.
function canonicalBusiness(fields) {
  return '{' + Object.keys(fields).sort().map(key => {
    const value = fields[key];
    const encoded = 'integerValue' in value ? value.integerValue
      : 'nullValue' in value ? 'null' : JSON.stringify(value.stringValue);
    return `${JSON.stringify(key)}:${encoded}`;
  }).join(',') + '}';
}

export function migrationWrites(document, mutationId = randomUUID()) {
  const prefix = `${database}/documents/`;
  if (!document.name.startsWith(prefix) || !/^users\/[^/]+\/expenses\/[^/]+$/.test(document.name.slice(prefix.length))) {
    throw new Error('Unexpected expense document path.');
  }
  const old = document.fields;
  if ('paymentTime' in old && !('startTime' in old) && !('endTime' in old) && old.schemaVersion?.integerValue === '2') return [];
  const expected = ['categoryId', 'amountCents', 'date', 'startTime', 'endTime', 'description', 'receiptObjectPath',
    'schemaVersion', 'revision', 'lastMutationId', 'updatedAt', 'isDeleted', 'deletedAt'].sort();
  if (JSON.stringify(Object.keys(old).sort()) !== JSON.stringify(expected) || old.schemaVersion?.integerValue !== '1') {
    throw new Error('Unexpected expense schema; migration stopped without guessing.');
  }
  const time = old.startTime?.stringValue;
  if (!/^([01][0-9]|2[0-3]):[0-5][0-9]$/.test(time ?? '')) throw new Error('Invalid legacy payment time.');
  const base = BigInt(old.revision.integerValue);
  if (base < 1n || base >= 9223372036854775807n) throw new Error('Invalid expense revision.');
  const { startTime, endTime, updatedAt, ...retained } = old;
  const fields = { ...retained, paymentTime: startTime, schemaVersion: { integerValue: '2' },
    revision: { integerValue: String(base + 1n) }, lastMutationId: { stringValue: mutationId } };
  const business = Object.fromEntries(['categoryId', 'amountCents', 'date', 'paymentTime', 'description', 'receiptObjectPath']
    .map(key => [key, fields[key]]));
  const recordId = document.name.split('/').at(-1);
  const fingerprint = sha(`expenses\n${recordId}\n${base}\n${old.isDeleted.booleanValue}\n${canonicalBusiness(business)}`);
  const receiptName = document.name.replace(/\/expenses\/[^/]+$/, `/mutations/${mutationId}`);
  return [{ update: { name: document.name, fields }, currentDocument: { updateTime: document.updateTime },
    updateTransforms: [{ fieldPath: 'updatedAt', setToServerValue: 'REQUEST_TIME' }] },
  { update: { name: receiptName, fields: { recordType: { stringValue: 'expenses' }, recordId: { stringValue: recordId },
    baseRevision: { integerValue: String(base) }, revision: { integerValue: String(base + 1n) }, fingerprint: { stringValue: fingerprint } } },
    currentDocument: { exists: false } }];
}

async function main() {
  const args = process.argv.slice(2);
  if (args.some(arg => arg !== '--apply')) throw new Error('Use no arguments for a dry run, or --apply.');
  const apply = args.includes('--apply');
  const require = createRequire(import.meta.url);
  const { getAccessToken } = require('firebase-tools/lib/auth');
  const config = JSON.parse(readFileSync(join(homedir(), '.config/configstore/firebase-tools.json'), 'utf8'));
  const credentials = await getAccessToken(config.tokens.refresh_token, []);
  async function api(url, body) {
    const response = await fetch(url, { method: body ? 'POST' : 'GET',
      headers: { Authorization: `Bearer ${credentials.access_token}`, 'Content-Type': 'application/json' },
      body: body ? JSON.stringify(body) : undefined });
    if (!response.ok) throw new Error(`Firebase API returned ${response.status}. No automatic retry was attempted.`);
    return response.json();
  }
  const release = await api(`https://firebaserules.googleapis.com/v1/projects/${project}/releases/cloud.firestore`);
  const rules = await api(`https://firebaserules.googleapis.com/v1/${release.rulesetName}`);
  const activeHash = sha(rules.source.files[0].content);
  if (apply && activeHash !== sha(readFileSync(join(root, 'firestore.rules'), 'utf8'))) {
    throw new Error('Deploy and verify the tested payment-time rules before applying the migration.');
  }
  const query = () => api(`https://firestore.googleapis.com/v1/${database}/documents:runQuery`, {
    structuredQuery: { from: [{ collectionId: 'expenses', allDescendants: true }] }
  }).then(rows => rows.filter(row => row.document).map(row => row.document));
  const documents = await query();
  const pending = documents.map(document => ({ document, writes: migrationWrites(document) })).filter(row => row.writes.length);
  console.log(JSON.stringify({ project, mode: apply ? 'apply' : 'dry-run', total: documents.length, toMigrate: pending.length, activeRulesHash: activeHash }));
  if (!apply || pending.length === 0) return;
  const backupDir = join(root, '.firebase');
  mkdirSync(backupDir, { recursive: true });
  const backup = join(backupDir, `expense-payment-time-backup-${new Date().toISOString().replace(/[:.]/g, '-')}.json`);
  writeFileSync(backup, JSON.stringify({ project, release, rules, documents }, null, 2), { flag: 'wx' });
  // Each expense and its immutable receipt commit together, with a precondition against concurrent edits.
  for (let i = 0; i < pending.length; i += 100) {
    await api(`https://firestore.googleapis.com/v1/${database}/documents:commit`, {
      writes: pending.slice(i, i + 100).flatMap(row => row.writes)
    });
  }
  const after = await query();
  const byName = new Map(after.map(document => [document.name, document]));
  for (const { document, writes } of pending) {
    const actual = byName.get(document.name);
    const expected = writes[0].update.fields;
    if (!actual || 'startTime' in actual.fields || 'endTime' in actual.fields
      || Object.keys(expected).some(key => JSON.stringify(actual.fields[key]) !== JSON.stringify(expected[key]))) {
      throw new Error('Post-migration verification failed; inspect the retained backup before further changes.');
    }
  }
  console.log(JSON.stringify({ migrated: pending.length, verified: pending.length, remainingLegacy: after.filter(d => 'startTime' in d.fields || 'endTime' in d.fields).length,
    backup: resolve(backup) }));
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch(error => { console.error(error.message); process.exitCode = 1; });
}
