package com.example.data.local

import com.example.data.model.RideLog
import kotlinx.coroutines.flow.Flow

class RideRepository(private val rideDao: RideDao) {
    val allRides: Flow<List<RideLog>> = rideDao.getAllRides()

    suspend fun insertRide(ride: RideLog) {
        rideDao.insertRide(ride)
    }

    suspend fun deleteRide(id: Int) {
        rideDao.deleteRide(id)
    }

    suspend fun clearAllRides() {
        rideDao.clearAllRides()
    }
}
