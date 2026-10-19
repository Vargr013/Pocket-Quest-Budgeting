import { after, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDoc, getDocs, setDoc, setLogLevel, updateDoc } from 'firebase/firestore';
import { deleteObject, getBytes, listAll, ref, updateMetadata, uploadBytes } from 'firebase/storage';

const projectId = 'demo-pocket-quest';
const owner = 'synthetic-alice';
const other = 'synthetic-bob';
const expenseId = '11111111-1111-4111-8111-111111111111';
const jobId = '22222222-2222-4222-8222-222222222222';
const receiptPath = `users/${owner}/receipts/${expenseId}/${jobId}`;
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a7WQAAAAASUVORK5CYII=', 'base64');
const setup = { schemaVersion: 1, username: 'Alice', categoriesInitialized: false };
let environment;

// I used synthetic contexts and rules-disabled fixtures only inside the emulators (Google, 2026o).
before(async () => {
  setLogLevel('silent');
  assert.equal(process.env.GCLOUD_PROJECT, projectId);
  for (const variable of ['FIRESTORE_EMULATOR_HOST', 'FIREBASE_STORAGE_EMULATOR_HOST']) {
    assert.match(process.env[variable] ?? '', /^127\.0\.0\.1:\d+$/);
  }
  environment = await initializeTestEnvironment({
    projectId,
    firestore: {
      host: '127.0.0.1', port: 8080,
      rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8'),
    },
    storage: {
      host: '127.0.0.1', port: 9199,
      rules: readFileSync(new URL('../storage.rules', import.meta.url), 'utf8'),
    },
  });
});

beforeEach(async () => {
  await environment.clearFirestore();
  // I cleared nested prefixes too; this harness version's clearStorage only removes root items.
  await environment.withSecurityRulesDisabled(async (context) => {
    await clearFiles(ref(context.storage()));
  });
  await seedReceiptGuard(receiptPath);
});

after(async () => { await environment?.cleanup(); });

function client(actor) {
  return actor === null ? environment.unauthenticatedContext() : environment.authenticatedContext(actor);
}

async function clearFiles(folder) {
  const contents = await listAll(folder);
  for (const item of contents.items) await deleteObject(item);
  for (const prefix of contents.prefixes) await clearFiles(prefix);
}

async function denied(operation) {
  const error = await assertFails(operation);
  assert.match(error.code, /^(permission-denied|storage\/unauthorized)$/);
}

async function seedDocument(path, data = setup) {
  await environment.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), path), data);
  });
}

async function seedReceiptGuard(path, state = 'STAGED') {
  const parts = path.split('/');
  await seedDocument(`users/${parts[1]}/expenses/${parts[3]}`, { isDeleted: false, receiptObjectPath: null });
  await seedDocument(`users/${parts[1]}/receiptObjects/${parts[4]}`, { expenseCloudId: parts[3], objectPath: path, state });
}

async function seedFile(path = receiptPath, metadata = { contentType: 'image/png' }) {
  await environment.withSecurityRulesDisabled(async (context) => {
    await uploadBytes(ref(context.storage(), path), png, metadata);
  });
}

test('owner can create, read and repeat the unchanged setup document', async () => {
  const document = doc(client(owner).firestore(), `users/${owner}`);
  await assertSucceeds(setDoc(document, setup));
  assert.deepEqual((await assertSucceeds(getDoc(document))).data(), setup);
  await assertSucceeds(setDoc(document, setup));
});

for (const actor of [other, null]) {
  test(`${actor ?? 'signed-out'} cannot read, create, update or delete another UID setup`, async () => {
    const document = doc(client(actor).firestore(), `users/${owner}`);
    await denied(setDoc(document, setup));
    await seedDocument(`users/${owner}`);
    await denied(getDoc(document));
    await denied(updateDoc(document, { username: 'Taken' }));
    await denied(deleteDoc(document));
  });
}

test('owner cannot place a setup document under another UID', async () => {
  await denied(setDoc(doc(client(owner).firestore(), `users/${other}`), setup));
});

test('setup fields/types are exact; passwords and submitted ownership are rejected', async () => {
  const document = doc(client(owner).firestore(), `users/${owner}`);
  for (const invalid of [
    { ...setup, uid: other }, { ...setup, userId: owner }, { ...setup, password: 'synthetic' },
    { ...setup, schemaVersion: 2 }, { ...setup, schemaVersion: '1' }, { ...setup, schemaVersion: 1.5 },
    { ...setup, username: '' }, { ...setup, username: 42 },
    { ...setup, categoriesInitialized: true }, { ...setup, categoriesInitialized: 'false' },
    { schemaVersion: 1, username: 'Alice' },
  ]) {
    await denied(setDoc(document, invalid));
  }
});

test('setup username/ownership cannot change, defaults cannot be marked complete, delete stays closed', async () => {
  await seedDocument(`users/${owner}`);
  const document = doc(client(owner).firestore(), `users/${owner}`);
  await denied(updateDoc(document, { username: 'Bob' }));
  await denied(updateDoc(document, { uid: other }));
  await denied(updateDoc(document, { categoriesInitialized: true }));
  await denied(deleteDoc(document));
});

