import { after, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, serverTimestamp, setDoc, setLogLevel, updateDoc, writeBatch } from 'firebase/firestore';
import { deleteObject, getBytes, listAll, ref, uploadBytes } from 'firebase/storage';

const uid = 'receipt-owner';
const expense = '11111111-1111-4111-8111-111111111111';
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a7WQAAAAASUVORK5CYII=', 'base64');
let env;
const path = job => `users/${uid}/receipts/${expense}/${job}`;
const client = actor => (actor === null ? env.unauthenticatedContext() : env.authenticatedContext(actor ?? uid));
const expenseRef = db => doc(db, `users/${uid}/expenses/${expense}`);
const guardRef = (db, job) => doc(db, `users/${uid}/receiptObjects/${job}`);
before(async () => {
  assert.equal(process.env.GCLOUD_PROJECT, 'demo-pocket-quest');
  setLogLevel('silent');
  env = await initializeTestEnvironment({ projectId: 'demo-pocket-quest',
    firestore: { host: '127.0.0.1', port: 8080, rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
    storage: { host: '127.0.0.1', port: 9199, rules: readFileSync(new URL('../storage.rules', import.meta.url), 'utf8') } });
});
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async c => {
    async function clear(folder) { const files = await listAll(folder); for (const f of files.items) await deleteObject(f); for (const p of files.prefixes) await clear(p); }
    await clear(ref(c.storage()));
    await setDoc(doc(c.firestore(), `users/${uid}/categories/category`), { isDeleted: false });
  });
  const db = client().firestore();
  await mutate(db, { categoryId: 'category', amountCents: 9007199254740991, date: '2024-02-29', startTime: '09:00', endTime: '09:30',
    description: 'Synthetic receipt', receiptObjectPath: null }, 1);
});
after(async () => env?.cleanup());
async function reserve(db, job, patch = {}) {
  return setDoc(guardRef(db, job), { schemaVersion: 1, expenseCloudId: expense, objectPath: path(job), state: 'STAGED',
    createdAt: serverTimestamp(), updatedAt: serverTimestamp(), lastExpenseMutationId: null, ...patch });
}
async function mutate(db, fields, revision, transitions = [], deleted = false) {
  const mutation = randomUUID();
  const batch = writeBatch(db);
  batch.set(expenseRef(db), { ...fields, schemaVersion: 1, revision, lastMutationId: mutation, updatedAt: serverTimestamp(), isDeleted: deleted,
    deletedAt: deleted ? serverTimestamp() : null });
  batch.set(doc(db, `users/${uid}/mutations/${mutation}`), { recordType: 'expenses', recordId: expense, baseRevision: revision - 1,
    revision, fingerprint: 'a'.repeat(64) });
  for (const [job, state] of transitions) batch.update(guardRef(db, job), { state, updatedAt: serverTimestamp(), lastExpenseMutationId: mutation });
  return batch.commit();
}
async function fields(db) { const value = (await getDoc(expenseRef(db))).data(); return Object.fromEntries(['categoryId','amountCents','date','startTime','endTime','description','receiptObjectPath'].map(k => [k,value[k]])); }

