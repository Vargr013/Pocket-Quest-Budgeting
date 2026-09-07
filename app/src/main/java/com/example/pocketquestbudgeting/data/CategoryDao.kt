package com.example.pocketquestbudgeting.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface CategoryDao {
    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Query("SELECT * FROM categories WHERE userId = :userId ORDER BY name, id")
    suspend fun getForUser(userId: Long): List<CategoryEntity>
}
