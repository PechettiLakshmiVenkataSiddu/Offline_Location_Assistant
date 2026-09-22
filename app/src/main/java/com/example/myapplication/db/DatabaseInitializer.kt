package com.example.myapplication.db

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DatabaseInitializer {

    private fun loadAddressesFromCsv(context: Context): List<Address> {
        val addresses = mutableListOf<Address>()
        context.assets.open("hyderabad_addresses.csv").bufferedReader().useLines { lines ->
            lines.drop(1).forEach { line ->
                val parts = line.split(",")
                if (parts.size >= 6) {
                    try {
                        addresses.add(
                            Address(
                                lat = parts[0].toDouble(),
                                lng = parts[1].toDouble(),
                                street = parts[2],
                                colony = parts[3],
                                city = parts[4],
                                district = parts[5]
                            )
                        )
                    } catch (e: NumberFormatException) {
                        // Skip malformed rows
                    }
                }
            }
        }
        return addresses
    }

    suspend fun initialize(context: Context) {
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val dao = db.addressDao()
            if (dao.getCount() == 0) {
                val addresses = loadAddressesFromCsv(context)
                dao.insertAll(addresses)
            }
        }
    }
}