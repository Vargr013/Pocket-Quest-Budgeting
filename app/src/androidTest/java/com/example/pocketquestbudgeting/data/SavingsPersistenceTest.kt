package com.example.pocketquestbudgeting.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavingsPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "savings-fixture-${UUID.randomUUID()}"
    private lateinit var db: AppDatabase
    private var owner = 0L
    private var other = 0L
    private fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(*DatabaseMigrations.ALL).build()

    @Before fun setup() = runBlocking {
        db = open()
        owner = db.userDao().insert(UserEntity(username = "Synthetic owner"))
        other = db.userDao().insert(UserEntity(username = "Synthetic other"))
    }
    @After fun finish() { db.close(); context.deleteDatabase(name) }

    private fun goal(userId: Long = owner) = SavingsGoalEntity(userId = userId, cloudId = UUID.randomUUID().toString(), name = "Synthetic goal", targetCents = 10000)
    private fun addition(goalId: Long, cents: Long = 123, key: String = UUID.randomUUID().toString()) = SavingsContributionEntity(
        userId = owner, goalId = goalId, businessId = key, kind = SavingsChecks.ADDITION,
        amountCents = cents, effectiveDate = "2024-02-29",
    )
    private suspend fun rejects(block: suspend () -> Unit) {
        try { block(); fail("Expected invalid values to be rejected") } catch (_: IllegalArgumentException) { }
    }

    @Test fun openingAndIndependentAdditionsRemainExactAfterReplayAndReopen() = runBlocking {
        val row = goal()
        val id = db.savingsDao().createGoal(row, 9007199254740993L, "2024-02-29")
        assertEquals(id, db.savingsDao().createGoal(row, 9007199254740993L, "2024-02-29"))
        val first = addition(id, 201)
        val firstId = db.savingsDao().recordContribution(first)
        val second = addition(id, 302)
        db.savingsDao().recordContribution(second)
        assertEquals(firstId, db.savingsDao().recordContribution(first))
        rejects { db.savingsDao().recordContribution(first.copy(amountCents = 999)) }
        db.close(); db = open()
        assertEquals(9007199254741496L, db.savingsDao().savedCents(owner, id))
        assertEquals(3, db.savingsDao().contributions(owner, id).size)
        assertEquals(firstId, db.savingsDao().recordContribution(first))
        assertEquals(row.cloudId, db.savingsDao().getForUserById(owner, id)?.cloudId)
        assertTrue(db.syncDao().pending("unused").isEmpty())
    }

    @Test fun removalRetainsHistoryAndAllowsOnlyAnExactReplay() = runBlocking {
        val id = db.savingsDao().createGoal(goal(), 50, "2024-02-29")
        val event = addition(id)
        val eventId = db.savingsDao().recordContribution(event)
        assertEquals(1, db.savingsDao().removeGoal(owner, id))
        assertTrue(db.savingsDao().getForUser(owner).isEmpty())
        assertTrue(db.savingsDao().observeForUser(owner).first().isEmpty())
        assertEquals(1, db.savingsDao().getForUser(owner, true).size)
        assertEquals(173L, db.savingsDao().savedCents(owner, id))
        assertEquals(2, db.savingsDao().observeContributions(owner, id).first().size)
        assertEquals(eventId, db.savingsDao().recordContribution(event))
        rejects { db.savingsDao().recordContribution(addition(id)) }
        rejects { db.savingsDao().editGoal(owner, id, "Changed", 1, null, null) }
        db.close(); db = open()
        assertTrue(requireNotNull(db.savingsDao().getForUserById(owner, id)).isDeleted)
        assertEquals(2, db.savingsDao().contributions(owner, id).size)
    }

    @Test fun fullReversalIsRetainedAndCannotBeRepeatedOrAppliedToAnotherGoal() = runBlocking {
        val id = db.savingsDao().createGoal(goal())
        val event = addition(id)
        db.savingsDao().recordContribution(event)
        val reversal = addition(id).copy(kind = SavingsChecks.REVERSAL, reversesBusinessId = event.businessId)
        rejects { db.savingsDao().recordContribution(reversal.copy(amountCents = 1)) }
        val another = db.savingsDao().createGoal(goal())
        rejects { db.savingsDao().recordContribution(reversal.copy(goalId = another)) }
        val reversalId = db.savingsDao().recordContribution(reversal)
        assertEquals(reversalId, db.savingsDao().recordContribution(reversal))
        assertEquals(0L, db.savingsDao().savedCents(owner, id))
        rejects { db.savingsDao().recordContribution(reversal.copy(businessId = UUID.randomUUID().toString())) }
        rejects { db.savingsDao().recordContribution(reversal.copy(businessId = UUID.randomUUID().toString(), reversesBusinessId = reversal.businessId)) }
        assertEquals(2, db.savingsDao().contributions(owner, id).size)
    }

    @Test fun ownerChecksAndForeignKeysBlockCrossAccountHistoryAndHardDeletion() = runBlocking {
        val row = goal()
        val id = db.savingsDao().createGoal(row)
        val otherId = db.savingsDao().createGoal(row.copy(userId = other))
        assertNull(db.savingsDao().getForUserById(other, id))
        assertEquals(0, db.savingsDao().removeGoal(other, id))
        rejects { db.savingsDao().editGoal(other, id, "Changed", 2, null, null) }
        rejects { db.savingsDao().recordContribution(addition(otherId)) }
        rejects { db.savingsDao().savedCents(other, id) }
        val event = addition(id)
        db.savingsDao().recordContribution(event)
        db.savingsDao().recordContribution(event.copy(userId = other, goalId = otherId))
        assertEquals(123L, db.savingsDao().savedCents(other, otherId))
        assertTrue(db.savingsDao().contributions(other, id).isEmpty())
        try {
            db.openHelper.writableDatabase.execSQL("DELETE FROM savings_goals WHERE id = ?", arrayOf(id))
            fail("History must prevent physical parent deletion")
        } catch (_: SQLiteConstraintException) { }
        try {
            db.openHelper.writableDatabase.execSQL("INSERT INTO savings_contributions (userId,goalId,businessId,kind,amountCents,effectiveDate) VALUES (?,?,?,'ADDITION',1,'2024-02-29')", arrayOf<Any>(other, id, "foreign-row"))
            fail("Composite foreign key must reject the other owner")
        } catch (_: SQLiteConstraintException) { }
    }

    @Test fun overflowAndInvalidOpeningRollBackWithoutPartialRows() = runBlocking {
        val invalid = goal()
        rejects { db.savingsDao().createGoal(invalid, 1, "2025-02-29") }
        assertNull(db.savingsDao().getByCloudId(owner, invalid.cloudId))
        val id = db.savingsDao().createGoal(goal(), Long.MAX_VALUE, "2024-02-29")
        try { db.savingsDao().recordContribution(addition(id, 1)); fail("Overflow") } catch (_: ArithmeticException) { }
        assertEquals(Long.MAX_VALUE, db.savingsDao().savedCents(owner, id))
        assertEquals(1, db.savingsDao().contributions(owner, id).size)
        rejects { db.savingsDao().recordContribution(addition(id, 0)) }
        rejects { db.savingsDao().recordContribution(addition(id).copy(businessId = "opening:reserved")) }
    }

    @Test fun concurrentIndependentAddsSurviveAndRepeatedIntentIsCountedOnce() = runBlocking {
        val id = db.savingsDao().createGoal(goal())
        val repeated = addition(id, 7)
        coroutineScope {
            (1..16).map { async(Dispatchers.IO) { db.savingsDao().recordContribution(addition(id, 1)) } }.awaitAll()
            (1..16).map { async(Dispatchers.IO) { db.savingsDao().recordContribution(repeated) } }.awaitAll()
        }
        assertEquals(23L, db.savingsDao().savedCents(owner, id))
        assertEquals(17, db.savingsDao().contributions(owner, id).size)
    }

    @Test fun failedOpeningInsertRollsBackTheNewGoalInTheSameTransaction() = runBlocking {
        val row = goal()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_synthetic_opening BEFORE INSERT ON savings_contributions BEGIN SELECT RAISE(ABORT, 'Synthetic opening failure'); END")
        try {
            db.savingsDao().createGoal(row, 50, "2024-02-29")
            fail("Opening failure should abort creation")
        } catch (_: SQLiteConstraintException) { }
        assertNull(db.savingsDao().getByCloudId(owner, row.cloudId))
        assertNull(db.savingsDao().contribution(owner, "opening:${row.cloudId}"))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_synthetic_opening")
        val id = db.savingsDao().createGoal(row, 50, "2024-02-29")
        assertEquals(50L, db.savingsDao().savedCents(owner, id))
    }

    @Test fun goalEditsKeepIdentityAndNeverReplaceSavedHistory() = runBlocking {
        val row = goal()
        val id = db.savingsDao().createGoal(row, 200, "2024-02-29")
        assertEquals(1, db.savingsDao().editGoal(owner, id, "Updated goal", 100, "2028-02-29", "Synthetic note"))
        assertEquals(200L, db.savingsDao().savedCents(owner, id))
        val updated = requireNotNull(db.savingsDao().getByCloudId(owner, row.cloudId))
        assertEquals(id, updated.id)
        assertEquals(100L, updated.targetCents)
        rejects { db.savingsDao().editGoal(owner, id, "", 100, null, null) }
        rejects { db.savingsDao().editGoal(owner, id, "Valid", 0, null, null) }
        rejects { db.savingsDao().editGoal(owner, id, "Valid", 1, "2025-02-29", null) }
    }
}
