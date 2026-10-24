package com.example.pocketquestbudgeting.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GamificationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertActiveDay(day: ActivityDayEntity)

    suspend fun markActiveDay(userId: Long, date: String) = insertActiveDay(ActivityDayEntity(userId, date))

    @Query("SELECT date FROM activity_days WHERE userId = :userId ORDER BY date")
    suspend fun getActiveDays(userId: Long): List<String>

    @Insert
    suspend fun insertCustomItem(item: CustomWorthItemEntity): Long

    @Query("SELECT * FROM custom_worth_items WHERE userId = :userId ORDER BY name COLLATE NOCASE, id")
    suspend fun getCustomItems(userId: Long): List<CustomWorthItemEntity>

    @Query("DELETE FROM custom_worth_items WHERE userId = :userId AND id = :id")
    suspend fun deleteCustomItem(userId: Long, id: Long): Int
}
