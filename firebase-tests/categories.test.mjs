import { after, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDoc, getDocs, runTransaction, serverTimestamp, setDoc, setLogLevel, writeBatch } from 'firebase/firestore';

let environment;
const uid = 'category-owner';
function nameKey(name) { return 'n:' + name.toLowerCase(); }
const fields = { name: 'Travel', minimumCents: 123, maximumCents: 56789 };
before(async () => {
  assert.equal(process.env.GCLOUD_PROJECT, 'demo-pocket-quest');
  assert.equal(process.env.FIRESTORE_EMULATOR_HOST, '127.0.0.1:8080');
  setLogLevel('silent');
  environment = await initializeTestEnvironment({ projectId: 'demo-pocket-quest', firestore: {
    host: '127.0.0.1', port: 8080, rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8'),
  }});
});
beforeEach(async () => environment.clearFirestore());
after(async () => environment?.cleanup());
function client(actor = uid) { return (actor ? environment.authenticatedContext(actor) : environment.unauthenticatedContext()).firestore(); }
function snapshot(business = fields, revision = 1, mutation = randomUUID(), deleted = false) {
  return { ...business, schemaVersion: 1, revision, lastMutationId: mutation, updatedAt: serverTimestamp(),
    isDeleted: deleted, deletedAt: deleted ? serverTimestamp() : null };
}
function receipt(id, data, base = data.revision - 1) {
  return { recordType: 'categories', recordId: id, baseRevision: base, revision: data.revision, fingerprint: 'a'.repeat(64) };
}
async function save(db, id, business = fields, base = 0, deleted = false) {
  const mutation = randomUUID();
  return runTransaction(db, async transaction => {
    const record = doc(db, `users/${uid}/categories/${id}`);
    const index = doc(db, `users/${uid}/categoryNames/index`);
    const [old, catalog] = await Promise.all([transaction.get(record), transaction.get(index)]);
    if ((old.data()?.revision ?? 0) !== base || old.data()?.isDeleted) throw new Error('conflict');
    const names = { ...(catalog.data()?.names ?? {}) };
    const key = nameKey(business.name);
    if (!deleted && names[key] && names[key] !== id) throw new Error('duplicate');
    if (old.exists()) delete names[nameKey(old.data().name)];
    if (!deleted) names[key] = id;
    const data = snapshot(business, base + 1, mutation, deleted);
    transaction.set(record, data);
    transaction.set(doc(db, `users/${uid}/mutations/${mutation}`), receipt(id, data, base));
    transaction.set(index, { names, recordId: id, lastMutationId: mutation });
  });
}
async function rawSave(db, id, data, names = { [nameKey(data.name || 'travel')]: id }) {
  const batch = writeBatch(db);
  batch.set(doc(db, `users/${uid}/categories/${id}`), data);
  batch.set(doc(db, `users/${uid}/mutations/${data.lastMutationId}`), receipt(id, data));
  batch.set(doc(db, `users/${uid}/categoryNames/index`), { names, recordId: id, lastMutationId: data.lastMutationId });
  return batch.commit();
}

test('owner round trips both cents, zero limits, rename and case-only rename', async () => {
  const db = client();
  await assertSucceeds(save(db, 'travel'));
  assert.equal((await getDoc(doc(db, `users/${uid}/categories/travel`))).data().maximumCents, 56789);
  await assertSucceeds(save(db, 'travel', { ...fields, name: 'TRAVEL', minimumCents: 0, maximumCents: 0 }, 1));
  await assertSucceeds(save(db, 'travel', { ...fields, name: 'Commute' }, 2));
  assert.equal((await getDocs(collection(db, `users/${uid}/categories`))).size, 1);
  assert.deepEqual((await getDoc(doc(db, `users/${uid}/categoryNames/index`))).data().names, { 'n:commute': 'travel' });
});

test('malformed fields, ranges, metadata and fractional cents are denied', async () => {
  const db = client();
  for (const patch of [ { minimumCents: -1 }, { maximumCents: -1 }, { minimumCents: 60000 },
    { minimumCents: 1.5 }, { maximumCents: '100' }, { name: '' }, { name: '   ' }, { name: ' Travel' },
    { uid }, { password: 'no' }, { revision: 2 }, { schemaVersion: 2 }, { updatedAt: null }, { deletedAt: serverTimestamp() } ]) {
    await assertFails(rawSave(db, 'invalid', { ...snapshot(), ...patch }));
  }
});

test('record and immutable receipt must be written together with the name reservation', async () => {
  const db = client();
  const data = snapshot();
  await assertFails(setDoc(doc(db, `users/${uid}/categories/travel`), data));
  await assertFails(setDoc(doc(db, `users/${uid}/mutations/${data.lastMutationId}`), receipt('travel', data)));
  await save(db, 'travel');
  const saved = (await getDoc(doc(db, `users/${uid}/categories/travel`))).data();
  await assertFails(setDoc(doc(db, `users/${uid}/mutations/${saved.lastMutationId}`), { ...receipt('travel', saved), fingerprint: 'b'.repeat(64) }));
  await assertFails(deleteDoc(doc(db, `users/${uid}/categories/travel`)));
  await assertFails(deleteDoc(doc(db, `users/${uid}/mutations/${saved.lastMutationId}`)));
});