test('reserve, upload, two-client read, publish, replace, retire and cleanup use production rules', async () => {
  const a = client(), b = client(); const db = a.firestore(); const first = randomUUID(), next = randomUUID();
  await assertSucceeds(reserve(db, first));
  await assertSucceeds(uploadBytes(ref(a.storage(), path(first)), png, { contentType: 'image/png' }));
  await assertFails(deleteObject(ref(a.storage(), path(first))));
  await assertSucceeds(mutate(db, { ...await fields(db), receiptObjectPath: path(first) }, 2, [[first,'PUBLISHED']]));
  assert.deepEqual(Buffer.from(await getBytes(ref(b.storage(), path(first)))), png);
  await assertFails(deleteObject(ref(a.storage(), path(first))));
  await reserve(db, next); await uploadBytes(ref(a.storage(), path(next)), png, { contentType: 'image/png' });
  await assertSucceeds(mutate(db, { ...await fields(db), receiptObjectPath: path(next) }, 3, [[first,'RETIRED'],[next,'PUBLISHED']]));
  await assertFails(getBytes(ref(a.storage(), path(first))));
  await assertSucceeds(deleteObject(ref(a.storage(), path(first))));
  await assertFails(uploadBytes(ref(a.storage(), path(first)), png, { contentType: 'image/png' }));
  await assertFails(mutate(db, { ...await fields(db), receiptObjectPath: path(first) }, 4, [[first,'PUBLISHED'],[next,'RETIRED']]));
});
test('publication requires atomic guard transitions and the old photo must retire', async () => {
  const db = client().firestore(), a = randomUUID(), b = randomUUID(); await reserve(db,a); await reserve(db,b);
  const original = await fields(db);
  await assertFails(mutate(db,{ ...original,receiptObjectPath:path(a) },2));
  await mutate(db,{ ...original,receiptObjectPath:path(a) },2,[[a,'PUBLISHED']]);
  await assertFails(mutate(db,{ ...original,receiptObjectPath:path(b) },3,[[b,'PUBLISHED']]));
  await assertFails(updateDoc(guardRef(db,a),{state:'RETIRED',updatedAt:serverTimestamp(),lastExpenseMutationId:null}));
  await assertFails(deleteDoc(guardRef(db,a)));
});
test('cancelling staged objects permanently prevents a raced publication', async () => {
  const db = client().firestore(), job = randomUUID(); await reserve(db,job);
  await assertSucceeds(updateDoc(guardRef(db,job),{state:'RETIRED',updatedAt:serverTimestamp(),lastExpenseMutationId:null}));
  await assertFails(mutate(db,{ ...await fields(db),receiptObjectPath:path(job) },2,[[job,'PUBLISHED']]));
  await assertFails(reserve(db,job));
});
test('concurrent same-revision publications accept exactly one association', async () => {
  const db = client().firestore(), a=randomUUID(), b=randomUUID(); await reserve(db,a);await reserve(db,b);const initial=await fields(db);
  const results=await Promise.allSettled([mutate(db,{...initial,receiptObjectPath:path(a)},2,[[a,'PUBLISHED']]),mutate(client().firestore(),{...initial,receiptObjectPath:path(b)},2,[[b,'PUBLISHED']])]);
  assert.equal(results.filter(r=>r.status==='fulfilled').length,1);
});
test('tombstone keeps its photo path and retires its object in the same transaction', async () => {
  const db=client().firestore(), job=randomUUID();await reserve(db,job);await mutate(db,{...await fields(db),receiptObjectPath:path(job)},2,[[job,'PUBLISHED']]);
  await assertFails(mutate(db,await fields(db),3,[],true));
  await assertSucceeds(mutate(db,await fields(db),3,[[job,'RETIRED']],true));
  await assertFails(reserve(db,randomUUID()));
  await assertFails(mutate(db,{...await fields(db),receiptObjectPath:null},4));
});
test('removal clears only the live reference; retired identity cannot be reused', async () => {
  const db=client().firestore(), job=randomUUID();await reserve(db,job);await mutate(db,{...await fields(db),receiptObjectPath:path(job)},2,[[job,'PUBLISHED']]);
  await assertSucceeds(mutate(db,{...await fields(db),receiptObjectPath:null},3,[[job,'RETIRED']]));
  await assertFails(deleteDoc(guardRef(db,job)));
});
test('wrong expense, owner, shape, standalone publication and unauthenticated lifecycle writes are denied', async () => {
  const db=client().firestore();
  for(const patch of [{expenseCloudId:randomUUID()},{objectPath:'users/other/receipts/bad/bad'},{state:'PUBLISHED'},{extra:true},{schemaVersion:2}])await assertFails(reserve(db,randomUUID(),patch));
  for(const actor of ['other',null]){const bad=client(actor);await assertFails(reserve(bad.firestore(),randomUUID()));await assertFails(getDoc(expenseRef(bad.firestore())));}
});
test('Storage requires an existing live expense and exact matching guard',async()=>{
  const c=client(),job=randomUUID(),object=ref(c.storage(),path(job));await assertFails(uploadBytes(object,png,{contentType:'image/png'}));
  await reserve(c.firestore(),job);await uploadBytes(object,png,{contentType:'image/png'});
  await assertFails(deleteObject(object));
  for(const actor of ['other',null]){await assertFails(getBytes(ref(client(actor).storage(),path(job))));await assertFails(deleteObject(ref(client(actor).storage(),path(job))));}
});
