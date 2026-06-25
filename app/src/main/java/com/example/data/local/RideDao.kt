package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.RideLog
import kotlinx.coroutines.flow.Flow

@Dao
interface RideDao {
    @Query("SELECT * FROM rides ORDER BY dateTimestamp DESC")
    fun getAllRides(): Flow<List<RideLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRide(ride: RideLog)

    @Query("DELETE FROM rides WHERE id = :id")
    suspend fun deleteRide(id: Int)

    @Query("DELETE FROM rides")
    suspend fun clearAllRides()
}
