import { after, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDoc, getDocs, serverTimestamp, setDoc, setLogLevel, writeBatch } from 'firebase/firestore';

let environment;
const uid = 'expense-owner';
const fields = { categoryId: 'category-a', amountCents: 12345, date: '2024-02-29', paymentTime: '09:15',
  description: 'Synthetic expense', receiptObjectPath: null };
before(async () => {
  assert.equal(process.env.GCLOUD_PROJECT, 'demo-pocket-quest');
  assert.equal(process.env.FIRESTORE_EMULATOR_HOST, '127.0.0.1:8080');
  setLogLevel('silent');
  environment = await initializeTestEnvironment({ projectId: 'demo-pocket-quest', firestore: { host: '127.0.0.1', port: 8080,
    rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') }});
});
beforeEach(async () => {
  await environment.clearFirestore();
  await seedCategory('category-a');
  await seedCategory('category-b');
});
after(async () => environment?.cleanup());
function client(actor = uid) { return (actor ? environment.authenticatedContext(actor) : environment.unauthenticatedContext()).firestore(); }
async function seedCategory(id, archived = false, owner = uid) {
  await environment.withSecurityRulesDisabled(async context => {
    await setDoc(doc(context.firestore(), `users/${owner}/categories/${id}`), { name: id, minimumCents: 0, maximumCents: 0,
      schemaVersion: 1, revision: 1, lastMutationId: randomUUID(), updatedAt: serverTimestamp(), isDeleted: archived,
      deletedAt: archived ? serverTimestamp() : null });
  });
}
function snapshot(business = fields, revision = 1, deleted = false) { return { ...business, schemaVersion: 2, revision,
  lastMutationId: randomUUID(), updatedAt: serverTimestamp(), isDeleted: deleted, deletedAt: deleted ? serverTimestamp() : null }; }
function receipt(id, data) { return { recordType: 'expenses', recordId: id, baseRevision: data.revision - 1, revision: data.revision, fingerprint: 'b'.repeat(64) }; }
function addExpense(batch, db, id, data, withReceipt = true) {
  batch.set(doc(db, `users/${uid}/expenses/${id}`), data);
  if (withReceipt) batch.set(doc(db, `users/${uid}/mutations/${data.lastMutationId}`), receipt(id, data));
}
async function save(db, id, data) { const batch = writeBatch(db); addExpense(batch, db, id, data); return batch.commit(); }

test('owner round trips exact fields, leap-day date and immutable deletion marker', async () => {
  const db = client();
  await assertSucceeds(save(db, 'expense', snapshot()));
  const record = (await getDoc(doc(db, `users/${uid}/expenses/expense`))).data();
  for (const [key, value] of Object.entries(fields)) assert.equal(record[key], value);
  await assertSucceeds(save(db, 'expense', snapshot({ ...fields, amountCents: 99999, categoryId: 'category-b' }, 2)));
  await assertSucceeds(save(db, 'expense', snapshot({ ...fields, amountCents: 99999, categoryId: 'category-b' }, 3, true)));
  await assertFails(save(db, 'expense', snapshot(fields, 4)));
  await assertFails(deleteDoc(doc(db, `users/${uid}/expenses/expense`)));
});

test('invalid cents, calendar dates, times, descriptions and device fields are denied', async () => {
  const db = client();
  for (const patch of [{ amountCents: 0 }, { amountCents: -1 }, { amountCents: 1.25 }, { amountCents: '123' },
    { date: '2023-02-29' }, { date: '1900-02-29' }, { date: '2024-04-31' }, { date: '2024-00-01' },
    { date: '0000-01-01' }, { date: '2024-1-01' }, { date: '2024-01-01Z' }, { paymentTime: '24:00' },
    { paymentTime: '10:60' }, { paymentTime: '9:15' }, { paymentTime: '' }, { startTime: '09:00' }, { endTime: '10:00' }, { description: '' }, { description: '   ' },
    { description: ' untrimmed' }, { categoryId: 1 }, { categoryId: '../bad' }, { uid }, { userId: 1 },
    { receiptImageUri: '/private/photo.jpg' }, { receiptObjectPath: '/private/photo.jpg' },
    { receiptObjectPath: `users/${uid}/receipts/expense/${randomUUID()}` }, { updatedAt: null },
    { revision: 2 }, { deletedAt: serverTimestamp() }]) {
    await assertFails(save(db, 'invalid', { ...snapshot(), ...patch }));
  }
});

test('real calendar boundaries and payment time boundaries are accepted', async () => {
  const db = client();
  for (const [index, date] of ['2000-02-29', '2024-02-29', '2026-04-30', '0001-01-01', '9999-12-31'].entries()) {
    await assertSucceeds(save(db, `date-${index}`, snapshot({ ...fields, date, paymentTime: index % 2 ? '00:00' : '23:59' })));
  }
});

test('legacy retries remain valid and convert to payment time on edit or deletion', async () => {
  const db = client();
  const { paymentTime, ...common } = fields;
  const legacy = { ...common, startTime: paymentTime, endTime: '10:30' };
  const old = { ...snapshot(legacy), schemaVersion: 1 };
  await assertSucceeds(save(db, 'legacy-edit', old));
  await assertSucceeds(save(db, 'legacy-edit', snapshot({ ...fields, paymentTime: '23:59' }, 2)));
  const changed = (await getDoc(doc(db, `users/${uid}/expenses/legacy-edit`))).data();
  assert.equal(changed.schemaVersion, 2);
  assert.equal(changed.paymentTime, '23:59');
  assert.equal('startTime' in changed, false); assert.equal('endTime' in changed, false);
  await assertSucceeds(save(db, 'legacy-delete', { ...snapshot(legacy), schemaVersion: 1 }));
  await assertFails(save(db, 'legacy-delete', snapshot({ ...fields, paymentTime: '09:16' }, 2, true)));
  await assertSucceeds(save(db, 'legacy-delete', snapshot(fields, 2, true)));
  await assertFails(save(db, 'mixed', snapshot({ ...fields, startTime: paymentTime, endTime: '10:30' })));
  await assertFails(save(db, 'wrong-version', { ...snapshot(), schemaVersion: 1 }));
  await assertFails(save(db, 'wrong-legacy-version', snapshot(legacy)));
});

test('missing and archived categories cannot receive new expenses; existing references survive archive', async () => {
  const db = client();
  await assertFails(save(db, 'missing', snapshot({ ...fields, categoryId: 'missing' })));
  await save(db, 'existing', snapshot());
  await seedCategory('category-a', true);
  await assertFails(save(db, 'new', snapshot()));
  await assertSucceeds(save(db, 'existing', snapshot({ ...fields, description: 'Kept historical reference' }, 2)));
  await seedCategory('category-b', true);
  await assertFails(save(db, 'existing', snapshot({ ...fields, categoryId: 'category-b' }, 3)));
  await assertSucceeds(save(db, 'discarded-unsent', snapshot(fields, 1, true)));
});

test('one batch cannot archive a category and create an active expense against it', async () => {
  const db = client();
  const batch = writeBatch(db), mutation = randomUUID();
  const category = { name: 'category-a', minimumCents: 0, maximumCents: 0, schemaVersion: 1, revision: 2,
    lastMutationId: mutation, updatedAt: serverTimestamp(), isDeleted: true, deletedAt: serverTimestamp() };
  batch.set(doc(db, `users/${uid}/categories/category-a`), category);
  batch.set(doc(db, `users/${uid}/categoryNames/index`), { names: {}, recordId: 'category-a', lastMutationId: mutation });
  batch.set(doc(db, `users/${uid}/mutations/${mutation}`), { recordType: 'categories', recordId: 'category-a', baseRevision: 1,
    revision: 2, fingerprint: 'c'.repeat(64) });
  addExpense(batch, db, 'raced', snapshot());
  await assertFails(batch.commit());
  // The category part is valid on its own; the final-state reference gate rejects the combined batch.
  const archive = writeBatch(db);
  archive.set(doc(db, `users/${uid}/categories/category-a`), category);
  archive.set(doc(db, `users/${uid}/categoryNames/index`), { names: {}, recordId: 'category-a', lastMutationId: mutation });
  archive.set(doc(db, `users/${uid}/mutations/${mutation}`), { recordType: 'categories', recordId: 'category-a', baseRevision: 1,
    revision: 2, fingerprint: 'c'.repeat(64) });
  await assertSucceeds(archive.commit());
});

test('record and receipt are atomic; stale revisions, changed delete fields and receipt reuse are denied', async () => {
  const db = client(), data = snapshot();
  await assertFails(setDoc(doc(db, `users/${uid}/expenses/expense`), data));
  await assertFails(setDoc(doc(db, `users/${uid}/mutations/${data.lastMutationId}`), receipt('expense', data)));
  await save(db, 'expense', data);
  await assertFails(save(db, 'expense', snapshot()));
  await assertFails(save(db, 'expense', snapshot({ ...fields, amountCents: 1 }, 2, true)));
  await assertFails(save(db, 'expense', { ...snapshot(fields, 2), lastMutationId: data.lastMutationId }));
  await assertFails(setDoc(doc(db, `users/${uid}/mutations/${data.lastMutationId}`), { ...receipt('expense', data), fingerprint: 'a'.repeat(64) }));
});

for (const actor of ['other-owner', null]) test(`${actor ?? 'signed-out'} cannot read, query, edit, delete or forge receipts`, async () => {
  const data = snapshot(); await save(client(), 'expense', data);
  const db = client(actor);
  await assertFails(getDoc(doc(db, `users/${uid}/expenses/expense`)));
  await assertFails(getDocs(collection(db, `users/${uid}/expenses`)));
  await assertFails(getDoc(doc(db, `users/${uid}/mutations/${data.lastMutationId}`)));
  await assertFails(save(db, 'foreign', snapshot()));
  await assertFails(deleteDoc(doc(db, `users/${uid}/expenses/expense`)));
});

test('an own expense cannot reference a category owned only by another UID', async () => {
  await seedCategory('foreign-only', false, 'other-owner');
  await assertFails(save(client(), 'foreign-reference', snapshot({ ...fields, categoryId: 'foreign-only' })));
});
