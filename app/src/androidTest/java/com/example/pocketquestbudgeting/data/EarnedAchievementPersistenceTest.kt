package com.example.pocketquestbudgeting.data

import android.content.Context
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
class EarnedAchievementPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "award-fixture-${UUID.randomUUID()}"
    private lateinit var db: AppDatabase
    private var owner = 0L
    private var other = 0L
    private fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
    private fun award() = EarnedAchievementEntity(userId = owner, awardKey = "synthetic:2024-02", badgeId = "synthetic",
        criteriaVersion = 1, earnedDate = "2024-02-29", earnedZoneId = "Africa/Johannesburg", evidenceRecordId = "synthetic-record")

    @Before fun setup() = runBlocking {
        db = open()
        owner = db.userDao().insert(UserEntity(username = "Synthetic owner"))
        other = db.userDao().insert(UserEntity(username = "Synthetic other"))
    }
    @After fun finish() { db.close(); context.deleteDatabase(name) }
    private suspend fun rejects(block: suspend () -> Unit) {
        try { block(); fail("Expected invalid award evidence to be rejected") } catch (_: IllegalArgumentException) { }
    }

    @Test fun suppliedEvidenceSurvivesReevaluationReopenAndConcurrentRecording() = runBlocking {
        val row = award()
        val ids = coroutineScope { (1..12).map { async(Dispatchers.IO) { db.earnedAchievementDao().recordEarned(row) } }.awaitAll() }
        assertEquals(1, ids.toSet().size)
        rejects { db.earnedAchievementDao().recordEarned(row.copy(criteriaVersion = 2)) }
        rejects { db.earnedAchievementDao().recordEarned(row.copy(earnedDate = "2024-03-01")) }
        db.close(); db = open()
        assertEquals(row.copy(id = ids.first()), db.earnedAchievementDao().getForUser(owner).single())
        assertNull(db.earnedAchievementDao().getForUser(owner).single().earnedAtEpochMillis)
        assertEquals(ids.first(), db.earnedAchievementDao().recordEarned(row))
    }

    @Test fun ownerAndCallerSelectedPeriodArePartOfAwardIdentity() = runBlocking {
        val row = award()
        db.earnedAchievementDao().recordEarned(row)
        db.earnedAchievementDao().recordEarned(row.copy(userId = other))
        assertEquals(1, db.earnedAchievementDao().getForUser(other).size)
        db.earnedAchievementDao().recordEarned(row.copy(awardKey = "synthetic:2024-03", earnedDate = "2024-03-31"))
        assertEquals(2, db.earnedAchievementDao().observeForUser(owner).first().size)
        assertNull(db.earnedAchievementDao().getByAwardKey(other, "synthetic:2024-03"))
    }

    @Test fun noHistoricalTimeOrEvidenceIsInvented() = runBlocking {
        val row = award()
        rejects { db.earnedAchievementDao().recordEarned(row.copy(earnedDate = null)) }
        rejects { db.earnedAchievementDao().recordEarned(row.copy(earnedZoneId = null)) }
        rejects { db.earnedAchievementDao().recordEarned(row.copy(earnedZoneId = "Unknown/Zone")) }
        rejects { db.earnedAchievementDao().recordEarned(row.copy(earnedDate = "2025-02-29")) }
        rejects { db.earnedAchievementDao().recordEarned(row.copy(evidenceRecordId = "")) }
        rejects { db.earnedAchievementDao().recordEarned(row.copy(criteriaVersion = 0)) }
        rejects { db.earnedAchievementDao().recordEarned(row.copy(earnedAtEpochMillis = -1)) }
        assertTrue(db.earnedAchievementDao().getForUser(owner).isEmpty())
        val instantOnly = row.copy(earnedDate = null, earnedZoneId = null, earnedAtEpochMillis = 1709164800000)
        db.earnedAchievementDao().recordEarned(instantOnly)
        assertEquals(instantOnly.copy(id = 1), db.earnedAchievementDao().getForUser(owner).single())
    }
}
