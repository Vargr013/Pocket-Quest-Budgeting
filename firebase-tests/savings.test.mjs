import { after, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDoc, getDocs, runTransaction, serverTimestamp, setDoc, setLogLevel, writeBatch } from 'firebase/firestore';

let environment;
const uid = 'savings-owner', goal = '11111111-1111-4111-8111-111111111111';
const goalFields = { name: 'Synthetic goal', targetCents: 10000, targetDate: '2028-02-29', note: null };
const eventFields = { goalCloudId: goal, kind: 'ADDITION', amountCents: 123, effectiveDate: '2024-02-29', reversesBusinessId: null, note: null };
const awardFields = { badgeId: 'synthetic', criteriaVersion: 1, earnedAtEpochMillis: null, earnedDate: '2024-02-29', earnedZoneId: 'Africa/Johannesburg', evidenceRecordId: 'synthetic-source' };
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
function ref(db, type, id, owner = uid) { return doc(db, `users/${owner}/${type}/${id}`); }
function snapshot(fields, revision = 1, deleted = false) { return { ...fields, schemaVersion: 1, revision,
  lastMutationId: randomUUID(), updatedAt: serverTimestamp(), isDeleted: deleted, deletedAt: deleted ? serverTimestamp() : null }; }
function mutation(type, id, data) { return { recordType: type, recordId: id, baseRevision: data.revision - 1, revision: data.revision, fingerprint: 'a'.repeat(64) }; }
async function save(db, type, id, data, owner = uid) {
  const batch = writeBatch(db); batch.set(ref(db, type, id, owner), data);
  batch.set(ref(db, 'mutations', data.lastMutationId, owner), mutation(type, id, data)); return batch.commit();
}
async function denied(promise) { const error = await assertFails(promise); assert.equal(error.code, 'permission-denied'); }
async function seedGoal(db) { await save(db, 'savingsGoals', goal, snapshot(goalFields)); }
async function contribute(db, id = randomUUID(), fields = eventFields, patchBalance = {}) {
  // Independent clients read the current balance in the same transaction as the new business event.
  return runTransaction(db, async transaction => {
    const current = await transaction.get(ref(db, 'savingsContributions', id));
    if (current.exists()) return;
    const old = await transaction.get(ref(db, 'savingsBalances', goal));
    const data = snapshot(fields), batchBalance = (old.data()?.balanceCents ?? 0) + (fields.kind === 'REVERSAL' ? -fields.amountCents : fields.amountCents);
    transaction.set(ref(db, 'savingsContributions', id), data);
    transaction.set(ref(db, 'mutations', data.lastMutationId), mutation('savingsContributions', id, data));
    transaction.set(ref(db, 'savingsBalances', goal), { schemaVersion: 1, balanceCents: batchBalance,
      lastContributionId: id, lastMutationId: data.lastMutationId, updatedAt: serverTimestamp(), ...patchBalance });
    if (fields.kind === 'REVERSAL') transaction.set(ref(db, 'savingsReversals', fields.reversesBusinessId), {
      schemaVersion: 1, goalCloudId: fields.goalCloudId, contributionId: id, lastMutationId: data.lastMutationId, createdAt: serverTimestamp() });
  });
}

test('savings owner creates edits removes and retains a goal while other accounts are denied', async () => {
  const db = client(); await seedGoal(db);
  await assertSucceeds(save(db, 'savingsGoals', goal, snapshot({ ...goalFields, targetCents: 1 }, 2)));
  await denied(save(client('other'), 'savingsGoals', goal, snapshot(goalFields)));
  await denied(getDoc(ref(client('other'), 'savingsGoals', goal)));
  await denied(getDocs(collection(client(null), `users/${uid}/savingsGoals`)));
  await assertSucceeds(save(db, 'savingsGoals', goal, snapshot({ ...goalFields, targetCents: 1 }, 3, true)));
  await denied(save(db, 'savingsGoals', goal, snapshot(goalFields, 4)));
  await denied(deleteDoc(ref(db, 'savingsGoals', goal)));
  assert.equal((await getDocs(collection(db, `users/${uid}/savingsGoals`))).size, 1);
});

test('savings goals reject extra missing malformed dates targets and unpaired writes', async () => {
  const db = client();
  for (const patch of [{ targetCents: 0 }, { targetCents: -1 }, { targetCents: 1.5 }, { targetCents: '100' },
    { targetDate: '2025-02-29' }, { name: '' }, { name: ' padded ' }, { name: 'a'.repeat(101) }, { note: '' }, { note: 'x'.repeat(1001) }, { userId: 1 }]) {
    await denied(save(db, 'savingsGoals', goal, snapshot({ ...goalFields, ...patch })));
  }
  for (const key of Object.keys(goalFields)) { const fields = { ...goalFields }; delete fields[key]; await denied(save(db, 'savingsGoals', goal, snapshot(fields))); }
  await denied(setDoc(ref(db, 'savingsGoals', goal), snapshot(goalFields)));
  await denied(save(db, 'savingsGoals', 'invalid', snapshot(goalFields)));
});