test('two clients cannot reserve duplicate names or overwrite another category reservation', async () => {
  const first = client(), second = client();
  await save(first, 'travel');
  await assert.rejects(save(second, 'duplicate', { ...fields, name: 'TRAVEL' }), /duplicate/);
  await assertFails(rawSave(second, 'duplicate', snapshot({ ...fields, name: 'TRAVEL' }), { 'n:travel': 'duplicate' }));
  await assertFails(rawSave(second, 'other', snapshot({ ...fields, name: 'Other' }), { 'n:other': 'other' }));
});

for (const actor of ['different-owner', null]) test(`${actor ?? 'signed-out'} cannot access category, index or receipt`, async () => {
  await save(client(), 'travel');
  const db = client(actor);
  const saved = (await getDoc(doc(client(), `users/${uid}/categories/travel`))).data();
  for (const path of [`users/${uid}/categories/travel`, `users/${uid}/categoryNames/index`,
    `users/${uid}/mutations/${saved.lastMutationId}`]) {
    await assertFails(getDoc(doc(db, path)));
    await assertFails(deleteDoc(doc(db, path)));
  }
  await assertFails(getDocs(collection(db, `users/${uid}/categories`)));
  await assertFails(rawSave(db, 'foreign', snapshot()));
});

test('archive retained the document and rejected stale edits, resurrection and changed archive fields', async () => {
  const first = client(), second = client();
  await save(first, 'travel');
  await assertFails(rawSave(second, 'travel', snapshot({ ...fields, name: 'Changed during archive' }, 2, randomUUID(), true), {}));
  await save(first, 'travel', fields, 1, true);
  const archived = (await getDoc(doc(second, `users/${uid}/categories/travel`))).data();
  assert.equal(archived.isDeleted, true);
  assert.equal(archived.maximumCents, fields.maximumCents);
  assert.ok(archived.deletedAt);
  await assertFails(rawSave(second, 'travel', snapshot(fields, 3)));
  await assertFails(deleteDoc(doc(second, `users/${uid}/categories/travel`)));
  await save(second, 'replacement', fields);
});

async function defaults(db, omitRent = false) {
  const batch = writeBatch(db);
  const names = { groceries: 'default-groceries', transport: 'default-transport', entertainment: 'default-entertainment', rent: 'default-rent' };
  for (const [name, id] of Object.entries(names)) {
    if (omitRent && name === 'rent') continue;
    const data = snapshot({ name: name[0].toUpperCase() + name.slice(1), minimumCents: 0, maximumCents: 0 });
    batch.set(doc(db, `users/${uid}/categories/${id}`), data);
    batch.set(doc(db, `users/${uid}/mutations/${data.lastMutationId}`), receipt(id, data));
  }
  batch.set(doc(db, `users/${uid}`), { schemaVersion: 1, username: 'Synthetic', categoriesInitialized: true });
  batch.set(doc(db, `users/${uid}/categoryNames/index`), { names: Object.fromEntries(Object.entries(names).map(([key, value]) => [nameKey(key), value])), recordId: 'defaults', lastMutationId: '' });
  return batch.commit();
}

test('defaults and setup flag were atomic; missing defaults and reseeding were denied', async () => {
  const db = client();
  await assertFails(defaults(db, true));
  assert.equal((await getDocs(collection(db, `users/${uid}/categories`))).size, 0);
  await assertSucceeds(defaults(db));
  assert.equal((await getDocs(collection(db, `users/${uid}/categories`))).size, 4);
  await assertFails(defaults(db));
  await assertFails(setDoc(doc(db, `users/${uid}`), { schemaVersion: 1, username: 'Synthetic', categoriesInitialized: false }));
});

test('concurrent clients accepted one case-insensitive name reservation', async () => {
  const results = await Promise.allSettled([
    save(client(), 'first', { ...fields, name: 'Concurrent' }),
    save(client(), 'second', { ...fields, name: 'CONCURRENT' }),
  ]);
  assert.equal(results.filter(result => result.status === 'fulfilled').length, 1);
  assert.equal((await getDocs(collection(client(), `users/${uid}/categories`))).size, 1);
});

test('punctuation and accented names kept their exact display text', async () => {
  const db = client();
  for (const [index, name] of ['Café / trips.v2', '__private__', 'Line\nbreak'].entries()) {
    await save(db, `text-${index}`, { ...fields, name });
    assert.equal((await getDoc(doc(db, `users/${uid}/categories/text-${index}`))).data().name, name);
  }
});
