package com.example.myapplication.db

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DatabaseInitializer {
    
    private val initialAddresses = listOf(
        Address(lat = 17.5601, lng = 78.4548, street = "Maisammaguda Main Road", colony = "Maisammaguda", city = "Hyderabad", district = "Medchal-Malkajgiri"),
        Address(lat = 17.4239, lng = 78.4738, street = "Necklace Road", colony = "Hussain Sagar", city = "Hyderabad", district = "Hyderabad"),
        Address(lat = 17.3616, lng = 78.4747, street = "Charminar Road", colony = "Charminar", city = "Hyderabad", district = "Hyderabad"),
        Address(lat = 17.4483, lng = 78.3915, street = "HITEC City Main Road", colony = "HITEC City", city = "Hyderabad", district = "Rangareddy"),
        Address(lat = 17.4435, lng = 78.3772, street = "Cyber Towers Road", colony = "Madhapur", city = "Hyderabad", district = "Rangareddy"),
        Address(lat = 17.4126, lng = 78.4482, street = "Banjara Hills Road No. 1", colony = "Banjara Hills", city = "Hyderabad", district = "Hyderabad"),
        Address(lat = 17.4239, lng = 78.4483, street = "Jubilee Hills Road No. 36", colony = "Jubilee Hills", city = "Hyderabad", district = "Hyderabad"),
        Address(lat = 17.3850, lng = 78.4867, street = "Abids Road", colony = "Abids", city = "Hyderabad", district = "Hyderabad"),
        Address(lat = 17.4062, lng = 78.4691, street = "Somajiguda Circle", colony = "Somajiguda", city = "Hyderabad", district = "Hyderabad"),
        Address(lat = 17.4948, lng = 78.3996, street = "Kompally Main Road", colony = "Kompally", city = "Hyderabad", district = "Medchal-Malkajgiri"),
        Address(lat = 17.3453, lng = 78.5511, street = "LB Nagar Ring Road", colony = "LB Nagar", city = "Hyderabad", district = "Rangareddy"),
        Address(lat = 17.4933, lng = 78.4004, street = "Bachupally Road", colony = "Bachupally", city = "Hyderabad", district = "Medchal-Malkajgiri"),
        Address(lat = 17.4326, lng = 78.4071, street = "Gachibowli Main Road", colony = "Gachibowli", city = "Hyderabad", district = "Rangareddy"),
        Address(lat = 17.3687, lng = 78.5247, street = "Dilsukhnagar Main Road", colony = "Dilsukhnagar", city = "Hyderabad", district = "Hyderabad"),
        Address(lat = 17.4400, lng = 78.4482, street = "Road No. 12", colony = "Banjara Hills", city = "Hyderabad", district = "Hyderabad"),
        Address(lat = 17.4239, lng = 78.5040, street = "Secunderabad Station Road", colony = "Secunderabad", city = "Secunderabad", district = "Hyderabad")
    )

    suspend fun initialize(context: Context) {
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val dao = db.addressDao()
            if (dao.getCount() == 0) {
                dao.insertAll(initialAddresses)
            }
        }
    }
}