test('independent concurrent additions both survive and stable business replay counts once', async () => {
  const db = client(), second = client(); await seedGoal(db);
  const id = randomUUID();
  await Promise.all([contribute(db, id, { ...eventFields, amountCents: 101 }), contribute(second, randomUUID(), { ...eventFields, amountCents: 202 })]);
  await contribute(second, id, { ...eventFields, amountCents: 101 });
  assert.equal((await getDoc(ref(db, 'savingsBalances', goal))).data().balanceCents, 303);
  assert.equal((await getDocs(collection(db, `users/${uid}/savingsContributions`))).size, 2);
  await denied(save(db, 'savingsContributions', id, snapshot({ ...eventFields, amountCents: 999 }, 2)));
  await denied(deleteDoc(ref(db, 'savingsContributions', id)));
});

test('opening identity is deterministic and cannot be reissued under another business identity', async () => {
  const db = client(); await seedGoal(db);
  await assertSucceeds(contribute(db, `opening:${goal}`, { ...eventFields, kind: 'OPENING' }));
  await denied(contribute(db, randomUUID(), { ...eventFields, kind: 'OPENING' }));
  await denied(contribute(db, 'opening:other', eventFields));
  await denied(save(db, 'savingsContributions', `opening:${goal}`, snapshot({ ...eventFields, kind: 'OPENING' })));
});

test('contributions require exact fields positive integer cents and atomic correct balance changes', async () => {
  const db = client(); await seedGoal(db);
  for (const patch of [{ amountCents: 0 }, { amountCents: -1 }, { amountCents: 1.5 }, { effectiveDate: '2025-02-29' },
    { goalId: 1 }, { userId: 1 }, { kind: 'UNKNOWN' }, { reversesBusinessId: 'unexpected' }, { note: ' ' }]) {
    await denied(contribute(db, randomUUID(), { ...eventFields, ...patch }));
  }
  await denied(save(db, 'savingsContributions', randomUUID(), snapshot(eventFields)));
  await denied(contribute(db, randomUUID(), eventFields, { balanceCents: 999 }));
  await denied(setDoc(ref(db, 'savingsBalances', goal), { schemaVersion: 1, balanceCents: 123, lastContributionId: 'missing', lastMutationId: randomUUID(), updatedAt: serverTimestamp() }));
  await denied(contribute(client('other')));
  await denied(getDoc(ref(client('other'), 'savingsBalances', goal)));
  assert.equal((await getDocs(collection(db, `users/${uid}/savingsContributions`))).size, 0);
});

test('full reversal is immutable paired once and tied to the same goal and original amount', async () => {
  const db = client(); await seedGoal(db); const original = randomUUID(); await contribute(db, original);
  await denied(contribute(db, randomUUID(), { ...eventFields, kind: 'REVERSAL', reversesBusinessId: original, amountCents: 1 }));
  const reversed = { ...eventFields, kind: 'REVERSAL', reversesBusinessId: original };
  const id = randomUUID(); await assertSucceeds(contribute(db, id, reversed));
  await denied(contribute(db, randomUUID(), reversed));
  await denied(contribute(db, randomUUID(), { ...reversed, reversesBusinessId: id }));
  await denied(deleteDoc(ref(db, 'savingsReversals', original)));
  await denied(getDoc(ref(client('other'), 'savingsReversals', original)));
  assert.equal((await getDoc(ref(db, 'savingsBalances', goal))).data().balanceCents, 0);
});

test('goal removal rejects later contributions and a same-batch deletion cannot smuggle one in', async () => {
  const db = client(); await seedGoal(db); await contribute(db);
  const removed = snapshot(goalFields, 2, true), event = snapshot(eventFields), id = randomUUID();
  const batch = writeBatch(db);
  batch.set(ref(db, 'savingsGoals', goal), removed); batch.set(ref(db, 'mutations', removed.lastMutationId), mutation('savingsGoals', goal, removed));
  batch.set(ref(db, 'savingsContributions', id), event); batch.set(ref(db, 'mutations', event.lastMutationId), mutation('savingsContributions', id, event));
  batch.set(ref(db, 'savingsBalances', goal), { schemaVersion: 1, balanceCents: 246, lastContributionId: id, lastMutationId: event.lastMutationId, updatedAt: serverTimestamp() });
  await denied(batch.commit());
  await save(db, 'savingsGoals', goal, removed); await denied(contribute(db));
  assert.equal((await getDocs(collection(db, `users/${uid}/savingsContributions`))).size, 1);
});

test('awards retain owner asserted evidence without replacing earned time or allowing other accounts', async () => {
  const db = client(), id = 'synthetic:2024-02';
  await assertSucceeds(save(db, 'earnedAchievements', id, snapshot(awardFields)));
  assert.equal((await getDoc(ref(db, 'earnedAchievements', id))).data().earnedAtEpochMillis, null);
  await denied(save(db, 'earnedAchievements', id, snapshot({ ...awardFields, earnedDate: '2024-03-01' }, 2)));
  await denied(deleteDoc(ref(db, 'earnedAchievements', id)));
  await denied(getDoc(ref(client('other'), 'earnedAchievements', id)));
  await denied(save(client('other'), 'earnedAchievements', randomUUID(), snapshot(awardFields)));
  for (const patch of [{ criteriaVersion: 0 }, { criteriaVersion: 2147483648 }, { earnedDate: null }, { earnedZoneId: null },
    { earnedAtEpochMillis: -1 }, { earnedDate: '2025-02-29' }, { evidenceRecordId: '' }, { fakeProgress: 7 }]) {
    await denied(save(db, 'earnedAchievements', randomUUID(), snapshot({ ...awardFields, ...patch })));
  }
});
