package com.example.pocketquestbudgeting.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GamificationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertActiveDay(day: ActivityDayEntity)

    suspend fun markActiveDay(userId: Long, date: String) = insertActiveDay(ActivityDayEntity(userId, date))

    @Query("SELECT date FROM activity_days WHERE userId = :userId ORDER BY date")
    suspend fun getActiveDays(userId: Long): List<String>

    @Insert
    suspend fun insertCustomItem(item: CustomWorthItemEntity): Long

    @Query("SELECT * FROM custom_worth_items WHERE userId = :userId AND isDeleted = 0 ORDER BY name COLLATE NOCASE, id")
    suspend fun getCustomItems(userId: Long): List<CustomWorthItemEntity>

    @Query("SELECT * FROM custom_worth_items WHERE userId = :userId ORDER BY id")
    suspend fun getAllCustomItems(userId: Long): List<CustomWorthItemEntity>

    @Query("SELECT * FROM custom_worth_items WHERE userId = :userId AND id = :id")
    suspend fun getCustomItem(userId: Long, id: Long): CustomWorthItemEntity?

    @Query("SELECT * FROM custom_worth_items WHERE userId = :userId AND cloudId = :cloudId")
    suspend fun getByCloudId(userId: Long, cloudId: String): CustomWorthItemEntity?

    @Update
    suspend fun updateCustomItem(item: CustomWorthItemEntity): Int

    @Query("UPDATE custom_worth_items SET isDeleted = 1 WHERE userId = :userId AND id = :id")
    suspend fun deleteCustomItem(userId: Long, id: Long): Int

    @Query("SELECT * FROM custom_worth_items WHERE userId = :userId AND isDeleted = 0 ORDER BY name COLLATE NOCASE, id")
    fun observeCustomItems(userId: Long): Flow<List<CustomWorthItemEntity>>
}
