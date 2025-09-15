package com.jherkenhoff.qalculate.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CurrencyRateEntity::class], version = 1)
abstract class CurrencyDatabase : RoomDatabase() {
    abstract fun currencyRateDao(): CurrencyRateDao

    companion object {
        @Volatile private var INSTANCE: CurrencyDatabase? = null

        fun getInstance(context: Context): CurrencyDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CurrencyDatabase::class.java,
                    "currency_rates.db"
                ).build().also { INSTANCE = it }
            }
    }
}
