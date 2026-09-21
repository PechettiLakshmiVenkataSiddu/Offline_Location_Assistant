package com.example.myapplication.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "addresses")
data class Address(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val lat: Double,
    val lng: Double,
    val street: String,
    val colony: String,
    val city: String,
    val district: String
)
