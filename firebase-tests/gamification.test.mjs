import { after, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, deleteDoc, serverTimestamp, writeBatch, setLogLevel } from 'firebase/firestore';

let environment;
const uid = 'gamification-owner';
const item = { name: 'Bicycle', valueCents: 450000, category: 'CUSTOM' };
before(async () => {
  assert.equal(process.env.GCLOUD_PROJECT, 'demo-pocket-quest');
  assert.equal(process.env.FIRESTORE_EMULATOR_HOST, '127.0.0.1:8080');
  setLogLevel('silent');
  environment = await initializeTestEnvironment({ projectId: 'demo-pocket-quest', firestore: { host: '127.0.0.1', port: 8080,
    rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') } });
});
beforeEach(async () => environment.clearFirestore());
after(async () => environment?.cleanup());
function client(owner = uid) { return (owner ? environment.authenticatedContext(owner) : environment.unauthenticatedContext()).firestore(); }
function ref(db, type, id) { return doc(db, `users/${uid}/${type}/${id}`); }
function data(fields, revision = 1, deleted = false) { return { ...fields, schemaVersion: 1, revision,
  lastMutationId: randomUUID(), updatedAt: serverTimestamp(), isDeleted: deleted, deletedAt: deleted ? serverTimestamp() : null }; }
function receipt(type, id, row) { return { recordType: type, recordId: id, baseRevision: row.revision - 1, revision: row.revision, fingerprint: 'a'.repeat(64) }; }
async function save(db, type, id, row, receiptPatch = {}) {
  const batch = writeBatch(db);
  batch.set(ref(db, type, id), row);
  batch.set(ref(db, 'mutations', row.lastMutationId), { ...receipt(type, id, row), ...receiptPatch });
  return batch.commit();
}
async function denied(promise) { const error = await assertFails(promise); assert.equal(error.code, 'permission-denied'); }

test('activity accepts an owner date with its receipt and retains it permanently', async () => {
  const db = client(), date = '2024-02-29';
  await assertSucceeds(save(db, 'activityDays', date, data({ date })));
  await assertSucceeds(getDoc(ref(db, 'activityDays', date)));
  await denied(save(db, 'activityDays', date, data({ date }, 2)));
  await denied(deleteDoc(ref(db, 'activityDays', date)));
  await denied(getDoc(ref(client('other'), 'activityDays', date)));
  await denied(getDoc(ref(client(null), 'activityDays', date)));
});

test('activity rejects malformed mismatched deleted and unpaired dates', async () => {
  const db = client();
  for (const [id, fields] of [['2025-02-29', { date: '2025-02-29' }], ['2026-10-20', { date: '2026-10-19' }],
    ['2026-10-20', { date: '2026-10-20', userId: 1 }], ['wrong', { date: 'wrong' }]]) {
    await denied(save(db, 'activityDays', id, data(fields)));
  }
  await denied(save(db, 'activityDays', '2026-10-20', data({ date: '2026-10-20' }, 1, true)));
  await denied(save(client('other'), 'activityDays', '2026-10-20', data({ date: '2026-10-20' })));
  await denied(save(client(null), 'activityDays', '2026-10-20', data({ date: '2026-10-20' })));
  const row = data({ date: '2026-10-20' });
  await denied(setDoc(ref(db, 'activityDays', row.date), row));
  await denied(setDoc(ref(db, 'mutations', row.lastMutationId), receipt('activityDays', row.date, row)));
  for (const patch of [{ baseRevision: 1 }, { recordId: 'other' }, { revision: 2 }, { fingerprint: 'bad' }]) {
    await denied(save(db, 'activityDays', row.date, data({ date: row.date }), patch));
  }
});

test('custom items support checked edits and retained deletion but cannot resurrect', async () => {
  const db = client(), id = randomUUID();
  await assertSucceeds(save(db, 'customWorthItems', id, data(item)));
  await assertSucceeds(save(db, 'customWorthItems', id, data({ ...item, valueCents: 500000 }, 2)));
  await denied(save(db, 'customWorthItems', id, data(item, 2)));
  await denied(save(db, 'customWorthItems', id, data({ ...item, name: 'Sneaked edit' }, 3, true)));
  await assertSucceeds(save(db, 'customWorthItems', id, data({ ...item, valueCents: 500000 }, 3, true)));
  await denied(save(db, 'customWorthItems', id, data(item, 4)));
  await denied(deleteDoc(ref(db, 'customWorthItems', id)));
  assert.equal((await getDoc(ref(db, 'customWorthItems', id))).data().isDeleted, true);
});

test('custom items reject bad fields identities access and unpaired mutations', async () => {
  const db = client();
  for (const patch of [{ name: '' }, { name: ' ' }, { name: ' x' }, { name: 'x'.repeat(41) }, { valueCents: 0 },
    { valueCents: -1 }, { valueCents: 1.5 }, { category: 'FOOD' }, { userId: 1 }]) {
    await denied(save(db, 'customWorthItems', randomUUID(), data({ ...item, ...patch })));
  }
  await denied(save(db, 'customWorthItems', 'numeric-42', data(item)));
  await denied(save(client('other'), 'customWorthItems', randomUUID(), data(item)));
  await denied(save(client(null), 'customWorthItems', randomUUID(), data(item)));
  const id = randomUUID(), row = data(item);
  await denied(setDoc(ref(db, 'customWorthItems', id), row));
  await denied(setDoc(ref(db, 'mutations', row.lastMutationId), receipt('customWorthItems', id, row)));
  for (const patch of [{ baseRevision: 1 }, { recordId: 'other' }, { revision: 2 }, { fingerprint: 'bad' }]) {
    await denied(save(db, 'customWorthItems', id, data(item), patch));
  }
  await save(db, 'customWorthItems', id, row);
  await denied(getDoc(ref(client('other'), 'customWorthItems', id)));
  await denied(getDoc(ref(client(null), 'customWorthItems', id)));
});
