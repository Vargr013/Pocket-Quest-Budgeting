import { after, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDoc, getDocs, serverTimestamp, setDoc, setLogLevel, writeBatch } from 'firebase/firestore';

let environment;
const uid = 'monthly-goal-owner';
const fields = { minimumCents: 12345, maximumCents: 99999, startDate: '2024-02-01', endDate: '2024-02-29' };
before(async () => {
  assert.equal(process.env.GCLOUD_PROJECT, 'demo-pocket-quest');
  assert.equal(process.env.FIRESTORE_EMULATOR_HOST, '127.0.0.1:8080');
  setLogLevel('silent');
  environment = await initializeTestEnvironment({ projectId: 'demo-pocket-quest', firestore: { host: '127.0.0.1', port: 8080,
    rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') }});
});
beforeEach(async () => environment.clearFirestore());
after(async () => environment?.cleanup());
function client(actor = uid) { return (actor ? environment.authenticatedContext(actor) : environment.unauthenticatedContext()).firestore(); }
function snapshot(business = fields, revision = 1, deleted = false) { return { ...business, schemaVersion: 1, revision,
  lastMutationId: randomUUID(), updatedAt: serverTimestamp(), isDeleted: deleted, deletedAt: deleted ? serverTimestamp() : null }; }
function receipt(id, data) { return { recordType: 'monthlyGoals', recordId: id, baseRevision: data.revision - 1, revision: data.revision, fingerprint: 'a'.repeat(64) }; }
async function save(db, id, data, patch = {}, owner = uid) {
  const batch = writeBatch(db);
  batch.set(doc(db, `users/${owner}/monthlyGoals/${id}`), data);
  batch.set(doc(db, `users/${owner}/mutations/${data.lastMutationId}`), { ...receipt(id, data), ...patch });
  return batch.commit();
}
async function denied(promise) { const error = await assertFails(promise); assert.equal(error.code, 'permission-denied'); }

test('monthly owner writes exact limits, repeats reads, edits and keeps zero distinct from absence', async () => {
  const db = client(), path = doc(db, `users/${uid}/monthlyGoals/2024-02`);
  assert.equal((await getDoc(path)).exists(), false);
  await assertSucceeds(save(db, '2024-02', snapshot()));
  for (let i = 0; i < 2; i++) {
    const saved = (await getDoc(path)).data();
    for (const [key, value] of Object.entries(fields)) assert.equal(saved[key], value);
  }
  await assertSucceeds(save(db, '2024-02', snapshot({ ...fields, minimumCents: 0, maximumCents: 0 }, 2)));
  assert.equal((await getDoc(path)).data().maximumCents, 0);
  assert.equal((await getDocs(collection(db, `users/${uid}/monthlyGoals`))).size, 1);
});

test('monthly keys enforce leap years, full ranges and December January boundaries', async () => {
  const db = client();
  for (const [month, last] of [['0001-01', '31'], ['1900-02', '28'], ['2000-02', '29'], ['2023-02', '28'],
    ['2024-02', '29'], ['2026-04', '30'], ['2026-12', '31'], ['2027-01', '31'], ['9999-12', '31']]) {
    await assertSucceeds(save(db, month, snapshot({ ...fields, startDate: `${month}-01`, endDate: `${month}-${last}` })));
  }
  await environment.clearFirestore();
  for (const month of ['0000-02', '2024-00', '2024-13', '2024-2', '24-02', '2024-02-extra', 'other']) {
    await denied(save(db, month, snapshot()));
  }
  for (const patch of [{ startDate: '2024-02-02' }, { endDate: '2024-02-28' }, { endDate: '2024-02-30' },
    { startDate: '2023-02-01', endDate: '2023-02-29' }, { startDate: '2024-01-01' }, { endDate: '2024-03-01' }]) {
    const month = patch.startDate?.slice(0, 7) ?? '2024-02';
    await denied(save(db, month, snapshot({ ...fields, ...patch })));
  }
});

test('monthly limits reject fractions negatives overflow types and inverted ranges', async () => {
  const db = client();
  for (const patch of [{ minimumCents: -1 }, { maximumCents: -1 }, { minimumCents: 1.5 }, { maximumCents: 1.5 },
    { minimumCents: '0' }, { maximumCents: null }, { minimumCents: 100000 }, { maximumCents: 1e20 }, { minimumCents: false }]) {
    await denied(save(db, '2024-02', snapshot({ ...fields, ...patch })));
  }
});

test('monthly shape rejects extra missing and malformed metadata fields', async () => {
  const db = client();
  for (const key of Object.keys(snapshot())) {
    const data = snapshot(); delete data[key];
    if (key === 'lastMutationId') {
      const batch = writeBatch(db), mutation = randomUUID();
      batch.set(doc(db, `users/${uid}/monthlyGoals/2024-02`), data);
      batch.set(doc(db, `users/${uid}/mutations/${mutation}`), receipt('2024-02', snapshot()));
      await denied(batch.commit());
    } else await denied(save(db, '2024-02', data, { baseRevision: 0, revision: 1 }));
  }
  for (const patch of [{ userId: 1 }, { uid }, { month: '2024-02' }, { schemaVersion: 2 }, { schemaVersion: 1.5 },
    { revision: 2 }, { revision: 1.5 }, { updatedAt: null }, { updatedAt: new Date(0) }, { isDeleted: 0 },
    { deletedAt: serverTimestamp() }, { lastMutationId: 'bad-id' }]) {
    await denied(save(db, '2024-02', { ...snapshot(), ...patch }));
  }
});

test('monthly record and new immutable receipt must commit together', async () => {
  const db = client(), data = snapshot();
  const record = doc(db, `users/${uid}/monthlyGoals/2024-02`), proof = doc(db, `users/${uid}/mutations/${data.lastMutationId}`);
  await denied(setDoc(record, data));
  await denied(setDoc(proof, receipt('2024-02', data)));
  for (const patch of [{ recordType: 'savingsGoals' }, { recordId: '2024-03' }, { baseRevision: 1 }, { revision: 2 },
    { fingerprint: '' }, { extra: 0 }]) await denied(save(db, '2024-02', data, patch));
  await save(db, '2024-02', data);
  await denied(setDoc(proof, receipt('2024-02', data))); await denied(deleteDoc(proof));
  await denied(save(db, '2024-02', snapshot()));
  await denied(save(db, '2024-02', { ...snapshot(fields, 2), lastMutationId: data.lastMutationId }));
});

test('monthly tombstones keep business fields and cannot be restored or physically deleted', async () => {
  const db = client(); await save(db, '2024-02', snapshot());
  await denied(save(db, '2024-02', snapshot({ ...fields, maximumCents: 100000 }, 2, true)));
  await assertSucceeds(save(db, '2024-02', snapshot(fields, 2, true)));
  await denied(save(db, '2024-02', snapshot(fields, 3)));
  await denied(deleteDoc(doc(db, `users/${uid}/monthlyGoals/2024-02`)));
});

for (const actor of ['other-owner', null]) test(`${actor ?? 'signed-out'} cannot access monthly goals or receipts`, async () => {
  const owner = client(), data = snapshot(); await save(owner, '2024-02', data);
  const db = client(actor);
  await denied(getDoc(doc(db, `users/${uid}/monthlyGoals/2024-02`)));
  await denied(getDocs(collection(db, `users/${uid}/monthlyGoals`)));
  await denied(save(db, '2024-02', snapshot(fields, 2)));
  await denied(save(db, '2024-03', snapshot({ ...fields, startDate: '2024-03-01', endDate: '2024-03-31' })));
  await denied(deleteDoc(doc(db, `users/${uid}/monthlyGoals/2024-02`)));
  await denied(getDoc(doc(db, `users/${uid}/mutations/${data.lastMutationId}`)));
});
