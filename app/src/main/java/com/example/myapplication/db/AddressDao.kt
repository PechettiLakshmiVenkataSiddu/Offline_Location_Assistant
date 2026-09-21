package com.example.myapplication.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AddressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(addresses: List<Address>)

    @Query("""
        SELECT * FROM addresses 
        ORDER BY ((lat - :lat) * (lat - :lat) + (lng - :lng) * (lng - :lng)) ASC 
        LIMIT 1
    """)
    suspend fun getClosestAddress(lat: Double, lng: Double): Address?

    @Query("SELECT COUNT(*) FROM addresses")
    suspend fun getCount(): Int
}