for (const actor of [owner, other, null]) {
  test(`${actor ?? 'signed-out'} cannot list user profiles`, async () => {
    await seedDocument(`users/${owner}`);
    await denied(getDocs(collection(client(actor).firestore(), 'users')));
  });

  test(`${actor ?? 'signed-out'} cannot access unimplemented or unmatched nested documents`, async () => {
    const database = client(actor).firestore();
    // I checked both known future paths and deeper/unexpected paths, rather than a broad users wildcard.
    for (const path of [
      `users/${owner}/savingsGoals/${expenseId}/unmatched/row`, `users/${owner}/achievements/first-save`,
      `users/${owner}/expenses/${expenseId}/private/hidden`,
      `users/${owner}/receipts/${expenseId}`, `users/${owner}/unexpected/value`, 'unexpected/value',
    ]) {
      const document = doc(database, path);
      await denied(setDoc(document, { amountCents: 1234, uid: actor ?? owner }));
      await seedDocument(path, { amountCents: 1234 });
      await denied(getDoc(document));
      await denied(getDocs(collection(database, path.substring(0, path.lastIndexOf('/')))));
      await denied(updateDoc(document, { amountCents: 2000, uid: other }));
      await denied(deleteDoc(document));
    }
  });
}

test('owner can upload, read and delete an exact nested receipt path', async () => {
  const object = ref(client(owner).storage(), receiptPath);
  await assertSucceeds(uploadBytes(object, png, { contentType: 'image/png' }));
  assert.deepEqual(Buffer.from(await assertSucceeds(getBytes(object))), png);
  await seedReceiptGuard(receiptPath, 'RETIRED');
  await assertSucceeds(deleteObject(object));
});

for (const actor of [other, null]) {
  test(`${actor ?? 'signed-out'} cannot upload, read, replace, change metadata or delete a receipt`, async () => {
    const object = ref(client(actor).storage(), receiptPath);
    await denied(uploadBytes(object, png, { contentType: 'image/png' }));
    await seedFile();
    await denied(getBytes(object));
    await denied(uploadBytes(object, png, { contentType: 'image/jpeg' }));
    await denied(updateMetadata(object, { customMetadata: { uid: actor ?? owner } }));
    await denied(deleteObject(object));
  });
}

test('owner cannot upload under another UID, even with an ownership claim', async () => {
  const object = ref(client(owner).storage(), receiptPath.replace(owner, other));
  await denied(uploadBytes(object, png, { contentType: 'image/png' }));
  await denied(uploadBytes(object, png, { contentType: 'image/png', customMetadata: { uid: owner } }));
});

test('owner accepts JPEG and the 10 MiB boundary; rejects empty, oversized and unsupported files', async () => {
  const storage = client(owner).storage();
  const invalidUploads = [
    [new Uint8Array(), { contentType: 'image/png' }],
    [png, { contentType: 'text/plain' }],
    [png, { contentType: 'image/gif' }],
    [png, undefined],
    [new Uint8Array(10 * 1024 * 1024 + 1), { contentType: 'image/png' }],
  ];
  for (const [index, [bytes, metadata]] of invalidUploads.entries()) {
    const path = receiptPath.replace(jobId, `33333333-3333-4333-8333-${String(index).padStart(12, '0')}`);
    const object = ref(storage, path);
    await seedReceiptGuard(path);
    await denied(uploadBytes(object, bytes, metadata));
    await assert.rejects(getBytes(object), { code: 'storage/object-not-found' });
  }
  // These bytes deliberately test metadata/size rules, not image decoding (slice 8).
  await assertSucceeds(uploadBytes(ref(storage, receiptPath), new Uint8Array(10 * 1024 * 1024), { contentType: 'image/jpeg' }));
});

test('receipt ownership/custom metadata, overwrite and metadata changes are rejected', async () => {
  const object = ref(client(owner).storage(), receiptPath);
  await denied(uploadBytes(object, png, { contentType: 'image/png', customMetadata: { uid: other } }));
  await seedFile();
  await denied(uploadBytes(object, png, { contentType: 'image/png' }));
  await denied(updateMetadata(object, { contentType: 'image/jpeg' }));
  await denied(updateMetadata(object, { customMetadata: { uid: other } }));
  await denied(updateMetadata(object, { customMetadata: { firebaseStorageDownloadTokens: 'synthetic-token' } }));
});

test('owner can clean up a legacy receipt whose metadata would fail a new upload', async () => {
  await seedFile(receiptPath, { contentType: 'text/plain', customMetadata: { uid: other } });
  await seedReceiptGuard(receiptPath, 'RETIRED');
  await assertSucceeds(deleteObject(ref(client(owner).storage(), receiptPath)));
});

for (const actor of [owner, other, null]) {
  test(`${actor ?? 'signed-out'} cannot access unexpected receipt paths or enumerate files`, async () => {
    const storage = client(actor).storage();
    for (const path of [
      `users/${owner}/receipts/${expenseId}/not-a-uuid`,
      `users/${owner}/receipts/not-a-uuid/${jobId}`,
      `${receiptPath}/extra`, `users/${owner}/receipts/${expenseId}`,
      `users/${owner}/expenses/${expenseId}/${jobId}`, `receipts/${jobId}`, `unexpected/${jobId}`,
    ]) {
      const object = ref(storage, path);
      await denied(uploadBytes(object, png, { contentType: 'image/png' }));
      await seedFile(path);
      await denied(getBytes(object));
      await denied(deleteObject(object));
    }
    for (const prefix of ['', 'users', `users/${owner}/receipts`, `users/${owner}/receipts/${expenseId}`]) {
      await denied(listAll(ref(storage, prefix)));
    }
  });
}
