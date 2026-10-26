import { test } from 'node:test';
import assert from 'node:assert/strict';
import { migrationWrites } from './migrate-expense-payment-time.mjs';

function legacy(deleted = false) {
  return { name: 'projects/pocket-quest-c7127/databases/(default)/documents/users/owner/expenses/expense',
    updateTime: '2026-10-26T12:00:00Z', fields: {
      categoryId: { stringValue: 'category' }, amountCents: { integerValue: '9007199254740993' },
      date: { stringValue: '2024-02-29' }, startTime: { stringValue: '09:15' }, endTime: { stringValue: '10:45' },
      description: { stringValue: 'Legacy' }, receiptObjectPath: { stringValue: 'users/owner/receipts/expense/photo' },
      schemaVersion: { integerValue: '1' }, revision: { integerValue: '9007199254740993' },
      lastMutationId: { stringValue: 'old-mutation' }, updatedAt: { timestampValue: '2026-10-25T12:00:00Z' },
      isDeleted: { booleanValue: deleted }, deletedAt: deleted ? { timestampValue: '2026-10-25T12:00:00Z' } : { nullValue: null }
    } };
}

test('migration keeps exact money, tombstones and photo references with a concurrency precondition', () => {
  for (const deleted of [false, true]) {
    const old = legacy(deleted);
    const writes = migrationWrites(old, 'new-mutation');
    assert.equal(writes.length, 2);
    const [expense, receipt] = writes;
    assert.deepEqual(expense.update.fields.paymentTime, old.fields.startTime);
    assert.equal('startTime' in expense.update.fields, false);
    assert.equal('endTime' in expense.update.fields, false);
    for (const key of ['categoryId', 'amountCents', 'date', 'description', 'receiptObjectPath', 'isDeleted', 'deletedAt']) {
      assert.deepEqual(expense.update.fields[key], old.fields[key]);
    }
    assert.equal(expense.update.fields.revision.integerValue, '9007199254740994');
    assert.deepEqual(expense.currentDocument, { updateTime: old.updateTime });
    assert.equal(receipt.update.fields.baseRevision.integerValue, '9007199254740993');
    assert.equal(receipt.update.fields.revision.integerValue, '9007199254740994');
    assert.deepEqual(receipt.currentDocument, { exists: false });
    assert.deepEqual(migrationWrites({ ...old, fields: expense.update.fields }), []);
  }
});

test('unexpected paths, mixed schemas, invalid payment times and revision overflow stop the migration', () => {
  assert.throws(() => migrationWrites({ ...legacy(), name: 'other-project/expenses/expense' }));
  for (const patch of [{ paymentTime: { stringValue: '09:15' } }, { startTime: { stringValue: '24:00' } },
    { revision: { integerValue: '9223372036854775807' } }, { schemaVersion: { integerValue: '3' } }]) {
    const old = legacy();
    assert.throws(() => migrationWrites({ ...old, fields: { ...old.fields, ...patch } }));
  }
});
