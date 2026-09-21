package com.example.myapplication

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.LocationDao
import com.example.myapplication.data.LocationEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class LocationDatabaseTest {
    private lateinit var locationDao: LocationDao
    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()
        locationDao = db.locationDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun writeLocationAndReadNearest() = runBlocking {
        val loc1 = LocationEntity(
            lat = 10.0, long = 10.0, street = "St 1", colony = "Col 1",
            city = "City 1", district = "Dist 1"
        )
        val loc2 = LocationEntity(
            lat = 20.0, long = 20.0, street = "St 2", colony = "Col 2",
            city = "City 2", district = "Dist 2"
        )
        
        locationDao.insert(loc1)
        locationDao.insert(loc2)

        // Target (11, 11) should be closer to loc1 (10, 10)
        val nearest = locationDao.getNearestLocation(11.0, 11.0)
        
        assertNotNull(nearest)
        assertEquals("St 1", nearest?.street)
    }

    @Test
    @Throws(Exception::class)
    fun testNearestNeighborWithDistances() = runBlocking {
        // Points: A(0,0), B(10,10), C(10,0)
        locationDao.insert(LocationEntity(lat = 0.0, long = 0.0, street = "A", colony = "", city = "", district = ""))
        locationDao.insert(LocationEntity(lat = 10.0, long = 10.0, street = "B", colony = "", city = "", district = ""))
        locationDao.insert(LocationEntity(lat = 10.0, long = 0.0, street = "C", colony = "", city = "", district = ""))

        // Query point (9, 1) -> closer to C(10,0) than B(10,10) or A(0,0)
        // Dist to A: sqrt(9^2 + 1^2) = sqrt(82) approx 9.05
        // Dist to B: sqrt(1^2 + 9^2) = sqrt(82) approx 9.05
        // Dist to C: sqrt(1^2 + 1^2) = sqrt(2) approx 1.41
        
        val nearest = locationDao.getNearestLocation(9.0, 1.0)
        assertEquals("C", nearest?.street)
    }
}
