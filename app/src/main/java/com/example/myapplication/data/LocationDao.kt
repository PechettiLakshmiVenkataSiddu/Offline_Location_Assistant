package com.example.myapplication.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LocationDao {
    @Insert
    suspend fun insert(location: LocationEntity)

    @Query("SELECT * FROM locations")
    suspend fun getAll(): List<LocationEntity>

    /**
     * Finds the nearest location using squared Euclidean distance approximation.
     * This is efficient for SQLite and sufficient for nearest-neighbor lookup.
     */
    @Query("""
        SELECT * FROM locations 
        ORDER BY (lat - :lat) * (lat - :lat) + (long - :long) * (long - :long) ASC 
        LIMIT 1
    """)
    suspend fun getNearestLocation(lat: Double, long: Double): LocationEntity?
}
